#!/usr/bin/env python3
"""Convert GeoNames cities15000 into the compact CSVs Taqwa bundles.

Sources (CC BY 4.0): https://download.geonames.org/export/dump/
  - cities15000.zip        city records
  - admin1CodesASCII.txt   admin1 (region) code -> readable name
  - countryInfo.txt        ISO country code -> readable name
  - alternateNamesV2.zip   geonameId -> city name in other languages

Output:
  - cities.csv          id,name,region,country,countryCode,lat,lon,timezone
  - city-names-<l>.csv  id,name  for each of ar, id, ur, bn, tr, fr

`id` is the GeoNames geonameId, the stable key the name files join on.

Comma handling: commas are stripped from the name/region/country fields when
writing, so the output CSV can be parsed with a naive split(","). This keeps
the Kotlin-side parser trivial and avoids needing quote-aware CSV parsing on
either end.

alternateNamesV2.txt is ~780 MB uncompressed, so it is streamed a line at a
time and only rows in one of the six languages, for a city that is actually
bundled, are kept. Pass a local alternateNamesV2.zip (or the already
uncompressed .txt) as the first argument to skip the 190 MB download:

    tools/build-city-db.py [path/to/alternateNamesV2.zip|.txt]
"""
import csv, io, os, sys, tempfile, urllib.request, zipfile

BASE = "https://download.geonames.org/export/dump/"
CITIES_URL = BASE + "cities15000.zip"
ADMIN1_URL = BASE + "admin1CodesASCII.txt"
COUNTRY_URL = BASE + "countryInfo.txt"
ALTNAMES_URL = BASE + "alternateNamesV2.zip"
OUT_DIR = "shared/src/commonMain/composeResources/files"
OUT = OUT_DIR + "/cities.csv"
LANGS = ["ar", "id", "ur", "bn", "tr", "fr"]


def clean(field: str) -> str:
    """Strip commas (and surrounding whitespace) so naive CSV split stays safe."""
    return field.replace(",", "").strip()


def fetch_text(url: str) -> str:
    with urllib.request.urlopen(url) as resp:
        return resp.read().decode("utf-8")


def writer(fh):
    """csv.writer that emits LF, matching the bundled files."""
    return csv.writer(fh, lineterminator="\n")


def alt_names_lines(local: str | None):
    """Yield alternateNamesV2.txt lines, from a local file or the download."""
    if local and local.endswith(".txt"):
        with open(local, encoding="utf-8") as fh:
            yield from fh
        return

    if local:
        path, tmp = local, None
    else:
        tmp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        print(f"downloading {ALTNAMES_URL}", file=sys.stderr)
        with urllib.request.urlopen(ALTNAMES_URL) as resp:
            while chunk := resp.read(1 << 20):
                tmp.write(chunk)
        tmp.close()
        path = tmp.name
    try:
        with zipfile.ZipFile(path) as zf, zf.open("alternateNamesV2.txt") as raw:
            yield from io.TextIOWrapper(raw, encoding="utf-8")
    finally:
        if tmp:
            os.unlink(tmp.name)


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
    # 0 geonameId, 1 name, 8 country code, 10 admin1 code, 4 lat, 5 lon,
    # 17 timezone, 14 population
    geoname_id = f[0]
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
        geoname_id,
        clean(name),
        clean(region),
        clean(country),
        country_code,
        lat,
        lon,
        tz,
        population,
    ))

rows.sort(key=lambda r: -r[8])  # population descending: London GB outranks London CA

with open(OUT, "w", newline="", encoding="utf-8") as fh:
    w = writer(fh)
    w.writerow(["id", "name", "region", "country", "countryCode", "lat", "lon", "tz"])
    for r in rows:
        w.writerow(r[:8])

print(f"wrote {len(rows)} cities to {OUT}", file=sys.stderr)

# --- alternate names: one file per language, for bundled cities only ---
bundled = {r[0] for r in rows}
wanted = set(LANGS)
# lang -> geonameId -> (name, is_preferred); first non-historic wins unless a
# later row is flagged isPreferredName.
picked = {lang: {} for lang in LANGS}

for line in alt_names_lines(sys.argv[1] if len(sys.argv) > 1 else None):
    # 0 alternateNameId, 1 geonameId, 2 isolanguage, 3 alternate name,
    # 4 isPreferredName, 5 isShortName, 6 isColloquial, 7 isHistoric
    f = line.rstrip("\n").split("\t")
    if len(f) < 4 or f[2] not in wanted or f[1] not in bundled:
        continue
    if len(f) > 7 and f[7] == "1":  # historic name
        continue
    name = clean(f[3])
    if not name:
        continue
    preferred = len(f) > 4 and f[4] == "1"
    seen = picked[f[2]].get(f[1])
    if seen is None or (preferred and not seen[1]):
        picked[f[2]][f[1]] = (name, preferred)

for lang in LANGS:
    path = f"{OUT_DIR}/city-names-{lang}.csv"
    names = picked[lang]
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = writer(fh)
        w.writerow(["id", "name"])
        for gid in sorted(names, key=int):
            w.writerow([gid, names[gid][0]])
    print(f"wrote {len(names)} {lang} names to {path}", file=sys.stderr)
