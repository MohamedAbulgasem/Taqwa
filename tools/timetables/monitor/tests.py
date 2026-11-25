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
from fetchers import muftiyat_kg, ummalqura, ummalqura_cities  # noqa: E402
from prove_tests import Attention, Handoff, Proof, RecipesCatalogue, Workflow  # noqa: E402,F401  (prove's half: proof.py, recipes.tsv, the workflow)
from guard_tests import HeapGuard, QmdbGuard, TzdbGuard  # noqa: E402,F401  (the run's footing: the JDK's tz database, QMDB answering two ways)


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

    def edition(self, first, days, minute="00"):
        t = Table("oslo", "Oslo", 59.9, 10.7, "Europe/Oslo", "NO", "F+E S D A M I", entry="tr.diyanet.europe")
        t.editions = True
        for i in range(days):
            d = dt.date.fromisoformat(first) + dt.timedelta(days=i)
            t.add(d.isoformat(), [f"0{h}:{minute}" for h in range(1, 7)])
        return t

    def test_a_source_that_re_issues_its_days_keeps_each_edition_and_the_live_file_holds_the_latest(self):
        live = os.path.join(self.root, "archive", "tables", "monitor", "src", "oslo.txt")
        editions = os.path.join(self.root, "archive", "tables", "monitor", "src", fetch.EDITIONS, "oslo")
        store = Store(self.root, self.monitor, dt.date(2026, 10, 26))
        self.assertEqual("new", store.record("src", self.edition("2026-10-26", 40)))
        self.assertIn("the first edition held", store.edition_notes["src"][0])
        # A week on, the page's window has moved: the live file is that capture alone, the same edition.
        store = Store(self.root, self.monitor, dt.date(2026, 11, 2))
        store.data["tables"]["src/oslo"] = {"hash": "x", "fetched": "2026-10-26"}
        self.assertEqual("changed", store.record("src", self.edition("2026-11-02", 40)))
        self.assertEqual(("2026-11-02", 40), (min(read_table_rows(live)), len(read_table_rows(live))))
        self.assertIn("the same edition moved on", store.edition_notes["src"][0])
        self.assertIn("7 past days left to the earlier captures", store.edition_notes["src"][0])
        # The source re-issues its year: days ahead withdrawn, days long past put back, one day changed.
        store = Store(self.root, self.monitor, dt.date(2026, 11, 9))
        store.data["tables"]["src/oslo"] = {"hash": "y", "fetched": "2026-11-02"}
        other = self.edition("2026-10-01", 40)
        other.rows["2026-11-05"] = ["09:59"] * 6
        self.assertEqual("changed", store.record("src", other))
        rows = read_table_rows(live)
        self.assertEqual(("2026-10-01", "2026-11-09"), (min(rows), max(rows)), "the live file is the latest edition alone, never a union")
        note = store.edition_notes["src"][0]
        self.assertIn("a new edition", note)
        self.assertIn("1 changed", note)
        self.assertIn("still ahead", note)
        self.assertFalse(re.search(r"\d{1,2}:\d{2}", note), "a note never carries a printed time")
        self.assertEqual(["2026-10-26.txt", "2026-11-02.txt", "2026-11-09.txt"], sorted(os.listdir(editions)))
        self.assertEqual(("2026-11-02", 40), (min(read_table_rows(os.path.join(editions, "2026-11-02.txt"))),
                                              len(read_table_rows(os.path.join(editions, "2026-11-02.txt")))))
        # The same capture again: unchanged, no new edition.
        store.save()
        again = Store(self.root, self.monitor, dt.date(2026, 11, 16))
        self.assertEqual("unchanged", again.record("src", other))
        self.assertEqual({}, again.edition_notes)
        self.assertEqual(3, len(os.listdir(editions)))

    def test_a_capture_that_came_back_in_part_is_kept_as_an_edition_but_never_made_the_live_file(self):
        live = os.path.join(self.root, "archive", "tables", "monitor", "src", "oslo.txt")
        editions = os.path.join(self.root, "archive", "tables", "monitor", "src", fetch.EDITIONS, "oslo")
        store = Store(self.root, self.monitor, dt.date(2026, 11, 2))
        store.record("src", self.edition("2026-11-01", 426))  # the month table and the year table ahead
        store.save()
        # A week on the page holds its month table alone: 30 days, none changed, 389 still ahead withdrawn.
        import types
        module = types.ModuleType("fetchers.fakeshort")
        module.fetch = lambda ctx: [self.edition("2026-11-01", 30)]
        sys.modules["fetchers.fakeshort"] = module
        try:
            ctx = Context("src", self.root, dt.date(2026, 11, 9), lambda line: None, self.monitor, http=FakeHttp([]))
            store = Store(self.root, self.monitor, dt.date(2026, 11, 9))
            status, message, _, statuses = fetch.run_source({"source": "src", "fetcher": "fakeshort"}, store, ctx)
        finally:
            del sys.modules["fetchers.fakeshort"]
        self.assertEqual(("partial", {"oslo": "unchanged"}), (status, statuses))
        self.assertIn("oslo: a capture of 30 days, 2026-11-01..2026-11-30, against the live file's 426, withdraws 396 days still ahead", message)
        self.assertIn("the live file is left as it was", message)
        self.assertEqual(426, len(read_table_rows(live)))
        self.assertEqual(30, len(read_table_rows(os.path.join(editions, "2026-11-09.txt"))))
        self.assertEqual("2026-11-02", store.data["tables"]["src/oslo"]["fetched"])
        self.assertEqual(426, store.data["tables"]["src/oslo"]["rows"])
        # A whole new edition the next week is made the live file as before.
        store.save()
        later = Store(self.root, self.monitor, dt.date(2026, 11, 16))
        self.assertEqual("changed", later.record("src", self.edition("2026-11-01", 425, minute="01")))
        self.assertEqual(425, len(read_table_rows(live)))
        self.assertEqual({}, later.edition_errors)

    def test_a_live_file_merged_before_editions_is_kept_beside_them_the_first_time(self):
        store = Store(self.root, self.monitor, dt.date(2026, 11, 2))
        merged = self.edition("2026-01-01", 400)
        merged.editions = False
        store.record("src", merged)
        store.save()
        later = Store(self.root, self.monitor, dt.date(2026, 11, 9))
        self.assertEqual("changed", later.record("src", self.edition("2026-11-09", 30)))
        editions = os.path.join(self.root, "archive", "tables", "monitor", "src", fetch.EDITIONS, "oslo")
        self.assertEqual(["2026-11-02-merged.txt", "2026-11-09.txt"], sorted(os.listdir(editions)))
        self.assertEqual(400, len(read_table_rows(os.path.join(editions, "2026-11-02-merged.txt"))))
        self.assertEqual(30, len(read_table_rows(os.path.join(self.root, "archive", "tables", "monitor", "src", "oslo.txt"))))
        # The source's message says so, through the driver.
        import types
        module = types.ModuleType("fetchers.fakeeditions")
        module.fetch = lambda ctx: [self.edition("2026-10-01", 30)]
        sys.modules["fetchers.fakeeditions"] = module
        try:
            ctx = Context("src", self.root, dt.date(2026, 11, 16), lambda line: None, self.monitor, http=FakeHttp([]))
            status, message, _, statuses = fetch.run_source({"source": "src", "fetcher": "fakeeditions"}, Store(self.root, self.monitor, dt.date(2026, 11, 16)), ctx)
        finally:
            del sys.modules["fetchers.fakeeditions"]
        self.assertEqual(("ok", {"oslo": "changed"}), (status, statuses))
        self.assertIn("oslo: a new edition, 2026-10-01..2026-10-30", message)

    def test_editions_are_rebuilt_from_the_raw_captures_without_touching_the_archive(self):
        import editions as editions_tool

        def page(first, days):
            months = {v: k for k, v in diyanet.MON.items()}
            rows = []
            for i in range(days):
                d = dt.date.fromisoformat(first) + dt.timedelta(days=i)
                rows.append(f"<tr><td>{d.day} {months[d.month]} {d.year} Pazartesi</td><td>x</td>"
                            + "".join(f"<td>0{h}:{d.day:02d}</td>" for h in range(1, 7)) + "</tr>")
            return ("<table>" + "".join(rows) + "</table>").encode("utf-8")
        import gzip as gz
        raw = os.path.join(self.root, "archive", "raw", "monitor", "tr-diyanet-europe")
        for date, first, days in (("2026-10-31", "2026-10-31", 30), ("2026-11-09", "2026-01-01", 20)):
            os.makedirs(os.path.join(raw, date))
            with open(os.path.join(raw, date, "diyanet-15702.html.gz"), "wb") as f:
                f.write(gz.compress(page(first, days)))
        os.makedirs(self.monitor)
        with open(os.path.join(self.monitor, "diyanet-ids.json"), "w") as f:
            json.dump({"cities": {"oslo": "15702"}}, f)
        live = os.path.join(self.root, "archive", "tables", "monitor", "tr-diyanet-europe", "oslo.txt")
        os.makedirs(os.path.dirname(live))
        with open(live, "w") as f:
            f.write("# merged\n2026-01-01 01:01 02:01 03:01 04:01 05:01 06:01\n2026-12-01 01:01 02:01 03:01 04:01 05:01 06:01\n")
        out = os.path.join(self.root, "out")
        self.assertEqual(0, editions_tool.main(["--official", self.root, "--source", "tr-diyanet-europe", "--out", out]))
        folder = os.path.join(out, "tr-diyanet-europe", fetch.EDITIONS, "oslo")
        self.assertEqual(["2026-10-31.txt", "2026-11-09.txt", "before-merged.txt"], sorted(os.listdir(folder)))
        self.assertEqual(30, len(read_table_rows(os.path.join(folder, "2026-10-31.txt"))))
        rebuilt = read_table_rows(os.path.join(out, "tr-diyanet-europe", "oslo.txt"))
        self.assertEqual(("2026-01-01", 20), (min(rebuilt), len(rebuilt)), "the live file rebuilt as the latest edition alone")
        with open(live) as f:
            self.assertTrue(f.read().startswith("# merged"), "--out leaves the archive alone")
        # --in-place is refused, writing nothing, unless the latest raw capture is the one the index records
        # and the live file agrees with it on every day both hold.
        in_place = ["--official", self.root, "--source", "tr-diyanet-europe", "--in-place"]
        editions_dir = os.path.join(os.path.dirname(live), fetch.EDITIONS)
        self.assertEqual(1, editions_tool.main(in_place), "not in the index")
        record = {"source": "tr-diyanet-europe", "key": "oslo", "path": "archive/tables/monitor/tr-diyanet-europe/oslo.txt",
                  "name": "Oslo", "zone": "Europe/Oslo", "cc": "NO", "columns": "F+E S D A M I", "hash": "old",
                  "first": "2026-01-01", "last": "2026-12-01", "rows": 2, "fetched": "2026-10-31"}
        hashes = os.path.join(self.monitor, "hashes.json")
        with open(hashes, "w") as f:
            json.dump({"sources": {}, "tables": {"tr-diyanet-europe/oslo": record}}, f)
        self.assertEqual(1, editions_tool.main(in_place), "the index records an older capture than the latest raw one")
        record["fetched"] = "2026-11-09"
        with open(live, "w") as f:
            f.write("# merged\n2026-01-01 01:59 02:01 03:01 04:01 05:01 06:01\n2026-12-01 01:01 02:01 03:01 04:01 05:01 06:01\n")
        with open(hashes, "w") as f:
            json.dump({"sources": {}, "tables": {"tr-diyanet-europe/oslo": record}}, f)
        self.assertEqual(1, editions_tool.main(in_place), "the live file differs from the latest capture")
        self.assertFalse(os.path.exists(editions_dir))
        with open(live, "w") as f:
            f.write("# merged\n2026-01-01 01:01 02:01 03:01 04:01 05:01 06:01\n2026-12-01 01:01 02:01 03:01 04:01 05:01 06:01\n")
        self.assertEqual(0, editions_tool.main(in_place))
        self.assertEqual(["2026-10-31.txt", "2026-11-09-merged.txt", "2026-11-09.txt"], sorted(os.listdir(os.path.join(editions_dir, "oslo"))))
        self.assertEqual(20, len(read_table_rows(live)))
        with open(hashes) as f:
            held = json.load(f)["tables"]["tr-diyanet-europe/oslo"]
        self.assertEqual((20, "2026-01-01", "2026-01-20", "2026-11-09"), (held["rows"], held["first"], held["last"], held["fetched"]))
        t = Table("oslo", "Oslo", 0, 0, "Europe/Oslo", "NO", "F+E S D A M I")
        t.rows = read_table_rows(live)
        self.assertEqual(t.content_hash(), held["hash"])
        with open(os.path.join(self.root, "archive", "tables", "monitor", "index.tsv")) as f:
            self.assertIn(held["hash"], f.read())

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

    def test_jakim_slips_are_left_out_by_the_table_itself(self):
        # An invented year: each column a slow ramp of hours no Malaysian table prints (ruling R69 keeps
        # printed times out of tests), then the shapes of e-solat's own slips put in.
        rows = {}
        for i in range(40):
            d = (dt.date(2027, 5, 1) + dt.timedelta(days=i)).isoformat()
            rows[d] = [f"0{h}:{10 + i // 4:02d}" for h in (1, 2)] + [f"1{h}:{10 + i // 4:02d}" for h in (2, 4, 6, 8)]
        bad = dict(rows)
        for d in ("2027-05-20", "2027-05-21", "2027-05-22", "2027-05-23", "2027-05-24", "2027-05-25"):
            bad[d] = bad[d][:4] + ["06" + bad[d][4][2:]] + bad[d][5:]   # Maghrib twelve hours off, six days running
        bad["2027-05-10"] = bad["2027-05-10"][:3] + ["13" + bad["2027-05-10"][3][2:]] + bad["2027-05-10"][4:]  # Asr an hour early
        bad["2027-05-30"] = ["13" + bad["2027-05-30"][0][2:]] + bad["2027-05-30"][1:]  # Fajr after noon
        clean, notes, dropped, standing = jakim.slips(bad)
        self.assertEqual({}, standing)
        self.assertEqual(set(), dropped)
        self.assertEqual(["-"] * 6, [clean[d][4] for d in ("2027-05-20", "2027-05-21", "2027-05-22", "2027-05-23", "2027-05-24", "2027-05-25")])
        self.assertEqual("-", clean["2027-05-10"][3])
        self.assertEqual("-", clean["2027-05-30"][0])
        self.assertEqual(8, len(notes), notes)
        self.assertIn("2027-05-10 Asr about 1 hour early against its neighbours", notes)
        self.assertIn("2027-05-20 Maghrib printed before noon", notes)
        self.assertFalse(any(re.search(r"\d{1,2}:\d{2}", n) for n in notes), "a note never carries a printed time")
        changed = {(d, c) for d in rows for c in range(6) if clean[d][c] != rows[d][c]}
        self.assertEqual(8, len(changed), "nothing else is touched")
        self.assertEqual((rows, [], set(), {}), jakim.slips(rows), "a clean table passes as it is")
        # A day still out of order once its slips are out is left out whole.
        close = {d: v[:3] + ["13" + v[3][2:], "13:" + f"{int(v[3][3:]) + 20:02d}"] + v[5:] for d, v in rows.items()}
        close["2027-05-15"] = close["2027-05-15"][:4] + ["13:" + f"{int(close['2027-05-15'][3][3:]) - 5:02d}"] + close["2027-05-15"][5:]
        clean, notes, dropped, _ = jakim.slips(close)
        self.assertEqual({"2027-05-15"}, dropped)
        self.assertEqual(["-"] * 6, clean["2027-05-15"], "every cell '-', so the merge cannot keep an earlier capture's row for it")
        # A run of days out of line with the days either side in two or more columns, though far inside an hour
        # (SBH09's shape of 28-31 March 2026), is NEVER left out (ruling R228 as its review round left it: a later
        # print dropped is an early start listed away): every cell stays as printed, and the run is handed back
        # with its direction and minutes. One column alone out of line is nothing (a table that steps keeps its
        # days), nor is a table whose every column steps for good by less.
        run = dict(rows)
        for d in ("2027-05-18", "2027-05-19", "2027-05-20", "2027-05-21"):
            v = run[d]
            run[d] = [v[0][:3] + f"{int(v[0][3:]) + 10:02d}", v[1][:3] + f"{int(v[1][3:]) + 10:02d}", v[2], v[3][:3] + f"{int(v[3][3:]) + 18:02d}", v[4], v[5]]
        tidy, said, out, standing = jakim.slips(run)
        self.assertEqual(set(), out, "an out-of-line day is never left out")
        self.assertEqual([], said)
        self.assertEqual(run, tidy, "every cell as printed")
        self.assertEqual(["2027-05-18", "2027-05-19", "2027-05-20", "2027-05-21"], sorted(standing))
        self.assertTrue(standing["2027-05-18"].startswith("printed Fajr about 1"), standing)
        self.assertIn("Asr about 1", standing["2027-05-18"])
        self.assertIn("min later", standing["2027-05-18"])
        self.assertNotIn("earlier", standing["2027-05-18"])
        self.assertFalse(any(re.search(r"\d{1,2}:\d{2}", n) for n in standing.values()), "never a printed time")
        before = dict(rows)
        for d in ("2027-05-18", "2027-05-19"):
            v = before[d]
            before[d] = [v[0][:3] + f"{int(v[0][3:]) - 9:02d}", v[1], v[2], v[3][:3] + f"{int(v[3][3:]) - 9:02d}", v[4], v[5]]
        tidy, said, out, standing = jakim.slips(before)
        self.assertEqual((before, [], set()), (tidy, said, out), "an earlier print is kept as printed too")
        self.assertIn("min earlier", standing["2027-05-19"])
        self.assertNotIn("later", standing["2027-05-19"])
        one = dict(rows)
        one["2027-05-19"] = one["2027-05-19"][:3] + [one["2027-05-19"][3][:3] + f"{int(one['2027-05-19'][3][3:]) + 18:02d}"] + one["2027-05-19"][4:]
        self.assertEqual((one, [], set(), {}), jakim.slips(one), "one column out of line alone is kept, and is nothing to rule")
        step = {d: ([c[:3] + f"{int(c[3:]) + 5:02d}" for c in v] if "2027-05-15" <= d <= "2027-05-27" else v) for d, v in rows.items()}
        self.assertEqual((step, [], set(), {}), jakim.slips(step), "a table that steps by less keeps its days")
        # Through the store, the day's earlier row in the live file is replaced, not kept.
        t = Table("WLY02-2027", "Labuan", None, None, "Asia/Kuching", "MY", "F+E S D A M I")
        t.rows = {d: list(v) for d, v in close.items()}
        root = tempfile.mkdtemp("jakim-merge")
        try:
            store = Store(root, os.path.join(root, "monitor"), dt.date(2026, 11, 12))
            store.record("my-jakim", t)
            again = Table("WLY02-2027", "Labuan", None, None, "Asia/Kuching", "MY", "F+E S D A M I")
            for d, v in clean.items():
                again.add(d, v)
            store.record("my-jakim", again)
            held = read_table_rows(os.path.join(root, "archive", "tables", "monitor", "my-jakim", "WLY02-2027.txt"))
            self.assertEqual(["-"] * 6, held["2027-05-15"])
        finally:
            shutil.rmtree(root)

    def test_jakim_out_of_line_days_are_kept_and_raised_unless_the_engine_holds_them(self):
        # An invented year (ruling R69): a slow ramp, then four days two columns later than the days either
        # side. In a zone and on days whose starts the engine holds to the print it is a note; anywhere else an
        # error, so a human rules it. Either way every cell is written as printed and no message carries a time.
        self.assertEqual({"SBH09": ("2026-03-28", "2026-03-31")}, jakim.HELD_AS_PRINTED)
        self.assertTrue(all(z in {zone for zone, _, _ in jakim.ZONES} for z in jakim.HELD_AS_PRINTED))
        self.assertTrue(jakim.held_as_printed("SBH09", "2026-03-28") and jakim.held_as_printed("SBH09", "2026-03-31"))
        self.assertFalse(jakim.held_as_printed("SBH09", "2026-03-27") or jakim.held_as_printed("SBH09", "2026-04-01"))
        self.assertFalse(jakim.held_as_printed("SBH08", "2026-03-28") or jakim.held_as_printed("SBH09", "2027-03-28"))
        rows = {}
        for i in range(40):
            d = (dt.date(2026, 3, 10) + dt.timedelta(days=i)).isoformat()
            rows[d] = [f"0{h}:{10 + i // 4:02d}" for h in (1, 2)] + [f"1{h}:{10 + i // 4:02d}" for h in (2, 4, 6, 8)]
        for d in ("2026-03-28", "2026-03-29", "2026-03-30", "2026-03-31"):
            v = rows[d]
            rows[d] = [v[0][:3] + f"{int(v[0][3:]) + 10:02d}", v[1], v[2], v[3][:3] + f"{int(v[3][3:]) + 18:02d}", v[4], v[5]]
        root = tempfile.mkdtemp("monitor-jakim-line")
        try:
            for zone, errors, notes in (("SBH09", 0, 1), ("SBH08", 1, 0)):
                ctx = Context("my-jakim", root, dt.date(2026, 11, 13), lambda line: None, root, http=FakeHttp([]))
                t = Table(f"{zone}-2026", zone, None, None, "Asia/Kuching", "MY", "F+E S D A M I")
                self.assertEqual(40, jakim.checked(ctx, t, rows, f"{zone} 2026", zone))
                self.assertEqual(rows["2026-03-29"], t.rows["2026-03-29"], "kept as printed")
                self.assertEqual((errors, notes), (len(ctx.errors), len(ctx.notes)), (ctx.errors, ctx.notes))
                said = (ctx.errors + ctx.notes)[0]
                self.assertIn("kept as printed", said)
                self.assertIn("2026-03-28 printed Fajr about 10 min later, Asr about 18 min later than the days either side", said)
                self.assertIn("for a human to rule (4)" if errors else "held by the engine (4)", said)
                self.assertIsNone(re.search(r"\d{1,2}:\d{2}", said), "never a printed time")
        finally:
            shutil.rmtree(root)

    def test_jakim_takes_the_year_ahead_from_esolat_and_the_mirror_only_when_esolat_fails(self):
        def year(zone, y):
            return json.dumps({"status": "OK!", "serverTime": "x", "zone": zone, "prayerTime": [
                {"date": (dt.date(y, 1, 1) + dt.timedelta(days=i)).strftime("%d-%b-%Y").replace("May", "Mei"),
                 "fajr": "01:00:00", "syuruk": "02:00:00", "dhuhr": "13:00:00", "asr": "14:00:00", "maghrib": "15:00:00", "isha": "16:00:00"}
                for i in range(365)]}).encode()
        no_record = json.dumps({"prayerTime": {"data": ["No data available for the current request"]}, "status": "NO_RECORD!"}).encode()
        mirror_month = json.dumps({"prayers": [{"day": d, "fajr": 1798736400 + d * 86400, "syuruk": 1798740000 + d * 86400,
                                                 "dhuhr": 1798779600 + d * 86400, "asr": 1798783200 + d * 86400,
                                                 "maghrib": 1798786800 + d * 86400, "isha": 1798790400 + d * 86400} for d in range(1, 29)]}).encode()
        answers = []
        for zone, _, _ in jakim.ZONES:
            answers.append((jakim.ESOLAT + zone, year(zone, 2026)))
        posted = []

        class Http(FakeHttp):
            def get(self, url, headers=None, data=None, timeout=None, retries=1):
                if url.startswith(jakim.ESOLAT_DURATION):
                    zone = url.rsplit("=", 1)[1]
                    posted.append((zone, data))
                    if zone == "SGR01":
                        return no_record
                    if zone == "SBH01":
                        raise FetchError("HTTP 500 from e-solat")
                    if zone == "SBH07":
                        raise FetchError("HTTP 500 from e-solat")
                    return year(zone, 2027)
                return super().get(url, headers=headers, data=data, timeout=timeout, retries=retries)
        answers.append((jakim.MIRROR + "SBH01?year=2027&month=", mirror_month))
        answers.append((jakim.MIRROR + "SBH07?year=2027&month=1", FetchError("HTTP 404 from the mirror")))
        root = tempfile.mkdtemp("monitor-jakim")
        try:
            http = Http(answers)
            ctx = Context("my-jakim", root, dt.date(2026, 11, 12), lambda line: None, root, http=http)
            tables = {t.key: t for t in jakim.fetch(ctx)}
        finally:
            shutil.rmtree(root)
        self.assertEqual(60, len(jakim.ZONES), "every zone of e-solat's list: the first thirteen, the 37 of the zone round of 13 Nov 2026, the ten of its review round")
        self.assertEqual(len(jakim.ZONES), len({z for z, _, _ in jakim.ZONES}), "no zone twice")
        self.assertEqual(len(jakim.ZONES), len(posted))
        self.assertEqual({"datestart": "2027-01-01", "dateend": "2027-12-31"}, posted[0][1])
        self.assertEqual(365, len(tables["WLY02-2027"].rows))
        self.assertIn("period=duration", tables["WLY02-2027"].source_line)
        self.assertIn("(POST, its date-range form", tables["WLY02-2027"].source_line, "the year ahead is a POST and its source line says so")
        self.assertNotIn("POST", tables["WLY02-2026"].source_line, "the current year is a plain GET")
        self.assertNotIn("SGR01-2027", tables)
        self.assertNotIn("SBH07-2027", tables)
        self.assertIn("(mirror)", tables["SBH01-2027"].name)
        self.assertEqual(12 * 28, len(tables["SBH01-2027"].rows))
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("2027 not published yet for SGR01, SBH07" in n for n in ctx.notes), ctx.notes)
        self.assertEqual(len(jakim.ZONES) + 12 + 1, len(http.urls), "e-solat's year for each zone, then the mirror for the two zones e-solat failed")

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
        # A page headed with another column (an imsak before Fajr, say) is refused, never read one column off.
        seven = ('<table id="horaire"><tr><th>الأيام</th><th>رمضان</th><th>فبراير / مارس</th><th>الإمساك</th><th>الصبح</th><th>الشروق</th>'
                 "<th>الظهر</th><th>العصر</th><th>المغرب</th><th>العشاء</th></tr>"
                 "<tr><td>الأحد</td><td>1</td><td>13</td><td>01:13</td><td>02:13</td><td>03:13</td><td>04:13</td><td>05:13</td><td>06:13</td><td>07:13</td></tr></table>")
        with self.assertRaisesRegex(FetchError, "time columns are not the six expected"):
            morocco.parse(seven, dt.date(2027, 2, 20))
        with self.assertRaisesRegex(FetchError, "time columns are not the six expected"):
            morocco.parse(page.replace("<th>الشروق</th>", "<th>الفجر</th>"), dt.date(2026, 9, 28))

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
        keys = [p[0] for p in qmdb.POINTS]
        self.assertEqual(len(keys), len(set(keys)), "a key names one place")
        self.assertEqual(len(qmdb.POINTS), len({(p[2], p[3]) for p in qmdb.POINTS}), "a point is one place")
        first = {"almaty", "astana", "kokshetau", "kostanay", "pavlodar", "petropavl",
                 "isakovka", "krasny-yar", "kulomzino", "oral", "aksay", "aktobe", "shalqar", "atyrau", "aktau",
                 "kyzylorda", "oteshqali", "ayagoz", "vagulino", "bugrovoe", "pulemetovka", "spasovka", "khromtau",
                 "arkalyk", "oskemen", "mamyrsu", "makat", "shymkent", "zhenis"}
        self.assertEqual(first, set(keys[:29]))
        # The city-points round (9 Oct 2026): QMDB's place for every other city of the app's list but the five whose
        # point QMDB gives to two places (its year API answers HTTP 500 there).
        self.assertEqual(87, len(keys))
        self.assertTrue({"karaganda", "taraz", "semey", "turkistan", "baikonur", "zhanaozen"} <= set(keys))
        self.assertEqual(set(), {"shchuchinsk", "zhitikara", "esik", "abay-karaganda", "abay-turkistan"} & set(keys))
        # QMDB's offsets switch at 48N, its AngleBased rule binds from about 46N (kz-north-west round, 6 Oct 2026).
        self.assertEqual({"almaty", "aktau", "kyzylorda", "shymkent", "zhenis"},
                         {p[0] for p in qmdb.POINTS[:29] if float(p[2]) < 46.0})
        self.assertEqual({"shalqar", "atyrau", "oteshqali", "ayagoz", "mamyrsu", "makat"},
                         {p[0] for p in qmdb.POINTS[:29] if 46.0 <= float(p[2]) < 48.0})
        # North of Petropavl to the country's edge: Isakovka, 55.4N, the northernmost of QMDB's list.
        self.assertEqual(55.405802, max(float(p[2]) for p in qmdb.POINTS))
        # ... and to its southern edge: Zhenis, 40.6N, the southernmost of its list.
        self.assertEqual(40.599543, min(float(p[2]) for p in qmdb.POINTS))
        # Every zone the app's Kazakh cities use has a point (Asia/Almaty, Qostanay, Oral, Aqtobe, Atyrau, Aqtau, Qyzylorda).
        self.assertEqual({"Asia/Almaty", "Asia/Qostanay", "Asia/Oral", "Asia/Aqtobe", "Asia/Atyrau", "Asia/Aqtau", "Asia/Qyzylorda"},
                         {p[4] for p in qmdb.POINTS})

    def test_ummalqura_cities(self):
        # KACST's own city list (173 places): the weekly source's twelve and this monthly source's 161, each once,
        # each at a point inside Saudi Arabia, and read the same way (ummalqura.parse).
        self.assertEqual("sa-ummalqura-cities", ummalqura_cities.SOURCE)
        self.assertIs(ummalqura.parse, ummalqura_cities.parse)
        self.assertEqual(161, len(ummalqura_cities.POINTS))
        both = ummalqura.POINTS + ummalqura_cities.POINTS
        self.assertEqual(173, len({p[0] for p in both}), "a key names one place")
        self.assertEqual(173, len({(p[2], p[3]) for p in both}), "a point is one place")
        for key, name, lat, lon in both:
            self.assertTrue(16.0 <= lat <= 32.5 and 34.0 <= lon <= 56.0, key)

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

    def test_the_shipped_interval_between_requests_is_at_least_two_seconds(self):
        # The rule (13 November 2026): one request at a time, at least 2 s apart, whatever the host. setUp saved the
        # shipped constant before zeroing it for the other tests; the source is read too, so no test's override hides it.
        self.assertGreaterEqual(self._interval, 2.0)
        with open(os.path.join(HERE, "common.py"), encoding="utf-8") as f:
            source = f.read()
        shipped = [line for line in source.splitlines() if line.startswith("MIN_INTERVAL =")]
        self.assertEqual(1, len(shipped))
        self.assertGreaterEqual(float(shipped[0].split("=")[1].split("#")[0]), 2.0)
        # A fetcher's own gap is never a way under it: the one written down today is no shorter.
        self.assertGreaterEqual(muftiyat_kg.KG_GAP, self._interval)

    def test_a_fetcher_can_ask_for_a_longer_gap_and_never_a_shorter(self):
        slept = []
        common.time.sleep = slept.append
        common.MIN_INTERVAL = 2.0
        self.fake(lambda url: common.urllib.error.HTTPError(url, 404, "no", {}, None))
        http = Http(self.lines.append)
        for gap in (None, 3.0, 0.5, 0):
            http._last = common.time.monotonic()
            with self.assertRaises(FetchError):
                http.get("https://a.example/1", gap=gap)
        self.assertEqual(4, len(slept))
        for need, got in zip((2.0, 3.0, 2.0, 2.0), slept):
            self.assertLessEqual(got, need)
            self.assertGreater(got, need - 0.2)

    def test_the_curl_fallback_is_a_second_request_and_waits_its_interval(self):
        # The review of 14 November 2026: where Python's TLS stack cannot reach a site, the same request goes through
        # curl. That is a second request to the site, so it waits like any other, is counted, and the next waits from it.
        slept = []
        common.time.sleep = slept.append
        common.MIN_INTERVAL = 2.0
        ran = []

        class Done:
            returncode = 0
            stdout = b"body\n200"
            stderr = b""

        def run(cmd, **kw):
            ran.append(cmd)
            return Done()

        self.addCleanup(setattr, common.subprocess, "run", common.subprocess.run)
        common.subprocess.run = run
        for outcome in (lambda url: common.urllib.error.URLError(common.ssl.SSLError("handshake")), lambda url: common.ssl.SSLError("handshake")):
            del slept[:], ran[:]
            self.fake(outcome)
            http = Http(self.lines.append)
            http._last = common.time.monotonic()
            self.assertEqual(b"body", http.get("https://a.example/1", gap=3.0))
            self.assertEqual(1, len(ran))
            self.assertEqual(2, http.requests)
            self.assertEqual(2, len(slept))
            for got in slept:
                self.assertLessEqual(got, 3.0)
                self.assertGreater(got, 2.8)
            # The next request waits from the curl one, not from the attempt before it.
            self.assertLess(common.time.monotonic() - http._last, 0.5)

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
        shell = ('<!doctype html><html><head><script type="module" crossorigin src="/assets/index-Ab12.js"></script></head>'
                 '<body>\n    <div id="root"></div>\n  </body></html>')
        with self.assertRaisesRegex(FetchError, "now an app whose calendars come from a data API that needs a key"):
            irn.page_config(shell)
        # The AJAX URL must be the site's own.
        elsewhere = page.replace("https://bonnetid.info/wp-admin/admin-ajax.php", "https://evil.example/admin-ajax.php")
        with self.assertRaises(FetchError) as host:
            irn.page_config(elsewhere)
        self.assertIn("evil.example, not bonnetid.info", str(host.exception))
        self.assertNotIn("(new)", str([c[1] for c in diyanet.EUROPE]))


