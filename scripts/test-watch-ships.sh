#!/usr/bin/env bash
# Tests scripts/watch-ships.sh in scratch git repositories, never on this one (no tag of this
# repository is read, made or moved):
#
#   scripts/test-watch-ships.sh
#
# Each scratch repository holds a copy of watch-ships.sh, the version files with the scripts that
# write them (scripts/version-files.sh names them), every build file of this repository with its
# version catalog (the catalog's keys are judged by which build files name them), this repository's
# .gitignore, and a file under each of wearApp/src/main, watchcore/src/commonMain and
# watchface/src/main. Then:
#   - noTagShips: with no wear-build tag the watch ships, saying no watch release is tagged, and
#     --tag prints nothing;
#   - aVersionOnlyBumpStays: tagged wear-build-46, then scripts/bump-version.sh to the next name and
#     code 58 (wearApp 100058, the face 58), committed: it stays, naming the tag and 100046, and an
#     uncommitted bump stays too;
#   - aWatchChangeShips: a change under wearApp/src, watchcore/ or watchface/src, committed, ships and
#     names the path; so does a line of wearApp/build.gradle.kts that is not a version line, beside
#     the bump; and a watch file removed;
#   - aBuildLineAfterTheBumpShips: the bump committed, then a line of wearApp/build.gradle.kts or
#     watchface/build.gradle.kts that is not a version line, left uncommitted, or staged alone, or
#     staged with the working tree put back (the index alone holds it), ships naming the file; so
#     does a version line with anything else on it, and a version line removed with none added;
#   - anUncommittedWatchChangeShips: a changed file not committed, and a new file not ignored, each
#     ship; a file git ignores (wearApp/build/) does not;
#   - whatNeverShipsStays (the lead's ruling 1): a new file in each test or debug-only source set
#     (wearApp/src/test, testDebug, debug, androidTest, androidTestDebug, testFixtures;
#     watchcore/src/commonTest, androidHostTest, iosTest, jvmTest; watchface/src/debug) or under
#     watchcore/vectors/, committed,
#     stays, saying its release build never carries it; so does an untracked one; while src/main
#     (a package named test in it included), src/release, src/staging, proguard-rules.pro,
#     permissions.txt, the face's pin, and the near names src/debugger, src/testing and
#     watchcore/vectorsX each ship; a test file beside a main change ships naming the main one alone;
#   - renamesInAndOut: a file moved into wearApp/src/main from the phone's tree, committed or staged,
#     ships naming where it went; one moved out to the phone's tree ships naming where it was; one
#     moved from a test set into src/main ships; one moved between two test sets stays;
#   - aDetachedHead: on the bump's commit, detached, it stays; on the tag's own commit it stays; a
#     watch change committed on the detached HEAD ships;
#   - aShallowClone: a depth-1 clone, which holds no tag, ships, saying the clone is shallow; with the
#     tag fetched at depth 1 it compares, and a version-only bump stays;
#   - theCatalogsWatchKeys (the lead's ruling 2): in gradle/libs.versions.toml, a version only the
#     watch's aliases use (wear-compose), a watch alias's line (wear-tiles, and
#     wear-protolayout-expression, which libs.wear.protolayout.expression names, not
#     wear-protolayout), and an uncommitted one (wear-watchfacepush) each ship naming the key and
#     print no note; a key the phone and the watch both build with (kotlinx-coroutines,
#     android-targetSdk, whose note says it is in the Wear app's and the face's manifests,
#     kotlinx-serialization) stays with a note naming it; a key the watch's release does not read
#     (robolectric, its tests' alone; media3 and sqldelight, the phone's) stays with an aside line
#     naming it and no note; a watch key beside a shared one ships naming the first and notes the
#     second; =0 over a watch key stays naming it; and the set is the build files' own: wear-tiles
#     named by shared/build.gradle.kts too stays, and play-services-wearable named by the watch
#     alone ships;
#   - aPhoneTestOfAWatchLibraryShips: a watch library that the phone names only in a test or debug
#     configuration (testImplementation, debugImplementation over three lines) or in a test source
#     set's block (shared's commonTest.dependencies { }, getByName("androidHostTest") { }) ships when
#     its version moves; named in shared's androidMain block, it stays with a note;
#   - theCatalogsForms: wear-tiles moved ships when the watch's use of it is spelt version.ref =
#     'wear-tiles', through a [bundles] entry, by libs.findLibrary("wear-tiles"), as a
#     [libraries.wear-tiles] sub-table (and its module line changed), in dotted keys, under a quoted
#     key, on one line with a test dependency, or after a raw string holding an unclosed
#     testImplementation(; or when the phone names libs.wear.tiles on a block comment's middle line;
#     a test dependency over three lines (robolectric) stays aside; GIT_LITERAL_PATHSPECS,
#     GIT_GLOB_PATHSPECS or GIT_NOGLOB_PATHSPECS in the environment changes nothing;
#   - aWatchBuildFileMissingIsAnError: watchcore/build.gradle.kts gone beside a catalog change exits 2;
#   - theNoteListMembers: settings.gradle.kts, gradle/wrapper/gradle-wrapper.properties,
#     tools/watchface/push-token.sh and tools/watchface/lib-validators.sh, each changed, stay with a
#     note naming it;
#   - aCatalogCommentIsAside: gradle/libs.versions.toml's comment changed stays with an aside and no
#     note; with a watch change beside it, it ships with the aside;
#   - aSharedConfigChangeStaysWithANote: kotlinx-coroutines moved beside the bump stays, its note
#     saying the Wear AAB Play keeps serving was built without it; with a watch change beside it, it
#     ships, its note saying this release's Wear AAB is built with it; the root build.gradle.kts and
#     a new gradle.properties are both named;
#   - theOverrides: TAQWA_RELEASE_WATCH=1 ships a version-only bump and =0 keeps back a watch change,
#     naming it, each saying it was forced; =0 with no tag stays too;
#   - aBadOverrideIsAnError: TAQWA_RELEASE_WATCH=yes, and an unknown argument, exit 2 with no
#     "ships:" or "stays:" line;
#   - theHighestTagWins: wear-build-46 on a commit, a watch change after it, and wear-build-9 on that
#     later commit: wear-build-46 is the tag (numerically highest, though lower by name and on the
#     earlier commit), so it ships; --tag prints it; after a bump to 100, wear-build-100 wins;
#   - aTagAboveThePhoneIsAnError: wear-build-47 on a tree whose androidApp is at 46 exits 2, with and
#     without --tag, naming both; wear-build-46 there is fine;
#   - aWearTagOffItsBuildIsAnError: wear-build-47 on a commit whose wearApp is at 100046, and
#     wear-build-47 on a later commit than build-47 with a watch change between, each exit 2, with
#     and without --tag; moved onto build-47's commit, the change ships; on a later commit whose
#     watch is build-47's (a phone change between), it is fine;
#   - aGitFailureIsAnError: the tag's tree for wearApp/src/main/kotlin missing from the object store,
#     so git cannot compare it, exits 2 instead of deciding; so does the tag's face build file
#     missing beside the bump, which the version-only reading must read;
#   - theTagNeedNotBeAnAncestor: the tag on a commit beside HEAD's history (a release squashed onto
#     main, as wear-build-46 is): its tree is what is compared, so a version-only bump stays;
#   - notAGitCheckoutIsAnError: a copy with no .git, alone and inside another repository, exits 2.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
. scripts/version-files.sh

