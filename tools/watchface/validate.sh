#!/usr/bin/env bash
# Runs Google's two validators on the Taqwa face, as Watch Face Push will:
#
#   tools/watchface/validate.sh                # builds :watchface's debug and release APKs first
#   tools/watchface/validate.sh --no-build     # validates the APKs already built
#   tools/watchface/validate.sh --schema-only  # wff-validator alone, on watchface.xml (no APK, no
#                                              # key: what CI runs)
#
#   1. wff-validator (github.com/google/watchface): watchface.xml against the Watch Face Format
#      schema of the version the manifest declares (com.google.wear.watchface.format.version). The
#      emulator's runtime ignores an element it does not expect, without a word (a Variant inside a
#      Font or a Stroke, say), so only the schema shows it.
#   2. validator-push-cli (Google Maven, com.google.android.wearable.watchface.validator): the
#      checks Watch Face Push makes before it installs a face (the APK's contents, its manifest and
#      package name, the format version, the memory footprint and the signature), run on the debug
#      APK, which is signed with the throwaway test key, and on the release APK. A release APK is
#      unsigned until the owner signs it, and on it the signature check alone would fail, so the
#      release is checked as a scratch copy in the cache signed with the same test key: every
#      other check reads the release's own contents. The owner's key never comes near this script.
#
# The validators are dev tools, fetched once into ~/.cache/taqwa-watchface-tools (never into the
# repository) and checked against the SHA-256 pinned in lib-validators.sh, which says why a pin and
# not a tag. Neither is run from inside the repository. Exits non-zero if either validator fails.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
# The pinned validators, their fetch and CLIENT_PACKAGE, shared with push-token.sh.
source "$REPO/tools/watchface/lib-validators.sh"
CACHE="$VALIDATOR_CACHE"
XML="$REPO/watchface/src/main/res/raw/watchface.xml"
MANIFEST="$REPO/watchface/src/main/AndroidManifest.xml"
APK="$REPO/watchface/build/outputs/apk/debug/watchface-debug.apk"
RELEASE_APK="$REPO/watchface/build/outputs/apk/release/watchface-release-unsigned.apk"
# The throwaway test key the debug build is signed with (watchface/build.gradle.kts), and the
# scratch copy of the release it signs for the Push check.
TEST_KEY="$HOME/.android/taqwa-face-debug.keystore"
RELEASE_COPY="$CACHE/scratch/watchface-release-test-signed.apk"

SCHEMA_ONLY=0
if [ "${1:-}" = --schema-only ]; then SCHEMA_ONLY=1; fi
if [ "$SCHEMA_ONLY" -eq 0 ]; then need_push_validator; fi
need_wff_validator

# Only the face is built: its debug is signed with the test key by :watchface itself, and its release
# is left unsigned. -Ptaqwa.unsignedRelease=true keeps :androidApp and :wearApp, which Gradle
# configures too, from reading a keystore.properties beside them, in scripts/release.sh's dry run
# (whose face gate runs this) as anywhere; the store build's own builds are release.sh's, which keep
# their signing.
if [ "${1:-}" != --no-build ] && [ "$SCHEMA_ONLY" -eq 0 ]; then
    (cd "$REPO" && ./gradlew -q :watchface:assembleDebug :watchface:assembleRelease -Ptaqwa.unsignedRelease=true)
fi

# The format version the manifest declares: the property's value, the line after its name.
VERSION="$(grep -A1 'com.google.wear.watchface.format.version' "$MANIFEST" | grep -o 'android:value="[0-9]*"' | grep -o '[0-9]*')"
if [ -z "$VERSION" ]; then
    echo "no com.google.wear.watchface.format.version in $MANIFEST" >&2
    exit 1
fi

status=0
# Both run from the cache directory, so that nothing they write lands in the repository.
cd "$CACHE"
echo "== wff-validator, format version $VERSION"
if ! wff_out="$(java -jar "$WFF_JAR" "$VERSION" "$XML" 2>&1)"; then status=1; fi
echo "$wff_out" | sed 's/^/   /'
if ! grep -q 'PASSED' <<< "$wff_out" || grep -q -E 'SEVERE|FAILED' <<< "$wff_out"; then status=1; fi

if [ "$SCHEMA_ONLY" -eq 1 ]; then
    if [ "$status" -eq 0 ]; then echo "wff-validator passes"; else echo "wff-validator failed" >&2; fi
    exit "$status"
fi

push() { # apk, what it is
    local out
    echo "== validator-push-cli $PUSH_VERSION, $2, for $CLIENT_PACKAGE"
    if ! out="$(run_push_validator "$1")"; then status=1; fi
    # The token a successful run prints is for Watch Face Push's install call, not for a log.
    echo "$out" | sed -E 's/(generated token: ).*/\1(withheld)/; s/^/   /'
    if ! grep -q 'Validation is successful' <<< "$out"; then status=1; fi
}
push "$APK" "$(basename "$APK")"

# The release: a scratch copy signed with the test key, with the build-tools' newest apksigner.
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$REPO/local.properties" 2>/dev/null)}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
if [ ! -f "$RELEASE_APK" ] || [ ! -f "$TEST_KEY" ] || [ -z "$APKSIGNER" ]; then
    echo "the release APK, the test key or apksigner is missing, so the release is not checked" >&2
    status=1
else
    mkdir -p "$(dirname "$RELEASE_COPY")"
    rm -f "$RELEASE_COPY"
    "$APKSIGNER" sign --ks "$TEST_KEY" --ks-key-alias taqwaface --ks-pass pass:android \
        --key-pass pass:android --out "$RELEASE_COPY" "$RELEASE_APK"
    rm -f "$RELEASE_COPY.idsig"
    push "$RELEASE_COPY" "$(basename "$RELEASE_APK") (a scratch copy signed with the test key)"
    rm -f "$RELEASE_COPY"
fi

if [ "$status" -eq 0 ]; then echo "both validators pass"; else echo "a validator failed" >&2; fi
exit "$status"
