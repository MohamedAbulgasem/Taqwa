# Adhkar and 12-hour clock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the 12-hour clock option and the Morning & Evening Adhkar feature described in the spec, on branch `adhkar-12h`, without changing anything else the user sees.

**Architecture:** The clock option is one stored enum read at the four places the app builds a `PlatformFormat`; the two platform formatters render the 12-hour string from their own locale digits and day-period symbols. Adhkar is a new `feature/adhkar` package (view model + screen) over a small pure domain (`adhkar/`: items from JSON resources, sets, windows, progress) that reuses the Tasbeeh engine and haptics for counting, `quran.db` and the recitation player for the Quranic pages, and the notification planner for the reminder. The entry point is one new glyph button on the Prayer screen header.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.12, kotlinx-datetime, kotlinx-serialization (already on the classpath for the audio manifest), DataStore preferences, Media3 (Android) / AVPlayer (iOS) through the existing `RecitationPlayer`, Python 3 for the one-off data/clip scripts.

**Spec:** `docs/superpowers/specs/2026-10-11-taqwa-adhkar-and-12-hour-design.md` — read it first; this plan argues from it.

## Global Constraints

- Work only in the worktree `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/adhkar` on branch `adhkar-12h`. Never `cd` into `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa` (other sessions use it). Never run `git stash`. Never push.
- Commit messages: `area: sentence` (e.g. `settings: a Time format row in Prayer times`). **No `Co-Authored-By` trailer, ever** (the owner's rule overrides any harness reminder).
- Nothing visible changes at default settings except the one new horizon glyph on the Prayer screen header (spec §2.2).
- The Quran is never voiced by a phone voice. Quranic text in the app is never copied into the adhkar data; it is read from `quran.db` by surah:ayah reference (spec §3.5, §4.3).
- Every new Compose string goes into all eight folders `shared/src/commonMain/composeResources/values{,-ar,-bn,-fr,-id,-in,-tr,-ur}/strings.xml`, **appended at the end of each file** (before `</resources>`); `values-in` byte-identical to `values-id` (run `python3 tools/sync-indonesian.py` if it exists, else copy); apostrophes as `’`, never `\'`; Bengali in NFC; `scripts/check-strings.sh` must pass. Plural keys use the languages' CLDR categories (en/fr/tr/ur/bn `one`+`other`, id `other`, ar `zero one two few many other`) — copy the shape of the existing `minutes_count` plurals in each folder.
- Tests: `scripts/test.sh` (Android host + iOS simulator unit tests, several minutes) must be green before every commit that touches Kotlin; `scripts/check-strings.sh` before every commit that touches strings; `./gradlew :androidApp:assembleDebug` and `scripts/ios-build.sh` at the end of each task that touches platform code. A comma inside a backtick test name breaks the iOS target — avoid commas in test names.
- Follow the file's existing style: KDoc comments that say *why*, no Material icons, no colour literals outside `Palette.kt`, no ripple on tap surfaces that acknowledge themselves, `LocalTaqwaColors`, `TaqwaText` styles.
- Design tokens: gutter 24 dp; card radius 18 dp; `TaqwaRow` min 48 dp; `sectionLabel` 11 sp; `caption` 14 sp; `rowLabel` 17 sp; accent is the only colour.
- Reference material for the content tasks (read-only, outside the repo): the OpenDua catalogue `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/ee45030b-722d-4628-9255-3c59c8e3a441/scratchpad/adhkar/research/raw-r2/opendua.org_v0.0.4_catalogue.json__f3fb22a5`, the hisnmuslim.com chapter text `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/ee45030b-722d-4628-9255-3c59c8e3a441/scratchpad/adhkar/research/raw/www.hisnmuslim.com_api_ar_27.json`, and the canon notes `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/ee45030b-722d-4628-9255-3c59c8e3a441/scratchpad/adhkar/research/R1-adhkar-canon.md`.

---

## File map

| File | Responsibility |
|------|----------------|
| `shared/src/commonMain/kotlin/world/taqwa/app/i18n/ClockFormat.kt` (new) | `ClockFormat` enum, `twelveHourParts`, `TwelveHourClock.render` (the pure join/position rule) |
| `shared/src/commonMain/kotlin/world/taqwa/app/i18n/PlatformFormat.kt` | `createPlatformFormat(clockFormat)` expect signature |
| `shared/src/{androidMain,iosMain}/.../i18n/PlatformFormat.*.kt` | 12-hour rendering with platform digits and symbols |
| `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt`, `SettingsRepository.kt` | `clock_format`, `adhkar_translation`, `adhkar_morning`, `adhkar_evening`, `adhkar_istighfar` keys and flows |
| `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`, `AppEffects.kt`, `notifications/NotificationCoordinator.kt`, `widget/WidgetMirrorRefresher.kt`, `feature/today/TodayViewModel.kt` | read the clock setting where a format is built |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/PrayerTimesSettingsScreen.kt`, `SettingsRoutes.kt` | Time format row + sheet |
| `shared/src/commonMain/composeResources/files/adhkar/items.json`, `files/adhkar/text/{en,bn,fr,id,tr,ur}.json` (new) | the content |
| `tools/adhkar/` (new): `opendua-morning-evening.json`, `build_items.py`, `check_items.py` | extraction from OpenDua, verification against the hisnmuslim text |
| `shared/src/commonMain/kotlin/world/taqwa/app/adhkar/` (new): `AdhkarItem.kt`, `AdhkarCatalogue.kt`, `AdhkarSet.kt`, `AdhkarWindows.kt`, `AdhkarProgress.kt` | pure domain: model, JSON loading, sets/pages, windows/phases, progress encoding |
| `shared/src/commonMain/kotlin/world/taqwa/app/settings/AdhkarStore.kt` (new) | DataStore access for progress |
| `shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt` | `drawHorizon` |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayScreen.kt`, `PrayerRoutes.kt`, `nav/Screen.kt`, `App.kt`, `AppEffects.kt` | the button, the route, the screen branch, the debug name |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/adhkar/` (new): `AdhkarViewModel.kt`, `AdhkarScreen.kt`, `AdhkarPage.kt`, `AdhkarReadingSheet.kt` | the screen |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.kt` + actuals, `feature/recitation/RecitationController.kt` | ayah-subset load, `requestPlayAyahs`, no advance |
| `shared/src/commonMain/composeResources/files/adhkar/clips/ar.alafasy/*.mp3` (new), `tools/quran-audio/adhkar_clips.py` (new), `recitation/ManifestProvider.kt` | bundled Alafasy clips |
| `shared/src/commonMain/kotlin/world/taqwa/app/domain/NotificationSettings.kt`, `notifications/*`, `feature/settings/NotificationSettingsScreen.kt`, `SettingsRoutes.kt`, `nav/LaunchRequests.kt`, `androidApp/.../MainActivity.kt`, `shared/src/androidMain/.../notifications/*`, `shared/src/iosMain/.../notifications/*` | the reminder |
| `docs/licences/OpenDua-CC-BY-4.0.txt` (new), `docs/ATTRIBUTION.md`, `NOTICE`, `README.md`, `feature/settings/AttributionScreen.kt` | credits |
| `docs/BUILD-LOG.md` | the entry |

---

### Task 1: ClockFormat and the pure 12-hour rule

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/i18n/ClockFormat.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt` (add one key), `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsRepository.kt` (flow + setter beside `themeMode` at about line 117 and `setThemeMode` at about line 320)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/i18n/TwelveHourClockTest.kt`

**Interfaces:**
- Produces: `enum class ClockFormat { TWENTY_FOUR_HOUR, TWELVE_HOUR }`; `fun twelveHourParts(hour: Int): Pair<Int, Boolean>` (hour 1–12, isPm); `object TwelveHourClock { fun render(hourText: String, minuteText: String, marker: String, markerFirst: Boolean): String; fun markerFirst(languageTag: String): Boolean }`; `SettingsRepository.clockFormat: Flow<ClockFormat>`, `suspend fun setClockFormat(format: ClockFormat)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwelveHourClockTest {
    @Test
    fun `midnight is twelve am and noon is twelve pm`() {
        assertEquals(12 to false, twelveHourParts(0))
        assertEquals(12 to true, twelveHourParts(12))
        assertEquals(1 to true, twelveHourParts(13))
        assertEquals(11 to true, twelveHourParts(23))
        assertEquals(5 to false, twelveHourParts(5))
    }

    @Test
    fun `the marker follows the digits joined by a no-break space`() {
        assertEquals("6:32 PM", TwelveHourClock.render("6", "32", "PM", markerFirst = false))
    }

    @Test
    fun `turkish puts the marker first`() {
        assertEquals("ÖS 6:32", TwelveHourClock.render("6", "32", "ÖS", markerFirst = true))
        assertTrue(TwelveHourClock.markerFirst("tr-TR"))
        assertTrue(TwelveHourClock.markerFirst("tr"))
        assertFalse(TwelveHourClock.markerFirst("en-GB"))
        assertFalse(TwelveHourClock.markerFirst("ar-EG"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.i18n.TwelveHourClockTest' -q`
Expected: compilation failure, `ClockFormat`/`twelveHourParts` unresolved.

- [ ] **Step 3: Write the implementation**

`shared/src/commonMain/kotlin/world/taqwa/app/i18n/ClockFormat.kt`:

```kotlin
package world.taqwa.app.i18n

/** How clock times are written. 24-hour is the default and what the site prints (spec §1.1). */
enum class ClockFormat { TWENTY_FOUR_HOUR, TWELVE_HOUR }

/** The 12-hour dial's hour for a 0..23 hour, and whether it is after noon: 0 → 12 am, 12 → 12 pm. */
fun twelveHourParts(hour: Int): Pair<Int, Boolean> {
    val h = hour % 12
    return (if (h == 0) 12 else h) to (hour >= 12)
}

/**
 * The pure half of 12-hour rendering (spec §1.2). The digits and the day-period marker come
 * from the platform; this decides only how they are joined: U+00A0 rather than CLDR's U+202F,
 * which Manrope has no glyph for, and the marker before the digits only for Turkish, the one
 * interface language whose CLDR pattern is "a h:mm".
 */
object TwelveHourClock {
    private const val NO_BREAK_SPACE = " "

    fun markerFirst(languageTag: String): Boolean =
        languageTag.substringBefore('-').substringBefore('_').lowercase() == "tr"

    fun render(hourText: String, minuteText: String, marker: String, markerFirst: Boolean): String =
        if (markerFirst) "$marker$NO_BREAK_SPACE$hourText:$minuteText" else "$hourText:$minuteText$NO_BREAK_SPACE$marker"
}
```

In `SettingsKeys.kt`, beside `SHOW_SUNRISE`:

```kotlin
    /** 24-hour or 12-hour clock times (spec §1.1). Absent means 24-hour, so no upgrade changes a screen. */
    val CLOCK_FORMAT = stringPreferencesKey("clock_format")
```

In `SettingsRepository.kt`, beside `themeMode`:

```kotlin
    val clockFormat: Flow<ClockFormat> =
        store.data.map { it[SettingsKeys.CLOCK_FORMAT].toEnumOr(ClockFormat.TWENTY_FOUR_HOUR) }
```

and beside `setThemeMode`:

```kotlin
    suspend fun setClockFormat(format: ClockFormat) {
        store.edit { it[SettingsKeys.CLOCK_FORMAT] = format.name }
    }
```

(import `world.taqwa.app.i18n.ClockFormat`).

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.i18n.TwelveHourClockTest' -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/i18n/ClockFormat.kt shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsRepository.kt shared/src/commonTest/kotlin/world/taqwa/app/i18n/TwelveHourClockTest.kt
git commit -m "i18n: a ClockFormat setting and the pure 12-hour rule"
```

---

### Task 2: The two platform formatters render 12-hour times

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/i18n/PlatformFormat.kt` (line 57: the expect), `shared/src/androidMain/kotlin/world/taqwa/app/i18n/PlatformFormat.android.kt`, `shared/src/iosMain/kotlin/world/taqwa/app/i18n/PlatformFormat.ios.kt`
- Test: `shared/src/androidHostTest/kotlin/world/taqwa/app/i18n/AndroidPlatformFormatTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `expect fun createPlatformFormat(clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR): PlatformFormat`; Android actual `AndroidPlatformFormat(locale: Locale = Locale.getDefault(), clockFormat)` made `internal` so the host test can build it for a given locale.

- [ ] **Step 1: Write the failing host test**

```kotlin
package world.taqwa.app.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidPlatformFormatTest {
    private fun format(tag: String, clock: ClockFormat) = AndroidPlatformFormat(Locale.forLanguageTag(tag), clock)

    @Test
    fun `twenty four hour is unchanged`() {
        assertEquals("18:32", format("en-US", ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32))
        assertEquals("3:07", format("en-GB", ClockFormat.TWENTY_FOUR_HOUR).clockTime(3, 7))
    }

    @Test
    fun `american english uses upper case markers after the digits`() {
        assertEquals("6:32 PM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("12:05 AM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(0, 5))
        assertEquals("12:05 PM", format("en-US", ClockFormat.TWELVE_HOUR).clockTime(12, 5))
    }

    @Test
    fun `british english uses lower case markers`() {
        assertEquals("6:32 pm", format("en-GB", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `turkish puts the marker first`() {
        assertEquals("ÖS 6:32", format("tr-TR", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `egyptian arabic keeps its digits and uses the arabic marker`() {
        val text = format("ar-EG", ClockFormat.TWELVE_HOUR).clockTime(18, 32)
        assertEquals("٦:٣٢ م", text)
        assertEquals("6:32 م", format("ar-LY", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }

    @Test
    fun `bengali indonesian and urdu use latin markers`() {
        assertEquals("৬:৩২ PM", format("bn-BD", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("6:32 PM", format("id-ID", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
        assertEquals("6:32 PM", format("ur-PK", ClockFormat.TWELVE_HOUR).clockTime(18, 32))
    }
}
```

If the JVM's CLDR data (Java 21, used by the Android plugin's host tests) yields a different marker for one locale — run the test and read the actual value — change the *expected* value to what `DateFormatSymbols` gives for that locale and note it in the test's KDoc; the rule under test is "the platform's own symbol", not a particular spelling. Do not special-case the code.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.i18n.AndroidPlatformFormatTest' -q`
Expected: compilation failure (constructor/`internal`).

- [ ] **Step 3: Implement the Android actual**

In `PlatformFormat.android.kt`: change the class header to

```kotlin
internal class AndroidPlatformFormat(
    private val locale: Locale = Locale.getDefault(),
    private val clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR,
) : PlatformFormat {
```

(remove the old `private val locale` line), add

```kotlin
    // CLDR's abbreviated day periods for this locale — "AM"/"PM" in American English, "am"/"pm"
    // in British, "ص"/"م" in Arabic, "ÖÖ"/"ÖS" in Turkish. Never our own strings (spec §1.2).
    private val amPm: Array<String> = DateFormatSymbols.getInstance(locale).amPmStrings
    private val markerFirst = TwelveHourClock.markerFirst(locale.toLanguageTag())
```

and replace `clockTime`:

```kotlin
    override fun clockTime(hour: Int, minute: Int): String {
        if (clockFormat == ClockFormat.TWENTY_FOUR_HOUR) {
            return "${plainFormat.format(hour)}:${twoDigitFormat.format(minute)}"
        }
        val (h, pm) = twelveHourParts(hour)
        return TwelveHourClock.render(
            plainFormat.format(h), twoDigitFormat.format(minute), amPm[if (pm) 1 else 0], markerFirst,
        )
    }
```

and the actual at the bottom:

```kotlin
actual fun createPlatformFormat(clockFormat: ClockFormat): PlatformFormat = AndroidPlatformFormat(clockFormat = clockFormat)
```

Import `java.text.DateFormatSymbols`.

- [ ] **Step 4: Implement the iOS actual**

In `PlatformFormat.ios.kt`: `private class IosPlatformFormat(private val clockFormat: ClockFormat) : PlatformFormat`, add after `tag`:

```kotlin
    // The day-period symbols of the *language* locale, not `currentLocale`: the marker is a
    // language question ("pm" in British English, "PM" in American), while the digits above
    // are a region question (spec §1.2).
    private val amPm: Pair<String, String> = NSDateFormatter().run {
        setLocale(NSLocale(localeIdentifier = tag))
        (AMSymbol ?: "AM") to (PMSymbol ?: "PM")
    }
    private val markerFirst = TwelveHourClock.markerFirst(tag)
```

and replace `clockTime`:

```kotlin
    override fun clockTime(hour: Int, minute: Int): String {
        val m = twoDigitFormatter.stringFromNumber(NSNumber(int = minute)) ?: minute.toString().padStart(2, '0')
        if (clockFormat == ClockFormat.TWENTY_FOUR_HOUR) {
            val h = plainFormatter.stringFromNumber(NSNumber(int = hour)) ?: hour.toString()
            return "$h:$m"
        }
        val (hour12, pm) = twelveHourParts(hour)
        val h = plainFormatter.stringFromNumber(NSNumber(int = hour12)) ?: hour12.toString()
        return TwelveHourClock.render(h, m, if (pm) amPm.second else amPm.first, markerFirst)
    }
```

and `actual fun createPlatformFormat(clockFormat: ClockFormat): PlatformFormat = IosPlatformFormat(clockFormat)`. In `PlatformFormat.kt` change the expect to `expect fun createPlatformFormat(clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR): PlatformFormat` (the default lives on the expect only). Every existing `createPlatformFormat()` call still compiles.

- [ ] **Step 5: Run the host test and the whole suite**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.i18n.AndroidPlatformFormatTest' -q` then `scripts/test.sh`
Expected: PASS; the full suite green (note the Android and iOS counts for the report).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/i18n/PlatformFormat.kt shared/src/androidMain/kotlin/world/taqwa/app/i18n/PlatformFormat.android.kt shared/src/iosMain/kotlin/world/taqwa/app/i18n/PlatformFormat.ios.kt shared/src/androidHostTest/kotlin/world/taqwa/app/i18n/AndroidPlatformFormatTest.kt
git commit -m "i18n: the platform formatters render 12-hour times from their own symbols"
```

---

### Task 3: The setting reaches every clock surface

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt` (about lines 68 and 113–114), `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCoordinator.kt` (`plan`, about line 75), `shared/src/commonMain/kotlin/world/taqwa/app/widget/WidgetMirrorRefresher.kt` (the `format` default), `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayViewModel.kt` (`widgetFormat` default, about line 123) and its construction in `PrayerRoutes.kt`, `shared/src/commonMain/kotlin/world/taqwa/app/notifications/LocalizedNotificationCopy.kt` (isolate `{time}`), `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/WidgetPreview.kt` (sample times), `androidApp/src/main/kotlin/world/taqwa/app/widget/TaqwaGlanceWidget.kt` (`LONGEST_ROW_EM`, `TWO_COLUMN_MIN_WIDTH`)
- Test: `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/WidgetInputsMirrorTest.kt` (new fixture), `shared/src/commonTest/kotlin/world/taqwa/app/notifications/LocalizedNotificationCopyTest.kt` (isolate)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: nothing new; `WidgetMirrorRefresher.refresh(settings, engine, store, format = null, now)` where a null format is built from `settings.clockFormat.first()`.

- [ ] **Step 1: Write the failing tests**

In `WidgetInputsMirrorTest.kt` add a round-trip case whose `clockTime` strings are `"5:10 PM"` and `"12:05 AM"` (copy the shape of the existing round-trip test in that file; the wire separators are `|`, `;`, `=`, `@`, none of which is a space). In `LocalizedNotificationCopyTest.kt` add:

```kotlin
    @Test
    fun `the time is isolated inside the body`() {
        val body = LocalizedNotificationCopy(fakeFormat("ur")).body(Prayer.FAJR, NotificationKind.PRAYER, "5:10 PM", 0)
        assertTrue(body.contains("⁨" + "5:10 PM" + "⁩"))
    }
```

(use the file's existing fake-format helper; name it as that file does).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :widgetcore:testAndroidHostTest :shared:testAndroidHostTest --tests '*WidgetInputsMirrorTest' --tests '*LocalizedNotificationCopyTest' -q`
Expected: the isolate test fails (no U+2068); the wire test should already pass (if it does, keep it as a regression fixture).

- [ ] **Step 3: Implement**

1. `LocalizedNotificationCopy.body`/`reminder`: replace every `.replace("{time}", clockTime)` with `.replace("{time}", isolated(clockTime))` where `private fun isolated(s: String) = "⁨$s⁩"` (FSI…PDI, the same isolate `TodayScreen`'s sub-lines use).
2. `App.kt`: next to `themeModeState` add `val clockFormat by settings.clockFormat.collectAsState(initial = ClockFormat.TWENTY_FOUR_HOUR)`; change `val platformFormat = remember(uiLanguage) { createPlatformFormat() }` to `remember(uiLanguage, clockFormat) { createPlatformFormat(clockFormat) }`.
3. `NotificationCoordinator.plan`: `val format = createPlatformFormat(settingsRepository.clockFormat.first())`.
4. `WidgetMirrorRefresher.refresh`: parameter `format: PlatformFormat? = null`; inside, `val fmt = format ?: createPlatformFormat(settings.clockFormat.first())` and use `fmt`. Check every caller still compiles (`PrayerAlarmReceiver`, `BackgroundRefreshBridge`, `SettingsRoutes`, `AppEffects`).
5. `TodayViewModel`: `widgetFormat` becomes `suspend () -> PlatformFormat` defaulting to `{ createPlatformFormat(settings.clockFormat.first()) }`; adjust its one use (it is called when the mirror is written) and the fake in `TodayViewModelTest` if the test passes a lambda.
6. `WidgetPreview.kt` `SampleTimes`: add a 12-hour twin and pick by `LocalPlatformFormat.current.clockTime(5, 35)` shape — simplest: render the sample through `format.clockTime(h, m)` from a list of `(hour, minute)` pairs `(5,35),(12,46),(16,3),(18,32),(19,50)` instead of literal strings.
7. `TaqwaGlanceWidget.kt`: read the comment deriving `LONGEST_ROW_EM` (about line 143) and `TWO_COLUMN_MIN_WIDTH` (about lines 122–127); add the marker's width (two capitals ≈ 0.65 em each + a space ≈ 0.28 em) to the longest-row estimate, re-derive the two-column minimum (expect ≈ 255 dp), and update both comments with the new arithmetic.

- [ ] **Step 4: Run the suite and both builds**

Run: `scripts/test.sh` then `./gradlew :androidApp:assembleDebug -q` then `scripts/ios-build.sh`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add -A shared/src/commonMain/kotlin/world/taqwa/app/App.kt shared/src/commonMain/kotlin/world/taqwa/app/notifications shared/src/commonMain/kotlin/world/taqwa/app/widget/WidgetMirrorRefresher.kt shared/src/commonMain/kotlin/world/taqwa/app/feature/today shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/WidgetPreview.kt androidApp/src/main/kotlin/world/taqwa/app/widget/TaqwaGlanceWidget.kt widgetcore/src/commonTest shared/src/commonTest/kotlin/world/taqwa/app/notifications
git commit -m "i18n: the clock format reaches the screens, notifications and widgets"
```

---

### Task 4: The Time format row and sheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/PrayerTimesSettingsScreen.kt` (signature at lines 105–122; Card C at lines 224–231), `shared/src/commonMain/kotlin/world/taqwa/app/SettingsRoutes.kt` (`PrayerTimesSettingsRoute`, about lines 216–258), eight `strings.xml`
- Test: `shared/src/iosTest/kotlin/world/taqwa/app/feature/settings/TimeFormatSheetTest.kt` (Compose UI test on the simulator target; see the harness rules below)

**Interfaces:**
- Consumes: Task 1 (`ClockFormat`, `setClockFormat`), Task 2.
- Produces: `PrayerTimesSettingsScreen(..., clockFormat: ClockFormat, onClockFormat: (ClockFormat) -> Unit, ...)`.

- [ ] **Step 1: Add the strings to all eight folders** (append before `</resources>`):

| key | en | ar | bn | fr | id (and in) | tr | ur |
|---|---|---|---|---|---|---|---|
| `prayer_times_time_format` | Time format | تنسيق الوقت | সময়ের ফরম্যাট | Format de l’heure | Format waktu | Saat biçimi | وقت کا فارمیٹ |
| `time_format_24` | 24-hour | 24 ساعة | ২৪ ঘণ্টা | 24 heures | 24 jam | 24 saat | 24 گھنٹے |
| `time_format_12` | 12-hour | 12 ساعة | ১২ ঘণ্টা | 12 heures | 12 jam | 12 saat | 12 گھنٹے |

Run `scripts/check-strings.sh`; expected clean.

- [ ] **Step 2: Write the failing UI test**

The iOS UI-test harness: `shared/src/iosTest`, `runComposeUiTest` from `androidx.compose.ui.test.v2`, `getString(Res.string.x)` for expected text, never compose `TaqwaTheme`/`SystemBarsAppearance`/`KeepScreenOn` under test (SIGSEGV). Copy the structure of an existing test in `shared/src/iosTest/kotlin/world/taqwa/app/` (there is at least one `ComposeUiTestSmokeTest.kt`-style file on other branches; if none exists on this branch, create the smoke pattern: a `runComposeUiTest { setContent { ... } }` with `onNodeWithText`). The test: compose `PrayerTimesSettingsScreen` with a default `PrayerSettings()` and `clockFormat = ClockFormat.TWENTY_FOUR_HOUR` inside `CompositionLocalProvider(LocalTaqwaColors provides LightColors)`; assert the row `prayer_times_time_format` shows value `time_format_24`; click it; assert both options exist; click `time_format_12`; assert the callback received `ClockFormat.TWELVE_HOUR`.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :shared:iosSimulatorArm64Test --tests '*TimeFormatSheetTest' -q`
Expected: compile failure (new parameters).

- [ ] **Step 4: Implement the row and sheet**

In `PrayerTimesSettingsScreen`, add parameters `clockFormat: ClockFormat` and `onClockFormat: (ClockFormat) -> Unit`. In Card C:

```kotlin
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.prayer_times_show_sunrise),
                trailing = { TaqwaToggle(checked = settings.showSunrise, onCheckedChange = onShowSunrise) },
            )
            CardDivider()
            // The screen's other display preference sits with "Show sunrise": both say how the
            // list is shown, neither changes a time (spec §1.4).
            TaqwaRow(
                stringResource(Res.string.prayer_times_time_format),
                value = clockFormatName(clockFormat),
                onClick = { timeFormatOpen = true },
            )
            CardDivider()
            ChevronRow(stringResource(Res.string.prayer_times_about_row), onClick = onOpenAboutTimes)
        }
```

with `var timeFormatOpen by remember { mutableStateOf(false) }` at the top of the composable, and after the scaffold:

```kotlin
    if (timeFormatOpen) {
        TaqwaBottomSheet(onDismissRequest = { timeFormatOpen = false }) {
            TimeFormatSheet(
                current = clockFormat,
                onPick = { onClockFormat(it); timeFormatOpen = false },
            )
        }
    }
```

and, private in the same file:

```kotlin
@Composable
private fun clockFormatName(format: ClockFormat): String = when (format) {
    ClockFormat.TWENTY_FOUR_HOUR -> stringResource(Res.string.time_format_24)
    ClockFormat.TWELVE_HOUR -> stringResource(Res.string.time_format_12)
}

/**
 * Two option rows in the "Remind me before" idiom, each showing the same sample time, 18:32, the
 * way that format writes it — so the reader sees "٦:٣٢ م" or "ÖS 6:32" before committing.
 */
@Composable
private fun TimeFormatSheet(current: ClockFormat, onPick: (ClockFormat) -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
        Text(stringResource(Res.string.prayer_times_time_format), style = TaqwaText.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))
        TaqwaCard(Modifier.selectableGroup()) {
            ClockFormat.entries.forEachIndexed { index, option ->
                if (index > 0) CardDivider()
                val sample = remember(option) { createPlatformFormat(option).clockTime(18, 32) }
                TaqwaRow(
                    clockFormatName(option),
                    subtitle = sample,
                    selectable = true,
                    selected = option == current,
                    onClick = { onPick(option) },
                    trailing = { if (option == current) CheckMark() },
                )
            }
        }
    }
}
```

Read `TaqwaRow`'s actual parameter names in `design/components/TaqwaCard.kt` on this branch (`selectable`/`selected`/`trailing`/`subtitle`) and the sound sheet in `NotificationSettingsScreen.kt` for the exact idiom; match them. In `SettingsRoutes.PrayerTimesSettingsRoute`, collect `settings.clockFormat` (`val clockFormat by settings.clockFormat.collectAsState(initial = ClockFormat.TWENTY_FOUR_HOUR)`) and pass `clockFormat = clockFormat, onClockFormat = { write { settings.setClockFormat(it) } }` — `write` already calls `refreshForNewTimes`, which re-plans notifications and rewrites the widget mirror. Note `refreshForNewTimes(container, settings, format)` receives the route's `LocalPlatformFormat.current`, which `App` rebuilds on the new value a frame later; to avoid writing the mirror with the old format, call `WidgetMirrorRefresher.refresh(settings, engine, format = null)` from `refreshForNewTimes` (Task 3 made null mean "build from the setting") — change that one line.

- [ ] **Step 5: Run the UI test, the suite, check-strings and both builds**

Run: `./gradlew :shared:iosSimulatorArm64Test --tests '*TimeFormatSheetTest' -q`, `scripts/test.sh`, `scripts/check-strings.sh`, `./gradlew :androidApp:assembleDebug -q`, `scripts/ios-build.sh`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/PrayerTimesSettingsScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/SettingsRoutes.kt shared/src/commonMain/composeResources shared/src/iosTest
git commit -m "settings: a Time format row in Prayer times, 24-hour or 12-hour"
```

---

### Task 5: The adhkar content as resources, with its verification

**Files:**
- Create: `tools/adhkar/opendua-morning-evening.json` (the 24 OpenDua entries, verbatim, with the attribution header), `tools/adhkar/build_items.py`, `tools/adhkar/check_items.py`, `shared/src/commonMain/composeResources/files/adhkar/items.json`, `shared/src/commonMain/composeResources/files/adhkar/text/en.json`, `docs/licences/OpenDua-CC-BY-4.0.txt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/adhkar/AdhkarCatalogueTest.kt` is Task 6; here only the Python check runs.

**Interfaces:**
- Produces: the JSON schema below, consumed by Task 6's loader.

`items.json` schema (one object with `"schema": 1`, `"attribution"`, `"items": [...]`); per item:

```json
{
  "id": "hisn-078",
  "hisn": 78,
  "kind": "dua",
  "sets": ["morning", "evening"],
  "count": 1,
  "countDaily": false,
  "arabic": { "morning": "اللَّهُمَّ بِكَ أَصْبَحْنَا، …", "evening": "اللَّهُمَّ بِكَ أَمْسَيْنَا، …" },
  "transliteration": { "morning": "Allāhumma bika aṣbaḥnā, …", "evening": "Allāhumma bika amsaynā, …" },
  "sources": [ { "collection": "abu_dawud", "number": "5068" }, { "collection": "tirmidhi", "number": "3391" }, { "collection": "ibn_majah", "number": "3868" } ]
}
```

Items with one wording use `"arabic": { "both": "…" }` / `"transliteration": { "both": "…" }`. Quran items: `"kind": "quran"`, `"quran": { "surah": 2, "ayah": 255, "prefix": "istiadhah" }` (no `ayah` for a whole surah; `"prefix": "basmala"` for the Quls), `"arabic"` holds only the prefix text for 2:255 (`"both": "أَعُوذُ بِاللَّهِ مِنَ الشَّيطَانِ الرَّجِيمِ"`) and is absent for the Quls, no `transliteration`, `"sources": [{"collection":"quran","number":"2:255"}, {"collection":"nasai","number":"al-Kubra 10731"}]`. Collections used: `quran`, `bukhari`, `muslim`, `abu_dawud`, `tirmidhi`, `ibn_majah`, `nasai`, `hakim`, `ahmad`. The 19 ids in spec §4.1 order: `hisn-075`, `hisn-076a`, `hisn-076b`, `hisn-076c` (surahs 112, 113, 114), `hisn-077`, `hisn-078`, `hisn-079`, `hisn-084`, `hisn-085`, `hisn-086`, `hisn-088`, `hisn-090` (morning only), `hisn-091` (count 100), `hisn-092` (10), `hisn-093` (100, morning), `hisn-094` (3, morning), `hisn-095` (morning), `hisn-096` (100, `countDaily: true`, both), `hisn-097` (3, evening only). `text/en.json`: `{ "language": "en", "items": { "hisn-078": { "morning": "…", "evening": "…" }, "hisn-086": { "both": "…" } } }` for the 15 du'a ids (no entries for the four Quran ids).

- [ ] **Step 1: Extract OpenDua's 24 entries**

Write `tools/adhkar/build_items.py` (run with `python3 -I`): reads the catalogue path given as argv[1], keeps the entries whose `tags` contain `morning-and-evening`, writes them verbatim to `tools/adhkar/opendua-morning-evening.json` with a top-level `"attribution"` string equal to OpenDua's required text ("OpenDua dataset v0.0.4 (https://opendua.org), by Ihsan Labs at the Human Development Fund. Based on Hisn al-Muslim by Sa'id ibn Ali ibn Wahf al-Qahtani. OpenDua editorial text and dataset structure are licensed CC BY 4.0. Recitation by Muhammad Jumah for OpenDua. OpenDua makes no ownership claim over the underlying religious texts."), then builds `items.json` and `text/en.json` from the entries whose `sourceReference` is in the spec's list, mapping variations `morning`/`evening`/`standard` to the schema above, the Quran entries by hand-coded references, counts from the step's `times` field with these overrides from the canon notes: Hisn 83 is not shipped; 93/94/95 morning only; 97 evening only; 90 morning only; 96 `countDaily`. Sources come from a table in the script (the spec §4.1 table), not from OpenDua. The script is deterministic (sorted keys, `ensure_ascii=False`, `indent=2`, trailing newline).

- [ ] **Step 2: Write the verification script**

`tools/adhkar/check_items.py`: for each du'a item, compare its Arabic (after stripping the `((`/`))` brackets, the `[…]` evening notes and the count notes in parentheses from the hisnmuslim text, and normalising whitespace) with the hisnmuslim.com chapter JSON entry of the same `ID`; print any character difference with both strings; exit 1 on a mismatch. Known legitimate differences to allow and list in the script's header: the hisnmuslim text writes the evening variants inside `[وإذا أمسى قال: …]` without tashkeel, so compare only the morning/`both` forms against it, and compare the evening forms against OpenDua's own `evening` variation. Also check: every id in the spec list present, counts as the table, 15 du'a ids in `text/en.json`, no empty strings, every `arabic` string contains at least one tashkeel mark (U+064B–U+0652).

- [ ] **Step 3: Run both scripts**

Run: `python3 -I tools/adhkar/build_items.py <catalogue path>` then `python3 -I tools/adhkar/check_items.py <hisnmuslim chapter json path>`
Expected: `check_items.py` exits 0. If it reports a real difference between OpenDua and hisnmuslim.com (not a bracket/note artefact), prefer the hisnmuslim.com wording for that word only after confirming it against the canon notes' text for that item (R1 §1 quotes each opening), and record the decision in `tools/adhkar/README.md`.

- [ ] **Step 4: Write the licence file and the README**

`docs/licences/OpenDua-CC-BY-4.0.txt`: the attribution text above verbatim, the licence URL `https://creativecommons.org/licenses/by/4.0/`, the developers-page URL `https://opendua.org/developers`, the dataset version `v0.0.4` and date retrieved `11 October 2026`, and the sentence "Used for: the Arabic structure, English translations and transliterations of the 19 morning-and-evening items in `files/adhkar`; the recordings are not used." `tools/adhkar/README.md`: what the two scripts do, how to run them, where the catalogue comes from, and that the hisnmuslim.com text is a verification witness only and is not checked in.

- [ ] **Step 5: Commit**

```bash
git add tools/adhkar shared/src/commonMain/composeResources/files/adhkar docs/licences/OpenDua-CC-BY-4.0.txt
git commit -m "adhkar: the nineteen undisputed items as resources, from OpenDua, checked against the Hisn text"
```

---

### Task 6: The adhkar domain — model, loader, sets, windows, progress

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/adhkar/AdhkarItem.kt`, `AdhkarCatalogue.kt`, `AdhkarSet.kt`, `AdhkarWindows.kt`, `AdhkarProgress.kt`; `shared/src/commonMain/kotlin/world/taqwa/app/settings/AdhkarStore.kt`
- Modify: `SettingsKeys.kt` (four keys)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/adhkar/AdhkarCatalogueTest.kt`, `AdhkarWindowsTest.kt`, `AdhkarProgressTest.kt`; `shared/src/androidHostTest/kotlin/world/taqwa/app/adhkar/AdhkarQuranReferencesTest.kt`

**Interfaces:**
- Consumes: Task 5's JSON.
- Produces:

```kotlin
enum class AdhkarSet { MORNING, EVENING }
enum class AdhkarKind { DUA, QURAN }
enum class QuranPrefix { ISTIADHAH, BASMALA }
data class QuranRef(val surah: Int, val ayah: Int?, val prefix: QuranPrefix?)
data class Source(val collection: String, val number: String)
data class AdhkarItem(val id: String, val hisn: Int, val kind: AdhkarKind, val sets: Set<AdhkarSet>, val count: Int, val countDaily: Boolean, val quran: QuranRef?, val arabic: Map<String, String>, val transliteration: Map<String, String>, val sources: List<Source>) {
    fun arabicFor(set: AdhkarSet): String?            // "both" or the set's own variant
    fun transliterationFor(set: AdhkarSet): String?
}
data class AdhkarText(val language: String, val items: Map<String, Map<String, String>>) { fun translationFor(id: String, set: AdhkarSet): String? }
object AdhkarCatalogue { fun parse(json: String): List<AdhkarItem>; fun parseText(json: String): AdhkarText; suspend fun load(): List<AdhkarItem> /* Res.readBytes("files/adhkar/items.json") */; suspend fun loadText(language: String): AdhkarText? /* null when the file is absent */ }
fun List<AdhkarItem>.pagesFor(set: AdhkarSet): List<AdhkarItem>   // book order, filtered by sets
enum class AdhkarPhase { BEST, MAKEUP, NONE }
data class AdhkarWindow(val set: AdhkarSet, val phase: AdhkarPhase, val until: Instant?)
object AdhkarWindows {
    fun setFor(now: Instant, today: DayPrayerTimes): AdhkarSet   // Fajr..Asr → MORNING, else EVENING
    fun windowFor(set: AdhkarSet, now: Instant, today: DayPrayerTimes): AdhkarWindow
    fun dateKey(set: AdhkarSet, today: DayPrayerTimes, zone: TimeZone): LocalDate   // the local date of that set's own prayer
}
data class AdhkarProgress(val date: LocalDate, val counts: List<Int>) { companion object { fun decode(raw: String?, pages: Int): AdhkarProgress?; } fun encode(): String }
class AdhkarStore(store: DataStore<Preferences>) { fun progress(set: AdhkarSet): Flow<String?>; suspend fun save(set: AdhkarSet, progress: AdhkarProgress); fun istighfar(): Flow<String?>; suspend fun saveIstighfar(date: LocalDate, count: Int); val translation: Flow<Boolean>; suspend fun setTranslation(on: Boolean) }
```

- [ ] **Step 1: Write the failing tests**

`AdhkarCatalogueTest` (commonTest, reads the real resource with `Res.readBytes` — the existing `ManifestProvider` tests show how resources are read in commonTest; if `Res.readBytes` is unavailable in commonTest on this branch, parse a string constant holding two items instead and move the resource-wide checks to the androidHostTest below):

```kotlin
@Test fun `the morning set has eighteen pages in book order`() = runTest {
    val items = AdhkarCatalogue.parse(itemsJson())
    val morning = items.pagesFor(AdhkarSet.MORNING)
    assertEquals(18, morning.size)
    assertEquals("hisn-075", morning.first().id)
    assertEquals("hisn-096", morning.last().id)
    assertEquals(15, items.pagesFor(AdhkarSet.EVENING).size)
}
@Test fun `the evening set drops the morning only items and adds the night refuge`() { … assertTrue(evening.none { it.id in setOf("hisn-090","hisn-093","hisn-094","hisn-095") }); assertEquals("hisn-097", evening.last().id) }
@Test fun `wording variants follow the set`() { val item = items.first { it.id == "hisn-078" }; assertTrue(item.arabicFor(AdhkarSet.MORNING)!!.contains("أَصْبَحْنَا")); assertTrue(item.arabicFor(AdhkarSet.EVENING)!!.contains("أَمْسَيْنَا")) }
@Test fun `counts match the book`() { assertEquals(100, byId("hisn-091").count); assertEquals(10, byId("hisn-092").count); assertEquals(3, byId("hisn-097").count); assertTrue(byId("hisn-096").countDaily) }
@Test fun `every dua has an english translation for each of its sets`() { … AdhkarCatalogue.parseText(enJson()).translationFor(id, set) != null for every dua item and set }
```

`AdhkarWindowsTest` (build a `DayPrayerTimes` the way `NotificationPlannerTest` or `TimelineBuilderTest` builds one — read one of them for the constructor): before Fajr → set EVENING (yesterday's), phase NONE; Fajr+10 min → MORNING BEST with `until` = sunrise; sunrise+1 h → MORNING MAKEUP until Dhuhr; Dhuhr+1 min → MORNING NONE; Asr+1 min → EVENING BEST until Maghrib; Maghrib+1 min → EVENING MAKEUP until Isha; Isha+1 min → EVENING NONE; `dateKey(MORNING)` is the Fajr instant's local date.

`AdhkarProgressTest`: `encode`/`decode` round trip `"2026-10-11|0,3,1"`; decode returns null for a different page count or a malformed string; a decoded progress with a date other than today is discarded by the caller (test the pure helper `AdhkarProgress.forDate(raw, date, pages)` that returns fresh zeros when the date differs).

`AdhkarQuranReferencesTest` (androidHostTest, opens the bundled `quran.db` as `QuranRepositoryDbTest` does): every `kind == QURAN` item's `surah`/`ayah` resolves (`ayahText` non-null, or `ayahs(surah)` non-empty for a whole surah).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.adhkar.*' -q`
Expected: compile failures.

- [ ] **Step 3: Implement**

Use `kotlinx.serialization` with `Json { ignoreUnknownKeys = true }` as `RecitationManifest.kt` does (copy its `ManifestJson` shape; serializable DTOs private to `AdhkarCatalogue.kt`, mapped to the public data classes). `pagesFor` keeps the file order. `AdhkarWindows`:

```kotlin
object AdhkarWindows {
    fun setFor(now: Instant, today: DayPrayerTimes): AdhkarSet =
        if (now >= today.time(Prayer.FAJR) && now < today.time(Prayer.ASR)) AdhkarSet.MORNING else AdhkarSet.EVENING

    fun windowFor(set: AdhkarSet, now: Instant, today: DayPrayerTimes): AdhkarWindow {
        val (open, best, late) = when (set) {
            AdhkarSet.MORNING -> Triple(today.time(Prayer.FAJR), today.time(Prayer.SUNRISE), today.time(Prayer.DHUHR))
            AdhkarSet.EVENING -> Triple(today.time(Prayer.ASR), today.time(Prayer.MAGHRIB), today.time(Prayer.ISHA))
        }
        return when {
            now < open || now >= late -> AdhkarWindow(set, AdhkarPhase.NONE, null)
            now < best -> AdhkarWindow(set, AdhkarPhase.BEST, best)
            else -> AdhkarWindow(set, AdhkarPhase.MAKEUP, late)
        }
    }

    fun dateKey(set: AdhkarSet, today: DayPrayerTimes, zone: TimeZone): LocalDate =
        today.time(if (set == AdhkarSet.MORNING) Prayer.FAJR else Prayer.ASR).toLocalDateTime(zone).date
}
```

Read `DayPrayerTimes` in `domain/Prayer.kt` for `time(prayer)` and whether sunrise can be null on a polar day; if `time(SUNRISE)` can be absent, treat the morning best phase as empty (open → `late` directly). Keys in `SettingsKeys`: `ADHKAR_MORNING = stringPreferencesKey("adhkar_morning")`, `ADHKAR_EVENING`, `ADHKAR_ISTIGHFAR`, `ADHKAR_TRANSLATION = booleanPreferencesKey("adhkar_translation")`. `AdhkarStore` follows `TasbeehStore.kt`'s shape (read it) over the same process-wide DataStore (`createDataStore()`), and is exposed on `AppContainer` as `val adhkarStore by lazy { AdhkarStore(dataStore) }` next to `tasbeehStore`.

- [ ] **Step 4: Run the tests and the suite**

Run: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.adhkar.*' -q` then `scripts/test.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/adhkar shared/src/commonMain/kotlin/world/taqwa/app/settings shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt shared/src/commonTest/kotlin/world/taqwa/app/adhkar shared/src/androidHostTest/kotlin/world/taqwa/app/adhkar
git commit -m "adhkar: the catalogue, the two sets, the windows and the day's progress"
```

---

### Task 7: The horizon glyph, the button, the route and the screen shell

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt` (after `drawMisbaha`), `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayScreen.kt` (`TodayScreen` params at 117–133, `ReadyActions` at 153–160, `CityAndDates` end of the `Row` at about line 392, `TasbeehButton` at 459–476), `shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt`, `shared/src/commonMain/kotlin/world/taqwa/app/App.kt` (the `when (screen)` branch next to `Screen.Tasbeeh`), `shared/src/commonMain/kotlin/world/taqwa/app/PrayerRoutes.kt` (`onOpenTasbeeh` wiring at about line 109; an `AdhkarRoute` next to `TasbeehRoute`), `shared/src/commonMain/kotlin/world/taqwa/app/AppEffects.kt` (`DebugScreenRequests`), eight `strings.xml`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/adhkar/AdhkarScreen.kt` (a placeholder screen: `BackChevron` + title; Task 8 fills it)
- Test: `shared/src/iosTest/kotlin/world/taqwa/app/feature/today/AdhkarButtonTest.kt`

**Interfaces:**
- Produces: `Screen.Adhkar(val set: AdhkarSet? = null)`; `TodayScreen(..., onOpenAdhkar: () -> Unit, ...)`; `ReadyActions.onOpenAdhkar`; `internal fun DrawScope.drawHorizon(tint: Color)`; `AdhkarRoute(container, navigator, set: AdhkarSet?)`; `AdhkarScreen(state, onBack, …)` (final signature in Task 8); strings `today_open_adhkar`, `adhkar_morning`, `adhkar_evening`.

- [ ] **Step 1: Strings** (append in all eight folders; check-strings clean):

| key | en | ar | bn | fr | id | tr | ur |
|---|---|---|---|---|---|---|---|
| `today_open_adhkar` | Adhkar | الأذكار | আযকার | Adhkar | Zikir | Zikirler | اذکار |
| `adhkar_morning` | Morning adhkar | أذكار الصباح | সকালের আযকার | Adhkar du matin | Zikir pagi | Sabah zikirleri | صبح کے اذکار |
| `adhkar_evening` | Evening adhkar | أذكار المساء | সন্ধ্যার আযকার | Adhkar du soir | Zikir petang | Akşam zikirleri | شام کے اذکار |

- [ ] **Step 2: Write the failing UI test**

Compose `TodayScreen` with a `TodayUiState.Ready` fixture (read `TodayPreviews.kt` for a ready-state builder) inside `CompositionLocalProvider(LocalTaqwaColors provides LightColors)`; `onNodeWithContentDescription(getString(Res.string.today_open_adhkar)).assertExists().performClick()`; assert `onOpenAdhkar` was called; also assert the misbaha's description (`today_open_tasbeeh`) still exists.

- [ ] **Step 3: Run it to verify it fails** — `./gradlew :shared:iosSimulatorArm64Test --tests '*AdhkarButtonTest' -q`; expected: compile failure.

- [ ] **Step 4: Implement**

`Glyphs.kt`:

```kotlin
/**
 * A sun on the horizon: the way to the morning and evening adhkar (spec §2.1), the one glyph
 * that means both halves of the day. A horizon line, the upper half of a disc resting on it,
 * and three rays — all at the glyph stroke, on the 16-unit grid.
 */
internal fun DrawScope.drawHorizon(tint: Color) {
    val u = size.width / 16f
    val stroke = glyphStroke()
    drawLine(tint, Offset(2f * u, 12f * u), Offset(14f * u, 12f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawArc(
        tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(4.6f * u, 8.6f * u), size = Size(6.8f * u, 6.8f * u), style = stroke,
    )
    drawLine(tint, Offset(8f * u, 5.6f * u), Offset(8f * u, 3.4f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(tint, Offset(3.9f * u, 8.2f * u), Offset(2.5f * u, 6.8f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(tint, Offset(12.1f * u, 8.2f * u), Offset(13.5f * u, 6.8f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
}
```

`TodayScreen.kt`: add `onOpenAdhkar: () -> Unit` to `TodayScreen` and `ReadyActions`; generalise `TasbeehButton` into `HeaderGlyphButton(description: String, onOpen: () -> Unit, draw: DrawScope.(Color) -> Unit)` with the same modifiers (offset −4 dp, 44 dp, circle, no ripple, `contentDescription`), and at the end of the `CityAndDates` row:

```kotlin
        HeaderGlyphButton(stringResource(Res.string.today_open_adhkar), actions.onOpenAdhkar) { drawHorizon(it) }
        HeaderGlyphButton(stringResource(Res.string.today_open_tasbeeh), actions.onOpenTasbeeh) { drawMisbaha(it) }
```

(the horizon before the misbaha, so the misbaha keeps the corner). `Screen.kt`: `data class Adhkar(val set: AdhkarSet? = null) : Screen` with KDoc. `PrayerRoutes.kt`: `onOpenAdhkar = { navigator.push(Screen.Adhkar()) }` and

```kotlin
@Composable
internal fun AdhkarRoute(container: AppContainer, navigator: Navigator, set: AdhkarSet?) {
    AdhkarScreen(set = set, onBack = { navigator.pop() })   // Task 8 replaces this with the view model
}
```

`App.kt`: `is Screen.Adhkar -> AdhkarRoute(container, navigator, screen.set)` next to the Tasbeeh branch. `AppEffects.DebugScreenRequests`: `"adhkar" -> { navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Adhkar()) }`. `AdhkarScreen.kt` placeholder: a `Column` with the Tasbeeh header row (`BackChevron`, title from `adhkar_morning`/`adhkar_evening`).

- [ ] **Step 5: Run the UI test, the suite, both builds** — expected green.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt shared/src/commonMain/kotlin/world/taqwa/app/App.kt shared/src/commonMain/kotlin/world/taqwa/app/PrayerRoutes.kt shared/src/commonMain/kotlin/world/taqwa/app/AppEffects.kt shared/src/commonMain/kotlin/world/taqwa/app/feature/adhkar shared/src/commonMain/composeResources shared/src/iosTest
git commit -m "today: a horizon glyph beside the misbaha opens the adhkar"
```

---

### Task 8: The Adhkar screen — view model, pager, counting, foot

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/adhkar/AdhkarViewModel.kt`, `AdhkarPage.kt`; rewrite `AdhkarScreen.kt`
- Modify: `PrayerRoutes.kt` (`AdhkarRoute` builds the view model), eight `strings.xml`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/adhkar/AdhkarViewModelTest.kt`; `shared/src/iosTest/kotlin/world/taqwa/app/feature/adhkar/AdhkarScreenTest.kt`

**Interfaces:**
- Consumes: Task 6 (`AdhkarCatalogue`, `AdhkarWindows`, `AdhkarProgress`, `AdhkarStore`), `TasbeehEngine`, `Haptics`, `PrayerTimesEngine.timesFor`, `SettingsRepository.location/prayerSettings/readingSettings`, `PlatformFormat`.
- Produces:

```kotlin
data class AdhkarPageUi(val item: AdhkarItem, val arabic: String?, val transliteration: String?, val translation: String?, val quran: QuranPageText?, val count: Int, val target: Int, val done: Boolean)
data class QuranPageText(val prefix: String?, val lines: List<Pair<String, Int?>> /* text to ayah number */, val translation: String?)
data class AdhkarUiState(val set: AdhkarSet, val phase: AdhkarPhase, val until: String?, val pages: List<AdhkarPageUi>, val currentPage: Int, val completed: Int, val showTranslation: Boolean, val arabicSizeSp: Int, val transliteration: Boolean, val loaded: Boolean)
class AdhkarViewModel(catalogue, textFor: suspend (String) -> AdhkarText?, quran: QuranSource, store: AdhkarStore, settings: SettingsRepository, engine: PrayerTimesEngine, haptics: Haptics, now: () -> Instant, format: PlatformFormat, language: String, scope) {
    val state: StateFlow<AdhkarUiState>
    fun open(set: AdhkarSet?)        // null → AdhkarWindows.setFor
    fun selectSet(set: AdhkarSet)
    fun goTo(page: Int)
    fun tap()                        // counts the current page
    fun setTranslation(on: Boolean)
    fun tick()                       // re-evaluates the phase once a minute
}
```

Strings (append, all eight folders): `adhkar_segment_morning` Morning / صباح / সকাল / Matin / Pagi / Sabah / صبح; `adhkar_segment_evening` Evening / مساء / সন্ধ্যা / Soir / Petang / Akşam / شام; `adhkar_progress` "%1$s of %2$s" (ar "%1$s من %2$s", bn "%2$s-এর মধ্যে %1$s", fr "%1$s sur %2$s", id "%1$s dari %2$s", tr "%1$s / %2$s", ur "%2$s میں سے %1$s"); `adhkar_once` ONCE / مرة واحدة / একবার / UNE FOIS / SEKALI / BİR KEZ / ایک بار; plural `adhkar_times` (en one "%1$s TIME" other "%1$s TIMES"; ar zero/one/two/few/many/other "%1$s مرة"/"مرة واحدة"/"مرتان"/"%1$s مرات"/"%1$s مرة"/"%1$s مرة"; bn "%1$s বার"; fr one "%1$s FOIS" other "%1$s FOIS"; id other "%1$s KALI"; tr one/other "%1$s KEZ"; ur one/other "%1$s بار"); `adhkar_times_today` "%1$s TIMES TODAY" / "%1$s مرة في اليوم" / "আজ %1$s বার" / "%1$s FOIS AUJOURD’HUI" / "%1$s KALI HARI INI" / "BUGÜN %1$s KEZ" / "آج %1$s بار"; `adhkar_of` "of %1$s" / "من %1$s" / "%1$s-এর মধ্যে" / "sur %1$s" / "dari %1$s" / "/ %1$s" / "%1$s میں سے"; `adhkar_until_sunrise` "Until sunrise · %1$s" / "حتى الشروق · %1$s" / "সূর্যোদয় পর্যন্ত · %1$s" / "Jusqu’au lever du soleil · %1$s" / "Hingga matahari terbit · %1$s" / "Güneş doğana kadar · %1$s" / "طلوعِ آفتاب تک · %1$s"; `adhkar_after_sunrise` "After sunrise · until %1$s" / "بعد الشروق · حتى %1$s" / "সূর্যোদয়ের পরে · %1$s পর্যন্ত" / "Après le lever du soleil · jusqu’à %1$s" / "Setelah matahari terbit · hingga %1$s" / "Güneş doğduktan sonra · %1$s’e kadar" / "طلوعِ آفتاب کے بعد · %1$s تک"; `adhkar_until_maghrib` "Until Maghrib · %1$s" (ar "حتى المغرب · %1$s", bn "মাগরিব পর্যন্ত · %1$s", fr "Jusqu’au Maghrib · %1$s", id "Hingga Maghrib · %1$s", tr "Akşama kadar · %1$s", ur "مغرب تک · %1$s"); `adhkar_after_maghrib` "After Maghrib · until %1$s" (ar "بعد المغرب · حتى %1$s", bn "মাগরিবের পরে · %1$s পর্যন্ত", fr "Après le Maghrib · jusqu’à %1$s", id "Setelah Maghrib · hingga %1$s", tr "Akşamdan sonra · %1$s’e kadar", ur "مغرب کے بعد · %1$s تک"); `adhkar_window_morning` "Fajr to sunrise" (ar "من الفجر إلى الشروق", bn "ফজর থেকে সূর্যোদয়", fr "Du Fajr au lever du soleil", id "Subuh hingga matahari terbit", tr "Sabahtan güneş doğana kadar", ur "فجر سے طلوعِ آفتاب تک"); `adhkar_window_evening` "Asr to Maghrib" (ar "من العصر إلى المغرب", bn "আসর থেকে মাগরিব", fr "De l’Asr au Maghrib", id "Asar hingga Maghrib", tr "İkindiden akşama kadar", ur "عصر سے مغرب تک"); `adhkar_a11y_count` Count / عدّ / গণনা / Compter / Hitung / Say / گنیں; `adhkar_a11y_previous` Previous / السابق / আগেরটি / Précédent / Sebelumnya / Önceki / پچھلا; `adhkar_a11y_next` Next / التالي / পরেরটি / Suivant / Berikutnya / Sonraki / اگلا; collection names `source_quran` Quran/القرآن/কুরআন/Coran/Al-Qur’an/Kur’an/قرآن, `source_bukhari` Bukhari/البخاري/বুখারী/Boukhari/Bukhari/Buhârî/بخاری, `source_muslim` Muslim/مسلم/মুসলিম/Mouslim/Muslim/Müslim/مسلم, `source_abu_dawud` Abu Dawud/أبو داود/আবু দাউদ/Abou Dawoud/Abu Dawud/Ebû Dâvûd/ابو داؤد, `source_tirmidhi` Tirmidhi/الترمذي/তিরমিযী/Tirmidhi/Tirmidzi/Tirmizî/ترمذی, `source_ibn_majah` Ibn Majah/ابن ماجه/ইবনে মাজাহ/Ibn Majah/Ibnu Majah/İbn Mâce/ابن ماجہ, `source_nasai` an-Nasa’i/النسائي/নাসাঈ/An-Nasa’i/An-Nasa’i/Nesâî/نسائی, `source_hakim` al-Hakim/الحاكم/হাকিম/Al-Hakim/Al-Hakim/Hâkim/حاکم, `source_ahmad` Ahmad/أحمد/আহমাদ/Ahmad/Ahmad/Ahmed/احمد. The page labels are upper-cased at runtime by the locale-aware rule in `i18n/Casing.kt` (read it), so store them in sentence case where a language has no upper case; the English/French/Indonesian/Turkish values above are already capitals.

- [ ] **Step 1: Write the failing view-model test**

Build the view model with: the real catalogue parsed from a string constant (or `Res.readBytes` if commonTest can), a text map for `en`, a fake `QuranSource` returning fixed strings, an in-memory `AdhkarStore` fake (make `AdhkarStore` an interface with a DataStore implementation and a `MemoryAdhkarStore` in the test), fake `SettingsRepository` pieces (there is a test fake in `TodayViewModelTest`; reuse it), an engine fixture (as `AdhkarWindowsTest`), a fake `Haptics` that records calls, `now` fixed at Fajr + 10 min, `EnglishPlatformFormat`, language `"en"`. Tests:

```kotlin
@Test fun `opening with no set picks the half day`()            // morning at Fajr+10
@Test fun `opens on the first undone page`()                     // after saving counts 1,3 for pages 0,1 it opens on page 2 … (page 1 target 3 done)
@Test fun `a tap counts and the last tap completes the page and advances`()   // haptics: count ×2 then setComplete; currentPage moves on
@Test fun `taps on a completed page do nothing`()
@Test fun `progress is counted never visited`()                   // goTo(5) leaves pages 2..4 undone; completed == 2
@Test fun `the istighfar page shares one daily counter across sets`()
@Test fun `the phase caption follows the window`()               // until == "6:12"-style via EnglishPlatformFormat
@Test fun `switching sets keeps each set's progress`()
```

- [ ] **Step 2: Run to verify they fail** — compile errors.

- [ ] **Step 3: Implement the view model**

`open(set)`: resolve `today = engine.timesFor(location, localDate, prayerSettings)` (location from `settings.location.first()`; if null, state stays `loaded = false` with an empty pager — the screen shows the title only), the set, the window, the pages via `pagesFor`, texts (`arabicFor`, `transliterationFor`, `textFor(language)?.translationFor`), Quran page text via `quran.ayahText`/`ayahs` and `quran.translationText(readingSettings.translationId, …)`, progress via `AdhkarProgress.forDate(store.progress(set).first(), dateKey, pages.size)`, the istighfar count via `store.istighfar()`, `currentPage` = first index whose `count < target`, or `pages.size` (the done page). `tap()`: if `currentPage >= pages.size` return; if page done return; `TasbeehEngine.tap(TasbeehState(id, count, 1), TasbeehPreset(id, listOf(DhikrPart(Dhikr(id, arabic ?: ""), target))))` → `Tick` → `haptics.count()`, `SetComplete` → `haptics.setComplete()`; write the new count at once (`store.save` / `saveIstighfar`); on completion, after `delay(400)` move `currentPage` to the next undone page (or `pages.size`). `tick()` recomputes the window and `until` text (`format.clockTime(h, m)` of the window's `until` in the location's zone). `selectSet` re-opens with the other set. Phase caption strings are resolved in the screen from `phase`/`until`/`set` (the view model exposes `until` as a formatted clock string or null).

- [ ] **Step 4: Implement the screen**

`AdhkarScreen(state: AdhkarUiState, onBack, onSelectSet: (AdhkarSet) -> Unit, onGoTo: (Int) -> Unit, onTap: () -> Unit, onPlay: (() -> Unit)?, playing: Boolean, onOpenReading: () -> Unit)` as spec §3.1: `Column(Modifier.fillMaxSize().contentWidth())`; header `Row` = `BackChevron(onBack)`, a `Column(Modifier.weight(1f))` with the title (`TaqwaText.rowLabel` ExtraBold, `adhkar_morning`/`adhkar_evening`) and the phase caption (`caption` 12 sp `textSecondary`: BEST → `adhkar_until_sunrise`/`adhkar_until_maghrib` with `until`; MAKEUP → `adhkar_after_sunrise`/`adhkar_after_maghrib`; NONE → `adhkar_window_morning`/`_evening`), then the speaker `GlyphButton` only when `onPlay != null` (Task 10 supplies it; use `drawSpeaker`/`drawEqualiser` from `Glyphs.kt` as `ReaderHeader.kt` does), then the "Aa" button (text 13 sp ExtraBold, 44 dp target, description `quran_sheet_title` or the reader's existing "Aa" description string — read `ReaderHeader.kt` and reuse its string). Then 12 dp; `TaqwaSegmented(listOf(segment_morning, segment_evening), selectedIndex = set.ordinal) { onSelectSet(AdhkarSet.entries[it]) }` centred in a `Box(Modifier.fillMaxWidth())`. Then 12 dp; the progress row at the gutter: `Text(adhkar_progress(completed, pages.size))` caption `textSecondary` + a 3 dp bar (`Box` hairline background, inner `Box` accent width fraction, radius 2 dp). Then `HorizontalPager(state = pagerState, pageCount = pages.size + 1, Modifier.weight(1f))` — keep `pagerState.currentPage` in step with `state.currentPage` both ways (`LaunchedEffect(state.currentPage) { pagerState.animateScrollToPage(...) }` and `LaunchedEffect(pagerState.currentPage) { onGoTo(...) }`); page index `pages.size` is the done page (Task 9; render a placeholder `Text("done")` here). Each real page is `AdhkarPageView(page, arabicSizeSp, showTranslation, transliteration, onTap)`: a `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).clickable(indication = null, interactionSource = …, onClick = onTap).padding(horizontal = 24.dp))` with: page label (`sectionLabel` accent; `adhkar_once` when target == 1, `adhkar_times_today` when `countDaily`, else the `adhkar_times` plural; upper-cased through `Casing`), 16 dp, the Arabic block (Task 10 handles `kind == QURAN`; here the du'a block: `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Text(arabic, fontFamily = FontFamily.Default, fontSize = (arabicSizeSp - 2).sp, lineHeight = ((arabicSizeSp - 2) * 1.8).sp, letterSpacing = 0.sp, textAlign = TextAlign.Right, color = textPrimary, modifier = Modifier.fillMaxWidth()) }`), 8 dp, transliteration (Latin UIs only — `isLatinScriptUi()` — and `state.transliteration`): caption 13 sp italic `textTertiary` in an LTR provider, 10 dp, translation (when `showTranslation` and not under the Arabic UI — `uiLanguage() != UiLanguage.ARABIC`): caption 14/21 `textSecondary` in a provider whose direction follows the UI language (RTL for ur), 14 dp, the reference row: `sources.joinToString(" · ") { sourceName(it.collection) + " " + it.number }` caption 12 `textTertiary`, 24 dp bottom. The foot: a `Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = CenterVertically, horizontalArrangement = SpaceBetween)` with a previous chevron (44 dp target, `drawChevron` mirrored, `textTertiary`, description `adhkar_a11y_previous`; disabled on page 0), a centre `Row` with `Box(Modifier.size(64.dp)) { RingArc(progress = count/target, diameter = 64.dp); Text(format.localizedDigits(count), style = TaqwaText.countdown.copy(fontSize = 22.sp)) }` shrink-to-fit via `BasicText` `autoSize` (Compose 1.8+ `TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 22.sp, stepSize = 0.5.sp)`) and `Text(adhkar_of(target))` caption beside it — the whole centre invisible (`alpha = 0f`, slot kept) when `target == 1`; a next chevron (description `adhkar_a11y_next`; disabled on the last page). Above the foot, `Text(tasbeeh_hint)` 12 sp `textTertiary` centred, shown only while `completed == 0 && pages.all { it.count == 0 }`. Semantics: the page column `semantics(mergeDescendants = true) { customActions = listOf(CustomAccessibilityAction(countLabel) { onTap(); true }) }`; the count `Text` has `liveRegion = LiveRegionMode.Polite` and `contentDescription = adhkar_progress(count, target)`. Wrap the screen's root in `KeepScreenOn()` as `TasbeehScreen.kt` does. Landscape: same column (no separate body).

`AdhkarRoute`: `val vm = remember { AdhkarViewModel(catalogue = AdhkarCatalogue::load, textFor = AdhkarCatalogue::loadText, quran = container.quranRepository, store = container.adhkarStore, settings = container.settingsRepository, engine = container.prayerTimesEngine, haptics = createHaptics(), now = { Clock.System.now() }, format = LocalPlatformFormat.current /* capture outside remember */, language = uiLanguage(), scope = rememberCoroutineScope()) }` (if the format or language changes, the route re-keys the `remember` on them, as `TasbeehRoute` explains); `LaunchedEffect(set) { vm.open(set) }`; a minute tick while STARTED (`repeatOnLifecycle` as `PrayerRoutes` does for Today) calling `vm.tick()`; `AdhkarScreen(state, onBack = { navigator.pop() }, onSelectSet = vm::selectSet, onGoTo = vm::goTo, onTap = vm::tap, onPlay = null, playing = false, onOpenReading = { /* Task 9 */ })`.

- [ ] **Step 5: Write the UI test** (`AdhkarScreenTest`, iosTest): compose `AdhkarScreen` with a two-page fixture state (page 0 target 3 count 1; page 1 target 1); assert the page label `adhkar_times` plural for 3 is shown, the count "1" is shown, `onNodeWithText(getString(Res.string.adhkar_progress, "0", "2"))` exists; `performSemanticsAction(SemanticsActions.OnClick)` on the page node calls `onTap`; the next chevron's description exists.

- [ ] **Step 6: Run the tests, the suite, check-strings, both builds** — green.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/adhkar shared/src/commonMain/kotlin/world/taqwa/app/PrayerRoutes.kt shared/src/commonMain/composeResources shared/src/commonTest/kotlin/world/taqwa/app/feature/adhkar shared/src/iosTest
git commit -m "adhkar: the session screen, one dhikr at a time, counted on the tasbeeh engine"
```

---

### Task 9: The done page, the reading sheet and the resume

**Files:**
- Modify: `AdhkarScreen.kt`, `AdhkarViewModel.kt`, `AdhkarRoute` in `PrayerRoutes.kt`; create `feature/adhkar/AdhkarReadingSheet.kt`; eight `strings.xml`
- Test: extend `AdhkarViewModelTest` and `AdhkarScreenTest`

**Interfaces:**
- Consumes: Task 8; `ReadingSettings` + `SettingsRepository.setReadingSettings` (read `SettingsRepository.kt` lines 218–245 for the exact names), `AdhkarStore.translation`.
- Produces: `AdhkarReadingSheet(settings: ReadingSettings, showTranslation: Boolean, onChange: (ReadingSettings) -> Unit, onTranslation: (Boolean) -> Unit, onDismiss: () -> Unit)`.

Strings (append, eight folders): `adhkar_done_morning` "Morning adhkar · done" (ar "أذكار الصباح · تمّت", bn "সকালের আযকার · সম্পন্ন", fr "Adhkar du matin · terminés", id "Zikir pagi · selesai", tr "Sabah zikirleri · tamamlandı", ur "صبح کے اذکار · مکمل"); `adhkar_done_evening` likewise with the evening name; `adhkar_next_evening` "Evening adhkar open after Asr" (ar "أذكار المساء بعد العصر", bn "আসরের পরে সন্ধ্যার আযকার", fr "Adhkar du soir après l’Asr", id "Zikir petang setelah Asar", tr "Akşam zikirleri ikindiden sonra", ur "عصر کے بعد شام کے اذکار"); `adhkar_next_morning` "Morning adhkar open after Fajr" (ar "أذكار الصباح بعد الفجر", bn "ফজরের পরে সকালের আযকার", fr "Adhkar du matin après le Fajr", id "Zikir pagi setelah Subuh", tr "Sabah zikirleri sabah namazından sonra", ur "فجر کے بعد صبح کے اذکار"); `adhkar_back` "Back to prayer times" (ar "العودة إلى مواقيت الصلاة", bn "নামাজের সময়ে ফিরে যান", fr "Retour aux horaires de prière", id "Kembali ke waktu salat", tr "Namaz vakitlerine dön", ur "نماز کے اوقات پر واپس"); `adhkar_reading` "Reading" (ar "القراءة", bn "পড়া", fr "Lecture", id "Bacaan", tr "Okuma", ur "پڑھائی"); `adhkar_translation` "Translation" (ar "الترجمة", bn "অনুবাদ", fr "Traduction", id "Terjemahan", tr "Çeviri", ur "ترجمہ").

- [ ] **Step 1: Failing tests** — view model: `completing the last page fires the firm haptic once and lands on the done page` (`haptics.setComplete` called once; `currentPage == pages.size`); `reopening a finished set opens on the done page`; `the translation toggle persists` (store fake). Screen: the done page shows `adhkar_done_morning` and the `adhkar_back` link whose click calls `onBack`.

- [ ] **Step 2: Implement** — done page: `Column(fillMaxSize, Center)`: a 44 dp `Box` with `accent.copy(alpha = 0.18f)` circle holding `CheckMark()` (20 dp), 16 dp, `Text(done string, TaqwaText.rowLabel)`, 6 dp, `Text(next string, caption, textSecondary)`, 28 dp, `TaqwaTextLink(adhkar_back, onBack)`. The reading sheet: `TaqwaBottomSheet` with a `Column` (24 dp horizontal, 24 dp bottom + navigation inset) holding `Text(adhkar_reading, screenTitle 20 sp)`, the size row + `Slider` copied from `ReadingSheet.kt` lines 148–199 (same colours, thumb and track; `enabled = true`; preview line = the current page's first six Arabic words at the live size, du'a face), then a card with `TaqwaRow(quran_sheet_transliteration-or-whatever the reader's key is named — read `ReadingSheet.kt`)` toggle (Latin UIs only) and `TaqwaRow(adhkar_translation, trailing = { TaqwaToggle(showTranslation, onTranslation) })` (not under the Arabic UI). Wire `onOpenReading` to a `var readingOpen` in the route; `onChange = { scope.launch { settings.setReadingSettings(it) } }` (the exact setter name from `SettingsRepository`); `onTranslation = vm::setTranslation`. The view model reads `settings.readingSettings` for `arabicSizeSp` and `transliteration` and `store.translation` for `showTranslation`, as flows combined into state.

- [ ] **Step 3: Run the tests, suite, check-strings, builds** — green.

- [ ] **Step 4: Commit** — `git commit -m "adhkar: the done page and the reading sheet"` (add the files above).

---

### Task 10: Quranic pages and the reciter

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.kt` (the `load` expect gains `ayahs: List<Int>? = null`), `shared/src/androidMain/.../recitation/RecitationPlayer.android.kt` (`load` at 150–186), `shared/src/iosMain/.../recitation/RecitationPlayer.ios.kt` (`load` at 240–280), `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/RecitationController.kt` (new `requestPlayAyahs`, advance suppression, `stopIfAdhkar`), `feature/recitation/RecitationPorts.kt` (the `PlayerPort` signature), `AdhkarPage.kt` (Quran block), `AdhkarViewModel.kt` (Quran texts), `AdhkarScreen.kt` (speaker), `PrayerRoutes.kt` (`onPlay`, `playing`, stop on leave)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/RecitationQueueTest.kt` (a subset queue), `feature/recitation/RecitationControllerTest.kt` (no advance after a subset play; read how that test fakes the ports)

**Interfaces:**
- Produces: `RecitationController.requestPlayAyahs(surah: Int, ayahs: List<Int>, title: String, subtitle: String)`; `RecitationController.stopAdhkar()`; `RecitationController.adhkarPlaying: StateFlow<Boolean>`.

- [ ] **Step 1: Failing tests** — `RecitationQueue(2, listOf(255), 300, emptySet())` has one `Ayah(255)` and no gap; controller test: after `requestPlayAyahs(2, listOf(255), …)` and the player emitting `surahEnds(2)`, no `load` of surah 3 follows and the session stops.

- [ ] **Step 2: Implement** — common `load(..., speech: SpokenTranslation? = null, ayahs: List<Int>? = null)`; Android: `val built = if (ayahs != null) RecitationQueue(surah, ayahs.filter { index.rangeOf(it) != null }, reciter.gapMs.toLong(), emptySet()) else RecitationQueue.of(...)`; iOS: filter `split.files.keys` to `ayahs` when given. Controller: `requestPlayAyahs` = `engage()`; if `surah in downloaded.value` → `adhkarSession = true; player.load(reciter, surah, ayahs.first(), NowPlayingText(title, subtitle), null, ayahs); player.play()` else the existing `playOrOffer(surah, ayahs.first())` path with a `pendingAyahs` so the download sheet's confirm plays the subset; in `advance(ended)`: `if (adhkarSession) { adhkarSession = false; player.stop(); return }`; `stopAdhkar()` stops if `adhkarSession`; `adhkarPlaying` derives from `player.state` playing && `adhkarSession`. `AdhkarPage` Quran block: when `page.quran != null`, render `prefix` (istiʿadhah in the du'a face at `size − 6` `textSecondary`, or the reader's basmala line — read `ReaderScreen.kt` line 316–326 and reuse its composable if it is reusable, else copy its three lines) then for each `(text, ayahNumber)` line `Text(buildAnnotatedString { append(text); if (ayahNumber != null) { append(QuranText.MARKER_SEPARATOR); withStyle(SpanStyle(color = accent)) { append(QuranText.arabicIndic(ayahNumber)) } } }, style = TaqwaText.quran(arabicSizeSp), fontFamily = mushafFamily(), color = textPrimary)` inside an RTL provider, with `clearAndSetSemantics { contentDescription = referenceLine }` on the block (never voiced by a screen reader); the translation from the view model (`quran.translationText(readingSettings.translationId, surah, ayah)` or the surah's texts joined with spaces for a whole surah). The header speaker: `onPlay = { vm.playCurrent(controller) }` → `requestPlayAyahs(surah, listOf(ayah) or 1..n, title = adhkar_morning/evening text, subtitle = surah name + reference)`; `playing` from `controller.adhkarPlaying`; stop on page change and on leaving (`DisposableEffect(Unit) { onDispose { controller.stopAdhkar() } }` in the route; `LaunchedEffect(state.currentPage) { controller.stopAdhkar() }`).

- [ ] **Step 3: Run tests, suite, both builds; then on the emulator** (Task 14 does the full pass; here just `assembleDebug`).

- [ ] **Step 4: Commit** — `git commit -m "adhkar: the Quranic pages read from quran.db and play through the reciter"`.

---

### Task 11: Bundled Alafasy clips for the first run

**Files:**
- Create: `tools/quran-audio/adhkar_clips.py`, `shared/src/commonMain/composeResources/files/adhkar/clips/ar.alafasy/002-255.mp3`, `112.mp3`, `113.mp3`, `114.mp3`
- Modify: `recitation/ManifestProvider.kt` (an `adhkarClipBytes(reciterId, surah, ayah?)` loader beside `recitationPreviewBytes`), `RecitationController.kt` (`requestPlayAyahs` plays the clip through the existing `ClipPort` when `reciter.id == "ar.alafasy"` and the surah is not downloaded), `assets/audio/README.md` or `tools/quran-audio/README.md` (provenance), `docs/ATTRIBUTION.md` (one sentence under Recitations)
- Test: `tools/quran-audio/check_adhkar_clips.py` (sizes and that each clip's bytes equal the ayah range in the source `.taqa`); controller test: Alafasy + not downloaded → the clip port is asked, the player is not.

- [ ] **Step 1:** Read `tools/quran-audio/pack.py` and `check_previews.py` to see how the previews are cut from the `.taqa` containers (the container format is in `shared/src/commonMain/kotlin/world/taqwa/app/recitation/Taqa.kt`: 12-byte header, JSON index with per-ayah `off`/`len`, then raw MP3 bytes). Write `adhkar_clips.py` to cut 2:255 and surahs 112–114 (all ayahs concatenated, byte-for-byte as the previews are) from Alafasy's containers (download them with the existing `fetch.py` if they are not in the pipeline cache; the manifest's `base` URL and the release name are in `files/recitation/manifest.json`).
- [ ] **Step 2:** Run it; check the four files exist and their total is under 2 MB; run `check_adhkar_clips.py`.
- [ ] **Step 3:** Loader + controller branch + the failing-then-passing controller test; `ClipPlayer.play(bytes) { adhkarPlaying = false }`.
- [ ] **Step 4:** Docs: provenance (same corpus, same licence as the reciters; which containers, which byte ranges, the date), the ATTRIBUTION sentence.
- [ ] **Step 5:** `scripts/test.sh`, both builds, commit: `git commit -m "adhkar: Alafasy's Ayat al-Kursi and the Quls travel with the app for the first run"`.

---

### Task 12: The reminder

**Files:**
- Modify: `domain/NotificationSettings.kt` (`adhkar: Boolean = false`, `adhkarSound: PrayerSound = PrayerSound.NOTIFICATION`), `settings/SettingsKeys.kt` (`ADHKAR_ENABLED`, `ADHKAR_SOUND`), `settings/SettingsRepository.kt` (read at 157–167, write at 535–536), `notifications/ScheduledNotification.kt` (`ADHKAR_MORNING`, `ADHKAR_EVENING` kinds), `notifications/NotificationPlanner.kt` (`windowDaysFor` + two entries per day + the priority fill), `notifications/NotificationChannels.kt` (`adhkar_<sound>` base, `allAdhkarChannelIds()`), `notifications/NotificationCopy.kt` + `LocalizedNotificationCopy.kt` (`Words.adhkarMorning`, `adhkarEvening`, `adhkarMorningBody`, `adhkarEveningBody` ×7, `title`/`body` branches), `notifications/NotificationScheduler.kt` (`fun cancel(ids: Set<String>)`), `shared/src/androidMain/.../notifications/AndroidNotificationScheduler.kt` (inexact for adhkar kinds; `cancel`; channel creation + stale deletion for the adhkar ids — read `createChannels`/`deleteStaleChannels` there), `PrayerAlarmReceiver.kt` (content intent extra `open_adhkar` for adhkar kinds), `shared/src/iosMain/.../notifications/IosNotificationScheduler.kt` (`cancel`), `feature/settings/NotificationSettingsScreen.kt` (card after Tahajjud), `SettingsRoutes.kt` (two callbacks), `nav/LaunchRequests.kt` (`pendingAdhkar`, `ANDROID_EXTRA_OPEN_ADHKAR`), `androidApp/.../MainActivity.kt` (record + strip), `AppEffects.kt` (`AdhkarLaunchRequests`), `App.kt` (collect it), `AdhkarViewModel.kt` (on set completion → `scheduler.cancel(todayIds)` via a port), eight `strings.xml`
- Test: `NotificationPlannerTest.kt` (entries, clamps, priority), `NotificationChannelsTest` if present, `LocalizedNotificationCopyTest` (titles in seven languages), `AdhkarViewModelTest` (cancel on done)

Strings (append): `notifications_adhkar` "Morning and evening adhkar" (ar "أذكار الصباح والمساء", bn "সকাল ও সন্ধ্যার আযকার", fr "Adhkar du matin et du soir", id "Zikir pagi dan petang", tr "Sabah ve akşam zikirleri", ur "صبح و شام کے اذکار"); `notifications_adhkar_subtitle` "Half an hour after Fajr and Asr" (ar "بعد الفجر والعصر بنصف ساعة", bn "ফজর ও আসরের আধা ঘণ্টা পরে", fr "Une demi-heure après le Fajr et l’Asr", id "Setengah jam setelah Subuh dan Asar", tr "Sabah ve ikindiden yarım saat sonra", ur "فجر اور عصر کے آدھے گھنٹے بعد"); `notifications_adhkar_sound` = the existing `notifications_tahajjud_sound` wording ("Sound") per language — reuse that key instead of adding one. `Words` (Kotlin, not resources): titles = the `adhkar_morning`/`adhkar_evening` values above; bodies: en "Until sunrise · {time}" / "Until Maghrib · {time}", ar "حتى الشروق · {time}" / "حتى المغرب · {time}", bn "সূর্যোদয় পর্যন্ত · {time}" / "মাগরিব পর্যন্ত · {time}", fr "Jusqu’au lever du soleil · {time}" / "Jusqu’au Maghrib · {time}", id "Hingga matahari terbit · {time}" / "Hingga Maghrib · {time}", tr "Güneş doğana kadar · {time}" / "Akşama kadar · {time}", ur "طلوعِ آفتاب تک · {time}" / "مغرب تک · {time}"; `{time}` is the window's end (sunrise / Maghrib), isolated.

- [ ] **Step 1: Failing planner tests**

```kotlin
@Test fun `adhkar entries sit half an hour after fajr and asr`()        // ids ADHKAR-MORNING-<date>, ADHKAR-EVENING-<date>; instants fajr+30m, asr+30m; sound = adhkarSound; prayer = FAJR / ASR
@Test fun `adhkar entries are clamped inside the best window`()         // with sunrise at fajr+20m → entry at sunrise−5m
@Test fun `adhkar never displaces a prayer within the capacity`()       // capacity small: all PRAYER entries of the window present, adhkar fills what is left
@Test fun `window days count the adhkar entries`()                       // 64 / (5 + 2) = 9
```

- [ ] **Step 2: Implement** — in `plan`, after the Tahajjud block, when `notifications.adhkar`: morning at `minOf(fajr + 30.minutes, sunrise - 5.minutes)` (skip if that is ≤ fajr), evening at `minOf(asr + 30.minutes, maghrib - 5.minutes)` (skip if ≤ asr), both only when `> from`, `prayer` = FAJR / ASR, `kind` = the new kinds, `sound = notifications.adhkarSound`, `title = copy.title(prayer, kind)`, `body = copy.body(prayer, kind, formatClockTime(sunrise or maghrib, tz), 0)`. Replace the final `out.sortedBy { it.instant }.take(capacity)` with a priority fill: `val byPriority = out.sortedBy { it.instant }; val kept = mutableListOf<ScheduledNotification>(); for (kind in listOf(PRAYER, REMINDER, TAHAJJUD, ADHKAR_MORNING, ADHKAR_EVENING)) kept += byPriority.filter { it.kind == kind }.take(capacity - kept.size); return kept.sortedBy { it.instant }`. `windowDaysFor`: `+ (if (notifications.adhkar) 2 else 0)`. Channels: `base()` returns `"adhkar_${sound.name.lowercase()}"` for the two adhkar kinds; `allAdhkarChannelIds()` like `allTahajjudChannelIds()`; the Android scheduler's channel bookkeeping enumerates it (read how Tahajjud's are created/deleted and mirror it). `EnglishNotificationCopy` + `LocalizedNotificationCopy` branches for the two kinds (`channelName` = title + sound). Android: `schedule(entry)`: for adhkar kinds always `setAndAllowWhileIdle`; `cancel(ids)`: `alarmManager.cancel(pendingIntentFor(id))` + `NotificationManagerCompat.cancel(requestCodeFor(id))` for each; receiver: when `kind` is an adhkar kind, add `putExtra(LaunchRequests.ANDROID_EXTRA_OPEN_ADHKAR, if (kind == ADHKAR_MORNING) "morning" else "evening")` to the launch intent. iOS: `cancel(ids)` = `center.removePendingNotificationRequestsWithIdentifiers(ids.toList())` and `removeDeliveredNotificationsWithIdentifiers`. `LaunchRequests`: `pendingAdhkar: StateFlow<AdhkarSet?>`, `openAdhkar(set)`, `consumeAdhkar()`, `const val ANDROID_EXTRA_OPEN_ADHKAR = "open_adhkar"`; `MainActivity.recordAyahRequest`: read the string extra, map `"morning"`/`"evening"`, strip it. `AppEffects.AdhkarLaunchRequests(settings, navigator)`: after onboarding, collect `pendingAdhkar` → `navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Adhkar(set)); LaunchRequests.consumeAdhkar()`; call it from `App` where `PlayingLaunchRequests` is called. Notifications screen: a `SettingsCard` after the Tahajjud card, `Spacer(28.dp)` before it, the row + `AnimatedVisibility(settings.adhkar)` sound row as the Tahajjud card does, a sheet using `SoundSheet` with `NotificationSettings.TahajjudSounds`; route callbacks `onToggleAdhkar`/`onPickAdhkarSound` copying the Tahajjud ones. View model: constructor gains `cancelReminder: (AdhkarSet, LocalDate) -> Unit`; the route passes `{ set, date -> container.notificationScheduler.cancel(setOf("ADHKAR-${set.name}-$date")) }` — expose the scheduler on `AppContainer` (it is created inline in `notificationCoordinator`; lift it to a `val notificationScheduler = createNotificationScheduler()`).

- [ ] **Step 3: iOS tap (optional, last)** — if time allows: in `iosApp/iosApp/iOSApp.swift` the `UNUserNotificationCenterDelegate` gains `userNotificationCenter(_:didReceive:withCompletionHandler:)` reading the request identifier prefix `ADHKAR-MORNING`/`ADHKAR-EVENING` and calling a new Kotlin entry `LaunchRequests_iosKt.openAdhkar(morning: Bool)` in `shared/src/iosMain/kotlin/world/taqwa/app/nav/LaunchRequests.ios.kt` (beside `openAyahFromWidget`). Otherwise skip and note it in the report.

- [ ] **Step 4: Run everything, check-strings, both builds; commit** — `git commit -m "notifications: an opt-in adhkar reminder after Fajr and Asr"`.

---

### Task 13: The five translations, the credits and the remaining strings

**Files:**
- Create: `shared/src/commonMain/composeResources/files/adhkar/text/{bn,fr,id,tr,ur}.json`
- Modify: `docs/ATTRIBUTION.md`, `NOTICE`, `README.md` (credits paragraph), `feature/settings/AttributionScreen.kt` (`Credit(credit_adhkar, credit_adhkar_detail, source = "opendua.org")`), eight `strings.xml` (`credit_adhkar`, `credit_adhkar_detail`), `tools/adhkar/check_items.py` (every shipped language has every du'a id)
- Test: `AdhkarCatalogueTest` gains `every shipped language file carries every dua` (loop over the six languages)

- [ ] **Step 1:** Translate the 15 du'a texts (morning and evening variants for `hisn-077` and `hisn-078`) into Bengali, French, Indonesian, Turkish and Urdu **from the Arabic**, using OpenDua's English only as a cross-check and keeping these rules: Allah's name untranslated ("Allah"); the Prophet's name followed by ﷺ written as the language writes it ("ﷺ" is fine in all five); "Lord" as the language's usual word for رب; no explanatory additions; a line per Arabic sentence boundary; typographic apostrophes. Write `tools/adhkar/TRANSLATIONS-FOR-REVIEW.md` listing, per language, every id with the Arabic, the English and the draft, headed "Drafts by Claude, 11 October 2026, for one reviewer per language; a language ships only after review." The Quran items need no entry (the app's own translations are used).
- [ ] **Step 2:** `credit_adhkar` "Adhkar" (ar "الأذكار", bn "আযকার", fr "Adhkar", id "Zikir", tr "Zikirler", ur "اذکار"); `credit_adhkar_detail` "Morning and evening adhkar from Hisn al-Muslim by Sa‘id ibn ‘Ali ibn Wahf al-Qahtani; Arabic structure, English and transliteration from the OpenDua dataset (Human Development Fund), CC BY 4.0." (translate into the seven languages in the same register as `credit_recitations_detail`). `ATTRIBUTION.md` bullet, `NOTICE` clause, README credit line, the `Credit` on the screen (read `AttributionScreen.kt` lines 118–141 for the list).
- [ ] **Step 3:** `python3 -I tools/adhkar/check_items.py …` with the language check; `scripts/test.sh`; `scripts/check-strings.sh`; both builds.
- [ ] **Step 4:** Commit — `git commit -m "adhkar: five translation drafts for review, and the credits"`.

---

### Task 14: Devices, the build log and the report

**Files:**
- Modify: `docs/BUILD-LOG.md` (an entry for 11 October 2026)
- Create: screenshots under the session scratchpad (not the repo)

- [ ] **Step 1:** Boot a throwaway emulator on port **5560** (`emulator -avd Pixel_8_Pro -port 5560 -no-window -gpu swiftshader_indirect -no-snapshot -read-only`; never 5554/5556, never the S23 or the LoopPhone, which other sessions use), `adb -s emulator-5560 shell settings put global hide_error_dialogs 1`, install the debug APK.
- [ ] **Step 2:** 12-hour: in Settings › Prayer times pick 12-hour; capture the Prayer screen, the Timetable screen, Settings › Notifications (then trigger a plan: toggle a sound and read the posted notification's body with `adb shell dumpsys notification --noredact | grep -i "PM\|AM"` after `am broadcast` of a test alarm if the harness has one, else check the planned bodies through the debug log), the prayer widget via the Appearance preview; repeat under Arabic (`-AppleLanguages` is iOS; on Android set the app language through the system per-app language page via `adb shell cmd locale`? — use the debug harness `--es cmd lang` if it exists, else `settings put system system_locales ar-EG`), Turkish and Bengali; at 200 % font (`settings put system font_scale 2.0`). Capture before/after pairs at 100 % to prove nothing else moved.
- [ ] **Step 3:** Adhkar: open from the glyph in each phase by moving the emulator clock (`adb shell date` needs root; instead use the debug harness `screen adhkar` and verify the caption against the real clock, and unit tests cover the other phases); count through three pages, leave, return (resume); finish a set (done page); the reading sheet; a Quran page: Play with Alafasy and no download (the clip), then with a downloaded Al-Ikhlas; the reminder toggle and a posted reminder; TalkBack tree via `uiautomator dump` for the page group, the count live region and the glyph's description; Arabic UI mirrored.
- [ ] **Step 4:** iOS: a dedicated simulator (create one; never the booted iPhone 17 Pro of another session); if `xcrun simctl boot` is refused by the permission classifier, stop and record "iOS device pass not done" in the report rather than working around it.
- [ ] **Step 5:** `docs/BUILD-LOG.md` entry: what shipped, test counts (Android, iOS), devices and what was checked, the licence additions, what was cut (iOS tap routing if so), the translation review requirement.
- [ ] **Step 6:** Commit — `git commit -m "docs: build log for the adhkar feature and the 12-hour clock"`; then report to the Lead (branch head, test counts, device pass, new resources and licences, store needs: one new screenshot of the Adhkar screen and release notes mentioning both features) and leave the morning report for Mohamed with the translation drafts' path.

---

## Self-review

- Spec coverage: §1.1–1.6 → Tasks 1–4; §2 → Task 7; §3.1–3.4 → Tasks 8–9; §3.5 → Tasks 10–11; §3.6 → Task 9; §3.7 → Tasks 8–10 (semantics), 14 (checks); §4 → Tasks 5, 6, 13; §5 → Task 12; §6 → every task's string step; §7 → Task 14.
- Placeholders: none of "TBD"/"later" remain except the explicitly optional iOS tap (Task 12 step 3) and the content cross-check decision rule (Task 5 step 3), both of which say exactly what to do.
- Type consistency: `AdhkarSet`, `AdhkarPhase`, `AdhkarWindow`, `AdhkarProgress`, `AdhkarStore`, `AdhkarCatalogue.load/loadText`, `pagesFor`, `requestPlayAyahs`, `stopAdhkar`, `adhkarPlaying`, `ClockFormat`, `twelveHourParts`, `TwelveHourClock.render/markerFirst`, `createPlatformFormat(clockFormat)`, `WidgetMirrorRefresher.refresh(format = null)` are used with the same names throughout.
