"""Renders a mockup of the Taqwa face from faces.js, the drawings the face is built to, at the Wear
emulator's 454 x 454 pixels with headless Chrome, the face only (no watch case), in the fonts the
face bundles. It is what a screenshot from shoot.sh is compared with.

    python3 -I tools/watchface/ref.py <out.png> [face=arch] [t=14:58] [aod] [accent=amber]
                                      [lang=en|ar|ar-SA] [clock=24|12] [comps] [sec=36]
                                      [hijri=...] [v4=82] [v5=6,410] [hands=filled|outline]
                                      [window=black|filled]
    python3 -I tools/watchface/ref.py <out.png> screen=g-today|g-tasbeeh|g-tasbeeh-list|g-qibla|g-tile
                                      [t=14:58] [accent=amber] [count=27] [round=1] [heading=340]
                                      [flash=part|set] [rtl] [textmask=<mask.png>]

screen draws one of the watch app's screens from faces.js's SCREENS instead of a face, as app.sh
shoots them (plan 3 Task 4): count and round are the counter's (g-tasbeeh; 27 and 1 by default),
flash its part's or set's flash, heading the Qibla dial's (340), t the moment of g-today and g-tile.
The screens are drawn in English and on a 24-hour clock, as faces.js has them, so a shot in
another language or clock is compared by its geometry, with the words masked (erratum p3 #3):
textmask writes a second picture, white over each line of the drawing's words and the room another
language's words may take on its rows (a line set from its start or end, two and a half times its
width inwards; one centred off the screen's middle, as the Qibla dial's N, E, S and W, two and a
half times its width about its own centre; one centred on the middle, across the screen; that room
stops inside the screen's ring or dial, so the ring, the dial's ticks and its marker are compared;
3 px more each way, and 35% of its size more above and below for Arabic's marks), which
compare.py's mask= leaves out of its count; the words themselves are read from the watch's
accessibility tree (uiautomator dump). rtl draws g-today's ring anticlockwise, as Today draws it
for a right-to-left feed (the phone's RingArc); faces.js's own gToday has no direction. A screen
whose whole layout mirrors in right to left (the Tile's rows) is compared with compare.py's mirror.

face is arch, ring, sun, moon or moon-d; t is a time on the mock day (HH:MM) or faces.js minutes
(898); sec is the second the Moon's hands are drawn at (faces.js draws 8 when it is not given), so
that a screenshot taken at 10:10:36 is compared with the drawing at 10:10:36.

moon-d is the Moon of option (d), Mohamed's choice of 23 October: faces.js's moonFace with its lower
half redrawn when a wearer's slot is filled, from the reference page beside faces.js
(moon-slot-choice.html, whose own script draws it). With comps it draws the two slots along the
bottom, with v4 and v5 as their values (faces.js's 82% and 6,410 by default; the watch's battery
source writes 82) and hijri as the Hijri date.

hands is the Moon's always-on hands. By default they are Ruling 41's (Mohamed, 24 October), as the
face draws them: the awake hands, faceted with their black keyline, their facets at face-moon.py's
A_HANDS and the cap's ink with them. faces.js and the reference page still draw the approved
drawing's outlines at 85%, which hands=outline gives; the drawings themselves are not changed, so the
two forms are made here from the same hand() of each.

window is the Moon's moon window always-on. By default its near-black fill is black, as the face
draws it under Ruling 46 (the Lead, 24 October: lit by the measure, the fill was 2 points of the
Moon's always-on share), with its hairline kept; window=filled draws the approved drawing's #070908.
Awake the window is the drawing's either way.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
MANROPE = os.path.join(REPO, "shared", "src", "commonMain", "composeResources", "font")
NOTO = os.path.join(REPO, "watchface", "src", "main", "res", "font")
FACES = os.path.join(REPO, "docs", "superpowers", "specs", "2026-10-19-watch-companion", "faces.js")
OPTION_D = os.path.join(REPO, "docs", "superpowers", "specs", "2026-10-19-watch-companion", "moon-slot-choice.html")
FACE_MOON = os.path.join(REPO, "tools", "face-moon.py")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# The drawings' always-on hands and cap, in faces.js's moonFace and the reference page's (d), and
# Ruling 41's in their place: the awake hand on its black (the whole diamond and its 1.4 keyline),
# its two facets at the always-on level; the cap's ink at the same level over its black disc.
OUTLINE_HAND = ("""return g + '<path d="' + whole + '" fill="none" stroke="' + C.ink + '" stroke-opacity=".85" """
                """stroke-width="1" stroke-linejoin="round"/></g>';""")
