#!/usr/bin/env python3
"""Writes the Moon style of the Taqwa watch face (:watchface) from faces.js's moonFace, into the
face-moon.py blocks of watchface/src/main/res/raw/watchface.xml:

    python3 tools/face-moon.py            # rewrites the blocks; the output is the same on every run
    python3 tools/face-moon.py --check    # says whether the blocks are up to date, and changes nothing

The Moon is analog (spec 3.4): faceted dauphine hands with a fine black keyline and a neutral
seconds hand; the Taqwa arch at twelve; the sunray finish awake only; the accent arc on the chapter
ring from the hour hand to the next prayer, with a pointer, what has passed since the last prayer
faint, at most a turn; the prayer and its countdown curved over the top; a date window at three with
the day number at its outer end; the moon at six with the Hijri date curved under it. With either of
the wearer's slots filled, its lower half re-lays (option (d), Mohamed's choice of 23 October): the
moon window rises, the Hijri date tightens under it, and the two slots curve along the bottom
(moon_slot(), which tools/face-centre.py calls for the slots it writes). It never mirrors: the hands,
the ring and the arc turn clockwise in every language. Its pieces sit where their data is read:

- the dial (the finish, the ticks, the indices and the mark) and the hands are the scene's own, from
  the face's clock;
- the chapter arc, its pointer and the prayer line are in slot 0, the next prayer's RANGED_VALUE.
  Its min and max are the period's start and end and its value is now, all in minutes (from the
  date's Dhuhr, which the Sun needs; here only differences are taken), so the arc is the minutes
  left from the hour hand, half a degree a minute, and the faint part the minutes gone behind it.
  The hour hand's own angle is the face's clock, its hour and minute, so the arc leaves from the hand
  wherever the watch's zone puts it;
- the Hijri date is in slot 2 (the dates' LONG_TEXT); the countdown is drawn by the scene from slot
  6 (the next prayer's LONG_TEXT), and the date window and the moon by the scene from slot 3 (the
  dates' SHORT_TEXT: the weekday in its title, the day in its text, the moon's lit part as its
  monochromatic image, which the face lays over a dark disc of its own and tints), each read across
  slots, because slots 3 and 6 have no room of their own (Task 12: the face header);
- the wearer's values and icons are in slots 4 and 5, inside their boxes; the moon window and the
  Hijri date read whether either is filled across slots (COMPS).

A part a slot draws is placed in the slot's own coordinates, and a complication draws only inside its
slot's bounding shape: so slot 2, which the other styles keep about the centre, spans the face. The
countdown, drawn by the scene, is placed in the face's own, and the two windows in the box slot 3
drew them in, which masks them as the slot's box did.

The always-on frame is the face header's (Ruling 25): every dimming is a Part's or a Group's alpha
in a Variant, and a thinner stroke is a second PartDraw, out while awake. Always-on the hour and
minute hands keep their awake pictures, the faceted dauphine and its black keyline, dimmed to
A_HANDS by their own Variants (Ruling 41). A dimmed picture lets what lies under it show through, so
each is laid on a black back of its own shape: the AnalogClock's other hand of its kind (it takes at
most two hour, two minute and two second hands), declared before it, since the clock draws its hands
in the order they are declared, and out while awake. The cap is dimmed the same way. The moon
window's near-black fill is black always-on (Ruling 46: lit by the measure, it was 2 points of the
Moon's always-on share, which reached 15.5% just after Fajr and Isha): the fill is a PartDraw of its
own at alpha 0 there, under its hairline, which stays. The date window keeps its fill.
Every switch that follows the data is an alpha Transform; the Conditions read the configuration
alone.

The right-to-left Moon (faces.js lang ar: the prayer's name to the right of its time, Noto Sans
Arabic a size up, the date window wider) is drawn beside the left-to-right one, each in a Group
shown by RTL, the phone's direction as slot 7 carries it (tools/face-centre.py reads the face's
settings). Its numbers, the prayer's time and the day in the window, are Noto Sans Arabic's when
the phone's digits are Arabic-Indic (ARAB_NUMS), and Manrope's otherwise, as faces.js has them.

faces.js (docs/superpowers/specs/2026-10-19-watch-companion) draws in 200 units and the face is
450: every length here is that drawing's x 2.25.
"""
import importlib.util
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FACE_XML = os.path.join(ROOT, "watchface", "src", "main", "res", "raw", "watchface.xml")

K = 2.25
C = 225.0

# faces.js's palette (C) and moonFace's own colours
INK, MIST, SLATE, HAIR, WELL = "F1F3F1", "8C948F", "5A625D", "232825", "0B0D0C"
MOON_WINDOW, MOON_DARK, MOON_LIT, MOON_LIT_AOD = "070908", "1A1E1C", "E8E2D2", "8C8A82"
ACCENT = "[CONFIGURATION.accent.0]"
CLEAR = "#00000000"

# Always-on alphas: faces.js's opacities (0.85, 0.7, 0.6, 0.22), and ink drawn at mist's level where
# faces.js turns an ink text to mist (151, as the other styles' prayer time).
A_85, A_70, A_60, A_22, INK_TO_MIST = 217, 179, 153, 56, 151
# The hands' always-on level (Ruling 41), tuned on the emulator against the other styles' always-on
# frames (plan 1 Task 13): the hands are the Moon's time, so their lit facet is drawn at the level
# Arch, Ring and Sun draw their time at (face-centre.py's A_CLOCK), the brightest thing on the face,
# and their shaded facet falls to about mist's, the level of the face's texts.
A_HANDS = 209

# The slots' corners: a part a slot draws is placed in the slot's own coordinates.
SLOT2 = (0, 0)
SLOT3 = (173, 204)
SLOT6 = (0, 0)

# The hour hand's angle on the face's own clock. The AnalogClock's hour hand moves once a minute
# (found on the emulator), so the arc leaves from HAND, the hour and minute on the dial; the
# instants the arc is measured to are taken from the same clock to the second ([SECONDS_IN_DAY]),
# so that the pointer and the faint arc's start stand still while the seconds pass.
HAND = "([HOUR_0_11] * 60 + [MINUTE]) / 2"
NOW_ANGLE = "([SECONDS_IN_DAY] % 43200) / 120"
V, N, X = "[COMPLICATION.RANGED_VALUE_VALUE]", "[COMPLICATION.RANGED_VALUE_MIN]", "[COMPLICATION.RANGED_VALUE_MAX]"
NOW = "clamp(%s, %s, %s)" % (V, N, X)
GONE = "clamp((%s - %s) / 2, 0, 360)" % (NOW, N)       # degrees behind now: the minutes gone, halved
LEFT = "clamp((%s - %s) / 2, 0, 359)" % (X, NOW)       # degrees ahead: the minutes left, halved, under a turn
SEC_ANGLE = "([SECONDS_IN_DAY] % 60) / 120"            # how far now is past the hand: this minute's seconds

# The faint arc runs from the period's start, NOW_ANGLE - GONE, to the hand. Through a period's first
# minute the two are the same angle, but the value is float32 and the clock double, so the start can
# land a float step past the hand; and the runtime draws a clockwise Arc whose end is behind its start,
# by however little, as a turn less that little (the face header). So the start is written from the
# hand back, by the degrees gone before this minute, never fewer than none: the same angle, and never
# past the hand.
FAINT_START = "%s - clamp(%s - %s, 0, 360)" % (HAND, GONE, SEC_ANGLE)

# The phone's direction and digits, as slot 7 carries them: the expressions are face-centre.py's.
_spec = importlib.util.spec_from_file_location("face_centre", os.path.join(os.path.dirname(os.path.abspath(__file__)), "face-centre.py"))
face_centre = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(face_centre)
RTL, ARAB_NUMS = face_centre.RTL, face_centre.ARAB_NUMS

