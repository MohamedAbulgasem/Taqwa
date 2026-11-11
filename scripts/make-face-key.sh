#!/usr/bin/env bash
# Creates the Taqwa watch face's release key, once. Mohamed runs it; no agent does.
#
#   scripts/make-face-key.sh             # ~/keys/taqwa-face.jks
#   scripts/make-face-key.sh /other/dir  # somewhere else
#
# The face is signed with a key of its own, never the app's (spec 3.1, Watch Face Push), and only
# by Mohamed: scripts/release-face.sh asks for this key's password at each release. So this asks for
# one password, twice, typed and never echoed, and writes it nowhere: there is no properties file,
# as there is for the upload key. It writes taqwa-face.jks (alias taqwa-face, RSA 4096, valid 10000
# days), refuses to overwrite one, and prints the certificate's SHA-256. That digest is public, not
# a secret: the Lead writes it into watchface/face-release-cert.sha256, the pin release-face.sh and
# release.sh hold every signed face to (plan 5 Task 17, M1).
#
# TAQWA_FACE_PASSWORD gives the password instead, for scripts/test-face-release.sh's scratch keys
# alone: it is refused for ~/keys and anything inside it, so the real key's password is only ever
# typed.
set -euo pipefail

DIR="${1:-$HOME/keys}"
case "$DIR" in /*) ;; *) DIR="$PWD/$DIR" ;; esac
KEYSTORE="$DIR/taqwa-face.jks"
cd "$(dirname "$0")/.."
ALIAS=taqwa-face

# Whether a path is ~/keys or inside it, through any link, whether or not it exists yet.
in_keys() {
    python3 -I -c 'import os, sys
keys = os.path.realpath(os.path.join(os.path.expanduser("~"), "keys"))
path = os.path.realpath(sys.argv[1])
sys.exit(0 if path == keys or path.startswith(keys + os.sep) else 1)' "$1"
}

if [ -n "${TAQWA_FACE_PASSWORD:-}" ] && { [ -z "${1:-}" ] || in_keys "$DIR"; }; then
    echo "TAQWA_FACE_PASSWORD is for test-face-release.sh's scratch keys: a key in ~/keys takes a typed password." >&2
    exit 1
fi

if [ -e "$KEYSTORE" ]; then
    echo "$KEYSTORE already exists; refusing to overwrite it." >&2
    exit 1
fi

if [ -n "${TAQWA_FACE_PASSWORD:-}" ]; then
    password="$TAQWA_FACE_PASSWORD"
else
    IFS= read -r -s -p "Password for the face key (12+ characters): " P1; echo
    IFS= read -r -s -p "The same password again: " P2; echo
    [ "$P1" = "$P2" ] || { echo "The two passwords differ; nothing written." >&2; exit 1; }
    password="$P1"
    unset P1 P2
fi
[ "${#password}" -ge 12 ] || { echo "Use at least 12 characters; nothing written." >&2; exit 1; }

# The keytool of the JDK Gradle uses: gradle.properties pins none on the Mac mini, so JAVA_HOME's,
# then PATH's.
JAVA_HOME_GRADLE="$(sed -n 's/^org.gradle.java.home=//p' gradle.properties)"
KEYTOOL="${JAVA_HOME_GRADLE:-${JAVA_HOME:-}}/bin/keytool"
[ -x "$KEYTOOL" ] || KEYTOOL=keytool

mkdir -p "$DIR"
chmod 700 "$DIR"
# The password reaches keytool through its environment alone, never its arguments.
TAQWA_FACE_KEY_PW="$password" "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass:env TAQWA_FACE_KEY_PW -keypass:env TAQWA_FACE_KEY_PW \
    -dname "CN=Taqwa watch face, O=Mohamed Abulgasem" 2> /dev/null
chmod 600 "$KEYSTORE"

# The SHA-256 of the certificate as DER, which is the digest apksigner prints for what it signs.
CERT_SHA256="$(TAQWA_FACE_KEY_PW="$password" "$KEYTOOL" -exportcert -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass:env TAQWA_FACE_KEY_PW 2> /dev/null | shasum -a 256 | cut -d' ' -f1)"
unset password

echo "Face key:  $KEYSTORE (alias $ALIAS, RSA 4096, valid 10000 days)"
echo
echo "Certificate SHA-256 (public, not a secret; paste it into chat for watchface/face-release-cert.sha256):"
echo "$CERT_SHA256"
echo
echo "Two warnings:"
echo "1. Back up $KEYSTORE and its password now, in two places (a password manager and an offline copy)."
echo "2. Unlike the upload key, nobody can reset a lost face key: Google does not hold it. A new key"
echo "   means every watch's Taqwa face is removed and added again, losing its wearer's style, colour and slots."