class KyrgyzCalendar(unittest.TestCase):
    """muftiyat_kg's calendar page, invented: its locality checked, and only a whole year accepted (the review of 12 Nov 2026)."""

    @staticmethod
    def page(point_id, year, skip=()):
        first = dt.date(year, 1, 1)
        days = []
        for i in range((dt.date(year + 1, 1, 1) - first).days):
            date = (first + dt.timedelta(days=i)).isoformat()
            if date not in skip:
                days.append({"date": date, "fajr": "05:00", "sunrise": "06:30", "dhuhr": "12:30", "asr": "16:00",
                             "sunset": "18:30", "maghrib": "18:37", "isha": "20:00"})
        return f'<select><option selected value="{point_id}">x</option></select>\n<script>days = {json.dumps(days)};\n</script>'.encode()

    def test_a_whole_year_of_its_own_locality(self):
        self.assertEqual(365, len(muftiyat_kg.parse_calendar(self.page(7, 2027), 7, 2027)))
        self.assertEqual(366, len(muftiyat_kg.parse_calendar(self.page(7, 2028), 7, 2028)))
        with self.assertRaises(FetchError):
            muftiyat_kg.parse_calendar(self.page(8, 2027), 7, 2027)

    def test_a_cut_short_year_is_refused(self):
        with self.assertRaisesRegex(FetchError, "364 days of 2027's 365, 2027-12-31"):
            muftiyat_kg.parse_calendar(self.page(7, 2027, skip={"2027-12-31"}), 7, 2027)
        with self.assertRaises(FetchError):
            muftiyat_kg.parse_calendar(self.page(7, 2027, skip={"2027-06-15"}), 7, 2027)


