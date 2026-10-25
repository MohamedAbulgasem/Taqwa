"""Tests of site/timetables.py and assets/timetable.js that need no generated document: the clock
correction (review I4), the minute ruler (review I1, M1, M9), French's "d’" before a vowel, and a
made-up city's page shown with one month or two (ruling R116) — its markup, check_page, and the
page script on the page's last evening, run with a fixed clock in headless Chrome where there is
one (skipped where there is not). Standard library only; the Pages workflow runs them before the
build, and so can anyone:

    python3 site/test_timetables.py

The instants here are made up (whole minutes on arbitrary days), never a published table's
(ruling R69): the expectations are read from the zone database, as the page's are.
"""
import copy
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import timetables  # noqa: E402

UTC = datetime.timezone.utc
WESTERN = "0123456789"
ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
ZONE = "Europe/London"  # UTC+1 until 25 October 2026, then UTC+0
SITE = os.path.dirname(os.path.abspath(__file__))


def at(month: int, day: int, hour: int, minute: int) -> int:
    """An instant in 2026, given in UTC."""
    return int(datetime.datetime(2026, month, day, hour, minute, tzinfo=UTC).timestamp())


def clock(epoch: int, ds: str, shift_hours: int = 0) -> str:
    """The clock the page should show for an instant in ZONE, or, with a shift, the clock a JDK
    with older zone data would have written (the instant read an hour off)."""
    moment = datetime.datetime.fromtimestamp(epoch, tz=UTC).astimezone(timetables._zone(ZONE))
    moment += datetime.timedelta(hours=shift_hours)
    return timetables.clock_text(moment.hour, moment.minute, ds)


def city(cautious: bool, shift_hours: int) -> dict:
    """Two days of a made-up city in ZONE whose page clocks were written `shift_hours` off: an
    English page in Western digits and an Arabic one in Arabic-Indic digits."""
    facts = []
    for n, day in enumerate((5, 6)):
        epochs = [at(10, day, 4, 7 + n), at(10, day, 5, 51), at(10, day, 11, 52), at(10, day, 14, 38),
                  at(10, day, 17, 19), at(10, day, 18, 44)]
        members = ([epochs + [at(10, day, 3, 57)], [e + 120 for e in epochs] + [at(10, day, 3, 59)]]
                   if cautious else [])
        facts.append({
            "date": f"2026-10-0{day}", "epochs": epochs, "asrOther": at(10, day, 15, 21),
            "endOfEating": at(10, day, 3, 57), "imsak": None if cautious else at(10, day, 3, 47),
            "members": members, "offset": shift_hours * 3600 + 3600,
        })
    pages = {}
    for lang, ds in (("en", WESTERN), ("ar", ARABIC_INDIC)):
        days = []
        for f in facts:
            days.append({
                "date": f["date"], "times": [clock(e, ds, shift_hours) for e in f["epochs"]],
                "asrOther": clock(f["asrOther"], ds, shift_hours), "endOfEating": clock(f["endOfEating"], ds, shift_hours),
                "imsak": None if f["imsak"] is None else clock(f["imsak"], ds, shift_hours),
                "members": [[clock(e, ds, shift_hours) for e in member] for member in f["members"]],
            })
        pages[lang] = {"digits": ds, "days": days, "offset": "", "clockChanges": []}
    return {"slug": "made-up-city", "timeZone": ZONE, "today": "2026-10-05", "days": facts, "pages": pages}


class CorrectClocksTest(unittest.TestCase):
    def assert_every_clock_right(self, c: dict) -> None:
        for lang, page in c["pages"].items():
            ds = page["digits"]
            for f, day in zip(c["days"], page["days"]):
                self.assertEqual([clock(e, ds) for e in f["epochs"]], day["times"], lang)
                self.assertEqual(clock(f["asrOther"], ds), day["asrOther"], lang)
                self.assertEqual(clock(f["endOfEating"], ds), day["endOfEating"], lang)
                if f["imsak"] is None:
                    self.assertIsNone(day["imsak"], lang)
                else:
                    self.assertEqual(clock(f["imsak"], ds), day["imsak"], lang)
                self.assertEqual([[clock(e, ds) for e in m] for m in f["members"]], day["members"], lang)

    def test_every_clock_of_a_day_is_corrected_not_the_six_alone(self):
        c = city(cautious=False, shift_hours=-1)
        self.assertTrue(timetables.correct_clocks(c))
        self.assert_every_clock_right(c)
        self.assertEqual(3600, c["days"][0]["offset"])
        self.assertEqual("UTC+1", c["pages"]["en"]["offset"])
        self.assertEqual("UTC+" + ARABIC_INDIC[1], c["pages"]["ar"]["offset"])

    def test_a_cautious_places_members_are_corrected_too(self):
        c = city(cautious=True, shift_hours=-1)
        self.assertTrue(timetables.correct_clocks(c))
        self.assert_every_clock_right(c)

    def test_a_detail_clock_alone_an_hour_off_is_found(self):
        c = city(cautious=True, shift_hours=0)
        f = c["days"][1]
        c["pages"]["en"]["days"][1]["members"][1][6] = clock(f["members"][1][6], WESTERN, -1)
        self.assertTrue(timetables.correct_clocks(c))
        self.assert_every_clock_right(c)

    def test_nothing_changes_where_the_generator_agrees(self):
        for cautious in (False, True):
            c = city(cautious=cautious, shift_hours=0)
            before = copy.deepcopy(c)
            self.assertFalse(timetables.correct_clocks(c))
            self.assertEqual(before, c)

    def test_the_same_minute_written_otherwise_in_a_detail_line_stops_the_build(self):
        c = city(cautious=False, shift_hours=0)
        day = c["pages"]["en"]["days"][0]
        day["endOfEating"] = "0" + day["endOfEating"]  # the hour padded: the same minute, another format
        with self.assertRaises(timetables.DataError):
            timetables.correct_clocks(c)


