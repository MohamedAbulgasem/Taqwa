package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.domain.Prayer
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.i18n.schoolDisplayName
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Member
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.IrnArctic
import world.taqwa.app.prayer.engine.registry.data.ProofStamp
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import world.taqwa.app.prayer.engine.registry.lateLimitFor
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_asr_heading
import world.taqwa.app.resources.about_asr_known
import world.taqwa.app.resources.about_asr_unknown
import world.taqwa.app.resources.about_authority_method_title
import world.taqwa.app.resources.about_authority_unchecked_body
import world.taqwa.app.resources.about_calculated_body
import world.taqwa.app.resources.about_cautious_body
import world.taqwa.app.resources.about_cautious_checked
import world.taqwa.app.resources.about_cautious_maghrib_cap
import world.taqwa.app.resources.about_check_mosque_body
import world.taqwa.app.resources.about_check_mosque_heading
import world.taqwa.app.resources.about_checked_so_far_with_data
import world.taqwa.app.resources.about_checked_so_far_without_data
import world.taqwa.app.resources.about_checked_through
import world.taqwa.app.resources.about_how_checked
import world.taqwa.app.resources.about_how_reproduces
import world.taqwa.app.resources.about_if_fasting
import world.taqwa.app.resources.about_majority_unchecked_body
import world.taqwa.app.resources.about_method_daily_sun
import world.taqwa.app.resources.about_method_fajr_angle
import world.taqwa.app.resources.about_method_fajr_curve
import world.taqwa.app.resources.about_method_horizon_dip
import world.taqwa.app.resources.about_method_intro
import world.taqwa.app.resources.about_method_isha_after_maghrib
import world.taqwa.app.resources.about_method_isha_angle
import world.taqwa.app.resources.about_method_isha_curve
import world.taqwa.app.resources.about_method_minutes_note
import world.taqwa.app.resources.about_method_rounding_note
import world.taqwa.app.resources.about_nearby_heading
import world.taqwa.app.resources.about_not_affiliated
import world.taqwa.app.resources.about_not_followed_irn_tromso
import world.taqwa.app.resources.about_not_measured_unit
import world.taqwa.app.resources.about_stat_at_most_after
import world.taqwa.app.resources.about_stat_days_at_places
import world.taqwa.app.resources.about_stat_minutes_value
import world.taqwa.app.resources.about_stat_never_before
import world.taqwa.app.resources.about_stat_never_before_decider
import world.taqwa.app.resources.about_stop_eating
import world.taqwa.app.resources.about_sunni_body
import world.taqwa.app.resources.about_sunni_heading
import world.taqwa.app.resources.about_times_title
import world.taqwa.app.resources.about_which_decides
import world.taqwa.app.resources.about_who_publishes
import world.taqwa.app.resources.about_who_publishes_body
import world.taqwa.app.resources.asr_note_hanafi
import world.taqwa.app.resources.asr_note_standard
import world.taqwa.app.resources.asr_shadow_hanafi
import world.taqwa.app.resources.asr_shadow_standard
import world.taqwa.app.resources.timetable_calculated
import world.taqwa.app.resources.timetable_cautious
import world.taqwa.app.resources.timetable_match_mosque
import world.taqwa.app.resources.today_whose_unchecked_body
import kotlin.time.Instant

/**
 * "About these times" (spec §2.3): one screen, filled from the registry and the proof stamp, in
 * one of four templates. Opened from the Prayer screen's ⓘ card and source line (Task 10, once
 * wired), and from Settings › Prayer times (this task).
 */

/**
 * Which of the four templates [resolution] fills, with the raw facts each needs. A plain data
 * holder — no [androidx.compose.runtime.Composable] here — so [aboutTimesUiState] stays a pure
 * function the template-selection tests can call directly, with no Compose test host.
 */
sealed interface AboutTimesUiState {
    val resolution: Resolution

    data class AuthorityChecked(override val resolution: Resolution, val stamp: ProofStamp?) : AboutTimesUiState
    data class AuthorityUnchecked(override val resolution: Resolution, val stamp: ProofStamp?) : AboutTimesUiState

    /**
     * [combined] is the members' own days combined ([DayPipeline.day]/[world.taqwa.app.prayer.engine.day.Cautious]),
     * *before* the Saudi Fajr rule, a manual adjustment or an unconfirmed timetable's pausing —
     * never [EngineDay.day], which those three can move away from every member's own raw value, and
     * did: the cautious table then compared apples to oranges and a row with no matching member
     * printed a stray leading "; " (review fix). Cautious entries are Automatic's own, so this
     * mismatch would only ever come from a chosen Other method's adjustment or the Saudi rule
     * leaking in through a shared code path — never from the cautious entry itself.
     *
     * [stamp] is the cautious entry's own proof, like the two authority states' (ruling R100): the
     * gate over every member's official days, read by [cautiousProof] into the tiles and sentence.
     */
    data class Cautious(
        override val resolution: Resolution,
        val members: List<Pair<Member, PrayerDay>>,
        val combined: PrayerDay,
        val stamp: ProofStamp?,
    ) : AboutTimesUiState

