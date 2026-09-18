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
# The in-app version therefore shows the code too ("1.0.0 (29)"), or a tester's report could not
# say which build it came from.
set -euo pipefail

NAME=${1:?usage: bump-version.sh <versionName e.g. 0.2.0> <versionCode e.g. 3>}
CODE=${2:?usage: bump-version.sh <versionName e.g. 0.2.0> <versionCode e.g. 3>}
[[ $NAME =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "versionName must look like 0.2.0, got '$NAME'" >&2; exit 1; }
[[ $CODE =~ ^[0-9]+$ ]] || { echo "versionCode must be a whole number, got '$CODE'" >&2; exit 1; }

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

perl -pi -e "s/versionCode = \d+/versionCode = $CODE/; s/versionName = \"[^\"]+\"/versionName = \"$NAME\"/" \
  androidApp/build.gradle.kts

for plist in iosApp/iosApp/Info.plist iosApp/TaqwaWidget/Info.plist; do
  perl -0pi -e "s|(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+|\${1}$NAME|; s|(<key>CFBundleVersion</key>\s*<string>)[^<]+|\${1}$CODE|" "$plist"
done

# Every language carries the version string (the resource system needs the key everywhere).
for strings in shared/src/commonMain/composeResources/values*/strings.xml; do
  # Inside a left-to-right isolate (U+2066 … U+2069, written as bytes): under Arabic and Urdu the
  # brackets are neutral characters and the line read "(29) 1.0.0" without it.
  perl -pi -e "s|(<string name=\"settings_version_value\">)[^<]+|\${1}\xE2\x81\xA6$NAME ($CODE)\xE2\x81\xA9|" "$strings"
done

echo "Version is now $NAME ($CODE):"
grep -Hn "versionCode\|versionName" androidApp/build.gradle.kts
grep -Hn -A1 "CFBundleShortVersionString\|CFBundleVersion" iosApp/iosApp/Info.plist iosApp/TaqwaWidget/Info.plist | grep string
grep -Hn "settings_version_value" shared/src/commonMain/composeResources/values*/strings.xml
