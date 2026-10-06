#!/usr/bin/env python3
"""The fetch half's unit tests, on invented data (never a printed time from a restricted table):

    python3 -m unittest tools/timetables/monitor/tests.py
"""
import calendar
import datetime as dt
import json
import os
import re
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import common  # noqa: E402
from common import Context, FetchError, Http, Table, add_all, nearest_year, norm_time, pm, read_table_rows, redact, runtime_line, write_atomic  # noqa: E402
import backup  # noqa: E402
import fetch  # noqa: E402
from fetch import CADENCES, INDEX_HEADER, Store, due, skip_reason  # noqa: E402
from fetchers import diyanet, egypt, irn, jakim, jordan, kemenag, london, masjidal, mawaqit, mjc, morocco, muis, qatar, qmdb, toronto  # noqa: E402
from prove_tests import Proof, RecipesCatalogue  # noqa: E402,F401  (prove's half: proof.py and recipes.tsv)


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
        # Another source's table not fetched this run stays in the index, with the date its content last changed.
        later.data["tables"]["other/x"] = dict(later.data["tables"]["src/k"], source="other", key="x", path="archive/tables/monitor/other/x.txt", fetched="2026-09-28")
        lines = later.index_lines()
        self.assertEqual("\t".join(INDEX_HEADER), lines[0])
        self.assertNotIn("status", INDEX_HEADER, "what a run found is the fetch log's, not the index's (review I8, M11)")
        rows = {l.split("\t")[0] + "/" + l.split("\t")[1]: l.split("\t") for l in lines[1:]}
        self.assertEqual("2026-10-12", rows["src/k"][INDEX_HEADER.index("fetched")])
        self.assertEqual("2026-09-28", rows["other/x"][INDEX_HEADER.index("fetched")])
        self.assertEqual("gb.cautious", rows["src/k"][INDEX_HEADER.index("entry")])
        self.assertEqual("", rows["src/k"][INDEX_HEADER.index("survey")])
        index = later.write_index()
        self.assertTrue(os.path.exists(os.path.join(self.root, "archive", "tables", "monitor", "index.tsv")))
        # Written again with the same content: the file is left alone (a mirror would copy it every week otherwise).
        before = os.stat(index).st_mtime_ns
        os.utime(index, ns=(before - 10_000_000_000, before - 10_000_000_000))
        later.write_index()
        self.assertEqual(before - 10_000_000_000, os.stat(index).st_mtime_ns)
        self.assertFalse(any(n.endswith(".tmp") for n in os.listdir(os.path.dirname(index))))

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
        # A source read by hand never runs, named or not (review I6: naming it reported a broken fetch).
        self.assertFalse(due(manual, None, today, {"c"}, False))
        self.assertEqual("fetched by hand", skip_reason(manual, None, today, {"c"}, False))
        self.assertFalse(due(keyed, None, today, set(), False))
        self.assertEqual("runs only when named", skip_reason(keyed, None, today, set(), False))
        self.assertTrue(due(keyed, None, today, {"d"}, False))
        self.assertTrue(due(monthly, dt.date(2026, 9, 20), today, set(), True))
        self.assertEqual("not due", skip_reason(monthly, dt.date(2026, 9, 20), today, set(), False))
        self.assertEqual(None, skip_reason(weekly, None, today, set(), False))


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

    def test_egypt_each_prayer_keeps_its_own_half_of_the_day(self):
        # After the clock goes back Dhuhr falls before noon and the page still labels it م: it is
        # morning, not night (the 720-minute "early" Dhuhr of 30-31 October 2026). Invented times.
        page = ("<div>مواقيت الصلاة خلال الشهر اليوم الفجر الشروق الظهر العصر المغرب العشاء "
                "29 أكتوبر 2026 م 5:13 ص 6:41 ص 12:38 م 3:51 م 6:22 م 7:44 م "
                "30 أكتوبر 2026 م 4:14 ص 5:42 ص 11:38 م 2:51 م 5:21 م 6:43 م "
                "31 أكتوبر 2026 م 4:15 ص 5:43 ص 11:37 ص 2:50 ص 5:20 م 6:42 م</div>")
        rows = egypt.month_table(page)
        self.assertEqual(["05:13", "06:41", "12:38", "15:51", "18:22", "19:44"], rows["2026-10-29"])
        self.assertEqual(["04:14", "05:42", "11:38", "14:51", "17:21", "18:43"], rows["2026-10-30"])
        self.assertEqual(["04:15", "05:43", "11:37", "14:50", "17:20", "18:42"], rows["2026-10-31"])
        daily = ("<p>المدينة التاريخ الميلادي التاريخ الهجري فجر شروق ظهر عصر مغرب عشاء القاهرة 2026-10-30 18 جمادى الأولى 1448 "
                 "4:14 ص 5:42 ص 11:38 م 2:51 م 5:21 م 6:43 م</p>")
        self.assertEqual(["04:14", "05:42", "11:38", "14:51", "17:21", "18:43"], egypt.esa_day(daily)[0][2])

    def test_egypt_h24_by_prayer(self):
        h = egypt.h24
        self.assertEqual("00:30", h("12:30", egypt.MORNING))
        self.assertEqual("04:05", h("4:05", egypt.MORNING))
        self.assertEqual("10:59", h("10:59", egypt.MIDDAY))
        self.assertEqual("11:40", h("11:40", egypt.MIDDAY))
        self.assertEqual("12:10", h("12:10", egypt.MIDDAY))
        self.assertEqual("13:02", h("1:02", egypt.MIDDAY))
        self.assertEqual("15:20", h("3:20", egypt.AFTERNOON))
        self.assertEqual("20:01", h("20:01", egypt.AFTERNOON))
        self.assertEqual(6, len(egypt.HALVES))

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

    def test_qmdb(self):
        # Invented times (ruling R69): the hour is the field's place, the minutes the day's number.
        days = [{"Date": f"2027-01-0{d}", "imsak": "-", **{k: f"{i:02d}:{d:02d}" for i, k in enumerate(qmdb.FIELDS)},
                 "sunset": "-", "midnight": "-"} for d in (1, 2)]
        rows = qmdb.parse(json.dumps({"result": days, "latitude": "1", "longitude": "2", "year": 2027, "city": None}).encode())
        self.assertEqual(["2027-01-01", "2027-01-02"], sorted(rows))
        self.assertEqual(["00:02", "01:02", "02:02", "03:02", "04:02", "05:02"], rows["2027-01-02"])
        with self.assertRaises(FetchError):
            qmdb.parse(b'{"detail": "Not found."}')
        self.assertEqual("kz-qmdb", qmdb.SOURCE)
        self.assertEqual({"almaty", "astana", "kokshetau", "kostanay", "pavlodar", "petropavl"}, {p[0] for p in qmdb.POINTS})
        self.assertTrue(all(float(p[2]) >= 48.0 for p in qmdb.POINTS if p[0] != "almaty"), "every point but Almaty is in the north variant")

    def test_qatar(self):
        body = json.dumps({"gregorianDate": {"year": 2026, "month": 9, "day": 28},
                           "times": [{"prayerTimeName": n, "time": {"hour": h, "minutes": m}} for n, h, m in
                                     [("Fajr", 4, 12), ("Sunrise", 5, 31), ("Jummah", 11, 33), ("Asr", 14, 58), ("Maghrib", 17, 36), ("Isha", 19, 6)]]}).encode()
        date, vals = qatar.ministry_day(body)
        self.assertEqual("2026-09-28", date)
        self.assertEqual(["04:12", "05:31", "11:33", "14:58", "17:36", "19:06"], vals)
        self.assertEqual("11:34", qatar.plus_minute("11:33"))
        self.assertEqual("12:00", qatar.plus_minute("11:59"))
        # The site's calendar flags today; the fetcher holds it against Doha's own date on the clock
        # (UTC+3), so the invented calendar takes its day from that clock, not a fixed date.
        doha = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=3)).date()
        page = ('<script>var prayData = [{"cityId":7,"fajr":"4:05","shrouq":"5:26","thahr":"11:26","aser":"2:51","moghreb":"5:31","ishaa":"7:01"},'
                '{"cityId":3,"fajr":"4:12","shrouq":"5:31","thahr":"11:33","aser":"2:58","moghreb":"5:36","ishaa":"7:06"}];'
                'var calData = {"year":"1448","days":[{"month":4,"days":[{"h":"١٦","m":"%d","today":true}]}]};</script>' % doha.day)
        date, vals = qatar.header(page, doha)
        self.assertEqual(["04:12", "05:31", "11:33", "14:58", "17:36", "19:06"], vals)
        self.assertEqual(10, len(date))


if __name__ == "__main__":
    unittest.main()


class Politeness(unittest.TestCase):
    """The client without a network: a fake urlopen, no waits (review I5 the key, M3 the breaker and the budget)."""

    def setUp(self):
        self.calls = []
        self.lines = []
        self._urlopen = common.urllib.request.urlopen
        self._sleep = common.time.sleep
        self._interval = common.MIN_INTERVAL
        common.time.sleep = lambda s: None
        common.MIN_INTERVAL = 0

    def tearDown(self):
        common.urllib.request.urlopen = self._urlopen
        common.time.sleep = self._sleep
        common.MIN_INTERVAL = self._interval

    def fake(self, outcome):
        def urlopen(req, timeout=None):
            self.calls.append(req.full_url)
            raise outcome(req.full_url)
        common.urllib.request.urlopen = urlopen

    def test_redact_masks_a_key_and_leaves_the_rest(self):
        self.assertEqual("https://x/api/?format=json&key=***&year=2027", redact("https://x/api/?format=json&key=SECRET&year=2027"))
        self.assertEqual("https://x/api/?token=***", redact("https://x/api/?token=abc"))
        self.assertEqual("https://x/api/?q=a+b%20c&key=***", redact("https://x/api/?q=a+b%20c&key=SECRET"))
        self.assertEqual("https://x/page", redact("https://x/page"))
        self.assertNotIn("SECRET", redact("https://x/?API_KEY=SECRET&apikey=SECRET&access_token=SECRET"))

    def test_an_http_error_names_the_url_without_its_key(self):
        self.fake(lambda url: common.urllib.error.HTTPError(url, 401, "no", {}, None))
        http = Http(self.lines.append)
        with self.assertRaises(FetchError) as caught:
            http.get("https://x/api/?format=json&key=SECRET&year=2027")
        self.assertIn("HTTP 401 from https://x/api/?format=json&key=***&year=2027", str(caught.exception))
        self.assertNotIn("SECRET", str(caught.exception))
        self.assertEqual(1, len(self.calls), "an HTTP 401 is not retried")

    def test_the_breaker_stops_a_host_after_three_network_failures(self):
        self.fake(lambda url: common.urllib.error.URLError("timed out"))
        http = Http(self.lines.append)
        with self.assertRaises(FetchError):
            http.get("https://a.example/1")  # two attempts: failures 1 and 2
        with self.assertRaises(FetchError) as third:
            http.get("https://a.example/2")  # failure 3 trips the breaker, no retry
        self.assertEqual(3, len(self.calls))
        self.assertIn("timed out", str(third.exception))
        with self.assertRaises(FetchError) as skipped:
            http.get("https://a.example/3")
        self.assertEqual(3, len(self.calls), "a tripped host is not tried")
        self.assertIn("not tried: a.example failed 3 times in a row", str(skipped.exception))
        self.assertTrue(any("BREAKER a.example" in l for l in self.lines))
        # Another host is unaffected.
        with self.assertRaises(FetchError):
            http.get("https://b.example/1")
        self.assertEqual(5, len(self.calls))

    def test_the_run_budget_stops_every_request_once_spent(self):
        self.fake(lambda url: common.urllib.error.URLError("unreachable"))
        http = Http(self.lines.append, deadline=common.time.monotonic() - 1)
        self.assertTrue(http.budget_spent())
        with self.assertRaises(FetchError) as caught:
            http.get("https://a.example/1")
        self.assertIn("time budget is spent", str(caught.exception))
        self.assertEqual(0, len(self.calls))

    def test_a_context_counts_its_own_requests_on_the_shared_client(self):
        self.fake(lambda url: common.urllib.error.HTTPError(url, 404, "no", {}, None))
        http = Http(self.lines.append)
        a = Context("a", "/nowhere", dt.date(2026, 10, 5), self.lines.append, "/nowhere/monitor", http=http)
        for _ in range(2):
            with self.assertRaises(FetchError):
                a.http.get("https://a.example/x")
        b = Context("b", "/nowhere", dt.date(2026, 10, 5), self.lines.append, "/nowhere/monitor", http=http)
        with self.assertRaises(FetchError):
            b.http.get("https://b.example/x")
        self.assertEqual(2, a.requests)
        self.assertEqual(1, b.requests)
        self.assertEqual(3, http.requests)

    def test_keys_come_from_the_environment_or_the_file(self):
        root = tempfile.mkdtemp("monitor-keys")
        try:
            ctx = Context("a", root, dt.date(2026, 10, 5), self.lines.append, root)
            had = os.environ.pop("LPT_API_KEY", None)
            try:
                self.assertEqual({}, ctx.keys_env())
                with open(os.path.join(root, "keys.env"), "w") as f:
                    f.write("# a comment\nLPT_API_KEY = fromfile\n")
                self.assertEqual("fromfile", ctx.keys_env()["LPT_API_KEY"])
                os.environ["LPT_API_KEY"] = "fromenv"
                self.assertEqual("fromenv", ctx.keys_env()["LPT_API_KEY"])
            finally:
                if had is None:
                    os.environ.pop("LPT_API_KEY", None)
                else:
                    os.environ["LPT_API_KEY"] = had
        finally:
            shutil.rmtree(root)

    def test_the_runtime_line_names_the_interpreter_and_tls(self):
        self.assertIn("python 3.", runtime_line())
        self.assertTrue("SSL" in runtime_line())


