package world.taqwa.app.wear.face

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchFaceWords
import world.taqwa.app.wear.TestFeeds

/**
 * Watch Face Push as Taqwa uses it (plan 5 Task 5): the face the app bundles is installed in its one
 * slot by the system, updated in that slot after each app update, and made active once, on the
 * wearer's tap. The rules are Google's ("Watch Face Push: Wear OS app configuration"): one slot, a
 * slot id never kept, no activity check right after an update, one activation per app and one
 * refusal of its permission. The bundled face is version 40; an older one installed is 39.
 */
class FacePushTest {

    private val dir: File = Files.createTempDirectory("face-push").toFile().apply { deleteOnExit() }
    private var clock: Duration = 1_000.seconds
    private val bundled40 = BundledFace(File(dir, "face-40.apk"), "token-40", 40)

    private fun ours(version: Long, slot: String = "slot-a") = InstalledFace(slot, FACE_PACKAGE, version)

    private fun memory(name: String = "face-memory") = FaceMemory(File(dir, name))

    private fun push(port: FakeFacePushPort, memory: FaceMemory = memory(), bundled: BundledFace? = bundled40) =
        FacePush(port, bundled = { bundled }, memory = memory, elapsed = { clock })

    @Test
    fun belowWearOsSixThereIsNoRow() = runTest {
        val port = FakeFacePushPort(supported = false)
        val push = push(port)

        assertEquals(FaceState.UNSUPPORTED, push.state())
        port.calls.clear()
        assertEquals(FaceState.UNSUPPORTED, push.ensureCurrent())
        assertEquals(listOf("installed"), port.calls, "no other call")
    }

    @Test
    fun withoutABundledFaceThereIsNoRow() = runTest {
        assertEquals(FaceState.NOT_BUNDLED, push(FakeFacePushPort(listOf(ours(40))), bundled = null).state())
        assertEquals(FaceState.NOT_BUNDLED, push(FakeFacePushPort(), bundled = null).ensureCurrent())
    }

    @Test
    fun aWatchWithoutTheFaceOffersToAddIt() = runTest {
        assertEquals(FaceState.NOT_INSTALLED, push(FakeFacePushPort(free = 1)).state())
    }

    @Test
    fun theDefaultFaceNotChosenOffersToUseIt() = runTest {
        assertEquals(FaceState.INSTALLED, push(FakeFacePushPort(listOf(ours(40)), free = 0, active = false)).state())
    }

    @Test
    fun theActiveFaceShowsNoRow() = runTest {
        assertEquals(FaceState.ACTIVE, push(FakeFacePushPort(listOf(ours(40)), free = 0, active = true)).state())
    }

    @Test
    fun anOlderFaceIsUpdatedInItsSlot() = runTest {
        val port = FakeFacePushPort(listOf(ours(39)), free = 0)

        assertEquals(FaceState.INSTALLED, push(port).ensureCurrent())
        assertEquals(listOf("update slot-a token-40"), port.calls.filter { it.startsWith("update") })
        assertFalse("add" in port.names(), "never added")
        assertEquals(listOf(ours(40)), port.faces, "the same slot, the bundled version")
    }

    @Test
    fun twoConcurrentUpdatesUpdateOnce() = runTest {
        val port = FakeFacePushPort(listOf(ours(39)), free = 0).apply { updateGate = CompletableDeferred() }
        val push = push(port)

        // The app's start and MY_PACKAGE_REPLACED, both at once on the process's one FacePush.
        val start = async { push.ensureCurrent() }
        val replaced = async { push.ensureCurrent() }
        runCurrent()
        port.updateGate!!.complete(Unit)

        assertEquals(start.await(), replaced.await())
        assertEquals(1, port.names().count { it == "update" }, port.calls.toString())
    }

    @Test
    fun anActiveFaceStaysActiveThroughItsUpdate() = runTest {
        val port = FakeFacePushPort(listOf(ours(39)), free = 0, active = true)

        assertEquals(FaceState.ACTIVE, push(port).ensureCurrent())
        val names = port.names()
        assertTrue(names.indexOf("isActive") in 0 until names.indexOf("update"), "isActive before the update: $names")
        assertFalse("isActive" in names.drop(names.indexOf("update")), "never after it: $names")
    }

