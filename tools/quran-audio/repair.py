"""Repair the ayahs a reciter's CDN directory refuses to serve.

    python3 repair.py <reciter id> [rounds] [--fallback] [--substitute] [--only=N,N,...]

--only names global ayahs to treat as absent whatever is on disk - the files are
removed first - for the case a human has found a file that plays and passes
every automatic gate and is still wrong (an ayah file carrying the next ayah
too). With --substitute and rounds 0 it takes exactly those from the other
bit-rate folder.

--substitute is the last resort after the fallback, and is never passed by
finish.py: a human asks for it. An ayah that is broken at source in every copy
of the reciter's edition at this bit-rate is taken from the CDN's *other*
published bit-rate folder for the same reciter (Al-Ajmi's 9:62 and 50:10 are an
MPEG-video fragment and a quarter-second stub at 128 kbps everywhere, and whole
at 64). The licence covers every bit-rate the Islamic Network publishes, and the
index carries each ayah's measured length, so a mixed container plays and clocks
correctly. The other folder is first shown to be the same take on three control
ayahs by loudness-envelope cross-correlation (same_take.py; the folders trim
differently, so lengths prove nothing), each substituted file passes the same
gate as any other, and every substitution is logged in capitals for someone to
listen to before the reciter ships.

Some objects on cdn.islamic.network answer 502 persistently rather than 403/404:
the object exists in the catalogue but the origin cannot read it.  This retries
those over a long window, and with --fallback falls back to everyayah.com, which
serves the *same encode* — the Islamic Network corpus is everyayah's, re-tagged,
so the decoded audio is bit-identical at the same published bitrate (verified per
reciter by `--check`, which compares three control ayahs we already hold).

Nothing is re-encoded: the fallback file is written byte for byte as served.
"""

import hashlib
import os
import subprocess
import sys
import time
import urllib.request

from common import UA, log, outdir, reciter, surah_ranges
from same_take import SAME_TAKE, same_take
from verify import probe, reference_durations

# Islamic Network edition -> candidate everyayah directories at the same true
# bitrate.  The first candidate whose decoded audio matches three ayahs we
# already hold is used; if none matches, no fallback happens.
EVERYAYAH = {
    "ar.alafasy": ["Alafasy_64kbps"],
    "ar.abdulbasitmurattal": ["Abdul_Basit_Murattal_64kbps"],
    "ar.husary": ["Husary_64kbps"],
    "ar.minshawi": ["Minshawy_Murattal_128kbps"],
    "ar.abdurrahmaansudais": ["Abdurrahmaan_As-Sudais_64kbps"],
    "ar.saoodshuraym": ["Saood_ash-Shuraym_128kbps", "Saood_ash-Shuraym_64kbps"],
    "ar.shaatree": ["Abu_Bakr_Ash-Shaatree_128kbps"],
    "ar.ahmedajamy": ["ahmed_ibn_ali_al_ajamy_128kbps",
                      "Ahmed_ibn_Ali_al-Ajamy_128kbps_ketaballah.net"],
    "ar.hudhaify": ["Hudhaify_128kbps", "Hudhaify_64kbps"],
    "ar.mahermuaiqly": ["Maher_AlMuaiqly_64kbps", "MaherAlMuaiqly128kbps"],
}


def missing_list(rid):
    d = outdir(rid)
    return [n for n in range(1, 6237)
            if not (os.path.exists(os.path.join(d, f"{n}.mp3"))
                    and os.path.getsize(os.path.join(d, f"{n}.mp3")) > 1000)]


def g2sa(g, ranges):
    for s, (a, b) in ranges.items():
        if a <= g <= b:
            return s, g - a + 1
    raise ValueError(g)


def fetch(url, path, attempts=5):
    """Download to `path`, retrying transport errors with backoff.

    everyayah.com resets the connection when asked for files too quickly, so the
    fallback path is deliberately slow and patient rather than parallel.
    """
    err = None
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as r:
                body = r.read()
            if len(body) < 1000:
                raise ValueError(f"{len(body)} bytes")
            tmp = f"{path}.{os.getpid()}.part"
            with open(tmp, "wb") as f:
                f.write(body)
            os.replace(tmp, path)
            return
        except Exception as e:  # noqa: BLE001
            err = e
            time.sleep(3 * (attempt + 1))
    raise err


def decoded_md5(path):
    out = subprocess.run(
        ["/opt/homebrew/bin/ffmpeg", "-v", "error", "-i", path,
         "-f", "s16le", "-ac", "2", "-ar", "44100", "-"],
        capture_output=True)
    return hashlib.md5(out.stdout).hexdigest()


def check_equivalence(rid, ranges):
    """Return the everyayah directory that is the same encode, or None.

    Proves it on ayahs we already hold: the decoded audio must be MD5-identical,
    which is the case because the Islamic Network corpus is everyayah's with
    rewritten ID3 tags.
    """
    d = outdir(rid)
    for dirname in EVERYAYAH.get(rid, []):
        ok = True
        for g in (262, 1000, 5000):
            mine = os.path.join(d, f"{g}.mp3")
            if not os.path.exists(mine):
                ok = False
                break
            s, a = g2sa(g, ranges)
            tmp = os.path.join("/tmp", f"eq-{rid}-{g}.mp3")
            try:
                fetch(f"https://everyayah.com/data/{dirname}/{s:03d}{a:03d}.mp3", tmp)
            except Exception as e:  # noqa: BLE001
                log(f"  equivalence {rid}/{dirname}: cannot fetch control {g}: {e}")
                ok = False
                break
            same = decoded_md5(mine) == decoded_md5(tmp)
            os.remove(tmp)
            if not same:
                log(f"  equivalence {rid}/{dirname}: control {g} differs")
                ok = False
                break
        if ok:
            log(f"  equivalence {rid}: everyayah/{dirname} decodes identically "
                f"on 3 controls")
            return dirname
    log(f"  equivalence {rid}: no everyayah directory matches - fallback refused")
    return None


