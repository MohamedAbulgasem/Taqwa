# Plan 4 Task 6: the complications and the Smart Stack

Lane A worked in `watch-apple`, plan 4's own worktree, on 30 October 2026. The base was `71dc53d7` ("Merge watch-apple: R60"), `watch-companion`'s head: `git merge --ff-only watch-companion` found it already up to date. The catch-up gate's line was in, and `xcodebuild -checkFirstLaunchStatus` exited 0.

The work was test-first. The package tests were written and seen failing before any code existed: `swift test` did not build, because `Glances`, `Glance`, `GlanceTimeline`, `GlanceEntry`, `GlanceScript`, `Speech`, `DebugArguments.startIn`, `DebugArguments.galleryPart` and `DebugSample.startingIn` were missing. Later, the three-part gallery's test failed on its own (`("1") is not equal to ("3")`) before its one-line change.

**In one line:** Taqwa's next prayer is now a WidgetKit widget in the four accessory families and the Smart Stack. It draws the feed's own words, digits, direction and clock, with an entry for each minute of the coming hour and one at each of the next ten starts. Every family is one VoiceOver element, in the feed's language. The gallery shots are taken on the SE 3. The placement on Apple's faces and the idle-timeline watch (Steps 6 and 7) did not run, because the simulator control tool was never given access to the Series 12.

## What was made

- **`iosApp/WatchCore/Sources/WatchCore/Glance.swift`** (the Swift port; `swift test` holds it):
  - `Glance`: the plan's fields, plus `language` (erratum p4 #5).
  - `GlanceEntry`.
  - `GlanceTimeline`: the plan's fields, plus `script: GlanceScript?`.
  - `GlanceScript`.
  - `Glances`: `make`, `relevantBefore` 1,800, `horizon` 3,600, `lateStarts` 10, `timeline`, `relevanceWindows`.
  - `Speech.line(_:language:)`.
- **`iosApp/WatchCore/Sources/WatchCore/DebugSample.swift`:**
  - `DebugArguments.startIn` (`-taqwaStartIn`, which counts in `holdsState`);
  - `DebugArguments.galleryPart` (`-taqwaGalleryPart 1|2|3`);
  - `-taqwaAt` with `HH:MM:SS`;
  - `DebugSample.startingIn(_:minutes:now:phrase:)` and `startInMinutes` (1 to 180);
  - `redated` now goes through a private `shifted(_:by:phrase:)`, which `startingIn` shares.
- **`iosApp/WatchShared/Style.swift`:**
  - `TaqwaColor`: the eight colours;
  - `TaqwaFont`: `of(_:_:latin:)` (Manrope Light, SemiBold or ExtraBold under `latinScript`, otherwise the system font), `tracking(_:latin:)` and `labelSize(_:latin:)`;
  - `direction(_ feed:)`, `direction(rtl:)`, and `View.feedDirection(rtl:)`.