class Writes(unittest.TestCase):
    def test_write_atomic_leaves_no_temp_file(self):
        root = tempfile.mkdtemp("monitor-atomic")
        try:
            path = os.path.join(root, "deep", "file.txt")
            write_atomic(path, "one\n")
            write_atomic(path, "two\n")
            with open(path) as f:
                self.assertEqual("two\n", f.read())
            write_atomic(os.path.join(root, "b.bin"), b"\x00\x01")
            with open(os.path.join(root, "b.bin"), "rb") as f:
                self.assertEqual(b"\x00\x01", f.read())
            self.assertEqual(["file.txt"], os.listdir(os.path.join(root, "deep")))
        finally:
            shutil.rmtree(root)

    def test_add_all_skips_a_bad_cell_and_keeps_the_rest(self):
        notes = []
        ctx = Context("a", "/nowhere", dt.date(2026, 10, 5), lambda line: None, "/nowhere/monitor")
        ctx.note = notes.append
        t = Table("k", "n", None, None, "UTC", "XX", "F S D A M I")
        added = add_all(ctx, t, {"2026-01-01": ["05:00", "06:30", "12:00", "15:00", "18:00", "19:30"],
                                 "2026-01-02": ["5h00", "06:30", "12:00", "15:00", "18:00", "19:30"]}, "n")
        self.assertEqual(1, added)
        self.assertEqual(["2026-01-01"], sorted(t.rows))
        self.assertEqual(1, len(notes))
        self.assertIn("2026-01-02", notes[0])
        # The cell's own text never reaches a message (a printed time in another notation would otherwise).
        self.assertIn("cell 1 is not a time", notes[0])
        self.assertNotIn("5h00", notes[0])
        with self.assertRaises(FetchError) as caught:
            muis.h24("7h12", 0)
        self.assertNotIn("7h12", str(caught.exception))


class Drivers(unittest.TestCase):
    """fetch.py and backup.py end to end on a throwaway root, without a network."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-driver")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")
        self.sources = os.path.join(self.root, "sources.tsv")
        with open(self.sources, "w") as f:
            f.write("source\tentries\tfetcher\tcadence\tnext_expected\tpoints\tnote\n")
            f.write("ae-iacad\tae.iacad.dubai\tmanual\tmanual\t2027-01\tp\tn\n")
            f.write("gb-london-lupt\tgb.london.lupt\tlondon\tmanual\t2026-12-01\tp\tn\n")
            f.write("za-jamiat\tza.jamiat\tjamiat\tmonthly\t-\tp\tn\n")
        self.had = os.environ.pop("LPT_API_KEY", None)

    def tearDown(self):
        shutil.rmtree(self.root)
        if self.had is not None:
            os.environ["LPT_API_KEY"] = self.had

    def outcome(self):
        with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
            return json.load(f)

    def test_named_manual_sources_are_skipped_not_failed_and_no_path_is_written(self):
        code = fetch.main(["--official", self.root, "--sources", self.sources, "--today", "2026-10-05",
                           "--only", "ae-iacad", "--only", "gb-london-lupt"])
        self.assertEqual(0, code)
        out = self.outcome()
        self.assertEqual("2026-10-05", out["date"])
        self.assertNotIn("official", out)
        self.assertEqual({"status": "skipped", "message": "fetched by hand", "requests": 0, "tables": {}}, out["sources"]["ae-iacad"])
        self.assertEqual("ok", out["sources"]["gb-london-lupt"]["status"])
        self.assertEqual(0, out["sources"]["gb-london-lupt"]["requests"])
        self.assertIn("no LPT_API_KEY", out["sources"]["gb-london-lupt"]["message"])
        self.assertEqual("not selected", out["sources"]["za-jamiat"]["message"])
        with open(os.path.join(self.monitor, "fetch", "2026-10-05.log")) as f:
            self.assertIn("python 3.", f.readline())

    def test_a_spent_budget_records_the_rest_as_not_fetched(self):
        # No --force: the monthly source is due on its own (never fetched), the manual-cadence one is not run.
        code = fetch.main(["--official", self.root, "--sources", self.sources, "--today", "2026-10-05", "--budget-minutes", "0.0000001"])
        self.assertEqual(0, code)
        out = self.outcome()
        self.assertEqual("failed", out["sources"]["za-jamiat"]["status"])
        self.assertIn("time budget", out["sources"]["za-jamiat"]["message"])
        self.assertEqual("runs only when named", out["sources"]["gb-london-lupt"]["message"])

    def test_a_broken_driver_writes_its_error_and_exits_2(self):
        code = fetch.main(["--official", self.root, "--sources", os.path.join(self.root, "missing.tsv"), "--today", "2026-10-05"])
        self.assertEqual(2, code)
        out = self.outcome()
        self.assertIn("FileNotFoundError", out["error"])
        self.assertEqual({}, out["sources"])

    def test_backup_mirrors_and_reports_a_missing_folder(self):
        with open(os.path.join(self.root, "archive", "a.txt"), "w") as f:
            f.write("x")
        target = os.path.join(self.root, "backup")
        code = backup.main(["--official", self.root, "--backup", target, "--today", "2026-10-05"])
        self.assertEqual(2, code)
        with open(os.path.join(self.monitor, "backup.json")) as f:
            record = json.load(f)
        self.assertEqual("2026-10-05", record["date"])
        self.assertIn("no backup archive", record["error"])
        os.makedirs(os.path.join(target, "archive"))
        self.assertEqual(0, backup.main(["--official", self.root, "--backup", target, "--today", "2026-10-05"]))
        with open(os.path.join(self.monitor, "backup.json")) as f:
            record = json.load(f)
        self.assertEqual(1, record["copied"])
        self.assertNotIn("error", record)
        self.assertEqual(2, backup.main(["--official", self.root, "--backup", "", "--today", "2026-10-05"]))
        with open(os.path.join(self.monitor, "backup.json")) as f:
            self.assertIn("no backup folder given", json.load(f)["error"])

    def test_without_a_root_neither_half_guesses_one(self):
        # Review M5 (final): no one's home folder is a default in the public repository; the root
        # comes from --official or TAQWA_OFFICIAL (scripts/monitor.sh passes it), else exit 2.
        had = os.environ.pop("TAQWA_OFFICIAL", None)
        try:
            self.assertEqual(2, fetch.main(["--sources", self.sources, "--today", "2026-10-05"]))
            self.assertEqual(2, backup.main(["--backup", os.path.join(self.root, "backup"), "--today", "2026-10-05"]))
            self.assertFalse(os.path.exists(self.monitor))
            os.environ["TAQWA_OFFICIAL"] = self.root
            self.assertEqual(0, fetch.main(["--sources", self.sources, "--today", "2026-10-05", "--only", "ae-iacad"]))
            self.assertEqual("skipped", self.outcome()["sources"]["ae-iacad"]["status"])
        finally:
            os.environ.pop("TAQWA_OFFICIAL", None)
            if had is not None:
                os.environ["TAQWA_OFFICIAL"] = had


class MoreParsers(unittest.TestCase):
    def test_kemenag_checks_the_place_name(self):
        body = json.dumps({"status": True, "data": {"lokasi": "KOTA SURABAYA", "jadwal": [
            {"date": "2026-10-01", "subuh": "04:00", "terbit": "05:15", "dzuhur": "11:30", "ashar": "14:45", "maghrib": "17:35", "isya": "18:45"}]}}).encode()
        rows, lokasi = kemenag.parse(body, expected="Kota Surabaya")
        self.assertEqual(["2026-10-01"], sorted(rows))
        self.assertEqual("KOTA SURABAYA", lokasi)
        self.assertEqual("KABJAYAWIJAYA", kemenag.fold("Kab. Jayawijaya"))
        with self.assertRaises(FetchError) as caught:
            kemenag.parse(body, expected="Kota Medan")
        self.assertIn("KOTA SURABAYA", str(caught.exception))
        self.assertNotIn("(new)", str([p[2] for p in kemenag.PLACES]))

    def test_muis_records_skip_a_bad_cell_when_asked(self):
        records = [{"Date": "2026-01-01", "Subuh": "5:44", "Syuruk": "7:07", "Zohor": "1:06", "Asar": "4:29", "Maghrib": "7:11", "Isyak": "8:26"},
                   {"Date": "2026-01-02", "Subuh": "5h44", "Syuruk": "7:07", "Zohor": "1:06", "Asar": "4:29", "Maghrib": "7:11", "Isyak": "8:26"}]
        with self.assertRaises(FetchError):
            muis.parse_records(records)
        bad = []
        rows = muis.parse_records(records, bad)
        self.assertEqual(["2026-01-01"], sorted(rows))
        self.assertEqual(1, len(bad))

    def test_london_month_needs_every_field(self):
        good = json.dumps({"times": {"2027-01-01": {"fajr": "06:20", "sunrise": "08:06", "dhuhr": "12:07", "asr": "13:37", "magrib": "16:09", "isha": "17:40", "asr_2": "14:15"}}}).encode()
        self.assertEqual(["06:20", "08:06", "12:07", "13:37", "16:09", "17:40", "14:15"], london.month_rows(good)["2027-01-01"])
        missing = json.dumps({"times": {"2027-01-01": {"fajr": "06:20", "sunrise": "08:06", "dhuhr": "12:07", "asr": "13:37", "magrib": "16:09", "isha": "17:40"}}}).encode()
        with self.assertRaises(FetchError) as caught:
            london.month_rows(missing)
        self.assertIn("no asr_2", str(caught.exception))

    def test_irn_page_and_month(self):
        page = ('<script>var prayerAjax = {"ajaxurl":"https://bonnetid.info/wp-admin/admin-ajax.php","nonce":"abc123"};</script>'
                '<select name="city"><option value="1">Oslo</option><option value="7">Trondheim</option><option value="9">Tromsø</option></select>')
        ajaxurl, nonce, cities = irn.page_config(page)
        self.assertEqual("https://bonnetid.info/wp-admin/admin-ajax.php", ajaxurl)
        self.assertEqual("abc123", nonce)
        self.assertEqual({"oslo": "1", "trondheim": "7", "tromso": "9"}, cities)
        month = json.dumps({"success": True, "data": {"html": "<h3>Januar 2027</h3><table>"
                            "<tr><td>1</td><td>x</td><td>06:40</td><td>09:15</td><td>12:25</td><td>13:20</td><td>13:50</td><td>15:30</td><td>17:15</td></tr>"
                            "<tr><td>2</td><td>x</td><td>06:39</td><td>09:14</td><td>12:26</td><td>13:21</td><td>13:52</td><td>15:32</td><td>17:16</td></tr></table>"}}).encode()
        rows, year = irn.parse_month(month, 1)
        self.assertEqual("2027", year)
        self.assertEqual([(1, ["06:40", "09:15", "12:25", "13:20", "15:30", "17:15", "13:50"]), (2, ["06:39", "09:14", "12:26", "13:21", "15:32", "17:16", "13:52"])], rows)
        # A reply for another month than the one asked for is refused: by the month it names, else by its day count.
        with self.assertRaises(FetchError) as named:
            irn.parse_month(month, 2)
        self.assertIn("names month 1, not 2", str(named.exception))
        unnamed = json.dumps({"success": True, "data": {"html": "<h3>2027</h3><table>"
                              "<tr><td>1</td><td>x</td><td>06:40</td><td>09:15</td><td>12:25</td><td>13:20</td><td>13:50</td><td>15:30</td><td>17:15</td></tr></table>"}}).encode()
        with self.assertRaises(FetchError) as counted:
            irn.parse_month(unnamed, 3)
        self.assertIn("has 1 days, month 3 of 2027 has 31", str(counted.exception))
        with self.assertRaises(FetchError):
            irn.page_config("<html>nothing</html>")
        # The AJAX URL must be the site's own.
        elsewhere = page.replace("https://bonnetid.info/wp-admin/admin-ajax.php", "https://evil.example/admin-ajax.php")
        with self.assertRaises(FetchError) as host:
            irn.page_config(elsewhere)
        self.assertIn("evil.example, not bonnetid.info", str(host.exception))
        self.assertNotIn("(new)", str([c[1] for c in diyanet.EUROPE]))


class FakeHttp:
    """A client that answers from a table of URL -> body (bytes) or an exception, for fetchers under test."""

    def __init__(self, answers):
        self.answers = answers
        self.urls = []
        self.requests = 0
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1):
        self.urls.append(url)
        self.requests += 1
        for prefix, answer in self.answers:
            if url.startswith(prefix):
                if isinstance(answer, Exception):
                    raise answer
                return answer
        raise FetchError(f"no canned answer for {redact(url)}")

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8", "replace")

    def json(self, url, **kw):
        return json.loads(self.get(url, **kw).decode("utf-8", "replace"))


class RoundTwo(unittest.TestCase):
    """The second fix round: a key with a newline (I5), a partial fetch (N7), the id cache (M7)."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-two")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")
        os.makedirs(self.monitor)
        self.lines = []
        self.had = os.environ.pop("LPT_API_KEY", None)
        self._urlopen = common.urllib.request.urlopen
        self._sleep = common.time.sleep
        self._interval = common.MIN_INTERVAL
        common.time.sleep = lambda s: None
        common.MIN_INTERVAL = 0
        common.SECRETS.clear()

    def tearDown(self):
        shutil.rmtree(self.root)
        if self.had is not None:
            os.environ["LPT_API_KEY"] = self.had
        else:
            os.environ.pop("LPT_API_KEY", None)
        common.urllib.request.urlopen = self._urlopen
        common.time.sleep = self._sleep
        common.MIN_INTERVAL = self._interval
        common.SECRETS.clear()

    def ctx(self, source="x", http=None):
        return Context(source, self.root, dt.date(2026, 10, 5), self.lines.append, self.monitor, http=http)

    def test_a_key_with_a_trailing_newline_is_stripped_quoted_and_never_written(self):
        # The owner pastes the secret with a trailing newline and an inner space; the client then refuses the
        # request as InvalidURL, whose text quotes the whole request path. Nothing written may hold the key.
        os.environ["LPT_API_KEY"] = "SEKRET KEY\n"
        seen = []

        def urlopen(req, timeout=None):
            seen.append(req.full_url)
            raise common.http.client.InvalidURL("URL can't contain control characters. " + repr(req.full_url) + " and SEKRET KEY")
        common.urllib.request.urlopen = urlopen
        store = Store(self.root, self.monitor, dt.date(2026, 10, 5))
        ctx = self.ctx("gb-london-lupt")
        status, message, requests, statuses = fetch.run_source({"source": "gb-london-lupt", "fetcher": "london"}, store, ctx)
        store.source_done("gb-london-lupt", status, message, requests)
        self.assertEqual("failed", status)
        self.assertEqual({}, statuses)
        self.assertEqual(12, len(seen))
        self.assertTrue(all("key=SEKRET%20KEY&" in u and "\n" not in u for u in seen), seen[0])
        for text in [message, "\n".join(self.lines), json.dumps(store.data)]:
            self.assertNotIn("SEKRET", text)
            self.assertNotIn("SEKRET%20KEY", text)
        self.assertIn("InvalidURL for https://www.londonprayertimes.com/api/times/?format=json&key=***&year=", message)

    def test_scrub_masks_a_loaded_key_in_every_form(self):
        common.remember_secret("abc def\n")
        self.assertEqual("x *** y *** z", common.scrub("x abc def y abc%20def z"))
        self.assertEqual("nothing", common.scrub("nothing"))
        ctx = self.ctx()
        ctx.error("failed with abc def")
        self.assertEqual(["failed with ***"], ctx.errors)
        self.assertTrue(all("abc def" not in l for l in self.lines))

    def test_a_partial_fetch_is_due_once_more_and_a_complete_one_clears_it(self):
        store = Store(self.root, self.monitor, dt.date(2026, 10, 5))
        store.source_done("a", "partial", "one table broke", 3)
        self.assertEqual("2026-10-05", store.data["sources"]["a"]["lastFetch"])
        self.assertEqual("partial", store.data["sources"]["a"]["status"])
        self.assertTrue(store.retry("a"), "due once more next run, whatever the cadence (review R3-M2)")
        store.source_done("a", "ok", "", 3)
        self.assertFalse(store.retry("a"))
        self.assertEqual(None, store.last_fetch("b"))
        self.assertFalse(store.retry("b"))

    def muis_page(self, records, nxt, total):
        return json.dumps({"result": {"records": records, "total": total, "_links": ({"next": nxt} if nxt else {})}}).encode()

    def test_muis_merges_a_dataset_read_in_part_and_replaces_a_complete_one(self):
        rec = {"_id": 1, "Date": "2026-01-01", "Subuh": "5:44", "Syuruk": "7:07", "Zohor": "1:06", "Asar": "4:29", "Maghrib": "7:11", "Isyak": "8:26"}
        rec2 = dict(rec, _id=2, Date="2026-01-02")
        website = json.dumps({"2026-01-02": {"subuh": "5:44am", "syuruk": "7:07am", "zohor": "1:06pm", "asar": "4:29pm", "maghrib": "7:11pm", "isyak": "8:26pm"}}).encode()
        # Page two fails: the year table is merged into what is held, and the website half still runs.
        http = FakeHttp([(muis.DATAGOV, self.muis_page([rec], "/api/page2", 2)), ("https://data.gov.sg/api/page2", FetchError("HTTP 429")), (muis.WEBSITE, website)])
        tables = muis.fetch(self.ctx("sg-muis", http=http))
        dataset = [t for t in tables if t.key == "singapore-2026"]
        self.assertEqual(1, len(dataset))
        self.assertTrue(dataset[0].merge, "a partial read merges")
        self.assertTrue(any("read in part" in n for n in self.lines))
        self.assertTrue(any(t.key == "website-2026" for t in tables))
        # A complete pagination replaces the held table.
        http = FakeHttp([(muis.DATAGOV, self.muis_page([rec], "/api/page2", 2)), ("https://data.gov.sg/api/page2", self.muis_page([rec2], None, 2)), (muis.WEBSITE, website)])
        tables = muis.fetch(self.ctx("sg-muis", http=http))
        dataset = [t for t in tables if t.key == "singapore-2026"][0]
        self.assertFalse(dataset.merge)
        self.assertEqual(["2026-01-01", "2026-01-02"], sorted(dataset.rows))

    def test_a_corrupt_diyanet_id_cache_is_discarded_and_rebuilt_atomically(self):
        path = os.path.join(self.monitor, "diyanet-ids.json")
        with open(path, "w") as f:
            f.write('{"countries": {"13": {"BERL')
        ctx = self.ctx("tr-diyanet-europe")
        ids = diyanet.Ids(ctx)
        self.assertEqual({}, ids.data)
        self.assertEqual(1, sum(1 for n in ctx.notes if "could not be read" in n))
        ids.data = {"cities": {"berlin": "1"}}
        ids.save()
        with open(path) as f:
            self.assertEqual({"cities": {"berlin": "1"}}, json.load(f))
        self.assertFalse(any(n.endswith(".tmp") for n in os.listdir(self.monitor)))
        self.assertEqual({"cities": {"berlin": "1"}}, diyanet.Ids(ctx).data)

    def test_london_quotes_the_key_in_the_url(self):
        with open(os.path.join(self.monitor, "keys.env"), "w") as f:
            f.write("LPT_API_KEY=a b/c\n")
        http = FakeHttp([(london.API, FetchError("HTTP 404"))])
        self.assertEqual([], london.fetch(self.ctx("gb-london-lupt", http=http)))
        self.assertEqual(1, len(http.urls), "a year the API lacks stops after its first month")
        self.assertIn("key=a%20b%2Fc&", http.urls[0])


