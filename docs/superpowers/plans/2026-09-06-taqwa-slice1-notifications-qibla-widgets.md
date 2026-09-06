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

### Task 17: Notification scheduling — Android `actual`

The plan is decided; now something has to hand it to `AlarmManager`. Channel identity is the one
piece of Android-specific logic worth keeping pure and testable, because it is also the piece
easiest to get wrong: a notification channel's sound cannot be changed after creation, so
changing a prayer's sound must produce a channel Android has never seen before.

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationChannels.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/SoundAssets.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCoordinator.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationOnboarding.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationChannelsTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/SoundAssetsTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationCoordinatorTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationOnboardingTest.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.android.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/AndroidNotificationScheduler.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/PrayerAlarmReceiver.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/SystemEventReceiver.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationPermissionRequester.kt`
- Create: `androidApp/src/androidMain/kotlin/world/taqwa/app/TaqwaApplication.kt`
- Create: `androidApp/src/androidMain/res/drawable/ic_stat_taqwa.xml`
- Modify: `androidApp/src/androidMain/AndroidManifest.xml`
- Modify: `androidApp/src/androidMain/kotlin/world/taqwa/app/MainActivity.kt`

**Interfaces:**
- Consumes: `ScheduledNotification`, `NotificationKind`, `RescheduleTrigger`, `NotificationPlanner`, `RescheduleDecider` from Task 16; `NotificationSettings`, `PrayerSound` from Task 15; `Prayer`, `ObligatoryPrayers`, `PrayerSettings`, `GeoLocation` from Plan 1 Task 4/5; `PrayerTimesEngine` from Plan 1 Task 5; `SettingsRepository` (including `location` and `notificationSettings`) from Plan 1 Task 4/10 and Task 15; `appContext` from Plan 1 Task 4
- Produces:
  - `object NotificationChannels { fun channelId(prayer: Prayer, sound: PrayerSound): String; fun allChannelIdsFor(prayer: Prayer): List<String> }`
  - `object SoundAssets { fun androidRawResourceName(sound: PrayerSound): String?; fun iosResourceFileName(sound: PrayerSound): String?; fun duration(sound: PrayerSound): Duration? }`
  - `interface NotificationScheduler { fun scheduleAll(plan: List<ScheduledNotification>); fun cancelAll() }` and `expect fun createNotificationScheduler(): NotificationScheduler`
  - `class NotificationCoordinator(engine, settingsRepository, locationOf, scheduler, now, capacity = NotificationPlanner.IOS_PENDING_LIMIT)` with `suspend fun reschedule(trigger: RescheduleTrigger): List<ScheduledNotification>` and `fun needsTopUp(plan: List<ScheduledNotification>): Boolean`
  - `class NotificationOnboarding(requestSystemPermission, setNotificationsEnabled, rescheduleIfEnabled)` with `suspend fun enable(): Boolean` and `suspend fun declineForNow()`
  - Android: `class AndroidNotificationScheduler(context: Context) : NotificationScheduler`; `class PrayerAlarmReceiver : BroadcastReceiver`; `class SystemEventReceiver : BroadcastReceiver`; `object NotificationPermissionRequester { fun isGranted(): Boolean }`; `var notificationSmallIconResId: Int` (set once from `androidApp`, since `shared` cannot see androidApp's generated `R` class)

- [ ] **Step 1: Write the failing tests for the two pure pieces**

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationChannelsTest.kt`:

