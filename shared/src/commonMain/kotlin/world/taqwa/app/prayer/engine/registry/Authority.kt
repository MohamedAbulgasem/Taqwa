package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.data.ReachLimits

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
 * as for [COUNTRY]: in [RegistryEntry.countries] (within its [RegistryEntry.area] there, where it
 * has one) and wherever Automatic resolves to it or lists it; [CITY] and [UNIT] only where
 * Automatic resolves to it or lists it (a metro area, a province, "the rest of" a country).
 */
enum class Scope { UNIT, CITY, COUNTRY, GLOBAL }

/**
 * A point to resolve: the stored location (spec §3.5), its IANA zone, its ISO 3166-1 alpha-2
 * country code and, when known, its first-level region ([admin1], as the app's city list names it;
 * Task 8 fills it from the nearest city). Where an authority's units are its regions the region
 * decides, the nearest unit only when it is not known (ruling R32).
 *
 * [alsoCountryCode], with its region [alsoAdmin1]: a second country the place may be in (ruling R259, the second trust
 * audit's G4 and G5). A GPS fix carries no country; the app takes it from the nearest bundled city, and where the
 * device's time zone belongs to another country than that city's, the place may stand on either side of a border.
 * Automatic then holds the day to both countries' entries ([Registry.resolve]): each start the later, each end the
 * earlier. Null everywhere else: a city picked by hand, a fix whose zone agrees with its nearest city, the site.
 */
