package world.taqwa.timetables.gate

import java.time.zone.ZoneRulesProvider

/**
 * The tz database the JVM reads every zone from. The gate, the surveys and the monitor read printed
 * times on civil clocks, so a JDK carrying older rules reads some tables an hour off: the cloud
 * runner moved from Temurin 21.0.12 to 21.0.13 (tzdata 2026e) between the 26 Oct and 2 Nov 2026
 * runs and the results changed with it (Manitoba, British Columbia, Alberta and the Northwest
 * Territories keep their summer offsets from 1 Nov 2026 in 2026b–e). Every run that reads a printed
 * time first checks the version is at least [MINIMUM] (tools/timetables/monitor/tzdb.py says the same
 * for the shell before a fetch, and must name the same minimum).
 */
object Tzdb {
    /** The oldest tz database whose rules the held tables and surveys are read on. */
    const val MINIMUM = "2026e"

    /** The JVM's tz database version, as its rules provider names it (`2026e`). */
    fun version(): String = ZoneRulesProvider.getVersions("Europe/London").lastKey()

    /** Whether tz database [version] is [minimum] or later (`2026e` < `2026f` < `2027a`). */
    fun atLeast(version: String, minimum: String = MINIMUM): Boolean = order(version) >= order(minimum)

    /** Why a run must not go on with [version], or null when it may. */
    fun problem(version: String = version()): String? =
        if (atLeast(version)) {
            null
        } else {
            "the JDK's tz database is $version, older than $MINIMUM: printed times would be read on old clock rules " +
                "(Canada's permanent offsets from 1 Nov 2026 among them); run on a JDK whose tzdb.dat is $MINIMUM or later " +
                "(java.home ${System.getProperty("java.home")}), or update it with the tzupdater tool"
        }

    /** Stops the run with [problem]'s reason, through [fail], before anything is read. */
    fun require(fail: (String) -> Nothing) {
        problem()?.let(fail)
    }

    private val shape = Regex("""(\d{4})([a-z]+)""")

    private fun order(version: String): Pair<Int, String> {
        val m = requireNotNull(shape.matchEntire(version.trim())) { "'$version' is not a tz database version (yyyy and letters)" }
        return m.groupValues[1].toInt() to m.groupValues[2]
    }

    private operator fun Pair<Int, String>.compareTo(other: Pair<Int, String>): Int =
        compareValuesBy(this, other, { it.first }, { it.second.length }, { it.second })
}
