#!/usr/bin/env bash
# Builds and checks the store artefacts of the version in androidApp/build.gradle.kts: the phone's,
# and the Galaxy watch app's with them, the Taqwa face inside it (plan 5 Task 7), when this release
# ships the watch.
#
#   scripts/release.sh                          the store build: the Lead's alone
#   TAQWA_RELEASE_DRY_RUN=1 scripts/release.sh  the dry run: the same path, with no upload key
#   TAQWA_RELEASE_WATCH=1 (or 0)                either run, the watch shipped (or kept back) whatever step 0 finds
#
# Whether the watch ships is step 0's question, scripts/watch-ships.sh's (Mohamed's decision of
# 26 November 2026): Play's Wear OS review takes days where the phone's takes hours, so the watch goes
# to Play's Wear OS track only when it has changed since the last watch release, the wear-build-<N>
# tag with the highest N; otherwise Play keeps serving that build, 100000 + N. Its versions move with
# the phone's all the same (scripts/bump-version.sh), so whenever it next ships its code is 100000 +
# the phone's then. That gives two paths:
#
# The watch's path, when it ships: the store build's outputs, signed with the upload key named in
# keystore.properties, are
#   androidApp/build/outputs/bundle/release/androidApp-release.aab  — upload this to Google Play
#   androidApp/build/outputs/apk/release/androidApp-release.apk     — the same build as an APK, for a phone check
#   wearApp/build/outputs/bundle/release/wearApp-release.aab        — the watch app's, on the same Play listing
#   wearApp/build/outputs/apk/release/wearApp-release.apk           — the same build as an APK, for a watch check
# and its PASS line says to upload both AABs and, once the Wear AAB is submitted to the Wear OS track,
# to tag wear-build-<C> on the commit it was built from, giving the git tag command with that commit
# (HEAD at step 0), or saying which of the watch's files HEAD did not hold as they were built (that
# tag means "Play's Wear OS track has this build": scripts/watch-ships.sh compares the next release
# with its commit, and refuses one whose wearApp code is not this build's; it is deleted if that
# Wear release is abandoned or rejected with no replacement, and sending the same watch again later
# takes TAQWA_RELEASE_WATCH=1). The phone-only path, when the watch stays: the first two alone. Step 4
# removes the Wear app's output directories all the same, so no Wear AAB or APK from an earlier run
# waits at those paths, and the PASS line says to upload the phone's AAB alone, naming the build
# Play's Wear OS track keeps serving and repeating any "note:" line of step 0.
# Upload only after "release.sh: PASS". A run that fails from step 4 on removes all four paths, so a
# bundle that failed a check (a face signed with the app's key, a permission a library added, a
# token for another face) is never left at those paths.
#
# In order, stopping at the first failure:
#   0. scripts/watch-ships.sh, its "ships:" or "stays:" line and any note printed as they are. The
#      watch ships on exit 0 with a first line "ships:", and stays on exit 1 with "stays:"; anything
#      else (an error there, such as no git checkout or a TAQWA_RELEASE_WATCH other than 1 or 0, or an
#      exit status its first line disagrees with) stops the run (exit 2);
#   1. scripts/check-versions.sh; on the watch's path, the Wear OS track's notes,
#      docs/store/release-notes/N-<100000 + C>-wear.txt, there and within Play's rules
#      (tools/store-listing-check.py --wear-notes N C: scripts/test.sh's listing check reads that file
#      when it is there but cannot ask for it, as only this run knows the watch ships); the
#      prayer-time stamps (spec §5, ruling R86) and the envelope rows (the second trust audit's G24);
#   2. scripts/face-gate.sh on the watch's path; on the phone-only path scripts/face-gate.sh
#      --fit-only, the face's generators and fit tools without its builds and validators, because
#      the face Play keeps serving must still draw every form this phone sends (its weekdays, months,
#      Hijri names and countdowns, tools/watchface/phone-forms.json and the phone's own files, which
#      a phone change can move while the watch stays). Under TAQWA_RELEASE_WATCH=0 over a change to
#      the face, the tools measure the tree's face, not the one Play serves;
#   3. on the watch's path alone, the signed face, build/face-release/watchface-release-signed.apk,
#      which Mohamed signs with scripts/release-face.sh: it exists, its one certificate is the pin,
#      and it is the current face (:watchface:assembleRelease is built again, and every zip entry
#      outside META-INF/ has the same name and CRC-32 in both);
#   4. the four builds, with -Ptaqwa.storeRelease=true and -Ptaqwa.faceApk=<the signed face>, so the
#      watch app carries that face as its default face, with the token push-token.sh makes from it;
#      on the phone-only path the phone's two alone (:androidApp:bundleRelease and
#      :androidApp:assembleRelease), with no -Ptaqwa.faceApk;
#   5. the checks, every one run, whatever those before it found: tools/watchface/check-bundle.sh on
#      the Wear APK; tools/wear-release-check.sh release; the phone's merged permissions are exactly
#      androidApp/permissions.txt; the Wear APK's certificate is the phone APK's, and each AAB's its
#      APK's; aapt2's badging gives the phone N (C), the Wear app N (100000 + C) and the face N (C);
#      the Wear APK and AAB carry the signed face byte for byte, and the APK's token is
#      push-token.sh's for it; no output holds a sample feed or a test vector (Ruling 55 item 6). On
#      the phone-only path, those that concern the phone: its merged permissions, its APK's one
#      certificate and its AAB's, its badging N (C), and no test data in either;
#   6. the outputs, four or the phone's two, with their sizes and certificate SHA-256s.
#
# The store build (no TAQWA_RELEASE_DRY_RUN) is the Lead's alone: Mohamed's keystore.properties is
# linked into the root for it and removed straight after (docs/STORE-CHECKLIST.md, "Each watch
# release"). Gradle reads that file; this script only asks whether it is there. Before anything is
# built it refuses without keystore.properties, on either path. On the watch's path it also refuses
# without the face's pin watchface/face-release-cert.sha256 (M1: Mohamed makes his face key with
# scripts/make-face-key.sh and the Lead commits its SHA-256, plan 5 Task 17), and with
# TAQWA_FACE_CERT_FILE set, which is the dry run's: a store build holds the face to the committed pin
# alone. The phone-only path bundles no face, so neither applies.
#
# The dry run (TAQWA_RELEASE_DRY_RUN=1) runs the same path without the upload key:
#   - it skips the keystore.properties check and passes -Ptaqwa.unsignedRelease=true to every build
#     of the root project it makes, step 3's face and step 4's builds, as tools/watchface/validate.sh
#     does to the face gate's builds, so neither module reads keystore.properties, present or not
#     (step 1's tools/timetables checkStamps and checkEnvelopeRows are builds of their own, which
#     read none);
#   - before signing anything it requires every APK and AAB it built unsigned: a signed one stops it
#     ("the release build was signed: taqwa.unsignedRelease is not honoured");
#   - it signs them with ~/.android/debug.keystore into build/release-dry-run/*-DRY-RUN.{aab,apk}
#     (emptied first, so the phone-only path leaves the phone's two there alone), and step 5 checks
#     those. Play refuses them, because the key is wrong, and their names say what they are; they
#     are for the emulators (plan 5 Task 12). A run that fails from step 4's build on leaves none
#     there; one refused before it (the versions, the Wear OS notes, the face gate or its fit, the
#     signed face) built none of the outputs, at most the face (steps 2 and 3), and leaves the
#     last dry run's outputs as they were;
#   - on the watch's path its pin is TAQWA_FACE_CERT_FILE (default the committed pin), so the face
#     can be one that release-face.sh signed with the throwaway test key (TAQWA_FACE_KS and the
#     rest, plan 5 Task 2).
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

