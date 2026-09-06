# Taqwa Slice 1, Plan 2 of 2 — Notifications, Qibla, Localisation & Widgets

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Taqwa wakes you for every prayer with the sound you chose, points you at Makkah using true north, speaks Arabic with the layout mirrored, and puts the next prayer on your home screen — all offline, on both platforms.

**Architecture:** Everything decidable is decided in `commonMain` and unit-tested without a device. The notification *plan* is a pure function of location, settings and a clock; the platform layer only hands that plan to `AlarmManager` or `UNUserNotificationCenter`. The qibla *bearing* and the *accuracy rules* are pure; only the sensor stream is `actual`. Notification copy is baked into the plan at schedule time, so no platform code ever localises anything.

**Tech Stack:** As Plan 1 — Kotlin 2.2.20, Compose Multiplatform 1.12.0, adhan2 0.0.7, kotlinx-datetime 0.7.1, kotlinx-coroutines, DataStore Preferences 1.1.7 — plus AndroidX Glance 1.1.1 (Android widgets only) and Apple's UserNotifications, CoreLocation, CoreMotion and WidgetKit frameworks reached through Kotlin/Native interop and one Swift target.

**Depends on Plan 1** (`docs/superpowers/plans/2026-09-06-taqwa-slice1-core.md`), Tasks 1–14. This plan consumes those interfaces by name and does not re-plan any of them.

**Closes Plan 1's three deliberate stubs:** the onboarding notifications button (Task 21), the inert Settings → Notifications row (Task 22), and the missing widget background row on Appearance (Task 28).

## Global Constraints

Plan 1's Global Constraints apply unchanged and are not repeated. These are additional, and are copied verbatim from the spec.

- **Sound default is Takbir for all five prayers.** *Remind me before* defaults to **Never**. Widget background defaults to **Follow theme**.
- **A notification channel's sound is immutable after creation.** Changing a prayer's sound creates a *new* channel; an existing channel is never edited. Channel identity encodes the sound.
- **iOS permits at most 64 pending local notifications.** At five prayers a day that is a 12-day rolling window, topped up on foreground and via `BGAppRefreshTask`.
- **Both compasses must resolve to true north.** Android corrects magnetic azimuth with `GeomagneticField.declination`; iOS uses `CLHeading.trueHeading`. Magnetic north is the classic qibla bug and is wrong by up to 15°.
- **Low accuracy engages** on Android `SENSOR_STATUS_ACCURACY_LOW` or `SENSOR_STATUS_UNRELIABLE`, or on iOS `headingAccuracy` above 20° or negative. In that state the dial dims to 28% and points nowhere.
- **Aligned is within 5°**, and produces one haptic tick on entry — one, not one per frame.
- **Kaaba is 21.4225° N, 39.8262° E.** Bearing comes from adhan2's `Qibla`; distance is our own haversine.
- **In Arabic locale the Arabic prayer name is shown alone**, never beside a transliteration. In every other locale it is the localised name plus Arabic. This governs the timeline, the widgets and the notification text.
- **Numerals follow platform CLDR formatting and are never hard-coded.** `ar-LY`, `ar-MA`, `ar-TN`, `ar-DZ` get Western digits; `ar-EG` and `ar-SA` get Arabic-Indic. No setting sits between them.
- **Audio durations are the measured ones**, not the spec's original estimate: takbir **15.80 s**, adhan **29.95 s** (`assets/audio/README.md`). Both are under the 30-second platform cap.
- **The widget's fourth background option is labelled per platform:** *Translucent — blends into your wallpaper* on Android, *Frosted — uses the system widget material* on iOS.
- **No network calls.** Still true. Nothing here contacts a server.

---

## File Structure

Files this plan adds or changes. Everything else is Plan 1's and stays put.

