"""Cut the four Quranic adhkar items from Alafasy's own `.taqa` containers.

    python3 -I adhkar_clips.py

The first run of the adhkar session has no network and nothing downloaded, yet the four
Quranic items (Ayat al-Kursi, Al-Ikhlas, Al-Falaq, An-Nas) are the ones a reader wants to hear
in the voice they were offered at the start. They travel with the app, for the default reciter
only, as clips under

    shared/src/commonMain/composeResources/files/adhkar/clips/ar.alafasy/
        002-255.mp3   Al-Baqarah 2:255, one ayah
        112.mp3       Al-Ikhlas, all four ayahs
        113.mp3       Al-Falaq, all five ayahs
        114.mp3       An-Nas, all six ayahs

Each clip is the corpus's own bytes and nothing else: the ayah MP3s of the surah's container,
concatenated in ayah order, exactly as `pack.py` cuts the preview (`previews/<id>.mp3` is the raw
bytes of 1:1 followed by 1:2). The container stores every ayah byte-identical to the Islamic
Network download, so a range of the container IS the download. No re-encoding, no trimming, no
re-tagging: the licence covers the published bitrates and nothing else (see
`docs/licences/Alafasy-adhkar-clips.txt`).

Source bytes. The four containers `ar.alafasy-002.taqa`, `-112`, `-113`, `-114` are looked for
in the pipeline's cache (`containers/ar.alafasy/`, where `pack.py` writes them; the root is
`WORK` in `common.py`, overridable with `TAQWA_AUDIO_WORK`). A missing one is downloaded from the
Taqwa-data release the shipped manifest names (`base + release + "/" + id + "-" + nnn + ".taqa"`)
into that same directory, never into the repository. Every container, found or fetched, must
match the byte size and SHA-256 the manifest publishes for it before a single byte is cut; a
container that does not is an error, not something to repair.

Container layout (v1), read here only far enough to find the ranges (the app's reader is
`recitation/Taqa.kt`; `pack.py` documents the writer):

    0..3 "TAQA", 4 version=1, 5..7 zero, 8..11 indexLength u32 big-endian,
    12.. index JSON {"reciter","surah","kbps","ayahs":[{"n","off","len","ms"}...]},
    then the MP3s back to back; an ayah's file offset is 12 + indexLength + off.

Deterministic: the output is a pure function of the four containers (no timestamps, no ordering
that depends on the file system), so a re-run on any machine rewrites identical files.
`check_adhkar_clips.py` re-derives every clip from the containers and compares byte for byte.
"""

import hashlib
import json
import os
import struct
import sys
import tempfile
import urllib.request

# `python3 -I` does not put this script's own directory on sys.path; the pipeline's helpers
# are siblings of this file, so name that one trusted directory explicitly.
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from common import BASE, UA, packdir  # noqa: E402

RID = "ar.alafasy"
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
MANIFEST = os.path.join(
    REPO_ROOT, "shared", "src", "commonMain", "composeResources", "files", "recitation", "manifest.json"
)
CLIP_DIR = os.path.join(
    REPO_ROOT, "shared", "src", "commonMain", "composeResources", "files", "adhkar", "clips", RID
)

# (file name, surah, ayahs): ayahs None = every ayah of the surah, in order.
CLIPS = [
    ("002-255.mp3", 2, [255]),
    ("112.mp3", 112, None),
    ("113.mp3", 113, None),
    ("114.mp3", 114, None),
]

# The whole set stays well under this; the plan promised "well under 2 MB".
BUDGET_BYTES = 2_000_000


def manifest_entry():
    """(release name, {surah: (bytes, sha256)}) for ar.alafasy, from the manifest the app ships."""
    with open(MANIFEST, "rb") as f:
        manifest = json.load(f)
    if manifest["base"] != BASE:
        raise SystemExit(f"manifest base {manifest['base']} is not common.BASE {BASE}")
    for r in manifest["reciters"]:
        if r["id"] == RID:
            return r["release"], {s["n"]: (s["bytes"], s["sha256"]) for s in r["surahs"]}
    raise SystemExit(f"{RID} is not in {MANIFEST}")