# --- 0. Whether the watch ships -----------------------------------------------------------------

step "0. Whether the watch ships (scripts/watch-ships.sh)"
# Its lines are printed as they are; it exits 0 when the watch ships, 1 when it stays, 2 on an error.
# The phone-only path is taken only when the exit status and the first line agree that it stays.
ships_status=0
SHIPS_SAID="$(bash scripts/watch-ships.sh)" || ships_status=$?
[ -z "$SHIPS_SAID" ] || echo "$SHIPS_SAID"
SHIPS_FIRST="$(sed -n 1p <<< "$SHIPS_SAID")"
case "$ships_status:$SHIPS_FIRST" in
    "0:ships:"*) WATCH=true ;;
    "1:stays:"*) WATCH=false ;;
    *) echo "release.sh: scripts/watch-ships.sh could not say whether the watch ships (exit $ships_status, first line '$SHIPS_FIRST'; above)" >&2; exit 2 ;;
esac
# On the watch's path, the commit this run builds, which the PASS line names for the wear-build tag,
# and the watch's files that differ from it (the three modules and the catalog, committed or not),
# so that line never names a commit that does not hold the watch that was built.
BUILT=""
BUILT_SHORT=""
BUILT_DIRTY=""
if $WATCH; then
    WATCH_FILES=(wearApp watchcore watchface gradle/libs.versions.toml)
    { BUILT="$(git rev-parse --verify -q 'HEAD^{commit}')" && BUILT_SHORT="$(git rev-parse --short "$BUILT")" \
        && BUILT_DIRTY="$( { git -c core.quotepath=off diff --name-only --no-renames HEAD -- "${WATCH_FILES[@]}" \
            && git -c core.quotepath=off ls-files --others --exclude-standard -- "${WATCH_FILES[@]}"; } | sort -u)"; } \
        || { echo "release.sh: git cannot name the commit this run builds, or what of the watch differs from it" >&2; exit 2; }
