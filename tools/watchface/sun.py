"""Measures the Sun style's dial in a screenshot, or in a mockup from ref.py, so that the two can be
set side by side in numbers: where the ring's stretches begin and end, where each mark is and what
colour it is, where the lit arc ends, and where the sun (or the crescent) is.

    python3 -I tools/watchface/sun.py <png> [<png> ...] [accent=#F0B429]

Angles are degrees clockwise from twelve, as on the dial (a quarter of a degree a minute, Dhuhr at
0). Everything is read on the 454 px screen, the dial's radius being 92 of faces.js's 200 units:

- ring: the colour along the ring's middle (207 x 454 / 450 px) every tenth of a degree, put in the
  class it is nearest (hairline, twilight, daylight, accent, or "-" for anything else, such as a mark
  crossing it), and printed as runs;
- marks: the runs of lit pixels just outside the ring (96 units), where only the marks and the sun
  reach, with the colour at the middle of each;
- sun: the middle of the pixels as bright as the sun's core (#FFF1C9), as an angle and a radius.
"""
import math
import sys

import numpy as np
from PIL import Image

SCALE = 454 / 450
CX = CY = 227.0
R_RING = 207 * SCALE
R_MARK = 96 * 2.25 * SCALE


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def at(img, r, deg):
    a = math.radians(deg)
    x, y = CX + r * math.sin(a), CY - r * math.cos(a)
    xi, yi = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - xi, y - yi
    p = img[yi:yi + 2, xi:xi + 2].astype(float)
    top = p[0, 0] * (1 - fx) + p[0, 1] * fx
    bottom = p[1, 0] * (1 - fx) + p[1, 1] * fx
    return top * (1 - fy) + bottom * fy


def classify(rgb, classes):
    best, dist = "-", 1e9
    for name, ref in classes.items():
        d = max(abs(a - b) for a, b in zip(rgb, ref))
        if d < dist:
            best, dist = name, d
    return best if dist <= 14 else "-"


def runs(labels, step):
    out, start = [], 0
    for i in range(1, len(labels) + 1):
        if i == len(labels) or labels[i] != labels[start]:
            out.append((labels[start], start * step, i * step))
            start = i
    # The circle closes: a run that ends at 360 and one that begins at 0 are one.
    if len(out) > 1 and out[0][0] == out[-1][0]:
        name, a0, _ = out.pop()
        out[0] = (name, a0 - 360, out[0][2])
    return out


def measure(path, accent):
    img = np.asarray(Image.open(path).convert("RGB"))
    classes = {"hairline": (0x23, 0x28, 0x25), "twilight": (0x42, 0x3D, 0x26), "daylight": (0x69, 0x58, 0x26),
               "accent": hexrgb(accent)}
    step = 0.1
    labels = [classify(at(img, R_RING, i * step), classes) for i in range(int(360 / step))]
    print("%s\n  ring:" % path)
    for name, a0, a1 in runs(labels, step):
        if a1 - a0 >= 0.25:
            print("    %-9s %7.1f .. %6.1f   (%5.1f deg, %4.0f min)" % (name, a0, a1, a1 - a0, (a1 - a0) * 4))
    lit = [at(img, R_MARK, i * step).max() > 40 for i in range(int(360 / step))]
    print("  marks (lit just outside the ring):")
    i = 0
    n = len(lit)
    while i < n:
        if lit[i]:
            j = i
            while j < n and lit[j]:
                j += 1
            mid = (i + j - 1) / 2 * step
            rgb = at(img, R_MARK, mid)
            print("    %6.1f .. %6.1f  middle %6.2f  #%02X%02X%02X" % (i * step, (j - 1) * step, mid, *[int(round(c)) for c in rgb]))
            i = j
        else:
            i += 1
    core = np.all(np.abs(img.astype(int) - np.array([0xFF, 0xF1, 0xC9])) <= 12, axis=2)
    ys, xs = np.nonzero(core)
    if len(xs) >= 8:
        x, y = xs.mean() + 0.5, ys.mean() + 0.5
        ang = math.degrees(math.atan2(x - CX, CY - y)) % 360
        print("  sun: %d px, middle (%.1f, %.1f): %.2f deg, radius %.1f px" % (len(xs), x, y, ang, math.hypot(x - CX, y - CY)))
    else:
        print("  sun: none of the core's colour (night, or always-on)")


def main():
    args = [a for a in sys.argv[1:] if "=" not in a]
    if not args:
        sys.exit(__doc__)
    opts = dict(a.split("=", 1) for a in sys.argv[1:] if "=" in a)
    for path in args:
        measure(path, opts.get("accent", "#F0B429"))


if __name__ == "__main__":
    main()
