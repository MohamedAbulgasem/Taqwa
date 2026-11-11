#!/usr/bin/env bash
# Sets the emulator's clock (emulator-5558) to a time on the mock day, Sunday 18 October 2026 in
# Cape Town, with automatic time off so that it stays; or gives the clock back to the network:
#
#   tools/watchface/clock.sh 14:58         # 14:58:00 on the mock day
#   tools/watchface/clock.sh 14:57:52      # to the second
#   tools/watchface/clock.sh 1792328280    # epoch seconds, any day
#   tools/watchface/clock.sh auto          # automatic time on again (leave the emulator so)
#
# WATCH=emulator-5566 or WATCH=emulator-5568 sets the clock of the Wear OS 4 or the Wear OS 3
# emulator instead (Taqwa_Wear_OS_4 and Taqwa_Wear_OS_3, scripts/wear-old-emulator.sh, plan 5
# Task 12 Step 1), each only as its own AVD; no other watch is driven:
#
#   WATCH=emulator-5568 tools/watchface/clock.sh 19:02:30
#   WATCH=emulator-5568 tools/watchface/clock.sh auto
#
# On Wear OS 3 and 4 (API 30 and 33) automatic time off is not enough: the watch's own sync from
# the phone, clockwork_auto_time 0, which falls back to the network's time, moved a set clock to
# the host's time within three minutes (Wear OS 3 in P5.12 Step 1, Wear OS 4 in Ruling 72's run).
# So there it also turns that sync off, clockwork_auto_time 2, first recording the two values it
# found on the watch, clockwork_auto_time and auto_time (/data/local/tmp/taqwa-clockwork-auto-time;
# a later set keeps that first record), and `auto` puts both back as found, or deletes a setting
# that was not there, sets the clock to the host's, and forgets the record. Those emulators boot
# with auto_time 0 and clockwork_auto_time 0. With nothing recorded (a watch this script never
# set), `auto` falls back to automatic time on, auto_time 1, and leaves clockwork_auto_time as it
# is. Wear OS 6 is set as it always was, automatic time alone; the one call added there reads its
# API level (getprop ro.build.version.sdk), which tells the watches apart.
# tools/watchface/clock-test.sh is its test.
source "$(dirname "$0")/lib.sh"
case "${WATCH:-$SERIAL}" in
    emulator-5558) ;;
    emulator-5566) SERIAL=emulator-5566; AVD=Taqwa_Wear_OS_4 ;;
    emulator-5568) SERIAL=emulator-5568; AVD=Taqwa_Wear_OS_3 ;;
    *)
        echo "WATCH is emulator-5558, emulator-5566 or emulator-5568, not '$WATCH'" >&2
        exit 2
        ;;
esac
need_emulator

CLOCKWORK_RECORD=/data/local/tmp/taqwa-clockwork-auto-time

# Whether the watch has the clockwork time sync that moves a set clock: Wear OS 3 and 4, below API 34.
has_clockwork_sync() { [ "$(wadb shell getprop ro.build.version.sdk | tr -d '\r')" -lt 34 ]; }

# The two values recorded on the watch, "<clockwork_auto_time> <auto_time>", or nothing.
clockwork_recorded() { { wadb shell cat "$CLOCKWORK_RECORD" 2> /dev/null || true; } | tr -d '\r'; }

# The watch's own time sync off, with the values found recorded the first time.
clockwork_off() {
    if [ -z "$(clockwork_recorded)" ]; then
        local clockwork automatic
        clockwork="$(wadb shell settings get global clockwork_auto_time | tr -d '\r')"
        automatic="$(wadb shell settings get global auto_time | tr -d '\r')"
        wadb shell "echo $clockwork $automatic > $CLOCKWORK_RECORD"
    fi
    wadb shell settings put global clockwork_auto_time 2
}

# Global setting $1 put back to $2 as found, or deleted if it was not set ("null").
put_back() {
    if [ "$2" = null ]; then
        wadb shell settings delete global "$1"
    else
        wadb shell settings put global "$1" "$2"
    fi
}

# The values recorded put back and the record gone; false when nothing was recorded.
clockwork_back() {
    local found
    found="$(clockwork_recorded)"
    [ -n "$found" ] || return 1
    put_back clockwork_auto_time "${found%% *}"
    put_back auto_time "${found##* }"
    wadb shell rm -f "$CLOCKWORK_RECORD"
}

if [ "${1:-}" = auto ]; then
    if ! { has_clockwork_sync && clockwork_back; }; then
        wadb shell settings put global auto_time 1
    fi
    wadb shell cmd alarm set-time "$(date +%s)000" > /dev/null
elif [ -n "${1:-}" ]; then
    at="$(epoch_of "$1")"
    if has_clockwork_sync; then clockwork_off; fi
    set_clock "$at"
else
    echo "usage: $0 HH:MM[:SS] | <epoch seconds> | auto" >&2
    exit 2
fi
wadb shell date | tr -d '\r'
