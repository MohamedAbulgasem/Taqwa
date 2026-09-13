"""Create a reciter's GitHub Release and upload its 114 containers.

    python3 publish.py <reciter id> [batch size]

Uploads in batches, retries a failed batch file by file, then verifies with
`gh release view --json assets` that all 114 assets are present with exactly the
byte sizes on disk.  Resumable and cheap to re-run: an asset already on the
release with the right size is not uploaded again, which matters because the
uplink here is the slowest part of the whole pipeline.
"""

import json
import os
import subprocess
import sys
import time

from common import REPO, WORK, log, packdir, reciter

GH = "/opt/homebrew/bin/gh"

NOTES = (
    "Per-ayah recitation of the complete Qur'an by {name} ({style}, {kbps} kbps), "
    "mirrored from the Islamic Network / alquran.cloud per-ayah audio corpus "
    "(cdn.islamic.network). The MP3 bytes are unmodified — no re-encoding, no "
    "re-tagging — and are packed one file per surah in the .taqa container "
    "described in the repository README, so that what is redistributed is exactly "
    "what is licensed. Licence: free, non-commercial redistribution per "
    "https://alquran.cloud/terms-and-conditions §IV; copyright in the recitation "
    "remains with the reciter or their estate, and any release here will be "
    "withdrawn on request from the reciter, their estate or the Islamic Network."
)


def gh(args, retries=1, **kw):
    """Run gh. Read-only calls are retried: api.github.com resets connections
    intermittently from here, and a lost `release view` must not be read as a
    failed release."""
    for attempt in range(retries):
        r = subprocess.run([GH, *args], capture_output=True, text=True, **kw)
        if r.returncode == 0 or attempt == retries - 1:
            return r
        time.sleep(5 * (attempt + 1))
    return r


def release_exists(tag):
    return gh(["release", "view", tag, "--repo", REPO], retries=4).returncode == 0


def existing_assets(tag):
    r = gh(["release", "view", tag, "--repo", REPO, "--json", "assets"], retries=4)
    if r.returncode != 0:
        return {}
    return {a["name"]: a["size"] for a in json.loads(r.stdout)["assets"]}


def main():
    rid = sys.argv[1]
    batch = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    _, _, kbps, _, _, name_en, _ = reciter(rid)
    tag = f"audio-{rid}-v1"
    d = packdir(rid)
    meta = json.load(open(os.path.join(d, "surahs.json")))
    files = [os.path.join(d, f"{rid}-{s['n']:03d}.taqa") for s in meta["surahs"]]
    if len(files) != 114:
        raise SystemExit(f"{rid}: expected 114 containers, found {len(files)}")

    t0 = time.time()
    if release_exists(tag):
        log(f"publish {rid}: release {tag} already exists, resuming upload")
    else:
        r = gh(["release", "create", tag, "--repo", REPO,
                "--title", f"{name_en} (murattal, {kbps} kbps)",
                "--notes", NOTES.format(name=name_en, style="murattal", kbps=kbps)])
        if r.returncode != 0:
            raise SystemExit(f"{rid}: release create failed: {r.stderr}")
        log(f"publish {rid}: created release {tag}")

    have = existing_assets(tag)
    todo = [f for f, s in zip(files, meta["surahs"])
            if have.get(os.path.basename(f)) != s["bytes"]]
    if len(todo) != len(files):
        log(f"publish {rid}: {len(files) - len(todo)} of 114 already uploaded, "
            f"{len(todo)} to go")
    files = todo

    for i in range(0, len(files), batch):
        chunk = files[i:i + batch]
        for attempt in range(4):
            r = gh(["release", "upload", tag, *chunk, "--repo", REPO, "--clobber"])
            if r.returncode == 0:
                break
            log(f"  upload {rid} batch {i // batch} attempt {attempt} failed: "
                f"{r.stderr.strip()[:200]}")
            time.sleep(5 * (attempt + 1))
        else:
            for f in chunk:  # last resort: one file at a time
                for attempt in range(4):
                    r = gh(["release", "upload", tag, f, "--repo", REPO, "--clobber"])
                    if r.returncode == 0:
                        break
                    time.sleep(5 * (attempt + 1))
                else:
                    raise SystemExit(f"{rid}: giving up on {f}: {r.stderr}")
        log(f"  upload {rid} {min(i + batch, len(files))}/{len(files)} of this run "
            f"({time.time() - t0:.0f}s)")

    # Verify: every container present, with the right size.
    r = gh(["release", "view", tag, "--repo", REPO, "--json", "assets"], retries=6)
    if r.returncode != 0:
        raise SystemExit(f"{rid}: release view failed: {r.stderr}")
    assets = {a["name"]: a["size"] for a in json.loads(r.stdout)["assets"]}
    problems = []
    for s in meta["surahs"]:
        n = f"{rid}-{s['n']:03d}.taqa"
        if n not in assets:
            problems.append(f"{n} missing")
        elif assets[n] != s["bytes"]:
            problems.append(f"{n} size {assets[n]} != {s['bytes']}")
    if problems:
        log(f"publish {rid}: VERIFY FAILED: {problems}")
        return 1
    os.makedirs(os.path.join(WORK, "state"), exist_ok=True)
    with open(os.path.join(WORK, "state", f"{rid}.done"), "w") as f:
        f.write(f"{len(assets)} assets, {meta['totalBytes']} bytes\n")
    log(f"publish {rid}: verified {len(assets)} assets, "
        f"{meta['totalBytes']:,} bytes, {time.time() - t0:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
