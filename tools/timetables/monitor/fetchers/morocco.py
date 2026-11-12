"""Morocco, the Ministry of Habous and Islamic Affairs (habous.gov.ma): the ten cities ma-habous.tsv
checks at their units, and 29 more of the app's own Moroccan cities beyond them (EDGE_PLACES), each at
the app's city point, where the edge's national margins answer most of Morocco. Habous prints the legal
time, UTC+0 all the year round since 20 September 2026 (ClockChanges), so each table is read with
`clock` UTC like the gate's live rows. Habous prints no imsak: the fast begins at its Fajr (F+E).
Restricted: archive only.

Since late October 2026 the site is a maintenance notice and the old city pages
(prieres/index.php?ville=N) answer 404. Its times now come from static month files,
data/horaires-<yyyy>-<mm>.js: one whole Gregorian month for 190 places, as
`window.HORAIRES_MOIS.push({annee, mois, fuseau, cols, villes: [{c: ville id, n: Arabic name, h: [[six
times] per day]}]})`. The ids are the old pages' ville ids. A run reads the current month and the next;
the next is posted a few days before it begins (November's on 31 October), so a 404 for it is a note
("not posted yet"), never an error, while a 404 for the current month is one. The file must say
`fuseau` "GMT": another zone (Morocco back on summer time, say) is refused until the tables' clock is
reconsidered, never read an hour off. Each place must carry the Arabic name its old page showed, so an id
that now names another place is left out. Each city's file keeps every month fetched (the store merges);
the old page parser (`parse`) stays for the day the portal returns."""
import calendar
import datetime as dt
import html
import json
import re

from common import FetchError, Table, add_all

SOURCE = "ma-habous"
PAGE = "https://www.habous.gov.ma/prieres/index.php?ville="
MONTH_FILE = "https://www.habous.gov.ma/data/horaires-{y:04d}-{m:02d}.js"
# The month files' own column names, in the order F+E S D A M I reads them, and the zone they must name.
FILE_COLUMNS = ["Fajr", "Chorouq", "Dhuhr", "Asr", "Maghrib", "Ishae"]
FUSEAU = "GMT"
MONTHS = {"يناير": 1, "فبراير": 2, "مارس": 3, "أبريل": 4, "ابريل": 4, "ماي": 5, "يونيو": 6, "يوليوز": 7, "غشت": 8,
          "شتنبر": 9, "أكتوبر": 10, "اكتوبر": 10, "نونبر": 11, "دجنبر": 12}
