#!/bin/bash
# Play's Wear OS screenshots, from Mohamed's Galaxy Watch over wireless debugging (plan 5 Task 16):
# the five, in order, each taken when he has opened that screen on the watch and pressed Return.
#
#   tools/store/capture-wear.sh <serial> <lang>             the five (lang: en ar fr tr id ur bn)
#   tools/store/capture-wear.sh --only 4 <serial> <lang>    re-shoots one of them, here Tasbeeh
#   tools/store/capture-wear.sh --check <png>...            checks files against Play's rules, no device
#
#   1 the face (Taqwa: Arch, Amber, the wearer's two slots empty), 2 the Tile, 3 Today, 4 Tasbeeh,
#   5 Qibla
#
# Output: docs/store/screenshots/play-wear/<lang>/<lang>-0N.png, the watch's own screen
# (adb exec-out screencap -p), 438 x 438 on the Galaxy Watch 8 Classic. WEAR_SHOTS_DIR=<dir> writes
# <dir>/<lang>/ instead (a test or a trial run). A whole run first takes away the language's five old
# files, which git still has; --only N replaces that one file. The files are committed (Task 16
# Step 4) and uploaded by hand, per language (M10).
#
# Play's rules for Wear OS screenshots, as Play Console Help gives them on 9 November 2026
# (https://support.google.com/googleplay/android-developer/answer/9866151, "Wear OS", and
# https://support.google.com/googleplay/android-developer/answer/1078870, "Screenshots"):
#   - a 1:1 aspect ratio, at least 384 x 384 px;
#   - JPEG or 24-bit PNG, no alpha; no side over 3840 px;
#   - no transparent background or masking; not inside a device frame;
#   - only the app's interface, with no text, graphics or backgrounds added;
#   - up to 8 per device type; with Tiles, a Tile shot is recommended.
# Each shot is checked here for the first three: square, 384 to 3840 px, every pixel opaque, and not
# all black (a screen that was off). It is then written as a 24-bit PNG, since screencap gives RGBA.
# The other rules hold by taking the screen as it is: nothing here frames or captions a shot
# (frame.py is the phones'). --check holds a file to the same, and also refuses an alpha channel.
#
# Devices: never the plugged-in LoopPhone KB2604110095, nor emulator-5558 or emulator-5562 (others'
# work): those are refused before any adb call, as is a device whose own serial is one of them, or
# a device that is not a watch. Every adb call names the serial.
#
# THE SHOOT. Mohamed's hands on the S23 and the watch; the Lead runs this at the Mac.
# Before the first language:
#   1. The build: the watch has the Wear build that goes to production, from Play or the candidate
#      itself, never a debug or staging build (no debug line may show), and the S23 the phone
#      build that pairs with it.
#   2. Reach the watch. The watch and the Mac on the same Wi-Fi, and on the watch Settings ›
#      Connections › Wi-Fi on. Settings › Developer options › ADB debugging and Wireless debugging on
#      (no Developer options: Settings › About watch › Software information, tap Software version
#      until they are on).
#      - Pairing, once (Task 13 Step 3 may have done it): Wireless debugging › Pair new device,
#        then, with the IP and port that screen shows, and he types its code:
#          adb pair <ip>:<pairing port>
#      - Connecting, each time Wireless debugging turns on (the connect port is not the pairing
#        port, and it changes each time):
#          dns-sd -B _adb-tls-connect._tcp                    the watch's name, adb-<id>-<suffix>
#          dns-sd -L adb-<id>-<suffix> _adb-tls-connect._tcp  "can be reached at <host>.local.:<port>"
#          adb connect <ip>:<port>                            <ip> as Wireless debugging shows it
#          adb devices -l                                     the serial to give this script
#        (Ctrl-C ends each dns-sd. Wireless debugging's own screen shows the port too, under
#        "IP address & Port".)
#      - Lost mid-set (the watch slept its Wi-Fi, restarted or left the network): Wireless
#        debugging on again, the dns-sd and connect lines again, then --only N from the shot it
#        missed.
#   3. Stage the S23's Taqwa, noting each setting first so as to put it back:
#      - Settings › Location › Choose a city instead › Cape Town, the house's staging city, so no
#        shot shows where he is. Today's title and the Tile say Cape Town within a minute.
#      - The 24-hour clock, as the phone's store shots (S23 Settings › General management › Date
#        and time).
#      - The time: Cape Town's afternoon, between Dhuhr and Asr (about 14:00 to 16:00 SAST, UTC+2),
#        the hour of the watch's mock day (14:58). If the S23's time zone is not UTC+2 then, set it
#        to Johannesburg for the shoot (Date and time › Automatic time zone off › Select time zone),
#        so that the watch's clock and Cape Town's times agree.
#   4. Quiet the watch: Do not disturb OFF (this script checks: the Galaxy face shows its icon at
#      12 o'clock, 11 November), and clear the notifications (their page › Clear all), so that
#      neither a notification nor the face's unread dot shows. Off the charger, whose screen
#      covers the face.
#   5. The face: touch and hold the face › Taqwa › Customise: Style Arch, Colour Amber,
#      Complications: the left and the right one each Empty (or None). Note his own choice first.
# Each language (en ar fr tr id ur bn):
#   6. S23: Settings › Apps › Taqwa › Language › that language (Arabic: plain العربية, whose Latin
#      digits the phone's Play shots have). Today on the watch is in it within a minute.
#   7. tools/store/capture-wear.sh <serial> <lang>. At each prompt, on the watch, then Return while
#      its screen is on:
#        1 the face: the Home key back to it, awake;
#        2 the Tile: swipe left from the face to Taqwa's "Prayer times" (not there: touch and hold
#          a tile › + › Taqwa);
#        3 Today: Apps › Taqwa, its first page, with no "Add the Taqwa face" row;
#        4 Tasbeeh: swipe to the next page; a built-in dhikr at count 0 of round 1, so that the
#          bezel hint shows, on one line (swipe up › choose one he is not counting, or Reset, as he
#          chooses: Reset takes that count away);
#        5 Qibla: the next page; the watch held flat until the dial points, with no tilt or
#          calibration line.
#   8. The Lead opens the five: Cape Town, the language, no notification, no debug line, the face
#      Arch, Amber, its slots empty. A wrong one: --only N.
# After the last language:
#   9. Put back what was noted: the S23's city (or Use my location), Taqwa's language, the clock and
#      automatic time zone; the face's style, colour and slots; Wireless debugging off.
#  10. Commit docs/store/screenshots/play-wear (Task 16 Step 4). Mohamed uploads each language's five
#      under Main store listing › Wear OS (M10).
set -uo pipefail
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DENY="KB2604110095 emulator-5558 emulator-5562"
LANGS="en ar fr tr id ur bn"
SCREEN=("" "the Taqwa face (Arch, Amber, both slots empty)" "the Taqwa Tile (Prayer times)" \
    "Today in Taqwa" "Tasbeeh in Taqwa (count 0 of round 1)" "Qibla in Taqwa")
