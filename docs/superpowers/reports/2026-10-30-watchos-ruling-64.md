# Ruling 64: the timeline's turns of the date, durable tests for the ring and the glance words, and Today keeping the wearer's scroll and VoiceOver focus

Lane A, in `watch-apple` (plan 4's own worktree), 30 October 2026. Base `31650dac` ("Merge watch-apple: P4.7"), which is `watch-companion`'s head; `git merge --ff-only watch-companion` found it already up to date. This is not a plan task. It is the ledger's Ruling 64, items 1 to 5, 7 and 8: P4.6's and P4.7's open items. Item 6 (the Arabic and Urdu row joins) is plan 5's native review and is not touched. Test-first: every new test was seen failing on the code before its change, or, where the behaviour was already right and the ruling asked for a durable test of it, on a named mutant of that code.

**In short:**
- The late entries and the Smart Stack's windows now step at a turn of the date that changes the answer, and end at the feed's last midnight (item 2). In Tromsø's late-Maghrib feed the face no longer names a dropped Maghrib for 45 minutes after midnight.
- The ring's direction, what each complication draws and says, and the 72 bound are held by tests that run in `swift test` (item 3). The 62 now states its whole-minute condition.
- Today keeps the wearer's scroll across a lowered and raised wrist (item 4), and a row keeps one identity, so VoiceOver keeps its focus, while its state word comes and goes (item 5).
- Item 1 is recorded as agreed. Items 7 and 8: the Series 12 was found booted by someone else, so it was reported and left as found, and P4.6 Steps 6–7 stay with P4.11 and P5.15.

## The commits

| Commit | Items | What |
|---|---|---|
| `cb29b21e` | 2 | Apple Watch: the timeline and the Smart Stack turn over with the date, and end at the feed's last midnight (Ruling 64) |
| `37148d58` | 3 | Apple Watch: the ring's direction and what each complication draws and says are held by tests, and the timeline by its 72 (Ruling 64) |
| the commit after `37148d58` | 4, 5, 1, 7, 8 | Apple Watch: Today keeps the wearer's scroll across a lowered wrist, and a row keeps VoiceOver's focus as its state changes (Ruling 64) |

Taqwa identity, no trailer, nothing pushed. Three WIP commits (`7303300e`, `50972ea7`, `f89ade0a`) were folded into these by a soft reset to the base, each commit taking its WIP's tree whole (`git read-tree`). There is one exception. `50972ea7` predated the targets script's re-run, so `37148d58` also carries the 12 pbxproj lines for its two new files, made by that script on its own tree. That tree was then checked by itself, in a `git archive` copy: `swift test` 168/0, and `build-for-testing` at `generic/platform=watchOS Simulator` TEST BUILD SUCCEEDED. The last commit is `f89ade0a`'s tree with the documents added.

## Item 1: the first entry on a whole minute (agreed)

The Lead agreed P4.6 review round 1's change: the first entry is `make(now + 1)` when `now` is a whole minute before its start, and `make(now)` otherwise. Plan 4's Task 6 as-built note now says AGREED where it asked for the Lead's agreement. No code changed.

## Item 2: Ruling 28's turn of the date, everywhere in the timeline

**The fault.** Within the hour the entries were already right: each minute is `make(minute + 1)`, and every turn of the date falls on a whole minute, so a minute's entry stands at it. Two parts stepped from start to start alone, though:
- **The late entries:** in `tromso-late-maghrib`, 18 May's Maghrib (00:45 on the 19th) comes after the 19th's Fajr (00:38). The rules count to it until midnight and drop it there (Ruling 28). The late entries still went 17:09 "MAGHRIB 0:45" until 00:45. So for 38 minutes the face named Maghrib where Fajr was next, and then for seven more it named Maghrib where Dhuhr was.
- **The windows:** `relevanceWindows` chained from start to start in the same way. It gave the dropped Maghrib a window, 00:15 to 00:45, and Fajr none.
- **The feed's end:** in both Tromsø feeds, the 19th's Maghrib falls after the feed's last midnight (20 May 00:00). Both parts went on naming it after that midnight, where nothing holds: the empty entry stood at 00:49 or 00:29, not at 00:00.

**The rule now** (`Glance.swift`):
- **`Glances.nextChange(feed, after:, glance)`:** the earlier of the glance's start and the first turn of the date after which the answer is another.
  - The turn is found by halving on `WatchRules.today` (`nextDayChange`), as the Galaxy's `ComplicationTimelines.nextDayChange` finds it. No second copy of the turn rule is kept: midnights, the solar-midnight fallback and the feed's end all come from `WatchRules`.
  - "Another answer" is `samePeriod` failing: any of the prayer, its start, the period's start, the label, the name, the time or the row's spoken form, as the Galaxy's `answerChangesAt` compares them. A turn that changes nothing (every normal midnight) gets no entry. So the samples' timelines are as before, and every earlier Glance test still passes unchanged.
- **The late entries** step by `nextChange`. Each is `make(at + 1)`, and `make` gives nil past the feed's end, so the timeline ends in the empty entry at the feed's last midnight. A turn's entry takes one of the 10 (`lateStarts`), so a timeline still holds at most 72 entries (item 3).
- **The windows** step by `nextChange` too. Each window is `relevantBefore` up to a start, clipped to the stretch in which that start is the one counted to: from `now` or the step's start (a start or a turn) to the next change. So:
  - a start that a turn drops before it comes has no window past the turn;
  - one the turn brings in has its window from the turn;
  - no window overlaps the next;
  - and an instant is in a window exactly when an entry drawn for it is `relevant`.

  The clipping at a start is beyond the ruling's words. Before, where two starts were under half an hour apart (Tromsø-may's Maghrib 00:21 and Fajr 00:34), the second window began 30 minutes before its start, inside the first. The union was the same, but "one window per instant" could not be tested. Now the second begins at the first start. Nothing the Smart Stack is told changes.

