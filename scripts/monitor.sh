#!/usr/bin/env bash
# The weekly timetable monitor (spec §5, docs/MONITOR.md): fetches each authority's newest
# published table into the restricted local archive, checks the engine against every new or changed
# one and against the whole gate and the surveys, watches the built-in data horizons, mirrors the
# archive into its backup, and writes a report. Silent when all is well.
#
#   scripts/monitor.sh [--no-fetch] [--only <source>]...
#
# Exit 0 all green, 1 attention needed, 2 the monitor itself failed; a macOS notification on 1 or 2.
# Prints one summary line and the report's path. Works from any checkout of the repository: the
# archive root is TAQWA_OFFICIAL (default ~/Desktop/Workspace/apps/Taqwa-official), passed to Gradle
# explicitly; the backup root TAQWA_OFFICIAL_BACKUP (default the 2026-09-27 backup folder beside it).
# Runs only JVM tasks. Nothing fetched is ever committed.
set -uo pipefail
cd "$(dirname "$0")/.."

OFFICIAL="${TAQWA_OFFICIAL:-$HOME/Desktop/Workspace/apps/Taqwa-official}"
BACKUP="${TAQWA_OFFICIAL_BACKUP:-$HOME/Desktop/Workspace/apps/Taqwa-official-archive-backup-2026-09-27}"
MONITOR="$OFFICIAL/monitor"
FETCH=1
ONLY=()
while [ $# -gt 0 ]; do
    case "$1" in
        --no-fetch) FETCH=0; shift ;;
        --only) [ $# -ge 2 ] || { echo "monitor: --only needs a source" >&2; exit 2; }; ONLY+=("$2"); shift 2 ;;
        -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
        *) echo "monitor: unknown option $1" >&2; exit 2 ;;
    esac
done

notify() {
    osascript -e "display notification \"$1\" with title \"Taqwa monitor\"" >/dev/null 2>&1 || true
}
failed() {
    echo "monitor: $1" >&2
    notify "The monitor itself failed: $1"
    exit 2
}

[ -d "$OFFICIAL/archive" ] || failed "no archive under $OFFICIAL (set TAQWA_OFFICIAL)"
mkdir -p "$MONITOR/fetch" "$MONITOR/reports" || failed "cannot write $MONITOR"
LOG="$MONITOR/fetch/run-$(date +%Y-%m-%d).log"
: > "$LOG"

ONLY_PY=()
ONLY_GRADLE=""
for s in "${ONLY[@]:-}"; do
    [ -n "$s" ] || continue
    ONLY_PY+=(--only "$s")
    ONLY_GRADLE="${ONLY_GRADLE:+$ONLY_GRADLE,}$s"
done

if [ "$FETCH" = 1 ]; then
    echo "monitor: fetching (log: $LOG)"
    if ! python3 tools/timetables/monitor/fetch.py --official "$OFFICIAL" "${ONLY_PY[@]}" >>"$LOG" 2>&1; then
        tail -20 "$LOG" >&2
        failed "the fetch step broke (see $LOG)"
    fi
    grep -E '^[a-z0-9-]+: ' "$LOG" | sed 's/^/  /'
fi

if [ -d "$BACKUP/archive" ]; then
    python3 tools/timetables/monitor/backup.py --official "$OFFICIAL" --backup "$BACKUP" >>"$LOG" 2>&1 \
        || echo "monitor: the backup step failed (see $LOG)" >&2
    grep -E '^backup: ' "$LOG" | tail -1 | sed 's/^/  /'
else
    echo "monitor: no backup folder at $BACKUP (set TAQWA_OFFICIAL_BACKUP); the archive was not mirrored" >&2
fi

rm -f "$MONITOR/last-run.json"
echo "monitor: checking (the fetched tables, the gate, the surveys, the horizons)"
GRADLE_ARGS=(-q -p tools/timetables monitor "-Pofficial=$OFFICIAL" "-Pmonitor=$MONITOR")
[ -n "$ONLY_GRADLE" ] && GRADLE_ARGS+=("-Ponly=$ONLY_GRADLE")
./gradlew "${GRADLE_ARGS[@]}" >>"$LOG" 2>&1
GRADLE_EXIT=$?
if [ ! -f "$MONITOR/last-run.json" ]; then
    tail -30 "$LOG" >&2
    failed "the check step wrote no result (Gradle exit $GRADLE_EXIT, see $LOG)"
fi

CODE=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["exit"])' "$MONITOR/last-run.json")
SUMMARY=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["summary"])' "$MONITOR/last-run.json")
REPORT=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["report"])' "$MONITOR/last-run.json")
echo "monitor: $SUMMARY"
echo "report: $REPORT"
if [ "$CODE" != "0" ]; then
    N=$(echo "$SUMMARY" | grep -oE '^[0-9]+' || echo "some")
    notify "$N items need attention — see $REPORT"
fi
exit "$CODE"
