#!/usr/bin/env bash
# Scratch: puts emulator-5558 back as P2.2 found it (found/): the found watch app and face APKs, the
# stored feed byte for byte, automatic time and zone, the battery at 100%, Arch Amber slots empty.
# Since plan 5 Tasks 4-5 the face comes inside the watch app (Watch Face Push) and is never installed
# by itself (errata p5): the found app brings it, and DEBUG_REBIND has it ask its sources again.
set -euo pipefail
REPO="$1"; F="$2"
T="$REPO/tools/watchface"
w() { adb -s emulator-5558 "$@"; }
"$T/choose.sh" style arch accent amber left empty right empty > /dev/null
w install -r "$F/world.taqwa.app-base.apk" > /dev/null
w shell settings put global auto_time 1
w shell settings put global auto_time_zone 1
w shell run-as world.taqwa.app sh -c "'cat > files/feed.json'" < "$F/feed.json"
w shell am force-stop world.taqwa.app
# the face asks its sources again
w shell am broadcast --include-stopped-packages -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver -a world.taqwa.app.wear.DEBUG_REBIND > /dev/null
sleep 3
w shell input keyevent KEYCODE_WAKEUP
perl -e 'alarm 20; exec @ARGV' adb -s emulator-5558 emu power capacity 100 > /dev/null || echo "the console did not answer the battery level in 20 s"
echo "restored"
