"""date-fit.py's reading of Arch's right-to-left Gregorian line (Ruling 51a's month rule A), tested on
the face as committed and on faces made wrong on purpose:

    python3 -I tools/watchface/date-fit-test.py

- step_for() gives each of the phone's forms the size face-centre.py's ARCH_GREGORIAN_STEPS gives it:
  19 characters or more, 11.25 units; else August as the phone writes it (أغسطس), 13; else 14;
- shows() reads a face expression as the runtime does, and refuses what it cannot read;
- written_steps() and expected_steps() read the face's Groups and the table, and face_steps_check()
  holds them to each other, form by form: a face that drops the month's test, spells August
  otherwise or draws a step at another size fails.

No font is drawn here, so no fonts= is needed.
"""
import importlib.util
import json
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("date_fit", os.path.join(HERE, "date-fit.py"))
D = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(D)
K = D.K
AUGUST = "أغسطس"
TEXT = "[COMPLICATION.TEXT]"
N = "textLength(%s)" % TEXT
LAST5 = "subText(%s, clamp(%s - 5, 0, 99), %s)" % (TEXT, N, N)

with open(D.FORMS, encoding="utf-8") as _fh:
    LOCALES = json.load(_fh)["locales"]
RTL_DATES = sorted({t for loc in LOCALES.values() if loc["rtl"] for ds in loc["digitSets"] for t in D.gregorian_lines(loc, ds)})


def steps():
    return D.arch_steps(HERE)   # the Bengali font's folder is never read: no form is drawn


def units(text):
    return D.step_for(steps(), text).line.size / K


class TheRule(unittest.TestCase):
    """Month rule A, as step_for() reads it from face-centre.py."""

    def test_the_sample_keeps_the_base_faces_14_units(self):
        self.assertEqual(units("الأحد ١٨ أكتوبر"), 14)
        self.assertEqual(units("الأحد 18 أكتوبر"), 14)

    def test_august_as_the_phone_writes_it_is_13_units_at_every_length(self):
        for text in ("السبت ١ أغسطس", "الخميس ١٠ أغسطس", "الخميس 10 أغسطس", "الثلاثاء ٣١ أغسطس"):
            with self.subTest(text=text):
                self.assertEqual(units(text), 13)

    def test_nineteen_characters_or_more_are_11_25_units(self):
        for text in ("الأحد ١ كانون الأول", "الخميس 10 تشرين الأول", "الثلاثاء ١٠ تشرين الأول"):
            with self.subTest(text=text, length=len(text)):
                self.assertGreaterEqual(len(text), 19)
                self.assertEqual(units(text), 11.25)

    def test_eighteen_characters_not_in_august_are_14_units(self):
        text = "الأربعاء ٣٠ ديسمبر"
        self.assertEqual(len(text), 18)
        self.assertEqual(units(text), 14)

    def test_every_other_august_the_phones_write_is_14_units(self):
        # ar-MA's غشت, ar-TN and ar-DZ's أوت, the Levantine آب, Urdu's اگست: the rule is the one spelling
        others = {loc["months"][7] for loc in LOCALES.values() if loc["rtl"]} - {AUGUST}
        self.assertTrue(others)
        for month in sorted(others):
            with self.subTest(month=month):
                self.assertEqual(units("الخميس ١٠ " + month), 14)

    def test_the_phones_that_write_august_so_are_the_four(self):
        writers = sorted(tag for tag, loc in LOCALES.items() if loc["months"][7] == AUGUST)
        self.assertEqual(writers, ["ar-AE", "ar-EG", "ar-LY", "ar-SA"])

    def test_the_last_step_takes_every_form(self):
        last = steps()[-1]
        self.assertIsNone(last.least)
        self.assertIsNone(last.month)


