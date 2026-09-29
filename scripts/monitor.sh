#!/usr/bin/env bash
# The weekly timetable monitor (spec §5, docs/MONITOR.md): fetches each authority's newest
# published table into the restricted archive, checks the engine against every table whose content,
# metadata or engine changed and against the whole gate and the surveys, watches the built-in data
# horizons, mirrors the archive into a backup folder where one is configured, and writes a report.
# Silent when all is well. Runs on macOS (by hand) and on Linux (the weekly GitHub Actions job,
# ruling R94) alike.
#
#   scripts/monitor.sh [--no-fetch] [--only <source>]... [--check-all] [--verbose]
#
# --no-fetch checks what is held; --only <source> fetches and checks that source alone: a PARTIAL
# run, whose report stands beside the day's (reports/<date>-partial-<source>.md) and which never
# changes the notified attention set nor latest.md (ruling N1); --check-all checks every held table
# again, not only the ones whose signature changed and the ones red last run; --verbose prints each
# step's progress (otherwise one summary line and the report's path).
# Exit 0 all green, 1 attention needed, 2 the monitor itself failed; every early exit writes
# monitor/last-run.json with exit 2 and the reason (ruling N3), except a run refused by the lock,
# which leaves the holder's record alone. On macOS a notification is posted when the attention set
# changed since the last one, and always on 2.
#
# Every path comes from TAQWA_OFFICIAL, the folder that holds archive/ and monitor/ (default
# ~/Desktop/Workspace/apps/Taqwa-official on the Mac; the workspace in the cloud). The backup mirror
# runs only where TAQWA_OFFICIAL_BACKUP names a folder; a configured mirror that fails is reported.
# TAQWA_PYTHON picks the interpreter (else Homebrew's python3, else the first python3 on PATH; 3.9
# or later); JAVA_HOME the JDK (else macOS's java_home for 21). Runs only JVM tasks. Nothing fetched
# is ever committed to the public repository.
set -uo pipefail

OFFICIAL="${TAQWA_OFFICIAL:-${HOME:-}/Desktop/Workspace/apps/Taqwa-official}"
BACKUP="${TAQWA_OFFICIAL_BACKUP:-}"
MONITOR="$OFFICIAL/monitor"
LOCK="$MONITOR/lock"
TODAY="$(date +%Y-%m-%d)"
FETCH=1
CHECK_ALL=0
VERBOSE=0
FETCH_BROKE=0
RECORDED=0      # 1 once this run has written monitor/last-run.json
LOCK_REFUSED=0  # 1 when another run holds the lock: its record is left alone
HOLD_LOCK=0
REASON=""
ONLY=()

json_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    s="${s//$'\n'/ }"
    s="${s//$'\t'/ }"
    printf '%s' "$s"
}
write_json_record() {
    # A small JSON object at $1 from name/value pairs, written through a temp file: true, false, null
    # and whole numbers bare, everything else a string. Pure shell, so it works before any interpreter
    # is known to exist.
    local path="$1"
    shift
    local tmp out="{" sep=""
    tmp="$(dirname "$path")/.$(basename "$path").tmp"
    while [ $# -ge 2 ]; do
        local k="$1" v="$2"
        shift 2
        if [ "$v" = true ] || [ "$v" = false ] || [ "$v" = null ] || [[ "$v" =~ ^-?[0-9]+$ ]]; then
            out="$out$sep\"$k\": $v"
        else
            out="$out$sep\"$k\": \"$(json_escape "$v")\""
        fi
        sep=", "
    done
    printf '%s}\n' "$out" > "$tmp" && mv -f "$tmp" "$path"
}
write_failure_record() {
    [ -d "$MONITOR" ] || return 0
    write_json_record "$MONITOR/last-run.json" date "$TODAY" exit 2 summary "The monitor itself failed: $1" report "" \
        partial false partialOf null attentionChanged true neverEarly 0 attention 0 \
        issue "**Taqwa monitor, $TODAY.** The monitor itself failed: $1" && RECORDED=1
}
notify() {
    # A macOS nicety; nothing anywhere else (the cloud run keeps an issue instead).
    if [ "$(uname)" = Darwin ] && command -v osascript >/dev/null 2>&1; then
        osascript -e "display notification \"$1\" with title \"Taqwa monitor\"" >/dev/null 2>&1 || true
    fi
}
failed() {
    REASON="$1"
    echo "monitor: $1" >&2
    write_failure_record "$1"
    notify "The monitor itself failed: $1"
    exit 2
}
finish() {
    # Every exit: give the lock back, and leave a failure record behind any exit that is not a result
    # (a signal, a set -u slip, a command that died), unless the lock refused this run.
    local code=$?
    [ "$HOLD_LOCK" = 1 ] && rm -rf "$LOCK"
    if [ "$code" != 0 ] && [ "$code" != 1 ] && [ "$RECORDED" != 1 ] && [ "$LOCK_REFUSED" != 1 ]; then
        write_failure_record "${REASON:-exited $code before a result (see the run log under monitor/fetch/)}"
    fi
}
trap finish EXIT

cd "$(dirname "$0")/.." || failed "cannot enter the repository"

while [ $# -gt 0 ]; do
    case "$1" in
        --no-fetch) FETCH=0; shift ;;
        --check-all) CHECK_ALL=1; shift ;;
        --verbose) VERBOSE=1; shift ;;
        --only) [ $# -ge 2 ] || failed "--only needs a source"; ONLY+=("$2"); shift 2 ;;
        -h|--help) sed -n '2,26p' "$0"; RECORDED=1; exit 0 ;;
        *) failed "unknown option $1" ;;
    esac
