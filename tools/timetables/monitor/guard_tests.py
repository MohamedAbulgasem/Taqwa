#!/usr/bin/env python3
"""The monitor's guards against a run whose own footing moved, on invented data (never a printed time
from a restricted table): the JDK's tz database (tzdb.py) and QMDB's year API answering a place two ways
(fetchers/qmdb.py). tests.py imports these:

    python3 -m unittest tools/timetables/monitor/tests.py
"""
import datetime as dt
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import tzdb  # noqa: E402
from common import Context, FetchError  # noqa: E402
from fetchers import qmdb  # noqa: E402


def _tzdb_dat(versions, magic="TZDB", fmt=1):
    """An invented tzdb.dat head in the JDK's layout."""
    def utf(s):
        b = s.encode("utf-8")
        return struct.pack(">H", len(b)) + b
    return bytes([fmt]) + utf(magic) + struct.pack(">H", len(versions)) + b"".join(utf(v) for v in versions) + b"\x00" * 16


class TzdbGuard(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = self.tmp.name
        os.makedirs(os.path.join(self.home, "lib"))

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, data):
        with open(os.path.join(self.home, "lib", "tzdb.dat"), "wb") as f:
            f.write(data)

    def test_order(self):
        self.assertTrue(tzdb.at_least("2026e", "2026e"))
        self.assertTrue(tzdb.at_least("2026f", "2026e"))
        self.assertTrue(tzdb.at_least("2027a", "2026e"))
        self.assertTrue(tzdb.at_least("2026aa", "2026z"))
        self.assertFalse(tzdb.at_least("2026d", "2026e"))
        self.assertFalse(tzdb.at_least("2025z", "2026e"))
        with self.assertRaises(ValueError):
            tzdb.at_least("21.0.13", "2026e")

    def test_reads_the_version_and_passes_the_minimum(self):
        self.write(_tzdb_dat(["2026e"]))
        self.assertEqual("2026e", tzdb.read_version(os.path.join(self.home, "lib", "tzdb.dat")))
        code, line = tzdb.check(self.home)
        self.assertEqual(0, code, line)
        self.assertIn("tz database 2026e", line)

    def test_an_older_version_is_refused_with_both_versions_named(self):
        self.write(_tzdb_dat(["2026a"]))
        code, line = tzdb.check(self.home)
        self.assertEqual(4, code)
        self.assertEqual(tzdb.OLDER, code)
        self.assertIn(f"2026a, older than {tzdb.MINIMUM}", line)
        self.assertIn("make JAVA_HOME the JDK Gradle's toolchain runs on", line)

    def test_the_last_named_version_counts(self):
        self.write(_tzdb_dat(["2025b", "2026f"]))
        self.assertEqual(0, tzdb.check(self.home)[0])

    def test_an_unreadable_file_is_not_a_refusal(self):
        self.assertEqual(3, tzdb.check(self.home)[0])  # no tzdb.dat
        self.write(b"not a tzdb")
        self.assertEqual(3, tzdb.check(self.home)[0])
        self.write(_tzdb_dat(["2026e"], magic="ZZZZ"))
        self.assertEqual(3, tzdb.check(self.home)[0])
        self.assertEqual(3, tzdb.check(None)[0])

    def test_a_linked_java_home_is_followed(self):
        # Homebrew's opt/openjdk@21 holds bin/java as a link into the real JDK, with no lib/tzdb.dat of its own.
        self.write(_tzdb_dat(["2026e"]))
        os.makedirs(os.path.join(self.home, "bin"))
        open(os.path.join(self.home, "bin", "java"), "w").close()
        with tempfile.TemporaryDirectory() as opt:
            os.makedirs(os.path.join(opt, "bin"))
            os.symlink(os.path.join(self.home, "bin", "java"), os.path.join(opt, "bin", "java"))
            self.assertEqual(0, tzdb.check(opt)[0])

    def test_the_minimum_is_the_kotlin_checks(self):
        kt = os.path.join(HERE, "..", "src", "jvmMain", "kotlin", "world", "taqwa", "timetables", "gate", "Tzdb.kt")
        with open(kt, encoding="utf-8") as f:
            m = re.search(r'const val MINIMUM = "([^"]+)"', f.read())
        self.assertEqual(tzdb.MINIMUM, m.group(1) if m else None)

    def test_monitor_sh_refuses_an_older_jdk_before_it_fetches(self):
        with open(os.path.join(HERE, "..", "..", "..", "scripts", "monitor.sh"), encoding="utf-8") as f:
            sh = f.read()
        at = sh.index("tools/timetables/monitor/tzdb.py")
        self.assertLess(at, sh.index("tools/timetables/monitor/fetch.py"))
        self.assertIn('[ "$TZDB_CODE" = 4 ] && failed "$TZDB_LINE"', sh)
        self.assertEqual(4, tzdb.OLDER)

    def test_a_crash_is_not_a_refusal(self):
        # Python exits 1 on an uncaught error; monitor.sh refuses only on tzdb.OLDER, and the Pages build lets 3 pass.
        self.assertNotIn(tzdb.OLDER, (0, 1, 2, tzdb.UNREAD))
        with open(os.path.join(HERE, "..", "..", "..", ".github", "workflows", "pages.yml"), encoding="utf-8") as f:
            self.assertIn('tzdb.py --java-home "$JAVA_HOME" || [ $? -eq 3 ]', f.read())