# The curved prayer line is one TextCircular of three Font runs, the name, a gap and the time (right
# to left the time, the gap and the name, in the order they are seen: the runtime lays runs out left
# to right whatever their script), which the runtime measures and centres on twelve as a whole, as
# it does the centre's prayer line, one Text of runs (tools/face-centre.py). So the line is centred
# whatever the name is, in every language, and no width is estimated. It is drawn twice, the name's
# copy and the time's, each with the other voice's run transparent, so that the two lie letter on
# letter and each dims to its own level always-on. The runs do not keep one baseline, and where each
# sits depends on the line's other runs, so each copy's circle is put where its own run's baseline
# lies on R_PRAYER, as measured (RUN_LIFT); the two circles differ by a pixel or two, which moves the
# letters along the line by less than a pixel at its ends. On emulator-5558 the five names' lines
# (and Arabic's, in both digit sets) are centred within 1 px of faces.js's, and every baseline within
# 1 px of its 64.5 units (final review fix round 2; the estimate before it was up to 6 px out).
R_PRAYER, R_COUNT, R_HIJRI = 64.5 * K, 52.5 * K, 71 * K
# The countdown's path over the top and the Hijri date's along the bottom (from, to), degrees: the
# runtime cuts a curved text where its path ends (tools/watchface/moon-fit.py measures both).
COUNT_ARC, HIJRI_ARC = (-62, 62), (232, 128)
# The gap between the name and the time, px along the circle, by the line's direction and the time's
# digits: faces.js leaves an en space of its 12-unit font between them, and the runtime's runs leave
# other room beside a gap run than its own width (measured on the emulator against faces.js: ASR
# 16:10 left to right, العصر 4:10 and العصر ١٦:١٠ right to left).
PRAYER_GAP = {(False, False): 21.2, (True, False): 15.7, (True, True): 16.6}
# The runtime centres the line by its runs' advances, and a Latin name's advance carries half its
# tracking before its first letter, so the line's ink sits half a degree left of faces.js's for
# every name (measured on the five): the left-to-right line is turned back by that much.
PRAYER_TURN = {False: 0.5, True: 0.0}
# A TextCircular's baseline lies this much of the font's size inside the circle it is given, for a
# text read clockwise (measured on the emulator: 9.3 px at 25.9 px, 10.8 px at 29.25 px).
LIFT = 0.365
# The same for each run of the prayer line, by the line's direction and the run: the name, the time
# in Manrope, the time in Noto Sans Arabic (Arabic-Indic digits). Measured on the emulator, the five
# names in English, in Libya's Arabic (Western digits) and in Saudi Arabia's: a Noto run sits
# further out than a Manrope one, and a Manrope time beside an Arabic name further out than beside
# a Latin one.
RUN_LIFT = {(False, "label"): 0.36, (False, "time"): 0.376, (True, "label"): 0.30, (True, "time"): 0.343, (True, "time_arab"): 0.30}
# What each curved Latin text's tracking gives back, as the em per letter track() is given (fitted on
# the emulator so that each comes out as wide as faces.js's, to a fifth of a degree: the label on
# DHUHR and ASR, the time on 12:33 and 16:10, the countdown on "in 2 h 23 min"), and the Hijri
# date's own spacing, which narrows it as much (on "6 Jumada al-Ula").
TRACK_LABEL, TRACK_TIME, TRACK_COUNT = 1.27, 0.62, 0.66
HIJRI_SPACING = -0.015
# The right-to-left Hijri date's two runs (slot2()): the gap between the month and the day, px, and
# how far outside their circle the runs' baseline lies, of the size. Against the one text on
# emulator-5558, "٣٠ جمادى الأولى", which it draws whole, the two runs come out 0.3 degrees narrower,
# their gap 0.3 degrees less and their baseline 0.6 px further in (a gap of 7.8 px and a lift of
# 0.343 came out further from it, not nearer: the runtime's room beside a gap run is not its width).
HIJRI_GAP_RTL = 7.0
HIJRI_LIFT_RTL = LIFT

# A curved run that can be shaped into more glyphs than characters carries PAD at the end where
# those glyphs are drawn (Ruling 37). The runtime draws a curved text in its font sandbox with
# Canvas.drawTextOnPath, which places each glyph by the advance of the character with the glyph's
# own index, so the glyphs past the last character are placed by whatever lies beyond the advances.
# Noto Sans Arabic shapes a dotted letter into its body and its dots (جمادى الآخرة 12 characters and
# 16 glyphs, بعد أقل من دقيقة 16 and 24), and the watch's Noto Sans Bengali UI, which a Manrope run
# falls back to for Bengali, splits an o-kar or an au-kar into its two signs (যোহর 4 and 5, a Bengali
# watch's day and date সোম ২০ 6 and 7). The glyphs past the count are a run's visually last, and on
# emulator-5558 they were lost or thrown elsewhere, differently each time the face was bound, and
# drawn whole the rest of the time: right to left a run's logically first (the jeem of جمادى, the
# alef and lam of العشاء, the بـ of بعد), left to right its logically last (সোম ২০ in a curved slot
# drew সোম ২ at 3 binds of 30; যোহর was drawn whole at 30, but its last glyph is placed the same
# way, past its four advances). A run that starts (right to left) or ends (left to right) with
# combining grapheme joiners (U+034F) ends visually in as many glyphs that have no ink and no
# advance, so the glyphs past the count are those. At a left-to-right run's end they moved no pixel,
# its letter spacing too (যোহর, Mon 20 and the pass's English names and values, review round 1). A
# right-to-left mark (U+200F) in the same place changed nothing: the runtime keeps a joiner, and
# seems to drop the mark. PAD is more than the most any form holds over its characters (8, the
# countdown's under-a-minute).
PAD = chr(0x034F) * 12
# The runs of the phone's words that carry PAD, by run and direction (True: right to left, at the
# run's logical start; False: left to right, at its logical end), which tools/watchface/moon-fit.py
# reads: a run here may hold as many glyphs past its characters as PAD has joiners, a run not here
# none, in every form the phone writes. Every Arabic-script run of words (the prayer's name, the
# countdown and the Hijri month) and the left-to-right prayer's name (যোহর). The prayer's time, the
# Hijri day and the left-to-right countdown and Hijri date hold none in any form today; a phrase of
# plan 2's that did would fail moon-fit until its run is added here. The wearer's values on the
# curve, the watch's own text in any language, always end with PAD (moon_slot()).
PADDED = {("label", True), ("label", False), ("countdown", True), ("hijri_month", True)}


def padded(run, rtl, template="%s"):
    """template with PAD where PADDED puts it for run in that direction: at its logical start right to
    left, at its logical end left to right, or not at all."""
    if (run, rtl) not in PADDED:
        return template
    return PAD + template if rtl else template + PAD

# Option (d) (Mohamed's choice of 23 October, Ruling 36; the reference page is
# docs/superpowers/specs/2026-10-19-watch-companion/moon-slot-choice.html, faces.js's moonFace with
# its lower half redrawn). With either of the wearer's slots filled, the moon window rises 5 units
# (11.25 px) and the Hijri date curves under it on a circle of 58.8 units (132.3 px) where it was 71,
# its path 60 degrees either side of six where it was 52; the two slots curve along the bottom
# (tools/face-centre.py). With both empty the Moon draws as it did. The two forms are switched by the
# slots' type, read across slots (COMPS), each in a Group whose alpha Transform shows one.
COMPS = face_centre.COMPS
LIFT_D = 5 * K
R_HIJRI_D = 58.8 * K
HIJRI_ARC_D = (240, 120)


def ccw_spacing(r, size):
    """The letterSpacing (em) of a Latin text read anticlockwise along the bottom on baseline r: the
    runtime spaces its letters along the circle inside the baseline, so it comes out wider, and
    HIJRI_SPACING was fitted on the emulator at the Hijri date's radius; at another radius the
    spacing grows with the circle's ratio to the baseline as track()'s model has it."""
    q0 = (R_HIJRI - LIFT * 11 * K) / R_HIJRI - 1
    return round(HIJRI_SPACING * ((r - LIFT * size) / r - 1) / q0, 4)


