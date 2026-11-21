"""Kyrgyzstan, the Spiritual Administration of Muslims of Kyrgyzstan (the Muftiate, muftiyat.kg): its calendar page,
/kg/calendar/?start=dd-mm-yyyy&end=dd-mm-yyyy, the current year and the next, at every one of its localities (115 on
12 Nov 2026: Bishkek, Osh and the seven regions' towns and villages; the list is its documented API's own,
/kg/api/v1/calendar/alllocations/).

The page reads its locality from the cookie `point=<id>`, the one the site's own locality picker sets, and names it
as the selected option of that picker: a reply whose selected locality is not the one asked for is refused, so one
locality's table can never be filed under another's.

What the cookie is, exactly (audit finding F29, looked at on 13 Nov 2026, two plain GETs held under
archive/raw/manual/kg-muftiyat/2026-11-13/ with their MANIFEST): a visitor's choice of locality and nothing else.
The site's own script (static/js/common.js) writes it when the picker (#region-select) changes, `point=<locality
id>` with path /, and reloads the calendar; the value is the same public id the documented API's list gives. It is
no login, no session and no token: the site asks for no account, the cookie carries no secret, and every visitor's
browser sends the same header once a locality is picked. The request stays one GET, with no form posted and no jar
kept (each request names its own locality; nothing the server sets is sent back). What was seen without it, once:
asked for the year 2027 with no cookie, the server replied 302 with `Set-Cookie: point=1; Path=/` (Bishkek) and a
Location for the same page from that day to the month's end (start=13-11-2026&end=30-11-2026). The redirect was not
followed and only that one query was tried, so what the page serves after it is not held; what is held is that a
request without the cookie is not given the year it asked for, and that the server itself picks Bishkek. The header
therefore stays, and is written down here, in sources.tsv and in docs/MONITOR.md. One other route is seen in the
site's own script and was not tried: common.js builds /{lang}/calendar/<locality id>/orozo/, the locality in the
path and no cookie, for the Ramadan (orozo) table; whether a year's table answers at such a path is the owner's to
have asked.

A whole year is one request (the documented API, /kg/api/v1/calendar/<id>/, serves at most two months a request,
six times as many: 1,380 against 230): 230 requests a run, plus the list, each page asked at least KG_GAP seconds
after the request before it (the client's own interval was shorter, 1.5 s, until 13 November 2026 and is the same
two seconds since; the gap stays written here so that 230 pages in a row at one site are never asked faster).
The page embeds the days as a script array, `days=[{date, weekday, fajr, sunrise, dhuhr, asr, sunset, maghrib, isha,
tahajjud}]`, in whole minutes; "sunset" is the plain sunset and "maghrib" the Muftiate's own Maghrib after it, which
is the M column here. The page's column header marks Fajr as also the start of the fast (Suhur), so the Fajr column
is also the end of eating (F+E). The Muftiate publishes no coordinates: each locality's point is the one its own
tables are computed for, recovered from them (POINTS, below), and a unit of kg.default there (CentralAsia.kt's
kyrgyzstanUnits), so each table is the entry's at its unit (`kg.default/<key>`, as kg-default.tsv reads it). The Muftiate
prints the same month-day times every year, and in a leap year the same rows by day of the year (its 29 Feb 2028 is
every other year's 1 Mar).

A reply without every day of its year (365 or 366) is refused, so a cut-short year is never filed as the whole.
A locality the list names that POINTS does not is an error of the run (a new locality needs its point before it can
be a unit), and so is one of POINTS the list no longer names. Terms: "© 2026 Бардык укуктары корголгон" (all rights
reserved); robots.txt allows all; used only to fit and prove, restricted to the archive."""
import ast
import datetime
import json
import re

from common import FetchError, Table, add_all

SOURCE = "kg-muftiyat"
ENTRY = "kg.default"
LIST_URL = "https://muftiyat.kg/kg/api/v1/calendar/alllocations/"
CALENDAR_URL = "https://muftiyat.kg/kg/calendar/?start=01-01-{year}&end=31-12-{year}"
ZONE = "Asia/Bishkek"
KG_GAP = 2.0  # seconds before each calendar page: 230 in a row at one site