FILLED_HAND = ("""return g + '<path d="' + whole + '" fill="#000" stroke="#000" stroke-width="1.4" stroke-linejoin="round"/>' + """
               """'<g opacity="%s"><path d="' + left + '" fill="#F4F5F2"/><path d="' + right + '" fill="#A9AFAB"/></g></g>';""")
OUTLINE_CAP = "q.aod ? ring(100, 100, 4.4, C.ink, 1, 0.85) : ''"
FILLED_CAP = "q.aod ? dot(100, 100, 4.4, C.ink, %s) : ''"
# The moon window's fill in faces.js's moonFace and the reference page's (d), and Ruling 46's: black
# always-on, the hairline (the rest of the same rect) unchanged.
FILLED_WINDOW = """rx="13.5" fill="#070908" stroke="' + C.hair"""
BLACK_WINDOW = """rx="13.5" fill="' + (q.aod ? '#000000' : '#070908') + '" stroke="' + C.hair"""


def hands_level():
    """face-moon.py's A_HANDS, as an opacity, so that the drawing and the face cannot disagree."""
    with open(FACE_MOON, encoding="utf-8") as f:
        return int(re.search(r"^A_HANDS = (\d+)$", f.read(), re.M).group(1)) / 255


def filled_hands(script):
    """The script with Ruling 41's always-on hands and cap in place of the drawing's outlines."""
    level = "%.4f" % hands_level()
    for old, new in ((OUTLINE_HAND, FILLED_HAND % level), (OUTLINE_CAP, FILLED_CAP % level)):
        if script.count(old) != 1:
            sys.exit("ref.py: the drawing's always-on hands are not where ref.py expects them")
        script = script.replace(old, new)
    return script


def black_window(script):
    """The script with Ruling 46's moon window, its fill black always-on, in place of the drawing's."""
    if script.count(FILLED_WINDOW) != 1:
        sys.exit("ref.py: the drawing's moon window is not where ref.py expects it")
    return script.replace(FILLED_WINDOW, BLACK_WINDOW)


# g-today's ring in faces.js, and the same ring given the drawing's direction (appRing's own rtl).
TODAY_RING = "appRing(100, 100, 90, s.progress, A, 7.4)"
TODAY_RING_RTL = "appRing(100, 100, 90, s.progress, A, 7.4, o.rtl)"
SCREENS = ("g-today", "g-tasbeeh", "g-tasbeeh-list", "g-qibla", "g-tile")


def ring_direction(script):
    """The script with g-today's ring drawn in the direction its options give (rtl)."""
    if script.count(TODAY_RING) != 1:
        sys.exit("ref.py: g-today's ring is not where ref.py expects it")
    return script.replace(TODAY_RING, TODAY_RING_RTL)


# The inner edge of each screen's ring or dial, in units from the middle, from faces.js's own figures,
# which must be there as written: no line's room reaches past it, so the ring is always compared.
# Today's ring is 7.4 wide at 90. The counter's widest stroke is the set's flash, 14 wide at 92. The
# dial's innermost marks are its Kaaba marker's halo, 0.95 of the 11-unit marker round a point on
# the 92 rim, and its ticks, from 9 units inside the rim; the dial is drawn 1.05 times over. The
# list and the Tile have no ring: their room stops at the screen's edge.
INSIDE = {
    "g-today": (("appRing(100, 100, 90, s.progress, A, 7.4)",), 90 - 7.4 / 2),
    "g-tasbeeh": (("appRing(100, 100, 92, count / tot, A, 5.6,", "ring(100, 100, 92, A, o.flash === 'set' ? 14 : 10, 0.22)"),
                  92 - 14 / 2),
    "g-qibla": (("qiblaDial(100, 100, 92, heading, 23, A, 1.05)", "radial(cx, cy, r - 9 * k, r - 5 * k,",
                 "cube(m[0], m[1], f(11 * k), A, true)", "dot(x, y, size * 0.95, A, 0.22)"),
                92 - max(9, 0.95 * 11) * 1.05),
}


# Half a unit, a pixel at 454 px: the ring's soft inner edge is compared with the ring.
EDGE = 0.5


def inside(screen, script):
    """How far from the middle a line's room may reach on this screen: inside its ring, or the screen."""
    if screen not in INSIDE:
        return 100
    figures, radius = INSIDE[screen]
    if any(script.count(s) != 1 for s in figures):
        sys.exit("ref.py: %s's ring is not where ref.py expects it" % screen)
    return radius - EDGE


