"""shotgap.py <repo> <fonts> <png> <text>: in a 454 px awake shot of Arch, the least distance (face px)
from the Gregorian line's ink (bright grey) to the amber stroke's pixels, against date-fit's model."""
import importlib.util, os, sys
import numpy as np
from PIL import Image
REPO, fonts, png, text = sys.argv[1:5]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
a = np.asarray(Image.open(png).convert("RGB")).astype(int)
s = 450 / a.shape[1]
r, g, b = a[..., 0], a[..., 1], a[..., 2]
ink = (r > 150) & (b > 150) & (abs(r - b) < 30)
amber = (r > 150) & (b < 90) & (r - b > 100)
line = D.Line("greg", True, fonts)
base = line.baselines[("arch", False)]
rows = np.arange(a.shape[0]) * s
band = (rows > base - 40) & (rows < base + 14)
iy, ix = np.nonzero(ink & band[:, None])
ay, ax = np.nonzero(amber)
P = np.stack([ix * s, iy * s], 1); Q = np.stack([ax * s, ay * s], 1)
d = np.sqrt(((P[:, None, :] - Q[None, :, :]) ** 2).sum(-1)).min()
print("%s: in the shot %.1f px between the line's ink and the stroke's pixels; model (ink to the stroke's edge) %.1f px" % (
    text, d, D.exact(text, line, base)))
# the model's own curve against the shot's amber: the shot's amber pixel nearest each model curve point
C = D.H.CURVE
sel = (C[:, 1] > 110) & (C[:, 1] < 190)
cc = C[sel]
dd = np.sqrt(((cc[:, None, :] - Q[None, :, :]) ** 2).sum(-1)).min(1)
print("  the model's stroke centre line (y 110-190) to the shot's nearest amber pixel: median %.1f px, max %.1f px" % (np.median(dd), dd.max()))
# where the shot's stroke centre is at a few heights, left side, against the model's
for y in (130, 140, 150, 160, 170):
    rowsel = np.abs(ay * s - y) < 0.6
    xs = ax[rowsel] * s
    left = xs[xs < 225]
    m = C[(np.abs(C[:, 1] - y) < 0.3) & (C[:, 0] < 225)]
    print("  y %d: shot's amber x %.1f..%.1f (centre %.1f), model centre x %.1f" % (y, left.min(), left.max(), (left.min() + left.max()) / 2, m[:, 0].mean()))