    data class Calculated(override val resolution: Resolution) : AboutTimesUiState
}

/**
 * [engineDay]'s effective resolution ([EngineDay.effective]: Automatic's, or the user's chosen
 * timetable where it applies here), read into the template its [Resolution.about] names. A
 * cautious resolution also carries each member's own day for [place] and [date] ([DayPipeline.members],
 * ui-common.md), in [Resolution.members]' order (most-followed first), and the members combined
 * before any adjustment ([DayPipeline.day]). [hijriOffsetDays] only ever moves the Ramadan
 * calendar a member falls back to; it is not one of the three things [AboutTimesUiState.Cautious.combined]
 * is deliberately computed without.
 */
fun aboutTimesUiState(engineDay: EngineDay, place: Place, date: LocalDate, hijriOffsetDays: Int = 0): AboutTimesUiState {
    val resolution = engineDay.effective
    return when (resolution.about) {
        AboutTemplate.AUTHORITY_CHECKED -> AboutTimesUiState.AuthorityChecked(resolution, ProofStamps.of(resolution.entry.id))
        AboutTemplate.AUTHORITY_UNCHECKED -> AboutTimesUiState.AuthorityUnchecked(resolution, ProofStamps.of(resolution.entry.id))
        AboutTemplate.CAUTIOUS -> {
            val zone = TimeZone.of(place.zoneId)
            val days = DayPipeline.members(resolution, date, zone, engineDay.school, hijriOffsetDays)
            val combined = DayPipeline.day(resolution, date, zone, engineDay.school, hijriOffsetDays)
            AboutTimesUiState.Cautious(resolution, resolution.members.zip(days), combined, ProofStamps.of(resolution.entry.id))
        }
        AboutTemplate.CALCULATED -> AboutTimesUiState.Calculated(resolution)
    }
}

/**
 * @param place Needed only to look an [world.taqwa.app.prayer.engine.registry.AuthorityUnit] back
 * up for [lateLimitFor] (Resolution keeps just the unit's name and point, not the unit itself).
 * @param onMatchMyMosque Null hides the link (Task 12 owns the route; the caller passes a callback
 * once it exists, per ui-common.md's "Tasks 11 and 12 connect them ... if that is trivial").
 */
