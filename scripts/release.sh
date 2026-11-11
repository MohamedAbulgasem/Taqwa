#!/usr/bin/env bash
# Builds and checks the store artefacts of the version in androidApp/build.gradle.kts: the phone's
# and the Galaxy watch app's together, with the Taqwa face inside the watch app (plan 5 Task 7).
#
#   scripts/release.sh                          the store build: the Lead's alone
#   TAQWA_RELEASE_DRY_RUN=1 scripts/release.sh  the dry run: the same path, with no upload key
#
# The store build's outputs, signed with the upload key named in keystore.properties:
#   androidApp/build/outputs/bundle/release/androidApp-release.aab  — upload this to Google Play
#   androidApp/build/outputs/apk/release/androidApp-release.apk     — the same build as an APK, for a phone check
#   wearApp/build/outputs/bundle/release/wearApp-release.aab        — the watch app's, on the same Play listing
#   wearApp/build/outputs/apk/release/wearApp-release.apk           — the same build as an APK, for a watch check
# Upload only after "release.sh: PASS". A run that fails from step 4 on removes all four, so a
# bundle that failed a check (a face signed with the app's key, a permission a library added, a
# token for another face) is never left at those paths.
#
# In order, stopping at the first failure:
#   1. scripts/check-versions.sh, and the prayer-time stamps (spec §5, ruling R86);
#   2. scripts/face-gate.sh;
#   3. the signed face, build/face-release/watchface-release-signed.apk, which Mohamed signs with
#      scripts/release-face.sh: it exists, its one certificate is the pin, and it is the current
#      face (:watchface:assembleRelease is built again, and every zip entry outside META-INF/ has
#      the same name and CRC-32 in both);
#   4. the four builds, with -Ptaqwa.storeRelease=true and -Ptaqwa.faceApk=<the signed face>, so the
#      watch app carries that face as its default face, with the token push-token.sh makes from it;
#   5. the checks, every one run, whatever those before it found: tools/watchface/check-bundle.sh on
#      the Wear APK; tools/wear-release-check.sh release; the phone's merged permissions are exactly
#      androidApp/permissions.txt; the Wear APK's certificate is the phone APK's, and each AAB's its
#      APK's; aapt2's badging gives the phone N (C), the Wear app N (100000 + C) and the face N (C);
#      the Wear APK and AAB carry the signed face byte for byte, and the APK's token is
#      push-token.sh's for it; no output holds a sample feed or a test vector (Ruling 55 item 6);
#   6. the four outputs, with their sizes and certificate SHA-256s.
#
# The store build (no TAQWA_RELEASE_DRY_RUN) is the Lead's alone: Mohamed's keystore.properties is
# linked into the root for it and removed straight after (docs/STORE-CHECKLIST.md, "Each watch
# release"). Gradle reads that file; this script only asks whether it is there. Before anything is
# built it refuses without keystore.properties, without the face's pin
# watchface/face-release-cert.sha256 (M1: Mohamed makes his face key with scripts/make-face-key.sh
# and the Lead commits its SHA-256, plan 5 Task 17), and with TAQWA_FACE_CERT_FILE set, which is the
# dry run's: a store build holds the face to the committed pin alone.
#
# The dry run (TAQWA_RELEASE_DRY_RUN=1) runs the same path without the upload key:
#   - it skips the keystore.properties check and passes -Ptaqwa.unsignedRelease=true to every build
#     of the root project it makes, step 3's face and step 4's four, as tools/watchface/validate.sh
#     does to the face gate's builds, so neither module reads keystore.properties, present or not
#     (step 1's tools/timetables checkStamps is a build of its own, which reads none);
#   - before signing anything it requires both APKs and both AABs unsigned: a signed one stops it
#     ("the release build was signed: taqwa.unsignedRelease is not honoured");
#   - it signs them with ~/.android/debug.keystore into build/release-dry-run/*-DRY-RUN.{aab,apk},
#     and step 5 checks those. Play refuses them, because the key is wrong, and their names say
#     what they are; they are for the emulators (plan 5 Task 12). A run that fails from step 4's
#     build on leaves none there; one refused before it (the versions, the face gate, the signed
#     face) built none of the four outputs, at most the face (steps 2 and 3), and leaves the last
#     dry run's outputs as they were;
#   - its pin is TAQWA_FACE_CERT_FILE (default the committed pin), so the face can be one that
#     release-face.sh signed with the throwaway test key (TAQWA_FACE_KS and the rest, plan 5 Task 2).
# scripts/test-release.sh is its test. Run scripts/test.sh and scripts/ios-build.sh before this, and
# scripts/bump-version.sh once per release. The R8 mapping files ride inside the bundles
# (BUNDLE-METADATA/), so Play de-obfuscates crash reports without a separate upload.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/version-files.sh

