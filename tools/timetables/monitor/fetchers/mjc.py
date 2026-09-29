"""South Africa, the Muslim Judicial Council (MJC): its Salaah Times page (mjc.org.za/salaah-times/,
server-rendered by WordPress), whose "This Month" table is the current month alone, "Calculated for
Cape Town (SAST)". There is no month parameter, form or API, and the page drops a month when the
next begins, so each month is kept as its own capture (`cape-town-<yyyy>-<mm>`), never replaced by
the next, and the source's cadence is month-start: due as soon as a new month has begun in
Africa/Johannesburg since its last complete fetch (the workflow also runs on the 1st).

The table's caption names the place and the month ("Cape Town — September 2026"); its rows only a
weekday and a day, so a table whose rows are not that month's days in order, each on its own
weekday, is refused, and so is one whose caption names no month or another place, or whose header
does not name each of the six times once (the columns are read by their names). A day whose cells
do not read as times, or are not in the day's order, is left out and the fetch is partial: what is
held for that day stays. The page's "Today's Salaah Times" card is not the table; where its date
falls in the table's month its labelled times must be the table's row for that day, else the
columns are suspect and the fetch is partial. A fetch is complete only when the month shown is the
month it is in Cape Town as the fetch begins (a page still cached from the month before on the 1st
is kept, but the new month is not captured yet). The MJC's Fajr is also the end of sehri (Radio
786); like za-cape.tsv, only its Fajr is read (F). Checked as za.mjc at its unit (the Cape Town
point) and, there, as the member of za.cape. No message quotes a cell. Restricted: archive only."""
import calendar
import datetime as dt
import html
import re

from common import FetchError, Table, month_of, norm_time

SOURCE = "za-mjc"
PAGE = "https://mjc.org.za/salaah-times/"
ENTRY = "za.mjc/za.mjc"  # the entry's one unit, its Cape Town point (SouthAfrica.capeTownTables)
MONTH_NAMES = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
               "November", "December"]
MONTHS = {name.lower(): i + 1 for i, name in enumerate(MONTH_NAMES)}
MONTHS.update({name[:3].lower(): i + 1 for i, name in enumerate(MONTH_NAMES)}, sept=9)
WEEKDAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
WEEKDAYS = {name[:3].lower(): i for i, name in enumerate(WEEKDAY_NAMES)}
# The six times in the gate's order (F S D A M I): the name a message uses, the card's data attribute,
# and the header words (folded to letters) that name the column.
COLUMNS = [
    ("Fajr", "fajr", {"fajr"}),
    ("Sunrise", "sunrise", {"sunrise", "shuruq", "shurooq"}),
    ("Dhuhr", "dhuhr", {"dhuhr", "zuhr", "zohr", "dhuhur"}),
    ("Asr", "asr", {"asr"}),
    ("Maghrib", "maghrib", {"maghrib"}),
    ("Isha", "isha", {"isha", "esha", "ishaa"}),
]
CAPTION = re.compile(r"^(?:(?P<place>.*?)\s*[—–-]+\s*)?(?P<month>[A-Za-z]+)\s+(?P<year>\d{4})$")
ROW_DATE = re.compile(r"^(?P<weekday>[A-Za-z]{3,9})\.?,?\s+(?P<day>\d{1,2})$")
CARD_DATE = re.compile(r"(?P<day>\d{1,2})\s+(?P<month>[A-Za-z]+)\s+(?P<year>\d{4})")


def clean(fragment):
    """A fragment's text: tags dropped, entities read, spaces folded."""
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", fragment))).strip()


def fold(text):
    return re.sub(r"[^a-z]", "", text.lower())


def masked(text, limit=80):
    """Page text for a message: cut short, anything shaped like a time masked (ruling R69)."""
    return re.sub(r"\d{1,2}\s*[:.h]\s*\d{2}", "hh:mm", str(text))[:limit]


def cells(row):
    return [clean(c) for c in re.findall(r"<t[hd]\b[^>]*>(.*?)</t[hd]>", row, flags=re.S | re.I)]


