#!/usr/bin/env bash
# check-bundle.sh's tests, with no device. The face is :watchface's debug build, signed with the
# throwaway test key (~/.android/taqwa-face-debug.keystore, scripts/face-debug-key.sh), which this
# test builds with Gradle; every watch app it checks is a small APK aapt2 links in a scratch
# directory: package world.taqwa.app, the face's version code plus 100000, a default_wf_token, and
# the face (or something else) as assets/default_watchface.apk. The one key it signs with is made in
# that scratch directory, and removed with it. They hold that check-bundle.sh
#   - passes a right bundle in an unsigned watch app, as every release build an agent makes is
#     (-Ptaqwa.unsignedRelease=true, plan 5 Task 3), and in a signed one;
#   - fails a face that is not signed, a face that is not an APK, a face signed with the app's own
#     key, a watch app with no face, a file that is not an APK, and a Push validator in the cache
#     that is not the pinned copy (under a scratch HOME), each with its reason;
#   - never stops early: in every case each of its checks prints its line, the summary is its last
#     line, its count is the number of FAIL lines, and it exits 0 on PASS and 1 otherwise;
#   - never prints the token.
# It fetches Google's pinned Push validator into ~/.cache/taqwa-watchface-tools once.
#
#   tools/watchface/check-bundle-test.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CHECK="$HERE/check-bundle.sh"

TEST_KEY="$HOME/.android/taqwa-face-debug.keystore"
[ -f "$TEST_KEY" ] || { echo "the test key $TEST_KEY is missing: run scripts/face-debug-key.sh" >&2; exit 2; }
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$REPO/local.properties" 2> /dev/null)}"
TOOLS="$(ls -d "$SDK"/build-tools/*/ 2> /dev/null | sort -V | tail -1)"
AAPT2="${TOOLS}aapt2"
APKSIGNER="${TOOLS}apksigner"
ANDROID_JAR="$(ls -d "$SDK"/platforms/android-*/android.jar 2> /dev/null | sort -V | tail -1)"
[ -x "$AAPT2" ] && [ -x "$APKSIGNER" ] && [ -f "$ANDROID_JAR" ] \
    || { echo "no aapt2, apksigner or android.jar under $SDK" >&2; exit 2; }
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-check-bundle-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }
indent() { sed 's/^/    /'; }

# The face: :watchface's debug build, signed with the test key.
(cd "$REPO" && ./gradlew -q :watchface:assembleDebug) | indent
FACE="$WORK/face-test-key.apk"
cp "$REPO/watchface/build/outputs/apk/debug/watchface-debug.apk" "$FACE"
cert_of() {
    { "$APKSIGNER" verify --print-certs "$1" 2> /dev/null || true; } \
        | sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u
}
FACE_CERT="$(cert_of "$FACE")"
[[ "$FACE_CERT" =~ ^[0-9a-f]{64}$ ]] || { echo "the debug face is not signed by one key" >&2; exit 2; }
FACE_CODE="$("$AAPT2" dump badging "$FACE" | sed -n "1s/.* versionCode='\([0-9]*\)'.*/\1/p")"

# The same face, unsigned: its entries zipped again, with no signing block. aapt2 still reads it.
UNSIGNED_FACE="$WORK/face-unsigned.apk"
mkdir "$WORK/face-entries"
unzip -q "$FACE" -d "$WORK/face-entries"
(cd "$WORK/face-entries" && zip -q -X -0 -r "$UNSIGNED_FACE" .)
NOT_AN_APK="$WORK/not-an-apk.apk"
printf 'not an APK\n' > "$NOT_AN_APK"

# The scratch key: the "app's" key, never a real one.
SCRATCH_KEY="$WORK/scratch.p12"
export TAQWA_CHECK_BUNDLE_TEST_PW="scratch-$$-$RANDOM"
"$KEYTOOL" -genkeypair -keystore "$SCRATCH_KEY" -storetype PKCS12 -alias scratch -keyalg RSA \
    -keysize 2048 -validity 2 -dname "CN=check-bundle-test scratch" \
    -storepass:env TAQWA_CHECK_BUNDLE_TEST_PW > /dev/null 2>&1
sign() { # apk, out
    "$APKSIGNER" sign --ks "$SCRATCH_KEY" --ks-pass env:TAQWA_CHECK_BUNDLE_TEST_PW --out "$2" "$1"
}
SCRATCH_FACE="$WORK/face-scratch-key.apk"
sign "$UNSIGNED_FACE" "$SCRATCH_FACE"
SCRATCH_CERT="$(cert_of "$SCRATCH_FACE")"