WORDS = {
    "percent": "{n}%", "alt": "No day before {authority}; {p0} same, {p1} one, {p2} two, {p3} more.",
    "none": "{n} days", "before": "before", "same": "same minute", "one": "{n} min", "two": "{n} min",
    "three": "{n} or more", "minute": "the \"minute\"",
}
SHARES = {"0": 0.4, "1": 0.3, "2": 0.2, "3+": 0.1}  # made up


class RulerTest(unittest.TestCase):
    def test_every_numeral_is_in_the_pages_digits(self):
        svg = timetables.ruler_svg(SHARES, True, WORDS, WESTERN)
        self.assertIsNone(re.search("[٠-٩]", svg))
        svg = timetables.ruler_svg(SHARES, True, WORDS, ARABIC_INDIC)
        text = "".join(re.findall(r">([^<]+)</text>", svg))
        self.assertIsNone(re.search("[0-9]", text))
        self.assertIn(ARABIC_INDIC[0] + " days", text)
        self.assertIn(ARABIC_INDIC[3] + " or more", text)

    def test_the_label_is_an_escaped_attribute(self):
        words = dict(WORDS, alt='A "quoted" {authority} & more; {p0} {p1} {p2} {p3}')
        svg = timetables.ruler_svg(SHARES, False, words, WESTERN)
        label = re.search(r'aria-label="([^"]*)"', svg).group(1)
        self.assertIn("&quot;quoted&quot;", label)
        self.assertIn("&amp;", label)

    def test_the_tallest_bars_figure_clears_the_authoritys_label(self):
        svg = timetables.ruler_svg({"0": 1.0}, False, WORDS, WESTERN)
        label_y = float(re.search(r'<text x="71" y="([\d.]+)"', svg).group(1))
        tallest = min(float(y) for y in re.findall(r'<rect x="[\d.]+" y="([\d.]+)" width="40"', svg))
        # The figure stands 4.5 above its bar and is 10.5 tall: its top must stay under the label's
        # line and the label's descenders (about a third of its size) with room to spare.
        self.assertGreaterEqual((tallest - 4.5 - 10.5) - (label_y + 10.5 / 3), 4)


# ---------------------------------------------------------------- a made-up page, one month or two

PRAYERS = ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"]
OCTOBER, NOVEMBER = (2026, 10), (2026, 11)
SLUG = "made-up-city"


def language(lang: str) -> dict:
    """A language's meta.json as build.py loads it."""
    with open(os.path.join(SITE, "pages", lang, "meta.json"), encoding="utf-8") as f:
        cfg = json.load(f)
    cfg["prefix"] = "" if lang == "en" else f"{lang}/"
    return cfg


def long_date(date: datetime.date) -> str:
    return f"{date.day} {date.strftime('%B %Y')}"


