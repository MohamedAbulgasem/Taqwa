"""The Moon always-on at its worst moment on emulator-5558, before Ruling 46 and as rebuilt under it
(Task 13's closing fix: the moon window's near-black fill black always-on, its hairline kept).

    python3 -I r46sheet.py <out.png> [<before frame> <rebuilt frame>]

By default the frames are Libyan Arabic on twelve hours, both slots filled (100%, the emulator's own
step count) and 28 Jumada al-Ula, at 04:40: worst/'s review round 3 frame (890 steps) and
worst-r46/'s of the same case. Two rows: each frame at its own levels, and at 8 times the light, so
that the fill, 6 to 8 of 255 on the emulator, can be seen at all; both as the eye gets them (each
dark pixel of the emulator's checkerboard filled from its lit neighbour, for viewing only). Under
each, the lit share as the face draws it (tools/watchface/lit.py undither).
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *[os.pardir] * 5))
sys.path.insert(0, os.path.join(REPO, "tools", "watchface"))
import lit  # noqa: E402

FONT = os.path.join(REPO, "watchface", "src", "main", "res", "font", "manrope_semibold.ttf")
BEFORE = os.path.join(HERE, "worst", "moon-ar-ly-12h-slots-most-890-0440-aod.png")
REBUILT = os.path.join(HERE, "worst-r46", "moon-ar-ly-12h-slots-most-0440-aod.png")
COLUMNS = ["before Ruling 46 (the fill lit)", "as rebuilt (the fill black)"]
GAIN = 8


def viewed(b):
    """Each black pixel filled from its neighbour to the right or below (compare.py undither): for the eye."""
    r = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
    d = np.concatenate([b[1:], b[-1:]], axis=0)
    return np.maximum(b, np.maximum(r, d))


def main():
    if len(sys.argv) not in (2, 4):
        sys.exit(__doc__)
    paths = sys.argv[2:4] if len(sys.argv) == 4 else [BEFORE, REBUILT]
    frames = [np.asarray(Image.open(p).convert("RGB")).astype(int) for p in paths]
    size, label, top = 454, 22, 30
    sheet = Image.new("RGB", (2 * size + 3 * 10, top + 2 * (size + label + 10)), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype(FONT, 15)
    for i, name in enumerate(COLUMNS):
        draw.text((10 + i * (size + 10), 8), name, fill=(230, 230, 230), font=font)
    for r, gain in enumerate((1, GAIN)):
        y = top + r * (size + label + 10)
        for i, b in enumerate(frames):
            x = 10 + i * (size + 10)
            sheet.paste(Image.fromarray(np.clip(viewed(b) * gain, 0, 255).astype("uint8")), (x, y))
            share = lit.measure(b, True)[0]
            what = "its own levels" if gain == 1 else "%d x the light" % gain
            draw.text((x, y + size + 3), "%s: %.2f%% lit" % (what, share), fill=(230, 230, 230), font=font)
            if r == 0:
                print("%s: %.2f%%" % (os.path.basename(paths[i]), share))
    sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
