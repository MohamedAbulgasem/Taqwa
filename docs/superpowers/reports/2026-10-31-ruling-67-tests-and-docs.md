# Ruling 67 item 2: R66's tests hold what they claim, and the docs say what was built

Lane P, on `watch-feed`, 31 October 2026. Base `6ba0afc1` (`git merge --ff-only watch-companion`
from `c9d003fe`; `watch-feed` had no commits of its own). This is no plan task: it is Ruling 67
item 2 in the ledger, tests and docs only. No drawn pixel moves: no file under `wearApp/src/main`,
`watchcore/src/main` or `shared/src/commonMain` changes, and `QiblaFitTest`'s tables print what
they printed; under `iosApp/` only a test changes, `WatchScreenWordsTests.swift`, in review round
1's fix (`d092200d`). Each new or tightened test was seen red first on a mutant it exists to
catch, in this worktree, and the mutant taken out again (`git checkout`, the model restored and
`qibla-ink.py --check` clean). No device, emulator or simulator was used by hand, and no lock taken.
Nothing is pushed; Taqwa identity; no trailer.

Four commits: the task's two, the WIPs (`ce1cafc1`, `b29f5422`, `b0eda938`, `a8006360`, `bbd6a08d`)
folded into them by a soft reset, then one for each review round's fix:

- `779d358c` "Wear: R66's tests hold what they claim, and the docs say what was built (Ruling 67)";
- `50732b35` "Store copy: P5.10's left minors and its code lens's nine, the platform rule in any case (Ruling 67)";
- `d092200d` "Wear: a caption's space is Manrope's and no test pins a short form, after review" (round 1);
- `ea6ac807` "Wear: HostInk draws a no-break space in the font of what stands before it in its run, as
  the watch does, after review" (round 2).

The tables and the Checks below are the first commit's; each round's section gives its own.

## What changed

