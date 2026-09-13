"""Compare a reciter's two bit-rate editions ayah by ayah, by decoded length.

    python3 compare_editions.py <reciter id> <other corpus dir>

Prints every ayah whose decoded length differs by more than TOLERANCE_S between
the reciter's current corpus (state/durations-<id>.json, as verify.py measured
it) and the other edition's files in <other corpus dir>, with the other
reciters' median for that ayah, so a human can see which edition is the sane
one. Writes state/durations-<id>-other.json for the other edition.
"""

import concurrent.futures as cf
import json
import os
import sys

from common import WORK, log
from verify import load_durations, measure, reference_durations

TOLERANCE_S = 3.0


def main():
    rid, other_dir = sys.argv[1], sys.argv[2]
    mine = load_durations(rid)
    ref = reference_durations(rid)
    paths = {n: os.path.join(other_dir, f"{n}.mp3") for n in range(1, 6237)}

    def one(n):
        p = paths[n]
        if not os.path.exists(p):
            return n, None
        ms, err = measure(p)
        return n, (None if err else ms)

    other = {}
    with cf.ThreadPoolExecutor(max_workers=8) as ex:
        for n, ms in ex.map(one, range(1, 6237)):
            if ms is not None:
                other[n] = ms
    os.makedirs(os.path.join(WORK, "state"), exist_ok=True)
    with open(os.path.join(WORK, "state", f"durations-{rid}-other.json"), "w") as f:
        json.dump({str(n): ms for n, ms in sorted(other.items())}, f)

    differ = []
    for n in range(1, 6237):
        a, b = mine.get(n), other.get(n)
        if a is None or b is None:
            continue
        if abs(a - b) > TOLERANCE_S * 1000:
            differ.append((n, a, b, ref.get(n)))
    log(f"compare {rid}: {len(mine)} mine, {len(other)} other, {len(differ)} differ by more than {TOLERANCE_S}s")
    for n, a, b, r in differ:
        log(f"  ayah {n}: mine {a / 1000:.1f}s  other {b / 1000:.1f}s  others' median {(r or 0) / 1000:.1f}s")
    missing_mine = [n for n in range(1, 6237) if n not in mine]
    log(f"compare {rid}: {len(missing_mine)} absent from mine; of those {sum(1 for n in missing_mine if n in other)} present in other")
    return 0


if __name__ == "__main__":
    sys.exit(main())
