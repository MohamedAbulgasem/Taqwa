package world.taqwa.app.wear.link

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The release build's side of the debug build's seams (`src/debug`): no feed is ever dropped and no
 * acknowledgement held, and no file name of the debug ones is in this build.
 */
internal object FeedDrop {

    /** The debug build's figure, kept so the listener reads one name in both builds; never waited here. */
    val ACK_HOLD: Duration = 20.seconds

    fun takeOne(filesDir: File): Boolean = false

    fun holdAck(filesDir: File): Boolean = false
}
