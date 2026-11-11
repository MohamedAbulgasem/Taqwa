#!/usr/bin/env bash
# Starts the phone emulator the watch link is tried on (plan 2 Task 7), creating it the first time:
#
#   scripts/phone-emulator.sh                    # headless; screenshots with adb exec-out screencap
#   scripts/phone-emulator.sh --window           # the same emulator in a window on this Mac
#   scripts/phone-emulator.sh --noplay [--window]  # the phone without Google Play services
#
# The AVD is Taqwa_Phone_Wear: the Pixel 8 with the Android 16 Google Play image
# (system-images;android-36;google_apis_playstore;arm64-v8a). It always runs on console port 5562,
# so its serial is always emulator-5562, clear of the Wear emulator (5558) and the other phone
# emulators (5554, 5556, 5560, 5572). It is the phone paired with Taqwa_Wear_OS_6: the one sign-in
# in its Play Store, the Pixel Watch app and Android Studio's Pair Wearable are Mohamed's, done in
# its window, and agents never sign in to anything.
#
# --noplay makes Taqwa_Phone_NoPlay instead, on the image without Google Play services
# (system-images;android-36;default;arm64-v8a), on port 5564 (emulator-5564). That image is not
# downloaded here: when it is not installed, the script says so and stops.
#
# Address it by its serial and nothing else (adb -s emulator-5562 ...), and stop it the same way,
# never by a process name:
#
#   adb -s emulator-5562 emu kill
#
# The script returns once the phone has booted, and prints its API level (36). Run while the
# emulator is up, it only checks and prints. The emulator's own output goes to $TMPDIR/<AVD>.log.
# It follows scripts/wear-emulator.sh line for line, and also corrects the config.ini a new AVD
# gets from avdmanager (below). scripts/test-pairing-scripts.sh is its test, on a fake SDK.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD=Taqwa_Phone_Wear
IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
DEVICE=pixel_8
PORT=5562
BOOT_TIMEOUT_SECONDS=600

ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

PLAY_STORE=true

WINDOW=false
for arg in "$@"; do
    case "$arg" in
        --window) WINDOW=true ;;
        --noplay)
            AVD=Taqwa_Phone_NoPlay
            IMAGE="system-images;android-36;default;arm64-v8a"
            PORT=5564
            PLAY_STORE=false
            ;;
        *) echo "usage: $0 [--window] [--noplay]" >&2; exit 2 ;;
    esac
done
SERIAL="emulator-$PORT"
LOG="${TMPDIR:-/tmp}/$AVD.log"

# avdmanager is a Java program: with no JAVA_HOME it runs on the JDK inside Android Studio.
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JDK" ]; then
    export JAVA_HOME="$STUDIO_JDK"
fi

avds="$("$EMULATOR" -list-avds)"
if ! grep -qx "$AVD" <<< "$avds"; then
    if [ ! -d "$SDK/${IMAGE//;//}" ]; then
        echo "The image $AVD needs is not installed: sdkmanager \"$IMAGE\"" >&2
        exit 1
    fi
    echo "Creating $AVD ($DEVICE, $IMAGE)"
    # "no" answers avdmanager's offer of a custom hardware profile.
    echo no | "$AVDMANAGER" create avd --name "$AVD" --package "$IMAGE" --device "$DEVICE" \
        > /dev/null
    # cmdline-tools 23's avdmanager writes every hardware default into config.ini, placeholders
    # (avd.id=<build>, avd.name=<build>, disk.dataPartition.path=<temp>) among them, and leaves the
    # GPU, the keyboard and the Play Store off whatever the image: the Play Store has to be on for
    # the sign-in and Studio's Pair Wearable, and a keyboard for typing in the window. The data
    # partition is 6 GB, as the Wear AVD's: the pixel_8 profile's 10 GB needs 12 GB free before
    # the emulator starts, more than this Mac's disk had on 31 October.
    config="${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}/$AVD.avd/config.ini"
    grep -vE '=<(build|temp)>$|^(PlayStore\.enabled|hw\.gpu\.enabled|hw\.gpu\.mode|hw\.keyboard|disk\.dataPartition\.size)=' \
        "$config" > "$config.new" || true
    printf 'PlayStore.enabled=%s\nhw.gpu.enabled=yes\nhw.gpu.mode=auto\nhw.keyboard=yes\ndisk.dataPartition.size=6G\n' \
        "$PLAY_STORE" >> "$config.new"
    mv "$config.new" "$config"
fi

devices="$("$ADB" devices)"
if grep -q "^$SERIAL[[:space:]]" <<< "$devices"; then
    # Something is on the port already. Wait for it only if it is this AVD: an emulator another
    # session started on the port is not ours to use or to stop.
    running="$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null || true)"
    running="$(head -1 <<< "$running" | tr -d '\r')"
    if [ "$running" != "$AVD" ]; then
        echo "$SERIAL is taken by '${running:-an emulator that does not say}', not $AVD;" \
            "leaving it alone." >&2
        exit 1
    fi
    echo "$AVD is already running as $SERIAL"
else
    # -no-snapshot-save: the clocks, languages and settings of a verification run are never saved
    # into the AVD's quick-boot state.
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
