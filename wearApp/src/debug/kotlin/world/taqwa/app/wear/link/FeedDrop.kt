package world.taqwa.app.wear.link

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Debug builds only (the release build's `FeedDrop` answers false to both): Task 7's seams for a
 * delivery lost on the way and an acknowledgement late, each set once by a file the harness puts in
 * the app's files, and taken (deleted) by the message it applies to:
 *
 *     adb -s emulator-5558 shell run-as world.taqwa.app touch files/drop-next-feed
 *     adb -s emulator-5558 shell run-as world.taqwa.app touch files/hold-next-ack
 */
internal object FeedDrop {

    /** How long a held acknowledgement waits before it goes. */
    val ACK_HOLD: Duration = 20.seconds

    private const val DROP = "drop-next-feed"
    private const val HOLD = "hold-next-ack"

    /** Whether the next feed is to be dropped unread: true once for each `drop-next-feed`, which it deletes. */
    fun takeOne(filesDir: File): Boolean = File(filesDir, DROP).delete()

    /** Whether the next acknowledgement is to be held back [ACK_HOLD]: true once for each `hold-next-ack`. */
    fun holdAck(filesDir: File): Boolean = File(filesDir, HOLD).delete()
}
