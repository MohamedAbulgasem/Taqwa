#!/usr/bin/env python3
"""Writes the centre of the Taqwa watch face (:watchface), the part Arch, Ring and Sun share, with
the Ring's own ring and the wearer's two slots, into the face-centre.py blocks of
watchface/src/main/res/raw/watchface.xml:

    python3 tools/face-centre.py            # rewrites the blocks; the output is the same on every run
    python3 tools/face-centre.py --check    # says whether the blocks are up to date, and changes nothing

The centre is faces.js's centre(): five lines in four voices (spec 3.4), the Hijri date in mist, the
Gregorian in ink, the time, the prayer ("ASR 16:10": the label in the accent, the time in ink) and
the countdown in mist; the Taqwa mark above them on Ring and Sun. Its pieces sit where their data is
read: the prayer line in slot 0 (the next prayer's title and text), the two dates in slot 2, the
time and the mark in the scene, from the face's own clock, and the countdown in the scene too, from
slot 6's text read across slots, since slot 6 has no room of its own (Task 12: a slot with room is
an item for a screen reader, the face header says why).

How each line is written follows the phone (spec 3.3, 3.5), from the settings slot 7's title and text
carry (ComplicationFields.faceSettings in :wearApp; the expressions below read them):

- right to left (Arabic, Urdu): each line in Noto Sans Arabic, at faces.js's Arabic sizes, with no
  letter spacing (Arabic's letters join), and the prayer line's time to the left of its name;
- twelve hours: the time smaller (55 units on Ring and Sun, 47 on Arch, so that "12:58 PM" clears
  the frame), the hour from 1 to 12, and the day period after it in reading order (to the right in
  Latin, to the left in Arabic), a fifth of the time's size (Arabic's 0.27), top-aligned with the
  figures; the markers are the phone's (AM and PM, ص and م);
- the digits: the clock is written digit by digit in the phone's own set, in Noto Sans Arabic when
  the set is Arabic-Indic or extended Arabic-Indic (and the prayer's time with it).

With no feed stored, slot 7 has no data: the face then writes its clock as the watch does (its own
12 or 24 hours and markers, Western digits), left to right.

The wearer's slots (spec 3.2) are two along the bottom on every style: on Arch, Ring and Sun at
faces.js's userComps places, and on the Moon curved along its bottom between the five and seven
o'clock indices (option (d), Mohamed's choice of 23 October; tools/face-moon.py moon_slot() draws
them). When one is filled the centre moves up to LAYOUT_COMPS, Ring's and Sun's mark gives way, and
the Moon's lower half re-lays (face-moon.py). A ComplicationSlot's place cannot follow the style (it
is the Scene's alone, with no Transform, in a Condition or a ListOption in none of formats 2 to 5),
and its box is where it draws, what the editor outlines and where a tap opens its source. So each
slot's box is its bottom place alone, the same on every style, and every style draws the slot inside
it (Task 10's review, fix round 1: one box over two places took the taps on the time; the Moon's
ring at nine, option (a), drew slot 4's data where no tap reached it). A long value is scaled down
to its room (value_part(), and on the Moon moon_slot()).

A line drawn in two voices is one Text of several Font runs (the runtime takes them, each in its own
family, size and colour), so the runtime measures it and centres it as a whole, in every language.
Where its two voices dim apart always-on, the Text is drawn twice, each copy with the other voice's
runs transparent, so the two copies lie letter on letter and each takes its own alpha. The day
period's copy is lifted so that its marker is top-aligned with the time. A run of only spaces is
dropped, so the gap between the voices is a transparent full stop of the width faces.js leaves.
Runs are laid out left to right whatever their script, so a right-to-left line is written in the
order it is seen.

Every switch that follows the data is an alpha Transform (the face header): a Group shows its
piece at 255 or not at 0, and exactly one of a set shows. The always-on dimming is an AMBIENT
Variant on a part inside, never on the Group the Transform is on (Ruling 25). The Conditions read
the configuration alone.

faces.js (docs/superpowers/specs/2026-10-19-watch-companion) draws in 200 units and the face is 450:
every length here is that drawing's x 2.25.
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FACE_XML = os.path.join(ROOT, "watchface", "src", "main", "res", "raw", "watchface.xml")

K = 2.25
C = 225.0

INK, MIST, SLATE, HAIR = "F1F3F1", "8C948F", "5A625D", "232825"
ACCENT, ACCENT_LIGHT = "[CONFIGURATION.accent.0]", "[CONFIGURATION.accent.1]"
CLEAR = "#00000000"

# Always-on alphas: faces.js's opacities, and the part-level levels a colour is dimmed to (mist to
# slate's level 166, ink to mist's 151), as the other styles have them.
A_CLOCK, A_LABEL, A_COUNT, MIST_TO_SLATE, INK_TO_MIST = 209, 179, 217, 166, 151

# The fonts' vertical metrics (hhea ascent and descent, em): the runtime centres a line box on its
# PartText's middle, so a baseline lies (ascent - descent) / 2 of the size below the middle.
METRICS = {"manrope": (1.066, 0.3), "noto": (1.374, 0.738)}
FAMILY = {
    "manrope_light": "manrope", "manrope_semibold": "manrope", "manrope_extrabold": "manrope",
    "noto_sans_arabic_subset_v2_light": "noto", "noto_sans_arabic_subset_v2_semibold": "noto", "noto_sans_arabic_subset_v2_bold": "noto",
}
# Manrope Light's full stop, the transparent glyph a gap is made of (em).
STOP_ADVANCE = 0.2365
# ---- The face's settings, from slot 7 (ComplicationFields.faceSettings)

S7 = '[COMPLICATION.7.TYPE] == "RANGED_VALUE"'
T7, X7 = "[COMPLICATION.7.TITLE]", "[COMPLICATION.7.TEXT]"
RTL = '(%s && subText(%s, 0, 1) == "R")' % (S7, T7)
TWELVE = '((%s && subText(%s, 1, 2) == "1") || (!(%s) && ![IS_24_HOUR_MODE]))' % (S7, T7, S7)
ARAB_NUMS = '(%s && subText(%s, 2, 3) == "A")' % (S7, T7)
DIGITS = '(%s ? %s : "0123456789")' % (S7, X7)
MARKER = ("(%s ? ([AMPM_STATE] == 0 ? subText(%s, 3, textLength(%s)) : subText(%s, 10, textLength(%s))) : [AMPM_STRING])"
          % (S7, T7, T7, X7, X7))


def digit(n):
    return "subText(%s, %s, %s + 1)" % (DIGITS, n, n)


ARCH = '[CONFIGURATION.style] == "arch"'

# A wearer's slot is filled when it has data of a type it draws.
USER_TYPES = ["SHORT_TEXT", "RANGED_VALUE", "GOAL_PROGRESS", "MONOCHROMATIC_IMAGE", "SMALL_IMAGE"]
COMPS = "(%s)" % " || ".join('[COMPLICATION.%d.TYPE] == "%s"' % (slot, t) for slot in (4, 5) for t in USER_TYPES)
# Each slot's order of preference (its supportedTypes): a source that offers several types gives the
# first of them. Neither slot draws a share (the Moon's ring at nine, which drew slot 4's, went with
# option (d)), so both ask for the short text first, whose text is never missing (a ranged value's
# may be, which would leave the slot its icon alone).
SLOT_TYPES = {4: ["SHORT_TEXT", "RANGED_VALUE", "GOAL_PROGRESS", "MONOCHROMATIC_IMAGE", "SMALL_IMAGE"],
              5: ["SHORT_TEXT", "RANGED_VALUE", "GOAL_PROGRESS", "MONOCHROMATIC_IMAGE", "SMALL_IMAGE"]}

# ---- faces.js's layouts (units): the baselines of the five lines, and the wearer's slots'

LAYOUT = {"ring": dict(h=54, g=70, t=126, p=151, c=167), "arch": dict(h=57, g=73, t=127, p=151, c=167)}
# With the wearer's slots filled, Arch's two dates stay where they are (57 and 73; faces.js lifts them
# a unit, to 56 and 72): its Hijri line sits in the crown, and a unit higher the crown is too narrow
# for the longest forms (tools/watchface/hijri-fit.py: Bengali's 17-character months come to 5.7 px
# from the lit stroke there, where 7 is the face's margin). The time and the lines under it move.
LAYOUT_COMPS = {"ring": dict(h=49, g=65, t=119, p=143, c=158, u=179), "arch": dict(h=57, g=73, t=121, p=144, c=159, u=181)}
TIME_SIZE = {"ring": (60, 55), "arch": (56, 47)}   # 24-hour and twelve-hour, units

# Where the Task 6 and Task 7 lines were put by measuring them against the mockups on the emulator
# (the top of each box, px): a line keeps that place, and moves by faces.js's own difference when
# the wearer's slots are filled. A line in a voice they never drew is put by the metrics above.
MEASURED_TOP = {
    ("hijri", "ring"): 91, ("greg", "ring"): 125, ("greg", "arch"): 132,
    ("clock", "ring"): 142, ("clock", "arch"): 148, ("count", "ring"): 346, ("count", "arch"): 346,
}

# Arch's Hijri line sits in the crown (Task 7): a 240 px box about the axis, and a size by the
# text's length, the baseline kept: (most characters, or None for the rest; Latin size, px). The
# right-to-left line is faces.js's Arabic size, 12.5 units for 12, in the same steps.
ARCH_HIJRI_STEPS = [(15, 27.0), (19, 24.0), (None, 22.5)]
ARCH_HIJRI_TOPS = {27.0: (98, 40), 24.0: (99, 40), 22.5: (99, 41)}   # measured (Task 7): top, height
ARCH_HIJRI_BOX = (105, 240)
ARABIC_HIJRI = 12.5 / 12


# The gap between the prayer's label and its time (px): faces.js's line leaves 19 px of black between
# the R of ASR and the 1 of 16:10 (its space and the label's tracking), and the runtime's runs leave
# 3.5 px more than the gap's own width, so the gap is 15.5 (measured on the emulator).
PRAYER_GAP = 15.5


def rnd(x):
    return int(math.floor(x + 0.5))


def f(n):
    """A number as the face writes it: up to four decimals, no trailing zeros."""
    s = ("%.4f" % n).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def attr(name, value):
    """An attribute whose value may hold quotes: single-quoted, as the face writes expressions."""
    return "%s='%s'" % (name, esc(value)) if '"' in value else '%s="%s"' % (name, esc(value))


def variant(alpha, ind):
    return '%s<Variant mode="AMBIENT" target="alpha" value="%d" />\n' % (ind, alpha)


def group(name, body, ind, show=None, angle=None, aod=None):
    out = '%s<Group name="%s" x="0" y="0" width="450" height="450">\n%s' % (ind, name, body)
    if angle is not None:
        out += '%s    <Transform target="angle" %s />\n' % (ind, attr("value", angle))
    if show is not None:
        out += '%s    <Transform target="alpha" %s />\n' % (ind, attr("value", "(%s) ? 255 : 0" % show))
    if aod is not None:
        out += variant(aod, ind + "    ")
    return out + "%s</Group>\n" % ind


def condition(name, expr, body, ind, default=None):
    out = ("%s<Condition>\n%s    <Expressions>\n%s        <Expression name=\"%s\"><![CDATA[%s]]></Expression>\n"
           "%s    </Expressions>\n%s    <Compare expression=\"%s\">\n%s%s    </Compare>\n"
           % (ind, ind, ind, name, expr, ind, ind, name, body, ind))
    if default is not None:
        out += "%s    <Default>\n%s%s    </Default>\n" % (ind, default, ind)
    return out + "%s</Condition>\n" % ind


def indent(body, by="    "):
    return "".join((by + l if l.strip() else l) for l in body.splitlines(True))


def baseline_below_middle(runs, which=0):
    """How far below a PartText's middle the baseline of run [which] of these runs lies, and the
    height of the tallest. The runtime centres each run of a Text on the PartText's middle by its
    own font's metrics, and lays them out side by side (measured on the emulator: ASR at 25.9 px
    and 16:10 at 30.4 px, one Text, sit 1.7 px apart, as their own centrings put them, and a taller
    run beside them moves neither). So two runs of one Text share a baseline only when their fonts
    and sizes do, and a line drawn twice, one copy for each voice, puts each copy where its own run
    sits on the line."""
    asc, desc = (m * runs[which][1] for m in METRICS[FAMILY[runs[which][0]]])
    tallest = max(sum(METRICS[FAMILY[r[0]]]) * r[1] for r in runs)
    return (asc - desc) / 2, tallest


def box_for(baseline, runs, slack=4, which=0):
    """(top, height) of a PartText whose run [which] sits on baseline."""
    below, line = baseline_below_middle(runs, which)
    h = int(math.ceil(line / 2)) * 2 + slack
    return rnd(baseline - below - h / 2), h


# ---- Fonts and runs

def run(family, size, colour, template=None, literal=None, spacing=None, params=()):
    """One Font run: a literal text, or a Template whose %s are the expressions in params."""
    ls = '' if not spacing else ' letterSpacing="%s"' % f(spacing)
    if template is not None:
        body = "<Template><![CDATA[%s]]>%s</Template>" % (
            template, "".join('<Parameter %s />' % attr("expression", p) for p in params))
    else:
        body = "<![CDATA[%s]]>" % literal
    return (family, size, '<Font family="%s" size="%s" color="%s"%s>%s</Font>' % (family, f(size), colour, ls, body))


def gap(width):
    """A gap of width px between two runs: transparent full stops (a run of spaces is dropped), as
    many as keep their size at 16 px or under, so that the gap is never the line's tallest run."""
    n = int(math.ceil(width / (STOP_ADVANCE * 16)))
    return run("manrope_light", width / (n * STOP_ADVANCE), CLEAR, literal="." * n)


