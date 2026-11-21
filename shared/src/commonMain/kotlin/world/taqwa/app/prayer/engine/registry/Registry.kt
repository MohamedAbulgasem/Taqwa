package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.engine.day.RamadanCalendar
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Americas
import world.taqwa.app.prayer.engine.registry.authorities.Balkans
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import world.taqwa.app.prayer.engine.registry.authorities.Generic
import world.taqwa.app.prayer.engine.registry.authorities.Gulf
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import world.taqwa.app.prayer.engine.registry.authorities.Levant
import world.taqwa.app.prayer.engine.registry.authorities.Maghreb
import world.taqwa.app.prayer.engine.registry.authorities.Muis
import world.taqwa.app.prayer.engine.registry.authorities.PlaceCurves
import world.taqwa.app.prayer.engine.registry.authorities.Russia
import world.taqwa.app.prayer.engine.registry.authorities.SouthAfrica
import world.taqwa.app.prayer.engine.registry.authorities.SouthAsia
import world.taqwa.app.prayer.engine.registry.authorities.UmmAlQura
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates

/**
 * From a place to the timetable it follows (spec §3.5–§3.7, §6).
 *
 * 1. The country from [Place.countryCode].
 * 2. A region override for that country ([Regions.rules]: London's M25, Chicago, Toronto, Montreal,
 *    Ottawa, Cape Town, Gaza, Iran's Sunni regions, Tatarstan, Dagestan, Bashkortostan, Dubai,
 *    Sandžak, Preševo, IRN's Tromsø calendar), else the country's own entry, else the safe default
 *    (class D, nothing known).
 * 3. The entry's units ([Units]): the nearest reference point within its radius, with the unit's
 *    own method; beyond every unit the authority's safe edge at the user's point, class D there and
 *    claiming no "at most" figure. Then the entry's regional variants ([MethodVariant]).
 *
 * Plain data and arithmetic: no resources, no platform code (tools/timetables compiles it).
 */
object Registry {

    /** Every entry, in a stable order. */
    val entries: List<RegistryEntry> by lazy {
        UmmAlQura.entries + Diyanet.entries + Muis.entries + Jakim.entries + Kemenag.entries + Egypt.entries +
            Gulf.entries + Gulf.others + Levant.entries + Maghreb.entries + SouthAfrica.entries + Russia.entries +
            Balkans.entries + Europe.entries + Americas.entries + SouthAsia.entries + CentralAsia.entries +
            Generic.others + Generic.entries
    }

    private val index: Map<String, RegistryEntry> by lazy { entries.associateBy { it.id } }

    fun byId(id: String): RegistryEntry? = index[id]

    /**
     * Settings' "Other methods", the only timetables that apply anywhere (ruling R50): the old
     * picker's methods without Tehran (controller ruling R12). A named timetable (Muhammadiyah,
     * London Unified, a cautious member …) is offered where Automatic lists it, see [inScope].
     */
    val otherMethods: List<RegistryEntry> by lazy {
        listOf(
            "other.mwl", "other.isna", "other.egyptian", "other.ummalqura", "other.karachi", "other.moonsighting",
            "other.turkey", "other.kuwait", "other.qatar", "other.dubai", "other.singapore",
        ).map { requireNotNull(byId(it)) { "missing Other method $it" } }
    }

    /** The Umm al-Qura lag dates the day computer needs for [DayRule.LAG_DATES_UQ][world.taqwa.app.prayer.engine.method.DayRule]. */
    val lagDates: Set<LocalDate> get() = UmmAlQuraDates.lagDates

    /**
     * What Automatic follows at [place]. Where the place may be in a second country ([Place.alsoCountryCode], ruling
     * R259) whose Automatic entry there is another, both: [border].
     */
    fun resolve(place: Place): Resolution {
        val own = resolveEntry(automaticEntry(place), place)
        val other = alsoPlace(place) ?: return own
        val otherEntry = automaticEntry(other)
        if (otherEntry.id == own.entry.id) return own
        return border(own, resolveEntry(otherEntry, other))
    }

    /** [place] as its second country's ([Place.alsoCountryCode], ruling R259), or null where it names none other. */
    fun alsoPlace(place: Place): Place? {
        val also = place.alsoCountryCode?.takeIf { it.isNotBlank() && !it.equals(place.countryCode, ignoreCase = true) } ?: return null
        return place.copy(countryCode = also, admin1 = place.alsoAdmin1, alsoCountryCode = null, alsoAdmin1 = null)
    }

