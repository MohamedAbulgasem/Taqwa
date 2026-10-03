"""Kazakhstan, QMDB (muftyat.kz): the year API its own site calls, api.muftyat.kz/prayer-times/<year>/<lat>/<lon>,
at the gate's two cities (Almaty below 48N, Astana at or above it) and at four cities north of Astana
(Kokshetau, Kostanay, Pavlodar, Petropavl), the current year and the next. The API answers only at its
own places' coordinates (its city list, api.muftyat.kz/cities/), so the northern points are QMDB's own.
The northern tables are what the end of eating's margin at and above 48N is fitted on (end-of-eating
fix round, 3 Oct 2026): QMDB's AngleBased residual grows to the north, and for an end of eating a
deeper residual is a late end. The fast begins at the printed Fajr (the API's "imsak", after its Fajr,
is not used), so the Fajr column is also the end of eating (F+E)."""
import json

from common import FetchError, Table, add_all

SOURCE = "kz-qmdb"
BASE = "https://api.muftyat.kz/prayer-times"
ENTRY = "kz.qmdb"

# key, name, lat, lon, zone: the gate's two points (kz-qmdb.tsv) and QMDB's own northern cities.
POINTS = [
    ("almaty", "Almaty", "43.238293", "76.945465", "Asia/Almaty"),
    ("astana", "Astana", "51.133333", "71.433333", "Asia/Almaty"),
    ("kokshetau", "Kokshetau", "53.291667", "69.391667", "Asia/Almaty"),
    ("kostanay", "Kostanay", "53.219333", "63.634194", "Asia/Qostanay"),
    ("pavlodar", "Pavlodar", "52.315556", "76.956389", "Asia/Almaty"),
    ("petropavl", "Petropavl", "54.862222", "69.140833", "Asia/Almaty"),
]

FIELDS = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")


def parse(body):
    """The API's year: {"result": [{"Date": yyyy-mm-dd, "fajr": HH:MM, ...}, ...]}."""
    d = json.loads(body.decode("utf-8", "replace"))
    days = d.get("result") if isinstance(d, dict) else None
    if not isinstance(days, list) or not days:
        raise FetchError("no days in the response")
    rows = {}
    for x in days:
        try:
            rows[str(x["Date"])[:10]] = [x[k] for k in FIELDS]
        except (KeyError, TypeError) as e:
            raise FetchError(f"a day without {e}") from e
    return rows


def fetch(ctx):
    tables = []
    for year in (ctx.today.year, ctx.today.year + 1):
        for key, name, lat, lon, zone in POINTS:
            url = f"{BASE}/{year}/{lat}/{lon}"
            try:
                body = ctx.http.get(url)
                rows = parse(body)
            except FetchError as e:
                ctx.error(f"{name} {year}: {e}")
                continue
            t = Table(f"{key}-{year}", f"{name} {year}", float(lat), float(lon), zone, "KZ", "F+E S D A M I", entry=ENTRY,
                      school="hanafi", source_line=f"QMDB (muftyat.kz) {url} (the year API the official site calls)",
                      raw=[(f"{key}-{year}.json", body)])
            add_all(ctx, t, rows, f"{name} {year}")
            t.merge = False
            if t.rows:
                tables.append(t)
    return tables
