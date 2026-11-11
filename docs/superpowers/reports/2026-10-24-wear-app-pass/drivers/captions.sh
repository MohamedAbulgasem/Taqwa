#!/usr/bin/env bash
# Ruling 68 item 4 on emulator-5558: the captions over a turning dial in the seven languages, at the
# font scale the watch is at, from langfeeds.py's feeds. For each: tilted ("Hold your wrist level")
# turned 15 degrees at a time and shot at the true headings that put a letter at each end of the
# caption (45, 135, 225, 315: letters at 135 and 225 degrees round the dial) and between (60, 345;
# not 30, within the facing window of the Qibla's 23, where the dial is Aligned and says "Facing
# qibla"); upright at true 340 (the bearing);
# facing the Qibla at true 23 ("Facing qibla"). A dump beside each shot holds its words.
#   captions.sh <label: 1.0|1.3> [form ...]      # forms: en fr tr id ar-sa ur bn (default all)
#   captions.sh tilted <label> [form ...]        # the tilted shots alone
#   captions.sh sweep <label> <feed> <true heading>...   # upright, at each heading (the detail's air, airpx.py)
source "$(dirname "$0")/lib.sh"
if [ "$1" = sweep ]; then SWEEP=1; shift; else SWEEP=; fi
if [ "$1" = tilted ]; then TILTED_ONLY=1; shift; else TILTED_ONLY=; fi
L=$1; shift
FORMS=("$@"); [ ${#FORMS[@]} -eq 0 ] && FORMS=(en fr tr id ar-sa ur bn)
D=$P/shots/captions/$L; mkdir -p "$D" "$P/feeds"
python3 -I "$DRIVERS/langfeeds.py" "$P/feeds" > /dev/null
put() {  # a hand-made feed, byte for byte, then the app afresh; the delivery the watch
         # holds stays, so a paired phone's next send of it is stale (Ruling 50) and the feed stays
    w shell run-as world.taqwa.app sh -c "'cat > files/feed.json'" < "$P/feeds/qibla-$1.json"
    w shell am force-stop world.taqwa.app
}
true_field() { field $(( ($1 + 27) % 360 )); }   # the sample's declination is -27: magnetic = true + 27
# The watch tilted 45 degrees about its x axis (gravity 0:6.9:6.9) facing true heading $1: the field
# lib.sh's field gives a level watch (-22 sin H, 22 cos H, -40), turned into the tilted watch's axes,
# so the tilt-compensated heading is the one asked (a level field read on a tilted watch is not).
tilted_field() {
    local xyz; xyz=$(python3 -I -c "import math,sys; h=math.radians(float(sys.argv[1])+27); t=math.radians(45); n=22*math.cos(h); print('%.4f:%.4f:%.4f' % (-22*math.sin(h), n*math.cos(t)-40*math.sin(t), -n*math.sin(t)-40*math.cos(t)))" "$1")
    con sensor set magnetic-field "$xyz"
}
echo "font_scale=$(w shell settings get system font_scale | tr -d '\r') ($L)" | tee "$D/scale.txt"
clock 14:58
if [ -n "$SWEEP" ]; then
    F=$1; shift; put "$F"
    con sensor set acceleration 0:0:9.81; true_field "$1"
    WAIT=2 "$T/app.sh" qibla; sleep 12
    for h in "$@"; do
        true_field "$h"; sleep 5
        shot "$D/$F-t$(printf %03d "$h").png" > /dev/null
    done
    w logcat -d -s TaqwaWearQibla:* | grep -E "detail '|caption '" | tail -6 > "$D/$F-log.txt" 2>/dev/null || true
    con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
    face; echo "done: sweep $L $F"; exit 0
fi
for f in "${FORMS[@]}"; do
    put "$f"
    con sensor set acceleration 0:6.9:6.9; tilted_field 0
    WAIT=2 "$T/app.sh" qibla; sleep 10
    # turned a step at a time, as the wrist turns (a jump of 90 degrees lags the fused heading)
    for h in $(seq 15 15 345); do
        tilted_field "$h"; sleep 2
        case $h in 45|60|135|225|315|345)
            sleep 3; shot "$D/$f-tilted-t$(printf %03d "$h").png" > /dev/null; dump "$D/$f-tilted-t$(printf %03d "$h").xml" ;;
        esac
    done
    [ -n "$TILTED_ONLY" ] && { echo "captions $L $f: tilted done"; continue; }
    con sensor set acceleration 0:0:9.81; true_field 340; sleep 12
    shot "$D/$f-searching-t340.png" > /dev/null; dump "$D/$f-searching-t340.xml"
    true_field 23; sleep 12
    shot "$D/$f-aligned-t023.png" > /dev/null; dump "$D/$f-aligned-t023.xml"
    echo "captions $L $f: done"
done
# the layout log: each detail's size and air, and each caption's size, box and baselines (the last
# on the half anchor)
w logcat -d -s TaqwaWearQibla:* | grep -E "detail '|caption '" | tail -120 > "$D/qibla-log.txt" 2>/dev/null || true
con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
face
echo "done: captions $L ${FORMS[*]}"