def made_up_document(months: list, today: str) -> dict:
    """The document of one made-up city in ZONE whose page shows `months`, (year, month) pairs —
    this month alone, or this month and next — built on `today`, in English. Every day has the
    same made-up instants in UTC (Fajr 4:07, Isha 18:44), and every clock is read from them on
    ZONE's clock, as the generator writes them."""
    zone = timetables._zone(ZONE)
    facts, days, month_facts, month_texts = [], [], [], []
    for year, month in months:
        first = datetime.date(year, month, 1)
        dates = [first + datetime.timedelta(days=n) for n in range(31) if (first + datetime.timedelta(days=n)).month == month]
        month_facts.append({"year": year, "month": month, "days": len(dates)})
        month_texts.append({"title": first.strftime("%B %Y"), "hijri": "Rabiʼ al-Thani – Jumada al-Ula 1448"})
        for date in dates:
            def utc(hour: int, minute: int) -> int:
                return int(datetime.datetime(date.year, date.month, date.day, hour, minute, tzinfo=UTC).timestamp())
            epochs = [utc(4, 7), utc(5, 51), utc(11, 52), utc(14, 38), utc(17, 19), utc(18, 44)]
            offset = int(datetime.datetime.fromtimestamp(epochs[2], tz=UTC).astimezone(zone).utcoffset().total_seconds())
            facts.append({"date": date.isoformat(), "friday": date.weekday() == 4, "offset": offset, "highLatitude": False,
                          "ramadanIsha": False, "epochs": epochs, "asrOther": utc(15, 21), "endOfEating": utc(3, 57),
                          "sunset": utc(17, 16), "imsak": None, "setByRule": [], "polar": False})
            days.append({"day": str(date.day), "weekday": date.strftime("%a"), "weekdayLong": date.strftime("%A"),
                         "date": long_date(date), "full": f"{date.strftime('%A')}, {long_date(date)}",
                         "hijri": f"{date.day} Jumada", "hijriLong": f"{date.day} Jumada al-Ula 1448",
                         "times": [clock(e, WESTERN) for e in epochs], "asrOther": clock(utc(15, 21), WESTERN),
                         "endOfEating": clock(utc(3, 57), WESTERN), "imsak": None, "setByRule": []})
    first_day = datetime.date(*months[0], 1)
    before = [int(datetime.datetime(first_day.year, first_day.month, 1, tzinfo=zone).utcoffset().total_seconds())]
    before += [f["offset"] for f in facts[:-1]]
    changes = [{"index": i, "date": days[i]["date"], "offset": timetables.offset_text(f["offset"], WESTERN)}
               for i, f in enumerate(facts) if f["offset"] != before[i]]
    i = [f["date"] for f in facts].index(today)
    built = datetime.date.fromisoformat(today)
    page = {
        "locale": "en-GB", "digits": WESTERN, "countdownDigits": WESTERN, "city": "Made-up City", "country": "United Kingdom",
        "method": "Made-up Authority", "madhab": "Standard", "otherSchool": "Hanafi", "members": [], "prayers": PRAYERS,
        "nextIn": [f"{p} in" for p in PRAYERS], "jumuah": "Jumuʿah", "qibla": "Qibla", "qiblaDetail": "119° · 4,000 km to Makkah",
        "bearing": "119°", "distance": "4,000", "offset": timetables.offset_text(facts[i]["offset"], WESTERN),
        "today": {"weekday": built.strftime("%A"), "full": days[i]["full"], "date": long_date(built), "hijri": days[i]["hijriLong"]},
        "highLatitude": [], "clockChanges": changes,
        "strings": {
            "whoseTitle": "Made-up Authority timetable", "notAffiliated": "Taqwa is not affiliated with Made-up Authority.",
            "checkedThrough": "Checked against Made-up Authority’s published timetable through 31 December 2026.",
            "whoPublishes": "Who publishes them", "whoPublishesBody": "Made-up Authority publishes the prayer times used in Made-up City.",
            "howReproduces": "How Taqwa reproduces them", "methodIntro": "The way Made-up Authority calculates.",
            "howChecked": "How it was checked", "statDaysValue": "365", "statDays": "days at 1 places",
            "statNever": "starts before Made-up Authority’s", "statNeverAny": "", "statMinutes": "2 min", "statAtMost": "at most after",
            "cautiousBody": "", "maghribGuard": "", "maghribGuardTemplate": "", "maghribNoSunsetTemplate": "", "whichDecides": "Which timetable decides each time today",
            "matchMosque": "Match my mosque", "setByRule": "Set by rule", "polarLine": "The sun does not rise or set here today.",
            "stopEating": "Stop eating by {time} today.",
        },
        "months": month_texts, "days": days,
    }
    city = {
        "slug": SLUG, "id": 1, "country": "GB", "region": "europe", "timeZone": ZONE, "latitude": 51.5, "longitude": -0.12,
        "languages": ["en"], "featured": [], "method": "made.up", "madhab": "STANDARD", "entryClass": "B", "measured": True,
        "unitId": None, "unitName": None,
        "proof": {"placeDays": 365, "places": 1, "ramadanDays": 30, "first": "2026-01-01", "through": "2026-12-31", "atMost": 2,
                  "fajrShares": {"0": 0.4, "1": 0.3, "2": 0.2, "3+": 0.1}, "cautious": False},
        "qibla": {"bearing": 119.2, "km": 4000.0}, "today": today, "months": month_facts,
        "nextUnchecked": None if len(months) > 1 else {"timetable": "made.up", "day": "2026-11-01"},
        "days": facts, "pages": {"en": page},
    }
    return {"generated": f"{today}T06:07:00Z", "prayersArabic": ["الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"],
            "regions": ["europe"], "cities": [city], "held": [], "proof": {"published": []}}


def rendered(months: list, today: str) -> tuple:
    """The made-up city's English page body, and the Timetables it was rendered with."""
    data = timetables.Timetables({"en": language("en")}, made_up_document(months, today))
    return data.city_body(data.cities[0], "en"), data


def live(body: str) -> dict:
    return json.loads(re.search(r'<script type="application/json" id="tt-data">(.*?)</script>', body, re.S).group(1))


REL = f"prayer-times/{SLUG}/index.html"


