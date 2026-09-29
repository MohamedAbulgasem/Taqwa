"""What every fetcher of the weekly monitor shares (spec §5, brief P): polite HTTP, the table layout
the gate reads, content hashes, and the index and log the check half (`./gradlew -p tools/timetables
monitor`) reads. Python 3.9 or later, standard library only (the Mac's /usr/bin/python3 and the
cloud runner's alike).

A fetcher is a module in `fetchers/` with a `SOURCE` id and `fetch(ctx)` returning a list of
`Table`s. It never writes files: `fetch.py` decides, from the content hash, whether a table is new,
changed or unchanged, writes the changed ones under `<official>/archive/tables/monitor/<source>/`
and keeps their raw responses under `<official>/archive/raw/monitor/<source>/<date>/`. A fetch
failure is recorded (`ctx.error`) or raised; either way it is a finding in the report, never a crash
of the run. Nothing fetched is ever committed: the archive is git-ignored and restricted.

A key (London Prayer Times') never reaches a log, a state file or the report: every URL is written
through `redact`, which masks the query parameters that carry one, and every message a fetcher
records or logs goes through `scrub`, which masks the values of the keys the run loaded (review I5).
"""
import datetime as dt
import gzip
import hashlib
import http.client
import json
import os
import re
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "Taqwa timetable monitor (taqwa.world)"
MIN_INTERVAL = 1.5  # seconds between requests, whatever the host
TIMEOUT = 60
# After this many consecutive network failures on one host the rest of its requests fail at once
# (review M3: a black-holed host would otherwise cost about two minutes a request).
BREAKER_FAILURES = 3
SECRET_PARAMS = {"key", "token", "apikey", "api_key", "access_token", "secret", "password"}

TIME_RE = re.compile(r"^(\d{1,2}):(\d{2})(?::\d{2})?$")


class FetchError(Exception):
    """A request or a response the fetcher could not use; the message is what the report shows."""


MASK = "***"

# The values of the keys this run loaded (`Context.keys_env`), in every form they may take in a
# message: as given, stripped, and URL-quoted. `scrub` masks them wherever a fetcher's words are
# written (review I5: an exception that quotes a request path would otherwise carry the key).
SECRETS = set()


def remember_secret(value):
    for form in (value, value.strip(), urllib.parse.quote(value, safe=""), urllib.parse.quote(value.strip(), safe="")):
        if form and len(form) >= 4:
            SECRETS.add(form)


def scrub(text):
    """`text` with every loaded key value masked."""
    out = str(text)
    for secret in sorted(SECRETS, key=len, reverse=True):
        out = out.replace(secret, MASK)
    return out


def redact(url):
    """`url` with the value of every secret-carrying query parameter masked (review I5); the other
    parameters are left byte for byte."""
    try:
        parts = urllib.parse.urlsplit(str(url))
    except ValueError:
        return "<url>"
    if not parts.query:
        return str(url)
    masked = []
    for pair in parts.query.split("&"):
        name, eq, value = pair.partition("=")
        masked.append(name + eq + (MASK if eq and name.lower() in SECRET_PARAMS else value))
    return scrub(urllib.parse.urlunsplit(parts._replace(query="&".join(masked))))


def host_of(url):
    try:
        return urllib.parse.urlsplit(str(url)).hostname or ""
    except ValueError:
        return ""