    @Test
    fun theSameOrANewerFaceIsLeftAlone() = runTest {
        for (version in listOf(40L, 41L)) {
            val port = FakeFacePushPort(listOf(ours(version)), free = 0)
            push(port).ensureCurrent()
            assertFalse("update" in port.names(), "$version: ${port.calls}")
        }
    }

    @Test
    fun onlyAnAddOrAnUpdateAsksForTheFacesFile() = runTest {
        var asked = 0
        val face = BundledFace("token-40", 40) { asked++; File(dir, "face-40.apk") }

        push(FakeFacePushPort(listOf(ours(40)), free = 0), bundled = face).state()
        push(FakeFacePushPort(listOf(ours(40)), free = 0), memory("m1"), bundled = face).ensureCurrent()
        push(FakeFacePushPort(free = 1), memory("m2"), bundled = face).state()
        assertEquals(0, asked, "a state, or a face already current, needs no copy of the face")

        push(FakeFacePushPort(listOf(ours(39)), free = 0), memory("m3"), bundled = face).ensureCurrent()
        assertEquals(1, asked, "the update")
        push(FakeFacePushPort(free = 1), memory("m4"), bundled = face).addAndActivate(permissionGranted = false)
        assertEquals(2, asked, "the add")
    }

    @Test
    fun aFaceThatCannotBeCopiedFailsItsAddOrUpdate() = runTest {
        val face = BundledFace("token-40", 40) { throw IOException("no space left on device") }

        val older = FakeFacePushPort(listOf(ours(39)), free = 0)
        assertEquals(FaceState.FAILED, push(older, bundled = face).ensureCurrent())
        assertFalse("update" in older.names(), older.calls.toString())

        val removed = FakeFacePushPort(free = 1)
        assertEquals(FaceState.FAILED, push(removed, memory("m1"), bundled = face).addAndActivate(permissionGranted = true))
        assertFalse("add" in removed.names(), removed.calls.toString())
        assertFalse(memory("m1").spent, "the activation is kept for the next tap")
    }

    @Test
    fun theSlotIsReadAgainBeforeEveryCall() = runTest {
        val logs = mutableListOf<List<String>>()
        FakeFacePushPort(listOf(ours(39)), free = 0).also { push(it).ensureCurrent(); logs += it.names() }
        FakeFacePushPort(free = 1).also { push(it, memory("m1")).addAndActivate(permissionGranted = true); logs += it.names() }
        FakeFacePushPort(listOf(ours(39)), free = 0).also { push(it, memory("m2")).addAndActivate(permissionGranted = true); logs += it.names() }

        for (log in logs) {
            var readSinceLastSlotCall = false
            for (name in log) {
                when (name) {
                    "installed" -> readSinceLastSlotCall = true
                    "add", "update", "setActive" -> {
                        assertTrue(readSinceLastSlotCall, "$name without the slot read again first: $log")
                        readSinceLastSlotCall = false
                    }
                }
            }
        }
    }

    @Test
    fun noActivityCheckRightAfterAnUpdate() = runTest {
        val port = FakeFacePushPort(listOf(ours(39)), free = 0)
        val push = push(port)
        val published = mutableListOf<Pair<FaceState, Boolean>>()

        val answered = refreshAfterUpdate(push) { state, spent -> published += state to spent }

        val names = port.names()
        assertFalse("isActive" in names.drop(names.indexOf("update")), names.toString())
        assertEquals(listOf(answered to false), published)

        clock += 29.seconds
        port.calls.clear()
        assertEquals(answered, push.state())
        assertFalse("isActive" in port.names(), "within UPDATE_SETTLE: ${port.calls}")

        clock += 2.seconds
        port.calls.clear()
        push.state()
        assertTrue("isActive" in port.names(), "past UPDATE_SETTLE: ${port.calls}")
    }