class KyrgyzFetch(unittest.TestCase):
    """muftiyat_kg's run against canned answers: each page asked with its own locality's cookie on a plain GET, and
    no faster than the fetcher's own gap, which is never below 2 s."""

    def test_each_page_carries_its_locality_and_the_gap(self):
        listing = json.dumps([{"id": 1, "title": "r", "locations": [{"id": p[0], "title": p[2]} for p in muftiyat_kg.POINTS]}]).encode()
        http = FakeHttp([(muftiyat_kg.LIST_URL, listing), ("https://muftiyat.kg/kg/calendar/", FetchError("no page"))])
        root = tempfile.mkdtemp("monitor-kg")
        self.addCleanup(shutil.rmtree, root)
        lines = []
        ctx = Context(muftiyat_kg.SOURCE, root, dt.date(2026, 11, 13), lines.append, os.path.join(root, "monitor"), http=http)
        self.assertEqual([], muftiyat_kg.fetch(ctx))
        self.assertGreaterEqual(muftiyat_kg.KG_GAP, 2.0)
        pages = [(u, g, h) for u, g, h in zip(http.urls, http.gaps, http.sent) if "/calendar/?" in u]
        self.assertEqual(2 * len(muftiyat_kg.POINTS), len(pages))
        self.assertTrue(all(g == muftiyat_kg.KG_GAP for _, g, _ in pages))
        cookies = [h["Cookie"] for _, _, h in pages]
        self.assertEqual([f"point={p[0]}" for p in muftiyat_kg.POINTS] * 2, cookies)
        self.assertTrue(all(set(h) == {"Cookie"} for _, _, h in pages), "one header, the locality, and nothing else")


