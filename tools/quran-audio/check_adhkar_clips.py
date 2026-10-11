"""Check the bundled adhkar clips are the corpus's own bytes, and nothing else.

    python3 -I check_adhkar_clips.py

For each clip written by `adhkar_clips.py` this re-derives the expected bytes straight from
Alafasy's container (its own small reader, not the one in `adhkar_clips.py`, so a mistake in the
cutter cannot excuse itself) and compares byte for byte:

  - the container is the one the shipped manifest publishes (size and SHA-256);
  - the clip is exactly the container's ayah ranges, in ayah order, and as long as their sum;
  - each ayah in it opens like an MP3 (an ID3 tag or a frame sync), as `fetch.py` demands;
  - the clip decodes to sound about as long as its ayahs do separately (the index's measured `ms`
    where the container carries them, else each ayah's own full decode; the decoded length, not
    ffprobe's, since the files carry Xing headers; skipped with a note if ffmpeg is not at the
    path `verify.py` uses);
  - the folder holds exactly the four clips (dot-files and `*.part` aside; a `.part` is reported),
    and they total under the 2 MB the plan promised.

The four clips and the ayahs each is are written out below (`EXPECTED`), not imported from
`adhkar_clips.py`: the checker holds the cutter to a table of its own.

Exits 1 on any difference. The containers are read from the pipeline cache (`containers/ar.alafasy/`
under `WORK`, `TAQWA_AUDIO_WORK` to point elsewhere) and fetched into it if absent, which is what
`adhkar_clips.py` does; this script never writes into the repository.
"""

import hashlib
import json
import os
import struct
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from adhkar_clips import (  # noqa: E402
    BUDGET_BYTES, CLIP_DIR, RID, container_name, ensure_container, manifest_entry,
)
from verify import FFMPEG, measure  # noqa: E402

# What the app expects the folder to hold, written out here and NOT taken from the cutter's own
# `CLIPS`: a checker that imports its answer from the script it checks agrees with it whatever
# that script does. Clip file name -> (surah, the ayahs it is). The three short surahs are whole
# (112 has four ayahs, 113 five, 114 six; the container must say so too), and the controller asks
# for a clip by exactly these names (`adhkarClipBytes`: `002-255.mp3` for one ayah, `112.mp3` for
# a whole surah).
EXPECTED = {
    "002-255.mp3": (2, [255]),
    "112.mp3": (112, [1, 2, 3, 4]),
    "113.mp3": (113, [1, 2, 3, 4, 5]),
    "114.mp3": (114, [1, 2, 3, 4, 5, 6]),
}

# Half a second over a clip, as check_previews.py, plus a join's worth for every ayah after the
# first. An ayah decoded alone has its encoder delay and its Xing/Info frame dropped; inside the
# concatenation (which is how the previews are cut too) the later ones are decoded as they come,
# which measures 0.1-0.16 s longer a join. A missing or cut ayah is seconds, far outside this.
MS_TOLERANCE = 500
MS_PER_JOIN = 200


def mp3_like(b):
    return b[:3] == b"ID3" or (len(b) > 1 and b[0] == 0xFF and (b[1] & 0xE0) == 0xE0)


def expected_ayahs(path, surah):
    """[(ayah, bytes, ms)] for the wanted-or-all ayahs of the container at [path], read afresh."""
    with open(path, "rb") as f:
        head = f.read(12)
        if head[:4] != b"TAQA" or head[4] != 1:
            raise ValueError("not a version-1 .taqa")
        index_len = struct.unpack(">I", head[8:])[0]
        index = json.loads(f.read(index_len))
        start = 12 + index_len
        if index["reciter"] != RID or index["surah"] != surah:
            raise ValueError(f"index names {index['reciter']} {index['surah']}")
        rows = index["ayahs"]
        if [a["n"] for a in rows] != list(range(1, len(rows) + 1)):
            raise ValueError("ayahs are not numbered 1..n in order")
        out, end = [], 0
        for a in rows:
            if a["off"] != end:
                raise ValueError(f"ayah {a['n']} does not follow the one before it")
            f.seek(start + a["off"])
            b = f.read(a["len"])
            if len(b) != a["len"]:
                raise ValueError(f"ayah {a['n']} runs past the end")
            out.append((a["n"], b, a.get("ms")))
            end = a["off"] + a["len"]
        return out