def track(r, size, spacing, advance):
    """The letterSpacing (em) that gives a text read clockwise over the top faces.js's width along
    its baseline r: the runtime spaces the letters along the circle it is given, LIFT of the size
    further out, so they come closer by that ratio, and the spacing gives back the difference, as
    advance em a letter. Not for Arabic, whose letters join (its spacing stays 0, and its curves are
    some 7% narrower or wider than faces.js's); a text read anticlockwise, whose circle is inside its
    baseline, comes out wider and is given HIJRI_SPACING, a negative spacing, which a TextCircular
    draws."""
    q = (r + LIFT * size) / r
    return round(spacing * q + (q - 1) * advance, 4)


def f(n):
    """A number as the face writes it: up to four decimals, no trailing zeros."""
    s = ("%.4f" % n).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def signed(n):
    """A term added to an expression: '+ 5.61' or '- 5.61'."""
    return "- %s" % f(-n) if n < 0 else "+ %s" % f(n)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def attr(name, value):
    """An attribute whose value may hold quotes: single-quoted, as the face writes expressions."""
    return "%s='%s'" % (name, esc(value)) if '"' in value else '%s="%s"' % (name, esc(value))


def pol(r, deg, cx=C, cy=C):
    a = math.radians(deg - 90)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def deg_of(px, r):
    return px / r * 180 / math.pi


def variant(alpha, ind):
    return '%s<Variant mode="AMBIENT" target="alpha" value="%d" />\n' % (ind, alpha)


def partdraw(body, ind, alpha=None, aod=None):
    a = '' if alpha is None else ' alpha="%d"' % alpha
    out = '%s<PartDraw x="0" y="0" width="450" height="450"%s>\n%s' % (ind, a, body)
    if aod is not None:
        out += variant(aod, ind + "    ")
    return out + "%s</PartDraw>\n" % ind


def line(x0, y0, x1, y1, w, colour, ind, cap="BUTT"):
    return ('%s<Line startX="%s" startY="%s" endX="%s" endY="%s">\n'
            '%s    <Stroke thickness="%s" color="%s" cap="%s" />\n%s</Line>\n'
            % (ind, f(x0), f(y0), f(x1), f(y1), ind, f(w), colour, cap, ind))


def radial(r0, r1, deg, w, colour, ind, cap="BUTT"):
    (x0, y0), (x1, y1) = pol(r0, deg), pol(r1, deg)
    return line(x0, y0, x1, y1, w, colour, ind, cap)


def ellipse(cx, cy, r, ind, fill=None, stroke=None):
    out = '%s<Ellipse x="%s" y="%s" width="%s" height="%s">\n' % (ind, f(cx - r), f(cy - r), f(2 * r), f(2 * r))
    if fill:
        out += '%s    <Fill color="%s" />\n' % (ind, fill)
    if stroke:
        out += '%s    <Stroke thickness="%s" color="%s" />\n' % (ind, f(stroke[0]), stroke[1])
    return out + "%s</Ellipse>\n" % ind


def arc(r, w, colour, cap, start, end, ind):
    return ('%s<Arc centerX="225" centerY="225" width="%s" height="%s" startAngle="0" endAngle="0" direction="CLOCKWISE">\n'
            '%s    <Stroke thickness="%s" color="%s" cap="%s" />\n'
            '%s    <Transform target="startAngle" %s />\n'
            '%s    <Transform target="endAngle" %s />\n%s</Arc>\n'
            % (ind, f(2 * r), f(2 * r), ind, f(w), colour, cap, ind, attr("value", start), ind, attr("value", end), ind))


def group(name, body, ind, show=None, angle=None, alpha=None):
    a = '' if alpha is None else ' alpha="%d"' % alpha
    out = '%s<Group name="%s" x="0" y="0" width="450" height="450"%s>\n%s' % (ind, name, a, body)
    if angle is not None:
        out += '%s    <Transform target="angle" %s />\n' % (ind, attr("value", angle))
    if show is not None:
        out += '%s    <Transform target="alpha" %s />\n' % (ind, attr("value", "(%s) ? 255 : 0" % show))
    return out + "%s</Group>\n" % ind


def condition(name, expr, body, ind):
    return ("%s<Condition>\n%s    <Expressions>\n%s        <Expression name=\"%s\"><![CDATA[%s]]></Expression>\n"
            "%s    </Expressions>\n%s    <Compare expression=\"%s\">\n%s%s    </Compare>\n%s</Condition>\n"
            % (ind, ind, ind, name, expr, ind, ind, name, body, ind, ind))


def moon_only(name, body, ind):
    """body, written at ind, inside a Condition on the Moon style (which reads the configuration
    alone), indented to sit in its Compare."""
    inner = "".join(("        " + l if l.strip() else l) for l in body.splitlines(True))
    return condition(name, '[CONFIGURATION.style] == "moon"', inner, ind)


def languages(name, ltr, rtl, ind, rtl_arab=None):
    """The left-to-right and right-to-left forms of a piece, each in a Group that RTL's expression
    shows. With rtl_arab, the right-to-left form is two, by the phone's digits: rtl for Western
    ones and rtl_arab for Arabic-Indic ones (ARAB_NUMS)."""
    i1 = ind + "    "
    out = group(name + "_ltr", ltr(i1), ind, show="!%s" % RTL)
    if rtl_arab is None:
        return out + group(name + "_rtl", rtl(i1), ind, show=RTL)
    return (out + group(name + "_rtl", rtl(i1), ind, show="%s && !%s" % (RTL, ARAB_NUMS))
            + group(name + "_rtl_arab", rtl_arab(i1), ind, show="%s && %s" % (RTL, ARAB_NUMS)))


def font(family, size, colour, template, ind, spacing=None, words="%s"):
    """One Font of a text whose words are the expression template, written into words where its %s
    is (padded(): with PAD on a curve)."""
    ls = '' if spacing is None else ' letterSpacing="%s"' % f(spacing)
    return ('%s<Font family="%s" size="%s" color="%s"%s>\n'
            '%s    <Template><![CDATA[%s]]><Parameter expression="%s" /></Template>\n%s</Font>\n'
            % (ind, family, f(size), colour, ls, ind, words, template, ind))


def circular(origin, r, size, start, end, align, fnt, ind, aod, direction="CLOCKWISE", start_t=None, end_t=None,
             ellipsis=False):
    """A PartText the size of the face, its text along a circle about the face's centre, placed in
    the coordinates of the slot whose corner is origin, with its baseline r out. The runtime lays a
    TextCircular's line box with its middle on the circle (as a PartText's on its box's middle), so
    the circle is LIFT of the size further out than the baseline for a text read clockwise over the
    top, whose letters stand outwards, and further in for one read anticlockwise along the bottom,
    whose letters stand inwards."""
    ox, oy = origin
    r = r + LIFT * size if direction == "CLOCKWISE" else r - LIFT * size
    i1, i2 = ind + "    ", ind + "        "
    out = '%s<PartText x="%d" y="%d" width="450" height="450">\n' % (ind, -ox, -oy)
    out += variant(aod, i1) if aod is not None else ""
    out += ('%s<TextCircular centerX="225" centerY="225" width="%s" height="%s" startAngle="%s" endAngle="%s" '
            'direction="%s" align="%s"%s>\n' % (i1, f(2 * r), f(2 * r), f(start), f(end), direction, align,
                                               ' ellipsis="TRUE"' if ellipsis else ''))
    if start_t:
        out += '%s<Transform target="startAngle" %s />\n' % (i2, attr("value", start_t))
    if end_t:
        out += '%s<Transform target="endAngle" %s />\n' % (i2, attr("value", end_t))
    out += fnt(i2)
    return out + "%s</TextCircular>\n%s</PartText>\n" % (i1, ind)


