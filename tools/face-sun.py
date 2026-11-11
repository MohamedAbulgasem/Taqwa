#!/usr/bin/env python3
"""Writes the Sun style of the Taqwa watch face (:watchface) from faces.js's sunFace, between the
face-sun.py markers in watchface/src/main/res/raw/watchface.xml:

    python3 tools/face-sun.py            # rewrites the block; the output is the same on every run
    python3 tools/face-sun.py --check    # says whether the block is up to date, and changes nothing

The Sun is a 24-hour dial with Dhuhr at twelve and midnight at the bottom, a quarter of a degree a
minute, turning clockwise in every language (spec 3.4). Its numbers come from three of the face's
fixed slots, and an expression in one slot can read another's as [COMPLICATION.<slot>.<field>]
(found on the Wear OS 6 runtime):

- slot 0, the next prayer's RANGED_VALUE: the period as minutes from the date's Dhuhr. Its min is
  where the period began and its max where it ends, and its value is now, which the system moves
  every minute: the lit arc, the two lit marks, the sun or the crescent, and the sky's glow.
- slot 1, the day's WEIGHTED_ELEMENTS: the seven stretches from midnight (night, twilight, three of
  daylight, twilight, night), drawn as a WeightedStroke in the colours faces.js composites them to.
- slot 7, the day dial's geometry, a RANGED_VALUE with two fields in each number, from
  DayDialSource in :wearApp, which no picker offers (ComplicationFields.packDial, whose DIAL_BASE
  and DIAL_OFFSET this tool reads out of ComplicationFields.kt, so the two can never disagree:
  a changed constant makes --check fail until the block is written again): the date's midnight,
  which the stretches are turned by, and Fajr, Sunrise, Asr, Maghrib and Isha, which the marks
  are put at. Dhuhr is 0.

A face cannot read one weight out of a weighted complication's weights (they are a string), and a
text is never a number to it, so the geometry travels as numbers in a ranged value.

The dial is drawn by the scene, just before slot 7 is declared (where its own Complication drew it
until Task 12), from slot 7's data read across slots, and only when the three slots have their data
(slot 7's being the dial's); a scene level ring stands in for it when they have not (a feed that
has run out), as the Arch's outline does. Slot 7's own box is empty (0 by 0): a screen reader is
given every slot with data at its box, and a slot with no room is no item (Task 12, the face
header), while its data still reaches every expression that reads it. Every switch that follows the data (which of a mark's three colours, the sun or the crescent,
the day's sky or the night's, the dial or the frame) is an alpha Transform and not a Condition: on
the Wear OS 6 runtime a Condition whose expression reads another slot's data was found stuck in a
period's colours after the period had turned (a passed mark left in mist until the face was loaded
again), while a Transform follows it at once, as the lit arc and the sun do. faces.js
(docs/superpowers/specs/2026-10-19-watch-companion) draws in 200 units and the face is 450: every
length here is that drawing's x 2.25.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FACE_XML = os.path.join(ROOT, "watchface", "src", "main", "res", "raw", "watchface.xml")
BEGIN = "<!-- face-sun.py: begin (written by tools/face-sun.py; do not edit by hand) -->"
END = "<!-- face-sun.py: end -->"

K = 2.25
C = 225.0
FIELDS_KT = os.path.join(ROOT, "wearApp", "src", "main", "kotlin", "world", "taqwa", "app", "wear",
                         "complication", "ComplicationFields.kt")


def dial_constants():
    """ComplicationFields.DIAL_BASE and DIAL_OFFSET, as the Kotlin source declares them."""
    with open(FIELDS_KT, encoding="utf-8") as f:
        text = f.read()
    found = []
    for name in ("DIAL_BASE", "DIAL_OFFSET"):
        m = re.search(r"const val %s: Int = (\d+)\b" % name, text)
        if not m:
            sys.exit("face-sun.py: no 'const val %s: Int = <number>' in %s" % (name, os.path.relpath(FIELDS_KT, ROOT)))
        found.append(int(m.group(1)))
    return tuple(found)


BASE, OFFSET = dial_constants()

# faces.js's palette and sunFace's figures.
HAIR, MIST, SLATE, INK = "232825", "8C948F", "5A625D", "F1F3F1"
GOLD = "F0B429"
R = 92 * K                         # the ring: 207
RING_W, RING_W_AOD = 2.4 * K, 1 * K
LIT_W, LIT_W_AOD = 3.6 * K, 2 * K
MARK_IN, MARK_OUT, MARK_W = (92 - 5.5) * K, (92 + 5.5) * K, 1.6 * K
SUNRISE_R = 1.7 * K
SKY_AT, SKY_R = 74 * K, 96 * K
SUNGLOW_R, SUN_R, SUN_AOD_R, SUN_AOD_W = 13 * K, 4.6 * K, 3.6 * K, 1.2 * K
NIGHT_DOT_R, CRESCENT_R = 6.2 * K, 4.6 * K

# The always-on frame's part alphas (faces.js's opacities): the marks 0.7, the Sunrise 0.6 and the
# thin lit arc 0.6.
A_MARKS, A_SUNRISE, A_LIT = 179, 153, 153

# Slot 0's numbers, and now held to its period.
V0, N0, X0 = ("[COMPLICATION.0.RANGED_VALUE_VALUE]", "[COMPLICATION.0.RANGED_VALUE_MIN]",
              "[COMPLICATION.0.RANGED_VALUE_MAX]")
NOW = "clamp(%s, %s, %s)" % (V0, N0, X0)
# Slot 7's numbers, taken apart (ComplicationFields.packDial).
MN, VL, MX = "[COMPLICATION.7.RANGED_VALUE_MIN]", "[COMPLICATION.7.RANGED_VALUE_VALUE]", "[COMPLICATION.7.RANGED_VALUE_MAX]"


def high(n, extra=0):
    return "(floor(%s / %d) - %d)" % (n, BASE, OFFSET + extra)


def low(n):
    return "(%s %% %d - %d)" % (n, BASE, OFFSET)


MIDNIGHT, FAJR, SUNRISE, MAGHRIB, ASR, ISHA = high(MN), low(MN), high(VL, 1), low(VL), high(MX, 2), low(MX)
DAY = "%s <= %s && %s < %s" % (SUNRISE, NOW, NOW, MAGHRIB)
# Slots 0 and 1 have their data; and the three have, slot 7's being the dial's (not the battery that stands in).
OTHERS = '[COMPLICATION.0.TYPE] == "RANGED_VALUE" && [COMPLICATION.1.TYPE] == "WEIGHTED_ELEMENTS"'
HAS_DATA = '%s && [COMPLICATION.7.TYPE] == "RANGED_VALUE" && [COMPLICATION.7.RANGED_VALUE_MAX] >= %d' % (OTHERS, 2 * BASE)


def f(x):
    s = ("%.4f" % x).rstrip("0").rstrip(".")
    return s if s != "-0" else "0"


def mix(top, alpha, under):
    """faces.js's colour `top` at `alpha` over `under`, as the browser composites it."""
    t = [int(top[i:i + 2], 16) for i in (0, 2, 4)]
    u = [int(under[i:i + 2], 16) for i in (0, 2, 4)]
    return "".join("%02X" % int(round(a * alpha + b * (1 - alpha))) for a, b in zip(t, u))


