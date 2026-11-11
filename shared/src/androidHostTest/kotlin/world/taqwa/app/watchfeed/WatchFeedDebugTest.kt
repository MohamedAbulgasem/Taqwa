package world.taqwa.app.watchfeed

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.watch.WatchFeedJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Plan 2 Task 7's debug seam, [WatchFeedDebug], over fakes: the rules its device harness relies on,
 * apart from Play services and the app's container, which the harness's own run on the phone
 * emulator proves (the pass report). Each test names what it holds; the bounds are the sync's own
 * [WatchFeedSync.CALLER_BOUND], on virtual time over fakes that suspend on a deferred alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchFeedDebugTest {

    // ── nodes ──

    /** Without a usable Play services nothing else is asked: no capability query, no node list. */
    @Test
    fun withoutPlayServicesNodesSaysNoWearApiAndAsksNothingElse() = runTest {
        var capabilityAsked = 0
        var connectedAsked = 0
        val nodes = PlayWatchNodes(
            availability = { ConnectionResult.SERVICE_MISSING },
            capability = { capabilityAsked++; FakeTask.succeeded(capabilityOf(emptyList())) },
            message = { _, _, _ -> error("nothing is sent") },
        )
        val printed = WatchFeedDebug.describeNodes(nodes) { connectedAsked++; emptyList() }
        assertEquals("no Wear API", printed)
        assertEquals(0, capabilityAsked, "no Wearable client is made")
        assertEquals(0, connectedAsked, "no node list is asked for")
    }

    /** A phone with Play services but no Wear OS app: the capability query's 17 is no Wear API. */
    @Test
    fun withoutTheWearOsAppNodesSaysNoWearApi() = runTest {
        var connectedAsked = 0
        val nodes = PlayWatchNodes(
            availability = { ConnectionResult.SUCCESS },
            capability = { FakeTask.failed(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))) },
            message = { _, _, _ -> error("nothing is sent") },
        )
        val printed = WatchFeedDebug.describeNodes(nodes) { connectedAsked++; emptyList() }
        assertEquals("no Wear API", printed)
        assertEquals(0, connectedAsked)
    }

    /**
     * Every connected node, with its id, its name and whether it is nearby, and which of them have
     * Taqwa's watch app; a node with the capability that is not connected (reached another way) is
     * listed too, from what the capability says of it.
     */
    @Test
    fun nodesListsEveryConnectedNodeAndWhichHaveTaqwa() = runTest {
        val nodes = FakeWatchNodes(steady = listOf(WatchNode("w1", nearby = true), WatchNode("w9", nearby = false)))
        val printed = WatchFeedDebug.describeNodes(nodes) {
            listOf(DebugNode("w1", "Taqwa_Wear_OS_6", nearby = true), DebugNode("p2", "Other watch", nearby = false))
        }
        assertEquals(
            listOf(
                "node w1 \"Taqwa_Wear_OS_6\" nearby=true taqwa_watch=true",
                "node p2 \"Other watch\" nearby=false taqwa_watch=false",
                "node w9 \"\" nearby=false taqwa_watch=true",
            ),
            printed.lines(),
        )
    }

    @Test
    fun noNodesSaysSo() = runTest {
        assertEquals("no nodes", WatchFeedDebug.describeNodes(FakeWatchNodes()) { emptyList() })
    }

    // ── sync ──

    @Test
    fun syncAnswersTheResultsName() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.link.reachable = false
        assertEquals("NO_WATCH", WatchFeedDebug.syncOn(h.sync, "RESCHEDULED"))
        h.link.reachable = true
        assertEquals("SENT", WatchFeedDebug.syncOn(h.sync, "WATCH_CHANGED"))
        assertEquals(listOf(WatchSyncReason.RESCHEDULED, WatchSyncReason.WATCH_CHANGED), h.reports.map { it.reason })
    }

    /** A sync that has not answered within the callers' bound is "TIMEOUT", at that bound and no later. */
    @Test
    fun aSyncThatOutlivesTheBoundIsTimeout() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true).apply { reachableGate = CompletableDeferred() }
        val sync = WatchFeedSync(
            feedFor = { syncFeed() },
            link = link,
            memory = FakeWatchFeedMemory(),
            scope = backgroundScope,
            work = StandardTestDispatcher(testScheduler),
            budget = 60.seconds,
            callerBound = 60.seconds,
        )
        assertEquals("TIMEOUT", WatchFeedDebug.syncOn(sync, "WATCH_APPEARED"))
        assertEquals(WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, currentTime)
    }

    /** A reason the sync does not know is named back, and nothing is asked. */
    @Test
    fun anUnknownReasonIsNamedAndNothingIsAsked() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        assertEquals("unknown reason SOMETIME", WatchFeedDebug.syncOn(h.sync, "SOMETIME"))
        assertEquals(0, h.link.reachableAsked)
        assertTrue(h.reports.isEmpty())
    }

    // ── settle ──

    @Test
    fun settleIsTrueOnceTheAsksBeforeItAreServed() = runTest {
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        h.sync.request(WatchSyncReason.RESCHEDULED)
        assertTrue(WatchFeedDebug.settleOn(h.sync))
        assertEquals(1, h.link.sent.size, "the ask before it was served first")
    }

    @Test
    fun settleIsFalseWhenTheBoundPasses() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true).apply { reachableGate = CompletableDeferred() }
        val sync = WatchFeedSync(
            feedFor = { syncFeed() },
            link = link,
            memory = FakeWatchFeedMemory(),
            scope = backgroundScope,
            work = StandardTestDispatcher(testScheduler),
            budget = 60.seconds,
            callerBound = 60.seconds,
        )
        sync.request(WatchSyncReason.RESCHEDULED)
        assertFalse(WatchFeedDebug.settleOn(sync))
        assertEquals(WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, currentTime)
    }

    // ── send --ei version n ──

    /**
     * The feed as the next ask would build it, with its version raised, sent through the link with
     * that feed's own fingerprint under the next number, which is on record before the link is
     * called (Ruling 50 item 1), so the watch takes it as new and finds it newer than it reads.
     */
    @Test
    fun aRaisedVersionGoesUnderTheNextNumberWrittenFirst() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true)
        val memory = FakeWatchFeedMemory(WatchFeedRecord(TEST_ORIGIN, lastSeq = 4))
        val h = SyncHarness(this, link, memory)
        var onRecordAtTheSend: Long? = null
        link.onSend = { onRecordAtTheSend = memory.record?.takeIf { r -> r.lastDelivery == it.delivery }?.lastSeq }
        val raised = syncFeed().copy(version = 2)

        assertEquals("SENT", WatchFeedDebug.sendWithVersion(2, h.sync, feedFor = { syncFeed() }, link, memory))

        val sent = link.sent.single()
        assertEquals(2, WatchFeedJson.versionOf(sent.json))
        assertEquals(WatchFeedJson.encode(raised), sent.json)
        assertEquals(TEST_ORIGIN, sent.delivery.origin)
        assertEquals(5L, sent.delivery.seq)
        assertEquals(watchFeedFingerprint(raised), sent.delivery.fingerprint)
        assertEquals(5L, onRecordAtTheSend, "the number is on record before the link is called")
        assertEquals(watchFeedFingerprint(raised), memory.record?.lastStandsFor)
        assertEquals(null, memory.record?.confirmedSeq, "nothing is confirmed by the send itself")
    }

    /** The asks made before it are served first, so its number follows theirs. */
    @Test
    fun aRaisedVersionWaitsForTheAsksBeforeIt() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true)
        val memory = FakeWatchFeedMemory()
        val h = SyncHarness(this, link, memory)
        h.sync.request(WatchSyncReason.RESCHEDULED)
        assertEquals("SENT", WatchFeedDebug.sendWithVersion(2, h.sync, feedFor = { syncFeed() }, link, memory))
        assertEquals(listOf(1L, 2L), link.sent.map { it.seq })
        assertEquals(listOf(1, 2), link.sent.map { WatchFeedJson.versionOf(it.json) })
    }

    @Test
    fun aRaisedVersionWithNoFeedSendsNothing() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true)
        val memory = FakeWatchFeedMemory()
        val h = SyncHarness(this, link, memory)
        assertEquals("NO_FEED", WatchFeedDebug.sendWithVersion(2, h.sync, feedFor = { null }, link, memory))
        assertTrue(link.sent.isEmpty())
        assertTrue(memory.calls.none { it.startsWith("write") }, "no number is used")
    }

    /** A send that never answers is cut at the callers' bound, its number kept on record. */
    @Test
    fun aRaisedVersionThatNeverAnswersIsTimeout() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true).apply { gate = CompletableDeferred() }
        val memory = FakeWatchFeedMemory()
        val h = SyncHarness(this, link, memory)
        assertEquals("TIMEOUT", WatchFeedDebug.sendWithVersion(2, h.sync, feedFor = { syncFeed() }, link, memory))
        assertEquals(1L, memory.record?.lastSeq, "the number is never used again")
        assertEquals(WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, currentTime)
    }

    /** A link that throws is a failure named, never a throw out of the seam. */
    @Test
    fun aRaisedVersionWhoseLinkThrowsIsFailed() = runTest {
        val link = FakeWatchLink(confirmsDelivery = true).apply { sendThrows = IllegalStateException("broken") }
        val h = SyncHarness(this, link)
        assertEquals("FAILED IllegalStateException", WatchFeedDebug.sendWithVersion(2, h.sync, feedFor = { syncFeed() }, link, h.memory))
    }

    // ── phrase ──

    /**
     * One of the user's own phrases added as the Tasbeeh sheet adds it, then the ask the screen's
     * hook makes (`PHRASES_CHANGED`), which the harness has to make itself: the hook lives on the
     * screen's view model. The phrase's own type stays inside `:shared` (plan 2's visibility rule).
     */
    @Test
    fun aPhraseIsAddedThenTheTasbeehScreensAskIsMade() = runTest {
        val store = TasbeehStore(MemoryDataStore()) { 1_700L }
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        assertEquals(
            "added custom_1700; PHRASES_CHANGED SENT",
            WatchFeedDebug.phraseOn(store, h.sync, "سبحان الله وبحمده", 100),
        )
        val added = store.customPresets.first().single()
        assertEquals("custom_1700", added.id)
        assertEquals(100, added.total)
        assertEquals(listOf(WatchSyncReason.PHRASES_CHANGED), h.reports.map { it.reason })
    }

    /** A phrase the store refuses (blank once cleaned) is the store's own error, and nothing is asked. */
    @Test
    fun aBlankPhraseIsRefusedAndNothingIsAsked() = runTest {
        val store = TasbeehStore(MemoryDataStore()) { 1_700L }
        val h = SyncHarness(this, FakeWatchLink(confirmsDelivery = true))
        assertFailsWith<IllegalArgumentException> { WatchFeedDebug.phraseOn(store, h.sync, "   ", 33) }
        assertTrue(h.reports.isEmpty())
        assertTrue(store.customPresets.first().isEmpty())
    }

    // ── forms ──

    /** The phone's forms are [PhoneForms]' own, the very text the face's tools read. */
    @Test
    fun theFormsAreTheFaceToolsOwn() {
        val forms = WatchFeedDebug.phoneFormsJson()
        assertEquals(PhoneForms.json(), forms)
        assertTrue(forms.startsWith("{\n    \"locales\": {\n"))
    }
}

/** Preferences held in memory, so the phrase tests stay on virtual time with no file behind them. */
private class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
