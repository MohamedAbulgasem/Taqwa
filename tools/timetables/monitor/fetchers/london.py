"""London Unified Prayer Timetable, as London Prayer Times republishes the East London Mosque's
table ("completely free for all use", ruling R33). Its API needs a key issued by hand: with one
(`LPT_API_KEY` in <monitor>/keys.env on the Mac, never in the repository; the repository secret in
the cloud) this reads the coming year month by month; without one it does nothing and the horizon
reminds from 1 December that the next year is wanted. The key travels in the query string and is
masked wherever a URL is written (common.redact). The API's shape here follows its published
parameters (year, month, format=json, 24hours=true); it has not been exercised without a key, so
the first run with one may need a fix: a month whose response lacks a field is an error, never a
silent "-", and a year the API does not have yet stops after its first month.

The first run with a key (10 Oct 2026) found no dated times in any month of 2027: London Prayer
Times adds the next year in late October ("2025 times added (24th Oct 2024)" on its API page), and
until then the API answers without the year's days instead of an HTTP error. So when the coming
year's first month holds no days, the current month is read once as a probe: if it reads, the
parser and the key are fine and the year is simply not out yet (a note, not a failure); if it does
not, both answers are an error, each with the response's shape (its keys and value types, and a
short error message where the API gives one, never a time) so a key the API refuses or a changed
format can be seen in the issue."""
import json
import urllib.parse

from common import FetchError, Table

SOURCE = "gb-london-lupt"
API = "https://www.londonprayertimes.com/api/times/"
NAMES = ("fajr", "sunrise", "dhuhr", "asr", "magrib", "isha", "asr_2")


def find_times(obj):
    """A dict of yyyy-mm-dd -> {fajr, sunrise, …} anywhere in the response."""
    if isinstance(obj, dict):
        if obj and all(isinstance(k, str) and len(k) == 10 and k[4] == "-" and isinstance(v, dict) for k, v in obj.items()):
            return obj
        for v in obj.values():
            hit = find_times(v)
            if hit:
                return hit
    if isinstance(obj, list):
        for v in obj:
            hit = find_times(v)
            if hit:
                return hit
    return None


MESSAGE_KEYS = ("error", "message", "msg", "status", "detail")


def shape(obj, depth=0):
    """What a response holds, for a finding: its keys and the types of their values, and a short
    error message where the API gives one; never a time or any other value."""
    if isinstance(obj, dict):
        if depth >= 2:
            return "{…}" if obj else "{}"
        parts = []
        for k in list(obj)[:8]:
            v = obj[k]
            if isinstance(v, str) and str(k).lower() in MESSAGE_KEYS and len(v) <= 80:
                parts.append(f"{k}: {v!r}")
            else:
                parts.append(f"{k}: {shape(v, depth + 1)}")
        more = f", … {len(obj) - 8} more" if len(obj) > 8 else ""
        return "{" + ", ".join(parts) + more + "}"
    if isinstance(obj, list):
        return f"[{len(obj)} items]"
    return type(obj).__name__


def month_rows(body):
    """{date: [7 times]} of one month's response; a day lacking a field is an error (review Q4)."""
    data = json.loads(body.decode("utf-8", "replace"))
    times = find_times(data)
    if not times:
        raise FetchError(f"no dated times in the response (it holds {shape(data)})")
    rows = {}
    for date, v in times.items():
        missing = [n for n in NAMES if not v.get(n)]
        if missing:
            raise FetchError(f"{date}: no {', '.join(missing)} in the response (fields: {', '.join(sorted(v))})")
        rows[date] = [v[n] for n in NAMES]
    return rows


def current_month(ctx, quoted):
    """The probe: None when this month's days read, else what went wrong (the key is masked by the
    context wherever the answer is written)."""
    url = f"{API}?format=json&key={quoted}&year={ctx.today.year}&month={ctx.today.month}&24hours=true"
    try:
        return None if month_rows(ctx.http.get(url)) else "no days"
    except (FetchError, ValueError) as e:
        return str(e)


def fetch(ctx):
    key = ctx.keys_env().get("LPT_API_KEY")
    if not key:
        ctx.note("no LPT_API_KEY (keys.env or the environment): London Unified's next year is fetched by hand (the horizon reminds from 1 December)")
        return []
    year = ctx.today.year + 1 if ctx.today.month >= 10 else ctx.today.year
    t = Table(f"elm-{year}", f"LUPT {year}", None, None, "Europe/London", "GB", "F+E S D As M I Ah", entry="gb.london.lupt/gb.london.lupt", school="-",
              source_line=f"London Prayer Times API {API} (the East London Mosque's LUPT table, free for all use), year {year}")
    quoted = urllib.parse.quote(key.strip(), safe="")
    for month in range(1, 13):
        url = f"{API}?format=json&key={quoted}&year={year}&month={month}&24hours=true"
        try:
            body = ctx.http.get(url)
            for date, times in month_rows(body).items():
                t.add(date, times)
            t.raw.append((f"lpt-{year}-{month:02d}.json", body))
        except (FetchError, ValueError) as e:
            if month == 1 and (str(e).startswith("HTTP 4") or str(e).startswith("no dated times")):
                probe = current_month(ctx, quoted)
                if probe is None:
                    ctx.note(f"{year} is not on the API yet ({e}); the current month reads fine, so the key and "
                             f"the parser work; nothing fetched")
                else:
                    ctx.error(f"{year}-01: {e}; and the current month does not read either ({probe}): "
                              f"the key or the API's format needs a look")
                return []
            ctx.error(f"{year}-{month:02d}: {e}")
    t.merge = False
    return [t] if t.rows else []