def part_text(box, runs, ind, align="CENTER", aod=None, y=None, ellipsis=False):
    """A PartText of one Text of the runs; box is (x, top, width, height); y its Transform, if it moves."""
    bx, top, w, h = box
    out = '%s<PartText x="%d" y="%d" width="%d" height="%d">\n' % (ind, bx, top, w, h)
    if y is not None:
        out += '%s    <Transform target="y" %s />\n' % (ind, attr("value", y))
    if aod is not None:
        out += variant(aod, ind + "    ")
    out += '%s    <Text align="%s"%s>\n' % (ind, align, ' ellipsis="TRUE"' if ellipsis else '')
    out += "".join("%s        %s\n" % (ind, r[2]) for r in runs)
    return out + "%s    </Text>\n%s</PartText>\n" % (ind, ind)


def follow(tops):
    """The y Transform of a part placed by style and by the wearer's slots: tops maps (style, comps)
    to the box's top; None when it never moves."""
    a0, a1, r0, r1 = tops[("arch", False)], tops[("arch", True)], tops[("ring", False)], tops[("ring", True)]
    if a0 == a1 == r0 == r1:
        return None
    if a0 == r0 and a1 == r1:
        return "%s ? %d : %d" % (COMPS, a1, a0)
    return "(%s) ? (%s ? %d : %d) : (%s ? %d : %d)" % (ARCH, COMPS, a1, a0, COMPS, r1, r0)