def other_bitrate_folder(rid):
    """The CDN folder of the reciter's other published bit-rate, or None."""
    folder = reciter(rid)[1]
    return {"128": "64", "64": "128"}.get(folder)


def check_other_bitrate(rid, other):
    """True when three control ayahs we hold are the same take as the other bit-rate
    folder's, by loudness-envelope cross-correlation (same_take.py): the two folders
    trim differently, so lengths cannot say, and re-encoding means bytes cannot either.
    A different take of the same words scores well under the bar."""
    d = outdir(rid)
    for g in (262, 1000, 5000):
        mine = os.path.join(d, f"{g}.mp3")
        if not os.path.exists(mine):
            return False
        tmp = os.path.join("/tmp", f"ob-{rid}-{g}.mp3")
        try:
            fetch(f"https://cdn.islamic.network/quran/audio/{other}/{rid}/{g}.mp3", tmp)
            score = same_take(mine, tmp)
        except Exception as e:  # noqa: BLE001
            log(f"  substitute {rid}/{other}: cannot compare control {g}: {e}")
            return False
        finally:
            if os.path.exists(tmp):
                os.remove(tmp)
        if score < SAME_TAKE:
            log(f"  substitute {rid}/{other}: control {g} is not the same take (envelope correlation {score:.2f})")
            return False
        log(f"  substitute {rid}/{other}: control {g} same take, correlation {score:.2f}")
    return True


def main():
    rid = sys.argv[1]
    args = sys.argv[2:]
    use_fallback = "--fallback" in args
    use_substitute = "--substitute" in args
    rounds = next((int(a) for a in args if a.isdigit()), 8)
    only = next((a for a in args if a.startswith("--only=")), None)
    folder = reciter(rid)[1]
    d = outdir(rid)
    ranges = surah_ranges()
    # Other reciters' measured lengths, so a fetched file is judged by the same rule
    # verify.py judges it by - a stub that plays is still a stub.
    reference = reference_durations(rid)

    if only:
        chosen = sorted({int(x) for x in only[len("--only="):].split(",") if x.strip()})
        for n in chosen:
            path = os.path.join(d, f"{n}.mp3")
            if os.path.exists(path):
                os.remove(path)
        log(f"repair {rid}: --only removed {len(chosen)} files a human judged wrong: {chosen}")
    todo = missing_list(rid)
    log(f"repair {rid}: {len(todo)} absent: {todo}")
    if len(todo) > 50:
        # A long list is usually a burst of transient failures from a fetch that
        # was pushing the CDN hard, not persistent 502s, so retry the CDN twice
        # with two attempts per file rather than grinding through many rounds.
        rounds = min(rounds, 2)
    for r in range(rounds):
        if not todo:
            break
        got = []
        for n in todo:
            path = os.path.join(d, f"{n}.mp3")
            try:
                fetch(f"https://cdn.islamic.network/quran/audio/{folder}/{rid}/{n}.mp3",
                      path, attempts=2)
                # The CDN can answer 200 with an object that is not an ayah - no audio
                # stream, or a quarter of a second of it (Ajmi 1297 and 4640). verify.py
                # would only delete it again and the fallback would never be reached, so
                # a broken object counts as absent here.
                dur, err = probe(path, reference.get(n))
                if err:
                    os.remove(path)
                    raise ValueError(err)
                got.append(n)
            except Exception:  # noqa: BLE001
                pass
        todo = [n for n in todo if n not in got]
        log(f"repair {rid} round {r}: recovered {len(got)}, {len(todo)} left")
        if todo and r < rounds - 1:
            time.sleep(60)

    if todo and use_fallback:
        dirname = check_equivalence(rid, ranges)
        if dirname:
            still = []
            for n in todo:
                s, a = g2sa(n, ranges)
                path = os.path.join(d, f"{n}.mp3")
                try:
                    fetch(f"https://everyayah.com/data/{dirname}/{s:03d}{a:03d}.mp3", path)
                    time.sleep(1)
                    dur, err = probe(path, reference.get(n))
                    if err:
                        os.remove(path)
                        raise ValueError(err)
                    log(f"  fallback {rid} ayah {n} ({s}:{a}) from everyayah/{dirname}: "
                        f"{os.path.getsize(path):,} bytes, {dur:.2f}s")
                except Exception as e:  # noqa: BLE001
                    log(f"  fallback {rid} ayah {n}: FAILED {e}")
                    still.append(n)
            todo = still

    if todo and use_substitute:
        other = other_bitrate_folder(rid)
        if other and check_other_bitrate(rid, other):
            still = []
            for n in todo:
                s, a = g2sa(n, ranges)
                path = os.path.join(d, f"{n}.mp3")
                try:
                    fetch(f"https://cdn.islamic.network/quran/audio/{other}/{rid}/{n}.mp3", path)
                    dur, err = probe(path, reference.get(n))
                    if err:
                        os.remove(path)
                        raise ValueError(err)
                    log(f"  SUBSTITUTE {rid} ayah {n} ({s}:{a}) taken from cdn/{other} kbps: "
                        f"{os.path.getsize(path):,} bytes, {dur:.2f}s - LISTEN TO IT before publishing")
                except Exception as e:  # noqa: BLE001
                    log(f"  substitute {rid} ayah {n}: FAILED {e}")
                    still.append(n)
            todo = still

    if todo:
        log(f"repair {rid}: STILL MISSING {todo} - reciter cannot ship")
        return 2
    log(f"repair {rid}: complete, no holes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
