#!/usr/bin/env bash
# Scratch (Ruling 51a's first step): the proof face (a scratch copy's watchface.xml whose Arch
# right-to-left Gregorian Groups test subText(...) == "أغسطس") installed on emulator-5558, and Arch's
# Arabic Gregorian line rewritten to each case (feedtext.sh), awake and always-on, on the Saudi
# sample at 14:58.   proof.sh <repo> <out dir> <proof face apk>
set -euo pipefail
REPO="$1"; OUT="$2"; FACE="$3"
T="$REPO/tools/watchface"
EV="$REPO/docs/superpowers/reports/2026-10-23-phone-forms-fit"
adb -s emulator-5558 install -r "$FACE" > /dev/null; sleep 3; adb -s emulator-5558 shell input keyevent KEYCODE_WAKEUP
"$T/choose.sh" style arch accent amber left empty right empty > /dev/null
names=(sample sat-aug-1 thu-aug-10 thu-aug-10-latn wed-dec-30 tue-oct-10)
texts=("الأحد ١٨ أكتوبر" "السبت ١ أغسطس" "الخميس ١٠ أغسطس" "الخميس 10 أغسطس" "الأربعاء ٣٠ ديسمبر" "الثلاثاء ١٠ تشرين الأول")
for i in "${!names[@]}"; do
    "$T/clock.sh" 14:57 > /dev/null < /dev/null
    SAMPLE=ar-sa FACE_APK="$FACE" "$EV/feedtext.sh" "$REPO" greg "${texts[$i]}" 14:58 > /dev/null < /dev/null
    KEEP_FEED=1 "$T/shoot.sh" "$OUT" "greg-${names[$i]}" 14:58 < /dev/null | sed "s/^/  ${texts[$i]}: /"
done
echo done
