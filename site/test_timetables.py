"""Tests of site/timetables.py that need no document: the clock correction (review I4) and the
minute ruler (review I1, M1, M9). Standard library only; the Pages workflow runs them before the
build, and so can anyone:

    python3 site/test_timetables.py

The instants here are made up (whole minutes on arbitrary days), never a published table's
(ruling R69): the expectations are read from the zone database, as the page's are.
"""
import copy
import datetime
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import timetables  # noqa: E402

UTC = datetime.timezone.utc
WESTERN = "0123456789"
ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
ZONE = "Europe/London"  # UTC+1 until 25 October 2026, then UTC+0


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


if __name__ == "__main__":
    unittest.main()
