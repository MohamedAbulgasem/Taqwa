#!/usr/bin/env bash
# Copies the pass's shots, drawings and words into the report folder (re-runnable).
source "$(dirname "$0")/lib.sh"
set -e
OUT=$DRIVERS/..
mkdir -p "$OUT"
words_of() {  # dir: one words.txt from its dumps, each dump's words in tree order
    local d=$1 f
    : > "$d/words.txt.tmp"
    for f in "$d"/*.xml; do
        [ -e "$f" ] || continue
        { echo "== $(basename "${f%.xml}")"; python3 -I "$DRIVERS/words.py" "$f" | grep -o "\(text\|content-desc\)='[^']*'" || true; } >> "$d/words.txt.tmp"
    done
}
copy_dir() {  # src dst: the PNGs and a words.txt
    local s=$1 d=$2
    mkdir -p "$d"
    cp "$s"/*.png "$d"/ 2>/dev/null || true
    words_of "$s"; mv "$s/words.txt.tmp" "$d/words.txt"
}
mkdir -p "$OUT/ref"; cp "$P"/ref/*.png "$OUT/ref/"
for W in en ar; do for F in en en12 ar-ly12 ar-sa; do
    copy_dir "$P/shots/matrix/watch-$W/$F" "$OUT/watch-$W/$F"
done; done
for W in en ar; do copy_dir "$P/shots/states/watch-$W" "$OUT/states/watch-$W"; done
copy_dir "$P/shots/r58-phone-words" "$OUT/phone-words"
rm -f "$OUT"/phone-words/*-tasbeeh-list.png
[ -d "$P/shots/large" ] && for S in "$P"/shots/large/*/; do for F in "$S"*/; do
    copy_dir "$F" "$OUT/large-text/$(basename "$S")/$(basename "$F")"
done; done
[ -d "$P/shots/r65" ] && { mkdir -p "$OUT/tile-late"; for f in late-160954 late-161000 late-161103 doze-190254 doze-190304 doze-190405 doze-190538 doze-unforced-190544 tb-start; do cp "$P/shots/r65/$f.png" "$OUT/tile-late/"; done; cp "$P/logs/r65.txt" "$OUT/tile-late/r65.txt"; }
[ -d "$P/shots/qibla-extra" ] && { mkdir -p "$OUT/qibla-minors"; cp "$P"/shots/qibla-extra/stale-*.png "$OUT/qibla-minors/"; for f in tilt-10-m000 tilt-10-m180 tilt-13-m000 tilt-13-m180; do cp "$P/shots/qibla-extra/$f.png" "$OUT/qibla-minors/"; done; (cd "$OUT/qibla-minors" && python3 "$DRIVERS/sheet.py" tilt-10-sheet.png 8 0.3 "$P"/shots/qibla-extra/tilt-10-m*.png && python3 "$DRIVERS/sheet.py" tilt-13-sheet.png 8 0.3 "$P"/shots/qibla-extra/tilt-13-m*.png); }
[ -d "$P/shots/talkback" ] && { mkdir -p "$OUT/talkback"; cp "$P"/shots/talkback/*.png "$OUT/talkback/" 2>/dev/null || true; }
cp "$P/logs/compare.tsv" "$OUT/compare.tsv"

mkdir -p "$OUT/face"; cp "$P/shots/face/arch-awake.png" "$OUT/face/arch-en-1458-awake.png"; cp "$P/shots/face/arch-aod.png" "$OUT/face/arch-en-1458-aod.png"
cp "$P/logs/en-vs-ar-watch.txt" "$OUT/en-vs-ar-watch.txt"
mkdir -p "$OUT/settings"; cp "$P/shots/settings/font-size-largest.png" "$OUT/settings/font-size-largest.png"
rm -f "$OUT"/tile-late/tmp.xml

du -sh "$OUT"
for s in 1.0 1.24 1.3; do for f in en en12 ar-sa; do mkdir -p "$OUT/large-text/after-fix/$s/$f"; cp "$P"/shots/fixcheck/$s/$f/*.png "$OUT/large-text/after-fix/$s/$f/"; done; done
