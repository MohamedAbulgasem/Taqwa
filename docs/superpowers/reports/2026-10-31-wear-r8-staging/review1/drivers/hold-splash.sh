#!/usr/bin/env bash
# The splash held still and shot (P5.3 fix round 1), under the wear-5558 lock the caller holds:
#
#   hold-splash.sh <out.png>
#
# A debuggable build of the watch app must be installed on emulator-5558. It is started with
# `am start -D`, so its process waits for a debugger behind the system's splash window; jdb then
# attaches with a breakpoint on TaqwaWearApp.onCreate, which takes the "Waiting For Debugger"
# dialog away and stops the app before its first frame, so the splash alone is on screen. After
# the shot the breakpoint is cleared, jdb leaves, the forward is removed, the app is stopped and
# the debug app cleared, and the face is put in front again.
set -u
OUT=${1:?usage: hold-splash.sh <out.png>}
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
w() { "$ADB" -s emulator-5558 "$@"; }
PORT=8711
w shell am force-stop world.taqwa.app
w shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null
sleep 2; w shell input keyevent KEYCODE_WAKEUP; sleep 1
w shell am start -D -n world.taqwa.app/world.taqwa.app.wear.app.WearMainActivity | tr -d '\r' | head -1
sleep 2
PID=$(w shell pidof world.taqwa.app | tr -d '\r')
w forward tcp:$PORT jdwp:"$PID" > /dev/null
( echo "stop in world.taqwa.app.wear.TaqwaWearApp.onCreate"; sleep 8
  echo "clear world.taqwa.app.wear.TaqwaWearApp.onCreate"; echo "exit" ) \
    | perl -e 'alarm 20; exec @ARGV' jdb -attach localhost:$PORT > "${OUT%.png}.jdb.txt" 2>&1 &
J=$!
sleep 4
w exec-out screencap -p > "$OUT"
wait "$J"
grep -q "Breakpoint hit" "${OUT%.png}.jdb.txt" && echo "held at TaqwaWearApp.onCreate" || echo "NOT HELD: see ${OUT%.png}.jdb.txt" >&2
w forward --remove tcp:$PORT
w shell am force-stop world.taqwa.app
w shell am clear-debug-app
w shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null
python3 -I "$(dirname "$0")/disc.py" "$OUT"
