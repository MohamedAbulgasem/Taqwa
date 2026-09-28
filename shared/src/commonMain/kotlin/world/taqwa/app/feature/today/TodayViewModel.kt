package world.taqwa.app.feature.today

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TodayState
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.prayer.ClockWarning
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Member
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.qibla.QiblaMath
import world.taqwa.app.settings.ResolvedCityName
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore
import world.taqwa.app.widget.WidgetInputsMirror
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.refreshWidgets
import kotlin.time.Instant

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NeedsLocation : TodayUiState
    data class Ready(
        val location: GeoLocation,
        /**
         * What the header prints: [GeoLocation.cityId]'s name in the interface language, falling
         * back to [GeoLocation.cityName] — the English snapshot — and null when there is neither,
         * which is where the screen substitutes its own "Current location".
         *
         * A field of its own rather than a rewritten `location.cityName`: the snapshot stays
         * exactly what was stored, and there is one obvious answer to "where does the string on
         * the header come from".
         */
        val cityDisplayName: String?,
        val hijri: String,
        /** The same day as [hijri], Gregorian, via [PlatformFormat.longDate]. */
        val gregorian: String,
        val today: TodayState,
        /** Whose times these are, for the ⓘ's card and the "Cautious times ›" line (spec §2.1). */
        val whose: WhoseTimes,
        /**
         * Today's times a high-latitude rule set, each marked "Set by rule" after its name (spec
         * §2.1, §3.8). Empty on a [polar] day, which says so in one line instead.
         */
        val setByRule: Set<Prayer> = emptySet(),
        /**
         * The sun does not both rise and set here today, so some times follow a nearby latitude
         * (rulings R21, R23): one line under the list, and no pills.
         */
        val polar: Boolean = false,
        /** The one-time card below the list, when one is due this launch (spec §2.1). */
        val oneTimeCard: OneTimeCard? = null,
        /** Why the phone's clock may be an hour off for this place, when it may (spec §3.9). */
        val clockWarning: ClockWarning? = null,
        /** "Show both Asr times": the other school's Asr, under Asr (spec §2.1). */
        val otherAsr: OtherAsr? = null,
        /** "Show where timetables differ", in cautious-times places (spec §2.1). */
        val differ: DifferLine? = null,
        /** For the Qibla card: pure geometry from the location, no sensor involved. */
        val qiblaBearingDegrees: Double,
        val qiblaDistanceKm: Double,
    ) : TodayUiState
}

/** The other school's Asr, "Standard 16:24" under a Hanafi Asr (spec §2.1). */
data class OtherAsr(val school: AsrSchool, val instant: Instant)

/**
 * Where timetables differ (spec §2.1): the earliest listed timetable's start of the next prayer,
 * shown under the [under] row. Before it: "{member}'s {prayer} begins at {memberStart}"; once it
 * has [begun]: "{member}'s {prayer} has begun · Taqwa shows it from {shownStart}".
 */
data class DifferLine(
    val under: Prayer,
    val prayer: Prayer,
    val memberNameKey: String,
    val memberStart: Instant,
    val shownStart: Instant,
    val begun: Boolean,
)

