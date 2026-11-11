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
# without -Ptaqwa.unsignedRelease=true, androidApp/build.gradle.kts and wearApp's):
#   - disagreeingVersionsAreRefused: the Wear code changed by hand stops the dry run at
#     check-versions.sh, before anything is built; the same tree unchanged gets past it;
#   - the real run (no TAQWA_RELEASE_DRY_RUN) refuses without the face's pin, naming M1, and
#     without keystore.properties, and refuses a TAQWA_FACE_CERT_FILE, which is the dry run's alone,
#     before anything is built; a TAQWA_RELEASE_DRY_RUN other than 1 is refused; the dry run
#     refuses to run with no pin;
#   - aFailedStoreRunLeavesNoBundleToUpload: the real run, beside a decoy pin and a decoy
#     keystore.properties inside the scratch tree, its gradlew writing fake outputs and its face
#     the one the test key signs below, builds with
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
#     beside the same decoy, builds the face with -Ptaqwa.unsignedRelease=true, so nothing reads it.
# scripts/release.sh's dry run on this repository, beside a decoy keystore.properties
# (storeFile=/nonexistent/decoy.jks, removed by a trap), with the face signed by the test key
# (~/.android/taqwa-face-debug.keystore) through scripts/release-face.sh:
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
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

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
# It records each call and fails it, unless FAKE_OUTPUTS names a tree of outputs (the store-run
# cases below): then the stamps pass, the face's build makes the signed face the current one (or
# FAKE_CURRENT_FACE, when set), and the four builds copy that tree in. Every call first does what
# Gradle's configuration does with a keystore.properties beside it: a build of the root project
# reads it unless -Ptaqwa.unsignedRelease=true is given (androidApp/build.gradle.kts, and wearApp's
# the same, configured whatever the tasks); tools/timetables is a build of its own and never does.
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
  *" checkStamps "*) ;;
  *" :watchface:assembleRelease "*)
    mkdir -p watchface/build/outputs/apk/release
    cp "\${FAKE_CURRENT_FACE:-build/face-release/watchface-release-signed.apk}" watchface/build/outputs/apk/release/watchface-release-unsigned.apk ;;
  *" :androidApp:bundleRelease "*) cp -R "\$FAKE_OUTPUTS"/. . ;;
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

# rel <env assignments...>: runs the scratch tree's release.sh.
rel() {
  status=0
  rm -f "$WORK/rel-gradlew.log" "$WORK/rel-keyreads.log"
  (cd "$REL_TREE" && env -u TAQWA_RELEASE_DRY_RUN -u TAQWA_FACE_CERT_FILE "$@" bash scripts/release.sh) > "$OUT" 2>&1 || status=$?
}
built_nothing() { [ ! -s "$WORK/rel-gradlew.log" ]; }
# Every build of the root project the fake recorded without the flag, so none but tools/timetables'
# own, which is a build of its own.
root_builds_without_flag() {
  { grep -vE -- '(^| )-p tools/timetables( |$)' "$WORK/rel-gradlew.log" 2> /dev/null || true; } \
    | { grep -vF -- "-Ptaqwa.unsignedRelease=true" || true; }
}

rel TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256"
if has "$OUT" "Versions agree" && [ -s "$WORK/rel-gradlew.log" ]; then
  pass "the scratch tree as it is gets past check-versions.sh to the stamps"
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

finish() {
  if [ "$(git status --porcelain)" = "$STATUS_BEFORE" ]; then
    pass "the tree is as the test found it"
  else
    fail "the test changed the tree:"; git status --porcelain | indent
  fi
  if [ "$FAILS" -eq 0 ]; then
    echo "test-release: PASS"
    exit 0
  fi
  echo "test-release: $FAILS failed"
  exit 1
}

# =================================================================================================
# scripts/release.sh's dry run on this repository, beside the decoy
# =================================================================================================

# Only a release.sh that knows its dry run is run here: one that does not would take the real path.
if [ "$DRY_RUN_KNOWN" -ne 1 ]; then
  fail "release.sh does not know TAQWA_RELEASE_DRY_RUN (above), so its dry runs on this repository are not run"
  finish
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
sign() { # sign <keystore> <alias> <in> <out>
  "$APKSIGNER" sign --ks "$1" --ks-key-alias "$2" --ks-pass env:TAQWA_TEST_FACE_PW --key-pass env:TAQWA_TEST_FACE_PW \
    --v4-signing-enabled false --out "$4" "$3"
}

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
python3 -I - "$UNSIGNED_FACE" "$WORK/face-stale-unsigned.apk" << 'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2], "w") as out:
    for info in src.infolist():
        out.writestr(info, src.read(info.filename))
    out.writestr(zipfile.ZipInfo("res/raw/stale_extra"), b"a face from before")
PY
sign "$TEST_KEY" taqwaface "$WORK/face-stale-unsigned.apk" "$WORK/face-stale.apk"

# -------------------------------------------------------------------------------------------------
# aFailedStoreRunLeavesNoBundleToUpload: scripts/release.sh's store path in the scratch tree, beside
# a decoy pin and a decoy keystore.properties inside that tree. Its gradlew is the fake above with
# FAKE_OUTPUTS, its face gate a stub and its face the test-signed one: nothing here reads a key.
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
cp "$WORK/face-test.apk" "$REL_TREE/$SIGNED"
# Unsigned outputs, which step 4 refuses; and signed ones (the test-signed face as each APK, a
# bundle with a signature block as each AAB), which get past step 4 and fail step 5's checks.
for output in "${REL_OUTPUTS[@]}"; do
  mkdir -p "$WORK/out-unsigned/$(dirname "$output")" "$WORK/out-signed/$(dirname "$output")"
  echo "not a bundle" > "$WORK/out-unsigned/$output"
  case "$output" in
    *.apk) cp "$WORK/face-test.apk" "$WORK/out-signed/$output" ;;
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
# Gradle would make one; its "build" is a copy of this repository's unsigned face, which the script
# then signs. Mohamed runs it at the release's step 3, before the Lead links keystore.properties,
# and it reads none even with one there.
rm -f "$WORK/rel-gradlew.log" "$WORK/rel-keyreads.log"
cp "$UNSIGNED_FACE" "$WORK/face-unsigned.apk"
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
cp "$WORK/face-test.apk" "$REL_TREE/$SIGNED"

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

cp "$WORK/face-test.apk" "$REL_TREE/$SIGNED"
sed -i '' "s/^\( *versionCode = \)$WEAR_CODE *$/\1$((WEAR_CODE - 1))/" "$REL_TREE/$WEAR_GRADLE"
dry_rel
kept "the versions" "the versions disagree"
cp "$WEAR_GRADLE" "$REL_TREE/$WEAR_GRADLE"

printf '#!/bin/bash\nexit 1\n' > "$REL_TREE/scripts/face-gate.sh"
dry_rel
kept "the face gate" "the face gate failed"
printf '#!/bin/bash\nexit 0\n' > "$REL_TREE/scripts/face-gate.sh"

cp "$WORK/face-stale.apk" "$REL_TREE/$SIGNED"
dry_rel FAKE_CURRENT_FACE="$WORK/face-test.apk"
kept "a stale face" "the signed face is not the current face"
cp "$WORK/face-test.apk" "$REL_TREE/$SIGNED"

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

dry_outputs() { find "$DRY" -type f 2> /dev/null | sort; }
# dry <face> <pin file> <log> [script]: the dry run on this repository with that face in place.
dry() {
  cp "$1" "$SIGNED"
  status=0
  env TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE="$2" bash "${4:-scripts/release.sh}" > "$3" 2>&1 || status=$?
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
