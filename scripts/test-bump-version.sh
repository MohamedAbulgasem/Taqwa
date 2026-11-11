#!/usr/bin/env bash
# Tests scripts/bump-version.sh and scripts/check-versions.sh on a scratch copy of the files they
# read and write (every file scripts/version-files.sh names, and the scripts), never on the
# repository itself:
#   - a bump writes one version everywhere: the phone (N, C), the Galaxy watch app
#     (N, 100000 + C), the face (N, C), every listed iOS plist (N, C) and every Compose
#     settings_version_value (N), and check-versions.sh agrees. The 100000 is spec 2.1's, written
#     here and not read from version-files.sh's WEAR_OFFSET, so a changed offset fails the test;
#   - check-versions.sh names the file of each disagreement, one line each: a hand-edited Wear
#     code, a Wear code not above the phone's, a stale face, a plist with a stale CFBundleVersion
#     or CFBundleShortVersionString, a stale version string, and an iOS plist that carries a
#     version without being listed (one under a build directory does not count);
#   - a malformed name or code is refused and changes nothing;
#   - bump-version.sh ends by running check-versions.sh, so a bump that leaves a disagreement (an
#     unlisted plist, which it never writes) exits non-zero and names that file.
# The version it bumps to is read from the files, not written here: the next minor name after the
# phone's, two codes on (1.3.0 (40) from 1.2.0 (38)), so the test holds whatever version the branch
# is at. It passes whether IOS_PLISTS lists the two iPhone plists or plan 4's four.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

if [ ! -f scripts/version-files.sh ]; then
  echo "FAIL: scripts/version-files.sh is missing (the list bump-version.sh and check-versions.sh read)"
  exit 1
fi
. scripts/version-files.sh

WORK=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-bump-test.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
PRISTINE=$WORK/pristine
T=$WORK/tree

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

# The scratch tree: the version's files and the three scripts, at their own paths.
for f in "$PHONE_GRADLE" "$WEAR_GRADLE" "$FACE_GRADLE" "${IOS_PLISTS[@]}" "${COMPOSE_STRINGS[@]}" \
  scripts/version-files.sh scripts/bump-version.sh scripts/check-versions.sh; do
  if [ -f "$f" ]; then
    mkdir -p "$PRISTINE/$(dirname "$f")"
    cp "$f" "$PRISTINE/$f"
  else
    fail "$f is missing from the repository"
  fi
done

fresh() { rm -rf "$T"; cp -R "$PRISTINE" "$T"; }
bump() { bash "$T/scripts/bump-version.sh" "$@"; }
check() { bash "$T/scripts/check-versions.sh"; }

# The values a file holds, one a line: a Gradle file's versionCode or versionName, a plist key's
# string, a strings file's settings_version_value.
gradle_code() { perl -ne 'print "$1\n" if /^\s*versionCode = (\d+)\s*$/' "$T/$1"; }
gradle_name() { perl -ne 'print "$1\n" if /^\s*versionName = "([^"]*)"\s*$/' "$T/$1"; }
plist_value() { KEY=$2 perl -0ne 'print "$1\n" while m{<key>\Q$ENV{KEY}\E</key>\s*<string>([^<]*)</string>}g' "$T/$1"; }
strings_value() { perl -ne 'print "$1\n" if m{<string name="settings_version_value">([^<]*)</string>}' "$T/$1"; }

CUR_NAME=$(perl -ne 'print $1 if /^\s*versionName = "([^"]+)"\s*$/' "$PHONE_GRADLE")
CUR_CODE=$(perl -ne 'print $1 if /^\s*versionCode = (\d+)\s*$/' "$PHONE_GRADLE")
[[ $CUR_NAME =~ ^([0-9]+)\.([0-9]+)\.[0-9]+$ ]] || { echo "FAIL: $PHONE_GRADLE has no versionName like 1.2.0"; exit 1; }
NAME="${BASH_REMATCH[1]}.$((BASH_REMATCH[2] + 1)).0"
[[ $CUR_CODE =~ ^[0-9]+$ ]] || { echo "FAIL: $PHONE_GRADLE has no versionCode"; exit 1; }
CODE=$((CUR_CODE + 2))
# Spec 2.1's scheme, which plan 5's Tasks 4, 5 and 7 rely on: the Galaxy watch app is the phone's
# code + 100000 (1.3.0 (100040) for 1.3.0 (40)). Written here, not taken from WEAR_OFFSET.
WEAR_CODE=$((100000 + CODE))
echo "# bumping a scratch copy from $CUR_NAME ($CUR_CODE) to $NAME ($CODE); IOS_PLISTS lists ${#IOS_PLISTS[@]}"

# 0. version-files.sh keeps spec 2.1's offset.
[ "$WEAR_OFFSET" = 100000 ] \
  && pass "scripts/version-files.sh sets WEAR_OFFSET=100000 (spec 2.1)" \
  || fail "scripts/version-files.sh sets WEAR_OFFSET=$WEAR_OFFSET, not spec 2.1's 100000"

