#!/usr/bin/env bash
# Tests the release path (plan 5 Task 7) with dry runs, the throwaway keys and fakes only. It never
# runs the real scripts/release.sh on this repository, never archives, exports or uploads, and never
# reads, links or uses Mohamed's upload key, his face key or keystore.properties:
#
#   scripts/test-release.sh
#
# scripts/ios-release.sh, in a scratch tree where xcodebuild and gradlew are fakes, so no case can
# reach Apple whatever the script does:
#   - `check <xcarchive>` on a fake archive passes when every app and app-extension plist reads the
#     iPhone app's version, a framework at another version included; a watch plist one code behind,
#     a watch widget at another version, no Watch/ and no watch widget each fail; the bundle check
#     runs as ${TAQWA_WATCH_BUNDLE_CHECK}, with --release and the archive's Taqwa.app
#     (theArchiveIsCheckedAsARelease), and its failure is the archive check's;
#   - `archive`, `export-check` and `all` exit 2 while TAQWA_CODESIGN or TAQWA_WATCH_BUNDLE_CHECK is
#     set, before anything is built;
#   - `archive`'s report runs the check on the archive it made, as a release;
#   - `export-check` exports the last archive locally (app-store-connect, destination export, never
#     upload), checks the IPA's Taqwa.app as a release, and deletes the IPA and its unzipped copy,
#     whether the check passes or fails;
#   - `all` (no argument) runs archive, then export-check, then upload, and stops at the first
#     failure: an archive that fails its check, an export that does not sign, an IPA that fails
#     the bundle check each leave the steps after it unrun, so nothing is uploaded unchecked.
# scripts/release.sh, in a scratch tree whose gradlew is a fake that records each call, and that
# notes a keystore.properties read wherever Gradle would read one (a build of the root project
# without -Ptaqwa.unsignedRelease=true, androidApp/build.gradle.kts and wearApp's). The scratch tree
# is a git repository of its own with no wear-build tag, so scripts/watch-ships.sh (release.sh's
# step 0) ships the watch there and these cases take the watch's path; it carries
# tools/store-listing-check.py and a stand-in for its version's Wear OS notes, which step 1 asks for
# on that path:
#   - disagreeingVersionsAreRefused: the Wear code changed by hand stops the dry run at
#     check-versions.sh, before anything is built; the same tree unchanged gets past it and its Wear
#     OS notes;
#   - the real run (no TAQWA_RELEASE_DRY_RUN) refuses without the face's pin, naming M1, and
#     without keystore.properties, and refuses a TAQWA_FACE_CERT_FILE, which is the dry run's alone,
#     before anything is built; a TAQWA_RELEASE_DRY_RUN other than 1 is refused; the dry run
#     refuses to run with no pin;
#   - the two paths (26 November 2026), in a copy of the scratch tree tagged wear-build-<C> and then
#     bumped by its bump-version.sh, so only the version lines moved, with no face pin, no signed
#     face and no Wear OS notes for the new version, its face gate noting each run and how (whole, or
#     --fit-only), and its fake builds leaving the phone's stand-ins (an APK aapt2 links from a
#     manifest asking for androidApp/permissions.txt's permissions, an unsigned AAB):
#     aPhoneOnlyRunBuildsNoWatch: the dry run passes on the phone-only path: the fake gradlew sees the
#     phone's two tasks alone, no :wearApp or :watchface task and no -Ptaqwa.faceApk, the face gate
#     runs with --fit-only alone, no Wear OS notes are asked for, an earlier run's Wear outputs (at
#     the store paths and in build/release-dry-run/) are gone, step 5 runs the phone's checks and
#     none of the watch's, and the PASS line is the phone-only one;
#     aPhoneOnlyRunMeasuresThePhonesForms: a --fit-only run that fails stops the phone-only path at
#     step 2, before any build, and leaves the last dry run's outputs;
#     aPhoneOnlyStoreRunNeedsNoFace: the store run asks for keystore.properties and no pin, and beside
#     a decoy, TAQWA_FACE_CERT_FILE set, builds the phone alone and leaves no output when refused;
#     aPhoneOnlyStoreRunPasses: beside the decoy, its build leaving outputs that a scratch key standing
#     in for the upload key signed, with a new gradle.properties for step 0 to note, the store run
#     passes on the phone's two outputs alone, and its PASS line is the phone-only one and repeats
#     the note; aPhoneOnlyDryRunRepeatsTheNote: the dry run beside it repeats the note in its DRY RUN
#     PASS line too; watchShipsMustSaySo: a watch-ships.sh whose exit status and first line disagree (exit
#     1 with "ships:", exit 0 with "stays:", exit 1 with a note first, nothing printed) stops the run
#     (exit 2) before the face gate and any build;
#     aShippingWatchNeedsItsWearNotes: TAQWA_RELEASE_WATCH=1 there, with no Wear OS notes for the new
#     version, stops at step 1 naming the file, before the face gate and any build; with them
#     (theOverrides) it takes the watch's path (the whole face gate runs, step 3 asks for the signed
#     face); aWatchChangeShipsTheWatch: a file under wearApp/src committed, the watch's path again,
#     its store run asking for the pin (M1); aWatchStoreRunPasses: then, beside the decoy, with the
#     test key's pin committed at the committed pin's path, a face at the new version signed by it,
#     stubs for check-bundle.sh, wear-release-check.sh and push-token.sh, and the four outputs signed
#     by the scratch key, the store run passes, and its PASS line is the watch's, word for word:
#     upload both AABs, once the Wear AAB is submitted to the Wear OS track tag the commit it was
#     built from as wear-build-<C> (the git tag command, with HEAD's full id), delete that tag if
#     the Wear release is abandoned or rejected with no replacement, and TAQWA_RELEASE_WATCH=1 to
#     send the same watch again; aWatchBuiltUncommittedNamesNoCommit: the same run with a watch file
#     changed and a new one, neither committed, names the two files to commit first and no commit to
#     tag; theTestDataMessageKeepsItsOrder: the same with a test vector in each
#     output fails step 5, naming them in release.sh's old order (phone APK, phone AAB, Wear APK, Wear
#     AAB), and leaves nothing; TAQWA_RELEASE_WATCH=0 then keeps that change back, phone only, the fit
#     alone, PASS; TAQWA_RELEASE_WATCH=yes, and a copy with no .git, exit 2 before any build;
#   - aFailedStoreRunLeavesNoBundleToUpload: the real run, beside a decoy pin and a decoy
#     keystore.properties inside the scratch tree, its gradlew writing fake outputs and its face a
#     stand-in that aapt2 links and the test key signs (these cases judge what release.sh does with
#     a face, not the face, so none builds one), builds with
#     -Ptaqwa.storeRelease=true and the signed face; refused at step 4 (unsigned outputs) or at
#     step 5 (signed ones that fail its checks), it removes the four outputs it built; refused
#     before step 4, it leaves the outputs that were there before it;
#   - theDryRunReadsNoKeystoreProperties: beside the same decoy, every build of the root project
#     the dry run makes (step 3's face, step 4's four) passes -Ptaqwa.unsignedRelease=true, and so
#     does tools/watchface/validate.sh's (the face gate's), so no keystore.properties is read and
#     nothing names the decoy's store file; the store run, beside it, is seen reading it;
#   - aDryRunRefusedBeforeItsBuildKeepsTheLastDryRun: refused at the versions, the face gate or a
#     stale face, the dry run leaves the build/release-dry-run/ it found as it was; one that reached
#     step 4's build removes it;
#   - theFaceBuildReadsNoKeystoreProperties: scripts/release-face.sh, run there with the test key
#     beside the same decoy, builds the face with -Ptaqwa.unsignedRelease=true, so nothing reads it;
#   - theFitOnlyGateBuildsNothing: scripts/face-gate.sh over stub tools: --fit-only (the phone-only
#     path's) runs the five generators' --check and the six fit tools with the pinned fonts and never
#     tools/watchface/validate.sh, the whole gate runs validate.sh too, and --fit is exit 2.
# scripts/release.sh's dry run on this repository, beside a decoy keystore.properties
# (storeFile=/nonexistent/decoy.jks, removed by a trap), with the face signed by the test key
# (~/.android/taqwa-face-debug.keystore) through scripts/release-face.sh, and TAQWA_RELEASE_WATCH=1,
# so each judges the watch's path whatever this repository's wear-build tags say; when this version
# has no Wear OS notes (a release that keeps the watch back writes none), a stand-in is written at
# their path for these runs and removed before the end:
#   - theFlagLeavesTheFaceAsItIs: :watchface:assembleRelease built without
#     -Ptaqwa.unsignedRelease=true (before the decoy is written, so nothing is read) and then with it
#     leaves the same APK at the same path, and release-face.sh, which passes it, signs those very
#     bytes: the face never reads the flag, which only keeps :androidApp and :wearApp from reading a
#     keystore.properties beside the build;
#   - aFaceSignedByAnUnpinnedKeyIsRefused: a face signed by a scratch key, against the test pin;
#   - aStaleSignedFaceIsRefused: a test-signed face with one extra res/ entry;
#     both refused at step 3, before the build, each with build/release-dry-run/ seeded with one
#     marker file named like an output first, so a run that emptied it, or removed only its
#     outputs, fails even where no dry run ran before; the marker is as it was, and what the
#     directory held before is put back after them;
#   - aFaceSignedWithTheAppKeyIsRefused: the face signed with the debug key, the dry run's app key,
#     and pinned to it, is caught by the bundle check, and the run leaves no output;
#   - aPhoneOnlyDryRunOnThisRepository: the dry run with TAQWA_RELEASE_WATCH=0 (the phone-only path,
#     whatever the tags say) and no face pin passes, build/release-dry-run/ holds the phone's two
#     outputs alone, and the Wear app's output paths are empty; it runs before the watch's dry run, so
#     that one's four outputs are what the test leaves;
#   - theDryRunBuildsBothBundlesWithTheFaceInside: every output exists in build/release-dry-run/,
#     step 5's checks pass, and the Wear APK and AAB carry the signed face byte for byte;
#   - theBundledTokenIsTheBundledApks: the Wear APK's default_wf_token is push-token.sh's for the
#     face inside it, made again here (and never printed);
#   - theDryRunNeverUsesTheUploadKey: beside the decoy, every output's certificate is the debug
#     key's and nothing names the decoy's store file; a scratch copy of release.sh with
#     -Ptaqwa.unsignedRelease=true taken out fails on the decoy.
# The dry runs on this repository run only once the scratch tree has shown that release.sh knows its
# dry run (TAQWA_RELEASE_DRY_RUN=yes refused): a release.sh without one would take the store path.
# It refuses to start (exit 2) while a keystore.properties file or link is in the root, which may be
# the upload key's, or while build/face-release/ holds an APK, which may be a face Mohamed signed. It
# leaves the last dry run's outputs in build/release-dry-run/ and the tree as it found it.
#
#   TAQWA_TEST_RELEASE_SCRATCH_ONLY=1 scripts/test-release.sh
#
# runs the cases in the scratch trees alone and stops before the dry runs on this repository: no
# Gradle build of this repository and no release-face.sh on it, in seconds. Its last line says so.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

case "${TAQWA_TEST_RELEASE_SCRATCH_ONLY-}" in
  "") SCRATCH_ONLY=0 ;;
  1) SCRATCH_ONLY=1 ;;
  *) echo "TAQWA_TEST_RELEASE_SCRATCH_ONLY is 1 or unset, not '$TAQWA_TEST_RELEASE_SCRATCH_ONLY'" >&2; exit 2 ;;
esac

PROPS=keystore.properties
DECOY_STORE=/nonexistent/decoy.jks
TEST_KEY="$HOME/.android/taqwa-face-debug.keystore"
DEBUG_KEY="$HOME/.android/debug.keystore"
FACE_DIR=build/face-release
SIGNED="$FACE_DIR/watchface-release-signed.apk"
UNSIGNED_FACE=watchface/build/outputs/apk/release/watchface-release-unsigned.apk
DRY=build/release-dry-run
NO_FLAG_COPY=scripts/.test-release-without-unsigned.sh

if [ -e "$PROPS" ] || [ -L "$PROPS" ]; then
  echo "$ROOT/$PROPS is there, which may be the upload key's: this test never touches it, so it does not run" >&2
  exit 2
fi
if compgen -G "$FACE_DIR/*.apk" > /dev/null; then
  echo "$FACE_DIR holds an APK, which may be a face signed for a release: move it away first" >&2
  exit 2
