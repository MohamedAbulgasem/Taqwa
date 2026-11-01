package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.Prayer
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widgets_lock_then
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetPrayerRow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Lock Screen card's picture of the rectangular accessory, which TaqwaWidgetViews.swift draws
 * as two lines: the next prayer with its time at the end, then "then" and the prayer after it, in
 * a smaller, fainter line. It shows no countdown. The picture once read "Maghrib in 0:21 · 18:32",
 * and then "in 0:21" under the first line, neither of which the real accessory ever shows. Its
 * first line is the accessory's own "Maghrib in" phrase (the owner's decision, 31 October), where
 * it once named the prayer alone.
 */
@OptIn(ExperimentalTestApi::class)
class LockScreenAccessoryTest {

    private val maghrib = WidgetContent(
        nextPrayerDisplayName = "Maghrib · المغرب",
        countdownMinutes = 21,
        nextClockTime = "18:32",
        rows = listOf(
            WidgetPrayerRow(Prayer.ASR, "Asr · العصر", "16:03", isCurrent = true),
            WidgetPrayerRow(Prayer.MAGHRIB, "Maghrib · المغرب", "18:32", isCurrent = false),
            WidgetPrayerRow(Prayer.ISHA, "Isha · العشاء", "19:50", isCurrent = false),
        ),
        ringProgress = 0.86f,
        countdownLabel = "Maghrib in",
        nextPrayerShortName = "Maghrib",
    )

    @Test
    fun thePrayerAndItsTimeShareTheFirstLineAndThePrayerAfterItComesUnderThem() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalTaqwaColors provides LightColors) { LockScreenAccessory(maghrib) } }
        val prayer = onNodeWithText("Maghrib in").getUnclippedBoundsInRoot()
        val time = onNodeWithText("18:32").getUnclippedBoundsInRoot()
        val then = onNodeWithText(getString(Res.string.widgets_lock_then, "Isha · العشاء")).getUnclippedBoundsInRoot()
        assertTrue(prayer.right <= time.left, "the time is not after the prayer on its line")
        assertTrue(time.top < prayer.bottom && prayer.top < time.bottom, "the prayer and its time are not on one line")
        assertTrue(then.top >= prayer.bottom && then.top >= time.bottom, "the prayer after it is not under the first line")
    }

    /**
     * The owner's decision of 1 November: the second line gives the prayer after the next its time, at
     * the line's end under the first line's time, where it once named the prayer alone.
     */
    @Test
    fun thePrayerAfterTheNextHasItsTimeAtTheEndOfItsLine() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalTaqwaColors provides LightColors) { LockScreenAccessory(maghrib) } }
        val then = onNodeWithText(getString(Res.string.widgets_lock_then, "Isha · العشاء")).getUnclippedBoundsInRoot()
        val thenTime = onNodeWithText("19:50").getUnclippedBoundsInRoot()
        val firstTime = onNodeWithText("18:32").getUnclippedBoundsInRoot()
        assertTrue(then.right <= thenTime.left, "Isha's time is not after its name")
        assertTrue(thenTime.top < then.bottom && then.top < thenTime.bottom, "Isha and its time are not on one line")
        assertTrue(thenTime.top >= firstTime.bottom, "Isha's time is not under the first line")
        assertTrue(abs((thenTime.right - firstTime.right).value) < 1f, "Isha's time does not end where Maghrib's does")
    }

    @Test
    fun theAccessoryShowsNoCountdownAsTheRealOneShowsNone() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalTaqwaColors provides LightColors) { LockScreenAccessory(maghrib) } }
        onAllNodesWithText("0:21", substring = true).assertCountEquals(0)
    }

    /** The same accessory in Arabic, right to left, as the mirror writes it for an Arabic interface. */
    private val maghribArabic = WidgetContent(
        nextPrayerDisplayName = "المغرب",
        countdownMinutes = 21,
        nextClockTime = "18:32",
        rows = listOf(
            WidgetPrayerRow(Prayer.ASR, "العصر", "16:03", isCurrent = true),
            WidgetPrayerRow(Prayer.MAGHRIB, "المغرب", "18:32", isCurrent = false),
            WidgetPrayerRow(Prayer.ISHA, "العشاء", "19:50", isCurrent = false),
        ),
        ringProgress = 0.86f,
        countdownLabel = "متبقٍّ على المغرب",
        nextPrayerShortName = "المغرب",
    )

    /** The accessory alone, [width] wide at most, with the reader's text at [fontScale] times. */
    @Composable
    private fun Accessory(
        fontScale: Float,
        width: Dp,
        content: WidgetContent = maghrib,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalDensity provides Density(density.density, fontScale),
            LocalLayoutDirection provides direction,
        ) {
            Box(Modifier.width(width)) { LockScreenAccessory(content) }
        }
    }

    /**
     * The Lead's follow-up 2 (31 Oct): each of the picture's lines was one line cut at the
     * accessory's edge, so at the largest text sizes a name could end mid-word. They shrink as the
     * real accessory's do, and where that is not enough they wrap ([accessoryLineSize] holds the
     * sizes): every line is whole. The layout's own record of its style keeps the size asked for,
     * not the size drawn, so the sizes are tested there rather than here.
     */
    @Test
    fun atLargeTextSizesEveryLineIsWhole() = runComposeUiTest {
        // The largest text an iPhone offers, in an accessory narrower than the iPhone SE's.
        setContent { Accessory(fontScale = 3.2f, width = 200.dp) }
        for (text in listOf("Maghrib in", "18:32", getString(Res.string.widgets_lock_then, "Isha · العشاء"))) {
            assertFalse(layoutOf(text).hasVisualOverflow, "\"$text\" is cut")
        }
    }

    /** The Arabic phrase, longer than the English, is whole at the largest text too, right to left. */
    @Test
    fun atLargeTextSizesTheArabicFirstLineIsWhole() = runComposeUiTest {
        setContent { Accessory(fontScale = 3.2f, width = 200.dp, maghribArabic, LayoutDirection.Rtl) }
        for (text in listOf("متبقٍّ على المغرب", "18:32")) {
            assertFalse(layoutOf(text).hasVisualOverflow, "\"$text\" is cut")
        }
    }

    /** At the default text size the picture is as it was: each line whole, on one line. */
    @Test
    fun atTheDefaultTextSizeEachLineIsOneWholeLine() = runComposeUiTest {
        setContent { Accessory(fontScale = 1f, width = 300.dp) }
        for (text in listOf("Maghrib in", "18:32", getString(Res.string.widgets_lock_then, "Isha · العشاء"))) {
            val line = layoutOf(text)
            assertEquals(1, line.lineCount, "\"$text\" is not on one line")
            assertFalse(line.hasVisualOverflow, "\"$text\" is cut")
        }
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /** The first line is the accessory's own: the "Maghrib in" phrase, as `entry.headline` reads, never the
     * name alone nor the pair with the Arabic. */
    @Test
    fun theFirstLineIsTheCountdownPhraseAsTheRealAccessorysIs() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalTaqwaColors provides LightColors) { LockScreenAccessory(maghrib) } }
        onAllNodesWithText("Maghrib in").assertCountEquals(1)
        onAllNodesWithText("Maghrib").assertCountEquals(0)
        onAllNodesWithText("Maghrib · المغرب").assertCountEquals(0)
    }
}
