#!/usr/bin/env python3
"""The fetch half's unit tests, on invented data (never a printed time from a restricted table):

    python3 -m unittest tools/timetables/monitor/tests.py
"""
import datetime as dt
import json
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from common import FetchError, Table, nearest_year, norm_time, pm, read_table_rows  # noqa: E402
from fetch import INDEX_HEADER, Store, due  # noqa: E402
from fetchers import diyanet, egypt, jakim, mawaqit, morocco, muis, qatar  # noqa: E402


class Times(unittest.TestCase):
    def test_norm_time(self):
        self.assertEqual("05:07", norm_time("5:07"))
        self.assertEqual("05:07", norm_time("05:07:00"))
        self.assertEqual("05:07", norm_time("5.07"))
        self.assertEqual("-", norm_time(None))
        self.assertEqual("-", norm_time("-"))
        with self.assertRaises(FetchError):
            norm_time("5h07")
        with self.assertRaises(FetchError):
            norm_time("31:00")

    def test_pm(self):
        self.assertEqual("16:15", pm("4:15"))
        self.assertEqual("16:15", pm("16:15"))
        self.assertEqual("04:15", pm("4:15", afternoon=False))

    def test_nearest_year(self):
        self.assertEqual(dt.date(2027, 1, 2), nearest_year(1, 2, dt.date(2026, 12, 30)))
        self.assertEqual(dt.date(2026, 12, 30), nearest_year(12, 30, dt.date(2027, 1, 2)))
        self.assertEqual(dt.date(2026, 6, 15), nearest_year(6, 15, dt.date(2026, 6, 15)))