class RoundThree(unittest.TestCase):
    """The third fix round: a source retried once (R3-M2), IRN's heading month (nit)."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-three")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")

    def tearDown(self):
        shutil.rmtree(self.root)

    def test_a_source_that_fails_or_comes_back_in_part_is_retried_once_then_waits_for_its_cadence(self):
        monthly = {"source": "mawaqit", "fetcher": "mawaqit", "cadence": "monthly"}
        # The cadence counts from the retry (12 Oct): a monthly source is due again from 8 November.
        d1, d2, d3, d4 = (dt.date(2026, 10, 5), dt.date(2026, 10, 12), dt.date(2026, 10, 19), dt.date(2026, 11, 9))
        store = Store(self.root, self.monitor, d1)
        # A partial run: recorded as fetched, and due once more next run.
        self.assertEqual((True, False), store.source_done("mawaqit", "partial", "one mosque: HTTP 404", 125))
        self.assertEqual("2026-10-05", store.data["sources"]["mawaqit"]["lastFetch"])
        self.assertTrue(store.retry("mawaqit"))
        self.assertEqual(None, skip_reason(monthly, store.last_fetch("mawaqit"), d2, set(), False, retry=store.retry("mawaqit")))
        # The retry comes back in part again: no third try, the cadence applies.
        store.today = d2
        self.assertEqual((False, True), store.source_done("mawaqit", "partial", "one mosque: HTTP 404", 125))
        self.assertFalse(store.retry("mawaqit"))
        self.assertEqual("not due", skip_reason(monthly, store.last_fetch("mawaqit"), d3, set(), False, retry=store.retry("mawaqit")))
        self.assertEqual(None, skip_reason(monthly, store.last_fetch("mawaqit"), d4, set(), False, retry=store.retry("mawaqit")))
        # A failed source is treated the same way; a complete fetch clears everything.
        store.today = d4
        self.assertEqual((True, False), store.source_done("mawaqit", "failed", "HTTP 503", 1))
        self.assertTrue(store.retry("mawaqit"))
        self.assertEqual((False, True), store.source_done("mawaqit", "ok", "", 125))
        self.assertFalse(store.retry("mawaqit"))
        self.assertEqual("2026-11-09", store.data["sources"]["mawaqit"]["lastFetch"])
        self.assertEqual((False, False), store.source_done("mawaqit", "ok", "", 125))

    def test_the_driver_says_what_happens_to_a_partial_source(self):
        with open(os.path.join(self.root, "sources.tsv"), "w") as f:
            f.write("source\tentries\tfetcher\tcadence\tnext_expected\tpoints\tnote\n")
            f.write("za-jamiat\tza.jamiat\tjamiat\tmonthly\t-\tp\tn\n")
        sources = os.path.join(self.root, "sources.tsv")
        # A budget already spent makes the source fail: the message says it is retried next run.
        fetch.main(["--official", self.root, "--sources", sources, "--today", "2026-10-05", "--budget-minutes", "0.0000001"])
        with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
            out = json.load(f)
        self.assertEqual("failed", out["sources"]["za-jamiat"]["status"])
        self.assertTrue(out["sources"]["za-jamiat"]["message"].endswith("retried on the next run, once"), out["sources"]["za-jamiat"]["message"])
        # The retry a week later, failing again: it now waits for its cadence.
        fetch.main(["--official", self.root, "--sources", sources, "--today", "2026-10-12", "--budget-minutes", "0.0000001"])
        with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
            out = json.load(f)
        self.assertEqual("failed", out["sources"]["za-jamiat"]["status"])
        self.assertIn("this was the retry; the source now waits for its cadence (monthly)", out["sources"]["za-jamiat"]["message"])
        # A week later still: not due.
        fetch.main(["--official", self.root, "--sources", sources, "--today", "2026-10-19", "--budget-minutes", "0.0000001"])
        with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
            out = json.load(f)
        self.assertEqual({"status": "skipped", "message": "not due", "requests": 0, "tables": {}}, out["sources"]["za-jamiat"])

    def test_irn_takes_the_month_from_the_heading_not_from_prose(self):
        def reply(html):
            return json.dumps({"success": True, "data": {"html": html}}).encode()
        row = "<tr><td>1</td><td>x</td><td>06:40</td><td>09:15</td><td>12:25</td><td>13:20</td><td>13:50</td><td>15:30</td><td>17:15</td></tr>"
        # Prose with "may" before the heading does not decide: the heading's "Januar 2027" does.
        rows, year = irn.parse_month(reply("<p>Times may vary.</p><h3>Januar 2027</h3><table>" + row + "</table>"), 1)
        self.assertEqual(("2027", 1), (year, len(rows)))
        with self.assertRaises(FetchError) as wrong:
            irn.parse_month(reply("<p>Times may vary.</p><h3>Januar 2027</h3><table>" + row + "</table>"), 5)
        self.assertIn("names month 1, not 5", str(wrong.exception))
        # An English heading works too, and so does "May 2027" for May.
        self.assertEqual("2027", irn.parse_month(reply("<h3>May 2027</h3><table>" + row + "</table>"), 5)[1])
        # No heading month: the day count decides (one row is not a month).
        with self.assertRaises(FetchError):
            irn.parse_month(reply("<p>Times may vary.</p><h3>2027</h3><table>" + row + "</table>"), 1)


# The MJC's Salaah Times page as its markup has it (the hidden copy of the today card in the top bar,
# the card shown, the "This Month" table with its caption), every time invented: the day's number as
# the minutes of six hours no Cape Town table prints (Fajr 02, sunrise 03, Dhuhr 10, Asr 13, Maghrib 16,
# Isha 22), in the day's order (ruling R69: never a printed time).
MJC_HEADER = ["Date", "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"]
MJC_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
              "November", "December"]
MJC_WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
TIME_SHAPE = re.compile(r"\b\d{1,2}:\d{2}\b")


def mjc_times(day):
    return [f"{h:02d}:{day:02d}" for h in (2, 3, 10, 13, 16, 22)]


def mjc_label(date, full=False):
    name = MJC_WEEKDAYS[date.weekday()]
    return f"{name if full else name[:3]} {date.day}"


def mjc_card(date, times):
    names = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
    attrs = " ".join(f'data-prayer-{n}="{t}"' for n, t in zip(names, times))
    grid = "".join(f'<div class="mjc-salaah-time" role="listitem"><span>{n.title()}</span><strong>{t}</strong></div>'
                   for n, t in zip(names, times))
    return (f'<div class="mjc-salaah-today" data-salaah-provider="mjc-native" data-hijri-date="an invented date AH" {attrs}>\n'
            f'      <p class="mjc-salaah-date"><strong>{MJC_WEEKDAYS[date.weekday()]}, {date.day} {MJC_MONTHS[date.month - 1]} {date.year}</strong>'
            '<span>an invented date AH</span></p>\n'
            f'      <div class="mjc-salaah-daily-grid" role="list" aria-label="Cape Town Salaah times for today">{grid}</div>\n'
            '      <p class="mjc-salaah-note">Calculated for Cape Town (SAST). Please confirm congregational times with your local Masjid.</p>\n'
            '    </div>')


def mjc_page(year=2026, month=9, days=None, caption=None, header=None, label=mjc_label, cells=None, card_day=None, card_times=None):
    """The page for a month: `days` its rows (every day by default), `label(date)` a row's date cell,
    `cells(day)` its times in the header's order, `card_day` the today card's day (with `card_times`,
    else that day's row)."""
    import calendar
    length = calendar.monthrange(year, month)[1]
    days = list(range(1, length + 1)) if days is None else list(days)
    header = header or MJC_HEADER
    cells = cells or mjc_times
    card_day = card_day or min(29, length)
    card = mjc_card(dt.date(year, month, card_day), card_times or mjc_times(card_day))
    caption = f"Cape Town — {MJC_MONTHS[month - 1]} {year}" if caption is None else caption
    rows = "".join(
        "\n              <tr" + (' class="is-today"' if d == card_day else "") + f'><th scope="row">{label(dt.date(year, month, d))}</th>'
        + "".join(f"<td>{c}</td>" for c in cells(d)) + "</tr>"
        for d in days)
    head = "".join(f'<th scope="col">{h}</th>' for h in header)
    return ('<!doctype html><html lang="en-ZA"><head><meta charset="UTF-8"><title>Salaah Times | Muslim Judicial Council (SA)</title></head><body>'
            '<div class="mjc-top"><span class="mjc-salaah-source" data-salaah-source hidden>    ' + card + '\n    </span></div>'
            '<main><section class="mjc-section"><div class="mjc-wrap"><div class="mjc-title-row"><div><span class="mjc-eyebrow">CAPE TOWN</span>'
            '<h2>Today’s Salaah Times</h2></div></div><div class="mjc-salaah-glass">    ' + card + '\n    </div></div></section>'
            '<section class="mjc-section mjc-tint"><div class="mjc-wrap"><div class="mjc-title-row"><div><span class="mjc-eyebrow">MONTHLY TIMETABLE</span>'
            '<h2>This Month</h2></div></div><div class="mjc-salaah-glass mjc-salaah-month">    <div class="mjc-salaah-calendar" data-salaah-provider="mjc-native">\n'
            f'      <table><caption>{caption}</caption><thead><tr>{head}</tr></thead><tbody>{rows}\n            </tbody></table>\n'
            '      <p class="mjc-salaah-note">Calculated for Cape Town (SAST). Please confirm congregational times with your local Masjid.</p>\n'
            '    </div>\n    </div></div></section></main></body></html>')


class Mjc(unittest.TestCase):
    """The MJC's page (ruling R69: invented times only): the month table read by its caption and
    header, never the today card; a table that is not the whole month refused; a bad day left out."""

    SEPT = common.parse_moment("2026-09-28T03:05:00Z")

    def setUp(self):
        self.lines = []

    def ctx(self, page, now=None):
        return Context("za-mjc", "/nowhere", dt.date(2026, 9, 28), self.lines.append, "/nowhere/monitor",
                       http=FakeHttp([(mjc.PAGE, page.encode("utf-8"))]), now=now or self.SEPT)

    def no_time(self, messages):
        for m in messages:
            self.assertIsNone(TIME_SHAPE.search(m), m)

    def test_the_month_table_is_read_by_its_caption_and_header_never_the_today_card(self):
        year, month, days = mjc.parse(mjc_page())
        self.assertEqual((2026, 9), (year, month))
        self.assertEqual(list(range(1, 31)), sorted(days))
        self.assertEqual(mjc_times(1), days[1])
        self.assertEqual(mjc_times(30), days[30])
        ctx = self.ctx(mjc_page())
        tables = mjc.fetch(ctx)
        self.assertEqual([], ctx.errors)
        self.assertEqual(1, len(tables))
        t = tables[0]
        self.assertEqual(("cape-town-2026-09", "Cape Town, September 2026", "za.mjc/za.mjc", "F S D A M I", "Africa/Johannesburg", "ZA", "standard"),
                         (t.key, t.name, t.entry, t.columns, t.zone, t.cc, t.school))
        self.assertEqual((None, None, None), (t.lat, t.lon, t.survey), "checked at za.mjc's unit, its own Cape Town point")
        self.assertEqual(30, len(t.rows))
        self.assertEqual(mjc_times(17), t.rows["2026-09-17"])
        self.assertFalse(t.merge, "a whole month replaces what is held for it (the MJC's corrections)")
        self.assertEqual(["salaah-times-2026-09.html"], [name for name, _ in t.raw])
        head = t.text("2026-09-28").splitlines()[0]
        self.assertTrue(head.startswith("# Muslim Judicial Council (SA), Salaah Times: https://mjc.org.za/salaah-times/"), head)
        self.assertIn("September 2026", head)
        self.assertTrue(head.endswith("fetched 2026-09-28"), head)
        self.no_time([head])

    @staticmethod
    def shape(page):
        year, month, days = mjc.parse(page)
        return year, month, len(days)

    def test_month_and_year_come_from_the_caption(self):
        # A leap February, an entity for the dash, a 28-day February, a month that ends the year.
        self.assertEqual((2028, 2, 29), self.shape(mjc_page(2028, 2, caption="Cape Town &mdash; February 2028")))
        self.assertEqual((2027, 2, 28), self.shape(mjc_page(2027, 2)))
        self.assertEqual((2026, 12, 31), self.shape(mjc_page(2026, 12, caption="Cape Town – December 2026")))
        # A full weekday name in a row reads too.
        self.assertEqual((2026, 9, 30), self.shape(mjc_page(label=lambda d: mjc_label(d, full=True))))
        # The caption decides: September's 30 rows captioned as October are not October.
        with self.assertRaises(FetchError) as caught:
            mjc.parse(mjc_page(caption="Cape Town — October 2026"))
        self.assertIn("the table has 30 rows, October 2026 has 31 days", str(caught.exception))
        # The today card names its day and month too, but only a table's caption gives the month.
        with self.assertRaises(FetchError) as none:
            mjc.parse(mjc_page(caption="This Month"))
        self.assertIn("no table captioned with a month and a year", str(none.exception))

    def test_a_shortened_or_malformed_table_is_refused(self):
        def refused(page, words):
            with self.assertRaises(FetchError) as caught:
                mjc.parse(page)
            self.assertIn(words, str(caught.exception))
            ctx = self.ctx(page)
            self.assertEqual([], mjc.fetch(ctx), "a refused table is not kept")
            self.assertEqual(1, len(ctx.errors), ctx.errors)
            self.assertIn(words, ctx.errors[0])
            self.no_time(ctx.errors)
        refused(mjc_page(days=range(1, 30)), "the table has 29 rows, September 2026 has 30 days: refused")
        refused(mjc_page(days=[1, 2, 4, 3] + list(range(5, 31))), "row 3 is day 4, not 3 of September 2026")
        # August's weekdays under September's caption give the rows away.
        refused(mjc_page(label=lambda d: f"{mjc_label(d - dt.timedelta(days=1)).split()[0]} {d.day}"),
                "row 1 says Monday 1, but 1 September 2026 is a Tuesday")
        refused(mjc_page(label=lambda d: f"{d.day}"), "row 1's date '1' is not a weekday and a day")
        refused(mjc_page(caption="Johannesburg — September 2026"), "captioned for 'Johannesburg', not Cape Town")
        refused(mjc_page(header=MJC_HEADER[:-1], cells=lambda d: mjc_times(d)[:-1]), "the header names Isha 0 times")
        refused(mjc_page(header=MJC_HEADER + ["Isha"], cells=lambda d: mjc_times(d) + mjc_times(d)[-1:]), "the header names Isha 2 times")
        refused(mjc_page(cells=lambda d: mjc_times(d)[:-1]), "row 1 has 6 cells, the header 7")

    def test_the_columns_are_read_by_their_names(self):
        # Sunrise printed before Fajr, and a column the gate does not read: each time still lands in its place.
        header = ["Date", "Sunrise", "Fajr", "Dhuhr", "Asr (Hanafi)", "Asr", "Maghrib", "Isha"]

        def cells(d):
            f, s, dh, a, m, i = mjc_times(d)
            return [s, f, dh, f"14:{d:02d}", a, m, i]
        _, _, days = mjc.parse(mjc_page(header=header, cells=cells))
        self.assertEqual(mjc_times(9), days[9])

    def test_a_bad_cell_or_a_day_out_of_order_is_left_out_and_the_fetch_is_partial(self):
        def cells(d):
            t = mjc_times(d)
            if d == 7:
                t[0] = "2h07"  # not a time
            if d == 12:
                t[4] = "4:12"  # a Maghrib on a 12-hour clock: not in the day's order
            return t
        ctx = self.ctx(mjc_page(cells=cells))
        tables = mjc.fetch(ctx)
        self.assertEqual(1, len(tables))
        t = tables[0]
        self.assertEqual(28, len(t.rows))
        self.assertNotIn("2026-09-07", t.rows)
        self.assertNotIn("2026-09-12", t.rows)
        self.assertTrue(t.merge, "a month read in part is merged into what is held, so the days left out keep their held values")
        self.assertEqual(1, len(ctx.errors), ctx.errors)
        self.assertIn("September 2026: 2 days left out, what is held for them stays", ctx.errors[0])
        self.assertIn("2026-09-07 (cell 1 is not a time)", ctx.errors[0])
        self.assertIn("2026-09-12 (its times are not in the day's order)", ctx.errors[0])
        self.assertNotIn("2h07", ctx.errors[0])
        self.no_time(ctx.errors)

    def test_the_today_card_must_agree_with_its_row(self):
        card = mjc_times(29)
        card[3] = "13:58"
        ctx = self.ctx(mjc_page(card_times=card))
        tables = mjc.fetch(ctx)
        self.assertEqual(30, len(tables[0].rows), "the table is kept and checked as read")
        self.assertEqual(1, len(ctx.errors), ctx.errors)
        self.assertIn("the page's today card for 2026-09-29 disagrees with the table's row in Asr", ctx.errors[0])
        self.no_time(ctx.errors)
        # A card that agrees says nothing, whichever day it is.
        ctx = self.ctx(mjc_page(card_day=3))
        mjc.fetch(ctx)
        self.assertEqual([], ctx.errors)

    def test_a_page_still_showing_last_month_is_kept_but_the_new_month_is_not_captured(self):
        ctx = self.ctx(mjc_page(), now=common.parse_moment("2026-10-01T03:17:00Z"))
        tables = mjc.fetch(ctx)
        self.assertEqual(["cape-town-2026-09"], [t.key for t in tables], "kept as September's capture")
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("the page shows September 2026 while it is October 2026 in Cape Town", ctx.errors[0])
        # The month is Cape Town's: at 22:30 UTC on 30 September it is October there already.
        ctx = self.ctx(mjc_page(2026, 10), now=common.parse_moment("2026-09-30T22:30:00Z"))
        self.assertEqual(["cape-town-2026-10"], [t.key for t in mjc.fetch(ctx)])
        self.assertEqual([], ctx.errors)


class MonthStart(unittest.TestCase):
    """The month-start cadence (the MJC's page shows the current month alone): due as soon as a new
    month has begun in Africa/Johannesburg since the last complete fetch; R3-M2's retry-once rule kept."""

    ROW = {"source": "za-mjc", "fetcher": "mjc", "cadence": "month-start"}

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-month-start")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")

    def tearDown(self):
        shutil.rmtree(self.root)

    @staticmethod
    def at(text):
        return common.parse_moment(text)

    def reason(self, last_complete, now, retry=False, today=dt.date(2026, 10, 1), last_fetch=None, only=(), force=False):
        return skip_reason(self.ROW, last_fetch, today, set(only), force, retry=retry,
                           last_complete=self.at(last_complete) if last_complete else None, now=self.at(now))

    def test_due_as_soon_as_a_new_month_has_begun_in_johannesburg(self):
        self.assertIn("month-start", CADENCES)
        last = "2026-09-28T03:05:00Z"
        self.assertEqual("not due", self.reason(last, "2026-09-30T21:59:59Z"), "23:59:59 on 30 September in Cape Town")
        self.assertEqual(None, self.reason(last, "2026-09-30T22:00:00Z"), "midnight in Cape Town: October, though 30 September in UTC")
        self.assertEqual(None, self.reason(last, "2026-10-01T03:17:00Z"), "the workflow's run on the 1st")
        # The month is Johannesburg's on both sides: a fetch at 22:30 UTC on 31 October was November's there.
        self.assertEqual("not due", self.reason("2026-10-31T22:30:00Z", "2026-11-02T03:00:00Z"))
        self.assertEqual(None, self.reason("2026-10-31T21:30:00Z", "2026-11-02T03:00:00Z"))
        # Across the year, and before any complete fetch.
        self.assertEqual(None, self.reason("2026-12-28T03:00:00Z", "2027-01-01T03:17:00Z"))
        self.assertEqual(None, self.reason(None, "2026-10-01T03:17:00Z"))
        # Only the month counts, not the days since the last fetch.
        self.assertEqual(None, self.reason("2026-09-30T08:00:00Z", "2026-10-01T03:17:00Z", last_fetch=dt.date(2026, 9, 30)))
        self.assertEqual("not due", self.reason("2026-10-01T03:17:00Z", "2026-10-29T03:00:00Z", last_fetch=dt.date(2026, 10, 1),
                                                today=dt.date(2026, 10, 29)))
        # The retry, --only and --force override, as for every cadence.
        self.assertEqual(None, self.reason("2026-10-01T03:17:00Z", "2026-10-05T03:00:00Z", retry=True))
        self.assertEqual(None, self.reason("2026-10-01T03:17:00Z", "2026-10-05T03:00:00Z", only=("za-mjc",)))
        self.assertEqual(None, self.reason("2026-10-01T03:17:00Z", "2026-10-05T03:00:00Z", force=True))
        self.assertTrue(due(self.ROW, None, dt.date(2026, 10, 1), set(), False, now=self.at("2026-10-01T03:17:00Z")))

    def test_only_a_complete_fetch_captures_the_month_and_the_retry_once_rule_holds(self):
        store = Store(self.root, self.monitor, dt.date(2026, 9, 28))

        def due_at(when):
            return skip_reason(self.ROW, store.last_fetch("za-mjc"), store.today, set(), False, retry=store.retry("za-mjc"),
                               last_complete=store.last_complete("za-mjc"), now=self.at(when))

        def done(day, status, started):
            store.today = dt.date.fromisoformat(day)
            return store.source_done("za-mjc", status, "" if status == "ok" else "a reason", 1, started=self.at(started))
        # September captured whole on the 28th.
        done("2026-09-28", "ok", "2026-09-28T03:05:00Z")
        self.assertEqual("2026-09-28T03:05:00+00:00", store.data["sources"]["za-mjc"]["lastComplete"])
        self.assertEqual("not due", due_at("2026-09-30T20:00:00Z"))
        # 1 October: the page still shows September (partial). Retried once; October is not captured.
        self.assertEqual(None, due_at("2026-10-01T03:17:00Z"))
        self.assertEqual("retried on the next run, once", fetch.next_time(store, self.ROW, self.at("2026-10-01T03:17:00Z")))
        self.assertEqual((True, False), done("2026-10-01", "partial", "2026-10-01T03:17:00Z"))
        self.assertEqual("2026-09-28T03:05:00+00:00", store.data["sources"]["za-mjc"]["lastComplete"], "a partial fetch captures nothing")
        # 5 October, the retry, fails too: no further retry, but October is still not captured, so still due.
        self.assertEqual(None, due_at("2026-10-05T03:00:00Z"))
        self.assertIn("stays due on every run until its month is captured complete", fetch.next_time(store, self.ROW, self.at("2026-10-05T03:00:00Z")))
        self.assertEqual((False, True), done("2026-10-05", "failed", "2026-10-05T03:00:00Z"))
        self.assertEqual(None, due_at("2026-10-12T03:00:00Z"))
        # 12 October, complete: October captured, not due again this month.
        done("2026-10-12", "ok", "2026-10-12T03:00:00Z")
        self.assertEqual("not due", due_at("2026-10-19T03:00:00Z"))
        # A forced fetch that fails later in the month is retried once (R3-M2), then waits for November.
        self.assertEqual((True, False), done("2026-10-20", "failed", "2026-10-20T09:00:00Z"))
        self.assertEqual(None, due_at("2026-10-26T03:00:00Z"))
        self.assertIn("this month is already captured, so the source now waits for the next month",
                      fetch.next_time(store, self.ROW, self.at("2026-10-26T03:00:00Z")))
        self.assertEqual((False, True), done("2026-10-26", "failed", "2026-10-26T03:00:00Z"))
        self.assertEqual("not due", due_at("2026-10-27T03:00:00Z"))
        self.assertEqual(None, due_at("2026-10-31T22:00:00Z"), "November begins at midnight in Cape Town")


