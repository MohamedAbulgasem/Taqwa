package world.taqwa.app.watchfeed

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.prayer.engine.registry.EntryClass
import java.io.File
import kotlin.time.Instant

/**
 * Prayer times frozen in a file, so that the golden feeds (`WatchFeedGoldenTest`) are built from
 * data and never from what the engine gives today: Cape Town's November rows, or any later engine
 * change, move no byte of them. A table is
 *
 *     {"zone": "<id>", "days": {"<yyyy-mm-dd>": {"fajr": s, "sunrise": s, "dhuhr": s, "asr": s,
 *      "maghrib": s, "isha": s, "fajrEnd": s|null, "asrEnd": s|null}}}
 *
 * in epoch seconds. It is recorded once, when the golden test is written, and never refreshed to
 * follow the engine: it is data, as the sample's instants are.
 */
internal object FrozenTimes {

    /**
     * The table in [file] as a `timesFor`, which throws for a date the table does not hold, as the
     * engine's caller sees a day it cannot give. Only what the feed reads is real: the six starts and
     * Fajr's and Asr's ends. The rest of [DayPrayerTimes] is filled from them, and nothing reads it.
     */
    fun read(file: File): (LocalDate) -> DayPrayerTimes {
        val days = Json.parseToJsonElement(file.readText()).jsonObject.getValue("days").jsonObject
        return { date ->
            val day = days[date.toString()]?.jsonObject ?: error("${file.name} holds no times for $date")
            fun at(key: String): Instant = Instant.fromEpochSeconds(day.getValue(key).jsonPrimitive.long)
            fun endAt(key: String): Instant? = day[key]?.jsonPrimitive?.longOrNull?.let(Instant::fromEpochSeconds)
            val ends = buildMap {
                endAt("fajrEnd")?.let { put(Prayer.FAJR, it) }
                endAt("asrEnd")?.let { put(Prayer.ASR, it) }
            }
            DayPrayerTimes(
                date = date,
                times = Prayer.entries.map { PrayerTime(it, at(KEYS.getValue(it))) },
                highLatitudeRuleApplied = null,
                asrOther = at("asr"),
                sunset = ends[Prayer.ASR] ?: at("maghrib"),
                endOfEating = at("fajr"),
                ends = ends,
                sourceEntryId = "frozen",
                entryClass = EntryClass.D_NONE,
            )
        }
    }

    /**
     * Writes [location]'s times for every date in [dates] into [file], from the real engine with the
     * default settings. Run only under `-Ptaqwa.recordTimes`, which the build passes to the test as
     * the system property `taqwa.recordTimes`: it is the one place where the golden test may build
     * the engine.
     */
    fun record(file: File, location: GeoLocation, dates: ClosedRange<LocalDate>) {
        val engine = world.taqwa.app.prayer.PrayerTimesEngine()
        val rows = mutableListOf<String>()
        var date = dates.start
        while (date <= dates.endInclusive) {
            val day = engine.timesFor(location, date, PrayerSettings())
            val row = JsonObject(
                Prayer.entries.associate { KEYS.getValue(it) to JsonPrimitive(day.time(it).epochSeconds) } +
                    mapOf(
                        "fajrEnd" to (day.ends[Prayer.FAJR]?.let { JsonPrimitive(it.epochSeconds) } ?: JsonNull),
                        "asrEnd" to (day.ends[Prayer.ASR]?.let { JsonPrimitive(it.epochSeconds) } ?: JsonNull),
                    ),
            )
            rows += "    ${JsonPrimitive(date.toString())}: $row"
            date = date.plus(1, DateTimeUnit.DAY)
        }
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("{")
                appendLine("  \"zone\": ${JsonPrimitive(location.timeZoneId)},")
                appendLine("  \"days\": {")
                rows.forEachIndexed { index, row -> appendLine(row + if (index < rows.lastIndex) "," else "") }
                appendLine("  }")
                appendLine("}")
            },
        )
    }

    private val KEYS: Map<Prayer, String> = mapOf(
        Prayer.FAJR to "fajr",
        Prayer.SUNRISE to "sunrise",
        Prayer.DHUHR to "dhuhr",
        Prayer.ASR to "asr",
        Prayer.MAGHRIB to "maghrib",
        Prayer.ISHA to "isha",
    )
}
