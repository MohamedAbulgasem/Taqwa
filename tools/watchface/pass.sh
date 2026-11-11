#!/usr/bin/env bash
# The face's full pass (plan 1, Task 10) on emulator-5558, into a directory of your choosing:
#
#   tools/watchface/pass.sh <out dir>
#
# For each shot, <name>-awake.png and, where the pass has one, <name>-aod.png (shoot.sh):
#   pass-<style>-en-1458              the four styles at 14:58, English, 24 hours, Amber
#   pass-moon-<en|ar-ly-12h>-1010     the Moon at the mockups' 10:10 as well, English and Arabic (Libya)
#   pass-arch-en-<1240|1855>          Arch at the mockups' other two moments: the apex, both feet
#   pass-<style>-ar-ly-12h-1458       the four in Arabic (Libya: Western digits) on twelve hours
#   pass-ring-ar-sa-1458              Ring in Arabic (Saudi Arabia: Arabic-Indic digits)
#   pass-arch-<colour>-1458           Arch in Pearl, Sage and Night (Amber is pass-arch-en-1458)
#   pass-arch-slots-1458              Arch with the wearer's two slots filled: the battery and the steps
#   pass-moon-slots-<1010|1458>       the Moon with the wearer's two slots filled (the battery, the steps):
#                                     its lower half re-laid, option (d)
#   pass-moon-ar-ly-12h-slots-<1010|1458>   the same in Arabic (Libya), twelve hours
#   pass-<style>-<en|ar-ly>-12h-<1258|2258>   Arch, Ring and Sun at 12:58 and 10:58 PM on twelve hours
#
# It leaves the watch as it found it as far as the pass goes: Arch, Amber, both slots empty, the
# sample for today, automatic time. The battery is set to 82%, as faces.js draws it (its source
# writes "82").
source "$(dirname "$0")/lib.sh"
need_emulator
OUT="${1:?usage: $0 <out dir>}"
mkdir -p "$OUT"
choose() { "$HERE/choose.sh" "$@" > /dev/null; }
shot() { SAMPLE="${SAMPLE:-}" "$HERE/shoot.sh" "$OUT" "$@" | sed 's/^/  /'; }

wadb emu power capacity 82 > /dev/null
choose style arch accent amber left empty right empty
for style in arch ring sun moon; do
    choose style "$style"
    shot "pass-$style-en-1458" 14:58
    if [ "$style" = arch ]; then
        shot pass-arch-en-1240 12:40
        shot pass-arch-en-1855 18:55
    fi
    SAMPLE=ar-ly12 shot "pass-$style-ar-ly-12h-1458" 14:58
    if [ "$style" = moon ]; then
        shot pass-moon-en-1010 10:10
        SAMPLE=ar-ly12 shot pass-moon-ar-ly-12h-1010 10:10
    fi
    if [ "$style" = ring ]; then SAMPLE=ar-sa shot pass-ring-ar-sa-1458 14:58; fi
    if [ "$style" != moon ]; then
        for t in 12:58 22:58; do
            SAMPLE=en12 shot "pass-$style-en-12h-${t/:/}" "$t" awake
            SAMPLE=ar-ly12 shot "pass-$style-ar-ly-12h-${t/:/}" "$t" awake
        done
    fi
done
choose left battery right steps
shot pass-moon-slots-1010 10:10
shot pass-moon-slots-1458 14:58
SAMPLE=ar-ly12 shot pass-moon-ar-ly-12h-slots-1010 10:10
SAMPLE=ar-ly12 shot pass-moon-ar-ly-12h-slots-1458 14:58
choose style arch
shot pass-arch-slots-1458 14:58
choose left empty right empty
for colour in pearl sage night; do
    choose accent "$colour"
    shot "pass-arch-$colour-1458" 14:58 awake
done
choose accent amber
"$HERE/clock.sh" auto > /dev/null
"$HERE/feed.sh" "$(device_epoch)" > /dev/null
echo "the pass is in $OUT"
