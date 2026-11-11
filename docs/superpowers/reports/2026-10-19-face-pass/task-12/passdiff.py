"""passdiff.py <committed dir> <new dir>: every pass-*.png in both, the pixels that differ, and those
left once the Moon's seconds hand (a band 14 px either side of its angle at the shot's second, its
tail included), the step count's value (the right slot's box, when slots are filled) and, on the
Moon, the moon's picture (which the source draws for the middle of the step it is asked at, so its
edge moves by a fraction of a pixel with the instant the pass asked at) are set aside."""
import glob, math, os, sys
from PIL import Image

ref_dir, new_dir = sys.argv[1], sys.argv[2]
SECOND = {"1458": 0, "1010": 0}
total_left = 0
for ref in sorted(glob.glob(os.path.join(ref_dir, "pass-*.png"))):
    name = os.path.basename(ref)
    new = os.path.join(new_dir, name)
    if not os.path.exists(new):
        print("%-40s MISSING" % name); continue
    a = Image.open(ref).convert("RGB"); b = Image.open(new).convert("RGB")
    W, H = a.size
    pa, pb = a.load(), b.load()
    moon_awake = name.startswith("pass-moon") and name.endswith("-awake.png")
    slots = "-slots-" in name
    sec = next((v for k, v in SECOND.items() if k in name), None)
    diff = left = moon = 0
    big = 0
    worst = 0
    box = None
    for y in range(H):
        for x in range(W):
            if pa[x, y] == pb[x, y]:
                continue
            diff += 1
            if moon_awake and sec is not None:
                ang = math.radians(sec * 6)
                dx, dy = math.sin(ang), -math.cos(ang)
                rx, ry = x - 227, y - 227
                along = rx * dx + ry * dy
                across = abs(rx * dy - ry * dx)
                if across <= 14 and -110 <= along <= 235:
                    continue
            if slots and 236 <= x <= 345 and 370 <= y <= 442:
                continue
            if name.startswith("pass-moon") and 200 <= x <= 256 and 268 <= y <= 330:
                moon += 1
                continue
            left += 1
            level = max(abs(p - q) for p, q in zip(pa[x, y], pb[x, y]))
            worst = max(worst, level)
            if level > 2:
                big += 1
                box = (x, y, x, y) if box is None else (min(box[0], x), min(box[1], y), max(box[2], x), max(box[3], y))
    total_left += left
    print("%-40s differ %6d  moon %3d  left %5d  worst %3d  over 2 levels %5d  %s" % (name, diff, moon, left, worst, big, box or ""))
print("left in all:", total_left)
