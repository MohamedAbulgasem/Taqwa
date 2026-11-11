"""Tests of tools/store-listing-check.py (plan 5 Task 10): each way the store copy can be wrong
for the watch release makes it fail, by name, on a scratch tree. Standard library only;
scripts/test.sh runs them before the check itself, and so can anyone:

    python3 tools/test_store_listing_check.py

Most cases build a small tree of their own (one listing, one notes file, the release notes and
the phone's version lines). The last ones copy the real store copy and break it, so they hold the
listings as written to the rule.
"""
import importlib.util
import io
import os
import shutil
import tempfile
import unittest
from contextlib import redirect_stdout

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
spec = importlib.util.spec_from_file_location("store_listing_check", os.path.join(TOOLS, "store-listing-check.py"))
slc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(slc)

TAGS = ["en-GB", "ar", "fr-FR", "tr-TR", "id", "ur", "bn-BD"]

LISTING = """# Store listing

## Google Play

### Title (30)
Taqwa: Prayer, Quran & Qibla

### Full description (4000)
Prayer times where you are.

On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer.

## App Store

### Name (30)
Taqwa: Prayer, Quran & Qibla

### Description (4000)
Prayer times where you are.

On your wrist: on Apple Watch, Taqwa shows the next prayer.

### What's new (4000)
What's new in 1.4.0:
• Taqwa on your Apple Watch

## Review notes (App Store and Play, English only)
No account or sign-in.

App Store Connect's Notes field carries the longer answers: `docs/store/app-review/2026-10-30-notes-1.6.0-43.txt` (the earlier files stay as the record).
"""


def notes(text_per_tag):
    return "".join(f"<{tag}>\n{text_per_tag(tag)}\n</{tag}>\n" for tag in TAGS)


LOCALIZATIONS = ["English (UK)", "Arabic", "French", "Turkish", "Indonesian", "Urdu", "Bengali"]


def appstore_notes(text_per_name):
    """An App Store "What's New" set as main writes them since 1.4.0 (40): a line saying what it is,
    then one `=== <localization> ===` block each."""
    return "App Store \"What's New\" for 1.4.0 (40).\n\n" + "\n".join(
        f"=== {name} ===\n{text_per_name(name)}\n" for name in LOCALIZATIONS)


