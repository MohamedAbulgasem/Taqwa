"""Saudi Arabia, Umm al-Qura (KACST): the prayer-times API the official page (ummulqura.org.sa)
calls itself, GetPrayerByYear at each of the gate's twelve points (sa-ummalqura.tsv), the current
year and the next. The API prints no end of eating: the fast begins at its Fajr (F+E). The raw JSON
carries each day's Hijri month, which is what UmmAlQuraDates' Ramadan dates are regenerated from."""
import json

from common import FetchError, Table

SOURCE = "sa-ummalqura"
BASE = "https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerByYear"
HEADERS = {"Origin": "https://www.ummulqura.org.sa", "Referer": "https://www.ummulqura.org.sa/"}

# key, name, lat, lon: the gate's own query points (derived parameters, sa-ummalqura.tsv).
POINTS = [
    ("makkah", "Makkah", 21.426666, 39.831666),
    ("madinah", "Madinah", 24.54, 39.63),
    ("riyadh", "Riyadh", 24.67, 46.69),
    ("jeddah", "Jeddah", 21.5, 39.17),
    ("dammam", "Dammam", 26.44, 50.1),
    ("abha", "Abha", 18.22, 42.51),
    ("taif", "Taif", 21.25, 40.4),
    ("tabuk", "Tabuk", 28.4, 36.58),
    ("haql", "Haql", 29.3, 34.95),
    ("al-kharkhir", "Al Kharkhir", 18.85, 51.819),
    ("at-tuwal", "At Tuwal", 16.528, 42.9685),
    ("turaif", "Turaif", 31.68, 38.66),
]


def parse(body):
    """The API's year: a list of days (sometimes JSON inside a JSON string)."""
    d = json.loads(body.decode("utf-8", "replace"))
    while isinstance(d, str):
        d = json.loads(d)
    if not isinstance(d, list) or not d:
        raise FetchError("no days in the response")
    rows = {}
    for x in d:
        p = x["prayerTimes"]
        rows[x["date"][:10]] = [p["fajr"], p["sunrise"], p["dhuhr"], p["asr"], p["maghrib"], p["isha"]]
    return rows


def fetch(ctx):
    tables = []
    for year in (ctx.today.year, ctx.today.year + 1):
        for key, name, lat, lon in POINTS:
            url = f"{BASE}?lang=en&format=24&lat={lat}&lon={lon}&zone=3&yg={year}"
            try:
                body = ctx.http.get(url, headers=HEADERS)
                rows = parse(body)
            except FetchError as e:
                ctx.error(f"{name} {year}: {e}")
                continue
            t = Table(f"{key}-{year}", f"{name} {year}", lat, lon, "Asia/Riyadh", "SA", "F+E S D A M I", entry="sa.ummalqura",
                      source_line=f"Umm al-Qura (KACST) {url} (the JSON API the official page ummulqura.org.sa calls)",
                      raw=[(f"{key}-{year}.json", body)])
            for date, times in rows.items():
                t.add(date, times)
            t.merge = False
            tables.append(t)
    return tables
