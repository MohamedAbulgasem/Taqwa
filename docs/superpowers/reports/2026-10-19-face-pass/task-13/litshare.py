"""The always-on lit share of every Moon always-on pass frame: raw (the emulator's dithered frame, at
its own levels), as the face draws it (lit.py undither: twice the checkerboard's lit parity, lit at
8 or more after the emulator's 0.85, the measure since Task 13's fix round 2), the drawing's (ref.py
aod, Ruling 41's hands and, since the closing fix, Ruling 46's moon window), and the first figure (round 1: each black pixel filled from its neighbours),
with the Moon's outline frames of d40776a7's pass and the other styles' always-on frames for comparison. The mean light is the frame's
undithered (twice its mean, at the emulator's levels), and the brightest the 99th percentile of its
lit pixels. With a fourth folder, the pass of ec8f1bd4 (git archive: the face before Ruling 46, its
moon window's fill lit always-on), each Moon frame's share then is given too.
    python3 -I litshare.py <the pass dir> <d40776a7's pass dir (git archive)> <ref dir> [<ec8f1bd4's pass dir>]"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), *[os.pardir] * 5, "tools", "watchface"))
import lit  # noqa: E402  (tools/watchface/lit.py: the measure)


def share(path, frame):
    """(lit share, mean light, brightest): of an emulator always-on frame as the face draws it, or of a
    drawing."""
    b = np.asarray(Image.open(path).convert("RGB")).astype(int)
    h, w = b.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    screen = (xx + 0.5 - w / 2) ** 2 + (yy + 0.5 - h / 2) ** 2 <= (w / 2) ** 2
    lum = 0.2126 * b[..., 0] + 0.7152 * b[..., 1] + 0.0722 * b[..., 2]
    on = (b.max(axis=2) > 8) & screen
    return lit.measure(b, frame)[0], (2 if frame else 1) * lum[screen].mean(), np.percentile(lum[on], 99)


def raw(path):
    b = np.asarray(Image.open(path).convert("RGB")).astype(int)
    h, w = b.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    screen = (xx + 0.5 - w / 2) ** 2 + (yy + 0.5 - h / 2) ** 2 <= (w / 2) ** 2
    r = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
    d = np.concatenate([b[1:], b[-1:]], axis=0)
    filled = np.maximum(b, np.maximum(r, d))
    return (100.0 * ((b.max(axis=2) > 8) & screen).sum() / screen.sum(),
            100.0 * ((filled.max(axis=2) > 8) & screen).sum() / screen.sum())


new, old, ref = sys.argv[1:4]
pre46 = sys.argv[4] if len(sys.argv) > 4 else None
names = ["pass-moon-en-1458", "pass-moon-en-1010", "pass-moon-ar-ly-12h-1458", "pass-moon-ar-ly-12h-1010",
         "pass-moon-slots-1458", "pass-moon-slots-1010", "pass-moon-ar-ly-12h-slots-1458", "pass-moon-ar-ly-12h-slots-1010"]
print("Moon always-on, Ruling 41's hands at A_HANDS 209%s (limit 15%% lit):" % (", Ruling 46's moon window" if pre46 else ""))
print("%-34s %7s %7s %9s %8s %11s %9s   %s%s" % ("frame", "raw", "face", "drawing", "round 1", "mean light", "brightest",
                                                 "before Ruling 46 (ec8f1bd4): face   " if pre46 else "",
                                                 "before (d40776a7, outline hands): raw / face"))
worst = 0
for n in names:
    p = os.path.join(new, n + "-aod.png")
    r, filled = raw(p)
    face, mean, p99 = share(p, True)
    drw = share(os.path.join(ref, n + "-aod.png"), False)[0]
    worst = max(worst, face, drw)
    o = os.path.join(old, n + "-aod.png")
    before = ("%.2f%% / %.2f%%" % (raw(o)[0], share(o, True)[0])) if os.path.exists(o) else "not in d40776a7's pass"
    b46 = ("%32.2f%%   " % share(os.path.join(pre46, n + "-aod.png"), True)[0]) if pre46 else ""
    print("%-34s %6.2f%% %6.2f%% %8.2f%% %7.2f%% %11.2f %9.1f   %s%s" % (n, r, face, drw, filled, mean, p99, b46, before))
print("highest: %.2f%% (under 15%%: %s)" % (worst, worst < 15))
print("\nThe other styles' always-on frames (the committed pass), for the mood:")
for n in ["pass-arch-en-1458", "pass-arch-en-1240", "pass-arch-en-1855", "pass-arch-slots-1458", "pass-ring-en-1458",
          "pass-ring-ar-sa-1458", "pass-sun-en-1458", "pass-arch-ar-ly-12h-1458", "pass-ring-ar-ly-12h-1458", "pass-sun-ar-ly-12h-1458"]:
    p = os.path.join(new, n + "-aod.png")
    r, filled = raw(p)
    face, mean, p99 = share(p, True)
    print("%-34s %6.2f%% %6.2f%% %8s %7.2f%% %11.2f %9.1f" % (n, r, face, "", filled, mean, p99))