class OneMonthPageTest(unittest.TestCase):
    """Ruling R116: a page shows this month alone until every day of the next is checked."""

    def test_a_one_month_page_has_one_month_and_nothing_of_a_second(self):
        body, data = rendered([OCTOBER], "2026-10-29")
        t = data.langs["en"]["timetable"]
        self.assertEqual(1, body.count('<table class="tt"'))
        self.assertEqual(1, body.count('<section class="month"'))
        self.assertEqual(['m-2026-10'], re.findall(r'<button type="button" class="[^"]*\bprint\b[^"]*" data-month="(m-[\d-]+)"', body))
        self.assertNotIn("m-2026-11", body)
        self.assertNotIn("November", body)
        self.assertEqual(31, len(re.findall(r'<tr data-i="\d+"', body)))
        # The clock change of 25 October is on a day shown: its note stands under the one month.
        self.assertEqual(1, body.count(timetables.fill(t["clock_change"], date="25 October 2026", offset="UTC")))
        # The "none set by rule" sentence speaks for this month alone, not "this month or next".
        self.assertIn(timetables.fill(t["note_no_rule_month"]), body)
        self.assertNotIn(timetables.fill(t["note_no_rule"]), body)

    def test_a_two_month_page_keeps_both_months_and_its_sentence(self):
        body, data = rendered([OCTOBER, NOVEMBER], "2026-10-29")
        t = data.langs["en"]["timetable"]
        self.assertEqual(2, body.count('<table class="tt"'))
        self.assertEqual(61, len(re.findall(r'<tr data-i="\d+"', body)))
        self.assertIn(timetables.fill(t["note_no_rule"]), body)
        self.assertNotIn(timetables.fill(t["note_no_rule_month"]), body)

    def test_every_language_says_the_one_month_sentence(self):
        for lang in ("en", "ar", "fr", "tr", "id", "ur", "bn"):
            t = language(lang)["timetable"]
            self.assertTrue(t["note_no_rule_month"], lang)
            self.assertNotEqual(t["note_no_rule"], t["note_no_rule_month"], lang)

    def test_the_live_data_is_the_days_shown_with_the_last_days_leaf(self):
        body, _ = rendered([OCTOBER], "2026-10-29")
        data = live(body)
        self.assertEqual([f"2026-10-{d:02d}" for d in range(1, 32)], [d["d"] for d in data["days"]])
        self.assertEqual(31, data["first"])  # the fold reaches the whole month
        self.assertEqual({"w": "Saturday", "n": "31", "m": "October 2026"}, data["end"])
        two = live(rendered([OCTOBER, NOVEMBER], "2026-10-29")[0])
        self.assertEqual(31, two["first"])
        self.assertEqual({"w": "Monday", "n": "30", "m": "November 2026"}, two["end"])

    def test_check_page_accepts_one_month_or_two_as_the_document_gives(self):
        for months in ([OCTOBER], [OCTOBER, NOVEMBER]):
            body, data = rendered(months, "2026-10-29")
            self.assertEqual([], timetables.check_page(REL, body, data), months)

    def test_check_page_refuses_a_page_whose_months_are_not_the_documents(self):
        one, one_data = rendered([OCTOBER], "2026-10-29")
        two, two_data = rendered([OCTOBER, NOVEMBER], "2026-10-29")
        problems = timetables.check_page(REL, two, one_data)  # a second table the document does not give
        self.assertTrue(any("2 month tables, not 1" in p for p in problems), problems)
        problems = timetables.check_page(REL, one, two_data)  # a month missing
        self.assertTrue(any("1 month tables, not 2" in p for p in problems), problems)
        emptied = re.sub(r'(<table class="tt" data-month="0">.*?<tbody>).*?(</tbody>)', r"\1\2", one, flags=re.S)
        problems = timetables.check_page(REL, emptied, one_data)  # an empty table
        self.assertTrue(any("0 rows for 31 days" in p for p in problems), problems)


# ---------------------------------------------------------------- a made-up cautious page: the far-north guard

COUNCIL, DIRECTORATE, MISSION = "Made-up Council", "Made-up Directorate", "Made-up Mission"
# The app's guard sentence (ruling R155) as the generator hands it over, unfilled: %1$s the member
# followed, %2$s the members whose own Maghrib, set by a far-north rule, is later. Made up here; the
# page takes whatever the app's strings say.
GUARD_T = ("Maghrib is the exception today: it follows %1$s rather than the later Maghrib of %2$s, "
           "set by a far-north rule instead of the sun.")
# The guard days of the made-up October: each held member's (Maghrib from the sun, own Maghrib its
# far-north rule holds), in UTC (London is on UTC from the 25th). On the 29th the Mission's held
# Maghrib is the latest member's own, so a page that fell back to the latest own Maghrib would
# name the Mission, not the Directorate whose Maghrib from the sun is the one shown.
GUARD_DAYS = {29: {1: ((17, 21), (17, 40)), 2: ((17, 15), (17, 50))}, 31: {1: ((17, 17), (17, 40))}}
# The app's second guard sentence, for a day the real sun does not set at the place: no member's
# Maghrib counts as from the sun, and only the most-followed member's own stands. Made up here too.
NO_SUN_T = "Maghrib is the exception today, as there is no sunset here: it follows %1$s rather than the later Maghrib of %2$s."
# The made-up October's day without a sunset: the Directorate's own Maghrib, set by its polar rule.
NO_SUNSET_DAYS = {27: {1: (17, 40)}}


def guard_sentence(followed: str, later: str, template: str = GUARD_T) -> str:
    return template.replace("%1$s", followed).replace("%2$s", later)


