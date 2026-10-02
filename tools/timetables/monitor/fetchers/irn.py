"""Norway, Islamsk Råd Norge's joint calendars (bonnetid.info): the site's own month tables for
Oslo, Trondheim and Tromsø through its WordPress AJAX call (action get_prayer_times, with the
nonce its page carries), twelve months per city. The page shows one year at a time (the current
one; the next appears when IRN publishes it, usually in December), so a table is keyed by its year.
Both Asr columns are printed (As, Ah), no imsak. Restricted: archive only.

Since late September 2026 the page lists its cities as a searchable list (`div.city-option` with
`data-value`, the city's name, which the AJAX call takes) instead of a `<select>`; both are read.
Trondheim's calendar is listed as "Trondheim - Jakobsli - Heimdal - Saupstad", so a city is found by
its name or by the first part of a joint name. The reply's html names neither the month nor the
year: the month is the one its month bar marks active (else a heading, else the day count), and the
year is the one, of the three around today, in which every row's weekday falls on its day (a
heading's year must agree). A reply for another city, another month, or with days that fall on no
such year is refused. No message quotes a cell."""
import calendar
import datetime as dt
import json
import re
import urllib.parse

from common import FetchError, Table

SOURCE = "no-irn"
HOME = "https://bonnetid.info/"
HOST = "bonnetid.info"
# The month a reply names, in the site's Norwegian or in English, folded.
MONTHS = {
    "januar": 1, "january": 1, "februar": 2, "february": 2, "mars": 3, "march": 3, "april": 4, "mai": 5, "may": 5,
    "juni": 6, "june": 6, "juli": 7, "july": 7, "august": 8, "september": 9, "oktober": 10, "october": 10,
    "november": 11, "desember": 12, "december": 12,
}
# The weekday a row names (the site's Norwegian, or English), Monday 0.
WEEKDAYS = {"man": 0, "tir": 1, "ons": 2, "tor": 3, "fre": 4, "lor": 5, "son": 6,
            "mon": 0, "tue": 1, "wed": 2, "thu": 3, "fri": 4, "sat": 5, "sun": 6}

# city (as the site names it, folded), unit id (no-irn.tsv), name, lat, lon
CITIES = [
    ("oslo", "no.irn.oslo", "Oslo", 59.91273, 10.74609),
    ("trondheim", "no.irn.trondheim", "Trondheim", 63.43049, 10.39506),
    ("tromso", "no.irn.tromso", "Tromsø", 69.6496, 18.956),
]


def fold(s):
    return re.sub(r"[^a-z]", "", str(s).lower().replace("ø", "o").replace("ö", "o").replace("æ", "ae").replace("å", "a"))


def page_config(page):
    """(ajaxurl, nonce, {folded city: value the AJAX call takes}) from the home page: the city list
    is the searchable one (`data-value` of each `city-option`) or, as before, a `<select>`."""
    m = re.search(r"prayerAjax\s*=\s*(\{.*?\});", page, flags=re.S)
    if not m:
        raise FetchError("no prayerAjax on the page")
    cfg = json.loads(m.group(1))
    ajaxurl = cfg.get("ajaxurl") or cfg.get("ajax_url")
    nonce = cfg.get("nonce")
    if not ajaxurl or not nonce:
        raise FetchError(f"prayerAjax without ajaxurl or nonce ({sorted(cfg)})")
    host = urllib.parse.urlsplit(ajaxurl).hostname or ""
    if host != HOST and not host.endswith("." + HOST):
        raise FetchError(f"the page's ajaxurl points at {host or '?'}, not {HOST}: not followed")
    cities = {}
    options = re.findall(r"<div[^>]*class=\"[^\"]*\bcity-option\b[^\"]*\"[^>]*data-value=\"([^\"]*)\"", page)
    options += re.findall(r"<div[^>]*data-value=\"([^\"]*)\"[^>]*class=\"[^\"]*\bcity-option\b", page)
    if options:
        cities = {fold(v): v for v in options}
    if "oslo" not in cities:
        cities = {}
        for sel in re.findall(r"<select[^>]*>(.*?)</select>", page, flags=re.S):
            opts = re.findall(r"<option[^>]*value=\"([^\"]*)\"[^>]*>([^<]*)</option>", sel)
            names = {fold(label): value for value, label in opts}
            if "oslo" in names:
                cities = names
                break
    if not cities:
        raise FetchError("no city list with Oslo on the page (neither city-option entries nor a select)")
    return ajaxurl, nonce, cities


def find_city(cities, key):
    """The value for `key`: its own name, else the one joint name whose first part it is
    ("Trondheim - Jakobsli - Heimdal - Saupstad"); None when there is none or more than one."""
    if key in cities:
        return cities[key]
    joint = [v for v in cities.values() if fold(re.split(r"\s+[-–—/]\s+|,", v)[0]) == key]
    return joint[0] if len(joint) == 1 else None


def month_years(rows, month, candidates):
    """The years among `candidates` in which `month` has exactly these days, each on the weekday its
    row names. Rows naming no weekday the site uses leave only the day count to decide."""
    out = []
    for y in candidates:
        days = calendar.monthrange(y, month)[1]
        if sorted(d for d, _, _ in rows) != list(range(1, days + 1)):
            continue
        named = [(d, WEEKDAYS[fold(w)[:3]]) for d, w, _ in rows if fold(w)[:3] in WEEKDAYS]
        if all(dt.date(y, month, d).weekday() == wd for d, wd in named):
            out.append(y)
    return out


