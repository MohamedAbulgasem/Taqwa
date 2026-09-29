"""The prayer-time pages of taqwa.world: a page per city and language, an index per language, and
the section at the bottom of every home page.

They are rendered from the one document tools/timetables writes (_data/timetables.json) with the
app's own prayer-time engine. Every time, date, number and name about a city in that document is
already written the way the app writes it for a reader there, in the page's language and that
country's digits; this module only places them in the site's sentences (each language's
meta.json, under "timetable") and markup, and formats nothing about a city itself.

A page is complete without script: the whole of this month and next are in the markup, today's
row is lit for the day the page was built, and the Today card shows that date. assets/timetable.js
then makes it live in the city's own time zone (see that file).

Every sentence on a city page is one of two things: an app string the generator filled for the
city in the page's language (`pages[lang].strings`, so the page says exactly what the app's About
screen says), or a site sentence from meta.json. A site sentence may carry a "|" where its opening
words are set in bold (the card's foot, the app section's points): the mark is the site's own and
is never shown.
"""
import datetime
import html
import importlib.resources
import json
import math
import os
import re
import unicodedata
import zoneinfo

from stores import block as store_block

SECTION = "prayer-times/"
CHECKS_PAGE_SLUG = "how-taqwa-checks"  # reserved for the "How Taqwa checks" page; no city may use it
HOME_MARKER = "<!-- prayer-times -->"
OBLIGATORY = [0, 2, 3, 4, 5]  # the prayers of the countdown, as in the app: Sunrise is never "next"
DHUHR = 2
FAJR, SUNRISE, ASR, MAGHRIB, ISHA = 0, 1, 3, 4, 5
NEARBY_KM = 900
AT_KAABA_KM = 5
MAX_SAME_COUNTRY = 14
MAX_NEARBY = 6
SUPPORT_EMAIL = "support@taqwa.world"

MIHRAB = ('<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M4.56 12.6V8.13c0-2.32 1.38-3.94 3.44-4.75 '
          '2.06.81 3.44 2.43 3.44 4.75v4.47" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" '
          'stroke-linejoin="round"/><circle cx="8" cy="6.3" r="0.95" fill="currentColor"/></svg>')
# The same glyph with the app icon's amber dot: the card's foot and the app section's tile.
MIHRAB_LIT = MIHRAB.replace('r="0.95" fill="currentColor"', 'r="0.95" fill="var(--ring)"')
CHEVRON = ('<svg class="chev" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.8" '
           'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4.5 2.5 8 6l-3.5 3.5"/></svg>')
CHECK = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" '
         'stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5 10 17.5 19 7"/></svg>')
SEARCH = ('<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" '
          'stroke-width="1.8"/><path d="M16 16l4.5 4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>')
RING_R = 88
RING_C = 2 * math.pi * RING_R
RULER_W = 342


class DataError(Exception):
    pass


def _zone(name: str) -> zoneinfo.ZoneInfo:
    """The zone from the tzdata package where it is installed (the Pages workflow installs the
    newest), else from the system's zone files."""
    try:
        ref = importlib.resources.files("tzdata").joinpath("zoneinfo", *name.split("/"))
        with ref.open("rb") as f:
            return zoneinfo.ZoneInfo.from_file(f, key=name)
    except (ModuleNotFoundError, FileNotFoundError):
        return zoneinfo.ZoneInfo(name)


def clock_text(hour: int, minute: int, digit_set: str) -> str:
    """The app's clock, as the generator's Formats.clock writes it: the hour unpadded, the minute
    padded, in the page's digits."""
    return digits(hour, digit_set) + ":" + digits(minute, digit_set).rjust(2, digit_set[0])


