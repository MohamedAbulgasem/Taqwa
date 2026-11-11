"""Checks the centre's two lines in the phone's own words on Arch, Ring and Sun, for every form the
phone can send: the Gregorian date (slot 2's text, "SUN 18 OCT") and the countdown (slot 6's text,
"in 1 h 12 min"). The forms are the phone's, from tools/watchface/phone-forms.json (shared's
FaceFormsTest writes it from the phone's own code: CLDR's weekdays and months in the words locale,
in the face's capitals, the countdown's phrases, and every digit set each tag can carry), and each
line is drawn as the face draws it, from tools/face-centre.py (CENTRE_LINE_FONT, LINE_BOX,
ARCH_GREGORIAN_STEPS and the boxes' tops):

- the Gregorian date: every weekday beside every month and every day (1 to 31), in each digit set
  of each tag, as the feed writes it ("$weekday $day $month", FaceFormsTest's
  formsComposeAsTheFeedWritesThem). On Arch it must keep min= px (7 by default) from the lit
  stroke, as Arch's Hijri line does (hijri-fit.py's curve and clearance), and be drawn whole in its
  box; on Ring and Sun be drawn whole in the line box. Right to left on Arch, the line's size is
  the one its length and its month pick, the baseline kept (ARCH_GREGORIAN_STEPS, Ruling 51a's
  month rule A: 19 characters or more, 11.25 units; else August as the phone writes it, أغسطس, 13;
  else 14), and the run also checks that watchface.xml draws those steps: its Groups' sizes and
  boxes, and for every right-to-left form the one Group the face's own expressions show (read by
  shows(); date-fit-test.py tests that reading);
- the countdown: each of its four phrases with every hour (1 to 99, the cap) and minute (1 to 59)
  in each digit set, drawn whole in its box on Arch, Ring and Sun.

Drawn whole is hijri-fit.py's rule: the line's width, its letter spacing included, at least 2 px
under its box's, so that the box's ellipsis, a guard, never cuts it. The box is the same 360 px on
all three styles (face-centre.py LINE_BOX; Sun takes Ring's places), and each line is checked in it
at each size it is drawn at (Ring's and Sun's, and on Arch its step's), and Arch's lit stroke is
checked at each of the line's places (its own, and LAYOUT_COMPS's when the wearer's slots are
filled). The countdown's room to Arch's lit feet is printed too.

    python3 -I tools/watchface/date-fit.py                   # every form; fails if one does not fit
    python3 -I tools/watchface/date-fit.py min=7             # another margin to the lit stroke (px)
    python3 -I tools/watchface/date-fit.py forms=<file>      # another phone-forms.json (a device's)
    python3 -I tools/watchface/date-fit.py fonts=<dir>       # the watch's Bengali font from <dir>
    python3 -I tools/watchface/date-fit-test.py              # the tests of its reading of Arch's steps

Fonts: Latin in the face's Manrope, ExtraBold tracked 1.3/13 em for the date and SemiBold for the
countdown; Arabic and Urdu in its Noto Sans Arabic, Bold and SemiBold, unspaced; Bengali, which
Manrope lacks, in the watch's own Noto Sans Bengali UI at the weight of the run it falls back from
(800, 600), unspaced: Android spaces the letters of neither script (Minikin leaves the scripts whose
letters join unspaced, as window-fit.py says). The Bengali font comes from emulator-5558's
/system/fonts when no fonts= is given; without it the run fails, as hijri-fit.py's does. A form
with a character its font lacks fails too (it would fall back to another font on the watch, and
its width here would mean nothing); that check needs hb-shape (Homebrew's harfbuzz), as
moon-fit.py's glyph count does.

The baseline of each box is face-centre.py's: the box's middle plus (ascent - descent) / 2 of the
run's font. Widths come from Pillow with Raqm (HarfBuzz), as Android shapes them. A line's room to
the lit stroke is first bounded by its ink's box (never more than the room its ink has), and a line
whose bound comes under min= is drawn and measured row by row, as hijri-fit.py measures.
"""
import collections
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import unicodedata
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image, ImageDraw, ImageFont, features

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = os.path.join(REPO, "watchface", "src", "main", "res", "font")
FORMS = os.path.join(REPO, "tools", "watchface", "phone-forms.json")
SS = 4
GUARD = 2   # px a line keeps under its box's width (hijri-fit.py's)