    /**
     * Step 7 check 4 on emulator-5558: the system's Push service updated the bundled face itself when
     * the app was installed over (41 to 42), before the app ran, and isWatchFaceActive answered
     * false for the active face right after it, as Google warns. So a face whose version this
     * process did not make is a change just made: no activity check, the activity last seen, held
     * for UPDATE_SETTLE, then the system is asked again.
     */
    @Test
    fun aFaceTheSystemUpdatedIsNotAskedWhetherItIsActiveRightAway() = runTest {
        val memory = memory()
        val port = FakeFacePushPort(listOf(ours(39)), free = 0, active = true)
        assertEquals(FaceState.ACTIVE, push(port, memory, bundled = BundledFace(File(dir, "face-39.apk"), "token-39", 39)).state())

        // The app updated, and the system updated its face with it: a new process, over the same
        // file, finds 40 current.
        val after = FakeFacePushPort(listOf(ours(40)), free = 0, active = false)
        val push = push(after, memory())
        assertEquals(FaceState.ACTIVE, push.ensureCurrent())
        assertFalse("isActive" in after.names(), after.calls.toString())
        assertFalse("update" in after.names())

        clock += 29.seconds
        after.calls.clear()
        assertEquals(FaceState.ACTIVE, push.state())
        assertFalse("isActive" in after.names(), "within UPDATE_SETTLE: ${after.calls}")

        clock += 2.seconds
        after.calls.clear()
        assertEquals(FaceState.INSTALLED, push.state(), "past it, the system's answer")
        assertTrue("isActive" in after.names())
    }

    @Test
    fun theFirstFaceSeenIsAskedWhetherItIsActive() = runTest {
        // A first install: nothing seen before, so the system is asked (its default face is never active).
        val port = FakeFacePushPort(listOf(ours(40)), free = 0)
        assertEquals(FaceState.INSTALLED, push(port).ensureCurrent())
        assertTrue("isActive" in port.names())
    }

