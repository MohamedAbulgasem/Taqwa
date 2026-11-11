# Plan 4 Task 2: `WatchCore` in Swift, held to the Kotlin vectors

Lane P, in `watch-apple` (plan 4's own worktree), 29–30 October 2026. Base `846adc9f` ("Merge watch-feed: R55"): `watch-apple` (`0c6f7e9e`, no commits of its own) was fast-forwarded to `watch-companion` first (erratum 1), so P4.1's vectors with their classes (Ruling 56) and R55 are in.

**In one line:** `iosApp/WatchCore/` is a Swift package holding the feed's model, its decoder and `WatchRules`, ported from `:watchcore`. `swift test --package-path iosApp/WatchCore` runs 15 tests against the files Kotlin commits in `watchcore/vectors/`, and `scripts/test.sh` runs it first on a Mac. Review round 1's three Importants are fixed (below).

## What was built

| File | What it holds |
|---|---|
| `Package.swift` | tools 6.0; `.macOS(.v14)`, `.watchOS(.v10)`; library `WatchCore`, test target `WatchCoreTests`; `swiftLanguageModes: [.v5]`. |
| `Sources/WatchCore/Feed.swift` | `WatchFeed`, `WatchDay`, `WatchTimes`, `WatchSix<T>` (`of(_:)`, `sunrise`), `WatchPlace`, `WatchUi`, `WatchSpoken`, `WatchCount`, `WatchCountdownPhrases`, `WatchPhrase`, each `Decodable, Equatable`, with `WatchFeed.kt`'s keys. Every Kotlin `Int` is an `Int32` (`version`, `upTo`, `target`) and every `Long` an `Int64`, each read whole (`decodeWhole`: `JSONDecoder`'s own read of the type, then the same number as a `Decimal`, which must equal it, so a fraction is refused, not rounded). `digitZero` is read as a string of exactly one UTF-16 unit (`UTF16Unit`). A key with a non-null Kotlin default is read only when present (`readDefaulted`, `readDefaultedWhole`: `contains`, then `decode`), so a `null` there throws; a Kotlin `T? = null` is `decodeIfPresent` (`decodeWholeIfPresent` for a `Long?`). |
| `Sources/WatchCore/FeedDecoder.swift` | `FeedDecodeResult` and `FeedDecoder.decode(_:)`: Ruling 56 item 4, nothing more. The root's `version` is read first as a whole `Int32` (a probe struct with that one key), and a version above 1 is `.newerVersion` without the body. A version that is missing, below 1, `null`, quoted, a boolean, a fraction or beyond an `Int32` is `.malformed`. Then `JSONDecoder` with default strategies decodes the body. There is no scanner of its own. |
| `Sources/WatchCore/WatchRules.swift` | `WatchPrayer`, `WatchStart`, `WatchMoment`, and `WatchRules`: `starts`, `moment`, `countdown`, `spokenCountdown`, `counted`, `digits`, `inDigits`, `filled`, `litOnList`, `litOnGlance`, `needsRefresh`, `today`, `dayStart`, `label`, with the private helpers line for line. |
| `Tests/WatchCoreTests/Vectors.swift` | Finds `watchcore/vectors/` from `#filePath` and reads the files with `JSONDecoder` (never `JSONSerialization`, which drops a string's opening U+FEFF). A case's text is its `json`, or the whole of its `file`, after EF BB BF where `bom` is true. `nonEmpty` fails a test on an empty section and prints the count. `JSONValue` and `TextValues` are the reference every feed read is held to (below). |
| `RulesVectorsTests.swift` | `testEveryRuleCaseMatchesTheVectors` (57), `testEveryCountdownMatchesTheVectors` (70), `testEveryRefreshMatchesTheVectors` (8), and `testEveryRulesFeedIsOfThisVersion` (16 feeds, each also held to its text key by key). |
| `FeedVectorsTests.swift` | `testEveryDecodeCaseMatchesTheVectors` (56, by class), `testTheKnownDifferencesAreRefusals`, `testSpokenCountdownsMatchTheVectors` (50), `testLabelsMatchTheVectors` (50), `testDaysMatchTheVectors` (33), and four more, below: `testEveryIntIsAnInt32AndEveryLongAnInt64`, `testANullInEveryKeyWithADefaultIsRefused`, `testEveryIntegerIsReadExactlyOrNotAtAll` and `testTheRulesNeverTrapOnExtremeNumbers`. |
| `PhoneGoldensTests.swift` | `testEveryPhoneGoldenDecodes` (5 goldens, 31 days, a language) and `testTheFourSamplesDecode`, each feed held to its text key by key. |

Also changed:
- `scripts/test.sh`: on Darwin with no arguments, `swift test --package-path iosApp/WatchCore` runs before Gradle, and `set -e` stops the script on a failure.
- `.github/workflows/ios.yml`: a step "The Apple Watch's Swift core against the vectors", before the simulator tests. That job names its Gradle tasks, so `test.sh`'s own swift step does not run there.
- `.gitignore`: `iosApp/WatchCore/.build/` and `.swiftpm/`.
- `README.md`: `WatchCore/` in the module tree.

## How each class binds the port (Ruling 56)

`testEveryDecodeCaseMatchesTheVectors` reads `class` from each case:
- **`ENCODER` (18) and `ONE_CHANGE` (15):** the answer is exactly `swift ?? result`, and a feed matches `expect` field by field (days, day 0's `hijriDayMonth`, `gregorianSpoken` and `midnight`, `digitZero`, `hasSpoken`, phrases, city). Then every other field of the feed is held to the text, key by key (the reference, next). All 33 pass.
- **`FOREIGN` (23):** the port has to answer, so no crash or hang. Where it answers `feed`, its values must be the text's own JSON values, as far as Foundation's `Decimal` keeps a number's digits (about 38 significant digits; past that a `FOREIGN` number may read as its rounded whole value: Ruling 57 item 1, which amends Ruling 56 item 2). A refusal is always a right answer (Ruling 57 item 3). The reference is the text's own tree (`JSONValue`, `TextValues.assertHolds`):
  - `JSONDecoder` reads the same text into a tree of JSON kinds, never through the model's types or its hand-written `init(from:)`. A number is kept as a `Decimal`, which keeps its digits, beside the `Double` nearest it. A string is kept as its scalars.
  - Each stored property of the feed is found in the tree by its own name, which is its key, through `Mirror`, so a key read into another property fails.
  - A Kotlin `Int` or `Long` must equal the tree's `Decimal` exactly. A `Double` must be the tree's nearest `Double`, bit for bit. A string or `Char` must be the same scalars, not merely canonically equivalent. A key the text lacks must hold its Kotlin default (`TextValues.defaults`) or nil.
  - A key written twice has no one JSON value (RFC 8259). The tree is read by the same reader as the port, so there the reference is the copy `JSONDecoder` keeps, the first.

  The round-0 check, `JSONSerialization`'s tree written back and decoded again (`plainForm`), is gone. It was Foundation against Foundation, and review round 1 measured it failing both ways: it agreed with the port's rounding of a fraction too small for a `Double` (`1792303200.0000000001`), and it rounded `9007199254740993.0`, which the port reads exactly. Both are now held (`testEveryIntegerIsReadExactlyOrNotAtAll`).
- The other sections' cases must say `ENCODER`, and so must the decode case of the feed each one names.

`testTheKnownDifferencesAreRefusals`: exactly the four quoted cases carry `swift`, each `result` `feed` and `swift` `malformed`, and each decodes `.malformed`. No `ENCODER` or `ONE_CHANGE` case that Kotlin refuses or calls newer decodes `.feed`. `FOREIGN` is left out on purpose, under Ruling 56.

What the port answers on each `FOREIGN` case. This is information only: Kotlin's answer is not asserted.

| Case | Kotlin | Port | Holds its text, key by key |
|---|---|---|---|
| notJson | malformed | malformed | (no JSON) |
| fractionalVersion, fractionalWrittenAt, fractionalTime, fractionalTarget | malformed | feed | yes, 67 values (`1.0` is the value 1) |
| fractionalNewerVersion | malformed | newer | (2.0 is 2) |
| byteOrderMark, the three trailing commas, escapedKeyFractionalWrittenAt | malformed | feed | yes, 67 values |
| exponentVersion, exponentWrittenAt, escapedVersionKey | feed | feed | yes, 67 values |
| duplicateVersionNewerLast | newer | feed | yes, 67 values (first copy, 1) |
| duplicateVersionNewerFirst | feed | newer | (first copy, 2) |
| duplicateVersionZeroFirst | feed | malformed | (first copy, 0) |
| duplicateVersionWholeFractionFirst, duplicateWrittenAtFractionLast, duplicateWrittenAtWholeFractionFirst | malformed | feed | yes, 67 values (first copy) |
| duplicateMidnightInADay, duplicatePhrasesLast | feed | feed | yes, 67 values (first copy) |
| duplicateCityNotAString | malformed | malformed | (first copy is 5, no string) |

## Beyond the plan's test list, and why

- **`testEveryIntIsAnInt32AndEveryLongAnInt64`.** The vectors hold one end of two `Int` fields (`wideVersion`, `wideTarget`). A field declared `Int` would pass them on a Mac (64 bits) and behave otherwise on a watch (32). Mutant M8 (`upTo` an `Int64`) survived the vectors. This test, on `oneDay` with one number changed, holds both ends of `target`, `upTo`, `version`, `writtenAt`, a `midnight` and a time:
  - each `Int32`/`Int64` bound is read exactly;
  - one past each bound is `.malformed`;
  - an `Int32.max` version is `.newerVersion`.

  It pins the types at compile time too, so M8 no longer builds.
- **`testANullInEveryKeyWithADefaultIsRefused`** (review round 1, Important 1). The Global Constraints' "a `null` where Kotlin has a non-null default is refused" is held at each of the five defaulted keys, on `oneDay` with that one value set to `null`: `midnight`, `hijriDayMonth`, `gregorianSpoken`, `digitZero` and `tasbeeh` are each `.malformed`, as Kotlin answers (measured by the review). The vectors held only `hijriDayMonth`. The idiomatic slip, `decodeIfPresent(...) ?? default`, takes the default where Kotlin refuses. The test also holds the other side: a `null` at a nullable key (`fajrEnd`, `asrEnd`, `spoken`) is a feed with nil. Task 3 adds plan 3's `screens` and `latinScript` to the list.
- **Every feed held to its text, key by key** (review round 1, Important 3). `TextValues.assertFeedHolds` runs on every feed the port reads in the tests: the decode cases of every class, the four samples, the five phone goldens and the 16 rules feeds. The hand-written `init(from:)`s map each key to its property by hand, and `expect` names only a few fields, so a swap such as `weekday`/`dayNumber` passed before. Task 3's additions to the same inits are held the day they land, since the walk visits every stored property.
- **`testEveryIntegerIsReadExactlyOrNotAtAll`** (review round 1, Important 2). It holds Ruling 56 item 2's value rule at every integer `oneDay` holds (21 members, the two `upTo`s in plan 3's `screens` among them), each written five ways: `n.00000000000000000001`, `(n−1).99999999999999999999`, `n.0`, `ne0` and `n0e-1`. Where the port answers feed, the feed must hold the text (above). Then `writtenAt` 9007199254740993 with a point and with an exponent must be a feed with exactly that value. A version just above or below 2 must be `.malformed`, not newer.
- **`testTheRulesNeverTrapOnExtremeNumbers`.** Kotlin's `Long` arithmetic wraps and Swift's traps. So every sum or difference of instants in `WatchRules.swift` is `&+`/`&-`: Kotlin's answer, and no crash on a feed whose numbers are at an `Int64`'s ends. The test runs every rule at six instants on such feeds. Mutants that put back a plain `-`/`+` in `needsRefresh`, `moment`'s elapsed and total, and the last day's end trap, so they are killed. Two survive, because no instant reaches them: the plain `-` in `secondsLeft` and in solar midnight.

## Choices the plan left open, and where Swift needs its own

- **Integers are read whole** (review round 1, under Ruling 56 item 2). `JSONDecoder`'s own `Int64` and `Int32` read takes a number with a point or an exponent through a `Double` (measured on Swift 6.4). So, on its own, it reads `1792303200.00000000000000000001` as 1792303200, `0.99999999999999999999` as 1, and `1e-400` as 0. Item 2 says a feed's values are exactly the text's, with no rounding. So each `Int` and `Long` is read by `decodeWhole`: the type's own read, which keeps the range check and refuses a quoted number or a boolean, then the same number again as a `Decimal`, which must equal it. A whole number in any spelling (`1.0`, `1e0`, `9007199254740993.0`) is still read exactly. This is still `JSONDecoder` with default strategies and strict `Int32`/`Int64` types (item 4), with no scanner of its own. The root's version is read the same way.
- **Plan 3's fields stay Task 3's.** The model at the branch head also has `WatchUi.screens`, `ringDigitZero` and `latinScript`, and `WatchDay.clockTime` (plan 3's Task 3 is in). Task 2's Interfaces say "Task 3 adds plan 3's fields", and Task 3 lists them, with `WatchScreenWords`, `WatchTime`, `clockText` and `spokenClock`. So this port ignores those four keys as a later phone's. Until Task 3, a bad value under one of them (`"ringDigitZero":"01"`, say) is a feed in Swift and malformed in Kotlin. No phone writes it, and no vector case holds it. `filled` and `inDigits` are here already, with Task 3's signatures, because `countdown` and `digits` are made of them.
- **Ties.** Kotlin's `minByOrNull`/`maxByOrNull` keep the first of equal elements, and Swift's `max(by:)` keeps the last. So the three picks are explicit loops. Mutants R1 and R3, which keep the other one, are killed by the combined Maghrib/Isha cases.
- **`filled`** scans by Unicode scalar, as Kotlin's regex scans UTF-16. A place's units are all ASCII, so a combining mark after a place's `s` is no part of it, where a scan by `Character` would miss the place. The number is read as an `Int32`, as `toIntOrNull` reads it.
- **`inDigits`** adds the digit to the zero's UTF-16 unit in 16 bits, as Kotlin's `Char` arithmetic does. A unit that is a surrogate is written U+FFFD, where Kotlin would write a lone surrogate. Only a zero within nine of a surrogate gives one, and no phone sends one.
- **Where Kotlin throws, Swift answers.** `dayStart` returns `Int64?`, nil for an index the feed has no day at, where Kotlin `require`s. `counted` on an empty list gives the number alone, where Kotlin's `last()` throws; `spokenCountdown` never passes one. `label` gives "" for a day the feed lacks. No watch surface may crash.
- **`label`** is `uppercased(with: Locale(identifier: ui.language))`. It meets all 50 label cases, the Turkish `İKİNDİ`/`MAGHRİB` and the English `IKINDI` among them. (Since Ruling 57 item 6 it cases by the primary language subtag, as Kotlin does: `docs/superpowers/reports/2026-10-30-watchos-ruling-57.md`.)

## Mutants (scratch copy, each alone, the shipped file put back)

Review round 1's fixes (scratch copy `p42f1/mut`, `tools/mutants.py`, 30 mutants, each alone):
- The four the review saw survive, each defaulted key read by `decodeIfPresent(...) ?? default` (`midnight`, `gregorianSpoken`, `digitZero`, `tasbeeh`), are all killed, by `testANullInEveryKeyWithADefaultIsRefused`. So is the control on `hijriDayMonth`, and a `fajrEnd` that refuses `null`.
- The seven key swaps the review saw survive are all killed, by the key-by-key holds: `weekday`/`dayNumber`, `hijri`/`gregorian`, `am`/`pm`, `rtl`/`twelveHour` (each way), `nextIn` from `language`, `date` from `weekday`, `clock` from `names`. So are `names` from `clock`, `maghrib`/`isha`, `fajrEnd` from `asrEnd`, a phrase's `id`/`phrase`, and `sunrise` from `fajrEnd`. Each fails between 7 and 4,400 assertions.
- `decodeWhole` without its `Decimal` check is killed (36 failures), and so is a plain read in place of the whole one at `writtenAt`, `midnight`, `asr`, `asrEnd`, `upTo`, `target` and the root's version. So is a `decodeWholeIfPresent` that reads `null` as a value.
- One survives, and it is equivalent: the body's `version` read plainly. The root's probe has already read that key whole, the same first copy, before the body is read.

Decoder, round 0:
- M1 (a `digitZero` of one `Character`), M2 (the version probe an `Int64`), M3 (`target` an `Int64`), M4 (defaulted keys by `decodeIfPresent`), M5 (no version read first, so `newerShape`/`newerDayShape` are malformed), M6 (no newer), M7 (version 0 read) and M9 (`midnight` an `Int32`) are all killed.
- M8 (`upTo` an `Int64`) survived the vectors. It is killed by the new range test, at compile time.
- M13 is a port that drops `exponentWrittenAt`'s exponent. Only the `FOREIGN` value check kills it, so that check bites.

Rules:
- R1–R11, R13–R15 and R17–R19 are all killed: ties, the glance's last lit, under a minute, Asr's and Fajr's ends, the first day's start, refresh's bound, the label's locale, the places' order, the digits, `counted`'s bound, the last day's end, solar midnight, the clamp, and the guard.
- **R12 survives:** yesterday's starts at or before today's Fajr, instead of before it.
- **R16 survives:** a start at the next day's Fajr kept in `starts`.

  Both are an equality no vector holds: a start exactly at the next Fajr. They are gaps in plan 1's and Task 1's vectors, for the Lead. No Swift-only case was added for a rule, since the rules' answers have to be Kotlin's.

## Errata (the Lead's list), as resolved

1. `git merge --ff-only watch-companion`: `0c6f7e9e` → `846adc9f`. Rulings 55 and 56 were read and followed.
2. **Step 0**, commit `822791fa`, on its own, before any code. Plan 4 Task 2's Interfaces now carry Ruling 56 item 4's words:
   - the version read first;
   - `JSONDecoder` with strict `Int32`/`Int64`, `digitZero` one UTF-16 unit, and default strategies;
   - `ENCODER`/`ONE_CHANGE` exact, `FOREIGN` with no crash and exact values;
   - no scanner.

   The struct line reads `version: Int32` (with `upTo`/`target` `Int32`). The same commit brings three neighbours in line with Ruling 56, since they contradicted it: the Global Constraints' feed bullet, a note under call 8, and the two decode tests in Step 1.
3. **Ruling 55 item 6 (the Wear debug APK carries `feed-vectors.json`) was left for Lane W**, and the ledger says so. `wearApp/build.gradle.kts` was Lane W's open work then: its running workflow went on to P2.6, which edits that file and records the watch APK's baseline for its library checks. Lane W has since done it, at `fa62c182` ("Wear: the debug APK carries the four sample feeds alone, copied from the vectors, so no test vector reaches an APK"), merged into `watch-companion` with P2.6.
4. WIP commits were made as the work went (`4ded6c37`, `96baa5c7`) and are folded into the task's commit. The resume list is below.
5. No flake was met: `scripts/test.sh` was green on its first run, with no EOFException.

## For the Lead

- **The limit of "exactly" (Ruling 56 item 2), measured.** A `Decimal` keeps a number's first 38 significant digits at least (39 measured, on `1792303200.` followed by 28 zeros and a 1), and drops any after them. So a fraction past the 39th significant digit at an integer field (`1792303200.` with 29 or more zeros and then a 1) still reads as the whole number. That text is `FOREIGN`, and no phone writes a point or an exponent there. To meet item 2 there too, the port would have to read the number's own digits, a scanner of its own, which item 4 rules out. I kept both items, met item 2 as far as Foundation's readers go, and stated the limit in `decodeWhole`'s and `FeedDecoder`'s docs. Whether item 2's words should name it was the Lead's call. **Ruled (Ruling 57 item 1):** "exactly" holds as far as Foundation's `Decimal` keeps a number's digits, about 38 significant digits; past that a `FOREIGN` number may read as its rounded whole value, and `ENCODER` and `ONE_CHANGE` texts, plain integers, are unaffected. The plan's Global Constraints, `decodeWhole`'s and `FeedDecoder`'s docs and this report now say so.
- **Item 4's words.** "strict Int32/Int64 types and default strategies" still describes the port. Each integer is now also checked against its `Decimal` read. Task 2's Interfaces could say so. Plan 3's only integers are the `upTo`s of `screens`' two turn lists, which are `WatchCount`s and so are read whole already; any other `Int` or `Long` a later task adds must use `decodeWhole`. `testEveryIntegerIsReadExactlyOrNotAtAll` already writes those two `upTo`s five ways, so it holds Task 3's read of them.
- **A bare value for a `Char`, now measured by review round 1.** `StreamingJsonDecoder.decodeChar` reads with `consumeStringLenient` (javap), and `oneDay` with a bare `"digitZero":0` or `5` is a feed in Kotlin. The port refuses it, since `JSONDecoder` wants a string. By the file's rule that text is `ONE_CHANGE`, so it is a fifth call-8 difference beyond the quoted four, in the safe direction. It needs a `swift` line in the vectors (P4.1's generator) or a ruling. Not changed here. **Ruled (Ruling 57 item 2):** an accepted difference in the safe direction; each such case is in the vectors as `ONE_CHANGE` with `swift: malformed` (`docs/superpowers/reports/2026-10-30-watchos-ruling-57.md`).
- **R12 and R16 survive** (above): two equalities no vector holds. **Ruled (Ruling 57 item 5):** vectors that kill them, and the other survivors, are added (`docs/superpowers/reports/2026-10-30-watchos-ruling-57.md`).
- **Plan 3's four fields** are Task 3's (above).

## Verification

- Fix round 1, after review round 1 (30 October, before its 00:43 commit `e83c9451`): `swift test --package-path iosApp/WatchCore` 15 tests, 0 failures, on a clean build with no warning. The sources build for `arm64_32-apple-watchos10.0` with no warning. The red step was seen first: the new exactness test failed 36 times on the round-0 port (re-counted on 30 October with `ee14fa91`'s sources under `e83c9451`'s tests). 34 are the two fractional forms at each of the 17 integers the model reads and the integer read alone does not refuse (an `upTo` of 0 is refused, since `0.00000000000000000001` is no whole `Double`). The other 2 are the versions just above and below 2, which the round-0 port called newer. The other new checks passed on it. `scripts/test.sh`'s run is in the ledger.
- Round 0: `swift test --package-path iosApp/WatchCore`: 13 tests, 0 failures. Every section is read and counted:
  - rules 57, countdowns 70, refreshes 8, feeds 16;
  - decode 56 (18/15/23);
  - spoken 50, labels 50, days 33;
  - goldens 5, samples 4.

  A fresh build shows no compiler warning.
- The sources also build for `arm64_32-apple-watchos10.0` against the watchOS SDK. That is the 32-bit watch whose `Int` is 32 bits.
- `scripts/test.sh` exit 0, 30 October 00:00:
  - check-versions 1.3.0 (39)/(100039), bump PASS;
  - swift test first (13/0);
  - shared host 2305 / iOS 2258, widgetcore 111/109, watchcore 130/117, wearApp 143;
  - 0 failures.
- The red step (Step 2) failed as planned, with the types missing, and was not committed.
- No device, emulator, simulator by hand or lock. KB2604110095 and the work iPhone 12 were not touched. No vector was regenerated, since none needed it.

## Resume list (if a later session continues)

The task is complete at its commit. If a review round follows:
1. Work in `watch-apple`, from the task commit (on top of `822791fa`).
2. Mutation scaffolding:
   - copy `iosApp/WatchCore` (without `.build`) and `watchcore/vectors` into a scratch `mut/` tree with the same layout;
   - mutate one source line there;
   - read the "All tests" line of `swift test`.

   A sub-suite's "with 0 failures" is no verdict.
3. Erratum 3 is done: Lane W's `fa62c182`, in `watch-companion` since P2.6.
