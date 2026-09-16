#!/usr/bin/env python3
"""Frames raw phone captures into Play's 1080x1920 slot with a caption above the screen.

    frame.py <lang> <in dir> <out dir> [WxH]   (default 1080x1920 for Play; 1320x2868 for the App Store)

Captions come from docs/store/listing/<lang>.md ("## Screenshot captions", eight numbered lines).
Parchment background in the app's palette, the caption in the app's type (Manrope for Latin,
the system faces for Arabic, Urdu and Bengali, shaped by raqm), the screen in a rounded frame
and its whole screen."""
import glob, os, re, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1080, 1920  # overridden by the optional WxH argument
BG = (250, 248, 243); INK = (26, 24, 20); ACCENT = (176, 122, 18); HAIR = (214, 208, 196)
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIRS = ["/System/Library/Fonts", "/System/Library/Fonts/Supplemental", "/Library/Fonts"]
PREFERRED = {
    "ar": ["GeezaPro.ttc", "SFArabic.ttf", "Damascus.ttc"],
    "ur": ["NotoNastaliq.ttc", "GeezaPro.ttc"],
    "bn": ["KohinoorBangla.ttc", "Bangla Sangam MN.ttc", "Bangla MN.ttc"],
}
RTL = {"ar", "ur"}

def font_path(lang):
    names = PREFERRED.get(lang, [])
    for name in names:
        for d in FONT_DIRS:
            p = os.path.join(d, name)
            if os.path.exists(p): return p
    return os.path.join(ROOT, "shared/src/commonMain/composeResources/font/Manrope-SemiBold.ttf")

def captions(lang):
    text = open(os.path.join(ROOT, f"docs/store/listing/{lang}.md"), encoding="utf-8").read()
    block = text.split("## Screenshot captions", 1)[1].split("\n## ", 1)[0]
    found = {}
    for line in block.split("\n"):
        m = re.match(r"^\s*(\d+)\.\s+(.*\S)\s*$", line)
        if m: found[int(m.group(1))] = m.group(2)
    return found

def frame(lang, caption, src, dst):
    shot = Image.open(src).convert("RGB")
    canvas = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(canvas)
    direction = "rtl" if lang in RTL else "ltr"
    size = int((66 if lang in ("ur",) else 60) * W / 1080)
    path = font_path(lang)
    def font(sz):
        return ImageFont.truetype(path, sz, layout_engine=ImageFont.Layout.RAQM)
    f = font(size)
    def width(t, ft): return draw.textlength(t, font=ft, direction=direction)
    lines = [caption]
    while width(lines[0], f) > W - 120 and len(lines) == 1:
        words = caption.split()
        if len(words) < 2: break
        # Split at the point that balances the two lines.
        best = min(range(1, len(words)), key=lambda i: abs(width(" ".join(words[:i]), f) - width(" ".join(words[i:]), f)))
        lines = [" ".join(words[:best]), " ".join(words[best:])]
    line_h = int(size * (1.75 if lang == "ur" else 1.35))
    y = int((112 if len(lines) == 1 else 84) * H / 1920)
    for line in lines:
        w = width(line, f)
        draw.text(((W - w) / 2, y), line, font=f, fill=INK, direction=direction)
        y += line_h
    draw.rounded_rectangle([W / 2 - 34, y + 22, W / 2 + 34, y + 28], radius=3, fill=ACCENT)
    top = y + 82
    # The whole screen, bar and tab row included: the recitation and Prayer shots need their
    # bottom edge, so nothing runs off the frame.
    scale = min((W - 176) / shot.width, (H - top - 64) / shot.height)
    sw, sh = int(shot.width * scale), int(shot.height * scale)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x = (W - sw) // 2
    radius = 64
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x, top + 22, x + sw, top + sh + 22], radius=radius, fill=(0, 0, 0, 80))
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    canvas.paste(shadow, (0, 0), shadow)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw, sh], radius=radius, fill=255)
    canvas.paste(shot, (x, top), mask)
    ImageDraw.Draw(canvas).rounded_rectangle([x, top, x + sw, top + sh], radius=radius, outline=HAIR, width=3)
    canvas.save(dst, "PNG", optimize=True)

if __name__ == "__main__":
    lang, indir, outdir = sys.argv[1:4]
    if len(sys.argv) > 4:
        W, H = map(int, sys.argv[4].lower().split("x"))
    caps = captions(lang)
    os.makedirs(outdir, exist_ok=True)
    for src in sorted(glob.glob(os.path.join(indir, "*.png"))):
        n = int(os.path.basename(src).split("-")[0])
        frame(lang, caps[n], src, os.path.join(outdir, f"{lang}-{n:02d}.png"))
    print(lang, "framed", len(caps), "→", outdir)
