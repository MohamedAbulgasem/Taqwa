#!/usr/bin/env bash
# Checks the Taqwa face a built watch app carries as its default watch face (plan 5 Task 4):
#
#   tools/watchface/check-bundle.sh <wear.apk> <face-cert-sha256>
#
#   - the APK holds assets/default_watchface.apk (FaceBundleTask, wearApp/build.gradle.kts);
#   - that face is world.taqwa.app.watchfacepush.taqwa, and its version code is the watch app's
#     minus 100000 (spec 2.1: the watch app is the phone's code + 100000, the face the phone's code),
#     so that every update of the app updates the face in its slot;
#   - the face is signed with the certificate <face-cert-sha256> (the SHA-256 of the signing
#     certificate, as apksigner prints it, colons and case ignored), and with no key of the watch
#     app's own: Watch Face Push refuses a face signed like the app that pushes it;
#   - the app's default_wf_token is not empty (aapt2 dump resources), so the system has a token to
#     install the face with;
#   - Google's pinned validator-push-cli (lib-validators.sh) passes on the face for world.taqwa.app.
#
# It builds nothing and changes nothing. It exits 0 when every check passes, 1 when any fails (each
# failure on its own line, and every check run whatever those before it found: an unsigned app, as
# every release build an agent makes is, an unsigned face, or a file that is no APK stops nothing),
# and 2 on wrong arguments. The token is never printed. check-bundle-test.sh is its test.
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <wear.apk> <face-cert-sha256>" >&2
    exit 2
fi
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
source "$REPO/tools/watchface/lib-validators.sh"

FACE_PACKAGE=world.taqwa.app.watchfacepush.taqwa
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$REPO/local.properties" 2> /dev/null)}"
TOOLS="$(ls -d "$SDK"/build-tools/*/ 2> /dev/null | sort -V | tail -1)"
AAPT2="${TOOLS}aapt2"
APKSIGNER="${TOOLS}apksigner"
[ -x "$AAPT2" ] && [ -x "$APKSIGNER" ] || { echo "no aapt2 or apksigner under $SDK/build-tools" >&2; exit 2; }

