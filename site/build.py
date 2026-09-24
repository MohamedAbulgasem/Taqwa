#!/usr/bin/env python3
"""Builds taqwa.world into _site/.

Seven languages from one set of templates. Each language is a directory under site/pages/<lang>/
holding a meta.json (titles, labels, direction, the policy file it uses) and the page bodies
(home.html, support.html); the privacy page is rendered from the Markdown policy at the
repository root (PRIVACY.md, PRIVACY.<lang>.md — the same text the app's About link and GitHub
show). Every page is wrapped in site/templates/base.html with that language's header and footer,
English lives at the root and every other language under /<lang>/, and every page links to its
twin in every other language through the language picker and the hreflang alternates.

It also writes sitemap.xml (every page with its language alternates), robots.txt, and the
404.html GitHub Pages serves for any address it has nothing for; each language's home page
carries JSON-LD for the app, and every page a share card from site/assets/og/ (drawn by
tools/site-og.py).

    python3 site/build.py            # writes _site/ next to the repo root
    python3 site/build.py --check    # builds, then fails on dangling links, missing assets,
                                     # broken structured data or a sitemap that disagrees

Needs the `markdown` package (pip install markdown); the Pages workflow installs it.
"""
import html
import json
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, "site")
OUT = os.path.join(ROOT, "_site")
ORIGIN = "https://taqwa.world"

SKIP = {"build.py", "templates", "pages", "README.md"}

# The order the picker lists them in: English first, then the app's own order.
LANGUAGE_ORDER = ["en", "ar", "fr", "tr", "id", "ur", "bn"]

# Output directory per page, relative to the language root ("" is the language's home).
PAGE_DIRS = {"home": "", "support": "support/", "privacy": "privacy/"}


def load_languages() -> dict:
    langs = {}
    for lang in LANGUAGE_ORDER:
        path = os.path.join(SITE, "pages", lang, "meta.json")
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            cfg = json.load(f)
        cfg["prefix"] = "" if lang == "en" else f"{lang}/"
        langs[lang] = cfg
    return langs


def copy_static() -> None:
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    for name in os.listdir(SITE):
        if name in SKIP or name.startswith("."):
            continue
        src = os.path.join(SITE, name)
        dst = os.path.join(OUT, name)
        if os.path.isdir(src):
            shutil.copytree(src, dst)
        else:
            shutil.copy2(src, dst)


