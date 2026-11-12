#!/usr/bin/env python3
"""Rebuilds the editions of a source that re-issues days it already published (a table whose
`editions` is set, docs/MONITOR.md) from the raw responses the archive keeps for every capture that
changed (archive/raw/monitor/<source>/<date>/): one file per capture, editions/<key>/<date>.txt, read
by the source's own fetcher (its `raw_keys` and `parse_raw`). The migration for a source whose live
files were written before the store kept editions apart (Diyanet's, whose 9 November 2026 capture
merged its 2026 edition into files holding its 2027 one):

    python3 tools/timetables/monitor/editions.py --official <root> --source tr-diyanet-europe --out <dir>
    python3 tools/timetables/monitor/editions.py --official <root> --source tr-diyanet-europe --in-place

`--out` writes the rebuilt tree under <dir>/<source>/ and touches nothing in the archive (look first);
`--in-place` writes editions/ into the archive, keeps each live file as it stands beside them as
`<its fetched date>-merged.txt`, rewrites the live file as the latest edition alone, and updates its
hash, first, last and rows in monitor/hashes.json and the index, so the next monitor checks it again.
`--in-place` is refused, and writes nothing, unless for every table the latest raw capture is the one
the index records (its date is the table's `fetched`: raw files are kept only for a new or changed
capture, so an older one would roll the live file back) and the live file says what that capture says
on every day both hold. Standard library only; prints what it did, in days, never a printed time."""
import argparse
import datetime as dt
import gzip
import importlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from common import FetchError, norm_time, read_table_rows, sha256, write_atomic  # noqa: E402
from fetch import EDITIONS, SOURCES_TSV, Store, read_sources  # noqa: E402


def rebuild(official, monitor_dir, source, fetcher):
    """{key: {date: (rows, raw name)}} from every raw capture of `source` its fetcher can read."""
    module = importlib.import_module("fetchers." + fetcher)
    if not hasattr(module, "raw_keys") or not hasattr(module, "parse_raw"):
        raise SystemExit(f"editions: the fetcher {fetcher} cannot read its raw captures (no raw_keys/parse_raw)")
    names = module.raw_keys(source, monitor_dir)
    raw_root = os.path.join(official, "archive", "raw", "monitor", source)
    out = {}
    for date in sorted(os.listdir(raw_root)):
        folder = os.path.join(raw_root, date)
        if not os.path.isdir(folder):
            continue
        for gz in sorted(os.listdir(folder)):
            name = gz[:-3] if gz.endswith(".gz") else gz
            key = names.get(name)
            if key is None:
                continue
            with open(os.path.join(folder, gz), "rb") as f:
                body = f.read()
            text = (gzip.decompress(body) if gz.endswith(".gz") else body).decode("utf-8", "replace")
            rows = {}
            for d, times in module.parse_raw(text).items():
                try:
                    rows[d] = [norm_time(t) for t in times]
                except FetchError:
                    continue
            if rows:
                out.setdefault(key, {})[date] = (rows, name)
    return out


