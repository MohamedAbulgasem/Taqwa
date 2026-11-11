#!/usr/bin/env bash
# Scratch (P2.2 Step 8's evidence): the base face (ff28cd5a, the APK found installed) and the v2 face,
# installed in turn in one session, each shot at the same moments: old, new, old again.
#   ab.sh <repo> <out dir> <old face apk> <new face apk> <round>...   (old, new, old2: the face by its name)
set -euo pipefail
REPO="$1"; OUT="$2"; OLD="$3"; NEW="$4"
T="$REPO/tools/watchface"
choose() { "$T/choose.sh" "$@" > /dev/null; }
shots() {   # $1: tag of this round
    choose style arch accent amber left empty right empty
    SAMPLE=ar-ly12 "$T/shoot.sh" "$OUT/$1" arch-ar-ly-12h-1458 14:58 | sed 's/^/  /'
    SAMPLE=ar-sa "$T/shoot.sh" "$OUT/$1" arch-ar-sa-1458 14:58 | sed 's/^/  /'
    "$T/shoot.sh" "$OUT/$1" arch-en-1458 14:58 | sed 's/^/  /'
    choose style ring
    SAMPLE=ar-sa "$T/shoot.sh" "$OUT/$1" ring-ar-sa-1458 14:58 | sed 's/^/  /'
    choose style sun
    SAMPLE=ar-ly12 "$T/shoot.sh" "$OUT/$1" sun-ar-ly-12h-1458 14:58 | sed 's/^/  /'
    choose style moon
    SAMPLE=ar-sa "$T/shoot.sh" "$OUT/$1" moon-ar-sa-1010 10:10 | sed 's/^/  /'
    SAMPLE=ar-ly12 "$T/shoot.sh" "$OUT/$1" moon-ar-ly-12h-1458 14:58 | sed 's/^/  /'
    choose style arch
    # Urdu's digits: the Saudi sample with every Arabic-Indic digit written extended, at 14:47
    # (the clock ۱۴:۴۷ in the Light, the Hijri day ۶ and the times in SemiBold and Bold)
    "$T/clock.sh" 14:46 > /dev/null
    SAMPLE=ar-sa FACE_APK="$FACE" "$SCRATCH/feedtext.sh" "$REPO" urdu - 14:47 > /dev/null
    KEEP_FEED=1 "$T/shoot.sh" "$OUT/$1" arch-urdu-digits-1447 14:47 awake | sed 's/^/  /'
    choose style ring
    "$T/clock.sh" 14:46 > /dev/null
    SAMPLE=ar-sa FACE_APK="$FACE" "$SCRATCH/feedtext.sh" "$REPO" urdu - 14:47 > /dev/null
    KEEP_FEED=1 "$T/shoot.sh" "$OUT/$1" ring-urdu-digits-1447 14:47 awake | sed 's/^/  /'
    choose style arch
}
SCRATCH="$(cd "$(dirname "$0")" && pwd)"
install_face() { FACE="$1"; adb -s emulator-5558 install -r "$1" > /dev/null; sleep 3; adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP; }
shift 4
for round in "$@"; do   # old, new, old2 ...
    case "$round" in old*) install_face "$OLD" ;; *) install_face "$NEW" ;; esac
    echo "$round"; shots "$round"
done
install_face "$NEW"; echo "the new face installed again"
