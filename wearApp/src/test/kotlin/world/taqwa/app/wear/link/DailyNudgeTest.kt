package world.taqwa.app.wear.link

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import world.taqwa.app.wear.link.DailyNudge.Arming

/**
 * When the daily nudge's alarm is set again (plan 2 Task 6, Ruling 22's safety net). The app cannot
 * ask the system whether its alarm is still set. What it sees is whether the alarm's
 * `PendingIntent` is still found, the nudge's anchor (when it last went off, or was first set), and
 * whether the last process was stopped by the user. On Wear OS 3 to 5 (API 30-34) a force stop
 * removes the alarm but keeps that `PendingIntent`, so the `PendingIntent` alone says nothing.
 */
class DailyNudgeTest {

    private val now = 1_792_310_400L
    private val hour = 3_600L
    private val day = DailyNudge.DAY
    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("daily-nudge").toFile()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun theFirstArmingIsADayAway() {
        assertEquals(Arming(day, now), DailyNudge.arming(found = false, anchor = null, now = now, stoppedByUser = false))
    }

    @Test
    fun aLiveAlarmIsLeftAlone() {
        // The process starts many times a day: setting a live alarm again at each start would push
        // its next nudge out each time, and it might never go off.
        for (age in listOf(0L, hour, 23 * hour, 30 * hour, DailyNudge.STALE, -hour)) {
            assertNull(DailyNudge.arming(found = true, anchor = now - age, now = now, stoppedByUser = false), "anchored ${age}s ago")
        }
    }

    @Test
    fun aForceStopSetsItAgainOnTheSameSchedule() {
        // The review's case: Settings, Apps, Taqwa, Force stop on Wear OS 5. The PendingIntent is still
        // found, but the alarm is gone. It is set again for when it was due, never a day from the start.
        assertEquals(Arming(14 * hour, null), DailyNudge.arming(found = true, anchor = now - 10 * hour, now = now, stoppedByUser = true))
        assertEquals(Arming(0, null), DailyNudge.arming(found = true, anchor = now - 30 * hour, now = now, stoppedByUser = true), "overdue: due now")
        assertEquals(
            Arming(day, null),
            DailyNudge.arming(found = true, anchor = now + hour, now = now, stoppedByUser = true),
            "anchored ahead of a clock set back: a day at most",
        )
    }

    @Test
    fun aRebootOrAStoppedStateKeepsTheSchedule() {
        // A reboot clears alarms and PendingIntents alike, and so does a force stop from Android 15 on.
        assertEquals(Arming(14 * hour, null), DailyNudge.arming(found = false, anchor = now - 10 * hour, now = now, stoppedByUser = false))
        assertEquals(Arming(14 * hour, null), DailyNudge.arming(found = false, anchor = now - 10 * hour, now = now, stoppedByUser = true))
    }

    @Test
    fun aNudgeUnheardForADayAndAHalfIsDueNow() {
        // An alarm lost without an exit reason to say so, as when the app is force stopped while no process
        // runs: the anchor goes stale, and the next process start sets the alarm due now.
        assertEquals(Arming(0, now), DailyNudge.arming(found = true, anchor = now - DailyNudge.STALE - 1, now = now, stoppedByUser = false))
        assertEquals(Arming(0, now), DailyNudge.arming(found = false, anchor = now - 10 * day, now = now, stoppedByUser = false), "a watch off for days")
        assertEquals(
            Arming(0, now),
            DailyNudge.arming(found = true, anchor = now + DailyNudge.STALE + 1, now = now, stoppedByUser = false),
            "a clock set back by days",
        )
    }

    @Test
    fun withoutAnAnchorItIsSetADayAway() {
        // Clear data deletes the anchor with the app's files (and force stops it first); a build from before
        // the anchor never wrote one.
        assertEquals(Arming(day, now), DailyNudge.arming(found = true, anchor = null, now = now, stoppedByUser = false))
        assertEquals(Arming(day, now), DailyNudge.arming(found = true, anchor = null, now = now, stoppedByUser = true))
    }

    @Test
    fun theAnchorIsTheLastNudgeOrNothing() {
        val anchor = File(directory, DailyNudge.ANCHOR)
        assertNull(readStamp(anchor), "never set")
        writeStamp(anchor, now)
        assertEquals(now, readStamp(anchor))
        anchor.writeText("garbage")
        assertNull(readStamp(anchor))
        anchor.delete()
        anchor.mkdir()
        assertEquals(false, writeStamp(anchor, now), "a stamp that cannot be written says so, and never throws")
        assertNull(readStamp(anchor))
    }
}