class Tree(unittest.TestCase):
    """A throwaway tree holding a clean listing, its notes file, the newest code's two
    release-notes files and the phone's version lines; each case breaks one thing."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = self.dir.name
        self.write("docs/store/listing/en.md", LISTING)
        self.write("docs/store/app-review/2026-10-30-notes-1.6.0-43.txt", "1. Screen recording\nNone needed.\n")
        self.write("docs/store/release-notes/1.3.0-39.txt", notes(lambda tag: "• An older build"))
        self.write("docs/store/release-notes/1.4.0-40.txt",
                   notes(lambda tag: "• Taqwa on your watch, on Wear OS watches such as Galaxy Watch"))
        self.write("docs/store/release-notes/1.4.0-100040-wear.txt",
                   notes(lambda tag: "The Taqwa face in four styles. Needs Taqwa on your phone."))
        self.write("androidApp/build.gradle.kts",
                   "android {\n    defaultConfig {\n        versionCode = 39\n        versionName = \"1.3.0\"\n    }\n}\n")

    def tearDown(self):
        self.dir.cleanup()

    def write(self, path, text):
        full = os.path.join(self.root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(text)

    def read(self, path):
        with open(os.path.join(self.root, path), encoding="utf-8") as f:
            return f.read()

    def replace(self, path, old, new):
        text = self.read(path)
        self.assertIn(old, text, f"the case's own text is not in {path}")
        self.write(path, text.replace(old, new, 1))

    def run_check(self, *langs):
        """(exit code, what it printed) of the whole check on this tree."""
        out = io.StringIO()
        with redirect_stdout(out):
            code = slc.main(list(langs), root=self.root)
        return code, out.getvalue()

    def assertFailsNaming(self, *words):
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        for word in words:
            self.assertIn(word, out)
        return out


class Clean(Tree):
    def test_the_tree_as_built_is_clean(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)
        self.assertIn("listing: clean", out)


class Platforms(Tree):
    """Each store names only its own watch (App Store Review Guideline 2.3.10 for Apple; on Play an
    Android phone cannot use an Apple Watch), and only its own company's relay."""

    def test_an_app_store_description_naming_galaxy_watch_fails(self):
        self.replace("docs/store/listing/en.md", "on Apple Watch, Taqwa", "on Apple Watch and Galaxy Watch, Taqwa")
        self.assertFailsNaming("en: App Store: Description: names Galaxy")

    def test_a_play_description_naming_apple_watch_fails(self):
        self.replace("docs/store/listing/en.md", "such as Galaxy Watch, Taqwa", "such as Galaxy Watch or Apple Watch, Taqwa")
        self.assertFailsNaming("en: Google Play: Full description: names Apple Watch")

    def test_every_other_platform_name_fails_in_the_app_store_fields(self):
        for name in ("Android", "Wear OS", "Google Play", "Play Store", "Google"):
            with self.subTest(name=name):
                self.tearDown()
                self.setUp()
                self.replace("docs/store/listing/en.md", "• Taqwa on your Apple Watch", f"• Taqwa on {name} too")
                self.assertFailsNaming(f"en: App Store: What's new: names {name}")

    def test_google_alone_fails_in_the_app_store_description(self):
        # R63a's forward note: the relay is Apple's there, so "Google or Apple" is a finding.
        self.replace("docs/store/listing/en.md", "Prayer times where you are.\n\nOn your wrist: on Apple",
                     "Google or Apple may carry its data.\n\nOn your wrist: on Apple")
        self.assertFailsNaming("en: App Store: Description: names Google")

    def test_apple_alone_fails_in_the_play_description(self):
        self.replace("docs/store/listing/en.md", "Prayer times where you are.\n\nOn your wrist: on Wear",
                     "Google or Apple may carry its data.\n\nOn your wrist: on Wear")
        self.assertFailsNaming("en: Google Play: Full description: names Apple")

    def test_watchos_and_the_digital_crown_fail_on_play(self):
        for name in ("watchOS", "Digital Crown"):
            with self.subTest(name=name):
                self.tearDown()
                self.setUp()
                self.replace("docs/store/listing/en.md", "Taqwa shows the next prayer.\n\n## App Store",
                             f"Taqwa shows the next prayer, with {name}.\n\n## App Store")
                self.assertFailsNaming(f"en: Google Play: Full description: names {name}")

    def test_a_note_in_parentheses_is_not_checked(self):
        # The notes are for whoever pastes the copy, never pasted themselves.
        self.replace("docs/store/listing/en.md", "### Description (4000)\n",
                     "### Description (4000)\n(The Play text above, with Apple in place of Google and Wear OS.)\n")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_the_review_notes_and_the_screenshot_captions_name_no_store(self):
        # The shared review notes are English text for both reviewers and name the user's own
        # watch, never a platform's; the rule reads only the two stores' own fields.
        self.replace("docs/store/listing/en.md", "No account or sign-in.", "No account or sign-in, on Android or iPhone.")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_a_name_in_another_case_fails_too(self):
        # P5.10 review round 1 (the code lens): 'android', 'WEAR OS' and 'watchos' passed the
        # case-sensitive rule.
        for written, store, name in (("android", "App Store", "Android"), ("WEAR OS", "App Store", "Wear OS"),
                                     ("watchos", "Google Play", "watchOS")):
            with self.subTest(written=written):
                self.tearDown()
                self.setUp()
                if store == "App Store":
                    self.replace("docs/store/listing/en.md", "• Taqwa on your Apple Watch", f"• Taqwa on your Apple Watch, not {written}")
                    self.assertFailsNaming(f"en: App Store: What's new: names {name}")
                else:
                    self.replace("docs/store/listing/en.md", "Taqwa shows the next prayer.\n\n## App Store",
                                 f"Taqwa shows the next prayer, not on {written}.\n\n## App Store")
                    self.assertFailsNaming(f"en: Google Play: Full description: names {name}")

    def test_samsung_fails_in_the_app_store_fields(self):
        # The same review: the App Store list had Galaxy but not the company.
        self.replace("docs/store/listing/en.md", "• Taqwa on your Apple Watch", "• Taqwa on Samsung's watches too")
        self.assertFailsNaming("en: App Store: What's new: names Samsung")

    def test_a_platform_name_inside_another_word_is_not_a_name(self):
        self.replace("docs/store/listing/en.md", "• Taqwa on your Apple Watch", "• Taqwa on your Apple Watch, Googleplex aside")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_the_app_store_connect_notes_name_no_other_platform(self):
        self.write("docs/store/app-review/2026-10-30-notes-1.6.0-43.txt", "Works like the Android app.\n")
        self.assertFailsNaming("2026-10-30-notes-1.6.0-43.txt: names Android")

    def test_the_newest_phone_notes_name_no_apple_watch(self):
        self.write("docs/store/release-notes/1.4.0-40.txt", notes(lambda tag: "• Taqwa on your Apple Watch"))
        self.assertFailsNaming("1.4.0-40.txt: names Apple Watch")

    def test_the_newest_wear_notes_name_no_watchos(self):
        self.write("docs/store/release-notes/1.4.0-100040-wear.txt", notes(lambda tag: "Also on watchOS."))
        self.assertFailsNaming("1.4.0-100040-wear.txt: names watchOS")

    def test_an_older_builds_notes_are_a_record_and_not_held_to_the_rule(self):
        self.write("docs/store/release-notes/1.3.0-39.txt", notes(lambda tag: "• iPhone and Apple Watch fixes"))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)