# Run in the page after the drawing, for textmask: every line of words becomes white, the room its
# words may take in any of the seven languages, and everything else is taken away. A line's rows are
# its own, 3 px more each way at 454 px (1.32 units) and 35% of its size more above and below for
# the marks over and under Arabic letters. Across, a line set from its start or its end (the Tile's
# names and times) runs from there two and a half times its English width inwards, so that what sits
# beside it (a row's pip) is still compared; a line centred anywhere but the screen's middle (the
# Qibla dial's N, E, S and W) runs two and a half times its width about its own centre; a line
# centred on the middle, which LineText may set as wide as the chord, runs across the screen. That
# room stops at the inner edge of the screen's ring (INSIDE), so the ring, the dial's ticks and its
# marker stay compared, wherever a line lies; the line's own box is white wherever it is.
MASK_SCRIPT = """
var s = document.getElementById('s'), room = [], own = [];
function box(x0, y0, x1, y1) {
  return '<rect x="' + x0 + '" y="' + y0 + '" width="' + (x1 - x0) + '" height="' + (y1 - y0) + '" fill="#fff"/>';
}
s.querySelectorAll('text').forEach(function (t) {
  var b = t.getBBox(), pad = 1.32, mid = b.x + b.width / 2, x = parseFloat(t.getAttribute('x'));
  var size = Math.max.apply(null, [t].concat([].slice.call(t.querySelectorAll('tspan'))).map(function (e) {
    return parseFloat(e.getAttribute('font-size')) || 0;
  })) || 10;
  var high = pad + 0.35 * size, y0 = b.y - high, y1 = b.y + b.height + high, anchor = t.getAttribute('text-anchor') || 'start', x0, x1;
  if (anchor === 'start') { x0 = b.x; x1 = b.x + 2.5 * b.width; }
  else if (anchor === 'end') { x0 = b.x + b.width - 2.5 * b.width; x1 = b.x + b.width; }
  else if (Math.abs((isNaN(x) ? mid : x) - 100) < 0.5) { x0 = 0; x1 = 200; }
  else { x0 = mid - 1.25 * b.width; x1 = mid + 1.25 * b.width; }
  room.push(box(Math.max(x0 - pad, 0), y0, Math.min(x1 + pad, 200), y1));
  own.push(box(b.x - pad, y0, b.x + b.width + pad, y1));
});
s.innerHTML = '<clipPath id="tw-inside"><circle cx="100" cy="100" r="%(inside)s"/></clipPath>' +
  '<g clip-path="url(#tw-inside)">' + room.join('') + '</g>' + own.join('');
"""


def option_d():
    """The reference page's own script for option (d), which adds TW.FACES['moon-d'] to faces.js."""
    with open(OPTION_D, encoding="utf-8") as f:
        page = f.read()
    return next(s for s in re.findall(r"<script>(.*?)</script>", page, re.S) if "var TWD" in s)


def main():
    if len(sys.argv) < 2 or sys.argv[1].startswith("-"):
        sys.exit(__doc__)
    out = os.path.abspath(sys.argv[1])
    o = {"face": "arch", "t": "898", "accent": "amber", "lang": "en", "clock": "24", "aod": False, "comps": False, "sec": None,
         "hijri": None, "v4": None, "v5": None, "hands": "filled", "window": "black",
         "screen": None, "count": None, "round": None, "heading": None, "flash": None, "rtl": False, "textmask": None}
    for a in sys.argv[2:]:
        if a in ("aod", "comps", "rtl"):
            o[a] = True
        elif "=" in a and a.split("=", 1)[0] in o:
            k, v = a.split("=", 1)
            o[k] = v
        else:
            sys.exit("unknown option %r\n%s" % (a, __doc__))
    if o["hands"] not in ("filled", "outline"):
        sys.exit("hands is filled or outline\n%s" % __doc__)
    if o["window"] not in ("black", "filled"):
        sys.exit("window is black or filled\n%s" % __doc__)
    if o["screen"] is not None and o["screen"] not in SCREENS:
        sys.exit("screen is one of %s\n%s" % (", ".join(SCREENS), __doc__))
    if o["flash"] not in (None, "part", "set"):
        sys.exit("flash is part or set\n%s" % __doc__)
    if o["screen"] is None and any(o[k] for k in ("count", "round", "heading", "flash", "rtl", "textmask")):
        sys.exit("count, round, heading, flash, rtl and textmask are a screen's options\n%s" % __doc__)
    if o["rtl"] and o["screen"] != "g-today":
        sys.exit("rtl is g-today's\n%s" % __doc__)
    as_built = [f for f, on in ((filled_hands, o["hands"] == "filled"), (black_window, o["window"] == "black")) if on]

    if o["screen"] is not None:
        as_built = [ring_direction] if o["screen"] == "g-today" else []

    def face_forms(script):
        """The drawing's script with the face's always-on forms the options ask for (Rulings 41 and 46)."""
        for form in as_built:
            script = form(script)
        return script
    t = o["t"]
    if ":" in t:
        h, m = t.split(":")[:2]
        t = str(int(h) * 60 + int(m))
    tmp = tempfile.TemporaryDirectory()
    faces = os.path.join(tmp.name, "faces.js")
    with open(FACES, encoding="utf-8") as f, open(faces, "w", encoding="utf-8") as out_js:
        drawing = f.read()
        out_js.write(face_forms(drawing))
    mask_script = MASK_SCRIPT % {"inside": "%g" % inside(o["screen"], drawing)} if o["textmask"] is not None else ""
    page = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face { font-family: Manrope; font-weight: 300; src: url('file://%(m)s/Manrope-Light.ttf'); }
