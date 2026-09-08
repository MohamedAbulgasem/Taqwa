#!/usr/bin/env python3
"""Prepare the Notification-level prayer tone from its source recording.

Source: "Clear announce tones" (mixkit-clear-announce-tones-2861.wav) from Mixkit,
https://mixkit.co/free-sound-effects/tones/, under the Mixkit Sound Effects Free License (use in
apps allowed, no attribution required; not to be redistributed on its own). Kept in
assets/audio/source/ so this step is reproducible.

Processing: fold to mono, trim leading and trailing silence (below -60 dBFS) leaving 40 ms of lead
and a 300 ms tail with a fade, normalise the peak to -1 dBFS, write assets/audio/chime.wav, then
chime.ogg (Vorbis q6, needs ffmpeg) and chime.caf (IMA4, needs macOS afconvert) and copy both into
the platform trees. Same file names as before, so no Xcode project edit; Android's channel id suffix
must be bumped whenever this file changes (NotificationChannels), because a channel keeps the sound
it was created with.

    python3 tools/prepare-chime.py
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets" / "audio" / "source" / "mixkit-clear-announce-tones-2861.wav"
OUT_DIR = ROOT / "assets" / "audio"
ANDROID_RAW = ROOT / "shared" / "src" / "androidMain" / "res" / "raw" / "chime.ogg"
IOS_RES = ROOT / "iosApp" / "iosApp" / "Resources" / "chime.caf"

PEAK_DBFS = -1.0
SILENCE_DBFS = -60.0
LEAD_S = 0.04
TAIL_S = 0.30


def read_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path)) as w:
        channels, width, rate, frames = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(frames)
    dtype = {1: np.int8, 2: np.int16, 4: np.int32}[width]
    x = np.frombuffer(raw, dtype=dtype).astype(np.float64) / float(2 ** (8 * width - 1))
    if channels > 1:
        x = x.reshape(-1, channels).mean(axis=1)
    return x, rate


def write_wav(path: Path, x: np.ndarray, rate: int) -> None:
    pcm = np.clip(np.round(x * 32767.0), -32768, 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())


def prepare(x: np.ndarray, rate: int) -> np.ndarray:
    threshold = 10 ** (SILENCE_DBFS / 20)
    loud = np.where(np.abs(x) > threshold)[0]
    start = max(int(loud[0] - LEAD_S * rate), 0)
    end = min(int(loud[-1] + TAIL_S * rate), len(x))
    y = x[start:end].copy()
    fade = int(TAIL_S * rate)
    y[-fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    y *= 10 ** (PEAK_DBFS / 20) / np.abs(y).max()
    return y


def run(cmd: list[str]) -> bool:
    return subprocess.run(cmd, capture_output=True).returncode == 0


def main() -> None:
    x, rate = read_wav(SOURCE)
    y = prepare(x, rate)
    wav = OUT_DIR / "chime.wav"
    write_wav(wav, y, rate)
    peak = 20 * np.log10(np.abs(y).max())
    print(f"chime.wav: {len(y) / rate:.2f} s, mono {rate} Hz, peak {peak:.1f} dBFS")

    ogg = OUT_DIR / "chime.ogg"
    if shutil.which("ffmpeg") and run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "6", str(ogg)]):
        shutil.copy(ogg, ANDROID_RAW)
        print(f"chime.ogg: {ogg.stat().st_size / 1024:.0f} KB, copied to {ANDROID_RAW.relative_to(ROOT)}")
    else:
        print("ffmpeg missing or failed; chime.ogg not written", file=sys.stderr)

    caf = OUT_DIR / "chime.caf"
    if shutil.which("afconvert") and run(["afconvert", "-f", "caff", "-d", "ima4", str(wav), str(caf)]):
        shutil.copy(caf, IOS_RES)
        print(f"chime.caf: {caf.stat().st_size / 1024:.0f} KB, copied to {IOS_RES.relative_to(ROOT)}")
    else:
        print("afconvert missing or failed; chime.caf not written", file=sys.stderr)


if __name__ == "__main__":
    main()
