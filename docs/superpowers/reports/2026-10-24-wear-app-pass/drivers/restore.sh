#!/usr/bin/env bash
# Puts emulator-5558 back as it was found (found/), then prints its state beside the record and
# compares every file of the app's own data with found/'s, byte for byte: STATE AS FOUND only when
# both agree and no start of the app asked the phone for a feed; else STATE NOT AS FOUND, exit 1.
source "$(dirname "$0")/lib.sh"
F=$P/found
# Nothing is touched unless found/ is what pullfound.sh writes: the record, the app, and each
# folder's list with every file it names (Ruling 68 item 4). An unset or mistyped PASS_WORK gives an
# empty found/, from which the loop below would delete the app's feed.json and its files/.
missing=()
[ -s "$F/state.txt" ] || missing+=("state.txt")
[ -s "$F/app.apk" ] || missing+=("app.apk")
for d in files shared_prefs; do
    if [ ! -f "$F/$d.list" ]; then missing+=("$d.list"); continue; fi
    while IFS= read -r f; do [ -z "$f" ] || [ -f "$F/$d/$f" ] || missing+=("$d/$f"); done < "$F/$d.list"
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "restore.sh: found/ is not complete in $P (no ${missing[*]}): run pullfound.sh with this PASS_WORK first; the watch was not touched" >&2
    exit 1
fi

# How long each start of the app is given (its face's check is bounded at 9 s and the capability's
# at 5; the profile installer writes files/profileInstalled 5 to 6 s after its first frame), how long
# a face the system has not settled is waited for before the app is started again, and how many
# times. test-restore.sh sets RESTORE_FAST=1, and no pause is made.
SETTLE=20 FACE_WAIT=15 RETRY=20 TRIES=8
nap() { [ "${RESTORE_FAST:-}" = 1 ] || sleep "$1"; }
app() { w shell run-as world.taqwa.app "$@"; }
# The app's handshake waits a minute after the last (refresh.asked, RefreshPolicy.GAP_SHORT); a watch
# that holds no delivery (the sample) shakes hands at every start, and a paired phone running Taqwa
# then sends its own feed (Ruling 50's heal). So every start this script makes, the install's own
# (MY_PACKAGE_REPLACED) among them, finds the stamp at now, and the found stamp goes back after.
stamp() { app sh -c "'mkdir -p files && date +%s > files/refresh.asked'" > /dev/null 2>&1 || true; }
# The app's own data as found: in files/ and shared_prefs/ every file pullfound.sh found, byte for
# byte, and no other (the Tile's stamp complications.asked, a held sample, the counts, a delivery's
# leftovers); a folder found empty or absent is removed. With "stamped", refresh.asked is the stamp
# at now in place of the found one, which is never written then, so that no start between the two
# finds an old stamp.
put_files() {
    local d f
    for d in files shared_prefs; do
        for f in $(app ls -A "$d" 2> /dev/null | tr -d '\r'); do
            grep -qxF -- "$f" "$F/$d.list" || [ "${1:-} $d/$f" = "stamped files/refresh.asked" ] || app rm -rf "$d/$f"
        done
        [ -s "$F/$d.list" ] || { app rm -rf "$d"; continue; }
        app mkdir -p "$d"
        for f in $(cat "$F/$d.list"); do
            [ "${1:-} $d/$f" = "stamped files/refresh.asked" ] || app sh -c "'cat > $d/$f'" < "$F/$d/$f"
        done
    done
    [ "${1:-}" != stamped ] || stamp
}
# Each file of the app's own data that is not found's, one line each: a file found/ has not, a
# file it has that is gone, and a file whose sha1 is not its found one (state.sh names the files;
# this reads them).
differs() {
    local d f on want got
    for d in files shared_prefs; do
        on=$(app ls -A "$d" 2> /dev/null | tr -d '\r')
        for f in $on; do grep -qxF -- "$f" "$F/$d.list" || echo "$d/$f: not found's"; done
        for f in $(cat "$F/$d.list"); do
            printf '%s\n' "$on" | grep -qxF -- "$f" || { echo "$d/$f: gone"; continue; }
            want=$(shasum < "$F/$d/$f" | cut -c1-40)
            got=$(w exec-out run-as world.taqwa.app cat "$d/$f" | shasum | cut -c1-40)
            [ "$want" = "$got" ] || echo "$d/$f: ${got:0:8}, found ${want:0:8}"
        done
    done
}
# What the app keeps of its face (FaceMemory's file), on the watch and as found (empty when found/
# has none, and then any face reads as found).
face_memory() { w exec-out run-as world.taqwa.app cat files/face-activation-spent 2> /dev/null | shasum | cut -c1-8; }
found_face=$(shasum < "$F/files/face-activation-spent" 2> /dev/null | cut -c1-8)
[ -f "$F/files/face-activation-spent" ] || found_face=
# The device's clock now, as logcat -T reads a time ('sssss.mmm').
now() { echo "$(w shell date +%s | tr -d '\r').000"; }
# The app started by name (DEBUG_REBIND asks the complications and the Tile to draw again from the
# store; since plan 5 Tasks 4-5 the face is Watch Face Push's, inside the app, and is never installed
# by itself, errata p5), with the found files and the stamp, then given its time: its face's check
# ends in a "TaqwaFace capability" line. The stamp comes first, so that a start the system makes
# after the stop (the face's complications bound again) is stamped too.
start() {
    local at i
    stamp
    w shell am force-stop world.taqwa.app
    put_files stamped
    at=$(now)
    w shell am broadcast --include-stopped-packages -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver -a world.taqwa.app.wear.DEBUG_REBIND | tail -1
    for i in $(seq 1 "$FACE_WAIT"); do
        w logcat -d -T "$at" 2> /dev/null | grep -q "TaqwaFace capability" && break
        nap 1
    done
    nap "$SETTLE"
}

