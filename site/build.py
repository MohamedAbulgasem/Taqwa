#!/usr/bin/env python3
"""Builds taqwa.world into _site/.

Two languages from one set of templates. Each page is a body fragment under site/pages/<lang>/
(home, support) or a Markdown file at the repository root (PRIVACY.md, PRIVACY.ar.md — the same
text the app's About link and GitHub show), wrapped in site/templates/base.html with that
language's header and footer. English lives at the root, Arabic under /ar/, and every page links
to its twin in the other language.

    python3 site/build.py            # writes _site/ next to the repo root
    python3 site/build.py --check    # builds, then fails if any local link is dangling

Needs the `markdown` package (pip install markdown); the Pages workflow installs it.
"""
import html
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

LANGS = {
    "en": {
        "dir": "ltr",
        "prefix": "",
        "og_locale": "en_GB",
        "brand_label": "Taqwa home",
        "nav_label": "Site",
        "footer_label": "Footer",
        "nav_privacy": "Privacy",
        "nav_support": "Support",
        "switch_lang": "ar",
        "switch_label": "العربية",
        "copyright": "© 2026 Mohamed Abulgasem",
        "policy_file": "PRIVACY.md",
        "pages": {
            "home": {"title": "Taqwa · Prayer times, Qibla and the Quran", "og_title": "Taqwa",
                     "description": "A free Islamic app for Android and iPhone: prayer times, adhan notifications, Qibla, the Quran with recitation, and a tasbeeh. No ads, no account, offline by design."},
            "support": {"title": "Support · Taqwa", "og_title": "Taqwa support",
                        "description": "How to report a problem with Taqwa, what to check when a prayer time looks wrong, and how your data is handled."},
            "privacy": {"title": "Privacy policy · Taqwa", "og_title": "Taqwa privacy policy",
                        "description": "What Taqwa stores on your phone, the one thing it uses the internet for, and what it never does."},
        },
    },
    "ar": {
        "dir": "rtl",
        "prefix": "ar/",
        "og_locale": "ar_LY",
        "brand_label": "الصفحة الرئيسية لتقوى",
        "nav_label": "الموقع",
        "footer_label": "التذييل",
        "nav_privacy": "الخصوصية",
        "nav_support": "الدعم",
        "switch_lang": "en",
        "switch_label": "English",
        "copyright": "© 2026 محمد أبو القاسم",
        "policy_file": "PRIVACY.ar.md",
        "pages": {
            "home": {"title": "تقوى · مواقيت الصلاة والقبلة والقرآن", "og_title": "تقوى",
                     "description": "تطبيق إسلامي مجاني لأندرويد وآيفون: مواقيت الصلاة، وإشعارات الأذان، والقبلة، والقرآن مع التلاوة، والتسبيح. بلا إعلانات ولا حساب، ودون اتصال بالتصميم."},
            "support": {"title": "الدعم · تقوى", "og_title": "دعم تقوى",
                        "description": "كيف تبلّغ عن مشكلة في تقوى، وما تراجعه حين يبدو وقت صلاة خاطئًا، وكيف تُعامل بياناتك."},
            "privacy": {"title": "سياسة الخصوصية · تقوى", "og_title": "سياسة خصوصية تقوى",
                        "description": "ما يخزّنه تقوى على هاتفك، والشيء الوحيد الذي يستخدم الإنترنت لأجله، وما لا يفعله أبدًا."},
        },
    },
}

# Output directory per page, relative to the language root ("" is the language's home).
PAGE_DIRS = {"home": "", "support": "support/", "privacy": "privacy/"}


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
        r"(?<![\w/@\"'>])((?:github\.com|docs\.github\.com)/[\w./#?=-]+)",
        lambda m: f'<a href="https://{m.group(1)}">{m.group(1)}</a>',
        body,
    )
    body = re.sub(
        r"(?<![\w/\"'>])([\w.+-]+@[\w-]+\.[\w.]+)",
        lambda m: f'<a href="mailto:{m.group(1)}">{m.group(1)}</a>',
        body,
    )
    page = f"<h1>{html.escape(title)}</h1>\n"
    if meta:
        page += f'<p class="meta">{html.escape(meta)}</p>\n'
    return f'<main class="wrap prose">\n{page}{body}\n</main>\n'


def page_body(lang: str, page: str) -> str:
    if page == "privacy":
        return render_markdown(os.path.join(ROOT, LANGS[lang]["policy_file"]))
    with open(os.path.join(SITE, "pages", lang, f"{page}.html"), encoding="utf-8") as f:
        return f.read()


def build_page(lang: str, page: str, template: str) -> None:
    cfg = LANGS[lang]
    out_dir = cfg["prefix"] + PAGE_DIRS[page]                 # e.g. "ar/support/"
    depth = out_dir.count("/")
    root = "../" * depth                                       # to the site root
    lang_root = "../" * PAGE_DIRS[page].count("/")             # to this language's home
    other = LANGS[cfg["switch_lang"]]
    links = {
        "root": root,
        "home": lang_root or "./",
        "privacy": lang_root + "privacy/" if page != "privacy" else "./",
        "support": lang_root + "support/" if page != "support" else "./",
        "switch_href": root + other["prefix"] + PAGE_DIRS[page] if (other["prefix"] + PAGE_DIRS[page]) else root or "./",
    }
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
        "alt_en": f"{ORIGIN}/{LANGS['en']['prefix']}{PAGE_DIRS[page]}",
        "alt_ar": f"{ORIGIN}/{LANGS['ar']['prefix']}{PAGE_DIRS[page]}",
        "brand_label": cfg["brand_label"],
        "nav_label": cfg["nav_label"],
        "footer_label": cfg["footer_label"],
        "nav_privacy": cfg["nav_privacy"],
        "nav_support": cfg["nav_support"],
        "switch_lang": cfg["switch_lang"],
        "switch_label": cfg["switch_label"],
        "privacy_current": ' aria-current="page"' if page == "privacy" else "",
        "support_current": ' aria-current="page"' if page == "support" else "",
        "copyright": cfg["copyright"],
        **links,
    }
    body = page_body(lang, page)
    for key, value in links.items():
        body = body.replace("{" + key + "}", value)
    values["body"] = body
    out = template
    for key, value in values.items():
        out = out.replace("{" + key + "}", value)
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
    copy_static()
    with open(os.path.join(SITE, "templates", "base.html"), encoding="utf-8") as f:
        template = f.read()
    for lang in LANGS:
        for page in PAGE_DIRS:
            build_page(lang, page, template)
    pages = sum(1 for _, _, files in os.walk(OUT) for f in files if f.endswith(".html"))
    print(f"built _site: {pages} pages")
    if "--check" in sys.argv:
        broken = check_links()
        print("links: all local links resolve" if not broken else f"links: {broken} problems")
        sys.exit(1 if broken else 0)
