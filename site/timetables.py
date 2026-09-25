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
"""
import datetime
import functools
import html
import importlib.resources
import json
import math
import os
import re
import unicodedata
import zoneinfo

SECTION = "prayer-times/"
HOME_MARKER = "<!-- prayer-times -->"
OBLIGATORY = [0, 2, 3, 4, 5]  # the prayers of the Today card, as in the app: Sunrise is not one
DHUHR = 2
FAJR, SUNRISE, ASR, MAGHRIB, ISHA = 0, 1, 3, 4, 5
NEARBY_KM = 900
AT_KAABA_KM = 5
MAX_SAME_COUNTRY = 14
MAX_NEARBY = 6

MIHRAB = ('<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M4.56 12.6V8.13c0-2.32 1.38-3.94 3.44-4.75 '
          '2.06.81 3.44 2.43 3.44 4.75v4.47" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" '
          'stroke-linejoin="round"/><circle cx="8" cy="6.3" r="0.95" fill="currentColor"/></svg>')
SEARCH = ('<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" '
          'stroke-width="1.8"/><path d="M16 16l4.5 4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>')
RING_R = 88
RING_C = 2 * math.pi * RING_R


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


def stale_zones(cities: list) -> list:
    """Cities whose clock the generator got wrong. Its offsets come from the JDK's copy of the
    time-zone database, which is only as new as the JDK; the tzdata package follows IANA within
    days. Morocco left UTC+1 for good on 20 September 2026 before any JDK knew, and a page built
    on the old rules prints every time an hour late, so any disagreement stops the build."""
    problems = []
    for city in cities:
        zone = _zone(city["timeZone"])
        for day in city["days"]:
            at = datetime.datetime.fromtimestamp(day["epochs"][DHUHR], tz=datetime.timezone.utc)
            offset = int(at.astimezone(zone).utcoffset().total_seconds())
            if offset != day["offset"]:
                problems.append(f"{city['slug']}: on {day['date']} the generator has UTC offset {day['offset']} s "
                                f"but the time-zone database says {offset} s for {city['timeZone']}; "
                                "the JDK's zone data is out of date (update the JDK or hold the city)")
                break
    return problems


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
        stale = stale_zones(self.cities)
        if stale:
            raise DataError("\n".join(stale))

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
        description = plain(t["description"], **values, month=month)
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
        cfg = self.langs[lang]
        t = cfg["timetable"]
        origin = "https://taqwa.world/"
        data = {
            "@context": "https://schema.org",
            "@type": "BreadcrumbList",
            "itemListElement": [
                {"@type": "ListItem", "position": 1, "name": cfg["brand"], "item": origin + cfg["prefix"]},
                {"@type": "ListItem", "position": 2, "name": t["nav"], "item": origin + self.index_dir(lang)},
                {"@type": "ListItem", "position": 3, "name": city["pages"][lang]["city"]},
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

        crumbs = (f'<nav class="crumbs" aria-label="{attr(t["nav"])}"><a href="../">{esc(t["nav"])}</a>'
                  f'<span aria-hidden="true">{sep}</span><a href="../#{city["country"].lower()}">{esc(page["country"])}</a>'
                  f'<span aria-hidden="true">{sep}</span><span aria-current="page">{esc(page["city"])}</span></nav>')
        if at_kaaba(city):
            # In Makkah itself a bearing and "0 km to Makkah" mean nothing: the Kaaba is there.
            qibla = f'<div class="fact qibla"><div><span class="label">{esc(page["qibla"])}</span><b>{esc(t["qibla_here"])}</b></div></div>'
        else:
            qibla = (f'<div class="fact qibla">{dial(city["qibla"]["bearing"])}<div><span class="label">{esc(page["qibla"])}</span>'
                     f'<b>{fill(t["qibla_bearing"], **values)}</b><span>{fill(t["qibla_distance"], **values)}</span></div></div>')
        facts = f'''<div class="facts">
        <div class="fact"><span class="label">{esc(t["method"])}</span><b>{esc(page["method"])}</b><span>{fill(t["asr"], **values)} · <span dir="ltr">{esc(page["offset"])}</span></span><a href="{{support}}#prayer-time">{esc(t["why"])}</a></div>
        {qibla}
      </div>'''
        hero = f'''<div class="city-hero">
    <div class="city-copy">
      {crumbs}
      <h1>{fill(t["h1"], **values)}</h1>
      <p class="city-meta"><span>{esc(page["country"])}</span> · <span data-tt="full">{esc(page["today"]["full"])}</span> · <span data-tt="hijri">{esc(page["today"]["hijri"])}</span></p>
      {facts}
    </div>
    {self.today_card(city, lang, today)}
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
  {"".join(months)}
  <p class="note tt-note">{fill(t["note"], date=page["today"]["date"])}</p>
  {self.after(city, lang)}
</main>
<script type="application/json" id="tt-data">{data}</script>
<script src="{{root}}assets/timetable.js" defer></script>
'''

    def today_card(self, city: dict, lang: str, today: int) -> str:
        """The app's Prayer screen in a card. Without script it is a calendar leaf for the day the
        page was built and the day's five times; the script turns the ring into the countdown."""
        cfg = self.langs[lang]
        t = cfg["timetable"]
        page = city["pages"][lang]
        day = page["days"][today]
        friday = city["days"][today]["friday"]
        arabic_script = cfg["dir"] == "rtl"
        items = []
        for p in OBLIGATORY:
            pair = "" if arabic_script else f'<em lang="ar">{esc(self.arabic[p])}</em>'
            tag = ""
            if p == DHUHR:
                tag = f'<span class="tag" data-tt="jumuah"{"" if friday else " hidden"}>{esc(page["jumuah"])}</span>'
            items.append(f'<li data-p="{p}"><i></i><b>{esc(page["prayers"][p])}</b>{pair}{tag}<time>{esc(day["times"][p])}</time></li>')
        month_title = heading(page["months"][0]["title"])
        return f'''<section class="today" aria-label="{attr(t["today"])}">
      <div class="ring-box">{ring(0)}
        <div class="ring-text"><span class="ring-label" data-tt="label">{esc(page["today"]["weekday"])}</span><span class="ring-count" data-tt="count">{esc(day["day"])}</span><span class="ring-at" data-tt="at">{esc(month_title)}</span></div>
      </div>
      <ol class="tl">{"".join(items)}</ol>
      <p class="tt-stale" hidden>{esc(t["stale"])} <a href="{{home}}">{esc(t["cta_button"])}</a></p>
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
            high = facts_d["highLatitude"]
            any_high = any_high or high
            cells = [f'<th scope="row" class="d"><b>{esc(day["day"])}</b><span>{esc(day["weekday"])}</span></th>',
                     f'<td class="h">{esc(day["hijri"])}</td>']
            for p, time in enumerate(day["times"]):
                if p == DHUHR and facts_d["friday"]:
                    cells.append(f'<td class="jm">{esc(time)}<span class="tag">{esc(page["jumuah"])}</span></td>')
                else:
                    cells.append(f"<td>{esc(time)}</td>")
            cls = f' class="{" ".join(classes)}"' if classes else ""
            rows.append(f'<tr data-i="{i}"{cls}>{"".join(cells)}</tr>')

        notes = []
        for change in page["clockChanges"]:
            if first <= change["index"] < first + count:
                notes.append(f'<p class="note">{fill(t["clock_change"], date=change["date"], offset=change["offset"])}</p>')
        if any_high and page["highLatitude"]:
            notes.append(f'<p class="note">{fill(t["high_latitude"], rule=page["highLatitude"][0], fajr=page["prayers"][FAJR], isha=page["prayers"][ISHA])}</p>')
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
            groups.append(f'<h2 class="label">{fill(t["other_cities"], **values)}</h2><div class="chips">{chips(same[:MAX_SAME_COUNTRY])}</div>')
        if near:
            groups.append(f'<h2 class="label">{fill(t["nearby"], **values)}</h2><div class="chips">{chips(near)}</div>')
        groups.append(f'<div class="chips"><a class="all" href="../">{esc(t["all_cities"])}</a></div>')
        return f'''<section class="after-month">
    <div class="cta">
      <div class="cta-copy"><span class="label">{esc(t["cta_label"])}</span><h2>{esc(t["cta_title"])}</h2>
        <p>{fill(t["cta_body"], **values)}</p>
        <div class="stores"><a class="pill" href="{{home}}">{MIHRAB} {esc(t["cta_button"])}</a></div></div>
      <div class="cta-shot">{self.screenshot(lang)}</div>
    </div>
    <div class="nearby">{"".join(groups)}</div>
  </section>'''

    @functools.lru_cache(maxsize=None)
    def screenshot(self, lang: str) -> str:
        """The Prayer screen the language's own home page shows, light and dark, loaded lazily."""
        with open(os.path.join(os.path.dirname(__file__), "pages", lang, "home.html"), encoding="utf-8") as f:
            home = f.read()
        match = re.search(r'<section class="wrap hero">.*?(<picture>.*?</picture>)', home, re.S)
        if not match:
            raise DataError(f"site/pages/{lang}/home.html has no hero screenshot to reuse")
        picture = match.group(1).replace(' fetchpriority="high"', ' loading="lazy"')
        return re.sub(r'alt="[^"]*"', 'alt=""', picture)

    def live_data(self, city: dict, lang: str, today: int) -> str:
        """What assets/timetable.js needs and cannot read from the markup: the zone, the instants,
        and the day's long forms for when the reader's day is not the one the page was built on."""
        cfg = self.langs[lang]
        page = city["pages"][lang]
        days = []
        for facts, day in zip(city["days"], page["days"]):
            days.append({"d": facts["date"], "e": facts["epochs"], "f": 1 if facts["friday"] else 0,
                         "full": day["full"], "hijri": day["hijriLong"], "t": day["times"]})
        data = {
            "tz": city["timeZone"],
            "digits": page["digits"],
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
        order = ["middle-east", "north-africa", "turkiye-central-asia", "south-asia", "southeast-asia",
                 "africa", "europe", "americas", "oceania"]
        for region in order:
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
<script src="{{root}}assets/timetable.js" defer></script>
'''

    def keys(self, city: dict, lang: str) -> str:
        """Every name the index filter should find a city by: in this language and in English,
        with its country, and the words of its address."""
        page, english = city["pages"][lang], city["pages"]["en"]
        words = [page["city"], english["city"], page["country"], english["country"], city["slug"].replace("-", " ")]
        return " ".join(dict.fromkeys(fold(w) for w in words))

    def home_section(self, lang: str) -> str:
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
    months, each with a row for every day of that month and six times in each, and the live
    data the script needs; an index lists every city that has a page in its language."""
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
    return problems
