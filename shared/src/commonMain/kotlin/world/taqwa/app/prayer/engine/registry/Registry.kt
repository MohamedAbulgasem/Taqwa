package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.prayer.engine.day.RamadanCalendar
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

    /** What Automatic follows at [place]. */
    fun resolve(place: Place): Resolution = resolveEntry(automaticEntry(place), place)

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
     * placed the same way, each through its own entry's units where it has them.
     */
    fun resolveEntry(entry: RegistryEntry, place: Place): Resolution {
        val cc = place.countryCode.uppercase()
        val user = GeoPoint(place.lat, place.lon)
        val shia = Regions.shia(cc, user)
        val saudi = cc == "SA"
        val base = entry.method
        if (base == null) {
            // Review I3: a cautious entry is measured only where it is and where every member's own
            // placement at the place is measured (a unit's flag, false beyond every unit; an entry
            // without units, its own flag; a convention, near the tables that prove it).
            val members = entry.members.map { it to placed(it.id, it.method, memberMeasured(it, user), place) }
            return Resolution(
                entry, user, null, shia, saudi, entry.measured && members.all { (_, here) -> here.measured }, method = null,
                members = members.map { (member, here) -> member.copy(method = here.method) },
            )
        }

        val here = placed(entry.id, base, entry.measured, place)
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
     * regional variants and its curves for the fixed point's latitude.
     */
    private fun placed(entryId: String, base: TimetableMethod, measured: Boolean, place: Place): Placed {
        val user = GeoPoint(place.lat, place.lon)
        var method = base
        var unit: AuthorityUnit? = null
        var isMeasured = measured
        val units = Units.of(entryId)
        if (units != null) {
            unit = units.unitFor(place)
            if (unit != null) {
                val own = unit.method ?: base
                method = if (own.fixedPoint == null) own.copy(fixedPoint = unit.point) else own
                isMeasured = unit.measured
            } else {
                method = units.outside(user)
                isMeasured = false
            }
        }
        for (variant in variants) {
            if (variant.entryId == entryId && variant.area.contains(user)) method = variant.change(method)
        }
        // The curves follow the point the starts are read at (an ends-only fixed point leaves them at the user's).
        // A rule read at each place's own latitude (QMDB's) holds for the user's latitude as well, so that a unit's
        // point never makes the user's own start earlier than it is without the unit (PlaceCurves.at).
        val curvesAt = if (method.fixedPointMode == FixedPointMode.ENDS_ONLY) user else method.fixedPoint ?: user
        return Placed(PlaceCurves.at(method, curvesAt, also = user), unit, isMeasured)
    }

    /**
     * Whether a chosen [entry] applies at [place] (spec §2.2, ruling R50): an Other method anywhere;
     * any other entry where Automatic follows it or lists it (nearby or as a member), and a country
     * one, or one declared global, also in its own countries. A named timetable is a place's own: a
     * London table is no one's timetable in İstanbul.
     */
    fun inScope(entry: RegistryEntry, place: Place): Boolean {
        if (entry.scope == Scope.GLOBAL && entry.id.startsWith(OTHER_METHOD_PREFIX)) return true
        val inItsCountries = (entry.scope == Scope.GLOBAL || entry.scope == Scope.COUNTRY) &&
            place.countryCode.uppercase() in entry.countries
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
                "NG", "ET", "TZ", "CI", "KE", "UG", "AO", "CD", "GH", "CM", "SN", "BF", "ML", "NE", "TD", "GN", "MZ",
                "BJ", "MG", "ZM", "ZW", "MW", "RW", "SO", "SS", "CF", "CG", "GA", "GQ", "GW", "LR", "SL", "TG", "GM",
                "CV", "ST", "ER", "DJ", "KM", "MU", "SC", "RE", "YT", "NA", "BW", "LS", "SZ", "BI", "SH",
            )
            all("default.safe.seasia", "TH", "PH", "MM", "VN", "KH", "LA", "TL", "CX", "CC")
            all("default.safe.levant", "IL")
            all("default.safe.southasia", "LK", "NP", "BT", "MV")
        }
    }
}
