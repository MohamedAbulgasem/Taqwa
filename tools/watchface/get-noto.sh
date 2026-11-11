#!/usr/bin/env bash
# Downloads the three Noto Sans Arabic weights the face bundles (Light, SemiBold, Bold) and the
# font's licence from the font's own upstream, into a directory of their own, and prints their
# SHA-256: the notofonts project's Google Fonts build (static instances of the family google/fonts
# publishes as a variable font) and google/fonts' OFL.txt. Nothing is run or installed; the files
# in watchface/src/main/res/font are these, subset by subset-noto.sh (noto_sans_arabic_subset_v2_light.ttf and so on), and
# the licence is docs/licences/OFL-1.1-NotoSansArabic.txt.
#
#   tools/watchface/get-noto.sh <empty directory outside the repository>
set -euo pipefail
DEST="${1:?usage: $0 <empty directory outside the repository>}"
mkdir -p "$DEST"
if [ -n "$(ls -A "$DEST")" ]; then
    echo "$DEST is not empty" >&2
    exit 1
fi
BASE=https://raw.githubusercontent.com/notofonts/notofonts.github.io/main/fonts/NotoSansArabic/googlefonts/ttf
for weight in Light SemiBold Bold; do
    curl -s -f -o "$DEST/NotoSansArabic-$weight.ttf" "$BASE/NotoSansArabic-$weight.ttf"
done
curl -s -f -o "$DEST/OFL.txt" "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansarabic/OFL.txt"
shasum -a 256 "$DEST"/*