    /** The prefix of a border resolution's entry id ([border]): no registry entry has it. */
    const val BORDER_PREFIX = "border:"

    /**
     * Ruling R259 (the second trust audit's G4 and G5, 14–15 Nov 2026): a place that may stand in either of two
     * countries, held to both countries' timetables. [own] is the nearest city's country's resolution, [beside] the
     * device zone's country's. The day is computed for each as it would be alone, every unit, ride and floor of its
     * own included, and combined start by start: each start the later, each end (sunrise, the end of eating, imsak,
     * every prayer's end) the earlier ([world.taqwa.app.prayer.engine.DayPipeline]). So it is never before the
     * timetable of the country the place is in, whichever of the two that is, and never ends after it.
     *
     * The resolution is cautious ("Cautious times", class C) and claims no figure: neither side's "at most" holds for
     * the other's minutes. Its entry is no registry entry (so no proof stamp, no Settings row of its own): it carries
     * both sides' timetables as its members, for the screens that name them, and lists both entries as used nearby,
     * so that a user who knows the side they are on can choose that country's timetable (in scope here, [inScope]),
     * confirm it, and have it alone. Automatic's Asr school is the later one where the two sides' differ (Hanafi):
     * the Asr shown is then never before the majority's Asr on either side; the other school's Asr stays the later of
     * the two as well (`asrOther`).
     */
    internal fun border(own: Resolution, beside: Resolution): Resolution {
        val sides = listOf(own, beside)
        val members = sides.flatMap { side ->
            side.method?.let { listOf(Member(side.entry.id, side.entry.shortNameKey, it, 1)) } ?: side.members.sortedBy { it.shareRank }
        }.mapIndexed { i, member -> member.copy(shareRank = i + 1) }
        val entry = RegistryEntry(
            id = BORDER_PREFIX + own.entry.id + "+" + beside.entry.id,
            shortNameKey = CAUTIOUS_NAME,
            entryClass = EntryClass.C,
            about = AboutTemplate.CAUTIOUS,
            members = members,
            school = if (AsrSchool.HANAFI in setOf(own.entry.school, beside.entry.school)) AsrSchool.HANAFI else own.entry.school,
            schoolKnown = own.entry.schoolKnown && beside.entry.schoolKnown && own.entry.school == beside.entry.school,
            nearby = (listOf(own.entry.id, beside.entry.id) + own.entry.nearby + beside.entry.nearby).distinct(),
            scope = Scope.UNIT,
            countries = own.entry.countries + beside.entry.countries,
            measured = false,
        )
        return Resolution(
            entry = entry,
            point = own.point,
            unitName = null,
            shiaRegion = own.shiaRegion || beside.shiaRegion,
            saudi = own.saudi,
            measured = false,
            method = null,
            members = members,
            sides = sides,
        )
    }

    /** The entry Automatic follows at [place], before its units. */
    fun automaticEntry(place: Place): RegistryEntry {
        val cc = place.countryCode.uppercase()
        val point = GeoPoint(place.lat, place.lon)
        val id = Regions.ruleFor(cc, point, place.admin1)?.entryId ?: countryEntries[cc] ?: DEFAULT_ID
        return requireNotNull(byId(id)) { "no entry $id" }
    }

