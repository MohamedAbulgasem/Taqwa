#!/usr/bin/env bash
# Checks that the app's version is the same everywhere it is written down. The files are listed in
# scripts/version-files.sh, and the phone (androidApp/build.gradle.kts) is the reference, N (C):
#   - the Galaxy watch app is N (100000 + C), and its code above the phone's, the face N (C);
#   - every plist in IOS_PLISTS is N (C);
#   - every settings_version_value is N;
#   - no Info.plist under iosApp/ (outside build/, .build/ and DerivedData/) carries a
#     CFBundleVersion without being listed, so a new target cannot ship at another version.
# Exits 0 when everything agrees. Otherwise it prints one line per disagreement, naming the file,
# and exits 1. scripts/bump-version.sh runs it last; scripts/test.sh and CI run it too.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
. scripts/version-files.sh

BAD=0
disagree() { echo "check-versions: $1: $2" >&2; BAD=$((BAD + 1)); }

# Every value the first group of a Perl pattern matches in a file, one a line.
values() { PATTERN=$2 perl -ne 'print "$1\n" if /$ENV{PATTERN}/' "$1"; }
plist_values() { KEY=$2 perl -0ne 'print "$1\n" while m{<key>\Q$ENV{KEY}\E</key>\s*<string>([^<]*)</string>}g' "$1"; }
CODE_RE='^\s*versionCode = (\d+)\s*$'
NAME_RE='^\s*versionName = "([^"]*)"\s*$'
STRING_RE='<string name="settings_version_value">([^<]*)</string>'

# expect <file> <what> <expected> <the values found, one a line>
expect() {
  local file=$1 what=$2 want=$3 got=$4
  case "$got" in
    "") disagree "$file" "has no $what (expected $want)" ;;
    *$'\n'*) disagree "$file" "has more than one $what ($(echo $got)), expected one, $want" ;;
    "$want") ;;
    *) disagree "$file" "$what is $got, expected $want" ;;
  esac
}

if [ ! -f "$PHONE_GRADLE" ]; then
  disagree "$PHONE_GRADLE" "is missing"
  exit 1
fi
N=$(values "$PHONE_GRADLE" "$NAME_RE")
C=$(values "$PHONE_GRADLE" "$CODE_RE")
if ! [[ $N =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && $C =~ ^[1-9][0-9]*$ ]]; then
  disagree "$PHONE_GRADLE" "needs one versionName like 1.2.0 and one versionCode like 38, has '$(echo $N)' and '$(echo $C)'"
  exit 1
fi

for file in "$WEAR_GRADLE" "$FACE_GRADLE"; do
  if [ ! -f "$file" ]; then disagree "$file" "is missing"; continue; fi
  want_code=$C
  [ "$file" = "$WEAR_GRADLE" ] && want_code=$((WEAR_OFFSET + C))
  got_code=$(values "$file" "$CODE_RE")
  expect "$file" versionName "$N" "$(values "$file" "$NAME_RE")"
  expect "$file" versionCode "$want_code" "$got_code"
  # The Wear code can agree with WEAR_OFFSET and still clash with the phone's, when the offset is
  # 0 or less. Play refuses two equal codes on the listing the phone and the watch share.
  if [ "$file" = "$WEAR_GRADLE" ] && [ "$got_code" = "$want_code" ] && [ "$got_code" -le "$C" ]; then
    disagree "$file" "versionCode is $got_code, not above the phone's $C: WEAR_OFFSET in scripts/version-files.sh is $WEAR_OFFSET, and spec 2.1 has the Galaxy watch app at 100000 + C"
  fi
done

for plist in "${IOS_PLISTS[@]}"; do
  if [ ! -f "$plist" ]; then disagree "$plist" "is listed in scripts/version-files.sh but missing"; continue; fi
  expect "$plist" CFBundleShortVersionString "$N" "$(plist_values "$plist" CFBundleShortVersionString)"
  expect "$plist" CFBundleVersion "$C" "$(plist_values "$plist" CFBundleVersion)"
done

for strings in "${COMPOSE_STRINGS[@]}"; do
  if [ ! -f "$strings" ]; then disagree "$strings" "is missing"; continue; fi
  expect "$strings" settings_version_value "$N" "$(values "$strings" "$STRING_RE")"
done

# A plist that carries a version but is not listed would keep whatever version it was made with.
# Build output is not the app's: Gradle's and Xcode's build/, Swift's .build/, DerivedData/.
if [ -d iosApp ]; then
  while IFS= read -r plist; do
    listed=false
    for known in "${IOS_PLISTS[@]}"; do [ "$plist" = "$known" ] && listed=true; done
    if ! $listed && grep -q '<key>CFBundleVersion</key>' "$plist"; then
      disagree "$plist" "carries a CFBundleVersion but is not in IOS_PLISTS (scripts/version-files.sh), so bump-version.sh never writes it"
    fi
  done < <(find iosApp -name Info.plist -not -path '*/build/*' -not -path '*/.build/*' -not -path '*/DerivedData/*' | sort)
fi

[ "$BAD" -eq 0 ] || exit 1
echo "Versions agree: $N ($C) on the phone, the face and iOS; $N ($((WEAR_OFFSET + C))) on the Galaxy watch app."