def curved_runs(circle, runs, ind, aod, turn=0.0, start=-90, end=90, direction="CLOCKWISE"):
    """A PartText the size of the face, one TextCircular of Font runs (tools/face-centre.py's run()
    and gap()) on a circle of radius circle about the face's centre, from start to end (read
    clockwise over the top unless direction says otherwise), and centred by the runtime as a whole
    between them, or turn degrees clockwise of that."""
    i1, i2 = ind + "    ", ind + "        "
    out = '%s<PartText x="0" y="0" width="450" height="450">\n' % ind
    out += variant(aod, i1) if aod is not None else ""
    out += ('%s<TextCircular centerX="225" centerY="225" width="%s" height="%s" startAngle="%s" endAngle="%s" '
            'direction="%s" align="CENTER">\n' % (i1, f(2 * circle), f(2 * circle), f(start + turn), f(end + turn), direction))
    out += "".join("%s%s\n" % (i2, r[2]) for r in runs)
    return out + "%s</TextCircular>\n%s</PartText>\n" % (i1, ind)


# ---- The dial: the finish, the ticks, the indices and the mark (the scene's)

def dial(ind):
    i1 = ind + "    "
    out = "%s<!-- the sunray finish, awake only: a dark gradient 88 units out, lit from the upper left, and 120\n" \
          "%s     faint rays (one PartDraw at 3.5%%, so where they cross at the centre they are no brighter) -->\n" % (ind, ind)
    grad = ('%s<Ellipse x="27" y="27" width="396" height="396">\n'
            '%s    <Fill color="#FFFFFFFF">\n'
            '%s        <RadialGradient centerX="%s" centerY="%s" radius="%s" colors="#FF161A18 #FF070908 #FF000000" positions="0 0.6 1" />\n'
            '%s    </Fill>\n%s</Ellipse>\n'
            % (i1, i1, i1, f(C + (-88 + 0.38 * 176) * K), f(C + (-88 + 0.30 * 176) * K), f(0.8 * 176 * K), i1, i1))
    out += partdraw(grad, ind, aod=0)
    rays = "".join(radial(-88 * K, 88 * K, i * 3, 0.7 * K, "#FFFFFFFF", i1) for i in range(60))
    out += partdraw(rays, ind, alpha=9, aod=0)
    out += "%s<!-- the minute ticks, slate, awake only -->\n" % ind
    ticks = "".join(radial(89.4 * K, 91.8 * K, i * 6, 0.8 * K, "#FF" + SLATE, i1) for i in range(60) if i % 5)
    out += partdraw(ticks, ind, aod=0)
    out += "%s<!-- the hour indices in ink (twelve is the mark's); always-on, only three, six and nine, thinner, in slate -->\n" % ind
    hours = "".join(radial(80.5 * K, 91.5 * K, i * 30, 2.4 * K, "#FF" + INK, i1) for i in range(1, 12))
    out += partdraw(hours, ind, aod=0)
    quarters = "".join(radial(80.5 * K, 91.5 * K, i * 30, 1.6 * K, "#FF" + SLATE, i1) for i in (3, 6, 9))
    out += partdraw(quarters, ind, alpha=0, aod=255)
    out += ("%s<!-- the Taqwa arch at twelve, where a watchmaker puts the maker's mark (tools/face-art.py): mist with\n"
            "%s     its lamp in the accent awake, a thinner slate arch always-on -->\n" % (ind, ind))
    out += ('%s<PartImage x="210" y="16" width="30" height="38" tintColor="#FF%s">\n%s%s    <Image resource="moon_mark" />\n%s</PartImage>\n'
            % (ind, MIST, variant(0, i1), ind, ind))
    out += ('%s<PartImage x="210" y="16" width="30" height="38" tintColor="#FF%s" alpha="0">\n%s%s    <Image resource="moon_mark_aod" />\n%s</PartImage>\n'
            % (ind, SLATE, variant(255, i1), ind, ind))
    k = 13 / 564
    out += partdraw(ellipse(C, (9 + 130 * k) * K, 46 * k * K, i1, fill=ACCENT), ind, aod=0)
    return moon_only("moonDial", out, ind)


# ---- Slot 0: the chapter arc, its pointer, and the prayer over the top

def slot0(ind):
    i1 = ind + "    "
    r = 96.6 * K
    out = ("%s<!-- the chapter ring: faint from the period's start up to the hour hand, then the accent from the\n"
           "%s     hand to the next prayer, at most a turn; thinner always-on (second PartDraws) -->\n" % (ind, ind))
    out += partdraw(arc(r, 1.4 * K, ACCENT, "BUTT", FAINT_START, HAND, i1), ind, alpha=77, aod=0)
    out += partdraw(arc(r, 1 * K, ACCENT, "BUTT", FAINT_START, HAND, i1), ind, alpha=0, aod=A_22)
    end_left = "%s + %s" % (NOW_ANGLE, LEFT)
    out += partdraw(arc(r, 2.8 * K, ACCENT, "ROUND", HAND, end_left, i1), ind, aod=0)
    out += partdraw(arc(r, 1.6 * K, ACCENT, "ROUND", HAND, end_left, i1), ind, alpha=0, aod=A_60)
    out += "%s<!-- the pointer at the arc's end, the next prayer (tools/face-art.py), in the accent; 60%% always-on -->\n" % ind
    img = ('%s<PartImage x="214" y="1" width="22" height="17" tintColor="%s">\n%s%s    <Image resource="moon_pointer" />\n%s</PartImage>\n'
           % (i1, ACCENT, variant(A_60, i1 + "    "), i1, i1))
    out += group("moon_pointer", img, ind, angle=end_left)

    out += ("%s<!-- the prayer and its time curved over the top, 64.5 units out: the name in the accent, then its\n"
            "%s     time in ink; always-on the name at 70%% and the time at mist's level -->\n" % (ind, ind))

    def prayer(rtl_, arab=False):
        title, text = "[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"
        if rtl_:
            label_family, label_size, label_ls = "noto_sans_arabic_subset_v2_bold", 13 * K, None
        else:
            label_family, label_size = "manrope_extrabold", 11.5 * K
            label_ls = track(R_PRAYER, label_size, 1.4 / 11.5, TRACK_LABEL)
        time_family = "noto_sans_arabic_subset_v2_bold" if arab else "manrope_extrabold"
        time_ls = track(R_PRAYER, 13 * K, 0, TRACK_TIME)

        def runs(label_colour, time_colour):
            label = face_centre.run(label_family, label_size, label_colour, template=padded("label", rtl_), params=[title],
                                    spacing=label_ls)
            time = face_centre.run(time_family, 13 * K, time_colour, template=padded("time", rtl_), params=[text], spacing=time_ls)
            g = face_centre.gap(PRAYER_GAP[(rtl_, arab)])
            return [time, g, label] if rtl_ else [label, g, time]

        def draw(i):
            out_ = ""
            # each copy's circle is where its own run's baseline lies on R_PRAYER
            for visible, colours, aod in (("label", (ACCENT, CLEAR), A_70), ("time", (CLEAR, "#FF" + INK), INK_TO_MIST)):
                family, size = (label_family, label_size) if visible == "label" else (time_family, 13 * K)
                lift = RUN_LIFT[(rtl_, visible + ("_arab" if arab and visible == "time" else ""))] * size
                out_ += curved_runs(R_PRAYER + lift, runs(*colours), i, aod, turn=PRAYER_TURN[rtl_])
            return out_
        return draw

    out += languages("moon_prayer", prayer(False), prayer(True), ind, rtl_arab=prayer(True, arab=True))
    return moon_only("isMoon", out, ind)


# ---- The countdown, curved under the prayer, from slot 6 (drawn by the scene)

def slot6(ind):
    out = "%s<!-- the Moon's countdown, curved over the top 52.5 units out, in mist; 85%% always-on -->\n" % ind
    ltr = lambda i: circular(SLOT6, R_COUNT, 11 * K, COUNT_ARC[0], COUNT_ARC[1], "CENTER",
                             lambda j: font("manrope_semibold", 11 * K, "#FF" + MIST, "[COMPLICATION.6.TEXT]", j,
                                            spacing=track(R_COUNT, 11 * K, 0, TRACK_COUNT), words=padded("countdown", False)), i, A_85)
    rtl = lambda i: circular(SLOT6, R_COUNT, 12 * K, COUNT_ARC[0], COUNT_ARC[1], "CENTER",
                             lambda j: font("noto_sans_arabic_subset_v2_semibold", 12 * K, "#FF" + MIST, "[COMPLICATION.6.TEXT]", j,
                                            words=padded("countdown", True)), i, A_85)
    out += languages("moon_countdown", ltr, rtl, ind)
    return moon_only("isMoon", out, ind)