def follow_style(tops, style):
    """The y Transform of a part drawn on one style alone (tops as follow()'s): None when the wearer's
    slots do not move it."""
    a0, a1 = tops[(style, False)], tops[(style, True)]
    return None if a0 == a1 else "%s ? %d : %d" % (COMPS, a1, a0)


def tops_for(name, runs, line):
    """The box tops of a line (h, g, t, p or c) by style and comps: the measured one moved by faces.js's
    difference where there is one, the metrics' otherwise. Returns ({(style, comps): top}, height)."""
    out, height = {}, None
    for st in ("arch", "ring"):
        y0, y1 = LAYOUT[st][line] * K, LAYOUT_COMPS[st][line] * K
        top0, height = box_for(y0, runs)
        top1, _ = box_for(y1, runs)
        if (name, st) in MEASURED_TOP and all(FAMILY[r[0]] == "manrope" for r in runs):
            top0 = MEASURED_TOP[(name, st)]
            top1 = rnd(top0 + y1 - y0)
        out[(st, False)], out[(st, True)] = top0, top1
    return out, height


# ---- Slot 0: Ring's ring

V, N, X = "[COMPLICATION.RANGED_VALUE_VALUE]", "[COMPLICATION.RANGED_VALUE_MIN]", "[COMPLICATION.RANGED_VALUE_MAX]"
SHARE = "(clamp(%s, %s, %s) - %s) / (%s - %s)" % (V, N, X, N, X, N)
RING_R = 93 * K