WORK=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-watch-ships-test.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
T=$WORK/repo
OUT=$WORK/out.txt
ERR=$WORK/err.txt
CATALOG=gradle/libs.versions.toml

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }
indent() { sed 's/^/    /'; }

# gitx <git arguments>: git with an identity of its own and nothing from the user's configuration that
# could sign, hook or prompt; gt, the same in the scratch repository.
gitx() {
  git -c user.name="test-watch-ships" -c user.email="test@example.invalid" -c commit.gpgsign=false \
    -c tag.gpgsign=false -c core.hooksPath=/dev/null -c init.defaultBranch=main -c advice.detachedHead=false "$@"
}
gt() { gitx -C "$T" "$@"; }
commit() { gt add -A && gt commit -q -m "$1"; }

# fresh: a new scratch repository, committed once, untagged.
BUILD_FILES=()
while IFS= read -r f; do BUILD_FILES+=("$f"); done < <(git ls-files '*.gradle.kts')
fresh() {
  rm -rf "$T"
  mkdir -p "$T"
  local f
  for f in scripts/watch-ships.sh scripts/version-files.sh scripts/bump-version.sh scripts/check-versions.sh .gitignore \
    "${BUILD_FILES[@]}" "${IOS_PLISTS[@]}" "${COMPOSE_STRINGS[@]}" "$CATALOG" wearApp/proguard-rules.pro wearApp/permissions.txt; do
    mkdir -p "$T/$(dirname "$f")"
    cp "$f" "$T/$f"
  done
  mkdir -p "$T/wearApp/src/main/kotlin" "$T/watchcore/src/commonMain/kotlin" "$T/watchface/src/main/res/raw" "$T/shared/src"
  echo "class Watch" > "$T/wearApp/src/main/kotlin/Watch.kt"
  echo "class Core" > "$T/watchcore/src/commonMain/kotlin/Core.kt"
  echo "<WatchFace/>" > "$T/watchface/src/main/res/raw/watchface.xml"
  echo "class Phone" > "$T/shared/src/Phone.kt"
  gt init -q
  commit "the scratch tree"
}
tag() { gt tag -a -m "$1" "$1" "${2:-HEAD}"; }
# The phone's version, read from the copy; bump moves it to the next minor name and code 58, bump_to
# to that name and the code given.
NAME=$(sed -n 's/^ *versionName = "\([^"]*\)" *$/\1/p' "$PHONE_GRADLE")
NEXT_NAME=$(awk -F. '{ print $1 "." $2 + 1 ".0" }' <<< "$NAME")
bump_to() { bash "$T/scripts/bump-version.sh" "$NEXT_NAME" "$1" > "$WORK/bump.txt" 2>&1 || { indent < "$WORK/bump.txt"; echo "bump-version.sh failed in the scratch repository" >&2; exit 2; }; }
bump() { bump_to 58; }
# catalog_set <key> <value>: the catalog's [versions] entry for that key, set to that value.
catalog_set() {
  perl -pi -e "s/^\\Q$1\\E = \"[^\"]*\"/$1 = \"$2\"/" "$T/$CATALOG"
  grep -qx "$1 = \"$2\"" "$T/$CATALOG" || { echo "the scratch catalog has no $1 to set" >&2; exit 2; }
}

status=0
AT=""
# ws [VAR=value ...] [-- arguments]: watch-ships.sh in the scratch repository (or in $AT, when set).
ws() {
  local -a vars=() args=()
  while [ "$#" -gt 0 ] && [ "$1" != -- ]; do vars+=("$1"); shift; done
  [ "$#" -eq 0 ] || { shift; args=("$@"); }
  status=0
  (cd "${AT:-$T}" && env -u TAQWA_RELEASE_WATCH ${vars[@]+"${vars[@]}"} bash scripts/watch-ships.sh ${args[@]+"${args[@]}"}) \
    > "$OUT" 2> "$ERR" || status=$?
}
# decided <case> <exit> <ships|stays> <words...>: the last run exited so, its first line begins with
# "ships:" or "stays:" as given, it printed no other such line, and every word is in its output.
decided() {
  local name=$1 want_exit=$2 want=$3 word missing=""
  shift 3
  for word in "$@"; do grep -qF -- "$word" "$OUT" || missing+="'$word' "; done
  if [ "$status" -eq "$want_exit" ] && [ "$(sed -n 1p "$OUT" | cut -d: -f1)" = "$want" ] \
    && [ "$(grep -cE '^(ships|stays):' "$OUT")" -eq 1 ] && [ -z "$missing" ] && [ ! -s "$ERR" ]; then
    pass "$name: exit $status, $(sed -n 1p "$OUT")"
  else
    fail "$name: exit $status (wanted $want_exit, $want:), missing $missing:"
    indent < "$OUT"
    indent < "$ERR"
  fi
}
# refused <case> <words...>: the last run exited 2 with no "ships:" or "stays:" line, every word on stderr.
refused() {
  local name=$1 word missing=""
  shift
  for word in "$@"; do grep -qF -- "$word" "$ERR" || missing+="'$word' "; done
  if [ "$status" -eq 2 ] && ! grep -qE '^(ships|stays):' "$OUT" && [ -s "$ERR" ] && [ -z "$missing" ]; then
    pass "$name: exit 2 ($(head -1 "$ERR"))"
  else
    fail "$name: exit $status, missing $missing:"; indent < "$OUT"; indent < "$ERR"
  fi
}
no_note() { ! grep -q '^note:' "$OUT"; }
# noted <case> <words...>: the last run printed a note holding every word.
noted() {
  local name=$1 word missing=""
  shift
  for word in "$@"; do grep '^note:' "$OUT" | grep -qF -- "$word" || missing+="'$word' "; done
  if grep -q '^note:' "$OUT" && [ -z "$missing" ]; then
    pass "$name: the note names $*"
  else
    fail "$name: no note naming $missing:"; indent < "$OUT"
  fi
}
quiet() { no_note && pass "$1: no note" || { fail "$1 printed a note:"; indent < "$OUT"; }; }
# asided <case> <words...>: the last run printed one aside line holding every word, and no note.
asided() {
  local name=$1 word missing=""
  shift
  for word in "$@"; do grep '^aside:' "$OUT" | grep -qF -- "$word" || missing+="'$word' "; done
  if [ "$(grep -c '^aside:' "$OUT")" -eq 1 ] && [ -z "$missing" ] && no_note; then
    pass "$name: no note, and the aside names $*"
  else
    fail "$name: no aside naming $missing, or a note:"; indent < "$OUT"
  fi
}
no_aside() { ! grep -q '^aside:' "$OUT"; }

