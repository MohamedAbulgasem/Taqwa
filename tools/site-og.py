#!/usr/bin/env python3
"""Renders the website's share cards: one 1200 x 630 image per language, shown when a page of
taqwa.world is shared in WhatsApp, Telegram, X, Facebook or iMessage.

    python3 tools/site-og.py            # writes site/assets/og/<lang>.jpg for every language
    python3 tools/site-og.py ar ur      # only these

Each card is that language's own hero, read from site/pages/<lang>/home.html, so a card never
says something the page does not: the headline, the lede and the light Prayer screenshot, laid
out right to left for Arabic and Urdu. Re-run it whenever a hero changes.

Needs Google Chrome (headless) and macOS's sips for the JPEG step; the fonts come from Google
Fonts, as on the site, so it needs the network.
"""
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, "site")
OUT = os.path.join(SITE, "assets", "og")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
LANGUAGES = ["en", "ar", "fr", "tr", "id", "ur", "bn"]
WIDTH, HEIGHT = 1200, 630

# The app's mihrab mark, as on the site's hero tile.
MARK = (
    '<svg viewBox="0 0 1024 1024" fill="none"><g transform="translate(512 512) scale(0.78) '
    'translate(-512 -512)"><path d="M292 780V520c0-148 88-252 220-304 132 52 220 156 220 304v260" '
    'stroke="#16160F" stroke-width="88" stroke-linecap="round" stroke-linejoin="round"/>'
    '<circle cx="512" cy="392" r="46" fill="#E3A21C"/></g></svg>'
)

CARD = """<!doctype html>
<html lang="{lang}" dir="{dir}">
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Manrope:wght@600;700;800&display=block">
<style>
  html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; background: #FBFAF7; }}
  .card {{
    box-sizing: border-box; width: {w}px; height: {h}px; padding: 0 76px;
    display: flex; align-items: center; gap: 64px; color: #16160F;
    font-family: "Manrope", system-ui, -apple-system, sans-serif;
  }}
  .copy {{ flex: 1; display: flex; flex-direction: column; gap: 22px; }}
  .brand {{ display: flex; align-items: center; gap: 18px; }}
  .tile {{
    width: 84px; height: 84px; border-radius: 22px; background: #FFFFFF;
    border: 2px solid #E7E5DD; display: grid; place-items: center;
  }}
  .tile svg {{ width: 66px; height: 66px; }}
  .name {{ font-family: "Manrope", sans-serif; font-size: 44px; font-weight: 800; letter-spacing: -0.02em; }}
  h1 {{ margin: 0; font-size: 58px; line-height: 1.1; font-weight: 800; letter-spacing: -0.025em; text-wrap: balance; }}
  .lede {{ margin: 0; font-size: 27px; line-height: 1.42; color: #6F6E62; max-width: 30ch; }}
  .lede strong {{ color: #16160F; font-weight: 700; }}
  /* Shrunk to its own width and placed at the start of the column, so in Arabic and Urdu it
     lines up with the text on the right even though the address itself runs left to right. */
  .url {{ align-self: flex-start; font-family: "Manrope", sans-serif; font-size: 25px; font-weight: 700; color: #B5820B; margin-top: 6px; }}
  .phone {{ flex: none; width: 292px; height: {h}px; position: relative; }}
  .phone img {{
    position: absolute; top: 62px; inset-inline-start: 0; width: 292px;
    border-radius: 34px; border: 2px solid #E7E5DD; box-shadow: 0 26px 60px rgba(22, 22, 15, 0.12);
  }}
  /* Arabic, Urdu and Bengali take the site's own face for those scripts, without Latin tracking. */
  html[lang="ar"] .copy, html[lang="ur"] .copy, html[lang="bn"] .copy {{
    font-family: system-ui, -apple-system, "Segoe UI", "Noto Naskh Arabic", sans-serif;
  }}
  html[lang="ar"] h1, html[lang="ur"] h1, html[lang="bn"] h1 {{ letter-spacing: 0; line-height: 1.3; }}
  html[lang="ur"] h1 {{ font-size: 54px; line-height: 1.5; }}
  html[lang="ur"] .lede {{ line-height: 1.8; }}
</style>
</head>
<body>
  <div class="card">
    <div class="copy">
      <div class="brand"><div class="tile">{mark}</div><span class="name" lang="en" dir="ltr">Taqwa</span></div>
      <h1>{h1}</h1>
      <p class="lede">{lede}</p>
      <div class="url" lang="en" dir="ltr">taqwa.world</div>
    </div>
    <div class="phone"><img src="{shot}" alt=""></div>
  </div>
</body>
</html>
"""


def hero(lang: str) -> dict:
    """The headline, the lede (keeping only its <strong>) and the light hero screenshot."""
    with open(os.path.join(SITE, "pages", lang, "home.html"), encoding="utf-8") as f:
        page = f.read()
    section = re.search(r'<section class="wrap hero">(.*?)</section>', page, re.S).group(1)
    h1 = html.unescape(re.sub(r"<[^>]+>", "", re.search(r"<h1[^>]*>(.*?)</h1>", section, re.S).group(1))).strip()
    lede = re.search(r'<p class="lede">(.*?)</p>', section, re.S).group(1)
    lede = re.sub(r"<(?!/?strong>)[^>]+>", "", lede).strip()
    shot = re.search(r'<img src="\{root\}(assets/img/[\w.-]+)"', section).group(1)
    return {"h1": html.escape(h1), "lede": lede, "shot": os.path.join(SITE, shot)}


def render(lang: str) -> str:
    with open(os.path.join(SITE, "pages", lang, "meta.json"), encoding="utf-8") as f:
        meta = json.load(f)
    parts = hero(lang)
    card = CARD.format(
        lang=lang, dir=meta["dir"], w=WIDTH, h=HEIGHT, mark=MARK,
        h1=parts["h1"], lede=parts["lede"], shot="file://" + parts["shot"],
    )
    work = tempfile.mkdtemp(prefix=f"og-{lang}-")
    page = os.path.join(work, "card.html")
    png = os.path.join(work, "card.png")
    with open(page, "w", encoding="utf-8") as f:
        f.write(card)
    # Headless Chrome writes the screenshot and then, on macOS, often fails to exit: wait for the
    # file, give it a moment to finish writing, then stop it.
    chrome = subprocess.Popen(
        [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
         "--allow-file-access-from-files", "--virtual-time-budget=6000",
         f"--window-size={WIDTH},{HEIGHT}", f"--screenshot={png}", "file://" + page],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    for _ in range(120):
        if os.path.exists(png) and os.path.getsize(png) > 0:
            time.sleep(1)
            break
        time.sleep(0.25)
    chrome.kill()
    if not os.path.exists(png):
        sys.exit(f"{lang}: Chrome wrote no screenshot")
    os.makedirs(OUT, exist_ok=True)
    jpg = os.path.join(OUT, f"{lang}.jpg")
    subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "86", png, "--out", jpg],
                   check=True, stdout=subprocess.DEVNULL)
    return jpg


if __name__ == "__main__":
    wanted = sys.argv[1:] or LANGUAGES
    for lang in wanted:
        path = render(lang)
        print(f"{lang}: {os.path.relpath(path, ROOT)} ({os.path.getsize(path) // 1024} KB)")
