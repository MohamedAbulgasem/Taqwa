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


class Attention(unittest.TestCase):
    """What in prove's outcome reaches the issue even on a green week, and the message's time guard."""

    CLEAN = dict(Proof.REPORT, leftOut=[])

    def test_a_clean_outcome_needs_nothing(self):
        self.assertEqual(("", False, ""), proof.attention(self.CLEAN, Proof.BEFORE, Proof.BEFORE, "pushed to main (abc1234)", "0"))
        self.assertEqual(("", False, ""), proof.attention(None, [], [], "not run", ""))

    def test_an_early_start_left_out_is_tier_one_and_its_fingerprint_ignores_numbers_and_dates(self):
        fp, tier1, summary = proof.attention(Proof.REPORT, Proof.BEFORE, Proof.BEFORE, "pushed to main (abc1234)", "0")
        self.assertTrue(fp)
        self.assertTrue(tier1)
        self.assertIn("1 rows left out (1 with an early start or a late end)", summary)
        later = dict(Proof.REPORT, date="2026-11-09", leftOut=[{"row": Proof.REPORT["leftOut"][0]["row"], "reason": "xx.test fajr: 3 early\n    2026-11-10 fajr: 2 min early (q)"}])
        self.assertEqual(fp, proof.attention(later, Proof.BEFORE, Proof.BEFORE, "pushed to main (def5678)", "0")[0])
        other = dict(Proof.REPORT, leftOut=[{"row": "xx-test.tsv xx.test/gamma at unit from xx-test/gamma", "reason": "xx.test isha: 4 over the late limit"}])
        fp2, tier1_2, _ = proof.attention(other, Proof.BEFORE, Proof.BEFORE, "", "0")
        self.assertNotEqual(fp, fp2)
        self.assertFalse(tier1_2)

    def test_a_push_that_did_not_land_a_city_newly_held_or_prove_stopping_needs_the_owner(self):
        self.assertTrue(proof.attention(self.CLEAN, Proof.BEFORE, Proof.BEFORE, "not pushed: the secret TAQWA_PUSH_TOKEN is not set", "0")[0])
        _, _, summary = proof.attention(self.CLEAN, Proof.BEFORE, Proof.AFTER, "pushed to main (abc1234)", "0")
        self.assertIn("cities newly held: delta-town", summary)
        self.assertTrue(proof.attention(dict(self.CLEAN, failure="the gate is red before prove at xx.test"), [], [], "", "2")[0])
        self.assertTrue(proof.attention(None, [], [], "", "2")[0])

    def test_the_attention_command_writes_output_lines(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "proof.json"), "w", encoding="utf-8") as f:
                json.dump(Proof.REPORT, f)
            out = os.path.join(d, "out.txt")
            stdout = sys.stdout
            try:
                with open(out, "w", encoding="utf-8") as sys.stdout:
                    self.assertEqual(0, proof.main(["proof.py", "attention", d, "--pushed", "nothing to push", "--code", "0"]))
            finally:
                sys.stdout = stdout
            lines = open(out, encoding="utf-8").read().splitlines()
            self.assertEqual(["fingerprint", "tier1", "summary"], [line.split("=", 1)[0] for line in lines])
            self.assertEqual("tier1=true", lines[1])

    def test_a_commit_message_with_a_clock_time_is_refused(self):
        self.assertEqual([], proof.timed_lines(proof.message(Proof.REPORT, Proof.BEFORE, Proof.AFTER)))
        self.assertEqual(["Fajr 04:31"], proof.timed_lines("UTC+02:00\nFajr 04:31\n2026-11-02"))
        timed = dict(Proof.REPORT, added=[dict(Proof.REPORT["added"][0], gate="xx 04:31.tsv")])
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "proof.json"), "w", encoding="utf-8") as f:
                json.dump(timed, f)
            stdout = sys.stdout
            try:
                with open(os.path.join(d, "out.txt"), "w", encoding="utf-8") as sys.stdout:
                    code = proof.main(["proof.py", "message", d])
            finally:
                sys.stdout = stdout
            self.assertEqual(3, code)


