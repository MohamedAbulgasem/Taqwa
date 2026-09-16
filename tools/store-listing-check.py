#!/usr/bin/env python3
"""Checks every docs/store/listing/<lang>.md against the stores' field limits.

    python3 tools/store-listing-check.py          # all languages
    python3 tools/store-listing-check.py fr ur    # only these

A field is a `### Heading (limit)` followed by its text up to the next heading; lines in
parentheses are notes and are skipped. Keywords are also checked for spaces after commas."""
import glob, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIELD = re.compile(r"^### (.+?) \((\d+)\)\s*$")

def check(path):
    problems = 0
    lang = os.path.basename(path)[:-3]
    lines = open(path, encoding="utf-8").read().split("\n")
    i = 0
    while i < len(lines):
        m = FIELD.match(lines[i])
        if not m:
            i += 1
            continue
        name, limit = m.group(1), int(m.group(2))
        body = []
        i += 1
        while i < len(lines) and not lines[i].startswith("#"):
            body.append(lines[i])
            i += 1
        text = "\n".join(body).strip()
        text = "\n".join(l for l in text.split("\n") if not (l.startswith("(") and l.endswith(")"))).strip()
        n = len(text)
        flag = "" if n <= limit else "  <-- OVER"
        if n > limit:
            problems += 1
        if name.startswith("Keywords") and ", " in text:
            print(f"{lang}: {name}: a space after a comma wastes characters")
            problems += 1
        print(f"{lang}: {name:20s} {n:4d}/{limit}{flag}")
    return problems

if __name__ == "__main__":
    only = set(sys.argv[1:])
    total = 0
    for path in sorted(glob.glob(os.path.join(ROOT, "docs/store/listing/*.md"))):
        if only and os.path.basename(path)[:-3] not in only:
            continue
        total += check(path)
    print("listing: clean" if not total else f"listing: {total} problems")
    sys.exit(1 if total else 0)
