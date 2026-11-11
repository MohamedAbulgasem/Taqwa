#!/usr/bin/env bash
# clock.sh's tests, with no emulator and no device: clock.sh and lib.sh are copied into a scratch
# repository whose SDK holds a fake adb, which records every call and plays the watches it is told
# to: which serials are up as which AVD, each one's API level, its settings and the file clock.sh
# keeps on it. clock.sh runs with a scratch HOME and, first on PATH, an adb that only records that
# it ran and fails, so a bare `adb` never reaches the real one beside the phone plugged in. They
# hold that clock.sh
#   - on Wear OS 6 (emulator-5558, the default) makes the calls it always made, automatic time off,
#     then the clock, and `auto` automatic time on again, and one more, a read of the watch's API
#     level (getprop ro.build.version.sdk), which tells the watches apart; it never reads or writes
#     clockwork_auto_time;
#   - on Wear OS 3 and 4 (WATCH=emulator-5568, Taqwa_Wear_OS_3, API 30; WATCH=emulator-5566,
#     Taqwa_Wear_OS_4, API 33) also turns off the watch's own time sync, clockwork_auto_time 2,
#     before it sets the clock, having recorded the two values it found on the watch
#     (clockwork_auto_time and auto_time); a second set keeps the first record, so 2 and 0 are never
#     taken for what was found; `auto` puts both back as found (deleting a setting that was not
#     there), sets the clock to the host's, and forgets the record; with nothing recorded, `auto`
#     turns automatic time on (auto_time 1) and leaves clockwork_auto_time as it is;
#   - drives only emulator-5558, emulator-5566 or emulator-5568, each as its own AVD, names that
#     serial in every adb call but `devices`, and touches nothing on another WATCH or another AVD.
#
#   tools/watchface/clock-test.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(cd "$(mktemp -d "${TMPDIR:-/tmp}/taqwa-clock-test.XXXXXX")" && pwd)"
trap 'rm -rf "$WORK"' EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

REPO="$WORK/repo"
SDK="$WORK/sdk"
mkdir -p "$REPO/tools/watchface" "$SDK/platform-tools" "$WORK/home" "$WORK/path"
cp "$HERE/lib.sh" "$HERE/clock.sh" "$REPO/tools/watchface/"
RECORD=/data/local/tmp/taqwa-clockwork-auto-time