```
shared/src/commonMain/kotlin/world/taqwa/app/
  domain/NotificationSettings.kt              PrayerSound, NotificationSettings
  domain/WidgetSettings.kt                    WidgetBackground

  notifications/ScheduledNotification.kt      the plan's unit of work
  notifications/NotificationCopy.kt           copy baked in at schedule time
  notifications/NotificationPlanner.kt        rolling window, cap, DST — pure
  notifications/RescheduleDecider.kt          triggers and top-up horizon — pure
  notifications/NotificationChannels.kt       Android channel identity — pure
  notifications/NotificationScheduler.kt      interface + expect factory
  notifications/NotificationCoordinator.kt    ties settings + engine + planner + scheduler
  notifications/NotificationOnboarding.kt     the onboarding button's logic, testable
  notifications/SoundAssets.kt                sound -> bundled file name, durations

  audio/SoundPreviewPlayer.kt                 expect: audition in the sound sheet

  qibla/QiblaMath.kt                          bearing, haversine, angle delta, alignment
  qibla/HeadingFilter.kt                      circular low-pass — pure
  qibla/CompassSource.kt                      expect: heading stream + accuracy
  qibla/CompassAccuracyRules.kt               platform thresholds — pure
  qibla/TrueNorth.kt                          magnetic -> true correction — pure
  qibla/Haptics.kt                            expect: one tick on alignment

  i18n/PrayerNaming.kt                        the Arabic-alone rule — pure
  i18n/LayoutDirection.kt                     isRtl — pure
  i18n/PlatformFormat.kt                      expect: CLDR clock, numbers, language tag
  i18n/CountdownFormatter.kt                  duration formatting + the digit fallback

  widget/WidgetContent.kt                     shared small/medium models — pure
  widget/WidgetInputsMirror.kt                cross-process settings snapshot — pure
  widget/KeyValueStore.kt                     expect: SharedPreferences / App Group defaults
  widget/WidgetPalette.kt                     background -> colours and alpha — pure

  feature/settings/NotificationSettingsScreen.kt
  feature/settings/SoundSheet.kt
  feature/settings/WidgetPreview.kt
  feature/qibla/QiblaViewModel.kt
  feature/qibla/QiblaScreen.kt
  feature/qibla/QiblaDial.kt

shared/src/commonMain/composeResources/
  values/strings.xml                          every user-visible string
  values-ar/strings.xml                       Arabic

shared/src/androidMain/
  res/raw/takbir.ogg, adhan_30s.ogg           notification sounds
  kotlin/world/taqwa/app/
    notifications/NotificationScheduler.android.kt
    notifications/AndroidNotificationScheduler.kt
    notifications/PrayerAlarmReceiver.kt
    notifications/SystemEventReceiver.kt
    notifications/NotificationPermissionRequester.kt
    audio/SoundPreviewPlayer.android.kt
    qibla/CompassSource.android.kt
    qibla/Haptics.android.kt
    i18n/PlatformFormat.android.kt
    widget/KeyValueStore.android.kt

shared/src/iosMain/kotlin/world/taqwa/app/
  notifications/NotificationScheduler.ios.kt
  notifications/IosNotificationScheduler.kt
  notifications/BackgroundRefreshBridge.kt
  audio/SoundPreviewPlayer.ios.kt
  qibla/CompassSource.ios.kt
  qibla/Haptics.ios.kt
  i18n/PlatformFormat.ios.kt
  widget/KeyValueStore.ios.kt

androidApp/src/androidMain/
  AndroidManifest.xml                          permissions, receivers, widget providers
  kotlin/world/taqwa/app/TaqwaApplication.kt
  kotlin/world/taqwa/app/widget/TaqwaGlanceWidget.kt
  kotlin/world/taqwa/app/widget/TaqwaWidgetReceivers.kt
  res/drawable/ic_stat_taqwa.xml
  res/xml/widget_small_info.xml, widget_medium_info.xml

iosApp/
  iosApp/Resources/takbir.caf, adhan-30s.caf
  iosApp/Info.plist                            background modes, task identifiers
  iosApp/iOSApp.swift                          BGAppRefreshTask registration
  TaqwaWidget/TaqwaWidgetBundle.swift
  TaqwaWidget/TaqwaWidgetViews.swift
  TaqwaWidget/Info.plist
```

---

### Task 15: Notification settings — per-prayer sounds, master toggle, reminder lead

