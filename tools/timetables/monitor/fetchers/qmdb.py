"""Kazakhstan, QMDB (muftyat.kz): the year API its own site calls, api.muftyat.kz/prayer-times/<year>/<lat>/<lon>,
the current year and the next, at 87 of QMDB's own places (the API answers only at its own places'
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
  read at a place's own latitude (same round);
- every other city of the app's own list, at QMDB's place for it (its "<name> қаласы" city entry where it lists
  one, else the locality of that name): 58 more, the city-points round of 9 Oct 2026, so that each of the app's
  Kazakh cities is a unit at QMDB's own point (ruling R44, CentralAsia.kt) and is checked there and at the app's
  own point. 87 places in all, 174 requests a run. Not five whose coordinates another place of QMDB's list shares
  (Shchuchinsk, Zhitikara, Esik, Abay in the Karaganda region and Abay in the Turkistan region, each listed twice):
  there the year API answers HTTP 500 (9 Oct 2026), so QMDB publishes no table to check.

The fast begins at the printed Fajr (the API's "imsak", after its Fajr, is not used), so the Fajr column
is also the end of eating (F+E).

The API sometimes answers a place two ways (engine survey, 11-12 Nov 2026): on 9 Nov it answered
Zhezkazgan (47.80N) with the minutes of the band at and above 48N from 5 Mar to 31 Dec, sunrise 2 min
earlier and Dhuhr, Asr and Maghrib 2 min later on 302 days, and 19 of 20 sequential requests on 11 Nov
gave the right year again. So a reply is taken only when it keeps QMDB's own band rule on every day
(Maghrib is sunset + 3 min below 48N, + 5 at or above, which every capture from 3 Oct to 9 Nov 2026
keeps, 127,385 days, but that one) and either says what the held copy says or a second reply says the
same. Otherwise the place is asked again, sequentially, up to REQUESTS times; when no reply can be taken
the held copy stays and the run says "QMDB answered two ways" as a fetch error, never as a new table.

A change that breaks the band rule (a new Maghrib offset, a moved 48N line) is never taken here, however
often it is said: it would freeze every held copy and give a year with none no table. Once METHOD_PLACES
places in one run have each answered the same way every time against the rule, the rest are asked once
each, not REQUESTS times (87 places and two years would otherwise cost some 520 requests and half an
hour of pauses from the run's budget), and the error names what to change after a look by hand:
BAND_LAT and band_minutes below (docs/MONITOR.md, "QMDB's band rule")."""
import collections
import json
import time

