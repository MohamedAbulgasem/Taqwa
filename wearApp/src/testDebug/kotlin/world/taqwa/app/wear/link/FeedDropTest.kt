package world.taqwa.app.wear.link

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Task 7's seams in the debug build (plan 2 Task 6): a feed dropped on arrival, and an
 * acknowledgement held back, each once, by a file the harness puts in the app's files.
 */
class FeedDropTest {

    private lateinit var filesDir: File

    @BeforeTest
    fun setUp() {
        filesDir = createTempDirectory("feed-drop").toFile()
    }

    @AfterTest
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun aDroppedFeedIsTakenOnce() {
        assertFalse(FeedDrop.takeOne(filesDir), "no file, no drop")
        val file = File(filesDir, "drop-next-feed")
        file.writeText("")

        assertTrue(FeedDrop.takeOne(filesDir))
        assertFalse(file.exists(), "taken: the file is gone")
        assertFalse(FeedDrop.takeOne(filesDir), "once")
        assertFalse(FeedDrop.holdAck(filesDir), "and it is not the held acknowledgement's")
    }

    @Test
    fun aHeldAckIsTakenOnce() {
        assertFalse(FeedDrop.holdAck(filesDir))
        val file = File(filesDir, "hold-next-ack")
        file.writeText("")

        assertTrue(FeedDrop.holdAck(filesDir))
        assertFalse(file.exists())
        assertFalse(FeedDrop.holdAck(filesDir))
        assertFalse(FeedDrop.takeOne(filesDir))
        assertEquals(20.seconds, FeedDrop.ACK_HOLD)
    }
}
