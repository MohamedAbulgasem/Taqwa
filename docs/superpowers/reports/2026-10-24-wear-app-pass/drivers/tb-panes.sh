#!/usr/bin/env bash
# With TalkBack on, the pager turned by an injected swipe (which reaches the app, not TalkBack, as a
# sighted wearer's would): what TalkBack says at each change of page.
source "$(dirname "$0")/lib.sh"
F=$1; mkdir -p "$P/logs"; LOG=$P/logs/talkback-$F.txt; TB=$T/talkback.sh
say() { echo "$*" | tee -a "$LOG"; }
spoken() { local pid; pid="$(w shell pidof com.google.android.marvin.talkback | tr -d '\r')"; w logcat -d --pid="$pid" | grep 'Speaking fragment text=' | sed 's/.*Speaking fragment text="\(.*\)", utteranceId.*/\1/' | grep -v '^Double-tap' | awk '!seen[$0]++' | paste -sd '|' -; }
say "== $F the pager, turned with TalkBack on =="
"$TB" off > /dev/null; clock 14:58:20; store "$F" 14:58; con sensor set acceleration 0:0:9.81; field 7
WAIT=3 "$T/app.sh" today
"$TB" on > /dev/null
if w shell dumpsys activity activities | grep topResumedActivity | grep -q PermissionRequestActivity; then w shell input tap 152 373; sleep 1.5; fi
sleep 8
for to in "Tasbeeh:400 227 60 227 300" "Qibla:400 227 60 227 300" "Tasbeeh:60 227 400 227 300" "Today:60 227 400 227 300"; do
    w logcat -c; w shell input swipe ${to#*:}; sleep 6
    say "  swipe to ${to%%:*}: $(spoken)"
done
"$TB" off > /dev/null
con sensor set magnetic-field 0:9.88766:-47.7452; con sensor set acceleration 0:9.77631:0.812349
