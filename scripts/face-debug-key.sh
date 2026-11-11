#!/usr/bin/env bash
# Creates the throwaway key the Taqwa watch face (:watchface) is signed with in its debug builds:
#
#   scripts/face-debug-key.sh
#
# The face is a Watch Face Format APK that must be signed with a key other than the watch app's
# (spec 3.1), so :watchface does not use the standard debug key the app builds with. This is the
# same kind of key the Android tools generate for every debug build, which is all it is for: the
# emulator and sideloading onto the owner's own watch. Its passwords are the well-known ones
# (store and key password `android`, alias `taqwaface`) and it is worth nothing. It lives in
# ~/.android, never in the repository, and nothing built with it can ship: the face's release key
# is a different one, kept with the release keystore, that only the owner creates.
#
# The script never overwrites a key that is already there. Gradle's debug build points at the file
# and stops with "run scripts/face-debug-key.sh" when it is missing (watchface/build.gradle.kts).
set -euo pipefail

KEYSTORE="${HOME}/.android/taqwa-face-debug.keystore"

# keytool is part of the JDK: with no JAVA_HOME it comes from the one inside Android Studio, as the
# other scripts do.
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JDK" ]; then
    export JAVA_HOME="$STUDIO_JDK"
fi
if [ -n "${JAVA_HOME:-}" ]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
else
    KEYTOOL="keytool"
fi

if [ -e "$KEYSTORE" ]; then
    echo "$KEYSTORE already exists; leaving it alone."
    exit 0
fi

mkdir -p "$(dirname "$KEYSTORE")"
"$KEYTOOL" -genkeypair \
    -keystore "$KEYSTORE" \
    -alias taqwaface \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Taqwa face debug"
echo "Created $KEYSTORE (alias taqwaface, password android)."
