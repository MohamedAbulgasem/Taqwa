# Plan 4 Task 8: Tasbeeh with the crown and undo

Lane A worked in `watch-apple`, plan 4's own worktree, on 31 October 2026 (00:16 to about 01:40). The base was `980baff0` ("Merge watch-apple: R64"), `watch-companion`'s head: `git merge --ff-only watch-companion` found it already up to date. Xcode 27.2's components were in, so every simulator step ran, on the SE 3 (40mm) under the `sim-se3` lock, and none was deferred.

The work was test-first. Step 1's Kotlin writer was seen failing on the missing file, then wrote it, then passed without the flag. Step 2's Swift tests were seen failing (`swift test` did not build: `TasbeehFeel`, `CrownCounter`, `TasbeehState` and the rest were missing), and the six UI tests were added to their target and seen failing on the SE 3, finding no `tasbeeh.counter` (Step 3). Each later addition was seen red first too: the debug arguments, the model's `actions`, and `RingGauge`'s notches. None of it was committed red.

**In one line:** the Apple Watch's second page counts the tasbeeh as the phone does: a tap anywhere or a detent of the crown up counts one, a detent back takes one away within the round, a finished set holds its full ring for a second and rolls over, each burst is saved once, one haptic a turn, VoiceOver hears the phone's spoken phrases with the two actions the Galaxy offers, and the list behind the dhikr's line holds the seven, his own phrases after them, and Reset.

## What was made

- **`watchcore/src/androidHostTest/kotlin/world/taqwa/app/tasbeeh/TasbeehVectorsTest.kt`** and **`watchcore/vectors/tasbeeh-vectors.json`**: the seven built-ins in the phone's order (each part's dhikr id, Arabic, transliteration and count), `defaultId`, `setCompleteHoldMs`, `maxTarget`, and 16 sequences, 246 steps, all the engine's own answers. Three tests: `tasbeehVectorsAreUpToDate`, `everyTurnBackTakesOneCountWithinTheRound` (the undo this task pins) and `theBuiltInsAreThePhonesInItsOrder`.
- **`iosApp/WatchCore/Sources/WatchCore/`** (the Swift port; `swift test` holds it):
  - `Tasbeeh.swift`: `Dhikr`, `DhikrPart`, `TasbeehPreset`, `TasbeehState` (`Codable`), `TapEvent`, `TasbeehEngine` (`tap`, `undo`, `currentPart`, `reset`, `nextRound`, `partEnds`), `TasbeehPresets` (`builtIn`, `defaultId`, `maxTarget`, `default`, `custom(_:)`, `list(_:)`) and `TasbeehTiming`;
  - `CrownCounter.swift`, `TasbeehCounter.swift` (with `TurnResult` and `reset(now:)`), `TasbeehFeel.swift`, `TasbeehStore.swift`, `TasbeehSpeech.swift` (with `CounterAction` and `CounterDoes`), `TasbeehScreen.swift` (`TasbeehScreenModel`, `PhraseRow`, `TasbeehScreen.model` and `rows`);
  - `DebugSample.swift`: `DebugArguments` reads `-taqwaFresh`, `-taqwaTasbeehCount` and `-taqwaTasbeehPhrase`.
- **`iosApp/WatchShared/RingGauge.swift`:** `notches` and `RingNotches`, `appRing`'s ticks.
- **`iosApp/TaqwaWatch/`:** `TasbeehView.swift` (`TasbeehSession`, `TasbeehPage`, `TasbeehView`, `TasbeehLayout`), `PhraseListView.swift`, `Haptics.swift`; `TaqwaWatchApp.swift` (the session above the pager, settled on leaving `.active` and on a swipe away); `DebugLaunch.swift` (`apply(toTasbeeh:)`).
- **Tests:** `TasbeehVectorsTests` 8, `CrownCounterTests` 4, `TasbeehCounterTests` 14, `TasbeehFeelTests` 7, `TasbeehStoreTests` 6, `TasbeehSpeechTests` 9, `TasbeehScreenTests` 8, and one more `DebugSampleTests` case: 57 in `WatchCoreTests` (172 to 229). `RingGaugeTests` gains 2 (`WatchViewsTests` 4 to 6). UI: `TasbeehTests` 3 and `TasbeehCrownTests` 3.
- **`tools/add-watch-targets.rb`:** needed no edit. Its re-run added the seven core files to both targets, the three views to the app and the two UI-test files to their target; a second run left `project.pbxproj` byte-identical.
- **The evidence:** 11 shots in `docs/superpowers/reports/2026-10-24-apple-watch-pass/` and the crown's measurement, in its README's Task 8 section.
- **Plan 4 Task 8** gains an as-built note.