def argb(rgb, alpha=1.0):
    return "#%02X%s" % (int(round(alpha * 255)), rgb)


# The stretches' colours: awake, the ring in hairline with the daylight at 34% and the twilights at
# 15% over it; always-on, the ring in slate at 50% with the daylight at 30% over it, and no twilight.
DAYLIGHT, TWILIGHT = mix(GOLD, 0.34, HAIR), mix(GOLD, 0.15, HAIR)
RING_AOD = mix(SLATE, 0.5, "000000")
DAYLIGHT_AOD = mix(GOLD, 0.3, RING_AOD)
AWAKE = [HAIR, TWILIGHT, DAYLIGHT, DAYLIGHT, DAYLIGHT, TWILIGHT, HAIR]
AOD = [RING_AOD, RING_AOD, DAYLIGHT_AOD, DAYLIGHT_AOD, DAYLIGHT_AOD, RING_AOD, RING_AOD]


def attr(value):
    """An expression as an XML attribute's value, quoted: single quotes, since the names of types are
    in double ones, and < and & escaped."""
    return "'%s'" % value.replace("&", "&amp;").replace("<", "&lt;")


def shown(condition):
    """An alpha Transform's value: the part drawn while the condition holds and not otherwise."""
    return "(%s) ? 255 : 0" % condition


