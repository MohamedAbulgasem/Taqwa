"""What every fetcher of the weekly monitor shares (spec §5, brief P): polite HTTP, the table layout
the gate reads, content hashes, and the index and log the check half (`./gradlew -p tools/timetables
monitor`) reads. Python 3 standard library only.

A fetcher is a module in `fetchers/` with a `SOURCE` id and `fetch(ctx)` returning a list of
`Table`s. It never writes files: `fetch.py` decides, from the content hash, whether a table is new,
changed or unchanged, writes the changed ones under `<official>/archive/tables/monitor/<source>/`
and keeps their raw responses under `<official>/archive/raw/monitor/<source>/<date>/`. A fetch
failure is recorded (`ctx.error`) or raised; either way it is a finding in the report, never a crash
of the run. Nothing fetched is ever committed: the archive is git-ignored and restricted.
"""
import datetime as dt
import gzip
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "Taqwa timetable monitor (taqwa.world)"
MIN_INTERVAL = 1.5  # seconds between requests, whatever the host
TIMEOUT = 60

TIME_RE = re.compile(r"^(\d{1,2}):(\d{2})(?::\d{2})?$")


class FetchError(Exception):
    """A request or a response the fetcher could not use; the message is what the report shows."""


class Http:
    """One polite client: a fixed User-Agent, at least MIN_INTERVAL between requests, one retry."""

    def __init__(self, log):
        self.log = log
        self.requests = 0
        self._last = 0.0

    def _wait(self):
        gap = time.monotonic() - self._last
        if gap < MIN_INTERVAL:
            time.sleep(MIN_INTERVAL - gap)

    def get(self, url, headers=None, data=None, timeout=TIMEOUT, retries=1):
        """The body (bytes) of `url`; `data` (bytes or a dict) makes it a POST. Raises FetchError."""
        hdrs = {"User-Agent": USER_AGENT, "Accept": "*/*"}
        if headers:
            hdrs.update(headers)
        body = data
        if isinstance(data, dict):
            body = urllib.parse.urlencode(data).encode("utf-8")
            hdrs.setdefault("Content-Type", "application/x-www-form-urlencoded")
        last = None
        for attempt in range(retries + 1):
            self._wait()
            self.requests += 1
            self._last = time.monotonic()
            try:
                req = urllib.request.Request(url, data=body, headers=hdrs)
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    out = resp.read()
                    if resp.headers.get("Content-Encoding") == "gzip":
                        out = gzip.decompress(out)
                    self.log(f"  {resp.status} {len(out)} bytes {url[:120]}")
                    return out
            except urllib.error.HTTPError as e:
                last = FetchError(f"HTTP {e.code} from {url}")
                if e.code in (429, 500, 502, 503, 504) and attempt < retries:
                    time.sleep(5)
                    continue
                raise last
            except (urllib.error.URLError, TimeoutError, OSError) as e:
                last = FetchError(f"{type(e).__name__}: {getattr(e, 'reason', e)} from {url}")
                if attempt < retries:
                    time.sleep(5)
                    continue
                raise last
        raise last

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8", "replace")

    def json(self, url, **kw):
        body = self.get(url, **kw)
        try:
            return json.loads(body.decode("utf-8", "replace"))
        except ValueError as e:
            raise FetchError(f"not JSON ({e}) from {url}: {body[:80]!r}")


class Table:
    """One normalised table (the gate's `daily` layout unless `fmt` says otherwise).

    - key: the file name without `.txt`, stable across runs (`makkah-2026`, a Mawaqit slug);
    - entry: the registry entry it is a table of (`id` or `id/unit`), or None for a mosque calendar
      with `survey` set to its survey folder;
    - columns / school / clock: as a gate row's;
    - rows: {date iso: [6 or more 'HH:MM' or '-']}, written in date order;
    - raw: [(file name, bytes)] kept under raw/monitor/<source>/<date>/ when the table is new or changed.
    """

    def __init__(self, key, name, lat, lon, zone, cc, columns, entry=None, survey=None, school="standard",
                 clock=None, fmt="daily", source_line="", note="", rows=None, raw=None):
        self.key = key
        self.name = name
        self.lat = lat
        self.lon = lon
        self.zone = zone
        self.cc = cc
        self.columns = columns
        self.entry = entry
        self.survey = survey
        self.school = school
        self.clock = clock
        self.fmt = fmt
        self.source_line = source_line
        self.note = note
        self.rows = dict(rows or {})
        self.raw = list(raw or [])

    def add(self, date, times):
        """One day; a later call for the same date replaces the earlier (the newest fetch wins)."""
        self.rows[date] = [norm_time(t) for t in times]

    def lines(self):
        return [f"{d} {' '.join(self.rows[d])}" for d in sorted(self.rows)]

    def content_hash(self):
        return sha256("\n".join(self.lines()))

    def text(self, fetched):
        head = f"# {self.source_line} fetched {fetched}" if self.source_line else f"# fetched {fetched}"
        return head + "\n" + "\n".join(self.lines()) + "\n"

    def first(self):
        return min(self.rows) if self.rows else None

    def last(self):
        return max(self.rows) if self.rows else None