TOKEN="check-bundle-test-token-$$"
# watch_app <out.apk> [<face file>]: an unsigned watch app at the face's code plus 100000, with the
# token, and the file given as its default face (none without one).
watch_app() {
    local dir="$WORK/app-$(basename "$1" .apk)"
    mkdir -p "$dir/res/values" "$dir/assets"
    cat > "$dir/AndroidManifest.xml" << EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="world.taqwa.app"
    android:versionCode="$((FACE_CODE + 100000))" android:versionName="check-bundle-test">
    <application/>
</manifest>
EOF
    printf '<resources><string name="default_wf_token" translatable="false">%s</string></resources>\n' \
        "$TOKEN" > "$dir/res/values/face_token.xml"
    if [ "$#" -ge 2 ]; then cp "$2" "$dir/assets/default_watchface.apk"; fi
    "$AAPT2" compile --dir "$dir/res" -o "$dir/res.zip"
    "$AAPT2" link -I "$ANDROID_JAR" --manifest "$dir/AndroidManifest.xml" --min-sdk-version 30 \
        --target-sdk-version 30 -A "$dir/assets" -o "$1" "$dir/res.zip"
}

# check_case <name> <expected exit> <apk> <cert> <line>...: runs check-bundle.sh, and holds that it
# exits as expected, prints every line given whole, ends with its summary, counts its FAIL lines
# right, and never prints the token.
check_case() {
    local name="$1" want="$2" apk="$3" cert="$4"
    shift 4
    local out code=0 before="$FAILS"
    out="$("$CHECK" "$apk" "$cert" 2>&1)" || code=$?
    [ "$code" -eq "$want" ] || fail "$name: check-bundle.sh exits $code, not $want"
    local line
    for line in "$@"; do
        grep -qxF -- "$line" <<< "$out" || fail "$name: no line '$line'"
    done
    local last fails_seen
    last="$(tail -1 <<< "$out")"
    fails_seen="$(grep -c '^FAIL: ' <<< "$out" || true)"
    if [ "$want" -eq 0 ]; then
        [ "$last" = "check-bundle: PASS" ] && [ "$fails_seen" -eq 0 ] \
            || fail "$name: the last line is '$last', with $fails_seen FAIL lines, not 'check-bundle: PASS'"
    else
        [ "$last" = "check-bundle: $fails_seen failed" ] && [ "$fails_seen" -gt 0 ] \
            || fail "$name: the last line is '$last', not 'check-bundle: $fails_seen failed'"
    fi
    # The token is checked in every case, and the validator in every case with a face to run on.
    grep -qE '^(ok - |FAIL: )(the APK has no string/)?default_wf_token' <<< "$out" \
        || fail "$name: no line for the token: check-bundle.sh stopped early"
    if grep -q '^ok - the APK holds assets/default_watchface.apk' <<< "$out"; then
        grep -qE '^(ok - |FAIL: )validator-push-cli' <<< "$out" \
            || fail "$name: no line for the Push validator: check-bundle.sh stopped early"
    fi
    if grep -qF -- "$TOKEN" <<< "$out"; then fail "$name: the token is printed"; fi
    if grep -E 'generated token: ' <<< "$out" | grep -vqF '(withheld)'; then
        fail "$name: the validator's token is printed"
    fi
    if [ "$FAILS" -eq "$before" ]; then
        pass "$name"
    else
        echo "$out" | indent
    fi
}

TOKEN_OK="ok - default_wf_token is not empty (${#TOKEN} characters, withheld)"
NO_FACE_KEY="FAIL: the face is not signed"
VALIDATOR_OK="ok - validator-push-cli 1.1.0-alpha01 passes on the face for world.taqwa.app"

# 1. The finding of review round 1: a right bundle in an unsigned watch app, as an agent's release
# build is, passes. check-bundle.sh stopped with exit 1 and no reason at the app's signer.
watch_app "$WORK/unsigned-app.apk" "$FACE"
check_case "an unsigned watch app with its face passes" 0 "$WORK/unsigned-app.apk" "$FACE_CERT" \
    "ok - the face is world.taqwa.app.watchfacepush.taqwa" \
    "ok - the face's version code $FACE_CODE is the app's $((FACE_CODE + 100000)) minus 100000" \
    "ok - the face is signed with the certificate $FACE_CERT alone" \
    "ok - the face is signed with no key of the app's (the app is not signed)" \
    "$TOKEN_OK" "$VALIDATOR_OK"

