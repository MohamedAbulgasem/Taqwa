package world.taqwa.app.wear.complication

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * How a source answers when working out its answer throws (`answerOrNothing`, `orOnFailure`): with
 * what says nothing, and never by letting the throw out, which would end the app's process inside
 * the library's request; and where it is worked out (`answerOffMain`). The library's types need the
 * watch, so the rules are held on plain values.
 */
class ComplicationSourcesTest {

    @Test
    fun anAnswerThatThrowsIsTheFallbackAndTheThrowIsReported() {
        val reported = mutableListOf<Exception>()
        val broken = IllegalStateException("a day out of order")

        val said = orOnFailure(compute = { throw broken }, fallback = { "nothing" }) { reported += it }

        assertEquals("nothing", said)
        assertEquals(1, reported.size)
        assertSame(broken, reported.single())
    }

    @Test
    fun anAnswerThatWorksIsTheAnswerAndNothingIsReported() {
        var fellBack = false
        val reported = mutableListOf<Exception>()

        val said = orOnFailure(compute = { "ASR 16:10" }, fallback = { fellBack = true; "nothing" }) { reported += it }

        assertEquals("ASR 16:10", said)
        assertTrue(!fellBack && reported.isEmpty(), "no fallback and no report")
    }

    @Test
    fun aCancellationIsThrownOnAndNeverReported() {
        // The system calling a request off is not a failure of the source, and a coroutine that
        // swallowed it would go on working for an answer no one waits for.
        val reported = mutableListOf<Exception>()

        assertFailsWith<CancellationException> {
            orOnFailure<String>(compute = { throw CancellationException("called off") }, fallback = { "nothing" }) { reported += it }
        }
        assertTrue(reported.isEmpty(), "nothing reported")
    }

    @Test
    fun anAnswerIsWorkedOutOffTheThreadThatAsked() {
        // The library asks on the main thread; the answer is worked out on Dispatchers.Default's
        // workers (answerOffMain), and handed back to the coroutine that asked.
        val asking = Thread.currentThread()
        var working: Thread? = null

        val said = runBlocking { answerOffMain("test") { working = Thread.currentThread(); null } }

        assertNull(said)
        val worker = assertNotNull(working)
        assertNotSame(asking, worker)
        assertTrue(worker.name.startsWith("DefaultDispatcher-worker"), worker.name)
    }

    @Test
    fun anErrorIsNotAnAnswerToSwallow() {
        // An Error (out of memory, a missing class) is the runtime's, not the feed's: it goes on up.
        assertFailsWith<StackOverflowError> {
            orOnFailure<String>(compute = { throw StackOverflowError() }, fallback = { "nothing" }) {}
        }
    }
}
