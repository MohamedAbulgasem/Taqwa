# Plan 2 Task 2: the phone's date words and countdowns, measured on the face, and Urdu's digits

Lane W (`watch-wear`), base `ff28cd5a`, 25 October 2026. Plan: `docs/superpowers/plans/2026-10-23-watch-companion-2-phone-feed.md`, Task 2. The evidence is in `2026-10-23-phone-forms-fit/` beside this file, Ruling 51's in its `r51/` folder and Ruling 51a's in `r51a/`.

## The two findings, and Ruling 51

The task's first run stopped twice under Step 4's rule (when a form does not fit, the task stops and the Lead rules):
1. **Arch's Gregorian line in Arabic came too close to the lit stroke.** 2,106 forms were under the face's 7 px (`date-fit.py`): the Levantine and Iraqi months, and the Thursdays and Saturdays of August on every phone that writes أغسطس.
2. **Urdu's ۶ in the Moon's date window, after the font fix.** `window-fit.py` found "جمعرات ۶" 2.9 px from its day, under 3 px, because `face-moon.py`'s estimate of the figure's lead was measured on the Persian ۶.

The Lead's Ruling 51 (the ledger, 25 October) answered both, and Ruling 51a amended its item 1. The face now holds:
1. **Arch's right-to-left Gregorian line takes its size by its length and its month** (Ruling 51a, the report's month rule A, which replaced Ruling 51 item 1's table by length): 19 characters or more, 11.25 units; else August as the phone writes it (أغسطس), 13 units; else 14 units, the base face's own box, size and baseline. `ARCH_GREGORIAN_STEPS` in `tools/face-centre.py` holds the rule, the generator writes it into the face through `subText()` and `==`, and `date-fit.py` measures it and reads the face's own expressions back. Every form is 7 px or more from the lit stroke (nearest 7.9 px), the sample is drawn as before Ruling 51, and Latin, Bengali, Ring and Sun are unchanged. Ruling 51 item 1's three steps by length were built first; "Ruling 51a" below says why they went and how the rule was built.
2. **`DAY_LEAD_NOTO` describes the glyphs the v2 fonts draw.** ۶ is in ٦'s class (0.041 em) and ۷ in the 0.054 class. `window-fit.py` passes. The only face change is the Moon window's `scaleX` and `scaleY`, two lines.
3. **`docs/ATTRIBUTION.md`** now says the Noto Sans Arabic files are modified, and how: subset, Urdu's ۴ ۶ ۷ mapped through the font's own Urdu `locl`, the Light's digits mapped to its `pnum` figures, and renamed files, under OFL 1.1. The licence names no Reserved Font Name, so the fonts' internal names stay.
4. The proof is the base and the new face shot in one session, and the installs use `adb -s emulator-5558 install -r`.
5. The task closed with the plan's two commits, made over the WIPs by a soft reset; review rounds 1 to 3's fixes and Ruling 51a each came after as a commit of their own.

### Ruling 51a: why the table by length went

Ruling 51 item 1 asked for a table by length that kept the commonest forms and the sample at 14 units, with as few steps as pass. Review rounds 1 to 3 found that no table by length could meet it, and the Lead's Ruling 51a (the ledger, 25 October) says why: the sample's "الأحد ١٨ أكتوبر" and the failing "الخميس ١٠ أغسطس" are both 15 characters, so a table by length draws them at one size, and at 14 units "الخميس ١٠ أغسطس" is −1.3 px from the stroke. The three steps built for Ruling 51 drew the sample at 13 units and kept 14 units on 34 days a year on ar-SA, ar-EG, ar-LY and ar-AE.

Ruling 51a amends item 1 to the report's month rule A, which review round 3 measured: at 14 units the only one-word month under 7 px is August as ar-SA, ar-EG, ar-LY and ar-AE write it, and 19 or more characters are exactly the Levantine and Iraqi two-word months. Under it:
- the sample keeps 14 units, in the base face's own box (x 45, y 118, 360 by 72, 31.5 px), and is drawn as before Ruling 51: 0 px from `ff28cd5a`'s face on emulator-5558, in one session;
- ar-SA, ar-EG, ar-LY and ar-AE keep 14 units on 334 days of 2027 (31 at 13, August), and their date changes size twice a year, into August and out of it;
- ar-IQ, ar-SY, ar-JO and ar-LB keep 14 units on 242 days (123 at 11.25, October to January), with 2 changes a year;
- the Maghreb's phones and Urdu's keep 14 units every day, with no change;
- every form the phones write is 7 px or more from the stroke under `date-fit.py`'s model: 11.25 units nearest 7.9 px ("الخميس 10 تشرين الأول"), 13 units 8.7 px ("الخميس 10 أغسطس"), 14 units 8.0 px ("الخميس 10 ديسمبر").

How it was built, test-first, and what the device shows is in "Ruling 51a (25 October)" below.

**For plan 2's Task 7.** The rule holds to the phone's own spelling of August, أغسطس, as `phone-forms.json` gives it for ar-SA, ar-EG, ar-LY and ar-AE. A phone whose ICU spelt August otherwise would draw its August at 14 units, and "الخميس ١٠ <its August>" might then come under 7 px. Task 7's `forms` diff, `phone-forms.json` written on the phone emulator's ICU against this one, shows any such spelling, and `date-fit.py forms=<the device's file>` then measures it. `date-fit-test.py`'s `test_the_phones_that_write_august_so_are_the_four` names the four tags, so a committed `phone-forms.json` regenerated with another spelling fails there too. Plan 2 Task 7's forms step now says this.

The tables weighed for Ruling 51 item 1 and Ruling 51a, kept as the record (`r51/tables.py`, `r51/tables.txt`, `review3/monthrule.py`, `review3/monthrule.txt`, each measured with `date-fit.py`'s own model at each step's own box and baseline). Days are at 14 units over 2027, and "changes" means the overnight size changes in the year:

| Table | `ARCH_GREGORIAN_STEPS` (characters, units) | Passes | Sample | ar-SA, ar-EG, ar-LY, ar-AE | Maghreb (ar-MA; ar-TN, ar-DZ) | ar-IQ, ar-SY, ar-JO, ar-LB | Urdu |
|---|---|---|---|---|---|---|---|
| One size (before Ruling 51) | (all, 14) | no: 2,106 forms under 7 px | 14 | 365 days, 0 changes | 365, 0 | 365, 0 | 365, 0 |
| Two steps, 14 units kept | (13, 14), (rest, 11.25) | yes | 11.25 | 34 (331 at 11.25), 32 | 61; 65 (rest at 11.25), 42; 44 | 73 (292 at 11.25), 46 | 276, 142 |
| Three steps (built for Ruling 51, replaced) | (13, 14), (18, 13), (rest, 11.25) | yes: 8.1 / 8.7 / 7.9 px | 13 | 34 (331 at 13), 32 | 61; 65 (rest at 13), 42; 44 | 73 (169 at 13, 123 at 11.25), 48 | 276, 142 |
| Two steps, no 14 units | (18, 13), (rest, 11.25) | yes: 8.7 / 7.9 px | 13 | 0 (365 at 13), **0** | 0 (365 at 13), 0 | 0 (242 at 13, 123 at 11.25), 2 | 0 (365 at 13), 0 |
| Non-monotonic (spec review) | (13, 14), (15, 13), (18, 14), (rest, 11.25) | yes: 8.1 / 8.7 / 8.0 / 7.9 px | 13 | **200** (165 at 13), 135 | 193; 205 (rest at 13), 128; 129 | 140 (102 at 13, 123 at 11.25), 100 | 282, 142 |
| **Month rule A (built, Ruling 51a)** | 19 or more: 11.25; else August (أغسطس): 13; else 14 | yes: 8.0 / 8.7 / 7.9 px | **14** | **334** (31 at 13), **2** | 365, 0; 365, 0 | 242 (123 at 11.25), 2 | 365, 0 |
| Month rule B (not by length; review round 3) | 19 or more: 11.25; else August of 14 or more: 13; else 14 | yes: 8.0 / 8.7 / 7.9 px | **14** | 337 (28 at 13), 4 | 365, 0; 365, 0 | 242 (123 at 11.25), 2 | 365, 0 |

## What is built

- **`PhoneForms`** (`shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/PhoneForms.kt`, internal, not called by release code). For every tag of `PhoneTags.TAGS` and `PhoneTags.FALLBACK_TAGS`, with `UiLanguage.of(tag)` as its interface language, it builds the tag through `AndroidPlatformFormat`, `WatchDateLine`, `WatchWords` and `WatchDigits`, and writes:
  - the interface language's code and `rtl`;
  - `digitSets`, which is `PhoneTags.digitSets(tag)`'s digits in order;
  - the 7 weekdays (Sunday first) and 12 months, exactly as the feed's line writes them: without the trailing dot, in the interface language's capitals;
  - the four countdown phrases.

  The month is taken from the feed's own line (`gregorian` without its `"$weekday $dayNumber "` prefix), so no second copy of the rule exists. The JSON is written by hand, with kotlinx's escaping (`JsonPrimitive.toString()`), so that each locale fits on a few readable lines.