# --- noTagShips ---------------------------------------------------------------------------------
fresh
ws
decided "noTagShips" 0 ships "no watch release is tagged"
ws -- --tag
[ "$status" -eq 0 ] && [ ! -s "$OUT" ] && pass "noTagShips: --tag prints nothing" \
  || { fail "noTagShips: --tag exits $status:"; indent < "$OUT"; }

# --- aVersionOnlyBumpStays ----------------------------------------------------------------------
fresh
tag wear-build-46
ws
decided "a tagged tree as it was" 1 stays wear-build-46 100046
bump
ws
decided "aVersionOnlyBumpStays, not committed" 1 stays wear-build-46 100046 "but for its version lines"
if grep -q 'versionCode = 100058' "$T/$WEAR_GRADLE" && grep -q 'versionCode = 58' "$T/$FACE_GRADLE"; then
  commit "the bump"
  ws
  decided "aVersionOnlyBumpStays: wearApp 100058 and the face 58, committed" 1 stays wear-build-46 100046 "but for its version lines"
  quiet "aVersionOnlyBumpStays"
else
  fail "the bump did not write wearApp 100058 and the face 58"
fi

# --- aWatchChangeShips --------------------------------------------------------------------------
for file in wearApp/src/main/kotlin/Watch.kt watchcore/src/commonMain/kotlin/Core.kt watchface/src/main/res/raw/watchface.xml; do
  fresh
  tag wear-build-46
  bump
  echo "// changed" >> "$T/$file"
  commit "a watch change"
  ws
  decided "aWatchChangeShips: $file" 0 ships "$file" wear-build-46 "1 path"
done

fresh
tag wear-build-46
bump
perl -pi -e 's/^(\s*)versionName = (.*)$/$1versionName = $2\n$1multiDexEnabled = true/' "$T/$WEAR_GRADLE"
commit "the bump and a build line"
ws
decided "aWatchChangeShips: a line of wearApp/build.gradle.kts that is not a version line" 0 ships "$WEAR_GRADLE"

fresh
tag wear-build-46
gt rm -q watchcore/src/commonMain/kotlin/Core.kt
commit "a watch file removed"
ws
decided "aWatchChangeShips: a watch file removed" 0 ships watchcore/src/commonMain/kotlin/Core.kt

fresh
tag wear-build-46
for i in 1 2 3 4 5; do echo "class W$i" > "$T/wearApp/src/main/kotlin/W$i.kt"; done
commit "five watch files"
ws
decided "aWatchChangeShips: five paths, three named and a count" 0 ships "5 paths" "and 2 more"

# --- aBuildLineAfterTheBumpShips: the dangerous direction, a build line hiding behind the bump -----
# build_line <file>: a line that is not a version line, after the versionName line.
build_line() { perl -pi -e 's/^(\s*)versionName = (.*)$/$1versionName = $2\n$1resValue("string", "built", "yes")/' "$T/$1"; }
for file in "$WEAR_GRADLE" "$FACE_GRADLE"; do
  fresh
  tag wear-build-46
  bump
  commit "the bump"
  build_line "$file"
  ws
  decided "aBuildLineAfterTheBumpShips: $file, the bump committed and a build line left uncommitted" 0 ships "$file" "1 path"

  gt add "$file"
  ws
  decided "aBuildLineAfterTheBumpShips: $file, that line staged alone" 0 ships "$file" "1 path"

  gt show "HEAD:$file" > "$T/$file"
  if [ "$(gt diff --cached --name-only)" = "$file" ] && cmp -s <(gt show "HEAD:$file") "$T/$file"; then
    ws
    decided "aBuildLineAfterTheBumpShips: $file, staged and the working tree put back, so the index alone holds it" 0 ships "$file"
  else
    fail "aBuildLineAfterTheBumpShips: $file is not staged alone with the working tree as HEAD, so the case tests nothing"
  fi
done

fresh
tag wear-build-46
bump
commit "the bump"
perl -pi -e 's/^(\s*versionCode = \d+)$/$1 + 1/' "$T/$WEAR_GRADLE"
ws
decided "aBuildLineAfterTheBumpShips: a version line with more on it ('versionCode = 100058 + 1')" 0 ships "$WEAR_GRADLE"
gt checkout -q -- "$WEAR_GRADLE"
perl -pi -e 's/^(\s*versionName = "[^"]*")$/$1 \/\/ the bump/' "$T/$FACE_GRADLE"
ws
decided "aBuildLineAfterTheBumpShips: a versionName line with a comment on it" 0 ships "$FACE_GRADLE"
gt checkout -q -- "$FACE_GRADLE"
perl -ni -e 'print unless /^\s*versionName = /' "$T/$WEAR_GRADLE"
ws
decided "aBuildLineAfterTheBumpShips: the versionName line removed, none added" 0 ships "$WEAR_GRADLE"

# --- anUncommittedWatchChangeShips --------------------------------------------------------------
fresh
tag wear-build-46
echo "// not committed" >> "$T/watchcore/src/commonMain/kotlin/Core.kt"
ws
decided "anUncommittedWatchChangeShips: a changed file" 0 ships watchcore/src/commonMain/kotlin/Core.kt

fresh
tag wear-build-46
echo "class New" > "$T/wearApp/src/main/kotlin/New.kt"
ws
decided "anUncommittedWatchChangeShips: a new file git does not ignore" 0 ships wearApp/src/main/kotlin/New.kt

