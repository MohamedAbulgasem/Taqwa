#!/usr/bin/env bash
# P2.3b Step 4's evidence (committed beside the report; no build or test runs it): the sample feed as
# P2.3a regenerated it (place as Ruling 39 rounds it) against the same feed with P2.3a's old place
# (the bearing, distance and declination to full precision), on the face as committed, in one session
# on emulator-5558, shot at the same moments: new, old, new again. Only the place differs between
# the two files; everything else is the debug receiver's own sample for the mock day.
#   ab-place.sh <repo> <out dir> <old place json dir> <round>...      (rounds named new*, old*)
# STYLES="sun" shoots those styles alone (default all four); a second run into the same <out dir>
# keeps the feeds the first one wrote.
# <old place json dir> holds place-<sample>.json (en, en12, ar-ly12, ar-sa): the place object of
# watchcore/vectors/sample-feed-capetown*.json before P2.3a (git show 32826332^1:...).
set -euo pipefail
REPO="$1"; OUT="$2"; OLDPLACE="$3"; shift 3
T="$REPO/tools/watchface"
source "$T/lib.sh"
need_emulator
FEEDS="$OUT/feeds"
mkdir -p "$FEEDS"
choose() { "$T/choose.sh" "$@" > /dev/null; }

# The two files of each sample, for the day of 14:58 on the mock day.
for s in en en12 ar-ly12 ar-sa; do
    [ ! -f "$FEEDS/old-$s.json" ] || continue
    "$T/clock.sh" 14:57 > /dev/null
    if [ "$s" = en ]; then SAMPLE= store_feed "$(epoch_of 14:58)"; else SAMPLE="$s" store_feed "$(epoch_of 14:58)"; fi
    sleep 1
    wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$FEEDS/new-$s.json"
    python3 -I -c '
import json, sys
feed = json.load(open(sys.argv[1], encoding="utf-8"))
feed["place"] = json.load(open(sys.argv[2], encoding="utf-8"))
open(sys.argv[3], "w", encoding="utf-8").write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$FEEDS/new-$s.json" "$OLDPLACE/place-$s.json" "$FEEDS/old-$s.json"
done

# One shot: the clock a minute before, the file stored, the face bound again, then shot.
one() {   # round sample name time mode
    "$T/clock.sh" "$(python3 -I -c 'import sys; h, m = map(int, sys.argv[1].split(":")); t = h * 60 + m - 1; print("%02d:%02d" % (t // 60, t % 60))' "$4")" > /dev/null
    "$T/store.sh" "$FEEDS/${1%%[0-9]*}-$2.json" > /dev/null
    KEEP_FEED=1 "$T/shoot.sh" "$OUT/$1" "$3" "$4" "$5" | sed 's/^/  /'
}
for round in "$@"; do
    echo "$round"
    for style in ${STYLES:-arch ring sun moon}; do
        choose style "$style"
        one "$round" en "$style-en-1458" 14:58 both
        one "$round" ar-ly12 "$style-ar-ly-12h-1458" 14:58 awake
        one "$round" ar-sa "$style-ar-sa-1458" 14:58 awake
        if [ "$style" != moon ]; then one "$round" en12 "$style-en-12h-1258" 12:58 awake; fi
    done
    choose style arch
done