Nothing can be scheduled until we know what the user asked for. This task also performs one
small, deliberate move: Plan 1 parked `remindBeforeMinutes` on `PrayerSettings`, where nothing
reads it. It belongs with the notification settings, so it moves — keeping the same DataStore
key, so no stored value migrates.

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/domain/NotificationSettings.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/domain/PrayerSettings.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsRepository.kt`
- Modify: `shared/src/commonTest/kotlin/world/taqwa/app/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `Prayer`, `ObligatoryPrayers`, `PrayerSettings` from Plan 1 Task 4; `SettingsRepository` from Plan 1 Task 4
- Produces:
  - `enum class PrayerSound { SILENT, NOTIFICATION, TAKBIR, ADHAN }` in `world.taqwa.app.domain`
  - `data class NotificationSettings(val enabled: Boolean = true, val sounds: Map<Prayer, PrayerSound> = ObligatoryPrayers.associateWith { PrayerSound.TAKBIR }, val remindBeforeMinutes: Int = 0)` with `fun soundFor(prayer: Prayer): PrayerSound` and `companion object { val LeadOptions: List<Int> }`
  - `SettingsRepository.notificationSettings: Flow<NotificationSettings>`
  - `suspend fun SettingsRepository.setNotificationSettings(settings: NotificationSettings)`
  - **Breaking:** `PrayerSettings.remindBeforeMinutes` no longer exists

- [ ] **Step 1: Write the failing test**

Append to `shared/src/commonTest/kotlin/world/taqwa/app/settings/SettingsRepositoryTest.kt`, and
**delete** the existing `remindBeforeDefaultsToNever` test that reads `prayerSettings` — the same
assertion is restated below against `notificationSettings`.

```kotlin
package world.taqwa.app.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationSettingsStorageTest {

    private fun repo(name: String) = SettingsRepository(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath {
            okio.Path.Companion.toPath("build/test-notif-$name.preferences_pb")
        }
    )

    @Test
    fun everyPrayerDefaultsToTakbir() = runTest {
        val s = repo("defaults").notificationSettings.first()
        ObligatoryPrayers.forEach { assertEquals(PrayerSound.TAKBIR, s.soundFor(it), "$it") }
    }

    @Test
    fun notificationsAreOnByDefault() = runTest {
        assertTrue(repo("enabled").notificationSettings.first().enabled)
    }

    @Test
    fun remindBeforeDefaultsToNever() = runTest {
        assertEquals(0, repo("lead").notificationSettings.first().remindBeforeMinutes)
    }

    @Test
    fun soundsRoundTripPerPrayer() = runTest {
        val r = repo("roundtrip")
        val s = r.notificationSettings.first()
        r.setNotificationSettings(
            s.copy(sounds = s.sounds + mapOf(Prayer.FAJR to PrayerSound.ADHAN,
                                             Prayer.ISHA to PrayerSound.SILENT)),
        )
        val back = r.notificationSettings.first()
        assertEquals(PrayerSound.ADHAN, back.soundFor(Prayer.FAJR))
        assertEquals(PrayerSound.SILENT, back.soundFor(Prayer.ISHA))
        assertEquals(PrayerSound.TAKBIR, back.soundFor(Prayer.ASR))
    }

    @Test
    fun writingPrayerSettingsDoesNotResetTheChosenSounds() = runTest {
        val r = repo("independent")
        val s = r.notificationSettings.first()
        r.setNotificationSettings(s.copy(sounds = s.sounds + (Prayer.FAJR to PrayerSound.ADHAN)))
        r.setPrayerSettings(PrayerSettings(hijriOffsetDays = 1))
        assertEquals(PrayerSound.ADHAN, r.notificationSettings.first().soundFor(Prayer.FAJR))
    }

    @Test
    fun anUnknownStoredSoundFallsBackToTakbirRatherThanCrashing() = runTest {
        val r = repo("corrupt-sound")
        r.writeRawSoundForTest(Prayer.MAGHRIB, "TRUMPET")
        assertEquals(PrayerSound.TAKBIR, r.notificationSettings.first().soundFor(Prayer.MAGHRIB))
    }

    @Test
    fun theLeadOptionsStartAtNever() {
        assertEquals(0, world.taqwa.app.domain.NotificationSettings.LeadOptions.first())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*NotificationSettingsStorageTest*"`
