package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Scope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Stamps.limits` (called for the top-level `lateLimits` summary) once keyed its result by the
 * readable label alone, "<source>: <minutes> min" — dropping the reason. Two different `Limit`s
 * that share a source and minutes but differ in reason (DUM RF's Moscow Fajr and end-of-eating
 * limits are both 4 min from "unit moscow") then collided on that label, and `associate` silently
 * kept only the last one, exactly as `ru.dumrf.json` showed for real.
 */
class StampsTest {

    private val method = Registry.byId("sg.muis")!!.method!!

    private val entry = RegistryEntry(
        id = "test.entry", shortNameKey = "x", entryClass = EntryClass.D_AUTHORITY, about = AboutTemplate.AUTHORITY_CHECKED,
        method = method, school = Registry.byId("sg.muis")!!.school, schoolKnown = true, scope = Scope.GLOBAL,
    )

    @Test
    fun `two limits with the same source and minutes but different reasons both reach the summary`() {
        val stats = EntryStats(entry)
        stats.event(Event.FAJR).applied["a"] = Limit(4, "unit moscow", "moscow fajr reason")
        stats.event(Event.END_OF_EATING).applied["b"] = Limit(4, "unit moscow", "moscow end reason")
        // An unrelated limit, to check it keeps its plain label (no disambiguator needed).
        stats.event(Event.ISHA).applied["c"] = Limit(5, "unit moscow", "moscow isha reason")

        @Suppress("UNCHECKED_CAST")
        val lateLimits = Stamps.stamp(stats, "hash")["lateLimits"] as Map<String, Any?>

        assertEquals(
            listOf("unit moscow: 4 min (endOfEating)", "unit moscow: 4 min (fajr)", "unit moscow: 5 min"),
            lateLimits.keys.toList(),
        )
        assertEquals(
            mapOf("reason" to "moscow fajr reason", "events" to listOf("fajr")),
            lateLimits["unit moscow: 4 min (fajr)"],
        )
        assertEquals(
            mapOf("reason" to "moscow end reason", "events" to listOf("endOfEating")),
            lateLimits["unit moscow: 4 min (endOfEating)"],
        )
        assertEquals(
            mapOf("reason" to "moscow isha reason", "events" to listOf("isha")),
            lateLimits["unit moscow: 5 min"],
        )
    }
}
