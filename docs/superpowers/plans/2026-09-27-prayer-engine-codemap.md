# Code map for the prayer-engine switch-over (27 September 2026, branch `prayer-engine`)

Root: `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site`. Paths below are relative to it; `app/` means `shared/src/commonMain/kotlin/world/taqwa/app/`.

## What breaks the build if forgotten

1. **adhan2 stays for Qibla**: `app/qibla/QiblaMath.kt` uses it, and `SmokeTest.kt`, `qibla/QiblaApiProbeTest.kt` import it. Do not remove the dependency from `shared/build.gradle.kts` or `tools/timetables/build.gradle.kts`.
2. **`tools/timetables` compiles app sources by an explicit list** (`tools/timetables/build.gradle.kts` lines 14–26, from both `shared/src/commonMain/kotlin` and `widgetcore/src/commonMain/kotlin`). Every new engine file must be added (switch to a `prayer/**` glob plus `domain/TimelineState.kt` if TimelineBuilder is needed). The module has **no Compose and no `Res`**: engine code and registry data must be plain Kotlin (no `Res.readBytes`).
3. `DayPrayerTimes` is constructed directly in `TimelineBuilderTest` and `WidgetMirrorWriterTest`; `CalculationMethodId` is mapped in `i18n/DisplayNames.kt` (`methodDisplayName`, exhaustive `when`), `PrayerTimesEngine.toAdhanParameters`, `tools/timetables/.../AppStrings.kt` (`METHOD_KEYS` map, runtime crash if a value is missing; `AppStringsTest` catches it) and `CalculationMethodDefaults`.
4. `shared` has **no `jvm()` target**: commonTest runs on the Android host (`:shared:testAndroidHostTest`) and iOS (`:shared:iosSimulatorArm64Test`). `scripts/test.sh` runs everything (sets DEVELOPER_DIR). CI: `tests.yml` (`:shared:testAndroidHostTest :widgetcore:testAndroidHostTest :androidApp:assembleDebug`, `scripts/check-strings.sh`), `ios.yml`, `pages.yml` (`./gradlew -p tools/timetables jvmTest generate`).

## Current engine (`app/prayer/`)

- `PrayerTimesEngine` (no constructor args): `fun timesFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): DayPrayerTimes` is the only public API; `internal var solveCount`, `internal fun engagedCacheSize()` exist for tests. `engagedCache` (LinkedHashMap, 8 entries, not synchronized). adhan2 mapping via `CalculationMethodId.toAdhanParameters()`; TEHRAN hand-built; polar days caught and recomputed at 48°; minute adjustments added flat; `highLatitudeRuleApplied` set when the rule changed Fajr/Isha.
- `HighLatitudeSelector` (used by the engine and `PrayerTimesSettingsScreen.kt:274`), `CalculationMethodDefaults.forCountry` (used by `SettingsRepository.kt:21,270` and `tools/timetables/.../Timetable.kt:18,45`), `NightThirds.lastThirdStart(maghrib, fajr)` (used by `NotificationPlanner.kt:73`), `TimelineBuilder.build(yesterday, today, tomorrow, now, showSunrise, zone): TodayState` and `isJumuah` (used by `TodayViewModel.kt:186`, `WidgetMirrorRefresher.kt:41`). Current = last obligatory prayer ≤ now; next = first after now else tomorrow's Fajr; ring start = last ≤ now else yesterday's Isha.
- Domain: `app/domain/Prayer.kt` has `PrayerTime(prayer, instant)` and `DayPrayerTimes(date, times: List<PrayerTime>, highLatitudeRuleApplied: HighLatitudePreference?, nearestLatitudeFallbackApplied = false) { fun time(p): Instant }` (throws when missing). The **`Prayer` enum (FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA) and `ObligatoryPrayers` live in `widgetcore/src/commonMain/kotlin/world/taqwa/app/domain/Prayer.kt`** — reuse it in the engine. `app/domain/TimelineState.kt`: `PrayerStatus`, `TimelineRow(prayer, instant, status, isJumuah)`, `TodayState(rows, next, countdown, ringProgress)`.
- Time types: `kotlin.time.Instant`/`Clock` (stdlib) with kotlinx-datetime 0.8 `LocalDate`, `TimeZone`. Kotlin 2.4.10.