@Composable
fun AboutTimesScreen(
    state: AboutTimesUiState,
    engineDay: EngineDay,
    place: Place,
    cityLabel: String,
    zone: TimeZone,
    onBack: () -> Unit,
    onMatchMyMosque: (() -> Unit)? = null,
) {
    val format = LocalPlatformFormat.current
    SettingsScaffold(stringResource(Res.string.about_times_title), onBack) {
        SettingsCard {
            when (state) {
                is AboutTimesUiState.AuthorityChecked -> AuthorityCheckedContent(state, place, cityLabel, format)
                is AboutTimesUiState.AuthorityUnchecked -> AuthorityUncheckedContent(state, cityLabel, format)
                is AboutTimesUiState.Cautious -> CautiousContent(state, cityLabel, zone, format, onMatchMyMosque)
                is AboutTimesUiState.Calculated -> CalculatedContent(state, cityLabel, format)
            }
            NearbySection(state)
            AsrSection(state.resolution, engineDay.school, cityLabel)
            SunniSection(state.resolution.shiaRegion)
            CardDivider()
            FastingSection(engineDay.day.endOfEating, zone, format)
            if (state is AboutTimesUiState.Calculated) {
                CardDivider()
                CheckMosqueSection()
            }
            // The last line's own 4 dp is too little against the card's edge: close the card with the
            // 14 dp a heading has above it.
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AuthorityCheckedContent(
    state: AboutTimesUiState.AuthorityChecked,
    place: Place,
    cityLabel: String,
    format: PlatformFormat,
) {
    val resolution = state.resolution
    val authority = authorityShortName(resolution.entry.shortNameKey)
    val unitLabel = resolution.unitName ?: cityLabel

    SectionHeading(stringResource(Res.string.about_who_publishes))
    SectionBody(
        stringResource(Res.string.about_who_publishes_body, authority, unitLabel) + " " +
            stringResource(Res.string.about_not_affiliated, authority),
    )

    val method = resolution.method
    if (method != null) {
        CardDivider()
        SectionHeading(stringResource(Res.string.about_how_reproduces))
        SectionBody(stringResource(Res.string.about_method_intro, authority, methodDescription(method, format)))
    }

    CardDivider()
    SectionHeading(stringResource(Res.string.about_how_checked))
    val stamp = state.stamp
    if (!resolution.measured || stamp == null) {
        SectionBody(stringResource(Res.string.about_not_measured_unit, unitLabel))
    } else {
        val atMost = checkedAtMostMinutes(resolution, place, stamp)
        StatTilesRow(
            tiles = listOf(
                format.localizedDigits(stamp.placeDays) to
                    stringResource(Res.string.about_stat_days_at_places, format.localizedDigits(stamp.places)),
                format.localizedDigits(0) to stringResource(Res.string.about_stat_never_before, authority),
                stringResource(Res.string.about_stat_minutes_value, format.localizedDigits(atMost)) to
                    stringResource(Res.string.about_stat_at_most_after),
            ),
        )
        stamp.provenThrough?.let { through ->
            SectionBody(
                stringResource(Res.string.about_checked_through, authority, format.longDate(LocalDate.parse(through))),
            )
        }
    }
    NotFollowedNote(resolution, authority)
}

@Composable
private fun AuthorityUncheckedContent(
    state: AboutTimesUiState.AuthorityUnchecked,
    cityLabel: String,
    format: PlatformFormat,
) {
    val resolution = state.resolution
    val authority = authorityShortName(resolution.entry.shortNameKey)
    val unitLabel = resolution.unitName ?: cityLabel
    // "{Authority} publishes the prayer times for {place}" is said of an authority only: a
    // convention most mosques follow (Karachi, ISNA in the United States) and a chosen Other method
    // publish no one's times.
    val kind = TimetableKind.of(resolution.entry)

    SectionHeading(stringResource(Res.string.about_authority_method_title, authority))
    SectionBody(
        when (kind) {
            TimetableKind.AUTHORITY -> stringResource(Res.string.about_authority_unchecked_body, authority, unitLabel)
            TimetableKind.MAJORITY -> stringResource(Res.string.about_majority_unchecked_body, authority, unitLabel)
            TimetableKind.METHOD -> stringResource(Res.string.today_whose_unchecked_body, authority, unitLabel)
        },
    )

    val stamp = state.stamp
    // Gated as the checked template is (spec §3.5): no figure is claimed for a unit the proof has
    // not measured, whatever the entry's stamp holds elsewhere.
    val worst = uncheckedWorstMinutes(resolution, stamp)
    if (stamp != null && worst != null) {
        CardDivider()
        SectionBody(
            stringResource(
                Res.string.about_checked_so_far_with_data,
                format.localizedDigits(stamp.placeDays),
                authority,
                format.localizedDigits(worst),
            ),
        )
    } else if (stamp != null) {
        CardDivider()
        SectionBody(stringResource(Res.string.about_not_measured_unit, unitLabel))
    } else if (kind == TimetableKind.AUTHORITY) {
        // Only an authority has "its own timetable" to compare with.
        CardDivider()
        SectionBody(stringResource(Res.string.about_checked_so_far_without_data, authority))
    }
    NotFollowedNote(resolution, authority)
    // A convention is no one to be affiliated with ("Taqwa is not affiliated with Karachi").
    if (kind != TimetableKind.MAJORITY) SectionBody(stringResource(Res.string.about_not_affiliated, authority))
}

/**
 * What of the authority's calendar Taqwa does not follow here, where its clock rule says (ruling
 * R82: IRN's Makkah-time Fajr after sunrise and its midnight-sun sunrise at Tromsø), so that "never
 * before" claims no more than is true; nothing elsewhere.
 */
@Composable
private fun NotFollowedNote(resolution: Resolution, authority: String) {
    val note = notFollowedNoteRes(resolution) ?: return
    SectionBody(stringResource(note, authority))
}

/** The sentence [resolution]'s clock rule names ([world.taqwa.app.prayer.engine.method.ClockRule.notFollowedNoteKey]), or null. */
internal fun notFollowedNoteRes(resolution: Resolution): StringResource? =
    when (resolution.method?.clockRule?.notFollowedNoteKey) {
        IrnArctic.NOTE_KEY -> Res.string.about_not_followed_irn_tromso
        else -> null
    }

@Composable
private fun CautiousContent(
    state: AboutTimesUiState.Cautious,
    cityLabel: String,
    zone: TimeZone,
    format: PlatformFormat,
    onMatchMyMosque: (() -> Unit)?,
) {
    val names = state.members.map { (member, _) -> authorityShortName(member.nameKey) }
    SectionHeading(stringResource(Res.string.timetable_cautious))
    SectionBody(stringResource(Res.string.about_cautious_body, cityLabel, joinNames(names)))
    maghribCapToday(state)?.let { cap ->
        SectionBody(
            stringResource(
                Res.string.about_cautious_maghrib_cap,
                authorityShortName(cap.followed.nameKey),
                joinNames(cap.later.map { authorityShortName(it.nameKey) }),
            ),
        )
    }

    CardDivider()
    SectionHeading(stringResource(Res.string.about_which_decides))
    CautiousTable(state.members, state.combined, zone, format)

    // The proof the site's cautious page shows from the same stamp (rulings R100, R105, R111 and
    // R112): two tiles and the sentence, and nothing at all where the proof does not reach this place.
    cautiousProof(state.resolution, state.stamp)?.let { proof ->
        CardDivider()
        SectionHeading(stringResource(Res.string.about_how_checked))
        StatTilesRow(
            tiles = listOf(
                format.localizedDigits(proof.placeDays) to
                    stringResource(Res.string.about_stat_days_at_places, format.localizedDigits(proof.places)),
                format.localizedDigits(0) to stringResource(Res.string.about_stat_never_before_decider),
            ),
        )
        SectionBody(stringResource(Res.string.about_cautious_checked))
    }

    if (onMatchMyMosque != null) {
        CardDivider()
        MatchMyMosqueLink(onMatchMyMosque)
    }
}

/**
 * What the cautious template's "How it was checked" shows (rulings R100, R105, R111 and R112,
 * city-pages spec §10): the stamp's place-days at its places for the first tile, and the 0 starts
 * before the timetable that decides each of them for the second — the site's cautious page reads the
 * same two from the same stamp.
 *
 * The second tile says "the timetable that decides it", never "any of them" (R111): the gate checks a
 * start against the latest member's printed time, and a Maghrib the cap decided against the
 * most-followed member's alone, so on a capped day another member's printed Maghrib is later than
 * the shown one — exactly what [MaghribCap] says above the table. Deliberately no "at most" figure
 * (R105): a cautious stamp is entry-wide, so its worst lateness would be another place's spread
 * (Oslo would read Trondheim's Isha, over two hours; Oslo's own is under half an hour). And no date
 * (R112): a cautious stamp's last date is the latest of *any* member's rows, which would claim the
 * others through it (Oslo's Diyanet table runs a year past IRN's), until the stamps hold each
 * member's own last date.
 */
internal data class CautiousProof(val placeDays: Int, val places: Int)

/**
 * [stamp] read into a [CautiousProof] where [resolution] is measured, and null — so nothing at all —
 * where there is no stamp or the place is not measured (spec §3.5, as the checked template does:
 * Vancouver lies beyond the places `ca.cautious`'s stamp was gated at). A stamp with unit rows proves
 * only the units in them: the place's own row must be there and complete, as [measuredStartsWorst]
 * requires of the checked template, never another unit's (the plan's units gate). No cautious stamp
 * has unit rows today, and a cautious resolution carries no unit, so such a stamp would show nothing
 * until the registry places cautious entries by unit — the same holding the site's proven rule
 * applies. Pure so a test can check it directly against a committed stamp.
 */
internal fun cautiousProof(resolution: Resolution, stamp: ProofStamp?): CautiousProof? {
    if (stamp == null || !resolution.measured) return null
    if (stamp.worstLateByUnit.isNotEmpty() && measuredStartsWorst(resolution, stamp) == null) return null
    return CautiousProof(stamp.placeDays, stamp.places)
}

/**
 * Ruling R91 (spec §3.6): where the Maghrib cap decided today's Maghrib, so that "each prayer once
 * all have begun it" does not hold for it: the shown Maghrib is before some member's own. [followed]
 * is the member whose own minutes it keeps (the most-followed among those giving the shown minute;
 * the most-followed member where a repair moved it), [later] the members whose Maghrib is later, in
 * the resolution's order. Null where every member has begun Maghrib by the minute shown.
 */
internal data class MaghribCap(val followed: Member, val later: List<Member>)

internal fun maghribCapToday(state: AboutTimesUiState.Cautious): MaghribCap? {
    val shown = state.combined.maghrib
    val later = state.members.filter { (_, day) -> day.maghrib > shown }.map { (member, _) -> member }
    if (later.isEmpty()) return null
    val followed = state.members.firstOrNull { (_, day) -> day.maghrib == shown }?.first
        ?: state.members.minBy { (member, _) -> member.shareRank }.first
    return MaghribCap(followed, later)
}

@Composable
private fun CalculatedContent(state: AboutTimesUiState.Calculated, cityLabel: String, format: PlatformFormat) {
    val method = state.resolution.method
    SectionHeading(stringResource(Res.string.timetable_calculated))
    val description = if (method != null) methodDescription(method, format) else ""
    SectionBody(stringResource(Res.string.about_calculated_body, cityLabel, description))
}

/**
 * Each prayer's shown time, and which member(s) it came from ([world.taqwa.app.prayer.engine.day.Cautious]'s
 * own logic, read back at the UI layer since it is private there): [day] is [AboutTimesUiState.Cautious.combined],
 * the members combined *before* any adjustment — never [EngineDay.day] (review fix: comparing an
 * adjusted instant against every member's own raw one left rows with no match and a stray leading
 * "; "). [members] is each member's own day ([DayPipeline.members]).
 */
@Composable
private fun CautiousTable(members: List<Pair<Member, PrayerDay>>, day: PrayerDay, zone: TimeZone, format: PlatformFormat) {
    val colors = LocalTaqwaColors.current
    for ((prayer, label) in ROWS) {
        val shown = day.instantFor(prayer)
        val agreeing = members.filter { (_, memberDay) -> memberDay.instantFor(prayer) == shown }
        val disagreeing = members - agreeing.toSet()
        // The card's own 16 dp inset, as every heading and body line in it has.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                label,
                style = TaqwaText.caption,
                color = colors.textPrimary,
                modifier = Modifier.width(64.dp).padding(end = 8.dp),
            )
            Column {
                Text(formatClock(shown, zone, format), style = TaqwaText.caption, color = colors.textPrimary)
                val decidedBy = joinNames(agreeing.map { (member, _) -> authorityShortName(member.nameKey) })
                // Not `joinToString { }`: its transform parameter is nullable, so the Compose
                // compiler cannot treat the lambda as inlined into this composable's context.
                val extraNames = disagreeing.map { (member, memberDay) ->
                    "${authorityShortName(member.nameKey)} ${formatClock(memberDay.instantFor(prayer), zone, format)}"
                }
                val semicolon = listSemicolon()
                val extra = extraNames.joinToString(semicolon)
                // Defensive: with the unadjusted [day] every row has at least one agreeing member
                // (tested), but this never prints a bare "; " even if that were somehow not so.
                val text = when {
                    decidedBy.isBlank() -> extra
                    extra.isBlank() -> decidedBy
                    else -> "$decidedBy$semicolon$extra"
                }
                Text(text, style = TaqwaText.caption, color = colors.textSecondary)
            }
        }
    }
}

private val ROWS: List<Pair<Prayer, String>>
    @Composable get() = listOf(
        Prayer.FAJR to localizedPrayerName(Prayer.FAJR),
        Prayer.SUNRISE to localizedPrayerName(Prayer.SUNRISE),
        Prayer.DHUHR to localizedPrayerName(Prayer.DHUHR),
        Prayer.ASR to localizedPrayerName(Prayer.ASR),
        Prayer.MAGHRIB to localizedPrayerName(Prayer.MAGHRIB),
        Prayer.ISHA to localizedPrayerName(Prayer.ISHA),
    )

private fun PrayerDay.instantFor(prayer: Prayer): Instant = when (prayer) {
    Prayer.FAJR -> fajr
    Prayer.SUNRISE -> sunrise
    Prayer.DHUHR -> dhuhr
    Prayer.ASR -> asr
    Prayer.MAGHRIB -> maghrib
    Prayer.ISHA -> isha
}

@Composable
private fun NearbySection(state: AboutTimesUiState) {
    if (state is AboutTimesUiState.Cautious) return // its members are already named above.
    val nearby = state.resolution.entry.nearby
    if (nearby.isEmpty()) return
    val names = nearby.mapNotNull { Registry.byId(it)?.shortNameKey }.map { authorityShortName(it) }
    if (names.isEmpty()) return
    CardDivider()
    SectionHeading(stringResource(Res.string.about_nearby_heading))
    SectionBody(joinNames(names))
}

@Composable
private fun AsrSection(resolution: Resolution, appliedSchool: AsrSchool, cityLabel: String) {
    CardDivider()
    SectionHeading(stringResource(Res.string.about_asr_heading))
    val hanafi = appliedSchool == AsrSchool.HANAFI
    val schoolName = schoolDisplayName(if (hanafi) "hanafi" else "standard")
    // Named by its shadow, as Settings' Asr note does (spec §2.4): "Hanafi: twice the shadow, …".
    val shadow = stringResource(if (hanafi) Res.string.asr_shadow_hanafi else Res.string.asr_shadow_standard)
    val regionLabel = resolution.unitName ?: cityLabel
    val body = when {
        // The user chose the other school in Settings: the place's reason is not this school's.
        appliedSchool != resolution.entry.school ->
            stringResource(if (hanafi) Res.string.asr_note_hanafi else Res.string.asr_note_standard, shadow)
        resolution.entry.schoolKnown -> stringResource(Res.string.about_asr_known, schoolName, regionLabel, shadow)
        else -> stringResource(Res.string.about_asr_unknown, schoolName, regionLabel, shadow)
    }
    SectionBody(body)
}

@Composable
private fun SunniSection(shiaRegion: Boolean) {
    if (!shiaRegion) return
    CardDivider()
    SectionHeading(stringResource(Res.string.about_sunni_heading))
    SectionBody(stringResource(Res.string.about_sunni_body))
}

@Composable
private fun FastingSection(endOfEating: Instant, zone: TimeZone, format: PlatformFormat) {
    SectionHeading(stringResource(Res.string.about_if_fasting))
    SectionBody(stringResource(Res.string.about_stop_eating, formatClock(endOfEating, zone, format)))
}

@Composable
private fun CheckMosqueSection() {
    SectionHeading(stringResource(Res.string.about_check_mosque_heading))
    SectionBody(stringResource(Res.string.about_check_mosque_body))
}

/** "Match my mosque ›" in the accent, the chevron drawn so it turns with the reading direction. */
@Composable
private fun MatchMyMosqueLink(onClick: () -> Unit) {
    val accent = LocalTaqwaColors.current.accent
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.timetable_match_mosque), style = TaqwaText.rowLabel, color = accent)
        Spacer(Modifier.width(4.dp))
        // Drawn, not typed: a typed › is not mirrored under Arabic on Android (spec §2.4).
        Canvas(Modifier.size(12.dp)) { drawChevron(accent, pointsForward = forward) }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = TaqwaText.rowLabel,
        color = LocalTaqwaColors.current.textPrimary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionBody(text: String) {
    Text(
        text,
        style = TaqwaText.caption,
        color = LocalTaqwaColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * The headline figures of the "how it was checked" section (spec §2.3, İstanbul mockup): the
 * checked template's three, the cautious template's two (ruling R105).
 */
@Composable
private fun StatTilesRow(tiles: List<Pair<String, String>>) {
    val colors = LocalTaqwaColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        tiles.forEach { (value, label) ->
            // Equal shares (thirds or halves), start-aligned like the rest of the card: a long label
            // ("starts before London Unified's") wraps inside its own share instead of taking the
            // row and leaving the last figure a sliver one letter wide.
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(value, style = TaqwaText.rowLabel, color = colors.textPrimary)
                Text(label, style = TaqwaText.caption, color = colors.textTertiary)
            }
        }
    }
}

