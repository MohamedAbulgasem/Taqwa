#!/usr/bin/env python3
"""Works out the Arch style's lit arch for the Taqwa watch face (:watchface) from faces.js's ARCH2,
and writes it into the face, between the face-arch.py markers in watchface/src/main/res/raw/watchface.xml:

    python3 tools/face-arch.py            # rewrites the block; the output is the same on every run
    python3 tools/face-arch.py --check    # says whether the block is up to date, and changes nothing

The outline never changes, so it is a picture (tools/face-art.py draws arch_track.png). The lit
part grows with the period, and Watch Face Format has no free path, so it
is drawn with the format's own shapes: each side's crown (a cubic curve from the apex to the spring)
is two biarcs, four circular arcs that meet with one tangent and stay within 0.12 units (0.27 px)
of the curve, and each foot is a straight line. faces.js lights a dash of the period's share of the
whole length, from the apex down each side, with round caps: so every arc here sweeps as much of
itself as that length has reached, and the arcs' own round caps are the dash's. The progress is
slot 0's RANGED_VALUE (Ruling 16), the same expression the Ring's arc reads.

faces.js (docs/superpowers/specs/2026-10-19-watch-companion) draws in 200 units and the face is 450:
every length here is that drawing's x 2.25.
"""
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FACE_XML = os.path.join(ROOT, "watchface", "src", "main", "res", "raw", "watchface.xml")
K = 2.25  # face units to a faces.js unit

# faces.js ARCH2: half 80, spring 118, apex 8, foot 160; the crown's controls from the icon's grid.
HALF, SPRING, APEX, FOOT = 80, 118, 8, 160
CH = SPRING - APEX
C1Y = round(SPRING - 0.487 * CH, 2)     # f() rounds to two places, as faces.js does
C2Y = round(SPRING - 0.829 * CH, 2)
P0, P1, P2, P3 = (100, APEX), (100 + 132 / 220 * HALF, C2Y), (100 + HALF, C1Y), (100 + HALF, SPRING)

# The lit part's strokes (faces.js archFace): 3.8 awake and 2 always-on, the accent, opacity 1 and
# 0.6, round caps.
LIT, LIT_AOD = 3.8, 2.0

PROGRESS = ("(clamp([COMPLICATION.RANGED_VALUE_VALUE], [COMPLICATION.RANGED_VALUE_MIN], [COMPLICATION.RANGED_VALUE_MAX])"
            " - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN])")


def bez(t):
    a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
    return (a * P0[0] + b * P1[0] + c * P2[0] + d * P3[0], a * P0[1] + b * P1[1] + c * P2[1] + d * P3[1])


def dbez(t):
    return (3 * (1 - t) ** 2 * (P1[0] - P0[0]) + 6 * (1 - t) * t * (P2[0] - P1[0]) + 3 * t ** 2 * (P3[0] - P2[0]),
            3 * (1 - t) ** 2 * (P1[1] - P0[1]) + 6 * (1 - t) * t * (P2[1] - P1[1]) + 3 * t ** 2 * (P3[1] - P2[1]))


def unit(v):
    n = math.hypot(*v)
    return (v[0] / n, v[1] / n)


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


