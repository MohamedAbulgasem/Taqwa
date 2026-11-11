#!/usr/bin/env bash
# Signs the Taqwa face's release with Mohamed's face key, at each release. Mohamed runs it, and types
# the key's password; no agent signs with his key.
#
#   scripts/release-face.sh
#
# 1. Builds :watchface's release (./gradlew :watchface:assembleRelease -Ptaqwa.unsignedRelease=true),
#    which Gradle leaves unsigned on purpose (watchface/build.gradle.kts). The face never reads the
#    flag, so it is the same APK at the same path with it or without it (scripts/test-release.sh,
#    theFlagLeavesTheFaceAsItIs); it keeps :androidApp and :wearApp, which every build of the root
#    project configures, from reading a keystore.properties beside them. Mohamed runs this at
#    STORE-CHECKLIST's step 3, before the Lead links one in for release.sh, and it would read none
#    even with one there.
# 2. Signs watchface-release-unsigned.apk into build/face-release/watchface-release-signed.apk with
#    apksigner, the newest build-tools', reading the password on stdin. On a terminal apksigner
#    asks for it itself (--ks-pass stdin), once, never echoed, and nothing asks again. It must be
#    the only one to ask: a password read here and piped on leaves apksigner's own prompt on the
#    screen, unanswered, and what is typed at it goes to the shell and its history. So its output
#    must be the terminal too: with stdout redirected, apksigner prompts down the redirect and
#    echoes what is typed, and this refuses to start. A password piped in is its first line
#    (--ks-pass file:/dev/stdin, never on a terminal, where it would echo), read with no prompt.
# 3. Verifies the signature, and that its one certificate's SHA-256 is the pin. Otherwise it deletes
#    what it signed and exits 1: build/face-release/ never holds a face the pin does not name.
# Then scripts/release.sh, the Lead's, bundles that file and makes its token (plan 5 Task 7).
#
# The key and the pin:
#   TAQWA_FACE_KS          the keystore, default ~/keys/taqwa-face.jks (scripts/make-face-key.sh)
#   TAQWA_FACE_ALIAS       its alias, default taqwa-face
#   TAQWA_FACE_CERT_FILE   the pin, default watchface/face-release-cert.sha256: the certificate's
#                          SHA-256, as make-face-key.sh printed it, which the Lead commits (M1)
#   TAQWA_FACE_KS_PASS_ENV the name of a variable holding the password (apksigner --ks-pass env:),
#                          for the throwaway test key and scratch keys alone: it is refused for a key
#                          in ~/keys, so the real key's password is only ever typed
set -euo pipefail
cd "$(dirname "$0")/.."

DEFAULT_KS="$HOME/keys/taqwa-face.jks"
KS="${TAQWA_FACE_KS:-$DEFAULT_KS}"
ALIAS="${TAQWA_FACE_ALIAS:-taqwa-face}"
CERT_FILE="${TAQWA_FACE_CERT_FILE:-watchface/face-release-cert.sha256}"
UNSIGNED=watchface/build/outputs/apk/release/watchface-release-unsigned.apk
OUT_DIR=build/face-release
OUT="$OUT_DIR/watchface-release-signed.apk"

refuse() { echo "release-face.sh: $*" >&2; exit 1; }
# Whether a path is inside ~/keys, through any link, whether or not it exists.
in_keys() {
    python3 -I -c 'import os, sys
keys = os.path.realpath(os.path.join(os.path.expanduser("~"), "keys"))
sys.exit(0 if os.path.realpath(sys.argv[1]).startswith(keys + os.sep) else 1)' "$1"
}

if [ -n "${TAQWA_FACE_KS_PASS_ENV:-}" ]; then
    [ -n "${TAQWA_FACE_KS:-}" ] || refuse "TAQWA_FACE_KS_PASS_ENV is for a test or scratch key named by TAQWA_FACE_KS, never the face key in ~/keys"
    ! in_keys "$KS" || refuse "TAQWA_FACE_KS_PASS_ENV is refused for a key in ~/keys: type the face key's password"
    [[ "$TAQWA_FACE_KS_PASS_ENV" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && [ -n "${!TAQWA_FACE_KS_PASS_ENV:-}" ] \
        || refuse "TAQWA_FACE_KS_PASS_ENV names no variable that holds a password"
    KS_PASS="env:$TAQWA_FACE_KS_PASS_ENV"
elif [ -t 0 ]; then
    [ -t 1 ] || refuse "run it on a terminal with its output on the terminal too, not piped or redirected: apksigner asks for the password there, and with its output elsewhere it would show what you type"
    KS_PASS=stdin
else
    KS_PASS=file:/dev/stdin
fi

[ -f "$CERT_FILE" ] \
    || refuse "no pin at $CERT_FILE: the Lead writes it from the SHA-256 make-face-key.sh printed (plan 5 Task 17, M1)"
PIN="$(tr -d '[:space:]' < "$CERT_FILE")"
[[ "$PIN" =~ ^[0-9a-f]{64}$ ]] || refuse "$CERT_FILE holds no certificate SHA-256 (64 lowercase hex digits)"
[ -f "$KS" ] || refuse "no face key at $KS: scripts/make-face-key.sh makes it, once"

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2> /dev/null)}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2> /dev/null | sort -V | tail -1)"
[ -n "$APKSIGNER" ] || refuse "no apksigner under $SDK/build-tools"

# Nothing signed before this run is left beside what this run signs, and on any failure from here
# on what it signed goes too.
rm -rf "$OUT_DIR"
signed=0
trap '[ "$signed" -eq 1 ] || rm -rf "$OUT_DIR"' EXIT

# Gradle never reads stdin, which holds the password when it is piped in.
./gradlew -q :watchface:assembleRelease -Ptaqwa.unsignedRelease=true < /dev/null
[ -f "$UNSIGNED" ] || refuse "the build left no $UNSIGNED"
mkdir -p "$OUT_DIR"

[ "$KS_PASS" != stdin ] || echo "apksigner asks for the face key's password ($KS) once; what you type is not shown." >&2
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "$KS_PASS" --v4-signing-enabled false \
    --out "$OUT" "$UNSIGNED" \
    || refuse "apksigner did not sign (a wrong password, alias or key?); nothing kept"

"$APKSIGNER" verify "$OUT" > /dev/null 2>&1 || refuse "the signed face does not verify; deleted"
certs="$("$APKSIGNER" verify --print-certs "$OUT" 2> /dev/null \
    | sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u)"
[ "$(grep -c . <<< "$certs")" -eq 1 ] || refuse "the signed face carries $(grep -c . <<< "$certs") certificates, not one; deleted"
[ "$certs" = "$PIN" ] \
    || refuse "the face is signed by $certs, not by the pinned $PIN ($CERT_FILE): the wrong key; deleted"

signed=1
echo "Signed face: $OUT ($(($(wc -c < "$OUT") / 1024)) KB)"
echo "Certificate SHA-256: $PIN, the pin in $CERT_FILE"
