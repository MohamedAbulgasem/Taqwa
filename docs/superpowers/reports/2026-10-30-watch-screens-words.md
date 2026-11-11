# Plan 3 Task 10: the phone fills the watch's words

Lane P (`watch-feed`), 30 October 2026. Plan 3 Task 10 (P3.10), built test-first on `watch-companion`'s head.

- Base: `524b0abe` (`git merge --ff-only watch-companion`: already up to date, so P3.2 and Lane A's Ruling 57 are under it).
- Commit: one, with the plan's Step 6 message ("Watch feed: the phone sends the words of the watch's screens in its own language, its 12-hour times as it writes them and the digits of its ring"), Taqwa identity, no trailer, nothing pushed. The red WIP `ccfdd5e2` (the tests on compile stubs) and the green WIP `24fcea36` were folded in by a soft reset.
- Devices: none. No lock taken, no emulator or simulator by hand (the Kotlin/Native tests boot their own simulator, and `scripts/ios-build.sh` its own destination). The plugged-in phone `KB2604110095` and the work iPhone 12 were not touched. No release key was read or used.

## What the phone now sends

| Field | Built from |
|---|---|
| `ui.screens` | `watchScreenWords(language)`, read in the sync beside `watchUiLanguage()` and `watchSpoken()` (`AppContainer.watchFeedFor`), so all three come from one resource environment |
| `ui.latinScript` | `language.latinScript`, the app's `isLatinScriptUi()` |
| `ui.ringDigitZero` | `'0'` when `CountdownDigits.westernFallback(format.languageTag())` holds and the feed's zero is not already `'0'`, else null (Ruling 47 item 1) |
| `days[].clockTime` | on a 12-hour phone, `format.clockTime(h, m)` for all six times, Sunrise's included, through `WatchDigits.write`, bare; null on 24 hours |

`watchScreenWords` reads the plan's table through the `ScreenStrings` seam:
- from the phone's resources, each as its pattern (`getString(res, "%1$s", "%2$s")`): the titles, the rows' spoken states, `round`, `of`, `reset`, `resetConfirm`, the seven `tasbeeh_preset_<id>` names in `TasbeehPresets.builtIn`'s order (a `LinkedHashMap`, Ruling 47 item 5), the Qibla words, the four cardinals and the four compass titles;
- from `WatchWords.screens(language)`, everything else: `openOnPhone` and `updateOnWatch` (equal to `:wearApp`'s own `open_taqwa_on_phone` and `update_taqwa_on_watch`, held by a test), the four new phrases, and accessibility round 2's six tasbeeh phrases and two turns.

`ResourceScreenStrings` holds the explicit key-to-resource map, and throws for a key outside it. A read that throws passes to the sync, which answers that it failed: no word is guessed.

## Errata, and how each was resolved

- **Execution map erratum 1** (ff-only to `watch-companion`): done, already up to date.
- **Plan 3 errata (`plan-errata-2026-10-24.json`, key p3):**
  - #2 (`vectors/phone/` come from `:shared`'s `WatchFeedGoldenTest`): both generators ran, `:watchcore` first, then `:shared:testAndroidHostTest -Ptaqwa.updateVectors` for the five goldens.
  - #7 (the 12-hour fixtures are not what the phone writes): already fixed by P3.3 (U+00A0, the Turkish marker first). The builder writes the phone's own `TwelveHourClock.render`, so no fixture moved and no `:wearApp` test or Swift vector broke. The builder's tests use an `NBSP` constant, and their Turkish case puts the marker first.
  - #1 and #3 to #6, #8 to #11 touch other tasks, not this one.
- **Ruling 47** (binding from P3.3): item 1 as the `ringDigitZero` row above, which replaces the plan's "else the feed's zero"; item 2, `Fixtures.bengali()` now carries `latinScript = false` and `ringDigitZero = '0'`, and `urdu()` `latinScript = false`, so `watch-rules-vectors.json`'s `capetown-bn` and `capetown-ur` feeds changed in those keys alone; item 5, as above; item 6, `capeTownIsTheSampleFeed` now compares `clockTime`, `screens`, `ringDigitZero`, `latinScript` and `tasbeeh` (his two phrases go in as `TasbeehPresets.custom`), on all four samples.
- **Rulings 39, 50, 52 to 57:** the words and `latinScript` are the app's own text, the same on every phone of a language. `clockTime` and `ringDigitZero` are the phone's text: `clockTime` writes the feed's own times at his place again, with the phone locale's day period, and `ringDigitZero` follows the phone's locale tag. So no coordinate is added, but `clockTime` can show the phone's locale where `ui.language` does not (a he-IL phone on the English interface). Plan 2's table under Ruling 39 names each in its row (since review round 2: the words and `latinScript` in a new row, `clockTime` in the clock strings' row, `ringDigitZero` in the digit zero's). The delivery numbers (Ruling 50) are untouched: the words are inside the feed, so its fingerprint covers them as it covers the rest. `largestFeed` (Ruling 54 item 5) gains a `screens` input, so all three size tests build the same worst case.
- **Errata in Task 10's own text, found while building:**
  1. `phrasesGoWhileTheyFit` is in `WatchFeedBuilderTest` (commonTest), not `WatchFeedSampleTest`, and runs on the host and the simulator, where no bundle can be read. There it takes `WatchScreenWords.ENGLISH`, which `bundledEnglishIsTheEnglishDefault` and the simulator's `theDefaultReadsThePhonesOwnStrings` tie to the phone's English.
  2. Accessibility round 2 is not on the branch (`a11y-round-2` holds its spec and plan only), so the six tasbeeh phrases and the turns come from `WatchWords`, with round 2's own table's values. They were generated from the round 2 spec's table by a script, not retyped, and the script checked the Arabic against `Fixtures.ARABIC_SCREENS` before writing. The turns are that table's plurals asked at 15, 30, … 180: one run of 180 in six languages, and Arabic's 90 / 105 / 180 (درجات at 105 alone, CLDR's "few"). `ScreenStrings.counted` is built as the plan's seam, and `ResourceScreenStrings`' plural map is empty until round 2's two plurals join it.
  3. The reader's host test reads `wearApp/src/main/res/values*/strings.xml`, so `shared/build.gradle.kts` declares them as a test input (`wearStrings`), as it does the other files its tests read; the plan's file list did not name it.
  4. No sample moved: the builder's words equal the samples' in English and Arabic, so Step 3's "change Fixtures wherever the phone writes otherwise" changed only Ruling 47 item 2's two fixtures.

## Step 4: the open handshake

Plan 2's Task 6 is on the branch, and Task 4 landed after it, so this step only checks: `WearMainActivity.onResume` calls `TaqwaWearApp.onAppOpened()`, which calls `askPhone(AskWhy.OPENED)`: `scope.launch { withTimeoutOrNull(PhoneAsk.BOUND /* 10 s */) { PhoneAsk(this, feedStore).askIfDue(why, now) } }`, on the app's scope, never throwing (`TaqwaWearApp.kt:69-86`). No change.

## The tests

| Test | What it holds |
|---|---|
| `WatchScreenWordsReaderTest.everyLanguageHasEveryWord` | for all seven languages: no blank word, 4 cardinals, the built-ins' ids in order, every field's set of `%n$s` places equal to English's (the set, never the order), the turns' runs in order and ending at 180 |
| `aPlaceMayComeFirst` | Bengali "১০০-এর মধ্যে ১৩", Urdu "100 میں سے 13" through `WatchRules.filled` |
| `theWordsAreThePhonesOwn` | every resource-read field, every preset and the cardinals equal the language's `strings.xml` (or `values/`), in all seven; and one field of each other source (a new phrase, round 2's count and action, Arabic's turn runs, Turkish's turn and count) |
| `theWatchAndThePhoneSayTheNoticesAlike` | `openOnPhone` and `updateOnWatch` equal the Wear app's own, in all seven (`values-in` for Indonesian) |
| `bundledEnglishIsTheEnglishDefault` | the host's English is `WatchScreenWords.ENGLISH` |
| `aReadThatThrowsIsNotGuessed` | every key the reader asks for, failing in turn, makes `watchScreenWords` throw that failure |
| `theWorstWordsOutweighEveryLanguage` | `WorstFeeds.SCREENS` weighs at least every language's words |
| `WatchScreenWordsResourceTest.theDefaultReadsThePhonesOwnStrings` (simulator) | the real resource path gives `WatchScreenWords.ENGLISH` |
| `WatchFeedBuilderTest`: `aTwelveHourPhoneSendsItsOwnClockTimes`, `aTwentyFourHourPhoneSendsNone`, `aSaudiPhonesRingIsWestern`, `aBengaliPhoneHidesTheTransliteration`, and `theScreensWordsGoAsTheyAre` | the four `Produces in WatchFeedBuilder` rows; the 12-hour case covers Sunrise, the Turkish marker first, a Saudi phone's own digits in Arabic and Western ones under English |
| the five size tests | run again with the words in (below) |