fresh
tag wear-build-46
mkdir -p "$T/wearApp/build/outputs/apk/release" "$T/watchface/build"
echo "an old build" > "$T/wearApp/build/outputs/apk/release/wearApp-release.apk"
echo "an old build" > "$T/watchface/build/face.apk"
ws
decided "anUncommittedWatchChangeShips: files git ignores (build/) do not count" 1 stays wear-build-46

# --- whatNeverShipsStays (ruling 1) -------------------------------------------------------------
# one_new_file <path>: a fresh tree tagged wear-build-46, then that one file added and committed.
one_new_file() {
  fresh
  tag wear-build-46
  mkdir -p "$T/$(dirname "$1")"
  echo "added after the tag" >> "$T/$1"
  commit "one file"
  ws
}
for file in wearApp/src/test/kotlin/WatchTest.kt wearApp/src/testDebug/kotlin/WatchTest.kt \
  wearApp/src/debug/kotlin/SampleFeed.kt wearApp/src/debug/AndroidManifest.xml wearApp/src/androidTest/kotlin/WatchTest.kt \
  wearApp/src/androidTestDebug/kotlin/WatchTest.kt \
  wearApp/src/testFixtures/kotlin/Fixture.kt wearApp/src/test/resources/feed.json \
  watchcore/src/commonTest/kotlin/CoreTest.kt watchcore/src/androidHostTest/kotlin/VectorsTest.kt \
  watchcore/src/iosTest/kotlin/CoreTest.kt watchcore/src/jvmTest/kotlin/CoreTest.kt watchface/src/debug/res/values/x.xml \
  watchcore/vectors/feed-vectors.json watchcore/vectors/phone/phone-capetown-en.json watchcore/vectors/sample-feed-capetown.json; do
  one_new_file "$file"
  decided "whatNeverShipsStays: $file" 1 stays wear-build-46 "but for 1 path its release build never carries"
done

fresh
tag wear-build-46
bump
commit "the bump"
mkdir -p "$T/watchcore/src/commonTest/kotlin"
echo "class Untracked" > "$T/watchcore/src/commonTest/kotlin/Untracked.kt"
ws
decided "whatNeverShipsStays: an untracked test file beside the bump" 1 stays wear-build-46 \
  "but for its version lines and 1 path its release build never carries"

for file in wearApp/src/main/kotlin/test/InMain.kt wearApp/src/release/kotlin/Release.kt wearApp/src/staging/kotlin/Staging.kt \
  wearApp/proguard-rules.pro wearApp/permissions.txt watchface/face-release-cert.sha256 watchface/src/main/res/raw/watchface.xml \
  watchcore/src/commonMain/kotlin/test/InMain.kt watchcore/src/androidMain/kotlin/Android.kt \
  wearApp/src/debugger/kotlin/Near.kt wearApp/src/testing/kotlin/Near.kt watchcore/vectorsX/near.json; do
  one_new_file "$file"
  decided "whatNeverShipsStays: $file still ships" 0 ships "$file" "1 path"
done

fresh
tag wear-build-46
mkdir -p "$T/wearApp/src/test/kotlin"
echo "class WatchTest" > "$T/wearApp/src/test/kotlin/WatchTest.kt"
echo "// changed" >> "$T/wearApp/src/main/kotlin/Watch.kt"
commit "a test beside a main change"
ws
decided "whatNeverShipsStays: a test beside a main change ships, naming the main one alone" 0 ships \
  "1 path: wearApp/src/main/kotlin/Watch.kt"

# --- renamesInAndOut ----------------------------------------------------------------------------
fresh
tag wear-build-46
gt mv shared/src/Phone.kt wearApp/src/main/kotlin/Phone.kt
commit "a file moved into the watch"
ws
decided "renamesInAndOut: moved into wearApp/src/main, committed" 0 ships wearApp/src/main/kotlin/Phone.kt "1 path"

fresh
tag wear-build-46
gt mv shared/src/Phone.kt watchcore/src/commonMain/kotlin/Phone.kt
ws
decided "renamesInAndOut: moved into watchcore/src/commonMain, staged" 0 ships watchcore/src/commonMain/kotlin/Phone.kt

fresh
tag wear-build-46
gt mv wearApp/src/main/kotlin/Watch.kt shared/src/Watch.kt
commit "a file moved out of the watch"
ws
decided "renamesInAndOut: moved out of wearApp/src/main, committed" 0 ships wearApp/src/main/kotlin/Watch.kt "1 path"

fresh
mkdir -p "$T/wearApp/src/test/kotlin"
echo "class Helper" > "$T/wearApp/src/test/kotlin/Helper.kt"
commit "a test helper"
tag wear-build-46
mkdir -p "$T/wearApp/src/testDebug/kotlin"
gt mv wearApp/src/test/kotlin/Helper.kt wearApp/src/testDebug/kotlin/Helper.kt
commit "the helper moved between test sets"
ws
decided "renamesInAndOut: moved between two test sets stays" 1 stays wear-build-46 "but for 2 paths its release build never carries"
gt mv wearApp/src/testDebug/kotlin/Helper.kt wearApp/src/main/kotlin/Helper.kt
commit "the helper moved into src/main"
ws
decided "renamesInAndOut: moved from a test set into src/main ships" 0 ships wearApp/src/main/kotlin/Helper.kt

# --- aDetachedHead ------------------------------------------------------------------------------
fresh
tag wear-build-46
bump
commit "the bump"
gt checkout -q --detach HEAD
if [ -z "$(gt symbolic-ref -q HEAD || true)" ]; then
  ws
  decided "aDetachedHead: on the bump's commit, detached" 1 stays wear-build-46 "but for its version lines"
  gt checkout -q --detach wear-build-46
  ws
  decided "aDetachedHead: on the tag's own commit" 1 stays "the watch has not changed since wear-build-46"
  echo "// changed" >> "$T/wearApp/src/main/kotlin/Watch.kt"
  commit "a watch change on a detached HEAD"
  ws
  decided "aDetachedHead: a watch change committed there ships" 0 ships wearApp/src/main/kotlin/Watch.kt
else
  fail "aDetachedHead: HEAD is still on a branch, so the case tests nothing"
fi