from common import FetchError, Table, add_all, norm_time

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
    # The app's other Kazakh cities, at QMDB's own place for each, north to south (9 Oct 2026).
    ("akkol", "Akkol", "53.296079", "69.597040", "Asia/Almaty"),
    ("rudny", "Rudny", "52.966667", "63.116667", "Asia/Qostanay"),
    ("makinsk", "Makinsk", "52.632810", "70.417677", "Asia/Almaty"),
    ("lisakovsk", "Lisakovsk", "52.544079", "62.492641", "Asia/Qostanay"),
    ("stepnogorsk", "Stepnogorsk", "52.346944", "71.881667", "Asia/Almaty"),
    ("aksu-pavlodar", "Aksu (Pavlodar)", "52.037890", "76.920582", "Asia/Almaty"),
    ("atbasar", "Atbasar", "51.815761", "68.358335", "Asia/Almaty"),
    ("ekibastuz", "Ekibastuz", "51.729778", "75.326583", "Asia/Almaty"),
    ("shemonaikha", "Shemonaikha", "50.627624", "81.916697", "Asia/Almaty"),
    ("semey", "Semey", "50.404976", "80.249235", "Asia/Almaty"),
    ("ridder", "Ridder", "50.338860", "83.506329", "Asia/Almaty"),
    ("temirtau", "Temirtau", "50.058756", "72.953424", "Asia/Almaty"),
    ("karaganda", "Karaganda", "49.806406", "73.085485", "Asia/Almaty"),
    ("saran", "Saran", "49.801993", "72.828387", "Asia/Almaty"),
    ("altay", "Altay", "49.725218", "84.273562", "Asia/Almaty"),
    ("shakhtinsk", "Shakhtinsk", "49.705868", "72.594637", "Asia/Almaty"),
    ("kandyagash", "Kandyagash", "49.474444", "57.423333", "Asia/Aqtobe"),
    ("kalbatau", "Kalbatau", "49.328084", "81.573693", "Asia/Almaty"),
    ("embi", "Embi", "48.823344", "58.148397", "Asia/Aqtobe"),
    ("satpayev", "Satpayev", "47.907455", "67.528112", "Asia/Almaty"),
    ("zhezkazgan", "Zhezkazgan", "47.799711", "67.714090", "Asia/Almaty"),
    ("zaysan", "Zaysan", "47.453008", "84.969846", "Asia/Almaty"),
    ("kulsary", "Kulsary", "46.983333", "54.016667", "Asia/Atyrau"),
    ("balkhash", "Balkhash", "46.843721", "74.977301", "Asia/Almaty"),
    ("aral", "Aral", "46.797738", "61.660792", "Asia/Qyzylorda"),
    ("makanshy", "Makanshy", "46.781629", "82.023857", "Asia/Almaty"),
    ("baikonur", "Baikonur", "45.966111", "63.307778", "Asia/Qostanay"),
    ("aiteke-bi", "Aiteke Bi (Novokazalinsk)", "45.835934", "62.148317", "Asia/Qyzylorda"),
    ("zhosaly", "Zhosaly", "45.488495", "64.086675", "Asia/Qyzylorda"),
    ("sarkand", "Sarkand", "45.413666", "79.916894", "Asia/Almaty"),
    ("ushtobe", "Ushtobe", "45.251221", "77.980827", "Asia/Almaty"),
    ("taldykorgan", "Taldykorgan", "45.017837", "78.382123", "Asia/Almaty"),
    ("tekeli", "Tekeli", "44.863094", "78.764266", "Asia/Almaty"),
    ("zharkent", "Zharkent", "44.169365", "80.003842", "Asia/Almaty"),
    ("shiyeli", "Shiyeli", "44.167573", "66.736893", "Asia/Qyzylorda"),
    ("zhanakorgan", "Zhanakorgan", "43.900451", "67.243723", "Asia/Qyzylorda"),
    ("konaev", "Konaev", "43.883333", "77.083333", "Asia/Almaty"),
    ("mangystau", "Mangystau", "43.691561", "51.305571", "Asia/Aqtau"),
    ("shu", "Shu", "43.611782", "73.760237", "Asia/Almaty"),
    ("shelek", "Shelek", "43.597525", "78.250618", "Asia/Almaty"),
    ("zhanatas", "Zhanatas", "43.554650", "69.722525", "Asia/Almaty"),
    ("kentau", "Kentau", "43.518131", "68.504652", "Asia/Almaty"),
    ("otegen-batyr", "Otegen Batyr", "43.423782", "77.028020", "Asia/Almaty"),
    ("boralday", "Boralday (Burunday)", "43.358094", "76.861006", "Asia/Almaty"),
    ("zhanaozen", "Zhanaozen", "43.343266", "52.865792", "Asia/Aqtau"),
    ("talgar", "Talgar", "43.302813", "77.239690", "Asia/Almaty"),
    ("turkistan", "Turkistan", "43.302025", "68.268979", "Asia/Almaty"),
    ("karatau", "Karatau", "43.166667", "70.466667", "Asia/Almaty"),
    ("sarykemer", "Sarykemer", "43.007610", "71.515131", "Asia/Almaty"),
    ("taraz", "Taraz", "42.883333", "71.366667", "Asia/Almaty"),
    ("merke", "Merke", "42.872481", "73.190139", "Asia/Almaty"),
    ("turar-ryskulov", "Turar Ryskulov", "42.534675", "70.350564", "Asia/Almaty"),
    ("arys", "Arys", "42.432744", "68.813798", "Asia/Almaty"),
    ("aksu-turkistan", "Aksu (Turkistan)", "42.420712", "69.828661", "Asia/Almaty"),
    ("lenger", "Lenger", "42.182051", "69.882120", "Asia/Almaty"),
    ("saryagash", "Saryagash", "41.466667", "69.166667", "Asia/Almaty"),
    ("shardara", "Shardara", "41.254722", "67.969167", "Asia/Almaty"),
    ("zhetysay", "Zhetysay", "40.775278", "68.327222", "Asia/Almaty"),
]

FIELDS = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

# QMDB's band rule: Maghrib is sunset + 3 min below 48N, + 5 at or above (CentralAsia.kt, Regions.kt kazakhNorth).
BAND_LAT = 48.0
REQUESTS = 3  # at most, for one place and year, when a reply cannot be taken
METHOD_PLACES = 3  # places in one run answering the same way against the band rule before the rest are asked once
KNOB = "if it has, change BAND_LAT/band_minutes in tools/timetables/monitor/fetchers/qmdb.py (docs/MONITOR.md)"
RETRY_PAUSE = 5.0  # seconds before asking again (on top of the client's own spacing); requests stay sequential


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


def _minutes(t):
    h, m = str(t).split(":")[:2]
    return int(h) * 60 + int(m)


def band_minutes(lat):
    return 5 if float(lat) >= BAND_LAT else 3


def off_band(body, lat):
    """The dates of a reply whose Maghrib is not sunset + QMDB's band minutes for `lat` (days without
    a sunset or a Maghrib are not judged)."""
    want = band_minutes(lat)
    out = []
    for x in json.loads(body.decode("utf-8", "replace")).get("result") or []:
        try:
            if _minutes(x["maghrib"]) - _minutes(x["sunset"]) != want:
                out.append(str(x["Date"])[:10])
        except (KeyError, TypeError, ValueError):
            continue
    return sorted(out)


def normalised(rows):
    """Rows as a held table file has them ('HH:MM'); a cell that is not a time stays as it is."""
    out = {}
    for d, cells in rows.items():
        line = []
        for c in cells:
            try:
                line.append(norm_time(c))
            except FetchError:
                line.append(c)
        out[d] = line
    return out


