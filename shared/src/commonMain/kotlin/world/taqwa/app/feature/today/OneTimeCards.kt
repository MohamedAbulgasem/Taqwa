package world.taqwa.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_about_these_times
import world.taqwa.app.resources.today_card_cautious_body
import world.taqwa.app.resources.today_card_cautious_title
import world.taqwa.app.resources.today_card_ok
import world.taqwa.app.resources.today_card_sunni_body
import world.taqwa.app.resources.today_card_sunni_title
import world.taqwa.app.resources.today_match_my_mosque

/** The cards the Prayer screen shows once, below the prayer list (spec §2.1). */
enum class OneTimeCard {
    /** Cautious-times places (decision §10.9): "Mosques here follow different timetables …". */
    CAUTIOUS,

    /** The registry's Shia regions: "These are Sunni prayer times …". */
    SUNNI,
}

/**
 * Which one-time card this launch shows (spec §2.1, decision §10.9: a once-only card). One at a
 * time, the cautious card first. A card is stored as seen the first time it is shown, so it never
 * comes back on a later launch whether or not it was answered; within the launch it stays until it
 * is answered, and only while it still applies to the place on screen. The other card waits for the
 * next launch ([OneTimeCardSession]).
 */
object OneTimeCards {
    fun pick(
        cautiousPlace: Boolean,
        shiaRegion: Boolean,
        cautiousSeen: Boolean,
        sunniSeen: Boolean,
        shownThisLaunch: OneTimeCard?,
        answeredThisLaunch: Boolean,
    ): OneTimeCard? = when {
        answeredThisLaunch -> null
        shownThisLaunch != null -> shownThisLaunch.takeIf {
            when (it) {
                OneTimeCard.CAUTIOUS -> cautiousPlace
                OneTimeCard.SUNNI -> shiaRegion
            }
        }
        cautiousPlace && !cautiousSeen -> OneTimeCard.CAUTIOUS
        shiaRegion && !sunniSeen -> OneTimeCard.SUNNI
        else -> null
    }
}

/**
 * The one-time card shown since the app started, and whether it was answered. It outlives the
 * Prayer screen's view model, which a language change replaces, so "the next launch" means the next
 * process.
 */
class OneTimeCardSession {
    var shown: OneTimeCard? = null
    var answered: Boolean = false

    companion object {
        /** This process's. */
        val process = OneTimeCardSession()
    }
}

/**
 * A one-time card (mockup §2): a title, one sentence and two text actions, the first in the accent
 * and "OK" quiet, on a card whose edge carries a little accent. Either action answers the card.
 */
@Composable
internal fun OneTimeCardView(
    card: OneTimeCard,
    onAction: () -> Unit,
    onOk: () -> Unit,
    horizontalPadding: Dp,
) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(18.dp)
    val (title, body, action) = when (card) {
        OneTimeCard.CAUTIOUS -> Triple(
            Res.string.today_card_cautious_title,
            Res.string.today_card_cautious_body,
            Res.string.today_match_my_mosque,
        )
        OneTimeCard.SUNNI -> Triple(
            Res.string.today_card_sunni_title,
            Res.string.today_card_sunni_body,
            Res.string.today_about_these_times,
        )
    }
    Column(
        Modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.accent.copy(alpha = 0.45f).compositeOver(colors.hairline), shape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(
            stringResource(title),
            style = TaqwaText.caption.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(body),
            style = TaqwaText.caption.copy(lineHeight = 20.sp),
            color = colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CardAction(stringResource(action), colors.accent, onAction)
            CardAction(stringResource(Res.string.today_card_ok), colors.textSecondary, onOk)
        }
    }
}

/** A card's text action: its own 44 dp target, no ripple, like the rest of the screen. */
@Composable
private fun CardAction(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = TaqwaText.caption.copy(fontWeight = FontWeight.ExtraBold), color = tint)
    }
}
