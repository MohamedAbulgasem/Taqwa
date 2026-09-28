package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Units
import java.util.Locale
import kotlin.time.Instant

/**
 * Never-early margins fitted on an entry's `split=fit` rows (the plan's Conventions), and the
 * held-out table they give on its `split=test` rows.
 *
 * For each official time `O` the fitter finds the engine's own `x = r + authorityMinutes·60` (the
 * raw model plus the authority's offsets, before our margin) to the second, by moving only that
 * event's margin through a minute and watching where the shown minute steps: the engine is the
 * model, so nothing is recomputed beside it. Then
 *
 * - a start's margin is `max(O − 60 − x) + 1 + safety`,
 * - an end's margin is `min(O + 60 − x) − 1 − safety`,
 *
 * over every fit day where the time follows its margin. Days where it does not (a fixed Dhuhr,
 * DUM RT's white nights, an end of eating held at Fajr) and days whose time the high-latitude rule
 * set are left out and counted. Both Asr columns share the Asr margin (the larger is used), the
 * end of eating and imsak the end-of-eating margin (the smaller). A cautious entry has no margins
 * of its own: fit its members' entries instead.
 *
 * `fit("entry/unit")` fits one authority unit (a JAKIM zone, a Diyanet district, a Habous city, a
 * Brunei district): only the rows whose point resolves to that unit, and the holdout applies the
 * margins to that unit's own method alone.
 */
class Fitter(private val roots: OfficialRoots, private val manifest: GateManifest, private val safety: Int = SAFETY) {

    /** [target] is an entry id, or `entry/unit` for one of its units. */
    fun fit(target: String, split: Split = Split.FIT): FitResult {
        val problems = mutableListOf<String>()
        val (entryId, unitId) = target.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
        if (unitId != null && Units.of(entryId)?.units?.none { it.id == unitId } != false) {
            throw GateError(listOf("$entryId has no unit '$unitId'"))
        }
        /** The row's setup when it belongs to the target (its entry, and its unit when one is named). */
        fun ofTarget(row: GateRow, into: MutableList<String>): Gate.Setup? {
            if (row.entry != entryId) return null
            val setup = Gate.setUp(row, roots, null, into) ?: return null
            return setup.takeIf { unitId == null || it.unit?.id == unitId }
        }
        val fits = sortedMapOf<Event, EventFit>()
        var current: TimetableMethod? = null
        var held = 0
        for (row in manifest.rows.filter { it.split == split }) {
            val setup = ofTarget(row, problems) ?: continue
            val table = setup.table ?: continue
            held++
            val method = setup.resolution.method
            if (method == null || setup.resolution.entryClass == EntryClass.C) {
                problems += "${row.where}: ${setup.entry.id} is cautious; fit its members' entries"
                continue
            }
            current = current ?: method
            val probe = Probe(setup, method)
            for (official in table.days) {
                row.columns.forEachIndexed { column, events ->
                    val minutes = official.minutes[column] ?: return@forEachIndexed
                    for (event in events) {
                        val fit = fits.getOrPut(event) { EventFit(event) }
                        val at = Gate.officialInstant(official.date, minutes, event, setup.clock, setup.zone)
                        fit.add(official.date, at, probe)
                    }
                }
            }
        }
        if (problems.isNotEmpty()) throw GateError(problems)
        val margins = fits.values.mapNotNull { f -> f.margin(safety)?.let { f.event to it } }.toMap()
        val holdout = if (current == null) {
            null
        } else {
            val tested = manifest.rows.filter { it.split != split && ofTarget(it, mutableListOf()) != null }
            Gate(roots, change = { method -> withMargins(method, margins) }).evaluate(GateManifest(tested))
        }
        return FitResult(target, split, held, safety, fits, margins, current, holdout)
    }