def made_up_cautious_document(today: str) -> dict:
    """The made-up city's October as a cautious place, built on `today`: the most-followed Made-up
    Council, whose own times are the ones shown, the Made-up Directorate two minutes before it at
    every start (after it at sunrise) and the Made-up Mission one minute before it, each with its
    own Maghrib from the sun — except on GUARD_DAYS, where a far-north rule holds a member's own
    Maghrib later and the guard sets that aside (ruling R155): on the 29th the Directorate's
    Maghrib from the sun, 17:21, is the latest and is shown, while its own is 17:40 and the
    Mission's 17:50; on the 31st the Directorate's from the sun is 17:17 and the Council's own
    17:19 stands. On NO_SUNSET_DAYS (the 27th) the real sun does not set: no member's Maghrib
    counts from the sun, the Council's own stands and the Directorate's polar 17:40 is later."""
    doc = made_up_document([OCTOBER], today)
    city = doc["cities"][0]
    city["entryClass"] = "C"
    city["proof"]["cautious"] = True
    page = city["pages"]["en"]
    page["members"] = [COUNCIL, DIRECTORATE, MISSION]
    built = int(today[-2:])
    for facts, day in zip(city["days"], page["days"]):
        date = datetime.date.fromisoformat(facts["date"])
        epochs = facts["epochs"]
        council = epochs + [facts["endOfEating"]]
        members = [council] + [[e + (step if p == timetables.SUNRISE else -step) for p, e in enumerate(epochs)]
                               + [facts["endOfEating"]] for step in (120, 60)]
        by_sun = [member[timetables.MAGHRIB] for member in members]
        guard = date.day in GUARD_DAYS or date.day in NO_SUNSET_DAYS
        sun_sets = date.day not in NO_SUNSET_DAYS
        if date.day in GUARD_DAYS:
            for k, ((sun_h, sun_m), (own_h, own_m)) in GUARD_DAYS[date.day].items():
                by_sun[k] = at(10, date.day, sun_h, sun_m)
                members[k][timetables.MAGHRIB] = at(10, date.day, own_h, own_m)
            epochs[timetables.MAGHRIB] = max(by_sun)
            day["times"][timetables.MAGHRIB] = clock(epochs[timetables.MAGHRIB], WESTERN)
        if not sun_sets:
            by_sun = [None for _ in members]
            for k, (own_h, own_m) in NO_SUNSET_DAYS[date.day].items():
                members[k][timetables.MAGHRIB] = at(10, date.day, own_h, own_m)
        facts.update(members=members, maghribBySun=by_sun, guard=guard, sunSets=sun_sets)
        day["members"] = [[clock(e, WESTERN) for e in member] for member in facts["members"]]
    followed, later = (DIRECTORATE, f"{DIRECTORATE}, {MISSION}") if built == 29 else (COUNCIL, DIRECTORATE)
    sentence = ""
    if built in GUARD_DAYS:
        sentence = guard_sentence(followed, later)
    elif built in NO_SUNSET_DAYS:
        sentence = guard_sentence(COUNCIL, DIRECTORATE, NO_SUN_T)
    page["strings"].update(
        cautiousBody="Mosques in Made-up City follow different timetables in good faith, and none is followed by most.",
        statNeverAny="starts before the timetable that decides it", maghribGuardTemplate=GUARD_T,
        maghribNoSunsetTemplate=NO_SUN_T, maghribGuard=sentence,
    )
    return doc


def rendered_cautious(today: str) -> tuple:
    data = timetables.Timetables({"en": language("en")}, made_up_cautious_document(today))
    return data.city_body(data.cities[0], "en"), data


def guard_paragraph(html: str) -> tuple:
    """The guard's paragraph: whether it is shown, and its words."""
    found = re.search(r'<p class="guard" data-tt="guard"( hidden(?:="[^"]*")?)?>([^<]*)</p>', html)
    return found.group(1) is None, found.group(2)


def maghrib_row(html: str) -> tuple:
    """Which timetable decides Maghrib: the time shown, the deciding chips, the others' chips."""
    row = re.search(r'<li data-p="4"><span class="pn">.*?</li>', html, re.S).group(0)
    return (re.search(r'<span class="shown">([^<]*)<', row).group(1), re.findall(r'<b class="chip on">([^<]*)</b>', row),
            re.findall(r'<span class="chip">([^<]*)</span>', row))


