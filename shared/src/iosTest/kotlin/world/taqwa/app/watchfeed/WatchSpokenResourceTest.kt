package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchSpoken
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `watchSpoken()`'s default reads the phone's own strings through Compose Multiplatform's resource
 * environment, off any composition, as the sync will. The simulator runs these tests in English, as
 * the other iOS tests that read resources do, so the answer is the English sample's `ui.spoken`.
 */
class WatchSpokenResourceTest {

    @Test
    fun theDefaultReadsThePhonesOwnStrings() = runTest {
        assertEquals(
            WatchSpoken(
                countdown = "%1\$s in %2\$s, at %3\$s",
                countdownSoon = "%1\$s in under a minute, at %2\$s",
                hoursMinutes = "%1\$s %2\$s",
                hours = listOf(WatchCount(0, "%1\$s hours"), WatchCount(1, "%1\$s hour"), WatchCount(99, "%1\$s hours")),
                minutes = listOf(WatchCount(0, "%1\$s minutes"), WatchCount(1, "%1\$s minute"), WatchCount(99, "%1\$s minutes")),
            ),
            watchSpoken(),
        )
    }
}