- **`FaceFormsTest`** (`shared/src/androidHostTest/…/watchfeed/FaceFormsTest.kt`):
  - `phoneFormsAreUpToDate`: the file is `PhoneForms.json()`. It rewrites the file under `-Ptaqwa.updateVectors`.
  - `formsComposeAsTheFeedWritesThem`: for every tag (fallback tags included), every digit set (the format built on that set's `formatTag`) and all 365 dates of 2027, `WatchDigits.of(format, language).write(WatchDateLine.of(date, format, language).gregorian)` is the file's weekday, the day in that set and the file's month, separated by single spaces. It also holds the file's `digitSets` to `PhoneTags.digitSets` in order.
  - `theFallbackLocalesAreMeasuredAsTheFeedWritesThem`: hi-IN, he-IL, fa-IR, mr-IN and zh-CN have language "en", `rtl` false, en's weekdays, months and countdown, and `["0123456789"]`.
- **`tools/watchface/phone-forms.json`** (generated, 244 lines, 30 tags). Forms of note:

  | Tag | Form |
  |---|---|
  | en-GB, en-ZA | "SEPT" |
  | fr-CA | "JUILL" |
  | fr-MA | "JUI" (June) beside "JUIL" (July) |
  | ar-MA | "ماي يوليوز غشت شتنبر نونبر دجنبر" |
  | ar-TN, ar-DZ | "جانفي فيفري … جويلية أوت" |
  | ar-IQ, ar-SY, ar-JO, ar-LB | the Levantine months; ar-IQ's تشرين الأول holds a no-break space (U+00A0) where its other two-word months have a space, and the Noto subset carries it |
  | bn-IN | "সেপ্টেঃ অক্টোঃ নভেঃ ডিসেঃ" (the visarga abbreviation) |
  | ur-PK | Western digits first; ur-IN starts with extended Arabic-Indic |
  | ar-LY, ar-MA, ar-TN, ar-DZ, ar-AE | Western digits first; the other Arabic tags start with Arabic-Indic |

- **`shared/build.gradle.kts`**: `phone-forms.json` is a test input (`phoneForms`), in the existing `taqwa.updateVectors` block, so a hand edit never leaves the test up to date.
- **`tools/face-centre.py`**:
  - The two centre lines' fonts and box are named once. `CENTRE_LINE_FONT` holds the family, size and letter spacing by line and direction, `LINE_BOX` is (45, 360), and `centre_line(name, rtl, size=None)` gives the run, the tops and the height, at another size when asked, with the baseline kept. `dates()` and `countdown()` use them, and `date-fit.py` reads them.
  - `ARCH_GREGORIAN_STEPS` (Ruling 51a) is `[("long", 19, None, 11.25 units), ("august", None, "أغسطس", 13 units), ("rest", None, None, 14 units)]`: each step's name, the least characters it takes, the month the text must end in, and its size in px. A form takes the first step whose tests it meets, and the last takes every form. `ARCH_GREGORIAN_AUGUST` names the month. `dates()` writes the right-to-left Gregorian line inside an `archGregorian` Condition:
    - on Arch, one Group a step (`greg_arch_rtl_long`, `_august`, `_rest`), each a box placed by the font's metrics on the line's baseline (y 127 height 58, y 122 height 66, y 118 height 72), and each shown by its own tests and none of an earlier step's (`gregorian_step_tests()`):
      - `long`: `textLength([COMPLICATION.TEXT]) > 18`;
      - `august`: `textLength(…) <= 18 && subText([COMPLICATION.TEXT], clamp(textLength(…) - 5, 0, 99), textLength(…)) == "أغسطس"`;
      - `rest`: the same with `!=`. Its box, size and baseline are the base face's (`ff28cd5a`'s one size);
    - the month is the text's last five characters, by `subText()` with its start kept inside the text (an empty text reads as ""), the way the face's clock already reads slot 7 with `subText()` and `==`. That an Arabic literal compares on this runtime was proven on emulator-5558 before the Groups were written ("Ruling 51a (25 October)");
    - on Ring and Sun, the Default, the whole line at 14 units as before.

    Arch's Gregorian line does not move when the wearer's slots are filled (`LAYOUT_COMPS` keeps its baseline at 73 units), so no step has a y Transform. The Latin line (`greg_ltr`) is unchanged.
- **`tools/watchface/date-fit.py`**, in `hijri-fit.py`'s manner. It reuses `hijri-fit.py`'s curve, `clearance()` and `system_fonts()`, and reads the fonts, boxes, baselines and Arch's steps from `face-centre.py`. It takes `forms=` (default `phone-forms.json`), `fonts=` (the Bengali font; without it the run fails) and `min=` (7). It checks:
  - every Gregorian line: 7 weekdays × 12 months × days 1–31, in every digit set of every tag;
  - every countdown: each phrase with every hour 1–99 and minute 1–59, a superset of the plan's 23/59 and 99/59.

  Each must be drawn whole in the 360 px box, at each size it is drawn at: its width, letter spacing included, at least 2 px under the box, as `hijri-fit.py` requires. On Arch, each date must also be `min=` px or more from the lit stroke at each of its places, the right-to-left date at the size of its step. The countdown's room to Arch's lit feet is printed but is not a check, since the plan bounds the countdown by its box.
  - **The steps are checked in the face too.** `written_steps()` reads the `greg_arch_rtl_*` Groups from `watchface.xml`: each one's name, font size, box and alpha expression. `face_steps_check()` fails the run unless the Groups are the table's steps in order, each at its size and in its box (`expected_steps()`), and unless, for every right-to-left form in the forms file and an empty text, exactly one Group shows, the one `step_for()` gives the form. It reads each Group's expression as the runtime does (`shows()`, a reader of the few WFF operators and functions the Groups use, which refuses anything else, a `subText()` outside its text and a string compared with a number). So a table the generator has not written, a generator that writes another, or a Group whose test is wrong cannot pass. A form outside the Basic Multilingual Plane fails too, since the runtime's `textLength()` and `subText()` may count it otherwise than `len()`; none is.
