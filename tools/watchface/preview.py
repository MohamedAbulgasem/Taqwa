"""Makes the face picker's preview (watchface/src/main/res/drawable/preview.png) from an awake
screenshot: 450 x 450, round, the corners transparent. The preview is the default style, Arch, at
14:58 in Amber, as the mockups draw the face.

    python3 -I tools/watchface/preview.py <awake shot.png>
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(os.path.dirname(HERE)), "watchface", "src", "main", "res", "drawable", "preview.png")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    im = Image.open(sys.argv[1]).convert("RGB").resize((450, 450), Image.LANCZOS)
    big = 4
    mask = Image.new("L", (450 * big, 450 * big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 450 * big - 1, 450 * big - 1), fill=255)
    out = Image.new("RGBA", (450, 450), (0, 0, 0, 0))
    out.paste(im, (0, 0))
    out.putalpha(mask.resize((450, 450), Image.LANCZOS))
    out.save(DST, optimize=True)
    print("wrote", DST, out.size)


if __name__ == "__main__":
    main()