Red first: the compile failure, then 11 behavioural failures on stubs (an empty `watchScreenWords`, a builder that ignored the inputs) on the host, and `theDefaultReadsThePhonesOwnStrings` on the simulator. `aTwentyFourHourPhoneSendsNone` and `theWorstWordsOutweighEveryLanguage` passed on the stubs, as guards do.

Eleven one-line mutants, each applied in the worktree, run against `:shared:testAndroidHostTest --tests 'world.taqwa.app.watchfeed.*'` and put back, all killed:

| Mutant | Killed by |
|---|---|
| the ring's zero sent without "and not already the feed's" | `aSaudiPhonesRingIsWestern` |
| `clockTime` from `clockFigures` | `aTwelveHourPhoneSendsItsOwnClockTimes`, the goldens, `capeTownIsTheSampleFeed` |
| `clockTime` without the feed's digit rule | `aTwelveHourPhoneSendsItsOwnClockTimes` (English on a Saudi phone) |
| Sunrise's `clockTime` bare | the same three as `clockFigures` |
| `latinScript` always true | `aBengaliPhoneHidesTheTransliteration`, the goldens, the samples |
| `screens` dropped by the builder | `theScreensWordsGoAsTheyAre`, the goldens, the samples |
| `rowPassed` read from `prayer_a11y_prayer_next` | `theWordsAreThePhonesOwn`, `bundledEnglishIsTheEnglishDefault`, the goldens, the samples |
| the presets in reverse order | `everyLanguageHasEveryWord`, the goldens |
| one read wrapped in `runCatching` with an English default | `aReadThatThrowsIsNotGuessed` |
| east and south swapped | `theWordsAreThePhonesOwn`, `bundledEnglishIsTheEnglishDefault`, the goldens, the samples |
| `WatchWords.screens` always English | `aPlaceMayComeFirst`, `theWatchAndThePhoneSayTheNoticesAlike`, `theWordsAreThePhonesOwn`, the goldens, the samples |

