"""Diyanet's namazvakitleri.diyanet.gov.tr: the district pages (the month's table and the year
ahead, about 396 days) for Türkiye's 84 unit districts, every il's centre among them (tr-diyanet.tsv), and for the
European cities its European method is proven on (tr-diyanet-europe.tsv), plus the seven cities
Europe's cautious entries lean on that are not held yet. Its first column is İmsak, both the Fajr
start and the end of eating (F+E). Restricted: archive only.

A European city's district id is resolved from the site's own dropdown endpoints by name
(GetRegList: the country's states, each state's districts) and cached in <monitor>/diyanet-ids.json,
so the lookups run once. Country ids are the site's own (its country dropdown, read 28 Sep 2026).

Diyanet re-issues days it already published: on 9 November 2026 its year table was the 2026 edition
again, whose takdir differs from the 2027 edition served until 31 October, and merging every capture
into one file mixed the two. Each capture is therefore kept whole as its own edition
(archive/tables/monitor/<source>/editions/<key>/<date>.txt, fetch.Store.keep_edition), and the live
file holds the latest capture alone (`editions` on each table) once KEEP_EDITIONS is on.

KEEP_EDITIONS is off until the Diyanet Europe curves (DiyanetEuropeCurveGenerator,
DiyanetEuropeCrossYearTest) read pinned copies, not the live files: with it on, the first run would
rewrite each live file as its latest capture alone and drop the 2027 days only an earlier edition holds,
which those still read. Switch it on, and run editions.py --in-place, after that lands (docs/MONITOR.md)."""
import html as H
import json
import os
import re

from common import FetchError, Table, add_all, write_json

SOURCE_TR = "tr-diyanet"
SOURCE_EU = "tr-diyanet-europe"
# Each capture kept as its own edition, the live file the latest alone (the docstring says why it waits).
KEEP_EDITIONS = False
BASE = "https://namazvakitleri.diyanet.gov.tr/tr-TR"
AJAX = {"X-Requested-With": "XMLHttpRequest"}
MON = {"Ocak": 1, "Şubat": 2, "Mart": 3, "Nisan": 4, "Mayıs": 5, "Haziran": 6, "Temmuz": 7,
       "Ağustos": 8, "Eylül": 9, "Ekim": 10, "Kasım": 11, "Aralık": 12}

