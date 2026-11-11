package world.taqwa.app.watchface

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone's "Add to Apple Watch" offer (plan 4 Task 10), over a stand-in for the hook
 * `iOSApp.swift` installs (`WatchFaceShare`). Swift answers in Booleans alone: whether the face is
 * offered (a paired watch and a bundled `Taqwa.watchface`), and whether ClockKit's library took it.
 * What the row makes of those answers is decided here, where it can be tested.
 */
class IosWatchFaceOfferTest {

    @AfterTest
    fun clearHook() {
        WatchFaceShareBridge.hook = null
    }

    /** A hook that says [offered], and answers each add with [added]; it counts the adds asked. */
    private class StandIn(var offered: Boolean, private val added: Boolean?) : WatchFaceShareHook {
        var adds = 0
        override fun isAvailable(): Boolean = offered
        override fun add(done: (Boolean) -> Unit) {
            adds += 1
            added?.let(done)
        }
    }

    /** Every answer [add] gives, in order. */
    private fun answersOf(offer: WatchFaceOffer): List<WatchFaceResult> {
        val answers = mutableListOf<WatchFaceResult>()
        offer.add { answers += it }
        return answers
    }

    @Test
    fun withoutAHookThereIsNoFace() {
        WatchFaceShareBridge.hook = null
        val offer = createWatchFaceOffer()
        assertFalse(offer.available())
        // Nothing to ask, so the add is a failure at once, never a silence the row waits on.
        assertEquals(listOf(WatchFaceResult.FAILED), answersOf(offer))
    }

    @Test
    fun theHookSaysWhenTheFaceIsOffered() {
        val hook = StandIn(offered = true, added = true)
        WatchFaceShareBridge.hook = hook
        val offer = createWatchFaceOffer()
        assertTrue(offer.available())
        // Asked again each time, never remembered: a watch is paired and unpaired outside the app.
        hook.offered = false
        assertFalse(offer.available())
        hook.offered = true
        assertTrue(offer.available())
        // A hook that cannot tell is no offer.
        WatchFaceShareBridge.hook = object : WatchFaceShareHook {
            override fun isAvailable(): Boolean = error("WCSession unavailable")
            override fun add(done: (Boolean) -> Unit) = done(true)
        }
        assertFalse(offer.available())
    }

    @Test
    fun aFaceTheLibraryRefusesIsFailed() {
        val hook = StandIn(offered = true, added = false)
        WatchFaceShareBridge.hook = hook
        assertEquals(listOf(WatchFaceResult.FAILED), answersOf(createWatchFaceOffer()))
        assertEquals(1, hook.adds)
        // A hook that throws is refused too, and answered once.
        WatchFaceShareBridge.hook = object : WatchFaceShareHook {
            override fun isAvailable(): Boolean = true
            override fun add(done: (Boolean) -> Unit) = error("CLKWatchFaceLibrary unavailable")
        }
        assertEquals(listOf(WatchFaceResult.FAILED), answersOf(createWatchFaceOffer()))
    }

    @Test
    fun anAddedFaceIsAdded() {
        val hook = StandIn(offered = true, added = true)
        WatchFaceShareBridge.hook = hook
        assertEquals(listOf(WatchFaceResult.ADDED), answersOf(createWatchFaceOffer()))
        assertEquals(1, hook.adds)
        // ClockKit's completion is not promised to come once; the row hears only the first.
        WatchFaceShareBridge.hook = object : WatchFaceShareHook {
            override fun isAvailable(): Boolean = true
            override fun add(done: (Boolean) -> Unit) {
                done(true)
                done(false)
            }
        }
        assertEquals(listOf(WatchFaceResult.ADDED), answersOf(createWatchFaceOffer()))
    }

    /**
     * The owner's report of 9 November: Settings asks the offer again when the session changes
     * while it is in front. Swift says so from `TaqwaWatchSession`'s delegate (activation,
     * inactive, deactivated, the watch's state), and the offer says it to whoever listens then.
     * None is kept for later: a listener asks the offer at its own start.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSessionChangeIsSaidToWhoeverListensThen() = runTest {
        val offer = createWatchFaceOffer()
        WatchFaceShareBridge.sessionChanged()
        var heard = 0
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) { offer.changes().collect { heard += 1 } }
        runCurrent()
        assertEquals(0, heard, "a change before anyone listened is kept for no one")
        WatchFaceShareBridge.sessionChanged()
        runCurrent()
        assertEquals(1, heard)
        WatchFaceShareBridge.sessionChanged()
        runCurrent()
        assertEquals(2, heard)
        listening.cancel()
    }
}