- **`iosApp/WatchShared/NextPrayerViews.swift`** (both watch targets):
  - `NextPrayerContent`: one entry's glance and its empty-form words (`appName`, `notice`, `cornerLine`, `inlineLine`, `spoken(_:)`, `withoutCountdown`);
  - `NextPrayerView(family:content:card:)`;
  - the families: circular, corner, rectangular, inline and the Smart Stack card;
  - `RectangularGlance`, `PeriodBar`, `RingGauge`, `ArchMark` and the empty form.
  - Since Ruling 64 item 3, `NextPrayerContent` is in WatchCore (`NextPrayerContent.swift`, with `spoken(inline:)` and the watch's own words as `OwnLines`), and `RingGauge` in `WatchShared/RingGauge.swift`, each with its tests (`2026-10-30-watchos-ruling-64.md`).
- **`iosApp/TaqwaWatchWidgets/NextPrayerWidget.swift`:**
  - `TaqwaNextPrayer`, kind `TaqwaNextPrayer` as Task 4's placeholder kept it: a `StaticConfiguration` over the four families, named and described from the extension's own table;
  - `NextPrayerEntry`, whose relevance is `TimelineEntryRelevance(score: relevant ? 1 : 0, duration: nextAt − at)`;
  - `NextPrayerProvider`: `.after(reloadAt)` or `.never`; each `getTimeline` logs; `relevance()` on watchOS 11 and later;
  - `NextPrayerEntryView`, on `containerBackground` `#1A1D1C`.
- **`TaqwaWatchWidgets.swift`:** the bundle is `TaqwaNextPrayer()`, and the placeholder is gone.
- **`iosApp/TaqwaWatch/ComplicationGallery.swift`** (`#if DEBUG`): the gallery, opened by `RootView` under `-taqwaPage gallery`.
- **`DebugLaunch.swift`:** applies `-taqwaStartIn` (in `-taqwaSampleFeed`'s place), and reloads all timelines after any launch that sets the store.
- **Tests:**
  - `GlanceTests`: 21 tests. The plan's fifteen, under its names, and six more: `testNoMomentIsNoGlance`, `testALateReloadStillNamesTheNextPrayer`, `testLateEntriesThatReachTheFeedsEndEndInTheEmptyEntry`, `testTheTimelineCarriesTheFeedsScriptAndWordsOnce`, `testASpokenLineCarriesTheFeedsLanguage` and `testTheWatchsOwnLineCarriesNone`.
  - `DebugSampleTests`: four more (`testTheStartAndTheGalleryPartAreRead`, `testStartInPutsTheNextStartThatManyMinutesAway`, `testStartInRefusesMinutesOutsideItsRange`, `testStartInKeepsThePhrase`). Two existing tests gained cases: `holdsState`, and `clockStart` with seconds.
- **`tools/add-watch-targets.rb`:** needed no edit. Its re-run added `Glance.swift`, `Style.swift` and `NextPrayerViews.swift` to both targets, `NextPrayerWidget.swift` to the widgets and `ComplicationGallery.swift` to the app. A second run left `project.pbxproj` byte-identical.
- **The evidence:** `docs/superpowers/reports/2026-10-24-apple-watch-pass/` (21 shots and the README).
- **Plan 4 Task 6** gains an as-built note.

## The rules as built

- **The glance** at `now`:
  - `WatchRules.moment`'s next start;
  - its own day's `label`, `names` and bare `clock`;
  - `countdown`;
  - `spoken`: `spokenCountdown` with `spokenClock`, or `"<name> <spokenClock>, <countdown>"` without spoken words;
  - `spokenWithoutCountdown`: `"<name>, <spokenClock>"`, the phone's `prayerRowDescription`;
  - `progress`, `rtl`, `latinScript` and `language`.
- **The timeline (the Lead's call 5, with erratum p4 #10):**
  1. At `now`, the glance that holds for now's minute: `make(now)`, or `make(now + 1)` when `now` is a whole minute before its start (fix round 1, below).
  2. Then, period by period, inside `(now, now + 3600)`: each whole minute `n − 60k` after the start before it, and each start itself. Each entry is `make(at + 1)`.
  3. The hour's end, `make(end)`, without the countdown.
  4. The next ten starts without it. Since Ruling 64 item 2, the ten step to the earlier of the next start and a turn of the date that changes the answer, and so do the relevance windows (`2026-10-30-watchos-ruling-64.md`).

  `relevant` holds for `at` in `[nextAt − 1800, nextAt)`. `reloadAt` is `min(first.nextAt, now + 3600)`. The first empty entry ends the timeline. No feed, or no moment at `now`, gives one empty entry and no reload. From 14:57:30 the hour is 62 entries and the whole timeline 72: the hour's most, while every start is on a whole minute (Ruling 64 item 3).
- **The families** (`faces.js`):
  - **circular:** the system's `.accessoryCircularCapacity` gauge, tinted amber, with the label (ExtraBold, 7.6, tracked 0.5) over the time (SemiBold, 10);
  - **corner:** `RingGauge` with the arch inside, and `"<label> <time>"` as its `.widgetLabel`;
  - **rectangular:** the label (ExtraBold 11.5, tracked 1.1, amber, accentable) and the time (SemiBold 15, ink), the countdown (SemiBold 12.5, mist), then the 4-point `PeriodBar`, which fills in reading order;
  - **inline:** `"<label> <countdown>"`;
  - **the Smart Stack:** the rectangular below the arch and `words.appName` in mist, when `showsWidgetContainerBackground` holds.
  - **Without the countdown:** no countdown line, both gauges as their track alone, the inline `"<label> <time>"`, and the row's sentence.
- **The empty form:**
  - its shape: the arch alone (circular, corner), the app's name above the notice (rectangular), or the app's name (inline);
  - its words: a spent feed's, in the feed's language, direction and type; with no feed, the extension's own `CFBundleDisplayName` and `open_taqwa_on_phone`, or `update_taqwa_on_watch` (plan 3's call 5, "with a feed or without");
  - with a feed still running and a newer format seen, the complications draw from the feed, as every page does.
- **VoiceOver** (Ruling 38, Ruling 42):
  - every family is one element (`children: .ignore`, decorative shapes hidden), labelled with one sentence: the prayer, the time left and its time; without the countdown, the row's form; in the empty form, the app's name and the notice (the name alone inline);
  - each label is `Speech.line` in the feed's language (erratum p4 #5);
  - nothing drawn changes for VoiceOver.
- **Words:** every word drawn or spoken is the feed's, except the extension's own table in the empty form with no feed (spec 1.3). No `DateFormatter`, `NumberFormatter`, `Date.FormatStyle` or `Text(_:style:)` is used: every number is the feed's string or `WatchRules.digits`. No clock is read but the instant (`Date().timeIntervalSince1970`).

## The errata and rulings

- **p4 #5** (VoiceOver's language):
  - Honoured as far as the watchOS SDK allows. The erratum names `accessibilitySpeechLanguage`, which watchOS has only as UIKit's `NSAttributedString` key. The SDK's AttributedString accessibility scope (`AttributeScopes.AccessibilityAttributes`, watchOS 27.2) holds no speech language.
  - So the label carries Foundation's `languageIdentifier` set to `ui.language`. SwiftUI keeps an internal `coreAXSpeechLanguage` key for its accessibility text (`SwiftUICore.tbd`).
  - The watch's own words carry no language, since they are already in the watch's.
  - Held by `testASpokenLineCarriesTheFeedsLanguage` and `testTheWatchsOwnLineCarriesNone`. That the voice follows it is unproven until plan 5's check on her watch, which the erratum also asks for.
- **p4 #8:** `-taqwaStartIn` holds state. On the paired Series 12 the launch logged `intake off (debug sample)` and every handshake `off` (the pass README).
- **p4 #10:** built as the erratum asks, with two tests (above).
- **p4 #12:** `feed.words` is read once per timeline, and once per gallery render, through `Glances.timeline`.
- **p4 #14** (Ruling 61): no strict per-position check is added and no model class is split, so `newerPhone` needs no new key.
- The other p4 errata touch Tasks 3, 7, 8, 9, 10 and 11.
- **Ruling 39:**
  - The widget extension reads the stored feed and writes nothing.
  - It has no network code and no location request (`check-watch-bundle.sh` rule 5 holds on both bundles).
  - No coordinate exists on the watch.
  - The promise line "Only your own devices can see your location." is plan 5's to apply. Nothing here changes it.
- **Rulings 41 and 43** are the face's and Task 11's, and are untouched here. **Ruling 42:** each complication is one element in the plan's sentence order. **Ruling 56:** the decoder is used as it is. **Rulings 57 to 60:** untouched.
- **Ruling 59:** no simulator step was deferred for Xcode, whose first launch was done. Steps 6 and 7 did not run in this task's rounds for another reason (below). **They ran afterwards, on 30 October from 23:20 to 00:07 (P4.6 faces, after Ruling 64 item 7's grant): see the pass README's Steps 6 and 7 (erratum p4 #16).**

## Where it differs from the plan

Each point is written into plan 4 Task 6 as an as-built note:
1. `Glance.language` and `GlanceTimeline.script` were added, for erratum p4 #5 and for a spent feed's empty form.
2. The late entries of erratum p4 #10 were added. An empty entry has `countdownShown` false.
3. The corner's gauge is `RingGauge` (`appRing`), not the system gauge, which keeps the circular family's size. The circular keeps the system gauge.
4. The gallery has three parts (`-taqwaGalleryPart`), and `-taqwaAt` takes seconds.
5. The log line appends the family and its box.
6. The rectangular's time is SemiBold, since no Bold face is bundled. Outside Latin letters, the label is 13/11.5 larger.
7. `relevance()` uses `.date(from:to:)`, which watchOS 26 deprecates; it builds with no warning at deployment target 10.0.
8. The plan's 12-hour strings carry the phone's no-break space ("4:10 PM" is `4:10\u{00A0}PM` in the en12 sample), and the tests use it.
9. The Global Constraints have an `ios-pair` task install the phone's app as well. Here the iPhone 17 Pro's Taqwa 1.2.0 (38) was left untouched: Steps 6 and 7 set the watch's state by debug arguments with intake off, so the phone app has no role, and nothing on the phone needed putting back. **Superseded (erratum p4 #16):** when Steps 6 and 7 ran (P4.6 faces), booting the phone made the watch's `appconduitd` uninstall a watch app whose phone holds no Taqwa with that watch counterpart, and a dependent watch app's complications are listed only while it does. So an `ios-pair` task installs the debug iPhone Taqwa (with `Watch/TaqwaWatch.app`) over the store's 1.2.0 (38), and puts the store's back afterwards, as the Global Constraints say.

## The steps and their evidence

1. **Step 1, the failing tests:** as above.
2. **Step 2:** `swift test` FAILED to build: the types were missing (`type 'DebugSample' has no member 'startingIn'` and the rest). Not committed.
3. **Step 3:** built as above, with WIP commits `a6c1776e`, `6dd3b9e4` and `5625f9b2`.
4. **Step 4:**
   - `swift test`: 122 tests, 0 failures (97 at the base).
   - `scripts/watch-build.sh` (build): BUILD SUCCEEDED, 0 warnings in the whole log.
   - `scripts/ios-build.sh` at its default iPhone 17 Pro: BUILD SUCCEEDED, with no TaqwaWatch, WatchCore or WatchShared warning. The only warning is TaqwaWidget's old "Compile Kotlin" one. TaqwaWatchWidgets is 3 MB (limit 5). `check-watch-bundle` holds every rule (4b skipped ad hoc, 8 skipped Debug; TaqwaWatchWidgets 3284 of 5120 KB, TaqwaWatch.app 7208 of 15360 KB; 2452 and 5164 KB at the base).
   - `scripts/watch-build.sh test` on the SE 3 under `sim-se3`: TEST SUCCEEDED, 3 tests, 0 failures (`LaunchTests`).
   - `tools/i18n-check.py`: clean.
   - `scripts/test.sh` whole: see the last section.
   - Ten mutants, each run in a scratch copy of the package: every one was killed. They were:
     - `make(minute)` for `make(minute + 1)`;
     - no late entries;
     - the hour's end with its countdown;
     - `>` for `>=` in `relevant`;
     - the bare clock in `spokenWithoutCountdown`;
     - `reloadAt` always the hour's end;
     - no `languageIdentifier`;
     - a spent feed's start dropped instead of ending in the empty entry;
     - `startingIn` floored instead of ceiled;
     - `HH:MM:SS` without its second colon.
5. **Step 5:** 21 shots on the SE 3, indexed in the pass README. Each family matches `faces.js` in its words, digits and direction, except the right-to-left corner ring, which started at 6 o'clock until fix round 1 (below). Two defects were fixed after the first shots: the corner's system gauge spilled out of its box, and the arch was drawn at a third of the drawing's size. The empty corner also lost a line that no face draws.
6. **Step 6: NOT RUN in this task's rounds; RAN on 30 October at 23:20 (P4.6 faces, the pass README's Step 6).** The `ios-pair` lock was taken and the pair made. The watch app was installed and launched on the Series 12 with the Cape Town sample, and intake stayed off. But the iOS Simulator control tool's access request for the Series 12 went unanswered twice, so no face could be edited. The plan's fallback (plan 5 on her watch, or a re-run once Mohamed granted the tool access) was taken the second way: he granted it, and the placement ran that night (the pass README's Step 6).
7. **Step 7: NOT RUN in this task's rounds,** since it watches Step 6's face; **RAN on 30 October from 23:42 (the pass README's Step 7): each minute moved and the start rolled over.** On the simulator, with the screen lit, the idle extension's face showed each minute and rolled over at the start by itself. The Lead's call 5 stays a gate for plan 5 on her watch, where the Always On cadence and an inactive face's next draw are seen.
8. **Step 8:** committed with the plan's message (below).

The devices: `sim-se3` was held 19:11 to 19:21, and `ios-pair` 19:21 to 19:26. Each was put back as found; the ledger has the details. KB2604110095 and the work iPhone 12 were untouched.

## scripts/test.sh

The whole suite ran at the task's tree and exited 0 on its first run (BUILD SUCCESSFUL in 4 min 10 s):
- versions agree: 1.3.0 (39) on the phone, the face and iOS, and 1.3.0 (100039) on the Galaxy watch app;
- `test-bump-version.sh`: PASS;
- `swift test`: 122 tests, 0 failures;
- the Kotlin/Native simulator suites: `:shared` iosSimulatorArm64Test 2215, `:watchcore` 197, `:widgetcore` 109;
- the host suites: `:shared` 2271, `:watchcore` 224, `:widgetcore` 111, `:wearApp` 342.

Every suite had 0 failures and 0 skipped. There was no EOFException and no flake, so nothing needed a re-run.

## Fix round 1 (30 October, after review round 1)

Both review lenses found NEEDS FIXES: 0 Critical, and 2 and 3 Important. One Important was the same in both. Every Important is dealt with below, and no ruling is contradicted.

1. **The right-to-left corner ring started at 6 o'clock** (both lenses). SwiftUI mirrors a rotation under a right-to-left layout, so `rotationEffect(-90)` and the explicit `scaleEffect(x: -1)` together drew the arc from the bottom.
   - **Fix:** `RingGauge` draws its track and arc under `.environment(\.layoutDirection, .leftToRight)`, and turns the arc anticlockwise by its flip alone. Its content keeps the feed's direction.
   - **Off-device:** the probe drew the old and the new code with `ImageRenderer` at 27/217, 145/217 and 1/2. The old code was wrong in all three right-to-left cases, and the new one right in all six.
   - **On the SE 3:** ar-sa and ar-ly12 were re-shot at 13:00 and 14:58, with Cape Town as the control. Measured along the stroke, both rings read 310–0° and 120–0° right to left, the mirror of Cape Town's 0–50° and 0–240°. The corner agrees with the system's circular gauge in every shot. The shots before the fix read 300–180°. The pass README has the table.
   - Plan 4's as-built note now says this. Task 7's Today ring and Task 8's tasbeeh ring take the same rule.
2. **A timeline made on a whole minute drew one second's countdown for the whole minute, and then skipped a minute** (spec lens).
   - From 14:58:00 it drew "in 1 h 12 min" and then "in 1 h 10 min". From Asr's start, 16:10:00, it drew "in 2 h 54 min" and then "2 h 52". A reload at a start always comes on a whole minute.
   - **Fix:** the first entry is `make(now + 1)` when `(nextAt − now) % 60 == 0`, and `make(now)` otherwise. That is the glance that holds for now's minute, as every other entry already was.
   - **For the Lead's agreement:** this changes the plan's words "The first entry is `make(now)`" to keep Review Focus 3, so plan 4's as-built note marks it.
   - **Tests, red first:**
     - `testAWholeMinuteNowDrawsTheMinuteThatHolds` (14:58:00);
     - `testATimelineMadeAtAStartDrawsTheMinuteThatHolds` (16:10:00);
     - `testEveryEntryShowsWhatHoldsUntilTheNext`. It makes 490 timelines on the four samples and Tromsø's May feed, from whole-minute, half-minute and one-second-off instants around up to 10 starts, and checks 56,682 spans;
     - `testANewerFormatSeenSaysUpdateInTheEmptyForm` now expects `make(now + 1)` at 14:58:00.
   - Before the fix, those four tests failed 386 times, and each failure was a whole-minute first entry. After it, `swift test` passes 125 tests with 0 failures.
   - The gallery draws the first entry, so its 14:58 shots now read "in 1 h 11 min". Parts 1 and 2 of all four sets were re-shot, and the parts without the countdown came out the same.
3. **The Smart Stack's relevance was never marked stale** (quality lens).
   - **Fix:** `NextPrayerWidgets.reload()` in `WatchShared` calls `reloadAllTimelines()`, then, on watchOS 11 and later, `invalidateRelevance(ofKind:)`. It is called at both places that stored a feed and reloaded: `WatchSession`'s intake and the DEBUG launch that sets the store.
   - The kind `TaqwaNextPrayer` moved into `WatchShared` as `NextPrayerWidgets.kind`, so both targets name one string. The SDK has `invalidateRelevance(ofKind:)` from watchOS 11 (WidgetKit's swiftinterface, watchOS 27.2), so CI's watchOS 26 SDK has it.
4. **Steps 6 and 7 still did not run** in this round (spec lens; they ran later, P4.6 faces, the pass README). Fix round 1 took `ios-pair` at 20:10, booted the Series 12 alone and asked the control tool for a screenshot. The answer was again "The user did not respond to the access request". The Series 12 was put back as found. Either Mohamed grants the tool access to the Series 12 and they are re-run, or the Lead rules that the plan's fallback hands them to plan 5. The whole-minute fix is in, so a re-run will not show the skipped minute.

The minors that were left are in the ledger.

**Checks at the fix round's tree:**
- `swift test`: 125 tests, 0 failures, no warning.
- `scripts/watch-build.sh` build, generic and SE 3: 0 warnings.
- `scripts/watch-build.sh test` on the SE 3: 3 tests, 0 failures.
- The rest are in the ledger's closing line for this round.