## Callers of the engine (production)

| Where | Call |
|---|---|
| `app/di/AppContainer.kt:138` | `val prayerTimesEngine = PrayerTimesEngine()` singleton; passed to `NotificationCoordinator` (144–151) |
| `app/feature/today/TodayViewModel.kt:60,176-178` | yesterday/today/tomorrow on every 1 s tick |
| `app/notifications/NotificationPlanner.kt:38,59,68` | the day before (Tahajjud) and each window day |
| `app/notifications/NotificationCoordinator.kt:9,20` | holds the engine |
| `app/widget/WidgetMirrorRefresher.kt:29,38-40` | yesterday/today/tomorrow |
| `app/feature/settings/PrayerTimesSettingsScreen.kt:55,296,302` | `ManualAdjustmentsScreen` (today, on every recomposition) |
| `app/SettingsRoutes.kt:237`, `app/PrayerRoutes.kt:58`, `app/AppEffects.kt:139` | wiring |
| `shared/src/androidMain/.../notifications/PrayerAlarmReceiver.kt:123`, `SystemEventReceiver.kt:54` | `WidgetMirrorRefresher.refresh` on `Dispatchers.Default` — **the cache must be thread-safe** |
| `shared/src/iosMain/.../notifications/BackgroundRefreshBridge.kt:5,24` | a fresh `PrayerTimesEngine()` |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Timetable.kt:19,43,63` | `Timetable(engine = PrayerTimesEngine())`; also site-only post-processing: Ramadan +30 Isha for UMM_AL_QURA (70–76), `check(dhuhrDate == date)` refusing Apia (80–84) |

Readers of `DayPrayerTimes` extras: `TodayViewModel.noteFor` → `HighLatitudeCopy.note(...)` (to be removed), `Timetable.kt:91`.

## Settings (`app/settings/`, `app/domain/PrayerSettings.kt`)

- `PrayerSettings(method = MWL, madhab = STANDARD, highLatitude = AUTOMATIC, hijriOffsetDays = 0, showSunrise = false, minuteAdjustments: Map<Prayer, Int>)`; enums `AsrMadhab`, `HighLatitudePreference`, `CalculationMethodId` (MWL, ISNA, EGYPTIAN, UMM_AL_QURA, KARACHI, TEHRAN, DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, MOONSIGHTING_COMMITTEE).
- Keys (`SettingsKeys.kt`): `calculation_method`, `calculation_method_user_chosen` (Boolean), `asr_madhab`, `high_latitude`, `hijri_offset_days`, `show_sunrise`, `minute_adjustments` (`"FAJR:5,ISHA:-3"`); location keys `location_latitude`, `location_longitude`, `location_timezone`, `location_city`, `location_city_id`, `location_city_display_name`, `location_city_display_language`, `location_country`, `location_source`.
- `SettingsRepository(store: DataStore<Preferences>)`: `methodUserChosen: Flow<Boolean>`, `prayerSettings: Flow<PrayerSettings>` (hijri clamped −1..1), `location: Flow<GeoLocation?>`, `setPrayerSettings` (writes six fields), `setMethodUserChosen()` (only from `SettingsRoutes.kt:195`), `applyCountryDefaultMethod(countryCode)` (**called at `app/App.kt:234`, `app/SettingsRoutes.kt:298`, `app/location/LocationRefresher.kt:114`**), `setLocation` (rounds to 3 decimals), test-only raw writers. No migration mechanism exists (only `TodayViewModel.migrateCityId`). Test stores: `PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-$name.preferences_pb".toPath() }` (absolute path needed for iOS).
- **Nothing observes `prayerSettings` to reschedule notifications**: a method or adjustment change reaches alarms only on the next foreground/alarm/top-up/boot. The new Settings screens should call `NotificationCoordinator.reschedule(SETTINGS_CHANGED)` after a prayer-settings write (the notification settings route does this at `SettingsRoutes.kt:112-151`).

