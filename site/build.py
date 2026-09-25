#!/usr/bin/env python3
"""Builds taqwa.world into _site/.

Seven languages from one set of templates. Each language is a directory under site/pages/<lang>/
holding a meta.json (titles, labels, direction, the policy file it uses) and the page bodies
(home.html, support.html); the privacy page is rendered from the Markdown policy at the
repository root (PRIVACY.md, PRIVACY.<lang>.md — the same text the app's About link and GitHub
show). Every page is wrapped in site/templates/base.html with that language's header and footer,
English lives at the root and every other language under /<lang>/, and every page links to its
twin in every other language through the language picker and the hreflang alternates.

The prayer-time pages (a page per city and language, an index per language, and a section at
the bottom of every home page) are rendered by site/timetables.py from the document
tools/timetables writes with the app's own prayer-time engine; see that module.

It also writes sitemap.xml (every page with its language alternates), robots.txt, and the
404.html GitHub Pages serves for any address it has nothing for; each language's home page
carries JSON-LD for the app, and every page a share card from site/assets/og/ (drawn by
tools/site-og.py).

    ./gradlew -p tools/timetables generate   # writes _data/timetables.json (JDK 21)
    python3 site/build.py                    # writes _site/ next to the repo root
    python3 site/build.py --check            # builds, then fails on dangling links, missing
                                             # assets, broken structured data, a sitemap that
                                             # disagrees, or a timetable page that is incomplete

Needs the `markdown` package (pip install markdown); the Pages workflow installs it.
"""
import functools
import hashlib
import html
import json
import os
import posixpath
import re
import shutil
import sys
import xml.etree.ElementTree as ET

import markdown

import timetables

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, "site")
OUT = os.path.join(ROOT, "_site")
ORIGIN = "https://taqwa.world"

SKIP = {"build.py", "timetables.py", "cities.tsv", "templates", "pages", "README.md", "__pycache__"}

# The order the picker lists them in: English first, then the app's own order.
LANGUAGE_ORDER = ["en", "ar", "fr", "tr", "id", "ur", "bn"]

# Output directory per page, relative to the language root ("" is the language's home).
PAGE_DIRS = {"home": "", "support": "support/", "privacy": "privacy/"}

# Every page written, as (directory, {language: twin directory}, x-default directory): the
# sitemap is written from this, so it can only list pages that exist, with the alternates the
# pages themselves carry.
SITEMAP = []

# Whether any city has a prayer-time page. While none does (site/cities.tsv holds every row), the
# section is not built at all: no index, no header or footer link, no home-page strip.
PRAYER_TIMES_LIVE = True


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
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__", ".*"))
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


def json_ld(data: dict) -> str:
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    text = text.replace("</", "<\\/")  # a literal "</" would close the script element early
    return f'  <script type="application/ld+json">{text}</script>\n'


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
    return json_ld({"@context": "https://schema.org", "@graph": graph})


def labels(cfg: dict) -> dict:
    """The header and footer words of one language."""
    words = {key: cfg[key] for key in ("brand_label", "nav_label", "footer_label", "nav_privacy",
                                        "nav_support", "languages_label", "copyright")}
    words["nav_prayer_times"] = cfg["timetable"]["nav"]
    return words


def render(template: str, values: dict) -> str:
    """Fills the template. The body goes in last, so nothing inside it is read as a placeholder."""
    values = {**values, **prayer_times_links(values)}
    out = template
    for key, value in values.items():
        if key != "body":
            out = out.replace("{" + key + "}", value)
    return shield_emails(out.replace("{body}", values["body"]))


def prayer_times_links(values: dict) -> dict:
    """The header and footer links to the prayer-time pages, or nothing while no city is live."""
    if not PRAYER_TIMES_LIVE:
        return {"prayer_times_nav": "", "prayer_times_footer": ""}
    href, name = values["prayer_times"], values["nav_prayer_times"]
    return {
        "prayer_times_nav": f'<a href="{href}"{values["prayer_times_current"]}>{name}</a>\n    ',
        "prayer_times_footer": f'<a href="{href}">{name}</a>\n    ',
    }


