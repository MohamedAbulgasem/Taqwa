"""Rebuild the manifest, place the previews, and commit the manifest.

    python3 finalize.py

Run after every successful publish, so the repository never advertises a
reciter whose release is not complete.  Writes:

  * manifest.json into the Taqwa-data clone and into the app's compose resources
  * the preview clips into the app's compose resources and assets/ copies

and commits *only* manifest.json in Taqwa-data.  Nothing in the Taqwa app
repository is committed — that is Mohamed's to do.
"""

import os
import shutil
import subprocess
import sys

from common import RECITERS, WORK, log

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.environ.get(
    "TAQWA_APP_REPO", "/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa")
DATA = os.path.join(WORK, "Taqwa-data")

RES = os.path.join(APP, "shared/src/commonMain/composeResources/files/recitation")
ASSETS = os.path.join(APP, "assets/audio/recitation-previews")


def published():
    state = os.path.join(WORK, "state")
    return [r[0] for r in RECITERS
            if os.path.exists(os.path.join(state, f"{r[0]}.done"))]


def main():
    ids = published()
    rc = subprocess.run(
        [sys.executable, os.path.join(HERE, "manifest.py"),
         os.path.join(DATA, "manifest.json"),
         os.path.join(RES, "manifest.json")]).returncode
    if rc != 0:
        return rc

    os.makedirs(os.path.join(RES, "previews"), exist_ok=True)
    os.makedirs(ASSETS, exist_ok=True)
    for rid in ids:
        src = os.path.join(WORK, "previews", f"{rid}.mp3")
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(RES, "previews", f"{rid}.mp3"))
            shutil.copy2(src, os.path.join(ASSETS, f"{rid}.mp3"))
    log(f"finalize: {len(ids)} previews placed in the app resources and assets")

    subprocess.run(["git", "-C", DATA, "add", "manifest.json"], check=True)
    st = subprocess.run(["git", "-C", DATA, "status", "--porcelain", "manifest.json"],
                        capture_output=True, text=True)
    if not st.stdout.strip():
        log("finalize: manifest unchanged, nothing to commit")
        return 0
    msg = (
        f"Publish {len(ids)} reciter{'s' if len(ids) != 1 else ''} in manifest.json\n"
        "\n"
        + "".join(f"* {rid}\n" for rid in ids)
        + "\nEach entry is written only after `gh release view` confirmed all 114\n"
          "containers on the release with the byte sizes they were packed with, so\n"
          "every asset URL in the manifest resolves.\n"
          "\nCo-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>\n"
    )
    subprocess.run(["git", "-C", DATA, "commit", "-q", "-m", msg], check=True)
    push = subprocess.run(["git", "-C", DATA, "push", "-q"], capture_output=True, text=True)
    if push.returncode != 0:
        log(f"finalize: push failed: {push.stderr.strip()[:200]}")
        return 1
    log(f"finalize: committed and pushed manifest.json with {len(ids)} reciters")
    return 0


if __name__ == "__main__":
    sys.exit(main())
