# Plan 4 Task 9: Qibla with the watch's compass

Lane A worked in `watch-apple`, plan 4's own worktree, on 31 October 2026 (from 06:31). The base was `94f2b58a` ("Merge watch-apple: P4.8"), `watch-companion`'s head: `git merge --ff-only watch-companion` found it already up to date. Xcode 27.2's components were in, so every simulator step ran, on the SE 3 (40mm) under the `sim-se3` lock. The execution map gives P4.9 no `ios-pair` step, so the Series 12 was not used.

The work was test-first. Step 1's Kotlin writer was seen failing on the missing file, then wrote it, then passed without the flag. Step 2's Swift tests were seen failing (`swift test` did not build: `QiblaAngles`, `WatchQibla`, `HeadingSample`, `QiblaTracker` and the rest were missing), and the five UI tests were added to their target and seen failing on the SE 3, finding no `qibla.dial` (Step 3). Each later addition was seen red first too: the debug compass's arguments, the model's `bearing`, and the title's two lines. None of it was committed red.

**In one line:** the Apple Watch's third page is the phone's Qibla dial on the watch's own compass: Core Location's magnetic heading made true by the declination the phone sends, judged by the phone's gate and filter, the needle and the Kaaba's marker only for a heading worth pointing with, the turn told to VoiceOver in the phone's words, one `.success` as the alignment begins, and no location or permission asked.

**Ruling 66 item 8** was built last, as the ruling asks: the rest of the task first, then the detail once `[Lead/int R66]` was in the ledger (08:39). The work so far was saved as a patch, `watch-apple` was reset to its base and fast-forwarded to `watch-companion` (`5761178a`, R66 with MM3), and the patch applied with no file in common. The dial now draws the watch's short forms and VoiceOver hears the phone's long phrases (below). The final commit's base is therefore `5761178a`; the task's first base was `94f2b58a`.

## What was made

