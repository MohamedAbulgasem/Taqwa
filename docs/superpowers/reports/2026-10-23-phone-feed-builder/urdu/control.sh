#!/usr/bin/env bash
# P2.3b Step 6's control (committed beside the report): the face before plan 2 Task 2 (the APK found
# installed on emulator-5558, its li.xml ff28cd5a's watchface.xml, the fonts before v2) shot with the
# phone's first Urdu feed at 16:47 on Arch, to show which forms the runtime's shaping language gives
# the old fonts: under English the SemiBold's and the Light's Persian forms, under Urdu the
# SemiBold's own Urdu forms (its URD locl), the Light's still Persian (no locl in its cmap's glyphs).
#   control.sh <repo> <old face apk> <out dir> <name>
set -euo pipefail
REPO="$1"; OLD="$2"; OUT="$3"; NAME="$4"
T="$REPO/tools/watchface"
"$T/choose.sh" style arch > /dev/null
"$T/clock.sh" 16:46 > /dev/null
"$T/store.sh" "$REPO/watchcore/vectors/phone/phone-capetown-ur24.json" > /dev/null
adb -s emulator-5558 install -r "$OLD" > /dev/null    # the old face asks its sources again
sleep 3
adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP
KEEP_FEED=1 "$T/shoot.sh" "$OUT" "$NAME" 16:47 awake
