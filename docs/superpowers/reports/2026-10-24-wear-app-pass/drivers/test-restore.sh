#!/usr/bin/env bash
# restore.sh, with every adb call to a stub that records it (ANDROID_HOME and HOME point at a scratch
# SDK), so no device is ever reached: run it with no emulator, or beside one.
#
# 1. Its guard (Ruling 68 item 4): it puts the watch back from found/, so it must stop before it
#    touches the watch when found/ is not what pullfound.sh writes (an unset or mistyped PASS_WORK
#    gives an empty found/, from which it would delete the app's feed.json and its files/).
# 2. The watch put back (Ruling 68 item 4's review round 1): the stub is a watch, its files in a
#    folder, and an app that does at each start what Taqwa's does on a watch paired with a phone
#    running Taqwa: it shakes hands unless refresh.asked is under a minute old, and the phone then
#    replaces a feed held without a delivery; it reads its face (inactive for its first n starts
#    when asked to, as the system answers for a minute after an install) and writes FaceMemory's
#    file; it may write the profile installer's mark. Only a watch whose every file is found's, byte
#    for byte, with no handshake made, is STATE AS FOUND.
#
#   docs/superpowers/reports/2026-10-24-wear-app-pass/drivers/test-restore.sh
set -u
DRIVERS="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-test-restore.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/sdk/platform-tools" "$WORK/home/Library/Android" "$WORK/bin"
ln -s "$WORK/sdk" "$WORK/home/Library/Android/sdk"
printf '#!/bin/sh\nexec shasum "$@"\n' > "$WORK/bin/sha1sum"; chmod +x "$WORK/bin/sha1sum"

# The stub watch: $WORK/watch/data is the app's data folder, $WORK/watch/app.apk its installed APK,
# $WORK/watch/log its log, $WORK/watch/starts one line per start. STUB_INACTIVE (starts that read
# the face inactive), STUB_PROFILE (a start writes profileInstalled) and STUB_STUCK (a file whose
# writes the watch keeps wrong) shape it.
cat > "$WORK/sdk/platform-tools/adb" <<EOF
#!/usr/bin/env bash
echo "\$*" >> "$WORK/adb.log"
W="$WORK/watch"
export PATH="$WORK/bin:\$PATH"
[ "\${1:-}" = -s ] && shift 2
start_app() {
    echo start >> "\$W/starts"
    local n now st
    n=\$(wc -l < "\$W/starts" | tr -d ' ')
    now=\$(date +%s); st=\$(tr -d ' \n' < "\$W/data/files/refresh.asked" 2> /dev/null)
    if [ -n "\$st" ] && [ \$((now - st)) -ge 0 ] && [ \$((now - st)) -lt 60 ]; then
        echo "I System.out: TaqwaWearFeed ASK STARTED skipped" >> "\$W/log"
    elif [ ! -f "\$W/data/files/feed.json.delivery" ]; then
        echo "I System.out: TaqwaWearFeed ASK STARTED sent" >> "\$W/log"
        echo '{"city":"London"}' > "\$W/data/files/feed.json"; echo 41/f00d > "\$W/data/files/feed.json.delivery"
        echo "I System.out: TaqwaWearFeed ACCEPTED 41/f00d" >> "\$W/log"
        echo "\$now" > "\$W/data/files/refresh.asked"
    fi
    if [ "\$n" -le "\${STUB_INACTIVE:-0}" ]; then
        printf 'seen 41 inactive\n' > "\$W/data/files/face-activation-spent"; echo "I TaqwaFace: TaqwaFace capability added INSTALLED" >> "\$W/log"
    else
        printf 'seen 41 active\n' > "\$W/data/files/face-activation-spent"; echo "I TaqwaFace: TaqwaFace capability removed ACTIVE" >> "\$W/log"
    fi
    [ -z "\${STUB_PROFILE:-}" ] || echo "profile of start \$n" > "\$W/data/files/profileInstalled"
}
case "\${1:-}" in
    devices) printf 'List of devices attached\nemulator-5558\tdevice\n' ;;
    emu) [ "\${2:-} \${3:-}" = "avd name" ] && echo Taqwa_Wear_OS_6 ;;
    install) cp "\${@: -1}" "\$W/app.apk"; echo Success; start_app ;;
    logcat) cat "\$W/log" 2> /dev/null ;;
    pull) cp "\$2" "\$3" 2> /dev/null ;;
    shell|exec-out)
        shift; cmd="\$*"
        case "\$cmd" in
            "run-as world.taqwa.app "*)
                cmd="\${cmd#run-as world.taqwa.app }"
                if [ -n "\${STUB_STUCK:-}" ] && [ "\$cmd" = "sh -c 'cat > \$STUB_STUCK'" ]; then cat > /dev/null; echo stuck > "\$W/data/\$STUB_STUCK"; exit 0; fi
                cd "\$W/data" && eval "\$cmd" ;;
            "pm path world.taqwa.app") echo "package:\$W/app.apk" ;;
            "sha1sum \$W/app.apk") shasum "\$W/app.apk" ;;
            "date +%s") date +%s ;;
            "am force-stop world.taqwa.app") ;;
            *DEBUG_REBIND*) start_app; echo "Broadcast completed: result=-1, data=\"REBOUND\"" ;;
        esac ;;
