"""Morocco, the Ministry of Habous and Islamic Affairs (habous.gov.ma): every place the Ministry prints a
table for that is a unit of ma.habous (PLACES: 187 units since rulings R195-R199 of 13 November 2026, the
ten of before and the 29 once read at the app's city points among them, under the keys they always had),
each read at its own unit, and Agoudal's second table against Agoudal's unit (SECOND_TABLES). Sebta and
Melilla, which the files also carry, are not units and are not read here: their rows ride beside Spain's safe
default (ruling R261) and are read by hand each month (sources.tsv's ma-habous-spain, ruling R288). Habous prints the legal time, UTC+0
all the year round since 20 September 2026 (ClockChanges), so each table is read with `clock` UTC like the
gate's rows. Habous prints no imsak: the fast begins at its Fajr (F+E). Restricted: archive only.

Since late October 2026 the site is a maintenance notice and the old city pages
(prieres/index.php?ville=N) answer 404. Its times now come from static month files,
data/horaires-<yyyy>-<mm>.js: one whole Gregorian month for 190 places, as
`window.HORAIRES_MOIS.push({annee, mois, fuseau, cols, villes: [{c: ville id, n: Arabic name, h: [[six
times] per day]}]})`. The ids are the old pages' ville ids. A run reads the current month and the next;
the next is posted a few days before it begins (November's on 31 October), so a 404 for it is a note
("not posted yet"), never an error, while a 404 for the current month is one. The file must say
`fuseau` "GMT": another zone (Morocco back on summer time, say) is refused until the tables' clock is
reconsidered, never read an hour off. Each place must carry the Arabic name its ville id has carried (its old
page's; for a place first read from the files, October and November 2026's), so an id that now names another
place is left out. Each place's file keeps every month fetched (the store merges);
the old page parser (`parse`) stays for the day the portal returns. A run is still two requests, whatever
the number of places: every unit's days are checked from the same two files."""
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

