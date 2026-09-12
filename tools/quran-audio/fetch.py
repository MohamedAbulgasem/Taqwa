"""Fetch one reciter's 6,236 per-ayah MP3s from the Islamic Network CDN.

    python3 fetch.py <reciter id> [workers]

Resumable: a file that already exists and is larger than 1000 bytes is skipped.
Transport errors (502, timeouts, resets) are retried with exponential backoff.
A 403/404 that survives its retries is a genuine hole in the corpus: it is
recorded in `<outdir>.missing.json` and the fetch STOPS for that reciter, because
a Quran app must never ship a surah with a silent gap.

The CDN answers a missing object with a 271-byte Ceph AccessDenied XML body and
an HTTP 403, so both the status code and the MP3 magic bytes are checked.
"""

import concurrent.futures as cf
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

from common import UA, log, outdir, reciter

MIN_BYTES = 1000


def looks_like_mp3(b):
    if len(b) < MIN_BYTES:
        return False
    if b[:3] == b"ID3":
        return True
    return b[0] == 0xFF and (b[1] & 0xE0) == 0xE0


def main():
    rid = sys.argv[1]
    workers = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    folder = reciter(rid)[1]
    out = outdir(rid)
    os.makedirs(out, exist_ok=True)

    stop = threading.Event()
    missing, lock = [], threading.Lock()
    counts = {"have": 0, "ok": 0, "fail": 0}

    def get(n):
        path = os.path.join(out, f"{n}.mp3")
        if os.path.exists(path) and os.path.getsize(path) > MIN_BYTES:
            return n, "have"
        if stop.is_set():
            return n, "skip"
        url = f"https://cdn.islamic.network/quran/audio/{folder}/{rid}/{n}.mp3"
        gone = 0
        err = None
        for attempt in range(6):
            if stop.is_set():
                return n, "skip"
            try:
                req = urllib.request.Request(url, headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=90) as r:
                    if r.status != 200:
                        raise urllib.error.HTTPError(url, r.status, "bad status", r.headers, None)
                    body = r.read()
                if not looks_like_mp3(body):
                    raise ValueError(f"not an mp3 ({len(body)} bytes)")
                tmp = f"{path}.{os.getpid()}.part"
                with open(tmp, "wb") as f:
                    f.write(body)
                os.replace(tmp, path)
                return n, "ok"
            except urllib.error.HTTPError as e:
                err = e
                if e.code in (403, 404):
                    gone += 1
                    if gone >= 3:
                        with lock:
                            missing.append(n)
                        stop.set()
                        return n, f"MISSING {e.code}"
                time.sleep(2 * (attempt + 1))
            except Exception as e:  # noqa: BLE001 - transport errors of every shape
                err = e
                time.sleep(2 * (attempt + 1))
        return n, f"FAIL {err}"

    t0 = time.time()
    log(f"fetch {rid} /{folder} -> {out} ({workers} workers)")
    failures = []
    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        done = 0
        for n, st in ex.map(get, range(1, 6237)):
            done += 1
            if st.startswith("FAIL"):
                counts["fail"] += 1
                failures.append(n)
            elif st in counts:
                counts[st] += 1
            if done % 500 == 0:
                log(f"  {rid} {done}/6236 new={counts['ok']} had={counts['have']} "
                    f"fail={counts['fail']} {time.time() - t0:.0f}s")

    if missing:
        path = out + ".missing.json"
        json.dump(sorted(missing), open(path, "w"))
        log(f"STOP {rid}: ayah(s) absent from the CDN: {sorted(missing)} -> {path}")
        return 2
    if failures:
        log(f"{rid}: {len(failures)} transport failures remain "
            f"(re-run to retry): {failures[:20]}")
        return 1
    log(f"fetch {rid} complete: new={counts['ok']} had={counts['have']} "
        f"in {time.time() - t0:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