/** Names joined the way the interface's own script joins a list (Arabic and Urdu use "، "). */
@Composable
private fun joinNames(names: List<String>): String = names.joinToString(listComma())

/** The list comma of the interface's script: "، " under Arabic and Urdu, ", " otherwise. */
@Composable
private fun listComma(): String = if (uiLanguage().arabicScript) "، " else ", "

/** The same for a semicolon: "؛ " under Arabic and Urdu. */
@Composable
private fun listSemicolon(): String = if (uiLanguage().arabicScript) "؛ " else "; "

/**
 * "The way {authority} calculates" (spec §2.3): the angles, the horizon rule implicit in them, the
 * authority's own minutes where any event carries them, and the rounding convention every method
 * shares — generated from [method], never hand-written per authority.
 */
@Composable
private fun methodDescription(method: TimetableMethod, format: PlatformFormat): String {
    val angles = methodAngleRanges(method)
    val parts = mutableListOf<String>()

    // The sun model (spec §3.2, İstanbul mockup: "the sun's position taken once a day"):
    // DAILY_0H_UT authorities (Diyanet, Oman, Jamiatul Ulama) take one position for the whole day
    // rather than the exact moment of each event.
    if (usesDailySun(method)) {
        parts += stringResource(Res.string.about_method_daily_sun)
    }

    // Fajr: a fixed angle, or — Diyanet Europe's cities, London Unified, IRN, EMB, DUM RF — a
    // curve that changes by day of year (ruling R28). Say so honestly rather than reading only
    // the static `fajrAngle`, which a curve-carrying method keeps as its fallback value alone.
    parts += if (angles.fajrIsCurve) {
        stringResource(
            Res.string.about_method_fajr_curve,
            formatAngle(angles.fajr.first, format),
            formatAngle(angles.fajr.second, format),
        )
    } else {
        stringResource(Res.string.about_method_fajr_angle, formatAngle(angles.fajr.first, format))
    }

    parts += when (val isha = method.isha) {
        is IshaRule.Angle -> if (angles.ishaIsCurve) {
            stringResource(
                Res.string.about_method_isha_curve,
                formatAngle(angles.isha!!.first, format),
                formatAngle(angles.isha.second, format),
            )
        } else {
            stringResource(Res.string.about_method_isha_angle, formatAngle(isha.degrees, format))
        }
        is IshaRule.AfterMaghrib -> stringResource(Res.string.about_method_isha_after_maghrib, format.localizedDigits(isha.minutes))
    }

    // The horizon (spec §3.2: INM and Habous by elevation, JAKIM's highland zones, Kemenag, Jordan):
    // a deeper horizon than the plain sea-level default, or extra depth added to the twilight
    // angles themselves, both read honestly off the method rather than staying silent about them.
    if (dipsHorizon(method)) {
        parts += stringResource(Res.string.about_method_horizon_dip)
    }

    val minutes = Prayer.entries.mapNotNull { prayer ->
        val value = method.authorityMinutes[prayer]
        if (value == 0) null else localizedPrayerName(prayer) to value
    }
    // The script's own comma, inside the brackets and between the parts ("، " under Arabic and Urdu).
    val comma = listComma()
    if (minutes.isNotEmpty()) {
        val list = minutes.joinToString(comma) { (name, value) -> "$name ${formatSigned(value, format)}" }
        parts += stringResource(Res.string.about_method_minutes_note, list)
    }
    // The last part, and in every language worded as one ("and each start rounded up…"), so the
    // list reads as one sentence that the intro closes, never "…, Maghrib +7, Starts are rounded".
    parts += stringResource(Res.string.about_method_rounding_note)
    return parts.joinToString(comma)
}

