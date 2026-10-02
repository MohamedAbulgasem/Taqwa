"""Jordan, the Ministry of Awqaf: awqaf.gov.jo's prayer-times page (AR/Pages/PrayerTime), an ASP.NET
form whose table lists the region's days from today, ten a page. Region 1 is "Amman, Balqa, Zarqa,
Madaba" (عمان، البلقاء، الزرقاء، مادبا), one table for the four, so the same days are checked at Amman
(jo.awqaf's one unit, jo-awqaf.tsv's point) and at Zarqa (the entry beyond the unit's reach).

The page's own date search has failed server-side since at least October 2026 (HTTP 500, a data
binding error), so the fetcher walks the table's pager instead: the first page as served, then
pages 2 to PAGES by the form's own postbacks, within one session (the site checks its view state
against the session cookie). Times are printed on a 12-hour clock with no AM or PM: Fajr and
sunrise are morning times, Dhuhr is after ten, Asr, Maghrib and Isha are afternoon. A page is
refused unless its header names the seven columns, the region selected is region 1 under its own
name, the first day is today (give or take a day for the site's clock) and each page carries on
from the day after the last. A day whose times are not in the day's order is left out and the
fetch is partial. The table holds one year: in December the pager ends with 31 December, which is
the table's end, not a failure. Every run adds its days to the held table, so the months build up.
Restricted: archive only."""
import datetime as dt
import http.cookiejar
import html
import re

from common import FetchError, Table, norm_time

SOURCE = "jo-awqaf"
PAGE = "https://awqaf.gov.jo/AR/Pages/PrayerTime"
REGION = "1"
REGION_WORDS = ("عمان", "الزرقاء")
PAGES = 10  # ten days a page: today and the 99 days after
HEADER = ["التاريخ", "الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"]
GRID = "ctl00$MainContent$gvWebparts"
# key, name, lat, lon, entry
PLACES = [
    ("amman", "Amman (region: Amman, Balqa, Zarqa, Madaba)", None, None, "jo.awqaf/jo.awqaf.amman"),
    ("zarqa", "Zarqa (region: Amman, Balqa, Zarqa, Madaba)", 32.07275, 36.08796, "jo.awqaf"),
]


def clean(fragment):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", fragment))).strip()


def hidden_fields(page):
    """Every hidden input of the form, {name: value}, whatever the attributes' order."""
    out = {}
    for tag in re.findall(r"<input\b[^>]*>", page, flags=re.I):
        attrs = dict((k.lower(), html.unescape(v)) for k, v in re.findall(r'(\w[\w-]*)="([^"]*)"', tag))
        if attrs.get("type", "").lower() == "hidden" and "name" in attrs:
            out[attrs["name"]] = attrs.get("value", "")
    if "__VIEWSTATE" not in out:
        raise FetchError("no __VIEWSTATE on the page: not the ASP.NET form")
    return out


def region(page):
    """(value, label) of the region the form shows as selected."""
    m = re.search(r'<select[^>]*id="MainContent_DropCompany"[^>]*>(.*?)</select>', page, flags=re.S)
    if not m:
        raise FetchError("no region list on the page")
    sel = re.search(r'<option[^>]*selected="selected"[^>]*value="([^"]*)"[^>]*>([^<]*)', m.group(1))
    if not sel:
        raise FetchError("no region selected on the page")
    return sel.group(1), clean(sel.group(2))


