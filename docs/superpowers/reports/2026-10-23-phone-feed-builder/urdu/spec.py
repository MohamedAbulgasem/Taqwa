"""spec.py <shots dir> <out.json> [control]: the boxes of every line that holds ۴, ۶ or ۷ in P2.3b Step 6's
shots (urdu-shots.sh), each with its font and its size in the face's units (tools/face-centre.py and
tools/face-moon.py: K = 2.25), for glyphs.py sheet. With "control", control.sh's old-face shots.
Committed beside the report; no build or test runs it."""
import json
import os
import sys

K = 2.25
HIJRI = 27.0 * 12.5 / 12          # Arch (15 characters or fewer) and Ring/Sun: 28.125 units
PRAYER = 13.5 * K                 # the prayer line's time, SemiBold
COUNT = 12.5 * K                  # the countdown, SemiBold
CLOCK = {"arch": 56 * K, "ring": 60 * K, "sun": 60 * K}   # 24 hours, Light
MOON = {"prayer": 13 * K, "count": 12 * K, "hijri": 12 * K}
TURN = list(range(-40, 41, 5))
TOP = {"arch": 0, "ring": -6, "sun": -6}    # Ring and Sun draw the stack 6 px higher than Arch


def centre(style, d, at):
    t = TOP[style]
    items = [
        dict(label="%s %s Hijri day" % (style, at), shot=d, box=[138, 103 + t, 314, 141 + t], weight="semibold", units=HIJRI, text="۶ جمادی الاول"),
    ]
    if at == "16:47":
        items += [
            dict(label="%s 16:47 clock" % style, shot=d, box=[100, 190 + t, 352, 296 + t], weight="light", units=CLOCK[style], text="۱۶:۴۷"),
            dict(label="%s 16:47 Maghrib's time" % style, shot=d, box=[142, 316, 314, 355], weight="semibold", units=PRAYER, text="۱۹:۰۶"),
        ]
    else:
        items += [
            dict(label="%s 15:23 Asr's time" % style, shot=d, box=[151, 316, 306, 353], weight="semibold", units=PRAYER, text="۱۶:۱۰"),
            dict(label="%s 15:23 countdown" % style, shot=d, box=[146, 353, 306, 390], weight="semibold", units=COUNT, text="۴۷ منٹ میں"),
        ]
    return items


def moon(d, at):
    items = [dict(label="moon %s Hijri day (curved)" % at, shot=d, box=[135, 340, 315, 395], weight="semibold", units=MOON["hijri"], text="۶ جمادی الاول", angles=TURN)]
    if at == "16:47":
        items.append(dict(label="moon 16:47 Maghrib's time (curved)", shot=d, box=[135, 55, 235, 102], weight="bold", units=MOON["prayer"], text="۱۹:۰۶", angles=TURN))
    else:
        items += [
            dict(label="moon 15:23 Asr's time (curved)", shot=d, box=[140, 55, 235, 102], weight="bold", units=MOON["prayer"], text="۱۶:۱۰", angles=TURN),
            dict(label="moon 15:23 countdown (curved)", shot=d, box=[150, 85, 305, 128], weight="semibold", units=MOON["count"], text="۴۷ منٹ میں", angles=TURN),
        ]
    return items


shots, out = sys.argv[1], sys.argv[2]
spec = []
if len(sys.argv) > 3 and sys.argv[3] == "control":
    for name in sorted(os.listdir(shots)):
        if name.endswith("-awake.png"):
            for item in centre("arch", os.path.join(shots, name), "16:47"):
                item["label"] = name[:-len("-awake.png")] + ": " + item["label"]
                spec.append(item)
else:
    for style in ("arch", "ring", "sun", "moon"):
        for at, suffix in (("16:47", ""), ("15:23", "-1523")):
            d = os.path.join(shots, "%s-ur%s-awake.png" % (style, suffix))
            spec += moon(d, at) if style == "moon" else centre(style, d, at)
json.dump(spec, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(out, len(spec), "lines")