esac
exit 0
EOF
chmod +x "$WORK/sdk/platform-tools/adb"

failures=0
fail() { echo "FAIL $1: $2"; failures=$((failures + 1)); }
# Runs restore.sh on the pass folder $1, the stub watch's settings (VAR=value...) after it.
restore() {
    local pass=$1; shift
    rm -f "$WORK/adb.log"
    env ANDROID_HOME="$WORK/sdk" HOME="$WORK/home" PASS_WORK="$pass" RESTORE_FAST=1 "$@" "$DRIVERS/restore.sh" > "$WORK/out" 2> "$WORK/err" < /dev/null
}
calls() { [ -s "$WORK/adb.log" ] && wc -l < "$WORK/adb.log" | tr -d ' ' || echo 0; }

# A found/ as pullfound.sh writes it: the record, both APKs, each folder's list and its files.
complete() {
    local f=$1/found
    mkdir -p "$f/files" "$f/shared_prefs"
    echo "app: 2b2d77c9" > "$f/state.txt"
    echo apk > "$f/app.apk"; echo apk > "$f/face.apk"
    printf 'feed.json\nnudge.anchor\nprofileInstalled\n' > "$f/files.list"
    echo '{}' > "$f/files/feed.json"; echo 1 > "$f/files/nudge.anchor"; : > "$f/files/profileInstalled"
    : > "$f/shared_prefs.list"
}

# guard <name> <setup>: restore.sh on a found/ the setup leaves stops before the watch.
guard() {
    local name=$1 pass="$WORK/$1"; shift
    mkdir -p "$pass"; "$@" "$pass"
    restore "$pass"
    local code=$?
    if [ "$code" -ne 0 ] && [ "$(calls)" -eq 0 ] && grep -q "found/ is not complete" "$WORK/err"; then
        echo "PASS $name: stops before the watch ($(head -1 "$WORK/err"))"
    else
        fail "$name" "exit $code, $(calls) adb calls, $(head -1 "$WORK/err")"
    fi
}

nothing() { :; }
no_found() { rmdir "$1" 2> /dev/null; mkdir -p "$1"; }
no_state() { complete "$1"; rm "$1/found/state.txt"; }
empty_state() { complete "$1"; : > "$1/found/state.txt"; }
no_app() { complete "$1"; rm "$1/found/app.apk"; }
no_files_list() { complete "$1"; rm "$1/found/files.list"; }
no_prefs_list() { complete "$1"; rm "$1/found/shared_prefs.list"; }
listed_missing() { complete "$1"; rm "$1/found/files/feed.json"; }

guard empty-found nothing
guard no-found-folder no_found
guard no-state no_state
guard empty-state empty_state
guard no-app no_app
guard no-files-list no_files_list
guard no-shared-prefs-list no_prefs_list
guard a-listed-file-missing listed_missing

