#!/usr/bin/env bash
# The tests of scripts/phone-emulator.sh and scripts/wear-pair.sh (plan 2 Task 7), with no emulator
# and no device. The scripts are copied into a scratch repository, with the one they run
# (scripts/wear-emulator.sh, which starts the watch), and run on
# a fake SDK (ANDROID_HOME) whose adb, emulator and avdmanager record every call and answer as the
# test sets them: which AVDs exist, which serials are up and as which AVD, whether the phone has
# the Taqwa debug build, and what the harness's `nodes` prints. First on PATH is an adb that only
# records that it ran and fails, so a bare `adb` never reaches the real one beside the phone
# plugged in, and fails the test; an sdkmanager on PATH and in the SDK does the same, so nothing
# here can download an image. They hold that
#   - phone-emulator.sh makes Taqwa_Phone_Wear (pixel_8, the Play Store image) on port 5562 the
#     first time, headless unless --window, and --noplay makes Taqwa_Phone_NoPlay (the image
#     without Play services) on 5564, refusing with the sdkmanager line when that image is not
#     installed, never creating the AVD then, never downloading it;
#   - a new AVD's config.ini loses the placeholders cmdline-tools 23's avdmanager writes
#     (`<build>`, `<temp>`), and gets the GPU, a keyboard and its Play Store flag, true for the
#     Play image and false for the other, which that avdmanager leaves off, and a 6 GB data
#     partition, as the Wear AVD has, for the pixel_8 profile's 10 GB does not fit this Mac's disk;
#   - it reuses its AVD when it already runs, and leaves a port another AVD holds alone;
#   - wear-pair.sh starts each emulator only when it is down, refuses when the phone has no Taqwa
#     debug build, makes the pairing's two halves and nothing else (the phone's forward of host
#     tcp:5601 to its own 5601, and the watch's reverse of its 5601 to host tcp:5601, which a
#     restart of the watch loses), polls the harness's `nodes` until the watch is listed nearby with
#     taqwa_watch, through an adb call that fails on the way, and stops, naming Studio's Pair
#     Wearable, when it does not come back within the time, with adb's own words as the last answer
#     when adb was what failed;
#   - every adb call but `devices` names emulator-5558, emulator-5562 or emulator-5564, never the
#     phone plugged in.
#
#   scripts/test-pairing-scripts.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SOURCE="$(cd "$HERE/.." && pwd)"
WORK="$(cd "$(mktemp -d "${TMPDIR:-/tmp}/taqwa-pairing-test.XXXXXX")" && pwd)"
trap 'rm -rf "$WORK"' EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

PLAY="system-images;android-36;google_apis_playstore;arm64-v8a"
NOPLAY="system-images;android-36;default;arm64-v8a"
WEAR="system-images;android-36;android-wear-signed;arm64-v8a"
HARNESS="shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver -a world.taqwa.app.debug.WATCH --es cmd nodes"