def ellipse(cx, cy, r, inner, ind):
    return ('%s<Ellipse x="%s" y="%s" width="%s" height="%s">\n%s%s</Ellipse>\n'
            % (ind, f(cx - r), f(cy - r), f(2 * r), f(2 * r), inner, ind))


def fill(color, ind):
    return '%s<Fill color="%s" />\n' % (ind, color)


def radial(cx, cy, r, stops, ind):
    colors = " ".join(c for c, _ in stops)
    positions = " ".join(f(p) for _, p in stops)
    return ('%s<Fill color="#FFFFFFFF">\n%s    <RadialGradient centerX="%s" centerY="%s" radius="%s" colors="%s" positions="%s" />\n%s</Fill>\n'
            % (ind, ind, f(cx), f(cy), f(r), colors, positions, ind))


def transforms(pairs, ind):
    return "".join('%s<Transform target="%s" value=%s />\n' % (ind, t, attr(v)) for t, v in pairs)


def ring_arc(stroke, turn, ind):
    return ('%s<Arc centerX="225" centerY="225" width="%s" height="%s" startAngle="0" endAngle="360" direction="CLOCKWISE">\n'
            '%s    %s\n%s%s</Arc>\n' % (ind, f(2 * R), f(2 * R), ind, stroke, transforms(turn, ind + "    "), ind))


def part(body, ind, alpha=None, aod=None, show=None):
    a = ' alpha="%d"' % alpha if alpha is not None else ""
    v = '%s    <Variant mode="AMBIENT" target="alpha" value="%d" />\n' % (ind, aod) if aod is not None else ""
    t = transforms([("alpha", shown(show))], ind + "    ") if show is not None else ""
    return '%s<PartDraw x="0" y="0" width="450" height="450"%s>\n%s%s%s%s</PartDraw>\n' % (ind, a, body, t, v, ind)


def group(name, body, ind, box=(0, 0, 450, 450), angle=None, show=None, aod=None):
    pairs = ([("angle", angle)] if angle is not None else []) + ([("alpha", shown(show))] if show is not None else [])
    v = '%s    <Variant mode="AMBIENT" target="alpha" value="%d" />\n' % (ind, aod) if aod is not None else ""
    return ('%s<Group name="%s" x="%s" y="%s" width="%s" height="%s">\n%s%s%s%s</Group>\n'
            % (ind, name, box[0], box[1], box[2], box[3], body, transforms(pairs, ind + "    "), v, ind))


def condition(name, expression, compare, ind):
    return ("%s<Condition>\n%s    <Expressions>\n%s        <Expression name=\"%s\"><![CDATA[%s]]></Expression>\n%s    </Expressions>\n"
            "%s    <Compare expression=\"%s\">\n%s%s    </Compare>\n%s</Condition>\n"
            % (ind, ind, ind, name, expression, ind, ind, name, compare, ind, ind))


def frame_rings(ind):
    """The ring alone, in the always-on and awake strokes: the frame a spent feed leaves."""
    def ring(width, color):
        return ('%s    <Ellipse x="%s" y="%s" width="%s" height="%s">\n%s        <Stroke thickness="%s" color="%s" />\n%s    </Ellipse>\n'
                % (ind, f(C - R), f(C - R), f(2 * R), f(2 * R), ind, f(width), argb(color), ind))
    return part(ring(RING_W, HAIR), ind, aod=0) + part(ring(RING_W_AOD, RING_AOD), ind, alpha=0, aod=255)


def mark(name, position, ind):
    i1 = ind + "    "

    def line(color, show):
        body = ('%s    <Line startX="225" startY="%s" endX="225" endY="%s">\n%s        <Stroke thickness="%s" color="%s" cap="ROUND" />\n%s    </Line>\n'
                % (i1, f(C - MARK_OUT), f(C - MARK_IN), i1, f(MARK_W), color, i1))
        return part(body, i1, show=show)

    lit = "abs(%s - %s) < 0.75 || abs(%s - %s) < 0.75" % (position, N0, position, X0)
    passed = "%s < %s" % (position, NOW)
    body = (line("[CONFIGURATION.accent.0]", lit)
            + line(argb(SLATE), "!(%s) && %s" % (lit, passed))
            + line(argb(MIST), "!(%s) && !(%s)" % (lit, passed)))
    return group("mark_" + name, body, ind, angle="(%s) / 4" % position)