# The watch as the found turn left it, recorded as pullfound.sh records it: Taqwa's sample (no
# delivery), its stamp of long ago, its face seen active, the profile installer's mark; then the
# turn's own build and files on it (a hand-made feed, the Tile's stamp, no stamp of its own).
found_watch() {
    local pass=$1 w="$WORK/watch"
    rm -rf "$w"; mkdir -p "$w/data/files" "$pass"
    echo apk > "$w/app.apk"
    echo '{"city":"Cape Town"}' > "$w/data/files/feed.json"
    echo 1793800000 > "$w/data/files/refresh.asked"
    echo 1793800001 > "$w/data/files/nudge.anchor"
    printf 'seen 41 active\n' > "$w/data/files/face-activation-spent"
    printf 'mark of the found install' > "$w/data/files/profileInstalled"
    ANDROID_HOME="$WORK/sdk" HOME="$WORK/home" PASS_WORK="$pass" "$DRIVERS/pullfound.sh" > /dev/null 2>&1 < /dev/null
    echo "debug build of the turn" > "$w/app.apk"
    echo '{"city":"Paris"}' > "$w/data/files/feed.json"
    echo 1 > "$w/data/files/complications.asked"
    rm -f "$w/data/files/refresh.asked" "$w/starts" "$w/log"
}
# Each file of the stub watch's files/ against found/'s, by its bytes.
same_files() {
    local pass=$1 f
    [ "$(ls -A "$WORK/watch/data/files" | sort)" = "$(sort "$pass/found/files.list")" ] || return 1
    for f in $(cat "$pass/found/files.list"); do cmp -s "$WORK/watch/data/files/$f" "$pass/found/files/$f" || return 1; done
}
starts() { [ -f "$WORK/watch/starts" ] && wc -l < "$WORK/watch/starts" | tr -d ' ' || echo 0; }

# put <name> <expect: found|not> <check> [VAR=value...]: restore.sh on the found watch after a turn.
put() {
    local name=$1 expect=$2 check=$3 pass="$WORK/$1"; shift 3
    found_watch "$pass"
    [ "$name" != the-found-app-installed ] || found_app
    restore "$pass" "$@"
    local code=$?
    local said; said=$(grep -E "STATE (NOT )?AS FOUND" "$WORK/out" | tail -1)
    if [ "$expect" = found ]; then
        if [ "$code" -eq 0 ] && [ "$said" = "STATE AS FOUND" ] && same_files "$pass" && "$check" "$pass"; then
            echo "PASS $name: STATE AS FOUND, every file found's, $(starts) start(s), $(grep -c 'ASK STARTED skipped' "$WORK/watch/log") handshake(s) skipped"
        else
            fail "$name" "exit $code, '$said', files $(same_files "$pass" && echo same || echo differ), $(starts) starts: $(grep -v '^the app' "$WORK/out" | head -3 | xargs) $(head -1 "$WORK/err")"
        fi
    else
        if [ "$code" -ne 0 ] && [ "$said" = "STATE NOT AS FOUND" ] && "$check" "$pass"; then
            echo "PASS $name: STATE NOT AS FOUND, exit 1 ($(grep -E '^not as found|restore.sh: after' "$WORK/out" "$WORK/err" | head -1 | cut -d: -f2- | cut -c1-110))"
        else
            fail "$name" "exit $code, '$said': $(tail -3 "$WORK/out" | xargs)"
        fi
    fi
}
no_handshake() { ! grep -q "ASK STARTED sent" "$WORK/watch/log"; }
installed() { grep -q " install -r -d " "$WORK/adb.log" && no_handshake; }
not_installed() { ! grep -q " install " "$WORK/adb.log" && no_handshake && grep -q "not installed again" "$WORK/out"; }
started_until_active() { [ "$(starts)" -ge 4 ] && no_handshake && grep -q "capability removed ACTIVE" "$WORK/out"; }
names_the_file() { grep -q "^not as found: files/profileInstalled: " "$WORK/out"; }
names_the_face() { grep -q "still reads its face as seen 41 inactive" "$WORK/err"; }
found_app() { echo apk > "$WORK/watch/app.apk"; }

# The found app over the turn's build: an install, whose start and the next are stamped, so the
# phone is never asked; the starts' writes undone.
put the-found-app-over-a-turns-build found installed STUB_PROFILE=1
# The face read inactive by the first three starts (the install's and two more): started until it
# reads as found, then every file found's.
put a-face-the-system-has-not-settled found started_until_active STUB_INACTIVE=3 STUB_PROFILE=1
# The found app installed already (the turn changed only settings and files): not installed again.
put the-found-app-installed found not_installed STUB_PROFILE=1
# A file the watch keeps wrong, one state.sh only names: the names agree, its bytes do not, and the
# check says which.
put a-file-kept-wrong not names_the_file STUB_STUCK=files/profileInstalled
# A face that never reads as found: given up after its starts, and not as found, though the found
# files are written back over what the app wrote.
put a-face-that-never-settles not names_the_face STUB_INACTIVE=99

[ "$failures" -eq 0 ] && echo "test-restore: PASS" || { echo "test-restore: $failures FAILED"; exit 1; }
