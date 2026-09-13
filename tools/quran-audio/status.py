"""One-screen state of the whole pipeline.

    python3 status.py
"""

import json
import os
import subprocess

from common import RECITERS, WORK, outdir, packdir

GH = "/opt/homebrew/bin/gh"


def main():
    print(f"{'reciter':24} {'fetched':>8} {'packed':>7} {'GB':>6} {'release':>8} state")
    for rid, folder, kbps, *_ in RECITERS:
        d = outdir(rid)
        n = (sum(1 for f in os.listdir(d) if f.endswith(".mp3"))
             if os.path.isdir(d) else 0)
        pd = packdir(rid)
        packed = (len([f for f in os.listdir(pd) if f.endswith(".taqa")])
                  if os.path.isdir(pd) else 0)
        gb = 0.0
        sj = os.path.join(pd, "surahs.json")
        if os.path.exists(sj):
            gb = json.load(open(sj))["totalBytes"] / 1e9
        r = subprocess.run(
            [GH, "release", "view", f"audio-{rid}-v1", "--repo",
             "MohamedAbulgasem/Taqwa-data", "--json", "assets"],
            capture_output=True, text=True)
        assets = len(json.loads(r.stdout)["assets"]) if r.returncode == 0 else 0
        done = os.path.exists(os.path.join(WORK, "state", f"{rid}.done"))
        print(f"{rid:24} {n:>8} {packed:>7} {gb:>6.2f} {assets:>8} "
              f"{'published' if done else ''}")


if __name__ == "__main__":
    main()
