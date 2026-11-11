#!/usr/bin/env bash
# Opens the watch app on one of its pages on emulator-5558, and shoots it (plan 3 Task 4):
#
#   tools/watchface/app.sh today|tasbeeh|qibla [<out.png>]
#
# The app is started afresh on that page (--activity-clear-task: a running app would only be
# brought to the front, on the page it was left at), and the picture is taken WAIT seconds later
# (3 by default), once the page has drawn; without <out.png> it only opens the page. The page
# draws the feed the watch holds and the emulator's clock: store a feed first (feed.sh, or
# SAMPLE=ar-sa feed.sh) and set the clock (clock.sh), as for the face. What the page says is in
# its accessibility tree:
#
#   adb -s emulator-5558 shell uiautomator dump /sdcard/app.xml && adb -s emulator-5558 shell cat /sdcard/app.xml
#
# The drawing it is compared with is ref.py's screen= (g-today, g-tasbeeh, g-qibla), with its
# textmask= and compare.py's mask= for a shot in another language or clock (README).
source "$(dirname "$0")/lib.sh"
need_emulator
if [ "$#" -lt 1 ]; then
    echo "usage: $0 today|tasbeeh|qibla [<out.png>]" >&2
    exit 2
fi
PAGE="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"
case "$PAGE" in
    TODAY|TASBEEH|QIBLA) ;;
    *) echo "no page '$1': today, tasbeeh or qibla" >&2; exit 2 ;;
esac

wadb shell input keyevent KEYCODE_WAKEUP
wadb shell am start -W --activity-clear-task -n "$APP_PACKAGE/$APP_PACKAGE.wear.app.WearMainActivity" \
    --es "$APP_PACKAGE.wear.PAGE" "$PAGE" > /dev/null
sleep "${WAIT:-3}"
if [ -n "${2:-}" ]; then screenshot "$2"; fi