class Http:
    """One polite client for the whole run: a fixed User-Agent, at least MIN_INTERVAL between
    requests whatever the host, one retry, a breaker per host and a deadline for the run."""

    def __init__(self, log, deadline=None):
        self.log = log
        self.requests = 0
        self._last = 0.0
        self.deadline = deadline  # a time.monotonic() value, or None
        self.failures = {}  # host -> consecutive network failures
        self.tripped = set()
        self.owner = None  # the Context whose requests are being made (it counts its own)

    def _count(self):
        self.requests += 1
        if self.owner is not None:
            self.owner.made += 1

    def _wait(self):
        gap = time.monotonic() - self._last
        if gap < MIN_INTERVAL:
            time.sleep(MIN_INTERVAL - gap)

    def budget_spent(self):
        return self.deadline is not None and time.monotonic() >= self.deadline

    def _network_failure(self, host):
        n = self.failures.get(host, 0) + 1
        self.failures[host] = n
        if n >= BREAKER_FAILURES and host not in self.tripped:
            self.tripped.add(host)
            self.log(f"  BREAKER {host}: {n} consecutive network failures, the rest of its requests are not tried")

    def get(self, url, headers=None, data=None, timeout=TIMEOUT, retries=1):
        """The body (bytes) of `url`; `data` (bytes or a dict) makes it a POST. Raises FetchError."""
        shown = redact(url)
        host = host_of(url)
        if self.budget_spent():
            raise FetchError(f"not tried: the run's time budget is spent ({shown})")
        if host in self.tripped:
            raise FetchError(f"not tried: {host} failed {self.failures[host]} times in a row ({shown})")
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
            self._count()
            self._last = time.monotonic()
            try:
                req = urllib.request.Request(url, data=body, headers=hdrs)
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    out = resp.read()
                    if resp.headers.get("Content-Encoding") == "gzip":
                        out = gzip.decompress(out)
                    self.log(f"  {resp.status} {len(out)} bytes {shown[:120]}")
                    self.failures[host] = 0
                    return out
            except urllib.error.HTTPError as e:
                # The host answered: not a network failure for the breaker.
                self.failures[host] = 0
                last = FetchError(f"HTTP {e.code} from {shown}")
                if e.code in (429, 500, 502, 503, 504) and attempt < retries:
                    time.sleep(5)
                    continue
                raise last
            except urllib.error.URLError as e:
                if isinstance(e.reason, ssl.SSLError):
                    # Python's own TLS stack cannot reach this site (a certificate chain its store does
                    # not know, habous.gov.ma; a protocol the Mac's LibreSSL lacks): curl verifies
                    # against the system's store and speaks the system's TLS, so the request goes
                    # through curl instead (review I7).
                    return self._curl(url, hdrs, body, timeout)
                self._network_failure(host)
                last = FetchError(f"{type(e).__name__}: {getattr(e, 'reason', e)} from {shown}")
                if attempt < retries and host not in self.tripped:
                    time.sleep(5)
                    continue
                raise last
            except ssl.SSLError:
                return self._curl(url, hdrs, body, timeout)
            except (TimeoutError, OSError) as e:
                self._network_failure(host)
                last = FetchError(f"{type(e).__name__}: {getattr(e, 'reason', e)} from {shown}")
                if attempt < retries and host not in self.tripped:
                    time.sleep(5)
                    continue
                raise last
            except (http.client.HTTPException, ValueError) as e:
                # A request the client refused to build or a reply it could not parse (InvalidURL quotes
                # the whole request path, so its text is never repeated: only its kind and the masked URL).
                raise FetchError(f"{type(e).__name__} for {shown}")
        raise last

    def _curl(self, url, headers, body, timeout):
        """The same request through curl (still verifying the certificate, against the system store)."""
        shown = redact(url)
        cmd = ["curl", "-sSL", "--compressed", "--max-time", str(timeout), "-o", "-", "-w", "\n%{http_code}"]
        for k, v in headers.items():
            cmd += ["-H", f"{k}: {v}"]
        if body is not None:
            cmd += ["--data-binary", "@-"]
        cmd.append(url)
        try:
            r = subprocess.run(cmd, input=body if body is not None else None, capture_output=True, timeout=timeout + 10)
        except (OSError, subprocess.TimeoutExpired) as e:
            self._network_failure(host_of(url))
            raise FetchError(f"curl: {type(e).__name__} from {shown}")
        out = r.stdout
        nl = out.rfind(b"\n")
        code = out[nl + 1:].decode("ascii", "replace").strip() if nl >= 0 else ""
        data = out[:nl] if nl >= 0 else out
        if r.returncode != 0:
            self._network_failure(host_of(url))
            raise FetchError(f"curl exit {r.returncode}: {r.stderr.decode('utf-8', 'replace').strip()[:160]} from {shown}")
        if not code.startswith("2"):
            raise FetchError(f"HTTP {code or '?'} (via curl) from {shown}")
        self.failures[host_of(url)] = 0
        self.log(f"  {code} {len(data)} bytes {shown[:120]} (via curl: Python's TLS stack could not reach the site)")
        return data

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8", "replace")

    def json(self, url, **kw):
        body = self.get(url, **kw)
        try:
            return json.loads(body.decode("utf-8", "replace"))
        except ValueError as e:
            raise FetchError(f"not JSON ({e}; {len(body)} bytes) from {redact(url)}")


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
        """One day; a later call for the same date replaces the earlier (the newest fetch wins). A
        cell that is not a time raises, naming the cell's place, never its text (a printed time in
        another notation would otherwise reach the report and the run log)."""
        cells = []
        for i, t in enumerate(times):
            try:
                cells.append(norm_time(t))
            except FetchError:
                raise FetchError(f"cell {i + 1} is not a time")
        self.rows[date] = cells

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


