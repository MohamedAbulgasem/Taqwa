"""Tests of tools/i18n-check.py (plan 5 Task 8): the watch apps' own words are checked as the
phone's are, and every iPhone and Apple Watch .lproj set is held to English. Each way they can be
wrong makes the check fail, by name, on a scratch copy of the tree's string resources. Standard
library only; scripts/check-strings.sh runs them before the check itself, and so can anyone:

    python3 tools/test_i18n_check.py

The check finds its tree by its own path, so each case copies it into the scratch tree beside the
resources and runs that copy.
"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)

# Every string resource the check reads, relative to the tree's root.
RESOURCES = (
    "shared/src/commonMain/composeResources/values*/strings.xml",
    "androidApp/src/main/res/values*/strings.xml",
    "wearApp/src/main/res/values*/strings.xml",
    "watchface/src/main/res/values*/strings.xml",
    "iosApp/*/*.lproj/*.strings",
)
SIX = ("ar", "fr", "tr", "in", "ur", "bn")
FACE = "watchface/src/main/res"
FACE_STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="watch_face_name">{name}</string>
    <string name="style">{style}</string>
    <string name="style_arch">{arch}</string>
</resources>
"""


class Tree(unittest.TestCase):
    """A scratch copy of the tree's string resources and of the check; each case breaks one thing."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = self.dir.name
        for pattern in RESOURCES:
            for path in glob.glob(os.path.join(ROOT, pattern)):
                self.copy(os.path.relpath(path, ROOT))
        self.copy("tools/i18n-check.py")

    def tearDown(self):
        self.dir.cleanup()

    def copy(self, rel):
        target = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(ROOT, rel), target)

    def path(self, rel):
        return os.path.join(self.root, rel)

    def write(self, rel, text):
        os.makedirs(os.path.dirname(self.path(rel)), exist_ok=True)
        with open(self.path(rel), "w", encoding="utf-8") as f:
            f.write(text)

    def read(self, rel):
        with open(self.path(rel), encoding="utf-8") as f:
            return f.read()

    def replace(self, rel, old, new):
        text = self.read(rel)
        self.assertIn(old, text, f"the case's own text is not in {rel}")
        self.write(rel, text.replace(old, new, 1))

    def run_check(self):
        """(exit code, what it printed) of the scratch copy of the check, all languages."""
        done = subprocess.run([sys.executable, "-I", self.path("tools/i18n-check.py")],
                              capture_output=True, text=True, cwd=self.root)
        return done.returncode, done.stdout + done.stderr

    def assertFailsNaming(self, *lines):
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        for line in lines:
            self.assertIn(line, out)
        return out


class WatchApp(Tree):
    """The Galaxy watch app's own resources (wearApp/src/main/res) are an Android root: English's
    keys and places in values-ar, -fr, -tr, -in, -ur and -bn, and aapt's apostrophes."""

    def test_a_key_missing_in_one_language_is_named(self):
        self.replace("wearApp/src/main/res/values-tr/strings.xml", '<string name="face_add">', '<string name="face_add_gone">')
        self.assertFailsNaming("wearApp/src/main/res/values-tr/strings.xml: missing key face_add",
                               "wearApp/src/main/res/values-tr/strings.xml: extra key face_add_gone")

    def test_a_straight_apostrophe_is_named(self):
        self.replace("wearApp/src/main/res/values-fr/strings.xml", "Impossible d’ajouter", "Impossible d'ajouter")
        self.assertFailsNaming("wearApp/src/main/res/values-fr/strings.xml: face_failed: unescaped apostrophe")

    def test_indonesian_spelt_id_is_named(self):
        os.rename(self.path("wearApp/src/main/res/values-in"), self.path("wearApp/src/main/res/values-id"))
        self.assertFailsNaming("wearApp/src/main/res/values-in/strings.xml: missing",
                               "wearApp/src/main/res/values-id: Android resources spell Indonesian 'in'")

    def test_its_words_are_read_and_the_brand_and_western_digits_are_not_noted(self):
        code, out = self.run_check()
        notes = [line for line in out.splitlines() if line.startswith("  note: ")]
        # French says "Dates" too, which the check notes for its reader: so the root is read.
        self.assertIn("  note: wearApp/src/main/res/values-fr/strings.xml: complication_dates: same as English ('Dates')", notes)
        for key in ("watch_face_name", "complication_mark", "complication_mark_description",
                    "preview_prayer_time", "preview_day_number"):
            self.assertFalse([n for n in notes if f": {key}: " in n], f"{key} is the same in every language")


