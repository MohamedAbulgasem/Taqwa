package world.taqwa.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.prayer.ClockIssue
import world.taqwa.app.prayer.ClockWarning
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The Prayer screen's states (spec §2.1, mockup sections 1–2), each as the view model would
// publish it. The five times are the engine's own for each place and date, computed when the
// preview renders: never a printed row of an authority's table (ruling R69).

private val FivePrayers = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

/** [place]'s Automatic day on [date], as the engine computes it. */
private fun engineDay(place: Place, date: LocalDate): EngineDay = PrayerEngine.dayTimes(place, date, EngineSettings())

/** The five starts of [day], Fajr to Isha, as local "H:mm" in [zoneId]. */
private fun fiveTimes(day: EngineDay, zoneId: String): List<String> {
    val zone = TimeZone.of(zoneId)
    return listOf(day.day.fajr, day.day.dhuhr, day.day.asr, day.day.maghrib, day.day.isha).map {
        val t = it.toLocalDateTime(zone)
        "${t.hour}:${t.minute.toString().padStart(2, '0')}"
    }
}

private fun fiveTimes(place: Place, date: LocalDate): List<String> = fiveTimes(engineDay(place, date), place.zoneId)

private fun localInstant(date: LocalDate, hhmm: String, zone: TimeZone): Instant {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    return LocalDateTime(date, LocalTime(h, m)).toInstant(zone)
}

/**
 * A ready state for [city] on [date] at [now] (local), with the five times [times] (local, Fajr to
 * Isha). [current] is the row the screen highlights, which the caller decides as TimelineBuilder
 * would; the ring's figures are only plausible.
 */
private fun previewState(
    city: String,
    zoneId: String,
    countryCode: String,
    date: LocalDate,
    hijri: String,
    gregorian: String,
    times: List<String>,
    now: String,
    current: Prayer?,
    whose: WhoseTimes,
    setByRule: Set<Prayer> = emptySet(),
    polar: Boolean = false,
    oneTimeCard: OneTimeCard? = null,
    clockWarning: ClockWarning? = null,
    otherAsr: OtherAsr? = null,
    differ: DifferLine? = null,
): TodayUiState.Ready {
    val zone = TimeZone.of(zoneId)
    val instants = FivePrayers.zip(times.map { localInstant(date, it, zone) })
    val nowInstant = localInstant(date, now, zone)
    val rows = instants.map { (prayer, instant) ->
        TimelineRow(
            prayer = prayer,
            instant = instant,
            status = when {
                prayer == current -> PrayerStatus.CURRENT
                instant <= nowInstant -> PrayerStatus.PASSED
                else -> PrayerStatus.UPCOMING
            },
        )
    }
    val next = instants.firstOrNull { it.second > nowInstant }
        ?.let { PrayerTime(it.first, it.second) }
        ?: PrayerTime(Prayer.FAJR, localInstant(date.plus(1, DateTimeUnit.DAY), times.first(), zone))
    return TodayUiState.Ready(
        location = GeoLocation(0.0, 0.0, zoneId, city, countryCode),
        cityDisplayName = city,
        hijri = hijri,
        gregorian = gregorian,
        today = TodayState(rows, next, next.instant - nowInstant, ringProgress = 0.4f),
        whose = whose,
        setByRule = setByRule,
        polar = polar,
        oneTimeCard = oneTimeCard,
        clockWarning = clockWarning,
        otherAsr = otherAsr,
        differ = differ,
        qiblaBearingDegrees = 152.0,
        qiblaDistanceKm = 2406.0,
    )
}

private val Sep26 = LocalDate(2026, 9, 26)
private val Oct1 = LocalDate(2026, 10, 1)

private val IstanbulPlace = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
private val TorontoPlace = Place(43.6532, -79.3832, "America/Toronto", "CA")

private fun istanbul(now: String, current: Prayer?) = previewState(
    city = "İstanbul", zoneId = "Europe/Istanbul", countryCode = "TR", date = Sep26,
    hijri = "13 Rabi’ al-Thani 1448", gregorian = "26 September 2026",
    times = fiveTimes(IstanbulPlace, Sep26),
    now = now, current = current,
    whose = WhoseTimes("authority_diyanet", EntryClass.A),
)

private fun toronto(
    now: String,
    current: Prayer?,
    oneTimeCard: OneTimeCard? = null,
    otherAsr: OtherAsr? = null,
    differ: DifferLine? = null,
) = previewState(
    city = "Toronto", zoneId = "America/Toronto", countryCode = "CA", date = Oct1,
    hijri = "18 Rabi’ al-Thani 1448", gregorian = "1 October 2026",
    times = fiveTimes(TorontoPlace, Oct1),
    now = now, current = current,
    whose = WhoseTimes("timetable_cautious", EntryClass.C),
    oneTimeCard = oneTimeCard, otherAsr = otherAsr, differ = differ,
)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTaqwaColors provides LightColors) { content() }
}

@Composable
private fun ScreenPreview(state: TodayUiState, exactAlarmsOff: Boolean = false) = PreviewSurface {
    TodayScreen(
        state = state,
        onChooseCity = {},
        onOpenLocationSettings = {},
        onOpenQibla = {},
        onOpenTasbeeh = {},
        exactAlarmsOff = exactAlarmsOff,
    )
}

