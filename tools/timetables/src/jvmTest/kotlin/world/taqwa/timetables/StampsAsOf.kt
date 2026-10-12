package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import java.io.File

/**
 * The committed stamps held where a test's case needs them, so that the monitor's `prove` proving a timetable further
 * never fails a test of a rule. Only the days checked are held; every figure (place-days, places, lateness) stays the
 * committed stamps' own.
 */
internal object StampsAsOf {
    private val FIRST_OCTOBER_DIYANET = listOf("2026-09-25..2026-11-01", "2027-01-01..2027-12-31")

    /**
     * Ruling R116's own case, kept as a fixture: the stamps as at the first run of 1 October 2026, when Diyanet's
     * captures reached 1 November, so İstanbul's page shows October alone and names 2 November as the first unchecked
     * day. The committed stamps move on whenever the monitor's `prove` adds Diyanet's next captures (on 12 October it
     * added 409 days, and with them November's first eleven), and a test of the rule must not fail because the proof
     * grew: here every tr.diyanet unit, and the entry, is checked exactly as it was that morning.
     */
    fun firstOctober(official: File, stamps: Map<String, Stamp>): Map<String, Stamp> {
        val root = json(official, "tr.diyanet")
        root["checked"] = FIRST_OCTOBER_DIYANET
        @Suppress("UNCHECKED_CAST")
        root["units"] = (root["units"] as Map<String, Any?>).mapValues { (_, unit) ->
            LinkedHashMap(unit as Map<String, Any?>).also { it["checked"] = FIRST_OCTOBER_DIYANET }
        }
        return stamps + ("tr.diyanet" to Stamp(root))
    }

    /** London's table, and Toronto's cautious entry with the three mosques whose tables it combines. */
    private val LONDON_AND_TORONTO = listOf("gb.london.lupt", "ca.toronto", "ca.ift", "ca.iit", "ca.mac")
    private val END_OF_2026 = LocalDate(2026, 12, 31)
    private val AFTER_2026 = Checked.parse(listOf("2027-01-01..9999-12-31"))

    /**
     * London's and Toronto's proof as it stood while their 2026 tables were the last the gate held, which the site's
     * cases are built on: London checked through 31 December 2026, so a page built in December shows it alone and names
     * 1 January 2027; Toronto's members through the same day, IFT's fault days excused. LUPT's next year comes through
     * the LPT API monthly and the Toronto mosques' with each new year, and the monitor's `prove` proves them as they
     * come: here every day gb.london.lupt, ca.toronto, ca.ift, ca.iit and ca.mac checked or excused after 31 December
     * 2026 is left out (the entry's runs, each unit's, each member's at each point). Their 2026 tables are held whole,
     * so up to that day this is the committed stamps' own proof.
     */
    fun londonAndTorontoThrough2026(official: File, stamps: Map<String, Stamp>): Map<String, Stamp> =
        stamps + LONDON_AND_TORONTO.associateWith { entry -> Stamp(endingWith2026(json(official, entry))) }

    @Suppress("UNCHECKED_CAST")
    private fun json(official: File, entry: String): MutableMap<String, Any?> =
        LinkedHashMap(Json.parse(official.resolve("stamps/$entry.json").readText()) as Map<String, Any?>)

    /** [root] with no day after 2026 in its checked and excused runs, and its last date no later. */
    @Suppress("UNCHECKED_CAST")
    private fun endingWith2026(root: MutableMap<String, Any?>): Map<String, Any?> {
        fun runs(value: Any?) = Checked.parse(value).minus(AFTER_2026).runs.map { "${it.start}..${it.endInclusive}" }
        fun MutableMap<String, Any?>.cut(key: String) {
            if (key in this) this[key] = runs(this[key])
        }
        root.cut("checked")
        root["last"] = minOf(LocalDate.parse(root["last"] as String), END_OF_2026).toString()
        (root["units"] as? Map<String, Any?>)?.let { units ->
            root["units"] = units.mapValues { (_, unit) -> LinkedHashMap(unit as Map<String, Any?>).apply { cut("checked") } }
        }
        (root["members"] as? Map<String, Any?>)?.let { members ->
            root["members"] = members.mapValues { (_, value) ->
                LinkedHashMap(value as Map<String, Any?>).apply {
                    cut("checked")
                    (this["points"] as? Map<String, Any?>)?.let { points -> this["points"] = points.mapValues { (_, r) -> runs(r) } }
                    (this["excused"] as? Map<String, Any?>)?.let { excused ->
                        this["excused"] = excused.mapValues { (_, kinds) -> (kinds as Map<String, Any?>).mapValues { (_, r) -> runs(r) } }
                    }
                }
            }
        }
        return root
    }
}