# ---- Slot 2: the Hijri date, curved under the moon

def slot2(ind):
    out = ("%s<!-- the Moon's Hijri date, curved under the moon and read left to right along the bottom, in mist;\n"
           "%s     70%% always-on: 71 units out with both of the wearer's slots empty, and with either filled 58.8\n"
           "%s     units out, under the risen moon and clear of the slots (option (d)); the slots' type switches -->\n"
           % (ind, ind, ind))
    # Right to left the day and the month are two runs, the month and then the day in the order they
    # are seen, with a gap between, each run's glyphs within its own count (PAD); the day is the
    # title's figures before its first space.
    title = "[COMPLICATION.TITLE]"
    one = 'subText(%s, 1, 2) == " "' % title
    day = "(%s) ? subText(%s, 0, 1) : subText(%s, 0, 2)" % (one, title, title)
    month = "(%s) ? subText(%s, 2, textLength(%s)) : subText(%s, 3, textLength(%s))" % (one, title, title, title, title)

    def curve(r, arc_):
        ltr = lambda i: circular(SLOT2, r, 11 * K, arc_[0], arc_[1], "CENTER",
                                 lambda j: font("manrope_semibold", 11 * K, "#FF" + MIST, title, j,
                                                spacing=ccw_spacing(r, 11 * K), words=padded("hijri", False)), i, A_70,
                                 direction="COUNTER_CLOCKWISE")

        def rtl(i):
            size = 12 * K
            runs = [face_centre.run("noto_sans_arabic_subset_v2_semibold", size, "#FF" + MIST, template=padded("hijri_month", True), params=[month]),
                    face_centre.gap(HIJRI_GAP_RTL),
                    face_centre.run("noto_sans_arabic_subset_v2_semibold", size, "#FF" + MIST, template=padded("hijri_day", True), params=[day])]
            return curved_runs(r - HIJRI_LIFT_RTL * size, runs, i, A_70, start=arc_[0], end=arc_[1],
                               direction="COUNTER_CLOCKWISE")
        return ltr, rtl

    i1 = ind + "    "
    full = languages("moon_hijri", *curve(R_HIJRI, HIJRI_ARC), ind=i1)
    compact = languages("moon_hijri_d", *curve(R_HIJRI_D, HIJRI_ARC_D), ind=i1)
    out += group("moon_hijri_full", full, ind, show="!%s" % COMPS)
    out += group("moon_hijri_compact", compact, ind, show=COMPS)
    return moon_only("isMoon", out, ind)


# ---- The date window at three and the moon window at six, from slot 3 (drawn by the scene)

def text_box(x, baseline, size, align, fnt, ind, aod=None, width=120, noto=False, scale=None, scale_noto=False):
    """A PartText whose line sits on baseline, its start (or end, or middle) at x, in slot 3's
    coordinates: for Manrope a baseline is the box's middle plus 0.38 of the size (the face header),
    for Noto Sans Arabic 0.318 (its ascent 1.374 and descent 0.738), in a box tall enough for it.
    With scale (an expression, START only), the line is scaled by it about its start and the
    baseline the runtime gives its font (scale_noto: Noto Sans Arabic's, in a box placed by
    Manrope's rule, as the Arabic weekday's has always been), and ends in an ellipsis past the box."""
    ox, oy = SLOT3
    h = int(round(size * (2.2 if noto else 1.5)))
    top = int(round(baseline - oy - h / 2 - (0.318 if noto else 0.38) * size))
    left = {"START": x - ox, "END": x - ox - width, "CENTER": x - ox - width / 2}[align]
    i1 = ind + "    "
    if scale is None:
        out = '%s<PartText x="%d" y="%d" width="%d" height="%d">\n' % (ind, int(round(left)), top, width, h)
    else:
        below = (0.318 if (noto or scale_noto) else 0.38) * size
        out = ('%s<PartText x="%d" y="%d" width="%d" height="%d" pivotX="0" pivotY="%s">\n'
               % (ind, int(round(left)), top, width, h, f(round((h / 2 + below) / h, 4))))
        out += '%s<Transform target="scaleX" %s />\n' % (i1, attr("value", scale))
        out += '%s<Transform target="scaleY" %s />\n' % (i1, attr("value", scale))
    out += variant(aod, i1) if aod is not None else ""
    out += '%s<Text align="%s"%s>\n%s%s</Text>\n%s</PartText>\n' % (
        i1, align, '' if scale is None else ' ellipsis="TRUE"', fnt(i1 + "    "), i1, ind)
    return out


# ---- The date window: the weekday scaled to the room its day leaves it
#
# faces.js draws one date, SUN 18 (الأحد 18): the weekday from the window's inner end, the day to its
# outer end, and nothing between. The phone's other weekdays are longer (MON, WED, and in Arabic
# every one but الأحد), and a day of two wide figures is longer than 18, so at faces.js's sizes the
# two ran into each other (Task 10's review: الخميس under the day, five others touching it). So the
# weekday keeps faces.js's size while it fits, and is scaled down about its start and its baseline
# when it would come nearer the day than WD_GAP: its room runs from its start to the ink of the
# day's first figure, less the gap, where the day's box ends at the window's outer end. A face
# cannot measure a text, so the widths are estimated from the characters, in em of their sizes (as
# the wearer's slots' values are, tools/face-centre.py):
#
# - a Latin weekday by its first five letters, each class at Manrope ExtraBold's widest advance in it
#   (within 0.01 em of each letter), and the window's tracking a letter; a letter not in a class (an
#   accent the weekdays do not have, a lower case) at the rest, wider than any capital but M and W;
#   Bengali, which the watch draws in its own Noto Sans Bengali, with the marks its weekdays have at
#   their width in a word, and its letters at the rest;
# - an Arabic-script weekday by its name: the seven the phone writes in Arabic and in Urdu (CLDR's,
#   which the app's calendar formats), at Noto Sans Arabic Bold's own advance; another text by its
#   length, at more a letter than any of theirs;
# - the day by its figures' advances, Manrope ExtraBold's or Noto Sans Arabic Bold's (tabular, for
#   the phone's Arabic-Indic figures), less its first figure's left side bearing, which in Noto runs
#   from nothing (٨) to a sixth of an em (١).
#
# The day's own figures are taken, so a weekday is no smaller than its day needs (الخميس 8 is larger
# than الخميس 28): the window shows one date a day. The weekday's own last side bearing (a twentieth
# of an em in Arabic, up to a tenth in Latin) adds to the gap. At SUN 18, faces.js's drawing, the
# weekday is 0.985 of its size, 0.7 px narrower. tools/watchface/window-fit.py checks the estimates
# against the fonts, for each weekday the phone writes in the seven languages beside each day of the
# month.
WINDOW = {False: (126, 44), True: (120, 52)}   # faces.js: the window's x and width, units, by direction
WD_SIZE = {False: 9 * K, True: 10 * K}         # the weekday's size, px: Manrope, or Noto Sans Arabic a size up
DAY_SIZE = 11.5 * K
WD_BOX, WD_DAY_BOX = 120, 80                   # the weekday's and the day's boxes, px wide
WD_GAP, WD_MIN = 3.0, 0.5
WD_TRACK = 0.6 / 9                             # the Latin weekday's letterSpacing, em (faces.js ls 0.6 at 9)
WD_LATIN = [("I", 0.296), (".", 0.32), ("J", 0.513), ("F", 0.53), ("L", 0.541), ("E", 0.59), ("T", 0.61),
            ("YB", 0.637), ("PV", 0.653), ("KRSX", 0.672), ("ZA", 0.683), ("D", 0.71), ("UNH", 0.734),
            ("GCÇ", 0.746), ("OQ", 0.758), ("M", 0.86), ("W", 0.989), ("\u09c1\u09c3\u09cd", 0), ("\u09bf", 0.31), ("\u09cb", 0.71)]