```kotlin
package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NotificationChannelsTest {

    @Test
    fun changingTheSoundProducesADifferentChannelId() {
        assertNotEquals(
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.TAKBIR),
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.ADHAN),
        )
    }

    @Test
    fun theSamePrayerAndSoundAlwaysProduceTheSameId() {
        assertEquals(
            NotificationChannels.channelId(Prayer.ISHA, PrayerSound.NOTIFICATION),
            NotificationChannels.channelId(Prayer.ISHA, PrayerSound.NOTIFICATION),
        )
    }

    @Test
    fun differentPrayersWithTheSameSoundStillGetDifferentChannels() {
        assertNotEquals(
            NotificationChannels.channelId(Prayer.FAJR, PrayerSound.TAKBIR),
            NotificationChannels.channelId(Prayer.DHUHR, PrayerSound.TAKBIR),
        )
    }

    @Test
    fun allChannelIdsForCoversEverySoundExactlyOnce() {
        val ids = NotificationChannels.allChannelIdsFor(Prayer.MAGHRIB)
        assertEquals(PrayerSound.entries.size, ids.toSet().size)
        assertEquals(
            NotificationChannels.channelId(Prayer.MAGHRIB, PrayerSound.TAKBIR),
            ids.first { it == NotificationChannels.channelId(Prayer.MAGHRIB, PrayerSound.TAKBIR) },
        )
    }
}
```

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/SoundAssetsTest.kt`:

```kotlin
package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SoundAssetsTest {

    @Test
    fun silentAndNotificationHaveNoBundledFileOrDuration() {
        listOf(PrayerSound.SILENT, PrayerSound.NOTIFICATION).forEach {
            assertNull(SoundAssets.androidRawResourceName(it), "$it")
            assertNull(SoundAssets.iosResourceFileName(it), "$it")
            assertNull(SoundAssets.duration(it), "$it")
        }
    }

    @Test
    fun takbirResolvesToTheMeasuredFifteenPointEightSeconds() {
        assertEquals(15.80.seconds, SoundAssets.duration(PrayerSound.TAKBIR))
        assertEquals("takbir", SoundAssets.androidRawResourceName(PrayerSound.TAKBIR))
        assertEquals("takbir.caf", SoundAssets.iosResourceFileName(PrayerSound.TAKBIR))
    }

    @Test
    fun adhanResolvesToTheMeasuredTwentyNinePointNineFiveSeconds() {
        assertEquals(29.95.seconds, SoundAssets.duration(PrayerSound.ADHAN))
        assertEquals("adhan_30s", SoundAssets.androidRawResourceName(PrayerSound.ADHAN))
        assertEquals("adhan-30s.caf", SoundAssets.iosResourceFileName(PrayerSound.ADHAN))
    }

    @Test
    fun everyBundledDurationIsUnderThePlatformThirtySecondCap() {
        PrayerSound.entries.mapNotNull { SoundAssets.duration(it) }.forEach {
            assertTrue(it.inWholeMilliseconds <= 30_000, "$it exceeds the 30s cap")
        }
    }

    @Test
    fun androidResourceNamesContainNoHyphenBecauseResourceNamesForbidThem() {
        PrayerSound.entries.mapNotNull { SoundAssets.androidRawResourceName(it) }.forEach {
            assertTrue(!it.contains("-"), "'$it' would not compile as an Android resource name")
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*NotificationChannelsTest*" --tests "*SoundAssetsTest*"`
Expected: FAIL — `Unresolved reference: NotificationChannels`

- [ ] **Step 3: Implement the two pure objects**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationChannels.kt`:

```kotlin
package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

/**
 * Android channel identity. A channel's sound is immutable after creation, so changing a
 * prayer's sound must create a channel Android has never seen before rather than editing the
 * old one — channel id therefore encodes the sound, not just the prayer.
 */
object NotificationChannels {
    fun channelId(prayer: Prayer, sound: PrayerSound): String =
        "prayer_${prayer.name.lowercase()}_${sound.name.lowercase()}"

    /** Every channel this prayer could ever have had, across all four sounds — the set the
     * Android scheduler checks when deciding which stale channels it may delete. */
    fun allChannelIdsFor(prayer: Prayer): List<String> =
        PrayerSound.entries.map { channelId(prayer, it) }
}
```

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/SoundAssets.kt`:

```kotlin
package world.taqwa.app.notifications

import world.taqwa.app.domain.PrayerSound
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bundled sound file names and their measured durations (`assets/audio/README.md`), shared by
 * both schedulers, the Android channel setup and the sound sheet's play button. Silent and
 * Notification resolve to nothing here: Silent plays no sound at all, and Notification uses
 * whatever system default tone the platform already provides.
 */
object SoundAssets {

    /** Android `res/raw/` resource name, no extension — Android resource names forbid hyphens. */
    fun androidRawResourceName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> "takbir"
        PrayerSound.ADHAN -> "adhan_30s"
    }

    /** iOS bundle resource file name, with extension. */
    fun iosResourceFileName(sound: PrayerSound): String? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> "takbir.caf"
        PrayerSound.ADHAN -> "adhan-30s.caf"
    }

    fun duration(sound: PrayerSound): Duration? = when (sound) {
        PrayerSound.SILENT, PrayerSound.NOTIFICATION -> null
        PrayerSound.TAKBIR -> 15.80.seconds
        PrayerSound.ADHAN -> 29.95.seconds
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:allTests --tests "*NotificationChannelsTest*" --tests "*SoundAssetsTest*"`
Expected: PASS — 4 tests in `NotificationChannelsTest`, 5 in `SoundAssetsTest`.

- [ ] **Step 5: Write the failing tests for the coordinator and the onboarding logic**

These tests reference a `NotificationScheduler` interface and a `FakeScheduler` that implements
it, so they will not compile until Step 7. That is expected — the interface is being designed
by its first caller.

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationCoordinatorTest.kt`:

```kotlin
package world.taqwa.app.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeScheduler : NotificationScheduler {
    val scheduledCalls = mutableListOf<List<ScheduledNotification>>()
    var cancelCalls = 0
    override fun scheduleAll(plan: List<ScheduledNotification>) { scheduledCalls += plan }
    override fun cancelAll() { cancelCalls++ }
}

class NotificationCoordinatorTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val now = Instant.parse("2026-09-06T00:30:00Z")

    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "build/test-coordinator-$name.preferences_pb".toPath() }
    )

    @Test
    fun withNoLocationEverythingIsCancelledAndNothingIsScheduled() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("no-loc"),
            locationOf = { null }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(plan.isEmpty())
        assertEquals(1, scheduler.cancelCalls)
        assertTrue(scheduler.scheduledCalls.isEmpty())
    }

    @Test
    fun withALocationTheFullWindowIsHandedToTheScheduler() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("with-loc"),
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertEquals(60, plan.size) // 12-day window at 5/day, default settings
        assertEquals(1, scheduler.scheduledCalls.size)
        assertEquals(plan, scheduler.scheduledCalls.single())
    }

    @Test
    fun disablingNotificationsSchedulesAnEmptyPlanRatherThanLeavingStaleAlarms() = runTest {
        val scheduler = FakeScheduler()
        val r = repo("disabled")
        r.setNotificationSettings(r.notificationSettings.first().copy(enabled = false))
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = r,
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        assertTrue(plan.isEmpty())
        assertEquals(listOf(emptyList<ScheduledNotification>()), scheduler.scheduledCalls)
    }

    @Test
    fun needsTopUpDelegatesToTheRescheduleDeciderWithAThreeDayHorizon() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("topup"),
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        assertTrue(coordinator.needsTopUp(emptyList()))
        val fullWindow = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(!coordinator.needsTopUp(fullWindow))
    }
}
```

`shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationOnboardingTest.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationOnboardingTest {

    @Test
    fun grantingPermissionEnablesNotificationsAndTriggersTheFirstSchedule() = runTest {
        var enabledValue: Boolean? = null
        var rescheduled = false
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { true },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { rescheduled = true },
        )
        assertTrue(onboarding.enable())
        assertEquals(true, enabledValue)
        assertTrue(rescheduled)
    }

    @Test
    fun aDeniedPermissionDisablesNotificationsAndNeverSchedules() = runTest {
        var enabledValue: Boolean? = null
        var rescheduled = false
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { false },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { rescheduled = true },
        )
        assertFalse(onboarding.enable())
        assertEquals(false, enabledValue)
        assertFalse(rescheduled)
    }

    @Test
    fun decliningForNowDisablesNotificationsWithoutAskingTheSystemAtAll() = runTest {
        var permissionAsked = false
        var enabledValue: Boolean? = null
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { permissionAsked = true; true },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { },
        )
        onboarding.declineForNow()
        assertFalse(permissionAsked)
        assertEquals(false, enabledValue)
    }
}
```

- [ ] **Step 6: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*NotificationCoordinatorTest*" --tests "*NotificationOnboardingTest*"`
Expected: FAIL — `Unresolved reference: NotificationScheduler`

- [ ] **Step 7: Write the interface, the coordinator and the onboarding logic**

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.kt`:

```kotlin
package world.taqwa.app.notifications

/**
 * The only door from pure planning into a real OS. `scheduleAll` fully replaces whatever is
 * currently pending — the coordinator never diffs an old plan against a new one, and neither
 * does the scheduler; a full replace is simpler than a diff and iOS has no diff primitive
 * anyway.
 */
interface NotificationScheduler {
    fun scheduleAll(plan: List<ScheduledNotification>)
    fun cancelAll()
}

expect fun createNotificationScheduler(): NotificationScheduler
```

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCoordinator.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Duration.Companion.days

/**
 * Ties settings, the prayer engine, the pure planner and a platform [NotificationScheduler]
 * together. Every platform entry point — app foreground, a boot receiver, a background task —
 * calls [reschedule] and never touches [NotificationPlanner] or the scheduler directly.
 */
class NotificationCoordinator(
    private val engine: PrayerTimesEngine,
    private val settingsRepository: SettingsRepository,
    private val locationOf: suspend () -> GeoLocation?,
    private val scheduler: NotificationScheduler,
    private val now: () -> Instant,
    private val capacity: Int = NotificationPlanner.IOS_PENDING_LIMIT,
) {
    companion object {
        /** Below this much runway left in the window, a background task is worth requesting. */
        val TOP_UP_HORIZON = 3.days
    }

    suspend fun reschedule(trigger: RescheduleTrigger): List<ScheduledNotification> {
        val location = locationOf()
        if (location == null) {
            // Nothing to schedule against, and nothing stale should be left behind either —
            // this is what happens when a user revokes location after granting it once.
            scheduler.cancelAll()
            return emptyList()
        }
        val prayerSettings = settingsRepository.prayerSettings.first()
        val notificationSettings = settingsRepository.notificationSettings.first()
        val windowDays = NotificationPlanner.windowDaysFor(capacity, notificationSettings)
        val plan = NotificationPlanner.plan(
            location = location,
            settings = prayerSettings,
            notifications = notificationSettings,
            engine = engine,
            from = now(),
            windowDays = windowDays,
            capacity = capacity,
        )
        // trigger is not branched on: every reason for waking up resolves to the same correct
        // plan for right now. It exists so callers and logs can say why a reschedule happened.
        scheduler.scheduleAll(plan)
        return plan
    }

    fun needsTopUp(plan: List<ScheduledNotification>): Boolean =
        RescheduleDecider.needsTopUp(plan, now(), TOP_UP_HORIZON)
}
```

`shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationOnboarding.kt`:

```kotlin
package world.taqwa.app.notifications

/**
 * The onboarding screen's "Enable notifications" button. Plan 1 Task 13 Step 5 left it as a
 * no-op that only advanced the flow, deliberately, because a granted permission with no
 * scheduler behind it is worse than not asking. This is the real behaviour: request the OS
 * permission, persist the user's choice either way, and only schedule anything if it was
 * granted.
 */
class NotificationOnboarding(
    private val requestSystemPermission: suspend () -> Boolean,
    private val setNotificationsEnabled: suspend (Boolean) -> Unit,
    private val rescheduleIfEnabled: suspend () -> Unit,
) {
    /** "Enable notifications". Returns whether the user ends up enabled. */
    suspend fun enable(): Boolean {
        val granted = requestSystemPermission()
        setNotificationsEnabled(granted)
        if (granted) rescheduleIfEnabled()
        return granted
    }

    /** "Not now" — declining is a first-class path, not a dead end, and never touches the
     * system permission dialog at all. */
    suspend fun declineForNow() {
        setNotificationsEnabled(false)
    }
}
```

- [ ] **Step 8: Run the coordinator and onboarding tests**

The `expect fun createNotificationScheduler()` above has no `actual` yet in either platform
source set. A Kotlin Multiplatform target only needs its `actual` to exist when *that target*
is compiled, so run the Android-specific test task rather than the aggregate one — it does not
touch `iosMain` at all:

Run: `./gradlew :shared:testDebugUnitTest --tests "*NotificationCoordinatorTest*" --tests "*NotificationOnboardingTest*"`
Expected: PASS — 4 tests in `NotificationCoordinatorTest`, 3 in `NotificationOnboardingTest`.

`:shared:allTests` and `./scripts/ios-build.sh` will not succeed again until Task 18 supplies
the iOS `actual`. That gap is one task wide and closes immediately next.

- [ ] **Step 9: Implement the Android scheduler**

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.android.kt`:

```kotlin
package world.taqwa.app.notifications

import world.taqwa.app.settings.appContext

actual fun createNotificationScheduler(): NotificationScheduler = AndroidNotificationScheduler(appContext)
```

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/AndroidNotificationScheduler.kt`:

```kotlin
package world.taqwa.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

private const val PREFS_NAME = "taqwa_scheduled_alarms"
private const val KEY_IDS = "ids"
private const val ALARM_ACTION = "world.taqwa.app.PRAYER_ALARM"

const val EXTRA_ID = "id"
const val EXTRA_PRAYER = "prayer"
const val EXTRA_SOUND = "sound"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"

/**
 * `AlarmManager`-backed [NotificationScheduler]. `scheduleAll` is a full replace: everything
 * previously scheduled by this class is cancelled first, using the request-code set persisted
 * from the last call — `AlarmManager` has no "list what I've scheduled" API — and the new
 * plan's own id set is persisted in turn for next time.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun scheduleAll(plan: List<ScheduledNotification>) {
        cancelAll()
        ensureChannels(plan)
        plan.forEach(::schedule)
        prefs.edit().putStringSet(KEY_IDS, plan.map { it.id }.toSet()).apply()
    }

    override fun cancelAll() {
        val previousIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        previousIds.forEach { id -> alarmManager.cancel(pendingIntentFor(id)) }
        prefs.edit().remove(KEY_IDS).apply()
    }

    private fun schedule(entry: ScheduledNotification) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            entry.instant.toEpochMilliseconds(),
            pendingIntentFor(entry.id, entry),
        )
    }

    private fun pendingIntentFor(id: String, entry: ScheduledNotification? = null): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra(EXTRA_ID, id)
            if (entry != null) {
                putExtra(EXTRA_PRAYER, entry.prayer.name)
                putExtra(EXTRA_SOUND, entry.sound.name)
                putExtra(EXTRA_TITLE, entry.title)
                putExtra(EXTRA_BODY, entry.body)
            }
        }
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** One channel per (prayer, sound) actually used by this plan. Never edits an existing
     * channel — a sound change always shows up as a channel id Android has never seen. */
    private fun ensureChannels(plan: List<ScheduledNotification>) {
        plan.map { it.prayer to it.sound }.toSet().forEach { (prayer, sound) ->
            val id = NotificationChannels.channelId(prayer, sound)
            if (notificationManager.getNotificationChannel(id) != null) return@forEach
            val channel = NotificationChannel(id, channelName(prayer), NotificationManager.IMPORTANCE_HIGH)
            configureSound(channel, sound)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun channelName(prayer: Prayer) =
        "Prayer: ${prayer.name.lowercase().replaceFirstChar { it.uppercase() }}"

    private fun configureSound(channel: NotificationChannel, sound: PrayerSound) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        when (sound) {
            PrayerSound.SILENT -> channel.setSound(null, null)
            PrayerSound.NOTIFICATION ->
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
            PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val name = SoundAssets.androidRawResourceName(sound)!!
                channel.setSound(Uri.parse("android.resource://${context.packageName}/raw/$name"), attrs)
            }
        }
    }
}
```

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/PrayerAlarmReceiver.kt`:

```kotlin
package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

/** Set once from `TaqwaApplication` — `shared` cannot see androidApp's generated `R` class. */
var notificationSmallIconResId: Int = android.R.drawable.ic_popup_reminder

/**
 * Fires exactly once per scheduled alarm. It never re-derives content: title, body and which
 * channel to post into all travel in the intent extras baked in at schedule time, so this class
 * has no locale, no settings lookup and nothing to get wrong at 3am.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { Prayer.valueOf(it) } ?: return
        val sound = intent.getStringExtra(EXTRA_SOUND)?.let { PrayerSound.valueOf(it) } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        val notification = NotificationCompat.Builder(context, NotificationChannels.channelId(prayer, sound))
            .setSmallIcon(notificationSmallIconResId)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }
}
```

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/SystemEventReceiver.kt`:

```kotlin
package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore

/**
 * `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` all invalidate whatever is currently
 * scheduled: a reboot clears every `AlarmManager` entry outright, and a clock or timezone
 * change can silently leave the existing plan pointing at the wrong wall-clock moments.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RescheduleTrigger.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED -> RescheduleTrigger.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleTrigger.TIMEZONE_CHANGED
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settingsRepository = SettingsRepository(createDataStore())
                val coordinator = NotificationCoordinator(
                    engine = PrayerTimesEngine(),
                    settingsRepository = settingsRepository,
                    locationOf = { settingsRepository.location.first() },
                    scheduler = createNotificationScheduler(),
                    now = { Clock.System.now() },
                )
                coordinator.reschedule(trigger)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationPermissionRequester.kt`:

```kotlin
package world.taqwa.app.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import world.taqwa.app.settings.appContext

/**
 * Android 13 (API 33) introduced a runtime `POST_NOTIFICATIONS` permission; below that,
 * notifications need no separate grant. The system prompt itself is driven from Compose via
 * `rememberLauncherForActivityResult` in the onboarding screen — the same pattern Task 10 uses
 * for location — so by the time this is read the result is already reflected here.
 */
