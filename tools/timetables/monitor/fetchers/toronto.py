"""Toronto, the three mosques of ca.toronto (ca-toronto.tsv), each table its own mosque's:

- the Islamic Foundation of Toronto (ca.ift): the CSV its site's display reads, a whole year
  (PrayerDate and the *Begins columns, 12-hour times with no AM or PM, Asr Hanafi). The file's
  address names the month it was uploaded, so the next year's file is looked for by hand (the
  sources.tsv row says so); a file that is not one whole year, in order, is refused;
- the Islamic Institute of Toronto (ca.iit): islam.ca's own monthly table (the Daily Prayer Time
  plugin's admin-ajax call get_monthly_timetable), twelve months, each row dated in full ("November
  1, 2026"); a reply whose heading or rows are another month's is refused; Asr Standard;
- MAC Masjid Toronto (ca.mac): its Mawaqit calendar (confData.calendar, twelve months, no year:
  mapped onto the current one, as ca-toronto.tsv's table was), at the mosque's own point.

Each table is checked at its mosque's unit and there as ca.toronto's member. Only the days from the
first of the current month are kept (the earlier ones are the gate's, its faults already left out);
the tables are replaced, not merged. A day whose times are not in the day's order is left out and
the fetch is partial. A table that changes its clock on another day than America/Toronto does (IFT's
2026 file keeps daylight time for 1-6 November) has the days between on the wrong clock: those are
the table's own fault, left out with a note, never checked as an hour off; so are the days
ca-toronto.tsv records as IFT's faults (IFT_FAULTS). No message quotes a cell. Restricted: archive only."""
import calendar
import csv
import datetime as dt
import io
import re

from common import FetchError, Table, norm_time
from fetchers import mawaqit

SOURCE = "ca-toronto"
IFT_CSV = "https://islamicfoundation.ca/wp-content/uploads/2026/02/prayer_timings_IFT-All_Data-1.csv"
IFT_COLUMNS = ["PrayerDate", "FajarBegins", "Sunrise", "ZuharBegins", "AsarBegins", "MagribBegins", "IshaBegins"]
IIT_AJAX = "https://islam.ca/wp-admin/admin-ajax.php?action=get_monthly_timetable&month={month}"
# Days of IFT's file that ca-toronto.tsv records as its own faults (proof.md) and the clock check does
# not find: left out, as its split files leave them out. A file that changes is the news, so only the
# 2026 file's are named.
IFT_FAULTS = {
    "2026-11-28": "IFT prints Isha 5 min after its neighbours on 28-30 November (ca-toronto.tsv, proof.md)",
    "2026-11-29": "IFT prints Isha 5 min after its neighbours on 28-30 November (ca-toronto.tsv, proof.md)",
    "2026-11-30": "IFT prints Isha 5 min after its neighbours on 28-30 November (ca-toronto.tsv, proof.md)",
}
MAC_SLUG = "masjid-toronto-mac"
MAC_POINT = (43.6555, -79.3858)
MONTHS = {name.lower(): i for i, name in enumerate(calendar.month_name) if name}


def minutes(t):
    h, m = (int(x) for x in t.split(":"))
    return h * 60 + m


def ordered(times, label):
    """`times` (24-hour) if they run in the day's order, else FetchError naming `label` only."""
    m = [minutes(t) for t in times]
    if any(b <= a for a, b in zip(m, m[1:])):
        raise FetchError(f"{label}: the times are not in the day's order")
    return times


def twelve(cell, afternoon):
    """A 12-hour cell with no AM or PM, read as morning or afternoon ('1:05' after noon is 13:05)."""
    t = norm_time(cell)
    if t == "-":
        raise FetchError("an empty cell")
    h, m = minutes(t) // 60, minutes(t) % 60
    if afternoon and h < 12:
        h += 12
    if not afternoon and h >= 12:
        raise FetchError("a morning cell after noon")
    return f"{h:02d}:{m:02d}"


def ampm(cell):
    """'6:05 am' / '1:05 pm' / '6:05AM' -> 24-hour."""
    m = re.match(r"^\s*(\d{1,2}):(\d{2})\s*([AaPp])\.?[Mm]\.?\s*$", str(cell))
    if not m:
        raise FetchError("a cell that is not a 12-hour time")
    h, mi = int(m.group(1)) % 12, int(m.group(2))
    if m.group(3).lower() == "p":
        h += 12
    return f"{h:02d}:{mi:02d}"