_spec = importlib.util.spec_from_file_location("hijri_fit", os.path.join(REPO, "tools", "watchface", "hijri-fit.py"))
H = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(H)
FC = H.face_centre
K = FC.K


def is_bengali(text):
    return any("ঀ" <= ch <= "৿" for ch in text)


def clusters(text):
    """The letters a letterSpacing is added to: characters that are not marks on another."""
    return sum(1 for ch in text if unicodedata.category(ch) not in ("Mn", "Mc", "Me"))


class Line:
    """One of the centre's lines in one direction, as face-centre.py writes it: its font, size and
    spacing, its box's x and width, and its baselines by style and the wearer's slots."""

    def __init__(self, name, rtl, bengali_dir, size=None):
        """size: one of ARCH_GREGORIAN_STEPS' sizes for Arch's right-to-left date; by default the
        line's own (CENTRE_LINE_FONT), Ring's and Sun's."""
        self.name, self.rtl = name, rtl
        family, self.size, spacing = FC.CENTRE_LINE_FONT[name][rtl]
        self.size = self.size if size is None else size
        self.spacing = spacing or 0.0
        self.path, self.weight = os.path.join(FONT_DIR, family + ".ttf"), None
        self.family = family
        # Bengali falls back from the Latin run, at its weight
        self.bengali = (os.path.join(bengali_dir, "NotoSansBengaliUI-VF.ttf"), 800 if "extrabold" in family else 600)
        r, tops, self.h = FC.centre_line(name, rtl, size)
        below = FC.baseline_below_middle([r])[0]
        self.baselines = {key: top + self.h / 2 + below for key, top in tops.items()}
        self.x, self.width = FC.LINE_BOX

    def face(self, text):
        """(path, weight, spacing em) the watch draws text in on this line."""
        if not self.rtl and is_bengali(text):
            return self.bengali[0], self.bengali[1], 0.0
        return self.path, self.weight, self.spacing


class Step(collections.namedtuple("Step", "name least month line")):
    """One of Arch's right-to-left Gregorian steps (ARCH_GREGORIAN_STEPS): its name (its Group is
    greg_arch_rtl_<name>), the least characters it takes or None, the month a text must end in for
    it or None, and its Line."""

    def says(self):
        if self.least is None and self.month is None:
            return "the rest"
        return " and ".join(([("%d chars or more" % self.least)] if self.least is not None else []) +
                            ([("ending in %s" % self.month)] if self.month is not None else []))


def arch_steps(bengali_dir):
    """Arch's right-to-left Gregorian line as face-centre.py writes it (ARCH_GREGORIAN_STEPS), in
    order: [Step]. A text takes the first step whose tests it meets."""
    return [Step(name, least, month, Line("greg", True, bengali_dir, size))
            for name, least, month, size in FC.ARCH_GREGORIAN_STEPS]


def step_for(steps, text):
    """The step of steps a text takes: the first whose least it reaches and whose month it ends in,
    as the face's textLength() and subText() read slot 2's text (face_steps_check() holds the face
    to this)."""
    return next(s for s in steps if (s.least is None or len(text) >= s.least)
                and (s.month is None or text.endswith(s.month)))


# ---- The face's expressions, as the runtime reads them

_TOKEN = re.compile(r'\s*(?:(\d+)|"([^"]*)"|(\[[A-Z0-9_.]+\])|([A-Za-z]\w*)|(<=|>=|==|!=|&&|\|\||[-+<>!?:(),]))')


def _tokens(expression):
    out, i = [], 0
    while expression[i:].strip():
        m = _TOKEN.match(expression, i)
        if not m:
            raise ValueError("cannot read %r at %r" % (expression, expression[i:]))
        number, string, source, name, op = m.groups()
        out.append(("num", int(number)) if number is not None else ("str", string) if string is not None else
                   ("src", source) if source else ("name", name) if name else ("op", op))
        i = m.end()
    return out + [("end", None)]