done

# The interpreter: the tested one, not whatever a scheduler's PATH finds first (review I7).
if [ -n "${TAQWA_PYTHON:-}" ]; then
    PY="$TAQWA_PYTHON"
elif [ -x /opt/homebrew/bin/python3 ]; then
    PY=/opt/homebrew/bin/python3
else
    PY="$(command -v python3 || true)"
fi
[ -n "$PY" ] && [ -x "$PY" ] || failed "no python3 (set TAQWA_PYTHON)"
# Java 21 for Gradle: JAVA_HOME as given, else macOS's own lookup.
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    JH="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    [ -n "$JH" ] && export JAVA_HOME="$JH"
fi

say() { [ "$VERBOSE" = 1 ] && echo "$1"; return 0; }

[ -d "$OFFICIAL/archive" ] || failed "no archive under $OFFICIAL (set TAQWA_OFFICIAL)"
mkdir -p "$MONITOR/fetch" "$MONITOR/reports" || failed "cannot write $MONITOR"

# One run at a time (review M8): a lock folder, with the holder's pid; a dead holder's lock is taken over.
if ! mkdir "$LOCK" 2>/dev/null; then
    HOLDER="$(cat "$LOCK/pid" 2>/dev/null || true)"
    if [ -n "$HOLDER" ] && kill -0 "$HOLDER" 2>/dev/null; then
        LOCK_REFUSED=1
        echo "monitor: another run is in progress (pid $HOLDER holds $LOCK); its record is left alone" >&2
        exit 2
    fi
    rm -rf "$LOCK"
    mkdir "$LOCK" 2>/dev/null || failed "cannot take the lock $LOCK"
fi
HOLD_LOCK=1
echo $$ > "$LOCK/pid"

LOG="$MONITOR/fetch/run-$TODAY.log"
: > "$LOG"
echo "== monitor $(date) python=$PY java_home=${JAVA_HOME:-unset} official=$OFFICIAL" >> "$LOG"

ONLY_PY=()
ONLY_GRADLE=""
for s in ${ONLY[@]+"${ONLY[@]}"}; do
    [ -n "$s" ] || continue
    ONLY_PY+=(--only "$s")
    ONLY_GRADLE="${ONLY_GRADLE:+$ONLY_GRADLE,}$s"
done

# 0. The run's own setup: does the archive repository's workflow file still match the public template? (review N8)
TEMPLATE="tools/timetables/monitor/ci/monitor-weekly.yml"
PRIVATE_WORKFLOW="$OFFICIAL/.github/workflows/monitor-weekly.yml"
if [ -f "$PRIVATE_WORKFLOW" ] && [ -f "$TEMPLATE" ] && ! cmp -s "$TEMPLATE" "$PRIVATE_WORKFLOW"; then
    DRIFT="$OFFICIAL/.github/workflows/monitor-weekly.yml differs from the public template $TEMPLATE ($(diff "$TEMPLATE" "$PRIVATE_WORKFLOW" | grep -c '^[<>]') lines differ)"
    write_json_record "$MONITOR/notes.json" date "$TODAY" workflowDrift "$DRIFT" || true
    echo "monitor: $DRIFT" >&2
else
    write_json_record "$MONITOR/notes.json" date "$TODAY" || true
fi

