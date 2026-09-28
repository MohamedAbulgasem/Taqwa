"""Libya, the General Authority of Awqaf and Dar al-Ifta: awqaf.gov.ly's prayer widget (the page's
own AJAX call) for its 22 cities, today's row with its imsak, and api.ifta.ly's eight days for
Tripoli. Each run appends a day per city, so a full Tripoli year builds up (spec §6.3). In the west
the Fajr printed is also when the fast begins (F+E) and the widget's emsak is the printed precaution
(Im); east of 18.5° E or south of 29.5° N the local adhan leads Fajr and Maghrib (ruling R73), so
there Fajr, Maghrib and imsak are not checked, as ly-awqaf.tsv has it."""
import json

from common import FetchError, Table, nearest_year

SOURCE = "ly-awqaf"
WIDGET = "https://awqaf.gov.ly/sovinel/admin-ajax.php"
IFTA = "https://api.ifta.ly/api/v1/prayer-time"
WEST = "F+E S D A M I Im"
EAST = "- S D A - I -"

# widget name, unit id (ly-awqaf.tsv), name, columns
CITIES = [
    ("Tripoli", "tripoli", "Tripoli", WEST), ("Alkomos", "khoms", "Khoms", WEST), ("Alzaweya", "zawiya", "Zawiya", WEST),
    ("BanyWalead", "bani-walid", "Bani Walid", WEST), ("Ekdames", "ghadames", "Ghadames", WEST), ("Mosrata", "misrata", "Misrata", WEST),
    ("Sert", "sirte", "Sirte", WEST), ("Zaleaten", "zliten", "Zliten", WEST),
    ("Ejdabya", "ajdabiya", "Ajdabiya (east)", EAST), ("Breaka", "brega", "Brega (east)", EAST), ("beda", "bayda", "Bayda (east)", EAST),
    ("Almarag", "marj", "Marj (east)", EAST), ("Koufra", "kufra", "Kufra (south-east)", EAST), ("Emsaed", "emsaed", "Emsaed (east)", EAST),
    ("Ojala", "awjila", "Awjila (east)", EAST), ("Benghazi", "benghazi", "Benghazi (east)", EAST), ("Jalo", "jalu", "Jalu (east)", EAST),
    ("Derna", "derna", "Derna (east)", EAST), ("Raslanof", "ras-lanuf", "Ras Lanuf (east)", EAST), ("Sabha", "sabha", "Sabha (south)", EAST),
    ("Tobrek", "tobruk", "Tobruk (east)", EAST), ("Hoon", "hun", "Hun (south)", EAST),
]


def widget_row(body, today):
    j = json.loads(body.decode("utf-8", "replace"))
    data = j.get("data")
    if not j.get("success") or not data:
        raise FetchError("no data in the widget's response")
    x = data[0]
    date = nearest_year(int(x["month"]), int(x["day"]), today)
    imsak = x.get("emsak") or x.get("imsak") or "-"
    return date.isoformat(), [x["fajr"], x["shorouk"], x["dohr"], x["aser"], x["magreb"], x["esha"], imsak]


def ifta_rows(body, today):
    j = json.loads(body.decode("utf-8", "replace"))
    rows = {}
    for x in j.get("value") or []:
        date = nearest_year(int(x["month"]), int(x["day"]), today)
        rows[date.isoformat()] = [x["fajr"], x["sunrise"], x["dhuhr"], x["asr"], x["maghrib"], x["isha"], x.get("imsak") or "-"]
    if not rows:
        raise FetchError("no days in api.ifta.ly's response")
    return rows


def fetch(ctx):
    tables = []
    for widget, unit, name, columns in CITIES:
        t = Table(unit, name, None, None, "Africa/Tripoli", "LY", columns, entry=f"ly.awqaf/{unit}",
                  source_line=f"Libya, awqaf.gov.ly prayer widget AJAX ({WIDGET} action=get_prayer_times city={widget}), one day per fetch; seventh column the widget's emsak"
                  + ("; and api.ifta.ly's eight days" if unit == "tripoli" else ""))
        try:
            body = ctx.http.get(WIDGET, data={"action": "get_prayer_times", "city": widget})
            date, times = widget_row(body, ctx.today)
            t.add(date, times)
            t.raw.append((f"widget-{widget}-{date}.json", body))
        except (FetchError, KeyError, ValueError) as e:
            ctx.error(f"{name}: {e}")
        if unit == "tripoli":
            try:
                body = ctx.http.get(IFTA)
                for date, times in ifta_rows(body, ctx.today).items():
                    t.add(date, times)
                t.raw.append((f"ifta-{ctx.today.isoformat()}.json", body))
            except (FetchError, KeyError, ValueError) as e:
                ctx.error(f"api.ifta.ly: {e}")
        if t.rows:
            tables.append(t)
    return tables
