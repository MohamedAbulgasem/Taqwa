package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod

/**
 * How sure Taqwa is of an entry's times (spec §5 and §6.1). A and B reproduce an authority's
 * published method, C combines the timetables local mosques follow, D_AUTHORITY is an authority's
 * method with safety minutes before a full proof, D_NONE is calculated by Taqwa where nothing is
 * known. The gate (Task 6 and 7) moves entries between classes.
 */
enum class EntryClass { A, B, C, D_AUTHORITY, D_NONE }

/** Which About screen an entry fills (spec §2.3). */
enum class AboutTemplate { AUTHORITY_CHECKED, AUTHORITY_UNCHECKED, CAUTIOUS, CALCULATED }

/**
 * Where a chosen entry applies (spec §2.2), as [Registry.inScope] reads it (ruling R50): [GLOBAL]
 * anywhere for the old picker's Other methods (`other.*`), and for any other entry declared global
 * as for [COUNTRY]: in [RegistryEntry.countries] and wherever Automatic resolves to it or lists it;
 * [CITY] and [UNIT] only where Automatic resolves to it or lists it (a metro area, a province, "the
 * rest of" a country).
 */
enum class Scope { UNIT, CITY, COUNTRY, GLOBAL }

/**
 * A point to resolve: the stored location (spec §3.5), its IANA zone, its ISO 3166-1 alpha-2
 * country code and, when known, its first-level region ([admin1], as the app's city list names it;
 * Task 8 fills it from the nearest city). Where an authority's units are its regions the region
 * decides, the nearest unit only when it is not known (ruling R32).
 */
data class Place(val lat: Double, val lon: Double, val zoneId: String, val countryCode: String, val admin1: String? = null)

/**
 * [name] folded for matching region names across spellings: lower case, the accents of the Latin
 * letters dropped, and nothing but the letters a–z kept ("Béjaïa", "Bejaia" and "BÉJAÏA" agree).
 */
internal fun regionKey(name: String): String = buildString {
    for (c in name.lowercase()) {
        val plain = when (c) {
            'à', 'á', 'â', 'ã', 'ä', 'å', 'ā' -> 'a'
            'ç' -> 'c'
            'è', 'é', 'ê', 'ë', 'ē' -> 'e'
            'ì', 'í', 'î', 'ï', 'ī' -> 'i'
            'ñ' -> 'n'
            'ò', 'ó', 'ô', 'õ', 'ö', 'ō' -> 'o'
            'ù', 'ú', 'û', 'ü', 'ū' -> 'u'
            'ý', 'ÿ' -> 'y'
            else -> c
        }
        if (plain in 'a'..'z') append(plain)
    }
}

/**
 * One timetable combined in a cautious entry, [shareRank] 1 for the most-followed (spec §3.6). A
 * convention (not an entry of its own, so no units) is measured only near the points of the tables
 * that prove it, [measuredAt] (review I3); an entry member is measured where its own entry is.
 */
data class Member(
    val id: String,
    val nameKey: String,
    val method: TimetableMethod,
    val shareRank: Int,
    val measuredAt: List<GeoPoint> = emptyList(),
)

/**
 * One timetable Taqwa can follow.
 *
 * - [id]: a stable lowercase dotted key, country first ("sa.ummalqura", "gb.london.lupt"); Other
 *   methods are "other.*".
 * - [shortNameKey]: the string resource of its short name (≤ 20 characters once translated); the
 *   Other methods reuse the old picker's `method_*` names.
 * - [method]: the method as published; null for a cautious entry, which has [members] instead,
 *   most-followed first.
 * - [school], [schoolKnown]: the Asr school that leads; when the local majority's school is not
 *   known the later one (Hanafi) leads (spec §3.7).
 * - [nearby]: ids of the named timetables Settings › Timetable lists near its places.
 * - [countries]: where a [Scope.COUNTRY] entry applies; for other scopes, the countries it is
 *   resolved in, for reference.
 * - [measured]: for an entry without units, whether official days exist for its whole area, so an
 *   "at most" figure can be claimed; an entry with units takes it from the unit (see [Units]).
 * - [lateLimits]: its exceptions to the late limit, each for its own events (see [lateLimitFor]).
 */
