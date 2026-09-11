package world.taqwa.app.feature.tasbeeh

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.tasbeeh.TasbeehState
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Counts what the screen would feel, one field per call, so a mapping error cannot hide. */
private class FakeHaptics : Haptics {
    var ticks = 0
    var counts = 0
    var parts = 0
    var sets = 0
    override fun tick() { ticks++ }
    override fun count() { counts++ }
    override fun partComplete() { parts++ }
    override fun setComplete() { sets++ }
}

/**
 * A real [TasbeehStore] over a real DataStore, with the two things the tests need that the store
 * does not expose: how many times it was written, and when a write has actually landed.
 *
 * [writes] moves when `updateData` is *called*, so "no write yet" can be asserted from a
 * virtual-time test without waiting on a file at all; [awaitWrite] then waits for the real write
 * behind it to finish, which is what makes the value readable afterwards.
 */
private class CountingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
    private val completed = Channel<Unit>(Channel.UNLIMITED)
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        writes++
        val result = delegate.updateData(transform)
        completed.send(Unit)
        return result
    }

    /** Suspends until one more write has been written through to the file. */
    suspend fun awaitWrite() { completed.receive() }
}

class TasbeehViewModelTest {

    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-tasbeeh-vm-${Random.nextULong()}.preferences_pb".toPath()
    }

    /**
     * The view model's scope is the test's, so the 300 ms debounce runs on virtual time:
     * `advanceTimeBy` is the clock, and no test has to sleep for a third of a second.
     */
    private fun TestScope.viewModel(store: TasbeehStore, haptics: Haptics = FakeHaptics()) =
        TasbeehViewModel(store, haptics, backgroundScope)

    @Test
    fun eachTapEventFiresItsOwnHaptic() = runTest {
        val haptics = FakeHaptics()
        val vm = viewModel(TasbeehStore(dataStore()), haptics)
        runCurrent()

        repeat(32) { vm.tap() }
        assertEquals(32, haptics.counts)
        assertEquals(0, haptics.parts)

        // 33 ends SubhanAllah, and the dhikr on screen becomes Alhamdulillah at the same moment.
        vm.tap()
        assertEquals(1, haptics.parts)
        assertEquals(1, vm.state.value.currentPart)

        repeat(32) { vm.tap() }
        vm.tap()
        assertEquals(2, haptics.parts)
        assertEquals(66, vm.state.value.state.count)
        assertEquals(2, vm.state.value.currentPart)

        repeat(33) { vm.tap() }
        vm.tap()
        assertEquals(1, haptics.sets)
        assertEquals(100, vm.state.value.state.count)

        // The tap that opens the next round is a plain count, not a second celebration.
        val countsBefore = haptics.counts
        vm.tap()
        assertEquals(countsBefore + 1, haptics.counts)
        assertEquals(TasbeehState("after_prayer", 1, 2), vm.state.value.state)
        assertEquals(1, haptics.sets)
        assertEquals(2, haptics.parts)
        // The compass's own call belongs to the compass.
        assertEquals(0, haptics.ticks)
    }

    @Test
    fun aBurstOfTapsIsOneWriteAfterTheDebounce() = runTest {
        val ds = CountingDataStore(dataStore())
        val store = TasbeehStore(ds)
        val vm = viewModel(store)
        runCurrent()
        val before = ds.writes

        repeat(5) { vm.tap() }
        advanceTimeBy(TasbeehViewModel.WRITE_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(before, ds.writes, "nothing is written while the taps are still coming")

        advanceTimeBy(2)
        runCurrent()
        assertEquals(before + 1, ds.writes, "five taps, one write")
        ds.awaitWrite()
        assertEquals(TasbeehState("after_prayer", 5, 1), store.stateOf("after_prayer").first())
    }

    @Test
    fun flushWritesWithoutWaitingForTheDebounce() = runTest {
        val ds = CountingDataStore(dataStore())
        val store = TasbeehStore(ds)
        val vm = viewModel(store)
        runCurrent()
        val before = ds.writes

        repeat(3) { vm.tap() }
        vm.flush()
        runCurrent()
        assertEquals(before + 1, ds.writes)
        ds.awaitWrite()
        assertEquals(TasbeehState("after_prayer", 3, 1), store.stateOf("after_prayer").first())

        // The pending debounced write was cancelled by the flush, not left to fire again.
        advanceTimeBy(TasbeehViewModel.WRITE_DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals(before + 1, ds.writes)
    }

    @Test
    fun selectingAPresetRestoresThatPresetsOwnCount() = runTest {
        val store = TasbeehStore(dataStore())
        val vm = viewModel(store)
        runCurrent()

        repeat(4) { vm.tap() }
        vm.select("astaghfirullah")
        assertEquals("astaghfirullah", vm.state.first { it.preset.id == "astaghfirullah" }.preset.id)
        assertEquals(0, vm.state.value.state.count)

        repeat(2) { vm.tap() }
        vm.select("after_prayer")
        assertEquals(4, vm.state.first { it.preset.id == "after_prayer" }.state.count)

        vm.select("astaghfirullah")
        assertEquals(2, vm.state.first { it.preset.id == "astaghfirullah" }.state.count)
        // And the selection itself survives, so the screen opens where it was left.
        assertEquals("astaghfirullah", store.selectedId.first())
    }

    @Test
    fun resetReturnsToZeroAndRoundOneAndWritesAtOnce() = runTest {
        val ds = CountingDataStore(dataStore())
        val store = TasbeehStore(ds)
        val vm = viewModel(store)
        runCurrent()
        val before = ds.writes

        repeat(101) { vm.tap() }
        assertEquals(TasbeehState("after_prayer", 1, 2), vm.state.value.state)
        vm.flush()
        runCurrent()
        ds.awaitWrite()
        assertEquals(TasbeehState("after_prayer", 1, 2), store.stateOf("after_prayer").first())

        vm.reset()
        assertEquals(TasbeehState("after_prayer", 0, 1), vm.state.value.state)
        runCurrent()
        ds.awaitWrite()
        assertEquals(TasbeehState("after_prayer", 0, 1), store.stateOf("after_prayer").first())
        assertEquals(2, ds.writes - before, "reset writes at once rather than waiting 300 ms")
    }

    @Test
    fun aCustomPhraseIsAddedSelectedAndDeleted() = runTest {
        val store = TasbeehStore(dataStore()) { 4_242L }
        val vm = viewModel(store)
        runCurrent()

        vm.addCustom("يا لطيف", 40)
        val added = vm.state.first { it.preset.custom }
        assertEquals("custom_4242", added.preset.id)
        assertEquals(40, added.preset.total)
        assertEquals(listOf("custom_4242"), added.custom.map { it.id })
        assertTrue(added.presets.last().custom, "the reader's own phrase comes after the built-ins")

        repeat(3) { vm.tap() }
        vm.removeCustom("custom_4242")
        val after = vm.state.first { !it.preset.custom }
        // Deleting the phrase on screen falls back to the default preset, and its count goes too.
        assertEquals("after_prayer", after.preset.id)
        assertEquals(emptyList(), after.custom)
        assertEquals(emptyList(), store.customPresets.first())
        assertEquals(TasbeehState("custom_4242", 0, 1), store.stateOf("custom_4242").first())
    }

    /**
     * The sheet on an existing phrase is an edit, not only a delete: the phrase and the target
     * both come back changed, and the count is clamped where a lowered target has overtaken it —
     * 8 against a new target of 5 reads 5 of 5, complete.
     */
    @Test
    fun editingTheSelectedCustomRerendersItsPhraseAndTarget() = runTest {
        val store = TasbeehStore(dataStore()) { 4_242L }
        val vm = viewModel(store)
        runCurrent()

        vm.addCustom("La hawla", 10)
        vm.state.first { it.preset.id == "custom_4242" }
        repeat(8) { vm.tap() }

        vm.updateCustom("custom_4242", "La hawla wa la quwwata", 5)
        val edited = vm.state.first { it.preset.total == 5 }

        assertEquals("custom_4242", edited.preset.id, "the id, and so the chip's place, is kept")
        assertEquals("La hawla wa la quwwata", edited.preset.parts.first().dhikr.arabic)
        assertEquals(5, edited.state.count, "the count is clamped to the target it now exceeds")
        assertEquals(1f, edited.progress, "5 of 5 is a closed ring")
        assertEquals(listOf("La hawla wa la quwwata"), edited.custom.map { it.parts.first().dhikr.arabic })
        // And the clamp reached disk, not only the screen.
        assertEquals(TasbeehState("custom_4242", 5, 1), store.stateOf("custom_4242").first { it.count == 5 })
    }

    /**
     * Editing a phrase that is not the one being counted must leave the counting alone: the chip
     * row is re-read, the selection and its count are not touched, and the pending taps still
     * reach disk — the same contract [removingACustomWritesThePendingCountFirst] holds a delete to.
     */
    @Test
    fun editingACustomThatIsNotSelectedLeavesTheSelectionAlone() = runTest {
        var stamp = 4_242L
        val store = TasbeehStore(dataStore()) { stamp++ }
        val vm = viewModel(store)
        runCurrent()

        vm.addCustom("first", 40)
        vm.state.first { it.preset.id == "custom_4242" }
        vm.addCustom("second", 40)
        vm.state.first { it.preset.id == "custom_4243" }

        repeat(3) { vm.tap() }
        vm.updateCustom("custom_4242", "first, rewritten", 3)
        runCurrent()

        val after = vm.state.first { it.custom.first().parts.first().dhikr.arabic == "first, rewritten" }
        assertEquals("custom_4243", after.preset.id, "the selection is untouched")
        assertEquals(3, after.state.count)
        assertEquals(40, after.preset.total, "and so is the target it is being counted against")
        assertEquals(listOf("custom_4242", "custom_4243"), after.custom.map { it.id })
        assertEquals(
            TasbeehState("custom_4243", 3, 1),
            store.stateOf("custom_4243").first { it.count == 3 },
            "the three taps reach disk rather than being cancelled by the edit",
        )
    }

    /**
     * A chip tap and a page tap land within the same 300 ms more often than not. The switch used
     * to share its job slot with the debounced write, so the tap's `scheduleWrite` cancelled the
     * switch outright — between saving the old count and selecting the new preset — and the chip
     * quietly did nothing.
     */
    @Test
    fun aTapDuringAPresetSwitchDoesNotCancelTheSwitch() = runTest {
        val store = TasbeehStore(dataStore())
        val vm = viewModel(store)
        runCurrent()

        vm.select("astaghfirullah")
        // The switch is still in flight — its coroutine has not run yet.
        vm.tap()
        runCurrent()

        // It survives the tap and lands: the tap counted against the preset still on screen.
        assertEquals(0, vm.state.first { it.preset.id == "astaghfirullah" }.state.count)
        assertEquals("astaghfirullah", store.selectedId.first())

        vm.tap()
        assertEquals("astaghfirullah", vm.state.value.preset.id)
        assertEquals(1, vm.state.value.state.count)
    }

    /**
     * Deleting a phrase that is not the one being counted must not take the pending count with
     * it: `removeCustom` writes what is on screen before it removes anything, exactly as a chip
     * switch writes the preset it leaves.
     */
    @Test
    fun removingACustomWritesThePendingCountFirst() = runTest {
        var stamp = 4_242L
        val store = TasbeehStore(dataStore()) { stamp++ }
        val vm = viewModel(store)
        runCurrent()

        vm.addCustom("يا لطيف", 40)
        vm.state.first { it.preset.id == "custom_4242" }
        vm.addCustom("يا رحيم", 40)
        vm.state.first { it.preset.id == "custom_4243" }

        repeat(3) { vm.tap() }
        // The debounced write is still pending, and the phrase going is not the one on screen.
        vm.removeCustom("custom_4242")
        runCurrent()

        assertEquals(
            TasbeehState("custom_4243", 3, 1),
            store.stateOf("custom_4243").first { it.count == 3 },
            "the three taps reach disk rather than being cancelled by the delete",
        )
        assertEquals(listOf("custom_4243"), vm.state.first { it.custom.size == 1 }.custom.map { it.id })
        assertEquals("custom_4243", vm.state.value.preset.id)
    }

    /**
     * The stored selection can outlive the phrase it names — a delete from the widget, a
     * half-written file. The screen opens on the default instead of throwing on the read.
     */
    @Test
    fun aStoredSelectionOfAMissingCustomFallsBackToTheDefault() = runTest {
        val store = TasbeehStore(dataStore())
        store.select("custom_9999")
        store.save(TasbeehState("after_prayer", 7, 1))

        val vm = viewModel(store)
        runCurrent()

        // The fallback brings its own stored count with it, so the read plainly completed.
        assertEquals(TasbeehState("after_prayer", 7, 1), vm.state.first { it.state.count == 7 }.state)
        assertEquals("after_prayer", vm.state.value.preset.id)
        assertTrue(vm.state.value.custom.isEmpty())
    }
}
