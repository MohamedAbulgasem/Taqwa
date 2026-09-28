#!/usr/bin/env python3
"""Mirrors every new or changed file of the restricted archive into a backup folder (a second copy
on the owner's Mac; in the cloud the private repository is the backup and this does not run,
ruling R94), never deleting anything there, and writes what it did to `<monitor>/backup.json` for
the report's reminder (the owner uploads the backup to his Drive by hand). When the mirror cannot
run, `backup.json` says so (`error`), and the report raises it (review I2).

    python3 tools/timetables/monitor/backup.py --official <root> --backup <root> [--monitor <dir>] [--today yyyy-mm-dd]

`--backup` is the folder that holds the mirrored `archive/` ($TAQWA_OFFICIAL_BACKUP).
"""
import argparse
import datetime as dt
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from common import write_json  # noqa: E402


def mirror(src_root, dst_root):
    """Copies files under src_root that dst_root lacks or holds with another size or time; returns
    (copied, total, newest mtime)."""
    copied = 0
    total = 0
    newest = 0.0
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames.sort()
        for name in sorted(filenames):
            if name == ".DS_Store" or name.endswith(".tmp"):
                continue
            src = os.path.join(dirpath, name)
            rel = os.path.relpath(src, src_root)
            dst = os.path.join(dst_root, rel)
            st = os.stat(src)
            total += 1
            newest = max(newest, st.st_mtime)
            if os.path.exists(dst):
                dt_ = os.stat(dst)
                if dt_.st_size == st.st_size and int(dt_.st_mtime) == int(st.st_mtime):
                    continue
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            copied += 1
    return copied, total, newest


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--official", default=os.environ.get("TAQWA_OFFICIAL") or os.path.expanduser("~/Desktop/Workspace/apps/Taqwa-official"))
    p.add_argument("--backup", default=os.environ.get("TAQWA_OFFICIAL_BACKUP") or "")
    p.add_argument("--monitor", default=None)
    p.add_argument("--today", default=None)
    args = p.parse_args(argv)
    official = os.path.abspath(args.official)
    monitor_dir = args.monitor or os.path.join(official, "monitor")
    today = args.today or dt.date.today().isoformat()
    record = os.path.join(monitor_dir, "backup.json")

    def fail(message):
        print(f"backup: {message}", file=sys.stderr)
        write_json(record, {"date": today, "error": message, "target": os.path.abspath(args.backup) if args.backup else ""})
        return 2

    if not args.backup:
        return fail("no backup folder given (--backup or TAQWA_OFFICIAL_BACKUP)")
    src = os.path.join(official, "archive")
    dst = os.path.join(os.path.abspath(args.backup), "archive")
    if not os.path.isdir(src):
        return fail(f"no archive at {src}")
    if not os.path.isdir(dst):
        return fail(f"no backup archive at {dst} (create it, or point TAQWA_OFFICIAL_BACKUP at the folder that holds archive/)")
    try:
        copied, total, newest = mirror(src, dst)
    except OSError as e:
        return fail(f"the mirror stopped: {type(e).__name__}: {e}")
    write_json(record, {"date": today, "copied": copied, "total": total,
                        "newest": dt.datetime.fromtimestamp(newest).date().isoformat() if newest else None,
                        "target": os.path.abspath(args.backup)})
    print(f"backup: {copied} files copied to {dst} ({total} in all)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
