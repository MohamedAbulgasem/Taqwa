#!/usr/bin/env bash
# P2.2's evidence script, committed beside the report (no build or test runs it): stores a debug sample for the day of a time, rewrites it,
# and has the face bind again (rebind_face, the debug build's DEBUG_REBIND; since plan 5 Tasks 4-5 the
# face is Push's, inside the watch app, and is never installed by itself, errata p5):
#   SAMPLE=ar-sa feedtext.sh <repo> greg '<text>' 14:58   every day's Gregorian line
#   SAMPLE=ar-sa feedtext.sh <repo> urdu - 14:47          every Arabic-Indic digit written extended (۰-۹)
REPO_ARG="$1"; shift
source "$REPO_ARG/tools/watchface/lib.sh"
need_emulator
MODE="$1"; TEXT="$2"; AT="$(epoch_of "${3:-14:58}")"
store_feed "$AT"
sleep 1
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-feedtext.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$WORK/sample.json"
python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    feed = json.load(f)
mode, text = sys.argv[2], sys.argv[3]
if mode == "greg":
    for day in feed["days"]:
        day["gregorian"] = text
else:
    ext = {chr(0x660 + k): chr(0x6F0 + k) for k in range(10)}
    def walk(v):
        if isinstance(v, str):
            return "".join(ext.get(c, c) for c in v)
        if isinstance(v, list):
            return [walk(x) for x in v]
        if isinstance(v, dict):
            return {k: walk(x) for k, x in v.items()}
        return v
    feed = walk(feed)
    feed["ui"]["digitZero"] = chr(0x6F0)
with open(sys.argv[4], "w", encoding="utf-8") as f:
    f.write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$WORK/sample.json" "$MODE" "$TEXT" "$WORK/feed.json"
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$WORK/feed.json"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored ($MODE) for the day of ${3:-14:58}; the face bound again"