def ring(ind):
    i2, i3 = ind + "        ", ind + "            "
    d = 2 * RING_R

    def arc(width, sweep, direction, alpha, aod, j):
        a = '' if alpha is None else ' alpha="%d"' % alpha
        return ('%s<PartDraw x="0" y="0" width="450" height="450"%s>\n'
                '%s    <Arc centerX="225" centerY="225" width="%s" height="%s"\n'
                '%s        startAngle="0" endAngle="0" direction="%s">\n'
                '%s        <Stroke thickness="%s" color="%s" cap="ROUND" />\n'
                '%s        <Transform target="endAngle" %s />\n'
                '%s    </Arc>\n%s%s</PartDraw>\n'
                % (j, a, j, f(d), f(d), j, direction, j, f(width), ACCENT, j, attr("value", sweep), j,
                   variant(aod, j + "    "), j))

    def glint(sign, j):
        k = j + "    "
        inner = ('%s<PartImage x="207" y="-2" width="36" height="36" tintColor="%s">\n'
                 '%s    <Image resource="glow" />\n%s</PartImage>\n'
                 '%s<PartDraw x="0" y="0" width="450" height="450">\n'
                 '%s    <Ellipse x="221.4" y="12.15" width="7.2" height="7.2">\n'
                 '%s        <Fill color="#E6FFFFFF" />\n%s    </Ellipse>\n%s</PartDraw>\n'
                 % (k, ACCENT_LIGHT, k, k, k, k, k, k, k))
        turned = group("glint_" + ("cw" if sign > 0 else "ccw"), inner, k, angle="%s360 * %s" % ("" if sign > 0 else "-", SHARE), aod=0)
        # not until the period has moved on a little (faces.js shows it above 1%), so that it does
        # not sit at twelve all through the first minutes
        return group("glint_" + ("cw" if sign > 0 else "ccw") + "_shown", turned, j, show="%s > 0.01" % SHARE)

    out = "%s<!-- the track -->\n" % i2
    out += ('%s<PartDraw x="0" y="0" width="450" height="450">\n'
            '%s    <Ellipse x="15.75" y="15.75" width="418.5" height="418.5">\n'
            '%s        <Stroke thickness="1.8" color="#E65A625D" />\n%s    </Ellipse>\n%s%s</PartDraw>\n'
            % (i2, i2, i2, i2, variant(142, i2 + "    "), i2))
    out += ("%s<!-- the arc, from twelve to where the period has reached: clockwise, or anticlockwise when the\n"
            "%s     phone reads right to left, as RingArc does (spec 3.5). A Stroke's thickness cannot change\n"
            "%s     always-on, so each arc is two: the awake one, 7.65 thick, out always-on, and the always-on\n"
            "%s     one, 4.5 thick at 60%%, out awake. Then the point of light: a soft glow in the accent's\n"
            "%s     light tint (a picture, because a gradient cannot take its colour from the configuration)\n"
            "%s     with a white core, 8 and 1.6 units in radius, carried round the ring by the progress. -->\n"
            % ((i2,) * 6))
    for sign, name in ((1, "ring_cw"), (-1, "ring_ccw")):
        sweep = "%s360 * %s" % ("" if sign > 0 else "-", SHARE)
        direction = "CLOCKWISE" if sign > 0 else "COUNTER_CLOCKWISE"
        body = arc(7.65, sweep, direction, None, 0, i3) + arc(4.5, sweep, direction, 0, 153, i3) + glint(sign, i3)
        out += group(name, body, i2, show=("!%s" % RTL) if sign > 0 else RTL)
    return condition("isRing", '[CONFIGURATION.style] == "ring"', out, ind)


# ---- Slot 0: the prayer line, "ASR 16:10"

def prayer(ind):
    i2, i3 = ind + "        ", ind + "            "
    title, text = "[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"
    out = ""
    for rtl in (False, True):
        for arab in (False, True):
            label_family = "noto_sans_arabic_subset_v2_bold" if rtl else "manrope_extrabold"
            label_size = (13 if rtl else 11.5) * K
            label_ls = None if rtl else 1.3 / 11.5
            time_family = "noto_sans_arabic_subset_v2_semibold" if arab else "manrope_semibold"
            time_size = 13.5 * K

            def runs(label_colour, time_colour):
                label = run(label_family, label_size, label_colour, template="%s", params=[title], spacing=label_ls)
                time = run(time_family, time_size, time_colour, template="%s", params=[text])
                g = gap(PRAYER_GAP)
                return [time, g, label] if rtl else [label, g, time]

            line = runs(ACCENT, "#FF" + INK)
            body = ""
            # each copy is put where its own run sits on the line's baseline
            for visible, colours, aod in (("label", (ACCENT, CLEAR), A_LABEL), ("time", (CLEAR, "#FF" + INK), INK_TO_MIST)):
                which = (2 if visible == "label" else 0) if rtl else (0 if visible == "label" else 2)
                tops = {}
                for style in ("arch", "ring"):
                    for comps in (False, True):
                        lay = LAYOUT_COMPS if comps else LAYOUT
                        tops[(style, comps)], h = box_for(lay[style]["p"] * K, line, which=which)
                body += part_text((0, tops[("ring", False)], 450, h), runs(*colours), i3, aod=aod, y=follow(tops))
            show = "%s%s && %s%s" % ("" if rtl else "!", RTL, "" if arab else "!", ARAB_NUMS)
            out += group("prayer_%s_%s" % ("rtl" if rtl else "ltr", "arab" if arab else "latn"), body, i2, show=show)
    head = ("%s<!-- the prayer line: the label in the accent (tracked capitals in Latin), then the time, one Text of\n"
            "%s     runs the runtime centres as a whole, drawn twice (the label's copy and the time's), each copy\n"
            "%s     put where its own run sits on the line and dimmed to its own level always-on; right to left\n"
            "%s     the time is on the left. One of the four shows, by the phone's direction and digits. -->\n" % ((i2,) * 4))
    return condition("hasCentre", HAS_CENTRE, head + out, ind)


HAS_CENTRE = '[CONFIGURATION.style] == "ring" || [CONFIGURATION.style] == "arch" || [CONFIGURATION.style] == "sun"'


# ---- Slot 2: the Hijri and Gregorian lines

# The centre's two lines in the phone's own words on Arch, Ring and Sun: the Gregorian date (slot 2's
# text, "SUN 18 OCT") in ink and the countdown (slot 6's text, "in 1 h 12 min") in mist. Their fonts,
# as (family, size px, letterSpacing em or None) by direction, and their box's x and width, the same
# on every style; tools/watchface/date-fit.py measures every form the phone writes in them.
CENTRE_LINE_FONT = {
    "greg": {False: ("manrope_extrabold", 13 * K, 1.3 / 13), True: ("noto_sans_arabic_subset_v2_bold", 14 * K, None)},
    "count": {False: ("manrope_semibold", 12 * K, None), True: ("noto_sans_arabic_subset_v2_semibold", 12.5 * K, None)},
}
LINE_BOX = (45, 360)

