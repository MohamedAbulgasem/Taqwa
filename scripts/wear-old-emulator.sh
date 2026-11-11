#!/usr/bin/env bash
# Starts one of the two older Wear OS emulators plan 5 Task 12 Step 1 runs the watch app on,
# creating it the first time:
#
#   scripts/wear-old-emulator.sh 33            # Wear OS 4: Taqwa_Wear_OS_4 as emulator-5566
#   scripts/wear-old-emulator.sh 30            # Wear OS 3: Taqwa_Wear_OS_3 as emulator-5568
#   scripts/wear-old-emulator.sh 30 --window   # the same emulator in a window on this Mac
#
# Each is the Wear OS Large Round profile (454 x 454, 320 dpi, round), as Taqwa_Wear_OS_6 is, so a
# shot from one compares with a shot from another:
#
#   API 33: system-images;android-33;android-wear;arm64-v8a, port 5566, lock wear-5566
#   API 30: system-images;android-30;android-wear;arm64-v8a, port 5568, lock wear-5568
#
# The images are downloads (the Lead's OK is in the ledger, Wave 0); the script never fetches one,
# and says which sdkmanager line would. Run the two one at a time. Address each by its serial and
# nothing else (adb -s emulator-5566 ...), and stop it the same way, never by a process name:
#
#   adb -s emulator-5566 emu kill
#   adb -s emulator-5568 emu kill
#
# The script returns once the watch has booted, and prints its API level. Run while the emulator is
# up, it only checks, isolates and prints. A port taken by another AVD is left alone. The emulator's
# own output goes to $TMPDIR/<AVD>.log.
#
# These watches are never paired with a phone: Task 12 Step 1 runs the debug build's sample feed.
# But a Wear emulator joins a phone by itself: its Wear services dial 10.0.2.2:5601, which is the
# host's 5601, and while scripts/wear-pair.sh's forward (emulator-5562 tcp:5601) is up that is the
# paired phone's pairing port. Seen on 6 November: Taqwa_Wear_OS_4, booted unpaired, was taken
# into the phone's Wear network (its cloud route too), and the debug app's first handshake got the
# phone's own feed. So, as soon as adbd answers, the script makes adbd root (both images are
# userdebug) and has the watch refuse every new connection that leaves it (iptables and ip6tables:
# a TCP SYN or any UDP off the loopback), before Play services is up to dial. Nothing the step
# runs needs the network: adb reaches the emulator through its own pipe, not its network. The
# rules last until the emulator stops; each start sets them again, and a run while it is up checks
# them. The check after boot fails the script if the watch still reaches a node.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DEVICE=wearos_large_round
BOOT_TIMEOUT_SECONDS=600

case "${1:-}" in
    33) AVD=Taqwa_Wear_OS_4; API=33; PORT=5566 ;;
    30) AVD=Taqwa_Wear_OS_3; API=30; PORT=5568 ;;
    *) echo "usage: $0 <30|33> [--window]" >&2; exit 2 ;;
esac
IMAGE="system-images;android-$API;android-wear;arm64-v8a"
SERIAL="emulator-$PORT"
LOG="${TMPDIR:-/tmp}/$AVD.log"

WINDOW=false
case "${2:-}" in
    "") ;;
    --window) WINDOW=true ;;
    *) echo "usage: $0 <30|33> [--window]" >&2; exit 2 ;;
esac

ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

# avdmanager is a Java program: with no JAVA_HOME it runs on the JDK inside Android Studio.
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JDK" ]; then
    export JAVA_HOME="$STUDIO_JDK"
fi

avds="$("$EMULATOR" -list-avds)"
if ! grep -qx "$AVD" <<< "$avds"; then
    if [ ! -d "$SDK/${IMAGE//;//}" ]; then
        echo "The API $API Wear image is not installed (a download, which needs the Lead's OK):" \
            "sdkmanager \"$IMAGE\"" >&2
        exit 1
    fi
    echo "Creating $AVD ($DEVICE, $IMAGE)"
    # "no" answers avdmanager's offer of a custom hardware profile.
    echo no | "$AVDMANAGER" create avd --name "$AVD" --package "$IMAGE" --device "$DEVICE" \
        > /dev/null
fi

