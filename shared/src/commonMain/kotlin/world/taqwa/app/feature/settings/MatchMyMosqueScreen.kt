package world.taqwa.app.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.domain.Prayer
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.match_both
import world.taqwa.app.resources.match_fajr_begins
import world.taqwa.app.resources.match_fajr_isha
import world.taqwa.app.resources.match_hour_apart
import world.taqwa.app.resources.match_in_use
import world.taqwa.app.resources.match_isha_begins
import world.taqwa.app.resources.match_none
import world.taqwa.app.resources.match_note
import world.taqwa.app.resources.match_one_fajr
import world.taqwa.app.resources.match_one_isha
import world.taqwa.app.resources.match_use
import world.taqwa.app.resources.prayer_times_manual
import world.taqwa.app.resources.timetable_automatic
import world.taqwa.app.resources.timetable_checking
import world.taqwa.app.resources.timetable_match_mosque

/**
 * Where the check of the matched timetable [id] stands: running, done (with what it found, if
 * anything), or not needed because Taqwa already follows it. It names its timetable, so a check
 * left over from an earlier match is never taken for the one on screen.
 */
sealed interface MatchCheck {
    val id: String

    data class Checking(override val id: String, val progress: Float) : MatchCheck
    data class Done(override val id: String, val finding: EarlierFinding?) : MatchCheck
    data class InUse(override val id: String) : MatchCheck
}

/**
 * [result]'s check when it is the matched timetable's own, else the check about to start for it:
 * a warning or an enabled "Use this timetable" never belongs to another timetable.
 */
internal fun MatchResult.checkOfMatch(): MatchCheck? {
    val id = named?.id ?: return null
    return check?.takeIf { it.id == id } ?: MatchCheck.Checking(id, 0f)
}

/**
 * What the board's two times found (spec §2.2): the [match], the timetable it names with today's
 * own times ([named], null for no match), every candidate for the no-match list, and the
 * earlier-than check of a timetable that matches both.
 */
data class MatchResult(
    val match: MosqueMatch,
    val named: TimetableOption?,
    val candidates: List<TimetableOption>,
    val check: MatchCheck?,
)

/**
 * Settings › Timetable › **Match my mosque** (spec §2.2, mockup section 3): the board's Fajr and
 * Isha, as the adhan times, against the timetables used here and the Other methods. A match is
 * both times equal or at most a minute later, never earlier; its Dhuhr, Asr and Maghrib are shown
 * to check against the board, and the earlier-than warning comes before "Use this timetable".
 * One time matching names the timetable and the time that differs; both an hour off says the
 * board may be on summer or winter time; nothing matching says so, with the list and Manual
 * adjustments.
 */
