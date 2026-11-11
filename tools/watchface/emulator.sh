#!/usr/bin/env bash
# Boots the Wear emulator the face is verified on, or reuses it when it is already up:
#
#   tools/watchface/emulator.sh            # headless (screenshots with adb exec-out screencap)
#   tools/watchface/emulator.sh --window   # in a window on this Mac
#
# scripts/wear-emulator.sh does the starting (Taqwa_Wear_OS_6 on port 5558, created the first time)
# and returns once the watch has booted. This then takes the watch off the charger, whose screen
# would otherwise cover the face, and wakes it. Stop it, when it has to stop, with
#
#   adb -s emulator-5558 emu kill
#
# and never by a process name.
source "$(dirname "$0")/lib.sh"

"$REPO/scripts/wear-emulator.sh" "$@"
need_emulator
wadb emu power ac off > /dev/null
wadb shell input keyevent KEYCODE_WAKEUP
echo "$SERIAL: off the charger, awake, $(wadb shell date | tr -d '\r')"
