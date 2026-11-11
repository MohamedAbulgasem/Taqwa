"""abdiff.py <dir a> <dir b>: for each png in both, how many pixels differ, the largest difference, and
their box (the Moon's seconds hand moves between shots, so its frames are told apart)."""
import glob, os, sys
import numpy as np
from PIL import Image
a_dir, b_dir = sys.argv[1], sys.argv[2]
for a in sorted(glob.glob(os.path.join(a_dir, "*.png"))):
    b = os.path.join(b_dir, os.path.basename(a))
    if not os.path.exists(b):
        print("%-36s MISSING" % os.path.basename(a)); continue
    x = np.asarray(Image.open(a).convert("RGB")).astype(int); y = np.asarray(Image.open(b).convert("RGB")).astype(int)
    d = np.abs(x - y).max(axis=2)
    n = int((d > 0).sum())
    if n:
        rows, cols = np.nonzero(d)
        print("%-36s %6d px differ, worst %3d levels, box (%d, %d, %d, %d)" % (os.path.basename(a), n, d.max(), cols.min(), rows.min(), cols.max(), rows.max()))
    else:
        print("%-36s      0 px differ" % os.path.basename(a))
