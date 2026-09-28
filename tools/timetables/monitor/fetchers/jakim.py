"""Malaysia, JAKIM e-solat: the year table of each of the thirteen fitted zones (my-jakim.tsv) from
e-solat's own API, and from October the year ahead from the public mirror api.waktusolat.app once
it appears (probed on WLY01: a 404 is "not yet")."""
import datetime as dt
import json

from common import FetchError, Table

SOURCE = "my-jakim"
ESOLAT = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone="
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
UTC8 = dt.timezone(dt.timedelta(hours=8))


def parse_esolat(body):
    d = json.loads(body.decode("utf-8", "replace"))
    rows = {}
    for r in d.get("prayerTime") or []:
        dd, mm, yy = r["date"].split("-")
        date = dt.date(int(yy), MONTHS[mm.lower()[:3]], int(dd)).isoformat()
        rows[date] = [r[k] for k in KEYS]
    if not rows:
        raise FetchError("no prayerTime rows (" + str(d)[:120] + ")")
    return rows, d.get("serverTime")


def parse_mirror(body, year, month):
    d = json.loads(body.decode("utf-8", "replace"))
    rows = {}
    for p in d.get("prayers") or []:
        date = dt.date(year, month, int(p["day"])).isoformat()
        rows[date] = [dt.datetime.fromtimestamp(p[k], UTC8).strftime("%H:%M") if p.get(k) else "-" for k in KEYS]
    if not rows:
        raise FetchError("no prayers rows")
    return rows


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
            t = Table(f"{zone}-{year}", f"{name} {zone} {year}", None, None, tz, "MY", "F S D A M I", entry=f"my.jakim/{zone}",
                      source_line=f"JAKIM e-solat {url} (serverTime {server})", raw=[(f"esolat-{zone}-{year}.json", body)])
            for date, times in days.items():
                t.add(date, times)
            tables.append(t)
    nxt = ctx.today.year + 1
    if ctx.today.month >= 10:
        probe = f"{MIRROR}WLY01?year={nxt}&month=1"
        try:
            ctx.http.get(probe)
            published = True
        except FetchError as e:
            published = False
            ctx.note(f"{nxt} not on the mirror yet ({e})")
        if published:
            for zone, name, tz in ZONES:
                held = ctx.held(f"{zone}-{nxt}")
                if len(held) >= 365:
                    continue
                t = Table(f"{zone}-{nxt}", f"{name} {zone} {nxt} (mirror)", None, None, tz, "MY", "F S D A M I", entry=f"my.jakim/{zone}",
                          source_line=f"JAKIM e-solat via the public mirror {MIRROR}{zone}?year={nxt}&month=1..12 (times as Unix seconds, shown in UTC+8)")
                for month in range(1, 13):
                    try:
                        body = ctx.http.get(f"{MIRROR}{zone}?year={nxt}&month={month}")
                        for date, times in parse_mirror(body, nxt, month).items():
                            t.add(date, times)
                        t.raw.append((f"mirror-{zone}-{nxt}-{month:02d}.json", body))
                    except FetchError as e:
                        ctx.error(f"{zone} {nxt}-{month:02d} (mirror): {e}")
                if t.rows:
                    tables.append(t)
    return tables