object NotificationPermissionRequester {
    fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```

- [ ] **Step 10: Move `appContext` assignment into an `Application` subclass**

`BOOT_COMPLETED` can deliver `SystemEventReceiver.onReceive` before any `Activity` has ever run,
so `appContext` must be set from `Application.onCreate`, not `MainActivity.onCreate`.

`androidApp/src/androidMain/kotlin/world/taqwa/app/TaqwaApplication.kt`:

```kotlin
package world.taqwa.app

import android.app.Application
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
    }
}
```

In `MainActivity.kt`, delete the `appContext = applicationContext` line — `TaqwaApplication` now
owns it and runs first on every launch path, including a cold boot receiver.

`androidApp/src/androidMain/res/drawable/ic_stat_taqwa.xml` — a status-bar icon must be a flat
single-colour silhouette; Android tints it at display time:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#FFFFFFFF">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,2 C12,2 7.5,7.6 7.5,11.3 C7.5,13.79 9.52,15.8 12,15.8 C14.48,15.8 16.5,13.79 16.5,11.3 C16.5,7.6 12,2 12,2 Z" />
    <path
        android:strokeColor="#FF000000" android:strokeWidth="1.6" android:strokeLineCap="round"
        android:pathData="M12,17.4 L12,21.4 M9.2,21.4 L14.8,21.4" />
</vector>
```

- [ ] **Step 11: Wire the manifest**

In `androidApp/src/androidMain/AndroidManifest.xml`, add inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Set `android:name=".TaqwaApplication"` on `<application>`, and add inside it:

```xml
<receiver android:name="world.taqwa.app.notifications.PrayerAlarmReceiver" android:exported="false" />
<receiver android:name="world.taqwa.app.notifications.SystemEventReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 12: Wire the coordinator into the app**

In `di/AppContainer.kt`, add:

```kotlin
val notificationCoordinator = NotificationCoordinator(
    engine = prayerTimesEngine,
    settingsRepository = settingsRepository,
    locationOf = { settingsRepository.location.first() },
    scheduler = createNotificationScheduler(),
    now = { kotlinx.datetime.Clock.System.now() },
)
```

In `App.kt`, extend the existing `LaunchedEffect(Unit)` (the one that checks
`onboardingComplete`) to also call, unconditionally:

```kotlin
container.notificationCoordinator.reschedule(world.taqwa.app.notifications.RescheduleTrigger.APP_FOREGROUND)
```

This fires once per process launch. True resume-from-background detection needs a lifecycle
observer this plan does not add; it is not load-bearing for correctness because the boot,
clock-change and timezone-change receivers, plus Task 18's `BGAppRefreshTask`, already cover
the multi-day gaps a single foreground hook would miss.

- [ ] **Step 13: Build and manually verify on Android**

Run: `./gradlew :androidApp:assembleDebug` — Expected: BUILD SUCCESSFUL.

Install the debug build, complete onboarding with a real location, grant the notification
permission, and open **Settings → Notifications** — it is still the Plan 1 placeholder row until
Task 19, so verification here is at the system level:

```bash
adb shell dumpsys notification | grep -A3 "prayer_"
adb shell am broadcast -a android.intent.action.TIME_SET
```

Expected: at least one `prayer_<name>_<sound>` channel exists per prayer whose sound is not
Silent or Notification, and the `TIME_SET` broadcast does not crash the app (check `adb logcat`
for `SystemEventReceiver`).

- [ ] **Step 14: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/notifications shared/src/commonMain/kotlin/world/taqwa/app/di shared/src/commonMain/kotlin/world/taqwa/app/App.kt shared/src/commonTest/kotlin/world/taqwa/app/notifications shared/src/androidMain/kotlin/world/taqwa/app/notifications androidApp/src/androidMain
git commit -m "feat: Android exact-alarm scheduling with sound-encoded channel identity"
```

---

### Task 18: Notification scheduling — iOS `actual`

iOS has no exact-alarm equivalent and no unlimited queue: `UNCalendarNotificationTrigger` fires
by wall-clock calendar components, and the OS accepts at most 64 pending requests. Both limits
are already respected by `NotificationPlanner`; this task only has to hand its output to
`UNUserNotificationCenter` and keep the window topped up.

**Files:**
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.ios.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/notifications/IosNotificationScheduler.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/notifications/BackgroundRefreshBridge.kt`
- Modify: `iosApp/iosApp/Info.plist`
- Modify: `iosApp/iosApp/iOSApp.swift`

**Interfaces:**
- Consumes: `NotificationScheduler`, `NotificationCoordinator`, `RescheduleTrigger` from Task 17; `ScheduledNotification`, `NotificationKind` from Task 16; `SoundAssets` from Task 17; `PrayerSound` from Task 15; `PrayerTimesEngine`, `SettingsRepository`, `createDataStore` from Plan 1
- Produces:
  - `actual fun createNotificationScheduler(): NotificationScheduler`
  - `class IosNotificationScheduler : NotificationScheduler`
  - `object BackgroundRefreshBridge { fun runBackgroundRefresh(): Boolean }` — the Kotlin side of `BGAppRefreshTask`, called from Swift

- [ ] **Step 1: Implement the scheduler**

There is nothing pure left to unit-test here — every remaining decision already lives in
`NotificationPlanner` and was tested in Task 16. This step is verified by running the app, the
same way Plan 1 verified `IosLocationProvider`.

`shared/src/iosMain/kotlin/world/taqwa/app/notifications/IosNotificationScheduler.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import world.taqwa.app.domain.PrayerSound

