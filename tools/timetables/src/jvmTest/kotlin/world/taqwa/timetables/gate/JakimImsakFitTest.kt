package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The imsak counted back from the printed Fajr ([ImsakFrom.PrintedFajr], the JAKIM imsak round of 12 Nov 2026) has a
 * margin of its own in the [Fitter], fitted as an end: on every printed Fajr less the method's minutes, the rule's own
 * claim, and on a printed imsak read beside it. Each JAKIM zone's margin (`JakimZones.kt`) is what the fitter gives over
 * the zone's fit and test rows in my-jakim.tsv, the smaller, and a zone read on one year alone carries
 * `JakimZones.FORWARD_SECONDS` less at its unit (the review round of 13 Nov 2026). Without the archive (CI) that half
 * reads nothing and says so.
 */
class JakimImsakFitTest {

    private val printed = TimetableMethod(
        id = "test.printed", fajrAngle = 18.0, isha = IshaRule.Angle(18.0), endOfEatingMarginSeconds = -20,
        imsakMinutesBeforeFajr = 10, imsakFrom = ImsakFrom.PrintedFajr(0),
    )

    @Test
    fun `a printed fajr imsak keeps its own margin apart from the end of eating's`() {
        val one = Fitter.withMargin(printed, Event.IMSAK, 37)
        assertEquals(ImsakFrom.PrintedFajr(37), one.imsakFrom)
        assertEquals(-20, one.endOfEatingMarginSeconds)
        assertEquals(37, Fitter.marginOf(one, Event.IMSAK))
        assertEquals(-20, Fitter.marginOf(one, Event.END_OF_EATING))
        val both = Fitter.withMargins(printed, mapOf(Event.END_OF_EATING to -45, Event.IMSAK to 30))
        assertEquals(-45, both.endOfEatingMarginSeconds)
        assertEquals(ImsakFrom.PrintedFajr(30), both.imsakFrom)
        // A dawn imsak still shares the end of eating's margin, the earlier of the two.
        val dawn = Fitter.withMargins(printed.copy(imsakFrom = ImsakFrom.Dawn), mapOf(Event.END_OF_EATING to -45, Event.IMSAK to 30))
        assertEquals(-45, dawn.endOfEatingMarginSeconds)
        assertEquals(ImsakFrom.Dawn, dawn.imsakFrom)
    }

    @Test
    fun `each jakim zone's imsak margin is the fitter's over its fit and test rows`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        if (!official.resolve("archive").isDirectory) {
            println("Jakim imsak fit skipped: no archive under $official")
            return
        }
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).only(setOf("my-jakim"))
        val fitter = Fitter(roots, manifest)
        val off = mutableListOf<String>()
        for (unit in Units.of("my.jakim")!!.units) {
            val from = unit.method?.imsakFrom as? ImsakFrom.PrintedFajr ?: continue
            val fitted = listOf(Split.FIT, Split.TEST).mapNotNull { fitter.fit("my.jakim/${unit.id}", it).margins[Event.IMSAK] }
            assertEquals(2, fitted.size, unit.id)
            assertIs<ImsakFrom.PrintedFajr>(unit.method?.imsakFrom)
            val zone = JakimZones.zones.single { it.id == unit.id }
            val fit = assertNotNull(zone.imsakMarginSeconds, unit.id)
            if (fitted.min() != fit) off += "${unit.id}: the fitter gives ${fitted.min()}, the zone carries $fit"
            val forward = if (zone.oneYear) JakimZones.FORWARD_SECONDS else 0
            assertEquals(fit - forward, from.marginSeconds, unit.id)
        }
        assertEquals(emptyList(), off)
    }
}