class Workflow(unittest.TestCase):
    """The workflow template's shell: every step parses, actions are pinned, and the push token stays in its place."""

    PATH = os.path.join(HERE, "ci", "monitor-weekly.yml")

    def steps(self):
        """(name, run script) of every step, `${{ … }}` replaced by a placeholder."""
        import re
        with open(self.PATH, encoding="utf-8") as f:
            lines = f.read().split("\n")
        out, name, i = [], None, 0
        while i < len(lines):
            line = lines[i]
            m = re.match(r"^\s*- name: (.*)$", line)
            if m:
                name = m.group(1)
            m = re.match(r"^(\s*)run: \|\s*$", line)
            if m:
                indent = len(m.group(1))
                body = []
                i += 1
                while i < len(lines) and (not lines[i].strip() or len(lines[i]) - len(lines[i].lstrip()) > indent):
                    body.append(lines[i])
                    i += 1
                out.append((name, re.sub(r"\$\{\{[^}]*\}\}", "X", "\n".join(body))))
                continue
            i += 1
        return out

    def test_every_bash_step_parses(self):
        import subprocess
        steps = self.steps()
        self.assertGreater(len(steps), 8)
        for name, script in steps:
            if "python3 - <<'PY'" in script:
                script = script.split("python3 - <<'PY'")[0]
            r = subprocess.run(["bash", "-n"], input=script, capture_output=True, text=True)
            self.assertEqual(0, r.returncode, "%s: %s" % (name, r.stderr))

    def test_actions_are_pinned_by_commit(self):
        import re
        with open(self.PATH, encoding="utf-8") as f:
            lines = f.read().splitlines()
        for line in lines:
            if "uses:" in line:
                self.assertRegex(line, r"uses: [\w.-]+/[\w.-]+@[0-9a-f]{40} # v", line)

    def test_the_daily_check_names_its_own_cron_and_gates_only_that_cron(self):
        import re
        with open(self.PATH, encoding="utf-8") as f:
            text = f.read()
        crons = re.findall(r'- cron: "([^"]+)"', text)
        self.assertEqual(["0 3 * * 1", "17 3 1 * *", "23 1 * * *"], crons)
        daily = "github.event.schedule == '23 1 * * *'"
        # The two checkouts and the decision ask about the daily cron alone; the full run's job asks the same question.
        self.assertEqual(3, text.count(daily), "the due job's two checkouts and its decision")
        self.assertIn("github.event.schedule != '23 1 * * *'", text)
        self.assertIn("needs: due", text)
        self.assertIn("needs.due.outputs.run == 'true'", text)
        decide = dict(self.steps())["Is a new month due today?"]
        self.assertIn("fetch.py --official \"$GITHUB_WORKSPACE\" --month-turn", decide)
        self.assertIn("run=true", decide)
        self.assertIn("run=false", decide)

    def test_the_push_token_is_unset_before_anything_else_runs_and_never_on_a_command_line(self):
        publish = dict(self.steps())["Push the proof to Taqwa's main"]
        uses = [i for i, line in enumerate(publish.split("\n")) if "$TAQWA_PUSH_TOKEN" in line or "${TAQWA_PUSH_TOKEN" in line]
        unset = [i for i, line in enumerate(publish.split("\n")) if "unset TAQWA_PUSH_TOKEN" in line]
        self.assertTrue(unset)
        self.assertTrue(all(i < max(unset) for i in uses), uses)
        self.assertIn('G() { git -c core.hooksPath=/dev/null "$@"; }', publish)
        self.assertNotIn("gradlew", publish)
        self.assertIn("GIT_CONFIG_VALUE_0", publish)
        # The header reaches git on the push commands alone.
        authed = [line.strip() for line in publish.split("\n") if "authed " in line and "authed()" not in line]
        self.assertTrue(authed)
        self.assertTrue(all(line.startswith("if authed push ") for line in authed), authed)
        # Pushed only when main's commits since the run's checkout leave what the proof depends on alone.
        self.assertIn('G diff --name-only "$PROVEN_ON" HEAD', publish)
        self.assertIn("handoff.py check --kind public", publish)

    def job(self, name):
        with open(self.PATH, encoding="utf-8") as f:
            text = f.read()
        start = text.index("\n  %s:\n" % name)
        ends = [text.index("\n  %s:\n" % other, start + 1) for other in ("due", "monitor", "publish", "report")
                if "\n  %s:\n" % other in text[start + 1:]]
        return text[start:min(ends)] if ends else text[start:]

    def test_each_job_holds_only_what_it_needs(self):
        # Security review D10: nothing by default, read-only where third-party code runs.
        import re
        with open(self.PATH, encoding="utf-8") as f:
            self.assertIn("\npermissions: {}\n", f.read())
        def permissions(job):
            m = re.search(r"\n    permissions:\n((?:      .*\n)+)", self.job(job))
            return sorted(line.strip() for line in m.group(1).splitlines())
        self.assertEqual(["contents: read"], permissions("due"))
        self.assertEqual(["contents: read"], permissions("monitor"))
        self.assertEqual(["contents: write"], permissions("publish"))
        self.assertEqual(["contents: read", "issues: write"], permissions("report"))

    def test_the_push_token_lives_on_a_clean_runner_that_runs_no_fetcher_pip_or_gradle(self):
        # Security review D1: what ran before the push (the fetchers, pip, Gradle) could read a token on its runner.
        with open(self.PATH, encoding="utf-8") as f:
            text = f.read()
        publish = self.job("publish")
        self.assertEqual(text.count("secrets.TAQWA_PUSH_TOKEN"), publish.count("secrets.TAQWA_PUSH_TOKEN"))
        self.assertEqual(1, publish.count("secrets.TAQWA_PUSH_TOKEN"))
        for word in ("pip ", "gradlew", "setup-java", "setup-python", "prove.sh", "monitor.sh", "fetch.py", "--taqwa"):
            self.assertNotIn(word, publish, word)
        self.assertNotIn("TAQWA_PUSH_TOKEN", self.job("monitor"))
        self.assertNotIn("contents: write", self.job("monitor"))
        # Every git command in the publish job runs with hooks off (G), never a bare git.
        for name, script in self.steps():
            if name in ("Commit what the run added or changed", "Push the proof to Taqwa's main"):
                bare = [line for line in script.split("\n") if line.strip().startswith("git ")]
                self.assertEqual([], bare, name)

    def test_pip_installs_are_pinned_with_their_hashes(self):
        with open(self.PATH, encoding="utf-8") as f:
            installs = [line for line in f.read().splitlines() if "pip install" in line]
        self.assertTrue(installs)
        for line in installs:
            self.assertIn("--require-hashes", line)

    def test_no_script_reads_an_expression_directly(self):
        # What a job hands on (a summary, a commit, a date) reaches a script through the environment alone.
        import re
        with open(self.PATH, encoding="utf-8") as f:
            lines = f.read().split("\n")
        i = 0
        while i < len(lines):
            m = re.match(r"^(\s*)run: (.*)$", lines[i])
            if m and not lines[i].lstrip().startswith("run: ${{ steps."):
                indent, body = len(m.group(1)), [m.group(2)]
                i += 1
                while i < len(lines) and (not lines[i].strip() or len(lines[i]) - len(lines[i].lstrip()) > indent):
                    body.append(lines[i])
                    i += 1
                self.assertNotIn("${{", "\n".join(body))
                continue
            i += 1

    def test_prove_runs_the_apps_tests_on_the_platform_the_app_compiles_against(self):
        # prove.sh's last check is the app's own tests on the new ProofStamps.kt (the proof of 12 October 2026's
        # captures failed main's Tests and iOS once published), so the monitor installs the Android SDK platform of
        # compileSdk just before prove, by the package name tests.yml installs: a compileSdk moved in one place fails
        # here, not on a run. The step runs when prove does and never fails the job (a missing platform puts back).
        import re
        root = os.path.join(HERE, "..", "..", "..")
        with open(os.path.join(root, "gradle", "libs.versions.toml"), encoding="utf-8") as f:
            sdk = re.search(r'^android-compileSdk = "(\d+)"$', f.read(), re.M).group(1)
        package = '"platforms;android-%s.0"' % sdk
        with open(self.PATH, encoding="utf-8") as f:
            text = f.read()
        with open(os.path.join(root, ".github", "workflows", "tests.yml"), encoding="utf-8") as f:
            self.assertIn(package, f.read())
        install = text.index("- name: Android SDK platform %s\n" % sdk)
        prove = text.index("- name: Prove the new captures")
        self.assertIn(package, text[install:prove])
        conditions = re.findall(r"^        if: (.*)$", text[install:], re.M)
        self.assertEqual(conditions[0], conditions[1], "the install's condition is prove's")
        self.assertIn("        continue-on-error: true\n", text[install:prove])
        with open(os.path.join(root, "scripts", "prove.sh"), encoding="utf-8") as f:
            script = f.read()
        self.assertRegex(script, r'\nCI=true "\$GRADLE" --no-daemon -q :shared:testAndroidHostTest \|\| put_back ')
        self.assertLess(script.index("site/test_timetables.py ||"), script.index(":shared:testAndroidHostTest ||"))
        self.assertLess(script.index(":shared:testAndroidHostTest ||"), script.index('echo true > "$OUT/changed"'))


