package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adjust_confirm_body_maghrib
import world.taqwa.app.resources.adjust_confirm_title
import world.taqwa.app.resources.adjust_keep
import world.taqwa.app.resources.adjust_use
import world.taqwa.app.resources.timetable_follow_it
import world.taqwa.app.resources.timetable_keep_current
import world.taqwa.app.resources.timetable_keep_automatic

/*
 * Previews of every state of Settings › Prayer times, Timetable, Other methods, Match my mosque and
 * Manual adjustments (mockup section 3), on 1 October 2026: London, Toronto and Riyadh as the
 * mockups have them. The times are the mockup's, not the engine's; the screens only draw them.
 */

private val day = LocalDate(2026, 10, 1)
private val londonZone = TimeZone.of("Europe/London")

private fun t(h: Int, m: Int, zone: TimeZone = londonZone) = day.atTime(h, m).toInstant(zone)

private fun times(fajr: Pair<Int, Int>, dhuhr: Pair<Int, Int>, asr: Pair<Int, Int>, maghrib: Pair<Int, Int>, isha: Pair<Int, Int>) =
    OwnTimes(t(fajr.first, fajr.second), t(dhuhr.first, dhuhr.second), t(asr.first, asr.second), t(maghrib.first, maghrib.second), t(isha.first, isha.second))

private val lupt = TimetableName("authority_london_unified", SourceKind.AUTHORITY)

private val londonStatus = TimetableStatus(
    automatic = lupt, chosenId = null, chosenNameKey = null, outOfScope = false, paused = false,
    effective = lupt, effectiveId = "gb.london.lupt", saudiFajr = false, highLatitude = false, whereDiffer = false,
    school = AsrSchool.HANAFI, schoolKnown = false,
)

private val torontoStatus = londonStatus.copy(
    automatic = TimetableName("timetable_cautious", SourceKind.CAUTIOUS),
    effective = TimetableName("timetable_cautious", SourceKind.CAUTIOUS),
    effectiveId = "ca.toronto",
    whereDiffer = true,
)

private val riyadhStatus = londonStatus.copy(
    automatic = TimetableName("authority_umm_al_qura", SourceKind.AUTHORITY),
    effective = TimetableName("authority_umm_al_qura", SourceKind.AUTHORITY),
    effectiveId = "sa.ummalqura",
    saudiFajr = true,
    school = AsrSchool.STANDARD,
    schoolKnown = true,
)

private val luptToday = TimetableOption(AUTOMATIC_TIMETABLE, "authority_london_unified", times(5 to 30, 12 to 57, 16 to 1, 18 to 42, 20 to 0))
private val diyanetLondon = TimetableOption("gb.diyanet", "authority_diyanet_europe", times(5 to 9, 12 to 55, 16 to 1, 18 to 47, 20 to 18))
private val wifaqul = TimetableOption("gb.wifaqul", "authority_wifaqul_ulama", times(5 to 10, 12 to 56, 16 to 2, 18 to 44, 20 to 10))
private val mwl = TimetableOption("other.mwl", "method_muslim_world_league", times(5 to 16, 12 to 55, 15 to 59, 18 to 41, 20 to 14))
private val isna = TimetableOption("other.isna", "method_isna", times(5 to 35, 12 to 55, 15 to 59, 18 to 41, 19 to 55))

@Composable
private fun Frame(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTaqwaColors provides if (dark) DarkColors else LightColors) {
        Box(Modifier.background(LocalTaqwaColors.current.background)) { content() }
    }
}

@Composable
private fun PrayerTimes(settings: PrayerSettings, status: TimetableStatus?, placeName: String?, adjustmentsPaused: Boolean = false) =
    PrayerTimesSettingsScreen(
        settings = settings, status = status, adjustmentsPaused = adjustmentsPaused, placeName = placeName, today = day,
        onSchool = {}, onHijriOffset = {}, onShowSunrise = {}, onShowBothAsr = {}, onShowWhereDiffer = {}, onSaudiFajrLater = {},
        onBack = {}, onOpenTimetable = {}, onOpenHighLatitudePicker = {}, onOpenManualAdjustments = {}, onOpenAboutTimes = {},
    )

// Settings › Prayer times

@Preview
@Composable
private fun PrayerTimesTorontoPreview() = Frame { PrayerTimes(PrayerSettings(), torontoStatus, "Toronto") }

@Preview
@Composable
private fun PrayerTimesRiyadhPreview() = Frame { PrayerTimes(PrayerSettings(), riyadhStatus, "Riyadh") }

@Preview
@Composable
private fun PrayerTimesOtherMethodPausedPreview() = Frame {
    PrayerTimes(
        PrayerSettings(timetable = "other.mwl", legacyHighLatitude = HighLatitudePreference.SEVENTH_OF_NIGHT, minuteAdjustments = mapOf(Prayer.MAGHRIB to -2)),
        londonStatus.copy(chosenId = "other.mwl", chosenNameKey = "method_muslim_world_league", paused = true, highLatitude = true),
        "London",
        adjustmentsPaused = true,
    )
}