    @Test
    fun noActivityCheckRightAfterSetActive() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0)
        val push = push(port)

        assertEquals(FaceState.ACTIVE, push.addAndActivate(permissionGranted = true))
        assertEquals("setActive", port.names().last())

        clock += 10.seconds
        port.calls.clear()
        assertEquals(FaceState.ACTIVE, push.state())
        assertFalse("isActive" in port.names(), port.calls.toString())
    }

    @Test
    fun addAndActivateAddsThenActivates() = runTest {
        val port = FakeFacePushPort(free = 1)

        assertEquals(FaceState.ACTIVE, push(port).addAndActivate(permissionGranted = true))
        val slotCalls = port.calls.filter { it.startsWith("add") || it.startsWith("setActive") }
        assertEquals(listOf("add token-40", "setActive new-1"), slotCalls)
    }

    @Test
    fun anInstalledFaceIsOnlyMadeActive() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0)
        val push = push(port)

        assertEquals(FaceState.ACTIVE, push.addAndActivate(permissionGranted = true))
        assertFalse("add" in port.names() || "update" in port.names(), port.calls.toString())
        assertEquals(listOf("setActive slot-a"), port.calls.filter { it.startsWith("setActive") })
        assertTrue(push.spent)
    }

    @Test
    fun anInstalledOlderFaceIsUpdatedThenMadeActive() = runTest {
        val port = FakeFacePushPort(listOf(ours(39)), free = 0)

        assertEquals(FaceState.ACTIVE, push(port).addAndActivate(permissionGranted = true))
        val names = port.names()
        assertFalse("add" in names)
        assertTrue(names.indexOf("update") in 0 until names.indexOf("setActive"), names.toString())
    }

    @Test
    fun aRefusedPermissionLeadsToChoosingItYourself() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0)
        val memory = memory()

        assertEquals(FaceState.CHOOSE_IT_YOURSELF, push(port, memory).addAndActivate(permissionGranted = false))
        assertFalse("setActive" in port.names())
        assertTrue(memory.spent)
    }

    @Test
    fun aSecondActivationLeadsToChoosingItYourself() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0).apply { setActiveThrows = FaceRefused(null) }
        val push = push(port)

        assertEquals(FaceState.CHOOSE_IT_YOURSELF, push.addAndActivate(permissionGranted = true))
        assertTrue(push.spent)
    }

    /**
     * Erratum p5 (setWatchFaceAsActive's codes): only a spent activation or a missing permission is
     * [FaceRefused]. Any other failure, the service's passing error or a slot changed under the
     * call, answers FAILED and keeps the one activation for the next tap.
     */
    @Test
    fun aTransientSetActiveErrorIsFailedNotSpent() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0).apply { setActiveThrows = IllegalStateException("ERROR_UNKNOWN") }
        val push = push(port)

        assertEquals(FaceState.FAILED, push.addAndActivate(permissionGranted = true))
        assertFalse(push.spent)

        port.setActiveThrows = null
        assertEquals(FaceState.ACTIVE, push.addAndActivate(permissionGranted = true))
        assertTrue(push.spent)
    }

    @Test
    fun aRemovedFaceIsAddedEvenWhenTheActivationIsSpent() = runTest {
        val memory = memory()
        for (round in 1..2) {
            // First with the permission refused, then with the activation already spent.
            val port = FakeFacePushPort(free = 1)
            assertEquals(round == 2, memory.spent)
            assertEquals(FaceState.CHOOSE_IT_YOURSELF, push(port, memory).addAndActivate(permissionGranted = round == 2))
            assertTrue("add" in port.names(), "round $round: ${port.calls}")
            assertFalse("setActive" in port.names(), "round $round: ${port.calls}")
            assertEquals(listOf(FACE_PACKAGE), port.faces.map { it.packageName }, "the face the guidance names is in the picker")
        }
    }

    @Test
    fun aFullSlotIsFailedNotThrown() = runTest {
        val port = FakeFacePushPort(free = 0)

        assertEquals(FaceState.FAILED, push(port).addAndActivate(permissionGranted = true))
        assertFalse("add" in port.names())
    }

    @Test
    fun theSpentActivationOutlivesTheProcess() = runTest {
        push(FakeFacePushPort(listOf(ours(40)), free = 0), memory("kept")).addAndActivate(permissionGranted = false)

        // A new process: a new FacePush over the same file.
        val port = FakeFacePushPort(listOf(ours(40)), free = 0)
        val push = push(port, memory("kept"))
        assertTrue(push.spent)
        assertEquals(FaceState.CHOOSE_IT_YOURSELF, push.state())
        assertEquals(FaceState.CHOOSE_IT_YOURSELF, push.addAndActivate(permissionGranted = true))
        assertFalse("setActive" in port.names())
    }

    @Test
    fun aPushThatThrowsShowsNoRow() = runTest {
        // A Push service that answers an error, and a watch whose system lacks the library's wear-sdk
        // (erratum p5): no row, whatever is asked.
        for (thrown in listOf(IllegalStateException("no service"), NoClassDefFoundError("com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager"))) {
            val port = FakeFacePushPort(listOf(ours(39)), free = 0).apply { installedThrows = thrown }
            val push = push(port)
            assertEquals(FaceState.UNSUPPORTED, push.state(), thrown.toString())
            assertEquals(FaceState.UNSUPPORTED, push.ensureCurrent(), thrown.toString())
            assertEquals(FaceState.UNSUPPORTED, push.addAndActivate(permissionGranted = true), thrown.toString())
        }

        val failing = FakeFacePushPort(free = 1).apply { addThrows = IllegalArgumentException("ERROR_INVALID_VALIDATION_TOKEN") }
        assertEquals(FaceState.FAILED, push(failing).addAndActivate(permissionGranted = true))

        val cancelled = FakeFacePushPort().apply { installedThrows = CancellationException("left") }
        assertFailsWith<CancellationException> { push(cancelled).state() }
    }

    @Test
    fun anotherPackageInTheListIsNeverTouched() = runTest {
        val other = InstalledFace("slot-x", "world.taqwa.app.watchfacepush.other", 1)
        val port = FakeFacePushPort(listOf(other, ours(39)), free = 0)

        push(port).ensureCurrent()
        assertEquals(listOf("update slot-a token-40"), port.calls.filter { it.startsWith("update") })

        val alone = FakeFacePushPort(listOf(other), free = 0)
        assertEquals(FaceState.NOT_INSTALLED, push(alone).ensureCurrent())
        assertFalse("update" in alone.names())
    }

    @Test
    fun theCapabilityFollowsTheState() {
        val wanted = setOf(FaceState.NOT_INSTALLED, FaceState.INSTALLED, FaceState.FAILED)
        for (state in FaceState.entries) {
            assertEquals(state in wanted, FaceCapability.wanted(state, spent = false), state.name)
        }
    }

    @Test
    fun theRowsGoQuietOnceTheActivationIsSpent() = runTest {
        for (state in FaceState.entries) {
            assertFalse(FaceCapability.wanted(state, spent = true), state.name)
        }

        val memory = memory()
        push(FakeFacePushPort(listOf(ours(40)), free = 0), memory).addAndActivate(permissionGranted = false)
        val published = mutableListOf<Pair<FaceState, Boolean>>()
        refreshAfterUpdate(push(FakeFacePushPort(listOf(ours(40)), free = 0), memory)) { state, spent -> published += state to spent }
        assertEquals(listOf(FaceState.CHOOSE_IT_YOURSELF to true), published)
        assertFalse(published.single().let { (state, spent) -> FaceCapability.wanted(state, spent) }, "the capability is removed")
    }

    /**
     * The Lead's call of 9 November: the face screen publishes what it reads as it opens, as Today
     * does, so the phone's row, which opens it, follows a face chosen in the watch's own picker
     * without waiting up to a day for the next publish. The state is shown before it is published,
     * so the screen waits for nothing new.
     */
    @Test
    fun theFaceScreenPublishesTheStateItReadsAsItOpens() = runTest {
        val chosenInThePicker = FakeFacePushPort(listOf(ours(40)), free = 0, active = true)
        val events = mutableListOf<String>()

        readAndPublish(push(chosenInThePicker), boundMs = 20_000, show = { events += "show $it" }) { state, spent -> events += "publish $state $spent" }

        assertEquals(listOf("show ACTIVE", "publish ACTIVE false"), events)
        assertFalse(FaceCapability.wanted(FaceState.ACTIVE, spent = false), "the row goes")

        val removedInThePicker = FakeFacePushPort(free = 1)
        events.clear()
        readAndPublish(push(removedInThePicker), boundMs = 20_000, show = { events += "show $it" }) { state, spent -> events += "publish $state $spent" }

        assertEquals(listOf("show NOT_INSTALLED", "publish NOT_INSTALLED false"), events)
        assertTrue(FaceCapability.wanted(FaceState.NOT_INSTALLED, spent = false), "the row comes back")
    }

    /** A state the screen cannot read in time is shown as FAILED and never published: it says nothing of the face. */
    @Test
    fun aStateNotReadInTimeIsShownButNotPublished() = runTest {
        val port = FakeFacePushPort(listOf(ours(40)), free = 0, active = true).apply { installedGate = CompletableDeferred() }
        val events = mutableListOf<String>()

        readAndPublish(push(port), boundMs = 20_000, show = { events += "show $it" }) { state, spent -> events += "publish $state $spent" }

        assertEquals(listOf("show FAILED"), events)
        assertEquals(20_000L, testScheduler.currentTime, "cut at the bound")
    }

    @Test
    fun theFaceScreenSaysEachStatesLineAndOffersTheButtonOnlyWhereATapHelps() {
        val words = WatchFaceWords(add = "add", about = "about", active = "active", chooseIt = "choose", failed = "failed")
        val expected = mapOf(
            null to FaceView("about", null),
            FaceState.UNSUPPORTED to FaceView("about", null),
            FaceState.NOT_BUNDLED to FaceView("about", null),
            FaceState.NOT_INSTALLED to FaceView("about", "add"),
            FaceState.INSTALLED to FaceView("about", "add"),
            FaceState.ACTIVE to FaceView("active", null),
            FaceState.CHOOSE_IT_YOURSELF to FaceView("choose", null),
            FaceState.FAILED to FaceView("failed", "add"),
        )
        for ((state, view) in expected) assertEquals(view, faceView(state, words), state.toString())
    }

    @Test
    fun theFeedsFaceWordsComeFirstAndTheWatchsOwnBeforeAnyFeed() {
        val own = WatchFaceWords(add = "own add", about = "own about", active = "own active", chooseIt = "own choose", failed = "own failed")
        val arabic = WatchFaceWords(add = "أضف", about = "عن", active = "نشطة", chooseIt = "اختر", failed = "تعذّر")

        assertEquals(own, faceWords(null, own), "before any feed")
        val feed = TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(face = arabic))
        assertEquals(arabic, faceWords(feed, own), "the feed's first")
        val older = TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(face = arabic.copy(failed = " ")))
        assertEquals(arabic.copy(failed = "own failed"), faceWords(older, own), "a phrase the feed left blank is the watch's own")
    }
}
