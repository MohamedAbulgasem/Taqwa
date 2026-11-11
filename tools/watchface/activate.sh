#!/usr/bin/env bash
# Makes the Taqwa face the watch's face on emulator-5558, through the system's debug surface (the
# command Google's Watch Face Format samples use), with no taps:
#
#   tools/watchface/activate.sh
#
# The first time it adds the face to the favourites; after that it brings the same favourite back,
# with the style and the colour it was left with.
source "$(dirname "$0")/lib.sh"
need_emulator
wadb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation set-watchface --es watchFaceId "$FACE_PACKAGE" | tail -1
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