    /** The margins one event's fit days ask for. */
    class EventFit(val event: Event) {
        var days = 0
            private set
        var unresponsive = 0
            private set
        var setByRule = 0
            private set
        /** For a start the largest `O − 60 − x`, for an end the smallest `O + 60 − x`, and its date. */
        var bound: Long? = null
            private set
        var decidedBy: LocalDate? = null
            private set

        internal fun add(date: LocalDate, official: Instant, probe: Probe) {
            days++
            when (val x = probe.x(date, event)) {
                Probe.Answer.SetByRule -> setByRule++
                Probe.Answer.Unresponsive -> unresponsive++
                is Probe.Answer.At -> {
                    val value = if (event.isStart) official.epochSeconds - 60 - x.seconds else official.epochSeconds + 60 - x.seconds
                    val better = bound?.let { if (event.isStart) value > it else value < it } ?: true
                    if (better) {
                        bound = value
                        decidedBy = date
                    }
                }
            }
        }

        /** The Conventions' margin in seconds, or null when no day followed its margin. */
        fun margin(safety: Int): Int? = bound?.let { if (event.isStart) it + 1 + safety else it - 1 - safety }?.toInt()
    }

    /**
     * Finds `x` for one event of one day by moving its margin: the shown start is
     * `⌈(x + k) / 60⌉·60`, which steps up at the smallest `k > S₀ − x`; an end `⌊(x + k) / 60⌋·60`
     * steps down at the largest `k < E₀ − x`. Bisection over the minute; the bound kept is the
     * safe side of the second found (the smaller `x` for a start, the larger for an end).
     */
    internal class Probe(private val setup: Gate.Setup, private val method: TimetableMethod) {
        sealed interface Answer {
            data object SetByRule : Answer
            data object Unresponsive : Answer
            data class At(val seconds: Long) : Answer
        }

        private fun day(date: LocalDate, event: Event, margin: Int): PrayerDay =
            DayPipeline.unended(setup.resolution.copy(method = withMargin(method, event, margin)), date, setup.zone)

        private fun shown(date: LocalDate, event: Event, margin: Int): Instant? =
            Gate.shown(day(date, event, margin), event, setup.entry.school)

