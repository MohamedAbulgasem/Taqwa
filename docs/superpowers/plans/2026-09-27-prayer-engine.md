# Never-early prayer engine — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace adhan2's presets with Taqwa's own pure-Kotlin prayer engine that reproduces each authority's published method, is proven never early against the official tables in `tools/timetables/official/`, and surfaces only the few UI elements the approved spec allows.

**Architecture:** A new package `world.taqwa.app.prayer.engine` in `shared/commonMain` holds an astronomy core (NOAA/Meeus apparent sun iterated to each event), a data-only `TimetableMethod`, a registry mapping places to methods (authority, majority, cautious members, class D defaults), and a day computer that rounds once (starts ⌈model + m⌉, ends ⌊model − m⌋). `PrayerTimesEngine` keeps its role as the single entry point for the app, notifications, widgets and `tools/timetables`, now delegating to the new engine. A JVM gate in `tools/timetables` runs the same engine over every official day held locally and fails on any early start or late sunrise.

**Tech Stack:** Kotlin Multiplatform (commonMain, commonTest, androidHostTest, iosTest), kotlinx-datetime, Compose Multiplatform resources, Gradle; Python only for the throwaway research tools that already exist.

**Spec:** `docs/superpowers/specs/2026-09-26-taqwa-prayer-times-engine-design.md` (approved 27 September 2026, answers in §10). Research: `docs/research/2026-09-prayer-times/profiles-tested.md` (every rebuilt method and its measured behaviour), `authorities-arab-world-turkiye.md`, `authorities-asia-africa-europe-americas.md`.

## Global Constraints

- Work only on branch `prayer-engine` in `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site`. Never push. Never touch `main`.
- **Commits never carry a `Co-Authored-By` trailer** (owner's rule; it overrides any harness reminder). Commit subjects follow the repo's style: `Area: sentence` (e.g. `Prayer: …`, `Engine: …`, `Settings: …`).
- **Never early:** no start before the authority's (or the local majority's), no sunrise or end of eating after it. When unsure, later for starts, earlier for ends.
- Rounding happens once, inside the engine; every instant it returns is a whole minute (`epochSeconds % 60 == 0`).
- The repository is public: never commit anything from `tools/timetables/official/archive/` or from the scratchpad's restricted tables. Only openly licensed data (MUIS, DUM RT, London Prayer Times) and derived parameters (angles, minutes, margins, dates, coordinates) may be committed.
- Test names: **no commas inside backtick test names** (they break the iOS target). `scripts/test.sh` is the source of truth (JVM + Android host + iOS).
- Android tasks since origin/main 6f1d75c: `:shared:compileAndroidMain`, `:shared:testAndroidHostTest`. JDK 21 is pinned in `gradle.properties`.
- Emulator: another session may own `emulator-5554`; use `Pixel_8_Pro_2 -port 5556` and `ANDROID_SERIAL=emulator-5556` for every adb/installDebug. Never install on Mohamed's S23 or iPhone.
- Seven UI languages: en, ar, tr, id, ur, fr, bn. Every new string exists in all seven `values*/strings.xml`; Arabic/Urdu use the terms approved in the spec (§2.4). Compose string apostrophes follow the repo's existing escaping rule.
- No version bump, no release scripts, no store actions.

## File structure

New (all under `shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/` unless noted):

