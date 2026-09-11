package world.taqwa.app.feature.tasbeeh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.KeepScreenOn
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CountdownRingSize
import world.taqwa.app.design.components.RingArc
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.design.components.drawPlus
import world.taqwa.app.design.contentWidth
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.tasbeeh_add_custom
import world.taqwa.app.resources.tasbeeh_custom_add
import world.taqwa.app.resources.tasbeeh_custom_delete
import world.taqwa.app.resources.tasbeeh_custom_phrase
import world.taqwa.app.resources.tasbeeh_custom_target
import world.taqwa.app.resources.tasbeeh_custom_title
import world.taqwa.app.resources.tasbeeh_hint
import world.taqwa.app.resources.tasbeeh_of
import world.taqwa.app.resources.tasbeeh_preset_after_prayer
import world.taqwa.app.resources.tasbeeh_preset_alhamdulillah
import world.taqwa.app.resources.tasbeeh_preset_allahu_akbar
import world.taqwa.app.resources.tasbeeh_preset_astaghfirullah
import world.taqwa.app.resources.tasbeeh_preset_la_ilaha_illallah
import world.taqwa.app.resources.tasbeeh_preset_subhanallah
import world.taqwa.app.resources.tasbeeh_preset_subhanallahi_wa_bihamdihi
import world.taqwa.app.resources.tasbeeh_reset
import world.taqwa.app.resources.tasbeeh_reset_confirm
import world.taqwa.app.resources.tasbeeh_round
import world.taqwa.app.tasbeeh.Dhikr
import world.taqwa.app.tasbeeh.TasbeehPreset

/** The counter's ring is the Prayer screen's ring, at the same 196 dp it draws itself at. */
private val RingSize = CountdownRingSize

/** The count's own size inside it — the countdown style at the mockup's 56 sp. */
private val CountSize = 56.sp

/** One tap's bump: 1 → 1.06 → 1, 120 ms end to end (spec §4). */
private const val BumpScale = 1.06f
private const val BumpHalfMillis = 60

/** Which sheet, if any, is up. */
private sealed interface TasbeehSheet {
    /** The plus in the top corner: a new phrase of the reader's own. */
    data object NewCustom : TasbeehSheet

    /** A long-press on a custom chip: the same sheet, showing Delete instead of Add. */
    data class EditCustom(val preset: TasbeehPreset) : TasbeehSheet

    /** Reset, which is the one thing here that ninety more taps cannot undo. */
    data object ResetConfirm : TasbeehSheet
}

/**
 * The tasbeeh (spec Tasbeeh §4): the dhikr, the count inside the app's own ring, the round, a row
 * of presets and a way back to zero.
 *
 * The whole middle of the page counts — dhikr, ring, reminder and hint are one `clickable` with
 * no ripple, so a tap lands wherever the thumb happens to be and the eyes can stay shut. Only the
 * chips, Reset and the two top buttons are outside it.
 *
 * [onLeave] is the view model's flush: the debounced write is 300 ms behind the last tap, and
 * leaving the screen must not lose it.
 */