data class RegistryEntry(
    val id: String,
    val shortNameKey: String,
    val entryClass: EntryClass,
    val about: AboutTemplate,
    val method: TimetableMethod? = null,
    val members: List<Member> = emptyList(),
    val school: AsrSchool,
    val schoolKnown: Boolean,
    val nearby: List<String> = emptyList(),
    val scope: Scope,
    val countries: Set<String> = emptySet(),
    val measured: Boolean = false,
    val lateLimits: List<LateLimit> = emptyList(),
) {
    init {
        require((method == null) == (entryClass == EntryClass.C)) { "$id: a method, or members when cautious" }
        requireEachEventOnce(lateLimits, id)
        require(entryClass != EntryClass.C || members.size >= 2) { "$id: cautious needs two members or more" }
    }

    /** This entry as a cautious member of rank [shareRank]. */
    fun asMember(shareRank: Int): Member =
        Member(id, shortNameKey, requireNotNull(method) { "$id has no single method" }, shareRank)
}

/** A time the app shows, as far as a late limit is concerned (ruling R41). */
enum class TimedEvent { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA, END_OF_EATING, IMSAK }

/**
 * An exception to the late limit (rulings R37, R41): here the [events] shown may run up to [minutes]
 * after the authority's own, for [reason] (a recorded exception, spec §6.3); every other event keeps
 * the class limit. It lives with its entry or unit so that the gate proves, and the About page
 * states, the same number for the same events.
 */
data class LateLimit(val minutes: Int, val reason: String, val events: Set<TimedEvent>) {
    init {
        require(minutes > 0) { "a late limit of $minutes min" }
        require(reason.isNotBlank()) { "a late limit needs its reason" }
        require(events.isNotEmpty()) { "a late limit names the events it covers" }
    }
}

/**
 * The late-limit exception for [event] at a place: the [unit]'s own that covers it, else the
 * [entry]'s, else null (the class limit applies).
 */
fun lateLimitFor(event: TimedEvent, unit: AuthorityUnit?, entry: RegistryEntry): LateLimit? =
    unit?.lateLimits?.firstOrNull { event in it.events } ?: entry.lateLimits.firstOrNull { event in it.events }

/** One owner's limits cover each event at most once, so the limit for an event is never ambiguous. */
internal fun requireEachEventOnce(limits: List<LateLimit>, owner: String) {
    val events = limits.flatMap { it.events }
    require(events.size == events.toSet().size) { "$owner: an event with two late limits" }
}

/**
 * What the registry resolved a place to.
 *
 * - [point]: the user's own point, the one to pass to the day computer with [method]. A unit's
 *   reference point travels as [method]'s fixed point, so the engine evaluates both (ruling R15,
 *   spec §3.5): starts the later of the two, sunrise and the ends the earlier.
 * - [unitName], [unitPoint], [unitId]: the authority's unit ("WLY01", "İstanbul", "Casablanca"),
 *   its reference point and its id (the key of its row in the proof stamps), null outside every
 *   unit.
 * - [shiaRegion]: in the Shia-regions list (the Sunni-times card, spec §2.1); [saudi]: Saudi Arabia
 *   (the Fajr switch, spec §2.2).
 * - [measured]: false where no "at most" figure may be claimed (a unit not in the proof).
 * - [method]: the method to compute with here, the unit's own where it has one; null when cautious.
 * - [members]: a cautious entry's members with their methods as they apply here (a member whose
 *   Fajr follows a night fraction carries this latitude's curve); empty otherwise.
 * - [entryClass], [about]: the class at this place. Outside an authority's checked units an A or B
 *   entry is D_AUTHORITY here (spec §6.2 a).
 * - [unitLabel]: what About and the site call the place: the unit's name, or null where the unit is
 *   the user's own city ([AuthorityUnit.named] false) or there is none, for the app's own name of
 *   the city in the reader's language.
 */
data class Resolution(
    val entry: RegistryEntry,
    val point: GeoPoint,
    val unitName: String?,
    val shiaRegion: Boolean,
    val saudi: Boolean,
    val measured: Boolean,
    val method: TimetableMethod? = entry.method,
    val members: List<Member> = entry.members,
    val entryClass: EntryClass = entry.entryClass,
    val about: AboutTemplate = entry.about,
    val unitPoint: GeoPoint? = null,
    val unitId: String? = null,
    val unitLabel: String? = unitName,
)

/** The About template each class fills. */
internal fun aboutFor(entryClass: EntryClass): AboutTemplate = when (entryClass) {
    EntryClass.A, EntryClass.B -> AboutTemplate.AUTHORITY_CHECKED
    EntryClass.C -> AboutTemplate.CAUTIOUS
    EntryClass.D_AUTHORITY -> AboutTemplate.AUTHORITY_UNCHECKED
    EntryClass.D_NONE -> AboutTemplate.CALCULATED
}

