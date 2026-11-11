#!/usr/bin/env bash
# Plan 5 Task 5, review round 1 of the re-issue: FaceButton's touch target on emulator-5558, one phase.
#   phase.sh <label>          e.g. before, after, after-1.3
# Today's list end (the chip) and the face screen (the button): a shot and a uiautomator dump of
# each into $PASS_WORK/shots, and the button's node bounds. It needs the wear-5558 lock, PASS_WORK
# set as for the pass's drivers (their lib.sh), and the Taqwa face installed but not the watch's
# face, with the activation unspent, so that both show the button. It runs on the real time: the
# paired phone answers the app's start with today's feed. pill.py measures the drawn pill in a shot.
set -u
REPO="$(cd "$(dirname "$0")/../../../../.." && pwd)"
source "$REPO/docs/superpowers/reports/2026-10-24-wear-app-pass/drivers/lib.sh"
K=$1
OUT=$P/shots
mkdir -p "$OUT"
top() { w shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tr -d '\r' | xargs; }
bounds() { python3 -I -c '
import re,sys
s=open(sys.argv[1]).read()
for m in re.finditer(r"<node [^>]*>", s):
    n=m.group(0)
    if "Add the Taqwa face" in n:
        b=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n).groups()
        x0,y0,x1,y1=map(int,b)
        print("node", re.search(r"class=\"([^\"]*)\"", n).group(1), "clickable", re.search(r"clickable=\"(\w+)\"", n).group(1), "bounds", b, "size", x1-x0, "x", y1-y0)
' "$1"; }

# Today, at its list's end, as fixcheck.sh reaches it
# real time: the paired phone sends today's feed at the app's start
w shell am force-stop world.taqwa.app
WAIT=5 "$T/app.sh" today > /dev/null
for _ in 1 2 3 4 5 6 7; do rotary -4; sleep 0.4; done; sleep 2
shot "$OUT/$K-today-end.png"; dump "$OUT/$K-today-end.xml"
echo "today:"; bounds "$OUT/$K-today-end.xml"

# The face screen
w shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d taqwa://face > /dev/null; sleep 3
shot "$OUT/$K-face.png"; dump "$OUT/$K-face.xml"
echo "face screen:"; bounds "$OUT/$K-face.xml"
echo "top: $(top)"