@font-face { font-family: Manrope; font-weight: 400; src: url('file://%(m)s/Manrope-Regular.ttf'); }
@font-face { font-family: Manrope; font-weight: 600; src: url('file://%(m)s/Manrope-SemiBold.ttf'); }
@font-face { font-family: Manrope; font-weight: 800; src: url('file://%(m)s/Manrope-ExtraBold.ttf'); }
@font-face { font-family: 'Noto Sans Arabic'; font-weight: 300; src: url('file://%(n)s/noto_sans_arabic_subset_v2_light.ttf'); }
@font-face { font-family: 'Noto Sans Arabic'; font-weight: 600; src: url('file://%(n)s/noto_sans_arabic_subset_v2_semibold.ttf'); }
@font-face { font-family: 'Noto Sans Arabic'; font-weight: 700; src: url('file://%(n)s/noto_sans_arabic_subset_v2_bold.ttf'); }
html, body { margin: 0; background: #000; overflow: hidden; }
.tw-tnum { font-variant-numeric: tabular-nums; font-feature-settings: "tnum"; }
.tw-pnum { font-variant-numeric: proportional-nums; font-feature-settings: "pnum"; }
svg { display: block; }
</style></head><body>
<svg id="s" width="454" height="454" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg"></svg>
<script src="file://%(faces)s"></script>
<script>%(option_d)s</script>
<script>
TW.mount();
document.getElementById('s').innerHTML = '<g clip-path="url(#tw-gclip)">' + %(draw)s + '</g>';
%(mask)s
</script></body></html>"""
    if o["screen"] is None:
        draw = "TW.FACES['%s']({t: %s, lang: '%s', accent: '%s', aod: %s, comps: %s, clock: '%s', sec: %s, %s})" % (
            o["face"], int(t), o["lang"], o["accent"], "true" if o["aod"] else "false", "true" if o["comps"] else "false",
            o["clock"], "undefined" if o["sec"] is None else float(o["sec"]),
            ", ".join("%s: %s" % (k, json.dumps(o[k])) for k in ("hijri", "v4", "v5") if o[k] is not None))
    else:
        screen = {"t": int(t), "accent": o["accent"], "rtl": o["rtl"]}
        for k in ("count", "round", "heading"):
            if o[k] is not None:
                screen[k] = float(o[k])
        if o["flash"] is not None:
            screen["flash"] = o["flash"]
        draw = "TW.SCREENS['%s'](%s)" % (o["screen"], json.dumps(screen))

    def render(path, mask):
        html = page % {"m": MANROPE, "n": NOTO, "faces": faces, "draw": draw, "mask": mask_script if mask else "",
                       "option_d": face_forms(option_d()) if o["face"] == "moon-d" and o["screen"] is None else ""}
        source = os.path.join(tmp.name, "mask.html" if mask else "ref.html")
        with open(source, "w") as f:
            f.write(html)
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--user-data-dir=" + os.path.join(tmp.name, "profile-mask" if mask else "profile"), "--window-size=454,454",
                        "--virtual-time-budget=4000", "--screenshot=" + path, "file://" + source],
                       check=True, capture_output=True)
        print("wrote", path)

    with tmp:
        render(out, mask=False)
        if o["textmask"] is not None:
            render(os.path.abspath(o["textmask"]), mask=True)


if __name__ == "__main__":
    main()