class Tables(unittest.TestCase):
    def test_hash_ignores_header_and_order(self):
        a = Table("k", "n", 1.0, 2.0, "UTC", "XX", "F S D A M I")
        a.add("2026-01-02", ["5:00", "06:30", "12:00", "15:00", "18:00", "19:30"])
        a.add("2026-01-01", ["05:01", "06:31", "12:01", "15:01", "18:01", "19:31"])
        b = Table("k", "n", 1.0, 2.0, "UTC", "XX", "F S D A M I", source_line="somewhere else")
        b.add("2026-01-01", ["05:01", "06:31", "12:01", "15:01", "18:01", "19:31"])
        b.add("2026-01-02", ["05:00", "06:30", "12:00", "15:00", "18:00", "19:30"])
        self.assertEqual(a.content_hash(), b.content_hash())
        self.assertEqual("2026-01-01", a.first())
        self.assertEqual("2026-01-02", a.last())
        self.assertTrue(a.text("2026-10-05").startswith("# fetched 2026-10-05\n2026-01-01 05:01"))
        b.add("2026-01-02", ["05:00", "06:30", "12:00", "15:00", "18:00", "19:31"])
        self.assertNotEqual(a.content_hash(), b.content_hash())


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-store")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")

    def tearDown(self):
        shutil.rmtree(self.root)

    def table(self, key="k", last="18:00"):
        t = Table(key, "A place", 1.5, 2.5, "Europe/London", "GB", "F S D A M I", entry="gb.cautious", raw=[("resp.json", b"{}")])
        t.add("2026-03-01", ["05:00", "06:30", "12:00", "15:00", last, "19:30"])
        return t

    def test_new_changed_unchanged_and_the_index(self):
        today = dt.date(2026, 10, 5)
        store = Store(self.root, self.monitor, today)
        self.assertEqual("new", store.record("src", self.table()))
        path = os.path.join(self.root, "archive", "tables", "monitor", "src", "k.txt")
        self.assertTrue(os.path.exists(path))
        self.assertTrue(os.path.exists(os.path.join(self.root, "archive", "raw", "monitor", "src", "2026-10-05", "resp.json.gz")))
        store.save()
        # A second run, the same content: unchanged, the file untouched, the fetched date kept.
        later = Store(self.root, self.monitor, dt.date(2026, 10, 12))
        self.assertEqual("unchanged", later.record("src", self.table()))
        self.assertEqual("2026-10-05", later.data["tables"]["src/k"]["fetched"])
        self.assertFalse(os.path.exists(os.path.join(self.root, "archive", "raw", "monitor", "src", "2026-10-12")))
        # A third, one minute moved: changed and rewritten.
        self.assertEqual("changed", later.record("src", self.table(last="18:01")))
        self.assertEqual("2026-10-12", later.data["tables"]["src/k"]["fetched"])
        with open(path) as f:
            self.assertIn("18:01", f.read())
        # Another source's table not fetched this run is `held` in the index.
        later.data["tables"]["other/x"] = dict(later.data["tables"]["src/k"], source="other", key="x", path="archive/tables/monitor/other/x.txt")
        lines = later.index_lines()
        self.assertEqual("\t".join(INDEX_HEADER), lines[0])
        rows = {l.split("\t")[0] + "/" + l.split("\t")[1]: l.split("\t") for l in lines[1:]}
        self.assertEqual("changed", rows["src/k"][INDEX_HEADER.index("status")])
        self.assertEqual("held", rows["other/x"][INDEX_HEADER.index("status")])
        self.assertEqual("gb.cautious", rows["src/k"][INDEX_HEADER.index("entry")])
        self.assertEqual("", rows["src/k"][INDEX_HEADER.index("survey")])
        later.write_index()
        self.assertTrue(os.path.exists(os.path.join(self.root, "archive", "tables", "monitor", "index.tsv")))

    def test_merge_keeps_held_days(self):
        store = Store(self.root, self.monitor, dt.date(2026, 10, 5))
        store.record("src", self.table())
        t = Table("k", "A place", 1.5, 2.5, "Europe/London", "GB", "F S D A M I", entry="gb.cautious")
        t.add("2026-03-02", ["05:00", "06:30", "12:00", "15:00", "18:00", "19:30"])
        self.assertEqual("changed", store.record("src", t))
        rows = read_table_rows(os.path.join(self.root, "archive", "tables", "monitor", "src", "k.txt"))
        self.assertEqual(["2026-03-01", "2026-03-02"], sorted(rows))
        # Without merging, the table is what the fetcher returned.
        t2 = Table("k", "A place", 1.5, 2.5, "Europe/London", "GB", "F S D A M I", entry="gb.cautious")
        t2.add("2026-03-03", ["05:00", "06:30", "12:00", "15:00", "18:00", "19:30"])
        t2.merge = False
        store.record("src", t2)
        self.assertEqual(["2026-03-03"], sorted(read_table_rows(os.path.join(self.root, "archive", "tables", "monitor", "src", "k.txt"))))

    def test_due(self):
        weekly = {"source": "a", "fetcher": "x", "cadence": "weekly"}
        monthly = {"source": "b", "fetcher": "x", "cadence": "monthly"}
        manual = {"source": "c", "fetcher": "manual", "cadence": "manual"}
        keyed = {"source": "d", "fetcher": "london", "cadence": "manual"}
        today = dt.date(2026, 10, 5)
        self.assertTrue(due(weekly, None, today, set(), False))
        self.assertTrue(due(weekly, dt.date(2026, 9, 28), today, set(), False))
        self.assertFalse(due(weekly, dt.date(2026, 10, 3), today, set(), False))
        self.assertTrue(due(monthly, None, today, set(), False))
        self.assertFalse(due(monthly, dt.date(2026, 9, 20), today, set(), False))
        self.assertTrue(due(monthly, dt.date(2026, 9, 1), today, set(), False))
        self.assertFalse(due(manual, None, today, set(), False))
        self.assertFalse(due(manual, None, today, set(), True))
        self.assertTrue(due(manual, None, today, {"c"}, False))
        self.assertFalse(due(keyed, None, today, set(), False))
        self.assertTrue(due(keyed, None, today, {"d"}, False))
        self.assertTrue(due(monthly, dt.date(2026, 9, 20), today, set(), True))