refuse() { echo "release.sh: $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

DEFAULT_PIN=watchface/face-release-cert.sha256
SIGNED_FACE=build/face-release/watchface-release-signed.apk
CURRENT_FACE=watchface/build/outputs/apk/release/watchface-release-unsigned.apk
DRY_DIR=build/release-dry-run
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
OUTPUT_DIRS=(androidApp/build/outputs/apk/release androidApp/build/outputs/bundle/release
    wearApp/build/outputs/apk/release wearApp/build/outputs/bundle/release)

case "${TAQWA_RELEASE_DRY_RUN-}" in
    "") DRY_RUN=false ;;
    1) DRY_RUN=true ;;
    *) echo "release.sh: TAQWA_RELEASE_DRY_RUN is 1 for the dry run or unset for the store build, not '$TAQWA_RELEASE_DRY_RUN'" >&2; exit 2 ;;
esac
# What the dry run passes to its own builds of the root project (step 3's and step 4's), and the
# store run's own builds never, so the upload key signs the four store outputs: a build of the root
# project configures :androidApp and :wearApp, whatever its tasks, and without it they read a
# keystore.properties beside them. Step 2's face gate passes it in both runs
# (tools/watchface/validate.sh), as release-face.sh does to the face it signs.
UNSIGNED=()
if $DRY_RUN; then UNSIGNED=(-Ptaqwa.unsignedRelease=true); fi

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2> /dev/null)}"
SDK="${SDK:-$HOME/Library/Android/sdk}"
TOOLS="$(ls -d "$SDK"/build-tools/*/ 2> /dev/null | sort -V | tail -1)"
APKSIGNER="${TOOLS}apksigner"
AAPT2="${TOOLS}aapt2"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
JARSIGNER="${JAVA_HOME:+$JAVA_HOME/bin/}jarsigner"

# --- Before anything is built: what this run needs, all of it named at once ----------------------

problems=()
if $DRY_RUN; then
    PIN_FILE="${TAQWA_FACE_CERT_FILE:-$DEFAULT_PIN}"
    [ -f "$DEBUG_KEYSTORE" ] || problems+=("the dry run signs with the debug key, and there is none at $DEBUG_KEYSTORE (any debug build makes it)")
else
    PIN_FILE="$DEFAULT_PIN"
    [ -z "${TAQWA_FACE_CERT_FILE+set}" ] \
        || problems+=("TAQWA_FACE_CERT_FILE is set ($TAQWA_FACE_CERT_FILE): it pins a dry run's face, and a store build holds the face to $DEFAULT_PIN alone; unset it")
    [ -f keystore.properties ] \
        || problems+=("keystore.properties is missing: the Lead links Mohamed's into the root for this run (docs/STORE-CHECKLIST.md, \"Each watch release\")")
fi
if [ ! -f "$PIN_FILE" ]; then
    if [ "$PIN_FILE" = "$DEFAULT_PIN" ]; then
        problems+=("no face pin at $DEFAULT_PIN: M1 is not done. Mohamed makes his face key with scripts/make-face-key.sh and pastes the certificate SHA-256 it prints; the Lead commits it there (plan 5 Task 17, M1)")
    else
        problems+=("no pin at $PIN_FILE (TAQWA_FACE_CERT_FILE)")
    fi
else
    PIN="$(tr -d '[:space:]' < "$PIN_FILE")"
    [[ "$PIN" =~ ^[0-9a-f]{64}$ ]] || problems+=("$PIN_FILE holds no certificate SHA-256 (64 lowercase hex digits)")
