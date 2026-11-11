#!/usr/bin/env bash
# Scratch (P2.2, Ruling 51a): on emulator-5558, in one session, (1) the face as committed (Arch's
# right-to-left Gregorian line by month rule A) with Arch's Arabic Gregorian line rewritten to each of
# r51's six cases (feedtext.sh), awake and always-on, on the Saudi sample at 14:58, as r51/ab2.sh shot
# them; then (2) at the pass's battery (82, as tools/watchface/pass.sh sets it) the committed pass's
# four Arch Arabic frames and Arch's Saudi and English samples, on the face as committed and on
# ff28cd5a's face (built from ff28cd5a's tree), installed in turn.
#   ab3.sh <repo> <out dir> <new face apk> <ff28cd5a face apk>
set -euo pipefail
REPO="$1"; OUT="$2"; NEW="$3"; BASE="$4"
T="$REPO/tools/watchface"
EV="$REPO/docs/superpowers/reports/2026-10-23-phone-forms-fit"
choose() { "$T/choose.sh" "$@" > /dev/null; }
install_face() { FACE="$1"; adb -s emulator-5558 install -r "$1" > /dev/null; sleep 3; adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP; }
shot() { "$T/shoot.sh" "$@" < /dev/null | sed 's/^/  /'; }
arch_frames() {   # $1: folder; the committed pass's four Arch Arabic frames, and Arch's other samples
    choose style arch accent amber left empty right empty
    SAMPLE=ar-ly12 shot "$OUT/$1" pass-arch-ar-ly-12h-1458 14:58
    SAMPLE=ar-ly12 shot "$OUT/$1" pass-arch-ar-ly-12h-1258 12:58 awake
    SAMPLE=ar-ly12 shot "$OUT/$1" pass-arch-ar-ly-12h-2258 22:58 awake
    SAMPLE=ar-sa shot "$OUT/$1" arch-ar-sa-1458 14:58
    shot "$OUT/$1" arch-en-1458 14:58
}
cases() {   # $1: folder; Arch, the Saudi sample at 14:58 with every day's Gregorian line rewritten
    choose style arch accent amber left empty right empty
    local names=(sample sat-aug-1 thu-aug-10 tue-oct-10 thu-oct-10 thu-nov-30)
    local texts=("الأحد ١٨ أكتوبر" "السبت ١ أغسطس" "الخميس ١٠ أغسطس" "الثلاثاء ١٠ تشرين الأول" "الخميس 10 تشرين الأول" "الخميس ٣٠ تشرين الثاني")
    local i
    for i in "${!names[@]}"; do
        "$T/clock.sh" 14:57 > /dev/null < /dev/null
        SAMPLE=ar-sa FACE_APK="$FACE" "$EV/feedtext.sh" "$REPO" greg "${texts[$i]}" 14:58 > /dev/null < /dev/null
        KEEP_FEED=1 shot "$OUT/$1" "greg-${names[$i]}" 14:58 | sed "s/^/${texts[$i]}: /"
    done
}
install_face "$NEW"; echo "cases (the face as committed)"; cases new
perl -e 'alarm 20; exec @ARGV' adb -s emulator-5558 emu power capacity 82 > /dev/null || echo "the console did not answer the battery level in 20 s"
echo "Arch frames, the face as committed"; arch_frames new
install_face "$BASE"; echo "Arch frames, ff28cd5a's face"; arch_frames base
echo done