class Parsers(unittest.TestCase):
    """Each parser on an invented snippet in the shape the probes of 28 September 2026 showed."""

    def test_diyanet_rows(self):
        page = ("<table><tr><td>28 Eylül 2026 Pazartesi</td><td>16 Rebiülahir 1448</td><td>05:11</td><td>06:38</td>"
                "<td>12:59</td><td>16:19</td><td>19:09</td><td>20:30</td></tr>"
                "<tr><td>1 Ocak 2027 Cuma</td><td>x</td><td>06:40</td><td>08:20</td><td>13:12</td><td>15:29</td><td>17:53</td><td>19:19</td></tr></table>")
        rows = diyanet.parse_page(page)
        self.assertEqual(["2026-09-28", "2027-01-01"], sorted(rows))
        self.assertEqual(["05:11", "06:38", "12:59", "16:19", "19:09", "20:30"], rows["2026-09-28"])
        self.assertEqual("MUNCHEN", diyanet.norm("Münçhen"))
        self.assertEqual("KOBENHAVN", diyanet.norm("København"))

    def test_egypt_month_table_and_daily(self):
        page = ("<div>مواقيت الصلاة خلال الشهر اليوم الفجر الشروق الظهر العصر المغرب العشاء "
                "01 سبتمبر 2026 م 4:59 ص 6:29 ص 12:53 م 4:27 م 7:17 م 8:41 م "
                "02 سبتمبر 2026 م 5:00 ص 6:30 ص 12:52 م 4:26 م 7:16 م 8:40 م</div>")
        rows = egypt.month_table(page)
        self.assertEqual(["2026-09-01", "2026-09-02"], sorted(rows))
        self.assertEqual(["04:59", "06:29", "12:53", "16:27", "19:17", "20:41"], rows["2026-09-01"])
        daily = ("<p>المدينة التاريخ الميلادي التاريخ الهجري فجر شروق ظهر عصر مغرب عشاء القاهرة 2026-09-28 16 ربيـــــــع الثانى 1448 "
                 "4:22 ص 5:49 ص 11:47 ص 3:13 م 5:44 م 7:02 م الأسكندرية 2026-09-28 16 ربيـــــــع الثانى 1448 4:27 ص 5:53 ص 11:52 ص 3:16 م 5:50 م 7:09 م</p>")
        out = egypt.esa_day(daily)
        self.assertEqual(2, len(out))
        self.assertEqual(("القاهرة", "2026-09-28"), out[0][:2])
        self.assertEqual(["04:22", "05:49", "11:47", "15:13", "17:44", "19:02"], out[0][2])
        self.assertEqual("alexandria", {egypt.norm_city(n): u for u, n in egypt.TOWNS}.get(egypt.norm_city("الأسكندرية"), None) or
                         {egypt.norm_city(k): v for k, v in egypt.ALIASES.items()}[egypt.norm_city("الأسكندرية")])

    def test_jakim(self):
        body = json.dumps({"prayerTime": [{"date": "01-Jan-2026", "fajr": "05:59:00", "syuruk": "07:19:00", "dhuhr": "13:16:00",
                                           "asr": "16:39:00", "maghrib": "19:14:00", "isha": "20:29:00"},
                                          {"date": "01-Mac-2026", "fajr": "06:12:00", "syuruk": "07:25:00", "dhuhr": "13:22:00",
                                           "asr": "16:35:00", "maghrib": "19:24:00", "isha": "20:33:00"}], "serverTime": "x"}).encode()
        rows, _ = jakim.parse_esolat(body)
        self.assertEqual(["2026-01-01", "2026-03-01"], sorted(rows))
        self.assertEqual(["05:59:00", "07:19:00", "13:16:00", "16:39:00", "19:14:00", "20:29:00"], rows["2026-01-01"])
        mirror = json.dumps({"prayers": [{"day": 1, "fajr": 1798756800, "syuruk": 1798761600, "dhuhr": 1798783200,
                                          "asr": 1798795200, "maghrib": 1798804800, "isha": 1798809600}]}).encode()
        m = jakim.parse_mirror(mirror, 2027, 1)
        self.assertEqual(["2027-01-01"], sorted(m))
        self.assertEqual(6, len(m["2027-01-01"]))

    def test_muis(self):
        self.assertEqual("05:44", muis.h24("5:44", 0))
        self.assertEqual("13:06", muis.h24("1:06", 2))
        self.assertEqual("19:12", muis.h24("7:12pm", 5))
        self.assertEqual("00:05", muis.h24("12:05am", 0))
        self.assertEqual("2026-01-01", muis.date_of("2026-01-01"))
        self.assertEqual("2026-01-01", muis.date_of("01/01/2026"))
        rows = muis.parse_records([{"_id": 1, "Date": "2026-01-01", "Day": "Thu", "Subuh": "5:44", "Syuruk": "7:07", "Zohor": "1:06",
                                    "Asar": "4:29", "Maghrib": "7:11", "Isyak": "8:26"}])
        self.assertEqual(["05:44", "07:07", "13:06", "16:29", "19:11", "20:26"], rows["2026-01-01"])
        web = muis.parse_website(json.dumps({"2026-01-02": {"hijri_date": "x", "subuh": "5:44am", "syuruk": "7:07am", "zohor": "1:06pm",
                                                            "asar": "4:29pm", "maghrib": "7:11pm", "isyak": "8:26pm"}}).encode())
        self.assertEqual(["05:44", "07:07", "13:06", "16:29", "19:11", "20:26"], web["2026-01-02"])

    def test_morocco(self):
        page = ('<table id="horaire"><tr><th>الأيام</th><th>ربيع الآخر</th><th>شتنبر / أكتوبر</th><th>الصبح</th><th>الشروق</th>'
                "<th>الظهر</th><th>العصر</th><th>المغرب</th><th>العشاء</th></tr>"
                "<tr><td>الأحد</td><td>1</td><td>13</td><td>05:23</td><td>06:48</td><td>12:33</td><td>16:00</td><td>18:11</td><td>19:29</td></tr>"
                "<tr><td>الجمعة</td><td>20</td><td>2</td><td>05:35</td><td>07:00</td><td>12:27</td><td>15:44</td><td>17:47</td><td>19:05</td></tr></table>")
        rows = morocco.parse(page, dt.date(2026, 9, 28))
        self.assertEqual(["2026-09-13", "2026-10-02"], sorted(rows))
        self.assertEqual(["05:23", "06:48", "12:33", "16:00", "18:11", "19:29"], rows["2026-09-13"])

    def test_mawaqit_takes_the_last_columns(self):
        six = {"calendar": [{"1": ["05:00", "07:00", "12:30", "15:00", "17:30", "19:00"]}]}
        seven = {"calendar": [{"1": ["04:40", "05:00", "07:00", "12:30", "15:00", "17:30", "19:00"]}]}
        self.assertEqual(["05:00", "07:00", "12:30", "15:00", "17:30", "19:00"], mawaqit.calendar_rows(six, 2026)["2026-01-01"])
        self.assertEqual(["05:00", "07:00", "12:30", "15:00", "17:30", "19:00"], mawaqit.calendar_rows(seven, 2026)["2026-01-01"])
        self.assertEqual(7, len(mawaqit.calendar_rows(seven, 2026, 7)["2026-01-01"]))
        page = 'x confData = {"name": "M", "calendar": [{"1": ["05:00", "07:00", "12:30", "15:00", "17:30", "19:00"]}], "s": "a}b"}; y'
        self.assertEqual("M", mawaqit.conf_data(page)["name"])

    def test_qatar(self):
        body = json.dumps({"gregorianDate": {"year": 2026, "month": 9, "day": 28},
                           "times": [{"prayerTimeName": n, "time": {"hour": h, "minutes": m}} for n, h, m in
                                     [("Fajr", 4, 12), ("Sunrise", 5, 31), ("Jummah", 11, 33), ("Asr", 14, 58), ("Maghrib", 17, 36), ("Isha", 19, 6)]]}).encode()
        date, vals = qatar.ministry_day(body)
        self.assertEqual("2026-09-28", date)
        self.assertEqual(["04:12", "05:31", "11:33", "14:58", "17:36", "19:06"], vals)
        self.assertEqual("11:34", qatar.plus_minute("11:33"))
        self.assertEqual("12:00", qatar.plus_minute("11:59"))
        page = ('<script>var prayData = [{"cityId":7,"fajr":"4:05","shrouq":"5:26","thahr":"11:26","aser":"2:51","moghreb":"5:31","ishaa":"7:01"},'
                '{"cityId":3,"fajr":"4:12","shrouq":"5:31","thahr":"11:33","aser":"2:58","moghreb":"5:36","ishaa":"7:06"}];'
                'var calData = {"year":"1448","days":[{"month":4,"days":[{"h":"١٦","m":"28","today":true}]}]};</script>')
        date, vals = qatar.header(page, dt.date(2026, 9, 28))
        self.assertEqual(["04:12", "05:31", "11:33", "14:58", "17:36", "19:06"], vals)
        self.assertEqual(10, len(date))


if __name__ == "__main__":
    unittest.main()