def norm_time(t):
    """'5:07', '05:07:00', '05.07' -> '05:07'; '-' or None stays '-'. Anything else raises FetchError."""
    if t is None:
        return "-"
    s = str(t).strip().replace(".", ":")
    if s in ("", "-"):
        return "-"
    m = TIME_RE.match(s)
    if not m:
        raise FetchError(f"not a time: {t!r}")
    h, mi = int(m.group(1)), int(m.group(2))
    if h > 30 or mi > 59:
        raise FetchError(f"not a time: {t!r}")
    return f"{h:02d}:{mi:02d}"


def pm(t, afternoon=True):
    """A 12-hour time read as a 24-hour one: '4:15' in the afternoon -> '16:15'."""
    s = norm_time(t)
    if s == "-":
        return s
    h, mi = (int(x) for x in s.split(":"))
    if afternoon and h < 12:
        h += 12
    return f"{h:02d}:{mi:02d}"


def sha256(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def read_table_rows(path):
    """The rows of a held table file: {date: [times]} (comment and blank lines skipped)."""
    rows = {}
    if not os.path.exists(path):
        return rows
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            p = line.split()
            rows[p[0]] = p[1:]
    return rows


def nearest_year(month, day, today):
    """The year that puts month/day nearest to today (a widget that prints no year)."""
    best = None
    for y in (today.year - 1, today.year, today.year + 1):
        try:
            d = dt.date(y, month, day)
        except ValueError:
            continue
        if best is None or abs((d - today).days) < abs((best - today).days):
            best = d
    return best


class Context:
    """What a fetcher gets: the client, today's date, the official root (to read what is held), a
    place for errors that do not sink the whole source, and a log line writer."""

    def __init__(self, source, official, today, log, monitor_dir, force=False):
        self.source = source
        self.official = official
        self.today = today
        self.log = log
        self.monitor_dir = monitor_dir
        self.force = force
        self.http = Http(log)
        self.errors = []
        self.notes = []

    def error(self, message):
        self.log(f"  ERROR {message}")
        self.errors.append(str(message))

    def note(self, message):
        self.log(f"  {message}")
        self.notes.append(str(message))

    def held(self, key):
        """The rows of this source's held table `key`, {} when none (append-style sources build on it)."""
        return read_table_rows(os.path.join(self.official, "archive", "tables", "monitor", self.source, key + ".txt"))

    def keys_env(self):
        """Secrets the owner keeps in <monitor>/keys.env (`NAME=value` lines), never in the repository."""
        out = {}
        path = os.path.join(self.monitor_dir, "keys.env")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        out[k.strip()] = v.strip()
        return out


def survey_calendars(repo):
    """Every calendar the committed surveys list: [(folder, slug, name, lat, lon, columns, zone or None)]."""
    out = []
    root = os.path.join(repo, "tools", "timetables", "official", "survey")
    for folder in sorted(os.listdir(root)):
        path = os.path.join(root, folder, "calendars.tsv")
        if not os.path.exists(path):
            continue
        header = None
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line.strip() or line.startswith("#"):
                    continue
                cells = line.split("\t")
                if header is None:
                    header = cells
                    continue
                row = dict(zip(header, cells))
                slug = os.path.basename(row["path"])[:-4]
                out.append((folder, slug, row["name"], float(row["lat"]), float(row["lon"]), row["columns"], row.get("zone")))
    return out