/**
 * Each of Fajr and Isha's angle, read honestly off [method] (ruling R28): a curve-carrying method
 * (Diyanet Europe's cities, London Unified, IRN, EMB, DUM RF) gives the range its
 * `fajrAngleByDayOfYear`/`ishaAngleByDayOfYear` covers over the year; a plain method gives its one
 * static angle twice, as a range of zero width. [isha] is null for an [IshaRule.AfterMaghrib]
 * method, which has no angle at all. Pure (no Compose) so a test can check it directly.
 */
internal data class MethodAngles(val fajr: Pair<Double, Double>, val isha: Pair<Double, Double>?) {
    val fajrIsCurve: Boolean get() = fajr.first != fajr.second
    val ishaIsCurve: Boolean get() = isha != null && isha.first != isha.second
}

internal fun methodAngleRanges(method: TimetableMethod): MethodAngles = MethodAngles(
    fajr = method.fajrAngleByDayOfYear?.let { it.min() to it.max() } ?: (method.fajrAngle to method.fajrAngle),
    isha = when (val isha = method.isha) {
        is IshaRule.Angle -> method.ishaAngleByDayOfYear?.let { it.min() to it.max() } ?: (isha.degrees to isha.degrees)
        is IshaRule.AfterMaghrib -> null
    },
)

