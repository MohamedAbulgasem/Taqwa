"""Indonesia, Kemenag, as the open api.myquran.com republishes bimasislam.kemenag.go.id's
schedule: the current month and the next for the thirteen fitted kab/kota (id-kemenag.tsv) and
five more not held yet (new data for a later fit, reported, not gated); from October, the whole
next year once it appears (probed on the first place, then fetched for every place that lacks it).
Six columns as the gate's rows (the API's imsak, a precaution, is not kept)."""
import calendar
import json

from common import FetchError, Table

SOURCE = "id-kemenag"
BASE = "https://api.myquran.com/v2/sholat/jadwal"

# key, myQuran id, name, zone, entry (its unit for the fitted places), lat, lon (the app's points for the new ones).
PLACES = [
    ("jakarta", "1301", "Kota Jakarta", "Asia/Jakarta", "id.kemenag/1301", None, None),
    ("bandaaceh", "0119", "Kota Banda Aceh", "Asia/Jakarta", "id.kemenag/0119", None, None),
    ("makassar", "2622", "Kota Makassar", "Asia/Makassar", "id.kemenag/2622", None, None),
    ("denpasar", "1709", "Kota Denpasar", "Asia/Makassar", "id.kemenag/1709", None, None),
    ("jayapura", "3329", "Kota Jayapura", "Asia/Jayapura", "id.kemenag/3329", None, None),
    ("malang", "1634", "Kota Malang", "Asia/Jakarta", "id.kemenag/1634", None, None),
    ("bandung", "1219", "Kota Bandung", "Asia/Jakarta", "id.kemenag/1219", None, None),
    ("jayawijaya", "3308", "Kab. Jayawijaya", "Asia/Jayapura", "id.kemenag/3308", None, None),
    ("batu", "1630", "Kota Batu", "Asia/Jakarta", "id.kemenag/1630", None, None),
    ("bukittinggi", "0313", "Kota Bukittinggi", "Asia/Jakarta", "id.kemenag/0313", None, None),
    ("bogor", "1222", "Kota Bogor", "Asia/Jakarta", "id.kemenag/1222", None, None),
    ("garut", "1208", "Kab. Garut", "Asia/Jakarta", "id.kemenag/1208", None, None),
    ("wonosobo", "1429", "Kab. Wonosobo", "Asia/Jakarta", "id.kemenag/1429", None, None),
    ("surabaya", "1638", "Kota Surabaya (new)", "Asia/Jakarta", "id.kemenag", -7.2575, 112.7521),
    ("medan", "0228", "Kota Medan (new)", "Asia/Jakarta", "id.kemenag", 3.5952, 98.6722),
    ("semarang", "1433", "Kota Semarang (new)", "Asia/Jakarta", "id.kemenag", -6.9667, 110.4167),
    ("palembang", "0816", "Kota Palembang (new)", "Asia/Jakarta", "id.kemenag", -2.9761, 104.7754),
    ("yogyakarta", "1505", "Kota Yogyakarta (new)", "Asia/Jakarta", "id.kemenag", -7.8014, 110.3647),
]
KEYS = ("subuh", "terbit", "dzuhur", "ashar", "maghrib", "isya")


def parse(body):
    j = json.loads(body.decode("utf-8", "replace"))
    if not j.get("status"):
        raise FetchError("status false (" + str(j)[:120] + ")")
    rows = {}
    for x in j["data"]["jadwal"]:
        rows[x["date"]] = [x[k] for k in KEYS]
    return rows, j["data"].get("lokasi", "")


def months_wanted(today):
    """(year, month) of the current month and the next."""
    y, m = today.year, today.month
    nxt = (y + 1, 1) if m == 12 else (y, m + 1)
    return [(y, m), nxt]


def month_held(ctx, key, year, month):
    held = ctx.held(f"{key}-{year}")
    days = calendar.monthrange(year, month)[1]
    return all(f"{year}-{month:02d}-{d:02d}" in held for d in range(1, days + 1))


def fetch(ctx):
    tables = {}

    def table(key, name, zone, entry, lat, lon, year):
        tid = f"{key}-{year}"
        if tid not in tables:
            tables[tid] = Table(tid, f"{name} {year}", lat, lon, zone, "ID", "F S D A M I", entry=entry,
                                source_line=f"Kemenag as api.myquran.com republishes it ({BASE}/<id>/<yyyy>/<mm>, {name})")
        return tables[tid]

    def take(key, cid, name, zone, entry, lat, lon, year, month):
        url = f"{BASE}/{cid}/{year}/{month:02d}"
        body = ctx.http.get(url)
        rows, _ = parse(body)
        t = table(key, name, zone, entry, lat, lon, year)
        for date, times in rows.items():
            t.add(date, times)
        t.raw.append((f"{key}-{year}-{month:02d}.json", body))

    wanted = months_wanted(ctx.today)
    for key, cid, name, zone, entry, lat, lon in PLACES:
        for year, month in wanted:
            try:
                take(key, cid, name, zone, entry, lat, lon, year, month)
            except FetchError as e:
                ctx.error(f"{name} {year}-{month:02d}: {e}")
    # From October: the next year, once it is published (probed on the first place).
    nxt = ctx.today.year + 1
    if ctx.today.month >= 10:
        first = PLACES[0]
        try:
            take(first[0], first[1], first[2], first[3], first[4], first[5], first[6], nxt, 1)
            published = True
        except FetchError as e:
            published = False
            ctx.note(f"{nxt} not published yet on myQuran ({e})")
        if published:
            for key, cid, name, zone, entry, lat, lon in PLACES:
                for month in range(1, 13):
                    if month_held(ctx, key, nxt, month):
                        continue
                    try:
                        take(key, cid, name, zone, entry, lat, lon, nxt, month)
                    except FetchError as e:
                        ctx.error(f"{name} {nxt}-{month:02d}: {e}")
    return list(tables.values())