WD_LATIN_REST, WD_LATIN_CHARS = 0.76, 5
WD_ARABIC = [("الأحد", 2.105), ("الاثنين", 2.862), ("الإثنين", 2.862), ("الثلاثاء", 2.807), ("الأربعاء", 2.941),
             ("الخميس", 3.696), ("الجمعة", 2.956), ("السبت", 3.115),
             ("اتوار", 1.838), ("پیر", 1.218), ("منگل", 2.265), ("بدھ", 1.659), ("جمعرات", 3.527),
             ("جمعہ", 2.391), ("ہفتہ", 1.841)]
WD_ARABIC_REST = 0.66                          # a letter of another text, em
DAY_MANROPE = [("1", 0.454), ("7", 0.544), ("5", 0.589), ("3", 0.591), ("2", 0.604), ("4", 0.615), ("69", 0.622),
               ("8", 0.623)]
DAY_MANROPE_REST, DAY_NOTO = 0.674, 0.572
# The first figure's left side bearing, em, each class at its least (a figure not here, none). In
# Noto they are the glyphs the face's subset draws: since its v2 files, Urdu's ۴ ۶ ۷ in Urdu's own
# forms (tools/watchface/subset-noto.py), so ۶ draws ٦'s glyph and sits in its class, and the Urdu ۷
# (0.058 em) in the class just under it (Ruling 51).
DAY_LEAD_MANROPE = [("37", 0.04), ("249", 0.05), ("5", 0.051), ("18", 0.06), ("06", 0.07)]
DAY_LEAD_NOTO = [("٧٨۸", 0.002), ("٣۳", 0.012), ("۵", 0.031), ("٦۴۶", 0.041), ("٢۲", 0.049), ("٩۹۷", 0.054),
                 ("٥", 0.061), ("٤", 0.07), ("١۱", 0.172), ("٠۰", 0.173)]


def classes_em(text, classes, rest, chars, tail=True):
    """An expression for text's advance, em: each of its first chars characters at its class's
    em (classes: (characters, em)), the others at rest (with tail; without it, the first chars
    characters only)."""
    terms = []
    for i in range(chars):
        c = "subText(%s, %d, %d)" % (text, i, i + 1)
        expr = f(rest)
        for members, em in reversed(classes):
            expr = "(%s) ? %s : (%s)" % (" || ".join('%s == "%s"' % (c, ch) for ch in members), f(em), expr)
        terms.append("(textLength(%s) > %d ? (%s) : 0)" % (text, i, expr))
    if tail:
        terms.append("clamp(textLength(%s) - %d, 0, 99) * %s" % (text, chars, f(rest)))
    return " + ".join(terms)


def weekday_em(text, rtl_):
    if not rtl_:
        return "%s + textLength(%s) * %s" % (classes_em(text, WD_LATIN, WD_LATIN_REST, WD_LATIN_CHARS), text, f(WD_TRACK))
    expr = "textLength(%s) * %s" % (text, f(WD_ARABIC_REST))
    for name, em in reversed(WD_ARABIC):
        expr = '(%s == "%s") ? %s : (%s)' % (text, name, f(em), expr)
    return expr


def day_em(text, arab):
    """The day's advance less its first figure's left side bearing, em."""
    lead = "(%s)" % classes_em(text, DAY_LEAD_NOTO if arab else DAY_LEAD_MANROPE, 0, 1, tail=False)
    if arab:
        return "textLength(%s) * %s - %s" % (text, f(DAY_NOTO), lead)
    return "%s - %s" % (classes_em(text, DAY_MANROPE, DAY_MANROPE_REST, 2), lead)


def window_edges(rtl_):
    """Where the weekday starts and the day ends, face px, as their boxes are placed (whole pixels
    in slot 3, the day's box WD_DAY_BOX wide)."""
    wx, ww = WINDOW[rtl_]
    ox = SLOT3[0]
    start = int(round((wx + 5) * K - ox)) + ox
    day_end = int(round((wx + ww - 5) * K - ox - WD_DAY_BOX)) + WD_DAY_BOX + ox
    return start, day_end


def weekday_scale(start, day_end, wd_size, rtl_, arab):
    """The weekday's scale: its room, from start to the day's start less WD_GAP, over its width."""
    title, text = "[COMPLICATION.TITLE]", "[COMPLICATION.TEXT]"
    room = "%s - %s * (%s)" % (f(day_end - start - WD_GAP), f(DAY_SIZE), day_em(text, arab))
    return "clamp((%s) / (%s * (%s) + 0.01), %s, 1)" % (room, f(wd_size), weekday_em(title, rtl_), f(WD_MIN))