# The Arabic copy writes a conjunction or a preposition onto a Latin name (ar.md's "وiPhone", the
# home card's "وSmart Stack"), and Bengali an ending ("Watchএ"). Python counts those letters as
# word characters, so the names are bounded by Latin letters alone (P5.10 review round 1).
LISTING_AR = (LISTING
              .replace("On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer.",
                       "على معصمك: على ساعات Wear OS مثل Galaxy Watch، يعرض تقوى الصلاة التالية.")
              .replace("On your wrist: on Apple Watch, Taqwa shows the next prayer.",
                       "على معصمك: على Apple Watch، يعرض تقوى الصلاة التالية.")
              .replace("• Taqwa on your Apple Watch", "• تقوى على Apple Watch"))


class AttachedLetters(Tree):
    """A letter of another script written onto a platform's name does not hide it."""

    def setUp(self):
        super().setUp()
        self.write("docs/store/listing/ar.md", LISTING_AR)

    def test_the_arabic_tree_as_built_is_clean(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_an_arabic_app_store_description_naming_galaxy_watch_after_wa_fails(self):
        self.replace("docs/store/listing/ar.md", "على Apple Watch، يعرض", "على Apple Watch وGalaxy Watch، يعرض")
        self.assertFailsNaming("ar: App Store: Description: names Galaxy")

    def test_an_arabic_play_description_naming_apple_watch_after_wa_fails(self):
        self.replace("docs/store/listing/ar.md", "مثل Galaxy Watch، يعرض", "مثل Galaxy Watch وApple Watch، يعرض")
        self.assertFailsNaming("ar: Google Play: Full description: names Apple Watch")

    def test_every_attached_form_fails_in_the_app_store_description(self):
        for written, name in (("وGalaxy Watch", "Galaxy"), ("بـGoogle", "Google"), ("لـAndroid", "Android"),
                              ("اورWear OS", "Wear OS"), ("وPlay Store", "Play Store"), ("Wear OSএ", "Wear OS")):
            with self.subTest(written=written):
                self.tearDown()
                self.setUp()
                self.replace("docs/store/listing/ar.md", "على Apple Watch، يعرض", f"على Apple Watch {written}، يعرض")
                self.assertFailsNaming(f"ar: App Store: Description: names {name}")

    def test_every_attached_form_fails_in_the_play_description(self):
        for written, name in (("وApple Watch", "Apple Watch"), ("بـApple", "Apple"), ("وwatchOS", "watchOS"),
                              ("لـDigital Crown", "Digital Crown"), ("Apple Watchএ", "Apple Watch")):
            with self.subTest(written=written):
                self.tearDown()
                self.setUp()
                self.replace("docs/store/listing/ar.md", "مثل Galaxy Watch، يعرض", f"مثل Galaxy Watch {written}، يعرض")
                self.assertFailsNaming(f"ar: Google Play: Full description: names {name}")

    def test_a_name_inside_a_longer_latin_word_is_still_no_name(self):
        self.replace("docs/store/listing/ar.md", "مثل Galaxy Watch، يعرض", "مثل Galaxy Watch وPineapples، يعرض")
        self.replace("docs/store/listing/ar.md", "على Apple Watch، يعرض", "على Apple Watch وGoogleplex، يعرض")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_names_in_reads_the_attached_forms(self):
        self.assertEqual(slc.names_in("على Apple Watch وGalaxy Watch", "App Store"), ["Galaxy"])
        self.assertEqual(slc.names_in("بـGoogle و لـAndroid", "App Store"), ["Android", "Google"])
        self.assertIn("Apple Watch", slc.names_in("على Galaxy Watch وApple Watch", "Google Play"))
        self.assertEqual(slc.names_in("Googleplex, Androids, Pineapple", "App Store"), [])


class Empty(Tree):
    def test_an_app_store_description_that_is_only_a_note_fails_as_empty(self):
        self.replace("docs/store/listing/en.md",
                     "### Description (4000)\nPrayer times where you are.\n\nOn your wrist: on Apple Watch, Taqwa shows the next prayer.\n",
                     "### Description (4000)\n(The same text as the Play full description above.)\n")
        self.assertFailsNaming("en: App Store: Description: empty")


class ReleaseNotes(Tree):
    def test_a_language_over_500_characters_fails_by_its_file_and_tag(self):
        self.write("docs/store/release-notes/1.4.0-40.txt",
                   notes(lambda tag: "• " + ("x" * 499 if tag == "fr-FR" else "Taqwa on your watch")))
        self.assertFailsNaming("1.4.0-40.txt: fr-FR  501/500")

    def test_500_characters_is_allowed(self):
        self.write("docs/store/release-notes/1.4.0-40.txt", notes(lambda tag: "• " + "x" * 498))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_an_older_file_over_500_fails_too(self):
        self.write("docs/store/release-notes/1.3.0-39.txt", notes(lambda tag: "x" * 501))
        self.assertFailsNaming("1.3.0-39.txt: en-GB  501/500")

    def test_a_file_missing_a_language_fails(self):
        text = self.read("docs/store/release-notes/1.4.0-40.txt")
        start, end = text.index("<ur>"), text.index("</ur>\n") + len("</ur>\n")
        self.write("docs/store/release-notes/1.4.0-40.txt", text[:start] + text[end:])
        self.assertFailsNaming("1.4.0-40.txt: no <ur>")

    def test_the_newest_codes_wear_notes_must_be_there(self):
        os.remove(os.path.join(self.root, "docs/store/release-notes/1.4.0-100040-wear.txt"))
        self.assertFailsNaming("1.4.0-100040-wear.txt")

    def test_the_wear_notes_must_carry_the_phones_version(self):
        os.rename(os.path.join(self.root, "docs/store/release-notes/1.4.0-100040-wear.txt"),
                  os.path.join(self.root, "docs/store/release-notes/1.3.0-100040-wear.txt"))
        self.assertFailsNaming("1.4.0-100040-wear.txt")

    def test_a_phone_code_without_its_notes_fails(self):
        # The cut gate: once bump-version.sh sets a code, that code's two files must exist.
        self.write("androidApp/build.gradle.kts",
                   "android {\n    defaultConfig {\n        versionCode = 41\n        versionName = \"1.4.0\"\n    }\n}\n")
        self.assertFailsNaming("41")

    def test_the_bumped_code_with_its_notes_is_clean(self):
        self.write("androidApp/build.gradle.kts",
                   "android {\n    defaultConfig {\n        versionCode = 40\n        versionName = \"1.4.0\"\n    }\n}\n")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_the_bumped_code_must_carry_its_version_in_the_notes_name(self):
        self.write("androidApp/build.gradle.kts",
                   "android {\n    defaultConfig {\n        versionCode = 40\n        versionName = \"1.5.0\"\n    }\n}\n")
        self.assertFailsNaming("1.5.0")

    def test_a_production_copy_of_the_notes_is_not_the_newest_code(self):
        self.write("docs/store/release-notes/1.4.0-41-production.txt", notes(lambda tag: "• Taqwa"))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)