def differ(rows, held):
    """The dates both hold whose cells differ."""
    return sorted(d for d in rows if d in held and rows[d] != held[d])


def shifts(rows, held, dates):
    """'sunrise -2, dhuhr +2' : each column's most common move over `dates`, where it moved."""
    parts = []
    for i, name in enumerate(FIELDS):
        moves = collections.Counter()
        for d in dates:
            try:
                moves[_minutes(rows[d][i]) - _minutes(held[d][i])] += 1
            except (IndexError, ValueError):
                continue
        moves.pop(0, None)
        if moves:
            move, n = moves.most_common(1)[0]
            parts.append(f"{name} {move:+d} on {n} days")
    return ", ".join(parts)


def span(dates):
    return f"{len(dates)} day{'s' if len(dates) != 1 else ''} {dates[0]}..{dates[-1]}" if dates else "no days"


def ask(ctx, url, lat, held, label, state=None):
    """The reply to take for `url`: (body, rows) or None, with why when None. A reply is taken when it
    keeps the band rule and says what the held copy says on every day both hold, or when a second
    reply that keeps the band rule says exactly the same. Raises FetchError when the API answers no year.
    `state` is the run's: {"against": [labels answered the same way every time against the band rule]};
    from METHOD_PLACES of them on, a place is asked once."""
    state = {"against": []} if state is None else state
    against = state.setdefault("against", [])
    tries = 1 if len(against) >= METHOD_PLACES else REQUESTS
    replies = []  # (body, rows, off-band dates)
    for attempt in range(tries):
        if attempt:
            time.sleep(RETRY_PAUSE)
        body = ctx.http.get(url)
        rows = normalised(parse(body))
        off = off_band(body, lat)
        if not off:
            moved = differ(rows, held) if held else []
            if not moved:
                if attempt:
                    ctx.note(f"{label}: reply {attempt + 1} taken, as the held copy has it")
                return body, rows
            if any(not o and r == rows for _, r, o in replies):
                ctx.note(f"{label}: reply {attempt + 1} taken, two replies agree on a new table ({span(moved)} moved from the held copy: {shifts(rows, held, moved)})")
                return body, rows
            reason = f"{span(moved)} differ from the held copy ({shifts(rows, held, moved)})"
        else:
            reason = f"Maghrib is not sunset + {band_minutes(lat)} min (QMDB's band rule) on {span(off)}"
            if held:
                moved = differ(rows, held)
                if moved:
                    reason += f"; against the held copy {shifts(rows, held, moved)}"
        replies.append((body, rows, off))
        ctx.log(f"  {label}: reply {attempt + 1} not taken yet: {reason}" + ("; asking again" if attempt + 1 < tries else ""))
    off = replies[-1][2]
    if tries == 1:
        if off:
            return None, (f"QMDB answered against its own band rule ({reason}), asked once since {len(against)} places this run "
                          f"({', '.join(against[:METHOD_PLACES])}) answered the same way every time; it may have changed its method: "
                          f"look by hand, and {KNOB}")
        return None, f"QMDB's reply differs from the held copy ({reason}), asked once since {len(against)} places this run answered against its band rule"
    agreeing = len(replies) > 1 and all(r == replies[0][1] for _, r, _ in replies)
    if not agreeing:
        return None, f"QMDB answered two ways in {len(replies)} requests ({reason})"
    if off:
        against.append(label)
        if len(against) == METHOD_PLACES:
            ctx.note(f"{METHOD_PLACES} places ({', '.join(against)}) answered the same way every time against QMDB's band rule: "
                     f"the rest of this run asks each place once")
        return None, (f"QMDB answered the same {len(replies)} times against its own band rule ({reason}); it may have changed its "
                      f"method: look by hand, and {KNOB}")
    return None, f"QMDB answered two ways in {len(replies)} requests ({reason})"


def fetch(ctx):
    tables = []
    state = {"against": []}
    for year in (ctx.today.year, ctx.today.year + 1):
        for key, name, lat, lon, zone in POINTS:
            url = f"{BASE}/{year}/{lat}/{lon}"
            label = f"{name} {year}"
            held = ctx.held(f"{key}-{year}")
            try:
                got = ask(ctx, url, lat, held, label, state)
            except FetchError as e:
                ctx.error(f"{label}: {e}")
                continue
            t = Table(f"{key}-{year}", label, float(lat), float(lon), zone, "KZ", "F+E S D A M I", entry=ENTRY,
                      school="hanafi", source_line=f"QMDB (muftyat.kz) {url} (the year API the official site calls)")
            if got[0] is None:
                if held:
                    ctx.error(f"{label}: {got[1]}; the held copy is kept")
                    add_all(ctx, t, held, label)
                else:
                    ctx.error(f"{label}: {got[1]}; nothing is held, so no table")
            else:
                body, rows = got
                t.raw = [(f"{key}-{year}.json", body)]
                add_all(ctx, t, rows, label)
            t.merge = False
            if t.rows:
                tables.append(t)
    return tables
