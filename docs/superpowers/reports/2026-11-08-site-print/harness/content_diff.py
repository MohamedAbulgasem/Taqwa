"""Compares two built sites: every HTML file byte for byte once the stylesheet's cache token is
ignored, every <table> byte for byte, every other file byte for byte."""
import os, re, sys
a, b = sys.argv[1], sys.argv[2]
SCRATCH = {"autoprint.html", "btn1.html", "ruler.html", "ruler2.html", "probe.html"}
def files(root):
    out = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            if f not in SCRATCH:
                p = os.path.join(d, f); out[os.path.relpath(p, root)] = p
    return out
fa, fb = files(a), files(b)
V = re.compile(rb"style\.css\?v=[0-9a-f]+")
same = tables = rows = 0; changed = []
for p in sorted(set(fa) | set(fb)):
    if p not in fa or p not in fb:
        changed.append(p + " (only in one)"); continue
    x, y = open(fa[p], "rb").read(), open(fb[p], "rb").read()
    if p.endswith(".html"):
        tx, ty = re.findall(rb"<table.*?</table>", x, re.S), re.findall(rb"<table.*?</table>", y, re.S)
        if tx != ty: changed.append(p + " (tables)")
        tables += len(tx); rows += sum(t.count(b"<tr data-i=") for t in tx)
        if V.sub(b"v=X", x) == V.sub(b"v=X", y): same += 1
        else: changed.append(p)
    elif x != y:
        changed.append(p)
print(f"{len(fa)} files before, {len(fb)} after; HTML identical apart from the stylesheet token: {same}; "
      f"tables compared: {tables} ({rows} day rows); differing files: {changed}")