    /**
     * [entry] at [place]: its unit, its method there, the class and whether it is measured there. For
     * Automatic's entry and for a timetable the user chose alike. A cautious entry's members are
     * placed the same way, each through its own entry's units where it has them. [inUnit]: a single method's unit to
     * place it in whichever unit [place] itself resolves to, for the gate alone, whose rows carry no region: a unit
     * that shares its point with another (Oran's band, fitted on its own table, around its seat's unit, ruling R217)
     * or that a place reaches by its region alone (an Algerian wilaya's seat unit, rulings R219 and R230) is checked
     * at the row's point as itself.
     */
    fun resolveEntry(entry: RegistryEntry, place: Place, inUnit: AuthorityUnit? = null): Resolution {
        val cc = place.countryCode.uppercase()
        val user = GeoPoint(place.lat, place.lon)
        val shia = Regions.shia(cc, user)
        val saudi = cc == "SA"
        val base = entry.method
        if (base == null) {
            // Review I3: a cautious entry is measured only where it is and where every member's own
            // placement at the place is measured (a unit's flag, false beyond every unit; an entry
            // without units, its own flag; a convention, near the tables that prove it).
            val members = entry.members.map { it to placed(it.id, it.method, memberMeasured(it, user), place, asMember = true) }
            // Another entry's tables riding beside this one (Units.crossRide, the owner's decision of 17 Oct 2026): ISNA's
            // Montréal and Ottawa mosques beside Canada's cautious times outside those cities. No figure is claimed for them.
            val alongside = Units.crossRide(entry.id)?.let { cross -> finishedAlongside(cross.tablesEntryId, cross.ride(user), place.zoneId) }
            return Resolution(
                entry, user, null, shia, saudi, entry.measured && members.all { (_, here) -> here.measured }, method = null,
                members = members.map { (member, here) -> member.copy(method = here.method) },
                alongside = alongside.orEmpty(),
            )
        }

        val here = if (inUnit == null) {
            placed(entry.id, base, entry.measured, place)
        } else {
            Placed(methodInUnit(entry.id, base, inUnit, user, place.zoneId), inUnit, inUnit.measured)
        }
        val entryClass = here.unit?.entryClass ?: if (!here.measured && (entry.entryClass == EntryClass.A || entry.entryClass == EntryClass.B)) {
            EntryClass.D_AUTHORITY
        } else {
            entry.entryClass
        }
        return Resolution(
            entry = entry,
            point = user,
            unitName = here.unit?.name,
            shiaRegion = shia,
            saudi = saudi,
            measured = here.measured,
            method = here.method,
            entryClass = entryClass,
            about = aboutFor(entryClass),
            unitPoint = here.unit?.point,
            unitId = here.unit?.id,
            unitLabel = here.unit?.takeIf { it.named }?.name,
            unitCityId = here.unit?.cityId,
            measuredThrough = here.unit?.measuredThrough,
            partlyRepublisher = entry.partlyRepublisher?.takeIf { here.unit == null || here.unit.partlyRepublished },
            partlyRepublishedYears = here.unit?.takeIf { entry.partlyRepublisher != null }?.republishedYears.orEmpty(),
            // A body's own table riding beside a calculated day beyond every unit (ruling R285: Ceuta and Melilla).
            safeSideOfTableKey = if (here.unit == null) Units.of(entry.id)?.safeSideOf?.invoke(user) else null,
        )
    }

    /**
     * Whether a cautious [member] is measured at [user] before its units are consulted: an entry's own
     * flag, or, for a convention, whether [user] is within class C's reach of one of its tables.
     */
    private fun memberMeasured(member: Member, user: GeoPoint): Boolean =
        byId(member.id)?.measured
            ?: member.measuredAt.any { distanceKm(user, it) <= lateReachKm(it.lat, EntryClass.C) }

    /** A method as it applies at a place, and the unit it came from. */
    private class Placed(val method: TimetableMethod, val unit: AuthorityUnit?, val measured: Boolean)

    /**
     * Entry [entryId]'s method [base] at [place]: its unit's own method, carrying the unit's point
     * as its fixed point (ruling R15: starts the later of that point and the user's, ends the
     * earlier; a method with a fixed point of its own keeps it), else the safe edge; then its
     * regional variants and its curves for the fixed point's latitude. [asMember]: placed as a cautious
     * entry's member, which beyond every unit takes [UnitSet.outsideAsMember] where the set has one.
     */
    private fun placed(entryId: String, base: TimetableMethod, measured: Boolean, place: Place, asMember: Boolean = false): Placed {
        val user = GeoPoint(place.lat, place.lon)
        val units = Units.of(entryId) ?: return Placed(methodAlone(entryId, base, user, place.zoneId), null, measured)
        // A cautious member beyond every unit may ride less far than the entry chosen on its own (UnitSet.outsideAsMember).
        val outside = units.outsideAsMember.takeIf { asMember } ?: units.outside
        val unit = units.unitFor(place)
            ?: return Placed(finish(entryId, outside(user), user, place.zoneId), null, measured && units.measuredBeyond)
        return Placed(methodInUnit(entryId, base, unit, user, place.zoneId), unit, unit.measured)
    }

