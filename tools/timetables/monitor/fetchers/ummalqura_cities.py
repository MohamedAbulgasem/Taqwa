"""Saudi Arabia, Umm al-Qura (KACST): the same API as `ummalqura` (GetPrayerByYear, the one the official page
ummulqura.org.sa calls), at every other place of KACST's own city list, the current year and the next, monthly.

The official page lists 173 places (https://www.ummulqura.org.sa/assets/data/cities.json: each region's capital,
its governorates and five centres) and passes each one's own coordinates to the API, so each place's table is the
API's answer at those coordinates. The registry's units for sa.ummalqura are those 173 points (ruling R44:
UmmAlQura.kt, data/UmmAlQuraCities.kt); `ummalqura` fetches twelve of them weekly, this source the other 161,
monthly (a year's table seldom changes; the weekly twelve watch the method). The API prints no end of eating: the
fast begins at its Fajr (F+E)."""
from common import Table, add_all, FetchError
from fetchers.ummalqura import BASE, HEADERS, parse

SOURCE = "sa-ummalqura-cities"
ENTRY = "sa.ummalqura"

# key, name, lat, lon: KACST's own places (cities.json, fetched 9 Oct 2026), by region, as the page passes them to
# the API; the twelve `ummalqura` fetches are not repeated here.
POINTS = [
    # Riyadh region (KACST region 1001).
    ("ad-diriyah", "Ad Diriyah", 24.74, 46.58),
    ("afif", "Afif", 23.91, 42.92),
    ("al-aflaj", "Al Aflaj", 22.22, 46.656),
    ("al-ghat", "Al Ghat", 26.03, 44.95),
    ("al-quwaiiyah", "Al Quwaiiyah", 24.05, 45.25),
    ("al-hariq", "Al Hariq", 23.62, 46.52),
    ("al-kharj", "Al Kharj", 24.15, 47.32),
    ("as-sulayyil", "As Sulayyil", 20.47, 45.57),
    ("az-zulfi", "Az Zulfi", 26.3, 44.82),
    ("dhurma", "Dhurma", 24.6, 46.13),
    ("al-duwadimi", "Al Duwadimi", 24.5, 44.38),
    ("howtat-bani-tamim", "Howtat Bani Tamim", 23.49, 46.86),
    ("huraymila", "Huraymila", 25.11, 46.1),
    ("al-majmaah", "Al Majmaah", 25.9, 45.34),
    ("al-muzahimiyah", "Al Muzahimiyah", 24.48, 46.29),
    ("rumah", "Rumah", 25.56, 47.17),
    ("shagra", "Shagra", 25.26, 45.26),
    ("thadig", "Thadig", 25.28, 45.86),
    ("wadi-ad-dawasir", "Wadi ad-Dawasir", 20.4722, 44.783),
    ("al-hayr", "Al Hayr", 24.404568, 46.839516),
    ("ad-dilam", "Ad Dilam", 23.99649, 47.138344),
    ("marat", "Marat", 25.0699, 45.4639),
    ("tumair", "Tumair", 25.7134, 45.8707),
    ("jalajil", "Jalajil", 25.6834, 45.4592),
    ("hawtat-sudayr", "Hawtat Sudayr", 25.5947, 45.6184),
    ("rawdat-sudayr", "Rawdat Sudayr", 25.6187, 45.5543),
    ("layla", "Layla", 22.2899, 46.7188),
    ("al-uyaynah", "Al Uyaynah", 24.9018, 46.3865),
    ("malham", "Malham", 25.1625, 46.3337),
    ("sudus", "Sudus", 24.993744, 46.207878),
    ("ar-rayn", "Ar Rayn", 23.542, 45.5159),
    # Makkah region (KACST region 1002).
    ("al-jumum", "Al Jumum", 21.63, 39.7),
    ("al-kamil", "Al Kamil", 22.26, 39.79),
    ("al-khurma", "Al Khurma", 21.91, 42.02),
    ("al-lith", "Al Lith", 20.15, 40.28),
    ("al-qunfudhah", "Al Qunfudhah", 19.13, 41.08),
    ("khulais", "Khulais", 22.11, 39.31),
    ("rabigh", "Rabigh", 22.79, 39.03),
    ("ranyah", "Ranyah", 21.28, 42.82),
    ("turbah", "Turbah", 21.23, 41.65),
    ("adham", "Adham", 20.423, 40.8624),
    ("maysan", "Maysan", 20.74756002, 40.85031999),
    ("al-ardhiyat", "Al Ardhiyat", 19.41604388, 41.74531637),
    ("thuwal", "Thuwal", 22.2758, 39.1132),
    ("dhahban", "Dhahban", 21.9309, 39.116),
    ("al-muwayh", "Al Muwayh", 22.4341, 41.7615),
    ("bahrah", "Bahrah", 21.4023, 39.4631),
    # Madinah region (KACST region 1003).
    ("al-henakiyah", "Al Henakiyah", 24.88, 40.52),
    ("mahd-al-thahab", "Mahd Al Thahab", 23.52, 40.89),
    ("al-ula", "Al Ula", 26.55, 37.96),
    ("badr", "Badr", 23.78, 38.8),
    ("khaybar", "Khaybar", 25.71, 39.28),
    ("yanbu", "Yanbu", 24.09, 38.07),
    ("al-is", "Al 'Is", 25.0598, 38.1167),
    ("wadi-al-fara", "Wadi Al Fara", 23.3127, 39.7776),
    # Qassim region (KACST region 1004).
    ("alasyah", "Alasyah", 26.77, 44.22),
    ("al-badayea", "Al Badayea", 26.0, 43.73),
    ("al-bukayriyah", "Al Bukayriyah", 26.15, 43.67),
    ("al-mithnab", "Al Mithnab", 25.87, 44.21),
    ("ar-rass", "Ar Rass", 25.86, 43.48),
    ("an-nabhaniyah", "An Nabhaniyah", 25.94, 42.95),
    ("unayzah", "Unayzah", 26.09, 43.97),
    ("uyun-al-jawa", "Uyun Al Jawa", 26.51, 43.62),
    ("riyadh-al-khabra", "Riyadh Al Khabra", 26.04, 43.55),
    ("ash-shimasiyah", "Ash Shimasiyah", 26.3189, 44.2574),
    ("uqlat-as-suqur", "Uqlat As Suqur", 25.8311, 42.1879),
    ("dariyah", "Dariyah", 24.7225, 42.9327),
    ("al-busur", "Al Busur", 26.2888, 43.8612),
    ("al-quwarah", "Al Quwarah", 26.7716, 43.4742),
    ("abanat", "Abanat", 25.5057, 42.8464),
    ("buraydah", "Buraydah", 26.35, 43.96),
    # Eastern Province region (KACST region 1005).
    ("abqaiq", "Abqaiq", 25.93, 49.66),
    ("qaryat-al-ulya", "Qaryat Al Ulya", 27.56, 47.7),
    ("al-hofuf", "Al Hofuf", 25.408, 49.6132),
    ("nairyah", "Nairyah", 27.47, 48.47),
    ("hafar-al-batin", "Hafar Al Batin", 28.4, 45.97),
    ("al-jubail", "Al Jubail", 27.0, 49.66),
    ("khafji", "Khafji", 28.42, 48.49),
    ("al-khobar", "Al Khobar", 26.26, 50.21),
    ("al-qatif", "Al Qatif", 26.56, 50.0),
    ("ras-tanura", "Ras Tanura", 26.71, 50.07),
    ("dhahran", "Dhahran", 26.239291, 50.040259),
    ("tarut", "Tarut", 26.57110001, 50.07269982),
    ("al-mubarraz", "Al Mubarraz", 25.405807, 49.548469),
    ("al-ahsa", "Al Ahsa", 24.1389, 49.4277),
    ("al-udayd", "Al Udayd", 24.147, 50.919),
    ("al-bayda", "Al Bayda", 26.361417, 49.969591),
    ("ras-tannurah", "Ras Tannurah", 26.7065, 50.0528),
    # Asir region (KACST region 1006).
    ("ahad-rafidah", "Ahad Rafidah", 18.17, 42.84),
    ("almajaridah", "Almajaridah", 19.13, 41.93),
    ("al-namas", "Al Namas", 19.14, 42.14),
    ("belqarn", "Belqarn", 19.57, 41.96),
    ("bishah", "Bishah", 20.0, 42.6),
    ("dhahran-al-janub", "Dhahran Al Janub", 17.69, 43.51),
    ("khamis-mushait", "Khamis Mushait", 18.31, 42.73),
    ("muhayil", "Muhayil", 18.54, 42.05),
    ("rojal", "Rojal", 18.23, 42.28),
    ("sarat-abidah", "Sarat Abidah", 18.08, 43.14),
    ("tathleeth", "Tathleeth", 19.55, 43.52),
    ("bariq", "Bariq", 18.9305, 41.9296),
    ("tanumah", "Tanumah", 18.929, 42.177),
    ("tarib", "Tarib", 18.5667607, 43.20690401),
    ("al-harajah", "Al Harajah", 17.9204, 43.3729),
    ("al-amwah", "Al Amwah", 18.7141, 43.6591),
    ("al-birk", "Al Birk", 18.2071, 41.5363),
    ("al-farshah", "Al Farshah", 17.7532, 43.1529),
    # Tabuk region (KACST region 1007).
    ("al-wajh", "Al Wajh", 26.23, 36.47),
    ("umluj", "Umluj", 25.03, 37.26),
    ("duba", "Duba", 27.35, 35.69),
    ("tayma", "Tayma", 28.63, 38.55),
    ("al-bada", "Al Bada", 28.473, 35.0278),
    # Hail region (KACST region 1008).
    ("al-ghazalah", "Al Ghazalah", 26.79, 41.32),
    ("ash-shinan", "Ash Shinan", 27.17, 42.44),
    ("baqaa", "Baqaa", 27.9, 42.4),
    ("al-hait", "Al Hait", 25.9933, 40.4669),
    ("as-sulaymi", "As Sulaymi", 26.2896, 41.3661),
    ("mawqaq", "Mawqaq", 27.378545, 41.179762),
    ("simira", "Simira'", 26.49941, 42.12939001),
    ("ash-shamli", "Ash Shamli", 26.858595, 40.328537),
    ("hail", "Hail", 27.52, 41.7),
    # Northern Borders region (KACST region 1009).
    ("rafha", "Rafha", 29.63, 43.5),
    ("al-uwayqiliyah", "Al 'Uwayqiliyah", 30.361771, 42.250924),
    ("arar", "Arar", 30.99, 41.02),
    # Jazan region (KACST region 1010).
    ("abu-arish", "Abu Arish", 16.97, 42.83),
    ("ahad-al-masarihah", "Ahad Al Masarihah", 16.7, 42.95),
    ("al-edabi", "Al Edabi", 17.24, 42.95),
    ("al-aridhah", "Al Aridhah", 17.04, 43.09),
    ("addayer", "Addayer", 17.34, 43.14),
    ("alharth", "Alharth", 16.78, 43.22),
    ("aldarb", "Aldarb", 17.71, 42.09),
    ("al-reeth", "Al Reeth", 17.6, 42.86),
    ("baish", "Baish", 17.378, 42.538),
    ("damad", "Damad", 17.1, 42.78),
    ("farasan-island", "Farasan Island", 16.71, 42.12),
    ("sabya", "Sabya", 17.15, 42.62),
    ("samtah", "Samtah", 16.59, 42.94),
    ("fayfa", "Fayfa", 17.2473, 43.1069),
    ("harub", "Harub", 17.435, 42.8857),
    ("jazan", "Jazan", 16.89, 42.54),
    # Najran region (KACST region 1011).
    ("badr-al-janoub", "Badr Al Janoub", 17.88, 43.72),
    ("hubuna", "Hubuna", 17.85, 43.02),
    ("khbash", "Khbash", 17.6, 45.066),
    ("sharorah", "Sharorah", 17.49, 47.11),
    ("thar", "Thar", 17.97, 44.11),
    ("yadamah", "Yadamah", 18.53, 44.21),
    ("najran", "Najran", 17.52, 44.2),
    # Al Bahah region (KACST region 1012).
    ("al-aqiq", "Al Aqiq", 20.27, 41.66),
    ("al-makhwah", "Al Makhwah", 19.78, 41.43),
    ("almandaq", "Almandaq", 20.16, 41.29),
    ("alqura", "Alqura", 20.25, 41.36),
    ("baljurashi", "Baljurashi", 19.86, 41.57),
    ("qilwah", "Qilwah", 19.95, 41.24),
    ("al-hajrah", "Al Hajrah", 20.1954, 41.0722),
    ("ghamid-az-zinad", "Ghamid Az Zinad", 19.6033, 41.4813),
    ("bani-hasan", "Bani Hasan", 20.051578, 41.37405),
    ("al-bahah", "Al Bahah", 20.01, 41.47),
    # Al Jawf region (KACST region 1013).
    ("al-qurayyat", "Al Qurayyat", 31.33, 37.34),
    ("dumah-al-jandal", "Dumah Al Jandal", 29.81, 39.87),
    ("tubarjal", "Tubarjal", 30.50098005, 38.22183997),
    ("suwayr", "Suwayr", 30.1147, 40.3803),
    ("sakaka", "Sakaka", 29.97, 40.2),
]


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
            t = Table(f"{key}-{year}", f"{name} {year}", lat, lon, "Asia/Riyadh", "SA", "F+E S D A M I", entry=ENTRY,
                      source_line=f"Umm al-Qura (KACST) {url} (the JSON API the official page ummulqura.org.sa calls)",
                      raw=[(f"{key}-{year}.json", body)])
            add_all(ctx, t, rows, f"{name} {year}")
            t.merge = False
            if t.rows:
                tables.append(t)
    return tables