Expected: FAIL — `Unresolved reference: PrayerSound`

- [ ] **Step 3: Write the domain type**

`shared/src/commonMain/kotlin/world/taqwa/app/domain/NotificationSettings.kt`:

```kotlin
package world.taqwa.app.domain

/** The four levels of the spec's sound sheet, in the order the sheet shows them. */
enum class PrayerSound { SILENT, NOTIFICATION, TAKBIR, ADHAN }

data class NotificationSettings(
    val enabled: Boolean = true,
    val sounds: Map<Prayer, PrayerSound> = ObligatoryPrayers.associateWith { PrayerSound.TAKBIR },
    val remindBeforeMinutes: Int = 0,
) {
    /** Takbir is the default for every prayer, including one the map has never held. */
    fun soundFor(prayer: Prayer): PrayerSound = sounds[prayer] ?: PrayerSound.TAKBIR

    companion object {
        /** "Remind me before" choices, in minutes. 0 is Never, and is the default. */
        val LeadOptions = listOf(0, 5, 10, 15, 30)
    }
}
```

- [ ] **Step 4: Move `remindBeforeMinutes` off `PrayerSettings`**

In `domain/PrayerSettings.kt`, delete this line from the `PrayerSettings` constructor:

```kotlin
    val remindBeforeMinutes: Int = 0,
```

- [ ] **Step 5: Extend the repository**

In `settings/SettingsRepository.kt`, add to the `private object Keys`:

```kotlin
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    fun soundKey(prayer: Prayer) = stringPreferencesKey("sound_${prayer.name.lowercase()}")
```

Remove `remindBeforeMinutes = p[Keys.REMIND_BEFORE] ?: 0,` from the `prayerSettings` mapping and
`it[Keys.REMIND_BEFORE] = settings.remindBeforeMinutes` from `setPrayerSettings`. `Keys.REMIND_BEFORE`
itself stays — it is now read and written by the notification settings below, under the same
preference name, so an already-stored value keeps working.

Add to the class body:

```kotlin
    val notificationSettings: Flow<NotificationSettings> = store.data.map { p ->
        NotificationSettings(
            enabled = p[Keys.NOTIFICATIONS_ENABLED] ?: true,
            sounds = ObligatoryPrayers.associateWith { prayer ->
                p[Keys.soundKey(prayer)].toEnumOr(PrayerSound.TAKBIR)
            },
            remindBeforeMinutes = p[Keys.REMIND_BEFORE] ?: 0,
        )
    }

    suspend fun setNotificationSettings(settings: NotificationSettings) {
        store.edit { e ->
            e[Keys.NOTIFICATIONS_ENABLED] = settings.enabled
            ObligatoryPrayers.forEach { prayer ->
                e[Keys.soundKey(prayer)] = settings.soundFor(prayer).name
            }
            e[Keys.REMIND_BEFORE] = settings.remindBeforeMinutes
        }
    }

    /** Test-only hook for the forward-compatibility case, matching `writeRawThemeForTest`. */
    internal suspend fun writeRawSoundForTest(prayer: Prayer, raw: String) {
        store.edit { it[Keys.soundKey(prayer)] = raw }
    }
```

Add the imports `world.taqwa.app.domain.NotificationSettings`, `world.taqwa.app.domain.ObligatoryPrayers`,
`world.taqwa.app.domain.Prayer` and `world.taqwa.app.domain.PrayerSound`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:allTests --tests "*SettingsRepositoryTest*" --tests "*NotificationSettingsStorageTest*"`
Expected: PASS — 5 tests in `SettingsRepositoryTest` (one removed) and 7 in `NotificationSettingsStorageTest`.

Then run the whole suite to catch any remaining reference to the moved field:
Run: `./gradlew :shared:allTests` — Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/domain shared/src/commonMain/kotlin/world/taqwa/app/settings shared/src/commonTest/kotlin/world/taqwa/app/settings
git commit -m "feat: per-prayer notification sounds defaulting to takbir"
```

