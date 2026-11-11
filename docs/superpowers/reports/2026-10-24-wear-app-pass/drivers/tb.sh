#!/usr/bin/env bash
# Step 3: what TalkBack says on each page and the Tile, for one feed, on emulator-5558.
#   tb.sh <feed: en|en12|ar-sa> [today|tasbeeh|qibla|tile ...]
# Nothing here dumps the tree while TalkBack is on (it pauses TalkBack and brings back its
# phone-access prompt). Every line goes to logs/talkback-<feed>.txt as well.
source "$(dirname "$0")/lib.sh"
F=$1; shift
PARTS=("$@"); [ ${#PARTS[@]} -eq 0 ] && PARTS=(today tasbeeh qibla tile)
TB="$T/talkback.sh"
mkdir -p "$P/logs"; LOG=$P/logs/talkback-$F.txt
say() { echo "$*" | tee -a "$LOG"; }
spoken() {
    local pid; pid="$(w shell pidof com.google.android.marvin.talkback | tr -d '\r')"
    w logcat -d --pid="$pid" | grep 'Speaking fragment text=' \
        | sed 's/.*Speaking fragment text="\(.*\)", utteranceId.*/\1/' | grep -v '^Double-tap' | awk '!seen[$0]++' | paste -sd '|' -
}
prompt_off() {  # cancel TalkBack's phone-access prompt if it is up (never granted)
    if w shell dumpsys activity activities | grep topResumedActivity | grep -q PermissionRequestActivity; then
        w shell input tap 152 373; sleep 1.5; say "  (phone-access prompt cancelled)"
    fi
}
open_with_tb() {  # page: open the app on a page with TalkBack on, and say what it says on opening
    w logcat -c
    w shell am start -W --activity-clear-task -n world.taqwa.app/world.taqwa.app.wear.app.WearMainActivity \
        --es world.taqwa.app.wear.PAGE "$1" > /dev/null
    sleep 5; prompt_off
    say "  opening $1: $(spoken)"
}

today() {
    say "== $F Today (14:58) =="
    "$TB" off > /dev/null; clock 14:58:20; store "$F" 14:58
    "$TB" on | sed 's/^/  /' | tee -a "$LOG"; prompt_off
    open_with_tb TODAY
    sleep 4; w logcat -c; sleep 10
    say "  idle 10 s on Today, mid-minute (from $(w shell date +%H:%M:%S | tr -d '\r')): said [$(spoken)]"
    while [ "$(w shell date +%S | tr -d '\r')" -lt 54 ]; do sleep 1; done
    w logcat -c; sleep 10
    say "  10 s across the minute (to $(w shell date +%H:%M:%S | tr -d '\r')): said [$(spoken)]"
    say "  the walk from the ring, one swipe right at a time:"
    "$TB" walk 14 | sed 's/^/    /' | tee -a "$LOG"
    prompt_off
}

tasbeeh() {
    say "== $F Tasbeeh =="
    "$TB" off > /dev/null; reset_counts; clock 14:58; store "$F" 14:58
    WAIT=2 "$T/app.sh" tasbeeh; rotary -27; sleep 1.5
    "$TB" on | sed 's/^/  /' | tee -a "$LOG"; prompt_off
    open_with_tb TASBEEH
    say "  walk to the counter:"; "$TB" walk 2 | sed 's/^/    /' | tee -a "$LOG"
    say "  $("$TB" tap2 227 227)"; prompt_off
    say "  stored after tap2: $(w shell run-as world.taqwa.app cat shared_prefs/tasbeeh.xml | grep -o 'count[^<]*<[^<]*' | head -2 | paste -sd' ' -)"
    local V=$REPO/watchcore/vectors
    local sample=$V/sample-feed-capetown.json; [ "$F" = en ] || sample=$V/sample-feed-capetown-$F.json
    UNDO=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["ui"]["screens"]["undoAction"])' "$sample")
    menu() {  # the TalkBack menu with its Actions, then the actions themselves: up to three tries
        local out k
        for k in 1 2 3; do
            out=$("$TB" actions); sleep 1.5
            if grep -q "Actions" <<< "$out"; then
                say "  $out"
                out=$("$TB" tap2 227 227); sleep 1.5; say "  tap2 on Actions: $out"
                grep -q "$UNDO" <<< "$out" && return 0
            fi
            say "  (menu try $k: $out; BACK)"; w shell input keyevent KEYCODE_BACK; sleep 1.5
        done
        return 1
    }
    menu && { say "  tap2 (Take one away): $("$TB" tap2 227 227)"; prompt_off; }
    sleep 1
    say "  stored after: $(w shell run-as world.taqwa.app cat shared_prefs/tasbeeh.xml | grep -o 'count[^<]*<[^<]*' | head -2 | paste -sd' ' -)"
    if menu; then
        say "  walk 1 to the next action:"; "$TB" walk 1 | sed 's/^/    /' | tee -a "$LOG"
        say "  tap2 (Choose a dhikr): $("$TB" tap2 227 227)"; prompt_off
    fi
    say "  the list, walked:"; "$TB" walk 13 | sed 's/^/    /' | tee -a "$LOG"
    w shell input keyevent KEYCODE_BACK; sleep 1.5
    say "  from the counter, the walk on:"
    open_with_tb TASBEEH
    "$TB" walk 8 | sed 's/^/    /' | tee -a "$LOG"
    prompt_off
}

qibla() {
    say "== $F Qibla =="
    "$TB" off > /dev/null; clock 14:58; store "$F" 14:58
    con sensor set acceleration 0:0:9.81; field 7
    "$TB" on | sed 's/^/  /' | tee -a "$LOG"; prompt_off
    open_with_tb QIBLA
    sleep 10; w logcat -c
    say "  walk at true 340:"; "$TB" walk 4 | sed 's/^/    /' | tee -a "$LOG"
    w logcat -c; field 50; sleep 18
    say "  turned to true 23, untouched: $(spoken)"
    w logcat -c; field 7; sleep 18
    say "  turned back to true 340, untouched: $(spoken)"
    say "  $("$TB" idle 8) (steady heading)"
    con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
}

tile() {
    say "== $F Tile (14:58) =="
    "$TB" off > /dev/null; face; sleep 1; clock 14:58:00; store "$F" 14:58; sleep 22
    city="Cape Town"; case "$F" in ar-*) city="كيب تاون" ;; esac
    tile_shot "$P/shots/talkback/tile-$F.png" "$city" en | tee -a "$LOG"
    "$TB" on | sed 's/^/  /' | tee -a "$LOG"; prompt_off
    say "  the walk on the Tile:"; "$TB" walk 10 | sed 's/^/    /' | tee -a "$LOG"
    prompt_off
}

for part in "${PARTS[@]}"; do "$part"; done
"$TB" off > /dev/null
face