# Türkiye: (IlceID, key, name, entry, lat, lon). A unit entry checks at the unit's own point; an
# edge district would name the entry alone and the app's city point (none is listed: the nine that were
# became units on 12 November 2026, ruling R140).
TURKEY = [
    ("9541", "istanbul", "İstanbul", "tr.diyanet/9541", None, None),
    ("9206", "ankara", "Ankara", "tr.diyanet/9206", None, None),
    ("9879", "tekirdag", "Tekirdağ", "tr.diyanet/9879", None, None),
    ("9419", "edirne", "Edirne", "tr.diyanet/9419", None, None),
    ("9930", "van", "Van", "tr.diyanet/9930", None, None),
    ("9146", "adana", "Adana", "tr.diyanet/9146", None, None),
    ("20089", "hatay", "Hatay", "tr.diyanet/20089", None, None),
    ("9587", "karaman", "Karaman", "tr.diyanet/9587", None, None),
    ("9352", "canakkale", "Çanakkale", "tr.diyanet/9352", None, None),
    ("9507", "hakkari", "Hakkâri", "tr.diyanet/9507", None, None),
    ("9594", "kars", "Kars", "tr.diyanet/9594", None, None),
    ("9522", "igdir", "Iğdır", "tr.diyanet/9522", None, None),
    ("9451", "erzurum", "Erzurum", "tr.diyanet/9451", None, None),
    ("9225", "antalya", "Antalya", "tr.diyanet/9225", None, None),
    ("9335", "bursa", "Bursa", "tr.diyanet/9335", None, None),
    ("9402", "diyarbakir", "Diyarbakır", "tr.diyanet/9402", None, None),
    ("9479", "gaziantep", "Gaziantep", "tr.diyanet/9479", None, None),
    ("9560", "izmir", "İzmir", "tr.diyanet/9560", None, None),
    ("9676", "konya", "Konya", "tr.diyanet/9676", None, None),
    ("9538", "catalca", "Çatalca", "tr.diyanet/9538", None, None),
    ("9547", "sile", "Şile", "tr.diyanet/9547", None, None),
    ("9548", "silivri", "Silivri", "tr.diyanet/9548", None, None),
    # The 62 other il centres, units since the round of 12–13 November 2026 (ruling R200), largest first: every il of
    # Türkiye is checked each week on its own table (84 pages, about two minutes more at the client's interval).
    ("9620", "kayseri", "Kayseri", "tr.diyanet/9620", None, None),
    ("9470", "eskisehir", "Eskişehir", "tr.diyanet/9470", None, None),
    ("9703", "malatya", "Malatya", "tr.diyanet/9703", None, None),
    ("9737", "mersin", "Mersin", "tr.diyanet/9737", None, None),
    ("9288", "batman", "Batman", "tr.diyanet/9288", None, None),
    ("9831", "sanliurfa", "Şanlıurfa", "tr.diyanet/9831", None, None),
    ("9432", "elazig", "Elazığ", "tr.diyanet/9432", None, None),
    ("9819", "samsun", "Samsun", "tr.diyanet/9819", None, None),
    ("9577", "kahramanmaras", "Kahramanmaraş", "tr.diyanet/9577", None, None),
    ("9919", "usak", "Uşak", "tr.diyanet/9919", None, None),
    ("9193", "aksaray", "Aksaray", "tr.diyanet/9193", None, None),
    ("9392", "denizli", "Denizli", "tr.diyanet/9392", None, None),
    ("9158", "adiyaman", "Adıyaman", "tr.diyanet/9158", None, None),
    ("9807", "sakarya", "Sakarya", "tr.diyanet/9807", None, None),
    ("9370", "corum", "Çorum", "tr.diyanet/9370", None, None),
    ("9868", "sivas", "Sivas", "tr.diyanet/9868", None, None),
    ("9167", "afyonkarahisar", "Afyonkarahisar", "tr.diyanet/9167", None, None),
    ("9905", "trabzon", "Trabzon", "tr.diyanet/9905", None, None),
    ("9716", "manisa", "Manisa", "tr.diyanet/9716", None, None),
    ("9270", "balikesir", "Balıkesir", "tr.diyanet/9270", None, None),
    ("9782", "ordu", "Ordu", "tr.diyanet/9782", None, None),
    ("9788", "osmaniye", "Osmaniye", "tr.diyanet/9788", None, None),
    ("9654", "kocaeli", "Kocaeli", "tr.diyanet/9654", None, None),
    ("9414", "duzce", "Düzce", "tr.diyanet/9414", None, None),
    ("9635", "kirikkale", "Kırıkkale", "tr.diyanet/9635", None, None),
    ("9689", "kutahya", "Kütahya", "tr.diyanet/9689", None, None),
    ("9315", "bolu", "Bolu", "tr.diyanet/9315", None, None),
    ("9528", "isparta", "Isparta", "tr.diyanet/9528", None, None),
    ("9252", "aydin", "Aydın", "tr.diyanet/9252", None, None),
    ("9440", "erzincan", "Erzincan", "tr.diyanet/9440", None, None),
    ("9646", "kirsehir", "Kırşehir", "tr.diyanet/9646", None, None),
    ("9726", "mardin", "Mardin", "tr.diyanet/9726", None, None),
    ("9887", "tokat", "Tokat", "tr.diyanet/9887", None, None),
    ("9303", "bingol", "Bingöl", "tr.diyanet/9303", None, None),
    ("9494", "giresun", "Giresun", "tr.diyanet/9494", None, None),
    ("9609", "kastamonu", "Kastamonu", "tr.diyanet/9609", None, None),
    ("9581", "karabuk", "Karabük", "tr.diyanet/9581", None, None),
    ("9185", "agri", "Ağrı", "tr.diyanet/9185", None, None),
    ("9799", "rize", "Rize", "tr.diyanet/9799", None, None),
    ("9198", "amasya", "Amasya", "tr.diyanet/9198", None, None),
    ("9839", "siirt", "Siirt", "tr.diyanet/9839", None, None),
    ("9629", "kilis", "Kilis", "tr.diyanet/9629", None, None),
    ("9955", "zonguldak", "Zonguldak", "tr.diyanet/9955", None, None),
    ("9327", "burdur", "Burdur", "tr.diyanet/9327", None, None),
    ("9747", "mugla", "Muğla", "tr.diyanet/9747", None, None),
    ("9766", "nigde", "Niğde", "tr.diyanet/9766", None, None),
    ("9359", "cankiri", "Çankırı", "tr.diyanet/9359", None, None),
    ("9949", "yozgat", "Yozgat", "tr.diyanet/9949", None, None),
    ("9755", "mus", "Muş", "tr.diyanet/9755", None, None),
    ("9285", "bartin", "Bartın", "tr.diyanet/9285", None, None),
    ("9760", "nevsehir", "Nevşehir", "tr.diyanet/9760", None, None),
    ("9297", "bilecik", "Bilecik", "tr.diyanet/9297", None, None),
    ("9935", "yalova", "Yalova", "tr.diyanet/9935", None, None),
    ("9854", "sirnak", "Şırnak", "tr.diyanet/9854", None, None),
    ("9638", "kirklareli", "Kırklareli", "tr.diyanet/9638", None, None),
    ("9311", "bitlis", "Bitlis", "tr.diyanet/9311", None, None),
    ("9295", "bayburt", "Bayburt", "tr.diyanet/9295", None, None),
    ("9501", "gumushane", "Gümüşhane", "tr.diyanet/9501", None, None),
    ("9914", "tunceli", "Tunceli", "tr.diyanet/9914", None, None),
    ("9847", "sinop", "Sinop", "tr.diyanet/9847", None, None),
    ("9246", "artvin", "Artvin", "tr.diyanet/9246", None, None),
    ("9238", "ardahan", "Ardahan", "tr.diyanet/9238", None, None),
]

