#!/usr/bin/env bash
# store.sh's tests, with no emulator and no device: store.sh and lib.sh are copied into a scratch
# repository whose SDK holds a fake adb, which records every call and answers `devices` and
# `emu avd name` as the test sets them. store.sh runs with a scratch HOME and, first on PATH, an adb
# that only records that it ran and fails, so a bare `adb` or one under `$HOME/Library/Android/sdk`
# never reaches the real adb beside the phone plugged in, and fails the test. The fake keeps the
# bytes it is sent only for the whole call
#   -s emulator-5558 shell run-as world.taqwa.app sh -c 'cat > files/feed.json'
# and every check below compares whole calls, never a fragment of one, so a write without run-as,
# as another package, or to any other file stores nothing. They hold that store.sh
#   - writes the file it is given, byte for byte, as the watch app's files/feed.json, from a path
#     relative to where it is run, and never stores the debug build's sample in its place;
#   - runs nothing else as the watch app: its only run-as calls are the removal and the write;
#   - removes files/feed.json.delivery first (rm -f), before the feed is written, then stops the
#     watch app and has the face bind its slots again through the debug build's DEBUG_REBIND, in
#     that order (plan 2 Task 3; the order of WatchLinkContract.STORE_STEPS, so no delivery ever
#     names a feed the watch does not hold);
#   - installs nothing, the face least of all: from plan 5 Task 4 the face reaches the watch only
#     inside the watch app (push-face.sh), and a face installed beside Push's slot is not the one
#     the slot holds (errata p5 #3);
#   - runs adb only as lib.sh names it (the SDK's, never a bare adb from PATH), names emulator-5558
#     in every adb call, never the phone plugged in beside it, and never runs Gradle (an
#     installDebug installs on every device attached);
#   - touches nothing when it has no file, when the file is missing, or when emulator-5558 is not
#     running or is another AVD; and needs no face APK built, since it installs none.
#
#   tools/watchface/store-test.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(cd "$(mktemp -d "${TMPDIR:-/tmp}/taqwa-store-test.XXXXXX")" && pwd)"   # as lib.sh writes REPO
trap 'rm -rf "$WORK"' EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

# The scratch repository: the two scripts, a Gradle wrapper that only records that it ran, and a
# fake SDK. No face APK is built there: store.sh installs none.
REPO="$WORK/repo"
SDK="$WORK/sdk"
mkdir -p "$REPO/tools/watchface" "$SDK/platform-tools" "$WORK/home" "$WORK/path"
cp "$HERE/lib.sh" "$REPO/tools/watchface/"
if [ -f "$HERE/store.sh" ]; then cp "$HERE/store.sh" "$REPO/tools/watchface/"; fi
# The calls store.sh must make, whole, as the fake adb records them ("$*").
RM_CALL="-s emulator-5558 shell run-as world.taqwa.app rm -f files/feed.json.delivery"
WRITE_CALL="-s emulator-5558 shell run-as world.taqwa.app sh -c 'cat > files/feed.json'"
STOP_CALL="-s emulator-5558 shell am force-stop world.taqwa.app"
REBIND_CALL="-s emulator-5558 shell am broadcast --include-stopped-packages -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver -a world.taqwa.app.wear.DEBUG_REBIND"
printf '%s\n' "$WRITE_CALL" > "$SDK/write-call"
cat > "$REPO/gradlew" <<EOF
#!/usr/bin/env bash
echo "GRADLE \$*" >> "$SDK/calls.log"
EOF
chmod +x "$REPO/gradlew"
cat > "$SDK/platform-tools/adb" <<'EOF'
#!/usr/bin/env bash
F="$(cd "$(dirname "$0")/.." && pwd)"
echo "$*" >> "$F/calls.log"
# The feed's bytes are kept for the whole write call alone, never for a call that only contains it.
if [ "$*" = "$(cat "$F/write-call")" ]; then cat > "$F/stored.bin"; exit 0; fi
# Each answer goes out in one write, as the real adb's does ('Taqwa_Wear_OS_6\r\nOK\r\n' at once).
# lib.sh reads them through grep -q and head -1 under pipefail: had this fake written a line, then
# another, a reader that has its line and is gone would kill it with SIGPIPE (exit 141) and fail a
# correct store.sh. A write that finds the reader gone fails quietly, and the fake still exits 0.
trap '' PIPE
case "$*" in
    devices)
        answer='List of devices attached\nKB2604110095\tdevice\n'
        [ -f "$F/no-emulator" ] || answer="${answer}emulator-5558\\tdevice\\n"
        printf '%b\n' "$answer" 2> /dev/null || true
        ;;
    *" emu avd name") printf '%s\nOK\n' "$(cat "$F/avd-name")" 2> /dev/null || true ;;
    *" install -r "*) echo Success ;;