fi
[ ! -e "$NO_FLAG_COPY" ] || { echo "$NO_FLAG_COPY is left from a run before: remove it first" >&2; exit 2; }
[ -f "$TEST_KEY" ] || { echo "the test key $TEST_KEY is missing: run scripts/face-debug-key.sh" >&2; exit 2; }
[ -f "$DEBUG_KEY" ] || { echo "the debug key $DEBUG_KEY is missing: any debug build makes it" >&2; exit 2; }
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2> /dev/null)}"
TOOLS="$(ls -d "$SDK"/build-tools/*/ 2> /dev/null | sort -V | tail -1)"
APKSIGNER="${TOOLS}apksigner"
AAPT2="${TOOLS}aapt2"
[ -x "$APKSIGNER" ] && [ -x "$AAPT2" ] || { echo "no apksigner or aapt2 under $SDK/build-tools" >&2; exit 2; }
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-test-release.XXXXXX")
wrote_decoy=0
# The Wear OS notes of this version, which the dry runs on this repository (the watch's path) ask
# for: a release that keeps the watch back writes none, so then the test writes a stand-in there for
# those runs alone and removes it before its end (wrote_notes).
REPO_WEAR_NOTES="docs/store/release-notes/$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' iosApp/iosApp/Info.plist)-$((100000 + $(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' iosApp/iosApp/Info.plist)))-wear.txt"
wrote_notes=0
remove_notes() {
  if [ "$wrote_notes" -eq 1 ] && [ -f "$ROOT/$REPO_WEAR_NOTES" ] && [ ! -L "$ROOT/$REPO_WEAR_NOTES" ] \
    && grep -q 'test-release.sh: a stand-in note' "$ROOT/$REPO_WEAR_NOTES"; then
    rm -f "$ROOT/$REPO_WEAR_NOTES"
  fi
  wrote_notes=0
}
# While the repository's build/release-dry-run/ is seeded (the two step-3 refusals below), what it
# held before waits in $DRY_HELD, or nothing did; the seeded directory goes and that comes back.
DRY_HELD="$WORK/release-dry-run-held"
dry_seeded=0
put_back_dry() {
  [ "$dry_seeded" -eq 1 ] || return 0
  rm -rf "${ROOT:?}/$DRY"
  if [ -e "$DRY_HELD" ]; then mv "$DRY_HELD" "$ROOT/$DRY"; fi
  dry_seeded=0
}
cleanup() {
  # Only the decoy this test wrote, and never through a link.
  if [ "$wrote_decoy" -eq 1 ] && [ -f "$ROOT/$PROPS" ] && [ ! -L "$ROOT/$PROPS" ]; then rm -f "$ROOT/$PROPS"; fi
  rm -f "$ROOT/$NO_FLAG_COPY"
  remove_notes
  put_back_dry
  rm -rf "$WORK" "${ROOT:?}/$FACE_DIR"
}
trap cleanup EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }
indent() { sed 's/^/    /'; }
# args_of <file>: a recorded argument list on one line, or nothing when none was recorded.
args_of() { { cat "$1" 2> /dev/null || true; } | xargs; }
# line_of <file> <n>: line n of a file, or nothing.
line_of() { sed -n "${2}p" "$1" 2> /dev/null || true; }
# has <file> <words...>: every word is in the file.
has() { local file=$1 word; shift; for word in "$@"; do grep -qF -- "$word" "$file" || return 1; done; }

STATUS_BEFORE=$(git status --porcelain)
V=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' iosApp/iosApp/Info.plist)
C=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' iosApp/iosApp/Info.plist)

# =================================================================================================
# scripts/ios-release.sh, in a scratch tree with a fake xcodebuild and a fake gradlew
# =================================================================================================

cat > "$WORK/fake-lib.sh" << 'LIB'
# plist <file> <id> <version> <build>
plist() {
  mkdir -p "$(dirname "$1")"
  cat > "$1" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleIdentifier</key>
	<string>$2</string>
	<key>CFBundleShortVersionString</key>
	<string>$3</string>
	<key>CFBundleVersion</key>
	<string>$4</string>
</dict>
</plist>
EOF
}
# fake_archive <dir.xcarchive> <version> <build>: the bundles an iPhone archive with the watch holds,
# and a framework that keeps its own version.
fake_archive() {
  local app="$1/Products/Applications/Taqwa.app"
  plist "$app/Info.plist" world.taqwa.ios "$2" "$3"
  plist "$app/PlugIns/TaqwaWidget.appex/Info.plist" world.taqwa.ios.widget "$2" "$3"
  plist "$app/Watch/TaqwaWatch.app/Info.plist" world.taqwa.ios.watchkitapp "$2" "$3"
  plist "$app/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex/Info.plist" world.taqwa.ios.watchkitapp.widgets "$2" "$3"
  plist "$app/Frameworks/shared.framework/Info.plist" world.taqwa.shared 9.9.9 1
  head -c 300000 /dev/zero > "$app/Watch/TaqwaWatch.app/TaqwaWatch"
  head -c 120000 /dev/zero > "$app/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex/TaqwaWatchWidgets"
}
LIB
. "$WORK/fake-lib.sh"

IOS_TREE="$WORK/ios-tree"
mkdir -p "$IOS_TREE/scripts" "$IOS_TREE/iosApp/Configuration" "$IOS_TREE/iosApp/iosApp" "$WORK/bin"
cp scripts/ios-release.sh scripts/xcode.sh "$IOS_TREE/scripts/"
cp iosApp/Configuration/Config.xcconfig "$IOS_TREE/iosApp/Configuration/"
cp iosApp/iosApp/Info.plist "$IOS_TREE/iosApp/iosApp/"
# The tree's own bundle check: a recorder, so a script that ignores TAQWA_WATCH_BUNDLE_CHECK, or
# that archive and export-check run, is seen. TREE_CHECK_EXIT is its exit; TREE_CHECK_EXIT_IPA, when
# set, its exit on an exported IPA's Taqwa.app (export-check's), so the archive's check can pass and
# the export's fail.
cat > "$IOS_TREE/scripts/check-watch-bundle.sh" << EOF
#!/bin/bash
printf '%s\n' "\$@" > "$WORK/tree-check.args"
case "\${2-}" in */Payload/Taqwa.app) exit \${TREE_CHECK_EXIT_IPA:-\${TREE_CHECK_EXIT:-0}} ;; esac
exit \${TREE_CHECK_EXIT:-0}
EOF
# The bundle check TAQWA_WATCH_BUNDLE_CHECK names in the check cases. STUB_EXIT is its exit.
cat > "$WORK/bundle-check-stub" << EOF
#!/bin/bash
printf '%s\n' "\$@" > "$WORK/stub-check.args"
exit \${STUB_EXIT:-0}
EOF
cat > "$IOS_TREE/gradlew" << EOF
#!/bin/bash
echo "\$*" >> "$WORK/ios-gradlew.log"
EOF
# The fake xcodebuild: Xcode 27 for xcode.sh, an archive of fake bundles, and an export that writes
# an IPA of the archive's Taqwa.app. Every call is recorded, and each archive and export, by its
# destination, in ios-steps.log; nothing reaches Apple. FAKE_EXPORT_FAIL=<destination> makes that
# export fail as an export that cannot sign does.
cat > "$WORK/bin/xcodebuild" << EOF
#!/bin/bash
set -euo pipefail
. "$WORK/fake-lib.sh"
echo "\$*" >> "$WORK/xcodebuild.log"
arg() { local want=\$1; shift; while [ "\$#" -gt 1 ]; do [ "\$1" = "\$want" ] && { echo "\$2"; return; }; shift; done; }
case " \$* " in
  *" -version "*) echo "Xcode 27.2"; echo "Build version 27C5" ;;
  *" archive "*)
    echo archive >> "$WORK/ios-steps.log"
    v=\$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' iosApp/iosApp/Info.plist)
    c=\$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' iosApp/iosApp/Info.plist)
    fake_archive "\$(arg -archivePath "\$@")" "\$v" "\$c" ;;
  *" -exportArchive "*)
    destination=\$(/usr/libexec/PlistBuddy -c 'Print :destination' "\$(arg -exportOptionsPlist "\$@")")
    echo "export \$destination" >> "$WORK/ios-steps.log"
    if [ "\${FAKE_EXPORT_FAIL-}" = "\$destination" ]; then echo "error: exportArchive: No signing certificate" >&2; exit 70; fi
    out=\$(arg -exportPath "\$@")
    cp "\$(arg -exportOptionsPlist "\$@")" "$WORK/export-options.plist"
    mkdir -p "\$out" "$WORK/ipa/Payload"
    out=\$(cd "\$out" && pwd)
    cp -R "\$(arg -archivePath "\$@")/Products/Applications/Taqwa.app" "$WORK/ipa/Payload/"
    (cd "$WORK/ipa" && zip -qr "\$out/Taqwa.ipa" Payload)
    rm -rf "$WORK/ipa"
    echo "summary" > "\$out/DistributionSummary.plist" ;;
esac
EOF
chmod +x "$IOS_TREE/scripts/check-watch-bundle.sh" "$WORK/bundle-check-stub" "$IOS_TREE/gradlew" "$WORK/bin/xcodebuild"

OUT="$WORK/out.txt"
status=0
# ios <env assignments...> -- <ios-release.sh arguments...>: runs the tree's ios-release.sh.
ios() {
  local -a vars=()
  while [ "$1" != -- ]; do vars+=("$1"); shift; done
  shift
  status=0
  rm -f "$WORK/tree-check.args" "$WORK/stub-check.args"
  (cd "$IOS_TREE" && env -u TAQWA_CODESIGN -u TAQWA_WATCH_BUNDLE_CHECK PATH="$WORK/bin:$PATH" \
    ${vars[@]+"${vars[@]}"} bash scripts/ios-release.sh "$@") > "$OUT" 2>&1 || status=$?
}

ARCH="$WORK/a/Taqwa.xcarchive"
fresh_archive() { rm -rf "$WORK/a"; fake_archive "$ARCH" "$V" "$C"; }
APP_IN="$ARCH/Products/Applications/Taqwa.app"

fresh_archive
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" -- check "$ARCH"
if [ "$status" -eq 0 ]; then
  pass "ios check: equal plists pass, the framework at 9.9.9 (1) beside them too"
else
  fail "ios check on equal plists exits $status:"; indent < "$OUT"
fi
if [ "$(cat "$WORK/stub-check.args" 2> /dev/null)" = "$(printf '%s\n' --release "$APP_IN")" ]; then
  pass "theArchiveIsCheckedAsARelease: TAQWA_WATCH_BUNDLE_CHECK ran with --release and the archive's Taqwa.app"
else
  fail "theArchiveIsCheckedAsARelease: the bundle check ran with '$(args_of "$WORK/stub-check.args")'"
fi
[ ! -e "$WORK/tree-check.args" ] && pass "ios check honours TAQWA_WATCH_BUNDLE_CHECK" \
  || fail "ios check ran scripts/check-watch-bundle.sh, not TAQWA_WATCH_BUNDLE_CHECK"
if has "$OUT" TaqwaWatch.app TaqwaWatchWidgets.appex KB; then
  pass "ios check prints the watch app's and the widget extension's sizes"
else
  fail "ios check prints no sizes:"; indent < "$OUT"
fi

fresh_archive
plist "$APP_IN/Watch/TaqwaWatch.app/Info.plist" world.taqwa.ios.watchkitapp "$V" $((C - 1))
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" -- check "$ARCH"
[ "$status" -eq 1 ] && has "$OUT" TaqwaWatch.app "$((C - 1))" && pass "ios check: a watch plist one code behind fails" \
  || { fail "ios check on a watch plist one code behind exits $status:"; indent < "$OUT"; }

fresh_archive
plist "$APP_IN/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex/Info.plist" world.taqwa.ios.watchkitapp.widgets 0.0.1 "$C"
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" -- check "$ARCH"
[ "$status" -eq 1 ] && has "$OUT" TaqwaWatchWidgets.appex 0.0.1 && pass "ios check: the watch's widgets at another version fail" \
  || { fail "ios check on the watch's widgets at 0.0.1 exits $status:"; indent < "$OUT"; }

fresh_archive
rm -rf "$APP_IN/Watch"
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" -- check "$ARCH"
[ "$status" -eq 1 ] && has "$OUT" "Watch/" && pass "ios check: no Watch/ fails" \
  || { fail "ios check with no Watch/ exits $status:"; indent < "$OUT"; }

fresh_archive
rm -rf "$APP_IN/Watch/TaqwaWatch.app/PlugIns"
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" -- check "$ARCH"
[ "$status" -eq 1 ] && has "$OUT" "widget" && pass "ios check: a watch app without its widget extension fails" \
  || { fail "ios check with no watch widgets exits $status:"; indent < "$OUT"; }

fresh_archive
ios TAQWA_WATCH_BUNDLE_CHECK="$WORK/bundle-check-stub" STUB_EXIT=1 -- check "$ARCH"
[ "$status" -eq 1 ] && pass "ios check: a failing bundle check fails the archive check" \
  || { fail "ios check with a failing bundle check exits $status:"; indent < "$OUT"; }

ios -- check
[ "$status" -eq 2 ] && pass "ios check with no archive named exits 2" || { fail "ios check with no archive exits $status:"; indent < "$OUT"; }

for command in archive export-check all; do
  for var in TAQWA_CODESIGN TAQWA_WATCH_BUNDLE_CHECK; do
    rm -f "$WORK/xcodebuild.log" "$WORK/ios-gradlew.log"
    ios "$var=$WORK/bundle-check-stub" -- "$command"
    if [ "$status" -eq 2 ] && has "$OUT" "$var" && ! grep -qE 'archive|exportArchive' "$WORK/xcodebuild.log" 2> /dev/null \
      && [ ! -s "$WORK/ios-gradlew.log" ]; then
      pass "ios-release.sh $command refuses a set $var (exit 2, nothing built)"
    else
      fail "ios-release.sh $command with $var set exits $status (xcodebuild: $(args_of "$WORK/xcodebuild.log")):"
      indent < "$OUT"
    fi
  done
done

rm -f "$WORK/xcodebuild.log" "$WORK/ios-gradlew.log"
ios -- archive
TREE_ARCHIVE="build/ios-archive/Taqwa-$V-$C.xcarchive"
if [ "$status" -eq 0 ] && [ "$(line_of "$WORK/tree-check.args" 1)" = --release ] \
  && [[ "$(line_of "$WORK/tree-check.args" 2)" == *"$TREE_ARCHIVE/Products/Applications/Taqwa.app" ]]; then
  pass "archive's report checks the archive it made as a release ($TREE_ARCHIVE)"
else
  fail "archive exits $status, its bundle check ran with '$(args_of "$WORK/tree-check.args")':"; indent < "$OUT"
fi

EXPORT_DIR="$IOS_TREE/build/ios-archive/export-check-$V-$C"
for check_exit in 0 1; do
  rm -f "$WORK/export-options.plist"
  ios TREE_CHECK_EXIT=$check_exit -- export-check
  method="" destination=""
  if [ -f "$WORK/export-options.plist" ]; then
    method=$(/usr/libexec/PlistBuddy -c 'Print :method' "$WORK/export-options.plist" 2> /dev/null || true)
    destination=$(/usr/libexec/PlistBuddy -c 'Print :destination' "$WORK/export-options.plist" 2> /dev/null || true)
  fi
  left=$({ find "$EXPORT_DIR" \( -name '*.ipa' -o -name Payload \) 2> /dev/null || true; } | xargs)
  if [ "$status" -eq "$check_exit" ] && [ "$method" = app-store-connect ] && [ "$destination" = export ] \
    && [ "$(line_of "$WORK/tree-check.args" 1)" = --release ] \
    && [[ "$(line_of "$WORK/tree-check.args" 2)" == */Payload/Taqwa.app ]] && [ -z "$left" ]; then
    pass "export-check (bundle check exit $check_exit): a local app-store-connect export, its Taqwa.app checked as a release, the IPA and its copy deleted"
  else
    fail "export-check (bundle check exit $check_exit) exits $status, method '$method', destination '$destination', checked '$(args_of "$WORK/tree-check.args")', left '$left':"
    indent < "$OUT"
  fi
