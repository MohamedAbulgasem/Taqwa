"""Are two MP3s the same recording? A loudness-envelope cross-correlation, in pure Python.

    from same_take import same_take
    peak = same_take(path_a, path_b)      # 0..1; the same take scores above 0.8

The two published bit-rate folders of a reciter are the same recording trimmed
differently - Al-Ajmi's 64 kbps 2:255 is 43.5 s and his 128 kbps one 46.1 s -
so lengths cannot prove a match and byte or decoded-sample identity cannot
either. What survives re-encoding and trimming is the shape of the loudness
over time: each file is decoded to 8 kHz mono, squared and averaged over 20 ms
windows, and the two envelopes are correlated over every lag within five
seconds. Two takes of the same words by the same voice still differ in pace and
score well under half; the same take scores above 0.9. No numpy: three control
ayahs a reciter is about two million multiplications, a second of Python.
"""

import math
import subprocess

FFMPEG = "/opt/homebrew/bin/ffmpeg"
RATE = 8000
WINDOW = 160          # 20 ms at 8 kHz
MAX_LAG_S = 5.0
SAME_TAKE = 0.6


def envelope(path):
    """RMS loudness per 20 ms window, as a list of floats."""
    out = subprocess.run(
        [FFMPEG, "-v", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", str(RATE), "-"],
        capture_output=True, timeout=120,
    )
    if out.returncode != 0 or not out.stdout:
        raise ValueError(f"cannot decode {path}")
    pcm = memoryview(out.stdout).cast("h")
    env = []
    for i in range(0, len(pcm) - WINDOW + 1, WINDOW):
        acc = 0
        for s in pcm[i:i + WINDOW]:
            acc += s * s
        env.append(math.sqrt(acc / WINDOW))
    return env


def _normalise(env):
    n = len(env)
    mean = sum(env) / n
    centred = [e - mean for e in env]
    norm = math.sqrt(sum(c * c for c in centred)) or 1.0
    return [c / norm for c in centred]


def peak_correlation(a, b):
    """The largest normalised cross-correlation of envelopes a and b over lags within MAX_LAG_S."""
    a = _normalise(a)
    b = _normalise(b)
    max_lag = int(MAX_LAG_S * RATE / WINDOW)
    best = 0.0
    for lag in range(-max_lag, max_lag + 1):
        if lag >= 0:
            pairs = zip(a[lag:], b)
        else:
            pairs = zip(a, b[-lag:])
        c = 0.0
        for x, y in pairs:
            c += x * y
        if c > best:
            best = c
    return best


def same_take(path_a, path_b):
    return peak_correlation(envelope(path_a), envelope(path_b))


if __name__ == "__main__":
    import sys
    print(f"{same_take(sys.argv[1], sys.argv[2]):.3f}")
