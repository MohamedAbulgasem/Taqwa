"""sheet.py <out.png> <title> <label:png>...: the shots side by side, each with its label, and under
them, at 3x, the region x0,y0,x1,y1 of each (REGION env), for the report."""
import os, sys
from PIL import Image, ImageDraw, ImageFont
out, title, items = sys.argv[1], sys.argv[2], [a.rsplit(":", 1) for a in sys.argv[3:]]
region = tuple(int(v) for v in os.environ.get("REGION", "0,0,0,0").split(","))
shots = [(l, Image.open(p).convert("RGB")) for l, p in items]
w, h = shots[0][1].size
zoom = None
if region[2] > region[0]:
    zw, zh = (region[2] - region[0]) * 3, (region[3] - region[1]) * 3
    zoom = (zw, zh)
font = ImageFont.load_default()
cols = len(shots)
cw = max(w, zoom[0] if zoom else 0)
H = 30 + 20 + h + (zoom[1] + 10 if zoom else 0) + 10
sheet = Image.new("RGB", (cols * (cw + 10) + 10, H), (40, 40, 40))
d = ImageDraw.Draw(sheet)
d.text((10, 8), title, fill=(230, 230, 230), font=font)
for i, (label, img) in enumerate(shots):
    x = 10 + i * (cw + 10)
    d.text((x, 30), label, fill=(230, 230, 230), font=font)
    sheet.paste(img, (x, 50))
    if zoom:
        z = img.crop(region).resize(zoom, Image.NEAREST)
        sheet.paste(z, (x, 50 + h + 10))
sheet.save(out)
print(out)
