#!/usr/bin/env python3
"""The fetch half of the weekly monitor (spec §5, brief P): each source's newest published table,
politely, into the restricted archive, with a content hash so an unchanged table is recognised.
`scripts/monitor.sh` runs it before the check half (`./gradlew -p tools/timetables monitor`), which
reads what this writes:

    <official>/archive/tables/monitor/<source>/<key>.txt   the normalised tables (the gate's layout)
    <official>/archive/tables/monitor/index.tsv            every table held (rewritten only when it changes)
    <official>/archive/raw/monitor/<source>/<date>/        the raw responses of new or changed tables (gzip)
    <monitor>/hashes.json                                  content hashes, last-fetch dates, metadata
    <monitor>/fetch/latest.json, <date>.json, <date>.log   this run's outcome per source

    python3 tools/timetables/monitor/fetch.py [--official <root>] [--only <source>]... [--today yyyy-mm-dd]
                                              [--force] [--budget-minutes N]

`--official` defaults to $TAQWA_OFFICIAL, else ~/Desktop/Workspace/apps/Taqwa-official. A source
whose cadence is not due (monthly ones) is skipped unless `--only` names it or `--force` is given;
a source with cadence `manual` runs only when named; a source whose fetcher is `manual` is read by
hand and never runs. A fetcher that fails is a finding in the report, never a crash: this exits 0
unless the driver itself breaks (2), and then `latest.json` carries the error so the report says so.
The run has a time budget (review M3; 90 minutes unless given): once it is spent the remaining
sources are recorded as not fetched. Python 3.9 or later, standard library only. The archive is
git-ignored and restricted: nothing fetched is ever committed.
"""
import argparse
import datetime as dt
import gzip
import importlib
import io
import json
import os
import sys
import time
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from common import Context, Http, Table, read_table_rows, runtime_line, scrub, write_atomic, write_json  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SOURCES_TSV = os.path.join(REPO, "tools", "timetables", "official", "monitor", "sources.tsv")
INDEX_HEADER = ["source", "key", "path", "name", "lat", "lon", "zone", "clock", "cc", "entry", "survey", "columns",
                "format", "school", "hash", "fetched", "note"]
CADENCE_DAYS = {"weekly": 6, "monthly": 27}
DEFAULT_BUDGET_MINUTES = 90


def read_sources(path):
    header = None
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            cells = [c.strip() for c in line.split("\t")]
            if header is None:
                header = cells
                continue
            out.append(dict(zip(header, cells)))
    return out


def skip_reason(row, last_fetch, today, only, force):
    """Why a source is not fetched this run, or None when it is: a source read by hand (fetcher
    `manual`) never, named or not; one with cadence `manual` only when named; a weekly one every run
    (six days after its last fetch), a monthly one after 27 days; `--only` and `--force` override."""
    if row["fetcher"] == "manual":
        return "fetched by hand"
    if row["source"] in only:
        return None
    if force:
        return None
    if row["cadence"] == "manual":
        return "runs only when named"
    days = CADENCE_DAYS.get(row["cadence"], 6)
    if last_fetch is None or (today - last_fetch).days >= days:
        return None
    return "not due"


def due(row, last_fetch, today, only, force):
    return skip_reason(row, last_fetch, today, only, force) is None