**The tests** (`GlanceTests`, Tromsø's two May feeds, in Norway's summer time):
- `testPastTheHourAMaghribDroppedAtMidnightIsDroppedThere`: from 18 May 12:00, the late entries are 17:09 (Maghrib), 00:00 (Fajr, at 00:38), 00:38 (Dhuhr), 12:46 (Asr), 17:08 (Maghrib), and the empty entry at 20 May 00:00. Each is `make(at + 1)`, and `reloadAt` is unchanged.
- `testPastTheHourTheEmptyFormComesAtTheFeedsLastMidnight`: tromso-may from 18 May 12:00, where 18 May's Maghrib (00:21) is no turn of the answer. It ends in the empty entry at 00:00, not at 00:29.
- `testTheRelevanceWindowsTurnOverWithTheDate`: both feeds' windows, written out:
  - late-Maghrib: Fajr's window is 00:08 to 00:38, and the dropped Maghrib has none;
  - tromso-may: Fajr's window runs from Maghrib's start, 00:21, and the 19th's Maghrib has 23:59 to 00:00, until the feed ends.
- `testEveryEntryAndWindowHoldsAcrossTheDatesTurn`, the review round 2 scratch probe, committed. It takes 208 instants around every start and turn of the date of both feeds. From each one:
  - every entry draws, through its span, the prayer, start, label and time that hold there, and within the hour the countdown too (38,709 spans);
  - an empty entry stands exactly where nothing holds;
  - entries strictly increase, there are at most 72, and there are never more than 10 late ones (all 10 when the timeline stops short of the feed's end);
  - the windows never overlap, and they match the entries' own rule instant by instant: every five minutes, and either side of each window's ends, each start and each turn.

**Red, then green.** The three named tests failed at the base on exactly the cases above. The probe test found 2,904 mismatches at the base: 249 in the entries and the late count, and 2,655 in the windows, overlaps included. It finds none after. `swift test` went 154 → 158, 0 failures.

## Item 3: durable tests for what only shots held

### (a) The ring's direction: `RingGaugeTests`

The review's ImageRenderer probe is now a committed test. It draws `RingGauge` on the Mac under the feed's direction, as every complication and page sets it. It reads 72 points round the stroke's middle line, clockwise from 12 o'clock, and skips the 15° at each end of the fill, where the round caps and the edge's smoothing fall. It checks:
- both thicknesses: the corner's 0.12 and Today's 5.4 of 109.4;
- three fills, 27/217, 145/217 and 1/2, in both directions;
- and that an empty ring is its track alone.

**Where it runs.** `RingGauge` moved, unchanged, from `NextPrayerViews.swift` into its own file, `WatchShared/RingGauge.swift`. `NextPrayerViews.swift` cannot be built for macOS, because the accessory families are unavailable there. A package cannot reach outside its own folder, so the WatchCore package gains two targets that serve the tests alone:
- `WatchViews`: symbolic links in `iosApp/WatchCore/Tests/WatchViews/` to `../../Sources/WatchCore` (the folder), `WatchShared/Style.swift`, `WatchShared/RingGauge.swift` and `TaqwaWatch/StateValue.swift`, compiled as one module, exactly as each Xcode target compiles them;
- `WatchViewsTests`.

The Xcode targets compile the same files from where they are. `tools/add-watch-targets.rb` finds files on disk, so its re-run added `RingGauge.swift` to both watch targets, `NextPrayerContent.swift` to both and `StateValue.swift` to the app (16 pbxproj lines). A second run left it byte-identical.

**Red.** The ring's code was already right (P4.6 fix round 1), so its red is the ruling's own mutant: removing the fixed left-to-right drawing (`.environment(\.layoutDirection, .leftToRight)`) fails the right-to-left test, with the arc at 130–155° clockwise from 12 (232 failing assertions). Dropping the flip fails it too (222), and so does dropping the quarter turn (300). Each was put back, and the test is green.

### (b) What each complication draws and says: `NextPrayerContentTests`

`NextPrayerContent` moved into WatchCore (`NextPrayerContent.swift`) with a family-free flag:
- `spoken(_ family:)` became `spoken(inline:)`, the one thing the family decided;
- the watch's own three words, which only the extension's bundle holds, come in as `OwnLines`;
- `OwnWords.lines` reads them once per timeline in the provider and in the gallery, as the feed's words are read once (erratum p4 #12).

Ten tests hold:
- the inline and corner lines, with and without the countdown, and in Arabic;
- the one sentence, and its language identifier: the glance's, a spent feed's script, or none for the watch's own words;
- `withoutCountdown`, the hour's end, and an entry with no glance;
- the empty form's notice in plan 3's call 5 order: the update first, with a feed or without; the feed's words where it has them, the watch's own otherwise;
- the inline saying the app's name alone;
- and a spent Arabic feed keeping its words, direction and type.

**Red.** The tests did not compile before the move. The code was already right, so nine mutants of the moved code stand in for its red. Each was killed: the update not first, a glance's line with no language, the inline always counting down, `withoutCountdown` keeping the countdown, the corner drawing the countdown, a countdown kept with no glance, the inline saying the notice, the feed's name ignored, and a spent feed's empty form without its language.

### (c) The documented 72

`testTheTimelineIsAnHourOfMinutes` held `62 + lateStarts + 1` (73). It now holds 72. Its cases had all been from a whole minute (61 in the hour), where a 73-entry timeline cannot occur, so each sample is now also tried from half a minute past (62, the hour's most).

**Red.** A mutant whose late loop runs 11 times (73 entries) passed the old bound and fails the new one (4 failures). The Tromsø probe test above holds 72 too.

### (d) The 62's whole-minute condition

The minutes are counted back from each start. So a start off the whole minute would put its own entry between two of them, and the hour could hold 63. The claim now says so wherever it is made:
- plan 4: the Lead's call 5, Task 6's Interfaces and its as-built note;
- `Glances.timeline`'s and `horizon`'s comments;
- the pass README;
- the complications report.

`testTheTimelineIsAnHourOfMinutes` asserts the condition on the feeds it counts: every start is a multiple of 60 s. This is a statement of a precondition, not a behaviour change, so it has no red of its own.

## Item 4: Today keeps the wearer's scroll across a lowered wrist

**The fault (P4.7 review round 2).** `TodayRows` went to its focus on every `.active`. By Apple's Always On rules, a lowered wrist makes the app inactive and leaves it frontmost and dimmed, and a raised wrist makes it active again; a banner does the same. So a scroll the wearer had made was reset on every raise, and the always-on frame jumped.

**The rule now** is a small pure policy in WatchCore, `TodayScroll` (`Today.swift`):
- `refocuses(entering:)` is true only on the first `.active` after a `.background`;
- `refocuses(focusFrom:to:)` is true whenever the focus moves, in any phase.

`TodayRows` keeps it in `@State` and maps SwiftUI's `ScenePhase` onto it. It still goes to the focus on its first appearance.

**The tests** (`TodayScrollTests`, 4):
- a lowered and raised wrist keeps the scroll, as does a banner;
- background, then inactive, then active refocuses;
- only the first active after the background refocuses;
- the lit row's move still follows: the next start, Sunrise ending Fajr, and the feed's midnight.

**Red, then green.** The tests were written against a stub that kept the rule as built (every `.active` refocuses; every focus "change" follows). Three of the four failed: the raised wrist, the second active, and nothing moved. Then the policy was written, and all four pass.

**On the SE 3:** `testOpenedAgainTheListStandsOnTheLitRow` still holds. Its Home press sends the app to the background, and the policy refocuses on its return. No simulator control lowers a wrist, so the inactive path is held by the unit tests alone, and by plan 5's pass on her watch.

## Item 5: one identity for a row, and the UI test's margin

**The fault.** `StateValue` added the value by an if/else, whose two branches are two views. So a row that became "Next prayer" at a start, or lost its word, was made anew, and VoiceOver loses its focus on a row made anew.

**The fix.** `StateValue` is now inert and in its own file, `TaqwaWatch/StateValue.swift`. The value is always set, and it is empty with no word. An empty value is not spoken, and the UI tests read it as "", as before.

**The test** (`StateValueTests`) draws a row in an NSHostingView on the Mac. It counts its content's appearances while the word goes nil, "Next prayer", nil, "Current prayer", "Passed".

**Red, then green.** The if/else form, moved as it was, made the row anew three times (4 appearances, 3 removals): red. The inert form keeps it (1, 0): green. Erratum p4 #15 makes this modifier the rule for every page whose state word comes and goes.

**`testTheListMovesWithTheLitRow`** now starts at 16:09:00, with `holds(for: 75)`. That is a minute from the clock's start to Asr's, twice the 30 s it had, and erratum p4 #15's floor. On the SE 3 it took 61.3 s.

## Items 7 and 8: the Series 12

The `ios-pair` lock was taken at 23:03 to ask the control tool once. It was released at 23:04 unused.

**What was found:**
- the iPhone 17 Pro `2D01CE58` shut down;
- no pair;
- the Series 12 `C803D94B` **Booted**, with no `ios-pair` lock held. That was its state when the Lead found it at 22:05, and at this run's look at 22:44. The Claude app's own process (PID 43701) has been streaming its log since 21:50:56 (`simctl spawn … log stream`, backboardd's orientation lines). That is the simulator panel's attachment.

No lane's ledger line booted it, so it was booted by someone else. By item 8 it was reported (in the ledger) and left exactly as found: not shut down, not paired, nothing installed, and no screenshot.

**The ask was not made.** Steps 6–7 would pair that watch and install on it, and a simulator another session booted is never used, so access alone could not run them. They stay with P4.11 and then P5.15, on her watch, per Step 6's own fallback and question 12's default. If the panel is Mohamed's grant for this, the Lead can rule that it may be used, and the pass README's steps run as written.

## The errata this touches

- **p4 #10** (a late reload): the late entries still name the coming prayer, now across a turn of the date too.
- **p4 #12** (words read once): unchanged. The watch's own words are read once per timeline as well.
- **p4 #5** (VoiceOver's language): every spoken line is still `Speech.line`, and `NextPrayerContentTests` holds its language identifier.
- **p4 #15** (Ruling 64 items 3–5 for Tasks 8, 9 and 11): `RingGauge` now has its committed direction test, `StateValue` is the inert modifier to reuse, `TodayScroll` is the re-focus rule, and the UI test keeps a 60 s margin.

## The checks

| Check | Result |
|---|---|
| `swift test --package-path iosApp/WatchCore` | 172 tests, 0 failures, no warning: 154 at the base, 4 Glance, 10 NextPrayerContent, 4 TodayScroll, 3 RingGauge, 1 StateValue |
| `scripts/watch-build.sh build-for-testing` (`generic/platform=watchOS Simulator`) | TEST BUILD SUCCEEDED, no watch-source warning |
| `scripts/watch-build.sh test` on the SE 3 (`sim-se3`, 22:59–23:03) | TEST SUCCEEDED, 7 tests, 0 failures: `LaunchTests` 3, `TodayReadingOrderTests` 4 |
| Re-shots from that build | 7 (5 gallery, 2 Today), equal to the committed ones below the status bar: 0 px, and 46 px of countdown anti-aliasing on ar-ly12's Today, as review round 2 saw |
| `scripts/ios-build.sh` (iPhone 17 Pro destination, build only) | BUILD SUCCEEDED, no watch-source warning; `check-watch-bundle` every rule holds (TaqwaWatchWidgets 3488 of 5120 KB, TaqwaWatch.app 8376 of 15360 KB) |
| `tools/add-watch-targets.rb` | the three new files added; a second run byte-identical |
| `tools/i18n-check.py` | clean |
| `tools/privacy-check.py --allow-awaiting` | clean but for the 177 rows awaiting M3, as on `watch-companion` |
| `scripts/test.sh`, whole | exit 0: versions 1.3.0 (39)/(100039), bump PASS, privacy tests PASS, `swift test` 172/0, Gradle BUILD SUCCESSFUL in 4m 20s. `:shared:iosSimulatorArm64Test` and `:wearApp:testDebugUnitTest` ran; the host suites were from cache, since no Kotlin input changed; no EOFException |
| `git diff --check` | clean |

**Devices:**
- the SE 3 was found shut down and unpaired, with 15 Apple apps, 39 groups, no Taqwa app or group, en-US and `en_US@rg=zazzzz`, and put back so (the app and its UI-test runner uninstalled, shut down);
- the pair was left as found;
- KB2604110095 and the work iPhone 12 were untouched.

## Known limits, for the Lead

1. **Within the hour, turns of the date are met only on whole minutes.** The minutes step at every turn because every turn falls on a whole minute. A feed with no midnights falls back to solar midnight, halfway between two Dhuhrs, which can fall on half a minute. In a far-north summer, that would leave a turn that changes the answer up to 30 s late inside the hour. The late entries and the windows have no such limit.
2. **The views' tests draw with macOS's SwiftUI, not watchOS's.** The ring's angles agree with P4.6's SE 3 measurements. CI's `swift test` now draws in an ImageRenderer and an NSHostingView inside an NSWindow, which needs the runner's window session; the macOS runners have one.
3. **The package's test module rests on symbolic links**, stored as links in git and kept by `git archive`. A checkout on a file system without links would not build `WatchViews`.
4. **The inactive path of item 4 is unit-tested only.** No simulator control lowers a wrist.
5. **Item 7's ask was not made**, as above.
6. **Rulings 39, 41, 42 and 43 hold.**
   - 39: no location or network code, and no coordinate.
   - 41 and 43: the Galaxy face, not touched.
   - 42: the ring is still read first, then the rows in the day's order, as `testTodayReadsTheRingThenTheDay` holds on the SE 3.
