package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationDiagnosticsTest {
    private val now = Instant.parse("2026-11-07T18:00:00Z")

    /** Every fact read, as an Android phone reports them. */
    private val full = NotificationState(
        notificationsAllowed = true,
        exactAlarmsAllowed = false,
        batteryOptimised = true,
        backgroundRestricted = true,
        ringerMode = 1,
        interruptionFilter = 1,
        recordsAlarms = true,
        lastAlarm = RecordedAlarm(now - 3.days - 2.hours),
        appUpdatedAt = now - 5.days - 7.hours,
    )

    @Test
    fun aPhoneThatReportsNothingAddsNoLines() {
        // iOS today: the email it sends stays exactly what it was.
        assertEquals(emptyList(), NotificationDiagnostics.lines(NotificationState(), now))
    }

    @Test
    fun everythingFitsInTwoLinesTheSettingsThenTheHistory() {
        assertEquals(
            listOf(
                "notifications: on, exact alarms: off, battery: restricted, ringer: vibrate",
                "last alarm: 3 d ago, app updated: 5 d ago",
            ),
            NotificationDiagnostics.lines(full, now),
        )
    }

    @Test
    fun neverMoreThanTwoLinesAndNeitherIsLongInTheWorstCase() {
        // The longest word in every place, and the longest relative times.
        val worst = NotificationState(
            notificationsAllowed = false,
            exactAlarmsAllowed = false,
            batteryOptimised = false,
            backgroundRestricted = false,
            ringerMode = 2,
            interruptionFilter = 2,
            recordsAlarms = true,
            lastAlarm = RecordedAlarm(now - 30.minutes, channelMissing = true),
            appUpdatedAt = now - 20.minutes,
        )
        val lines = NotificationDiagnostics.lines(worst, now)
        assertEquals(
            listOf(
                "notifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb",
                "last alarm: under 1 h ago (no channel), app updated: under 1 h ago",
            ),
            lines,
        )
        // Under 90 characters each at worst: the settings line's 85 is the four longest words at
        // once, which no phone gives. Below Android 12 exact alarms are always on, and from 12 an app
        // the battery optimisation leaves alone may always set them, so "not optimised" comes with
        // "exact alarms: on", and the longest settings line a phone can write is 84.
        assertEquals(listOf(85, 66), lines.map { it.length })
        val longestOnAPhone = NotificationDiagnostics.lines(worst.copy(exactAlarmsAllowed = true), now)[0]
        assertEquals("notifications: off, exact alarms: on, battery: not optimised, ringer: do not disturb", longestOnAPhone)
        assertEquals(84, longestOnAPhone.length)
        // Years without an update or an alarm cost a digit or two, not a line.
        val old = worst.copy(lastAlarm = RecordedAlarm(now - 1_500.days, true), appUpdatedAt = now - 1_500.days)
        val history = NotificationDiagnostics.lines(old, now)[1]
        assertEquals("last alarm: 1500 d ago (no channel), app updated: 1500 d ago", history)
        assertTrue(history.length <= 66, "${history.length}")
    }

    @Test
    fun eachPhoneSettingHasItsWord() {
        fun settings(state: NotificationState) = NotificationDiagnostics.lines(state, now).single()
        assertEquals("notifications: on", settings(NotificationState(notificationsAllowed = true)))
        assertEquals("notifications: off", settings(NotificationState(notificationsAllowed = false)))
        assertEquals("exact alarms: on", settings(NotificationState(exactAlarmsAllowed = true)))
        assertEquals("exact alarms: off", settings(NotificationState(exactAlarmsAllowed = false)))
    }

    @Test
    fun theBatteryIsOptimisedNotOptimisedOrRestrictedAndRestrictedWins() {
        fun battery(optimised: Boolean?, restricted: Boolean?) =
            NotificationDiagnostics.lines(NotificationState(batteryOptimised = optimised, backgroundRestricted = restricted), now)
        assertEquals(listOf("battery: optimised"), battery(true, false))
        assertEquals(listOf("battery: not optimised"), battery(false, false))
        assertEquals(listOf("battery: restricted"), battery(true, true))
        assertEquals(listOf("battery: restricted"), battery(false, true))
        assertEquals(listOf("battery: restricted"), battery(null, true))
        // Below Android 9 there is no "Restricted" to read.
        assertEquals(listOf("battery: optimised"), battery(true, null))
        assertEquals(emptyList(), battery(null, false))
    }

    @Test
    fun theRingerIsNormalVibrateSilentOrDoNotDisturbAndDoNotDisturbWins() {
        fun ringer(mode: Int?, filter: Int?) =
            NotificationDiagnostics.lines(NotificationState(ringerMode = mode, interruptionFilter = filter), now)
        assertEquals(listOf("ringer: normal"), ringer(2, 1))
        assertEquals(listOf("ringer: vibrate"), ringer(1, 1))
        assertEquals(listOf("ringer: silent"), ringer(0, 1))
        // Priority only, total silence and alarms only all silence an adhan the person did not let through.
        for (filter in 2..4) assertEquals(listOf("ringer: do not disturb"), ringer(2, filter), "filter $filter")
        assertEquals(listOf("ringer: do not disturb"), ringer(0, 3))
        assertEquals(listOf("ringer: do not disturb"), ringer(null, 2))
        // The filter Android could not name is not Do Not Disturb.
        assertEquals(listOf("ringer: normal"), ringer(2, 0))
        assertEquals(listOf("ringer: normal"), ringer(2, null))
        // A code this build does not know is printed as it is.
        assertEquals(listOf("ringer: 7"), ringer(7, 1))
    }

    @Test
    fun theLastAlarmIsOnlyWhenItCameNotTheLevelThePersonChose() {
        // The level is a setting of the person's own, and About, the site and the policy say
        // settings never leave the phone; support asks for it in the reply instead.
        val state = NotificationState(recordsAlarms = true, lastAlarm = RecordedAlarm(now - 5.hours))
        assertEquals(listOf("last alarm: 5 h ago"), NotificationDiagnostics.lines(state, now))
        val lines = NotificationDiagnostics.lines(full, now)
        for (level in listOf("takbir", "adhan", "(notification", "(silent")) {
            assertTrue(lines.none { level in it }, "$level in $lines")
        }
    }

    @Test
    fun anAlarmAndroidHadNoChannelForIsMarked() {
        val state = NotificationState(recordsAlarms = true, lastAlarm = RecordedAlarm(now - 26.hours, channelMissing = true))
        assertEquals(listOf("last alarm: 26 h ago (no channel)"), NotificationDiagnostics.lines(state, now))
    }

    @Test
    fun aPhoneThatRecordsAlarmsSaysSoWhenThereIsNone() {
        // "None recorded" days after the update is the finding: an alarm that never reached the
        // app is exactly what a battery manager that stops apps leaves.
        assertEquals(
            listOf("last alarm: none recorded, app updated: 4 d ago"),
            NotificationDiagnostics.lines(NotificationState(recordsAlarms = true, appUpdatedAt = now - 4.days), now),
        )
        assertEquals(
            listOf("last alarm: none recorded"),
            NotificationDiagnostics.lines(NotificationState(recordsAlarms = true), now),
        )
    }

    @Test
    fun aFactThatCouldNotBeReadLeavesOnlyItsOwnPartOut() {
        assertEquals(
            listOf("notifications: on, ringer: vibrate", "app updated: 5 d ago"),
            NotificationDiagnostics.lines(
                full.copy(exactAlarmsAllowed = null, batteryOptimised = null, backgroundRestricted = null, recordsAlarms = false),
                now,
            ),
        )
        assertEquals(
            listOf("last alarm: 3 d ago"),
            NotificationDiagnostics.lines(NotificationState(recordsAlarms = true, lastAlarm = full.lastAlarm), now),
        )
    }

    @Test
    fun timesAreToTheHourAtBestThenToTheDay() {
        fun history(ago: Duration) =
            NotificationDiagnostics.lines(NotificationState(appUpdatedAt = now - ago), now).single()
        assertEquals("app updated: under 1 h ago", history(0.seconds))
        assertEquals("app updated: under 1 h ago", history(59.minutes))
        assertEquals("app updated: 1 h ago", history(61.minutes))
        assertEquals("app updated: 47 h ago", history(47.hours + 59.minutes))
        assertEquals("app updated: 2 d ago", history(2.days))
        assertEquals("app updated: 6 d ago", history(6.days + 23.hours))
    }

    @Test
    fun noLineCarriesAClockTimeOrAMomentThatCanBePutBackOnIt() {
        // Relative times only: a prayer's clock time would tell support roughly where the phone is.
        val lines = NotificationDiagnostics.lines(
            full.copy(lastAlarm = RecordedAlarm(now - 47.minutes), appUpdatedAt = now - 2.hours - 13.minutes),
            now,
        )
        assertTrue(lines.none { Regex("""\d{1,2}:\d{2}""").containsMatchIn(it) }, lines.toString())
        assertTrue(lines.none { "2026" in it }, lines.toString())
        // Nor one that can be: the mail's own send time beside "47 min ago" would give the
        // prayer's time to the minute.
        assertTrue(lines.none { Regex("""\d+ ?min""").containsMatchIn(it) }, lines.toString())
        assertEquals("last alarm: under 1 h ago, app updated: 2 h ago", lines[1])
    }

    @Test
    fun aMomentAfterNowFromAClockSetBackIsSaidToBeAhead() {
        val ahead = NotificationState(
            recordsAlarms = true,
            lastAlarm = RecordedAlarm(now + 3.hours),
            appUpdatedAt = now + 20.minutes,
        )
        assertEquals(
            listOf("last alarm: in 3 h, app updated: in under 1 h"),
            NotificationDiagnostics.lines(ahead, now),
        )
    }
}