def slot3(ind):
    ox, oy = SLOT3
    i1, i2, i3 = ind + "    ", ind + "        ", ind + "            "

    def window(rtl_, arab=False):
        wx, ww = WINDOW[rtl_]

        def draw(i):
            j = i + "    "
            rect = ('%s<RoundRectangle x="%s" y="%s" width="%s" height="%s" cornerRadiusX="%s" cornerRadiusY="%s">\n'
                    '%s    <Fill color="#FF%s" />\n%s    <Stroke thickness="%s" color="#FF%s" />\n%s</RoundRectangle>\n'
                    % (j, f(wx * K - ox), f(91 * K - oy), f(ww * K), f(18 * K), f(3.6 * K), f(3.6 * K), j, WELL, j, f(K), HAIR, j))
            out = partdraw(rect, i)
            wd_size = WD_SIZE[rtl_]
            if rtl_:
                wd = lambda k: font("noto_sans_arabic_subset_v2_bold", wd_size, "#FF" + MIST, "[COMPLICATION.TITLE]", k)
            else:
                wd = lambda k: font("manrope_extrabold", wd_size, "#FF" + MIST, "[COMPLICATION.TITLE]", k, spacing=WD_TRACK)
            start, day_end = window_edges(rtl_)
            out += text_box(start, 104 * K, wd_size, "START", wd, i, width=WD_BOX, scale_noto=rtl_,
                            scale=weekday_scale(start, day_end, wd_size, rtl_, arab))
            num = lambda k: font("noto_sans_arabic_subset_v2_bold" if arab else "manrope_extrabold", DAY_SIZE, "#FF" + INK, "[COMPLICATION.TEXT]", k)
            out += text_box((wx + ww - 5) * K, 104.4 * K, DAY_SIZE, "END", num, i, aod=INK_TO_MIST, width=WD_DAY_BOX, noto=arab)
            return out
        return draw

    body = "%s<!-- three o'clock: the date window, the weekday and then the day at its outer end in every language,\n" \
           "%s     so that the hour hand only ever covers the weekday -->\n" % (i2, i2)
    body += languages("moon_date", window(False), window(True), i2, rtl_arab=window(True, arab=True))
    body += "%s<!-- six o'clock: the moon window (its fill black always-on, its hairline kept: Ruling 46), a few stars\n" \
            "%s     awake, and the moon: a dark disc with the lit part over it, the source's monochromatic image tinted\n" \
            "%s     moonlight (dimmer always-on); 5 units higher with either of the wearer's slots filled (option (d)),\n" \
            "%s     the slots' type switching the two -->\n" % (i2, i2, i2, i2)

    def moon_window(lift, my, i):
        """The window lift px higher than faces.js's, its picture's box centred on my in the slot."""
        j = i + "    "
        shape = ('%s<RoundRectangle x="%s" y="%s" width="%s" height="%s" cornerRadiusX="%s" cornerRadiusY="%s">\n%s%s</RoundRectangle>\n'
                 % (j, f(77 * K - ox), f(121 * K - oy - lift), f(46 * K), f(27 * K), f(13.5 * K), f(13.5 * K), "%s", j))
        # The fill and its hairline, as faces.js draws them awake; always-on the fill is black (Ruling 46),
        # so it is a PartDraw of its own at alpha 0 there, and the hairline a second one over it, kept.
        out = partdraw(shape % ('%s    <Fill color="#FF%s" />\n' % (j, MOON_WINDOW)), i, aod=0)
        out += partdraw(shape % ('%s    <Stroke thickness="%s" color="#FF%s" />\n' % (j, f(K), HAIR)), i)
        stars = (ellipse(86 * K - ox, 128.6 * K - oy - lift, 0.6 * K, j, fill="#FF" + MIST)
                 + ellipse(115.4 * K - ox, 131.4 * K - oy - lift, 0.7 * K, j, fill="#FF" + MIST)
                 + ellipse(110.6 * K - ox, 142.2 * K - oy - lift, 0.5 * K, j, fill="#FF" + SLATE)
                 + ellipse(89.4 * K - ox, 141 * K - oy - lift, 0.5 * K, j, fill="#FF" + SLATE))
        out += partdraw(stars, i, aod=0)
        mx, mr = 52, 9.4 * K
        out += partdraw(ellipse(mx, my, mr, j, fill="#FF" + MOON_DARK), i)
        for tint, alpha, aod in ((MOON_LIT, None, 0), (MOON_LIT_AOD, 0, 255)):
            a = '' if alpha is None else ' alpha="%d"' % alpha
            out += ('%s<PartImage x="%d" y="%d" width="42" height="42" tintColor="#FF%s"%s>\n%s%s    <Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]" />\n%s</PartImage>\n'
                    % (i, mx - 21, my - 21, tint, a, variant(aod, i + "    "), i, i))
        return out

    # The picture's box is whole pixels: 42 across about (52, 99) in the slot, the face's (225, 303),
    # 0.375 px under faces.js's centre, and risen, about (52, 88), the face's (225, 292), 0.625 px under
    # (so that it sits as far under the risen window's top as it does under the window's, within a
    # quarter of a pixel); the dark disc is put about the same point, so the two agree.
    body += group("moon_window", moon_window(0, 99, i2 + "    "), i2, show="!%s" % COMPS)
    body += group("moon_window_d", moon_window(LIFT_D, 88, i2 + "    "), i2, show=COMPS)
    # The scene draws the two windows from slot 3's data, in the box they lie in (a Group at its
    # corner, in the coordinates the slot drew them in until Task 12), just before the slot is
    # declared, shown while it has its short text; the slot has no room of its own (0 by 0), so
    # that a screen reader is not given it: the dates it shows are slot 2's to say (the face header).
    for field in ("TITLE", "TEXT", "MONOCHROMATIC_IMAGE"):
        own = "[COMPLICATION.%s]" % field
        assert own in body, "the windows no longer read slot 3's %s: the rewrite below has nothing to do" % field
        body = body.replace(own, "[COMPLICATION.3.%s]" % field)
    # Every own-slot read is now slot 3's: one left behind would read nothing in the scene.
    assert not re.search(r"\[COMPLICATION\.[A-Z]", body), "the windows still read a slot of their own"
    # A Group does not clip what it holds, and the slot's box did: the windows' strokes reach a
    # fraction of a pixel past it. So the box is the windows' mask (renderMode), which keeps
    # exactly what the box kept.
    inner = moon_only("isMoon", body, i1 + "    ")
    mask = ('%s<PartDraw x="0" y="0" width="216" height="130" renderMode="MASK">\n'
            '%s    <Rectangle x="0" y="0" width="216" height="130">\n%s        <Fill color="#FFFFFFFF" />\n%s    </Rectangle>\n'
            '%s</PartDraw>\n' % ((i1,) * 5))
    scene = ('%s<Group name="moon_windows" x="%d" y="%d" width="216" height="130">\n%s'
             '%s<Group name="moon_windows_drawn" x="0" y="0" width="216" height="130" renderMode="SOURCE">\n%s%s</Group>\n'
             '%s    <Transform target="alpha" %s />\n%s</Group>\n'
             % (ind, ox, oy, mask, i1, inner, i1, ind, attr("value", '([COMPLICATION.3.TYPE] == "SHORT_TEXT") ? 255 : 0'), ind))
    slot = ('%s<ComplicationSlot slotId="3" x="0" y="0" width="450" height="450"\n'
            '%s    supportedTypes="SHORT_TEXT EMPTY" isCustomizable="FALSE" displayName="slot_dates_short">\n'
            '%s    <DefaultProviderPolicy\n'
            '%s        primaryProvider="world.taqwa.app/world.taqwa.app.wear.complication.DatesSource"\n'
            '%s        primaryProviderType="SHORT_TEXT"\n'
            '%s        defaultSystemProvider="EMPTY"\n'
            '%s        defaultSystemProviderType="EMPTY" />\n'
            '%s    <BoundingBox x="3" y="0" width="0" height="0" />\n'
            '%s    <Complication type="SHORT_TEXT" />\n'
            '%s</ComplicationSlot>\n' % ((ind,) * 10))
    return ("%s<!-- The Moon style's date window at three and moon window at six, from slot 3: a weekday, a day\n"
            "%s     number and the moon -->\n%s"
            "%s<!-- Slot 3: the dates as a weekday, a day number and the moon, which the windows above read; no room\n"
            "%s     of its own, so that a screen reader is not given it (the header) -->\n%s"
            % (ind, ind, scene, ind, ind, slot))


# ---- The hands (the scene's, over everything)

def hands(ind):
    i1, i2 = ind + "    ", ind + "        "

    def hand(kind, resource, box, alpha, aod, extra=""):
        x, y, w, h = box
        a = '' if alpha is None else ' alpha="%d"' % alpha
        return ('%s<%s resource="%s" x="%d" y="%d" width="%d" height="%d" pivotX="0.5" pivotY="%s"%s>\n%s%s%s</%s>\n'
                % (i1, kind, resource, x, y, w, h, f(round((C - y) / h, 6)), a, variant(aod, i2), extra, i1, kind))

    hour, minute, second = (213, 114, 24, 134), (216, 43, 18, 207), (220, 32, 10, 231)
    clock = '%s<AnalogClock x="0" y="0" width="450" height="450">\n' % ind
    clock += hand("HourHand", "hand_hour_back", hour, 0, 255)
    clock += hand("HourHand", "hand_hour", hour, None, A_HANDS)
    clock += hand("MinuteHand", "hand_minute_back", minute, 0, 255)
    clock += hand("MinuteHand", "hand_minute", minute, None, A_HANDS)
    clock += hand("SecondHand", "hand_second", second, None, 0, '%s<Sweep frequency="15" />\n' % i2)
    clock += "%s</AnalogClock>\n" % ind
    out = ("%s<!-- the hands (tools/face-art.py): faceted dauphine with a black keyline, dimmed always-on (Ruling 41)\n"
           "%s     and laid on a black back of their own shape, the other hand of its kind in the one AnalogClock,\n"
           "%s     declared first (it draws them in that order) and out while awake; the seconds hand in mist,\n"
           "%s     sweeping, awake only -->\n%s" % (ind, ind, ind, ind, clock))
    out += ("%s<!-- the cap: ink with the accent at its heart; always-on the ink dimmed as the hands are, on a\n"
            "%s     black disc -->\n" % (ind, ind))
    out += partdraw(ellipse(C, C, 4.4 * K, i1, fill="#FF000000"), ind, alpha=0, aod=255)
    out += partdraw(ellipse(C, C, 4.4 * K, i1, fill="#FF" + INK), ind, aod=A_HANDS)
    out += partdraw(ellipse(C, C, 1.8 * K, i1, fill=ACCENT), ind)
    return moon_only("moonHands", out, ind)