# Arch's Gregorian line right to left takes its size by its length and its month (Ruling 51a, the
# report's month rule A), the baseline kept. At 14 units for every form, 2,106 of the phones' forms
# came under the face's 7 px from Arch's lit stroke, where the arch narrows over the line's ends
# (Ruling 51): the Levantine and Iraqi two-word months (تشرين الأول, كانون الثاني …), which are
# exactly the forms of 19 characters or more, and the Thursdays and Saturdays of August as
# ar-SA, ar-EG, ar-LY and ar-AE write it (الخميس ١٠ أغسطس), the one one-word month under 7 px. So
# the line takes the first of these that applies: 19 characters or more, 11.25 units; else a form
# ending in أغسطس, 13 units, every day of August; else 14 units, the base face's own box, size and
# baseline, which keeps the sample's الأحد ١٨ أكتوبر as it was. Every form is 7 px or more from the
# lit stroke (tools/watchface/date-fit.py). An ar-SA, ar-EG, ar-LY or ar-AE phone's date changes
# size twice a year, into August and out of it, and a Levantine or Iraqi phone's twice, at its
# two-word months; a Maghreb or Urdu phone's never does. No table by length can keep the sample at
# 14 and pass, since الخميس ١٠ أغسطس has its 15 characters (Ruling 51a). The month is the phone's
# own spelling of August in phone-forms.json, read on the face by subText() of slot 2's text: a
# phone whose ICU spelt it otherwise would draw its August at 14 units, which plan 2 Task 7's
# forms diff on the phone emulator would show and date-fit.py would then measure.
# Each step is (name, the least characters it takes or None, the month the text ends in or None,
# size px); its Group is greg_arch_rtl_<name>, shown for its own tests and none of an earlier step's.
# Latin, and Ring and Sun, keep CENTRE_LINE_FONT's one size, which fits.
ARCH_GREGORIAN_AUGUST = "أغسطس"
ARCH_GREGORIAN_STEPS = [("long", 19, None, 11.25 * K), ("august", None, ARCH_GREGORIAN_AUGUST, 13 * K),
                        ("rest", None, None, 14 * K)]


def gregorian_step_tests(least, month):
    """One of ARCH_GREGORIAN_STEPS' tests of slot 2's text, as the face writes them: ([its terms],
    the expression true when it does not apply, or None for a step with no test). The month is the
    text's last characters, by subText() with its start kept inside the text."""
    text = "[COMPLICATION.TEXT]"
    n = "textLength(%s)" % text
    own, flipped = [], []
    if least is not None:
        own.append("%s > %d" % (n, least - 1))
        flipped.append("%s <= %d" % (n, least - 1))
    if month is not None:
        last = "subText(%s, clamp(%s - %d, 0, 99), %s)" % (text, n, len(month), n)
        own.append('%s == "%s"' % (last, month))
        flipped.append('%s != "%s"' % (last, month))
    if not own:
        return [], None
    return own, flipped[0] if len(own) == 1 else "!(%s)" % " && ".join(own)


def centre_line(name, rtl, size=None):
    """The Gregorian line ("greg") or the countdown ("count") right to left or not: its run, its
    box's tops by (style, comps) as tops_for() gives them, and the box's height (the Latin boxes'
    as measured in Task 6). size: another size than CENTRE_LINE_FONT's, the baseline kept (one of
    ARCH_GREGORIAN_STEPS')."""
    family, own, spacing = CENTRE_LINE_FONT[name][rtl]
    size = own if size is None else size
    colour, param, line, latin_h = {"greg": (INK, "[COMPLICATION.TEXT]", "g", 42),
                                    "count": (MIST, "[COMPLICATION.6.TEXT]", "c", 40)}[name]
    r = run(family, size, "#FF" + colour, template="%s", params=[param], spacing=spacing)
    tops, h = tops_for(name, [r], line)
    return r, tops, (h if rtl else latin_h)


def dates(ind):
    i1, i2, i3, i4 = ind + "    ", ind + "        ", ind + "            ", ind + "                "
    title = "[COMPLICATION.TITLE]"

    def hijri_run(rtl, size):
        if rtl:
            return run("noto_sans_arabic_subset_v2_semibold", size * ARABIC_HIJRI, "#FF" + MIST, template="%s", params=[title])
        return run("manrope_semibold", size, "#FF" + MIST, template="%s", params=[title])

    # Arch: the line's steps, each a Group shown by the text's length
    arch = ""
    for rtl in (False, True):
        steps = ""
        low = None
        for most, size in ARCH_HIJRI_STEPS:
            r = hijri_run(rtl, size)
            top0, h = ARCH_HIJRI_TOPS[size]
            if rtl:
                top0, h = box_for(LAYOUT["arch"]["h"] * K, [r])
            top1 = rnd(top0 + (LAYOUT_COMPS["arch"]["h"] - LAYOUT["arch"]["h"]) * K)
            y = "%s ? %d : %d" % (COMPS, top1, top0)
            part = part_text((ARCH_HIJRI_BOX[0], top0, ARCH_HIJRI_BOX[1], h), [r], i4, aod=MIST_TO_SLATE, y=y, ellipsis=True)
            n = "textLength(%s)" % title
            show = ("%s <= %d" % (n, most)) if low is None else ("%s > %d" % (n, low))
            if low is not None and most is not None:
                show = "%s > %d && %s <= %d" % (n, low, n, most)
            steps += group("hijri_arch_%s_%s" % ("rtl" if rtl else "ltr", most or "more"), part, i3, show=show)
            low = most
        arch += group("hijri_arch_" + ("rtl" if rtl else "ltr"), steps, i2, show=RTL if rtl else "!" + RTL)
    # Ring and Sun: the whole line
    ring_ = ""
    for rtl in (False, True):
        r = hijri_run(rtl, 27.0)
        if rtl:
            top0, h = box_for(LAYOUT["ring"]["h"] * K, [r])
        else:
            top0, h = MEASURED_TOP[("hijri", "ring")], 40
        top1 = rnd(top0 + (LAYOUT_COMPS["ring"]["h"] - LAYOUT["ring"]["h"]) * K)
        part = part_text((45, top0, 360, h), [r], i3, aod=MIST_TO_SLATE, y="%s ? %d : %d" % (COMPS, top1, top0), ellipsis=True)
        ring_ += group("hijri_ring_" + ("rtl" if rtl else "ltr"), part, i2, show=RTL if rtl else "!" + RTL)
    hijri = condition("archHijri", ARCH, arch, i1, default=indent(ring_))

    greg = ""
    r, tops, h = centre_line("greg", False)
    part = part_text((LINE_BOX[0], tops[("ring", False)], LINE_BOX[1], h), [r], i2, aod=INK_TO_MIST, y=follow(tops), ellipsis=True)
    greg += group("greg_ltr", part, i1, show="!" + RTL)
    # right to left: on Arch the line's steps, each a Group shown by the text's length and its
    # month (ARCH_GREGORIAN_STEPS: its own tests and none of an earlier step's), and on Ring and Sun
    # the whole line
    steps, earlier = "", []
    for name, least, month, size in ARCH_GREGORIAN_STEPS:
        r, tops, h = centre_line("greg", True, size)
        part = part_text((LINE_BOX[0], tops[("arch", False)], LINE_BOX[1], h), [r], i4, aod=INK_TO_MIST,
                         y=follow_style(tops, "arch"), ellipsis=True)
        own, not_own = gregorian_step_tests(least, month)
        show = " && ".join(earlier + own) or None
        steps += group("greg_arch_rtl_" + name, part, i3, show=show)
        if not_own:
            earlier.append(not_own)
    r, tops, h = centre_line("greg", True)
    ring_ = part_text((LINE_BOX[0], tops[("ring", False)], LINE_BOX[1], h), [r], i3, aod=INK_TO_MIST,
                      y=follow_style(tops, "ring"), ellipsis=True)
    greg += group("greg_rtl", condition("archGregorian", ARCH, steps, i2, default=indent(ring_)), i1, show=RTL)
    head = ("%s<!-- the Hijri date (title) in mist and the Gregorian (text) in ink, tracked capitals in Latin, Noto Sans\n"
            "%s     Arabic at faces.js's Arabic sizes right to left; on Arch the Hijri line in the crown by its length\n"
            "%s     (ARCH_HIJRI_STEPS, which tools/watchface/hijri-fit.py measures), and the right-to-left Gregorian\n"
            "%s     by its length and its month (ARCH_GREGORIAN_STEPS, which tools/watchface/date-fit.py measures) -->\n" % ((i1,) * 4))
    return condition("hasCentre", HAS_CENTRE, head + hijri + greg, ind)