APP=world.taqwa.app

usage() { sed -n '5,7p' "$0" | sed 's/^#   //' >&2; exit 2; }

# fit <png> [write]: Play's checks. With "write", an opaque RGBA shot is written again as a 24-bit
# PNG; without it (--check), an alpha channel is itself a failure. Prints what it found.
fit() {
    python3 -I - "$1" "${2:-}" <<'PY'
import sys
from PIL import Image
path, write = sys.argv[1], sys.argv[2] == "write"
def no(why): print(why); sys.exit(1)
try:
    im = Image.open(path); im.load()
except Exception as e:
    no(f"not an image ({e.__class__.__name__})")
w, h = im.size
if im.format not in ("PNG", "JPEG"): no(f"{im.format}, not PNG or JPEG")
if w != h: no(f"{w} x {h}, not square (Play: 1:1)")
if w < 384: no(f"{w} x {h}, under Play's 384 x 384")
if w > 3840: no(f"{w} x {h}, over Play's 3840 px")
alpha = im.mode in ("RGBA", "LA", "PA") or "transparency" in im.info
if alpha:
    lo = im.convert("RGBA").getchannel("A").getextrema()[0]
    if lo < 255: no(f"{w} x {h}, with transparent pixels (alpha down to {lo}): Play takes none")
rgb = im.convert("RGB")
if rgb.convert("L").getextrema()[1] == 0: no(f"{w} x {h}, all black: was the screen off?")
if im.format == "PNG" and im.mode != "RGB":
    if not write: no(f"{w} x {h}, {im.mode}: Play takes a 24-bit PNG, no alpha")
    rgb.save(path, "PNG", optimize=True)
print(f"{w} x {h}, {'24-bit PNG' if im.format == 'PNG' else 'JPEG'}")
PY
}

if [ "${1:-}" = "--check" ]; then
    shift; [ "$#" -ge 1 ] || usage; bad=0
    for f in "$@"; do
        if r=$(fit "$f"); then echo "ok    $f: $r"; else echo "FAIL  $f: $r"; bad=1; fi
    done
    exit $bad