class UnreadFormat(unittest.TestCase):
    """Ruling R281 (the second audit's G19): a fetcher whose answers came back in a format it cannot read fails, never
    'ok' with no table. Every time invented, in a 12-hour notation no fetcher reads (never a printed time)."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-unread")
        self.addCleanup(shutil.rmtree, self.root)

    def ctx(self, source, http, today=dt.date(2026, 11, 16)):
        return Context(source, self.root, today, lambda line: None, os.path.join(self.root, "monitor"), http=http)

    @staticmethod
    def esolat_year(y, cell):
        return json.dumps({"status": "OK!", "serverTime": "x", "prayerTime": [
            {"date": (dt.date(y, 1, 1) + dt.timedelta(days=i)).strftime("%d-%b-%Y").replace("May", "Mei"),
             "fajr": cell(1), "syuruk": cell(2), "dhuhr": cell(13), "asr": cell(14), "maghrib": cell(15), "isha": cell(16)}
            for i in range(365)]}).encode()

    def test_jakim_fails_when_no_cell_can_be_read(self):
        def am_pm(h):
            return f"{(h - 1) % 12 + 1}:01 {'am' if h < 12 else 'pm'}"
        http = FakeHttp([(jakim.ESOLAT, self.esolat_year(2026, am_pm))])
        ctx = self.ctx(jakim.SOURCE, http, today=dt.date(2026, 6, 1))
        self.assertEqual([], jakim.fetch(ctx))
        self.assertEqual(len(jakim.ZONES), len(http.urls))
        self.assertEqual(2 * len(jakim.ZONES), len(ctx.errors), "each zone: its unread days, and no day read")
        self.assertTrue(all("not a time" in e or "no day of the answer could be read" in e for e in ctx.errors), ctx.errors[:2])
        self.assertIn("WLY01 2026: 365 of 365 days have a cell that is not a time (the first 2026-01-01)", "\n".join(ctx.errors))
        self.assertFalse(any(TIME_SHAPE.search(e) for e in ctx.errors), "no printed time in a message (ruling R69)")

    def test_jakim_takes_the_days_it_can_read_and_errs_for_the_rest(self):
        body = json.loads(self.esolat_year(2026, lambda h: f"{h:02d}:01:00"))
        body["prayerTime"][40]["maghrib"] = "3:01 pm"
        http = FakeHttp([(jakim.ESOLAT, json.dumps(body).encode())])
        ctx = self.ctx(jakim.SOURCE, http, today=dt.date(2026, 6, 1))
        tables = {t.key: t for t in jakim.fetch(ctx)}
        self.assertEqual(364, len(tables["WLY01-2026"].rows))
        self.assertNotIn("2026-02-10", tables["WLY01-2026"].rows)
        self.assertIn("WLY01 2026: 1 of 365 days have a cell that is not a time (the first 2026-02-10): "
                      "e-solat's format may have changed; those days are not taken", ctx.errors)
        self.assertEqual(len(jakim.ZONES), len(ctx.errors), "one error a zone, and no table refused whole")

    def test_the_kyrgyz_muftiate_fails_when_a_cell_cannot_be_read(self):
        listing = json.dumps([{"id": 1, "title": "r", "locations": [{"id": p[0], "title": p[2]} for p in muftiyat_kg.POINTS]}]).encode()

        class Http(FakeHttp):
            def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None):
                if "/calendar/?" in url:
                    self.urls.append(url)
                    pid = int(headers["Cookie"].split("=")[1])
                    year = int(url.rsplit("-", 1)[1])
                    page = KyrgyzCalendar.page(pid, year).decode()
                    if pid == muftiyat_kg.POINTS[0][0]:
                        page = page.replace('"maghrib": "18:37"', '"maghrib": "6:37 PM"', 1)
                    elif pid == muftiyat_kg.POINTS[1][0]:
                        page = page.replace('"18:37"', '"6:37 PM"').replace('"05:00"', '"5:00 AM"')
                    return page.encode()
                return super().get(url, headers=headers, data=data, timeout=timeout, retries=retries, gap=gap)
        http = Http([(muftiyat_kg.LIST_URL, listing)])
        ctx = self.ctx(muftiyat_kg.SOURCE, http)
        tables = {t.key for t in muftiyat_kg.fetch(ctx)}
        first, second = muftiyat_kg.POINTS[0], muftiyat_kg.POINTS[1]
        self.assertNotIn(f"{first[1]}-2026", tables, "a year with one unread day is not filed in part: the held year stands")
        self.assertNotIn(f"{second[1]}-2027", tables)
        self.assertIn(f"{muftiyat_kg.POINTS[2][1]}-2026", tables)
        self.assertIn(f"{first[2]} 2026: 1 of 365 days could not be read (a cell that is not a time): the Muftiate's page may have changed; the year is not taken", ctx.errors)
        self.assertIn(f"{second[2]} 2027: 365 of 365 days could not be read (a cell that is not a time): the Muftiate's page may have changed; the year is not taken", ctx.errors)
        self.assertEqual(4, len(ctx.errors), ctx.errors)

    def test_run_source_fails_a_fetcher_that_read_nothing_it_was_given(self):
        import types

        def fetch_nothing(ctx):
            ctx.made += 3
            for d in ("2026-11-01", "2026-11-02", "2026-11-03"):
                ctx.note(f"town {d}: cell 1 is not a time")
            return []

        def fetch_not_yet(ctx):
            ctx.made += 1
            ctx.note("2027 not published yet")
            return []
        out = {}
        for name, fn in (("fakeunread", fetch_nothing), ("fakenotyet", fetch_not_yet)):
            module = types.ModuleType("fetchers." + name)
            module.fetch = fn
            sys.modules["fetchers." + name] = module
            try:
                ctx = self.ctx("src", FakeHttp([]))
                out[name] = fetch.run_source({"source": "src", "fetcher": name}, Store(self.root, os.path.join(self.root, "monitor"), dt.date(2026, 11, 16)), ctx)
            finally:
                del sys.modules["fetchers." + name]
        status, message, requests, statuses = out["fakeunread"]
        self.assertEqual(("failed", 3, {}), (status, requests, statuses))
        self.assertIn("no table from 3 requests: 3 answers had a cell that is not a time (the source's format may have changed)", message)
        self.assertEqual("ok", out["fakenotyet"][0], "a source with nothing new and nothing unread is still ok")


class PrintedImsak(unittest.TestCase):
    """Ruling R284 (the second audit's G25): JAKIM's and Kemenag's fetchers keep the printed imsak beside each table as
    its own capture, `<key>-imsak`, [Subuh, imsak]; kept as printed, an imsak far before its Subuh raised for a human,
    one after it '-' and an error, one at it kept (Perlis prints no precaution). Every time invented."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-imsak")
        self.addCleanup(shutil.rmtree, self.root)

    @staticmethod
    def before(t, minutes):
        h, m = (int(x) for x in t.split(":")[:2])
        total = h * 60 + m - minutes
        return f"{total // 60:02d}:{total % 60:02d}"

    def esolat_year(self, y, imsak=10, odd=None):
        """A whole year with an imsak `imsak` minutes before Subuh; `odd` {day index: minutes before Subuh}."""
        rows = []
        for i in range(365 if y % 4 else 366):
            date = dt.date(y, 1, 1) + dt.timedelta(days=i)
            fajr = f"05:{i % 50 + 10:02d}:00"
            rows.append({"date": date.strftime("%d-%b-%Y").replace("May", "Mei"), "imsak": self.before(fajr, (odd or {}).get(i, imsak)) + ":00",
                         "fajr": fajr, "syuruk": "07:00:00", "dhuhr": "13:00:00", "asr": "16:00:00", "maghrib": "19:00:00", "isha": "20:00:00"})
        return json.dumps({"status": "OK!", "serverTime": "x", "prayerTime": rows}).encode()

    def test_jakim_keeps_each_zones_imsak_beside_its_table(self):
        # Perlis's pattern: its imsak at its Subuh on most days, a minute before it on some (my-jakim.tsv: no precaution).
        perlis = self.esolat_year(2026, imsak=0, odd={i: 1 for i in range(0, 365, 3)})
        answers = [(jakim.ESOLAT + "PLS01", perlis), (jakim.ESOLAT + "WLY01", self.esolat_year(2026, odd={40: 20, 41: -1}))]
        answers += [(jakim.ESOLAT + z, self.esolat_year(2026)) for z, _, _ in jakim.ZONES if z not in ("PLS01", "WLY01")]
        ctx = Context(jakim.SOURCE, self.root, dt.date(2026, 1, 15), lambda line: None, self.root, http=FakeHttp(answers))
        tables = {t.key: t for t in jakim.fetch(ctx)}
        self.assertEqual(2 * len(jakim.ZONES), len(tables))
        wly = tables["WLY01-2026-imsak"]
        self.assertEqual(("- Im", "my.jakim/WLY01", 365), (wly.columns, wly.entry, len(wly.rows)))
        pls = tables["PLS01-2026-imsak"]
        self.assertEqual("- E", pls.columns, "Perlis's imsak is read as its end of eating, as my-jakim.tsv reads it")
        self.assertEqual(365, sum(1 for r in pls.rows.values() if r[1] != "-"), "an imsak at its Subuh is kept, never an error")
        self.assertEqual(pls.rows["2026-01-02"][0], pls.rows["2026-01-02"][1])
        self.assertEqual([tables["WLY01-2026"].rows["2026-01-01"][0], "05:00"], wly.rows["2026-01-01"])
        # A day printed 20 min before Subuh is kept as printed and raised; one after Subuh is left '-' and raised.
        self.assertEqual(self.before(wly.rows["2026-02-10"][0], 20), wly.rows["2026-02-10"][1])
        self.assertEqual("-", wly.rows["2026-02-11"][1])
        self.assertEqual(["WLY01 2026 imsak: 1 imsak cell not a time or after Subuh (the first 2026-02-11), left '-'",
                          "WLY01 2026 imsak: imsak printed more than 15 min before Subuh on 1 day (the first 2026-02-10, up to 20 min), "
                          "kept as printed, for a human to rule"], ctx.errors)
        self.assertFalse(any(TIME_SHAPE.search(e) for e in ctx.errors))
        # The same days once past are notes: the year is read every run, and a past day raised every week would bury
        # what is coming.
        later = Context(jakim.SOURCE, self.root, dt.date(2026, 6, 1), lambda line: None, self.root, http=FakeHttp(answers))
        jakim.fetch(later)
        self.assertEqual([], later.errors)
        self.assertIn("WLY01 2026 imsak: imsak printed more than 15 min before Subuh on 1 day (the first 2026-02-10, up to 20 min), "
                      "kept as printed, for a human to rule (past days)", later.notes)

    def test_jakims_whole_year_ahead_replaces_a_mirrors_year(self):
        year_ahead = self.esolat_year(2027)

        class Http(FakeHttp):
            def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None):
                if url.startswith(jakim.ESOLAT_DURATION):
                    self.urls.append(url)
                    return year_ahead
                return super().get(url, headers=headers, data=data, timeout=timeout, retries=retries, gap=gap)
        answers = [(jakim.ESOLAT + z, self.esolat_year(2026)) for z, _, _ in jakim.ZONES]
        ctx = Context(jakim.SOURCE, self.root, dt.date(2026, 11, 16), lambda line: None, self.root, http=Http(answers))
        tables = {t.key: t for t in jakim.fetch(ctx)}
        self.assertFalse(tables["WLY01-2027"].merge, "e-solat's whole year is the capture, never merged into a mirror's")
        self.assertFalse(tables["WLY01-2027-imsak"].merge)
        self.assertTrue(getattr(tables["WLY01-2026"], "merge", True), "the current year still merges (a page's days kept)")
        # Stored over a mirror's year: the live file is e-solat's alone, and its name says so.
        store = Store(self.root, os.path.join(self.root, "monitor"), dt.date(2026, 11, 16))
        mirror = Table("WLY01-2027", "Kuala Lumpur WLY01 2027 (mirror)", None, None, "Asia/Kuala_Lumpur", "MY", "F+E S D A M I",
                       rows={"2027-01-01": ["01:00"] * 6, "2028-01-01": ["01:00"] * 6})
        store.record("my-jakim", mirror)
        store.record("my-jakim", tables["WLY01-2027"])
        live = read_table_rows(os.path.join(self.root, "archive", "tables", "monitor", "my-jakim", "WLY01-2027.txt"))
        self.assertEqual(365, len(live))
        self.assertNotIn("2028-01-01", live)
        self.assertNotIn("(mirror)", store.data["tables"]["my-jakim/WLY01-2027"]["name"])

    def test_a_partial_year_ahead_merged_over_held_days_keeps_the_mirrors_mark(self):
        whole = json.loads(self.esolat_year(2027))
        part = dict(whole, prayerTime=whole["prayerTime"][:200])
        broken = json.loads(self.esolat_year(2027))
        broken["prayerTime"][9]["dhuhr"] = "x"  # 365 days answered, 364 taken: a held day must not be erased

        def run(body, held):
            root = tempfile.mkdtemp("monitor-imsak-part")
            self.addCleanup(shutil.rmtree, root)
            if held:
                store = Store(root, os.path.join(root, "monitor"), dt.date(2026, 11, 16))
                store.record("my-jakim", Table("WLY01-2027", "Kuala Lumpur WLY01 2027 (mirror)", None, None, "Asia/Kuala_Lumpur", "MY",
                                               "F+E S D A M I", rows={"2027-12-31": ["01:00"] * 6}))

            class Http(FakeHttp):
                def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None):
                    if url.startswith(jakim.ESOLAT_DURATION):
                        return json.dumps(body).encode() if "WLY01" in url else self.year
                    return super().get(url, headers=headers, data=data, timeout=timeout, retries=retries, gap=gap)
            http = Http([(jakim.ESOLAT + z, self.esolat_year(2026)) for z, _, _ in jakim.ZONES])
            http.year = self.esolat_year(2027)
            ctx = Context(jakim.SOURCE, root, dt.date(2026, 11, 16), lambda line: None, root, http=http)
            return {t.key: t for t in jakim.fetch(ctx)}, ctx
        tables, ctx = run(part, held=True)
        t = tables["WLY01-2027"]
        self.assertTrue(t.merge)
        self.assertTrue(t.name.endswith("(mirror)"), "merged over held days, the capture may hold the mirror's")
        self.assertTrue(tables["WLY01-2027-imsak"].name.endswith("(mirror)"))
        self.assertIn("WLY01 2027: e-solat printed 200 days of the year; merged over the days held, it keeps the mirror's mark", ctx.notes)
        self.assertFalse(tables["JHR01-2027"].name.endswith("(mirror)"), "a whole year is e-solat's own")
        tables, _ = run(part, held=False)
        self.assertFalse(tables["WLY01-2027"].name.endswith("(mirror)"), "nothing held: the capture is e-solat's alone")
        tables, _ = run(broken, held=True)
        self.assertTrue(tables["WLY01-2027"].merge, "a day not taken leaves the held one standing")

    def test_the_imsak_recipes_read_the_keys_the_fetchers_write(self):
        # recipes.tsv's `-imsak` lines against the keys jakim.py and kemenag.py write (Recipe.matches: a glob, and only a
        # pattern ending `-imsak` reads such a key), so the first real capture is planned, not left unread.
        import fnmatch
        with open(os.path.join(HERE, "..", "official", "monitor", "recipes.tsv"), encoding="utf-8") as f:
            lines = [l.rstrip("\n").split("\t") for l in f if l.strip() and not l.startswith("#")][1:]
        imsak = [(c[0], c[1]) for c in lines if c[1].endswith("-imsak")]
        for zone, _, _ in jakim.ZONES:
            self.assertTrue(any(s == jakim.SOURCE and fnmatch.fnmatchcase(f"{zone}-2027-imsak", pat) for s, pat in imsak), zone)
        places = {p[0] for p in kemenag.PLACES}
        kem = [pat for s, pat in imsak if s == kemenag.SOURCE]
        self.assertEqual(18, len(kem))
        for pat in kem:
            self.assertIn(pat[:-len("-*-imsak")], places, pat)
            self.assertTrue(fnmatch.fnmatchcase(pat[:-len("-*-imsak")] + "-2027-imsak", pat))
        for s, pat in imsak:
            self.assertFalse(fnmatch.fnmatchcase("WLY01-2027", pat) or fnmatch.fnmatchcase("jakarta-2027", pat), pat)

    def kemenag_reply(self, name, year, month, odd=None):
        body = json.loads(kemenag_reply(name, year, month))
        for x in body["data"]["jadwal"]:
            d = int(x["date"][-2:])
            x["imsak"] = self.before(x["subuh"], (odd or {}).get(d, 10))
        return json.dumps(body).encode()

    def test_kemenag_keeps_each_places_imsak_beside_its_table(self):
        key, cid, name = kemenag.PLACES[0][:3]
        special = {(cid, 2026, 11): self.kemenag_reply(name, 2026, 11, odd={5: 25, 25: 25}), (cid, 2026, 12): self.kemenag_reply(name, 2026, 12)}
        http = KemenagHttp(special)
        ctx = Context(kemenag.SOURCE, self.root, dt.date(2026, 11, 16), [].append, os.path.join(self.root, "monitor"), http=http)
        tables = {t.key: t for t in kemenag.fetch(ctx)}
        imsak = tables[f"{key}-2026-imsak"]
        self.assertEqual(("- Im", 61), (imsak.columns, len(imsak.rows)))
        self.assertEqual([tables[f"{key}-2026"].rows["2026-11-01"][0], self.before(tables[f"{key}-2026"].rows["2026-11-01"][0], 10)], imsak.rows["2026-11-01"])
        self.assertEqual(self.before(imsak.rows["2026-11-25"][0], 25), imsak.rows["2026-11-25"][1], "kept as printed")
        self.assertIn(f"{name} 2026-11 imsak: imsak printed more than 15 min before Subuh on 1 day (the first 2026-11-25, up to 25 min), "
                      "kept as printed, for a human to rule", ctx.errors)
        self.assertIn(f"{name} 2026-11 imsak: imsak printed more than 15 min before Subuh on 1 day (the first 2026-11-05, up to 25 min), "
                      "kept as printed, for a human to rule (past days)", ctx.notes)
        # The other places' answers print no imsak ('-'): no imsak capture for them.
        self.assertEqual(1, sum(1 for k in tables if k.endswith("-imsak")))


