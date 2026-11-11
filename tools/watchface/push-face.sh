#!/usr/bin/env bash
# The one way the Taqwa face reaches emulator-5558 since plan 5 Task 4: inside the watch app, as its
# default watch face, which the system's Watch Face Push service installs into Taqwa's one slot when
# the app is installed. Nothing installs world.taqwa.app.watchfacepush.taqwa by itself any more: a
# face installed beside Push's slot is not the face the slot holds.
#
#   tools/watchface/push-face.sh                  # the debug face (the test key), made active
#   tools/watchface/push-face.sh --inactive       # as the system leaves it: installed, not active
#   tools/watchface/push-face.sh <face.apk>       # a signed face APK built elsewhere (plan 1's tools)
#
# It builds :wearApp:assembleDebug, with -Ptaqwa.faceApk=<face.apk> when one is given; removes the
# face and the watch app, so that the install is a first install; installs the watch app; waits up
# to 120 s for the system to install the face; stores the sample feed again (feed.sh); and makes the
# face active (activate.sh) unless --inactive. It prints the face's version code and its installer,
# and exits 1 if the face never appears.
#
# A first install loses the face's style, colour and slots, and the watch app's own memory (its feed,
# its last ask, the phone's fingerprint): pass.sh chooses the face's again, and a paired phone sends
# its feed again at the watch's next handshake.
source "$(dirname "$0")/lib.sh"
usage() { echo "usage: $0 [--inactive] [face.apk]" >&2; exit 2; }
INACTIVE=0
FACE=""
for arg in "$@"; do
    case "$arg" in
        --inactive) INACTIVE=1 ;;
        -*) usage ;;
        *) [ -z "$FACE" ] || usage; FACE="$arg" ;;
    esac
done
GRADLE_ARGS=(:wearApp:assembleDebug)
if [ -n "$FACE" ]; then
    [ -f "$FACE" ] || { echo "no face APK at $FACE" >&2; exit 1; }
    GRADLE_ARGS+=("-Ptaqwa.faceApk=$(cd "$(dirname "$FACE")" && pwd)/$(basename "$FACE")")
fi
need_emulator

(cd "$REPO" && ./gradlew -q "${GRADLE_ARGS[@]}")
WEAR_APK="$REPO/wearApp/build/outputs/apk/debug/wearApp-debug.apk"
listing="$(unzip -Z1 "$WEAR_APK")"
if ! grep -qx 'assets/default_watchface.apk' <<< "$listing"; then
    echo "the watch app carries no face (the build says why: 'the face is not bundled')" >&2
    exit 1
fi

wadb uninstall "$FACE_PACKAGE" > /dev/null 2>&1 || true
wadb uninstall "$APP_PACKAGE" > /dev/null 2>&1 || true
wadb install "$WEAR_APK" > /dev/null
echo "installed the watch app on $SERIAL; waiting up to 120 s for the system to install its face"
appeared=""
for _ in $(seq 1 120); do
    packages="$(wadb shell pm list packages | tr -d '\r')"
    if grep -qx "package:$FACE_PACKAGE" <<< "$packages"; then appeared=1; break; fi
    sleep 1
done
if [ -z "$appeared" ]; then
    echo "the system never installed the face ($FACE_PACKAGE) within 120 s" >&2
    exit 1
fi

"$HERE/feed.sh" > /dev/null
if [ "$INACTIVE" -eq 0 ]; then "$HERE/activate.sh" > /dev/null; fi
info="$(wadb shell dumpsys package "$FACE_PACKAGE" | tr -d '\r')"
code="$(sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' <<< "$info" | head -1)"
installer="$(sed -n 's/.*installerPackageName=\([^ ]*\).*/\1/p' <<< "$info" | head -1)"
echo "the face is on $SERIAL: version code ${code:-unknown}, installed by ${installer:-nobody named}$([ "$INACTIVE" -eq 1 ] && echo ', not made active' || echo ', made active')"