# A fresh scratch repository and fake SDK for each case.
setup() {
    REPO="$WORK/repo"
    SDK="$WORK/sdk"
    rm -rf "$REPO" "$SDK" "$WORK/home" "$WORK/path"
    mkdir -p "$REPO/scripts" "$SDK/platform-tools" "$SDK/emulator" \
        "$SDK/cmdline-tools/latest/bin" "$SDK/up" "$WORK/home" "$WORK/path"
    for f in phone-emulator.sh wear-pair.sh wear-emulator.sh; do
        if [ -f "$SOURCE/scripts/$f" ]; then cp "$SOURCE/scripts/$f" "$REPO/scripts/"; fi
    done
    : > "$SDK/calls.log"
    : > "$SDK/avds"
    printf 'no nodes\n' > "$SDK/nodes-answers"
    mkdir -p "$SDK/system-images/android-36/google_apis_playstore/arm64-v8a" \
        "$SDK/system-images/android-36/android-wear-signed/arm64-v8a"

    cat > "$SDK/platform-tools/adb" <<'EOF'
#!/usr/bin/env bash
F="$(cd "$(dirname "$0")/.." && pwd)"
echo "ADB $*" >> "$F/calls.log"
if [ "$*" = devices ]; then
    echo "List of devices attached"
    for f in "$F"/up/*; do [ -e "$f" ] && printf '%s\tdevice\n' "$(basename "$f")"; done
    echo
    exit 0
fi
[ "${1:-}" = -s ] || { echo "adb without a serial: $*" >&2; exit 1; }
serial=$2; shift 2
up="$F/up/$serial"
case "$*" in
    "emu avd name") if [ -e "$up" ]; then printf '%s\r\nOK\r\n' "$(cat "$up")"; fi ;;
    "emu kill") rm -f "$up" ;;
    "shell getprop sys.boot_completed") if [ -e "$up" ]; then printf '1\r\n'; fi ;;
    "shell getprop ro.build.version.sdk") printf '36\r\n' ;;
    "forward tcp:5601 tcp:5601") touch "$F/forwarded-$serial" ;;
    "reverse tcp:5601 tcp:5601") touch "$F/reversed-$serial" ;;
    "shell pm path world.taqwa.app") if [ -e "$F/installed-$serial" ]; then printf 'package:/data/app/~~x/world.taqwa.app/base.apk\r\n'; fi ;;
    "shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver -a world.taqwa.app.debug.WATCH --es cmd nodes")
        # The watch comes back only through both halves, the phone's forward and the watch's
        # reverse; each poll then takes the next answer, and an answer ADB-ERROR is adb failing.
        answer="no nodes"
        if [ -e "$F/forwarded-$serial" ] && [ -e "$F/reversed-emulator-5558" ]; then
            answer="$(head -n 1 "$F/nodes-answers")"
            if [ "$(wc -l < "$F/nodes-answers")" -gt 1 ]; then
                tail -n +2 "$F/nodes-answers" > "$F/nodes.tmp" && mv "$F/nodes.tmp" "$F/nodes-answers"
            fi
        fi
        if [ "$answer" = ADB-ERROR ]; then echo "error: closed" >&2; exit 1; fi
        printf 'Broadcasting: Intent { act=world.taqwa.app.debug.WATCH flg=0x400000 cmp=world.taqwa.app/.debug.WatchHarnessReceiver (has extras) }\r\n'
        printf 'Broadcast completed: result=-1, data="%s"\r\n' "$answer" ;;
    *) echo "unexpected adb call: -s $serial $*" >&2; exit 1 ;;
esac
EOF
    cat > "$SDK/emulator/emulator" <<'EOF'
#!/usr/bin/env bash
F="$(cd "$(dirname "$0")/.." && pwd)"
if [ "$*" = -list-avds ]; then cat "$F/avds"; exit 0; fi
echo "EMULATOR $*" >> "$F/calls.log"
avd=; port=
while [ $# -gt 0 ]; do
    case "$1" in -avd) avd=$2; shift ;; -port) port=$2; shift ;; esac
    shift
done
echo "$avd" > "$F/up/emulator-$port"
sleep 8
EOF
    cat > "$SDK/cmdline-tools/latest/bin/avdmanager" <<'EOF'
#!/usr/bin/env bash
F="$(cd "$(dirname "$0")/../../.." && pwd)"
echo "AVDMANAGER $*" >> "$F/calls.log"
cat > /dev/null
name=
while [ $# -gt 0 ]; do [ "$1" = --name ] && name=$2; shift; done
echo "$name" >> "$F/avds"
# What cmdline-tools 23 writes: every hardware default, the placeholders among them, the GPU off
# and the Play Store off whatever the image.
mkdir -p "$HOME/.android/avd/$name.avd"
cat > "$HOME/.android/avd/$name.avd/config.ini" <<CONFIG
PlayStore.enabled=no
avd.id=<build>
avd.name=<build>
disk.dataPartition.path=<temp>
disk.dataPartition.size=10G
hw.gpu.enabled=no
hw.keyboard=no
hw.ramSize=2048
image.sysdir.1=system-images/android-36/x/arm64-v8a/
CONFIG
EOF
    cat > "$SDK/cmdline-tools/latest/bin/sdkmanager" <<EOF
#!/usr/bin/env bash
echo "SDKMANAGER \$*" >> "$SDK/calls.log"
exit 1
EOF
    cat > "$WORK/path/adb" <<EOF
#!/usr/bin/env bash
echo "BARE ADB \$*" >> "$SDK/calls.log"
exit 1
EOF
    cp "$WORK/path/adb" "$WORK/path/sdkmanager"
    sed -i '' 's/BARE ADB/BARE SDKMANAGER/' "$WORK/path/sdkmanager" 2> /dev/null || sed -i 's/BARE ADB/BARE SDKMANAGER/' "$WORK/path/sdkmanager"
    chmod +x "$SDK/platform-tools/adb" "$SDK/emulator/emulator" "$SDK/cmdline-tools/latest/bin/avdmanager" \
        "$SDK/cmdline-tools/latest/bin/sdkmanager" "$WORK/path/adb" "$WORK/path/sdkmanager"
}

# Runs one script of the scratch repository on the fake SDK: run <script> [args…]; sets OUT and CODE.
run() {
    local script=$1
    shift
    set +e
    OUT="$(cd "$REPO" && env -i PATH="$WORK/path:/usr/bin:/bin" HOME="$WORK/home" TMPDIR="$WORK" \
        ANDROID_HOME="$SDK" JAVA_HOME=/nonexistent PAIR_TIMEOUT_SECONDS="${PAIR_TIMEOUT_SECONDS:-}" \
        PAIR_POLL_SECONDS="${PAIR_POLL_SECONDS:-}" bash "scripts/$script" "$@" 2>&1)"
    CODE=$?
    set -e
}

calls() { cat "$SDK/calls.log"; }
has_call() { grep -qxF -- "$1" "$SDK/calls.log"; }
no_call_like() { ! grep -q -- "$1" "$SDK/calls.log"; }
up_as() { echo "$2" > "$SDK/up/$1"; }

# Every adb call names one of the three serials (or is `devices`), and nothing ran from PATH.
serials_only() {
    local bad
    bad="$(grep '^ADB ' "$SDK/calls.log" | grep -vxF 'ADB devices' \
        | grep -vE '^ADB -s emulator-(5558|5562|5564) ' || true)"
    [ -z "$bad" ] || { fail "$1: an adb call without one of the three serials: $bad"; return; }
    if grep -q 'KB2604110095' "$SDK/calls.log"; then fail "$1: the phone plugged in was named"; return; fi
    if grep -qE '^(BARE|SDKMANAGER)' "$SDK/calls.log"; then fail "$1: ran $(grep -E '^(BARE|SDKMANAGER)' "$SDK/calls.log" | head -1)"; return; fi
    pass "$1: adb named its serial every time, and nothing ran from PATH"
}

# ── phone-emulator.sh ────────────────────────────────────────────────────────────────

setup
run phone-emulator.sh --bogus
if [ "$CODE" = 2 ] && no_call_like '^EMULATOR' && no_call_like '^AVDMANAGER'; then pass "an unknown option is refused (2) and nothing starts"
else fail "an unknown option: exit $CODE, calls: $(calls)"; fi

setup
run phone-emulator.sh
if [ "$CODE" = 0 ] \
    && has_call "AVDMANAGER create avd --name Taqwa_Phone_Wear --package $PLAY --device pixel_8" \
    && has_call "EMULATOR -avd Taqwa_Phone_Wear -port 5562 -no-snapshot-save -no-boot-anim -no-window -gpu swiftshader_indirect -no-audio" \
    && grep -q 'API level 36' <<< "$OUT"; then
    pass "the first run makes Taqwa_Phone_Wear (pixel_8, Play Store image) and boots it headless on 5562"
else fail "the first run: exit $CODE, out: $OUT, calls: $(calls)"; fi
serials_only "the first run"
CONFIG="$WORK/home/.android/avd/Taqwa_Phone_Wear.avd/config.ini"
if [ -f "$CONFIG" ] && ! grep -qE '<build>|<temp>' "$CONFIG" \
    && grep -qx 'PlayStore.enabled=true' "$CONFIG" && grep -qx 'hw.gpu.enabled=yes' "$CONFIG" \
    && grep -qx 'hw.keyboard=yes' "$CONFIG" && grep -qx 'hw.ramSize=2048' "$CONFIG" \
    && grep -qx 'disk.dataPartition.size=6G' "$CONFIG" \
    && [ "$(grep -c '^PlayStore.enabled=' "$CONFIG")" = 1 ] && [ "$(grep -c '^disk.dataPartition.size=' "$CONFIG")" = 1 ]; then
    pass "the new AVD's config loses avdmanager's placeholders and gets its Play Store, GPU, keyboard and a 6 GB data partition"
else fail "the new AVD's config: $(cat "$CONFIG" 2> /dev/null)"; fi

setup
echo Taqwa_Phone_Wear > "$SDK/avds"
run phone-emulator.sh --window
if [ "$CODE" = 0 ] && no_call_like '^AVDMANAGER' \
    && has_call "EMULATOR -avd Taqwa_Phone_Wear -port 5562 -no-snapshot-save -no-boot-anim"; then
    pass "--window boots the AVD it already has, in a window"
else fail "--window: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
echo Taqwa_Phone_Wear > "$SDK/avds"
up_as emulator-5562 Taqwa_Phone_Wear
run phone-emulator.sh
if [ "$CODE" = 0 ] && no_call_like '^EMULATOR' && grep -q 'already running' <<< "$OUT"; then
    pass "a phone already running is reused, not started again"
else fail "already running: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
echo Taqwa_Phone_Wear > "$SDK/avds"
up_as emulator-5562 Someone_Elses
run phone-emulator.sh
if [ "$CODE" = 1 ] && no_call_like '^EMULATOR' && no_call_like 'emu kill' && grep -q 'Someone_Elses' <<< "$OUT"; then
    pass "a port another AVD holds is left alone"
else fail "another AVD on 5562: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
run phone-emulator.sh --noplay
if [ "$CODE" = 1 ] && no_call_like '^AVDMANAGER' && no_call_like '^EMULATOR' \
    && grep -qF "sdkmanager \"$NOPLAY\"" <<< "$OUT"; then
    pass "--noplay without its image refuses with the sdkmanager line and makes nothing"
else fail "--noplay without its image: exit $CODE, out: $OUT, calls: $(calls)"; fi
serials_only "--noplay without its image"

setup
mkdir -p "$SDK/system-images/android-36/default/arm64-v8a"
run phone-emulator.sh --window --noplay
if [ "$CODE" = 0 ] \
    && has_call "AVDMANAGER create avd --name Taqwa_Phone_NoPlay --package $NOPLAY --device pixel_8" \
    && has_call "EMULATOR -avd Taqwa_Phone_NoPlay -port 5564 -no-snapshot-save -no-boot-anim"; then
    pass "--noplay makes Taqwa_Phone_NoPlay on the image without Play services, on 5564"
else fail "--noplay: exit $CODE, out: $OUT, calls: $(calls)"; fi
serials_only "--noplay"
CONFIG="$WORK/home/.android/avd/Taqwa_Phone_NoPlay.avd/config.ini"
if [ -f "$CONFIG" ] && ! grep -qE '<build>|<temp>' "$CONFIG" && grep -qx 'PlayStore.enabled=false' "$CONFIG"; then
    pass "the phone without Play services says so in its config"
else fail "the NoPlay config: $(cat "$CONFIG" 2> /dev/null)"; fi

# ── wear-pair.sh ─────────────────────────────────────────────────────────────────────

WATCH_LINE='node 7c1e "Taqwa_Wear_OS_6" nearby=true taqwa_watch=true'

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
touch "$SDK/installed-emulator-5562"
printf 'no nodes\n%s\n' "$WATCH_LINE" > "$SDK/nodes-answers"
PAIR_POLL_SECONDS=1 run wear-pair.sh
if [ "$CODE" = 0 ] \
    && has_call "EMULATOR -avd Taqwa_Wear_OS_6 -port 5558 -no-snapshot-save -no-boot-anim -no-window -gpu swiftshader_indirect -no-audio" \
    && has_call "EMULATOR -avd Taqwa_Phone_Wear -port 5562 -no-snapshot-save -no-boot-anim -no-window -gpu swiftshader_indirect -no-audio" \
    && has_call "ADB -s emulator-5562 forward tcp:5601 tcp:5601" \
    && has_call "ADB -s emulator-5558 reverse tcp:5601 tcp:5601" \
    && grep -qF "$WATCH_LINE" <<< "$OUT"; then
    pass "both down: both start, the phone's forward and the watch's reverse are made, and the watch is found nearby with Taqwa"
else fail "both down: exit $CODE, out: $OUT, calls: $(calls)"; fi
if [ "$(grep -c -- "ADB -s emulator-5562 $HARNESS" "$SDK/calls.log")" = 2 ]; then
    pass "the poll asks the harness until the watch is there, and stops"
else fail "the poll: $(grep -c -- "$HARNESS" "$SDK/calls.log") asks"; fi
if [ "$(grep -cE ' (forward|reverse) ' "$SDK/calls.log")" = 2 ]; then pass "one forward and one reverse, and no other"
else fail "forwards and reverses: $(grep -E 'forward|reverse' "$SDK/calls.log")"; fi
serials_only "both down"

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
printf 'ADB-ERROR\n%s\n' "$WATCH_LINE" > "$SDK/nodes-answers"
PAIR_POLL_SECONDS=1 run wear-pair.sh
if [ "$CODE" = 0 ] && grep -qF "$WATCH_LINE" <<< "$OUT" \
    && [ "$(grep -c -- "ADB -s emulator-5562 $HARNESS" "$SDK/calls.log")" = 2 ]; then
    pass "an adb call that fails during the poll is asked again, not the end of the script"
else fail "an adb error in the poll: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
printf 'ADB-ERROR\n' > "$SDK/nodes-answers"
PAIR_TIMEOUT_SECONDS=2 PAIR_POLL_SECONDS=1 run wear-pair.sh
if [ "$CODE" = 1 ] && grep -qF 'adb failed: error: closed' <<< "$OUT"; then
    pass "an adb that fails until the deadline is named by its own words in the last answer"
else fail "the last answer of a failing adb: exit $CODE, out: $OUT"; fi
serials_only "a failing adb"

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
printf '%s\n' "$WATCH_LINE" > "$SDK/nodes-answers"
PAIR_POLL_SECONDS=1 run wear-pair.sh --window
if [ "$CODE" = 0 ] && no_call_like '^EMULATOR' && no_call_like 'emu kill'; then
    pass "both up: nothing is started or stopped"
else fail "both up: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
touch "$SDK/installed-emulator-5562"
printf '%s\n' "$WATCH_LINE" > "$SDK/nodes-answers"
PAIR_POLL_SECONDS=1 run wear-pair.sh --window
if [ "$CODE" = 0 ] && has_call "EMULATOR -avd Taqwa_Phone_Wear -port 5562 -no-snapshot-save -no-boot-anim" \
    && no_call_like 'EMULATOR -avd Taqwa_Wear_OS_6'; then
    pass "--window starts the phone in a window, and only what is down"
else fail "--window: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
run wear-pair.sh
if [ "$CODE" = 1 ] && no_call_like 'forward' && no_call_like 'reverse' && no_call_like 'WatchHarnessReceiver' \
    && grep -q 'androidApp-debug.apk' <<< "$OUT"; then
    pass "a phone without the Taqwa debug build is refused before the forward, naming the install"
else fail "no debug build: exit $CODE, out: $OUT, calls: $(calls)"; fi

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
printf 'node 7c1e "Taqwa_Wear_OS_6" nearby=false taqwa_watch=true\n' > "$SDK/nodes-answers"
PAIR_TIMEOUT_SECONDS=3 PAIR_POLL_SECONDS=1 run wear-pair.sh
if [ "$CODE" = 1 ] && grep -q 'Pair Wearable' <<< "$OUT"; then
    pass "a watch that does not come back nearby within the time stops the script, naming Pair Wearable"
else fail "never nearby: exit $CODE, out: $OUT"; fi
serials_only "never nearby"

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
printf 'node 9a "Other watch" nearby=true taqwa_watch=false\n' > "$SDK/nodes-answers"
PAIR_TIMEOUT_SECONDS=3 PAIR_POLL_SECONDS=1 run wear-pair.sh
if [ "$CODE" = 1 ]; then pass "a nearby node without Taqwa's watch app is not the watch"
else fail "a node without Taqwa: exit $CODE, out: $OUT"; fi

setup
printf 'Taqwa_Wear_OS_6\nTaqwa_Phone_Wear\n' > "$SDK/avds"
up_as emulator-5558 Taqwa_Wear_OS_6
up_as emulator-5562 Taqwa_Phone_Wear
touch "$SDK/installed-emulator-5562"
run wear-pair.sh --bogus
if [ "$CODE" = 2 ] && no_call_like 'forward'; then pass "wear-pair.sh refuses an unknown option (2)"
else fail "wear-pair.sh --bogus: exit $CODE, out: $OUT"; fi

if [ "$FAILS" -gt 0 ]; then
    echo "$FAILS failed"
    exit 1
fi
echo "all passed"
