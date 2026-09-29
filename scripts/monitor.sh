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
# --no-fetch checks what is held; --only <source> fetches and checks that source alone (a source
# with cadence manual runs only when named); --check-all checks every held table again, not only
# the ones whose signature changed and the ones red last run; --verbose prints each step's progress
# (otherwise one summary line and the report's path).
# Exit 0 all green, 1 attention needed, 2 the monitor itself failed. On macOS a notification is
# posted when the attention set changed since the last one, and always on 2.
#
# Every path comes from TAQWA_OFFICIAL, the folder that holds archive/ and monitor/ (default
# ~/Desktop/Workspace/apps/Taqwa-official on the Mac; the workspace in the cloud). The backup mirror
# runs only where TAQWA_OFFICIAL_BACKUP names a folder; a configured mirror that fails is reported.
# TAQWA_PYTHON picks the interpreter (else Homebrew's python3, else the first python3 on PATH; 3.9
# or later); JAVA_HOME the JDK (else macOS's java_home for 21). Runs only JVM tasks. Nothing fetched
# is ever committed to the public repository.
set -uo pipefail
cd "$(dirname "$0")/.." || { echo "monitor: cannot enter the repository" >&2; exit 2; }

OFFICIAL="${TAQWA_OFFICIAL:-${HOME:-}/Desktop/Workspace/apps/Taqwa-official}"
BACKUP="${TAQWA_OFFICIAL_BACKUP:-}"
MONITOR="$OFFICIAL/monitor"
TODAY="$(date +%Y-%m-%d)"
FETCH=1
CHECK_ALL=0
VERBOSE=0
FETCH_BROKE=0
ONLY=()
while [ $# -gt 0 ]; do
    case "$1" in
        --no-fetch) FETCH=0; shift ;;
        --check-all) CHECK_ALL=1; shift ;;
        --verbose) VERBOSE=1; shift ;;
        --only) [ $# -ge 2 ] || { echo "monitor: --only needs a source" >&2; exit 2; }; ONLY+=("$2"); shift 2 ;;
        -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
        *) echo "monitor: unknown option $1" >&2; exit 2 ;;
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
[ -n "$PY" ] && [ -x "$PY" ] || { echo "monitor: no python3 (set TAQWA_PYTHON)" >&2; exit 2; }
# Java 21 for Gradle: JAVA_HOME as given, else macOS's own lookup.
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    JH="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    [ -n "$JH" ] && export JAVA_HOME="$JH"
fi

say() { [ "$VERBOSE" = 1 ] && echo "$1"; return 0; }
notify() {
    # A macOS nicety; nothing anywhere else (the cloud run keeps an issue instead).
    if [ "$(uname)" = Darwin ] && command -v osascript >/dev/null 2>&1; then
        osascript -e "display notification \"$1\" with title \"Taqwa monitor\"" >/dev/null 2>&1 || true
    fi
}
write_record() {
    # A small JSON record under monitor/ ($1 the file, then name/value pairs), for the check half.
    "$PY" -c '
import json, sys
path = sys.argv[1]
pairs = sys.argv[2:]
out = {pairs[i]: (pairs[i + 1] == "true" if pairs[i + 1] in ("true", "false") else pairs[i + 1]) for i in range(0, len(pairs), 2)}
with open(path + ".tmp", "w", encoding="utf-8") as f:
    json.dump(out, f, indent=1, ensure_ascii=False)
import os
os.replace(path + ".tmp", path)
' "$@"
}
failed() {
    echo "monitor: $1" >&2
    if [ -d "$MONITOR" ]; then
        write_record "$MONITOR/last-run.json" date "$TODAY" exit 2 summary "The monitor itself failed: $1" report "" \
            attentionChanged true issue "**Taqwa monitor, $TODAY.** The monitor itself failed: $1" || true
    fi
    notify "The monitor itself failed: $1"
    exit 2
}

[ -d "$OFFICIAL/archive" ] || failed "no archive under $OFFICIAL (set TAQWA_OFFICIAL)"
mkdir -p "$MONITOR/fetch" "$MONITOR/reports" || failed "cannot write $MONITOR"

# One run at a time (review M8): a lock folder, with the holder's pid; a dead holder's lock is taken over.
LOCK="$MONITOR/lock"
if ! mkdir "$LOCK" 2>/dev/null; then
    HOLDER="$(cat "$LOCK/pid" 2>/dev/null || true)"
    if [ -n "$HOLDER" ] && kill -0 "$HOLDER" 2>/dev/null; then
        failed "another run is in progress (pid $HOLDER holds $LOCK)"
    fi
    rm -rf "$LOCK"
    mkdir "$LOCK" 2>/dev/null || failed "cannot take the lock $LOCK"
fi
echo $$ > "$LOCK/pid"
trap 'rm -rf "$LOCK"' EXIT

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
        write_record "$MONITOR/backup.json" date "$TODAY" error "backup.py wrote no record (see $LOG)" target "$BACKUP" || true
    fi
    [ "$VERBOSE" = 1 ] && grep -E '^backup: ' "$LOG" | tail -1 | sed 's/^/  /'
else
    write_record "$MONITOR/backup.json" date "$TODAY" skipped "TAQWA_OFFICIAL_BACKUP is not set; the private repository is the backup" || true
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

field() {
    "$PY" -c 'import json, sys
v = json.load(open(sys.argv[1], encoding="utf-8")).get(sys.argv[2], "")
print(str(v).lower() if isinstance(v, bool) else v)' "$MONITOR/last-run.json" "$1"
}
CODE="$(field exit)"
SUMMARY="$(field summary)"
REPORT="$(field report)"
CHANGED="$(field attentionChanged)"
case "$REPORT" in
    /*) ;;
    "") REPORT="$MONITOR/latest.md" ;;
    *) REPORT="$OFFICIAL/$REPORT" ;;
esac
[ "$FETCH_BROKE" = 1 ] && CODE=2
echo "monitor: $SUMMARY"
echo "report: $REPORT"

# 4. Notify on change only (ruling R93); always when the monitor itself failed.
if [ "$CODE" = 2 ]; then
    notify "The monitor itself failed — see $REPORT"
elif [ "$CHANGED" = true ]; then
    if [ "$CODE" = 0 ]; then notify "All green again"; else notify "$SUMMARY — see $REPORT"; fi
fi

# 5. Logs older than 90 days go (review M11); reports and captures stay.
find "$MONITOR/fetch" -type f \( -name '*.log' -o -name '20*.json' \) -mtime +90 -delete 2>/dev/null || true
exit "$CODE"