def dial(ind):
    i1, i2, i3, i4 = ind + "    ", ind + "        ", ind + "            ", ind + "                "
    out = []
    # 1. The sky's glow, a faint light about the sun by day and the night's blue after it, awake only.
    def sky(stops, show):
        return part(ellipse(C, C - SKY_AT, SKY_R, radial(C, C - SKY_AT, SKY_R, stops, i4), i3), i2, show=show)
    glow = (sky([(argb(GOLD, 0.13), 0), (argb(GOLD, 0.04), 0.5), (argb(GOLD, 0), 1)], DAY)
            + sky([(argb("9FB3EC", 0.09), 0), (argb("9FB3EC", 0.025), 0.55), (argb("9FB3EC", 0), 1)], "!(%s)" % DAY))
    out.append("%s<!-- the sky's glow about now: gold by day and the night's blue after Maghrib, awake only -->\n" % i1)
    out.append(group("sky", glow, i1, angle="%s / 4" % NOW, aod=0))
    # 2. The ring of the day, turned by the date's midnight.
    turn = [("startAngle", "%s / 4" % MIDNIGHT), ("endAngle", "%s / 4 + 360" % MIDNIGHT)]
    weighted = lambda colors, width: ('<WeightedStroke thickness="%s" colors="%s" weights="[COMPLICATION.1.WEIGHTED_ELEMENTS_WEIGHTS]" />'
                                      % (f(width), " ".join(argb(c) for c in colors)))
    out.append("%s<!-- the day: slot 1's seven stretches from midnight, turned so that Dhuhr is at twelve; awake the hairline\n"
               "%s     ring with the daylight at 34%% and the twilights at 15%% over it, always-on slate at 50%% with the daylight\n"
               "%s     at 30%%, and thinner (a second PartDraw) -->\n" % (i1, i1, i1))
    out.append(part(ring_arc(weighted(AWAKE, RING_W), turn, i2), i1, aod=0))
    out.append(part(ring_arc(weighted(AOD, RING_W_AOD), turn, i2), i1, alpha=0, aod=255))
    # 3. The lit period, from its start to now, in the accent.
    lit = [("startAngle", "%s / 4" % N0), ("endAngle", "%s / 4" % NOW)]
    stroke = lambda w: '<Stroke thickness="%s" color="[CONFIGURATION.accent.0]" cap="ROUND" />' % f(w)
    out.append("%s<!-- the period lit from its start to now, in the accent; thinner and at 60%% always-on -->\n" % i1)
    out.append(part(ring_arc(stroke(LIT_W), lit, i2), i1, aod=0))
    out.append(part(ring_arc(stroke(LIT_W_AOD), lit, i2), i1, alpha=0, aod=A_LIT))
    # 4. The five marks: the period's two in the accent, those gone by in slate, those to come in mist.
    marks = "".join(mark(n, p, i2) for n, p in (("fajr", FAJR), ("dhuhr", "0"), ("asr", ASR), ("maghrib", MAGHRIB), ("isha", ISHA)))
    out.append("%s<!-- the five prayers' marks, across the ring: the period's start and end in the accent, those gone by\n"
               "%s     today in slate and those to come in mist; at 70%% always-on. Each is three strokes, one shown. -->\n" % (i1, i1))
    out.append(group("marks", marks, i1, aod=A_MARKS))
    # 5. The Sunrise, a small dot on the ring.
    out.append("%s<!-- the Sunrise, a dot on the ring in mist; at 60%% always-on -->\n" % i1)
    sunrise = part(ellipse(C, C - R, SUNRISE_R, fill(argb(MIST), i4), i3), i2, aod=A_SUNRISE)
    out.append(group("sunrise", sunrise, i1, angle="%s / 4" % SUNRISE))
    # 6. Now: the sun by day, the crescent by night.
    i5 = i4 + "    "
    sun = (part(ellipse(C, C - R, SUNGLOW_R, radial(C, C - R, SUNGLOW_R, [(argb("FFD978", 0.55), 0), (argb("FFD978", 0), 1)], i5), i4)
                + ellipse(C, C - R, SUN_R, fill(argb("FFF1C9"), i5), i4), i3, aod=0)
           + part(ellipse(C, C - R, SUN_AOD_R, fill("#FF000000", i5) + '%s<Stroke thickness="%s" color="%s" />\n' % (i5, f(SUN_AOD_W), argb(GOLD)), i4),
                  i3, alpha=0, aod=255))
    # The crescent keeps its shape upright wherever it is on the ring: a disc less a second one, offset
    # to the right (faces.js crescent()), over a black disc that holds the ring back about it. It is
    # in a group of its own about its centre, turned back by the angle the dial turned it by.
    cx, cy = 30.0, 18.0
    crescent = lambda color: (ellipse(cx, cy, CRESCENT_R, fill(color, i5), i4)
                              + ellipse(cx + 0.42 * CRESCENT_R, cy, 0.91 * CRESCENT_R, fill("#FF000000", i5), i4))
    upright = (part(ellipse(cx, cy, NIGHT_DOT_R, fill("#FF000000", i5), i4), i3)
               + part(crescent(argb("E9E2D2")), i3, aod=0) + part(crescent(argb(MIST)), i3, alpha=0, aod=255))
    now = (group("sun", sun, i2, show=DAY)
           + group("crescent", upright, i2, box=(195, 0, 60, 36), angle="0 - %s / 4" % NOW, show="!(%s)" % DAY))
    out.append("%s<!-- now: the sun, with its glow, by day (a black disc rimmed in gold always-on); the crescent by\n"
               "%s     night (in mist always-on). Gold whatever the accent. -->\n" % (i1, i1))
    out.append(group("now", now, i1, angle="%s / 4" % NOW))
    return "".join(out)


