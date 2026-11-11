"""The always-on lit share at the face's worst moments (Task 13's fix round 1, after review round 1;
the emulator's measure corrected in fix round 2, after review round 2; review round 3's frames with
three-figure step counts, and the second lever, added in fix round 3; the face as rebuilt under
Ruling 46, the moon window's fill black always-on, in the closing fix).
litshare.py measured the pass's moments, 14:58 and 10:10, when the Moon's chapter arc is short. The
Moon lights the most just after Fajr (04:33) and just after Isha (20:24), when its arc is about
eight hours long, with both slots filled with wide values and a long Hijri month. Arch, Ring and
Sun were measured at their own worst moments too, for the Lead's 15% question (Arch, Ring and Sun
are not Task 13's, and nothing of them changed).

    python3 -I litworst.py <emulator frames dir> [rebuilt=<dir>] [sweep=5]

The measure is the share of the round screen's pixels that are lit, a pixel being lit when any
channel of the face's colour is over 8 (tools/watchface/lit.py).
1. The drawing: ref.py aod sec=0 (the Moon with Ruling 41's hands, A_HANDS from face-moon.py, and
   since the closing fix Ruling 46's moon window, its fill black, as face=moon-d; hands=outline is
   the approved drawing's hands), each form at each moment, drawn into a scratch folder; then Arch,
   Ring and Sun at their own worst moments.
2. With sweep=N, the drawing over the whole mock day every N minutes, for each form: the highest
   minute, and how many of the day's moments reach 14.5% and 15%. Drawn in parallel, then drawn again
   one at a time wherever a share stands alone (more than LONE above both its neighbours or below
   both) and at the day's highest, before the row is written (sweep(), Ruling 49).
3. The emulator's frames in the folder (worst/, taken with tools/watchface/shoot.sh, and
   tools/watchface/hijri.sh for a Hijri form, with the clock set to the moment before the face is
   bound again). The emulator dithers an always-on frame as a strict checkerboard and draws it at
   0.85 of the face's colours, so each frame is given: raw, as the emulator shows it (over 8 at its
   own levels); as the face draws it, lit.py undither (twice the lit parity, lit at 8 or more after
   the 0.85), which is the measure; fix round 1's figure (each black pixel filled from its
   neighbours, over 8), which widened every edge by a pixel and lost the moon window's fill, a face
   9 the emulator draws 8; and the frame's mean light, undithered, at the emulator's levels. These
   frames are the face before Ruling 46. With rebuilt=<dir> (task-13/worst-r46/), the same Moon
   cases taken again on the face as rebuilt, the moon window's fill black always-on, are measured
   the same way, beside the frame before.
4. The measure checked: section 1's and 3's worst drawings drawn as the emulator draws them (0.85
   of their colours, rounded, then one parity black), and measured as section 3 measures a frame.
5. The two levers fix round 3 measured for the Lead or Mohamed: the moon window's fill (#070908,
   lit always-on before Ruling 46: 9 is over 8) black always-on, which Ruling 46 chose and the
   closing fix built, and the date window's fill too (WELL, #0B0D0C, lit the same way), which the
   ruling left as it is. The Moon's worst drawings before Ruling 46 (ref.py window=filled), as
   built (ref.py's default) and as built with WELL's own pixels black; and the frames before it, as
   measured and with the fill's own pixels black (what the ruling expected), beside the rebuilt
   face's frames, as measured and with WELL's own pixels black (the drawing's 7, 9, 8 and 11, 13,
   12; the emulator's 6, 8, 7 and 9, 11, 10). The strokes stay.
The emulator's battery was 100. Its step count is its own and cannot be set: it read 1,620 to 2,700
in fix round 1's frames (four figures, no separator), so the drawing carries the five-figure 12,345.
Review round 3's three frames (the face of 97779b42, the same face) read 890, 960 and 1,030: an
ordinary count is over 15% too. The rebuilt face's frames (the closing fix) carry the emulator's own
count again, battery 100.
"""
import concurrent.futures as cf
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *[os.pardir] * 5))
REF = os.path.join(REPO, "tools", "watchface", "ref.py")
sys.path.insert(0, os.path.join(REPO, "tools", "watchface"))
import lit  # noqa: E402  (tools/watchface/lit.py: the measure)