class AppStoreWhatsNew(Tree):
    """main's separate App Store sets (<V>-<C>-appstore.txt, since 1.4.0 (40)): the App Store's
    localizations in `=== ... ===` blocks, not Play's tags, so they are held to the App Store's
    rules (each localization there, at most 4,000 characters, the App Store's names only)."""

    def setUp(self):
        super().setUp()
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "• Taqwa on your Apple Watch"))

    def test_an_app_store_set_is_not_held_to_plays_tags(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)
        self.assertNotIn("1.4.0-40-appstore.txt: no <", out)

    def test_an_app_store_set_missing_a_localization_fails(self):
        text = self.read("docs/store/release-notes/1.4.0-40-appstore.txt")
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt", text.replace("=== Urdu ===", "=== Urdo ==="))
        self.assertFailsNaming("1.4.0-40-appstore.txt: no === Urdu ===")

    def test_an_app_store_set_naming_android_fails(self):
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "• Works like the Android app"))
        self.assertFailsNaming("1.4.0-40-appstore.txt: names Android")

    def test_an_app_store_localization_over_4000_characters_fails(self):
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "x" * (4001 if name == "French" else 10)))
        self.assertFailsNaming("1.4.0-40-appstore.txt: French  4001/4000")

    def test_a_note_in_parentheses_in_a_block_is_not_counted(self):
        # Ruling 71 item 4b: until M8 each non-English block carried its draft marker as the
        # listings did, a note line in parentheses, which is not pasted into the field and is not
        # its text. (The note here holds no marker, so the cut gate's grep finds none in this file.)
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "x" * 4000 + "\n(A note for the reader, which is not pasted.)"))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_a_block_holding_only_a_note_fails_as_empty(self):
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "(A note for the reader, which is not pasted.)" if name == "Urdu" else "• Taqwa"))
        self.assertFailsNaming("1.4.0-40-appstore.txt: Urdu is empty")

    def test_an_app_store_set_is_not_the_newest_phone_code(self):
        self.write("docs/store/release-notes/1.5.0-41-appstore.txt",
                   appstore_notes(lambda name: "• Later"))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)