fi
[ -x "$APKSIGNER" ] && [ -x "$AAPT2" ] || problems+=("no apksigner or aapt2 under $SDK/build-tools")
if [ "${#problems[@]}" -gt 0 ]; then
    for problem in "${problems[@]}"; do echo "release.sh: refused: $problem" >&2; done
    exit 1
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-release.XXXXXX")"
PASSED=false
# Set just before step 4 clears the output paths: from then on they hold only this run's outputs.
BUILDING=false
cleanup() {
    rm -rf "$WORK"
    if $PASSED; then return 0; fi
    # A run that did not pass leaves nothing it built at the output paths, so no bundle that failed
    # a check waits where the store build's are uploaded from; and a dry run none to install. A run
    # refused before step 4 built none of them (steps 2 and 3 build only the face), so it leaves
    # those paths, and the last dry run's build/release-dry-run/, as they were.
    if $BUILDING; then
        rm -rf "${OUTPUT_DIRS[@]}"
        if $DRY_RUN; then rm -rf "$DRY_DIR"; fi
        echo "release.sh: the run did not pass, so the outputs it built are removed" >&2
    fi
}
trap cleanup EXIT

# The SHA-256 digests of an APK's signers' certificates, one a line; nothing when unsigned.
certs_of_apk() {
    { "$APKSIGNER" verify --print-certs "$1" 2> /dev/null || true; } \
        | sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u
}
# The same of an AAB, signed as a JAR; nothing when unsigned.
certs_of_aab() {
    { "$KEYTOOL" -printcert -jarfile "$1" 2> /dev/null || true; } \
        | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | sort -u
}
# Whether an AAB carries a JAR signature block. The listing is read whole first: grep -q leaving a
# pipe early would kill unzip (SIGPIPE), and pipefail would read a signed bundle as unsigned.
aab_signed() {
    local listing
    listing="$(unzip -Z1 "$1" 2> /dev/null || true)"
    grep -qE '^META-INF/[^/]+\.(RSA|EC|DSA)$' <<< "$listing"
}
badging() { { "$AAPT2" dump badging "$1" 2> /dev/null || true; } | sed -n 1p; }
code_of() { sed -n "s/.* versionCode='\([0-9]*\)'.*/\1/p" <<< "$1"; }
name_of() { sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<< "$1"; }
sha256_of() { shasum -a 256 < "$1" | cut -d' ' -f1; }
# The one file with this extension in a directory, or nothing.
only_file() {
    local found
    found="$(find "$1" -maxdepth 1 -name "*.$2" 2> /dev/null | sort)"
    [ "$(grep -c . <<< "$found")" -eq 1 ] && echo "$found"
}

if $DRY_RUN; then
    echo "release.sh: DRY RUN: no upload key, the outputs signed with the debug key, the face pinned to $PIN_FILE"
fi

# --- 1. The versions and the stamps -------------------------------------------------------------

step "1. The versions and the prayer-time stamps"
bash scripts/check-versions.sh || refuse "the versions disagree (above): scripts/bump-version.sh writes them"
# Spec §5 / ruling R86: refuse to build on a stale or red prayer-time stamp. The Ramadan build
# (ruling R146) runs as TAQWA_RAMADAN=<Hijri year> scripts/release.sh, e.g. 1448: it then also
# refuses an A/B unit whose Fajr, end of eating and Maghrib were not each checked on every day of
# that Ramadan (ruling R148), unless the gate has demoted it.
if ! ./gradlew -q -p tools/timetables checkStamps ${TAQWA_RAMADAN:+-Pramadan="$TAQWA_RAMADAN"}; then
    refuse "a prayer-time stamp is stale or red, or its Ramadan is not proven (see above). Refresh it first:" \
        "./gradlew -p tools/timetables gate, then ./gradlew -p tools/timetables generateProofStamps."
