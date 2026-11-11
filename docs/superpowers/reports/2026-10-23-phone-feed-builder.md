# Plan 2, Task 3: the feed builder and the link's contract

Lane P (`watch-feed`), 24 October 2026. This report covers **P2.3a**: every step of Task 3 except Step 4's `pass.sh` run and Step 6 (the Urdu shots), which wait for Task 2 and run as P2.3b in Lane W (the execution map): its own section, "P2.3b", at the end. Step 7's privacy wording has its own report, `2026-10-23-watch-privacy-wording.md`.

- Base: `ae407749` (watch-companion after P2.1's merge; plan 1's Task 12 closed, G12).
- WIP `a913cb51`, then the task commit (the plan's Step 5 message) and the wording report's commit (Step 7's).
- No device, no emulator, no lock: Kotlin/Native's simulator tests and the JVM only.

## What was built

| File | What |
|---|---|
| `watchcore/.../WatchLinkContract.kt` | The link's paths, capabilities, context keys, the fingerprint's form (`isFingerprint`, `feedPath`, `isFeedPath`, `fingerprintIn`), each link's limit and the 60,000-byte budget, with Google's and Apple's caveats in the KDoc. |
| `shared/.../watchfeed/WatchFeedBuilder.kt` | `WatchFeedInputs` and `WatchFeedBuilder`: 31 days from yesterday at the place, the engine's instants unchanged, each date's midnight, the clock without its day period, the interface language's names and Jumuʿah, the Hijri date and its day-and-month from one date, the date line, the spoken date, the place as Ruling 39 allows it, `ui` from the words locale, and the phrases by bisection within the budget (at most 8 encodes). |
| `shared/.../watchfeed/WatchCityName.kt` | `watchCityName`: the Prayer screen's path (`setLanguage(format.languageTag())`, `displayName`, else the stored name, else ""). |
| `watchcore/.../WatchFeed.kt` | Two KDocs: `WatchPlace` (Ruling 39's place) and `WatchUi.language` (the words locale's tag, letter case only). |
| `shared/build.gradle.kts` | `watchcore/vectors/phone/` and `:watchcore`'s sample feeds as test inputs, after P2.1's `stringResources` (Ruling 44); `-Ptaqwa.updateVectors` and `-Ptaqwa.recordTimes` passed to the tests as system properties. |
| Tests | `WatchLinkContractTest` (watchcore, 4); `WatchFeedBuilderTest` (12) and `WatchCityNameTest` (4) in commonTest, so both platforms run them; `WatchFeedSampleTest` (10), `WatchFeedGoldenTest` (1) and `WatchFeedAgreesTest` (2) on the host; one more `PhoneTagsTest` case (erratum 1). |
| Helpers | `WorstFeeds` (commonTest); `BundledCities`, `BundledSpoken`, `FrozenTimes` (androidHostTest). |
| Vectors | `watchcore/vectors/phone/phone-{capetown-en,capetown-friday-en,capetown-ur24,tromso-may-en,tripoli-ar-ly12}.json`, from `phone/times/{capetown,tromso-may,tripoli}.json`; the five `:watchcore` vectors regenerated from the changed fixtures. |

## Test first

1. **Step 2, red.** With the tests written and no production code: `./gradlew :watchcore:testAndroidHostTest :shared:testAndroidHostTest` failed to compile on `Unresolved reference 'WatchLinkContract'`, `'WatchFeedBuilder'`, `'WatchFeedInputs'`; `:shared:iosSimulatorArm64Test` on `'watchCityName'`, `'WatchLinkContract'` and `'WatchFeedBuilder'`.
2. **The first real failures,** once the code was in (53 watchfeed host tests, 2 failed):
   - `WatchFeedGoldenTest`: `FileNotFoundException: ../watchcore/vectors/phone/times/capetown.json`, before the tables were recorded.
   - `WatchFeedSampleTest.capeTownIsTheSampleFeed`: the samples' **place** alone, in all four (en/24, en/12, ar-LY/12, ar-SA/24): `qiblaBearing` 23.352542268995705 against the phone's 23.0, `qiblaKm` 6,558 / 6558 / ٦٥٥٨ against 6,600 / 6,600 / ٦٬٦٠٠, and `declination` -25.41184382938471 against -27.0. Every day field (times, clock, names, Hijri, Gregorian, weekday, day number, the spoken date, midnight) and every `ui` field (language, rtl, digitZero, twelveHour, the markers, the countdown, `nextIn`, `spoken`) already equalled what the builder writes on the JVM's CLDR.
3. **Green:** the fixtures changed to the phone's values (below), the `:watchcore` vectors regenerated, the three tables recorded once with `-Ptaqwa.recordTimes`, the goldens written with `-Ptaqwa.updateVectors`.

## Every fixture change (`Fixtures.kt`)

The phone wins: each new value is what the phone's own function writes, and the builder was not bent to any fixture.

| Feed | Field | Old | New | The phone's function |
|---|---|---|---|---|
| Cape Town, every language | `place.qiblaBearing` | 23.352542268995705 | 23.0 | `QiblaMath.bearing(location).toInt()`, the Prayer screen's Qibla card (`PrayerScreen.kt:839`) |
| Cape Town, en and ar-LY (Western digits) | `place.qiblaKm` | "6,558" (en), "6558" (ar-LY) | "6,600" | `localizedGroupedKm(WatchFeedBuilder.roundedKm(QiblaMath.distanceKm(…)), format)`: 6,558.3 km to two figures, grouped as `DigitGrouping` groups Arabic in Western digits (a comma) |
| Cape Town, ar-SA (Arabic-Indic) | `place.qiblaKm` | "٦٥٥٨" | "٦٬٦٠٠" (U+066C) | the same, with `DigitGrouping`'s Arabic thousands separator beside Arabic-Indic digits |
| Cape Town, the rules vectors' Bengali-digit feed | `place.qiblaKm` | "৬,৫৫৮" | "৬,৬০০" | the same, a comma beside Bengali digits |
| Cape Town, the rules vectors' Urdu-digit feed | `place.qiblaKm` | "۶,۵۵۸" | "۶٬۶۰۰" (U+066C) | the same, U+066C beside extended Arabic-Indic digits |
| Cape Town, every language | `place.declination` | -25.411843829384712 | **-27.0** | `WorldMagneticModel.declinationDegrees(-33.9249, 18.4241, 0, decimalYear(2026-10-18))` = -26.809, `.roundToInt()` |
| Tromsø (rules vectors only) | `place.qiblaBearing` | 154.28048653422434 | 154.0 | `QiblaMath.bearing(…).toInt()` (154.2805) |
| Tromsø | `place.qiblaKm` | "5,542" | "5,500" | `roundedKm(5,541.8)` grouped |
| Tromsø | `place.declination` | 8.1 | **11.0** | WMM2025 at (69.6492, 18.9553) on 17 May 2026 = 11.157, rounded |

- **Two values differ from the plan's "known in advance" list** (Task 3's Files block, which rounded the fixtures' invented figures): Cape Town's declination is **-27.0**, not -25.0, and Tromsø's is **11.0**, not 8.0. Both are WMM2025's own answer at the fixture's point and date; -25.41 and 8.1 were never the model's. Cape Town's is the one the sample test holds the phone to; Tromsø's is not compared with the phone by any test, and follows the same function so that no fixture disagrees with the phone.
  - **For plan 3's Task 7 (P3.7):** its Step 5 reads D from the stored `feed.json`, so it holds; but its text names "−25.0 once it has rounded it". After this task the sample's D is **−27.0**, so "magnetic 23 − D" is magnetic 50, and "magnetic 340 − D" is magnetic 7. Its literal should be changed when P3.7 starts.
- The cities stay: the bundle names Cape Town (3369157) "Cape Town" in English and "كيب تاون" in Arabic, as the fixtures had them.
- `WatchRulesTest`'s three place assertions follow the fixtures ("6,600", "٦٬٦٠٠", "৬,৬০০"). `WatchFeedJsonTest`'s full-precision literals stay: they test the codec, not the phone.
- **No drawn string changed.** The face draws no field of `place` (the Moon reads `southern` alone, unchanged), so Task 2's fit tools need no rerun for these changes, and P2.3b's `pass.sh` is expected at 0 px against Task 2's pass. The Tile, Today and the Qibla dial that draw the city and the distance are plan 3's.
- `:watchcore` vectors regenerated with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`: `sample-feed-capetown{,-en12,-ar-ly12,-ar-sa}.json` and `watch-rules-vectors.json`, 20 lines, every change inside `place`. `:wearApp:testDebugUnitTest` (its `SampleFeedTest` reads the samples) stays green, 142 tests.

## The golden feeds

Built from frozen instants alone (`FrozenTimes.read`), each with `BundledSpoken.of(language)`, default `PrayerSettings` and `AndroidPlatformFormat`. The tables were recorded once from the engine with `-Ptaqwa.recordTimes`; `-Ptaqwa.updateVectors` rewrites the feeds and never the tables.

| File | Place, format | Now | UTF-8 | Days | `ui.language` | Place |
|---|---|---|---|---|---|---|
| `phone-capetown-en.json` | Cape Town, en/24 | 2026-10-18T06:00Z | 18,846 | 10-17 to 11-16 | `en` | Cape Town, 23.0, "6,600", -27.0 |
| `phone-capetown-friday-en.json` | Cape Town, en/24 | 2026-10-23T06:00Z | 18,898 | 10-22 to 11-21 | `en` | the same |
| `phone-capetown-ur24.json` | Cape Town, `ur-PK-u-nu-arabext`/24 | 2026-10-18T06:00Z | 21,940 | 10-17 to 11-16 | `ur-PK-u-nu-arabext` | کیپ ٹاؤن, 23.0, "۶٬۶۰۰", -27.0 |
| `phone-tromso-may-en.json` | Tromsø, en/24 | 2026-05-15T10:00Z | 18,695 | 05-14 to 06-13 | `en` | Tromsø, 154.0, "5,500", 11.0 |
| `phone-tripoli-ar-ly12.json` | Tripoli, ar-LY/12 | 2026-10-18T06:00Z | 21,830 | 10-17 to 11-16 | `ar-LY` | طرابلس, 109.0, "2,900", 3.0 |

- Every golden carries `ui.spoken` and each day's `gregorianSpoken`, in English, Urdu and Arabic, for plan 4's decoder.
- **Tromsø's ends:** the plan expected "polar ends", but the engine gives Fajr's and Asr's ends on every day of 14 May to 13 June 2026: its high-latitude rule sets the evening from the 18th (Maghrib 19:23, Isha 20:41) and the whole day from the 19th (Fajr 04:43, Sunrise 06:04). So no golden carries a null end. The Swift decoder still meets null ends in `watch-rules-vectors.json` (`capetown-no-ends`, `capetown-one-end-missing`). Tromsø's golden does hold the late shapes plan 4 needs: on 14–17 May the Maghrib and Isha fall at or after midnight (the 17th's Isha at 00:36 on the 18th, after which the 18th's Fajr is at 00:50), and the day the rule takes over.
- The Urdu golden is the first Urdu feed, for P2.3b's shots: in extended Arabic-Indic digits throughout (18 October: Fajr ۴:۴۲, Asr ۱۶:۱۰, "اتوار ۱۸ اکتوبر", "۶ جمادی الاول ۱۴۴۸"), its distance ۶٬۶۰۰.
- `grep -n "PrayerTimesEngine"` over `WatchFeedGoldenTest.kt`, `FrozenTimes.kt`, `WatchFeedSampleTest.kt` and `WorstFeeds.kt` prints one line, `FrozenTimes.kt:72`, inside `FrozenTimes.record` (written fully qualified, so no import line names it).

## Sizes

`theLargestFeedStaysWithinItsBudget`, over the sample's 31 days on a 12-hour clock, with `WorstFeeds.CITY` (300 bytes) and `BundledSpoken`. The UTF-16 column is what a `String` application context would have taken (a binary property list stores a string with any non-ASCII character as UTF-16), against the UTF-8 `Data` both links carry.

| Feed | Phrases | Phrases sent | UTF-8 bytes | UTF-16 bytes |
|---|---|---|---|---|
| ar-SA/12 | 100 Arabic | 100 | 40,699 | 61,472 |
| ar-SA/12 | 100 emoji | 100 | 52,699 | 73,472 |
| ar-SA/12 | 100 control | **88** | 59,707 | 111,488 |
| bn-BD/12 | 100 Arabic | 100 | 43,598 | 60,680 |
| bn-BD/12 | 100 emoji | 100 | 55,598 | 72,680 |
| bn-BD/12 | 100 control | **81** | 59,694 | 104,872 |
| ur-PK-u-nu-arabext/12 | 100 Arabic | 100 | 39,637 | 60,346 |
| ur-PK-u-nu-arabext/12 | 100 emoji | 100 | 51,637 | 72,346 |
| ur-PK-u-nu-arabext/12 | 100 control | **91** | 59,893 | 112,858 |

- Every case is within 60,000 UTF-8 bytes with all 31 days, the phrases are a prefix in order, and where fewer than 100 go the next one would have passed the budget. Only phrases of control characters (six bytes each in JSON) lose any.
- **The largest times-alone feed** (`theTimesAloneLeaveRoomForThePhrases`, every tag of `PhoneTags.TAGS` in each of its digit sets, 12 hours, the worst city, no phrases): **bn-IN/12, 26,092 bytes of `TIMES_ALONE_MAX_BYTES` (40,000)**. Plans 3 and 4 add their screens' words into the remaining 13,908 bytes (the Lead's call 6 estimates under 2 KB).
- The longest name the bundle holds is 91 UTF-8 bytes (Bengali); `WorstFeeds.CITY` is 300.
- The committed samples now: en 18,846, en12 18,752, ar-LY12 21,833 and ar-SA 23,002 bytes in UTF-8 (ar-SA 38,278 in UTF-16).

## The plan's errata, and how each was resolved

1. **`ui.language` keeps a phone's Unicode extension; `PhoneTags.numberingTags` must not build a double `-u-`.**
   - The builder writes `wordsLocaleTag(format.languageTag(), language)` unchanged, so `ar-SA-u-nu-latn` stays `ar-SA-u-nu-latn`. The tests expect it: the Urdu golden's `ui.language` is `ur-PK-u-nu-arabext`; `theDigitZeroIsTheFeedsOwnZero` asserts `ui.language` equals the tag for `ar-LY-u-nu-arab` and `ur-PK-u-nu-arabext`; and `WatchFeedAgreesTest` runs Makkah on an `ar-SA-u-nu-latn` format and asserts the feed's `ui.language` is that tag (with `en-GB` for London and `en` for the others).
   - `numberingTags` now names the other sets on the tag without its `-u-` extension (found case-insensitively) and lists each tag once: `ar-SA-u-nu-latn` gives `[ar-SA-u-nu-latn, ar-SA-u-nu-arab]`, `ur-PK-u-nu-arabext` gives `[ur-PK-u-nu-arabext, ur-PK-u-nu-latn]`, `ar-EG-u-ca-islamic` gives `[ar-EG-u-ca-islamic, ar-EG-u-nu-latn, ar-EG-u-nu-arab]`. Test first: `PhoneTagsTest.aTagThatAlreadyNamesItsDigitsIsNotGivenASecondExtension` (it also holds that no tag it gives carries two `-u-`). Every tag of `TAGS` and `FALLBACK_TAGS` gives what it gave before.
2. **The stale "NumberFormat only on the UI thread" comment** (`PlatformFormat.android.kt:23-24`): it now says no instance is shared across threads, the screens call theirs on the UI thread, and the watch feed builds a fresh `createPlatformFormat()` for each ask (`feedFor`, plan 2 Task 4) and uses it, `clockFigures` included, only on the worker serving that ask. Comment only; Task 4 builds `feedFor` that way.
3. **Ruling 44:** the `stringResources` input stays as P2.1 wrote it, and this task's inputs and flags come after it in the same `tasks.withType<Test>()` block. P2.2 appends its `phone-forms.json` line there too: an expected small merge conflict for the Lead.

## Notes for the Lead

- **One input beyond the plan's line.** `:shared`'s tests also take `:watchcore`'s `sample-feed-capetown*.json` as inputs (`sampleFeeds`), because `WatchFeedSampleTest` reads them: a regenerated sample would otherwise leave it UP-TO-DATE, green from the file before, as the build file's comment says of the strings.
- **The distance's thousands mark beside digits the face was not built for: fixed after review (round 1).** The plan's formula, `WatchDigits.write(localizedGroupedKm(…, format))`, was built first and flagged here: `DigitGrouping` picks the mark from the *format's* digits, so where `WatchDigits` turns a set the face cannot draw into Western digits the Arabic mark stayed, and a fa-IR phone (English interface, Persian digits) or an ar-SA phone with the app in English sent "6٬600" (U+066C beside Western digits, which the English and French fonts do not hold). Both review lenses found it, and it broke the Lead's call 3 ("Western digits throughout"). Now `WatchFeedBuilder.distance` writes the phone's own `localizedGroupedKm` where `WatchDigits.kept`, and otherwise `DigitGrouping.group(km, language.code) { it.toString() }`: Western digits with the interface language's mark, the language whose words stand beside it on the watch. ar-SA, fa-IR, fa-AF, ur-PK-u-nu-arabext and bn-BD with English give "6,600", ar-SA with French "6 600" (U+202F), ar-EG with Turkish "6.600"; ar-EG with Arabic keeps "٦٬٦٠٠" and fr-FR with Arabic "6 600" (U+202F), the phone's own. Test first: `aDistanceInWesternDigitsTakesTheInterfaceLanguagesMark` (common, both platforms, and on Android's real formats) and `assertOnlyDigitsFrom`, which now also fails on U+066B or U+066C in any string of a feed whose `digitZero` is '0' (red before the fix in `theLanguageIsTheAppsNotTheTags` and `aSetTheFaceWasNotBuiltForIsWesternThroughout`). No fixture or vector changes: they hold only pairs the face draws. Plan 2's literal formula (its Global Constraints' place bullet and Task 3 Step 3) is superseded by this, for the Lead to carry into the plan.
- `build` skips a preset with no parts rather than throwing (`parts.firstOrNull()`); `TasbeehStore` never stores one, so no feed changes.
- The bisection encodes the whole set first and only bisects when it does not fit: 1 encode in the usual case, at most 8 for 100 phrases.

## Verification

- `scripts/test.sh` on the final tree: exit 0. `:shared:testAndroidHostTest` 2,042 tests (P2.1's 2,012 + 30), `:shared:iosSimulatorArm64Test` 1,986 (1,969 + 17) (after review round 1's fix: 2,044 and 1,987), `:watchcore` 69 host / 67 iOS (65/63 + 4), `:wearApp:testDebugUnitTest` 142, `:widgetcore` 99/97 (up to date, untouched); 0 failures, 0 skipped.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, then Task 1's header grep for `WatchCountdownPhrases|WatchFeed|WatchUi|WatchSpoken|WatchCount|WatchDay|WatchPlace|WatchLinkContract`: prints nothing. Everything new in `:shared` is `internal`, and `:watchcore` stays an `implementation` dependency.
- Not run here, as the execution map splits it: Step 4's `tools/watchface/pass.sh` on `emulator-5558` and Step 6's `store.sh` and Urdu shots (P2.3b, after Task 2, below). `scripts/ios-build.sh` is the Lead's at the merge (the task touched `shared/`).

## P2.3b: Step 4's pass and Step 6's Urdu shots

Lane W (`watch-wear`), 25 October 2026. Base `431d4e99` (`watch-companion` after P5.2's merge, with P2.2 and Ruling 51a in). P2.3a's work was not redone. Evidence in `2026-10-23-phone-feed-builder/`.

### Step 4: the pass with the regenerated sample

- **Builds.** The debug watch app and the debug face were built from this tree with `./gradlew :wearApp:assembleDebug :watchface:assembleDebug`. They were installed with `adb -s emulator-5558 install -r`, not `install.sh`, because Gradle's `installDebug` is ruled out while the phone is attached.
  - The watch app's assets (`sample-feed-capetown*.json`, `phone/*.json`) are byte for byte `watchcore/vectors`.
  - The face's `res/li.xml` is byte for byte `watchface.xml`.
- **The pass** (`tools/watchface/pass.sh`, 12:22–12:45, exit 0, 51 shots) against Task 2's pass, which is `2026-10-19-face-pass/pass-*.png` as committed with Ruling 51a. It is measured with `task-12/passdiff.py`, whose set-asides are the Moon's seconds hand, its moon and the step count. The full table is in `pass-vs-committed.txt`.
  - Arch, Ring and Sun: every shot is within 3 levels. None is over 2 levels except one pixel of the Sun's dial at (229, 13), which P2.2's full pass also had.
  - Arch's four Arabic frames: 342, 83, 354 and 306 px, at worst 1 level. These are exactly the counts in Ruling 51a's same-session record for the face as committed against the files it restored.
  - Against P2.2 review 1's full pass (`phone-forms-fit/review1/pass-vs-committed.txt`), 36 of the 51 shots give the same counts, shot for shot. The four Arabic frames above differ from it only because Ruling 51a put those files back. The other 11 are the Moon's, each within a pixel or a level of P2.2's, plus the battery icon with the slots filled (57 px against 56), which moves between runs. This is the emulator's cross-session noise, which Ruling 51 item 4 says is not required to be 0 px.
- **The 0 px in one session** (Ruling 51 item 4; `same-session-place.txt`, `ab-place.sh`).
  - The method:
    - P2.3a changed only the samples' `place`.
    - So each sample, as the receiver stores it for the mock day, was shot against the same file with the `place` from before P2.3a (`32826332^1`: 23.352542268995705, "6,558", -25.41184382938471; the four objects are in `ab-place/`).
    - Both files went through `store.sh`, on the face as committed, at the same moments.
    - Rounds: new, old, new again. Then the Sun alone in four more rounds, alternating old and new.
  - The result:
    - Arch, Ring and Sun: 11 of their 15 frames are 0 px between old and new. The other four differ by 1 level: Ring's one pixel, which differs between new and new2 as well, and the Sun's English frames. Those Sun frames differ just as much between two rounds of the same feed (old against old2, 102 px; new against new3, 93 px), and are equal across feeds in other rounds (new3 against old3, 0 px at 12:58). That is the Sun dial's run-to-run noise, not the place.
    - The Moon's always-on frame is 0 px. Its awake frames differ only in the seconds hand's band at twelve, which `passdiff` sets aside.
  - **No drawn string changed,** so no shot replaces a committed one, as P2.3a's report expected. The face draws only `southern` from `place`, and `southern` did not change.

### Step 6: Urdu's digits with the phone's first Urdu feed

- **`tools/watchface/store.sh`**, written test-first:
  - The test: `tools/watchface/store-test.sh`, which uses a fake adb in a scratch repository and touches no device.
    - It was seen red: 4 FAIL with no `store.sh`.
    - Seven mutants are each killed by it: the delivery removed after the write, or not removed at all, `need_emulator` dropped, the face check dropped, Gradle's `installDebug`, the debug sample stored first, and a bare adb.
    - Then green: 11 ok.
    - After review round 1: the fake adb kept the feed's bytes for any call containing `cat > files/feed.json`, and the order check found the write by that fragment too. So a write without `run-as`, to `files/feed.json.tmp`, or into `files/feed.json.delivery` passed.
      - Now the fake keeps the bytes only for the whole call `-s emulator-5558 shell run-as world.taqwa.app sh -c 'cat > files/feed.json'`, and every order check compares whole calls (`grep -x`).
      - A new check holds that the only `run-as` calls are the removal and the write, once each.
      - 13 mutants in scratch copies all fail: those three, a second write into the delivery after the right one, another package, the write without the serial, an append (`>>`), and the six of the first round's kind (the delivery removed after the write or not at all, the face installed before the stop, no stop, Gradle's `installDebug`, `need_emulator` dropped). The shipped `store.sh` passes: 12 ok.
    - After review round 2:
      - The fake adb answered `emu avd name` in two writes (`cat`, then `echo OK`), and `need_emulator` reads that answer through `head -1` under `pipefail`. Under load, `head` could exit before the second write: the fake died of SIGPIPE, and a correct `store.sh` exited 141 (24 of 48 runs failed, 12 at once with every core busy). Now each answer goes out in one write, as the real adb's does, and the fake ignores SIGPIPE and exits 0. Under the same load 0 of 240 runs fail.
      - `store.sh` ran with the caller's PATH, whose `adb` is the real SDK adb, so a bare `adb` reached it before the test failed. Now `store.sh` runs with a scratch HOME and, first on PATH, an adb that records the call apart and fails, and a new check holds that no bare adb ran: 13 ok.
      - 16 mutants in scratch copies all fail, with a recording adb first on the caller's PATH that none of them reached. They are four bare adbs (the stop and the write with `-s`, the delivery's removal and the install without it), an adb under `$HOME`'s SDK, a bare wake before `need_emulator` (which round 1's test passed), and ten of the earlier kinds. Under the old test the bare ones reached the caller's PATH adb.
  - What `store.sh` does:
    - It writes the file byte for byte as the watch app's `files/feed.json` through `run-as`, from a path relative to where it is run, and never stores the debug sample in its place.
    - It removes `files/feed.json.delivery` with `rm -f` **before** the feed is written, which is `WatchLinkContract.STORE_STEPS`'s order.
    - It then stops the watch app and installs the face's debug APK again with `adb -s emulator-5558 install -r`.
    - Every adb call names `emulator-5558`. It never runs Gradle. It touches nothing when there is no file, the file is missing, the emulator is not running or is another AVD, or the face is not built.
  - One README line covers both scripts. P2.7 adds its "stop the paired phone first" to that line.
- **The shots** (`urdu-shots.sh`). `store.sh watchcore/vectors/phone/phone-capetown-ur24.json` stored the feed. Then each of Arch, Ring, Sun and the Moon (slots empty) was shot with `KEEP_FEED=1 shoot.sh … <style>-ur 16:47 awake`, with the clock set a minute before and the face bound again just before each shot.
  - At 16:47 the shots hold ۶ ۴ ۷ in the clock (۱۶:۴۷), ۶ in Maghrib's time (۱۹:۰۶) and ۶ in the Hijri date (۶ جمادی الاول).
  - At 16:47 the countdown (۲ گھنٹے ۱۹ منٹ میں) holds none of the three. So each style was **also shot at 15:23**, whose countdown to Asr is ۴۷ منٹ میں and whose Asr time is ۱۶:۱۰ (`<style>-ur-1523-awake.png`).
  - Folders: `urdu/watch-en/` (8 shots) and `urdu/watch-ur/` (8 shots).
- **How the watch's language was set.** The plan expected the emulator's Settings to do it, and Wear OS 6's Settings cannot.
  - Settings › System › Language shows only "The watch language is the same as your phone's. Go to your phone's language settings to update." (`urdu/settings-language-refused.png`). No phone is paired with this emulator: pairing is P2.7's, with Mohamed's sign-in. The setup wizard's language step does not resolve once setup is done. The image is a user build, with no root.
  - **First try, the face runtime's own per-app language.** `cmd locale set-app-locales com.google.wear.watchface.runtime --locales ur-PK` was set, then reset to `[]`. The control below shows it does **not** change the face's shaping, so no Step 6 shot uses it.
  - **What was used: the system language, as the Settings' picker sets it.** `urdu/SetLocale.java` is a few lines run as the shell user through `app_process`, from a dex pushed to `/data/local/tmp` and removed afterwards.
    - It makes the call that `LocalePicker.updateLocales` makes: the configuration's locales and `userSetLocale`, then `IActivityManager.updatePersistentConfigurationWithAttribution`. The shell user holds `CHANGE_CONFIGURATION`.
    - With ur-PK set, the system interface turned Urdu (the face's description read "واچ فیس"), and `persist.sys.locale` and `system_locales` read ur-PK.
    - The face editor that `choose.sh` drives is found by its English labels. So each style was chosen in English, the language set to Urdu, the shots taken, and English set again (`urdu-shots-system-ur.sh`).
- **The control: the shaping language takes effect** (`urdu/control-three.png`, `urdu/sheet-control.png`, `urdu/verdict-control.txt`). The face from before plan 2 Task 2 was used: the APK found installed on the emulator, whose `li.xml` is `ff28cd5a`'s and whose fonts are the ones before v2. It was shot with the same Urdu feed at 16:47 on Arch:
  - **watch in English:** every ۴ ۶ ۷ is Persian;
  - **runtime per-app language Urdu:** Persian, the same as in English;
  - **system language Urdu:** the SemiBold's ۶ (the Hijri date, Maghrib's time) become Urdu forms through its own `locl` URD. The Light clock stays Persian, since the old Light maps those digits to `pnum` glyphs that no `locl` reaches.

  That is call 2's premise, seen on the runtime: it shapes in the watch's **system** language. So the Urdu-watch shots are real Urdu-watch shots.
- **The verdict, measured.** `urdu/glyphs.py` draws each digit with Pillow at each line's size, in the face's v2 fonts (Urdu forms) and in `ff28cd5a`'s fonts (Persian forms), both with no language, so each draws what its cmap names.
  - The sizes are the generators' (K = 2.25): the clock Light 126 u on Arch and 135 u on Ring and Sun; the prayer time SemiBold 30.375 u; the Hijri date and the countdown SemiBold 28.125 u; on the Moon, SemiBold 27 u and the prayer time Bold 29.25 u.
  - Each glyph is matched anywhere in the line's box, by normalized cross-correlation, within 6% of the size and, on the Moon's curves, turned by up to ±40°.
  - Checked first on P2.2's committed old-and-new frames (`urdu-digits-old-new.png`): its base face scores Persian (0.99 against 0.50) and its v2 face scores Urdu (0.99 against 0.50).
  - **Every ۴, ۶ and ۷ in all 16 shots is the Urdu form, under the English and the Urdu watch alike** (`urdu/verdict-watch-en.txt`, `urdu/verdict-watch-ur.txt`: 23 lines and 33 digits each). Urdu scores 0.959–0.993 against Persian 0.340–0.713, the narrowest margin 0.25 (the Moon's ۴ in its countdown).
  - Pictures:
    - `urdu/sheet-watch-en.png` and `urdu/sheet-watch-ur.png`: each line at 3x, beside both fonts' drawings of its text at its size;
    - `urdu/glyphs-urdu-persian.png`: ۴ ۶ ۷ in both forms at each size.
  - The Urdu watch's shots equal the English watch's in 0 px on Arch and Ring (the Moon in all but its seconds hand, the Sun within 1 level). So the forms do not depend on the watch's language. **No Persian form was found, so nothing is handed to the Lead.**
- **Then** (Step 6's last line): `tools/watchface/feed.sh` (the sample again) and `tools/watchface/clock.sh auto`.

### The errata, and how each was resolved

1. **Only Step 4's pass and Step 6 were left.** P2.3a's steps were not redone: no code, test, vector or fixture changed. The Urdu shots show Urdu's digit forms (above).
2. **`emulator-5558` is shared.**
   - The `wear-5558` lock (`~/.cache/taqwa-locks`) was held from 12:21 to 13:38 for all of it: the pass, the A/B, the shots and the control. Its owner file named the lane, the task and the steps, and it was released with `rm -r`.
   - Before taking the lock, the emulator's state was recorded and the APKs and stored feed pulled (`state.sh`, `device-state.txt`): watch app `40a5cadf`, face `7ae6b630` (`ff28cd5a`'s face), feed `0aa48771`, Arch, Amber, slots empty, English, automatic time and zone, Africa/Johannesburg, font 1.0, TalkBack off, battery 100.
   - The emulator was put back with P2.2's `restore.sh` on those files. Both APKs and the feed are byte-equal again, and every recorded setting reads as found, with **one exception, which the Lead should know about**: `persist.sys.locale` was empty and now reads `en-US`.
     - The Urdu round trip has to write that property, and the plan's own Settings route would have written it too. Only root can empty it.
     - It is the system default either way (`ro.product.locale` is `en-US`). `system_locales` was put back to null (`settings delete`).
   - The console answered throughout, with no hang and no crash, and nothing was stopped or rebooted. Installs used `adb -s emulator-5558 install -r` only.
   - One slip: `choose.sh` once ran while Settings was still open (after the language screen). It opened Settings › System › Input methods, then stopped because it found no editor. Nothing was toggled: Smart Reply reads checked, the default, and no setting changed. Settings was closed and the run went on.
3. **The Gradle flake:** see Verification below.

### For the Lead

- Plan 2 Task 3 Step 6, and every later step that says "set the watch's language in the emulator's Settings", cannot be done that way on Wear OS 6 without a paired phone. Once P2.7 has paired the emulators, the phone's language (which the watch follows) is the plan's route. Without a pairing, `urdu/SetLocale.java` is the call Settings would make.
- The emulator's resting face (the lock rule's "plan 1's state") is still `ff28cd5a`'s face from before P2.2: an Urdu feed shows Persian forms on it until the face as committed is installed.
- `install.sh` and `activate.sh`, which Step 6 names, were not run: `install.sh` uses Gradle's `installDebug`, and the face was already the active one.

### Verification (P2.3b)

- `tools/watchface/store-test.sh`: PASS, 11 ok; 12 ok after review round 1's fix, which changed only the test; 13 ok after review round 2's, also the test alone, and 0 FAIL in 240 runs under parallel load.
- `scripts/test.sh`: exit 0 on the first run, with no EOFException and no `EngineBudgetBenchmarkTest` failure. The counts: check-versions and test-bump-version PASS; `:shared` 2,173 host and 2,115 iOS; `:widgetcore` 111 and 109; `:watchcore` 119 and 117; `:wearApp` 143; 0 failures, 0 skipped.
  - The host suites came from the cache, which is valid because no Gradle input changed (tools and docs only). So they were run again with `--rerun`: the same counts, 0 failures.
- `git diff --check` is clean. The evidence files' trailing spaces were trimmed.
