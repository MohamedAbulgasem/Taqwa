package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An imsak counted back from the printed Fajr ([ImsakFrom.PrintedFajr]) is never after the Fajr shown less its minutes:
 * read at the same points and rounded up as the Fajr is, it stays there while its own margin is no later than the
 * Fajr's (review of the JAKIM imsak round, 12 Nov 2026). Held here for every method the registry ships, whichever
 * authority takes the rule up, not in [TimetableMethod]'s own checks: the fitter probes that margin on copies of a
 * method apart from the Fajr's (`Fitter.withMargin`).
 */
class PrintedFajrImsakTest {
    private fun TimetableMethod.withAlongside(): List<TimetableMethod> = listOf(this) + alongside.flatMap { it.method.withAlongside() }

    @Test
    fun `every printed-fajr imsak the registry ships keeps a margin no later than its fajr's`() {
        val methods = Registry.entries.flatMap { entry ->
            listOfNotNull(entry.method) + entry.members.map { it.method } +
                Units.of(entry.id)?.units.orEmpty().mapNotNull { it.method }
        }.flatMap { it.withAlongside() }
        val printed = methods.filter { it.imsakFrom is ImsakFrom.PrintedFajr }
        // JAKIM's twelve zones that print a precaution take the rule up today; the check reads at least them.
        assertTrue(printed.size >= 12, "${printed.size} methods count their imsak back from the printed Fajr")
        for (method in printed) {
            val margin = (method.imsakFrom as ImsakFrom.PrintedFajr).marginSeconds
            assertTrue(method.imsakMinutesBeforeFajr != null && method.imsakMinutesBeforeFajr >= 0, "${method.id}: imsak minutes")
            assertTrue(margin <= method.margins.fajr, "${method.id}: imsak margin $margin s after its Fajr's ${method.margins.fajr} s")
        }
    }
}
