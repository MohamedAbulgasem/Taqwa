#!/usr/bin/env bash
# The Tasbeeh drawings and their text masks (ref.py), into $PASS_WORK/ref.
source "$(dirname "$0")/lib.sh"
R=$P/ref; mkdir -p "$R"
cd "$REPO"
while read -r n args; do
    python3 -I tools/watchface/ref.py "$R/$n.png" screen=g-tasbeeh $args textmask="$R/$n-mask.png" > /dev/null
done <<'L'
tasbeeh-0 count=0 round=1
tasbeeh-27 count=27 round=1
tasbeeh-33 count=33 round=1
tasbeeh-33-flash count=33 round=1 flash=part
tasbeeh-100-flash count=100 round=1 flash=set
tasbeeh-0-round2 count=0 round=2
L
