#!/usr/bin/env bash
# TalkBack on emulator-5558, and what it says on the face (plan 1, Task 12):
#
#   tools/watchface/talkback.sh on          # TalkBack on; its phone-access prompt is cancelled
#   tools/watchface/talkback.sh walk [n]    # n "next item" swipes (default 8), and what each one said
#   tools/watchface/talkback.sh idle [s]    # how many times TalkBack spoke in s seconds untouched (10)
#   tools/watchface/talkback.sh tap2 x y    # a double tap at (x, y): TalkBack's "activate" (plan 3 Task 4)
#   tools/watchface/talkback.sh actions     # TalkBack's gesture for the focused item's actions menu
#   tools/watchface/talkback.sh off
#
# What TalkBack says is read from its own log, which it writes only with its Diagnosis mode on
# (TalkBack settings, Advanced settings, Developer settings, Diagnosis mode: once per emulator; it
# also draws a log overlay over the screen while TalkBack runs, so shoot the face with TalkBack off).
# A swipe is sent through the emulator's own touch screen (the console's "event mouse"), because a
# gesture injected with "adb shell input" reaches the face and not TalkBack. A tap injected that
# way is not TalkBack's either, which is how "on" cancels the prompt (TalkBack asks for the phone's
# call state, and this script never grants it). The editor (choose.sh) needs TalkBack off.
#
# A walk prints each item once; an empty line is the end of the list, after which TalkBack starts
# again at the first item. Each line is what TalkBack said, but for its "Double-tap to activate".
#
# tap2's two taps must fall within TalkBack's double-tap window (ViewConfiguration's 300 ms) and
# no closer than its 40 ms minimum, which separate "adb emu" calls do not keep to (each starts adb
# and opens the console anew), so its four touch events go through one console session of their
# own (port 5558, after "auth" with ~/.emulator_console_auth_token), with the gaps the README gives.
# actions draws the one-finger gesture that leads to the actions menu by TalkBack's own settings
# ("Customize gestures") on this emulator, as the README records it; what TalkBack says is printed
# as walk prints it.
source "$(dirname "$0")/lib.sh"
need_emulator
TALKBACK=com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService

# One console command, given 5 s: a console call has hung for good once in a long run.
console() { perl -e 'alarm shift; exec @ARGV' 5 "$ADB_BIN" -s "$SERIAL" emu "$@" > /dev/null || true; }

swipe() {  # x1 y1 x2 y2: one finger, through the emulator's touch screen
    local x1=$1 y1=$2 x2=$3 y2=$4 steps=6 i
    console event mouse "$x1" "$y1" 0 1
    for i in $(seq 1 $steps); do
        console event mouse $((x1 + (x2 - x1) * i / steps)) $((y1 + (y2 - y1) * i / steps)) 0 1
    done
    console event mouse "$x2" "$y2" 0 0
}

# One console session for several events: "event mouse x y 0 1" and the like, one an argument, and
# "sleep:<s>" between them where they must be apart. The token is the emulator's own, read here and
# never printed.
session() {
    local port="${SERIAL#emulator-}" token
    token="$(cat "$HOME/.emulator_console_auth_token")"
    {
        printf 'auth %s\n' "$token"
        sleep 0.3
        for step in "$@"; do
            case "$step" in
                sleep:*) sleep "${step#sleep:}" ;;
                *) printf '%s\n' "$step" ;;
            esac
        done
        sleep 0.3
        printf 'quit\n'
    } | perl -e 'alarm shift; exec @ARGV' 10 nc localhost "$port" > /dev/null || true
}

tap2() {  # x y: down, up, down, up, the second tap 120 ms after the first is lifted
    local x=$1 y=$2
    session "event mouse $x $y 0 1" sleep:0.05 "event mouse $x $y 0 0" sleep:0.12 \
        "event mouse $x $y 0 1" sleep:0.05 "event mouse $x $y 0 0"
}

# Wear TalkBack 16 assigns no gesture to the actions menu itself ("Customize gestures" on this
# emulator, 30 October: five of its eight angle gestures are "Tap to assign"). "Swipe down then
# right" opens the TalkBack menu, where a phone's TalkBack lists the focused item's actions: this
# draws that, one finger down the middle and then right, with its points 20 ms apart.
actions() {
    local steps=() i
    steps+=("event mouse 227 110 0 1")
    for i in 1 2 3 4 5 6; do steps+=(sleep:0.02 "event mouse 227 $((110 + 190 * i / 6)) 0 1"); done
    for i in 1 2 3 4 5 6; do steps+=(sleep:0.02 "event mouse $((227 + 170 * i / 6)) 300 0 1"); done
    steps+=(sleep:0.02 "event mouse 397 300 0 0")
    session "${steps[@]}"
}

spoken() {  # what TalkBack has said since the log was cleared, one fragment a line
    local pid
    pid="$(wadb shell pidof com.google.android.marvin.talkback | tr -d '\r')"
    [ -n "$pid" ] || { echo "TalkBack is not running" >&2; return 1; }
    wadb logcat -d --pid="$pid" | grep 'Speaking fragment text=' \
        | sed 's/.*Speaking fragment text="\(.*\)", utteranceId.*/\1/' | grep -v '^Double-tap' || true
}

case "${1:-}" in
    on)
        wadb shell settings put secure enabled_accessibility_services "$TALKBACK"
        wadb shell settings put secure accessibility_enabled 1
        for _ in $(seq 1 20); do
            sleep 1
            if wadb shell dumpsys activity activities | grep topResumedActivity | grep -q PermissionRequestActivity; then
                sleep 1; wadb shell input tap 152 373; sleep 1.5; break
            fi
        done
        wadb shell input keyevent KEYCODE_WAKEUP
        sleep 2
        echo "TalkBack on: $(wadb shell settings get secure enabled_accessibility_services | tr -d '\r')"
        ;;
    off)
        wadb shell settings delete secure enabled_accessibility_services > /dev/null
        wadb shell settings put secure accessibility_enabled 0
        sleep 2
        echo "TalkBack off"
        ;;
    walk)
        for i in $(seq 1 "${2:-8}"); do
            wadb logcat -c
            swipe 110 227 350 227
            sleep 3.5
            echo "$i: $(spoken | awk '!seen[$0]++' | paste -sd '|' -)"
        done
        ;;
    idle)
        wadb logcat -c
        sleep "${2:-10}"
        echo "spoken in ${2:-10} s untouched: $(spoken | grep -c .)"
        ;;
    tap2)
        [ "$#" -eq 3 ] || { echo "usage: $0 tap2 x y" >&2; exit 2; }
        wadb logcat -c
        tap2 "$2" "$3"
        sleep 3
        echo "tap2 $2 $3: $(spoken | awk '!seen[$0]++' | paste -sd '|' -)"
        ;;
    actions)
        wadb logcat -c
        actions
        sleep 3
        echo "actions: $(spoken | awk '!seen[$0]++' | paste -sd '|' -)"
        ;;
    *)
        echo "usage: $0 on | walk [n] | idle [seconds] | tap2 x y | actions | off" >&2
        exit 2
        ;;
esac