class FaceExpressions(unittest.TestCase):
    """shows(): the face's expressions for slot 2's text, read as the runtime reads them."""

    def test_the_length_and_the_last_five_characters(self):
        august = "(%s <= 18 && %s == \"%s\") ? 255 : 0" % (N, LAST5, AUGUST)
        self.assertEqual(D.shows(august, "الخميس ١٠ أغسطس"), 255)
        self.assertEqual(D.shows(august, "الأحد ١٨ أكتوبر"), 0)
        self.assertEqual(D.shows(august, "أغسطس"), 255)
        self.assertEqual(D.shows("(%s != \"%s\") ? 255 : 0" % (LAST5, AUGUST), "الأحد ١٨ أكتوبر"), 255)

    def test_an_empty_text_is_read(self):
        self.assertEqual(D.shows("(%s <= 18 && %s != \"%s\") ? 255 : 0" % (N, LAST5, AUGUST), ""), 255)

    def test_numbers_and_logic(self):
        self.assertEqual(D.shows("(%s > 18) ? 255 : 0" % N, "x" * 19), 255)
        self.assertEqual(D.shows("(%s > 18) ? 255 : 0" % N, "x" * 18), 0)
        self.assertEqual(D.shows("!(1 == 2) && (3 - 1 >= 2 || 0 < -1) ? 7 : 8", ""), 7)
        self.assertEqual(D.shows("clamp(%s - 5, 0, 99)" % N, "abc"), 0)

    def test_a_function_or_source_it_does_not_know_fails(self):
        for expression in ("icuText(\"EEE\")", "[COMPLICATION.TITLE]", "%s * 2" % N, "subText(%s, 0" % TEXT):
            with self.subTest(expression=expression):
                with self.assertRaises(ValueError):
                    D.shows(expression, "الخميس")

    def test_a_subtext_outside_the_text_fails(self):
        with self.assertRaises(ValueError):
            D.shows("subText(%s, %s - 5, %s)" % (TEXT, N, N), "abc")

    def test_a_string_is_never_compared_with_a_number(self):
        with self.assertRaises(ValueError):
            D.shows("%s == 5" % LAST5, "الخميس ١٠ أغسطس")


class TheFace(unittest.TestCase):
    """written_steps(), expected_steps() and face_steps_check() on the committed face and on faces
    made wrong on purpose."""
    CASES = ["الأحد ١٨ أكتوبر", "السبت ١ أغسطس", "الخميس ١٠ أغسطس", "الثلاثاء ١٠ تشرين الأول", ""]

    def check(self, xml, texts=CASES):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".xml", delete=False) as fh:
            fh.write(xml)
        try:
            return D.face_steps_check(D.written_steps(fh.name), steps(), texts)
        finally:
            os.unlink(fh.name)

    def face(self):
        with open(D.FC.FACE_XML, encoding="utf-8") as fh:
            return fh.read()

    def in_group(self, xml, text, old, new):
        """xml with old made new once inside the Group that draws text's step."""
        i = xml.index('<Group name="greg_arch_rtl_%s"' % D.step_for(steps(), text).name)
        j = xml.index("</Group>", i)
        self.assertEqual(xml[i:j].count(old), 1, old)
        return xml[:i] + xml[i:j].replace(old, new) + xml[j:]

    def test_the_face_draws_the_tables_groups(self):
        self.assertEqual([row[:4] for row in D.written_steps()], D.expected_steps(steps()))

    def test_every_form_shows_the_one_group_its_step_names(self):
        self.assertEqual(D.face_steps_check(D.written_steps(), steps(), RTL_DATES + [""]), [])

    def test_the_sample_is_drawn_in_the_base_faces_own_box(self):
        rest = next(row for row in D.written_steps() if row[0] == D.step_for(steps(), "الأحد ١٨ أكتوبر").name)
        self.assertEqual(rest[1:4], (31.5, 118, 72))

    def test_a_face_without_the_months_test_fails(self):
        xml = self.in_group(self.face(), "الخميس ١٠ أغسطس", ' &amp;&amp; %s == "%s"' % (LAST5, AUGUST), "")
        failures = self.check(xml)
        self.assertTrue(any("الأحد ١٨ أكتوبر" in f for f in failures), failures)

    def test_a_face_that_spells_august_otherwise_fails(self):
        xml = self.face()
        self.assertEqual(xml.count('"%s"' % AUGUST), 2)
        failures = self.check(xml.replace('"%s"' % AUGUST, '"اغسطس"'))
        self.assertTrue(any("الخميس ١٠ أغسطس" in f for f in failures), failures)

    def test_a_face_drawing_a_step_at_another_size_fails(self):
        xml = self.in_group(self.face(), "الخميس ١٠ أغسطس", 'size="%g"' % (13 * K), 'size="%g"' % (14 * K))
        failures = self.check(xml)
        self.assertTrue(any("ARCH_GREGORIAN_STEPS" in f for f in failures), failures)

    def test_a_form_outside_the_basic_multilingual_plane_fails(self):
        # the face's textLength() and subText() may count it as two, and len() as one
        failures = D.face_steps_check(D.written_steps(), steps(), ["الخميس \U0001D7D9 أغسطس"])
        self.assertTrue(any("Basic Multilingual Plane" in f for f in failures), failures)


if __name__ == "__main__":
    unittest.main()
