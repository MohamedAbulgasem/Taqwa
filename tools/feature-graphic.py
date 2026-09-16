#!/usr/bin/env python3
"""The Play Store feature graphic (1024 x 500), English and Arabic, from the app's own icon,
palette and type: assets/store/feature-graphic-{en,ar}.png.

Play shows it at the top of the listing and behind the video button, so the middle stays quiet
and nothing sits near the edges. Requires Pillow with libraqm (for Arabic shaping); the Arabic
wordmark is set in the mushaf typeface the app reads the Quran in, the tagline in the system's
Geeza Pro, which is what an Arabic iPhone shows the app's own UI in.

    python3 tools/feature-graphic.py
"""
import os

from PIL import Image, ImageDraw, ImageFont, features

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.join(ROOT, "shared/src/commonMain/composeResources/font")
ICON = os.path.join(ROOT, "androidApp/src/androidMain/ic_launcher-playstore.png")
OUT = os.path.join(ROOT, "assets/store")

W, H = 1024, 500
S = 4  # supersample, for smooth arcs and corners
# Palette.kt, light theme.
BG, INK, SECONDARY, HAIRLINE, AMBER, RING = (
    (0xFB, 0xFA, 0xF7), (0x16, 0x16, 0x0F), (0x6F, 0x6E, 0x62), (0xE7, 0xE5, 0xDD), (0xB5, 0x82, 0x0B), (0xE3, 0xA2, 0x1C),
)
MANROPE_XB = os.path.join(FONTS, "Manrope-ExtraBold.ttf")
MANROPE_R = os.path.join(FONTS, "Manrope-Regular.ttf")
MANROPE_SB = os.path.join(FONTS, "Manrope-SemiBold.ttf")
UTHMANIC = os.path.join(FONTS, "uthmanic_hafs.ttf")
ARABIC_UI = next(
    (p for p in ("/System/Library/Fonts/GeezaPro.ttc", "/System/Library/Fonts/Supplemental/GeezaPro.ttc") if os.path.exists(p)),
    None,
)


def icon_tile(size):
    src = Image.open(ICON).convert("RGBA").resize((size * S, size * S), Image.LANCZOS)
    radius = int(size * S * 0.22)
    mask = Image.new("L", src.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, src.width - 1, src.height - 1], radius=radius, fill=255)
    tile = Image.new("RGBA", src.size, (0, 0, 0, 0))
    tile.paste(src, (0, 0), mask)
    # The hairline every card in the app has.
    ImageDraw.Draw(tile).rounded_rectangle([0, 0, src.width - 1, src.height - 1], radius=radius, outline=HAIRLINE + (255,), width=S)
    return tile


def ring(cx, cy, r, rtl):
    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S]
    d.ellipse(box, outline=HAIRLINE + (255,), width=9 * S)
    # The countdown ring's amber sweep from the top, mirrored for Arabic.
    d.arc(box, start=180 if rtl else 270, end=270 if rtl else 360, fill=RING + (255,), width=9 * S)
    return layer


def spaced(d, xy, text, font, fill, spacing):
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=font, fill=fill, anchor="lm")
        x += d.textlength(ch, font=font) + spacing


def dotted_rtl(d, right, baseline, phrases, font, fill, gap=14, dot=2.5):
    """Phrases laid out right-to-left with a drawn dot between them: Geeza Pro has no middle
    dot, and a missing glyph in a store graphic is exactly the kind of thing a reviewer sees."""
    x = right
    for i, phrase in enumerate(phrases):
        if i:
            cx = x - gap
            d.ellipse([cx - dot, baseline - 9 - dot, cx + dot, baseline - 9 + dot], fill=fill)
            x = cx - gap
        d.text((x, baseline), phrase, font=font, fill=fill, anchor="rs", direction="rtl")
        x -= d.textlength(phrase, font=font, direction="rtl")


def render(lang):
    rtl = lang == "ar"
    img = Image.new("RGBA", (W * S, H * S), BG + (255,))
    icon, cy = 260, 250
    cx = W - 226 if rtl else 226
    img.alpha_composite(ring(cx, cy, 196, rtl))
    img.alpha_composite(icon_tile(icon), ((cx - icon // 2) * S, (cy - icon // 2) * S))
    out = img.resize((W, H), Image.LANCZOS)
    d = ImageDraw.Draw(out)
    if rtl:
        x = W - 488
        d.text((x, 206), "تقوى", font=ImageFont.truetype(UTHMANIC, 132), fill=INK, anchor="rs", direction="rtl")
        d.text((x, 280), "مواقيت الصلاة والقرآن والتلاوة.", font=ImageFont.truetype(ARABIC_UI, 32), fill=SECONDARY, anchor="rs", direction="rtl")
        dotted_rtl(d, x, 336, ["مجاني", "بلا إنترنت", "بلا إعلانات", "بلا تتبّع"], ImageFont.truetype(ARABIC_UI, 21), AMBER)
    else:
        x = 488
        d.text((x - 6, 196), "Taqwa", font=ImageFont.truetype(MANROPE_XB, 124), fill=INK, anchor="ls")
        d.text((x, 274), "Prayer times, Quran and recitation.", font=ImageFont.truetype(MANROPE_R, 32), fill=SECONDARY, anchor="ls")
        spaced(d, (x + 1, 334), "FREE · OFFLINE · NO ADS · NO TRACKING", ImageFont.truetype(MANROPE_SB, 20), AMBER, 3)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"feature-graphic-{lang}.png")
    out.convert("RGB").save(path, optimize=True)
    print(path, out.size)


if __name__ == "__main__":
    if not features.check("raqm"):
        raise SystemExit("Pillow was built without libraqm; Arabic would not be shaped.")
    if ARABIC_UI is None:
        raise SystemExit("Geeza Pro not found; this script expects macOS.")
    render("en")
    render("ar")
