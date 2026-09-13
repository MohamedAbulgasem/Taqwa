#!/usr/bin/env bash
# Builds the store artefacts for the version in androidApp/build.gradle.kts:
#
#   androidApp/build/outputs/bundle/release/androidApp-release.aab  — upload this to Google Play
#   androidApp/build/outputs/apk/release/androidApp-release.apk     — the same build as an APK,
#                                                                     for a phone check first
#
# Both are signed with the upload key named in keystore.properties (see
# keystore.properties.example). Run scripts/test.sh and scripts/ios-build.sh before this, and
# scripts/bump-version.sh once per release. The R8 mapping file rides inside the bundle
# (BUNDLE-METADATA/), so Play de-obfuscates crash reports without a separate upload.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f keystore.properties ]; then
    echo "keystore.properties is missing: copy keystore.properties.example and fill it in." >&2
    exit 1
fi

./gradlew -q :androidApp:bundleRelease :androidApp:assembleRelease

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
AAB=androidApp/build/outputs/bundle/release/androidApp-release.aab
APK=androidApp/build/outputs/apk/release/androidApp-release.apk

echo "Signed by:"
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -E "SHA-256|DN:" | sed 's/^/  /'
echo
"$BUILD_TOOLS/aapt2" dump badging "$APK" | grep -E "^package:|^targetSdkVersion" | sed 's/^/  /'
echo
ls -la "$AAB" "$APK" | awk '{printf "  %6.1f MB  %s\n", $5/1048576, $9}'