def _compile(expression):
    """A function of slot 2's text giving expression's value, for the part of WFF's expression
    language the face's Gregorian Groups use: whole numbers, "strings", [COMPLICATION.TEXT],
    textLength(), subText(text, start, end), clamp(), unary ! and -, + -, < <= > >=, == !=, && ||
    and ?:, with C's precedence. Anything else raises ValueError, so a face this reader cannot read
    fails, and so does a subText() outside its text or a string compared with a number."""
    toks = _tokens(expression)
    pos = [0]

    def peek():
        return toks[pos[0]]

    def take(kind=None, value=None):
        tok = toks[pos[0]]
        if (kind and tok[0] != kind) or (value is not None and tok[1] != value):
            raise ValueError("%r: wanted %s, found %r" % (expression, value or kind, tok[1]))
        pos[0] += 1
        return tok

    def num(v):
        if isinstance(v, bool) or not isinstance(v, int):
            raise ValueError("%r: %r is not a number" % (expression, v))
        return v

    def truth(v):
        return bool(num(int(v)) if isinstance(v, bool) else num(v))

    def same_kind(a, b):
        if isinstance(a, str) != isinstance(b, str):
            raise ValueError("%r compares %r with %r" % (expression, a, b))
        return a, b

    def text_of(v):
        if not isinstance(v, str):
            raise ValueError("%r: %r is not a text" % (expression, v))
        return v

    def sub_text(s, start, end):
        s, start, end = text_of(s), num(start), num(end)
        if not 0 <= start <= end <= len(s):
            raise ValueError("%r: subText(%r, %d, %d) is outside its text" % (expression, s, start, end))
        return s[start:end]

    funcs = {"textLength": (1, lambda s: len(text_of(s))), "subText": (3, sub_text),
             "clamp": (3, lambda v, lo, hi: min(max(num(v), num(lo)), num(hi)))}

    def primary():
        kind, value = take()
        if kind == "num" or kind == "str":
            return lambda t: value
        if kind == "src":
            if value != "[COMPLICATION.TEXT]":
                raise ValueError("%r reads %s, not slot 2's text" % (expression, value))
            return lambda t: t
        if kind == "name":
            if value not in funcs:
                raise ValueError("%r calls %s, which this reader does not know" % (expression, value))
            arity, fn = funcs[value]
            take("op", "(")
            args = [ternary()]
            while peek() == ("op", ","):
                take()
                args.append(ternary())
            take("op", ")")
            if len(args) != arity:
                raise ValueError("%r: %s takes %d arguments" % (expression, value, arity))
            return lambda t: fn(*(a(t) for a in args))
        if (kind, value) == ("op", "("):
            inner = ternary()
            take("op", ")")
            return inner
        raise ValueError("%r: unexpected %r" % (expression, value))

    def unary():
        if peek() == ("op", "!"):
            take()
            a = unary()
            return lambda t: not truth(a(t))
        if peek() == ("op", "-"):
            take()
            a = unary()
            return lambda t: -num(a(t))
        return primary()

    def binary(next_level, ops):
        def level():
            left = next_level()
            while peek()[0] == "op" and peek()[1] in ops:
                fn = ops[take()[1]]
                right = next_level()
                left = (lambda f, a, b: lambda t: f(a, b, t))(fn, left, right)
            return left
        return level

    additive = binary(unary, {"+": lambda a, b, t: num(a(t)) + num(b(t)), "-": lambda a, b, t: num(a(t)) - num(b(t))})
    relational = binary(additive, {"<": lambda a, b, t: num(a(t)) < num(b(t)), "<=": lambda a, b, t: num(a(t)) <= num(b(t)),
                                   ">": lambda a, b, t: num(a(t)) > num(b(t)), ">=": lambda a, b, t: num(a(t)) >= num(b(t))})
    equality = binary(relational, {"==": lambda a, b, t: (lambda x, y: x == y)(*same_kind(a(t), b(t))),
                                   "!=": lambda a, b, t: (lambda x, y: x != y)(*same_kind(a(t), b(t)))})
    conjunction = binary(equality, {"&&": lambda a, b, t: truth(a(t)) and truth(b(t))})
    disjunction = binary(conjunction, {"||": lambda a, b, t: truth(a(t)) or truth(b(t))})

    def ternary():
        cond = disjunction()
        if peek() != ("op", "?"):
            return cond
        take()
        yes = ternary()
        take("op", ":")
        no = ternary()
        return lambda t: yes(t) if truth(cond(t)) else no(t)

    out = ternary()
    take("end")
    return out


