#!/usr/bin/env bash
# Today and its list's end after the cap fix, at three font scales, three feeds (hand-made, longest city).
source "$(dirname "$0")/lib.sh"
D=$P/shots/fixcheck
for s in 1.0 1.24 1.3; do
    w shell settings put system font_scale $s; sleep 2
    for F in en en12 ar-sa; do
        mkdir -p "$D/$s/$F"
        clock 14:58:20
        w shell run-as world.taqwa.app sh -c "'cat > files/feed.json'" < "$DRIVERS/feeds/large-$F.json"
        w shell am force-stop world.taqwa.app
        WAIT=3 "$T/app.sh" today "$D/$s/$F/today-1458.png" > /dev/null
        for _ in 1 2 3 4 5 6 7; do rotary -4; sleep 0.4; done; sleep 1
        shot "$D/$s/$F/today-list-end.png" > /dev/null
    done
done
w shell settings put system font_scale 1.0
echo done