done
if ! grep -q upload "$WORK/xcodebuild.log" 2> /dev/null && ! grep -q '<string>upload</string>' "$WORK/export-options.plist" 2> /dev/null; then
  pass "export-check uploads nothing"
else
  fail "export-check named an upload: $(grep upload "$WORK/xcodebuild.log" 2> /dev/null | head -1)"
fi

# `all`: archive, export-check, upload, in that order, stopping at the first failure.
# all_case <what> <the steps it must take, comma-separated> <its exit: 0 or fail> <env...>
all_case() {
  local what=$1 want=$2 want_exit=$3 steps left
  shift 3
  rm -f "$WORK/ios-steps.log"
  ios "$@" -- all
  steps=$({ cat "$WORK/ios-steps.log" 2> /dev/null || true; } | paste -sd, -)
  left=$({ find "$EXPORT_DIR" \( -name '*.ipa' -o -name 'unzipped.*' \) 2> /dev/null || true; } | xargs)
  local exit_right=0
  case "$want_exit" in 0) [ "$status" -eq 0 ] && exit_right=1 ;; fail) [ "$status" -ne 0 ] && exit_right=1 ;; esac
  if [ "$steps" = "$want" ] && [ "$exit_right" -eq 1 ] && [ -z "$left" ]; then
    pass "ios-release.sh all, $what: $want, exit $status"
  else
    fail "ios-release.sh all, $what: took '$steps' (not '$want'), exit $status, export-check left '$left':"
    indent < "$OUT"
  fi
}
all_case "everything passing" "archive,export export,export upload" 0
all_case "the archive failing its check" "archive" fail TREE_CHECK_EXIT=1
all_case "the export-check export not signing" "archive,export export" fail FAKE_EXPORT_FAIL=export
all_case "the exported IPA failing the bundle check" "archive,export export" fail TREE_CHECK_EXIT_IPA=1

# =================================================================================================
# scripts/release.sh's refusals, in a scratch tree whose gradlew only records
# =================================================================================================

REL_TREE="$WORK/rel-tree"
mkdir -p "$REL_TREE"
. scripts/version-files.sh
for file in scripts/*.sh tools/watchface/validate.sh tools/watchface/lib-validators.sh \
  "$PHONE_GRADLE" "$WEAR_GRADLE" "$FACE_GRADLE" "${IOS_PLISTS[@]}" "${COMPOSE_STRINGS[@]}"; do
  mkdir -p "$REL_TREE/$(dirname "$file")"
  cp "$file" "$REL_TREE/$file"
done
# release.sh asks scripts/watch-ships.sh first, which reads git, so the scratch tree is a repository
# of its own: this repository's .gitignore and one commit. It carries no wear-build tag, so the watch
# ships there and every case on it takes the watch's path, as every release did before 26 November
# 2026.
cp .gitignore "$REL_TREE/"
# On the watch's path step 1 asks for the release's Wear OS notes (tools/store-listing-check.py
# --wear-notes), so the scratch tree carries the check and a stand-in for its version's: Play's seven
# blocks, written here, so the test does not hang on this repository's notes.
mkdir -p "$REL_TREE/tools"
cp tools/store-listing-check.py "$REL_TREE/tools/"
# wear_notes_of <tree>: the path, inside it, of the Wear OS notes its phone's version asks for.
wear_notes_of() {
  local v c
  v=$(sed -n 's/^ *versionName = "\([^"]*\)" *$/\1/p' "$1/$PHONE_GRADLE")
  c=$(sed -n 's/^ *versionCode = \([0-9]*\) *$/\1/p' "$1/$PHONE_GRADLE")
  echo "docs/store/release-notes/$v-$((100000 + c))-wear.txt"
}
# stand_in_notes <file>: Play's seven blocks, a line each.
stand_in_notes() {
  mkdir -p "$(dirname "$1")"
  local tag
  for tag in en-GB ar fr-FR tr-TR id ur bn-BD; do printf '<%s>\ntest-release.sh: a stand-in note\n</%s>\n' "$tag" "$tag"; done > "$1"
}
stand_in_notes "$REL_TREE/$(wear_notes_of "$REL_TREE")"
# git_at <tree> <git arguments>: git in a scratch tree, with an identity of its own and nothing from
# the user's configuration that could sign, hook or prompt.
git_at() {
  local tree=$1
  shift
  git -C "$tree" -c user.name=test-release -c user.email=test@example.invalid -c commit.gpgsign=false \
    -c tag.gpgsign=false -c core.hooksPath=/dev/null -c init.defaultBranch=main "$@"
}
git_at "$REL_TREE" init -q
git_at "$REL_TREE" add -A
git_at "$REL_TREE" commit -q -m "the scratch tree"
# It records each call and fails it, unless FAKE_OUTPUTS names a tree of outputs (the store-run
# cases below): then the stamps and the envelope rows pass, the face's build makes the signed face
# the current one (or FAKE_CURRENT_FACE, when set), and step 4's build copies in the outputs, from
# that tree, of each module whose bundleRelease it names (androidApp, wearApp). Every
# call first does what Gradle's configuration does with a keystore.properties beside it: a build of
# the root project reads it unless -Ptaqwa.unsignedRelease=true is given (androidApp/build.gradle.kts,
# and wearApp's the same, configured whatever the tasks); tools/timetables is a build of its own and
# never does.
# A read is noted in rel-keyreads.log, with the store file it names.
cat > "$REL_TREE/gradlew" << EOF
#!/bin/bash
echo "\$*" >> "$WORK/rel-gradlew.log"
case " \$* " in
  *" -Ptaqwa.unsignedRelease=true "* | *" -p tools/timetables "*) ;;
  *) if [ -f keystore.properties ]; then
       echo "\$(sed -n 's/^storeFile=//p' keystore.properties): \$*" >> "$WORK/rel-keyreads.log"
     fi ;;
esac
[ -n "\${FAKE_OUTPUTS-}" ] || exit 1
case " \$* " in
  *" checkStamps "* | *" checkEnvelopeRows "*) ;;
  *" :watchface:assembleRelease "*)
    mkdir -p watchface/build/outputs/apk/release
    cp "\${FAKE_CURRENT_FACE:-build/face-release/watchface-release-signed.apk}" watchface/build/outputs/apk/release/watchface-release-unsigned.apk ;;
  *" :androidApp:bundleRelease "*)
    for module in androidApp wearApp; do
      case " \$* " in
        *" :\$module:bundleRelease "*)
          [ ! -d "\$FAKE_OUTPUTS/\$module" ] || { mkdir -p "\$module" && cp -R "\$FAKE_OUTPUTS/\$module"/. "\$module"/; } ;;
      esac
    done ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$REL_TREE/gradlew"

# The test key's certificate, the pin of every dry run here but one.
export TAQWA_TEST_FACE_PW=android
cert_of_key() { "$KEYTOOL" -exportcert -keystore "$1" -alias "$2" -storepass:env TAQWA_TEST_FACE_PW 2> /dev/null | shasum -a 256 | cut -d' ' -f1; }
TEST_CERT=$(cert_of_key "$TEST_KEY" taqwaface)
DEBUG_CERT=$(cert_of_key "$DEBUG_KEY" androiddebugkey)
[[ "$TEST_CERT" =~ ^[0-9a-f]{64}$ && "$DEBUG_CERT" =~ ^[0-9a-f]{64}$ && "$TEST_CERT" != "$DEBUG_CERT" ]] \
  || { echo "cannot read the test key's and the debug key's certificates" >&2; exit 2; }
echo "$TEST_CERT" > "$WORK/test-cert.sha256"
echo "$DEBUG_CERT" > "$WORK/debug-cert.sha256"

# rel_at <tree> <env assignments...>: runs that tree's release.sh; rel <env assignments...>, the
# scratch tree's.
rel_at() {
  local tree=$1
  shift
  status=0
  rm -f "$WORK/rel-gradlew.log" "$WORK/rel-keyreads.log" "$WORK/face-gate.log"
  (cd "$tree" && env -u TAQWA_RELEASE_DRY_RUN -u TAQWA_FACE_CERT_FILE -u TAQWA_RELEASE_WATCH "$@" bash scripts/release.sh) \
    > "$OUT" 2>&1 || status=$?
}
rel() { rel_at "$REL_TREE" "$@"; }
built_nothing() { [ ! -s "$WORK/rel-gradlew.log" ]; }
# Every build of the root project the fake recorded without the flag, so none but tools/timetables'
# own, which is a build of its own.
root_builds_without_flag() {
  { grep -vE -- '(^| )-p tools/timetables( |$)' "$WORK/rel-gradlew.log" 2> /dev/null || true; } \
    | { grep -vF -- "-Ptaqwa.unsignedRelease=true" || true; }
}

rel TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if has "$OUT" "Versions agree" "wear-notes: $(basename "$(wear_notes_of "$REL_TREE")"): clean" && [ -s "$WORK/rel-gradlew.log" ]; then
  pass "the scratch tree as it is gets past check-versions.sh and its Wear OS notes to the stamps"
else
  fail "the scratch tree as it is (exit $status) did not get past check-versions.sh:"; indent < "$OUT"
fi
WEAR_CODE=$(sed -n 's/^ *versionCode = \([0-9]*\) *$/\1/p' "$REL_TREE/$WEAR_GRADLE")
sed -i '' "s/^\( *versionCode = \)$WEAR_CODE *$/\1$((WEAR_CODE - 1))/" "$REL_TREE/$WEAR_GRADLE"
rel TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
[ "$status" -eq 1 ] && has "$OUT" "$WEAR_GRADLE" "$((WEAR_CODE - 1))" && built_nothing \
  && pass "disagreeingVersionsAreRefused: the Wear code changed by hand stops the dry run before any build" \
  || { fail "disagreeingVersionsAreRefused: exit $status, gradlew '$(args_of "$WORK/rel-gradlew.log")':"; indent < "$OUT"; }
cp "$WEAR_GRADLE" "$REL_TREE/$WEAR_GRADLE"

rel
[ "$status" -eq 1 ] && has "$OUT" M1 scripts/make-face-key.sh watchface/face-release-cert.sha256 keystore.properties && built_nothing \
  && pass "the real run refuses without the face's pin, naming M1, and without keystore.properties, before any build" \
  || { fail "the real run with no pin and no keystore.properties exits $status:"; indent < "$OUT"; }

rel TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
[ "$status" -eq 1 ] && has "$OUT" TAQWA_FACE_CERT_FILE && built_nothing \
  && pass "the real run refuses TAQWA_FACE_CERT_FILE, the dry run's pin" \
  || { fail "the real run with TAQWA_FACE_CERT_FILE exits $status:"; indent < "$OUT"; }

DRY_RUN_KNOWN=0
rel TAQWA_RELEASE_DRY_RUN=yes TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if [ "$status" -eq 2 ] && has "$OUT" TAQWA_RELEASE_DRY_RUN && built_nothing; then
  pass "TAQWA_RELEASE_DRY_RUN=yes is refused (exit 2): it is 1 or unset"
  DRY_RUN_KNOWN=1
else
  fail "TAQWA_RELEASE_DRY_RUN=yes exits $status:"; indent < "$OUT"
fi

rel TAQWA_RELEASE_DRY_RUN=1
[ "$status" -eq 1 ] && has "$OUT" M1 watchface/face-release-cert.sha256 && built_nothing \
  && pass "the dry run with no TAQWA_FACE_CERT_FILE refuses without the pin, naming M1" \
  || { fail "the dry run with no pin exits $status:"; indent < "$OUT"; }

# =================================================================================================
# scripts/release.sh's two paths, in a copy of the scratch tree tagged as a watch release
# =================================================================================================

# The stand-ins, which aapt2 links from a manifest and nothing signs, so no case here needs Gradle:
# the phone's APK, the phone's package at a version asking for exactly androidApp/permissions.txt's
# permissions (the dry run signs it with the debug key, and step 5's checks of the phone pass on it);
# and the face further down, which the test key signs as release-face.sh signs.
ANDROID_JAR="$(ls -d "$SDK"/platforms/android-*/android.jar 2> /dev/null | sort -V | tail -1)"
[ -n "$ANDROID_JAR" ] || { echo "no android.jar under $SDK/platforms, which aapt2 links the stand-ins against" >&2; exit 2; }
# standin_apk <out> <package> <versionCode> <versionName> [<permissions file>]
standin_apk() {
  {
    echo "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$2\" android:versionCode=\"$3\" android:versionName=\"$4\">"
    if [ -n "${5-}" ]; then sed -e 's/#.*//' "$5" | awk 'NF { print "  <uses-permission android:name=\"" $1 "\"/>" }'; fi
    echo "  <application/>"
    echo "</manifest>"
  } > "$WORK/standin-manifest.xml"
  mkdir -p "$(dirname "$1")"
  "$AAPT2" link -o "$1" -I "$ANDROID_JAR" --manifest "$WORK/standin-manifest.xml" > "$WORK/standin-link.txt" 2>&1 \
    || { indent < "$WORK/standin-link.txt"; echo "aapt2 could not link the stand-in $1" >&2; exit 2; }
}
# standin_aab <out> [<entry>[=<file>]...]: a bundle with a manifest entry, and each entry given (that
# file's bytes, or a line), and no signature.
standin_aab() {
  mkdir -p "$(dirname "$1")"
  python3 -I - "$@" << 'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as out:
    out.writestr("base/manifest/AndroidManifest.xml", b"a stand-in manifest")
    for arg in sys.argv[2:]:
        name, _, path = arg.partition("=")
        out.writestr(name, open(path, "rb").read() if path else b"a stand-in entry")
PY
}
# add_entry <zip> <entry>: one more entry, a line, before the zip is signed.
add_entry() {
  python3 -I - "$1" "$2" << 'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as out:
    out.writestr(sys.argv[2], b"a stand-in test vector")
PY
}
sign() { # sign <keystore> <alias> <in> <out>
  "$APKSIGNER" sign --ks "$1" --ks-key-alias "$2" --ks-pass env:TAQWA_TEST_FACE_PW --key-pass env:TAQWA_TEST_FACE_PW \
    --v4-signing-enabled false --out "$4" "$3"
}

