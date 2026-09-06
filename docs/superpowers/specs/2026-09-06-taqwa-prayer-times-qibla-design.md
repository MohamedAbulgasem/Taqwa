# Taqwa — Slice 1: Prayer Times, Adhan Notifications & Qibla

**Date:** 2026-09-06
**Status:** Approved design, ready for implementation planning
**Scope:** First of four slices. Ships as a complete, useful app on its own.

---

## 1. Product

Taqwa is a free Islamic app for Android and iOS, built with Kotlin Multiplatform. No ads, no accounts, no tracking, no paid tiers, and no recurring infrastructure cost. It works offline after first setup.

The full product is four slices, each with its own spec, plan and build cycle:

| Slice | Contents |
|---|---|
| **1 (this spec)** | Prayer times, adhan notifications, qibla, settings, widgets |
| 2 | Quran reader — Arabic text with translations beneath |
| 3 | Quran audio — reciters, playback, downloads |
| 4 | Dhikr — adhkar, tasbih; plus khatm plan and streak |

Slice 1 is first because it front-loads the riskiest platform work (iOS background notification scheduling, sensor fusion) and because prayer times are the daily-open habit that brings people back.

**Zero recurring cost is a hard constraint.** Slice 1 satisfies it completely: prayer times and qibla are pure mathematics, location comes from the device, and the city database is bundled. Nothing in slice 1 contacts a server.

---

## 2. Scope

### In scope

- First-run onboarding (3 screens)
- Today screen: countdown ring, prayer timeline
- Prayer time calculation with method, madhab and high-latitude handling
- Hijri date with ±1 day adjustment
- Location via GPS, with a bundled offline city database as fallback
- Per-prayer notifications with four sound levels
- Qibla compass with true-north correction and calibration states
- Settings: location, prayer times, notifications, appearance, language, about/attribution
- Light, dark and system theming
- Home screen widgets (small, medium, iOS lock screen) with four background options

### Explicitly out of scope

Quran, Dhikr content, adhkar text, tasbih, khatm plan, bookmarks, the bottom tab bar, mosque finder, zakat calculator, hadith, community features, prayer tracking, 99 Names.

**No tab bar in slice 1.** Today is the root screen; qibla and settings are reached from header icons. Shipping a four-tab bar with two dead tabs is precisely the unfinished feeling this app exists to avoid, and a two-tab bar reads as a mistake. The tab bar arrives with slice 2 and only ever grows — it never rearranges.

The adhkar nudge card is **not** in slice 1 in any form. Today ends with the timeline. The nudge arrives in slice 4 alongside the content it points to.

---

## 3. Design language

### Palette — six tokens per mode, and nothing else

| Role | Light | Dark |
|---|---|---|
| Background | `#FBFAF7` | `#0B0D0C` |
| Surface (card) | `#FFFFFF` | `#131614` |
| Hairline | `#E7E5DD` | `#232825` |
| Text secondary | `#6F6E62` | `#8C948F` |
| Text primary | `#16160F` | `#F1F3F1` |
| Accent | `#B5820B` | `#F0B429` |

A third text tone is used for the faintest labels: `#A5A498` light, `#5A625D` dark.

The accent differs between modes deliberately: `#F0B429` fails contrast on a light surface, so light mode deepens it to `#B5820B`. They read as the same colour and both remain legible.

**Amber is the only colour in the app.** There is no success green, no error red, no secondary accent. Warning and error states are expressed through the accent, weight, and plain language. If a future feature appears to need a new colour, treat that as evidence the feature does not belong here.

Ring stroke in light mode uses `#E3A21C` — a large-area stroke tolerates a lighter value than text does. Dark mode uses the accent directly with a faint outer glow; light mode has no glow.

### Typography

- **Latin:** Manrope (OFL, variable 200–800, ~90 KB subset). Bundled.
- **Arabic:** system default — SF Arabic on iOS, the OEM font on Android. Not bundled.
- Tabular figures everywhere a time is shown. Verified: Manrope holds digit width, so the countdown does not jitter as it ticks.

The Arabic decision is deliberate and has a known cost: Android's Arabic face varies by manufacturer, so Arabic will not look identical across Android devices. In slice 1 this affects five prayer names and is acceptable. **Revisit at slice 4**, where adhkar are vocalised Arabic at paragraph length and OEM harakat rendering becomes a real problem.

### Components