def shows(expression, text):
    """The value a face expression gives when slot 2's text is text (_compile()'s reader)."""
    return _compile(expression)(text)


def written_steps(path=FC.FACE_XML):
    """Arch's right-to-left Gregorian steps as watchface.xml draws them, in its order: [(name, size,
    box top, box height, the Group's alpha expression or None)] from its greg_arch_rtl_<name> Groups."""
    out = []
    for g in ET.parse(path).getroot().iter("Group"):
        name = g.get("name", "")
        if not name.startswith("greg_arch_rtl_"):
            continue
        show = next((t.get("value") for t in g.findall("Transform") if t.get("target") == "alpha"), None)
        part = g.find("PartText")
        out.append((name[len("greg_arch_rtl_"):], float(part.find("Text/Font").get("size")),
                    int(part.get("y")), int(part.get("height")), show))
    return out


def expected_steps(steps):
    """written_steps()'s rows, but the expression, as face-centre.py's table and boxes give them."""
    return [(s.name, s.line.size, round(s.line.baselines[("arch", False)] - s.line.h / 2 - FC.baseline_below_middle(
        [(s.line.family, s.line.size)])[0]), s.line.h) for s in steps]


def face_steps_check(written, steps, texts):
    """The face's right-to-left Gregorian Groups on Arch (written_steps()) against the table
    (arch_steps()): [failures]. The Groups must be the table's steps, in order, each at its size
    and in its box (expected_steps()); and for each of texts the face must show exactly one Group,
    the one its step names (step_for()), by reading each Group's alpha expression as the runtime
    does (shows()). A text outside the Basic Multilingual Plane fails: the runtime's textLength()
    and subText() may count its characters otherwise than len() does."""
    out = []
    wanted = expected_steps(steps)
    drawn = [row[:4] for row in written]
    if drawn != wanted:
        out.append("watchface.xml draws Arch's right-to-left Gregorian steps as %s, and ARCH_GREGORIAN_STEPS"
                   " says %s: run tools/face-centre.py" % (drawn, wanted))
    try:
        alphas = [(row[0], _compile(row[4]) if row[4] is not None else (lambda t: 255)) for row in written]
    except ValueError as e:
        return out + ["watchface.xml's Arch right-to-left Gregorian Groups: %s" % e]
    wrong = []
    for text in texts:
        if any(ord(ch) > 0xFFFF for ch in text):
            out.append("%s: a character outside the Basic Multilingual Plane, which the face's textLength()"
                       " may count otherwise" % text)
            continue
        try:
            shown = [name for name, alpha in alphas if truth_of(alpha(text))]
        except ValueError as e:
            out.append("%s: %s" % (text, e))
            continue
        step = step_for(steps, text).name
        if shown != [step]:
            wrong.append("the face shows %s for %s (%d chars), and its step is %s" % (
                " and ".join(shown) or "no Group", text or '""', len(text), step))
    if wrong:
        out.append("%d forms are drawn in another Group than their step's:" % len(wrong))
        out += ["  " + w for w in wrong[:10]]
    return out


def truth_of(alpha):
    """A Group is drawn when its alpha is not 0."""
    return alpha != 0


_fonts = {}


def font(path, weight, size):
    key = (path, weight, size)
    if key not in _fonts:
        f = ImageFont.truetype(path, round(size * SS), layout_engine=ImageFont.Layout.RAQM)
        if weight:
            f.set_variation_by_axes([weight])
        _fonts[key] = f
    return _fonts[key]


def layout(text, line):
    """(advance px, ink left, ink right, ink top, ink bottom), the ink in face px about the centred
    line's pen (x from the line's start, y from its baseline, up negative)."""
    path, weight, spacing = line.face(text)
    f = font(path, weight, line.size)
    kw = {"direction": "rtl"} if line.rtl else {}
    left, top, right, bottom = f.getbbox(text, anchor="ls", **kw)
    sp = spacing * line.size
    n = clusters(text)
    advance = f.getlength(text, **kw) / SS + n * sp
    # each letter sits half its spacing in, and the letters after the first one spacing further on
    return advance, left / SS + sp / 2, right / SS + sp / 2 + max(n - 1, 0) * sp, top / SS, bottom / SS


