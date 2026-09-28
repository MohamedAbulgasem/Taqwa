package world.taqwa.app.feature.settings

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.lateLimitFor
import kotlin.time.Instant

/** The stored timetable that follows the place (spec §2.2). */
const val AUTOMATIC_TIMETABLE = "automatic"

/**
 * Whose time a sentence compares with (spec §2.2's {source}): an authority by name, the cautious
 * time where mosques differ, or Taqwa's own where nothing is known.
 */
enum class SourceKind { AUTHORITY, CAUTIOUS, TAQWA }

fun AboutTemplate.sourceKind(): SourceKind = when (this) {
    AboutTemplate.AUTHORITY_CHECKED, AboutTemplate.AUTHORITY_UNCHECKED -> SourceKind.AUTHORITY
    AboutTemplate.CAUTIOUS -> SourceKind.CAUTIOUS
    AboutTemplate.CALCULATED -> SourceKind.TAQWA
}

/** A timetable by its name key and how it is known, for the sentences that name it. */
data class TimetableName(val nameKey: String, val source: SourceKind)

fun Resolution.timetableName(): TimetableName = TimetableName(entry.shortNameKey, about.sourceKind())

/**
 * What an authority-template entry is, for the words that say where its times come from: an
 * authority's own timetable ("the timetable Diyanet publishes for this place"), the timetable or
 * convention most local mosques follow, with no authority behind it (spec §3.6 and §6.2 b), or a
 * calculation method chosen from Other methods, which publishes no one's times.
 */
enum class TimetableKind {
    AUTHORITY, MAJORITY, METHOD;

    companion object {
        fun of(entry: RegistryEntry): TimetableKind = when {
            // Only a single timetable can be "the one most mosques follow": an entry that became
            // cautious (Chicago did, in Task 7g) is worded by its own template, never as a majority.
            entry.id in MAJORITY_TIMETABLES && entry.method != null && entry.entryClass != EntryClass.C -> MAJORITY
            Registry.otherMethods.any { it.id == entry.id } -> METHOD
            else -> AUTHORITY
        }
    }
}

/**
 * The entries followed because most local mosques follow them: London Unified (37 of 55 sampled
 * London mosques, spec §3.6), and the conventions of the United States, Montreal and Ottawa,
 * Pakistan and India (spec §6.2 b).
 *
 * A list, because nothing else in the registry says it: London Unified is class B like Diyanet
 * and uses the same About template, and ISNA's convention is D_AUTHORITY like the Libyan Awqaf, so
 * neither the class nor the template tells a majority from an authority. It is kept with the words
 * rather than as a registry field, because a field in `registry/Authority.kt` would change the
 * proof stamps' core hash for a change of wording. `TimetableStatusTest` holds every id to a single
 * timetable that is neither cautious nor calculated, so an entry that changes class fails there.
 */
internal val MAJORITY_TIMETABLES: Set<String> = setOf(
    "gb.london.lupt", "us.isna", "ca.isna", "pk.karachi", "in.karachi",
)

/**
 * What Settings › Prayer times says about the timetable here today (spec §2.2; rulings R50, R52),
 * from the stored settings and the engine's day.
 *
 * - [automatic]: what Automatic follows here, for "Automatic · {name}".
 * - [chosenId], [chosenNameKey]: the stored entry, when one is stored (the key null for an id
 *   the registry no longer has).
 * - [outOfScope]: the stored entry does not apply here, so Automatic does, and the screen says so
 *   in one line (ruling R50).
 * - [paused]: the stored entry applies but is not confirmed, so no start is earlier than
 *   Automatic's until the user confirms it through the warning (ruling R52).
 * - [effective]: the timetable the day follows; [effectiveId] its entry.
 * - [saudiFajr]: the "Pray Fajr 5 minutes later" row: Umm al-Qura in Saudi Arabia only.
 * - [highLatitude]: the High latitude rule row: only for an Other method.
 * - [whereDiffer]: "Show where timetables differ": cautious times only.
 * - [school], [schoolKnown]: Automatic's Asr school here and whether the majority's is known.
 * - [automaticKind]: whether Automatic's entry is an authority's timetable or the one most
 *   mosques here follow, for the line under Automatic.
 */
