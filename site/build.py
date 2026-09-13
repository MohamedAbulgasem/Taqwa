#!/usr/bin/env python3
"""Builds taqwa.world into _site/.

The site is static HTML under site/, copied as is, plus one generated page: the privacy policy,
rendered from the repository's PRIVACY.md so the app's About link, GitHub and the website all
show one text. Run from anywhere:

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
POLICY = os.path.join(ROOT, "PRIVACY.md")

SKIP = {"build.py", "templates", "README.md"}


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


def render_policy() -> None:
    with open(POLICY, encoding="utf-8") as f:
        text = f.read()
    # The H1 becomes the page title; the italic "Last updated" line becomes the meta line.
    title_match = re.match(r"# (.+)\n", text)
    title = title_match.group(1).strip() if title_match else "Privacy policy"
    text = text[title_match.end():] if title_match else text
    meta = ""
    meta_match = re.match(r"\s*_(.+?)_\s*\n", text)
    if meta_match:
        meta = meta_match.group(1).strip()
        text = text[meta_match.end():]
    body = markdown.markdown(text, extensions=["smarty"])
    # Bare domains in the policy become links; the policy itself stays plain text for GitHub.
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
    page += body
    with open(os.path.join(SITE, "templates", "page.html"), encoding="utf-8") as f:
        template = f.read()
    out = (
        template.replace("{title}", "Privacy policy")
        .replace("{description}", "What Taqwa stores on your phone, the one thing it uses the internet for, and what it never does.")
        .replace("{privacy_current}", ' aria-current="page"')
        .replace("{support_current}", "")
        .replace("{body}", page)
    )
    os.makedirs(os.path.join(OUT, "privacy"), exist_ok=True)
    with open(os.path.join(OUT, "privacy", "index.html"), "w", encoding="utf-8") as f:
        f.write(out)


def check_links() -> int:
    """Every relative href/src in the built site must point at a file that exists."""
    missing = 0
    for dirpath, _, files in os.walk(OUT):
        for name in files:
            if not name.endswith(".html"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as f:
                doc = f.read()
            for ref in re.findall(r'(?:href|src)="([^"#]+)"', doc):
                if re.match(r"^(https?:|mailto:|data:)", ref):
                    continue
                target = os.path.normpath(os.path.join(dirpath, ref))
                if os.path.isdir(target):
                    target = os.path.join(target, "index.html")
                if not os.path.exists(target):
                    print(f"dangling: {os.path.relpath(path, OUT)} -> {ref}", file=sys.stderr)
                    missing += 1
    return missing


if __name__ == "__main__":
    copy_static()
    render_policy()
    pages = sum(1 for _, _, files in os.walk(OUT) for f in files if f.endswith(".html"))
    print(f"built _site: {pages} pages")
    if "--check" in sys.argv:
        broken = check_links()
        print("links: all local links resolve" if not broken else f"links: {broken} dangling")
        sys.exit(1 if broken else 0)
