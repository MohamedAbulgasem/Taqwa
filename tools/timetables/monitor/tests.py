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

import common  # noqa: E402
from common import Context, FetchError, Http, Table, add_all, nearest_year, norm_time, pm, read_table_rows, redact, runtime_line, write_atomic  # noqa: E402
import backup  # noqa: E402
import fetch  # noqa: E402
from fetch import INDEX_HEADER, Store, due, skip_reason  # noqa: E402
from fetchers import diyanet, egypt, irn, jakim, kemenag, london, mawaqit, morocco, muis, qatar  # noqa: E402


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
        page = ('<script>var prayData = [{"cityId":7,"fajr":"4:05","shrouq":"5:26","thahr":"11:26","aser":"2:51","moghreb":"5:31","ishaa":"7:01"},'
                '{"cityId":3,"fajr":"4:12","shrouq":"5:31","thahr":"11:33","aser":"2:58","moghreb":"5:36","ishaa":"7:06"}];'
                'var calData = {"year":"1448","days":[{"month":4,"days":[{"h":"١٦","m":"28","today":true}]}]};</script>')
        date, vals = qatar.header(page, dt.date(2026, 9, 28))
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
        rows, year = irn.parse_month(month)
        self.assertEqual("2027", year)
        self.assertEqual([(1, ["06:40", "09:15", "12:25", "13:20", "15:30", "17:15", "13:50"]), (2, ["06:39", "09:14", "12:26", "13:21", "15:32", "17:16", "13:52"])], rows)
        with self.assertRaises(FetchError):
            irn.page_config("<html>nothing</html>")
        self.assertNotIn("(new)", str([c[1] for c in diyanet.EUROPE]))
