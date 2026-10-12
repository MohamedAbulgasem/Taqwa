package world.taqwa.timetables

import java.io.File

/**
 * Ruling R116's own case, kept as a fixture: the stamps as at the first run of 1 October 2026, when Diyanet's
 * captures reached 1 November, so İstanbul's page shows October alone and names 2 November as the first unchecked
 * day. The committed stamps move on whenever the monitor's `prove` adds Diyanet's next captures (on 12 October it
 * added 409 days, and with them November's first eleven), and a test of the rule must not fail because the proof
 * grew: here every tr.diyanet unit, and the entry, is checked exactly as it was that morning.
 */
internal object StampsAsOf {
    private val FIRST_OCTOBER_DIYANET = listOf("2026-09-25..2026-11-01", "2027-01-01..2027-12-31")

    fun firstOctober(official: File, stamps: Map<String, Stamp>): Map<String, Stamp> {
        @Suppress("UNCHECKED_CAST")
        val root = LinkedHashMap(Json.parse(official.resolve("stamps/tr.diyanet.json").readText()) as Map<String, Any?>)
        root["checked"] = FIRST_OCTOBER_DIYANET
        @Suppress("UNCHECKED_CAST")
        root["units"] = (root["units"] as Map<String, Any?>).mapValues { (_, unit) ->
            LinkedHashMap(unit as Map<String, Any?>).also { it["checked"] = FIRST_OCTOBER_DIYANET }
        }
        return stamps + ("tr.diyanet" to Stamp(root))
    }
}
