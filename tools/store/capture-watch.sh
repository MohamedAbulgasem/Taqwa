#!/bin/bash
# The App Store's Apple Watch screenshots (plan 5 Task 16), one folder per language, on a paired
# iPhone and Apple Watch simulator pair of your own, running the DEBUG build (scripts/ios-build.sh
# with IOS_DEST naming that iPhone). Every word, digit and time on the watch is the phone's feed,
# sent over WatchConnectivity by the iPhone app in that language: no debug sample is stored.
#
#   tools/store/capture-watch.sh <iphone udid> <watch udid> en ar fr tr id ur bn      shots 1-3
#   FACE=1 tools/store/capture-watch.sh <iphone udid> <watch udid> en ar …           shot 4
#   ONLY="2-tasbeeh" tools/store/capture-watch.sh <iphone udid> <watch udid> en …     re-shoots just those
#
# Before the first run, once:
#   - pair the two (xcrun simctl pair <watch> <iphone>), boot the iPhone, install
#     build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app on it and its Watch/TaqwaWatch.app on
#     the watch (a watch app whose phone lacks its counterpart is removed, and its complications are
#     never offered);
#   - set the iPhone app's city to Cape Town by hand: with the app installed and never launched, write
#     its settings file (Library/Application Support/Taqwa/taqwa.preferences_pb in its data container)
#     with onboarding_complete, location_latitude -33.926, location_longitude 18.423, location_timezone
#     Africa/Johannesburg, location_city "Cape Town", location_city_id 3369157, location_country ZA,
#     location_region "Western Cape" and location_source MANUAL (a DataStore PreferenceMap, protobuf);
#   - for FACE=1, the watch's current face set by hand in its face editor (touch and hold the face, New,
#     All Watch Faces, Modular, Add, then Edit): Modular in its default Multicolor, so that Taqwa's
#     complications draw in their own amber; Taqwa's "Next prayer" in the middle slot and the bottom-left
#     one; every other slot Off, the Date slot too, so that the face's own text is its time alone (the
#     simulator draws the date in English under Urdu and Bengali). The editor needs the crown, which
#     the iOS Simulator panel or a UI test (XCUIDevice.rotateDigitalCrown) can turn.
#
# Shots 1-3 (Today, Tasbeeh, Qibla) are of a Cape Town afternoon. watchOS has no status-bar override
# and draws the clock outside the app, so the watch is booted in a whole-hour zone (WATCH_TZ, by
# SIMCTL_CHILD_TZ, which reaches every process of the boot) whose wall clock now reads the afternoon,
# and the app's DEBUG clock (-taqwaAt) is set to that same wall time at each launch: the system's
# clock and the app's instant agree to the second. The feed's times are Cape Town's whatever the
# watch's zone: the watch app reads no zone. Pick WATCH_TZ so that its wall clock is between Dhuhr
# and Asr on the Cape Town date of the moment (Etc/GMT+5 from 19:00 to 21:00 UTC in November).
# Tasbeeh is Astaghfirullah at 64 (the mockup's), Qibla the DEBUG compass at magnetic 50, true 23
# with the feed's declination of -27 at Cape Town: facing the qibla.
#
# Shot 4 (the face) is at the real time in the Mac's own zone, which must be Cape Town's (UTC+2), so
# the face's clock and the complications' countdown, both from the real instant, agree.
#
# Each language reboots the watch with its language and region as the system's (the clock in the
# corner follows it), installs the watch app again (WATCH_APP, the build's by default), then launches
# the iPhone app in that language so that it sends the feed. The system's clock is held to 24-hour
# time in every language (AppleICUForce24HourTime, the iPhone's "24-Hour Time" switch), as Taqwa's
# own times and the phone shots' status bars are: ar_LY, ur_PK and bn_BD default to 12-hour, which
# put "2:12" in the corner beside Taqwa's 16:14 and a face reading "10:11" an evening.
#
# Output: docs/store/screenshots/appstore-watch/<lang>/<lang>-0{1..4}.png, each 422 x 514 (Apple
# Watch Ultra 3 and 4, the largest size App Store Connect lists): the watch's screen as
# `simctl io screenshot` gives it, with no frame, its opaque alpha channel dropped (App Store Connect
# takes no alpha channel). Needs Python 3 with Pillow, as frame.py does.
P=$1; W=$2; shift 2
ROOT=$(cd "$(dirname "$0")/../.." && pwd); OUT=$ROOT/docs/store/screenshots/appstore-watch
APP=world.taqwa.ios; WAPP=world.taqwa.ios.watchkitapp
WATCH_APP=${WATCH_APP:-$ROOT/build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app/Watch/TaqwaWatch.app}
WATCH_TZ=${WATCH_TZ:-Etc/GMT+5}
locale_for() { case $1 in en) echo en_GB;; ar) echo ar_LY;; fr) echo fr_FR;; tr) echo tr_TR;; id) echo id_ID;; ur) echo ur_PK;; bn) echo bn_BD;; esac; }
tag_for() { case $1 in en) echo en-GB;; ar) echo ar-LY;; fr) echo fr-FR;; tr) echo tr-TR;; id) echo id-ID;; ur) echo ur-PK;; bn) echo bn-BD;; esac; }
want() { [ -z "$ONLY" ] || [[ " $ONLY " == *" $1 "* ]]; }
# shot <file>: the watch's screen, saved without its alpha channel.
shot() {
  xcrun simctl io $W screenshot "$1" >/dev/null 2>&1
  python3 -I -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").save(sys.argv[1])' "$1"
}
feed_lang() {
  local g; g=$(xcrun simctl get_app_container $W $WAPP group.world.taqwa.ios 2>/dev/null) || return
  python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["ui"]["language"])' \
    "$g/Library/Application Support/TaqwaWatch/feed.json" 2>/dev/null
}
# reboot <lang>: the watch's own language and region set to the language's, booted in WATCH_TZ (shots
# 1-3) or the Mac's zone (shot 4).
reboot() {
  xcrun simctl spawn $W defaults write -g AppleLanguages -array "$1"
  xcrun simctl spawn $W defaults write -g AppleLocale "$(locale_for $1)"
  xcrun simctl spawn $W defaults write -g AppleICUForce24HourTime -bool YES
  xcrun simctl shutdown $W
  if [ -n "$FACE" ]; then xcrun simctl boot $W; else SIMCTL_CHILD_TZ=$WATCH_TZ xcrun simctl boot $W; fi
  xcrun simctl bootstatus $W -b >/dev/null 2>&1; sleep 8
  # After a reboot the iPhone reads the watch app as not installed (isWatchAppInstalled false) and
  # sends nothing, until the watch app is installed again (its data and the feed are kept).
  xcrun simctl install $W "$WATCH_APP"; sleep 3
}
# feed <lang>: the iPhone app relaunched in the language, which sends its feed; waits until the watch
# holds it (the watch app open, so that the context is taken in at once).
feed() {
  local want_tag; want_tag=$(tag_for $1)
  xcrun simctl launch $W $WAPP >/dev/null; sleep 3
  xcrun simctl terminate $P $APP 2>/dev/null; sleep 1
  xcrun simctl launch $P $APP -AppleLanguages "($1)" -AppleLocale "$(locale_for $1)" >/dev/null
  for _ in $(seq 1 40); do [ "$(feed_lang)" = "$want_tag" ] && { echo "  feed $want_tag"; sleep 3; return 0; }; sleep 1; done
  echo "  no $want_tag feed on the watch after 40 s" >&2; return 1
}
# page <name> <args…>: the watch app opened on a page at the watch's wall time, then shot.
page() {
  local name=$1; shift
  xcrun simctl terminate $W $WAPP 2>/dev/null; sleep 1
  local at; at=$(TZ=$WATCH_TZ date +%H:%M:%S)
  xcrun simctl launch $W $WAPP -taqwaAt "$at" "$@" >/dev/null
  sleep 7; shot "$D/$name"; echo "  shot $name at $at"
}
# Both booted first: reboot writes the watch's settings with simctl spawn, which a shut-down watch
# refuses, leaving the first language on the last run's settings.
xcrun simctl bootstatus $P -b >/dev/null 2>&1; xcrun simctl bootstatus $W -b >/dev/null 2>&1
for L in "$@"; do
  D=$OUT/$L; mkdir -p "$D"; echo "$L"
  reboot $L; feed $L || exit 1
  if [ -n "$FACE" ]; then
    # The face: the app closed, the face in front, and its complications reloaded from the feed.
    xcrun simctl terminate $W $WAPP 2>/dev/null; sleep 12
    shot "$D/$L-04.png"; echo "  shot $L-04.png at $(date +%H:%M:%S)"
    continue
  fi
  want 1-today && page $L-01.png -taqwaPage today
  want 2-tasbeeh && page $L-02.png -taqwaPage tasbeeh -taqwaFresh 1 -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64
  want 3-qibla && page $L-03.png -taqwaPage qibla -taqwaHeading 50
  xcrun simctl terminate $W $WAPP 2>/dev/null
done
for f in "$OUT"/*/*.png; do
  [ "$(sips -g pixelWidth -g pixelHeight -g hasAlpha "$f" | awk '/pixel|hasAlpha/ {printf "%s ", $2}')" = "422 514 no " ] ||
    echo "NOT 422 x 514 WITHOUT ALPHA: $f" >&2
done
echo "appstore-watch: $(find "$OUT" -name '*.png' | wc -l | tr -d ' ') files"
