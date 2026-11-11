# Watch companion 1: the Taqwa face on a Galaxy watch, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** the Taqwa face runs on a Wear OS 6 emulator, drawn from the Taqwa watch app's own complication data sources over a feed file. That means all four styles, four colours, the user's slots, always-on, English and Arabic, and 12 and 24 hours. In this plan the feed is a bundled sample; plan 2 makes the phone send it.

**Architecture:**
- **`:watchcore`** (new KMP module, Compose-free): the feed's model, its JSON, and the rules every watch surface applies (next, previous, progress, countdown wording, which prayer is lit). It is unit-tested, and writes JSON vectors that the watchOS port runs in plan 4.
- **`:wearApp`** (new Android app, same id as the phone): stores the feed and serves it as complications.
- **`:watchface`** (new resource-only Watch Face Format APK): holds the four styles and reads the complications through fixed slots.

**Tech stack:**
- Kotlin 2.4.10 and AGP 9.4.0 (built-in Kotlin for app modules);
- kotlinx-serialization-json 1.11.0;
- `androidx.wear.watchface:watchface-complications-data-source-ktx`;
- `androidx.wear.protolayout:protolayout-expression`, for dynamic values;
- Watch Face Format version 2;
- the Wear OS 6.0 emulator (`system-images;android-36;android-wear-signed;arm64-v8a`, installed 19 October).

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`. Drawings: `docs/superpowers/specs/2026-10-19-watch-companion/faces.js` (every proportion, colour and size; the 200-unit drawings scale ×2.25 to WFF's 450). Mockups: `docs/superpowers/specs/2026-10-19-watch-companion/mockups.html`.

**The series:** this is plan 1 of 5. Plans 2–5 are written when the one before them ships.
1. The Taqwa face on a Galaxy, emulator first (this plan).
2. The feed from the phones: the Android and iPhone senders, the Wear receiver, and the refresh request.
3. The Galaxy watch app: the Tile, Today, Tasbeeh with the bezel and undo, and Qibla.
4. The Apple Watch: the watchOS app, complications, Smart Stack, the three screens, and face sharing.
5. Ship: Watch Face Push install and activation, the phone rows, strings in seven languages, docs, privacy, version codes, his two watches, and the stores.

## Global Constraints

- **Package for `:wearApp`:** `world.taqwa.app` (the phone's; the Data Layer requires it).
- **Package for the face:** `world.taqwa.app.watchfacepush.taqwa`, with `android:hasCode="false"`.
- **minSdk:** `:wearApp` 30, `:watchface` 33.
- **Permissions:** `:wearApp` declares **no `INTERNET` and no location permission**. This plan needs none at all.
- **Face signing:** with a throwaway test key (Mohamed approved it on 19 October) at `~/.android/taqwa-face-debug.keystore`, kept out of the repository. Never use the app's key for the face. Agents never touch release keys.
- **Module dependencies:** `:watchcore` has no dependency beyond the Kotlin standard library and kotlinx-serialization-json. It is never linked by the iPhone widget extension. `:widgetcore` stays dependency-free.
- **No time is computed on the watch.** Every instant comes from the feed. The face reads only Taqwa's slots and its own clock.
- **The countdown text:**
  - English: `in %1$s h %2$s min`, `in %1$s h`, `in %1$s min`, `in under a minute`.
  - Arabic: `بعد %1$s س %2$s د`, `بعد %1$s س`, `بعد %1$s د`, `بعد أقل من دقيقة`.
  - Numbers are in the feed's digits.
  - Minutes left = floor((next − now) / 60 s), as the phone's widgets count.
- **Colours:** OLED black `#000000`, ink `#F1F3F1`, mist `#8C948F`, slate `#5A625D`, hairline `#232825`. Accents: Amber `#F0B429` (default, first), Pearl `#E9E2D2`, Sage `#93BFA3`, Night `#9FB3EC`. Sun's daylight, sky and sun stay `#F0B429` whatever the accent.
- **Styles:** Arch (default), Ring, Sun, Moon.
- **Slots:** the user's own are two on Arch, Ring and Sun, and one on Moon, empty by default. Task 10 built that. On 23 October Mohamed chose two along the bottom on Moon as well, its lower half re-laid when one is filled (spec 3.4, option (d)). Task 11 built it.
- **Fonts:** Manrope (OFL) for Latin and Western digits; Noto Sans Arabic (OFL) for Arabic and Arabic-Indic digits. Both are bundled, with their licence texts recorded in `docs/licences/`.
- **Commits** carry no `Co-Authored-By`. Nothing is pushed.
- **Emulators:**
  - The Wear emulator runs on port **5558** (the Lead's phone is 5556, Dev 2's is 5560).
  - Stop it only with `adb -s emulator-5558 emu kill`. Never use `pkill`.

## Review Focus

1. **Far-north days:** a previous day's Maghrib or Isha that falls after the next day's Fajr is not a start. The rules must skip it, as `TimelineBuilder` does (Task 2, `aLateIshaAfterTheNextFajrIsNotAStart`).
2. **The minute boundary:** exactly 72 minutes left reads "in 1 h 12 min", and 71 min 59 s reads "in 1 h 11 min". The face, the complications and the vectors must agree (Task 2, `countdownFloorsToTheMinute`).
3. **A feed that has run out:** past the last start, the sources return no data. The face shows its empty state rather than a stale or negative countdown (Task 2, `theMomentIsNullPastTheLastStart`; Task 5, `aSpentFeedServesTheEmptyForm`).
4. **The day boundary:** the hours from Isha to the next Fajr fill the ring. Previous is the day before's Isha, and there is no zero-length period at midnight (Task 2, `afterIshaThePeriodRunsToTomorrowsFajr`, `beforeFajrThePeriodStartsAtYesterdaysIsha`).
5. **The phone's own digits** in a countdown: "بعد ١ س ١٢ د" in Arabic-Indic, ১ and ১২ in Bengali, never mixed digit sets (Task 2, `arabicIndicDigitsInTheCountdown`, `bengaliDigitsInTheCountdown`).

---

### Task 1: `:watchcore`: the feed model and its JSON

**Files:**
- Create: `watchcore/build.gradle.kts`, `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchFeed.kt`, `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchFeedJson.kt`
- Modify: `settings.gradle.kts` (add `include(":watchcore")`)
- Test: `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchFeedJsonTest.kt`

**Interfaces:**
- Produces, package `world.taqwa.app.watch`, all `@Serializable`:
  - `WatchFeed(version: Int, writtenAt: Long, days: List<WatchDay>, place: WatchPlace, ui: WatchUi, tasbeeh: List<WatchPhrase> = emptyList())`
  - `WatchDay(date: String, times: WatchTimes, clock: WatchSix<String>, names: WatchSix<String>, hijri: String, gregorian: String, weekday: String, dayNumber: String)`. Here `date` is ISO `2026-10-18`, `names` already says Jumuʿah on a Friday, and `gregorian` is the face's form, "SUN 18 OCT" in Latin.
  - `WatchTimes(fajr: Long, sunrise: Long, dhuhr: Long, asr: Long, maghrib: Long, isha: Long, fajrEnd: Long? = null, asrEnd: Long? = null)`, in epoch seconds.
  - `WatchSix<T>(fajr: T, sunrise: T, dhuhr: T, asr: T, maghrib: T, isha: T)`
  - `WatchPlace(city: String, qiblaBearing: Double, qiblaKm: String, declination: Double, southern: Boolean)`
  - `WatchUi(language: String, rtl: Boolean, digitZero: Char = '0', twelveHour: Boolean, am: String, pm: String, countdown: WatchCountdownPhrases, nextIn: String)`
  - `WatchCountdownPhrases(hoursMinutes: String, hours: String, minutes: String, underAMinute: String)`
  - `WatchPhrase(id: String, phrase: String, target: Int)`
- Produces: `object WatchFeedJson { const val VERSION = 1; fun encode(feed: WatchFeed): String; fun decode(json: String): WatchFeed? }`. `decode` returns null on malformed JSON, and on a feed whose `version` is greater than `VERSION`. Unknown keys are ignored.

- [ ] **Step 1: Write the failing tests:**
  - `roundTripKeepsEveryField`: encode, then decode, equals the original.
  - `unknownKeysAreIgnored`: a JSON document with an extra `"future": 1` at the top and inside a day still decodes.
  - `aNewerVersionDecodesToNull`: `"version": 2`.
  - `malformedJsonDecodesToNull`.
  - `missingEndsDecodeAsNull`: a polar day.
- [ ] **Step 2:** Run `./gradlew :watchcore:allTests`. Expected: FAIL, unresolved `WatchFeedJson`.
- [ ] **Step 3: Create the module.**
  - The plugins are `kotlinMultiplatform`, `androidKotlinMultiplatformLibrary` and `kotlinSerialization`. The targets mirror `widgetcore` (android with `withHostTest {}`, `iosArm64`, `iosSimulatorArm64`; framework `baseName = "watchcore"`, static).
  - `commonMain` depends on `libs.kotlinx.serialization.json` only.
  - Implement the model and `WatchFeedJson` with `Json { ignoreUnknownKeys = true; encodeDefaults = true }`.
- [ ] **Step 4:** Run `./gradlew :watchcore:allTests`. Expected: PASS, on the android host and iosSimulatorArm64.
- [ ] **Step 5: Commit** `watchcore/` and `settings.gradle.kts` with the message "Watch core: the feed the phone will send the watch, and its JSON".

### Task 2: `:watchcore`: the rules every watch surface applies

