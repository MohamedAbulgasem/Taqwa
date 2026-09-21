package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostGateTest {
    @Test
    fun `the first post always goes`() {
        assertTrue(PostGate(1_000).shouldPost("Al-Baqarah 0%", nowMs = 5))
    }

    @Test
    fun `the same content is never posted twice however long it has been`() {
        val gate = PostGate(1_000)
        assertTrue(gate.shouldPost("12 of 114 surahs", 0))
        assertFalse(gate.shouldPost("12 of 114 surahs", 60_000))
    }

    @Test
    fun `a change inside the interval waits and goes on a later ask`() {
        val gate = PostGate(1_000)
        assertTrue(gate.shouldPost("1%", 0))
        assertFalse(gate.shouldPost("2%", 400))
        assertFalse(gate.shouldPost("3%", 900))
        assertTrue(gate.shouldPost("3%", 1_000))
    }

    @Test
    fun `forty asks a second from five workers post once a second`() {
        val gate = PostGate(1_000)
        var posts = 0
        // Ten seconds, 200 asks a second in all, the content changing on every one of them.
        for (ms in 0 until 10_000 step 5) if (gate.shouldPost("bytes $ms", ms.toLong())) posts++
        assertEquals(10, posts)
    }

    @Test
    fun `after a reset the next post is a first again`() {
        val gate = PostGate(1_000)
        assertTrue(gate.shouldPost("a", 0))
        gate.reset()
        assertTrue(gate.shouldPost("a", 1))
    }

    @Test
    fun `a forced post counts so the event right behind it does not post again`() {
        val gate = PostGate(1_000)
        gate.record("12 of 114 surahs", 0)
        assertFalse(gate.shouldPost("12 of 114 surahs", 300))
        assertFalse(gate.shouldPost("13 of 114 surahs", 300))
        assertTrue(gate.shouldPost("13 of 114 surahs", 1_000))
    }
}
