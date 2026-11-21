"""Egypt, the General Authority for Survey (ESA): for the 30 towns Dar al-Ifta republishes ("according to the
calculations of the Egyptian General Authority for Survey"), each town's current-month table from Dar
al-Ifta's own GetPrayer endpoint; and for every city ESA's own daily all-cities page prints, those 30 and
the 48 it alone prints (ESA_CITIES, units since ruling R163), that page's row of the day. Both feed one
growing table per town (the gate's units, eg-esa.tsv): a town of the 30 grows a month at a time, a city of
the 48 by the one day each run reads, since the page is asked for plainly and shows today alone (its other
days sit behind the page's own date form, which the monitor never drives). A town of the 30's month becomes
gate rows (recipes.tsv); a city of the 48's days are a sample, six in seven never read, so the monitor's own
check reads each at the town's point and none becomes a gate row or counts as its month being watched. The
fast begins at the printed Fajr (F+E). Restricted: archive only."""
import html
import re
import urllib.parse

from common import FetchError, Table, add_all

SOURCE = "eg-esa"
DARIFTA = "https://www.dar-alifta.org/ar/Prayer/GetPrayer?town="
ESA = "https://www.esa.gov.eg/praytimes.aspx"
MONTHS = {"يناير": 1, "فبراير": 2, "مارس": 3, "أبريل": 4, "ابريل": 4, "إبريل": 4, "مايو": 5, "يونيو": 6, "يوليو": 7,
          "أغسطس": 8, "اغسطس": 8, "سبتمبر": 9, "أكتوبر": 10, "اكتوبر": 10, "نوفمبر": 11, "ديسمبر": 12}

# unit id (eg-esa.tsv), Dar al-Ifta's town name; ESA's page names the cities the same way (folded).
TOWNS = [
    ("cairo", "القاهرة"), ("alexandria", "الاسكندرية"), ("aswan", "اسوان"), ("marsa-matruh", "مطروح"), ("el-arish", "العريش"),
    ("sallum", "السلوم"), ("sidi-barrani", "سيدي براني"), ("siwa", "واحة سيوة"), ("halaib", "حلايب"), ("rafah", "رفح الجديدة"),
    ("taba", "طابا"), ("sharm-el-sheikh", "شرم الشيخ"), ("hurghada", "الغردقة"), ("kharga", "الخارجة"), ("mut", "موط (الداخلة)"),
    ("bawiti", "البويطي"), ("luxor", "الاقصر"), ("quseir", "القصير"), ("port-said", "بورسعيد"), ("el-tor", "الطور"),
    ("saint-catherine", "كاترين"), ("edfu", "ادفو"), ("el-dabaa", "الضبعه"), ("damietta", "دمياط"), ("ras-gharib", "راس غارب"),
    ("shalatin", "شلاتين"), ("farafra", "الفرافرة"), ("nuweiba", "نويبع"), ("suez", "السويس"), ("dahab", "دهب"),
]
ALIASES = {"الأسكندرية": "alexandria", "أسوان": "aswan", "الأقصر": "luxor", "أدفو": "edfu", "الضبعة": "el-dabaa",
           "رفح": "rafah", "سانت كاترين": "saint-catherine", "الداخلة": "mut", "موط": "mut", "موط (الداخلة)": "mut", "رأس غارب": "ras-gharib"}