| File | Responsibility |
|---|---|
| `astro/SolarMath.kt` | Julian day, NOAA apparent sun (declination, equation of time), hour-angle helpers. Pure functions. |
| `astro/SunClock.kt` | Event times for one place and civil date under a `SunModel` (EXACT, DAILY_0H_UT, CLASSIC_NOON): transit, altitude crossings, Asr by shadow factor with an `AsrModel`, optional declination bias. Returns UTC epoch seconds as `Double`, or null when the sun never reaches the altitude. |
| `method/TimetableMethod.kt` | Data-only description of a published method (angles, Isha rule, horizon, dips, sun/Asr models, the authority's minutes, our margins in seconds, day rules, Ramadan rules, fixed times, end-of-eating and imsak kinds, high-latitude rule, reference points). |
| `method/Harmonics.kt` | Small helper for day-of-year harmonic series (Umm al-Qura declination bias). |
| `day/PrayerDay.kt` | The engine's output for one date: rounded instants for fajr, sunrise, dhuhr, asr, asrOther, maghrib, isha, sunset, endOfEating, imsak, earliestStart, ends; flags; provenance. |
| `day/DayComputer.kt` | Raw events for a method, point and date → apply the authority's minutes, day rules (MUIS neighbours, UQ lag dates), Ramadan rules, fixed times, high-latitude handling → round with margins → `PrayerDay`. |
| `day/HighLatitude.kt` | Real events when they exist; a missing Fajr/Isha from the MWL 1986 proportion from 45°, ramped; polar days from the nearest latitude; authority rules (DUM RT summer window, DUM RF, Diyanet Europe). |
| `day/Cautious.kt` | Combine member days: latest start, earliest sunrise and end of eating, Maghrib cap at the most-followed member, `earliestStart` kept. |
| `day/Invariants.kt` | Check and repair the order of a day on the safe side; sets a flag. |
| `registry/Authority.kt` | `RegistryEntry` (id, short-name key, class, method or members, scope, Asr school, Shia-region flag, about template, provenance), `EntryClass` (A, B, C, D_AUTHORITY, D_NONE). |
| `registry/authorities/*.kt` | One file per authority group, each exporting its `TimetableMethod`s and entries: `UmmAlQura.kt`, `Diyanet.kt`, `Muis.kt`, `Jakim.kt`, `Kemenag.kt`, `Egypt.kt`, `Gulf.kt` (IACAD, Qatar, Kuwait, Bahrain, Oman), `Levant.kt` (Jordan, PA, Lebanon, Syria, Iraq, Yemen), `Maghreb.kt` (Libya, Tunisia, Algeria, Morocco, Mauritania, Sudan), `SouthAfrica.kt`, `Russia.kt` (DUM RT, DUM RF, Dagestan), `Balkans.kt` (IZ BiH, Kosovo, Albania), `Europe.kt` (LUPT, EMB, Diyanet Europe, conventions for C places), `Americas.kt` (ISNA/FCNA, Chicago, Canada members), `SouthAsia.kt` (Karachi, IFB), `CentralAsia.kt`, `Generic.kt` (MWL and the other "Other methods", the class D safe default). |
| `registry/Registry.kt` | `Registry.resolve(place): Resolution` — country, region overrides (bounding boxes / radii), units by nearest reference point, class, Asr default school, Shia-region flag, cautious members. Deterministic, data-only. |
| `registry/Regions.kt` | Named regions: London (M25), Chicago metro, Toronto/GTA, Montreal, Ottawa, Saudi Eastern Province, Iran provinces, eastern/southern Libya, northern Yemen, Gilgit-Baltistan, Kurram, Hazarajat. |
| `Engine.kt` | `PrayerEngine.dayTimes(place: Place, date: LocalDate, settings: EngineSettings): EngineDay` (Task 8) with a bounded thread-safe LRU cache. |
| `engine/registry/data/*.kt` | Derived data as Kotlin source only (the engine must compile without Compose resources): Umm al-Qura Ramadan and lag dates, JAKIM zone points, Kemenag unit horizons, the LUPT day-of-year angle curve, Diyanet district points, Jamiat town points, Habous city parameters, Algerian wilaya seats. |
| `shared/src/commonTest/kotlin/world/taqwa/app/prayer/engine/*Test.kt` | Engine tests; golden vector test. |
| `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/gate/NeverEarlyGateTest.kt` | The gate over the local archive (skips cleanly when the archive is absent). |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/*` | Manifest reader, official-table reader, fitter, report and stamp writer. |

Modified: `prayer/PrayerTimesEngine.kt`, `domain/Prayer.kt` (DayPrayerTimes), `prayer/TimelineBuilder.kt`, `prayer/NightThirds.kt` (unchanged maths, new inputs), `domain/PrayerSettings.kt`, `settings/SettingsKeys.kt`, `settings/SettingsRepository.kt`, `feature/today/*`, `feature/settings/PrayerTimesSettingsScreen.kt`, `SettingsRoutes.kt`, every `values*/strings.xml`, `tools/timetables/build.gradle.kts`, every caller in the code map (`2026-09-27-prayer-engine-codemap.md`).

---

## Conventions every engine task uses

- Times inside the engine are **UTC epoch seconds as `Double`** until rounding; `PrayerDay` holds `kotlinx.datetime.Instant`s that are whole minutes.
- **Offsets** are signed seconds applied before rounding: a start is `ceil((raw + offset) / 60) * 60`, an end (sunrise, end of eating, imsak, ends) is `floor((raw + offset) / 60) * 60`. The total offset of an event is `authorityMinutes * 60 + marginSeconds`. A negative margin on a start reproduces an authority that rounds to the nearest minute (Diyanet: starts −20 s, sunrise +20 s).
- **Fitting a margin** on official data (minute-aligned official time `O`, raw model `r`, both epoch seconds): for a start, `margin = max(O − 60 − r − authorityMinutes·60) + 1 + safety`; for an end, `margin = min(O + 60 − r − authorityMinutes·60) − 1 − safety`, with `safety = 5 s` unless the task says otherwise. Minutes late for a start = `(shown − O) / 60`; for an end, minutes early = `(O − shown) / 60`.
- The civil date `D` is the place's local date; the zone offset used is the offset at local noon of `D`.
- `GeoPoint(lat, lon)` in degrees, east and north positive.
- Engine code lives in `shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/`; its tests in `shared/src/commonTest/kotlin/world/taqwa/app/prayer/engine/`. `shared` has no JVM target: run one test class with `./gradlew :shared:testAndroidHostTest --tests <fully.qualified.Class>` and everything with `scripts/test.sh` (Android host + iOS).
- **No Compose, no `Res`, no platform code in the engine**: `tools/timetables` compiles these files on a plain JVM. Registry data (dates, points, parameters) is Kotlin source.
- Use the existing `world.taqwa.app.domain.Prayer` enum (in `widgetcore`) for prayer keys and `kotlin.time.Instant` for instants (the repo uses stdlib `Instant` with kotlinx-datetime 0.8).
- Read `docs/superpowers/plans/2026-09-27-prayer-engine-codemap.md` before touching existing code.

---

## Task 1: Astronomy core

**Files:**
- Create: `engine/astro/SolarMath.kt`, `engine/astro/SunClock.kt`
- Test: `engine/astro/SunClockTest.kt`

**Interfaces:**
- Produces: `SolarMath.sun(jd): SolarMath.Sun(declinationDeg, equationOfTimeMinutes)`, `SolarMath.julianDay(epochSeconds: Double)`, `enum class SunModel { EXACT, DAILY_0H_UT, CLASSIC_NOON }`, `enum class AsrModel { EXACT_MOMENT, NOON_SHADOW, UTC12_ONE_SHOT, DAILY_0H_UT }`, `class SunClock(lat: Double, lon: Double, date: LocalDate, utcOffsetSeconds: Int, model: SunModel, phaseYear: Int? = null)` with `fun transit(): Double`, `fun altitudeTime(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Double?`, `fun asr(shadowFactor: Double, asrModel: AsrModel, declinationBiasDeg: Double = 0.0): Double?`. All return UTC epoch seconds; null when the sun never reaches the altitude.

- [ ] **Step 1: Write the failing test** (reference values from the NOAA algorithm to the second; tolerance 3 s)

```kotlin
package world.taqwa.app.prayer.engine.astro

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunClockTest {
    private fun local(date: LocalDate, hms: String, offsetHours: Int): Double {
        val (h, m, s) = hms.split(":").map { it.toInt() }
        val ldt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, h, m, s)
        return ldt.toInstant(TimeZone.UTC).epochSeconds.toDouble() - offsetHours * 3600
    }

    private fun near(expected: Double, actual: Double?, label: String) {
        assertTrue(actual != null && abs(actual - expected) <= 3.0, "$label: expected $expected, got $actual")
    }

    private data class Ref(val lat: Double, val lon: Double, val tz: Int, val date: LocalDate, val transit: String,
        val dawn18: String, val sunrise: String, val asr1: String, val asr2: String, val sunset: String, val dusk17: String)

    private val refs = listOf(
        Ref(51.5074, -0.1278, 1, LocalDate(2026, 10, 1), "12:50:11", "5:09:04", "7:01:03", "15:56:13", "16:43:21", "18:38:22", "20:23:14"),
        Ref(21.426666, 39.831666, 3, LocalDate(2026, 9, 20), "12:14:10", "4:54:48", "6:08:55", "15:39:07", "16:37:07", "18:19:06", "19:28:47"),
        Ref(3.139, 101.687, 8, LocalDate(2026, 2, 25), "13:26:20", "6:15:26", "7:25:01", "16:43:15", "17:45:28", "19:27:41", "20:33:11"),
        Ref(43.6532, -79.3832, -4, LocalDate(2026, 10, 1), "13:07:08", "5:39:33", "7:15:11", "16:22:26", "17:10:53", "18:58:22", "20:28:07"),
        Ref(32.1167, 20.0667, 2, LocalDate(2026, 9, 27), "12:30:45", "5:09:33", "6:31:00", "15:55:44", "16:47:53", "18:30:02", "19:46:34"),
    )

    @Test
    fun `exact model matches the NOAA reference events`() {
        for (r in refs) {
            val c = SunClock(r.lat, r.lon, r.date, r.tz * 3600, SunModel.EXACT)
            near(local(r.date, r.transit, r.tz), c.transit(), "transit ${r.date}")
            near(local(r.date, r.dawn18, r.tz), c.altitudeTime(-18.0, morning = true), "dawn ${r.date}")
            near(local(r.date, r.sunrise, r.tz), c.altitudeTime(-0.8333, morning = true), "sunrise ${r.date}")
            near(local(r.date, r.asr1, r.tz), c.asr(1.0, AsrModel.EXACT_MOMENT), "asr1 ${r.date}")
            near(local(r.date, r.asr2, r.tz), c.asr(2.0, AsrModel.EXACT_MOMENT), "asr2 ${r.date}")
            near(local(r.date, r.sunset, r.tz), c.altitudeTime(-0.8333, morning = false), "sunset ${r.date}")
            near(local(r.date, r.dusk17, r.tz), c.altitudeTime(-17.0, morning = false), "dusk ${r.date}")
        }
    }

    @Test
    fun `an altitude the sun never reaches returns null`() {
        val c = SunClock(51.5074, -0.1278, LocalDate(2026, 6, 21), 3600, SunModel.EXACT)
        assertNull(c.altitudeTime(-18.0, morning = false))
    }

    @Test
    fun `daily model differs from exact by less than a minute at mid latitudes`() {
        val d = LocalDate(2026, 9, 26)
        val exact = SunClock(41.012, 28.974, d, 3 * 3600, SunModel.EXACT)
        val daily = SunClock(41.012, 28.974, d, 3 * 3600, SunModel.DAILY_0H_UT)
        val a = exact.altitudeTime(-0.8333, morning = false)!!
        val b = daily.altitudeTime(-0.8333, morning = false)!!
        assertTrue(abs(a - b) < 60.0, "sunset exact $a daily $b")
    }

    @Test
    fun `transit stays on the civil date for zones beyond twelve hours`() {
        val d = LocalDate(2026, 9, 26)
        val c = SunClock(1.87, -157.4, d, 14 * 3600, SunModel.EXACT)
        val t = c.transit()
        val localDayStart = LocalDateTime(2026, 9, 26, 0, 0).toInstant(TimeZone.UTC).epochSeconds - 14 * 3600
        assertTrue(t >= localDayStart && t < localDayStart + 86_400, "transit $t not on 26 Sep local")
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :shared:testAndroidHostTest --tests world.taqwa.app.prayer.engine.astro.SunClockTest`: compilation error, `SunClock` undefined.

- [ ] **Step 3: Implement `SolarMath.kt`**

```kotlin
package world.taqwa.app.prayer.engine.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * The sun's apparent position by the NOAA / Meeus low-precision formulas, accurate to a few seconds
 * of time for prayer events. Pure; nothing but kotlin.math.
 */
object SolarMath {
    const val SECONDS_PER_DAY = 86_400.0
    private const val J2000 = 2_451_545.0

    data class Sun(val declinationDeg: Double, val equationOfTimeMinutes: Double)

    fun julianDay(epochSeconds: Double): Double = epochSeconds / SECONDS_PER_DAY + 2_440_587.5

    fun sun(jd: Double): Sun {
        val t = (jd - J2000) / 36_525.0
        val l0 = normalizeDegrees(280.46646 + t * (36_000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = rad(m)
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val declination = deg(asin(sin(rad(eps)) * sin(rad(lambda))))
        val y = tan(rad(eps / 2)).pow(2)
        val l0r = rad(l0)
        val eot = 4 * deg(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr),
        )
        return Sun(declination, eot)
    }

    fun rad(d: Double) = d * PI / 180.0
    fun deg(r: Double) = r * 180.0 / PI
    fun normalizeDegrees(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}
```

- [ ] **Step 4: Implement `SunClock.kt`** following these rules exactly:
  1. `dayStartUtc = date.toEpochDays() * 86_400.0` (00:00 UTC of the calendar date). Solar noon for a given `Sun` is `dayStartUtc + (12.0 − lon / 15.0) · 3600 − eot · 60`. If that instant plus `utcOffsetSeconds` falls on another local date than `date`, add or subtract 86 400 s until it does.
  2. `EXACT`: iterate 6 times: take the sun at the current estimate `t` (start at local noon), recompute noon with that sun's EoT, the hour angle `H` from that sun's declination, `t = noon ∓ H·240 s` (240 s per degree). Transit iterates the same way with `H = 0`.
  3. `DAILY_0H_UT`: one sun, at `dayStartUtc` — or, when `phaseYear` is set, at 00:00 UTC of the same month and day in `phaseYear` (29 February maps to 28 February); no iteration.
  4. `CLASSIC_NOON`: transit as EXACT; every other event uses the declination at the transit moment and `t = transit ∓ H·240`.
  5. Hour angle: `cosH = (sin a − sin φ sin δ) / (cos φ cos δ)`; if `|cosH| > 1` return null. `declinationBiasDeg` is added to δ for that event only.
  6. Asr altitude `a = atan(1 / (f + tan|φ − δs|))`, where δs (shadow) and δh (hour angle) are: `EXACT_MOMENT` both at the Asr moment (iterate); `NOON_SHADOW` δs at transit, δh at the Asr moment; `UTC12_ONE_SHOT` both from one sun at 12:00 UTC of the date, no iteration; `DAILY_0H_UT` both from the daily sun. The bias applies to both.

- [ ] **Step 5: Run the test to verify it passes.** Expected: 4 tests pass.

- [ ] **Step 6: Commit** — `Engine: the sun's position and event times, reproducing the NOAA reference to the second`

---

## Task 2: Methods as data and the day computer

**Files:**
- Create: `engine/method/TimetableMethod.kt`, `engine/method/Harmonics.kt`, `engine/day/PrayerDay.kt`, `engine/day/DayComputer.kt`, `engine/day/RamadanCalendar.kt`
- Create: `engine/registry/data/UmmAlQuraDates.kt` — two Kotlin lists of ISO date strings parsed lazily: Umm al-Qura Ramadan dates (from scratchpad `round/data-umm-al-qura/ramadan-dates.txt`) and lag dates (from `round/data-umm-al-qura/lag-dates-model.txt`, comment lines dropped). The scratchpad is `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/`.
- Test: `engine/day/DayComputerTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces (exact names; later tasks rely on them):

```kotlin
package world.taqwa.app.prayer.engine.method

data class GeoPoint(val lat: Double, val lon: Double)

enum class AsrSchool { STANDARD, HANAFI; val shadowFactor get() = if (this == HANAFI) 2.0 else 1.0 }

sealed interface IshaRule {
    data class Angle(val degrees: Double) : IshaRule
    /** Minutes after the authority's Maghrib (before rounding); `ramadanMinutes` on Ramadan dates. */
    data class AfterMaghrib(val minutes: Int, val ramadanMinutes: Int = minutes) : IshaRule
}

/** Signed values per event; `authorityMinutes` in minutes, `margins` in seconds (see Conventions). */
data class EventOffsets(
    val fajr: Int = 0, val sunrise: Int = 0, val dhuhr: Int = 0, val asr: Int = 0,
    val maghrib: Int = 0, val isha: Int = 0,
)

enum class DayRule { SAME_DAY, NEIGHBOURS_MUIS, LAG_DATES_UQ }

sealed interface EndOfEating {
    data object SameAsFajrDawn : EndOfEating
    data class DawnAngle(val degrees: Double) : EndOfEating
    data class MinutesBeforeFajr(val minutes: Int) : EndOfEating
}

data class RamadanRule(val maghribExtraSeconds: Int = 0)

sealed interface HighLatRule {
    data object Standard : HighLatRule
    data class DumRtSummer(
        val fromMonth: Int = 5, val fromDay: Int = 6, val toMonth: Int = 8, val toDay: Int = 8,
        val sahurBeforeSunriseMinutes: Int = 121, val ishaAfterMaghribMinutes: Int = 90,
    ) : HighLatRule
    data class NightFraction(val fajrFraction: Double, val ishaFraction: Double) : HighLatRule
    data class Legacy(val kind: String) : HighLatRule   // "middle", "seventh", "angle": Other methods only
}

data class Harmonics(val c0: Double, val terms: List<Pair<Double, Double>>) {
    /** c0 + Σ (a_k cos kt + b_k sin kt), t = 2π (dayOfYear − 0.5) / daysInYear. */
    fun at(dayOfYear: Int, daysInYear: Int): Double
}

data class TimetableMethod(
    val id: String,
    val fajrAngle: Double,
    val isha: IshaRule,
    val sunModel: SunModel = SunModel.EXACT,
    val phaseYear: Int? = null,
    val horizonDeg: Double = -0.8333,
    val twilightDipDeg: Double = 0.0,
    val asrModel: AsrModel = AsrModel.EXACT_MOMENT,
    val fajrDeclinationBias: Harmonics? = null,
    val asrBiasFactor: Double = 0.0,
    val authorityMinutes: EventOffsets = EventOffsets(),
    val margins: EventOffsets = EventOffsets(),
    val dayRule: DayRule = DayRule.SAME_DAY,
    val ramadan: RamadanRule? = null,
    val fixedDhuhrLocalMinutes: Int? = null,
    val endOfEating: EndOfEating = EndOfEating.SameAsFajrDawn,
    val endOfEatingMarginSeconds: Int = 0,
    val imsakMinutesBeforeFajr: Int? = null,
    val fajrAfterDawnMinutes: Int = 0,
    val highLatitude: HighLatRule = HighLatRule.Standard,
    val startPoints: List<GeoPoint> = emptyList(),
    val fixedPoint: GeoPoint? = null,
)
```

```kotlin
package world.taqwa.app.prayer.engine.day

// Prayer = world.taqwa.app.domain.Prayer (widgetcore); Instant = kotlin.time.Instant

data class PrayerDay(
    val date: LocalDate,
    val fajr: Instant, val sunrise: Instant, val dhuhr: Instant, val asr: Instant, val asrOther: Instant,
    val maghrib: Instant, val isha: Instant, val sunset: Instant,
    val endOfEating: Instant, val imsak: Instant?,
    val earliestStart: Map<Prayer, Instant> = emptyMap(),
    val ends: Map<Prayer, Instant> = emptyMap(),
    val setByRule: Set<Prayer> = emptySet(),
    val polar: Boolean = false,
    val repaired: Boolean = false,
    val methodId: String,
)

fun interface RamadanCalendar { fun isRamadan(date: LocalDate): Boolean }

object DayComputer {
    fun compute(method: TimetableMethod, point: GeoPoint, date: LocalDate, zone: TimeZone,
        school: AsrSchool, ramadan: RamadanCalendar, lagDates: Set<LocalDate> = emptySet()): PrayerDay
}
```

- [ ] **Step 1: Write failing tests.** At least:
  1. **Umm al-Qura structure** (method: 18.5°, `AfterMaghrib(90, 120)`, bias harmonics from `profiles-tested.md` §data-umm-al-qura, `asrBiasFactor = 0.4`, minutes 0, margins starts +8, sunrise −4 — refined in Task 8): Makkah (21.426666, 39.831666) on 2026-09-20 and 2027-02-15 → every instant minute-aligned; `isha − maghrib == 90 min` on 2026-09-20 and `== 120 min` on 2027-02-15 (a Ramadan date in the resource); order `fajr < sunrise < dhuhr < asr < maghrib < isha`; each start is at or after its raw `SunClock` event and sunrise at or before.
  2. **MUIS rule B** (open data, rows may be embedded as literals in the test): Singapore fixed point (1.28967, 103.85007), 20°/18°, `CLASSIC_NOON`, `NEIGHBOURS_MUIS`, margins Fajr +21, sunrise +58, Dhuhr +79, Asr +31, Maghrib +18, Isha +20, `RamadanRule(60)`: 2026-01-15 and 2026-07-15 against `tools/timetables/official/open/SG-MUIS/off-muis-2026.txt` — every start ≥ official, sunrise ≤ official, each at most 2 min late.
  3. **Diyanet**: `DAILY_0H_UT`, 18°/`Angle(17.0)`, minutes sunrise −7, Dhuhr +5, Asr +4, Maghrib +7, İstanbul (41.012, 28.974): margins of ∓30 s round each of its own raw times (from `SunClock`) to the nearest minute, as Diyanet prints them; margins starts −20 s, sunrise +20 s are never before that minute and at most one after (no printed row in the tree, ruling R69).
  4. **Kosovo rule**: `fajrAfterDawnMinutes = 20` → `fajr − endOfEating ≥ 20 min`.
  5. **Fixed Dhuhr**: `fixedDhuhrLocalMinutes = 720` → Dhuhr is 12:00 local when transit is earlier and the transit when later.
  6. **Jamiatul Ulama**: `EndOfEating.MinutesBeforeFajr(5)` → `fajr − endOfEating == 5 min`.
  7. **Dip**: `twilightDipDeg = 1.0` makes Fajr earlier and Isha later than with 0.
- [ ] **Step 2: Run, verify they fail.**
- [ ] **Step 3: Implement.** Per event: raw instant(s) from `SunClock` — Fajr at `−(fajrAngle + twilightDipDeg)` with the bias `fajrDeclinationBias.at(doy)` if set; sunrise and sunset at `horizonDeg`; Dhuhr = transit; Asr with the school factor and the method's Asr model, bias × `asrBiasFactor`; `asrOther` the same with the other school; Isha by angle at `−(degrees + twilightDipDeg)` or `rawMaghrib + authorityMinutes.maghrib·60 + minutes·60`. Add `authorityMinutes·60`. Day rules: `NEIGHBOURS_MUIS` — starts take the later of the D−1 and D+1 raw times (shifted by ∓86 400 s), sunrise the earlier; `LAG_DATES_UQ` — on a listed date starts take the later of D and D−1 (+86 400 s), sunrise the earlier. Ramadan: `ramadan.maghribExtraSeconds` added to Maghrib on Ramadan dates; `IshaRule.AfterMaghrib.ramadanMinutes` likewise. `startPoints`: starts are the latest over the points; sunrise, sunset and end of eating stay at the given point. `fixedPoint` replaces the point entirely. Fixed Dhuhr: `max(computed, local 12:00)`. Round with the margins. End of eating: `SameAsFajrDawn` = floor(raw dawn + authority Fajr minutes·60 + `endOfEatingMarginSeconds`); `DawnAngle(d)` = floor(raw at −d + margin); `MinutesBeforeFajr(n)` = shown Fajr − n min; always `≤ fajr`. Kosovo: shown Fajr = ceil(raw dawn + `fajrAfterDawnMinutes`·60 + margin). `imsak` = shown Fajr − n min. `sunset` = floor(raw sunset). When a Fajr or Isha altitude returns null, delegate to `HighLatitude` (Task 3); until then throw `NotImplementedError` in that branch.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** — `Engine: methods as data, and a day computed and rounded once`

---

## Task 3: High latitudes and polar days

**Files:**
- Create: `engine/day/HighLatitude.kt`; Modify: `engine/day/DayComputer.kt`
- Test: `engine/day/HighLatitudeTest.kt`

**Rules** (spec §3.8):
1. Real times whenever the sign occurs, at any latitude.
2. `Standard`, missing Fajr or Isha: the MWL Fiqh Council 1986 proportion from 45°. At latitude 45° (same hemisphere, same longitude, same date) compute `fIsha = (isha45 − sunset45) / night45` and `fFajr = (sunrise45 − fajr45) / night45`, where `night45` is sunset to next sunrise at 45°. Locally Isha = sunset + fIsha × (tonight's sunset → tomorrow's sunrise), Fajr = sunrise − fFajr × (last night's sunset → today's sunrise). **Ramp**: an estimated Isha is never earlier, as a fraction of the night, than the latest real Isha fraction in the previous 7 days, and an estimated Fajr never later than the earliest real Fajr fraction in the previous 7 days. Mark the key in `setByRule`.
3. `DumRtSummer`: inside the window (6 May – 8 Aug inclusive) end of eating = sunrise − 121 min and the Fajr shown equals it; Isha = Maghrib + 90 min; mark FAJR and ISHA.
4. `NightFraction`: only on days the real sign is missing, Fajr = sunrise − f·night, Isha = sunset + f·night.
5. Polar day or night (no sunrise or no sunset): compute the whole day at the nearest latitude towards the equator (0.5° steps, same longitude) where the sun both rises and sets; `polar = true`; every key in `setByRule`.
6. `Legacy(kind)` (Other methods chosen by the user): `middle` = half the night, `seventh` = one-seventh, `angle` = angle/60 of the night; only when the sign is missing.

- [ ] Tests: London 2026-06-21 with 18°/`Angle(17.0)` → Fajr and Isha set by rule, `isha > maghrib`, `fajr < sunrise`; London 31 May and 1 June 2026 → Isha on 1 June is not earlier as a fraction of the night than on 31 May; Murmansk (68.97, 33.07) 2026-06-21 → `polar`, all keys set by rule, order holds; Kazan (55.79, 49.12) 2026-06-20 with `DumRtSummer` → end of eating = sunrise − 121 min and Isha = Maghrib + 90 min (compare with `tools/timetables/official/open/RU-DUMRT/off_kzn.txt` 2026-06-20: `00:57 02:58 11:46 17:34 20:33 22:03`, allowing ±1 min).
- [ ] Commit — `Engine: real times at any latitude, an estimate only when the sign is missing`

---

## Task 4: Cautious times, ends and invariants

**Files:**
- Create: `engine/day/Cautious.kt`, `engine/day/Invariants.kt`
- Test: `engine/day/CautiousTest.kt`, `engine/day/InvariantsTest.kt`

**Rules:**
- `Cautious.combine(members: List<PrayerDay>, mostFollowed: Int, sunriseToMaghribMinutes: List<Int>): PrayerDay` — each start is the latest member's; sunrise, sunset and end of eating the earliest; Maghrib is the latest member's when all member Maghribs are within 2 minutes of each other (no precaution is being stacked), otherwise the most-followed member's (the cap, spec §3.6); `earliestStart[k]` is the earliest member start for each prayer; `methodId = "cautious:" + member ids joined by "+"`; `asrOther` the latest other-school Asr.
- `Ends.of(day, nextDay: PrayerDay?)`: FAJR → sunrise; DHUHR → the Standard Asr (the day's `asr` or `asrOther`, whichever is the Standard one — pass the school); ASR → sunset; MAGHRIB → min(earliest Isha in play, 17° dusk from `SunClock`); ISHA → the next day's end of eating (omit when `nextDay` is null).
- `Invariants.repair(day)`: enforce `endOfEating ≤ fajr < sunrise < dhuhr < asr < maghrib < isha` and `maghrib ≥ sunset`; a violation moves starts later or ends earlier by whole minutes and sets `repaired = true`. Never throws.

- [ ] Tests: the Toronto example (three synthetic members with invented rows for one day, the first printing the Hanafi Asr, their Maghribs within 2 min and their Ishas about 20 min apart; most-followed index 0) → each start the latest member's, sunrise the earliest, Maghrib the latest (members within 2 min), `earliestStart[ISHA]` the earliest member's Isha; a second case with a member Maghrib 5 min later than the others → Maghrib is the most-followed member's; invariant repair of a day with `isha < maghrib` comes out ordered and flagged.
- [ ] Commit — `Engine: cautious times over timetables in use, and a day whose order always holds`

---

## Task 5: Registry skeleton, places and every authority's first method

**Files:**
- Create: `engine/registry/Authority.kt`, `engine/registry/Registry.kt`, `engine/registry/Regions.kt`, `engine/registry/Units.kt`
- Create: `engine/registry/authorities/{UmmAlQura,Diyanet,Muis,Jakim,Kemenag,Egypt,Gulf,Levant,Maghreb,SouthAfrica,Russia,Balkans,Europe,Americas,SouthAsia,CentralAsia,Generic}.kt`
- Create: `engine/registry/data/*.kt` — derived data as Kotlin source (unit points, horizons, per-unit margins, wilaya seats); never official times
- Test: `engine/registry/RegistryTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:

```kotlin
package world.taqwa.app.prayer.engine.registry

enum class EntryClass { A, B, C, D_AUTHORITY, D_NONE }
enum class AboutTemplate { AUTHORITY_CHECKED, AUTHORITY_UNCHECKED, CAUTIOUS, CALCULATED }

data class Place(val lat: Double, val lon: Double, val zoneId: String, val countryCode: String)

data class Member(val id: String, val nameKey: String, val method: TimetableMethod, val shareRank: Int)

data class RegistryEntry(
    val id: String,                     // stable string key, e.g. "sa.ummalqura", "tr.diyanet", "gb.london.lupt"
    val shortNameKey: String,           // string resource name, e.g. "authority_diyanet"
    val entryClass: EntryClass,
    val about: AboutTemplate,
    val method: TimetableMethod? = null,          // null when cautious
    val members: List<Member> = emptyList(),      // cautious members, most-followed first
    val school: AsrSchool,
    val schoolKnown: Boolean,                      // false → the later school leads (spec §3.7)
    val nearby: List<String> = emptyList(),        // ids of named timetables listed in Settings › Timetable
    val scope: Scope,                              // UNIT, CITY, COUNTRY, GLOBAL
)
enum class Scope { UNIT, CITY, COUNTRY, GLOBAL }

data class Resolution(
    val entry: RegistryEntry,
    val point: GeoPoint,            // where the method is evaluated (unit reference point or the user's point)
    val unitName: String?,          // "WLY01", "İstanbul", "Casablanca"
    val shiaRegion: Boolean,        // Sunni-times card
    val saudi: Boolean,             // Saudi-only Fajr switch
    val measured: Boolean,          // false: no "at most" figure (unit not in the proof)
)

object Registry {
    fun resolve(place: Place): Resolution
    fun byId(id: String): RegistryEntry?
    val otherMethods: List<RegistryEntry>     // MWL, ISNA, Egyptian, Umm al-Qura, Karachi, Moonsighting, Diyanet, Kuwait, Qatar, Dubai, Singapore, Muhammadiyah …
}
```

**Resolution rules** (spec §3.5–§3.7, §6):
1. Country from `Place.countryCode` (ISO 3166-1 alpha-2).
2. Region overrides first (`Regions.kt`, circles or boxes): London inside the M25 → `gb.london.lupt`; Chicago metro (Cook, DuPage, Lake, Will, Kane, NW Indiana; a box 41.2–42.5 N, 88.6–87.2 W) → `us.chicago` (18°/15°, Hanafi); Toronto/GTA (43.4–44.1 N, 80.0–78.9 W) → `ca.toronto` cautious; Montreal (45.3–45.8 N, 74.1–73.3 W) and Ottawa (45.2–45.6 N, 76.0–75.4 W) → ISNA majority; Saudi Eastern Province (lon > 48.0 within SA) and the other Shia regions in spec §2.1 → `shiaRegion = true`; Iran's Sistan-Baluchestan, Golestan, Khorasan (Razavi, North, South), Kurdistan (Shafi'i), Hormozgan → school per spec §3.7; eastern and southern Libya (lon > 18.5 or lat < 29.5) → Libya entry with `EndOfEating.DawnAngle(19.5)`.
3. Units by nearest reference point within the unit's radius (`Units.kt`): JAKIM's 13 fitted zones (scratchpad `round/data-wide-band/jakim-zone-offsets.csv`), Kemenag's 13 kab/kota with their measured horizon (1° lowland: Jakarta, Banda Aceh, Makassar, Denpasar, Jayapura, Malang; 2° highland: Bandung, Jayawijaya, Batu, Bukittinggi, Bogor, Garut, Wonosobo), Diyanet's published district points (İstanbul 41.012/28.974, Ankara 39.938/32.848, Tekirdağ 40.973/27.511, Adana 36.987/35.326, Hatay 36.2/36.153, Karaman 37.181/33.214) and the other analysed districts at the app's coordinates, Habous's cities, Jamiatul Ulama's towns, Qatar's zones (Doha for Al Khor–Al Wakrah), Egypt's ESA points where fitted (Cairo 30.08/31.27), Algeria's three base cities and every wilaya seat. Outside every known unit of an authority: the authority's method at the user's point with the unit's safe-edge margin (JAKIM +90 s starts / −60 s sunrise; Diyanet +60 s / −60 s; Kemenag highland horizon −2°), `measured = false`.
4. Class and method per country from spec §6.1–§6.2. Start with the research parameters (profiles-tested.md and the two authorities files); Task 8 refits every margin on this core. The class D table in spec §6.2 is the source for D entries; the safe default (case c) is `Generic.safeDefault(region)`.
5. `Registry.otherMethods` keeps every method the old picker offered except Tehran (`method_*` strings), plus Muhammadiyah, London Unified, Diyanet Europe and the cautious members that have names.

- [ ] **Step 1: Tests:** resolution of the spec's example places — Makkah → `sa.ummalqura` A; İstanbul → Diyanet with unit "İstanbul" and point 41.012/28.974; Kuala Lumpur → JAKIM WLY01 (two start points); Jakarta → Kemenag 1° horizon; Bogor → 2°; a Kemenag place far from the 13 units → 2° and `measured = false`; London (51.5074, −0.1278) → `gb.london.lupt`; Manchester → UK cautious members; Toronto → `ca.toronto` cautious; Ottawa → ISNA; Chicago → Hanafi 18°/15°; New York → ISNA 15°/15° Standard; Karachi → Karachi 18°/18° Hanafi; Zahedan → D none, Hanafi, `shiaRegion = true`; Sanandaj (Kurdistan) → Standard; Tehran → Hanafi (school not known → the later one), `shiaRegion = true`; Benghazi → Libya with `DawnAngle(19.5)`; Tripoli → Libya with `SameAsFajrDawn`; Dammam → `shiaRegion = true`; Casablanca → Habous unit; Kazan → DUM RT; Moscow → DUM RF; Murmansk → the Russia entry with polar handling. Also: every country code in the app's `cities.csv` resolves to some entry (loop over the distinct codes; read the CSV in the test via the same loader the app uses, or a test copy of the code list).
- [ ] **Step 2–4:** run, implement, pass.
- [ ] **Step 5: Commit** — `Engine: the registry, from a place to the timetable it follows`

---

## Task 6: The gate over the official tables

**Files:**
- Modify: `tools/timetables/build.gradle.kts` — compile `shared/src/commonMain/kotlin/world/taqwa/app/prayer/**` by glob (plus the files it already compiles, and `domain/TimelineState.kt` if TimelineBuilder is pulled in); the engine is plain Kotlin, so nothing else is needed.
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/{GateManifest,OfficialTable,Gate,Fitter,Stamps}.kt` and a `gate` Gradle task (JavaExec, like `generate`)
- Create: `tools/timetables/official/gate/*.tsv` — one per authority group, columns: `path` (relative to `tools/timetables/official/`), `entry` (registry id), `lat`, `lon`, `zone` (IANA), `columns` (e.g. `F S D A M I`, `I F S D A M Is` with `I` = imsak/end of eating, `Ah` = Hanafi Asr), `school` (of the Asr column), `split` (`fit` or `test`), `note`.
- Create: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/gate/NeverEarlyGateTest.kt`
- Output (committed): `tools/timetables/official/stamps/<entry>.json` — stats only (places, first/last date, place-days, Ramadan days, early count per event, late histogram, exact share, worst late, engine hash). Never official times.

**Behaviour:**
- The archive root defaults to the checkout's own `tools/timetables/official` (its `archive/` git-ignored) and can be overridden with `-Pofficial=<dir>` / env `TAQWA_OFFICIAL`; where that archive is missing the run stops with a message, except on CI (no archive), which passes with "0 rows checked".
- For each row: resolve the entry by id (not by place), evaluate at the row's point (or through the unit logic when `lat/lon` is empty), compute the day, compare each mapped column: a start earlier than official → **early**; sunrise or end of eating later than official → **late end**; otherwise record minutes late (starts) / early (ends).
- `Gate.report()` prints a table per entry and event: rows, early, late-end, 0/1/2/3+ late, exact share.
- `Fitter.fit(entryId, split = "fit")` prints the never-early margins for each event (Conventions formula) and the resulting holdout table.
- `NeverEarlyGateTest`: for every row with an existing file, 0 early starts and 0 late ends across all entries in class A, B and C, and for D entries with official data; lateness within the entry's limit (A 1, B 2, C 1 after the latest member, D 3) except rows whose entry declares a recorded exception (`lateLimit` override with a reason string).
- Gradle: `./gradlew -p tools/timetables gate` runs the report; the test runs in `jvmTest`/`test`.

- [ ] Tests: a tiny fixture table under `tools/timetables/src/jvmTest/resources/` built from **open** MUIS rows, run through the gate, asserting the report counts; the manifest parser; the "file missing → skipped" path.
- [ ] Commit — `Gate: every official day held locally, checked against the engine`

---

## Task 7: Fit and prove each authority (one subtask per group, parallel-safe)

Each subtask owns exactly: its `engine/registry/authorities/<Group>.kt`, its `tools/timetables/official/gate/<group>.tsv`, its stamps, and a section in `docs/research/2026-09-prayer-times/proof.md` (create the file in 7a; later subtasks append their own section under their own heading). For each authority in the group:
1. Build the gate rows from `tools/timetables/official/MANIFEST.tsv` and the research (profiles-tested.md lists every place, coordinate, split and column layout used). Use the same fit/holdout split as the research unless a better forward-in-time split exists.
2. Run `Fitter.fit` on the fit rows; set the margins (seconds) in the method; run the gate on all rows.
3. If held-out rows are early, widen the margin by the observed excess plus 5 s, re-run, and note it in proof.md (the spec's rule: never tune on the holdout silently).
4. Set the entry's class from the result (A: ≤1 min late everywhere measured; B: ≤2 or an unverified unit/season; D_AUTHORITY otherwise) and write the stamp.
5. Add a commonTest for the authority with at most a few committed data points where the data is open (MUIS, DUM RT, LUPT) or already public in the spec (Diyanet İstanbul 26 Sep 2026).

Subtasks:
- **7a Saudi Arabia, Gulf, Egypt:** Umm al-Qura (bias, Asr factor, lag dates, Ramadan 120), IACAD Dubai (fit over 2026–2029 mapped dates), Dubai Rural, Hatta, Qatar (Doha zone, Maghrib +3 all year, later of calendar and API), Kuwait, Bahrain (east/west points), Oman (DAILY_0H_UT, +5, up), ESA (19.5/17.5, NOON_SHADOW Asr, ESA points, 2025+ data only).
- **7b Türkiye, Balkans, Russia:** Diyanet (DAILY_0H_UT, district points), IZ BiH (Sarajevo + real-dawn floor; southern towns exception recorded), Kosovo (dawn ≈ 19°, Fajr = imsak + 20), Albania (as Diyanet Tirana), DUM RT (Kazan open table; summer rule; Dhuhr 12:00), DUM RF (Moscow), Dagestan.
- **7c Singapore, Malaysia, Indonesia:** MUIS rule B, JAKIM 13 zones (CLASSIC_NOON, WLY01/SGR01 two start points, PHG06 horizon −2.5°, Kelantan Isha 17°), Kemenag (20/18, +2 ihtiyat minutes, horizon 1°/2° per unit, imsak −10), Muhammadiyah (18°) as a named timetable.
- **7d Maghreb and Libya:** MARW base cities (Asr UTC12_ONE_SHOT, all rounded up) and each wilaya seat computed directly with margins fitted against the derived Oran and Tamanrasset tables; Habous (national Fajr/Dhuhr/Asr/Isha margins; per-city sunrise and Maghrib for the 11 fitted cities, a national safe margin elsewhere, recorded exception); INM (18/18, Dhuhr +7, Maghrib +2, monthly Asr table, dips for Tataouine and Tala); Libya (18.5/18.3 national, the 22 cities; east/south end of eating 19.5°; Jalu/Awjila Asr +7.5); Mauritania; Sudan.
- **7e Levant, Iraq, Yemen:** Jordan (horizon −2.0°/−2.25° behaviour), PA, Lebanon, Syria, Iraq, Yemen (Aden points) — normalise the raw captures in `archive/raw/moral-auth-mena/levant` into gate rows first; D_AUTHORITY unless the data proves more.
- **7f South Asia and Central Asia:** Karachi (Banuri, Dawat-e-Islami as a later member where needed), India, IFB Bangladesh, Uzbekistan (15.5°, Maghrib +4, versions), Kazakhstan (QMDB 15°, ±3/±5, AngleBased clamp), Kyrgyzstan, Tajikistan, Turkmenistan, Afghanistan (safe default, Hanafi).
- **7g Europe, Americas, South Africa, Oceania:** LUPT (London open table → day-of-year Fajr/Isha depression curve derived from the 2026 table at the London point, Dhuhr transit + 5, Maghrib sunset + 3, sunrise −3; prove on the Oct 2026 file), UK-outside-London and Ireland members, France/Belgium/Netherlands/Germany/Nordics members from the research tables, Diyanet Europe, EMB; US ISNA 15/15 with the mosque tables as floors, Chicago; Canada (Toronto members IFT, IIT, MAC fitted from their 2026 tables; Montreal/Ottawa ISNA); Jamiatul Ulama (DAILY_0H_UT phase 2026, towns, Suhoor = Fajr − 5), Cape Town members; Australia (LMA), New Zealand.
- **7h Other methods and the safe default:** MWL, ISNA, Egyptian, Umm al-Qura, Karachi, Moonsighting Committee, Kuwait, Qatar, Dubai, Singapore as global "Other methods" with safe rounding (+30 s starts, −30 s sunrise) and the legacy high-latitude choice; the class D safe default (spec §6.2 c).

- [ ] Each subtask ends with `./gradlew -p tools/timetables jvmTest gate` green (and `:shared:testAndroidHostTest` for its commonTest) and commits `Proof: <group> reproduced and checked against its official tables`.

---

## Task 8: Engine entry point and the switch-over

**Files:**
- Create: `engine/Engine.kt` (`PrayerEngine`)
- Modify: `prayer/PrayerTimesEngine.kt`, `domain/Prayer.kt` (`DayPrayerTimes`), every caller in the code map (`docs/superpowers/plans/2026-09-27-prayer-engine-codemap.md`, "Callers of the engine"), `tools/timetables` (`Timetable.kt` post-processing removed, `Catalog.kt`, `Document.kt`, `AppStrings.kt`, their tests). adhan2 stays (Qibla).
- Test: `engine/EngineTest.kt`, existing prayer tests updated.

**Interfaces:**

```kotlin
package world.taqwa.app.prayer.engine

data class EngineSettings(
    val timetable: TimetableChoice = TimetableChoice.Automatic,
    val school: SchoolChoice = SchoolChoice.Automatic,
    val saudiFajrLater: Boolean = false,
    val legacyHighLatitude: String? = null,          // only for Other methods
    val adjustmentsMinutes: Map<Prayer, Int> = emptyMap(),
    val confirmedAdjustments: Map<Prayer, String> = emptyMap(),  // prayer → registry entry id it was confirmed under
    val hijriOffsetDays: Int = 0,
)
sealed interface TimetableChoice { data object Automatic : TimetableChoice; data class Entry(val id: String) : TimetableChoice }
enum class SchoolChoice { Automatic, Standard, Hanafi }

data class EngineDay(
    val day: PrayerDay,               // after adjustments and invariants
    val resolution: Resolution,       // what Automatic resolved to for this place
    val effectiveEntry: RegistryEntry,// Automatic's entry, or the chosen one when in scope
    val school: AsrSchool,            // the school that leads
    val pausedAdjustments: Set<Prayer>,
)

object PrayerEngine {
    fun dayTimes(place: Place, date: LocalDate, settings: EngineSettings): EngineDay   // bounded LRU inside
}
```

Rules: a chosen `Entry` outside its scope falls back to Automatic (the UI says so); `saudiFajrLater` applies only when the effective entry is Umm al-Qura and the place is in Saudi Arabia (+5 min to the shown Fajr, `endOfEating` unchanged); adjustments apply after the invariants, never move `endOfEating` or sunrise later, and are clamped: Maghrib ≥ sunset, Dhuhr ≥ transit + 1 min, Asr ≥ the Standard Asr, order preserved; a negative adjustment whose confirmation belongs to another entry is paused (not applied) and reported. Cache: bounded LRU (256 entries) keyed on (lat, lon rounded to 1e-4, zone, date, settings), thread-safe.

`PrayerTimesEngine.timesFor(location, date, settings)` keeps its signature for callers and maps `EngineDay` to `DayPrayerTimes`, extended with: `setByRule: Set<Prayer>`, `polar: Boolean`, `asrOther: Instant`, `endOfEating: Instant`, `sourceEntryId`, `entryClass`, `sunset`, `ends`. `highLatitudeRuleApplied`/`nearestLatitudeFallbackApplied` map from `setByRule`/`polar` until their readers are removed (Task 10).

- [ ] Tests: the engine returns minute-aligned instants for 20 sample places across classes; the LRU returns the same object for the same key; Saudi Fajr +5 only in SA with Umm al-Qura; a chosen London Unified entry used in İstanbul falls back to Diyanet; adjustment clamps (Maghrib −10 min in Riyadh stops at sunset); paused adjustment when the confirmation entry differs.
- [ ] `GeoLocation.countryCode` may be null (old installs): resolve it from the nearest city (`CityRepository.nearest`) where available, else from the zone id. The site's `Timetable.kt` must drop its Ramadan +30 and madhab post-processing and read everything from the engine; update `TimetableTest`'s pinned Tripoli numbers to what the engine now gives (they change: Libya is now the Awqaf method) and keep Makkah's Ramadan 120; Apia must now work (the engine keeps every time on its civil date).
- [ ] Run `scripts/test.sh` (Android host + iOS) and `./gradlew -p tools/timetables jvmTest`; fix every caller; delete `CalculationMethodDefaults` and `HighLatitudeSelector` once nothing uses them (keep `HighLatitudePreference` only if the legacy picker still needs it).
- [ ] Commit — `Prayer: the app and the site on the new engine`

---

## Task 9: Settings model and migration

**Files:** `domain/PrayerSettings.kt`, `settings/SettingsKeys.kt`, `settings/SettingsRepository.kt`, their tests.

**Rules** (spec §8): a versioned migration (`prayer_settings_schema = 3`) run inside `SettingsRepository` before any reader:
- **Method**: `calculation_method_user_chosen` not true → `timetable = Automatic`. True: `TEHRAN` and the stand-ins (SINGAPORE in SG/MY/ID/BN, KARACHI in BD, TURKEY in TR, UMM_AL_QURA in SA, EGYPTIAN in EG, DUBAI/KUWAIT/QATAR at home, ISNA in US/CA) → Automatic; any other → `Entry(global id)` of the matching Other method.
- **Asr**: stored HANAFI stays Hanafi; STANDARD or nothing → Automatic.
- **High latitude**: kept only as `legacyHighLatitude` for Other methods.
- **Negative adjustments**: kept, unconfirmed (paused until confirmed in Settings).
- New keys: `prayer_timetable` (string: `automatic` or entry id), `prayer_school` (`automatic`/`standard`/`hanafi`), `prayer_show_both_asr`, `prayer_show_where_differ`, `prayer_saudi_fajr_later`, `prayer_adjust_confirmed` (serialized map), `prayer_card_sunni_seen`, `prayer_card_cautious_seen`, `prayer_settings_schema`.
- Remove `applyCountryDefaultMethod` and its three call sites (`App.kt:234`, `SettingsRoutes.kt:298`, `LocationRefresher.kt:114`) so a location save never writes a method; update `LocationRefresherTest` (line 84 expects TURKEY) and the four `SettingsRepositoryTest` cases. Per-field writes (no whole-object `setPrayerSettings` rewriting unrelated keys). After any prayer-settings write from the new screens, call `NotificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)` (today nothing does).
- Tests (named without commas): UK profile with MWL and no flag → Automatic; PK with KARACHI and no flag → Automatic; flag with TEHRAN → Automatic; flag with MWL → Entry(MWL); stored HANAFI stays; stored STANDARD → Automatic; negative Maghrib −2 stays but unconfirmed; the migration runs once (schema key).
- [ ] Commit — `Settings: prayer settings that name a timetable, and a migration that never keeps an early method`

---

## Task 10: The Prayer screen

**Files:** `feature/today/TodayScreen.kt`, `feature/today/PrayerTimeline.kt`, `feature/today/TodayViewModel.kt`, `prayer/TimelineBuilder.kt`, new `feature/today/WhoseTimesCard.kt`, new `feature/today/OneTimeCards.kt`, new `feature/today/ClockLine.kt`; remove `HighLatitudeCard`/`HighLatitudeCopy` (keep only the polar sentence string).

Build exactly the spec §2.1 table and the mockups (artifact Dk3p2Upgwn2DfhsJTjiJfP, sections 1–2):
1. **ⓘ after the date**: an inline 1em glyph (InlineTextContent) at the end of the date text; the whole city-and-date column is the 48 dp touch target; tapping opens a small anchored card (Popup) with the entry's short name + " timetable" / "Cautious times" / "{short} method, not yet fully checked" / "Calculated by Taqwa", one sentence, and "About these times ›". Screen-reader label per spec §2.4. RTL: the glyph at the end of the date on the left.
2. **Source line**: only for class C: one tertiary line "Cautious times ›" under the prayer card, 48 dp touch height, opens About.
3. **Set by rule**: the Jumuʿah pill style, inline after the prayer's Arabic name, for keys in `setByRule`; on polar days no pills and one tertiary line under the list with the existing polar sentence, opening About.
4. **One-time cards** below the prayer card, one at a time: cautious-times card (class C places, `prayer_card_cautious_seen`): "Mosques here follow different timetables. Taqwa shows each prayer once they have all begun it." actions "Match my mosque" and "OK"; Sunni card (Shia regions, `prayer_card_sunni_seen`): "These are Sunni prayer times. In Shia (Ja'fari) practice, Maghrib and iftar are later." actions "About these times" and "OK". Cautious first; the other waits for the next launch. The exact-alarms card keeps its place.
5. **Clock line** (spec §3.9): one line above the ring when the phone's zone data or hand-set clock disagrees with a known change (Morocco −1 h from 2026-09-20, British Columbia +1 h from 2026-11-01, Kazakhstan −1 h from 2024-03-01): compare the phone's current UTC offset for the place's zone with the table's true offset; Android also reads `Settings.Global.AUTO_TIME` (expect/actual). Tapping opens a sheet with the direction-specific text. Landscape: top of the right pane.
6. **Other Asr** sub-line when "Show both Asr times" is on; **where timetables differ** sub-line (class C, switch on) under the current prayer: before the earliest next start "{member}'s {prayer} begins at {time}", after it "{member}'s {prayer} has begun · Taqwa shows it from {time}".
7. **Highlight** (`TimelineBuilder`): unchanged rule except Fajr is not current from sunrise and Asr is not current from sunset (use `ends` FAJR and ASR); the ring still counts to the next start. Widgets unchanged (their builder is not touched).
8. Isha after local midnight: `TimelineBuilder` considers yesterday's Isha when it is still ahead.

- [ ] Compose previews for each state; unit tests for TimelineBuilder's new highlight rule and the after-midnight Isha; screenshot checks on the emulator and simulator (Task 14).
- [ ] Commit per element or two (e.g. `Today: whose times, one tap away`, `Today: set by rule, the one-time cards and the clock line`).

---

## Task 11: About these times

**Files:** new `feature/settings/AboutTimesScreen.kt`, route in `SettingsRoutes.kt` (and a route reachable from Today).

Four templates (spec §2.3) filled from `EngineDay` and the entry's stamp (bundle the stamps' headline numbers as a small generated Kotlin table or resource: place-days, places, worst late, provenThrough). Sections as in the mockups: who publishes (authority, not affiliated), how Taqwa reproduces (angles and minutes in words, generated from the method), how it was checked (stat tiles) or "not measured for {unit}", timetables some local mosques follow instead (`nearby`), cautious table (which timetable decides each time today, the later Maghrib named), calculated text (safe default in words), Asr school and why, "If you are fasting: stop eating by {endOfEating}", the Sunni note where it applies, "Match my mosque ›" where relevant.

- [ ] Tests for the template selection per class; previews for the four mockup screens (İstanbul, Tripoli, Toronto, Zahedan).
- [ ] Commit — `About: whose times these are, how they were checked, and when to stop eating`

---

## Task 12: Settings › Prayer times, Timetable and Match my mosque

**Files:** `feature/settings/PrayerTimesSettingsScreen.kt`, `SettingsRoutes.kt`, new `feature/settings/TimetableScreen.kt`, new `feature/settings/MatchMyMosqueScreen.kt`.

Exactly spec §2.2 and mockup section 3:
- Timetable row (value "Automatic · {name}"); the Timetable screen: Automatic (with its explanation), the entry's `nearby` timetables each with today's Fajr, Maghrib, Isha, "Other methods" (sub-list), "Match my mosque"; out-of-scope notice line; the earlier-than warning when a choice begins any prayer before Automatic on any day in the next 12 months (check with the engine) — "Its {prayer} begins {n} minutes before {Automatic's name} on some days. Follow it only if your mosque does." with "Keep Automatic" / "Follow it".
- "Pray Fajr 5 minutes later" row inside the Timetable card, Saudi Arabia only.
- High latitude rule row only when the timetable is an Other method.
- Asr: segmented Automatic / Standard / Hanafi, the note naming the school by its shadow; "Show both Asr times"; "Show where timetables differ" (class C only).
- Manual adjustments: rows show today's clock time; the first step below zero asks with the one template (spec §2.2); stronger text past the timetable's own margin; paused rows "−2 min · paused (set for {short}) ›"; floors enforced by the engine (Task 8).
- Match my mosque: fields "Fajr begins" and "Isha begins", note about the adhan vs jamāʿah, candidates = `nearby` + Other methods, a match is both typed times equal or at most 1 minute later (never earlier), exact first then latest; result card with Dhuhr, Asr, Maghrib to check; one-time-matches and 60-minute states; no match state; "Use this timetable" (with the earlier-than warning).
- About these times row at the foot. Hijri and Show sunrise unchanged.

- [ ] Unit tests for the matcher (exact, later by 1, earlier rejected, 60-minute case, none) and the earlier-than scan; previews.
- [ ] Commit — `Settings: timetables by name, Match my mosque, and adjustments that explain themselves`

---

## Task 13: Strings in seven languages

**Files:** every `shared/src/commonMain/composeResources/values*/strings.xml` (en, ar, tr, id, ur, fr, bn).

- Add every new string from Tasks 10–12 and the authority short names (`authority_*`), in all seven languages, following spec §2.4's approved terms: Cautious times (ar الأحوط / "الأوقات الأحوط", ur احتیاطی اوقات, tr İhtiyatlı vakitler, id Waktu ihtiyat, fr Horaires par précaution, bn সতর্কতামূলক সময়), Set by rule (ar تقديري, ur تقدیری, tr takdirî …), Calculated by Taqwa.
- Change fr `madhab_standard` from "Majoritaire" to "Standard"; notes name schools by their shadow in every language.
- `values-in` must stay byte-identical to `values-id` (`tools/sync-indonesian.py`); run `scripts/check-strings.sh`.
- Update `credit_calculation_detail` so it credits Taqwa's own engine for prayer times and adhan2 only for the Qibla bearing (and `docs/ATTRIBUTION.md`).
- Remove Tehran's strings and the high-latitude card strings no longer used (keep the polar sentence).
- Run the repo's existing string tests (key parity across locales, apostrophe rules).
- [ ] Commit — `i18n: the new prayer-time words in seven languages`

---

## Task 14: Verification on devices

- Run `scripts/test.sh`; `./gradlew -p tools/timetables test gate` and save the gate report to `docs/research/2026-09-prayer-times/proof.md` (summary only).
- Android emulator (`Pixel_8_Pro_2`, port 5556, `ANDROID_SERIAL=emulator-5556`): install the debug build and, with the mock-location recipe from the toolchain notes, visit İstanbul, Makkah, Riyadh (Arabic), London, Toronto, Kuala Lumpur, Jakarta, Cairo, Tripoli, Benghazi, Casablanca, Karachi, Chicago, New York, Moscow (with a June date if the app has a debug date override; otherwise today's), Zahedan, Murmansk; screenshot each; compare the times with the official tables where held.
- iOS simulator: build and run with `scripts/ios-build.sh` (or the repo's script), screenshots of İstanbul, London, Toronto and Riyadh in Arabic, Settings, About.
- Check landscape once; check 200 % font once on each platform.
- [ ] Commit screenshots only if the repo keeps such evidence (docs/screenshots is for store assets — do not overwrite). Write the evidence list into the final report instead.

---

## Task 15: Wrap-up

- Update `docs/BUILD-LOG.md` with a section on the engine (what it reproduces, how it is proven, what remains).
- Update the spec's status line and record deviations in a "What changed while building" section.
- Final full test run and gate; make sure `git status` is clean and no archive file is tracked.
- Do not push. Report to Mohamed: branch name, how to build an APK for his phone (`./gradlew :androidApp:assembleDebug` path) and how to run on iPhone, the gate summary, what is not done (the website slice, Ramadan release items, anything deferred).

---

## Deferred (not in this branch)

- The weekly monitor of each authority's newest table (spec §5): needs the stamps from Task 7 and a local scheduled job; after launch.
- The website slice (spec §7): after the app, as Mohamed asked.
- The Ramadan release items (spec §7).