class Store:
    """`hashes.json`, the table files and the index: what is held, and what this run found."""

    def __init__(self, official, monitor_dir, today):
        self.official = official
        self.monitor_dir = monitor_dir
        self.today = today
        self.path = os.path.join(monitor_dir, "hashes.json")
        if os.path.exists(self.path):
            with open(self.path, encoding="utf-8") as f:
                self.data = json.load(f)
        else:
            self.data = {"sources": {}, "tables": {}}
        self.data.setdefault("sources", {})
        self.data.setdefault("tables", {})
        self.run_status = {}

    def last_fetch(self, source):
        s = self.data["sources"].get(source, {}).get("lastFetch")
        return dt.date.fromisoformat(s) if s else None

    @staticmethod
    def rel_path(source, key):
        return "/".join(["archive", "tables", "monitor", source, key + ".txt"])

    def record(self, source, table):
        """Writes `table` when its content is new or changed (and its raw responses), remembers its
        hash and metadata, and returns `new`, `changed` or `unchanged`."""
        tid = f"{source}/{table.key}"
        rel = self.rel_path(source, table.key)
        path = os.path.join(self.official, rel)
        if getattr(table, "merge", True):
            held = read_table_rows(path)
            held.update(table.rows)
            table.rows = held
        h = table.content_hash()
        prev = self.data["tables"].get(tid)
        status = "new" if prev is None else ("unchanged" if prev.get("hash") == h else "changed")
        if status != "unchanged":
            write_atomic(path, table.text(self.today.isoformat()))
            raw_dir = os.path.join(self.official, "archive", "raw", "monitor", source, self.today.isoformat())
            for name, body in table.raw:
                buf = io.BytesIO()
                with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as z:
                    z.write(body if isinstance(body, bytes) else body.encode("utf-8"))
                write_atomic(os.path.join(raw_dir, name + ".gz"), buf.getvalue())
        self.data["tables"][tid] = {
            "source": source, "key": table.key, "path": rel, "name": table.name, "lat": table.lat, "lon": table.lon,
            "zone": table.zone, "clock": table.clock, "cc": table.cc, "entry": table.entry, "survey": table.survey,
            "columns": table.columns, "format": table.fmt, "school": table.school, "note": table.note,
            "hash": h, "first": table.first(), "last": table.last(), "rows": len(table.rows),
            "fetched": self.today.isoformat() if status != "unchanged" else prev.get("fetched"),
            "seen": self.today.isoformat(),
        }
        self.run_status[tid] = status
        return status

    def source_done(self, source, status, message, requests):
        """Only a complete fetch moves `lastFetch` on: a partial one (a breaker, the budget, a table
        that failed) is due again next run (review N7)."""
        entry = self.data["sources"].setdefault(source, {})
        if status == "ok":
            entry["lastFetch"] = self.today.isoformat()
        entry.update({"status": status, "message": scrub(message), "requests": requests, "lastRun": self.today.isoformat()})

    def index_lines(self):
        lines = ["\t".join(INDEX_HEADER)]
        for tid in sorted(self.data["tables"]):
            t = self.data["tables"][tid]
            cells = [
                t["source"], t["key"], t["path"], t["name"], t.get("lat"), t.get("lon"), t["zone"], t.get("clock"), t["cc"],
                t.get("entry"), t.get("survey"), t["columns"], t.get("format", "daily"), t.get("school", "standard"), t["hash"],
                t.get("fetched") or "", t.get("note") or "",
            ]
            lines.append("\t".join("" if c is None else str(c).replace("\t", " ").replace("\n", " ") for c in cells))
        return lines

    def write_index(self):
        """The index, rewritten only when its content changed (so a mirror copies it only then, review M11)."""
        path = os.path.join(self.official, "archive", "tables", "monitor", "index.tsv")
        text = ("# Written by tools/timetables/monitor/fetch.py when a table changes; never committed (the archive is restricted).\n"
                + "\n".join(self.index_lines()) + "\n")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                if f.read() == text:
                    return path
        write_atomic(path, text)
        return path

    def save(self):
        write_json(self.path, self.data)


def run_source(row, store, ctx):
    """One source through its fetcher; returns (status, message, requests, {key: status})."""
    source = row["source"]
    tables = []
    try:
        module = importlib.import_module("fetchers." + row["fetcher"])
        tables = list(module.fetch(ctx) or [])
    except Exception as e:  # a fetcher's failure is a finding, never a crash of the run
        ctx.error(f"{type(e).__name__}: {e}")
        ctx.log(scrub(traceback.format_exc()))
    statuses = {}
    for t in tables:
        if not isinstance(t, Table) or not t.rows:
            ctx.error(f"{getattr(t, 'key', t)}: no rows")
            continue
        try:
            statuses[t.key] = store.record(source, t)
        except Exception as e:
            ctx.error(f"{t.key}: could not be written: {type(e).__name__}: {e}")
    status = "ok" if not ctx.errors else ("partial" if statuses else "failed")
    message = scrub("; ".join(ctx.errors + ctx.notes)[:4000])
    return status, message, ctx.requests, statuses