class TodayViewModel(
    private val engine: PrayerTimesEngine,
    private val settings: SettingsRepository,
    private val locationOf: suspend () -> GeoLocation?,
    private val now: () -> Instant,
    // Null means "no city lookup": the header then shows the stored English name, which is what
    // it did before this existed and what the tests that care only about times want.
    private val cityRepository: CityRepository? = null,
    // The device's own formatter in the app; the locale-free English one by default, so these
    // tests read the same on a machine whose system language is Arabic.
    private val format: PlatformFormat = EnglishPlatformFormat,
    // Both are called lazily rather than resolved once, so nothing platform-specific is touched
    // until a refresh actually has something new to publish — and so a test can count the writes
    // and the refreshes this loop provokes.
    private val widgetStore: () -> KeyValueStore = { createWidgetKeyValueStore() },
    private val widgetFormat: () -> PlatformFormat = { world.taqwa.app.i18n.createPlatformFormat() },
    private val onWidgetsChanged: () -> Unit = { refreshWidgets() },
    /**
     * Called once the one-time backfill has written a location's country or region, which can
     * change the timetable its times follow: the app then refreshes its notifications and the
     * widgets' mirror (ruling R53). Nothing by default, for the tests about the screen alone.
     */
    private val onLocationBackfilled: suspend () -> Unit = {},
    /**
     * Whether the phone's clock is set by hand, null where the platform cannot tell (the clock
     * line, spec §3.9). The app passes the platform's reading; nothing by default, for the tests.
     */
    private val clockSetByHand: () -> Boolean? = { null },
    /** The offset the phone's own zone data gives a zone at an instant; stale data is the point. */
    private val zoneOffsetSeconds: (zoneId: String, at: Instant) -> Int = { id, at -> TimeZone.of(id).offsetAt(at).totalSeconds },
    /**
     * Whether a one-time card was answered this launch. The app passes the process's own, which
     * outlives this object across a language change; a fresh one by default, for the tests.
     */
    private val cardSession: OneTimeCardSession = OneTimeCardSession(),
    /**
     * What the screen shows until the first [refresh] lands. `Loading` on a cold start, and — on
     * a language change, which swaps this whole object for one holding a format that reports the
     * new language — the state the outgoing one last published. Without it the Prayer screen
     * emptied to its spinner for the length of one refresh every time the phone's language
     * changed; with it the same rows simply re-render in the new language.
     */
    initialState: TodayUiState = TodayUiState.Loading,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    /**
     * The last string actually written to the widget mirror, so [refresh] can tell a tick that
     * changed something a widget can show from the ~59 ticks a minute that changed nothing.
     *
     * Without this, [start]'s one-second loop rewrote the mirror and nudged both widget systems
     * 60 times a minute. On iOS that outran `WidgetCenter`'s ~40-70 reloads/day budget in under a
     * minute and the widget then stopped updating for the rest of the day; on Android it put two
     * Glance recompositions plus `AppWidgetManager` IPC on the caller's dispatcher every second.
     * Every field in the snapshot is now minute-granular (see `WidgetMirrorWriter.RING_STEPS`),
     * so comparing the serialised form collapses those 60 ticks to one.
     */
    private var lastWrittenMirror: String? = null

    /**
     * The last name resolved, with what it was resolved from. The tick runs once a second and the
     * answer only changes when the city or the language does, so this keeps ~59 of every 60 ticks
     * from touching the city repository at all.
     */
    private var cachedNameFor: Pair<Int, String>? = null
    private var cachedName: String? = null

    /**
     * Whether the one-time id backfill has been attempted this run — set before the lookup, not
     * after, so a location that matches no bundled city costs exactly one scan per app run rather
     * than one per second.
     */
    private var cityIdMigrationAttempted = false

    /**
     * The id [migrateCityId] wrote back, held until the store catches up. `settings.location` is
     * a DataStore flow: the tick that writes the id is followed by ticks that still read the
     * location without one, and until DataStore emits the new value the header would fall back to
     * the English snapshot — an upgraded install flickering Arabic → English → Arabic once.
     */
    private var migratedCityId: Int? = null

    /** The cautious members' own days for one date, for the differ line; computed once a day. */
    private var memberDays: Pair<MemberDaysKey, List<PrayerDay>>? = null

    private data class MemberDaysKey(val entryId: String, val point: GeoPoint, val date: LocalDate, val school: AsrSchool, val hijriOffsetDays: Int)

    /**
     * What the two stored display-name keys hold, as far as this run knows: read once on the
     * first [refresh] and updated by every write this run makes. Its purpose is the write side —
     * a tick that resolves the same name again must not touch the store.
     */
    private var storedName: ResolvedCityName? = null
    private var storedNameRead = false

    /**
     * Runs the one-second tick until the calling coroutine is cancelled. No owned scope and no
     * `launch` of its own: the caller (`repeatOnLifecycle(STARTED) { … }` in `App.kt`) is what
     * ties this to the screen actually being on screen, not merely composed.
     *
     * `LaunchedEffect` alone was not enough — Android stops an activity (screen off, Home
     * pressed) without destroying it, so the composable, and the effect, stayed alive and this
     * loop kept writing the widget mirror once a minute for as long as the process lived.
     * `repeatOnLifecycle` cancels the block on STOP and restarts it fresh on the next START,
     * which is why the first thing this does on every (re)entry is an immediate [refresh] rather
     * than waiting out the first second — the screen must be correct the instant it reappears.
     */
    suspend fun tickWhileActive() {
        while (true) {
            refresh()
            // The timeline is time-dependent: a pip must fill and a row must dim the moment a
            // prayer arrives, with no pull-to-refresh.
            delay(1_000)
        }
    }

    /** Which call to [refresh] is the latest; see the check inside it. */
    private var latestRefresh = 0

    suspend fun refresh() {
        val ticket = ++latestRefresh
        val location = locationOf()
        if (location == null) {
            _state.value = TodayUiState.NeedsLocation
            return
        }
        // Before the state below is built, because it is what that state's header prints on a
        // cold start. One store read, on the first refresh only.
        seedStoredCityName(location)
        val prefs = settings.prayerSettings.first()
        val cautiousSeen = settings.cautiousCardSeen.first()
        val sunniSeen = settings.sunniCardSeen.first()
        // The reads above suspend on real I/O, and a refresh started later can come out of
        // them first. This one then holds a location the screen has since left, and publishing
        // it would put the old city's header over the new one until the next tick — which is
        // exactly what happened on iOS, where the reads finish in the other order.
        if (ticket != latestRefresh) return
        val zone = TimeZone.of(location.timeZoneId)
        val instant = now()
        val localDate = instant.toLocalDateTime(zone).date

        val yesterday = engine.timesFor(location, localDate.plus(-1, DateTimeUnit.DAY), prefs)
        // The engine's whole answer for today, with the resolution behind it; the adapter's view
        // of the same cached day is what the timeline reads.
        val todayDay = engine.dayFor(location, localDate, prefs)
        val today = engine.timesFor(location, localDate, prefs)
        val tomorrow = engine.timesFor(location, localDate.plus(1, DateTimeUnit.DAY), prefs)

        // The offset is applied to the Gregorian date before conversion, never to the Hijri day
        // number — shifting the Hijri day directly can produce day 0 or day 31.
        val hijri = TabularHijriCalendar.fromGregorian(
            localDate.plus(prefs.hijriOffsetDays, DateTimeUnit.DAY),
        )

        val timeline = TimelineBuilder.build(yesterday, today, tomorrow, instant, prefs.showSunrise, zone)
        val whose = WhoseTimes.of(todayDay)
        // The widgets keep today's highlight until the Ramadan release (ruling R8), so their mirror
        // is written from their own timeline, not the screen's.
        val widgetTimeline = TimelineBuilder.buildForWidgets(yesterday, today, tomorrow, instant, prefs.showSunrise, zone)
        val card = OneTimeCards.pick(
            cautiousPlace = whose.cautious,
            shiaRegion = todayDay.resolution.shiaRegion,
            cautiousSeen = cautiousSeen,
            sunniSeen = sunniSeen,
            shownThisLaunch = cardSession.shown,
            answeredThisLaunch = cardSession.answered,
        )
        // Shown now for the first time: remembered for this launch before anything suspends, so a
        // tick racing this one cannot pick a second card, and stored as seen below (once-only).
        val firstShown = card?.takeIf { cardSession.shown == null }
        if (firstShown != null) cardSession.shown = firstShown
        _state.value = TodayUiState.Ready(
            location = location,
            cityDisplayName = cachedCityName(location),
            hijri = HijriFormatter.format(hijri, format),
            gregorian = format.longDate(localDate),
            today = timeline,
            whose = whose,
            setByRule = if (today.polar) emptySet() else today.setByRule,
            polar = today.polar,
            oneTimeCard = card,
            clockWarning = ClockChanges.warningFor(
                zoneId = location.timeZoneId,
                now = instant,
                phoneOffsetSeconds = zoneOffsetSeconds(location.timeZoneId, instant),
                clockSetByHand = clockSetByHand(),
            ),
            otherAsr = if (prefs.showBothAsr) OtherAsr(todayDay.school.other, today.asrOther) else null,
            differ = if (prefs.showWhereDiffer && whose.cautious) {
                differLine(location, zone, localDate, prefs, timeline, instant, listOf(yesterday, today, tomorrow))
            } else {
                null
            },
            qiblaBearingDegrees = QiblaMath.bearing(location),
            qiblaDistanceKm = QiblaMath.distanceKm(location),
        )
        if (firstShown != null) markSeen(firstShown)
        // Serialise first, then compare: the store write and the widget nudge are both skipped
        // when this tick produced a mirror identical to the last one published.
        val mirror = WidgetMirrorWriter.serializedSnapshot(
            today = widgetTimeline,
            timeZoneId = location.timeZoneId,
            format = widgetFormat(),
            days = listOf(today, tomorrow),
            yesterday = yesterday,
        )
        if (mirror != lastWrittenMirror) {
            lastWrittenMirror = mirror
            widgetStore().putString(WidgetInputsMirror.KEY, mirror)
            onWidgetsChanged()
        }
        // Both last, deliberately: the state above is already published, so neither the first
        // frame nor any later tick waits on the city list being parsed or scanned. Each one
        // republishes the header's name itself if it ends up with a better one.
        resolveCityName(location)
        migrateCityId(location)
    }

    /**
     * The differ line for the next start (spec §2.1), or null: when the day it belongs to is not
     * cautious, when every listed timetable begins it together, or when there is no row to put it
     * under. It goes under the current prayer; with none current (before Fajr, after sunrise), under
     * the next prayer's own row when that is today's.
     */
    private fun differLine(
        location: GeoLocation,
        zone: TimeZone,
        localDate: LocalDate,
        prefs: PrayerSettings,
        timeline: TodayState,
        now: Instant,
        days: List<DayPrayerTimes>,
    ): DifferLine? {
        val next = timeline.next
        val nextDay = days.firstOrNull { day -> day.times.any { it.prayer == next.prayer && it.instant == next.instant } }
            ?: return null
        val engineDay = engine.dayFor(location, nextDay.date, prefs)
        if (engineDay.effective.entryClass != EntryClass.C) return null
        val earliest = engineDay.day.earliestStart[next.prayer] ?: return null
        if (earliest >= next.instant) return null
        val member = memberBeginning(engineDay, nextDay.date, zone, prefs, next.prayer, earliest) ?: return null
        val under = timeline.rows.firstOrNull { it.status == PrayerStatus.CURRENT }?.prayer
            ?: next.prayer.takeIf { nextDay.date == localDate && timeline.rows.any { it.prayer == next.prayer } }
            ?: return null
        return DifferLine(under, next.prayer, member.nameKey, earliest, next.instant, begun = now >= earliest)
    }

    /**
     * The listed timetable that begins [prayer] at [at], the most-followed where several do: the
     * members' own days as the engine combined them ([DayPipeline.members]).
     */
    private fun memberBeginning(
        day: EngineDay,
        date: LocalDate,
        zone: TimeZone,
        prefs: PrayerSettings,
        prayer: Prayer,
        at: Instant,
    ): Member? {
        val resolution = day.effective
        val key = MemberDaysKey(resolution.entry.id, resolution.point, date, day.school, prefs.hijriOffsetDays)
        val days = memberDays?.takeIf { it.first == key }?.second
            ?: DayPipeline.members(resolution, date, zone, day.school, prefs.hijriOffsetDays).also { memberDays = key to it }
        return resolution.members.indices
            .filter { it < days.size && days[it].startOf(prayer) == at }
            .minByOrNull { resolution.members[it].shareRank }
            ?.let { resolution.members[it] }
    }

    private fun PrayerDay.startOf(prayer: Prayer): Instant = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    /**
     * Answers a one-time card (spec §2.1): it goes at once, and no other card shows until the next
     * launch. It was stored as seen when it was first shown; storing it again costs nothing and
     * covers a card answered before that write landed.
     */
    suspend fun answerOneTimeCard(card: OneTimeCard) {
        cardSession.answered = true
        val current = _state.value
        if (current is TodayUiState.Ready && current.oneTimeCard != null) {
            _state.value = current.copy(oneTimeCard = null)
        }
        markSeen(card)
    }

    /** A once-only card (decision §10.9) is seen once shown, answered or not. */
    private suspend fun markSeen(card: OneTimeCard) = when (card) {
        OneTimeCard.CAUTIOUS -> settings.setCautiousCardSeen(true)
        OneTimeCard.SUNNI -> settings.setSunniCardSeen(true)
    }

    /**
     * Hands [cachedCityName] the answer the last run already worked out, so the first frame of a
     * cold start carries the reader's own name for the city instead of the English snapshot.
     *
     * This is the whole point of the two stored keys. The city list is parsed off the first frame
     * deliberately — the prayer times must not wait behind 1.6 MB of CSV — which used to mean the
     * header showed "London" for ~0.4 s before «لندن» replaced it under the reader.
     *
     * **Only when the stored language is the one on screen now.** A reader who has changed
     * language since has a name here in the old one, and printing that would be worse than the
     * flicker; that launch falls back to [GeoLocation.cityName] and swaps when the lookup lands,
     * as every launch used to. One flicker after a language change, none otherwise.
     */
    private suspend fun seedStoredCityName(location: GeoLocation) {
        if (storedNameRead) return
        storedNameRead = true
        val stored = settings.resolvedCityName.first() ?: return
        storedName = stored
        val id = cityIdOf(location) ?: return
        if (stored.languageTag != format.languageTag()) return
        cachedName = stored.name
        cachedNameFor = id to stored.languageTag
    }

    /**
     * Writes the header's name back for the next cold start, and only when it is genuinely new:
     * the tick runs once a second, and this compares against what the store is known to hold
     * rather than editing DataStore sixty times a minute to rewrite the same two strings.
     */
    private suspend fun rememberCityName(resolved: ResolvedCityName) {
        if (storedName == resolved) return
        storedName = resolved
        settings.setResolvedCityName(resolved)
    }

    /**
     * The header's string with no I/O: the resolved name when this exact city and language were
     * resolved already — including when [seedStoredCityName] supplied it from the store — and the
     * stored English snapshot until then. Nothing here can suspend, so every tick, the first one
     * above all, publishes its state immediately.
     */
    private fun cachedCityName(location: GeoLocation): String? {
        val id = cityIdOf(location) ?: return location.cityName
        return if (cachedNameFor == id to format.languageTag()) {
            cachedName ?: location.cityName
        } else {
            location.cityName
        }
    }

    /**
     * Looks the header's name up in the reader's language and republishes it when it differs from
     * what the tick above already showed. A no-op on all but the first tick after the city or the
     * language changes, because [cachedCityName] then already has the answer.
     *
     * The language is read from [format] on every call rather than captured once: a language
     * change recreates the Android activity and restarts the iOS app, but even without that, the
     * next tick simply asks again and the cache key stops matching.
     */
    private suspend fun resolveCityName(location: GeoLocation) {
        val repository = cityRepository ?: return
        val id = cityIdOf(location) ?: return
        val language = format.languageTag()
        if (cachedNameFor != id to language) {
            repository.setLanguage(language)
            cachedName = repository.displayName(id)
            cachedNameFor = id to language
        }
        val resolved = cachedName ?: location.cityName
        val current = _state.value
        // Only when the state on screen is still the one this lookup was started for. The lookup
        // suspends on a 1.6 MB parse; a city picked, or a fix taken, while it was running has
        // already published a state about somewhere else, and republishing this name over it
        // would put the old city's name under the new city's times.
        if (current !is TodayUiState.Ready || current.location != location) return
        if (current.cityDisplayName != resolved) {
            _state.value = current.copy(cityDisplayName = resolved)
        }
        // Behind the same guard, and for the same reason: a name resolved for a city the screen
        // has already left must not be stored as this location's, or the next cold start would
        // open on the old city's name.
        if (resolved != null) rememberCityName(ResolvedCityName(resolved, language))
    }

    /**
     * The id to name the header from: the stored one, or — for the one run in which the backfill
     * has written an id the location flow has not re-emitted yet — the one just written.
     */
    private fun cityIdOf(location: GeoLocation): Int? = location.cityId ?: migratedCityId

    /**
     * Gives a location stored by an older build what its coordinates imply and it lacks, once: the
     * city id (for the header's name), and the country and first-level region the prayer engine
     * resolves the timetable by (spec §8, ruling R32; until then the engine reads the country from
     * the zone and picks a region's unit by distance).
     *
     * Here rather than in `SettingsRepository`, because this is the one place that has the stored
     * location, the city list and a coroutine to suspend in all at once, and it already runs on
     * every entry to the Prayer screen. `SettingsRepository.location` is a cold `Flow` mapped from
     * DataStore — it cannot suspend on a 1.6 MB parse, and doing the lookup there would run it for
     * every collector, including the notification scheduler.
     *
     * A location whose coordinates match no bundled city — only possible when the list itself is
     * empty — keeps a null id and goes on showing [GeoLocation.cityName]. One whose city the list
     * gives no region (Singapore, Hong Kong …) keeps none, and the stored done flag stops the scan
     * from running again on every launch.
     */
    private suspend fun migrateCityId(location: GeoLocation) {
        val repository = cityRepository ?: return
        val complete = location.cityId != null && location.countryCode != null && location.region != null
        if (cityIdMigrationAttempted || complete) return
        cityIdMigrationAttempted = true
        if (location.cityId != null && settings.locationBackfilled.first()) return
        // The location's own city when it names one (its coordinates are rounded to 3 decimals, and
        // a neighbouring city can be nearer to them), else the nearest.
        val city = location.cityId?.let { repository.byId(it) }
            ?: repository.nearest(location.latitude, location.longitude)
            ?: return
        // Only what is missing, and only while the stored coordinates are still these, so a city
        // picked or a fix taken while the scan was running is not undone.
        if (settings.backfillLocation(location, city.id, city.countryCode, city.region)) onLocationBackfilled()
        if (location.cityId != null) return
        val id = city.id
        migratedCityId = id
        val migrated = location.copy(cityId = id)
        val current = _state.value
        if (current is TodayUiState.Ready && current.location == location) {
            _state.value = current.copy(location = migrated)
            // Now that there is an id, the name it stands for — this is the first tick on which
            // an upgraded install can show the header in the reader's own language.
            resolveCityName(migrated)
        }
    }

}
