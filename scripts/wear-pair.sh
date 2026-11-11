#!/usr/bin/env bash
# Brings the paired emulators back after a restart (plan 2 Task 7), and says whether the watch came
# back:
#
#   scripts/wear-pair.sh            # the phone headless
#   scripts/wear-pair.sh --window   # the phone in a window on this Mac
#
# It starts each emulator only if it is down (scripts/wear-emulator.sh, Taqwa_Wear_OS_6 on 5558,
# headless; scripts/phone-emulator.sh, Taqwa_Phone_Wear on 5562), then makes the pairing's two
# halves, as Android Studio's Pair Wearable does (seen on 31 October: Studio forwarded the phone's
# 5601 from a host port of its own, tcp:64922, and reversed the watch's 5601 to that port):
#
#   adb -s emulator-5562 forward tcp:5601 tcp:5601   # host 5601 to the phone's pairing port
#   adb -s emulator-5558 reverse tcp:5601 tcp:5601   # the watch's 5601 to host 5601
#
# A restart of either emulator loses its half (adb keeps neither across one), so both are made
# every time, through host 5601: each rebinds what is there, so a reverse Studio left pointing at
# its own port is pointed at this one, and the chain is whole whichever emulator restarted.
#
# Then it polls the phone's debug harness for its nodes (WatchHarnessReceiver's `nodes`) for up
# to two minutes, until the watch is listed nearby with Taqwa's watch app:
#
#   node <id> "<name>" nearby=true taqwa_watch=true
#
# Exit 0 with that line when it came back. Exit 1 when it did not: then stop, and the Lead asks
# Mohamed for Studio's Pair Wearable again (the one-time pairing is his; agents never sign in or
# pair). It needs the phone's debug build installed, which carries the harness. It touches only
# emulator-5558 and emulator-5562, always by serial, and stops neither.
#
# PAIR_TIMEOUT_SECONDS (120) and PAIR_POLL_SECONDS (5) set the wait, for its tests
# (scripts/test-pairing-scripts.sh).
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
PHONE=emulator-5562
WATCH=emulator-5558
TIMEOUT="${PAIR_TIMEOUT_SECONDS:-120}"
POLL="${PAIR_POLL_SECONDS:-5}"
HARNESS=(shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver
    -a world.taqwa.app.debug.WATCH --es cmd nodes)

phone_args=()
for arg in "$@"; do
    case "$arg" in
        --window) phone_args+=(--window) ;;
        *) echo "usage: $0 [--window]" >&2; exit 2 ;;
    esac
done

"$REPO/scripts/wear-emulator.sh"
"$REPO/scripts/phone-emulator.sh" ${phone_args[@]+"${phone_args[@]}"}

if [ -z "$("$ADB" -s "$PHONE" shell pm path world.taqwa.app | tr -d '\r')" ]; then
    echo "$PHONE has no Taqwa build, so no harness to ask. Install the debug build first:" >&2
    echo "  adb -s $PHONE install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk" >&2
    exit 1
fi

"$ADB" -s "$PHONE" forward tcp:5601 tcp:5601
"$ADB" -s "$WATCH" reverse tcp:5601 tcp:5601
echo "Forwarded tcp:5601 to $PHONE and reversed $WATCH's tcp:5601 to it;" \
    "asking the harness for its nodes for up to $TIMEOUT s"

deadline=$((SECONDS + TIMEOUT))
while :; do
    # An adb call that fails (the phone's adbd restarting, say) is asked again at the next poll.
    printed="$("$ADB" -s "$PHONE" "${HARNESS[@]}" 2>&1 | tr -d '\r')" || printed="adb failed: $printed"
    watch="$(grep -o 'node [^"]*"[^"]*" nearby=true taqwa_watch=true' <<< "$printed" | head -1 || true)"
    if [ -n "$watch" ]; then
        echo "The watch is back: $watch"
        exit 0
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "The watch did not come back nearby within $TIMEOUT s. Last answer:" >&2
        grep 'Broadcast completed' <<< "$printed" >&2 || echo "$printed" >&2
        echo "Stop here: the Lead asks Mohamed for Android Studio's Pair Wearable again" \
            "(Device Manager, Taqwa_Phone_Wear, Pair Wearable, Taqwa_Wear_OS_6)." >&2
        exit 1
    fi
    sleep "$POLL"
done