# The fake adb. A watch is a directory $SDK/watch/<serial> holding avd (its name), sdk (its API
# level), settings/<name> (a global setting; absent is "null") and files/<path> (a file on it).
cat > "$SDK/platform-tools/adb" <<'EOF'
#!/usr/bin/env bash
F="$(cd "$(dirname "$0")/.." && pwd)"
echo "$*" >> "$F/calls.log"
trap '' PIPE
if [ "$*" = devices ]; then
    answer='List of devices attached\nKB2604110095\tdevice\n'
    for w in "$F"/watch/*; do [ -d "$w" ] && answer="$answer$(basename "$w")\\tdevice\\n"; done
    printf '%b\n' "$answer" 2> /dev/null || true
    exit 0
fi
[ "$1" = -s ] || exit 1
W="$F/watch/$2"
[ -d "$W" ] || { echo "error: device '$2' not found" >&2; exit 1; }
shift 2
cmd="$*"
case "$cmd" in
    "emu avd name") printf '%s\nOK\n' "$(cat "$W/avd")" 2> /dev/null || true ;;
    "shell getprop ro.build.version.sdk") cat "$W/sdk" ;;
    "shell settings get global "*)
        name="${cmd#shell settings get global }"
        if [ -f "$W/settings/$name" ]; then cat "$W/settings/$name"; else echo null; fi ;;
    "shell settings put global "*)
        rest="${cmd#shell settings put global }"
        mkdir -p "$W/settings"; echo "${rest#* }" > "$W/settings/${rest%% *}" ;;
    "shell settings delete global "*) rm -f "$W/settings/${cmd#shell settings delete global }" ;;
    "shell cat "*)
        path="${cmd#shell cat }"
        if [ -f "$W/files$path" ]; then cat "$W/files$path"; else echo "cat: $path: No such file or directory" >&2; exit 1; fi ;;
    "shell echo "*" > "*)
        value="${cmd#shell echo }"; path="${value#* > }"; value="${value%% > *}"
        mkdir -p "$(dirname "$W/files$path")"; echo "$value" > "$W/files$path" ;;
    "shell rm -f "*) rm -f "$W/files${cmd#shell rm -f }" ;;
    "shell date") echo "Sun Oct 18 14:58:00 SAST 2026" ;;
esac
exit 0
EOF
chmod +x "$SDK/platform-tools/adb"
cat > "$WORK/path/adb" <<EOF
#!/usr/bin/env bash
echo "\$*" >> "$SDK/bare-adb.log"
exit 1
EOF
chmod +x "$WORK/path/adb"

# A watch on serial $1, as AVD $2, at API level $3, with clockwork_auto_time $4 and auto_time $5
# (each "none" for absent; auto_time 1 when not given).
watch() {
    local w="$SDK/watch/$1"
    rm -rf "$w"; mkdir -p "$w/settings" "$w/files"
    echo "$2" > "$w/avd"; echo "$3" > "$w/sdk"
    if [ "$4" != none ]; then echo "$4" > "$w/settings/clockwork_auto_time"; fi
    if [ "${5:-1}" != none ]; then echo "${5:-1}" > "$w/settings/auto_time"; fi
}
setting() { local f="$SDK/watch/$1/settings/$2"; if [ -f "$f" ]; then cat "$f"; else echo null; fi; }
recorded() { local f="$SDK/watch/$1/files$RECORD"; if [ -f "$f" ]; then cat "$f"; else echo none; fi; }

# Runs clock.sh with WATCH=$1 ("" for none) and the remaining arguments; its exit code is in $CODE.
run_clock() {
    local watch_serial="$1"; shift
    rm -f "$SDK/calls.log" "$SDK/bare-adb.log"; touch "$SDK/calls.log"
    CODE=0
    if [ -n "$watch_serial" ]; then
        (cd "$WORK" && WATCH="$watch_serial" PATH="$WORK/path:$PATH" HOME="$WORK/home" ANDROID_HOME="$SDK" \
            bash "$REPO/tools/watchface/clock.sh" "$@") > "$WORK/out.txt" 2>&1 || CODE=$?
    else
        (cd "$WORK" && PATH="$WORK/path:$PATH" HOME="$WORK/home" ANDROID_HOME="$SDK" \
            bash "$REPO/tools/watchface/clock.sh" "$@") > "$WORK/out.txt" 2>&1 || CODE=$?
    fi
}
line_of() { { grep -n -x -F -- "$1" "$SDK/calls.log" || true; } | head -1 | cut -d: -f1; }
only_serial() {   # every adb call but `devices` names serial $1
    ! grep -v -x 'devices' "$SDK/calls.log" | grep -v -q "^-s $1 "
}
calls_made() {    # the distinct adb calls, the clock's milliseconds as <ms>, sorted and joined by |
    sed -E 's/set-time [0-9]+000$/set-time <ms>/' "$SDK/calls.log" | LC_ALL=C sort -u | tr '\n' '|'
}

# 1. Wear OS 6, the default watch: the calls clock.sh always made, and no clockwork_auto_time.
watch emulator-5558 Taqwa_Wear_OS_6 36 0
run_clock "" 14:58
AT_1458="$(TZ=Africa/Johannesburg python3 -I -c 'import datetime,zoneinfo;print(int(datetime.datetime(2026,10,18,14,58,tzinfo=zoneinfo.ZoneInfo("Africa/Johannesburg")).timestamp()))')"
if [ "$CODE" -eq 0 ] && [ -n "$(line_of "-s emulator-5558 shell settings put global auto_time 0")" ] \
    && [ -n "$(line_of "-s emulator-5558 shell cmd alarm set-time ${AT_1458}000")" ]; then
    pass "Wear OS 6: automatic time off and the clock set to 14:58 on the mock day, as before"
else
    fail "Wear OS 6: exit $CODE, or not automatic time off and the clock set"; sed 's/^/    /' "$WORK/out.txt" "$SDK/calls.log"
fi
if ! grep -q clockwork "$SDK/calls.log" && [ "$(recorded emulator-5558)" = none ]; then
    pass "Wear OS 6: clockwork_auto_time is never read or written, and nothing is recorded"
else
    fail "Wear OS 6: clock.sh touched clockwork_auto_time: $(grep clockwork "$SDK/calls.log" | head -1)"
fi
OLD_CALLS='-s emulator-5558 emu avd name|-s emulator-5558 shell cmd alarm set-time <ms>|-s emulator-5558 shell date|'
SDK_READ='-s emulator-5558 shell getprop ro.build.version.sdk|'
if [ "$(calls_made)" = "$OLD_CALLS$SDK_READ-s emulator-5558 shell settings put global auto_time 0|devices|" ]; then
    pass "Wear OS 6: the calls it always made, and one more, the read of its API level"
else
    fail "Wear OS 6: the calls were $(calls_made)"
fi
run_clock "" auto
if [ "$CODE" -eq 0 ] && [ "$(setting emulator-5558 auto_time)" = 1 ] && ! grep -q clockwork "$SDK/calls.log"; then
    pass "Wear OS 6: auto turns automatic time on again, and leaves clockwork_auto_time alone"
else
    fail "Wear OS 6: auto: exit $CODE, auto_time $(setting emulator-5558 auto_time)"
fi
if [ "$(calls_made)" = "$OLD_CALLS$SDK_READ-s emulator-5558 shell settings put global auto_time 1|devices|" ]; then
    pass "Wear OS 6: auto makes the calls it always made, and the read of its API level"
else
    fail "Wear OS 6: auto's calls were $(calls_made)"
fi
if only_serial emulator-5558 && [ ! -e "$SDK/bare-adb.log" ] && ! grep -q KB2604110095 "$SDK/calls.log"; then
    pass "Wear OS 6: every call names emulator-5558, none is a bare adb, and the phone is never addressed"
else
    fail "Wear OS 6: a call names another device or is a bare adb"
fi

# 2. Wear OS 3: its own time sync off too, the values it found recorded first. As the emulators
# boot, automatic time (auto_time) is off there, and clockwork_auto_time 0 keeps the time.
watch emulator-5568 Taqwa_Wear_OS_3 30 0 0
run_clock emulator-5568 19:02:30
off_line="$(line_of "-s emulator-5568 shell settings put global clockwork_auto_time 2")"
set_line="$({ grep -n -E '^-s emulator-5568 shell cmd alarm set-time [0-9]+000$' "$SDK/calls.log" || true; } | head -1 | cut -d: -f1)"
if [ "$CODE" -eq 0 ] && [ "$(setting emulator-5568 clockwork_auto_time)" = 2 ] && [ "$(setting emulator-5568 auto_time)" = 0 ] \
    && [ -n "$off_line" ] && [ -n "$set_line" ] && [ "$off_line" -lt "$set_line" ]; then
    pass "Wear OS 3: clockwork_auto_time 2 and automatic time off, before the clock is set"
else
    fail "Wear OS 3: exit $CODE, clockwork_auto_time $(setting emulator-5568 clockwork_auto_time), auto_time $(setting emulator-5568 auto_time), lines off ${off_line:-none} set ${set_line:-none}"
    sed 's/^/    /' "$WORK/out.txt" "$SDK/calls.log"
fi
if [ "$(recorded emulator-5568)" = "0 0" ]; then
    pass "Wear OS 3: the values found, clockwork_auto_time 0 and auto_time 0, are recorded on the watch"
else
    fail "Wear OS 3: recorded '$(recorded emulator-5568)', not the '0 0' found"
fi
run_clock emulator-5568 19:09:11
if [ "$CODE" -eq 0 ] && [ "$(recorded emulator-5568)" = "0 0" ]; then
    pass "Wear OS 3: a second set keeps the first record, never the 2 and 0 it put"
else
    fail "Wear OS 3: after a second set the record is '$(recorded emulator-5568)'"
fi
run_clock emulator-5568 auto
if [ "$CODE" -eq 0 ] && [ "$(setting emulator-5568 clockwork_auto_time)" = 0 ] && [ "$(setting emulator-5568 auto_time)" = 0 ] \
    && [ "$(recorded emulator-5568)" = none ] && grep -q -E '^-s emulator-5568 shell cmd alarm set-time [0-9]+000$' "$SDK/calls.log"; then
    pass "Wear OS 3: auto puts clockwork_auto_time 0 and auto_time 0 back as found, sets the host's time, and forgets the record"
else
    fail "Wear OS 3: auto: exit $CODE, clockwork_auto_time $(setting emulator-5568 clockwork_auto_time), auto_time $(setting emulator-5568 auto_time), record $(recorded emulator-5568)"
    sed 's/^/    /' "$WORK/out.txt" "$SDK/calls.log"
fi
if only_serial emulator-5568 && [ ! -e "$SDK/bare-adb.log" ] && ! grep -q -E 'KB2604110095|emulator-5558' "$SDK/calls.log"; then
    pass "Wear OS 3: every call names emulator-5568, never emulator-5558 or the phone"
else
    fail "Wear OS 3: a call names another device"
fi
echo 1 > "$SDK/watch/emulator-5568/settings/clockwork_auto_time"
run_clock emulator-5568 auto
if [ "$CODE" -eq 0 ] && [ "$(setting emulator-5568 clockwork_auto_time)" = 1 ]; then
    pass "Wear OS 3: with nothing recorded, auto leaves clockwork_auto_time as it is"
else
    fail "Wear OS 3: with nothing recorded, auto made clockwork_auto_time $(setting emulator-5568 clockwork_auto_time)"
fi
if [ "$(setting emulator-5568 auto_time)" = 1 ]; then
    pass "Wear OS 3: with nothing recorded, auto falls back to automatic time on, auto_time 1, as on Wear OS 6"
else
    fail "Wear OS 3: with nothing recorded, auto left auto_time $(setting emulator-5568 auto_time)"
fi

# 3. A setting that was not there is not there afterwards, and one found on is on again.
watch emulator-5568 Taqwa_Wear_OS_3 30 none 1
run_clock emulator-5568 19:02:30
run_clock emulator-5568 auto
if [ "$CODE" -eq 0 ] && [ ! -f "$SDK/watch/emulator-5568/settings/clockwork_auto_time" ] \
    && [ -n "$(line_of "-s emulator-5568 shell settings delete global clockwork_auto_time")" ]; then
    pass "Wear OS 3: a clockwork_auto_time that was not set is deleted again by auto"
else
    fail "Wear OS 3: the absent setting is now '$(setting emulator-5568 clockwork_auto_time)'"
fi
if [ "$(setting emulator-5568 auto_time)" = 1 ]; then
    pass "Wear OS 3: an auto_time found at 1 is 1 again after auto"
else
    fail "Wear OS 3: auto_time found at 1 is now $(setting emulator-5568 auto_time)"
fi

# 4. Wear OS 4 (emulator-5566): its own AVD, and the same time sync (seen moving a set clock too).
watch emulator-5566 Taqwa_Wear_OS_4 33 0 0
run_clock emulator-5566 14:58
if [ "$CODE" -eq 0 ] && [ -n "$(line_of "-s emulator-5566 shell cmd alarm set-time ${AT_1458}000")" ] && only_serial emulator-5566; then
    pass "Wear OS 4: WATCH=emulator-5566 sets its clock, naming it alone"
else
    fail "Wear OS 4: exit $CODE"; sed 's/^/    /' "$WORK/out.txt"
fi
if [ "$(setting emulator-5566 clockwork_auto_time)" = 2 ] && [ "$(recorded emulator-5566)" = "0 0" ]; then
    pass "Wear OS 4: clockwork_auto_time 2, the values found recorded"
else
    fail "Wear OS 4: clockwork_auto_time $(setting emulator-5566 clockwork_auto_time), record '$(recorded emulator-5566)'"
fi
run_clock emulator-5566 auto
if [ "$CODE" -eq 0 ] && [ "$(setting emulator-5566 clockwork_auto_time)" = 0 ] && [ "$(setting emulator-5566 auto_time)" = 0 ] \
    && [ "$(recorded emulator-5566)" = none ]; then
    pass "Wear OS 4: auto puts both back as found"
else
    fail "Wear OS 4: auto: clockwork_auto_time $(setting emulator-5566 clockwork_auto_time), auto_time $(setting emulator-5566 auto_time), record '$(recorded emulator-5566)'"
fi

# 5. No other watch, and no other AVD on the port.
for other in emulator-5562 emulator-5572 KB2604110095; do
    run_clock "$other" 14:58
    if [ "$CODE" -ne 0 ] && ! grep -q -E -- "-s $other |set-time|settings put" "$SDK/calls.log"; then
        pass "WATCH=$other is refused (exit $CODE) and nothing is set"
    else
        fail "WATCH=$other: exit $CODE, or a call reached it"
    fi
done
echo Taqwa_Wear_OS_6 > "$SDK/watch/emulator-5568/avd"
run_clock emulator-5568 14:58
if [ "$CODE" -ne 0 ] && ! grep -q -E 'set-time|settings put' "$SDK/calls.log"; then
    pass "another AVD on 5568: refused (exit $CODE), nothing set"
else
    fail "another AVD on 5568: exit $CODE, or something was set"
fi

if [ "$FAILS" -eq 0 ]; then echo "clock-test.sh: PASS"; else echo "clock-test.sh: $FAILS FAIL"; exit 1; fi