def text_of(source, key, date, name, rows):
    head = (f"# {source} {key}: the capture of {date}, rebuilt by editions.py from "
            f"archive/raw/monitor/{source}/{date}/{name}.gz (one edition, as the site published it that day) fetched {date}")
    return head + "\n" + "\n".join(f"{d} {' '.join(rows[d])}" for d in sorted(rows)) + "\n"


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--official", default=os.environ.get("TAQWA_OFFICIAL") or None)
    p.add_argument("--monitor", default=None)
    p.add_argument("--source", required=True)
    where = p.add_mutually_exclusive_group(required=True)
    where.add_argument("--out", help="write the rebuilt tree under this folder, touching nothing in the archive")
    where.add_argument("--in-place", action="store_true", help="write editions/ into the archive and the live files as the latest edition")
    p.add_argument("--sources", default=SOURCES_TSV)
    args = p.parse_args(argv)
    if not args.official:
        print("editions: no archive root given (pass --official or set TAQWA_OFFICIAL)", file=sys.stderr)
        return 2
    official = os.path.abspath(args.official)
    monitor_dir = args.monitor or os.path.join(official, "monitor")
    row = next((r for r in read_sources(args.sources) if r["source"] == args.source), None)
    if row is None:
        print(f"editions: no source {args.source} in sources.tsv", file=sys.stderr)
        return 2
    captures = rebuild(official, monitor_dir, args.source, row["fetcher"])
    live_root = os.path.join(official, "archive", "tables", "monitor", args.source)
    base = live_root if args.in_place else os.path.join(os.path.abspath(args.out), args.source)
    hashes = {}
    path = os.path.join(monitor_dir, "hashes.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            hashes = json.load(f).get("tables", {})
    if args.in_place:
        refused = []
        for key in sorted(captures):
            latest_date = max(captures[key])
            latest = captures[key][latest_date][0]
            record = hashes.get(f"{args.source}/{key}")
            live_rows = read_table_rows(os.path.join(live_root, key + ".txt"))
            if record is None:
                refused.append(f"{key}: not in the index")
            elif record.get("fetched") != latest_date:
                refused.append(f"{key}: the latest raw capture is of {latest_date} but the index records {record.get('fetched')}")
            elif any(live_rows[d] != latest[d] for d in latest if d in live_rows):
                refused.append(f"{key}: the live file differs from the capture of {latest_date} on "
                               f"{sum(1 for d in latest if d in live_rows and live_rows[d] != latest[d])} days")
        if refused:
            for line in refused:
                print(f"editions: refused, {line}", file=sys.stderr)
            print("editions: --in-place wrote nothing; look with --out first", file=sys.stderr)
            return 1
    store = Store(official, monitor_dir, dt.date.today()) if args.in_place else None
    for key in sorted(captures):
        dates = sorted(captures[key])
        folder = os.path.join(base, EDITIONS, key)
        live = os.path.join(live_root, key + ".txt")
        held = read_table_rows(live)
        if os.path.exists(live):
            fetched = hashes.get(f"{args.source}/{key}", {}).get("fetched") or "before"
            with open(live, encoding="utf-8") as f:
                write_atomic(os.path.join(folder, f"{fetched}-merged.txt"),
                             "# The live file as it stood before the monitor kept each edition apart: every row ever fetched, "
                             "merged across editions (editions.py).\n" + f.read())
        prev = None
        for date in dates:
            rows, name = captures[key][date]
            write_atomic(os.path.join(folder, date + ".txt"), text_of(args.source, key, date, name, rows))
            if prev is not None:
                added = sum(1 for d in rows if d not in prev)
                dropped = sum(1 for d in prev if d not in rows)
                changed = sum(1 for d in rows if d in prev and prev[d] != rows[d])
                print(f"{key} {date}: {min(rows)}..{max(rows)} ({len(rows)} days): {added} added, {changed} changed, {dropped} no longer in it")
            else:
                print(f"{key} {date}: {min(rows)}..{max(rows)} ({len(rows)} days): the first capture held")
            prev = rows
        latest_date = dates[-1]
        latest, name = captures[key][latest_date]
        lost = sorted(d for d in held if d not in latest)
        print(f"{key}: the live file holds {len(held)} days, the latest edition ({latest_date}) {len(latest)}; "
              f"{len(lost)} days only in earlier editions; the live file equal to the latest edition's on every day both hold: "
              f"{all(held[d] == latest[d] for d in latest if d in held)}")
        target = live if args.in_place else os.path.join(base, key + ".txt")
        text = text_of(args.source, key, latest_date, name, latest)
        write_atomic(target, text)
        if store is not None:
            lines = text.splitlines()[1:]
            store.data["tables"][f"{args.source}/{key}"].update(
                hash=sha256("\n".join(lines)), first=min(latest), last=max(latest), rows=len(latest))
    if store is not None:
        store.write_index()
        store.save()
    return 0



if __name__ == "__main__":
    sys.exit(main())