/**
 * `UNUserNotificationCenter`-backed [NotificationScheduler]. iOS has no partial-update API for
 * pending requests, so `scheduleAll` tears every request down and rebuilds from the new plan,
 * the same full-replace shape as the Android path. [NotificationPlanner] already enforces the
 * 64-request cap; the `take` here is a defensive re-clamp, not the real limiter.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationScheduler : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun scheduleAll(plan: List<ScheduledNotification>) {
        center.removeAllPendingNotificationRequests()
        plan.take(NotificationPlanner.IOS_PENDING_LIMIT).forEach(::schedule)
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    private fun schedule(entry: ScheduledNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(entry.title)
            setBody(entry.body)
            setSound(soundFor(entry.sound))
        }

        val timeZone = NSTimeZone.timeZoneWithName(entry.timeZoneId) ?: NSTimeZone.localTimeZone
        val calendar = NSCalendar.currentCalendar.apply { this.timeZone = timeZone }
        val components = calendar.components(
            unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = NSDate.dateWithTimeIntervalSince1970(entry.instant.epochSeconds.toDouble()),
        )

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components, repeats = false,
        )

        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(entry.id, content, trigger),
            withCompletionHandler = null,
        )
    }

    /** Silent means no sound at all; every other level either uses the bundled `.caf` or, for
     * "Notification", the system default — mirroring the Android channel setup in Task 17. */
    private fun soundFor(sound: PrayerSound): UNNotificationSound? {
        val fileName = SoundAssets.iosResourceFileName(sound)
        return when {
            sound == PrayerSound.SILENT -> null
            fileName != null -> UNNotificationSound.soundNamed(fileName)
            else -> UNNotificationSound.defaultSound
        }
    }
}
```

`shared/src/iosMain/kotlin/world/taqwa/app/notifications/NotificationScheduler.ios.kt`:

```kotlin
package world.taqwa.app.notifications

actual fun createNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()
```

- [ ] **Step 2: Confirm the full multiplatform build is green again**

Run: `./gradlew :shared:allTests` — Expected: all tests PASS, including the Task 17 tests that
could only run on the Android target task before now.

Run: `./scripts/ios-build.sh` — Expected: BUILD SUCCEEDED. This is the step that proves the
one-task gap opened in Task 17 is closed.

- [ ] **Step 3: Write the background refresh bridge**

`shared/src/iosMain/kotlin/world/taqwa/app/notifications/BackgroundRefreshBridge.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore

/**
 * The Kotlin side of `BGAppRefreshTask`. Swift owns task registration and the execution budget;
 * this only does the rescheduling work and reports whether it produced a usable result, so
 * `iOSApp.swift` can call `setTaskCompleted(success:)` honestly rather than always `true`.
 */
object BackgroundRefreshBridge {

    fun runBackgroundRefresh(): Boolean = runBlocking {
        val settingsRepository = SettingsRepository(createDataStore())
        val coordinator = NotificationCoordinator(
            engine = PrayerTimesEngine(),
            settingsRepository = settingsRepository,
            locationOf = { settingsRepository.location.first() },
            scheduler = createNotificationScheduler(),
            now = { Clock.System.now() },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH)
        // No location is not a failure — there is nothing to schedule and nothing went wrong.
        plan.isNotEmpty() || settingsRepository.location.first() == null
    }
}
```

- [ ] **Step 4: Register the background task in Swift**

In `iosApp/iosApp/Info.plist`, add:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
</array>
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>world.taqwa.app.refresh</string>
</array>
```

In `iosApp/iosApp/iOSApp.swift`:

```swift
import SwiftUI
import BackgroundTasks
import shared

private let refreshTaskId = "world.taqwa.app.refresh"

@main
struct iOSApp: App {

    init() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
            Self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
        Self.scheduleNextRefresh()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear { Self.scheduleNextRefresh() }
        }
    }

    static func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleNextRefresh()
        task.expirationHandler = { task.setTaskCompleted(success: false) }
        DispatchQueue.global(qos: .background).async {
            let success = BackgroundRefreshBridge.shared.runBackgroundRefresh()
            task.setTaskCompleted(success: success)
        }
    }

    static func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}
```

`BackgroundRefreshBridge.shared` is Kotlin/Native's generated Swift accessor for a Kotlin
`object` — the same convention Plan 1 relies on implicitly wherever shared code is called from
`iosApp`.

- [ ] **Step 5: Verify on a simulator**

`BGAppRefreshTask` cannot be triggered by waiting in a simulator. Force it instead, after running
the app at least once so the task is registered:

```bash
xcrun simctl push booted world.taqwa.app - <<'EOF'
EOF
```

does not apply here — background *tasks* are triggered from the debugger, not push. In Xcode,
pause at a breakpoint after `BGTaskScheduler.shared.register(...)` runs, then in the LLDB
console:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"world.taqwa.app.refresh"]
```

Expected: `handleAppRefresh` runs, `runBackgroundRefresh()` returns without throwing, and
`center.pendingNotificationRequests` (inspect via the debugger or a temporary log) shows a fresh
12-day window.

- [ ] **Step 6: Commit**

```bash
git add shared/src/iosMain/kotlin/world/taqwa/app/notifications iosApp
git commit -m "feat: iOS calendar-trigger scheduling with BGAppRefreshTask top-up"
```

---

### Task 19: Audio assets, the sound picker sheet, and closing the first two stubs

Everything scheduling needs is real; nothing yet lets a user *choose* a sound or *hear* one before
committing to it. This task also closes two of Plan 1's three deliberate stubs: the onboarding
"Enable notifications" button (Plan 1 Task 13 Step 5) and the inert Settings → Notifications row
(Plan 1 Task 14 Step 5).

**Files:**
- Create: `shared/src/androidMain/res/raw/takbir.ogg`, `shared/src/androidMain/res/raw/adhan_30s.ogg`
- Create: `iosApp/iosApp/Resources/takbir.caf`, `iosApp/iosApp/Resources/adhan-30s.caf`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.ios.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationOnboarding.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationPermission.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/notifications/NotificationPermission.ios.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SoundSheet.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/NotificationSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Consumes: `PrayerSound`, `NotificationSettings` from Task 15; `SoundAssets` from Task 17; `NotificationOnboarding`, `NotificationCoordinator`, `RescheduleTrigger` from Task 17/16; `Screen`, `Navigator`, `AppContainer` from Plan 1 Task 13; `TaqwaCard`, `TaqwaRow`, `CheckMark`, `TaqwaPrimaryButton`, `TaqwaTextLink` from Plan 1 Task 11
- Produces:
  - `interface SoundPreviewPlayer { fun play(sound: PrayerSound); fun stop() }` and `expect fun createSoundPreviewPlayer(): SoundPreviewPlayer`
  - `expect suspend fun requestNotificationPermission(): Boolean` and `expect suspend fun isNotificationPermissionGranted(): Boolean`
  - `@Composable fun SoundSheet(current: PrayerSound, onPick: (PrayerSound) -> Unit, onPreview: (PrayerSound) -> Unit)`
  - `@Composable fun NotificationSettingsScreen(settings: NotificationSettings, onToggleEnabled: (Boolean) -> Unit, onPickLead: (Int) -> Unit, onPickSound: (Prayer, PrayerSound) -> Unit, onPreviewSound: (PrayerSound) -> Unit)`
  - `Screen.NotificationSettings` added to the sealed interface

There is nothing new and pure to test-drive here — every decidable rule was already tested in
Tasks 15–17. This task is code plus a manual walkthrough on both platforms, the same shape Plan 1
used for `IosLocationProvider` and the settings screens.

- [ ] **Step 1: Copy the audio assets into each platform's resource location**

```bash
mkdir -p shared/src/androidMain/res/raw
cp assets/audio/takbir.ogg shared/src/androidMain/res/raw/takbir.ogg
cp assets/audio/adhan-30s.ogg shared/src/androidMain/res/raw/adhan_30s.ogg
```

Android resource names forbid hyphens, which is why `SoundAssets.androidRawResourceName` already
returns `"adhan_30s"` rather than `"adhan-30s"`.

```bash
mkdir -p iosApp/iosApp/Resources
cp assets/audio/takbir.caf iosApp/iosApp/Resources/takbir.caf
cp assets/audio/adhan-30s.caf iosApp/iosApp/Resources/adhan-30s.caf
```

In Xcode, right-click the `iosApp` group → **Add Files to "iosApp"...**, select both `.caf`
files, and confirm **Copy items if needed** is off (they are already inside the project
directory) and **Target Membership: iosApp** is checked. Hand-editing `project.pbxproj` for
resource references is exactly the kind of fragile edit Task 1 warned off; use the Xcode UI.

- [ ] **Step 2: Write the preview player interface**

`shared/src/commonMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.kt`:

```kotlin
package world.taqwa.app.audio