# --- aShallowClone ------------------------------------------------------------------------------
fresh
tag wear-build-46
bump
commit "the bump"
SHALLOW_TREE="$WORK/shallow"
rm -rf "$SHALLOW_TREE"
gitx clone -q --depth 1 "file://$T" "$SHALLOW_TREE" 2> "$WORK/clone.txt" || { indent < "$WORK/clone.txt"; echo "cannot make a shallow clone" >&2; exit 2; }
if [ "$(gitx -C "$SHALLOW_TREE" rev-parse --is-shallow-repository)" = true ] && [ -z "$(gitx -C "$SHALLOW_TREE" tag -l)" ]; then
  AT="$SHALLOW_TREE"
  ws
  decided "aShallowClone: a depth-1 clone holds no tag, so the watch ships, saying the clone is shallow" 0 ships \
    "no watch release is tagged" "shallow"
  gitx -C "$SHALLOW_TREE" fetch -q --depth 1 origin tag wear-build-46 2> "$WORK/fetch.txt" || indent < "$WORK/fetch.txt"
  ws
  decided "aShallowClone: with the tag fetched at depth 1 it compares, and a version-only bump stays" 1 stays wear-build-46 \
    "but for its version lines"
  AT=""
else
  fail "aShallowClone: the clone is not shallow, or holds a tag, so the case tests nothing"
fi

# --- theCatalogsWatchKeys (ruling 2) ------------------------------------------------------------
fresh
tag wear-build-46
catalog_set wear-compose 1.8.0
commit "a newer Compose for Wear OS"
ws
decided "theCatalogsWatchKeys: wear-compose, a version only the watch's aliases use" 0 ships "$CATALOG (wear-compose)" "1 path"
quiet "theCatalogsWatchKeys: wear-compose"

fresh
tag wear-build-46
perl -pi -e 's/"androidx.wear.tiles:tiles"/"androidx.wear.tiles:tiles-renamed"/' "$T/$CATALOG"
commit "the Tile library's line"
ws
decided "theCatalogsWatchKeys: the wear-tiles library line, an alias only wearApp names" 0 ships "$CATALOG (wear-tiles)"
quiet "theCatalogsWatchKeys: wear-tiles"

fresh
tag wear-build-46
bump
commit "the bump"
catalog_set wear-watchfacepush 1.0.1
ws
decided "theCatalogsWatchKeys: wear-watchfacepush, uncommitted after the bump" 0 ships "$CATALOG (wear-watchfacepush)"
ws TAQWA_RELEASE_WATCH=0
decided "theCatalogsWatchKeys: TAQWA_RELEASE_WATCH=0 over it stays, naming it" 1 stays "forced by TAQWA_RELEASE_WATCH=0" \
  "$CATALOG (wear-watchfacepush)"

fresh
tag wear-build-46
catalog_set kotlinx-coroutines 1.10.3
commit "newer coroutines"
ws
decided "theCatalogsWatchKeys: kotlinx-coroutines, which the phone builds with too, stays" 1 stays wear-build-46
noted "theCatalogsWatchKeys: kotlinx-coroutines" "$CATALOG (kotlinx-coroutines)" "TAQWA_RELEASE_WATCH=1"

fresh
tag wear-build-46
catalog_set robolectric 4.18
commit "a newer Robolectric"
ws
decided "theCatalogsWatchKeys: robolectric, which only the watch's tests use (testImplementation), stays" 1 stays wear-build-46
asided "theCatalogsWatchKeys: robolectric, which the watch's release build does not read" "$CATALOG" "(robolectric)"

fresh
tag wear-build-46
catalog_set kotlin 2.4.20
catalog_set wear-compose 1.8.0
commit "Kotlin and Compose for Wear OS"
ws
decided "theCatalogsWatchKeys: wear-compose beside kotlin ships, naming wear-compose" 0 ships "$CATALOG (wear-compose)"
noted "theCatalogsWatchKeys: and its note names kotlin" "$CATALOG (kotlin)"
grep '^ships:' "$OUT" | grep -q 'kotlin)' && fail "theCatalogsWatchKeys: the ships line names kotlin: $(head -1 "$OUT")" \
  || pass "theCatalogsWatchKeys: the ships line does not name kotlin"

fresh
printf 'dependencies {\n    implementation(libs.wear.tiles)\n}\n' >> "$T/shared/build.gradle.kts"
commit "the phone builds with the Tile library too"
tag wear-build-46
catalog_set wear-tiles 1.6.3
commit "a newer Tile library"
ws
decided "theCatalogsWatchKeys: wear-tiles, once shared/build.gradle.kts names it too, stays (the set is the build files')" 1 stays wear-build-46
noted "theCatalogsWatchKeys: wear-tiles named by the phone" "$CATALOG (wear-tiles)"

fresh
perl -ni -e 'print unless /libs\.play\.services\.wearable/' "$T/shared/build.gradle.kts"
commit "the phone no longer builds with the Data Layer"
tag wear-build-46
catalog_set play-services-wearable 20.1.0
commit "a newer Data Layer"
ws
decided "theCatalogsWatchKeys: play-services-wearable, once only wearApp names it, ships" 0 ships "$CATALOG (play-services-wearable)"

fresh
tag wear-build-46
perl -pi -e 's/"androidx.wear.protolayout:protolayout-expression"/"androidx.wear.protolayout:protolayout-expression2"/' "$T/$CATALOG"
commit "the expression library's line"
ws
decided "theCatalogsWatchKeys: the wear-protolayout-expression line, libs.wear.protolayout.expression read as that alias, not wear-protolayout" 0 ships \
  "$CATALOG (wear-protolayout-expression)"

fresh
tag wear-build-46
catalog_set media3 1.11.2
commit "a newer Media3"
ws
decided "theCatalogsWatchKeys: media3, which only the phone builds with, stays" 1 stays wear-build-46
asided "theCatalogsWatchKeys: media3" "$CATALOG" "(media3)"

fresh
tag wear-build-46
catalog_set android-targetSdk 37
commit "a newer target SDK"
ws
decided "theCatalogsWatchKeys: android-targetSdk, which the phone and the watch both build with, stays" 1 stays wear-build-46
noted "theCatalogsWatchKeys: android-targetSdk" "$CATALOG (android-targetSdk)" "target SDK in the Wear app's and the face's manifests"
no_aside && pass "theCatalogsWatchKeys: android-targetSdk: no aside" || { fail "theCatalogsWatchKeys: android-targetSdk printed an aside:"; indent < "$OUT"; }