## The plan's tests, by name

Every test the plan's Step 2 names exists under its name: `testEveryTasbeehSequenceMatchesTheVectors`, `testUndoAtZeroStaysAtZeroInTheRound`, `testUndoAcrossAPartBoundaryShowsTheEarlierPart`, `testTheCrownCountsWholeDetents`, `testAFastSpinPastTheEndOpensTheNextRound`, the eight `TasbeehCounterTests` (`testTheHoldRollsOverToZeroOfTheNextRound`, `testACrownSpinThroughTheHundredDuringTheHoldKeepsEveryCount`, `testAnUndoDuringTheHoldIsKept`, `testLeavingDuringTheHoldSavesTheNextRound`, `testOpeningOnAFinishedSetHolds`, `testOneWriteAfterTheLastCount`, `testUndoAtZeroWritesNothing`, `testATurnAppliesEveryDetentInOrder`), the four `TasbeehFeelTests` (`testAFastTurnAcrossAPartsEndBuzzesItAlone`, `testNoTickCutsThePartsBuzz`, `testNoTickCutsTheSetsBuzz`, `testUndoAtZeroIsSilent`), `TasbeehSpeechTests` (the English, Saudi, his own and Turkish cases), `testTheSetsRingHasNotchesAt33And66`, `testNotchesShowOnceTheArcPassesThem`, `testTheTransliterationShowsOnlyUnderALatinInterface`, `testHisOwnPhrasesFollowTheBuiltIns`, `testADeletedPhraseFallsBackToAfterPrayer`, `testCountsAreKeptPerPhrase`, `testTheStoreIsExcludedFromBackup`, `testTheSpokenStateIsCountOfTarget`, and the UI tests `testATapCountsAndTheValueIsSpoken`, `testASlowTurnCountsAndATurnBackUndoes`, `testAFastSpinFrom99OpensRoundTwo` and `testTheSpokenValueFollowsEachStep`.

Beyond them: the Galaxy's `WatchTasbeehTest` cases `aTurnAcrossAPartsEndAndTheSetsBuzzesTheSet` and `aPartsBuzzIsAlwaysSent` restated, a count inside the hold, settling after a burst, reset, a count above the target, a phrase of nothing, the built-ins against both vector files, a blank or taken id left out, targets held to 1–1,000, the selection read back, a file that does not read, `clear`, the actions' order, a count above the target as drawn and said, the list's rows in English and Saudi digits, and the UI tests `testThePhraseListOpensFromTheDhikrAndChoosesAPhrase` and `testResetAsksAndGoesBackToTheStart`.

## The rules as built