import world.taqwa.app.domain.PrayerSound

/** Lets the sound sheet's play button audition a choice before it is committed to. */
interface SoundPreviewPlayer {
    fun play(sound: PrayerSound)
    fun stop()
}

expect fun createSoundPreviewPlayer(): SoundPreviewPlayer
```

- [ ] **Step 3: Implement the Android player**

`shared/src/androidMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.android.kt`:

```kotlin
package world.taqwa.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets
import world.taqwa.app.settings.appContext

private class AndroidSoundPreviewPlayer : SoundPreviewPlayer {

    private var mediaPlayer: MediaPlayer? = null

    override fun play(sound: PrayerSound) {
        stop()
        // Silent has nothing to audition; the button press itself is the reassurance that
        // nothing plays.
        if (sound == PrayerSound.SILENT) return

        val uri = if (sound == PrayerSound.NOTIFICATION) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            val name = SoundAssets.androidRawResourceName(sound)!!
            Uri.parse("android.resource://${appContext.packageName}/raw/$name")
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(appContext, uri)
            setOnCompletionListener { player -> player.release(); mediaPlayer = null }
            prepare()
            start()
        }
    }

    override fun stop() {
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = AndroidSoundPreviewPlayer()
```

- [ ] **Step 4: Implement the iOS player**

`shared/src/iosMain/kotlin/world/taqwa/app/audio/SoundPreviewPlayer.ios.kt`:

```kotlin
package world.taqwa.app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.notifications.SoundAssets

@OptIn(ExperimentalForeignApi::class)
private class IosSoundPreviewPlayer : SoundPreviewPlayer {

    private var player: AVAudioPlayer? = null

    override fun play(sound: PrayerSound) {
        stop()
        when (sound) {
            // Nothing to audition; the button press itself is the reassurance.
            PrayerSound.SILENT -> return
            // iOS has no public API to play "the" default notification tone outside a
            // delivered notification; this system sound is the closest available stand-in.
            PrayerSound.NOTIFICATION -> AudioServicesPlaySystemSound(1007u)
            PrayerSound.TAKBIR, PrayerSound.ADHAN -> {
                val fileName = SoundAssets.iosResourceFileName(sound)!!
                val path = NSBundle.mainBundle.pathForResource(
                    fileName.substringBeforeLast('.'), fileName.substringAfterLast('.'),
                ) ?: return
                player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
                player?.play()
            }
        }
    }

    override fun stop() {
        player?.stop()
        player = null
    }
}

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = IosSoundPreviewPlayer()
```

- [ ] **Step 5: Add the notification permission request, per platform**

`NotificationPermissionRequester` from Task 17 only *checks* Android's permission; the onboarding
button needs something that *asks*. Add to `notifications/NotificationOnboarding.kt`:

```kotlin
expect suspend fun requestNotificationPermission(): Boolean
expect suspend fun isNotificationPermissionGranted(): Boolean
```

`shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationPermission.android.kt`:

```kotlin
package world.taqwa.app.notifications

// The Activity-scoped POST_NOTIFICATIONS request is driven from Compose via
// rememberLauncherForActivityResult in OnboardingScreen — the same pattern Task 10 uses for
// location — so by the time this suspend function is called the result already reflects it.
actual suspend fun requestNotificationPermission(): Boolean = NotificationPermissionRequester.isGranted()

actual suspend fun isNotificationPermissionGranted(): Boolean = NotificationPermissionRequester.isGranted()
```

`shared/src/iosMain/kotlin/world/taqwa/app/notifications/NotificationPermission.ios.kt`:

```kotlin
package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun requestNotificationPermission(): Boolean = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { granted, _ -> if (cont.isActive) cont.resume(granted) }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun isNotificationPermissionGranted(): Boolean = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        val status = settings?.authorizationStatus
        if (cont.isActive) {
            cont.resume(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional)
        }
    }
}
```

- [ ] **Step 6: Write the sound sheet**

`shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SoundSheet.kt`:

```kotlin
package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.PrayerSound

private fun displayName(sound: PrayerSound): String = when (sound) {
    PrayerSound.SILENT -> "Silent"
    PrayerSound.NOTIFICATION -> "Notification"
    PrayerSound.TAKBIR -> "Takbir"
    PrayerSound.ADHAN -> "Adhan"
}