@Composable
fun MatchMyMosqueScreen(
    todayLabel: String,
    fajrText: String,
    ishaText: String,
    onFajrText: (String) -> Unit,
    onIshaText: (String) -> Unit,
    result: MatchResult?,
    automatic: TimetableName,
    zone: TimeZone,
    onUse: () -> Unit,
    onOpenManualAdjustments: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    SettingsScaffold(stringResource(Res.string.timetable_match_mosque), onBack) {
        SettingsNote(stringResource(Res.string.match_note, todayLabel))
        Spacer(Modifier.height(14.dp))
        SettingsCard {
            BoardTimeField(stringResource(Res.string.match_fajr_begins), fajrText, onFajrText, ImeAction.Next) {
                focus.moveFocus(FocusDirection.Next)
            }
            CardDivider()
            BoardTimeField(stringResource(Res.string.match_isha_begins), ishaText, onIshaText, ImeAction.Done) {
                focus.clearFocus()
            }
        }
        if (result != null) {
            Spacer(Modifier.height(14.dp))
            MatchResultBlock(result, automatic, zone, onUse, onOpenManualAdjustments)
        }
        // The keyboard covers the foot of a phone: room enough to scroll the button above it.
        if (result != null) Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun MatchResultBlock(
    result: MatchResult,
    automatic: TimetableName,
    zone: TimeZone,
    onUse: () -> Unit,
    onOpenManualAdjustments: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val named = result.named
    when (val match = result.match) {
        is MosqueMatch.Both, is MosqueMatch.HourApart -> {
            val times = named?.today
            AmberBlock(outlined = true) {
                FitTitle(optionName(named))
                if (times != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (match is MosqueMatch.Both) {
                            stringResource(
                                Res.string.match_both,
                                formatClock(times.dhuhr, zone, format),
                                formatClock(times.asr, zone, format),
                                formatClock(times.maghrib, zone, format),
                            )
                        } else {
                            stringResource(Res.string.match_hour_apart, formatClock(times.fajr, zone, format), formatClock(times.isha, zone, format))
                        },
                        style = TaqwaText.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            val check = result.checkOfMatch()
            when (check) {
                is MatchCheck.Checking -> {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = SettingsGutter + 4.dp)) {
                        Text(stringResource(Res.string.timetable_checking), style = TaqwaText.caption, color = colors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        CheckProgressLine(check.progress)
                    }
                }
                is MatchCheck.Done -> check.finding?.let { finding ->
                    Spacer(Modifier.height(12.dp))
                    AmberBlock(outlined = false) {
                        Text(earlierSentence(finding, automatic), style = TaqwaText.caption, color = colors.textPrimary)
                    }
                }
                is MatchCheck.InUse -> {
                    Spacer(Modifier.height(12.dp))
                    SettingsNote(stringResource(Res.string.match_in_use))
                }
                null -> Unit
            }
            if (check !is MatchCheck.InUse) {
                Spacer(Modifier.height(14.dp))
                TaqwaPrimaryButton(
                    stringResource(Res.string.match_use),
                    onUse,
                    modifier = Modifier.padding(horizontal = SettingsGutter),
                    enabled = check is MatchCheck.Done,
                )
            }
        }
        is MosqueMatch.OneTime -> {
            val times = named?.today
            AmberBlock(outlined = true) {
                Text(optionName(named), style = TaqwaText.rowLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = colors.textPrimary)
                if (times != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (match.matched == Prayer.FAJR) {
                            stringResource(Res.string.match_one_fajr, formatClock(times.isha, zone, format))
                        } else {
                            stringResource(Res.string.match_one_isha, formatClock(times.fajr, zone, format))
                        },
                        style = TaqwaText.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        MosqueMatch.None -> {
            SettingsNote(stringResource(Res.string.match_none))
            if (result.candidates.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SettingsCard {
                    result.candidates.forEachIndexed { i, option ->
                        if (i > 0) CardDivider()
                        CandidateRow(option, zone)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SettingsCard {
                ChevronRow(stringResource(Res.string.prayer_times_manual), onClick = onOpenManualAdjustments)
            }
        }
    }
}

/** The matched timetable's name after a check mark, as the mockup's result card has it. */
@Composable
private fun FitTitle(name: String) {
    val colors = LocalTaqwaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckMark(Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, style = TaqwaText.rowLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = colors.textPrimary)
    }
}

/** A candidate in the no-match list: its name, and today's Fajr and Isha to hold against the board. */
@Composable
private fun CandidateRow(option: TimetableOption, zone: TimeZone) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(optionName(option), style = TaqwaText.rowLabel, color = colors.textPrimary)
        option.today?.let { times ->
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.match_fajr_isha, formatClock(times.fajr, zone, format), formatClock(times.isha, zone, format)),
                style = TaqwaText.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** "Automatic" for Automatic's own entry, else the timetable's short name. */
@Composable
private fun optionName(option: TimetableOption?): String = when {
    option == null -> ""
    option.id == AUTOMATIC_TIMETABLE ->
        stringResource(Res.string.timetable_automatic) + " · " + authorityShortName(option.nameKey)
    else -> authorityShortName(option.nameKey)
}

/**
 * One of the board's times: its label and a small amber-edged field (the mockup's). Digits only,
 * as the number pad types them; the colon is drawn in before the last two, so "514" reads 5:14.
 */
@Composable
private fun BoardTimeField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    imeAction: ImeAction,
    onIme: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValue(boardDigits(it)) },
            singleLine = true,
            textStyle = TaqwaText.rowTime.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
            visualTransformation = BoardTimeTransformation,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .width(96.dp)
                .border(1.5.dp, colors.accent, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** What a board field keeps of what is typed: up to four digits, whatever the keyboard's script. */
internal fun boardDigits(typed: String): String = buildString {
    for (c in typed) {
        val digit = when (c) {
            in '0'..'9' -> c
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            in '০'..'৯' -> '0' + (c - '০')
            else -> null
        }
        if (digit != null && length < 4) append(digit)
    }
}

/** "514" shown as "5:14", "2027" as "20:27": the colon before the last two digits. */
private object BoardTimeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length < 3) return TransformedText(text, OffsetMapping.Identity)
        val split = raw.length - 2
        val shown = raw.substring(0, split) + ":" + raw.substring(split)
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = if (offset <= split) offset else offset + 1
                override fun transformedToOriginal(offset: Int): Int =
                    (if (offset <= split) offset else offset - 1).coerceIn(0, raw.length)
            },
        )
    }
}
