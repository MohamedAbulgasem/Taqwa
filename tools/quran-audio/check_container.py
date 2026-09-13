"""Read a packed reciter back the way the app will, and prove it round-trips.

    python3 check_container.py <reciter id> [surah ...]

For each surah checked: parse the header exactly as the app does, confirm the
index covers every ayah in order with contiguous offsets, confirm the file size
equals dataStart + sum(len), extract every ayah slice and require it to be
byte-identical to the downloaded MP3, confirm every ayah carries a measured
length, and decode the first and last slices to check their lengths against it.
"""

import json
import os
import struct
import sys

from common import log, outdir, packdir, surah_ranges
from verify import probe


def check(rid, surah, ranges):
    path = os.path.join(packdir(rid), f"{rid}-{surah:03d}.taqa")
    blob = open(path, "rb").read()
    assert blob[:4] == b"TAQA", f"{path}: bad magic"
    version, r1, r2, r3, index_len = struct.unpack(">BBBBI", blob[4:12])
    assert version == 1 and (r1, r2, r3) == (0, 0, 0), f"{path}: bad header"
    index = json.loads(blob[12:12 + index_len].decode("utf-8"))
    data_start = 12 + index_len
    assert index["reciter"] == rid and index["surah"] == surah
    first, last = ranges[surah]
    count = last - first + 1
    ayahs = index["ayahs"]
    assert [a["n"] for a in ayahs] == list(range(1, count + 1)), f"{path}: index order"
    for a in ayahs:
        assert isinstance(a.get("ms"), int) and a["ms"] > 300, f"{path}: ayah {a['n']} has no measured length"
    off = 0
    for i, a in enumerate(ayahs):
        assert a["off"] == off, f"{path}: ayah {a['n']} offset {a['off']} != {off}"
        slice_ = blob[data_start + a["off"]: data_start + a["off"] + a["len"]]
        src = open(os.path.join(outdir(rid), f"{first + i}.mp3"), "rb").read()
        assert slice_ == src, f"{path}: ayah {a['n']} bytes differ from download"
        off += a["len"]
    assert len(blob) == data_start + off, f"{path}: trailing bytes"
    for a in (ayahs[0], ayahs[-1]):
        tmp = f"/tmp/slice-{rid}-{surah}-{a['n']}.mp3"
        with open(tmp, "wb") as f:
            f.write(blob[data_start + a["off"]: data_start + a["off"] + a["len"]])
        dur, err = probe(tmp)
        assert err is None, f"{path}: ayah {a['n']} slice unplayable: {err}"
        assert abs(dur * 1000 - a["ms"]) <= 30, f"{path}: ayah {a['n']} index says {a['ms']} ms, slice decodes to {dur * 1000:.0f}"
        os.remove(tmp)
    return len(blob), count


def main():
    rid = sys.argv[1]
    ranges = surah_ranges()
    todo = [int(x) for x in sys.argv[2:]] or [1, 2, 18, 36, 55, 112, 114]
    for s in todo:
        size, count = check(rid, s, ranges)
        log(f"container {rid}-{s:03d}.taqa: {count} ayahs, {size:,} bytes, "
            f"slices byte-identical, endpoints playable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
