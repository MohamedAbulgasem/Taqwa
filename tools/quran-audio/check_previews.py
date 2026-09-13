"""Check every preview clip parses and lasts about as long as its two ayahs.

    python3 check_previews.py [reciter id ...]

A preview is the raw bytes of global ayah 1 followed by global ayah 2
(Al-Fatiha 1:1-1:2) concatenated unmodified, so its duration should equal the
sum of the two source files to within a frame or two.
"""

import os
import sys

from common import RECITERS, WORK, log, outdir
from verify import probe


def main():
    ids = sys.argv[1:] or [r[0] for r in RECITERS]
    bad = 0
    for rid in ids:
        p = os.path.join(WORK, "previews", f"{rid}.mp3")
        if not os.path.exists(p):
            log(f"preview {rid}: MISSING")
            bad += 1
            continue
        dur, err = probe(p)
        parts = []
        for g in (1, 2):
            d, e = probe(os.path.join(outdir(rid), f"{g}.mp3"))
            parts.append(d or 0)
        expected = sum(parts)
        ok = err is None and abs(dur - expected) < 0.5
        log(f"preview {rid}: {os.path.getsize(p):,} bytes, {dur if dur else '?'}s "
            f"vs expected {expected:.2f}s ({parts[0]:.2f}+{parts[1]:.2f}) "
            f"-> {'ok' if ok else 'FAIL ' + str(err)}")
        bad += 0 if ok else 1
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
