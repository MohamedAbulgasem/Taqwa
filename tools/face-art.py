#!/usr/bin/env python3
"""Draws the pictures the Taqwa watch face (:watchface) cannot draw with Watch Face Format's own
shapes. The format has lines, ellipses, arcs and rectangles, a radial gradient that cannot take a
colour from the face's configuration, and no free path, so anything with a curve or a glow of its
own is a PNG in watchface/src/main/res/drawable, white on transparent and tinted by the face to the
colour it needs.

    python3 tools/face-art.py            # draws every picture below, and rewrites those that changed
    python3 tools/face-art.py --check    # says whether every picture is up to date, and changes nothing

A picture is compared by what it decodes to: its pixels, its mode and its size. The bytes are not
compared, because the PNG encoder writes the same pixels as other bytes on another machine (this
Mac mini's Pillow does for arch_mark.png and glow.png), and the watch only ever sees the pixels. So a
run rewrites only a picture whose pixels, mode or size differ, and --check exits 1 if any does, or if
one is missing, writing nothing.

    arch_mark.png   the app icon's arch (ic_launcher_monochrome.xml, a 1024 grid), apex up, 88 grid
                    units of round stroke: the small mark above the dates. Shown in a 24 x 30 box
                    (watchface.xml: PartImage x=213 y=52), the apex's path 2 units down from the top
                    and in the middle, so that the box can sit on whole numbers. Its lamp, the icon's
                    dot, is an ellipse in the accent and is not in the picture.
    glow.png        a soft round glow, 36 x 36 units: the point of light that rides the Ring (and,
                    for Arch, the lamp under its crown, drawn 58 across). faces.js `tw-glint-*`:
                    opacity 75% at the centre, 22% at 45% of the radius, none at the edge. White
                    here; the face tints it with the accent's light tint.
    arch_track.png  the Arch style's outline: faces.js ARCH2, the icon's arch as the face's frame,
    arch_track_aod.png  both sides from the apex to the feet on the screen's edge, 1.4 units of
                    faces.js (3.15 face units) awake and 1 (2.25) always-on, with butt ends, as
                    faces.js strokes it. Shown in a 364 x 344 box at x=43 y=16, tinted slate. (A
                    vector drawable would be exact at any size, but Watch Face Push's validator
                    finds no picture in one and refuses the face.)
    arch_niche.png  the faint light of the Arch's lamp inside its niche, 360 x 342 units, shown at
                    x=45 y=18 (the arch's feet to its apex): faces.js `tw-niche-*`, a radial
                    gradient about the lamp (100, 33), 135 units out, opacity 11% at the centre,
                    3.5% at 45% and none at the edge, kept inside the arch's line and above its
                    feet. White here; the face tints it with the accent. One pixel to a face unit
                    is plenty for a light this soft, whose edge is under the arch's stroke.

    moon_mark.png   the Moon style's mark at twelve, where a watchmaker puts the maker's mark:
    moon_mark_aod.png  the same arch 13 units tall with a 2.1-unit stroke awake and 1.4 always-on
                    (faces.js moonFace), white in a 30 x 38 box at x=210 y=16, the apex 4.25 down.
    hand_hour.png   the Moon's dauphine hands (faces.js moonFace hand()): the lit facet #F4F5F2
    hand_minute.png left of the spine and the shaded one #A9AFAB right of it, inside a fine black
                    keyline, in their own colours (no tint). Shown pointing at twelve in the boxes
                    HOUR_HAND and MINUTE_HAND give, which the face's AnalogClock turns about the
                    face's centre.
    hand_hour_back.png  each hand's black back, for always-on (Ruling 41): the awake hand's own shape,
    hand_minute_back.png  keyline and all, in black. The face dims the awake hand always-on and lays
                    it on its back, so that the hand stays opaque: its keyline black and its facets
                    at the dimmed level, with nothing under it showing through.
    hand_second.png the neutral seconds hand: a mist line with its counterweight (SECOND_BOX).
    moon_pointer.png  the chapter arc's pointer, a small triangle across the arc at twelve, white
                    for the accent's tint, which the face turns to the next prayer (POINTER_BOX).

The shapes are the ones faces.js draws (docs/superpowers/specs/2026-10-19-watch-companion): the
arch's crown is two cubic curves meeting at the apex, which is a point, and its feet are straight.
Needs Pillow and numpy (python3 -m pip install pillow numpy). The pixels are the same on every run.
scripts/face-gate.sh runs --check before every release.
"""
import math
import os
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE = os.path.join(ROOT, "watchface", "src", "main", "res", "drawable")

