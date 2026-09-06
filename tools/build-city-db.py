#!/usr/bin/env python3
"""Convert GeoNames cities15000 into the compact CSV Taqwa bundles.

Sources (CC BY 4.0): https://download.geonames.org/export/dump/
  - cities15000.zip        city records
  - admin1CodesASCII.txt   admin1 (region) code -> readable name
  - countryInfo.txt        ISO country code -> readable name

Output columns: name,region,country,countryCode,lat,lon,timezone

Comma handling: commas are stripped from the name/region/country fields when
writing, so the output CSV can be parsed with a naive split(","). This keeps
the Kotlin-side parser trivial and avoids needing quote-aware CSV parsing on
either end.
"""
import csv, io, sys, urllib.request, zipfile

BASE = "https://download.geonames.org/export/dump/"
CITIES_URL = BASE + "cities15000.zip"
ADMIN1_URL = BASE + "admin1CodesASCII.txt"
COUNTRY_URL = BASE + "countryInfo.txt"
OUT = "shared/src/commonMain/composeResources/files/cities.csv"


def clean(field: str) -> str:
    """Strip commas (and surrounding whitespace) so naive CSV split stays safe."""
    return field.replace(",", "").strip()


def fetch_text(url: str) -> str:
    with urllib.request.urlopen(url) as resp:
        return resp.read().decode("utf-8")


# --- admin1 code -> readable region name, e.g. "GB.ENG" -> "England" ---
admin1_names = {}
for line in fetch_text(ADMIN1_URL).splitlines():
    if not line.strip():
        continue
    f = line.split("\t")
    code = f[0]  # "GB.ENG"
    name = f[1]
    admin1_names[code] = name

# --- ISO country code -> readable country name ---
country_names = {}
for line in fetch_text(COUNTRY_URL).splitlines():
    if not line.strip() or line.startswith("#"):
        continue
    f = line.split("\t")
    iso = f[0]
    name = f[4]
    country_names[iso] = name

with urllib.request.urlopen(CITIES_URL) as resp:
    zf = zipfile.ZipFile(io.BytesIO(resp.read()))
    raw = zf.read("cities15000.txt").decode("utf-8")

rows = []
for line in raw.splitlines():
    f = line.split("\t")
    # 1 name, 8 country code, 10 admin1 code, 4 lat, 5 lon, 17 timezone, 14 population
    name = f[1]
    country_code = f[8]
    admin1_code = f[10]
    lat = f[4]
    lon = f[5]
    tz = f[17]
    population = int(f[14] or 0)

    region = admin1_names.get(f"{country_code}.{admin1_code}", "")
    country = country_names.get(country_code, "")

    rows.append((
        clean(name),
        clean(region),
        clean(country),
        country_code,
        lat,
        lon,
        tz,
        population,
    ))

rows.sort(key=lambda r: -r[7])  # population descending: London GB outranks London CA

with open(OUT, "w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["name", "region", "country", "countryCode", "lat", "lon", "tz"])
    for r in rows:
        w.writerow(r[:7])

print(f"wrote {len(rows)} cities to {OUT}", file=sys.stderr)
