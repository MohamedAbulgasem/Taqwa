package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.glyphStroke
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.method_picker_note
import world.taqwa.app.resources.prayer_times_paused_value
import world.taqwa.app.resources.prayer_times_timetable
import world.taqwa.app.resources.timetable_auto_authority
import world.taqwa.app.resources.timetable_auto_calculated
import world.taqwa.app.resources.timetable_auto_cautious
import world.taqwa.app.resources.timetable_auto_majority
import world.taqwa.app.resources.timetable_automatic
import world.taqwa.app.resources.timetable_keep_automatic
import world.taqwa.app.resources.timetable_keep_current
import world.taqwa.app.resources.timetable_match_mosque
import world.taqwa.app.resources.timetable_nearby_label
import world.taqwa.app.resources.timetable_other_examples
import world.taqwa.app.resources.timetable_other_methods
import world.taqwa.app.resources.timetable_out_of_scope
import world.taqwa.app.resources.timetable_paused_row

/**
 * Settings › Prayer times › **Timetable** (spec §2.2, mockup section 3; rulings R50 and R52).
 *
 * Automatic first, naming what it follows here and today's Fajr, Maghrib and Isha; then the
 * timetables Automatic lists as used nearby that apply here, and the stored choice where it
 * applies here without being listed ([timetableRowIds]), each with the same three times to hold
 * against a mosque's board; then Other methods and Match my mosque. A stored timetable that
 * does not apply here leaves Automatic selected, with one line saying so; one not yet confirmed
 * reads as paused, and tapping it confirms it through the same check as choosing it.
 *
 * Choosing runs the earlier-than check ([checking] while it runs, in the tapped row); what it
 * finds comes back as [warning], a sheet whose button keeps the timetable followed now, by name,
 * and whose link is "Follow it".
 */
@Composable
fun TimetableScreen(
    status: TimetableStatus,
    automaticAbout: AboutTemplate,
    automatic: TimetableOption,
    nearby: List<TimetableOption>,
    zone: TimeZone,
    checking: ChoiceInProgress?,
    warning: PendingWarning?,
    onChoose: (String) -> Unit,
    onKeepAutomatic: () -> Unit,
    onFollow: () -> Unit,
    onOpenOtherMethods: () -> Unit,
    onOpenMatch: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val selected = status.selectedId
    SettingsScaffold(stringResource(Res.string.prayer_times_timetable), onBack) {
        if (status.outOfScope) {
            SettingsNote(stringResource(Res.string.timetable_out_of_scope, chosenName(status)))
            Spacer(Modifier.height(14.dp))
        }
        SettingsCard {
            val explanation = when (automaticAbout) {
                AboutTemplate.AUTHORITY_CHECKED, AboutTemplate.AUTHORITY_UNCHECKED -> stringResource(
                    // "London Unified, followed by most mosques here" (mockup section 3); an
                    // authority's: "The timetable Diyanet publishes for this place".
                    if (status.automaticKind == TimetableKind.MAJORITY) Res.string.timetable_auto_majority else Res.string.timetable_auto_authority,
                    timetableNameText(status.automatic),
                )
                AboutTemplate.CAUTIOUS -> stringResource(Res.string.timetable_auto_cautious)
                AboutTemplate.CALCULATED -> stringResource(Res.string.timetable_auto_calculated)
            }
            TimetableChoiceRow(
                label = stringResource(Res.string.timetable_automatic),
                detail = listOfNotNull(explanation, automatic.today?.let { timesToday(it, zone) }).joinToString(" · "),
                selected = selected == AUTOMATIC_TIMETABLE,
                checking = null,
                onClick = { onChoose(AUTOMATIC_TIMETABLE) },
            )
        }

        Spacer(Modifier.height(28.dp))
        if (nearby.isNotEmpty()) SectionLabel(stringResource(Res.string.timetable_nearby_label))
        SettingsCard {
            nearby.forEach { option ->
                TimetableChoiceRow(
                    label = authorityShortName(option.nameKey),
                    detail = if (option.id == selected && status.paused) {
                        stringResource(Res.string.timetable_paused_row)
                    } else {
                        option.today?.let { timesToday(it, zone) }
                    },
                    selected = option.id == selected,
                    checking = checking?.takeIf { it.id == option.id }?.progress,
                    onClick = { onChoose(option.id) },
                )
                CardDivider()
            }
            // Selected here when the timetable is an Other method not listed above: its name then
            // stands in for the list of examples.
            val otherSelected = selected != AUTOMATIC_TIMETABLE && nearby.none { it.id == selected } &&
                status.highLatitude
            OtherMethodsRow(
                selected = otherSelected,
                detail = if (otherSelected) {
                    val name = chosenName(status)
                    if (status.paused) stringResource(Res.string.prayer_times_paused_value, name) else name
                } else {
                    stringResource(
                        Res.string.timetable_other_examples,
                        authorityShortName(OTHER_METHOD_EXAMPLES[0]),
                        authorityShortName(OTHER_METHOD_EXAMPLES[1]),
                        authorityShortName(OTHER_METHOD_EXAMPLES[2]),
                    )
                },
                onClick = onOpenOtherMethods,
            )
            CardDivider()
            ChevronRow(
                label = stringResource(Res.string.timetable_match_mosque),
                leading = {
                    val tint = colors.accent
                    Canvas(Modifier.size(20.dp)) { drawBoard(tint) }
                },
                onClick = onOpenMatch,
            )
        }
    }
    if (warning != null) EarlierWarningSheet(warning, status.automatic, keepLabel(status), onKeepAutomatic, onFollow)
}