AR_LONG = "29 جمادى الآخرة"         # the longest Arabic month by arc (moon-fit.py)
AR_MOST = "28 جمادى الأولى"         # the Arabic form that lights the most (8 and the long alif)
EN_LONG = "29 Jumada al-Akhirah"    # the longest English month (moon-fit.py)
SA_LONG = "٢٩ جمادى الآخرة"
WIDE = ["comps", "v4=100", "v5=12,345"]
PASS = ["comps", "v4=82", "v5=0"]   # the pass's values (pass.sh's battery; the steps of faces.js's v5=0)

# label, ref.py options (besides face=moon-d aod sec=0)
FORMS = [
    ("ar 12h, slots, the pass's values", ["lang=ar", "clock=12"] + PASS),
    ("ar 12h, slots 100 and 12,345", ["lang=ar", "clock=12"] + WIDE),
    ("ar 12h, slots wide, " + AR_LONG, ["lang=ar", "clock=12", "hijri=" + AR_LONG] + WIDE),
    ("ar 12h, slots wide, " + AR_MOST, ["lang=ar", "clock=12", "hijri=" + AR_MOST] + WIDE),
    ("  the same, outline hands", ["lang=ar", "clock=12", "hijri=" + AR_MOST, "hands=outline"] + WIDE),
    ("ar 12h, slots empty, " + AR_LONG, ["lang=ar", "clock=12", "hijri=" + AR_LONG]),
    ("ar-SA, slots wide, " + SA_LONG, ["lang=ar-SA", "hijri=" + SA_LONG] + WIDE),
    ("en 24h, slots, the pass's values", ["lang=en", "clock=24"] + PASS),
    ("en 24h, slots wide, " + EN_LONG, ["lang=en", "clock=24", "hijri=" + EN_LONG] + WIDE),
    ("  the same, outline hands", ["lang=en", "clock=24", "hijri=" + EN_LONG, "hands=outline"] + WIDE),
    ("en 24h, slots empty, " + EN_LONG, ["lang=en", "clock=24", "hijri=" + EN_LONG]),
    ("en 12h, slots wide, " + EN_LONG, ["lang=en", "clock=12", "hijri=" + EN_LONG] + WIDE),
]
MOMENTS = ["04:40", "20:55", "10:10", "14:58"]   # after Fajr (04:33) and after Isha (20:24); the pass's two

# Arch, Ring and Sun at their own worst moments (the sweep's highest, every 5 minutes): label, options, moment.
OTHERS = [
    ("Ring en 24h, slots empty", ["face=ring", "lang=en", "clock=24"], "18:50"),
    ("Ring en 24h, slots wide, " + EN_LONG, ["face=ring", "lang=en", "clock=24", "hijri=" + EN_LONG] + WIDE, "18:50"),
    ("Ring ar 12h, slots wide, " + AR_LONG, ["face=ring", "lang=ar", "clock=12", "hijri=" + AR_LONG] + WIDE, "10:55"),
    ("Sun en 24h, slots empty", ["face=sun", "lang=en", "clock=24"], "18:00"),
    ("Sun en 24h, slots wide, " + EN_LONG, ["face=sun", "lang=en", "clock=24", "hijri=" + EN_LONG] + WIDE, "18:00"),
    ("Arch en 24h, slots empty", ["face=arch", "lang=en", "clock=24"], "18:55"),
    ("Arch en 24h, slots wide, " + EN_LONG, ["face=arch", "lang=en", "clock=24", "hijri=" + EN_LONG] + WIDE, "18:55"),
]

