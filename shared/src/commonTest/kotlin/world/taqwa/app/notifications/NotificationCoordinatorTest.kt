package world.taqwa.app.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watchfeed.FakeWatchFeedMemory
import world.taqwa.app.watchfeed.FakeWatchLink
import world.taqwa.app.watchfeed.WatchFeedBuilder
import world.taqwa.app.watchfeed.WatchFeedInputs
import world.taqwa.app.watchfeed.WatchFeedSync
import world.taqwa.app.watchfeed.WatchSendResult
import world.taqwa.app.watchfeed.WatchSyncReason
import world.taqwa.app.watchfeed.syncFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class FakeScheduler : NotificationScheduler {
    val scheduledCalls = mutableListOf<List<ScheduledNotification>>()
    var cancelCalls = 0
    override fun scheduleAll(plan: List<ScheduledNotification>) { scheduledCalls += plan }
    override fun cancelAll() { cancelCalls++ }
    val cancelledIds = mutableListOf<Set<String>>()
    override fun cancel(ids: Set<String>) { cancelledIds += ids }
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

    @Test
    fun aSetAlreadySaidIsNotArmedAgainByAReschedule() = runTest {
        // The morning set of the 6th was finished at Fajr; the app is then opened again, and the
        // foreground reschedule must not bring its reminder back.
        val scheduler = FakeScheduler()
        val r = repo("adhkar-done")
        r.setNotificationSettings(r.notificationSettings.first().copy(enabled = true, adhkar = true))
        val sixth = LocalDate(2026, 9, 6)
        val done = adhkarNotificationId(AdhkarSet.MORNING, sixth)
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = r,
            locationOf = { london }, scheduler = scheduler, now = { now },
            doneAdhkar = { setOf(done) },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertTrue(plan.none { it.id == done })
        assertTrue(plan.any { it.id == adhkarNotificationId(AdhkarSet.EVENING, sixth) })
        assertTrue(plan.any { it.id == adhkarNotificationId(AdhkarSet.MORNING, LocalDate(2026, 9, 7)) })
    }

    // The watch (plan 2 Task 4): asked after the plan is armed, inside the lock, and only asked, so
    // the reminders never wait on it. These hold a real store and a coordinator planning on
    // Dispatchers.Default, so they assert order and outcome, never a virtual instant; the one sync
    // among them runs on a second scheduler that the test advances by hand once the real work has
    // returned.

    @Test
    fun theWatchIsAskedAfterThePlanIsArmed() = runTest {
        val events = mutableListOf<String>()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("watch-after"),
            locationOf = { london }, scheduler = EventScheduler(events), now = { now },
            afterPlan = { events += "watch ${it.cityName}" },
        )
        coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertEquals(listOf("armed", "watch London"), events)
    }

    /**
     * The watch is asked inside the lock, right after the plan is armed (mutant MD, Ruling 48), so
     * the asks are made in the order the plans were armed. A reschedule started while the first is
     * still telling the watch runs at once as far as it can: it waits for the lock, and is located
     * only once the first has told the watch. Outside the lock it would be located before.
     */
    @Test
    fun theWatchIsAskedBeforeTheNextRescheduleTakesTheLock() = runTest {
        val events = mutableListOf<String>()
        lateinit var coordinator: NotificationCoordinator
        var second: Job? = null
        coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("watch-in-lock"),
            locationOf = { london }, scheduler = EventScheduler(events), now = { now },
            locationFor = { events += "located"; london },
            afterPlan = {
                events += "watch asked"
                if (second == null) {
                    second = launch(start = CoroutineStart.UNDISPATCHED) {
                        coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
                    }
                }
                events += "watch told"
            },
        )
        coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        checkNotNull(second).join()
        assertEquals(
            listOf("located", "armed", "watch asked", "watch told", "located", "armed", "watch asked", "watch told"),
            events,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theLockIsFreeWhileTheWatchIsSlow() = runTest {
        val watchTime = TestCoroutineScheduler()
        val reports = mutableListOf<Pair<WatchSendResult, Long>>()
        val link = FakeWatchLink(confirmsDelivery = true).apply { gate = CompletableDeferred() }
        val watchScope = CoroutineScope(StandardTestDispatcher(watchTime) + Job())
        val sync = WatchFeedSync(
            feedFor = { delay(60.seconds); syncFeed() },
            link = link,
            memory = FakeWatchFeedMemory(),
            scope = watchScope,
            work = StandardTestDispatcher(watchTime),
            report = { _, result, _ -> reports += result to watchTime.currentTime },
        )
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("watch-slow"),
            locationOf = { london }, scheduler = scheduler, now = { now },
            afterPlan = { sync.request(WatchSyncReason.RESCHEDULED, it) },
        )
        coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        assertEquals(2, scheduler.scheduledCalls.size, "both plans armed with the watch still slow")
        assertEquals(emptyList(), reports)

        watchTime.advanceTimeBy(10_000)
        watchTime.runCurrent()
        assertEquals(listOf(WatchSendResult.FAILED to 5_000L, WatchSendResult.FAILED to 10_000L), reports)
        watchScope.cancel()
    }

    @Test
    fun aWatchRequestThatThrowsNeverCostsTheReminders() = runTest {
        val scheduler = FakeScheduler()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("watch-throws"),
            locationOf = { london }, scheduler = scheduler, now = { now },
            afterPlan = { throw IllegalStateException("the watch side broke") },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertEquals(60, plan.size)
        assertEquals(listOf(plan), scheduler.scheduledCalls)
    }

    /**
     * Show sunrise turned on or off in Settings › Prayer times (the owner's decision of 9 November
     * 2026): the route writes it and refreshes for the new times (`refreshForNewTimes`), whose
     * reschedule asks the watch, and the feed built from the stored settings then says so, so it
     * goes to the watch again. On both links: Android's, where only the watch's echo confirms a
     * feed, and the iPhone's, where the system delivers what it was handed. Once the watch holds
     * it, the same setting sends nothing more. The sync works on the default dispatcher and is
     * waited for with `settle`, as a background entry point waits for it, since the feed is
     * built from the real store.
     */
    @Test
    fun turningShowSunriseOnOrOffSendsTheWatchTheFeedAgain() = runTest {
        for (confirmsDelivery in listOf(true, false)) {
            val r = repo("watch-sunrise-$confirmsDelivery")
            r.setShowSunrise(false)
            val link = FakeWatchLink(confirmsDelivery)
            val watchScope = CoroutineScope(Dispatchers.Default + Job())
            val sync = WatchFeedSync(
                feedFor = { planned -> watchFeedAt(planned ?: london, r.prayerSettings.first()) },
                link = link,
                memory = FakeWatchFeedMemory(),
                scope = watchScope,
            )
            val coordinator = NotificationCoordinator(
                engine = engine, settingsRepository = r,
                locationOf = { london }, scheduler = FakeScheduler(), now = { now },
                afterPlan = { sync.request(WatchSyncReason.RESCHEDULED, it) },
            )
            suspend fun written(change: suspend () -> Unit): List<Boolean> {
                change()
                coordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
                withContext(Dispatchers.Default) {
                    assertTrue(sync.settle(), "the ask is served")
                    // Android's watch acknowledges what it stored.
                    if (confirmsDelivery) link.sent.lastOrNull()?.let { sync.acknowledge(it.echo) }
                }
                return link.sent.map { it.feed.ui.showSunrise }
            }
            val which = if (confirmsDelivery) "Android" else "iPhone"
            assertEquals(listOf(false), written {}, which)
            assertEquals(listOf(false, true), written { r.setShowSunrise(true) }, "$which: turned on")
            assertEquals(listOf(false, true), written {}, "$which: unchanged")
            assertEquals(listOf(false, true, false), written { r.setShowSunrise(false) }, "$which: turned off")
            watchScope.cancel()
        }
    }

    /** The feed the phone sends from [location] with [settings], in English, as `AppContainer.watchFeedFor` builds it. */
    private fun watchFeedAt(location: GeoLocation, settings: PrayerSettings): WatchFeed? = WatchFeedBuilder.build(
        WatchFeedInputs(
            location = location,
            city = location.cityName.orEmpty(),
            language = UiLanguage.ENGLISH,
            spoken = null,
            screens = WatchScreenWords.ENGLISH,
            prayerSettings = settings,
            format = EnglishPlatformFormat,
            clockFormat = ClockFormat.TWENTY_FOUR_HOUR,
            phrases = emptyList(),
            now = now,
            timesFor = { date -> engine.timesFor(location, date, settings) },
        ),
    )

    @Test
    fun withNoLocationTheWatchIsNotAsked() = runTest {
        val asked = mutableListOf<GeoLocation>()
        val coordinator = NotificationCoordinator(
            engine = engine, settingsRepository = repo("watch-no-loc"),
            locationOf = { null }, scheduler = FakeScheduler(), now = { now },
            afterPlan = { asked += it },
        )
        coordinator.reschedule(RescheduleTrigger.APP_FOREGROUND)
        assertEquals(emptyList(), asked)
    }
}

/** Records "armed" when a plan is handed over, into a list the test shares with other steps. */
private class EventScheduler(private val events: MutableList<String>) : NotificationScheduler {
    override fun scheduleAll(plan: List<ScheduledNotification>) { events += "armed" }
    override fun cancelAll() {}
    override fun cancel(ids: Set<String>) {}
}