# 1. A bump writes the one version everywhere.
fresh
if out=$(bump "$NAME" "$CODE" 2>&1); then
  pass "bump-version.sh $NAME $CODE exits 0"
else
  fail "bump-version.sh $NAME $CODE exits non-zero:"; echo "$out" | sed 's/^/    /'
fi
[ "$(gradle_code "$PHONE_GRADLE")" = "$CODE" ] && [ "$(gradle_name "$PHONE_GRADLE")" = "$NAME" ] \
  && pass "the phone reads $NAME ($CODE)" \
  || fail "the phone reads $(gradle_name "$PHONE_GRADLE") ($(gradle_code "$PHONE_GRADLE")), not $NAME ($CODE)"
grep -qE "^[[:space:]]*versionCode = $WEAR_CODE$" "$T/$WEAR_GRADLE" && [ "$(gradle_name "$WEAR_GRADLE")" = "$NAME" ] \
  && pass "the Galaxy watch app reads $NAME ($WEAR_CODE)" \
  || fail "the Wear code is not 100000 + $CODE: $WEAR_GRADLE reads $(gradle_name "$WEAR_GRADLE") ($(gradle_code "$WEAR_GRADLE")), not $NAME ($WEAR_CODE)"
grep -qE "^[[:space:]]*versionCode = $CODE$" "$T/$FACE_GRADLE" && [ "$(gradle_name "$FACE_GRADLE")" = "$NAME" ] \
  && pass "the face reads $NAME ($CODE)" \
  || fail "the face's code is not written: $FACE_GRADLE reads $(gradle_name "$FACE_GRADLE") ($(gradle_code "$FACE_GRADLE")), not $NAME ($CODE)"
for plist in "${IOS_PLISTS[@]}"; do
  [ "$(plist_value "$plist" CFBundleShortVersionString)" = "$NAME" ] && [ "$(plist_value "$plist" CFBundleVersion)" = "$CODE" ] \
    && pass "$plist reads $NAME ($CODE)" \
    || fail "$plist reads $(plist_value "$plist" CFBundleShortVersionString) ($(plist_value "$plist" CFBundleVersion)), not $NAME ($CODE)"
done
stale=0
for strings in "${COMPOSE_STRINGS[@]}"; do
  [ "$(strings_value "$strings")" = "$NAME" ] || { fail "$strings's settings_version_value reads $(strings_value "$strings"), not $NAME"; stale=1; }
done
[ "$stale" = 0 ] && pass "all ${#COMPOSE_STRINGS[@]} settings_version_value strings read $NAME"
if [ ! -f "$T/scripts/check-versions.sh" ]; then
  fail "scripts/check-versions.sh is missing"
elif out=$(check 2>&1); then
  pass "check-versions.sh agrees after the bump"
else
  fail "check-versions.sh disagrees after the bump:"; echo "$out" | sed 's/^/    /'
fi
BUMPED=$WORK/bumped
rm -rf "$BUMPED"; cp -R "$T" "$BUMPED"
from_bumped() { rm -rf "$T"; cp -R "$BUMPED" "$T"; }