def ayah_ms(picked):
    """(total ms, how it was found): the index's measured lengths if every ayah has one, else
    each ayah decoded on its own from a scratch file outside the repository."""
    if all(ms is not None for _, _, ms in picked):
        return sum(ms for _, _, ms in picked), "measured in the index"
    total = 0
    with tempfile.TemporaryDirectory(prefix="adhkar-check-") as tmp:
        for n, b, _ in picked:
            part = os.path.join(tmp, f"{n}.mp3")
            with open(part, "wb") as f:
                f.write(b)
            ms, err = measure(part)
            if err:
                raise SystemExit(f"ayah {n} does not decode: {err}")
            total += ms
    return total, "decoded one by one"


def main():
    release, published = manifest_entry()
    bad, total = 0, 0

    def fail(msg):
        nonlocal bad
        bad += 1
        print("  FAIL", msg)

    # The folder is bundled whole, so it must hold exactly the expected files. Dot-files (a
    # Finder's `.DS_Store`) are not part of that, and a `.part` is a cutter run in flight or
    # interrupted: neither fails the check, and a `.part` is named on its own line because the
    # build would bundle it if it stayed.
    names = os.listdir(CLIP_DIR) if os.path.isdir(CLIP_DIR) else []
    partial = sorted(n for n in names if n.endswith(".part") and not n.startswith("."))
    present = sorted(n for n in names if not n.startswith(".") and not n.endswith(".part"))
    if present != sorted(EXPECTED):
        fail(f"{CLIP_DIR} holds {present}, expected {sorted(EXPECTED)}")
    if partial:
        print(f"  note: partial file(s) {partial} in the clip folder: a cutter run in progress, or "
              f"one that was interrupted; delete them before building")

    for name, (surah, wanted) in EXPECTED.items():
        path = os.path.join(CLIP_DIR, name)
        print(f"{name}  (from {container_name(surah)})")
        try:
            container = ensure_container(release, surah, published[surah])
            rows = expected_ayahs(container, surah)
        except (SystemExit, ValueError, OSError) as e:
            fail(f"container: {e}")
            continue
        picked = [r for r in rows if r[0] in wanted]
        if [r[0] for r in picked] != wanted:
            fail(f"container lacks some of the wanted ayahs {wanted}")
            continue
        # A name without an ayah is a whole surah: the container must have exactly these ayahs.
        if "-" not in name and [r[0] for r in rows] != wanted:
            fail(f"{name} is a whole surah of {len(wanted)} ayahs, the container has {len(rows)}")
            continue
        expected = b"".join(b for _, b, _ in picked)
        if not os.path.exists(path):
            fail("clip is missing")
            continue
        with open(path, "rb") as f:
            actual = f.read()
        total += len(actual)
        digest = hashlib.sha256(actual).hexdigest()
        print(f"  {len(actual):,} bytes, {len(picked)} ayah(s) {[n for n, _, _ in picked]}, sha256 {digest}")
        if actual != expected:
            at = next((i for i, (x, y) in enumerate(zip(actual, expected)) if x != y), min(len(actual), len(expected)))
            fail(f"differs from the container's ayah bytes: clip {len(actual):,} vs expected "
                 f"{len(expected):,}, first difference at byte {at:,}")
            continue
        for n, b, _ in picked:
            if not mp3_like(b):
                fail(f"ayah {n} does not open like an MP3")
        if os.path.exists(FFMPEG):
            ms, err = measure(path)
            want, how = ayah_ms(picked)
            if err or abs(ms - want) > MS_TOLERANCE + MS_PER_JOIN * (len(picked) - 1):
                fail(f"decodes to {ms} ms ({err}), the ayahs {how} add to {want} ms")
            else:
                print(f"  decodes to {ms/1000:.2f} s, the ayahs {how} add to {want/1000:.2f} s: ok")
        else:
            print(f"  (duration cross-check skipped: no ffmpeg at {FFMPEG})")
        print("  identical to the container's bytes: ok")

    print(f"total {total:,} bytes (budget {BUDGET_BYTES:,})")
    if total >= BUDGET_BYTES:
        fail("over budget")
    print("OK" if not bad else f"FAILED: {bad} problem(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
