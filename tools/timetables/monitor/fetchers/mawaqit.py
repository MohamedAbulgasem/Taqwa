"""The surveyed Mawaqit calendars (official/survey/*/calendars.tsv): each mosque's page on
mawaqit.net carries its whole calendar (confData.calendar, twelve months of days), read for the
current year and checked at the mosque's own point through its survey's machinery, with the faults
and outliers the survey records for it. A calendar the mosque changed is the news. Monthly.
Restricted: archive only."""
import json
import re

from common import FetchError, Table, survey_calendars

SOURCE = "mawaqit"
PAGE = "https://mawaqit.net/en/"
ZONES = {"gb-cautious": "Europe/London", "fr-cautious": "Europe/Paris", "be-cautious": "Europe/Brussels",
         "nl-cautious": "Europe/Amsterdam", "de-cautious": "Europe/Berlin"}


def conf_data(page):
    m = re.search(r"confData\s*=\s*(\{.*)", page, flags=re.S)
    if not m:
        raise FetchError("no confData on the page")
    raw = m.group(1)
    depth = 0
    end = None
    in_string = False
    escape = False
    for i, ch in enumerate(raw):
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise FetchError("confData does not close")
    return json.loads(raw[:end])


def calendar_rows(conf, year):
    cal = conf.get("calendar")
    if not cal:
        raise FetchError("no calendar in confData")
    import datetime as dt
    rows = {}
    for mi, month in enumerate(cal):
        if not isinstance(month, dict):
            continue
        for day, row in month.items():
            try:
                d = dt.date(year, mi + 1, int(day))
            except ValueError:
                continue
            if isinstance(row, list) and len(row) >= 6:
                rows[d.isoformat()] = row[:6]
    if not rows:
        raise FetchError("an empty calendar")
    return rows


def fetch(ctx):
    import os
    repo = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", ".."))
    tables = []
    for folder, slug, name, lat, lon, columns, zone in survey_calendars(repo):
        url = PAGE + slug
        try:
            page = ctx.http.text(url)
            conf = conf_data(page)
            rows = calendar_rows(conf, ctx.today.year)
        except (FetchError, ValueError) as e:
            ctx.error(f"{folder} {slug}: {e}")
            continue
        plat, plon = conf.get("latitude"), conf.get("longitude")
        note = ""
        if isinstance(plat, (int, float)) and isinstance(plon, (int, float)) and (abs(plat - lat) > 0.02 or abs(plon - lon) > 0.02):
            note = f"the page's point ({plat}, {plon}) is not the survey's"
            ctx.note(f"{slug}: {note}")
        t = Table(slug, name, lat, lon, zone or ZONES.get(folder, "UTC"), folder[:2].upper(), columns, survey=folder, school="-",
                  source_line=f"{name}: its {ctx.today.year} Mawaqit calendar, {url} (confData.calendar); the mosque's own point {lat}, {lon}",
                  note=note, raw=[(f"mawaqit-{slug}.html", page)])
        for date, times in rows.items():
            try:
                t.add(date, times)
            except FetchError as e:
                ctx.note(f"{slug} {date}: {e}")
        t.merge = False
        if t.rows:
            tables.append(t)
    return tables
