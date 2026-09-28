package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Conventions' margins fitted on the open MUIS fixture's five fit days, and per unit on DUM RT's. */
class FitterTest {

    private val root = OfficialRoots.of(File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI()))

    private val manifest = GateManifest.parse(
        "fixture.tsv",
        listOf(
            GateManifest.HEADER.joinToString("\t"),
            "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t",
            "open/SG-MUIS/muis-2026-02-b.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\t",
        ).joinToString("\n"),
    )

    private val fitRows = GateManifest(manifest.rows.filter { it.split == Split.FIT }, emptyList())

    @Test
    fun `fitted margins keep every fit day never early and the holdout is reported`() {
        val fit = Fitter(root, manifest).fit("sg.muis")
        assertEquals(1, fit.rows)
        assertEquals(
            setOf(Event.FAJR, Event.SUNRISE, Event.DHUHR, Event.ASR_STANDARD, Event.MAGHRIB, Event.ISHA, Event.END_OF_EATING),
            fit.margins.keys,
        )
        for ((event, f) in fit.events) {
            assertEquals(5, f.days, event.key)
            assertEquals(0, f.unresponsive + f.setByRule, event.key)
        }
        val onFitDays = Gate(root, change = { Fitter.withMargins(it, fit.margins) }).evaluate(fitRows).entries.getValue("sg.muis")
        for ((event, e) in onFitDays.events) {
            assertEquals(0, e.early, event.key)
            assertEquals(0, e.lateEnd, event.key)
        }
        val holdout = assertNotNull(fit.holdout)
        assertEquals(1, holdout.checkedRows)
        assertEquals(5, holdout.entries.getValue("sg.muis").placeDayCount)
        val report = fit.report()
        assertTrue("Fit sg.muis on 1 fit rows, safety 5 s" in report, report)
        assertTrue("Holdout with the fitted margins" in report, report)
    }

    /**
     * The margin is as small as the formula allows: two seconds less than the safety below it, the
     * day that decided it is a minute early.
     */
    @Test
    fun `a fajr margin below the fitted one less its safety is early on the deciding day`() {
        val fit = Fitter(root, manifest).fit("sg.muis")
        val margin = fit.margins.getValue(Event.FAJR) - (Fitter.SAFETY + 2)
        val result = Gate(root, change = { it.copy(margins = it.margins.copy(fajr = margin)) }).evaluate(fitRows)
        val fajr = result.entries.getValue("sg.muis").event(Event.FAJR)
        assertTrue(fajr.early >= 1, result.report())
        assertTrue(fajr.broken.any { it.startsWith(fit.events.getValue(Event.FAJR).decidedBy.toString()) }, fajr.broken.toString())
    }

    /** The same for an end: two seconds more than the safety above the fitted sunrise margin is a late end. */
    @Test
    fun `a sunrise margin above the fitted one plus its safety is a late end on the deciding day`() {
        val fit = Fitter(root, manifest).fit("sg.muis")
        val margin = fit.margins.getValue(Event.SUNRISE) + (Fitter.SAFETY + 2)
        val result = Gate(root, change = { it.copy(margins = it.margins.copy(sunrise = margin)) }).evaluate(fitRows)
        val sunrise = result.entries.getValue("sg.muis").event(Event.SUNRISE)
        assertTrue(sunrise.lateEnd >= 1, result.report())
        assertTrue(sunrise.broken.any { it.startsWith(fit.events.getValue(Event.SUNRISE).decidedBy.toString()) }, sunrise.broken.toString())
        val atFitted = Gate(root, change = { it.copy(margins = it.margins.copy(sunrise = fit.margins.getValue(Event.SUNRISE))) }).evaluate(fitRows)
        assertEquals(0, atFitted.entries.getValue("sg.muis").event(Event.SUNRISE).lateEnd)
    }

    /**
     * One unit's fit: ten days of DUM RT's open Kazan table (CC BY 4.0), the first five to fit and
     * the rest held out, listed for Kazan and, as if it were theirs, for Zelenodolsk.
     */
    @Test
    fun `a unit is fitted on its own rows and held out on its own rows`() {
        val archive = Files.createTempDirectory("gate-unit").toFile()
        try {
            val kazan = TestPaths.repoRoot.resolve("tools/timetables/official/open/RU-DUMRT/off_kzn.txt").readLines()
            archive.resolve("archive").mkdirs()
            archive.resolve("archive/kzn-a.txt").writeText(kazan.take(5).joinToString("\n"))
            archive.resolve("archive/kzn-b.txt").writeText(kazan.drop(5).take(5).joinToString("\n"))
            val rows = listOf("kazan", "zelenodolsk").flatMap { unit ->
                listOf("kzn-a.txt" to "fit", "kzn-b.txt" to "test").map { (file, split) ->
                    "archive/$file\tru.dumrt/$unit\t\t\tEurope/Moscow\tF+E S - A M I\tdaily\thanafi\t$split\t"
                }
            }
            val units = GateManifest.parse("u.tsv", (listOf(GateManifest.HEADER.joinToString("\t")) + rows).joinToString("\n"))
            val roots = OfficialRoots(archive, root.checkout)

            val one = Fitter(roots, units).fit("ru.dumrt/kazan")
            assertEquals(1, one.rows)
            assertTrue(one.events.values.all { it.days == 5 }, one.report())
            val holdout = assertNotNull(one.holdout)
            assertEquals(listOf("u.tsv:3"), holdout.entries.getValue("ru.dumrt").rows)
            assertTrue("Fit ru.dumrt/kazan on 1 fit rows" in one.report(), one.report())

            val all = Fitter(roots, units).fit("ru.dumrt")
            assertEquals(2, all.rows)
            assertTrue(all.events.values.all { it.days == 10 }, all.report())

            val error = assertFailsWith<GateError> { Fitter(roots, units).fit("ru.dumrt/nowhere") }
            assertTrue("ru.dumrt has no unit 'nowhere'" in error.message!!, error.message)
        } finally {
            archive.deleteRecursively()
        }
    }

    @Test
    fun `shared margins take the later start and the earlier end`() {
        val method = Registry.byId("sg.muis")!!.method!!
        val m = Fitter.withMargins(method, mapOf(Event.ASR_STANDARD to 10, Event.ASR_HANAFI to 40, Event.END_OF_EATING to 20, Event.IMSAK to -5))
        assertEquals(40, m.margins.asr)
        assertEquals(-5, m.endOfEatingMarginSeconds)
        assertEquals(method.margins.fajr, m.margins.fajr)
    }
}