class Face(Tree):
    """The Taqwa face's own resources (watchface/src/main/res: its name, and the face editor's style,
    colour and slot names) are an Android root too. These cases give it a small set of their own."""

    def setUp(self):
        super().setUp()
        shutil.rmtree(self.path(FACE), ignore_errors=True)
        self.write(f"{FACE}/values/strings.xml", FACE_STRINGS.format(name="Taqwa", style="Style", arch="Arch"))
        for folder in SIX:
            self.write(f"{FACE}/values-{folder}/strings.xml",
                       FACE_STRINGS.format(name=f"Taqwa {folder}", style=f"Style {folder}", arch=f"Arch {folder}"))

    def test_every_language_is_read(self):
        code, out = self.run_check()
        for folder in SIX:
            self.assertIn(f"{FACE}/values-{folder}/strings.xml: 3 strings, 0 plurals checked", out)

    def test_a_missing_language_is_named(self):
        shutil.rmtree(self.path(f"{FACE}/values-bn"))
        self.assertFailsNaming(f"{FACE}/values-bn/strings.xml: missing")

    def test_a_key_missing_in_one_language_is_named(self):
        self.replace(f"{FACE}/values-ar/strings.xml", '    <string name="style_arch">Arch ar</string>\n', "")
        self.assertFailsNaming(f"{FACE}/values-ar/strings.xml: missing key style_arch")

    def test_a_straight_apostrophe_is_named(self):
        self.replace(f"{FACE}/values-fr/strings.xml", "Style fr", "Style d'fr")
        self.assertFailsNaming(f"{FACE}/values-fr/strings.xml: style: unescaped apostrophe")


