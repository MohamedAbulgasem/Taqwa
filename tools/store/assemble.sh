#!/bin/bash
# Frames every captured language for both stores into ~/Downloads/Taqwa-store-screenshots:
# Play at 1080x1920 (8 per language), App Store at 1320x2868 (7 per language, no Qibla: the
# simulator has no compass). Captions come from docs/store/listing/<lang>.md.
#
#   tools/store/assemble.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd); IN=$ROOT/build/store-shots; OUT=$HOME/Downloads/Taqwa-store-screenshots
rm -rf "$OUT"; mkdir -p "$OUT"
for L in en ar fr tr id ur bn; do
  [ -d "$IN/android/$L" ] && python3 "$ROOT/tools/store/frame.py" $L "$IN/android/$L" "$OUT/play/$L" 1080x1920
  if [ -d "$IN/ios/$L" ]; then
    T=$(mktemp -d); for f in "$IN/ios/$L"/*.png; do case $(basename "$f") in 6-qibla.png) ;; *) cp "$f" "$T/";; esac; done
    python3 "$ROOT/tools/store/frame.py" $L "$T" "$OUT/appstore/$L" 1320x2868; rm -rf "$T"
  fi
done
echo "play: $(find "$OUT/play" -name '*.png' 2>/dev/null | wc -l | tr -d ' ') files, appstore: $(find "$OUT/appstore" -name '*.png' 2>/dev/null | wc -l | tr -d ' ') files"
