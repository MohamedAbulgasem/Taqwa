#!/usr/bin/env python3
"""Mirrors every new or changed file of the restricted archive into its backup folder (brief P:
"keeps the local archive backed up"), never deleting anything there, and writes what it did to
`<monitor>/backup.json` for the report's reminder (the owner uploads the backup to his Drive by hand).

    python3 tools/timetables/monitor/backup.py [--official <root>] [--backup <root>] [--monitor <dir>]

`--backup` is the folder that holds the mirrored `archive/` (default
~/Desktop/Workspace/apps/Taqwa-official-archive-backup-2026-09-27, or $TAQWA_OFFICIAL_BACKUP).
"""
import argparse
import datetime as dt
import json
import os
import shutil
import sys


def mirror(src_root, dst_root):
    """Copies files under src_root that dst_root lacks or holds with another size or time; returns
    (copied, total, newest mtime)."""
    copied = 0
    total = 0
    newest = 0.0
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames.sort()
        for name in sorted(filenames):
            if name == ".DS_Store":
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
    p.add_argument("--backup", default=os.environ.get("TAQWA_OFFICIAL_BACKUP")
                   or os.path.expanduser("~/Desktop/Workspace/apps/Taqwa-official-archive-backup-2026-09-27"))
    p.add_argument("--monitor", default=None)
    p.add_argument("--today", default=None)
    args = p.parse_args(argv)
    src = os.path.join(os.path.abspath(args.official), "archive")
    dst = os.path.join(os.path.abspath(args.backup), "archive")
    if not os.path.isdir(src):
        print(f"backup: no archive at {src}", file=sys.stderr)
        return 2
    if not os.path.isdir(dst):
        print(f"backup: no backup archive at {dst} (create it, or pass --backup)", file=sys.stderr)
        return 2
    copied, total, newest = mirror(src, dst)
    monitor_dir = args.monitor or os.path.join(os.path.abspath(args.official), "monitor")
    os.makedirs(monitor_dir, exist_ok=True)
    today = args.today or dt.date.today().isoformat()
    out = {"date": today, "copied": copied, "total": total,
           "newest": dt.datetime.fromtimestamp(newest).date().isoformat() if newest else None, "target": os.path.abspath(args.backup)}
    with open(os.path.join(monitor_dir, "backup.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1)
    print(f"backup: {copied} files copied to {dst} ({total} in all)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
