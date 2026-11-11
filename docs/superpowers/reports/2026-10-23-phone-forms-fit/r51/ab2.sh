#!/usr/bin/env bash
# Scratch (P2.2 close, Ruling 51 item 1): in one session on emulator-5558, the face found installed
# (ff28cd5a's, base) and the new face (Arch's right-to-left Gregorian steps), installed in turn and
# shot at the same moments on the committed samples; then, on the new face, Arch's Arabic Gregorian
# line rewritten to each case (feedtext.sh), awake and always-on.
#   ab2.sh <repo> <out dir> <old face apk> <new face apk>
set -euo pipefail
REPO="$1"; OUT="$2"; OLD="$3"; NEW="$4"
T="$REPO/tools/watchface"
SCRATCH="$(cd "$(dirname "$0")" && pwd)"
choose() { "$T/choose.sh" "$@" > /dev/null; }
install_face() { FACE="$1"; adb -s emulator-5558 install -r "$1" > /dev/null; sleep 3; adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP; }
samples() {   # $1: this round's folder
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
}
cases() {   # $1: folder; Arch, the Saudi sample at 14:58 with every day's Gregorian line rewritten
    choose style arch accent amber left empty right empty
    local names=(sample sat-aug-1 thu-aug-10 tue-oct-10 thu-oct-10 thu-nov-30)
    local texts=("الأحد ١٨ أكتوبر" "السبت ١ أغسطس" "الخميس ١٠ أغسطس" "الثلاثاء ١٠ تشرين الأول" "الخميس 10 تشرين الأول" "الخميس ٣٠ تشرين الثاني")
    local i
    for i in "${!names[@]}"; do
        [ -n "${ONLY:-}" ] && [ "${ONLY}" != "${names[$i]}" ] && [ "${FROM:-}" = "" ] && continue
        "$T/clock.sh" 14:57 > /dev/null < /dev/null
        SAMPLE=ar-sa FACE_APK="$FACE" "$SCRATCH/../feedtext.sh" "$REPO" greg "${texts[$i]}" 14:58 > /dev/null < /dev/null
        KEEP_FEED=1 "$T/shoot.sh" "$OUT/$1" "greg-${names[$i]}" 14:58 < /dev/null | sed "s/^/  ${texts[$i]}: /"
    done
}
if [ "${CASES_ONLY:-0}" = 1 ]; then FACE="$NEW"; echo cases; cases new; echo done; exit 0; fi
install_face "$OLD"; echo old; samples old
install_face "$NEW"; echo new; samples new
echo cases; cases new
echo done
