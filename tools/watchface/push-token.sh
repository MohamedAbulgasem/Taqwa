#!/usr/bin/env bash
# Makes the Watch Face Push validation token of a signed face APK, for the Taqwa watch app:
#
#   tools/watchface/push-token.sh <signed.apk> <token-file>
#
# Runs Google's pinned validator-push-cli (lib-validators.sh) on the APK with
# --package_name=world.taqwa.app, the package that pushes the face, and writes the token it issues
# into <token-file>, the token alone, with no newline. The token is made from this very APK: the watch
# app bundles it beside the face it was made from (plan 5 Task 4), and the system refuses the face
# with any other. It is never printed: what the validator says is shown with the token withheld.
#
# Exits 1 unless the validator says "Validation is successful" and issues a token, as on an
# unsigned APK, whose signature check fails. <token-file> is emptied first, so a failed run leaves
# it empty and never a token from before. Exits 2 on wrong arguments.
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <signed.apk> <token-file>" >&2
    exit 2
fi
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
source "$REPO/tools/watchface/lib-validators.sh"

TOKEN_FILE="$2"
: > "$TOKEN_FILE"
if [ ! -f "$1" ]; then
    echo "push-token.sh: no APK at $1" >&2
    exit 1
fi
# The validator wants an absolute path, and runs from the cache.
APK="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"

need_push_validator
out="$(run_push_validator "$APK")" || true
token="$(sed -n 's/^No failing checks detected, generated token: //p' <<< "$out" | head -1 | tr -d '\r')"

# What the validator said, the token withheld wherever it appears.
shown="$(sed -E 's/(generated token: ).*/\1(withheld)/' <<< "$out")"
if [ -n "$token" ]; then shown="${shown//"$token"/(withheld)}"; fi
echo "== validator-push-cli $PUSH_VERSION, $(basename "$APK"), for $CLIENT_PACKAGE"
sed 's/^/   /' <<< "$shown"

if ! grep -qx 'Validation is successful' <<< "$out" || [ -z "$token" ]; then
    echo "push-token.sh: the validator issued no token for $(basename "$APK"); $TOKEN_FILE is empty" >&2
    exit 1
fi
printf '%s' "$token" > "$TOKEN_FILE"
echo "push-token.sh: the token for $(basename "$APK") is in $TOKEN_FILE (withheld here)"
