# Shared helpers for the P3.9 pass (sourced). Drives emulator-5558 alone.
set -u
# Where things are: the drivers (this folder), the repository, its watch tools and the work
# directory the shots go to, PASS_WORK (default $TMPDIR/taqwa-wear-app-pass); found/ in it holds
# the state a turn found, which restore.sh puts back.
DRIVERS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$DRIVERS/../../../../.." && pwd)"
P="${PASS_WORK:-${TMPDIR:-/tmp}/taqwa-wear-app-pass}"
mkdir -p "$P/found"
T=$REPO/tools/watchface
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
w() { "$ADB" -s emulator-5558 "$@"; }
# the console, bounded (it has hung before while adb shell kept working)
con() { perl -e 'alarm shift; exec @ARGV' 10 "$ADB" -s emulator-5558 emu "$@" > /dev/null || echo "console call failed: $*" >&2; }
shot() { mkdir -p "$(dirname "$1")"; w exec-out screencap -p > "$1"; echo "$(basename "$1") at $(w shell date +%H:%M:%S | tr -d '\r')"; }
dump() { w shell uiautomator dump /sdcard/p39.xml > /dev/null 2>&1; w exec-out cat /sdcard/p39.xml > "$1"; w shell rm -f /sdcard/p39.xml; }
# The words of a dump, in tree order: text and content-desc, with state descriptions.
words() { python3 -I "$DRIVERS/words.py" "$1"; }
rotary() { w shell input rotaryencoder scroll --axis "SCROLL,$1"; }
# The magnetic field for a magnetic heading H (degrees): x = -22 sin H, y = 22 cos H, z = -40.
field() { local xy; xy=$(python3 -I -c "import math,sys; h=math.radians(float(sys.argv[1])); print('%.4f:%.4f:-40' % (-22*math.sin(h), 22*math.cos(h)))" "$1"); con sensor set magnetic-field "$xy"; }
# The sample for a feed label: en (the English 24-hour sample), en12, ar-ly12, ar-sa.
sample_of() { [ "$1" = en ] && echo "" || echo "$1"; }
store() { SAMPLE="$(sample_of "$1")" "$T/feed.sh" "${2:-14:58}" > /dev/null; }
clock() { "$T/clock.sh" "$1" > /dev/null; }
reset_counts() { w shell am force-stop world.taqwa.app; w shell run-as world.taqwa.app rm -f shared_prefs/tasbeeh.xml; }
face() { w shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null; }   # the face, whatever is in front (the HOME key on the face opens the app list)
# The Taqwa Tile in front, found by a word it must show (the carousel may need a wake first, and
# a swipe that lands elsewhere is tried again from the face): tile_shot <out.png> <word> [en|ar]
tile_shot() {
    local out=$1 want=$2 sw=(400 227 60 227 200) i
    [ "${3:-en}" = ar ] && sw=(60 227 400 227 200)
    for i in 1 2 3 4; do
        face; sleep 1; w shell input keyevent KEYCODE_WAKEUP; sleep 1
        w shell input swipe "${sw[@]}"; sleep 3
        dump "${out%.png}.xml"
        if grep -q "$want" "${out%.png}.xml"; then shot "$out" > /dev/null; echo "tile $(basename "$out"): found '$want' (try $i)"; return 0; fi
    done
    shot "$out" > /dev/null; echo "tile $(basename "$out"): NOT FOUND '$want'" >&2; return 1
}