esac
exit 0
EOF
chmod +x "$SDK/platform-tools/adb"
# The adb a bare `adb` finds: first on store.sh's PATH, it records the call apart and fails.
cat > "$WORK/path/adb" <<EOF
#!/usr/bin/env bash
echo "\$*" >> "$SDK/bare-adb.log"
exit 1
EOF
chmod +x "$WORK/path/adb"

# Runs store.sh from directory $1 with the remaining arguments, on a fresh fake device; its exit code
# is in $CODE and its output in $WORK/out.txt.
run_store() {
    local dir="$1"; shift
    rm -f "$SDK/calls.log" "$SDK/stored.bin" "$SDK/bare-adb.log"
    touch "$SDK/calls.log"
    CODE=0
    (cd "$dir" && PATH="$WORK/path:$PATH" HOME="$WORK/home" ANDROID_HOME="$SDK" \
        bash "$REPO/tools/watchface/store.sh" "$@") > "$WORK/out.txt" 2>&1 || CODE=$?
}
calls() { cat "$SDK/calls.log"; }
# The line of the first call that is $1, whole (grep -x), never a call that only contains it.
line_of() { { grep -n -x -F -- "$1" "$SDK/calls.log" || true; } | head -1 | cut -d: -f1; }
touched_nothing() {   # no write, no removal, no stop, no rebind and no install, and no bare adb at all
    ! grep -q -E 'run-as|force-stop|DEBUG_REBIND| install' "$SDK/calls.log" && [ ! -e "$SDK/stored.bin" ] \
        && [ ! -e "$SDK/bare-adb.log" ]
}

FEED_DIR="$WORK/feeds"
mkdir -p "$FEED_DIR/phone"
printf '{"version":1,"ui":{"language":"ur-PK-u-nu-arabext","digitZero":"\xdb\xb0"},"days":[{"clock":{"asr":"\xdb\xb1\xdb\xb6:\xdb\xb1\xdb\xb0"}}]}\n' \
    > "$FEED_DIR/phone/ur.json"
echo "Taqwa_Wear_OS_6" > "$SDK/avd-name"
[ -f "$REPO/tools/watchface/store.sh" ] || fail "tools/watchface/store.sh does not exist"

# 1. The file, as given, from a path relative to where store.sh runs.
run_store "$FEED_DIR" phone/ur.json
if [ "$CODE" -eq 0 ]; then pass "stores a feed (exit 0)"; else fail "stores a feed: exit $CODE"; sed 's/^/    /' "$WORK/out.txt"; fi
if [ -f "$SDK/stored.bin" ] && cmp -s "$SDK/stored.bin" "$FEED_DIR/phone/ur.json"; then
    pass "the watch app's files/feed.json is the file, byte for byte, from a relative path"
else
    fail "the bytes written as files/feed.json are not the file's"
fi
if ! grep -q -E 'DEBUG_FEED|--es sample' "$SDK/calls.log"; then
    pass "the debug build's sample is never stored in its place"
else
    fail "store.sh stored the debug build's sample: $(grep -E 'DEBUG_FEED|--es sample' "$SDK/calls.log")"
fi
as_app="$(grep -F 'run-as' "$SDK/calls.log" || true)"
if [ "$as_app" = "$(printf '%s\n%s' "$RM_CALL" "$WRITE_CALL")" ]; then
    pass "as the watch app it only removes the delivery and writes files/feed.json, once each"
