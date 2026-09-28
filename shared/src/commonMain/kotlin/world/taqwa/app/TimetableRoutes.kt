package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.settings.AUTOMATIC_TIMETABLE
import world.taqwa.app.feature.settings.ChoiceInProgress
import world.taqwa.app.feature.settings.MatchCheck
import world.taqwa.app.feature.settings.MatchMyMosqueScreen
import world.taqwa.app.feature.settings.MatchResult
import world.taqwa.app.feature.settings.MosqueCandidate
import world.taqwa.app.feature.settings.MosqueMatch
import world.taqwa.app.feature.settings.MosqueMatcher
import world.taqwa.app.feature.settings.OtherMethodsScreen
import world.taqwa.app.feature.settings.PendingWarning
import world.taqwa.app.feature.settings.TimetableCheck
import world.taqwa.app.feature.settings.TimetableOption
import world.taqwa.app.feature.settings.TimetableChooser
import world.taqwa.app.feature.settings.TimetableScreen
import world.taqwa.app.feature.settings.TimetableStatus
import world.taqwa.app.feature.settings.checkAgainstAutomatic
import world.taqwa.app.feature.settings.nearbyEntries
import world.taqwa.app.feature.settings.timetableRowIds
import world.taqwa.app.feature.settings.ownTimes
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Instant

/*
 * Settings › Prayer times › Timetable, its Other methods, and Match my mosque (spec §2.2; rulings
 * R50, R52, R53). A choice is checked against Automatic over the next 12 months off the main
 * thread, in the screen's own scope, so leaving the screen stops it; what is chosen is written in
 * the app's scope, so the write and the refresh after it (ruling R53) finish after the screen has
 * gone.
 */

/**
 * The earlier-than check for entry [id] at [location] from [today] (spec §2.2): its own day
 * against Automatic's on every day of the next 12 months, on `Dispatchers.Default`.
 */
internal suspend fun checkTimetable(
    engine: PrayerTimesEngine,
    location: GeoLocation,
    settings: PrayerSettings,
    today: LocalDate,
    id: String,
    onProgress: (Float) -> Unit,
): TimetableCheck = withContext(Dispatchers.Default) {
    // Both days are in the school the user's day is in (Automatic's or the one chosen), so their
    // asrOther is the other school's, which the warning names.
    val otherSchool = engine.dayFor(location, today, settings).school.other
    checkAgainstAutomatic(
        from = today,
        days = { date ->
            val own = engine.ownDay(location, date, settings, id)
            val automatic = engine.ownDay(location, date, settings, AUTOMATIC_TIMETABLE)
            if (own == null || automatic == null) null else own to automatic
        },
        onProgress = onProgress,
        otherSchool = otherSchool,
    )
}

/** Remembers a [TimetableChooser] whose writes run in [appScope] and end with [done]. */
@Composable
private fun rememberTimetableChooser(
    location: GeoLocation,
    prayerSettings: PrayerSettings,
    today: LocalDate,
    appScope: CoroutineScope,
    settings: SettingsRepository,
    container: AppContainer,
    done: () -> Unit,
): TimetableChooser {
    val format = LocalPlatformFormat.current
    val screenScope = rememberCoroutineScope()
    return remember(location, prayerSettings, today) {
        TimetableChooser(
            checkScope = screenScope,
            check = { id, onProgress -> checkTimetable(container.prayerTimesEngine, location, prayerSettings, today, id, onProgress) },
            nameKeyOf = { id -> Registry.byId(id)?.shortNameKey ?: id },
            apply = { id, confirmed ->
                appScope.launch {
                    // Bound to this place's Automatic (ruling R70): abroad it waits to be confirmed again.
                    settings.chooseTimetable(id, if (confirmed) container.prayerTimesEngine.automaticEntryId(location) else null)
                    refreshForNewTimes(container, settings, format)
                }
                done()
            },
        )
    }
}

/**
 * Today's own times of each of [ids] at [location], off the main thread, each under its name
 * ([automaticNameKey] for Automatic); empty until they are known.
 */