/** An entry that follows one method. */
internal fun single(
    id: String,
    nameKey: String,
    entryClass: EntryClass,
    method: TimetableMethod,
    school: AsrSchool,
    schoolKnown: Boolean = true,
    scope: Scope = Scope.COUNTRY,
    countries: Set<String> = emptySet(),
    measured: Boolean = false,
    nearby: List<String> = emptyList(),
    lateLimits: List<LateLimit> = emptyList(),
): RegistryEntry = RegistryEntry(
    id = id, shortNameKey = nameKey, entryClass = entryClass, about = aboutFor(entryClass), method = method,
    school = school, schoolKnown = schoolKnown, nearby = nearby, scope = scope, countries = countries,
    measured = measured, lateLimits = lateLimits,
)

/** A cautious entry over [members] (most-followed first); the named ones are listed as nearby. */
internal fun cautious(
    id: String,
    members: List<Member>,
    school: AsrSchool,
    schoolKnown: Boolean,
    scope: Scope,
    countries: Set<String>,
    measured: Boolean,
    named: (String) -> Boolean,
    lateLimits: List<LateLimit> = emptyList(),
): RegistryEntry = RegistryEntry(
    id = id, shortNameKey = CAUTIOUS_NAME, entryClass = EntryClass.C, about = AboutTemplate.CAUTIOUS,
    members = members, school = school, schoolKnown = schoolKnown, nearby = members.map { it.id }.filter(named),
    scope = scope, countries = countries, measured = measured, lateLimits = lateLimits,
)

/**
 * A convention member with no name of its own (not an entry, so not in Settings' lists), measured only
 * within class C's reach of the tables that prove it, [measuredAt] (review I3).
 */
internal fun convention(id: String, nameKey: String, method: TimetableMethod, shareRank: Int, measuredAt: List<GeoPoint> = emptyList()) =
    Member(id, nameKey, method, shareRank, measuredAt)

/** The short names of the two non-authority templates (strings added in Task 13). */
internal const val CAUTIOUS_NAME = "timetable_cautious"
internal const val CALCULATED_NAME = "timetable_calculated"

/**
 * Margins in seconds, our convention (see the plan's Conventions): starts ⌈raw + offset⌉, sunrise
 * ⌊raw + offset⌋. [start] fills every start not given.
 */
internal fun margins(
    start: Int,
    sunrise: Int,
    fajr: Int = start,
    dhuhr: Int = start,
    asr: Int = start,
    maghrib: Int = start,
    isha: Int = start,
) = EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha)

/** [this] with [starts] seconds added to every start and [sunrise] to sunrise. */
internal fun EventOffsets.widened(starts: Int, sunrise: Int = -starts) = EventOffsets(
    fajr = fajr + starts, sunrise = this.sunrise + sunrise, dhuhr = dhuhr + starts, asr = asr + starts,
    maghrib = maghrib + starts, isha = isha + starts,
)

/**
 * [this] where the authority's own point is not known (a unit's edge, spec §3.5), with the edge's
 * [margins]: the end of eating moves as far as sunrise does, since the point moves both ends alike,
 * and a margin fitted at a table's point applies there only, so it is at most [SAFE_END] (ruling R44).
 */
internal fun TimetableMethod.atEdge(id: String, margins: EventOffsets) = copy(
    id = id,
    margins = margins,
    endOfEatingMarginSeconds = minOf(endOfEatingMarginSeconds + (margins.sunrise - this.margins.sunrise), SAFE_END),
)

/**
 * The first-guess safety on a method known only as angles and minutes (controller ruling for
 * Task 5; Task 7 refits every margin): starts 30 s later, sunrise 30 s earlier than the authority's
 * own rounding would give.
 */
internal const val SAFE_START = 30
internal const val SAFE_SUNRISE = -30

/** The same first-guess safety on the end of eating, an end like sunrise (ruling R31). */
internal const val SAFE_END = SAFE_SUNRISE

/**
 * Research margins fitted on adhan2 0.0.7's raw Asr, whose shadow uses the declination at 0h UT,
 * run up to 34 s later than the exact Asr this core computes (profiles-tested.md, data-maghreb-libya:
 * adhan2 minus first-principles Asr is −37..+34 s); such margins are raised by this much.
 */
internal const val ADHAN2_ASR_ALLOWANCE = 35