def offset_text(seconds: int, digit_set: str) -> str:
    """"UTC+2", "UTC+5:30", "UTC−4" or "UTC", as the generator's Formats.utcOffset writes it."""
    if seconds == 0:
        return "UTC"
    sign = "+" if seconds > 0 else "−"
    hours, minutes = divmod(abs(seconds) // 60, 60)
    tail = "" if minutes == 0 else ":" + digits(minutes, digit_set).rjust(2, digit_set[0])
    return f"UTC{sign}{digits(hours, digit_set)}{tail}"


def minutes_of(text: str, digit_set: str) -> int:
    hour, minute = text.translate({ord(d): str(i) for i, d in enumerate(digit_set)}).split(":")
    return int(hour) * 60 + int(minute)


def correct_clocks(city: dict) -> bool:
    """Rewrites a city's clock times, offsets and clock-change notes from the newest tzdata where
    the generator's differ, and says whether it had to.

    The instants are the app's engine's and do not depend on any time-zone data; only reading them
    on the local clock does, and the generator reads them with the JDK's copy of the database,
    which is only as new as the JDK. Morocco left UTC+1 for good on 20 September 2026 and Alberta
    stopped changing its clocks after 2026 (tzdata 2026c) before any JDK knew, so a page built on
    the JDK alone would print their times an hour out. Phones get new zone data within weeks; the
    page follows the newest there is. A difference that is not a different clock (the same minute
    written differently) would be a bug in one of the two formatters, and stops the build."""
    zone = _zone(city["timeZone"])
    utc = datetime.timezone.utc
    local = [[datetime.datetime.fromtimestamp(e, tz=utc).astimezone(zone) for e in day["epochs"]] for day in city["days"]]
    wrong = False
    for lang, page in city["pages"].items():
        ds = page["digits"]
        for i, day in enumerate(page["days"]):
            for p, (theirs, moment) in enumerate(zip(day["times"], local[i])):
                ours = clock_text(moment.hour, moment.minute, ds)
                if ours == theirs:
                    continue
                if minutes_of(theirs, ds) == moment.hour * 60 + moment.minute:
                    raise DataError(f"{city['slug']} {lang}: the generator wrote {theirs!r} where the site writes "
                                    f"{ours!r} for the same minute; the two clock formats have drifted apart")
                wrong = True
    if not wrong:
        return False

    days = city["days"]
    for i, day in enumerate(days):
        day["offset"] = int(local[i][DHUHR].utcoffset().total_seconds())
    first = days[0]["date"].split("-")
    midnight = datetime.datetime(int(first[0]), int(first[1]), int(first[2]), tzinfo=zone)
    before = [int(midnight.utcoffset().total_seconds())] + [d["offset"] for d in days[:-1]]
    today = [d["date"] for d in days].index(city["today"])
    for page in city["pages"].values():
        ds = page["digits"]
        for i, day in enumerate(page["days"]):
            day["times"] = [clock_text(m.hour, m.minute, ds) for m in local[i]]
        page["offset"] = offset_text(days[today]["offset"], ds)
        page["clockChanges"] = [
            {"index": i, "date": page["days"][i]["date"], "offset": offset_text(days[i]["offset"], ds)}
            for i in range(len(days)) if days[i]["offset"] != before[i]
        ]
    return True


def load(path: str) -> dict:
    if not os.path.exists(path):
        raise SystemExit(
            f"No prayer timetables at {os.path.relpath(path)}. They are written by the app's own engine:\n"
            "    ./gradlew -p tools/timetables generate\n"
            "(JDK 21; the Pages workflow does this on every build)."
        )
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def esc(text: str) -> str:
    return html.escape(str(text), quote=False)


def attr(text: str) -> str:
    return html.escape(str(text), quote=True)


def fill(sentence: str, **values) -> str:
    """One of the site's sentences with the city's values put in, escaped for HTML."""
    out = esc(sentence)
    for key, value in values.items():
        out = out.replace("{" + key + "}", esc(value))
    if re.search(r"\{[a-z_]+\}", out):
        raise DataError(f"unfilled placeholder in: {sentence}")
    return out


def plain(sentence: str, **values) -> str:
    """The same, unescaped, for titles and descriptions that the page writer escapes itself."""
    for key, value in values.items():
        sentence = sentence.replace("{" + key + "}", str(value))
    return sentence


def lead(sentence: str, **values) -> str:
    """A site sentence whose opening words are set in bold: the part before its "|" is the lead,
    the rest follows it in the running weight. A sentence without the mark is filled as it is."""
    head, mark, rest = sentence.partition("|")
    if not mark:
        return fill(sentence, **values)
    return f"<b>{fill(head, **values)}</b>{fill(rest, **values)}"


def list_comma(lang: str) -> str:
    """The comma between the members of a list, as the app joins them (MethodWords.listComma)."""
    return "، " if lang in ("ar", "ur") else ", "


def ruler_svg(shares: dict, rtl: bool, words: dict, digit_set: str) -> str:
    """B's minute ruler (spec §3.3.1): the hatched "before" box holding the one true count of
    early starts, 0, and four bars for the checked days whose Fajr fell on the authority's own
    minute, one, two, or three or more minutes after it — drawn and labelled as shares of that
    event's checked count, so the only day total on the page is the tile's. On an RTL page the
    layout is mirrored: every x is measured from the right, and the text, which the SVG lays out
    in the page's direction, is never flipped."""
    order = ["0", "1", "2", "3+"]
    values = [float(shares.get(key, 0.0)) for key in order]
    top = max(values) or 1.0
    percents = [fill(words["percent"], n=digits(round(value * 100), digit_set)) for value in values]

    def x(left: float, width: float = 0) -> str:
        at = RULER_W - left - width if rtl else left
        return f"{at:g}"

    alt = fill(words["alt"], p0=percents[0], p1=percents[1], p2=percents[2], p3=percents[3])
    parts = [f'<svg viewBox="0 0 {RULER_W} 100" role="img" aria-label="{alt}">',
             '<defs><pattern id="ruler-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">'
             '<line x1="0" y1="0" x2="0" y2="6" stroke="var(--hair)" stroke-width="2"/></pattern></defs>',
             f'<rect x="{x(0, 66)}" y="18" width="66" height="54" fill="url(#ruler-hatch)"/>',
             f'<text x="{x(33)}" y="48" font-size="11" font-weight="800" fill="var(--t1)" text-anchor="middle">{esc(words["none"])}</text>',
             f'<text x="{x(33)}" y="90" font-size="11" fill="var(--t2)" text-anchor="middle">{esc(words["before"])}</text>']
    labels = [words["same"], words["one"], words["two"], words["three"]]
    for i, value in enumerate(values):
        left = 76 + 60 * i
        height = 48 * value / top
        parts.append(f'<rect x="{x(left, 40)}" y="{72 - height:.1f}" width="40" height="{height:.1f}" rx="2" fill="var(--accent)"/>')
        parts.append(f'<text x="{x(left + 20)}" y="{72 - height - 4.5:.1f}" font-size="10.5" font-weight="700" fill="var(--t1)" '
                     f'text-anchor="middle">{esc(percents[i])}</text>')
        parts.append(f'<text x="{x(left + 20)}" y="90" font-size="11" fill="var(--t2)" text-anchor="middle">{esc(labels[i])}</text>')
    parts.append(f'<line x1="{x(0)}" y1="72" x2="{x(RULER_W)}" y2="72" stroke="var(--t3)" stroke-width="1"/>')
    for tick in (126, 186, 246, 306):
        parts.append(f'<line x1="{x(tick)}" y1="72" x2="{x(tick)}" y2="77" stroke="var(--t3)" stroke-width="1"/>')
    parts.append(f'<line x1="{x(66)}" y1="12" x2="{x(66)}" y2="77" stroke="var(--accent)" stroke-width="2"/>')
    # Anchored at its start: on an RTL page the start is the right end, so the label still hangs
    # off the amber line towards the bars.
    parts.append(f'<text x="{x(71)}" y="10" font-size="10.5" font-weight="700" fill="var(--t1)" text-anchor="start">{esc(words["minute"])}</text>')
    parts.append("</svg>")
    return "".join(parts)


def at_kaaba(city: dict) -> bool:
    """Whether the city is Makkah itself, where the Qibla is the Kaaba in front of you."""
    return city["qibla"]["km"] < AT_KAABA_KM


def heading(title: str) -> str:
    """A CLDR month title as a heading: French and others write "septembre 2026" in lower case."""
    return title[:1].upper() + title[1:]


def digits(number: int, digit_set: str) -> str:
    return "".join(digit_set[int(c)] for c in str(number))


def fold(text: str) -> str:
    """What the index filter compares: lowercase, without accents and Arabic diacritics. The page
    script folds what the reader types the same way."""
    text = unicodedata.normalize("NFKD", text.lower())
    return re.sub("[\u0300-\u036f\u064b-\u065f\u0670]", "", text).replace("\u2019", "'")


def km_between(a: dict, b: dict) -> float:
    rad = math.radians
    dlat, dlon = rad(b["latitude"] - a["latitude"]), rad(b["longitude"] - a["longitude"])
    h = math.sin(dlat / 2) ** 2 + math.cos(rad(a["latitude"])) * math.cos(rad(b["latitude"])) * math.sin(dlon / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(h))


def ring(fraction: float) -> str:
    return (f'<svg class="ring" viewBox="0 0 200 200" aria-hidden="true">'
            f'<circle cx="100" cy="100" r="{RING_R}" fill="none" stroke="var(--track)" stroke-width="9"/>'
            f'<circle class="ring-arc" cx="100" cy="100" r="{RING_R}" fill="none" stroke="var(--ring)" stroke-width="9" '
            f'stroke-linecap="round" stroke-dasharray="{fraction * RING_C:.1f} {RING_C:.1f}" transform="rotate(-90 100 100)"/></svg>')


def dial(bearing: float) -> str:
    """The app's mini Qibla dial: north up, the needle on the bearing, the Kaaba at its tip."""
    return ('<svg class="dial" viewBox="0 0 48 48" aria-hidden="true">'
            '<circle cx="24" cy="24" r="21.5" fill="var(--surface)" stroke="var(--hair)"/>'
            '<circle cx="24" cy="24" r="17" fill="none" stroke="var(--t3)" stroke-width="1" stroke-dasharray="1 3.45"/>'
            '<text x="24" y="12.2" text-anchor="middle" font-size="6.5" font-weight="800" fill="var(--t2)" font-family="Manrope, sans-serif">N</text>'
            f'<g transform="rotate({bearing:.1f} 24 24)"><line x1="24" y1="24" x2="24" y2="11" stroke="var(--t1)" stroke-width="1.7" stroke-linecap="round"/>'
            '<rect x="21.3" y="6.2" width="5.4" height="5.4" rx="1" fill="var(--t1)"/><rect x="21.3" y="7.6" width="5.4" height="1.3" fill="var(--ring)"/></g>'
            '<circle cx="24" cy="24" r="2" fill="var(--ring)"/></svg>')


class Timetables:
    def __init__(self, langs: dict, doc: dict):
        self.langs = langs
        self.doc = doc
        self.cities = doc["cities"]
        self.by_slug = {c["slug"]: c for c in self.cities}
        self.arabic = doc["prayersArabic"]
        self.page_count = 0
        for lang, cfg in langs.items():
            if "timetable" not in cfg:
                raise DataError(f"site/pages/{lang}/meta.json has no \"timetable\" sentences")
        for city in self.cities:
            for lang in city["languages"]:
                if lang not in langs:
                    raise DataError(f"{city['slug']} has a {lang} page but the site has no {lang}")
        self.corrected = [c["slug"] for c in self.cities if correct_clocks(c)]
        for slug in self.corrected:
            # A GitHub Actions notice, so the run summary says which pages the JDK was behind on.
            print(f"::notice::{slug}: clock times taken from the newest tzdata; the JDK's zone data is older")

    # ---------------------------------------------------------------- addresses

    def index_dir(self, lang: str) -> str:
        return self.langs[lang]["prefix"] + SECTION

    def city_dir(self, lang: str, slug: str) -> str:
        return self.index_dir(lang) + slug + "/"

    def cities_in(self, lang: str) -> list:
        return [c for c in self.cities if lang in c["pages"]]

    def picker_to_city(self, city: dict) -> dict:
        """Where each language's name leads from a city page: the twin, or that language's index."""
        return {lang: self.city_dir(lang, city["slug"]) if lang in city["pages"] else self.index_dir(lang)
                for lang in self.langs}

    # ---------------------------------------------------------------- building

    def build(self, template: str, write_page) -> None:
        if not self.cities:
            return  # every row of site/cities.tsv is held: no index either, the section is off
        for lang in self.langs:
            self.build_index(lang, template, write_page)
        for city in self.cities:
            twins = {lang: self.city_dir(lang, city["slug"]) for lang in city["languages"]}
            for lang in city["languages"]:
                self.build_city(city, lang, template, write_page, twins)

    def build_index(self, lang: str, template: str, write_page) -> None:
        t = self.langs[lang]["timetable"]
        count = digits(len(self.cities_in(lang)), t["digits"])
        twins = {other: self.index_dir(other) for other in self.langs}
        meta = {
            "title": plain(t["index_title"], count=count),
            "og_title": plain(t["index_og_title"], count=count),
            "description": plain(t["index_description"], count=count),
        }
        write_page(self.langs, lang, template, out_dir=twins[lang], meta=meta, body=self.index_body(lang),
                   twins=twins, picker=twins, current="prayer_times")
        self.page_count += 1

    def build_city(self, city: dict, lang: str, template: str, write_page, twins: dict) -> None:
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        month = page["months"][0]["title"]
        values = self.sentence_values(city, lang)
        described = "description_cautious" if city["entryClass"] == "C" else "description_checked"
        description = plain(t[described], **values, month=month)
        if not at_kaaba(city):
            description += plain(t["description_qibla"], **values)
        meta = {
            "title": plain(t["title"], **values, month=month, brand=cfg["brand"]),
            "og_title": plain(t["og_title"], **values),
            "description": description,
        }
        write_page(self.langs, lang, template, out_dir=twins[lang], meta=meta, body=self.city_body(city, lang),
                   twins=twins, picker=self.picker_to_city(city), current="prayer_times",
                   head=self.breadcrumbs(city, lang))
        self.page_count += 1

    def sentence_values(self, city: dict, lang: str) -> dict:
        page = city["pages"][lang]
        names = page["prayers"]
        return dict(
            city=page["city"], country=page["country"], method=page["method"], madhab=page["madhab"],
            qibla=page["qiblaDetail"], bearing=page["bearing"], distance=page["distance"], offset=page["offset"],
            fajr=names[FAJR], sunrise=names[SUNRISE], dhuhr=names[DHUHR], asr=names[ASR],
            maghrib=names[MAGHRIB], isha=names[ISHA],
        )

    def breadcrumbs(self, city: dict, lang: str) -> str:
        """The same trail the page shows: the index, the country on it, the city."""
        t = self.langs[lang]["timetable"]
        page = city["pages"][lang]
        index = "https://taqwa.world/" + self.index_dir(lang)
        data = {
            "@context": "https://schema.org",
            "@type": "BreadcrumbList",
            "itemListElement": [
                {"@type": "ListItem", "position": 1, "name": t["nav"], "item": index},
                {"@type": "ListItem", "position": 2, "name": page["country"], "item": f'{index}#{city["country"].lower()}'},
                {"@type": "ListItem", "position": 3, "name": page["city"]},
            ],
        }
        text = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
        return f'  <script type="application/ld+json">{text}</script>\n'

    # ---------------------------------------------------------------- the city page

    def city_body(self, city: dict, lang: str) -> str:
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        values = self.sentence_values(city, lang)
        rtl = cfg["dir"] == "rtl"
        dates = [d["date"] for d in city["days"]]
        today = dates.index(city["today"])
        sep = "‹" if rtl else "›"

        crumbs = (f'<nav class="crumbs" aria-label="{attr(t["crumbs"])}"><a href="../">{esc(t["nav"])}</a>'
                  f'<span aria-hidden="true">{sep}</span><a href="../#{city["country"].lower()}">{esc(page["country"])}</a>'
                  f'<span aria-hidden="true">{sep}</span><span aria-current="page">{esc(page["city"])}</span></nav>')
        # The pitch shows on desktop beside the card; on phones the app section carries it (CSS).
        pitch = (f'<div class="pitch"><p><b>{esc(t["pitch_title"])}</b> <span>{esc(t["pitch_body"])}</span></p>'
                 f'{store_block(cfg, lang, beta_anchor=False)}</div>')
        hero = f'''<div class="city-hero">
    <div class="city-copy">
      {crumbs}
      <h1>{fill(t["h1"], **values)}</h1>
      <p class="city-meta"><span>{esc(page["country"])}</span> · <span data-tt="full">{esc(page["today"]["full"])}</span> · <span data-tt="hijri">{esc(page["today"]["hijri"])}</span></p>
    </div>
    {self.today_card(city, lang, today)}
    {self.qibla_line(city, lang)}
    {pitch}
  </div>'''

        months = []
        first = 0
        for m, (facts_m, month) in enumerate(zip(city["months"], page["months"])):
            other = page["months"][1 - m] if len(page["months"]) == 2 else None
            months.append(self.month_section(city, lang, m, first, facts_m["days"], today, other))
            first += facts_m["days"]

        data = self.live_data(city, lang, today)
        return f'''<main class="wrap city">
  {hero}
  {self.whence(city, lang)}
  <section class="months">
  {"".join(months)}
  </section>
  <div class="app-row">
    {self.app_section(city, lang)}
    {self.after(city, lang)}
  </div>
</main>
<script type="application/json" id="tt-data">{data}</script>
<script src="{{root}}assets/timetable.js?v={{script_version}}" defer></script>
'''

    def today_card(self, city: dict, lang: str, today: int) -> str:
        """The app's Prayer screen in a card: the ring, the six times with sunrise, the pill after a
        prayer the high-latitude rule set, the polar line, and the foot that says so. Without
        script it is a calendar leaf for the day the page was built and that day's times; the
        script turns the ring into the countdown and follows the reader's day."""
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        words = page["strings"]
        day = page["days"][today]
        facts = city["days"][today]
        arabic_script = cfg["dir"] == "rtl"
        items = []
        for p, name in enumerate(page["prayers"]):
            pair = "" if arabic_script else f'<em lang="ar">{esc(self.arabic[p])}</em>'
            tags = ""
            if p == DHUHR:
                tags = f'<span class="tag" data-tt="jumuah"{"" if facts["friday"] else " hidden"}>{esc(page["jumuah"])}</span> '
            tags += f'<span class="tag" data-rule="{p}"{"" if p in facts["setByRule"] else " hidden"}>{esc(words["setByRule"])}</span>'
            sun = ' class="sun"' if p == SUNRISE else ""
            # The spaces are for screen readers: layout ignores them between the row's flex items.
            items.append(f'<li data-p="{p}"{sun}><i></i><b>{esc(name)}</b> {pair} {tags} <span class="t">{esc(day["times"][p])}</span></li>')
        month_title = heading(page["months"][0]["title"])
        return f'''<section class="today" aria-label="{attr(t["today"])}">
      <div class="today-head">
        <div class="ring-box">{ring(0)}</div>
        <div class="ring-text"><span class="ring-label" data-tt="label">{esc(page["today"]["weekday"])}</span><span class="ring-count" data-tt="count">{esc(day["day"])}</span><span class="ring-at" data-tt="at">{esc(month_title)}</span></div>
      </div>
      <ol class="tl">{"".join(items)}</ol>
      <p class="polar" data-tt="polar"{"" if facts["polar"] else " hidden"}>{esc(words["polarLine"])}</p>
      <div class="today-foot">{MIHRAB_LIT}<span class="foot-text">{lead(t["card_foot"])}</span><a class="pill quiet small" href="#app">{esc(t["cta_button"])}</a></div>
      <p class="tt-stale" hidden>{esc(t["stale"])} <a href="#app">{esc(t["cta_button"])}</a></p>
    </section>'''

    def qibla_line(self, city: dict, lang: str) -> str:
        """The dial and one line: the bearing and the distance, or in Makkah itself, where a bearing
        and "0 km to Makkah" mean nothing, that the Kaaba is there."""
        t = self.langs[lang]["timetable"]
        page = city["pages"][lang]
        values = self.sentence_values(city, lang)
        if at_kaaba(city):
            return f'<p class="qibla-line"><span><b>{esc(page["qibla"])}</b> · {esc(t["qibla_here"])}</span></p>'
        return (f'<p class="qibla-line">{dial(city["qibla"]["bearing"])}<span><b>{esc(page["qibla"])} {fill(t["qibla_bearing"], **values)}</b>'
                f' · {fill(t["qibla_distance"], **values)}</span></p>')

    def whence(self, city: dict, lang: str) -> str:
        """"Where these times come from" (spec §3.3, R97): a folded explainer whose summary — the
        authority line and the stamp's proof sentence — is always visible. Open, a checked place
        shows the three steps of the app's About screen with the proof tiles and the minute
        ruler; a cautious place shows its first step here (the rest is the cautious pages' work)."""
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        words = page["strings"]
        rtl = cfg["dir"] == "rtl"
        comma = list_comma(lang)
        cautious = city["entryClass"] == "C"
        authority = page["method"]
        if cautious:
            line = (f'<b>{esc(words["whoseTitle"])}</b> · {esc(comma.join(page["members"]))}{esc(comma)}{esc(t["combined"])}'
                    f' · {esc(t["not_affiliated_any"])}')
            steps = [f'<li><h3>{esc(words["whoPublishes"])}</h3><p>{esc(words["cautiousBody"])}</p><p>{esc(t["not_affiliated_any"])}</p></li>']
        else:
            line = f'<b>{esc(words["whoseTitle"])}</b> · {esc(t["reproduced"])} · {esc(words["notAffiliated"])}'
            ruler_words = {
                "percent": t["ruler_percent"], "alt": plain(t["ruler_alt"], authority=authority),
                "none": t["ruler_none"], "before": t["ruler_before"], "same": t["ruler_same"],
                "one": t["ruler_one"], "two": t["ruler_two"], "three": t["ruler_three"],
                "minute": plain(t["ruler_minute"], authority=authority),
            }
            tiles = (f'<div class="tiles">'
                     f'<div class="tile"><b>{esc(words["statDaysValue"])}</b><span>{esc(words["statDays"])}</span></div>'
                     f'<div class="tile zero"><b>{digits(0, t["digits"])}</b><span>{esc(words["statNever"])}</span></div>'
                     f'<div class="tile"><b>{esc(words["statMinutes"])}</b><span>{esc(words["statAtMost"])}</span></div></div>')
            ruler = (f'<figure class="ruler"><figcaption>{fill(t["ruler_caption"], authority=authority, fajr=page["prayers"][FAJR])}</figcaption>'
                     f'{ruler_svg(city["proof"]["fajrShares"], rtl, ruler_words, t["digits"])}</figure>')
            steps = [
                f'<li><h3>{esc(words["whoPublishes"])}</h3><p>{esc(words["whoPublishesBody"])} {esc(words["notAffiliated"])}</p></li>',
                f'<li><h3>{esc(words["howReproduces"])}</h3><p>{esc(t["no_copy"])}</p><p>{esc(words["methodIntro"])}</p></li>',
                f'<li><h3>{esc(words["howChecked"])}</h3><p>{fill(t["replays"], authority=authority)}</p>{tiles}{ruler}</li>',
            ]
        return f'''<details class="whence" id="about">
    <summary>
      <span class="whence-line">{line}</span>
      <span class="whence-proof">{esc(words["checkedThrough"])}</span>
      <span class="whence-label">{esc(t["whence"])} {CHEVRON}</span>
    </summary>
    <div class="whence-body"><ol class="steps">{"".join(steps)}</ol></div>
  </details>'''

    def app_section(self, city: dict, lang: str) -> str:
        """The app, once (spec §3.5, R98): the pitch, four checked points, the stores block the home
        hero uses — the official badges once a store is live, the coming-soon line until then —
        and the platforms line."""
        cfg = self.langs[lang]
        t = cfg["timetable"]
        values = self.sentence_values(city, lang)
        points = "".join(f'<li>{CHECK}<span>{lead(t[key])}</span></li>'
                         for key in ("app_point_adhan", "app_point_widgets", "app_point_qibla", "app_point_free"))
        return f'''<section class="app" id="app" aria-labelledby="app-h">
      <div class="icon-tile">{MIHRAB_LIT}</div>
      <h2 id="app-h">{esc(t["app_title"])}</h2>
      <p>{fill(t["app_body"], **values)}</p>
      <ul class="points">{points}</ul>
      {store_block(cfg, lang, beta_anchor=False)}
      <p class="platforms">{esc(t["app_platforms"])}</p>
    </section>'''

    def month_section(self, city: dict, lang: str, m: int, first: int, count: int, today: int, other) -> str:
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        month = page["months"][m]
        facts = city["months"][m]
        anchor = f'm-{facts["year"]}-{facts["month"]:02d}'
        other_anchor = None
        if other is not None:
            o = city["months"][1 - m]
            other_anchor = f'm-{o["year"]}-{o["month"]:02d}'
        arabic_script = cfg["dir"] == "rtl"
        heads = [f'<th scope="col" class="d">{esc(t["date"])}</th>', f'<th scope="col" class="h">{esc(t["hijri"])}</th>']
        heads += [f'<th scope="col">{esc(name)}</th>' for name in page["prayers"]]
        rows = []
        any_high = False
        any_ramadan = False
        for i in range(first, first + count):
            facts_d = city["days"][i]
            day = page["days"][i]
            classes = []
            if i < today:
                classes.append("past")
            if i == today:
                classes.append("is-today")
            if facts_d["friday"]:
                classes.append("fri")
            any_high = any_high or facts_d["highLatitude"]
            any_ramadan = any_ramadan or facts_d["ramadanIsha"]
            cells = [f'<th scope="row" class="d"><b>{esc(day["day"])}</b> <span>{esc(day["weekday"])}</span></th>',
                     f'<td class="h">{esc(day["hijri"])}</td>']
            for p, time in enumerate(day["times"]):
                if p == DHUHR and facts_d["friday"]:
                    cells.append(f'<td class="jm">{esc(time)} <span class="tag">{esc(page["jumuah"])}</span></td>')
                else:
                    cells.append(f"<td>{esc(time)}</td>")
            cls = f' class="{" ".join(classes)}"' if classes else ""
            current = ' aria-current="date"' if i == today else ""
            rows.append(f'<tr data-i="{i}"{cls}{current}>{"".join(cells)}</tr>')

        notes = []
        for change in page["clockChanges"]:
            if first <= change["index"] < first + count:
                notes.append(f'<p class="note">{fill(t["clock_change"], date=change["date"], offset=change["offset"])}</p>')
        if any_high and page["highLatitude"]:
            notes.append(f'<p class="note">{fill(t["high_latitude"], rule=page["highLatitude"][0], fajr=page["prayers"][FAJR], isha=page["prayers"][ISHA])}</p>')
        if any_ramadan:
            notes.append(f'<p class="note">{fill(t["ramadan_isha"], **self.sentence_values(city, lang))}</p>')
        jump = f'<a class="pill quiet" href="#{other_anchor}">{esc(heading(other["title"]))}</a>' if other_anchor else ""
        caption = fill(t["h1"], **self.sentence_values(city, lang)) + " · " + esc(month["title"])
        return f'''
  <section class="month" id="{anchor}" aria-labelledby="{anchor}-h">
    <div class="month-head"><div><h2 id="{anchor}-h">{esc(heading(month["title"]))}</h2><p>{esc(month["hijri"])}</p></div>{jump}</div>
    <div class="tt-wrap"><table class="tt" data-month="{m}">
      <caption class="sr">{caption}</caption>
      <thead><tr>{"".join(heads)}</tr></thead>
      <tbody>{"".join(rows)}</tbody>
    </table></div>
    {"".join(notes)}
  </section>'''

    def after(self, city: dict, lang: str) -> str:
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        values = self.sentence_values(city, lang)
        same = [c for c in self.cities_in(lang) if c["country"] == city["country"] and c["slug"] != city["slug"]]
        near = sorted(
            (c for c in self.cities_in(lang) if c["country"] != city["country"]),
            key=lambda c: km_between(city, c),
        )
        near = [c for c in near if km_between(city, c) <= NEARBY_KM][:MAX_NEARBY]

        def chips(cities):
            return "".join(f'<a href="../{c["slug"]}/">{esc(c["pages"][lang]["city"])}</a>' for c in cities)

        groups = []
        if same:
            groups.append(f'<div><h2>{fill(t["other_cities"], **values)}</h2><div class="chips">{chips(same[:MAX_SAME_COUNTRY])}</div></div>')
        if near:
            groups.append(f'<div><h2>{fill(t["nearby"], **values)}</h2><div class="chips">{chips(near)}</div></div>')
        groups.append(f'<div><h2>{esc(t["more_times"])}</h2><div class="chips"><a class="all" href="../">{esc(t["all_cities"])}</a></div></div>')
        groups.append(f'<div><h2>{esc(t["questions"])}</h2><div class="chips"><a href="{{support}}">{esc(cfg["nav_support"])}</a>'
                      f'<a href="mailto:{SUPPORT_EMAIL}">{SUPPORT_EMAIL}</a></div></div>')
        return f'''<section class="after">{"".join(groups)}</section>'''

    def live_data(self, city: dict, lang: str, today: int) -> str:
        """What assets/timetable.js needs and cannot read from the markup: the zone, the instants,
        and the day's long forms for when the reader's day is not the one the page was built on."""
        cfg = self.langs[lang]
        page = city["pages"][lang]
        days = []
        for facts, day in zip(city["days"], page["days"]):
            days.append({"d": facts["date"], "e": facts["epochs"], "f": 1 if facts["friday"] else 0,
                         "full": day["full"], "hijri": day["hijriLong"], "t": day["times"],
                         "r": facts["setByRule"], "p": 1 if facts["polar"] else 0})
        data = {
            "tz": city["timeZone"],
            # The countdown's digits, which are the page's except where the app falls back to
            # Western ones for the ring (Egyptian and Saudi Arabic, Bengali: CountdownDigits).
            "digits": page["countdownDigits"],
            "rtl": cfg["dir"] == "rtl",
            "next": page["nextIn"],
            "today": today,
            "first": city["months"][0]["days"],
            "earlier": cfg["timetable"]["earlier"],
            "days": days,
        }
        return json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")

    # ---------------------------------------------------------------- the index and the home section

    def index_body(self, lang: str) -> str:
        cfg = self.langs[lang]
        t = cfg["timetable"]
        cities = self.cities_in(lang)
        count = digits(len(cities), t["digits"])
        regions = []
        for region in self.doc["regions"]:
            in_region = [c for c in cities if c["region"] == region]
            if not in_region:
                continue
            countries = []
            for code in dict.fromkeys(c["country"] for c in in_region):
                here = [c for c in in_region if c["country"] == code]
                links = "".join(
                    f'<li><a href="{c["slug"]}/" data-k="{attr(self.keys(c, lang))}">{esc(c["pages"][lang]["city"])}</a></li>' for c in here
                )
                countries.append(f'<div class="country" id="{code.lower()}"><h3>{esc(here[0]["pages"][lang]["country"])}</h3><ul class="cities">{links}</ul></div>')
            regions.append(f'<section class="region" id="r-{region}"><h2 class="label">{esc(t["regions"][region])}</h2>'
                           f'<div class="countries">{"".join(countries)}</div></section>')
        return f'''<main class="wrap index">
  <h1>{esc(t["index_h1"])}</h1>
  <p class="lede">{fill(t["index_lede"], count=count)}</p>
  <label class="search" hidden>{SEARCH}<input id="city-filter" type="search" placeholder="{attr(t["search"])}" aria-label="{attr(t["search"])}" autocomplete="off" spellcheck="false"></label>
  {"".join(regions)}
  <p class="no-match" hidden>{esc(t["no_match"])}</p>
</main>
<script src="{{root}}assets/timetable.js?v={{script_version}}" defer></script>
'''

    def keys(self, city: dict, lang: str) -> str:
        """Every name the index filter should find a city by: in this language and in English,
        with its country, and the words of its address."""
        page, english = city["pages"][lang], city["pages"]["en"]
        words = [page["city"], english["city"], page["country"], english["country"], city["slug"].replace("-", " ")]
        return " ".join(dict.fromkeys(fold(w) for w in words))

    def home_section(self, lang: str) -> str:
        if not self.cities:
            return ""
        t = self.langs[lang]["timetable"]
        featured = [c for c in self.cities_in(lang) if lang in c["featured"]]
        count = digits(len(self.cities_in(lang)), t["digits"])
        chips = "".join(f'<a href="{{prayer_times}}{c["slug"]}/">{esc(c["pages"][lang]["city"])}</a>' for c in featured)
        return f'''<section class="section" id="prayer-times">
    <div class="wrap cities-strip">
      <div>
        <p class="label">{esc(t["nav"])}</p>
        <h2>{esc(t["home_title"])}</h2>
        <p class="lede">{fill(t["home_lede"], count=count)}</p>
      </div>
      <div class="chips big">{chips}<a class="all" href="{{prayer_times}}">{esc(t["all_cities"])}</a></div>
    </div>
  </section>
  '''


def check_page(rel_path: str, doc: str, data: Timetables) -> list:
    """What --check asks of a prayer-time page beyond links and markup: a city page has both
    months, each with a row for every day of that month and six times in each, the live data
    the script needs, its folded explainer and its app section; an index lists every city that
    has a page in its language."""
    problems = []
    parts = rel_path.split("/")
    if SECTION.rstrip("/") not in parts:
        return problems
    at = parts.index(SECTION.rstrip("/"))
    lang = parts[0] if at == 1 else "en"
    rest = parts[at + 1:]
    if rest == ["index.html"]:
        for city in data.cities_in(lang):
            if f'href="{city["slug"]}/"' not in doc:
                problems.append(f"timetable index: {rel_path} does not list {city['slug']}")
        return problems
    slug = rest[0]
    city = data.by_slug.get(slug)
    if city is None:
        return [f"timetable: {rel_path} is not a city in the document"]
    tables = re.findall(r'<table class="tt" data-month="(\d)">(.*?)</table>', doc, re.S)
    if len(tables) != 2:
        problems.append(f"timetable: {rel_path} has {len(tables)} month tables, not 2")
    for (m, table), facts in zip(tables, city["months"]):
        rows = re.findall(r"<tr data-i=\"\d+\"[^>]*>(.*?)</tr>", table, re.S)
        if len(rows) != facts["days"]:
            problems.append(f"timetable: {rel_path} month {m} has {len(rows)} rows for {facts['days']} days")
        for row in rows:
            if len(re.findall(r"<td(?: class=\"jm\")?>", row)) != 6:
                problems.append(f"timetable: {rel_path} month {m} has a row without six times")
                break
    match = re.search(r'<script type="application/json" id="tt-data">(.*?)</script>', doc, re.S)
    if not match:
        problems.append(f"timetable: {rel_path} has no live data")
    else:
        live = json.loads(match.group(1))
        if len(live["days"]) != sum(m["days"] for m in city["months"]):
            problems.append(f"timetable: {rel_path} live data covers {len(live['days'])} days")
    for piece, name in (('<details class="whence"', "explainer"), ('<section class="app"', "app section")):
        if doc.count(piece) != 1:
            problems.append(f"timetable: {rel_path} has {doc.count(piece)} {name}s, not 1")
    return problems