def block(ind):
    i1 = ind + "    "
    slot = ('%s<ComplicationSlot slotId="7" x="0" y="0" width="450" height="450"\n'
            '%s    supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE" displayName="slot_day_dial">\n'
            '%s    <DefaultProviderPolicy\n'
            '%s        primaryProvider="world.taqwa.app/world.taqwa.app.wear.complication.DayDialSource"\n'
            '%s        primaryProviderType="RANGED_VALUE"\n'
            '%s        defaultSystemProvider="EMPTY"\n'
            '%s        defaultSystemProviderType="EMPTY" />\n'
            '%s    <BoundingBox x="7" y="0" width="0" height="0" />\n'
            '%s    <Complication type="RANGED_VALUE" />\n'
            '%s</ComplicationSlot>\n'
            % ((ind,) * 10))
    # Drawn only on the Sun style (the Condition, which reads the configuration alone), and only
    # from the dial's own data: the battery that stands in for a slot with no source of its own has
    # no 2 * 4096 in its max. That, and whether slots 0 and 1 have their data, is the dial's alpha,
    # since a Transform follows the data and a Condition may not.
    dial_part = condition("sunDial", '[CONFIGURATION.style] == "sun"', group("dial", dial(i1), i1, show=HAS_DATA), ind)
    frame = condition("sunFrame", '[CONFIGURATION.style] == "sun"',
                      group("sun_frame", frame_rings(i1 + "        "), i1 + "    ", show="!(%s)" % HAS_DATA), ind)
    return ("%s%s\n"
            "%s<!-- The Sun's ring alone, while its three slots have not all got their data (a feed that has run\n"
            "%s     out leaves this frame and the time) -->\n%s"
            "%s<!-- The Sun's dial itself (faces.js sunFace), from slot 7's day dial and slots 0 and 1 -->\n%s"
            "%s<!-- Slot 7: the day dial's geometry for the Sun style, which the dial above reads; no room of its own,\n"
            "%s     so that a screen reader is not given it (the header) -->\n%s"
            "%s%s" % (ind, BEGIN, ind, ind, frame, ind, dial_part, ind, ind, slot, ind, END))


def main():
    check = "--check" in sys.argv[1:]
    with open(FACE_XML, encoding="utf-8") as fh:
        xml = fh.read()
    start = xml.index(BEGIN)
    line_start = xml.rindex("\n", 0, start) + 1
    ind = xml[line_start:start]
    end = xml.index(END, start) + len(END)
    new = xml[:line_start] + block(ind) + xml[end:]
    if check:
        if new != xml:
            sys.exit("tools/face-sun.py: the Sun's block in watchface.xml is out of date; run tools/face-sun.py")
        print("tools/face-sun.py: up to date")
        return
    with open(FACE_XML, "w", encoding="utf-8") as fh:
        fh.write(new)
    print("tools/face-sun.py: wrote the Sun's block (%d lines)" % block(ind).count("\n"))


if __name__ == "__main__":
    main()
