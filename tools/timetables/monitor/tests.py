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
from fetchers import diyanet, egypt, irn, jakim, kemenag, london, mawaqit, mjc, morocco, muis, qatar  # noqa: E402


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
