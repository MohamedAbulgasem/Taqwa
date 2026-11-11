"""The splash's icon in a shot: the box of every pixel brighter than the black around it, its centre,
and its colours. python3 -I disc.py <shot.png>"""
import sys
from collections import Counter
from PIL import Image

im = Image.open(sys.argv[1]).convert("RGB")
px = im.load()
W, H = im.size
lit = [(x, y) for y in range(H) for x in range(W) if max(px[x, y]) > 40]
xs = [p[0] for p in lit]
ys = [p[1] for p in lit]
print("%s %dx%d: icon box %dx%d px, centre (%.1f, %.1f)" % (
    sys.argv[1].split("/")[-1], W, H, max(xs) - min(xs) + 1, max(ys) - min(ys) + 1,
    (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2))
print("colours:", Counter(px[x, y] for x, y in lit).most_common(3))
