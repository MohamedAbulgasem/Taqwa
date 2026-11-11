#!/usr/bin/env bash
# P2.3b Step 6 under an Urdu watch (committed beside the report): for each style, the system language
# set to English for the face editor (choose.sh), then to Urdu (Pakistan) with SetLocale.java (the
# call the Settings' language picker makes), and urdu-shots.sh's two moments shot; English again at
# the end.
#   urdu-shots-system-ur.sh <repo> <out dir>
set -euo pipefail
REPO="$1"; OUT="$2"; HERE="$(cd "$(dirname "$0")" && pwd)"
setlocale() { adb -s emulator-5558 shell app_process -cp /data/local/tmp/taqwa-setlocale.dex / SetLocale "$1"; sleep 3; adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP; }
for style in arch ring sun moon; do
    setlocale en-US
    "$REPO/tools/watchface/choose.sh" style "$style" > /dev/null
    setlocale ur-PK
    STYLES="$style" NO_CHOOSE=1 "$HERE/urdu-shots.sh" "$REPO" "$OUT"
done
setlocale en-US
"$REPO/tools/watchface/choose.sh" style arch > /dev/null