- **The engine** is `TasbeehEngine.kt` line for line, held step for step by the vectors. The undo is the plan's: one count less within the round, read as the count shown (held to the target), nothing at 0, the round unchanged. Kotlin's is the same, so the Lead needed no word.
- **The counter** (`TasbeehCounter`) is the phone's `TasbeehViewModel` hold and debounce on explicit time: a set's end holds for 1.0 s and rolls over to 0 of the next round, saved at once; a count or a turn back cancels the hold (a count inside it opens the next round at 1); each burst is saved 0.3 s after its last change; `settle()` rolls a pending hold over first. A state read back is held as the Galaxy's `TasbeehCodec` holds it (the count 0 to the target, the round from 1), so one opened at its target holds. A change that changes nothing writes nothing.
- **The haptic** (`TasbeehFeel`): the set's end, then a part's, then the count's click, then the turn back's; a part's or the set's always, opening a 0.14 s or 0.40 s quiet window in which no click or turn back plays. `Haptics` plays `.click`, `.directionDown`, `.directionUp` and `.success`.
- **The crown:** `digitalCrownRotation(detent:)` from −1,000,000 to 1,000,000 by 1, sensitivity `.medium`, its own haptic off; `CrownCounter` reads each value as its nearest detent and is seeded afresh when the page appears and when the phrase changes. One detent is 1/8 of a turn in `XCUIDevice.rotateDigitalCrown` (measured).
- **The store:** one `tasbeeh.json` in `<Application Support>/Tasbeeh`, the app's own, written atomically on each save, the directory marked excluded from backup. A phrase the phone no longer sends falls back to After prayer, which becomes the choice.
- **The list:** the seven in the phone's order, then his phrases from `feed.tasbeeh` in the phone's order, a blank one (Kotlin's `isBlank`) or a built-in's or repeated id left out, targets held to 1–1,000. Each row's target is "33·33·34" for the set, with the Arabic comma in digits whose nought is a dot.
- **The page:** one element for VoiceOver, `tasbeeh.counter`, labelled with `TasbeehSpeech.name` and valued with `TasbeehSpeech.state` in the feed's language; its default action counts (hint `countAction`), its named actions are take one away and choose a dhikr, and an adjustable increment and decrement count and take back. A count or turn back from an action posts the new value as an announcement.

## The errata and rulings

- **p4 #1 and #6 (VoiceOver reaches the list):** `phrasesAction` is the counter's second named action, after `undoAction`, and each list row is one element ("After prayer, 100") with the selected trait on the phrase counted. XCUITest cannot perform a named action (watchOS 27's `XCUIVoiceOverService` moves and reads only), so the order is held by unit tests and the view offers exactly `model.actions`; plan 5 hears it on her watch.
- **p4 #5:** every spoken line is `Speech.line` in the feed's language.
- **p4 #7 (Reset):** the list's last row, the phone's question, `TasbeehCounter.reset(now:)` saved at once; the dialog's dismiss is the system's.
- **p4 #8:** the tasbeeh arguments set no feed; the UI tests' samples keep intake off as before.
- **p4 #9:** `-taqwaFresh 1` and `-taqwaTasbeehPhrase` join `-taqwaTasbeehCount`; every tasbeeh UI test sets its whole state by them.
- **p4 #12:** `feed.words` is read once a drawing and once an announcement.
- **p4 #13 and #14 (Rulings 60 item 4 and 61 item 3):** the built-ins are held to the words vectors' `presetIds` and the tasbeeh vectors'; an id the built-ins lack is listed after them in the phone's order only when its words are not blank. Those ids are his phrases from `feed.tasbeeh`, an ordered array. The words' names are a dictionary, which keeps no order, and are only ever looked up by id: no screen lists a name with no dhikr to count, so the phone's order of an unknown name id is never needed.
- **p4 #15 and Ruling 64 items 3 and 5:** `RingGauge` is reused with `rtl: false`, its notches drawn inside its fixed left-to-right group and held by two new `RingGaugeTests`; the counter's label and value are always set and a row's traits are one modifier. No UI test here runs on the clock.
- **Ruling 64 item 4:** the session settles whenever the scene leaves `.active`, so a lowered wrist (`.inactive`) loses no count and rolls a pending hold over, as the phone's flush on leave does; nothing on the page moves the wearer's view.
- **Ruling 39:** the counts are the watch's own, kept in a directory excluded from backup, and nothing is sent to the phone; no location, coordinate or network is touched. The promise line "Only your own devices can see your location." is plan 5's to apply; nothing here changes it.
- **Rulings 41, 42 and 43** are the face's, untouched here.

## Where the build differs from the plan

- **The dhikr's line** stands at 44 and 57 (14 points), not `aTasbeeh`'s 30 and 45 (15): watchOS's clock takes the top 28 points of the SE 3, and the first build's line sat under the clock and never opened the list (the debug tree put the page's frame at y 40 and the status bar's window over the line).
- **The page ignores the safe area**, so the counter draws over the whole screen and the dhikr's line is tappable. The tap itself first covered only the page's own frame, y 40 to 171: review round 1 found it (below), and its fix round put the tap on the drawing's full-screen frame, so a tap anywhere counts, the hint's band and the page dots' row included.
- **The hint is always drawn**, as `aTasbeeh` draws it at 64; the Galaxy shows its hint at 0 of round 1 alone.
- **The list is a stack in a `ScrollView`**, not a `List`, which makes only the rows on screen: the Reset row and his phrases were missing to the UI test.
- **`-taqwaTasbeehPhrase <id>`** is new, for the shots' Astaghfirullah.
- **The UI tests wait for the page to stand** before touching it: on the first launch after the SE 3 booted, the tap test's tap came while the pager scrolled from Today to Tasbeeh and counted nothing.

## Checks

- `./gradlew :watchcore:testAndroidHostTest` (TasbeehVectorsTest 3/0, after the file was written with `-Ptaqwa.updateVectors`).
- `swift test --package-path iosApp/WatchCore`: 229/0 in `WatchCoreTests` and 6/0 in `WatchViewsTests`, no warning.
- Mutants, each in a scratch copy and each killed: a tap that keeps the hold, no quiet window, a blank phrase kept, a crown that floors, the spoken count not held to the target, a turn back at 0 that writes, a settle that keeps the hundred, a fallback not written, notches shown only strictly past, the actions reversed, the set's buzz not first; and in the worktree, the notches drawn anticlockwise.
- `scripts/watch-build.sh test` on the SE 3: 13/0 (LaunchTests 3, TasbeehCrownTests 3, TasbeehTests 3, TodayReadingOrderTests 4), no warning in the watch's sources.
- `tools/add-watch-targets.rb` byte-identical on a second run; `tools/i18n-check.py` clean; `git diff --check` clean.
- `scripts/test.sh` whole: exit 0 (BUILD SUCCESSFUL 4 m 49 s): test-bump and the privacy tests PASS, privacy-check clean but for the 186 rows awaiting M3, `swift test` first 229 + 6, then the host suites shared 2274, widgetcore 111, watchcore 227 (224 and this task's 3), the K/N simulator suites shared 2218, widgetcore 109, watchcore 197, and wearApp 398, 0 failures, no EOFException. Some Gradle suites came from the cache or were up to date: no input of theirs changed but `:watchcore`'s new host test, which ran.
- `scripts/ios-build.sh`: BUILD SUCCEEDED, no warning in the watch's sources; `check-watch-bundle` every rule holds (TaqwaWatchWidgets.appex 3,996 KB of 5,120, TaqwaWatch.app 10,504 KB of 15,360, both Debug; rule 4b skipped ad hoc, rule 8 skipped Debug). The widgets compile WatchCore whole, the tasbeeh port with it, as the plan has both targets compile every WatchCore file: about 500 KB more in this Debug build than at R64's 3,488.

## Left for later

- Her watch (plan 5): the haptics' feel and windows, VoiceOver's two named actions and the announcements heard in an Arabic feed on an English watch, and the crown's feel at sensitivity `.medium`.
- "After prayer" takes two lines in the list on the 40mm at 13 points.
- The list's Reset dialog's dismiss is the system's word in the watch's language.
- The watch's own Text Size setting (plan 5, on her watch): by launch argument on the SE 3, XS to L draw as the default, XL, XXL and XXXL grow the lines by 5.5, 11 and 16 % up to their caps, and the accessibility sizes by 27 % and more (fix round 2); on the Series 12 (46mm), XS to its own default, XL, draw as the default, and XXL, XXXL and the accessibility sizes grow by 5, 10.5 and 20.5 % and more (fix round 3). `xcrun simctl ui content_size` is unsupported on watchOS, so the setting itself could not be tried, nor the 41, 42, 44, 45 and 49mm sizes.

## Fix round 1 (31 October, after review round 1)

Review round 1 (two lenses) found no Critical and two Importants. Both are fixed; no ruling is touched by either (Rulings 39, 41, 42 and 43 hold: no location, network, face or promise line changes).

1. **A tap did not count in a band along the foot.** The tap was a modifier after `.ignoresSafeArea()` on the page, which covers the page's own frame alone (y 40 to 171 of the SE 3's 197 points). So a tap on the hint "Turn the crown or tap" or on the page dots' row counted nothing and played no haptic (review: y 175 to 195 at three columns), and, as the fix round found, nor did one at y 33 beside the dhikr's line. Fixed: the tap is on `TasbeehView`'s full-screen frame, before its `ignoresSafeArea`. Two UI tests were written first and seen red on the first build: `testATapOnTheHintCounts` and `testATapAnywhereBelowTheClockCounts` (under the clock either side of the line, the crown's arrows, the ring, the hint's ends, the page dots' row and the foot's corner, one count each, and the list never opened). A throwaway probe (never committed) then tapped five columns (x 6.5 to 155.5) at rows from y 3 to 196.5: 88 of 91 taps counted, and the three that did not were on the clock's own digits (x 137.7, y 10 to 24), which the system takes. The line's top at y 30 still opens the list, and a swipe from the page, the hint's band included, still turns it. The as-built's "that bar takes every touch" is corrected: on the fixed page, only the clock's digits take a touch at the top.
2. **The screens ignored the watch's text size.** Fixed as the Galaxy does it (plan 3's call 15): `TasbeehType` in WatchCore holds the Galaxy's caps (the list's names, targets and Reset, the transliteration, "ROUND n" and "of 100" 1.3; the dhikr 1.15; the count and the hint 1.0) and `grow(scale, cap:)`, from a floor of 1, with `@ScaledMetric(relativeTo: .body)` read as Today's rows read it; `testTheLinesGrowWithTheTextSizeUpToTheGalaxysCaps` holds the caps and the floor. The list's title is the system's and grows itself. The first larger-text shot broke "Alhamdulillah" inside the word ("Alhamdulilla / h"), so a grown row name, and the dhikr's one line, are drawn at the largest of three steps from the grown size down that fits whole (a row's widest word in the room beside its target), and at the drawing's size otherwise. Nothing changed at the default size: the five counter shots and the two list shots of Task 8, taken again on this tree, are the committed ones pixel for pixel outside the clock. Shots at `UICTContentSizeCategoryAccessibilityXL`: `tasbeeh-astaghfirullah-64-axl.png`, `tasbeeh-ar-ly12-40-axl.png`, `tasbeeh-list-capetown-top-axl.png` and `tasbeeh-list-capetown-his-axl.png` (the pass README's Task 8 section). This round read the scale from a 1-point base, which review round 2 found the pixel grid hid at every standard size: fix round 2 below.

The reviews' minors did not reach this fix round's brief, so none is recorded here as left.

Checks on the fixed tree: `swift test` 230/0 in `WatchCoreTests` (one more) and 6/0 in `WatchViewsTests`, no warning; `scripts/watch-build.sh test` on the SE 3 15/0 (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 5, `TodayReadingOrderTests` 4), no warning in the watch's sources; `scripts/ios-build.sh` BUILD SUCCEEDED with `check-watch-bundle` holding every rule (TaqwaWatchWidgets.appex 4,016 KB of 5,120, TaqwaWatch.app 10,688 KB of 15,360, Debug); `scripts/test.sh` exit 0 (bump and privacy PASS, `swift test` first, the Gradle suites up to date, as no Kotlin input changed); `tools/add-watch-targets.rb` left `project.pbxproj` unchanged (no file added); `tools/i18n-check.py` clean; `git diff --check` clean.

## Fix round 2 (31 October, after review round 2)

Review round 2 (two lenses) found no Critical and one Important: round 1's text-size fix was only partly right. No ruling is touched by the fix (Rulings 39, 41, 42 and 43 hold: no location, network, face or promise line changes).

1. **The watch's standard text sizes above the default grew no line.** `TasbeehView` and `PhraseListView` read the scale as `@ScaledMetric(relativeTo: .body)` of 1 point, as Today's rows do, and SwiftUI rounds a scaled metric to the pixel grid, half a point on the watch. So it read 1.0 from XS to XXXL and 1.5 at every accessibility size (review round 2's probe on the SE 3; a base of 100 read 89, 94.5, 100, 100, 105.5, 111 and 116 from XS to XXXL, and 127, 132.5, 138 and 138 at AccessibilityM, L, XL and XXXL). XL, XXL and XXXL drew as the default, and every accessibility size jumped to the caps. Round 1's README and this report called that the platform's behaviour; it was the base's. Fixed: both views read `@ScaledMetric(relativeTo: .body)` of `TasbeehType.scaleBase`, 100 points, and `TasbeehType.scale` divides it back, so the grid's half point is half a percent. Two tests were written first and seen red on the 1-point base:
   - `testTheTextSizeIsReadFromABaseThePixelGridKeeps` (WatchCore): the measured ratios at XL, XXL, XXXL and AccessibilityM, rounded to the half-point grid at the base, read back within half a percent and grow a line capped at 1.3; the default reads exactly 1. Red at a base of 1 (1.0 at XL, XXL and XXXL, 1.5 at AccessibilityM).
   - `testTheStandardSizesAboveTheDefaultGrowTheLines` (UI, SE 3): Astaghfirullah's transliteration is wider at XL than at the default and wider again at XXXL, and its dhikr wider at XL and XXXL. Red at a base of 1 (61.0 points wide at both the default and XL).

   Measured on the SE 3 after the fix: the transliteration (cap 1.3) is 61.0 points wide at the default and at L, 64.0 at XL, 70.5 at XXXL, 77.0 at AccessibilityM and 79.0 at AccessibilityXL; the dhikr (cap 1.15) 62.5, 65.5, then 71.5 from XXXL on. The seven default shots and the four AccessibilityXL shots, taken again, are the committed ones pixel for pixel outside the clock (1.38 is above every cap). Four shots at XXXL are added: `tasbeeh-astaghfirullah-64-xxxl.png`, `tasbeeh-ar-ly12-40-xxxl.png`, `tasbeeh-list-capetown-top-xxxl.png` and `tasbeeh-list-capetown-his-xxxl.png` (the pass README's fix round 2 section). Every word stays whole in them. The as-built, the pass README's fix round 1 line and this report's "Left for later" now say what the simulator measured.

**Today, for the Lead.** `TodayView.swift:47` (Task 7, already merged) reads the same 1-point base, so Today's rows grow only at the accessibility sizes, straight to their 1.3 cap. It is unchanged here, as the review left it to the Lead. `TasbeehType.scale` and `scaleBase` would serve it as they stand. The pass README's Today row now names the cause.

**Minors left.** Code lens, review round 2 (seven):
- Fixed here: the stale "every touch there" in this report and in the pass README.
- `TasbeehSession`'s `use`, `select` and `settle` are untested: they are in the app target, which `swift test` does not reach.
- The caps test cannot catch a view that uses the wrong cap.
- No test holds that a tap on the dhikr's line does not also count. Review round 2's probe saw it count nothing.
- No test holds that the crown on Today leaves the count alone.
- Growth at the default size is proven on the 40 mm only.
- The widgets extension compiles the tasbeeh port: 4,016 KB of 5,120 in Debug, before Qibla.

The spec lens's four minors are in its reviewer's report. They were not in this round's brief, so they stand as that report records them.

Checks on the fixed tree:
- `swift test`: 231/0 in `WatchCoreTests` (one more) and 6/0 in `WatchViewsTests`, no warning.
- `scripts/watch-build.sh test` on the SE 3: 16/0 (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 6, `TodayReadingOrderTests` 4), no warning in the watch's sources.
- `scripts/ios-build.sh`: BUILD SUCCEEDED, and `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4,016 KB of 5,120, TaqwaWatch.app 10,688 KB of 15,360, Debug).
- `scripts/test.sh`: exit 0. Bump, privacy and `test_privacy_check` PASS; privacy-check is clean but for the 186 rows awaiting M3; `swift test` ran first, 231 + 6; the Gradle suites were up to date, since no Kotlin input changed.
- `tools/add-watch-targets.rb` left `project.pbxproj` and the scheme unchanged (no file was added). `tools/i18n-check.py` and `git diff --check` are clean.

## Fix round 3 (31 October, after review round 3)

Review round 3 (two lenses) found no Critical. The code lens found one Important, and the spec lens was clean. No ruling is touched (Rulings 39, 41, 42 and 43 hold: no location, network, face or promise line changes).

1. **The finding: the 100-point base would grow the lines at a 44mm or larger watch's default size.** Apple gives 40, 41 and 42mm watches Large and 44, 45, 46 and 49mm ones XLarge as their default text size ("Supporting multiple watch sizes"). The review read `UIFontMetrics`, which `@ScaledMetric` uses, as scaling from Large. If so, a large watch's default would read about 105.5, and every line would draw about 5 % larger with no setting changed. The review asked for a measurement on the Series 12 (46mm) first.

   **Measured, the lines do not grow.** On the Series 12 at its default (`.xLarge`), the base reads 100, as `UIFontMetrics.default.scaledValue(for: 100)` does; L and M read 95. The body style scales from each watch's own default, not from Large. So the floor already starts at the watch's own default, and the code's claim that nothing drawn changes at the default holds on the 46mm too. Neither the review's option (a), a floor taken from a table of watch sizes, nor option (b), a ruling, is needed, and no drawing code changed. The pass README's fix round 3 section has the whole table and the shots. At its default the 46mm draws Astaghfirullah's lines as at XS to XL, and its counter is the same pixel for pixel at the default, XS, L and XL.

   What changed:
   - `testTheWatchsOwnDefaultSizeDrawsTheDrawing` (UI, new): on any watch, the default draws both lines as wide as XS and L do. It is green on the SE 3 and on the Series 12. On the Series 12 it is red against a mutant that takes the scale from Large (82.0 points at the default against 78.0).
   - `testTheStandardSizesAboveTheDefaultGrowTheLines` (UI) now starts from the watch's own size, read from `WKInterfaceDevice` in the test's own process: XL above L, XXL above XL. As fix round 2 wrote it, it asserted growth at XL, and it was red on the Series 12, where XL is the default.
   - WatchCore: `testEachWatchsOwnDefaultDrawsTheDrawing` (new) holds both watches' readings, at their defaults and below them, to the drawing's sizes for every cap. `testTheTextSizeIsReadFromABaseThePixelGridKeeps` gains the Series 12's ratios.
   - The comments on `TasbeehType`, `scaleBase` and `scale`, on the metric in `TasbeehView` and `PhraseListView`, and in the tests now name the watch's own default and the measurements on both watches.
   - Two shots at the 46mm's default: `tasbeeh-astaghfirullah-64-46mm.png` and `tasbeeh-list-capetown-top-46mm.png`.

   These tests could not be seen red first on this tree, because the defect is not in it. The red runs are of fix round 2's test on the Series 12 and of the mutant. The 41, 42, 44, 45 and 49mm sizes were not measured, because no simulator of theirs is in the execution map's locks. The SE 3 stands for the Large group and the Series 12 for the XLarge group.

**Today, for the Lead.** Today's rows (`TodayView.swift:47`) still read a 1-point base. On the 46mm it reads 1.0 up to AccessibilityM and 1.5 from AccessibilityL, so there they grow only from AccessibilityL. With `TasbeehType.scaleBase` they would keep the drawing at every watch's own default, as Tasbeeh does.

**Minors left.** The spec lens's three, from review round 3:
- A flake: once, `testTheSpokenValueFollowsEachStep` stayed at "33 of 100. Next, Alhamdulillah" after the third slow turn. It was not seen in that lens's 41 later runs, nor in this round's run (17/0).
- The crown's arrows mirror to the left edge but keep their height (plan 5).
- The known items stand: Today's 1-point base (above). The default size on a 44mm or larger watch is now measured, on the 46mm only.

The code lens's eight minors are in its reviewer's report. They were not in this round's brief, so they stand as that report records them.

Checks on the fixed tree:
- `swift test`: 232/0 in `WatchCoreTests` (one more) and 6/0 in `WatchViewsTests`, no warning.
- `scripts/watch-build.sh test` on the SE 3: 17/0 (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 7, `TodayReadingOrderTests` 4). On the Series 12, by `WATCH_DEST`, the two text-size tests: 2/0.
- `scripts/ios-build.sh`: BUILD SUCCEEDED, and `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4,016 KB of 5,120, TaqwaWatch.app 10,688 KB of 15,360, Debug).
- `scripts/test.sh`: exit 0. The versions agree at 1.3.0 (39). Bump, privacy and `test_privacy_check` PASS. Privacy-check is clean but for the 186 rows awaiting M3. `swift test` ran first, 232 + 6.
- `tools/add-watch-targets.rb` left `project.pbxproj` and the scheme unchanged (no file was added). `tools/i18n-check.py` and `git diff --check` are clean.
