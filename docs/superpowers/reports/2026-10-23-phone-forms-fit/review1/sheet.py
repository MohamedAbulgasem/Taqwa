"""The committed pass's Arch Arabic frames before and after this fix round, side by side:

    python3 -I sheet.py <old dir> <new dir> <out.png> <name>...

For each pass shot <name>.png: the old frame (left), the new one (middle) and where they differ by
more than 8 levels in any channel (right, red; the rest dimmed), then the line's band, y 128-182 and
x 110-345, at three times. Always-on frames are undithered for the eye, as tools/watchface/compare.py
does (each dark pixel filled from its lit neighbour to the right or below); the count is on the
frames as shot."""
import os, sys

import numpy as np
from PIL import Image, ImageDraw

OLD, NEW, OUT, names = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
BAND, ZOOM, GAP, LABEL = (110, 128, 345, 182), 3, 12, 22


def undither(b):
    right = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
    below = np.concatenate([b[1:], b[-1:]], axis=0)
    return np.maximum(b, np.maximum(right, below))


rows = []
for name in names:
    a = np.asarray(Image.open(os.path.join(OLD, name + ".png")).convert("RGB")).astype(int)
    b = np.asarray(Image.open(os.path.join(NEW, name + ".png")).convert("RGB")).astype(int)
    differs = np.abs(a - b).max(axis=2) > 8
    heat = np.zeros(a.shape, dtype=np.uint8)
    heat[..., 0] = np.where(differs, 255, b.max(axis=2) // 4)
    heat[..., 1] = np.where(differs, 64, b.max(axis=2) // 4)
    heat[..., 2] = np.where(differs, 64, b.max(axis=2) // 4)
    if name.endswith("-aod"):
        a, b = undither(a), undither(b)
    panels = [Image.fromarray(p.astype(np.uint8), "RGB") for p in (a, b, heat)]
    w, h = panels[0].size
    bw, bh = (BAND[2] - BAND[0]) * ZOOM, (BAND[3] - BAND[1]) * ZOOM
    zoomed = [p.crop(BAND).resize((bw, bh), Image.NEAREST) for p in panels[:2]]
    width = max(3 * w + 2 * GAP, 2 * bw + GAP)
    row = Image.new("RGB", (width, LABEL + h + GAP + bh + GAP), (40, 40, 40))
    d = ImageDraw.Draw(row)
    ys, xs = np.nonzero(differs)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1) if len(xs) else None
    d.text((4, 4), "%s: committed (left), now (middle), %d px differ, box %s (right); the band at %dx below"
           % (name, differs.sum(), box, ZOOM), fill=(230, 230, 230))
    for i, p in enumerate(panels):
        row.paste(p, (i * (w + GAP), LABEL))
    for i, p in enumerate(zoomed):
        row.paste(p, (i * (bw + GAP), LABEL + h + GAP))
    rows.append(row)
    print("%-36s %5d px differ, box %s" % (name, differs.sum(), box))

sheet = Image.new("RGB", (max(r.width for r in rows), sum(r.height for r in rows)), (40, 40, 40))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height
sheet.save(OUT)
print("wrote", OUT, sheet.size)