fi
# Its notes (what the watch is built with beside its own modules), which the phone-only PASS line repeats.
SHIPS_NOTES="$( { grep '^note: ' <<< "$SHIPS_SAID" || true; } | sed 's/^note: //' | awk 'NR > 1 { printf "; " } { printf "%s", $0 }')"
# On the phone-only path, the PASS line's words for the watch build Play keeps serving.
KEPT=""
if ! $WATCH; then
    LAST_WEAR_TAG="$(bash scripts/watch-ships.sh --tag)" || exit 2
    if [ "${TAQWA_RELEASE_WATCH-}" = 0 ]; then
        KEPT="TAQWA_RELEASE_WATCH=0 keeps the watch back"
    else
        KEPT="the watch has not changed since $LAST_WEAR_TAG"
    fi
    if [ -n "$LAST_WEAR_TAG" ]; then
        KEPT+=", so Play's Wear OS track keeps serving $((WEAR_OFFSET + ${LAST_WEAR_TAG#wear-build-}))"
    else
        KEPT+=", so Play's Wear OS track keeps serving the last watch build it took, which no wear-build tag names"
    fi
fi

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
    # The face's two rules hold on the watch's path: the phone-only path bundles no face.
    if $WATCH && [ -n "${TAQWA_FACE_CERT_FILE+set}" ]; then
        problems+=("TAQWA_FACE_CERT_FILE is set ($TAQWA_FACE_CERT_FILE): it pins a dry run's face, and a store build holds the face to $DEFAULT_PIN alone; unset it")
    fi
    [ -f keystore.properties ] \
        || problems+=("keystore.properties is missing: the Lead links Mohamed's into the root for this run (docs/STORE-CHECKLIST.md, \"Each watch release\")")
fi
if ! $WATCH; then
    PIN=""
elif [ ! -f "$PIN_FILE" ]; then
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

if $DRY_RUN && $WATCH; then
    echo "release.sh: DRY RUN: no upload key, the outputs signed with the debug key, the face pinned to $PIN_FILE"
elif $DRY_RUN; then
    echo "release.sh: DRY RUN (phone only): no upload key, the phone's outputs signed with the debug key"
fi