class AppleSets(Tree):
    """Every .lproj set under iosApp (the iPhone app, its widgets, the Apple Watch app and its
    widgets, and any target added later) is held to its English: the seven folders, each with
    English's tables, and in each table English's keys and placeholders."""

    def test_a_key_missing_from_the_iphone_widgets_table_is_named(self):
        self.replace("iosApp/TaqwaWidget/fr.lproj/Localizable.strings", '"lock_widget_then"', '"lock_widget_later"')
        self.assertFailsNaming("iosApp/TaqwaWidget/fr.lproj/Localizable.strings: missing key lock_widget_then",
                               "iosApp/TaqwaWidget/fr.lproj/Localizable.strings: extra key lock_widget_later")

    def test_a_placeholder_lost_from_the_iphone_widgets_table_is_named(self):
        self.replace("iosApp/TaqwaWidget/ur.lproj/Localizable.strings", '"پھر %@"', '"پھر"')
        self.assertFailsNaming("iosApp/TaqwaWidget/ur.lproj/Localizable.strings: lock_widget_then: placeholders differ")

    def test_a_table_missing_from_one_language_of_the_iphone_app_is_named(self):
        os.remove(self.path("iosApp/iosApp/tr.lproj/InfoPlist.strings"))
        self.assertFailsNaming("iosApp/iosApp/tr.lproj/InfoPlist.strings: missing")

    def test_a_table_english_lacks_is_named(self):
        self.write("iosApp/TaqwaWidget/ar.lproj/InfoPlist.strings", '"CFBundleDisplayName" = "تقوى";\n')
        self.assertFailsNaming("iosApp/TaqwaWidget/ar.lproj/InfoPlist.strings: extra table (en.lproj has none)")

    def test_a_missing_folder_is_named(self):
        shutil.rmtree(self.path("iosApp/iosApp/bn.lproj"))
        self.assertFailsNaming("iosApp/iosApp/bn.lproj: missing")

    def test_an_eighth_folder_is_named(self):
        shutil.copytree(self.path("iosApp/TaqwaWidget/en.lproj"), self.path("iosApp/TaqwaWidget/de.lproj"))
        self.assertFailsNaming("iosApp/TaqwaWidget/de.lproj: not one of the seven languages")

    def test_a_new_target_is_checked_without_being_listed(self):
        for lang in ("en", "ar", "fr", "tr", "id", "ur", "bn"):
            self.write(f"iosApp/NewTarget/{lang}.lproj/Localizable.strings",
                       '"a" = "A";\n' + ('' if lang == "fr" else '"b" = "B %@";\n'))
        self.assertFailsNaming("iosApp/NewTarget/fr.lproj/Localizable.strings: missing key b")

    def test_a_known_set_with_no_folders_left_is_named(self):
        for lang in ("en", "ar", "fr", "tr", "id", "ur", "bn"):
            shutil.rmtree(self.path(f"iosApp/TaqwaWatch/{lang}.lproj"))
        self.assertFailsNaming("iosApp/TaqwaWatch: no .lproj folders")

    def test_the_watch_apps_name_table_missing_in_english_is_named(self):
        os.remove(self.path("iosApp/TaqwaWatchWidgets/en.lproj/InfoPlist.strings"))
        self.assertFailsNaming("iosApp/TaqwaWatchWidgets/en.lproj/InfoPlist.strings: missing")

    def test_an_apple_watch_notice_spelt_otherwise_than_the_galaxys_is_named(self):
        # M8 point A1: both watches spell the update line alike, so a Galaxy watch's line that
        # changes alone fails on both Apple Watch targets.
        self.replace("wearApp/src/main/res/values-fr/strings.xml",
                     '<string name="update_taqwa_on_watch">Mettez à jour Taqwa sur votre montre</string>',
                     '<string name="update_taqwa_on_watch">Mettez Taqwa à jour sur votre montre</string>')
        self.assertFailsNaming(
            "iosApp/TaqwaWatch/fr.lproj/Localizable.strings: update_taqwa_on_watch is 'Mettez à jour Taqwa sur votre montre', "
            "the Galaxy watch's is 'Mettez Taqwa à jour sur votre montre'",
            "iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings: update_taqwa_on_watch is")

    def test_the_arabic_widgets_name_is_the_phones_widget_label(self):
        # M8 point A4: in Arabic the Apple Watch widget says the phone's widget label, as the Galaxy
        # watch does; the other languages keep the phone's prayer row word.
        self.replace("iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings",
                     '"next_prayer_widget_name" = "الصلاة القادمة";', '"next_prayer_widget_name" = "الصلاة التالية";')
        self.replace("iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings",
                     '"next_prayer_widget_name" = "Prochaine prière";', '"next_prayer_widget_name" = "Prière suivante";')
        self.assertFailsNaming(
            "iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings: next_prayer_widget_name is 'الصلاة التالية', "
            "the phone's widget_small_label is 'الصلاة القادمة'",
            "iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings: next_prayer_widget_name is 'Prière suivante', "
            "the phone's prayer_a11y_prayer_next is 'Prochaine prière'")

    def test_an_empty_value_is_named(self):
        rel = "iosApp/TaqwaWidget/bn.lproj/Localizable.strings"
        text = self.read(rel)
        start = text.index('"home_widget_name" = "') + len('"home_widget_name" = "')
        self.write(rel, text[:start] + text[text.index('";', start):])
        self.assertFailsNaming(f"{rel}: home_widget_name: empty")


if __name__ == "__main__":
    unittest.main()
