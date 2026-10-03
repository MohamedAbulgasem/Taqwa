#!/usr/bin/env python3
"""prove's Python half (proof.py) and the recipes' catalogue, on invented reports (never a printed time):

    python3 -m unittest tools/timetables/monitor/tests.py   # runs these too
"""
import json
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import proof  # noqa: E402


class Proof(unittest.TestCase):
    """prove's issue section and public commit message (docs/MONITOR.md, "Prove"), on invented reports."""

    REPORT = {
        "failure": None, "changed": True, "date": "2026-11-02", "rowsBefore": 10, "rowsAfter": 12, "placeDaysBefore": 100, "placeDaysAfter": 160,
        "added": [
            {"gate": "xx-test.tsv", "entry": "xx.test/alpha", "member": None, "point": "unit", "path": "archive/tables/pinned/xx-test/2026-11-02/alpha.txt",
             "capture": "xx-test/alpha", "days": ["2026-11-01..2026-11-20", "2026-11-22..2026-11-30"], "dayCount": 29, "recipe": "recipes.tsv:30", "family": "f"},
            {"gate": "xx-cautious.tsv", "entry": "xx.cautious", "member": "xx.test", "point": "1.5,2.5", "path": "p", "capture": "xx-test/alpha",
             "days": ["2026-11-01..2026-11-30"], "dayCount": 30, "recipe": "recipes.tsv:31", "family": "f"},
        ],
        "leftOut": [{"row": "xx-test.tsv xx.test/beta at unit from xx-test/beta", "reason": "xx.test fajr: 2 early\n    2026-11-03 fajr: 1 min early (p)"}],
        "pinned": ["archive/tables/pinned/xx-test/2026-11-02/alpha.txt"],
    }
    BEFORE = ["alpha-town: held — xx.test: no checked table day 1 Nov 2026 – 30 Nov 2026", "beta-town: held — not measured at this place",
              "gamma-town: showing October alone — the next month is not yet checked: xx.test, first unchecked day 2026-11-01"]
    AFTER = ["beta-town: held — not measured at this place", "delta-town: held — class D_AUTHORITY is not proven"]

    def test_the_section_lists_rows_added_and_left_out_and_the_cities_newly_proven(self):
        text = proof.section(self.REPORT, self.BEFORE, self.AFTER, pushed="pushed to main (abc1234)")
        self.assertTrue(text.startswith("## Proof\n"))
        self.assertIn("2 rows added, 1 left out; the whole gate 10 -> 12 rows, 100 -> 160 place-days, green.", text)
        self.assertIn("- xx-test.tsv: xx.test/alpha at unit, 29 days 2026-11-01..2026-11-30 (xx-test/alpha)", text)
        self.assertIn("- xx-cautious.tsv: xx.cautious member xx.test at 1.5,2.5, 30 days", text)
        self.assertIn("- xx-test.tsv xx.test/beta at unit from xx-test/beta: xx.test fajr: 2 early 2026-11-03 fajr: 1 min early (p)", text)
        self.assertIn("Cities newly proven: alpha-town.", text)
        self.assertIn("Cities newly held: delta-town.", text)
        self.assertIn("Public repository: pushed to main (abc1234)", text)

    def test_a_failure_or_no_report_says_nothing_was_added(self):
        failed = dict(self.REPORT, failure="the gate is red before prove at xx.test and no new row repairs it")
        self.assertIn("Prove could not run, so nothing was added: the gate is red", proof.section(failed, [], []))
        self.assertIn("Prove did not run", proof.section(None, [], []))
        put_back = dict(self.REPORT, putBack="the tools' tests failed", changed=False)
        text = proof.section(put_back, self.BEFORE, self.BEFORE)
        self.assertIn("Prove added 2 rows with the whole gate green, but a check after it failed (the tools' tests failed), so everything was put back", text)
        self.assertNotIn("2 rows added, 1 left out", text)

    def test_the_commit_message_names_counts_files_entries_and_cities_never_a_time_or_a_table_date(self):
        text = proof.message(self.REPORT, self.BEFORE, self.AFTER)
        self.assertTrue(text.startswith("Gate: the weekly monitor's captures of 2026-11-02 proven (2 rows)\n"))
        self.assertIn("Gate files: xx-cautious.tsv, xx-test.tsv.", text)
        self.assertIn("Entries: xx.cautious, xx.test.", text)
        self.assertIn("Cities newly proven: alpha-town.", text)
        self.assertNotRegex(text, r"\b\d{1,2}:\d{2}\b")
        self.assertNotIn("2026-11-01", text)
        self.assertNotIn("Co-Authored-By", text)

    def test_load_reads_the_proof_folder(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual((None, [], []), proof.load(d))
            with open(os.path.join(d, "proof.json"), "w", encoding="utf-8") as f:
                json.dump(self.REPORT, f)
            with open(os.path.join(d, "notices-before.txt"), "w", encoding="utf-8") as f:
                f.write("\n".join(self.BEFORE) + "\n")
            report, before, after = proof.load(d)
            self.assertEqual(2, len(report["added"]))
            self.assertEqual(3, len(before))
            self.assertEqual([], after)


class RecipesCatalogue(unittest.TestCase):
    """official/monitor/recipes.tsv: every source a source of sources.tsv, every gate file present, metadata only."""

    def test_recipes_name_known_sources_and_gate_files_and_no_time(self):
        official = os.path.join(HERE, "..", "official")
        with open(os.path.join(official, "monitor", "sources.tsv"), encoding="utf-8") as f:
            rows = [line.rstrip("\n").split("\t") for line in f if line.strip() and not line.startswith("#")]
        sources = {r[0] for r in rows[1:]}
        with open(os.path.join(official, "monitor", "recipes.tsv"), encoding="utf-8") as f:
            text = f.read()
        self.assertNotRegex(text, r"\b\d{1,2}:\d{2}\b")
        lines = [line.split("\t") for line in text.splitlines() if line.strip() and not line.startswith("#")]
        self.assertEqual(["source", "capture", "gate", "entry", "member", "point", "days", "from"], lines[0])
        for cells in lines[1:]:
            self.assertEqual(8, len(cells), cells)
            self.assertIn(cells[0], sources, cells)
            self.assertTrue(os.path.isfile(os.path.join(official, "gate", cells[2])), cells)
            if cells[7] != "-":
                self.assertIn(cells[7].split("/")[0], sources, cells)
        fetched = {r[0] for r in rows[1:] if r[2] not in ("manual", "mawaqit", "london")}
        self.assertEqual(set(), fetched - {cells[0] for cells in lines[1:]}, "a fetched source without a recipe")


if __name__ == "__main__":
    unittest.main()
