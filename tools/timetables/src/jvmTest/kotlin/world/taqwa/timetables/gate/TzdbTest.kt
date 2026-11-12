package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tz database check every gate, monitor and prove run starts with ([Tzdb]). */
class TzdbTest {

    @Test
    fun `the JVM these tests run on carries the minimum tz database or later`() {
        // The surveys and the gate hold Canada's clocks as tzdata 2026e has them (permanent offsets from 1 Nov 2026): on older rules
        // their tests fail an hour off, so this says why first.
        assertNull(Tzdb.problem(), "tz database ${Tzdb.version()} on java.home ${System.getProperty("java.home")}")
    }

    @Test
    fun `versions order by year then letters`() {
        assertTrue(Tzdb.atLeast("2026e", "2026e"))
        assertTrue(Tzdb.atLeast("2026f", "2026e"))
        assertTrue(Tzdb.atLeast("2027a", "2026e"))
        assertFalse(Tzdb.atLeast("2026d", "2026e"))
        assertFalse(Tzdb.atLeast("2025z", "2026e"))
        assertTrue(Tzdb.atLeast("2026aa", "2026z"))
        assertFailsWith<IllegalArgumentException> { Tzdb.atLeast("21.0.13", "2026e") }
    }

    @Test
    fun `an older tz database is refused with a reason that names both versions`() {
        val problem = assertNotNull(Tzdb.problem("2026a"))
        assertTrue("2026a, older than ${Tzdb.MINIMUM}" in problem, problem)
        assertNull(Tzdb.problem(Tzdb.MINIMUM))
    }

    @Test
    fun `the shell's check names the same minimum`() {
        val python = TestPaths.repoRoot.resolve("tools/timetables/monitor/tzdb.py").readText()
        assertEquals(Tzdb.MINIMUM, Regex("""(?m)^MINIMUM = "([^"]+)"""").find(python)?.groupValues?.get(1))
    }
}