# Every unit of ma.habous (shared/.../registry/data/HabousCities.kt, rulings R195-R199): the unit's id (the
# table's key and its entry's unit), its name, the site's ville id, and the Arabic name that id carried in
# the month files of October and November 2026, which every later file's `n` must repeat. HabousPlaces
# (tests.py) holds this list to HabousCities.kt, so a unit is never left unchecked.
PLACES = [
    ("rabat", "Rabat", 1, "الرباط"), ("khemisset", "Khemisset", 2, "الخميسات"), ("tiflet", "Tiflet", 3, "تيفلت"),
    ("rommani", "Rommani", 4, "الرماني"), ("oulmes", "Oulmes", 5, "والماس"), ("bouznika", "Bouznika", 6, "بوزنيقة"),
    ("kenitra", "Kenitra", 7, "القنيطرة"), ("sidi-kacem", "Sidi Kacem", 8, "سيدي قاسم"),
    ("sidi-yahia-el-gharb", "Sidi Yahia El Gharb", 9, "سيدي يحيى الغرب"),
    ("sidi-slimane", "Sidi Slimane", 10, "سيدي سليمان"), ("souk-el-arbaa", "Souk El Arbaa", 11, "سوق أربعاء الغرب"),
    ("arbaoua", "Arbaoua", 12, "عرباوة"), ("moulay-bousselham", "Moulay Bousselham", 13, "مولاي بوسلهام"),
    ("tangier", "Tangier", 14, "طنجة"), ("tetouan", "Tétouan", 15, "تطوان"), ("larache", "Larache", 16, "العرائش"),
    ("asilah", "Asilah", 17, "أصيلة"), ("chefchaouen", "Chefchaouen", 18, "شفشاون"),
    ("martil", "Martil", 19, "مرتيل"), ("mdiq", "Mdiq", 20, "المضيق"),
    ("ksar-el-kebir", "Ksar El Kebir", 21, "القصر الكبير"), ("ksar-sghir", "Ksar Sghir", 22, "القصر الصغير"),
    ("al-hoceima", "Al Hoceïma", 23, "الحسيمة"), ("fnidek", "Fnidek", 25, "الفنيدق"),
    ("el-jebha", "El Jebha", 26, "الجبهة"), ("oued-laou", "Oued Laou", 27, "واد لاو"),
    ("bab-berred", "Bab Berred", 28, "باب برد"), ("ouezzane", "Ouezzane", 29, "وزان"),
    ("bouskour", "Bouskour", 30, "بوسكور"), ("oujda", "Oujda", 31, "وجدة"), ("berkane", "Berkane", 32, "بركان"),
    ("figuig", "Figuig", 33, "فكيك"), ("bouarfa", "Bouarfa", 34, "بوعرفة"), ("guercif", "Guercif", 35, "كرسيف"),
    ("jerada", "Jerada", 36, "جرادة"), ("ain-chair", "Ain Chair", 37, "عين الشعير"),
    ("taourirt", "Taourirt", 38, "تاوريرت"), ("nador", "Nador", 39, "الناظور"), ("debdou", "Debdou", 41, "دبدو"),
    ("selouane", "Selouane", 42, "سلوان"), ("beni-enzar", "Beni Enzar", 43, "بني أنصار"),
    ("farkhana", "Farkhana", 44, "فرخانة"), ("talsint", "Talsint", 45, "تالسينت"),
    ("tendrara", "Tendrara", 46, "تندرارة"), ("el-aioun", "El Aïoun", 47, "العيون الشرقية"),
    ("beni-drar", "Beni Drar", 48, "بني ادرار"), ("saidia", "Saidia", 49, "السعيدية"),
    ("ras-el-ma", "Ras El Ma (Nador)", 50, "رأس الماء"), ("tafoughalt", "Tafoughalt", 51, "تافوغالت"),
    ("fezouane", "Fezouane", 52, "فزوان"), ("ahfir", "Ahfir", 53, "أحفير"), ("zaio", "Zaïo", 54, "زايو"),
    ("driouch", "Driouch", 55, "دريوش"), ("beni-tadjit", "Beni Tadjit", 56, "بني تجيت"),
    ("bouanane", "Bouanane", 57, "بوعنان"), ("casablanca", "Casablanca", 58, "الدار البيضاء"),
    ("mohammedia", "Mohammedia", 59, "المحمدية"), ("benslimane", "Benslimane", 60, "بن سليمان"),
    ("settat", "Settat", 61, "سطات"), ("el-gara", "El Gara", 62, "الكارة"), ("al-brouj", "Al Brouj", 63, "البروج"),
    ("ben-ahmed", "Ben Ahmed", 64, "ابن أحمد"), ("berrechid", "Berrechid", 65, "برشيد"),
    ("el-jadida", "El Jadida", 66, "الجديدة"), ("azemmour", "Azemmour", 67, "أزمور"),
    ("sidi-bennour", "Sidi Bennour", 68, "سيدي بنور"), ("khemis-zemamra", "Khemis Zemamra", 69, "خميس الزمامرة"),
    ("khenifra", "Khenifra", 70, "خنيفرة"), ("moulay-bouazza", "Moulay Bouazza", 71, "مولاي بوعزة"),
    ("zaouiat-ahansal", "Zaouiat Ahansal", 72, "زاوية أحنصال"), ("beni-mellal", "Beni Mellal", 73, "بني ملال"),
    ("azilal", "Azilal", 74, "أزيلال"), ("al-fqih-ben-calah", "Al Fqih Ben Çalah", 75, "الفقيه بنصالح"),
    ("demnate", "Demnate", 76, "دمنات"), ("el-ksiba", "El Ksiba", 77, "القصيبة"),
    ("kasba-tadla", "Kasba Tadla", 78, "قصبة تادلة"), ("khouribga", "Khouribga", 79, "خريبكة"),
    ("oued-zem", "Oued Zem", 80, "وادي زم"), ("fes", "Fes", 81, "فاس"), ("sefrou", "Sefrou", 82, "صفرو"),
    ("moulay-yacoub", "Moulay Yacoub", 83, "مولاي يعقوب"), ("boulemane", "Boulemane", 84, "بولمان"),
    ("missour", "Missour", 85, "ميسور"), ("ribat-al-khayr", "Ribat Al Khayr", 86, "رباط الخير"),
    ("el-menzel", "El Menzel", 87, "المنزل بني يازغة"), ("imouzzer-kandar", "Imouzzer Kandar", 88, "إموزار كندر"),
    ("taza", "Taza", 89, "تازة"), ("taounate", "Taounate", 90, "تاونات"), ("aknoul", "Aknoul", 91, "أكنول"),
    ("tizi-ouasli", "Tizi Ouasli", 92, "تيزي وسلي"), ("boured", "Boured", 93, "بورد"),
    ("tahla", "Tahla", 94, "تاهلة"), ("tissa", "Tissa", 95, "تيسة"),
    ("karia-ba-mohamed", "Karia Ba Mohamed", 96, "قرية با محمد"), ("ketama", "Ketama", 97, "كتامة"),
    ("oued-amlil", "Oued Amlil", 98, "واد أمليل"), ("meknes", "Meknes", 99, "مكناس"),
    ("ifrane", "Ifrane", 100, "يفرن"), ("el-hajeb", "El Hajeb", 101, "الحاجب"), ("zerhoun", "Zerhoun", 102, "زرهون"),
    ("azrou", "Azrou", 103, "آزرو"), ("marrakesh", "Marrakesh", 104, "مراكش"),
    ("el-kelaa-des-srarhna", "El Kelaa des Srarhna", 105, "قلعة السراغنة"),
    ("essaouira", "Essaouira", 106, "الصويرة"), ("chichaoua", "Chichaoua", 107, "شيشاوة"),
    ("ben-guerir", "Ben Guerir", 108, "بنجرير"), ("rhamna", "Rhamna", 109, "الرحامنة"),
    ("tamanar", "Tamanar", 110, "تمنار"), ("safi", "Safi", 111, "آسفي"), ("oualidia", "Oualidia", 112, "الوليدية"),
    ("youssoufia", "Youssoufia", 113, "اليوسفية"), ("tassoultante", "Tassoultante", 114, "تسلطانت"),
    ("tameslouht", "Tameslouht", 115, "تامصلوحت"), ("kettara", "Kettara", 116, "قطارة"),
    ("agadir", "Agadir", 117, "أكادير"), ("taroudant", "Taroudant", 118, "تارودانت"),
    ("tiznit", "Tiznit", 119, "تزنيت"), ("igherm", "Igherm", 120, "إغرم"), ("taliouine", "Taliouine", 121, "تالوين"),
    ("tafraout", "Tafraout", 122, "تافراوت"), ("tata", "Tata", 123, "طاطا"), ("akka", "Akka", 124, "أقا"),
    ("foum-el-hisn", "Foum El Hisn", 125, "فم لحصن"), ("tine-biougra", "Tine Biougra", 126, "بويكرة"),
    ("oulad-teima", "Oulad Teïma", 127, "أولاد تايمة"), ("errachidia", "Errachidia", 128, "الرشيدية"),
    ("recani", "Reçani", 129, "الريصاني"), ("arfoud", "Arfoud", 130, "أرفود"), ("tendite", "Tendite", 131, "تنديت"),
    ("goulmima", "Goulmima", 132, "كلميمة"), ("imilchil", "Imilchil", 133, "إملشيل"),
    ("tinejdad", "Tinejdad", 134, "تنجداد"), ("rich", "Rich", 135, "الريش"), ("midelt", "Midelt", 136, "ميدلت"),
    ("zagora", "Zagora", 137, "زاكورة"), ("ouarzazate", "Ouarzazate", 138, "ورزازات"),
    ("tinghir", "Tinghir", 139, "تنغير"), ("skoura", "Skoura", 140, "هسكورة"),
    ("kelaat-mgouna", "Kelaat Mgouna", 141, "قلعة مكونة"), ("agdz", "Agdz", 142, "أكدز"),
    ("boumalne-dades", "Boumalne Dades", 143, "بومالن دادس"), ("alnif", "Alnif", 144, "النيف"),
    ("assoul", "Assoul", 145, "أسول"), ("msemrir", "Msemrir", 146, "أمسمرير"),
    ("tazarine", "Tazarine", 147, "تازارين"), ("sidi-ifni", "Sidi Ifni", 148, "سيدي إفني"),
    ("guelmim", "Guelmim", 149, "كلميم"), ("assa", "Assa", 150, "أسا"), ("zag", "Zag", 151, "الزاك"),
    ("tan-tan", "Tan-Tan", 152, "طانطان"), ("bou-izakarn", "Bou Izakarn", 153, "بويزكارن"),
    ("mahbes", "Mahbes", 154, "المحبس"), ("lemseid", "Lemseid", 155, "لمسيد"),
    ("laayoune", "Laayoune", 156, "العيون"), ("smara", "Smara", 157, "السمارة"),
    ("boujdour", "Boujdour", 158, "بوجدور"), ("tarfaya", "Tarfaya", 159, "طرفاية"),
    ("tifariti", "Tifariti", 160, "تفاريتي"), ("boukraa", "Boukraa", 161, "بوكراع"),
    ("guelta-zemmur", "Guelta Zemmur", 162, "كلتة زمور"), ("amgala", "Amgala", 163, "أمكالة"),
    ("akhfennir", "Akhfennir", 164, "أخفنير"), ("dakhla", "Dakhla", 165, "الداخلة"),
    ("lagouira", "Lagouira", 166, "الكويرة"), ("aousserd", "Aousserd", 167, "أوسرد"),
    ("bir-gandouz", "Bir Gandouz", 168, "بئر كندوز"), ("bir-anzarane", "Bir Anzarane", 169, "بئر أنزاران"),
    ("khemis-sidi-abdeljalil", "Khemis Sidi Abdeljalil", 301, "خميس سيدي عبد الجليل"),
    ("oulad-ayad-tahla", "Oulad Ayad Tahla", 302, "أولاد عياد"), ("matmata", "Matmata", 304, "مطماطة"),
    ("imi-n-tanout", "Imi-n-Tanout", 305, "إيمنتانوت"), ("sidi-ghanem", "Sidi Ghanem", 306, "سيدي غانم"),
    ("tifentane", "Tifentane", 307, "تفنتان"), ("ait-el-kak", "Ait El Kak", 308, "آيت القاق"),
    ("agoudal", "Agoudal", 309, "أكدال أملشيل"), ("agaiouar", "Agaiouar", 311, "أكايوار"),
    ("ain-el-aouda", "Ain El Aouda", 312, "عين العودة"), ("asguine", "Asguine", 313, "أسكين"),
    ("ait-ourir", "Ait Ourir", 314, "آيت ورير"),
    ("zaouiat-moulay-brahim", "Zaouiat Moulay Brahim", 315, "زاوية مولاي ابراهيم"),
    ("toulkoult", "Toulkoult", 316, "تولكولت"), ("iks", "Iks", 317, "إيكس"), ("kers", "Kers", 318, "كرس"),
    ("tissint", "Tissint", 319, "تيسنت"), ("foum-zguid", "Foum Zguid", 320, "فم زكيد"),
    ("ksar-ich", "Ksar Ich", 321, "قصر إيش"), ("imi-n-tlat", "Imi-n-Tlat", 322, "إيمين ثلاث"),
]
# A place Habous prints a second table for, against the unit it belongs to: key, name, ville id, Arabic
# name, the unit. Agoudal is printed twice (ville 309, and 310 for a lower horizon); the unit answers both.
SECOND_TABLES = [
    ("agoudal-midelt", "Agoudal (Midelt)", 310, "اكودال املشيل ميدلت", "agoudal"),
]
# The one unit whose rows the gate files under another zone name (Western Sahara's; the tables' clock is UTC
# either way).
ZONES = {"boujdour": "Africa/El_Aaiun"}


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
    places = [(key, name, ville, label, key) for key, name, ville, label in PLACES] + list(SECOND_TABLES)
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
    for key, name, ville, label, unit in places:
        zone = ZONES.get(unit, "Africa/Casablanca")
        cc = "EH" if zone == "Africa/El_Aaiun" else "MA"
        used = [url for url, _, _ in files]
        t = Table(key, name, None, None, zone, cc, "F+E S D A M I", entry=f"ma.habous/{unit}", clock="UTC",
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