class CautiousGuardTest(unittest.TestCase):
    """Ruling R155 on a cautious page, as the app's About shows it: Maghrib is the latest any
    timetable gives from the sun, so a member whose far-north rule holds its own Maghrib later
    can decide it by its Maghrib from the sun, and on such a day the guard's sentence says that a
    later Maghrib was set aside. There is no cap any more."""

    def test_on_a_guard_day_the_sentence_stands_and_the_sun_time_decides(self):
        body, data = rendered_cautious("2026-10-29")
        self.assertEqual((True, guard_sentence(DIRECTORATE, f"{DIRECTORATE}, {MISSION}")), guard_paragraph(body))
        # 17:21 is the Directorate's Maghrib from the sun, not its own (17:40): it decides all the
        # same, and the Mission, whose own is the latest, does not.
        self.assertEqual(("17:21", [DIRECTORATE], [f"{COUNCIL} 17:19", f"{MISSION} 17:50"]), maghrib_row(body))
        self.assertEqual([], timetables.check_page(REL, body, data))

    def test_where_the_most_followeds_own_maghrib_is_shown_it_decides(self):
        body, _ = rendered_cautious("2026-10-31")
        self.assertEqual((True, guard_sentence(COUNCIL, DIRECTORATE)), guard_paragraph(body))
        self.assertEqual(("17:19", [COUNCIL], [f"{DIRECTORATE} 17:40", f"{MISSION} 17:18"]), maghrib_row(body))

    def test_on_a_day_without_a_sunset_the_second_sentence_stands_and_the_most_followed_decides(self):
        body, data = rendered_cautious("2026-10-27")
        self.assertEqual((True, guard_sentence(COUNCIL, DIRECTORATE, NO_SUN_T)), guard_paragraph(body))
        shown, deciding, others = maghrib_row(body)
        self.assertEqual(([COUNCIL], f"{DIRECTORATE} 17:40"), (deciding, others[0]))
        self.assertEqual([], timetables.check_page(REL, body, data))

    def test_on_any_other_day_nothing_is_said_and_the_latest_decides(self):
        body, data = rendered_cautious("2026-10-28")
        self.assertEqual((False, ""), guard_paragraph(body))
        self.assertEqual(("17:19", [COUNCIL], [f"{DIRECTORATE} 17:17", f"{MISSION} 17:18"]), maghrib_row(body))
        self.assertNotIn("cap", re.findall(r'data-tt="(\w+)"', body))
        self.assertEqual([], timetables.check_page(REL, body, data))

    def test_deciders_match_a_members_maghrib_from_the_sun_and_nothing_else(self):
        doc = made_up_cautious_document("2026-10-28")
        days = doc["cities"][0]["days"]
        self.assertEqual([1], timetables.deciders(days[28], timetables.MAGHRIB))  # the 29th
        self.assertEqual([0], timetables.deciders(days[30], timetables.MAGHRIB))  # the 31st
        # A sun time equal to another prayer's shown instant decides nothing but Maghrib.
        day = copy.deepcopy(days[27])
        day["maghribBySun"] = [None, day["epochs"][timetables.ISHA], None]
        self.assertEqual([0], timetables.deciders(day, timetables.ISHA))
        self.assertEqual([0], timetables.deciders(day, timetables.MAGHRIB))

    def test_the_live_data_carries_the_guard_and_each_members_maghrib_from_the_sun(self):
        body, _ = rendered_cautious("2026-10-28")
        data = live(body)
        guard_days = set(GUARD_DAYS) | set(NO_SUNSET_DAYS)
        self.assertEqual([1 if d in guard_days else 0 for d in range(1, 32)], [day["g"] for day in data["days"]])
        self.assertEqual([0 if d in NO_SUNSET_DAYS else 1 for d in range(1, 32)], [day["ss"] for day in data["days"]])
        # "s" stays the day's sunset epoch on a cautious page, as on every page: the page script ends Asr at it.
        doc = made_up_cautious_document("2026-10-28")
        self.assertEqual([facts["sunset"] for facts in doc["cities"][0]["days"]], [day["s"] for day in data["days"]])
        self.assertEqual([at(10, 29, 17, 19), at(10, 29, 17, 21), at(10, 29, 17, 15)], data["days"][28]["ms"])
        self.assertEqual([None, None, None], data["days"][26]["ms"])
        self.assertEqual(GUARD_T, data["guardT"])
        self.assertEqual(NO_SUN_T, data["guardNoSunT"])
        self.assertEqual("", data["guardText"])
        self.assertFalse({"cap", "capT", "capText"} & (set(data) | set(data["days"][0])))


class FrenchElisionTest(unittest.TestCase):
    """French writes "de" as "d’" before a vowel: "chaque jour d’octobre", "Près d’Anvers"."""

    FRENCH_MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre",
                     "novembre", "décembre"]

    def page(self, city: str, month: str) -> dict:
        """The made-up city's French page, named `city`, showing `month`, beside a neighbour across
        a border (so "Près de …" has cities to list): its meta and body as write_page gets them."""
        doc = made_up_document([OCTOBER], "2026-10-29")
        here = doc["cities"][0]
        page = here["pages"].pop("en")
        here["pages"] = {"fr": dict(page, city=city, months=[dict(m, title=month) for m in page["months"]])}
        here["languages"] = ["fr"]
        neighbour = copy.deepcopy(here)
        neighbour.update(slug="made-up-town", country="NL")
        neighbour["pages"]["fr"]["city"] = "Utrecht"
        doc["cities"].append(neighbour)
        data = timetables.Timetables({"fr": language("fr")}, doc)
        written = {}
        data.build_city(data.cities[0], "fr", "", lambda *_, **page: written.update(page), {"fr": "fr/prayer-times/x/"})
        return written

    def test_de_is_elided_before_a_vowel_and_nowhere_else(self):
        for written, said in (("chaque jour de octobre 2026", "chaque jour d’octobre 2026"),
                              ("chaque jour de août", "chaque jour d’août"), ("De avril", "D’avril"),
                              ("Près de Anvers", "Près d’Anvers"), ("Près de Île-de-France", "Près d’Île-de-France"),
                              ("chaque jour de novembre", "chaque jour de novembre"), ("Près de Hambourg", "Près de Hambourg"),
                              ("Près de Yaoundé", "Près de Yaoundé"), ("Les horaires de 41 villes", "Les horaires de 41 villes"),
                              ("le monde entier", "le monde entier"), ("Moscou de Octobre", "Moscou d’Octobre")):
            self.assertEqual(said, timetables.elide("fr", written))
        self.assertEqual("de octobre", timetables.elide("en", "de octobre"))  # French alone

    def test_every_month_after_de_in_the_description(self):
        for month in self.FRENCH_MONTHS:
            description = self.page("Paris", f"{month} 2026")["meta"]["description"]
            elided = month[0] in "aeiouéèêâ"
            self.assertIn(f"chaque jour {'d’' if elided else 'de '}{month} 2026", description)
        self.assertEqual(["avril", "août", "octobre"], [m for m in self.FRENCH_MONTHS if m[0] in "aeiouéèêâ"])

    def test_near_a_city_whose_name_begins_with_a_vowel(self):
        self.assertIn("<h2>Près d’Anvers</h2>", self.page("Anvers", "octobre 2026")["body"])
        self.assertIn("<h2>Près de Bruxelles</h2>", self.page("Bruxelles", "octobre 2026")["body"])

    def test_every_french_sentence_with_a_word_after_de_is_one_the_pages_elide(self):
        numbers = {"count", "calendars"}  # "de 41 villes": a number never elides

        def sentences(node, key=""):
            if isinstance(node, dict):
                for k, v in node.items():
                    yield from sentences(v, k)
            elif isinstance(node, str):
                yield key, node

        words = {key for key, text in sentences(language("fr"))
                 if set(re.findall(r"\b[Dd]e \{(\w+)\}", text)) - numbers}
        # A new one: put it through timetables.elide where it is filled, and add it to ELIDED.
        self.assertEqual(set(timetables.ELIDED), words)