def ift_rows(body, bad):
    """{date: [F, S, D, A, M, I]} of IFT's CSV: one whole year, every date once (a repeated date's
    first row kept, as ca-toronto.tsv's table did), in order; a day out of order goes to `bad`."""
    text = body.decode("utf-8-sig", "replace")
    reader = csv.DictReader(io.StringIO(text))
    if not reader.fieldnames or any(c not in reader.fieldnames for c in IFT_COLUMNS):
        raise FetchError(f"the CSV's header is not IFT's ({len(reader.fieldnames or [])} columns)")
    rows = {}
    for r in reader:
        try:
            date = dt.date.fromisoformat(r["PrayerDate"].strip())
        except ValueError:
            raise FetchError("a PrayerDate that is not a date")
        if date.isoformat() in rows:
            continue
        try:
            rows[date.isoformat()] = ordered([twelve(r["FajarBegins"], False), twelve(r["Sunrise"], False), twelve(r["ZuharBegins"], True),
                                              twelve(r["AsarBegins"], True), twelve(r["MagribBegins"], True), twelve(r["IshaBegins"], True)],
                                             date.isoformat())
        except FetchError as e:
            bad.append(f"{date}: {e}")
            rows[date.isoformat()] = None
    years = {d[:4] for d in rows}
    if len(years) != 1:
        raise FetchError(f"the CSV spans {len(years)} years, not one")
    year = int(years.pop())
    want = 366 if calendar.isleap(year) else 365
    if len(rows) != want or list(rows) != sorted(rows):
        raise FetchError(f"the CSV holds {len(rows)} days of {year}, not the year's {want} in order")
    return year, {d: t for d, t in rows.items() if t is not None}