class AppleWords(Tree):
    """What the App Store shows names Apple's features as Apple's own guide does in that language
    (plan 5 Task 11, review round 1): Turkish "saat kadranı", not the Wear word "saat yüzü";
    Indonesian "wajah jam", not "tampilan jam"; Smart Stack as Apple names it."""

    def setUp(self):
        super().setUp()
        self.write("docs/store/listing/tr.md", LISTING.replace(
            "On your wrist: on Apple Watch, Taqwa shows the next prayer.",
            "Bileğinizde: Apple Watch’ta Taqwa, sonraki namazı saat kadranınızın komplikasyonlarında gösterir."))
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "• Taqwa on your Apple Watch"))

    def test_the_tree_with_apples_words_is_clean(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_a_turkish_app_store_description_with_the_wear_face_word_fails(self):
        self.replace("docs/store/listing/tr.md", "saat kadranınızın", "saat yüzünüzün")
        self.assertFailsNaming("tr: App Store: Description: 'saat yüz' is not Apple's word (Apple's Turkish: 'saat kadranı')")

    def test_the_wear_face_word_on_play_is_not_held_to_apples(self):
        self.replace("docs/store/listing/tr.md", "On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer.",
                     "Bileğinizde: Galaxy Watch gibi Wear OS saatlerde Taqwa, sonraki namazı kendi saat yüzünde gösterir.")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_a_word_apple_does_not_use_fails_in_any_case(self):
        self.replace("docs/store/listing/tr.md", "saat kadranınızın", "Saat Yüzünüzün")
        self.assertFailsNaming("tr: App Store: Description: 'saat yüz' is not Apple's word")

    def test_an_app_store_set_with_a_word_apple_does_not_use_fails_by_its_localization(self):
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "• Taqwa di Tumpukan Pintar" if name == "Indonesian" else "• Taqwa"))
        self.assertFailsNaming("1.4.0-40-appstore.txt: Indonesian: 'Tumpukan Pintar' is not Apple's word (Apple's Indonesian: 'Smart Stack')")

    def test_a_turkish_word_apple_does_not_use_fails_in_turkish_capitals(self):
        # Turkish capitals dotless ı as I and dotted i as İ, which Python's casefold does not
        # (review round 2's minor: 'AKILLI YIĞIN' and 'GELİŞMİŞ VERİ KORUMA' passed).
        for capitals, wrong in [("AKILLI YIĞIN", "Akıllı Yığın"), ("GELİŞMİŞ VERİ KORUMA", "Gelişmiş Veri Koruma")]:
            with self.subTest(capitals=capitals):
                self.write("docs/store/listing/tr.md", LISTING.replace(
                    "On your wrist: on Apple Watch, Taqwa shows the next prayer.", f"Apple Watch: {capitals}."))
                self.assertFailsNaming(f"tr: App Store: Description: '{wrong}' is not Apple's word")

    def test_every_word_apple_does_not_use_fails_in_its_language(self):
        for lang, words in slc.APPLE_WORDS.items():
            for wrong, apple in words:
                with self.subTest(lang=lang, wrong=wrong):
                    self.write(f"docs/store/listing/{lang}.md", LISTING.replace(
                        "On your wrist: on Apple Watch, Taqwa shows the next prayer.", f"Apple Watch: {wrong}."))
                    out = self.assertFailsNaming(f"{lang}: App Store: Description: '{wrong}' is not Apple's word")
                    self.assertIn(f"'{apple}'", out)
                    self.write(f"docs/store/listing/{lang}.md", LISTING)