# ---- The countdown, from slot 6 (drawn by the scene, just before the slot)

def countdown(ind):
    i1, i2 = ind + "    ", ind + "        "
    out = "%s<!-- the countdown, \"in 1 h 12 min\", in mist; 85%% always-on -->\n" % i1
    for rtl in (False, True):
        r, tops, h = centre_line("count", rtl)
        part = part_text((LINE_BOX[0], tops[("ring", False)], LINE_BOX[1], h), [r], i2, aod=A_COUNT, y=follow(tops), ellipsis=True)
        out += group("count_" + ("rtl" if rtl else "ltr"), part, i1, show=RTL if rtl else "!" + RTL)
    return condition("hasCentre", HAS_CENTRE, out, ind)


# ---- The scene: the time, and Ring's and Sun's mark

def time_run(family, size, colour, hour):
    return run(family, size, colour, template="%s%s:%s%s", params=[
        '%s >= 10 ? %s : ""' % (hour, digit("floor(%s / 10)" % hour)),
        digit("%s %% 10" % hour), digit("floor([MINUTE] / 10)"), digit("[MINUTE] % 10")])


def clock(style, ind):
    """The time for a style ("arch", or "ring" for Ring and Sun), each form in a Group of its own."""
    i1, i2, i3 = ind + "    ", ind + "        ", ind + "            "
    size24, size12 = (s * K for s in TIME_SIZE[style])
    out = ""
    t0, t1 = LAYOUT[style]["t"] * K, LAYOUT_COMPS[style]["t"] * K
    measured = MEASURED_TOP[("clock", style)]
    # how far the measured Manrope line (a box 180 high) sits from where the metrics put it: every
    # other form of the time is moved as much
    below, _ = baseline_below_middle([("manrope_light", size24)])
    nudge = measured - (t0 - below - 90)

    def place(runs, baseline_shift=0.0, height=None, which=0):
        below_, line = baseline_below_middle(runs, which)
        h = height or (int(math.ceil(line / 2)) * 2 + 4)
        top0 = rnd(t0 + baseline_shift - below_ - h / 2 + nudge)
        top1 = rnd(top0 + t1 - t0)
        return top0, top1, h

    for arab in (False, True):
        fam = "noto_sans_arabic_subset_v2_light" if arab else "manrope_light"
        # 24 hours
        r = time_run(fam, size24, "#FF" + INK, "[HOUR_0_23]")
        if arab:
            top0, top1, h = place([r])
        else:
            top0, top1, h = measured, rnd(measured + t1 - t0), 180
        part = part_text((45, top0, 360, h), [r], i2, aod=A_CLOCK, y="%s ? %d : %d" % (COMPS, top1, top0))
        out += group("clock24_%s" % ("arab" if arab else "latn"), part, i1,
                     show="!%s && %s%s" % (TWELVE, "" if arab else "!", ARAB_NUMS))
    for arab in (False, True):
        for rtl in (False, True):
            fam = "noto_sans_arabic_subset_v2_light" if arab else "manrope_light"
            ps = size12 * (0.27 if rtl else 0.2)
            mark_family = "noto_sans_arabic_subset_v2_bold" if rtl else "manrope_extrabold"
            mark_ls = None if rtl else 0.6 * K / ps
            g = gap(0.07 * size12)

            def runs(time_colour, mark_colour):
                t = time_run(fam, size12, time_colour, "[HOUR_1_12]")
                m = run(mark_family, ps, mark_colour, template="%s", params=[MARKER], spacing=mark_ls)
                return [m, g, t] if rtl else [t, g, m]

            # the marker's copy is lifted so that the marker's top meets the figures' (faces.js:
            # 0.72 of each size above the baseline, Arabic's marker 0.9 of that)
            lift = 0.72 * size12 - 0.72 * ps * (0.9 if rtl else 1)
            line = runs("#FF" + INK, "#FF" + INK)
            t_at, m_at = (2, 0) if rtl else (0, 2)
            top0, top1, h = place(line, which=t_at)
            mtop0, mtop1, _ = place(line, baseline_shift=-lift, height=h, which=m_at)
            body = part_text((25, top0, 400, h), runs("#FF" + INK, CLEAR), i3, aod=A_CLOCK, y="%s ? %d : %d" % (COMPS, top1, top0))
            body += part_text((25, mtop0, 400, h), runs("#00" + INK, "#FF" + INK), i3, aod=INK_TO_MIST,
                              y="%s ? %d : %d" % (COMPS, mtop1, mtop0))
            out += group("clock12_%s_%s" % ("arab" if arab else "latn", "rtl" if rtl else "ltr"), body, i1,
                         show="%s && %s%s && %s%s" % (TWELVE, "" if arab else "!", ARAB_NUMS, "" if rtl else "!", RTL))
    return out


