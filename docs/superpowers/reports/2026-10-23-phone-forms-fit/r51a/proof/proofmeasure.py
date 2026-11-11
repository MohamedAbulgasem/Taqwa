"""Scratch (Ruling 51a's first step): reads, from each shot of Arch's Arabic Gregorian line, the size
the face drew it at. Two measures: (1) the line's ink, x and y extent in the band, against
date-fit.py's model of the same text at 14, 13 and 11.25 units (the model is never narrower than
the emulator's ink, wider by 2.6 to 6.6 px in these shots, and the three sizes' widths are 14 px or
more apart, so the nearest is the size drawn); (2) the shot against an earlier
shot of the same text whose size is known (r51/shots: the base face's sample at 14 units, the
three-step face's cases), pixel for pixel in the line's band.
    proofmeasure.py <repo> <fonts dir> <proof shots dir>"""
import importlib.util, os, sys
import numpy as np
from PIL import Image
REPO, fonts, shots = sys.argv[1:4]
EV = os.path.join(REPO, "docs/superpowers/reports/2026-10-23-phone-forms-fit/r51/shots")
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
K = D.K
LINES = {u: D.Line("greg", True, fonts, u * K) for u in (14, 13, 11.25)}
CASES = [
    ("sample", "الأحد ١٨ أكتوبر", 14, [("old/arch-ar-sa-1458", 14), ("new/greg-sample", 13)]),
    ("sat-aug-1", "السبت ١ أغسطس", 13, [("new/greg-sat-aug-1", 14)]),
    ("thu-aug-10", "الخميس ١٠ أغسطس", 13, [("new/greg-thu-aug-10", 13)]),
    ("thu-aug-10-latn", "الخميس 10 أغسطس", 13, []),
    ("wed-dec-30", "الأربعاء ٣٠ ديسمبر", 14, []),
    ("tue-oct-10", "الثلاثاء ١٠ تشرين الأول", 11.25, [("new/greg-tue-oct-10", 11.25)]),
]
BAND = (125, 180)   # face px rows the Gregorian line can reach (y 118..190 boxes; Hijri ends above)


def load(p):
    return np.asarray(Image.open(p).convert("RGB")).astype(int)


def ink(a, mode):
    s = 450 / a.shape[1]
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    lo = 150 if mode == "awake" else 70
    m = (np.minimum(np.minimum(r, g), b) > lo) & ((np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)) < 30)
    rows = np.arange(a.shape[0]) * s
    cols = np.arange(a.shape[1]) * s
    m &= ((rows > BAND[0]) & (rows < BAND[1]))[:, None] & ((cols > 60) & (cols < 390))[None, :]
    ys, xs = np.nonzero(m)
    return xs.min() * s, (xs.max() + 1) * s, ys.min() * s, (ys.max() + 1) * s


def band_diff(a, b):
    s = 450 / a.shape[1]
    rows = np.arange(a.shape[0]) * s
    sel = (rows > BAND[0]) & (rows < BAND[1])
    d = np.abs(a[sel] - b[sel]).max(-1)
    return int((d > 2).sum()), int(d.max())


ok = True
for name, text, want, refs in CASES:
    model = {}
    for u, line in LINES.items():
        adv, l, r, t, b = D.layout(text, line)
        model[u] = (r - l, b - t)
    for mode in ("awake", "aod"):
        a = load(os.path.join(shots, "greg-%s-%s.png" % (name, mode)))
        x0, x1, y0, y1 = ink(a, mode)
        w = x1 - x0
        drawn = min(model, key=lambda u: abs(model[u][0] - w))
        ok &= drawn == want
        print("%-16s %-24s %-5s ink %5.1f px wide (x %5.1f..%5.1f, y %5.1f..%5.1f); model 14u %5.1f, 13u %5.1f, 11.25u %5.1f"
              " -> drawn at %g units, rule A says %g: %s" % (
                  name, text, mode, w, x0, x1, y0, y1, model[14][0], model[13][0], model[11.25][0], drawn, want,
                  "OK" if drawn == want else "WRONG"))
        for ref, size in refs:
            p = os.path.join(EV, "%s-%s.png" % (ref, mode))
            n, mx = band_diff(a, load(p))
            same = n == 0
            print("    against r51/shots/%s-%s.png (%g units): %d px of the band differ over 2 levels (max %d) -> %s" % (
                ref, mode, size, n, mx, ("equal" if same else "different")))
            if size == want and not same:
                ok = False
            if size != want and same:
                ok = False
print("PROOF %s: the face's subText(...) == \"أغسطس\" picks each case's size, awake and always-on" % ("PASSES" if ok else "FAILS"))
sys.exit(0 if ok else 1)