    /**
     * Entry [entryId]'s method [base] for a user at [user] inside [unit], whichever unit the place would resolve to:
     * the unit's own method carrying the unit's point as its fixed point (ruling R15; a method with a fixed point of
     * its own keeps it), then the regional variants and curves ([finish]). The reach of QMDB's units is measured
     * with it (the city-points round of 9 Oct 2026, `tools/timetables`' QmdbReach), [alone]: the unit's own table beside
     * the user's point and nothing else, without the other tables whose reach holds the user ([UnitSet.around]; a reach
     * is how far one table carries, whatever rides beside it, ruling R249). [zoneId]: the user's zone, as [finish].
     */
    internal fun methodInUnit(
        entryId: String,
        base: TimetableMethod,
        unit: AuthorityUnit,
        user: GeoPoint,
        zoneId: String? = null,
        alone: Boolean = false,
    ): TimetableMethod {
        val own = unit.method ?: base
        val placed = if (own.fixedPoint == null) {
            own.copy(fixedPoint = unit.point, endsBoundAtUserPoint = unit.endsBoundAtUserPoint)
        } else {
            own
        }
        // The other tables whose reach holds the user, or the authority's town figures (UnitSet.around).
        val around = if (alone) placed else Units.of(entryId)?.around?.invoke(user, unit, placed) ?: placed
        return finish(entryId, around, user, zoneId)
    }

    /** Entry [entryId]'s method [base] at [user] as if it had no units: the user's own point alone, then [finish]. */
    internal fun methodAlone(entryId: String, base: TimetableMethod, user: GeoPoint, zoneId: String? = null): TimetableMethod =
        finish(entryId, base, user, zoneId)

    /**
     * Entry [entryId]'s method at [user] beyond every unit of its, as [placed] gives it there: its units' own method
     * outside them (a point table's edge, or the tables riding beside the user, [TableEnvelope.beyond]), else [base],
     * then [finish]. For the tests that hold such a place to its own point alone ([methodAlone]).
     */
    internal fun methodBeyondUnits(entryId: String, base: TimetableMethod, user: GeoPoint, zoneId: String? = null): TimetableMethod =
        finish(entryId, Units.of(entryId)?.outside?.invoke(user) ?: base, user, zoneId)

    /**
     * [method] with entry [entryId]'s regional variants for [user] and its curves for the point the starts are
     * read at (an ends-only fixed point leaves them at the user's). A rule read at each place's own latitude
     * (QMDB's) holds for the user's latitude as well, so that a unit's point never makes the user's own start
     * earlier than it is without the unit (PlaceCurves.at). [zoneId], the user's zone, is where the Moonsighting
     * Committee's rule finds each civil date's own noon (T3 review, 11 Oct 2026: east of 180° in UTC+12 it is the solar
     * day before the UTC date's); null reads it at local mean time.
     *
     * Each of the method's days alongside ([TimetableMethod.alongside], the nearest table's ride past its bound) is
     * finished the same way at its own point, as if the user stood there: that table's own variants and its curves at its
     * own latitude, so its day is the one its own point shows.
     */
    private fun finish(entryId: String, method: TimetableMethod, user: GeoPoint, zoneId: String?): TimetableMethod {
        var m = method
        for (variant in variants) {
            if (variant.entryId == entryId && variant.area.contains(user)) m = variant.change(m)
        }
        val curvesAt = if (m.fixedPointMode == FixedPointMode.ENDS_ONLY) user else m.fixedPoint ?: user
        val placed = PlaceCurves.at(m, curvesAt, also = user, others = m.envelopePoints, zoneId = zoneId)
        if (placed.alongside.isEmpty()) return placed
        return placed.copy(alongside = placed.alongside.map { it.copy(method = finish(entryId, it.method, it.point, zoneId)) })
    }

    /**
     * [alongside] (another entry's tables riding beside a cautious place, [Units.crossRide]) finished at each table's
     * own point under its own entry [entryId], as [finish] finishes a method's own days alongside.
     */
    private fun finishedAlongside(entryId: String, alongside: List<Alongside>, zoneId: String?): List<Alongside> =
        alongside.map { it.copy(method = finish(entryId, it.method, it.point, zoneId)) }

    /**
     * Whether a chosen [entry] applies at [place] (spec §2.2, ruling R50): an Other method anywhere;
     * any other entry where Automatic follows it or lists it (nearby or as a member), and a country
     * one, or one declared global, also in its own countries, within its [RegistryEntry.area] there
     * where it has one. A named timetable is a place's own: a London table is no one's timetable in
     * İstanbul, nor in Glasgow (the owner's decision of 17 Oct 2026: London Unified beyond R45's three
     * reaches of its table's point). Elsewhere Automatic applies, and Settings says so in one line. A place that may be in
     * a second country ([Place.alsoCountryCode], ruling R259) takes what applies in either.
     */
    fun inScope(entry: RegistryEntry, place: Place): Boolean =
        inScopeAlone(entry, place) || alsoPlace(place)?.let { inScopeAlone(entry, it) } == true

