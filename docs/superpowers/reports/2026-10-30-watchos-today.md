# Plan 4 Task 7: the app's pages, and Today

Lane A worked in `watch-apple`, plan 4's own worktree, on 30 October 2026. The base was `d7de3183` ("Merge watch-apple: P4.6"), `watch-companion`'s head: `git merge --ff-only watch-companion` found it already up to date. `xcodebuild -checkFirstLaunchStatus` exited 0 (Xcode 27.2's components are in), so every simulator step ran and none was deferred under Ruling 59.

The work was test-first. `TodayTests` was written and seen failing before any code existed: `swift test` did not build, because `TodayModel` and the rest were missing. The two UI tests were added to their target and seen failing on the SE 3: `scripts/watch-build.sh test -only-testing:TaqwaWatchUITests/TodayReadingOrderTests` failed 2 of 2, finding no `today.ring` and no `page.today`. None of it was committed red.

**In one line:** the watch app is now three pages a swipe apart, Today, Tasbeeh and Qibla. Today is the phone's countdown ring above the day's six times, in the phone's words, digits and clock, and VoiceOver reads the ring's one sentence first, then each row in the order of the day. With no feed every page shows the watch's own line, and a newer format seen says "Update Taqwa on your watch" first.

## What was made

- **`iosApp/WatchCore/Sources/WatchCore/Today.swift`** (the Swift port; `swift test` holds it):
  - `RowState`, `TodayRow`, `TodayModel` (the plan's fields, plus `language`, `latinScript` and the computed `focus`), `OwnMessage` and `TodayScreen`;
  - `Today.screen(_:newerVersionSeen:now:seconds:)` and `Today.clock(_:secondsLeft:seconds:)`;
  - `WatchTimes.of(_ time:)`.
- **`WatchRules.swift`:** `capitals(_:language:)`, the casing `label` already used, now shared with Today's ring label.
- **`iosApp/TaqwaWatch/TodayView.swift`:** `TodayPage` (the `TimelineView`, each frame `Today.screen` at its own date), `TodayView`, the ring, the rows and the pip, and `TodayLayout` (`aToday`'s places).
- **`iosApp/TaqwaWatch/MessageView.swift`:** a page's one line: the watch's own (`init(own:)`) or a word of the feed's (`init(feedWord:of:)`).
- **`TaqwaWatchApp.swift`:** `RootView` opens `Pages` (or the DEBUG gallery), `Page`, and `Pages`, the `TabView` with `.tabViewStyle(.page)`. `OwnLineView` is gone, replaced by `MessageView`.
- **`AppClock.swift`:** `at(_ date:)`, with `now()` as `at(Date())`.
- **`WatchShared/NextPrayerViews.swift`:** `RingGauge` takes a `thickness` (0.12 by default, as before).
- **Tests:**
  - `TodayTests`: 29 tests. The plan's thirteen under its names, and sixteen more. Ten restate plan 3's `TodayModelTest` by its names (`testTheRingTakesTheFeedsDigitsWhenItNamesNoneOfItsOwn`, `testTheHoursAreNotPaddedAndAStartBeginsTheRingAgain`, `testUnderAMinuteTheRingSaysSo`, `testCapitalsOnlyUnderALatinInterface`, `testTheLabelIsCasedInThePhonesLanguage`, `testWithoutSpokenWordsTheRingSaysWhatItShows`, `testATwelveHourSunriseRowCarriesItsDayPeriod`, `testSunriseIsNeverCurrent`, `testAfterIshaTheNextIsTomorrowsFajr` and `testAFridayNamesJumuah`), and `testASpentFeedIsSpent` also holds `pastTheLastStartThereIsNoRing`'s instants. The other six are `testAlwaysOnOnAWholeMinuteShowsTheMinuteThatHolds`, `testTheClockIsNeverNegative`, `testTheListOpensOnTheLitRow`, `testYesterdaysIshaAfterMidnightIsNoRowOfTodays`, `testASpokenLineCarriesTheFeedsLanguage` and `testABlankWordIsEnglish`.
  - `iosApp/TaqwaWatchUITests/TodayReadingOrderTests.swift`: `testTodayReadsTheRingThenTheDay` and `testANewerFormatWithNoFeedSaysUpdate`.
- **`tools/add-watch-targets.rb`:** needed no edit. Its re-run added `Today.swift` to both targets, `TodayView.swift` and `MessageView.swift` to the app, and `TodayReadingOrderTests.swift` to the UI tests. A second run left `project.pbxproj` byte-identical.
- **The evidence:** 14 shots in `docs/superpowers/reports/2026-10-24-apple-watch-pass/` (the README's Task 7 section).
- **Plan 4 Task 7** gains an as-built note.

## The rules as built

- **The states (plan 3's call 5):** no feed is `.message(.updateOnWatch)` once a newer format has been seen, else `.message(.openOnPhone)`. A feed with no start ahead of `now` (`WatchRules.moment` nil: past its last start, or before its first day) is `.spent(words.updateOnWatch)` or `.spent(words.openOnPhone)`, in the feed's words. Otherwise `.ready`.
- **The ring:**
  - `ringLabel` is `filled(ui.nextIn, name)`, in capitals by the feed's language under `latinScript` ("ASR IN", Turkish "ASR İN"), and as written otherwise ("متبقٍّ على العصر");
  - `countdown` is `Today.clock`: H:MM:SS, or H:MM without seconds, the hours unpadded and never negative, in `ringDigitZero ?? digitZero`;
  - `time` is the next start's `clockText` from its own day, or `words.updateOnWatch` with `notice` true once a newer format has been seen;
  - `spoken` is `spokenCountdown` with `spokenClock`, or `"<ringLabel> <clockText>"` with no spoken words, followed by "، " (right to left) or ", " and the update when the notice is on;
  - `progress` is the moment's, and `mirrorsRing` is `ui.rtl`.
- **The always-on screen:** without seconds, the screen is the one that holds for the minute from `now`. When `now` is a whole minute before the next start it is drawn a second later, so the minute's frame at 14:58:00 reads "1:11" and not the instant's "1:12", as a complication's minute does (P4.6 fix round 1, Review Focus 3).
- **The rows:** the six of `WatchRules.today`'s day, lit by `litOnList`. A row is current for the lit prayer (never Sunrise), passed once its start is at or before `now`, and upcoming otherwise. `clock` is `clockText`, `spoken` the name and `spokenClock` joined by ", ", and `stateWord` `rowCurrent`, `rowPassed`, or `rowNext` on the next start's row (the same prayer at the same instant), nil otherwise. The list stands on `focus`, the lit row or the first upcoming prayer, whenever it is seen afresh: when it first appears, each time the scene becomes active, and when the focus moves (fix round 1).
- **The view (`aToday`, in its 172 × 212 units, scaled by the screen's width / 172):**
  - the ring is `RingGauge`, centred at (86, 66), radius 52 and width 5.4, drawn left to right with its flip explicit, so it fills anticlockwise from 12 o'clock in Arabic (measured in the shots);
  - the label is ExtraBold 8.8 in amber, tracked 1.2 under Latin letters and 13/11.5 larger outside them; the countdown Manrope Light 23 with tabular figures; the time SemiBold 10.5 in mist; on the baselines 52, 77 and 92;
  - the notice is SemiBold 9.5 in amber, on a baseline at 90, two lines in a box 64 wide, shrinking to 0.7 at most;
  - below the ring, from 129, a `ScrollView` of the rows: the pip centred at 20, the name from 32 (ExtraBold and amber when lit, slate when passed, ink to come), the time ending at 156 (slate when passed), 21 high; Sunrise 10/12 the size, 17 high, in slate, with no pip; the row at the screen's foot dimmed to a third, as the drawing has it;
  - a `TimelineView` on a `.periodic` schedule from a whole minute, by 1 s, or by 60 s while `isLuminanceReduced`; each frame maps its own date through `AppClock.at`, so frames drawn ahead for the always-on screen show their own instant.
- **The pages:** `TabView` with `.tabViewStyle(.page)`: Today, Tasbeeh and Qibla, in that order. The pager runs left to right in every language, as the Galaxy's (plan 3's call 4), and each page's content takes the feed's direction, or with no feed the watch's own. With no feed, all three show `MessageView`'s own line. With a feed, Today draws it, and Tasbeeh and Qibla show `words.tasbeeh` and `words.qibla` until Tasks 8 and 9, a spent feed included. `-taqwaPage today|tasbeeh|qibla` opens on that page.
- **VoiceOver (Ruling 38, Ruling 42):** the ring is one element (`today.ring`), labelled `spoken`, read first; each row is one element (`today.row.<TIME>`), labelled `spoken`, with `stateWord` as its value; the ring's stroke and the pips are hidden. Every line is `Speech.line` in the feed's language (erratum p4 #5). Each page is a container (`page.<page>`).
- **Words and time:** every word drawn or spoken is the feed's, but the watch's own two lines with no feed (spec 1.3). No `DateFormatter`, `NumberFormatter`, `Date.FormatStyle` or `Text(_:style:)` is used; the only clock read is the instant.

## The errata and rulings

- **p4 #2:** `TodayRow.time` is the `WatchTime`, Sunrise included, and `WatchTimes.of(_:)` reads a day's instant by it.
- **p4 #5:** the ring's label and each row's label and value are `Speech.line(text, language: model.language)`. That the voice follows it is plan 5's check on her watch, as for the complications.
- **p4 #8:** every UI test and shot launches with a sample or `-taqwaNoFeed`, so intake and handshakes are off for the run (Task 5's `holdsState`).
- **p4 #9:** both UI tests set their whole state by launch arguments and pin the watch's own language to English, as `LaunchTests` does.
- **p4 #12:** `feed.words` is read once per `Today.screen` (each frame) and once per drawing of the pages.
- **p4 #14:** no strict per-position check is added and no model class is split, so `newerPhone` needs no new key.
- The other p4 errata touch Tasks 3, 6 and 8 to 11.
- **Ruling 39:** the pages add nothing to what the watch holds or sends: no coordinate exists on the watch, Today sends nothing, and no location is asked for (`check-watch-bundle.sh` rule 5 holds). The promise line "Only your own devices can see your location." is plan 5's to apply; nothing here changes it.
- **Ruling 42:** on Today the ring's sentence is read first, then the rows in the order of the day, each one element, as the UI test reads them.
- **Rulings 41 and 43** are the face's and Task 11's, untouched here.
- **Ruling 56:** the decoder is used as it is.
- **Ruling 59:** nothing deferred; the first launch was done.

## Where it differs from the plan

Each point is in plan 4 Task 7's as-built note:
1. `TodayModel` gains `language` and `latinScript`, and a computed `focus`.
2. `Today.screen` gains `seconds: Bool = true`, with the always-on minute rule above.
3. `RingGauge` gains `thickness`.
4. The notice's size, place and box are the builder's (the drawing has none).
5. The rows grow with the watch's text size up to 1.3 times, as the Galaxy's do (plan 3's call 15); the ring keeps its size.
6. The pager runs left to right in every language (plan 3's call 4), and the page dots stay (`.page`).
7. `AppClock` is `at(_ date:)`, so the `TimelineView`'s frames show their own instant.
8. The UI test reads the tree depth first from a snapshot: a query's order is not VoiceOver's (mutant 1 below). XCUITest lists the texts drawn inside each element, which VoiceOver does not visit, so it cannot hold that the ring's parts are not read alone; it reads only the identified elements.
9. The shots are at 14:58:24, the drawing's "1:11:36".
10. The plan's 12-hour strings carry the phone's no-break space ("4:10 PM" is `4:10\u{00A0}PM`), as in Task 6.

## The steps and their evidence

1. **Step 1, the failing tests:** as above.
2. **Step 2:** `swift test` did not build ("cannot find type 'TodayModel' in scope"). `scripts/watch-build.sh test -only-testing:TaqwaWatchUITests/TodayReadingOrderTests` on the SE 3: TEST FAILED, 2 of 2 ("Today has drawn its ring", "the today page exists"). Not committed.
3. **Step 3:** built as above, with WIP commits `1951a007`, `440cc7d7`, `ba4d1533` and `7e8a1cc9`, folded by a soft reset into the task's one commit, whose tree is the tested one.
4. **Step 4:**
   - `swift test`: 154 tests, 0 failures, no warning (125 at the base).
   - `scripts/watch-build.sh test` on the SE 3 under `sim-se3`: TEST SUCCEEDED, 5 tests, 0 failures (`LaunchTests` 3, `TodayReadingOrderTests` 2), no watch-source warning.
   - `scripts/watch-build.sh` (build) and `build-for-testing` at `generic/platform=watchOS Simulator`: BUILD SUCCEEDED and TEST BUILD SUCCEEDED, no warning in a watch source.
   - `scripts/ios-build.sh` at its default iPhone 17 Pro: BUILD SUCCEEDED, no TaqwaWatch, WatchCore or WatchShared warning; TaqwaWidget 3 MB, TaqwaWatchWidgets 3 MB (limit 5); `check-watch-bundle` holds every rule (4b skipped ad hoc, 8 skipped Debug; TaqwaWatchWidgets 3428 of 5120 KB, TaqwaWatch.app 8212 of 15360 KB; 3340 and 7292 KB at the base).
   - `tools/i18n-check.py`: clean.
   - `tools/add-watch-targets.rb`: a second run byte-identical.
   - **Mutants**, each seen red:
     - on the SE 3, three: the ring declared after the rows (`testTodayReadsTheRingThenTheDay`: the rows read first); the Tasbeeh page with no feed ignoring the notice (`testANewerFormatWithNoFeedSaysUpdate`); the test's swipe removed (the Tasbeeh page never came). The first one survived the test's first form, which read a query's `allElementsBoundByIndex`: that order put the ring first wherever it was declared. The test now walks the snapshot depth first, and kills it.
     - in a scratch copy of the package, twelve of `Today.swift`: the glance rule for the list's; `digitZero` for the ring's digits; passed only after its instant; no whole-minute step; one comma for every language; capitals without the language; the next row by prayer alone; a spent feed ignoring the notice; the bare clock in a row; a row said with the drawn clock; Sunrise allowed to be lit; the language dropped. Ten were killed at once. "The next row by prayer alone" survived, and `testYesterdaysIshaAfterMidnightIsNoRowOfTodays` (a high-latitude Isha after midnight) now kills it. "Sunrise allowed to be lit" is equivalent: the lit time comes from a prayer, never Sunrise.
   - **The shots:** 14 on the SE 3, indexed in the pass README: the four samples at 14:58:24, no feed, no feed with the notice, a sample with the notice in English and Arabic, the Tasbeeh and Qibla pages, and Cape Town at an accessibility text size (the rows grown to their 1.3 cap, the ring unchanged). Each matches `aToday`. The ar-SA countdown is in Western digits under Arabic-Indic times, en12 shows "4:10 PM", and the Arabic ring fills anticlockwise: measured along the stroke, Cape Town and en12 read amber 0–240° clockwise from 12 o'clock, ar-ly12 and ar-sa 120–0°. The first notice shots cut "on your watch" against the ring's stroke; the notice was set smaller and higher, and re-shot.
5. **Step 5:** committed with the plan's message (below).

The device: `sim-se3` was held 20:55 to 21:00 (Step 2), 21:03 to 21:16 and 21:21 to 21:22 (Step 4). Each time the SE 3 was put back as found: the app and its UI-test runner uninstalled, the 15 Apple apps as found, no Taqwa group container, `en-US` and `en_US@rg=zazzzz`, unpaired, shut down. No `ios-pair` step is in this task. KB2604110095 and the work iPhone 12 were untouched.

## Open, for the Lead or a later task

- **The notice in French** ("Mettez à jour Taqwa sur votre montre") is the longest of the seven and will shrink towards the 0.7 floor in its 64-wide box. No French sample feed exists to shoot it here; Task 11's languages pass is the place.
- **The page dots** sit over the dimmed fourth row. The Galaxy hides its pager's dots (plan 3's call 16); the plan says `.page`, so they stay unless the Lead rules otherwise (`.page(indexDisplayMode: .never)`).
- The spent state was not shot: the scripted clock reaches only the sample's day 1, and a spent feed needs day 30's evening. `testASpentFeedIsSpent` holds it.

## Review round 1 and fix round 1

Review round 1 (30 October): the spec lens CLEAN with 8 minors, the code-quality lens NEEDS FIXES with one Important and 7 minors.

- **The Important, fixed:** the list scrolled to its focus only in `onAppear`. watchOS resumes the app's views when it is opened again from the clock face, a complication or the Dock, and the `TimelineView`'s frames keep the list's identity, so neither ran it again: opened at 12:40 and again at 20:30, Isha was lit in the faded fourth row, and after midnight the next start (Fajr) sat above the visible rows. `TodayRows` now also goes to the focus when the scene becomes active (`scenePhase`) and when the focus changes (`onChange(of: model.focus)`), without animation, as on its first appearance. (Ruling 64 item 4 then narrowed the first to an active scene after a background, so a lowered and raised wrist keeps the wearer's scroll; item 5 made the rows' state value inert, and moved `testTheListMovesWithTheLitRow` to 16:09:00 with 75 s: `2026-10-30-watchos-ruling-64.md`.) No ruling is touched: the ring is still the first element VoiceOver reads (Ruling 42), and nothing drawn changes at the first appearance.
- **Test-first:** two UI tests in `TodayReadingOrderTests`, both seen red on the SE 3 at their own checks with their preconditions holding, then green:
  - `testOpenedAgainTheListStandsOnTheLitRow` (14:57, frozen): opened on Dhuhr, dragged on past it, the app sent to the clock face (`XCUIDevice.shared.press(.home)`) and activated again without a relaunch; red at "opened again on the lit row, Dhuhr";
  - `testTheListMovesWithTheLitRow` (16:09:30 on a running clock): Dhuhr lit at the top, Asr lit at 16:10; red at "the list has moved to Asr".
  - Each pins its own trigger by construction: the frozen clock never moves the focus, and the running one never changes the scene's phase.
  - They measure against the list's own frame (`page.today`'s one `ScrollView`). A row's element is its texts' frame, not its box, so at the top it starts about 1.5 pt under the list's edge (the ring's element is the gauge's square, not its box, so it is no reference).
- **Evidence:** `scripts/watch-build.sh test` on the SE 3 under `sim-se3`: TEST SUCCEEDED, 7 tests, 0 failures (`LaunchTests` 3, `TodayReadingOrderTests` 4); `swift test` 154/0, no warning; `build-for-testing` at `generic/platform=watchOS Simulator` TEST BUILD SUCCEEDED, no watch-source warning; `scripts/ios-build.sh` (generic iOS Simulator, arm64) BUILD SUCCEEDED and `check-watch-bundle` every rule (TaqwaWatchWidgets 1892 of 5120 KB, TaqwaWatch.app 4544 of 15360 KB); `tools/i18n-check.py` clean; `tools/add-watch-targets.rb` re-run byte-identical (no file added). `scripts/test.sh` was not re-run: no file it compiles changed. `sim-se3` was held 21:50 to 21:59 and the SE 3 put back as found.
- **Minors left,** for the Lead (none blocks the task):
  - the notice's fit is a CoreText estimate: Bengali 0.66, under the 0.7 floor, French 0.71, Turkish 0.72, Indonesian 0.80 (Task 11's languages pass shoots them);
  - the rows' growth is seen only at accessibility sizes (`@ScaledMetric` is 1.0 from XS to XXXL by launch argument), and not on 44 mm or larger;
  - `MessageView`'s feed word is a fixed 15 pt;
  - the Arabic and Urdu own update lines are spelled unlike the feed's (`c3b38143`);
  - the always-on screen's last minute reads "0:00";
  - Tasbeeh and Qibla with a feed and a newer format seen show no notice (Tasks 8 and 9);
  - the ring's box sizes are inline numbers;
  - `k` is taken from the 158 pt page, not the 162 pt screen, so the drawing is 2.5 % small;
  - 3.6 px from the status bar's clock to the arc;
  - the no-spoken-words fallback voices the capitals and the drawn clock, as the plan's rule has it;
  - the page dots sit over the fourth row (the Lead's `.page(indexDisplayMode: .never)` call, above);
  - the pages have no name for VoiceOver.

## scripts/test.sh

The whole suite ran at the task's tree and exited 0 on its first run (versions agree, 1.3.0 (39) on the phone, the face and iOS and 1.3.0 (100039) on the Galaxy watch app; `test-bump-version.sh` PASS; `swift test` 154 tests, 0 failures). Its Gradle suites were up to date, since this task changes no Kotlin, so each was run again with `--rerun` (`scripts/test.sh` with the seven test tasks named, BUILD SUCCESSFUL in 5 min 18 s, exit 0):
- the Kotlin/Native simulator suites: `:shared` iosSimulatorArm64Test 2215, `:widgetcore` 109, `:watchcore` 197;
- the host suites: `:shared` 2271, `:widgetcore` 111, `:watchcore` 224, and `:wearApp` 398.

Every suite had 0 failures and 0 skipped. There was no EOFException and no flake, so nothing needed a re-run.
