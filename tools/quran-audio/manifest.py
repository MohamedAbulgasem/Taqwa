"""Build manifest.json from the packed containers.

    python3 manifest.py <output path> [<output path> ...]

Only reciters that are actually published are included: their 114 containers
must exist, surahs.json must be complete, and `state/<id>.done` must be present,
which publish.py writes only after `gh release view` has confirmed all 114 assets
with the right byte sizes.  A reciter that failed anywhere is omitted, so the
manifest never points at a URL that would 404.
Asset URL = base + release + "/" + "<id>-<nnn>.taqa".
"""

import datetime
import json
import os
import sys

from common import BASE, RECITERS, STYLE, WORK, log, packdir, release


def build():
    reciters, dropped = [], []
    for rid, _folder, kbps, gap, hue, name_en, name_ar in RECITERS:
        p = os.path.join(packdir(rid), "surahs.json")
        published = os.path.exists(os.path.join(WORK, "state", f"{rid}.done"))
        if not os.path.exists(p) or not published:
            dropped.append(rid)
            continue
        meta = json.load(open(p))
        if len(meta["surahs"]) != 114:
            dropped.append(rid)
            continue
        reciters.append({
            "id": rid,
            "nameEn": name_en,
            "nameAr": name_ar,
            "style": STYLE,
            "kbps": kbps,
            "gapMs": gap,
            "hue": hue,
            "photo": None,
            "release": release(rid),
            "totalBytes": meta["totalBytes"],
            "surahs": meta["surahs"],
        })
    return {
        "schema": 1,
        "generated": datetime.datetime.now(datetime.timezone.utc)
                             .strftime("%Y-%m-%dT%H:%M:%SZ"),
        "base": BASE,
        "reciters": reciters,
    }, dropped


def rewritten(previous, manifest):
    """Every way `manifest` changes what `previous` already published.

    A release is published once and never changed. Every installed app holds the manifest it was
    built with and refuses a fetched one that gives a release it shipped any other size or
    SHA-256, or that moves the base (security review, 26 October 2026), so changed audio goes
    out under a new release name, never over the old one.
    """
    problems = []
    if manifest["base"] != previous["base"]:
        problems.append(f"base {previous['base']} -> {manifest['base']}")
    shipped = {(r["id"], r["release"]): {s["n"]: (s["bytes"], s["sha256"]) for s in r["surahs"]}
               for r in previous["reciters"]}
    for r in manifest["reciters"]:
        old = shipped.get((r["id"], r["release"]))
        if old is None:
            continue
        for s in r["surahs"]:
            if old.get(s["n"]) != (s["bytes"], s["sha256"]):
                problems.append(f"{r['release']} surah {s['n']}")
    return problems


def main():
    manifest, dropped = build()
    blob = json.dumps(manifest, ensure_ascii=False, indent=1).encode("utf-8")
    # Checked against every copy already on disk before any is written: the Taqwa-data clone is
    # what installed apps fetch, and the app's own copy is what the next build ships.
    for path in sys.argv[1:]:
        if os.path.exists(path):
            problems = rewritten(json.load(open(path)), manifest)
            if problems:
                log(f"manifest: REFUSED, it would change published releases ({path}): "
                    f"{problems[:5]}{' ...' if len(problems) > 5 else ''}")
                log("manifest: give changed audio a new release name; see README.md")
                return 1
    for path in sys.argv[1:]:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(blob)
        log(f"manifest -> {path} ({len(blob):,} bytes)")
    log(f"manifest: {len(manifest['reciters'])} reciters"
        + (f", dropped {dropped}" if dropped else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
