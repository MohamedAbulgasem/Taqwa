#!/bin/bash
# Frames every captured language for both stores into docs/store/screenshots, which is checked
# in: Play at 1080x1920 (8 per language), App Store at 1320x2868 (7 per language, no Qibla: the
# simulator has no compass). Captions come from docs/store/listing/<lang>.md.
#
# The sets live in the repo so that a feature which changes a screen changes its store shot in
# the same commit: re-shoot that screen (ONLY="2-notifications" tools/store/capture-android.sh …,
# and capture-ios.sh), run this, commit, and upload the changed files to each store by hand.
# `git status docs/store/screenshots` is the list of what to upload.
#
#   tools/store/assemble.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd); IN=$ROOT/build/store-shots; OUT=$ROOT/docs/store/screenshots
rm -rf "$OUT/play" "$OUT/appstore"; mkdir -p "$OUT"
for L in en ar fr tr id ur bn; do
  [ -d "$IN/android/$L" ] && python3 "$ROOT/tools/store/frame.py" $L "$IN/android/$L" "$OUT/play/$L" 1080x1920
  if [ -d "$IN/ios/$L" ]; then
    T=$(mktemp -d); for f in "$IN/ios/$L"/*.png; do case $(basename "$f") in 6-qibla.png) ;; *) cp "$f" "$T/";; esac; done
    python3 "$ROOT/tools/store/frame.py" $L "$T" "$OUT/appstore/$L" 1320x2868; rm -rf "$T"
  fi
done
echo "play: $(find "$OUT/play" -name '*.png' 2>/dev/null | wc -l | tr -d ' ') files, appstore: $(find "$OUT/appstore" -name '*.png' 2>/dev/null | wc -l | tr -d ' ') files, $(du -sh "$OUT" | cut -f1)"