"$T/talkback.sh" off > /dev/null 2>&1 || true
w shell settings put system font_scale 1.0
w shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile \
    --ecn component world.taqwa.app/world.taqwa.app.wear.tile.TodayTileService | tail -1
con sensor set acceleration 0:9.77631:0.812349
con sensor set magnetic-field 0:9.88766:-47.7452
w shell cmd deviceidle unforce > /dev/null 2>&1 || true
"$T/clock.sh" auto > /dev/null; w shell settings put global auto_time_zone 1
w shell wm user-rotation free > /dev/null 2>&1 || true
# Every line of this script's starts is read from here on, once the clock is the network's again: a
# turn's lines stamped on the mock day (clock.sh) are older, and are not this script's.
since=$(now)

# The found app, unless it is the one installed: an install over another build starts the app
# (MY_PACKAGE_REPLACED), stamped first, and for a minute or so after it the system answers that the
# Taqwa face is not active, so that start tells the phone there is a face to add (the capability);
# the starts below are made until the app reads its face as found, which takes it back.
installed=$(w shell sha1sum "$(w shell pm path world.taqwa.app | head -1 | sed 's/package://' | tr -d '\r')" 2> /dev/null | cut -c1-40)
if [ "$installed" = "$(shasum < "$F/app.apk" | cut -c1-40)" ]; then
    echo "the found app is the one installed (${installed:0:8}): not installed again"
else
    stamp
    w install -r -d "$F/app.apk" | tail -1
fi

# The app started with the found files until its face reads as found (FaceMemory's file as found
# after its check), at most TRIES times, RETRY s apart.
tries=0 face_ok=1
while :; do
    start
    tries=$((tries + 1))
    [ -z "$found_face" ] || [ "$(face_memory)" = "$found_face" ] && break
    if [ "$tries" -ge "$TRIES" ]; then
        echo "restore.sh: after $tries starts the app still reads its face as $(app cat files/face-activation-spent 2> /dev/null | xargs), found '$(xargs < "$F/files/face-activation-spent")': the watch's face is not the found one (tools/watchface/activate.sh puts the Taqwa face back)" >&2
        face_ok=0
        break
    fi
    echo "the app read its face as $(app cat files/face-activation-spent 2> /dev/null | xargs), not as found: started again in $RETRY s"
    nap "$RETRY"
done
echo "the app started $tries time(s); its face's last word: $(w logcat -d -T "$since" 2> /dev/null | grep -o 'TaqwaFace capability.*' | tail -1)"

w shell settings get system system_locales | grep -qx null || w shell settings delete system system_locales
face; nap 6; w shell input keyevent KEYCODE_WAKEUP; nap 3
shot "$P/found/left-as-found.png"
# The found files once more, after the starts have written theirs (the face's memory, the profile
# installer's mark, the stamp at now): the app is left running and settled, as the found watch had
# it, since a stopped app's next start would shake hands with the found stamp, older than a minute,
# and the paired phone would send its feed.
put_files

as_found=$face_ok
"$DRIVERS/state.sh" > "$P/found/state-after.txt"
diff <(grep -v '^time:' "$F/state.txt") <(grep -v '^time:' "$P/found/state-after.txt") || as_found=0
bad=$(differs)
[ -z "$bad" ] || { printf 'not as found: %s\n' "$bad"; as_found=0; }
# A handshake that went out, or a feed the phone sent, while this script ran: the phone's answer may
# yet replace the found feed, after the check below.
asked=$(w logcat -d -T "$since" 2> /dev/null | grep -oE 'TaqwaWearFeed (ASK [A-Z_]+ sent|ACCEPTED)' | sort | uniq -c | xargs)
[ -z "$asked" ] || { echo "the phone was asked for a feed or sent one: $asked"; as_found=0; }
w exec-out run-as world.taqwa.app cat files/feed.json | shasum | cut -c1-8
if [ "$as_found" = 1 ]; then echo "STATE AS FOUND"; else echo "STATE NOT AS FOUND"; exit 1; fi