data class Place(
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val countryCode: String,
    val admin1: String? = null,
    val alsoCountryCode: String? = null,
    val alsoAdmin1: String? = null,
)

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
 * - [area]: where in its [countries] a chosen [Scope.COUNTRY] or [Scope.GLOBAL] entry applies, when
 *   not the whole of them (London Unified: within R45's three reaches of its table's point, the
 *   owner's decision of 17 Oct 2026); null for the whole. Where Automatic follows the entry or lists
 *   it, it applies whatever its area.
 * - [measured]: for an entry without units, whether official days exist for its whole area, so an
 *   "at most" figure can be claimed; an entry with units takes it from the unit (see [Units]).
 * - [lateLimits]: its exceptions to the late limit, each for its own events (see [lateLimitFor]).
 * - [republisher]: whose copy of the authority's figures the tables it is checked against were read from, where they
 *   were not read from the authority's own pages (the audit of 12 Nov 2026, finding F26: Kemenag's kab/kota tables
 *   come from the myQuran API); null where no republisher is recorded, which is not a finding that the
 *   authority's own publication was read: the other entries' sources have not been audited for it. The app and the site say so
 *   wherever they say "checked against its published timetable".
 * - [partlyRepublisher]: whose copy SOME of the tables it is checked against were read from, the rest from the
 *   authority's own pages (the second audit's G7, ruling R276: JAKIM's 2025 tables and nine zones' 2027 from the
 *   api.waktusolat.app mirror, 2026 and the other zones' 2027 from e-solat itself; the Islamic Foundation's Dhaka
 *   permanent table from a PDF on kivabe.com, its Ramadan pages from its own). A unit says it where its own tables are
 *   among them ([AuthorityUnit.partlyRepublished]); a sentence of the whole entry's days (About's tiles, "checked on …
 *   days so far", the site's checks page) says it always. Null where no table of the entry is a republisher's.
 * - [safeSideOfKey]: for an entry Taqwa calculates itself ([EntryClass.D_NONE]), the short-name key of the body whose
 *   published timetable Taqwa holds a part of and calculates on the safe side of (Tajikistan's Council of Ulama,
 *   Côte d'Ivoire's COSIM; the trust audit's F30, ruling R209): the card and About name the body and say so, where
 *   they would otherwise say no local timetable is known. Null where nothing of a local timetable is held. The kind
 *   stays "Calculated by Taqwa": the times are Taqwa's own calculation, not the body's method reproduced.
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
    val area: Area? = null,
    val measured: Boolean = false,
    val lateLimits: List<LateLimit> = emptyList(),
    val republisher: String? = null,
    val safeSideOfKey: String? = null,
    val partlyRepublisher: String? = null,
) {
    init {
        require((method == null) == (entryClass == EntryClass.C)) { "$id: a method, or members when cautious" }
        require(republisher == null || republisher.isNotBlank()) { "$id: a republisher has a name" }
        require(partlyRepublisher == null || republisher == null) { "$id: its tables are a copy in whole or in part, not both" }
        require(safeSideOfKey == null || entryClass == EntryClass.D_NONE) {
            "$id: only Taqwa's own calculation is on the safe side of a body's part-held timetable"
        }
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
fun lateLimitFor(event: TimedEvent, unit: AuthorityUnit?, entry: RegistryEntry): LateLimit? {
    val own = ownLateLimitFor(event, unit, entry)
    // Ruling R298: an entry with no units (MUIS's one table) claims its figure across its country (ruling R297), and its
    // reach's measured worst is keyed by the entry itself: the registry's entry, not a variant a tool builds under its id.
    val key = unit?.id ?: entry.id.takeIf { Units.of(it) == null && Registry.byId(it) == entry } ?: return own
    val reach = reachLateLimit(entry.id, key, event) ?: return own
    return if (own != null && own.minutes >= reach.minutes) own else reach
}

/** [lateLimitFor] without the reach's own measured figure ([reachLateLimit]): the unit's recorded exception, else the entry's. */
fun ownLateLimitFor(event: TimedEvent, unit: AuthorityUnit?, entry: RegistryEntry): LateLimit? =
    unit?.lateLimits?.firstOrNull { event in it.events } ?: entry.lateLimits.firstOrNull { event in it.events }

/**
 * Ruling R270 (the second trust audit of 14 Nov 2026, finding G26): a unit's limit for [event] where its own tables,
 * read across its reach, were measured later than its own limit allowed ([ReachLimits], written from the gate's stamps
 * by `generateReachLimits`, as R254 and R255 did for Bahla and Türkiye by hand and by script): the measured worst, so
 * that About's "at most N min" and the gate say the same figure. Null where the reach needs nothing more. Since ruling
 * R298 an entry with no units, whose table is read across the area it claims (R297), is keyed by its own id as [unitId].
 */
fun reachLateLimit(entryId: String, unitId: String, event: TimedEvent): LateLimit? = reachLimits[entryId]?.get(unitId)?.get(event)

private val reachLimits: Map<String, Map<String, Map<TimedEvent, LateLimit>>> by lazy {
    ReachLimits.MINUTES.mapValues { (entryId, units) ->
        units.mapValues { (unitId, events) ->
            events.mapValues { (event, minutes) ->
                LateLimit(minutes, reachReason(event, minutes, area = "$entryId/$unitId" in AREA_READ_UNITS), setOf(event))
            }
        }
    }
}

/**
 * The claims the proof reads across an area rather than a unit's circle alone (the tools' `ReachAreas.AREAS`, which a
 * test holds to this list), as `<entry>/<unit>` (an entry with no units under its own id, ruling R298): London
 * Unified's M25, MUIS's Singapore and the Supreme Council's Bahrain (ruling R297), and KACST's Tayma table, which the set
 * also hands to the town 111 km south of it (ruling R300). Their reach limit gives the area's reason; any other unit's,
 * the circle's, even where the unit is keyed by its entry's id (Austria's IGGO, a circle round Vienna).
 */
val AREA_READ_UNITS: Set<String> = setOf("bh.council/bh.council", "gb.london.lupt/gb.london.lupt", "sa.ummalqura/tayma", "sg.muis/sg.muis")

/**
 * Ruling R318 (the owner's ruling of 18 Nov 2026, the final audit's B1): the claims the proof reads only where a GPS fix
 * is placed in the claim's country alone, its nearest app city the country's and no other country held beside it (the
 * tools' `ReachAreas` Cell, Chosen and Handed claims, which a test holds to this): MUIS's and the Supreme Council's
 * nearest-city cells (ruling R307), Tayma's town (R300) and every zone of JAKIM's (R310), as `<entry>/<unit>` (an entry
 * with no units under its own id) or the whole entry. Chosen at a border place whose nearest city is the neighbour's and
 * resolved in the claim's country through the second country (ruling R259, [Registry.resolveChosen]), such a timetable
 * is shown at a point no reach row reads, so it claims no figure there. London's M25 is not here: its shape is read at
 * every point inside it, whatever the nearest city; nor are the units read by their circles (`ReachRows.SPECS`), whose
 * rows hold every point of the circle, whatever the country.
 */
fun readInCountryAlone(entryId: String, unitId: String?): Boolean =
    entryId in READ_IN_COUNTRY_ALONE_ENTRIES || "$entryId/${unitId ?: entryId}" in READ_IN_COUNTRY_ALONE_UNITS

/** [readInCountryAlone]'s claims of one unit (or of an entry with no units). */
val READ_IN_COUNTRY_ALONE_UNITS: Set<String> = setOf("bh.council/bh.council", "sa.ummalqura/tayma", "sg.muis/sg.muis")

/** [readInCountryAlone]'s entries, every unit of theirs: JAKIM's zones, each read across the ground the app hands it (ruling R310). */
val READ_IN_COUNTRY_ALONE_ENTRIES: Set<String> = setOf("my.jakim")

/**
 * The reason a reach limit gives. [area]: a table followed across an area rather than a unit's circle
 * ([AREA_READ_UNITS]: London Unified's M25, MUIS's Singapore, rulings R297 and R298; Tayma's town, R300), where no other
 * table rides.
 */
private fun reachReason(event: TimedEvent, minutes: Int, area: Boolean): String {
    val end = event == TimedEvent.SUNRISE || event == TimedEvent.END_OF_EATING || event == TimedEvent.IMSAK
    val measured = "was measured " + (if (end) "up to $minutes min before it" else "up to $minutes min after it") +
        " on some days, and never " + (if (end) "after" else "before") + " it"
    return if (area) {
        "This table, read across the area the proof draws for it (ReachAreas.AREAS), not only its own point, " +
            "$measured: away from the point it is printed for the user's own sun decides the time shown (rulings R270 and R297)."
    } else {
        "This place's table, read across the place's reach (the ground around the point it is printed for that " +
            "follows it), $measured: toward the edge of the reach the user's own sun, and the tables that ride beside this " +
            "one, decide the time shown (ruling R270)."
    }
}

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
 * - [unitCityId]: the app's own city whose table the unit is ([AuthorityUnit.cityId]), whose name
 *   About gives the place, in the reader's language; null for every other unit.
 * - [alongside]: another entry's tables riding beside the place's day ([CrossRide], the owner's decision of
 *   17 Oct 2026), each that table's own day at its own point: the day shown is the safe side of the place's own
 *   day (a cautious one, its members combined) and theirs. Empty almost everywhere; no figure is claimed for them.
 * - [measuredThrough]: the last date [measured] holds for ([AuthorityUnit.measuredThrough]); null where it holds on
 *   every date. What the place is on a given date is [on].
 * - [partlyRepublisher]: [RegistryEntry.partlyRepublisher] where the place's own tables are among those read from that
 *   copy (its unit's [AuthorityUnit.partlyRepublished], or no unit: the entry's days), else null.
 * - [partlyRepublishedYears]: the years the place's copied tables are ([AuthorityUnit.republishedYears]), every other
 *   checked year the authority's own; empty where not known apart. A sentence about one day reads [wholeCopyOn] and
 *   [partlyCopyOn] (ruling R278).
 * - [checkedClass]: where [on] made a checked authority [EntryClass.D_AUTHORITY] for a day its timetable was not
 *   compared on (ruling R275), the class it has on the days that were (A or B), so the site's proven rule can still
 *   name the first unchecked day; null otherwise.
 * - [sides]: where the place may be in either of two countries ([Place.alsoCountryCode], ruling R259), each country's
 *   own resolution there, the nearest city's country first; the day is held to both ([DayPipeline][world.taqwa.app
 *   .prayer.engine.DayPipeline]: each start the later of their days, each end the earlier), the resolution is
 *   cautious and claims no figure, and [members] lists both sides' timetables for the screens that name them. Empty
 *   everywhere else.
 * - [safeSideOfTableKey]: the short-name key of the body whose own table for this place rides beside a calculated day
 *   ([UnitSet.safeSideOf], ruling R285: Habous's Sebta and Melilla tables beside Spain's safe default within 10 km of
 *   either, ruling R261), so the day's end of eating and sunrise are that table's where it is the earlier; null
 *   everywhere else. [safeSideOfKey] is the body the card and About name: this, else the entry's own
 *   ([RegistryEntry.safeSideOfKey]).
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
    val unitCityId: Int? = null,
    val alongside: List<Alongside> = emptyList(),
    val measuredThrough: LocalDate? = null,
    val checkedClass: EntryClass? = null,
    val partlyRepublisher: String? = entry.partlyRepublisher,
    val partlyRepublishedYears: Set<Int> = emptySet(),
    val sides: List<Resolution> = emptyList(),
    val safeSideOfTableKey: String? = null,
) {
    /** The body whose timetable a calculated day is on the safe side of: a table riding beside it here, else the entry's (ruling R285). */
    val safeSideOfKey: String? get() = safeSideOfTableKey ?: entry.safeSideOfKey

    /**
     * Whose copy the place's own table compared on [date] was, wholly, for a sentence about that one day (the Prayer
     * screen's card; ruling R278, the second audit's G7 as its review read it): the entry's [RegistryEntry.republisher]
     * where every table is one (Kemenag's myQuran); where only some of the place's are and their years are known
     * ([partlyRepublishedYears]: Kuala Lumpur's 2025 and 2027 are the mirror's, its 2026 e-solat's own), the copy in
     * those years; else null. A run of days (About's "checked through", the site's page) keeps [partlyRepublisher].
     */
    fun wholeCopyOn(date: LocalDate): String? =
        entry.republisher ?: partlyRepublisher?.takeIf { date.year in partlyRepublishedYears }

    /** [partlyRepublisher] for a sentence about [date]: only where the copy's years are not known apart (Dhaka's), so every day may be either. */
    fun partlyCopyOn(date: LocalDate): String? =
        partlyRepublisher?.takeIf { partlyRepublishedYears.isEmpty() && wholeCopyOn(date) == null }

    /**
     * This resolution as it stands on [date].
     *
     * - Ruling R231 as revised (13 Nov 2026): a unit whose figure rests on a calendar that ends ([measuredThrough]:
     *   Algeria's fitted seats, fitted on the 1448 calendar's days) claims no figure on a later date: there it is not
     *   [measured].
     * - Ruling R275 (the second audit's G9, 14 Nov 2026): a checked authority (class A or B) is checked only on a day
     *   its timetable was compared on there ([CheckedDays], the stamp's runs for the place's unit, as the site's proven
     *   rule reads them). On any other day (past the last checked run, in a hole between runs) it is the authority's
     *   published method not checked on that day: [EntryClass.D_AUTHORITY], with its label ("Published method"), card
     *   and About, as the city page is held then. The times are not touched: only what the user is told of them.
     *   Its name, unit and measured flag stay, so About's "checked on … days so far" keeps the days that were.
     *
     * Every other resolution is itself on every date.
     */
    fun on(date: LocalDate): Resolution {
        val figure = if (measured && measuredThrough != null && date > measuredThrough) copy(measured = false) else this
        val checked = entryClass != EntryClass.A && entryClass != EntryClass.B || CheckedDays.covers(entry.id, unitId, date)
        return if (checked) {
            figure
        } else {
            figure.copy(entryClass = EntryClass.D_AUTHORITY, about = aboutFor(EntryClass.D_AUTHORITY), checkedClass = entryClass)
        }
    }
}

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
    area: Area? = null,
    republisher: String? = null,
    safeSideOfKey: String? = null,
    partlyRepublisher: String? = null,
): RegistryEntry = RegistryEntry(
    id = id, shortNameKey = nameKey, entryClass = entryClass, about = aboutFor(entryClass), method = method,
    school = school, schoolKnown = schoolKnown, nearby = nearby, scope = scope, countries = countries, area = area,
    measured = measured, lateLimits = lateLimits, republisher = republisher, safeSideOfKey = safeSideOfKey,
    partlyRepublisher = partlyRepublisher,
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
 * An imsak counted back from the printed Fajr ([ImsakFrom.PrintedFajr]) is counted back from the dawn
 * again: its margin too was fitted at the table's point alone.
 */
internal fun TimetableMethod.atEdge(id: String, margins: EventOffsets) = copy(
    id = id,
    margins = margins,
    endOfEatingMarginSeconds = minOf(endOfEatingMarginSeconds + (margins.sunrise - this.margins.sunrise), SAFE_END),
    imsakFrom = ImsakFrom.Dawn,
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
