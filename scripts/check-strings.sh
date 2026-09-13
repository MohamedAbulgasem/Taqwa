#!/bin/bash
# Every <string name="…"> in values/strings.xml must exist in values-ar/strings.xml and vice
# versa. The Compose resource accessors are generated per key, so a key missing from one locale
# compiles fine and falls back silently at runtime; this is the check the compiler does not do.
#
#   scripts/check-strings.sh
set -e
cd "$(dirname "$0")/.."
en=$(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values/strings.xml | sort)
ar=$(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values-ar/strings.xml | sort)
if diff <(echo "$en") <(echo "$ar"); then
  echo "strings: en and ar carry the same $(echo "$en" | wc -l | tr -d ' ') keys"
else
  echo "strings: the locales differ (< only in en, > only in ar)" >&2
  exit 1
fi