def month_table(page):
    """(place, year, month, header cells, [body rows' cells]) of the table whose caption names a month
    and a year: the "This Month" table, never the today card."""
    seen = []
    for m in re.finditer(r"<table\b[^>]*>(.*?)</table>", page, flags=re.S | re.I):
        inner = m.group(1)
        cap = re.search(r"<caption\b[^>]*>(.*?)</caption>", inner, flags=re.S | re.I)
        caption = clean(cap.group(1)) if cap else ""
        c = CAPTION.match(caption)
        if not c or c.group("month").lower() not in MONTHS:
            seen.append(caption)
            continue
        head = re.search(r"<thead\b[^>]*>(.*?)</thead>", inner, flags=re.S | re.I)
        rest = inner[:head.start()] + inner[head.end():] if head else inner
        body = re.search(r"<tbody\b[^>]*>(.*?)</tbody>", rest, flags=re.S | re.I)
        trs = [cs for cs in (cells(r) for r in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body.group(1) if body else rest, flags=re.S | re.I)) if cs]
        if head:
            header = next((cs for cs in (cells(r) for r in re.findall(r"<tr\b[^>]*>(.*?)</tr>", head.group(1), flags=re.S | re.I)) if cs), [])
        else:
            header, trs = (trs[0], trs[1:]) if trs else ([], [])
        return (c.group("place") or "").strip(), int(c.group("year")), MONTHS[c.group("month").lower()], header, trs
    if seen:
        raise FetchError(f"no table captioned with a month and a year (captions: {'; '.join(repr(masked(s, 40)) for s in seen[:3])})")
    raise FetchError("no table on the page")


def column_indices(header):
    """Where each of the six times sits, by the header's names; each must be named exactly once."""
    folded = [fold(h) for h in header]
    out = []
    for name, _, words in COLUMNS:
        hits = [i for i, f in enumerate(folded) if f in words]
        if len(hits) != 1:
            raise FetchError(f"the header names {name} {len(hits)} times (header: {masked(' | '.join(header))}): refused")
        out.append(hits[0])
    if 0 in out:
        raise FetchError(f"the header's first column is a time, not the date (header: {masked(' | '.join(header))}): refused")
    return out


def parse(page):
    """(year, month, {day: [6 cell texts, F S D A M I]}) of the page's month table. The caption gives
    the month and year; a table whose rows are not exactly that month's days, in order, each on its
    own weekday, is refused (FetchError), as is one for another place."""
    place, year, month, header, rows = month_table(page)
    label = f"{MONTH_NAMES[month - 1]} {year}"
    if place and "cape town" not in place.lower():
        raise FetchError(f"the table is captioned for {masked(place, 40)!r}, not Cape Town: refused")
    if not place and not re.search(r"Calculated for Cape Town", clean(page), flags=re.I):
        raise FetchError("the table names no place and the page does not say it is calculated for Cape Town: refused")
    where = column_indices(header)
    length = calendar.monthrange(year, month)[1]
    if len(rows) != length:
        raise FetchError(f"the table has {len(rows)} rows, {label} has {length} days: refused")
    days = {}
    for n, row in enumerate(rows, start=1):
        d = ROW_DATE.match(row[0])
        weekday = d.group("weekday")[:3].lower() if d else None
        if not d or weekday not in WEEKDAYS:
            raise FetchError(f"row {n}'s date {masked(row[0], 24)!r} is not a weekday and a day: refused")
        day = int(d.group("day"))
        if day != n:
            raise FetchError(f"row {n} is day {day}, not {n} of {label}: refused")
        actual = dt.date(year, month, day).weekday()
        if WEEKDAYS[weekday] != actual:
            raise FetchError(f"row {n} says {WEEKDAY_NAMES[WEEKDAYS[weekday]]} {day}, but {day} {label} is a {WEEKDAY_NAMES[actual]}: "
                             "the rows are not that month's, refused")
        if len(row) <= max(where):
            raise FetchError(f"row {n} has {len(row)} cells, the header {len(header)}: refused")
        days[day] = [row[i] for i in where]
    return year, month, days