    /**
     * The place a chosen [entry] is resolved at: [place], or its second country's ([alsoPlace], ruling R259) where the
     * entry applies only there (Kyrgyzstan's Muftiate chosen at Cholpon-Ata, whose nearest city is Almaty).
     */
    fun placeFor(entry: RegistryEntry, place: Place): Place =
        if (inScopeAlone(entry, place)) place else alsoPlace(place)?.takeIf { inScopeAlone(entry, it) } ?: place

    /**
     * A chosen [entry] at [place]: [resolveEntry] at [placeFor]'s place. Ruling R318 (the owner's ruling of 18 Nov 2026,
     * the final audit's B1): where that is the second country's ([alsoPlace]: the place's nearest city is the
     * neighbour's) and the entry's figure there is read only where a fix is placed in its country alone
     * ([readInCountryAlone]: MUIS at Batu Pahat on a Singapore network, the Supreme Council at Dammam on a Bahraini one,
     * a JAKIM zone at Woodlands on a Malaysian one), no reach row reads the point: it is not measured there, and a
     * checked authority is its published method ([EntryClass.D_AUTHORITY]), so About says "Not measured" and claims no
     * "at most" or "up to". No time moves.
     */
    fun resolveChosen(entry: RegistryEntry, place: Place): Resolution {
        if (inScopeAlone(entry, place)) return resolveEntry(entry, place)
        val also = alsoPlace(place)?.takeIf { inScopeAlone(entry, it) } ?: return resolveEntry(entry, place)
        val there = resolveEntry(entry, also)
        if (!there.measured || !readInCountryAlone(entry.id, there.unitId)) return there
        val entryClass = if (there.entryClass == EntryClass.A || there.entryClass == EntryClass.B) EntryClass.D_AUTHORITY else there.entryClass
        return there.copy(measured = false, entryClass = entryClass, about = aboutFor(entryClass))
    }

    /** [inScope] in [place]'s own country alone. */
    private fun inScopeAlone(entry: RegistryEntry, place: Place): Boolean {
        if (entry.scope == Scope.GLOBAL && entry.id.startsWith(OTHER_METHOD_PREFIX)) return true
        val inItsCountries = (entry.scope == Scope.GLOBAL || entry.scope == Scope.COUNTRY) &&
            place.countryCode.uppercase() in entry.countries &&
            entry.area?.contains(GeoPoint(place.lat, place.lon)) != false
        if (inItsCountries) return true
        val automatic = automaticEntry(place)
        return automatic.id == entry.id || entry.id in automatic.nearby || automatic.members.any { it.id == entry.id }
    }

    /**
     * The Ramadan calendar (controller ruling R11): Umm al-Qura's Ramadan dates inside their range
     * (2024–2030), beyond it every date within a day of the tabular Hijri Ramadan, the user's Hijri
     * offset applied as the app applies it. A Ramadan rule only ever makes a time later, so the
     * fallback errs towards Ramadan.
     */
    fun ramadanCalendar(hijriOffsetDays: Int = 0): RamadanCalendar = calendar(hijriOffsetDays, toleranceDays = 0)

    /**
     * [entry]'s Ramadan: Umm al-Qura's own dates for Umm al-Qura; for every other authority those
     * dates widened by a day each side, since local announcements differ (MUIS's Ramadan 1447 ended
     * on 20 Mar 2026, a day after Umm al-Qura's).
     */
    fun ramadanCalendarFor(entry: RegistryEntry, hijriOffsetDays: Int = 0): RamadanCalendar =
        calendar(hijriOffsetDays, toleranceDays = if (entry.id in UMM_AL_QURA_IDS) 0 else 1)

    private fun calendar(hijriOffsetDays: Int, toleranceDays: Int) = RamadanCalendar { date ->
        if (date in UMM_AL_QURA_RANGE) {
            (-toleranceDays..toleranceDays).any { date.plus(it, DateTimeUnit.DAY) in UmmAlQuraDates.ramadanDates }
        } else {
            (-1..1).any { k ->
                TabularHijriCalendar.fromGregorian(date.plus(k + hijriOffsetDays, DateTimeUnit.DAY)).month == RAMADAN
            }
        }
    }

    /** Regional changes to an entry's method (eastern and southern Libya, northern Kazakhstan, Scotland). */
    private val variants: List<MethodVariant> by lazy { Maghreb.variants + CentralAsia.variants + Europe.variants }