def main(argv=None):
    if sys.version_info < (3, 9):
        print(f"fetch: Python 3.9 or later is needed, this is {sys.version.split()[0]}", file=sys.stderr)
        return 2
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--official", default=os.environ.get("TAQWA_OFFICIAL") or os.path.expanduser("~/Desktop/Workspace/apps/Taqwa-official"))
    p.add_argument("--monitor", default=None, help="the state folder (default <official>/monitor)")
    p.add_argument("--only", action="append", default=[], help="fetch this source only (repeatable); a manual-cadence source runs only when named")
    p.add_argument("--today", default=None)
    p.add_argument("--force", action="store_true", help="fetch every source whatever its cadence")
    p.add_argument("--budget-minutes", type=float, default=DEFAULT_BUDGET_MINUTES, help="the run's time budget (0: none)")
    p.add_argument("--sources", default=SOURCES_TSV)
    args = p.parse_args(argv)

    official = os.path.abspath(args.official)
    if not os.path.isdir(os.path.join(official, "archive")):
        print(f"fetch: no archive under {official} (pass --official or set TAQWA_OFFICIAL)", file=sys.stderr)
        return 2
    monitor_dir = args.monitor or os.path.join(official, "monitor")
    today = dt.date.fromisoformat(args.today) if args.today else dt.date.today()
    os.makedirs(os.path.join(monitor_dir, "fetch"), exist_ok=True)
    log_file = open(os.path.join(monitor_dir, "fetch", today.isoformat() + ".log"), "a", encoding="utf-8")

    def log(line):
        log_file.write(scrub(line) + "\n")
        log_file.flush()

    def write_outcome(results, error=None):
        out = {"date": today.isoformat(), "sources": results}
        if error:
            out["error"] = scrub(error)
        for name in ("latest.json", today.isoformat() + ".json"):
            write_json(os.path.join(monitor_dir, "fetch", name), out)

    log(f"== fetch {dt.datetime.now().isoformat(timespec='seconds')} {runtime_line()}")
    results = {}
    try:
        store = Store(official, monitor_dir, today)
        only = set(args.only)
        deadline = time.monotonic() + args.budget_minutes * 60 if args.budget_minutes and args.budget_minutes > 0 else None
        http = Http(log, deadline=deadline)
        for row in read_sources(args.sources):
            source = row["source"]
            if only and source not in only:
                results[source] = {"status": "skipped", "message": "not selected", "requests": 0, "tables": {}}
                continue
            why = skip_reason(row, store.last_fetch(source), today, only, args.force)
            if why:
                results[source] = {"status": "skipped", "message": why, "requests": 0, "tables": {}}
                print(f"{source}: skipped ({why})")
                continue
            if http.budget_spent():
                message = f"not fetched: the run's time budget ({args.budget_minutes:g} min) was spent before its turn"
                results[source] = {"status": "failed", "message": message, "requests": 0, "tables": {}}
                store.source_done(source, "failed", message, 0)
                print(f"{source}: failed — {message}", flush=True)
                continue
            log(f"-- {source} ({row['fetcher']})")
            print(f"{source}: fetching …", flush=True)
            ctx = Context(source, official, today, log, monitor_dir, args.force, http=http)
            status, message, requests, statuses = run_source(row, store, ctx)
            store.source_done(source, status, message, requests)
            results[source] = {"status": status, "message": message, "requests": requests, "tables": statuses}
            counts = {s: list(statuses.values()).count(s) for s in ("new", "changed", "unchanged")}
            print(f"{source}: {status}, {requests} requests, {len(statuses)} tables "
                  f"({counts['new']} new, {counts['changed']} changed, {counts['unchanged']} unchanged)"
                  + (f" — {message[:300]}" if message else ""), flush=True)
            store.save()
        index = store.write_index()
        store.save()
        write_outcome(results)
        print(f"index: {index}")
        return 0
    except Exception:
        error = scrub(traceback.format_exc())
        log(error)
        write_outcome(results, error=error)
        print(error, file=sys.stderr)
        return 2
    finally:
        log_file.close()


if __name__ == "__main__":
    sys.exit(main())
