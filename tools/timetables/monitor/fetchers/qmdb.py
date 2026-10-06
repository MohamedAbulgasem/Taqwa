"""Kazakhstan, QMDB (muftyat.kz): the year API its own site calls, api.muftyat.kz/prayer-times/<year>/<lat>/<lon>,
the current year and the next, at 29 of QMDB's own places (the API answers only at its own places'
coordinates, listed by api.muftyat.kz/cities/, 5,694 of them, every one on UTC+5):

- the gate's first two cities, Almaty (below 48N) and Astana (at or above it), and four north of Astana
  (Kokshetau, Kostanay, Pavlodar, Petropavl): what the end of eating's margin at and above 48N was first
  fitted on (end-of-eating fix round, 3 Oct 2026), since QMDB's AngleBased residual grows to the north
  and for an end of eating a deeper residual is a late end;
- north of Petropavl to the country's edge, QMDB's three northernmost places (Isakovka, 55.4N, the
  northernmost of its list; Krasny Yar; Kulomzino), and the west and the zones the first six left without
  a table (Oral and Aksay, Asia/Oral; Aktobe, Khromtau and Shalqar, Asia/Aqtobe; Atyrau, Makat and
  Oteshqali Atambayev, Asia/Atyrau; Aktau, Asia/Aqtau; Kyzylorda, Asia/Qyzylorda), the north-west round of
  6 Oct 2026;
- the south of Almaty, where the AngleBased rule never binds: Shymkent and Zhenis (40.6N, the southernmost
  place of QMDB's list), the same round;
- from 46 to 48N, where the AngleBased rule binds around the summer solstice but QMDB's offsets are still
  +-3: Oteshqali Atambayev (the place nearest 48N) and Ayagoz, which decide the end of eating there, with
  Shalqar, Atyrau, Makat and Mamyrsu;
- places just either side of the engine's 0.1 deg curve steps (Vagulino, Bugrovoe, Pulemetovka, Spasovka,
  Khromtau, Arkalyk, Oskemen, Mamyrsu, Makat, and Kulomzino above), which proved the curves must also be
  read at a place's own latitude (same round).

The fast begins at the printed Fajr (the API's "imsak", after its Fajr, is not used), so the Fajr column
is also the end of eating (F+E)."""
import json

from common import FetchError, Table, add_all

SOURCE = "kz-qmdb"
BASE = "https://api.muftyat.kz/prayer-times"
ENTRY = "kz.qmdb"

# key, name, lat, lon, zone: QMDB's own places, as its city list gives them (kz-qmdb.tsv reads each one).
POINTS = [
    # The first round's six (3 Oct 2026).
    ("almaty", "Almaty", "43.238293", "76.945465", "Asia/Almaty"),
    ("astana", "Astana", "51.133333", "71.433333", "Asia/Almaty"),
    ("kokshetau", "Kokshetau", "53.291667", "69.391667", "Asia/Almaty"),
    ("kostanay", "Kostanay", "53.219333", "63.634194", "Asia/Qostanay"),
    ("pavlodar", "Pavlodar", "52.315556", "76.956389", "Asia/Almaty"),
    ("petropavl", "Petropavl", "54.862222", "69.140833", "Asia/Almaty"),
    # North of Petropavl to the edge (6 Oct 2026).
    ("isakovka", "Isakovka", "55.405802", "68.935933", "Asia/Almaty"),
    ("krasny-yar", "Krasny Yar", "55.369280", "69.367089", "Asia/Almaty"),
    ("kulomzino", "Kulomzino", "55.250541", "70.473193", "Asia/Almaty"),
    # The west and the zones without a table (6 Oct 2026).
    ("oral", "Oral", "51.204019", "51.370537", "Asia/Oral"),
    ("aksay", "Aksay", "51.164945", "53.020868", "Asia/Oral"),
    ("aktobe", "Aktobe", "50.300377", "57.154555", "Asia/Aqtobe"),
    ("shalqar", "Shalqar", "47.831392", "59.619290", "Asia/Aqtobe"),
    ("atyrau", "Atyrau", "47.116667", "51.883333", "Asia/Atyrau"),
    ("aktau", "Aktau", "43.635379", "51.169135", "Asia/Aqtau"),
    ("kyzylorda", "Kyzylorda", "44.842544", "65.502563", "Asia/Qyzylorda"),
    # The south, to QMDB's southernmost place (6 Oct 2026).
    ("shymkent", "Shymkent", "42.368009", "69.612769", "Asia/Almaty"),
    ("zhenis", "Zhenis", "40.599543", "68.504760", "Asia/Almaty"),
    # 46-48N (6 Oct 2026).
    ("oteshqali", "Oteshqali Atambayev", "47.994187", "51.622514", "Asia/Atyrau"),
    ("ayagoz", "Ayagoz", "47.966667", "80.433333", "Asia/Almaty"),
    # Either side of a 0.1 deg curve step (6 Oct 2026).
    ("vagulino", "Vagulino", "55.151518", "69.262462", "Asia/Almaty"),
    ("bugrovoe", "Bugrovoe", "55.049091", "69.724025", "Asia/Almaty"),
    ("pulemetovka", "Pulemetovka", "54.551120", "70.421316", "Asia/Almaty"),
    ("spasovka", "Spasovka", "54.349933", "67.721896", "Asia/Almaty"),
    ("khromtau", "Khromtau", "50.250278", "58.434722", "Asia/Aqtobe"),
    ("arkalyk", "Arkalyk", "50.248611", "66.911389", "Asia/Qostanay"),
    ("oskemen", "Oskemen", "49.948325", "82.627848", "Asia/Almaty"),
    ("mamyrsu", "Mamyrsu", "47.951147", "80.382042", "Asia/Almaty"),
    ("makat", "Makat", "47.647520", "53.349220", "Asia/Atyrau"),
]

FIELDS = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")


def parse(body):
    """The API's year: {"result": [{"Date": yyyy-mm-dd, "fajr": HH:MM, ...}, ...]}."""
    d = json.loads(body.decode("utf-8", "replace"))
    days = d.get("result") if isinstance(d, dict) else None
    if not isinstance(days, list) or not days:
        raise FetchError("no days in the response")
    rows = {}
    for x in days:
        try:
            rows[str(x["Date"])[:10]] = [x[k] for k in FIELDS]
        except (KeyError, TypeError) as e:
            raise FetchError(f"a day without {e}") from e
    return rows


def fetch(ctx):
    tables = []
    for year in (ctx.today.year, ctx.today.year + 1):
        for key, name, lat, lon, zone in POINTS:
            url = f"{BASE}/{year}/{lat}/{lon}"
            try:
                body = ctx.http.get(url)
                rows = parse(body)
            except FetchError as e:
                ctx.error(f"{name} {year}: {e}")
                continue
            t = Table(f"{key}-{year}", f"{name} {year}", float(lat), float(lon), zone, "KZ", "F+E S D A M I", entry=ENTRY,
                      school="hanafi", source_line=f"QMDB (muftyat.kz) {url} (the year API the official site calls)",
                      raw=[(f"{key}-{year}.json", body)])
            add_all(ctx, t, rows, f"{name} {year}")
            t.merge = False
            if t.rows:
                tables.append(t)
    return tables
