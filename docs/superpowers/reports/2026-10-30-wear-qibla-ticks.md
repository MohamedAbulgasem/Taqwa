# Ruling 63 items 2 and 3: the Qibla detail keeps inside the ticks, and French says "de La Mecque"

Lane P, on `watch-feed`, 30 October 2026. Base `44cfbd8d` (= `watch-companion`, after R63a;
`git merge --ff-only watch-companion` was already up to date). This is no plan task: it is Ruling 63
items 2 and 3 in the ledger, on top of Ruling 62, built test-first. Nothing is pushed. One commit,
"Wear: Qibla's detail keeps inside the dial's ticks on one line as on two, and the French detail
says de La Mecque (Ruling 63)", Taqwa identity, no trailer; the WIPs `f31baf75`, `14aeca16` and
`e2ab9c05` are folded into it by a soft reset.

## What changed

| File | Change |
|---|---|
| `wearApp/.../qibla/QiblaModel.kt` | `QiblaDialMeasures`, the dial's own measures in one place (rim 92, scale 1.05, 24 ticks from 9 to 5 units in, the Kaaba marker 11 with its halo 0.95 of it). `QiblaType.RIM_RADIUS`, `TICK_ROOM` and `DIAL_ROOM` are made from it. `detailBox`: the one-line box is the chord of `TICK_ROOM` at the line's far edge, taken at each size it is tried at, as the two-line box already was; it no longer takes the screen's shape (`round`), since the dial is a circle on any screen. KDocs say what was measured. |
| `wearApp/.../qibla/QiblaPage.kt` | The ticks and the Kaaba marker drawn from `QiblaDialMeasures`. The detail's style comes from its box: `internal fun detailStyle(box, units, density)`. |
| `shared/.../values-fr/strings.xml` | `prayer_qibla_detail` = "%1$s° · %2$s km de La Mecque", marked `<!-- draft, to native review (plan 5 Task 11) -->`, with the reason on the comment above it. |
| `wearApp/src/test/.../QiblaInkTest.kt` | New `theDetailsInkKeepsInsideTheTicksAtAnyHeading` and `thePageSetsATwoLineDetailsLinesDetailPitchApart`; `frenchsDetailSaysDeLaMecqueAndKeepsInsideTheTicksOnTwoLines` replaces the "jusqu’à" case; the worst-figure cases in one `details()` helper, now with the sample's own figures too; `advance` caches by font and text. |
| `wearApp/src/test/.../QiblaModelTest.kt` | The one-line box inside the ticks at 1.0 (118.5 units) and 1.3 (108.0), each size's own box while it shrinks, the two-line steps after them. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | Task 7's `detail` line and an "After Ruling 63 items 2 and 3" as-built note; the round-3 "Left, for Task 9" item marked answered. |

## How each brief item and erratum was met

