# Shared by the scripts in tools/watchface (sourced, never run). They drive the Wear OS emulator the
# Taqwa face is verified on, and only that one: the AVD Taqwa_Wear_OS_6 on console port 5558, serial
# emulator-5558 (scripts/wear-emulator.sh starts it). Other devices may be plugged in (a phone on
# USB, the phone emulators on 5554, 5556 and 5560), so nothing here runs a bare adb or a Gradle
# install* task (which installs on every device attached): every adb call names the serial, and every
# install is an adb install to it (plan 5 Task 4).
#
# The mock day is the one the sample feed (:wearApp debug) and the mockups (faces.js) share:
# Sunday 18 October 2026 in Cape Town, SAST (UTC+2), where the emulator's zone is
# Africa/Johannesburg. A time given as HH:MM or HH:MM:SS means that time on the mock day.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB_BIN="$SDK/platform-tools/adb"
SERIAL=emulator-5558
AVD=Taqwa_Wear_OS_6
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$REPO/tools/watchface"
FACE_PACKAGE=world.taqwa.app.watchfacepush.taqwa
APP_PACKAGE=world.taqwa.app
MOCK_DATE=2026-10-18
MOCK_TZ=Africa/Johannesburg

wadb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

# Stops unless emulator-5558 is up and is the Taqwa Wear AVD (an emulator someone else started on
# the port is not ours to drive).
need_emulator() {
    if ! "$ADB_BIN" devices | grep -q "^$SERIAL[[:space:]]*device"; then
        echo "$SERIAL is not running: tools/watchface/emulator.sh starts it" >&2
        exit 1
    fi
    local name
    # bounded: a hung emulator's console takes the connection and never answers (Task 12)
    name="$(perl -e 'alarm shift; exec @ARGV' 10 "$ADB_BIN" -s "$SERIAL" emu avd name 2>/dev/null \
        | head -1 | tr -d '\r')"
    if [ -z "$name" ]; then
        echo "$SERIAL does not say its name within 10 s (hung?); leaving it alone" >&2
        exit 1
    fi
    if [ "$name" != "$AVD" ]; then
        echo "$SERIAL is '${name:-an emulator that does not say}', not $AVD; leaving it alone" >&2
        exit 1
    fi
}

# Epoch seconds of HH:MM[:SS] on the mock day; an all-digit argument is already epoch seconds.
epoch_of() {
    case "$1" in
        *[!0-9]*) python3 -I -c '
import sys, datetime, zoneinfo
t = sys.argv[1].split(":")
h, m, s = int(t[0]), int(t[1]), int(t[2]) if len(t) > 2 else 0
d = datetime.date.fromisoformat(sys.argv[2])
print(int(datetime.datetime(d.year, d.month, d.day, h, m, s, tzinfo=zoneinfo.ZoneInfo(sys.argv[3])).timestamp()))
' "$1" "$MOCK_DATE" "$MOCK_TZ" ;;
        *) echo "$1" ;;
    esac
}

# The emulator's clock as epoch seconds.
device_epoch() { wadb shell date +%s | tr -d '\r'; }
device_time() { wadb shell date +%H:%M:%S | tr -d '\r'; }

# Sets the emulator's clock (automatic time off, so that it stays) to epoch seconds.
set_clock() {
    wadb shell settings put global auto_time 0
    wadb shell cmd alarm set-time "${1}000" > /dev/null
}

# Waits until the emulator's clock has reached epoch seconds $1 (polling, so it returns within about
# a fifth of a second of it).
wait_until() {
    local target="$1"
    until [ "$(device_epoch)" -ge "$target" ]; do sleep 0.15; done
}

# Stores the sample feed for the date of epoch seconds $1 through the watch app's debug receiver
# (named with -n, so the app need not be running, and --include-stopped-packages, so that it also
# reaches an app just installed or stopped). SAMPLE=<name> stores another of the debug build's
# samples (SampleFeed.NAMES: en12, ar-ly12, ar-sa) in place of the English 24-hour one.
store_feed() {
    local extra=()
    if [ -n "${SAMPLE:-}" ]; then extra=(--es sample "$SAMPLE"); fi
    wadb shell am broadcast --include-stopped-packages -n "$APP_PACKAGE/$APP_PACKAGE.wear.DebugFeedReceiver" \
        -a "$APP_PACKAGE.wear.DEBUG_FEED" --el epoch "$1" ${extra[@]+"${extra[@]}"} > /dev/null
}

# Has the face bind its slots again from what the watch app's store holds now, with nothing
# installed: the debug build's DEBUG_REBIND asks every complication and the Tile to draw again
# (DebugFeedReceiver). Since plan 5 Task 4 the face reaches the watch only inside the watch app
# (push-face.sh), so the tools that rewrite files/feed.json no longer install the face again to make
# it ask (errata p5 #3): a face installed beside Push's slot is not the face the slot holds. Sent
# after the app is stopped, named, and to a stopped package, so it starts the process, whose store
# then reads files/feed.json again.
rebind_face() {
    wadb shell am broadcast --include-stopped-packages -n "$APP_PACKAGE/$APP_PACKAGE.wear.DebugFeedReceiver" \
        -a "$APP_PACKAGE.wear.DEBUG_REBIND" > /dev/null
}

# One screenshot of the emulator into the file $1, and the emulator's time when it was taken.
screenshot() {
    mkdir -p "$(dirname "$1")"
    wadb exec-out screencap -p > "$1"
    echo "$1 at $(device_time)"
}
