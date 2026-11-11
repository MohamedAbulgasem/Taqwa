# Ruling 60 items 1 to 3: P4.3's test gaps in the Apple Watch's Swift core

Lane A, in `watch-apple` (plan 4's own worktree), 30 October 2026. Base `30d402de` ("Merge watch-apple: P4.3"), which is `watch-companion`'s head; `git merge --ff-only watch-companion` found it already up to date. This is not a plan task. It is items 1 to 3 of the ledger's Ruling 60, the gaps P4.3's two review lenses left as minors. Test-first, with no behaviour change: no production source changed, in Kotlin or in Swift. Every new vector is the encoder's own text, written by `SwiftPortVectorsTest.kt` with `-Ptaqwa.updateVectors`, never by hand.

**In short:** the clock vectors now say numbers that are no decimal digit, and the blank words hold U+001D, U+001E and U+001F, so the four mutants Ruling 60 names, which survived at the base, now die (items 1a and 1b). The samples' word check uses the port's own blank rule (1c). `newerPhone` carries a key no build reads in every kind of object the feed holds, each of the model's eleven classes once, the countdown phrases and the screens' words among them, and both sides read it as the feed without that key (1d; the countdown phrases since review round 1, which found them missing). Each `words` case carries its presets' ids in Kotlin's order, the built-ins first, for plan 4 Task 8 (1e). The P4.3 report and plan 4 Task 3 take their true count, survivors and apostrophe (item 2). Three commits, and two more after review round 1.

## The commits

| Commit | Items | What |
|---|---|---|
| `2b7c3ade` | 1a, 1b, 1c | `clockForms` day 2, `blankWords`' three controls, the samples' check by `WatchScreenWords.isBlank` |
| `ae008984` | 1d | `newerPhone`'s key in ten of the model's eleven classes, held on both sides (its message says every kind of object: the countdown phrases had none) |
| `765c82e8` | 1e, 2 | `presetIds` in the words vectors; the P4.3 report, plan 4 and this report |
| `5dc5fdcb` | 1d, after review | `ui.countdown`'s key, the eleventh class; Kotlin holds the places to the model's classes |
| the commit after `5dc5fdcb` | 1d, 2, after review | plan 4's two lines and this report take the eleven |

Each message is "Watch core: … (Ruling 60)", and the fix round's "Watch core: …, after review", in the Taqwa identity, with no trailer. Nothing is pushed.

## Item 1a: numbers that are no decimal digit

`WatchRules.spokenClock` isolates a time unless every UTF-16 unit is a colon or a decimal digit. Kotlin's test is `Char.isDigit`, Unicode's Nd alone. The clock vectors held only Nd digits, so a port that took any number for a digit survived. `clockForms` gains day 2 (the sample's third day), whose clock strings are `NUMERALS`, one UTF-16 unit each, Unicode No or Nl:

| Time | Clock string | Units |
|---|---|---|
| Fajr | ⁰⁴:³³ | superscripts U+2070, U+2074, U+00B3 |
| Sunrise | ⁰⁶:⁰¹ | superscripts U+2070, U+2076, U+00B9 |
| Dhuhr | ①②:③③ | circled digits U+2460 to U+2462 |
| Asr | ⑯:①⓪ | circled number sixteen U+246F, circled digits U+2460 and U+24EA |
| Maghrib | ⑲:⓪④ | circled number nineteen U+2472, U+24EA, U+2463 |
| Isha | ⅩⅩ:24 | Roman numeral ten U+2169 twice, beside Western digits |

Kotlin isolates all six, so the `clock` section goes from 60 to 66 cases. `theClockCasesHoldTheIsolateRuleInEveryDigitSet` holds them: 18 `clockForms` cases, each day-2 string drawn as it is and spoken between U+2068 and U+2069. Each one holds a No or Nl unit, and nothing but those, decimal digits and colons.

## Item 1b: U+001D, U+001E and U+001F

Kotlin's `isBlank` takes every `Char.isWhitespace`, and that includes U+001C to U+001F. `blankWords` held only U+001C, so a port whose rule stopped at U+001E, or took U+001C alone, survived. Now `blankWords` gives each of the other three a word of its own: `round` U+001D, `of` U+001E, `bezelHint` U+001F, beside `updateOnWatch` U+001C. Kotlin turns each back into the English. `theWordsCasesAreEachFeedsWordsWithTheEnglish` checks that the text sends each one as a lone control, that each is a `Char.isWhitespace`, and that the words read the English. Swift's `testABlankWordIsKotlinsBlank` takes the three as blanks too.

## Item 1c: the samples' check uses the port's blank rule

`testTheSamplesCarryEveryScreenWord` used a private helper built on Foundation's `whitespacesAndNewlines`, which is not the rule `orEnglish` falls back by. The helper is gone, and every check calls `WatchScreenWords.isBlank`.

## Item 1d: a later phone's key in every kind of object

`newerPhone` had `"future": 1` at the top and in `place`, `ui`, `ui.spoken`, a day, its `times` and a phrase. It now also has one in `ui.screens`, in its first `turnRight` count, in day 0's `clockTime`, and in `ui.countdown`. That makes eleven places, one in each class of the model (`NEWER_PHONE_STRAYS`): `WatchFeed`, `WatchPlace`, `WatchUi`, `WatchCountdownPhrases`, `WatchSpoken`, `WatchScreenWords`, `WatchCount`, `WatchDay`, `WatchTimes`, `WatchSix` and `WatchPhrase`. A map, the screens' `presets`, is no such object: each of its keys is an id.

**`ui.countdown` came in review round 1.** The first build of this item, `ae008984`, held ten places and missed the countdown phrases, yet its message, this report, plan 4 and both tests' docs said every kind of object. A Swift `WatchCountdownPhrases` that refused an unknown key passed `swift test` 97/0. Both lenses of review round 1 found it. The fix gives `ui.countdown` its key (option (a) of the review, which keeps the claim true; narrowing the wording was the other), and makes Kotlin read the classes from the model itself, so a class added later cannot be missed the same way.

**Its base changed.** The 24-hour English sample's days hold no `clockTime` (it is `null`), so a key cannot sit inside one. `newerPhone` is now the twelve-hour English sample (`Fixtures.capeTownSamples()["en12"]`) with the first sample's two phrases, encoded by `WatchFeedJson.encode`, with the eleven keys added over its tree. It is still ENCODER, by the file's own rule, and still a feed. Its `expect` now says day 0's Asr is "4:10 PM". A 12-hour phone with his phrases is a state a real phone can be in. The other choice was to give the 24-hour sample a `clockTime` on one day, which no phone writes.

- Kotlin, `aLaterPhonesKeyIsIgnoredInEveryObject`: the eleven places in the text's order, each the number 1, and one in each class `OBJECT_TYPES` reads from the serializers' descriptors (a nullable's `?` dropped, so `clockTime` is `WatchSix`), so a class added to the model fails there until `newerPhone` holds a key in it. The tree without them is `WatchFeedJson.encode`'s own text of the feed Kotlin reads, and that feed is the en12 sample with the phrases.
- Swift, `testALaterPhonesKeyIsIgnoredInEveryObject`: it finds the eleven places in the text, and decodes the text equal to the same text with every `future` taken out (through `JSONSerialization`, as `testANullInEveryWordIsRefused` does).

## Item 1e: the presets' order for Task 8

Kotlin's `presets` is an ordered map. `WatchFeed.words()` gives `TasbeehPresets.builtIn`'s ids first, whatever order the phone sent them in, then each id they lack, in the order the phone sent it (`english.presets + presets.filterValues { it.isNotBlank() }`). Neither the words case's tree as Swift reads it nor the port's `[String: String]` keeps that order. So each `words` case gains `presetIds`, the ids of `WatchFeed.words().presets` in Kotlin's order.

`blankWords` now sends its presets out of that order: `custom_2` ("Yours"), `alhamdulillah`, `subhanallah` (blank, so it takes the English), `custom_1`. Its ids are the seven built-ins, then `custom_2`, then `custom_1`. A list that keeps the phone's order for the built-ins, or sorts the rest, therefore differs. This second id the built-ins lack, and the reordering, are beyond the ruling's words. They are there so that errata p4 #13's "in the order the phone sent" can be held at all: with one such id, no order is shown.

- Kotlin, `theWordsCasesCarryThePresetsInTheBuiltInsOrder`: `ENGLISH.presets`' keys are `TasbeehPresets.builtIn`'s. For every case, `presetIds` equals `words().presets`' keys and equals the built-ins followed by the phone's other ids that are not blank, in its order. `blankWords` sends custom_2, alhamdulillah, subhanallah, custom_1.
- Swift, `testThePresetIdsAreTheBuiltInsInTheirOrderThenThePhonesOwn`: every case's ids are the port's names exactly, with no id twice. The first seven are the same in every case and are the English built-ins, `after_prayer` first. `blankWords` ends `custom_2`, `custom_1`. `WordsCase` reads `presetIds` as a required field.

Task 8's own list, and the test that ties it to these ids, are Task 8's (errata p4 #13). The port gained no ordering code here.

## Mutants: seen surviving first, then dying

Each mutant was applied alone to the port's sources by a scratch script, `swift test` was run, and the file was restored (`git diff` empty after each run).

| Mutant | At `30d402de` | After | Killed by |
|---|---|---|---|
| `isDigitOrColon` as `Character(scalar).isNumber` (named) | survives, 95/0 | killed, 6 failures | `testEveryClockCaseMatchesTheVectors` (day 2's six) |
| `isDigitOrColon` as `numericType != nil` (named) | survives, 95/0 | killed, 6 failures | the same |
| `isBlank` with `0x1C...0x1E` (named) | survives, 95/0 | killed | `testABlankWordIsKotlinsBlank`, `testEveryWordsCaseMatchesTheVectors` |
| `isBlank` with `0x09...0x0D, 0x1C` (named) | survives, 95/0 | killed | the same |
| `WatchScreenWords` refuses a key it does not know (own) | survives, 95/0 | killed | `testALaterPhonesKeyIsIgnoredInEveryObject`, `testEveryDecodeCaseMatchesTheVectors` |
| `WatchSix` refuses a key it does not know (own) | survives, 95/0 | killed | the same |
| a day's `clockTime` alone refuses one (own) | survives, 95/0 | killed | the same |
| `WatchCount` refuses one (own) | already killed (the laterKeys cases) | killed | the same, and the laterKeys cases |
| `WatchCountdownPhrases` refuses one (review round 1) | survives, 95/0, and at `765c82e8` 97/0 | killed, 2 failures | `testALaterPhonesKeyIsIgnoredInEveryObject`, `testEveryDecodeCaseMatchesTheVectors` |

In the fix round a script ran one strict-key mutant for each of the eleven classes, each refusing any key outside its own (a check at the top of its decoder, or one written for a type whose decoder Swift synthesizes), in a copy of the tree, and restored the sources byte for byte. At `765c82e8` ten die and `WatchCountdownPhrases` survives (97/0). After `5dc5fdcb` all eleven die, each to `testALaterPhonesKeyIsIgnoredInEveryObject` and `testEveryDecodeCaseMatchesTheVectors`. A Kotlin mutant that drops the countdown's key from the generator and from `NEWER_PHONE_STRAYS` alike, with the vectors regenerated from it, fails `aLaterPhonesKeyIsIgnoredInEveryObject` on its class check alone.

The two blank mutants already died to `testABlankWordIsKotlinsBlank` alone, before the vectors were rewritten. Two generator mutants of item 1e were run the same way, the vectors regenerated from each, then restored byte for byte. `presetIds` written sorted, and `presetIds` in the phone's order with the English ids after it, each fail `theWordsCasesCarryThePresetsInTheBuiltInsOrder` and Swift's preset test.

The red steps, before each generator change and never committed:
- 1a/1b: `theClockCasesHoldTheIsolateRuleInEveryDigitSet` expected 18, found 12, and `theWordsCasesAreEachFeedsWordsWithTheEnglish` found "ROUND %1$s" where U+001D was due.
- 1d: Kotlin found 7 of the 10 places; Swift found 7, and no `clockTime`.
- 1d after review: Kotlin expected `ui.countdown.future` and found the ten without it; Swift found 10 of 11, no `ui.countdown`.
- 1e: Kotlin found no `presetIds` key and `custom_2` missing; Swift's loader stopped on `keyNotFound(presetIds)`.

## Item 2: the P4.3 report and plan 4

- `2026-10-30-watchos-words-and-clock.md`: "`SwiftPortVectorsTest` 16" is now 23, the count at P4.3 (25 since this ruling). The mutant line now names the two survivors the reviews found and their kill here.
- Plan 4 Task 3's Produces line: the Turkish crown hint is `Digital Crown’u çevirin veya dokunun`, with the typographic apostrophe the resources hold.
- Plan 4 Task 3 gains a Ruling 60 as-built bullet. Task 1's `newerPhone` line and Task 3's `words` line note the new places and `presetIds`.

## The feed's size budget

These are vector cases, not feed words, so no shipped feed grows:
- no file under `shared/`, `wearApp/`, `watchcore/src/commonMain/` or `iosApp/WatchCore/Sources/` changed;
- the samples, the phone goldens and `watch-rules-vectors.json` are byte-identical to `30d402de`;
- `-Ptaqwa.updateVectors` on `:shared`'s golden and sample tests rewrites nothing;
- only `feed-vectors.json` changed, a test file that no app bundles (656,354 to 663,853 bytes, and 663,930 after the fix round; 93 decode cases, still 23 FOREIGN; `clock` 66; `words` 6).

The host's `[measure]` lines after this work, with P4.3's beside them:

| Measure | Now | At P4.3 |
|---|---|---|
| Largest feed, words in: bn-BD/12 CONTROL | 59,989 of 60,000 UTF-8 bytes, 63 phrases | 59,989 |
| The same as a message | 59,989 data bytes, path 65 | 59,989, path 65 |
| Largest times-alone feed: bn-IN/12 | 33,875 of 40,000 | 33,875 |
| Screens' words, largest (bn) | 2,555 bytes | 2,555 |
| `WorstFeeds.SCREENS` | 4,702 bytes | 4,702 |

## Checks, under the host state of 30 October

`xcodebuild -checkFirstLaunchStatus` exited 69 at 16:00, again just before the host suites ran, and at 16:20. No `xcrun simctl` was called, no `runFirstLaunch` was started, and no simulator lock was taken.

- `swift test --package-path iosApp/WatchCore`: 97 tests, 0 failures (95 at the base, plus `testALaterPhonesKeyIsIgnoredInEveryObject` and `testThePresetIdsAreTheBuiltInsInTheirOrderThenThePhonesOwn`). `clock` 66 cases, `words` 6.
- `scripts/test.sh :shared:testAndroidHostTest :widgetcore:testAndroidHostTest :watchcore:testAndroidHostTest :wearApp:testDebugUnitTest`, with the three `linkDebugTestIosSimulatorArm64` tasks, `--rerun-tasks`: exit 0, BUILD SUCCESSFUL in 2m 31s.
  - Versions agree: 1.3.0 (39), and 1.3.0 (100039) on the Galaxy; test-bump-version.sh PASS.
  - shared host 2271/0, widgetcore 111/0, watchcore 224/0 (222 at the base; `SwiftPortVectorsTest` 25, 23 at the base), wearApp 267/0. Nothing skipped, and no EOFException: the known flake did not come up.
  - The `:shared`, `:watchcore` and `:widgetcore` test binaries linked.
- `-Ptaqwa.updateVectors` run again, `--rerun-tasks`, on `:watchcore` and on `:shared`'s `WatchFeedGoldenTest` and `WatchFeedSampleTest`, leaves every generated file byte-identical (`feed-vectors.json` sha1 `cd168a25`).
- `WATCH_DEST='generic/platform=watchOS Simulator' scripts/watch-build.sh build`: BUILD SUCCEEDED. xcodebuild logged its known line, "CoreSimulator is out of date … Simulator device support disabled".
- `IOS_DEST='generic/platform=iOS Simulator' scripts/ios-build.sh build ARCHS=arm64`: BUILD SUCCEEDED. TaqwaWidget.appex is 3 MB. check-watch-bundle: every rule holds (rule 4b skipped as ad hoc, rule 8 skipped as Debug), TaqwaWatchWidgets.appex 1396 KB, TaqwaWatch.app 2988 KB, as at P4.3.
- `tools/i18n-check.py`: clean. `git diff --check`: clean.

**Fix round 1 (after review), at `5dc5fdcb` and its docs commit.** `xcodebuild -checkFirstLaunchStatus` exited 69 at 16:34 and 16:46; no `xcrun simctl`, no `runFirstLaunch`, no lock.
- Red first: before the generator change, Kotlin's `aLaterPhonesKeyIsIgnoredInEveryObject` expected `ui.countdown.future` and failed, and Swift's `testALaterPhonesKeyIsIgnoredInEveryObject` found 10 of 11 (2 failures).
- `swift test --package-path iosApp/WatchCore`: 97 tests, 0 failures.
- The same `scripts/test.sh` line as above, `--rerun-tasks`: exit 0, BUILD SUCCESSFUL in 2m 24s; versions 1.3.0 (39) and (100039), test-bump-version.sh PASS; shared host 2271/0, widgetcore 111/0, watchcore 224/0 (`SwiftPortVectorsTest` 25), wearApp 267/0, nothing skipped, no EOFException; the three test binaries linked.
- `-Ptaqwa.updateVectors` again, `--rerun-tasks`, on `:watchcore` and `:shared`'s golden and sample tests: byte-identical (`feed-vectors.json` sha1 `6cc468cc`, 663,930 bytes). Against `765c82e8` only `newerPhone`'s `json` and the `about` line differ.
- `WATCH_DEST='generic/platform=watchOS Simulator' scripts/watch-build.sh build-for-testing`: TEST BUILD SUCCEEDED. `IOS_DEST='generic/platform=iOS Simulator' scripts/ios-build.sh build ARCHS=arm64`: BUILD SUCCEEDED, TaqwaWidget.appex 3 MB, check-watch-bundle every rule holds (4b and 8 skipped as before), 1396 KB and 2988 KB.
- `tools/i18n-check.py`: clean. `git diff --check`: clean. No file under `iosApp/WatchCore/Sources/`, `shared/`, `wearApp/` or `watchcore/src/commonMain/` changed.

**DEFERRED to the catch-up gate (Ruling 59), as at P4.3:**
- the Kotlin/Native `iosSimulatorArm64Test` suites of `:shared`, `:watchcore` and `:widgetcore` at this head. Nothing they run changed here: `SwiftPortVectorsTest` is a host test, and no common source or common test changed but this file's generator, which is host-only;
- `scripts/test.sh` run whole;
- `scripts/ios-build.sh` at its default iPhone 17 Pro.

This work has no device or simulator step of its own.

## Errata and rulings

1. **ff-only merge:** done. `watch-apple` was `30d402de` = `watch-companion`, with no commits of its own.
2. **Not a plan task:** built as Ruling 60 items 1 to 3 alone, in three commits "Watch core: … (Ruling 60)", and two "Watch core: …, after review" for review round 1. The vectors are the encoder's. The Swift tests stay green. Each named mutant was seen surviving, then dying.
3. **Size:** the largest feed is unchanged, as measured above.
4. **Errata p4** (numbered from 1, as `plan-errata-2026-10-24.json` lists them; this report first gave them from 0): #13 (Ruling 60 items 1e and 4) is met on the vectors' side, by `presetIds` and `blankWords`' order. Task 8's ordering and its test stay Task 8's. #12 (read `feed.words` once per render) is for Tasks 6 to 9, and nothing here reads it in a view. #11 (P4.9's compass vectors) is Task 9's. No other entry touches this work.
5. **Rulings 55 to 59:**
   - Ruling 56: every new or changed case is ENCODER (`newerPhone`, `blankWords`, `clockForms`) or a section case on an ENCODER feed. No FOREIGN case was added; the list stays 23, held by `everyCaseHasTheClassItsRuleGives`.
   - Ruling 57 item 2's `swift` cases are unchanged.
   - Ruling 59: the simulator steps are DEFERRED, as listed above.
   - Ruling 39: nothing about the place or the privacy promise changed. The vectors' places are the samples' Cape Town as before.
   - Rulings 41, 42 and 43 (faces and reading order) are not touched.
6. **WIP:** each cycle was committed as soon as it was green, so there is no WIP to fold. No red step was committed.
7. **Host state:** see Checks.
8. **Simulators:** none was used, so no lock (`sim-se3`, `ios-pair`) was taken. KB2604110095 and the work iPhone 12 were not touched.
9. **The Gradle flake:** see Checks.

## Left open, for the reviewers and the Lead

- `newerPhone`'s new base (the en12 sample with his phrases) is my reading of "a day's clockTime where the model has them". The Lead may prefer to keep the 24-hour sample and add a twelve-hour later phone as a case of its own (an ENCODER case, 94 decode cases).
- An id the built-ins lack whose name is blank is dropped by both sides' `orEnglish`, and no vector holds that. It is outside this ruling.
- Review round 1's minors, fixed in the fix round: the vectors' `about` source line is wrapped (its text is unchanged but for the eleven), the commit table names `765c82e8` instead of "HEAD (the third)", the size section is no longer headed "Item 3" (Ruling 60's item 3 is the commits and the deferral), and the errata are numbered from 1, as the errata file and the ledger number them.
- Review round 1's minors left open: `presetIds`' tail (`custom_2`, then `custom_1`, in the phone's order) cannot be read back through the port's `[String: String]` without a JSON scanner of its own, and the phone writes built-in ids alone, so how Task 8 keeps the phone's order for an id it lacks is Task 8's (errata p4 #13). Swift's preset test takes the built-ins' order from the vectors, which Kotlin holds to `TasbeehPresets.builtIn`; Task 8's own list is held to them there. The four expected controls in `theWordsCasesAreEachFeedsWordsWithTheEnglish` are a `JsonObject` built for the comparison where a plain map would do: style only, and left.