def find_chrome():
    """Chrome, to run the page script in: $CHROME, the Mac's, or one on the PATH."""
    for candidate in (os.environ.get("CHROME"), "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                      shutil.which("google-chrome"), shutil.which("google-chrome-stable"), shutil.which("chromium"),
                      shutil.which("chromium-browser")):
        if candidate and os.path.exists(candidate):
            return candidate
    return None


CHROME = find_chrome()

# Date, pinned: Date.now() and new Date() read one instant however long the page runs.
FIXED_CLOCK = ('<script>(function(){var R=Date,T=%d;function F(){if(arguments.length){'
               'return new (Function.prototype.bind.apply(R,[null].concat([].slice.call(arguments))))();}'
               'return new R(T);}F.now=function(){return T;};F.parse=R.parse;F.UTC=R.UTC;F.prototype=R.prototype;'
               'window.Date=F;})();</script>')


def dump_dom(page: str) -> str:
    """The DOM headless Chrome holds after running `page` (a whole document) for a moment."""
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "page.html")
        with open(path, "w", encoding="utf-8") as f:
            f.write(page)
        # Headless Chrome keeps a throwaway profile of its own; a --user-data-dir of ours made it
        # hang on the Mac.
        args = [CHROME, "--headless=new", "--disable-gpu", "--no-first-run", "--virtual-time-budget=1500",
                "--dump-dom", "file://" + path]
        if sys.platform.startswith("linux"):
            args.insert(1, "--no-sandbox")  # a CI runner's user namespace may refuse Chrome's sandbox
        return subprocess.run(args, capture_output=True, text=True, timeout=60).stdout


def run_page(body: str, instant: str) -> dict:
    """The page script run over `body` in headless Chrome with the clock pinned at `instant`, and
    what the page then shows: the ring's three lines and its arc, each prayer row's classes, the
    stale line, today's table row, the fold row."""
    with open(os.path.join(SITE, "assets", "timetable.js"), encoding="utf-8") as f:
        script = f.read()
    body = re.sub(r'<script src="\{root\}assets/timetable\.js\?v=\{script_version\}" defer></script>',
                  lambda _: "<script>" + script + "</script>", body)
    ms = int(datetime.datetime.fromisoformat(instant.replace("Z", "+00:00")).timestamp() * 1000)
    dom = dump_dom(f'<!doctype html><html lang="en" dir="ltr"><head><meta charset="utf-8">{FIXED_CLOCK % ms}</head>'
                   f'<body>{body}</body></html>')
    if 'data-tt="count"' not in dom:
        raise AssertionError(f"Chrome returned no page: {dom[:200]!r}")

    def text(tt: str) -> str:
        return re.search(rf'data-tt="{tt}">([^<]*)<', dom).group(1)

    today = re.search(r'<tr data-i="(\d+)" class="[^"]*\bis-today\b', dom)
    # The Today card's rows come first; a cautious page's "which timetable decides" list repeats data-p below them.
    rows = {}
    for p, cls in re.findall(r'<li data-p="(\d)"(?: class="([^"]*)")?>', dom):
        rows.setdefault(int(p), (cls or "").split())
    return {
        "label": text("label"), "count": text("count"), "at": text("at"),
        "arc": re.search(r'class="ring-arc"[^>]*stroke-dasharray="([^"]*)"', dom).group(1),
        "rows": rows,
        "stale": "hidden" not in re.search(r'<p class="tt-stale"[^>]*>', dom).group(0),
        "today": int(today.group(1)) if today else None,
        "fold": re.search(r'<tr class="fold">.*?</tr>', dom, re.S),
        "dom": dom,
    }