# unit id (eg-esa.tsv), ESA's printed name: the 48 cities ESA's daily page prints and Dar al-Ifta does not
# republish, units since 12 Nov 2026 (ruling R163). The list of the archive's code/manual/eg-esa/esa_cities.py,
# which read their 2026 tables by hand; kept apart from TOWNS, which drives a Dar al-Ifta request per town.
ESA_CITIES = [
    ("tanta", "طنطا"), ("mansoura", "المنصورة"), ("el-mahalla-el-kubra", "المحلة الكبرى"), ("zagazig", "الزقازيق"),
    ("asyut", "أسيوط"), ("faiyum", "الفيوم"), ("esna", "اسنا"), ("ismailia", "الإسماعيلية"), ("6th-of-october", "6أكتـوبر"),
    ("damanhur", "دمنهور"), ("new-cairo", "القاهرة الجديدة"), ("minya", "المنيا"), ("beni-suef", "بنى سويف"), ("sohag", "سوهاج"),
    ("qena", "قنا"), ("10th-of-ramadan", "العاشر من رمضان"), ("kafr-el-sheikh", "كفر الشيخ"), ("obour", "العبور"),
    ("delengat", "الدلنجات"), ("nag-hammadi", "نجع حمادى"), ("safaga", "سفاجة"), ("el-hamul", "الحامول"), ("baltim", "بلطيم"),
    ("sadat-city", "السادات"), ("badr", "بدر"), ("el-hammam", "الحمام"), ("new-salhia", "الصالحية الجديدة"),
    ("ras-sudr", "راس سدر"), ("abu-zenima", "ابوزنيمة"), ("berenice", "برنيس"), ("marsa-alam", "مرسى علم"),
    ("el-alamein", "العلمين"), ("baris", "باريس"), ("toshka", "توشكى"), ("15th-of-may", "مدينة 15 مايو"),
    ("new-administrative-capital", "العاصمة الادارية الجديدة"), ("new-fashn", "الفشن الجديدة"),
    ("new-mansoura", "المنصورة الجديدة"), ("new-nubaria", "النوبارية الجديدة"), ("new-beni-mazar", "بنى مزار الجديدة"),
    ("new-port-said", "بورسعيد الجديدة سلام"), ("new-bir-al-abd", "بئر العبد الجديدة"), ("new-toshka", "توشكى الجديدة"),
    ("capital-gardens", "حدائق العاصمة"), ("new-rashid", "رشيد الجديدة"), ("new-sphinx", "سفنكس الجديدة"),
    ("east-owainat", "شرق العوينات"), ("west-nubaria", "غرب النوبارية"),
]
T = r"(\d{1,2}:\d{1,2}) ([صم])"
ROW = re.compile(r"(\d{2}) (\S+) (\d{4}) م " + " ".join([T] * 6))


