# Plan 4 Task 1: the vectors the Swift port reads beyond plan 1's

Lane P, in `watch-apple` (plan 4's own worktree), 25 October 2026. Base `a35a4736` ("Merge watch-feed: R54"), so plan 2's Task 3 (`WatchLinkContract`, the phone goldens) and Ruling 50's receivers' rules are both in.

**In one line:** `watchcore/vectors/feed-vectors.json` now holds what the watchOS port reads beyond `watch-rules-vectors.json`. That is the decoder's answers on 56 texts (21 at first, 18 added in review round 1, 3 in review round 2, 12 in review round 3 and 2 in review round 4, below), the spoken countdowns, the complication labels, the turn of the date with and without midnights, the clock strings for all six times, and the link's contract with Ruling 50's receivers' rules. Every answer is worked out by Kotlin from the same text the port reads, and `SwiftPortVectorsTest` keeps the file current. Since Ruling 56 every case carries its class, `ENCODER`, `ONE_CHANGE` or `FOREIGN`, and the port is held to Kotlin exactly only on the first two, where a phone can write the text ("Ruling 56", below).

## What was built

| Section | Cases | From | Notes |
|---|---|---|---|
| `decode` | 56 | `WatchFeedJson.decode` / `versionOf`, as `FeedStore` reads the two answers | Each case is `{name, class, result, swift?, expect?, bom?, json \| file}`, `class` since Ruling 56. The four samples are `file` cases, naming `sample-feed-capetown*.json` in the same directory. Every inline text is the English sample with one change, apart from `notJson`, the feeds the later sections run on, `newerShape`, and the cases on `oneDay` (the sample cut to its first day), each `oneDay` with one change. `bom: true` puts EF BB BF before the text (review round 1). The three `laterKeys` cases are `oneDay` with a later phone's keys, named like the integer fields (review round 2). Review round 3 adds three trailing commas, two keys spelled with an escape and seven keys written twice, texts no `JsonObject` can hold, so they are written on `oneDay`'s own text. Review round 4 adds two more keys written twice, a whole number with a fraction in the copy not kept. |
| `spoken` | 50 | `WatchRules.spokenCountdown` | Day 0's Asr, at 0, 59, 60, 119, 3600, 4319, 4320, 7199, 36000 and 359999 s, on the four samples and `olderPhone` (all null). `clock` is `spokenClock(day0, WatchTime.ASR)`, so en12 and ar-ly12 carry the FSI…PDI form. |
| `labels` | 50 | `day.names.of(WatchTime.of(prayer)).uppercase(Locale.forLanguageTag(ui.language))`, as `ComplicationFields.nextPrayer` writes it | Covers the four samples on days 0 and 6, all five prayers. Day 6 is Friday 23 October: `JUMUʿAH` and `الجمعة`. The `turkishIkindi` copy reads `İKİNDİ` (and `MAGHRİB`), and `englishIkindi` reads `IKINDI`. |
| `days` | 33 | `WatchRules.today`, `WatchRules.dayStart` | Each named day is tested a second before its start, at its start and a second after, plus the last day's last second and its end. `sample` covers days 0, 1 and 30, at its midnights. `noMidnights` covers days 0, 1 and 30 at solar midnight (every midnight 0, day 1 ten minutes late). `tromsoMay` covers days 0, 1 and 2. |
| `clock` | 48 | `WatchRules.clockText`, `spokenClock` | Covers the four samples on days 0 and 6, all six `WatchTime`s. Sunrise is included: `6:01 AM` drawn (a no-break space before the marker) and the same between U+2068 and U+2069 spoken in en12. A 24-hour time is the same both ways, in any digit set. |
| `contract` | keys, bytes, 6 + 19 + 10 + 3 + steps | `WatchLinkContract` | The four context/message keys and four byte figures come under their Kotlin constant names. Then `isFingerprint` on 6 texts, and `deliveryOf` on 19 texts (3 valid, up to `Long.MAX_VALUE`, plus every refused form of `WatchLinkContractTest.anythingElseIsNoDelivery`). Then `stores` on 10 pairs (`held` is absent when nothing is held) and `echoOf` on 3. Last is `STORE_STEPS`' order. |

Each case of `spoken`, `labels`, `days` and `clock` also carries `class`, its feed's, always `ENCODER` (Ruling 56); the contract's cases carry none.

The file is 484 KB (477 KB before Ruling 56's classes, 354 KB before review round 1, 414 KB before round 2, 427 KB before round 3, 470 KB before round 4). Ten inline texts are the 21 KB sample with one change each, so a reviewer can see that the change is the only difference; three more 21 KB texts are feeds of their own, the labels' and the days' copies (review round 3 corrected the count, which said 13). The 34 texts on `oneDay`, `oneDay` among them, are about 3.5 KB each as the file writes them, so they take 121 KB rather than 710 KB. The generator checks that the sample's parsed form re-writes byte for byte. A Python diff against `sample-feed-capetown.json` agrees, case by case (below), and so does one against `oneDay` for the added cases.

**Tests** (`SwiftPortVectorsTest`, androidHostTest):
- `swiftPortVectorsAreUpToDate`: writes the file under `-Ptaqwa.updateVectors`, and otherwise fails, naming the first differing line, when the file is missing or differs.
- `decodeCasesAreWhatTheDecoderSays`: every case's `result` is declared in the generator and checked against the decoder's own answer, so the file cannot hold a wish. The four quoted cases come out `feed`.
- `swiftDiffersOnlyOnTheFourQuotedCases`: exactly `quotedWrittenAt`, `quotedVersion`, `quotedBearing` and `quotedRtl` carry `swift`, each `result` `feed` and `swift` `malformed`.
- `theFridayLabelIsTheJumuah`: each sample's day 6 is dated 2026-10-23 and its Dhuhr label is the Jumuʿah in capitals, never `DHUHR`. Day 0 (2026-10-17) reads Dhuhr's own name.
- `theDaysCasesSitOnBothSidesOfEachTurnOver` (added, for errata #3): it holds each `days` feed's premise. `sample` and `tromsoMay` have every midnight, and `noMidnights` has none. Each covered day's last index is the feed's last day. The instants fall on both sides of each start and of the end.
- `everyTimeOfTheDayHasAClockCaseSunriseIncluded` (added, for errata #2): every sample and day has the six times in `WatchTime` order. The 12-hour Sunrise and Asr have their day period and isolates. The 24-hour and Arabic-Indic times are bare.
- `aNewerFeedOfAnotherShapeIsNewer` (review round 1): the `newer` cases are `version2`, `newerShape`, `newerDayShape` and (since review round 3) `duplicateVersionNewerLast`, each with a version above `WatchFeedJson.VERSION`. Exactly `newerShape` and `newerDayShape` still fail to decode once their version is set back to 1, so the version is all that makes them newer.
- `everyCaseHasTheClassItsRuleGives` (Ruling 56): every case has a class, and it is the one the file's rule gives from the text alone. The `FOREIGN` cases are a fixed list, `FOREIGN_CASES`. An `ENCODER` case answers feed (or newer above version 1) and has no `swift`, and the 11 `MODEL_TEXTS` are `WatchFeedJson.encode`'s own text. The port's answer on each `ONE_CHANGE` case is `malformed`, and no `FOREIGN` case has `swift`. The other sections' cases carry their feed's class, `ENCODER`, and the contract's none.
- `theFormsOnOneDayAreOneChangeEach` (review round 1, named `theFormsNoPhoneWritesHoldThePortToKotlin` until Ruling 56, since its KDoc now gives each form's class and the port is held to Kotlin only on the `ONE_CHANGE` ones): the forms kotlinx refuses (twelve, and since review round 3 sixteen) are `malformed`, with no `swift` and no `expect`. The `KOTLIN_READS` cases (the two exponent forms, since review round 2 the three `laterKeys` cases, and since round 3 `escapedVersionKey`) are feeds, with no `swift`, whose text differs from `oneDay`'s and which decode to `oneDay`'s own feed, value for value. `byteOrderMark` is the only case with `bom`, and its `json` is `oneDay`'s. Each trailing-comma case has exactly one comma before a closing bracket (the root's last, the phrases' list, the place) and is `oneDay`'s text without it. Each escaped-key case parses to its plain case's tree (`oneDay`, `fractionalWrittenAt`) from another text.
- `aLaterPhonesKeysNamedLikeTheIntegersAreIgnored` (review round 2): `INTEGER_PATHS`, the model's integer fields by path, equals what `WatchFeed`'s serializers declare. Each `laterKeys` case holds keys named like those fields at paths the model does not have: a fraction, a string and a version below the root among them, some before an object's own keys and some after.
- `aKeyWrittenTwiceIsReadByItsLastCopy` (review round 3): reading the text itself, it finds exactly one key written twice in each `DUPLICATE_KEYS` case, at the path named, and none in any other case. Each is `oneDay` with a copy added. Each copy alone (the other renamed to a key no build reads) reads otherwise, by answer or by feed, so the copy Kotlin keeps shows. Since Ruling 56 the duplicates are `FOREIGN`, so this is Kotlin's reading, as information. A feed or a newer version is the last copy's, and a `malformed` one has one copy that alone is malformed and one that is not. Since review round 4 it also holds the two `WHOLE_FRACTION_FIRST` cases: an integer field whose first copy, the one not kept, is the kept copy's own number with `.0`, so that only the grammar refuses it.

## The Lead's call 8, measured

kotlinx 1.11.0 reads all four quoted texts as feeds: `"writtenAt": "1792303200"`, `"version": "1"`, `"qiblaBearing": "23.0"` and `"rtl": "false"`. `decodeCasesAreWhatTheDecoderSays` is green with all four declared `feed`. So no case drops its `swift`, and the list stays the plan's four.

Review round 1 measured the other direction too (below, "Review round 1"). Ruling 56 has since narrowed call 8 to the texts a phone can write: the quoted four are `ONE_CHANGE`, where the port is held exactly to `swift`, and the forms no encoder writes are `FOREIGN` (below).

## Ruling 56: each case's class

Ruling 56 (29 October, after review round 5) narrows the Lead's call 8 to what a phone can write. Only what the Taqwa phone app writes reaches a watch, and the phone writes with kotlinx's encoder. So every case now carries `class`, and the port is held by it:

| Class | What it is | What binds the Swift port |
|---|---|---|
| `ENCODER` | the text kotlinx's encoder writes for a value of a model the plans name: this build's, an older phone's, a later phone's at version 1, or a newer phone's of any shape | exactly `swift ?? result` (no `ENCODER` case has `swift`), and `expect` exactly for a feed |
| `ONE_CHANGE` | an `ENCODER` case's text with one value changed, removed or set to null | exactly `swift ?? result`, which is `malformed` on every one of them (the quoted four by call 8) |
| `FOREIGN` | a text no encoder configuration the app uses can write | no crash and no hang; where it answers `feed`, every value it read is exactly the text's JSON value (no truncation or rounding, the `Int32`/`Int64` ranges checked). `result` and `expect` are Kotlin's answer, kept as information, and not asserted |

**The rule** is in the file's `about`, and `everyCaseHasTheClassItsRuleGives` applies it to each case's text (a file's without the line end it closes with). The generator's declared class must agree with it, as `result` must agree with the decoder.
1. **FOREIGN** when kotlinx's encoder, configured as `WatchFeedJson`'s, writes no such text. That is so when:
   - kotlinx's own reader reads no JSON from the text: a cut text, a trailing comma, a bad escape, a mark before it;
   - or the encoder, writing back the JSON kotlinx reads from it, writes another text: an exponent (`1e0` comes back `1.0`), `1792303200.0` (comes back `1.7923032E9`), an escaped key, a key written twice, and, beyond the file, leading zeros, capital booleans, raw control characters and lone surrogates;
   - or a number at one of `INTEGER_PATHS` has a point or an exponent: `1.0`, `2.0` and `100.0`, which a `Double` field writes but no integer field does, since each is an `Int` or a `Long` in every model the plans name.
2. Otherwise **ENCODER** when the text holds a value of a model the plans name:
   - a version above 1, whatever the rest holds (a newer phone's, any shape);
   - or version 1 with every key the model has holding a value of its field (an `Int` a whole number in 32 bits, a `Long` in 64, a `Char` one UTF-16 unit, null only where the field is nullable) and every required key there. Any other key is a later phone's, and a defaulted key may be absent, as an older phone leaves it.
3. Otherwise **ONE_CHANGE** when the text's tree differs from an `ENCODER` case's at exactly one path.

No case may be left without a class.

**The 56 cases:**
- **ENCODER (18):** `sample`, `sampleEn12`, `sampleArLy12`, `sampleArSa`, `olderPhone`, `newerPhone`, `version2`, `newerShape`, `newerDayShape`, `emptyDays`, `oneDay`, the three `laterKeys` cases, `turkishIkindi`, `englishIkindi`, `noMidnights` and `tromsoMay`.
- **ONE_CHANGE (15):** `version0`, `missingVersion`, `noDays`, `nullInADefaultedField`, `twoCharacterZero`, the four quoted cases, `booleanVersion`, `wideVersion`, `wideTarget`, `combiningDigitZero`, `astralDigitZero` and `lineBreakDigitZero`.
- **FOREIGN (23):** `notJson`, `fractionalVersion`, `fractionalNewerVersion`, `fractionalWrittenAt`, `fractionalTime`, `fractionalTarget`, `byteOrderMark`, the three trailing commas, both escaped keys, both exponents and the nine duplicates. `FOREIGN_CASES` fixes the list, since a new one needs a ruling (item 3).
- Every case of `spoken`, `labels`, `days` and `clock` carries its feed's class, always `ENCODER`. The contract's cases are delivery texts, not feeds. They carry no class, and the port gives exactly Kotlin's answer on each (Ruling 50).

**From the encoder itself.** The generator writes each `ENCODER` text with kotlinx's encoder:
- `WatchFeedJson.encode` for a value of this build's model. `oneDay`, `emptyDays` and `version2` are now `encode` of the sample's feed with its days cut to one, its days emptied, or its version raised;
- for an older or a later phone's value, the same encoder over its JSON tree. kotlinx's `JsonElement` serializer writes each number, string and word as a model's field of that type is written.

The test checks it: the 11 `MODEL_TEXTS` are `WatchFeedJson.encode`'s own text of the feed they hold (the version aside), and the other 7 are the encoder's writing of their tree (rule 1). Every text in the file is byte for byte what it was; only `class` is added.

**What this replaces.** Ruling 56 supersedes Ruling 55 items 3 and 4, and call 8's "it never accepts a case Kotlin refuses" for texts no encoder writes. Ruling 55 item 5 (plan 4 Task 2's Interfaces) is replaced by Ruling 56 item 4:
- the port reads the root's version first, and a newer version answers newer without the body being decoded;
- it then decodes with Foundation's `JSONDecoder` and default strategies, Kotlin's `Int` an `Int32`, its `Long` an `Int64`, and `digitZero` one UTF-16 unit;
- it needs **no JSON scanner of its own**.

So the scanner recipe this report gave Task 2 is removed ("What Task 2's text needs", below). What it measured of kotlinx stays, as information on the `FOREIGN` cases.

### Kotlin on the FOREIGN cases, as information

- **Integer fields** (`AbstractJsonLexer.consumeNumericLiteral`):
  - a point is refused (`1.0`, `1792303200.0`, `100.0`), and an exponent is read when the value is whole (`1e0`, `17923032e2`, `10e-1`);
  - the digits are read into a `Long`, so a mantissa beyond a `Long` is malformed (`10000000000000000000e-19`, `17923032000000000000e-10`: "Numeric value overflow");
  - the exponent is applied in double arithmetic (`Math.pow` on the JVM), which judges whether the value is whole. So `179230320000000e-5` is malformed, since `Math.pow(10, -5)` is `9.999999999999999E-6`.
  - These are review round 5's measures. They correct this report's earlier "a whole value, as in `consumeNumericLiteral`". No case holds the last three forms (item 3). `JSONDecoder` reads them as feeds, which item 2 allows when the values it reads are exact.
- **Keys** are read with their escapes undone: `escapedVersionKey` is a feed, and `escapedKeyFractionalWrittenAt` is malformed, as `fractionalWrittenAt` is.
- **A trailing comma** and **a mark before the text** are refused. Both Foundation readers take them.
- **A key written twice:** kotlinx reads every copy as its field, its grammar checked, and keeps the last copy. Foundation's readers keep the first. So for the version the answer flips both ways, and `duplicateCityNotAString` and the two `WholeFractionFirst` cases are malformed in Kotlin.
- **A key the model lacks** is skipped (`ignoreUnknownKeys`) by `AbstractJsonLexer.skipElement`. It balances the value's brackets, and it reads each string inside with its escapes checked (`consumeKeyString`, `consumeStringLenient`; review round 5).
  - So a bad escape there is malformed: `"bad\q"`, `"bad\x41"` or `"bad\u12G4"`, alone, in a list or in an object.
  - Other text there that is not JSON is a feed: `nope`, `True`, `01`, `'x'`, `{"a":1,}`, `[1,,2]`, `{a:1}` and the rest of round 4's list.
  - That corrects round 4's "checks only that the value's brackets balance". At version 2, `versionOf` parses the whole tree, so the words there come out newer and the bracket forms malformed.
- **Forms kotlinx reads and Foundation refuses** (Ruling 55 item 4's accepted differences, now `FOREIGN`): leading zeros, capital booleans, a raw tab, line break or NUL in a string, a lone surrogate, and `23.`, `.5` or `1e-400` for a `Double`. No case holds them.

## The errata (plan-errata-2026-10-24.json, p4), and how each was met

- **#3, the `days` premise (resolved).** `Fixtures.tromsoMay()` has real midnights and 3 days, so "days 0, 1 and 30" would index past its end and test no fallback. As built:
  - The fallback is `noMidnights`: `Fixtures.withoutMidnights(capeTownSample())`, 31 days, with day 1 moved ten minutes on (`Fixtures.shifted`, applied before the midnights are cleared, as `capetown-no-midnights` has it). Its covered days are 0, 1 and 30. Day 1 therefore begins halfway between two Dhuhrs that differ, at 00:38 rather than 00:33. A port that took "Dhuhr − 12 h" would fail there.
  - Days 0, 1 and 30 are also on the 31-day `sample`, which has midnights.
  - Tromsø keeps its days 0–2, as the high-latitude night.
  - The instants are `dayStart(feed, i) ± 1` rather than "midnight ± 1", which is the same thing where a midnight exists. Each feed also gets its end: a day after the last midnight, or half a day after the last Dhuhr. This covers all three of `turnOver`'s branches: the first day, the middle and the end.
  - `theDaysCasesSitOnBothSidesOfEachTurnOver` holds the premise, so the error cannot come back unseen. Mutants M4 and M5 below are killed.
- **#2, `WatchTime` (resolved where it touches Task 1, and the clock vector built here).**
  - `:watchcore` already keys `WatchSix.of`, `clockText` and `spokenClock` by `WatchTime` at this head. Task 1's writers read through it: the labels by `WatchTime.of(prayer)`, and the spoken lines' `clock` by `spokenClock(day, WatchTime.ASR)`.
  - The six clock vectors, Sunrise's `6:01 AM` included, are written here as the `clock` section. Plan 4 had them in Task 3, keyed by `WatchPrayer?`, with Sunrise as nil and the five prayers only. The Kotlin they record exists now, and nothing in Task 3 changes it.
  - The Swift port's own parts stay with Tasks 2 and 3: `enum WatchTime`, `WatchSix.of(_ time:)`, `clockText`/`spokenClock` keyed by the six times, and Today's rows by `WatchTime`. Task 2 can key its `labels` test by `WatchTime.of(prayer)` from the start.
- **#4, the dependencies (checked).**
  - Task 1's own two are on this branch: plan 2's Task 3 (`WatchLinkContract`, `watchcore/vectors/phone/*.json`) and Ruling 50 (`deliveryOf`, `stores`, `echoOf`, `STORE_STEPS`).
  - The erratum itself is about Task 3 and Task 10, which this task leaves for the Lead. Task 3 also waits on Tasks 1–2. Task 10 also waits on Tasks 4 and 6.
- **#1, #5, #6, #7, #8, #9, #10** concern Tasks 6–9 and 11: VoiceOver's phrase list and language, Reset, the debug intake, UI-test state and the late-reload timeline. None of them touches the vectors.

## Rulings

- **Ruling 50, and the task's note on it.** The `contract` section holds the receivers' rules from `WatchLinkContract` itself:
  - a watch stores only another origin or a greater number, including the same feed under a greater number. Also covered: another origin with the same number and feed, and `Long.MAX_VALUE` after `Long.MAX_VALUE − 1`, so Swift's `Int64` comparison is tested at its top;
  - it echoes `origin/seq/fingerprint` or "";
  - `STORE_STEPS` keeps its order.
  - The delivery travels beside the feed, never inside it, so no `decode` text holds one and the feed vectors are unchanged.
- **Ruling 39:** the vectors carry what the samples already carry: the city and a bearing to the whole degree, never a coordinate. No new place appears, and Tromsø's figures are the existing fixture's.
- **Ruling 52 item 4 and Ruling 53 item 4 (NSLog):** this task has no Swift and writes no log line. They bind Tasks 5 and 5B.
- **Rulings 41, 42 and 43** concern the Taqwa face, which this task does not touch.

## Test-first, and what each test was seen to catch

1. **RED:** the five tests were written first against a missing builder (`TODO`). Four failed with `NotImplementedError`, and the up-to-date test failed on the missing file.
2. **Plan Step 2:** with the builder in, `./gradlew :watchcore:testAndroidHostTest` FAILED on `vectors/feed-vectors.json is missing` alone.
3. **`clock`, test-first:** `everyTimeOfTheDayHasAClockCaseSunriseIncluded` failed on `Key clock is missing`, then passed once the section was written. The up-to-date test then caught the stale file, as it should.
4. **Mutants**, each applied to a copy and the file restored afterwards (scratchpad `p41/run-mutant.sh`), all killed:
   - M1: `quotedRtl` declared `malformed` (a wish). `decodeCasesAreWhatTheDecoderSays` and `swiftDiffersOnlyOnTheFourQuotedCases` fail.
   - M2: a fifth `swift` on `olderPhone`. `swiftDiffersOnlyOnTheFourQuotedCases` fails.
   - M3: labels on day 5, the plan's old Friday. `theFridayLabelIsTheJumuah` fails.
   - M4: `noMidnights` keeps its midnights, the errata's wrong premise. `theDaysCasesSitOnBothSidesOfEachTurnOver` fails.
   - M5: a no-midnights feed ending a day after midnight 0. `theDaysCasesSitOnBothSidesOfEachTurnOver` fails.
   - M6: the clock cases without Sunrise. `everyTimeOfTheDayHasAClockCaseSunriseIncluded` fails.
   - M7: names read by `WatchTime.entries[prayer.ordinal]`, which counts Sunrise. `theFridayLabelIsTheJumuah` fails, reading `SUNRISE`.
5. **Plan Step 3:** first `-Ptaqwa.updateVectors`, then without the flag (`--rerun`). PASS: watchcore host 125 tests, 0 failures (119 at the base plus 6). `git status` showed only the new test file and `feed-vectors.json`, with `VectorsTest`'s files unchanged. The compile shows no warnings.
6. **Outside Kotlin:** a Python diff of each inline text against `sample-feed-capetown.json` (run with `python3 -I`) shows exactly the stated change.
   - `olderPhone`: `tasbeeh`, `ui.spoken` and `ui.digitZero` removed, and `hijriDayMonth`, `gregorianSpoken` and `midnight` removed from all 31 days.
   - `newerPhone`: `future` added at its seven places.
   - The quoted four: the one value quoted.
   - `version2`/`version0`, `noDays`, `nullInADefaultedField` (day 0), `twoCharacterZero` and `emptyDays`: as named.

## Where the build departs from the plan's text

- **The decode schema and the naming.** The four samples are `file` cases, so their text is not copied into the vectors file. A `feed` in any later section names a `decode` case. Seven decode feeds are added for that:
  - `sampleEn12`, `sampleArLy12` and `sampleArSa`;
  - `turkishIkindi` and `englishIkindi`, the labels' copies;
  - `noMidnights` and `tromsoMay`, the days' feeds.
- **`olderPhone`** lacks exactly the six fields the plan names. Task 3 removes plan 3's four (`screens`, `ringDigitZero`, `latinScript`, `clockTime`) as its plan says.
- **The `clock` section** is written here (errata #2), so the file has six sections. Task 3 adds `words` and its new `expect` fields, and only regenerates `clock`.
- **Labels in a section.** The `days`, `deliveries` and `stores` cases carry a `name` that says what they are.
- **The `stores` cases** add another origin with the same number and feed, and the two `Long.MAX_VALUE` cases.
- **The tests.** Two tests were added beyond the plan's four, both tied to errata (#3 and #2). Review round 1 added two more.
- **`oneDay` and `bom` (review round 1).** The 17 texts added in review are built on `oneDay`, the sample cut to its first day, which keeps the file small. A byte-order mark is a field of its own (`bom`), not a character in `json`, for the reason given under "Review round 1".

## For Task 2 (the Swift core)

- `Vectors.swift` reads a decode case's text as `json`, or as the whole text of `file` in `watchcore/vectors/`, with the bytes EF BB BF before it where `bom` is true. The four sample files end in a newline, which `JSONSerialization` and `JSONDecoder` accept.
- The decoder follows Ruling 56 item 4, with no JSON scanner of its own. It reads the root's version first: a version above 1 is newer before the body is decoded, since `newerShape` and `newerDayShape` have another body. It then decodes with Foundation's `JSONDecoder` and default strategies, Kotlin's `Int` an `Int32`, its `Long` an `Int64` and `digitZero` one UTF-16 unit. The Lead's words for Task 2's Interfaces come at the first step of P4.2 (erratum p4 #1).
- Its tests go by `class`. On `ENCODER` and `ONE_CHANGE` cases the answer is `swift ?? result` exactly, and `expect` is compared where that answer is `feed`. On `FOREIGN` cases it must not crash or hang, and where it answers `feed`, every value it read must be the text's own JSON value. `result` and `expect` are not asserted there.
- In `contract`, `seq` is an `Int64` (it reaches 9223372036854775807), and an absent `held` is nil.
- `labels` and `clock` name times as `WatchTime` / `WatchPrayer` case names: `FAJR`, `SUNRISE`, …, `ISHA`.
- Plan 4's erratum #2 asks Task 2's `WatchSix` to read by `WatchTime`, not by `WatchPrayer`.
- A probe on the Mac (Xcode 27.1's `swift`, scratchpad `p41/probe.swift`, read-only) ran Foundation's `name.uppercased(with: Locale(identifier: ui.language))` over all 50 `labels` cases. It agrees with every one, Turkish `İKİNDİ`/`MAGHRİB` and the Arabic tags included. So the plan's `label` port holds as written.

## Review round 1: what was fixed, and what is the Lead's

The two lenses found 0 Critical and 3 Important (two of them on the same call). All three are fixed in the file. The call they touch is kept as written, and the choices it leaves go to the Lead.

### Finding 1: a newer feed of another shape

- **Before:** `version2` was the only newer case, and its body was the v1 sample. Take a port that decodes first, checks the decoded version, and answers malformed on any failure. It got all 21 cases right, yet answered malformed for a real newer feed. So the Apple Watch could never show "Update Taqwa on your watch" (spec 1.1, plan 3's call 5).
- **Now:**
  - `newerShape` is `{"version":2,"layout":"something else"}`, `WatchFeedJsonTest`'s own text, and it is `newer`.
  - `newerDayShape` is `oneDay` at version 2, its day's `times` a list of the six instants, also `newer`.
  - `missingVersion` is `oneDay` without `version`, and it is `malformed`.
  - `aNewerFeedOfAnotherShapeIsNewer` holds them.

### Findings 2 and 3: the Lead's call 8, measured both ways

Each text is `oneDay` with one change. Kotlin's answer is read as `FeedStore` reads it, and `decodeCasesAreWhatTheDecoderSays` holds each one. The Swift columns come from a throwaway probe on the Mac (Xcode 27.1, Swift 6.4, in the session scratchpad, not committed). It uses a partial model with Kotlin's defaults and null rules. The three columns:
- **A** decodes first and checks the decoded version: finding 1's port.
- **B** is plan 4 Task 2 as written. The version is read first by `JSONSerialization`, and only a non-float, non-boolean `NSNumber` counts. Then `JSONDecoder` runs with its default strategies, `Int` for Kotlin's `Int` and `digitZero` a `Character`.
- **C** is a strict reference (below). As round 1 wrote it, C checked a number by its key's name at any depth; review round 2 found that this refuses a later phone's feeds, and C now checks only at the model's own paths ("Review round 2").

| Case | The change | Kotlin | A | B | C |
|---|---|---|---|---|---|
| `newerShape` | another document at version 2 | newer | **malformed** | newer | newer |
| `newerDayShape` | version 2, `times` a list | newer | **malformed** | newer | newer |
| `missingVersion` | no `version` | malformed | malformed | malformed | malformed |
| `fractionalVersion` | `"version": 1.0` | malformed | **feed** | malformed | malformed |
| `fractionalNewerVersion` | `"version": 2.0` | malformed | **newer** | malformed | malformed |
| `booleanVersion` | `"version": true` | malformed | malformed | malformed | malformed |
| `wideVersion` | `"version": 4294967297` | malformed | **newer** | **newer** | malformed |
| `fractionalWrittenAt` | `"writtenAt": 1792303200.0` | malformed | **feed** | **feed** | malformed |
| `fractionalTime` | day 0's `asr` with `.0` | malformed | **feed** | **feed** | malformed |
| `fractionalTarget` | `"target": 100.0` | malformed | **feed** | **feed** | malformed |
| `wideTarget` | `"target": 3000000000` | malformed | **feed** | **feed** | malformed |
| `byteOrderMark` | EF BB BF before the text | malformed | **feed** | **feed** | malformed |
| `combiningDigitZero` | `digitZero` 0 + U+0301 | malformed | **feed** | **feed** | malformed |
| `astralDigitZero` | `digitZero` U+1D7CE | malformed | **feed** | **feed** | malformed |
| `lineBreakDigitZero` | `digitZero` CR LF | malformed | **feed** | **feed** | malformed |
| `exponentVersion` | `"version": 1e0` | feed | feed | **malformed** | feed |
| `exponentWrittenAt` | `"writtenAt": 17923032e2` | feed | feed | feed | feed |

**Over round 1's 39 cases:**
- A misses 13.
- B misses 10: the nine forms Foundation takes, and `exponentVersion`.
- C, as round 1 wrote it, misses none of the 39. But it misses all three cases review round 2 added, since it checks by key name.
- C by the model's paths misses none of the 42.

So the file catches both ports. A Swift port can meet all of it if its integer checks follow the model's paths, never a key's name.

**What B rests on, as measured:**
- `JSONSerialization` gives `1e0` and `1.0` alike, as a double `NSNumber` (objCType `d`, `CFNumberIsFloatType` true, `as? Int` 1).
- `4294967297` is a `q`: `as? Int` reads it on the Mac and `as? Int32` refuses it.
- `true` is a `c`, which `as? Int` reads as 1.

**The ruling kept.** Both findings ask the Lead to rule, and a lane cannot. So the file follows call 8 as written:
- what it says: Swift "never accepts a vector case that Kotlin refuses", and `swift` is on "exactly four cases";
- so every new case carries Kotlin's answer and no `swift`, and `QUOTED` and `swiftDiffersOnlyOnTheFourQuotedCases` are unchanged;
- for the twelve forms kotlinx refuses, that is the call's own guarantee, now tested;
- for the two exponent forms, it means the port reads an integer written with an exponent as kotlinx does. That is no new difference, but plan 4's Task 2 text cannot meet it (B above).

If the Lead rules instead that the port refuses exponents (a fifth and a sixth difference), the change is three lines and a regeneration:
- `swift = "malformed"` on the two cases;
- the two names added to `QUOTED`, which `swiftDiffersOnlyOnTheFourQuotedCases` then checks (its name changes too);
- `KOTLIN_READS`'s no-`swift` check dropped in `theFormsOnOneDayAreOneChangeEach`.

Ruling 55 item 5 has since ruled the other way: the exponent forms are as this report measures them, so the port reads them, and those three lines are not written. Ruling 56 has since replaced item 5: both exponent cases are `FOREIGN`, so the port may answer either way, provided any values it reads are exact.

**Why `bom` is a field.** Foundation's `JSONSerialization` drops a U+FEFF that opens a JSON string value, whether it is written raw or escaped, while `JSONDecoder` keeps it (measured by a second throwaway probe). A `Vectors.swift` that read the file through `JSONSerialization` would hand the port `oneDay` without its mark. So the case keeps the mark out of `json` and says `bom: true`. A reader that ignores the field gets `oneDay`, a feed, so it fails the case and never passes it by chance.

### What Task 2's text needs (removed under Ruling 56)

This section gave Task 2 a recipe: a JSON scanner of the port's own around `JSONDecoder` (port C, about 200 lines), checking the grammar, the keys' escapes, the repeated keys and the integer forms at the model's paths. Ruling 56 item 4 says the port needs no scanner, so the recipe is removed. Task 2's decoder reads the version first and then runs `JSONDecoder` with strict types ("For Task 2", above). What the recipe rested on of kotlinx is information on the `FOREIGN` cases ("Ruling 56", above). The rounds below say what each round's recipe and port C were, as history; port C is no longer the target.

Two of its measures still bind Task 2, both on `ONE_CHANGE` cases:
- **Kotlin's `Int` is `Int32`** (`version`, `target`, `upTo`). A 64-bit `Int` would take `wideVersion` and `wideTarget`, which are `malformed`.
- **`digitZero` and `ringDigitZero` are one UTF-16 unit** (`utf16.count == 1`), not one `Character`. The three digitZero cases of two units are `malformed`.

### Accepted differences (Ruling 55 item 4, superseded: now `FOREIGN`)

**Superseded by Ruling 56:** these forms are `FOREIGN`, so what follows is information ("Ruling 56", above). It is kept as round 4 wrote it.

kotlinx reads more text that no phone writes, and the Foundation readers refuse it (`JSONDecoder` all of it; Kotlin's side from a throwaway probe test in a scratch copy of the tree). Ruling 55 item 4 names these the **accepted differences**: kotlinx's encoder never writes them, and a port that refuses them can only turn a text no phone sends into `malformed`, which keeps the old feed. Call 8 forbids accepting what Kotlin refuses, and these go the other way. So the port refuses them, and no case is added for them:
- leading zeros: `"version": 01` and `"writtenAt": 01792303200`;
- booleans in capitals: `False` and `FALSE`;
- a raw tab, line break or NUL inside a string;
- a lone-surrogate escape;
- for a `Double` (`qiblaBearing`): `23.` and `.5`, which kotlinx reads as 23 and 0.5, and `1e-400`, which kotlinx reads as 0. `JSONDecoder` refuses that one, though `JSONSerialization` takes the document.

kotlinx also reads `10e-1` as 1 and `2e0` as 2 (newer), and C matches both. Those are no difference: the port reads them as kotlinx does (Ruling 55 item 5, "the exponent forms are as the report measures them").

Item 4's list is of forms at the model's own keys. Review round 4 found one more class, under a key the model lacks, which the list does not name: it is under "Review round 4", for the Lead.

Round 1 also wrote here that a key written twice "differs in the value read, not in the answer". **That was wrong** (review round 3). kotlinx keeps the last copy and Foundation the first, so for `version` the answer flips both ways: `oneDay` with `"version":2` added after its own is newer in Kotlin and a feed through `JSONDecoder`, and with it added before is a feed in Kotlin and newer in Foundation. Round 1 also missed the trailing comma, which Foundation reads and kotlinx refuses. Both are in the file now, with Kotlin's answers ("Review round 3").

### Mutants (review round 1), all killed

Each mutant ran in a scratch copy of the tree, with the vectors file as committed:
- M8: `newerShape` and `newerDayShape` removed. `aNewerFeedOfAnotherShapeIsNewer` and `decodeCasesAreWhatTheDecoderSays` (the count) fail.
- M9: `newerDayShape` keeps its `times` an object, a v1 body under version 2. `aNewerFeedOfAnotherShapeIsNewer` fails.
- M10: `fractionalWrittenAt` declared `feed`, a wish. `decodeCasesAreWhatTheDecoderSays` and `theFormsOnOneDayAreOneChangeEach` fail.
- M11: a quiet fifth difference, `swift` malformed on `exponentVersion`. `swiftDiffersOnlyOnTheFourQuotedCases` and `theFormsOnOneDayAreOneChangeEach` fail.
- M12: the mark written inside `json` instead of as `bom`. `theFormsOnOneDayAreOneChangeEach` fails.
- M13: `astralDigitZero` dropped. `theFormsOnOneDayAreOneChangeEach` and the count fail.
- M14: `exponentWrittenAt` written with one zero too few, so it reads another instant. `theFormsOnOneDayAreOneChangeEach` fails.
- M15: `wideTarget` within 32 bits (2147483647). `decodeCasesAreWhatTheDecoderSays` and `theFormsOnOneDayAreOneChangeEach` fail.

`swiftPortVectorsAreUpToDate` also fails on every one of them.

### Minors left (recorded in the ledger)

- **Null in a defaulted field.** It is pinned on `hijriDayMonth` alone. kotlinx refuses null in every non-null defaulted field (`midnight`, `gregorianSpoken`, `digitZero`, `tasbeeh`, `screens`, `latinScript`), and Swift's `decodeIfPresent` reads a null as absent. Task 2's tests should cover each field.
- **Two mutants survive regeneration:** labels upper-cased by `Locale.ROOT`, and the spoken lines' bare clock. A regenerated file agrees with itself, so only the Swift port, or a reviewer reading the diff, would see either.
- **The Wear debug APK.** It carries `feed-vectors.json` (now 414 KB) from its asset directory, while `wearApp/build.gradle.kts:58` says only the rules' vectors come along. `wearApp/` is outside plan 4's files, so this is for the Lead or Lane W. (Since ruled: Ruling 55 item 6 has it fixed in P4.2 or the next Wear task.)
- **The `file` cases' text.** Kotlin's tests read it from `Fixtures`, not from the disk files. `VectorsTest` holds the disk files to the same fixtures.
- **Hard-to-read lines.** The 21 KB one-line cases on the full sample are hard to read in a diff. The new cases are on `oneDay`.
- **Plan 4's Task 3 text** still lists the clock vectors as its own work (errata #2 and #4). That is the Lead's plan document.
- **`spoken`** has no negative case and none above 99 hours.

### Rulings

- Rulings 39, 41, 42 and 43 are unchanged.
- The new texts carry only the sample's city and its whole-degree bearing, never a coordinate, so the promise "Only your own devices can see your location." still holds.
- Nothing here touches the face.

## Review round 2: a later phone's keys named like the integer fields

The code lens found 0 Critical and 1 Important, and the spec lens was clean (5 minors). The Important is fixed in the file and in this report.

### The finding

- **Before:** round 1's cases make the port read raw number tokens, since `JSONDecoder` reads `1.0` into an `Int`. The reference C that this report gave Task 2 checked a number by its key's name at any depth, `version` alone pinned to the root.
  - Under `WatchFeedJson.VERSION`'s own KDoc, a field added with a default does not raise the version, and every build ignores a key it does not know (plan 4's Global Constraints).
  - So a later phone at version 1 may add a key named like an integer field somewhere else, a per-prayer `Double` under a day, say. The Galaxy reads that feed, and C refused it.
  - No case showed it. A Task 2 built from the report would pass all 39 cases and keep its last feed with no "Update" notice (Review Focus 1).
- **Now:** three `feed` cases on `oneDay`, each without `swift`, listed in `KOTLIN_READS` through `LATER_KEYS`. Each decodes to `oneDay`'s own feed, value for value, and its text differs from `oneDay`'s (`theFormsOnOneDayAreOneChangeEach`). Each holds one of the reviewer's three texts:
  - `laterKeysAtTheTop`. Before the root's own keys: `upTo: 0.5` and `earlier: {version: 2, writtenAt: "soon"}`. After them: `target: "all"` and `future: {version: 2.5, writtenAt: 1.5, target: "x", asr: 1.5}`, the reviewer's first text. `earlier.version` comes before the root's `version`, so a reader that takes the first `version` it meets says newer.
  - `laterKeysInADay`. In day 0, `version: 1.5` and `writtenAt: "yesterday"` before its keys, and `angles: {fajr: 18.5, isha: 17.5}` (the reviewer's third) and `target: "x"` after. In its `times`, `midnight: 0.5` before, and `upTo: "x"` and `next: {version: 2}` after.
  - `laterKeysInThePlaceUiAndPhrases`:
    - `place`: `target: 0.5` before (the reviewer's second), and `midnight: "x"` and `later: {version: 2.5}` after;
    - `ui`: `version: 2` before and `writtenAt: "x"` after;
    - `ui.spoken`: `upTo: 1.5` before and `fajr: "x"` after, and in its first `hours` count `target: 1.5` before and `isha: "x"` after;
    - the first `ui.screens.turnRight` count: `target: 2.5` before;
    - the first phrase: `upTo: 0.5` before, and `version: "x"` and `writtenAt: 2.5` after.
- **`aLaterPhonesKeysNamedLikeTheIntegersAreIgnored`** holds them:
  - `INTEGER_PATHS`, 16 paths, equals the `Int` and `Long` fields that `WatchFeed`'s serializers' descriptors declare. A field added to the model without a path fails it.
  - Each case has, at paths the model does not have, a key named like one of those fields: one holding a fraction, one holding a string, a `version` below the root, one before its object's own keys and one after them.
  - The generator's `between` refuses a later key that shares a name with one of its object's own keys, so no case changes a real field.
- **The recipe** ("What Task 2's text needs", above) now follows the model's paths. The scanner records each number with its path from the root, and C checks a number's form only at `INTEGER_PATHS`. (Since removed: under Ruling 56 item 4 the port has no scanner.)
- A Python diff (`python3 -I`, scratchpad) of each case against `oneDay` finds exactly the keys above, in those places, and every other value equal.

### Swift, measured

This is the same throwaway probe as round 1 (Xcode 27.1, Swift 6.4, scratchpad, not committed), with C in two forms. Here are the three new cases:

| Case | Kotlin | A | B | C by name (round 1) | C by the model's paths |
|---|---|---|---|---|---|
| `laterKeysAtTheTop` | feed | feed | feed | **malformed** | feed |
| `laterKeysInADay` | feed | feed | feed | **malformed** | feed |
| `laterKeysInThePlaceUiAndPhrases` | feed | feed | feed | **malformed** | feed |

Over all 42 cases:
- A misses 13 and B misses 10, as before. Neither checks numbers by name, so both read the new three right.
- C by name misses exactly the three new cases.
- C by the model's paths misses none.

### Mutants (review round 2), all killed

Each was applied in the worktree, regenerated with `-Ptaqwa.updateVectors` (so the up-to-date test passes and only the other tests can catch it), and then the files were restored:
- M16: `laterKeysAtTheTop` without its leading `upTo`, so no later key sits before the root's own keys. `aLaterPhonesKeysNamedLikeTheIntegersAreIgnored` fails.
- M17: `tasbeeh[].target` dropped from `INTEGER_PATHS`. `aLaterPhonesKeysNamedLikeTheIntegersAreIgnored` fails, because the descriptors disagree.
- M18: `laterKeysInADay` dropped. `theFormsOnOneDayAreOneChangeEach`, `aLaterPhonesKeysNamedLikeTheIntegersAreIgnored` and the count in `decodeCasesAreWhatTheDecoderSays` fail.
- M19: `exponentVersion` written as a plain `1` (the spec lens's minor 2). `theFormsOnOneDayAreOneChangeEach` fails, because a `KOTLIN_READS` text must differ from `oneDay`'s.

### Minors

Fixed:
- `about` names the `oneDay` cases group by group. It used to say that every case after `oneDay` was `oneDay` with one change, which `turkishIkindi`, `englishIkindi`, `noMidnights` and `tromsoMay` are not.
- `about` says "above 2147483647" for a version or a target, where it said "past 32 bits".
- `KOTLIN_REFUSES`' KDoc gives `true` to `JSONSerialization`, which bridges it to 1. `JSONDecoder` refuses it.
- A `KOTLIN_READS` text must differ from `oneDay`'s (M19).
- The round 1 `test.sh` times, under "Checks".

Left (recorded in the ledger):
- **`expect` has no `writtenAt`,** so a port that misreads `17923032e2` still passes `exponentWrittenAt`. The `expect` fields are the plan's. Task 2's test can compare the decoded `writtenAt` with `oneDay`'s.
- **`KOTLIN_REFUSES`' one-change premise** is held by a test for the BOM case alone. The Python diff checks the rest.
- **The exponent grammar** is pinned by two lowercase forms. kotlinx also reads `1E0`, `1e+0` and `10e-1`, and C matches each.
- **No later key is named `digitZero` or `ringDigitZero`** outside `ui`. The one-unit check sits in the decoder, at the model's path, so a port that moved it into the scanner by name would not be caught.
- **Carried from round 1:**
  - null is pinned on `hijriDayMonth` alone;
  - the `Locale.ROOT` and bare spoken-clock mutants survive regeneration;
  - the Wear debug APK carries `feed-vectors.json`, now 427 KB, against `wearApp/build.gradle.kts:58`'s comment (the Lead or Lane W; since ruled by Ruling 55 item 6);
  - Swift's `String ==` is canonical equivalence, so Task 2 should compare the file's strings by `unicodeScalars` where normalisation could differ;
  - plan 4 Task 2's Interfaces need the Lead's words before Task 2 starts (since given: Ruling 55 item 5).

### Rulings

- Rulings 39, 41, 42 and 43 are unchanged, and nothing in this round contradicts them.
- The later keys hold only made-up numbers and strings: no place, and never a coordinate. The promise "Only your own devices can see your location." still holds.
- Nothing here touches the face.

## Review round 3: a trailing comma, a key written twice, and a key spelled with an escape

Each lens found 0 Critical and 1 Important, and the two overlap. Both are in call 8's forbidden direction, a port that accepts a text Kotlin refuses, and both are fixed in the file and in this report.

### The findings

- **Code lens.** Two forms that Foundation reads and kotlinx 1.11 refuses were in neither the file nor the report, though the report said no such form was left:
  - a trailing comma (the root's `,}`, the phrases' `},]}`, the place's `"southern":true,}`): kotlinx refuses it (`allowTrailingComma` is off), while `JSONDecoder` (`allowsJSON5` false) and `JSONSerialization` read `oneDay`'s feed;
  - a key written twice: kotlinx keeps the last copy and Foundation the first, so for `version` the answer flips both ways.
- **Spec lens.** The same class, with a third text: `oneDay` with `"writtenAt":1.5` added after its own is malformed in Kotlin and a feed through `JSONDecoder` (`writtenAt` 1792303200). The recipe for Task 2 never said which copy of a repeated key counts. The sentence under "Also measured" (since review round 4, "Accepted differences") saying a doubled key "differs in the value read, not in the answer" was wrong (corrected there).
- **The practical risk** is nil from a real phone, since kotlinx's encoder writes neither form. What is at stake is call 8's guarantee that the port never accepts a case Kotlin refuses.

### Now

Twelve cases, each on `oneDay`'s own text, since no `JsonObject` can hold them, and none with `swift`:

| Case | The change to `oneDay` | Kotlin | A port on `JSONDecoder` alone (port A, below) |
|---|---|---|---|
| `trailingCommaAtTheTop` | a comma before the root's last `}` | malformed | feed |
| `trailingCommaInAList` | a comma before the phrases' `]` | malformed | feed |
| `trailingCommaInAnObject` | a comma before the place's `}` | malformed | feed |
| `escapedKeyFractionalWrittenAt` | `fractionalWrittenAt`, its key's `w` written as the escape for U+0077 | malformed | feed |
| `escapedVersionKey` | the version's key, its `v` written as the escape for U+0076 | feed | feed |
| `duplicateVersionNewerLast` | `"version":2` after the root's own keys | newer | feed (version 1) |
| `duplicateVersionNewerFirst` | `"version":2` before them | feed | newer (version 2) |
| `duplicateVersionZeroFirst` | `"version":0` before them | feed | malformed (version 0) |
| `duplicateWrittenAtFractionLast` | `"writtenAt":1.5` after them | malformed | feed |
| `duplicateMidnightInADay` | `"midnight":1` before day 0's own keys | feed, midnight 1792188000 | feed, midnight 1 |
| `duplicatePhrasesLast` | `"tasbeeh"` again after the root's keys, with the first phrase alone | feed, 1 phrase | feed, 2 phrases |
| `duplicateCityNotAString` | `"city":5` before the place's own keys | malformed | malformed |

- **The lists.** `KOTLIN_REFUSES` grows from twelve to sixteen (the three trailing commas and `escapedKeyFractionalWrittenAt`). `KOTLIN_READS` gains `escapedVersionKey`. The seven `DUPLICATE_KEYS` cases are held by the new `aKeyWrittenTwiceIsReadByItsLastCopy` (under "Tests", above).
- **Why each duplicate is there.**
  - The three version cases pin the version to the root's last copy, in each direction. `duplicateVersionZeroFirst` shows that only the kept copy is held to 1..`VERSION`.
  - `duplicateWrittenAtFractionLast` and `duplicateCityNotAString` pin that every copy is read as its field. A port that keeps the last copy alone and drops the rest reads `duplicateCityNotAString` as a feed.
  - For `duplicateMidnightInADay` (a list item's object) and `duplicatePhrasesLast` (a list as the value), both readers answer feed. The answer is the same, but `expect`'s `day0Midnight` and `phrases` show which copy was read. `duplicatePhrasesLast` also shows that kotlinx replaces a list whole, never merging the two.
- **The escaped keys.** These were found while this round was being measured, not by the reviewers. The recipe's scanner matched keys as written, so `escapedKeyFractionalWrittenAt` reached `JSONDecoder` with no integer check and came out a feed. `escapedVersionKey`, a feed, holds the other side: a scanner that cannot find the version under its escaped key answers malformed.
- **The recipe** ("What Task 2's text needs", above) now says that the scanner checks the grammar itself and never leaves it to Foundation, reads keys with their escapes undone, and reads a repeated key as kotlinx does: every copy as its field, the last copy kept, the version's included. It also gives a way to build that. (Since removed: under Ruling 56 item 4 the port has no scanner.)

### Measured both sides

- **Kotlin:** a throwaway probe test in a scratch `git archive` copy of `0293d09c`, over 57 texts, `oneDay` and 56 changes to it.
- **Foundation:** Swift 6.4 (Xcode 27.1) on macOS 27, through both `JSONDecoder` and `JSONSerialization`. The scratchpad holds the probe, which is not committed.
- **Both refuse,** at the model's own keys, so the answers agree and nothing is added:
  - a leading or doubled comma;
  - a block or line comment;
  - text after the root (`x`, `{}`, `}`);
  - a form feed or a no-break space used as white space;
  - `+1`, `1.`, `-` and `0x1` as the version;
  - `NaN`, `Infinity` and `1e400` as the bearing;
  - `1` or `null` as `rtl`;
  - single quotes, an unquoted key or value, and a bad escape;
  - a list at the root;
  - a `place` written twice, its first copy `{}`;
  - a `version` written twice, its first copy `"x"`.

  Under a key the model lacks, kotlinx reads some of these: a leading or doubled comma, single quotes, an unquoted key or value (review round 4, below).
- **Both read,** and the answers agree:
  - a newline after the root;
  - `-0` as 0;
  - `23` for a `Double`;
  - an empty key;
  - an unknown key written twice;
  - a `place` written twice, its first copy whole but for another city (Kotlin's city is the last copy's).
- **kotlinx reads and Foundation refuses:** the accepted differences of Ruling 55 item 4, above; and, under a key the model lacks, the class under "Review round 4", below.

### Swift, measured

This is the round 2 probe with a fourth port added. The ports:
- A, B and C2 (round 2's C by the model's paths) are as before;
- C3 is C2 with the grammar checked by its own parser, keys read with their escapes undone, and the repeated keys read in one pass per copy.

The probe checks each port's answers on all 54 cases, and also the `expect` of each feed against the feed it decoded:
- A misses 21 answers, and B misses 18.
- C2, the port round 2's recipe builds, misses 8 answers: the three trailing commas, both escaped keys and the three duplicated versions. It also misreads 2 feeds, `day0Midnight` in `duplicateMidnightInADay` and `phrases` in `duplicatePhrasesLast`. So a port built from round 2's recipe passes 44 of 54.
- C3 decoding the last copy alone misses `duplicateCityNotAString`.
- C3 misses none of the 54, by answer or by `expect`.

### Mutants (review round 3), all killed

Each was applied in the worktree and regenerated with `-Ptaqwa.updateVectors`, so the up-to-date test passes and only the other tests can catch it. Then the files were restored:
- M20: `duplicateMidnightInADay`'s added copy holds `oneDay`'s own midnight, so its two copies read alike. `aKeyWrittenTwiceIsReadByItsLastCopy` fails.
- M21: `duplicateVersionNewerFirst`'s copy is written after the root's keys and declared `newer`. `aKeyWrittenTwiceIsReadByItsLastCopy` (the answers) and `aNewerFeedOfAnotherShapeIsNewer` fail.
- M22: `trailingCommaInAnObject` loses its comma and is declared `feed`. `theFormsOnOneDayAreOneChangeEach` fails.
- M23: the escaped keys are written plainly. `theFormsOnOneDayAreOneChangeEach` fails.
- M24: `duplicateCityNotAString` is dropped. `decodeCasesAreWhatTheDecoderSays` (the count) and `aKeyWrittenTwiceIsReadByItsLastCopy` fail.
- M25: `trailingCommaAtTheTop` gets a key added as well. `theFormsOnOneDayAreOneChangeEach` fails.
- M26: `newerShape` writes its `layout` twice. `aKeyWrittenTwiceIsReadByItsLastCopy` fails, since no other case may write a key twice.
- M27: `duplicateVersionZeroFirst` is dropped. `decodeCasesAreWhatTheDecoderSays` (the count) and `aKeyWrittenTwiceIsReadByItsLastCopy` fail.

### Minors

Fixed:
- The count of 21 KB texts. It said 13, but ten are the sample with one change, and three are feeds of their own.
- `about` named the decode cases "most of them the English sample with one change". It now names them group by group, `notJson` among them.

Left (recorded in the ledger):
- **`integerPaths` and `objectKeys` skip some kinds.** They skip a map, the items of a `List<Int>` or `List<Long>`, and `SHORT`/`BYTE`. The model has none of these today, so a field of such a kind added later would go unseen.
- **The one-change premise** is now held by a test for the BOM, the trailing commas, the escaped keys and the duplicates. The Python diff checks the rest of `KOTLIN_REFUSES`.
- **The rule that every copy is read as its field** is pinned on a number (`writtenAt`) and a string (`city`). No case pins it on a boolean or a list.
- **Carried:**
  - `expect` has no `writtenAt`;
  - null is pinned on `hijriDayMonth` alone;
  - the `Locale.ROOT` and bare spoken-clock mutants survive regeneration;
  - the Wear debug APK carries `feed-vectors.json`, now 470 KB, against `wearApp/build.gradle.kts:58`: Ruling 55 item 6 has it fixed in P4.2 or the next Wear task, since test vectors stay out of every APK;
  - Swift's `String ==` is canonical equivalence;
  - plan 4 Task 2's Interfaces: Ruling 55 item 5 gives the Lead's words, the grammar, the keys' escapes and the repeated keys among them, as erratum p4 #1, the first step of P4.2.

### Rulings

- **Call 8 is kept as written.** Every new case carries Kotlin's answer and no `swift`, and `QUOTED` and `swiftDiffersOnlyOnTheFourQuotedCases` are unchanged. The reviewers' other road, refusing any repeated key, is closed by Ruling 55 item 3: call 8 stands as written, a key written twice is read by its last copy as kotlinx reads it, repeated keys are not refused, and no duplicate case gets a `swift` line.
- **Rulings 39, 41, 42 and 43 are unchanged.** The new texts hold only `oneDay`'s values and made-up numbers (0, 1, 1.5, 2, 5): no place, and never a coordinate. The promise "Only your own devices can see your location." still holds. Nothing here touches the face.

## Review round 4: Ruling 55 in the report, a copy not kept, and a later phone's key that is not JSON

The two lenses, judged against Ruling 55, found 0 Critical and 4 Important between them (the spec lens 1, the code lens 3), two of them the same finding. No finding contradicts Ruling 55, so none is set aside. Three are fixed, and the fourth is corrected in the report and put to the Lead.

### The findings, and what was done

- **The report was not yet in Ruling 55's words (both lenses).** Items 3 and 4 were met in the data, not in the text. Now:
  - "Also measured" is "Accepted differences (Ruling 55 item 4)". It lists item 4's forms under that name and no longer offers the Lead a `swift` case for each kind, which item 4 rules out;
  - round 3's "Rulings" says that item 3 has decided repeated keys: read by the last copy, never refused, no `swift` line;
  - the carried minors cite item 6 (the Wear APK, in P4.2 or the next Wear task) and item 5 (Task 2's Interfaces, erratum p4 #1, the first step of P4.2). The recipe and round 1's exponent paragraph cite item 5;
  - this part needed no regeneration.
- **Every copy of an integer field held to the grammar (code lens).** The recipe said so, but no case held it. `duplicateWrittenAtFractionLast` puts `1.5` in the copy kept, and `JSONDecoder` refuses `1.5` into an `Int64` by itself. Take a port whose scanner keeps one number token per path, the last winning, and which runs one `JSONDecoder` pass per copy. By the code lens's measure it passed all 54 cases, and it read as feeds two texts Kotlin refuses, which is call 8's forbidden direction (`JSONDecoder`'s half is measured again below). Now there are two more cases on `oneDay`, both `malformed`, with no `swift`:

  | Case | The change to `oneDay` | Kotlin | That port |
  |---|---|---|---|
  | `duplicateVersionWholeFractionFirst` | `"version":1.0` before the root's own keys | malformed | feed |
  | `duplicateWrittenAtWholeFractionFirst` | `"writtenAt":1792303200.0` before them | malformed | feed |

  - `aKeyWrittenTwiceIsReadByItsLastCopy` holds them through `WHOLE_FRACTION_FIRST`. Each sits at one of `INTEGER_PATHS`, and its first copy is the kept copy's text with `.0`, so the two copies are one number to `JSONDecoder`. The kept copy alone is `oneDay`'s feed. Its list of answers gains two `malformed`s.
  - Swift 6.4 on macOS 27 (this round's own probe, scratchpad, not committed) reads `1792303200.0` into an `Int64` as 1792303200. It reads both `{"version":1,"version":1.0}` and `{"version":1.0,"version":1}` into an `Int32` as 1.
  - The file's `about` says it too: a copy not kept is held to its field's grammar.
- **A later phone's key whose value is not JSON (code lens).** kotlinx skips a key the model lacks (`ignoreUnknownKeys`) with `AbstractJsonLexer.skipElement`, which balances the value's brackets and, as review round 5 found, also reads each string in it with its escapes checked. So the report's grammar claims held only at the model's own keys.
  - Measured again this round (kotlinx 1.11, a throwaway probe test, removed before the commit): `oneDay` with a last key `future` is a **feed** in Kotlin when `future` holds any of `nope`, `True`, `01`, `'x'`, `{"a":1,}`, `[1,2,]`, `{"a" 1}`, `{"a":1 "b":2}`, `[1,,2]`, `{a:1}`, `[1 2]`, `{"a":}`, `[,1]` or `{,"a":1}`. It is the same with the key written first, or inside `place`. A string with a bad escape is refused there too.
  - Foundation on the same values, after a root `version` and `writtenAt`: `JSONSerialization` reads only the two trailing commas. `JSONDecoder` reads those two and also `01`, which it never decodes and does not check, and refuses the rest.
  - At version 2 the same texts go through `versionOf`, which parses the whole tree. The four words come out `newer` and every bracket form `malformed`. So Kotlin is not consistent with itself here either: `{"a":1,}` under `future` is a feed at version 1 and malformed at version 2.
  - **Corrected in the report:** the recipe's scanner bullet and its repeated-key bullet, round 3's "Both refuse" list and its "kotlinx reads" line now say "at the model's own keys". The file's `about` says the same, and that no case holds such a value.
  - **Not in the file.** Ruling 55 item 4's list does not name the class, and item 3 asks the port to read what Kotlin reads. So it is the Lead's call (below), and no case is added before it.

### For the Lead, before P4.2's Interfaces (erratum p4 #1)

1. **A later phone's key holding text that is not JSON.** There are two ways:
   - **(a) Add the class to the accepted differences.** The port's scanner checks the whole grammar under every key and refuses these texts. That is call 8's allowed direction, and kotlinx's encoder never writes them, as with item 4. A phone at version 1 whose feed held such a value would leave the watch on its last feed with no notice, and no phone can write one. Erratum p4 #1 keeps "the scanner checks the grammar itself", with the accepted differences "those of item 4, and a value under a key the model lacks that is not JSON". No case changes.
   - **(b) Skip an unknown value by its brackets, as kotlinx does.** The scanner then has two modes. `JSONDecoder`, which refuses most of these texts, can no longer be handed the text as written, so the port must cut each unknown member out first. That is more code on a path no phone writes, and it still would not match Kotlin's `newer` answers (above). The file would gain feed cases holding such values.

   This lane would take (a), but the call is the Lead's.

   **Answered by Ruling 56** (after review round 5): such texts are `FOREIGN`, so neither (a) nor (b) is built. The port has no scanner, and no case is added.
2. **Erratum p4 #1's "a repeated key is read by its last copy"** (the spec lens). Alone, those words let a port pass that decodes the last copy only, which misses `duplicateCityNotAString`. Since this round, the same port, or one that checks only the kept copy's grammar, also misses the two `WholeFractionFirst` cases. Better words: "every copy is read as its field, its grammar checked, and the last copy is kept".

### Mutants (review round 4), both killed

Each was applied in the worktree and regenerated with `-Ptaqwa.updateVectors`, so only the other tests could catch it. Then the files were restored:
- M28: `duplicateWrittenAtWholeFractionFirst`'s first copy is written `1.5`, which `JSONDecoder` refuses on its own. `aKeyWrittenTwiceIsReadByItsLastCopy` fails: the first copy is not the kept copy's number with `.0`.
- M29: `duplicateVersionWholeFractionFirst`'s copy is written after the root's keys, so it becomes the copy kept. `aKeyWrittenTwiceIsReadByItsLastCopy` fails: the text is not `oneDay` with a copy added before.

### Minors left (recorded in the ledger)

- **From the spec lens:** the escaped-key cases are not held to the key they escape. Two mutants survive regeneration, `escapedVersionKey` moved to `writtenAt` and `escapedKeyFractionalWrittenAt` moved to `version`, and the second drops the forbidden-direction case.
- **The code lens's five minors** were not among the findings handed to this fix round. They stay with its report (ledger, 29 October 21:48).
- **Carried:**
  - null is pinned on `hijriDayMonth` alone;
  - `expect` has no `writtenAt`;
  - the rule that every copy is read as its field is pinned on numbers (`writtenAt`, the version) and a string (`city`), not on a boolean or a list;
  - the `Locale.ROOT` and bare spoken-clock mutants survive regeneration;
  - Swift's `String ==` is canonical equivalence;
  - `integerPaths` and `objectKeys` skip a map, the items of a `List<Int>` or `List<Long>`, and `SHORT`/`BYTE`.

### Rulings

- **Ruling 55 items 3 and 4 are followed** (Ruling 56 has since superseded both). Neither new case has `swift`, `QUOTED` and `swiftDiffersOnlyOnTheFourQuotedCases` are unchanged, and the accepted differences get no case. Items 5 and 6 are cited where the report carried them as open.
- **Rulings 39, 41, 42 and 43 are unchanged.** The new texts hold only `oneDay`'s values, two of them with `.0`: no place, and never a coordinate. The promise "Only your own devices can see your location." still holds. Nothing here touches the face.

## Review round 5, and Ruling 56

Both lenses, judged against Ruling 55, found 0 Critical and 1 Important each, of one kind: texts no phone writes that kotlinx refuses and `JSONDecoder` reads.
- **Spec lens:** a bad escape in a string under a key the model lacks. kotlinx's `skipElement` reads a skipped value's strings with their escapes checked, so `oneDay` with a `"future"` holding `"bad\q"` and the like is malformed in Kotlin (15 of 15 texts) and `oneDay`'s feed through `JSONDecoder`. The report and the `about` said brackets alone.
- **Code lens:** exponent forms whose mantissa overflows a `Long`, or whose value kotlinx judges with `Math.pow`. They are malformed in Kotlin and feeds through `JSONDecoder`, and the report's "a whole value, as in `consumeNumericLiteral`" missed both.

**The Lead's Ruling 56.** Rounds 1 to 5 each found a new member of this class, and the class has no end: holding the port to kotlinx's reader on texts no encoder writes means copying kotlinx's lexer down to the platform's `Math.pow`. So Ruling 56 holds the port to Kotlin only where a phone can write the text ("Ruling 56", above). Both findings are `FOREIGN`. They get no case (item 3), and the report corrects what it said of kotlinx, as information.

### What was done, test-first

1. **RED.** `everyCaseHasTheClassItsRuleGives` and the rule's functions (`classOf`, `isForeign`, `holdsAModelsValue`, `holdsAValueOf`, `changedPaths`), with `FOREIGN_CASES` and `MODEL_TEXTS`, were written before any case had a class. `SwiftPortVectorsTest` ran 11 and failed 2: the new test ("sample has no class") and `swiftDiffersOnlyOnTheFourQuotedCases` (no `class` key).
2. **The classes.** The generator declares each case's class, as it declares `result`, and the cases of the other sections take their feed's.
   - The next run found the rule's integer clause too wide. It called `booleanVersion` (`"version": true`) `FOREIGN`, since `true` is no integer's spelling. But `true` is another type, not another spelling, so the clause now reads numbers only, and `booleanVersion` is `ONE_CHANGE`, as declared.
   - After that, only `swiftPortVectorsAreUpToDate` failed, on the stale file.
3. **The file.** After `-Ptaqwa.updateVectors`, every text is byte for byte what it was at `b25b842b`, with `class` added to each decode case and to each case of `spoken`, `labels`, `days` and `clock`. A Python comparison with `class` stripped finds the two files equal, the `about` apart.
4. **The words.** The `about` now gives the rule, the bindings by class and Kotlin on `FOREIGN` as information, with round 5's corrections. The tests' KDoc follows.
   - `theFormsNoPhoneWritesHoldThePortToKotlin` is renamed `theFormsOnOneDayAreOneChangeEach`, since the port is no longer held to Kotlin on all its forms.
   - `aKeyWrittenTwiceIsReadByItsLastCopy` keeps its name, which is now Kotlin's reading, as information.
   - The report's scanner recipe is removed.

### Mutants (Ruling 56), all killed

Each was applied in the worktree and regenerated with `-Ptaqwa.updateVectors`, so only the other tests could catch it. Then the files were restored:
- M30: `fractionalVersion` declared `ONE_CHANGE`. `everyCaseHasTheClassItsRuleGives` fails.
- M31: `wideTarget` declared `FOREIGN`. The same test fails, on the rule and on `FOREIGN_CASES`.
- M32: `laterKeysAtTheTop` declared `ONE_CHANGE`. The same test fails.
- M33: the rule's integer clause dropped, so `1.0`, `2.0` and `100.0` at integer fields are no longer `FOREIGN`. The same test fails.
- M34: `swift` `malformed` on `exponentVersion`, a `FOREIGN` case. `everyCaseHasTheClassItsRuleGives`, `swiftDiffersOnlyOnTheFourQuotedCases` and `theFormsOnOneDayAreOneChangeEach` fail.
- M35: `emptyDays` declared `ONE_CHANGE`. `everyCaseHasTheClassItsRuleGives` fails.
- M36: the `days` cases on `tromsoMay` carry `FOREIGN`. `everyCaseHasTheClassItsRuleGives` fails.

### For the Lead

- Plan 4's Global Constraints ("On every vector case, Swift answers `swift ?? result` … it never accepts a case Kotlin refuses") and Task 2's Interfaces still say what call 8 said. They need Ruling 56 item 4's words at P4.2's first step (erratum p4 #1). That is the Lead's plan document, which this lane does not edit.
- How Swift's test checks item 2's value rule on a `FOREIGN` feed is Task 2's to write. Where a `FOREIGN` text writes a key twice, that key has no one JSON value (RFC 8259 leaves it open), so the nine duplicates can be held to "no crash" alone, unless the Lead names a copy.
- `notJson` (`{`) is `FOREIGN` by the rule, since kotlinx reads no JSON from it. No port can read a feed from it, so its binding comes to "no crash".

### Minors left (recorded in the ledger)

- **`olderPhone`** lacks the six fields plan 4 named, but keeps plan 3's `screens`, `ringDigitZero`, `latinScript` and `clockTime`, so it is not the text of a phone that shipped. It is `ENCODER` by the rule (defaulted keys may be absent), and Task 3 removes plan 3's four, as its plan says.
- **The rule's kinds.** `holdsAValueOf` reads the model's kinds today and fails loudly on any other (an enum, say), so a model change shows there.
- **Round 4's minors on `FOREIGN` cases** (the escaped-key cases not held to the key they escape; every copy read as its field pinned on numbers and a string only) are information now.
- **Carried:** null is pinned on `hijriDayMonth` alone; `expect` has no `writtenAt`; the `Locale.ROOT` and bare spoken-clock mutants survive regeneration; Swift's `String ==` is canonical equivalence; `integerPaths` and `objectKeys` skip a map, the items of a `List<Int>` or `List<Long>`, and `SHORT`/`BYTE`.

### Rulings

- **Ruling 56 items 2 and 3 are met.** Every case has a class, by a rule in the `about` that the test holds. The Swift side and the report follow the classes. Every existing case is kept, and none is added. Item 4 is the Lead's words for Task 2 (above). Item 5 leaves Ruling 55 items 1, 2 and 6 standing, which are watch-feed and Wear work.
- **Rulings 39, 41, 42 and 43 are unchanged.** No text changed, so there is no new place and never a coordinate. Nothing here touches the face.

## Checks

- Ruling 56, on the fix (29 October): RED first ("Review round 5, and Ruling 56", above). Then `-Ptaqwa.updateVectors`, and twice more with `--rerun`: every vector file byte-identical (`feed-vectors.json` 483,838 bytes, sha1 dc288ed7; every text as at `b25b842b`, with `class` added). `./gradlew :watchcore:testAndroidHostTest --rerun`: 130/0 (`SwiftPortVectorsTest` 11/11). Without the file, exactly 1 of 130 fails (`swiftPortVectorsAreUpToDate`). Mutants M30 to M36 killed, and `git status` clean after each was restored. `git diff --check` clean.
- Ruling 56, `scripts/test.sh` (29 October, 22:27 SAST, exit 0): versions 1.2.0 (38) / (100038) agree, and `test-bump-version.sh` passes. `:watchcore:testAndroidHostTest` ran, and everything else was up to date from inputs this change does not touch. Then `scripts/test.sh :watchcore:iosSimulatorArm64Test --rerun :watchcore:testAndroidHostTest --rerun` (22:28, exit 0): watchcore host 130/0 and iOS 117/0.
- Review round 4, on the fix (29 October): RED first. With `DUPLICATE_KEYS`, `WHOLE_FRACTION_FIRST` and the answers written and the cases not yet declared, `SwiftPortVectorsTest` failed in `aKeyWrittenTwiceIsReadByItsLastCopy` alone ("no decode case duplicateVersionWholeFractionFirst"). With the cases declared, only `swiftPortVectorsAreUpToDate` failed, on the stale file. Then `-Ptaqwa.updateVectors` and `--rerun`: 129/0 (`SwiftPortVectorsTest` 10/10). `feed-vectors.json` is 477,448 bytes (sha1 475be84e). A second `-Ptaqwa.updateVectors` leaves every vector file byte-identical, and every other vector file is unchanged from 6b1c1cf9. Without the file, exactly 1 of 129 fails (`swiftPortVectorsAreUpToDate`). A Python diff (`python3 -I`) shows each new case is `oneDay` with one member before its own keys. `git status` was clean after each mutant was restored.
- Review round 4, `scripts/test.sh` (29 October, 21:55 SAST, exit 0): versions 1.2.0 (38) / (100038) agree, and `test-bump-version.sh` passes. `:watchcore:testAndroidHostTest` ran, and everything else was up to date from inputs this fix does not touch. Then `scripts/test.sh :watchcore:iosSimulatorArm64Test --rerun :watchcore:testAndroidHostTest --rerun` (21:56, exit 0): watchcore host 129/0 and iOS 117/0.
- Review round 3, on the fix (29 October): `./gradlew :watchcore:testAndroidHostTest` FAILED first on the stale file alone (`swiftPortVectorsAreUpToDate`, 10 tests in the class, 1 failed). Then `-Ptaqwa.updateVectors` and `--rerun`: 129/0 (`SwiftPortVectorsTest` 10/10). `feed-vectors.json` is 469,979 bytes, and every other vector file is byte-for-byte unchanged (shasums before and after). `git status` was clean after each mutant was restored. A Python diff (`python3 -I`) of each new case against `oneDay` shows only its stated change.
- Review round 3, `scripts/test.sh` (29 October, 21:36 SAST, exit 0): versions 1.2.0 (38) / (100038) agree, and `test-bump-version.sh` passes. `:watchcore:testAndroidHostTest` ran, and everything else was up to date from inputs this fix does not touch. Totals: shared host 2184 / iOS 2116, widgetcore 111/109, watchcore 129/117, wearApp 143, 0 failures.
- Review round 2, on the fix (29 October, resumed from the 25 October WIPs): `./gradlew :watchcore:testAndroidHostTest` 128/0 (127 + 1; `SwiftPortVectorsTest` 9/9). Then `-Ptaqwa.updateVectors` rewrote every vector file byte for byte (the shasums before and after agree; `feed-vectors.json` is 426,557 bytes). `git status` was clean after each mutant was restored.
- Review round 2, `scripts/test.sh` (29 October, 21:04 SAST, exit 0): versions 1.2.0 (38) / (100038) agree, and `test-bump-version.sh` passes. `:watchcore:testAndroidHostTest` ran, and everything else was up to date from inputs this fix does not touch. Totals: shared host 2184 / iOS 2116, widgetcore 111/109, watchcore 128/117, wearApp 143, 0 failures.
- Review round 1, on the fix: `./gradlew :watchcore:testAndroidHostTest` 127/0 (125 + 2), with `-Ptaqwa.updateVectors` and then without the flag (`--rerun`). Before the regeneration, the new declarations passed every test but the up-to-date one, which named the stale file. A Python diff shows each added text is `oneDay` with exactly its one change (`newerDayShape`: the version and the list).
- Review round 1, `scripts/test.sh` (25 October, 14:19 SAST, and again at 14:23 after the last WIP, exit 0 both times): versions 1.2.0 (38) / (100038) agree, and `test-bump-version.sh` passes. `:watchcore:testAndroidHostTest` ran, and everything else was up to date from inputs this fix does not touch. Totals: shared host 2184 / iOS 2116, widgetcore 111/109, watchcore 127/117, wearApp 143, 0 failures.
- At the task's commit `1858f98c`, `./gradlew :watchcore:testAndroidHostTest`: 125/0, with `-Ptaqwa.updateVectors` and then without the flag.
- At `1858f98c`, `scripts/test.sh` (25 October, about 13:40–13:45 SAST, exit 0, first run, no EOFException):
  - versions agree at 1.2.0 (38) / (100038), and `test-bump-version.sh` PASS;
  - shared host 2184, from the cache, which is valid because no `:shared` input changed; shared iOS 2116;
  - widgetcore 111/109;
  - watchcore 125/117 (the host tests ran: 119 at the base + 6; iOS unchanged, since the new test is host only);
  - wearApp 143;
  - 0 failures anywhere.
- No device, emulator, simulator or lock was used. `KB2604110095` and the work iPhone 12 were not touched. Nothing was pushed.
