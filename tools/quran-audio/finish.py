"""Take one fetched reciter all the way to a verified GitHub Release.

    python3 finish.py <reciter id>

verify -> repair (with the everyayah fallback) -> verify again -> pack ->
container round-trip check -> preview check -> publish -> verify the assets.
Stops at the first failure, so a reciter with a hole never reaches a release.
repair.py's --substitute (an ayah from the other bit-rate folder) is deliberately
not passed here: it is a human's call, made by running repair.py by hand and
listening to what it logged before running finish.py again.
publish.py writes `state/<id>.done` once the release is verified, and this
then re-runs finalize.py so the manifest and the committed repository always
describe exactly what is published.
"""

import os
import subprocess
import sys

from common import log

HERE = os.path.dirname(os.path.abspath(__file__))


def run(script, *args):
    r = subprocess.run([sys.executable, os.path.join(HERE, script), *args])
    return r.returncode


def main():
    rid = sys.argv[1]
    # The first verify only deletes unplayable files; absences are expected here
    # and repair.py is what closes them, so its exit code is not fatal.
    run("verify.py", rid, "8")
    steps = [
        ("repair.py", (rid, "3", "--fallback")),
        ("verify.py", (rid, "8")),
        ("pack.py", (rid,)),
        ("check_container.py", (rid,)),
        ("check_previews.py", (rid,)),
        ("publish.py", (rid,)),
    ]
    for script, args in steps:
        rc = run(script, *args)
        if rc != 0:
            log(f"finish {rid}: {script} failed (rc={rc}) - reciter NOT published")
            return rc
    log(f"finish {rid}: published and verified")
    return run("finalize.py")


if __name__ == "__main__":
    sys.exit(main())