# id, key, name, lat, lon: the Muftiate's localities, each at the point its own tables are computed for, recovered from
# its 2026 table (the same month-day times every year) on 12 Nov 2026: the point where the method's sunrise, transit,
# sunset, 18 deg dawn and 16 deg dusk fall within each printed minute's rounding (KyrgyzLocalities.kt holds the same).
POINTS = [
    (1, "bishkek", "Bishkek", "42.8668", "74.5813"),  # Бишкек
    (2, "osh", "Osh", "40.5136", "72.7993"),  # Ош шаары
    (4, "kemin", "Kemin", "42.7784", "75.6863"),  # Кемин
    (5, "talas", "Talas", "42.5278", "72.2116"),  # Талас
    (6, "balykchy", "Balykchy", "42.4500", "76.1749"),  # Балыкчы
    (7, "karakol", "Karakol", "42.4854", "78.3985"),  # Каракол
    (8, "cholpon-ata", "Cholpon-Ata", "42.6478", "77.0705"),  # Чолпон-Ата
    (9, "bokonbaev", "Bokonbaev", "42.1150", "76.9929"),  # Бөкөнбаев
    (10, "bakay-ata", "Bakay-Ata", "42.4840", "71.9328"),  # Бакай-Ата
    (11, "manas-talas", "Manas (Talas)", "42.7218", "71.6065"),  # Манас
    (12, "aytmatov", "Aytmatov", "42.6134", "71.5899"),  # Айтматов
    (22, "tokmok", "Tokmok", "42.8354", "75.2916"),  # Токмок ш
    (23, "kant", "Kant", "42.8908", "74.8471"),  # Кант
    (24, "sokuluk", "Sokuluk", "42.8494", "74.2941"),  # Сокулук
    (25, "kara-balta", "Kara-Balta", "42.8216", "73.8483"),  # Кара-Балта
    (26, "suusamyr", "Suusamyr", "42.1808", "73.9622"),  # Суусамыр
    (29, "kochkor", "Kochkor", "42.2142", "75.7540"),  # Кочкор
    (30, "naryn", "Naryn", "41.4286", "75.9953"),  # Нарын
    (31, "at-bashy", "At-Bashy", "41.1656", "75.8113"),  # Ат Башы
    (32, "ak-talaa", "Ak-Talaa", "41.0844", "75.6760"),  # Ак-Талаа
    (33, "torugart", "Torugart", "40.5892", "75.4146"),  # Торугарт
    (34, "ozgorush", "Ozgorush", "41.2092", "76.2079"),  # Өзгөрүш
    (35, "toktogul", "Toktogul", "41.8692", "72.9479"),  # Токтогул
    (36, "karakol-36", "Kara-Kol (36)", "41.6288", "72.6743"),  # Каракөл
    (37, "tash-komur", "Tash-Komur", "41.3348", "72.2097"),  # Таш-Көмүр
    (38, "shamaldy-say", "Shamaldy-Say", "41.1928", "72.1797"),  # Шамалды-Сай
    (39, "kochkor-ata", "Kochkor-Ata", "41.0424", "72.4808"),  # Кочкор-Ата
    (40, "nooken", "Nooken", "41.0366", "72.5919"),  # Ноокен
    (41, "bazar-korgon", "Bazar-Korgon", "41.0310", "72.7425"),  # Бaзaр-Кoргoн
    (42, "manas-city", "Manas (Jalal-Abad)", "40.9252", "72.9862"),  # Манас ш.
    (43, "suzak", "Suzak", "40.8998", "72.9067"),  # Сузак
    (44, "mayluu-suu", "Mayluu-Suu", "41.2506", "72.4643"),  # Майлуу-Суу
    (45, "ozgon", "Ozgon", "40.7676", "73.2977"),  # Өзгөн
    (46, "kara-suu", "Kara-Suu", "40.6976", "72.8687"),  # Кара-Суу
    (47, "otuz-adyr", "Otuz-Adyr", "40.6038", "72.9653"),  # Отуз-Адыр
    (49, "aravan", "Aravan", "40.0464", "72.4835"),  # Араван
    (50, "nookat", "Nookat", "40.2704", "72.6102"),  # Ноокат
    (51, "gulcho", "Gulcho", "40.3078", "73.4455"),  # Гүлчө
    (52, "daroot-korgon", "Daroot-Korgon", "39.5490", "72.2081"),  # Дароот-Коргон
    (53, "kara-kulzha", "Kara-Kulzha", "40.6282", "73.5892"),  # Кара-Кулжа
    (54, "amanbaev", "Amanbaev", "42.6062", "71.1871"),  # Аманбаев
    (57, "batken", "Batken", "40.0484", "70.8171"),  # Баткен
    (58, "kadamzhay", "Kadamzhay", "40.1146", "71.7018"),  # Кадамжай
    (59, "razzakov", "Razzakov", "39.8298", "69.5159"),  # Раззаков
    (60, "suluktu", "Suluktu", "39.9306", "69.5495"),  # Сүлүктү
    (61, "kyzyl-kyya", "Kyzyl-Kyya", "40.2456", "72.1340"),  # Кызыл-Кыя
    (62, "ak-say", "Ak-Say", "39.8684", "70.5167"),  # Ак-Сай
    (63, "burgondu", "Burgondu", "40.2478", "71.0849"),  # Бүргөндү
    (64, "katrang", "Katrang", "39.8464", "69.9994"),  # Катраң
    (65, "kulundu", "Kulundu", "40.0968", "69.6336"),  # Кулунду
    (66, "kok-tash-batken", "Kok-Tash (Batken)", "39.7158", "69.3847"),  # Көк-Таш
    (67, "maydan", "Maydan", "40.0478", "72.0853"),  # Майдан
    (68, "muras", "Muras", "40.0142", "69.9335"),  # Мурас
    (69, "ormosh", "Ormosh", "39.9288", "71.2498"),  # Ормош
    (70, "ravat", "Ravat", "39.8654", "70.1494"),  # Рават
    (71, "samarkandek", "Samarkandek", "39.9986", "70.5349"),  # Самаркандек
    (72, "sogment", "Sogment", "39.8810", "71.0329"),  # Согмент
    (73, "zhengish", "Zhengish", "40.1984", "71.6171"),  # Жеңиш
    (74, "rout", "Rout", "39.7468", "70.8996"),  # Роут
    (75, "ak-tash", "Ak-Tash", "41.6978", "70.6338"),  # Ак-Таш
    (76, "ala-buka", "Ala-Buka", "41.3822", "71.4837"),  # Ала-Бука
    (77, "arkyt", "Arkyt", "41.7968", "71.9508"),  # Аркыт
    (78, "arslanbap", "Arslanbap", "41.3306", "72.9177"),  # Арсланбап
    (79, "kazarman", "Kazarman", "41.3974", "74.0159"),  # Казарман
    (80, "kanysh-kyya", "Kanysh-Kyya", "41.7484", "71.0845"),  # Каныш-Кыя
    (81, "kara-alma", "Kara-Alma", "41.1968", "73.3347"),  # Кара-Алма
    (82, "kara-kol", "Kara-Kol", "41.6142", "72.6663"),  # Кара-Көл
    (83, "kerben", "Kerben", "41.4804", "71.7500"),  # Кербен
    (84, "kyzyl-tuu", "Kyzyl-Tuu", "41.0976", "72.1831"),  # Кызыл-туу
    (85, "kyzyl-ozgorush", "Kyzyl-Ozgorush", "41.6302", "73.4010"),  # Кызыл-Өзгөрүш
    (86, "kok-tash-jalal-abad", "Kok-Tash (Jalal-Abad)", "41.1936", "71.0202"),  # Кок-Таш
    (87, "maasy", "Maasy", "41.0486", "72.6161"),  # Маасы
    (88, "chychkan-suu", "Chychkan-Suu", "42.0490", "72.7993"),  # Чычкан-Суу
    (89, "baetov", "Baetov", "41.2680", "74.9502"),  # Баетов
    (90, "segizinchi-mart", "Segizinchi Mart", "41.4664", "75.3821"),  # Сегизинчи Март
    (91, "kazybek", "Kazybek", "41.0270", "75.5307"),  # Казыбек
    (92, "kosh-bulak", "Kosh-Bulak", "41.4666", "74.3819"),  # Кош-Булак
    (93, "kosh-dobo", "Kosh-Dobo", "41.0770", "74.2506"),  # Кош-Дөбө
    (94, "kyzart", "Kyzart", "42.0170", "74.9692"),  # Кызарт
    (95, "ming-kush", "Ming-Kush", "41.6694", "74.4516"),  # Миң-Куш
    (96, "tash-bashat", "Tash-Bashat", "41.4662", "76.3821"),  # Таш-Башат
    (97, "chaek", "Chaek", "41.9170", "74.5010"),  # Чаек
    (98, "akshar", "Akshar", "40.4306", "72.2008"),  # Акшар
    (99, "oy-tal", "Oy-Tal", "40.4176", "74.1000"),  # Ой-Тал
    (100, "papan", "Papan", "40.2968", "72.9839"),  # Папан
    (101, "salamalik", "Salamalik", "40.8312", "73.6177"),  # Саламалик
    (102, "sary-mogol", "Sary-Mogol", "39.6644", "72.8661"),  # Сары-Могол
    (103, "sopu-korgon", "Sopu-Korgon", "40.0138", "73.4841"),  # Сопу-Коргон
    (104, "terek-alay-kuu", "Terek Alay-Kuu", "40.3466", "74.2165"),  # Терек Алай-Куу
    (105, "erkeshtam", "Erkeshtam", "39.6656", "73.8858"),  # Эркештам
    (106, "kara-oy", "Kara-Oy", "42.4474", "72.7002"),  # Кара-Ой
    (107, "kyzyl-adyr", "Kyzyl-Adyr", "42.6144", "71.5835"),  # Кызыл-Адыр
    (108, "pokrovka", "Pokrovka", "42.7136", "71.6000"),  # Покровка
    (109, "taldy-bulak", "Taldy-Bulak", "42.3992", "72.9814"),  # Талды-Булак
    (110, "sheker", "Sheker", "42.5316", "71.1512"),  # Шекер
    (111, "otmok", "Otmok", "42.1980", "73.2488"),  # Өтмөк
    (112, "manas-airport", "Manas Airport", "43.0472", "74.4712"),  # Аэропорт Манас
    (113, "belovodskoe", "Belovodskoe", "42.8274", "74.1008"),  # Беловодское
    (114, "kaindy", "Kaindy", "42.8174", "73.6808"),  # Каинды
    (115, "kamyshanovka", "Kamyshanovka", "43.2170", "74.2298"),  # Камышановка
    (116, "kyzyl-oy", "Kyzyl-Oy", "41.9478", "74.1506"),  # Кызыл-Ой
    (117, "too-ashuu", "Too-Ashuu", "42.2276", "73.7314"),  # Төө-Ашуу
    (118, "chaldovar", "Chaldovar", "42.8182", "73.5511"),  # Чалдовар
    (119, "ysyk-ata", "Ysyk-Ata", "42.5784", "74.9010"),  # Ысык-Ата
    (120, "ak-bulung", "Ak-Bulung", "42.6278", "78.8205"),  # Ак-Булуң
    (121, "ak-shyyrak", "Ak-Shyyrak", "41.7978", "78.7210"),  # Ак-Шыйрак
    (122, "ala-bash", "Ala-Bash", "42.1000", "76.4718"),  # Ала-Баш
    (123, "ananevo", "Ananevo", "42.7274", "77.6496"),  # Ананьево
    (124, "barskoon", "Barskoon", "42.1486", "77.5996"),  # Барскоон
    (125, "kara-koo", "Kara-Koo", "42.1978", "76.6323"),  # Кара-Коо
    (126, "kumtor", "Kumtor", "41.8466", "78.1903"),  # Кумтөр
    (127, "kyzyl-suu", "Kyzyl-Suu", "42.3272", "78.0006"),  # Кызыл-Суу
    (128, "tamchy", "Tamchy", "42.5660", "76.6706"),  # Тамчы
    (129, "tup", "Tup", "42.7182", "78.3691"),  # Түп
    (130, "engilchek", "Engilchek", "42.0282", "79.0705"),  # Эңилчек
]

