#!/usr/bin/env bash
# Checks a minified build of the Wear app, as it was built (plan 5 Task 3):
#
#   tools/wear-release-check.sh <variant>      # release or staging, after
#                                              # ./gradlew :wearApp:assemble<Variant> -Ptaqwa.unsignedRelease=true
#
#   - the variant's R8 mapping.txt exists, so the build was minified (and a crash on it can be read);
#   - no debug code or test data is in the APK: its dex holds no DEBUG_FEED, SampleFeed or
#     sample-feed (src/debug's seams and sample feeds), and no file of the APK is a sample feed or a
#     test vector (Ruling 55 item 6);
#   - the APK's manifest, the merged one, asks for exactly the permissions wearApp/permissions.txt
#     names, no more and no fewer;
#   - the APK is not debuggable, and holds Watch Face Push's wear-sdk as not required (plan 5 Task 5);
#   - a dex missing or unreadable fails the check rather than pass its search;
#   - it prints the APK's size.
#
# It builds nothing and changes nothing. It exits 0 when every check passes, 1 when any fails (each
# failure on its own line, every check still run), and 2 on a wrong argument.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

if [ "$#" -ne 1 ] || ! [[ "$1" =~ ^[a-z][A-Za-z]*$ ]]; then
  echo "usage: $0 <variant>   (release or staging)" >&2
  exit 2
fi
VARIANT=$1
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2> /dev/null)}"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2> /dev/null | sort -V | tail -1)"
PERMISSIONS=wearApp/permissions.txt
MAPPING=wearApp/build/outputs/mapping/$VARIANT/mapping.txt
APK_DIR=wearApp/build/outputs/apk/$VARIANT

FAILS=0
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

[ -n "$AAPT2" ] || { echo "no aapt2 under $SDK/build-tools" >&2; exit 2; }

APKS=()
if [ -d "$APK_DIR" ]; then
  while IFS= read -r apk; do APKS+=("$apk"); done < <(find "$APK_DIR" -maxdepth 1 -name '*.apk' | sort)
fi
if [ "${#APKS[@]}" -ne 1 ]; then
  fail "expected one APK in $APK_DIR, found ${#APKS[@]}: build it with ./gradlew :wearApp:assemble$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1} first"
  echo "wear-release-check $VARIANT: $FAILS failed"
  exit 1
fi
APK=${APKS[0]}
echo "APK: $APK"

# 1. Minified: R8 wrote its mapping beside the build.
if [ -s "$MAPPING" ]; then
  echo "ok - R8's mapping: $MAPPING"
else
  fail "no R8 mapping at $MAPPING: the $VARIANT build is not minified"
fi

# 2. No debug code, sample feed or test vector. The dex's strings are plain bytes for these names.
#    A dex that is missing or cannot be read is a failure, never a clean result.
DEX_FILE=$(mktemp)
trap 'rm -f "$DEX_FILE"' EXIT
# (The listing is read whole first: grep -q leaving a pipe early would fail it under pipefail.)
LISTING=$(unzip -Z1 "$APK" 2> /dev/null || true)
if ! grep -qE '^classes[0-9]*\.dex$' <<< "$LISTING"; then
  fail "the APK holds no classes.dex"
elif ! unzip -p "$APK" 'classes*.dex' > "$DEX_FILE" 2> /dev/null || [ ! -s "$DEX_FILE" ]; then
  fail "the APK's dex cannot be read"
else
  DEX_HITS=$(LC_ALL=C grep -a -o -e DEBUG_FEED -e SampleFeed -e sample-feed "$DEX_FILE" | sort | uniq -c | xargs || true)
  if [ -z "$DEX_HITS" ]; then
    echo "ok - the dex holds no DEBUG_FEED, SampleFeed or sample-feed"
  else
    fail "the dex holds debug code: $DEX_HITS"
  fi
fi
DATA_FILES=$(unzip -Z1 "$APK" | grep -E 'sample-feed|feed-vectors|watch-rules-vectors|vectors/' || true)
if [ -z "$DATA_FILES" ]; then
  echo "ok - no sample feed or test vector among the APK's files"
else
  fail "the APK carries test data: $(xargs <<< "$DATA_FILES")"
fi

# 3. The merged manifest's permissions are exactly the list's.
WANTED=$(sed -e 's/#.*//' "$PERMISSIONS" | awk 'NF { print $1 }' | sort -u)
ASKED=$("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission[-a-z0-9]*: name='\([^']*\)'.*/\1/p" | sort -u)
if [ "$ASKED" = "$WANTED" ]; then
  echo "ok - the manifest asks for exactly $PERMISSIONS: $(xargs <<< "$ASKED")"
else
  fail "the manifest's permissions differ from $PERMISSIONS (< asked only, > listed only):"
  diff <(echo "$ASKED") <(echo "$WANTED") | grep '^[<>]' | sed 's/^/    /' || true
fi

# 4. A store build is not debuggable, and needs no system library a watch before Wear OS 6 lacks:
#    Watch Face Push's wear-sdk is not required (erratum p5), so Wear OS 3 to 5 watches install it.
BADGING=$("$AAPT2" dump badging "$APK" 2> /dev/null || true)
if [ -z "$BADGING" ]; then
  fail "aapt2 cannot read the APK's manifest"
else
  if grep -q '^application-debuggable' <<< "$BADGING"; then
    fail "the APK is debuggable"
  else
    echo "ok - the APK is not debuggable"
  fi
  if grep -q "^uses-library-not-required:'wear-sdk'" <<< "$BADGING" && ! grep -q "^uses-library:'wear-sdk'" <<< "$BADGING"; then
    echo "ok - wear-sdk is not required, so watches before Wear OS 6 install the app"
  else
    fail "the merged manifest does not hold wear-sdk as not required (uses-library android:required=\"false\")"
  fi
fi

# 5. The size.
BYTES=$(wc -c < "$APK" | tr -d ' ')
echo "size: $BYTES bytes ($(awk -v b="$BYTES" 'BEGIN { printf "%.1f", b / 1048576 }') MB)"

if [ "$FAILS" -eq 0 ]; then
  echo "wear-release-check $VARIANT: PASS"
else
  echo "wear-release-check $VARIANT: $FAILS failed"
  exit 1
fi