    private const val DEFAULT_ID = "default.safe"
    private const val OTHER_METHOD_PREFIX = "other."
    private const val RAMADAN = 9
    private val UMM_AL_QURA_IDS = setOf("sa.ummalqura", "other.ummalqura")
    private val UMM_AL_QURA_RANGE = LocalDate(2024, 1, 1)..LocalDate(2030, 12, 31)

    /** Each country's own entry (ISO 3166-1 alpha-2), before region overrides. */
    private val countryEntries: Map<String, String> by lazy {
        buildMap {
            fun all(id: String, vararg codes: String) = codes.forEach { put(it, id) }
            all("sa.ummalqura", "SA")
            all("tr.diyanet", "TR")
            all("sg.muis", "SG")
            all("bn.mora", "BN")
            all("my.jakim", "MY")
            all("id.kemenag", "ID")
            all("eg.esa", "EG")
            all("ae.awqaf", "AE")
            all("qa.calendarhouse", "QA")
            all("kw.awqaf", "KW")
            all("bh.council", "BH")
            all("om.mara", "OM")
            all("jo.awqaf", "JO")
            all("ps.iftaa", "PS")
            all("lb.fatwa", "LB")
            all("sy.awqaf", "SY")
            all("iq.sunni", "IQ")
            all("ye.default", "YE")
            all("ly.awqaf", "LY")
            all("tn.inm", "TN")
            all("dz.marw", "DZ")
            all("ma.habous", "MA", "EH")
            all("mr.ministry", "MR")
            all("sd.ministry", "SD")
            all("za.jamiat", "ZA")
            all("ru.dumrf", "RU")
            all("ba.iz", "BA")
            all("xk.bik", "XK")
            all("al.kmsh", "AL")
            all("me.izcg", "ME")
            all("gb.cautious", "GB", "IM", "JE", "GG")
            all("ie.cautious", "IE")
            all("fr.cautious", "FR", "MC")
            all("be.cautious", "BE")
            all("nl.cautious", "NL")
            all("de.cautious", "DE")
            all("at.iggo", "AT")
            all("ch.fids", "CH", "LI")
            all("no.cautious", "NO", "SJ")
            all("se.cautious", "SE", "DK", "FI", "IS", "FO", "AX", "GL")
            all("us.isna", "US", "PR", "VI", "GU", "AS", "MP", "UM")
            all("ca.cautious", "CA")
            all("au.cautious", "AU")
            all("nz.fianz", "NZ")
            all("pk.karachi", "PK")
            all("in.karachi", "IN")
            all("bd.ifb", "BD")
            all("af.default", "AF")
            all("uz.board", "UZ")
            all("kz.qmdb", "KZ")
            all("kg.default", "KG")
            all("tj.default", "TJ")
            all("tm.default", "TM")
            all("ci.default", "CI")
            all("ir.default", "IR")
            all(
                "default.safe.americas",
                "MX", "BR", "AR", "CO", "VE", "PE", "CL", "EC", "BO", "PY", "UY", "GY", "SR", "GF", "CU", "DO", "HT",
                "JM", "TT", "BS", "BB", "BZ", "GT", "HN", "SV", "NI", "CR", "PA", "AG", "DM", "GD", "KN", "LC", "VC",
                "AW", "CW", "SX", "BQ", "MF", "BL", "GP", "MQ", "KY", "TC", "MS", "AI", "BM", "PM", "FK", "GS", "VG",
            )
            all("default.safe.europe", "ES", "PT", "IT", "MT", "LU", "AD", "SM", "VA", "GI")
            all("default.safe.balkans", "MK", "RS", "HR", "SI", "BG", "GR", "RO", "CY", "MD")
            all(
                "default.safe.africa",
                "NG", "ET", "TZ", "KE", "UG", "AO", "CD", "GH", "CM", "SN", "BF", "ML", "NE", "TD", "GN", "MZ",
                "BJ", "MG", "ZM", "ZW", "MW", "RW", "SO", "SS", "CF", "CG", "GA", "GQ", "GW", "LR", "SL", "TG", "GM",
                "CV", "ST", "ER", "DJ", "KM", "MU", "SC", "RE", "YT", "NA", "BW", "LS", "SZ", "BI", "SH",
            )
            all("default.safe.seasia", "TH", "PH", "MM", "VN", "KH", "LA", "TL", "CX", "CC")
            all("default.safe.levant", "IL")
            all("default.safe.southasia", "LK", "NP", "BT", "MV")
        }
    }
}
