#!/usr/bin/env python3
# The weekly monitor's handoff between its two jobs (docs/MONITOR.md, "Publishing"), with the Python standard library
# alone. The proving job (fetchers, pip, Gradle; no token) PACKS what the run changed; the publish job (a clean runner
# that runs nothing else: no pip, no Gradle, no fetcher) CHECKS the pack and applies it, then commits and pushes.
#
#   handoff.py pack [--archive <archive checkout>] [--taqwa <Taqwa checkout>] --out <dir>
#       private/ (with --archive): every file the run added or changed under archive/ and monitor/, and
#       private.manifest (deletions too); public/ (with --taqwa): the gate files, stamps and ProofStamps.kt prove
#       changed, and public.manifest. Manifest lines: "A<TAB>sha256<TAB>path" or "D<TAB>-<TAB>path". A path with a
#       part that starts with a dot is left out with a warning (the check refuses one). Prints "packed=true".
#   handoff.py check --kind private|public --from <dir> --into <checkout>
#       Refuses (exit 3, "refused: <why>" on stdout) a path that is not safe or not allowed (private: under archive/ or
#       monitor/; public: a line of bot-paths.txt beside this file, never a deletion), a file the manifest does not
#       list, a hash that differs, a symlink, more than the size limits; for public also a file that is not UTF-8 text,
#       a stamp that is not JSON, an added line with a clock time's shape (ruling R69), a commit message
#       (proof-message.txt) that is not short plain text without a time's shape or a trailer, and a ProofStamps.kt that
#       is not exactly what proof_stamps.py writes from the resulting stamps. Otherwise applies the files to <checkout>
#       (nothing staged).
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import proof_stamps  # noqa: E402

PROOF_STAMPS = "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt"
PUBLIC_PATHSPECS = ["tools/timetables/official/gate", "tools/timetables/official/stamps", PROOF_STAMPS]
PRIVATE_PATHSPECS = ["archive", "monitor"]
CLOCK = re.compile(r"(^|[^0-9+-])[0-2]?[0-9]:[0-5][0-9]([^0-9]|$)")
SAFE = re.compile(r"[A-Za-z0-9._+=@,-]+(/[A-Za-z0-9._+=@,-]+)*")
TRAILER = re.compile(r"^[A-Za-z][A-Za-z-]*-[Bb]y:")
LIMITS = {"private": (20000, 1 << 30), "public": (500, 32 << 20)}  # files, bytes


class Refused(Exception):
    pass


def allowed_public():
    with open(os.path.join(HERE, "bot-paths.txt"), encoding="utf-8") as f:
        return [re.compile(line.strip()) for line in f if line.strip() and not line.lstrip().startswith("#")]


def git(repo, *args):
    return subprocess.run(["git", "-c", "core.hooksPath=/dev/null", "-C", repo, *args], check=True,
                          capture_output=True).stdout


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def changes(repo, pathspecs):
    """(status, path) of every change under the pathspecs, untracked files included, renames as delete + add."""
    git(repo, "add", "-A", "--", *pathspecs)
    out = git(repo, "diff", "--cached", "--no-renames", "--name-status", "-z", "--", *pathspecs).decode("utf-8")
    parts = [p for p in out.split("\0") if p]
    return [(parts[i][0], parts[i + 1]) for i in range(0, len(parts), 2)]


def pack_one(repo, pathspecs, out, kind):
    lines = []
    for status, path in changes(repo, pathspecs):
        if any(part.startswith(".") for part in path.split("/")):
            print("warning: %s left out (a dot path; the publish job refuses one)" % path)
            continue
        if status == "D":
            lines.append("D\t-\t%s" % path)
            continue
        src = os.path.join(repo, path)
        dst = os.path.join(out, kind, path)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        lines.append("A\t%s\t%s" % (sha256(dst), path))
    with open(os.path.join(out, kind + ".manifest"), "w", encoding="utf-8") as f:
        f.write("".join(line + "\n" for line in lines))
    return len(lines)


def pack(args):
    out = args["--out"]
    os.makedirs(out, exist_ok=True)
    if "--archive" in args:
        print("private: %d paths" % pack_one(args["--archive"], PRIVATE_PATHSPECS, out, "private"))
    if "--taqwa" in args:
        print("public: %d paths" % pack_one(args["--taqwa"], PUBLIC_PATHSPECS, out, "public"))
    print("packed=true")


def read_manifest(src, kind):
    path = os.path.join(src, kind + ".manifest")
    if not os.path.isfile(path):
        raise Refused("no %s.manifest in the handoff" % kind)
    entries = []
    with open(path, encoding="utf-8") as f:
        for line in f.read().splitlines():
            cells = line.split("\t")
            if len(cells) != 3 or cells[0] not in ("A", "D") or (cells[0] == "A" and not re.fullmatch(r"[0-9a-f]{64}", cells[1])):
                raise Refused("a manifest line is not 'A|D<TAB>hash<TAB>path'")
            entries.append(tuple(cells))
    return entries


def check_path(kind, path, deleted, patterns):
    if not SAFE.fullmatch(path) or any(part == "" or part.startswith(".") for part in path.split("/")):
        raise Refused("a path that is not plain: %r" % path[:200])
    if kind == "private":
        if not path.startswith(("archive/", "monitor/")):
            raise Refused("a path outside archive/ and monitor/: %s" % path)
    else:
        if deleted:
            raise Refused("prove never deletes a file, but the handoff deletes %s" % path)
        if not any(p.fullmatch(path) for p in patterns):
            raise Refused("a path outside the bot's allowlist (bot-paths.txt): %s" % path)


