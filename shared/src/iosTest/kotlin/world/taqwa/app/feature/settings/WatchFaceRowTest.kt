package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.settings_add_to_apple_watch
import world.taqwa.app.resources.settings_add_to_apple_watch_failed
import world.taqwa.app.watchface.WatchFaceOffer
import world.taqwa.app.watchface.WatchFaceResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings' "Add to Apple Watch" row (plan 4 Task 10): shown while the offer holds, asked again
 * each time the app comes to the front, and a refused face said under the row until the screen is
 * left. [world.taqwa.app.watchface.IosWatchFaceOfferTest] holds what the offer makes of Swift's
 * answers; this holds what the row does with the offer's.
 */
@OptIn(ExperimentalTestApi::class)
class WatchFaceRowTest {

    /** An offer that says [offered], and answers each add with [answer] (none while it is null). */
    private class Offer(var offered: Boolean, var answer: WatchFaceResult? = null) : WatchFaceOffer {
        var asked = 0
        var adds = 0
        override fun available(): Boolean {
            asked += 1
            return offered
        }
        override fun add(onDone: (WatchFaceResult) -> Unit) {
            adds += 1
            answer?.let(onDone)
        }
    }

    /** The app in front or not, as the test moves it. */
    private class Front : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        fun leave() { registry.currentState = Lifecycle.State.CREATED }
        fun come() { registry.currentState = Lifecycle.State.RESUMED }
    }

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process. */
    @Composable
    private fun Row(offer: WatchFaceOffer, front: Front) {
        CompositionLocalProvider(LocalTaqwaColors provides LightColors, LocalLifecycleOwner provides front) {
            SettingsCard { WatchFaceRow(offer) }
        }
    }

    @Test
    fun noFaceOfferedShowsNoRow() = runComposeUiTest {
        val offer = Offer(offered = false)
        setContent { Row(offer, Front()) }
        waitForIdle()
        assertEquals(1, offer.asked)
        onAllNodesWithText(getString(Res.string.settings_add_to_apple_watch)).assertCountEquals(0)
    }

    @Test
    fun anOfferedFaceIsOneTapAway() = runComposeUiTest {
        val offer = Offer(offered = true, answer = WatchFaceResult.ADDED)
        setContent { Row(offer, Front()) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onNodeWithText(label).performClick()
        waitForIdle()
        assertEquals(1, offer.adds)
        onAllNodesWithText(getString(Res.string.settings_add_to_apple_watch_failed)).assertCountEquals(0)
    }

    @Test
    fun aRefusedFaceIsSaidUnderTheRowUntilOneIsAdded() = runComposeUiTest {
        val offer = Offer(offered = true, answer = WatchFaceResult.FAILED)
        setContent { Row(offer, Front()) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        val failed = getString(Res.string.settings_add_to_apple_watch_failed)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onNodeWithText(label).performClick()
        waitForIdle()
        onAllNodesWithText(failed).assertCountEquals(1)
        // The line stays while the sheet is asked again, and goes once a face is taken.
        offer.answer = null
        onNodeWithText(label).performClick()
        waitForIdle()
        onAllNodesWithText(failed).assertCountEquals(1)
        offer.answer = WatchFaceResult.ADDED
        onNodeWithText(label).performClick()
        waitForIdle()
        assertEquals(3, offer.adds)
        onAllNodesWithText(failed).assertCountEquals(0)
    }

    /**
     * VoiceOver says the line as it comes. CMP 1.12.1's iOS mediator announces a live region only
     * when that node's own label changes, and a node that merges nothing has no label of its own:
     * so the live region has to be one node with the line's words in it, not a box around them.
     */
    @Test
    fun aRefusedFaceIsSpokenWithItsWordsAsItComes() = runComposeUiTest {
        val offer = Offer(offered = true, answer = WatchFaceResult.FAILED)
        setContent { Row(offer, Front()) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        val failed = getString(Res.string.settings_add_to_apple_watch_failed)
        val politeLine = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
            hasText(failed)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
        onNodeWithText(label).performClick()
        waitForIdle()
        onAllNodes(politeLine).assertCountEquals(1)
    }

    @Test
    fun theOfferIsAskedAgainEachTimeTheAppComesToTheFront() = runComposeUiTest {
        val offer = Offer(offered = false)
        val front = Front()
        setContent { Row(offer, front) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        waitForIdle()
        onAllNodesWithText(label).assertCountEquals(0)
        // A watch paired in the Watch app, then Taqwa brought back.
        offer.offered = true
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        // And unpaired: the row goes, its line with it.
        offer.answer = WatchFaceResult.FAILED
        onNodeWithText(label).performClick()
        waitForIdle()
        offer.offered = false
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("no row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().isEmpty() }
        onAllNodesWithText(getString(Res.string.settings_add_to_apple_watch_failed)).assertCountEquals(0)
        assertEquals(3, offer.asked)
    }

    /** A watch unpaired and paired again while Settings stays open: the row comes back clean. */
    @Test
    fun aRowThatGoesAndComesBackBringsNoOldLine() = runComposeUiTest {
        val offer = Offer(offered = true, answer = WatchFaceResult.FAILED)
        val front = Front()
        setContent { Row(offer, front) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        val failed = getString(Res.string.settings_add_to_apple_watch_failed)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onNodeWithText(label).performClick()
        waitForIdle()
        onAllNodesWithText(failed).assertCountEquals(1)
        offer.offered = false
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("no row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().isEmpty() }
        offer.offered = true
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the row again", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onAllNodesWithText(failed).assertCountEquals(0)
    }

    @Test
    fun theFailureLineGoesWhenTheScreenIsLeft() = runComposeUiTest {
        val offer = Offer(offered = true, answer = WatchFaceResult.FAILED)
        var shown by mutableStateOf(true)
        val front = Front()
        setContent { if (shown) Row(offer, front) }
        val label = getString(Res.string.settings_add_to_apple_watch)
        val failed = getString(Res.string.settings_add_to_apple_watch_failed)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onNodeWithText(label).performClick()
        waitForIdle()
        onAllNodesWithText(failed).assertCountEquals(1)
        shown = false
        waitForIdle()
        shown = true
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 1 }
        onAllNodesWithText(failed).assertCountEquals(0)
    }
}