# The ship tree: the scratch tree as it is, with the phone's permissions list and a face gate that
# notes each run, committed and tagged wear-build-<C> at its version C (the last watch release); then
# its own scripts/bump-version.sh to the next minor name and C + 1, committed, so only the version
# lines moved. It has no face pin and no signed face.
SHIP_TREE="$WORK/ship-tree"
cp -R "$REL_TREE" "$SHIP_TREE"
mkdir -p "$SHIP_TREE/androidApp"
cp androidApp/permissions.txt "$SHIP_TREE/androidApp/"
# Its face gate notes each run, "ran" for the whole gate and "ran --fit-only" for the phone-only
# path's; with FAKE_FIT_FAIL set, the --fit-only run fails as a form that does not fit would.
cat > "$SHIP_TREE/scripts/face-gate.sh" << EOF
#!/bin/bash
echo "ran\${1:+ \$1}" >> "$WORK/face-gate.log"
if [ "\${1-}" = --fit-only ] && [ -n "\${FAKE_FIT_FAIL-}" ]; then echo "   FAIL: date-fit.py"; exit 1; fi
exit 0
EOF
# The watch's step-5 tools, as stubs: the bundle check and the R8 check pass, and the token script
# writes the stand-in token, which the watch's stand-in APK carries. The store run that passes on the
# watch's path (aWatchStoreRunPasses) judges what release.sh does with them, not the tools.
STANDIN_TOKEN="test-release-stand-in-token"
mkdir -p "$SHIP_TREE/tools/watchface"
printf '#!/bin/bash\necho "check-bundle: PASS (a stub)"\n' > "$SHIP_TREE/tools/watchface/check-bundle.sh"
printf '#!/bin/bash\necho "wear-release-check release: PASS (a stub)"\n' > "$SHIP_TREE/tools/wear-release-check.sh"
printf '#!/bin/bash\nprintf %%s %s > "$2"\n' "$STANDIN_TOKEN" > "$SHIP_TREE/tools/watchface/push-token.sh"
TREE_C=$(sed -n 's/^ *versionCode = \([0-9]*\) *$/\1/p' "$SHIP_TREE/$PHONE_GRADLE")
TREE_V=$(sed -n 's/^ *versionName = "\([^"]*\)" *$/\1/p' "$SHIP_TREE/$PHONE_GRADLE")
NEXT_C=$((TREE_C + 1))
NEXT_V=$(awk -F. '{ print $1 "." $2 + 1 ".0" }' <<< "$TREE_V")
LAST_TAG="wear-build-$TREE_C"
LAST_WEAR=$((100000 + TREE_C))
git_at "$SHIP_TREE" add -A
git_at "$SHIP_TREE" commit -q -m "the phone's permissions, a face gate that notes its runs, and the watch's tools as stubs"
git_at "$SHIP_TREE" tag -a -m "the last watch release" "$LAST_TAG"
(cd "$SHIP_TREE" && bash scripts/bump-version.sh "$NEXT_V" "$NEXT_C") > "$WORK/ship-bump.txt" 2>&1 \
  || { indent < "$WORK/ship-bump.txt"; echo "bump-version.sh failed in the ship tree" >&2; exit 2; }
git_at "$SHIP_TREE" commit -q -am "the bump"
# What step 4's fake build leaves: the phone's stand-ins, unsigned, at the new version; and a watch's,
# which a build that names no :wearApp task never copies in.
PHONE_OUT="$WORK/out-phone"
standin_apk "$PHONE_OUT/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk" world.taqwa.app \
  "$NEXT_C" "$NEXT_V" androidApp/permissions.txt
standin_aab "$PHONE_OUT/androidApp/build/outputs/bundle/release/androidApp-release.aab"
mkdir -p "$PHONE_OUT/wearApp/build/outputs/apk/release" "$PHONE_OUT/wearApp/build/outputs/bundle/release"
echo "a watch build" > "$PHONE_OUT/wearApp/build/outputs/apk/release/wearApp-release-unsigned.apk"
echo "a watch build" > "$PHONE_OUT/wearApp/build/outputs/bundle/release/wearApp-release.aab"
# What a store run's build leaves when it passes: the four outputs at the new version, signed by a
# scratch key that stands in for the upload key (each APK with apksigner, each AAB with jarsigner, as
# Gradle signs them), the watch app carrying the face as its default face, byte for byte, with the
# stand-in token; and the same with a test vector in each output (TESTDATA_OUT).
UPLOAD_STANDIN="$WORK/upload-standin.jks"
"$KEYTOOL" -genkeypair -keystore "$UPLOAD_STANDIN" -alias upload -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=Taqwa test upload stand-in" -storepass:env TAQWA_TEST_FACE_PW -keypass:env TAQWA_TEST_FACE_PW > /dev/null 2>&1 \
  || { echo "keytool could not make the scratch key that stands in for the upload key" >&2; exit 2; }
JARSIGNER="${JAVA_HOME:+$JAVA_HOME/bin/}jarsigner"
jar_sign() { # jar_sign <in> <out>
  "$JARSIGNER" -keystore "$UPLOAD_STANDIN" -storepass:env TAQWA_TEST_FACE_PW -keypass:env TAQWA_TEST_FACE_PW \
    -signedjar "$2" "$1" upload > "$WORK/jarsigner.txt" 2>&1 \
    || { indent < "$WORK/jarsigner.txt"; echo "jarsigner could not sign $1 with the scratch key" >&2; exit 2; }
}
# The face at the new version, signed by the test key as release-face.sh signs.
NEXT_FACE="$WORK/next-face.apk"
standin_apk "$WORK/next-face-unsigned.apk" world.taqwa.app.watchface "$NEXT_C" "$NEXT_V"
sign "$TEST_KEY" taqwaface "$WORK/next-face-unsigned.apk" "$NEXT_FACE" > /dev/null
# standin_wear_apk <out>: the watch app's stand-in at the new version, the face in its assets and the
# stand-in token as its default_wf_token, unsigned.
standin_wear_apk() {
  local dir="$WORK/wear-standin"
  rm -rf "$dir"
  mkdir -p "$dir/res/values" "$dir/assets"
  cp "$NEXT_FACE" "$dir/assets/default_watchface.apk"
  printf '<resources>\n  <string name="default_wf_token" translatable="false">%s</string>\n</resources>\n' "$STANDIN_TOKEN" \
    > "$dir/res/values/face_token.xml"
  printf '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="world.taqwa.app" android:versionCode="%s" android:versionName="%s">\n  <application/>\n</manifest>\n' \
    "$((100000 + NEXT_C))" "$NEXT_V" > "$dir/manifest.xml"
  { "$AAPT2" compile --dir "$dir/res" -o "$dir/res.zip" \
    && "$AAPT2" link -o "$1" -I "$ANDROID_JAR" --manifest "$dir/manifest.xml" -A "$dir/assets" "$dir/res.zip"; } > "$WORK/standin-link.txt" 2>&1 \
    || { indent < "$WORK/standin-link.txt"; echo "aapt2 could not link the watch's stand-in" >&2; exit 2; }
}
# signed_outputs <dir> [test data]: the four outputs, signed, under <dir> as the fake build copies them.
signed_outputs() {
  local dir=$1 data=${2-} raw="$WORK/raw-outputs"
  rm -rf "$dir" "$raw"
  mkdir -p "$raw" "$dir/androidApp/build/outputs/apk/release" "$dir/androidApp/build/outputs/bundle/release" \
    "$dir/wearApp/build/outputs/apk/release" "$dir/wearApp/build/outputs/bundle/release"
  standin_apk "$raw/phone.apk" world.taqwa.app "$NEXT_C" "$NEXT_V" androidApp/permissions.txt
  standin_wear_apk "$raw/wear.apk"
  local -a phone_entries=() wear_entries=("base/assets/default_watchface.apk=$NEXT_FACE")
  if [ -n "$data" ]; then
    add_entry "$raw/phone.apk" assets/vectors/feed-vectors.json
    add_entry "$raw/wear.apk" assets/vectors/watch-rules-vectors.json
    phone_entries+=(base/assets/sample-feed-capetown.json)
    wear_entries+=(base/assets/vectors/feed-vectors.json)
  fi
  standin_aab "$raw/phone.aab" ${phone_entries[@]+"${phone_entries[@]}"}
  standin_aab "$raw/wear.aab" "${wear_entries[@]}"
  sign "$UPLOAD_STANDIN" upload "$raw/phone.apk" "$dir/androidApp/build/outputs/apk/release/androidApp-release.apk" > /dev/null
  sign "$UPLOAD_STANDIN" upload "$raw/wear.apk" "$dir/wearApp/build/outputs/apk/release/wearApp-release.apk" > /dev/null
  jar_sign "$raw/phone.aab" "$dir/androidApp/build/outputs/bundle/release/androidApp-release.aab"
  jar_sign "$raw/wear.aab" "$dir/wearApp/build/outputs/bundle/release/wearApp-release.aab"
}
SIGNED_OUT="$WORK/out-store"
TESTDATA_OUT="$WORK/out-store-test-data"
signed_outputs "$SIGNED_OUT"
signed_outputs "$TESTDATA_OUT" with-test-data

ship() { rel_at "$SHIP_TREE" FAKE_OUTPUTS="$PHONE_OUT" "$@"; }
ship_dry() { ship TAQWA_RELEASE_DRY_RUN=1 "$@"; }
# An earlier run's Wear outputs, at the store paths and in the dry run's directory.
stale_wear() {
  local output
  for output in wearApp/build/outputs/apk/release/wearApp-release.apk wearApp/build/outputs/bundle/release/wearApp-release.aab \
    "$DRY/wearApp-release-DRY-RUN.apk" "$DRY/wearApp-release-DRY-RUN.aab"; do
    mkdir -p "$SHIP_TREE/$(dirname "$output")"
    echo "an earlier run's" > "$SHIP_TREE/$output"
  done
}
# ship_files <dir...>: the files under those directories of the ship tree, on one line.
ship_files() { (cd "$SHIP_TREE" && find "$@" -type f 2> /dev/null | sort | xargs) || true; }
gate_ran() { [ -s "$WORK/face-gate.log" ]; }
# gate_was <whole|--fit-only>: the face gate ran once, so; gate_how says how it ran.
gate_was() {
  local want="ran"
  [ "$1" = whole ] || want="ran $1"
  [ "$(cat "$WORK/face-gate.log" 2> /dev/null)" = "$want" ]
}
gate_how() { if gate_ran; then xargs < "$WORK/face-gate.log"; else echo "did not run"; fi; }
NEXT_WEAR_NOTES="docs/store/release-notes/$NEXT_V-$((100000 + NEXT_C))-wear.txt"
# The watch's builds and the face's argument, as the fake recorded them.
watch_builds() { grep -E ':wearApp|:watchface|-Ptaqwa\.faceApk' "$WORK/rel-gradlew.log" 2> /dev/null || true; }
KEPT_LINE="the watch has not changed since $LAST_TAG, so Play's Wear OS track keeps serving $LAST_WEAR"

# aPhoneOnlyRunBuildsNoWatch: the dry run, with only the versions bumped since the tag.
if [ ! -e "$SHIP_TREE/watchface/face-release-cert.sha256" ] && [ ! -e "$SHIP_TREE/$FACE_DIR" ] \
  && [ ! -e "$SHIP_TREE/$NEXT_WEAR_NOTES" ] && [ "$(wear_notes_of "$SHIP_TREE")" = "$NEXT_WEAR_NOTES" ]; then
  pass "aPhoneOnlyRunBuildsNoWatch: the ship tree has no face pin, no signed face and no Wear OS notes for $NEXT_V ($NEXT_C)"
else
  fail "aPhoneOnlyRunBuildsNoWatch: the ship tree holds a face pin, a signed face or $NEXT_WEAR_NOTES, so the case cannot show none is needed"
fi
stale_wear
ship_dry
if [ "$status" -eq 0 ] && has "$OUT" "stays: the watch has not changed since $LAST_TAG" \
  "release.sh: DRY RUN PASS (phone only): $DRY/ holds the phone's two outputs" "$KEPT_LINE"; then
  pass "aPhoneOnlyRunBuildsNoWatch: the dry run passes, and its PASS line is the phone-only one ($KEPT_LINE)"
else
  fail "aPhoneOnlyRunBuildsNoWatch: the dry run exits $status:"; tail -30 "$OUT" | indent
fi
if [ -z "$(watch_builds)" ] && gate_was --fit-only \
  && grep -qx -- "-q :androidApp:bundleRelease :androidApp:assembleRelease -Ptaqwa.storeRelease=true -Ptaqwa.unsignedRelease=true" \
    "$WORK/rel-gradlew.log"; then
  pass "aPhoneOnlyRunBuildsNoWatch: the fake gradlew saw the phone's two tasks alone, no :wearApp or :watchface task, no -Ptaqwa.faceApk, and the face gate ran with --fit-only alone"
else
  fail "aPhoneOnlyRunBuildsNoWatch: the builds were '$(args_of "$WORK/rel-gradlew.log")', the face gate: $(gate_how)"
fi
if ! grep -q 'wear-notes' "$OUT"; then
  pass "aPhoneOnlyRunBuildsNoWatch: step 1 asks for no Wear OS notes"
else
  fail "aPhoneOnlyRunBuildsNoWatch: step 1 asked for Wear OS notes: $(grep 'wear-notes' "$OUT" | head -2 | xargs)"
