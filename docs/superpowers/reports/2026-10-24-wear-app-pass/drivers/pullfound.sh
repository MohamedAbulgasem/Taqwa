#!/usr/bin/env bash
# The state a turn finds, before it changes anything: the app, the face and the app's own data
# (every file in its files/ and shared_prefs/, by name), and state.sh's record, into
# $PASS_WORK/found for restore.sh.
source "$(dirname "$0")/lib.sh"
"$DRIVERS/state.sh" > "$P/found/state.txt"
w shell run-as world.taqwa.app ls -la /data/data/world.taqwa.app/
w shell dumpsys package com.google.android.marvin.talkback | grep -A1 "READ_PHONE_STATE" | head -4
w pull "$(w shell pm path world.taqwa.app | head -1 | sed 's/package://' | tr -d '\r')" "$P/found/app.apk" > /dev/null
w pull "$(w shell pm path world.taqwa.app.watchfacepush.taqwa | head -1 | sed 's/package://' | tr -d '\r')" "$P/found/face.apk" > /dev/null
# found/<dir>.list names what is there (empty when the folder is not), found/<dir>/ holds each file
for d in files shared_prefs; do
    rm -rf "${P:?}/found/$d"; mkdir -p "$P/found/$d"
    w shell run-as world.taqwa.app ls -A "$d" 2> /dev/null | tr -d '\r' > "$P/found/$d.list"
    for f in $(cat "$P/found/$d.list"); do
        w shell run-as world.taqwa.app test -f "$d/$f" || echo "NOT A FILE, restore.sh cannot put it back: $d/$f" >&2
        w exec-out run-as world.taqwa.app cat "$d/$f" > "$P/found/$d/$f"
    done
done
shasum "$P"/found/*.apk "$P"/found/files/* "$P"/found/shared_prefs/* 2> /dev/null | cut -c1-8,41-
w shell input keyevent KEYCODE_WAKEUP; sleep 1
w exec-out screencap -p > "$P/found/face-found.png"; ls -la "$P/found"