# The crown, sampled finely, with the length along it from the apex.
N = 40000
TS = [i / N for i in range(N + 1)]
PTS = [bez(t) for t in TS]
LENGTHS = [0.0]
for a, b in zip(PTS, PTS[1:]):
    LENGTHS.append(LENGTHS[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
CROWN = LENGTHS[-1]
LEG = FOOT - SPRING
WHOLE = CROWN + LEG


def at_length(s):
    """The crown's point and tangent at a length s from the apex."""
    lo, hi = 0, N
    while lo < hi:
        mid = (lo + hi) // 2
        if LENGTHS[mid] < s:
            lo = mid + 1
        else:
            hi = mid
    return PTS[lo], unit(dbez(TS[lo]))


def arc_through(a, ta, b):
    """The circle that leaves a along the tangent ta and passes through b: its centre and radius."""
    n = (-ta[1], ta[0])
    d = sub(b, a)
    k = dot(d, d) / (2 * dot(n, d))
    return (a[0] + k * n[0], a[1] + k * n[1]), abs(k)


def biarc(p0, t0, p1, t1):
    """Two circular arcs from p0 (tangent t0) to p1 (tangent t1) that meet with one tangent."""
    d = sub(p1, p0)
    v = (t0[0] + t1[0], t0[1] + t1[1])
    denom = 2 * (1 - dot(t0, t1))
    a = (-dot(v, d) + math.sqrt(dot(v, d) ** 2 + denom * dot(d, d))) / denom
    j = ((p0[0] + a * t0[0] + p1[0] - a * t1[0]) / 2, (p0[1] + a * t0[1] + p1[1] - a * t1[1]) / 2)
    c1, r1 = arc_through(p0, t0, j)
    radius = unit(sub(j, c1))
    tj = (-radius[1], radius[0])
    if dot(tj, sub(j, p0)) < 0:
        tj = (-tj[0], -tj[1])
    c2, r2 = arc_through(j, tj, p1)
    return [(p0, j, c1, r1), (j, p1, c2, r2)]


def angle(c, p):
    """Watch Face Format's angle of p seen from c: 0 at twelve o'clock, growing clockwise."""
    return math.degrees(math.atan2(p[0] - c[0], c[1] - p[1])) % 360


def crown_arcs():
    """The right side's crown as four arcs: (centre, radius, start angle, sweep, length from the
    apex at its start, its own length), in faces.js units, with the fit's worst distance."""
    knots = [0, CROWN / 2, CROWN]
    arcs = []
    for s0, s1 in zip(knots, knots[1:]):
        (a, ta), (b, tb) = at_length(s0), at_length(s1)
        arcs += biarc(a, ta, b, tb)
    out, run = [], 0.0
    for p, q, c, r in arcs:
        start, end = angle(c, p), angle(c, q)
        sweep = (end - start) % 360
        out.append((c, r, start, sweep, run, r * math.radians(sweep)))
        run += r * math.radians(sweep)
    worst = max(min(abs(math.hypot(*sub(pt, c)) - r) for c, r, *_ in out) for pt in PTS[::40])
    return out, worst, run


def n(v, places=4):
    s = ("%." + str(places) + "f") % v
    s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def lit_draw(thickness, alpha_awake, alpha_ambient, arcs, crown):
    """A PartDraw with the lit arch in one thickness: the four arcs and the foot on each side. The
    length is the arcs' own (crown, a few hundredths short of the curve's), so that the foot takes
    over exactly where the last arc ends."""
    w = thickness * K
    whole = crown + LEG
    lines = ['<PartDraw x="0" y="0" width="450" height="450"%s>' % ('' if alpha_awake == 255 else ' alpha="%d"' % alpha_awake)]
    for side in (1, -1):
        for c, r, start, sweep, s0, length in arcs:
            cx = 100 + side * (c[0] - 100)
            # how many degrees of this arc the lit length has reached: (p * whole - s0) / r, in degrees
            per_unit = 180 / (math.pi * r)
            reach = "clamp(%s * (%s) - %s, 0, %s)" % (n(whole * per_unit), PROGRESS, n(s0 * per_unit), n(sweep))
            if side == 1:
                a0 = start
                attrs = 'startAngle="%s" endAngle="%s"' % (n(a0), n(a0 + sweep))
                transform = '<Transform target="endAngle" value="%s + %s" />' % (n(a0), reach)
            else:
                a0 = 360 - start          # the mirror image: the apex end, reached going anticlockwise
                attrs = 'startAngle="%s" endAngle="%s"' % (n(a0 - sweep), n(a0))
                transform = '<Transform target="startAngle" value="%s - %s" />' % (n(a0), reach)
            lines += ['    <Arc centerX="%s" centerY="%s" width="%s" height="%s" %s direction="CLOCKWISE">' % (
                          n(cx * K), n(c[1] * K), n(2 * r * K), n(2 * r * K), attrs),
                      '        <Stroke thickness="%s" color="[CONFIGURATION.accent.0]" cap="ROUND" />' % n(w, 3),
                      '        ' + transform,
                      '    </Arc>']
        # the foot: from the spring down to the screen's edge, once the lit length has passed the
        # crown (until then the line stands off the screen, where its round cap cannot show)
        x = (100 + side * HALF) * K
        past = "(%s) * %s > %s" % (PROGRESS, n(whole), n(crown))
        lines += ['    <Line startX="%s" startY="%s" endX="%s" endY="%s">' % (n(x), n(SPRING * K), n(x), n(SPRING * K)),
                  '        <Stroke thickness="%s" color="[CONFIGURATION.accent.0]" cap="ROUND" />' % n(w, 3),
                  '        <Transform target="startX" value="%s ? %s : -100" />' % (past, n(x)),
                  '        <Transform target="endX" value="%s ? %s : -100" />' % (past, n(x)),
                  '        <Transform target="endY" value="%s + %s * clamp(%s * (%s) - %s, 0, %s)" />' % (
                      n(SPRING * K), n(K), n(whole), PROGRESS, n(crown), n(LEG)),
                  '    </Line>']
    lines.append('    <Variant mode="AMBIENT" target="alpha" value="%d" />' % alpha_ambient)
    lines.append('</PartDraw>')
    return lines




def main():
    check = "--check" in sys.argv[1:]
    arcs, worst, crown = crown_arcs()
    assert abs(crown - CROWN) < 0.05, (crown, CROWN)
    assert worst < 0.15, worst
    block = ["<!-- face-arch.py: begin (written by tools/face-arch.py; do not edit by hand) -->"]
    block += ["<!-- the lit arch, awake: %s px (%s units), the accent; the crown is four arcs a side, within %s"
              " units of faces.js's curve, and the foot a line -->" % (n(LIT * K, 3), n(LIT, 3), n(worst, 2))]
    block += lit_draw(LIT, 255, 0, arcs, crown)
    block += ["<!-- the lit arch always-on: %s px (%s units) at 60%%, out while awake -->" % (n(LIT_AOD * K, 3), n(LIT_AOD, 3))]
    block += lit_draw(LIT_AOD, 0, 153, arcs, crown)
    block.append("<!-- face-arch.py: end -->")

    xml = open(FACE_XML, encoding="utf-8").read()
    m = re.search(r"^([ \t]*)<!-- face-arch\.py: begin.*?<!-- face-arch\.py: end -->", xml, re.S | re.M)
    if not m:
        sys.exit("watchface.xml has no face-arch.py markers")
    indent = m.group(1)
    new_xml = xml[:m.start()] + "\n".join(indent + line for line in block) + xml[m.end():]
    files = {FACE_XML: new_xml}
    stale = [p for p, text in files.items() if not os.path.exists(p) or open(p, encoding="utf-8").read() != text]
    if check:
        print("up to date" if not stale else "stale: " + ", ".join(os.path.relpath(p, ROOT) for p in stale))
        sys.exit(1 if stale else 0)
    for p in stale:
        with open(p, "w", encoding="utf-8") as f:
            f.write(files[p])
    print("crown %.3f + foot %d = %.3f units a side; arcs (centre, r, start, sweep):" % (CROWN, LEG, WHOLE))
    for c, r, start, sweep, s0, length in arcs:
        print("  (%.3f, %.3f) r %.3f from %.3f for %.3f deg, %.3f to %.3f along" % (c[0], c[1], r, start, sweep, s0, s0 + length))
    print("worst distance from the curve %.3f units; wrote %s" % (worst, ", ".join(os.path.relpath(p, ROOT) for p in stale) or "nothing"))


if __name__ == "__main__":
    main()