fi
left_wear=$(ship_files wearApp/build)
left_dry=$(ship_files "$DRY")
if [ -z "$left_wear" ] && [ "$left_dry" = "$DRY/androidApp-release-DRY-RUN.aab $DRY/androidApp-release-DRY-RUN.apk" ]; then
  pass "aPhoneOnlyRunBuildsNoWatch: the Wear output paths are empty afterwards, and $DRY/ holds the phone's two outputs alone"
else
  fail "aPhoneOnlyRunBuildsNoWatch: wearApp/build holds '$left_wear', $DRY/ '$left_dry'"
fi
for line in "the phone is $NEXT_V ($NEXT_C)" "the phone APK is signed by one certificate ($DEBUG_CERT)" \
  "androidApp/permissions.txt" "androidApp-release-DRY-RUN.aab is signed by its APK's certificate" \
  "no output holds a sample feed or a test vector"; do
  has "$OUT" "$line" && pass "aPhoneOnlyRunBuildsNoWatch: step 5 says: $line" || fail "aPhoneOnlyRunBuildsNoWatch: step 5 does not say: $line"
done
if ! grep -qE 'check-bundle|wear-release-check|the Wear app is|the face is|default_wf_token' "$OUT"; then
  pass "aPhoneOnlyRunBuildsNoWatch: step 5 runs none of the watch's checks"
else
  fail "aPhoneOnlyRunBuildsNoWatch: step 5 ran a watch check: $(grep -E 'check-bundle|wear-release-check|the Wear app is|the face is|default_wf_token' "$OUT" | head -3 | xargs)"
fi

# aPhoneOnlyRunMeasuresThePhonesForms: the face Play keeps serving must draw what this phone sends,
# so a fit that fails stops the phone-only run at step 2, before the build, and the last dry run's
# outputs stay.
kept_dry=$(ship_files "$DRY")
ship_dry FAKE_FIT_FAIL=1
if [ "$status" -eq 1 ] && has "$OUT" "the face Play keeps serving does not fit what this phone sends" "FAIL: date-fit.py" \
  && gate_was --fit-only && ! grep -q ':androidApp:' "$WORK/rel-gradlew.log" && [ -n "$kept_dry" ] && [ "$(ship_files "$DRY")" = "$kept_dry" ]; then
  pass "aPhoneOnlyRunMeasuresThePhonesForms: a fit that fails refuses the phone-only run at step 2, before any build, and $DRY/ stays"
else
  fail "aPhoneOnlyRunMeasuresThePhonesForms: exit $status, the face gate: $(gate_how), built '$(args_of "$WORK/rel-gradlew.log")', $DRY/ '$(ship_files "$DRY")':"
  tail -15 "$OUT" | indent
fi

# aPhoneOnlyStoreRunNeedsNoFace: the store run asks for keystore.properties, and no pin; beside a
# decoy, with TAQWA_FACE_CERT_FILE set (which only the watch's path refuses), it builds the phone alone
# for the store, and its unsigned stand-ins, refused at step 4, are removed with the Wear outputs.
ship
if [ "$status" -eq 1 ] && has "$OUT" keystore.properties && ! grep -qE 'M1|face-release-cert|TAQWA_FACE_CERT_FILE' "$OUT" && built_nothing; then
  pass "aPhoneOnlyStoreRunNeedsNoFace: with no keystore.properties the store run is refused for it alone, no pin asked for, before any build"
else
  fail "aPhoneOnlyStoreRunNeedsNoFace: the store run with no keystore.properties exits $status:"; indent < "$OUT"
fi
printf 'storeFile=%s\nstorePassword=decoy\nkeyAlias=decoy\nkeyPassword=decoy\n' "$DECOY_STORE" > "$SHIP_TREE/$PROPS"
stale_wear
ship TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
left=$(ship_files wearApp/build androidApp/build)
if [ "$status" -eq 1 ] && has "$OUT" "is not signed" "outputs it built are removed" && ! has "$OUT" "TAQWA_FACE_CERT_FILE is set" \
  && [ -z "$left" ] && [ -z "$(watch_builds)" ] && gate_was --fit-only \
  && grep -qx -- "-q :androidApp:bundleRelease :androidApp:assembleRelease -Ptaqwa.storeRelease=true" "$WORK/rel-gradlew.log"; then
  pass "aPhoneOnlyStoreRunNeedsNoFace: beside a decoy it builds the phone alone with -Ptaqwa.storeRelease=true, and refused at step 4 leaves no output, the Wear app's included"
else
  fail "aPhoneOnlyStoreRunNeedsNoFace: the store run beside a decoy exits $status, built '$(args_of "$WORK/rel-gradlew.log")', left '$left':"
  tail -20 "$OUT" | indent
fi
rm -f "$SHIP_TREE/$PROPS"
rm -rf "${SHIP_TREE:?}/$DRY"

# aPhoneOnlyStoreRunPasses: the store run on the phone-only path, beside the decoy, its build leaving
# the phone's outputs signed by the scratch key (and a watch's, which it must not copy in), with a new
# gradle.properties for step 0 to note: it passes, its PASS line is the phone-only one and repeats the
# note, and the phone's two outputs alone are left.
printf 'storeFile=%s\nstorePassword=decoy\nkeyAlias=decoy\nkeyPassword=decoy\n' "$DECOY_STORE" > "$SHIP_TREE/$PROPS"
echo "org.gradle.caching=true" > "$SHIP_TREE/gradle.properties"
stale_wear
ship FAKE_OUTPUTS="$SIGNED_OUT"
pass_line=$(grep '^release.sh: PASS' "$OUT" || true)
want_start="release.sh: PASS (phone only): upload the phone's AAB to Google Play; $KEPT_LINE (docs/STORE-CHECKLIST.md). Step 0's note, to weigh before the upload: what the watch is built with beside its own modules has changed since $LAST_TAG"
if [ "$status" -eq 0 ] && [ "${pass_line:0:${#want_start}}" = "$want_start" ] && grep -qF "gradle.properties; it does not ship the watch on its own" <<< "$pass_line" \
  && [ -z "$(watch_builds)" ] && gate_was --fit-only && [ -z "$(ship_files wearApp/build)" ] \
  && [ "$(ship_files androidApp/build/outputs)" = "androidApp/build/outputs/apk/release/androidApp-release.apk androidApp/build/outputs/bundle/release/androidApp-release.aab" ]; then
  pass "aPhoneOnlyStoreRunPasses: the store run passes on the phone's two signed outputs alone, and its PASS line repeats step 0's note: $pass_line"
else
  fail "aPhoneOnlyStoreRunPasses: exit $status, PASS line '$pass_line', built '$(args_of "$WORK/rel-gradlew.log")', left '$(ship_files androidApp/build wearApp/build)':"
  tail -30 "$OUT" | indent
fi
for line in "the phone APK is signed by one certificate" "androidApp-release.aab is signed by its APK's certificate" \
  "the phone is $NEXT_V ($NEXT_C)" "no output holds a sample feed or a test vector"; do
  has "$OUT" "$line" && pass "aPhoneOnlyStoreRunPasses: step 5 says: $line" || fail "aPhoneOnlyStoreRunPasses: step 5 does not say: $line"
done
# aPhoneOnlyDryRunRepeatsTheNote: the dry run beside the same gradle.properties: its DRY RUN PASS line
# repeats step 0's note as the store run's does.
ship_dry
pass_line=$(grep '^release.sh: DRY RUN PASS' "$OUT" || true)
want_start="release.sh: DRY RUN PASS (phone only): $DRY/ holds the phone's two outputs, signed with the debug key, for the emulators alone; Play refuses them; $KEPT_LINE. Step 0's note, to weigh before the upload: what the watch is built with beside its own modules has changed since $LAST_TAG"
if [ "$status" -eq 0 ] && [ "${pass_line:0:${#want_start}}" = "$want_start" ] && grep -qF "gradle.properties; it does not ship the watch on its own" <<< "$pass_line" \
  && [ "$(grep -c '^note: ' "$OUT")" -eq 1 ] && [ -z "$(watch_builds)" ]; then
  pass "aPhoneOnlyDryRunRepeatsTheNote: the phone-only dry run passes, and its PASS line repeats step 0's note: $pass_line"
else
  fail "aPhoneOnlyDryRunRepeatsTheNote: exit $status, PASS line '$pass_line':"
  tail -30 "$OUT" | indent
fi
rm -f "$SHIP_TREE/$PROPS" "$SHIP_TREE/gradle.properties"
rm -rf "${SHIP_TREE:?}/$DRY" "${SHIP_TREE:?}/androidApp/build" "${SHIP_TREE:?}/wearApp/build"

# watchShipsMustSaySo: release.sh takes the phone-only path only when watch-ships.sh's exit status and
# its first line agree that the watch stays, and the watch's only when both say it ships; any other
# answer stops the run (exit 2) before the face gate and any build. A copy of the ship tree, its
# watch-ships.sh a stub that answers as given, and --tag as the real one would.
SAY_TREE="$WORK/say-tree"
cp -R "$SHIP_TREE" "$SAY_TREE"
for answer in "1:ships: the watch has changed" "1:" "0:stays: the watch has not changed" "1:note: a note first" "0:"; do
  printf '#!/bin/bash\nif [ "${1-}" = --tag ]; then echo %s; exit 0; fi\n[ -z "%s" ] || echo "%s"\nexit %s\n' \
    "$LAST_TAG" "${answer#*:}" "${answer#*:}" "${answer%%:*}" > "$SAY_TREE/scripts/watch-ships.sh"
  rel_at "$SAY_TREE" TAQWA_RELEASE_DRY_RUN=1 FAKE_OUTPUTS="$PHONE_OUT"
  if [ "$status" -eq 2 ] && has "$OUT" "could not say whether the watch ships" && built_nothing && ! gate_ran; then
    pass "watchShipsMustSaySo: exit ${answer%%:*} with the first line '${answer#*:}' stops the run (exit 2) before the face gate and any build"
  else
    fail "watchShipsMustSaySo: exit ${answer%%:*} with the first line '${answer#*:}': release.sh exits $status, the face gate: $(gate_how):"
    tail -10 "$OUT" | indent
  fi
done
rm -rf "$SAY_TREE"

# aShippingWatchNeedsItsWearNotes: the watch's path (TAQWA_RELEASE_WATCH=1 on the same tree) asks for
# the Wear OS notes the phone-only runs above never needed, and without them stops at step 1, before
# the face gate and before any build. Then the Lead's notes are written and committed.
ship_dry TAQWA_RELEASE_WATCH=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if [ "$status" -eq 1 ] && has "$OUT" "$(basename "$NEXT_WEAR_NOTES") is missing" "the watch ships, and its Play notes are not ready" \
  && ! gate_ran && built_nothing; then
  pass "aShippingWatchNeedsItsWearNotes: with no $(basename "$NEXT_WEAR_NOTES") the watch's path stops at step 1, before the face gate and any build"
else
  fail "aShippingWatchNeedsItsWearNotes: exit $status, the face gate: $(gate_how), built '$(args_of "$WORK/rel-gradlew.log")':"; tail -15 "$OUT" | indent
fi
stand_in_notes "$SHIP_TREE/$NEXT_WEAR_NOTES"
git_at "$SHIP_TREE" add -A
git_at "$SHIP_TREE" commit -q -m "the Wear OS notes"

# theOverrides, first: TAQWA_RELEASE_WATCH=1 takes the watch's path on the same tree. With no signed
# face it stops at step 3, after the face gate and before any build.
ship_dry TAQWA_RELEASE_WATCH=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if [ "$status" -eq 1 ] && has "$OUT" "ships: forced by TAQWA_RELEASE_WATCH=1" "wear-notes: $(basename "$NEXT_WEAR_NOTES"): clean" "no signed face" \
  && gate_was whole && ! grep -q ':androidApp:' "$WORK/rel-gradlew.log" && ! has "$OUT" "phone only"; then
  pass "theOverrides: TAQWA_RELEASE_WATCH=1 ships the watch with only its versions moved: its notes pass, the whole face gate runs, and step 3 asks for the signed face"
else
  fail "theOverrides: TAQWA_RELEASE_WATCH=1 exits $status, the face gate: $(gate_how):"; tail -15 "$OUT" | indent
fi

# aWatchChangeShipsTheWatch: a file under wearApp/src, committed after the tag: the watch's path, as
# before 26 November 2026.
mkdir -p "$SHIP_TREE/wearApp/src/main/kotlin"
echo "class Changed" > "$SHIP_TREE/wearApp/src/main/kotlin/Changed.kt"
git_at "$SHIP_TREE" add -A
git_at "$SHIP_TREE" commit -q -m "a watch change"
ship_dry TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if [ "$status" -eq 1 ] && has "$OUT" "ships: the watch has changed since $LAST_TAG" wearApp/src/main/kotlin/Changed.kt "no signed face" \
  && gate_was whole && ! grep -q ':androidApp:' "$WORK/rel-gradlew.log" && ! has "$OUT" "phone only"; then
  pass "aWatchChangeShipsTheWatch: the dry run takes the watch's path: the whole face gate runs, and step 3 asks for the signed face"
else
  fail "aWatchChangeShipsTheWatch: the dry run exits $status, the face gate: $(gate_how):"; tail -15 "$OUT" | indent
fi
ship
if [ "$status" -eq 1 ] && has "$OUT" M1 watchface/face-release-cert.sha256 keystore.properties && built_nothing; then
  pass "aWatchChangeShipsTheWatch: the store run asks for the face's pin again (M1) and keystore.properties"
else
  fail "aWatchChangeShipsTheWatch: the store run with no pin exits $status:"; indent < "$OUT"
fi

