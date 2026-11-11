# Ruling 57 items 1, 2, 3, 5 and 6: P4.2's follow-ups for the Apple Watch's Swift core

Lane A, in `watch-apple` (plan 4's own worktree), 30 October 2026. Base `9b6009c0` ("Merge watch-wear: P2.6"): `watch-apple` (`dfe2ef52`, no commits of its own) was fast-forwarded to `watch-companion` first. This is not a plan task. It is the five items of the ledger's Ruling 57 that do not wait for plan 3, which the Lead gave Lane A ahead of P4.3. Item 4 (plan 3's keys) stays P4.3's.

**In one line:** the FOREIGN value rule now names its limit (item 1); 16 new `ONE_CHANGE` cases hold the port's refusal of every wrong type kotlinx reads leniently (item 2); the exactness test can no longer turn a correct port red on another Foundation (item 3); new vector sections kill every surviving rule mutant that can be killed (item 5); and `label` cases as Kotlin does on the JVM (item 6). `swift test` is 19 tests, 0 failures. `scripts/test.sh` exits 0.

## Item 1: the FOREIGN precision note

Ruling 56 item 2's "exactly the text's value" now says how far it holds: as far as Foundation's `Decimal` keeps a number's digits, about 38 significant digits. Past that, a `FOREIGN` number may read as its rounded whole value. `ENCODER` and `ONE_CHANGE` texts hold plain integers, so they are unaffected. The words are in:
- the plan's Global Constraints ("The feed", the bullet on classes) and Task 2's Interfaces;
- `decodeWhole`'s doc and `Feed.swift`'s header;
- `FeedDecoder`'s doc;
- the vectors' `about`, in Ruling 56's own sentence;
- the P4.2 report: the FOREIGN bullet and "For the Lead".

## Item 3: a CI-safe exactness test, and the doc minors

**The test.** In `testEveryIntegerIsReadExactlyOrNotAtAll`, `writtenAt` 9007199254740993 with a point or an exponent used to have to be a feed (`XCTFail` otherwise), and some form had to be a feed (`XCTAssertGreaterThan(feeds, 0)`). Every one of those texts is `FOREIGN`. So now:
- each form is `guard case .feed else continue`, and the exact-value check follows;
- the count of feeds is printed (`integer forms: 67 of 105 were feeds` on this Mac), not asserted;
- the versions just off 2 must still be `.malformed`, which is the refusal, and the only answer that reads no rounded value.

Evidence, test-first. A scratch port that refuses those two spellings stood in for a stricter Foundation. Under the old test it was red, with 2 failures ("writtenAt 9007199254740993.0 is no feed", and the same for `e0`). Under the new test it is green. The real port is green under both. The test still bites: `decodeWhole` without its `Decimal` check fails it 36 times.

**The doc minors:**
- `.github/workflows/ios.yml`'s header no longer says the app's build is the only CI step that compiles Swift. Two steps do: `swift test` (the watch's core) and the build (the app and its widget).
- P4.2 report :110 and :151: erratum 3 is done, by Lane W, at `fa62c182`.
- P4.2 report :124: the run is fix round 1's, before its 00:43 commit `e83c9451`, not "Review round 1 (00:45)". Its red step is 36, re-counted today with `ee14fa91`'s sources under `e83c9451`'s tests: 34 value holds and the 2 versions just off 2, which the round-0 port called newer. The report said 34.
- `decodeWhole`'s mechanism, now measured on Swift 6.4 and macOS 27. `JSONDecoder` reads an integer written with a point or an exponent through the nearest `Double`. That `Double` must be whole and within range, or the number is refused (`1.5`; `9223372036854775807.0`, whose nearest `Double` is 2^63). Below 2^53 that `Double` is the answer (`1792303200.00000000000000000001` reads 1792303200, `0.99999999999999999999` reads 1, `1e-400` reads 0). From 2^53 up, it reads the digits again as a `Decimal` and keeps their whole part (`9007199254740993.5` reads 9007199254740993, and `.9` does too). The old doc said "through a `Double`" alone, which the `.5` case contradicted (review round 2's minor 3).

## Item 2: the lenient wrong-type cases

A value of the wrong type that kotlinx reads leniently (each reader measured with `javap` on kotlinx-serialization-json 1.11.0's `StreamingJsonDecoder`) is an accepted difference in the safe direction: no phone writes one, and the port's refusal keeps the last feed. Before, only the four quoted cases (call 8) carried `swift`. Now there is one case for each other field the port reads, each `ONE_CHANGE` with `swift: "malformed"`:

| Case | The change on oneDay | kotlinx's lenient read |
|---|---|---|
| `quotedFajr`, `quotedSunrise`, `quotedDhuhr`, `quotedAsr`, `quotedMaghrib`, `quotedIsha` | a time as a string | `consumeNumericLiteral` |
| `quotedFajrEnd`, `quotedAsrEnd` | a nullable `Long` as a string | the same |
| `quotedMidnight` | a defaulted `Long` as a string | the same |
| `quotedDeclination` | a `Double` as a string (`"-27.0"`) | `consumeStringLenient`, then `Double.parseDouble` |
| `quotedSouthern`, `quotedTwelveHour` | a `Boolean` as a string | `consumeBooleanLenient` |
| `bareDigitZero` | `"digitZero":0` | `decodeChar`'s `consumeStringLenient` |
| `quotedHoursUpTo`, `quotedMinutesUpTo` | a spoken count's `upTo` as a string | `consumeNumericLiteral` |
| `quotedTarget` | a phrase's `target` as a string | the same |

**Generated, never hand-written.** `SwiftPortVectorsTest.kt` walks the model's own serializers (`lenientPaths`: every Int, Long, Double, Boolean and Char field, by path). For each field not in the quoted four and not plan 3's, it writes oneDay with that field's own value in the lenient form, and names the case from the path. Kotlin's checks:
- `swiftDiffersOnlyWhereKotlinReadsAWrongType` replaces `swiftDiffersOnlyOnTheFourQuotedCases`. The `swift` cases are exactly `QUOTED + LENIENT`, each `ONE_CHANGE`, `feed` and `malformed`.
- `everyFieldThePortReadsHasItsLenientCase`:
  - Kotlin decodes each case to oneDay's own feed, value for value;
  - the changed value is a string, or a bare digit for the `Char`;
  - the fields covered, the quoted four's included, are exactly the model's lenient fields less plan 3's.

  So a field added later without its case fails here.
- `everyCaseHasTheClassItsRuleGives` (unchanged) gives each its class from its text alone.

The file's `about` says why: a refusal is the safe side.

**Plan 3's fields are left to P4.3** (`PLAN_3_LENIENT`): `ui.screens.turnRight[].upTo`, `turnLeft[].upTo`, `ui.ringDigitZero` and `ui.latinScript`. The port ignores them until Task 3. So their lenient forms are a feed on both sides today, and a `swift` line now would be false. When Task 3 reads them, it moves them out of `PLAN_3_LENIENT`, and the generator writes their cases.

Swift: `testTheKnownDifferencesAreRefusals` names all 20. Every case decodes `.malformed`, and the existing by-class test holds `swift ?? result`.

## Item 5: vectors that kill the surviving rule mutants

New decode feeds (all `ENCODER`, `WatchFeedJson.encode`'s own text, so in `MODEL_TEXTS`):
- `ishaAtTheNextFajr`: the sample's first two days, with day 0's Isha moved onto day 1's Fajr;
- `aDayOfOneMinute`: the same two days, with day 1 beginning a minute after day 0. When the date has turned, no start has arrived;
- `ishaFirstInADayOfOneMinute`: `aDayOfOneMinute` with day 0's Isha a minute before its Fajr, so the fallback's Isha is the next start too, a period of no length;
- `startsAtTheEndOfTime` (added after review round 1): the same two days with every time at `Long.MAX_VALUE - 1000`, day 0 beginning at `Long.MIN_VALUE + 13600` and day 1 a day later. Once day 1 has begun, the previous is yesterday's Isha and the next is today's Fajr, at one instant more than half of a `Long` ahead: a period of no length whose time since its start wraps to a positive number;
- `emptySpokenHours` and `emptySpokenMinutes`: oneDay with an empty list.

New sections:
- `moments`: 8 cases in `watch-rules-vectors.json`'s rule shape. The `progress` `Float` is widened to a `Double`, so Swift holds it exactly, not within a tolerance.
- `starts`: `WatchRules.starts` whole, on `ishaAtTheNextFajr` and `tromsoMay`.
- `filled`: 13 cases. Among them are `%0$s`, a place beyond the parts, `%2147483648$s`, `%01$s`, `%%1$s`, a combining mark after `s`, a part holding a place, and no parts.
- `inDigits`: 5 cases. One starts from U+FFFA, where the 6 wraps to U+0000. No zero within nine of a surrogate is used: Kotlin would write a lone surrogate, which no JSON reader reads.

`spoken` gains 20 cases on the two empty-list feeds, all null. The Kotlin checks that hold the file to intent are `theMomentCasesReachTheTieAndTheFallback`, `theStartsCasesDropAStartAtTheNextFajr`, `theSpokenCasesOnAnEmptyListAreNull`, `theFilledCasesKeepEveryPlaceWithNoPart` and `theInDigitsCasesWrapInSixteenBits`. Three generator mutants show they bite:
- leaving `ui.twelveHour` out fails 3 checks;
- making the one-minute day a whole day fails the moments check;
- beginning `startsAtTheEndOfTime`'s days at an ordinary time, so that nothing wraps, fails the moments check (after review).

The Swift tests are `testEveryMomentMatchesTheVectors`, `testEveryStartsListMatchesTheVectors`, `testEveryFilledPatternMatchesTheVectors` and `testEveryInDigitsCaseMatchesTheVectors`.

**Mutants, each alone, in a scratch copy** (`git archive`, then one line changed; the verdict is read from `swift test`'s "All tests" line). "Before" is the same mutant on base `9b6009c0`'s sources, tests and vectors:

| Mutant | Before | Now |
|---|---|---|
| R12: yesterday's starts at or before today's Fajr | survives | killed (moments) |
| R16: a start at the next day's Fajr kept in `starts` | survives | killed (starts) |
| spokenCountdown's empty-list guard gone; its hours half; its minutes half | survive | killed (20, 10, 10) |
| moment's yesterday-Isha fallback gone | survives | killed (15) |
| the progress guard gone, the clamp kept (G2) | survives | killed (3) |
| the progress guard read `total < 0`, the clamp kept (G1) | survives | killed (1, `startsAtTheEndOfTime`; it survived `5e980ff8`'s vectors too) |
| filled's bound `n >= 0` (traps on `%0$s`); `<= parts.count + 1` (traps) | survive | killed (the test traps) |
| filled drops a place with no part | survives | killed (3) |
| inDigits without the 16-bit wrap | survives | killed (1) |
| a lenient read of digitZero, twelveHour, fajrEnd, midnight, target, upTo, fajr, isha (8 mutants) | all survive | all killed (2 to 4 each) |
| the guard read `total < 0` with the clamp gone, its lower end gone, or its upper end gone (three pairs) | survive | killed (2, 2 and 1: the two no-length cases, and `startsAtTheEndOfTime` alone for the third, which survived `5e980ff8`) |
| `decodeWhole` without its `Decimal` check | killed (36) | killed (36) |

**Three survive, and they are equivalent:** the clamp gone (C1), its lower end gone (C2) and its upper end gone (C3). Ruling 57 item 5 names "the progress clamp". With the guard in place, no feed can kill it, whole or either end. Both differences are taken in `Int64`, which wraps as Kotlin's `Long` does (`&-`). So "exact" below means the difference before it wraps, and it wraps by 2^64 when it falls outside the `Int64` range:
- **Where a start has arrived,** previous ≤ now < next. So 0 ≤ elapsed < total, exactly. If total is 2^63 or more, it wraps negative, and the guard answers 0. Otherwise neither wraps, and `Float` rounding is monotone, so `Float(elapsed) / Float(total)` is in [0, 1]. The clamp changes nothing.
- **In the fallback,** the previous is yesterday's Isha, and it is still ahead. The next is the earliest start ahead, and that is no later than that Isha: either the Isha is one of yesterday's starts before today's Fajr, or today's Fajr comes no later than it. So now < next ≤ previous, and the exact total is ≤ 0. If it is −2^63 or more, total ≤ 0, and the guard answers 0. Otherwise it wraps to a positive total below 2^63. The exact elapsed is below the exact total, so it wraps too, into (0, total), and the ratio is in [0, 1] again. That is the wrap review round 1 found the first version of this bullet missed ("so total ≤ 0" was false there): Kotlin's own progress can be anything in [0, 1] in that case. The clamp still changes nothing.

**The guard read `total < 0` (G1) is not equivalent,** as review round 1 showed, and this report first said it was. It differs only where total = 0. That is reachable only in the fallback, as a period of no length, where the exact elapsed is negative. Unwrapped, as on `ishaFirstInADayOfOneMinute`, the ratio is −∞ (the time since that Isha over 0; the first version of this report called it 0 / 0, which it is not), and the clamp makes it 0, as the port does. But when yesterday's Isha is more than 2^63 seconds ahead, the elapsed wraps to a positive number, the ratio is +∞, and the clamp makes it 1, where Kotlin and the port give 0. `startsAtTheEndOfTime` is that feed, and an ordinary `ENCODER` one, since `WatchFeedJson.decode` checks no time's range. G1 now dies on it: 1 failure, `("Optional(1.0)") is not equal to ("Optional(0.0)") - progress: day 1, ten seconds in, …`. It survived `5e980ff8`'s 19 tests.

So the guard covers the clamp, and the clamp does not cover the guard's equality. The three pairs (G1 with the clamp gone, or one end of it gone) die too. On `startsAtTheEndOfTime` each answers +∞ or 1. On `ishaFirstInADayOfOneMinute` the two with no lower end answer −∞. A new fuzz checks the port's formula against each mutant (`scratchpad/r57fix/probe/progress.swift`). It covers 29.6 million reachable (previous, now, next) triples, extremes and wrapping included: 9.9 million no-length fallbacks, drawn on purpose, 1.35 million of which wrap. G1 differs on 1,376,869 of them. C1, C2 and C3 never differ. The first fuzz (`scratchpad/r57/probe/clamp.swift`) tried only the clamp removed, and rarely drew a period of no length.

## Item 6: label's locale

Kotlin's label is `uppercase(Locale.forLanguageTag(ui.language))` on the JVM. P4.2's Swift handed the whole tag to Foundation. Measured on JDK 21 and macOS 27:

| Tag | Kotlin (JVM) | P4.2's Swift | Now |
|---|---|---|---|
| `tr-u-ca-gregory` | İKİNDİ | IKINDI | İKİNDİ |
| `tr_TR` (ill-formed: no language to Java) | IKINDI | İKİNDİ (ICU reads it) | IKINDI |
| `AZ` | İKİNDİ | İKİNDİ | İKİNDİ |
| `lt`, Asr `i` + U+0307 + `kindi` | IKINDI | IKINDI | IKINDI |
| `el`, Asr `άσρ` | ΆΣΡ | ΑΣΡ (ICU drops the tonos) | ΆΣΡ |

The ruling says to use the primary language subtag, as Kotlin does. Foundation handed the primary subtag alone (`Locale(identifier: "el")`) would still differ from Java for `el`, `hy`, `tur`, `aze` and `lit`, which ICU cases its own way and Java does not. That is measured: mutant T2 dies on 3 cases. Java's `toUpperCase` cases only `tr`, `az` and `lt` otherwise than the default. So `casingLocale` takes the primary subtag (before the first `-`, ASCII, lower-cased) and gives `Locale(identifier:)` for those three, and `nil`, the default casing, for any other. That is Kotlin's answer on every tag measured. The vectors hold it with five new oneDay feeds (`turkishIkindiTagged`, `turkishIkindiIllFormed`, `azerbaijaniIkindiInCapitals`, `lithuanianDotAbove`, `greekTonos`), 25 label cases. The phone itself only ever sends one of the seven interface languages' tags (`wordsLocaleTag`), so `tr-u-ca-gregory` is the case that can arise from a phone.

It was test-first: the new label cases failed 5 times on P4.2's port, and pass now. These mutants die: the whole tag (T1, 5 failures), the subtag's own `Locale` (T2, 3), no lower-casing (T3, 2), Lithuanian left out (T4, 1), Azerbaijani left out (T5, 2), and a split at `_` too (T6, 2).

## The task's errata, as resolved

1. `git merge --ff-only watch-companion`: `dfe2ef52` to `9b6009c0`. Base `9b6009c0`.
2. Ruling 57 items 1, 2, 3, 5 and 6 only. Item 4 is untouched. Test-first throughout:
   - Kotlin: 10 checks red (each a missing case or section), then the generator, then green;
   - Swift: 5 label failures red, then the fix, then green; item 3 by the refusing scratch port.

   The vectors are generated. `swift test` is green. Every mutant the items name dies, except the three shown equivalent, the clamp's (after review round 1, which killed G1).
3. Plan 4 errata (`plan-errata-2026-10-24.json`, key p4): none of the ten touches these items. They concern Tasks 1, 3, 6–8 and 11 and VoiceOver. #2's clock keys (`WatchTime`) and #3's `days` premise were already in the vectors, and are unchanged.
4. Rulings 55–57 and the Lead's notes since were read and followed. Ruling 56's classes are kept: no new `FOREIGN` case, and every new case's class is the one its rule gives. Ruling 39 is untouched: no coordinate, location or network code; only test data and the casing helper changed.
5. WIP commits were made as the work went (`44b7b9d7`, `5233fdf6`, `5597e7e6`, `d39c81b1`). They are folded into the task's commit, whose tree is the last WIP's.
6. No simulator, emulator or device was used, and no lock was taken. `KB2604110095` and the work iPhone 12 were not touched. `scripts/test.sh`'s Kotlin/Native iOS tasks were up to date (no input of theirs changed), so they booted nothing.
7. No flake was met. `scripts/test.sh` was green on its first run, with no EOFException.

## Review round 1

The spec, behaviour and evidence lens found one Important: G1 is not equivalent, as item 5 above now says. Its counter-example is a no-length fallback whose elapsed time wraps. Fixed, test-first:
- **Red.** `theMomentCasesReachTheTieAndTheFallback` gained the wrapping case's checks. These are: the previous is day 0's Isha and still ahead, the next is day 1's Fajr at the same instant, `now - previous.at` wraps positive, and the progress is 0. `MODEL_TEXTS` gained `startsAtTheEndOfTime`. Two checks were red, each the missing case: `no decode case startsAtTheEndOfTime`, and the `MODEL_TEXTS` list.
- **Green.** The generator writes the feed and its one moment case, and the `about` says why. `decodeCasesAreWhatTheDecoderSays` now counts 83 cases. The Swift port and tests are unchanged: `testEveryMomentMatchesTheVectors` reads the new case as it reads the others.
- **Mutants:** G1 is killed (1), and so is the third pair, G1 with the clamp's upper end gone. C1–C3 still survive, and the corrected proof and the new fuzz show they are equivalent. One generator mutant was run: days beginning at an ordinary time, so nothing wraps. It fails the new check ("the time since it wraps to a positive number").
- **Also corrected:** the fallback bullet's "so total ≤ 0" (the lens's minor: the exact total can wrap positive, and Kotlin's progress is then a real ratio in [0, 1]). And "0 / 0" for `ishaFirstInADayOfOneMinute`, in this report, the Kotlin KDoc and the vectors' `about`: there the time since the Isha is −16200 s, so the ratio is −∞.
- **Left, minors** (recorded in the ledger):
  - plan 4's Task 1 and Task 2 lines 191, 198, 199 and 241 still name the four quoted cases. They are a closed task's text, and the plan is the Lead's.
  - `greekTonos` pins the JVM's ΆΣΡ, where the Wear watch's ART cases `el` by its own rule and drops the tonos. So item 6's "as Kotlin does" holds on the JVM only. No phone sends `el`.
  - The other lens's minors stand as its ledger line has them.
- Rulings 39, 41, 42 and 43 are untouched: only test data, a test generator and this report changed.
- WIPs `5e3b4bb2` and `e36a8386` are folded into the round's commit. No device, emulator, simulator or lock was used, and `scripts/test.sh`'s Kotlin/Native iOS tasks were up to date, so they booted nothing.

## Verification

- `swift test --package-path iosApp/WatchCore`: 19 tests, 0 failures, on a clean build with no warning. Also green under `TZ=Pacific/Auckland` with `LC_ALL=tr_TR.UTF-8`, and in `-c release` under `America/Los_Angeles` with `ar_SA`. Sections read: decode 83, spoken 70, labels 75, days 33, moments 8, starts 2, filled 13, inDigits 5, rules 57, countdowns 70, refreshes 8, goldens 5, samples 4.
- The sources type-check for `arm64_32-apple-watchos10.0`, `arm64-apple-watchos10.0` and the watchOS 10 simulator, with no warning. Not re-run after review round 1, which changed no Swift file.
- `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`, then without the flag: 137 tests, 0 failures. Run again, the file is unchanged. `feed-vectors.json` is 599,873 B (it was 483,838 B, and 594,380 B before review round 1). It is test data only: since `fa62c182`, no APK carries it.
- `scripts/test.sh`, no arguments: exit 0.
  - 1.3.0 (39)/(100039), bump PASS;
  - swift test first, 19/0;
  - shared host 2305 / iOS 2258, widgetcore 111/109, watchcore 137/117, wearApp 193, 0 failures, 0 skipped.
- `git diff --check` is clean. `git merge-tree` is clean with `watch-companion` `9b6009c0`, `watch-wear` `da07dfd7` and `watch-feed` `9568e251` (after review round 1; before it, `b96e4545` and `05d397c6`).
- The commit has the Taqwa identity and no trailer. Nothing is pushed.

## For the Lead

- **The progress clamp is an equivalent mutant** (item 5, above), whole or either end: with the guard in place, the ratio is in [0, 1] wherever it is computed, wrapping included, so no feed can kill it. **The guard's equality is not** (review round 1). Read `total < 0`, it gives 1 where Kotlin gives 0, on a period of no length whose start is more than 2^63 seconds ahead. `startsAtTheEndOfTime` kills it, and the fallback case kills the guard's removal. Every other mutant item 5 names dies.
- **Item 6 goes one step past the ruling's words.** Taking the primary subtag only would still case `el`, `hy`, `tur`, `aze` and `lit` the ICU way, where Kotlin does not. `casingLocale` follows Java's own three languages instead. If you would rather have the plainer `Locale(identifier: primary)`, it differs from Kotlin on three of the new label cases (mutant T2): `tr_TR`'s Asr and Maghrib, which ICU reads as Turkish, and Greek's tonos. No phone sends either tag.
- **P4.3 (item 4)** should move plan 3's four fields out of `PLAN_3_LENIENT` when the port reads them. The generator then writes their lenient cases (two quoted `upTo`s, a bare `ringDigitZero`, a quoted `latinScript`). Swift's `testTheKnownDifferencesAreRefusals` list grows by those names.
- **A vacuous pass is possible by design.** On a Foundation that refuses every point and exponent form, `testEveryIntegerIsReadExactlyOrNotAtAll` holds nothing, and it prints `0 of 105`. Ruling 57 item 3 asks for exactly that.
