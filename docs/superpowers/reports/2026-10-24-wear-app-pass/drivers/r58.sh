#!/usr/bin/env bash
# Ruling 58 item 5, the watch's half: the phone's own goldens (its builder, its words) on the three pages.
source "$(dirname "$0")/lib.sh"
V=$REPO/watchcore/vectors/phone
D=$P/shots/r58-phone-words; mkdir -p "$D"
clock 14:58; con sensor set acceleration 0:0:9.81; field 7
for g in "$@"; do
    w shell run-as world.taqwa.app rm -f files/feed.json.delivery
    w shell run-as world.taqwa.app sh -c "'cat > files/feed.json'" < "$V/$g.json"
    w shell am force-stop world.taqwa.app
    for p in today tasbeeh qibla; do
        WAIT=3 "$T/app.sh" "$p" "$D/$g-$p.png" > /dev/null
        if [ "$p" = qibla ]; then sleep 12; shot "$D/$g-$p.png" > /dev/null; fi
        dump "$D/$g-$p.xml"
        python3 -I "$DRIVERS/words.py" "$D/$g-$p.xml" | grep -o "\(text\|content-desc\)='[^']*'" | paste -sd'|' - | sed "s/^/$g $p: /"
    done
    w shell input swipe 227 380 227 80 250; sleep 1.5; shot "$D/$g-tasbeeh-list.png" > /dev/null; dump "$D/$g-tasbeeh-list.xml"
done
con sensor set acceleration 0:9.77631:0.812349; con sensor set magnetic-field 0:9.88766:-47.7452
w shell am broadcast -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver -a world.taqwa.app.wear.DEBUG_FEED | tail -1
