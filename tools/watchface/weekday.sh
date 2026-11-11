#!/usr/bin/env bash
# Stores the sample feed for the day of a time with every day's weekday and day number (the Moon's
# date window: slot 3's title and text) replaced by texts of your choosing, on emulator-5558, and has
# the face bind its slots again, so that the window shows them: how a long weekday ("الخميس") beside
# a wide day ("28") is checked before it comes round in the calendar. SAMPLE= picks the sample the
# texts go into (en12, ar-ly12 or ar-sa: the window's direction and digits are that sample's).
#
#   tools/watchface/weekday.sh 'MON' 28                       # for the mock day, at 14:58
#   SAMPLE=ar-sa tools/watchface/weekday.sh 'الخميس' '٢٨' 10:10
#   KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots thu 10:10:36 awake   # shoot it without the sample
#
# As hijri.sh: the debug receiver stores the sample, the texts go into the stored file (run-as,
# files/feed.json), the watch app's process is stopped so that its store reads the file again, and
# the face is asked to bind its slots again (lib.sh rebind_face), so that it asks its sources for
# their data; nothing is installed. Put the face on first (tools/watchface/install.sh).
source "$(dirname "$0")/lib.sh"
need_emulator
if [ "$#" -lt 2 ]; then
    echo "usage: $0 <weekday> <day number> [HH:MM]" >&2
    exit 2
fi
WEEKDAY="$1"; DAY="$2"; AT="$(epoch_of "${3:-14:58}")"

store_feed "$AT"
sleep 1
# Read, then write: in one pipeline the writer would empty the file before the reader had read it.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-weekday.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$WORK/sample.json"
python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    feed = json.load(f)
for day in feed["days"]:
    day["weekday"] = sys.argv[2]
    day["dayNumber"] = sys.argv[3]
with open(sys.argv[4], "w", encoding="utf-8") as f:
    f.write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$WORK/sample.json" "$WEEKDAY" "$DAY" "$WORK/feed.json"
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$WORK/feed.json"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored: every day's window reads '$WEEKDAY' '$DAY' (the sample ${SAMPLE:-en} for the day of ${3:-14:58}); the face was bound again"