## The sizes, with the words in

Each size test takes its words from its `WatchFeedInputs.screens`: on the host each feed's own language's (`BundledScreenStrings.words(language)`), so `PhoneTags.TAGS` covers all seven; on the simulator `WorstFeeds.SCREENS`.

| Measure | Bytes | Limit |
|---|---|---|
| the largest times-alone feed (`theTimesAloneLeaveRoomForThePhrases`) | 33,756, bn-IN on 12 hours with the worst city | `TIMES_ALONE_MAX_BYTES` 40,000 |
| the largest feed (`theLargestFeedStaysWithinItsBudget`) | 59,870, bn-BD on 12 hours, 63 control-character phrases of 100 | `FEED_BUDGET_BYTES` 60,000 |
| the same as an Android message (`aFeedAtTheBudgetIsWithinTheMessageLimit`) | 59,870 data, path 65 | 60,000 and the envelope |
| the largest iPhone context (`theLargestContextFitsTheSmallestLimit`) | feed 59,982 (ar-SA, 12 hours, 95 emoji phrases), binary plist 60,111, keyed archive 60,399 | 65,536 |
| the screens' words alone (`theWorstWordsOutweighEveryLanguage`) | Bengali 2,436 at the largest; `WorstFeeds.SCREENS` 4,518 | the worst at least every language |