# 1. Fetch. A driver that breaks is a finding the check reports; what is held is still checked.
if [ "$FETCH" = 1 ]; then
    say "monitor: fetching (log: $LOG)"
    if ! "$PY" tools/timetables/monitor/fetch.py --official "$OFFICIAL" --today "$TODAY" ${ONLY_PY[@]+"${ONLY_PY[@]}"} >>"$LOG" 2>&1; then
        FETCH_BROKE=1
        tail -20 "$LOG" >&2
        echo "monitor: the fetch driver broke (see $LOG); what is held is checked anyway" >&2
    fi
    [ "$VERBOSE" = 1 ] && grep -E '^[a-z0-9-]+: ' "$LOG" | sed 's/^/  /'
fi

# 2. Backup: only where a mirror is configured; the record says what happened either way (review I2).
if [ -n "$BACKUP" ]; then
    if ! "$PY" tools/timetables/monitor/backup.py --official "$OFFICIAL" --backup "$BACKUP" --today "$TODAY" >>"$LOG" 2>&1; then
        echo "monitor: the backup step failed (see $LOG)" >&2
    fi
    if ! grep -q "\"date\": \"$TODAY\"" "$MONITOR/backup.json" 2>/dev/null; then
        write_json_record "$MONITOR/backup.json" date "$TODAY" error "backup.py wrote no record (see $LOG)" target "$BACKUP" || true
    fi
    [ "$VERBOSE" = 1 ] && grep -E '^backup: ' "$LOG" | tail -1 | sed 's/^/  /'
else
    write_json_record "$MONITOR/backup.json" date "$TODAY" skipped "TAQWA_OFFICIAL_BACKUP is not set; the private repository is the backup" || true
fi

# 3. Check.
rm -f "$MONITOR/last-run.json"
say "monitor: checking (the tables whose signature changed, the gate, the surveys, the horizons)"
GRADLE_ARGS=(-q -p tools/timetables monitor "-Pofficial=$OFFICIAL" "-Pmonitor=$MONITOR" "-Ptoday=$TODAY")
[ -n "$ONLY_GRADLE" ] && GRADLE_ARGS+=("-Ponly=$ONLY_GRADLE")
[ "$CHECK_ALL" = 1 ] && GRADLE_ARGS+=("-PcheckAll=true")
[ -n "${CI:-}" ] && GRADLE_ARGS+=(--no-daemon)
./gradlew "${GRADLE_ARGS[@]}" >>"$LOG" 2>&1
GRADLE_EXIT=$?
if [ ! -f "$MONITOR/last-run.json" ]; then
    tail -30 "$LOG" >&2
    failed "the check step wrote no result (Gradle exit $GRADLE_EXIT, see $LOG)"
fi
RECORDED=1

field() {
    "$PY" -c 'import json, sys
v = json.load(open(sys.argv[1], encoding="utf-8")).get(sys.argv[2], "")
print(str(v).lower() if isinstance(v, bool) else ("" if v is None else v))' "$MONITOR/last-run.json" "$1"
}
if [ "$FETCH_BROKE" = 1 ]; then
    # The check ran on what is held, but the run as a whole failed: the record says so (exit 2).
    "$PY" -c 'import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["exit"] = 2
d["summary"] = "The monitor itself failed: the fetch driver broke (see the run log); " + d.get("summary", "")
d["attentionChanged"] = True
d["issue"] = "**Taqwa monitor, " + d.get("date", "") + ".** The monitor itself failed: the fetch driver broke (see the run log).\n\n" + d.get("issue", "")
with open(p + ".tmp", "w", encoding="utf-8") as f:
    json.dump(d, f, indent=1, ensure_ascii=False)
import os
os.replace(p + ".tmp", p)' "$MONITOR/last-run.json" || true
fi
CODE="$(field exit)"
SUMMARY="$(field summary)"
REPORT="$(field report)"
CHANGED="$(field attentionChanged)"
PARTIAL="$(field partial)"
case "$REPORT" in
    /*) ;;
    "") REPORT="$MONITOR/latest.md" ;;
    *) REPORT="$OFFICIAL/$REPORT" ;;
esac
echo "monitor: $SUMMARY"
echo "report: $REPORT"

# 4. Notify on change only (ruling R93); always when the monitor itself failed; never on a partial run.
if [ "$CODE" = 2 ]; then
    notify "The monitor itself failed — see $REPORT"
elif [ "$PARTIAL" != true ] && [ "$CHANGED" = true ]; then
    if [ "$CODE" = 0 ]; then notify "All green again"; else notify "$SUMMARY — see $REPORT"; fi
fi

# 5. Logs older than 90 days go (review M11); reports and captures stay.
find "$MONITOR/fetch" -type f \( -name '*.log' -o -name '20*.json' \) -mtime +90 -delete 2>/dev/null || true
exit "$CODE"
