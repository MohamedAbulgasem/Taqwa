"""Tunisia, the Institut National de la Météorologie (meteo.tn): the coming seven days for the six
delegations tn-inm.tsv checks, one request a day each for the prayer times and one for INM's own
sunrise (its ephemeris endpoint). The Fajr printed is when the fast begins (F+E). Restricted:
archive only."""
import datetime as dt
import json

from common import FetchError, Table

SOURCE = "tn-inm"
HORAIRE = "https://www.meteo.tn/horaire_gouvernorat/{date}/{gouv}/{deleg}"
LEVER = "https://www.meteo.tn/lever_coucher_gouvernorat/{date}/{gouv}/{deleg}"

# unit id (tn-inm.tsv), name, gouvernorat id, delegation id (INM's own ids)
PLACES = [
    ("tunis", "Tunis", 342, 615), ("sfax", "Sfax", 359, 632), ("tabarka", "Tabarka", 354, 497),
    ("ben-guerdane", "Ben Guerdane", 353, 488), ("tataouine", "Tataouine", 351, 624), ("tala", "Tala", 362, 573),
]
DAYS = 7


def times_of(body):
    d = json.loads(body.decode("utf-8", "replace")).get("data")
    if not d:
        raise FetchError("no data")
    return [d["sobh"], d["dhohr"], d["aser"], d["magreb"], d["isha"]]


def sunrise_of(body):
    d = json.loads(body.decode("utf-8", "replace")).get("data")
    if not d or not d.get("lever"):
        raise FetchError("no lever")
    return d["lever"]


def fetch(ctx):
    tables = []
    for unit, name, gouv, deleg in PLACES:
        t = Table(unit, name, None, None, "Africa/Tunis", "TN", "F+E S D A M I", entry=f"tn.inm/{unit}",
                  source_line=f"Tunisia, Institut National de la Meteorologie: {HORAIRE.format(date='<date>', gouv=gouv, deleg=deleg)} (one request per day), "
                              f"sunrise from {LEVER.format(date='<date>', gouv=gouv, deleg=deleg)}")
        for i in range(DAYS):
            day = (ctx.today + dt.timedelta(days=i)).isoformat()
            try:
                body = ctx.http.get(HORAIRE.format(date=day, gouv=gouv, deleg=deleg))
                f, d, a, m, isha = times_of(body)
            except (FetchError, KeyError, ValueError) as e:
                ctx.error(f"{name} {day}: {e}")
                continue
            t.raw.append((f"horaire-{unit}-{day}.json", body))
            try:
                sun = ctx.http.get(LEVER.format(date=day, gouv=gouv, deleg=deleg))
                s = sunrise_of(sun)
                t.raw.append((f"lever-{unit}-{day}.json", sun))
            except (FetchError, KeyError, ValueError) as e:
                ctx.note(f"{name} {day}: no sunrise ({e})")
                s = "-"
            t.add(day, [f, s, d, a, m, isha])
        if t.rows:
            tables.append(t)
    return tables