Every feed keeps its 31 days and fits its budget; the phrases give way, as the bisection does, never the times or the words. The other cases: ar-SA 47,402 (100 Arabic phrases), 59,402 (100 emoji), 59,754 (72 control); bn-BD 51,262 (100 Arabic), 59,710 (88 emoji); Urdu (`ur-PK-u-nu-arabext`) 46,078, 58,078 and 59,678 (75 control). The iPhone's other three: ar-SA control 59,734 (67), bn-BD control 59,715 (65) and emoji 59,907 (92), each archive under 60,400.

## Step 5: the checks

- `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`: green, host 2,268 and iOS 2,215, none failed or skipped (`WatchScreenWordsReaderTest` 7, `WatchFeedBuilderTest` 18 on both, `WatchFeedSampleTest` 11, `AndroidWatchLinkTest` 24, `IosWatchLinkTest` 25, `WatchScreenWordsResourceTest` 1).
- `scripts/test.sh`: exit 0. Versions agree, 1.3.0 (39) and (100039); `test-bump-version.sh` PASS; `swift test` 19 with no failure; shared 2,268 / 2,215, widgetcore 111 / 109, watchcore 217 / 197, wearApp 235, none failed. No `EOFException` and no `EngineBudgetBenchmarkTest` flake in the run. Run again on the commit itself: exit 0, swift 19, every suite as above (`:shared:testAndroidHostTest` came from the build cache, so it was run once more with `--rerun`: 2,268, none failed).
- `scripts/ios-build.sh` (`IOS_DEST` the never-booted iPhone 17 `D9C6CFA7`): BUILD SUCCEEDED, `TaqwaWidget.appex` 3 MB of 15. Both `shared.framework/Headers/shared.h` (the Gradle framework and Xcode's) name `WatchFeedBridge` alone: no `WatchFeed`, `WatchUi`, `WatchScreenWords` or `WatchDay`.
- Regenerated: `:watchcore:testAndroidHostTest -Ptaqwa.updateVectors` changed `watch-rules-vectors.json`'s `capetown-bn` (`ringDigitZero` "0", `latinScript` false) and `capetown-ur` (`latinScript` false) and nothing else; the samples and `feed-vectors.json` did not move. Then `WatchFeedGoldenTest` under `-Ptaqwa.updateVectors` rewrote the five goldens: compared key by key, each only gains its `ui.screens` words (and 15 keys for the presets, cardinals and turns; Arabic 23), Urdu and Arabic `latinScript` false, and Tripoli's 12-hour golden a `clockTime` on all 31 days where it had null ("4:05 م", "7:11 ص"); nothing else changed.

## Drafts for native review (plan 5)

The four new phrases, in `WatchWords.screens`, each marked `// draft, to native review (plan 5)`:

| | `bezelHint` | `undoAction` | `phrasesAction` | `holdLevel` |
|---|---|---|---|---|
| en | Turn the bezel or tap | Take one away | Choose a dhikr | Hold your wrist level |
| ar | أدِر الإطار أو اضغط | أنقِص واحدة | اختر ذكرًا | أبقِ معصمك مستويًا |
| fr | Tournez la lunette ou touchez | Retirer un | Choisir un dhikr | Gardez le poignet à plat |
| tr | Çerçeveyi çevirin veya dokunun | Bir eksilt | Bir zikir seçin | Bileğinizi düz tutun |
| id | Putar bezel atau ketuk | Kurangi satu | Pilih zikir | Jaga pergelangan tetap datar |
| ur | بیزل گھمائیں یا ٹیپ کریں | ایک کم کریں | ذکر منتخب کریں | اپنی کلائی سیدھی رکھیں |
| bn | বেজেল ঘোরান বা চাপ দিন | একটি কমান | একটি জিকির বেছে নিন | কব্জি সমান করে রাখুন |

English and Arabic are Task 3's. The other five follow each language's own app words: dhikr, zikir and জিকির as `tasbeeh_reset_confirm` writes them, and the app's own verb for a tap (`tasbeeh_hint`).

## For the Lead

- **A 12-hour phone whose interface is not its locale's language.** `clockTime` is what the app writes, `format.clockTime`, whose marker comes from the phone's locale, while `ui.am` and `ui.pm` come from the words locale (plan 2). So an English interface on a 12-hour ar-SA phone sends "4:10 م" (Western digits by the Lead's call 3), as its Prayer screen writes "٤:١٠ م", while its `am`/`pm` are "AM"/"PM". The watch copies the app, as this task asks; whether the app itself should write the interface language's marker there is a phone question, outside this task.
- Plan 4 Task 3 can start: the samples, the goldens and `WorstFeeds.SCREENS` are as this task left them.

## Review round 1, and its fix

The code lens's one Important: `clockTime`'s day period is the phone locale's (`format.clockTime`), while `ui.am` and `ui.pm` are the words locale's (plan 2), and nothing tested a 12-hour phone outside the seven. The reviewer put two ways to the Lead: (a) keep it and pin it, or (b) build `clockTime` from the words locale (`TwelveHourClock.render` over `clockFigures`' parts, `dayPeriods(language)` and `markerFirst` of the words locale's tag).

**The fix keeps the plan's default, (a),** because that is what the plan's own words rule: plan 3's call 9 ("12-hour times carry their day period as the app writes it") and plan 2's hand-off ("its own `clockTime`, marker included"). A different ruling changes this task alone. `WatchFeedSampleTest.aTwelveHourPhoneOutsideTheSevenWritesItsTimesAsItsPrayerScreenDoes` now pins it on the real Android formats, the app on English and 12 hours:

| Phone | Today's Asr | Today's Sunrise | `ui.am`/`ui.pm` |
|---|---|---|---|
| hi-IN | 4:10 pm | 6:01 am | AM/PM |
| he-IL | 4:10 אחה״צ | 6:01 לפנה״צ | AM/PM |
| fa-IR | 4:10 بعدازظهر | 6:01 قبل‌ازظهر | AM/PM |
| mr-IN, az-AZ | 4:10 PM | 6:01 AM | AM/PM |
| zh-CN | 4:10 下午 | 6:01 上午 | AM/PM |
| ko-KR | 4:10 오후 | 6:01 오전 | AM/PM |
| my-MM | 4:10 ညနေ | 6:01 နံနက် | AM/PM |
| am-ET | 4:10 ከሰዓት | 6:01 ጥዋት | AM/PM |

On every day of those feeds, each of the six `clockTime`s begins with that day's bare `clock` (Western digits) and a no-break space. Within the seven it pins the mixed cases too: tr-TR on English sends "ÖS 4:10" beside AM/PM, ar-SA on English "4:10 م" beside AM/PM, and en-US on Turkish "4:10 PM" beside ÖÖ/ÖS. The builder's comment at `clockTime` now says the marker is the phone locale's on purpose.

**What (b) would move, measured:** (b) applied as a mutant, then `:shared:testAndroidHostTest --tests 'world.taqwa.app.watchfeed.*'`: 208 tests, 1 failure, the new pin (hi-IN "4:10 pm" became "4:10 PM"). The goldens (`WatchFeedGoldenTest`), the samples (`capeTownIsTheSampleFeed`) and the rest stayed green, so (b) changes only the mixed cases above, as the reviewer said. Its cost besides: a phone on English with ar-SA or tr-TR as its locale would no longer copy its own Prayer screen. Its gain: on one watch, the face (which draws `clock` with `ui.am`/`ui.pm`) and the app's Today and Tile (which draw `clockTime`) would show the same marker, and Task 11's spoken times would stay in the interface language's script. Nobody has yet checked how Manrope's fallback and Wear's system fonts draw Hebrew, Persian, CJK, Hangul, Myanmar or Ethiopic markers on Today and the Tile, or how long markers such as "بعدازظهر" sit in their measured rows; under (a) that is plan 5's device pass, under (b) it does not arise.

**Checks after the fix (30 October, about 13:20):** the host suites ran green: shared 2,269 (one more than before), widgetcore 111, watchcore 217 and wearApp 235, with no failures, and `:androidApp:compileDebugKotlin` passed. `scripts/test.sh`'s versions, bump and `swift test` (19/0) passed too. Its Kotlin/Native simulator suites could not run, because Xcode became 27.2 at 12:33 and its simulator components are not installed. `simctl` now waits for an admin prompt (Lane A's ledger lines of 12:35 and 12:49), so my run stopped at `:shared:iosSimulatorArm64Test`. `:shared:compileTestKotlinIosSimulatorArm64` and `linkDebugTestIosSimulatorArm64` built with the change first. `scripts/ios-build.sh` was not run, for the same reason. The fix changes nothing the iPhone runs: one comment in `commonMain`, and the rest is an Android host test and this report. i18n is clean, and `diff --check` is clean.

## Review round 2, and its fix

The spec lens's one Important: the row this task added to plan 2's table under Ruling 39 named `clockTime` and `ringDigitZero` with the screens' words, and called all of them "the same on every phone of that language; never anything of his". That is untrue of both. `clockTime` is the prayer times at his place (the Tripoli 12-hour golden sends "5:50 ص"), with the phone locale's day period: on the English interface a he-IL phone sends "4:10 אחה״צ", an en-US phone "4:10 PM". `ringDigitZero` follows the phone's locale tag (`CountdownDigits.westernFallback(format.languageTag())`): on the Arabic interface ar-SA sends "0" and ar-LY null (`aSaudiPhonesRingIsWestern`). Plan 5 answers Play's Data safety and the App Store's privacy label from this table, so it has to be true.

**The fix, docs only, as the reviewer's first option:**
- `clockTime` moved into the clock strings' row, which says "the phone's text", with a note that these are the first row's times at his place and that `clockTime`'s day period is the phone locale's, with the he-IL and en-US example.
- `ringDigitZero` moved into the digit zero's row, which says "the phone's text", with the ar-SA and ar-LY example.
- The new row keeps the screens' words and `latinScript` (`language.latinScript`, the interface language's alone), says where the other two went, and keeps "the same on every phone of that language; never anything of his" for those alone.
- This report's Rulings line above says the same.

