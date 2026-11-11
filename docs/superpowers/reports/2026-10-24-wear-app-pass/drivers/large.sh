#!/usr/bin/env bash
# Step 4 at the font scale the watch is at: every screen with the longest city, for one feed; with
# "qibla", the Qibla page's four states alone (Ruling 68 item 4's re-shoot).
#   large.sh <label: 1.0|largest|1.3> <feed: en|en12|ar-sa> [qibla]
source "$(dirname "$0")/lib.sh"
S=$1; F=$2; ONLY=${3:-}
D=$P/shots/large/$S/$F; mkdir -p "$D"
CITY='সান ফার্নান্দো ডে মোন্তে ক্রিস্টি'
PHRASE='سبحان الله وبحمده، سبحان الله العظيم'
R=world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver
put() {  # a hand-made feed (feeds/), byte for byte, then the app afresh; the delivery the watch
         # holds stays, so a paired phone's next send of it is stale (Ruling 50) and the feed stays
    w shell run-as world.taqwa.app sh -c "'cat > files/feed.json'" < "$DRIVERS/feeds/$1"
    w shell am force-stop world.taqwa.app
}
tap_text() {  # tap the node whose text is $2 in dump $1
    local b; b=$(python3 -I - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter():
    if n.attrib.get("text") == sys.argv[2] or n.attrib.get("content-desc", "").startswith(sys.argv[2]):
        x0, y0, x1, y1 = map(int, re.findall(r"\d+", n.attrib["bounds"]))
        print((x0 + x1) // 2, (y0 + y1) // 2); break
PY
)
    [ -n "$b" ] && w shell input tap $b && echo "tapped '$2' at $b" || echo "no node '$2'"
}
echo "font_scale=$(w shell settings get system font_scale | tr -d '\r') ($S, $F)" | tee "$D/scale.txt"

qibla() {
    clock 14:58; put "large-$F.json"; con sensor set acceleration 0:0:9.81; field 7
    WAIT=2 "$T/app.sh" qibla; sleep 15; shot "$D/qibla-searching.png" > /dev/null; dump "$D/qibla-searching.xml"
    field 50; sleep 15; shot "$D/qibla-aligned.png" > /dev/null; dump "$D/qibla-aligned.xml"
    put "large-$F-calibration.json"; con sensor set magnetic-field 600:0:0
    WAIT=2 "$T/app.sh" qibla; sleep 4; shot "$D/qibla-low-calibration.png" > /dev/null; dump "$D/qibla-low-calibration.xml"
    sleep 20; shot "$D/qibla-besteffort.png" > /dev/null; dump "$D/qibla-besteffort.xml"
    con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
}
if [ "$ONLY" = qibla ]; then qibla; face; echo "done: large $S $F qibla"; exit 0; fi

clock 14:58:20; put "large-$F.json"
WAIT=3 "$T/app.sh" today "$D/today-1458.png"; dump "$D/today-1458.xml"
rotary -4; sleep 0.8; shot "$D/today-list-1.png" > /dev/null
for _ in 1 2 3 4 5 6; do rotary -4; sleep 0.4; done; sleep 1
shot "$D/today-list-end.png" > /dev/null; dump "$D/today-list-end.xml"

reset_counts; clock 14:58; put "large-$F.json"
WAIT=3 "$T/app.sh" tasbeeh "$D/tasbeeh-0.png"; dump "$D/tasbeeh-0.xml"
rotary -27; sleep 1.2; shot "$D/tasbeeh-27.png" > /dev/null; dump "$D/tasbeeh-27.xml"
w shell input swipe 227 380 227 80 250; sleep 1.5
shot "$D/tasbeeh-list.png" > /dev/null; dump "$D/tasbeeh-list.xml"
for _ in 1 2 3 4 5 6 7 8; do rotary -6; sleep 0.4; done; sleep 1
shot "$D/tasbeeh-list-end.png" > /dev/null; dump "$D/tasbeeh-list-end.xml"
tap_text "$D/tasbeeh-list-end.xml" "$PHRASE"; sleep 1.5
shot "$D/tasbeeh-phrase.png" > /dev/null; dump "$D/tasbeeh-phrase.xml"

qibla

# the Tile with the longest city, through the debug receiver (which has the Tile asked again)
face; clock 14:58:00
w shell am broadcast -n "$R" -a world.taqwa.app.wear.DEBUG_FEED --el epoch 1792328280 \
    $( [ "$F" = en ] || echo "--es sample $F" ) --es city "'$CITY'" | tail -1
sleep 21; tile_shot "$D/tile-1458.png" "$CITY" en

# the no-feed line, then the sample again
w shell am broadcast -n "$R" -a world.taqwa.app.wear.DEBUG_FEED --ez clear true | tail -1
WAIT=3 "$T/app.sh" today "$D/nofeed-today.png"; dump "$D/nofeed-today.xml"
face; sleep 20; tile_shot "$D/nofeed-tile.png" "Open Taqwa on your phone" en
w shell am broadcast -n "$R" -a world.taqwa.app.wear.DEBUG_FEED | tail -1
face
echo "done: large $S $F"
