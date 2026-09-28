"""Norway, Islamsk Råd Norge's joint calendars (bonnetid.info): the site's own month tables for
Oslo, Trondheim and Tromsø through its WordPress AJAX call (action get_prayer_times, with the
nonce its page carries), twelve months per city. The page shows one year at a time (the current
one; the next appears when IRN publishes it, usually in December), so a table is keyed by the year
read off the page. Both Asr columns are printed (As, Ah), no imsak. Restricted: archive only."""
import json
import re

from common import FetchError, Table

SOURCE = "no-irn"
HOME = "https://bonnetid.info/"

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


def parse_month(body):
    """The AJAX month: rows of (day, [fajr, sunrise, dhuhr, asr1, maghrib, isha, asr2]) and the year the html names."""
    j = json.loads(body.decode("utf-8", "replace"))
    if not j.get("success"):
        raise FetchError("success false (" + str(j)[:100] + ")")
    h = j["data"]["html"] if isinstance(j.get("data"), dict) else str(j.get("data"))
    rows = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", h, flags=re.S):
        tds = [re.sub(r"<[^>]+>", "", x).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if len(tds) >= 9 and tds[0].isdigit():
            fajr, sunr, dhuhr, asr1, asr2, magh, isha = tds[2:9]
            rows.append((int(tds[0]), [fajr, sunr, dhuhr, asr1, magh, isha, asr2]))
    years = re.findall(r"\b(20\d\d)\b", re.sub(r"<[^>]+>", " ", h))
    year = max(set(years), key=years.count) if years else None
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
                rows, year = parse_month(body)
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
