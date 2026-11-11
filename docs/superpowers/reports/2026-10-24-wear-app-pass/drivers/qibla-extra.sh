#!/usr/bin/env bash
# Ruling 62 item 5's carried minors on emulator-5558 (the English sample):
#   qibla-extra.sh stale            the page left while facing, the heading moved, the page again
#   qibla-extra.sh tilt <label>     tilted, the dial turned through 24 headings (letters vs the notice)
source "$(dirname "$0")/lib.sh"
D=$P/shots/qibla-extra; mkdir -p "$D"
case "$1" in
stale)
    clock 14:58; store en 14:58; con sensor set acceleration 0:0:9.81; field 50   # true 23
    WAIT=2 "$T/app.sh" qibla; sleep 15; shot "$D/stale-0-facing.png"
    w shell input swipe 60 227 400 227 200; sleep 1.5; shot "$D/stale-1-tasbeeh.png"   # to Tasbeeh
    field 7; sleep 12                                                                  # true 340, off the page
    w shell "input swipe 400 227 60 227 200; sleep 0.25; screencap -p /sdcard/a.png; sleep 0.5; screencap -p /sdcard/b.png; sleep 1; screencap -p /sdcard/c.png"
    for x in a b c; do w exec-out cat /sdcard/$x.png > "$D/stale-2-back-$x.png"; w shell rm -f /sdcard/$x.png; done
    sleep 4; shot "$D/stale-3-settled.png"
    ;;
tilt)
    L=$2; clock 14:58; store en 14:58; con sensor set acceleration 0:6.9:6.9; field 0
    WAIT=2 "$T/app.sh" qibla; sleep 10
    for h in $(seq 0 15 345); do
        field "$h"; sleep 5; shot "$D/tilt-$L-m$(printf %03d "$h").png" > /dev/null
    done
    ;;
esac
con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
echo "done: $*"
