"""Singapore, MUIS: the national table from data.gov.sg's consolidated dataset (Singapore Open Data
Licence v1.0, every year it holds, one table a year) and the MUIS website's own JSON (the current
calendar year, which turns over first). Subuh is also when eating stops (F+E); checked at MUIS's
fixed point (no lat/lon)."""
import json
import re

from common import FetchError, Table, norm_time

SOURCE = "sg-muis"
DATAGOV = "https://data.gov.sg/api/action/datastore_search?resource_id=d_a6a206cba471fe04b62dd886ef5eaf22&limit=2000"
WEBSITE = "https://isomer-user-content.by.gov.sg/muis_prayers_timetable.json"
NAMES = ("subuh", "syuruk", "zohor", "asar", "maghrib", "isyak")


def h24(value, index):
    """A 12-hour cell ('5:44', '1:06', '7:12pm') as 24-hour: a suffix decides; without one the
    afternoon columns (Zohor on) before 11 o'clock are p.m."""
    s = str(value).strip().lower()
    suffix = None
    m = re.match(r"^(\d{1,2}[:.]\d{2})\s*(am|pm)?$", s)
    if not m:
        raise FetchError(f"not a time: {value!r}")
    t = norm_time(m.group(1))
    suffix = m.group(2)
    h, mi = (int(x) for x in t.split(":"))
    if suffix == "pm" and h < 12:
        h += 12
    elif suffix == "am" and h == 12:
        h = 0
    elif suffix is None and index >= 2 and h < 11:
        h += 12
    return f"{h:02d}:{mi:02d}"


def date_of(text):
    s = str(text).strip()[:10]
    if re.match(r"^\d{4}-\d{2}-\d{2}$", s):
        return s
    m = re.match(r"^(\d{1,2})[/-](\d{1,2})[/-](\d{4})", str(text).strip())
    if m:
        return f"{m.group(3)}-{int(m.group(2)):02d}-{int(m.group(1)):02d}"
    raise FetchError(f"not a date: {text!r}")


def parse_records(records, bad=None):
    """{date: [6 times]} of the dataset's records; a record whose cells do not read goes to `bad`
    (a list, when given) instead of dropping the whole year (review M5)."""
    rows = {}
    for r in records:
        keys = {k.lower(): k for k in r}
        if "date" not in keys or not all(n in keys for n in NAMES):
            continue
        try:
            rows[date_of(r[keys["date"]])] = [h24(r[keys[n]], i) for i, n in enumerate(NAMES)]
        except FetchError as e:
            if bad is None:
                raise
            bad.append(f"{r.get(keys['date'])}: {e}")
    return rows


def parse_website(body):
    d = json.loads(body.decode("utf-8", "replace"))
    rows = {}
    if isinstance(d, dict):
        for k, v in d.items():
            if isinstance(v, dict) and all(n in v for n in NAMES):
                rows[date_of(k)] = [h24(v[n], i) for i, n in enumerate(NAMES)]
    elif isinstance(d, list):
        rows = parse_records(d)
    if not rows:
        raise FetchError("no dated rows in the website's JSON")
    return rows


def fetch(ctx):
    tables = []
    rows = {}
    raw = []
    bad = []
    url = DATAGOV
    # The dataset half; its failure leaves the website half to run (review M5).
    try:
        for page in range(20):
            body = ctx.http.get(url)
            d = json.loads(body.decode("utf-8", "replace"))
            result = d.get("result") or {}
            records = result.get("records") or []
            raw.append((f"datagov-{page}.json", body))
            rows.update(parse_records(records, bad))
            nxt = (result.get("_links") or {}).get("next")
            if not records or not nxt or len(rows) >= int(result.get("total") or 0):
                break
            url = "https://data.gov.sg" + nxt
        if not rows:
            ctx.error("data.gov.sg: no records")
        if bad:
            ctx.note(f"data.gov.sg: {len(bad)} records skipped ({'; '.join(bad[:3])})")
    except (FetchError, ValueError, KeyError, TypeError) as e:
        ctx.error(f"data.gov.sg: {e}")
    by_year = {}
    for date, times in rows.items():
        by_year.setdefault(date[:4], {})[date] = times
    for year, days in sorted(by_year.items()):
        t = Table(f"singapore-{year}", f"Singapore {year}", None, None, "Asia/Singapore", "SG", "F+E S D A M I", entry="sg.muis",
                  source_line=f"MUIS Singapore {year}: {DATAGOV} (dataset 'Muslim Prayer Timetable (consolidated)', MUIS, Singapore Open Data Licence v1.0); 12-h times converted to 24-h",
                  raw=raw if year == max(by_year) else [])
        for date, times in days.items():
            t.add(date, times)
        t.merge = False
        tables.append(t)
    try:
        body = ctx.http.get(WEBSITE)
        wrows = parse_website(body)
        by_year = {}
        for date, times in wrows.items():
            by_year.setdefault(date[:4], {})[date] = times
        for year, days in sorted(by_year.items()):
            t = Table(f"website-{year}", f"MUIS website {year}", None, None, "Asia/Singapore", "SG", "F+E S D A M I", entry="sg.muis",
                      source_line=f"MUIS website {WEBSITE} (muis.gov.sg's own file; 12-h times converted to 24-h)",
                      raw=[("website.json", body)] if year == max(by_year) else [])
            for date, times in days.items():
                t.add(date, times)
            t.merge = False
            tables.append(t)
    except (FetchError, ValueError, KeyError, TypeError) as e:
        ctx.error(f"website: {e}")
    return tables
