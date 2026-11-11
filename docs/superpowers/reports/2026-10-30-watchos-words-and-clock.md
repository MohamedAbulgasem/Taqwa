# Plan 4 Task 3: the words, the 12-hour clock and the ring's digits the Apple Watch reads, and two words of its own

Lane A, in `watch-apple` (plan 4's own worktree), 30 October 2026. Base `b7d63d1f` ("Merge watch-apple: P4.5"), which is `watch-companion`'s head; `git merge --ff-only watch-companion` found it already up to date. The task's gate (plan 3's Task 10 on `watch-companion`) was open: P3.10 landed as `3e309b78`. Test-first. The Kotlin tests failed to compile on the two missing fields, and the Swift tests on the missing types and fields. Neither red was committed.

**In short:** `WatchScreenWords` gains `appName` (the phone's `app_name`) and `crownHint` ("Turn the crown or tap", seven languages, six of them drafts). The phone's reader fills both, and the Swift port now reads every one of plan 3's fields as the phone sends them: the screens' words with `orEnglish`, `ringDigitZero`, `latinScript` and `clockTime`. It also has `WatchTime`, `clockText` and `spokenClock`. All of it is held to Kotlin by regenerated vectors. `VERSION` stays 1.

## What was made

- **Kotlin, `:watchcore`.** `WatchScreenWords.kt` gains `appName` (after `qibla`) and `crownHint` (after `bezelHint`), both `""` by default, in `orEnglish` and in `ENGLISH` ("Taqwa", "Turn the crown or tap"). `Fixtures.ARABIC_SCREENS` gains "تقوى" and "أدِر التاج أو اضغط".
- **Kotlin, `:shared`.**
  - `WatchScreenWordsReader.kt` reads `appName` as `app_name` through `ScreenStrings.text`, and `ResourceScreenStrings`' `TEXTS` gains `"app_name" to Res.string.app_name`.
  - `WatchWords.kt` gives `crownHint` in each language: en "Turn the crown or tap", ar "أدِر التاج أو اضغط", fr "Tournez la couronne ou touchez", tr "Digital Crown’u çevirin veya dokunun", id "Putar Digital Crown atau ketuk", ur "کراؤن گھمائیں یا تھپتھپائیں", bn "ক্রাউন ঘোরান বা ট্যাপ করুন".
  - All but the English are under the "draft, to native review (plan 5)" markers, now "the five new phrases". The Arabic is a draft too, since the spec gives no Arabic for it.
  - The Turkish apostrophe is the typographic ’, the house style that `tools/i18n-check.py`'s Compose rule allows.
  - Not changed: `WatchFeedInputs`, `feedFor` and `WatchFeedBuilder`. The two words travel through `watchScreenWords(...)` with no edit there.
- **Swift, `iosApp/WatchCore`.**
  - `Feed.swift`:
    - `WatchUi` gains `screens` (default `WatchScreenWords()`), `ringDigitZero: Character?` (absent or `null` is nil, otherwise one UTF-16 unit) and `latinScript` (default true).
    - `WatchDay` gains `clockTime: WatchSix<String>?`.
    - `WatchTime` comes in with `of(_ prayer:)`, and `WatchSix.of(_ time:)` replaces Task 2's `of(_ prayer:)`.
  - `WatchScreenWords.swift` (new):
    - every field, key and default of the Kotlin class, with `english`, `orEnglish()` and `WatchFeed.words`;
    - each key is read by `readDefaulted`, so a `null` is refused;
    - `isBlank` is Kotlin's.
  - `WatchRules.swift` gains `clockText` and `spokenClock`, and `label` reads by `WatchTime.of(prayer)`.
- **Vectors, regenerated.**
  - `feed-vectors.json`: 599,873 to 656,354 bytes. It has 93 decode cases (83 before) and the new `words` section; `clock` goes from 48 to 60 cases.
  - The four samples, `watch-rules-vectors.json` and the five phone goldens changed by exactly `ui.screens.crownHint` and `appName`, checked with a JSON diff.
- **The project.** `tools/add-watch-targets.rb` was re-run: `WatchScreenWords.swift` joins both watch targets (6 pbxproj lines), and a second run leaves the project byte-identical. No other edit was needed: the script finds files on disk.
- **Plan 2's "What leaves the phone" table** (Step 5): the screens' words row names the crown's hint and the app's name, what each is (`app_name`, a new phrase), and where the Apple Watch uses them.
- **Plan 4 Task 3** gains an as-built note.

## The vectors' new parts (all Kotlin's own answers)

- **`expect`** gains `ringDigitZero` (null for none), `latinScript`, and `day0Asr: {clockText, spokenClock}` (null with no day). `theExpectHoldsPlan3sFieldsAndDay0sAsr` holds each one to the decoded feed.
- **`olderPhone`** also lacks `screens`, `ringDigitZero`, `latinScript` and every day's `clockTime` (`theOlderPhoneHasNoneOfPlan3sFields`). Its words are `ENGLISH`.
- **`clock`:** this section existed already (P4.1, after erratum p4 #2: the six times keyed by `WatchTime`, Sunrise among them). This task adds `clockForms`, days 0 and 1 (`theClockCasesHoldTheIsolateRuleInEveryDigitSet`):
  - Bengali, extended Arabic-Indic and Devanagari digits are said as they are;
  - "𝟏𝟔:𝟏𝟎" in the mathematical bold digits is isolated, because each digit is two UTF-16 units, neither of which is a digit to Kotlin's `Char.isDigit`;
  - "19.04" and "20 24" are isolated;
  - the 12-hour forms are "ÖÖ 4:33" (marker first), "6:01 AM" with a narrow no-break space, "12:33" (said as it is), "ÖS 4:10" and "7:04 PM".
- **`words`:** `WatchFeed.words()` written whole on the four samples, `olderPhone` and `blankWords` (`theWordsCasesAreEachFeedsWordsWithTheEnglish`). `blankWords` holds the port's blank rule to Kotlin's `isBlank`:
  - a space, U+00A0, U+3000, tab with line feed, U+001C, U+2028 and "" are blank;
  - U+0085 and U+200B are not;
  - an empty list takes the English, and a list holding only a blank item is kept;
  - a blank preset name takes the English, and an id the English lacks (`custom_1`) is kept.
- **Ruling 57 item 2, for plan 3's fields:** `quotedTurnRightUpTo`, `quotedTurnLeftUpTo`, `bareRingDigitZero` and `quotedLatinScript`, ONE_CHANGE with `swift: malformed`. `PLAN_3_LENIENT` is no longer excluded, so `everyFieldThePortReadsHasItsLenientCase` now covers every lenient path the model has. oneDay's ring zero is null, so `bareRingDigitZero` writes a bare `0` there (Kotlin reads `'0'`). The test compares each lenient case with the same value written in its own type.
- **Ruling 57 item 4:** `nullScreens`, `nullLatinScript`, `twoCharacterRingZero` ("01") and `numberClockTime` (5), ONE_CHANGE and malformed (`plan3sKeysAreRefusedAsKotlinRefusesThem`). The same test also sets each of the 36 words inside `screens` to null in turn, and Kotlin refuses every one.
- **Classes (Ruling 56):** every new case is ENCODER or ONE_CHANGE, by the file's own rule (`everyCaseHasTheClassItsRuleGives`). No FOREIGN case was added: the list stays at 23. `MODEL_TEXTS` gains `blankWords` and `clockForms`.

## Swift tests (95, 0 failures, no warning)

- `ClockVectorsTests`:
  - `testEveryClockCaseMatchesTheVectors`: 60 cases, compared scalar for scalar;
  - `testATwelveHourTimeIsDrawnAndSpokenWhole`: en12's Asr is "4:10 PM" drawn and FSI "4:10 PM" PDI spoken; ar-ly12 is FSI "4:10 م" PDI; capetown is "16:10" both ways; en12's Sunrise is "6:01 AM";
  - `testTheSaudiRingIsWestern`: ring zero "0", digit zero "٠";
  - `testTheTimesAreTheDaysSixInOrder`.
- `WatchScreenWordsTests`:
  - `testEveryWordsCaseMatchesTheVectors`: key by key through `TextValues`, which now reads a map;
  - `testAnOlderFeedSpeaksEnglish`;
  - `testTheSamplesCarryEveryScreenWord`: every field is non-empty before `orEnglish`, the seven built-ins are there, and the English the UI tests listen for is checked: "Passed", "of %1$s", "%1$s of %2$s", "Taqwa", "Turn the crown or tap", "Facing qibla", "Makkah · %1$s km away";
  - `testEveryPhoneGoldenCarriesTheWords`: Tripoli ar-ly12 and Cape Town en;
  - `testANullInEveryWordIsRefused`;
  - `testABlankWordIsKotlinsBlank`;
  - `testBlankFieldsTakeTheEnglish`.
- `FeedVectorsTests`:
  - `testEveryDecodeCaseMatchesTheVectors` reads the new `expect` fields;
  - `testTheKnownDifferencesAreRefusals` covers the 4 + 20 cases;
  - `testANullInEveryKeyWithADefaultIsRefused` adds `screens` and `latinScript` (Ruling 57 item 4);
  - `TextValues.defaults` gains `WatchUi.screens`, each word's default, and `WatchUi.latinScript`.
- `DebugSampleTests`' reference `shifted` now carries `clockTime`. The moved en12 sample keeps it, as it should, and the old reference dropped it.

**Mutants of the port** (a scratch copy, each alone, then restored): 20 of 20 killed. The reviews then found two that survived every test here, test gaps only: `spokenClock`'s digit read as `Character.isNumber` or as `numericType != nil` (the clock vectors held no number that is not a decimal digit), and `isBlank` without U+001D to U+001F, as `0x1C...0x1E` or as `0x09...0x0D, 0x1C` (only U+001C was in the blank words). Ruling 60 kills both (`2026-10-30-watchos-ruling-60.md`): `clockForms`' day 2, in superscript and circled digits, circled numbers and a Roman numeral, and `blankWords`' U+001D, U+001E and U+001F, each a word of its own.

| # | Mutant | Killed by |
|---|---|---|
| 1 | `isBlank` as Foundation's `whitespacesAndNewlines` | the words vectors, the blank test |
| 2 | `spokenClock` tested by scalars | `clockForms` |
| 3 | ASCII digits only | `clockForms` |
| 4 | `clockText` ignores `clockTime` | the clock cases |
| 5, 6 | `screens` or `latinScript` read with `decodeIfPresent ?? default` | the null-key test, `nullScreens`, `nullLatinScript` |
| 7 | `latinScript` defaults to false | `olderPhone` |
| 8 | the ring zero read as a string's first character | `twoCharacterRingZero` |
| 9 | a blank preset name overlays the English | `blankWords` |
| 10 | `crownHint` read from `bezelHint`'s key | `TextValues` |
| 11 | English `appName` wrong | the `olderPhone` words |
| 12 | Sunrise read as Fajr | the clock cases |
| 13 | a word's null read as absent | `testANullInEveryWordIsRefused` alone |
| 14 | `words` without `orEnglish` | `olderPhone` |
| 15 | the ring zero refuses null | every oneDay case |
| 16 | a list of blanks taken as English | `blankWords` |
| 17 | a 24-hour time isolated | the clock cases |
| 18 | a label always by Fajr's name | the labels |
| 19 | U+0085 counted as blank | `blankWords`, the blank test |
| 20 | U+001C not blank | `blankWords`, the blank test |

`FeedStoreTests.testTheAppGroupIsTheIPhones` also fails in the scratch copy at baseline, because it reads the repository's entitlements, which the copy lacks. Every mutant is also killed by the tests named here.

## Checks (Step 4 and the gates), under the host state of 30 October

`xcodebuild -checkFirstLaunchStatus` exited 69 at 15:27 and again at 15:30. No `xcrun simctl` was called, no `runFirstLaunch` was started, and no simulator lock was taken.

- `swift test --package-path iosApp/WatchCore`: 95 tests, 0 failures, no warning (84 at the base).
- `scripts/test.sh :shared:testAndroidHostTest :widgetcore:testAndroidHostTest :watchcore:testAndroidHostTest :wearApp:testDebugUnitTest` with the three `linkDebugTestIosSimulatorArm64` tasks, `--rerun-tasks`: exit 0.
  - check-versions 1.3.0 (39)/(100039), test-bump PASS.
  - shared host 2271/0 (2269 before, plus `theAppleWordsAreThePhonesOwn` and `theCrownHintIsInEveryLanguage`), widgetcore 111/0, watchcore 222/0 (217 before; `SwiftPortVectorsTest` 23), wearApp 267/0. Nothing skipped and no EOFException; the known flake did not come up.
  - `:shared`, `:watchcore` and `:widgetcore` test binaries linked.
- **The size tests** (plan 2's and plan 3's), all green on the host. The figures are below.
- `-Ptaqwa.updateVectors` on `:watchcore` and `:shared` a second time leaves every generated file byte-identical (`feed-vectors.json` sha1 `b9ee3daf`).
- `IOS_DEST='generic/platform=iOS Simulator' scripts/ios-build.sh build ARCHS=arm64`: BUILD SUCCEEDED. TaqwaWidget.appex 3 MB; check-watch-bundle: every rule holds, TaqwaWatchWidgets.appex 1396 KB, TaqwaWatch.app 2988 KB (4b skipped ad hoc, 8 skipped Debug).
- `WATCH_DEST='generic/platform=watchOS Simulator' scripts/watch-build.sh build` and `build-for-testing`: BUILD SUCCEEDED and TEST BUILD SUCCEEDED.
- `tools/i18n-check.py`: clean. `git diff --check`: clean.

**Measures** (the host's `[measure]` lines):

| Measure | Now | Before (P3.10) |
|---|---|---|
| Largest feed, words in: bn-BD/12 CONTROL | 59,989 of 60,000 UTF-8 bytes, 63 phrases | 59,870 |
| The same as a message | 59,989 data bytes, path 65 | |
| Largest times-alone feed: bn-IN/12 | 33,875 of 40,000 | 33,756 |
| Screens' words, largest (bn) | 2,555 bytes | 2,436 |
| `WorstFeeds.SCREENS` | 4,702 bytes | 4,518 |

**DEFERRED to the catch-up gate (Ruling 59):**

- The Kotlin/Native `iosSimulatorArm64Test` suites of `:shared`, `:watchcore` and `:widgetcore`, so `scripts/test.sh` run whole. Among them:
  - `WatchScreenWordsResourceTest.theDefaultReadsThePhonesOwnStrings`, the only test that exercises `ResourceScreenStrings`' new `app_name` line through Compose's real resources. The host's stand-in reads `strings.xml` directly; the line is compiled and linked;
  - `IosWatchLinkTest.theLargestContextFitsTheSmallestLimit`, whose host twins `theLargestFeedStaysWithinItsBudget` and `aFeedAtTheBudgetIsWithinTheMessageLimit` passed;
  - `WatchScreenWordsTest` and `WatchFeedJsonTest` on iOS.
- `scripts/ios-build.sh` at its default iPhone 17 Pro.

## Errata and rulings

1. **ff-only merge:** done; already up to date at `b7d63d1f`, which holds P3.10 and P4.5.
2. **Ruling 57 item 4:** the four refusals are vector cases, `screens` and `latinScript` are in Swift's null-key test and in `TextValues.defaults`, and every word inside `screens` refuses a null on both sides.
3. **Ruling 58 item 3:** the port works nothing out. `clockText` is `clockTime ?? clock`, as sent. A 12-hour phone's day period is the one its `clockTime` carries, its own locale's, even outside the seven languages. `ui.am` and `ui.pm` are untouched.
4. **Step 4 under the host state:** the host twins and the links ran, and the simulator suites are DEFERRED, as listed above.
5. **Plan 4's errata (p4):**
   - #0: the Swift `WatchScreenWords` carries `phrasesAction` by the every-field rule.
   - #1: `WatchTime` and `WatchSix.of(_ time:)` are ported, and `clockText` and `spokenClock` are keyed by the six times, Sunrise's "6:01 AM" included.
   - #3: Tasks 1 and 2 are in.
   - The others are for later tasks (#4 belongs to Task 11's VoiceOver constraint).
6. **Rulings 55–59:**
   - Ruling 56: the classes and the port's contract are kept, with no FOREIGN case added.
   - Ruling 39: nothing about the place was added. The two words are interface text, the same on every phone of a language; plan 2's table says so.
   - Rulings 41, 42 and 43 are not touched: no face, complication or reading order changes here.
7. **WIP commits:** `61273a9e` and `0e10b6ba`, folded into the task's commit.

## Left open, for the reviewers and the Lead

- Kotlin's `presets` is ordered (the built-ins' order). Swift's is a `[String: String]`, since `JSONDecoder` keeps no order. Every test compares by key; Task 8 should list the built-ins in the watch's own order.
- `ResourceScreenStrings`' `app_name` line has no host test. The simulator test above holds it, at the catch-up gate.
- Kotlin's `Char.isDigit` and `isWhitespace` follow the JDK's Unicode tables, and Swift's `generalCategory` follows the OS's. They could differ only on a character Unicode adds or moves, and no phone writes one.