class Handoff(unittest.TestCase):
    """ci/handoff.py: the proving job packs, the publish job checks before anything is committed (docs/MONITOR.md)."""

    STAMPS = "tools/timetables/official/stamps"
    GATE = "tools/timetables/official/gate"

    def setUp(self):
        import shutil
        import subprocess
        sys.path.insert(0, os.path.join(HERE, "ci"))
        import handoff
        import proof_stamps
        self.handoff, self.proof_stamps, self.run = handoff, proof_stamps, subprocess.run
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        self.base = self.repo("base")
        self.write(self.base, self.GATE + "/xx-test.tsv", "path\tentry\n# a comment\n")
        self.write(self.base, self.STAMPS + "/xx.test.json", json.dumps({"entry": "xx.test", "places": 1, "placeDays": 30}))
        self.stamps(self.base)
        self.write(self.base, "archive/tables/a.txt", "a\n")
        self.git(self.base, "add", "-A")
        self.git(self.base, "commit", "-q", "-m", "base")

    def git(self, repo, *args):
        self.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", *args], check=True, capture_output=True)

    def repo(self, name):
        path = os.path.join(self.tmp, name)
        self.run(["git", "init", "-q", path], check=True)
        return path

    def clone(self, name):
        path = os.path.join(self.tmp, name)
        self.run(["git", "clone", "-q", self.base, path], check=True)
        return path

    def write(self, repo, rel, text):
        os.makedirs(os.path.dirname(os.path.join(repo, rel)), exist_ok=True)
        with open(os.path.join(repo, rel), "w", encoding="utf-8") as f:
            f.write(text)

    def stamps(self, repo):
        self.write(repo, self.handoff_path(), self.proof_stamps.generate(os.path.join(repo, self.STAMPS)))

    def handoff_path(self):
        return "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt"

    def proved(self):
        """A run that adds a gate row and changes a stamp, and the pack of it."""
        work = self.clone("work")
        self.write(work, self.GATE + "/xx-test.tsv", "path\tentry\n# a comment\nnew-row\txx.test\n")
        self.write(work, self.STAMPS + "/xx.test.json", json.dumps({"entry": "xx.test", "places": 1, "placeDays": 60}))
        self.stamps(work)
        self.write(work, "monitor/reports/r.md", "report\n")
        out = os.path.join(self.tmp, "out")
        self.assertEqual(0, self.handoff.main(["pack", "--archive", work, "--taqwa", work, "--out", out]))
        self.write(out, "proof-message.txt", "Gate: the weekly monitor's captures proven (1 row)\n")
        return out

    def check(self, out, kind="public"):
        target = self.clone("target-%s-%d" % (kind, len(os.listdir(self.tmp))))
        return self.handoff.main(["check", "--kind", kind, "--from", out, "--into", target]), target

    def tamper(self, out, rel, text, kind="public"):
        """Rewrites a packed file and its manifest line, as a compromised proving job could."""
        import hashlib
        self.write(os.path.join(out, kind), rel, text)
        digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
        manifest = os.path.join(out, kind + ".manifest")
        with open(manifest, encoding="utf-8") as f:
            lines = [line for line in f.read().splitlines() if not line.endswith("\t" + rel)]
        lines.append("A\t%s\t%s" % (digest, rel))
        with open(manifest, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

    def test_a_proof_packed_by_one_job_is_applied_whole_by_the_other(self):
        out = self.proved()
        code, target = self.check(out)
        self.assertEqual(0, code)
        with open(os.path.join(target, self.handoff_path()), encoding="utf-8") as f:
            self.assertIn("placeDays = 60,", f.read())
        code, target = self.check(out, "private")
        self.assertEqual(0, code)
        self.assertTrue(os.path.isfile(os.path.join(target, "monitor/reports/r.md")))

    def test_kotlin_the_stamps_do_not_make_is_refused(self):
        out = self.proved()
        self.tamper(out, self.handoff_path(), self.proof_stamps.generate(os.path.join(self.tmp, "work", self.STAMPS)) + "fun x() = 1\n")
        self.assertEqual(3, self.check(out)[0])

    def test_a_path_outside_the_allowlist_or_a_clock_time_or_a_wrong_hash_is_refused(self):
        for rel, text in [("site/index.html", "x"), ("tools/timetables/official/gate/x.kts", "x"), ("scripts/prove.sh", "x"),
                          (self.GATE + "/xx-test.tsv", "path\tentry\nrow at 05:12\n")]:
            out = self.proved()
            self.tamper(out, rel, text)
            self.assertEqual(3, self.check(out)[0], rel)
            import shutil
            shutil.rmtree(out)
            shutil.rmtree(os.path.join(self.tmp, "work"))
        out = self.proved()
        self.write(os.path.join(out, "public"), self.GATE + "/xx-test.tsv", "changed after packing\n")
        self.assertEqual(3, self.check(out)[0], "a hash the manifest does not have")

    def test_the_commit_message_is_plain_text_without_a_time_or_a_trailer(self):
        import shutil
        for text in ["Gate: proven at 05:12\n", "Gate: proven\n\nCo-Authored-By: someone <a@b>\n", "Gate\x07\n", ""]:
            out = self.proved()
            self.write(out, "proof-message.txt", text)
            self.assertEqual(3, self.check(out)[0], repr(text))
            shutil.rmtree(out)
            shutil.rmtree(os.path.join(self.tmp, "work"))

    def test_a_binary_gate_file_is_refused_even_where_git_shows_no_lines(self):
        out = self.proved()
        self.tamper(out, self.GATE + "/xx-test.tsv", "path\tentry\n\0row at 05:12\n")
        self.assertEqual(3, self.check(out)[0])

    def test_the_archive_side_takes_archive_and_monitor_alone(self):
        for rel in [".github/workflows/monitor-weekly.yml", "archive/../x", "README.md", "monitor/.gitattributes", "archive/.x/y"]:
            out = self.proved()
            self.tamper(out, rel, "x", kind="private")
            self.assertEqual(3, self.check(out, "private")[0], rel)
            import shutil
            shutil.rmtree(out)
            shutil.rmtree(os.path.join(self.tmp, "work"))


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
        fetched = {r[0] for r in rows[1:] if r[2] not in ("manual", "mawaqit")}
        self.assertEqual(set(), fetched - {cells[0] for cells in lines[1:]}, "a fetched source without a recipe")


if __name__ == "__main__":
    unittest.main()