@Preview
@Composable
private fun PrayerTimesOutOfScopePreview() = Frame {
    PrayerTimes(
        PrayerSettings(timetable = "sa.ummalqura", school = "standard"),
        londonStatus.copy(chosenId = "sa.ummalqura", chosenNameKey = "authority_umm_al_qura", outOfScope = true),
        "London",
    )
}

@Preview
@Composable
private fun PrayerTimesNoLocationDarkPreview() = Frame(dark = true) { PrayerTimes(PrayerSettings(school = "hanafi"), null, null) }

// Timetable

@Composable
private fun Timetable(status: TimetableStatus, about: AboutTemplate, automatic: TimetableOption, nearby: List<TimetableOption>, checking: ChoiceInProgress? = null) =
    TimetableScreen(
        status = status, automaticAbout = about, automatic = automatic, nearby = nearby, zone = londonZone, checking = checking,
        warning = null, onChoose = {}, onKeepAutomatic = {}, onFollow = {}, onOpenOtherMethods = {}, onOpenMatch = {}, onBack = {},
    )

@Preview
@Composable
private fun TimetableLondonPreview() = Frame { Timetable(londonStatus, AboutTemplate.AUTHORITY_CHECKED, luptToday, emptyList()) }

@Preview
@Composable
private fun TimetableNearbyCheckingPreview() = Frame {
    Timetable(torontoStatus, AboutTemplate.CAUTIOUS, luptToday.copy(nameKey = "timetable_cautious"), listOf(wifaqul, diyanetLondon), ChoiceInProgress("gb.diyanet", 0.4f))
}

@Preview
@Composable
private fun TimetableNearbyPausedPreview() = Frame {
    Timetable(londonStatus.copy(chosenId = "gb.wifaqul", chosenNameKey = "authority_wifaqul_ulama", paused = true), AboutTemplate.AUTHORITY_CHECKED, luptToday, listOf(wifaqul, diyanetLondon))
}

@Preview
@Composable
private fun TimetableOtherMethodOutOfScopePreview() = Frame {
    Timetable(londonStatus.copy(chosenId = "sa.ummalqura", chosenNameKey = "authority_umm_al_qura", outOfScope = true), AboutTemplate.AUTHORITY_CHECKED, luptToday, emptyList())
}

@Preview
@Composable
private fun TimetableCalculatedDarkPreview() = Frame(dark = true) {
    val calculated = TimetableName("timetable_calculated", SourceKind.TAQWA)
    Timetable(londonStatus.copy(automatic = calculated, effective = calculated), AboutTemplate.CALCULATED, luptToday.copy(nameKey = "timetable_calculated"), emptyList())
}

@Preview
@Composable
private fun EarlierWarningPreview() = Frame {
    ConfirmSheetContent(
        title = "Diyanet, London",
        body = earlierSentence(EarlierFinding(CheckedTime.FAJR, 21), lupt),
        safe = stringResource(Res.string.timetable_keep_automatic),
        other = stringResource(Res.string.timetable_follow_it),
        onSafe = {},
        onOther = {},
    )
}

@Preview
@Composable
private fun EarlierWarningFastPreview() = Frame {
    ConfirmSheetContent(
        title = "Muhammadiyah",
        body = earlierSentence(EarlierFinding(CheckedTime.END_OF_EATING, 10), TimetableName("authority_kemenag", SourceKind.AUTHORITY)),
        // Chosen while another timetable is followed: the button keeps that one, by name.
        safe = keepLabel(londonStatus.copy(chosenId = "other.mwl", chosenNameKey = "method_muslim_world_league")),
        other = stringResource(Res.string.timetable_follow_it),
        onSafe = {},
        onOther = {},
    )
}

@Preview
@Composable
private fun OtherMethodsPreview() = Frame {
    OtherMethodsScreen(
        status = londonStatus.copy(chosenId = "other.mwl", chosenNameKey = "method_muslim_world_league", highLatitude = true),
        methods = listOf(mwl, isna, TimetableOption("other.egyptian", "method_egyptian")),
        zone = londonZone, checking = ChoiceInProgress("other.isna", 0.7f), warning = null,
        onChoose = {}, onKeepAutomatic = {}, onFollow = {}, onBack = {},
    )
}

// High latitude rule of a confirmed Other method: the new rule is checked first.

@Preview
@Composable
private fun HighLatitudeRecheckingPreview() = Frame {
    HighLatitudePickerScreen(current = HighLatitudePreference.AUTOMATIC, onPick = {}, onBack = {}, checking = 0.6f)
}

@Preview
@Composable
private fun HighLatitudeWarningPreview() = Frame {
    ConfirmSheetContent(
        title = "Muslim World League",
        body = earlierSentence(EarlierFinding(CheckedTime.ISHA, 152), TimetableName("timetable_cautious", SourceKind.CAUTIOUS)),
        safe = stringResource(Res.string.timetable_keep_current, "Automatic"),
        other = stringResource(Res.string.timetable_follow_it),
        onSafe = {},
        onOther = {},
    )
}

// Match my mosque

