#!/usr/bin/env bash
# Creates the Google Play upload key and the keystore.properties that makes scripts/release.sh
# sign with it.
#
#   scripts/make-upload-key.sh             # keystore in ~/keys, keystore.properties in the repo
#   scripts/make-upload-key.sh /other/dir  # keystore somewhere else
#
# Asks for one password, typed and never echoed. It ends up in exactly two places: inside the
# keystore and in keystore.properties (git-ignored, mode 600). With Play App Signing, which is on
# for every new app, this is only the UPLOAD key: Google keeps the key that signs what phones
# install, and a lost upload key can be reset through Play Console support. Back up the .jks and
# the password in two places anyway; a reset costs a support round and days.
set -euo pipefail
cd "$(dirname "$0")/.."

DIR="${1:-$HOME/keys}"
KEYSTORE="$DIR/taqwa-upload.jks"
ALIAS=taqwa-upload
PROPERTIES="${TAQWA_KEYSTORE_PROPERTIES:-keystore.properties}"

for f in "$KEYSTORE" "$PROPERTIES"; do
    if [ -e "$f" ]; then
        echo "$f already exists; refusing to overwrite it." >&2
        exit 1
    fi
done

if [ -z "${TAQWA_UPLOAD_PASSWORD:-}" ]; then
    read -r -s -p "Password for the upload key (12+ characters, no backslash): " P1; echo
    read -r -s -p "The same password again: " P2; echo
    [ "$P1" = "$P2" ] || { echo "The two passwords differ; nothing written." >&2; exit 1; }
    TAQWA_UPLOAD_PASSWORD="$P1"
fi
case "$TAQWA_UPLOAD_PASSWORD" in
    *\\*) echo "A backslash would be mangled by the .properties format; pick another password." >&2; exit 1 ;;
esac
[ "${#TAQWA_UPLOAD_PASSWORD}" -ge 12 ] || { echo "Use at least 12 characters; nothing written." >&2; exit 1; }
export TAQWA_UPLOAD_PASSWORD

# The keytool that ships with the JDK gradle uses (gradle.properties pins it); PATH as a fallback.
JAVA_HOME_GRADLE="$(sed -n 's/^org.gradle.java.home=//p' gradle.properties)"
KEYTOOL="$JAVA_HOME_GRADLE/bin/keytool"
[ -x "$KEYTOOL" ] || KEYTOOL=keytool

mkdir -p "$DIR"
chmod 700 "$DIR"
"$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
    -validity 10000 -storepass:env TAQWA_UPLOAD_PASSWORD -keypass:env TAQWA_UPLOAD_PASSWORD \
    -dname "CN=Taqwa upload key, O=Mohamed Abulgasem" 2>/dev/null
chmod 600 "$KEYSTORE"

umask 077
cat > "$PROPERTIES" <<PROPS
storeFile=$KEYSTORE
storePassword=$TAQWA_UPLOAD_PASSWORD
keyAlias=$ALIAS
keyPassword=$TAQWA_UPLOAD_PASSWORD
PROPS

echo "Upload key:      $KEYSTORE"
echo "Signing config:  $PROPERTIES (git-ignored)"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env TAQWA_UPLOAD_PASSWORD -alias "$ALIAS" 2>/dev/null \
    | grep -E "SHA256:|Valid from" | sed 's/^[[:space:]]*/  /'
echo
echo "Now back up $KEYSTORE and the password in two places (password manager + an offline copy)."
