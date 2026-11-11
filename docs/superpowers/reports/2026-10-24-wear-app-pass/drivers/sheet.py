"""A contact sheet: sheet.py <out.png> <cols> <scale> <png>... , each tile labelled with its name."""
import os
import sys

from PIL import Image, ImageDraw

out, cols, scale = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
files = sys.argv[4:]
size = int(454 * scale)
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (cols * size, rows * (size + 14)), (40, 40, 40))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    im = Image.open(f).convert("RGB").resize((size, size))
    x, y = (i % cols) * size, (i // cols) * (size + 14)
    sheet.paste(im, (x, y + 14))
    label = os.path.basename(os.path.dirname(f)) + "/" + os.path.basename(f)
    d.text((x + 2, y + 1), label[-60:], fill=(255, 255, 0))
sheet.save(out)
