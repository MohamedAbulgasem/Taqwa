"""Malaysia, JAKIM e-solat: the year table of each of the thirteen fitted zones (my-jakim.tsv) from
e-solat's own API, and from October the year ahead, from e-solat itself too (one `period=duration`
request a zone, the form e-solat's own site posts; GET answers only the current year), with the public
mirror api.waktusolat.app (which copies e-solat exactly) as the fallback when e-solat cannot be read. A
zone whose year e-solat has not loaded yet (`NO_RECORD!`, the mirror's 404 "No data found") is a note,
never an error. The fast begins at Subuh (e-solat's imsak is a precaution), so the Fajr column is also
the end of eating (F+E), as my-jakim.tsv reads it since the audit of 3 October 2026.

JAKIM's own data carries slips now and then (PLS01's 2025-03-01 Fajr printed 12 hours off; WLY02's 2027
Maghrib in the morning on 20-25 May and its Asr an hour early on 10 January, e-solat's own figures,
12 Nov 2026). `slips` finds them, and the cell is written '-' with a note naming the day and column,
never a time: a morning column after noon or an afternoon one before it, a cell about one or twelve
hours off its column's neighbours, and a day whose cells still do not run in the day's order (left
out whole: every cell '-', so an earlier capture's row for it is not kept by the merge). The check holds no time of its own: it compares the table with itself."""
import datetime as dt
import json

from common import FetchError, Table, add_all, norm_time

SOURCE = "my-jakim"
ESOLAT = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone="
ESOLAT_DURATION = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=duration&zone="
MIRROR = "https://api.waktusolat.app/v2/solat/"
MONTHS = {"jan": 1, "feb": 2, "mac": 3, "mar": 3, "apr": 4, "mei": 5, "may": 5, "jun": 6, "jul": 7,
          "ogo": 8, "aug": 8, "sep": 9, "okt": 10, "oct": 10, "nov": 11, "dis": 12, "dec": 12}
ZONES = [
    ("WLY01", "Kuala Lumpur", "Asia/Kuala_Lumpur"), ("SGR01", "Shah Alam", "Asia/Kuala_Lumpur"),
    ("JHR01", "Pulau Aur", "Asia/Kuala_Lumpur"), ("JHR02", "Johor Bahru", "Asia/Kuala_Lumpur"),
    ("KTN01", "Kota Bharu", "Asia/Kuala_Lumpur"), ("PHG01", "Pulau Tioman", "Asia/Kuala_Lumpur"),
    ("PHG06", "Cameron Highlands", "Asia/Kuala_Lumpur"), ("PLS01", "Kangar", "Asia/Kuala_Lumpur"),
    ("PNG01", "George Town", "Asia/Kuala_Lumpur"), ("SBH01", "Sandakan", "Asia/Kuching"),
    ("SBH07", "Kota Kinabalu", "Asia/Kuching"), ("SWK08", "Kuching", "Asia/Kuching"), ("WLY02", "Labuan", "Asia/Kuching"),
]
KEYS = ("fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha")
NAMES = ("Fajr", "Syuruk", "Dhuhr", "Asr", "Maghrib", "Isha")
UTC8 = dt.timezone(dt.timedelta(hours=8))
MORNING = (0, 1)        # columns before noon in Malaysia, whatever the day
AFTERNOON = (3, 4, 5)   # columns after it
NEIGHBOURS = 10         # days either side a cell is compared with (its column's median there)
OFF = 45                # minutes off that median that make a cell a slip (the year's drift over 10 days is far less)


class NotYet(FetchError):
    """e-solat (NO_RECORD!) or the mirror (404, "No data found") says the year is not loaded yet."""


def minutes(t):
    h, m = t.split(":")
    return int(h) * 60 + int(m)