def container_name(surah):
    return f"{RID}-{surah:03d}.taqa"


def container_path(surah):
    return os.path.join(packdir(RID), container_name(surah))


def container_url(release, surah):
    return BASE + release + "/" + container_name(surah)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, dest):
    """Stream [url] to [dest] through a .part file; the caller verifies it."""
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=os.path.basename(dest) + ".", suffix=".part", dir=os.path.dirname(dest))
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=120) as r, os.fdopen(fd, "wb") as out:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
        os.replace(tmp, dest)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def ensure_container(release, surah, expected):
    """The container's path, fetched if absent, and proven to be the one the manifest publishes."""
    path = container_path(surah)
    want_bytes, want_sha = expected
    if not os.path.exists(path):
        url = container_url(release, surah)
        print(f"fetch  {container_name(surah)} <- {url}", flush=True)
        download(url, path)
    got_bytes = os.path.getsize(path)
    if got_bytes != want_bytes:
        raise SystemExit(f"{path}: {got_bytes} bytes, the manifest says {want_bytes}; delete it and re-run")
    got_sha = sha256_of(path)
    if got_sha != want_sha:
        raise SystemExit(f"{path}: SHA-256 {got_sha}, the manifest says {want_sha}; delete it and re-run")
    return path


def read_index(buf, surah):
    """(ayahs, dataStart) of a version-1 container held in [buf]."""
    if buf[:4] != b"TAQA" or buf[4] != 1:
        raise SystemExit(f"surah {surah}: not a version-1 .taqa")
    (length,) = struct.unpack(">I", buf[8:12])
    index = json.loads(buf[12:12 + length].decode("utf-8"))
    if index["reciter"] != RID or index["surah"] != surah:
        raise SystemExit(f"surah {surah}: the index says {index['reciter']} {index['surah']}")
    return index["ayahs"], 12 + length


def cut(buf, surah, wanted):
    """The wanted ayahs' MP3 bytes concatenated in order, and [(ayah, first byte, last byte)]
    as absolute offsets in the container (both ends inclusive, as the app's `rangeOf`)."""
    ayahs, data_start = read_index(buf, surah)
    by_n = {a["n"]: a for a in ayahs}
    ns = [a["n"] for a in ayahs] if wanted is None else wanted
    parts, ranges = [], []
    for n in ns:
        a = by_n.get(n)
        if a is None:
            raise SystemExit(f"surah {surah} has no ayah {n}")
        first = data_start + a["off"]
        chunk = buf[first:first + a["len"]]
        if len(chunk) != a["len"]:
            raise SystemExit(f"surah {surah} ayah {n} runs past the end of the container")
        parts.append(chunk)
        ranges.append((n, first, first + a["len"] - 1))
    return b"".join(parts), ranges


def main():
    if len(sys.argv) > 1:
        raise SystemExit(__doc__)
    release, published = manifest_entry()
    os.makedirs(CLIP_DIR, exist_ok=True)
    total = 0
    for name, surah, wanted in CLIPS:
        with open(ensure_container(release, surah, published[surah]), "rb") as f:
            buf = f.read()
        clip, ranges = cut(buf, surah, wanted)
        dest = os.path.join(CLIP_DIR, name)
        tmp = dest + ".part"
        with open(tmp, "wb") as f:
            f.write(clip)
        os.replace(tmp, dest)
        total += len(clip)
        spans = ", ".join(f"{n}:{a}-{b}" for n, a, b in ranges)
        print(f"{name}: {len(clip):,} bytes  sha256 {hashlib.sha256(clip).hexdigest()}")
        print(f"        from {container_name(surah)}, ayah:first-last byte  {spans}")
    print(f"total {total:,} bytes in {len(CLIPS)} clips (budget {BUDGET_BYTES:,})")
    if total >= BUDGET_BYTES:
        raise SystemExit("over budget")
    return 0


if __name__ == "__main__":
    sys.exit(main())