class ChosenWords(Tree):
    """The words Mohamed chose at M8 hold in every store's text and in the newest notes: Arabic
    إضافات for complications (point A3), never مضاعفات, about either watch."""

    def setUp(self):
        super().setUp()
        self.write("docs/store/listing/ar.md", LISTING_AR
                   .replace("يعرض تقوى الصلاة التالية.", "يعرض تقوى الصلاة التالية في إضافات لأي واجهة.")
                   .replace("على Apple Watch، يعرض تقوى الصلاة التالية في إضافات لأي واجهة.",
                            "على Apple Watch، يعرض تقوى الصلاة التالية في إضافات واجهة ساعتك."))

    def test_the_arabic_tree_with_his_word_is_clean(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_the_other_word_fails_on_play_and_on_the_app_store(self):
        self.write("docs/store/listing/ar.md", self.read("docs/store/listing/ar.md").replace("إضافات", "مضاعفات"))
        self.assertFailsNaming(
            "ar: Google Play: Full description: 'مضاعف' is not the word Mohamed chose (M8: 'إضافات')",
            "ar: App Store: Description: 'مضاعف' is not the word Mohamed chose (M8: 'إضافات')")

    def test_the_other_word_fails_in_the_newest_play_notes_and_an_app_store_set(self):
        self.write("docs/store/release-notes/1.4.0-100040-wear.txt",
                   notes(lambda tag: "واجهة تقوى، ومضاعفات لأي واجهة." if tag == "ar" else "The Taqwa face."))
        self.write("docs/store/release-notes/1.4.0-40-appstore.txt",
                   appstore_notes(lambda name: "• الصلاة القادمة في مضاعفات ساعتك" if name == "Arabic" else "• Taqwa"))
        self.assertFailsNaming("release-notes: 1.4.0-100040-wear.txt: ar: 'مضاعف' is not the word Mohamed chose",
                               "release-notes: 1.4.0-40-appstore.txt: Arabic: 'مضاعف' is not the word Mohamed chose")

    def test_an_older_builds_notes_are_a_record(self):
        self.write("docs/store/release-notes/1.3.0-39.txt", notes(lambda tag: "مضاعفات" if tag == "ar" else "• An older build"))
        code, out = self.run_check()
        self.assertEqual(code, 0, out)


class AppStoreConnectNotes(Tree):
    def test_the_notes_file_en_md_points_to_is_the_one_checked(self):
        self.write("docs/store/app-review/2026-10-30-notes-1.6.0-43.txt", "é" * 2001)
        self.assertFailsNaming("2026-10-30-notes-1.6.0-43.txt", "4002/4000")

    def test_a_newer_named_file_en_md_does_not_point_to_is_not_checked(self):
        # A reply or a draft sorted after the pointed file is not what goes into the Notes field.
        self.write("docs/store/app-review/2026-11-01-notes-9.9.9-99.txt", "x" * 4001)
        code, out = self.run_check()
        self.assertEqual(code, 0, out)
        self.assertIn("2026-10-30-notes-1.6.0-43.txt", out)

    def test_a_pointer_to_no_file_fails(self):
        os.remove(os.path.join(self.root, "docs/store/app-review/2026-10-30-notes-1.6.0-43.txt"))
        self.assertFailsNaming("2026-10-30-notes-1.6.0-43.txt")

    def test_an_en_md_with_no_pointer_fails(self):
        self.replace("docs/store/listing/en.md", "`docs/store/app-review/2026-10-30-notes-1.6.0-43.txt`", "the newest file")
        self.assertFailsNaming("en.md")


class TheRealListings(unittest.TestCase):
    """Scratch copies of the store copy as committed: clean as they are, and failing by name once
    one store's text names the other's watch."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = self.dir.name
        shutil.copytree(os.path.join(ROOT, "docs/store/listing"), os.path.join(self.root, "docs/store/listing"))
        shutil.copytree(os.path.join(ROOT, "docs/store/release-notes"), os.path.join(self.root, "docs/store/release-notes"))
        shutil.copytree(os.path.join(ROOT, "docs/store/app-review"), os.path.join(self.root, "docs/store/app-review"))
        os.makedirs(os.path.join(self.root, "androidApp"))
        shutil.copy(os.path.join(ROOT, "androidApp/build.gradle.kts"), os.path.join(self.root, "androidApp"))

    def tearDown(self):
        self.dir.cleanup()

    def run_check(self):
        out = io.StringIO()
        with redirect_stdout(out):
            code = slc.main([], root=self.root)
        return code, out.getvalue()

    def section(self, lang, store):
        path = os.path.join(self.root, f"docs/store/listing/{lang}.md")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        start = text.index(f"## {store}\n")
        end = text.index("\n## ", start + 1)
        return path, text, start, end

    def break_store(self, lang, store, name):
        """Writes name into the first line of that store's long description."""
        path, text, start, end = self.section(lang, store)
        field = "### Full description (4000)\n" if store == "Google Play" else "### Description (4000)\n"
        at = text.index(field, start, end) + len(field)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text[:at] + name + " " + text[at:])

    def test_the_committed_store_copy_is_clean(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_each_languages_app_store_description_naming_galaxy_watch_fails(self):
        for lang in ("en", "ar", "fr", "tr", "id", "ur", "bn"):
            with self.subTest(lang=lang):
                self.tearDown()
                self.setUp()
                self.break_store(lang, "App Store", "Galaxy Watch")
                code, out = self.run_check()
                self.assertEqual(code, 1, out)
                self.assertIn(f"{lang}: App Store: Description: names Galaxy", out)

    def test_each_languages_play_description_naming_apple_watch_fails(self):
        for lang in ("en", "ar", "fr", "tr", "id", "ur", "bn"):
            with self.subTest(lang=lang):
                self.tearDown()
                self.setUp()
                self.break_store(lang, "Google Play", "Apple Watch")
                code, out = self.run_check()
                self.assertEqual(code, 1, out)
                self.assertIn(f"{lang}: Google Play: Full description: names Apple Watch", out)

    def test_a_name_written_onto_a_word_of_the_language_fails_too(self):
        # As the copy itself writes Latin names in Arabic and Urdu ("وiPhone"), and Bengali's ending.
        for lang, store, written, field in (
                ("ar", "App Store", "وGalaxy Watch", "Description: names Galaxy"),
                ("ar", "Google Play", "وApple Watch", "Full description: names Apple Watch"),
                ("ur", "App Store", "وGalaxy Watch", "Description: names Galaxy"),
                ("ur", "Google Play", "وApple Watch", "Full description: names Apple Watch"),
                ("bn", "App Store", "Wear OSএ", "Description: names Wear OS"),
                ("bn", "Google Play", "Apple Watchএ", "Full description: names Apple Watch")):
            with self.subTest(lang=lang, store=store):
                self.tearDown()
                self.setUp()
                self.break_store(lang, store, written)
                code, out = self.run_check()
                self.assertEqual(code, 1, out)
                self.assertIn(f"{lang}: {store}: {field}", out)


if __name__ == "__main__":
    unittest.main()