def iit_month(body, month, bad):
    """{date: [F, S, D, A, M, I]} of one IIT month: the heading must name `month`, and every row's
    date must be a day of it, each once; Asr Standard."""
    page = body.decode("utf-8", "replace")
    heading = re.search(r'<th class="prayerName" colspan="2">\s*([A-Za-z]+)\s*</th>', page)
    if not heading or MONTHS.get(heading.group(1).lower()) != month:
        raise FetchError(f"the table's heading is not month {month}")
    rows = {}
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", page, flags=re.S):
        tds = [re.sub(r"<[^>]+>", " ", x) for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if not tds:
            continue
        if len(tds) != 14:
            raise FetchError(f"a row of {len(tds)} cells, not 14")
        m = re.search(r"([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})", tds[0])
        if not m or MONTHS.get(m.group(1).lower()) != month:
            raise FetchError(f"a row dated outside month {month}")
        date = dt.date(int(m.group(3)), month, int(m.group(2))).isoformat()
        if date in rows:
            raise FetchError(f"{date} twice")
        # Date, Day, Fajr begins, iqamah, Sunrise, Zuhr begins, iqamah, Asr Standard, Hanafi, iqamah, Maghrib begins, iqamah, Isha begins, iqamah
        try:
            rows[date] = ordered([ampm(tds[i].strip()) for i in (2, 4, 5, 7, 10, 12)], date)
        except FetchError as e:
            bad.append(f"{date}: {e}")
    if not rows:
        raise FetchError("no days in the month's table")
    year = int(min(rows)[:4])
    if len(rows) + sum(1 for b in bad if b.startswith(f"{year}-{month:02d}")) != calendar.monthrange(year, month)[1]:
        raise FetchError(f"{len(rows)} days, month {month} of {year} has {calendar.monthrange(year, month)[1]}")
    return rows


def clock_slips(rows, zone="America/Toronto"):
    """The dates of `rows` ({date: [six 24-hour times]}) printed on the wrong clock: where the zone
    changes its offset on one day and a column of the table jumps by about as much on another
    (within a fortnight, the same way), the days between; each column is compared on its own (IFT's
    2026 file moves its sunrise and Maghrib a day before its Fajr and Dhuhr). Returns (set of dates,
    [(the last date on the wrong clock, or None, note)])."""
    try:
        from zoneinfo import ZoneInfo
        tz = ZoneInfo(zone)
    except Exception:  # no zone data on this machine: nothing is left out, and the note says so
        return set(), [(None, f"no zone data for {zone}: clock changes not compared")]
    dates = sorted(rows)

    def day(d):
        return dt.date.fromisoformat(d)

    def offset(d):
        return int(dt.datetime.combine(day(d), dt.time(12), tz).utcoffset().total_seconds() // 60)

    pairs = [(a, b) for a, b in zip(dates, dates[1:]) if (day(b) - day(a)).days == 1]
    real = [(b, offset(b) - offset(a)) for a, b in pairs if offset(b) != offset(a)]
    slips, notes = set(), []
    for when, change in real:
        wrong = set()
        for col in range(len(rows[dates[0]])):
            jumps = []
            for a, b in pairs:
                step = minutes(rows[b][col]) - minutes(rows[a][col])
                if abs(step) >= 40 and (step > 0) == (change > 0) and abs((day(b) - day(when)).days) <= 14:
                    jumps.append(b)
            if not jumps:
                continue
            j = min(jumps, key=lambda x: abs((day(x) - day(when)).days))
            lo, hi = sorted((when, j))
            wrong.update(d for d in dates if lo <= d < hi)
        if wrong:
            slips |= wrong
            notes.append((max(wrong), f"the clock changes on {when}, the table's on another day: {min(wrong)} to {max(wrong)} are on the wrong clock "
                         "in one column or more (the table's own fault), left out"))
    return slips, notes


def keep(ctx, t, rows, label, faults=None):
    """Adds the days of `rows` from the first of the current month, less the days on the wrong clock
    and the recorded `faults` ({date: why})."""
    faults = faults or {}
    first = ctx.today.replace(day=1).isoformat()
    slips, notes = clock_slips(rows)
    for last, n in notes:
        if last is None or last >= first:
            ctx.note(f"{label}: {n}")
    left = sorted(d for d in faults if d in rows and d >= first)
    for why in sorted({faults[d] for d in left}):
        ctx.note(f"{label}: " + ", ".join(d for d in left if faults[d] == why) + f" left out: {why}")
    for date, times in rows.items():
        if date >= first and date not in slips and date not in left:
            t.add(date, times)
    t.merge = False


def table(key, name, unit, school, source_line, raw):
    return Table(key, name, None, None, "America/Toronto", "CA", "F+E S D A M I", entry=f"{unit}/{unit}", school=school,
                 source_line=source_line, raw=raw)


def fetch(ctx):
    tables = []
    # IFT
    try:
        body = ctx.http.get(IFT_CSV)
        bad = []
        year, rows = ift_rows(body, bad)
        for b in bad:
            ctx.error(f"IFT {b}; the day is left out")
        t = table(f"ift-{year}", f"IFT {year}", "ca.ift", "hanafi",
                  f"Islamic Foundation of Toronto {year}, {IFT_CSV} (the *Begins columns, 12-hour times read as the day's)",
                  [(f"ift-{year}.csv", body)])
        keep(ctx, t, rows, "IFT", IFT_FAULTS)
        if t.rows:
            tables.append(t)
        if year < ctx.today.year:
            ctx.note(f"IFT's CSV is still {year}'s: the next year's file is to be found by hand")
    except FetchError as e:
        ctx.error(f"IFT: {e}")
    # IIT
    by_year = {}
    raw = []
    for month in range(1, 13):
        try:
            body = ctx.http.get(IIT_AJAX.format(month=month), headers={"X-Requested-With": "XMLHttpRequest", "Referer": "https://islam.ca/"})
            bad = []
            rows = iit_month(body, month, bad)
        except (FetchError, ValueError) as e:
            ctx.error(f"IIT month {month}: {e}")
            continue
        for b in bad:
            ctx.error(f"IIT {b}; the day is left out")
        year = int(min(rows)[:4])
        by_year.setdefault(year, {}).update(rows)
        raw.append((f"iit-{year}-{month:02d}.html", body))
    for year, rows in sorted(by_year.items()):
        t = table(f"iit-{year}", f"IIT {year}", "ca.iit", "standard",
                  f"Islamic Institute of Toronto {year}, islam.ca's monthly table (admin-ajax get_monthly_timetable; Begins, Asr Standard)", raw)
        keep(ctx, t, rows, "IIT")
        if t.rows:
            tables.append(t)
    # MAC
    url = mawaqit.PAGE + MAC_SLUG
    try:
        page = ctx.http.text(url)
        conf = mawaqit.conf_data(page)
        plat, plon = conf.get("latitude"), conf.get("longitude")
        if not isinstance(plat, (int, float)) or not isinstance(plon, (int, float)) or abs(plat - MAC_POINT[0]) > 0.02 or abs(plon - MAC_POINT[1]) > 0.02:
            raise FetchError(f"the page's point ({plat}, {plon}) is not MAC Masjid Toronto's")
        if str(conf.get("timezone", "America/Toronto")) != "America/Toronto":
            raise FetchError(f"the page's zone is {conf.get('timezone')}, not America/Toronto")
        rows = mawaqit.calendar_rows(conf, ctx.today.year, 6)
        t = table(f"mac-{ctx.today.year}", f"MAC Masjid Toronto {ctx.today.year}", "ca.mac", "standard",
                  f"MAC Masjid Toronto: its Mawaqit calendar ({url}, confData.calendar) mapped onto {ctx.today.year}",
                  [(f"mawaqit-{MAC_SLUG}.html", page)])
        good = {}
        for date, times in rows.items():
            try:
                good[date] = ordered([norm_time(x) for x in times], date)
            except FetchError as e:
                ctx.error(f"MAC {e}; the day is left out")
        keep(ctx, t, good, "MAC")
        if t.rows:
            tables.append(t)
    except (FetchError, ValueError) as e:
        ctx.error(f"MAC: {e}")
    return tables