/** [TimetableMethod.horizonDeg]'s own default (the plain sea-level horizon), for the honesty check above. */
private const val DEFAULT_HORIZON_DEG = -0.8333

/** Diyanet, Oman and Jamiatul Ulama take the sun's position once a day rather than at each event's own moment. */
internal fun usesDailySun(method: TimetableMethod): Boolean = method.sunModel == SunModel.DAILY_0H_UT

/** INM and Habous by elevation, JAKIM's highland zones, Kemenag and Jordan: a horizon deeper than plain sea level. */
internal fun dipsHorizon(method: TimetableMethod): Boolean =
    method.horizonDeg != DEFAULT_HORIZON_DEG || method.twilightDipDeg != 0.0

private fun formatSigned(minutes: Int, format: PlatformFormat): String {
    val digits = format.localizedDigits(if (minutes < 0) -minutes else minutes)
    return if (minutes < 0) "−$digits" else "+$digits"
}

/**
 * A method's angle, in whole degrees where it is one (Umm al-Qura's 18°), else one decimal (Egypt's
 * 19.5°, Kemenag's Isha 18.3°) — never truncated or rounded away, unlike a plain `roundToInt()`.
 * `PlatformFormat` has no locale-aware decimal separator (nothing in the app needed one before
 * this), so the point itself is the literal "." every locale already shares for a clock's ":" —
 * only the digits around it are localized.
 */
