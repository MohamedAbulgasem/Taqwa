#!/usr/bin/env bash
# Sets the app version everywhere it is written down, on both platforms, in one go.
#
#   scripts/bump-version.sh 0.2.0 3
#
# Pre-release policy (Mohamed, 9 September 2026): the version moves only when a release build is
# cut, one step per build however many commits it carries. Minor (0.2.0 -> 0.3.0) if anything since
# the last release was a feature or visible change, patch (0.2.0 -> 0.2.1) if it was all bug fixes;
# a fix + two features + a release is 0.3.0. The version code goes up by one per release build;
# Android refuses to install a lower one over a higher one, and iOS's CFBundleVersion must match
# between the app and the widget extension, which is why both plists are written here.
#
# Until the first production release (Mohamed, 18 September 2026): the name stays 1.0.0 and only
# the code moves — 1.0.0 (28) went to Play's closed track only and iOS has not shipped, so the
# first public version on both stores is still 1.0.0. `build-<code>` tags a tester build and
# `v1.0.0` belongs to the build that reaches production; after that the rule above applies again.
# The app shows the name alone ("1.0.0"): Mohamed tells testers which build they are on himself
# (19 September 2026; a day of showing "1.0.0 (29)" was one day too many).
#
# One version for the phone, the Galaxy watch app, the face and the Apple Watch (the watch release).
# The files are listed in scripts/version-files.sh, and scripts/check-versions.sh, which this
# script runs last (and scripts/test.sh and CI run too), fails when any of them disagrees:
#   - the Galaxy watch app is the phone's code + 100000 under the phone's name, so Play never sees
#     two equal codes on the one listing the phone and the watch share (spec 2.1);
#   - the face is the phone's code and name, so every watch-app update carries a newer face, and
#     Watch Face Push, which only replaces an older face, updates it in its slot (the Lead's call 2
#     in plan 5);
#   - the watchOS app and its widgets carry the iPhone app's name and code, as the iPhone's widget
#     extension does (spec 4.1): every plist in IOS_PLISTS.
set -euo pipefail

NAME=${1:?usage: bump-version.sh <versionName e.g. 0.2.0> <versionCode e.g. 3>}
CODE=${2:?usage: bump-version.sh <versionName e.g. 0.2.0> <versionCode e.g. 3>}
[[ $NAME =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "versionName must look like 0.2.0, got '$NAME'" >&2; exit 1; }
# No leading zero: the shell would read 040 as octal and give the watch the wrong code.
[[ $CODE =~ ^[1-9][0-9]*$ ]] || { echo "versionCode must be a whole number from 1, got '$CODE'" >&2; exit 1; }

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
. scripts/version-files.sh

# set_gradle <file> <versionName> <versionCode>
set_gradle() {
  perl -pi -e "s/^(\s*)versionCode = \d+/\${1}versionCode = $3/; s/^(\s*)versionName = \"[^\"]+\"/\${1}versionName = \"$2\"/" "$1"
}
set_gradle "$PHONE_GRADLE" "$NAME" "$CODE"
set_gradle "$WEAR_GRADLE" "$NAME" "$((WEAR_OFFSET + CODE))"
set_gradle "$FACE_GRADLE" "$NAME" "$CODE"

for plist in "${IOS_PLISTS[@]}"; do
  perl -0pi -e "s|(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+|\${1}$NAME|; s|(<key>CFBundleVersion</key>\s*<string>)[^<]+|\${1}$CODE|" "$plist"
done

# Every language carries the version string (the resource system needs the key everywhere).
for strings in "${COMPOSE_STRINGS[@]}"; do
  perl -pi -e "s|(<string name=\"settings_version_value\">)[^<]+|\${1}$NAME|" "$strings"
done

echo "Version is now $NAME ($CODE):"
grep -Hn "versionCode\|versionName" "$PHONE_GRADLE" "$WEAR_GRADLE" "$FACE_GRADLE"
grep -Hn -A1 "CFBundleShortVersionString\|CFBundleVersion" "${IOS_PLISTS[@]}" | grep string
grep -Hn "settings_version_value" "${COMPOSE_STRINGS[@]}"
bash scripts/check-versions.sh
