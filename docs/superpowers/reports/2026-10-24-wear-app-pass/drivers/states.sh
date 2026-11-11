#!/usr/bin/env bash
# Step 2's states on emulator-5558 (no paired phone running), every page and the Tile:
#   states.sh <watch: en|ar>
source "$(dirname "$0")/lib.sh"
WATCH=$1
D=$P/shots/states/watch-$WATCH; mkdir -p "$D"
OPEN="Open Taqwa on your phone"; UPDATE="Update Taqwa on your watch"
[ "$WATCH" = ar ] && OPEN="افتح تقوى على هاتفك" && UPDATE="حدّث تقوى على ساعتك"
R=world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver
debug() { w shell am broadcast -n "$R" -a world.taqwa.app.wear.DEBUG_FEED "$@" | tail -1 | tee -a "$D/broadcasts.txt"; }
pages() {  # <name> <the Tile's word>: the three pages and the Tile
    for page in today tasbeeh qibla; do
        WAIT=3 "$T/app.sh" "$page" "$D/$1-$page.png" > /dev/null; dump "$D/$1-$page.xml"
    done
    face; sleep 20   # the system holds a Tile request for about 20 s after the last
    tile_shot "$D/$1-tile.png" "$2" "$WATCH"
    face
    for page in today tasbeeh qibla tile; do
        python3 -I "$DRIVERS/words.py" "$D/$1-$page.xml" | grep -o "\(text\|content-desc\)='[^']*'" | head -3 | paste -sd'|' - | sed "s/^/$1 $page: /"
    done
}
con sensor set acceleration 0:0:9.81
clock 14:58
debug --ez clear true
pages noFeed "$OPEN"
debug --ei version 2
pages noFeedNewer "$UPDATE"
w shell am force-stop world.taqwa.app
pages noFeedNewerAfterForceStop "$UPDATE"
debug --el epoch 1792328280
clock 1795784280   # 40 days after 14:58 on the mock day: the feed is spent
pages spent "Open Taqwa on your phone"
clock 14:58
debug --el epoch 1792328280
debug --ei version 2
pages newerWithFeed "Update Taqwa on your watch"
debug
con sensor set acceleration 0:9.77631:0.812349
echo "done: states watch-$WATCH"