@Composable
fun TasbeehScreen(
    state: TasbeehUiState,
    onBack: () -> Unit,
    onTap: () -> Unit,
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onAddCustom: (String, Int) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val scope = rememberCoroutineScope()

    // A set counted in the hand is minutes of no touch input at all; the display staying on is
    // the difference between finishing a tasbeeh and unlocking the phone at 67.
    KeepScreenOn()

    // `rememberUpdatedState` so the effect below never captures the first lambda it was given and
    // then flushes through a stale view model after a language change swaps it.
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) { onDispose { leave() } }

    var sheet by remember { mutableStateOf<TasbeehSheet?>(null) }

    // Kicked on every tap, including one that lands mid-animation: `snapTo` restarts it from 1
    // rather than queueing, so a fast burst bumps once per tap and never falls behind.
    val bump = remember { Animatable(1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 250),
        label = "tasbeehProgress",
    )

    fun count() {
        onTap()
        scope.launch {
            bump.snapTo(1f)
            bump.animateTo(BumpScale, tween(BumpHalfMillis))
            bump.animateTo(1f, tween(BumpHalfMillis))
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .contentWidth(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackChevron(onBack)
                Spacer(Modifier.weight(1f))
                GlyphButton(
                    description = stringResource(Res.string.tasbeeh_add_custom),
                    onClick = { sheet = TasbeehSheet.NewCustom },
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { count() },
                    ),
            ) {
                // The gutter is on the blocks that need it, not on the column: the reminder row
                // is the widest thing on the page — three transliterations in capitals — and on a
                // 402 pt iPhone the last of them wrapped when it had to clear 24 dp a side too.
                //
                // The stack is centred as a group rather than pinned at the top with the ring
                // floating in what is left: a dhikr held far from the ring it belongs to read as
                // two unrelated things with a hole between them.
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DhikrBlock(state.preset.parts[state.currentPart].dhikr, Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(28.dp))
                    Counter(state, animatedProgress, bump.value)
                    Spacer(Modifier.height(20.dp))
                    // The row's height is held for a single-part preset too — drawn and then
                    // hidden rather than measured into a constant, so the reserved slot is the
                    // row's own height whatever the interface's face does to it. Without this the
                    // ring jumped by half the row every time a chip swapped a set for a phrase.
                    val multi = state.preset.parts.size > 1
                    Box(
                        if (multi) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        PartReminder(state)
                    }
                    Spacer(Modifier.height(12.dp))
                    // The same reservation for the hint, which leaves at the first tap: the ring
                    // must not slide up the screen when it does.
                    Box(Modifier.height(28.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        if (state.state.count == 0 && state.state.round == 1) {
                            Text(
                                stringResource(Res.string.tasbeeh_hint),
                                style = TaqwaText.caption.copy(fontSize = 12.sp),
                                color = colors.textTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            ChipRow(state, onSelect = onSelect, onLongPress = { sheet = TasbeehSheet.EditCustom(it) })
            TaqwaTextLink(
                stringResource(Res.string.tasbeeh_reset),
                onClick = { sheet = TasbeehSheet.ResetConfirm },
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
        }
    }

    when (val open = sheet) {
        null -> Unit
        TasbeehSheet.NewCustom -> CustomDhikrSheet(
            existing = null,
            onAdd = { phrase, target -> onAddCustom(phrase, target); sheet = null },
            onDelete = {},
            onDismiss = { sheet = null },
        )

        is TasbeehSheet.EditCustom -> CustomDhikrSheet(
            existing = open.preset,
            onAdd = { _, _ -> },
            onDelete = { onRemoveCustom(open.preset.id); sheet = null },
            onDismiss = { sheet = null },
        )

        TasbeehSheet.ResetConfirm -> ResetSheet(
            onReset = { onReset(); sheet = null },
            onDismiss = { sheet = null },
        )
    }
}

/** The plus in the top corner, drawn and targeted exactly as [BackChevron] is at the other end. */
@Composable
private fun GlyphButton(description: String, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .padding(end = 8.dp, top = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { drawPlus(colors.textPrimary) }
    }
}

/**
 * The phrase being said: the Arabic alone under an Arabic interface, with the transliteration and
 * the meaning beneath it under a Latin one (spec §4). A custom phrase has neither in either
 * interface — it is shown exactly as it was typed.
 *
 * The face is always the OS one. Manrope ships no Arabic glyphs, and a custom phrase can be in
 * any script at all, so the one family that can draw both is the system's.
 */
@Composable
private fun DhikrBlock(dhikr: Dhikr, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            dhikr.arabic,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 30.sp,
                lineHeight = 48.sp,
                // Explicitly none, not merely unset: an unset tracking inherits material3's 0.5sp
                // from `bodyLarge`, and tracking on a cursive script measures wide and wraps.
                letterSpacing = 0.sp,
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (!isRtlLocale()) {
            dhikr.transliteration?.let {
                Text(it, style = TaqwaText.caption.copy(fontSize = 13.sp), color = colors.textSecondary)
            }
            dhikr.meaning?.let {
                Text(it, style = TaqwaText.caption.copy(fontSize = 12.sp), color = colors.textTertiary)
            }
        }
    }
}

/** The ring and what it holds: the round above the count, the target below it. */
@Composable
private fun Counter(state: TasbeehUiState, progress: Float, scale: Float) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        RingArc(progress = progress, diameter = RingSize, ticks = state.partEnds)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.tasbeeh_round, format.localizedDigits(state.state.round)),
                style = TaqwaText.sectionLabel,
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
            Text(
                format.localizedDigits(state.state.count),
                // The countdown's own style, so the two rings show their number in one voice;
                // tabular figures come with it, which is what stops a count from 99 to 100
                // shuffling sideways.
                style = TaqwaText.countdown.copy(fontSize = CountSize),
                color = colors.textPrimary,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Text(
                stringResource(Res.string.tasbeeh_of, format.localizedDigits(state.preset.total)),
                style = TaqwaText.caption.copy(fontSize = 13.sp),
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * The three parts of a multi-part set under the ring: a filled dot for what is done and for where
 * you are, a hollow one for what is still to come, the current part in the accent.
 */
@Composable
private fun PartReminder(state: TasbeehUiState) {
    val colors = LocalTaqwaColors.current
    // The section label's 0.14 em is display tracking for a lone word; three of them in a row need
    // the mockup's tighter 0.08 em to fit a narrow phone. Arabic keeps none at all, as everywhere.
    val style = TaqwaText.sectionLabel.copy(fontSize = 10.sp).let {
        if (isRtlLocale()) it else it.copy(letterSpacing = 0.08.em)
    }
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        state.preset.parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(" · ", style = style, color = colors.textTertiary)
            }
            val current = index == state.currentPart
            val done = index < state.currentPart
            val tint = if (current) colors.accent else colors.textTertiary
            Canvas(Modifier.size(6.dp)) {
                val radius = size.width / 2f
                if (current || done) {
                    drawCircle(tint, radius = radius)
                } else {
                    drawCircle(tint, radius = radius - size.width * 0.1f, style = Stroke(width = size.width * 0.2f))
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(dhikrLabel(part.dhikr), style = style, color = tint, maxLines = 1)
        }
    }
}

/**
 * Every preset in spec §2's order, the selected one filled. A long press on one of the reader's
 * own opens its sheet — there is nowhere else to delete it from, and a chip has no room for a
 * second control.
 */
@Composable
private fun ChipRow(
    state: TasbeehUiState,
    onSelect: (String) -> Unit,
    onLongPress: (TasbeehPreset) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val listState = rememberLazyListState()
    // A phrase just added sits past the end of the row, and a selection you cannot see is worse
    // than no selection at all — so the row walks to whichever chip is selected. Keyed on the id,
    // not the index, so it moves when the selection changes and not when the list merely does.
    LaunchedEffect(state.preset.id) {
        val index = state.presets.indexOfFirst { it.id == state.preset.id }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.presets, key = { it.id }) { preset ->
            val selected = preset.id == state.preset.id
            val shape = RoundedCornerShape(percent = 50)
            Box(
                Modifier
                    .height(44.dp)
                    .clip(shape)
                    .background(if (selected) colors.accent else colors.surface)
                    .border(1.dp, if (selected) colors.accent else colors.hairline, shape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(preset.id) },
                        onLongClick = if (preset.custom) ({ onLongPress(preset) }) else null,
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    presetLabel(preset),
                    // The page's own ground on the amber fill — off-white in light, which is the
                    // mockup's white, and near-black in dark, where the accent is bright enough
                    // that white on it is barely legible. The same pairing the primary button
                    // uses for its label.
                    color = if (selected) colors.background else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Default,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Phrase and target for a dhikr of the reader's own; Delete instead, on one that already exists. */
@Composable
private fun CustomDhikrSheet(
    existing: TasbeehPreset?,
    onAdd: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    var phrase by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("33") }
    val bounded = target.toIntOrNull()?.takeIf { it in 1..MAX_TARGET }
    val valid = phrase.isNotBlank() && bounded != null

    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                existing?.parts?.first()?.dhikr?.arabic ?: stringResource(Res.string.tasbeeh_custom_title),
                style = TaqwaText.screenTitle.copy(fontSize = 20.sp),
                color = colors.textPrimary,
                fontFamily = if (existing == null) null else FontFamily.Default,
            )
            if (existing == null) {
                SheetField(
                    label = stringResource(Res.string.tasbeeh_custom_phrase),
                    value = phrase,
                    onValueChange = { phrase = it.take(MAX_PHRASE) },
                    numeric = false,
                )
                SheetField(
                    label = stringResource(Res.string.tasbeeh_custom_target),
                    value = target,
                    // Four digits is 1000, the largest target the store keeps; anything longer
                    // could only be typed to be clamped away on the way in.
                    onValueChange = { typed -> target = typed.filter { it.isDigit() }.take(4) },
                    numeric = true,
                )
                Box(Modifier.alpha(if (valid) 1f else 0.4f)) {
                    TaqwaPrimaryButton(
                        stringResource(Res.string.tasbeeh_custom_add),
                        onClick = { if (valid) onAdd(phrase.trim(), bounded!!) },
                    )
                }
            } else {
                TaqwaCard {
                    TaqwaRow(
                        label = stringResource(Res.string.tasbeeh_custom_delete),
                        onClick = onDelete,
                        ripple = false,
                    )
                }
            }
        }
    }
}

/** The sheets' one text field: the city search's box, with its label above it. */
@Composable
private fun SheetField(label: String, value: String, onValueChange: (String) -> Unit, numeric: Boolean) {
    val colors = LocalTaqwaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TaqwaText.sectionLabel, color = colors.accent)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                // The system face: a phrase can be in any script, and the target is digits.
                textStyle = TextStyle(fontSize = 17.sp, color = colors.textPrimary, fontFamily = FontFamily.Default),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One line and one row: the only undoable-by-counting action on the screen asks first. */
@Composable
private fun ResetSheet(onReset: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.tasbeeh_reset_confirm),
                style = TaqwaText.rowLabel,
                color = colors.textPrimary,
            )
            TaqwaCard {
                TaqwaRow(label = stringResource(Res.string.tasbeeh_reset), onClick = onReset, ripple = false)
            }
        }
    }
}