class EgyptFetch(unittest.TestCase):
    """egypt's run against canned answers: every city ESA's daily page prints that is a unit is kept, the 30
    Dar al-Ifta republishes and the 48 ESA alone prints (finding F25: the 48 were read and thrown away), and
    nothing is asked but plain GETs of the two authorities' own pages. Invented times."""

    TIMES = "4:41 ص 6:08 ص 11:39 ص 2:41 م 5:02 م 6:21 م"

    def daily(self, names, date="2027-01-09"):
        head = "<p>المدينة التاريخ الميلادي التاريخ الهجري فجر شروق ظهر عصر مغرب عشاء "
        return (head + " ".join(f"{n} {date} 1 شعبان 1448 {self.TIMES}" for n in names) + "</p>").encode("utf-8")

    def run_fetch(self, esa_page, darifta=None):
        month = ("<div>مواقيت الصلاة خلال الشهر اليوم الفجر الشروق الظهر العصر المغرب العشاء "
                 "08 يناير 2027 م 5:19 ص 6:51 ص 12:02 م 2:53 م 5:13 م 6:35 م</div>").encode("utf-8")
        http = PlainHttp([(egypt.DARIFTA, month if darifta is None else darifta), (egypt.ESA, esa_page)])
        root = tempfile.mkdtemp("monitor-eg")
        self.addCleanup(shutil.rmtree, root)
        ctx = Context(egypt.SOURCE, root, dt.date(2027, 1, 9), [].append, os.path.join(root, "monitor"), http=http)
        return http, ctx, {t.key: t for t in egypt.fetch(ctx)}

    def test_the_48_cities_esa_alone_prints_are_kept(self):
        self.assertEqual(48, len(egypt.ESA_CITIES))
        towns, only = [u for u, _ in egypt.TOWNS], [u for u, _ in egypt.ESA_CITIES]
        self.assertEqual(len(towns) + 48, len(set(towns) | set(only)), "a unit is in one list alone")
        self.assertEqual(len(towns) + 48, len(set(egypt.esa_units().values())), "every unit has a name ESA's page is read by")
        names = [n for _, n in egypt.TOWNS] + [n for _, n in egypt.ESA_CITIES]
        http, ctx, tables = self.run_fetch(self.daily(names))
        self.assertEqual([], ctx.errors)
        self.assertEqual([], ctx.notes)
        self.assertEqual(sorted(towns + only), sorted(tables))
        for unit in only:
            t = tables[unit]
            self.assertEqual(f"eg.esa/{unit}", t.entry)
            self.assertEqual(["2027-01-09"], sorted(t.rows), unit)
            self.assertEqual(["04:41", "06:08", "11:39", "14:41", "17:02", "18:21"], list(t.rows["2027-01-09"]))
            self.assertEqual(["esa-2027-01-09.html"], [n for n, _ in t.raw])
            self.assertIn(egypt.ESA, t.source_line)
            self.assertNotIn("Dar al-Ifta", t.source_line)
        # A town of the 30 holds its month table and the page's day beside it.
        self.assertEqual(["2027-01-08", "2027-01-09"], sorted(tables["cairo"].rows))

    def test_nothing_is_asked_but_plain_gets_of_the_two_pages(self):
        names = [n for _, n in egypt.TOWNS] + [n for _, n in egypt.ESA_CITIES]
        http, _, _ = self.run_fetch(self.daily(names))
        self.assertEqual(len(egypt.TOWNS) + 1, http.requests, "a request a Dar al-Ifta town and ESA's page once: none for the 48")
        self.assertEqual([egypt.ESA], [u for u in http.urls if not u.startswith(egypt.DARIFTA)])
        self.assertTrue(all(d is None for d in http.posted), "no request carries a body")
        self.assertTrue(all(not h for h in http.sent), "no request carries a header of its own")
        self.assertTrue(all("?" not in u for u in http.urls if u.startswith("https://www.esa.gov.eg/")), "ESA's page is asked for as it stands")

    def test_esa_spelling_of_the_48_is_folded(self):
        by = egypt.esa_units()
        self.assertEqual("6th-of-october", by[egypt.norm_city("6أكتوبر")])
        self.assertEqual("beni-suef", by[egypt.norm_city("بني سويف")])
        self.assertEqual("ismailia", by[egypt.norm_city("الاسماعيلية")])
        self.assertEqual("mut", by[egypt.norm_city("موط (الداخلة )")])

    def test_a_city_with_no_unit_is_named_and_a_silent_one_is_an_error(self):
        names = [n for _, n in egypt.TOWNS] + [n for u, n in egypt.ESA_CITIES if u != "tanta"] + ["مدينة لا وحدة لها"]
        _, ctx, tables = self.run_fetch(self.daily(names))
        self.assertNotIn("tanta", tables)
        self.assertEqual(47, len([u for u, _ in egypt.ESA_CITIES if u in tables]))
        self.assertEqual(["ESA's daily page printed no row for 1 of its own cities: tanta"], ctx.errors)
        self.assertEqual(["ESA's page lists 1 cities the gate has no unit for (not kept): مدينة لا وحدة لها"], ctx.notes)

    def test_a_page_that_cannot_be_had_keeps_the_towns_and_says_so(self):
        _, ctx, tables = self.run_fetch(FetchError("HTTP 503"))
        self.assertEqual(sorted(u for u, _ in egypt.TOWNS), sorted(tables))
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("ESA's daily page", ctx.errors[0])


class FakeHttp:
    """A client that answers from a table of URL -> body (bytes) or an exception, for fetchers under test."""

    def __init__(self, answers):
        self.answers = answers
        self.urls = []
        self.gaps = []  # the gap each request asked for
        self.sent = []  # the headers each request carried
        self.requests = 0
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None):
        self.urls.append(url)
        self.requests += 1
        self.gaps.append(gap)
        self.sent.append(headers)
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