def _hm(minutes):
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def _qmdb_year(year, band=3, wrong_from=None, wrong_band=5, move=0):
    """An invented QMDB year reply (a slow ramp of times no Kazakh table prints, ruling R69): Maghrib is
    sunset + `band`, from `wrong_from` on the other band's minutes (sunrise earlier, Dhuhr, Asr, Maghrib
    later, as the API's wrong answer of 9 Nov 2026 had them), every time moved by `move` minutes."""
    days = []
    start = dt.date(year, 1, 1)
    for i in range((dt.date(year + 1, 1, 1) - start).days):
        d = start + dt.timedelta(days=i)
        b = wrong_band if wrong_from and d.isoformat() >= wrong_from else band
        k = b - band
        sunset = 15 * 60 + 10 + (i % 90) + move
        days.append({
            "imsak": _hm(5 * 60 + i % 40 + move), "fajr": _hm(5 * 60 + 10 + i % 40 + move), "sunrise": _hm(6 * 60 + 20 + i % 40 + move - k),
            "dhuhr": _hm(12 * 60 + 5 + i % 9 + move + k), "asr": _hm(14 * 60 + i % 50 + move + k), "sunset": _hm(sunset),
            "maghrib": _hm(sunset + b), "isha": _hm(sunset + 75), "midnight": "00:10", "Date": d.isoformat(),
        })
    return json.dumps({"result": days, "latitude": "1", "longitude": "2", "year": year, "city": None}).encode()


class _QueueHttp:
    """Answers each URL from its own queue of replies, in order (the last one repeats)."""

    def __init__(self, answers):
        self.answers = {u: list(a) for u, a in answers.items()}
        self.urls = []
        self.owner = None

    def get(self, url, **kw):
        self.urls.append(url)
        queue = self.answers.get(url)
        if not queue:
            raise FetchError("no canned answer")
        answer = queue.pop(0) if len(queue) > 1 else queue[0]
        if isinstance(answer, Exception):
            raise answer
        return answer


