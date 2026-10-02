package world.taqwa.app.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import world.taqwa.app.resources.timetable_cautious
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TabRootTitleTop
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.components.CountdownRing
import world.taqwa.app.design.components.CountdownRingSize
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.drawMisbaha
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.feature.qibla.QiblaMiniDial
import world.taqwa.app.feature.qibla.localizedGroupedKm
import world.taqwa.app.i18n.CountdownFormatter
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.localizedPrayerName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.notifications_allow_exact_alarms
import world.taqwa.app.resources.onboarding_location_body
import world.taqwa.app.resources.qibla_title
import world.taqwa.app.resources.today_location_settings
import world.taqwa.app.resources.today_choose_city
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.resources.today_exact_alarms_body
import world.taqwa.app.resources.today_exact_alarms_label
import world.taqwa.app.resources.madhab_hanafi
import world.taqwa.app.resources.madhab_standard
import world.taqwa.app.resources.today_about_these_times
import world.taqwa.app.resources.today_differ_after
import world.taqwa.app.resources.today_differ_before
import world.taqwa.app.resources.today_polar_line
import world.taqwa.app.resources.today_next_in
import world.taqwa.app.resources.today_no_location_title
import world.taqwa.app.resources.today_open_tasbeeh
import world.taqwa.app.resources.today_other_asr
import world.taqwa.app.resources.today_qibla_detail
import kotlin.time.Instant

/** The page gutter, shared with every settings screen. */
private val Gutter = 24.dp

/**
 * The Prayer tab (iteration 8; still `Today` in code, see `Tab`). City and both dates, the
 * countdown ring free on the page, the timeline in a card, and beneath it the Qibla card that
 * opens the compass — the compass stopped being a tab so that the bar could stay small.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onChooseCity: () -> Unit,
    /** "No location yet"'s link: the app's own page in the system's settings. */
    onOpenLocationSettings: () -> Unit,
    onOpenQibla: () -> Unit,
    onOpenTasbeeh: () -> Unit,
    /** Android with notifications on and "Alarms & reminders" off; see [ExactAlarmsCard]. */
    exactAlarmsOff: Boolean = false,
    onAllowExactAlarms: () -> Unit = {},
    /** About these times (Task 11), from the ⓘ's card, the source line and the Sunni card. */
    onOpenAboutTimes: () -> Unit = {},
    /** Match my mosque (Task 12), from the cautious-times card. */
    onMatchMyMosque: () -> Unit = {},
    /** A one-time card was answered, by either of its actions. */
    onAnswerCard: (OneTimeCard) -> Unit = {},
) {
    val colors = LocalTaqwaColors.current
    val actions = ReadyActions(
        onOpenQibla = onOpenQibla,
        onOpenTasbeeh = onOpenTasbeeh,
        onAllowExactAlarms = onAllowExactAlarms,
        onOpenAboutTimes = onOpenAboutTimes,
        onMatchMyMosque = onMatchMyMosque,
        onAnswerCard = onAnswerCard,
    )
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (state) {
            TodayUiState.Loading -> Unit
            TodayUiState.NeedsLocation -> NeedsLocationBody(onChooseCity, onOpenLocationSettings)
            is TodayUiState.Ready -> ReadyBody(state, actions, exactAlarmsOff)
        }
    }
}

/** Everything the ready screen can be asked to do, passed down as one. */
private class ReadyActions(
    val onOpenQibla: () -> Unit,
    val onOpenTasbeeh: () -> Unit,
    val onAllowExactAlarms: () -> Unit,
    val onOpenAboutTimes: () -> Unit,
    val onMatchMyMosque: () -> Unit,
    val onAnswerCard: (OneTimeCard) -> Unit,
)