# The emulator frames: file, what it shows.
FRAMES = [
    ("moon-ar-ly-12h-slots-0440-aod.png", "ar-ly12, slots (100, its steps), 6 جمادى الأولى"),
    ("moon-ar-ly-12h-slots-2055-aod.png", "ar-ly12, slots (100, its steps), 6 جمادى الأولى"),
    ("moon-ar-ly-12h-slots-long-0440-aod.png", "ar-ly12, slots, " + AR_LONG),
    ("moon-ar-ly-12h-slots-long-2055-aod.png", "ar-ly12, slots, " + AR_LONG),
    ("moon-ar-ly-12h-slots-most-0440-aod.png", "ar-ly12, slots, " + AR_MOST),
    ("moon-ar-ly-12h-long-0440-aod.png", "ar-ly12, slots empty, " + AR_LONG),
    ("moon-ar-sa-slots-long-0440-aod.png", "ar-sa, slots, " + SA_LONG),
    ("moon-en-slots-0440-aod.png", "en 24h, slots (100, its steps), 6 Jumada al-Ula"),
    ("moon-en-slots-long-0440-aod.png", "en 24h, slots, " + EN_LONG),
    ("moon-en-slots-long-2055-aod.png", "en 24h, slots, " + EN_LONG),
    ("moon-en-long-0440-aod.png", "en 24h, slots empty, " + EN_LONG),
    ("moon-en12-slots-long-0440-aod.png", "en 12h, slots, " + EN_LONG),
    ("moon-ar-ly-12h-slots-most-890-0440-aod.png", "ar-ly12, slots (100, 890 steps), " + AR_MOST + " (review round 3)"),
    ("moon-ar-ly-12h-slots-most-960-2055-aod.png", "ar-ly12, slots (100, 960 steps), " + AR_MOST + " (review round 3)"),
    ("moon-en-slots-long-1030-0440-aod.png", "en 24h, slots (100, 1,030 steps), " + EN_LONG + " (review round 3)"),
    ("ring-en-1850-aod.png", "Ring en 24h, slots empty, 6 Jumada al-Ula"),
    ("ring-en-slots-1850-aod.png", "Ring en 24h, slots (100, its steps), 6 Jumada al-Ula"),
    ("ring-en-slots-long-1850-aod.png", "Ring en 24h, slots, " + EN_LONG),
    ("sun-en-slots-long-1800-aod.png", "Sun en 24h, slots, " + EN_LONG),
    ("arch-en-slots-long-1855-aod.png", "Arch en 24h, slots, " + EN_LONG),
]


# The rebuilt face's frames (the closing fix, Ruling 46): the Moon cases again, by name, the frames before
# it that show the same case (review round 3's three are the same cases with their counts), and what the
# rebuilt frame shows (its step count read off it).
REBUILT = [
    ("moon-ar-ly-12h-slots-0440-aod.png", ["moon-ar-ly-12h-slots-0440-aod.png"], "ar-ly12, slots (100, 630 steps), 6 جمادى الأولى"),
    ("moon-ar-ly-12h-slots-2055-aod.png", ["moon-ar-ly-12h-slots-2055-aod.png"], "ar-ly12, slots (100, 650 steps), 6 جمادى الأولى"),
    ("moon-ar-ly-12h-slots-long-0440-aod.png", ["moon-ar-ly-12h-slots-long-0440-aod.png"], "ar-ly12, slots (100, 740 steps), " + AR_LONG),
    ("moon-ar-ly-12h-slots-long-2055-aod.png", ["moon-ar-ly-12h-slots-long-2055-aod.png"], "ar-ly12, slots (100, 790 steps), " + AR_LONG),
    ("moon-ar-ly-12h-slots-most-0440-aod.png", ["moon-ar-ly-12h-slots-most-0440-aod.png", "moon-ar-ly-12h-slots-most-890-0440-aod.png"],
     "ar-ly12, slots (100, 840 steps), " + AR_MOST),
    ("moon-ar-ly-12h-slots-most-2055-aod.png", ["moon-ar-ly-12h-slots-most-960-2055-aod.png"], "ar-ly12, slots (100, 890 steps), " + AR_MOST),
    ("moon-ar-ly-12h-long-0440-aod.png", ["moon-ar-ly-12h-long-0440-aod.png"], "ar-ly12, slots empty, " + AR_LONG),
    ("moon-ar-sa-slots-long-0440-aod.png", ["moon-ar-sa-slots-long-0440-aod.png"], "ar-sa, slots (100, 940 steps), " + SA_LONG),
    ("moon-en-slots-0440-aod.png", ["moon-en-slots-0440-aod.png"], "en 24h, slots (100, 1000 steps), 6 Jumada al-Ula"),
    ("moon-en-slots-long-0440-aod.png", ["moon-en-slots-long-0440-aod.png", "moon-en-slots-long-1030-0440-aod.png"],
     "en 24h, slots (100, 1050 steps), " + EN_LONG),
    ("moon-en-slots-long-2055-aod.png", ["moon-en-slots-long-2055-aod.png"], "en 24h, slots (100, 1110 steps), " + EN_LONG),
    ("moon-en-long-0440-aod.png", ["moon-en-long-0440-aod.png"], "en 24h, slots empty, " + EN_LONG),
    ("moon-en12-slots-long-0440-aod.png", ["moon-en12-slots-long-0440-aod.png"], "en 12h, slots (100, 1160 steps), " + EN_LONG),
]