internal fun formatAngle(value: Double, format: PlatformFormat): String {
    val sign = if (value < 0) "−" else ""
    val abs = kotlin.math.abs(value)
    val tenths = kotlin.math.round(abs * 10).toInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    val digits = if (fraction == 0) format.localizedDigits(whole) else "${format.localizedDigits(whole)}.${format.localizedDigits(fraction)}"
    return "$sign$digits"
}

/**
 * The starts an "at most N minutes after" figure is about: never sunrise, the end of eating or imsak,
 * which are ends (an end is never after the authority's, and lateness there is on the safe side).
 */
private val START_EVENTS = listOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)

/** The stamp's event keys for those starts: the gate writes one Asr, or the Standard and Hanafi apart. */
private val START_KEYS = setOf("fajr", "dhuhr", "asr", "asrStandard", "asrHanafi", "maghrib", "isha")

/**
 * The worst lateness [stamp] measured over the starts at [resolution]'s own unit (review I3): the
 * unit's own row where the gate checks the entry by unit; the entry's own figure where it checks it
 * without units (Umm al-Qura's twelve places, MUIS); null where the stamp holds units but not this
 * one, or where this unit's row lacks a start the entry is checked on elsewhere (eastern and
 * southern Libya's Fajr and Maghrib, ruling R73: the rest of the row is no figure for the whole
 * day). Never another unit's worst, nor an end's.
 */
internal fun measuredStartsWorst(resolution: Resolution, stamp: ProofStamp): Int? {
    val events = if (stamp.worstLateByUnit.isEmpty()) {
        stamp.worstLateMinutes
    } else {
        val row = resolution.unitId?.let { stamp.worstLateByUnit[it] } ?: return null
        if (!startsOf(row.keys).containsAll(startsOf(stamp.worstLateMinutes.keys))) return null
        row
    }
    return events.filterKeys { it in START_KEYS }.values.maxOrNull()
}