**Files:**
- Create: `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchRules.kt`, `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchRulesTest.kt`, `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/Fixtures.kt`

**Interfaces:**
- Consumes: Task 1's model.
- Produces:
  - `enum class WatchPrayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }`. These are the starts; Sunrise is not one.
  - `data class WatchStart(prayer: WatchPrayer, at: Long, dayIndex: Int)`
  - `data class WatchMoment(previous: WatchStart, next: WatchStart, secondsLeft: Long, progress: Float)`
  - `object WatchRules`, with:
    - `starts(feed: WatchFeed): List<WatchStart>`
    - `moment(feed: WatchFeed, now: Long): WatchMoment?`
    - `countdown(feed: WatchFeed, secondsLeft: Long): String`
    - `digits(feed: WatchFeed, n: Long): String`
    - `litOnList(feed: WatchFeed, now: Long): WatchPrayer?`: the Prayer screen's rule.
    - `litOnGlance(feed: WatchFeed, now: Long): WatchPrayer?`: the widgets' rule.
    - `needsRefresh(feed: WatchFeed, now: Long): Boolean`: true when the last start is less than 3 days away, or `writtenAt` is more than 7 days old.
- Produces: `object Fixtures { fun capeTown(days: Int = 30, ui: WatchUi = english24()): WatchFeed; fun english24(); fun english12(); fun arabicLibya(); fun arabicSaudi(); fun tromsoMay(): WatchFeed }`.
  - This lives in commonTest, and is the same data as the mockups.
  - Day 0 is Sun 18 Oct 2026 in Cape Town, UTC+2: Fajr 04:33, Sunrise 06:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24. The ends are 06:01 and 19:02.
  - Later days repeat the clock times on their own dates. Dates and weekdays advance; the Hijri day advances from 6 Jumada al-Ula. Day 5, Fri 23 Oct, names Dhuhr "Jumuʿah".

- [ ] **Step 1: Write the failing tests.** `now` is local Cape Town time, given as epoch seconds.
  - `atTwoFiftyEightTheNextIsAsr`: at 14:58:00 day 0, next is ASR at 16:10, previous is DHUHR at 12:33, `secondsLeft` is 4320, and progress is 145/217, to within 1e-4.
  - `countdownFloorsToTheMinute`: `countdown(feed, 4320)` is `in 1 h 12 min`; `countdown(feed, 4319)` is `in 1 h 11 min`; `countdown(feed, 3600)` is `in 1 h`; `countdown(feed, 720)` is `in 12 min`; `countdown(feed, 59)` is `in under a minute`.
  - `arabicLibyaCountdown`: `بعد 1 س 12 د`.
  - `arabicIndicDigitsInTheCountdown`: `بعد ١ س ١٢ د`.
  - `afterIshaThePeriodRunsToTomorrowsFajr`: at 22:30 day 0, the next is day 1's FAJR and the previous is day 0's ISHA.
  - `beforeFajrThePeriodStartsAtYesterdaysIsha`: at 04:10 day 1, the previous is day 0's ISHA and the next is day 1's FAJR.
  - `aLateIshaAfterTheNextFajrIsNotAStart`: `tromsoMay()`, where day 0's Isha falls at 00:41 the next date and day 1's Fajr at 00:38. `starts` does not contain day 0's ISHA.
  - `theMomentIsNullPastTheLastStart`.
  - `litOnListDropsFajrAtSunrise`: at 06:40, null.
  - `litOnListDropsAsrAtSunset`: at 19:03, null.
  - `litOnListBeforeFajrIsNull`: at 04:10, null.
  - `litOnGlanceKeepsFajrUntilDhuhr`: at 06:40, FAJR.
  - `litOnGlanceBeforeFajrIsNull`.
  - `aPolarDayWithoutEndsKeepsThePlainRule`: ends are null, and at 06:40 `litOnList` is FAJR.
  - `needsRefreshThreeDaysBeforeTheEnd`.
  - `needsRefreshAWeekAfterWriting`.