def hijri_month_rows(first, days=30):
    """An invented Habous-shaped month: `days` rows from `first`, each time the day's number as minutes
    of hours no Moroccan table prints (ruling R69)."""
    out = {}
    for i in range(days):
        d = first + dt.timedelta(days=i)
        out[d.isoformat()] = [f"0{h}:{d.day:02d}" for h in (1, 2, 3, 4, 5, 6)]
    return out


class HijriMonth(unittest.TestCase):
    """The hijri-month cadence (Habous: the current Hijri month alone, 30 rows from its first day, published
    the day it begins): weekly, and due on every run while the month turns, from the last day held to three
    days after it; the workflow's daily check (`--month-turn`) asks the same question of the state alone."""

    ROW = {"source": "ma-habous", "fetcher": "morocco", "cadence": "hijri-month"}

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-hijri")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")

    def tearDown(self):
        shutil.rmtree(self.root)

    def held(self, store, key, first, days=30, source="ma-habous"):
        t = Table(key, key.title(), None, None, "Africa/Casablanca", "MA", "F+E S D A M I", entry=f"ma.habous/{key}", clock="UTC")
        for d, times in hijri_month_rows(first, days).items():
            t.add(d, times)
        return store.record(source, t)

    def test_the_turn_runs_from_the_last_day_held_to_three_days_after(self):
        self.assertIn("hijri-month", CADENCES)
        last = dt.date(2026, 10, 12)
        self.assertFalse(fetch.month_turn_due(None, last), "nothing held: the weekly cadence fetches it")
        self.assertFalse(fetch.month_turn_due(last, dt.date(2026, 10, 11)))
        self.assertTrue(fetch.month_turn_due(last, last), "a 29-day month: the page's 30th row is the next month's first day")
        self.assertTrue(fetch.month_turn_due(last, dt.date(2026, 10, 13)))
        self.assertTrue(fetch.month_turn_due(last, dt.date(2026, 10, 15)))
        self.assertFalse(fetch.month_turn_due(last, dt.date(2026, 10, 16)), "a page that has not moved is fetched weekly again")
        self.assertEqual(3, fetch.MONTH_TURN_DAYS)

    def test_due_weekly_and_while_the_month_turns(self):
        def reason(today, last_fetch, held_last, retry=False, only=(), force=False):
            return skip_reason(self.ROW, last_fetch, today, set(only), force, retry=retry, held_last=held_last)
        held = dt.date(2026, 10, 12)
        # Mid-month: weekly, six days after the last fetch.
        self.assertEqual("not due", reason(dt.date(2026, 10, 2), dt.date(2026, 9, 28), held))
        self.assertEqual(None, reason(dt.date(2026, 10, 5), dt.date(2026, 9, 28), held))
        # The turn: due on every run, the day after a fetch included.
        self.assertEqual(None, reason(dt.date(2026, 10, 12), dt.date(2026, 10, 11), held))
        self.assertEqual(None, reason(dt.date(2026, 10, 13), dt.date(2026, 10, 12), held))
        self.assertEqual(None, reason(dt.date(2026, 10, 15), dt.date(2026, 10, 14), held))
        # Past the turn with the page unmoved: weekly again.
        self.assertEqual("not due", reason(dt.date(2026, 10, 16), dt.date(2026, 10, 15), held))
        self.assertEqual(None, reason(dt.date(2026, 10, 21), dt.date(2026, 10, 15), held))
        # Never fetched, the retry, --only and --force, as for every cadence.
        self.assertEqual(None, reason(dt.date(2026, 10, 2), None, None))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, retry=True))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, only=("ma-habous",)))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, force=True))
        self.assertTrue(due(self.ROW, dt.date(2026, 10, 11), dt.date(2026, 10, 12), set(), False, held_last=held))
        # Another cadence never reads the turn.
        weekly = dict(self.ROW, cadence="weekly")
        self.assertEqual("not due", skip_reason(weekly, dt.date(2026, 10, 11), dt.date(2026, 10, 12), set(), False, held_last=held))

    def test_the_last_day_held_is_the_earliest_of_the_current_tables(self):
        store = Store(self.root, self.monitor, dt.date(2026, 9, 28))
        self.assertIsNone(store.held_last("ma-habous"))
        self.held(store, "rabat", dt.date(2026, 9, 13))
        self.held(store, "oujda", dt.date(2026, 9, 13))
        self.assertEqual(dt.date(2026, 10, 12), store.held_last("ma-habous"))
        # The next month for Rabat alone: Oujda's missing month keeps the turn open.
        store.today = dt.date(2026, 10, 13)
        self.held(store, "rabat", dt.date(2026, 10, 13))
        self.assertEqual("2026-11-11", store.data["tables"]["ma-habous/rabat"]["last"])
        self.assertEqual(dt.date(2026, 10, 12), store.held_last("ma-habous"))
        self.held(store, "oujda", dt.date(2026, 10, 13))
        self.assertEqual(dt.date(2026, 11, 11), store.held_last("ma-habous"))
        # A city the page no longer lists, over 40 days behind the newest, no longer holds the turn open.
        self.held(store, "gone", dt.date(2026, 8, 14))
        self.assertEqual(dt.date(2026, 11, 11), store.held_last("ma-habous"))
        # Another source's tables are not this source's.
        self.held(store, "x", dt.date(2026, 9, 1), source="other")
        self.assertEqual(dt.date(2026, 11, 11), store.held_last("ma-habous"))

    def test_what_a_failed_turn_says_happens_next(self):
        store = Store(self.root, self.monitor, dt.date(2026, 10, 13))
        self.held(store, "rabat", dt.date(2026, 9, 13))
        self.assertEqual("retried on the next run, once", fetch.next_time(store, self.ROW))
        store.source_done("ma-habous", "failed", "a reason", 1)
        self.assertIn("stays due on every run until 2026-10-15 (3 days after the last day held, 2026-10-12), then weekly",
                      fetch.next_time(store, self.ROW))
        store.today = dt.date(2026, 10, 20)
        self.assertIn("waits for its cadence (weekly, and on every run from 2026-10-12", fetch.next_time(store, self.ROW))

    def write_sources(self, extra=""):
        path = os.path.join(self.root, "sources.tsv")
        with open(path, "w", encoding="utf-8") as f:
            f.write("source\tentries\tfetcher\tcadence\tnext_expected\tpoints\tnote\n")
            f.write("ma-habous\tma.habous\tfakehijri\thijri-month\t-\tp\tn\n")
            f.write("za-jamiat\tza.jamiat\tjamiat\tmonthly\t-\tp\tn\n")
            f.write(extra)
        return path

    def month_turn(self, today, sources, official=None):
        import contextlib
        import io as _io
        out = _io.StringIO()
        with contextlib.redirect_stdout(out):
            code = fetch.main(["--official", official or self.root, "--sources", sources, "--today", today, "--month-turn"])
        return code, out.getvalue().split()

    def test_the_daily_check_reads_the_state_alone_and_names_a_turning_source(self):
        sources = self.write_sources("ma-print\tma.habous\tmanual\thijri-month\t2027-01\tp\tn\n")
        store = Store(self.root, self.monitor, dt.date(2026, 9, 28))
        self.held(store, "rabat", dt.date(2026, 9, 13))
        store.save()
        # The workflow's check has the state and no archive.
        state_only = tempfile.mkdtemp("monitor-state-only")
        try:
            os.makedirs(os.path.join(state_only, "monitor"))
            shutil.copy(os.path.join(self.monitor, "hashes.json"), os.path.join(state_only, "monitor", "hashes.json"))
            self.assertEqual((0, []), self.month_turn("2026-10-11", sources, state_only))
            self.assertEqual((0, ["ma-habous"]), self.month_turn("2026-10-12", sources, state_only), "a source read by hand is never named")
            self.assertEqual((0, ["ma-habous"]), self.month_turn("2026-10-15", sources, state_only))
            self.assertEqual((0, []), self.month_turn("2026-10-16", sources, state_only))
            self.assertFalse(os.path.exists(os.path.join(state_only, "monitor", "fetch")), "the check writes nothing")
            # No state at all: nothing turning.
            self.assertEqual((0, []), self.month_turn("2026-10-12", sources, tempfile.gettempdir() + "/no-such-monitor-root"))
        finally:
            shutil.rmtree(state_only)

    def test_a_run_at_the_turn_fetches_until_the_new_month_is_held(self):
        import types
        pages = {"first": dt.date(2026, 9, 13)}

        def fake_fetch(ctx):
            t = Table("rabat", "Rabat", None, None, "Africa/Casablanca", "MA", "F+E S D A M I", entry="ma.habous/rabat", clock="UTC")
            for d, times in hijri_month_rows(pages["first"]).items():
                t.add(d, times)
            return [t]
        module = types.ModuleType("fetchers.fakehijri")
        module.fetch = fake_fetch
        sys.modules["fetchers.fakehijri"] = module
        sources = self.write_sources()
        try:
            def run(today):
                code = fetch.main(["--official", self.root, "--sources", sources, "--today", today])
                self.assertEqual(0, code)
                with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
                    return json.load(f)["sources"]["ma-habous"]
            self.assertEqual("ok", run("2026-09-28")["status"], "never fetched: due")
            self.assertEqual("not due", run("2026-10-02")["message"])
            # 12 October, the last day held: the page still shows the old month (a 30-day month).
            out = run("2026-10-12")
            self.assertEqual(("ok", {"rabat": "unchanged"}), (out["status"], out["tables"]))
            self.assertEqual((0, ["ma-habous"]), self.month_turn("2026-10-13", sources))
            # 13 October: the new month is up, and held; the turn is over.
            pages["first"] = dt.date(2026, 10, 13)
            out = run("2026-10-13")
            self.assertEqual(("ok", {"rabat": "changed"}), (out["status"], out["tables"]))
            rows = read_table_rows(os.path.join(self.root, "archive", "tables", "monitor", "ma-habous", "rabat.txt"))
            self.assertEqual(("2026-09-13", "2026-11-11", 60), (min(rows), max(rows), len(rows)), "the file keeps both months")
            self.assertEqual("not due", run("2026-10-14")["message"])
            self.assertEqual((0, []), self.month_turn("2026-10-14", sources))
            self.assertEqual("ok", run("2026-10-19")["status"], "weekly as before")
        finally:
            del sys.modules["fetchers.fakehijri"]


