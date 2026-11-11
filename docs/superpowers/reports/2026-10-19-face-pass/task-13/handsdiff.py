"""handsdiff.py <committed dir> <new dir>: for each Moon always-on pass frame in both, the pixels that
differ inside the hands' footprint (the hour and minute hands' diamonds at the frame's minute, faces.js
moonFace hand(), widened by 6 px for the old outline's round joins, the keyline and the edge, and the
cap) and outside it. Outside it, as task-12/passdiff.py does, the moon's picture (the source draws it
for the middle of the step it is asked at) and, with the slots filled, the step count's box are set
aside; what is left is counted, with how many differ by more than 2 levels in 255."""
import glob, math, os, sys
import numpy as np
from PIL import Image, ImageDraw

K = 2.25
S = 454 / 450

def footprint(hh, mm, size=454):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    for deg, (tip, half, tail) in (((hh % 12) * 30 + mm / 2, (52, 4.2, 9)), (mm * 6, (20, 3.1, 10))):
        pts = [(100, tip), (100 + half, 96), (100, 100 + tail), (100 - half, 96)]
        a = math.radians(deg)
        out = []
        for x, y in pts:
            x, y = (x - 100) * K, (y - 100) * K
            out.append(((225 + x * math.cos(a) - y * math.sin(a)) * S, (225 + x * math.sin(a) + y * math.cos(a)) * S))
        d.polygon(out, fill=255)
        for p, q in zip(out, out[1:] + out[:1]):
            d.line([p, q], fill=255, width=13)
        for x, y in out:
            d.ellipse((x - 6.5, y - 6.5, x + 6.5, y + 6.5), fill=255)
    r = (4.4 * K + 4) * S
    d.ellipse((227 - r, 227 - r, 227 + r, 227 + r), fill=255)
    return np.asarray(m) > 0

def set_aside(name, size=454):
    """passdiff.py's: the moon's picture, and the step count's value with the slots filled."""
    m = np.zeros((size, size), bool)
    m[268:331, 200:257] = True
    if "-slots-" in name:
        m[370:443, 236:346] = True
    return m

old_dir, new_dir = sys.argv[1], sys.argv[2]
for new in sorted(glob.glob(os.path.join(new_dir, "pass-moon-*-aod.png"))):
    name = os.path.basename(new)
    old = os.path.join(old_dir, name)
    if not os.path.exists(old):
        print("%-40s not in the committed pass" % name)
        continue
    t = name.split("-")[-2]
    hands = footprint(int(t[:2]), int(t[2:]))
    a = np.asarray(Image.open(old).convert("RGB")).astype(int)
    b = np.asarray(Image.open(new).convert("RGB")).astype(int)
    diff = np.abs(a - b).max(axis=2)
    aside = set_aside(name)
    outside = (diff > 0) & ~hands & ~aside
    worst = diff[outside].max() if outside.any() else 0
    print("%-40s differ %5d: in the hands' footprint %5d (over 8 levels %4d); set aside %3d; left %4d, worst %3d, over 2 levels %d" % (
        name, (diff > 0).sum(), ((diff > 0) & hands).sum(), ((diff > 8) & hands).sum(), ((diff > 0) & aside & ~hands).sum(),
        outside.sum(), worst, ((diff > 2) & outside).sum()))