- [ ] **Step 2:** Run `./gradlew :watchcore:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement `WatchRules`.**
  - `moment`, `litOnList` and `litOnGlance` follow `TimelineBuilder.build`/`buildForWidgets` exactly (Ruling 10). For the day `now` is on (by its `midnight`), the candidates are today's five starts, then yesterday's starts that fall before today's Fajr (`yesterdaysStarts`), then `firstOfTomorrowAfter`. Previous falls back to yesterday's Isha. A start after the next day's Fajr counts until midnight, then not. `starts` stays a flat list for callers that want one.
  - `moment`: the next start is the first candidate after `now`; the previous is the last at or before `now`; progress is `elapsed / total`, clamped to 0..1. If there is no next start, it returns null.
  - `digits` maps 0–9 from `ui.digitZero` (Ruling 9). Each `WatchDay.midnight` sets the day boundary (Ruling 8).
  - `countdown` fills `ui.countdown`'s phrases with `digits`.
- [ ] **Step 4:** Run `./gradlew :watchcore:allTests`. Expected: PASS.
- [ ] **Step 5: Write `vectorsAreUpToDate`.**
  - Add `watchcore/src/androidHostTest/kotlin/world/taqwa/app/watch/VectorsTest.kt`. It writes `watchcore/vectors/watch-rules-vectors.json`: each case's feed, `now`, and the expected next, previous, secondsLeft, progress, countdown, litOnList and litOnGlance.
  - With `-Ptaqwa.updateVectors` it rewrites the file. Otherwise it fails if the file differs.
  - Generate the file, and commit it. The watchOS port in plan 4 runs it.
- [ ] **Step 6: Commit** with the message "Watch core: the rules each watch surface applies to the feed, restated from TimelineBuilder, with vectors for the watchOS port".

### Task 3: `:wearApp`: the module, the feed store and the sample feed

**Files:**
- Create:
  - `wearApp/build.gradle.kts`, `wearApp/src/main/AndroidManifest.xml`;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/FeedStore.kt`;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/TaqwaWearApp.kt` (the `Application`);
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/SampleFeed.kt`, `wearApp/src/debug/assets/sample-feed.json`;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/StatusActivity.kt`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/FeedStoreTest.kt`.
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml` (the Wear libraries, latest stable).

**Interfaces:**
- Consumes: `WatchFeedJson`.
- Produces:
  - `class FeedStore(file: File)`, with:
    - `fun read(): WatchFeed?`
    - `fun write(json: String): FeedWriteResult`, which is `ACCEPTED`, `NEWER_VERSION` or `MALFORMED` (Ruling 6), from `WatchFeedJson.decode` and `versionOf`. It is atomic: write to a temp file, then rename. A newer version keeps the last feed, and a persisted `feed.json.newer` notice outlives the process (Ruling 13).
    - `val changes: StateFlow<WatchFeed?>`, plus an `onStored` callback run on every accepted write. A StateFlow swallows an identical feed, so `onStored` is what asks the sources to update (Ruling 20).
  - `TaqwaWearApp.feedStore`
- Produces: the debug-only `SampleFeed.installIfEmpty(store)`, which reads `assets/sample-feed.json`. That file is `watchcore/vectors/sample-feed-capetown.json` (`Fixtures.capeTownSample()`, 31 days from Sat 17 Oct 2026, Ruling 7), re-dated by whole days so the mock day (Sun 18 Oct) lands on the install day. Each day's times and its `midnight` shift together. A debug broadcast `world.taqwa.app.wear.DEBUG_FEED` with an `epoch` extra re-dates it, for screenshots at 14:58.

- [ ] **Step 1: Write the failing tests:**
  - `writeThenReadReturnsTheFeed`;
  - `aRejectedFeedLeavesTheOldOneInPlace`;
  - `writeIsAtomic`: a crash-simulated temp file is ignored on read.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Create the module.**
  - The plugins are `androidApplication` and `composeCompiler`.
  - `applicationId "world.taqwa.app"`, `minSdk 30`, `targetSdk` from the catalog.
  - The manifest has:
    - `<uses-feature android:name="android.hardware.type.watch"/>`;
    - meta-data `com.google.android.wearable.standalone=false`;
    - **no `INTERNET`**.
  - `StatusActivity` (Compose for Wear OS) shows "Open Taqwa on your phone" when there is no feed, and the city and `writtenAt` in debug builds when there is.
  - Implement `FeedStore`.
- [ ] **Step 4:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: PASS. Then `./gradlew :wearApp:assembleDebug`. Expected: an APK, and the merged manifest lists no `INTERNET` and no location permission.
- [ ] **Step 5: Commit** with the message "Wear: the Taqwa watch app's module, its feed store, and a sample feed for debug builds".

### Task 4: the Wear emulator, and the spike that settles the countdown's mechanism

**Files:**
- Create: `scripts/wear-emulator.sh` (create and start AVD `Taqwa_Wear_OS_6` on port 5558, then wait for boot), `docs/superpowers/reports/2026-10-19-wear-spike.md`.

This task settles spec 2.3's open choice, how the countdown updates every minute, on the emulator before Task 5 builds on it.

- [ ] **Step 1: Start the emulator.** `scripts/wear-emulator.sh` uses the device profile `wearos_large_round` and the image named in the tech stack. Expected: `adb -s emulator-5558 shell getprop ro.build.version.sdk` prints 36.
- [ ] **Step 2: Build a throwaway source.** There is no spike branch: the spike stays uncommitted and is deleted before the task's commit (Ruling 3). One `RANGED_VALUE` source carrying a **dynamic value**: `DynamicInstant` arithmetic for the progress, and a `DynamicString` countdown built from the feed's phrases. Install `:wearApp`, and put it on the emulator's default face in a ranged slot.
- [ ] **Step 3: Observe for 3 minutes** with `adb -s emulator-5558 exec-out screencap -p`, one screenshot a minute.
  - Pass: the ring and the text move every minute with the app not running (check `pidof`).
  - Also record:
    - whether Arabic-Indic digits come out of `DynamicInt32.format()` under an Arabic feed;
    - whether a permission prompt appears for complication data.
- [ ] **Step 4: Write the report:**
  - **If dynamic values pass:** the mechanism is dynamic values, with digits mapped in our code if `format()` can't.
  - **If they fail:** the fallback is `ComplicationDataTimeline` with one entry a minute for the next 120 minutes, plus a re-request every 60 minutes. Record that it was chosen.
  - Delete the spike source.
- [ ] **Step 5: Commit** the script and the report, with the message "Wear: the emulator script, and the spike that settles how the countdown moves each minute".

### Task 5: the complication data sources

**Files:**
- Create:
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/ComplicationFields.kt` (pure);
  - `.../complication/NextPrayerSource.kt`, `.../complication/DaySource.kt`, `.../complication/DatesSource.kt` and `.../complication/MarkSource.kt`. The planned `ArchFillSource.kt` was dropped by Ruling 16, before it was built: the Arch lights from slot A's own progress. Task 8 added `.../complication/DayDialSource.kt`;
  - `wearApp/src/main/res/drawable/ic_taqwa_arch.xml` (the icon's arch path from `androidApp/src/main/res/drawable/ic_launcher_monochrome.xml`);
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/complication/ComplicationFieldsTest.kt`.
- Modify: `wearApp/src/main/AndroidManifest.xml`, adding the services. Each declares its supported types. The update period is `21600` (Ruling 14, which replaced the plan's `0`). A timeline holds about two days, so a six-hourly request keeps it ahead, and every stored feed calls `requestUpdateAll()`.

**Interfaces:**
- Consumes: `WatchRules`, `FeedStore`, and Task 4's report (the mechanism).
- Produces: `object ComplicationFields`, with:
  - `nextPrayer(feed: WatchFeed, now: Long): NextPrayerFields?`, where `NextPrayerFields` is `(label: String, time: String, countdown: String, progress: Float, startAt: Long, endAt: Long, rtl: Boolean, twelveHour: Boolean)`. The label is the name in Latin capitals ("ASR"), or the Arabic name as is.
  - `day(feed: WatchFeed, now: Long): DayFields?`, where `DayFields` is `(weights: List<Float>, colors: List<Int>)`. There are seven stretches since Task 8 (Ruling 21): midnight→Fajr→Sunrise→Dhuhr→Asr→Maghrib→Isha→midnight, in minutes from where the date begins (`WatchRules.dayStart`). `WeightedElements` allows at most 7. The colours are hairline, half gold for twilight, and gold at 34% for daylight. A face cannot read one weight or recolour one stretch, so the Sun lights its period from slot A and slot 7 (Task 8).
  - `dates(feed: WatchFeed, now: Long): DatesFields?`, where `DatesFields` is `(hijri: String, gregorian: String, weekday: String, dayNumber: String, hijriDayMonth: String)`. `hijriDayMonth` (Task 10, Ruling 24) is what the face draws, the day and month with no year. It falls back to `hijri` when the feed has none.
  - ~~`archFill(progress: Float): Float`~~: dropped by Ruling 16 before it was built. The Arch lights from slot A's own progress (Task 7, Ruling 32).
  - `moonLit(epochSeconds: Long, southern: Boolean): MoonShape`, where `MoonShape` is `(litFraction: Float, waxing: Boolean, litOnLeft: Boolean)`. The moon's age comes from the mean synodic month (29.530588853 days) counted from the new moon of 6 January 2000 18:14 UTC. Waxing in the south means lit on the left.
  - `moonImage(shape: MoonShape, sizePx: Int): Bitmap`: the lit part only, white on transparent, for a monochromatic image. This replaces the spec's `[MOON_PHASE_POSITION]` images; the face tints it. *This is the builder's call recorded here: it needs no hemisphere slot and no 58 face images.*
- Produces: component names that the face's fixed slots name:
  - `world.taqwa.app/world.taqwa.app.wear.complication.NextPrayerSource`, offering `RANGED_VALUE`, `SHORT_TEXT` and `LONG_TEXT`. Since Task 8 (Ruling 30), the `RANGED_VALUE` range is the period in minutes from the date's Dhuhr: min is the start, max is the end, and the value is now, dynamic. The share is the same as 0..1;
  - `.../DaySource`, offering `WEIGHTED_ELEMENTS`;
  - `.../DatesSource`, offering `LONG_TEXT`, with the Hijri day and month in TITLE and the Gregorian in TEXT, and `SHORT_TEXT`, with the weekday in TITLE, the day number in TEXT and `moonImage` as its MONOCHROMATIC_IMAGE;
  - `.../MarkSource`, offering `MONOCHROMATIC_IMAGE`;
  - ~~`.../ArchFillSource`~~: dropped by Ruling 16;
  - `.../DayDialSource` (added by Task 8, fix round 1, Ruling 31), offering `RANGED_VALUE` only and **hidden from every picker** (`METADATA_KEY_HIDDEN`). It carries the Sun's dial geometry and, in its title and text, the face's settings (Ruling 27). It serves the Taqwa face's fixed slot 7 alone.

  Each serves a timeline of dynamic values with one entry per prayer period (Ruling 17). A period is split where the date turns over inside it (Ruling 28 of the close). Every timeline ends in no data, except the dial's, which ends in its settings alone (Ruling 29). No source lists a safe watch face. No permission prompt appeared (Ruling 15), and `SAFE_WATCH_FACE_SUPPORTED_TYPES` needs a signature|privileged permission (Ruling 31).

- [ ] **Step 1: Write the failing tests** on `Fixtures.capeTown()` at 14:58 day 0:
  - `nextPrayerAtTwoFiftyEight`: ASR, 16:10, "in 1 h 12 min", progress 145/217;
  - `nextPrayerArabicKeepsTheName`: العصر;
  - `dayWeightsSumToADay`: 1440 minutes; seven stretches since Task 8 (`dayWeightsAreTheSevenStretchesOfTheDayInMinutes`, Ruling 21);
  - `datesOnAFriday`: day 5 shows "FRI 23 OCT";
  - `aSpentFeedServesTheEmptyForm`: past the last start, all three return null;
  - ~~`archFillRunsFromApexToFeet`~~: dropped with `archFill` (Ruling 16);
  - `moonOnTheEighteenthIsWaxingNearHalf`: at 14:58 day 0, `litFraction` is within 0.45–0.52 (Ruling 18: the real moon was 0.49, by Meeus's periodic terms), `waxing` is true, and `litOnLeft` is true for Cape Town and false for `southern = false`.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement `ComplicationFields`, then the services.**
  - Each service maps its fields to `ComplicationData` with Task 4's mechanism. A null maps to `NoDataComplicationData`.
  - A computation that throws is logged and answers no data, so the face shows its empty form (`answerOrNothing`, added at the final review).
  - `FeedStore`'s `onStored` triggers `ComplicationDataSourceUpdateRequester.requestUpdateAll()` (Ruling 20).
- [ ] **Step 4:** Run the tests. Expected: PASS. Then check on the emulator: install, and put NextPrayer and Dates on the default face. Expected: the screenshot shows "ASR 16:10" and the ring after `adb -s emulator-5558 shell am broadcast -a world.taqwa.app.wear.DEBUG_FEED --el epoch <14:58 today>`.
- [ ] **Step 5: Commit** with the message "Wear: the complication sources the Taqwa face and anyone's face can show".

### Task 6: `:watchface`: the face module, its test key, and the Ring style

**Files:**
- Create:
  - `watchface/build.gradle.kts`, `watchface/src/main/AndroidManifest.xml`;
  - `watchface/src/main/res/raw/watchface.xml`, `watchface/src/main/res/xml/watch_face_info.xml`;
  - `watchface/src/main/res/font/manrope_*.ttf`, `watchface/src/main/res/font/noto_sans_arabic_*.ttf`;
  - `watchface/src/main/res/drawable/preview.png`, `watchface/src/main/res/values/strings.xml`;
  - `scripts/face-debug-key.sh`;
  - `docs/licences/` entries for the two fonts.
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: Task 5's component names and types.
- Produces: the face's slots, as plan 1 left them (spec 3.3 has the full table):
  - slot 0 is A (NextPrayer, `RANGED_VALUE`);
  - slot 1 is B (Day, `WEIGHTED_ELEMENTS`);
  - slot 2 is C (Dates, `LONG_TEXT`);
  - slot 3 is C's short form (`SHORT_TEXT`, for Moon, with the moon image);
  - slots 4 and 5 are the user's own, D and E, `isCustomizable="TRUE"`, empty by default;
  - slot 6 is NextPrayer's `LONG_TEXT`, the countdown (Ruling 19). It was first planned as the Arch fill, which Ruling 16 dropped. A ranged value's text is its clock time, so the countdown needs its own slot;
  - slot 7 is DayDialSource's `RANGED_VALUE` (added by Task 8). It holds the Sun's dial geometry, and its title and text carry the face's settings (Ruling 27).

  That is all eight allowed. Slots 0–3, 6 and 7 are `isCustomizable="FALSE"`, with `DefaultProviderPolicy primaryProvider` set to Task 5's components. Their system fallback is `EMPTY` (Task 10). They are declared in the order 7, 0, 1, 2, 3, 6, 4, 5, because a tap goes to the last slot whose box holds it (Task 10), and a screen reader is given slot 0, the prayer, before slot 2, the dates (Task 12, Ruling 42). Slots 0, 1, 2, 6 and 7 spanned the face until Task 12, because a complication draws only inside its own box (Task 9). Since Task 12 only slots 0 and 2 span it, each a row or a column short (450 × 449 and 449 × 450), and slots 1, 3, 6 and 7 have 0 by 0 boxes, the scene drawing the Sun's dial, the Moon's windows and the countdown from their data, so that a screen reader is given one item for each box and no repeats (spec 3.3 and 3.6).
- Produces: the `UserConfigurations`:
  - a `ListConfiguration id="style"` with `arch` as the default, then `ring`, `sun` and `moon`;
  - a `ColorConfiguration id="accent"` with `amber` as the default, then `pearl`, `sage` and `night`.
  Later tasks fill in the other styles.

- [ ] **Step 1: Create the test key.** `scripts/face-debug-key.sh` creates `~/.android/taqwa-face-debug.keystore` with `keytool`: alias `taqwaface`, store and key password `android`, CN `Taqwa face debug`. It never overwrites an existing file.
- [ ] **Step 2: Create the module.**
  - The `androidApplication` plugin, with no Kotlin sources.
  - `applicationId "world.taqwa.app.watchfacepush.taqwa"`, `minSdk 33`.
  - The debug `signingConfig` points at the key from Step 1. If the file is missing, it fails with "run scripts/face-debug-key.sh".
  - The manifest has `android:hasCode="false"`, the WFF format-version property `2`, and the watch face meta-data.
- [ ] **Step 3: Write the Ring style.** A `Group` shown when the style is `ring`. It follows `faces.js` `ringFace` with `centre` at ×2.25:
  - a 1.8 slate hairline track;
  - the 7.65 accent arc, from slot A's `RANGED_VALUE`;
  - the point of light;
  - the arch mark;
  - the five-line centre: the time from the face's clock, then the label and the time from slot A, the countdown from slot 6 (Ruling 19), and the two date lines from slot C. If WFF's `Font` can't ask for proportional hours and tabular minutes, keep Manrope's default figures. The minutes then shift once a minute, which is not a jitter.
  - the always-on frame, as the face header teaches (Ruling 25): `Variant mode="AMBIENT"` changes a Part's or a Group's alpha, never inside a Font or a Stroke (where it is ignored), and a thinner always-on stroke is a second PartDraw.
- [ ] **Step 4: Verify.** Install with `./gradlew :watchface:installDebug` (`ANDROID_SERIAL=emulator-5558`), choose "Taqwa" in the face picker, and run the debug feed at 14:58. Expected: the screenshot matches Ring in `mockups.html` at 14:58 (Amber, English, 24-hour). Then the always-on screenshot (`adb -s emulator-5558 shell input keyevent KEYCODE_SLEEP`, or the emulator's ambient toggle) matches the Ring always-on drawing.
- [ ] **Step 5: Commit** with the message "Face: the Taqwa face's module, its test key, and the Ring style".

### Task 7: the Arch style

**Files:** Modify: `watchface/src/main/res/raw/watchface.xml`. Create:
- `tools/face-arch.py`, which writes the lit arch's block with `--check`;
- `tools/face-art.py`, which draws the face's pictures as PNGs (`arch_track`, `arch_track_aod`, `arch_niche`, from `faces.js` `ARCH2` at ×2.25);
- the harness in `tools/watchface/`.

The plan's vector drawables `arch_lit.xml` and `arch_track.xml` were not used: Watch Face Push's validator refuses vector drawables.

**Interfaces:** Consumes: slots A and C.

- [ ] **Step 1: Write the Arch group** (`faces.js` `archFace`):
  - the slate track, kept in ambient;
  - the lit arch, which runs down both sides from the apex at once. **As built (Ruling 32):** four circular arcs a side (two biarcs, within 0.12 units of `faces.js`'s crown) and a foot line. Each sweeps `clamp(p × length − its start, 0, its length)` of itself, from slot A's progress `p`. The planned `renderMode="MASK"` rectangle, driven by slot 6, draws a flat tent at 12:40 and slanted ends.
  - the Hijri line, in its own 240 px box with three sizes by the title's length: 27, 24 and 22.5 px (Ruling 33, checked by `tools/watchface/hijri-fit.py`);
  - the lamp dot and the niche glow (awake only);
  - the centre stack at 56 units (47 in 12-hour);
  - the always-on frame, in the face header's pattern (Ruling 25): Part- or Group-level alpha Variants, a second PartDraw for any thinner stroke, no Variant inside a Font or a Stroke.
- [ ] **Step 2: Verify** on the emulator at 14:58, 12:40 and 18:55, awake and always-on. Expected: matches Arch in `mockups.html` (the fill reaches the same height on both sides; the outline is visible at 12:40).
- [ ] **Step 3: Commit** with the message "Face: the Arch style, the app icon's arch lit from the crown".

### Task 8: the Sun style

**Files:** Modify: `watchface/src/main/res/raw/watchface.xml`, `wearApp/.../ComplicationFields.kt` (`dial`, `packDial`, the seven stretches) and the wearApp manifest. Create: `tools/face-sun.py` (the Sun's block, with `--check`, reading `DIAL_BASE` and `DIAL_OFFSET` from `ComplicationFields.kt`), `wearApp/.../complication/DayDialSource.kt`, and `SourceManifestTest`.

**Interfaces:** Consumes: slot B (`WEIGHTED_ELEMENTS`), A, C, and the new slot 7. Produces:
- **Slot 7, `DayDialSource` (Ruling 30, Ruling 31).** It offers `RANGED_VALUE` only and is hidden from every picker by `METADATA_KEY_HIDDEN`. Its three numbers carry the date's midnight, Fajr, Sunrise, Asr, Maghrib and Isha in whole minutes from Dhuhr, two to a number: base 4096, plus 1440, in order. Each prayer is the instance the period starts or ends at.
- **Slot A in minutes (Ruling 30).** Slot A's range becomes the period in minutes from the date's Dhuhr.
- **Why both:** a face cannot read one weight out of slot B, and a text is never a number.

- [ ] **Step 1: Write the Sun group** (`faces.js` `sunFace`), drawn inside slot 7 and reading slots 0 and 1 across slots (since Task 12 the scene draws it, reading slot 7 across slots too, and slot 7 has no room):
  - the day ring as a `WeightedStroke` on a full `Arc` of slot B's seven stretches, rotated so Dhuhr sits at twelve. The rotation is slot 7's midnight in minutes from Dhuhr, a quarter of a degree a minute, not slot B's first weight, which a face cannot read;
  - the five marks, at slot 7's minutes, and the lit period from the previous start to now, from slot A's minutes. Every data-following switch is an alpha Transform, never a Condition (see the face header);
  - the sun disc and the sky glow by day, and the crescent by night, positioned by slot A's value, which is now in minutes from Dhuhr;
  - clockwise in every language;
  - the centre stack at 60 (55 in 12-hour);
  - the always-on frame, in the face header's pattern (Ruling 25): Part- or Group-level alpha Variants, a second PartDraw for any thinner stroke, no Variant inside a Font or a Stroke.
- [ ] **Step 2: Verify** at 14:58, 04:10 and 22:30, in Amber and Sage, awake and always-on. Expected: matches Sun in `mockups.html`; Sage leaves the daylight and the sun gold.
- [ ] **Step 3: Commit** with the message "Face: the Sun style, the day as a dial".

### Task 9: the Moon style

**Files:** Modify: `watchface/src/main/res/raw/watchface.xml`. Create:
- `tools/face-moon.py`, which writes the Moon's blocks with `--check`;
- the hands as PNGs drawn by `tools/face-art.py`: the dauphine facets and their keyline from `faces.js` `hand()`, the outlines (since Task 13, the hands' black backs), the seconds hand and the pointer.

The planned `hand_hour.xml` and `hand_minute.xml` were not made, because Push refuses vector drawables. Later, `tools/watchface/moon-fit.py` was added to measure the Moon's three curves.

**Interfaces:** Consumes: slots A, 3 (the weekday, the day number and the moon image) and D.

- [ ] **Step 1: Write the Moon group** (`faces.js` `moonFace`):
  - `AnalogClock` with dauphine hands as images, with their keyline, and a neutral seconds hand;
  - indices, and the arch at twelve;
  - the chapter arc from the hour hand to the next prayer, from slot A's start and end, capped at one turn;
  - the curved prayer and countdown in `TextCircular` over the top;
  - the date window, with the number at its outer end;
  - the moon window: a dark disc with slot 3's monochromatic image over it, tinted `#E8E2D2`;
  - the curved Hijri date;
  - one user slot at nine. It was built in Task 10 as option (a): slot 4's data is drawn at nine by the scene, because a slot cannot move by style (Ruling 26). Mohamed has since chosen option (d), so Task 11 replaces it;
  - the always-on frame, in the face header's pattern (Ruling 25). Task 9 built outline hands, a second set shown only always-on. Ruling 41 (24 October) replaced them: always-on the awake hands are dimmed by their own Variants, each on a black back of its own shape (Task 13);
  - never mirrored.
- [ ] **Step 2: Verify** at 10:10 and 14:58, in English and Arabic, awake and always-on. Expected: matches Moon in `mockups.html`; at 14:58 the day number shows beside the hour hand.
- [ ] **Step 3: Commit** with the message "Face: the Moon style, analog with the Hijri moon".

### Task 10: the user's slots, colours, 12-hour time, Arabic, and the full pass

**Files:** Modify:
- `watchface/src/main/res/raw/watchface.xml`;
- `wearApp/.../ComplicationFields.kt`: `faceSettings`, which carries the phone's settings in **slot 7's title and text** (Ruling 27). Slot A's numbers were the first carrier, but Task 8 took them for the minutes from Dhuhr;
- `watchcore` (`hijriDayMonth`, Ruling 24);
- `wearApp/src/debug/assets/`, with three more samples from `watchcore/vectors/`: English 12-hour, Libya's Arabic 12-hour, and Saudi Arabic.

Create:
- `tools/face-centre.py`, for the centre stack, Ring's ring and the wearer's slots, with `--check`;
- `tools/watchface/subset-noto.sh`, `slot-fit.py` and `window-fit.py`;
- `docs/superpowers/reports/2026-10-19-face-pass/`, for the screenshots.

- [ ] **Step 1: Add the user's slots.**
  - On Arch, Ring and Sun: two slots along the bottom (`faces.js` `userComps` positions).
  - On Moon: one at nine. As built, this is option (a), Ruling 26: slot 4's data is drawn at nine by the scene. WFF 2–5 cannot move a ComplicationSlot by style, so slot 4's box, its taps and its editor outline stay at the bottom. **Mohamed has since chosen option (d)**: two along the bottom, with the Moon's lower half re-laid when one is filled. Task 11 builds it.
  - All empty by default and customisable.
  - Declared after every fixed slot, each in a tight box of its own: slot 4 is (110, 368, 124 × 68) and slot 5 is (234, 368, 106 × 68). A tap goes to the last slot whose box holds it.
  - The layout moves up when a slot is filled (`LAYOUT_COMPS`).
  - A wearer's value is scaled to its room by an estimate from its characters (`slot-fit.py` checks about 136k values).
- [ ] **Step 2: The 12-hour time and RTL**, from **slot 7's title and text** (Ruling 27; spec 3.3 has the code). They are kept for as long as a feed is stored. With no feed, the face follows `[IS_24_HOUR_MODE]` and `[AMPM_STRING]`.
  - **12-hour:** the time follows slot 7's clock setting, with the day period top-aligned after the time in reading order. The clock is written in the phone's digits, digit by digit, with `subText()` from slot 7's text.
  - **RTL:** Ring's arc fills anticlockwise when slot 7's title says `R`. Arch, Sun and Moon never mirror. Right-to-left lines are written in the order they are seen, because the runtime lays a Text's runs out left to right.
  - **Fonts:** Arabic text and Arabic-Indic digits use Noto Sans Arabic, subset from 917 to 322 KB. Arabic `letterSpacing` is 0.
- [ ] **Step 3: The full pass** on the emulator, saved to `docs/superpowers/reports/2026-10-19-face-pass/`:
  - 4 styles × {awake, always-on} at 14:58, in English;
  - the 4 styles in Arabic (Libya) with 12-hour;
  - Ring in Arabic (Saudi);
  - each colour on Arch;
  - Arch with both user slots filled (battery, steps).
  - Expected: each matches its drawing in `mockups.html`, and nothing is clipped at 10:58 PM or 12:58 PM.
- [ ] **Step 4: Commit** with the message "Face: the user's slots, the four colours, 12-hour time and Arabic, with the emulator pass".

### Task 11: the Moon's two slots along the bottom (added 23 October, Mohamed's choice)

The Lead writes this task's brief after plan 1's close. It is built before plan 2 runs: an implementer, a two-lens review, then fixes.

- **What:** spec 3.4's Moon row, option (d). With a slot filled:
  - the moon window rises 11 px;
  - the Hijri curve tightens from a 160 to a 132 px radius, under the moon;
  - slots 4 and 5 curve between the five and seven o'clock indices, inside their own boxes;
  - nine o'clock is a plain index again, and the scene's ring at nine goes.
- **With both slots empty,** the Moon equals the face at 103f6aea in 0 px, apart from any fix the Lead rules for the Hijri curve (below).
- **Reference:** `moon-slot-choice.html` and its PNGs in the ledger folder, `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/`. That folder is git-ignored, so copy them beside the spec when the task starts. The page is a copy of `faces.js` `moonFace` with the change, and its own check is `#check`.
- **The Hijri curve:** this task redraws the curve whose right-to-left form drops letters at the close (spec 3.6). The Lead rules whether this task fixes it. He did (Ruling 37).

**As built (23 October):**
- `tools/face-moon.py` writes the second moon window (5 units higher) and the second Hijri curve (58.8 units, 60 degrees either side of six), each pair in Groups whose alpha Transforms read the slots' type across slots (`COMPS`), and `moon_slot()`, the Moon's drawing inside slots 4 and 5: the source's icon turned to its place and the value as a TextCircular along the bottom, 80 units out, scaled to its room about its anchor. `tools/face-centre.py` calls it for each of the slots' types; the scene's ring at nine is gone, and both slots ask for a short text first.
- Ruling 37: the runtime's `drawTextOnPath` places the glyphs past a run's characters by what lies past its advances, and Noto Sans Arabic shapes dotted letters into more glyphs than characters, as Noto Sans Bengali UI does an o-kar (`যোহর`, 4 and 5). Every Arabic-script run on the Moon's curves (the Hijri month, the prayer's name, the countdown) begins with twelve combining grapheme joiners (`PAD`), which take those places, and the left-to-right prayer's name and the wearer's curved values end with them (`PADDED`, after review round 1; spec 3.6).
- `tools/watchface/moon-fit.py` measures the tighter curve (calibrated on the emulator) and counts every curved run's glyphs, either way, against its `PAD`; `slot-fit.py` checks the Moon's two rooms; `ref.py face=moon-d` draws the reference page's Moon; `editor.py outlines` shows the editor's slot page; `pass.sh` shoots the Moon with both slots.
- Evidence: `docs/superpowers/reports/2026-10-19-face-pass/task-11/`; the report is `task-11-report.md` in the ledger folder.

