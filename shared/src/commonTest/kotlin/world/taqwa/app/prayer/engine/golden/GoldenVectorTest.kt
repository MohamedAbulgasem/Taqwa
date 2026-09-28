package world.taqwa.app.prayer.engine.golden

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.SchoolChoice
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Spec §3.2: "deterministic on every target: tests in `shared/commonTest` run on the JVM, Android and
 * iOS against a committed golden vector of about 2,000 place-days" (ruling R84).
 * [GOLDEN_VECTOR_ROWS] (`GoldenVectorData.kt`, written by `tools/timetables`'
 * `generateGoldenVector`) is recomputed here through the app's own [PrayerEngine.dayTimes] and
 * compared exactly, field by field, in minutes from 00:00 UTC on each row's civil date — the same
 * encoding `world.taqwa.timetables.golden.encodeRow` used to write it (UTC, because a target's zone data
 * can lag a zone's change, as British Columbia's November 2026 one did on the iOS simulator). The point of the test:
 * every target this runs on (a JVM here, through `:shared:testAndroidHostTest` on the Android host,
 * and on iOS through `scripts/test.sh`) computes the same minute the JVM did when the vector was
 * generated, however each target's Kotlin/Native or Android runtime does its trigonometry.
 *
 * Regenerate the vector (`./gradlew -p tools/timetables generateGoldenVector`) after any intended
 * change to the engine or the registry, and re-run this test; never hand-edit `GoldenVectorData.kt`.
 */
class GoldenVectorTest {

    @Test
    fun `every place-day matches the committed golden vector`() {
        val mismatches = mutableListOf<String>()
        var checked = 0
        for (row in GOLDEN_VECTOR_ROWS) {
            val case = decodeRow(row)
            val day = PrayerEngine.dayTimes(case.place, case.date, case.settings).day
            val actual = fieldsInMinutes(day, case.date)
            for (i in FIELD_NAMES.indices) {
                if (actual[i] != case.expected[i]) {
                    mismatches += "${case.place.lat},${case.place.lon} ${case.place.zoneId} ${case.date}: " +
                        "${FIELD_NAMES[i]} expected ${case.expected[i]}, got ${actual[i]} (row: $row)"
                }
                if (mismatches.size >= MAX_REPORTED) break
            }
            checked++
            if (mismatches.size >= MAX_REPORTED) break
        }
        if (mismatches.isNotEmpty()) {
            fail(
                "${mismatches.size}+ mismatch(es) against the golden vector, checked $checked/" +
                    "${GOLDEN_VECTOR_ROWS.size} rows before stopping — first $MAX_REPORTED:\n" +
                    mismatches.joinToString("\n"),
            )
        }
        check(checked == GOLDEN_VECTOR_ROWS.size) { "stopped early after $checked rows" }
    }
}

private const val MAX_REPORTED = 10

/** The ten timed fields a row holds, in the order `encodeRow` writes them. */
private val FIELD_NAMES = listOf(
    "fajr", "sunrise", "dhuhr", "asr", "asrOther", "maghrib", "isha", "sunset", "endOfEating", "imsak",
)

private class DecodedCase(val place: Place, val date: LocalDate, val settings: EngineSettings, val expected: List<Int?>)

/** The reverse of `world.taqwa.timetables.golden.encodeRow`'s eighteen `;`-separated fields. */
private fun decodeRow(row: String): DecodedCase {
    val f = row.split(";")
    require(f.size == 18) { "bad golden-vector row (expected 18 fields, got ${f.size}): $row" }
    val place = Place(
        lat = f[0].toInt() / 1000.0,
        lon = f[1].toInt() / 1000.0,
        zoneId = f[2],
        countryCode = f[3],
        admin1 = f[4].ifEmpty { null },
    )
    val date = LocalDate.parse(f[5])
    val settings = decodeSettings(f[6])
    val expected = (7..16).map { i -> f[i].takeIf { it != "-" }?.toInt() }
    return DecodedCase(place, date, settings, expected)
}

/** The reverse of `world.taqwa.timetables.golden.SettingsCase.toToken`. */
private fun decodeSettings(token: String): EngineSettings = when {
    token == "AUTO" -> EngineSettings()
    token == "HANAFI" -> EngineSettings(school = SchoolChoice.Hanafi)
    token == "SAUDI" -> EngineSettings(saudiFajrLater = true)
    token.startsWith("CHOSEN:") -> EngineSettings(
        timetable = TimetableChoice.Entry(token.removePrefix("CHOSEN:")),
        timetableConfirmed = true,
    )
    token.startsWith("ADJ:") -> {
        val parts = token.split(":")
        EngineSettings(adjustmentsMinutes = mapOf(Prayer.valueOf(parts[1]) to parts[2].toInt()))
    }
    else -> error("unknown golden-vector settings token '$token'")
}

/** [day]'s ten timed fields, each in minutes from 00:00 UTC on [date] (an `Int`, safe for any place-day
 * this vector holds: never more than a few thousand minutes either way). UTC, as the generator writes it:
 * the engine's instants are what must match on every target, whatever each platform's zone data says. */
private fun fieldsInMinutes(day: PrayerDay, date: LocalDate): List<Int?> {
    val midnight = date.atStartOfDayIn(TimeZone.UTC)
    fun minutes(instant: Instant): Int = ((instant.epochSeconds - midnight.epochSeconds) / 60).toInt()
    return listOf(
        minutes(day.fajr), minutes(day.sunrise), minutes(day.dhuhr), minutes(day.asr), minutes(day.asrOther),
        minutes(day.maghrib), minutes(day.isha), minutes(day.sunset), minutes(day.endOfEating),
        day.imsak?.let(::minutes),
    )
}
