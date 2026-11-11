#!/usr/bin/env bash
# Subsets the face's three Noto Sans Arabic weights (subset-noto.py says to what and why), with
# fontTools at a pinned version in a virtual environment of its own in ~/.cache/taqwa-watchface-tools,
# made the first time (never in the repository):
#
#   tools/watchface/get-noto.sh /tmp/noto       # the fonts as Google publishes them, into an empty directory
#   tools/watchface/subset-noto.sh /tmp/noto    # writes watchface/src/main/res/font/noto_sans_arabic_subset_v2_*.ttf
set -euo pipefail
FONTTOOLS=4.55.0
VENV="$HOME/.cache/taqwa-watchface-tools/fonttools-$FONTTOOLS"
if [ ! -x "$VENV/bin/python" ]; then
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install -q "fonttools==$FONTTOOLS"
fi
"$VENV/bin/python" -I "$(cd "$(dirname "$0")" && pwd)/subset-noto.py" "$@"
