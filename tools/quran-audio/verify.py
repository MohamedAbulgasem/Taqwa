"""ffprobe every downloaded ayah of a reciter.

    python3 verify.py <reciter id> [workers]

A file passes when ffprobe parses it, it carries an audio stream, and its
duration is longer than 0.3 s.  Failures are deleted so that a re-run of
fetch.py pulls them again; verify.py exits non-zero while any file is bad.
"""

import concurrent.futures as cf
import json
import os
import subprocess
import sys
import time

from common import log, outdir

FFPROBE = "/opt/homebrew/bin/ffprobe"
MIN_DURATION = 0.3


def probe(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-show_entries",
             "format=duration:stream=codec_type", "-of", "json", path],
            capture_output=True, timeout=60,
        )
        if out.returncode != 0:
            return None, f"ffprobe rc={out.returncode} {out.stderr.decode()[:120]}"
        d = json.loads(out.stdout)
        streams = d.get("streams", [])
        if not any(s.get("codec_type") == "audio" for s in streams):
            return None, "no audio stream"
        dur = float(d.get("format", {}).get("duration", 0) or 0)
        if dur <= MIN_DURATION:
            return None, f"duration {dur:.3f}s"
        return dur, None
    except Exception as e:  # noqa: BLE001
        return None, f"{type(e).__name__}: {e}"


def main():
    rid = sys.argv[1]
    workers = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    out = outdir(rid)
    t0 = time.time()
    bad, total_dur, absent = [], 0.0, []

    def one(n):
        path = os.path.join(out, f"{n}.mp3")
        if not os.path.exists(path):
            return n, None, "absent"
        return (n,) + probe(path)

    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        for i, (n, dur, err) in enumerate(ex.map(one, range(1, 6237)), 1):
            if err == "absent":
                absent.append(n)
            elif err:
                bad.append((n, err))
            else:
                total_dur += dur
            if i % 2000 == 0:
                log(f"  verify {rid} {i}/6236 bad={len(bad)} absent={len(absent)}")

    for n, err in bad:
        log(f"  verify {rid} ayah {n}: {err} (deleting)")
        try:
            os.remove(os.path.join(out, f"{n}.mp3"))
        except OSError:
            pass

    log(f"verify {rid}: {6236 - len(bad) - len(absent)}/6236 good, "
        f"{len(bad)} bad (deleted), {len(absent)} absent, "
        f"audio {total_dur / 3600:.2f} h, {time.time() - t0:.0f}s")
    return 0 if not bad and not absent else 1


if __name__ == "__main__":
    sys.exit(main())
