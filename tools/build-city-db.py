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
import csv, io, os, re, sys, tempfile, urllib.request, zipfile

BASE = "https://download.geonames.org/export/dump/"
CITIES_URL = BASE + "cities15000.zip"
ADMIN1_URL = BASE + "admin1CodesASCII.txt"
COUNTRY_URL = BASE + "countryInfo.txt"
ALTNAMES_URL = BASE + "alternateNamesV2.zip"
OUT_DIR = "shared/src/commonMain/composeResources/files"
OUT = OUT_DIR + "/cities.csv"
LANGS = ["ar", "id", "ur", "bn", "tr", "fr"]

# GeoNames rows the bundle leaves out, by geonameId, each with its reason. A city the app lists is a
# place people pick to get their prayer times, so a row whose point is not its town's is worse than
# no row: the times shown are another place's. Put a row back only once GeoNames (or the owner) has
# its true point. Every id here is also a row of WithdrawnCities (shared/.../city/WithdrawnCities.kt)
# with the point it had, so a phone that picked the row before is asked for its place again;
# WithdrawnCitiesTest holds the two lists together.
DROPPED = {
    # "Batang, Central Java" (Kab. Batang's town) stands in GeoNames at -6.4846, 110.7083, which is in Kab.
    # Jepara, 12.7 km from Jepara's own point and inside its Kemenag circle: the app showed Jepara's table
    # under Batang's name (the audit of 12 Nov 2026, finding F4; ruling R212; RegistryCitiesTest holds every
    # Indonesian city on a Kemenag table to that table's own kab/kota).
    "1649881": "Batang, Central Java, Indonesia: GeoNames' point is in Kab. Jepara",
}

# Names GeoNames has wrong in a language, or lacks there, by language and geonameId. Its alternate names
# are kept by many hands, and a city can carry an old Ottoman form, a misspelling, the English name left
# untranslated, its country's own name, or a Wikipedia title with the state or country after a comma, none
# of them marked historic and none beaten by a preferred name, so the pick below keeps them; and a city
# with no name in a language (Singapore in Turkish, Indonesian, Urdu and Bengali) shows the English one.
# The app shows these names in its city search and on Prayer, and the website titles its city pages with
# them, city then country (Algiers' Arabic page read the country's name twice), and names the other cities
# on each language's index by them: a name override in site/cities.tsv is a finding to fix here. Each name
# below was checked on 25 Nov 2026 against the city's Wikidata label and Wikipedia title in that language
# and is one of them (a title less the state or country it adds), unless a comment above the name says
# what else it rests on. It wins over GeoNames' choice, so a rebuild writes the same names;
# CityNamesBundleTest pins them. A fix is written as it stands, so it must have no ASCII comma: the script
# checks that before it downloads anything.
NAME_FIXES = {
    "ar": {
        # Gaziantep: GeoNames had its old name alone, as do Wikidata's label عين تاب and arwiki's title
        # عنتاب; arwiki redirects غازي عنتاب to the city, and the province is ولاية غازي عين تاب.
        "314830": "غازي عنتاب",
        "1735106": "جورج تاون",  # George Town, Penang: misspelt
        # Tunis: GeoNames had the country's name, as Wikidata's label does; Wikidata gives تونس العاصمة as an
        # alias and arwiki redirects it to the city; for Algiers below the same form is the label.
        "2464470": "تونس العاصمة",
        "2507480": "الجزائر العاصمة",  # Algiers: GeoNames had the country's name
    },
    "bn": {
        "1880252": "সিঙ্গাপুর",  # Singapore: GeoNames had no Bengali name
        "3143244": "অসলো",  # Oslo: misspelt
        "6167865": "টরন্টো",  # Toronto: misspelt
    },
    "fr": {
        "1736376": "Kota Bharu",  # GeoNames had the variant Kota Bahru
    },
    "id": {
        "1735106": "George Town",  # George Town, Penang: GeoNames had the variant Georgetown
        "1880252": "Singapura",  # Singapore: GeoNames had no Indonesian name
        # Brussels: GeoNames had Brusel; idwiki's title (Wikidata's label is the Capital Region's).
        "2800866": "Brussel",
    },
    "tr": {
        "1880252": "Singapur",  # Singapore: GeoNames had no Turkish name
        "2553604": "Kazablanka",  # Casablanca: GeoNames had the Ottoman form
        "2561668": "Agadir",  # GeoNames had ğ for its g
        "2803138": "Anvers",  # Antwerp: GeoNames had the English name
    },
    "ur": {
        "101628": "تبوک",  # Tabuk: GeoNames had ", Saudi Arabia" after it
        # Djibouti the city: GeoNames had "region", and Wikidata's label is the country's name; urwiki
        # titles it جبوتی (شہر) and redirects جبوتی شہر there, "city" as in تونس شہر and الجزائر شہر.
        "223817": "جبوتی شہر",
        "286621": "صلالہ",  # Salalah: GeoNames had سلالہ, with س, which urwiki gives to a village in Iran
        "1735106": "جارج ٹاؤن",  # George Town: GeoNames had ", Penang" after it
        "1880252": "سنگاپور",  # Singapore: GeoNames had no Urdu name
        "2210247": "طرابلس",  # Tripoli: GeoNames had ", Libya" after it
        "2219905": "خمس",  # Al Khums: GeoNames had ", Libya" after it
        "2530335": "طنجہ",  # Tangier: GeoNames had the English name in Urdu letters
        # Antwerp: GeoNames had the Arabic spelling, with ت and ب for ٹ and پ; urwiki's title (Wikidata's label
        # splits it, اینٹ ورپن).
        "2803138": "اینٹورپن",
        # Atlanta: GeoNames had ", Georgia" after it, as Wikidata's label does; urwiki's title is bare.
        "4180439": "اٹلانٹا",
        # Philadelphia: GeoNames had ", Pennsylvania" after it; urwiki's title without the state (Wikidata's
        # label spells it فلاڈلفیا).
        "4560349": "فلاڈیلفیا",
        "4990510": "ڈئربورن",  # Dearborn: GeoNames had ", Michigan" after it
    },
}


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