## Location

- `GeoLocation(latitude, longitude, timeZoneId, cityName?, countryCode?, cityId?)` — no elevation, no accuracy. `LocationRepository.resolveGpsLocation` uses the exact fix with the phone's zone and the nearest city's name/country/id. `LocationRefresher.refresh` writes a new location on a 5 km move or zone change.
- `CityRepository` reads `files/cities.csv` (`id,name,region,country,countryCode,lat,lon,tz`, 34,135 rows; no admin-2) via `Res.readBytes` in `AppContainer.kt:45-58`.

## UI

- `app/feature/today/TodayScreen.kt`: `TodayScreen(state, onChooseCity, onAllowLocation, onOpenQibla, onOpenTasbeeh, exactAlarmsOff, onAllowExactAlarms)`; `PortraitBody` order: CityAndDates, Countdown, TimelineCard, QiblaCard, ExactAlarmsCard, HighLatitudeCard. `LandscapeBody`: left pane CityAndDates + Countdown (not scrolling), right pane scrolls the rest. `CityAndDates` (245–289) is a Row: city, then `hijri · ⁨gregorian⁩`, then `TasbeehButton` — **the ⓘ goes into the date Text**. `HighLatitudeCard` (355–372) is removed by the spec. `formatClock` (519) is shared.
- `TodayViewModel`: `TodayUiState.Ready(location, cityDisplayName, hijri, gregorian, today, highLatitudeNote, qiblaBearingDegrees, qiblaDistanceKm)`; `refresh()` computes three days each tick, hijri via `TabularHijriCalendar`, builds the timeline, writes the widget mirror when it changed; no try/catch.
- `app/feature/today/PrayerTimeline.kt`: `PrayerTimeline(rows, horizontalPadding, formatTime)`; each row: pip, FlowRow of names, `if (row.isJumuah) JumuahPill()` (178), time; `JumuahPill` (207–223) is the outlined pill to reuse for "Set by rule".
- `app/feature/settings/PrayerTimesSettingsScreen.kt`: `PrayerTimesSettingsScreen(settings, today, onChange, onBack, onOpenMethodPicker, onOpenHighLatitudePicker, onOpenManualAdjustments)`; `MethodPickerScreen`, `HighLatitudePickerScreen`, `ManualAdjustmentsScreen(settings, location, engine, today, onChange, onBack)`; `ADJUSTMENT_LIMIT = 59`.
- `app/SettingsRoutes.kt`: `SettingsRootRoute` (root row value = `methodDisplayName`), `PrayerTimesSettingsRoute` (163), `MethodPickerRoute` (182), `HighLatitudePickerRoute` (204), `ManualAdjustmentsRoute` (224), `CitySearchRoute` (270). `app/App.kt`: route dispatch at 467 (PrayerTimesSettings), 474, 481, 488, 506; `today` remembered per zone (249). Screens are data objects in `app/nav/Screen.kt:25-31`.
- `app/i18n/HighLatitudeCopy.kt` (Kotlin maps, 7 languages; only TodayViewModel uses it), `app/i18n/DisplayNames.kt` (`methodDisplayName`, `highLatitudeDisplayName`, `madhabDisplayName`).

## Notifications

- `NotificationPlanner.plan(location, settings, notifications, engine, from, windowDays, capacity, copy, formatClockTime)`: Tahajjud at `NightThirds.lastThirdStart(prevMaghrib, fajr)`, body `"The last third of the night has begun · {prayer} at {time}"` (NotificationCopy.kt:49, LocalizedNotificationCopy.kt:91-134); ids `"${prayer}-PRAYER-$date"`, `"${prayer}-REMINDER-$date"`, `"TAHAJJUD-$date"`.
- `NotificationCoordinator(engine, settingsRepository, locationOf, scheduler, now, capacity, locationFor).reschedule(trigger)`; triggers include SETTINGS_CHANGED and BOOT_COMPLETED (MY_PACKAGE_REPLACED maps to BOOT_COMPLETED in `SystemEventReceiver`).

