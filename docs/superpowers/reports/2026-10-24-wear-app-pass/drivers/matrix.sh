#!/usr/bin/env bash
# Step 1's matrix for one feed on emulator-5558: Today at 14:58, 22:30 and 04:10 with its list,
# Tasbeeh at 0, 27, 33 and 100 and its list, Qibla Searching, Aligned, Low and BestEffort.
#   matrix.sh <watch: en|ar> <feed: en|en12|ar-ly12|ar-sa> [today|tasbeeh|qibla ...]
source "$(dirname "$0")/lib.sh"
WATCH=$1; F=$2; shift 2
PARTS=("$@"); [ ${#PARTS[@]} -eq 0 ] && PARTS=(today tasbeeh qibla)
D=$P/shots/matrix/watch-$WATCH/$F
mkdir -p "$D"

today() {
    for t in 14:58 22:30 04:10; do
        tt=${t/:/}
        clock "$t:20"; store "$F" "$t"
        WAIT=3 "$T/app.sh" today "$D/today-$tt.png"
        dump "$D/today-$tt.xml"
        for _ in 1 2 3 4; do rotary -4; sleep 0.4; done
        sleep 1
        shot "$D/today-$tt-list.png"; dump "$D/today-$tt-list.xml"
    done
}

tasbeeh() {
    reset_counts
    clock 14:58; store "$F" 14:58
    WAIT=3 "$T/app.sh" tasbeeh "$D/tasbeeh-0.png"; dump "$D/tasbeeh-0.xml"
    rotary -27; sleep 1.2; shot "$D/tasbeeh-27.png"; dump "$D/tasbeeh-27.xml"
    # 33: the part's flash in the same shell as the click (240 ms), then plain
    w shell "input rotaryencoder scroll --axis SCROLL,-6; sleep 0.1; screencap -p /sdcard/f.png"
    w exec-out cat /sdcard/f.png > "$D/tasbeeh-33-flash.png"; w shell rm -f /sdcard/f.png
    sleep 0.8; shot "$D/tasbeeh-33.png"; dump "$D/tasbeeh-33.xml"
    # 100: the set's flash inside the one-second hold, then the next round
    w shell "input rotaryencoder scroll --axis SCROLL,-67; sleep 0.15; screencap -p /sdcard/f.png"
    w exec-out cat /sdcard/f.png > "$D/tasbeeh-100-flash.png"; w shell rm -f /sdcard/f.png
    sleep 1.5; shot "$D/tasbeeh-0-round2.png"
    w shell run-as world.taqwa.app cat shared_prefs/tasbeeh.xml > "$D/tasbeeh-prefs.xml"
    # the list: a swipe up on the counter
    w shell input swipe 227 380 227 80 250; sleep 1.5
    shot "$D/tasbeeh-list.png"; dump "$D/tasbeeh-list.xml"
    for _ in 1 2 3 4 5 6; do rotary -6; sleep 0.4; done; sleep 1
    shot "$D/tasbeeh-list-end.png"; dump "$D/tasbeeh-list-end.xml"
}

qibla() {
    clock 14:58; store "$F" 14:58
    con sensor set acceleration 0:0:9.81
    field 7                      # magnetic 7 = true 340 with the sample's declination of -27
    WAIT=2 "$T/app.sh" qibla; sleep 15
    shot "$D/qibla-searching.png"; dump "$D/qibla-searching.xml"
    field 50; sleep 15           # true 23: facing
    shot "$D/qibla-aligned.png"; dump "$D/qibla-aligned.xml"
    con sensor set magnetic-field 600:0:0; sleep 4
    shot "$D/qibla-low.png"; dump "$D/qibla-low.xml"
    sleep 22
    shot "$D/qibla-besteffort.png"; dump "$D/qibla-besteffort.xml"
    con sensor set magnetic-field 0:9.88766:-47.7452
    con sensor set acceleration 0:9.77631:0.812349
}

for part in "${PARTS[@]}"; do "$part"; done
w logcat -d -s TaqwaWearQibla:* | tail -5 > "$D/qibla-log-tail.txt" 2>/dev/null || true
echo "done: watch-$WATCH $F ${PARTS[*]}"
