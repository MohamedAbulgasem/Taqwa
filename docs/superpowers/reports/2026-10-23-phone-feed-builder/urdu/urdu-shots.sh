#!/usr/bin/env bash
# P2.3b Step 6's shots (committed beside the report; no build or test runs it): the phone's first
# Urdu feed (watchcore/vectors/phone/phone-capetown-ur24.json, Cape Town in ur-PK-u-nu-arabext on 24
# hours) stored with tools/watchface/store.sh, and each style shot awake as the plan names it:
#   <style>-ur-awake.png        at 16:47: the clock ۱۶:۴۷ (۶ ۴ ۷), Maghrib ۱۹:۰۶ (۶), the Hijri ۶
#   <style>-ur-1523-awake.png   at 15:23 as well: the countdown ۴۷ منٹ میں to Asr (۴ ۷), the clock ۱۵:۲۳
# The watch's own language is set before (the report says how); this script does not change it. The
# face editor that choose.sh drives is found by its English labels, so under an Urdu watch the style
# is chosen first, in English, and the shots taken with NO_CHOOSE=1 and STYLES=<that style>.
#   urdu-shots.sh <repo> <out dir>
set -euo pipefail
REPO="$1"; OUT="$2"
T="$REPO/tools/watchface"
FEED="$REPO/watchcore/vectors/phone/phone-capetown-ur24.json"
mkdir -p "$OUT"
for style in ${STYLES:-arch ring sun moon}; do
    [ "${NO_CHOOSE:-0}" = 1 ] || "$T/choose.sh" style "$style" > /dev/null
    for at in 16:47 15:23; do
        before="$(python3 -I -c 'import sys; h, m = map(int, sys.argv[1].split(":")); t = h * 60 + m - 1; print("%02d:%02d" % (t // 60, t % 60))' "$at")"
        "$T/clock.sh" "$before" > /dev/null
        "$T/store.sh" "$FEED" > /dev/null
        name="$style-ur"; [ "$at" = 16:47 ] || name="$style-ur-${at/:/}"
        KEEP_FEED=1 "$T/shoot.sh" "$OUT" "$name" "$at" awake | sed 's/^/  /'
    done
done
[ "${NO_CHOOSE:-0}" = 1 ] || "$T/choose.sh" style arch > /dev/null