/** An authority's checked timetable: the only change is the ⓘ at the end of the date. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun IstanbulPreview() = ScreenPreview(istanbul(now = "17:05", current = Prayer.ASR))

/** After sunrise nothing is lit until Dhuhr; the ring still counts to it. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun AfterSunrisePreview() = ScreenPreview(istanbul(now = "9:00", current = null))

/** Cautious times: the source line, and the cautious card on its first launch. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TorontoCautiousCardPreview() =
    ScreenPreview(toronto(now = "19:20", current = Prayer.MAGHRIB, oneTimeCard = OneTimeCard.CAUTIOUS), exactAlarmsOff = true)

/** Both Asr times and where timetables differ, switched on in Settings. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TorontoDifferPreview() {
    val day = engineDay(TorontoPlace, Oct1)
    ScreenPreview(
        toronto(
            now = "19:20",
            current = Prayer.MAGHRIB,
            otherAsr = OtherAsr(day.school.other, day.day.asrOther),
            differ = DifferLine(
                under = Prayer.MAGHRIB,
                prayer = Prayer.ISHA,
                memberNameKey = "authority_ift",
                // An invented earlier member Isha: the line's shape, not any timetable's minute.
                memberStart = day.day.isha - 20.minutes,
                shownStart = day.day.isha,
                begun = false,
            ),
        ),
    )
}

/** A high-latitude summer: Fajr and Isha set by DUM RF's rule, marked inline. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun MoscowSetByRulePreview() = ScreenPreview(
    previewState(
        city = "Moscow", zoneId = "Europe/Moscow", countryCode = "RU", date = LocalDate(2027, 6, 21),
        hijri = "16 Muharram 1449", gregorian = "21 June 2027",
        times = fiveTimes(Place(55.7558, 37.6173, "Europe/Moscow", "RU"), LocalDate(2027, 6, 21)),
        now = "22:40", current = Prayer.MAGHRIB,
        whose = WhoseTimes("authority_dum_rf", EntryClass.D_AUTHORITY),
        setByRule = setOf(Prayer.FAJR, Prayer.ISHA),
    ),
)

/**
 * A polar day: no pills, one line under the list. (Not Tromsø: IRN's Makkah-time rule gives its
 * polar days whole, ruling R82, so nothing there follows the nearest latitude.)
 */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LongyearbyenPolarPreview() = ScreenPreview(
    previewState(
        city = "Longyearbyen", zoneId = "Europe/Oslo", countryCode = "SJ", date = LocalDate(2026, 12, 21),
        hijri = "1 Rajab 1448", gregorian = "21 December 2026",
        times = fiveTimes(Place(78.2232, 15.6267, "Europe/Oslo", "SJ"), LocalDate(2026, 12, 21)),
        now = "10:00", current = Prayer.FAJR,
        whose = WhoseTimes("timetable_cautious", EntryClass.C),
        polar = true,
    ),
)

/** Where Shia are many: the Sunni card, calculated by Taqwa. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ZahedanSunniCardPreview() = ScreenPreview(
    previewState(
        city = "Zahedan", zoneId = "Asia/Tehran", countryCode = "IR", date = Sep26,
        hijri = "13 Rabi’ al-Thani 1448", gregorian = "26 September 2026",
        times = fiveTimes(Place(29.4963, 60.8629, "Asia/Tehran", "IR", admin1 = "Sistan and Baluchestan"), Sep26),
        now = "13:20", current = Prayer.DHUHR,
        whose = WhoseTimes("timetable_calculated", EntryClass.D_NONE),
        oneTimeCard = OneTimeCard.SUNNI,
    ),
)

private val moroccoStale = ClockWarning(ClockChanges.table.first { it.id == "morocco" }, ClockIssue.STALE_ZONE_DATA, timesEarly = false)

private fun casablanca() = previewState(
    city = "Casablanca", zoneId = "Africa/Casablanca", countryCode = "MA", date = Sep26,
    hijri = "13 Rabi’ al-Thani 1448", gregorian = "26 September 2026",
    times = fiveTimes(Place(33.5731, -7.5898, "Africa/Casablanca", "MA"), Sep26),
    now = "22:02", current = Prayer.ISHA,
    whose = WhoseTimes("authority_habous", EntryClass.B),
    clockWarning = moroccoStale,
)

/** A phone behind on its clocks: one line above the ring. */
@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun CasablancaClockLinePreview() = ScreenPreview(casablanca())

/** Sideways, the clock line heads the right pane. */
@Preview(widthDp = 891, heightDp = 411)
@Composable
private fun CasablancaClockLineLandscapePreview() = ScreenPreview(casablanca())

/** Arabic, right to left: the ⓘ ends the date on the left. */
@Preview(widthDp = 411, heightDp = 891, locale = "ar")
@Composable
private fun RiyadhArabicPreview() = ScreenPreview(
    previewState(
        city = "الرياض", zoneId = "Asia/Riyadh", countryCode = "SA", date = Sep26,
        hijri = "١٣ ربيع الآخر ١٤٤٨", gregorian = "٢٦ سبتمبر ٢٠٢٦",
        times = fiveTimes(Place(24.7136, 46.6753, "Asia/Riyadh", "SA"), Sep26),
        now = "18:30", current = Prayer.MAGHRIB,
        whose = WhoseTimes("authority_umm_al_qura", EntryClass.A),
    ),
)

/** The ⓘ's card, once per class. */
@Preview(widthDp = 380, heightDp = 620)
@Composable
private fun WhoseTimesCardsPreview() = PreviewSurface {
    Column(Modifier.background(LightColors.background).padding(16.dp)) {
        listOf(
            WhoseTimes("authority_diyanet", EntryClass.A) to "İstanbul",
            WhoseTimes("timetable_cautious", EntryClass.C) to "Toronto",
            WhoseTimes("authority_awqaf_libya", EntryClass.D_AUTHORITY) to "Tripoli",
            WhoseTimes("timetable_calculated", EntryClass.D_NONE) to "Zahedan",
        ).forEach { (whose, place) ->
            Box(Modifier.padding(bottom = 12.dp)) {
                WhoseTimesCardContent(whose, place, caretX = 240f, onOpenAboutTimes = {}, onDismiss = {})
            }
        }
    }
}
