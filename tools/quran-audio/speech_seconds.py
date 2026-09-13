"""How much of each file is voice rather than silence, for two editions of a reciter.

    python3 speech_seconds.py <reciter id> <other corpus dir> <compare log>

For every ayah the compare log listed as differing in length, decode both
editions' files, count the 20 ms windows whose loudness is above a tenth of the
file's loudest window, and print voice seconds side by side. Padding shows as
equal voice under unequal length; a mis-cut file (an ayah file that carries the
next ayah too) shows as more voice.
"""

import concurrent.futures as cf
import os
import re
import sys

from common import log, outdir
from same_take import envelope

THRESHOLD = 0.1
WINDOW_S = 0.02


def voice_seconds(path):
    env = envelope(path)
    peak = max(env) or 1.0
    return sum(1 for e in env if e > peak * THRESHOLD) * WINDOW_S, len(env) * WINDOW_S


def main():
    rid, other_dir, logfile = sys.argv[1], sys.argv[2], sys.argv[3]
    ayahs = [int(m.group(1)) for m in re.finditer(r"  ayah (\d+): mine", open(logfile).read())]
    mine_dir = outdir(rid)

    def one(n):
        a = os.path.join(mine_dir, f"{n}.mp3")
        b = os.path.join(other_dir, f"{n}.mp3")
        try:
            va, la = voice_seconds(a)
            vb, lb = voice_seconds(b)
        except Exception as e:  # noqa: BLE001
            return n, None, str(e)
        return n, (va, la, vb, lb), None

    suspect = []
    with cf.ThreadPoolExecutor(max_workers=8) as ex:
        for n, r, err in ex.map(one, ayahs):
            if err:
                log(f"  ayah {n}: {err}")
                continue
            va, la, vb, lb = r
            flag = ""
            if va > vb * 1.5 + 1.0:
                flag = "  <- MINE HAS MORE VOICE"
                suspect.append(n)
            elif vb > va * 1.5 + 1.0:
                flag = "  <- other has more voice"
            log(f"  ayah {n}: mine voice {va:.1f}s of {la:.1f}s   other voice {vb:.1f}s of {lb:.1f}s{flag}")
    log(f"speech {rid}: {len(ayahs)} compared, {len(suspect)} where mine carries more voice than other: {suspect}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