LONE = 0.05   # points: a sweep's share this far above both its neighbours, or below both, is drawn again alone
FILL_DRAWING, FILL_EMULATOR = (7, 9, 8), (6, 8, 7)   # the moon window's fill, #070908, and as the emulator draws it
WELL_DRAWING, WELL_EMULATOR = (11, 13, 12), (9, 11, 10)   # the date window's fill, WELL #0B0D0C, and as the emulator draws it


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(int)


def screen_of(b):
    h, w = b.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    return (xx + 0.5 - w / 2) ** 2 + (yy + 0.5 - h / 2) ** 2 <= (w / 2) ** 2, (xx + yy) % 2


def share(path, undither=False):
    """The lit share of the round screen, in percent: of a drawing, or with undither of an emulator
    always-on frame as the face draws it (lit.py undither)."""
    return lit.share(path, undither)


def frame(path):
    """An emulator always-on frame: (raw, as the face draws it, fix round 1's max-fill, mean light)."""
    b = load(path)
    screen, _ = screen_of(b)
    raw = 100.0 * ((b.max(axis=2) > 8) & screen).sum() / screen.sum()
    r = np.concatenate([b[:, 1:], b[:, -1:]], axis=1)
    d = np.concatenate([b[1:], b[-1:]], axis=0)
    filled = 100.0 * ((np.maximum(b, np.maximum(r, d)).max(axis=2) > 8) & screen).sum() / screen.sum()
    light = 2 * (0.2126 * b[..., 0] + 0.7152 * b[..., 1] + 0.0722 * b[..., 2])[screen].mean()
    return raw, lit.measure(b, True)[0], filled, light


def as_emulator(b, parity):
    """A drawing as the emulator draws it always-on: 0.85 of its colours, rounded, one parity black."""
    e = np.floor(b * lit.AOD_DIM + 0.5).astype(int)
    _, par = screen_of(e)
    e[par != parity] = 0
    return e


def without_fill(b, *fills):
    b = b.copy()
    for fill in fills:
        b[(b == fill).all(axis=2)] = 0
    return b


def drawn(tmp, opts, minute):
    """The drawing of a form at a minute of the mock day, as a file (a headless Chrome now and then
    fails or never returns: it is tried again)."""
    out = os.path.join(tmp, "%s-%d.png" % (abs(hash(tuple(opts))), minute))
    if os.path.exists(out):
        return out
    for _ in range(4):
        try:
            subprocess.run([sys.executable, "-I", REF, out, "face=moon-d", "aod", "sec=0", "t=%d" % minute] + opts,
                           check=True, capture_output=True, timeout=30)
            return out
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
            subprocess.run(["pkill", "-f", "--", "--screenshot=" + out])
    sys.exit("litworst.py: no drawing for %s at %d" % (opts, minute))


def drawing(tmp, opts, minute):
    """The drawing's share at a minute of the mock day."""
    return share(drawn(tmp, opts, minute))


def redrawn(tmp, opts, minute):
    """The drawing's share at a minute, drawn again on its own, in place of the one drawn in parallel."""
    os.remove(drawn(tmp, opts, minute))
    return drawing(tmp, opts, minute)


def sweep(ex, tmp, opts, day):
    """A form's share at each minute of day. Six headless Chromes at once now and then draw a frame a few
    tenths of a point off (the closing fix's sweep had en 12h at 12.93% at 23:05, which draws 12.38% on
    its own, Ruling 49), so the row is drawn in parallel and then, one at a time with nothing else
    drawing, again where a share stands alone, more than LONE above both its neighbours or below both,
    and at the day's highest until the highest is one drawn alone: one glitchy render cannot reach the
    file."""
    row = list(ex.map(lambda m: drawing(tmp, opts, m), day))
    alone = set()

    def lone(i):
        steps = [row[i] - row[j] for j in (i - 1, i + 1) if 0 <= j < len(row)]
        return all(d > LONE for d in steps) or all(d < -LONE for d in steps)

    while True:
        again = [i for i in range(len(row)) if i not in alone and lone(i)]
        top = int(np.argmax(row))
        if not again and top in alone:
            return row
        for i in again or [top]:
            row[i] = redrawn(tmp, opts, day[i])
            alone.add(i)