WEAR="$1"
WANT_CERT="$(tr -d ':[:space:]' <<< "$2" | tr '[:upper:]' '[:lower:]')"
[[ "$WANT_CERT" =~ ^[0-9a-f]{64}$ ]] || { echo "check-bundle.sh: '$2' is not a SHA-256 digest" >&2; exit 2; }
[ -f "$WEAR" ] || { echo "check-bundle.sh: no APK at $WEAR" >&2; exit 1; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-check-bundle.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

# The helpers print nothing for a file their tool cannot read, and never fail: under set -e and
# pipefail an aapt2 or apksigner that exits 1 (an unsigned APK, a face that is no APK) would end
# the script there, with no FAIL line and no summary (P5.4 review round 1). Every check below
# reads an empty answer as its failure, and says so.
version_code() { { "$AAPT2" dump badging "$1" 2> /dev/null || true; } | sed -n "1s/.* versionCode='\([0-9]*\)'.*/\1/p"; }
package_of() { { "$AAPT2" dump badging "$1" 2> /dev/null || true; } | sed -n "1s/^package: name='\([^']*\)'.*/\1/p"; }
# The SHA-256 digests of every signer's certificate, one a line, lower case; nothing when unsigned,
# as every release build an agent makes is (-Ptaqwa.unsignedRelease=true).
certs_of() {
    { "$APKSIGNER" verify --print-certs "$1" 2> /dev/null || true; } \
        | sed -n -E 's/^(Signer #[0-9]+|V[0-9.]+ Signer|Source Stamp Signer): certificate SHA-256 digest: //p; s/^Signer #[0-9]+ certificate SHA-256 digest: //p' \
        | tr '[:upper:]' '[:lower:]' | tr -d ':' | sort -u
}

echo "APK: $WEAR"

# 1. The face is in the APK, where the system looks for a default watch face.
FACE="$WORK/default_watchface.apk"
# The listing is read whole first: grep -q leaving a pipe early would kill unzip (SIGPIPE), and
# pipefail would turn a face that is there into one that is not.
if ! LISTING="$(unzip -Z1 "$WEAR" 2> /dev/null)"; then
    fail "the APK cannot be read as a zip: the face is not bundled"
elif grep -qx 'assets/default_watchface.apk' <<< "$LISTING"; then
    if unzip -p "$WEAR" assets/default_watchface.apk > "$FACE" 2> /dev/null; then
        pass "the APK holds assets/default_watchface.apk ($(wc -c < "$FACE" | tr -d ' ') bytes)"
    else
        rm -f "$FACE"
        fail "the APK's assets/default_watchface.apk cannot be read out of it"
    fi
else
    fail "the APK holds no assets/default_watchface.apk: the face is not bundled"
fi

if [ -s "$FACE" ]; then
    # 2. The face's package, and its version code against the app's.
    face_package="$(package_of "$FACE")"
    if [ "$face_package" = "$FACE_PACKAGE" ]; then
        pass "the face is $FACE_PACKAGE"
    else
        fail "the face is '${face_package:-unreadable}', not $FACE_PACKAGE"
    fi
    face_code="$(version_code "$FACE")"
    wear_code="$(version_code "$WEAR")"
    if [ -n "$face_code" ] && [ -n "$wear_code" ] && [ "$face_code" -eq $((wear_code - 100000)) ]; then
        pass "the face's version code $face_code is the app's $wear_code minus 100000"
    else
        fail "the face's version code is ${face_code:-unreadable}, and the app's ${wear_code:-unreadable} minus 100000 is $(( ${wear_code:-100000} - 100000 ))"
    fi

    # 3. The face's signer: the one asked for, and never the app's own.
    face_certs="$(certs_of "$FACE")"
    wear_certs="$(certs_of "$WEAR")"
    if [ -z "$face_certs" ]; then
        fail "the face is not signed"
    elif [ "$face_certs" = "$WANT_CERT" ]; then
        pass "the face is signed with the certificate $WANT_CERT alone"
    else
        fail "the face is signed with $(xargs <<< "$face_certs"), not $WANT_CERT"
    fi
    if [ -n "$face_certs" ] && grep -qxF -f <(echo "$face_certs") <(echo "$wear_certs"); then
        fail "the face is signed with the app's key ($(grep -xF -f <(echo "$face_certs") <(echo "$wear_certs") | head -1))"
    elif [ -n "$face_certs" ] && [ -z "$wear_certs" ]; then
        # An agent's unsigned build: the app's key can be compared only once the app is signed, as
        # release.sh signs it (plan 5 Task 7 runs this script there).
        pass "the face is signed with no key of the app's (the app is not signed)"
    elif [ -n "$face_certs" ]; then
        pass "the face is signed with no key of the app's"
    fi
fi

# 4. The token the system installs the face with: present, and never shown.
RESOURCES="$("$AAPT2" dump resources "$WEAR" 2> /dev/null || true)"
token_line="$(grep -A1 -E ' string/default_wf_token$' <<< "$RESOURCES" | sed -n 2p || true)"
token_value="$(sed -E 's/^[[:space:]]*\([^)]*\) "(.*)"[[:space:]]*$/\1/' <<< "$token_line")"
if [ -z "$token_line" ]; then
    fail "the APK has no string/default_wf_token"
elif [ -z "$token_value" ] || [ "$token_value" = "$token_line" ]; then
    fail "default_wf_token is empty"
else
    pass "default_wf_token is not empty (${#token_value} characters, withheld)"
fi

# 5. Google's Push validator on the face itself, for the package that pushes it.
if [ -s "$FACE" ]; then
    # The fetch runs in a shell of its own, with set -e of its own, so that one that fails (no
    # network, or a copy that is not the pinned one, where fetch exits) is this check's failure, with
    # the checks before it still counted. (A subshell inside `if` would run with set -e off, and a
    # failed download would be moved into the cache.)
    if ! bash -c 'set -euo pipefail; source "$1"; need_push_validator' check-bundle "$VALIDATORS_LIB"; then
        fail "validator-push-cli $PUSH_VERSION could not be fetched, or is not the pinned copy (above)"
    else
        out="$(run_push_validator "$FACE")" || true
        if grep -qx 'Validation is successful' <<< "$out"; then
            pass "validator-push-cli $PUSH_VERSION passes on the face for $CLIENT_PACKAGE"
        else
            fail "validator-push-cli $PUSH_VERSION fails on the face for $CLIENT_PACKAGE:"
            sed -E 's/(generated token: ).*/\1(withheld)/; s/^/    /' <<< "$out"
        fi
    fi
fi

if [ "$FAILS" -eq 0 ]; then
    echo "check-bundle: PASS"
else
    echo "check-bundle: $FAILS failed"
    exit 1
fi