# Europe: (key, name, country id, district name candidates, lat, lon, zone, cc, entry).
# The twelve cities of the first proof name their unit (checked at the unit's own point,
# DiyanetEuropeCurves, and as the member of the cautious entry Automatic follows there); the seven
# of the round of 29 September 2026 name the entry at their point, so the check resolves whatever
# unit the registry holds for them, and the cautious entry's member row beside it.
EUROPE = [
    ("sarajevo", "Sarajevo", "9", ["SARAJEVO", "SARAYBOSNA"], 43.84864, 18.35644, "Europe/Sarajevo", "BA", "tr.diyanet.europe/tr.diyanet.europe.sarajevo"),
    ("zurich", "Zürich", "49", ["ZÜRİH", "ZURIH", "ZURICH", "ZÜRICH"], 47.36667, 8.55, "Europe/Zurich", "CH", "tr.diyanet.europe/tr.diyanet.europe.zurich"),
    ("munich", "München", "13", ["MUNCHEN", "MÜNCHEN", "MUNICH", "MÜNIH"], 48.13743, 11.57549, "Europe/Berlin", "DE", "tr.diyanet.europe/tr.diyanet.europe.munich"),
    ("paris", "Paris", "21", ["PARIS"], 48.85341, 2.3488, "Europe/Paris", "FR", "tr.diyanet.europe/tr.diyanet.europe.paris"),
    ("berlin", "Berlin", "13", ["BERLIN"], 52.52437, 13.41053, "Europe/Berlin", "DE", "tr.diyanet.europe/tr.diyanet.europe.berlin"),
    ("stockholm", "Stockholm", "12", ["STOCKHOLM"], 59.32938, 18.06871, "Europe/Stockholm", "SE", "tr.diyanet.europe/tr.diyanet.europe.stockholm"),
    ("freiburg", "Freiburg", "13", ["FREIBURG", "FREIBURG IM BREISGAU", "FREIBURG I. BR.", "FREIBURG (BREISGAU)"], 47.9959, 7.85222, "Europe/Berlin", "DE", "tr.diyanet.europe/tr.diyanet.europe.freiburg"),
    ("wien", "Wien", "35", ["WIEN", "VIENNA", "VİYANA", "VIYANA"], 48.20849, 16.37208, "Europe/Vienna", "AT", "tr.diyanet.europe/tr.diyanet.europe.wien"),
    ("brussels", "Brussels", "11", ["BRUSSELS", "BRUXELLES", "BRUSSEL", "BRÜKSEL", "BRUKSEL"], 50.85045, 4.34878, "Europe/Brussels", "BE", "tr.diyanet.europe/tr.diyanet.europe.brussels"),
    ("london", "London", "15", ["LONDON", "LONDRA"], 51.5074, -0.1278, "Europe/London", "GB", "tr.diyanet.europe/tr.diyanet.europe.london"),
    ("amsterdam", "Amsterdam", "4", ["AMSTERDAM"], 52.37403, 4.88969, "Europe/Amsterdam", "NL", "tr.diyanet.europe/tr.diyanet.europe.amsterdam"),
    ("oslo", "Oslo", "36", ["OSLO"], 59.91273, 10.74609, "Europe/Oslo", "NO", "tr.diyanet.europe/tr.diyanet.europe.oslo"),
    ("antwerpen", "Antwerpen", "11", ["ANTWERPEN", "ANTWERP", "ANVERS"], 51.2194, 4.4025, "Europe/Brussels", "BE", "tr.diyanet.europe"),
    ("gent", "Gent", "11", ["GENT", "GHENT", "GAND"], 51.0543, 3.7174, "Europe/Brussels", "BE", "tr.diyanet.europe"),
    ("lyon", "Lyon", "21", ["LYON"], 45.764, 4.8357, "Europe/Paris", "FR", "tr.diyanet.europe"),
    ("lille", "Lille", "21", ["LILLE"], 50.6292, 3.0573, "Europe/Paris", "FR", "tr.diyanet.europe"),
    ("copenhagen", "Copenhagen", "26", ["COPENHAGEN", "KOPENHAG", "KØBENHAVN", "KOBENHAVN"], 55.6761, 12.5683, "Europe/Copenhagen", "DK", "tr.diyanet.europe"),
    ("helsinki", "Helsinki", "41", ["HELSINKI"], 60.1699, 24.9384, "Europe/Helsinki", "FI", "tr.diyanet.europe"),
    ("trondheim", "Trondheim", "36", ["TRONDHEIM"], 63.4305, 10.3951, "Europe/Oslo", "NO", "tr.diyanet.europe"),
]