# The face is 450 units across and faces.js draws 200: its units are 2.25 of the face's.
FACE_PER_DRAWING = 2.25
# Every picture is drawn at this many pixels to the face unit, so that a watch whose screen is a
# little larger than 450 pixels still has pixels to spare.
PX_PER_UNIT = 2
SUPERSAMPLE = 8


def cubic(p0, p1, p2, p3, steps=64):
    """Points along a cubic Bezier curve, p0 to p3."""
    points = []
    for i in range(steps + 1):
        t = i / steps
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
        points.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0],
                       a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return points


def arch_mark(box_w=24, box_h=30, apex_y=2.0, tall=11, stroke=88):
    """The arch as a white PNG in a box_w x box_h face-unit box, its apex's path apex_y units down
    and in the middle: `tall` of faces.js's units from the apex to the feet, with a round stroke
    `stroke` units of the icon's grid wide. The Ring and Sun's mark is the default; the Moon's, at
    twelve, is 13 tall (faces.js moonFace: archMark(100, 9, 13, ..., 2.1), 1.4 always-on)."""
    grid_height = 564         # the arch, apex to feet, on the icon's grid
    half_width = 220          # the feet stand 220 either side of the apex
    # The mark's height in faces.js's units fixes how big a grid unit is on the face.
    face_per_grid = tall * FACE_PER_DRAWING / grid_height

    w, h = box_w * PX_PER_UNIT, box_h * PX_PER_UNIT
    scale = face_per_grid * PX_PER_UNIT * SUPERSAMPLE        # big pixels to a grid unit
    ox, oy = w * SUPERSAMPLE / 2, apex_y * PX_PER_UNIT * SUPERSAMPLE

    def at(x, y):
        return (ox + x * scale, oy + y * scale)

    path = [at(-half_width, grid_height), at(-half_width, 304)]
    path += cubic(at(-half_width, 304), at(-half_width, 156), at(-132, 52), at(0, 0))[1:]
    path += cubic(at(0, 0), at(132, 52), at(half_width, 156), at(half_width, 304))[1:]
    path += [at(half_width, grid_height)]

    mask = Image.new("L", (w * SUPERSAMPLE, h * SUPERSAMPLE), 0)
    draw = ImageDraw.Draw(mask)
    r = stroke * scale / 2
    # A round stroke is a disc dragged along the path: stamp one every half pixel (of the big
    # drawing), which leaves no corner where the segments meet, and round caps for free.
    for (x0, y0), (x1, y1) in zip(path, path[1:]):
        steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
        for i in range(steps + 1):
            t = i / steps
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            draw.ellipse((x - r, y - r, x + r, y + r), fill=255)
    mask = mask.resize((w, h), Image.LANCZOS)

    image = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    image.putalpha(mask)
    return image


