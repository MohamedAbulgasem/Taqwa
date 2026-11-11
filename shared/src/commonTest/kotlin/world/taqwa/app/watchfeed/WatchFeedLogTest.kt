package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watchfeed.WatchAskStep.BUILD
import world.taqwa.app.watchfeed.WatchAskStep.ECHO
import world.taqwa.app.watchfeed.WatchAskStep.ENCODE
import world.taqwa.app.watchfeed.WatchAskStep.RECORD
import world.taqwa.app.watchfeed.WatchAskStep.SEND
import world.taqwa.app.watchfeed.WatchAskStep.WATCHES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a release log line names a delivery (Ruling 52 item 4): by its number and fingerprint alone.
 * The origin is the install's one random number, the same in every feed that phone sends, so a log
 * line never carries it; nor the place or the bearing, which no delivery holds.
 */
class WatchFeedLogTest {

    private val origin = "0123456789abcdef"

    @Test
    fun aDeliveryIsLoggedByItsNumberAndFingerprintAlone() {
        val delivery = WatchDelivery(origin, 7, "0011223344556677")
        assertEquals("7/0011223344556677", loggedDelivery(delivery.text))
        assertEquals("9223372036854775807/0011223344556677", loggedDelivery(WatchDelivery(origin, Long.MAX_VALUE, "0011223344556677").text))
    }

    @Test
    fun anEchoOfNothingIsLoggedAsNone() {
        assertEquals("none", loggedDelivery(""))
    }

    /** What is not a delivery is never logged as it came: it may hold anything, an origin among it. */
    @Test
    fun anythingElseIsNeverLoggedAsItCame() {
        for (text in listOf(
            origin,
            "$origin/0/0011223344556677",
            "$origin/07/0011223344556677",
            "/taqwa/feed/$origin/7/0011223344556677",
            "London 51.5074,-0.1278",
        )) {
            assertEquals("unreadable", loggedDelivery(text), text)
            assertFalse(origin in loggedDelivery(text), text)
        }
    }

    /**
     * The step line of one ask (Ruling 70 item 1): its reason, whether it is the ask asked
     * again, its result and whether the budget cut it, then each step with its milliseconds, an
     * unfinished one marked. No place, no person, no delivery.
     */
    @Test
    fun anAsksStepsAreOneDebugLine() {
        assertEquals(
            "TaqwaWatch STEPS RESCHEDULED SENT: watches 12ms, build 340ms, encode 3ms, record 2ms, send 41ms",
            askStepsLine(
                AskTimes(
                    WatchSyncReason.RESCHEDULED, again = false, result = WatchSendResult.SENT, cut = false,
                    steps = listOf(AskStep(WATCHES, 12), AskStep(BUILD, 340), AskStep(ENCODE, 3), AskStep(RECORD, 2), AskStep(SEND, 41)),
                ),
            ),
        )
        assertEquals(
            "TaqwaWatch STEPS WATCH_ASKED again FAILED cut: echo 2ms, watches 812ms, build 4186ms unfinished",
            askStepsLine(
                AskTimes(
                    WatchSyncReason.WATCH_ASKED, again = true, result = WatchSendResult.FAILED, cut = true,
                    steps = listOf(AskStep(ECHO, 2), AskStep(WATCHES, 812), AskStep(BUILD, 4_186, done = false)),
                ),
            ),
        )
        assertEquals(
            "TaqwaWatch STEPS WATCH_APPEARED NO_WATCH: watches 9ms",
            askStepsLine(AskTimes(WatchSyncReason.WATCH_APPEARED, again = false, result = WatchSendResult.NO_WATCH, cut = false, steps = listOf(AskStep(WATCHES, 9)))),
        )
    }

    /**
     * The step line on a signed build (Ruling 70 item 1, review round 2): P5.13's Galaxy pass runs
     * the release candidate, so the line is printed in a debug build, or once its tag is set to
     * DEBUG (`adb shell setprop log.tag.TaqwaWatch DEBUG`), as plan 5's compass log is
     * (`qiblaLogOn`). Off only when neither.
     */
    @Test
    fun theStepsLineIsOnInADebugBuildOrWhenItsTagIsEnabled() {
        assertTrue(stepsLogOn(debugBuild = true, tagEnabled = false))
        assertTrue(stepsLogOn(debugBuild = true, tagEnabled = true))
        assertTrue(stepsLogOn(debugBuild = false, tagEnabled = true))
        assertFalse(stepsLogOn(debugBuild = false, tagEnabled = false))
    }

    /** The tag P5.13's `setprop log.tag.TaqwaWatch DEBUG` names is the word every watch-feed line begins with. */
    @Test
    fun theTagTheStepsLineIsAskedByIsItsFirstWord() {
        assertEquals("TaqwaWatch", WATCH_LOG_TAG)
        val line = askStepsLine(AskTimes(WatchSyncReason.WATCH_ASKED, again = true, result = WatchSendResult.SENT, cut = false, steps = listOf(AskStep(WATCHES, 9))))
        assertTrue(line.startsWith("$WATCH_LOG_TAG STEPS "), line)
    }
}
