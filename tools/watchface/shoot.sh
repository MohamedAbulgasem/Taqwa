#!/usr/bin/env bash
# Screenshots the face on emulator-5558 at a minute of the mock day, awake, always-on, or both:
#
#   tools/watchface/shoot.sh <out dir> <name> <HH:MM> [both|awake|aod]
#
#   <out dir>/<name>-awake.png   the clock set 8 s before the minute, the sample feed stored for the
#                                day, and the picture taken as the minute turns (the face redraws
#                                its clock and the slots' timelines move at :00)
#   <out dir>/<name>-aod.png     the clock set 20 s before, the watch put to sleep, and the
#                                always-on frame taken 5 s past the minute (the always-on frame is
#                                redrawn once a minute)
#
# Each picture's line says the emulator's time when it was taken. The clock stays where it was left
# (automatic time off): tools/watchface/clock.sh auto gives it back to the network. With KEEP_FEED=1
# the feed already stored is kept (tools/watchface/hijri.sh stores one with a Hijri text of your
# choosing) rather than replaced by the sample.
source "$(dirname "$0")/lib.sh"
need_emulator
if [ "$#" -lt 3 ]; then
    echo "usage: $0 <out dir> <name> <HH:MM> [both|awake|aod]" >&2
    exit 2
fi
DIR="$1"; NAME="$2"; AT="$(epoch_of "$3")"; MODE="${4:-both}"
mkdir -p "$DIR"

if [ "$MODE" = both ] || [ "$MODE" = awake ]; then
    wadb shell input keyevent KEYCODE_WAKEUP
    sleep 1
    set_clock $((AT - 8))
    [ "${KEEP_FEED:-0}" = 1 ] || store_feed "$AT"
    wait_until "$AT"
    sleep 0.4
    screenshot "$DIR/$NAME-awake.png"
fi
if [ "$MODE" = both ] || [ "$MODE" = aod ]; then
    wadb shell input keyevent KEYCODE_WAKEUP
    sleep 1
    set_clock $((AT - 20))
    [ "${KEEP_FEED:-0}" = 1 ] || store_feed "$AT"
    sleep 1
    wadb shell input keyevent KEYCODE_SLEEP
    wait_until $((AT + 5))
    screenshot "$DIR/$NAME-aod.png"
    wadb shell input keyevent KEYCODE_WAKEUP
fi