# 2. Each kind of disagreement fails the check, naming its file, one line a disagreement.
# expect_refused <what> <the files it must name, one line each, space-separated>
expect_refused() {
  local what=$1 files=$2 out status=0 file missing=""
  out=$(check 2>&1) || status=$?
  for file in $files; do
    printf '%s\n' "$out" | grep -qF "check-versions: $file: " || missing="$missing $file"
  done
  # shellcheck disable=SC2086 # counting the words on purpose
  set -- $files
  if [ "$status" != 1 ]; then
    fail "$what: check-versions.sh exits $status, not 1"
  elif [ -n "$missing" ]; then
    fail "$what: check-versions.sh does not name$missing:"; echo "$out" | sed 's/^/    /'
  elif [ "$(printf '%s\n' "$out" | grep -c .)" != "$#" ]; then
    fail "$what: check-versions.sh prints $(printf '%s\n' "$out" | grep -c .) lines, not $#:"; echo "$out" | sed 's/^/    /'
  else
    pass "$what: check-versions.sh exits 1 and names $files"
  fi
}
if [ -f "$T/scripts/check-versions.sh" ]; then
  from_bumped
  perl -pi -e "s/^(\s*)versionCode = \d+/\${1}versionCode = $((WEAR_CODE - 1))/" "$T/$WEAR_GRADLE"
  expect_refused "a hand-edited Wear code ($((WEAR_CODE - 1)))" "$WEAR_GRADLE"

  # An offset of 0 gives the Galaxy watch app the phone's own code, which Play refuses on the one
  # listing they share. The bump writes it and agrees with itself; check-versions.sh refuses it.
  fresh
  perl -pi -e 's/^WEAR_OFFSET=.*/WEAR_OFFSET=0/' "$T/scripts/version-files.sh"
  bump "$NAME" "$CODE" > /dev/null 2>&1 || true
  expect_refused "a WEAR_OFFSET of 0, the Galaxy watch app at the phone's code ($CODE)" "$WEAR_GRADLE"

  from_bumped
  perl -pi -e "s/^(\s*)versionCode = \d+/\${1}versionCode = $CUR_CODE/" "$T/$FACE_GRADLE"
  expect_refused "a stale face code ($CUR_CODE)" "$FACE_GRADLE"

  from_bumped
  perl -pi -e "s/^(\s*)versionCode = \d+/\${1}versionCode = $((WEAR_CODE - 1))/" "$T/$WEAR_GRADLE"
  perl -pi -e "s/^(\s*)versionName = \"[^\"]+\"/\${1}versionName = \"$CUR_NAME\"/" "$T/$FACE_GRADLE"
  expect_refused "two disagreements, the Wear code and the face's name" "$WEAR_GRADLE $FACE_GRADLE"

  from_bumped
  last_plist=${IOS_PLISTS[${#IOS_PLISTS[@]} - 1]}
  perl -0pi -e "s|(<key>CFBundleVersion</key>\s*<string>)[^<]+|\${1}$CUR_CODE|" "$T/$last_plist"
  expect_refused "a stale CFBundleVersion" "$last_plist"

  # Apple needs the watch app's and the extensions' short version to equal the iPhone app's.
  from_bumped
  perl -0pi -e "s|(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+|\${1}$CUR_NAME|" "$T/$last_plist"
  expect_refused "a stale CFBundleShortVersionString ($CUR_NAME)" "$last_plist"

  from_bumped
  last_strings=${COMPOSE_STRINGS[${#COMPOSE_STRINGS[@]} - 1]}
  perl -pi -e "s|(<string name=\"settings_version_value\">)[^<]+|\${1}$CUR_NAME|" "$T/$last_strings"
  expect_refused "a stale settings_version_value" "$last_strings"

  from_bumped
  mkdir -p "$T/iosApp/Extra"
  cp "$T/${IOS_PLISTS[0]}" "$T/iosApp/Extra/Info.plist"
  expect_refused "an unlisted iosApp/Extra/Info.plist with a CFBundleVersion" iosApp/Extra/Info.plist

  from_bumped
  for dir in iosApp/build/Extra iosApp/WatchCore/.build/debug/Tests.xctest iosApp/DerivedData/Extra; do
    mkdir -p "$T/$dir"
    perl -0pe "s|(<key>CFBundleVersion</key>\s*<string>)[^<]+|\${1}1|" "$T/${IOS_PLISTS[0]}" > "$T/$dir/Info.plist"
  done
  if out=$(check 2>&1); then
    pass "plists under build/, .build/ and DerivedData/ are not the app's"
  else
    fail "check-versions.sh counts a plist under build/, .build/ or DerivedData/:"; echo "$out" | sed 's/^/    /'
  fi
fi

# 3. A malformed name or code is refused, and nothing changes.
for args in "${NAME%.*} $CODE" "$NAME forty" "$NAME 0$CODE" "$NAME"; do
  fresh
  status=0
  # shellcheck disable=SC2086 # the arguments are split on purpose
  bump $args > "$WORK/out" 2>&1 || status=$?
  if [ "$status" = 0 ]; then
    fail "bump-version.sh $args exits 0"
  elif ! diff -r "$PRISTINE" "$T" > "$WORK/diff"; then
    fail "bump-version.sh $args exits $status but changes files:"; sed 's/^/    /' "$WORK/diff" | head -20
  else
    pass "bump-version.sh $args exits $status and changes nothing"
  fi
done

# 4. bump-version.sh ends by running check-versions.sh: a disagreement the bump does not write
# away fails it. An unlisted plist with a CFBundleVersion is one, since the bump never writes it.
# Everything else is written, so it is the one disagreement named.
fresh
mkdir -p "$T/iosApp/Extra"
cp "$T/${IOS_PLISTS[0]}" "$T/iosApp/Extra/Info.plist"
status=0
out=$(bump "$NAME" "$CODE" 2>&1) || status=$?
named=$(printf '%s\n' "$out" | grep -c '^check-versions: ' || true)
if [ "$status" = 0 ]; then
  fail "bump-version.sh $NAME $CODE exits 0 with an unlisted iosApp/Extra/Info.plist:"; echo "$out" | sed 's/^/    /'
elif ! printf '%s\n' "$out" | grep -qF "check-versions: iosApp/Extra/Info.plist: "; then
  fail "bump-version.sh $NAME $CODE exits $status but does not name iosApp/Extra/Info.plist:"; echo "$out" | sed 's/^/    /'
elif [ "$named" != 1 ]; then
  fail "bump-version.sh $NAME $CODE names $named disagreements, not the unlisted plist alone:"; echo "$out" | sed 's/^/    /'
else
  pass "bump-version.sh $NAME $CODE with an unlisted iosApp/Extra/Info.plist exits $status and names it"
fi

if [ "$FAILS" -gt 0 ]; then
  echo "test-bump-version.sh: $FAILS failed"
  exit 1
fi
echo "test-bump-version.sh: PASS"