def text(page):
    t = re.sub(r"<script.*?</script>", " ", page, flags=re.S)
    t = re.sub(r"<style.*?</style>", " ", t, flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    return html.unescape(re.sub(r"\s+", " ", t)).replace("﻿", "")


# The half of the day each of the six columns (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) falls in. The
# page's ص/م is not read: Dar al-Ifta labels a Dhuhr before noon م (once Egypt's clock goes back in
# late October, 30 October in 2026, Dhuhr falls before 12:00) and has labelled one after noon ص, so a
# 12-hour clock reading is placed by its prayer alone. Fajr and Sunrise are morning; Dhuhr is midday
# (10 and 11 o'clock are before noon, 12 is noon, 1 to 9 after it); Asr, Maghrib and Isha are afternoon.
MORNING, MIDDAY, AFTERNOON = "morning", "midday", "afternoon"
HALVES = (MORNING, MORNING, MIDDAY, AFTERNOON, AFTERNOON, AFTERNOON)


def h24(tok, half):
    """A 12-hour reading `tok` ('11:47') of a prayer in `half` of the day, as a 24-hour time. An hour
    above 12 is already on the 24-hour clock and is kept."""
    hh, mm = (int(x) for x in tok.split(":"))
    if hh <= 12:
        hh %= 12
        if half == AFTERNOON or (half == MIDDAY and hh < 10):
            hh += 12
    return f"{hh:02d}:{mm:02d}"


def norm_city(n):
    n = n.replace("ـ", "").replace("ى", "ي").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه")
    # ESA prints Mut as "موط (الداخلة )", with a space before the bracket
    n = re.sub(r"\(\s+", "(", re.sub(r"\s+\)", ")", n))
    return re.sub(r"\s+", " ", n).strip()


def month_table(page):
    """Dar al-Ifta's month table: {date: [6 times]}, each time placed in its prayer's half of the day."""
    t = text(page)
    i = t.find("مواقيت الصلاة خلال الشهر")
    if i < 0:
        return {}
    out = {}
    for m in ROW.finditer(t[i:]):
        g = m.groups()
        if g[1] not in MONTHS:
            continue
        date = f"{g[2]}-{MONTHS[g[1]]:02d}-{g[0]}"
        out[date] = [h24(g[3 + 2 * k], HALVES[k]) for k in range(6)]
    return out


def esa_day(page):
    """ESA's daily all-cities table: [(city name, date, [6 times])]."""
    t = text(page)
    i = t.find("المدينة التاريخ الميلادي")
    if i < 0:
        return []
    seg = t[i + len("المدينة التاريخ الميلادي التاريخ الهجري فجر شروق ظهر عصر مغرب عشاء"):]
    pat = re.compile(r"\s*(.+?) (\d{4}-\d{2}-\d{2}) (\d{1,2}) (.+?) (\d{4}) " + " ".join([T] * 6))
    out = []
    pos = 0
    while True:
        m = pat.match(seg, pos)
        if not m:
            break
        g = m.groups()
        out.append((g[0].strip(), g[1], [h24(g[5 + 2 * k], HALVES[k]) for k in range(6)]))
        pos = m.end()
    return out


def esa_units():
    """ESA's printed city name (folded, `norm_city`) -> unit id, for every city of its daily page the gate
    holds a unit for: the 30 towns under Dar al-Ifta's spelling and ESA's own (ALIASES), and the 48 ESA alone
    prints."""
    by_name = {norm_city(n): u for u, n in TOWNS}
    by_name.update({norm_city(k): v for k, v in ALIASES.items()})
    by_name.update({norm_city(n): u for u, n in ESA_CITIES})
    return by_name


def fetch(ctx):
    tables = {}
    esa_only = {u for u, _ in ESA_CITIES}

    def table(unit):
        if unit not in tables:
            line = (f"Egypt, ESA's own daily all-cities page {ESA} (the row of the day each run reads)" if unit in esa_only else
                    f"Egypt, ESA as Dar al-Ifta republishes it: {DARIFTA}<town> (the month table) and ESA's daily page {ESA}")
            tables[unit] = Table(unit, unit.replace("-", " ").title(), None, None, "Africa/Cairo", "EG", "F+E S D A M I", entry=f"eg.esa/{unit}",
                                 source_line=line)
        return tables[unit]

    for unit, name in TOWNS:
        url = DARIFTA + urllib.parse.quote(name)
        try:
            page = ctx.http.text(url)
        except FetchError as e:
            ctx.error(f"{unit}: {e}")
            continue
        rows = month_table(page)
        if not rows:
            ctx.error(f"{unit}: no month table in Dar al-Ifta's response for {name}")
            continue
        t = table(unit)
        add_all(ctx, t, rows, unit)
        t.raw.append((f"darifta-{unit}.html", page))
    by_name = esa_units()
    try:
        page = ctx.http.text(ESA)
        rows = esa_day(page)
        if not rows:
            ctx.error("ESA's daily page: no rows read")
        unmapped = []
        read = set()
        for city, date, times in rows:
            unit = by_name.get(norm_city(city))
            if unit is None:
                unmapped.append(city)
                continue
            t = table(unit)
            if not add_all(ctx, t, [(date, times)], f"ESA {unit}"):
                continue
            read.add(unit)
            if not any(n.startswith("esa-") for n, _ in t.raw):
                t.raw.append((f"esa-{date}.html", page))
        # The page is the only reading of the 48: one of them it did not print today is said, not passed over.
        silent = sorted(esa_only - read) if rows else []
        if silent:
            ctx.error(f"ESA's daily page printed no row for {len(silent)} of its own cities: {', '.join(silent)}")
        if unmapped:
            # A city with no unit is a new city on ESA's page (every one it printed in 2026 is a unit): its row
            # is not kept, and the run's message names every one for the owner to see.
            ctx.note(f"ESA's page lists {len(unmapped)} cities the gate has no unit for (not kept): {', '.join(unmapped)}")
    except FetchError as e:
        ctx.error(f"ESA's daily page: {e}")
    return [t for t in tables.values() if t.rows]
