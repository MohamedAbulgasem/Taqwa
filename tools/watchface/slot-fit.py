"""Checks that the wearer's slots fit the values a watch's sources write: the battery, a step count, a
heart rate, a time, a date, a temperature and the like, in the forms Wear OS writes them. It takes the
slots' rule from tools/face-centre.py, which writes it into the face: each value at faces.js's size,
scaled down to its room by an estimate of its width made from its characters (VALUE_EM), no less
than MIN_SCALE. It works out the scale as the face would, draws the value in the face's Manrope
ExtraBold as Android shapes it (Pillow with Raqm), and fails if a value scaled so comes out wider
than its room by more than the slack (slack= px), or would end in an ellipsis:

    python3 -I tools/watchface/slot-fit.py            # every value, both places; fails if one does not fit
    python3 -I tools/watchface/slot-fit.py slack=1    # a tighter slack
    python3 -I tools/watchface/slot-fit.py show=6,410 show=12,345   # how given values come out

The places: a bottom slot's value (VALUE_ROOM px from its start; past it lie 4 px to the left slot's
edge, and on Ring and Sun 7 px to the ring's inner edge at the baseline), and the Moon's two, curved
along its bottom (option (d), tools/face-moon.py moon_room(): each slot's run from six less its icon,
the left one's ending at its anchor, the right one's starting after its icon; past either lie about
4 px to the next hour index). The default slack, 3 px, stays inside each. On the Moon a value scaled
down is drawn on a wider circle than the baseline (face-moon.py MOON_CURVE), so its far end lies off
the baseline by a little: the run prints the most, px, towards the centre (+) or away from it (-).
The emulator agrees with the widths to about a pixel; tools/watchface/choose.sh fills the slots with
the emulator's own sources (dates, times, sunrise) to see them there.
"""
import importlib.util
import os
import sys

from PIL import ImageFont, features

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = os.path.join(REPO, "watchface", "src", "main", "res", "font", "manrope_extrabold.ttf")
SS = 8

_spec = importlib.util.spec_from_file_location("face_centre", os.path.join(REPO, "tools", "face-centre.py"))
face_centre = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(face_centre)
_spec = importlib.util.spec_from_file_location("face_moon", os.path.join(REPO, "tools", "face-moon.py"))
face_moon = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(face_moon)


def off_curve(drawn, s):
    """How far the far end of a Moon value drawn px long at scale s lies inside the baseline, px."""
    r = face_moon.MOON_R
    return drawn ** 2 / 2 * (1 / (s * face_moon.MOON_CURVE * r) - 1 / r)


def estimate(text):
    """The face's estimate of text's advance, em (face_centre.value_em, worked out here)."""
    total = 0.0
    for i, c in enumerate(text):
        if i >= face_centre.VALUE_EM_CHARS:
            total += face_centre.VALUE_EM_REST
            continue
        total += next((em for chars, em in face_centre.VALUE_EM if c in chars), face_centre.VALUE_EM_REST)
    return total


def scale_of(text, size, room):
    return min(max(room / (size * estimate(text) + 0.01), face_centre.MIN_SCALE), 1.0)


def values():
    """What the watch's own sources and the common ones write, by kind."""
    out = {}
    nums = list(range(0, 1000)) + list(range(1000, 100000, 3)) + [9999, 99999, 100000]
    # step counts and other counts, grouped as English, German and French write them, and ungrouped
    out["count"] = sorted({"{:,}".format(n) for n in nums} | {"{:,}".format(n).replace(",", ".") for n in nums}
                          | {"{:,}".format(n).replace(",", " ") for n in nums} | {str(n) for n in nums})
    out["percent"] = ["%d%%" % n for n in range(0, 101)] + ["%d" % n for n in range(0, 101)]
    out["time"] = sorted({"%d:%02d" % (h, m) for h in range(0, 24) for m in range(0, 60)}
                         | {"%02d:%02d" % (h, m) for h in range(0, 24) for m in range(0, 60)})
    out["temperature"] = ["%d°" % t for t in range(-40, 125)]
    days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    out["date"] = (["%d" % d for d in range(1, 32)] + ["%s %d" % (w, d) for w in days for d in range(1, 32)]
                   + ["%d %s" % (d, m) for d in range(1, 32) for m in ("Jan", "May", "Oct", "Nov")] + days)
    out["heart"] = ["%d" % n for n in range(30, 221)] + ["--"]
    return out


def main(argv):
    opts = {}
    shows = []
    for a in argv:
        if a.startswith("show="):
            shows.append(a[5:])
        elif "=" in a:
            k, v = a.split("=", 1)
            opts[k] = v
    slack = float(opts.get("slack", 3))
    if not features.check("raqm"):
        sys.exit("Pillow has no Raqm here, so the values cannot be shaped as Android shapes them")
    places = [("bottom", face_centre.VALUE_SIZE, face_centre.VALUE_ROOM),
              ("Moon left", face_moon.MOON_SIZE, int(face_moon.moon_room(4))),
              ("Moon right", face_moon.MOON_SIZE, int(face_moon.moon_room(5)))]
    fonts = {size: ImageFont.truetype(FONT, round(size * SS), layout_engine=ImageFont.Layout.RAQM) for _, size, _ in places}

    def width(text, size):
        return fonts[size].getlength(text) / SS

    if shows:
        for text in shows:
            for name, size, room in places:
                s = scale_of(text, size, room)
                curve = (", its end %+.1f px off the baseline" % off_curve(width(text, size) * s, s)) if name.startswith("Moon") else ""
                print("%-8r %-12s scale %.3f, %.1f px at %.2f px (room %d)%s" % (text, name, s, width(text, size) * s, size * s, room, curve))
        return
    print("The wearer's slots (tools/face-centre.py VALUE_ROOM %d, MIN_SCALE %s; face-moon.py's rooms %s), slack %s px:"
          % (face_centre.VALUE_ROOM, face_centre.MIN_SCALE, " and ".join(str(r) for n, _, r in places if n.startswith("Moon")), slack))
    bad = 0
    for kind, texts in values().items():
        for name, size, room in places:
            worst, worst_text, least, full, bend = -99.0, None, 1.0, 0, (0.0, None)
            for text in texts:
                s = scale_of(text, size, room)
                w = width(text, size)
                over = w * s - room
                if name.startswith("Moon"):
                    d = off_curve(w * s, s)
                    if abs(d) > abs(bend[0]):
                        bend = (d, text)
                if over > worst:
                    worst, worst_text = over, text
                least = min(least, s)
                full += s >= 1.0
                if over > slack or w > room / face_centre.MIN_SCALE:
                    bad += 1
                    if bad <= 20:
                        print("  DOES NOT FIT: %r in the %s, %.1f px against %d" % (text, name, w * s, room))
            print("  %-11s %-12s %6d values, %3d%% at full size, least scale %.2f, widest %+.1f px (%r)%s"
                  % (kind, name, len(texts), round(100.0 * full / len(texts)), least, worst, worst_text,
                     ("; off the baseline at most %+.1f px (%r)" % bend) if name.startswith("Moon") else ""))
    if bad:
        sys.exit("tools/watchface/slot-fit.py: %d values do not fit" % bad)
    print("tools/watchface/slot-fit.py: every value fits")


if __name__ == "__main__":
    main(sys.argv[1:])