## Widgets

- `widgetcore` has **no dependencies** (not even kotlinx-datetime). `WidgetSnapshot` / `ScheduledPrayer(prayer, epochSeconds, clockTime, dayIndex)`; `WidgetContentBuilder.build(snapshot, nowEpochSeconds)` decides current/next from the schedule alone. Writer: `app/widget/WidgetMirrorWriter.kt` (`snapshotOf(today, timeZoneId, format, days)`). Readers: Android `TaqwaGlanceWidget.kt`, iOS `TaqwaWidgetViews.swift`. **Leave widgets' logic unchanged in this release** (spec §10.7).

## tools/timetables

- `src/jvmMain/kotlin/world/taqwa/timetables/`: `Main.kt`, `Timetable.kt` (engine + post-processing to remove), `Catalog.kt` (`City.madhab = PrayerSettings().madhab`), `Document.kt` (emits `method.name`, `madhab.name`, `highLatitude`, `ramadanIsha`, epochs), `AppStrings.kt` (`METHOD_KEYS`). `src/jvmTest/...`: 55 tests; `TimetableTest` pins Tripoli 2026-09-13 at 5:26/13:04/16:34/19:16/20:35 and Makkah's Ramadan 120 min, the London DST change, Hanafi later than Standard, Apia refused — update these when the engine changes. `TestPaths` reads `-Dtaqwa.repoRoot`. Site: `site/timetables.py` reads `_data/timetables.json`; `site/assets/timetable.js` reimplements current/next. The website slice is last: keep `generate` working with the new engine but change nothing on the site.

## Strings

- Locales: `values` (en), `values-ar`, `values-bn`, `values-fr`, `values-id`, `values-in` (**must be byte-identical to `values-id`**; run `tools/sync-indonesian.py`), `values-tr`, `values-ur`; 335 strings each. `scripts/check-strings.sh` runs `tools/i18n-check.py` (keys and placeholders match English).
- Prayer-related names: `prayer_*`, `today_next_in`, `today_latitude_label`, `today_jumuah`, `settings_prayer_times`, `minutes_count` (plural), `prayer_times_method`, `prayer_times_high_latitude`, `prayer_times_manual`, `prayer_times_madhab_label`, `prayer_times_hijri_label`, `prayer_times_show_sunrise`, `madhab_standard`, `madhab_hanafi`, `hijri_*`, `adjustments_*`, `method_*` (incl. `method_tehran`, `method_tehran_maghrib_note`), `method_picker_note`, `high_lat_*`, `manual_note`, `credit_calculation`, `credit_calculation_detail` ("Adhan by Batoul Apps, MIT licence." — adhan2 stays for Qibla; update the credit to say so).
- Kotlin-side copy: `HighLatitudeCopy`, `LocalizedNotificationCopy`, `widgetcore/.../i18n/PrayerNaming.kt`.

## Tests that depend on the engine (update, don't delete, unless the behaviour is removed)

`prayer/PrayerTimesEngineTest.kt` (18), `HighLatitudeSelectorTest.kt` (6), `CalculationMethodDefaultsTest.kt` (8), `NightThirdsTest.kt` (3), `TimelineBuilderTest.kt` (17), `notifications/NotificationPlannerTest.kt` (22), `TahajjudPlanTest.kt` (10), `NotificationCoordinatorTest.kt` (5), `feature/today/TodayViewModelTest.kt` (22; asserts the high-latitude note text), `widget/WidgetMirrorWriterTest.kt` (15), `i18n/HighLatitudeCopyTest.kt` (6), `settings/SettingsRepositoryTest.kt` (48; four test `applyCountryDefaultMethod`), `location/LocationRefresherTest.kt` (16; line 84 asserts a move sets TURKEY).
