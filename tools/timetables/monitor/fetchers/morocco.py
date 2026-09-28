"""Morocco, the Ministry of Habous and Islamic Affairs (habous.gov.ma): each city's live page, the
current Hijri month, for the ten cities ma-habous.tsv checks. The page prints the legal time, UTC+0
all the year round since 20 September 2026 (ClockChanges), so the table is read with `clock` UTC
like the gate's live rows. Habous prints no imsak: the fast begins at its Fajr (F+E). Restricted:
archive only."""
import datetime as dt
import html
import re

from common import FetchError, Table

SOURCE = "ma-habous"
PAGE = "https://www.habous.gov.ma/prieres/index.php?ville="
MONTHS = {"يناير": 1, "فبراير": 2, "مارس": 3, "أبريل": 4, "ابريل": 4, "ماي": 5, "يونيو": 6, "يوليوز": 7, "غشت": 8,
          "شتنبر": 9, "أكتوبر": 10, "اكتوبر": 10, "نونبر": 11, "دجنبر": 12}

# unit id (ma-habous.tsv), name, the site's ville id
CITIES = [
    ("casablanca", "Casablanca", 58), ("rabat", "Rabat", 1), ("oujda", "Oujda", 31), ("tangier", "Tangier", 14),
    ("laayoune", "Laayoune", 156), ("dakhla", "Dakhla", 165), ("figuig", "Figuig", 33), ("midelt", "Midelt", 136),
    ("lagouira", "Lagouira", 166), ("ifrane", "Ifrane", 100),
]


def rows_of(page):
    i = page.find('id="horaire"')
    seg = page[i:] if i >= 0 else page
    out = []
    for r in re.findall(r"<tr[^>]*>(.*?)</tr>", seg, re.S):
        c = [re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", x))).strip()
             for x in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", r, re.S)]
        if c:
            out.append(c)
    return out


def parse(page, today):
    """{date: [6 times]} of the live month: the header names the Gregorian month(s), each row its
    Gregorian day; the year is the one that puts the day nearest today."""
    rows = rows_of(page)
    if not rows:
        raise FetchError("no table on the page")
    hdr = rows[0]
    gm = [MONTHS.get(x.strip()) for x in (hdr[2].split("/") if len(hdr) > 2 else [])]
    gm = [m for m in gm if m]
    if not gm:
        raise FetchError(f"no Gregorian month in the header ({' | '.join(hdr)[:80]})")
    out = {}
    mi = 0
    prev = 0
    for c in rows[1:]:
        if len(c) < 9 or not c[2].isdigit():
            continue
        g = int(c[2])
        if g < prev and mi + 1 < len(gm):
            mi += 1
        prev = g
        m = gm[mi]
        best = None
        for y in (today.year - 1, today.year, today.year + 1):
            try:
                d = dt.date(y, m, g)
            except ValueError:
                continue
            if best is None or abs((d - today).days) < abs((best - today).days):
                best = d
        if best is None or abs((best - today).days) > 45:
            continue
        t = c[3:9]
        if not all(re.fullmatch(r"\d{1,2}:\d{2}", x) for x in t):
            continue
        out[best.isoformat()] = t
    if not out:
        raise FetchError("no dated rows")
    return out


def fetch(ctx):
    tables = []
    for unit, name, ville in CITIES:
        url = PAGE + str(ville)
        try:
            page = ctx.http.text(url)
            rows = parse(page, ctx.today)
        except FetchError as e:
            ctx.error(f"{name}: {e}")
            continue
        sel = re.findall(r"<option[^>]*selected[^>]*>([^<]*)", page)
        t = Table(unit, name, None, None, "Africa/Casablanca", "MA", "F+E S D A M I", entry=f"ma.habous/{unit}", clock="UTC",
                  source_line=f"Ministry of Habous and Islamic Affairs, Morocco: {url} (the current Hijri month, legal time UTC+0; page selected '{sel[0].strip() if sel else '?'}')",
                  raw=[(f"habous-{ville}.html", page)])
        for date, times in rows.items():
            t.add(date, times)
        tables.append(t)
    return tables
