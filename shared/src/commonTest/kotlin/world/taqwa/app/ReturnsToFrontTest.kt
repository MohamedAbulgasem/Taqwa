package world.taqwa.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [onEachReturnToFront] on Compose Multiplatform's iOS lifecycle: the app in front is STARTED, and
 * active is RESUMED. Control Center, Notification Center and a system alert make the app inactive
 * (RESUMED to STARTED and back) without it ever leaving the front; going to the background is
 * STOPPED, CREATED here. Only the way back from the background is a return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnsToFrontTest {

    private class App : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    /** Runs [script] against an app whose returns are recorded, with the request count [requests] reads. */
    private fun returnsDuring(requests: () -> Long = { 0L }, script: App.() -> Unit): List<Long> {
        val returns = mutableListOf<Long>()
        // Unconfined, on the main dispatcher's scheduler: the watcher starts at once and each
        // lifecycle event is handled before the script's next line, as on the main thread.
        runTest(UnconfinedTestDispatcher()) {
            val app = App()
            val watching = launch { app.lifecycle.onEachReturnToFront(requests) { returns += it } }
            app.script()
            watching.cancel()
        }
        return returns
    }

    private fun App.goTo(state: Lifecycle.State) {
        registry.currentState = state
    }

    @Test
    fun theLaunchItselfIsNotAReturn() {
        val returns = returnsDuring {
            goTo(Lifecycle.State.CREATED)
            goTo(Lifecycle.State.RESUMED)
        }
        assertEquals(emptyList(), returns)
    }

    @Test
    fun controlCenterAndSystemAlertsAreNotAReturn() {
        val returns = returnsDuring {
            goTo(Lifecycle.State.RESUMED)
            repeat(3) {
                goTo(Lifecycle.State.STARTED)
                goTo(Lifecycle.State.RESUMED)
            }
        }
        assertEquals(emptyList(), returns)
    }

    @Test
    fun eachWayBackFromTheBackgroundIsOneReturn() {
        val returns = returnsDuring {
            goTo(Lifecycle.State.RESUMED)
            repeat(2) {
                goTo(Lifecycle.State.CREATED)
                goTo(Lifecycle.State.RESUMED)
            }
        }
        assertEquals(2, returns.size)
    }

    /**
     * A return carries the request count from when the app left the front, so a widget tap or
     * an adhkar reminder that brought it back (counted on the way in, before or after the return
     * itself) can be told apart from a plain return.
     */
    @Test
    fun aReturnCarriesTheRequestCountFromWhenTheAppLeft() {
        var requests = 3L
        val returns = returnsDuring(requests = { requests }) {
            goTo(Lifecycle.State.RESUMED)
            goTo(Lifecycle.State.CREATED)
            requests = 4L
            goTo(Lifecycle.State.RESUMED)
        }
        assertEquals(listOf(3L), returns)
    }
}
