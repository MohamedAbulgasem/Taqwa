"""The ink box of a screen's biggest line: Today's countdown or Tasbeeh's count.

    measure.py <shot.png> [y0=150] [y1=300] [x0=40] [x1=414]

Within the band, a row holds ink when one of its pixels is the screens' ink (#F1F3F1, every channel
over 200 and the channels within 30 of each other, so the accent and the mist are left out). The
rows group into runs; the tallest run is the line, and its box is printed as x0 y0 x1 y1 and its
height and width in pixels.
"""
import sys

import numpy as np
from PIL import Image

path = sys.argv[1]
opts = dict(a.split("=") for a in sys.argv[2:])
y0, y1 = int(opts.get("y0", 150)), int(opts.get("y1", 300))
x0, x1 = int(opts.get("x0", 40)), int(opts.get("x1", 414))
a = np.asarray(Image.open(path).convert("RGB")).astype(int)
band = a[y0:y1, x0:x1]
ink = (band.min(axis=2) > 200) & (band.max(axis=2) - band.min(axis=2) < 30)
rows = ink.any(axis=1)
runs, start = [], None
for i, r in enumerate(list(rows) + [False]):
    if r and start is None:
        start = i
    if not r and start is not None:
        runs.append((start, i)); start = None
if not runs:
    print("no ink"); sys.exit(0)
s, e = max(runs, key=lambda r: r[1] - r[0])
cols = np.nonzero(ink[s:e].any(axis=0))[0]
bx0, bx1 = x0 + int(cols.min()), x0 + int(cols.max()) + 1
print("box %d %d %d %d  height %d  width %d" % (bx0, y0 + s, bx1, y0 + e, e - s, bx1 - bx0))