@Composable
private fun rememberOwnTimes(
    engine: PrayerTimesEngine,
    location: GeoLocation,
    settings: PrayerSettings,
    today: LocalDate,
    ids: List<String>,
    automaticNameKey: String,
): State<Map<String, TimetableOption>> = produceState(emptyMap(), location, settings, today, ids, automaticNameKey) {
    value = withContext(Dispatchers.Default) {
        ids.associateWith { id ->
            val nameKey = if (id == AUTOMATIC_TIMETABLE) automaticNameKey else Registry.byId(id)?.shortNameKey ?: id
            TimetableOption(id, nameKey, engine.ownDay(location, today, settings, id)?.ownTimes())
        }
    }
}

/** The nearby entries that apply at [location], by id, from the engine's own resolution of it. */
private fun nearbyIds(location: GeoLocation, day: EngineDay): List<String> =
    nearbyEntries(day.resolution.entry, PrayerTimesEngine.placeOf(location)).map { it.id }

@Composable
internal fun TimetableRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    container: AppContainer,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location = locationState.value
    if (location == null) {
        // A timetable belongs to a place; with none there is nothing to list or check.
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    val engine = container.prayerTimesEngine
    val day = engine.dayFor(location, today, prayerSettings)
    val status = TimetableStatus.of(prayerSettings, day)
    // The stored choice keeps a row of its own where it applies here but is not listed nearby (review M8).
    val nearby = remember(location, day.resolution.entry.id, prayerSettings.timetable) {
        timetableRowIds(day.resolution.entry, PrayerTimesEngine.placeOf(location), prayerSettings.timetable)
    }
    val times by rememberOwnTimes(engine, location, prayerSettings, today, listOf(AUTOMATIC_TIMETABLE) + nearby, day.resolution.entry.shortNameKey)
    val chooser = rememberTimetableChooser(location, prayerSettings, today, scope, settings, container) { navigator.pop() }
    TimetableScreen(
        status = status,
        automaticAbout = day.resolution.about,
        automatic = times[AUTOMATIC_TIMETABLE] ?: TimetableOption(AUTOMATIC_TIMETABLE, day.resolution.entry.shortNameKey),
        nearby = nearby.map { id -> times[id] ?: TimetableOption(id, Registry.byId(id)?.shortNameKey ?: id) },
        zone = TimeZone.of(location.timeZoneId),
        checking = chooser.checking,
        warning = chooser.warning,
        onChoose = { id ->
            // The timetable already followed, confirmed and in scope: nothing to change.
            if (id == status.selectedId && !status.paused && !status.outOfScope) navigator.pop() else chooser.choose(id)
        },
        onKeepAutomatic = chooser::keep,
        onFollow = chooser::follow,
        onOpenOtherMethods = { navigator.push(Screen.OtherMethods) },
        onOpenMatch = { navigator.push(Screen.MatchMyMosque) },
        onBack = { navigator.pop() },
    )
}

@Composable
internal fun OtherMethodsRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    container: AppContainer,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location = locationState.value
    if (location == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    val engine = container.prayerTimesEngine
    val day = engine.dayFor(location, today, prayerSettings)
    val status = TimetableStatus.of(prayerSettings, day)
    val ids = remember { Registry.otherMethods.map { it.id } }
    val times by rememberOwnTimes(engine, location, prayerSettings, today, ids, day.resolution.entry.shortNameKey)
    // Chosen here, the way back is to Prayer times, past the Timetable list this was opened from.
    val chooser = rememberTimetableChooser(location, prayerSettings, today, scope, settings, container) {
        navigator.pop()
        if (navigator.current == Screen.Timetable) navigator.pop()
    }
    OtherMethodsScreen(
        status = status,
        methods = ids.map { id -> times[id] ?: TimetableOption(id, Registry.byId(id)?.shortNameKey ?: id) },
        zone = TimeZone.of(location.timeZoneId),
        checking = chooser.checking,
        warning = chooser.warning,
        onChoose = { id ->
            if (id == status.selectedId && !status.paused) {
                navigator.pop()
                if (navigator.current == Screen.Timetable) navigator.pop()
            } else {
                chooser.choose(id)
            }
        },
        onKeepAutomatic = chooser::keep,
        onFollow = chooser::follow,
        onBack = { navigator.pop() },
    )
}