data class TimetableStatus(
    val automatic: TimetableName,
    val chosenId: String?,
    val chosenNameKey: String?,
    val outOfScope: Boolean,
    val paused: Boolean,
    val effective: TimetableName,
    val effectiveId: String,
    val saudiFajr: Boolean,
    val highLatitude: Boolean,
    val whereDiffer: Boolean,
    val school: AsrSchool,
    val schoolKnown: Boolean,
    val automaticKind: TimetableKind = TimetableKind.AUTHORITY,
) {
    /** The selected row on the Timetable screen: the stored entry where it applies, else Automatic. */
    val selectedId: String get() = if (chosenId != null && !outOfScope) chosenId else AUTOMATIC_TIMETABLE

    companion object {
        fun of(settings: PrayerSettings, day: EngineDay): TimetableStatus {
            val stored = settings.timetable.takeIf { it != AUTOMATIC_TIMETABLE }
            val automatic = day.resolution
            val effective = day.effective
            return TimetableStatus(
                automatic = automatic.timetableName(),
                chosenId = stored,
                chosenNameKey = stored?.let { Registry.byId(it)?.shortNameKey },
                // The engine followed Automatic although an entry is stored: unknown here or out of scope.
                outOfScope = stored != null && effective.entry.id != stored && automatic.entry.id != stored,
                paused = day.timetablePaused,
                effective = effective.timetableName(),
                effectiveId = effective.entry.id,
                saudiFajr = effective.entry.id == UMM_AL_QURA && effective.saudi,
                highLatitude = stored != null && Registry.otherMethods.any { it.id == stored },
                whereDiffer = effective.entryClass == EntryClass.C,
                school = automatic.entry.school,
                schoolKnown = automatic.entry.schoolKnown,
                automaticKind = TimetableKind.of(automatic.entry),
            )
        }

        private const val UMM_AL_QURA = "sa.ummalqura"
    }
}

/**
 * Whether a new high-latitude rule must go through the earlier-than check first (review I1): the
 * rule moves Fajr and Isha of an Other method, and a confirmed one applies as it is, so the new
 * rule's times are checked against Automatic's as a new timetable's would be. An unconfirmed
 * timetable is held to Automatic anyway (ruling R52), and no other timetable follows the rule.
 */
fun highLatitudeNeedsCheck(settings: PrayerSettings): Boolean =
    settings.timetableConfirmed && Registry.otherMethods.any { it.id == settings.timetable }

/** Today's own times of a timetable, for its row and Match my mosque's card. */
data class OwnTimes(val fajr: Instant, val dhuhr: Instant, val asr: Instant, val maghrib: Instant, val isha: Instant)

fun PrayerDay.ownTimes(): OwnTimes = OwnTimes(fajr, dhuhr, asr, maghrib, isha)

/** One timetable a list offers: [id] "automatic" or an entry, its name, and today's own times (null while computing). */
data class TimetableOption(val id: String, val nameKey: String, val today: OwnTimes? = null)

/**
 * The timetables a place offers besides Automatic (spec §2.2, ruling R50): the entries its
 * [automatic] entry lists as used nearby that apply at [place], in the registry's order, without
 * Automatic's own.
 */
fun nearbyEntries(automatic: RegistryEntry, place: Place): List<RegistryEntry> =
    automatic.nearby.distinct()
        .mapNotNull { Registry.byId(it) }
        .filter { it.id != automatic.id && Registry.inScope(it, place) }

/**
 * The Timetable screen's rows besides Automatic, by id: [nearbyEntries], then the stored choice
 * [storedId] where it applies at [place] but is not among them (review M8: an entry of the place's
 * own country, or one Automatic follows as a member), so the timetable followed always has a row
 * to be seen selected in. An Other method is shown on the Other methods row instead, and a choice
 * that does not apply here leaves Automatic selected.
 */
fun timetableRowIds(automatic: RegistryEntry, place: Place, storedId: String?): List<String> {
    val nearby = nearbyEntries(automatic, place).map { it.id }
    val stored = storedId
        ?.takeIf { it != AUTOMATIC_TIMETABLE && it != automatic.id && it !in nearby }
        ?.let { Registry.byId(it) }
        ?.takeIf { entry -> Registry.otherMethods.none { it.id == entry.id } && Registry.inScope(entry, place) }
    return nearby + listOfNotNull(stored?.id)
}

/**
 * The adjustment an earlier step may reach before it passes the timetable's own margin (spec §2.2:
 * it asks again, more strongly, past it): the "at most N minutes late" the timetable is held to
 * for [prayer] here (the unit's or entry's exception, else its class's), or null where no figure is
 * claimed (a place not measured, or a time Taqwa calculates with nothing to compare against).
 */
fun marginMinutes(resolution: Resolution, prayer: Prayer): Int? {
    if (!resolution.measured || resolution.entryClass == EntryClass.D_NONE) return null
    val event = when (prayer) {
        Prayer.FAJR -> TimedEvent.FAJR
        Prayer.SUNRISE -> TimedEvent.SUNRISE
        Prayer.DHUHR -> TimedEvent.DHUHR
        Prayer.ASR -> TimedEvent.ASR
        Prayer.MAGHRIB -> TimedEvent.MAGHRIB
        Prayer.ISHA -> TimedEvent.ISHA
    }
    val unit: AuthorityUnit? = resolution.unitName?.let { name ->
        Units.of(resolution.entry.id)?.units?.firstOrNull { it.name == name && it.point == resolution.unitPoint }
    }
    return lateLimitFor(event, unit, resolution.entry)?.minutes ?: classLimit(resolution.entryClass)
}

/** The most minutes late each class allows (spec §1 and §5), as the gate holds them. */
private fun classLimit(entryClass: EntryClass): Int = when (entryClass) {
    EntryClass.A -> 1
    EntryClass.B -> 2
    EntryClass.C -> 1
    EntryClass.D_AUTHORITY, EntryClass.D_NONE -> 3
}
