#!/usr/bin/env bash
# Ruling 65 item 4 on the final code, emulator-5558: a full start with the renderer late (Asr),
# a doze across a start (Maghrib), and TalkBack on a two-sided entry (Isha).
#   r65.sh [late|doze|talkback ...]
source "$(dirname "$0")/lib.sh"
PARTS=("$@"); [ ${#PARTS[@]} -eq 0 ] && PARTS=(late doze talkback)
D=$P/shots/r65; mkdir -p "$D"; mkdir -p "$P/logs"; LOG=$P/logs/r65.txt
say() { echo "$*" | tee -a "$LOG"; }
line() {  # the Tile's line and its lit row, from a dump
    dump "$D/tmp.xml"
    python3 -I - "$D/tmp.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
r = ET.parse(sys.argv[1]).getroot()
descs = [n.attrib.get("content-desc", "") for n in r.iter() if n.attrib.get("content-desc")]
print(" | ".join(descs[:3]))
PY
}
alarm() { w shell dumpsys alarm | grep -A1 "^ *tag=\*alarm\*:ProtoTilesTimelineMgr" | grep -o "origWhen=[0-9-]* [0-9:.]* window=[^ ]*" | head -2 | paste -sd ';' -; }
lit() {  # the lit row of the Tile shot $1, by its accent pip
    python3 -I - "$1" <<'PY'
import sys, numpy as np
from PIL import Image
rows = {'Fajr': 136, 'Sunrise': 185, 'Dhuhr': 222, 'Asr': 269, 'Maghrib': 317, 'Isha': 365}
a = np.asarray(Image.open(sys.argv[1]).convert('RGB')).astype(int)
acc = (abs(a[:, :, 0] - 0xF0) < 30) & (abs(a[:, :, 1] - 0xB4) < 30) & (abs(a[:, :, 2] - 0x29) < 40)
ys = [y for y in np.nonzero(acc[:, 85:125].any(axis=1))[0] if y > 110]
print(",".join(n for n, y in rows.items() if any(abs(v - y) < 12 for v in ys)) or "none")
PY
}
until_t() { "$DRIVERS/w" shell date +%H:%M:%S | tr -d '\r'; }

late() {
    say "== a full start with the renderer late: Asr 16:10 =="
    face; clock 16:08:10; store en 16:08; sleep 21
    tile_shot "$D/late-start.png" "Cape Town" | tee -a "$LOG"
    say "  asked at about 16:08:10; alarms: $(alarm)"
    for i in $(seq 1 22); do
        t=$(until_t); shot "$D/late-${t//:/}.png" > /dev/null; say "  $t: $(line) | lit $(lit "$D/late-${t//:/}.png") | pending: $(alarm)"
        [[ "$t" > "16:11:20" ]] && break
        sleep 4
    done
    say "  alarms after: $(alarm)"
}

doze() {
    say "== a doze across a start: Maghrib 19:04 =="
    face; clock 19:01:10; store en 19:01; sleep 21
    tile_shot "$D/doze-start.png" "Cape Town" | tee -a "$LOG"
    say "  alarms before: $(alarm)"
    say "  force-idle: $(w shell dumpsys deviceidle force-idle | tr -d '\r')"
    for i in $(seq 1 30); do
        t=$(until_t); shot "$D/doze-${t//:/}.png" > /dev/null; say "  $t: $(line) | lit $(lit "$D/doze-${t//:/}.png") | pending: $(alarm)"
        [[ "$t" > "19:05:30" ]] && break
        sleep 8
    done
    say "  alarms in idle: $(alarm)"
    say "  unforce: $(w shell dumpsys deviceidle unforce | tr -d '\r')"
    sleep 4; t=$(until_t); shot "$D/doze-unforced-${t//:/}.png" > /dev/null; say "  $t: $(line)"
}

talkback() {
    say "== TalkBack on a two-sided entry: Isha 20:24 =="
    "$T/talkback.sh" off > /dev/null
    face; clock 20:21:30; store en 20:21; sleep 21
    tile_shot "$D/tb-start.png" "Cape Town" | tee -a "$LOG"
    "$T/talkback.sh" on | sed 's/^/  /' | tee -a "$LOG"
    if w shell dumpsys activity activities | grep topResumedActivity | grep -q PermissionRequestActivity; then w shell input tap 152 373; sleep 1.5; fi
    say "  at $(until_t), the walk:"; "$T/talkback.sh" walk 9 | sed 's/^/    /' | tee -a "$LOG"
    while [[ "$(until_t)" < "20:24:03" ]]; do sleep 1; done
    say "  at $(until_t), the walk:"; "$T/talkback.sh" walk 9 | sed 's/^/    /' | tee -a "$LOG"
    "$T/talkback.sh" off > /dev/null
}

for part in "${PARTS[@]}"; do "$part"; done
rm -f "$D/tmp.xml"; face