def rel(from_dir: str, to_dir: str) -> str:
    """A link from the page in site directory `from_dir` to the one in `to_dir` ("" is the root)."""
    path = posixpath.relpath(to_dir.rstrip("/") or ".", from_dir.rstrip("/") or ".")
    return "./" if path == "." else path + "/"


@functools.lru_cache(maxsize=None)
def asset_versions() -> dict:
    """A fingerprint of the stylesheet and the page script, put in the query string of every URL
    that loads them. GitHub Pages lets browsers keep a file ten minutes, and without it a reader
    who opened the site just before a deploy got the new pages with the old stylesheet: every
    new component unstyled. A changed file now has a new address, so no page can meet an old copy."""
    def fingerprint(name: str) -> str:
        with open(os.path.join(SITE, "assets", name), "rb") as f:
            return hashlib.sha256(f.read()).hexdigest()[:10]
    return {"style_version": fingerprint("style.css"), "script_version": fingerprint("timetable.js")}


def page_links(cfg: dict, out_dir: str) -> dict:
    """The links every page carries, relative to it: the site root for assets, and this
    language's home, privacy, support and prayer-times pages."""
    prefix = cfg["prefix"]
    return {
        "root": "../" * out_dir.count("/"),
        "home": rel(out_dir, prefix),
        "privacy": rel(out_dir, prefix + "privacy/"),
        "support": rel(out_dir, prefix + "support/"),
        "prayer_times": rel(out_dir, prefix + timetables.SECTION),
    }


def write_page(langs: dict, lang: str, template: str, *, out_dir: str, meta: dict, body: str,
               twins: dict, picker: dict, current: str = "", head: str = "") -> None:
    """One page of the site. `twins` maps each language that has this page to its directory
    (the hreflang alternates and the sitemap entry); `picker` maps every language to where its
    name in the language row leads: the twin where there is one, else that language's nearest
    page. `current` names the header link to mark ("privacy", "support", "prayer_times")."""
    cfg = langs[lang]
    links = page_links(cfg, out_dir)
    entries = []
    for other, other_cfg in langs.items():
        if other == lang:
            entries.append(f'<span aria-current="true" lang="{other}">{other_cfg["endonym"]}</span>')
        else:
            entries.append(f'<a href="{rel(out_dir, picker[other])}" lang="{other}" hreflang="{other}">{other_cfg["endonym"]}</a>')
    x_default = twins.get("en", out_dir)
    alternates = "\n".join(
        f'  <link rel="alternate" hreflang="{other}" href="{ORIGIN}/{twins[other]}">' for other in langs if other in twins
    ) + f'\n  <link rel="alternate" hreflang="x-default" href="{ORIGIN}/{x_default}">'
    canonical = f"{ORIGIN}/{out_dir}"
    for key, value in {**links, **asset_versions()}.items():
        body = body.replace("{" + key + "}", value)
    values = {
        "lang": lang,
        "dir": cfg["dir"],
        "title": html.escape(meta["title"], quote=False),
        "og_title": html.escape(meta["og_title"]),
        "description": html.escape(meta["description"]),
        "og_locale": cfg["og_locale"],
        "og_image": f"{ORIGIN}/assets/og/{lang}.jpg",
        "canonical": canonical,
        "link_tags": f'  <link rel="canonical" href="{canonical}">\n{alternates}',
        "robots": "",
        "structured_data": head,
        "language_links": "\n    ".join(entries),
        "privacy_current": ' aria-current="page"' if current == "privacy" else "",
        "support_current": ' aria-current="page"' if current == "support" else "",
        "prayer_times_current": ' aria-current="page"' if current == "prayer_times" else "",
        **labels(cfg),
        **links,
        **asset_versions(),
        "body": body,
    }
    target = os.path.join(OUT, out_dir)
    os.makedirs(target, exist_ok=True)
    with open(os.path.join(target, "index.html"), "w", encoding="utf-8") as f:
        f.write(render(template, values))
    SITEMAP.append((out_dir, twins, x_default))


