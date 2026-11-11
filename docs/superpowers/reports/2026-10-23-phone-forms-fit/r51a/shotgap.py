"""shotgap.py <repo> <fonts> <png> <text> [units=<u>]: r51/shotgap.py for Ruling 51a's steps. In a 454
px awake shot of Arch, the least distance (face px) from the Gregorian line's ink (bright grey) to the
amber stroke's pixels, beside date-fit's model of the same form at the step its length and its
month pick (ARCH_GREGORIAN_STEPS, date-fit.py's step_for()), and the ink's x extent in the shot.
units=: the model at that one size instead."""
import importlib.util, os, sys
import numpy as np
from PIL import Image
REPO, fonts, png, text = sys.argv[1:5]
units = next((float(a[len("units="):]) for a in sys.argv[5:] if a.startswith("units=")), None)
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
a = np.asarray(Image.open(png).convert("RGB")).astype(int)
s = 450 / a.shape[1]
r, g, b = a[..., 0], a[..., 1], a[..., 2]
ink = (r > 150) & (b > 150) & (abs(r - b) < 30)
amber = (r > 150) & (b < 90) & (r - b > 100)
if units is None:
    step = D.step_for(D.arch_steps(fonts), text)
    name, line = step.name, step.line
else:
    name, line = "one size", D.Line("greg", True, fonts, units * D.K)
base = line.baselines[("arch", False)]
rows = np.arange(a.shape[0]) * s
band = (rows > base - 40) & (rows < base + 14)
iy, ix = np.nonzero(ink & band[:, None])
ay, ax = np.nonzero(amber)
P = np.stack([ix * s, iy * s], 1); Q = np.stack([ax * s, ay * s], 1)
d = np.sqrt(((P[:, None, :] - Q[None, :, :]) ** 2).sum(-1)).min()
print("%s (%d chars, step %s, %.2f px): in the shot %.1f px between the line's ink and the stroke's pixels; model %.1f px;"
      " the ink x %.1f..%.1f" % (text, len(text), name, line.size, d, D.exact(text, line, base), P[:, 0].min(), P[:, 0].max()))