fi
N="$(sed -n 's/^ *versionName = "\([^"]*\)" *$/\1/p' "$PHONE_GRADLE")"
C="$(sed -n 's/^ *versionCode = \([0-9]*\) *$/\1/p' "$PHONE_GRADLE")"
WEAR_CODE=$((WEAR_OFFSET + C))

# --- 2. The face gate ---------------------------------------------------------------------------

step "2. The face gate"
bash scripts/face-gate.sh || refuse "the face gate failed (above)"

# --- 3. The signed face -------------------------------------------------------------------------

step "3. The signed face"
[ -f "$SIGNED_FACE" ] || refuse "no signed face at $SIGNED_FACE: Mohamed signs it with scripts/release-face.sh first"
"$APKSIGNER" verify "$SIGNED_FACE" > /dev/null 2>&1 || refuse "the signed face $SIGNED_FACE does not verify: run scripts/release-face.sh again"
face_certs="$(certs_of_apk "$SIGNED_FACE")"
[ "$face_certs" = "$PIN" ] \
    || refuse "the signed face is signed by $(xargs <<< "${face_certs:-nothing}"), not by the pinned $PIN ($PIN_FILE)"
echo "ok - $SIGNED_FACE is signed by the pinned certificate $PIN alone"
# Gradle never reads stdin.
./gradlew -q :watchface:assembleRelease ${UNSIGNED[@]+"${UNSIGNED[@]}"} < /dev/null \
    || refuse "the face's release did not build (above)"
[ -f "$CURRENT_FACE" ] || refuse "the build left no $CURRENT_FACE"
if ! differences="$(python3 -I - "$SIGNED_FACE" "$CURRENT_FACE" << 'PY'
import sys, zipfile
def entries(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename: i.CRC for i in z.infolist() if not i.filename.startswith("META-INF/")}
signed, current = entries(sys.argv[1]), entries(sys.argv[2])
lines = [f"only in the signed face: {n}" for n in sorted(signed.keys() - current.keys())]
lines += [f"only in the current face: {n}" for n in sorted(current.keys() - signed.keys())]
lines += [f"not the same: {n}" for n in sorted(signed.keys() & current.keys()) if signed[n] != current[n]]
print("\n".join(lines))
sys.exit(1 if lines else 0)
PY
)"; then
    echo "$differences" | head -20 | sed 's/^/   /' >&2
    refuse "the signed face is not the current face: run scripts/release-face.sh again"
fi
echo "ok - the signed face is the current face: every entry outside META-INF/ has the same name and CRC-32"

# --- 4. The build -------------------------------------------------------------------------------

step "4. The phone's and the watch's bundles and APKs"
# Only this run's outputs are judged, and a run that does not pass removes them (cleanup).
BUILDING=true
rm -rf "${OUTPUT_DIRS[@]}"
$DRY_RUN && rm -rf "$DRY_DIR"
GRADLE_ARGS=(-Ptaqwa.storeRelease=true "-Ptaqwa.faceApk=$PWD/$SIGNED_FACE" ${UNSIGNED[@]+"${UNSIGNED[@]}"})
./gradlew -q :androidApp:bundleRelease :androidApp:assembleRelease :wearApp:bundleRelease :wearApp:assembleRelease \
    "${GRADLE_ARGS[@]}" < /dev/null || refuse "the build failed (above)"
PHONE_APK="$(only_file androidApp/build/outputs/apk/release apk)" || refuse "the phone's build left no one APK"
PHONE_AAB="$(only_file androidApp/build/outputs/bundle/release aab)" || refuse "the phone's build left no one AAB"
WEAR_APK="$(only_file wearApp/build/outputs/apk/release apk)" || refuse "the watch's build left no one APK"
WEAR_AAB="$(only_file wearApp/build/outputs/bundle/release aab)" || refuse "the watch's build left no one AAB"

if $DRY_RUN; then
    for apk in "$PHONE_APK" "$WEAR_APK"; do
        ! "$APKSIGNER" verify "$apk" > /dev/null 2>&1 \
            || refuse "the release build was signed: taqwa.unsignedRelease is not honoured ($apk verifies)"
    done
    for aab in "$PHONE_AAB" "$WEAR_AAB"; do
        ! aab_signed "$aab" || refuse "the release build was signed: taqwa.unsignedRelease is not honoured ($aab carries a signature)"
    done
    echo "ok - the four outputs are unsigned: no key was read"
    mkdir -p "$DRY_DIR"
    dry_sign() { # dry_sign <module> <apk> <aab>
        "$APKSIGNER" sign --ks "$DEBUG_KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android \
            --key-pass pass:android --v4-signing-enabled false --out "$DRY_DIR/$1-release-DRY-RUN.apk" "$2" \
            || refuse "apksigner could not sign $2 with the debug key"
        "$JARSIGNER" -keystore "$DEBUG_KEYSTORE" -storepass android -keypass android \
            -signedjar "$DRY_DIR/$1-release-DRY-RUN.aab" "$3" androiddebugkey > "$WORK/jarsigner.txt" 2>&1 \
            || { cat "$WORK/jarsigner.txt" >&2; refuse "jarsigner could not sign $3 with the debug key"; }
    }
    dry_sign androidApp "$PHONE_APK" "$PHONE_AAB"
    dry_sign wearApp "$WEAR_APK" "$WEAR_AAB"
    PHONE_APK="$DRY_DIR/androidApp-release-DRY-RUN.apk"
    PHONE_AAB="$DRY_DIR/androidApp-release-DRY-RUN.aab"
    WEAR_APK="$DRY_DIR/wearApp-release-DRY-RUN.apk"
    WEAR_AAB="$DRY_DIR/wearApp-release-DRY-RUN.aab"
    echo "ok - signed with the debug key into $DRY_DIR/"
else
    for apk in "$PHONE_APK" "$WEAR_APK"; do
        "$APKSIGNER" verify "$apk" > /dev/null 2>&1 \
            || refuse "$apk is not signed: is taqwa.unsignedRelease set somewhere (gradle.properties, ORG_GRADLE_PROJECT_*)?"
    done
    for aab in "$PHONE_AAB" "$WEAR_AAB"; do
        aab_signed "$aab" || refuse "$aab is not signed: is taqwa.unsignedRelease set somewhere (gradle.properties, ORG_GRADLE_PROJECT_*)?"
    done
fi

# --- 5. The checks ------------------------------------------------------------------------------

step "5. The checks"
FAILS=0
ok() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

if bash tools/watchface/check-bundle.sh "$WEAR_APK" "$PIN"; then ok "check-bundle.sh on $WEAR_APK"; else fail "check-bundle.sh on $WEAR_APK (above)"; fi
if bash tools/wear-release-check.sh release; then ok "wear-release-check.sh release"; else fail "wear-release-check.sh release (above)"; fi

WANTED="$(sed -e 's/#.*//' androidApp/permissions.txt | awk 'NF { print $1 }' | sort -u)"
ASKED="$({ "$AAPT2" dump permissions "$PHONE_APK" 2> /dev/null || true; } \
    | sed -n "s/^uses-permission[-a-z0-9]*: name='\([^']*\)'.*/\1/p" | sort -u)"