def scene_clock(ind):
    i1, i2 = ind + "    ", ind + "        "
    mark = ('%s<PartImage x="213" y="52" width="24" height="30" tintColor="#FF%s">\n'
            '%s    <Variant mode="AMBIENT" target="alpha" value="%d" />\n'
            '%s    <Image resource="arch_mark" />\n%s</PartImage>\n'
            '%s<PartDraw x="0" y="0" width="450" height="450">\n'
            '%s    <Ellipse x="222.98" y="57.67" width="4.04" height="4.04">\n'
            '%s        <Fill color="%s" />\n%s    </Ellipse>\n%s%s</PartDraw>\n'
            % (i2, MIST, i2, MIST_TO_SLATE, i2, i2, i2, i2, i2, ACCENT, i2, variant(0, i2 + "    "), i2))
    ring_body = ("%s<!-- the Taqwa mark above the dates: the arch in mist (dimmed to slate's level always-on) and its\n"
                 "%s     lamp in the accent (awake only); it gives way to the wearer's slots (faces.js: no mark with\n"
                 "%s     them) -->\n" % (i1, i1, i1))
    ring_body += group("mark", mark, i1, show="!" + COMPS)
    ring_body += clock("ring", ind)
    out = ("%s<!-- The face's own clock: the time as the phone writes it, 24-hour with the hour unpadded or\n"
           "%s     twelve-hour with its day period, in the phone's digits, in Manrope Light (Noto Sans Arabic\n"
           "%s     Light for Arabic-Indic digits), untracked (faces.js asks for -0.02 em, which the runtime does not\n"
           "%s     draw; the attribute only moved the line right). Ring's and Sun's is 60 units (135; 55, 123.75,\n"
           "%s     on twelve hours), its baseline at 126 (283.5), under the Taqwa mark; Arch's is 56 (126; 47,\n"
           "%s     105.75), its baseline at 127 (285.75), and the arch itself is its mark. One form shows. -->\n"
           % ((ind,) * 6))
    out += condition("ringOrSun", '[CONFIGURATION.style] == "ring" || [CONFIGURATION.style] == "sun"', ring_body, ind)
    out += condition("isArch", ARCH, clock("arch", ind), ind)
    return out


# ---- Slots 4 and 5: the wearer's own

# Their places along the bottom (x, y, width, height, px). Each slot's box is its own place and no
# more: the box is where the slot draws, what the editor outlines and where a tap opens its source,
# so neither reaches the time or the lines above it. They meet at x 234, between the left value's
# room and the right slot's icon, and run from under the countdown to the screen's edge, a target a
# finger can find. The slots are declared after every fixed slot (users() is written after slot 6):
# the runtime gives a tap to the last slot whose box holds it, and the fixed slots span the face
# (0 and 2 since Task 12, 1, 6 and 7 too before it), so a wearer's slot declared before them never
# had a tap (Task 10's review).
SLOT_BOX = {4: (110, 368, 124, 68), 5: (234, 368, 106, 68)}

# A value is written at faces.js's size, 10 units (22.5 px; 9.5 units along the Moon's bottom), and
# scaled down about its start and its baseline when it would pass its room: VALUE_ROOM px at the
# bottom, the left value's from 75 units to its slot's edge, and the right one's too on Ring and Sun,
# where the ring crosses the baseline at x 329, so that a source looks the same in either slot and
# every style; on the Moon each slot's run along the curve, less its icon (face-moon.py moon_room()).
# A face cannot measure a text, so its width is estimated from its characters: each class at Manrope
# ExtraBold's widest advance in it (em: the narrow figure 1, the separators, the space, and the per
# cent sign with the wide letters of "Wed" and "Mon"), the rest at the figures' 0.66, which most
# letters come under. tools/watchface/slot-fit.py checks the estimate against the font over the
# values the watch's own sources write. Below MIN_SCALE the value ends in an ellipsis.
VALUE_ROOM, MIN_SCALE = 61, 0.5
VALUE_EM = [("1", 0.454), (",.", 0.32), (":", 0.36), (" ", 0.2), ("%MWmw", 0.99)]
VALUE_EM_REST, VALUE_EM_CHARS = 0.66, 8
VALUE_SIZE = 10 * K


def value_em(text):
    """An expression for the advance of text in em, estimated character by character (VALUE_EM)."""
    terms = []
    for i in range(VALUE_EM_CHARS):
        c = "subText(%s, %d, %d)" % (text, i, i + 1)
        expr = f(VALUE_EM_REST)
        for chars, em in reversed(VALUE_EM):
            expr = "(%s) ? %s : (%s)" % (" || ".join('%s == "%s"' % (c, ch) for ch in chars), f(em), expr)
        terms.append("(textLength(%s) > %d ? (%s) : 0)" % (text, i, expr))
    terms.append("clamp(textLength(%s) - %d, 0, 99) * %s" % (text, VALUE_EM_CHARS, f(VALUE_EM_REST)))
    return " + ".join(terms)


def value_scale(text, size, room):
    return "clamp(%d / (%s * (%s) + 0.01), %s, 1)" % (room, f(size), value_em(text), f(MIN_SCALE))


def value_part(text, box, size, room, ind, align="START", y=None):
    """A wearer's value: one PartText of text at size, as wide as room at MIN_SCALE so that the runtime
    lays it out whole, scaled about its start (CENTER: its middle) and its baseline to fit room."""
    bx, top, w, h = box
    r = run("manrope_extrabold", size, "#FF" + INK, template="%s", params=[text])
    below, _ = baseline_below_middle([r])
    scale = value_scale(text, size, room)
    out = ('%s<PartText x="%d" y="%d" width="%d" height="%d" pivotX="%s" pivotY="%s">\n'
           % (ind, bx, top, w, h, "0" if align == "START" else "0.5", f(round((h / 2 + below) / h, 4))))
    if y is not None:
        out += '%s    <Transform target="y" %s />\n' % (ind, attr("value", y))
    out += '%s    <Transform target="scaleX" %s />\n' % (ind, attr("value", scale))
    out += '%s    <Transform target="scaleY" %s />\n' % (ind, attr("value", scale))
    out += variant(INK_TO_MIST, ind + "    ")
    out += '%s    <Text align="%s" ellipsis="TRUE">\n%s        %s\n%s    </Text>\n%s</PartText>\n' % (ind, align, ind, r[2], ind, ind)
    return out