---

### Task 16: The notification planner — rolling window, the 64 cap, DST boundaries

The whole scheduling decision is a pure function. Get it right here and the two platform layers
have nothing left to decide.

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/ScheduledNotification.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCopy.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationPlanner.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/RescheduleDecider.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationPlannerTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/RescheduleDeciderTest.kt`

**Interfaces:**
- Consumes: `PrayerTimesEngine` and `GeoLocation` from Plan 1 Task 5, `PrayerSettings`/`Prayer`/`ObligatoryPrayers` from Plan 1 Task 4, `NotificationSettings`/`PrayerSound` from Task 15
- Produces:
  - `enum class NotificationKind { PRAYER, REMINDER }`
  - `data class ScheduledNotification(val id: String, val prayer: Prayer, val kind: NotificationKind, val instant: Instant, val timeZoneId: String, val sound: PrayerSound, val title: String, val body: String)`
  - `interface NotificationCopy { fun title(prayer: Prayer, kind: NotificationKind): String; fun body(prayer: Prayer, kind: NotificationKind, clockTime: String, minutesBefore: Int): String }`
  - `object EnglishNotificationCopy : NotificationCopy`
  - `fun isoClockTime(instant: Instant, timeZoneId: String): String`
  - `object NotificationPlanner` with `const val IOS_PENDING_LIMIT = 64`, `fun windowDaysFor(capacity: Int, notifications: NotificationSettings): Int`, and `fun plan(location: GeoLocation, settings: PrayerSettings, notifications: NotificationSettings, engine: PrayerTimesEngine, from: Instant, windowDays: Int, capacity: Int, copy: NotificationCopy = EnglishNotificationCopy, formatClockTime: (Instant, String) -> String = ::isoClockTime): List<ScheduledNotification>`
  - `enum class RescheduleTrigger { APP_FOREGROUND, BACKGROUND_REFRESH, SETTINGS_CHANGED, LOCATION_CHANGED, BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, ALARM_FIRED }`
  - `object RescheduleDecider { fun needsTopUp(plan: List<ScheduledNotification>, now: Instant, minimumHorizon: Duration): Boolean }`

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationPlannerTest.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPlannerTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val newYork = GeoLocation(40.7128, -74.0060, "America/New_York", "New York", "US")
    private val defaults = NotificationSettings()

    private fun planFor(
        location: GeoLocation = london,
        notifications: NotificationSettings = defaults,
        from: Instant = Instant.parse("2026-09-06T00:30:00Z"),
        windowDays: Int = 12,
        capacity: Int = 64,
    ) = NotificationPlanner.plan(
        location = location,
        settings = PrayerSettings(),
        notifications = notifications,
        engine = engine,
        from = from,
        windowDays = windowDays,
        capacity = capacity,
    )

    @Test
    fun sixtyFourSlotsAtFiveAPrayerDayIsATwelveDayWindow() {
        assertEquals(12, NotificationPlanner.windowDaysFor(64, defaults))
    }

    @Test
    fun turningOnRemindersHalvesTheWindowBecauseEachPrayerCostsTwoSlots() {
        assertEquals(6, NotificationPlanner.windowDaysFor(64, defaults.copy(remindBeforeMinutes = 10)))
    }

    @Test
    fun aTwelveDayWindowProducesSixtyPrayerNotifications() {
        assertEquals(60, planFor().size)
    }

    @Test
    fun theCapIsNeverExceededHoweverLongTheWindow() {
        val plan = planFor(windowDays = 30)
        assertEquals(64, plan.size)
    }

    @Test
    fun thePlanIsSortedSoTheCapKeepsTheSoonest() {
        val plan = planFor(windowDays = 30)
        assertEquals(plan.map { it.instant }.sorted(), plan.map { it.instant })
    }

    @Test
    fun nothingIsScheduledInThePast() {
        val from = Instant.parse("2026-09-06T18:00:00Z")
        assertTrue(planFor(from = from).all { it.instant > from })
    }

    @Test
    fun sunriseIsNeverNotified() {
        assertTrue(planFor().none { it.prayer == Prayer.SUNRISE })
    }

    @Test
    fun disablingNotificationsProducesAnEmptyPlan() {
        assertTrue(planFor(notifications = defaults.copy(enabled = false)).isEmpty())
    }

    @Test
    fun eachEntryCarriesThatPrayersOwnSound() {
        val plan = planFor(
            notifications = defaults.copy(
                sounds = defaults.sounds + mapOf(
                    Prayer.FAJR to PrayerSound.ADHAN,
                    Prayer.ISHA to PrayerSound.SILENT,
                ),
            ),
        )
        assertTrue(plan.filter { it.prayer == Prayer.FAJR }.all { it.sound == PrayerSound.ADHAN })
        assertTrue(plan.filter { it.prayer == Prayer.ISHA }.all { it.sound == PrayerSound.SILENT })
        assertTrue(plan.filter { it.prayer == Prayer.ASR }.all { it.sound == PrayerSound.TAKBIR })
    }

    @Test
    fun remindersLandBeforeTheirPrayerAndUseTheDefaultToneNotTheAdhan() {
        val plan = planFor(notifications = defaults.copy(remindBeforeMinutes = 10), windowDays = 1)
        val reminders = plan.filter { it.kind == NotificationKind.REMINDER }
        assertTrue(reminders.isNotEmpty())
        assertTrue(reminders.all { it.sound == PrayerSound.NOTIFICATION })
        val fajr = plan.first { it.prayer == Prayer.FAJR && it.kind == NotificationKind.PRAYER }
        val fajrReminder = plan.first { it.prayer == Prayer.FAJR && it.kind == NotificationKind.REMINDER }
        assertEquals(600L, fajr.instant.epochSeconds - fajrReminder.instant.epochSeconds)
    }

    @Test
    fun neverMeansNoRemindersAtAll() {
        assertTrue(planFor().none { it.kind == NotificationKind.REMINDER })
    }

    @Test
    fun springForwardStillYieldsFiveDistinctPrayersOnTheShortDay() {
        // 2027-03-14 is the US spring-forward date: 02:00 becomes 03:00.
        val plan = NotificationPlanner.plan(
            location = newYork, settings = PrayerSettings(), notifications = defaults,
            engine = engine, from = Instant.parse("2027-03-14T05:00:00Z"),
            windowDays = 1, capacity = 64,
        )
        val zone = TimeZone.of("America/New_York")
        val onTheDay = plan.filter { it.instant.toLocalDateTime(zone).date.dayOfMonth == 14 }
        assertEquals(5, onTheDay.size)
        assertEquals(5, onTheDay.map { it.instant }.toSet().size)
    }

    @Test
    fun fallBackStillYieldsFiveDistinctPrayersOnTheLongDay() {
        // 2026-11-01 is the US fall-back date: 02:00 happens twice.
        val plan = NotificationPlanner.plan(
            location = newYork, settings = PrayerSettings(), notifications = defaults,
            engine = engine, from = Instant.parse("2026-11-01T04:00:00Z"),
            windowDays = 1, capacity = 64,
        )
        val zone = TimeZone.of("America/New_York")
        val onTheDay = plan.filter { it.instant.toLocalDateTime(zone).date.dayOfMonth == 1 }
        assertEquals(5, onTheDay.size)
        assertEquals(5, onTheDay.map { it.instant }.toSet().size)
    }

    @Test
    fun idsAreUniqueWithinAPlanSoNoEntryOverwritesAnother() {
        val plan = planFor(notifications = defaults.copy(remindBeforeMinutes = 15), windowDays = 6)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test
    fun idsAreStableAcrossTwoIdenticalPlansSoReschedulingIsIdempotent() {
        assertEquals(planFor().map { it.id }, planFor().map { it.id })
    }

    @Test
    fun everyEntryCarriesTheLocationsTimeZoneForTheCalendarTrigger() {
        assertTrue(planFor().all { it.timeZoneId == "Europe/London" })
    }

    @Test
    fun copyNamesThePrayerAndItsClockTime() {
        val fajr = planFor().first { it.prayer == Prayer.FAJR }
        assertEquals("Fajr", fajr.title)
        assertTrue(fajr.body.contains(":"), "body should carry a clock time, was '${fajr.body}'")
    }

    @Test
    fun aSilentPrayerIsStillScheduledBecauseTheBannerStillShows() {
        val plan = planFor(notifications = defaults.copy(
            sounds = defaults.sounds + (Prayer.ISHA to PrayerSound.SILENT),
        ))
        assertFalse(plan.none { it.prayer == Prayer.ISHA })
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*NotificationPlannerTest*"`
Expected: FAIL — `Unresolved reference: NotificationPlanner`

