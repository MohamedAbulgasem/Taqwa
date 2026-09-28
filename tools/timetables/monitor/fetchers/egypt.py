"""Egypt, the General Authority for Survey (ESA), as Dar al-Ifta republishes it town by town
("according to the calculations of the Egyptian General Authority for Survey"): each town's
current-month table from Dar al-Ifta's own GetPrayer endpoint, and ESA's daily all-cities page (one
row a day per city). Both feed one growing table per town (the gate's units, eg-esa.tsv). The fast
begins at the printed Fajr (F+E). Restricted: archive only."""
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
           "رفح": "rafah", "سانت كاترين": "saint-catherine", "الداخلة": "mut", "موط": "mut", "رأس غارب": "ras-gharib"}
T = r"(\d{1,2}:\d{1,2}) ([صم])"
ROW = re.compile(r"(\d{2}) (\S+) (\d{4}) م " + " ".join([T] * 6))


def text(page):
    t = re.sub(r"<script.*?</script>", " ", page, flags=re.S)
    t = re.sub(r"<style.*?</style>", " ", t, flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    return html.unescape(re.sub(r"\s+", " ", t)).replace("﻿", "")


def h24(tok, ampm):
    hh, mm = (int(x) for x in tok.split(":"))
    if ampm == "م" and hh < 12:
        hh += 12
    if ampm == "ص" and hh == 12:
        hh = 0
    return f"{hh:02d}:{mm:02d}"


def norm_city(n):
    n = n.replace("ـ", "").replace("ى", "ي").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه")
    return re.sub(r"\s+", " ", n).strip()


def month_table(page):
    """Dar al-Ifta's month table: {date: [6 times]} (a Dhuhr labelled a.m. by mistake is p.m.)."""
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
        vals = [h24(g[3 + 2 * k], g[4 + 2 * k]) for k in range(6)]
        if int(vals[2][:2]) < 10:
            vals[2] = f"{int(vals[2][:2]) + 12:02d}{vals[2][2:]}"
        out[date] = vals
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
        out.append((g[0].strip(), g[1], [h24(g[5 + 2 * k], g[6 + 2 * k]) for k in range(6)]))
        pos = m.end()
    return out


def fetch(ctx):
    tables = {}

    def table(unit):
        if unit not in tables:
            tables[unit] = Table(unit, unit.replace("-", " ").title(), None, None, "Africa/Cairo", "EG", "F+E S D A M I", entry=f"eg.esa/{unit}",
                                 source_line=f"Egypt, ESA as Dar al-Ifta republishes it: {DARIFTA}<town> (the month table) and ESA's daily page {ESA}")
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
    by_name = {norm_city(n): u for u, n in TOWNS}
    by_name.update({norm_city(k): v for k, v in ALIASES.items()})
    try:
        page = ctx.http.text(ESA)
        rows = esa_day(page)
        if not rows:
            ctx.error("ESA's daily page: no rows read")
        unmapped = []
        for city, date, times in rows:
            unit = by_name.get(norm_city(city))
            if unit is None:
                unmapped.append(city)
                continue
            t = table(unit)
            if not add_all(ctx, t, [(date, times)], f"ESA {unit}"):
                continue
            if not any(n.startswith("esa-") for n, _ in t.raw):
                t.raw.append((f"esa-{date}.html", page))
        if unmapped:
            ctx.note(f"ESA's page lists {len(unmapped)} cities the gate has no unit for (not kept): {', '.join(unmapped[:8])} …")
    except FetchError as e:
        ctx.error(f"ESA's daily page: {e}")
    return [t for t in tables.values() if t.rows]