class HabousPlaces(unittest.TestCase):
    """The Morocco fetcher's ten units and its 29 edge cities: each edge page must show its own place, is
    read at the app's city point as ma.habous, and has its row in ma-habous.tsv at that very point."""

    @staticmethod
    def page(label):
        return ('<select name="ville"><option value=\'index.php?ville=1\' selected=selected >' + label + "</option></select>"
                '<table id="horaire"><tr><th>الأيام</th><th>ربيع الآخر</th><th>شتنبر / أكتوبر</th><th>الصبح</th><th>الشروق</th>'
                "<th>الظهر</th><th>العصر</th><th>المغرب</th><th>العشاء</th></tr>"
                "<tr><td>الأحد</td><td>1</td><td>13</td><td>01:13</td><td>02:13</td><td>03:13</td><td>04:13</td><td>05:13</td><td>06:13</td></tr>"
                "<tr><td>الإثنين</td><td>2</td><td>14</td><td>01:14</td><td>02:14</td><td>03:14</td><td>04:14</td><td>05:14</td><td>06:14</td></tr></table>").encode()

    def test_edge_pages_are_read_at_the_apps_points_and_a_page_for_another_place_is_left_out(self):
        answers = {morocco.PAGE + str(ville): self.page("x") for _, _, ville in morocco.CITIES}
        for key, name, ville, label, lat, lon, zone in morocco.EDGE_PLACES:
            answers[morocco.PAGE + str(ville)] = self.page(label)
        answers[morocco.PAGE + "104"] = self.page("فاس")  # Marrakesh's id showing Fes's page

        class ExactHttp(FakeHttp):
            def get(self, url, **kw):
                self.urls.append(url)
                if url not in answers:
                    raise FetchError("no canned answer")
                return answers[url]
        root = tempfile.mkdtemp("monitor-habous")
        try:
            ctx = Context("ma-habous", root, dt.date(2026, 9, 28), lambda line: None, root, http=ExactHttp([]))
            tables = {t.key: t for t in morocco.fetch(ctx)}
        finally:
            shutil.rmtree(root)
        self.assertEqual(10 + 29 - 1, len(tables))
        self.assertNotIn("marrakesh", tables)
        self.assertTrue(any("Marrakesh: the page shows another place" in e for e in ctx.errors), ctx.errors)
        unit, fes, boujdour = tables["rabat"], tables["fes"], tables["boujdour"]
        self.assertEqual(("ma.habous/rabat", None, None, "Africa/Casablanca", "UTC"), (unit.entry, unit.lat, unit.lon, unit.zone, unit.clock))
        self.assertEqual(("ma.habous", 34.03313, -5.00028, "Africa/Casablanca", "MA", "UTC", "F+E S D A M I"),
                         (fes.entry, fes.lat, fes.lon, fes.zone, fes.cc, fes.clock, fes.columns))
        self.assertEqual(("Africa/El_Aaiun", "EH"), (boujdour.zone, boujdour.cc))
        self.assertEqual(["2026-09-13", "2026-09-14"], sorted(fes.rows))

    def test_every_edge_place_has_its_row_in_the_gate_at_its_own_point(self):
        gate = os.path.join(HERE, "..", "official", "gate", "ma-habous.tsv")
        with open(gate, encoding="utf-8") as f:
            lines = [line.rstrip("\n").split("\t") for line in f if line.strip() and not line.startswith("#")]
        header, rows = lines[0], [dict(zip(lines[0], r)) for r in lines[1:]]
        self.assertIn("lat", header)
        at = {(r["entry"], float(r["lat"]), float(r["lon"]), r["zone"], r["clock"], r["columns"]) for r in rows if r["lat"]}
        for key, name, ville, label, lat, lon, zone in morocco.EDGE_PLACES:
            self.assertIn(("ma.habous", lat, lon, zone, "UTC", "F+E S D A M I"), at, key)
        self.assertEqual(29, len(morocco.EDGE_PLACES))
        self.assertEqual(len(morocco.EDGE_PLACES), len({p[2] for p in morocco.EDGE_PLACES} | set()), "one place per ville id")
        self.assertFalse({p[2] for p in morocco.EDGE_PLACES} & {c[2] for c in morocco.CITIES}, "no unit city twice")