fresh
tag wear-build-46
catalog_set kotlinx-serialization 1.11.1
catalog_set sqldelight 2.2.2
commit "a newer serialization and SQLDelight"
ws
decided "theCatalogsWatchKeys: kotlinx-serialization beside sqldelight stays" 1 stays wear-build-46
noted "theCatalogsWatchKeys: kotlinx-serialization, the watch's feed parser and the phone's" "$CATALOG (kotlinx-serialization)"
grep '^note:' "$OUT" | grep -q sqldelight && fail "theCatalogsWatchKeys: the note names sqldelight: $(grep '^note:' "$OUT")" \
  || pass "theCatalogsWatchKeys: the note does not name sqldelight"
grep '^aside:' "$OUT" | grep -qF "(sqldelight)" && pass "theCatalogsWatchKeys: the aside names sqldelight alone" \
  || { fail "theCatalogsWatchKeys: no aside naming sqldelight alone:"; indent < "$OUT"; }

# --- aPhoneTestOfAWatchLibraryShips (the reading of "only the watch builds with") ---------------
# A watch library that another module names only in a test or debug configuration, or in a test
# source set's block, is the watch's: the phone's tests and debug build do not build the phone.
# commit_then_tag <message>: the build-file change committed, then wear-build-46 on it.
commit_then_tag() { commit "$1"; tag wear-build-46; }
fresh
printf 'dependencies {\n    testImplementation(libs.wear.compose.foundation)\n}\n' >> "$T/androidApp/build.gradle.kts"
commit_then_tag "the phone's tests name a watch library"
catalog_set wear-compose 1.8.0
ws
decided "aPhoneTestOfAWatchLibraryShips: androidApp's testImplementation" 0 ships "$CATALOG (wear-compose)"

fresh
printf 'dependencies {\n    debugImplementation(\n        libs.wear.compose.foundation\n    )\n}\n' >> "$T/androidApp/build.gradle.kts"
commit_then_tag "the phone's debug build names a watch library, over three lines"
catalog_set wear-compose 1.8.0
ws
decided "aPhoneTestOfAWatchLibraryShips: androidApp's debugImplementation over three lines" 0 ships "$CATALOG (wear-compose)"

fresh
printf 'kotlin {\n    sourceSets {\n        commonTest.dependencies {\n            implementation(libs.wear.compose.foundation)\n        }\n        getByName("androidHostTest") { dependencies { implementation(libs.wear.compose.material3) } }\n    }\n}\n' >> "$T/shared/build.gradle.kts"
commit_then_tag "shared's test source sets name watch libraries"
catalog_set wear-compose 1.8.0
ws
decided "aPhoneTestOfAWatchLibraryShips: shared's commonTest and androidHostTest blocks" 0 ships "$CATALOG (wear-compose)"

fresh
printf 'kotlin {\n    sourceSets {\n        androidMain.dependencies {\n            implementation(libs.wear.compose.foundation)\n        }\n    }\n}\n' >> "$T/shared/build.gradle.kts"
commit_then_tag "shared's app code names a watch library"
catalog_set wear-compose 1.8.0
ws
decided "aPhoneTestOfAWatchLibraryShips: shared's androidMain block, the phone's release, stays" 1 stays wear-build-46
noted "aPhoneTestOfAWatchLibraryShips: shared's androidMain" "$CATALOG (wear-compose)"

# --- theCatalogsForms: each way TOML or a build file can spell a watch entry ---------------------
# set_tiles: the wear-tiles version moved, whatever form its line is in.
set_tiles() { perl -pi -e 's/^(\s*wear-tiles\s*=\s*)(["\x27])1\.6\.2\2/${1}${2}1.6.3${2}/' "$T/$CATALOG"; grep -q '1\.6\.3' "$T/$CATALOG" || { echo "the scratch catalog has no wear-tiles version to move" >&2; exit 2; }; }
forms_case() { # forms_case <what> : the change made, committed and tagged; then wear-tiles moved
  commit_then_tag "$1"
  set_tiles
  ws
  decided "theCatalogsForms: $1" 0 ships "$CATALOG (wear-tiles)"
}
fresh
perl -pi -e "s/version.ref = \"wear-tiles\"/version.ref = 'wear-tiles'/" "$T/$CATALOG"
forms_case "version.ref = 'wear-tiles', single-quoted"

fresh
printf '\n[bundles]\nwear-tile-kit = ["wear-tiles", "wear-protolayout"]\n' >> "$T/$CATALOG"
perl -pi -e 's/implementation\(libs\.wear\.tiles\)/implementation(libs.bundles.wear.tile.kit)/' "$T/$WEAR_GRADLE"
forms_case "the watch naming the Tile library only through a bundle"

fresh
perl -pi -e 's/implementation\(libs\.wear\.tiles\)/implementation(libs.findLibrary("wear-tiles").get())/' "$T/$WEAR_GRADLE"
forms_case "the watch naming the Tile library by libs.findLibrary(\"wear-tiles\")"

fresh
printf '/*\n   The watch builds with libs.wear.tiles,\n   the phone does not.\n*/\n' >> "$T/androidApp/build.gradle.kts"
forms_case "the phone naming libs.wear.tiles on a block comment's middle line"

fresh
perl -0pi -e 's/^wear-tiles = \{ module = "androidx.wear.tiles:tiles", version.ref = "wear-tiles" \}\n//m' "$T/$CATALOG"
printf '\n[libraries.wear-tiles]\nmodule = "androidx.wear.tiles:tiles"\nversion.ref = "wear-tiles"\n' >> "$T/$CATALOG"
forms_case "the Tile library as a [libraries.wear-tiles] sub-table"
perl -pi -e 's/^module = "androidx.wear.tiles:tiles"$/module = "androidx.wear.tiles:tiles2"/' "$T/$CATALOG"
ws
decided "theCatalogsForms: the sub-table's module line" 0 ships "$CATALOG (wear-tiles)"

fresh
perl -0pi -e 's/^wear-tiles = \{ module = "androidx.wear.tiles:tiles", version.ref = "wear-tiles" \}$/wear-tiles.module = "androidx.wear.tiles:tiles"\nwear-tiles.version.ref = "wear-tiles"/m' "$T/$CATALOG"
forms_case "the Tile library in dotted keys (wear-tiles.module, wear-tiles.version.ref)"

fresh
perl -pi -e 's/^wear-tiles = \{/"wear-tiles" = {/' "$T/$CATALOG"
forms_case "the Tile library under a quoted key (\"wear-tiles\" = { ... })"