def add_all(ctx, table, rows, label):
    """Every (date, times) of `rows` into `table`; a cell that is not a time is noted and skipped, so
    one malformed cell never drops the whole table or its source (review M5). Returns how many were added."""
    added = 0
    items = rows.items() if isinstance(rows, dict) else rows
    for date, times in items:
        try:
            table.add(date, times)
            added += 1
        except FetchError as e:
            ctx.note(f"{label} {date}: {e}")
    return added


def norm_time(t):
    """'5:07', '05:07:00', '05.07' -> '05:07'; '-' or None stays '-'. Anything else raises FetchError."""
    if t is None:
        return "-"
    s = str(t).strip().replace(".", ":")
    if s in ("", "-"):
        return "-"
    m = TIME_RE.match(s)
    if not m:
        raise FetchError("not a time")
    h, mi = int(m.group(1)), int(m.group(2))
    if h > 30 or mi > 59:
        raise FetchError("not a time")
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


def write_atomic(path, data):
    """Writes `data` (str or bytes) to `path` through a sibling temp file and os.replace, so a kill
    mid-write leaves the old file whole (review M7)."""
    os.makedirs(os.path.dirname(os.path.abspath(path)) or ".", exist_ok=True)
    tmp = os.path.join(os.path.dirname(os.path.abspath(path)), "." + os.path.basename(path) + ".tmp")
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(tmp, mode, **({} if isinstance(data, bytes) else {"encoding": "utf-8"})) as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def write_json(path, value):
    write_atomic(path, json.dumps(value, ensure_ascii=False, indent=1, sort_keys=True) + "\n")


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


def runtime_line():
    """The interpreter and TLS stack a run used, for the top of its log (review I7)."""
    return f"python {sys.version.split()[0]} ({sys.executable}), {ssl.OPENSSL_VERSION}, {sys.platform}"


class Context:
    """What a fetcher gets: the client (shared by the run), today's date, the official root (to read
    what is held), a place for errors that do not sink the whole source, and a log line writer."""

    def __init__(self, source, official, today, log, monitor_dir, force=False, http=None):
        self.source = source
        self.official = official
        self.today = today
        self.log = log
        self.monitor_dir = monitor_dir
        self.force = force
        self.http = http or Http(log)
        self.http.owner = self
        self.made = 0
        self.errors = []
        self.notes = []

    @property
    def requests(self):
        """The requests this source made."""
        return self.made

    def error(self, message):
        message = scrub(message)
        self.log(f"  ERROR {message}")
        self.errors.append(message)

    def note(self, message):
        message = scrub(message)
        self.log(f"  {message}")
        self.notes.append(message)

    def held(self, key):
        """The rows of this source's held table `key`, {} when none (append-style sources build on it)."""
        return read_table_rows(os.path.join(self.official, "archive", "tables", "monitor", self.source, key + ".txt"))

    def keys_env(self):
        """Secrets the owner keeps: <monitor>/keys.env (`NAME=value` lines, never in the repository) on
        the Mac, the environment (a repository secret) in the cloud; the environment wins. Values
        are stripped (a pasted secret may carry a newline) and remembered for `scrub`."""
        out = {}
        path = os.path.join(self.monitor_dir, "keys.env")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        out[k.strip()] = v.strip()
        for name in ("LPT_API_KEY",):
            if os.environ.get(name, "").strip():
                out[name] = os.environ[name].strip()
        for value in out.values():
            remember_secret(value)
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