# The six time columns the page heads, in the order the table reads them (F+E S D A M I): Fajr, sunrise,
# Dhuhr, Asr, Maghrib, Isha. A page headed otherwise (a Ramadan page with an imsak column, say) is refused,
# never read one column off.
COLUMNS = ["الصبح", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"]

# unit id (ma-habous.tsv), name, the site's ville id
CITIES = [
    ("casablanca", "Casablanca", 58), ("rabat", "Rabat", 1), ("oujda", "Oujda", 31), ("tangier", "Tangier", 14),
    ("laayoune", "Laayoune", 156), ("dakhla", "Dakhla", 165), ("figuig", "Figuig", 33), ("midelt", "Midelt", 136),
    ("lagouira", "Lagouira", 166), ("ifrane", "Ifrane", 100),
]
# The Arabic name each unit's ville id carried on its old page (the held captures' 'page selected'), which
# the month files' `n` must repeat.
CITY_LABELS = {58: "الدار البيضاء", 1: "الرباط", 31: "وجدة", 14: "طنجة", 156: "العيون", 165: "الداخلة", 33: "فكيك",
               136: "ميدلت", 166: "الكويرة", 100: "يفرن"}

# The app's cities (shared/.../files/cities.csv, GeoNames) whose Arabic name is a place on Habous's list,
# beyond the ten units but Azrou (inside Ifrane's): key, name, the site's ville id, the place the page must
# show selected, the app's city point, its zone. Checked as ma.habous at that point, as ma-habous.tsv's rows
# read the month first held by hand (archive/tables/manual/ma-habous/2026-10-06/).
EDGE_PLACES = [
    ("fes", "Fes", 81, "فاس", 34.03313, -5.00028, "Africa/Casablanca"),
    ("marrakesh", "Marrakesh", 104, "مراكش", 31.63416, -7.99994, "Africa/Casablanca"),
    ("agadir", "Agadir", 117, "أكادير", 30.42018, -9.59815, "Africa/Casablanca"),
    ("meknes", "Meknes", 99, "مكناس", 33.89352, -5.54727, "Africa/Casablanca"),
    ("kenitra", "Kenitra", 7, "القنيطرة", 34.26101, -6.5802, "Africa/Casablanca"),
    ("tetouan", "Tétouan", 15, "تطوان", 35.57845, -5.36837, "Africa/Casablanca"),
    ("al-hoceima", "Al Hoceïma", 23, "الحسيمة", 35.25165, -3.93723, "Africa/Casablanca"),
    ("safi", "Safi", 111, "آسفي", 32.29939, -9.23718, "Africa/Casablanca"),
    ("khouribga", "Khouribga", 79, "خريبكة", 32.88108, -6.9063, "Africa/Casablanca"),
    ("el-jadida", "El Jadida", 66, "الجديدة", 33.25682, -8.50882, "Africa/Casablanca"),
    ("beni-mellal", "Beni Mellal", 73, "بني ملال", 32.33725, -6.34983, "Africa/Casablanca"),
    ("nador", "Nador", 39, "الناظور", 35.16813, -2.93352, "Africa/Casablanca"),
    ("taza", "Taza", 89, "تازة", 34.21, -4.01, "Africa/Casablanca"),
    ("settat", "Settat", 61, "سطات", 33.00103, -7.61662, "Africa/Casablanca"),
    ("larache", "Larache", 16, "العرائش", 35.19321, -6.15572, "Africa/Casablanca"),
    ("guelmim", "Guelmim", 149, "كلميم", 28.98696, -10.05738, "Africa/Casablanca"),
    ("khenifra", "Khenifra", 70, "خنيفرة", 32.93492, -5.66167, "Africa/Casablanca"),
    ("berkane", "Berkane", 32, "بركان", 34.92, -2.32, "Africa/Casablanca"),
    ("oued-zem", "Oued Zem", 80, "وادي زم", 32.8627, -6.57359, "Africa/Casablanca"),
    ("taroudant", "Taroudant", 118, "تارودانت", 30.47028, -8.87695, "Africa/Casablanca"),
    ("essaouira", "Essaouira", 106, "الصويرة", 31.5125, -9.77, "Africa/Casablanca"),
    ("tiznit", "Tiznit", 119, "تزنيت", 29.69742, -9.73162, "Africa/Casablanca"),
    ("tan-tan", "Tan-Tan", 152, "طانطان", 28.43799, -11.10321, "Africa/Casablanca"),
    ("azrou", "Azrou", 103, "آزرو", 33.43443, -5.22126, "Africa/Casablanca"),
    ("chefchaouen", "Chefchaouen", 18, "شفشاون", 35.16878, -5.2636, "Africa/Casablanca"),
    ("boujdour", "Boujdour", 158, "بوجدور", 26.13073, -14.48513, "Africa/El_Aaiun"),
    ("smara", "Smara", 157, "السمارة", 26.73841, -11.67194, "Africa/Casablanca"),
    ("sidi-ifni", "Sidi Ifni", 148, "سيدي إفني", 29.37975, -10.17299, "Africa/Casablanca"),
    ("goulmima", "Goulmima", 132, "كلميمة", 31.69227, -4.95256, "Africa/Casablanca"),
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
    if hdr[3:] != COLUMNS:
        raise FetchError(f"the table's time columns are not the six expected ({' | '.join(hdr[3:])[:80]})")
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


def parse_month_file(text):
    """(year, month, {ville id: (name, {date: [6 times]})}) of one month file. Refuses a file whose zone
    is not GMT, whose columns are not the six expected, or whose place has a day too few or too many."""
    i, j = text.find("push("), text.rfind(")")
    if i < 0 or j <= i:
        raise FetchError("not a month file (no push(...))")
    try:
        data = json.loads(text[i + 5:j])
    except ValueError as e:
        raise FetchError(f"the month file's data is not JSON ({e.__class__.__name__})")
    if not isinstance(data, dict):
        raise FetchError("the month file's data is not an object")
    try:
        year, month = int(data["annee"]), int(data["mois"])
    except (KeyError, TypeError, ValueError):
        raise FetchError("the month file names no year and month")
    if data.get("fuseau") != FUSEAU:
        raise FetchError(f"the month file's zone is {str(data.get('fuseau'))[:20]!r}, not {FUSEAU}: its clock must be "
                         "reconsidered before it is read (Morocco has kept UTC+0 since 20 September 2026)")
    if data.get("cols") != FILE_COLUMNS:
        raise FetchError(f"the month file's columns are not the six expected ({' | '.join(map(str, data.get('cols') or []))[:80]})")
    days = calendar.monthrange(year, month)[1]
    out = {}
    for v in data.get("villes") or []:
        try:
            ville, name, h = int(v["c"]), str(v["n"]).strip(), v["h"]
        except (KeyError, TypeError, ValueError):
            continue
        if not isinstance(h, list) or len(h) != days or any(not isinstance(r, list) or len(r) != 6 for r in h):
            out[ville] = (name, None)
            continue
        out[ville] = (name, {dt.date(year, month, d + 1).isoformat(): h[d] for d in range(days)})
    if not out:
        raise FetchError("the month file holds no place")
    return year, month, out


def months_to_read(today):
    """The current Gregorian month and the next: [(year, month)]."""
    nxt = (today.year + (today.month == 12), today.month % 12 + 1)
    return [(today.year, today.month), nxt]


def fetch(ctx):
    places = [(unit, name, ville, CITY_LABELS[ville], None, None, "Africa/Casablanca") for unit, name, ville in CITIES] + list(EDGE_PLACES)
    files = []  # (url, text, {ville: (name, rows)})
    for n, (y, m) in enumerate(months_to_read(ctx.today)):
        url = MONTH_FILE.format(y=y, m=m)
        try:
            text = ctx.http.text(url)
        except FetchError as e:
            if re.search(r"HTTP 404\b", str(e)) and n > 0:
                ctx.note(f"{y:04d}-{m:02d} not posted yet (HTTP 404): the Ministry posts each month a few days before it begins")
            else:
                ctx.error(f"{y:04d}-{m:02d}: {e}")
            continue
        try:
            fy, fm, villes = parse_month_file(text)
        except FetchError as e:
            ctx.error(f"{y:04d}-{m:02d}: {e}")
            continue
        if (fy, fm) != (y, m):
            ctx.error(f"{y:04d}-{m:02d}: the file holds {fy:04d}-{fm:02d}; left out")
            continue
        files.append((url, text, villes))
    tables = []
    for key, name, ville, label, lat, lon, zone in places:
        entry = f"ma.habous/{key}" if lat is None else "ma.habous"
        cc = "EH" if zone == "Africa/El_Aaiun" else "MA"
        used = [url for url, _, _ in files]
        t = Table(key, name, lat, lon, zone, cc, "F+E S D A M I", entry=entry, clock="UTC",
                  source_line=f"Ministry of Habous and Islamic Affairs, Morocco: {', '.join(used)} (whole Gregorian months, "
                              f"fuseau {FUSEAU} = legal time UTC+0; ville {ville} '{label}')")
        for url, text, villes in files:
            month = url.rsplit("horaires-", 1)[1][:-3]
            got = villes.get(ville)
            if got is None:
                ctx.error(f"{name}: ville {ville} is not in {month}'s file")
                continue
            shown, rows = got
            if shown != label:
                # A ville id that now names another place would check the wrong point.
                ctx.error(f"{name}: ville {ville} names another place in {month}'s file; left out")
                continue
            if rows is None:
                ctx.error(f"{name}: {month}'s file does not hold the whole month for ville {ville}; left out")
                continue
            if add_all(ctx, t, rows, name):
                t.raw.append((f"horaires-{month}.js", text))
        if t.rows:
            tables.append(t)
    return tables