No ruling is contradicted: the fix makes the table true to Ruling 39, whose standard is "short, easy to understand and true". No code, test, golden or vector changed, so the round 1 fix's runs and both round 2 reviews' runs still stand for the code at this head.

**Plan text erratum:** plan 3 Task 10's file list says "plan 2's table under Ruling 39: one row". The task now touches three rows: one new and two amended.

**Minors left, as the two round 2 reviews list them** (the code lens CLEAN with six, the spec lens five beside its Important):
- The marker ruling, (a) or (b), is still the Lead's call. Under (a), Task 11 would make the face draw `ui.pm` ("PM", `ComplicationFields.kt:401`) but speak `clockTime`'s marker, for a 12-hour phone outside the seven.
- The new pin asserts the host JDK 21's CLDR markers for nine locales outside the seven, literally, not Android ICU's.
- The host stand-in reads raw XML: no Compose unescape, no `%n$d` canonicalisation. That is harmless for today's 29 keys.
- There is no tripwire for when accessibility round 2's keys reach the resources (carried from round 1).
- From round 1, still open: `AppContainer`'s wiring and the Android resource path are untested (P2.7 check 4 reads `ui.screens`), and plan 3 Task 10's text is not amended with its errata.
- KDoc hygiene: `AppContainer.kt:200` is not reflowed (126 characters), `WatchScreenWordsReader.kt:45` has a garbled clause, and `WatchFeedBuilder.kt:93` carries a measured number. Left so that the code stays the tree both reviews tested.
- The Kotlin/Native simulator suites and the full `scripts/test.sh` were not run at bc131685, because this machine's CoreSimulator is 1174.9.2 and Xcode 27.2 asks for 1174.21. `ios-build.sh` to a generic destination did build there (the spec lens). The only iOS-compiled change since 92effa42, where the suites ran green, is one comment.