@Composable
private fun ReadyBody(
    state: TodayUiState.Ready,
    actions: ReadyActions,
    exactAlarmsOff: Boolean,
) {
    val zone = rememberZone(state.location.timeZoneId)
    val format = LocalPlatformFormat.current
    var clockSheetOpen by remember { mutableStateOf(false) }
    val openClockSheet = { clockSheetOpen = true }
    val warning = state.clockWarning
    if (clockSheetOpen && warning != null) ClockSheet(warning, onDismiss = { clockSheetOpen = false })
    // safeDrawing, not systemBars: held sideways the navigation bar and the camera cutout move to
    // the left and right edges, which systemBars alone does not report.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        if (maxWidth > maxHeight) {
            // Two panes rather than one long scroll: upright, the ring sits above the fold and
            // the timeline below it, which is the right order for a page you read top to bottom.
            // Sideways there is no room for that order — the ring alone would fill the screen and
            // push every prayer time off the bottom — so the two halves sit side by side instead.
            LandscapeBody(state, zone, format, maxWidth, maxHeight, actions, exactAlarmsOff, openClockSheet)
        } else {
            PortraitBody(state, zone, format, actions, exactAlarmsOff, openClockSheet)
        }
    }
}

/** Upright: exactly what this screen has always been, now capped to the shared content width. */
@Composable
private fun PortraitBody(
    state: TodayUiState.Ready,
    zone: TimeZone,
    format: PlatformFormat,
    actions: ReadyActions,
    exactAlarmsOff: Boolean,
    onOpenClockSheet: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.contentWidth()) {
            CityAndDates(state, actions)

            // 32 dp here, not 44: CityAndDates already carries 4 dp under its dates, so this puts
            // 36 dp of air above the ring — the same 36 dp that separates its bottom from the
            // timeline card, so the ring sits in a band of its own rather than closer to the card
            // than to the header it hangs beneath. The clock line, when it shows, sits in that air.
            if (state.clockWarning != null) {
                Spacer(Modifier.height(12.dp))
                ClockLine(onClick = onOpenClockSheet, horizontalPadding = Gutter)
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(32.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Countdown(state, zone, format)
            }
            Spacer(Modifier.height(36.dp))

            TimelineCard(state, zone, format)
            UnderTheList(state, actions)

            QiblaCard(state.qiblaBearingDegrees, state.qiblaDistanceKm, format, actions.onOpenQibla)
            if (exactAlarmsOff) {
                Spacer(Modifier.height(14.dp))
                ExactAlarmsCard(actions.onAllowExactAlarms)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Sideways: who and when, with the ring, on the side the eye starts from; everything that is a
 * list — the timeline, the Qibla card, the high-latitude note — scrolling on the other. Both
 * halves are capped by the same rule the rest of the app uses, so on a tablet they stay two
 * columns of readable width rather than two very wide ones.
 */
@Composable
private fun LandscapeBody(
    state: TodayUiState.Ready,
    zone: TimeZone,
    format: PlatformFormat,
    width: Dp,
    height: Dp,
    actions: ReadyActions,
    exactAlarmsOff: Boolean,
    onOpenClockSheet: () -> Unit,
) {
    // The ring gets whatever its own half can spare: never bigger than upright, and small enough
    // that the city and the date above it still fit above the fold on a short sideways screen.
    val ring = minOf(CountdownRingSize, width / 2 - Gutter * 2, height - 120.dp)
        .coerceAtLeast(96.dp)
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().contentWidth().padding(horizontal = Gutter),
            verticalArrangement = Arrangement.Center,
        ) {
            CityAndDates(state, actions, horizontalPadding = 0.dp, topPadding = 12.dp)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Countdown(state, zone, format, diameter = ring)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().contentWidth().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            if (state.clockWarning != null) {
                ClockLine(onClick = onOpenClockSheet, horizontalPadding = Gutter)
                Spacer(Modifier.height(12.dp))
            }
            TimelineCard(state, zone, format)
            UnderTheList(state, actions)
            QiblaCard(state.qiblaBearingDegrees, state.qiblaDistanceKm, format, actions.onOpenQibla)
            if (exactAlarmsOff) {
                Spacer(Modifier.height(14.dp))
                ExactAlarmsCard(actions.onAllowExactAlarms)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The city, then both calendars — the same block in either orientation.
 *
 * [topPadding] is what puts the city on the same line as the Quran and Settings titles when the
 * phone is upright. Sideways the whole left pane is centred vertically instead, so the caller
 * passes the smaller inset it had before: 24 dp there would only push a centred block off centre.
 */
@Composable
private fun CityAndDates(
    state: TodayUiState.Ready,
    actions: ReadyActions,
    horizontalPadding: Dp = Gutter,
    topPadding: Dp = TabRootTitleTop,
) {
    val colors = LocalTaqwaColors.current
    val density = LocalDensity.current
    val place = state.cityDisplayName ?: stringResource(Res.string.today_current_location)
    var whoseOpen by remember { mutableStateOf(false) }
    // Where the block and the ⓘ sit in the window, for the card's width and its caret.
    var blockLeft by remember { mutableFloatStateOf(0f) }
    var blockWidth by remember { mutableIntStateOf(0) }
    var infoCentre by remember { mutableStateOf<Float?>(null) }
    // A Row now, for the misbaha at its end (spec Tasbeeh §5). The text keeps the weight and the
    // block keeps its own paddings, so the 36 dp of air above the ring is untouched: the button is
    // 44 dp tall against a city line of about 32, and it is the *text* column that still sets the
    // block's height, because the button is aligned to the city's line rather than stretched.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(top = topPadding, bottom = 4.dp),
    ) {
        // The whole city-and-date block is the ⓘ's 48 dp target (spec §2.1). No ripple, like
        // every surface on this screen: the card opening is the feedback.
        Column(
            Modifier
                .weight(1f)
                .onGloballyPositioned {
                    blockLeft = it.positionInWindow().x
                    blockWidth = it.size.width
                }
                .defaultMinSize(minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { whoseOpen = true },
                ),
        ) {
            Text(
                // Resolved by the view model, in the interface's language; `location.cityName` stays
                // the English snapshot and is only what this falls back to.
                place,
                style = TaqwaText.screenTitle,
                color = colors.textPrimary,
            )
            // Hijri first, then the Gregorian a step quieter: one line, the two calendars read
            // as a pair. The separator is punctuation, not a translated string, so it is the
            // same in both languages; the bidi algorithm orders the halves under Arabic.
            //
            // The Gregorian half is isolated (FSI…PDI). Without it, an English date that opens with a
            // digit — "8 September 2026" — has that leading number swallowed by the surrounding
            // right-to-left run and comes out as "September 2026 8"; inside the isolate the date takes
            // its own direction from its own first strong character and lays out whole.
            //
            // The ⓘ ends the line (spec §2.1), held to the date by a no-break space so it never
            // wraps alone and adds no line of its own; under Arabic the end is the left. Its
            // alternate text is the block's one screen-reader label (spec §2.4).
            val label = whoseLabel(state.whose, place)
            val infoTint = if (whoseOpen) colors.accent else colors.textTertiary
            Text(
                buildAnnotatedString {
                    append(state.hijri)
                    withStyle(SpanStyle(color = colors.textTertiary)) {
                        append(" · ")
                        append("\u2068" + state.gregorian + "\u2069")
                    }
                    append("\u00A0")
                    appendInlineContent(INFO_GLYPH, label)
                },
                style = TaqwaText.caption,
                color = colors.textSecondary,
                inlineContent = mapOf(
                    INFO_GLYPH to InlineTextContent(
                        Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        InfoGlyph(
                            infoTint,
                            Modifier.fillMaxSize().onGloballyPositioned {
                                infoCentre = it.boundsInWindow().center.x
                            },
                        )
                    },
                ),
            )
            if (whoseOpen) {
                WhoseTimesPopup(
                    whose = state.whose,
                    place = place,
                    width = with(density) { blockWidth.toDp() },
                    caretX = infoCentre?.let { it - blockLeft },
                    onOpenAboutTimes = actions.onOpenAboutTimes,
                    onDismiss = { whoseOpen = false },
                )
            }
        }
        TasbeehButton(actions.onOpenTasbeeh)
    }
}

private const val INFO_GLYPH = "info"

/**
 * What sits between the prayer list and the Qibla card, each only where it applies (spec §2.1):
 * the "Cautious times ›" line and, on a polar day, the line that says some times follow a nearby
 * latitude (both open About these times), then the one-time card due this launch. The
 * exact-alarms card keeps its place under the Qibla card.
 */
@Composable
private fun UnderTheList(state: TodayUiState.Ready, actions: ReadyActions) {
    val place = state.cityDisplayName ?: stringResource(Res.string.today_current_location)
    val about = stringResource(Res.string.today_about_these_times)
    val anyLine = state.whose.cautious || state.polar
    if (anyLine) Spacer(Modifier.height(2.dp))
    if (state.whose.cautious) {
        TertiaryLine(
            text = stringResource(Res.string.timetable_cautious),
            label = aboutLabel(state.whose, place),
            onClick = actions.onOpenAboutTimes,
            horizontalPadding = Gutter + 6.dp,
            onClickLabel = about,
        )
    }
    if (state.polar) {
        val sentence = stringResource(Res.string.today_polar_line)
        TertiaryLine(
            text = sentence,
            label = sentence,
            onClick = actions.onOpenAboutTimes,
            horizontalPadding = Gutter + 6.dp,
            onClickLabel = about,
        )
    }
    val card = state.oneTimeCard
    if (card != null) {
        Spacer(Modifier.height(if (anyLine) 6.dp else 14.dp))
        OneTimeCardView(
            card = card,
            onAction = {
                actions.onAnswerCard(card)
                when (card) {
                    OneTimeCard.CAUTIOUS -> actions.onMatchMyMosque()
                    OneTimeCard.SUNNI -> actions.onOpenAboutTimes()
                }
            },
            onOk = { actions.onAnswerCard(card) },
            horizontalPadding = Gutter,
        )
        Spacer(Modifier.height(14.dp))
    } else {
        Spacer(Modifier.height(if (anyLine) 2.dp else 14.dp))
    }
}

/**
 * The way to the tasbeeh: the misbaha glyph at the tab bar's 22 dp, in a 44 dp target at the end
 * of the header row. No ripple, like the tab bar and the Qibla card — the pushed screen is the
 * feedback. It carries a content description because it is a glyph with no words beside it.
 *
 * Nudged up by 4 dp so the 22 dp glyph sits on the city's own line rather than on the block's
 * centre, which the two lines of dates below would otherwise pull it down to.
 */
@Composable
private fun TasbeehButton(onOpen: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val description = stringResource(Res.string.today_open_tasbeeh)
    Box(
        Modifier
            .offset(y = (-4).dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { drawMisbaha(colors.textPrimary) }
    }
}

@Composable
private fun Countdown(
    state: TodayUiState.Ready,
    zone: TimeZone,
    format: PlatformFormat,
    diameter: Dp = CountdownRingSize,
) {
    CountdownRing(
        progress = state.today.ringProgress,
        label = stringResource(
            Res.string.today_next_in,
            localizedPrayerName(state.today.next.prayer),
        ),
        countdown = CountdownFormatter.countdown(
            state.today.countdown,
            format,
            CountdownFormatter.ARABIC_INDIC_DIGITS_VERIFIED_TABULAR,
        ),
        clockTime = formatClock(state.today.next.instant, zone, format),
        diameter = diameter,
    )
}

@Composable
private fun TimelineCard(state: TodayUiState.Ready, zone: TimeZone, format: PlatformFormat) {
    TaqwaCard(Modifier.padding(horizontal = Gutter)) {
        Spacer(Modifier.height(4.dp))
        PrayerTimeline(
            state.today.rows,
            horizontalPadding = 16.dp,
            setByRule = state.setByRule,
            subLines = subLines(state, zone, format),
        ) { row: TimelineRow ->
            formatClock(row.instant, zone, format)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The quiet lines under the rows (spec §2.1): the other school's Asr under Asr ("Standard {time}"),
 * and where timetables differ under the current prayer. Names and times are isolated so each keeps
 * its own direction inside the other script's sentence.
 */
@Composable
private fun subLines(state: TodayUiState.Ready, zone: TimeZone, format: PlatformFormat): Map<Prayer, List<String>> {
    val lines = mutableMapOf<Prayer, MutableList<String>>()
    state.otherAsr?.let { other ->
        val school = stringResource(
            if (other.school == AsrSchool.HANAFI) Res.string.madhab_hanafi else Res.string.madhab_standard,
        )
        lines.getOrPut(Prayer.ASR) { mutableListOf() } +=
            stringResource(Res.string.today_other_asr, school, isolated(formatClock(other.instant, zone, format)))
    }
    state.differ?.let { d ->
        val member = isolated(authorityShortName(d.memberNameKey))
        val prayer = localizedPrayerName(d.prayer)
        val line = if (d.begun) {
            stringResource(Res.string.today_differ_after, member, prayer, isolated(formatClock(d.shownStart, zone, format)))
        } else {
            stringResource(Res.string.today_differ_before, member, prayer, isolated(formatClock(d.memberStart, zone, format)))
        }
        lines.getOrPut(d.under) { mutableListOf() } += line
    }
    return lines
}

/**
 * Shown while notifications are on but Android's "Alarms & reminders" is off for Taqwa, which is
 * the default from Android 14: without it a notification can be up to an hour late, and
 * Settings › Notifications is too far away for a promise this central. The whole card is the tap
 * target, like the Qibla card below it; it opens the system page, and the card leaves on the next
 * foreground once the grant is there. Never on iOS, which has no such permission.
 */
@Composable
private fun ExactAlarmsCard(onAllow: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaCard(Modifier.padding(horizontal = Gutter)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onAllow,
                )
                .padding(16.dp),
        ) {
            Text(
                stringResource(Res.string.today_exact_alarms_label),
                style = TaqwaText.sectionLabel,
                color = colors.accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Res.string.today_exact_alarms_body),
                style = TaqwaText.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(Res.string.notifications_allow_exact_alarms),
                style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
        }
    }
}

/**
 * The way to the compass, where the thumb rests: a still north-up dial, the bearing and the
 * distance, and a chevron. The whole card is the tap target; no ripple, like every other
 * surface in the app — the pushed screen is the feedback.
 */
@Composable
private fun QiblaCard(bearingDegrees: Double, distanceKm: Double, format: PlatformFormat, onOpen: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaCard(Modifier.padding(horizontal = Gutter)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QiblaMiniDial(bearingDegrees)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.qibla_title), style = TaqwaText.rowLabel, color = colors.textPrimary)
                Text(
                    stringResource(
                        Res.string.today_qibla_detail,
                        format.localizedDigits(bearingDegrees.toInt()),
                        localizedGroupedKm(distanceKm, format),
                    ),
                    style = TaqwaText.caption.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            ForwardChevron()
        }
    }
}

/** The settings back chevron's mirror image: "onwards", so it points the way the text runs. */
@Composable
private fun ForwardChevron() {
    val colors = LocalTaqwaColors.current
    val pointsRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        fun x(fraction: Float) = if (pointsRight) w * fraction else w * (1f - fraction)
        val path = Path().apply {
            moveTo(x(0.36f), w * 0.14f)
            lineTo(x(0.68f), w * 0.50f)
            lineTo(x(0.36f), w * 0.86f)
        }
        drawPath(
            path = path,
            color = colors.textTertiary,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun NeedsLocationBody(onChooseCity: () -> Unit, onOpenLocationSettings: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .contentWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.today_no_location_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            // The same sentence onboarding used to ask the question, because it is the same
            // question — a user who declined there is reading it a second time, not a new one.
            stringResource(Res.string.onboarding_location_body),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        TaqwaPrimaryButton(stringResource(Res.string.today_choose_city), onChooseCity)
        Spacer(Modifier.height(4.dp))
        // To the system's settings, never to the system prompt: iOS asks only once, so after a
        // refusal the prompt cannot come back, and a screen that explains and then raises the
        // prompt beside a second button is what App Review rejects (guideline 5.1.1(iv)).
        TaqwaTextLink(stringResource(Res.string.today_location_settings), onOpenLocationSettings)
    }
}

/** `TimeZone.of` parses an IANA id; the zone only changes when the location does. */
@Composable
private fun rememberZone(id: String): TimeZone = remember(id) { TimeZone.of(id) }

/**
 * A clock time in the location's zone and the device locale's own digits — "3:42" for a Libyan
 * reader, "٣:٤٢" for an Egyptian one, with no setting between them (spec §4.2). Unlike the
 * countdown, a clock time never falls back to Western digits.
 */
internal fun formatClock(instant: Instant, zone: TimeZone, format: PlatformFormat): String {
    val t = instant.toLocalDateTime(zone)
    return format.clockTime(t.hour, t.minute)
}
