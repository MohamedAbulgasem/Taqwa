"""How much of a screenshot is lit, and what colours its parts are: the share of the screen's
pixels that are lit, a pixel being lit when any channel of the face's colour is over 8 (Google's
always-on limit is 15% lit), and, for each region named on the command line, the most common
colours among its lit pixels and its brightest one.

    python3 -I tools/watchface/lit.py <png> [undither] [name=x0,y0,x1,y1 ...]

undither is for the emulator's always-on frame, and measures it as the face draws it (since Task
13's fix round 2: review round 2 found the earlier fill a point high on edges, and the emulator's
dimming uncounted). The emulator changes an always-on frame twice:
- It dithers it as a strict checkerboard: every pixel it draws lies on one parity of x + y, and
  every pixel of the other is black. So twice the lit pixels is the undithered count. On ref.py's
  drawings, dimmed (below) and masked to one parity, this gives the drawing's own share within 0.02
  point. Filling each black pixel from its neighbours instead, as this tool did before, counts 0.9
  to 1.3 points more on a drawing masked to one parity, because it widens every edge by a pixel.
- It draws it at 0.85 of the face's colours, rounded: the moon window's #070908 comes out 6, 8, 7,
  the window's stroke 35, 40, 37 as 30, 34, 31. So a face's 9 is the frame's 8 and its 8 the
  frame's 7, and a pixel is lit at 8 or more there.
The frame must be one of the emulator's: undither refuses a frame with pixels on both parities. The
regions' colours are read from the frame's own lit pixels, as the emulator draws them. A mockup from
ref.py is neither dithered nor dimmed and needs no undither.
"""
import sys
from collections import Counter

import numpy as np
from PIL import Image

LIT = 8            # lit: any channel of the face's colour over 8
BRIGHT = 64        # brighter than 25%
AOD_DIM = 0.85     # the emulator's always-on frame is drawn at 0.85 of the face's colours, rounded


def first(level, undither):
    """The lowest level of the frame that shows a face colour over `level`."""
    return int(round(AOD_DIM * (level + 1))) if undither else level + 1


def measure(im, undither=False):
    """(lit, bright, lit pixels) of the round screen: the lit share and the share brighter than 25%,
    in percent of the screen, as the face draws them; with undither, the frame is the emulator's
    always-on one (twice its lit parity, at the levels its dimming leaves)."""
    h, w = im.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    screen = (xx + 0.5 - w / 2) ** 2 + (yy + 0.5 - h / 2) ** 2 <= (w / 2) ** 2     # the round screen
    top = im.max(axis=2)
    scale = 100.0 / screen.sum()
    if undither:
        drawn = (top > 0) & screen
        parity = (xx + yy) % 2
        if (drawn & (parity == 0)).any() and (drawn & (parity == 1)).any():
            raise ValueError("not the emulator's always-on checkerboard: pixels on both parities")
        scale *= 2
    lit = (top >= first(LIT, undither)) & screen
    return scale * lit.sum(), scale * ((top >= first(BRIGHT, undither)) & screen).sum(), lit.sum()


def share(path, undither=False):
    """The lit share of a PNG's round screen, in percent."""
    return measure(np.asarray(Image.open(path).convert("RGB")).astype(int), undither)[0]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    im = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(int)
    undither = "undither" in sys.argv[2:]
    try:
        lit, bright, count = measure(im, undither)
    except ValueError as e:
        sys.exit("lit.py: %s: %s" % (sys.argv[1], e))
    print("lit: %.1f%% of the round screen (%d px%s); brighter than 25%%: %.1f%%" % (
        lit, count, ", twice them undithered" if undither else "", bright))
    for arg in (a for a in sys.argv[2:] if a != "undither"):
        name, box = arg.split("=", 1)
        x0, y0, x1, y1 = (int(v) for v in box.split(","))
        part = im[y0:y1, x0:x1].reshape(-1, 3)
        part = part[part.sum(axis=1) > 40]
        if not len(part):
            print("%-14s no lit pixels" % name)
            continue
        common = Counter(map(tuple, part)).most_common(3)
        best = max(map(tuple, part), key=sum)
        print("%-14s lit %5d  most common %s  brightest #%02X%02X%02X" % (
            name, len(part), ", ".join("#%02X%02X%02X x%d" % (c + (n,)) for c, n in common), *best))


if __name__ == "__main__":
    main()