fresh
perl -pi -e 's/^(\s*)implementation\(libs\.wear\.tiles\)$/$1implementation(libs.wear.tiles); testImplementation(libs.robolectric)/' "$T/$WEAR_GRADLE"
forms_case "the Tile library on one line with a test dependency"

fresh
perl -ni -e 'print unless /^\s*implementation\(libs\.wear\.tiles\)$/' "$T/$WEAR_GRADLE"
printf 'val unclosed = """\n    testImplementation(\n"""\ndependencies {\n    implementation(libs.wear.tiles)\n}\n' >> "$T/$WEAR_GRADLE"
forms_case "the Tile library after a raw string holding an unclosed testImplementation("

fresh
perl -0pi -e 's/testImplementation\(libs\.robolectric\)/testImplementation(\n        libs.robolectric\n    )/' "$T/$WEAR_GRADLE"
commit_then_tag "robolectric over three lines"
catalog_set robolectric 4.18
ws
decided "theCatalogsForms: a test dependency over three lines stays" 1 stays wear-build-46
asided "theCatalogsForms: robolectric over three lines" "(robolectric)"

for env_var in GIT_LITERAL_PATHSPECS=1 GIT_GLOB_PATHSPECS=1 GIT_NOGLOB_PATHSPECS=1; do
  fresh
  tag wear-build-46
  set_tiles
  ws "$env_var"
  decided "theCatalogsForms: $env_var in the environment reads the build files all the same" 0 ships "$CATALOG (wear-tiles)"
done

# --- aWatchBuildFileMissingIsAnError ------------------------------------------------------------
fresh
tag wear-build-46
rm "$T/watchcore/build.gradle.kts"
set_tiles
ws
refused "aWatchBuildFileMissingIsAnError: watchcore/build.gradle.kts gone, beside a catalog change" "watchcore/build.gradle.kts" "cannot be read"

# --- theNoteListMembers -------------------------------------------------------------------------
for file in settings.gradle.kts gradle/wrapper/gradle-wrapper.properties tools/watchface/push-token.sh tools/watchface/lib-validators.sh; do
  fresh
  tag wear-build-46
  mkdir -p "$T/$(dirname "$file")"
  echo "# a change" >> "$T/$file"
  commit "a change to $file"
  ws
  decided "theNoteListMembers: $file stays" 1 stays wear-build-46
  noted "theNoteListMembers: $file" "$file"
done

# --- aCatalogCommentIsAside --------------------------------------------------------------------
fresh
tag wear-build-46
bump
echo "# a newer library" >> "$T/$CATALOG"
commit "the bump and a comment"
ws
decided "aCatalogCommentIsAside: a comment in the catalog stays" 1 stays wear-build-46 100046
asided "aCatalogCommentIsAside" "$CATALOG" "(its comments or spacing)"
echo "class Changed" > "$T/wearApp/src/main/kotlin/Watch.kt"
ws
decided "aCatalogCommentIsAside: with a watch change beside it, it ships" 0 ships wearApp/src/main/kotlin/Watch.kt
asided "aCatalogCommentIsAside: the shipping run keeps the aside" "(its comments or spacing)"

# --- aSharedConfigChangeStaysWithANote ----------------------------------------------------------
fresh
tag wear-build-46
bump
catalog_set kotlinx-coroutines 1.10.3
commit "the bump and newer coroutines"
ws
decided "aSharedConfigChangeStaysWithANote: kotlinx-coroutines" 1 stays wear-build-46 100046
noted "aSharedConfigChangeStaysWithANote: kotlinx-coroutines" "$CATALOG (kotlinx-coroutines)" "the Wear AAB Play keeps serving, 100046, was built without it" "TAQWA_RELEASE_WATCH=1"
no_aside && pass "aSharedConfigChangeStaysWithANote: no aside" || { fail "aSharedConfigChangeStaysWithANote printed an aside:"; indent < "$OUT"; }
echo "class Changed" > "$T/wearApp/src/main/kotlin/Watch.kt"
ws
decided "aSharedConfigChangeStaysWithANote: with a watch change beside it, it ships" 0 ships wearApp/src/main/kotlin/Watch.kt
noted "aSharedConfigChangeStaysWithANote: the shipping run keeps the note" "$CATALOG (kotlinx-coroutines)" "the Wear AAB this release ships is built with it"

fresh
tag wear-build-46
echo "// a change" >> "$T/build.gradle.kts"
echo "org.gradle.caching=true" > "$T/gradle.properties"
ws
decided "aSharedConfigChangeStaysWithANote: the root build.gradle.kts and a new gradle.properties" 1 stays wear-build-46
noted "aSharedConfigChangeStaysWithANote: both" "build.gradle.kts" "gradle.properties"

# --- theOverrides -------------------------------------------------------------------------------
fresh
tag wear-build-46
bump
commit "the bump"
ws TAQWA_RELEASE_WATCH=1
decided "theOverrides: TAQWA_RELEASE_WATCH=1 ships a version-only bump" 0 ships "forced by TAQWA_RELEASE_WATCH=1" wear-build-46
ws TAQWA_RELEASE_WATCH=
decided "theOverrides: TAQWA_RELEASE_WATCH empty is unset" 1 stays wear-build-46
echo "class Changed" > "$T/wearApp/src/main/kotlin/Watch.kt"
commit "a watch change"
ws TAQWA_RELEASE_WATCH=0
decided "theOverrides: TAQWA_RELEASE_WATCH=0 keeps back a watch change, naming it" 1 stays "forced by TAQWA_RELEASE_WATCH=0" \
  wearApp/src/main/kotlin/Watch.kt 100046

fresh
ws TAQWA_RELEASE_WATCH=0
decided "theOverrides: TAQWA_RELEASE_WATCH=0 with no tag stays" 1 stays "forced by TAQWA_RELEASE_WATCH=0" "no watch release is tagged"
ws TAQWA_RELEASE_WATCH=1
decided "theOverrides: TAQWA_RELEASE_WATCH=1 with no tag ships" 0 ships "forced by TAQWA_RELEASE_WATCH=1"

# --- aBadOverrideIsAnError ----------------------------------------------------------------------
for bad in "TAQWA_RELEASE_WATCH=yes" "TAQWA_RELEASE_WATCH=true" "-- --all"; do
  if [ "$bad" = "-- --all" ]; then ws -- --all; else ws "$bad"; fi
  refused "aBadOverrideIsAnError: $bad"
done