def check_message(path):
    """The public commit's message: short UTF-8 text, no control character but the newline, no time's shape (R69), and
    no trailer (an attribution or a sign-off: the owner's commits carry none)."""
    if not os.path.isfile(path):
        raise Refused("the handoff has no commit message")
    with open(path, "rb") as f:
        data = f.read()
    if len(data) > 65536:
        raise Refused("the commit message is longer than 64 KB")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        raise Refused("the commit message is not UTF-8")
    if not text.strip() or any(ord(c) < 0x20 and c != "\n" for c in text):
        raise Refused("the commit message is empty or holds a control character")
    for line in text.splitlines():
        if CLOCK.search(line):
            raise Refused("the commit message has a clock time's shape (ruling R69)")
        if TRAILER.match(line.strip()):
            raise Refused("the commit message carries a trailer")


def check(args):
    kind, src, into = args["--kind"], args["--from"], args["--into"]
    if kind not in ("private", "public"):
        raise Refused("--kind is private or public")
    entries = read_manifest(src, kind)
    patterns = allowed_public()
    root = os.path.join(src, kind)
    listed = {}
    for status, digest, path in entries:
        if path in listed:
            raise Refused("a path listed twice: %s" % path)
        check_path(kind, path, status == "D", patterns)
        listed[path] = (status, digest)
    max_files, max_bytes = LIMITS[kind]
    if len(listed) > max_files:
        raise Refused("%d paths, more than %d" % (len(listed), max_files))
    found, total = set(), 0
    for dirpath, dirnames, filenames in os.walk(root) if os.path.isdir(root) else []:
        for name in dirnames + filenames:
            if os.path.islink(os.path.join(dirpath, name)):
                raise Refused("a symlink in the handoff: %s" % os.path.relpath(os.path.join(dirpath, name), root))
        for name in filenames:
            rel = os.path.relpath(os.path.join(dirpath, name), root).replace(os.sep, "/")
            found.add(rel)
            total += os.path.getsize(os.path.join(dirpath, name))
    added = {p for p, (s, _) in listed.items() if s == "A"}
    if found != added:
        extra, missing = sorted(found - added), sorted(added - found)
        raise Refused("the files are not the manifest's (not listed: %s; missing: %s)" % (extra[:5], missing[:5]))
    if total > max_bytes:
        raise Refused("%d bytes, more than %d" % (total, max_bytes))
    for path in sorted(added):
        if sha256(os.path.join(root, path)) != listed[path][1]:
            raise Refused("%s does not have the manifest's sha256" % path)
    if kind == "public":
        for path in sorted(added):
            with open(os.path.join(root, path), "rb") as f:
                data = f.read()
            try:
                text = data.decode("utf-8")
            except UnicodeDecodeError:
                raise Refused("%s is not UTF-8 text" % path)
            if "\0" in text:
                raise Refused("%s holds a NUL byte" % path)
            if path.endswith(".json"):
                try:
                    json.loads(text)
                except ValueError:
                    raise Refused("%s is not JSON" % path)
        check_message(os.path.join(src, "proof-message.txt"))
    for path, (status, _) in sorted(listed.items()):
        target = os.path.join(into, path)
        if os.path.islink(target):
            raise Refused("%s is a symlink in the checkout" % path)
        if status == "D":
            if os.path.lexists(target):
                os.remove(target)
        else:
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(os.path.join(root, path), target)
    if kind == "public":
        git(into, "add", "-A", "--", *PUBLIC_PATHSPECS)
        stray = [p for p in git(into, "diff", "--cached", "--name-only", "-z").decode("utf-8").split("\0")
                 if p and not any(r.fullmatch(p) for r in patterns)]
        if stray:
            raise Refused("the change touches files prove never writes: %s" % " ".join(stray[:5]))
        diff = git(into, "diff", "--cached", "-U0", "--text").decode("utf-8", "replace")
        for line in diff.splitlines():
            if line.startswith("+") and not line.startswith("+++ ") and CLOCK.search(line[1:]):
                raise Refused("an added line has a clock time's shape (ruling R69)")
        try:
            written = proof_stamps.generate(os.path.join(into, "tools/timetables/official/stamps"))
        except ValueError as e:
            raise Refused("the stamps do not make a ProofStamps.kt: %s" % str(e)[:300])
        with open(os.path.join(into, PROOF_STAMPS), encoding="utf-8", newline="") as f:
            if f.read() != written:
                raise Refused("ProofStamps.kt is not what proof_stamps.py writes from the stamps")
        git(into, "reset", "-q")
    print("checked: %d paths, %d bytes" % (len(listed), total))


def main(argv):
    command, rest = (argv[0], argv[1:]) if argv else ("", [])
    args, i = {}, 0
    while i < len(rest):
        if rest[i] == "--taqwa" or rest[i].startswith("--"):
            args[rest[i]] = rest[i + 1] if i + 1 < len(rest) else ""
            i += 2
        else:
            i += 1
    try:
        if command == "pack":
            pack(args)
        elif command == "check":
            check(args)
        else:
            print("usage: handoff.py pack|check …", file=sys.stderr)
            return 2
    except Refused as e:
        print("refused: %s" % e)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