class QmdbGuard(unittest.TestCase):
    SOUTH = ("south", "South", "47.799711", "67.714090", "Asia/Almaty")  # below 48N: Maghrib is sunset + 3
    TODAY = dt.date(2026, 11, 12)

    def setUp(self):
        self.root = tempfile.mkdtemp("monitor-qmdb")
        self.points, self.pause = qmdb.POINTS, qmdb.RETRY_PAUSE
        qmdb.POINTS, qmdb.RETRY_PAUSE = [self.SOUTH], 0
        self.url = {y: f"{qmdb.BASE}/{y}/{self.SOUTH[2]}/{self.SOUTH[3]}" for y in (2026, 2027)}
        self.lines = []

    def tearDown(self):
        qmdb.POINTS, qmdb.RETRY_PAUSE = self.points, self.pause
        shutil.rmtree(self.root)

    def hold(self, year, body):
        path = os.path.join(self.root, "archive", "tables", "monitor", qmdb.SOURCE, f"south-{year}.txt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        rows = qmdb.normalised(qmdb.parse(body))
        with open(path, "w", encoding="utf-8") as f:
            f.write("# held\n" + "".join(f"{d} {' '.join(rows[d])}\n" for d in sorted(rows)))
        return rows

    def run_fetch(self, answers2026, answers2027=None):
        http = _QueueHttp({self.url[2026]: answers2026, self.url[2027]: answers2027 or [_qmdb_year(2027)]})
        ctx = Context(qmdb.SOURCE, self.root, self.TODAY, self.lines.append, self.root, http=http)
        tables = {t.key: t for t in qmdb.fetch(ctx)}
        return tables, ctx, http.urls.count(self.url[2026])

    def test_the_band_rule(self):
        self.assertEqual([], qmdb.off_band(_qmdb_year(2026), 47.8))
        self.assertEqual([], qmdb.off_band(_qmdb_year(2026, band=5), 48.0))
        wrong = qmdb.off_band(_qmdb_year(2026, wrong_from="2026-03-05"), 47.8)
        self.assertEqual(302, len(wrong))
        self.assertEqual(("2026-03-05", "2026-12-31"), (wrong[0], wrong[-1]))
        self.assertEqual(365, len(qmdb.off_band(_qmdb_year(2026), 48.8)))  # the south's minutes at a northern place

    def test_a_one_off_wrong_answer_is_asked_again_and_the_held_copy_stays(self):
        held = self.hold(2026, _qmdb_year(2026))
        tables, ctx, asked = self.run_fetch([_qmdb_year(2026, wrong_from="2026-03-05"), _qmdb_year(2026)])
        self.assertEqual(2, asked)
        self.assertEqual(held, tables["south-2026"].rows)
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("reply 1 not taken yet: Maghrib is not sunset + 3 min" in line and "302 days 2026-03-05..2026-12-31" in line
                            and "sunrise -2 on 302 days, dhuhr +2 on 302 days, asr +2 on 302 days, maghrib +2 on 302 days" in line
                            for line in self.lines), self.lines)
        self.assertTrue(any("reply 2 taken, as the held copy has it" in n for n in ctx.notes), ctx.notes)

    def test_a_server_that_keeps_answering_wrong_keeps_the_held_copy_and_says_so(self):
        held = self.hold(2026, _qmdb_year(2026))
        tables, ctx, asked = self.run_fetch([_qmdb_year(2026, wrong_from="2026-03-05"), _qmdb_year(2026, wrong_from="2026-08-31"),
                                             _qmdb_year(2026, wrong_from="2026-02-19")])
        self.assertEqual(qmdb.REQUESTS, asked)
        self.assertEqual(held, tables["south-2026"].rows)
        self.assertEqual(1, len(ctx.errors), ctx.errors)
        self.assertIn("QMDB answered two ways in 3 requests", ctx.errors[0])
        self.assertIn("the held copy is kept", ctx.errors[0])
        self.assertEqual([], tables["south-2026"].raw)

    def test_a_real_change_is_taken_when_two_replies_agree(self):
        self.hold(2026, _qmdb_year(2026))
        moved = _qmdb_year(2026, move=1)
        tables, ctx, asked = self.run_fetch([moved, moved])
        self.assertEqual(2, asked)
        self.assertEqual(qmdb.normalised(qmdb.parse(moved)), tables["south-2026"].rows)
        self.assertEqual([], ctx.errors)
        self.assertTrue(any("two replies agree on a new table (365 days 2026-01-01..2026-12-31 moved" in n for n in ctx.notes), ctx.notes)

    def test_a_change_one_reply_alone_says_is_not_taken(self):
        held = self.hold(2026, _qmdb_year(2026))
        tables, ctx, asked = self.run_fetch([_qmdb_year(2026, move=1), _qmdb_year(2026, move=2), _qmdb_year(2026, move=3)])
        self.assertEqual(3, asked)
        self.assertEqual(held, tables["south-2026"].rows)
        self.assertIn("QMDB answered two ways", ctx.errors[0])

    def test_a_new_year_is_taken_on_one_good_reply(self):
        tables, ctx, asked = self.run_fetch([_qmdb_year(2026)])
        self.assertEqual(1, asked)
        self.assertEqual(365, len(tables["south-2026"].rows))
        self.assertEqual(365, len(tables["south-2027"].rows))
        self.assertEqual(["south-2026.json"], [name for name, _ in tables["south-2026"].raw])
        self.assertEqual([], ctx.errors)

    def test_nothing_held_and_every_reply_wrong_is_no_table(self):
        tables, ctx, asked = self.run_fetch([_qmdb_year(2026, wrong_from="2026-03-05")])
        self.assertEqual(3, asked)
        self.assertNotIn("south-2026", tables)
        self.assertIn("QMDB answered the same 3 times against its own band rule", ctx.errors[0])
        self.assertIn("it may have changed its method", ctx.errors[0])
        self.assertIn("change BAND_LAT/band_minutes in tools/timetables/monitor/fetchers/qmdb.py", ctx.errors[0])
        self.assertIn("nothing is held, so no table", ctx.errors[0])

    def test_a_method_change_across_places_is_asked_once_per_place_after_the_first_few(self):
        # Every place answers the same way every time against the band rule (a new Maghrib offset, say):
        # the first METHOD_PLACES are asked REQUESTS times, every other place-year once.
        points = [(f"p{i}", f"P{i}", f"47.{i:06d}", "67.714090", "Asia/Almaty") for i in range(5)]
        qmdb.POINTS = points
        urls = {f"{qmdb.BASE}/{y}/{lat}/{lon}": [_qmdb_year(y, band=4)] for y in (2026, 2027) for _, _, lat, lon, _ in points}
        http = _QueueHttp(urls)
        ctx = Context(qmdb.SOURCE, self.root, self.TODAY, self.lines.append, self.root, http=http)
        tables = qmdb.fetch(ctx)
        self.assertEqual([], tables)
        self.assertEqual(qmdb.METHOD_PLACES * qmdb.REQUESTS + (2 * len(points) - qmdb.METHOD_PLACES), len(http.urls))
        self.assertEqual(2 * len(points), len(ctx.errors), ctx.errors)
        self.assertTrue(all("BAND_LAT/band_minutes" in e for e in ctx.errors), ctx.errors)
        self.assertIn("asked once since 3 places this run (P0 2026, P1 2026, P2 2026)", ctx.errors[-1])
        self.assertTrue(any("the rest of this run asks each place once" in n for n in ctx.notes), ctx.notes)

    def test_places_answering_two_ways_never_count_as_a_method_change(self):
        points = [(f"p{i}", f"P{i}", f"47.{i:06d}", "67.714090", "Asia/Almaty") for i in range(4)]
        qmdb.POINTS = points
        two_ways = [_qmdb_year(2026, wrong_from="2026-03-05"), _qmdb_year(2026, wrong_from="2026-08-31"), _qmdb_year(2026, wrong_from="2026-02-19")]
        urls = {f"{qmdb.BASE}/2026/{lat}/{lon}": two_ways for _, _, lat, lon, _ in points}
        urls.update({f"{qmdb.BASE}/2027/{lat}/{lon}": [_qmdb_year(2027)] for _, _, lat, lon, _ in points})
        http = _QueueHttp(urls)
        ctx = Context(qmdb.SOURCE, self.root, self.TODAY, self.lines.append, self.root, http=http)
        qmdb.fetch(ctx)
        self.assertEqual(len(points) * qmdb.REQUESTS + len(points), len(http.urls))

    def test_no_year_at_all_is_a_fetch_error_as_before(self):
        tables, ctx, asked = self.run_fetch([b'{"detail": "Not found."}'])
        self.assertEqual(1, asked)
        self.assertNotIn("south-2026", tables)
        self.assertIn("South 2026: no days in the response", ctx.errors)


if __name__ == "__main__":
    unittest.main()