class Tls(unittest.TestCase):
    """habous.gov.ma sends its certificate without Sectigo's intermediate; the committed chain completes it
    for that host's requests alone, with verification on (the cloud run of 5 October 2026 failed on it)."""

    def setUp(self):
        self.kwargs = []
        self._urlopen = common.urllib.request.urlopen
        self._sleep = common.time.sleep
        self._interval = common.MIN_INTERVAL
        common.time.sleep = lambda s: None
        common.MIN_INTERVAL = 0

        def urlopen(req, timeout=None, **kw):
            self.kwargs.append((req.full_url, kw))
            return FakeResponse(b"<html></html>")
        common.urllib.request.urlopen = urlopen

    def tearDown(self):
        common.urllib.request.urlopen = self._urlopen
        common.time.sleep = self._sleep
        common.MIN_INTERVAL = self._interval

    def test_the_chain_file_is_loaded_for_habous_alone(self):
        self.assertIsNone(common.tls_context("api.example"))
        ctx = common.tls_context("www.habous.gov.ma")
        self.assertIs(ctx, common.tls_context("habous.gov.ma"), "one context per chain file")
        self.assertEqual(common.ssl.CERT_REQUIRED, ctx.verify_mode)
        self.assertTrue(ctx.check_hostname)
        def cn(name):
            return dict(x[0] for x in name).get("commonName")
        self.assertIn("Sectigo Public Server Authentication CA DV R36", {cn(c["subject"]) for c in ctx.get_ca_certs()})
        # The file alone: the intermediate, and Sectigo's R46 root as cross-signed by USERTrust (a store without R46
        # still ends at a root it holds), both valid well past the leaf's renewals.
        bare = common.ssl.SSLContext(common.ssl.PROTOCOL_TLS_CLIENT)
        bare.load_verify_locations(cafile=os.path.join(common.CERTS, "habous.gov.ma.pem"))
        held = sorted((cn(c["subject"]), cn(c["issuer"])) for c in bare.get_ca_certs())
        self.assertEqual([("Sectigo Public Server Authentication CA DV R36", "Sectigo Public Server Authentication Root R46"),
                          ("Sectigo Public Server Authentication Root R46", "USERTrust RSA Certification Authority")], held)
        for c in bare.get_ca_certs():
            self.assertGreater(common.ssl.cert_time_to_seconds(c["notAfter"]), dt.datetime(2036, 1, 1, tzinfo=dt.timezone.utc).timestamp())

    def test_a_habous_request_carries_the_context_and_others_do_not(self):
        http = Http(lambda line: None)
        http.get("https://www.habous.gov.ma/prieres/index.php?ville=1")
        http.get("https://api.example/x")
        self.assertIs(common.tls_context("www.habous.gov.ma"), self.kwargs[0][1].get("context"))
        self.assertEqual({}, self.kwargs[1][1], "every other request is made as before")


class FakeResponse:
    def __init__(self, body):
        self.body = body
        self.status = 200
        self.headers = {}

    def read(self):
        return self.body

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class MjcDriver(unittest.TestCase):
    """fetch.py end to end for the MJC without a network: each month kept as its own capture when the
    page moves on, the index, the raw copy, and the cadence between runs."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-mjc")
        os.makedirs(os.path.join(self.root, "archive"))
        self.monitor = os.path.join(self.root, "monitor")
        self.sources = os.path.join(self.root, "sources.tsv")
        with open(self.sources, "w") as f:
            f.write("source\tentries\tfetcher\tcadence\tnext_expected\tpoints\tnote\n")
            f.write("za-mjc\tza.mjc\tmjc\tmonth-start\t-\tp\tn\n")
        self.page = None
        self.urls = []
        self._urlopen = common.urllib.request.urlopen
        self._sleep = common.time.sleep
        self._interval = common.MIN_INTERVAL
        common.time.sleep = lambda s: None
        common.MIN_INTERVAL = 0

        def urlopen(req, timeout=None):
            self.urls.append(req.full_url)
            return FakeResponse(self.page.encode("utf-8"))
        common.urllib.request.urlopen = urlopen

    def tearDown(self):
        shutil.rmtree(self.root)
        common.urllib.request.urlopen = self._urlopen
        common.time.sleep = self._sleep
        common.MIN_INTERVAL = self._interval

    def run_fetch(self, today, now, *more):
        self.assertEqual(0, fetch.main(["--official", self.root, "--sources", self.sources, "--today", today, "--now", now, *more]))
        with open(os.path.join(self.monitor, "fetch", "latest.json")) as f:
            return json.load(f)["sources"]["za-mjc"]

    def path(self, key):
        return os.path.join(self.root, "archive", "tables", "monitor", "za-mjc", key + ".txt")

    def index(self):
        with open(os.path.join(self.root, "archive", "tables", "monitor", "index.tsv")) as f:
            lines = [line.rstrip("\n").split("\t") for line in f if not line.startswith("#")]
        return [dict(zip(lines[0], cells)) for cells in lines[1:]]

    def test_each_month_is_kept_when_the_page_moves_on(self):
        self.page = mjc_page(2026, 9)
        run = self.run_fetch("2026-09-28", "2026-09-28T03:05:00Z")
        self.assertEqual(("ok", {"cape-town-2026-09": "new"}), (run["status"], run["tables"]), run)
        self.assertEqual(["https://mjc.org.za/salaah-times/"], self.urls)
        september = read_table_rows(self.path("cape-town-2026-09"))
        self.assertEqual(30, len(september))
        with open(self.path("cape-town-2026-09")) as f:
            head = f.readline()
        self.assertTrue(head.startswith("# Muslim Judicial Council (SA), Salaah Times: https://mjc.org.za/salaah-times/"), head)
        self.assertIn("fetched 2026-09-28", head)
        self.assertTrue(os.path.exists(os.path.join(self.root, "archive", "raw", "monitor", "za-mjc", "2026-09-28", "salaah-times-2026-09.html.gz")))
        rows = self.index()
        self.assertEqual(1, len(rows))
        self.assertEqual(("za-mjc", "cape-town-2026-09", "archive/tables/monitor/za-mjc/cape-town-2026-09.txt", "za.mjc/za.mjc", "F S D A M I",
                          "Africa/Johannesburg", "ZA", "", ""),
                         tuple(rows[0][k] for k in ("source", "key", "path", "entry", "columns", "zone", "cc", "lat", "lon")))
        # The next day: September is captured, nothing is asked.
        run = self.run_fetch("2026-09-29", "2026-09-29T03:00:00Z")
        self.assertEqual(("skipped", "not due"), (run["status"], run["message"]))
        self.assertEqual(1, len(self.urls))
        # The run on the 1st: the page has turned to October and no longer holds September.
        self.page = mjc_page(2026, 10)
        run = self.run_fetch("2026-10-01", "2026-10-01T03:17:00Z")
        self.assertEqual(("ok", {"cape-town-2026-10": "new"}), (run["status"], run["tables"]), run)
        self.assertEqual(31, len(read_table_rows(self.path("cape-town-2026-10"))))
        self.assertEqual(september, read_table_rows(self.path("cape-town-2026-09")), "September's capture is kept as it was")
        self.assertEqual(["cape-town-2026-09", "cape-town-2026-10"], sorted(r["key"] for r in self.index()))
        # The next Monday: not due; named with --only it is fetched, and October is unchanged.
        run = self.run_fetch("2026-10-05", "2026-10-05T03:00:00Z")
        self.assertEqual(("skipped", "not due"), (run["status"], run["message"]))
        run = self.run_fetch("2026-10-05", "2026-10-05T09:00:00Z", "--only", "za-mjc")
        self.assertEqual(("ok", {"cape-town-2026-10": "unchanged"}), (run["status"], run["tables"]))
        self.assertEqual(["cape-town-2026-09", "cape-town-2026-10"], sorted(r["key"] for r in self.index()))

    def test_a_refused_table_fails_and_the_source_stays_due_until_the_month_is_captured(self):
        self.page = mjc_page(2026, 10, days=range(1, 31))
        run = self.run_fetch("2026-10-01", "2026-10-01T03:17:00Z")
        self.assertEqual("failed", run["status"])
        self.assertIn("the table has 30 rows, October 2026 has 31 days: refused", run["message"])
        self.assertTrue(run["message"].endswith("retried on the next run, once"), run["message"])
        self.assertFalse(os.path.exists(self.path("cape-town-2026-10")))
        # The retry fails too; October is still not captured, so the source stays due.
        run = self.run_fetch("2026-10-05", "2026-10-05T03:00:00Z")
        self.assertEqual("failed", run["status"])
        self.assertIn("stays due on every run until its month is captured complete", run["message"])
        self.page = mjc_page(2026, 10)
        run = self.run_fetch("2026-10-12", "2026-10-12T03:00:00Z")
        self.assertEqual(("ok", {"cape-town-2026-10": "new"}), (run["status"], run["tables"]))

    def test_a_malformed_now_is_refused(self):
        self.assertEqual(2, fetch.main(["--official", self.root, "--sources", self.sources, "--now", "yesterday"]))


class Catalogue(unittest.TestCase):
    """The committed sources.tsv: well formed, metadata only, and the MJC's row as the check needs it."""

    def test_the_committed_catalogue_is_valid(self):
        with open(fetch.SOURCES_TSV, encoding="utf-8") as f:
            lines = [line.rstrip("\n") for line in f if line.strip() and not line.startswith("#")]
        header = lines[0].split("\t")
        self.assertEqual(["source", "entries", "fetcher", "cadence", "next_expected", "points", "note"], header)
        for line in lines[1:]:
            self.assertEqual(len(header), len(line.split("\t")), line[:60])
        rows = fetch.read_sources(fetch.SOURCES_TSV)
        ids = [r["source"] for r in rows]
        self.assertEqual(len(ids), len(set(ids)), "source ids are unique")
        fetchers = {n[:-3] for n in os.listdir(os.path.join(HERE, "fetchers")) if n.endswith(".py") and n != "__init__.py"}
        for r in rows:
            self.assertRegex(r["source"], r"^[a-z0-9-]+$", "the workflow accepts --only <id> in this shape")
            self.assertIn(r["cadence"], CADENCES, r["source"])
            self.assertTrue(r["fetcher"] == "manual" or r["fetcher"] in fetchers, r["source"])
            self.assertRegex(r["next_expected"], r"^(-|\d{4}-\d{2}(-\d{2})?)$", r["source"])
            self.assertFalse(any(TIME_SHAPE.search(v) for v in r.values()), f"{r['source']}: no printed time in the catalogue (ruling R69)")
        mine = [r for r in rows if r["source"] == "za-mjc"]
        self.assertEqual(1, len(mine))
        self.assertEqual(("mjc", "month-start", "-"), (mine[0]["fetcher"], mine[0]["cadence"], mine[0]["next_expected"]))
        self.assertIn("za.mjc", mine[0]["entries"])
        self.assertIn("za.cape", mine[0]["entries"])
        self.assertEqual("za-mjc", mjc.SOURCE)
        self.assertEqual("za.mjc/za.mjc", mjc.ENTRY, "the entry's one unit, as za-cape.tsv holds the MJC's table")
        habous = [r for r in rows if r["source"] == "ma-habous"]
        self.assertEqual([("morocco", "hijri-month", "-")], [(r["fetcher"], r["cadence"], r["next_expected"]) for r in habous],
                         "Habous publishes one Hijri month at a time, on its first day: fetched as the month turns")
        self.assertEqual("ma-habous", morocco.SOURCE)
        self.assertEqual("habous.gov.ma.pem", common.EXTRA_CA["www.habous.gov.ma"])
        self.assertTrue(os.path.isfile(os.path.join(common.CERTS, common.EXTRA_CA["www.habous.gov.ma"])))


# bonnetid.info as it has been since late September 2026: a searchable city list instead of a select,
# and month replies that name neither the month nor the year (the month bar marks the month; the
# weekdays fix the year). Every time invented: the day's number as the minutes of hours no Norwegian
# table prints, in the day's order (ruling R69).
IRN_WEEKDAYS = ["man", "tir", "ons", "tor", "fre", "lør", "søn"]


def irn_home(cities=("Oslo", "Tromsø", "Trondheim - Jakobsli - Heimdal - Saupstad", "Drammen")):
    options = "".join(f'<div class="city-option" data-value="{c}"><div class="city-name">{c}</div>'
                      f'<div class="city-coords">1.000000°N, 2.000000°E</div></div>' for c in cities)
    return ('<div class="prayer-table-wrapper"></div><form id="city-search-form"><div class="city-dropdown">' + options + "</div></form>"
            '<script id="umer-script-js-extra">\nvar prayerAjax = {"ajaxurl":"https://bonnetid.info/wp-admin/admin-ajax.php","nonce":"n0nce"};\n</script>')


