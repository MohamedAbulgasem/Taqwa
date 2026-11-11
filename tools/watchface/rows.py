"""Where the pixels of a colour lie in a screenshot: the runs of rows (y) and of columns (x) that
hold them, so that a text's or a stroke's position can be measured against the mockup's.

    python3 -I tools/watchface/rows.py <png> [box=x0,y0,x1,y1] [colour=#F0B429] [tol=40]

With no colour it measures the face's own classes: ink (#F1F3F1), mist (#8C948F), slate
(#5A625D) and amber (#F0B429), each within its tolerance. A pixel belongs to a class when every
channel is within tol of it.
"""
import sys

import numpy as np
from PIL import Image

CLASSES = {"ink": ("#F1F3F1", 40), "mist": ("#8C948F", 28), "slate": ("#5A625D", 18), "amber": ("#F0B429", 45)}


def runs(values):
    out = []
    for v in values:
        if out and v == out[-1][1] + 1:
            out[-1][1] = v
        else:
            out.append([v, v])
    return [tuple(r) for r in out]


def rgb(hexcolour):
    h = hexcolour.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)])


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    opts = dict(a.split("=", 1) for a in sys.argv[2:] if "=" in a)
    im = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(int)
    x0, y0, x1, y1 = (int(v) for v in opts["box"].split(",")) if "box" in opts else (0, 0, im.shape[1], im.shape[0])
    part = im[y0:y1, x0:x1]
    classes = {opts["colour"]: (opts["colour"], int(opts.get("tol", 40)))} if "colour" in opts else CLASSES
    for name, (hexcolour, tol) in classes.items():
        hit = (np.abs(part - rgb(hexcolour)).max(axis=2) <= tol)
        ys = np.nonzero(hit.any(axis=1))[0] + y0
        xs = np.nonzero(hit.any(axis=0))[0] + x0
        print("%-8s %5d px  rows %s  cols %s" % (name, hit.sum(), runs(ys.tolist())[:10], runs(xs.tolist())[:10]))


if __name__ == "__main__":
    main()