def grid(page):
    """(header cells, [(date, [six cells])], {page numbers the pager offers}, the grid's markup)."""
    i = page.find('id="MainContent_gvWebparts"')
    if i < 0:
        raise FetchError("no prayer table on the page")
    start = page.rfind("<table", 0, i)
    end = page.find("pagerGV", i)
    body = page[start:end if end > 0 else len(page)]
    header = [clean(x) for x in re.findall(r"<th[^>]*>(.*?)</th>", body, flags=re.S)]
    rows = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", body, flags=re.S):
        cells = [clean(x) for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if not cells:
            continue
        m = re.match(r"^(\d{1,2})/(\d{1,2})/(\d{4})$", cells[0])
        if not m or len(cells) != 7:
            raise FetchError(f"a row of the table does not read as a date and six times ({len(cells)} cells)")
        try:
            date = dt.date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
        except ValueError:
            raise FetchError("a row's date is not a date")
        rows.append((date, cells[1:]))
    tail = page[end:end + 6000] if end > 0 else ""
    offered = {int(n) for n in re.findall(r"Page\$(\d+)", tail)}
    keep = page[start:end + 6000] if end > 0 else body
    return header, rows, offered, keep


def day_times(cells):
    """The six printed cells as 24-hour times, in the day's order, or FetchError (no cell quoted)."""
    out = []
    for i, cell in enumerate(cells):
        t = norm_time(cell)
        if t == "-":
            raise FetchError(f"cell {i + 1} is empty")
        h, mi = (int(x) for x in t.split(":"))
        if i <= 1 and h >= 12:
            raise FetchError(f"cell {i + 1} is not a morning time")
        if i == 2 and h < 10:
            h += 12
        if i >= 3 and h < 12:
            h += 12
        out.append(h * 60 + mi)
    if any(b <= a for a, b in zip(out, out[1:])):
        raise FetchError("the times are not in the day's order")
    return [f"{m // 60:02d}:{m % 60:02d}" for m in out]


def read_page(page, expect_first=None, today=None):
    """The page's days, checked: its header, its region, and that it starts where it should
    (`expect_first`, else today give or take a day)."""
    header, rows, offered, keep = grid(page)
    if header != HEADER:
        raise FetchError(f"the table's header is not the seven columns expected ({len(header)} cells)")
    value, label = region(page)
    if value != REGION or not all(w in label for w in REGION_WORDS):
        raise FetchError(f"the region shown is {value} ({label}), not Amman, Balqa, Zarqa, Madaba")
    if not rows:
        raise FetchError("the table has no days")
    dates = [d for d, _ in rows]
    if any((b - a).days != 1 for a, b in zip(dates, dates[1:])):
        raise FetchError("the page's days do not follow one another")
    if expect_first is not None and dates[0] != expect_first:
        raise FetchError(f"the page starts on {dates[0]}, not {expect_first}, the day after the last page")
    if expect_first is None and today is not None and abs((dates[0] - today).days) > 1:
        raise FetchError(f"the first page starts on {dates[0]}, not today ({today})")
    return rows, offered, keep


def fetch(ctx):
    jar = http.cookiejar.CookieJar()
    page = ctx.http.text(PAGE, jar=jar)
    rows, offered, keep = read_page(page, today=ctx.today)
    days = list(rows)
    raw = [(f"page-01-{ctx.today.isoformat()}.html", keep)]
    for n in range(2, PAGES + 1):
        if n not in offered:
            if (days[-1][0].month, days[-1][0].day) == (12, 31):
                ctx.note(f"the table ends with the year on {days[-1][0]} (the site lists one year at a time)")
                break
            ctx.error(f"the pager offers no page {n} after {days[-1][0]} (the site lists fewer days than the {PAGES * 10} asked)")
            break
        form = hidden_fields(page)
        form.update({"ctl00$MainContent$DropCompany": REGION, "ctl00$MainContent$txtFromDate": "", "ctl00$MainContent$txtToDate": "",
                     "ctl00$txtSearch": "", "ctl00$txtSearch1": "", "__EVENTTARGET": GRID, "__EVENTARGUMENT": f"Page${n}"})
        try:
            page = ctx.http.text(PAGE, data=form, jar=jar, headers={"Referer": PAGE})
            rows, offered, keep = read_page(page, expect_first=days[-1][0] + dt.timedelta(days=1))
        except FetchError as e:
            ctx.error(f"page {n}: {e}")
            break
        days += rows
        raw.append((f"page-{n:02d}-{ctx.today.isoformat()}.html", keep))
    good = {}
    for date, cells in days:
        try:
            good[date.isoformat()] = day_times(cells)
        except FetchError as e:
            ctx.error(f"{date}: {e}; the day is left out")
    tables = []
    for key, name, lat, lon, entry in PLACES:
        t = Table(key, name, lat, lon, "Asia/Amman", "JO", "F S D A M I", entry=entry,
                  source_line=f"Jordan, Ministry of Awqaf, {PAGE} region {REGION} (Amman, Balqa, Zarqa, Madaba), the table's pages from today; "
                              "12-hour times read as the day's",
                  raw=raw if key == "amman" else [])
        for date, times in good.items():
            t.add(date, times)
        if t.rows:
            tables.append(t)
    return tables
