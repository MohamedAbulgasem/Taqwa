#!/usr/bin/env python3
"""Builds taqwa.world into _site/.

Seven languages from one set of templates. Each language is a directory under site/pages/<lang>/
holding a meta.json (titles, labels, direction, the policy file it uses) and the page bodies
(home.html, support.html); the privacy page is rendered from the Markdown policy at the
repository root (PRIVACY.md, PRIVACY.<lang>.md — the same text the app's About link and GitHub
show). Every page is wrapped in site/templates/base.html with that language's header and footer,
English lives at the root and every other language under /<lang>/, and every page links to its
twin in every other language through the language picker and the hreflang alternates.

    python3 site/build.py            # writes _site/ next to the repo root
    python3 site/build.py --check    # builds, then fails if any local link is dangling

Needs the `markdown` package (pip install markdown); the Pages workflow installs it.
"""
import html
import json
import os
import re
import shutil
import sys

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
    values = {
        "lang": lang,
        "dir": cfg["dir"],
        "title": meta["title"],
        "og_title": meta["og_title"],
        "description": meta["description"],
        "og_locale": cfg["og_locale"],
        "canonical": canonical,
        "alternates": alternates,
        "brand_label": cfg["brand_label"],
        "nav_label": cfg["nav_label"],
        "footer_label": cfg["footer_label"],
        "nav_privacy": cfg["nav_privacy"],
        "nav_support": cfg["nav_support"],
        "languages_label": cfg["languages_label"],
        "language_links": "\n    ".join(picker),
        "privacy_current": ' aria-current="page"' if page == "privacy" else "",
        "support_current": ' aria-current="page"' if page == "support" else "",
        "copyright": cfg["copyright"],
        **links,
    }
    body = page_body(langs, lang, page)
    for key, value in links.items():
        body = body.replace("{" + key + "}", value)
    values["body"] = body
    out = template
    for key, value in values.items():
        out = out.replace("{" + key + "}", value)
    out = shield_emails(out)
    target = os.path.join(OUT, out_dir)
    os.makedirs(target, exist_ok=True)
    with open(os.path.join(target, "index.html"), "w", encoding="utf-8") as f:
        f.write(out)


def check_links() -> int:
    """Every relative href/src in the built site must point at a file that exists, and no
    template placeholder may survive."""
    problems = 0
    for dirpath, _, files in os.walk(OUT):
        for name in files:
            if not name.endswith(".html"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as f:
                doc = f.read()
            for leftover in re.findall(r"\{[a-z_]+\}", doc):
                print(f"placeholder: {os.path.relpath(path, OUT)} -> {leftover}", file=sys.stderr)
                problems += 1
            for address in unshielded_emails(doc):
                print(f"unshielded email: {os.path.relpath(path, OUT)} -> {address}", file=sys.stderr)
                problems += 1
            for ref in re.findall(r'(?:href|src|srcset)="([^"#]+)"', doc):
                if re.match(r"^(https?:|mailto:|data:)", ref):
                    continue
                target = os.path.normpath(os.path.join(dirpath, ref))
                if os.path.isdir(target):
                    target = os.path.join(target, "index.html")
                if not os.path.exists(target):
                    print(f"dangling: {os.path.relpath(path, OUT)} -> {ref}", file=sys.stderr)
                    problems += 1
    return problems


if __name__ == "__main__":
    langs = load_languages()
    copy_static()
    with open(os.path.join(SITE, "templates", "base.html"), encoding="utf-8") as f:
        template = f.read()
    for lang in langs:
        for page in PAGE_DIRS:
            build_page(langs, lang, page, template)
    pages = sum(1 for _, _, files in os.walk(OUT) for f in files if f.endswith(".html"))
    print(f"built _site: {pages} pages in {len(langs)} languages ({', '.join(langs)})")
    if "--check" in sys.argv:
        broken = check_links()
        print("links: all local links resolve" if not broken else f"links: {broken} problems")
        sys.exit(1 if broken else 0)