# --- 1. The versions, the Wear OS notes, the stamps and the envelope rows -------------------------

step "1. The versions, the Wear OS notes (the watch's path), the prayer-time stamps and the envelope rows"
bash scripts/check-versions.sh || refuse "the versions disagree (above): scripts/bump-version.sh writes them"
N="$(sed -n 's/^ *versionName = "\([^"]*\)" *$/\1/p' "$PHONE_GRADLE")"
C="$(sed -n 's/^ *versionCode = \([0-9]*\) *$/\1/p' "$PHONE_GRADLE")"
WEAR_CODE=$((WEAR_OFFSET + C))
# When step 0 said the watch ships, this run asks for its notes here, where that is decided: the
# listing check scripts/test.sh runs reads them when they are there, and asks for the phone's alone.
if $WATCH; then
    python3 -I tools/store-listing-check.py --wear-notes "$N" "$C" \
        || refuse "the watch ships, and its Play notes are not ready (above): write docs/store/release-notes/$N-$WEAR_CODE-wear.txt, Play's seven blocks as in the phone's $N-$C.txt"
fi
# Spec §5 / ruling R86: refuse to build on a stale or red prayer-time stamp. The Ramadan build
# (ruling R146) runs as TAQWA_RAMADAN=<Hijri year> scripts/release.sh, e.g. 1448: it then also
# refuses an A/B unit whose Fajr, end of eating and Maghrib (and its imsak where a table prints one)
# were not each held out and checked on every day of that Ramadan (rulings R148 and R246: a day
# held only in a table the method was fitted to does not count), unless the gate has demoted it, or
# the entry is covered under O4
# (ruling R185: method proven on the months held and on a Ramadan before, the monitor fetching the
# authority's own site with every fetched day in the gate, demoted by the monitor on the first
# failing day) — which is read from the archive's checkout (TAQWA_OFFICIAL, else the archive link
# under tools/timetables/official). Any build refuses an entry the monitor has demoted (a
# "@demoted" gate line) until it is looked at, and (-Prelease=true) any build is refused while a
# reach proof is owed: a unit left out of its entry's reach rows, or an entry whose reach rows are
# pending, where About's figure is known to be too low (ReachRows.outstanding, never early), an entry
# whose measured units have no reach spec, or a reach limit over 4 min the owner has not ruled on
# (ruling R270, the second trust audit of 14 Nov 2026).
# Without TAQWA_RAMADAN the check still prints, and never fails for, one line naming the entries
# not yet proven for the coming Ramadan (ruling R246).
if ! ./gradlew -q -p tools/timetables checkStamps -Prelease=true ${TAQWA_RAMADAN:+-Pramadan="$TAQWA_RAMADAN"} ${TAQWA_OFFICIAL:+-Pofficial="$TAQWA_OFFICIAL"}; then
    refuse "a prayer-time stamp is stale, red or demoted, its Ramadan is not proven, or a reach proof is owed (see above)." \
        "For a stamp, refresh it first: ./gradlew -p tools/timetables gate, then ./gradlew -p tools/timetables generateProofStamps."
fi

# The second trust audit's G24 (14 Nov 2026): checkEnvelopeRows is one of the checks of a release (docs/MONITOR.md).
# The envelope's promise-only rows replay each table printed for its own point at the app's cities its reach holds; they
# are a pure function of the gate's group files and fall behind when prove appends a year to a group file with units
# (us-chicago-envelope.tsv was 517 rows behind for 12 days). scripts/prove.sh now writes them after each proof; this
# refuses a release cut from a tree where they are stale all the same.
if ! ./gradlew -q -p tools/timetables checkEnvelopeRows; then
    refuse "an envelope file is not what generateEnvelopeRows would write (see above): run ./gradlew -p tools/timetables" \
        "generateEnvelopeRows, then the gate, and commit."
fi

# --- 2. The face gate (on the phone-only path its fit alone) and 3. the signed face --------------

if $WATCH; then
    step "2. The face gate"
    bash scripts/face-gate.sh || refuse "the face gate failed (above)"

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
else
    # The phone-to-face contract runs one way: the phone sends the face its words, and the face Play
    # keeps serving has to draw them. A phone change to its forms reaches every watch with this AAB,
    # so the fit tools measure them here as on the watch's path; they need no key and no Gradle.
    step "2. The face's fit to the phone's forms (scripts/face-gate.sh --fit-only: the watch stays, so no face build)"
    bash scripts/face-gate.sh --fit-only \
        || refuse "the face Play keeps serving does not fit what this phone sends, or its generators disagree with it (above):" \
            "put the phone's forms back, or change the face and ship the watch with it"
    step "3. The signed face: not needed, the watch stays (step 0)"
fi

# --- 4. The build -------------------------------------------------------------------------------

if $WATCH; then
    step "4. The phone's and the watch's bundles and APKs"
    TASKS=(:androidApp:bundleRelease :androidApp:assembleRelease :wearApp:bundleRelease :wearApp:assembleRelease)
    FACE_ARGS=("-Ptaqwa.faceApk=$PWD/$SIGNED_FACE")
else
    step "4. The phone's bundle and APK (the watch stays: no Wear build, no face)"
    TASKS=(:androidApp:bundleRelease :androidApp:assembleRelease)
    FACE_ARGS=()
fi
# Only this run's outputs are judged, and a run that does not pass removes them (cleanup). On the
# phone-only path this removes the Wear app's outputs too, so none from an earlier run waits there.
BUILDING=true
rm -rf "${OUTPUT_DIRS[@]}"
$DRY_RUN && rm -rf "$DRY_DIR"
GRADLE_ARGS=(-Ptaqwa.storeRelease=true ${FACE_ARGS[@]+"${FACE_ARGS[@]}"} ${UNSIGNED[@]+"${UNSIGNED[@]}"})
./gradlew -q "${TASKS[@]}" "${GRADLE_ARGS[@]}" < /dev/null || refuse "the build failed (above)"
PHONE_APK="$(only_file androidApp/build/outputs/apk/release apk)" || refuse "the phone's build left no one APK"
PHONE_AAB="$(only_file androidApp/build/outputs/bundle/release aab)" || refuse "the phone's build left no one AAB"
APKS=("$PHONE_APK")
AABS=("$PHONE_AAB")
COUNT=two
if $WATCH; then
    WEAR_APK="$(only_file wearApp/build/outputs/apk/release apk)" || refuse "the watch's build left no one APK"
    WEAR_AAB="$(only_file wearApp/build/outputs/bundle/release aab)" || refuse "the watch's build left no one AAB"
    APKS+=("$WEAR_APK")
    AABS+=("$WEAR_AAB")
    COUNT=four
fi

if $DRY_RUN; then
    for apk in "${APKS[@]}"; do
        ! "$APKSIGNER" verify "$apk" > /dev/null 2>&1 \
            || refuse "the release build was signed: taqwa.unsignedRelease is not honoured ($apk verifies)"
    done
    for aab in "${AABS[@]}"; do
        ! aab_signed "$aab" || refuse "the release build was signed: taqwa.unsignedRelease is not honoured ($aab carries a signature)"
    done
    echo "ok - the $COUNT outputs are unsigned: no key was read"
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
    PHONE_APK="$DRY_DIR/androidApp-release-DRY-RUN.apk"
    PHONE_AAB="$DRY_DIR/androidApp-release-DRY-RUN.aab"
    if $WATCH; then
        dry_sign wearApp "$WEAR_APK" "$WEAR_AAB"
        WEAR_APK="$DRY_DIR/wearApp-release-DRY-RUN.apk"
        WEAR_AAB="$DRY_DIR/wearApp-release-DRY-RUN.aab"
    fi
    echo "ok - signed with the debug key into $DRY_DIR/"
else
    for apk in "${APKS[@]}"; do
        "$APKSIGNER" verify "$apk" > /dev/null 2>&1 \
            || refuse "$apk is not signed: is taqwa.unsignedRelease set somewhere (gradle.properties, ORG_GRADLE_PROJECT_*)?"
    done
    for aab in "${AABS[@]}"; do
        aab_signed "$aab" || refuse "$aab is not signed: is taqwa.unsignedRelease set somewhere (gradle.properties, ORG_GRADLE_PROJECT_*)?"
    done
fi
# What steps 5 and 6 judge and list, in step 6's order.
OUTPUTS=("$PHONE_AAB" "$PHONE_APK")
if $WATCH; then OUTPUTS+=("$WEAR_AAB" "$WEAR_APK"); fi

# --- 5. The checks ------------------------------------------------------------------------------

step "5. The checks"
FAILS=0
ok() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

# The watch's checks run on its path alone; the phone's, on both.
if $WATCH; then
    if bash tools/watchface/check-bundle.sh "$WEAR_APK" "$PIN"; then ok "check-bundle.sh on $WEAR_APK"; else fail "check-bundle.sh on $WEAR_APK (above)"; fi
    if bash tools/wear-release-check.sh release; then ok "wear-release-check.sh release"; else fail "wear-release-check.sh release (above)"; fi
fi

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
if $WATCH; then
    WEAR_CERT="$(certs_of_apk "$WEAR_APK")"
    if [ -n "$PHONE_CERT" ] && [ "$(grep -c . <<< "$PHONE_CERT")" -eq 1 ] && [ "$WEAR_CERT" = "$PHONE_CERT" ]; then
        ok "the Wear APK's certificate is the phone APK's ($PHONE_CERT)"
    else
        fail "the Wear APK is signed by '$(xargs <<< "$WEAR_CERT")' and the phone APK by '$(xargs <<< "$PHONE_CERT")': the watch's link needs one key"
    fi
    CERT_PAIRS=("$PHONE_AAB:$PHONE_CERT" "$WEAR_AAB:$WEAR_CERT")
else
    if [ -n "$PHONE_CERT" ] && [ "$(grep -c . <<< "$PHONE_CERT")" -eq 1 ]; then
        ok "the phone APK is signed by one certificate ($PHONE_CERT)"
    else
        fail "the phone APK is signed by '$(xargs <<< "$PHONE_CERT")', not by one certificate"
    fi
    CERT_PAIRS=("$PHONE_AAB:$PHONE_CERT")
fi
for pair in "${CERT_PAIRS[@]}"; do
    aab=${pair%%:*} want=${pair#*:}
    got="$(certs_of_aab "$aab")"
    if [ -n "$got" ] && [ "$got" = "$want" ]; then ok "$(basename "$aab") is signed by its APK's certificate"; else fail "$(basename "$aab") is signed by '$(xargs <<< "$got")', not its APK's '$want'"; fi
done

phone_badging="$(badging "$PHONE_APK")"
version_is() { # version_is <what> <its badging line> <the code it must have>
    if [ "$(name_of "$2")" = "$N" ] && [ "$(code_of "$2")" = "$3" ]; then
        ok "$1 is $N ($3)"
    else
        fail "$1 is '$(name_of "$2")' ($(code_of "$2")), not $N ($3)"
    fi
}
version_is "the phone" "$phone_badging" "$C"
if $WATCH; then
    FACE_IN="$WORK/default_watchface.apk"
    unzip -p "$WEAR_APK" assets/default_watchface.apk > "$FACE_IN" 2> /dev/null || : > "$FACE_IN"
    wear_badging="$(badging "$WEAR_APK")"
    face_badging="$(badging "$FACE_IN")"
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
fi

test_data=""
# The order the message has always named them in: each APK before its AAB.
TEST_DATA_ORDER=("$PHONE_APK" "$PHONE_AAB")
if $WATCH; then TEST_DATA_ORDER+=("$WEAR_APK" "$WEAR_AAB"); fi
for output in "${TEST_DATA_ORDER[@]}"; do
    found="$({ unzip -Z1 "$output" 2> /dev/null || true; } | { grep -E 'sample-feed|feed-vectors|watch-rules-vectors|(^|/)vectors/' || true; })"
    [ -z "$found" ] || test_data+="$(basename "$output"): $(xargs <<< "$found") "
done
if [ -z "$test_data" ]; then ok "no output holds a sample feed or a test vector"; else fail "test data in a store output: $test_data"; fi

[ "$FAILS" -eq 0 ] || refuse "$FAILS of step 5's checks failed (above)"

# --- 6. The outputs -----------------------------------------------------------------------------

step "6. The outputs"
for output in "${OUTPUTS[@]}"; do
    case "$output" in *.apk) cert="$(certs_of_apk "$output")" ;; *) cert="$(certs_of_aab "$output")" ;; esac
    printf '  %6.1f MB  %s\n             certificate SHA-256 %s\n' \
        "$(awk -v b="$(wc -c < "$output")" 'BEGIN { print b / 1048576 }')" "$output" "$(xargs <<< "$cert")"
done
echo
echo "  $(sed -n 1p <<< "$phone_badging" | cut -d' ' -f1-4)"
if $WATCH; then
    echo "  $(sed -n 1p <<< "$wear_badging" | cut -d' ' -f1-4)"
    echo "  the face inside: $(sed -n 1p <<< "$face_badging" | cut -d' ' -f1-4), certificate SHA-256 $PIN"
fi

PASSED=true
# The phone-only PASS lines repeat step 0's notes, for the Lead to weigh before the upload.
NOTED=""
if [ -n "$SHIPS_NOTES" ]; then NOTED=" Step 0's note, to weigh before the upload: $SHIPS_NOTES."; fi
echo
if $WATCH && $DRY_RUN; then
    echo "release.sh: DRY RUN PASS: $DRY_DIR/ holds the four outputs, signed with the debug key, for the emulators alone; Play refuses them."
elif $WATCH; then
    # The tag, annotated as wear-build-45 and wear-build-46 are, on the commit the Wear AAB was built
    # from: scripts/watch-ships.sh compares the next release with that commit's watch.
    TAG_COMMAND="git tag -a wear-build-$C -m \"$N Wear OS ($WEAR_CODE) store build, on Play's Wear OS track\""
    if [ -z "$BUILT_DIRTY" ]; then
        TAG_IT=": $TAG_COMMAND $BUILT"
    else
        dirty_count="$(grep -c . <<< "$BUILT_DIRTY")"
        dirty_shown="$(head -3 <<< "$BUILT_DIRTY" | awk 'NR > 1 { printf ", " } { printf "%s", $0 }')"
        if [ "$dirty_count" -gt 3 ]; then dirty_shown+=" and $((dirty_count - 3)) more"; fi
        dirty_paths="$dirty_count paths"
        if [ "$dirty_count" -eq 1 ]; then dirty_paths="1 path"; fi
        TAG_IT=". HEAD ($BUILT_SHORT) is not that commit: the watch was built with $dirty_paths as the working tree has them, not as HEAD does ($dirty_shown), so commit them as they were built, then $TAG_COMMAND <that commit>"
    fi
    echo "release.sh: PASS: upload the two AABs to Google Play (docs/STORE-CHECKLIST.md, \"Each watch release\"). Once the Wear AAB is submitted to the Wear OS track, tag the commit it was built from as wear-build-$C, which means Play's Wear OS track has $WEAR_CODE$TAG_IT. Delete that tag if this Wear release is abandoned or rejected with no replacement. Sending this watch again unchanged takes TAQWA_RELEASE_WATCH=1."
elif $DRY_RUN; then
    echo "release.sh: DRY RUN PASS (phone only): $DRY_DIR/ holds the phone's two outputs, signed with the debug key, for the emulators alone; Play refuses them; $KEPT.$NOTED"
else
    echo "release.sh: PASS (phone only): upload the phone's AAB to Google Play; $KEPT (docs/STORE-CHECKLIST.md).$NOTED"
fi
