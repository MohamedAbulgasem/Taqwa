"""Decode every downloaded ayah of a reciter and judge it.

    python3 verify.py <reciter id> [workers] [--measure-only]

A file passes when ffmpeg decodes it to sound, the sound runs longer than 0.3 s,
its average bit-rate over the audio bytes is a plausible MP3 rate (24-400 kbps),
and, where other reciters' lengths are on hand, it is within a factor of three
of their median for the same ayah.  Failures are deleted so that a re-run of
fetch.py pulls them again; verify.py exits non-zero while any file is bad.

Why a full decode and not ffprobe's duration: a tenth of Al-Ajmi's 64 kbps files
carry a Xing header claiming fifteen times their real length, and ffprobe repeats
the header.  The decoded sample count is the truth, and it is also what the app
needs — every ayah's measured length goes into the container's index (pack.py),
so the player's clock is exact rather than estimated.

Lengths are written to `state/durations-<id>.json` (global ayah -> ms) whether
or not the reciter passes; `--measure-only` writes them and deletes nothing,
which is how a reciter already published contributes a reference.
"""

import concurrent.futures as cf
import json
import os
import statistics
import subprocess
import sys
import time

from common import WORK, log, outdir

FFPROBE = "/opt/homebrew/bin/ffprobe"
FFMPEG = "/opt/homebrew/bin/ffmpeg"
MIN_DURATION = 0.3
MIN_KBPS, MAX_KBPS = 24, 400
RATIO = 3.0

# ffmpeg decodes to 8 kHz mono signed 16-bit: 16 bytes of PCM per millisecond.
PCM_RATE = 8000
PCM_BYTES_PER_MS = PCM_RATE * 2 // 1000


def id3v2_bytes(path):
    """The ID3v2 tag at the head of the file, in bytes, or 0."""
    with open(path, "rb") as f:
        b = f.read(10)
    if len(b) < 10 or b[:3] != b"ID3":
        return 0
    size = ((b[6] & 0x7F) << 21) | ((b[7] & 0x7F) << 14) | ((b[8] & 0x7F) << 7) | (b[9] & 0x7F)
    return 10 + size + (10 if b[5] & 0x10 else 0)


def measure(path):
    """(decoded length in ms, None) or (None, why it could not be decoded)."""
    try:
        out = subprocess.run(
            [FFMPEG, "-v", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", str(PCM_RATE), "-"],
            capture_output=True, timeout=120,
        )
    except Exception as e:  # noqa: BLE001
        return None, f"{type(e).__name__}: {e}"
    if out.returncode != 0:
        return None, f"ffmpeg rc={out.returncode} {out.stderr.decode()[:120].strip()}"
    if not out.stdout:
        return None, "no audio stream"
    return len(out.stdout) // PCM_BYTES_PER_MS, None


def judge(path, ms, reference_ms=None):
    """None when the decoded file is a plausible ayah, else the reason it is not."""
    dur = ms / 1000.0
    if dur <= MIN_DURATION:
        return f"duration {dur:.3f}s"
    audio_bytes = os.path.getsize(path) - id3v2_bytes(path)
    kbps = audio_bytes * 8.0 / max(ms, 1)
    if not MIN_KBPS <= kbps <= MAX_KBPS:
        return f"{kbps:.0f} kbps average over {dur:.1f}s"
    if reference_ms and not (reference_ms / RATIO <= ms <= reference_ms * RATIO):
        return f"{dur:.1f}s where other reciters take {reference_ms / 1000:.1f}s"
    return None


def probe(path, reference_ms=None):
    """(duration in seconds, None) or (None, reason). The one gate every script uses."""
    ms, err = measure(path)
    if err:
        return None, err
    err = judge(path, ms, reference_ms)
    return (None, err) if err else (ms / 1000.0, None)


def durations_path(rid):
    return os.path.join(WORK, "state", f"durations-{rid}.json")


def load_durations(rid):
    p = durations_path(rid)
    if not os.path.exists(p):
        return {}
    return {int(k): v for k, v in json.load(open(p)).items()}


def reference_durations(exclude_rid):
    """Global ayah -> median decoded ms across every other reciter measured so far."""
    state = os.path.join(WORK, "state")
    per_ayah = {}
    if not os.path.isdir(state):
        return {}
    for name in os.listdir(state):
        if not (name.startswith("durations-") and name.endswith(".json")):
            continue
        rid = name[len("durations-"):-len(".json")]
        if rid == exclude_rid:
            continue
        for k, v in json.load(open(os.path.join(state, name))).items():
            per_ayah.setdefault(int(k), []).append(v)
    return {n: statistics.median(v) for n, v in per_ayah.items()}


def main():
    rid = sys.argv[1]
    args = sys.argv[2:]
    measure_only = "--measure-only" in args
    workers = next((int(a) for a in args if a.isdigit()), 12)
    out = outdir(rid)
    t0 = time.time()
    reference = reference_durations(rid)

    def one(n):
        path = os.path.join(out, f"{n}.mp3")
        if not os.path.exists(path):
            return n, None, "absent"
        ms, err = measure(path)
        return n, ms, err

    measured, bad, absent = {}, [], []
    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        for i, (n, ms, err) in enumerate(ex.map(one, range(1, 6237)), 1):
            if err == "absent":
                absent.append(n)
            elif err:
                bad.append((n, err))
            else:
                measured[n] = ms
                verdict = judge(os.path.join(out, f"{n}.mp3"), ms, reference.get(n))
                if verdict:
                    bad.append((n, verdict))
            if i % 2000 == 0:
                log(f"  verify {rid} {i}/6236 bad={len(bad)} absent={len(absent)}")

    os.makedirs(os.path.join(WORK, "state"), exist_ok=True)
    good = {n: ms for n, ms in measured.items() if n not in {b for b, _ in bad}}
    with open(durations_path(rid), "w") as f:
        json.dump({str(n): ms for n, ms in sorted(good.items())}, f)

    total_dur = sum(good.values()) / 1000.0
    if measure_only:
        log(f"verify {rid}: measured {len(good)}/6236, {len(bad)} would fail, {len(absent)} absent, "
            f"audio {total_dur / 3600:.2f} h, reference from {'other reciters' if reference else 'nothing'}, "
            f"{time.time() - t0:.0f}s (measure only, nothing deleted)")
        for n, err in bad[:40]:
            log(f"  verify {rid} ayah {n}: {err}")
        return 0

    for n, err in bad:
        log(f"  verify {rid} ayah {n}: {err} (deleting)")
        try:
            os.remove(os.path.join(out, f"{n}.mp3"))
        except OSError:
            pass

    log(f"verify {rid}: {len(good)}/6236 good, {len(bad)} bad (deleted), {len(absent)} absent, "
        f"audio {total_dur / 3600:.2f} h, reference from {len(reference)} ayahs of other reciters, "
        f"{time.time() - t0:.0f}s")
    return 0 if not bad and not absent else 1


if __name__ == "__main__":
    sys.exit(main())
