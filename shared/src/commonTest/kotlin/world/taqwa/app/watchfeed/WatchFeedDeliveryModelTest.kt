package world.taqwa.app.watchfeed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watchfeed.WatchSendResult.UNCHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.PHRASES_CHANGED
import world.taqwa.app.watchfeed.WatchSyncReason.RESCHEDULED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_APPEARED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_ASKED
import world.taqwa.app.watchfeed.WatchSyncReason.WATCH_CHANGED
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ruling 50 item 7: a seeded randomized model of the phone's sync (the real [WatchFeedSync] over a
 * record in memory), both links and the watches (each storing by the contract's rule, as
 * [TestWatch] does), as the platforms allow them:
 * - **Android:** every message in flight may be lost, duplicated or delivered in any order, both
 *   ways, each echo naming the node it came from, and taken in for that node even once the watch is
 *   away (Ruling 52 item 9); a send may be refused, cut off by its budget before or after it was
 *   queued, or throw before or after; Play services may drop what it holds; a second watch may be
 *   nearby at once, or switched to (the worn one going away as the other comes nearby, its reconnect
 *   asked before its handshake arrives: Ruling 52 item 9); a new install (a new origin, nothing
 *   restored) may leave the old one's feeds in flight (stragglers); a lone watch may go away and
 *   come back (Ruling 52 item 7), so that an ask, its handshake's among them, can find no watch.
 * - **iPhone:** the latest handed context per watch, delivered only to the worn watch and re-read
 *   at activation; handshakes in order (the bridge queues each one's ask in WatchConnectivity's
 *   order, Ruling 53 item 4a), lost or duplicated, reaching the phone only from the session's
 *   watch, waking the app; `updateApplicationContext` that throws hands nothing over; a
 *   reinstalled or erased watch app may lose the context and get a new watch directory (a new key);
 *   a second watch, switched to automatically, with `sessionWatchStateDidChange` told only to a
 *   running app and sometimes lost to a process death.
 * - **Both:** the phone's process may die between any two asks (its queue goes, its record stays);
 *   the watch app may be wiped, with or without its start's handshake; feeds over the link's limit;
 *   no feed at times (no location yet, or a day the engine cannot give: Ruling 52 item 7), so that
 *   an ask, its handshake's among them, ends without a send.
 *
 * The lone watch that goes away and the absent feed are what let an ask take in a wiped watch's
 * echo of nothing and end before it takes a number (`NO_WATCH`, `NO_FEED`), with the watch's
 * acknowledgement from before the wipe still on its way: D1 of Ruling 50's review, which the
 * configuration "a lone watch that goes away" seeks out (`resetBehindItsAck`). With amendment A2
 * undone (a forget that sets no number aside, `forgotten()` as it was before Ruling 50's fix),
 * (S) fails on this seed (Ruling 52 item 7).
 *
 * Asserted for every schedule of the documented platform:
 * - **(S)** an `UNCHANGED` to watch W's handshake only while W holds the feed; any other
 *   `UNCHANGED` only while the watch whose echo the phone took in last holds it and is nearby
 *   (Android, Ruling 52 item 9), or the session's watch holds it (the iPhone). Either way the watch
 *   may instead have been wiped after its last echo (item 7), unseen: after the echo the phone took
 *   in from it last, and after every echo of nothing of this install's (nothing, or another
 *   install's) the phone has taken in from it. So once the phone has heard a wiped watch say it
 *   holds nothing, no echo sent before that excuses an `UNCHANGED`, whatever order they arrive in
 *   (D1, Ruling 50's review). "Holds" counts the
 *   times alone of a feed over the limit, and, on the iPhone, a feed that is the latest context
 *   handed over for that watch with a number above what it holds (amendment to item 7). The known
 *   limit rides along: the watch stored another install's feed after that echo (a straggler),
 *   unseen in the same way, healed by its acknowledgement or next handshake.
 * - **(L)** once the links are quiet, each watch's own handshake leaves it on the current feed (on
 *   the iPhone, the watch worn).
 * - **(D)** spec 1.2's damping: once every watch is on the current feed, a reschedule is
 *   `UNCHANGED`.
 *
 * Outside the documented platform (a record restored from an older snapshot despite item 1, a
 * context the system loses), only (L) and (D) are asserted: their transient `UNCHANGED`s are what
 * no phone-side rule can see, and the watch's next echo heals them.
 *
 * The world moves only between asks: the build never suspends, so no watch comes or goes while an
 * ask is served, and the model's link reads the watches at each call. So Android's known limit of
 * one reading per ask (Ruling 54 item 4: a watch switched to while an ask builds its feed is judged
 * as the watch read before the build) lies outside this model.
 * `AndroidWatchLinkTest.aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk` holds it.
 */
class WatchFeedDeliveryModelTest {

    private enum class Feature { WIPE, SECOND, SWITCH, AWAY, NO_FEED, STRAGGLERS, CLONE, CONTEXT_LOSS }

    private class Config(val name: String, val confirms: Boolean, val features: Set<Feature>, val runs: Int) {
        val outOfPremise: Boolean get() = Feature.CLONE in features || Feature.CONTEXT_LOSS in features
    }

    private val configs = listOf(
        Config("android, one watch, wipes", confirms = true, setOf(Feature.WIPE), RUNS),
        Config("android, two watches, wipes", confirms = true, setOf(Feature.WIPE, Feature.SECOND), RUNS),
        Config("android, stragglers of an old install", confirms = true, setOf(Feature.STRAGGLERS, Feature.SECOND), RUNS),
        Config("android, a lone watch that goes away, wipes, no feed at times", confirms = true, setOf(Feature.WIPE, Feature.AWAY, Feature.NO_FEED), RUNS * 3),
        Config("iphone, one watch, reinstalls", confirms = false, setOf(Feature.WIPE), RUNS),
        Config("iphone, one watch, reinstalls, no feed at times", confirms = false, setOf(Feature.WIPE, Feature.NO_FEED), RUNS),
        Config("iphone, two watches switched", confirms = false, setOf(Feature.WIPE, Feature.SECOND), RUNS),
        Config("android, a restored record", confirms = true, setOf(Feature.CLONE, Feature.WIPE), RUNS / 2),
        Config("iphone, a restored record, a lost context", confirms = false, setOf(Feature.CLONE, Feature.CONTEXT_LOSS), RUNS / 2),
        // Last, so every configuration before it keeps its seeds (Ruling 52 item 9, plan 2 Task 5).
        Config("android, two watches switched, wipes", confirms = true, setOf(Feature.WIPE, Feature.SECOND, Feature.SWITCH), RUNS),
    )

    @Test
    fun everyScheduleEndsOnTheCurrentFeedWithNoWrongUnchanged() = runTest {
        val failures = mutableListOf<String>()
        val names = FeedNames()
        for ((index, config) in configs.withIndex()) {
            for (run in 0 until config.runs) {
                val seed = SEED + index * 1_000_000L + run
                val problem = Schedule(this, config, Random(seed), names).run()
                if (problem != null) failures += "${config.name}, seed $seed: $problem"
            }
        }
        assertEquals(emptyList(), failures.take(10), "${failures.size} schedules failed")
    }

    /** One schedule: a phone, its link and one or two watches. Answers its first problem, or null. */
    private class Schedule(
        private val test: TestScope,
        private val config: Config,
        private val rng: Random,
        private val names: FeedNames,
    ) {
        private val confirms = config.confirms
        private val watches = if (Feature.SECOND in config.features) 2 else 1
        private val link = WorldLink(confirms, limitBytes = WatchFeedJson.encode(X).encodeToByteArray().size)
        private val feeds = listOf(X, Y, Z, W1, W2)
        /** The phone's feed now; null while it has none (no location, or no day the engine gives). */
        private var current: WatchFeed? = feeds[rng.nextInt(feeds.size)]
        private var previous = current
        private var origins = 0
        private var memory = FakeWatchFeedMemory(origin = nextOrigin())
        private val snapshots = mutableListOf<WatchFeedRecord>()
        private var phoneScope: CoroutineScope? = null
        private var sync: WatchFeedSync? = null
        private val queue = ArrayDeque<Ask>()
        private var running = true
        /** The world's clock: every step that the checks compare moves it on, so no two are at one instant. */
        private var time = 0L

        private fun tick(): Long = ++time
        private var quiet = false
        private var problem: String? = null

        private val watch = List(watches) { TestWatch(names) }
        private val wipedAt = LongArray(watches) { -1 }
        private val foreignAt = LongArray(watches) { -1 }

        /** When watch w sent the echo this install's phone took in from it last. */
        private val lastHeardAt = LongArray(watches) { -1 }

        /** When watch w sent the latest echo of nothing of this install's the phone took in from it. */
        private val emptyHeardAt = LongArray(watches) { -1 }

        // Android
        private val nearby = BooleanArray(watches) { it == 0 || rng.nextBoolean() }
        private val down = List(watches) { mutableListOf<FakeWatchLink.Sent>() }
        private val up = mutableListOf<Echo>()
        private var lastEcho: Echo? = null

        // iPhone
        private var worn = 0
        private var session = 0
        private var sessionActive = true
        private var keys = 0
        private val key = Array(watches) { nextKey() }
        private val pending = arrayOfNulls<FakeWatchLink.Sent>(watches)
        private val received = arrayOfNulls<FakeWatchLink.Sent>(watches)
        private val handshakes = List(watches) { ArrayDeque<Echo>() }

        /** An echo on its way: from watch [w], what it held when it sent it, at [at]. */
        private class Echo(val w: Int, val text: String, val at: Long, val handshake: Boolean)

        /** An ask on the phone's queue; [from] is the watch key the bridge read when [echo] arrived. */
        private class Ask(val reason: WatchSyncReason, val echo: Echo? = null, val from: String? = null)

        init {
            link.reachable = { if (confirms) nearby.any { it } else sessionActive }
            link.key = { if (confirms) null else if (sessionActive) "k${key[session]}" else null }
            // Android's watch keys are the nearby watches' node ids (Ruling 52 item 9).
            if (confirms) link.keys = { watch.indices.filter { nearby[it] }.map(::node).toSet() }
            link.outcome = ::outcome
            link.onHanded = ::handOver
            launch()
        }

        private fun nextOrigin(): String = (0x1000000000000000L + origins++).toString(16)

        private fun nextKey(): Int = keys++

        /** Watch [w]'s Wear node id, which every message from it names (Ruling 52 item 9). */
        private fun node(w: Int): String = "n$w"

        suspend fun run(): String? {
            repeat(rng.nextInt(10, 61)) {
                tick()
                event()
                if (problem != null) return problem
            }
            quiet = true
            for (w in watch.indices) nearby[w] = true
            wake()
            if (current == null) { // a location again: the reschedule that follows it
                current = previous ?: X
                queue += Ask(RESCHEDULED)
            }
            val current = checkNotNull(current)
            if (!confirms) settleSession()
            drain()
            val targets = if (confirms) watch.indices.toList() else listOf(worn)
            for (w in targets) {
                emitHandshake(w)
                drain()
            }
            problem?.let { return it }
            val off = targets.filterNot { watch[it].holds(current) }
            if (off.isNotEmpty()) return "(L) watch $off ends on ${watch[off[0]].held?.delivery}, not ${fp(current)}"
            val damped = sync().sync(RESCHEDULED)
            if (damped != UNCHANGED) return "(D) a reschedule with every watch current answered $damped"
            phoneScope?.cancel()
            return problem
        }

        // ── the phone ──

        private fun sync(): WatchFeedSync = checkNotNull(sync)

        private fun launch() {
            phoneScope?.cancel()
            val scope = CoroutineScope(test.backgroundScope.coroutineContext + Job(test.backgroundScope.coroutineContext[Job]))
            phoneScope = scope
            queue.clear()
            sync = WatchFeedSync(
                feedFor = { current },
                link = link,
                memory = memory,
                scope = scope,
                work = StandardTestDispatcher(test.testScheduler),
            )
            if (!confirms) {
                session = worn
                sessionActive = true
                queue += Ask(WATCH_APPEARED) // activation, at every launch
            }
        }

        private fun kill() {
            running = false
            phoneScope?.cancel()
            queue.clear()
        }

        private fun wake() {
            if (!running) {
                running = true
                launch()
            }
        }

        private suspend fun serveOne() {
            val ask = queue.removeFirstOrNull() ?: return
            val echo = ask.echo
            // On Android the sync takes every echo in, for the node it came from (Ruling 52 item 9);
            // on the iPhone only the session's watch's (amendment A3), so one that arrives while the
            // session is between watches is ignored.
            val takenIn = if (confirms) echo != null else sessionActive && ask.from == "k${key[session]}"
            if (ask.reason == WATCH_ASKED && echo != null && !echo.handshake) {
                sync().acknowledge(echo.text, from = node(echo.w))
                if (takenIn) heard(echo)
                return
            }
            val from = if (confirms) echo?.let { node(it.w) } else ask.from
            val result = sync().sync(ask.reason, have = echo?.text, from = from)
            if (echo != null && takenIn) heard(echo)
            if (result == UNCHANGED) check(ask, takenIn)
            if (rng.nextInt(if (Feature.CLONE in config.features) 3 else 20) == 0) memory.record?.let { snapshots += it }
        }

        private fun heard(echo: Echo) {
            lastEcho = echo
            lastHeardAt[echo.w] = echo.at
            if (WatchLinkContract.deliveryOf(echo.text)?.origin != memory.record?.origin) {
                emptyHeardAt[echo.w] = maxOf(emptyHeardAt[echo.w], echo.at)
            }
        }

        /** Whether what befell watch [w] at [at] is unseen by the phone (property S). */
        private fun unseen(w: Int, at: Long): Boolean = at > lastHeardAt[w] && at > emptyHeardAt[w]

        /** Property (S), as the class's KDoc states it. */
        private fun check(ask: Ask, takenIn: Boolean) {
            if (config.outOfPremise || problem != null) return
            val echo = ask.echo
            val w: Int
            if (confirms) {
                val reference = echo?.takeIf { takenIn } ?: lastEcho ?: run {
                    problem = "(S) UNCHANGED for ${ask.reason} before any echo"
                    return
                }
                w = reference.w
                // Ruling 52 item 9: a confirmation counts only while its watch is nearby.
                if (!nearby[w]) {
                    problem = "(S) UNCHANGED for ${ask.reason} at $time while watch $w, the one heard from last, is away"
                    return
                }
                if (unseen(w, wipedAt[w]) || unseen(w, foreignAt[w])) return
            } else {
                w = echo?.takeIf { takenIn }?.w ?: session
                if (unseen(w, wipedAt[w])) return
                val handed = pending[w]
                if (handed != null && holdsFeed(handed) && WatchLinkContract.stores(watch[w].held?.delivery, handed.delivery)) return
            }
            val current = current ?: run {
                problem = "(S) UNCHANGED for ${ask.reason} at $time with no feed"
                return
            }
            if (!watch[w].holds(current)) {
                problem = "(S) UNCHANGED for ${ask.reason} at $time while watch $w holds ${watch[w].held?.delivery}, the feed is ${fp(current)}"
            }
        }

        private fun holdsFeed(sent: FakeWatchLink.Sent): Boolean = current?.let { sent.fingerprint in names.heldAs(it) } == true

        // ── the link ──

        private fun outcome(): Outcome {
            if (quiet) return Outcome.SENT
            if (rng.nextInt(10) == 0) linkEvent() // the world moves while the send is in flight
            if (!confirms && !sessionActive) return Outcome.REFUSED // Task 8's re-check: nothing handed over
            val r = rng.nextInt(100)
            return if (confirms) {
                when {
                    r < 62 -> Outcome.SENT
                    r < 68 -> Outcome.REFUSED
                    r < 74 -> Outcome.NO_WATCH
                    r < 79 -> Outcome.CUT_OFF
                    r < 84 -> Outcome.CUT_OFF_BEFORE
                    r < 92 -> Outcome.THROWN_AFTER
                    else -> Outcome.THROWN
                }
            } else {
                when {
                    r < 80 -> Outcome.SENT
                    r < 90 -> Outcome.THROWN
                    else -> Outcome.CUT_OFF
                }
            }
        }

        private fun handOver(sent: FakeWatchLink.Sent) {
            if (confirms) {
                for (w in watch.indices) if (nearby[w] && (quiet || rng.nextInt(100) >= 15)) down[w] += sent
            } else if (sessionActive) {
                pending[session] = sent
            }
        }

        /** A step of the world that touches no phone code: safe while a send is in flight. */
        private fun linkEvent() {
            if (confirms) {
                val busy = watch.indices.filter { down[it].isNotEmpty() }
                if (busy.isNotEmpty()) deliverDown(busy[rng.nextInt(busy.size)])
            } else {
                when (rng.nextInt(3)) {
                    0 -> deliverContext(worn)
                    1 -> if (watches > 1) switchWatch()
                    else -> emitHandshake(worn)
                }
            }
        }

        private fun take(w: Int, sent: FakeWatchLink.Sent) {
            val echo = watch[w].take(sent) ?: return
            if (sent.delivery.origin != memory.record?.origin) foreignAt[w] = tick()
            if (confirms) up += Echo(w, echo, tick(), handshake = false)
            if (!quiet && Feature.AWAY in config.features && rng.nextInt(4) != 0) resetBehindItsAck(w)
        }

        /**
         * D1 of Ruling 50's review, as the platform allows it: the watch reset while its
         * acknowledgement is still on its way, its start's handshake reaching the phone at once,
         * ahead of that acknowledgement (no order is documented), and then either the watch out of
         * reach or the phone without a feed. So the ask serving the handshake may take in its echo
         * of nothing and end before it takes a number (`NO_WATCH`, `NO_FEED`); the world decides
         * the rest. Without amendment A2 the late acknowledgement then confirms the latest number
         * again, and (S) fails.
         */
        private fun resetBehindItsAck(w: Int) {
            wipe(w)
            wake() // a message wakes the phone app
            queue += Ask(WATCH_ASKED, Echo(w, watch[w].echo, tick(), handshake = true))
            if (Feature.NO_FEED in config.features && rng.nextBoolean()) {
                previous = current
                current = null
            } else {
                nearby[w] = false
            }
        }

        private fun deliverDown(w: Int) {
            val channel = down[w]
            val at = rng.nextInt(channel.size)
            val sent = if (!quiet && rng.nextInt(20) == 0) channel[at] else channel.removeAt(at)
            take(w, sent)
        }

        private fun deliverContext(w: Int) {
            val sent = pending[w] ?: return
            if (w != worn) return
            pending[w] = null
            received[w] = sent
            take(w, sent)
        }

        private fun emitHandshake(w: Int) {
            val echo = Echo(w, watch[w].echo, tick(), handshake = true)
            if (confirms) {
                if (nearby[w]) up += echo
            } else if (w == worn) {
                handshakes[w] += echo
            }
        }

        private fun wipe(w: Int) {
            watch[w].wipe()
            wipedAt[w] = tick()
        }

        private fun switchWatch() {
            worn = (worn + 1) % watches
            if (running && sessionActive) sessionActive = false
        }

        /** The session follows the worn watch: activation's `WATCH_APPEARED`, and a change told or lost. */
        private fun settleSession() {
            if (sessionActive || !running) return
            session = worn
            sessionActive = true
            val told = quiet || rng.nextBoolean()
            if (told && rng.nextBoolean()) queue += Ask(WATCH_CHANGED)
            queue += Ask(WATCH_APPEARED)
            if (told && rng.nextBoolean()) queue += Ask(WATCH_CHANGED)
        }

        // ── the events ──

        private suspend fun event() {
            val r = rng.nextInt(100)
            when {
                r < 9 -> { // a setting, the stepper, a phrase, the location: often back to the feed before
                    wake()
                    val next = when {
                        Feature.NO_FEED in config.features && rng.nextInt(5) == 0 -> null
                        rng.nextInt(10) < 4 -> previous
                        else -> feeds[rng.nextInt(feeds.size)]
                    }
                    previous = current
                    current = next
                    queue += Ask(if (rng.nextBoolean()) RESCHEDULED else PHRASES_CHANGED)
                }
                r < 13 -> {
                    wake()
                    queue += Ask(if (rng.nextBoolean()) RESCHEDULED else WATCH_APPEARED)
                }
                r < 41 -> if (running) serveOne()
                r < 44 -> kill()
                r < 52 -> emitHandshake(if (confirms) rng.nextInt(watches) else worn)
                r < 57 -> if (Feature.WIPE in config.features) wipeEvent() else Unit
                r < 63 -> if (watches > 1 || Feature.AWAY in config.features) awayOrSwitch() else Unit
                r < 65 -> if (Feature.STRAGGLERS in config.features) newInstall() else Unit
                r < 70 -> if (Feature.CLONE in config.features) restoreOldRecord() else Unit
                else -> if (confirms) androidLink() else iphoneLink()
            }
        }

        private fun wipeEvent() {
            val w = rng.nextInt(watches)
            wipe(w)
            if (!confirms) {
                if (rng.nextBoolean()) received[w] = null
                if (rng.nextBoolean()) pending[w] = null
                if (rng.nextBoolean()) key[w] = nextKey() // reinstalled: iOS deleted its directory
                if (running && sessionActive && w == session && rng.nextBoolean()) queue += Ask(WATCH_CHANGED)
            }
            if (rng.nextInt(10) < 7) emitHandshake(w) // its start's handshake, or lost
        }

        private suspend fun awayOrSwitch() {
            if (confirms && Feature.SWITCH in config.features && rng.nextBoolean()) {
                // The user puts on the other watch, often once the links have gone quiet, so the
                // phone may hold a confirmation from the first: the one worn goes away and the
                // other comes nearby, its reconnect asked before its handshake reaches the phone.
                // It must not inherit the first watch's confirmation (Ruling 52 item 9).
                if (rng.nextBoolean()) drain()
                val on = if (nearby[0] != nearby[1]) (if (nearby[0]) 1 else 0) else rng.nextInt(watches)
                for (w in watch.indices) nearby[w] = w == on
                if (rng.nextInt(5) < 4) emitHandshake(on) // PHONE_NEARBY
                if (rng.nextInt(5) < 4 && running) queue += Ask(WATCH_APPEARED)
                return
            }
            if (confirms) {
                val w = rng.nextInt(watches)
                nearby[w] = !nearby[w]
                if (nearby[w]) {
                    if (rng.nextInt(5) < 4) emitHandshake(w) // PHONE_NEARBY
                    if (rng.nextInt(5) < 4 && running) queue += Ask(WATCH_APPEARED)
                }
            } else {
                switchWatch()
            }
        }

        private fun newInstall() {
            kill()
            memory = FakeWatchFeedMemory(origin = nextOrigin()) // nothing restored (item 1)
            snapshots.clear()
            lastEcho = null
            lastHeardAt.fill(-1)
            emptyHeardAt.fill(-1)
            wake()
            if (rng.nextBoolean()) emitHandshake(rng.nextInt(watches))
        }

        private fun restoreOldRecord() {
            if (snapshots.isEmpty()) return
            kill()
            memory.record = snapshots[rng.nextInt(snapshots.size)]
            wake()
        }

        private fun androidLink() {
            val r = rng.nextInt(100)
            val busy = watch.indices.filter { down[it].isNotEmpty() }
            when {
                r < 40 -> if (busy.isNotEmpty()) deliverDown(busy[rng.nextInt(busy.size)])
                r < 50 -> if (busy.isNotEmpty()) busy[rng.nextInt(busy.size)].let { down[it].removeAt(rng.nextInt(down[it].size)) }
                r < 85 -> if (up.isNotEmpty()) {
                    // No order is documented: with a lone watch that goes away, the newest goes
                    // first, so a handshake overtakes every older acknowledgement.
                    val at = if (Feature.AWAY in config.features) up.lastIndex else rng.nextInt(up.size)
                    val echo = if (rng.nextInt(20) == 0) up[at] else up.removeAt(at)
                    wake()
                    queue += Ask(WATCH_ASKED, echo)
                }
                r < 95 -> if (up.isNotEmpty()) up.removeAt(rng.nextInt(up.size))
                else -> { // Play services restarts: what it holds in flight may go
                    up.removeAll { rng.nextBoolean() }
                    for (channel in down) channel.removeAll { rng.nextBoolean() }
                }
            }
        }

        private fun iphoneLink() {
            val r = rng.nextInt(100)
            val w = worn
            when {
                r < 35 -> handshakes[w].firstOrNull()?.let { echo ->
                    if (rng.nextInt(10) != 0) handshakes[w].removeFirst() // a duplicate otherwise
                    wake() // sendMessage from the worn watch wakes the app
                    if (sessionActive && session == w) queue += Ask(WATCH_ASKED, echo, from = "k${key[w]}")
                }
                r < 42 -> handshakes[w].removeFirstOrNull()
                r < 75 -> deliverContext(w)
                r < 82 -> received[w]?.let { take(w, it) } // receivedApplicationContext read again
                r < 92 -> settleSession()
                else -> if (Feature.CONTEXT_LOSS in config.features) pending[w] = null else Unit
            }
        }

        private suspend fun drain() {
            var steps = 0
            while (steps++ < 2_000) {
                if (!confirms) settleSession()
                if (queue.isNotEmpty()) {
                    serveOne()
                    continue
                }
                if (confirms) {
                    val busy = watch.indices.firstOrNull { down[it].isNotEmpty() }
                    if (busy != null) {
                        deliverDown(busy)
                        continue
                    }
                    if (up.isNotEmpty()) {
                        queue += Ask(WATCH_ASKED, up.removeAt(0))
                        continue
                    }
                } else {
                    val echo = handshakes[worn].removeFirstOrNull()
                    if (echo != null) {
                        queue += Ask(WATCH_ASKED, echo, from = "k${key[worn]}")
                        continue
                    }
                    if (pending[worn] != null) {
                        deliverContext(worn)
                        continue
                    }
                }
                return
            }
            problem = problem ?: "the links never went quiet"
        }
    }

    companion object {
        /**
         * Schedules per configuration (three times as many for the one that seeks out D1, half as
         * many for the two outside the documented platform): 1,760 in all, 1,600 until plan 2
         * Task 5 added the switched Android watches. Measured on 25 October over all 1,760, two
         * runs each (Ruling 53 item 4): 0.58 s on the JVM and 3.8 to 4.0 s on the simulator;
         * `scripts/test.sh` runs both. The reports record larger runs of the same code with this
         * one figure raised and the test's timeout lifted, each for that run alone:
         * 1,200,000 schedules for Ruling 50 (`2026-10-25-feed-delivery-number.md`), and 400,000
         * with the configurations Ruling 52 added (`2026-10-25-link-follow-ups.md`).
         */
        private const val RUNS = 160
        private const val SEED = 50_000_000L

        private val X = syncFeed()
        private val Y = syncFeed(fajrShiftMinutes = 1)
        private val Z = syncFeed(fajrShiftMinutes = 2)

        /** Over the link's limit, both: their times alone are X's very bytes. */
        private val W1 = syncFeed(phrases = (1..3).map(::syncPhrase))
        private val W2 = syncFeed(phrases = (1..4).map(::syncPhrase))
    }
}
