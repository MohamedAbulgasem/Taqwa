"""Pack a verified reciter into 114 `.taqa` containers, plus the preview clip.

    python3 pack.py <reciter id>

Container layout (v1), exactly as the app reads it:

    0..3    "TAQA" ASCII
    4       version = 1 (u8)
    5..7    reserved = 0
    8..11   indexLength u32 big-endian
    12..    index JSON, UTF-8, exactly indexLength bytes:
            {"reciter":"ar.alafasy","surah":2,"kbps":64,
             "ayahs":[{"n":1,"off":0,"len":31872}, ...]}
            off is relative to dataStart = 12 + indexLength; ayahs in order
            1..count; len = the MP3's file size
    then    the MP3 files concatenated in ayah order, byte-identical to the
            downloads (no re-encoding: the licence covers the published bitrates)

Writes `containers/<id>/<id>-<nnn>.taqa` and `containers/<id>/surahs.json`
(per-surah byte size and SHA-256), and the preview `previews/<id>.mp3`, which is
the raw bytes of global ayah 1 followed by global ayah 2 (Al-Fatiha 1:1-1:2).
"""

import hashlib
import json
import os
import struct
import sys
import time

from common import log, outdir, packdir, reciter, surah_ranges, WORK

MAGIC = b"TAQA"
VERSION = 1


def build(rid, surah, kbps, first, last, src, dest):
    ayahs, blobs, off = [], [], 0
    for i, g in enumerate(range(first, last + 1), 1):
        path = os.path.join(src, f"{g}.mp3")
        with open(path, "rb") as f:
            b = f.read()
        if len(b) < 1000:
            raise SystemExit(f"{rid} surah {surah} ayah {i}: {path} is {len(b)} bytes")
        ayahs.append({"n": i, "off": off, "len": len(b)})
        blobs.append(b)
        off += len(b)
    index = json.dumps(
        {"reciter": rid, "surah": surah, "kbps": kbps, "ayahs": ayahs},
        ensure_ascii=False, separators=(",", ":"),
    ).encode("utf-8")
    header = MAGIC + struct.pack(">BBBBI", VERSION, 0, 0, 0, len(index))
    h = hashlib.sha256()
    tmp = dest + ".part"
    with open(tmp, "wb") as f:
        for chunk in [header, index, *blobs]:
            f.write(chunk)
            h.update(chunk)
    os.replace(tmp, dest)
    return os.path.getsize(dest), h.hexdigest()


def main():
    rid = sys.argv[1]
    kbps = reciter(rid)[2]
    src, dst = outdir(rid), packdir(rid)
    os.makedirs(dst, exist_ok=True)
    ranges = surah_ranges()
    t0, total, out = time.time(), 0, []

    for surah in range(1, 115):
        first, last = ranges[surah]
        dest = os.path.join(dst, f"{rid}-{surah:03d}.taqa")
        size, digest = build(rid, surah, kbps, first, last, src, dest)
        total += size
        out.append({"n": surah, "bytes": size, "sha256": digest})

    json.dump({"reciter": rid, "kbps": kbps, "totalBytes": total, "surahs": out},
              open(os.path.join(dst, "surahs.json"), "w"), indent=1)

    prevdir = os.path.join(WORK, "previews")
    os.makedirs(prevdir, exist_ok=True)
    with open(os.path.join(prevdir, f"{rid}.mp3"), "wb") as f:
        for g in (1, 2):
            with open(os.path.join(src, f"{g}.mp3"), "rb") as a:
                f.write(a.read())

    log(f"pack {rid}: 114 containers, {total:,} bytes, preview written, "
        f"{time.time() - t0:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