else
    fail "its run-as calls are not exactly the removal and the write:"
    printf '%s\n' "$as_app" | sed 's/^/    /'
fi

# 2. The order: the delivery removed, the feed written, the app stopped, the face bound again.
rm_line="$(line_of "$RM_CALL")"
write_line="$(line_of "$WRITE_CALL")"
stop_line="$(line_of "$STOP_CALL")"
rebind_line="$(line_of "$REBIND_CALL")"
if [ -n "$rm_line" ] && [ -n "$write_line" ] && [ -n "$stop_line" ] && [ -n "$rebind_line" ] \
    && [ "$rm_line" -lt "$write_line" ] && [ "$write_line" -lt "$stop_line" ] \
    && [ "$stop_line" -lt "$rebind_line" ]; then
    pass "the delivery is removed (rm -f) before the feed is written, then the app stops and the face binds again (DEBUG_REBIND)"
else
    fail "not in the order rm -f the delivery, write the feed, force-stop, DEBUG_REBIND (lines: rm ${rm_line:-none}, write ${write_line:-none}, stop ${stop_line:-none}, rebind ${rebind_line:-none})"
    calls | sed 's/^/    /'
fi
if ! grep -q -E '(^| )(install|install-multiple|uninstall)( |$)' "$SDK/calls.log"; then
    pass "nothing is installed or removed, the face least of all"
else
    fail "store.sh installed or removed a package: $(grep -E '(^| )(install|install-multiple|uninstall)( |$)' "$SDK/calls.log" | head -1)"
fi

# 3. Only lib.sh's adb, only emulator-5558, and no Gradle.
if [ ! -e "$SDK/bare-adb.log" ]; then
    pass "every adb call is lib.sh's (the SDK's), never a bare adb from PATH"
else
    fail "store.sh ran a bare adb from PATH: $(head -1 "$SDK/bare-adb.log")"
fi
if ! grep -v -x 'devices' "$SDK/calls.log" | grep -v -q '^-s emulator-5558 '; then
    pass "every adb call but 'devices' names emulator-5558"
else
    fail "an adb call does not name emulator-5558: $(grep -v -x 'devices' "$SDK/calls.log" | grep -v '^-s emulator-5558 ' | head -1)"
fi
if ! grep -q 'KB2604110095' "$SDK/calls.log" && ! grep -q '^GRADLE' "$SDK/calls.log"; then
    pass "the phone is never addressed, and Gradle never runs"
else
    fail "the phone was addressed or Gradle ran"
fi

# 4. Nothing touched when anything is missing.
run_store "$FEED_DIR"
if [ "$CODE" -ne 0 ] && touched_nothing; then pass "no file: exit $CODE, nothing touched"; else fail "no file: exit $CODE, or the device was touched"; fi
run_store "$FEED_DIR" phone/missing.json
if [ "$CODE" -ne 0 ] && touched_nothing; then pass "a missing file: exit $CODE, nothing touched"; else fail "a missing file: exit $CODE, or the device was touched"; fi
touch "$SDK/no-emulator"
run_store "$FEED_DIR" phone/ur.json
if [ "$CODE" -ne 0 ] && touched_nothing; then pass "emulator-5558 not running: exit $CODE, nothing touched"; else fail "emulator-5558 not running: exit $CODE, or the device was touched"; fi
rm -f "$SDK/no-emulator"
echo "Pixel_8_Pro" > "$SDK/avd-name"
run_store "$FEED_DIR" phone/ur.json
if [ "$CODE" -ne 0 ] && touched_nothing; then pass "another AVD on 5558: exit $CODE, nothing touched"; else fail "another AVD on 5558: exit $CODE, or the device was touched"; fi
echo "Taqwa_Wear_OS_6" > "$SDK/avd-name"

if [ "$FAILS" -eq 0 ]; then echo "store-test.sh: PASS"; else echo "store-test.sh: $FAILS FAIL"; exit 1; fi