FIELDS = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

DAYS_RE = re.compile(r"\bdays\s*=\s*(\[.*?\])\s*;?\s*\n", re.S)
SELECTED_RE = re.compile(r'<option\s+selected\s+value="(\d+)"')


def parse_locations(body):
    """The documented list: [{"id", "title", "locations": [{"id", "title"}]}] -> {id: (title, region)}."""
    try:
        regions = json.loads(body.decode("utf-8", "replace"))
    except ValueError as e:
        raise FetchError(f"the locality list is not JSON ({e})")
    out = {}
    try:
        for region in regions:
            for loc in region["locations"]:
                out[int(loc["id"])] = (str(loc["title"]), str(region["title"]))
    except (KeyError, TypeError, ValueError) as e:
        raise FetchError(f"the locality list has no {e}")
    if not out:
        raise FetchError("the locality list is empty")
    return out


def parse_calendar(body, point_id, year):
    """The calendar page's days for `point_id`: {yyyy-mm-dd: [fajr, sunrise, dhuhr, asr, maghrib, isha]}."""
    text = body.decode("utf-8", "replace")
    selected = SELECTED_RE.findall(text)
    if selected != [str(point_id)]:
        raise FetchError(f"the page's selected locality is {selected or 'none'}, not {point_id}")
    m = DAYS_RE.search(text)
    if not m:
        raise FetchError("no days array in the page")
    try:
        days = ast.literal_eval(m.group(1))
    except (ValueError, SyntaxError):
        raise FetchError("the days array is not a literal")
    rows = {}
    for x in days:
        try:
            date = str(x["date"])[:10]
            rows[date] = [x[k] for k in FIELDS]
        except (KeyError, TypeError) as e:
            raise FetchError(f"a day without {e}")
    if not rows or any(not d.startswith(f"{year}-") for d in rows):
        raise FetchError(f"days outside {year}")
    first = datetime.date(year, 1, 1)
    whole = {(first + datetime.timedelta(days=i)).isoformat() for i in range((datetime.date(year + 1, 1, 1) - first).days)}
    missing = sorted(whole - set(rows))
    if missing:
        raise FetchError(f"{len(rows)} days of {year}'s {len(whole)}, {missing[0]} the first missing (a cut-short reply)")
    return rows