def minutes(cell):
    h, m = cell.split(":")
    return int(h) * 60 + int(m)


def today_cards(page):
    """The page's "today" cards: [(date iso, [6 times or None, F S D A M I])], from each card's own
    date line and its labelled data attributes (the page carries one hidden copy besides the one shown)."""
    out = []
    starts = [m for m in re.finditer(r"<div\b[^>]*\bmjc-salaah-today\b[^>]*>", page, flags=re.I)]
    for i, m in enumerate(starts):
        attrs = {k.lower(): v for k, v in re.findall(r'data-prayer-([a-z]+)="([^"]*)"', m.group(0), flags=re.I)}
        end = starts[i + 1].start() if i + 1 < len(starts) else len(page)
        line = re.search(r"<p\b[^>]*mjc-salaah-date[^>]*>(.*?)</p>", page[m.end():min(end, m.end() + 3000)], flags=re.S | re.I)
        d = CARD_DATE.search(clean(line.group(1))) if line else None
        if not d or d.group("month").lower() not in MONTHS:
            continue
        try:
            date = dt.date(int(d.group("year")), MONTHS[d.group("month").lower()], int(d.group("day"))).isoformat()
        except ValueError:
            continue
        times = []
        for _, attr, _ in COLUMNS:
            try:
                times.append(norm_time(attrs[attr]) if attr in attrs else None)
            except FetchError:
                times.append(None)
        out.append((date, times))
    return out


def fetch(ctx):
    begun = month_of(ctx.started)
    try:
        body = ctx.http.get(PAGE)
        page = body.decode("utf-8", "replace")
        year, month, days = parse(page)
    except FetchError as e:
        ctx.error(str(e))
        return []
    label = f"{MONTH_NAMES[month - 1]} {year}"
    t = Table(f"cape-town-{year}-{month:02d}", f"Cape Town, {label}", None, None, "Africa/Johannesburg", "ZA", "F S D A M I",
              entry=ENTRY, school="standard",
              source_line=f"Muslim Judicial Council (SA), Salaah Times: {PAGE} (the page's \"This Month\" table, {label}, "
                          "calculated for Cape Town (SAST); the page shows the current month only); columns Fajr, Sunrise, Dhuhr, Asr, "
                          "Maghrib, Isha",
              raw=[(f"salaah-times-{year}-{month:02d}.html", body)])
    left_out = []
    for day in sorted(days):
        date = dt.date(year, month, day).isoformat()
        try:
            t.add(date, days[day])
        except FetchError as e:
            left_out.append(f"{date} ({e})")
            continue
        read = [minutes(c) for c in t.rows[date] if c != "-"]
        if any(b <= a for a, b in zip(read, read[1:])):
            del t.rows[date]
            left_out.append(f"{date} (its times are not in the day's order)")
    if left_out:
        ctx.error(f"{label}: {len(left_out)} day{'s' if len(left_out) != 1 else ''} left out, what is held for "
                  f"{'them' if len(left_out) != 1 else 'it'} stays: {'; '.join(left_out[:5])}{' …' if len(left_out) > 5 else ''}")
    # A table read in part is merged into the held capture; a whole one replaces it (the MJC's corrections).
    t.merge = bool(left_out)
    for date, times in today_cards(page):
        row = t.rows.get(date)
        if row is None or not date.startswith(f"{year}-{month:02d}-"):
            continue
        differ = [COLUMNS[i][0] for i, v in enumerate(times) if v is not None and v != row[i]]
        if differ:
            ctx.error(f"the page's today card for {date} disagrees with the table's row in {', '.join(differ)}: "
                      "the table's columns may have moved (kept, and checked as read)")
            break
    if (year, month) != begun:
        ctx.error(f"the page shows {label} while it is {MONTH_NAMES[begun[1] - 1]} {begun[0]} in Cape Town: kept as "
                  f"{label}'s capture, but {MONTH_NAMES[begun[1] - 1]} is not captured yet (a page cached from before the month turned?)")
    return [t] if t.rows else []
