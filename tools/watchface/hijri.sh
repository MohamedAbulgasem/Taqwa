#!/usr/bin/env bash
# Stores the sample feed for the day of a time with every day's Hijri date (hijriDayMonth, the
# day and month the face shows, Ruling 24, and the full hijri beside it) replaced by a text of
# your choosing, on emulator-5558, and has the face bind its slots again, so that the face shows
# that text: how a long month ("29 Jumada al-Akhirah") or another language's form is checked
# against the arch before it comes round in the calendar.
#
#   tools/watchface/hijri.sh '29 Jumada al-Akhirah'          # for the mock day
#   tools/watchface/hijri.sh '১০ জমাদিউল আউয়াল' 14:58
#   KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots long 14:58 awake   # shoot it without the sample
#
# The debug receiver only stores the sample, so the text goes into the stored file itself (run-as,
# files/feed.json, a debug build's own file). The watch app's process is stopped, so that its store
# reads the file again, and the face is asked to bind its slots again (lib.sh rebind_face), so that
# it asks its sources for their data: a source answers from the store when asked. Nothing is
# installed; put the face on first (tools/watchface/install.sh). The sources answer for the emulator's clock as it is when
# the face binds, so set the clock near the shot's time first (lib.sh set_clock): bound in the
# evening and shot at 04:40, the face showed the evening's next prayer, the next day's Fajr, and its
# countdown (Task 13's fix round 1).
source "$(dirname "$0")/lib.sh"
need_emulator
if [ "$#" -lt 1 ]; then
    echo "usage: $0 <hijri text> [HH:MM]" >&2
    exit 2
fi
TEXT="$1"; AT="$(epoch_of "${2:-14:58}")"

store_feed "$AT"
sleep 1
# Read, then write: in one pipeline the writer would empty the file before the reader had read it.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-hijri.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$WORK/sample.json"
python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    feed = json.load(f)
for day in feed["days"]:
    day["hijri"] = sys.argv[2]
    day["hijriDayMonth"] = sys.argv[2]
with open(sys.argv[3], "w", encoding="utf-8") as f:
    f.write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$WORK/sample.json" "$TEXT" "$WORK/feed.json"
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$WORK/feed.json"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored: every day's hijri is '$TEXT' (the sample for the day of ${2:-14:58}); the face was bound again"