class PlainHttp(FakeHttp):
    """FakeHttp that also remembers the body each request carried (None on a plain GET)."""

    def __init__(self, answers):
        super().__init__(answers)
        self.posted = []

    def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None):
        self.posted.append(data)
        return super().get(url, headers=headers, data=data, timeout=timeout, retries=retries, gap=gap)


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

    def test_diyanet_keeps_editions_only_once_switched_on(self):
        # Off until the Diyanet Europe curves read pinned copies (diyanet.py's docstring): the live file
        # stays merged, so its 2027 days are not dropped from under them.
        page = ("<table><tr><td>28 Eylül 2026 Pazartesi</td><td>x</td><td>05:11</td><td>06:38</td>"
                "<td>12:59</td><td>16:19</td><td>19:09</td><td>20:30</td></tr></table>").encode()
        self.assertFalse(diyanet.KEEP_EDITIONS)
        tables = diyanet.fetch(self.ctx("tr-diyanet", http=FakeHttp([(diyanet.BASE, page)])))
        self.assertTrue(tables)
        self.assertTrue(all(not t.editions for t in tables))
        saved = diyanet.KEEP_EDITIONS
        diyanet.KEEP_EDITIONS = True
        try:
            tables = diyanet.fetch(self.ctx("tr-diyanet", http=FakeHttp([(diyanet.BASE, page)])))
        finally:
            diyanet.KEEP_EDITIONS = saved
        self.assertTrue(all(t.editions for t in tables))

    def test_london_quotes_the_key_in_the_url(self):
        with open(os.path.join(self.monitor, "keys.env"), "w") as f:
            f.write("LPT_API_KEY=a b/c\n")
        http = FakeHttp([(london.API, FetchError("HTTP 404"))])
        self.assertEqual([], london.fetch(self.ctx("gb-london-lupt", http=http)))
        self.assertEqual(2, len(http.urls), "a year the API lacks stops after its first month and the probe")
        self.assertIn("key=a%20b%2Fc&", http.urls[0])

    def lupt_ctx(self, answers):
        with open(os.path.join(self.monitor, "keys.env"), "w") as f:
            f.write("LPT_API_KEY=testkey123\n")
        http = FakeHttp([(f"{london.API}?format=json&key=testkey123&{query}", body) for query, body in answers])
        return self.ctx("gb-london-lupt", http=http), http

    def test_a_year_not_yet_on_the_api_is_a_note_when_the_current_month_reads(self):
        # As on 10 Oct 2026: the API answers 2027 without its days; October 2026 reads.
        month = {"city": "london", "times": {"2026-10-01": {"fajr": "05:31", "sunrise": "07:06", "dhuhr": "12:52",
                                                           "asr": "15:56", "magrib": "18:32", "isha": "19:55", "asr_2": "16:24"}}}
        ctx, http = self.lupt_ctx([("year=2027", json.dumps({"city": "london", "times": {}}).encode()),
                                   ("year=2026&month=10", json.dumps(month).encode())])
        self.assertEqual([], london.fetch(ctx))
        self.assertEqual([], ctx.errors)
        self.assertEqual(1, len(ctx.notes))
        self.assertIn("2027 is not on the API yet", ctx.notes[0])
        self.assertIn("the current month reads fine", ctx.notes[0])
        self.assertEqual(2, len(http.urls))

    def test_an_answer_that_never_reads_is_an_error_with_its_shape_and_never_a_time(self):
        refused = json.dumps({"error": "Invalid API key", "times": {}}).encode()
        ctx, http = self.lupt_ctx([("year=2027", refused), ("year=2026&month=10", refused)])
        self.assertEqual([], london.fetch(ctx))
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("'Invalid API key'", ctx.errors[0])
        self.assertIn("the current month does not read either", ctx.errors[0])
        self.assertIsNone(TIME_SHAPE.search(ctx.errors[0]), ctx.errors[0])

    def test_a_shape_names_keys_and_types_never_values(self):
        day = {"fajr": "05:31", "sunrise": "07:06", "date": "2026-10-01"}
        text = london.shape({"city": "london", "times": {"2026-10-01": day}, "note": "x" * 200, "list": [1, 2]})
        self.assertIsNone(TIME_SHAPE.search(text), text)
        self.assertIn("city: str", text)
        self.assertIn("list: [2 items]", text)


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
        def reason(today, last_fetch, lasts, retry=False, only=(), force=False):
            return skip_reason(self.ROW, last_fetch, today, set(only), force, retry=retry, turn=fetch.turn_last(lasts, today))
        held = [dt.date(2026, 10, 12)]
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
        self.assertEqual(None, reason(dt.date(2026, 10, 2), None, []))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, retry=True))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, only=("ma-habous",)))
        self.assertEqual(None, reason(dt.date(2026, 10, 2), dt.date(2026, 10, 1), held, force=True))
        self.assertTrue(due(self.ROW, dt.date(2026, 10, 11), dt.date(2026, 10, 12), set(), False, turn=dt.date(2026, 10, 12)))
        # Another cadence never reads the turn.
        weekly = dict(self.ROW, cadence="weekly")
        self.assertEqual("not due", skip_reason(weekly, dt.date(2026, 10, 11), dt.date(2026, 10, 12), set(), False, turn=dt.date(2026, 10, 12)))

    def test_any_table_inside_its_turn_holds_the_turn_open(self):
        store = Store(self.root, self.monitor, dt.date(2026, 9, 28))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 10, 12)))
        self.assertIsNone(store.newest_last("ma-habous"))
        self.held(store, "rabat", dt.date(2026, 9, 13))
        self.held(store, "oujda", dt.date(2026, 9, 13))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 10, 11)))
        self.assertEqual(dt.date(2026, 10, 12), store.turn_last("ma-habous", dt.date(2026, 10, 12)))
        # The next month for Rabat alone: Oujda's missing month keeps the turn open to its third day.
        store.today = dt.date(2026, 10, 13)
        self.held(store, "rabat", dt.date(2026, 10, 13))
        self.assertEqual("2026-11-11", store.data["tables"]["ma-habous/rabat"]["last"])
        self.assertEqual(dt.date(2026, 10, 12), store.turn_last("ma-habous", dt.date(2026, 10, 13)))
        self.assertEqual(dt.date(2026, 10, 12), store.turn_last("ma-habous", dt.date(2026, 10, 15)))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 10, 16)))
        self.assertEqual(dt.date(2026, 11, 11), store.newest_last("ma-habous"))
        # Oujda a month behind never hides Rabat's next turn.
        self.assertEqual(dt.date(2026, 11, 11), store.turn_last("ma-habous", dt.date(2026, 11, 11)))
        self.held(store, "oujda", dt.date(2026, 10, 13))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 10, 13)))
        # A table 60 days old holds nothing open, alone or beside the current ones.
        self.held(store, "only", dt.date(2026, 8, 14), source="aged")
        self.assertEqual(dt.date(2026, 9, 12), store.turn_last("aged", dt.date(2026, 9, 12)))
        self.assertIsNone(store.turn_last("aged", dt.date(2026, 9, 12) + dt.timedelta(days=60)))
        self.held(store, "gone", dt.date(2026, 8, 14))
        self.assertEqual(dt.date(2026, 11, 11), store.turn_last("ma-habous", dt.date(2026, 11, 11)))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 11, 15)))
        # Another source's tables are not this source's.
        self.held(store, "x", dt.date(2026, 10, 3), source="other")
        self.assertEqual(dt.date(2026, 11, 1), store.turn_last("other", dt.date(2026, 11, 2)))
        self.assertIsNone(store.turn_last("ma-habous", dt.date(2026, 11, 2)))
        self.assertEqual(dt.date(2026, 11, 11), store.newest_last("ma-habous"))

    def test_what_a_failed_turn_says_happens_next(self):
        store = Store(self.root, self.monitor, dt.date(2026, 10, 13))
        self.held(store, "rabat", dt.date(2026, 9, 13))
        self.assertEqual("retried on the next run, once", fetch.next_time(store, self.ROW))
        store.source_done("ma-habous", "failed", "a reason", 1)
        self.assertIn("stays due on every run until 2026-10-15 (3 days after 2026-10-12, the last day a table holds), then weekly",
                      fetch.next_time(store, self.ROW))
        # Past the turn with the page unmoved: weekly, no turn ahead to name.
        store.today = dt.date(2026, 10, 20)
        self.assertTrue(fetch.next_time(store, self.ROW).endswith("waits for its cadence (weekly)"), fetch.next_time(store, self.ROW))
        # With the next month held, its own last day is the next turn.
        self.held(store, "rabat", dt.date(2026, 10, 13))
        self.assertIn("waits for its cadence (weekly, and on every run from 2026-11-11, the last day held", fetch.next_time(store, self.ROW))
        # One city a month behind and the other inside its turn: the turn named is the latest.
        self.held(store, "fes", dt.date(2026, 9, 13))
        store.today = dt.date(2026, 11, 12)
        self.assertIn("until 2026-11-14 (3 days after 2026-11-11, the last day a table holds)", fetch.next_time(store, self.ROW))

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

    def test_a_city_a_month_behind_never_hides_the_next_turn(self):
        sources = self.write_sources()
        store = Store(self.root, self.monitor, dt.date(2026, 10, 13))
        self.held(store, "rabat", dt.date(2026, 10, 13))
        self.held(store, "fes", dt.date(2026, 9, 13))
        store.save()
        self.assertEqual(("2026-11-11", "2026-10-12"), (store.data["tables"]["ma-habous/rabat"]["last"], store.data["tables"]["ma-habous/fes"]["last"]))
        for today in ("2026-11-11", "2026-11-12", "2026-11-14"):
            self.assertEqual((0, ["ma-habous"]), self.month_turn(today, sources), today)
        for today in ("2026-11-15", "2026-11-02"):
            self.assertEqual((0, []), self.month_turn(today, sources), today)
        # Fes's own month turn still keeps the source due at the October turn.
        self.assertEqual((0, ["ma-habous"]), self.month_turn("2026-10-15", sources))

    def test_a_table_sixty_days_old_is_never_due(self):
        sources = self.write_sources()
        store = Store(self.root, self.monitor, dt.date(2026, 8, 14))
        self.held(store, "rabat", dt.date(2026, 8, 14))
        store.save()
        self.assertEqual("2026-09-12", store.data["tables"]["ma-habous/rabat"]["last"])
        self.assertEqual((0, ["ma-habous"]), self.month_turn("2026-09-12", sources))
        self.assertEqual((0, []), self.month_turn("2026-11-11", sources))
        self.assertEqual("not due", skip_reason(self.ROW, dt.date(2026, 11, 10), dt.date(2026, 11, 11), set(), False,
                                                turn=store.turn_last("ma-habous", dt.date(2026, 11, 11))))

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

    def test_a_page_that_has_not_turned_is_a_fetch_finding(self):
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
            out = run("2026-09-28")
            self.assertEqual(("ok", ""), (out["status"], out["message"]))
            # Inside the turn (the last day held, 12 October, and the three days after it), an unmoved page is no finding.
            out = run("2026-10-13")
            self.assertEqual(("ok", "", {"rabat": "unchanged"}), (out["status"], out["message"], out["tables"]))
            # 19 October, the Monday after: still the month that ended on the 12th.
            out = run("2026-10-19")
            self.assertEqual(("partial", {"rabat": "unchanged"}), (out["status"], out["tables"]))
            self.assertIn("the page still shows the Hijri month that ended 2026-10-12 (7 days ago): the next month has not appeared", out["message"])
            self.assertTrue(out["message"].endswith("; retried on the next run, once"), out["message"])
            # The retry the next run (outside the turn, due by the retry alone), then the cadence.
            out = run("2026-10-20")
            self.assertEqual("partial", out["status"])
            self.assertTrue(out["message"].endswith("this was the retry; the source now waits for its cadence (weekly)"), out["message"])
            self.assertEqual("not due", run("2026-10-21")["message"])
            # The next month held: no finding again.
            pages["first"] = dt.date(2026, 10, 21)
            out = run("2026-10-26")
            self.assertEqual(("ok", "", {"rabat": "changed"}), (out["status"], out["message"], out["tables"]))
        finally:
            del sys.modules["fetchers.fakehijri"]