@unittest.skipUnless(CHROME, "no Chrome to run the page script in")
class LastEveningTest(unittest.TestCase):
    """The page script never shows a time the page does not carry (ruling R116): on the last
    evening of a page that shows October alone, tomorrow's Fajr is on no day it holds. Built on
    29 October; London's clock is on GMT from the 25th, so the made-up Isha is 18:44."""

    @classmethod
    def setUpClass(cls):
        # A Chrome that cannot run even a bare page here (a runner that refuses it) is a missing
        # tool, not a broken page: skip. Once it can, anything the page script does wrong fails.
        try:
            ran = 'id="ok"' in dump_dom('<!doctype html><p id="ok">ok</p>')
        except (OSError, subprocess.SubprocessError):
            ran = False
        if not ran:
            raise unittest.SkipTest(f"{CHROME} runs no page here")
        cls.one = rendered([OCTOBER], "2026-10-29")[0]
        cls.two = rendered([OCTOBER, NOVEMBER], "2026-10-29")[0]

    def test_before_isha_on_the_last_day_it_counts_down_to_isha(self):
        shown = run_page(self.one, "2026-10-31T18:00:00Z")
        self.assertEqual(("Isha in", "0:44:00", "18:44"), (shown["label"], shown["count"], shown["at"]))
        self.assertIn("now", shown["rows"][4])  # Maghrib current
        self.assertEqual(30, shown["today"])
        self.assertFalse(shown["stale"])

    def test_after_isha_on_the_last_day_the_card_is_the_days_leaf_with_no_countdown(self):
        shown = run_page(self.one, "2026-10-31T20:00:00Z")
        # The calendar leaf of the 31st (not of the 29th, the day the page was built), no clock,
        # an empty ring, and nothing that says the page is out of date: today's times are right.
        self.assertEqual(("Saturday", "31", "October 2026"), (shown["label"], shown["count"], shown["at"]))
        self.assertNotRegex(shown["label"] + shown["count"] + shown["at"], r"\d:\d\d")
        self.assertTrue(shown["arc"].startswith("0 "), shown["arc"])
        self.assertFalse(shown["stale"])
        self.assertEqual(30, shown["today"])
        # Isha has begun and stays current, as in the app; every other prayer has passed.
        self.assertIn("now", shown["rows"][5])
        for p in range(5):
            self.assertIn("past", shown["rows"][p], p)
        # The days gone are folded behind one row, the 31st alone below it.
        self.assertIsNotNone(shown["fold"])
        self.assertIn("1–30", shown["fold"].group(0))

    def test_once_the_last_day_is_over_the_page_says_it_is_out_of_date(self):
        shown = run_page(self.one, "2026-11-01T00:30:00Z")
        self.assertEqual(("", "–", ""), (shown["label"], shown["count"], shown["at"]))
        self.assertTrue(shown["stale"])
        self.assertIsNone(shown["today"])
        self.assertFalse(any("now" in classes for classes in shown["rows"].values()))

    def test_a_two_month_page_counts_down_into_its_next_month(self):
        # The same evening on a page that carries November: tomorrow's Fajr is on it.
        shown = run_page(self.two, "2026-10-31T20:00:00Z")
        self.assertEqual(("Fajr in", "8:07:00", "4:07"), (shown["label"], shown["count"], shown["at"]))
        self.assertFalse(shown["stale"])


@unittest.skipUnless(CHROME, "no Chrome to run the page script in")
class CautiousGuardScriptTest(unittest.TestCase):
    """The page script redraws the cautious explainer for the reader's today (ruling R155): the
    guard's sentence, filled as the generator fills it for the built day, on the reader's guard
    day and nowhere else, and the Maghrib decided by a member's Maghrib from the sun."""

    @classmethod
    def setUpClass(cls):
        try:
            ran = 'id="ok"' in dump_dom('<!doctype html><p id="ok">ok</p>')
        except (OSError, subprocess.SubprocessError):
            ran = False
        if not ran:
            raise unittest.SkipTest(f"{CHROME} runs no page here")
        cls.plain = rendered_cautious("2026-10-28")[0]
        cls.guarded = rendered_cautious("2026-10-29")[0]

    def test_the_readers_guard_day_says_the_sentence_and_its_sun_time_decides(self):
        dom = run_page(self.plain, "2026-10-29T12:00:00Z")["dom"]
        self.assertEqual((True, guard_sentence(DIRECTORATE, f"{DIRECTORATE}, {MISSION}")), guard_paragraph(dom))
        self.assertEqual(("17:21", [DIRECTORATE], [f"{COUNCIL} 17:19", f"{MISSION} 17:50"]), maghrib_row(dom))

    def test_where_the_most_followeds_own_maghrib_is_shown_it_is_the_one_followed(self):
        dom = run_page(self.plain, "2026-10-31T12:00:00Z")["dom"]
        self.assertEqual((True, guard_sentence(COUNCIL, DIRECTORATE)), guard_paragraph(dom))
        self.assertEqual(("17:19", [COUNCIL], [f"{DIRECTORATE} 17:40", f"{MISSION} 17:18"]), maghrib_row(dom))

    def test_the_readers_day_without_a_sunset_says_the_second_sentence(self):
        dom = run_page(self.plain, "2026-10-27T12:00:00Z")["dom"]
        self.assertEqual((True, guard_sentence(COUNCIL, DIRECTORATE, NO_SUN_T)), guard_paragraph(dom))
        self.assertEqual([COUNCIL], maghrib_row(dom)[1])

    def test_a_day_without_the_guard_hides_the_built_days_sentence(self):
        dom = run_page(self.guarded, "2026-10-30T12:00:00Z")["dom"]
        self.assertFalse(guard_paragraph(dom)[0])
        self.assertEqual(("17:19", [COUNCIL], [f"{DIRECTORATE} 17:17", f"{MISSION} 17:18"]), maghrib_row(dom))

    def test_asr_stays_current_until_sunset_on_a_cautious_page_as_in_the_app(self):
        # Between Asr and sunset Asr is the current prayer (the app's TimelineBuilder); the page
        # script ends it at the day's sunset epoch, which the sun-sets flag must not overwrite.
        facts = made_up_cautious_document("2026-10-28")["cities"][0]["days"][27]
        middle = (facts["epochs"][timetables.ASR] + facts["sunset"]) // 2
        instant = datetime.datetime.fromtimestamp(middle, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        shown = run_page(self.plain, instant)
        self.assertIn("now", shown["rows"][timetables.ASR])
        self.assertEqual("Maghrib in", shown["label"])


if __name__ == "__main__":
    unittest.main()
