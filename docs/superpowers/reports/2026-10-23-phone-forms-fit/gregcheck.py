import importlib.util, os, sys
import numpy as np
from PIL import Image
REPO = sys.argv[1]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
def shot_ink(png, y0, y1):
    a = np.asarray(Image.open(png).convert("RGB")).astype(int)
    # the ink colour F1F3F1: bright and grey (not the amber arch)
    m = (a[:, :, 0] > 150) & (a[:, :, 2] > 150) & (abs(a[:, :, 0] - a[:, :, 2]) < 30)
    s = 450 / a.shape[1]
    rows = [r for r in range(a.shape[0]) if y0 <= r * s <= y1 and m[r].any()]
    xs = [c for r in rows for c in np.nonzero(m[r])[0]]
    return min(rows) * s, (max(rows) + 1) * s, min(xs) * s, (max(xs) + 1) * s
def model_ink(text, line, base):
    adv, l, r, t, b = D.layout(text, line)
    x0 = D.FC.C - adv / 2
    return base + t, base + b, x0 + l, x0 + r
fonts = sys.argv[2]
for png, text, rtl, base_key in [
    ("docs/superpowers/reports/2026-10-19-face-pass/pass-arch-ar-ly-12h-1458-awake.png", "الأحد 18 أكتوبر", True, ("arch", False)),
    ("docs/superpowers/reports/2026-10-19-face-pass/pass-arch-en-1458-awake.png", "SUN 18 OCT", False, ("arch", False)),
    ("docs/superpowers/reports/2026-10-19-face-pass/pass-ring-ar-sa-1458-awake.png", "الأحد ١٨ أكتوبر", True, ("ring", False)),
    ("docs/superpowers/reports/2026-10-19-face-pass/pass-ring-en-1458-awake.png", "SUN 18 OCT", False, ("ring", False)),
]:
    line = D.Line("greg", rtl, fonts)
    base = line.baselines[base_key]
    print(text, base_key)
    print("  shot  top %.1f bottom %.1f left %.1f right %.1f" % shot_ink(os.path.join(REPO, png), base - 40, base + 14))
    print("  model top %.1f bottom %.1f left %.1f right %.1f" % model_ink(text, line, base))