fi

ONLY=""
if [ "${1:-}" = "--only" ]; then ONLY=${2:-}; shift 2 || usage; fi
[ "$#" -eq 2 ] || usage
S=$1; L=$2
case "$ONLY" in ""|[1-5]) ;; *) echo "--only takes 1 to 5, not '$ONLY'" >&2; exit 2;; esac
[[ " $LANGS " == *" $L "* ]] || { echo "no language '$L': $LANGS" >&2; exit 2; }
denied() { local s; for s in $DENY; do [[ "$1" == *"$s"* ]] && return 0; done; return 1; }
if denied "$S"; then echo "$S is not to be touched here ($DENY): refused" >&2; exit 2; fi

a() { adb -s "$S" "$@" </dev/null; }          # never reads the prompts' input
ash() { a shell "$@" 2>/dev/null | tr -d '\r'; }
[ "$(a get-state 2>/dev/null)" = "device" ] || { echo "$S is not connected (adb devices -l; the header says how)" >&2; exit 1; }
own=$(ash getprop ro.serialno)
if [ -n "$own" ] && denied "$own"; then echo "$S is $own, not to be touched here: refused" >&2; exit 2; fi
[[ "$(ash getprop ro.build.characteristics)" == *watch* ]] || { echo "$S is not a watch: refused" >&2; exit 2; }
v=$(ash dumpsys package $APP | grep -m1 versionName | sed 's/.*=//')
echo "$S: $(ash getprop ro.product.model), $(ash wm size | head -1 | sed 's/^Physical size: //'), Taqwa ${v:-not installed}"

ask() { local r; printf '%s' "$1" >&2; read -r r || { echo >&2; echo "no more input: stopped" >&2; exit 1; }; REPLY=$r; }
# Do not disturb off, once (zen_mode 0 is off): One UI Watch draws its icon on the face.
while [ "$(ash settings get global zen_mode)" != "0" ]; do
    ask "Do not disturb is on, and the face shows its icon. Turn it off, then Return (s: go on with it, q: stop) "
    case $REPLY in s) break;; q) exit 1;; esac
done

OUT=${WEAR_SHOTS_DIR:-$ROOT/docs/store/screenshots/play-wear}; D=$OUT/$L; mkdir -p "$D"
[ -z "$ONLY" ] && rm -f "$D/$L"-0[1-5].png
top() { ash dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1; }
awake() { [ "$(ash dumpsys power | grep -m1 -oE 'mWakefulness=[A-Za-z]+')" = "mWakefulness=Awake" ]; }

shoot() {
    local n=$1 f="$D/$L-0$1.png" part="$D/.$L-0$1.png.part" t r
    ask "$n/5 Open ${SCREEN[$n]} on the watch, then press Return "
    while :; do
        [ "$REPLY" = q ] && { rm -f "$part"; exit 1; }
        if [ "$(a get-state 2>/dev/null)" != "device" ]; then
            echo "  lost $S: connect again (header, step 2), then --only $n with the serial adb gives it" >&2; exit 1
        fi
        a shell input keyevent KEYCODE_WAKEUP; sleep 1
        t=$(top)
        if ! awake; then
            ask "  the watch's screen is off. Wake it on ${SCREEN[$n]}, then Return (q: stop) "; continue
        fi
        # The face and the Tile are the system's own screen (on Wear OS 6, sysui's SysUiActivity),
        # neither Taqwa's activity nor the apps list (AllAppsLauncherActivity there).
        if [ "$REPLY" != s ] && { { [ "$n" -le 2 ] && [[ "$t" == $APP/* || "$t" == *Launcher* ]]; } || { [ "$n" -ge 3 ] && [[ "$t" != $APP/* ]]; }; }; then
            ask "  the watch shows ${t:-nothing it names}, not ${SCREEN[$n]}. Return to look again (s: shoot it as it is, q: stop) "; continue
        fi
        a exec-out screencap -p > "$part"
        if r=$(fit "$part" write); then mv "$part" "$f"; echo "  shot $(basename "$f"): $r"; return; fi
        rm -f "$part"
        ask "  not taken: $r. Return to shoot again (q: stop) "
    done
}

echo "$L into $D"
for n in 1 2 3 4 5; do [ -z "$ONLY" ] || [ "$ONLY" = "$n" ] && shoot $n; done
echo "  $(ls "$D" | grep -c "^$L-0[1-5]\.png$") of 5 files. The Lead opens them (header, step 8)."