def box_bound(text, line, baseline):
    """The least room, px, between the lit stroke and the box of the line's ink, centred on the
    face's axis at baseline: never more than the room the ink itself has. It is hijri-fit.py's
    clearance() with the box for the ink: each point of the lit stroke's centre line (H.CURVE) to
    the nearest point of the box, less the stroke's half width."""
    advance, l, r, t, b = layout(text, line)
    x0 = FC.C - advance / 2
    left, right, top, bottom = x0 + l, x0 + r, baseline + t, baseline + b
    dx = np.maximum(np.maximum(left - H.CURVE[:, 0], H.CURVE[:, 0] - right), 0)
    dy = np.maximum(np.maximum(top - H.CURVE[:, 1], H.CURVE[:, 1] - bottom), 0)
    return np.hypot(dx, dy).min() - H.LIT_HALF


def exact(text, line, baseline):
    """The least room, px, between the lit stroke and the line's ink, drawn as the face draws it and
    measured row by row (hijri-fit.py's measure, with the line's letter spacing)."""
    path, weight, spacing = line.face(text)
    f = font(path, weight, line.size)
    advance = layout(text, line)[0]
    top = int(baseline - 2 * line.size) - 4
    img = Image.new("L", (450 * SS, int(4 * line.size + 8) * SS), 0)
    d = ImageDraw.Draw(img)
    x0 = (FC.C - advance / 2) * SS
    y = (baseline - top) * SS
    if spacing and not line.rtl:
        sp = spacing * line.size * SS
        for i, ch in enumerate(text):
            d.text((x0 + f.getlength(text[:i]) + i * sp + sp / 2, y), ch, font=f, fill=255, anchor="ls")
    else:
        kw = {"direction": "rtl"} if line.rtl else {}
        d.text((x0, y), text, font=f, fill=255, anchor="ls", **kw)
    ink = np.asarray(img) > 96
    xs, ys = [], []
    for row in np.nonzero(ink.any(axis=1))[0]:
        cols = np.nonzero(ink[row])[0]
        xs += [cols[0] / SS, (cols[-1] + 1) / SS]
        ys += [row / SS + top] * 2
    return H.clearance(np.array(xs), np.array(ys))


def uncovered(texts, line):
    """[(text, its font)] of texts holding a character their font has no glyph for, by hb-shape
    (glyph 0); None without hb-shape."""
    groups = {}
    for text in texts:
        path, weight, _ = line.face(text)
        groups.setdefault((path, weight), set()).add(text)
    bad = []
    for (path, weight), group in groups.items():
        group = sorted(group)
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".txt", delete=False) as fh:
            fh.write("".join(t + "\n" for t in group))
        try:
            args = ["hb-shape", "--no-glyph-names", "--no-positions", "--no-clusters", "--text-file=" + fh.name, path]
            if weight:
                args[1:1] = ["--variations=wght=%d" % weight]
            out = subprocess.run(args, check=True, capture_output=True, text=True).stdout.splitlines()
        except (OSError, subprocess.SubprocessError):
            return None
        finally:
            os.unlink(fh.name)
        for text, shaped in zip(group, out):
            if "0" in shaped.strip().strip("[]").split("|"):
                bad.append((text, os.path.basename(path)))
    return bad


# ---- The forms the phone sends

def in_set(n, digit_set):
    return "".join(digit_set[int(c)] for c in str(n))


def gregorian_lines(loc, digit_set):
    for weekday in loc["weekdays"]:
        for month in loc["months"]:
            for day in range(1, 32):
                yield "%s %s %s" % (weekday, in_set(day, digit_set), month)


def countdowns(phrases, digit_set):
    yield phrases["underAMinute"]
    for m in range(1, 60):
        yield phrases["minutes"].replace("%1$s", in_set(m, digit_set))
    for h in range(1, 100):
        yield phrases["hours"].replace("%1$s", in_set(h, digit_set))
        for m in range(1, 60):
            yield phrases["hoursMinutes"].replace("%1$s", in_set(h, digit_set)).replace("%2$s", in_set(m, digit_set))


