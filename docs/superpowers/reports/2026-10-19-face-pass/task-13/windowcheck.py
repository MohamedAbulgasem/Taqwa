"""Checks that ref.py draws the Moon's moon window as the face draws it under Ruling 46 (Task 13's
closing fix): always-on the window's near-black fill, #070908, is black, and its hairline, the moon
and everything else are as before; awake nothing changes.

    python3 -I windowcheck.py

For the Moon and the Moon of option (d) with its slots, at the moments its always-on frame lights the
most (04:40 and 20:55) and the pass's (14:58), in English and Arabic, it draws ref.py's always-on
frame by default (Ruling 46) and with window=filled (the approved drawing's fill), and the awake
frame the same two ways, and fails unless:
1. inside the window's hairline, away from the moon's disc, every pixel window=filled draws in the
   fill's #070908 is pure black, #000000, in the default always-on frame (the other pixels there are
   a hand over the window, which check 3 holds);
2. the fill shows in at least 90% of that inside (so the check sees the fill it is about);
3. the two always-on frames differ only inside the window's box, and by no more than the fill's own
   9 levels (where the hairline's and the moon's edges are blended with the fill);
4. the two awake frames are the same to the pixel.
A few pixels of the hairline's inner edge, blended over the black, come out #070908 by chance; so
the fill is told by where it lies, not by its colour alone.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *[os.pardir] * 5))
REF = os.path.join(REPO, "tools", "watchface", "ref.py")
FILL = (7, 9, 8)             # the moon window's fill, #070908
SCALE = 454 / 200            # ref.py draws faces.js's 200 units at the emulator's 454 pixels
LIFT_D = 5                   # the risen window of option (d), units (moon-slot-choice.html's L)

CASES = [
    ("moon en 04:40", ["face=moon", "lang=en", "t=04:40"], 0),
    ("moon ar 12h 20:55", ["face=moon", "lang=ar", "clock=12", "t=20:55"], 0),
    ("moon en 14:58", ["face=moon", "lang=en", "t=14:58"], 0),
    ("moon-d ar 12h slots 20:55", ["face=moon-d", "lang=ar", "clock=12", "comps", "v4=100", "v5=12,345", "t=20:55"], LIFT_D),
    ("moon-d en slots 04:40", ["face=moon-d", "lang=en", "comps", "v4=100", "v5=12,345", "t=04:40"], LIFT_D),
    ("moon-d ar 12h slots 14:58", ["face=moon-d", "lang=ar", "clock=12", "comps", "t=14:58"], LIFT_D),
]


class Refused(Exception):
    pass


def draw(tmp, name, opts):
    out = os.path.join(tmp, name + ".png")
    run = subprocess.run([sys.executable, "-I", REF, out, "sec=0"] + opts, capture_output=True, text=True, timeout=60)
    if run.returncode:
        raise Refused("ref.py %s: %s" % (" ".join(opts), (run.stderr.strip().splitlines() or ["exit %d" % run.returncode])[0]))
    return np.asarray(Image.open(out).convert("RGB")).astype(int)


def interior(shape, lift):
    """The window's inside, clear of its hairline (1 unit wide) and of the moon's disc (9.4 units),
    each with a unit to spare for its blended edge: the pixels the fill alone draws."""
    h, w = shape
    yy, xx = np.mgrid[0:h, 0:w]
    x, y = (xx + 0.5) / SCALE, (yy + 0.5) / SCALE
    d, r = 1.5, 13.5 - 1.5                       # inset, and the stadium's end radius inside it
    cy = 134.5 - lift                            # the window's middle, and the moon's
    inside = (np.abs(y - cy) <= r) & (x >= 77 + 13.5) & (x <= 123 - 13.5)
    inside |= ((x - (77 + 13.5)) ** 2 + (y - cy) ** 2 <= r * r) | ((x - (123 - 13.5)) ** 2 + (y - cy) ** 2 <= r * r)
    return inside & ((x - 100) ** 2 + (y - cy) ** 2 > (9.4 + 1) ** 2)


def window(lift):
    """The moon window's box in the drawing's pixels, with a pixel round it for its hairline's edge."""
    x0, y0, x1, y1 = 77 - 0.5, 121 - lift - 0.5, 123 + 0.5, 148 - lift + 0.5
    return int(x0 * SCALE) - 1, int(y0 * SCALE) - 1, int(x1 * SCALE) + 2, int(y1 * SCALE) + 2


def main():
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for i, (label, opts, lift) in enumerate(CASES):
            black = draw(tmp, "%d-aod" % i, opts + ["aod"])
            fill = interior(black.shape[:2], lift)
            dark = (black[fill] == 0).all(axis=1)       # before window=filled tells the fill from a hand
            try:
                filled = draw(tmp, "%d-aod-filled" % i, opts + ["aod", "window=filled"])
                awake = draw(tmp, "%d-awake" % i, opts)
                awake_filled = draw(tmp, "%d-awake-filled" % i, opts + ["window=filled"])
            except Refused as e:
                print("%-28s FAIL  always-on, %d of the window's %d px not black; %s" % (label, (~dark).sum(), fill.sum(), e))
                failures += ["%s: always-on, %d of the window's %d px not black" % (label, (~dark).sum(), fill.sum())] * bool((~dark).any())
                failures.append("%s: %s" % (label, e))
                continue
            x0, y0, x1, y1 = window(lift)
            inside = np.zeros(black.shape[:2], bool)
            inside[y0:y1, x0:x1] = True
            shows = fill & (filled == FILL).all(axis=2)
            differs = np.abs(black - filled).max(axis=2)
            dark = (black[shows] == 0).all(axis=1)
            checks = [
                (dark.all(), "always-on, %d of the fill's %d px not black" % ((~dark).sum(), shows.sum())),
                (shows.sum() >= 0.9 * fill.sum(), "window=filled: the fill shows in %d of the window's %d px" % (shows.sum(), fill.sum())),
                (not ((differs > 0) & ~inside).any() and differs.max() <= 9,
                 "always-on: %d px differ outside the window, by at most %d" % (((differs > 0) & ~inside).sum(), differs.max())),
                (np.array_equal(awake, awake_filled), "awake: %d px differ" % (np.abs(awake - awake_filled).max(axis=2) > 0).sum()),
            ]
            ok = all(c for c, _ in checks)
            print("%-28s %s  the fill %d px (of the window's %d), %d black; %d px differ, all in the box: %s,"
                  " by at most %d; awake the same: %s" % (
                      label, "ok  " if ok else "FAIL", shows.sum(), fill.sum(), dark.sum(), (differs > 0).sum(),
                      not ((differs > 0) & ~inside).any(), differs.max(), np.array_equal(awake, awake_filled)))
            failures += ["%s: %s" % (label, what) for c, what in checks if not c]
    if failures:
        sys.exit("windowcheck.py: ref.py does not draw Ruling 46's window:\n  " + "\n  ".join(failures))
    print("ref.py draws Ruling 46's moon window: its fill black always-on, the rest as before, awake unchanged")


if __name__ == "__main__":
    main()
