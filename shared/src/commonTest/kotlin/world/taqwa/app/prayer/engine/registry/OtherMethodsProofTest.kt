package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.RamadanCalendar
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Task 7h: the 11 Other methods (R12) against an independent reference: adhan2 itself, called
 * directly through [Adhan2Reference], reproducing the mapping the old adhan2-backed
 * `PrayerTimesEngine` used before Task 8 replaced it with the new engine (ruling R6). This is a
 * structural sanity check, not the never-early gate: the gate proves an authority's own printed
 * tables; here there is no authority beyond adhan2's own preset, since that preset *is* what
 * "Other methods" promises to reproduce (`Generic.kt`'s doc comment).
 *
 * The one binding promise this test enforces strictly is never-early: a start
 * ([Prayer.FAJR], [Prayer.DHUHR], [Prayer.ASR], [Prayer.MAGHRIB], [Prayer.ISHA]) must never be
 * before the old engine's, and [Prayer.SUNRISE] (an end here, like the end of eating) must never
 * be after it — except the one confirmed gap this test found in a file Task 7h does not own
 * ([knownEarlyExceptions]), reported instead of fixed (task-7-common's ownership rule).
 *
 * "Close" (at most a minute or so after) is checked too, but generously
 * ([DEFAULT_LATE_TOLERANCE_SECONDS]): two independently rounded clocks can differ by a couple of
 * minutes purely from where their *continuous* times happen to fall relative to a minute boundary,
 * even when the true gap between them is a few tens of seconds (measured directly at Cape Town,
 * 15 April 2026: adhan2's raw Asr there is only ~36 s from this core's own, but our raw value sits
 * 1.4 s before its own minute boundary, so the 30–65 s safe margin pushes the shown time a further
 * whole minute or two later than adhan2's independently-NEAREST-rounded one). Proof.md documents
 * the measured spread; genuinely different definitions (Qatar's baked-in Maghrib + 3 min, Umm
 * al-Qura's Fajr/Asr declination bias and lag dates) are called out there too, not modelled as
 * separate tolerances here, since the generous bound already covers what was measured.
 *
 * Both engines are run at the same points, dates and Standard (Shafi'i) Asr, at latitudes under
 * 34 degrees so neither the old engine's per-latitude automatic high-latitude pick nor the new
 * engine's [world.taqwa.app.prayer.engine.method.HighLatRule.Standard] proportion engages (that
 * comparison, against adhan2's three legacy rules at London and Oslo, is §2 of the report, not
 * this test).
 */
class OtherMethodsProofTest {

    private data class Spot(val name: String, val lat: Double, val lon: Double, val zoneId: String, val countryCode: String)

    private val spots = listOf(
        Spot("Cairo", 30.0444, 31.2357, "Africa/Cairo", "EG"),
        Spot("Karachi", 24.8607, 67.0011, "Asia/Karachi", "PK"),
        Spot("Kuala Lumpur", 3.1390, 101.6869, "Asia/Kuala_Lumpur", "MY"),
        Spot("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "ZA"),
    )

    // Outside Ramadan 1447 (18 Feb - 19 Mar 2026) and Ramadan 1448, and away from any DST edge.
    private val dates = listOf(LocalDate(2026, 1, 15), LocalDate(2026, 4, 15), LocalDate(2026, 7, 15), LocalDate(2026, 10, 15))

    /** Registry id to the old picker's [CalculationMethodId] it reproduces (R12; all 11, no Tehran). */
    private val methodIds: Map<String, CalculationMethodId> = mapOf(
        "other.mwl" to CalculationMethodId.MUSLIM_WORLD_LEAGUE,
        "other.isna" to CalculationMethodId.ISNA,
        "other.egyptian" to CalculationMethodId.EGYPTIAN,
        "other.ummalqura" to CalculationMethodId.UMM_AL_QURA,
        "other.karachi" to CalculationMethodId.KARACHI,
        "other.moonsighting" to CalculationMethodId.MOONSIGHTING_COMMITTEE,
        "other.turkey" to CalculationMethodId.TURKEY,
        "other.kuwait" to CalculationMethodId.KUWAIT,
        "other.qatar" to CalculationMethodId.QATAR,
        "other.dubai" to CalculationMethodId.DUBAI,
        "other.singapore" to CalculationMethodId.SINGAPORE,
    )

    private val events = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    private val starts = setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    private fun oldTimes(methodId: CalculationMethodId, spot: Spot, date: LocalDate): Map<Prayer, Instant> {
        val day = Adhan2Reference.timesFor(
            methodId = methodId,
            lat = spot.lat,
            lon = spot.lon,
            date = date,
            highLatitudeRule = com.batoulapps.adhan2.HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
        )
        return mapOf(
            Prayer.FAJR to day.fajr, Prayer.SUNRISE to day.sunrise, Prayer.DHUHR to day.dhuhr,
            Prayer.ASR to day.asr, Prayer.MAGHRIB to day.maghrib, Prayer.ISHA to day.isha,
        )
    }

    private fun newTimes(entryId: String, spot: Spot, date: LocalDate): Map<Prayer, Instant> {
        val entry = requireNotNull(Registry.byId(entryId)) { "missing registry entry $entryId" }
        val place = Place(spot.lat, spot.lon, spot.zoneId, spot.countryCode)
        val day = DayPipeline.day(entry, place, date, AsrSchool.STANDARD)
        return mapOf(
            Prayer.FAJR to day.fajr, Prayer.SUNRISE to day.sunrise, Prayer.DHUHR to day.dhuhr,
            Prayer.ASR to day.asr, Prayer.MAGHRIB to day.maghrib, Prayer.ISHA to day.isha,
        )
    }

    private class Delta(val entryId: String, val spot: String, val date: LocalDate, val event: Prayer, val seconds: Long)

    /**
     * A confirmed early violation this test measured in a file Task 7h does not own (`Muis.kt`'s
     * `other.singapore`), reported as a concern rather than fixed here. Diagnosed at Cape Town,
     * 15 Oct 2026: adhan2's raw Asr there is only about 32 s later than this core's own (within the
     * documented [world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE], 35 s), but
     * Singapore is the one adhan2 preset that rounds Asr UP (`Rounding.UP`, not `NEAREST` like the
     * other ten), which turns that sub-minute gap into an exact one-minute early violation the
     * plain 30 s safe start does not cover. `Generic.kt`'s `mwl`/`isna`/`karachi`/`moonsighting` now
     * add the same allowance to Asr (this task's fix, in the file it owns); `other.singapore`,
     * `other.egyptian`, `other.ummalqura`, `other.kuwait`, `other.qatar` and `other.dubai` do not
     * (measured never-early at every sampled point here, since NEAREST rounding tends to absorb a
     * gap this small — but the same fix, `asr = SAFE_START + ADHAN2_ASR_ALLOWANCE`, would remove
     * the risk everywhere, and is the exact, confirmed fix for `other.singapore`).
     */
    private val knownEarlyExceptions: Map<Pair<String, Prayer>, Int> = mapOf(
        ("other.singapore" to Prayer.ASR) to 90,
    )

    @Test
    fun neverEarlierThanTheOldEngineAndCloseToIt() {
        val deltas = mutableListOf<Delta>()
        for ((entryId, methodId) in methodIds) {
            for (spot in spots) {
                for (date in dates) {
                    val old = oldTimes(methodId, spot, date)
                    val new = newTimes(entryId, spot, date)
                    for (event in events) {
                        val oldInstant = requireNotNull(old[event])
                        val newInstant = requireNotNull(new[event])
                        val seconds = (newInstant - oldInstant).inWholeSeconds
                        deltas += Delta(entryId, spot.name, date, event, seconds)
                    }
                }
            }
        }

        // Starts: never before the reference (the never-early promise itself), except the one
        // confirmed, reported gap in a file this task does not own (knownEarlyExceptions).
        val earlyStarts = deltas.filter { d ->
            d.event in starts && d.seconds < -(knownEarlyExceptions[d.entryId to d.event] ?: 0)
        }
        assertTrue(
            earlyStarts.isEmpty(),
            "a start earlier than the old engine (new − old, seconds):\n" +
                earlyStarts.joinToString("\n") { "${it.entryId} ${it.spot} ${it.date} ${it.event}: ${it.seconds}" },
        )

        // Starts: no more than the (generous) allowed tolerance after the reference — see the class
        // doc for why this has to be generous rather than a strict "1 minute".
        val lateStarts = deltas.filter { it.event in starts && it.seconds > DEFAULT_LATE_TOLERANCE_SECONDS }
        assertTrue(
            lateStarts.isEmpty(),
            "a start later than the old engine by more than the allowed tolerance (new − old, seconds):\n" +
                lateStarts.joinToString("\n") { "${it.entryId} ${it.spot} ${it.date} ${it.event}: ${it.seconds}" },
        )

        // Sunrise: never after the reference (the never-early promise's mirror for an end).
        val lateSunrise = deltas.filter { it.event == Prayer.SUNRISE && it.seconds > 0 }
        assertTrue(
            lateSunrise.isEmpty(),
            "sunrise later than the old engine (new − old, seconds):\n" +
                lateSunrise.joinToString("\n") { "${it.entryId} ${it.spot} ${it.date} ${it.event}: ${it.seconds}" },
        )

        // Sunrise: a sanity bound so a real bug (not just the safe rounding) would still be caught.
        val earlySunrise = deltas.filter { it.event == Prayer.SUNRISE && it.seconds < -SUNRISE_EARLY_SANITY_SECONDS }
        assertTrue(
            earlySunrise.isEmpty(),
            "sunrise implausibly earlier than the old engine (new − old, seconds):\n" +
                earlySunrise.joinToString("\n") { "${it.entryId} ${it.spot} ${it.date} ${it.event}: ${it.seconds}" },
        )
    }

    // --- §2: the legacy high-latitude choice (settings' HighLatitudePreference) against adhan2's
    // three HighLatitudeRule options, at London in June and Oslo in May, where the real sign is
    // missing for an 18°/17° method (MWL) and the substitution rule alone decides Fajr and Isha.
    // Task 8 maps `PrayerSettings.legacyHighLatitude` onto this; see the class doc and proof.md §2
    // for the mapping this measured.

    private val legacyMethod = TimetableMethod(id = "test.legacy", fajrAngle = 18.0, isha = IshaRule.Angle(17.0))
    private val noRamadan = RamadanCalendar { false }

    private data class HighLatSpot(val name: String, val lat: Double, val lon: Double, val zoneId: String, val date: LocalDate)

    private val highLatSpots = listOf(
        HighLatSpot("London", 51.5074, -0.1278, "Europe/London", LocalDate(2026, 6, 21)),
        HighLatSpot("Oslo", 59.9139, 10.7522, "Europe/Oslo", LocalDate(2026, 5, 15)),
    )

    /** Our [HighLatRule.Legacy] kind to adhan2's matching [com.batoulapps.adhan2.HighLatitudeRule]. */
    private val legacyKinds = mapOf(
        HighLatRule.Legacy.MIDDLE to com.batoulapps.adhan2.HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
        HighLatRule.Legacy.SEVENTH to com.batoulapps.adhan2.HighLatitudeRule.SEVENTH_OF_THE_NIGHT,
        HighLatRule.Legacy.ANGLE to com.batoulapps.adhan2.HighLatitudeRule.TWILIGHT_ANGLE,
    )

    private fun ourLegacy(kind: String, spot: HighLatSpot): Map<Prayer, Instant> {
        val method = legacyMethod.copy(highLatitude = HighLatRule.Legacy(kind))
        val day = DayComputer.compute(
            method = method, point = GeoPoint(spot.lat, spot.lon), date = spot.date,
            zone = TimeZone.of(spot.zoneId), school = AsrSchool.STANDARD, ramadan = noRamadan, lagDates = emptySet(),
        )
        return mapOf(Prayer.FAJR to day.fajr, Prayer.ISHA to day.isha)
    }

    private fun adhan2Legacy(rule: com.batoulapps.adhan2.HighLatitudeRule, spot: HighLatSpot): Map<Prayer, Instant> {
        val day = Adhan2Reference.timesFor(
            methodId = CalculationMethodId.MUSLIM_WORLD_LEAGUE,
            lat = spot.lat,
            lon = spot.lon,
            date = spot.date,
            highLatitudeRule = rule,
        )
        return mapOf(Prayer.FAJR to day.fajr, Prayer.ISHA to day.isha)
    }

    @Test
    fun legacyHighLatitudeKindsMatchAdhan2AtLondonAndOslo() {
        val mismatches = mutableListOf<String>()
        for (spot in highLatSpots) {
            for ((kind, adhanRule) in legacyKinds) {
                val ours = ourLegacy(kind, spot)
                val theirs = adhan2Legacy(adhanRule, spot)
                for (event in listOf(Prayer.FAJR, Prayer.ISHA)) {
                    val delta = (requireNotNull(ours[event]) - requireNotNull(theirs[event])).inWholeSeconds
                    if (delta < -LEGACY_TOLERANCE_SECONDS || delta > LEGACY_TOLERANCE_SECONDS) {
                        mismatches += "${spot.name} $kind $event: ours=${ours[event]} adhan2=${theirs[event]} deltaSeconds=$delta"
                    }
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "Legacy high-latitude kind mismatched adhan2:\n" + mismatches.joinToString("\n"))
    }

    private companion object {
        const val LEGACY_TOLERANCE_SECONDS = 120L

        /**
         * Measured worst case across the sampled grid was 240 s (Qatar's Isha, which carries its
         * Maghrib's own +3 min baked in, plus rounding-boundary noise); this bound leaves headroom
         * above that without going so wide the test stops catching a real regression (a wrong
         * angle, a wrong sign, a wrong zone — errors of many minutes to hours).
         */
        const val DEFAULT_LATE_TOLERANCE_SECONDS = 300
        const val SUNRISE_EARLY_SANITY_SECONDS = 150
    }
}
