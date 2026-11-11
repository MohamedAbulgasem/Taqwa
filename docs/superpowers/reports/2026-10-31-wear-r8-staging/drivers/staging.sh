#!/usr/bin/env bash
# Plan 5 Task 3 Step 4: the staging build (R8, debug key) on the paired emulators, one step a call:
#
#   staging.sh found      the watch's state, APKs and files (the P3.9 drivers' pullfound.sh), the phone's app
#   staging.sh install    the staging APK over the debug one (same key), checked by its sha1
#   staging.sh feed       plan 2's debug phone app installed on the phone (found without Taqwa), London
#                         placed by its harness, which builds and sends the phone's real feed; both logs
#                         read for ACCEPTED and its ACK
#   staging.sh phoneback  the phone's Taqwa uninstalled again, as found
#   staging.sh face <epoch>   the face, awake, at that minute (KEEP_FEED: the feed the phone sent)
#   staging.sh slots      the wearer's slots given Taqwa's two pickable sources through the system's chooser
#   staging.sh noslots    the wearer's slots emptied again
#   staging.sh tile       the Tile added and shown, found by the city it must draw
#   staging.sh pages      Today, Tasbeeh and Qibla opened afresh, shot and their words dumped
#   staging.sh cold       a cold start under screenrecord (3 s), pulled for frames.swift
#   staging.sh debug      this tree's debug APK over staging, then the found debug APK (a downgrade with
#                         -d is refused over the non-debuggable staging), its data kept
#   staging.sh restore    the P3.9 drivers' restore.sh: the watch put back as found
#
# Work files go to PASS_WORK (the P3.9 drivers' variable). It drives emulator-5558, and 5562 only
# through Taqwa's debug harness, under the wear-5558 and phone-5562 locks the caller holds.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../../.." && pwd)"
DRIVERS="$REPO/docs/superpowers/reports/2026-10-24-wear-app-pass/drivers"
source "$DRIVERS/lib.sh"
ph() { "$ADB" -s emulator-5562 "$@"; }
harness() { ph shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver -a world.taqwa.app.debug.WATCH --es cmd "$@" | tr -d '\r'; }
STAGING="$REPO/wearApp/build/outputs/apk/staging/wearApp-staging.apk"
installed_sha() { w shell sha1sum "$(w shell pm path world.taqwa.app | head -1 | sed 's/package://' | tr -d '\r')" | cut -c1-8; }

case "${1:-}" in
found)
    "$DRIVERS/pullfound.sh"
    echo "phone app sha1: $(ph shell sha1sum "$(ph shell pm path world.taqwa.app | head -1 | sed 's/package://' | tr -d '\r')" | cut -c1-8)"
    harness nodes | tail -1 ;;
install)
    echo "staging sha1: $(shasum "$STAGING" | cut -c1-8)"
    w install -r "$STAGING" | tail -1
    echo "installed sha1: $(installed_sha)"
    w shell dumpsys package world.taqwa.app | grep -m1 -o 'flags=\[[^]]*\]' ;;
feed)
    w logcat -c
    ph install -r "$REPO/androidApp/build/outputs/apk/debug/androidApp-debug.apk" | tail -1
    ph logcat -c
    harness place --es place 51.5074,-0.1278,Europe/London,London,2643743 | tail -1
    sleep 15
    w logcat -d -s TaqwaWearFeed System.out | grep -v '^-' > "$P/feed-watch.txt"; ph logcat -d -s TaqwaWatch System.out TaqwaWatchHarness | grep -v '^-' > "$P/feed-phone.txt"
    cat "$P/feed-watch.txt" "$P/feed-phone.txt"
    w logcat -d -b crash > "$P/crash-after-feed.txt"; echo "crash lines: $(grep -c . "$P/crash-after-feed.txt")" ;;
face)
    face; sleep 2
    KEEP_FEED=1 "$T/shoot.sh" "$P/shots" "face-${3:-staging}" "$2" awake ;;
slots)
    "$T/choose.sh" left next right taqwa
    face; sleep 4; w shell input keyevent KEYCODE_WAKEUP; sleep 2; shot "$P/shots/slots-staging.png" ;;
noslots)
    "$T/choose.sh" left empty right empty
    face; sleep 2 ;;
tile)
    w shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile \
        --ecn component world.taqwa.app/world.taqwa.app.wear.tile.TodayTileService | tail -1
    sleep 3
    tile_shot "$P/shots/tile-staging.png" "${2:-London}"
    words "$P/shots/tile-staging.xml" | head -12
    face ;;
pages)
    for page in today tasbeeh qibla; do
        "$T/app.sh" "$page" "$P/shots/app-$page-staging.png" > /dev/null
        dump "$P/shots/app-$page-staging.xml"
        echo "== $page: $(words "$P/shots/app-$page-staging.xml" | head -8 | xargs)"
    done
    w logcat -d -b crash > "$P/crash-after-pages.txt"; echo "crash lines: $(grep -c . "$P/crash-after-pages.txt")"
    face ;;
cold)
    w shell am force-stop world.taqwa.app
    face; sleep 2; w shell input keyevent KEYCODE_WAKEUP; sleep 1
    w shell screenrecord --time-limit 3 /sdcard/p53-cold.mp4 &
    rec=$!
    sleep 0.3
    w shell am start -W -n world.taqwa.app/world.taqwa.app.wear.app.WearMainActivity | tr -d '\r' | tee "$P/cold-start.txt"
    wait "$rec"
    w pull /sdcard/p53-cold.mp4 "$P/cold.mp4" > /dev/null; w shell rm -f /sdcard/p53-cold.mp4
    ls -la "$P/cold.mp4"; face ;;
phoneback)
    ph uninstall world.taqwa.app | tail -1
    echo "phone's Taqwa: $(ph shell pm path world.taqwa.app | grep -c package) installed" ;;
debug)
    w install -r "$REPO/wearApp/build/outputs/apk/debug/wearApp-debug.apk" | tail -1
    w install -r -d "$P/found/app.apk" | tail -1
    echo "installed sha1: $(installed_sha)" ;;
restore)
    "$DRIVERS/restore.sh" ;;
*)
    sed -n 2,16p "$0"; exit 2 ;;
esac
