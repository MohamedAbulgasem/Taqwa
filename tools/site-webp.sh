#!/bin/bash
# Converts every PNG screenshot in site/assets/img to WebP and removes the PNG.
#
#   tools/site-webp.sh
#
# The website serves WebP only: the same screenshot is five to ten times smaller than its PNG,
# which is most of the page weight on a phone. Quality 85 keeps the Mushaf's harakat and the
# small print sharp. Drop a new PNG into site/assets/img, run this, and reference the .webp from
# the page; `python3 site/build.py --check` fails on any reference to a file that is not there.
#
# Needs cwebp (brew install webp).
set -euo pipefail
cd "$(dirname "$0")/.."
command -v cwebp > /dev/null || { echo "cwebp not found: brew install webp" >&2; exit 1; }
shopt -s nullglob
converted=0
for png in site/assets/img/*.png; do
  webp="${png%.png}.webp"
  cwebp -quiet -q 85 -m 6 -metadata none "$png" -o "$webp"
  printf '%8d -> %7d  %s\n' "$(stat -f %z "$png")" "$(stat -f %z "$webp")" "$(basename "$webp")"
  rm "$png"
  converted=$((converted + 1))
done
echo "converted $converted screenshot(s)"