def render_markdown(path: str) -> str:
    """A policy file as prose: the H1 is the page title, the italic line the meta line."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    title_match = re.match(r"# (.+)\n", text)
    title = title_match.group(1).strip() if title_match else ""
    text = text[title_match.end():] if title_match else text
    meta = ""
    meta_match = re.match(r"\s*_(.+?)_\s*\n", text)
    if meta_match:
        meta = meta_match.group(1).strip()
        text = text[meta_match.end():]
    body = markdown.markdown(text, extensions=["smarty"])
    # Bare domains and addresses in the policy become links; the file stays plain text for GitHub.
    body = re.sub(
        r"(?<![\w/@\"'])((?:github\.com|docs\.github\.com)/[\w./#?=-]+)",
        lambda m: f'<a href="https://{m.group(1)}">{m.group(1)}</a>',
        body,
    )
    body = re.sub(
        r"(?<![\w/\"'])([\w.+-]+@[\w-]+\.[\w.]+)",
        lambda m: f'<a href="mailto:{m.group(1)}">{m.group(1)}</a>',
        body,
    )
    page = f"<h1>{html.escape(title)}</h1>\n"
    if meta:
        page += f'<p class="meta">{html.escape(meta)}</p>\n'
    return f'<main class="wrap prose">\n{page}{body}\n</main>\n'


EMAIL_LINK = re.compile(r'<a href="mailto:[^"]*"[^>]*>[^<]*</a>')
EMAIL_OFF, EMAIL_ON = "<!--email_off-->", "<!--/email_off-->"


def shield_emails(doc: str) -> str:
    """Wrap every mailto link in Cloudflare's opt-out comments. The domain's DNS sits on
    Cloudflare, and any request that reaches its proxy edge gets "Email Address Obfuscation"
    applied: the address is swapped for "[email protected]" plus a script that undoes it, and a
    reader whose script never ran sees the placeholder. The comments tell the edge to leave the
    address alone, so the page reads the same whichever way it arrives."""
    return EMAIL_LINK.sub(lambda m: f"{EMAIL_OFF}{m.group(0)}{EMAIL_ON}", doc)


def unshielded_emails(doc: str) -> list:
    """Addresses that would still be obfuscated: any that sit outside an email_off block."""
    outside = re.sub(re.escape(EMAIL_OFF) + r".*?" + re.escape(EMAIL_ON), "", doc, flags=re.S)
    return re.findall(r"[\w.+-]+@[\w-]+\.[\w.]+", outside)


def page_body(langs: dict, lang: str, page: str) -> str:
    if page == "privacy":
        return render_markdown(os.path.join(ROOT, langs[lang]["policy_file"]))
    with open(os.path.join(SITE, "pages", lang, f"{page}.html"), encoding="utf-8") as f:
        return f.read()


# What the structured data says about the app. It is the same on every language's home page
# apart from the description and the screenshot. No rating is claimed: Google shows stars only
# from real reviews, and an invented one is a manual-penalty offence.
APP = {
    "operatingSystem": "Android 8.0 or later, iOS 16 or later",
    "applicationCategory": "LifestyleApplication",
    "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
    "isAccessibleForFree": True,
    "license": "https://www.gnu.org/licenses/gpl-3.0.html",
    "image": f"{ORIGIN}/assets/icon-512.png",
    "author": {"@type": "Person", "name": "Mohamed Abulgasem", "url": "https://mohamedabulgasem.github.io/"},
    "sameAs": ["https://github.com/MohamedAbulgasem/Taqwa"],
}


def structured_data(langs: dict, lang: str, page: str, raw_body: str) -> str:
    """JSON-LD for a language's home page: the app, and on the root page the site as well, so a
    search result can name the site "Taqwa" and tell it apart from the other apps of that name."""
    if page != "home":
        return ""
    cfg = langs[lang]
    app = {
        "@type": "MobileApplication",
        "@id": f"{ORIGIN}/#app",
        "name": "Taqwa",
        "description": cfg["pages"]["home"]["description"],
        "url": f"{ORIGIN}/{cfg['prefix']}",
        "inLanguage": list(langs),
        **APP,
    }
    if cfg["brand"] != "Taqwa":
        app["alternateName"] = cfg["brand"]
    shot = re.search(r'<section class="wrap hero">.*?<img src="\{root\}(assets/img/[\w.-]+)"', raw_body, re.S)
    if shot:
        app["screenshot"] = f"{ORIGIN}/{shot.group(1)}"
    graph = [app]
    if lang == "en":
        graph.insert(0, {
            "@type": "WebSite",
            "@id": f"{ORIGIN}/#website",
            "name": "Taqwa",
            "alternateName": ["taqwa.world", "Taqwa app"],
            "url": f"{ORIGIN}/",
            "inLanguage": list(langs),
        })
    data = json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False, separators=(",", ":"))
    data = data.replace("</", "<\\/")  # a literal "</" would close the script element early
    return f'  <script type="application/ld+json">{data}</script>\n'


def labels(cfg: dict) -> dict:
    """The header and footer words of one language."""
    return {key: cfg[key] for key in ("brand_label", "nav_label", "footer_label", "nav_privacy",
                                       "nav_support", "languages_label", "copyright")}


def render(template: str, values: dict) -> str:
    """Fills the template. The body goes in last, so nothing inside it is read as a placeholder."""
    out = template
    for key, value in values.items():
        if key != "body":
            out = out.replace("{" + key + "}", value)
    return shield_emails(out.replace("{body}", values["body"]))


def build_page(langs: dict, lang: str, page: str, template: str) -> None:
    cfg = langs[lang]
    out_dir = cfg["prefix"] + PAGE_DIRS[page]                 # e.g. "ar/support/"
    depth = out_dir.count("/")
    root = "../" * depth                                       # to the site root
    lang_root = "../" * PAGE_DIRS[page].count("/")             # to this language's home

    def twin(other: str) -> str:
        """This page in another language, relative to this one."""
        path = langs[other]["prefix"] + PAGE_DIRS[page]
        return (root + path) if path else (root or "./")

    links = {
        "root": root,
        "home": lang_root or "./",
        "privacy": lang_root + "privacy/" if page != "privacy" else "./",
        "support": lang_root + "support/" if page != "support" else "./",
    }
    # The picker: every language, the current one marked and not a link. Each entry carries its
    # own lang so the browser picks a face for it, and the endonym is what a reader looking for
    # their language recognises.
    picker = []
    for other, other_cfg in langs.items():
        if other == lang:
            picker.append(f'<span aria-current="true" lang="{other}">{other_cfg["endonym"]}</span>')
        else:
            picker.append(f'<a href="{twin(other)}" lang="{other}" hreflang="{other}">{other_cfg["endonym"]}</a>')
    alternates = "\n".join(
        f'  <link rel="alternate" hreflang="{other}" href="{ORIGIN}/{langs[other]["prefix"]}{PAGE_DIRS[page]}">'
        for other in langs
    ) + f'\n  <link rel="alternate" hreflang="x-default" href="{ORIGIN}/{PAGE_DIRS[page]}">'
    meta = cfg["pages"][page]
    canonical = f"{ORIGIN}/{out_dir}"
    raw_body = page_body(langs, lang, page)
    body = raw_body
    for key, value in links.items():
        body = body.replace("{" + key + "}", value)
    values = {
        "lang": lang,
        "dir": cfg["dir"],
        "title": meta["title"],
        "og_title": meta["og_title"],
        "description": meta["description"],
        "og_locale": cfg["og_locale"],
        "og_image": f"{ORIGIN}/assets/og/{lang}.jpg",
        "canonical": canonical,
        "link_tags": f'  <link rel="canonical" href="{canonical}">\n{alternates}',
        "robots": "",
        "structured_data": structured_data(langs, lang, page, raw_body),
        "language_links": "\n    ".join(picker),
        "privacy_current": ' aria-current="page"' if page == "privacy" else "",
        "support_current": ' aria-current="page"' if page == "support" else "",
        **labels(cfg),
        **links,
        "body": body,
    }
    target = os.path.join(OUT, out_dir)
    os.makedirs(target, exist_ok=True)
    with open(os.path.join(target, "index.html"), "w", encoding="utf-8") as f:
        f.write(render(template, values))


NOT_FOUND = """<main class="wrap prose">
  <h1>Page not found</h1>
  <p class="meta">There is no page at this address on taqwa.world.</p>
  <p>The link may be mistyped, or the page may have moved. The <a href="/">home page</a> has everything, and the list above has it in every language.</p>