1. **`git merge --ff-only watch-companion`:** already up to date at `44cfbd8d`.
2. **Ruling 63 items 2 and 3, test-first, on Ruling 62's order:** the order is unchanged (10 grown to 1.3, else one line down to 9, else two lines). `QiblaInkTest` and the screen model failed on the old box first (below). The French string changed in `values-fr` alone, marked a draft to native review; `tools/i18n-check.py` is clean.
3. **Step 5-style check on emulator-5558 under `wear-5558`:** below. The emulator was put back as found and the lock released at once.
4. **Errata p3:** #11 (the right wrist) and #12 (Ruling 58 item 1) are untouched by this change and their tests still pass. #14 (Ruling 62) holds: the new guard runs the same worst figures. #15 (Ruling 63 item 2 at Task 9's device pass: no glyph on the ticks or the rim in all seven languages at 1.0 and 1.3) stays Task 9's. This run checked French, English, Urdu, Bengali and Indonesian on the emulator; Arabic and Turkish were checked on the host only. **Erratum p5 #17** (Ruling 62 item 4, the shorter French form at native review) is answered by item 3; the native reviewer may still change it.
5. **Rulings 39, 41, 42, 43, 50 and 52 to 54 hold:** the watch reads the same feed figures (the city and the whole-degree bearing, never coordinates) and asks for no location. The promise "Only your own devices can see your location." is untouched. No face, complication, feed or delivery file changed.
6. **WIP commits:** `f31baf75` (the box and the string), `14aeca16` (the measures in one place, the box without the screen's shape, the style from the box) and `e2ab9c05` (the name `QiblaDialMeasures`, the KDocs as measured).
7. **The Gradle flake:** not met in any run.
8. **Host state:** `scripts/test.sh` ran whole, Kotlin/Native simulator suites included (below). No simulator was touched by hand. `ios-pair` was held by the Lead throughout and was not used.

## Test-first

- **Red, on the old box and the old string** (the new tests written against the API as it was, so each failed by assertion, not by compiling), 5 of 57 Qibla tests:
  - `QiblaModelTest.aDetailThatFitsKeepsTheDrawingsSizeOnOneLineInsideTheTicks`: expected 118.46, was 151.63 (the screen's chord);
  - `aDetailTooWideShrinksOnItsOneLineDownToNine`: expected 118.94 at 9, was 151.63;
  - `aDetailTooWideAtNineTakesASecondLineGrowingDownInsideTheTicks`: one line at 13 expected 107.97, was 144.20;
  - `QiblaInkTest.theDetailsInkKeepsInsideTheTicksAtAnyHeading`: "200° · 10,000 km to Makkah" reaches 86.4 units from the middle, where the ticks begin at 82.55;
  - `QiblaInkTest.frenchsDetailSaysDeLaMecqueAndKeepsInsideTheTicksOnTwoLines`: the text was "… km jusqu’à La Mecque".
- **Red, the wiring:** `thePageSetsATwoLineDetailsLinesDetailPitchApart` did not compile while `detailStyle` was private and took no box.
- **Green:** all 58 Qibla tests (`QiblaModelTest` 30, `QiblaInkTest` 8, `WristCompassPolicyTest` 14, `TiltTest` 6).
- **Mutants, each killed, each restored from HEAD:**
  - no line height for two lines: `thePageSetsATwoLineDetailsLinesDetailPitchApart`;
  - the style ignores the box's lines: the same;
  - the one-line box back on the screen's chord: three `QiblaModelTest` tests, the ink test and the French test;
  - the one-line box inside the rim (92): the same five;
  - the ticks beginning 5 units in (`TICK_FROM`, the drawing's own constant): five `QiblaModelTest` tests, the ink test and the French test;
  - the one-line box at the largest size only: two `QiblaModelTest` tests.

## What the box costs: two lines and smaller text

The ruling is built as written. The figures below are for the Lead, since the ruling expected "de La Mecque" to keep one line at full size. Measured by Java2D in Manrope on the host, by HarfBuzz in layoutlib's Noto fonts for Arabic, Urdu and Bengali, and on the emulator, which measures a little narrower than either.

- **French BestEffort and NoSensor** ("23° · 6 600 km de La Mecque" is 133.8 units at 10; the box at 1.0 is 118.5 at 10 and 118.9 at 9):
  - two lines at every font size by the host's measure;
  - at 1.0 the break is "… km de" over "La Mecque" (the worst figures; Cape Town's too by the host's measure);
  - from 1.15 the break is "… km" over "de La Mecque";
  - on the emulator, Cape Town's kept one line at 9 at 1.0, and the worst figures went "359° · 19 000 km de" over "La Mecque".
  - Just 1.5 units short of one line at 9: a box that held each glyph's own ink inside the ticks, rather than the line's rectangle, would fit it (the line's end glyphs have no descenders, so the chord at the baseline, 123.1 units, would do).
- **The widest English and Indonesian details** (a three-figure bearing, 19,000 or 20,000 km):
  - one line at 9 at 1.0, two from 1.15;
  - Turkish's take two at 1.3;
  - the break is often between the figure and its unit ("359° · 20,000" over "km to Makkah", "Makkah · 6.600" over "km dari sini").
- **Indonesian's distance under a pointing dial:**
  - by the host's measure, two lines with the farthest figures at every size, and with Cape Town's at 1.3;
  - the emulator kept the farthest on one line at 1.0;
  - a second line there passes over the turning letters at some headings, as the tilt notice does (Ruling 62 item 5, Task 9).
- **At font size 1.3 the one-line details shrink:**
  - the box at y 159.2 is 108 to 110 units, so English's Cape Town distance is drawn at 9.5 and its BestEffort detail at 9;
  - before this change they were 12.5 and 12;
  - so a wearer who asked for larger text gets the detail smaller than at 1.0;
  - Arabic and Bengali keep 10 to 13.
- **Urdu's BestEffort detail:** two lines at every size with the farthest figures ("359° · مکہ مکرمہ تک" over "20,000 کلومیٹر"), and Cape Town's from 1.15. Arabic and Bengali keep one line throughout.
- **Never cut, never under 9, never on the ticks:** held in every case above by the tests, the models and the shots.
- **Options, if the Lead wants them (none built):**
  - (a) accept as built;
  - (b) fit each glyph's own ink inside the ticks, not the line's rectangle (French Cape Town back on one line at 1.0);
  - (c) above font size 1.0, take two lines at the grown size before shrinking below the drawing's 10: a change to Ruling 62's order;
  - (d) at native review, no-break spaces in "de La Mecque" and between a figure and "km", so a second line never begins with "km" or "La Mecque" alone.

## The phone keeps one line

The phone's Qibla card draws `prayer_qibla_detail` at 13 sp in Manrope Regular. On a 360 dp phone its room is 194 dp: 360, less the 24 dp gutters, the 16 dp padding, the 52 dp dial, and the 12 + 8 + 14 dp of gap and chevron. HarfBuzz widths at 13 sp:

| Text | Width | Fits 194 dp? |
|---|---|---|
| "23° · 6 600 km de La Mecque" | 169.0 dp | yes |
| the worst figures, "200° · 20 000 km de La Mecque" | 184.8 dp | yes |
| the old "23° · 6 600 km jusqu’à La Mecque" | 196.6 dp | no: it wrapped |

So the new form keeps one line where the old one wrapped.

The site's French timetables read the same string (`tools/timetables`' `Document.kt`). Their Qibla line now says "… km de La Mecque". No test or vector holds the French text.

## Seen on emulator-5558

- **Lock:** `wear-5558`, 23:28 to 23:48 SAST. The emulator was off and no lock was held when it was taken. It was booted with `tools/watchface/emulator.sh`, its state recorded first, and stopped by its serial after.
- **Build:** a scratch debug build (`34e519de`) of WIP `14aeca16`, from a git-archive copy that is not committed. The final tree differs from it only in the object's name (`QiblaDialMeasures`) and in comments. It carries hand-made samples: the committed English sample with fix round 1's language words and the new French detail.
  - `frc`: French, 23°, 6 600 km;
  - `frw`: French, 359°, 19 000 km;
  - `enw`: English, 359°, 20,000 km;
  - `urw`: Urdu, 359°, 20,000 km;
  - `bnw`: Bengali, ৩৫৯°, ২০,০০০ km;
  - `idw`: Indonesian, 200°, 20.000 km.
- **Shots:** each sample searching at true 340, 346 and 352, so the ticks turn under the line's ends, then BestEffort after a 600 µT field. French Cape Town and worst at 1.0 and 1.3; English, Urdu and Bengali worst at 1.3; Indonesian's farthest distance at 1.0.
- **Measured:** lit pixels below y 140, grouped into components and classed by their distance from the middle. In every shot the detail's ink keeps inside the ticks' circle (the farthest 81.1 units, against 82.55), and no glyph of the detail joins a tick.
  - The one join found, Urdu's BestEffort at 1.3, is the title's last line at y 140. A title keeps 6 units inside the rim by the round-2 rule, over a dimmed dial with no letters.
- **What the shots show:**
  - French BestEffort at 1.0: Cape Town on one line at 9; the worst figures "359° · 19 000 km de" over "La Mecque".
  - French BestEffort at 1.3: two lines, "… km" over "de La Mecque".
  - English at 1.3: "Makkah · 20,000 km away" on one line at 9; BestEffort "359° · 20,000" over "km to Makkah".
  - Urdu BestEffort at 1.3: two lines.
  - Bengali at 1.3: one line throughout.
  - Indonesian at 1.0: "Makkah · 20.000 km dari sini" on one line.
- **Put back:**
  - the app as found, `2b2d77c9` (`install -r -d` of the pulled APK);
  - the feed `0b3f92ef` (`DEBUG_FEED` epoch 1792821600, byte-equal), and `nudge.anchor` unchanged;
  - the session's `shared_prefs/tasbeeh.xml` removed, and `system_locales` deleted again;
  - font size 1.0, the console's sensors as found, and the face as found (shot);
  - then stopped by its serial. Its daily nudge alarm, set when the app's process started, goes with the boot.
- **One slip:** a zsh loop handed the shot script the wrong arguments once. The sample name was refused, the font size stayed 1.0, and no file was written. The run was redone under bash.
- **Not touched:** `KB2604110095`, the work iPhone 12 and every simulator.

## Checks

- **`scripts/test.sh` whole, exit 0** (BUILD SUCCESSFUL 6 m 29 s, on WIP `e2ab9c05`):
  - versions 1.3.0 (39)/(100039), `test-bump` PASS, `test_privacy_check` PASS;
  - `privacy-check --allow-awaiting`: clean but for the 186 rows awaiting M3;
  - `swift test` 154/0; `tools/timetables` compiled;
  - `:shared` host 2274 and K/N `iosSimulatorArm64Test` 2218, both run afresh (the French string changed);
  - `:widgetcore` and `:watchcore` up to date (nothing they read changed);
  - wearApp 400, 0 failures, 0 skipped, no EOFException.
- **`:wearApp`, on the final tree** (`--rerun-tasks`):
  - `testDebugUnitTest` 400/0 (`QiblaModelTest` 30, `QiblaInkTest` 8);
  - `assembleDebug`, and `assembleRelease` unsigned (no release key);
  - `lintDebug` and `lintRelease`: 0 errors, the same 3 warnings (`MissingApplicationIcon`, `UnusedResources`);
  - no compile warning in the Qibla sources.
- **The site** (Ruling 63 item 1):
  - `CI=true ./gradlew -p tools/timetables jvmTest generate`: 55 cities, 129 pages;
  - `python3 site/build.py --check`: 164 pages, PASS;
  - the French city pages' meta description now reads "Qibla : 119° · 4 496 km de La Mecque";
  - their visible Qibla line comes from the site's own `site/pages/fr/meta.json` ("{distance} km jusqu’à La Mecque"), which this task does not touch (Ruling 63 item 3 is `values-fr` alone). A minor for the Lead: the two French phrasings now differ on one page.
- **Other checks:** `tools/i18n-check.py` clean; `git diff --check` clean.
- **The commit:** Taqwa identity (Mohamed Abulgasem), no Co-Authored-By and no AI line.

## Fix round 1 (31 October, Lane P)

Review round 1 (both lenses) found three Importants. All three come from what Rulings 62 and 63
make the detail do, so this round keeps both rulings, as the brief says, and asks the Lead the
questions they leave. What it could fix without a ruling, it fixed.

### What changed

| File | Change |
|---|---|
| `wearApp/.../qibla/QiblaModel.kt` | `QiblaDialMeasures.LETTERS` (19 units inside the rim) and `LETTER_DROP` (3.6), and `QiblaType.LETTER_RADIUS` (72.05) made from them. `detailBox`'s KDoc names Indonesian's Cape Town distance on two lines from 1.2, the letters, and the smaller size at 1.3. |
| `wearApp/.../qibla/QiblaPage.kt` | The letters drawn from `QiblaDialMeasures`. One style for the detail, `detailStyle(box, units, density)`, used to measure it and to draw it; the private overload is gone. |
| `wearApp/src/test/.../QiblaInkTest.kt` | `aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead`: round 3's guard, back where it holds, with the cases it no longer holds in named (`heldForTheLead`). |
| `wearApp/src/test/.../QiblaModelTest.kt` | `aSecondLineOfTheDetailWouldStandOnTheTurningLettersPath`: the letters' measures, and why a pointing dial's distance keeps to one line. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | Round 3's guard sentence says what became of it; review round 1's emulator sighting; a fix-round-1 note with the Lead's questions. |

### Round 3's guard, and what became of it (Important 3)

At `44cfbd8d`, `theWorstRealFiguresAreNeverCut` also held that a distance under a pointing dial
never takes two lines, "a pointing dial's letters turn where a second line would go". The change
above dropped that assertion and said nothing of it. Put back as it was, it fails inside the ticks
in 5,898 of the test's cases (Java2D in Manrope SemiBold, kerned, as the test measures):

- **Indonesian**, "Makkah · … km dari sini" (126.3 units at 10 with Cape Town's figure):
  - the 20,000 km figures at every font size, 1.0 included ("Makkah · 20.000" over "km dari sini", at 10 in a 93.1-unit box);
  - Cape Town's own figure from 1.2 (at 1.3: two lines at 10.5 in 77.0 units);
  - measured 3 % wider, nearly every figure at every size, and Cape Town's from 1.1.
- **English and Turkish**, measured 3 % wider, from 1.24: English's 20,000 km ("Makkah ·" over "20,000 km away" at 1.3), and most of Turkish's five-figure distances ("Mekke · 10,000" over "km uzakta" at 1.3).
- **French:** never.

So the guard is back as `aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead`. It holds
one line for every pointing-dial distance outside those cases, so no new one comes in unseen, and
it names them in `heldForTheLead` until the Lead rules. It also holds that they are real today:
the sample's own Indonesian figure takes two lines at 1.3.

Why one line: the letters' middles stand 72.05 units out. Near the dial's foot that circle crosses
the band a second line takes, inside its box, at 1.0, 1.15 and 1.3 and at every size two lines may
take (`aSecondLineOfTheDetailWouldStandOnTheTurningLettersPath`). At 1.3 the second baseline is at
about y 170, on the circle itself, so a letter within about 25° of the foot sits on it; at 1.0 a
letter crosses it roughly 12° to 33° out from the foot. Review round 1 saw it on emulator-5558: Indonesian's
farthest at 1.3, the "S" on "km" at true 340 and 346.

### For the Lead

None of these is built. Each departs from Ruling 62 (one line from 10 grown down to a floor of 9,
then two lines; fix round 2's gap unchanged) or Ruling 63 (the box inside the ticks).

1. **A pointing dial's distance on two lines** (Importants 1 and 3). The ways to keep it on one line:
   - a floor of 8 for the distance alone, which keeps every one on one line by the test's measure
     with 3 % more width (Indonesian needs 8 at 1.3, English and Turkish 8.5);
   - a shorter Indonesian `qibla_distance` at native review, without "dari sini" (the phone's
     string too);
   - or the Lead's word that it is a Task 9 item, as Ruling 62 item 5 made the tilt notice's.
2. **A larger font size draws the detail smaller** (Important 2). The sizes, by the test's own
   measure, at 1.0, 1.15 and 1.3:
   - English's Cape Town distance 10, 10 and 9.5 (12.5 at 1.3 before Ruling 63);
   - its BestEffort detail 10, 9.5 and 9 (12 at 1.3 before);
   - Turkish's distance 10, 9.5 and 9.5;
   - Indonesian's Cape Town distance 9, 9 and two lines at 10.5;
   - French's distance 10 throughout.

   It is Ruling 62's order in a box that narrows as the line moves down: fix round 2's gap moves
   the detail down to y 159.2 at 1.3, where the ticks' chord is 108 to 110 units, against 118.5 at y 155.
   Option (b) above (fit each glyph's own ink) gives back half a unit in places. Option (c) (two
   lines at the grown size before going below 10) changes Ruling 62's order, and under a pointing
   dial it would put more distances on two lines, so it fits the dimmed dial (no letters) only.
3. Whichever is ruled, `heldForTheLead` becomes the accepted cases, or goes.

### Test-first and mutants

- **Red first:** the new tests against `f7419eaf`'s code do not compile (`QiblaDialMeasures.LETTERS`,
  `LETTER_DROP` and `QiblaType.LETTER_RADIUS` unresolved). The guard without its held cases fails
  at `f7419eaf`, as review round 1 found.
- **Mutants, each killed and restored:**
  - `heldForTheLead` false (round 3's guard as it was): the guard fails;
  - Indonesian held only below 1.24: the guard fails;
  - the one-line box 3 units narrower: the guard fails for a language not held, with three
    `QiblaModelTest` box tests;
  - `LETTERS` 17: the letters test fails;
  - `LETTER_RADIUS` from the ticks' constant: the letters test fails.
- Review round 1's surviving mutant (the page drawing with the private `detailStyle(size, 1)`)
  cannot be written now: there is one style function, and `thePageSetsATwoLineDetailsLinesDetailPitchApart`
  pins it. (Corrected in fix round 2: a mutant at the draw site that hands that function another
  box, `detailStyle(detailBox.copy(maxLines = 1), …)`, survives every test, review round 2 found.
  The page's composition does not run in these JVM tests, so the draw call is held by reading it
  and by Task 9's shots.)

### Not on the emulator

Nothing drawn changes: the letters are drawn from the same 19 and 3.6, which the letters test pins,
and the detail's style is the same text style by the same rule. So this round took no lock.

## Fix round 2 (31 October, Lane P)

Review round 2 (both lenses) found no new Important. It found the four of round 1 still open: a
pointing dial's distance on two lines under the turning letters, and a larger font size drawing
the detail smaller. Both reviewers said these need the Lead's ruling, as fix round 1 did. The
ledger had no ruling on them when this round started (01:40). So this round keeps Rulings 62 and
63 and builds no remedy. It corrects what the plan, the report and the code said of the two
questions, from review round 2's emulator shots, and fixes three minors.

### What review round 2 adds to the Lead's questions

1. **A pointing dial's distance on two lines** happens for real Indonesian figures on the watch,
   not only the farthest ones. On emulator-5558, under `wear-5558`, at true 340 (a scratch debug
   build of `a61fa2b8` with hand-made samples):
   - "Makkah · 10.000 km dari sini" takes two lines at 1.2, 1.24 and 1.3 ("Makkah · 10.000" over
     "km dari sini"), with the "S" drawn over "km". 12.000 and 15.000 do the same at 1.3.
   - One line: 6.600 (Cape Town's) and 9.000 at 1.3, and 10.000 at 1.15.
   - Eastern Indonesia gets these figures. By `QiblaMath`'s haversine and the feed's two-figure
     rounding (`WatchFeedBuilder.roundedKm`), Ambon (9,988 km), Sorong (10,191) and Fakfak (10,382)
     round to 10.000. Biak, Nabire, Timika, Wamena, Jayapura and Merauke (10,700 to 11,440) round
     to 11.000.
   - 11.000 was not shown in round 2 (review round 3 showed it; see fix round 3). In Manrope SemiBold it is 128.4 units at 10, between 9.000 (126.5, one
     line at 1.3) and 12.000 (130.4, two at 1.3).
   - `44cfbd8d` drew all of these on one line, in the screen's chord.

   The emulator measures narrower than the host. On the emulator, Cape Town's 6.600 keeps one
   line at 1.3. By the host's measure it takes two from 1.2, so the plan's "Cape Town's own from
   1.2" holds on the host only.
2. **A larger font size draws the detail smaller.** On the emulator, the English sample's
   "Makkah · 6,600 km away" has ink 109.7 units wide and 9.7 high at 1.0, against 103.5 by 8.8 at
   1.3. The Indonesian sample's is 115.0 wide at 1.0 and 108.4 at 1.3. The dial's letters still
   grow. The host's sizes are fix round 1's: in English, the Cape Town distance is 10 / 10 / 9.5
   at 1.0 / 1.15 / 1.3, and the BestEffort detail 10 / 9.5 / 9. The Turkish distance is
   10 / 9.5 / 9.5. The Indonesian distance is 9 at every size up to 1.15. Plan 3's call 15 says a
   wearer's font size grows a line up to its cap, and only the dhikr and the notices may shrink
   below 100%. Rulings 62 and 63 together depart from that here, so this is the Lead's call.

The remedies are unchanged from fix round 1, and each changes Ruling 62 or 63:

- for question 1: a floor of 8 for the distance alone, a shorter Indonesian `qibla_distance` at
  native review, or a Task 9 acceptance, as Ruling 62 item 5 gave the tilt notice;
- for question 2: fit each glyph's own ink rather than the line's rectangle, take two lines at
  the grown size before going below 10 (on the dimmed dial only), or accept it.

Whichever is ruled, `heldForTheLead` then becomes the accepted cases, or goes.

### What changed

| File | Change |
|---|---|
| `wearApp/.../qibla/QiblaPage.kt` | The letters are drawn at `QiblaType.LETTER_RADIUS`, the radius the letters test holds, where the page used to work it out again. Major ticks fall every `TICKS / 4`, not every 6. Both values are the same, so nothing drawn changes. |
| `wearApp/.../qibla/QiblaModel.kt` | `detailBox`'s KDoc gives the emulator's five-figure Indonesian cases and eastern Indonesia's figures. `LETTER_RADIUS`'s KDoc names the test that holds the letters' circle (`QiblaModelTest`) and the one-line guard (`QiblaInkTest`). |
| `wearApp/src/test/.../QiblaInkTest.kt` | The guard also holds that eastern Indonesia's 10.000 takes two lines at 1.2, 1.24 and 1.3 by its measure, as on the emulator. `heldForTheLead`'s KDoc says it is wider than the failing cases: every Indonesian distance, and English and Turkish measured 3 % wider from 1.24. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | The R63 as-built note on Indonesian gives review round 2's cases. The renamed French test is named. The one-style note says the draw-site mutant survives. A fix-round-2 note. |
| this report | This section, and the correction above under fix round 1's mutants. |

### Not on the emulator

Nothing drawn changes. The letters' radius is the same constant expression (92 − 19 · 1.05), and
24 / 4 is 6. So this round took no lock. The emulator facts above are review round 2's.

## Fix round 3 (31 October, Lane P)

Review round 3 (both lenses) again found no new Important. It found the same two questions still
open, with new facts, and both reviewers said again that they need the Lead's ruling. The ledger
had no ruling on them when this round started (02:11), or when it ended. So this round keeps
Rulings 62 and 63 and builds no remedy. It does what needs no ruling: the held cases in the
one-line guard are now exactly the measured ones, 11.000 is pinned, and the docs give round 3's
facts.

### What review round 3 adds to the Lead's questions

1. **A pointing dial's distance on two lines.** On emulator-5558, under `wear-5558`, at true 340
   (a scratch debug build of `93eaa8d7`):
   - "Makkah · 11.000 km dari sini" takes two lines at 1.3 ("Makkah · 11.000" over "km dari
     sini"), with the "S" drawn over "km". It keeps one line at 1.15, 1.2 and 1.24.
   - "Makkah · 10.000 km dari sini" takes two lines at 1.2 and 1.3 again, the "S" over "km".
   - By `QiblaMath`'s haversine and `WatchFeedBuilder.roundedKm` over the phone's `cities.csv`
     (my own check agrees with the reviewer's), nine Indonesian places round to 10.000: Ambon,
     Amahai, Masohi, Waisai, Sorong, Teminabuan, Fakfak, Bintuni and Manokwari. Fourteen round to
     11.000: Ransiki, Tual, Dobo, Biak, Nabire, Timika, Mulia, Tiom, Agats, Wamena, Sentani,
     Abepura, Jayapura and Merauke.
   - `44cfbd8d` drew all of these on one line.
2. **A larger font size draws the detail smaller.** On the emulator, the English Cape Town
   distance "Makkah · 6,600 km away" has ink 110.1 by 10.1 units at 1.0 and 104.0 by 9.3 at 1.3.
   The BestEffort detail "23° · 6,600 km to Makkah" is 113.2 by 8.8 at 1.0 and 106.6 by 8.4 at
   1.3. By the host's measure (this round's probe agrees with the reviewer's), at 1.0 / 1.1 /
   1.15 / 1.2 / 1.24 / 1.3:
   - English BestEffort, Cape Town: 10 / 9.5 / 9.5 / 9.5 / 9.5 / 9 (`44cfbd8d`: 10 / 11 / 11.5 /
     12 / 12.4 / 12);
   - English distance, Cape Town: 10 / 10 / 10 / 9.5 / 9.5 / 9.5;
   - Turkish distance, Cape Town: 10 / 10 / 9.5 / 9.5 / 9.5 / 9.5;
   - Indonesian distance, Cape Town: 9 up to 1.18, then two lines at 11 and 10.5.

The remedies are unchanged from fix rounds 1 and 2, and each changes Ruling 62 or 63:

- for question 1: a floor of 8 for the distance alone, a shorter Indonesian `qibla_distance` at
  native review, or a Task 9 acceptance, as Ruling 62 item 5 gave the tilt notice;
- for question 2: fit each glyph's own ink, take two lines at the grown size before going below
  10 (on the dimmed dial only), or accept it in writing. The tests then pin what is chosen.

### What changed

| File | Change |
|---|---|
| `wearApp/src/test/.../QiblaInkTest.kt` | `heldForTheLead` is a table, not a predicate: each case is the language's folder, whether it is measured 3 % wider, and the distance's thousands, with the smallest font size it takes two lines from. The guard fails on a two-line case outside the table or below its size. It also fails on an entry that keeps one line at its own size, so the table is the cases and no wider. 11.000 is pinned on two lines from 1.15 by the test's measure, beside Cape Town's 6.600 and Ambon's 10.000. |
| `wearApp/.../qibla/QiblaModel.kt` | `detailBox`'s KDoc only. It gives where the widest BestEffort details break, by the host's measure: English, Indonesian and Turkish from 1.1, 1.06 and 1.2 (it said "from 1.15" and "at 1.3"). It gives the five-figure Indonesian distances from 1.06 to 1.15, and review round 3's 11.000 on the emulator. It counts the places (nine and fourteen), and gives the sizes at 1.0 and 1.3. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | The R63 as-built bullets for the widest details and the Indonesian places. A note on fix round 2's "was not shown". A fix-round-3 note. |
| this report | This section, and the same note on fix round 2's "11.000 was not shown". |

The table, by the test's own measure (Java2D, Manrope SemiBold, kerned), under any caption and
with any of the grouping marks:

| Language, measure | From | Thousands of km |
|---|---|---|
| Indonesian, as Java2D measures | 0.85 | 20 |
| | 1.06 | 10, 12, 14, 15, 16, 18, 19 |
| | 1.1 | 13, 17 |
| | 1.15 | 11 |
| | 1.2 | 6 (Cape Town's 6.600) |
| Indonesian, 3 % wider | 0.85 | 10 to 20 |
| | 1.1 | 6 |
| English, 3 % wider | 1.3 | 20 |
| Turkish, 3 % wider | 1.24 | 20 |
| | 1.3 | 10, 12, 13, 14, 15, 16, 18, 19 |

Each case takes two lines at every size above its own, and beyond the cap (a probe found no gap).
French never takes two lines, and English and Turkish do not as Java2D measures.

### Test-first and mutants

The table changes what the guard lets through, so it was checked by mutants, each killed by
`aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead` alone, then restored:

- Indonesian 11 held from 1.12, a size too early: it fails, "held for the Lead, but on one line at
  the size each is held from".
- Indonesian 11 held from 1.18, a size too late: it fails at 1.15, "a pointing dial's distance on
  two lines".
- A French entry no case reaches: it fails, as held but on one line.
- The phone's Indonesian `qibla_distance` made longer ("Kota Makkah · …", in `values-id` only):
  it fails at 0.85. **`93eaa8d7`'s guard passes it** (9 of 9 green), which is what review round
  3's Important 4 said: the old predicate let every Indonesian case through.
- The one-line box a unit narrower (`oneLineWidth` − 1): it fails. So does `93eaa8d7`'s guard.

### Checks

- `./gradlew :wearApp:testDebugUnitTest --rerun-tasks`: 402 tests, 0 failures (QiblaModelTest 31,
  QiblaInkTest 9).
- `:wearApp:assembleDebug` and `:wearApp:assembleRelease` (unsigned) build. `lintDebug` and
  `lintRelease` give 0 errors and the same 3 warnings (MissingApplicationIcon, 2 UnusedResources).
- `tools/i18n-check.py` is clean, and `git diff --check` is clean.
- `scripts/test.sh` whole, on the code as committed: exit 0. Versions 1.3.0 (39) and (100039),
  the bump test and the privacy gate's tests PASS, privacy-check clean but the 186 rows awaiting
  M3 (233 rows), `swift test` 154/0, `tools/timetables` compiled. The Gradle suites were up to date
  on identical inputs: `:shared`, `:widgetcore` and `:watchcore` are unchanged since `93eaa8d7`,
  and `:wearApp`'s tests had just run with `--rerun-tasks`.

### Minors left

- The mutant at the draw site (`detailStyle(detailBox.copy(maxLines = 1), …)`) still survives, and
  the page's `LETTER_RADIUS` is not pinned there. There is no JVM composition test (no
  Robolectric), so it is Task 9's.
- The page measures the detail with a placeholder `DetailBox(maxLines = 1, …, y = 0f)`
  (`QiblaPage.kt`). `detailStyle` reads only the size and the line count, and the line height does
  not change how many lines a text takes, so the count is right. It is tidier with a style that
  takes the size alone, but that brings back the second style that round 1's mutant used. Left.
- The Kaaba's halo reaches `DIAL_ROOM` (81.03 units) inside the 82.55-unit box, so a full line's
  end may meet it faintly. Task 9.
- French "km de" / "La Mecque" breaks at 1.0, and errata p5 #17's "one line at full size" is stale.
  The errata file is the Lead's.
- The site's French pages say "de La Mecque" in the meta and "jusqu’à La Mecque" on the page
  (`site/pages/fr/meta.json:46`). For P5.10's site copy.
- The figure and its unit split across lines ("359° · 20,000" over "km to Makkah"). For native
  review.
- The phone keeps one line by measure only: Manrope Regular at 13 sp by raqm, 167.7 and 183.5 dp
  against 194. From phone font size 1.15 it wraps; it is never cut. Not seen on a phone.

### Not on the emulator

Nothing drawn changes: the code change is a KDoc, and the rest is the test and the docs. So this
round took no lock. The emulator facts above are review round 3's.

## Fix round 4 (31 October, Lane P)

Review round 4 carried the two questions for the Lead, with new facts. The code lens also found a
new Important that needs no ruling: the one-line guard measured too few figures. The ledger had
no ruling on the two questions when this round started (02:49). So this round keeps Rulings 62 and
63 and builds no remedy. It fixes the guard and gives the Lead the cases it finds.

### The guard's figures (Important 3, fixed)

`QiblaInkTest.details` took the five-figure distances and Cape Town's 6,600 alone. So "every other
case keeps one line" held only for those figures, and the cases given to the Lead named eastern
Indonesia's 23 places only. It now takes every figure the phone sends (`FIGURES`). The phone
rounds to two significant figures (`WatchFeedBuilder.roundedKm`), so the figures are the whole
kilometre to 99, tens to 990, hundreds to 9,900 and thousands to 20,000. The never-cut and
inside-the-ticks tests take them too, and still pass.

`heldForTheLead` is now keyed by the figure, the distance in km. It was keyed by the thousands,
which made 7.000 and 7.900 one case. The table, by the test's own measure (Java2D, Manrope
SemiBold, kerned), under any pointing caption and with any grouping mark:

| Language, measure | Figures | From |
|---|---|---|
| Indonesian, as Java2D measures | 20.000 | 0.85 |
| | the other five-figure ones | 1.06 to 1.15 (11.000 from 1.15) |
| | 6.000 | 1.18 |
| | the other four-figure ones but 1.100 | 1.2, 1.24 or 1.3 |
| | 1.100, and every figure under 1.000 | one line throughout |
| Indonesian, 3 % wider | every five-figure one | 0.85 |
| | every four-figure one | 1.06 to 1.24 (1.100 from 1.24) |
| | every figure under 1.000 | one line throughout |
| English, 3 % wider | 20,000 | 1.3 |
| Turkish, 3 % wider | 20.000; 10, 12 to 16, 18 and 19 thousand | 1.24; 1.3 |

English and Turkish have no four-figure case, and French has none at all. The guard now also
holds each entry on two lines at every size above its own, and beyond the cap. Its KDoc said so
before, but it checked only the entry's own size.

### For the Lead: question 1 covers every Indonesian place

The phone's `cities.csv` has 448 Indonesian places, from Banda Aceh (6.200 km) to Jayapura
(11.000). I counted them with `QiblaMath`'s haversine and `roundedKm`. By the host's measure,
every one of them has its distance on two lines on a pointing dial from some font size:

- as Java2D measures: 9 places from 1.06 (10.000), 14 from 1.15 (11.000), 185 from 1.2 (Medan's
  6.600, Bandung's 8.000, Surabaya's 8.600, and Mataram's and Palu's 9.000 among them), 198 from
  1.24 (Jakarta's 7.900 among them), and 42 at 1.3 only (Makassar's 9.100 among them);
- measured 3 % wider: 23 at every size, 296 from 1.1, 77 from 1.12, 24 from 1.15 and 28 from 1.18.

On emulator-5558, 6.600 and 9.000 kept one line at 1.3 (review rounds 2 and 4). Review round 4
puts the watch about 1.5 % from the break there: 9.000 is 126.5 units at 10 and kept one line,
and 11.000 is 128.4 and took two. So the host's measure may overstate how many places break on a
real watch, but not by much.

The remedies are the same as before, and each changes Ruling 62 or 63: a floor of 8 for the
distance, a shorter Indonesian `qibla_distance` at native review, or a Task 9 acceptance. Fix
round 1 measured the floor of 8 on the five-figure figures, and the four-figure ones have a digit
fewer.

### For the Lead: question 2's new fact

By the host's measure, Indonesian's distance for Medan (6.600), Jakarta (7.900) and Surabaya
(8.600) is drawn at 9 at every font size it keeps one line at, 1.0 among them. Review round 4
gives `44cfbd8d`'s size for the same distances as 10 at 1.0 and 11.5 from 1.15. The other figures
are unchanged from review rounds 3 and 4: English's Cape Town distance 10 at 1.0 and 9.5 from 1.2,
its BestEffort detail 10, 9.5 from 1.06, then 9 at 1.3, and Turkish's distance 9.5 from 1.12.

### What changed

| File | Change |
|---|---|
| `wearApp/src/test/.../QiblaInkTest.kt` | `details` takes every figure the phone sends (`FIGURES`), and reads each string once per language. `heldForTheLead` is keyed by the figure in km, and lists the four-figure Indonesian cases. The guard holds each entry on two lines at every size from its own. It also pins Medan's 6.600, Bandung's 8.000 and Surabaya's 8.600 from 1.2, Jakarta's 7.900 from 1.24, 10.000 from 1.06 and 11.000 from 1.15. Its comment gives 10.000's break by the test's measure (1.06, not 1.2). |
| `wearApp/.../qibla/QiblaModel.kt` | `detailBox`'s KDoc only: the four-figure cases and the 448 places, the widest bearings' BestEffort breaks (200°, 300° and 206°: 1.06, 1.06 and 1.18; 19,000 km 1.12, 1.1 and 1.24; four-figure from 1.24), where it gave 359°'s, and the Indonesian sizes at 1.0. Nothing drawn changes. |
| `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` | The R63 as-built bullets, and a fix-round-4 note. |
| this report | This section. |

### Test-first and mutants

- Red first: with every figure in `details` and round 3's table, the guard fails: "values-id at
  font size 1.06, measured 3 % wider: 'Makkah · 6,000 km dari sini' in DetailBox(maxLines=2,
  size=10.6, …) (held from 1.1)". The never-cut and inside-the-ticks tests pass. This is review
  round 4's mutant: the thousands key let 6.000 ride on 6.600's size.
- Each mutant below is killed by `aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead`
  alone, then restored:
  - Jakarta's 7.900 dropped from the table: it fails at 1.24, "held from no size".
  - 7.900 held from 1.2, a size too early: it fails, held but on one line at its size.
  - 1.100 held from 1.3, where it keeps one line: it fails the same way.
  - The Indonesian `qibla_distance` made longer ("Kota Makkah · …", in `values-id` only): it fails
    at 0.85.
- `QiblaInkTest` runs in about 4 s with every figure. It took 5.2 s at `8a42c5a0` with a tenth of
  them, and 53 s with every figure before `details` read each string once.

### Checks

- `./gradlew :wearApp:testDebugUnitTest --rerun-tasks :wearApp:assembleDebug
  :wearApp:assembleRelease :wearApp:lintDebug :wearApp:lintRelease`: 402 tests, 0 failures
  (QiblaModelTest 31, QiblaInkTest 9). Debug and unsigned release build. Lint gives 0 errors and
  the same 3 warnings (MissingApplicationIcon, 2 UnusedResources).
- `tools/i18n-check.py` is clean, and `git diff --check` is clean.
- `scripts/test.sh` whole, on the code as committed: exit 0. The bump test and the privacy gate's
  tests PASS, privacy-check is clean but for the 186 rows awaiting M3 (233 rows), `swift test` is
  154/0, and `tools/timetables` compiled. The Gradle suites, Kotlin/Native simulator suites
  included, were up to date on identical inputs: `:shared`, `:widgetcore` and `:watchcore` are
  unchanged since `93eaa8d7`, and `:wearApp`'s tests had just run with `--rerun-tasks`.

### Minors left

- The mutant at the draw site and the page's `LETTER_RADIUS` are still unpinned (no JVM
  composition test). Task 9.
- The page still measures with a placeholder `DetailBox(maxLines = 1, …, y = 0f)`. Left, as in fix
  round 3.
- The Kaaba's halo reaches `DIAL_ROOM` (81.03 units) inside the 82.55-unit box. Task 9.
- A one-line detail's ends lie on the letters' circle too (review round 4: English's Cape Town
  distance is 113.3 units wide, and the circle crosses its band at |x| 43.4 to 54.6). This was
  true before Ruling 63, with wider boxes. It goes with question 1, for the Lead or Task 9.
- `QiblaDialMeasures`'s grouped KDoc (`TICK_FROM`/`TICK_TO`, `KAABA`/`KAABA_HALO`,
  `LETTERS`/`LETTER_DROP`) documents only the first of each pair. The page draws the ticks from
  `RIM - TICK_FROM * K`, not from `TICK_ROOM`. Both are the same figure from one source. Left.
- `detailBox`'s KDoc still names review rounds for the emulator's facts. Left, since they are the
  evidence.
- The site's French pages mix "de La Mecque" with `meta.json`'s "jusqu’à". For P5.10, as before.
- The phone keeps one line by measure only, and errata p5 #17 is stale. The errata file is the
  Lead's.

### Not on the emulator

Nothing drawn changes: the code change is a KDoc, and the rest is the test and the docs. So this
round took no lock. The emulator facts above are review rounds 2 to 4's.
