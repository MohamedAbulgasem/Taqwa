#!/usr/bin/env python3
"""Generate Taqwa's own notification chime (`assets/audio/chime.{wav,ogg,caf}`).

Original synthesis, no samples and no third-party material: additive bell tones built from
inharmonic partials with independent exponential decays, the recipe first written down in
`docs/BUILD-LOG.md`.

What it makes now (iteration: "a chime you notice at work"): a three-note ascending motif —
A4, C#5, E5 — struck once, left to ring, then struck again after a gap, six seconds end to end
and peaking at -1 dBFS. The earlier chime was two soft strikes over 2.4 s at -5 dBFS, which the
owner kept missing for Dhuhr and Asr in an office; this is longer, louder and brighter, but it is
still a bell rather than an alarm: the same partial structure, only with a stronger and
slower-decaying second partial (the octave) to carry the sound across a room.

Usage:
    python3 tools/make-chime.py            # write wav, ogg, caf and copy to the platform trees
    python3 tools/make-chime.py --wav-only # just assets/audio/chime.wav

`.ogg` needs ffmpeg, `.caf` needs macOS `afconvert`; either being absent is reported, not fatal.
"""

from __future__ import annotations

import argparse
import math
import shutil
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np

SAMPLE_RATE = 44_100

# Equal-tempered A4, C#5, E5 — an A major triad, ascending. The ear reads a rising figure as
# "attend to this" where a falling one reads as "that's done".
NOTES = (440.000, 554.365, 659.255)

# Bell partials: ratios deliberately off the harmonic series (2.98, 4.21, 5.4) so the tone is a
# struck bell and not an organ. The octave partial (index 1) is where "bright" lives: it is
# louder and decays far more slowly than in the 2.4 s chime, which is what makes this one
# audible over an open-plan office.
PARTIAL_RATIOS = (1.0, 2.0, 2.98, 4.21, 5.40)
PARTIAL_AMPS = (1.00, 0.58, 0.18, 0.080, 0.035)
PARTIAL_DECAYS = (1.55, 1.05, 0.55, 0.30, 0.180)  # seconds, e-folding

DETUNE = 0.0012  # ±0.12 %: two copies a hair apart beat slowly and sound warm rather than sterile
ATTACK = 0.006  # 6 ms — a strike, with no click

NOTE_GAP = 0.26  # seconds between the notes of one motif
STRIKE_2_AT = 2.60  # seconds: long enough to read as a second ring, not an echo
STRIKE_2_LEVEL = 0.85

DURATION = 6.00
TAIL_FADE = 0.35  # a fade rather than a cut, so the file does not end on a step
PEAK_DBFS = -1.0


def strike(freq: float, offset: int, out: np.ndarray, level: float) -> None:
    """Add one struck bell tone at `freq`, scaled by `level`, starting at sample `offset`."""
    n = out.size - offset
    if n <= 0:
        return
    t = np.arange(n, dtype=np.float64) / SAMPLE_RATE
    attack = np.minimum(t / ATTACK, 1.0)
    voice = np.zeros(n, dtype=np.float64)
    for ratio, amp, decay in zip(PARTIAL_RATIOS, PARTIAL_AMPS, PARTIAL_DECAYS):
        envelope = amp * np.exp(-t / decay)
        for detune in (1.0 - DETUNE, 1.0 + DETUNE):
            voice += 0.5 * envelope * np.sin(2.0 * math.pi * freq * ratio * detune * t)
    out[offset:] += level * attack * voice


def render() -> np.ndarray:
    total = int(DURATION * SAMPLE_RATE)
    buf = np.zeros(total, dtype=np.float64)
    for strike_at, level in ((0.0, 1.0), (STRIKE_2_AT, STRIKE_2_LEVEL)):
        for i, freq in enumerate(NOTES):
            strike(freq, int((strike_at + i * NOTE_GAP) * SAMPLE_RATE), buf, level)

    fade = int(TAIL_FADE * SAMPLE_RATE)
    buf[total - fade:] *= np.linspace(1.0, 0.0, fade) ** 2

    peak = float(np.max(np.abs(buf)))
    return buf * (10.0 ** (PEAK_DBFS / 20.0) / peak)


def write_wav(path: Path, samples: np.ndarray) -> None:
    pcm = np.round(np.clip(samples, -1.0, 1.0) * 32767.0).astype("<i2")
    data = pcm.tobytes()
    header = (
        b"RIFF"
        + struct.pack("<I", 36 + len(data))
        + b"WAVEfmt "
        + struct.pack("<IHHIIHH", 16, 1, 1, SAMPLE_RATE, SAMPLE_RATE * 2, 2, 16)
        + b"data"
        + struct.pack("<I", len(data))
    )
    path.write_bytes(header + data)


def run(cmd: list) -> bool:
    print("  $ " + " ".join(cmd))
    return subprocess.run(cmd, capture_output=True).returncode == 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--wav-only", action="store_true", help="skip .ogg/.caf and the copies")
    args = ap.parse_args()

    root = Path(__file__).resolve().parent.parent
    audio = root / "assets" / "audio"
    wav, ogg, caf = audio / "chime.wav", audio / "chime.ogg", audio / "chime.caf"

    samples = render()
    write_wav(wav, samples)
    peak_db = 20.0 * math.log10(float(np.max(np.abs(samples))))
    print("%s: %.2f s, peak %+.2f dBFS" % (wav, samples.size / SAMPLE_RATE, peak_db))
    if args.wav_only:
        return 0

    ok = True
    if shutil.which("ffmpeg"):
        # -q:a 6 rather than the adhan clips' 4: this is a short tone whose brightness is the
        # point, and the file is a few tens of kB either way.
        if run(["ffmpeg", "-y", "-i", str(wav), "-ac", "1", "-ar", str(SAMPLE_RATE),
                "-c:a", "libvorbis", "-q:a", "6", str(ogg)]):
            shutil.copyfile(ogg, root / "shared/src/androidMain/res/raw/chime.ogg")
        else:
            ok = False
            print("ffmpeg failed; chime.ogg not written", file=sys.stderr)
    else:
        ok = False
        print("ffmpeg not found; chime.ogg not written", file=sys.stderr)

    if shutil.which("afconvert"):
        if run(["afconvert", "-f", "caff", "-d", "LEI16", str(wav), str(caf)]):
            shutil.copyfile(caf, root / "iosApp/iosApp/Resources/chime.caf")
        else:
            ok = False
            print("afconvert failed; chime.caf not written", file=sys.stderr)
    else:
        ok = False
        print("afconvert not found (macOS only); chime.caf not written", file=sys.stderr)

    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
