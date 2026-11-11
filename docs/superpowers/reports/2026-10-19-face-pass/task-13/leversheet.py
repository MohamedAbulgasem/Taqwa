"""The two fill levers for Ruling 41's 15% call, side by side (Task 13's fix round 3, after review
round 3). Neither lever was built then, and the face drew the first column. Ruling 46 chose the
second column, the moon window's fill black, which the closing fix built: so the drawing here is
ref.py window=filled, the face before the ruling, and the sheet is the one the call was made on.

    python3 -I leversheet.py <out.png>

Columns: the Moon always-on as built; the moon window's fill (#070908) black; the date window's
fill (WELL, #0B0D0C) black too. Each window keeps its hairline stroke.
Rows:
1. the drawing (ref.py aod sec=0, face=moon-d) at its worst moment, Arabic 12h at 20:55 with both
   slots wide (100%, 12,345) and 28 جمادى الأولى, at its own levels: what the eye gets;
2. the same at 8 times the light, so that the two fills, 7 to 13 of 255, can be seen at all;
3. review round 3's emulator frame (the face of 97779b42) at 04:40, ordinary slot values (100%,
   890 steps), with the fills' own pixels black, dithered as the emulator draws it, at 8 times the
   light.
Under each, the lit share (tools/watchface/lit.py: a pixel lit when any channel of the face's
colour is over 8; the emulator's frame undithered).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *[os.pardir] * 5))
sys.path.insert(0, os.path.join(REPO, "tools", "watchface"))
import lit  # noqa: E402

REF = os.path.join(REPO, "tools", "watchface", "ref.py")
FONT = os.path.join(REPO, "watchface", "src", "main", "res", "font", "manrope_semibold.ttf")
FRAME = os.path.join(HERE, "worst", "moon-ar-ly-12h-slots-most-890-0440-aod.png")
DRAWING = ["face=moon-d", "aod", "sec=0", "t=20:55", "lang=ar", "clock=12", "hijri=28 جمادى الأولى",
           "comps", "v4=100", "v5=12,345", "window=filled"]
FILL = {"drawing": [(7, 9, 8), (11, 13, 12)], "emulator": [(6, 8, 7), (9, 11, 10)]}
COLUMNS = ["as built", "moon window's fill black", "the date window's fill black too"]
GAIN = 8


def black(b, fills):
    b = b.copy()
    for fill in fills:
        b[(b == fill).all(axis=2)] = 0
    return b


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "moon.png")
        subprocess.run([sys.executable, "-I", REF, out] + DRAWING, check=True, capture_output=True, timeout=60)
        drawing = np.asarray(Image.open(out).convert("RGB")).astype(int)
    frame = np.asarray(Image.open(FRAME).convert("RGB")).astype(int)
    rows = []
    for kind, b, undither, gains in (("drawing", drawing, False, (1, GAIN)), ("emulator", frame, True, (GAIN,))):
        for gain in gains:
            row = []
            for n in range(3):
                c = black(b, FILL[kind][:n])
                row.append((np.clip(c * gain, 0, 255).astype("uint8"), lit.measure(c, undither)[0], kind, gain))
            rows.append(row)
    size, label, top = 454, 22, 30
    sheet = Image.new("RGB", (3 * size + 4 * 10, top + len(rows) * (size + label + 10)), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype(FONT, 15)
    for i, name in enumerate(COLUMNS):
        draw.text((10 + i * (size + 10), 8), name, fill=(230, 230, 230), font=font)
    for r, row in enumerate(rows):
        y = top + r * (size + label + 10)
        for i, (im, share, kind, gain) in enumerate(row):
            x = 10 + i * (size + 10)
            sheet.paste(Image.fromarray(im), (x, y))
            what = "%s, %s" % (kind, "its own levels" if gain == 1 else "%d x the light" % gain)
            draw.text((x, y + size + 3), "%s: %.2f%% lit" % (what, share), fill=(230, 230, 230), font=font)
    sheet.save(sys.argv[1])
    for row in rows:
        print("  ".join("%s x%d %.2f%%" % (kind, gain, share) for _, share, kind, gain in row))


if __name__ == "__main__":
    main()