def build_page(langs: dict, lang: str, page: str, template: str, data: timetables.Timetables) -> None:
    cfg = langs[lang]
    twins = {other: langs[other]["prefix"] + PAGE_DIRS[page] for other in langs}
    raw_body = page_body(langs, lang, page)
    body = raw_body
    if page == "home":
        body = body.replace(timetables.HOME_MARKER, data.home_section(lang))
    write_page(
        langs, lang, template,
        out_dir=twins[lang], meta=cfg["pages"][page], body=body, twins=twins, picker=twins,
        current=page if page in ("privacy", "support") else "",
        head=structured_data(langs, lang, page, raw_body),
    )


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
        "prayer_times_current": "",
        **labels(cfg),
        **asset_versions(),
        "root": "/",
        "home": "/",
        "privacy": "/privacy/",
        "support": "/support/",
        "prayer_times": f"/{timetables.SECTION}",
        "body": NOT_FOUND,
    }
    with open(os.path.join(OUT, "404.html"), "w", encoding="utf-8") as f:
        f.write(render(template, values))


def write_sitemap() -> None:
    """Every page written, each listing its twins in the other languages: the same alternates the
    pages carry in their heads. No lastmod: Google only trusts one that is exact, and the build
    has no honest date to give."""
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ]
    for out_dir, twins, x_default in SITEMAP:
        lines.append("  <url>")
        lines.append(f"    <loc>{ORIGIN}/{out_dir}</loc>")
        for other in LANGUAGE_ORDER:
            if other in twins:
                lines.append(f'    <xhtml:link rel="alternate" hreflang="{other}" href="{ORIGIN}/{twins[other]}"/>')
        lines.append(f'    <xhtml:link rel="alternate" hreflang="x-default" href="{ORIGIN}/{x_default}"/>')
        lines.append("  </url>")
    lines.append("</urlset>")
    with open(os.path.join(OUT, "sitemap.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def write_robots() -> None:
    with open(os.path.join(OUT, "robots.txt"), encoding="utf-8", mode="w") as f:
        f.write(f"User-agent: *\nAllow: /\n\nSitemap: {ORIGIN}/sitemap.xml\n")


def check_site(data: timetables.Timetables) -> int:
    """Fails the build on: a relative or root-relative href/src that points at nothing, a
    template placeholder left in a page, an address Cloudflare would rewrite, an image of this
    site named by absolute address that does not exist, structured data that is not valid JSON,
    a sitemap that disagrees with the pages built, an hreflang alternate the other page does not
    return, and a timetable page that is not whole (see timetables.check_page)."""
    problems = 0

    def problem(message: str) -> None:
        nonlocal problems
        print(message, file=sys.stderr)
        problems += 1

    pages = []
    alternates = {}
    texts = {}

    def text_of(path: str) -> str:
        if path not in texts:
            with open(path, encoding="utf-8") as f:
                texts[path] = f.read()
        return texts[path]

    for dirpath, _, files in os.walk(OUT):
        for name in files:
            if not name.endswith(".html"):
                continue
            path = os.path.join(dirpath, name)
            rel_path = os.path.relpath(path, OUT)
            if name == "index.html":
                pages.append(rel_path)
            with open(path, encoding="utf-8") as f:
                doc = f.read()
            for leftover in re.findall(r"\{[a-z_]+\}", doc):
                problem(f"placeholder: {rel_path} -> {leftover}")
            for address in unshielded_emails(doc):
                problem(f"unshielded email: {rel_path} -> {address}")
            for kind, ref in re.findall(r'(href|src|srcset)="([^"]*)"', doc):
                if not ref or re.match(r"^(https?:|mailto:|data:)", ref):
                    continue
                address, _, anchor = ref.partition("#")
                address = address.partition("?")[0]  # a fingerprint in the query names no other file
                target = path
                if address:
                    base = OUT if address.startswith("/") else dirpath
                    target = os.path.normpath(os.path.join(base, address.lstrip("/")))
                    if os.path.isdir(target):
                        target = os.path.join(target, "index.html")
                    if not os.path.exists(target):
                        problem(f"dangling: {rel_path} -> {ref}")
                        continue
                if kind == "href" and anchor and target.endswith(".html"):
                    if f'id="{anchor}"' not in (doc if target == path else text_of(target)):
                        problem(f"no such anchor: {rel_path} -> {ref}")
            for ref in re.findall(re.escape(ORIGIN) + r'/(assets/[^"\s<]+)', doc):
                if not os.path.exists(os.path.join(OUT, ref)):
                    problem(f"missing asset: {rel_path} -> {ref}")
            for block in re.findall(r'<script type="application/(?:ld\+)?json"[^>]*>(.*?)</script>', doc, re.S):
                try:
                    json.loads(block)
                except ValueError as error:
                    problem(f"structured data: {rel_path} -> {error}")
            if name == "index.html":
                here = f"{ORIGIN}/{os.path.dirname(rel_path)}/".replace(f"{ORIGIN}//", f"{ORIGIN}/")
                alternates[here] = dict(re.findall(r'<link rel="alternate" hreflang="([\w-]+)" href="([^"]+)">', doc))
                for message in timetables.check_page(rel_path, doc, data):
                    problem(message)

    for here, links in alternates.items():
        for language, there in links.items():
            if language == "x-default":
                continue
            if there not in alternates:
                problem(f"hreflang: {here} -> {there} does not exist")
            elif here not in alternates[there].values():
                problem(f"hreflang: {there} does not link back to {here}")

    ns = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    locs = [loc.text for loc in ET.parse(os.path.join(OUT, "sitemap.xml")).getroot().findall("s:url/s:loc", ns)]
    for loc in locs:
        if not os.path.exists(os.path.join(OUT, loc[len(ORIGIN) + 1:], "index.html")):
            problem(f"sitemap: no page for {loc}")
    if len(locs) != len(pages) or len(set(locs)) != len(locs):
        problem(f"sitemap: {len(locs)} addresses ({len(set(locs))} distinct) for {len(pages)} pages")
    with open(os.path.join(OUT, "robots.txt"), encoding="utf-8") as f:
        if f"Sitemap: {ORIGIN}/sitemap.xml" not in f.read():
            problem("robots.txt: no Sitemap line")
    with open(os.path.join(OUT, "404.html"), encoding="utf-8") as f:
        if 'content="noindex"' not in f.read():
            problem("404.html: not marked noindex")
    return problems


if __name__ == "__main__":
    langs = load_languages()
    data = timetables.Timetables(langs, timetables.load(os.path.join(ROOT, "_data", "timetables.json")))
    PRAYER_TIMES_LIVE = bool(data.cities)
    copy_static()
    with open(os.path.join(SITE, "templates", "base.html"), encoding="utf-8") as f:
        template = f.read()
    for lang in langs:
        for page in PAGE_DIRS:
            build_page(langs, lang, page, template, data)
    data.build(template, write_page)
    build_404(langs, template)
    write_sitemap()
    write_robots()
    pages = sum(1 for _, _, files in os.walk(OUT) for f in files if f == "index.html")
    print(f"built _site: {pages} pages in {len(langs)} languages ({', '.join(langs)}), "
          f"{data.page_count} of them prayer-time pages for {len(data.cities)} cities, "
          "plus 404.html, sitemap.xml and robots.txt")
    if "--check" in sys.argv:
        broken = check_site(data)
        print("check: links, assets, structured data, sitemap and timetables all good" if not broken else f"check: {broken} problems")
        sys.exit(1 if broken else 0)