/** The starts among a stamp's event [keys], either Asr school counting as the one Asr. */
private fun startsOf(keys: Set<String>): Set<String> =
    keys.filter { it in START_KEYS }.map { if (it.startsWith("asr")) "asr" else it }.toSet()

/**
 * The unchecked template's "up to {worst} minutes after" (review I2): [measuredStartsWorst] at the
 * user's own unit, and null, so no figure at all, where that unit is not measured (spec §3.5) or
 * [stamp] holds no row for it.
 */
internal fun uncheckedWorstMinutes(resolution: Resolution, stamp: ProofStamp?): Int? =
    stamp?.takeIf { resolution.measured }?.let { measuredStartsWorst(resolution, it) }

/**
 * "At most N minutes later" (ruling R41, ui-common.md): [lateLimitFor]'s own recorded exception for
 * [event] at [place]'s unit, else the class default (A 1, B 2, C 1 after the latest member, D 3).
 * Only called once a stamp exists, so this is always a figure the gate has proven, never a bare
 * promise.
 */
private fun atMostMinutes(resolution: Resolution, place: Place, event: TimedEvent): Int {
    val unit = Units.of(resolution.entry.id)?.unitFor(place)
    val limit = lateLimitFor(event, unit, resolution.entry)
    if (limit != null) return limit.minutes
    return when (resolution.entryClass) {
        EntryClass.A -> 1
        EntryClass.B -> 2
        EntryClass.C -> 1
        EntryClass.D_AUTHORITY, EntryClass.D_NONE -> 3
    }
}

/**
 * The checked template's "at most N minutes after" (spec §2.3, İstanbul mockup: "figures from the
 * release's proof stamp, never from the fitting"): [stamp]'s measured worst over the starts at the
 * user's own unit ([measuredStartsWorst]) — the same figure the unchecked template's "up to {worst}
 * minutes after" reads — falling back to [atMostMinutes] over the starts (the proven late-limit
 * exception, else the class default) only when [stamp] carries no such row. Pure so a test can
 * check it directly against a committed stamp.
 */
internal fun checkedAtMostMinutes(resolution: Resolution, place: Place, stamp: ProofStamp): Int =
    measuredStartsWorst(resolution, stamp) ?: START_EVENTS.maxOf { atMostMinutes(resolution, place, it) }

// ─────────────────────────────── Previews (task 11) ───────────────────────────────
// The four mockup screens (ui-common.md, spec §2.3): İstanbul (authority, checked), Tripoli
// (authority, not yet fully checked), Toronto (cautious times) and Zahedan (calculated). Each
// builds a real `EngineDay` through the actual engine and registry — no hand-written fixtures —
// so a preview drifts the moment the registry or the engine would actually change these places'
// times, exactly like the app.

private val PREVIEW_DATE = LocalDate(2026, 9, 26)
private val ISTANBUL_PREVIEW_PLACE = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
private val TRIPOLI_PREVIEW_PLACE = Place(32.88743, 13.18733, "Africa/Tripoli", "LY")
private val TORONTO_PREVIEW_PLACE = Place(43.6532, -79.3832, "America/Toronto", "CA")
private val ZAHEDAN_PREVIEW_PLACE = Place(29.4963, 60.8629, "Asia/Tehran", "IR", admin1 = "Sistan and Baluchestan")

@Preview
@Composable
private fun AboutTimesAuthorityCheckedPreview() {
    AboutTimesPreview(ISTANBUL_PREVIEW_PLACE, "İstanbul")
}

@Preview
@Composable
private fun AboutTimesAuthorityUncheckedPreview() {
    AboutTimesPreview(TRIPOLI_PREVIEW_PLACE, "Tripoli")
}

@Preview
@Composable
private fun AboutTimesCautiousPreview() {
    AboutTimesPreview(TORONTO_PREVIEW_PLACE, "Toronto", onMatchMyMosque = {})
}

@Preview
@Composable
private fun AboutTimesCalculatedPreview() {
    AboutTimesPreview(ZAHEDAN_PREVIEW_PLACE, "Zahedan")
}

@Composable
private fun AboutTimesPreview(place: Place, cityLabel: String, onMatchMyMosque: (() -> Unit)? = null) {
    val engineDay = remember(place) { PrayerEngine.dayTimes(place, PREVIEW_DATE, EngineSettings()) }
    val state = remember(engineDay) { aboutTimesUiState(engineDay, place, PREVIEW_DATE) }
    TaqwaTheme(ThemeMode.LIGHT) {
        AboutTimesScreen(
            state = state,
            engineDay = engineDay,
            place = place,
            cityLabel = cityLabel,
            zone = TimeZone.of(place.zoneId),
            onBack = {},
            onMatchMyMosque = onMatchMyMosque,
        )
    }
}