- **Ring** — the app's defining motif. Countdown on Today, dial on Qibla. One idea carrying two features.
- **Timeline rail** — the prayer list on Today. Not used elsewhere.
- **Card with hairline border and inner dividers** — every settings group, and the widget preview. Radius 18dp, 1px border, no shadow.
- **Buttons** — full-width pill, radius 999, ~46pt tall. Secondary action is a plain centred text link, never a second button. There is one obvious action per screen.
- **Selection mark** — a drawn vector check (stroke 2.6, round caps), accent coloured. Never the `✓` text character, which inherits font weight and sits wrong on the baseline.
- **Radio** — used only in the sound picker sheet, where showing unselected targets aids the choice.
- **Toggle** — accent when on, hairline-grey when off.

All tap targets ≥44pt.

---

## 4. Screens

### 4.1 Onboarding — three screens

Each asks for exactly one thing and states why *before* the system dialog appears. Unexplained permission prompts get denied, and a denied location permission is the difference between a working app and a dead one.

1. **Welcome** — app mark (the ring), name, and the three promises: free forever, no ads or account, works offline.
2. **Location** — explains that times depend on exact position and that everything is computed on-device. Primary: *Use my location*. Secondary: *Choose a city instead*.
3. **Notifications** — explains that sound is chosen per prayer and changeable. Primary: *Enable notifications*. Secondary: *Not now*.

Declining either permission is a first-class path, not a dead end. Onboarding is shown once; completion is persisted.

### 4.2 Today — root screen

Header: location name and Hijri date on the left; compass and settings icon buttons on the right.

**Countdown ring** — next prayer name, time remaining (H:MM), and clock time. The ring's filled arc represents elapsed proportion of the interval between the previous and next prayer.

**Prayer timeline** — five rows on a vertical rail:

| State | Pip | Row |
|---|---|---|
| Passed | filled, muted | 44% opacity |
| Current | larger, accent, with halo | accent, bold |
| Upcoming | hollow ring, hairline border | normal |

Each row shows the prayer name in the UI language, the Arabic name in the system face, and the time. Arabic appears on every row.

**Localisation rule:** when the UI language is Arabic, the Arabic name is shown alone — never duplicated beside a transliteration. In every other language the pair is shown: localised name plus Arabic. This rule governs the widgets and notification text too.

**Numerals follow platform locale formatting; never hard-code a digit set.** CLDR already resolves this correctly: `ar-LY`, `ar-MA`, `ar-TN` and `ar-DZ` default to Western digits, while `ar-EG` and `ar-SA` default to Arabic-Indic. A Libyan user must see `3:42`, an Egyptian user `٣:٤٢`, with no setting between them.

**Verified on device (Task 22): Arabic-Indic digits in the system Arabic font are NOT tabular** — a 4 px horizontal shift was measured across a minute boundary. The fallback below is therefore in effect: the countdown ring uses Western digits in ar-EG/ar-SA while the timeline's clock times keep the locale's own digits. This is an accepted, deliberate deviation from "one digit set per locale"; the two elements carry different expectations (a duration versus a clock time) and a jittering countdown is the worse failure. Revisit only if a tabular Arabic-Indic font is bundled.

**Original note, kept for the record:** Arabic-Indic digits render in the system Arabic font rather than Manrope, and that font's digits may not be tabular — which would make the countdown jitter as it ticks for Egyptian and Gulf users. Verify on device. If they are not tabular, the countdown ring specifically falls back to Western digits (it is a duration, not a clock time, so it carries less locale expectation); the timeline's clock times keep the locale's own digits regardless.

**Requirement:** the timeline is time-dependent, so Today re-renders on a clock tick. When Asr arrives, its pip must fill and its row dim without the user refreshing. A coroutine tick in the view model, cancelled when the screen is not resumed.

**Two states that must be built, not deferred:**

- **Location denied** — replaces the ring and timeline with a clear explanation and two actions (*Choose a city*, *Allow location instead*). Never an error toast over an empty screen.
- **High latitude** — normal layout plus a note stating which rule is in force: "The sun never sets far enough here. Fajr and Isha use the one-seventh rule." Most apps either hide this or invent times; it is why they lose their UK and Nordic users.

### 4.3 Qibla

Reached from the compass icon. Three states:

- **Searching** — dial with tick marks, Kaaba marker on the rim at the qibla bearing, needle from centre, bearing in degrees and great-circle distance to Makkah.
- **Aligned** — within 5°, the rim lights and the device emits a single haptic tick. Confirmation you can feel without looking.
- **Low accuracy** — the dial dims to 28% and the screen asks for a figure-of-eight motion, explaining that metal, cases and speakers cause interference. It does not point anywhere. Saying "I don't know" is the correct behaviour.

### 4.4 Settings

Root, in three groups:

- **Prayer** — Location, Prayer times, Notifications
- **App** — Appearance, Language
- **About** — About Taqwa, Attribution & licences

**Prayer times:** calculation method, high-latitude rule, manual adjustments, Asr madhab (segmented Standard/Hanafi), Hijri date (segmented −1 / Umm al-Qura / +1, with the resulting date shown beneath), show sunrise toggle.

**Notifications:** master toggle; *Remind me before* (default **Never**); then one row per prayer showing its current sound. Tapping a prayer opens a bottom sheet.

**Sound sheet** — four options, each auditionable via a play button before committing:

| Option | Behaviour |
|---|---|
| Silent | Banner only, no sound |
| Notification | The phone's default tone |
| **Takbir** (default) | "Allahu akbar, Allahu akbar" — ~6s |
| Adhan | Full call — 30s |

A footnote in the sheet states that notification sounds are capped at 30 seconds on both platforms and that the complete adhan can be played inside the app.

**Location:** *Use my location* toggle; a card showing the resolved city, country and **IANA timezone** (when times look wrong, the timezone is usually why); *Choose a city instead*; and a note that coordinates never leave the device.

**City search:** search field over the bundled database. Every result shows its region beneath the name — there are eleven Londons, and picking the wrong one silently breaks every time in the app with no visible cause.

**Appearance:** Theme (System / Light / Dark) and Widget background.

**Attribution & licences:** a required screen, not a courtesy. GeoNames is CC BY 4.0 and Adhan is MIT; both mandate credit.

### 4.5 Widgets

- **Small (2×2)** — next prayer name, countdown, clock time. One question answered.
- **Medium (4×2)** — countdown on the left, all five times on the right, current prayer in accent.
- **iOS lock screen** — circular complication (ring + abbreviation + countdown) and rectangular.

**Background is a user setting** with four values: Follow theme (default), Light, Dark, Translucent.

Translucency is genuine on Android — Glance renders a semi-transparent surface over the wallpaper. **iOS cannot do this**: WidgetKit cannot sample the wallpaper for its own blur, and iOS may tint the widget to match the home screen regardless.

Rather than explain that discrepancy in a footnote, **the fourth option is labelled per platform**: on Android it reads *Translucent — blends into your wallpaper*; on iOS, *Frosted — uses the system widget material*. Each platform describes what it actually does, and neither user reads a caveat about the other's phone.

Beneath the option list sits a **live preview** of the medium widget rendered in the currently selected background, over a neutral wallpaper swatch. It replaces the explanatory note entirely: the user sees the result instead of reading about it, and it updates as the selection changes.

Widgets are the **last work in slice 1** so they can be cut without disturbing anything else.

---

## 5. Architecture

Compose Multiplatform for all UI, one shared codebase.

```
:core:designsystem    tokens, theming, type scale, shared components
:core:datetime        Hijri conversion, formatting, timezone handling
:core:location        expect/actual GPS, city database, permission state
:core:notifications   expect/actual scheduling
:core:settings        persisted preferences
:feature:prayertimes  calculation engine + Today screen
:feature:qibla        bearing maths + sensor fusion + compass screen
```

**The governing rule: every platform-specific thing sits behind an `expect`/`actual` interface that returns plain data.** Sensors, GPS and notification scheduling are the only three places `actual` code exists. All mathematics lives in `commonMain` and is unit-testable without a device. This is what makes slice 2 cheap.

Modules stay small and single-purpose. If a file grows large enough to be hard to hold in your head, that is the signal its responsibilities have blurred.

### Platform targets

- **Android:** minSdk 26 (notification channels), targetSdk latest stable
- **iOS:** 16.0+ (lock screen widgets)

### Libraries

Pinned at planning time:

| Dependency | Version | Note |
|---|---|---|
| Kotlin | 2.2.20 | Recommended floor for iOS targets |
| Compose Multiplatform | 1.12.0 | Latest stable |
| adhan2 | 0.0.7 | MIT, KMP, prayer times + qibla |
| kotlinx-datetime | 0.7.1 | |
| AndroidX DataStore | 1.1.7 | Preferences only; the KMP-supported artifact |

**No navigation library.** Slice 1's navigation is a root screen with a handful of pushed children. A sealed-class backstack in a `StateFlow` is about forty lines, is fully testable in `commonMain`, and avoids taking a dependency on a fast-moving multiplatform navigation API for no benefit. Revisit when the tab bar arrives in slice 2.

**No DI framework.** Manual constructor injection with a single `AppContainer`. Koin earns its place when there is a graph worth managing; there is not one yet.

