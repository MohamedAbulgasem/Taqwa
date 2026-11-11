"""The Qibla page's air on the drawn pixels (Ruling 68 item 4; errata p3 #18, Ruling 67 item 3).

    airpx.py <shot.png>...

On a shot of the turning dial, the four major ticks (mist, where the slate minor ticks are dimmer)
give the letters' angles, and each letter's middle stands 72.05 units out at its tick's angle. The
lit pixels (any channel over 60 on the black page) touching 8 ways are one glyph: a glyph whose ink
centre is within 3.5 units of a letter's middle, or within 9 and 66 units out or more (where no
caption's ink reaches once it keeps its air), is that letter (a letter of two parts, Bengali's পূ or
Urdu's مش, is joined there); the others below the hub are the lines
of text (the caption's lines and the detail), grouped by their rows; an amber part is the needle
when it reaches the hub, else the caption facing the Qibla. A letter that touches a line
is one glyph with it and is reported missing ("touching"). For each
line of text, by rows, it prints the least gap in units from its pixels to a letter's, as drawn
(whole pixels, with the anti-aliased edge: a little less than the outlines' gap), and for the first
line the gap to the needle's hub (4.83 units round the middle); and for each letter its brightest
pixel against its colour's (the north letter ink, #F1F3F1; the others mist, #8C948F), which is
the alpha it was drawn at when no other ink is under it (Ruling 66 item 5's fade).
"""
import math
import sys

import numpy as np
from PIL import Image

LIT = 60
HUB = 4.6 * 1.05
# An amber part that reaches within this many units of the hub is the needle; the amber caption
# ("Facing qibla") keeps 2 units from the hub (T9), so it is text.
NEEDLE = 1.5


def glyphs(mask):
    """The 8-connected parts of [mask], each a list of (y, x)."""
    seen = np.zeros(mask.shape, bool)
    parts = []
    for y, x in zip(*np.nonzero(mask)):
        if seen[y, x]:
            continue
        stack, part = [(y, x)], []
        seen[y, x] = True
        while stack:
            cy, cx = stack.pop()
            part.append((cy, cx))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < mask.shape[0] and 0 <= nx < mask.shape[1] and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
        parts.append(np.array(part))
    return parts


def least(a, b):
    """The least distance between two sets of pixel centres, in pixels."""
    best = math.inf
    for chunk in range(0, len(a), 512):
        d = a[chunk:chunk + 512, None, :] - b[None, :, :]
        best = min(best, float(np.sqrt((d ** 2).sum(-1)).min()))
    return best


def measure(path):
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(int)
    side = rgb.shape[0]
    unit = side / 200.0
    lit = rgb.max(-1) > LIT
    amber = (rgb[..., 0] - rgb[..., 2]) > 60
    ys, xs = np.mgrid[0:side, 0:side]
    r = np.hypot((xs + 0.5) / unit - 100, (ys + 0.5) / unit - 100)
    # the major ticks: mist pixels in the ticks' band, in four clusters of angle
    band = (r > 83) & (r < 87) & (np.abs(rgb[..., 0] - 140) < 25) & ~amber
    angles = np.degrees(np.arctan2((xs[band] + 0.5) / unit - 100, -((ys[band] + 0.5) / unit - 100))) % 360
    ticks = []
    for a in sorted(angles):
        for t in ticks:
            if abs((a - t[0] + 180) % 360 - 180) < 6:
                t.append(a); break
        else:
            ticks.append([a])
    ticks = [math.degrees(math.atan2(np.mean(np.sin(np.radians(t))), np.mean(np.cos(np.radians(t))))) % 360 for t in ticks if len(t) > 3]
    middles = [(100 + 72.05 * math.sin(math.radians(t)), 100 - 72.05 * math.cos(math.radians(t))) for t in ticks]
    found = {k: [] for k in range(len(middles))}
    text = []
    for part in glyphs(lit & (r < 80)):
        cy, cx = (part.mean(0) + 0.5) / unit
        pr = math.hypot(cx - 100, cy - 100)
        near = [k for k, (mx, my) in enumerate(middles) if math.hypot(cx - mx, cy - my) < 3.5 or (math.hypot(cx - mx, cy - my) < 9 and pr >= 66)]
        if near:
            found[near[0]].append(part)
        elif cy > 100 + HUB + 1 and not (amber[part[:, 0], part[:, 1]].any() and r[part[:, 0], part[:, 1]].min() < HUB + NEEDLE):
            text.append(part)
    joined = [np.vstack(found[k]) for k in found if found[k]]
    missing = len(middles) - len(joined)
    # the lines of text: the text parts whose rows overlap
    lines = []
    for part in sorted(text, key=lambda p: p[:, 0].min()):
        top, bottom = part[:, 0].min(), part[:, 0].max()
        for line in lines:
            if top <= line["bottom"] and bottom >= line["top"]:
                line["px"] = np.vstack([line["px"], part]); line["top"] = min(line["top"], top); line["bottom"] = max(line["bottom"], bottom)
                break
        else:
            lines.append({"px": part, "top": top, "bottom": bottom})
    lines.sort(key=lambda l: l["top"])
    out = []
    for k, line in enumerate(lines):
        gap = min((least(line["px"].astype(float), l.astype(float)) for l in joined), default=math.inf)
        what = "line %d (y %.1f-%.1f)" % (k + 1, line["top"] / unit, (line["bottom"] + 1) / unit)
        text_gap = (gap - 1) / unit if gap != math.inf else math.inf
        extra = ""
        if k == 0:
            hub = float(np.hypot((line["px"][:, 1] + 0.5) / unit - 100, (line["px"][:, 0] + 0.5) / unit - 100).min()) - HUB
            extra = ", hub %.2f" % hub
        out.append("%s: letters %.2f%s" % (what, text_gap, extra))
    alphas = []
    for l in joined:
        peak = rgb[l[:, 0], l[:, 1]].max()
        cy, cx = l.mean(0)
        angle = (math.degrees(math.atan2((cx + 0.5) / unit - 100, -((cy + 0.5) / unit - 100))) + 360) % 360
        alphas.append("%.0f deg: peak %d (%.2f of ink, %.2f of mist)" % (angle, peak, peak / 241, peak / 140))
    if missing or len(middles) != 4:
        out.append("%d major ticks, %d letters touching other ink or not found" % (len(middles), missing))
    return out, alphas


def main():
    for path in sys.argv[1:]:
        lines, alphas = measure(path)
        print("%s | %s | letters: %s" % (path.split("/")[-1], "; ".join(lines), "; ".join(alphas)))


if __name__ == "__main__":
    main()