/** A drawn triangle, matching `CheckMark`'s "never a text glyph for a control" rule. */
@Composable
private fun PlayTriangle(modifier: Modifier = Modifier) {
    val color = LocalTaqwaColors.current.textSecondary
    Canvas(modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            lineTo(size.width * 0.86f, size.height * 0.5f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun SoundSheet(
    current: PrayerSound,
    onPick: (PrayerSound) -> Unit,
    onPreview: (PrayerSound) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.padding(16.dp)) {
        Text("Notification sound", style = TaqwaText.screenTitle)
        TaqwaCard(Modifier.padding(top = 16.dp)) {
            PrayerSound.entries.forEachIndexed { i, sound ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = displayName(sound),
                    onClick = { onPick(sound) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPreview(sound) }) { PlayTriangle() }
                            if (sound == current) CheckMark(Modifier.padding(start = 4.dp))
                        }
                    },
                )
            }
        }
        Text(
            "Notification sounds are capped at 30 seconds on both platforms. The complete " +
                "adhan can be played inside the app.",
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
```

- [ ] **Step 7: Write the notification settings screen**

`shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/NotificationSettingsScreen.kt`:

```kotlin
package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound

private fun englishName(p: Prayer) = when (p) {
    Prayer.FAJR -> "Fajr"; Prayer.SUNRISE -> "Sunrise"; Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"; Prayer.MAGHRIB -> "Maghrib"; Prayer.ISHA -> "Isha"
}

private fun soundName(s: PrayerSound) = when (s) {
    PrayerSound.SILENT -> "Silent"; PrayerSound.NOTIFICATION -> "Notification"
    PrayerSound.TAKBIR -> "Takbir"; PrayerSound.ADHAN -> "Adhan"
}

private fun leadLabel(minutes: Int) = if (minutes == 0) "Never" else "$minutes min"

@Composable
fun NotificationSettingsScreen(
    settings: NotificationSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onPickLead: (Int) -> Unit,
    onPickSound: (Prayer, PrayerSound) -> Unit,
    onPreviewSound: (PrayerSound) -> Unit,
) {
    var soundSheetFor by remember { mutableStateOf<Prayer?>(null) }
    var remindSheetOpen by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Text("Notifications", style = TaqwaText.screenTitle)

        TaqwaCard(Modifier.padding(top = 16.dp)) {
            TaqwaRow(
                label = "Prayer notifications",
                trailing = { Switch(checked = settings.enabled, onCheckedChange = onToggleEnabled) },
            )
        }

        TaqwaCard(Modifier.padding(top = 16.dp)) {
            TaqwaRow(
                label = "Remind me before",
                value = leadLabel(settings.remindBeforeMinutes),
                onClick = { remindSheetOpen = true },
            )
        }

        TaqwaCard(Modifier.padding(top = 16.dp)) {
            ObligatoryPrayers.forEachIndexed { i, prayer ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = englishName(prayer),
                    value = soundName(settings.soundFor(prayer)),
                    onClick = { soundSheetFor = prayer },
                )
            }
        }
    }

    if (remindSheetOpen) {
        ModalBottomSheet(onDismissRequest = { remindSheetOpen = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp)) {
                Text("Remind me before", style = TaqwaText.screenTitle)
                TaqwaCard(Modifier.padding(top = 16.dp)) {
                    NotificationSettings.LeadOptions.forEachIndexed { i, minutes ->
                        if (i > 0) CardDivider()
                        TaqwaRow(
                            label = leadLabel(minutes),
                            onClick = { onPickLead(minutes); remindSheetOpen = false },
                            trailing = { if (minutes == settings.remindBeforeMinutes) CheckMark() },
                        )
                    }
                }
            }
        }
    }

    soundSheetFor?.let { prayer ->
        ModalBottomSheet(onDismissRequest = { soundSheetFor = null }, sheetState = rememberModalBottomSheetState()) {
            SoundSheet(
                current = settings.soundFor(prayer),
                onPick = { onPickSound(prayer, it); soundSheetFor = null },
                onPreview = onPreviewSound,
            )
        }
    }
}
```

- [ ] **Step 8: Wire the onboarding button — the first stub**

In `nav/Screen.kt`, no change is needed for onboarding itself; in `feature/onboarding/OnboardingScreen.kt`,
replace the no-op third step with:

```kotlin
@Composable
private fun NotificationsStep(container: AppContainer, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    val onboarding = remember {
        NotificationOnboarding(
            requestSystemPermission = { requestNotificationPermission() },
            setNotificationsEnabled = { enabled ->
                val current = container.settingsRepository.notificationSettings.first()
                container.settingsRepository.setNotificationSettings(current.copy(enabled = enabled))
            },
            rescheduleIfEnabled = {
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            },
        )
    }
    Column {
        Text("Never miss a prayer", style = TaqwaText.screenTitle)
        Text(
            "A notification at each prayer time. You pick the sound for every prayer " +
                "separately — and can change it whenever you like.",
            style = TaqwaText.caption,
        )
        TaqwaPrimaryButton(
            text = "Enable notifications",
            onClick = { scope.launch { onboarding.enable(); onFinished() } },
        )
        TaqwaTextLink(
            text = "Not now",
            onClick = { scope.launch { onboarding.declineForNow(); onFinished() } },
        )
    }
}
```

`onFinished` is whatever Task 13 already calls on completion of the third step — the flow
advances to `settingsRepository.setOnboardingComplete(true)` and `navigator.replaceAll(Screen.Today)`
exactly as before; only the primary button's action changed from a no-op to a real one.

- [ ] **Step 9: Wire the Settings root row — the second stub**

Add to `nav/Screen.kt`'s sealed interface: `data object NotificationSettings : Screen`.

In `feature/settings/SettingsRootScreen.kt`, replace the Plan 1 placeholder row:

```kotlin
val notificationSettings by container.settingsRepository.notificationSettings.collectAsState(
    initial = world.taqwa.app.domain.NotificationSettings(),
)
val notifValue = if (!notificationSettings.enabled) "Off" else {
    val on = ObligatoryPrayers.count { notificationSettings.soundFor(it) != PrayerSound.SILENT }
    "$on on"
}
TaqwaRow("Notifications", value = notifValue, onClick = { navigator.push(Screen.NotificationSettings) })
```

In `App.kt`, add the branch:

```kotlin
Screen.NotificationSettings -> {
    val settings by container.settingsRepository.notificationSettings.collectAsState(
        initial = world.taqwa.app.domain.NotificationSettings(),
    )
    val previewPlayer = remember { createSoundPreviewPlayer() }
    NotificationSettingsScreen(
        settings = settings,
        onToggleEnabled = { enabled ->
            scope.launch {
                container.settingsRepository.setNotificationSettings(settings.copy(enabled = enabled))
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        onPickLead = { minutes ->
            scope.launch {
                container.settingsRepository.setNotificationSettings(settings.copy(remindBeforeMinutes = minutes))
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        onPickSound = { prayer, sound ->
            scope.launch {
                val updated = settings.copy(sounds = settings.sounds + (prayer to sound))
                container.settingsRepository.setNotificationSettings(updated)
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        onPreviewSound = { previewPlayer.play(it) },
    )
}
```

Every change writes through immediately and reschedules — there is no save button, matching
`PrayerTimesSettingsScreen`'s existing convention from Plan 1 Task 14.

- [ ] **Step 10: Build and manually verify both platforms**

Run: `./gradlew :androidApp:assembleDebug` — Expected: BUILD SUCCESSFUL.
Run: `./scripts/ios-build.sh` — Expected: BUILD SUCCEEDED.

Walk through on both: onboarding's third screen now actually prompts for the system permission;
declining still reaches Today. In Settings → Notifications, tap a prayer, hear Takbir play when
its row's triangle is tapped, pick Adhan, confirm the row now reads "Adhan" and a new
`prayer_<name>_adhan` channel appears on Android (`adb shell dumpsys notification`). Toggle the
master switch off and confirm the Settings root row now reads "Off".

- [ ] **Step 11: Commit**

```bash
git add shared/src/androidMain/res/raw iosApp/iosApp/Resources shared/src/commonMain/kotlin/world/taqwa/app/audio shared/src/androidMain/kotlin/world/taqwa/app/audio shared/src/iosMain/kotlin/world/taqwa/app/audio shared/src/commonMain/kotlin/world/taqwa/app/notifications shared/src/androidMain/kotlin/world/taqwa/app/notifications shared/src/iosMain/kotlin/world/taqwa/app/notifications shared/src/commonMain/kotlin/world/taqwa/app/feature shared/src/commonMain/kotlin/world/taqwa/app/nav shared/src/commonMain/kotlin/world/taqwa/app/App.kt
git commit -m "feat: sound picker, audition playback, and wire the onboarding and settings stubs"
```

---

### Task 20: Qibla — bearing and distance

Everything the compass screen needs to *decide* is pure: the bearing to the Kaaba, the distance,
and the arithmetic of "is the phone currently pointed close enough." Only the sensor stream
that feeds a live heading into this math is platform code, and that is Task 21.

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/qibla/QiblaMath.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/qibla/QiblaApiProbeTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/qibla/QiblaMathTest.kt`

**Interfaces:**
- Consumes: `GeoLocation` from Plan 1 Task 5
- Produces:
  - `object QiblaMath { const val KAABA_LATITUDE = 21.4225; const val KAABA_LONGITUDE = 39.8262; fun bearing(from: GeoLocation): Double; fun distanceKm(from: GeoLocation): Double; fun angleDelta(a: Double, b: Double): Double; fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = 5.0): Boolean }`

- [ ] **Step 1: Probe adhan2's `Qibla` API shape before writing against it**

Same caution as Plan 1 Task 5's `AdhanApiProbeTest` — the published surface is documented but
the exact property name matters.

`shared/src/commonTest/kotlin/world/taqwa/app/qibla/QiblaApiProbeTest.kt`:

```kotlin
package world.taqwa.app.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import kotlin.test.Test
import kotlin.test.assertTrue

class QiblaApiProbeTest {
    @Test
    fun adhanQiblaReturnsADegreeBearingForLondon() {
        val q = Qibla(Coordinates(51.5074, -0.1278))
        assertTrue(q.direction in 0.0..360.0, "was ${q.direction}")
    }
}
```

Run: `./gradlew :shared:allTests --tests "*QiblaApiProbeTest*"`

Expected: PASS. If `direction` does not resolve, open the resolved `adhan2` sources (as Task 5
did) and correct the property name here and in `QiblaMath.bearing` below before continuing.

- [ ] **Step 2: Write the failing test**

`shared/src/commonTest/kotlin/world/taqwa/app/qibla/QiblaMathTest.kt`:

```kotlin
package world.taqwa.app.qibla

import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaMathTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val makkah = GeoLocation(21.4225, 39.8262, "Asia/Riyadh", "Makkah", "SA")
    private val auckland = GeoLocation(-36.8485, 174.7633, "Pacific/Auckland", "Auckland", "NZ")
    private val quito = GeoLocation(-0.1807, -78.4678, "America/Guayaquil", "Quito", "EC")

    @Test
    fun londonBearingMatchesTheKnownValueOfAboutOneHundredAndNineteenDegrees() {
        val bearing = QiblaMath.bearing(london)
        assertTrue(bearing in 118.0..120.0, "was $bearing")
    }

    @Test
    fun bearingIsAlwaysWithinACompassRangeIncludingNearEquatorial() {
        listOf(london, auckland, quito).forEach {
            val b = QiblaMath.bearing(it)
            assertTrue(b in 0.0..360.0, "was $b for $it")
        }
    }

    @Test
    fun distanceFromMakkahToItselfIsEffectivelyZero() {
        assertTrue(QiblaMath.distanceKm(makkah) < 1.0)
    }

    @Test
    fun londonIsRoughlyFortyEightHundredToFiveThousandKilometresFromMakkah() {
        val d = QiblaMath.distanceKm(london)
        assertTrue(d in 4500.0..5100.0, "was $d")
    }

    @Test
    fun theKaabasAntipodeIsRoughlyHalfTheEarthsCircumferenceAway() {
        // The antipode of 21.4225N, 39.8262E sits in the South Pacific.
        val antipode = GeoLocation(-21.4225, -140.1738, "Pacific/Tahiti", "Antipode", "PF")
        val d = QiblaMath.distanceKm(antipode)
        assertTrue(d in 19800.0..20100.0, "was $d")
    }

    @Test
    fun angleDeltaIsTheShortestSignedDifference() {
        assertEquals(10.0, QiblaMath.angleDelta(350.0, 0.0), absoluteTolerance = 0.001)
        assertEquals(-10.0, QiblaMath.angleDelta(0.0, 350.0), absoluteTolerance = 0.001)
        assertEquals(0.0, QiblaMath.angleDelta(45.0, 45.0), absoluteTolerance = 0.001)
    }

    @Test
    fun angleDeltaNeverExceedsAHalfTurnAcrossTheWholeCircle() {
        (0..350 step 10).forEach { a ->
            (0..350 step 10).forEach { b ->
                val delta = QiblaMath.angleDelta(a.toDouble(), b.toDouble())
                assertTrue(delta in -180.0..180.0, "delta $delta for $a -> $b")
            }
        }
    }

    @Test
    fun isAlignedIsTrueWithinFiveDegreesEitherSideAndFalseJustBeyond() {
        assertTrue(QiblaMath.isAligned(heading = 115.0, bearing = 119.0))
        assertTrue(QiblaMath.isAligned(heading = 124.0, bearing = 119.0))
        assertTrue(!QiblaMath.isAligned(heading = 113.0, bearing = 119.0))
        assertTrue(!QiblaMath.isAligned(heading = 125.0, bearing = 119.0))
    }

    @Test
    fun isAlignedWrapsCorrectlyAcrossTheZeroThreeSixtyDegreeSeam() {
        assertTrue(QiblaMath.isAligned(heading = 358.0, bearing = 2.0))
        assertTrue(!QiblaMath.isAligned(heading = 350.0, bearing = 2.0))
    }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*QiblaMathTest*"`
Expected: FAIL — `Unresolved reference: QiblaMath`

- [ ] **Step 4: Implement**

`shared/src/commonMain/kotlin/world/taqwa/app/qibla/QiblaMath.kt`:

```kotlin
package world.taqwa.app.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import world.taqwa.app.domain.GeoLocation
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything decidable about pointing at the Kaaba, decided without a device. The bearing comes
 * from adhan2; the distance is our own haversine, which adhan2 does not provide.
 */
object QiblaMath {

    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle bearing to the Kaaba, degrees clockwise from true north. */
    fun bearing(from: GeoLocation): Double =
        Qibla(Coordinates(from.latitude, from.longitude)).direction

    /** Haversine distance to the Kaaba, in kilometres. */
    fun distanceKm(from: GeoLocation): Double {
        fun rad(d: Double) = d * kotlin.math.PI / 180.0
        val dLat = rad(KAABA_LATITUDE - from.latitude)
        val dLon = rad(KAABA_LONGITUDE - from.longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(rad(from.latitude)) * cos(rad(KAABA_LATITUDE)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Signed shortest angular distance from [a] to [b], in the range (-180, 180]. Positive
     * means [b] lies clockwise of [a]. This is the one piece of arithmetic that makes alignment
     * correct across the 0/360 seam — a naive subtraction reports 349 instead of 11.
     */
    fun angleDelta(a: Double, b: Double): Double {
        var delta = (b - a) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    /** True when [heading] is within [thresholdDegrees] of [bearing]. The spec's "aligned" is
     * exactly this call with the default 5-degree threshold. */
    fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = 5.0): Boolean =
        abs(angleDelta(heading, bearing)) <= thresholdDegrees
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:allTests --tests "*QiblaMathTest*"`
Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/qibla shared/src/commonTest/kotlin/world/taqwa/app/qibla
git commit -m "feat: pure qibla bearing, haversine distance and alignment math"
```

---