# 2. The same app, signed with a key that is not the face's: passes.
sign "$WORK/unsigned-app.apk" "$WORK/signed-app.apk"
check_case "a signed watch app with its face passes" 0 "$WORK/signed-app.apk" "$FACE_CERT" \
    "ok - the face is signed with the certificate $FACE_CERT alone" \
    "ok - the face is signed with no key of the app's" \
    "$TOKEN_OK" "$VALIDATOR_OK"

# 3. An unsigned face in an unsigned app: its reason, and the checks after it still run.
watch_app "$WORK/unsigned-face-app.apk" "$UNSIGNED_FACE"
check_case "an unsigned face fails as not signed" 1 "$WORK/unsigned-face-app.apk" "$FACE_CERT" \
    "ok - the face is world.taqwa.app.watchfacepush.taqwa" \
    "$NO_FACE_KEY" "$TOKEN_OK" \
    "FAIL: validator-push-cli 1.1.0-alpha01 fails on the face for world.taqwa.app:"

# 4. A face that is no APK at all: unreadable, unsigned, refused by the validator.
watch_app "$WORK/garbage-face-app.apk" "$NOT_AN_APK"
check_case "a face that is not an APK fails as unreadable" 1 "$WORK/garbage-face-app.apk" "$FACE_CERT" \
    "FAIL: the face is 'unreadable', not world.taqwa.app.watchfacepush.taqwa" \
    "FAIL: the face's version code is unreadable, and the app's $((FACE_CODE + 100000)) minus 100000 is $FACE_CODE" \
    "$NO_FACE_KEY" "$TOKEN_OK" \
    "FAIL: validator-push-cli 1.1.0-alpha01 fails on the face for world.taqwa.app:"

# 5. A face signed with the app's own key: Watch Face Push refuses it.
watch_app "$WORK/scratch-face-app-unsigned.apk" "$SCRATCH_FACE"
sign "$WORK/scratch-face-app-unsigned.apk" "$WORK/scratch-face-app.apk"
check_case "a face signed with the app's key fails" 1 "$WORK/scratch-face-app.apk" "$SCRATCH_CERT" \
    "ok - the face is signed with the certificate $SCRATCH_CERT alone" \
    "FAIL: the face is signed with the app's key ($SCRATCH_CERT)" "$TOKEN_OK"

# 6. An unsigned app with no face: its reason, and the token still checked.
watch_app "$WORK/no-face-app.apk"
check_case "a watch app with no face fails" 1 "$WORK/no-face-app.apk" "$FACE_CERT" \
    "FAIL: the APK holds no assets/default_watchface.apk: the face is not bundled" "$TOKEN_OK"

# 7. A file that is no APK: its reason, and nothing stops early.
check_case "a file that is not an APK fails" 1 "$NOT_AN_APK" "$FACE_CERT" \
    "FAIL: the APK cannot be read as a zip: the face is not bundled" \
    "FAIL: the APK has no string/default_wf_token"

# 8. A Push validator in the cache that is not the pinned copy: that check fails with its reason,
# the others still counted, and the copy is left for a person to read (lib-validators.sh). The cache
# is a scratch HOME's, never the real one.
FAKE_JAR="$WORK/home/.cache/taqwa-watchface-tools/validator-push-cli-1.1.0-alpha01/validator-push-cli-1.1.0-alpha01.jar"
mkdir -p "$(dirname "$FAKE_JAR")"
printf 'not the pinned validator\n' > "$FAKE_JAR"
HOME="$WORK/home" check_case "a validator that is not the pinned copy fails that check alone" 1 \
    "$WORK/unsigned-app.apk" "$FACE_CERT" \
    "ok - the face is signed with the certificate $FACE_CERT alone" "$TOKEN_OK" \
    "FAIL: validator-push-cli 1.1.0-alpha01 could not be fetched, or is not the pinned copy (above)" \
    "check-bundle: 1 failed"
[ "$(cat "$FAKE_JAR")" = "not the pinned validator" ] || fail "the validator in the scratch cache was replaced"

if [ "$FAILS" -eq 0 ]; then
    echo "check-bundle-test: PASS"
else
    echo "check-bundle-test: $FAILS failed"
    exit 1
fi
