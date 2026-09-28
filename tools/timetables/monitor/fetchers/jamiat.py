"""South Africa, Jamiatul Ulama's perpetual salaah times: each town's perpetual CSV (the public
export linked from jamiat.org.za/salaah-times.php) mapped onto the current year and the next, with
its Suhoor column (E), both Asr columns and both Isha columns, as za-jamiat.tsv reads them (the
Shafi'i Isha not checked). A perpetual table, so a changed file is the news."""
import csv
import datetime as dt

from common import FetchError, Table, add_all

SOURCE = "za-jamiat"
EXPORT = "https://salaahtimes.starlite.za.net/jamiat/perpetual/export-csv.php?id="

# key, unit id (za-jamiat.tsv), name, the export's id
TOWNS = [
    ("jhb", "johannesburg", "Johannesburg", "fdead345d15555d292e52a041aef1c4e402369b8"),
    ("cpt", "cape-town", "Cape Town", "a8e12c9f28bf220e8243849cd2231fd9fb427566"),
    ("dbn", "durban", "Durban", "712755729da76a79096bdf78b8842bf22e349ecd"),
    ("plk", "pietersburg/polokwane", "Polokwane", "f7a1d15e6d3d2e4ed81da1fda613c7d575a92193"),
    ("spb", "springbok-ncape", "Springbok", "09dd6d665f44201302d49b3d4db48785c1eb9668"),
    ("pe", "port-elizabeth", "Port Elizabeth", "a0c80a4c16b36e8e04878464768e2e6b735056e0"),
    ("blm", "bloemfontein", "Bloemfontein", "13e8e7f3520e66b731971f4fb9a72b8601c0af3e"),
    ("upt", "upington", "Upington", "53bc000c016a76020a8d36f814d4b2a173355493"),
]
MON = {m: i + 1 for i, m in enumerate(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])}
COLUMNS = ["Suhoor Ends", "Fajr", "Sunrise Starts", "Zuhr Starts", "Asr (S)", "Asr (H)", "Maghrib", "Isha (S)", "Isha (H)"]


def parse(text):
    """The CSV: a title line, then a header; returns (title, [(month, day, [9 times])])."""
    lines = text.splitlines()
    if len(lines) < 3:
        raise FetchError("too short to be the perpetual CSV")
    title = lines[1].strip().strip('"')
    reader = list(csv.DictReader(lines[2:]))
    if not reader or any(c not in reader[0] for c in COLUMNS + ["Date"]):
        raise FetchError(f"unexpected columns ({list(reader[0].keys())[:6] if reader else 'none'})")
    out = []
    for r in reader:
        dd, mm = r["Date"].split()
        out.append((MON[mm], int(dd), [r[c] for c in COLUMNS]))
    return title, out


def fetch(ctx):
    tables = []
    for key, unit, name, export_id in TOWNS:
        url = EXPORT + export_id
        try:
            body = ctx.http.get(url)
            title, days = parse(body.decode("utf-8", "ignore"))
        except (FetchError, KeyError, ValueError) as e:
            ctx.error(f"{name}: {e}")
            continue
        for year in (ctx.today.year, ctx.today.year + 1):
            t = Table(f"{key}-{year}", f"{name} {year}", None, None, "Africa/Johannesburg", "ZA", "E F S D As Ah M - I",
                      entry=f"za.jamiat/{unit}", school="-",
                      source_line=f"Jamiatul Ulama perpetual salaah times, {title}; mapped onto {year} (29 Feb only in leap years): {url} "
                                  f"(linked from https://jamiat.org.za/salaah-times.php); columns Suhoor, Fajr, Sunrise, Zuhr, Asr (S), Asr (H), Maghrib, Isha (S), Isha (H)",
                      raw=[(f"jamiat-{key}.csv", body)] if year == ctx.today.year else [])
            rows = {}
            for month, day, times in days:
                try:
                    rows[dt.date(year, month, day).isoformat()] = times
                except ValueError:
                    continue
            t.merge = False
            if add_all(ctx, t, rows, f"{name} {year}"):
                tables.append(t)
    return tables