        fun x(date: LocalDate, event: Event): Answer {
            val day = day(date, event, 0)
            if (event.prayer?.let { it in day.setByRule } == true) return Answer.SetByRule
            // A cell the day declares not followed (ruling R82) says nothing of its margin: counted with the rule's.
            if (with(Gate) { event.declarable }?.let { it in day.notFollowed } == true) return Answer.SetByRule
            val s0 = Gate.shown(day, event, setup.entry.school) ?: return Answer.Unresponsive
            return if (event.isStart) {
                if (shown(date, event, 60)?.minus(s0)?.inWholeSeconds != 60L) return Answer.Unresponsive
                var lo = 0 // not stepped
                var hi = 60 // stepped
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if (shown(date, event, mid)!! > s0) hi = mid else lo = mid
                }
                Answer.At(s0.epochSeconds - hi)
            } else {
                if (shown(date, event, -60)?.let { s0 - it }?.inWholeSeconds != 60L) return Answer.Unresponsive
                var lo = -60 // stepped
                var hi = 0 // not stepped
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if (shown(date, event, mid)!! < s0) lo = mid else hi = mid
                }
                Answer.At(s0.epochSeconds - lo)
            }
        }
    }

    companion object {
        const val SAFETY = 5

        /** [method] with the one margin [event] follows set to [seconds]. */
        fun withMargin(method: TimetableMethod, event: Event, seconds: Int): TimetableMethod {
            val m = method.margins
            return when (event) {
                Event.FAJR -> method.copy(margins = m.copy(fajr = seconds))
                Event.SUNRISE -> method.copy(margins = m.copy(sunrise = seconds))
                Event.DHUHR -> method.copy(margins = m.copy(dhuhr = seconds))
                Event.ASR_STANDARD, Event.ASR_HANAFI -> method.copy(margins = m.copy(asr = seconds))
                Event.MAGHRIB -> method.copy(margins = m.copy(maghrib = seconds))
                Event.ISHA -> method.copy(margins = m.copy(isha = seconds))
                Event.END_OF_EATING, Event.IMSAK -> method.copy(endOfEatingMarginSeconds = seconds)
            }
        }

        /** The margin [event] follows in [method]. */
        fun marginOf(method: TimetableMethod, event: Event): Int = when (event) {
            Event.FAJR -> method.margins.fajr
            Event.SUNRISE -> method.margins.sunrise
            Event.DHUHR -> method.margins.dhuhr
            Event.ASR_STANDARD, Event.ASR_HANAFI -> method.margins.asr
            Event.MAGHRIB -> method.margins.maghrib
            Event.ISHA -> method.margins.isha
            Event.END_OF_EATING, Event.IMSAK -> method.endOfEatingMarginSeconds
        }

        /**
         * [method] with every fitted margin: where two events share one (the two Asr schools, the
         * end of eating and imsak), the later start or the earlier end.
         */
        fun withMargins(method: TimetableMethod, margins: Map<Event, Int>): TimetableMethod {
            fun pick(events: List<Event>, start: Boolean): Int? =
                events.mapNotNull { margins[it] }.let { if (it.isEmpty()) null else if (start) it.max() else it.min() }
            val m = method.margins
            return method.copy(
                margins = EventOffsets(
                    fajr = margins[Event.FAJR] ?: m.fajr,
                    sunrise = margins[Event.SUNRISE] ?: m.sunrise,
                    dhuhr = margins[Event.DHUHR] ?: m.dhuhr,
                    asr = pick(listOf(Event.ASR_STANDARD, Event.ASR_HANAFI), start = true) ?: m.asr,
                    maghrib = margins[Event.MAGHRIB] ?: m.maghrib,
                    isha = margins[Event.ISHA] ?: m.isha,
                ),
                endOfEatingMarginSeconds = pick(listOf(Event.END_OF_EATING, Event.IMSAK), start = false) ?: method.endOfEatingMarginSeconds,
            )
        }
    }
}

/** What [Fitter.fit] found, and the held-out table the fitted margins give. */
class FitResult(
    val entryId: String,
    val split: Split,
    val rows: Int,
    val safety: Int,
    val events: Map<Event, Fitter.EventFit>,
    val margins: Map<Event, Int>,
    private val current: TimetableMethod?,
    val holdout: GateResult?,
) {
    fun report(): String = buildString {
        append("Fit ${entryId} on $rows ${split.name.lowercase()} rows, safety ${safety} s (margins in seconds)\n")
        append(String.format(Locale.ROOT, "  %-12s %6s %8s %8s %7s %7s  %s\n", "event", "days", "no-step", "by-rule", "fitted", "now", "decided by"))
        for ((event, f) in events) {
            val fitted = margins[event]?.let { String.format(Locale.ROOT, "%+d", it) } ?: "-"
            val now = current?.let { String.format(Locale.ROOT, "%+d", Fitter.marginOf(it, event)) } ?: "-"
            append(String.format(Locale.ROOT, "  %-12s %6d %8d %8d %7s %7s  %s\n", event.key, f.days, f.unresponsive, f.setByRule, fitted, now, f.decidedBy ?: "-"))
        }
        append('\n')
        if (holdout == null || holdout.checkedRows == 0) {
            append("Holdout: no held-out rows held locally for $entryId\n")
        } else {
            append("Holdout with the fitted margins:\n")
            append(holdout.report())
        }
    }
}

/** The prayer whose high-latitude flag marks [this] as set by rule. */
private val Event.prayer: Prayer?
    get() = when (this) {
        Event.FAJR, Event.END_OF_EATING, Event.IMSAK -> Prayer.FAJR
        Event.ISHA -> Prayer.ISHA
        else -> null
    }