@Composable
private fun Match(fajr: String, isha: String, result: MatchResult?) = MatchMyMosqueScreen(
    todayLabel = "1 October 2026", fajrText = fajr, ishaText = isha, onFajrText = {}, onIshaText = {}, result = result,
    automatic = lupt, zone = londonZone, onUse = {}, onOpenManualAdjustments = {}, onBack = {},
)

private val candidates = listOf(luptToday, wifaqul, diyanetLondon, mwl, isna)

@Preview
@Composable
private fun MatchEmptyPreview() = Frame { Match("", "", null) }

@Preview
@Composable
private fun MatchBothWithWarningPreview() = Frame {
    Match("509", "2018", MatchResult(MosqueMatch.Both("gb.diyanet", exact = true), diyanetLondon, candidates, MatchCheck.Done("gb.diyanet", EarlierFinding(CheckedTime.FAJR, 21))))
}

@Preview
@Composable
private fun MatchCheckingPreview() = Frame {
    Match("509", "2018", MatchResult(MosqueMatch.Both("gb.diyanet", exact = true), diyanetLondon, candidates, MatchCheck.Checking("gb.diyanet", 0.35f)))
}

@Preview
@Composable
private fun MatchAutomaticInUsePreview() = Frame {
    Match("530", "2000", MatchResult(MosqueMatch.Both(AUTOMATIC_TIMETABLE, exact = true), luptToday, candidates, MatchCheck.InUse(AUTOMATIC_TIMETABLE)))
}

@Preview
@Composable
private fun MatchOneTimePreview() = Frame {
    Match("509", "2040", MatchResult(MosqueMatch.OneTime("gb.diyanet", Prayer.FAJR, Prayer.ISHA), diyanetLondon, candidates, null))
}

@Preview
@Composable
private fun MatchHourApartPreview() = Frame {
    Match("409", "1918", MatchResult(MosqueMatch.HourApart("gb.diyanet"), diyanetLondon, candidates, MatchCheck.Done("gb.diyanet", null)))
}

@Preview
@Composable
private fun MatchNonePreview() = Frame { Match("430", "2145", MatchResult(MosqueMatch.None, null, candidates, null)) }

// Manual adjustments

private val adjustmentsToday = AdjustmentsState(
    clocks = mapOf(Prayer.FAJR to t(5, 23), Prayer.DHUHR to t(13, 1), Prayer.ASR to t(16, 22), Prayer.MAGHRIB to t(19, 1), Prayer.ISHA to t(20, 22)),
    zone = londonZone,
    source = TimetableName("authority_diyanet", SourceKind.AUTHORITY),
    effectiveId = "tr.diyanet",
    margins = mapOf(Prayer.FAJR to 1, Prayer.DHUHR to 1, Prayer.ASR to 1, Prayer.MAGHRIB to 1, Prayer.ISHA to 1),
)

@Preview
@Composable
private fun ManualAdjustmentsPreview() = Frame {
    ManualAdjustmentsScreen(
        adjustments = mapOf(Prayer.MAGHRIB to -2, Prayer.ISHA to 5),
        confirmed = mapOf(Prayer.MAGHRIB to "tr.diyanet"),
        state = adjustmentsToday,
        onSet = { _, _, _ -> }, onOpenTimetable = {}, onBack = {},
    )
}

@Preview
@Composable
private fun ManualAdjustmentsPausedPreview() = Frame {
    ManualAdjustmentsScreen(
        adjustments = mapOf(Prayer.MAGHRIB to -2, Prayer.FAJR to -3),
        confirmed = mapOf(Prayer.MAGHRIB to "tr.diyanet"),
        state = adjustmentsToday.copy(
            source = lupt,
            effectiveId = "gb.london.lupt",
            paused = setOf(Prayer.MAGHRIB, Prayer.FAJR),
            confirmedNameKeys = mapOf(Prayer.MAGHRIB to "authority_diyanet"),
        ),
        onSet = { _, _, _ -> }, onOpenTimetable = {}, onBack = {},
    )
}

@Preview
@Composable
private fun ManualAdjustmentsTimetablePausedPreview() = Frame(dark = true) {
    ManualAdjustmentsScreen(
        adjustments = mapOf(Prayer.ISHA to -4),
        confirmed = emptyMap(),
        state = adjustmentsToday.copy(paused = setOf(Prayer.ISHA), timetablePaused = true, pausedTimetableNameKey = "method_isna"),
        onSet = { _, _, _ -> }, onOpenTimetable = {}, onBack = {},
    )
}

@Preview
@Composable
private fun AdjustmentConfirmPreview() = Frame {
    val diyanet = TimetableName("authority_diyanet", SourceKind.AUTHORITY)
    ConfirmSheetContent(
        title = stringResource(Res.string.adjust_confirm_title, localizedPrayerName(Prayer.MAGHRIB), sourceTime(diyanet)),
        body = stringResource(Res.string.adjust_confirm_body_maghrib),
        safe = stringResource(Res.string.adjust_keep, sourceTime(diyanet)),
        other = stringResource(Res.string.adjust_use, formatOffset(-2)),
        onSafe = {},
        onOther = {},
    )
}