- **`tools/watchface/date-fit-test.py`** (Ruling 51a), `date-fit.py`'s tests, run as `python3 -I tools/watchface/date-fit-test.py` (20 tests, no font or emulator): the rule as `step_for()` reads it from `face-centre.py` (the sample at 14, every August form at 13 whatever its length, 19 characters and more at 11.25, 18 not in August at 14, every other August the phones write at 14, the four tags that write أغسطس, a last step that takes every form); `shows()` on the face's own expressions and on what it must refuse; and `face_steps_check()` on the face as committed, and on faces made wrong on purpose (the month's test dropped, August spelt "اغسطس", the August step at another size, a character outside the plane), each of which fails.
  - Latin is drawn tracked as the face draws it. Arabic, Urdu and Bengali are unspaced: Minikin spaces neither script, as `window-fit.py` already says. Bengali is drawn in the watch's Noto Sans Bengali UI at the weight of the run it falls back from (800 for the date, 600 for the countdown).
  - A character the font lacks fails the run, through `hb-shape` (glyph 0), because the watch would fall back to another font and the width would mean nothing. None does.
  - The room to the stroke is first bounded by the ink's box, exactly: the distance from each point of the stroke's centre line to the box, minus the half width. A form whose bound comes within 7.5 px is then drawn and measured row by row, as `hijri-fit.py` measures. The bound never exceeds the exact figure (`الخميس ١٠ أغسطس` at 14 units: bound −4.27, exact −1.31).
- **`tools/watchface/window-fit.py`**: `WEEKDAYS` and `PHONES` (the hand table) are gone. It reads `phones(forms)` from the file (`forms=`), one window for each (direction, weekdays, digit set), with the tags that send it named together. Numbers are drawn in Noto for the zeros in `ComplicationFields`' `NOTO_ZEROS`.
- **`tools/watchface/moon-fit.py`**: no code change; it already read the file by default once the file existed. Its docstring now says the file exists, and its README row was updated.
- **`tools/watchface/README.md`**: rows for `date-fit.py` (with Arch's steps) and `phone-forms.json`, the `window-fit.py` and `moon-fit.py` rows on the forms file, and `date-fit.py` in the verification block.

### The model against the emulator

`gregcheck.py` compares the model's ink of the Gregorian line with the committed pass shots, scaled from 454 to 450 px. The model is never narrower, and it is wider by 0.7–2.6 px, so it errs on the safe side:

| Shot | Line | Emulator ink (top, bottom, left, right) | Model |
|---|---|---|---|
| `pass-arch-ar-ly-12h-1458-awake` | الأحد 18 أكتوبر | 133.8, 170.5, 126.9, 320.2 | 133.3, 171.5, 126.2, 322.4 |
| `pass-arch-en-1458-awake` | SUN 18 OCT | 142.7, 164.5, 129.8, 319.2 | 142.7, 164.7, 128.2, 321.8 |
| `pass-ring-ar-sa-1458-awake` | الأحد ١٨ أكتوبر | 126.9, 163.5, 126.9, 320.2 | 126.3, 164.5, 126.2, 322.4 |
| `pass-ring-en-1458-awake` | SUN 18 OCT | 135.8, 157.6, 129.8, 319.2 | 135.7, 157.7, 128.2, 321.8 |

## Steps 1–2: red, then green

- The test alone: `:shared:compileAndroidHostTest` failed with "Unresolved reference 'PhoneForms'".
- With `PhoneForms` and without the file: 3 run, 3 failed, each on "… phone-forms.json is missing" (`FaceFormsTest.kt` lines 73 and 44). That is the plan's expected failure.
- Written with `-Ptaqwa.updateVectors`, then run again without it: 3 of 3 pass.

## Step 4: the four tools

On the fonts as they were (`v1/`; `fonts=` a fresh copy of emulator-5558's `NotoSansBengaliUI-VF.ttf`, SHA-256 `2df70a74…`, pulled under the `wear-5558` lock):

| Tool | Result |
|---|---|
| `date-fit.py` | **FAIL**: Arabic on Arch (finding 1, below). Every countdown fits: the widest is "۹۹ گھنٹے ۵۹ منٹ میں" at 263.8 px of 360, and every countdown is at least 43.8 px from Arch's lit feet. Every Latin and Bengali date fits: the widest is bn-IN's "বৃহস্পতি ৩১ সেপ্টেঃ" at 242.9 px, 17.2 px from the stroke; the least Latin room is 24.8 px (fr "MAR 20 AOÛT"). The Arabic dates that fit on Arch: ar-MA nearest 13.3 px, ar-TN and ar-DZ 8.0 px. On Ring and Sun every form is drawn whole (widest "الخميس ٣١ تشرين الثاني", 346.4 px, 13.6 px under the box) |
| `window-fit.py` | PASS on the phone's weekdays: nearest 3.3 px (ur "جمعرات 20"); Bengali's বৃহস্পতি at Ruling 34's 0.5 floor with the phone's own short form, 8.7 px from its day; every estimate checks |
| `moon-fit.py` | PASS: the countdown from `phone-forms.json`, all 30 tags and every digit set the file carries (258,000 forms): the widest is "এক মিনিটের কম সময়ে" (bn), 112.3° of the 124° path, 12.0 px to spare at each end. The Hijri date in both layouts and the prayer line are as before. The glyph count against PAD passes |
| `hijri-fit.py` | PASS, unchanged (the guard): least 7.1 px, ১০ জমাদিউল আউয়াল |

With Arch's steps (Ruling 51) and the v2 fonts (`r51/`):

| Tool | Result |
|---|---|
| `date-fit.py` | **PASS** (`r51/date-fit-green.txt`). Every form is drawn whole and 7 px or more from Arch's lit stroke. Arch's right-to-left steps: up to 13 characters, 14 units, 6,704 forms, nearest 8.1 px ("السبت 1 أغسطس"); up to 18, 13 units, 12,392 forms, nearest 8.7 px ("الخميس 10 أغسطس"); longer, 11.25 units, 2,170 forms, nearest 7.9 px ("الخميس 10 تشرين الأول"). The widest on Arch is now 277.7 px ("الخميس ٣١ تشرين الثاني"). Ring and Sun, Latin, Bengali and the countdowns give exactly the figures above |
| `date-fit.py`, Ruling 51a's month rule A | **PASS** (`r51a/date-fit-green.txt`), the face's steps read back from its expressions. 19 characters or more, 11.25 units, 2,170 forms, nearest 7.9 px ("الخميس 10 تشرين الأول"); ending in أغسطس, 13 units, 434 forms, nearest 8.7 px ("الخميس 10 أغسطس"); the rest, 14 units, 18,662 forms, nearest 8.0 px ("الخميس 10 ديسمبر"). ar-MA's nearest is 13.3 px and ar-TN's and ar-DZ's 8.0 px, as at one size before Ruling 51; Urdu's 12.2 px. The countdowns, Latin, Bengali, Ring and Sun are unchanged |
| `window-fit.py` | **PASS** with Ruling 51's `DAY_LEAD_NOTO` (`r51/window-fit-green.txt`): Urdu's extended days nearest 3.4 px ("جمعرات ۲۰"), and every estimate checks |
| `moon-fit.py`, `hijri-fit.py` | PASS, the same output as on the v2 fonts before Ruling 51 (`v2/`) |
| `window-fit.py`, `moon-fit.py`, `hijri-fit.py`, `slot-fit.py` after Ruling 51a | PASS; the first three byte for byte `r51/`'s outputs |

The Lead's call 7, the shorter under-a-minute drafts on the Moon's countdown curve (`call7.py`, the same model). The first drafts give exactly Task 11's figures:

| Language | First draft | Shorter draft (call 7) | Room left |
|---|---|---|---|
| French | dans moins d’une minute, 139.2° | d’ici une minute, **89.4°** | 35.7 px at each end |
| Indonesian | kurang dari semenit lagi, 133.6° | kurang dari semenit, **110.5°** | 13.9 px at each end |
| Bengali | এক মিনিটেরও কম সময়ে, 121.2° | এক মিনিটের কম সময়ে, **112.3°** | 12.0 px at each end |

Each is measured against the curve's 121.1° room (124° less 3 px at each end).

`gregorianSpoken` and `ui.spoken` are never drawn, so no tool measures them.

### Finding 1: Arch's Arabic Gregorian line at one size

The face drew the Arabic Gregorian line in Noto Sans Arabic Bold at 31.5 px (14 units), at baseline 164 px, in the 360 px box. Its tall letters reach about 31 px above the baseline, into the part of the arch where the crown narrows.
- `greg-fails.txt` groups every failing form by month and weekday; `greg-fails-all.txt` lists all 2,106 forms with each one's room and sends.
- The rooms in the table are the tool's model. −4.3 px is the metric's floor: the model's ink is on the stroke's centre line.
- The model's stroke is the emulator's to within 0.5 px (`shotgap.py`), and its ink is wider than the emulator's. So the real room was larger: 1–2 px for the forms shot on the device (`gregorian-on-the-emulator.txt`, `gregorian-arch-arabic.png`).

| Room | Month (as sent) | Weekday | Forms | Tags (both digit sets each) |
|---|---|---|---|---|
| −4.3 | تشرين الأول | الثلاثاء, الجمعة, الأربعاء, الخميس, الاثنين, السبت | 62 each | ar-IQ (with U+00A0), ar-JO, ar-LB, ar-SY |
| −4.3 | تشرين الثاني | السبت, الخميس, الثلاثاء, الجمعة, الاثنين, الأربعاء | 62 each | ar-IQ, ar-JO, ar-LB, ar-SY |
| −4.3 to −2.6 | كانون الثاني | الخميس, السبت, الأربعاء, الجمعة, الاثنين, الثلاثاء | 62 each | ar-IQ, ar-JO, ar-LB, ar-SY |
| −4.2 to −0.7 | كانون الأول | الخميس, السبت, الجمعة, الأربعاء (10th on) | 62 / 44 | ar-IQ, ar-JO, ar-LB, ar-SY |
| −1.3 | تشرين الثاني | الأحد | 62 | ar-IQ, ar-JO, ar-LB, ar-SY |
| **−1.3** | **أغسطس** | **الخميس** | **62** | **ar-SA, ar-EG, ar-LY, ar-AE** |
| 0.2, 1.1 | كانون الأول | الاثنين, الثلاثاء (10th on) | 44 | ar-IQ, ar-JO, ar-LB, ar-SY |
| 2.4 | تشرين الأول | الأحد (10th on) | 44 | ar-IQ, ar-JO, ar-LB, ar-SY |
| 6.5 | أغسطس | السبت (10th on) | 44 | ar-SA, ar-EG, ar-LY, ar-AE |
| 6.9 | كانون الثاني | الأحد (10th on) | 44 | ar-IQ, ar-JO, ar-LB, ar-SY |

`gregsizes.py` (`gregsizes.txt`) measured every right-to-left form at one smaller size for all, for the ruling: one size for every form needs 11 units (least room 9.4 px).

### Ruling 51 item 1: the steps, built test-first

(Replaced by Ruling 51a's month rule A: see "Ruling 51a (25 October)". This section is the record of the three steps by length.)

- **Red, twice** (`r51/date-fit-red.txt`, `r51/date-fit-red2.txt`):
  - With `date-fit.py` measuring Arch's right-to-left date at its step and checking the face's steps, and `ARCH_GREGORIAN_STEPS` holding today's one size, the run failed on **2,107**: the 2,106 forms, and "watchface.xml draws Arch's right-to-left Gregorian steps as [] …".
  - With the generator writing the steps, the XML check passed and the 2,106 forms still failed.
- **The table.** `r51/bylen.py` (`r51/bylen.txt`) measured the least room of every length (9 to 24 characters) at sizes from 14 to 11 units in quarter units, at Arch's baseline. `r51/tables.py` (`r51/tables.txt`) then measured each candidate table at each step's own box and baseline, as `date-fit.py` does. In brief:
  - at 14 units, every length up to 13 characters fits (13: 8.1 px), 14 and 15 do not, and 16 to 18 do again (8.0, 8.9 and 12.4 px): the August forms are short and wide;
  - at 13 units, every length up to 18 fits (14: 9.8 px, 15: 8.5 px);
  - lengths 19 to 24 are exactly the forms with a Levantine or Iraqi two-word month (every one-word month gives 9 to 18 characters, every two-word one 19 to 24), and they all fit first at 11.25 units (21: 7.9 px, 22: 8.5 px).

  So the steps are 14 units up to 13 characters, 13 units up to 18, and 11.25 units for the rest.
- **Green** (`r51/date-fit-green.txt`): every form fits. The generators' `--check` are up to date, and `hijri-fit.py`, `moon-fit.py` and `window-fit.py` pass.
- **The baselines** are the box's top, rounded to a pixel as every box on the face is, plus its middle and the font's offset: 164.02, 164.30 and 164.05 px for the three steps, against the line's 164.25 (73 units). `date-fit.py` measures each at its own.

**On emulator-5558** (`r51/shots/`, the new face; `r51/shotgap.txt`, the room between the line's ink and the stroke's lit pixels in each awake shot, beside the model's figure):

| Case | Characters, step | In the shot | Model | Before the ruling, 14 units (shot, model) |
|---|---|---|---|---|
| "الأحد ١٨ أكتوبر", the sample | 15, 13 units | 49.0 px | 47.7 px | 42.4 px, 41.1 px |
| "السبت ١ أغسطس" | 13, 14 units | 16.5 px | 14.2 px | not shot; the same size then |
| "الخميس ١٠ أغسطس" | 15, 13 units | 9.1 px | 8.7 px | 1.0 px, −1.3 px |
| "الثلاثاء ١٠ تشرين الأول" | 23, 11.25 units | 24.2 px | 21.2 px | 2.0 px, −4.3 px |
| "الخميس 10 تشرين الأول" | 21, 11.25 units | 14.9 px | 11.6 px | not shot; model −4.3 px |
| "الخميس ٣٠ تشرين الثاني", the widest | 22, 11.25 units | 12.4 px | 8.5 px | 1.0 px, −4.1 px |

- Each case is shot awake and always-on, on Arch with the Saudi sample at 14:58 and every day's Gregorian line rewritten (`feedtext.sh`, as in the first run).
- The model is never more generous than the shot: it is 0.4 to 3.9 px under the room the device shows, the same caution as before.
- The nearest form of the 11.25-unit step in `date-fit.py` (7.9 px) is ar-IQ's "الخميس 10 تشرين الأول", whose month holds a no-break space. The shot's case is written with a plain space, which the model puts at 11.6 px.

`r51/gregorian-arch-arabic.png` puts each case before the ruling (the first run's shots) above its shot now, with the line's left end at three times.

**The sample, shot in one session against the face found installed** (`r51/same-session.txt`): the base face (`ff28cd5a`'s, as found) and the new face, installed in turn, shot at the same moments on the committed samples.

- **Only Arch's Arabic Gregorian line changes.** Its band (y 134–173 of the 454 px shot) differs on the two Arabic Arch samples, awake and always-on. That is the sample's line drawn at 13 units in place of 14: "الأحد ١٨ أكتوبر" and "الأحد 18 أكتوبر" are 15 characters.
- Arch in English, Ring, Sun and the Moon's always-on frames are 0 px apart.
- The Moon's two awake frames differ in the seconds hand alone (x 220–239), and Ring's awake frame in one pixel by one level at the ring's edge, as in the first run's same-session check.
- So the sample was not unchanged on Arch in Arabic under the three steps, which is why Ruling 51a replaced them ("Ruling 51a: why the table by length went", above). Everywhere else it was unchanged.

**The always-on lit share** (`r51/lit-share.txt`, `tools/watchface/lit.py undither`):

| Frame (always-on, as the face draws it) | Base face | Now |
|---|---|---|
| Arch, Libya's Arabic, 12-hour, 14:58 | 10.26% | 10.02% |
| Arch, Saudi Arabia's Arabic, 14:58 | 9.96% | 9.74% |
| Arch, English, 14:58 | 11.76% | 11.76% |
| Ring, Saudi Arabia's Arabic, 14:58 | 11.53% | 11.53% |
| Sun, Libya's Arabic, 12-hour, 14:58 | 11.80% | 11.80% |
| The Moon, Saudi Arabia's Arabic, 10:10 | 11.80% | 11.80% |
| The Moon, Libya's Arabic, 12-hour, 14:58 | 10.27% | 10.27% |

- The six cases on Arch are 9.74% to 10.21% now. The two the first run shot at 14 units were 10.42% ("الخميس ١٠ أغسطس") and 10.79% ("الثلاثاء ١٠ تشرين الأول").
- The highest frame here is 11.80%, under Google's 15%.
- A right-to-left date is now drawn at its old size or smaller, on the same baseline, so no frame of any style lights more than before. Plan 1's worst moments (Task 13's `lit-share.txt`, Arch's highest 13.52%) stand.

## Steps 6–9: Urdu's ۴ ۶ ۷ in all three weights

### Steps 6–7: the forms and the subsets

- **Step 6, as they were.** `tools/watchface/get-noto.sh` was run into an empty scratch folder. The three upstream files' SHA-256 are the same as plan 1's download (Bold `fad3cb45…`, Light `40feb5bd…`, SemiBold `784bfd6b…`). `subset-noto.sh` on them left `git status --short watchface/src/main/res/font` empty, so the change starts from the committed bytes.
- The forms, read with the pinned fontTools 4.55.0 (`fontcheck.py forms`, `fontforms.txt`):
  - Upstream, in every weight, the cmap gives U+06F4 `uni06F4`, U+06F6 `uni06F6` and U+06F7 `uni06F7`, the Persian forms. The arab script's URD `locl` maps them to `uni06F4.locl`, `uni0666` and `uni06F7.locl`, and the Light's `pnum` maps those to `uni06F4.locl.lf`, `uni0666.lf` and `uni06F7.locl.lf`.
  - The committed subsets carry no glyph names, so fontTools names their glyphs from the cmap. In SemiBold and Bold the `locl` gives `glyph00143`, `uni0666` and `glyph00144`, the plan's names.
  - In the committed Light, the glyph called `uni06F4` is already the Persian `pnum` form, and it has no `locl` of its own. That is the plan's finding: the Light's straight `pnum` mapping took ۴ ۶ ۷ out of Urdu's `locl`.
- **Step 7, the change** (`tools/watchface/subset-noto.py`):
  - `urdu_digits()` maps U+06F4, U+06F6 and U+06F7, in every Unicode cmap of every weight, to the glyph the upstream font's own arab/URD `locl` lookup gives their default glyph. It is found from the lookup, never by name, and lookup types 1 and 7 are both read.
  - In the Light, `proportional_digits()` then maps those to their `pnum` forms, and every other digit as before.
  - The script stops, naming the code point and the weight, when one has no URD form or, in the Light, no `pnum` form. I tried both stops on broken copies of the upstream fonts: "SemiBold: U+06F7 has no Urdu form …" and "Light: U+06F4's Urdu form uni06F4.locl has no pnum form". Neither changed a committed byte.
  - The glyphs the three mapped to before are kept (`populate(glyphs=…)`), so each subset holds the same 719 glyphs and the same layout tables as before, and only the cmap differs.
  - One bug was found and fixed while building: `getBestCmap()` returns the live table's dict, so it is now copied before the tables are written.
  - Output: `noto_sans_arabic_subset_v2_light.ttf`, `_semibold.ttf` and `_bold.ttf`. The script's docstring says why the names change and that the next change takes `_v3_`.
- **The check** (`fontcheck.py check`, `fontcheck.txt`):
  - Red first: the old files copied under the new names give **FAIL: 9**, exactly the three digits in each weight.
  - Green on the new files: **PASS**. In each weight, the cmap's glyphs for the three have the outline (decomposed, point for point) and the advance of upstream's `locl_URD(…)` (`pnum(locl_URD(…))` in the Light). The other 503 code points have the outline and advance of the old file of the same weight. Every table but `cmap` has the old file's bytes, except head's `checkSumAdjustment`, which is the whole file's checksum.
  - The old files still fail against the old files under the new names.
- **The renames.**
  - The old three files are deleted. Every reference is renamed: `tools/face-centre.py` (its family map and runs), `tools/face-moon.py`, `tools/watchface/{moon-fit,hijri-fit,window-fit,ref}.py`, the comments of `get-noto.sh` and `subset-noto.sh`, `subset-noto.py`, and the `subset-noto.sh` row of `tools/watchface/README.md`.
  - `python3 tools/face-centre.py` and `python3 tools/face-moon.py` regenerated `watchface.xml`. Its only change was the three family names, 46 lines, checked by diffing the removed lines against the added lines with the names put back.
  - All four generators' `--check` are up to date. The plan's Files grep finds nothing outside `docs/superpowers`, which it leaves out: there, this report's evidence of the old fonts' run names them.

### Step 8: the face, signed with the test key

- `./gradlew :watchface:assembleDebug :watchface:assembleRelease :watchface:lint :wearApp:assembleDebug --rerun-tasks`: green; lint "No issues found."
- `tools/watchface/validate.sh --no-build`: wff-validator 1.7.0 PASSED at format 2. Watch Face Push validator: 10 of 10 checks on the debug face and on the release's scratch copy signed with the test key (`validate.log`).
- The CI face step by hand:
  - the four generators' `--check` are up to date;
  - neither APK holds a `classes.dex`, `hasCode=false`, and there is no `uses-permission`;
  - `res/li.xml` is byte for byte `watchface.xml` in both;
  - both APKs' font resources are `font/noto_sans_arabic_subset_v2_{bold,light,semibold}`, and each file is the committed font byte for byte.
- **Installed on emulator-5558** with `adb -s emulator-5558 install -r` (the watch app's debug build and the debug face; the installed face equals the build). I did not use `install.sh`, whose Gradle `installDebug` the plan's Global Constraints rule out while the physical phone is attached; Ruling 51 item 4 confirms this.
- **The pass** (`tools/watchface/pass.sh`, `pass-vs-committed.txt`).
  - The first run stopped at 02:21:30, when the emulator crashed in the face editor on the way to Sun. Its log ends in gfxstream "Failed to find ColorBuffer" errors and a Crashpad line, with no shutdown. It was under my lock, and nobody stopped it. I booted it again with `tools/watchface/emulator.sh` and ran the whole pass again (02:23–02:46).
  - Against the committed pass it gives, shot for shot, the counts of Ruling 46's close (`task-13/r46-pass-vs-committed.txt`): every shot of Arch, Ring and Sun within 3 levels, the emulator's run-to-run noise. The Moon's always-on frames are within 1 px of the committed ones, which Ruling 46's close committed, except the battery's icon with the slots filled (56 px, 8 levels, which moves between runs). Its awake frames differ in the seconds hand and the moon, as before.
  - So against the committed shots it is not 0 px, and it never was, as Task 12's minor recorded ("0 px" against "within 3 levels"). Ruling 51 item 4 accepts the same-session proof below in its place.
- **The 0 px, shown in one session** (`same-session.txt`, `ab.sh`, `abdiff.py`), as Ruling 46's close showed its change.
  - The base face (the APK found installed, whose `li.xml` is `ff28cd5a`'s) and the v2 face were installed in turn and shot at the same moments: Arch in Libya's Arabic, in Saudi Arabia's and in English; Ring in Saudi Arabia's; Sun in Libya's; the Moon in Saudi Arabia's at 10:10 and Libya's at 14:58; each awake and always-on.
  - Base against v2, **11 of the 14 frames are 0 px**. The other three: the Moon's two awake frames differ only in the seconds hand (the band at twelve, x 220–240, the hand at :00), and Ring's awake frame in one pixel by one level at the ring's edge.
  - The two shots in Urdu's digits differ, and only there: the Saudi sample with every Arabic-Indic digit written in its extended twin (`feedtext.sh urdu`), at 14:47, on Arch and Ring. `urdu-digits-old-new.png`: the base face draws the Persian ۴ ۶ ۷ in the clock (Light), the Hijri day and the prayer's time (SemiBold); the v2 face draws Urdu's own forms, ۶ as ٦.
- **Step 4's tools on the new fonts** (`v2/`):
  - `hijri-fit.py` and `moon-fit.py`: the same results.
  - `date-fit.py`: the same failures; only its font names and a tie among equal forms differ.
  - `window-fit.py`: **FAIL**, finding 2. "جمعرات ۶" came 2.9 px from its day (margin 3), and two estimates were flagged: "figure '۷''s left side bearing is 0.058 em, the face takes 0.002" and "figure '۶''s left side bearing is 0.041 em, the face takes 0.066".
- The Urdu shots wait for Task 3, whose builder writes the first Urdu feed (the plan).

### Ruling 51 item 2: the Moon window's estimate

- **Red** (`r51/window-fit-red.txt`): on the v2 fonts, before the change, `window-fit.py` fails as above: one date and two estimates.
- **The change** (`tools/face-moon.py` `DAY_LEAD_NOTO`, with a comment saying why): ۶ joins ٦'s class (0.041 em), since it now draws ٦'s glyph, and ۷ joins ٩۹'s class (0.054 em, under its 0.058). The ("۶", 0.066) class goes. `python3 tools/face-moon.py` regenerated `watchface.xml`: the Moon date window's `scaleX` and `scaleY`, two lines, the same two lines as the remedy measured in the first run (`remedy/watchface-remedy.diff`).
- **Green** (`r51/window-fit-green.txt`): every weekday keeps clear of its day, Urdu's extended days nearest 3.4 px again, and every estimate checks.
- Only the 6th and the 7th of a month in Urdu's extended digits draw differently. No committed shot draws one, and the same-session shots (Ruling 51 item 1, above) show the Moon's samples unchanged.

### Ruling 51 item 3: the attribution

- `docs/ATTRIBUTION.md`'s Noto Sans Arabic entry said the face bundles the files "unmodified". It now says they are modified versions, in three ways, by `subset-noto.py`:
  - subset to the characters the face can be sent, with every layout feature kept;
  - Urdu's ۴ ۶ ۷ mapped to the glyphs of the font's own Urdu forms in every weight, and both sets of Arabic-Indic digits mapped to the font's own proportional figures in the Light;
  - renamed files.

  No glyph is redrawn, and the fonts stay under OFL 1.1.
- **No Reserved Font Name.** The licence's copyright line, as `docs/licences/OFL-1.1-NotoSansArabic.txt` and upstream's `OFL.txt` (byte for byte the same) give it, is "Copyright 2022 The Noto Project Authors (https://github.com/notofonts/arabic)", with no "with Reserved Font Name". The name tables of the three upstream files and the three v2 files (IDs 0 and 13, checked with the pinned fontTools) name none either. The only "Reserved Font Name" in the file is the licence's own definition. So the modified fonts may keep their internal names ("Noto Sans Arabic"), and they do.

## The errata from the planning reviews

1. **Resume, do not restart.** The close builds on the first run's WIPs (`c433b438`, `1f2f64bc`, `2ea2e131`, `9467cb1d`, Steps 1–9 built, `test.sh` green). Ruling 51's items were added as WIPs on top, test-first, and then all were folded into the plan's two commits by a soft reset to `ff28cd5a`.
2. **Plan 1 is closed, so face changes go through the tools.** Every face change here went through `tools/face-centre.py` and `tools/face-moon.py`, and the four `--check` stay up to date.
   - The always-on lit share stays under 15%: see Ruling 51 item 1's shots. A form is now drawn at its old size or smaller, in the same place, so no frame lights more than before.
   - Ruling 46's moon window, Ruling 42's tap and reading order, and Ruling 43's PAD are untouched: no slot, slot box, order or PAD changed, and `moon-fit.py`'s PAD count passes. The new Groups and boxes are inside slot 2's own, as the Hijri steps are. A right-to-left wearer value in a glyph-splitting font is still plan 5's device check.
3. **`emulator-5558` is shared.**
   - The first run took the `wear-5558` lock twice: for the read-only font pull (released at once), and for Step 8 (the install, the pass, the evidence shots, the restore). The close took it once, for Ruling 51 item 1's shots.
   - Each time I recorded the watch's state and pulled the installed APKs and the stored feed first (`device-state.txt`, `r51/device-state.txt`): automatic time and zone, Africa/Johannesburg, font scale 1.0, TalkBack off, English, battery 100%. The face was Arch, Amber, slots empty, the base commit's face (its `li.xml` equals `ff28cd5a`'s `watchface.xml`), with a stored sample written 24 October 23:34.
   - Each time I put it back exactly (`restore.sh`) and checked it: the watch app and face APKs on the device are byte for byte the found ones, `files/feed.json` is the found bytes, and every recorded setting is as found. The face is Arch, Amber, slots empty, English, the stored sample ("MON 19 OCT"). Then I released the lock.
   - The console (`adb emu`) never hung. The emulator crashed once in the first run (above) and was booted again by serial with the project's script, never with `pkill`.
4. **Gradle flake.** Neither came up: no EOFException in `:shared:testAndroidHostTest`'s worker and no `EngineBudgetBenchmarkTest` failure.

## Review round 1's fixes (25 October)

The two reviews (spec, behaviour and evidence; code quality, platform APIs, tests, privacy, accessibility, hygiene) found no Critical and no code to change. They found three Important items:
1. and 2. **Ruling 51 item 1 is not met as worded**, and no Lead line answers it yet. Both reviews found this, and both say the choice of table is the Lead's. The section then called "For the Lead" (now "Ruling 51a: why the table by length went", above) said plainly which three points were not met and why. It weighs a fifth table, the non-monotonic one the spec reviewer found, and adds how often each table changes a phone's date size overnight (`r51/tables.py` and `r51/tables.txt`, re-run: the four tables measured before give the same figures). The face kept the three steps built until the Lead ruled (Ruling 51a, below).
3. **The committed reference pass was stale.** Four frames in `2026-10-19-face-pass/` were shot before Ruling 51, and the face no longer draws them. Plan 2 Task 3 Step 4 compares with "Task 2's pass", which had not been committed. So this round:
   - Shot the full pass of the face as committed on emulator-5558, under the `wear-5558` lock (04:49–05:12 SAST), with the debug face built from this tree and installed by `adb -s emulator-5558 install -r` (`review1/pass-vs-committed.txt`).
   - Found that against the committed pass, only Arch's four Arabic (Libya) frames differ beyond the emulator's noise, and only in the Gregorian line's band (x 127–322, y 134–173). That is the sample's 15-character "الأحد 18 أكتوبر" at 13 units. Every other frame gives the same counts, shot for shot, as the close's full pass before Ruling 51. The two new 14:58 frames equal the close's same-session shots of this face in 0 px.
   - Committed the four new frames in place of the old ones: `pass-arch-ar-ly-12h-1458-awake.png`, `-1458-aod.png`, `-1258-awake.png` and `-2258-awake.png`. Their mockup side-by-sides in `task-10/` were remade (`review1/vsmockup.py`), with the committed mockup panel kept, since faces.js is unchanged. The script gives back the committed pictures from the committed shots, pixel for pixel. The before and after is `review1/arch-arabic-before-after.png`.
   - Measured the always-on Arabic Arch frame at 10.02% lit, against 10.26% before (`review1/lit-share.txt`).
   - Put the watch back as found (`review1/device-state.txt`).

**What Task 3 compares with** (as Ruling 51a left it). Plan 2 Task 3 Step 4's "Task 2's pass" is `docs/superpowers/reports/2026-10-19-face-pass/pass-*.png` as committed with Ruling 51a. Month rule A draws the sample at 14 units again, so Ruling 51a put this round's four Arch Arabic frames and their side-by-sides back to the bytes committed at `ff28cd5a` (Task 10's): the face as committed draws them 0 px from `ff28cd5a`'s face in one session, and within 1 level of those files (`r51a/same-session.txt`). The other 47 are as they were. Against the committed shots, a new session is within 3 levels on Arch, Ring and Sun, not 0 px. The Moon's seconds hand, its moon and the step count move between runs, as `task-12/passdiff.py` sets aside (Ruling 51 item 4, and Task 12's minor).

**The minors, left and recorded in the ledger:**
- Bengali with Western digits (a pair in `FACE_PAIRS`) is in no tag of the forms file. The spec reviewer measured it, and it fits.
- `date-fit.py` names one of several equal forms by the hash seed.
- `window-fit.py` labels Urdu's extended set "Arabic-Indic".
- `r51/device-state.txt`'s trailing spaces. "Checks run" below now says so, where it had said `git diff --check` was clean.

## Review rounds 2 and 3's fixes (25 October)

Both rounds' two reviews found no Critical and no code to change. Their Important items are the Lead's:
1. **Ruling 51 item 1 is still not met as worded**, and no Lead line chooses the table (rounds 2 and 3, both lenses). Round 2's fix corrected `face-centre.py`'s note on what a table by length can keep at 14 units. Round 3's spec reviewer found that the only one-word month under 7 px at 14 units is أغسطس, so a rule that also tests the month could keep the sample at 14 units, and nobody had measured it. This round measured two such rules (`review3/`), and "For the Lead" weighed them beside the five tables. They are not tables by length, so they contradicted item 1's words: the ruling was kept, and the face kept the three steps built until Ruling 51a took rule A ("Ruling 51a (25 October)", below).
2. **emulator-5558 is hung and the `wear-5558` lock is held for the Lead** (round 2's spec lens, 05:28; round 3 polled it from 05:58 to 06:10). It needs the Lead: the hang cleared, then `restore.sh` with the found state round 2 saved. Until then no reviewer has shot the four re-shot pass frames again on the device. Offline they are consistent: they differ from `ff28cd5a`'s only in the Gregorian band, and the 14:58 frames equal `r51/shots/new` in 0 px. (Since then the Lead stopped the hung emulator and cleared the lock, and Ruling 51a's run booted it again and put it back: "Ruling 51a (25 October)".)

Round 3's minors fixed here (comments and evidence, nothing a build or test reads):
- `face-centre.py`'s note said the 18-character step holds "every month name but the Levantine ones". It holds every form whose month is one word; the longer forms are the Levantine and Iraqi two-word months.
- `window-fit.py`'s docstring said the first two digit sets are drawn in Noto. It is the two Arabic-Indic sets (`NOTO_ZEROS`); Western is Manrope.
- `feedtext.sh` said it is not committed. It is committed evidence.
- The table of tag forms said ar-IQ's two-word months hold U+00A0. Only تشرين الأول does.
- `r51/shotgap.txt`'s base-face line gave the model at 13 units (47.7 px) beside the 14-unit shot. `shotgap.py` now takes `units=`, and the line is measured again from the committed shot at 14 units: 41.1 px, as the table above says. Its docstring now says the script prints the ink's x extent, not its height.

The minors left are recorded in the ledger.

## Ruling 51a (25 October)

Ruling 51a (the ledger) amends Ruling 51 item 1 to month rule A ("Ruling 51a: why the table by length went", above). Its order was: first prove an Arabic literal in a face expression on emulator-5558, then the Groups, `date-fit.py`'s three readers, the shots, spec 3.4, the generator's note and this report. If the runtime would not compare the literal, the fallback was the non-monotonic table by length. It compares it, so there was no fallback.

### 1. The Arabic literal, proven on emulator-5558 (`r51a/proof/`)

- A scratch copy of `72c7bcb5` (never the worktree), whose `watchface.xml` alone was edited by `proof-edit.py`: the 14-unit Group shown for `textLength(…) <= 18 && subText([COMPLICATION.TEXT], clamp(textLength(…) - 5, 0, 99), textLength(…)) != "أغسطس"`, the 13-unit Group for the same with `==`, and the 11.25-unit Group as it was. It was built with `:watchface:assembleDebug` (signed with the face's test key) and installed with `adb -s emulator-5558 install -r`.
- `proof.sh` rewrote Arch's Arabic Gregorian line to six forms on the Saudi sample at 14:58 and shot each awake and always-on. `proofmeasure.py` read the size each was drawn at from its ink's width, against `date-fit.py`'s model at 14, 13 and 11.25 units, and compared each with an earlier shot of the same text whose size is known (`proof/proof.txt`):

| Form | Characters | Rule A | Drawn, awake and always-on | Against an earlier shot |
|---|---|---|---|---|
| الأحد ١٨ أكتوبر (the sample) | 15 | 14 | 14 | the base face's sample (`r51/shots/old`), 0 px; Ruling 51's 13-unit shot, different |
| السبت ١ أغسطس | 13 | 13 | 13 | Ruling 51's 14-unit shot, different |
| الخميس ١٠ أغسطس | 15 | 13 | 13 | Ruling 51's 13-unit shot, 0 px |
| الخميس 10 أغسطس | 15 | 13 | 13 | none |
| الأربعاء ٣٠ ديسمبر | 18 | 14 | 14 | none |
| الثلاثاء ١٠ تشرين الأول | 23 | 11.25 | 11.25 | Ruling 51's 11.25-unit shot, 0 px |

So the runtime compares `subText()` of a complication's text with an Arabic literal, with `==` and `!=`, awake and always-on, and both directions are told apart: a 13-character August form drawn smaller than under the table by length, and a 15-character October form drawn larger.

### 2. Test-first, then the Groups

- **The tests first** (`tools/watchface/date-fit-test.py`, 20 tests, listed in "What is built"):
  - red 1 (`r51a/date-fit-test-red1.txt`): before `date-fit.py`'s readers existed, 18 of the 20 fail: 17 error on the missing readers (`'tuple' object has no attribute 'line'`, no `shows` or `face_steps_check`), and one fails because the face holds no August test. The two that pass are the four tags in the forms file that write أغسطس, and the face's rows against the table's, which the old readers already agreed on;
  - red 2 (`r51a/date-fit-test-red2.txt`, 9 failures): with `date-fit.py`'s readers written, and `ARCH_GREGORIAN_STEPS` holding Ruling 51's three steps in the new shape (a temporary line, never committed), the rule's tests fail as they should ("the sample … 13.0 != 14", "السبت ١ أغسطس 14.0 != 13", Urdu's اگست at 13 units), and the face's tests fail on the three-step Groups;
  - red 3 (`r51a/date-fit-test-red3.txt`, 6 failing): with the table set to rule A and the face not yet regenerated, the rule's tests pass and the face's fail: "watchface.xml draws … [('13', …), ('18', …), ('more', …)], and ARCH_GREGORIAN_STEPS says [('long', …), ('august', …), ('rest', …)]". `date-fit.py`'s whole run failed the same way (`r51a/date-fit-red.txt`): its measure of rule A passed, and its face check found 21,267 forms in another Group than their step's.
- **The Groups.** `dates()` writes one Group a step with `gregorian_step_tests()`, and `python3 tools/face-centre.py` regenerated `watchface.xml`. Its diff is the three Groups and the comment above them: the names, the 11.25-unit and 14-unit boxes in each other's places, and the alpha expressions, which are the proof face's three, box for box and character for character.
- **Green** (`r51a/date-fit-test-green.txt`, `r51a/date-fit-green.txt`): 20 of 20 tests pass, and `date-fit.py` passes with the figures in Step 4's table. The four generators' `--check` are up to date. `window-fit.py`, `moon-fit.py` and `hijri-fit.py` give `r51/`'s outputs byte for byte, and `slot-fit.py` passes.
- **The generator's note** above `ARCH_GREGORIAN_STEPS` now says the rule, why August, what each phone keeps and how often its size changes, that no table by length could keep the sample, and Task 7's check of the phone's spelling. Spec 3.4's Arch "As built" names the Gregorian line's rule and its two size changes a year. `tools/watchface/README.md` names the rule and `date-fit-test.py`.
- `r51/shotgap.py` and `r51/tables.py` read Ruling 51's step shape, (most characters, size). They are the record of that run, so they stay as they are and run on `72c7bcb5`'s tree. `r51a/shotgap.py` is the same measure on the new steps.

### 3. The face as committed on emulator-5558 (`r51a/ab3.sh`, `r51a/shots/`)

- The debug face built from this tree (`:watchface:assembleDebug :watchface:assembleRelease :watchface:lint`, lint "No issues found"; `res/li.xml` = `watchface.xml` in both APKs; no `classes.dex`) was installed with `adb -s emulator-5558 install -r`. The watch app found installed was kept.
- **Ruling 51's six cases, shot again** (awake and always-on, the Saudi sample at 14:58, `feedtext.sh`; `r51a/shotgap.txt`, `r51a/same-session.txt`):

| Case | Characters, step | In the shot | Model | Under Ruling 51's three steps (shot) |
|---|---|---|---|---|
| "الأحد ١٨ أكتوبر", the sample | 15, rest, 14 units | 42.4 px | 41.1 px | 49.0 px at 13 units |
| "السبت ١ أغسطس" | 13, august, 13 units | 23.5 px | 23.0 px | 16.5 px at 14 units |
| "الخميس ١٠ أغسطس" | 15, august, 13 units | 9.1 px | 8.7 px | the same shot, 0 px |
| "الثلاثاء ١٠ تشرين الأول" | 23, long, 11.25 units | 24.2 px | 21.2 px | the same shot, 0 px |
| "الخميس 10 تشرين الأول" | 21, long, 11.25 units | 14.9 px | 11.6 px | the same shot, 0 px |
| "الخميس ٣٠ تشرين الثاني", the widest | 22, long, 11.25 units | 12.4 px | 8.5 px | the same shot, 0 px |

  The sample's two shots are 0 px from the base face's (`r51/shots/old/arch-ar-sa-1458-*.png`) and from the proof face's. The model is never more generous than the device. `r51a/gregorian-arch-arabic.png` puts the two cases whose size changed beside their shots under the three steps.
- **The committed pass's four Arch Arabic frames, and Arch's other samples, in one session against `ff28cd5a`'s face** (built from `ff28cd5a`'s tree), at the pass's battery of 82 (`r51a/same-session.txt`): all eight frames, `pass-arch-ar-ly-12h-1458-awake`, `-aod`, `-1258-awake`, `-2258-awake`, `arch-ar-sa-1458-awake`, `-aod`, `arch-en-1458-awake` and `-aod`, are 0 px apart, the same PNG byte for byte. Against the files committed at `ff28cd5a` the four pass frames are within 1 level (the emulator's noise between sessions), and against review round 1's (`72c7bcb5`) they differ in the Gregorian band alone. So the four files and their `task-10/` side-by-sides are back to `ff28cd5a`'s bytes, and `2026-10-19-face-pass/README.md` says why.
- **The always-on lit share** (`r51a/lit-share.txt`): every always-on frame of the session is 9.92% to 11.76% as the face draws it, under Google's 15%. The pass's Arch Arabic frame is 10.26%, as on `ff28cd5a`'s face (it was 10.02% at 13 units). A form is drawn at its base size or smaller, in the same place, so no frame lights more than on the base face, and plan 1's worst moments (Arch's highest 13.52%) stand.
- **Validators** (`r51a/validate.log`, `tools/watchface/validate.sh --no-build`): wff-validator 1.7.0 PASSED at format 2; the Push validator's 10 checks on the debug face and on the release's copy signed with the test key.

### 4. The errata, in this run

1. **Resume, do not restart.** The run started from `72c7bcb5`, the plan's two commits and the three review-fix commits as they were. Only Ruling 51a's items changed; everything the reviews closed is kept (the v2 fonts, `DAY_LEAD_NOTO`, the attribution, the forms file and its test, the minors' fixes). WIPs `68f5c03b` (the rule, the readers, the tests, the face) and `1d60af35` (the evidence, the pass frames, spec 3.4), and a third for this report and plan 2 Task 7's note, were folded into one commit, "Face: … (Ruling 51a)", by a soft reset to `72c7bcb5`.
2. **emulator-5558 was off.** Under the `wear-5558` lock, it was booted with `tools/watchface/emulator.sh` (06:28) and put back to plan 1's state first with `restore.sh` and the session scratchpad's `p22r2spec/found`: after the boot it held the found APKs but automatic time off and the stored Libyan sample of the hang, and after `restore.sh` the APKs and `feed.json` were byte for byte the found ones and every recorded setting as found (`r51a/device-state.txt`). It was put back the same way after the proof and after the shots.
3. **Plan 1 is closed.** The only face change went through `tools/face-centre.py`; the four `--check` are up to date. The always-on lit share stays under 15% (`tools/watchface/lit.py undither`, 11.76% at most). Ruling 46's moon window, Ruling 42's slot order (7, 0, 1, 2, 3, 6, 4, 5) and Ruling 43's PAD are untouched: only Arch's right-to-left Gregorian Groups and a comment changed, inside slot 2's own Condition.
4. **emulator-5558 is shared.** The lock was taken twice, for the proof (06:27–06:40) and for the shots (06:49–07:06), and released after each restore. Every install was `adb -s emulator-5558 install -r`. The console answered every time; the emulator's log shows gfxstream "Failed to find ColorBuffer" errors during the second turn, as before its earlier hang, but it never hung or crashed.
5. **The Gradle flake.** Neither came up.

## Not changed, for the Lead to see

- `PhoneForms` lives in androidMain, so Task 7's harness can call it on the phone's ICU. R8 drops it from release, since nothing in release calls it.
- `date-fit.py` measures Bengali unspaced, as Minikin draws it. `moon-fit.py`'s countdown applies the curve's tracking to Bengali too, which only makes it more cautious.
- `window-fit.py` now needs `tools/watchface/phone-forms.json`, which is committed. `moon-fit.py` falls back to the samples without it; `window-fit.py` does not.
- `date-fit.py`'s model is cautious by several pixels near Arch's stroke, measured on the device above. That is the same model `hijri-fit.py` uses for the Hijri line's 7 px, so the two lines are held to one standard.
- `date-fit.py` takes a step by `len(text)` and `text.endswith()`, and the face by `textLength()` and `subText()`. Every character the forms hold is in the Basic Multilingual Plane, the no-break space included, so the counts agree; since Ruling 51a `date-fit.py` fails a form that is not.

## The evidence (`2026-10-23-phone-forms-fit/`)

| File | What it holds |
|---|---|
| `v1/*-fit.txt`, `v2/*-fit.txt` | The four fit tools' output on the fonts as they were (Step 4) and on the v2 fonts before Ruling 51 (Step 8) |
| `greg-fails.txt`, `greg-fails-all.txt`, `gregfails.py` | Finding 1: every Arabic Gregorian form under 7 px on Arch at one size, grouped and one by one |
| `gregsizes.txt`, `gregsizes.py` | Finding 1: the least room at one smaller size for all, for the ruling |
| `gregcheck.txt`, `gregcheck.py` | The model's Gregorian ink against the committed emulator shots |
| `gregorian-on-the-emulator.txt`, `shotgap.py`, `gregorian-arch-*.png`, `gregorian-arch-arabic.png`, `feedtext.sh`, `sheet.py` | Finding 1 on emulator-5558 before the ruling: three failing forms and the sample, and the room each had on the device |
| `call7.txt`, `call7.py` | The Lead's call 7: the under-a-minute drafts in degrees on the Moon |
| `fontforms.txt`, `fontcheck.txt`, `fontcheck.py` | Steps 6–7: the Urdu `locl` and `pnum` forms, and the check of the v2 fonts (red on the old ones, then PASS) |
| `validate.log` | Both validators on the v2 face |
| `pass-vs-committed.txt` | The full pass against the committed one |
| `same-session.txt`, `ab.sh`, `abdiff.py`, `urdu-digits-old-new.png` | The base face against the v2 face in one session, and Urdu's digits on both |
| `remedy/` | Finding 2's remedy as the first run measured it, not applied then (Ruling 51 item 2 applied the same statement) |
| `device-state.txt`, `restore.sh` | emulator-5558 as found and as left, in the first run |
| `r51/date-fit-red.txt`, `r51/date-fit-red2.txt`, `r51/date-fit-green.txt` | Ruling 51 item 1: `date-fit.py` red (the one size, then the steps written at one size), then green on the three steps |
| `r51/bylen.py`, `r51/bylen.txt`, `r51/tables.py`, `r51/tables.txt` | Ruling 51 item 1: the least room of each length at each size, and the candidate tables, with the days of 2027 each step draws by tag and the overnight size changes (the non-monotonic table and the changes added in review round 1's fixes) |
| `r51/window-fit-red.txt`, `r51/window-fit-green.txt`, `r51/hijri-fit.txt`, `r51/moon-fit.txt` | Ruling 51 item 2, red then green, and the two guards |
| `r51/validate.log` | Both validators on the face as committed |
| `r51/shots/`, `r51/shotgap.py`, `r51/shotgap.txt`, `r51/gregorian-arch-arabic.png`, `r51/ab2.sh` | Ruling 51 item 1 on emulator-5558: the six cases awake and always-on, and the room each has |
| `r51/same-session.txt`, `r51/lit-share.txt`, `r51/litshare.py` | The base face against the new one in one session, and the always-on lit share |
| `r51/device-state.txt`, `r51/state.sh` | emulator-5558 as found and as left, in the close |
| `review1/pass-vs-committed.txt` | Review round 1's fixes: the full pass of the face as committed against the committed pass, read against the close's |
| `review1/arch-arabic-before-after.png`, `review1/sheet.py`, `review1/vsmockup.py` | The four Arch Arabic frames, committed before and now, and the script that remade their mockup side-by-sides |
| `review1/lit-share.txt`, `review1/device-state.txt` | The always-on Arabic Arch frame's lit share, and emulator-5558 as found and as left |
| `review1/nonmono-date-fit.txt` | The non-monotonic table, generated on a scratch copy and measured by `date-fit.py`'s whole run |
| `review3/monthrule.py`, `review3/monthrule.txt` | Review round 3: two rules that test for August as well as the length (not tables by length), measured with `date-fit.py`'s model, with the built table first as a check; Ruling 51a took rule A |
| `r51a/proof/` | Ruling 51a's first step: the proof face's edit (`proof-edit.py`), its shots on emulator-5558 (`proof.sh`, `shots/`) and the sizes read from them (`proofmeasure.py`, `proof.txt`) |
| `r51a/date-fit-test-red1.txt`, `-red2.txt`, `-red3.txt`, `-green.txt`, `r51a/date-fit-red.txt`, `r51a/date-fit-green.txt` | Ruling 51a test-first: `date-fit-test.py` red three times, then green; `date-fit.py` red on the face before its regeneration, then green |
| `r51a/ab3.sh`, `r51a/shots/`, `r51a/shotgap.py`, `r51a/shotgap.txt`, `r51a/gregorian-arch-arabic.png` | The face as committed on emulator-5558: Ruling 51's six cases again, the pass's four Arch Arabic frames and Arch's samples on it (`new/`) and on `ff28cd5a`'s face (`base/`) |
| `r51a/same-session.txt`, `r51a/lit-share.txt`, `r51a/validate.log`, `r51a/device-state.txt` | The comparisons, the always-on lit share, both validators, and emulator-5558 as booted, found and left |

## Checks run

- `FaceFormsTest`: red (compile, then "missing" 3 of 3), then green 3 of 3. Shared's host tests go from 2,083 to 2,086.
- `date-fit.py`: red on Arch's one size (2,107 failures, then 2,106), then green on the three steps. `window-fit.py`: red on the v2 fonts before Ruling 51 item 2, then green. `hijri-fit.py` and `moon-fit.py`: green, unchanged.
- `scripts/test.sh` on the final tree: exit 0. Versions 1.2.0 (38) and (100038), bump PASS. Shared host 2,086 and iOS 2,027; widgetcore 111/109; watchcore 112/110; wearApp 143 (`watchface.xml` is its test input, and it ran again on the new file). 0 failures, no EOFException.
- `:watchface:assembleDebug :watchface:assembleRelease :watchface:lint :wearApp:assembleDebug --rerun-tasks`: green. Lint: "No issues found."
- `tools/watchface/validate.sh --no-build`: wff-validator 1.7.0 PASSED at format 2, and the Push validator's 10 checks on the debug face and on the release's copy signed with the test key (`r51/validate.log`).
- The CI face step by hand: the four generators' `--check` are up to date, neither APK holds a `classes.dex`, and `res/li.xml` is `watchface.xml` byte for byte in both.
- `tools/i18n-check.py` is clean. `git diff --check` is clean but for `r51/device-state.txt`'s two "locales: null / " lines. Their trailing space is the empty `persist.sys.locale`, as `state.sh` printed it (review round 1's minor, left as recorded).
- No commit has a `Co-Authored-By` line, and all are Mohamed's Taqwa identity. Nothing is pushed.
- Untouched: the physical phone KB2604110095, every other emulator, the iOS simulators (bar Kotlin/Native's own test run), release keys, and other worktrees and branches.
- No dependency, permission or app string changed. Rulings 39, 41, 42 and 43 are untouched: no privacy line, slot, slot box, order or PAD changed, and the only copy changed is `docs/ATTRIBUTION.md`'s Noto entry (Ruling 51 item 3).

**Ruling 51a's checks** (25 October, on the final tree):
- `python3 -I tools/watchface/date-fit-test.py`: red three times (`r51a/date-fit-test-red1.txt` to `-red3.txt`), then 20 of 20.
- `date-fit.py fonts=<emulator-5558's NotoSansBengaliUI-VF.ttf, 2df70a74…>`: red on the face before its regeneration (`r51a/date-fit-red.txt`), then green (`r51a/date-fit-green.txt`). `window-fit.py`, `moon-fit.py` and `hijri-fit.py`: `r51/`'s outputs byte for byte; `slot-fit.py` passes.
- The four generators' `--check` (`face-arch`, `face-sun`, `face-moon`, `face-centre`): up to date.
- `./gradlew :watchface:assembleDebug :watchface:assembleRelease :watchface:lint :wearApp:assembleDebug :wearApp:testDebugUnitTest`: green; lint "No issues found"; wearApp 143 of 143 on the new `watchface.xml`, and again with `--rerun`, beside `FaceFormsTest` 3 of 3. Neither face APK holds a `classes.dex`, and `res/li.xml` is `watchface.xml` in both.
- `tools/watchface/validate.sh --no-build`: wff-validator 1.7.0 PASSED at format 2; the Push validator's 10 checks on the debug face and on the release's copy signed with the test key (`r51a/validate.log`).
- `scripts/test.sh`, twice: exit 0. Versions 1.2.0 (38) and (100038), bump PASS. This run changed no Gradle input but `watchface.xml`, so its test tasks were up to date or from the build cache: shared host 2,086 and iOS 2,027, widgetcore 111/109, watchcore 112/110, all 0 failures; wearApp's 143 ran on the new file. No EOFException and no `EngineBudgetBenchmarkTest` failure.
- `tools/i18n-check.py`: clean. `git diff --check`: clean (`r51a/device-state.txt` has no trailing space).
- One commit, "Face: … (Ruling 51a)", Mohamed's Taqwa identity, no `Co-Authored-By`; nothing pushed. emulator-5558 under the `wear-5558` lock only, put back as found; KB2604110095, every other emulator, the simulators, release keys and other worktrees untouched.
