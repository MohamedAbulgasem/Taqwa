#!/usr/bin/env bash
# Starts the Wear OS emulator the watch work runs on, creating it the first time:
#
#   scripts/wear-emulator.sh            # headless; screenshots with adb exec-out screencap
#   scripts/wear-emulator.sh --window   # the same emulator in a window on this Mac
#
# The AVD is Taqwa_Wear_OS_6: the Wear OS Large Round device with the Wear OS 6.0 image
# (system-images;android-36;android-wear-signed;arm64-v8a, which sdkmanager installs). It always
# runs on console port 5558, so its serial is always emulator-5558, clear of the phone emulators
# (5554, 5556 and 5560 here). Address it by that serial and nothing else (adb -s emulator-5558 ...,
# ANDROID_SERIAL=emulator-5558 for Gradle's install tasks), and stop it the same way, never by a
# process name:
#
#   adb -s emulator-5558 emu kill
#
# The script returns once the watch has booted, and prints its API level (36). Run while the
# emulator is up, it only checks and prints. The emulator's own output goes to
# $TMPDIR/Taqwa_Wear_OS_6.log. tools/watchface/emulator.sh runs this, then takes the watch off the
# charger, whose screen would cover the face.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD=Taqwa_Wear_OS_6
IMAGE="system-images;android-36;android-wear-signed;arm64-v8a"
DEVICE=wearos_large_round
PORT=5558
SERIAL="emulator-$PORT"
BOOT_TIMEOUT_SECONDS=600
LOG="${TMPDIR:-/tmp}/$AVD.log"

ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

WINDOW=false
case "${1:-}" in
    "") ;;
    --window) WINDOW=true ;;
    *) echo "usage: $0 [--window]" >&2; exit 2 ;;
esac

# avdmanager is a Java program: with no JAVA_HOME it runs on the JDK inside Android Studio.
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JDK" ]; then
    export JAVA_HOME="$STUDIO_JDK"
fi

avds="$("$EMULATOR" -list-avds)"
if ! grep -qx "$AVD" <<< "$avds"; then
    if [ ! -d "$SDK/${IMAGE//;//}" ]; then
        echo "The Wear OS 6.0 image is not installed: sdkmanager \"$IMAGE\"" >&2
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
    # session started on 5558 is not ours to use or to stop.
    running="$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null || true)"
    running="$(head -1 <<< "$running" | tr -d '\r')"
    if [ "$running" != "$AVD" ]; then
        echo "$SERIAL is taken by '${running:-an emulator that does not say}', not $AVD;" \
            "leaving it alone." >&2
        exit 1
    fi
    echo "$AVD is already running as $SERIAL"
else
    # -no-snapshot-save: the clocks, sleeps and face choices of a verification run are never
    # saved into the AVD's quick-boot state.
    args=(-avd "$AVD" -port "$PORT" -no-snapshot-save -no-boot-anim)
    if [ "$WINDOW" = false ]; then
        # Software rendering: the mode that draws for screencap with no window on this Mac.
        args+=(-no-window -gpu swiftshader_indirect -no-audio)
    fi
    echo "Starting $AVD as $SERIAL (log: $LOG)"
    nohup "$EMULATOR" "${args[@]}" > "$LOG" 2>&1 &
    started=$!
fi

# adb sees the device long before Android has finished starting; sys.boot_completed is the end.
deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    if [ -n "${started:-}" ] && ! ps -p "$started" > /dev/null; then
        echo "The emulator exited while starting:" >&2
        tail -20 "$LOG" >&2
        exit 1
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "$AVD did not finish booting within $BOOT_TIMEOUT_SECONDS s; see $LOG" >&2
        exit 1
    fi
    sleep 3
done
echo "$AVD booted: API level $("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