def glow():
    """A round glow, white, 36 x 36 face units, opacity 75% at the centre, 22% at 45% of the radius
    and 0 at the edge (faces.js `tw-glint-*`), linear between the stops."""
    size = 36 * PX_PER_UNIT
    ys, xs = np.mgrid[0:size, 0:size]
    # distance from the centre as a fraction of the radius, at each pixel's centre
    d = np.hypot(xs + 0.5 - size / 2, ys + 0.5 - size / 2) / (size / 2)
    alpha = np.interp(d, [0.0, 0.45, 1.0], [0.75, 0.22, 0.0])
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def arch_track(stroke):
    """The Arch's outline, white, `stroke` faces.js units wide, butt ends, in the 364 x 344 face-unit
    box whose corner is the face's (43, 16). Each side is a polygon: the path, apex to foot, pushed
    out half the stroke either way along its normals."""
    box_x, box_y, box_w, box_h = 43, 16, 364, 344
    k = FACE_PER_DRAWING
    big = PX_PER_UNIT * SUPERSAMPLE
    c1y, c2y = round(118 - 0.487 * 110, 2), round(118 - 0.829 * 110, 2)
    half = stroke * k / 2
    mask = Image.new("L", (box_w * big, box_h * big), 0)
    draw = ImageDraw.Draw(mask)
    for side in (1, -1):
        x = lambda u: 100 + side * (u - 100)
        crown = cubic((100, 8), (x(148), c2y), (x(180), c1y), (x(180), 118), steps=2000)
        path = [(px * k, py * k) for px, py in crown] + [(x(180) * k, 160 * k)]
        pts = np.array(path)
        # the tangent at each point (the crown's own at its ends; the foot is straight down)
        tangent = np.gradient(pts, axis=0)
        tangent[-2:] = (0, 1)
        tangent /= np.hypot(tangent[:, 0], tangent[:, 1])[:, None]
        normal = np.stack([-tangent[:, 1], tangent[:, 0]], axis=1)
        outline = np.concatenate([pts + normal * half, (pts - normal * half)[::-1]])
        draw.polygon([((px - box_x) * big, (py - box_y) * big) for px, py in outline], fill=255)
    mask = mask.resize((box_w * PX_PER_UNIT, box_h * PX_PER_UNIT), Image.LANCZOS)
    image = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    image.putalpha(mask)
    return image