/** The store's own ceiling, restated here only to grey the Add button out before it is hit. */
private const val MAX_TARGET = 1000
private const val MAX_PHRASE = 60

/** A chip's words: the built-in's resource, or the reader's own phrase exactly as typed. */
@Composable
private fun presetLabel(preset: TasbeehPreset): String =
    presetLabelRes(preset.id)?.let { stringResource(it) } ?: preset.parts.first().dhikr.arabic

/**
 * The reminder row's words: the transliteration in capitals under a Latin interface, and the
 * chip's own Arabic under an Arabic one — the same words the chips use, so the row reads as the
 * set the chip names rather than as three new phrases. Dhikr ids and the built-in preset ids for
 * the single-dhikr presets are the same strings, which is what makes one lookup serve both.
 */
@Composable
private fun dhikrLabel(dhikr: Dhikr): String = if (isRtlLocale()) {
    presetLabelRes(dhikr.id)?.let { stringResource(it) } ?: dhikr.arabic
} else {
    dhikr.transliteration?.uppercase() ?: dhikr.arabic
}

private fun presetLabelRes(id: String): StringResource? = when (id) {
    "after_prayer" -> Res.string.tasbeeh_preset_after_prayer
    "subhanallah" -> Res.string.tasbeeh_preset_subhanallah
    "alhamdulillah" -> Res.string.tasbeeh_preset_alhamdulillah
    "allahu_akbar" -> Res.string.tasbeeh_preset_allahu_akbar
    "astaghfirullah" -> Res.string.tasbeeh_preset_astaghfirullah
    "la_ilaha_illallah" -> Res.string.tasbeeh_preset_la_ilaha_illallah
    "subhanallahi_wa_bihamdihi" -> Res.string.tasbeeh_preset_subhanallahi_wa_bihamdihi
    else -> null
}
