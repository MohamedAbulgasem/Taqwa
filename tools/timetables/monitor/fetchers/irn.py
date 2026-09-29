"""Norway, Islamsk Råd Norge's joint calendars (bonnetid.info): the site's own month tables for
Oslo, Trondheim and Tromsø through its WordPress AJAX call (action get_prayer_times, with the
nonce its page carries), twelve months per city. The page shows one year at a time (the current
one; the next appears when IRN publishes it, usually in December), so a table is keyed by the year
read off the page. Both Asr columns are printed (As, Ah), no imsak. Restricted: archive only."""
import calendar
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

# city (as the site's select names it, folded), unit id (no-irn.tsv), name, lat, lon
CITIES = [
    ("oslo", "no.irn.oslo", "Oslo", 59.91273, 10.74609),
    ("trondheim", "no.irn.trondheim", "Trondheim", 63.43049, 10.39506),
    ("tromso", "no.irn.tromso", "Tromsø", 69.6496, 18.956),
]


def fold(s):
    return re.sub(r"[^a-z]", "", str(s).lower().replace("ø", "o").replace("ö", "o").replace("æ", "ae").replace("å", "a"))


def page_config(page):
    """(ajaxurl, nonce, {folded city: option value}) from the home page."""
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
    for sel in re.findall(r"<select[^>]*>(.*?)</select>", page, flags=re.S):
        opts = re.findall(r"<option[^>]*value=\"([^\"]*)\"[^>]*>([^<]*)</option>", sel)
        names = {fold(label): value for value, label in opts}
        if "oslo" in names:
            cities = names
            break
    if not cities:
        raise FetchError("no city select with Oslo on the page")
    return ajaxurl, nonce, cities


def parse_month(body, month=None):
    """The AJAX month: rows of (day, [fajr, sunrise, dhuhr, asr1, maghrib, isha, asr2]) and the year
    the html names. With `month`, a reply for another month is an error: the month the html names
    must be it, or, where it names none, the rows must be as many as the month has days (a handler
    that ignored the month asked for would otherwise store one month under all twelve)."""
    j = json.loads(body.decode("utf-8", "replace"))
    if not j.get("success"):
        raise FetchError("success false")
    h = j["data"]["html"] if isinstance(j.get("data"), dict) else str(j.get("data"))
    rows = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", h, flags=re.S):
        tds = [re.sub(r"<[^>]+>", "", x).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if len(tds) >= 9 and tds[0].isdigit():
            fajr, sunr, dhuhr, asr1, asr2, magh, isha = tds[2:9]
            rows.append((int(tds[0]), [fajr, sunr, dhuhr, asr1, magh, isha, asr2]))
    text = re.sub(r"<[^>]+>", " ", h)
    years = re.findall(r"\b(20\d\d)\b", text)
    year = max(set(years), key=years.count) if years else None
    if month is not None:
        # The heading names the month beside its year ("Januar 2027"); a month word in prose ("times
        # may vary") is not a heading and never decides.
        named = [MONTHS[w.lower()] for w, _ in re.findall(r"([A-Za-zÆØÅæøå]+)\s+(20\d\d)", text) if w.lower() in MONTHS]
        if named:
            if month not in named:
                raise FetchError(f"the reply names month {named[0]}, not {month}")
        else:
            days = calendar.monthrange(int(year), month)[1] if year else None
            if days is not None and len(rows) != days:
                raise FetchError(f"the reply has {len(rows)} days, month {month} of {year} has {days}")
    return rows, year


def fetch(ctx):
    page = ctx.http.text(HOME)
    ajaxurl, nonce, cities = page_config(page)
    tables = []
    for key, unit, name, lat, lon in CITIES:
        value = cities.get(key)
        if value is None:
            ctx.error(f"{name}: not in the site's city list ({', '.join(sorted(cities))})")
            continue
        rows_by_year = {}
        raw = []
        for month in range(1, 13):
            try:
                body = ctx.http.get(ajaxurl, data={"action": "get_prayer_times", "nonce": nonce, "city": value, "month": month},
                                    headers={"X-Requested-With": "XMLHttpRequest", "Referer": HOME})
                rows, year = parse_month(body, month)
            except (FetchError, KeyError, ValueError) as e:
                ctx.error(f"{name} month {month}: {e}")
                continue
            year = int(year) if year else ctx.today.year
            for day, times in rows:
                rows_by_year.setdefault(year, {})[f"{year}-{month:02d}-{day:02d}"] = times
            raw.append((f"irn-{key}-{year}-{month:02d}.json", body))
        for year, rows in rows_by_year.items():
            t = Table(f"{key}-{year}", f"IRN {name} {year}", lat, lon, "Europe/Oslo", "NO", "F+E S D As M I Ah", entry=f"no.irn/{unit}", school="-",
                      source_line=f"Islamsk Råd Norge joint calendar {year}, {name} ({HOME}, the site's month tables)", raw=raw)
            for date, times in rows.items():
                try:
                    t.add(date, times)
                except FetchError as e:
                    ctx.note(f"{name} {date}: {e}")
            if t.rows:
                tables.append(t)
    return tables