Package identifier: `world.taqwa.app` (the `taqwa.world` domain is available and should be registered). The name "Taqwa" still needs App Store, Play and trademark checks before release.

---

## 6. Prayer time calculation

**`com.batoulapps.adhan:adhan2:0.0.7`** (Batoul Apps, MIT) — already a Kotlin Multiplatform library with JVM and iOS targets, so it is a dependency, not a port. It supplies `PrayerTimes`, `CalculationMethod`, `Madhab`, `HighLatitudeRule`, `PrayerAdjustments`, `SunnahTimes` and `Qibla`. It requires Android API 26+ or core library desugaring, which our minSdk 26 already satisfies.

**Methods:** Muslim World League, ISNA, Egyptian, Umm al-Qura, Karachi, Tehran, Dubai, Kuwait, Qatar, Singapore, Diyanet, Moonsighting Committee.

**Asr madhab:** Standard (Shafi'i) default; Hanafi option.

**High latitudes.** Above roughly 48°, Fajr and Isha cease to exist for part of the year because the sun never drops far enough below the horizon. Implement the standard rules — middle of the night, one-seventh of the night, twilight angle — auto-selected by latitude, and **display which rule is in effect** rather than silently fudging. This governs the UK, Scandinavia, Canada and Russia.

**Manual adjustments:** per-prayer offset in minutes, for local mosque conventions.

**Method auto-detection on first run**, from the resolved country: Saudi Arabia → Umm al-Qura; Türkiye → Diyanet; North America → ISNA; Egypt → Egyptian; Pakistan, India, Bangladesh → Karachi; Indonesia, Malaysia, Singapore → Singapore; otherwise Muslim World League. Always user-overridable.

---

## 7. Hijri date

Umm al-Qura tabular calendar. It can differ by a day from local moonsighting, and during Ramadan people care intensely — so a **±1 day adjustment** is offered in settings, showing the resulting date live. Cheap to build; its absence generates furious reviews.

---

## 8. Location

GPS requested once and coordinates cached. Times are recomputed when the device has moved more than 5 km from the cached position, when the IANA timezone changes, or at the start of a new day — not on every location update. **When-in-use permission only** — background location is never requested, because times are computed from cached coordinates.

Declining falls through to a bundled city picker built from GeoNames `cities15000` (CC BY 4.0, ~25,000 cities, ~1 MB), each row carrying name, region, country, latitude, longitude and IANA timezone. Search works with no connection.

Coordinates are stored on device and never transmitted. This claim appears in the UI and must remain literally true.

---

## 9. Notifications

Scheduling logic lives in `commonMain`; the platform layer is deliberately thin.

**iOS** — `UNCalendarNotificationTrigger` with a bundled `.caf` sound. iOS permits a maximum of **64 pending local notifications**, which at five prayers a day allows roughly a 12-day rolling window. Top up on foreground and via `BGAppRefreshTask`.

**Android** — `setExactAndAllowWhileIdle`, rescheduled on `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`. **A notification channel's sound is immutable after creation**, so changing a prayer's sound means creating a new channel, never editing the existing one. Channel identity must therefore encode the sound.

Sound assets bundled: a ~6s takbir and a ~30s adhan, in each platform's required format.

Full adhan playback is available **inside the app** via a player, on both platforms. Neither platform can play a three-minute adhan from a background notification, and the app does not pretend otherwise.

---

## 10. Qibla

Great-circle bearing to the Kaaba (21.4225° N, 39.8262° E), computed by adhan2's `Qibla` class. Distance to Makkah is our own haversine, which adhan2 does not provide.

- **Android:** `TYPE_ROTATION_VECTOR`, with magnetic declination corrected via `GeomagneticField`. Fall back to accelerometer + magnetometer if unavailable.
- **iOS:** `CLHeading.trueHeading`, which handles declination and provides the system calibration prompt.

**Both must resolve to true north.** Using magnetic north is the classic qibla bug and is wrong by up to 15° in some regions.

The needle is low-pass filtered to prevent jitter. The low-accuracy state engages when Android reports `SENSOR_STATUS_ACCURACY_LOW` or `UNRELIABLE`, or when iOS reports `headingAccuracy` above 20° or negative. Below that bar the app shows the calibration state rather than pointing confidently at the wrong thing. Devices with no magnetometer show the numeric bearing and instruct the user to use a physical compass.

---

## 11. Settings and defaults

| Setting | Default |
|---|---|
| Theme | System |
| Calculation method | Auto-detected from country |
| Asr madhab | Standard |
| High latitude rule | Automatic |
| Hijri adjustment | 0 |
| Show sunrise | Off |
| Prayer notifications | On, if permission granted |
| Sound, all five prayers | Takbir |
| Remind me before | **Never** |
| Widget background | Follow theme |
| Language | System |

Defaults are chosen so a user who never opens settings still gets correct times.

---

## 12. Error and edge states

Each is a designed state, not a discovered one:

- Location permission denied → city picker path on Today
- GPS unavailable, coordinates cached → use cache silently
- Latitude too high for conventional calculation → apply rule, state which
- Notification permission denied → in-app explanation with a route to system settings
- No magnetometer → numeric bearing only
- Device clock or timezone changed while backgrounded → recompute and reschedule
- Crossing midnight, and DST transitions → correct next-prayer resolution

---

## 13. Testing

The mathematics is pure functions in `commonMain`, so it is genuinely testable without a device:

- **Prayer times** — golden-value tests across a matrix of cities, methods and dates, cross-checked against Adhan's own published test data.
- **High latitude** — Tromsø across a full year, asserting each rule engages at the right threshold.
- **Hijri** — known date pairs, plus the ±1 offset.
- **Qibla** — known bearings for known cities (London ≈ 118.9°), including antipodal and equatorial edge cases.
- **Scheduling** — pure scheduling logic: rolling window generation, the 64-notification cap, reschedule triggers, DST boundaries.
- **Timeline state** — given a clock time, the correct partition into passed / current / upcoming.

`actual` layers stay thin enough to verify by hand on device.

---

## 14. Attribution and licensing

| Asset | Licence | Obligation |
|---|---|---|
| Adhan algorithm | MIT | Credit in app |
| GeoNames cities15000 | CC BY 4.0 | Credit in app |
| Manrope | OFL | Credit; no restriction on embedding |
| Adhan and takbir audio | CC0 1.0 | None required |

Audio is sourced from ["Beautiful adhan" by Adam-synagda](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg) on Wikimedia Commons, under CC0 1.0 — a public domain dedication by the person who made the recording, which is the only kind of free licence worth relying on for this. Two clips are derived from it, cut on silence-detected phrase boundaries so neither ends mid-word: `takbir` (one complete "Allahu akbar, Allahu akbar" pair, 15.80s) and `adhan-30s` (the full four-takbir opening, 29.95s). Both sit under the platform 30-second cap. Provenance, licence and reproduction commands are recorded in `assets/audio/README.md`.

**A CC0-labelled recording on Freesound was rejected**: its own description states it was extracted from YouTube, and someone who did not create a recording cannot validly dedicate it to the public domain.

**Two findings from the actual audio that correct earlier assumptions:**

1. **A 6-second takbir is not achievable with a melodic adhan.** This spec originally assumed ~6s. In practice one complete "Allahu akbar, Allahu akbar" pair takes 15.8 seconds, and cutting at 6s truncates the muezzin mid-word. A genuinely 6-second takbir needs a plainly recited, non-melodic source — a different recording, not a different edit.
2. **A 30-second clip cannot reach the shahada.** At this pace 30 seconds covers the four opening takbirs and no more; reaching "Ashhadu an la ilaha illa Allah" would need roughly 45 seconds, beyond what either platform allows. The two sound options therefore differ in length rather than content: two takbirs versus four.

**Both accepted.** Two takbirs versus four is the shipped distinction; the sheet labels them by length rather than implying different content. **This is a placeholder to revisit before release** — including whether a specific muezzin should be chosen deliberately, and whether to offer a choice of muezzin as the established apps do.

---

## 15. Definition of done

Slice 1 is complete when the app tells you when to pray, wakes you for it, points you at Makkah, and does all three offline, in light and dark, on both platforms — with every state in section 12 built and tested.

---

## 16. Known risks

1. **iOS 64-notification cap** — if a user does not open the app for 12 days, notifications stop. `BGAppRefreshTask` is best-effort and iOS may not run it. **Decided:** schedule the maximum window, refresh on every foreground, and accept the residual risk. It is a platform limit, not a defect, and no amount of engineering removes it.
2. **Android OEM battery optimisation** — aggressive vendors (Xiaomi, Huawei, Samsung) may kill exact alarms. **Decided:** detect the condition and offer a one-tap route to the battery-optimisation exemption setting. Show it once, never nag.
3. **Compose Multiplatform Arabic shaping on iOS** — not exercised in slice 1, but slice 2 depends on it entirely. **Validate before committing slice 2's design**, since a failure there could force a native reader.
4. **Adhan audio is a development placeholder** — CC0 and safe to ship, but chosen for its licence rather than for its recitation. See section 14.
5. **Name availability** — "Taqwa" is unverified on both stores and for trademark.