- **`watchcore/src/androidHostTest/kotlin/world/taqwa/app/qibla/CompassVectorsTest.kt`** and **`watchcore/vectors/compass-vectors.json`**: every answer the Kotlin rules' own. 20 `angleDelta` pairs across the seam; 32 `isAligned` cases (4.99, 5.0 and 5.01 off, and with Dev 1's B40 memory 6.99, 7.0 and 7.01 off with `wasAligned` true and 4.99 and 5.01 with it false, either way of 23° and of 1°, across the seam); 14 `TrueNorth.fromMagneticOrInvalid` cases (a negative heading, no declination, Cape Town's −27 and the plan's −25) and 5 `correct`; 8 `iosSampleIsLow` cases (the plan's four among them); 10 filter runs (54 steps: 0.15 smoothing, 359 → 1 and back, a half turn, a NaN kept, a reset, no smoothing, a third); 18 gate runs (113 steps: `CompassAccuracyGateTest`'s, every one: Low after 1,000 ms, Good after 1,500, BestEffort after 20,000 and its only exit, the reason's window both ways, a reason left out, and a timestamp that goes back in a low run and in a recovery); 17 `QiblaGuidance.of` cases (round 2's five, 0, −0.0 and the half steps); 10 tracker sequences (47 steps: `QiblaGuidanceTest`'s, every one); and 19 dial runs (1,859 steps: `WatchQiblaTest`'s, every one, and `WatchDialParityTest`'s scripted run on 23.0). Three tests: `compassVectorsAreUpToDate`, `theDialRunsReachEveryStateBothTurnsAndBothReasons` and `theSearchingSpellsRunOnABootClockFarFromNought` (Ruling 58 item 4: the turns are told at 11,500 and 13,000 ms on a clock that starts at 10,000, right and left alike).
- **`iosApp/WatchCore/Sources/WatchCore/`** (the Swift port; `swift test` holds it):
  - `Compass.swift`: `QiblaAngles`, `TrueNorth`, `CompassAccuracyRules` (the iOS rules), `HeadingFilter`, `CompassLowReason`, `CompassAccuracyState`, `CompassAccuracyGate` and `CompassReading`, line for line;
  - `WatchQibla.swift`: `Turn`, `Guidance`, `QiblaGuidance`, `QiblaGuidanceTracker`, `DialState`, `DialStep` and `WatchQibla`, line for line;
  - `QiblaScreen.swift`: `HeadingSample`, `HeadingReading` (the iPhone's `IosCompassSource` rule, and `hasDirection`), `QiblaDialModel`, `QiblaScreen` (`model`, `drawn`, `wholeDegree`), `QiblaStep`, `QiblaTracker`, `QiblaCaption`, `QiblaType` and `QiblaLayout`; since review round 1 also `HeadingSample.onScreen`, `QiblaChange` and `QiblaCompass` (below);
  - `DebugSample.swift`: `DebugArguments.compass`, a `DebugCompass` from `-taqwaHeading`, `-taqwaHeadingAccuracy` and `-taqwaNoCompass`.
- **`iosApp/TaqwaWatch/`:** `HeadingSource.swift` (the `HeadingSource` protocol, `CoreLocationHeading`, the DEBUG `ScriptedHeading`), `QiblaView.swift` (`QiblaSession`, `QiblaPage`, `QiblaView`, `QiblaDial`); `TaqwaWatchApp.swift` (the third page is `QiblaPage`, told whether the pager shows it); `MessageView.swift` (its doc no longer names the Qibla title).
- **Tests:** `CompassVectorsTests` 4, `WatchQiblaTests` 13, `QiblaScreenTests` 21, and one more `DebugSampleTests` case: 39 in `WatchCoreTests` (234 at R66's head to 273; `WatchViewsTests` keeps its 6). Review round 1 adds `QiblaCompassTests` 12, so 285. UI: `QiblaTests` 5.
- **`tools/add-watch-targets.rb`:** needed no edit. Its re-run added the three core files to both targets, the two views to the app and the UI-test file to its target; a second run left `project.pbxproj` byte-identical.
- **The evidence:** 20 shots in `docs/superpowers/reports/2026-10-24-apple-watch-pass/` (16 with the short detail, at the default size and XXXL, and 4 from before Ruling 66 item 8 for the before and after), in its README's Task 9 section.
- **Plan 4 Task 9** gains an as-built note.

## The plan's tests, by name

Every test the plan's Step 2 names exists under its name: `testEveryCompassCaseMatchesTheVectors`, `testEveryGuidanceCaseMatchesTheVectors`, `testEveryDialRunMatchesTheVectors` (each step's kind, heading within 1e-9, turn, degrees, reason and buzz), `testANegativeHeadingNeverDrawsANeedle`, `testNoDeclinationNeverDrawsANeedle`, `testTheSeamDoesNotSwingTheNeedle`, `testFacingWithinFiveDegrees`, `testTheDeclinationTurnsMagneticIntoTrue`, `testSearchingSaysWhichWayToTurn`, `testTheBuzzComesOnceOnEnteringAlignment`, `testALowCompassDimsAndDropsTheMarker`, `testTwentySecondsLowIsBestEffort`, `testNoCompassShowsTheBearingAndDistanceOnly`, `testBeforeTheFirstHeadingNoNeedleIsDrawn`, `testTheDistanceIsTheFeedsOwnFigure` (the sample, the ar-SA sample's "٦٬٦٠٠", every phone golden's own figure, and a figure written without its group kept as written) and `testArabicCaptionsAndAnUnmirroredDial`, Ruling 66 item 8's `testTheDialDrawsTheShortFormAndSaysTheLongOne` (the four samples and the five goldens: drawn, the short form; heard, the long phrase), and the UI tests `testFacingTheQiblaIsSpoken`, `testSearchingSpeaksTheTurn` and `testNoCompassSpeaksTheBearing`, each waiting up to 5 s for the gate.

Beyond them: the constants against Kotlin's; `wearApp`'s `QiblaModelTest` restated without the tilt (searching right and left, no turn yet, facing, calibration and interference with no readout, BestEffort, a heading that is not a number, an infinite one); a negative accuracy; a NaN heading kept until reset through the tracker (Ruling 58 item 1); reset; the bearing's whole degree as Kotlin's `toInt()`; the sentinel's guard (`drawn`); the marker at the bearing on the turned dial; the title's two lines; the layout fitted to the screen and the readout clear of the page dots; the lines' growth with the text size; the debug compass's arguments; and the UI tests `testALowCompassAsksForCalibration` and `testTheReadoutStandsClearOfThePageDotsAndInsideTheScreen` (Latin, Arabic and no compass).

## The rules as built

- **The port** (`Compass.swift`, `WatchQibla.swift`) is the Kotlin line for line, held step for step by the vectors. Kotlin's `%` on a `Double` is `truncatingRemainder`; `roundToInt()` on the non-negative steps is `.toNearestOrAwayFromZero`, done in `Double` so that no finite delta can trap where Kotlin's `Int` would clamp; the gate's window is an array.
- **The reading** (`HeadingReading`) is the iPhone's `IosCompassSource` rule: `TrueNorth.fromMagneticOrInvalid(magneticHeading, place.declination)`, low by `iosSampleIsLow`, reason CALIBRATION; Core Location's sentinel (a negative heading, or no declination) is sent as 0 and low, never 359°.
- **A sample with no direction never points** (`QiblaScreen.drawn`, beyond the plan's text, for Review Focus 5's "no needle points at nothing"). The gate trusts a low sample for its first second, as the phone's does, so the sentinel's 0 would draw a needle at true north for up to a second, and could even light the rim and buzz where the Qibla is within 5° of north. Such a sample is drawn as the searching dial with no heading (Ruling 58 item 1's form: no needle, marker, letters or turn, the bearing's caption, not dimmed) and never buzzes; after a second the gate says Low. The dial itself still sees every sample, as the phone's does, so the filter and the gate keep the phone's behaviour.
- **The page's words** (`QiblaScreen.model`) are plan 3's `qiblaScreen` without the tilt: Searching shows the bearing over the distance and says the turn (`WatchRules.counted`, the phrase its number takes) or, with none, the bearing; Aligned is lit and says "Facing qibla" in the accent; Low dims, drops the needle and the marker and shows no readout, as the phone and the Galaxy do; BestEffort and NoSensor dim and show the bearing and the distance. The detail drawn is the watch's short form (Ruling 66: `distanceOnDial`, "6,600 km", on a dial that turns and before the first reading; `qiblaDetailOnDial`, "23° · 6,600 km", on one that has given up), and what VoiceOver hears is the phone's long phrase (`distance`, `qiblaDetail`), never the short form. A heading that is not finite draws no needle, marker or letters and tells no turn. The bearing is the feed's whole degree (`wholeDegree`, Kotlin's `toInt()`), in the feed's digits; the distance is `place.qiblaKm` as the feed writes it.
- **VoiceOver:** one element, `qibla.dial`, labelled `spoken` and valued `spokenDetail` (the detail as it is heard), each in the feed's language (`Speech.line`), both always set; the dial, the caption and the detail are hidden inside it. While VoiceOver runs, a change of `spoken` is announced. The UI tests read the label and the value; an announcement cannot be read by XCUITest, so plan 5 hears it.
- **The compass runs** only while the pager shows the page and the scene is active (a lowered wrist stops it); each start resets the dial, as the phone resets on every `collectWhileActive`. While it is off, and from each start until its first heading, the page draws the resting dial (review round 1; the first build kept its last drawing). `CoreLocationHeading` reads `magneticHeading` and `headingAccuracy` with `kCLHeadingFilterNone`, lets watchOS show its calibration screen, asks for no authorization and no location, and logs once in DEBUG what Core Location says. The monotonic clock is `ProcessInfo.systemUptime`.
- **The drawing** (`QiblaDial`, a `Canvas` in a fixed left-to-right space): `qiblaDial(86, 90, 62, …, 0.86)` in `aQibla`'s 172 × 212 units, fitted to the screen both ways (`QiblaLayout`). The 24 ticks and the four letters turn by `dialRotation`; the letters stay upright; the needle and the marker point at `bearing + dialRotation`; facing, the rim lights with its halo; dimmed, the dial is at alpha 0.4. The caption is ExtraBold 14 at y 180, the detail SemiBold 10 at y 196, in Manrope under a Latin interface and the system font otherwise.

## The errata and rulings

- **Ruling 58 item 1:** a trusted heading that is not a number keeps the phone's behaviour (Searching at NaN until reset, in the vectors), and the page draws neither needle nor marker and tells no turn; `testAHeadingThatIsNotANumberDrawsNoNeedleAndSaysNoTurn` and the tracker's `testAHeadingThatIsNotANumberDrawsNoNeedleUntilReset` pin it.
- **Ruling 58 item 4 and erratum p4 #11:** the compass vectors carry P3.2's review cases, generated: the interval from the last telling, a non-finite delta (NaN and both infinities), the left margin as the right one, and the searching spells on a boot clock from 10,000 ms; the Kotlin test `theSearchingSpellsRunOnABootClockFarFromNought` holds them in the file.
- **Ruling 66 item 8 and erratum p4 #17:** built after `[Lead/int R66]`, test-first (the model's tests red, then the two UI asserts red on the SE 3 against the model before it). `QiblaDialModel.detail` is `distanceOnDial` or `qiblaDetailOnDial` where the plan says `distance` or `qiblaDetail`; `spokenDetail` and the label never change (the long phrase, the resting dial's "\(bearing), \(distance)" included). The readout stands below the dial (`aQibla` y 180 and 196), so items 3 to 5 need no port: one line, `.fixedSize(horizontal: true, vertical: false)` after `lineLimit(1)`, never a `lineLimit` alone, from 10 grown by `@ScaledMetric` to 1.3 times and never below, inside the screen. `WatchScreenWords`' two fields are R66's own (with its `WatchScreenWordsTests`); `FeedVectorsTests` read the regenerated vectors. Item 9's Apple shots, English and Arabic at the default size and XXXL, are in the pass README, with four from before for the before and after.
- **Rulings 62 and 63, as Ruling 66 replaced them:** the detail is never cut and never sits on the ticks or the rim: on the Apple Watch it stands below the dial. Ruling 62 item 4 and Ruling 63 item 3 (the French wording) are the phone's: the watch draws the feed's words.
- **Erratum p4 #5:** every spoken line is `Speech.line` in the feed's language.
- **Erratum p4 #9:** every Qibla UI test sets its whole state by launch arguments (a sample and the scripted compass). The compass's arguments set no feed, so `holdsState` is unchanged.
- **Erratum p4 #12:** `feed.words` is read once a drawing (`QiblaScreen.model`); `Pages` no longer reads them at all.
- **Erratum p4 #15 and Ruling 64 items 3-5:** the page's label and value are always set, never an if/else modifier; no UI test here runs on the clock.
- **Ruling 64 item 8:** `sim-se3` was found shut down and unpaired, and put back so, twice (the ledger's lines).
- **Ruling 39:** the watch reads the city's bearing to a whole degree, its distance as the phone wrote it and its declination to a whole degree, all from the phone's feed; it asks for no location and no permission, reads no coordinate and sends nothing. No `NSLocation*` key, no `requestWhenInUseAuthorization`, `requestAlwaysAuthorization`, `startUpdatingLocation` or `requestLocation` (`check-watch-bundle.sh` rules 3 and 5). The promise "Only your own devices can see your location." is plan 5's to apply; nothing here changes it.
- **Rulings 41, 42 and 43** are the face's, untouched here.

## Where the build differs from the plan

- **The detail drawn is the watch's short form** (Ruling 66 item 8, erratum p4 #17), where the plan's text says `distance` and `qiblaDetail`; what is heard is the plan's.
- **The declination is −27°, not the −25° the plan wrote**, so its magnetic headings move by two degrees: facing is `-taqwaHeading 50` (the plan's 48), true 340 is 7 (5), true 120 is 147 (145) and true 358 is 25 (23). `testTheSampleIsCapeTownAsTheUITestsTakeIt` pins the sample's bearing, declination and distance, which the UI tests' figures are worked out from.
- **A sample with no direction never points** (`QiblaScreen.drawn`), above.
- **`QiblaDialModel`** carries five more fields than the plan's: `bearing`, `spokenDetail`, `cardinals`, `language` and `latinScript`. The VoiceOver value is `spokenDetail`, which is the plan's `detail` as it is heard, but empty before the first reading, whose label already says the distance.
- **The resting dial and the low dial draw no letters and no hub**, and Low shows no readout, as the Galaxy's `qiblaScreen` and the phone's dimmed dial do. The plan's words list no detail for Low.
- **The layout is fitted to the screen's height** where the screen is shorter for its width than the drawing, which both simulators are; Tasbeeh's page is fitted to the width and lifts its hint clear of the 46mm's dots instead.
- **A bad state's title takes two lines** where one does not fit (the plan's caption is one size-14 line), and the lines grow with the text size (caption to 1.2, detail and letters to 1.3).
- **`-taqwaHeadingAccuracy` alone** scripts magnetic 0. Two UI tests beyond the plan's three: `testALowCompassAsksForCalibration` and `testTheReadoutStandsClearOfThePageDotsAndInsideTheScreen`.

## For the Lead and plan 5

- **A watch worn with the crown on the left** (built in review round 1, below). watchOS turns the display half round for it, and Apple's header says Core Location's `magneticHeading` does not turn with it, so each heading is turned by 180° to the screen's top. The simulator cannot show a crown on the left. Plan 5's check 13 now runs with the crown on each side: a needle 180° out with the crown on the left, and right with it on the right, would mean watchOS turns the heading itself, and the half turn would go.
- **The heading itself** (no prompt, the needle against a real compass, the calibration screen) and **VoiceOver's announcements** (the turn, then "Facing qibla") are plan 5's, on her watch: XCUITest reads the label and the value, not an announcement.
- **Arabic's degree sign**: in a right-to-left paragraph the feed's "٢٣° · ٦٬٦٠٠ كم إلى مكة" draws the degree sign to the left of the digits (the neutral "°" after Arabic-Indic digits takes the paragraph's direction), as the phone's own string would. It is the phone's wording, so the watch draws it as sent; plan 5's native review may want a mark.

## Checks, on the final tree (on `5761178a`, R66's head)

- `swift test --package-path iosApp/WatchCore`: `WatchCoreTests` 273/0, `WatchViewsTests` 6/0, no warning. Two mutants tried while building were each red: the declination zeroed in `QiblaTracker` (`WatchQiblaTests` failed throughout) and the sentinel's guard removed (`testANegativeHeadingNeverDrawsANeedle` and `testASampleWithNoDirectionIsDrawnAsNoHeading`).
- `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors` leaves every vector file byte-identical; without the flag, 231/0 (`CompassVectorsTest` 3/0).
- `scripts/test.sh`, whole: exit 0 (test-bump PASS, test_privacy_check PASS, privacy-check clean but the 186 rows awaiting M3, swift 273/0 + 6/0, shared 2313 host / 2282 simulator, widgetcore 111 / 109, watchcore 231 / 198, wearApp 497; no EOFException, no re-run needed).
- `scripts/ios-build.sh` (iPhone 17 Pro): BUILD SUCCEEDED, no warning in the watch's sources; `check-watch-bundle.sh` every rule holds (TaqwaWatchWidgets.appex 4,324 KB of 5,120, TaqwaWatch.app 11,900 KB of 15,360, Debug; 4b and 8 skipped as designed). The binary names `startUpdatingHeading` and none of rule 5's location calls.
- CI's watch build, `WATCH_DEST="generic/platform=watchOS Simulator" scripts/watch-build.sh build-for-testing CODE_SIGNING_ALLOWED=NO`: TEST BUILD SUCCEEDED.
- `scripts/watch-build.sh test` on the SE 3: TEST SUCCEEDED, 23/0.
- `python3 tools/i18n-check.py`: clean. `ruby tools/add-watch-targets.rb` re-run: `project.pbxproj` byte-identical. `git diff --check`: clean.
- Not run: the Series 12 (no `ios-pair` step in this task); a real compass (plan 5).

## Left for later

- The widget extension compiles the whole of WatchCore, the compass port included (about 250 KB of its Debug build, still under its 5 MB): `add-watch-targets.rb` gives both targets every core file. Whether the widgets should leave out what they never call is the Lead's call.
- The crown-left wearer's heading on a real watch, the wrist-down page, the announcements, and the needle against a real compass are plan 5's (above, and its checks 5 and 13).

## Review round 1 (31 October, Lane A)

Two reviewers, 09:06 to 09:33: the code-quality lens and the spec lens each found one Important item, and between them 15 minors. Both Important items are fixed here, test-first. No ruling is contradicted; Rulings 39, 41, 42 and 43 hold as before.

### Important 1: the dial kept an old needle after the compass stopped

**The fault.** `QiblaSession.stop` stopped the source but kept the last drawing ("The page keeps what it last drew until that sample comes"). A lowered wrist on an Always On watch makes the app inactive, and the app stays frontmost and dimmed (the Ruling 64 report, item 4). Always On is on by default on the SE 3 and on Series 5 and later. So a wearer who had faced the Qibla kept the lit rim, the needle, the Kaaba marker and "Facing qibla", drawn and in VoiceOver's label, while turning away. The same old needle showed on a swipe back, and after a raise, until Core Location's first heading came. That breaks Review Focus 5 ("no needle points at nothing"), and the Galaxy draws `NO_HEADING_YET` on every start.

**The fix.** The page's life is a pure `QiblaCompass` in WatchCore (`QiblaScreen.swift`), and `QiblaSession` drives it with the heading source and the haptic:
- `start` (never twice, never on a watch with no compass), `stop` and `use(feed)` each draw the resting dial, `QiblaScreen.model(feed, nil)`: no needle, no marker, no lit rim, dimmed, the bearing over the distance, said together. On a watch with no compass the page stays "No compass sensor".
- `take(_:crownOnLeft:)` answers nil once the compass is off, so a heading Core Location hands over after the stop changes nothing.
- Only a heading is announced, and only when it changes the label (`QiblaChange.announcement`, which `QiblaSession.announcements` sends and the page posts while VoiceOver runs). The first build announced every change of the label. With a resting stop, that would have said the resting label each time the page was swiped away or the wrist lowered, over whatever page showed. A new feed is not announced either, which also settles the minor about a feed changing while another page shows. Back on the page, the first heading's label is told afresh.
- A raise now shows the resting dial until Core Location's first heading, a fraction of a second. That is the price of never showing a stale needle, as on the Galaxy.

**The tests** (`QiblaCompassTests`, 12; on the Cape Town sample):
- `testStoppingTheCompassDrawsTheRestingDial` and `testStoppingWhileSearchingDrawsTheRestingDialToo`;
- `testStartingAgainRestsUntilTheFirstHeadingAndJudgesItAfresh`;
- `testASampleAfterTheCompassStoppedChangesNothing`;
- `testStartingTwiceStartsOnce`;
- `testAWatchWithNoCompassNeverStartsAndSaysSo`;
- `testANewFeedRestsTheDialAndJudgesByItsBearing` (Tromsø's golden: bearing 154°, declination 11°);
- `testOnlyAHeadingThatChangesTheLabelIsAnnounced`.

`QiblaCompass` was first written with the first build's behaviour: the drawing kept on stop, and no crown turn. On that, seven of the twelve tests failed on their assertions, these four of Important 1 and three of Important 2. The other five already held. Then the fix, and 12/0.

**What no test here can see.** A throwaway UI test (never committed) faced the Qibla, swiped to Tasbeeh and read `qibla.dial`. Once off the screen the page has no element, and after a swipe back the resting frame lasts less than one 50 ms sample of the scripted compass. The simulator has no wrist to lower. So plan 5's check 5 gains "Qibla, wrist down": face the Qibla, lower the wrist and turn. Expected: the dimmed page is the resting dial, and nothing old is said.

### Important 2: a crown on the left would point the dial 180° the wrong way

**The fault.** `CoreLocationHeading` passed `CLHeading.magneticHeading` on with no orientation. Apple's header (`WatchOS27.2.sdk`, `CLHeading.h:52`) says the heading "is referenced from the top of the device regardless of device orientation as well as the orientation of the user interface". watchOS turns the screen half round for a crown on the left, so for such a wearer the dial would most likely point backwards. The iPhone's own source tells Core Location its interface's orientation (`CompassSource.ios.kt`, `headingOrientation`) for this reason. The report called it "not known", and plan 5's checks 12 and 13 never set the crown on the left.

**The fix.** `HeadingSample.onScreen(crownOnLeft:)` turns Core Location's heading to the screen's top. With the crown on the left it adds 180° (`TrueNorth.correct`, so 230 is 50 and 359.5 is 179.5). Core Location's negative sentinel, and a heading that is not finite, stay as they are, so the sentinel is still never a direction. `QiblaSession` reads `WKInterfaceDevice.current().crownOrientation` on the main actor at each heading, as Tasbeeh reads it for its arrows (watchOS 3 and later). The finding's other way, `headingOrientation = .portraitUpsideDown`, is deprecated in watchOS 27 (`CLLocationManager.h:403`, replaced by `headingBody`), and a pure rule can be held by `swift test`. The sources still hand on Core Location's own value, so `-taqwaHeading` goes into `HeadingSample.magnetic` as it is, and on the simulator, whose crown is on the right, nothing changes.

**The tests:**
- `testACrownOnTheLeftTurnsTheHeadingHalfRound`;
- `testACrownOnTheRightKeepsCoreLocationsHeading`;
- `testACrownOnTheLeftKeepsCoreLocationsSentinel`;
- `testACrownOnTheLeftFacesTheQiblaByTheScreensTop`: Core Location's 230 with the crown on the left is facing, rotation −23. Its 50 there is true 203, "Turn right about 180 degrees".

**Plan 5.** Check 13 now runs with the crown on each side, set in the Watch app on her iPhone (General › Watch Orientation), and back as she wears it. If the needle is about 180° out with the crown on the left, and right with it on the right, watchOS turns the heading itself, and the half turn goes as a fix task. Plan 4's hand-off to plan 5 names both new checks.

### Checks, on the fixed tree

- `swift test --package-path iosApp/WatchCore`: `WatchCoreTests` 285/0, `WatchViewsTests` 6/0.
- `scripts/ios-build.sh` (iPhone 17 Pro): BUILD SUCCEEDED, no warning in the watch's sources. `check-watch-bundle.sh`: every rule holds (TaqwaWatchWidgets.appex 4,380 KB of 5,120, TaqwaWatch.app 12,000 KB of 15,360, Debug).
- CI's watch build (`generic/platform=watchOS Simulator`, `build-for-testing`, `CODE_SIGNING_ALLOWED=NO`): TEST BUILD SUCCEEDED, no warning.
- `scripts/watch-build.sh test` on the SE 3, under `sim-se3`, 09:43 to 09:52: TEST SUCCEEDED, 23/0. The SE 3 was put back as found, as the ledger records.
- `ruby tools/add-watch-targets.rb` re-run: `project.pbxproj` byte-identical, since no file was added to a target.
- The rest (the whole `scripts/test.sh`, `tools/i18n-check.py`, `git diff --check`) is in the ledger's closing line.

### Minors left (recorded in the ledger)

- A sentinel among the gate's first second still announces "23°", and can set `wasAligned` near north and swallow the next buzz. It also flickers dim and undim in the first second.
- A whole-page re-layout for each heading.
- The two-line captions beside the lit halo, and the accessibility sizes, are not shot. The Arabic two-line Low title stands 1 pt under the dimmed rim on the SE 3.
- No `didFailWithError`.
- `CompassVectorsTests` force-unwraps `wanted.heading!` after a kind mismatch.
- The widgets carry the compass port (the Lead's call).
- A feed change mid-use still resets the dial and can buzz again. The new feed itself announces nothing, and the next heading tells its label afresh.
- Process: two reviewers shared one scratch folder name.