@Composable
internal fun MatchMyMosqueRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    container: AppContainer,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location = locationState.value
    if (location == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    val engine = container.prayerTimesEngine
    val format = LocalPlatformFormat.current
    val zone = TimeZone.of(location.timeZoneId)
    val day = engine.dayFor(location, today, prayerSettings)
    val status = TimetableStatus.of(prayerSettings, day)

    // Automatic's own first (a board that follows it needs nothing changed), then the timetables
    // used here, then the Other methods (spec §2.2).
    val ids = remember(location, day.resolution.entry.id) {
        (listOf(AUTOMATIC_TIMETABLE) + nearbyIds(location, day) + Registry.otherMethods.map { it.id }).distinct()
    }
    val times by rememberOwnTimes(engine, location, prayerSettings, today, ids, day.resolution.entry.shortNameKey)
    var fajrText by remember { mutableStateOf("") }
    var ishaText by remember { mutableStateOf("") }

    val fajr = MosqueMatcher.parseBoardTime(fajrText)
    val isha = MosqueMatcher.parseBoardTime(ishaText)
    val loaded = times.size == ids.size
    val options = ids.mapNotNull { times[it] }.filter { it.today != null }
    val match = if (fajr != null && isha != null && loaded) {
        MosqueMatcher.match(
            fajr,
            isha,
            options.map { MosqueCandidate(it.id, minuteOfDay(it.today!!.fajr, zone), minuteOfDay(it.today.isha, zone)) },
        )
    } else {
        null
    }
    val matchedId = when (match) {
        is MosqueMatch.Both -> match.id
        is MosqueMatch.HourApart -> match.id
        else -> null
    }
    val inUse = matchedId != null && matchedId == status.selectedId && !status.paused && !status.outOfScope
    // The earlier-than check of the timetable that matches, as it appears; a new match cancels it.
    val check by produceState<MatchCheck?>(null, matchedId, inUse, prayerSettings) {
        value = when {
            matchedId == null -> null
            inUse -> MatchCheck.InUse(matchedId)
            matchedId == AUTOMATIC_TIMETABLE -> MatchCheck.Done(matchedId, null)
            else -> {
                value = MatchCheck.Checking(matchedId, 0f)
                // Progress comes from the check's background thread: posted to this scope (the
                // screen's), and never after the check is done (review M11).
                var done = false
                val found = checkTimetable(engine, location, prayerSettings, today, matchedId) { progress ->
                    launch { if (!done) value = MatchCheck.Checking(matchedId, progress) }
                }
                done = true
                MatchCheck.Done(matchedId, (found as? TimetableCheck.Earlier)?.finding)
            }
        }
    }

    val named = when (match) {
        is MosqueMatch.Both -> times[match.id]
        is MosqueMatch.HourApart -> times[match.id]
        is MosqueMatch.OneTime -> times[match.id]
        else -> null
    }
    MatchMyMosqueScreen(
        todayLabel = format.longDate(today),
        fajrText = fajrText,
        ishaText = ishaText,
        onFajrText = { fajrText = it },
        onIshaText = { ishaText = it },
        result = match?.let { MatchResult(it, named, options, check) },
        automatic = status.automatic,
        zone = zone,
        onUse = {
            val id = matchedId ?: return@MatchMyMosqueScreen
            // Only once this timetable's own check is done, never on a check left from another match.
            if ((check as? MatchCheck.Done)?.id != id) return@MatchMyMosqueScreen
            // "Use this timetable" after the warning is "Follow it": the choice is confirmed.
            scope.launch {
                settings.chooseTimetable(id, if (id != AUTOMATIC_TIMETABLE) engine.automaticEntryId(location) else null)
                refreshForNewTimes(container, settings, format)
            }
            navigator.pop()
        },
        onOpenManualAdjustments = { navigator.push(Screen.ManualAdjustments) },
        onBack = { navigator.pop() },
    )
}

private fun minuteOfDay(instant: Instant, zone: TimeZone): Int =
    instant.toLocalDateTime(zone).let { it.hour * 60 + it.minute }
