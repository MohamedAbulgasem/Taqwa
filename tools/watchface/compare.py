"""Puts a mockup (left) and an emulator screenshot (right) side by side, optionally cropped and
zoomed, with a third panel of where they differ; and counts the pixels that differ by more than 8
in any channel, with the box they fall in.

    python3 -I tools/watchface/compare.py <ref.png> <shot.png> [<out.png>] [box=x0,y0,x1,y1] [zoom=2] [diff] [undither]
                                          [mask=<mask.png>] [mirror]

Without <out.png> it only counts. Both pictures must be the same size (ref.py draws 454 x 454, the
emulator's screen).

mask is a picture of the same size whose white pixels are left out of the count (and shown grey in
the diff panel): ref.py's textmask, the words of a watch app screen, so that a shot in another
language or clock is compared by its geometry. mirror turns the shot left to right first, for a
screen whose whole layout mirrors in right to left (the Tile's rows), so that it meets the
left-to-right drawing.

undither is for looking at an always-on frame: the emulator draws one as a checkerboard, every
other pixel off, which a picture viewer that scales it shows grey and dim. With it, the shot's
panel fills each dark pixel from its lit neighbour to the right or below, which gives back the
frame's colours as the watch's own screen shows them (the count is made on the shot as taken).
"""
import sys

import numpy as np
from PIL import Image


def main():
    args = [a for a in sys.argv[1:] if "=" not in a and a not in ("diff", "undither", "mirror")]
    if len(args) < 2:
        sys.exit(__doc__)
    opts = dict(a.split("=", 1) for a in sys.argv[1:] if "=" in a)
    ref = Image.open(args[0]).convert("RGB")
    shot = Image.open(args[1]).convert("RGB")
    if "mirror" in sys.argv[1:]:
        shot = shot.transpose(Image.FLIP_LEFT_RIGHT)
    if ref.size != shot.size:
        sys.exit("sizes differ: %s and %s" % (ref.size, shot.size))
    a, b = np.asarray(ref).astype(int), np.asarray(shot).astype(int)
    masked = np.zeros(a.shape[:2], dtype=bool)
    if "mask" in opts:
        mask = Image.open(opts["mask"]).convert("L")
        if mask.size != ref.size:
            sys.exit("the mask is %s, not %s" % (mask.size, ref.size))
        masked = np.asarray(mask) > 127
        print("masked pixels %d (%.2f%%)" % (masked.sum(), 100.0 * masked.mean()))
    differs = (np.abs(a - b).max(axis=2) > 8) & ~masked
    ys, xs = np.nonzero(differs)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1) if len(xs) else None
    print("differing pixels %d (%.2f%%), box %s" % (differs.sum(), 100.0 * differs.mean(), box))
    if len(args) < 3:
        return
    crop = tuple(int(v) for v in opts["box"].split(",")) if "box" in opts else (0, 0, ref.width, ref.height)
    zoom = float(opts.get("zoom", 1))
    shown = shot
    if "undither" in sys.argv[1:]:
        right = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
        below = np.concatenate([b[1:], b[-1:]], axis=0)
        shown = Image.fromarray(np.maximum(b, np.maximum(right, below)).astype(np.uint8), "RGB")
    panels = [ref.crop(crop), shown.crop(crop)]
    if "diff" in sys.argv[1:]:
        heat = np.zeros(a.shape, dtype=np.uint8)
        heat[..., 0] = np.where(differs, 255, (a.max(axis=2) // 4)).astype(np.uint8)
        heat[..., 1] = np.where(differs, 64, (b.max(axis=2) // 4)).astype(np.uint8)
        heat[masked] = 90
        panels.append(Image.fromarray(heat, "RGB").crop(crop))
    if zoom != 1:
        size = (round(panels[0].width * zoom), round(panels[0].height * zoom))
        panels = [p.resize(size, Image.LANCZOS) for p in panels]
    w, h, gap = panels[0].width, panels[0].height, 12
    sheet = Image.new("RGB", (w * len(panels) + gap * (len(panels) - 1), h), (40, 40, 40))
    for i, p in enumerate(panels):
        sheet.paste(p, (i * (w + gap), 0))
    sheet.save(args[2])
    print("wrote", args[2], sheet.size)


if __name__ == "__main__":
    main()
