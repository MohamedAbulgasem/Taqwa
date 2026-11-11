package world.taqwa.app.wear.link

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.wear.TestFeeds

/**
 * Task 7's daily handshake on demand (plan 2 Task 6): a handshake a minute earlier leaves the stamp
 * inside [RefreshPolicy.GAP_DAILY], so the debug nudge forgets the last ask before it nudges.
 */
class DebugNudgeTest {

    private lateinit var filesDir: File

    @BeforeTest
    fun setUp() {
        filesDir = createTempDirectory("debug-nudge").toFile()
    }

    @AfterTest
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun theDebugNudgeForgetsTheLastAsk() {
        val feed = TestFeeds.sample
        val now = feed.writtenAt + 3_600L
        val stamp = File(filesDir, PhoneAsk.STAMP)
        stamp.writeText((now - 60).toString())
        assertFalse(RefreshPolicy.shouldAsk(AskWhy.DAILY, feed, fromPhone = true, now, PhoneAsk.lastAskedAt(stamp)))

        DebugNudge.forgetLastAsk(filesDir)

        assertNull(PhoneAsk.lastAskedAt(stamp))
        assertTrue(RefreshPolicy.shouldAsk(AskWhy.DAILY, feed, fromPhone = true, now, PhoneAsk.lastAskedAt(stamp)))
    }
}