def parse_month(body, month=None, city=None, today=None):
    """The AJAX month: rows of (day, [fajr, sunrise, dhuhr, asr1, maghrib, isha, asr2]) and its year
    (a string, or None when nothing decides it). With `month`, a reply for another month is an error:
    the month the bar marks active, else the month a heading names beside a year ("Januar 2027"; a
    month word in prose never decides), must be it, and the rows must be its days. With `city` (the
    value asked for), a reply naming another city is an error. With `today`, the year is the one of
    the three around it in which the rows' weekdays fall on their days; a heading's year must agree,
    and rows that fit none of them are refused."""
    j = json.loads(body.decode("utf-8", "replace"))
    if not j.get("success"):
        raise FetchError("success false")
    data = j.get("data")
    h = data["html"] if isinstance(data, dict) else str(data)
    nav = (data.get("monthNav") or "") if isinstance(data, dict) else ""
    if city is not None and isinstance(data, dict) and data.get("city") and fold(data["city"]) != fold(city):
        raise FetchError(f"the reply is for {data['city']!r}, not {city!r}")
    rows = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", h, flags=re.S):
        tds = [re.sub(r"<[^>]+>", "", x).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if len(tds) >= 9 and tds[0].isdigit():
            fajr, sunr, dhuhr, asr1, asr2, magh, isha = tds[2:9]
            rows.append((int(tds[0]), tds[1], [fajr, sunr, dhuhr, asr1, magh, isha, asr2]))
    if not rows:
        raise FetchError("no day rows in the reply")
    text = re.sub(r"<[^>]+>", " ", h)
    headed = re.findall(r"([A-Za-zÆØÅæøå]+)\s+(20\d\d)", text)
    heading_years = [y for w, y in headed if w.lower() in MONTHS]
    years = heading_years or re.findall(r"\b(20\d\d)\b", text)
    year = max(set(years), key=years.count) if years else None
    if month is not None:
        active = re.findall(r"<a[^>]*data-month=\"(\d+)\"[^>]*class=\"[^\"]*\bactive\b", nav)
        active += re.findall(r"<a[^>]*class=\"[^\"]*\bactive\b[^\"]*\"[^>]*data-month=\"(\d+)\"", nav)
        named = [MONTHS[w.lower()] for w, _ in headed if w.lower() in MONTHS]
        if active:
            if {int(a) for a in active} != {month}:
                raise FetchError(f"the reply's month bar marks month {active[0]}, not {month}")
        elif named and month not in named:
            raise FetchError(f"the reply names month {named[0]}, not {month}")
        if today is not None:
            fits = month_years(rows, month, [today.year - 1, today.year, today.year + 1])
            if year is not None:
                if int(year) not in fits:
                    raise FetchError(f"the reply's {len(rows)} days do not fall as month {month} of {year} has them")
            elif len(fits) == 1:
                year = str(fits[0])
            else:
                raise FetchError(f"the reply's {len(rows)} days fall as month {month} has them in "
                                 + ("no year" if not fits else "more than one year") + f" around {today.year}")
        elif year is not None and not active and not named:
            days = calendar.monthrange(int(year), month)[1]
            if len(rows) != days:
                raise FetchError(f"the reply has {len(rows)} days, month {month} of {year} has {days}")
    return [(d, times) for d, _, times in rows], year


def fetch(ctx):
    page = ctx.http.text(HOME)
    ajaxurl, nonce, cities = page_config(page)
    tables = []
    for key, unit, name, lat, lon in CITIES:
        value = find_city(cities, key)
        if value is None:
            ctx.error(f"{name}: not in the site's city list ({len(cities)} cities)")
            continue
        rows_by_year = {}
        raw = []
        for month in range(1, 13):
            try:
                body = ctx.http.get(ajaxurl, data={"action": "get_prayer_times", "nonce": nonce, "city": value, "month": month},
                                    headers={"X-Requested-With": "XMLHttpRequest", "Referer": HOME})
                rows, year = parse_month(body, month, city=value, today=ctx.today)
            except (FetchError, KeyError, ValueError) as e:
                ctx.error(f"{name} month {month}: {e}")
                continue
            year = int(year)
            for day, times in rows:
                rows_by_year.setdefault(year, {})[f"{year}-{month:02d}-{day:02d}"] = times
            raw.append((f"irn-{key}-{year}-{month:02d}.json", body))
        for year, rows in rows_by_year.items():
            t = Table(f"{key}-{year}", f"IRN {name} {year}", lat, lon, "Europe/Oslo", "NO", "F+E S D As M I Ah", entry=f"no.irn/{unit}", school="-",
                      source_line=f"Islamsk Råd Norge joint calendar {year}, {name} ({HOME}, the site's month tables, city {value!r})", raw=raw)
            for date, times in rows.items():
                try:
                    t.add(date, times)
                except FetchError as e:
                    ctx.note(f"{name} {date}: {e}")
            if t.rows:
                tables.append(t)
    return tables