# ---- Slots 4 and 5 on the Moon: the wearer's own, curved along the bottom (option (d))
#
# moon-slot-choice.html's D: each slot's pair, its source's icon and then its value in ink, read left
# to right along the bottom on the hour indices' inner end (80 units), between the five and seven
# o'clock indices, the six o'clock index between the two: the left slot's pair ends at its anchor,
# just left of six, and the right one's starts at its anchor, just right of it. Each is drawn in its
# own slot, in the slot's own coordinates, so it lies inside the slot's box (tools/face-centre.py
# SLOT_BOX), where the editor outlines it and where a tap opens it. tools/face-centre.py writes the
# slots and calls moon_slot() for each of their types.
MOON_R = 80 * K                                  # the values' baseline, px
MOON_ANCHOR = {4: 184, 5: 175.6}                 # degrees: where each pair meets six
MOON_LIMIT = {4: 205.8, 5: 153}                  # and how far out it may run
MOON_ICON = {4: 7.5 * 0.8 * K, 5: 6.9 * 0.8 * K}  # the room each icon takes along the curve, px (faces.js's at 0.8)
MOON_GAP = 2.2 * K                               # between the icon and the value
MOON_SIZE = 9.5 * K                              # the value's size, as the ring at nine had it
MOON_ICON_BOX = 18                               # the icon's box, px (faces.js's 10 units at 0.8)
# A value scaled down to its room is scaled about its anchor, the point where its baseline meets the
# six o'clock side of its run, and a part's scale scales its circle too: a TextCircular's own circle
# follows no expression (the runtime ignores a Transform on its width or centre). So a value's curve
# is drawn on a circle MOON_CURVE times the baseline's, tangent to the baseline at the anchor, which
# a scale of 1 / MOON_CURVE turns into the baseline itself; at another scale its far end lies off the
# baseline by its length squared over twice the radii's difference: within a pixel for the values
# at their size (82 and 100 half a pixel away from the centre, 80% 0.8) and for four-figure counts
# and times (6,410, 10:58), 1.7 px towards the centre for a five-figure count (12,804 at 0.69), at
# most 3.7 for the longest any source writes (100.000 at 0.53). tools/watchface/slot-fit.py prints
# each.
MOON_CURVE = 1.15


def moon_room(slot):
    """The room a slot's value has along the curve, px: its run less its icon and the gap."""
    return math.radians(abs(MOON_LIMIT[slot] - MOON_ANCHOR[slot])) * MOON_R - MOON_ICON[slot] - MOON_GAP


def moon_deg(px):
    return math.degrees(px / MOON_R)


def moon_slot(slot, kind, origin, ind):
    """The Moon's drawing of one of the wearer's slots for data of kind (one of face-centre.py's
    USER_TYPES), in the coordinates of the slot whose corner is origin: the icon, and for a type with
    a text the value. A value keeps faces.js's size while it fits its room, and is scaled down to it
    as the other styles' are (face-centre.py value_scale(): a face cannot measure a text, so its
    width is estimated from its characters), about its anchor on the baseline (MOON_CURVE). The
    left slot's icon stands as far out from six as its value's estimate reaches."""
    ox, oy = origin
    i1, i2, i3 = ind + "    ", ind + "        ", ind + "            "
    text = "[COMPLICATION.TEXT]"
    an, lim = MOON_ANCHOR[slot], MOON_LIMIT[slot]
    room = moon_room(slot)
    has_text = kind in ("RANGED_VALUE", "GOAL_PROGRESS", "SHORT_TEXT")
    s = face_centre.value_scale(text, MOON_SIZE, room)
    drawn = "(%s) * %s * (%s)" % (s, f(MOON_SIZE), face_centre.value_em(text))   # the value's estimated length, px
    out = ""
    # the icon, upright at six and turned to its place: its box's foot 0.8 units under the baseline
    if slot == 4:
        base = an + moon_deg(MOON_ICON[slot] / 2) + (moon_deg(MOON_GAP) if has_text else 0)
        angle = ("%s + (%s) * %s" % (f(base - 180), drawn, f(moon_deg(1)))) if has_text else None
        fixed = None if has_text else base - 180
    else:
        angle, fixed = None, an - moon_deg(MOON_ICON[slot] / 2) - 180
    b = MOON_ICON_BOX
    ix, iy = int(round(C - b / 2)), int(round(C + MOON_R + 0.8 * K - b))
    if kind == "SMALL_IMAGE":
        img = ('%s<PartImage x="%d" y="%d" width="%d" height="%d">\n%s%s    <Image resource="[COMPLICATION.SMALL_IMAGE]" />\n%s</PartImage>\n'
               % (i1, ix, iy, b, b, variant(INK_TO_MIST, i1 + "    "), i1, i1))
    else:
        img = ('%s<PartImage x="%d" y="%d" width="%d" height="%d" tintColor="#FF%s">\n%s%s    <Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]" />\n%s</PartImage>\n'
               % (i1, ix, iy, b, b, MIST, variant(face_centre.MIST_TO_SLATE, i1 + "    "), i1, i1))
    out += '%s<Group name="moon_icon_%d" x="%d" y="%d" width="450" height="450"%s>\n%s' % (
        ind, slot, -ox, -oy, '' if fixed is None else ' angle="%s"' % f(fixed), img)
    if angle is not None:
        out += '%s    <Transform target="angle" %s />\n' % (ind, attr("value", angle))
    out += "%s</Group>\n" % ind
    if not has_text:
        return out
    # the value, END at the anchor on the left, START after the icon and the gap on the right: a
    # TextCircular whose baseline passes through that point, scaled about it, its text ending with
    # PAD (the watch's own words, which can hold more glyphs than characters in many scripts)
    start = an if slot == 4 else an - moon_deg(MOON_ICON[slot] + MOON_GAP)
    size, lift = MOON_SIZE, LIFT * MOON_SIZE
    px, py = pol(MOON_R, start)
    rb = MOON_CURVE * MOON_R
    ux, uy = (px - C) / MOON_R, (py - C) / MOON_R
    cx, cy = px - rb * ux + ox, py - rb * uy + oy
    # The runtime cuts a curved text where its path ends, and lays it out before it is scaled: the
    # path runs on from the anchor as far as the longest value scaled to MIN_SCALE reaches, unscaled,
    # past the slot's limit (what lies past it after the scale is the room the value is scaled to).
    reach = math.degrees((room / face_centre.MIN_SCALE + 10) / rb)
    a0, a1, align = (an + reach, an, "END") if slot == 4 else (start, start - reach, "START")
    out += ('%s<PartText x="%d" y="%d" width="450" height="450" pivotX="%s" pivotY="%s">\n'
            % (ind, -ox, -oy, f(round(px / 450, 6)), f(round(py / 450, 6))))
    out += '%s<Transform target="scaleX" %s />\n' % (i1, attr("value", s))
    out += '%s<Transform target="scaleY" %s />\n' % (i1, attr("value", s))
    out += variant(INK_TO_MIST, i1)
    out += ('%s<TextCircular centerX="%s" centerY="%s" width="%s" height="%s" startAngle="%s" endAngle="%s" '
            'direction="COUNTER_CLOCKWISE" align="%s">\n' % (i1, f(cx - ox), f(cy - oy), f(2 * (rb - lift)), f(2 * (rb - lift)),
                                                              f(a0), f(a1), align))
    out += font("manrope_extrabold", size, "#FF" + INK, text, i2, spacing=ccw_spacing(rb, size), words="%s" + PAD)
    out += "%s</TextCircular>\n%s</PartText>\n" % (i1, ind)
    return out


def blocks():
    return {
        "dial": dial,
        "slot0": slot0,
        "slot2": slot2,
        "slot3": slot3,
        "slot6": slot6,
        "hands": hands,
    }


def begin(name):
    return "<!-- face-moon.py: begin %s (written by tools/face-moon.py; do not edit by hand) -->" % name


def end(name):
    return "<!-- face-moon.py: end %s -->" % name


def write(xml):
    for name, make in blocks().items():
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
            sys.exit("tools/face-moon.py: the Moon's blocks in watchface.xml are out of date; run tools/face-moon.py")
        print("tools/face-moon.py: up to date")
        return
    with open(FACE_XML, "w", encoding="utf-8") as fh:
        fh.write(new)
    print("tools/face-moon.py: wrote the Moon's blocks")


if __name__ == "__main__":
    main()