def set_name(digit_set):
    return {"0": "Western", "٠": "Arabic-Indic", "۰": "extended Arabic-Indic", "০": "Bengali"}.get(digit_set[0], "U+%04X" % ord(digit_set[0]))


# ---- The run

def main(argv):
    opts = dict(a.split("=", 1) for a in argv if "=" in a)
    if not features.check("raqm"):
        sys.exit("Pillow has no Raqm here, so Arabic and Bengali cannot be shaped")
    with tempfile.TemporaryDirectory(prefix="taqwa-date-fit.") as scratch:
        sysdir = H.system_fonts(opts.get("fonts"), scratch)
        if not os.path.exists(os.path.join(sysdir, "NotoSansBengaliUI-VF.ttf")):
            print("no Bengali font (fonts=<dir> holding NotoSansBengaliUI-VF.ttf, or start emulator-5558):"
                  " Bengali's are among the widest forms, so the run cannot pass without it")
            return 1
        return run(opts, sysdir, float(opts.get("min", 7)))


def run(opts, sysdir, least):
    path = opts.get("forms", FORMS)
    with open(path, encoding="utf-8") as fh:
        locales = json.load(fh)["locales"]
    lines = {(name, rtl): Line(name, rtl, sysdir) for name in ("greg", "count") for rtl in (False, True)}
    steps = arch_steps(sysdir)
    failures = []
    wanted = expected_steps(steps)
    print("from %s: %d tags (%s)" % (os.path.relpath(path, REPO), len(locales), ", ".join(locales)))
    for (name, rtl), line in sorted(lines.items()):
        print("  %-5s %-13s %s %.2f px%s, box x %d..%d; baselines Arch %s, Ring and Sun %s" % (
            name, "right to left" if rtl else "left to right", line.family, line.size,
            " tracked %.4f em" % line.spacing if line.spacing else "", line.x, line.x + line.width,
            " and ".join("%.2f" % line.baselines[("arch", c)] for c in (False, True)
                         if c is False or line.baselines[("arch", True)] != line.baselines[("arch", False)]),
            " and ".join("%.2f" % line.baselines[("ring", c)] for c in (False, True))))
    print("  Arch's right-to-left date by its length and its month, the first step that applies"
          " (tools/face-centre.py ARCH_GREGORIAN_STEPS):")
    for s, row in zip(steps, wanted):
        print("    %-8s %-20s %5.2f px (%5.2f units), box y %d, height %d; baselines %s" % (
            s.name, s.says(), s.line.size, s.line.size / K, row[2], s.line.h,
            " and ".join("%.2f" % s.line.baselines[("arch", c)] for c in (False, True)
                         if c is False or s.line.baselines[("arch", True)] != s.line.baselines[("arch", False)])))

    # every form, once per line it is drawn on, with the tags and digit sets that send it
    forms = {}
    for tag, loc in locales.items():
        for digit_set in loc["digitSets"]:
            for text in gregorian_lines(loc, digit_set):
                forms.setdefault(("greg", loc["rtl"], text), set()).add((tag, digit_set))
            for text in countdowns(loc["countdown"], digit_set):
                forms.setdefault(("count", loc["rtl"], text), set()).add((tag, digit_set))
    # the face's Groups draw each right-to-left date at the step step_for() gives it (and an empty text)
    failures += face_steps_check(written_steps(), steps, sorted(t for (n, r, t) in forms if (n, r) == ("greg", True)) + [""])

    for key, line in sorted(lines.items()):
        texts = [t for (n, r, t) in forms if (n, r) == key]
        bad = uncovered(texts, line)
        if bad is None:
            failures.append("hb-shape (Homebrew's harfbuzz) is not here, so no form's glyphs can be checked")
            break
        for text, fname in bad[:10]:
            failures.append("%s (%s): a character %s has no glyph for, which the watch would draw in another font" % (text, key[0], fname))

    room = FC.LINE_BOX[1] - GUARD
    # (name, tag, digit set) -> [widest (advance, text), least room on Arch (px, text)]
    stats = {}
    # Arch's right-to-left date, by step: name -> [forms, widest (advance, text), least room (px, text)]
    by_step = {s.name: [0, (0.0, ""), (float("inf"), "")] for s in steps}
    exact_count = 0
    for (name, rtl, text), senders in sorted(forms.items()):
        line = lines[(name, rtl)]
        # on Arch the right-to-left date is drawn at its step's size
        on_arch = step_for(steps, text).line if (name, rtl) == ("greg", True) else line
        advance = max(layout(text, line)[0], layout(text, on_arch)[0])
        clear = None
        places = sorted({on_arch.baselines[("arch", comps)] for comps in (False, True)})
        if name == "greg":
            for base in places:
                c = box_bound(text, on_arch, base)
                if c < least + 0.5:
                    c = exact(text, on_arch, base)
                    exact_count += 1
                clear = c if clear is None else min(clear, c)
            if rtl:
                s = by_step[step_for(steps, text).name]
                s[0] += 1
                s[1] = max(s[1], (layout(text, on_arch)[0], text))
                s[2] = min(s[2], (clear, text))
        else:
            clear = min(box_bound(text, on_arch, base) for base in places)
        for tag, digit_set in senders:
            s = stats.setdefault((name, tag, digit_set), [(0.0, ""), (float("inf"), ""), 0])
            s[0] = max(s[0], (advance, text))
            s[1] = min(s[1], (clear, text))
            s[2] += 1
        if advance > room:
            failures.append("%s %s (%s): %.1f px wide, in a %d px box: the ellipsis would cut it" % (
                name, text, ", ".join(sorted({t for t, _ in senders})), advance, FC.LINE_BOX[1]))
        if name == "greg" and clear < least:
            failures.append("greg %s (%s): %.1f px from Arch's lit stroke, under %g" % (
                text, ", ".join(sorted({t for t, _ in senders})), clear, least))

    for name, title in (("greg", "The Gregorian date"), ("count", "The countdown")):
        rows = [(tag, ds, s) for (n, tag, ds), s in stats.items() if n == name]
        print("%s on Arch, Ring and Sun: %d forms in %d (tag, digit set) pairs; the widest of each, of the %d px box%s:" % (
            title, sum(s[2] for _, _, s in rows), len(rows), FC.LINE_BOX[1],
            ", and its least room to Arch's lit stroke (min=%g)" % least if name == "greg" else
            ", and the least room to Arch's lit feet (not a check: the box is the countdown's bound)"))
        order = list(locales)
        for tag, ds, (widest, nearest, n) in sorted(rows, key=lambda r: (order.index(r[0]), locales[r[0]]["digitSets"].index(r[1]))):
            print("  %-6s %-21s %5d  widest %6.1f px (%s)  nearest %5.1f px (%s)" % (
                tag, set_name(ds), n, widest[0], widest[1], nearest[0], nearest[1]))
        worst_w = max(rows, key=lambda r: r[2][0])
        print("  widest of all: %s (%s, %s), %.1f px, %.1f px under the box" % (
            worst_w[2][0][1], worst_w[0], set_name(worst_w[1]), worst_w[2][0][0], FC.LINE_BOX[1] - worst_w[2][0][0]))
        worst_c = min(rows, key=lambda r: r[2][1])
        print("  nearest Arch's lit stroke: %s (%s, %s), %.1f px" % (
            worst_c[2][1][1], worst_c[0], set_name(worst_c[1]), worst_c[2][1][0]))
    print("Arch's right-to-left date by its step (ARCH_GREGORIAN_STEPS), its widest form and its least room to the lit stroke:")
    for st in steps:
        n, widest, nearest = by_step[st.name]
        print("  %-8s %-20s %5.2f px  %6d forms  widest %6.1f px (%s)  nearest %5.1f px (%s)" % (
            st.name, st.says(), st.line.size, n, widest[0], widest[1], nearest[0], nearest[1]))
    print("(%d dates drawn row by row, their ink's box having come within %g px of the lit stroke)" % (exact_count, least + 0.5))

    if failures:
        print("%d do not fit:" % len(failures))
        for f in failures[:40]:
            print("  " + f)
        return 1
    print("every form fits: drawn whole in its box, %d px or more under it, and the date %g px or more from Arch's lit stroke" % (GUARD, least))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
