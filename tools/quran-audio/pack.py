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
            1..count; len = the MP3's file size; an ayah whose first frame
            is at another bit-rate than the reciter's (repair.py --substitute)
            carries its own "kbps", which the app's length estimate reads
    then    the MP3 files concatenated in ayah order, byte-identical to the
            downloads (no re-encoding: the licence covers the published bitrates)

Writes `containers/<id>/<id>-<nnn>.taqa` and `containers/<id>/surahs.json`
(per-surah byte size and SHA-256), and the preview `previews/<id>.mp3`, which is
the raw bytes of global ayah 1 followed by global ayah 2 (Al-Fatiha 1:1-1:2).
"""

import concurrent.futures as cf
import hashlib
import json
import os
import struct
import subprocess
import sys
import time

from common import log, outdir, packdir, reciter, surah_ranges, WORK
from verify import FFPROBE

MAGIC = b"TAQA"
VERSION = 1

# ffprobe's average for a constant-bit-rate file runs a kbps or so over the nominal
# because it counts the ID3 tag; only a real departure is worth twelve bytes an ayah.
KBPS_TOLERANCE = 2

def stream_kbps(paths, workers=8):
    """Each file's audio bit-rate in kbps by ffprobe, keyed by path.

    ffprobe rather than a header parse: the CDN's re-tagged files begin with an ID3
    tag whose declared size is not where the audio starts, and a hand-rolled sync
    scan misread five of a hundred and sixty sampled files - two consecutive
    sync-shaped byte pairs turn up inside tag text and audio data alike. The probe
    is what verify.py already trusts, and 6,236 of them take under a minute.
    """
    def one(path):
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-select_streams", "a:0", "-show_entries",
             "stream=bit_rate", "-of", "default=nw=1:nk=1", path],
            capture_output=True, text=True, timeout=60)
        try:
            return path, round(int(out.stdout.strip()) / 1000)
        except ValueError:
            return path, None
    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        return dict(ex.map(one, paths))


def build(rid, surah, kbps, first, last, src, dest, rates):
    ayahs, blobs, off = [], [], 0
    for i, g in enumerate(range(first, last + 1), 1):
        path = os.path.join(src, f"{g}.mp3")
        with open(path, "rb") as f:
            b = f.read()
        if len(b) < 1000:
            raise SystemExit(f"{rid} surah {surah} ayah {i}: {path} is {len(b)} bytes")
        entry = {"n": i, "off": off, "len": len(b)}
        own = rates.get(path)
        if own is not None and abs(own - kbps) > KBPS_TOLERANCE:
            # Not the reciter's rate: a variable-bit-rate encode in an otherwise constant
            # edition (Al-Ajmi has hundreds averaging 117-134 kbps), or an ayah repair.py
            # --substitute took from the other published bit-rate. Either way the app's
            # length estimate needs this ayah's own average, not the reciter's nominal.
            entry["kbps"] = own
        ayahs.append(entry)
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
    rates = stream_kbps([os.path.join(src, f"{g}.mp3") for g in range(1, 6237)])
    recorded = sum(1 for v in rates.values() if v is not None and abs(v - kbps) > KBPS_TOLERANCE)
    log(f"pack {rid}: bit-rates probed for {len(rates)} files; {recorded} carry their own "
        f"(further than {KBPS_TOLERANCE} kbps from {kbps})")

    for surah in range(1, 115):
        first, last = ranges[surah]
        dest = os.path.join(dst, f"{rid}-{surah:03d}.taqa")
        size, digest = build(rid, surah, kbps, first, last, src, dest, rates)
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