class HabousPlaces(unittest.TestCase):
    """The Morocco fetcher's places, read from Habous's month files (since November 2026, the old city pages
    answer 404): every unit of ma.habous and no other place, each under the name its ville id has carried,
    read at its own unit (rulings R195-R199), with its rows in ma-habous.tsv at that unit."""

    @staticmethod
    def month_file(year, month, names=None, fuseau="GMT", cols=None, short=()):
        """An invented month file: every place the fetcher reads, each time the day's number as minutes of
        hours no Moroccan table prints (ruling R69)."""
        days = calendar.monthrange(year, month)[1]
        names = dict(names or {})
        villes = []
        for key, name, ville, label in morocco.PLACES:
            villes.append((ville, label))
        for key, name, ville, label, unit in morocco.SECOND_TABLES:
            villes.append((ville, label))
        villes.append((999, "مكان آخر"))
        data = {"annee": year, "mois": month, "fuseau": fuseau, "cols": cols or list(morocco.FILE_COLUMNS), "villes": [
            {"c": c, "n": names.get(c, n), "h": [[f"0{h}:{d:02d}" for h in (1, 2, 3, 4, 5, 6)] for d in range(1, days + 1 - (c in short))]}
            for c, n in villes]}
        return ("(window.HORAIRES_MOIS=window.HORAIRES_MOIS||[]).push(" + json.dumps(data, ensure_ascii=False) + ");").encode("utf-8")

    def run_fetch(self, answers, today=dt.date(2026, 11, 12)):
        class ExactHttp(FakeHttp):
            def get(self, url, **kw):
                self.urls.append(url)
                if url not in answers:
                    raise FetchError("no canned answer")
                answer = answers[url]
                if isinstance(answer, Exception):
                    raise answer
                return answer
        root = tempfile.mkdtemp("monitor-habous")
        try:
            http = ExactHttp([])
            ctx = Context("ma-habous", root, today, lambda line: None, root, http=http)
            tables = {t.key: t for t in morocco.fetch(ctx)}
        finally:
            shutil.rmtree(root)
        return ctx, tables, http.urls

    def test_the_month_files_are_read_at_each_unit_and_an_id_naming_another_place_is_left_out(self):
        nov, dec = morocco.MONTH_FILE.format(y=2026, m=11), morocco.MONTH_FILE.format(y=2026, m=12)
        ctx, tables, urls = self.run_fetch({nov: self.month_file(2026, 11, names={104: "فاس"}),  # Marrakesh's id naming Fes
                                            dec: self.month_file(2026, 12)})
        self.assertEqual([nov, dec], urls, "two requests a run: the current month and the next")
        self.assertEqual(len(morocco.PLACES) + len(morocco.SECOND_TABLES), len(tables), "every unit, and Agoudal's second table")
        self.assertEqual({f"ma.habous/{key}" for key, _, _, _ in morocco.PLACES}, {t.entry for t in tables.values()}, "each at its own unit")
        self.assertEqual({(None, None, "UTC", "F+E S D A M I")}, {(t.lat, t.lon, t.clock, t.columns) for t in tables.values()},
                         "no table at a point of its own: the unit's point answers")
        self.assertEqual(("2026-12-01", "2026-12-31"), (tables["marrakesh"].first(), tables["marrakesh"].last()),
                         "the month whose file names another place at Marrakesh's id is left out, the other kept")
        self.assertTrue(any("Marrakesh: ville 104 names another place in 2026-11" in e for e in ctx.errors), ctx.errors)
        unit, fes, boujdour = tables["rabat"], tables["fes"], tables["boujdour"]
        self.assertEqual(("ma.habous/rabat", None, None, "Africa/Casablanca", "UTC"), (unit.entry, unit.lat, unit.lon, unit.zone, unit.clock))
        self.assertEqual(("ma.habous/fes", None, None, "Africa/Casablanca", "MA", "UTC", "F+E S D A M I"),
                         (fes.entry, fes.lat, fes.lon, fes.zone, fes.cc, fes.clock, fes.columns),
                         "a city once read at the app's point keeps its key and is read at its unit")
        second = tables["agoudal-midelt"]
        self.assertEqual(("ma.habous/agoudal", None, None), (second.entry, second.lat, second.lon), "Agoudal's second table against Agoudal's unit")
        self.assertEqual(61, len(second.rows))
        self.assertEqual(("Africa/El_Aaiun", "EH"), (boujdour.zone, boujdour.cc))
        self.assertEqual(61, len(fes.rows))
        self.assertEqual(("2026-11-01", "2026-12-31"), (fes.first(), fes.last()))
        self.assertEqual(["01:30", "02:30", "03:30", "04:30", "05:30", "06:30"], fes.rows["2026-11-30"])
        self.assertEqual(["horaires-2026-11.js", "horaires-2026-12.js"], [name for name, _ in fes.raw])
        self.assertIn("fuseau GMT", fes.source_line)

    def test_a_next_month_not_posted_yet_is_a_note_and_a_missing_current_month_an_error(self):
        nov, dec = morocco.MONTH_FILE.format(y=2026, m=11), morocco.MONTH_FILE.format(y=2026, m=12)
        ctx, tables, _ = self.run_fetch({nov: self.month_file(2026, 11), dec: FetchError(f"HTTP 404 from {dec}")})
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("2026-12 not posted yet" in n for n in ctx.notes), ctx.notes)
        self.assertEqual(len(morocco.PLACES) + len(morocco.SECOND_TABLES), len(tables))
        self.assertEqual("2026-11-30", tables["casablanca"].last())
        ctx, tables, _ = self.run_fetch({nov: FetchError(f"HTTP 404 from {nov}"), dec: FetchError(f"HTTP 404 from {dec}")})
        self.assertEqual({}, tables)
        self.assertTrue(any(e.startswith("2026-11: HTTP 404") for e in ctx.errors), ctx.errors)
        # December's turn: January 2027 is the next month.
        jan = morocco.MONTH_FILE.format(y=2027, m=1)
        ctx, tables, urls = self.run_fetch({morocco.MONTH_FILE.format(y=2026, m=12): self.month_file(2026, 12), jan: FetchError("HTTP 404")},
                                           today=dt.date(2026, 12, 30))
        self.assertEqual(jan, urls[-1])
        self.assertEqual([], ctx.errors)

    def test_a_file_in_another_zone_or_with_other_columns_or_a_short_month_is_refused(self):
        nov, dec = morocco.MONTH_FILE.format(y=2026, m=11), morocco.MONTH_FILE.format(y=2026, m=12)
        no_dec = FetchError("HTTP 404")
        ctx, tables, _ = self.run_fetch({nov: self.month_file(2026, 11, fuseau="GMT+1"), dec: no_dec})
        self.assertEqual({}, tables)
        self.assertTrue(any("zone is 'GMT+1', not GMT" in e for e in ctx.errors), ctx.errors)
        ctx, tables, _ = self.run_fetch({nov: self.month_file(2026, 11, cols=["Imsak", "Fajr", "Chorouq", "Dhuhr", "Asr", "Maghrib"]), dec: no_dec})
        self.assertEqual({}, tables)
        self.assertTrue(any("columns are not the six expected" in e for e in ctx.errors), ctx.errors)
        ctx, tables, _ = self.run_fetch({nov: self.month_file(2026, 11, short=(58,)), dec: no_dec})
        self.assertNotIn("casablanca", tables)
        self.assertEqual(len(morocco.PLACES) + len(morocco.SECOND_TABLES) - 1, len(tables))
        self.assertTrue(any("Casablanca: 2026-11's file does not hold the whole month" in e for e in ctx.errors), ctx.errors)
        ctx, tables, _ = self.run_fetch({nov: b"<html>Just a moment...</html>", dec: no_dec})
        self.assertEqual({}, tables)
        self.assertTrue(any("not a month file" in e for e in ctx.errors), ctx.errors)
        ctx, tables, _ = self.run_fetch({nov: self.month_file(2026, 10), dec: no_dec})
        self.assertTrue(any("the file holds 2026-10" in e for e in ctx.errors), ctx.errors)

    def test_the_places_are_the_registrys_units_and_each_has_its_rows_in_the_gate(self):
        """A unit the fetcher does not read is a timetable nobody checks as it is printed (ruling R185's
        condition), so the list is held to the registry's (HabousCities.kt) both ways."""
        kotlin = os.path.join(HERE, "..", "..", "..", "shared", "src", "commonMain", "kotlin", "world", "taqwa", "app", "prayer", "engine",
                              "registry", "data", "HabousCities.kt")
        with open(kotlin, encoding="utf-8") as f:
            text = f.read()
        line = r'^\s+city\((\d+), "([a-z0-9-]+)", "([^"]+)",.*\), // (.+)$'
        # Habous's units are HabousCities.cities alone: its `spain` list (Sebta and Melilla, ruling R261) is read the same
        # way but is no unit of Morocco's, rides beside Spain's safe default, and is read by hand (sources.tsv's
        # ma-habous-spain, ruling R288), so the fetcher must not read it.
        cities, spain = text.split("val spain", 1) if "val spain" in text else (text, "")
        units = re.findall(line, cities, re.M)
        self.assertTrue(len(units) > 150, len(units))
        spain_villes = {int(v) for v, _, _, _ in re.findall(line, spain, re.M)}
        self.assertEqual({24, 40}, spain_villes)
        self.assertFalse(spain_villes & {p[2] for p in morocco.PLACES + [t[:4] for t in morocco.SECOND_TABLES]})
        # The unit, its name, and Habous's own name for the place (the comment on its line), which a run holds the
        # file's to: the registry's record of whose table a unit reads is the one the monitor checks.
        self.assertEqual(sorted((int(ville), unit, name, label) for ville, unit, name, label in units),
                         sorted((ville, key, name, label) for key, name, ville, label in morocco.PLACES))
        keys = [p[0] for p in morocco.PLACES] + [p[0] for p in morocco.SECOND_TABLES]
        villes = [p[2] for p in morocco.PLACES] + [p[2] for p in morocco.SECOND_TABLES]
        self.assertEqual(len(keys), len(set(keys)), "one table per key")
        self.assertEqual(len(villes), len(set(villes)), "one table per ville id")
        self.assertTrue(all(label.strip() == label and label for _, _, _, label in morocco.PLACES))
        for key, name, ville, label, unit in morocco.SECOND_TABLES:
            self.assertIn(unit, [p[0] for p in morocco.PLACES], key)
        self.assertTrue(set(morocco.ZONES) <= {p[0] for p in morocco.PLACES})
        gate = os.path.join(HERE, "..", "official", "gate", "ma-habous.tsv")
        with open(gate, encoding="utf-8") as f:
            lines = [line.rstrip("\n").split("\t") for line in f if line.strip() and not line.startswith("#")]
        rows = [dict(zip(lines[0], r)) for r in lines[1:]]
        at = {(r["entry"], r["zone"], r["clock"], r["columns"]) for r in rows if not r["lat"]}
        for key, name, ville, label in morocco.PLACES:
            self.assertIn((f"ma.habous/{key}", morocco.ZONES.get(key, "Africa/Casablanca"), "UTC", "F+E S D A M I"), at, key)
        held_out = {r["entry"] for r in rows if not r["lat"] and r["split"] == "test"}
        self.assertEqual(set(), {f"ma.habous/{key}" for key, _, _, _ in morocco.PLACES} - held_out, "every unit has a held-out row of its own")


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
        if hasattr(common.ssl, "VERIFY_X509_PARTIAL_CHAIN"):
            # Python 3.13+ sets it by default; off, the file's intermediate is never a trust anchor by itself.
            self.assertFalse(ctx.verify_flags & common.ssl.VERIFY_X509_PARTIAL_CHAIN)
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
            if r["fetcher"] == "manual":
                # The audit's F27: a source read by hand is raised by its date alone, so it always carries one.
                self.assertNotEqual("-", r["next_expected"], f"{r['source']}: a manual source with no date is never raised")
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

    def test_the_hand_read_editions_this_line_rests_on_are_asked_before_ramadan_1448(self):
        # The second audit's G22: tj.default, ci.default and pk.karachi/in.karachi were built on hand-read editions
        # (the Council of Ulama, COSIM, Dawat-e-Islami's cards) and are class D, so no Ramadan horizon asks for them;
        # each is a manual source whose next edition is due before Ramadan 1448 begins (about 8 Feb 2027).
        rows = {r["source"]: r for r in fetch.read_sources(fetch.SOURCES_TSV)}
        for source, entries in (("tj-council", ["tj.default"]), ("ci-cosim", ["ci.default"]), ("pk-dawateislami", ["pk.karachi", "in.karachi"])):
            r = rows[source]
            self.assertEqual(("manual", "manual"), (r["fetcher"], r["cadence"]), source)
            for entry in entries:
                self.assertIn(entry, r["entries"], source)
            due = r["next_expected"] if len(r["next_expected"]) == 10 else r["next_expected"] + "-01"
            self.assertLessEqual(due, "2027-02-07", f"{source}: asked before Ramadan 1448")
        self.assertEqual("default.safe.americas", rows["mcw-far-south"]["entries"])
        self.assertLessEqual(rows["mcw-far-south"]["next_expected"], "2027-12", "2028's calculator read before 2027 ends")


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


def kemenag_times(date, shift=0):
    """Invented: a function of the day of the month alone."""
    m = date.day + shift
    return [f"04:{m:02d}", f"05:{m:02d}", f"11:{m:02d}", f"15:{m:02d}", f"17:{m:02d}", f"19:{m:02d}"]


def kemenag_reply(name, year, month, days=None, shifted=None, cell=None):
    """myQuran's answer for a month: every day unless `days` lists them; `shifted` {day: minutes} moves a day's times;
    `cell` puts that text in every cell."""
    jadwal = []
    for d in (range(1, calendar.monthrange(year, month)[1] + 1) if days is None else days):
        date = dt.date(year, month, d)
        times = kemenag_times(date, (shifted or {}).get(d, 0))
        jadwal.append(dict(zip(kemenag.KEYS, [cell] * 6 if cell else times), date=date.isoformat(), imsak="-"))
    return json.dumps({"status": True, "data": {"id": 1, "lokasi": name.upper(), "daerah": "X", "jadwal": jadwal}}).encode()


class KemenagHttp:
    """Answers every month asked with the invented table of the place the id names; `special` gives another answer
    (bytes or an exception) for an (id, year, month)."""

    def __init__(self, special=None):
        self.special = special or {}
        self.names = {p[1]: p[2] for p in kemenag.PLACES}
        self.asked = []  # (id, year, month)
        self.plain = True  # every request a bare GET: no header, no body
        self.gaps = []  # the gap each request asked for
        self.owner = None

    def get(self, url, headers=None, data=None, timeout=None, retries=1, gap=None, jar=None):
        self.plain = self.plain and headers is None and data is None and jar is None and url.startswith(kemenag.BASE + "/")
        self.gaps.append(gap)
        cid, year, month = url[len(kemenag.BASE) + 1:].split("/")
        at = (cid, int(year), int(month))
        self.asked.append(at)
        answer = self.special.get(at)
        if isinstance(answer, Exception):
            raise answer
        return answer if answer is not None else kemenag_reply(self.names[cid], at[1], at[2])