- [ ] **Step 3: Write the plan's unit of work**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/ScheduledNotification.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.Instant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

enum class NotificationKind { PRAYER, REMINDER }

/**
 * One notification, fully decided. Title and body are baked in at schedule time so that no
 * platform code — a broadcast receiver, a background task — ever has to resolve a string
 * resource or know what language the user reads.
 */
data class ScheduledNotification(
    val id: String,
    val prayer: Prayer,
    val kind: NotificationKind,
    val instant: Instant,
    val timeZoneId: String,
    val sound: PrayerSound,
    val title: String,
    val body: String,
)
```

- [ ] **Step 4: Write the copy provider**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCopy.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer

/**
 * Supplies notification text. Task 26 replaces [EnglishNotificationCopy] with a localised
 * implementation; the planner never knows which it has.
 */
interface NotificationCopy {
    fun title(prayer: Prayer, kind: NotificationKind): String
    fun body(prayer: Prayer, kind: NotificationKind, clockTime: String, minutesBefore: Int): String
}

object EnglishNotificationCopy : NotificationCopy {

    private fun name(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.SUNRISE -> "Sunrise"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
    }

    override fun title(prayer: Prayer, kind: NotificationKind): String = name(prayer)

    override fun body(
        prayer: Prayer,
        kind: NotificationKind,
        clockTime: String,
        minutesBefore: Int,
    ): String = when (kind) {
        NotificationKind.PRAYER -> "It is time for ${name(prayer)} · $clockTime"
        NotificationKind.REMINDER -> "${name(prayer)} in $minutesBefore minutes · $clockTime"
    }
}

/** A 24-hour HH:MM in the location's own zone. Task 27 replaces this with CLDR formatting. */
fun isoClockTime(instant: Instant, timeZoneId: String): String {
    val t = instant.toLocalDateTime(TimeZone.of(timeZoneId))
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
```