# A name fix goes into its file as it stands, not through clean(), so it must already be what clean()
# would write: csv.writer would put a name with a comma in quotes, and the app, which takes everything
# after the id's comma as the name, would show them.
for lang, fixes in NAME_FIXES.items():
    for gid, name in fixes.items():
        if clean(name) != name:
            sys.exit(f"name fix {lang} {gid} ({name!r}) has a comma or surrounding space")

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
    if geoname_id in DROPPED:
        continue
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

print(f"wrote {len(rows)} cities to {OUT} ({len(DROPPED)} dropped by rule: {', '.join(sorted(DROPPED))})", file=sys.stderr)

# --- alternate names: one file per language, for bundled cities only ---
bundled = {r[0] for r in rows}
wanted = set(LANGS)
# Harakat and tatweel. Arabic place names are conventionally written bare, and
# GeoNames carries both spellings for some cities with neither flagged
# preferred: Tripoli had طَرَابُلُس ahead of طرابلس purely by file order. An
# unmarked name therefore beats a marked one whenever the preferred flag does
# not already decide it.
MARKS = re.compile("[\u064B-\u0652\u0670\u0640]")


def is_marked(name: str) -> bool:
    return bool(MARKS.search(name))


# lang -> geonameId -> (name, is_preferred, is_marked). A preferred name wins;
# among equally preferred candidates an unmarked name wins; otherwise the first
# non-historic row seen wins.
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
    marked = is_marked(name)
    seen = picked[f[2]].get(f[1])
    if (
        seen is None
        or (preferred and not seen[1])
        or (preferred == seen[1] and seen[2] and not marked)
    ):
        picked[f[2]][f[1]] = (name, preferred, marked)

# The hand-checked names win over whatever GeoNames offered (NAME_FIXES above).
for lang, fixes in NAME_FIXES.items():
    for gid, name in fixes.items():
        if gid not in bundled:
            print(f"name fix {lang} {gid} ({name}) not applied: the city is not in the bundle", file=sys.stderr)
            continue
        picked[lang][gid] = (name, True, is_marked(name))

for lang in LANGS:
    path = f"{OUT_DIR}/city-names-{lang}.csv"
    names = picked[lang]
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = writer(fh)
        w.writerow(["id", "name"])
        for gid in sorted(names, key=int):
            w.writerow([gid, names[gid][0]])
    print(f"wrote {len(names)} {lang} names to {path}", file=sys.stderr)