def irn_reply(city, year, month, active=None, days=None, weekday_shift=0, reply_city=None):
    days = days or calendar.monthrange(year, month)[1]
    rows = "".join(
        f'<tr class=""> <td>{d:02d}</td> <td>{IRN_WEEKDAYS[(dt.date(year, month, d).weekday() + weekday_shift) % 7]}</td>'
        f"<td>01:{d:02d}</td><td>02:{d:02d}</td><td>10:{d:02d}</td><td>13:{d:02d}</td><td>14:{d:02d}</td><td>16:{d:02d}</td><td>22:{d:02d}</td></tr>"
        for d in range(1, days + 1))
    nav = "".join(f'<li><a href="#" data-month="{m}" class="month-link {"active" if m == (active or month) else ""}">M{m}</a></li>' for m in range(1, 13))
    html = (f'<div class="api-card"><h2>{reply_city or city}</h2><table> <tr> <th>Dato</th> <th>Dag</th> <th>Fajr</th> <th>Solopp.</th>'
            f" <th>Duhr</th> <th>Asr</th> <th>Asr 2x</th> <th>Maghrib</th> <th>Isha</th> </tr>{rows}</table></div>")
    return json.dumps({"success": True, "data": {"html": html, "monthNav": f'<div class="scrollable-nav"><nav><ul>{nav}</ul></nav></div>',
                                                 "city": reply_city or city}}).encode()


class IrnAjaxHttp:
    """bonnetid.info under test: the home page, and each month reply by the city and month posted."""

    def __init__(self, home, reply):
        self.home = home
        self.reply = reply
        self.posts = []
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1):
        if data is None:
            return self.home.encode("utf-8")
        self.posts.append((data["city"], data["month"], data["nonce"]))
        return self.reply(data["city"], data["month"])

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8")


class IrnSearchList(unittest.TestCase):
    def test_the_searchable_city_list_is_read_and_a_joint_name_found_by_its_first_part(self):
        ajaxurl, nonce, cities = irn.page_config(irn_home())
        self.assertEqual(("https://bonnetid.info/wp-admin/admin-ajax.php", "n0nce"), (ajaxurl, nonce))
        self.assertEqual("Oslo", irn.find_city(cities, "oslo"))
        self.assertEqual("Tromsø", irn.find_city(cities, "tromso"))
        self.assertEqual("Trondheim - Jakobsli - Heimdal - Saupstad", irn.find_city(cities, "trondheim"))
        self.assertIsNone(irn.find_city(cities, "bergen"))
        # Two joint names starting alike: neither is guessed.
        _, _, two = irn.page_config(irn_home(("Oslo", "Trondheim - Nord", "Trondheim - Sør")))
        self.assertIsNone(irn.find_city(two, "trondheim"))
        with self.assertRaises(FetchError):
            irn.page_config(irn_home(("Bergen",)))

    def test_the_year_is_the_one_the_weekdays_fall_in_and_the_month_the_bar_marks(self):
        today = dt.date(2026, 12, 10)
        rows, year = irn.parse_month(irn_reply("Oslo", 2027, 1), 1, city="Oslo", today=today)
        self.assertEqual("2027", year, "January's weekdays as 2027 has them: next year's calendar, published in December")
        self.assertEqual(31, len(rows))
        self.assertEqual((1, ["01:01", "02:01", "10:01", "13:01", "16:01", "22:01", "14:01"]), rows[0], "Asr 2x is the seventh column")
        self.assertEqual("2026", irn.parse_month(irn_reply("Oslo", 2026, 2), 2, city="Oslo", today=today)[1])
        # October 2026 and January 2026 both begin on a Thursday with 31 days: the bar tells them apart.
        with self.assertRaises(FetchError) as bar:
            irn.parse_month(irn_reply("Oslo", 2026, 10), 1, city="Oslo", today=today)
        self.assertIn("month bar marks month 10, not 1", str(bar.exception))
        with self.assertRaises(FetchError) as weekdays:
            irn.parse_month(irn_reply("Oslo", 2026, 3, weekday_shift=3), 3, city="Oslo", today=today)
        self.assertIn("in no year around 2026", str(weekdays.exception))
        with self.assertRaises(FetchError) as short:
            irn.parse_month(irn_reply("Oslo", 2026, 4, days=29), 4, city="Oslo", today=today)
        self.assertIn("29 days", str(short.exception))
        with self.assertRaises(FetchError) as other:
            irn.parse_month(irn_reply("Oslo", 2026, 5, reply_city="Bergen"), 5, city="Oslo", today=today)
        self.assertIn("'Bergen', not 'Oslo'", str(other.exception))
        for e in (bar.exception, weekdays.exception, short.exception, other.exception):
            self.assertIsNone(TIME_SHAPE.search(str(e)), "no message quotes a cell")

    def test_a_fetch_reads_twelve_months_for_each_city_under_the_names_the_site_uses(self):
        lines = []
        root = tempfile.mkdtemp("irn")
        self.addCleanup(shutil.rmtree, root)
        http = IrnAjaxHttp(irn_home(), lambda city, month: irn_reply(city, 2026, month))
        ctx = Context("no-irn", root, dt.date(2026, 10, 2), lines.append, root, http=http)
        tables = irn.fetch(ctx)
        self.assertEqual([], ctx.errors)
        self.assertEqual(["oslo-2026", "trondheim-2026", "tromso-2026"], [t.key for t in tables])
        self.assertEqual([365, 365, 365], [len(t.rows) for t in tables])
        self.assertEqual("no.irn/no.irn.trondheim", tables[1].entry)
        self.assertEqual(36, len(http.posts))
        self.assertEqual({"Oslo", "Trondheim - Jakobsli - Heimdal - Saupstad", "Tromsø"}, {c for c, _, _ in http.posts})
        self.assertTrue(all(n == "n0nce" for _, _, n in http.posts))
        # One month for another city: that month alone is an error, the rest is kept (a partial fetch).
        http = IrnAjaxHttp(irn_home(), lambda city, month: irn_reply(city, 2026, month, reply_city="Bergen" if (city, month) == ("Oslo", 3) else None))
        ctx = Context("no-irn", root, dt.date(2026, 10, 2), lines.append, root, http=http)
        tables = irn.fetch(ctx)
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("Oslo month 3", ctx.errors[0])
        self.assertEqual(365 - 31, len(tables[0].rows))


# awqaf.gov.jo's PrayerTime form as its markup has it: the region list, the paged table (ten days a page,
# dd/mm/yyyy, 12-hour times with no AM or PM) and the pager's postback links. Every time invented: the
# day of the month as the minutes of hours in the day's order (ruling R69).
JO_HEADER = "".join(f'<th class="headrgv" scope="col"><p style="color: #fff">{h}</p></th>' for h in jordan.HEADER)


def jo_page(first, days=10, region="1", label="عمان، البلقاء، الزرقاء، مادبا", offered=(), state="vs0", header=JO_HEADER, cells=None):
    rows = ""
    for i in range(days):
        d = first + dt.timedelta(days=i)
        times = cells(d) if cells else [f"04:{d.day:02d}", f"05:{d.day:02d}", f"11:{d.day:02d}", f"02:{d.day:02d}", f"05:{d.day:02d}", f"07:{d.day:02d}"]
        rows += "<tr>" + f"<td><span>{d.day:02d}/{d.month:02d}/{d.year}</span></td>" + "".join(f"<td><span>{t}:00</span></td>" for t in times) + "</tr>"
    pager = "".join(f"<td><a href=\"javascript:__doPostBack(&#39;ctl00$MainContent$gvWebparts&#39;,&#39;Page${n}&#39;)\">{n}</a></td>" for n in offered)
    return (f'<form method="post" action="./PrayerTime" id="ctl01"><input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="{state}" />'
            '<input type="hidden" name="__EVENTVALIDATION" id="__EVENTVALIDATION" value="ev" />'
            '<select name="ctl00$MainContent$DropCompany" id="MainContent_DropCompany"><option value="0">الرجاء الاختيار</option>'
            f'<option selected="selected" value="{region}">{label}</option><option value="6">العقبة</option></select>'
            '<table class="borderTable" id="MainContent_gvWebparts"><tr align="center">' + header + "</tr>" + rows
            + f'<tr class="pagerGV"><td colspan="7"><table><tr>{pager}</tr></table></td></tr></table></form>')


class JordanHttp:
    """awqaf.gov.jo under test: the first page on a GET, each postback's page by its argument."""

    def __init__(self, pages):
        self.pages = pages  # {"GET" or "Page$n": html}
        self.calls = []
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1, jar=None):
        self.calls.append((data, jar))
        key = "GET" if data is None else data["__EVENTARGUMENT"]
        if key not in self.pages:
            raise FetchError("HTTP 500")
        return self.pages[key].encode("utf-8")

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8")


class Jordan(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp("jordan")
        self.addCleanup(shutil.rmtree, self.root)
        self._pages = jordan.PAGES
        jordan.PAGES = 3
        self.addCleanup(setattr, jordan, "PAGES", self._pages)

    def ctx(self, http, today=dt.date(2026, 10, 2)):
        return Context("jo-awqaf", self.root, today, [].append, self.root, http=http)

    def test_twelve_hour_times_are_read_as_the_days(self):
        self.assertEqual(["04:07", "05:07", "11:07", "14:07", "17:07", "19:07"], jordan.day_times(["04:07:00", "05:07:00", "11:07:00", "02:07:00", "05:07:00", "07:07:00"]))
        self.assertEqual("12:30", jordan.day_times(["04:07", "05:07", "12:30", "02:07", "05:07", "07:07"])[2], "a Dhuhr after noon stays")
        with self.assertRaises(FetchError) as order:
            jordan.day_times(["04:07", "05:07", "11:07", "06:07", "05:07", "07:07"])
        self.assertIn("not in the day's order", str(order.exception))
        with self.assertRaises(FetchError):
            jordan.day_times(["04:07", "-", "11:07", "02:07", "05:07", "07:07"])

    def test_a_walk_of_the_pager_within_one_session(self):
        http = JordanHttp({"GET": jo_page(dt.date(2026, 10, 2), offered=(2, 3, 4), state="vs1"),
                           "Page$2": jo_page(dt.date(2026, 10, 12), offered=(1, 3, 4), state="vs2"),
                           "Page$3": jo_page(dt.date(2026, 10, 22), offered=(1, 2, 4), state="vs3")})
        ctx = self.ctx(http)
        tables = jordan.fetch(ctx)
        self.assertEqual([], ctx.errors)
        self.assertEqual(["amman", "zarqa"], [t.key for t in tables])
        self.assertEqual("jo.awqaf/jo.awqaf.amman", tables[0].entry)
        self.assertEqual(("jo.awqaf", 32.07275, 36.08796), (tables[1].entry, tables[1].lat, tables[1].lon))
        self.assertEqual(30, len(tables[0].rows))
        self.assertEqual(("2026-10-02", "2026-10-31"), (tables[0].first(), tables[0].last()))
        self.assertEqual(["04:02", "05:02", "11:02", "14:02", "17:02", "19:02"], tables[0].rows["2026-10-02"])
        self.assertEqual(tables[0].rows, tables[1].rows, "one table for the region")
        # One cookie jar for the whole walk; each postback carries the previous page's view state.
        jars = {id(j) for _, j in http.calls}
        self.assertEqual(1, len(jars))
        self.assertIsNotNone(http.calls[0][1])
        self.assertEqual(["vs1", "vs2"], [d["__VIEWSTATE"] for d, _ in http.calls[1:]])
        self.assertEqual(["Page$2", "Page$3"], [d["__EVENTARGUMENT"] for d, _ in http.calls[1:]])
        self.assertTrue(all(d["ctl00$MainContent$DropCompany"] == "1" and d["__EVENTTARGET"] == jordan.GRID for d, _ in http.calls[1:]))
        self.assertEqual(3, len(tables[0].raw))
        self.assertEqual([], tables[1].raw)

    def test_a_page_that_does_not_read_as_expected_is_refused(self):
        start = dt.date(2026, 10, 2)
        for page, words in [
            (jo_page(start, region="6", label="العقبة"), "region shown is 6"),
            (jo_page(start, header=JO_HEADER.replace(jordan.HEADER[3], "x")), "header"),
            (jo_page(dt.date(2026, 9, 20)), "not today"),
        ]:
            with self.assertRaises(FetchError) as caught:
                jordan.fetch(self.ctx(JordanHttp({"GET": page})))
            self.assertIn(words, str(caught.exception))
        # A second page that does not carry on from the first stops the walk: the fetch is partial.
        http = JordanHttp({"GET": jo_page(start, offered=(2, 3)), "Page$2": jo_page(dt.date(2026, 10, 20), offered=(1, 3))})
        ctx = self.ctx(http)
        tables = jordan.fetch(ctx)
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("page 2: the page starts on 2026-10-20, not 2026-10-12", ctx.errors[0])
        self.assertEqual(10, len(tables[0].rows))
        # A day out of order is left out alone, and no message quotes a cell.
        bad = lambda d: ["04:00", "05:00", "11:00", "02:00", "05:00", "07:00"] if d.day != 5 else ["04:00", "05:00", "11:00", "06:00", "05:00", "07:00"]
        ctx = self.ctx(JordanHttp({"GET": jo_page(start, cells=bad)}))
        jordan.PAGES = 1
        tables = jordan.fetch(ctx)
        self.assertEqual(9, len(tables[0].rows))
        self.assertTrue(any("2026-10-05" in e for e in ctx.errors))
        self.assertFalse(any(TIME_SHAPE.search(e) for e in ctx.errors))

    def test_the_year_ends_the_table_without_a_failure(self):
        http = JordanHttp({"GET": jo_page(dt.date(2026, 12, 22), days=10, offered=())})
        ctx = self.ctx(http, today=dt.date(2026, 12, 22))
        tables = jordan.fetch(ctx)
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("ends with the year" in n for n in ctx.notes))
        self.assertEqual("2026-12-31", tables[0].last())
        # Before the year's end, a pager that stops short is a partial fetch.
        ctx = self.ctx(JordanHttp({"GET": jo_page(dt.date(2026, 10, 2), offered=())}))
        jordan.fetch(ctx)
        self.assertIn("offers no page 2", ctx.errors[0])

    def test_a_request_within_a_session_carries_the_cookies_and_never_goes_through_curl(self):
        seen = []

        class Opener:
            def open(self, req, timeout=None):
                seen.append(req.full_url)
                return FakeResponse(b"ok")
        built = []
        real_build, real_urlopen = common.urllib.request.build_opener, common.urllib.request.urlopen
        common.urllib.request.build_opener = lambda *handlers: built.append(handlers) or Opener()
        common.urllib.request.urlopen = lambda *a, **k: self.fail("a session request goes through its opener")
        self.addCleanup(setattr, common.urllib.request, "build_opener", real_build)
        self.addCleanup(setattr, common.urllib.request, "urlopen", real_urlopen)
        interval, common.MIN_INTERVAL = common.MIN_INTERVAL, 0
        self.addCleanup(setattr, common, "MIN_INTERVAL", interval)
        import http.cookiejar
        jar = http.cookiejar.CookieJar()
        client = Http([].append)
        self.assertEqual(b"ok", client.get("https://awqaf.gov.jo/AR/Pages/PrayerTime", jar=jar))
        self.assertEqual(1, len(built))
        self.assertIs(jar, built[0][0].cookiejar)
        with self.assertRaises(FetchError) as curl:
            client._curl("https://awqaf.gov.jo/AR/Pages/PrayerTime", {}, None, 5, jar)
        self.assertIn("not sent through curl", str(curl.exception))


