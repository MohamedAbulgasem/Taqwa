"""windowdiff.py <committed dir> <new dir>: for each Moon frame in both (pass-moon-*, or moon-*), what Ruling 46 changed
(Task 13's closing fix: the moon window's fill black always-on). Inside the window (its box, the risen
one with the slots filled, with 2 px round it for its hairline's edge) an always-on frame's fill
pixels, which the emulator draws at 6, 8, 7 (0.85 of #070908), should be black now; outside it nothing
should change. Awake nothing should change at all, the window included, so an awake frame's pixels in
the window are counted with those outside it. As task-12/passdiff.py does, the moon's picture (the
source draws it for the middle of the step it is asked at) and, with the slots filled, the step
count's box are set aside, and on an awake frame the seconds hand's band at the shot's second. Each
line gives the pixels that differ, those inside the window, the fill's pixels inside it before and
now, those set aside, and what is left (always-on outside the window, awake anywhere), with its
largest difference in any channel (0 to 255) and how many differ by more than 2 levels.

The emulator dithers an always-on frame as a checkerboard on one parity of x + y, which can change from
one frame to the next: two always-on frames are compared pixel by pixel only on the same parity, and a
pair on different parities is said so and not counted."""
import glob
import math
import os
import sys

import numpy as np
from PIL import Image

K, S = 2.25, 454 / 450
LIFT_D = 11.25                          # face-moon.py: the risen window's lift (5 units)
EMULATOR_FILL = (6, 8, 7)


def window(name, size=454):
    lift = LIFT_D if "-slots-" in name else 0
    x0, x1 = (77 * K - 0.5 * K) * S - 2, (123 * K + 0.5 * K) * S + 2
    y0, y1 = (121 * K - lift - 0.5 * K) * S - 2, (148 * K - lift + 0.5 * K) * S + 2
    m = np.zeros((size, size), bool)
    m[int(y0):int(math.ceil(y1)), int(x0):int(math.ceil(x1))] = True
    return m


def set_aside(name, size=454):
    """passdiff.py's: the moon's picture, the step count's value with the slots filled, and awake the
    seconds hand's band (at second 0, the pass's)."""
    m = np.zeros((size, size), bool)
    m[268:331, 200:257] = True
    if "-slots-" in name:
        m[370:443, 236:346] = True
    if name.endswith("-awake.png"):
        yy, xx = np.mgrid[0:size, 0:size]
        rx, ry = xx - 227, yy - 227
        along, across = -ry, np.abs(rx)              # the hand at 0 s points to twelve
        m |= (across <= 14) & (along >= -110) & (along <= 235)
    return m


def parity(b):
    drawn = b.max(axis=2) > 0
    yy, xx = np.mgrid[0:b.shape[0], 0:b.shape[1]]
    p = (xx + yy) % 2
    on = {int(v) for v in np.unique(p[drawn])}
    return on.pop() if len(on) == 1 else None


def main():
    old_dir, new_dir = sys.argv[1], sys.argv[2]
    for new in sorted(glob.glob(os.path.join(new_dir, "*moon-*.png"))):
        name = os.path.basename(new)
        old = os.path.join(old_dir, name)
        if not os.path.exists(old):
            print("%-40s not in the committed pass" % name)
            continue
        a = np.asarray(Image.open(old).convert("RGB")).astype(int)
        b = np.asarray(Image.open(new).convert("RGB")).astype(int)
        aod = name.endswith("-aod.png")
        if aod and parity(a) != parity(b):
            print("%-40s always-on frames on different parities (%s, %s): not compared" % (name, parity(a), parity(b)))
            continue
        diff = np.abs(a - b).max(axis=2)
        win, aside = window(name), set_aside(name)
        fill_old = (a == EMULATOR_FILL).all(axis=2) & win
        fill_new = (b == EMULATOR_FILL).all(axis=2) & win
        held = ~win if aod else np.ones_like(win)    # always-on the window's own change is set apart; awake it is held too
        left = (diff > 0) & held & ~aside
        worst = diff[left].max() if left.any() else 0
        fills = "fill px before %4d, now %4d" % (fill_old.sum(), fill_new.sum()) if aod else "awake: the fill as drawn"
        print("%-40s differ %5d; in the window %5d (%s); set aside %4d; left %4d, worst %3d, over 2 levels %d" % (
            name, (diff > 0).sum(), ((diff > 0) & win).sum(), fills, ((diff > 0) & held & aside).sum(),
            left.sum(), worst, ((diff > 2) & left).sum()))


if __name__ == "__main__":
    main()