- [ ] **Step 5: Write the planner**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationPlanner.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.time.Duration.Companion.minutes

object NotificationPlanner {

    /** iOS will hold no more than this many pending local notifications. It is a platform limit. */
    const val IOS_PENDING_LIMIT = 64

    /**
     * How many whole days of prayers fit in [capacity]. Five obligatory prayers a day, doubled
     * when a reminder precedes each one — so 64 slots is twelve days, or six with reminders on.
     */
    fun windowDaysFor(capacity: Int, notifications: NotificationSettings): Int {
        val perDay = ObligatoryPrayers.size * if (notifications.remindBeforeMinutes > 0) 2 else 1
        return (capacity / perDay).coerceAtLeast(1)
    }

    fun plan(
        location: GeoLocation,
        settings: PrayerSettings,
        notifications: NotificationSettings,
        engine: PrayerTimesEngine,
        from: Instant,
        windowDays: Int,
        capacity: Int,
        copy: NotificationCopy = EnglishNotificationCopy,
        formatClockTime: (Instant, String) -> String = ::isoClockTime,
    ): List<ScheduledNotification> {
        if (!notifications.enabled) return emptyList()

        val zone = TimeZone.of(location.timeZoneId)
        val firstDate = from.toLocalDateTime(zone).date
        val lead = notifications.remindBeforeMinutes
        val out = mutableListOf<ScheduledNotification>()

        for (offset in 0 until windowDays) {
            val date = firstDate.plus(offset, DateTimeUnit.DAY)
            // Recomputing per local date is what makes DST correct: the engine returns instants,
            // and a day that is 23 or 25 hours long still has exactly five prayers.
            val times = engine.timesFor(location, date, settings)

            ObligatoryPrayers.forEach { prayer ->
                val at = times.time(prayer)
                val clock = formatClockTime(at, location.timeZoneId)

                if (at > from) {
                    out += ScheduledNotification(
                        id = "${prayer.name}-PRAYER-$date",
                        prayer = prayer,
                        kind = NotificationKind.PRAYER,
                        instant = at,
                        timeZoneId = location.timeZoneId,
                        sound = notifications.soundFor(prayer),
                        title = copy.title(prayer, NotificationKind.PRAYER),
                        body = copy.body(prayer, NotificationKind.PRAYER, clock, 0),
                    )
                }

                if (lead > 0) {
                    val remindAt = at - lead.minutes
                    if (remindAt > from) {
                        out += ScheduledNotification(
                            id = "${prayer.name}-REMINDER-$date",
                            prayer = prayer,
                            kind = NotificationKind.REMINDER,
                            instant = remindAt,
                            timeZoneId = location.timeZoneId,
                            // A reminder is a nudge, not the call: it never plays the adhan.
                            sound = PrayerSound.NOTIFICATION,
                            title = copy.title(prayer, NotificationKind.REMINDER),
                            body = copy.body(prayer, NotificationKind.REMINDER, clock, lead),
                        )
                    }
                }
            }
        }

        return out.sortedBy { it.instant }.take(capacity)
    }
}
```

- [ ] **Step 6: Write the reschedule decider and its test**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/RescheduleDecider.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.Instant
import kotlin.time.Duration

/** Why the app woke up. Platform entry points name their reason; the coordinator debounces on it. */
enum class RescheduleTrigger {
    APP_FOREGROUND,
    BACKGROUND_REFRESH,
    SETTINGS_CHANGED,
    LOCATION_CHANGED,
    BOOT_COMPLETED,
    TIME_SET,
    TIMEZONE_CHANGED,
    ALARM_FIRED,
}

object RescheduleDecider {
    /**
     * True when the furthest scheduled notification is nearer than [minimumHorizon] — the signal
     * that iOS's rolling window has drained and a `BGAppRefreshTask` is worth requesting.
     */
    fun needsTopUp(
        plan: List<ScheduledNotification>,
        now: Instant,
        minimumHorizon: Duration,
    ): Boolean {
        val furthest = plan.maxOfOrNull { it.instant } ?: return true
        return furthest - now < minimumHorizon
    }
}
```

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/RescheduleDeciderTest.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.datetime.Instant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class RescheduleDeciderTest {

    private val now = Instant.parse("2026-09-06T09:00:00Z")

    private fun entryAt(instant: Instant) = ScheduledNotification(
        id = "x", prayer = Prayer.FAJR, kind = NotificationKind.PRAYER, instant = instant,
        timeZoneId = "Europe/London", sound = PrayerSound.TAKBIR, title = "Fajr", body = "…",
    )

    @Test
    fun anEmptyPlanAlwaysNeedsTopUp() {
        assertTrue(RescheduleDecider.needsTopUp(emptyList(), now, 3.days))
    }

    @Test
    fun aDrainingWindowNeedsTopUp() {
        val plan = listOf(entryAt(now + 2.days))
        assertTrue(RescheduleDecider.needsTopUp(plan, now, 3.days))
    }

    @Test
    fun aFullWindowDoesNot() {
        val plan = listOf(entryAt(now + 2.days), entryAt(now + 11.days))
        assertFalse(RescheduleDecider.needsTopUp(plan, now, 3.days))
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :shared:allTests --tests "*NotificationPlannerTest*" --tests "*RescheduleDeciderTest*"`
Expected: PASS — 18 tests in the planner, 3 in the decider.

If `springForwardStillYieldsFiveDistinctPrayersOnTheShortDay` fails with four entries, the
planner is iterating instants rather than local dates; re-read Step 5.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/notifications shared/src/commonTest/kotlin/world/taqwa/app/notifications
git commit -m "feat: pure notification planner with rolling window, 64 cap and DST handling"
```

---