# --- theHighestTagWins --------------------------------------------------------------------------
fresh
tag wear-build-46
echo "class Changed" > "$T/wearApp/src/main/kotlin/Watch.kt"
commit "a watch change"
tag wear-build-9
ws
decided "theHighestTagWins: wear-build-46 over wear-build-9 on a later commit" 0 ships wear-build-46 wearApp/src/main/kotlin/Watch.kt
ws -- --tag
[ "$status" -eq 0 ] && [ "$(cat "$OUT")" = wear-build-46 ] && pass "theHighestTagWins: --tag prints wear-build-46" \
  || { fail "theHighestTagWins: --tag exits $status and prints '$(xargs < "$OUT")'"; }
bump_to 100
commit "the bump to 100"
tag wear-build-100
ws
decided "theHighestTagWins: wear-build-100 on the latest commit" 1 stays wear-build-100 100100

# --- aTagAboveThePhoneIsAnError -----------------------------------------------------------------
fresh
tag wear-build-47
ws
refused "aTagAboveThePhoneIsAnError: wear-build-47 over androidApp's 46" wear-build-47 46
ws -- --tag
refused "aTagAboveThePhoneIsAnError: --tag refuses it too" wear-build-47
gt tag -d wear-build-47 > /dev/null
tag wear-build-46
ws
decided "aTagAboveThePhoneIsAnError: wear-build-46 at androidApp's 46 is fine" 1 stays wear-build-46

# --- aWearTagOffItsBuildIsAnError ---------------------------------------------------------------
# wear-build-<N> goes on the commit its Wear AAB was built from: one whose wearApp is not at
# WEAR_OFFSET + N, or whose watch is not build-<N>'s, would hide the watch changes between the two.
fresh
tag wear-build-47
bump_to 47
commit "the bump to 47, after the tag"
ws
refused "aWearTagOffItsBuildIsAnError: wear-build-47 on a commit whose wearApp is at 100046" wear-build-47 "versionCode = 100046, not 100047"
ws -- --tag
refused "aWearTagOffItsBuildIsAnError: --tag refuses it too" wear-build-47 "not 100047"

fresh
bump_to 47
commit "the bump to 47"
tag build-47
echo "// a watch change after the build" >> "$T/wearApp/src/main/kotlin/Watch.kt"
commit "a watch change merged after the release"
tag wear-build-47
ws
refused "aWearTagOffItsBuildIsAnError: wear-build-47 on a later commit than build-47, a watch change between" \
  wear-build-47 build-47 "hold different watches" wearApp/src/main/kotlin/Watch.kt
ws -- --tag
refused "aWearTagOffItsBuildIsAnError: --tag refuses that too" "hold different watches"
gt tag -d wear-build-47 > /dev/null
tag wear-build-47 'build-47^{commit}'
ws
decided "aWearTagOffItsBuildIsAnError: wear-build-47 moved onto build-47's commit, the watch change ships" 0 ships \
  wear-build-47 wearApp/src/main/kotlin/Watch.kt

fresh
bump_to 47
commit "the bump to 47"
tag build-47
echo "class Later" > "$T/shared/src/Later.kt"
commit "a phone change after the release"
tag wear-build-47
ws
decided "aWearTagOffItsBuildIsAnError: wear-build-47 on a later commit whose watch is build-47's is fine" 1 stays wear-build-47 100047

# --- aGitFailureIsAnError -----------------------------------------------------------------------
# The tag's tree for wearApp/src/main/kotlin taken out of the object store: git cannot compare it,
# and the run says so rather than decide on what the failed comparison printed.
fresh
tag wear-build-46
gone=$(gt rev-parse wear-build-46:wearApp/src/main/kotlin)
echo "class Changed" > "$T/wearApp/src/main/kotlin/Watch.kt"
commit "a watch change"
if [ -f "$T/.git/objects/${gone:0:2}/${gone:2}" ]; then
  rm -f "$T/.git/objects/${gone:0:2}/${gone:2}"
  ws
  refused "aGitFailureIsAnError: a tree git cannot read" "git could not compare"
else
  fail "aGitFailureIsAnError: the tree $gone is not a loose object, so the case tests nothing"
fi

# The face's build file at the tag taken out after the bump: its version-only reading needs it, and
# a failed read is an error, never "only its version lines moved".
fresh
tag wear-build-46
gone=$(gt rev-parse "wear-build-46:$FACE_GRADLE")
bump
commit "the bump"
if [ -f "$T/.git/objects/${gone:0:2}/${gone:2}" ] && [ "$(gt rev-parse "HEAD:$FACE_GRADLE")" != "$gone" ]; then
  rm -f "$T/.git/objects/${gone:0:2}/${gone:2}"
  ws
  refused "aGitFailureIsAnError: the tag's face build file git cannot read, beside the bump" "git diff" "$FACE_GRADLE" "failed"
else
  fail "aGitFailureIsAnError: the face build file's blob $gone is not a loose object of the tag alone, so the case tests nothing"
fi

# --- theTagNeedNotBeAnAncestor ------------------------------------------------------------------
fresh
gt checkout -q -b release
echo "release work" > "$T/shared/src/Release.kt"
commit "the release branch"
tag wear-build-46
gt checkout -q main
echo "release work" > "$T/shared/src/Release.kt"
commit "the release, squashed onto main"
bump
commit "the next bump"
if ! gt merge-base --is-ancestor wear-build-46 HEAD; then
  ws
  decided "theTagNeedNotBeAnAncestor: a version-only bump after a squashed release stays" 1 stays wear-build-46 100046
else
  fail "theTagNeedNotBeAnAncestor: the tag is an ancestor of HEAD, so the case tests nothing"
fi

# --- notAGitCheckoutIsAnError -------------------------------------------------------------------
fresh
mkdir -p "$WORK/plain"
cp -R "$T" "$WORK/plain/tree"
rm -rf "$WORK/plain/tree/.git"
AT="$WORK/plain/tree"
ws
AT=""
refused "notAGitCheckoutIsAnError: a copy with no .git" "not a git checkout"
rm -rf "$WORK/plain/tree"
cp -R "$T" "$WORK/inner"
rm -rf "$WORK/inner/.git"
mv "$WORK/inner" "$T/inner"
AT="$T/inner"
ws
AT=""
refused "notAGitCheckoutIsAnError: a copy inside another repository, not reading that one" "not a git checkout's root"

if [ "$FAILS" -gt 0 ]; then
  echo "test-watch-ships: $FAILS failed"
  exit 1
fi
echo "test-watch-ships: PASS"