/**
 * Settings › Timetable › **Other methods**: the eleven that apply anywhere (ruling R50), each
 * with today's Fajr, Maghrib and Isha, chosen through the same earlier-than check.
 */
@Composable
fun OtherMethodsScreen(
    status: TimetableStatus,
    methods: List<TimetableOption>,
    zone: TimeZone,
    checking: ChoiceInProgress?,
    warning: PendingWarning?,
    onChoose: (String) -> Unit,
    onKeepAutomatic: () -> Unit,
    onFollow: () -> Unit,
    onBack: () -> Unit,
) {
    val selected = status.selectedId
    SettingsScaffold(stringResource(Res.string.timetable_other_methods), onBack) {
        SettingsCard {
            methods.forEachIndexed { i, option ->
                if (i > 0) CardDivider()
                TimetableChoiceRow(
                    label = authorityShortName(option.nameKey),
                    detail = if (option.id == selected && status.paused) {
                        stringResource(Res.string.timetable_paused_row)
                    } else {
                        option.today?.let { timesToday(it, zone) }
                    },
                    selected = option.id == selected,
                    checking = checking?.takeIf { it.id == option.id }?.progress,
                    onClick = { onChoose(option.id) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(stringResource(Res.string.method_picker_note))
    }
    if (warning != null) EarlierWarningSheet(warning, status.automatic, keepLabel(status), onKeepAutomatic, onFollow)
}

/** The Other methods row: a radio like the timetables above it (the mockup's), and a chevron. */
@Composable
private fun OtherMethodsRow(selected: Boolean, detail: String, onClick: () -> Unit) {
    TimetableChoiceRow(
        label = stringResource(Res.string.timetable_other_methods),
        detail = detail,
        selected = selected,
        checking = null,
        chevron = true,
        onClick = onClick,
    )
}

/**
 * The warning's button, which keeps what is followed now and names it (ruling on M2): "Keep
 * Automatic" while Automatic applies, else "Keep {the current timetable}".
 */
@Composable
internal fun keepLabel(status: TimetableStatus): String =
    if (status.selectedId == AUTOMATIC_TIMETABLE) {
        stringResource(Res.string.timetable_keep_automatic)
    } else {
        stringResource(Res.string.timetable_keep_current, chosenName(status))
    }

/** The stored timetable's name, or its id where the registry no longer has it. */
@Composable
internal fun chosenName(status: TimetableStatus): String =
    status.chosenNameKey?.let { authorityShortName(it) } ?: status.chosenId.orEmpty()

/** The Other methods named as examples under their row (the mockup's "Muslim World League, ISNA, Umm al-Qura…"). */
private val OTHER_METHOD_EXAMPLES = listOf("method_muslim_world_league", "method_isna", "method_umm_al_qura")

/** A mosque's board: a rounded panel with three lines of times, the last one short (the mockup's glyph). */
private fun DrawScope.drawBoard(tint: Color) {
    val u = size.width / 24f
    drawRoundRect(tint, topLeft = Offset(4f * u, 3.5f * u), size = Size(16f * u, 17f * u), cornerRadius = CornerRadius(2.5f * u), style = glyphStroke())
    val w = glyphStroke().width
    drawLine(tint, Offset(8f * u, 8f * u), Offset(16f * u, 8f * u), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(tint, Offset(8f * u, 12f * u), Offset(16f * u, 12f * u), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(tint, Offset(8f * u, 16f * u), Offset(13f * u, 16f * u), strokeWidth = w, cap = StrokeCap.Round)
}
