package world.taqwa.app.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeScheduler : NotificationScheduler {
    val scheduledCalls = mutableListOf<List<ScheduledNotification>>()
    var cancelCalls = 0
    override fun scheduleAll(plan: List<ScheduledNotification>) { scheduledCalls += plan }
    override fun cancelAll() { cancelCalls++ }
}

class NotificationCoordinatorTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val now = Instant.parse("2026-09-06T00:30:00Z")

    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "build/test-coordinator-$name.preferences_pb".toPath() }
    )

    @Test
    fun withNoLocationEverythingIsCancelledAndNothingIsScheduled() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("no-loc"),
            locationOf = { null }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(plan.isEmpty())
        assertEquals(1, scheduler.cancelCalls)
        assertTrue(scheduler.scheduledCalls.isEmpty())
    }

    @Test
    fun withALocationTheFullWindowIsHandedToTheScheduler() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("with-loc"),
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertEquals(60, plan.size) // 12-day window at 5/day, default settings
        assertEquals(1, scheduler.scheduledCalls.size)
        assertEquals(plan, scheduler.scheduledCalls.single())
    }

    @Test
    fun disablingNotificationsSchedulesAnEmptyPlanRatherThanLeavingStaleAlarms() = runTest {
        val scheduler = FakeScheduler()
        val r = repo("disabled")
        r.setNotificationSettings(r.notificationSettings.first().copy(enabled = false))
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = r,
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        assertTrue(plan.isEmpty())
        assertEquals(listOf(emptyList<ScheduledNotification>()), scheduler.scheduledCalls)
    }

    @Test
    fun needsTopUpDelegatesToTheRescheduleDeciderWithAThreeDayHorizon() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("topup"),
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        assertTrue(coordinator.needsTopUp(emptyList()))
        val fullWindow = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(!coordinator.needsTopUp(fullWindow))
    }
}
