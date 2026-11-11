"""The drawn pill's box in a shot: the amber pixels inside a search box (x0 y0 x1 y1).

    python3 -I pill.py <shot.png> x0 y0 x1 y1
"""
import sys
from PIL import Image

img = Image.open(sys.argv[1]).convert("RGB")
x0, y0, x1, y1 = map(int, sys.argv[2:6])
x0, y0 = max(x0, 0), max(y0, 0)
x1, y1 = min(x1, img.width), min(y1, img.height)
xs, ys = [], []
for y in range(y0, y1):
    for x in range(x0, x1):
        r, g, b = img.getpixel((x, y))
        if r > 120 and r > g > b and r - b > 70:
            xs.append(x)
            ys.append(y)
if not xs:
    print("no amber in the box")
else:
    print(f"pill [{min(xs)},{min(ys)}][{max(xs) + 1},{max(ys) + 1}] size {max(xs) + 1 - min(xs)} x {max(ys) + 1 - min(ys)}")
