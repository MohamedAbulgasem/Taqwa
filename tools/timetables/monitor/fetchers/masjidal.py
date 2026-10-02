"""Chicago (us.chicago): the mosques of us-chicago.tsv whose own tables Masjidal (AthanPlus) serves,
the platform their own sites read (masjidal.com/api/v1/time/range, the `salah` block, start times;
the iqamah block is not read): Makki Masjid and Masjid DarusSalam Lombard (the 18° block,
us.chicago.eighteen) and the Mosque Foundation, Bridgeview (us.isna). Each is checked at the
mosque's point as us.chicago's member, as us-chicago.tsv holds them, with Hanafi Asr; none prints
an imsak, so each Fajr is also the start of the fast.

One request per mosque: the first of the current month to the end of the month after next. A reply
is refused unless it is a success whose days are exactly those asked, in order, each on its printed
weekday; a day whose times are not in the day's order is left out (partial), and so are days a
table prints on the wrong clock around a change of America/Chicago's (the table's own fault, with a
note). Every run adds its days to the held table, so the months build up. No message quotes a
cell. Restricted: archive only."""
import calendar
import datetime as dt
import json
import re

from common import FetchError, Table
from fetchers.toronto import ampm, clock_slips, ordered

SOURCE = "us-chicago"
API = "https://masjidal.com/api/v1/time/range?masjid_id={id}&from_date={start}&to_date={end}"
# key, Masjidal id, name, lat, lon (us-chicago.tsv's point), the entry or convention member it is a table of
MOSQUES = [
    ("makki-masjid-chicago", "1VL4WVKx", "Makki Masjid, Chicago (Albany Park), IL", 41.9704, -87.7144, "us.chicago.eighteen"),
    ("masjid-darussalam-lombard", "1QL0MDAZ", "Masjid DarusSalam, Lombard, IL", 41.9028, -88.0470, "us.chicago.eighteen"),
    ("mosque-foundation-bridgeview", "pQKM3ABE", "Mosque Foundation, Bridgeview, IL", 41.7229, -87.8030, "us.isna"),
]
FIELDS = ["fajr", "sunrise", "zuhr", "asr", "maghrib", "isha"]
WEEKDAYS = {name.lower(): i for i, name in enumerate(calendar.day_name)}
MONTHS = {name.lower(): i for i, name in enumerate(calendar.month_abbr) if name}


def window(today):
    """(first of the current month, last day of the month after next)."""
    start = today.replace(day=1)
    y, m = start.year + (start.month + 1) // 12, (start.month + 1) % 12 + 1
    return start, dt.date(y, m, calendar.monthrange(y, m)[1])


def parse_date(text):
    """'Sunday, Nov 1, 2026' -> the date, its printed weekday checked."""
    m = re.match(r"^\s*([A-Za-z]+),\s*([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2}),\s*(\d{4})\s*$", str(text))
    if not m or m.group(2).lower() not in MONTHS or m.group(1).lower() not in WEEKDAYS:
        raise FetchError("a day whose date does not read as one")
    d = dt.date(int(m.group(4)), MONTHS[m.group(2).lower()], int(m.group(3)))
    if d.weekday() != WEEKDAYS[m.group(1).lower()]:
        raise FetchError(f"{d} is printed on another weekday")
    return d


def parse(body, start, end, bad):
    """{date: [F, S, D, A, M, I]} of one reply, which must hold exactly the days start..end."""
    try:
        j = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        raise FetchError("not JSON")
    if not isinstance(j, dict) or j.get("status") != "success" or not isinstance(j.get("data"), dict):
        raise FetchError("not a success")
    days = j["data"].get("salah")
    if not isinstance(days, list) or not days:
        raise FetchError("no salah days")
    rows, dates = {}, []
    for x in days:
        d = parse_date(x.get("date"))
        dates.append(d)
        try:
            rows[d.isoformat()] = ordered([ampm(x.get(f)) for f in FIELDS], d.isoformat())
        except FetchError as e:
            bad.append(f"{d}: {e}")
    want = [start + dt.timedelta(days=i) for i in range((end - start).days + 1)]
    if dates != want:
        raise FetchError(f"{len(dates)} days from {dates[0]} to {dates[-1]}, not the {len(want)} asked ({start} to {end}) in order")
    return rows


def fetch(ctx):
    start, end = window(ctx.today)
    tables = []
    for key, mid, name, lat, lon, entry in MOSQUES:
        url = API.format(id=mid, start=start.isoformat(), end=end.isoformat())
        try:
            body = ctx.http.get(url)
            bad = []
            rows = parse(body, start, end, bad)
        except FetchError as e:
            ctx.error(f"{name}: {e}")
            continue
        for b in bad:
            ctx.error(f"{name} {b}; the day is left out")
        slips, notes = clock_slips(rows, "America/Chicago")
        for _, n in notes:
            ctx.note(f"{name}: {n}")
        t = Table(key, name, lat, lon, "America/Chicago", "US", "F+E S D A M I", entry=entry, school="hanafi",
                  source_line=f"{name}: its Masjidal table ({url}, the salah block)",
                  raw=[(f"masjidal-{key}-{start}-{end}.json", body)])
        for date, times in rows.items():
            if date not in slips:
                t.add(date, times)
        if t.rows:
            tables.append(t)
    return tables