</main>
"""


def build_404(langs: dict, template: str) -> None:
    """GitHub Pages answers every address it has nothing for with /404.html, at any depth, so
    every link here is absolute from the site root. It asks not to be indexed."""
    cfg = langs["en"]
    picker = [f'<a href="/{c["prefix"]}" lang="{lang}" hreflang="{lang}">{c["endonym"]}</a>' for lang, c in langs.items()]
    values = {
        "lang": "en",
        "dir": "ltr",
        "title": "Page not found · Taqwa",
        "og_title": "Taqwa",
        "description": "There is no page at this address on taqwa.world.",
        "og_locale": cfg["og_locale"],
        "og_image": f"{ORIGIN}/assets/og/en.jpg",
        "canonical": f"{ORIGIN}/",
        "link_tags": "",
        "robots": '  <meta name="robots" content="noindex">\n',
        "structured_data": "",
        "language_links": "\n    ".join(picker),
        "privacy_current": "",
        "support_current": "",
        **labels(cfg),
        "root": "/",
        "home": "/",
        "privacy": "/privacy/",
        "support": "/support/",
        "body": NOT_FOUND,
    }
    with open(os.path.join(OUT, "404.html"), "w", encoding="utf-8") as f:
        f.write(render(template, values))


def write_sitemap(langs: dict) -> None:
    """Every page, each listing its twins in the other languages: the same alternates the pages
    carry in their heads. No lastmod: Google only trusts one that is exact, and the build has no
    honest date to give."""
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ]
    for sub in PAGE_DIRS.values():
        for cfg in langs.values():
            lines.append("  <url>")
            lines.append(f"    <loc>{ORIGIN}/{cfg['prefix']}{sub}</loc>")
            for other, other_cfg in langs.items():
                lines.append(f'    <xhtml:link rel="alternate" hreflang="{other}" href="{ORIGIN}/{other_cfg["prefix"]}{sub}"/>')
            lines.append(f'    <xhtml:link rel="alternate" hreflang="x-default" href="{ORIGIN}/{sub}"/>')
            lines.append("  </url>")
    lines.append("</urlset>")
    with open(os.path.join(OUT, "sitemap.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def write_robots() -> None:
    with open(os.path.join(OUT, "robots.txt"), "w", encoding="utf-8") as f:
        f.write(f"User-agent: *\nAllow: /\n\nSitemap: {ORIGIN}/sitemap.xml\n")


def check_site() -> int:
    """Fails the build on: a relative or root-relative href/src that points at nothing, a
    template placeholder left in a page, an address Cloudflare would rewrite, an image of this
    site named by absolute address that does not exist, structured data that is not valid JSON,
    and a sitemap that disagrees with the pages built."""
    problems = 0

    def problem(message: str) -> None:
        nonlocal problems
        print(message, file=sys.stderr)
        problems += 1

    pages = []
    for dirpath, _, files in os.walk(OUT):
        for name in files:
            if not name.endswith(".html"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, OUT)
            if name == "index.html":
                pages.append(rel)
            with open(path, encoding="utf-8") as f:
                doc = f.read()
            for leftover in re.findall(r"\{[a-z_]+\}", doc):
                problem(f"placeholder: {rel} -> {leftover}")
            for address in unshielded_emails(doc):
                problem(f"unshielded email: {rel} -> {address}")
            for ref in re.findall(r'(?:href|src|srcset)="([^"#]+)"', doc):
                if re.match(r"^(https?:|mailto:|data:)", ref):
                    continue
                base = OUT if ref.startswith("/") else dirpath
                target = os.path.normpath(os.path.join(base, ref.lstrip("/")))
                if os.path.isdir(target):
                    target = os.path.join(target, "index.html")
                if not os.path.exists(target):
                    problem(f"dangling: {rel} -> {ref}")
            for ref in re.findall(re.escape(ORIGIN) + r'/(assets/[^"\s<]+)', doc):
                if not os.path.exists(os.path.join(OUT, ref)):
                    problem(f"missing asset: {rel} -> {ref}")
            for block in re.findall(r'<script type="application/ld\+json">(.*?)</script>', doc, re.S):
                try:
                    json.loads(block)
                except ValueError as error:
                    problem(f"structured data: {rel} -> {error}")

    ns = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    locs = [loc.text for loc in ET.parse(os.path.join(OUT, "sitemap.xml")).getroot().findall("s:url/s:loc", ns)]
    for loc in locs:
        if not os.path.exists(os.path.join(OUT, loc[len(ORIGIN) + 1:], "index.html")):
            problem(f"sitemap: no page for {loc}")
    if len(locs) != len(pages):
        problem(f"sitemap: {len(locs)} addresses for {len(pages)} pages")
    with open(os.path.join(OUT, "robots.txt"), encoding="utf-8") as f:
        if f"Sitemap: {ORIGIN}/sitemap.xml" not in f.read():
            problem("robots.txt: no Sitemap line")
    with open(os.path.join(OUT, "404.html"), encoding="utf-8") as f:
        if 'content="noindex"' not in f.read():
            problem("404.html: not marked noindex")
    return problems


if __name__ == "__main__":
    langs = load_languages()
    copy_static()
    with open(os.path.join(SITE, "templates", "base.html"), encoding="utf-8") as f:
        template = f.read()
    for lang in langs:
        for page in PAGE_DIRS:
            build_page(langs, lang, page, template)
    build_404(langs, template)
    write_sitemap(langs)
    write_robots()
    pages = sum(1 for _, _, files in os.walk(OUT) for f in files if f == "index.html")
    print(f"built _site: {pages} pages in {len(langs)} languages ({', '.join(langs)}), "
          "plus 404.html, sitemap.xml and robots.txt")
    if "--check" in sys.argv:
        broken = check_site()
        print("check: links, assets, structured data and sitemap all good" if not broken else f"check: {broken} problems")
        sys.exit(1 if broken else 0)