| File | Change |
|---|---|
| `wearApp/src/test/.../qibla/QiblaFitTest.kt` | T1's KDoc says what binds it, and T1 prints the sweep's count; `t2TheDialsLettersGrowAsTheirLinesSay` (new); `t3TheDimmedSweepTakesTheBearingsWhoseInkReachesNearestTheTicks` (new); `t6TheCaptionGapIsNeverTighterThanAtTheDrawingsSizeInEveryLanguage` (new; the Latin test's KDoc says it is the Latin bound); `t7ADirectionMarkHasNoInkAndTakesNoRoom` (new). |
| `wearApp/src/test/.../qibla/QiblaSweep.kt` | `bearings` by the ink's reach (`bearingReach`, new), not the advance; the KDoc's count and bearings. |
| `wearApp/src/test/.../qibla/HostInk.kt` | A format character (a direction mark, the Arabic letter mark) has no ink and no advance. (The commit also gave a modelled font a path that set a caption from its words and the font's space; it was never reached, since the space is Manrope's, and review round 1 took it out: see the last section.) |
| `wearApp/src/test/resources/.../fallback-ink.json` | Regenerated: the captions' words in the Naskh fonts and Bengali's 800; every run it held is unchanged. |
| `tools/watchface/qibla-ink.py` | Reads the captions, the short forms' units and the dial letters from the phone's strings and `WatchWords.kt` (the model it writes for the old runs is byte for byte the old one); checks each caption set from its words and the font's own space against it shaped whole. |
| `tools/watchface/README.md` | `qibla-ink.py`'s row, with its regeneration step. |
| `shared/src/commonTest/.../WatchWordsTest.kt` | The short forms held by their shape, not each form. |
| `docs/superpowers/reports/2026-10-31-wear-qibla-short-detail.md` | The sweep's count, the bearings, T6 and T8 as they are now; a closing Ruling 67 section. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | Task 7 Step 1's two Qibla details in the short forms; a Ruling 67 as-built bullet. |
| `tools/store-listing-check.py`, `tools/test_store_listing_check.py` | The platform rule in any case, Samsung on the App Store's list; two tests. |
| `docs/store/listing/*.md` (seven) | A note under Play's "Release notes (500)": 1.0.0's, the record. |
| `README.md` | Roadmap slice 5: "Coming in 1.4.0". |
| `CONTRIBUTING.md` | "Settings › About Taqwa › Attribution & licences". |
| `docs/superpowers/plans/2026-10-24-watch-companion-5-ship.md` | Task 4's files name the root README's build steps; M13 turns slice 5 to Done in the squash. |
| `docs/superpowers/reports/2026-10-31-watch-store-copy.md` | The left minors and the code lens's nine, with what became of each. |

## Ruling 67 item 2, as built

1. **The report's sweep count.** 48,896 series of 116 font sizes, 5,671,936 rows, 708,992 of them on
   the turning dial; R66's report said 1,417,984. Corrected there, in `QiblaSweep`'s KDoc and in plan
   3's as-built note, and T1 prints it (`[measure] the sweep: 48896 series, 5671936 rows, 708992 of
   them on the turning dial`), so the next report reads it from the run. The count is the same after
   item 2's bearings: every form still sweeps seven distinct bearings.
2. **The dimmed dial's bearings by their measured ink, naming ২০০°.** `QiblaSweep.bearings` takes the
   five bearings whose dimmed detail (20,000 km at 10, placed by `QiblaFit.placed`) keeps least room
   inside the ticks, as T3 judges a line (`Judge.tickGap`), then 23 and 359. Bengali's digits share one
   advance (0.61 em), so the advance took ১০০ to ১০৪; by the ink it takes ২০০ to ২০৪ in both font
   variants. Not by the ink's width, which would have taken ৩০০ to ৩০৪ (৩'s ink starts 0.030 em from
   its pen, ২'s 0.057), because ২ runs 0.027 em below the baseline where ৩ stays 0.033 above it, and
   the detail sits in the dial's lower half, so ২ reaches nearer the ticks: 15.01 units inside them,
   against 15.25 for the advance's choice. The new bearings elsewhere: English, French, Turkish,
   Indonesian and Bengali in Western digits 300, 306, 309, 304, 340; Arabic in Arabic-Indic digits
   333, 337, 338, 336, 339; Arabic and Urdu in Western digits 200, 300, 206, 209, 260; Urdu in Extended
   digits 333, 334, 343, 344, 337. The test holds that no bearing 0 to 359 reaches nearer the ticks than
   the nearest swept, in every form and variant, and names ২০০°. T1 to T5 stay green on the new rows.
3. **T6's caption descent in all seven languages.** The Latin test is a bound (Manrope's deepest
   descent in any caption against the highest ascent of any detail), which the Arabic, Urdu and
   Bengali captions cannot use: the elegant Naskh's ي reaches 0.46 of its size below the baseline, and
   the bound would then leave less than none at the drawing's size. So the new test measures each
   caption on its glyphs: the room from the lowest ink of its last line, as the page breaks it in its
   box (`QiblaType.captionBox`, its size as TextAutoSize steps it, Android's simple strategy), to the
   highest ink of the detail under it, across the whole width, for every caption the page draws over
   a detail (the bearing in the dimmed sweep's bearings, "Facing qibla" and the tilt notice over the
   distance; "Compass unavailable" and "No compass sensor" over the bearing and the distance; Low's
   titles have no detail), every figure, every font size (0.85 to 2.0, each cap reached), every size
   the detail is tried at (from 9), ten forms and both variants. The caption's ink is taken at its
   line's largest size, which the half anchor moves it by; TextAutoSize may draw it smaller, so
   higher, never larger. It holds: more than none at the drawing's size, and never less as the font
   grows. The least room at the drawing's size, by form and variant:

   | Form | UI | Elegant |
   |---|---|---|
   | en | 3.05 | |
   | fr, tr | 2.82 | |
   | id | 3.05 | |
   | ar, Arabic-Indic | 2.50 | 1.15 |
   | ar, Western | 2.50 | 0.59 |
   | ur, Western | 4.25 | 2.13 |
   | ur, Extended | 4.03 | 2.13 |
   | bn, Bengali | 2.74 | 2.74 |
   | bn, Western | 2.58 | 2.58 |

   The least of all is Arabic's tilt notice, "أبقِ معصمك مستويًا", in the elegant Naskh over "100 كم":
   0.59 units, counted across the whole width (the glyphs that come nearest do not stand over each
   other). Two measures were tried first and set aside, with the probe's numbers: the caption at the
   size TextAutoSize draws it, whose room at the drawing's size can be the larger, so that a tilt
   notice shrunk at 1.0 and grown at 1.05 read 0.43 units tighter than at 1.0 (English "Hold your
   wrist level"), the line's growth rather than the layout's; and the least distance between glyph
   boxes in two dimensions, which stayed above 2.1 units everywhere but grew less than the font where
   the detail grows past its caption (English, 0.15 units at 1.3) and where a title breaks anew
   (Turkish "yok" alone at 1.1, Urdu "نہیں" at 1.2). Neither is what T6 claims; the room is, and it
   holds in all seven languages.

   HostInk's model holds the captions for it: `qibla-ink.py` reads them from the phone's strings and
   `WatchWords.kt` (the feed's words) and shapes each word whole in the Naskh fonts and Bengali's 800.
   HostInk sets each word whole from the model and the space between two words in Manrope (0.200 em),
   as the watch draws it: U+0020 is not one of minikin's sticky marks, and minikin gives a character to
   the first family that has it. `qibla-ink.py` checks besides that each caption set from its words and
   the font's own space is the caption shaped whole (within 0.0000 em): no shaping crosses a space.
   (As first written, this said HostInk set a caption from its words and the font's space; review
   round 1 found that path dead, and the last section says what changed.)
4. **A test pins `QiblaType.cardinal`'s cap.** The page grows every dial letter by north's growth, so
   a cap or a floor of `cardinal`'s own was ignored: with `Line(9.975f, 1.4f)` 504 of the 505 wearApp
   tests passed. `t2TheDialsLettersGrowAsTheirLinesSay` holds both letters' lines to north's growth at
   every font size, and never below their drawing's units.
5. **`WatchWordsTest` holds the short forms' shape.** The places exactly, in order; no digit of its
   own; the bearing in degrees on the dimmed dial and no degree sign on the turning one; one word, the
   unit, the same on both dials, and no other. It collects every language's problems into one message.
   A reviewer's form (Urdu with a right-to-left mark, the unit first and a no-break space) passes it,
   where the exact forms failed. For such a form to be measured in `QiblaFitTest`, HostInk
   now draws a format character with no ink and no advance (the Arabic letter mark stopped it with
   "the model has no run"; the left-to-right and right-to-left marks were already Manrope's empty
   glyphs), and `qibla-ink.py` reads the units and the dial letters from the sources, so the model is
   regenerated from `WatchWords.kt` alone (`--check` fails until it is). Which other files a form
   touches depends on its language (review round 1, below): French, Turkish, Indonesian and Bengali
   none; Urdu, Arabic and English more.
6. **T1's KDoc** says what binds: the three constants (`maxLines` 1, `DETAIL_SOFT_WRAP` false,
   `DETAIL_OVERFLOW` Visible) and the size floor; the rows' lines and cut are one and false by
   construction, since `QiblaSweep.layout` writes them.
7. **Plan 3 Task 7 Step 1** gives Searching's detail as "${km} km" and BestEffort's as "23° · ${km}
   km", the phone's phrases as their descriptions; a Ruling 67 bullet joins the as-built note.
8. **`qibla-ink.py` in `tools/watchface/README.md`'s table**, with its regeneration step: run it when a
   short form, a dial letter or a caption changes in Arabic, Urdu or Bengali, commit the model with the
   words, then `./gradlew :wearApp:testDebugUnitTest`.
9. **P5.10's left minors.** Roadmap slice 5 reads "Coming in 1.4.0", and plan 5's M13 turns it to Done
   in the squash. The App Store Connect notes do not take "Settings ›": it is 13 bytes and the file is
   at 3,998 of 4,000, so 4,011; the room would come from other words of the notes, which are M3's. The
   code lens's nine are recorded in the P5.10 report's last section and in the ledger: three fixed as
   lines (the platform rule in any case with Samsung, each listing's 1.0.0 notes marked, CONTRIBUTING's
   path), one fixed in plan 5 (Task 4's files name the root README's build steps), five carried (the
   notes' bytes and their Apple Watch clause, for M3; the release-notes parser's duplicate tags and
   `notes_file`'s pointer, not lines; the Wear OS 6 words in the plan's fixed English, for the Lead).

## Seen red first

| Test | Mutant | Red |
|---|---|---|
| `t3TheDimmedSweepTakesTheBearingsWhoseInkReachesNearestTheTicks` | the sweep as it was, by the advance | "bn/Bengali ui: 200° keeps 15.008 inside the ticks, nearer than the swept [100, 101, 102, 103, 104, 23, 359] (15.246)" |
| `t2TheDialsLettersGrowAsTheirLinesSay` | `cardinal = Line(9.975f, 1.4f)` | "a letter's cap is the cap the letters are judged at expected:<1.3> but was:<1.4>"; the whole wearApp suite 505, this one alone failed |
| the same | `cardinal = Line(9.975f, 1.3f, floor = 9f)` | "never smaller than the drawing's 9.975 at 0.85 expected:<9.975> but was:<9.0>" |
| `t6TheCaptionGapIsNeverTighterThanAtTheDrawingsSizeInEveryLanguage` | Arabic's tilt notice "أبقِ معصمك في مستوى" (the elegant Naskh's ي of في), the model regenerated | "ar/Western elegant: 'أبقِ معصمك في مستوى' over '100 كم' at the drawing's size: -0.22"; of `QiblaFitTest`'s 17, this one alone (the Latin T6 passed) |
| the same | `level = Line(17f, 1.2f, floor = 11f)` | -0.22 the same way; also three tests that pin the notice's size for other reasons; the Latin T6 passed |
| `t7ADirectionMarkHasNoInkAndTakesNoRoom` | HostInk as it was | "the model has no run '؜'" for the Arabic letter mark |
| `WatchWordsTest.theDialsShortFormsAreTheFiguresAndTheUnitInEveryLanguage` | six forms at once: en "%1$s", fr "… km away", ar's dimmed "km", tr "%1$s · …", id "%1$s km 2", bn "%2$s° · %1$s km" | all seven problems named in one message; then a reviewer's Urdu form (U+200F, "km", U+00A0) green |
| `test_a_name_in_another_case_fails_too`, `test_samsung_fails_in_the_app_store_fields` | the rule as it was | four failures (android, WEAR OS, watchos, Samsung), then 42/42 |
| `qibla-ink.py --check` | the model before the captions | "is not what the fonts give" |

## The binding rulings and the errata

1. `git merge --ff-only watch-companion` first: `c9d003fe` to `6ba0afc1` (P3.9's landing).
2. Ruling 67 item 2 exactly, tests and docs, no behaviour change: above. `QiblaFitTest`'s tables print
   what they printed (T2's table unchanged; T6's Latin bound 0.255 and 0.805 em).
3. No device: wear-5558 was not taken; no lock.
4. Errata p3: #17 (Ruling 66) is what these tests hold; #18 (Ruling 67 item 3) is plan 3 Task 9's
   device log, not this run's, and nothing here changes it; #14 and #15 stay superseded by #17.
5. Rulings 39, 50 and 52 to 54: nothing of the feed, the delivery number or the link changes; the
   watch still draws only the feed's whole-degree bearing, its distance and the unit, never a
   coordinate; "Only your own devices can see your location." untouched. Rulings 41 to 43: no face
   file, slot or drawn text changed.
6. WIP commits as above.
7. The Gradle flake: see Checks.
8. Host state: `scripts/test.sh` ran whole, its Kotlin/Native simulator suites included; no simulator
   by hand, so Lane A's sim-se3 and ios-pair untouched.

## Checks

- `scripts/test.sh` whole, exit 0 (BUILD SUCCESSFUL in 7m 29s): check-versions 1.3.0 (39) and
  (100039); test-bump PASS; test_privacy_check PASS and the privacy gate clean but for the 194 rows
  awaiting M3 (241 rows); test_store_listing_check PASS (42) and the listing clean; `swift test`
  WatchCore 234/0 and WatchViews 6/0; tools/timetables compiled; Kotlin/Native simulator suites
  shared 2,282, widgetcore 109, watchcore 198; host shared 2,313, widgetcore 111, watchcore 228;
  wearApp 507 (QiblaFitTest 18 with the four new tests); 0 failures, 0 skipped. `:shared`'s two test
  tasks and `:wearApp`'s ran; `:widgetcore`'s and `:watchcore`'s were up to date (none of their
  inputs changed). No EOFException, and `EngineBudgetBenchmarkTest` passed: the known flake was not
  met.
- `QiblaFitTest`'s tables: T2's `[table]` rows are R66's report's host table, figure for figure; the
  Latin T6 bound still prints 0.255 and 0.805 em; T2's least gap is still 1.00 (Bengali in Western
  digits, 4,400 km, at 1.3).
- `python3 -I tools/watchface/qibla-ink.py --check`: the model is the fonts' (digits and captions'
  words within 0.0000 em); every run the old model held is unchanged, value for value.
- `tools/i18n-check.py` clean; `git diff --check` clean.
- Not run, as nothing they check changed: `scripts/ios-build.sh` (no iOS-linked source; `:shared`'s
  test source alone, which the simulator suite compiled), `:wearApp:lintDebug` (no main source), the
  face generators and `validate.sh` (no face file), the site build (no site page).

## For the Lead

- **The two dimensions.** T6 holds the room across the whole width, as the Latin bound did. The least
  distance between glyph boxes stays above 2.1 units everywhere but does not always grow with the font
  (the detail grows past its caption above 1.2, and titles break anew); if the Lead wants that held
  too, it is a new claim, not T6's.
- **Carried:** the five code-lens minors above; the notes' "Settings ›" for M3.
- **For native review:** the shape test lets a reviewer's short form through to `QiblaFitTest`, and
  what else the form touches, by language, is in the last section and plan 3's Ruling 67 note;
  Urdu's "km 6,600" (Ruling 67 item 4) can be answered with a direction mark, which the host now
  measures.

## Review round 1 (code lens): two Important items, fixed

On `watch-feed` at `50732b35`, no merge (`watch-companion` had moved to `9d747537`, P4.9; this round
takes nothing of it). The spec lens was clean; its seven minors are below.

1. **HostInk's caption path was dead, and the docs said the opposite of what it does.** The space
   between two Naskh or Bengali words is not one of minikin's sticky marks (the list holds U+00A0
   and U+202F, not U+0020), and minikin gives a character to the first family that has it, which is
   Manrope. So `HostInk.shape` ends a system-font run at every space, each word reaches the model
   alone, and the branch that set a caption from its words and the font's own space was never
   reached (the reviewer put an `error()` in it and the suite stayed green; "أنت باتجاه القبلة" is
   6.006 em, its words' 5.606 and two Manrope spaces of 0.200). That matches the watch. Fixed: the
   branch is gone, and `Modelled`'s and the class's KDoc, `qibla-ink.py`'s docstring and comments,
   this report (above, marked) and plan 3 say that each word comes whole from the model and the
   space is Manrope's. `qibla-ink.py`'s check is kept and described as what it is: each caption set
   from its words and the font's own space (0.221 em in the Naskh fonts, 0.26 in Bengali's, which the
   model keeps for that check alone) is the caption shaped whole, so no shaping crosses a space. A
   new test, `t7TheSpaceBetweenTheSystemFontsWordsIsManropes`, holds every caption of two words or
   more drawn over a detail in Arabic, Urdu and Bengali (eleven, both variants) to its words and
   Manrope's space, glyph count and advance. Red first on its mutant (the space sticky and the old
   branch back): "values-ar 'أنت باتجاه القبلة', ui: its words and Manrope's space. Expected <6.006>
   ... actual <6.048>", the font's space twice. The commit message of `779d358c` ("HostInk sets a
   caption from its words") is superseded by this section.
2. **"A native reviewer's form changes only `WatchWords`" was false for Arabic and English.** Exact
   forms were pinned outside `WatchWordsTest`, and some copies cannot go. Fixed both ways the
   reviewer offered:
   - **The pins read the form they test.** `QiblaModelTest`'s ten details are the feed's short form
     filled with the figures the tests hold (Cape Town's distance and 23, Saudi Arabic's ٦٬٦٠٠ and
     ٢٣); T8's unit is the form's one word, read from the feed, and "nothing else drawn" is no other
     letter or digit, so a direction mark or a no-break space between the parts is the phone's.
     `WatchScreenWordsTest` holds the English fallback's and `Fixtures.ARABIC_SCREENS`'s places and
     that the fallback keeps them, not the forms. `WatchFeedJsonTest`'s hand-written document writes
     the two forms from `WatchScreenWords.ENGLISH` (their keys stay by hand). The Swift
     `WatchScreenWordsTests` holds the port's `english` to the `olderPhone` words vector, which is
     Kotlin's English key by key, and the samples' forms by their places.
   - **What a form still touches is written down, by language,** in plan 3's Ruling 67 note, the
     `WatchWordsTest` KDoc, R66's report and plan 5 Task 11 Step 4: French, Turkish, Indonesian and
     Bengali, `WatchWords.kt` alone; Urdu, also its golden `phone-capetown-ur24.json`, regenerated;
     Arabic, also `Fixtures.ARABIC_SCREENS` (the hand copy `WatchFeedSampleTest` holds to the
     builder) and the regenerated vectors and Arabic golden; English, also the watch's own fallback
     in `:watchcore` and the Swift port (main source: a feed without the field draws it), and the
     regenerated vectors and English goldens. A new unit in Arabic or Bengali letters reruns
     `qibla-ink.py`.
   - **Seen whole.** Reviewer's forms written as `\u` escapes in `WatchWords.kt`: English
     `%1$s\u00A0km` and `%1$s°\u00A0·\u00A0%2$s\u00A0km`; Arabic the same with
     `\u200Fكم`; Urdu `\u200Fkm\u00A0%1$s` and `%1$s° · \u200Fkm\u00A0%2$s`. Changed with them only
     what the list names: `Fixtures.ARABIC_SCREENS`, `WatchScreenWords.ENGLISH` and the Swift
     `english`; then `:watchcore`'s and `:shared`'s `-Ptaqwa.updateVectors` rewrote eleven files
     (four samples, `feed-vectors.json`, `watch-rules-vectors.json`, five goldens) and nothing else;
     `qibla-ink.py --check` stayed clean; `scripts/test.sh` whole exit 0 (BUILD SUCCESSFUL in 8m 10s:
     swift 234/0, K/N shared 2,282 and watchcore 198, host shared 2,313 and watchcore 228, wearApp
     508, all executed; widgetcore up to date). Then every file was put back (`git checkout`).
     (Review round 2, below: each of these forms' no-break spaces opens its run or follows one of
     Manrope's characters, so Manrope's is the watch's there; beside the system fonts' digits or
     letters a no-break space is the font's own, which HostInk did not know until round 2.)
   - **To make that true,** two readers took a `\u` escape literally (the spec lens's minor 5): a
     reviewer's mark written as Kotlin writes one gave a false red. `QiblaSweep.watchWord` and
     `qibla-ink.py`'s `watch_word` now read it as the character, and `qibla-ink.py` leaves format
     characters out of the words it shapes (HostInk draws them with no ink), so a mark beside كم
     leaves the model unchanged.
   - **For the Lead, on `watch-companion`:** P4.9 (plan 4 Task 9, Lane A) pins English's and
     Arabic's drawn forms in Swift too ("6,600 km", "23° · 6,600 km", "٦٬٦٠٠ كم" in
     `QiblaScreenTests`, `QiblaCompassTests`, `WatchQiblaTests` and `TaqwaWatchUITests/QiblaTests`),
     and `compass-vectors.json` holds the forms and regenerates with the rest. This branch has not
     taken P4.9, so they are named here and in plan 3's note, not changed: relaxing them is Lane A's
     or the Lead's at the merge.

### The minors

- Spec lens: (1) the dead path: fixed above. (2) The dimmed bearings by the ink's reach toward the
  ticks rather than the ruling's word "widest": kept, as item 2 explains why. (3) The T3 test is a
  selection guard (the swept least equals the global least by construction): kept. (4) Arabic-Indic
  elegant's deepest bearing caption, ٤, is not swept (0.015 units, no effect): kept. (5) An escape
  read literally: fixed above. (6) "WearOS" still passes the listing check (P5.10's code-lens minor
  3): P5.10's, carried. (7) Plan 3's line needed the model for an Arabic-script unit: fixed in its
  note.
- Code lens: its eight minors were not in this round's brief (only its two Important items), so
  none is recorded here as fixed or carried; the ledger says so.

KB2604110095, the work iPhone 12, every emulator and simulator: untouched.

## Review round 2 (code quality, platform APIs, tests, privacy, accessibility and hygiene lens): one Important item, fixed as the watch measures it

(Corrected under Ruling 68 item 4: the Important was the code lens's, at 12:11 in the ledger; the
spec lens was CLEAN at 12:24. `ea6ac807`'s message gives it to the spec lens, as this heading did.)

On `watch-feed` at `d092200d`, no merge. One commit, its WIP folded in by a soft reset: "Wear: HostInk
draws a no-break space in the font of what stands before it in its run, as the watch does, after
review". Tests, the tool and docs only; nothing drawn changes.

1. **A no-break space beside the system fonts' digits or letters was Manrope's on the host and the
   font's own on the watch.** U+00A0 is one of minikin's sticky marks, and both fonts' cmaps have it
   (`hb-info --list-unicodes`: Naskh maps it to its space glyph, 452 of 2,048 units, 0.221 em; Bengali
   to `uni00A0.beng`, 0.26). The model held neither, so `Modelled.has` said no and HostInk drew
   Manrope's 0.200, 0.02 em narrow in Naskh and 0.06 in Bengali. Measured on emulator-5558 under the
   `wear-5558` lock (12:33 to 12:38; found off, booted with `scripts/wear-emulator.sh`): a debug-key
   instrumentation, `world.taqwa.nbspprobe`, laid each line out with `StaticLayout` at 1,000 px over
   the app's own Manrope SemiBold, in the page's direction; then it was uninstalled,
   `/data/local/tmp` cleaned, the watch checked as found (font scale 1.0, TalkBack off, automatic
   time, en-US) and stopped by its serial. In ems, NBSP the no-break space:

   | Line, page | The watch | Its no-break space |
   |---|---|---|
   | ১০০ NBSP km, left to right | 3.4960 | Bengali's, 0.26 (ExtraBold 3.5910) |
   | উ NBSP পূ, left to right | 1.7370 | Bengali's, each word alone |
   | ٦ NBSP ٦٠٠, right to left | 1.8896 | Naskh's, 0.2207 |
   | ۷ NBSP ۹۰۰, right to left | 1.7837 | Naskh's |
   | كم NBSP ١٠٠, right to left | 2.2783 | Naskh's |
   | كم NBSP كم, right to left | 2.2158 | Naskh's, each word alone |
   | كم, the Arabic letter mark, NBSP ١٠٠ | 2.2783 | Naskh's |
   | ١٠٠ NBSP كم, right to left | 2.2576 | Manrope's, 0.200 |
   | كم, a right-to-left mark, NBSP ١٠٠ | 2.2576 | Manrope's |
   | ১০০, a left-to-right mark, NBSP km | 3.4360 | Manrope's |
   | ১০০, a narrow no-break space, km | 3.4845 | the next fallback's, 0.2485 |

   Behind each of seventeen format characters, a no-break space after "كم" and after "১০০" was the
   font's own behind the zero-width non-joiner, the soft hyphen, the byte order mark and the grapheme
   joiner, and behind the Arabic letter mark after "كم" (after "১০০" that mark, a right-to-left
   character, ends the left-to-right run); Manrope's behind the two direction marks, the five
   embeddings and overrides and the four isolates. The zero-width joiner kept Bengali's after
   "১০০"; after "كم" it changes م's joining, so its line is no reading of the space (0.0791 em).

   **Fixed.** `qibla-ink.py` adds U+00A0 to both fonts' singles, holds every single to the font's
   cmap (`hb-info`), and checks two numbers grouped by a no-break space in Naskh and one in Bengali,
   and each dial word on both sides of one, against them shaped whole (within 0.0000 em: no shaping
   crosses it). The model gains the six fonts' no-break spaces and nothing else changes. `HostInk`
   sets a run that holds one from its words and the font's no-break spaces, each whole, drawn from
   the left; carries the run's font across a format character for the mark after it; and judges a
   mark afresh after a bidirectional control (`BIDI_CONTROLS`: U+200E, U+200F, U+202A to U+202E,
   U+2066 to U+2069). `t7ANoBreakSpaceIsTheFontOfWhatStandsBeforeItInItsRun` holds the first ten
   rows above and Bengali's grouped ৬ NBSP ৬০০ to their parts and the font's own no-break space, in
   both variants and both weights, glyph count and advance; in the elegant SemiBold fonts each
   measured row to the watch's advance within 0.002 em (the worst is 0.0012); and the narrow one to
   the stand-in's quarter em, never under the watch's. Red first on three mutants, each taken out
   again: the old model ("'১০০U+00A0km', ui 600: its parts and the no-break space it stands in.
   Expected <3.496> ... actual <3.4359999>"); every format character carrying the font
   ("'كمU+200FU+00A0١٠٠' ... Expected <2.2570002> ... actual <2.2779999>"); and none of them
   ("'كمU+061CU+00A0١٠٠' ... Expected <2.278> ... actual <2.257>").

   **Two parts of the fix the review asked for are not what the watch does, and were not made:**
   - *U+202F is not in the singles.* Neither font's cmap has it. hb-shape's 0.110 and 0.130 em are
     HarfBuzz's own narrow space, made from the space glyph, which Android never draws in these
     fonts: the watch draws its next fallback's, 0.2485 em. In the model it would have made the host
     draw it narrower than the watch, the unsafe side. The cmap check now refuses it ("qibla-ink.py:
     NotoSansBengaliUI-VF.ttf's cmap has no U+202F, so the watch never draws it in that font", seen
     on a copy with it added).
   - *A format character does not always keep the run's font.* The Arabic letter mark does, as the
     review read minikin; a direction mark, embedding or isolate does not, on the watch. Keeping the
     font across every one would have made the host 0.0207 em wide after a right-to-left mark in
     Naskh (the second mutant). HostInk does both, as measured.

   **The review's Arabic probe** "١٠٠ NBSP كم" is 2.2576 em on the watch, not hb-shape's 2.2783 for
   the whole line: Android itemizes each run of one direction alone, and a common separator between a
   number and a letter takes the letter's direction, so the no-break space opens the right-to-left run
   of كم with nothing of Naskh before it there. HostInk gave 2.2570 before and gives it still. Its
   Bengali probe, 3.496, is the watch's.

   **What the docs said.** "A native reviewer's form is measured as the watch draws it" (plan 3's
   Ruling 67 note, item 5 above) was not exact where a no-break space follows the system fonts'
   digits or letters, the Arabic letter mark between or not; it is now, within 0.0012 em of
   emulator-5558 on these lines. The round-1 forms seen whole put each no-break space where it opens
   a run or follows one of Manrope's characters (`java.text.Bidi` over each, filled), so their
   Manrope space was the watch's, and what that round reported of them stands. `HostInk`'s KDoc,
   `Modelled`'s, `qibla-ink.py`'s docstring and comments, `tools/watchface/README.md`, plan 3's
   Ruling 67 note and this report say so.

### Checks

- `scripts/test.sh` whole, exit 0 (BUILD SUCCESSFUL in 2m 28s; swift 234/0; the wearApp suite ran,
  509 tests, 0 failures, `QiblaFitTest` 20, executed at 12:44; the other modules' tests up to date,
  their inputs unchanged since round 1's run). The new test prints "[measure] T7: 11 no-break-space
  lines in both variants and weights; the elegant SemiBold ones within 0.0012 em of emulator-5558's".
- `python3 -I tools/watchface/qibla-ink.py --check`: "the model is the fonts' (digits, captions'
  words and no-break spaces set one at a time within 0.0000 em)".
- `python3 -I tools/i18n-check.py`: clean.

### The minors left

- A zero-width joiner inside an Arabic word changes its letters' joining on the watch (0.0791 em in
  the probe, where the joined م narrows); HostInk draws the joiner with no ink and the word as the
  model holds it. No short form writes one, and a reviewer's would show in `WatchWordsTest`'s one
  word; left, and in the ledger.

KB2604110095 and the work iPhone 12: untouched. emulator-5558 only under its lock, as above.

## Ruling 68 item 4: what this report left out

- **Review round 2's minors, unrecorded at the fix round's close** ("minors LEFT: none", 12:47).
  The code lens's (12:11): its Important's probe figures ('١٠٠ NBSP كم' 2.2570 em against hb-shape's
  2.2783, '১০০ NBSP km' 3.436 against 3.496), fixed by `ea6ac807`; T7's no-break-space check then
  Latin only, widened by the same fix. The spec lens's six (12:24): T6 holds the host's line break
  only (Arabic "لا توجد بوصلة في هذا الجهاز" is 1.051 of its box on one line at 1.0, so a device
  measure 4.9 % narrower would draw it on one line, its elegant في 0.22 into the detail's band), for
  Task 9's device log; this report's opening and commit count (corrected above); "Manrope's empty
  glyphs" for the direction marks (Manrope's cmap has neither; Java2D draws its invisible glyph);
  "the first family that has it" over-states minikin (family 0 always wins, and Manrope is family 0,
  so the conclusion holds); review round 1's code-lens minors unrecorded; round 1's carried minors.
- **Round 3's two test gaps**, taken by Ruling 68 item 4 (its report,
  `docs/superpowers/reports/2026-10-24-wear-app-pass/qibla-r68/README.md`): a right-to-left run's
  parts set in drawing order (`QiblaFitTest.t7ARightToLeftRunIsSetFromTheLeftInDrawingOrder`, red on
  the mutant that dropped the reversal), and a sticky mark the system font's cmap has but the model
  lacks an error, never Manrope's in its place (`t7AStickyMarkTheFontDrawsButTheModelLacksIsAnError`,
  `qibla-ink.py` writing each font's "sticky"); and `HostInk.kt`'s raw invisible characters written
  as escapes.