FOLD = str.maketrans({"İ": "I", "Ü": "U", "Ö": "O", "Ş": "S", "Ç": "C", "Ğ": "G", "Â": "A", "Ø": "O", "Å": "A",
                      "Ä": "A", "É": "E", "È": "E", "Ê": "E", "Ë": "E", "Û": "U", "Ô": "O", "Ï": "I"})


def norm(s):
    return re.sub(r"[^A-Z0-9]", "", str(s).upper().translate(FOLD))


def parse_page(text):
    """Every dated row of a district page: {yyyy-mm-dd: [imsak, güneş, öğle, ikindi, akşam, yatsı]}."""
    out = {}
    for r in re.findall(r"<tr[^>]*>(.*?)</tr>", text, re.S):
        c = [H.unescape(re.sub("<[^>]+>", "", x)).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", r, re.S)]
        if len(c) < 8:
            continue
        m = re.match(r"(\d+) (\S+) (\d{4})", c[0])
        if m and m.group(2) in MON:
            out[f"{m.group(3)}-{MON[m.group(2)]:02d}-{int(m.group(1)):02d}"] = c[2:8]
    return out


def raw_keys(source, monitor_dir):
    """{raw file name: table key} of a source's captures, for rebuilding its editions from the raw
    responses (editions.py): Türkiye's ids are listed here, Europe's are the cached ones."""
    if source == SOURCE_TR:
        return {f"diyanet-{ilce}.html": key for ilce, key, *_ in TURKEY}
    path = os.path.join(monitor_dir, "diyanet-ids.json")
    with open(path, encoding="utf-8") as f:
        cities = json.load(f).get("cities") or {}
    return {f"diyanet-{ilce}.html": key for key, ilce in cities.items()}


def parse_raw(text):
    """The rows of one raw capture, as fetch() reads them (parse_page)."""
    return parse_page(text)


def district_page(ctx, ilce):
    text = ctx.http.text(f"{BASE}/{ilce}")
    rows = parse_page(text)
    if not rows:
        raise FetchError(f"no dated rows on the page for district {ilce}")
    return text, rows


class Ids:
    """The cached district ids of the European cities (<monitor>/diyanet-ids.json). A cache that does
    not read (a kill mid-write, a stray edit) is discarded, said once in the source's message, and
    rebuilt from the site's own dropdowns; it is written atomically (review M7)."""

    def __init__(self, ctx):
        self.ctx = ctx
        self.path = os.path.join(ctx.monitor_dir, "diyanet-ids.json")
        self.data = {}
        if os.path.exists(self.path):
            try:
                with open(self.path, encoding="utf-8") as f:
                    loaded = json.load(f)
                if not isinstance(loaded, dict):
                    raise ValueError("not an object")
                self.data = loaded
            except (ValueError, OSError) as e:
                ctx.note(f"diyanet-ids.json could not be read ({type(e).__name__}): the district ids are resolved again from the site")
                self.data = {}

    def save(self):
        write_json(self.path, self.data)

    def districts(self, country):
        """{normalised name: (id, state name)} over every state of a country, fetched once."""
        cached = self.data.setdefault("countries", {}).get(country)
        if cached:
            return cached
        d = self.ctx.http.json(f"{BASE}/home/GetRegList?ChangeType=country&CountryId={country}&Culture=tr-TR", headers=AJAX)
        states = d.get("StateList") or []
        found = {}

        def take(regions, state_name):
            for r in regions or []:
                for name in (r.get("IlceAdiEn"), r.get("IlceAdi")):
                    if name:
                        found.setdefault(norm(name), (str(r["IlceID"]), state_name))

        # The country call's own district list is one state's, not necessarily the first listed
        # (Germany's came without Baden-Württemberg): every state is asked for its own.
        take(d.get("StateRegionList"), "")
        for s in states:
            e = self.ctx.http.json(f"{BASE}/home/GetRegList?ChangeType=state&CountryId={country}&StateId={s['SehirID']}&Culture=tr-TR", headers=AJAX)
            take(e.get("StateRegionList"), s.get("SehirAdiEn"))
        if not found:
            raise FetchError(f"country {country}: no districts listed")
        self.data["countries"][country] = found
        self.save()
        return found

    def resolve(self, key, country, candidates):
        known = self.data.setdefault("cities", {}).get(key)
        if known:
            return known
        districts = self.districts(country)
        hit = None
        for c in candidates:
            hit = districts.get(norm(c))
            if hit:
                break
        if hit is None:
            # The site's own spelling may carry a suffix ("FREIBURG IM BREISGAU" as "FREIBURG I.BR."):
            # the one district whose folded name starts with a candidate, the shortest when several.
            for c in candidates:
                starts = sorted((name for name in districts if name.startswith(norm(c))), key=len)
                if starts:
                    hit = districts[starts[0]]
                    c = starts[0]
                    break
        if hit is None:
            sample = ", ".join(sorted(districts)[:12])
            raise FetchError(f"{key}: no district named {'/'.join(candidates)} in country {country} (some: {sample} …)")
        self.data["cities"][key] = hit[0]
        self.save()
        self.ctx.note(f"{key}: district {hit[0]} ({c}, {hit[1]})")
        return hit[0]


def fetch(ctx):
    tables = []
    if ctx.source == SOURCE_TR:
        for ilce, key, name, entry, lat, lon in TURKEY:
            try:
                text, rows = district_page(ctx, ilce)
            except FetchError as e:
                ctx.error(f"{name}: {e}")
                continue
            t = Table(key, name, lat, lon, "Europe/Istanbul", "TR", "F+E S D A M I", entry=entry,
                      source_line=f"Diyanet {BASE}/{ilce} (namazvakitleri.diyanet.gov.tr, IlceID {ilce}; the month and the year tables)",
                      raw=[(f"diyanet-{ilce}.html", text)])
            t.editions = KEEP_EDITIONS
            if add_all(ctx, t, rows, name):
                tables.append(t)
        return tables
    ids = Ids(ctx)
    for key, name, country, candidates, lat, lon, zone, cc, entry in EUROPE:
        try:
            ilce = ids.resolve(key, country, candidates)
            text, rows = district_page(ctx, ilce)
        except FetchError as e:
            ctx.error(f"{name}: {e}")
            continue
        t = Table(key, name, lat, lon, zone, cc, "F+E S D A M I", entry=entry,
                  source_line=f"Diyanet {BASE}/{ilce} (namazvakitleri.diyanet.gov.tr, {name}, IlceID {ilce}; the month and the year tables)",
                  raw=[(f"diyanet-{ilce}.html", text)])
        t.editions = KEEP_EDITIONS
        if add_all(ctx, t, rows, name):
            tables.append(t)
    return tables