def slips(rows):
    """{date: [6 'HH:MM']} -> ({date: [cells, slips '-']}, [notes], {dates left out, all '-'}). See the module's
    doc; a note names the day, the column and how far off it is, never a printed time."""
    out = {d: list(v) for d, v in rows.items()}
    notes = []
    for col in range(6):
        for d in sorted(out):
            cell = out[d][col]
            if cell == "-":
                continue
            m = minutes(cell)
            if col in MORNING and m >= 12 * 60:
                out[d][col] = "-"
                notes.append(f"{d} {NAMES[col]} printed after noon")
                continue
            if col in AFTERNOON and m < 12 * 60:
                out[d][col] = "-"
                notes.append(f"{d} {NAMES[col]} printed before noon")
                continue
    for col in range(6):
        values = {d: minutes(v[col]) for d, v in rows.items() if out[d][col] != "-"}
        for d in sorted(values):
            day = dt.date.fromisoformat(d)
            near = sorted(values[x] for x in (
                (day + dt.timedelta(days=k)).isoformat() for k in range(-NEIGHBOURS, NEIGHBOURS + 1) if k)
                if x in values)
            if len(near) < 4:
                continue
            median = near[len(near) // 2] if len(near) % 2 else (near[len(near) // 2 - 1] + near[len(near) // 2]) / 2
            off = values[d] - median
            if abs(off) >= OFF:
                out[d][col] = "-"
                hours = round(abs(off) / 60)
                notes.append(f"{d} {NAMES[col]} about {hours} hour{'s' if hours != 1 else ''} {'late' if off > 0 else 'early'} against its neighbours")
    dropped = set()
    for d in sorted(out):
        m = [minutes(c) for c in out[d] if c != "-"]
        if any(b <= a for a, b in zip(m, m[1:])):
            dropped.add(d)
            notes.append(f"{d} not in the day's order: left out")
    for d in dropped:
        # Written as six '-', not deleted: the live file merges captures, so a deleted day would keep an
        # earlier capture's row for it.
        out[d] = ["-"] * 6
    return out, notes, dropped


def parse_esolat(body):
    try:
        d = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        raise FetchError(f"e-solat's answer is not JSON ({len(body)} bytes)")
    if not isinstance(d, dict):
        raise FetchError("e-solat's answer is not an object")
    if "NO_RECORD" in str(d.get("status") or ""):
        raise NotYet(f"e-solat has no record for the request yet (status {str(d.get('status'))[:20]})")
    rows = {}
    for r in d.get("prayerTime") or []:
        dd, mm, yy = r["date"].split("-")
        date = dt.date(int(yy), MONTHS[mm.lower()[:3]], int(dd)).isoformat()
        rows[date] = [r[k] for k in KEYS]
    if not rows:
        raise FetchError("no prayerTime rows (" + str(d)[:120] + ")")
    return rows, d.get("serverTime")


def parse_mirror(body, year, month):
    try:
        d = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        raise FetchError(f"the mirror's answer is not JSON ({len(body)} bytes)")
    rows = {}
    for p in d.get("prayers") or []:
        date = dt.date(year, month, int(p["day"])).isoformat()
        rows[date] = [dt.datetime.fromtimestamp(p[k], UTC8).strftime("%H:%M") if p.get(k) else "-" for k in KEYS]
    if not rows:
        raise FetchError("no prayers rows")
    return rows


def checked(ctx, t, rows, label):
    """`rows` (24-hour cells, normalised) through `slips` into `t`, each slip a note; the count added."""
    normal = {}
    for date, times in rows.items():
        try:
            normal[date] = [norm_time(x) for x in times]
        except FetchError as e:
            ctx.note(f"{label} {date}: {e}")
    clean, notes, _ = slips(normal)
    if notes:
        ctx.note(f"{label}: JAKIM's own slips left out ({len(notes)}): " + "; ".join(notes))
    return add_all(ctx, t, clean, label)


def year_ahead_esolat(ctx, zone, year):
    """(rows, body, url) of `zone`'s whole `year` from e-solat (period=duration). NotYet when e-solat
    has not loaded it; FetchError otherwise."""
    url = ESOLAT_DURATION + zone
    body = ctx.http.get(url, data={"datestart": f"{year}-01-01", "dateend": f"{year}-12-31"})
    rows, server = parse_esolat(body)
    rows = {d: v for d, v in rows.items() if d.startswith(str(year))}
    if not rows:
        raise FetchError(f"no {year} rows in e-solat's answer")
    return rows, body, url, server


def year_ahead_mirror(ctx, zone, year, t):
    """The mirror's twelve months of `zone`'s `year` into `t`, checked for slips as one year (a slip at a
    month's edge still has neighbours both sides); NotYet when its first month is 'No data found'."""
    rows = {}
    for month in range(1, 13):
        try:
            body = ctx.http.get(f"{MIRROR}{zone}?year={year}&month={month}")
            rows.update(parse_mirror(body, year, month))
            t.raw.append((f"mirror-{zone}-{year}-{month:02d}.json", body))
        except (FetchError, KeyError, ValueError) as e:
            if month == 1 and "HTTP 404" in str(e):
                raise NotYet(f"not on the mirror yet ({e})")
            ctx.error(f"{zone} {year}-{month:02d} (mirror): {e}")
    checked(ctx, t, rows, f"{zone} {year} (mirror)")


def fetch(ctx):
    tables = []
    for zone, name, tz in ZONES:
        url = ESOLAT + zone
        try:
            body = ctx.http.get(url)
            rows, server = parse_esolat(body)
        except FetchError as e:
            ctx.error(f"{zone}: {e}")
            continue
        by_year = {}
        for date, times in rows.items():
            by_year.setdefault(date[:4], {})[date] = times
        for year, days in by_year.items():
            t = Table(f"{zone}-{year}", f"{name} {zone} {year}", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                      source_line=f"JAKIM e-solat {url} (serverTime {server})", raw=[(f"esolat-{zone}-{year}.json", body)])
            if checked(ctx, t, days, f"{zone} {year}"):
                tables.append(t)
    if ctx.today.month < 10:
        return tables
    nxt = ctx.today.year + 1
    waiting = []
    for zone, name, tz in ZONES:
        # e-solat first, every run (one request a zone, so a correction JAKIM makes is seen); the mirror
        # only when e-solat cannot be read and the year is not held whole yet.
        try:
            rows, body, url, server = year_ahead_esolat(ctx, zone, nxt)
            t = Table(f"{zone}-{nxt}", f"{name} {zone} {nxt}", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                      source_line=f"JAKIM e-solat {url} with datestart {nxt}-01-01, dateend {nxt}-12-31 (serverTime {server})",
                      raw=[(f"esolat-{zone}-{nxt}.json", body)])
            if checked(ctx, t, rows, f"{zone} {nxt}"):
                tables.append(t)
            continue
        except NotYet:
            waiting.append(zone)
            continue
        except FetchError as e:
            if len(ctx.held(f"{zone}-{nxt}")) >= 365:
                ctx.note(f"{zone} {nxt}: e-solat could not be read ({e}); the year is held whole, so the mirror is not asked")
                continue
            ctx.note(f"{zone} {nxt}: e-solat could not be read ({e}); the mirror is asked instead")
        t = Table(f"{zone}-{nxt}", f"{name} {zone} {nxt} (mirror)", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                  source_line=f"JAKIM e-solat via the public mirror {MIRROR}{zone}?year={nxt}&month=1..12 (times as Unix seconds, shown in UTC+8)")
        try:
            year_ahead_mirror(ctx, zone, nxt, t)
        except NotYet:
            waiting.append(zone)
            continue
        if t.rows:
            tables.append(t)
    if waiting:
        ctx.note(f"{nxt} not published yet for {', '.join(waiting)} (e-solat NO_RECORD! or the mirror's 404)")
    return tables