def arch_niche():
    """The lamp's light in the Arch's niche: white, its alpha faces.js's `tw-niche-*` gradient,
    inside the arch's centre line (faces.js ARCH2, both sides from the apex to the feet, closed
    along the feet), in a 360 x 342 face-unit box whose corner is the face's (45, 18)."""
    box_x, box_y, box_w, box_h = 45, 18, 360, 342
    k = FACE_PER_DRAWING
    # the alpha: about the lamp, (100, 33) in faces.js's units, out to 135 of them
    ys, xs = np.mgrid[0:box_h, 0:box_w]
    d = np.hypot(xs + 0.5 + box_x - 100 * k, ys + 0.5 + box_y - 33 * k) / (135 * k)
    alpha = np.interp(d, [0.0, 0.45, 1.0], [0.11, 0.035, 0.0])
    # the inside of the arch, drawn large and brought down so that its edge is smooth
    big = SUPERSAMPLE

    def at(x, y):
        return ((x * k - box_x) * big, (y * k - box_y) * big)

    c1y, c2y = round(118 - 0.487 * 110, 2), round(118 - 0.829 * 110, 2)
    outline = [at(20, 160), at(20, 118)]
    outline += cubic(at(20, 118), at(20, c1y), at(52, c2y), at(100, 8))[1:]
    outline += cubic(at(100, 8), at(148, c2y), at(180, c1y), at(180, 118))[1:]
    outline += [at(180, 160)]
    inside = Image.new("L", (box_w * big, box_h * big), 0)
    ImageDraw.Draw(inside).polygon(outline, fill=255)
    inside = np.asarray(inside.resize((box_w, box_h), Image.LANCZOS)).astype(float) / 255
    rgba = np.zeros((box_h, box_w, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * inside * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


# The Moon's hands (faces.js moonFace hand()): a dauphine diamond from its tip to its tail, widest
# 4 units above the centre, pointing at twelve. Each is (tip y, half width, tail) in faces.js's units,
# and the face-unit box it is shown in (x, y, width, height): the AnalogClock turns the picture about
# the face's centre, (225, 225), which is the pivot the face gives each hand inside its box.
HOUR_HAND = (52, 4.2, 9), (213, 114, 24, 134)
MINUTE_HAND = (20, 3.1, 10), (216, 43, 18, 207)
SECOND_BOX = (220, 32, 10, 231)
POINTER_BOX = (214, 1, 22, 17)
HAND_LIT, HAND_SHADE = (0xF4, 0xF5, 0xF2), (0xA9, 0xAF, 0xAB)
MIST = (0x8C, 0x94, 0x8F)


def _hand_points(spec):
    """The diamond's four corners in face units: tip, right, tail, left."""
    (tip, half, tail), _ = spec
    k = FACE_PER_DRAWING
    return [(100 * k, tip * k), ((100 + half) * k, 96 * k), (100 * k, (100 + tail) * k), ((100 - half) * k, 96 * k)]


def _stroke_path(draw, points, width, closed, fill):
    """A round-joined, round-capped stroke along points (in the big drawing's pixels): a disc
    stamped every half pixel, as arch_mark draws its arch."""
    r = width / 2
    pairs = list(zip(points, points[1:] + points[:1])) if closed else list(zip(points, points[1:]))
    for (x0, y0), (x1, y1) in pairs:
        steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
        for i in range(steps + 1):
            t = i / steps
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            draw.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def _render(box, paint):
    """A picture of the face-unit box, painted by paint(draw_rgb, draw_mask, at, scale) at the
    supersampled size: the colours on black and, apart, how much of each pixel is covered, each
    brought down on its own, so that an edge is the colour under it and not a blend with black."""
    bx, by, bw, bh = box
    big = PX_PER_UNIT * SUPERSAMPLE
    rgb = Image.new("RGB", (bw * big, bh * big), (0, 0, 0))
    mask = Image.new("L", (bw * big, bh * big), 0)

    def at(x, y):
        return ((x - bx) * big, (y - by) * big)

    paint(ImageDraw.Draw(rgb), ImageDraw.Draw(mask), at, big)
    size = (bw * PX_PER_UNIT, bh * PX_PER_UNIT)
    rgb = rgb.resize(size, Image.BOX)
    mask = mask.resize(size, Image.BOX)
    # The colours were drawn over black, so where a pixel is only partly covered its colour is
    # darkened by the same share: put it back, so that the alpha alone does the fading.
    c = np.asarray(rgb).astype(float)
    a = np.asarray(mask).astype(float)
    with np.errstate(divide="ignore", invalid="ignore"):
        c = np.where(a[..., None] > 0, np.clip(c * 255.0 / a[..., None], 0, 255), 0)
    out = np.zeros((size[1], size[0], 4), dtype=np.uint8)
    out[..., :3] = np.round(c).astype(np.uint8)
    out[..., 3] = np.round(a).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def hand(spec):
    """An awake hand: the lit facet (left of the spine) and the shaded one, inside a fine black
    keyline 0.7 units wide (faces.js strokes the whole diamond 1.4 wide in black, round-joined, and
    lays the two facets over it, so half of the stroke shows)."""
    pts = _hand_points(spec)
    tip, right, tail, left = pts

    def paint(rgb, mask, at, big):
        p = [at(*q) for q in pts]
        keyline = 1.4 * FACE_PER_DRAWING * big
        rgb.polygon(p, fill=(0, 0, 0))
        _stroke_path(rgb, p, keyline, True, (0, 0, 0))
        mask.polygon(p, fill=255)
        _stroke_path(mask, p, keyline, True, 255)
        rgb.polygon([at(*tip), at(*left), at(*tail)], fill=HAND_LIT)
        rgb.polygon([at(*tip), at(*right), at(*tail)], fill=HAND_SHADE)

    return _render(spec[1], paint)


def hand_back(spec):
    """A hand's black back (always-on, Ruling 41): the awake hand's own picture with every colour
    turned black, so that it covers exactly what the hand covers, pixel for pixel."""
    rgba = np.asarray(hand(spec)).copy()
    rgba[..., :3] = 0
    return Image.fromarray(rgba, "RGBA")


def second_hand():
    """The seconds hand: a mist line 1 unit wide, round-capped, from 16 units below the centre to 85
    above, with a counterweight 2 units in radius 12.5 below it (faces.js moonFace)."""
    k = FACE_PER_DRAWING

    def paint(rgb, mask, at, big):
        line = [at(100 * k, 116 * k), at(100 * k, 15 * k)]
        for d, colour in ((rgb, MIST), (mask, 255)):
            _stroke_path(d, line, 1.0 * k * big, False, colour)
            cx, cy = at(100 * k, 112.5 * k)
            r = 2 * k * big
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour)

    return _render(SECOND_BOX, paint)


def pointer():
    """The chapter arc's pointer at twelve, white, for the face to tint with the accent and turn to
    the next prayer: a triangle with its tip 92.2 units out and its base 99.4 out, 2.6 degrees
    either side (faces.js moonFace)."""
    k = FACE_PER_DRAWING

    def polar(r, deg):
        a = math.radians(deg - 90)
        return (225 + r * k * math.cos(a), 225 + r * k * math.sin(a))

    def paint(rgb, mask, at, big):
        tri = [at(*polar(92.2, 0)), at(*polar(99.4, -2.6)), at(*polar(99.4, 2.6))]
        rgb.polygon(tri, fill=(255, 255, 255))
        mask.polygon(tri, fill=255)

    return _render(POINTER_BOX, paint)


PICTURES = (("arch_mark.png", arch_mark), ("glow.png", glow), ("arch_track.png", lambda: arch_track(1.4)),
            ("arch_track_aod.png", lambda: arch_track(1.0)), ("arch_niche.png", arch_niche),
            ("moon_mark.png", lambda: arch_mark(30, 38, 4.25, 13, 2.1 * 564 / 13)),
            ("moon_mark_aod.png", lambda: arch_mark(30, 38, 4.25, 13, 1.4 * 564 / 13)),
            ("hand_hour.png", lambda: hand(HOUR_HAND)), ("hand_hour_back.png", lambda: hand_back(HOUR_HAND)),
            ("hand_minute.png", lambda: hand(MINUTE_HAND)), ("hand_minute_back.png", lambda: hand_back(MINUTE_HAND)),
            ("hand_second.png", second_hand), ("moon_pointer.png", pointer))

USAGE = """usage: python3 tools/face-art.py            # draws the face's pictures, rewrites those that changed
       python3 tools/face-art.py --check    # exits 1 if a picture is not up to date, writes nothing"""


def differs(drawn, committed):
    """Why the picture file `drawn` decodes to another picture than the file `committed` (its mode,
    its size or the pixels that differ), or None when both are the same picture."""
    if not os.path.exists(committed):
        return "missing"
    try:
        b = Image.open(committed)
        b.load()
    except OSError:
        return "not a picture"
    with Image.open(drawn) as a, b:
        if a.mode != b.mode:
            return "mode %s, drawn %s" % (b.mode, a.mode)
        if a.size != b.size:
            return "%dx%d px, drawn %dx%d" % (b.width, b.height, a.width, a.height)
        pa, pb = np.asarray(a), np.asarray(b)
        if np.array_equal(pa, pb):
            return None
        unequal = pa != pb
        n = int((unequal.any(axis=-1) if unequal.ndim == 3 else unequal).sum())
        return "%d px differ" % n


def main(argv):
    if argv not in ([], ["--check"]):
        print(USAGE, file=sys.stderr)
        return 2
    check = argv == ["--check"]
    stale = []
    # Every picture is drawn into a directory of its own and read back from there, so that a
    # picture is compared as the file it is, and --check never writes beside the face.
    with tempfile.TemporaryDirectory(prefix="taqwa-face-art.") as scratch:
        for name, draw in PICTURES:
            image = draw()
            drawn = os.path.join(scratch, name)
            image.save(drawn, optimize=True)
            why = differs(drawn, os.path.join(DRAWABLE, name))
            if why:
                stale.append((name, why, drawn, image))
        if check:
            for name, why, _, _ in stale:
                print("stale: %s (%s)" % (name, why))
            print("tools/face-art.py: %s" % ("up to date" if not stale else "%d of %d pictures stale" % (len(stale), len(PICTURES))))
            return 1 if stale else 0
        os.makedirs(DRAWABLE, exist_ok=True)
        for name, why, drawn, image in stale:
            with open(drawn, "rb") as src, open(os.path.join(DRAWABLE, name), "wb") as dst:
                dst.write(src.read())
            print("%s %dx%d px (%s)" % (name, image.width, image.height, why))
    print("tools/face-art.py: wrote %s" % (", ".join(name for name, _, _, _ in stale) or "nothing: every picture is up to date"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