def user_slot(slot, ind):
    sx, sy, sw, sh = SLOT_BOX[slot]
    i1, i3, i4 = ind + "    ", ind + "            ", ind + "                "
    # faces.js userComps: the icon (its box 10 units square) at 63.2 or 105 units, the value from 75 or
    # 116, on the baseline u; mist and ink, slate's and mist's levels always-on
    icon_x, text_x = (63.2, 75) if slot == 4 else (105, 116)

    def bottom(kind, j):
        out = ""
        ic = 10 * K
        tops = {}
        for style in ("arch", "ring"):
            u = LAYOUT_COMPS[style]["u"] * K
            tops[style] = (rnd(u + 1 * K - ic) - sy, rnd(u - 0.383 * VALUE_SIZE - 17) - sy)
        icon_y = "(%s) ? %d : %d" % (ARCH, tops["arch"][0], tops["ring"][0])
        text_y = "(%s) ? %d : %d" % (ARCH, tops["arch"][1], tops["ring"][1])
        ix = rnd(icon_x * K) - sx
        if kind != "SMALL_IMAGE":
            out += ('%s<PartImage x="%d" y="%d" width="%d" height="%d" tintColor="#FF%s">\n'
                    '%s    <Transform target="y" %s />\n%s'
                    '%s    <Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]" />\n%s</PartImage>\n'
                    % (j, ix, tops["ring"][0], rnd(ic), rnd(ic), MIST, j, attr("value", icon_y),
                       variant(MIST_TO_SLATE, j + "    "), j, j))
        else:
            out += ('%s<PartImage x="%d" y="%d" width="%d" height="%d">\n'
                    '%s    <Transform target="y" %s />\n%s'
                    '%s    <Image resource="[COMPLICATION.SMALL_IMAGE]" />\n%s</PartImage>\n'
                    % (j, ix, tops["ring"][0], rnd(ic), rnd(ic), j, attr("value", icon_y),
                       variant(INK_TO_MIST, j + "    "), j, j))
        if kind in ("RANGED_VALUE", "GOAL_PROGRESS", "SHORT_TEXT"):
            out += value_part("[COMPLICATION.TEXT]", (rnd(text_x * K) - sx, tops["ring"][1], rnd(VALUE_ROOM / MIN_SCALE), 34),
                              VALUE_SIZE, VALUE_ROOM, j, y=text_y)
        return out

    moon = face_moon()
    comps = ""
    for kind in USER_TYPES:
        body = condition("offMoon", '[CONFIGURATION.style] != "moon"', bottom(kind, i4), i3)
        body += condition("onMoon", '[CONFIGURATION.style] == "moon"', moon.moon_slot(slot, kind, (sx, sy), i4), i3)
        comps += '%s<Complication type="%s">\n%s%s</Complication>\n' % (i1, kind, body, i1)
    return ('%s<ComplicationSlot slotId="%d" x="%d" y="%d" width="%d" height="%d"\n'
            '%s    supportedTypes="%s EMPTY" isCustomizable="TRUE" displayName="%s">\n'
            '%s    <BoundingBox x="0" y="0" width="%d" height="%d" />\n%s%s</ComplicationSlot>\n'
            % (ind, slot, sx, sy, sw, sh, ind, " ".join(SLOT_TYPES[slot]), "slot_left" if slot == 4 else "slot_right",
               ind, sw, sh, comps, ind))


def face_moon():
    """tools/face-moon.py, which draws the Moon's curved slots (moon_slot()); read when the slots are
    written, since it reads this file for its own settings."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("face_moon", os.path.join(os.path.dirname(os.path.abspath(__file__)), "face-moon.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def users(ind):
    out = ("%s<!-- Slots 4 and 5: the wearer's own, empty until they choose (spec 3.2), two along the bottom\n"
           "%s     (faces.js userComps: the icon in mist, the value in ink, scaled down to its room when it is\n"
           "%s     long; slate's and mist's levels always-on), each box its own place alone. They come after\n"
           "%s     every fixed slot, because the runtime gives a tap to the last slot whose box holds it. On the\n"
           "%s     Moon they curve along the bottom inside the same boxes, between the five and seven o'clock\n"
           "%s     indices (option (d), tools/face-moon.py moon_slot()). With either filled, the centre moves up\n"
           "%s     (LAYOUT_COMPS, tools/face-centre.py), and on the Moon its lower half re-lays (face-moon.py). -->\n"
           % ((ind,) * 7))
    return out + user_slot(4, ind) + user_slot(5, ind)


# ---- Writing the blocks

BLOCKS = {
    "ring": ring,
    "prayer": prayer,
    "dates": dates,
    "countdown": countdown,
    "clock": scene_clock,
    "users": users,
}


def begin(name):
    return "<!-- face-centre.py: begin %s (written by tools/face-centre.py; do not edit by hand) -->" % name


def end(name):
    return "<!-- face-centre.py: end %s -->" % name


def write(xml):
    for name, make in BLOCKS.items():
        start = xml.index(begin(name))
        line_start = xml.rindex("\n", 0, start) + 1
        ind = xml[line_start:start]
        stop = xml.index(end(name), start) + len(end(name))
        xml = xml[:line_start] + "%s%s\n%s%s%s" % (ind, begin(name), make(ind), ind, end(name)) + xml[stop:]
    return xml


def main():
    check = "--check" in sys.argv[1:]
    with open(FACE_XML, encoding="utf-8") as fh:
        xml = fh.read()
    new = write(xml)
    if check:
        if new != xml:
            sys.exit("tools/face-centre.py: the centre's blocks in watchface.xml are out of date; run tools/face-centre.py")
        print("tools/face-centre.py: up to date")
        return
    with open(FACE_XML, "w", encoding="utf-8") as fh:
        fh.write(new)
    print("tools/face-centre.py: wrote the centre's blocks")


if __name__ == "__main__":
    main()
