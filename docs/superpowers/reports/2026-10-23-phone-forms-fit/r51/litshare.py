"""litshare.py <repo> <dir>...: the always-on lit share of every *-aod.png in each dir, as the face
draws it (tools/watchface/lit.py undither: twice the emulator's lit parity, at the levels its
dimming leaves), and the raw dithered share; Google's limit is 15%."""
import glob, importlib.util, os, sys
import numpy as np
from PIL import Image
REPO = sys.argv[1]
spec = importlib.util.spec_from_file_location("lit", os.path.join(REPO, "tools/watchface/lit.py"))
L = importlib.util.module_from_spec(spec); spec.loader.exec_module(L)
worst = (0, "")
for d in sys.argv[2:]:
    for p in sorted(glob.glob(os.path.join(d, "*-aod.png"))):
        im = np.asarray(Image.open(p).convert("RGB")).astype(int)
        face = L.measure(im, undither=True)[0]
        raw = L.measure(im, undither=False)[0]
        worst = max(worst, (face, p))
        print("%-12s %-36s raw %5.2f%%  face %6.2f%%" % (os.path.basename(d), os.path.basename(p), raw, face))
print("highest as the face draws it: %.2f%% (%s), under 15%%: %s" % (worst[0], os.path.relpath(worst[1], os.path.dirname(os.path.dirname(worst[1]))), worst[0] < 15))