def minute(hhmm):
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    frames = sys.argv[1]
    step = next((int(a.split("=")[1]) for a in sys.argv[2:] if a.startswith("sweep=")), None)
    rebuilt = next((a.split("=", 1)[1] for a in sys.argv[2:] if a.startswith("rebuilt=")), None)
    worst = 0.0
    with tempfile.TemporaryDirectory() as tmp, cf.ThreadPoolExecutor(6) as ex:
        print("1. The drawing as built (ref.py aod sec=0, face=moon-d: Ruling 41's hands and, since the closing fix,"
              "\n   Ruling 46's moon window, its fill black), lit share; the limit is 15%:")
        print("%-46s %s" % ("form", " ".join("%7s" % t for t in MOMENTS)))
        for label, opts in FORMS:
            row = list(ex.map(lambda t: drawing(tmp, opts, minute(t)), MOMENTS))
            if "hands=outline" not in opts:
                worst = max(worst, max(row))
            print("%-46s %s" % (label, " ".join("%6.2f%%" % s for s in row)))
        print("the Moon's highest as built (Rulings 41 and 46): %.2f%% (under 15%%: %s)" % (worst, worst < 15))
        print("\nArch, Ring and Sun at their own worst moments (the day sweep's highest):")
        rows = list(ex.map(lambda o: drawing(tmp, o[1], minute(o[2])), OTHERS))
        for (label, _, t), sh in zip(OTHERS, rows):
            print("%-46s %s %6.2f%%" % (label, t, sh))
        if step:
            print("\n2. The drawing as built over the mock day every %d minutes (a share that stands alone, and the"
                  "\n   day's highest, drawn again on its own before the row is written):" % step)
            print("%-46s %16s %10s %8s" % ("form", "highest", ">= 14.5%", ">= 15%"))
            day = list(range(0, 1440, step))
            for label, opts in FORMS + [(label, opts) for label, opts, _ in OTHERS]:
                row = sweep(ex, tmp, opts, day)
                i = int(np.argmax(row))
                print("%-46s %7.2f%% at %02d:%02d %10d %8d" % (label, row[i], day[i] // 60, day[i] % 60,
                                                             sum(s >= 14.5 for s in row), sum(s >= 15 for s in row)))
        print("\n3. emulator-5558, the face before Ruling 46 (the face of 4608eb65; review round 3's three frames are"
              "\n   of 97779b42, the same face; Ring, Sun and Arch are as built): the lit share raw (the dithered"
              "\n   frame at the emulator's levels), as the face draws it (lit.py undither: twice the lit parity, lit"
              "\n   at 8 or more after the emulator's 0.85; the measure), and fix round 1's max-fill; the undithered"
              "\n   frame's mean light (0-255):")
        print("%-42s %6s %7s %8s %6s" % ("frame", "raw", "face", "round 1", "light"))
        high = {}
        for name, what in FRAMES:
            p = os.path.join(frames, name)
            if not os.path.exists(p):
                print("%-42s missing" % name)
                continue
            raw, face, filled, light = frame(p)
            style = name.split("-")[0]
            high[style] = max(high.get(style, 0.0), face)
            print("%-42s %5.2f%% %6.2f%% %7.2f%% %6.2f   %s" % (name, raw, face, filled, light, what))
        for style, u in high.items():
            print("%s's highest as the face draws it%s: %.2f%% (under 15%%: %s)" % (
                style, " (before Ruling 46)" if style == "moon" else "", u, u < 15))
        if rebuilt:
            print("\n   The same Moon cases on the face as rebuilt under Ruling 46 (the closing fix: the moon window's fill"
                  "\n   black always-on), in %s; the frame before it of each case beside it:" % os.path.basename(os.path.normpath(rebuilt)))
            print("%-42s %6s %7s %6s %18s" % ("frame", "raw", "face", "light", "before (face)"))
            built_high = 0.0
            for name, olds, what in REBUILT:
                p = os.path.join(rebuilt, name)
                if not os.path.exists(p):
                    print("%-42s missing" % name)
                    continue
                raw, face, _, light = frame(p)
                built_high = max(built_high, face)
                before = " / ".join("%.2f%%" % frame(os.path.join(frames, o))[1] for o in olds if os.path.exists(os.path.join(frames, o)))
                print("%-42s %5.2f%% %6.2f%% %6.2f %18s   %s" % (name, raw, face, light, before, what))
            print("moon's highest as the face draws it, as rebuilt: %.2f%% (under 15%%: %s)" % (built_high, built_high < 15))

        print("\n4. The measure checked: drawings drawn as the emulator draws them always-on (0.85 of their"
              "\n   colours, rounded, then each parity black in turn), measured as section 3 measures a frame:")
        print("%-56s %7s %15s %15s" % ("drawing (each parity in turn)", "own", "as section 3", "round 1's"))
        checks = [(FORMS[3][0] + ", 20:55", FORMS[3][1], "20:55"),
                  (FORMS[3][0] + ", outline hands, 20:55", FORMS[4][1], "20:55"),
                  (FORMS[8][0] + ", 04:40", FORMS[8][1], "04:40"),
                  (FORMS[8][0] + ", outline hands, 04:40", FORMS[9][1], "04:40")]
        checks += [(label + ", " + t, opts, t) for label, opts, t in OTHERS if label.startswith(("Ring", "Sun", "Arch"))]
        error = 0.0
        for label, opts, t in checks:
            b = load(drawn(tmp, opts, minute(t)))
            own = lit.measure(b)[0]
            ours, old = [], []
            for parity in (0, 1):
                e = as_emulator(b, parity)
                ours.append(lit.measure(e, True)[0])
                r = np.concatenate([e[:, 1:], e[:, -1:]], axis=1)
                d = np.concatenate([e[1:], e[-1:]], axis=0)
                old.append(lit.measure(np.maximum(e, np.maximum(r, d)))[0])
            error = max(error, max(abs(s - own) for s in ours))
            print("%-56s %6.2f%% %6.2f / %5.2f%% %6.2f / %5.2f%%" % (label, own, ours[0], ours[1], old[0], old[1]))
        print("section 3's measure is within %.2f point of each drawing's own share" % error)

        print("\n5. The two levers of fix round 3: the moon window's fill black always-on, which Ruling 46 chose and"
              "\n   the closing fix built, and the date window's fill (WELL) black too, which it left as it is; lit share."
              "\n   The drawing: before Ruling 46 (ref.py window=filled), the same with the fill's own pixels black (what"
              "\n   fix round 3 expected), as built (ref.py's default), and as built with WELL's own pixels black:")
        print("%-60s %8s %10s %9s %10s" % ("", "before", "fill black", "as built", "WELL black"))
        for label, opts in FORMS:
            if "hands=outline" in opts:
                continue
            for t in MOMENTS[:2]:
                before = load(drawn(tmp, opts + ["window=filled"], minute(t)))
                built = load(drawn(tmp, opts, minute(t)))
                print("%-60s %7.2f%% %9.2f%% %8.2f%% %9.2f%%" % (
                    "drawing, " + label.strip() + ", " + t, lit.measure(before)[0], lit.measure(without_fill(before, FILL_DRAWING))[0],
                    lit.measure(built)[0], lit.measure(without_fill(built, WELL_DRAWING))[0]))
        print("   emulator-5558: the frame before Ruling 46, the same with the fill's own pixels black, the rebuilt"
              "\n   face's frame of the same case, and that frame with WELL's own pixels black:")
        for name, olds, _ in REBUILT:
            p = os.path.join(rebuilt, name) if rebuilt else None
            built = load(p) if p and os.path.exists(p) else None
            for old in olds:
                o = os.path.join(frames, old)
                if not os.path.exists(o):
                    continue
                b = load(o)
                print("%-60s %7.2f%% %9.2f%% %8s %10s" % (
                    "emulator, " + old, lit.measure(b, True)[0], lit.measure(without_fill(b, FILL_EMULATOR), True)[0],
                    "%.2f%%" % lit.measure(built, True)[0] if built is not None else "-",
                    "%.2f%%" % lit.measure(without_fill(built, WELL_EMULATOR), True)[0] if built is not None else "-"))

if __name__ == "__main__":
    main()