# The three Toronto tables as their sources print them, every time invented: the hours of the day's
# order with the day of the month as the minutes, an hour less from 1 November (America/Toronto's
# clock change) where `slip` days leave the table on the old clock (ruling R69).
def toronto_times(d, slip=()):
    back = 60 if d >= dt.date(2026, 11, 1) and d not in slip else 0
    base = [5 * 60, 7 * 60, 13 * 60, 16 * 60, 19 * 60, 20 * 60 + 30]
    return [f"{(m - back + d.day) // 60:02d}:{(m - back + d.day) % 60:02d}" for m in base]


def toronto_ift_csv(year=2026, slip=(), days=None):
    lines = ["PrayerDate,FajarBegins,Fajar,Sunrise,ZuharBegins,Zuhar,AsarBegins,Asar,Sunset,MagribBegins,IshaBegins,Isha"]
    d = dt.date(year, 1, 1)
    while d.year == year and (days is None or len(lines) <= days):
        f, s, z, a, m, i = toronto_times(d, slip)
        twelve = lambda t: f"{int(t[:2]) % 12 or 12}:{t[3:]}"
        lines.append(",".join([d.isoformat(), twelve(f), twelve(f), twelve(s), twelve(z), twelve(z), twelve(a), twelve(a), twelve(m), twelve(m), twelve(i), twelve(i)]))
        d += dt.timedelta(days=1)
    return ("\r\n".join(lines) + "\r\n").encode()


def toronto_iit_month(year, month, heading=None):
    name = calendar.month_name[month]
    rows = ""
    for day in range(1, calendar.monthrange(year, month)[1] + 1):
        d = dt.date(year, month, day)
        f, s, z, a, m, i = toronto_times(d)
        ap = lambda t: f"{int(t[:2]) % 12 or 12}:{t[3:]} {'pm' if int(t[:2]) >= 12 else 'am'}"
        cells = [f"{name} {day}, {year} <p class=\"hijriDate\"> x</p>", d.strftime("%A"), ap(f), ap(f), ap(s), ap(z), ap(z), ap(a), ap(a), ap(a), ap(m), ap(m), ap(i), ap(i)]
        rows += "<tr>" + "".join(f"<td>{c}</td>" for c in cells) + "</tr>"
    return (f'<div class="dpt-monthly-table-wrapper"><table><thead class="prayerName"><th class="prayerName" colspan="2">{heading or name}</th></thead>'
            + rows + "</table></div>").encode()


def toronto_mac_page(lat=43.6554647, lon=-79.3857551):
    cal = []
    for month in range(1, 13):
        cal.append({str(day): toronto_times(dt.date(2026, month, day)) for day in range(1, calendar.monthrange(2026, month)[1] + 1)})
    conf = {"latitude": lat, "longitude": lon, "timezone": "America/Toronto", "calendar": cal}
    return ("<script>var confData = " + json.dumps(conf) + ";</script>").encode()


class TorontoHttp:
    def __init__(self, answers):
        self.answers = answers
        self.urls = []
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1):
        self.urls.append(url)
        answer = self.answers(url)
        if isinstance(answer, Exception):
            raise answer
        return answer

    def text(self, url, **kw):
        return self.get(url, **kw).decode("utf-8")


def toronto_answers(ift=None, iit=None, mac=None):
    def answer(url):
        if url == toronto.IFT_CSV:
            return ift if ift is not None else toronto_ift_csv(slip={dt.date(2026, 11, 1), dt.date(2026, 11, 2)})
        if url.startswith("https://islam.ca/"):
            month = int(url.rsplit("=", 1)[1])
            return (iit or (lambda m: toronto_iit_month(2026, m)))(month)
        if url.endswith(toronto.MAC_SLUG):
            return mac if mac is not None else toronto_mac_page()
        return FetchError("no canned answer")
    return answer


class Toronto(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp("toronto")
        self.addCleanup(shutil.rmtree, self.root)

    def ctx(self, http, today=dt.date(2026, 10, 2)):
        return Context("ca-toronto", self.root, today, [].append, self.root, http=http)

    def test_a_fetch_keeps_the_three_tables_from_the_month_on_less_the_days_on_the_wrong_clock(self):
        ctx = self.ctx(TorontoHttp(toronto_answers()))
        tables = {t.key: t for t in toronto.fetch(ctx)}
        self.assertEqual([], ctx.errors)
        self.assertEqual({"ift-2026", "iit-2026", "mac-2026"}, set(tables))
        self.assertEqual(("ca.ift/ca.ift", "hanafi"), (tables["ift-2026"].entry, tables["ift-2026"].school))
        self.assertEqual(("ca.iit/ca.iit", "ca.mac/ca.mac"), (tables["iit-2026"].entry, tables["mac-2026"].entry))
        for t in tables.values():
            self.assertEqual("2026-10-01", t.first(), "the days from the first of the current month")
            self.assertFalse(t.merge)
        self.assertEqual(toronto_times(dt.date(2026, 10, 5)), tables["ift-2026"].rows["2026-10-05"], "12-hour cells read as the day's")
        self.assertEqual(toronto_times(dt.date(2026, 10, 5)), tables["iit-2026"].rows["2026-10-05"])
        self.assertEqual(toronto_times(dt.date(2026, 10, 5)), tables["mac-2026"].rows["2026-10-05"])
        # IFT's file keeps the old clock on 1-2 November: those days (and its recorded 28-30 November) are left out, with notes.
        left = {"2026-11-01", "2026-11-02", "2026-11-28", "2026-11-29", "2026-11-30"}
        self.assertEqual(set(), left & set(tables["ift-2026"].rows))
        self.assertEqual(92 - 5, len(tables["ift-2026"].rows))
        self.assertEqual(92, len(tables["iit-2026"].rows))
        self.assertTrue(any("2026-11-01 to 2026-11-02 are on the wrong clock" in n for n in ctx.notes), ctx.notes)
        self.assertTrue(any("left out: IFT prints Isha" in n for n in ctx.notes))
        self.assertEqual(1 + 12 + 1, len(ctx.http.urls))

    def test_the_clock_check_reads_each_column_and_only_near_a_change(self):
        rows = {}
        d = dt.date(2026, 10, 20)
        while d <= dt.date(2026, 11, 15):
            times = toronto_times(d)
            if d in (dt.date(2026, 11, 1), dt.date(2026, 11, 2)):
                times[1] = toronto_times(d, slip={d})[1]  # the sunrise column alone a day late
            rows[d.isoformat()] = times
            d += dt.timedelta(days=1)
        slips, notes = toronto.clock_slips(rows)
        self.assertEqual({"2026-11-01", "2026-11-02"}, slips)
        self.assertEqual("2026-11-02", notes[0][0])
        # A table on the right clock leaves nothing out.
        self.assertEqual(set(), toronto.clock_slips({k: toronto_times(dt.date.fromisoformat(k)) for k in rows})[0])

    def test_tables_that_do_not_read_as_expected_are_refused(self):
        # IFT: not one whole year.
        ctx = self.ctx(TorontoHttp(toronto_answers(ift=toronto_ift_csv(days=200))))
        tables = toronto.fetch(ctx)
        self.assertTrue(any(e.startswith("IFT: the CSV holds 200 days of 2026") for e in ctx.errors), ctx.errors)
        self.assertNotIn("ift-2026", [t.key for t in tables])
        # IIT: a reply for another month.
        ctx = self.ctx(TorontoHttp(toronto_answers(iit=lambda m: toronto_iit_month(2026, 5 if m == 6 else m))))
        tables = toronto.fetch(ctx)
        self.assertEqual(["IIT month 6: the table's heading is not month 6"], ctx.errors)
        # MAC: another mosque's page.
        ctx = self.ctx(TorontoHttp(toronto_answers(mac=toronto_mac_page(lat=45.5))))
        tables = toronto.fetch(ctx)
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("MAC: the page's point", ctx.errors[0])
        self.assertNotIn("mac-2026", [t.key for t in tables])
        for e in ctx.errors:
            self.assertIsNone(TIME_SHAPE.search(e), "no message quotes a cell")

    def test_twelve_hour_cells(self):
        self.assertEqual("13:05", toronto.twelve("1:05", True))
        self.assertEqual("12:30", toronto.twelve("12:30", True))
        self.assertEqual("06:05", toronto.twelve("6:05", False))
        self.assertEqual(("00:10", "12:10", "18:05"), (toronto.ampm("12:10 am"), toronto.ampm("12:10 pm"), toronto.ampm("6:05PM")))
        with self.assertRaises(FetchError):
            toronto.ampm("6:05")
        with self.assertRaises(FetchError):
            toronto.ordered(["05:00", "04:00"], "x")


# Masjidal's time/range reply as the API gives it, every time invented (toronto_times's, ruling R69).
def masjidal_reply(start, end, weekday_shift=0, skip=None, status="success"):
    days = []
    d = start
    while d <= end:
        if d != skip:
            f, s, z, a, m, i = toronto_times(d)
            ap = lambda t: f"{int(t[:2]) % 12 or 12}:{t[3:]}{'PM' if int(t[:2]) >= 12 else 'AM'}"
            name = calendar.day_name[(d.weekday() + weekday_shift) % 7]
            days.append({"date": f"{name}, {d.strftime('%b')} {d.day}, {d.year}", "hijri_date": "1, 1448", "day": name,
                         "fajr": ap(f), "sunrise": ap(s), "zuhr": ap(z), "asr": ap(a), "maghrib": ap(m), "isha": ap(i)})
        d += dt.timedelta(days=1)
    return json.dumps({"status": status, "data": {"salah": days, "iqamah": []}}).encode()


class Masjidal(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp("masjidal")
        self.addCleanup(shutil.rmtree, self.root)

    def test_the_window_runs_from_this_month_to_the_end_of_the_month_after_next(self):
        self.assertEqual((dt.date(2026, 10, 1), dt.date(2026, 12, 31)), masjidal.window(dt.date(2026, 10, 2)))
        self.assertEqual((dt.date(2026, 11, 1), dt.date(2027, 1, 31)), masjidal.window(dt.date(2026, 11, 30)))
        self.assertEqual((dt.date(2026, 12, 1), dt.date(2027, 2, 28)), masjidal.window(dt.date(2026, 12, 15)))

    def test_a_reply_must_hold_the_days_asked_each_on_its_weekday(self):
        start, end = dt.date(2026, 10, 1), dt.date(2026, 10, 31)
        bad = []
        rows = masjidal.parse(masjidal_reply(start, end), start, end, bad)
        self.assertEqual(([], 31), (bad, len(rows)))
        self.assertEqual(toronto_times(dt.date(2026, 10, 9)), rows["2026-10-09"])
        for reply, words in [(masjidal_reply(start, end, weekday_shift=1), "printed on another weekday"),
                             (masjidal_reply(start, end, skip=dt.date(2026, 10, 9)), "not the 31 asked"),
                             (masjidal_reply(start, dt.date(2026, 10, 20)), "not the 31 asked"),
                             (masjidal_reply(start, end, status="error"), "not a success")]:
            with self.assertRaises(FetchError) as caught:
                masjidal.parse(reply, start, end, [])
            self.assertIn(words, str(caught.exception))
            self.assertIsNone(TIME_SHAPE.search(str(caught.exception)))

    def test_a_fetch_asks_each_mosque_once_and_checks_each_as_us_chicagos_member(self):
        start, end = masjidal.window(dt.date(2026, 10, 2))
        asked = []

        class Answers:
            owner = None

            def get(self, url, headers=None, data=None, timeout=None, retries=1):
                asked.append(url)
                if "masjid_id=1QL0MDAZ" in url:
                    raise FetchError("HTTP 500")
                return masjidal_reply(start, end)
        ctx = Context("us-chicago", self.root, dt.date(2026, 10, 2), [].append, self.root, http=Answers())
        tables = masjidal.fetch(ctx)
        self.assertEqual(3, len(asked))
        self.assertTrue(all("from_date=2026-10-01&to_date=2026-12-31" in u for u in asked))
        self.assertEqual([("makki-masjid-chicago", "us.chicago.eighteen"), ("mosque-foundation-bridgeview", "us.isna")], [(t.key, t.entry) for t in tables])
        self.assertEqual({"hanafi"}, {t.school for t in tables})
        self.assertEqual(92, len(tables[0].rows))
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("Masjid DarusSalam, Lombard, IL: HTTP 500", ctx.errors[0])
