package world.taqwa.app.wear.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.tasbeeh.TasbeehState

/**
 * How a phrase's count is kept on the watch (plan 3 Task 6): `"<count>,<round>"` under its id, and
 * read back as a count the counter can show, whatever the file holds.
 */
class TasbeehCodecTest {

    @Test
    fun aCountIsReadBackAsItWasWritten() {
        val state = TasbeehState("after_prayer", 27, 3)
        val raw = TasbeehCodec.encode(state)
        assertEquals("27,3", raw)
        assertEquals(state, TasbeehCodec.decode("after_prayer", raw, total = 100))
    }

    @Test
    fun malformedInputIsAFreshStart() {
        for (raw in listOf(null, "", "27", "27,", ",3", "27;3", "a,b", "27,3,1", " 27,3", "27,3 ", "1.5,2", "99999999999,1")) {
            assertEquals(TasbeehState("subhanallah", 0, 1), TasbeehCodec.decode("subhanallah", raw, total = 100), "'$raw'")
        }
    }

    @Test
    fun aNegativeCountIsHeldToZero() {
        assertEquals(TasbeehState("subhanallah", 0, 2), TasbeehCodec.decode("subhanallah", "-4,2", total = 100))
    }

    @Test
    fun aCountOverTheTotalIsHeldToIt() {
        assertEquals(TasbeehState("custom_1", 33, 1), TasbeehCodec.decode("custom_1", "40,1", total = 33))
    }

    @Test
    fun theRoundIsAtLeastOne() {
        assertEquals(TasbeehState("subhanallah", 5, 1), TasbeehCodec.decode("subhanallah", "5,0", total = 100))
        assertEquals(TasbeehState("subhanallah", 5, 1), TasbeehCodec.decode("subhanallah", "5,-3", total = 100))
    }

    @Test
    fun theStateIsTheIdAskedFor() {
        assertEquals("alhamdulillah", TasbeehCodec.decode("alhamdulillah", "3,1", total = 100).presetId)
    }
}