# aWatchStoreRunPasses: the store run on the watch's path, beside the decoy, with the test key's pin at
# the committed pin's path, the face at the new version that key signed, the watch's step-5 tools as
# stubs and the four outputs signed by the scratch key: it passes, and its PASS line says to upload
# both AABs, to tag wear-build-<C> once the Wear AAB is submitted to the Wear OS track, to delete that
# tag if the Wear release is abandoned or rejected with no replacement, and that sending the same
# watch again takes TAQWA_RELEASE_WATCH=1.
printf 'storeFile=%s\nstorePassword=decoy\nkeyAlias=decoy\nkeyPassword=decoy\n' "$DECOY_STORE" > "$SHIP_TREE/$PROPS"
# The pin committed, as the repository's is (M1), so HEAD holds the watch as it is built.
echo "$TEST_CERT" > "$SHIP_TREE/watchface/face-release-cert.sha256"
git_at "$SHIP_TREE" add watchface/face-release-cert.sha256
git_at "$SHIP_TREE" commit -q -m "the face's pin"
BUILT_HEAD=$(git_at "$SHIP_TREE" rev-parse HEAD)
mkdir -p "$SHIP_TREE/$FACE_DIR"
cp "$NEXT_FACE" "$SHIP_TREE/$SIGNED"
ship FAKE_OUTPUTS="$SIGNED_OUT"
pass_line=$(grep '^release.sh: PASS' "$OUT" || true)
want="release.sh: PASS: upload the two AABs to Google Play (docs/STORE-CHECKLIST.md, \"Each watch release\"). Once the Wear AAB is submitted to the Wear OS track, tag the commit it was built from as wear-build-$NEXT_C, which means Play's Wear OS track has $((100000 + NEXT_C)): git tag -a wear-build-$NEXT_C -m \"$NEXT_V Wear OS ($((100000 + NEXT_C))) store build, on Play's Wear OS track\" $BUILT_HEAD. Delete that tag if this Wear release is abandoned or rejected with no replacement. Sending this watch again unchanged takes TAQWA_RELEASE_WATCH=1."
if [ "$status" -eq 0 ] && [ "$pass_line" = "$want" ] && gate_was whole \
  && grep -qF -- "-Ptaqwa.storeRelease=true -Ptaqwa.faceApk=" "$WORK/rel-gradlew.log" \
  && [ "$(ship_files wearApp/build/outputs | wc -w | tr -d ' ')" -eq 2 ] && [ "$(ship_files androidApp/build/outputs | wc -w | tr -d ' ')" -eq 2 ]; then
  pass "aWatchStoreRunPasses: the store run passes on the four signed outputs, and its PASS line is the watch's: $pass_line"
else
  fail "aWatchStoreRunPasses: exit $status, the face gate: $(gate_how), PASS line '$pass_line' (wanted '$want'):"
  tail -40 "$OUT" | indent
fi
for line in "check-bundle: PASS (a stub)" "the Wear APK's certificate is the phone APK's" "wearApp-release.aab is signed by its APK's certificate" \
  "the Wear app is $NEXT_V ($((100000 + NEXT_C)))" "the face is $NEXT_V ($NEXT_C)" "the Wear APK and AAB carry the signed face byte for byte" \
  "default_wf_token is push-token.sh's for the bundled face" "no output holds a sample feed or a test vector"; do
  has "$OUT" "$line" && pass "aWatchStoreRunPasses: step 5 says: $line" || fail "aWatchStoreRunPasses: step 5 does not say: $line"
done
# aWatchBuiltUncommittedNamesNoCommit: the same store run with a watch file changed and not
# committed, and a new one: the PASS line names no commit to tag, since HEAD does not hold the watch
# that was built, and says which files to commit first.
echo "// built, not committed" >> "$SHIP_TREE/wearApp/src/main/kotlin/Changed.kt"
echo "class Uncommitted" > "$SHIP_TREE/wearApp/src/main/kotlin/Uncommitted.kt"
ship FAKE_OUTPUTS="$SIGNED_OUT"
pass_line=$(grep '^release.sh: PASS' "$OUT" || true)
want="release.sh: PASS: upload the two AABs to Google Play (docs/STORE-CHECKLIST.md, \"Each watch release\"). Once the Wear AAB is submitted to the Wear OS track, tag the commit it was built from as wear-build-$NEXT_C, which means Play's Wear OS track has $((100000 + NEXT_C)). HEAD ($(git_at "$SHIP_TREE" rev-parse --short HEAD)) is not that commit: the watch was built with 2 paths as the working tree has them, not as HEAD does (wearApp/src/main/kotlin/Changed.kt, wearApp/src/main/kotlin/Uncommitted.kt), so commit them as they were built, then git tag -a wear-build-$NEXT_C -m \"$NEXT_V Wear OS ($((100000 + NEXT_C))) store build, on Play's Wear OS track\" <that commit>. Delete that tag if this Wear release is abandoned or rejected with no replacement. Sending this watch again unchanged takes TAQWA_RELEASE_WATCH=1."
if [ "$status" -eq 0 ] && [ "$pass_line" = "$want" ] && ! grep -qF "$BUILT_HEAD" <<< "$pass_line"; then
  pass "aWatchBuiltUncommittedNamesNoCommit: HEAD does not hold the watch that was built, so the PASS line names the two files to commit and no commit to tag"
else
  fail "aWatchBuiltUncommittedNamesNoCommit: exit $status, PASS line '$pass_line' (wanted '$want'):"
  tail -20 "$OUT" | indent
fi
git_at "$SHIP_TREE" checkout -q -- wearApp/src/main/kotlin/Changed.kt
rm -f "$SHIP_TREE/wearApp/src/main/kotlin/Uncommitted.kt"
# theTestDataMessageKeepsItsOrder: the same run with a test vector in each output fails step 5 alone,
# and names them as release.sh always has, each APK before its AAB, the phone's first.
ship FAKE_OUTPUTS="$TESTDATA_OUT"
data_line=$(grep '^FAIL: test data in a store output: ' "$OUT" || true)
if [ "$status" -eq 1 ] && has "$OUT" "1 of step 5's checks failed" "outputs it built are removed" \
  && grep -qE '^FAIL: test data in a store output: androidApp-release\.apk: [^ ]+ androidApp-release\.aab: [^ ]+ wearApp-release\.apk: [^ ]+ wearApp-release\.aab: ' <<< "$data_line" \
  && [ -z "$(ship_files wearApp/build androidApp/build)" ]; then
  pass "theTestDataMessageKeepsItsOrder: test data in each output fails step 5, named phone APK, phone AAB, Wear APK, Wear AAB, and nothing is left"
else
  fail "theTestDataMessageKeepsItsOrder: exit $status, '$data_line':"; tail -20 "$OUT" | indent
fi
# The pin stays committed: the runs below keep the watch back, or are refused before any build.
rm -f "$SHIP_TREE/$PROPS"
rm -rf "${SHIP_TREE:?}/$FACE_DIR" "${SHIP_TREE:?}/androidApp/build" "${SHIP_TREE:?}/wearApp/build"

# theOverrides, then: TAQWA_RELEASE_WATCH=0 keeps that change back, and the run passes phone only.
stale_wear
ship_dry TAQWA_RELEASE_WATCH=0
if [ "$status" -eq 0 ] && has "$OUT" "stays: forced by TAQWA_RELEASE_WATCH=0" wearApp/src/main/kotlin/Changed.kt \
  "release.sh: DRY RUN PASS (phone only)" "TAQWA_RELEASE_WATCH=0 keeps the watch back, so Play's Wear OS track keeps serving $LAST_WEAR" \
  && [ -z "$(watch_builds)" ] && gate_was --fit-only && [ -z "$(ship_files wearApp/build)" ]; then
  pass "theOverrides: TAQWA_RELEASE_WATCH=0 keeps a watch change back: phone only, the fit alone, PASS, no Wear output left"
else
  fail "theOverrides: TAQWA_RELEASE_WATCH=0 exits $status, built '$(args_of "$WORK/rel-gradlew.log")':"; tail -15 "$OUT" | indent
fi
rm -rf "${SHIP_TREE:?}/$DRY"

ship_dry TAQWA_RELEASE_WATCH=yes
[ "$status" -eq 2 ] && has "$OUT" "TAQWA_RELEASE_WATCH" && built_nothing \
  && pass "theOverrides: TAQWA_RELEASE_WATCH=yes is refused (exit 2) before any build" \
  || { fail "theOverrides: TAQWA_RELEASE_WATCH=yes exits $status:"; indent < "$OUT"; }

cp -R "$SHIP_TREE" "$WORK/no-git-tree"
rm -rf "$WORK/no-git-tree/.git"
rel_at "$WORK/no-git-tree" TAQWA_RELEASE_DRY_RUN=1 FAKE_OUTPUTS="$PHONE_OUT"
[ "$status" -eq 2 ] && has "$OUT" "not a git checkout" && built_nothing \
  && pass "a tree that is no git checkout is refused (exit 2) before any build: whether the watch ships cannot be told" \
  || { fail "a tree with no .git exits $status:"; indent < "$OUT"; }
rm -rf "$WORK/no-git-tree"

finish() {
  remove_notes
  if [ "$(git status --porcelain)" = "$STATUS_BEFORE" ]; then
    pass "the tree is as the test found it"
  else
    fail "the test changed the tree:"; git status --porcelain | indent
  fi
  local name=test-release
  [ "$SCRATCH_ONLY" -eq 0 ] || name="test-release (the scratch trees alone, TAQWA_TEST_RELEASE_SCRATCH_ONLY=1)"
  if [ "$FAILS" -eq 0 ]; then
    echo "$name: PASS"
    exit 0
  fi
  echo "$name: $FAILS failed"
  exit 1
}

# =================================================================================================
# scripts/release.sh's store path and dry run in the scratch tree, with a stand-in face
# =================================================================================================

# The face these cases put at build/face-release/: an APK aapt2 links, signed with the test key as
# release-face.sh signs; and the same with an extra res/ entry, signed the same, which is stale beside
# it. The cases judge what release.sh does with a face, not the face, so none needs Gradle.
# stale_face <in> <out>: the face with one extra res/ entry, unsigned.
stale_face() {
  python3 -I - "$1" "$2" << 'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2], "w") as out:
    for info in src.infolist():
        out.writestr(info, src.read(info.filename))
    out.writestr(zipfile.ZipInfo("res/raw/stale_extra"), b"a face from before")
PY
}
TREE_FACE_UNSIGNED="$WORK/standin-face-unsigned.apk"
TREE_FACE="$WORK/standin-face.apk"
TREE_STALE_FACE="$WORK/standin-face-stale.apk"
standin_apk "$TREE_FACE_UNSIGNED" world.taqwa.app.watchface "$TREE_C" "$TREE_V"
sign "$TEST_KEY" taqwaface "$TREE_FACE_UNSIGNED" "$TREE_FACE"
stale_face "$TREE_FACE_UNSIGNED" "$WORK/standin-face-stale-unsigned.apk"
sign "$TEST_KEY" taqwaface "$WORK/standin-face-stale-unsigned.apk" "$TREE_STALE_FACE"

# -------------------------------------------------------------------------------------------------
# aFailedStoreRunLeavesNoBundleToUpload: scripts/release.sh's store path in the scratch tree, beside
# a decoy pin and a decoy keystore.properties inside that tree. Its gradlew is the fake above with
# FAKE_OUTPUTS, its face gate a stub and its face the stand-in the test key signed: nothing here reads a key.
# -------------------------------------------------------------------------------------------------

REL_OUTPUTS=(androidApp/build/outputs/apk/release/androidApp-release.apk
  androidApp/build/outputs/bundle/release/androidApp-release.aab
  wearApp/build/outputs/apk/release/wearApp-release.apk
  wearApp/build/outputs/bundle/release/wearApp-release.aab)
printf '#!/bin/bash\nexit 0\n' > "$REL_TREE/scripts/face-gate.sh"
printf 'storeFile=%s\nstorePassword=decoy\nkeyAlias=decoy\nkeyPassword=decoy\n' "$DECOY_STORE" > "$REL_TREE/$PROPS"
mkdir -p "$REL_TREE/watchface" "$REL_TREE/androidApp" "$REL_TREE/$FACE_DIR"
echo "$TEST_CERT" > "$REL_TREE/watchface/face-release-cert.sha256"
cp androidApp/permissions.txt "$REL_TREE/androidApp/"
cp "$TREE_FACE" "$REL_TREE/$SIGNED"
# Unsigned outputs, which step 4 refuses; and signed ones (the stand-in face as each APK, a
# bundle with a signature block as each AAB), which get past step 4 and fail step 5's checks.
for output in "${REL_OUTPUTS[@]}"; do
  mkdir -p "$WORK/out-unsigned/$(dirname "$output")" "$WORK/out-signed/$(dirname "$output")"
  echo "not a bundle" > "$WORK/out-unsigned/$output"
  case "$output" in
    *.apk) cp "$TREE_FACE" "$WORK/out-signed/$output" ;;
    *) python3 -I - "$WORK/out-signed/$output" << 'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as out:
    out.writestr("base/manifest/AndroidManifest.xml", b"not a manifest")
    out.writestr("META-INF/TEST.RSA", b"not a signature")
PY
      ;;
  esac
done
rel_outputs_left() { (cd "$REL_TREE" && find androidApp/build wearApp/build -type f 2> /dev/null | sort) || true; }