if [ -n "$ASKED" ] && [ "$ASKED" = "$WANTED" ]; then
    ok "the phone's merged manifest asks for exactly androidApp/permissions.txt's $(grep -c . <<< "$WANTED"): $(xargs <<< "$ASKED")"
else
    fail "the phone's merged manifest's permissions differ from androidApp/permissions.txt (< asked only, > listed only):"
    diff <(echo "$ASKED") <(echo "$WANTED") | grep '^[<>]' | sed 's/^/    /' || true
fi

PHONE_CERT="$(certs_of_apk "$PHONE_APK")"
WEAR_CERT="$(certs_of_apk "$WEAR_APK")"
if [ -n "$PHONE_CERT" ] && [ "$(grep -c . <<< "$PHONE_CERT")" -eq 1 ] && [ "$WEAR_CERT" = "$PHONE_CERT" ]; then
    ok "the Wear APK's certificate is the phone APK's ($PHONE_CERT)"
else
    fail "the Wear APK is signed by '$(xargs <<< "$WEAR_CERT")' and the phone APK by '$(xargs <<< "$PHONE_CERT")': the watch's link needs one key"
fi
for pair in "$PHONE_AAB:$PHONE_CERT" "$WEAR_AAB:$WEAR_CERT"; do
    aab=${pair%%:*} want=${pair#*:}
    got="$(certs_of_aab "$aab")"
    if [ -n "$got" ] && [ "$got" = "$want" ]; then ok "$(basename "$aab") is signed by its APK's certificate"; else fail "$(basename "$aab") is signed by '$(xargs <<< "$got")', not its APK's '$want'"; fi
done

FACE_IN="$WORK/default_watchface.apk"
unzip -p "$WEAR_APK" assets/default_watchface.apk > "$FACE_IN" 2> /dev/null || : > "$FACE_IN"
phone_badging="$(badging "$PHONE_APK")"
wear_badging="$(badging "$WEAR_APK")"
face_badging="$(badging "$FACE_IN")"
version_is() { # version_is <what> <its badging line> <the code it must have>
    if [ "$(name_of "$2")" = "$N" ] && [ "$(code_of "$2")" = "$3" ]; then
        ok "$1 is $N ($3)"
    else
        fail "$1 is '$(name_of "$2")' ($(code_of "$2")), not $N ($3)"
    fi
}
version_is "the phone" "$phone_badging" "$C"
version_is "the Wear app" "$wear_badging" "$WEAR_CODE"
version_is "the face" "$face_badging" "$C"

face_sha="$(sha256_of "$SIGNED_FACE")"
aab_face_sha="$({ unzip -p "$WEAR_AAB" base/assets/default_watchface.apk 2> /dev/null || true; } | shasum -a 256 | cut -d' ' -f1)"
if [ -s "$FACE_IN" ] && [ "$(sha256_of "$FACE_IN")" = "$face_sha" ] && [ "$aab_face_sha" = "$face_sha" ]; then
    ok "the Wear APK and AAB carry the signed face byte for byte"
else
    fail "the face the Wear APK or AAB carries is not $SIGNED_FACE"
fi
# The token the APK installs the face with is the one Google's validator issues for that very face.
# It is compared, never printed.
bundled_token="$({ "$AAPT2" dump resources "$WEAR_APK" 2> /dev/null || true; } \
    | { grep -A1 -E ' string/default_wf_token$' || true; } | sed -n 2p \
    | sed -E 's/^[[:space:]]*\([^)]*\) "(.*)"[[:space:]]*$/\1/')"
if bash tools/watchface/push-token.sh "$SIGNED_FACE" "$WORK/token" > "$WORK/push-token.txt" 2>&1 \
    && [ -s "$WORK/token" ] && [ "$bundled_token" = "$(cat "$WORK/token")" ]; then
    ok "the Wear APK's default_wf_token is push-token.sh's for the bundled face (withheld)"
else
    sed 's/^/    /' "$WORK/push-token.txt"
    fail "the Wear APK's default_wf_token is not push-token.sh's for the bundled face"
fi

test_data=""
for output in "$PHONE_APK" "$PHONE_AAB" "$WEAR_APK" "$WEAR_AAB"; do
    found="$({ unzip -Z1 "$output" 2> /dev/null || true; } | { grep -E 'sample-feed|feed-vectors|watch-rules-vectors|(^|/)vectors/' || true; })"
    [ -z "$found" ] || test_data+="$(basename "$output"): $(xargs <<< "$found") "
done
if [ -z "$test_data" ]; then ok "no output holds a sample feed or a test vector"; else fail "test data in a store output: $test_data"; fi

[ "$FAILS" -eq 0 ] || refuse "$FAILS of step 5's checks failed (above)"

# --- 6. The outputs -----------------------------------------------------------------------------

step "6. The outputs"
for output in "$PHONE_AAB" "$PHONE_APK" "$WEAR_AAB" "$WEAR_APK"; do
    case "$output" in *.apk) cert="$(certs_of_apk "$output")" ;; *) cert="$(certs_of_aab "$output")" ;; esac
    printf '  %6.1f MB  %s\n             certificate SHA-256 %s\n' \
        "$(awk -v b="$(wc -c < "$output")" 'BEGIN { print b / 1048576 }')" "$output" "$(xargs <<< "$cert")"
done
echo
echo "  $(sed -n 1p <<< "$phone_badging" | cut -d' ' -f1-4)"
echo "  $(sed -n 1p <<< "$wear_badging" | cut -d' ' -f1-4)"
echo "  the face inside: $(sed -n 1p <<< "$face_badging" | cut -d' ' -f1-4), certificate SHA-256 $PIN"

PASSED=true
echo
if $DRY_RUN; then
    echo "release.sh: DRY RUN PASS: $DRY_DIR/ holds the four outputs, signed with the debug key, for the emulators alone; Play refuses them."
else
    echo "release.sh: PASS: upload the two AABs to Google Play (docs/STORE-CHECKLIST.md, \"Each watch release\")."
fi
