"""Remakes a pass shot's mockup side-by-side (face-pass task-10/<name>-vs-mockup.png) with a new
shot, keeping the mockup panel as committed, since the face's change does not change faces.js:

    python3 -I vsmockup.py <committed side-by-side> <shot.png> <out.png>

The picture is laid out as tools/watchface/compare.py <ref> <shot> <out> lays it out, with no crop
or zoom: the mockup (454 px) on the left, a 12 px gap in (40, 40, 40), and the shot on the right,
undithered when its name ends in -aod.png. A run on the committed shot gives back the committed
picture, pixel for pixel (P2.2 review round 1's check), so the left panel is ref.py's mockup."""
import os, sys

import numpy as np
from PIL import Image

old, shot, out = sys.argv[1:4]
sheet = np.asarray(Image.open(old).convert("RGB"))
b = np.asarray(Image.open(shot).convert("RGB")).astype(int)
w = b.shape[1]
if sheet.shape != (b.shape[0], 2 * w + 12, 3):
    sys.exit("%s is not a two-panel compare.py picture of %d px panels" % (old, w))
if os.path.basename(shot).endswith("-aod.png"):
    right = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
    below = np.concatenate([b[1:], b[-1:]], axis=0)
    b = np.maximum(b, np.maximum(right, below))
new = Image.new("RGB", (2 * w + 12, b.shape[0]), (40, 40, 40))
new.paste(Image.fromarray(sheet[:, :w]), (0, 0))
new.paste(Image.fromarray(b.astype(np.uint8), "RGB"), (w + 12, 0))
new.save(out)
same = np.array_equal(np.asarray(new), sheet)
print("%s: %s the committed picture" % (os.path.basename(out), "equals" if same else "differs from"))
