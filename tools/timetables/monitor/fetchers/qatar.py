"""Qatar: the Ministry of Awqaf's API for Doha over the coming two weeks (Fajr, sunrise, Maghrib and
Isha as the Calendar House prints them; its Dhuhr and Asr a minute earlier, so those two are kept
as a derived table with the calendar's minute added, as qa-calendarhouse.tsv checks them), and the
Calendar House site's own header (today's Doha times). The printed calendar and the Ramadan
imsakiya stay manual (PDFs). The fast begins at the printed Fajr (F+E)."""
import datetime as dt
import json
import re

from common import FetchError, Table, norm_time

SOURCE = "qa-calendarhouse"
MINISTRY = "https://meiaservicesext.islam.gov.qa/ServicesBrokerGatewayAPI/api/PrayerTimes?date="
QATARCH = "https://www.qatarch.com/"
DOHA_CITY_ID = 3
AR = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")


def ministry_day(body):
    j = json.loads(body.decode("utf-8", "replace"))
    g = j["gregorianDate"]
    date = f"{int(g['year']):04d}-{int(g['month']):02d}-{int(g['day']):02d}"
    tm = {t["prayerTimeName"]: (int(t["time"]["hour"]), int(t["time"]["minutes"])) for t in j["times"]}
    dh = tm.get("Dhuhr") or tm.get("Jummah")
    vals = [tm["Fajr"], tm["Sunrise"], dh, tm["Asr"], tm["Maghrib"], tm["Isha"]]
    if any(v is None for v in vals):
        raise FetchError(f"{date}: a prayer is missing ({sorted(tm)})")
    return date, [f"{h:02d}:{m:02d}" for h, m in vals]


def plus_minute(t):
    h, m = (int(x) for x in t.split(":"))
    m += 1
    if m == 60:
        h, m = h + 1, 0
    return f"{h:02d}:{m:02d}"


def t24(k, v):
    s = norm_time(v)
    h, m = (int(x) for x in s.split(":"))
    if k == "thahr" and h < 10:
        h += 12
    if k in ("aser", "moghreb", "ishaa") and h < 12:
        h += 12
    return f"{h:02d}:{m:02d}"


def header(page, today):
    """The site's prayData for Doha and the day it is for: the calendar's `today` flag, else the
    local (UTC+3) date."""
    m = re.search(r"var prayData\s*=\s*(\[.*?\]);", page, flags=re.S)
    if not m:
        raise FetchError("no prayData on the page")
    pd = json.loads(m.group(1))
    doha = next((p for p in pd if int(p.get("cityId", 0)) == DOHA_CITY_ID), None)
    if doha is None:
        raise FetchError("no Doha row in prayData")
    vals = [t24(k, doha[k]) for k in ("fajr", "shrouq", "thahr", "aser", "moghreb", "ishaa")]
    local = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=3)).date()
    date = local
    c = re.search(r"var calData\s*=\s*(\{.*?\});", page, flags=re.S)
    if c:
        try:
            cd = json.loads(c.group(1))
            today_g = None
            for mo in cd.get("days", []):
                for dd in mo.get("days", []):
                    if dd.get("today"):
                        today_g = int(str(dd["m"]).translate(AR))
            if today_g is not None and today_g != local.day:
                for delta in (-1, 1):
                    if (local + dt.timedelta(days=delta)).day == today_g:
                        date = local + dt.timedelta(days=delta)
                        break
                else:
                    raise FetchError(f"calData says today is the {today_g}th, the clock the {local.day}th: skipped")
        except (ValueError, KeyError, TypeError) as e:
            raise FetchError(f"calData unreadable ({e})")
    return date.isoformat(), vals


def fetch(ctx):
    tables = []
    ministry = Table("doha-ministry", "Doha, the ministry API", None, None, "Asia/Qatar", "QA", "F+E S - - M I", entry="qa.calendarhouse/doha",
                     source_line=f"Qatar Ministry of Awqaf API {MINISTRY}DD-MM-YYYY (Doha), 24h")
    derived = Table("doha-derived", "Doha, the API's Dhuhr and Asr plus the calendar's minute (derived)", None, None, "Asia/Qatar", "QA",
                    "- - D A - -", entry="qa.calendarhouse/doha",
                    source_line="Calendar House Doha derived: the ministry API's Dhuhr and Asr with the calendar's extra minute (qa-calendarhouse.tsv)")
    for i in range(14):
        day = ctx.today + dt.timedelta(days=i)
        url = MINISTRY + day.strftime("%d-%m-%Y")
        try:
            body = ctx.http.get(url)
            date, vals = ministry_day(body)
        except (FetchError, KeyError, ValueError) as e:
            ctx.error(f"ministry {day}: {e}")
            continue
        ministry.add(date, vals)
        derived.add(date, [vals[0], vals[1], plus_minute(vals[2]), plus_minute(vals[3]), vals[4], vals[5]])
        ministry.raw.append((f"ministry-{date}.json", body))
    if ministry.rows:
        tables += [ministry, derived]
    try:
        page = ctx.http.text(QATARCH)
        date, vals = header(page, ctx.today)
        web = Table("doha-website", "Doha, the Calendar House site's header", None, None, "Asia/Qatar", "QA", "F+E S - - M I", entry="qa.calendarhouse/doha",
                    source_line=f"Qatar Calendar House website header prayData (Doha, as computed by qatarch.com) {QATARCH}; one row per fetch, dated by the site's own calendar",
                    raw=[(f"qatarch-{date}.html", page)])
        web.add(date, vals)
        tables.append(web)
    except FetchError as e:
        ctx.error(f"qatarch.com: {e}")
    return tables