### Task 12: the face speaks the essentials to TalkBack (added 23 October, Ruling 38)

At the close, TalkBack on emulator-5558 read the face as three items: one for every full-face slot (slot 0's "ISHA 20:24"), the clock, and slot 3's "SUN 18". The dates and the countdown were never spoken, against spec line 39 ("every screen gets real screen-reader text"). This task makes the face speak the essentials without changing anything drawn (Mohamed's rule: accessibility must not change the visible UI at default settings).

**Files:**
- Modify:
  - `wearApp/.../complication/ComplicationFields.kt`: the spoken texts, worked out from the feed alone;
  - `wearApp/.../complication/CountdownExpression.kt`: the spoken clock, a dynamic value like the countdown;
  - `wearApp/.../complication/NextPrayerSource.kt`, `DatesSource.kt`, `ComplicationSources.kt`;
  - `wearApp/src/main/AndroidManifest.xml` and `res/values/strings.xml`;
  - `watchface/src/main/res/raw/watchface.xml` (its header, and the slots the reader is given) and the generator that writes the slots it changes;
  - the tests: `ComplicationFieldsTest`, `CountdownExpressionTest`, `SourceManifestTest`, and `DynamicEvaluator` for any new kind of expression;
  - spec 3.6, and `tools/watchface/README.md`.
- Create:
  - `tools/watchface/talkback.sh`: TalkBack on and off on emulator-5558, and a walk through the face's items that records what TalkBack says;
  - `docs/superpowers/reports/2026-10-19-face-pass/task-12/`, for the evidence.

**Interfaces:**
- Consumes: the feed's words (`names`, `clock`, `countdown`, `hijri`, `gregorian`, `twelveHour`, `am`, `pm`, `digitZero`, `rtl`), and Task 4's dynamic-value mechanism.
- Produces: what the system's screen reader is given for the face, in the phone's words and digits, in this reading order:
  1. the time;
  2. the next prayer, its time and the countdown;
  3. the Hijri date and the Gregorian one;
  4. the wearer's slots, in their sources' own words.

  On a prayer face the prayer comes first, so slot 0 is declared before slot 2 (Ruling 42, 24 October, which withdrew Ruling 40's dates first). What a touch finds is heard on his Galaxy in plan 5's device pass, because on the emulator a touch reaches no item of any face.

- [ ] **Step 1: Research what reaches TalkBack.** Read the format's reference for versions 2 to 5 (the `ComplicationSlot` page and any screen-reader element it offers), and how `ComplicationData`'s `contentDescription` travels, in a timeline and as a dynamic value. Then find out, on emulator-5558 and in the runtime itself, which items the Wear OS 6 face runtime and the system UI make from the face, and which they leave out.
- [ ] **Step 2: Decide where each essential is carried.** Where the runtime reads every slot, each slot says its own part. Where it reads only some, the items it does read carry the missing words (Ruling 38). Nothing drawn may change, at any setting.
- [ ] **Step 3: Write the failing JVM tests** for the spoken texts the sources build, on `Fixtures.capeTown()`:
  - English on 24 hours;
  - English on 12 hours, with the day period's marker;
  - Arabic in Libya (Western digits, right to left);
  - Arabic in Saudi Arabia (Arabic-Indic digits): the digits are the feed's, so the reader says them as numbers;
  - the empty form past the feed's end.
- [ ] **Step 4:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 5: Implement the spoken texts and give them to the items the reader reads.**
  - Each prayer's name is said as the phone wrote it ("Dhuhr"), never in the face's capitals ("DHUHR"), which a speech engine may spell out.
  - A spoken part that changes with the clock (the countdown, the time) is a dynamic value, so it is never a minute stale.
- [ ] **Step 6:** Run the tests. Expected: PASS.
- [ ] **Step 7: Verify with TalkBack on emulator-5558.** Turn it on, swipe through every item and record what it says, from TalkBack's own log:
  - each of the four styles in English, with the wearer's slots empty and filled (battery, steps);
  - in Arabic (Libya on 12 hours, and Saudi Arabia's digits);
  - the drawn face unchanged: the pass's shots equal in 0 px, but for the seconds hand and the step count.
  - Turn TalkBack off at the end.
- [ ] **Step 8: Run the checks:** `:watchcore`, `:wearApp`, `:watchface` debug and release, both validators (`tools/watchface/validate.sh`), the four generators' `--check`, and the fit tools.
- [ ] **Step 9: Commit** with the message "Face: TalkBack speaks the time, the next prayer, the countdown and the dates, in the phone's words".

Plan 5's device pass checks this again on his Galaxy Watch, with TalkBack and with Samsung's screen reader, as a ship gate (Ruling 38).

**As built (24 October):**
- **What reaches TalkBack** (the face runtime and the system UI read on emulator-5558, and heard; spec 3.6 has the rules):
  - the runtime lists the clock, then every slot with data at its bounding box, in declaration order, then any `ScreenReader` element (a string resource, so the watch's words and not the phone's);
  - the system makes one item per box and tap action. Where two boxes were the same (all six fixed slots spanned the face), it rebuilt every item each time the list was made, every second because of the ring, and TalkBack said the item it was on again every second;
  - a description cannot hide a slot: an empty one becomes the title and text (slot 7's settings code, "0123456789PM L2LAM"), and a space is read as "Space".
- **The face:** no two boxes are the same, and none is the face's square. Slots 0 and 2 keep the face's square less its last row (slot 0) or column (slot 2); boxes past the face are cut to the screen first, and then two of them were one. Slots 1, 3, 6 and 7 have 0 by 0 boxes, and the scene draws the Sun's dial (`tools/face-sun.py`), the Moon's windows (`tools/face-moon.py`, masked by slot 3's old box with `renderMode`) and the countdown (`face-centre.py`, `face-moon.py`) from their slots' data. Slot 6 is `NextPrayerSource`'s long text again.
- **The sources:** `NextPrayerFields.name` is the name as the phone wrote it. The ring and the long text say the phone's spoken sentence, "Dhuhr in 7 hours 10 minutes, at 12:33", a dynamic description (`SpokenLine`, `nextPrayerSaid`, `CountdownExpression.spoken`, worded by `WatchRules.spokenCountdown`); the dates say the Hijri date with its year and the Gregorian in words, "Sunday 18 October", joined by the phone's comma (`ComplicationFields.datesSpoken`).
- **The feed's spoken words (fix round 1 of the review, which found the first build saying the screen's abbreviations: "بعد ١ س ١٢ د", whose bare letters a speech engine names, and "SUN 18 OCT"):** `WatchUi.spoken` carries the phone's `prayer_a11y_countdown`, its under-a-minute form, `duration_hours_minutes` and the plurals `a11y_hours` and `a11y_minutes` as runs of numbers (`WatchCount`), and `WatchDay.gregorianSpoken` the date in words; both are defaulted, as `hijriDayMonth` was (Ruling 24), and the sample feeds carry them. A feed without them gives the screen's words. Plan 2's phone fills them (spec 1.1, 1.3).
- **TalkBack reads** (Step 7, heard on 07e511be at Task 12's close, slot 0 declared before slot 2 again by Ruling 42, `task-12/talkback-walks.txt`): the time, then "Asr in 1 hour 11 minutes, at 16:10" ("العصر بعد ١ ساعة و١١ دقيقة، الساعة ١٦:١٠"), then "6 Jumada al-Ula 1448, Sunday 18 October", then "Battery: 82" and "Step count N Steps", once each, and nothing in 10 s while no one touches the watch. That is all four styles in English with the wearer's slots empty and with the battery and the steps, and Libya on 12 hours, Saudi Arabia and English on 12 hours. With no new request the line moves on its own: 15:30:05 says "in 39 minutes", 16:09:20 "in under a minute", 16:10:30 "Maghrib in 2 hours 53 minutes, at 19:04", and 00:00:30 on Monday "Fajr in 4 hours 32 minutes, at 4:33" with "7 Jumada al-Ula 1448, Monday 19 October". Four days on, the empty form's slots say "No data". Fix round 3's walks on fef65dc7, under Ruling 40, said the same words with the dates before the prayer (in git at a75edce9), and review round 2's on 95559883 the same words in this order (at 0298756d); the first build's walks, in the screen's words, are in git at 6c074bc7.
- **Explore by touch** (heard on emulator-5558 in review rounds 2 and 3, fix rounds 2 and 3 and at Task 12's close, `task-12/touch.txt`): no item of the face answers a touch, before Task 12 or after it, in either order of slots 0 and 2, and no item of Google's own accessible face either. Every hover goes to the system UI's own views over the face, and TalkBack finds nothing to focus under the finger, while the same touch on TalkBack's own window is spoken.
- **The order, Ruling 42:** slot 0 is declared before slot 2 (7, 0, 1, 2, 3, 6, 4, 5), so the swipe reads the time, the prayer with its countdown, the dates, then the wearer's slots (Interfaces). Ruling 40 had declared slot 2 first, so that the prayer's box would be the later of the two that lie across the face, for a touch (fix round 3, fef65dc7). Ruling 42 withdrew it, because a touch cannot be judged on the emulator and the swipe order is what a TalkBack user gets for certain. On the emulator either order changed the swipe order and nothing else: the system UI's view tree listed the items in the same order, and nothing drawn changed (`task-12/reorder-pass.txt`). Since the revert the face is 0298756d's again, byte for byte, but for its header comment. What a touch finds is heard in plan 5's device pass on his Galaxy, and (b) may be reopened with that evidence.
- **The drawn face** is the committed pass within 3 levels in 255 per channel, apart from what moves between runs (the Moon's seconds hand, the step count, the battery's icon, the moon picture's edge).
- **Tests:** `SpokenTest` (11) and `SourceManifestTest`'s boxes. The harness is `tools/watchface/talkback.sh`.
- Evidence: `docs/superpowers/reports/2026-10-19-face-pass/task-12/`; the report is `task-12-report.md` in the ledger folder.

### Task 13: the Moon's filled always-on hands (added 24 October, Ruling 41)

Mohamed's call of 24 October: in always-on the Moon's hour and minute hands keep their awake form, the faceted dauphine with its black keyline, at a reduced brightness, as Samsung's own analog faces show them. Task 9 drew them as outlines. The hands move every minute, so burn-in is not a concern. Plan 5's device pass judges the look on his Galaxy.

**Files:**
- Modify:
  - `tools/face-moon.py` (the hands block and its header) and so `watchface/src/main/res/raw/watchface.xml` (the hands block, and the face header's AnalogClock line);
  - `tools/face-art.py`: the outline pictures go; a black back for each hand, if the dimmed hand needs one to stay opaque;
  - `watchface/src/main/res/drawable/`: `hand_hour_aod.png` and `hand_minute_aod.png` go;
  - `tools/watchface/ref.py`, so that the mockups draw Ruling 41's hands (the drawings themselves stay as approved), and `lit.py` (`undither`, corrected in fix round 2);
  - `tools/watchface/pass.sh` (the Moon's always-on shots at 10:10 as well);
  - spec 3.4 (the Moon's row and **Always-on**) and 3.6 (**The hands**), this plan's Task 9 Step 1, the pass README.
- Create: `docs/superpowers/reports/2026-10-19-face-pass/task-13/`, for the evidence.
- The closing fix (Ruling 46, 24 October): `tools/face-moon.py` (the moon window's fill and its header) and so `watchface.xml`; `tools/watchface/ref.py` (`window=`); a new `wearApp/src/test/kotlin/world/taqwa/app/wear/complication/FaceAlwaysOnTest.kt`; the Moon's always-on pass shots and their side-by-sides again; in `task-13/`, `worst-r46/`, `windowcheck.py`, `windowdiff.py`, `r46sheet.py` and their outputs, and `litworst.py`, `litshare.py`, `leversheet.py`, `lit-share.txt`, `variants.txt` again; spec 3.4 and 7, this task's as-built, its plan 5 line, both READMEs.

**Interfaces:** nothing changes for the slots, the sources or the feed. The awake face is unchanged to the pixel.

- [ ] **Step 1: The hands always-on.** The awake `HourHand` and `MinuteHand` keep their pictures and take an `AMBIENT` alpha `Variant` at the always-on level, `A_HANDS` in `face-moon.py` (Ruling 25: a Variant on the hand element, never in a Font or a Stroke). The outline set goes, with its pictures. The seconds hand stays out always-on. If a dimmed hand lets what is under it show through, its keyline and facets are laid on a black back of its own shape, the other hand of its kind in the `AnalogClock`, shown only always-on. The cap follows the hands.
- [ ] **Step 2: Tune the level.** Start near 50%. Shoot the Moon always-on at 14:58 and 10:10 at each level tried, beside Arch, Ring and Sun's always-on frames, and choose the level at which the Moon's always-on frame reads like theirs. That is the time the brightest thing on the face, and the hands' facets still told apart. Record the levels tried and why the one chosen.
- [ ] **Step 3: The lit share.** Measure every Moon always-on frame raw and as the face draws it, and the drawing's (`ref.py aod`). The emulator's frame is a strict checkerboard drawn at 0.85 of the face's colours, so the face's share is twice its lit pixels, a pixel lit at 8 or more there (`lit.py undither`; fix round 2 replaced the fill `lit.py` had copied from `compare.py`, which read edges a point high and lost the moon window's fill). The limit is 15%. Measure it at the Moon's worst moments too, not only the pass's: the long chapter arcs just after Fajr and just after Isha, both slots filled with wide values, and the longest Hijri months (added by review round 1).
- [ ] **Step 4: Verify on emulator-5558.** Take the Moon's always-on pass shots again: English and Arabic (Libya, 12 hours), with the slots empty and filled (the battery, the steps), at 14:58 and 10:10, each beside its mockup. Check that the awake Moon and the other three styles equal the committed pass in 0 px, apart from what moves between runs.
- [ ] **Step 5: Run the checks:** the four generators' `--check`, `:watchface` debug and release with lint, both validators (`tools/watchface/validate.sh`), `:wearApp` and `:watchcore`, and the Variant audit (every Variant an `AMBIENT` alpha, none in a Font or a Stroke).
- [ ] **Step 6: Commit** with the message "Face: the Moon's hands filled in always-on, dimmed".

**As built (24 October):**
- **The hands:** `face-moon.py` gives the awake `HourHand` and `MinuteHand` an `AMBIENT` alpha Variant at `A_HANDS`, and declares before each a black back of its own shape (`hand_hour_back.png`, `hand_minute_back.png`, from `face-art.py`: the awake picture with every colour black), alpha 0 awake and 255 always-on. The clock draws its hands in the order they are declared: a probe with the hour hand's back declared after it showed no hour hand. So a dimmed hand stays opaque, its keyline black, and it covers the prayer line and the weekday always-on as it does awake. The outline pictures are gone. The cap's ink is dimmed with the hands, over a black disc, instead of the black disc ringed at 85%. The seconds hand stays out. The awake face is unchanged.
- **The level, `A_HANDS` = 209 (82%):** it is `face-centre.py`'s `A_CLOCK`, the level Arch, Ring and Sun draw their time at always-on. 50%, 70% and 82% were shot beside the other styles' always-on frames (`task-13/tuning.txt`). On the emulator the lit facet comes out at 171 against their time's 168.7, and the shaded facet at 121, about the Moon's texts (88 to 126). At 50% the lit facet (105) was only the countdown's level, and the hands receded behind the texts they are the time of. The whole frame stays darker than the other styles' at any level (mean light 6.5–8.0 against 9.6–13.3, undithered), because the Moon has no large digits.
- **The drawing:** `faces.js` and the reference pages are not changed. `ref.py` draws Ruling 41's hands by default, from the same `hand()`, at `face-moon.py`'s `A_HANDS`; `hands=outline` draws the approved outlines. `lit.py undither` measures an emulator always-on frame as the face draws it.
- **Lit share** (`task-13/lit-share.txt`): a pixel is lit when any channel of the face's colour is over 8. The eight Moon always-on pass frames, at 14:58 and 10:10, are 4.90–6.24% raw, 9.86–12.55% as the face draws them and 9.51–12.17% in the drawing, as rebuilt under Ruling 46 (the closing fix, below); before it they were 12.18–14.82% and 11.81–14.46%.
  - **The emulator's measure** (fix round 2, after review round 2): the emulator dithers an always-on frame as a strict checkerboard, every pixel it draws on one parity (all 17 frames in `task-13/worst/` and the pass's), and draws it at 0.85 of the face's colours, rounded (the moon window's #070908 comes out 6, 8, 7). So `lit.py undither` counts twice the lit pixels, a pixel lit at 8 or more, where a face's 9 lands. On the drawings dimmed and dithered the same way it gives their own share within 0.02 point (`litworst.py` section 4). Task 13 and its fix round 1 filled each black pixel from its neighbours instead. That read 0.9–1.3 points high on edges and dropped the window's fill, 2.0–2.3 points of the Moon, under the threshold, so it had the Moon's worst at 14.2% and Ring and Sun a point high.
  - **The pass's moments are not the Moon's worst** (review round 1; fix round 1's `task-13/litworst.py`, its frames in `task-13/worst/`). The Moon lights the most just after Fajr and just after Isha, at 04:40 and 20:55 on the mock day, when the chapter arc is about eight hours long.
  - **There the Moon was over Ruling 41's 15% by both measures until Ruling 46, with ordinary slot values too** (review round 3). On the emulator, a full battery and its own step count gave 15.41% at 04:40 (890 steps) and 15.45% at 20:55 (960) in Libyan Arabic with 28 Jumada al-Ula, 15.42% in English with 1,030 steps and Jumada al-Akhirah (review round 3's frames, `task-13/worst/*-890-*`, `*-960-*`, `*-1030-*`), and 15.0–15.5% across fix round 1's frames with four-figure counts (15.46% at 04:40 in Libyan Arabic with 28 Jumada al-Ula; 15.34–15.36% in Arabic on the sample's own 6 Jumada al-Ula). The drawing, with 100% and 12,345 steps, reached 15.0–15.2%. The outline hands gave 14.4–14.8% in the drawing at the same moments. With the pass's slot values (82%, no steps) the drawing was 14.3–14.9% there, and with the slots empty 13.8–14.6% (the emulator 14.9%).
  - `A_HANDS` is not the lever: the share does not depend on the level (`tuning.txt`). The filled hands add 0.35–0.47 point over the outlines.
  - **Two levers** (fix round 3; `litworst.py` section 5, `task-13/lever-sheet.png`, the sheet the call was made on): the moon window's near-black fill, #070908, had no always-on Variant, and its 9 is over 8. Black always-on, with its stroke kept, it takes 2.0–2.3 points off: the Moon's worst becomes 13.1% in the drawing and 13.4% on the emulator (13.2–13.4% on review round 3's frames). The date window's fill, `WELL` #0B0D0C, is lit always-on in the same way. Black too, with its hairline kept, it takes the Moon's worst to 11.2% in the drawing and 11.7% on the emulator. Built, either is a fill split from its stroke into its own `PartDraw` with an `AMBIENT` alpha 0 Variant (Ruling 25), and either changes the always-on look beyond the hands. The fills are 7 to 13 of 255; the sheet shows them at their own levels and at 8 times the light. Ruling 46 took the first.
  - Arch, Ring and Sun, measured at their own worst moments for comparison (nothing of them changed in Task 13), on the emulator and in the drawing: Ring at 18:50 is 15.6–16.1% with its slots filled (15.3%) and 14.8% with them empty (14.5%), Sun at 18:00 15.0% (14.2%), Arch 13.5% (13.4%).
  - **The call, Ruling 46 (the Lead, 24 October): the moon window's fill is black always-on**, its hairline and the moon kept, and the date window keeps its fill, since the moon window alone gives the margin. The fill is 6 to 8 of 255 on the emulator, too dark to see always-on, so the look is unchanged and the 15% holds. Built in the closing fix (below).
  - **The limit is met: the Moon is under 15% at its worst moments, as rebuilt** (`litworst.py` sections 3 and 5, its frames in `task-13/worst-r46/`). On emulator-5558, as the face draws it, the thirteen worst cases are 12.91–13.32% with both slots filled (a full battery and the emulator's own 630–1,160 steps; the highest English with Jumada al-Akhirah at 04:40, 1,050 steps) and 12.62–12.64% with them empty, against 14.92–15.46% before: 2.1–2.3 points less in each case, as fix round 3 expected (13.2–13.4% on review round 3's frames). In the drawing the Moon's worst is 13.02% with 100% and 12,345 steps (also its highest over the whole day, every 5 minutes), 12.3–12.7% with the pass's slot values and 11.9–12.3% with the slots empty. Counting every pixel that is not black, the stricter reading, the rebuilt Moon's worst frames are 13.7–14.7% (16.0–16.8% before). `task-13/r46-before-after.png` (`r46sheet.py`) shows the worst frame before and after, at its own levels and at 8 times the light.
  - Ring and Sun are not Task 13's, and nothing of them changed: at their own worst moments Ring with filled slots (15.6–16.1% on the emulator, 15.3% in the drawing) is over 15%, and Sun is at the limit (15.0%, 14.2%); Ring with its slots empty and Arch are under. Their overs are a call of their own. The threshold of 8 is the project's own: counting every pixel that is not black, their worst frames are 16.2–17.4% on Ring, 16.3% on Sun and 14.9% on Arch.
- **The closing fix (Ruling 46, 24 October):** `face-moon.py`'s `moon_window()` writes the window's fill, #070908, as a `PartDraw` of its own with an `AMBIENT` alpha 0 Variant (Ruling 25: a Part's alpha, never a Variant in a Fill or a Stroke), and its hairline as a second `PartDraw` of the same rectangle, declared after it, so drawn over it as before, with no Variant; in the window and in option (d)'s risen one. Nothing is drawn under the window always-on, so the fill's alpha 0 is black there. `FaceAlwaysOnTest` (`:wearApp`, beside `SourceManifestTest`) holds it: it failed on the face before (the fill's rectangle carried the stroke) and passes on the rebuilt one, and fails again on three mutants (the Variant dropped, the hairline dimmed too, the stroke put back with the fill). `ref.py` draws the window so by default, and `window=filled` draws the approved drawing's fill. Two things show that `window=filled` is the approved drawing: ec8f1bd4's `ref.py`, from before the fix, and this one with `window=filled` draw the same frames, 0 px in five cases always-on and awake (the Moon, the Moon of (d) with its slots, Arch, Ring, and the outline hands; review round 1, made again under Ruling 49); and `leversheet.py`, which now asks for `window=filled`, draws the sheet the call was made on again to 0 px. `task-13/windowcheck.py` checks something else: that the default differs from `window=filled` only in the window's fill, black always-on, and not at all awake (it failed before `ref.py` knew the window).
  - On emulator-5558: the face before the ruling (ec8f1bd4's) and the rebuilt one, installed in turn in one session, differ only in the moon window, its 1,662–1,811 fill pixels black, and nowhere else by a pixel, awake or always-on, but the seconds hand and the step count (`task-13/r46-same-session.txt`, `windowdiff.py`). The full pass again: every awake shot and Arch, Ring and Sun's always-on shots within 3 levels of the committed pass, but the battery's icon, which moves between runs; the Moon's always-on shots differ only in the window (`task-13/r46-pass-vs-committed.txt`). The Moon's 8 always-on pass shots are committed again, each beside its mockup in `task-10/`.
- **The pass:** `pass.sh` also shoots the Moon at 10:10 with the slots empty, in English and Arabic, and in Arabic with them filled. Every Moon always-on shot is taken again, and each is beside its mockup in `task-10/`. Outside the hands' footprint they equal the committed frames within 2 levels. Every other shot is within 3 levels, but one Arabic awake shot, where the lit arc meets its pointer.
- **Checks:** the four `--check` up to date; `:watchface` debug and release, lint 'No issues found', `res/li.xml` byte-identical to the source; both validators pass (format 2, Push 10/10 on both); `:wearApp` 142/142 and `:watchcore` 65/65; Ruling 25's audit 142 Variants, all `AMBIENT` alpha, none in a Font or a Stroke. At the closing fix: the four `--check` up to date; `:watchface` debug and release with `--rerun-tasks`, lint 'No issues found' on both, `res/li.xml` byte-identical to the source in both, no `classes.dex`, no `uses-permission`, `hasCode` false; both validators pass, the log byte-identical to the committed `task-13/validate.log`; `:wearApp` 143/143 (`FaceAlwaysOnTest` the new one) and `:watchcore` 112/112 host, with `--rerun-tasks`; `scripts/test.sh` exit 0 on the tree with the Lead's main merge (`:shared` 2036 host and 1981 iOS, `:widgetcore` 111/109, `:watchcore` 112/110, `:wearApp` 143); Ruling 25's audit 144 Variants (the two new ones the window's fill), all `AMBIENT` alpha, none in a Font or a Stroke.
- Evidence: `docs/superpowers/reports/2026-10-19-face-pass/task-13/` (the levers in `lever-sheet.png`, from `leversheet.py`; the closing fix's in `worst-r46/`, `r46-before-after.png`, `r46-same-session.txt`, `r46-pass-vs-committed.txt` and `windowcheck.py`); the report is `task-13-report.md` in the ledger folder.

## Plan 1 as built: what plans 2–5 inherit

Plan 1's work is on `watch-companion` from 07e9958c to 103f6aea, with Tasks 1–10 and the final review's two fix rounds, and then Tasks 11, 12 and 13. It is squashed onto main later. The runtime's rules for any change to the face are in spec 3.6, and the source facts are in spec 2.3. These are what each later plan takes from plan 1:

**Plan 2 (the phone feed):**
- **The face reads every field of spec 1.1:** `midnight` in the location's zone, `digitZero`, the yesterday-plus-30 days, and `hijriDayMonth`.
- **`ui.rtl`, `twelveHour`, `digitZero`, `am` and `pm` become slot 7's settings,** so they must be the app's own, not the device's.
- **`WatchFeedJson.encode` throws on NaN or Infinity,** so the sender at the end of `NotificationCoordinator.reschedule` catches it.
- **`decode` takes versions 1 to `VERSION`,** and `versionOf` tells a newer feed from a malformed one.
- **The debug samples** (`watchcore/vectors/sample-feed-capetown*.json`) are re-dated by whole days, but their words are not. The phone's feed replaces them for every real check.
- **The fit tools measure the phone's real forms:**
  - `tools/watchface/hijri-fit.py`, `window-fit.py`, `slot-fit.py` and `moon-fit.py`, each run with `fonts=<dir>` holding the emulator's Bengali and Naskh fonts;
  - `moon-fit.py` reads `tools/watchface/phone-forms.json` once plan 2 writes it;
  - `hijri-fit.py`, `window-fit.py` and `moon-fit.py` fail without the Bengali font, so a run cannot pass by leaving it out.
- **Bengali's Thursday** sits at the 0.5 floor in the Moon's window until plan 2's short forms.
- **Urdu clock digits:** the Light subset's U+06F4, U+06F6 and U+06F7 need Noto's Urdu forms and a new font file name, because the runtime keeps a font by its name.

**Plan 3 (the Galaxy watch app):**
- `WatchRules.today` and `WatchRules.dayStart` are public, for the Today screen.
- `StatusActivity` is a placeholder for plan 3's screens.
- The re-request safety net (Ruling 22): `requestUpdateAll()` at app start, from the Tile, and daily.

**Plan 4 (the Apple Watch):**
- `watchcore/vectors/watch-rules-vectors.json` holds 16 feeds and 57 cases. They include each day's candidate starts, the tie rule, and Ruling 11's start that has already passed.

**Plan 5 (ship):**
- **The device pass on his Galaxy Watch 8 Classic.** Only the emulator has proven these, so each is checked there:
  - the always-on look (the emulator dithers it), and the Moon's filled always-on hands at 82% (Ruling 41, Task 13; `A_HANDS` in `face-moon.py` is the one number to change) and its moon window black always-on, the hairline kept (Ruling 46, Task 13's closing fix);
  - the Moon always-on at its brightest moments, which the pass does not shoot (Task 13's fix round 1, `task-13/litworst.py`): just after Fajr and just after Isha, when the chapter arc is about eight hours long (04:40 and 20:55 on the mock day), in English and Arabic, with both slots filled with wide values (a full battery, a five-figure step count) and a Jumada month in the Hijri date (Jumada al-Akhirah, or 28 Jumada al-Ula in Arabic). Ring and Sun are brightest late in the Asr chapter, Ring at 18:50 and Sun at 18:00, so they are shot then too, with the same slots and month. Mohamed judges the look; where the watch gives an always-on screenshot, its lit share is measured with `lit.py` (`undither` only for the emulator's checkerboard). There the Moon, as rebuilt under Ruling 46 (its moon window's fill black always-on), is at most 13.02% in the drawing and 13.3% on emulator-5558 with a full battery and the emulator's 630–1,160 steps (it was 15.0–15.2% and 15.0–15.5% before the ruling), under Google's 15%; Ring with its slots filled is 15.3% and 15.6–16.1%, and Sun 14.2% and 15.0%, and what gives where Ring or Sun is over is the Lead's or Mohamed's call (Task 13's fix rounds 2 and 3 and its closing fix; fix round 2 made the emulator's measure the face's own);
  - the battery cost of the seconds hand's 15 Hz sweep, a day on the Moon beside a day on Arch (Ruling 35);
  - that Samsung's picker hides `DayDialSource` (Ruling 31);
  - the cross-slot reads, the multi-run text, the clip to the box, the font cache, `COUNTER_CLOCKWISE`, the `renderMode` mask of the Moon's windows and the editor's outlines;
  - TalkBack and Samsung's screen reader (Task 12): that the face is read as the time, the prayer with its countdown, the dates and the wearer's slots, once each, and that nothing is said again while no one touches it; and what a touch finds, which on the emulator was no item of the face, nor of Google's own accessible face (`task-12/touch.txt`): if a touch finds the dates where their box and the prayer's overlap, declaring slot 2 before slot 0 (Ruling 40's option (b)) may be reopened with that evidence (Ruling 42); and that the time is said in the watch's own format and voice, not the phone's (the emulator said "2:58 PM" on a 24-hour feed);
  - the Hijri size steps (Ruling 33) and the weekday's scaling (Ruling 34);
  - where the PM marker sits, the proportional Arabic-Indic clock, and the font weights;
  - the wearer's own sources from Samsung and Google;
  - an Arabic watch language.
- **A 12-hour observation** of the six-hourly re-request (Ruling 22).
- **A Wear OS 4 emulator** (API 33, the face's minSdk).
- **CI:**
  - the four fit tools need Pillow with Raqm and the Noto Bengali and Naskh fonts, so plan 5 either fetches them under pinned hashes or makes the tools a Mac-side release gate;
  - `face-art.py` has no `--check`;
  - `wff-validator` comes from Google's moving `release` URL under a pinned hash.
- **Polish:**
  - the Moon's arc on the night of a clock change (30° off for the hour around it);
  - a far-north twilight past the dial's midnight;
  - a track for a spent Ring.
- **Release:**
  - the face's release key, which Mohamed creates;
  - the watch app's launcher icon and store assets (the icon is wearApp lint's one warning);
  - `wearApp` release has no R8 yet.
- **The harness:**
  - `tools/watchface/README.md` covers the emulator, installing, choosing, shooting, comparing and the full pass;
  - `validate.sh` runs both validators, with Push on the debug face and on a copy of the release signed with the test key;
  - CI runs `validate.sh --schema-only`.
