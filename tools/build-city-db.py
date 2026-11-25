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
import csv, io, os, re, sys, tempfile, unicodedata, urllib.request, zipfile

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
        "89055": "البيضاء",  # Al Bayḑā’: GeoNames had قرية البيضاء
        "89570": "زاخو",  # Zaxo: GeoNames had Zākhū
        "90026": "طوز خورماتو",  # Ţūz Khūrmātū: GeoNames had Ţūzkhūrmātū
        "95788": "هيت",  # Hīt: GeoNames had Hīt
        "101554": "تاروت",  # Tārūt: GeoNames had Tārūt
        "107304": "بريدة",  # Buraydah: GeoNames had بُرَيدَة
        "109223": "المدينة المنورة",  # Madinah: GeoNames had المدينة
        # Afif: GeoNames had ‘Afīf. Wikidata's item with its GeoNames id is the governorate's (محافظة عفيف);
        # its alias عفيف is the city's name, as the same item's Urdu label writes it.
        "110250": "عفيف",
        "163915": "صوران",  # Şūrān: GeoNames had Şūrān
        "165060": "صافيتا",  # Şāfītā: GeoNames had Şāfītā
        "172082": "أعزاز",  # I‘zāz: GeoNames had A‘zāz
        "248299": "ماحص",  # Māḩiş: GeoNames had Māḩiş
        "250090": "الزرقاء",  # Zarqa: GeoNames had زرقاء
        "267008": "صور",  # Tyre: GeoNames had Şūr
        "281715": "صوريف",  # Surif City: GeoNames had Şūrīf
        # Sur, Oman: GeoNames had Şūr. Wikidata's Arabic label and title are the wilayat's, ولاية صور, with no
        # alias: this is that label less ولاية, and the item's Urdu label. Tyre is صور too, told apart by
        # country.
        "286245": "صور",
        # Sohar: GeoNames had Aş Şuḩār. Wikidata's Arabic label and title are the wilayat's, ولاية صحار; its
        # alias صحار, the city's name and the item's Urdu label, is stored with invisible direction marks, left
        # out here.
        "286282": "صحار",
        "287830": "عبري",  # ‘Ibrī: GeoNames had ‘Ibrī
        "292672": "الشارقة",  # Sharjah: GeoNames had إمارة الشارقة
        "293253": "يافا",  # Jaffa: GeoNames had Yāffā
        # Gaziantep: GeoNames had its old name alone, as do Wikidata's label عين تاب and arwiki's title
        # عنتاب; arwiki redirects غازي عنتاب to the city, and the province is ولاية غازي عين تاب.
        "314830": "غازي عنتاب",
        "347236": "طوخ",  # Ţūkh: GeoNames had Ţūkh
        "357039": "فايد",  # Fāyīd: GeoNames had Fā’id
        "359678": "الطور",  # El-Tor: GeoNames had Aţ Ţūr
        "359796": "السويس",  # Suez: GeoNames had سويس
        "360761": "المنصورة",  # Al Mansurah: GeoNames had منصورة
        "932035": "تياتيانينغ",  # Teyateyaneng: GeoNames had تيايتيانeng
        "1622786": "ماكاسار",  # Makassar: GeoNames had no Arabic name
        "1655078": "موانج إكساي",  # Muang Xay: GeoNames had مواng ساي
        "1655123": "فونسافان",  # Muang Phônsavan: GeoNames had مواng فونسافان
        "1735106": "جورج تاون",  # George Town, Penang: misspelt
        "2210554": "سرت",  # Sirte: GeoNames had شعبية سرت
        "2219905": "الخمس",  # Al Khums: GeoNames had المرقب
        # Tunis: GeoNames had the country's name, as Wikidata's label does; Wikidata gives تونس العاصمة as an
        # alias and arwiki redirects it to the city; for Algiers below the same form is the label.
        "2464470": "تونس العاصمة",
        "2507480": "الجزائر العاصمة",  # Algiers: GeoNames had the country's name
        "2538475": "الرباط",  # Rabat: GeoNames had رباط
        "3353383": "روندو",  # Rundu: GeoNames had رونdu
        "6167865": "تورنتو",  # Toronto: GeoNames had تورونتو
    },
    "bn": {
        "250090": "জারকা",  # Zarqa: GeoNames had no Bengali name
        "306571": "কোনিয়া",  # Konya: GeoNames had no Bengali name
        "347497": "তান্তা",  # Tanta: GeoNames had no Bengali name
        "358619": "সাইদ বন্দর",  # Port Said: GeoNames had no Bengali name
        "359783": "আসিউত",  # Assiut: GeoNames had no Bengali name
        "359792": "আসওয়ান",  # Aswān: GeoNames had no Bengali name
        "359796": "সুয়েজ",  # Suez: GeoNames had no Bengali name
        "360502": "লাক্সর",  # Luxor: GeoNames had no Bengali name
        "360761": "মনসুরা",  # Al Mansurah: GeoNames had no Bengali name
        "360995": "গিজা",  # Giza: GeoNames had no Bengali name
        "1007311": "ডারবান",  # Durban: GeoNames had no Bengali name
        "1185092": "ঠাকুরগাঁও",  # Thākurgaon: GeoNames had Thākurgaon
        "1185095": "টেকনাফ",  # Teknāf: GeoNames had Teknāf
        "1185141": "পঞ্চগড়",  # Panchagarh: GeoNames had Panchagarh
        "1185148": "পটিয়া",  # Patiya: GeoNames had Patiya
        "1185149": "পার্বতীপুর",  # Parbatipur: GeoNames had Parbatipur
        "1185160": "নাগেশ্বরী",  # Nageswari: GeoNames had Nageswari
        "1185167": "মোড়লগঞ্জ",  # Morrelgonj: GeoNames had Morrelgonj
        "1185173": "মঠবাড়িয়া",  # Mathba: GeoNames had Mathba
        "1185181": "লালমনিরহাট",  # Lalmonirhat: GeoNames had Lalmanirhat
        "1185191": "কুষ্টিয়া",  # Kushtia: GeoNames had Kushtia
        "1185199": "কালিগঞ্জ",  # Kālīganj: GeoNames had Kālīganj
        "1185204": "ঝিকরগাছা",  # Jhingergācha: GeoNames had Jhikargāchha
        "1185206": "জয়পুরহাট",  # Joypur Hāt: GeoNames had Joypur Hāt
        "1185239": "শরণখোলা",  # Sarankhola: GeoNames had Sarankhola
        "1185247": "চিলমারী",  # Chilmāri: GeoNames had Chilmāri
        "1185251": "লালমোহন",  # Lālmohan: GeoNames had Lālmohan
        "1185260": "অভয়নগর",  # Bhātpāra Abhaynagar: GeoNames had Bhātpāra Abhaynagar
        "1185262": "ভেড়ামারা",  # Bherāmāra: GeoNames had Bherāmāra
        "1185270": "বান্দরবান",  # Bāndarban: GeoNames had Bāndarban
        "1185272": "কালিয়া",  # Kālia: GeoNames had Kālia
        "1189638": "রাউজান",  # Raojān: GeoNames had Raojān
        "1203878": "দোহাজারী",  # Dohazāri: GeoNames had Dohazāri
        "1210565": "বোরহানউদ্দিন",  # Burhānuddin: GeoNames had Burhānuddin
        "1214520": "মেদান",  # Medan: GeoNames had no Bengali name
        "1215502": "বান্দা আচেহ",  # Banda Aceh: GeoNames had no Bengali name
        "1275004": "কলকাতা",  # Kolkata: GeoNames had Kolkata
        "1336133": "সাতকানিয়া",  # Sātkania: GeoNames had Sātkania
        "1336134": "কক্সবাজার",  # Cox’s Bāzār: GeoNames had Cox’s Bāzār
        "1336135": "খুলনা",  # Khulna: GeoNames had Khulna
        "1336139": "রাঙ্গামাটি",  # Rangamati: GeoNames had Rāngāmāti
        "1336140": "যশোর",  # Jessore: GeoNames had Jessore
        "1337240": "চাঁপাইনবাবগঞ্জ",  # Nawābganj: GeoNames had Nawābganj
        "1462681": "মানিকছড়ি",  # Manikchari: GeoNames had Mānikchhari
        "1477498": "ভান্ডারিয়া",  # Bhāndāria: GeoNames had Bhāndāria
        "1621177": "যোগ্যকার্তা",  # Yogyakarta: GeoNames had no Bengali name
        "1622786": "মাকাসার",  # Makassar: GeoNames had no Bengali name
        "1625822": "সুরাবায়া",  # Surabaya: GeoNames had no Bengali name
        "1627896": "সেমারাং",  # Semarang: GeoNames had no Bengali name
        "1633070": "পালেমবাং",  # Palembang: GeoNames had no Bengali name
        "1650357": "বানদুং",  # Bandung: GeoNames had no Bengali name
        "1880252": "সিঙ্গাপুর",  # Singapore: GeoNames had no Bengali name
        "2464470": "তিউনিস",  # Tunis: GeoNames had no Bengali name
        "2803138": "আন্টভের্প",  # Antwerp: GeoNames had no Bengali name
        "3143244": "অসলো",  # Oslo: misspelt
        "6075357": "মিসিসগা",  # Mississauga: GeoNames had no Bengali name
        "6167865": "টরন্টো",  # Toronto: misspelt
        "7483813": "নাটোর",  # Natore: GeoNames had Nātor
    },
    "fr": {
        "359783": "Assiout",  # Assiut: GeoNames had no French name
        "1736376": "Kota Bharu",  # GeoNames had the variant Kota Bahru
    },
    "id": {
        "359783": "Asyut",  # Assiut: GeoNames had no Indonesian name
        "1214520": "Medan",  # Medan: GeoNames had Kota Medan
        "1215502": "Banda Aceh",  # Banda Aceh: GeoNames had Kota Banda Aceh
        "1621177": "Yogyakarta",  # Yogyakarta: GeoNames had Daerah Istimewa Yogyakarta
        "1622786": "Makassar",  # Makassar: GeoNames had Kota Makassar
        "1625822": "Surabaya",  # Surabaya: GeoNames had Kota Surabaya
        "1627896": "Semarang",  # Semarang: GeoNames had Kota Semarang
        "1633070": "Palembang",  # Palembang: GeoNames had Kota Palembang
        "1650357": "Bandung",  # Bandung: GeoNames had Kota Bandung
        "1735106": "George Town",  # George Town, Penang: GeoNames had the variant Georgetown
        "1880252": "Singapura",  # Singapore: GeoNames had no Indonesian name
        # Brussels: GeoNames had Brusel. Brussel is idwiki's title on the Capital Region's item (Q240); the
        # city's own item (Q239) is labelled Kota Brussel.
        "2800866": "Brussel",
    },
    "tr": {
        "107304": "Büreyde",  # Buraydah: GeoNames had Burayda
        "250090": "Zerka",  # Zarqa: GeoNames had no Turkish name
        "359783": "Asyut",  # Assiut: GeoNames had no Turkish name
        "360995": "Gize",  # Giza: GeoNames had no Turkish name
        "1642911": "Cakarta",  # Jakarta: GeoNames had Jakarta
        "1880252": "Singapur",  # Singapore: GeoNames had no Turkish name
        "2467454": "Sefakus",  # Sfax: GeoNames had Safakes
        "2553604": "Kazablanka",  # Casablanca: GeoNames had the Ottoman form
        "2561668": "Agadir",  # GeoNames had ğ for its g
        "2803138": "Anvers",  # Antwerp: GeoNames had the English name
    },
    "ur": {
        "101628": "تبوک",  # Tabuk: GeoNames had ", Saudi Arabia" after it
        # Djibouti the city: GeoNames had "region", and Wikidata's label is the country's name; urwiki
        # titles it جبوتی (شہر) and redirects جبوتی شہر there, "city" as in تونس شہر and الجزائر شہر.
        "223817": "جبوتی شہر",
        "250090": "زرقاء",  # Zarqa: GeoNames had no Urdu name
        "286245": "صور",  # Sur: GeoNames had صور، عمان
        "286621": "صلالہ",  # Salalah: GeoNames had سلالہ, with س, which urwiki gives to a village in Iran
        "347497": "طنطا",  # Tanta: GeoNames had no Urdu name
        "358619": "پورٹ سعید",  # Port Said: GeoNames had no Urdu name
        "359783": "اسیوط",  # Assiut: GeoNames had no Urdu name
        "360761": "منصورہ",  # Al Mansurah: GeoNames had no Urdu name
        "360995": "جیزہ",  # Giza: GeoNames had no Urdu name
        "932035": "تئیاتئیانگ",  # Teyateyaneng: GeoNames had تیاتیانeng
        "1214520": "مدان",  # Medan: GeoNames had no Urdu name
        "1215502": "باندا آچے",  # Banda Aceh: GeoNames had no Urdu name
        "1273294": "دہلی",  # Delhi: GeoNames had پرانی دہلی
        "1621177": "یوگیاکارتا",  # Yogyakarta: GeoNames had no Urdu name
        "1622786": "ماکاسار",  # Makassar: GeoNames had no Urdu name
        "1625822": "سورابایا",  # Surabaya: GeoNames had no Urdu name
        "1627896": "سیمارانگ",  # Semarang: GeoNames had no Urdu name
        "1735106": "جارج ٹاؤن",  # George Town: GeoNames had ", Penang" after it
        "1880252": "سنگاپور",  # Singapore: GeoNames had no Urdu name
        "2210247": "طرابلس",  # Tripoli: GeoNames had ", Libya" after it
        "2219905": "خمس",  # Al Khums: GeoNames had ", Libya" after it
        "2530335": "طنجہ",  # Tangier: GeoNames had the English name in Urdu letters
        # Brussels: GeoNames had no Urdu name. urwiki's title and the label on the Capital Region's item (Q240),
        # as Indonesian's Brussel; the city's own item (Q239) writes برسلز شہر, "the City of Brussels".
        "2800866": "برسلز",
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
        if unicodedata.normalize("NFC", name) != name:
            sys.exit(f"name fix {lang} {gid} ({name!r}) is not in Unicode's composed form (NFC)")

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
            # In Unicode's composed form (NFC): GeoNames writes some Bengali nukta letters precomposed and
            # some Arabic marks out of their canonical order, and a name in two forms is two strings.
            w.writerow([gid, unicodedata.normalize("NFC", names[gid][0])])
    print(f"wrote {len(names)} {lang} names to {path}", file=sys.stderr)
