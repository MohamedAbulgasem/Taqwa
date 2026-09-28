package world.taqwa.app.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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

    // DataStore's OkioStorage requires an absolute path — see SettingsRepositoryTest for why
    // "/tmp" (rather than the brief's relative "build/...") is used here too.
    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-coordinator-$name.preferences_pb".toPath() }
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
    fun twoReschedulesAtOnceRunOneAfterTheOther() = runTest {
        // Two quick stepper taps: the second must not plan while the first is still arming, or
        // the first could arm last and leave a stale plan behind.
        val scheduler = FakeScheduler()
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var calls = 0
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("serial"),
            locationOf = { london }, scheduler = scheduler, now = { now },
            locationFor = {
                val call = ++calls
                events += "start $call"
                if (call == 1) gate.await()
                london
            },
        )
        val first = launch { coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED); events += "armed 1" }
        runCurrent()
        val second = launch { coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED); events += "armed 2" }
        runCurrent()
        assertEquals(listOf("start 1"), events, "the second began while the first was running")
        gate.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("start 1", "armed 1", "start 2", "armed 2"), events)
        assertEquals(2, scheduler.scheduledCalls.size)
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

    @Test
    fun needsTopUpAnswersTheSameFromAHorizonAloneAsFromThePlan() = runTest {
        // Android's alarm receiver has no plan in memory — only the furthest instant the armed
        // plan reaches, read back from the scheduler's preferences. Both must decide alike.
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("topup-horizon"),
            locationOf = { london }, scheduler = scheduler, now = { now },
        )
        assertTrue(coordinator.needsTopUp(null as Instant?))
        val fullWindow = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(!coordinator.needsTopUp(fullWindow.maxOf { it.instant }))
        assertTrue(coordinator.needsTopUp(now + 1.days))
    }
}