devices="$("$ADB" devices)"
if grep -q "^$SERIAL[[:space:]]" <<< "$devices"; then
    # Something is on the port already. Wait for it only if it is this AVD: an emulator another
    # session started on this port is not ours to use or to stop.
    running="$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null || true)"
    running="$(head -1 <<< "$running" | tr -d '\r')"
    if [ "$running" != "$AVD" ]; then
        echo "$SERIAL is taken by '${running:-an emulator that does not say}', not $AVD;" \
            "leaving it alone." >&2
        exit 1
    fi
    echo "$AVD is already running as $SERIAL"
else
    # -no-snapshot-save: the clocks and choices of a verification run are never saved into the
    # AVD's quick-boot state.
    args=(-avd "$AVD" -port "$PORT" -no-snapshot-save -no-boot-anim)
    if [ "$WINDOW" = false ]; then
        # Software rendering: the mode that draws for screencap with no window on this Mac.
        args+=(-no-window -gpu swiftshader_indirect -no-audio)
    fi
    echo "Starting $AVD as $SERIAL (log: $LOG)"
    nohup "$EMULATOR" "${args[@]}" > "$LOG" 2>&1 &
    started=$!
fi

deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
waiting() {
    if [ -n "${started:-}" ] && ! ps -p "$started" > /dev/null; then
        echo "The emulator exited while starting:" >&2
        tail -20 "$LOG" >&2
        exit 1
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "$AVD did not $1 within $BOOT_TIMEOUT_SECONDS s; see $LOG" >&2
        exit 1
    fi
    sleep "${2:-3}"
}
wsh() { "$ADB" -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

# The rules: a new TCP connection or any UDP that leaves by anything but the loopback is refused,
# in both families. Connections already open may still close, so a phone's end is not left hanging.
RULES=("! -o lo -p tcp --syn -j REJECT --reject-with tcp-reset" "! -o lo -p udp -j REJECT")
isolate() {
    local rule table
    for table in iptables ip6tables; do
        for rule in "${RULES[@]}"; do
            # shellcheck disable=SC2086 # each rule is split into its words on purpose
            if ! wsh "$table" -C OUTPUT $rule > /dev/null; then
                wsh "$table" -I OUTPUT $rule > /dev/null
            fi
        done
    done
}
isolated() {
    local rule table
    for table in iptables ip6tables; do
        for rule in "${RULES[@]}"; do
            # shellcheck disable=SC2086
            wsh "$table" -C OUTPUT $rule > /dev/null || return 1
        done
    done
}

# adbd as root, as soon as it answers, then the rules once netd has laid out its own chains (netd
# lays out OUTPUT afresh when it starts, which would drop rules set before it).
until [ "$(wsh echo up)" = up ]; do waiting "answer adb" 1; done
if [ "$(wsh id -u)" != 0 ]; then
    "$ADB" -s "$SERIAL" root > /dev/null
    until [ "$(wsh id -u)" = 0 ]; do waiting "give adb root" 1; done
fi
until wsh iptables -S OUTPUT | grep -q -- '-j oem_out' && wsh ip6tables -S OUTPUT | grep -q -- '-j oem_out'; do
    waiting "set out its firewall" 0.5
done
isolate

# adb sees the device long before Android has finished starting; sys.boot_completed is the end.
until [ "$(wsh getprop sys.boot_completed)" = 1 ]; do waiting "finish booting"; done

# The rules again (netd may have redone OUTPUT), then the proof: no open link to a pairing port
# (5601, hex 15E1), and Play services reaching no node.
isolate
isolated || { echo "$AVD: the isolation rules did not hold" >&2; exit 1; }
if wsh cat /proc/net/tcp /proc/net/tcp6 | awk '$3 ~ /:15E1$/ && $4 == "01"' | grep -q .; then
    echo "$AVD has an open link to a pairing port (5601): it may have joined a phone" >&2
    exit 1
fi
# perl's alarm bounds a dumpsys that does not return (macOS has no timeout).
nodes="$(perl -e 'alarm 60; exec @ARGV' "$ADB" -s "$SERIAL" shell \
    dumpsys activity service com.google.android.gms/.wearable.service.WearableService 2>/dev/null \
    | tr -d '\r' | sed -n '/Reachable Nodes:/,/####/p' || true)"
if [ -n "$nodes" ] && ! grep -q "no reachable nodes" <<< "$nodes"; then
    echo "$AVD reaches a Wear node, so it is not on its own:" >&2
    echo "$nodes" >&2
    exit 1
fi
echo "$AVD booted: API level $(wsh getprop ro.build.version.sdk), on its own (no network, no phone)"
