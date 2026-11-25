"""Tests of tools/privacy-check.py, the watch release's privacy gate (plan 5 Task 9): each way a
privacy line can be wrong makes it fail, by name, on a scratch tree. Standard library and git
only; scripts/test.sh runs them before the check itself, and so can anyone:

    python3 tools/test_privacy_check.py
"""
import datetime
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import unittest

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
spec = importlib.util.spec_from_file_location("privacy_check", os.path.join(TOOLS, "privacy-check.py"))
pc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pc)

LANGS = {"en", "ar", "fr", "tr", "id", "ur", "bn"}


class ScratchTree(unittest.TestCase):
    """A throwaway tree with only the files a case writes, staged in its own git repository so
    that the sweep lists them as it lists the real tree's files."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = self.dir.name
        subprocess.run(["git", "init", "-q", self.root], check=True)

    def tearDown(self):
        self.dir.cleanup()

    def write(self, path, text):
        full = os.path.join(self.root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(text)
        subprocess.run(["git", "-C", self.root, "add", path], check=True)


def script_commands(text):
    """A shell script's commands, as the shell reads its lines: a line ending in a backslash is
    joined to the next one (Ruling 69 item 1), and comment lines are left out."""
    commands, current = [], ""
    for line in text.split("\n"):
        if not current and line.lstrip().startswith("#"):
            continue
        if line.endswith("\\"):
            current += line[:-1] + " "
            continue
        commands.append(current + line)
        current = ""
    if current:
        commands.append(current)
    return commands


def row(path="PRIVACY.md", old="Your location is never transmitted.",
        new="Only your own devices can see your location.", status="approved", rid="1-test", lang="en"):
    return pc.Row(rid, status, path, lang, old, new)


class Rows(ScratchTree):
    def test_a_line_whose_old_text_is_still_there_fails_by_its_row(self):
        self.write("PRIVACY.md", "Your location is never transmitted.\n"
                                 "Only your own devices can see your location.\n")
        problems, _ = pc.check_rows(self.root, [row()], allow_awaiting=False)
        self.assertEqual(len(problems), 1)
        self.assertIn("1-test", problems[0])
        self.assertIn("old text is still there", problems[0])

    def test_a_line_whose_new_text_is_missing_fails_by_its_row(self):
        self.write("PRIVACY.md", "Something else entirely.\n")
        problems, _ = pc.check_rows(self.root, [row()], allow_awaiting=False)
        self.assertEqual(len(problems), 1)
        self.assertIn("new text is missing", problems[0])

    def test_a_missing_file_fails(self):
        problems, _ = pc.check_rows(self.root, [row(path="SECURITY.md")], allow_awaiting=False)
        self.assertEqual(len(problems), 1)
        self.assertIn("SECURITY.md", problems[0])

    def test_an_old_text_inside_its_own_new_text_is_not_counted(self):
        # 4k: the new list line begins with the old one.
        line = row(old="- No third-party SDK that talks to the internet.",
                   new="- No third-party SDK that talks to the internet. The Android app's one library.")
        self.write("PRIVACY.md", "- No third-party SDK that talks to the internet. The Android\n"
                                 "  app's one library.\n")
        problems, _ = pc.check_rows(self.root, [line], allow_awaiting=False)
        self.assertEqual(problems, [])
        # ...but a second, bare copy of the old line still fails.
        self.write("PRIVACY.md", "- No third-party SDK that talks to the internet. The Android\n"
                                 "  app's one library.\n- No third-party SDK that talks to the internet.\n")
        problems, _ = pc.check_rows(self.root, [line], allow_awaiting=False)
        self.assertEqual(len(problems), 1)

    def test_line_breaks_inside_a_wrapped_paragraph_do_not_matter(self):
        self.write("PRIVACY.md", "The app uses it on the device. Only your own\ndevices can see your location.\n")
        problems, _ = pc.check_rows(self.root, [row()], allow_awaiting=False)
        self.assertEqual(problems, [])

    def test_an_awaiting_line_fails_the_gate_and_only_warns_when_allowed(self):
        self.write("PRIVACY.md", "Only your own devices can see your location.\n")
        waiting = row(status="awaiting")
        problems, warnings = pc.check_rows(self.root, [waiting], allow_awaiting=False)
        self.assertEqual(len(problems), 1)
        self.assertIn("awaiting", problems[0])
        problems, warnings = pc.check_rows(self.root, [waiting], allow_awaiting=True)
        self.assertEqual(problems, [])
        self.assertEqual(len(warnings), 1)
        self.assertIn("1-test", warnings[0])

    def test_an_awaiting_line_that_is_not_applied_fails_even_when_allowed(self):
        self.write("PRIVACY.md", "Your location is never transmitted.\n")
        problems, _ = pc.check_rows(self.root, [row(status="awaiting")], allow_awaiting=True)
        self.assertEqual(len(problems), 2)


class Dates(ScratchTree):
    def test_each_language_writes_its_date_its_own_way(self):
        cases = {
            "en": ("_Last updated 30 October 2026. Applies to Taqwa._", datetime.date(2026, 10, 30)),
            "ar": ("_آخر تحديث: 30 أكتوبر 2026. تنطبق على تقوى._", datetime.date(2026, 10, 30)),
            "bn": ("_সর্বশেষ হালনাগাদ ৩০ অক্টোবর ২০২৬। প্রযোজ্য।_", datetime.date(2026, 10, 30)),
            "fr": ("_Dernière mise à jour le 30 octobre 2026. S’applique._", datetime.date(2026, 10, 30)),
            "id": ("_Terakhir diperbarui 30 Oktober 2026. Berlaku._", datetime.date(2026, 10, 30)),
            "tr": ("_Son güncelleme 30 Ekim 2026. Geçerlidir._", datetime.date(2026, 10, 30)),
            "ur": ("_آخری تازہ کاری: 30 اکتوبر 2026۔ لاگو۔_", datetime.date(2026, 10, 30)),
        }
        for lang, (line, expected) in cases.items():
            self.assertEqual(pc.policy_date(line, lang), expected, lang)

    def test_a_policy_dated_before_the_watch_branch_fails(self):
        self.write("PRIVACY.md", "# Policy\n\n_Last updated 29 September 2026. Applies to Taqwa._\n")
        problems = pc.check_dates(self.root, ["PRIVACY.md"])
        self.assertEqual(len(problems), 1)
        self.assertIn("2026-09-29", problems[0])

    def test_a_policy_dated_on_the_branch_day_passes(self):
        self.write("PRIVACY.fr.md", "# P\n\n_Dernière mise à jour le 19 octobre 2026. S’applique._\n")
        self.assertEqual(pc.check_dates(self.root, ["PRIVACY.fr.md"]), [])

    def test_a_date_it_cannot_read_fails(self):
        self.write("PRIVACY.md", "# Policy\n\n_Last updated recently._\n")
        problems = pc.check_dates(self.root, ["PRIVACY.md"])
        self.assertEqual(len(problems), 1)


class Sweep(ScratchTree):
    KNOWN = [("item 4: works offline", r"README\.md", r"works offline")]

    def test_a_promise_in_no_row_and_no_list_fails_by_its_line(self):
        self.write("README.md", "It works offline.\nYour data never leaves your phone.\n")
        hits = pc.sweep(self.root, pages=False)
        problems = pc.classify(self.root, hits, [], self.KNOWN)
        self.assertEqual(len(problems), 1)
        self.assertIn("README.md:2", problems[0])

    def test_a_line_of_only_a_rows_new_text_needs_no_entry(self):
        self.write("README.md", "The app itself makes no network\nrequest until you ask.\n")
        line = row(path="README.md", old=None, new="The app itself makes no network request until you ask.")
        hits = pc.sweep(self.root, pages=False)
        self.assertEqual(len(hits), 1)
        self.assertEqual(pc.classify(self.root, hits, [line], self.KNOWN), [])

    def test_a_promise_beside_a_rows_new_text_on_its_line_still_fails(self):
        # Ruling 69 item 1: a line holding a row's new text was that row's whole, so the home
        # page's "The one thing it uses the internet for is recitation", beside row 4g's new
        # sentence, passed unseen. Only the row's own words are the row's.
        self.write("README.md", "Your data never leaves your phone. The app itself makes no network\n"
                                "request until you ask.\n")
        line = row(path="README.md", old=None, new="The app itself makes no network request until you ask.")
        hits = pc.sweep(self.root, pages=False)
        problems = pc.classify(self.root, hits, [line], self.KNOWN)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("README.md:1", problems[0])
        self.assertIn("never leaves your phone", problems[0])
        self.assertNotIn("makes no network", problems[0])

    def test_what_is_beside_a_rows_new_text_may_be_a_line_the_report_keeps_whole(self):
        known = [("item 4: works offline", r"README\.md", r"^It works offline\. $")]
        self.write("README.md", "It works offline. The app itself makes no network request until you ask.\n")
        line = row(path="README.md", old=None, new="The app itself makes no network request until you ask.")
        hits = pc.sweep(self.root, pages=False)
        self.assertEqual(pc.classify(self.root, hits, [line], known), [])

    def test_a_kept_pattern_beside_a_rows_new_text_must_match_all_of_the_words_there(self):
        # Ruling 69 item 1, review round 1: beside a row's new text, KNOWN was matched anywhere in
        # the words there, so an entry not held whole ("works offline") excused a promise added
        # after it. A line without a row's text is still matched anywhere, as plan 2's sweep reads it.
        self.write("README.md", "It works offline.\n"
                                "It works offline. Your data never leaves your phone. The app itself makes no"
                                " network request until you ask.\n")
        line = row(path="README.md", old=None, new="The app itself makes no network request until you ask.")
        hits = pc.sweep(self.root, pages=False)
        self.assertEqual(len(hits), 2)
        problems = pc.classify(self.root, hits, [line], self.KNOWN)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("README.md:2", problems[0])
        self.assertIn("never leaves your phone", problems[0])

    def test_a_kept_pattern_inside_the_rows_own_words_does_not_excuse_a_promise_beside_them(self):
        self.write("README.md", "Your data never leaves your phone. The app itself makes no network request.\n")
        line = row(path="README.md", old=None, new="The app itself makes no network request.")
        known = [("a pattern of the row's own words", r"README\.md", r"makes no network")]
        hits = pc.sweep(self.root, pages=False)
        problems = pc.classify(self.root, hits, [line], known)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("never leaves your phone", problems[0])

    def test_the_sweep_reads_only_the_files_plan_2_names(self):
        self.write("PRIVACY.fr.md", "Rien ne quitte votre téléphone, never leaves your phone.\n")
        self.write("tools/notes.md", "never leaves your phone\n")
        self.write("shared/values-ar/strings.xml", "<s>never leaves your phone</s>\n")
        self.write("docs/superpowers/plan.md", "never leaves your phone\n")
        self.assertEqual(pc.sweep(self.root, pages=False), [])

    def test_pages_sweeps_only_the_public_pages(self):
        self.write("docs/store/listing/en.md", "Your data never leaves your phone.\n")
        self.write("SECURITY.md", "Your data never leaves your phone.\n")
        hits = pc.sweep(self.root, pages=True)
        self.assertEqual([h[0] for h in hits], ["SECURITY.md"])


class NoGit(unittest.TestCase):
    """A tree that is not a git work tree of its own (a git-archive copy, a source zip, or such a
    copy unpacked inside another repository) is walked, skipping what its .gitignore ignores, so
    that scripts/test.sh runs there as it does in a checkout."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.dir.name, "copy")
        os.makedirs(self.root)

    def tearDown(self):
        self.dir.cleanup()

    def write(self, path, text):
        full = os.path.join(self.root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(text)

    def test_a_tree_without_git_is_swept_without_its_ignored_and_build_files(self):
        self.write(".gitignore", "# Gradle\nbuild/\n_site/\n.superpowers/\nlocal.properties\n*.iml\n"
                                 "iosApp/WatchCore/.build/\n")
        self.write("README.md", "It works offline.\nYour data never leaves your phone.\n")
        self.write("androidApp/build/intermediates/AndroidManifest.xml", "<uses-permission network/>\n")
        self.write("_site/index.html", "<p>never leaves your phone</p>\n")
        self.write(".superpowers/sdd/progress.md", "never leaves your phone\n")
        self.write("iosApp/WatchCore/.build/notes.txt", "never leaves your phone\n")
        self.write("local.properties", "sdk.dir=/nowhere\n")
        self.write("shared/shared.iml", "<module network=\"x\"/>\n")
        hits = pc.sweep(self.root, pages=False)
        self.assertEqual([(h[0], h[1]) for h in hits], [("README.md", 1), ("README.md", 2)])

    def test_a_copy_unpacked_inside_another_repository_is_walked_not_listed_by_it(self):
        # The outer repository tracks nothing of the copy, so its ls-files would list nothing and
        # the sweep would pass on an empty list.
        subprocess.run(["git", "init", "-q", self.dir.name], check=True)
        self.write("SECURITY.md", "Your data never leaves your phone.\n")
        hits = pc.sweep(self.root, pages=True)
        self.assertEqual([h[0] for h in hits], ["SECURITY.md"])

    def test_a_negated_pattern_stops_the_walk_by_its_line(self):
        self.write(".gitignore", "# built\nbuild/\n!build/keep.md\n")
        with self.assertRaises(pc.UnsupportedIgnore) as raised:
            pc.tree_files(self.root)
        self.assertIn(".gitignore:3", str(raised.exception))
        self.assertIn("!build/keep.md", str(raised.exception))
        self.assertIn("negation", str(raised.exception))

    def test_a_double_star_pattern_stops_the_walk_by_its_line(self):
        self.write(".gitignore", "docs/**/draft.md\n")
        with self.assertRaises(pc.UnsupportedIgnore) as raised:
            pc.tree_files(self.root)
        self.assertIn(".gitignore:1", str(raised.exception))
        self.assertIn("**", str(raised.exception))

    def test_an_escaped_pattern_stops_the_walk_by_its_line(self):
        self.write(".gitignore", "\\#notes.md\n")
        with self.assertRaises(pc.UnsupportedIgnore) as raised:
            pc.tree_files(self.root)
        self.assertIn(".gitignore:1", str(raised.exception))

    def test_a_nested_gitignore_stops_the_walk_by_its_path(self):
        self.write(".gitignore", "build/\n")
        self.write("site/pages/.gitignore", "draft.html\n")
        with self.assertRaises(pc.UnsupportedIgnore) as raised:
            pc.tree_files(self.root)
        self.assertIn("site/pages/.gitignore", str(raised.exception))

    def test_a_nested_gitignore_where_the_sweep_never_reads_is_not_walked(self):
        # Taqwa's own: tools/timetables/official/.gitignore and its monitor/ one, under tools/.
        self.write(".gitignore", "build/\n")
        self.write("tools/timetables/official/.gitignore", "/archive\n")
        self.write("tools/timetables/official/monitor/.gitignore", "*\n!.gitignore\n")
        self.write("SECURITY.md", "Your data never leaves your phone.\n")
        hits = pc.sweep(self.root, pages=False)
        self.assertEqual([h[0] for h in hits], ["SECURITY.md"])

    def test_a_slash_pattern_ignores_what_git_ignores_and_nothing_more(self):
        # A pattern with a slash is anchored to the root, and its * never crosses a slash; git
        # itself is the judge, on the same files in a repository of their own.
        ignore = ("*.xcworkspace/xcuserdata/\ndocs/*.md\n/archive\ntools/cache/\nsite/a?.html\n"
                  "site/[xy].html\nbuild/\n*.iml\n")
        files = ["a.xcworkspace/xcuserdata/x.md", "ios/a.xcworkspace/xcuserdata/y.md",
                 "docs/a.md", "docs/sub/b.md", "archive/t.md", "data/archive/u.md",
                 "tools/cache/c.md", "src/tools/cache/d.md", "site/ab.html", "site/abc.html",
                 "site/x.html", "site/z.html", "lib/build/e.md", "m.iml", "deep/n.iml", "README.md"]
        self.write(".gitignore", ignore)
        for path in files:
            self.write(path, "text\n")
        oracle = os.path.join(self.dir.name, "oracle")
        subprocess.run(["git", "init", "-q", oracle], check=True)
        for path in [".gitignore"] + files:
            full = os.path.join(oracle, path)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w", encoding="utf-8") as f:
                f.write(ignore if path == ".gitignore" else "text\n")
        listed = subprocess.run(["git", "-C", oracle, "ls-files", "-o", "--exclude-standard"], check=True,
                                capture_output=True, text=True).stdout.split()
        self.assertEqual(sorted(pc.tree_files(self.root)), sorted(listed))
        self.assertIn("ios/a.xcworkspace/xcuserdata/y.md", listed)
        self.assertIn("docs/sub/b.md", listed)

    def test_the_gate_fails_by_name_on_a_pattern_it_cannot_read(self):
        self.write(".gitignore", "build/\n!build/keep.md\n")
        self.write("README.md", "Nothing here.\n")
        done = subprocess.run([sys.executable, os.path.join(TOOLS, "privacy-check.py"), "--pages",
                               "--allow-awaiting", "--root", self.root], capture_output=True, text=True)
        self.assertNotIn("Traceback", done.stderr)
        self.assertEqual(done.returncode, 1, done.stdout + done.stderr)
        self.assertIn("FAIL: .gitignore:2", done.stdout)

    def test_the_gate_runs_on_a_tree_without_git_and_names_what_it_finds(self):
        self.write("README.md", "Your data never leaves your phone.\n")
        done = subprocess.run([sys.executable, os.path.join(TOOLS, "privacy-check.py"), "--pages",
                               "--allow-awaiting", "--root", self.root], capture_output=True, text=True)
        self.assertNotIn("Traceback", done.stderr)
        self.assertEqual(done.returncode, 1, done.stdout + done.stderr)
        self.assertIn("sweep: README.md:1:", done.stdout)
        self.assertIn("PRIVACY.md: the file is missing", done.stdout)


class Table(unittest.TestCase):
    """The rows themselves: the gate is only as good as its list."""

    def test_every_translated_line_is_there_in_all_seven_languages(self):
        by_line = {}
        for r in pc.ROWS:
            by_line.setdefault(r.id, set()).add(r.lang)
        english_only = {rid for rid in by_line if rid.startswith(("3-4m-", "3-4n-", "1-readme"))}
        for rid, langs in by_line.items():
            if rid not in english_only:
                self.assertEqual(langs, LANGS, rid)

    def test_indonesian_goes_in_values_id_and_its_values_in_twin(self):
        for r in pc.ROWS:
            if "/values-id/" in r.file:
                twin = r._replace(file=r.file.replace("/values-id/", "/values-in/"))
                self.assertIn(twin, pc.ROWS, r)

    def test_every_row_is_ruled_or_awaiting_and_names_a_text(self):
        for r in pc.ROWS:
            self.assertIn(r.status, ("approved", "awaiting"), r.id)
            self.assertTrue(r.new.strip(), r.id)
            self.assertNotIn("..", r.file, r.id)

    def test_ruling_39s_sentence_is_the_english_location_line_word_for_word(self):
        for r in pc.ROWS:
            if r.id.startswith("1-") and r.lang == "en":
                self.assertIn("Only your own devices can see your location.", r.new, r.id)

    def test_the_readmes_watch_line_is_ruling_39s_promise_and_the_policys_list_approved_at_m3(self):
        # P5.10 review round 1: the README's watch part had a second location promise, with a
        # shorter list than the policy's, that no row held, so neither the gate nor M3 saw it.
        rows = [r for r in pc.ROWS if r.id == "3-4m-readme-watch"]
        self.assertEqual(len(rows), 1)
        r = rows[0]
        self.assertEqual((r.file, r.lang, r.status), ("README.md", "en", "approved"))
        self.assertTrue(r.new.startswith("**Only your own devices can see your location.**"), r.new)
        self.assertIn("(PRIVACY.md#your-watch)", r.new)
        with open(os.path.join(ROOT, "PRIVACY.md"), encoding="utf-8") as f:
            self.assertIn("\n## Your watch\n", f.read())

    def test_every_line_of_the_readmes_watch_part_naming_your_location_is_a_row(self):
        # The sweep's words do not catch a new way of saying it, so the watch part is read whole.
        with open(os.path.join(ROOT, "README.md"), encoding="utf-8") as f:
            readme = f.read()
        start = readme.index("## On your wrist\n")
        part = readme[start:readme.index("\n## ", start + 1)]
        rows = [pc.normalize(r.new) for r in pc.ROWS if r.file == "README.md"]
        for line in part.split("\n"):
            if re.search(r"location|coordinates", line, re.I):
                self.assertTrue(any(new in pc.normalize(line) for new in rows), line)


class M3(unittest.TestCase):
    """Mohamed's answer, 31 October 2026 (the ledger's "M3 GIVEN"): "M3 approved, with your two
    suggested tweaks (the opening and the backups sentence)." Every row is approved; the two
    sentences are his in English and carry the same meaning in the six other languages; and the
    suite runs the strict gate, which fails on any row still awaiting. The backups sentence names
    the ayah widget's pool, 365 since 19 November 2026 (a hundred when he approved it)."""

    OPENING = "If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below."
    DRAFTED_OPENING = "A watch you add Taqwa to may be reached over the internet too, encrypted, as below."
    CACHES = ("the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming"
              " prayer times and the 365 ayahs the daily-ayah widget rotates through, neither of which"
              " contains your location.")
    DROPPED = "and that backup is encrypted by Apple and never visible to us"

    def test_every_row_is_approved_since_m3(self):
        awaiting = [f"{r.id} [{r.lang}] {r.file}" for r in pc.ROWS if r.status != "approved"]
        self.assertEqual(awaiting, [])

    def test_the_policys_opening_ends_with_the_sentence_he_approved(self):
        rows = {r.lang: r for r in pc.ROWS if r.id == "3-4i-policy-opening"}
        self.assertEqual(set(rows), LANGS)
        self.assertTrue(rows["en"].new.endswith(" " + self.OPENING), rows["en"].new)
        for r in pc.ROWS:
            self.assertNotIn(self.DRAFTED_OPENING, r.new, r.id)

    def test_the_backups_sentence_is_his_and_the_clause_he_dropped_must_be_gone_in_every_policy(self):
        rows = {r.lang: r for r in pc.ROWS if r.id == "3-4d-policy-backups-caches"}
        self.assertEqual(set(rows), LANGS)
        for lang, r in rows.items():
            self.assertEqual(r.file, "PRIVACY.md" if lang == "en" else f"PRIVACY.{lang}.md", lang)
            self.assertTrue(r.old and r.old.strip(), lang)  # the dropped clause, in that language
            self.assertNotIn(pc.normalize(r.old), pc.normalize(r.new), lang)
        self.assertEqual((rows["en"].old, rows["en"].new), (self.DROPPED, self.CACHES))

    def test_scripts_test_sh_runs_the_strict_gate_since_m3(self):
        with open(os.path.join(ROOT, "scripts", "test.sh"), encoding="utf-8") as f:
            commands = script_commands(f.read())
        gate = [l for l in commands if "tools/privacy-check.py" in l]
        for line in gate:
            self.assertNotIn("--allow-awaiting", line)
        # main's scripts/test.sh runs neither the gate nor its tests until the watch release lands
        # there, so this holds wherever the suite runs the gate's tests.
        if any("tools/test_privacy_check.py" in l for l in commands):
            self.assertTrue(gate, "scripts/test.sh runs the gate's tests but not the gate")

    def test_a_flag_on_a_continued_line_is_read_with_its_command(self):
        # Ruling 69 item 1: read line by line, a backslash-continued --allow-awaiting passed.
        commands = script_commands("# the gate\nif ! privacy=$(python3 tools/privacy-check.py \\\n"
                                   "    --allow-awaiting 2>&1); then echo \"$privacy\"; exit 1; fi\n")
        gate = [c for c in commands if "tools/privacy-check.py" in c]
        self.assertEqual(len(gate), 1, commands)
        self.assertIn("--allow-awaiting", gate[0])


class Ruling69(ScratchTree):
    """Ruling 69 item 1 (31 October 2026): no page or note still says recitation is the one thing
    Taqwa uses the internet for. Each such line takes the approved rows' own words, and a row
    holds each leftover in every language it is in, so that a copy left behind fails the gate."""

    POLICY = {lang: "PRIVACY.md" if lang == "en" else f"PRIVACY.{lang}.md" for lang in LANGS}

    @staticmethod
    def approved_clause(lang):
        """The approved store bullet's opening words in a language (4l): "The internet is used
        for recitation", as Mohamed approved it at M3, and its six translations."""
        bullet = next(r.new for r in pc.ROWS if r.id == "3-4l-listing-internet-play" and r.lang == lang)
        return bullet.lstrip("• ").split(":")[0].strip()

    def test_the_home_pages_recitation_sentence_takes_the_approved_words_in_seven_languages(self):
        rows = {r.lang: r for r in pc.ROWS if r.id == "3-4g-home-lede-recitation"}
        self.assertEqual(set(rows), LANGS)
        for lang, r in rows.items():
            self.assertEqual((r.file, r.status), (f"site/pages/{lang}/home.html", "approved"), lang)
            self.assertTrue(r.old and r.old.strip(), lang)  # "the one thing", in that language
            self.assertTrue(r.new.startswith(self.approved_clause(lang)), (lang, r.new))
        self.assertEqual(rows["en"].old, "The one thing it uses the internet for is recitation")
        self.assertEqual(self.approved_clause("en"), "The internet is used for recitation")

    def test_the_drafted_opening_is_a_row_in_every_policy_with_his_sentence_as_its_new_text(self):
        drafted = {r.lang: r for r in pc.ROWS if r.id == "3-4i-policy-opening-drafted"}
        opening = {r.lang: r for r in pc.ROWS if r.id == "3-4i-policy-opening"}
        self.assertEqual(set(drafted), LANGS)
        for lang, r in drafted.items():
            self.assertEqual((r.file, r.status), (self.POLICY[lang], "approved"), lang)
            self.assertTrue(r.old and r.old.strip(), lang)  # the drafted sentence, in that language
            self.assertTrue(opening[lang].new.endswith(r.new), lang)
            self.assertNotIn(pc.normalize(r.old), pc.normalize(opening[lang].new), lang)
        self.assertEqual((drafted["en"].old, drafted["en"].new), (M3.DRAFTED_OPENING, M3.OPENING))

    def test_a_sentence_added_beside_the_review_notes_row_is_seen(self):
        # The words the report keeps beside a row are held whole (KNOWN's ^...$), so an addition
        # beside the row's new text fails, though the line still holds that text.
        with open(os.path.join(ROOT, "docs", "store", "listing", "en.md"), encoding="utf-8") as f:
            line = next(l for l in f.read().split("\n") if l.startswith("No account or sign-in."))
        self.write("docs/store/listing/en.md", line + "\n")
        self.assertEqual(pc.classify(self.root, pc.sweep(self.root, pages=False), pc.ROWS), [])
        added = line.replace(" Nothing is collected,", " Nothing goes to the internet. Nothing is collected,")
        self.assertNotEqual(added, line)
        self.write("docs/store/listing/en.md", added + "\n")
        problems = pc.classify(self.root, pc.sweep(self.root, pages=False), pc.ROWS)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("docs/store/listing/en.md:1", problems[0])

    def test_a_promise_added_after_a_kept_line_beside_a_row_is_seen(self):
        # Ruling 69 item 1, review round 1's two mutants: README's principle line (row
        # 3-4m-readme-principle) and the policy's location line (Ruling 39's row), each with a
        # line KNOWN keeps elsewhere in that file and a promise added beside the row's text.
        cases = [("README.md", "- **Zero recurring cost.**",
                  " No ads, no account, offline by design. Your location never leaves your phone."),
                 ("PRIVACY.md", "Your location is used on the device.",
                  " It sends the text nowhere itself; your location never leaves your phone.")]
        for path, start, added in cases:
            with self.subTest(path=path):
                with open(os.path.join(ROOT, path), encoding="utf-8") as f:
                    line = next(l for l in f.read().split("\n") if l.startswith(start))
                swept = lambda: [h for h in pc.sweep(self.root, pages=False) if h[0] == path]
                self.write(path, line + "\n")
                self.assertEqual(pc.classify(self.root, swept(), pc.ROWS), [])
                self.write(path, line + added + "\n")
                problems = pc.classify(self.root, swept(), pc.ROWS)
                self.assertEqual(len(problems), 1, problems)
                self.assertIn(f"{path}:1", problems[0])
                self.assertIn("never leaves your phone", problems[0])
                self.assertIn("beside a row's new text", problems[0])

    def test_the_app_review_notes_say_what_the_internet_is_used_for_in_the_approved_words(self):
        rows = [r for r in pc.ROWS if r.id == "3-4n-notes-file" and r.old == "The only network use is recitation"]
        self.assertEqual(len(rows), 1)
        self.assertTrue(rows[0].new.startswith(self.approved_clause("en") + ":"), rows[0].new)

    def test_a_drafted_opening_left_in_a_translated_policy_fails_by_its_row(self):
        # M3's code lens: the French drafted sentence appended to PRIVACY.fr.md passed the gate.
        rows = [r for r in pc.ROWS if r.id == "3-4i-policy-opening-drafted" and r.lang == "fr"]
        self.assertEqual(len(rows), 1)
        self.write("PRIVACY.fr.md", rows[0].new + "\n\n" + rows[0].old + "\n")
        problems, _ = pc.check_rows(self.root, rows, allow_awaiting=False)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("3-4i-policy-opening-drafted [fr]", problems[0])
        self.assertIn("old text is still there", problems[0])


class M8(ScratchTree):
    """Mohamed's reading of the Arabic (M8, 6 November 2026, point A6): the policy's Backups
    sentence names Advanced Data Protection as Apple's ar-sa page 108756 does, and the draft's name
    coming back anywhere in the policy fails by its row (Ruling 73 item 5)."""

    APPLE = "الحماية المتقدمة للبيانات"
    DRAFT = "حماية البيانات المتقدمة"

    def test_the_drafts_arabic_name_for_advanced_data_protection_coming_back_fails(self):
        rows = [r for r in pc.ROWS if r.id == "3-4d-policy-backups" and r.lang == "ar"]
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].old, self.DRAFT)
        self.assertIn(self.APPLE, rows[0].new)
        self.write("PRIVACY.ar.md", rows[0].new + "\n")
        self.assertEqual(pc.check_rows(self.root, rows, allow_awaiting=False)[0], [])
        self.write("PRIVACY.ar.md", rows[0].new + "\n\n" + rows[0].new.replace(self.APPLE, self.DRAFT) + "\n")
        problems, _ = pc.check_rows(self.root, rows, allow_awaiting=False)
        self.assertEqual(len(problems), 1, problems)
        self.assertIn("3-4d-policy-backups [ar]", problems[0])
        self.assertIn("old text is still there", problems[0])


if __name__ == "__main__":
    unittest.main()