class KemenagFetch(unittest.TestCase):
    """The Kemenag fetcher's run against canned answers (audit findings F23, F24, F28): what the round holds is held,
    the next year comes a few places a run, an answer that is not the month asked is an error, a place nothing was
    read for is raised, and held months are read again in rotation, a changed held day noted."""

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-kemenag")
        self.addCleanup(shutil.rmtree, self.root)
        self.ids = {p[0]: p[1] for p in kemenag.PLACES}

    def run_on(self, today, special=None):
        http = KemenagHttp(special)
        ctx = Context(kemenag.SOURCE, self.root, today, [].append, os.path.join(self.root, "monitor"), http=http)
        tables = {t.key: t for t in kemenag.fetch(ctx)}
        self.assertTrue(http.plain, "every request is a bare GET of the schedule's own address")
        for text in ctx.errors + ctx.notes:
            self.assertIsNone(TIME_SHAPE.search(text), text)
        return http, ctx, tables

    def hold(self, folder, key, year, months=range(1, 13), shifted=None):
        """A held table of `key`'s year: the invented one, `shifted` {(month, day): minutes} aside."""
        lines = ["# invented"]
        for month in months:
            for d in range(1, calendar.monthrange(year, month)[1] + 1):
                date = dt.date(year, month, d)
                lines.append(" ".join([date.isoformat()] + kemenag_times(date, (shifted or {}).get((month, d), 0))))
        write_atomic(os.path.join(self.root, *folder, f"{key}-{year}.txt"), "\n".join(lines) + "\n")

    def a_week_that_reads_again(self, year, month, start):
        """The first day on or after `start` on which the rotation reads (year, month) again."""
        day = start
        while kemenag.reread_months(day, [(year, month)]) != [(year, month)]:
            day += dt.timedelta(days=1)
        return day

    def beyond_the_rotation(self, http, today, year):
        """What was asked of `year` besides the month the rotation reads again on `today`."""
        again = set(kemenag.reread_months(today, [(year, m) for m in range(1, 13)]))
        return [a for a in http.asked if a[1] == year and (a[1], a[2]) not in again]

    MONITOR = ("archive", "tables", "monitor", kemenag.SOURCE)

    def test_a_run_reads_the_month_and_the_next_for_every_place(self):
        http, ctx, tables = self.run_on(dt.date(2026, 6, 10))
        self.assertEqual(2 * len(kemenag.PLACES), len(http.asked))
        self.assertEqual({(2026, 6), (2026, 7)}, {(y, m) for _, y, m in http.asked})
        self.assertEqual(([], []), (ctx.errors, ctx.notes))
        self.assertEqual(len(kemenag.PLACES), len(tables))
        self.assertEqual({61}, {len(t.rows) for t in tables.values()})

    def test_every_request_is_a_bare_get_no_faster_than_one_in_two_seconds(self):
        # Hundreds of requests in a row at a republisher's free API: each asks the client for the fetcher's own gap,
        # which the client never shortens and which is never below two seconds.
        self.assertGreaterEqual(kemenag.KEMENAG_GAP, 2.0)
        for today in (dt.date(2026, 6, 10), dt.date(2026, 11, 13)):
            http, _, _ = self.run_on(today)
            self.assertTrue(http.plain)
            self.assertTrue(http.asked)
            self.assertEqual({kemenag.KEMENAG_GAP}, set(http.gaps))
            self.assertEqual(len(http.asked), len(http.gaps))

    def test_a_month_the_round_holds_is_held(self):
        ctx = Context(kemenag.SOURCE, self.root, dt.date(2026, 11, 13), [].append, os.path.join(self.root, "monitor"), http=KemenagHttp())
        self.assertFalse(kemenag.month_held(ctx, "jepara", 2027, 2))
        self.hold(kemenag.ROUND, "jepara", 2027, months=[1, 2])
        self.assertTrue(kemenag.month_held(ctx, "jepara", 2027, 2))
        self.assertFalse(kemenag.month_held(ctx, "jepara", 2027, 3))
        self.hold(self.MONITOR, "jepara", 2027, months=[3])
        self.assertTrue(kemenag.month_held(ctx, "jepara", 2027, 3), "the monitor's own months count beside the round's")
        self.assertTrue(kemenag.month_held(ctx, "jepara", 2027, 1))
        self.assertFalse(kemenag.month_held(ctx, "jepara", 2028, 1))

    def test_the_next_year_is_not_fetched_again_where_the_round_holds_it(self):
        keys = [p[0] for p in kemenag.PLACES]
        for key in keys[:-3]:
            self.hold(kemenag.ROUND, key, 2027)
        today = dt.date(2026, 11, 13)
        http, ctx, tables = self.run_on(today)
        again = set(kemenag.reread_months(today, [(2027, m) for m in range(1, 13)]))
        filled = {}
        for cid, year, month in http.asked:
            if year == 2027 and (year, month) not in again:
                filled.setdefault(cid, set()).add(month)
        self.assertEqual({self.ids[k] for k in keys[-3:]}, set(filled), "only the places that lack the year are asked for it")
        self.assertEqual(2 * len(keys) + 3 * 12 + len(again) * (len(keys) - 3), len(http.asked))
        self.assertEqual([], ctx.errors)
        self.assertEqual({365}, {len(tables[f"{k}-2027"].rows) for k in keys[-3:]})

    def test_a_year_new_to_every_place_comes_a_few_places_a_run(self):
        keys = [p[0] for p in kemenag.PLACES]
        self.assertLessEqual(kemenag.BACKFILL_PLACES * 12, 300, "a run's share of a new year stays a few minutes")
        http, ctx, tables = self.run_on(dt.date(2026, 11, 13))
        asked = [a for a in http.asked if a[1] == 2027]
        self.assertEqual(kemenag.BACKFILL_PLACES * 12, len(asked))
        self.assertEqual(len(asked), len(set(asked)), "the month that probes the year is not asked twice")
        self.assertEqual({self.ids[k] for k in keys[:kemenag.BACKFILL_PLACES]}, {a[0] for a in asked})
        self.assertEqual([], ctx.errors)
        left = len(keys) - kemenag.BACKFILL_PLACES
        self.assertTrue(any(f"2027: {left} more places" in n for n in ctx.notes), ctx.notes)
        # What a run fetched is held on the next, which goes on with the places after them.
        for key in keys[:kemenag.BACKFILL_PLACES]:
            self.hold(self.MONITOR, key, 2027)
        http, ctx, tables = self.run_on(dt.date(2026, 11, 13))
        filled = {a[0] for a in self.beyond_the_rotation(http, dt.date(2026, 11, 13), 2027)}
        self.assertEqual({self.ids[k] for k in keys[kemenag.BACKFILL_PLACES:2 * kemenag.BACKFILL_PLACES]}, filled)
        # Before October the next year is not asked for, and a year not yet published is a note, asked once.
        http, ctx, tables = self.run_on(dt.date(2026, 9, 13))
        self.assertEqual([], self.beyond_the_rotation(http, dt.date(2026, 9, 13), 2027))
        first = kemenag.PLACES[kemenag.BACKFILL_PLACES]
        http, ctx, tables = self.run_on(dt.date(2026, 10, 13), {(first[1], 2027, 1): FetchError("HTTP 404")})
        self.assertEqual([(first[1], 2027, 1)], self.beyond_the_rotation(http, dt.date(2026, 10, 13), 2027))
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("2027 not published yet" in n for n in ctx.notes), ctx.notes)

    def test_an_answer_that_is_not_the_month_asked_is_an_error(self):
        short, empty, other, unread, shapeless, fine = kemenag.PLACES[:6]
        special = {
            (short[1], 2026, 6): kemenag_reply(short[2], 2026, 6, days=range(1, 21)),
            (empty[1], 2026, 6): kemenag_reply(empty[2], 2026, 6, days=[]),
            (empty[1], 2026, 7): kemenag_reply(empty[2], 2026, 7, days=[]),
            (other[1], 2026, 6): kemenag_reply(other[2], 2026, 5),
            (unread[1], 2026, 6): kemenag_reply(unread[2], 2026, 6, cell="soon"),
            (unread[1], 2026, 7): FetchError("HTTP 500"),
            (shapeless[1], 2026, 7): json.dumps({"status": True, "data": ["x"]}).encode(),
        }
        http, ctx, tables = self.run_on(dt.date(2026, 6, 10), special)
        self.assertEqual(sorted([
            f"{short[2]} 2026-06: the answer holds 20 of the month's 30 days",
            f"{empty[2]}: no rows this run (2026-06: the answer holds 0 of the month's 30 days; 2026-07: the answer holds 0 of the month's 31 days)",
            f"{other[2]} 2026-06: the answer holds 0 of the month's 30 days and 31 that are not the month's",
            f"{unread[2]}: no rows this run (2026-06: no day of the answer could be read; 2026-07: HTTP 500)",
            f"{shapeless[2]} 2026-07: the answer is not in the shape of a month's schedule",
        ]), sorted(ctx.errors))
        # Never part of a month for the whole of it: the short month adds no day, the other months stand.
        self.assertEqual(31, len(tables[f"{short[0]}-2026"].rows))
        self.assertEqual(31, len(tables[f"{other[0]}-2026"].rows))
        self.assertEqual(30, len(tables[f"{shapeless[0]}-2026"].rows))
        self.assertNotIn(f"{empty[0]}-2026", tables)
        self.assertNotIn(f"{unread[0]}-2026", tables)
        self.assertEqual(61, len(tables[f"{fine[0]}-2026"].rows))
        self.assertEqual(len(kemenag.PLACES) - 2, len(tables))

    def test_check_month_takes_the_whole_month_and_nothing_else(self):
        whole = {f"2028-02-{d:02d}": [] for d in range(1, 30)}
        kemenag.check_month(whole, 2028, 2)
        for rows in ({}, dict(list(whole.items())[:-1]), {**whole, "2028-03-01": []}, {d.replace("2028", "2027"): [] for d in whole}):
            with self.assertRaises(FetchError):
                kemenag.check_month(rows, 2028, 2)

    def test_every_month_held_is_read_again_once_in_the_cycle(self):
        months = [(y, m) for y in (2026, 2027, 2028) for m in range(1, 13)]
        start = dt.date(2026, 11, 13)
        read = [kemenag.reread_months(start + dt.timedelta(weeks=w), months) for w in range(kemenag.REREAD_WEEKS)]
        self.assertEqual(sorted(months), sorted(m for week in read for m in week), "each month once in the cycle")
        self.assertTrue(all(len(week) <= 1 for week in read), "never two months in one run")
        self.assertEqual(read[0], kemenag.reread_months(start + dt.timedelta(weeks=kemenag.REREAD_WEEKS), months))
        self.assertEqual([], kemenag.reread_months(start, []))

    def test_a_month_held_beyond_the_next_is_read_again_and_a_changed_day_noted(self):
        keys = [p[0] for p in kemenag.PLACES]
        moved, gone = kemenag.PLACES[4], kemenag.PLACES[7]
        for key in keys:
            self.hold(kemenag.ROUND, key, 2027)
        today = self.a_week_that_reads_again(2027, 3, dt.date(2026, 1, 1))
        self.assertTrue(today < dt.date(2026, 10, 1), "a week of the cycle before the next year's own turn")
        # As held: every place asked once more, nothing kept.
        http, ctx, tables = self.run_on(today)
        self.assertEqual(3 * len(keys), len(http.asked))
        self.assertEqual({(2027, 3)}, {(y, m) for _, y, m in http.asked if y == 2027})
        self.assertEqual(([], []), (ctx.errors, ctx.notes))
        self.assertEqual({2026}, {int(k[-4:]) for k in tables})
        # Two days of one place read otherwise today, and another place's answer fails.
        special = {(moved[1], 2027, 3): kemenag_reply(moved[2], 2027, 3, shifted={5: 1, 9: -1}), (gone[1], 2027, 3): FetchError("HTTP 500")}
        http, ctx, tables = self.run_on(today, special)
        self.assertEqual([f"{moved[2]} 2027-03: 2 held days changed (the first 2027-03-05); the table now holds what was read today"], ctx.notes)
        self.assertEqual([f"{gone[2]} 2027-03, read again: HTTP 500"], ctx.errors)
        kept = tables[f"{moved[0]}-2027"]
        self.assertEqual(31, len(kept.rows))
        self.assertEqual(kemenag_times(dt.date(2027, 3, 5), 1), kept.rows["2027-03-05"])
        self.assertEqual([f"{moved[0]}-2027-03.json"], [name for name, _ in kept.raw])
        self.assertEqual([f"{moved[0]}-2027"], [k for k in tables if k.endswith("-2027")])
        # Once the monitor holds what was read, the next run has nothing to note.
        self.hold(self.MONITOR, moved[0], 2027, months=[3], shifted={(3, 5): 1, (3, 9): -1})
        http, ctx, tables = self.run_on(today, {(moved[1], 2027, 3): special[(moved[1], 2027, 3)]})
        self.assertEqual(([], []), (ctx.errors, ctx.notes))

    def test_a_held_day_of_the_month_itself_that_changed_is_noted(self):
        place = kemenag.PLACES[0]
        self.hold(kemenag.ROUND, place[0], 2026, shifted={(6, 10): 2})
        http, ctx, tables = self.run_on(dt.date(2026, 6, 10))
        self.assertEqual([f"{place[2]} 2026-06: 1 held day changed (the first 2026-06-10); the table now holds what was read today"], ctx.notes)
        self.assertEqual(kemenag_times(dt.date(2026, 6, 10)), tables[f"{place[0]}-2026"].rows["2026-06-10"])


# Last in the file: every class above it is defined before the tests run (the review round of 13 Nov 2026
# found this block in the middle of the file, so `python3 tests.py` ran only the classes above it).
if __name__ == "__main__":
    unittest.main()