# theFaceBuildReadsNoKeystoreProperties: scripts/release-face.sh in the scratch tree, beside its
# decoy, with the test key and its pin. The fake records the face's build, and notes a read where
# Gradle would make one; its "build" is a copy of the stand-in face, unsigned, which the script then
# signs. Mohamed runs it at the release's step 3, before the Lead links keystore.properties,
# and it reads none even with one there.
rm -f "$WORK/rel-gradlew.log" "$WORK/rel-keyreads.log"
cp "$TREE_FACE_UNSIGNED" "$WORK/face-unsigned.apk"
status=0
(cd "$REL_TREE" && env ANDROID_HOME="$SDK" FAKE_OUTPUTS="$WORK/out-unsigned" FAKE_CURRENT_FACE="$WORK/face-unsigned.apk" \
  TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
  TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" bash scripts/release-face.sh < /dev/null) > "$OUT" 2>&1 || status=$?
without_flag=$(root_builds_without_flag)
if [ "$status" -eq 0 ] && [ "$(grep -c . "$WORK/rel-gradlew.log" 2> /dev/null)" = 1 ] \
  && grep -q ':watchface:assembleRelease' "$WORK/rel-gradlew.log" && [ -z "$without_flag" ] \
  && [ ! -s "$WORK/rel-keyreads.log" ] && [ -f "$REL_TREE/$SIGNED" ]; then
  pass "theFaceBuildReadsNoKeystoreProperties: release-face.sh builds the face with -Ptaqwa.unsignedRelease=true beside the decoy, nothing reads it, and it signs"
else
  fail "theFaceBuildReadsNoKeystoreProperties: release-face.sh exits $status, built '$(args_of "$WORK/rel-gradlew.log")', read '$(args_of "$WORK/rel-keyreads.log")':"
  tail -10 "$OUT" | indent
fi
cp "$TREE_FACE" "$REL_TREE/$SIGNED"

rel FAKE_OUTPUTS="$WORK/out-unsigned"
if [ "$status" -eq 1 ] && has "$OUT" "is not signed" "outputs it built are removed" && [ -z "$(rel_outputs_left)" ]; then
  pass "aFailedStoreRunLeavesNoBundleToUpload: a store run refused at step 4 removes the four outputs it built"
else
  fail "aFailedStoreRunLeavesNoBundleToUpload: a store run refused at step 4 exits $status and leaves '$(rel_outputs_left | xargs)':"
  tail -20 "$OUT" | indent
fi
# The tree as release.sh's $PWD spells it (TMPDIR may end in a slash).
if grep -qF -- "-Ptaqwa.storeRelease=true -Ptaqwa.faceApk=$(cd "$REL_TREE" && pwd)/$SIGNED" "$WORK/rel-gradlew.log" \
  && ! grep -qF -- "-Ptaqwa.unsignedRelease" "$WORK/rel-gradlew.log"; then
  pass "the store run builds with -Ptaqwa.storeRelease=true and the signed face, never -Ptaqwa.unsignedRelease"
else
  fail "the store run's builds were '$(args_of "$WORK/rel-gradlew.log")'"
fi
# The fake sees a read where Gradle makes one, so the dry run's cases below can fail.
if grep -qF -- "$DECOY_STORE: " "$WORK/rel-keyreads.log" 2> /dev/null; then
  pass "the store run, beside the decoy, is seen reading keystore.properties"
else
  fail "the store run beside the decoy was not seen reading keystore.properties, so no read can be seen"
fi

rel FAKE_OUTPUTS="$WORK/out-signed"
if [ "$status" -eq 1 ] && has "$OUT" "of step 5's checks failed" "outputs it built are removed" && [ -z "$(rel_outputs_left)" ]; then
  pass "aFailedStoreRunLeavesNoBundleToUpload: signed outputs that fail step 5's checks are removed"
else
  fail "aFailedStoreRunLeavesNoBundleToUpload: a store run refused at step 5 exits $status and leaves '$(rel_outputs_left | xargs)':"
  tail -30 "$OUT" | indent
fi

# A run refused before step 4 built nothing, so what was at the output paths stays.
cp -R "$WORK/out-signed"/. "$REL_TREE"/
rm "$REL_TREE/$SIGNED"
rel FAKE_OUTPUTS="$WORK/out-signed"
if [ "$status" -eq 1 ] && has "$OUT" "no signed face" && [ "$(rel_outputs_left | grep -c .)" -eq 4 ]; then
  pass "a store run refused before step 4 leaves the outputs that were there before it"
else
  fail "a store run refused at step 3 exits $status and leaves '$(rel_outputs_left | xargs)':"; tail -20 "$OUT" | indent
fi

# -------------------------------------------------------------------------------------------------
# theDryRunReadsNoKeystoreProperties and aDryRunRefusedBeforeItsBuildKeepsTheLastDryRun: the dry
# run in the scratch tree, beside the same decoy keystore.properties and the test pin, its face gate
# the stub and its gradlew the fake, which writes the unsigned fake outputs.
# -------------------------------------------------------------------------------------------------

DRY_OUTPUTS=(androidApp-release-DRY-RUN.aab androidApp-release-DRY-RUN.apk wearApp-release-DRY-RUN.aab wearApp-release-DRY-RUN.apk)
# The scratch tree's build/release-dry-run/, as a last dry run left it, before each case.
last_dry_run() {
  rm -rf "${REL_TREE:?}/$DRY"
  mkdir -p "$REL_TREE/$DRY"
  for output in "${DRY_OUTPUTS[@]}"; do echo "the last dry run's $output" > "$REL_TREE/$DRY/$output"; done
}
rel_dry_left() { (cd "$REL_TREE" && find "$DRY" -type f -exec shasum {} + 2> /dev/null | sort) || true; }
last_dry_run
LAST_DRY=$(rel_dry_left)
dry_rel() { rel TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" FAKE_OUTPUTS="$WORK/out-unsigned" "$@"; }
# kept <where it was refused> <the refusal it names>: refused there, the last dry run is as it was.
kept() {
  if [ "$status" -eq 1 ] && has "$OUT" "$2" && [ -n "$LAST_DRY" ] && [ "$(rel_dry_left)" = "$LAST_DRY" ]; then
    pass "aDryRunRefusedBeforeItsBuildKeepsTheLastDryRun: refused at $1, $DRY/ is as it was"
  else
    fail "aDryRunRefusedBeforeItsBuildKeepsTheLastDryRun: refused at $1, exit $status, $DRY/ holds '$(rel_dry_left | awk '{ print $2 }' | xargs)':"
    tail -15 "$OUT" | indent
  fi
  last_dry_run
}

cp "$TREE_FACE" "$REL_TREE/$SIGNED"
sed -i '' "s/^\( *versionCode = \)$WEAR_CODE *$/\1$((WEAR_CODE - 1))/" "$REL_TREE/$WEAR_GRADLE"
dry_rel
kept "the versions" "the versions disagree"
cp "$WEAR_GRADLE" "$REL_TREE/$WEAR_GRADLE"

printf '#!/bin/bash\nexit 1\n' > "$REL_TREE/scripts/face-gate.sh"
dry_rel
kept "the face gate" "the face gate failed"
printf '#!/bin/bash\nexit 0\n' > "$REL_TREE/scripts/face-gate.sh"

cp "$TREE_STALE_FACE" "$REL_TREE/$SIGNED"
dry_rel FAKE_CURRENT_FACE="$TREE_FACE"
kept "a stale face" "the signed face is not the current face"
cp "$TREE_FACE" "$REL_TREE/$SIGNED"

# On to step 4's build: its unsigned fake outputs pass the signed-output refusal, then fail the debug
# key's signing, so this run reached the build and failed after it.
dry_rel
if [ "$status" -eq 1 ] && has "$OUT" "the four outputs are unsigned" "outputs it built are removed" && [ -z "$(rel_dry_left)" ]; then
  pass "aDryRunRefusedBeforeItsBuildKeepsTheLastDryRun: a dry run that failed after step 4's build removes $DRY/"
else
  fail "a dry run that failed after step 4's build exits $status, $DRY/ holds '$(rel_dry_left | awk '{ print $2 }' | xargs)':"
  tail -15 "$OUT" | indent
fi
without_flag=$(root_builds_without_flag)
if grep -q ':watchface:assembleRelease' "$WORK/rel-gradlew.log" && grep -q ':androidApp:bundleRelease' "$WORK/rel-gradlew.log" \
  && [ -z "$without_flag" ] && [ ! -s "$WORK/rel-keyreads.log" ] && ! grep -qF -- "$DECOY_STORE" "$OUT"; then
  pass "theDryRunReadsNoKeystoreProperties: step 3's face build and step 4's builds pass -Ptaqwa.unsignedRelease=true beside the decoy, and none reads it"
else
  fail "theDryRunReadsNoKeystoreProperties: built without the flag '$(xargs <<< "$without_flag")', read '$(args_of "$WORK/rel-keyreads.log")'"
fi
last_dry_run

# The face gate's builds: tools/watchface/validate.sh in the scratch tree, beside the decoy. The
# fake's build fails, so it stops there, before either validator runs.
rm -f "$WORK/rel-gradlew.log" "$WORK/rel-keyreads.log"
status=0
(cd "$REL_TREE" && bash tools/watchface/validate.sh) > "$OUT" 2>&1 || status=$?
without_flag=$(root_builds_without_flag)
if [ "$(grep -c . "$WORK/rel-gradlew.log" 2> /dev/null)" = 1 ] && grep -q ':watchface:assembleRelease' "$WORK/rel-gradlew.log" \
  && [ -z "$without_flag" ] && [ ! -s "$WORK/rel-keyreads.log" ]; then
  pass "theDryRunReadsNoKeystoreProperties: validate.sh, the face gate's, builds the face with -Ptaqwa.unsignedRelease=true beside the decoy, and nothing reads it"
else
  fail "theDryRunReadsNoKeystoreProperties: validate.sh built '$(args_of "$WORK/rel-gradlew.log")', read '$(args_of "$WORK/rel-keyreads.log")':"
  tail -10 "$OUT" | indent
fi

# theFitOnlyGateBuildsNothing: scripts/face-gate.sh itself, in a scratch tree whose generators, fit
# tools, font check and validate.sh are stubs that note their runs. --fit-only runs the five
# generators' --check and the six fit tools, with the pinned fonts, and never validate.sh (whose face
# builds are Gradle's); the whole gate runs all twelve. An argument it does not know is exit 2.
FG_TREE="$WORK/face-gate-tree"
mkdir -p "$FG_TREE/scripts" "$FG_TREE/tools/watchface"
cp scripts/face-gate.sh "$FG_TREE/scripts/"
for tool in face-arch face-centre face-moon face-sun face-art; do
  printf 'import sys\nopen(%s, "a").write("%s " + " ".join(sys.argv[1:]) + "\\n")\n' "'$WORK/face-gate-tools.log'" "$tool" > "$FG_TREE/tools/$tool.py"
done
for tool in hijri-fit window-fit slot-fit moon-fit date-fit date-fit-test; do
  printf 'import sys\nopen(%s, "a").write("%s " + " ".join(sys.argv[1:]) + "\\n")\n' "'$WORK/face-gate-tools.log'" "$tool" > "$FG_TREE/tools/watchface/$tool.py"
done
printf '#!/bin/bash\n[ "$1" = --check ]\n' > "$FG_TREE/tools/watchface/get-system-fonts.sh"
printf '#!/bin/bash\necho "validate.sh $*" >> %s\n' "'$WORK/face-gate-tools.log'" > "$FG_TREE/tools/watchface/validate.sh"
git_at "$FG_TREE" init -q
git_at "$FG_TREE" add -A
git_at "$FG_TREE" commit -q -m "a face gate over stubs"
# face_gate <arguments...>: the scratch tree's face gate, the tools' runs noted afresh.
face_gate() {
  rm -f "$WORK/face-gate-tools.log"
  status=0
  (cd "$FG_TREE" && TAQWA_SYSTEM_FONTS=/the/pinned/fonts bash scripts/face-gate.sh "$@") > "$OUT" 2>&1 || status=$?
}
ran_tools() { cut -d' ' -f1 "$WORK/face-gate-tools.log" 2> /dev/null | xargs; }
FIT_TOOLS="face-arch face-centre face-moon face-sun face-art hijri-fit window-fit slot-fit moon-fit date-fit date-fit-test"
face_gate --fit-only
if [ "$status" -eq 0 ] && [ "$(ran_tools)" = "$FIT_TOOLS" ] && has "$OUT" "PASS (--fit-only" \
  && grep -qx 'hijri-fit fonts=/the/pinned/fonts' "$WORK/face-gate-tools.log" && grep -qx 'face-arch --check' "$WORK/face-gate-tools.log"; then
  pass "theFitOnlyGateBuildsNothing: face-gate.sh --fit-only runs the five generators' --check and the six fit tools, with the pinned fonts, and never validate.sh"
else
  fail "theFitOnlyGateBuildsNothing: face-gate.sh --fit-only exits $status and ran '$(ran_tools)':"; tail -10 "$OUT" | indent
fi
face_gate
if [ "$status" -eq 0 ] && [ "$(ran_tools)" = "$FIT_TOOLS validate.sh" ] && has "$OUT" "face-gate.sh: PASS" && ! has "$OUT" "--fit-only"; then
  pass "theFitOnlyGateBuildsNothing: the whole face gate runs those and validate.sh"
else
  fail "theFitOnlyGateBuildsNothing: the whole face gate exits $status and ran '$(ran_tools)':"; tail -10 "$OUT" | indent
fi
face_gate --fit
[ "$status" -eq 2 ] && [ -z "$(ran_tools)" ] && pass "theFitOnlyGateBuildsNothing: face-gate.sh --fit is refused (exit 2) before any check" \
  || { fail "theFitOnlyGateBuildsNothing: face-gate.sh --fit exits $status and ran '$(ran_tools)':"; indent < "$OUT"; }

# TAQWA_TEST_RELEASE_SCRATCH_ONLY=1 stops here, before any build of this repository.
[ "$SCRATCH_ONLY" -eq 0 ] || finish

# =================================================================================================
# scripts/release.sh's dry run on this repository, beside the decoy
# =================================================================================================

# Only a release.sh that knows its dry run is run here: one that does not would take the real path.
if [ "$DRY_RUN_KNOWN" -ne 1 ]; then
  fail "release.sh does not know TAQWA_RELEASE_DRY_RUN (above), so its dry runs on this repository are not run"
  finish
fi
if [ ! -e "$REPO_WEAR_NOTES" ] && [ ! -L "$REPO_WEAR_NOTES" ]; then
  wrote_notes=1
  stand_in_notes "$ROOT/$REPO_WEAR_NOTES"
fi

# theFlagLeavesTheFaceAsItIs, first half: the face's release without -Ptaqwa.unsignedRelease=true,
# while no keystore.properties is in the root (the test refuses to start beside one), so nothing is
# read; then with it, after the first APK is removed, so the build packs it again.
# face_build <log> [flag]: builds :watchface:assembleRelease, or stops the test (exit 2).
face_build() {
  local log=$1
  shift
  ./gradlew -q :watchface:assembleRelease "$@" < /dev/null > "$log" 2>&1 && [ -f "$UNSIGNED_FACE" ] \
    || { indent < "$log"; echo "the face's release did not build ($*), or left no $UNSIGNED_FACE" >&2; exit 2; }
}
rm -f "$UNSIGNED_FACE"
face_build "$WORK/face-no-flag.log"
cp "$UNSIGNED_FACE" "$WORK/face-no-flag.apk"
rm -f "$UNSIGNED_FACE"
face_build "$WORK/face-flag.log" -Ptaqwa.unsignedRelease=true
if cmp -s "$UNSIGNED_FACE" "$WORK/face-no-flag.apk"; then
  pass "theFlagLeavesTheFaceAsItIs: with and without -Ptaqwa.unsignedRelease=true, $UNSIGNED_FACE is the same APK byte for byte"
else
  fail "theFlagLeavesTheFaceAsItIs: -Ptaqwa.unsignedRelease=true changes $UNSIGNED_FACE ($(shasum -a 256 < "$WORK/face-no-flag.apk" | cut -c1-12) without, $(shasum -a 256 < "$UNSIGNED_FACE" | cut -c1-12) with)"
fi
rm -f "$UNSIGNED_FACE"

cat > "$PROPS" << EOF
storeFile=$DECOY_STORE
storePassword=decoy
keyAlias=decoy
keyPassword=decoy
EOF
wrote_decoy=1

# The faces: the test key's, through release-face.sh as Mohamed signs his; a scratch key's; one with
# an extra res/ entry; and one signed with the debug key, the dry run's app key.
if ! TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
  TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" bash scripts/release-face.sh > "$OUT" 2>&1; then
  indent < "$OUT"
  echo "release-face.sh could not sign the face with the test key" >&2
  exit 2
fi
cp "$SIGNED" "$WORK/face-test.apk"
"$KEYTOOL" -genkeypair -keystore "$WORK/scratch.jks" -alias scratch -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=Taqwa test scratch" -storepass:env TAQWA_TEST_FACE_PW -keypass:env TAQWA_TEST_FACE_PW > /dev/null 2>&1

# theFlagLeavesTheFaceAsItIs, second half: release-face.sh built the face again (its unsigned APK
# was removed above), and what it signed is the build without the flag, signed by the test key as
# it signs (RSA, so the same bytes sign the same).
sign "$TEST_KEY" taqwaface "$WORK/face-no-flag.apk" "$WORK/face-no-flag-signed.apk"
if cmp -s "$UNSIGNED_FACE" "$WORK/face-no-flag.apk" && cmp -s "$WORK/face-test.apk" "$WORK/face-no-flag-signed.apk"; then
  pass "theFlagLeavesTheFaceAsItIs: release-face.sh signs, at the same path, the very APK the build without the flag makes"
else
  fail "theFlagLeavesTheFaceAsItIs: release-face.sh's face is not the build without the flag, signed by the test key"
fi
sign "$WORK/scratch.jks" scratch "$UNSIGNED_FACE" "$WORK/face-scratch.apk"
sign "$DEBUG_KEY" androiddebugkey "$UNSIGNED_FACE" "$WORK/face-appkey.apk"
stale_face "$UNSIGNED_FACE" "$WORK/face-stale-unsigned.apk"
sign "$TEST_KEY" taqwaface "$WORK/face-stale-unsigned.apk" "$WORK/face-stale.apk"

dry_outputs() { find "$DRY" -type f 2> /dev/null | sort; }
# dry <face> <pin file> <log> [script]: the dry run on this repository with that face in place. It
# judges the watch's path whatever this repository's wear-build tags say, so TAQWA_RELEASE_WATCH=1.
dry() {
  cp "$1" "$SIGNED"
  status=0
  env TAQWA_RELEASE_DRY_RUN=1 TAQWA_RELEASE_WATCH=1 TAQWA_FACE_CERT_FILE="$2" bash "${4:-scripts/release.sh}" > "$3" 2>&1 || status=$?
}

# Refused at step 3, before the build: build/release-dry-run/ is left as each found it. Each first
# seeds it with one marker file named like an output, what it held before set aside, so a run that
# emptied it, or removed only its outputs, fails even on a tree where no dry run ran before; what it
# held comes back after the two (put_back_dry).
dry_sums() { find "$DRY" -type f -exec shasum {} + 2> /dev/null | sort || true; }
seed_dry() {
  if [ "$dry_seeded" -eq 0 ]; then
    rm -rf "$DRY_HELD"
    if [ -e "$DRY" ]; then mv "$DRY" "$DRY_HELD"; fi
    dry_seeded=1
  fi
  rm -rf "$DRY"
  mkdir -p "$DRY"
  echo "test-release.sh's marker: a dry run refused before its build leaves this" > "$DRY/${DRY_OUTPUTS[1]}"
}
seed_dry
found=$(dry_sums)
dry "$WORK/face-scratch.apk" "$WORK/test-cert.sha256" "$WORK/unpinned.log"
[ "$status" -eq 1 ] && has "$WORK/unpinned.log" "$TEST_CERT" "not by the pinned" && [ -n "$found" ] && [ "$(dry_sums)" = "$found" ] \
  && pass "aFaceSignedByAnUnpinnedKeyIsRefused, and $DRY/ is as it found it (seeded, $(grep -c . <<< "$found") file)" \
  || { fail "aFaceSignedByAnUnpinnedKeyIsRefused: exit $status, $DRY/ holds '$(dry_outputs | xargs)', seeded with '$(awk '{ print $2 }' <<< "$found" | xargs)':"; tail -25 "$WORK/unpinned.log" | indent; }

seed_dry
found=$(dry_sums)
dry "$WORK/face-stale.apk" "$WORK/test-cert.sha256" "$WORK/stale.log"
[ "$status" -eq 1 ] && has "$WORK/stale.log" "the signed face is not the current face: run scripts/release-face.sh again" res/raw/stale_extra \
  && [ -n "$found" ] && [ "$(dry_sums)" = "$found" ] && pass "aStaleSignedFaceIsRefused, and $DRY/ is as it found it (seeded, $(grep -c . <<< "$found") file)" \
  || { fail "aStaleSignedFaceIsRefused: exit $status, $DRY/ holds '$(dry_outputs | xargs)', seeded with '$(awk '{ print $2 }' <<< "$found" | xargs)':"; tail -25 "$WORK/stale.log" | indent; }
put_back_dry

dry "$WORK/face-appkey.apk" "$WORK/debug-cert.sha256" "$WORK/appkey.log"
[ "$status" -eq 1 ] && has "$WORK/appkey.log" "the face is signed with the app's key" && [ -z "$(dry_outputs)" ] \
  && pass "aFaceSignedWithTheAppKeyIsRefused: the bundle check catches it, and no output is left" \
  || { fail "aFaceSignedWithTheAppKeyIsRefused: exit $status, outputs '$(dry_outputs | xargs)':"; tail -40 "$WORK/appkey.log" | indent; }

# theDryRunNeverUsesTheUploadKey: without -Ptaqwa.unsignedRelease=true the build signs with the decoy.
sed 's/ *-Ptaqwa\.unsignedRelease=true//g' scripts/release.sh > "$NO_FLAG_COPY"
if grep -qF -- "-Ptaqwa.unsignedRelease=true" scripts/release.sh && ! grep -qF -- "-Ptaqwa.unsignedRelease=true" "$NO_FLAG_COPY"; then
  dry "$WORK/face-test.apk" "$WORK/test-cert.sha256" "$WORK/no-flag.log" "$NO_FLAG_COPY"
  [ "$status" -ne 0 ] && grep -qF "$DECOY_STORE" "$WORK/no-flag.log" \
    && pass "theDryRunNeverUsesTheUploadKey: a copy of release.sh without -Ptaqwa.unsignedRelease=true fails on the decoy" \
    || { fail "theDryRunNeverUsesTheUploadKey: the copy without the flag exits $status, not on the decoy:"; tail -30 "$WORK/no-flag.log" | indent; }
else
  fail "theDryRunNeverUsesTheUploadKey: scripts/release.sh passes no -Ptaqwa.unsignedRelease=true to take out"
fi
rm -f "$NO_FLAG_COPY"
[ -z "$(dry_outputs)" ] || fail "the failed run of the copy left outputs in $DRY: $(dry_outputs | xargs)"


# aPhoneOnlyDryRunOnThisRepository: the phone-only path built for real, TAQWA_RELEASE_WATCH=0 whatever
# this repository's wear-build tags say, with no face pin given: it passes, build/release-dry-run/
# holds the phone's two outputs alone, and the Wear app's output paths are empty. It runs before the
# watch's dry run below, so the last dry run left is that one's four.
status=0
env TAQWA_RELEASE_DRY_RUN=1 TAQWA_RELEASE_WATCH=0 bash scripts/release.sh > "$WORK/phone-only.log" 2>&1 || status=$?
# A passing run leaves neither Wear directory (step 4 removes them, and builds the phone alone), and
# find exits 1 on a missing one, which pipefail and errexit would turn into the end of the test.
wear_left=$( { find wearApp/build/outputs/apk/release wearApp/build/outputs/bundle/release -type f 2> /dev/null || true; } | xargs)
if [ "$status" -eq 0 ] && has "$WORK/phone-only.log" "release.sh: DRY RUN PASS (phone only)" \
  && [ -s "$DRY/androidApp-release-DRY-RUN.aab" ] && [ -s "$DRY/androidApp-release-DRY-RUN.apk" ] \
  && [ "$(dry_outputs | wc -l | tr -d ' ')" -eq 2 ] && [ -z "$wear_left" ]; then
  pass "aPhoneOnlyDryRunOnThisRepository: TAQWA_RELEASE_WATCH=0 passes with the phone's two outputs in $DRY/ alone, and the Wear output paths are empty"
else
  fail "aPhoneOnlyDryRunOnThisRepository: exit $status, $DRY/ holds '$(dry_outputs | xargs)', the Wear output paths '$wear_left':"
  tail -60 "$WORK/phone-only.log" | indent
fi

dry "$WORK/face-test.apk" "$WORK/test-cert.sha256" "$WORK/dry.log"
OUTPUTS=(androidApp-release-DRY-RUN.aab androidApp-release-DRY-RUN.apk wearApp-release-DRY-RUN.aab wearApp-release-DRY-RUN.apk)
missing=""
for output in "${OUTPUTS[@]}"; do [ -s "$DRY/$output" ] || missing+="$output "; done
if [ "$status" -eq 0 ] && [ -z "$missing" ] && [ "$(dry_outputs | wc -l | tr -d ' ')" -eq 4 ]; then
  pass "theDryRunBuildsBothBundlesWithTheFaceInside: the four outputs in $DRY/"
else
  fail "theDryRunBuildsBothBundlesWithTheFaceInside: exit $status, missing '$missing', outputs '$(dry_outputs | xargs)':"
  tail -60 "$WORK/dry.log" | indent
fi
for line in "check-bundle: PASS" "wear-release-check release: PASS" "androidApp/permissions.txt" \
  "the Wear APK's certificate is the phone APK's" "the phone is $V ($C)" "the Wear app is $V ($((100000 + C)))" \
  "the face is $V ($C)"; do
  has "$WORK/dry.log" "$line" && pass "step 5 says: $line" || fail "step 5 does not say: $line"
done
face_sha=$(shasum -a 256 < "$WORK/face-test.apk" | cut -d' ' -f1)
for pair in "wearApp-release-DRY-RUN.apk:assets/default_watchface.apk" "wearApp-release-DRY-RUN.aab:base/assets/default_watchface.apk"; do
  file=${pair%%:*} entry=${pair#*:}
  got=$({ unzip -p "$DRY/$file" "$entry" 2> /dev/null || true; } | shasum -a 256 | cut -d' ' -f1)
  [ "$got" = "$face_sha" ] && pass "$file carries the signed face byte for byte at $entry" \
    || fail "$file's $entry is not the signed face"
done

# theDryRunNeverUsesTheUploadKey: every certificate is the debug key's, and the decoy is never named.
cert_of_apk() {
  { "$APKSIGNER" verify --print-certs "$1" 2> /dev/null || true; } \
    | sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u
}
cert_of_aab() {
  { "$KEYTOOL" -printcert -jarfile "$1" 2> /dev/null || true; } \
    | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | sort -u
}
for output in "${OUTPUTS[@]}"; do
  case "$output" in *.apk) cert=$(cert_of_apk "$DRY/$output") ;; *) cert=$(cert_of_aab "$DRY/$output") ;; esac
  [ "$cert" = "$DEBUG_CERT" ] && pass "theDryRunNeverUsesTheUploadKey: $output is signed by the debug key alone" \
    || fail "theDryRunNeverUsesTheUploadKey: $output is signed by '$(xargs <<< "$cert")', not the debug key $DEBUG_CERT"
done
! grep -qF "$DECOY_STORE" "$WORK/dry.log" && pass "theDryRunNeverUsesTheUploadKey: nothing in the dry run names $DECOY_STORE" \
  || fail "theDryRunNeverUsesTheUploadKey: the dry run named the decoy's store file"

# theBundledTokenIsTheBundledApks, never printed.
unzip -p "$DRY/wearApp-release-DRY-RUN.apk" assets/default_watchface.apk > "$WORK/bundled-face.apk" 2> /dev/null || true
bundled_token=$({ "$AAPT2" dump resources "$DRY/wearApp-release-DRY-RUN.apk" 2> /dev/null || true; } \
  | { grep -A1 -E ' string/default_wf_token$' || true; } | sed -n 2p | sed -E 's/^[[:space:]]*\([^)]*\) "(.*)"[[:space:]]*$/\1/')
if bash tools/watchface/push-token.sh "$WORK/bundled-face.apk" "$WORK/token-again" > /dev/null 2>&1 && [ -s "$WORK/token-again" ] \
  && [ "$bundled_token" = "$(cat "$WORK/token-again")" ]; then
  pass "theBundledTokenIsTheBundledApks: push-token.sh, run again on the bundled face, gives the bundled token"
else
  fail "theBundledTokenIsTheBundledApks: the Wear APK's token is not push-token.sh's for the face it carries (${#bundled_token} characters)"
fi
if [ -n "$bundled_token" ] && ! grep -qF "$bundled_token" "$WORK/dry.log"; then
  pass "the dry run never prints the token"
else
  fail "the dry run printed the token, or there is none"
fi

finish