def fetch(ctx):
    listed = parse_locations(ctx.http.get(LIST_URL))
    known = {p[0] for p in POINTS}
    for pid in sorted(set(listed) - known):
        ctx.error(f"locality {pid} is listed but has no point yet (a new locality: recover its point first)")
    for pid in sorted(known - set(listed)):
        ctx.error(f"locality {pid} is no longer listed")
    tables = []
    for year in (ctx.today.year, ctx.today.year + 1):
        url = CALENDAR_URL.format(year=year)
        for pid, key, name, lat, lon in POINTS:
            if pid not in listed:
                continue
            try:
                body = ctx.http.get(url, headers={"Cookie": f"point={pid}"}, gap=KG_GAP)
                rows = parse_calendar(body, pid, year)
            except FetchError as e:
                ctx.error(f"{name} {year}: {e}")
                continue
            t = Table(f"{key}-{year}", f"{name} {year}", float(lat), float(lon), ZONE, "KG", "F+E S D A M I", entry=f"{ENTRY}/{key}",
                      school="hanafi", source_line=f"Kyrgyzstan Muftiate (muftiyat.kg) {url} with the cookie point={pid} "
                                                   f"(the site's own calendar page, locality {pid}); all rights reserved, archive only",
                      raw=[(f"{key}-{year}.html", body)])
            added = add_all(ctx, t, rows, f"{name} {year}")
            t.merge = False
            if added < len(rows):
                # Ruling R281 (the second audit's G19): a cell that is not a time is a change of format, an error and
                # never a note; and the year is not filed in part (it replaces what is held, merge off), so the
                # whole year held before stands.
                ctx.error(f"{name} {year}: {len(rows) - added} of {len(rows)} days could not be read (a cell that is not a time): "
                          "the Muftiate's page may have changed; the year is not taken")
                continue
            tables.append(t)
    return tables
