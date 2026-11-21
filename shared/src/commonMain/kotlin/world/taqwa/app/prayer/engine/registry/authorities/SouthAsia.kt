package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.OwnTable
import world.taqwa.app.prayer.engine.registry.RIDE_REACHES
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.TableEnvelope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.single

/** Pakistan, India and Bangladesh (research South Asia sections; spec §6.2). */
object SouthAsia {

    /**
     * A majority convention with the MWL Fiqh Council's minutes (spec §6.2 b, 1986): Dhuhr, Asr,
     * Maghrib and Isha + 2, sunrise − 2, the end of eating 2 min before the dawn; the Fajr start at
     * the convention's own angle. With the first-guess 30 s. Used elsewhere for authorities this
     * subtask does not own (the Americas' ISNA-convention entries); Pakistan and India (below) now
     * have their own task-7f-fitted methods instead of this first guess.
     */
    internal fun conventionWithMwlMinutes(id: String, fajr: Double, isha: Double, fajrMinutes: Int = 0) = TimetableMethod(
        id = id,
        fajrAngle = fajr,
        isha = IshaRule.Angle(isha),
        margins = margins(start = 120 + SAFE_START, sunrise = -120 + SAFE_SUNRISE, fajr = fajrMinutes * 60 + SAFE_START),
        endOfEatingMarginSeconds = -120,
    )

    /**
     * Pakistan: no binding state timetable; the Karachi method is near-universal (the Banuri Town
     * calculator equals 18°/18° with no offsets, nearest, Hanafi). Margins fitted (task 7f) on the
     * Deobandi Karachi Daimi (permanent) timetable's June, September and December pages, folded
     * together with Dawat-e-Islami's Karachi AJAX (a later, Barelvi member: `pk-karachi.tsv`,
     * `proof/7f-asia.md`), so the single Asr margin (+71 s) clears both the printed Standard and
     * Hanafi columns; the entry itself shows the Hanafi one (spec §3.7). Class D (no authority
     * publishes one binding table; the near-universal method is well fitted, but not to A/B's
     * every-day standard once Dawat-e-Islami's own +1 min advice is folded in). How that September 2026 AJAX table was
     * asked is not on record (no capture, address or headers are held with it): whether it may stay is the owner's to
     * rule (docs/BUILD-LOG.md, rulings R235 to R237).
     */
    val pakistanMethod = TimetableMethod(
        id = "pk.karachi",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = EventOffsets(fajr = 4, sunrise = -28, dhuhr = 3, asr = 71, maghrib = 26, isha = 5),
        endOfEatingMarginSeconds = -26,
    )

    val pakistan: RegistryEntry = single(
        id = "pk.karachi", nameKey = "authority_karachi", entryClass = EntryClass.D_AUTHORITY,
        method = pakistanMethod, school = AsrSchool.HANAFI, countries = setOf("PK"),
    )

    /**
     * Dawat-e-Islami's city cards (ruling R190, 13 Nov 2026; `pk-dawateislami.tsv`). Dawat-e-Islami, a Barelvi movement,
     * prints a card for each city, "prepared for the complete boundaries" of the city "with other necessary precautions"
     * (its own note): 18° for Fajr and Isha as the Karachi method, but its Maghrib a minute or two after the method's at the
     * same point, its Dhuhr, both Asr columns and Isha up to a minute after, and its sunrise before (the survey's item O9:
     * on [pakistanMethod] alone the app began Maghrib before the card on 3,191 of 3,566 place-days of the first fifty
     * cities held). Whether the card is the timetable Pakistan's majority follows is the owner's to rule (O9 is open): it
     * is held here as the later of two bodies, which is never early by either. Each card is a table printed for its own
     * city: a unit at the app's own point for that city, a minute's reach wide, with the card's own margins
     * ([CityCard.on]). The figures are each card's worst second of November 2026 against the sea-level sun at that point
     * (the month held for every city; never a printed time): the starts' latest, sunrise's and the dawn's earliest, each
     * kept [CARD_SAFETY] past. November 2026 is the only month held so far, for every card (ruling R235, 13 Nov 2026): a
     * plain request reaches the month the page shows and no other, so no card is held through the seasons yet and none in
     * Ramadan. Each later month can be held by one plain GET per city in that month (Ramadan 1448's February and March
     * 2027 included), and the margins refitted on what it shows. The card says of itself that it does not serve the next
     * year: the margins fitted on November 2026 are held against nothing of 2027.
     */
    internal class CityCard(
        val key: String,
        val name: String,
        val point: GeoPoint,
        val fajr: Int,
        val sunrise: Int,
        val dhuhr: Int,
        val asr: Int,
        val maghrib: Int,
        val isha: Int,
        val endOfEating: Int,
    ) {
        /**
         * [method] never before this card nor any end after it: each start's margin the later of [method]'s own and the
         * card's with [CARD_SAFETY], sunrise's and the end of eating's the earlier (two bodies everywhere, the method's own
         * and the card's: the later holds every start and the earlier every end, so Karachi's unit keeps Banuri's Daimi
         * table as well).
         */
        fun on(method: TimetableMethod): TimetableMethod = method.copy(
            margins = EventOffsets(
                fajr = maxOf(method.margins.fajr, fajr + CARD_SAFETY),
                sunrise = minOf(method.margins.sunrise, sunrise - CARD_SAFETY),
                dhuhr = maxOf(method.margins.dhuhr, dhuhr + CARD_SAFETY),
                asr = maxOf(method.margins.asr, asr + CARD_SAFETY),
                maghrib = maxOf(method.margins.maghrib, maghrib + CARD_SAFETY),
                isha = maxOf(method.margins.isha, isha + CARD_SAFETY),
            ),
            endOfEatingMarginSeconds = minOf(method.endOfEatingMarginSeconds, endOfEating - CARD_SAFETY),
        )
    }

    /**
     * Seconds kept past each card's worst November second, the same for every card (rulings R194 and R235, 13 Nov 2026).
     * November 2026 is the only month held so far (a plain request reaches the month the page shows and no other), so
     * nothing held shows how far a card's other months run past its November figure: the 45 s is a safety, not a bound
     * proven through the year, until later months are held the same plain way. (The review of 13 Nov 2026 set it while
     * other months of twelve cities were held, which ran up to 23 s past November's worst, about 25 s read at the other
     * cards' latitudes. Those months had been asked of the site's month picker, not by plain requests. The owner left
     * them to the Lead (his ruling 3 of 13 Nov 2026: "you can decide"), and the Lead dropped them under the engine
     * lanes' web rule, plain GETs only; they are no longer held, so the figure rests on nothing the archive can show.
     * Those twelve cities kept 30 s until then and keep 45 s now, as every other card always did: 15 s later, never
     * earlier. Both Asr columns ride the one Asr margin, so the standard Asr is 15 s later there as well.)
     */
    internal const val CARD_SAFETY = 45

    /**
     * Ruling R194: how far from a card's own point a place is taken as that city itself. Farther out, still within the
     * card's unit, the next town has a card of its own that is not held (ruling R191's own premise), and held neighbours
     * that near differ by more than a minute at Maghrib (Haripur and Havelian, 22 km apart): there the place takes the
     * later of the unit's card and what it would take with no card held ([cardsBesideTheTable]).
     */
    internal const val CITY_KM = 10.0

    /**
     * Rulings R190, R191 and R194: [table], the entry's table printed for its own point and promised out to its ride as
     * before ([world.taqwa.app.prayer.engine.registry.tablesAtTheirPoints]: Banuri's Daimi table at Karachi,
     * Dawat-e-Islami's Delhi table), with Dawat-e-Islami's [cards] as units of their own. A card is its city's alone
     * ([TableEnvelope]'s city-bound tables): within a minute's reach of its point the nearest card is the unit, and every
     * other card whose own reach holds the user rides beside it as its own day at its own point ([Alongside]); no such
     * card rides past its unit. Within a card's unit but farther than [CITY_KM] from its point (the next town, whose own
     * card is not held) the place's own day beyond the cards ([beyond]) rides beside the card's as well, so the day shown
     * is never before either. [table]'s own card (the one of its key) holds [table]'s unit, and rides with [table] as far
     * as [table]'s bound reach holds a user (the city's own outskirts, which the card is printed for: Karachi's eastern
     * suburbs lie past its unit); in [table]'s ride past its bound [table]'s day rides on [base] alone, as it did.
     * [beyond] is the method at the user's own point beyond every unit, which those days ride beside.
     */
    private fun cardsBesideTheTable(
        entryId: String,
        base: TimetableMethod,
        table: OwnTable,
        cards: List<CityCard>,
        beyond: (GeoPoint) -> TimetableMethod,
    ): UnitSet {
        val own = cards.single { it.key == table.key }
        require(own.point == table.point) { "$entryId: ${table.key}'s card is fitted at the table's own point" }
        val units = cards.map { card ->
            AuthorityUnit(
                card.key, card.name, card.point, lateReachKm(card.point.lat, EntryClass.A),
                method = if (card === own) null else card.on(base), measured = false,
                lateLimits = if (card === own) table.lateLimits else emptyList(), named = false, endsBoundAtUserPoint = true,
            )
        }
        val tableUnit = units.single { it.id == table.key }
        val envelope = TableEnvelope(units, EntryClass.D_AUTHORITY, RIDE_REACHES, cityBound = cards.map { it.key }.toSet() - table.key)

        /** The days riding beside [user]'s: each unit's the envelope holds there, [table]'s with its own card within its bound. */
        fun beside(user: GeoPoint): List<Alongside> {
            val cardHolds = distanceKm(user, table.point) <= envelope.boundKm(tableUnit)
            return envelope.alongside(user, base).map { if (cardHolds && it.point == table.point) Alongside(it.point, own.on(base)) else it }
        }
        return UnitSet(
            entryId, units, envelope = envelope,
            around = { user, unit, placed ->
                val inCity = if (unit.id == table.key) own.on(placed) else placed
                val nextTown = unit.id != table.key && distanceKm(user, unit.point) > CITY_KM
                inCity.copy(
                    alongside = inCity.alongside + beside(user).filter { it.point != unit.point } +
                        if (nextTown) listOf(Alongside(user, beyond(user))) else emptyList(),
                )
            },
        ) { user -> beyond(user).copy(alongside = beside(user)) }
    }

    /**
     * Dawat-e-Islami's Pakistani cards ([CityCard]): Karachi's at the Karachi tables' own point, the rest at the app's own
     * point for the city (Taxila, which the app does not list, at its own town; the app's Wah lies in its unit). The last
     * thirteen were added by the review of 13 Nov 2026 (ruling R194): Kallar Kahar, an app city within Chakwal's unit whose
     * own card begins Maghrib after Chakwal's with its safety, Khairpur and Wazirabad within Sukkur's and Gujrat's, and ten
     * towns that had no card held, each a minute or two less late by its own card than by [pakistanBeyondCards].
     */
    internal val pakistanCards = listOf(
        CityCard("karachi", "Karachi", GeoPoint(24.8607, 67.0011), -53, -102, 17, 94, 100, 16, -58),
        CityCard("attock", "Attock City", GeoPoint(33.76671, 72.35977), -25, -103, 12, 105, 103, 12, -30),
        CityCard("bahawalpur", "Bahawalpur", GeoPoint(29.39779, 71.6752), -28, -77, 14, 101, 63, 14, -34),
        CityCard("bannu", "Bannu", GeoPoint(32.98527, 70.60403), -28, -100, 8, 99, 99, 8, -33),
        CityCard("battagram", "Battagram", GeoPoint(34.67719, 73.02329), -28, -334, 2, 96, 332, 2, -33),
        CityCard("chakwal", "Chakwal", GeoPoint(32.93286, 72.85394), -26, -194, 4, 96, 197, 4, -31),
        CityCard("dera-ghazi-khan", "Dera Ghazi Khan", GeoPoint(30.04587, 70.64029), -24, -48, 5, 91, 53, 5, -29),
        CityCard("dera-ismail-khan", "Dera Ismail Khan", GeoPoint(31.83129, 70.9017), -27, -63, 22, 111, 70, 22, -32),
        CityCard("faisalabad", "Faisalabad", GeoPoint(31.41554, 73.08969), -23, -78, 25, 114, 86, 25, -28),
        CityCard("gilgit", "Gilgit", GeoPoint(35.91869, 74.31245), -26, -376, 4, 100, 375, 4, -31),
        CityCard("gujranwala", "Gujranwala", GeoPoint(32.15567, 74.18705), -28, -82, 21, 112, 88, 21, -33),
        CityCard("gujrat", "Gujrat", GeoPoint(32.5742, 74.07542), -28, -64, 2, 93, 62, 2, -33),
        CityCard("gwadar", "Gwadar", GeoPoint(25.12163, 62.32541), -20, -50, 41, 111, 81, 36, -23),
        CityCard("hafizabad", "Hafizabad", GeoPoint(32.07095, 73.68802), -26, -51, 4, 93, 52, 4, -31),
        CityCard("hangu", "Hangu", GeoPoint(33.53198, 71.0595), -26, -165, 4, 96, 166, 4, -32),
        CityCard("haripur", "Haripur", GeoPoint(33.99783, 72.93493), -25, -138, 16, 108, 136, 16, -30),
        CityCard("havelian", "Havelian", GeoPoint(34.05348, 73.15993), -25, -196, 5, 99, 201, 5, -30),
        CityCard("islamabad", "Islamabad", GeoPoint(33.72148, 73.04329), -35, -164, 27, 132, 157, 31, -44),
        CityCard("jacobabad", "Jacobabad", GeoPoint(28.28187, 68.43761), -27, -45, 3, 88, 46, 3, -32),
        CityCard("jhang", "Jhang Sadr", GeoPoint(31.26981, 72.31687), -27, -49, 2, 88, 49, 1, -32),
        CityCard("jhelum", "Jhelum", GeoPoint(32.93448, 73.73102), -27, -51, 3, 93, 51, 2, -32),
        CityCard("kalat", "Kalat", GeoPoint(29.02663, 66.59361), -26, -137, 4, 89, 140, 4, -31),
        CityCard("kasur", "Kasur", GeoPoint(31.11866, 74.45025), -26, -50, 4, 93, 53, 4, -31),
        CityCard("kohat", "Kohat", GeoPoint(33.58196, 71.44929), -28, -126, 18, 110, 123, 18, -33),
        CityCard("lahore", "Lahore", GeoPoint(31.558, 74.35071), -33, -126, 32, 123, 119, 32, -38),
        CityCard("larkana", "Larkana", GeoPoint(27.55898, 68.21204), -27, -44, 3, 88, 44, 4, -32),
        CityCard("loralai", "Loralai", GeoPoint(30.37051, 68.59795), -27, -123, 3, 90, 124, 3, -32),
        CityCard("mansehra", "Mansehra", GeoPoint(34.33023, 73.19679), -27, -327, 3, 97, 328, 3, -32),
        CityCard("mardan", "Mardan", GeoPoint(34.19794, 72.04965), -23, -66, 18, 112, 79, 18, -29),
        CityCard("mastung", "Mastung", GeoPoint(29.79966, 66.84553), -27, -118, 3, 89, 117, 3, -33),
        CityCard("mianwali", "Mianwali", GeoPoint(32.57756, 71.52847), -30, -72, 6, 96, 66, 6, -35),
        CityCard("mirpur-khas", "Mirpur Khas", GeoPoint(25.5276, 69.01255), -28, -46, 2, 84, 45, 2, -33),
        CityCard("multan", "Multan", GeoPoint(30.19679, 71.47824), -25, -79, 23, 110, 84, 23, -30),
        CityCard("murree", "Murree", GeoPoint(33.90836, 73.3903), -24, -462, 6, 99, 468, 6, -29),
        CityCard("muzaffarabad", "Muzaffarābād", GeoPoint(34.37002, 73.47082), -26, -258, 5, 102, 261, 6, -32),
        CityCard("okara", "Okara", GeoPoint(30.81029, 73.45155), -29, -54, 2, 92, 51, 2, -35),
        CityCard("peshawar", "Peshawar", GeoPoint(34.008, 71.57849), -25, -134, 47, 141, 168, 47, -30),
        CityCard("pishin", "Pishin", GeoPoint(30.58176, 66.99406), -29, -119, 1, 88, 115, 1, -34),
        CityCard("quetta", "Quetta", GeoPoint(30.18414, 67.00141), -26, -134, 16, 104, 137, 16, -31),
        CityCard("rahim-yar-khan", "Rahim Yar Khan", GeoPoint(28.41987, 70.30345), -26, -56, 16, 99, 57, 15, -31),
        CityCard("rawalakot", "Rawalakot", GeoPoint(33.85782, 73.76043), -26, -374, 4, 97, 377, 4, -31),
        CityCard("rawalpindi", "Rawalpindi", GeoPoint(33.59733, 73.0479), -29, -148, 30, 116, 146, 29, -33),
        CityCard("sahiwal", "Sahiwal", GeoPoint(30.66595, 73.10186), -28, -52, 2, 91, 51, 3, -33),
        CityCard("sargodha", "Sargodha", GeoPoint(32.08586, 72.67418), -24, -59, 12, 101, 66, 12, -29),
        CityCard("sheikhupura", "Shekhupura", GeoPoint(31.71287, 73.98556), -26, -62, 10, 100, 59, 11, -31),
        CityCard("sialkot", "Sialkot", GeoPoint(32.49268, 74.53134), -28, -77, 2, 93, 75, 2, -33),
        CityCard("skardu", "Skardu", GeoPoint(35.29787, 75.63372), -30, -475, 1, 99, 471, 2, -35),
        CityCard("sukkur", "Sukkur", GeoPoint(27.70323, 68.85889), -27, -58, 21, 106, 65, 22, -32),
        CityCard("swabi", "Swabi", GeoPoint(34.12018, 72.46982), -26, -62, 3, 95, 93, 3, -32),
        CityCard("zhob", "Zhob", GeoPoint(31.34082, 69.4493), -27, -123, 3, 91, 123, 3, -32),
        CityCard("kallar-kahar", "Kallar Kahar", GeoPoint(32.77998, 72.69793), -26, -243, 4, 95, 244, 4, -32),
        CityCard("khairpur", "Khairpur Mir’s", GeoPoint(27.52948, 68.75915), -26, -51, 12, 96, 53, 12, -32),
        CityCard("wazirabad", "Wazirabad", GeoPoint(32.44324, 74.12), -28, -59, 2, 93, 57, 3, -33),
        CityCard("taxila", "Taxila", GeoPoint(33.74592, 72.78747), -34, -119, -4, 90, 105, -4, -39),
        CityCard("gujar-khan", "Gujar Khan", GeoPoint(33.25411, 73.30433), -26, -93, 4, 95, 94, 3, -32),
        CityCard("choa-saidan-shah", "Choa Saidan Shah", GeoPoint(32.71962, 72.98625), -26, -218, 4, 95, 221, 4, -31),
        CityCard("chiniot", "Chiniot", GeoPoint(31.72091, 72.97836), -28, -52, 2, 90, 49, 1, -33),
        CityCard("shikarpur", "Shikarpur", GeoPoint(27.95558, 68.63823), -26, -51, 4, 88, 53, 4, -31),
        CityCard("mandi-bahauddin", "Mandi Bahauddin", GeoPoint(32.58704, 73.49123), -27, -52, 2, 93, 51, 3, -33),
        CityCard("narowal", "Narowal", GeoPoint(32.10197, 74.87303), -26, -56, 4, 94, 53, 4, -31),
        CityCard("bahawalnagar", "Bahawalnagar", GeoPoint(29.99835, 73.25272), -27, -47, 3, 92, 47, 4, -33),
        CityCard("khushab", "Khushāb", GeoPoint(32.29667, 72.3525), -25, -47, 4, 95, 50, 4, -31),
        CityCard("vehari", "Vihari", GeoPoint(30.0445, 72.3556), -25, -62, 11, 97, 59, 10, -30),
    )

    /** The lie of the land beyond the cards ([pakistanBeyondCards]), each holding the ones before it. */
    internal enum class Terrain { PLAINS, UPLAND, MOUNTAIN }

    /**
     * Ruling R192, where a Pakistani place lies: the mountains (Hazara, Azad Kashmir, Gilgit-Baltistan and the north of
     * Khyber Pakhtunkhwa, with Murree's hills), the Indus plains (Punjab south of the Salt Range and east of the Sulaimans,
     * Sindh east of the Kirthar, with Karachi and the coast east of it: ruling R194, the review of 13 Nov 2026), and the
     * uplands between and west of them (the Potohar, Peshawar's valley and the south of Khyber Pakhtunkhwa, Balochistan
     * and the coast west of Karachi). Lines drawn between the cards' own cities, whose sunrise and Maghrib margins fall
     * mostly into the three bands with the height of the ground; a city's size moves them as well (Lahore's Maghrib is the
     * plains' latest), which no line gives.
     */
    internal fun terrain(p: GeoPoint): Terrain = when {
        p.lat >= 34.45 || (p.lat >= 33.75 && p.lon >= 72.95) || (p.lat >= 32.95 && p.lon >= 73.65) -> Terrain.MOUNTAIN
        (p.lat < 32.45 || (p.lon >= 73.4 && p.lat < 32.85)) && p.lon >= plainsWestEdge(p.lat) -> Terrain.PLAINS
        else -> Terrain.UPLAND
    }

    /** The plains' western edge at [lat]: the Sulaimans' foot, the Kirthar's, and south of it the Hub river at Karachi's west. */
    private fun plainsWestEdge(lat: Double): Double = when {
        lat >= 28.5 -> 70.0
        lat >= 25.3 -> 67.6
        else -> 66.95
    }

    /**
     * [base] with the latest margin [cards] ask, each card with the safety ([CityCard.on]): Fajr, Dhuhr, Asr and Isha
     * the latest and the end of eating the earliest over all of them, sunrise the earliest and Maghrib the latest over
     * [ground], the cards whose ground a place shares (all of them where no bands are drawn).
     */
    internal fun latestOf(base: TimetableMethod, cards: List<CityCard>, ground: List<CityCard> = cards): TimetableMethod {
        val all = cards.map { it.on(base) }
        val sun = ground.map { it.on(base) }
        return base.copy(
            margins = EventOffsets(
                fajr = all.maxOf { it.margins.fajr },
                sunrise = sun.minOf { it.margins.sunrise },
                dhuhr = all.maxOf { it.margins.dhuhr },
                asr = all.maxOf { it.margins.asr },
                maghrib = sun.maxOf { it.margins.maghrib },
                isha = all.maxOf { it.margins.isha },
            ),
            endOfEatingMarginSeconds = all.minOf { it.endOfEatingMarginSeconds },
        )
    }

    /** [band]'s method beyond the cards as [cards] make it: [latestOf] them, the ground the cards of [band] and the lower ones. */
    internal fun bandOf(cards: List<CityCard>, band: Terrain): TimetableMethod =
        latestOf(pakistanMethod, cards, cards.filter { terrain(it.point) <= band })

    private val beyondCards: Map<Terrain, TimetableMethod> by lazy { Terrain.entries.associateWith { bandOf(pakistanCards, it) } }

    /**
     * Ruling R192, a Pakistani place with no card held: Dawat-e-Islami prints a card for every town, and every one of
     * those held is later than [pakistanMethod] at its own point, so the method alone would begin before the place's own
     * card on most days. Until its card is held the place is computed at its own point with the latest margin any held
     * card asks, each with the safety: Fajr, Dhuhr, Asr and Isha the latest and the end of eating the earliest over
     * every card (they follow each city's width, which no rule gives: Gwadar, a small town, asks Dhuhr 41 s and Peshawar
     * 47 s with their dawns where the others' are, so neither is a shifted point to be taken out), sunrise and Maghrib the
     * extreme over the cards of its own [terrain] and the lower ones (they follow the height of the ground). Expected to be
     * later than the place's own card, not proven against it: no figure is claimed there. What holds is that every held
     * card, left out, would be covered by the band the others make (the rule test's leave-one-out); a large city or a high
     * town with no card held can still ask more than its band.
     */
    internal fun pakistanBeyondCards(user: GeoPoint): TimetableMethod = beyondCards.getValue(terrain(user))

    /**
     * Banuri's Daimi table and Dawat-e-Islami's Karachi table are printed for Karachi (pk-karachi.tsv), as [indiaUnits]: on
     * main, at the app's own PECHS, 6.5 km east of their point, a start came a minute before them on some days (the
     * city-point audit of 10–11 Oct 2026). Rulings R190 to R192 and R194 (13 Nov 2026): Dawat-e-Islami's city cards are
     * units beside it ([cardsBesideTheTable]), Karachi's own card within Karachi's unit and out to the bound reach of
     * Karachi's tables, and beyond every unit a place takes the cards' latest margins for its terrain
     * ([pakistanBeyondCards]) with Banuri's day riding beside it as far as before. No figure is claimed, there or elsewhere.
     */
    val pakistanUnits: UnitSet = cardsBesideTheTable(
        pakistan.id, pakistanMethod,
        OwnTable(
            "karachi", "Karachi", GeoPoint(24.8607, 67.0011),
            listOf(
                LateLimit(
                    KARACHI_LATE,
                    "two bodies print for Karachi and the later holds every start, the earlier every end (ruling R190): " +
                        "Dawat-e-Islami's card, with the 45 s kept past it, begins Maghrib up to 4 min after Banuri's " +
                        "Daimi table and ends sunrise up to 4 min before it; an early end is the safe side",
                    setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB),
                ),
                LateLimit(
                    KARACHI_ASR_LATE,
                    "two bodies print for Karachi and the later holds every start (ruling R190): Dawat-e-Islami's card, " +
                        "with the 45 s kept past it (ruling R235), begins Asr under the Hanafi school up to 5 min " +
                        "after Banuri's Daimi table, at Hub in June; the standard Asr stays within 4",
                    setOf(TimedEvent.ASR),
                ),
            ),
        ),
        pakistanCards, ::pakistanBeyondCards,
    )

    /**
     * India: no authority found; the only verified table is Dawat-e-Islami Delhi (18°/18° Hanafi,
     * with elevation, 701 ft). Margins fitted (task 7f) on its September 2026 AJAX capture alone
     * (`in-karachi.tsv`): Delhi's elevation widens sunrise/Maghrib well beyond Karachi's own fit, so
     * India keeps its own margins rather than sharing Pakistan's. No printed sehri/imsak column was
     * captured for Delhi; the end of eating keeps the sunrise margin as a conservative stand-in (both
     * are "ends" at the same point), unproven on its own. Hanafi (spec §3.7; Kerala's Shafi'is,
     * unverified, get the later Asr). Class D: one month, one source, "sharing prohibited". How that capture was asked
     * is not on record, as Karachi's ([pakistanMethod]): the same open question.
     */
    val indiaMethod = TimetableMethod(
        id = "in.karachi",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = EventOffsets(fajr = 1, sunrise = -123, dhuhr = 4, asr = 75, maghrib = 121, isha = 4),
        endOfEatingMarginSeconds = -123,
    )

    val india: RegistryEntry = single(
        id = "in.karachi", nameKey = "authority_karachi", entryClass = EntryClass.D_AUTHORITY,
        method = indiaMethod, school = AsrSchool.HANAFI, countries = setOf("IN"),
    )

    /**
     * Dawat-e-Islami's Indian cards held ([CityCard], rulings R193 and R194, 13 Nov 2026; `in-dawateislami.tsv`): Delhi's
     * at the Delhi table's own point, and eight cities at the app's own points. On [indiaMethod] alone a prayer began before
     * the city's own card in November 2026 at every one of the eight (Hyderabad's and Bengaluru's Maghrib, Bengaluru's,
     * Chennai's, Pune's and Ahmedabad's Fajr, Mumbai's Isha, Kolkata's Asr under the Hanafi school, and Dhuhr, Asr or Isha
     * at Chennai, Pune and Jaipur on up to 21 days of the month): nine of the ten Indian cities asked. Lucknow's card,
     * which the method alone was never before, is held as a row only, so that [indiaBeyondCards] is held to a card it was
     * not made from.
     */
    internal val indiaCards = listOf(
        CityCard("delhi", "Delhi", GeoPoint(28.6139, 77.209), 2, -29, 29, 105, 114, 26, -2),
        CityCard("bengaluru", "Bengaluru", GeoPoint(12.97194, 77.59369), 9, -57, 9, 80, 145, 9, 4),
        CityCard("hyderabad", "Hyderabad", GeoPoint(17.38405, 78.45636), 4, -80, 7, 85, 132, 10, -2),
        CityCard("mumbai", "Mumbai", GeoPoint(19.07283, 72.88261), -25, -90, 11, 83, 93, 9, -29),
        CityCard("kolkata", "Kolkata", GeoPoint(22.56263, 88.36304), -24, -60, 6, 84, 97, 6, -29),
        CityCard("chennai", "Chennai", GeoPoint(13.08784, 80.27847), 18, -19, 20, 94, 98, 22, 12),
        CityCard("pune", "Pune", GeoPoint(18.51957, 73.85535), 4, -32, 34, 108, 79, 34, -1),
        CityCard("jaipur", "Jaipur", GeoPoint(26.91962, 75.78781), -26, -93, 34, 117, 95, 34, -32),
        CityCard("ahmedabad", "Ahmedabad", GeoPoint(23.02579, 72.58727), 5, -31, 5, 83, 65, 4, 0),
    )

    private val indiaBeyond: TimetableMethod by lazy { latestOf(indiaMethod, indiaCards) }

    /**
     * Ruling R194 (the review of 13 Nov 2026), an Indian place with no card held, as [pakistanBeyondCards]: the method alone
     * began a prayer before the city's own card at nine of the ten Indian cities asked, so until its card is held a place
     * is computed at its own point with the latest margin any held Indian card asks for each start, with its safety, and
     * the earliest for sunrise and the end of eating. One band for the whole country: the nine cards do not sort by the
     * height of the ground (Bengaluru's and Hyderabad's Maghrib are the latest, Pune's, as high, among the earliest), and
     * none is held in the Himalaya, where a town's own card may well ask more. Expected to be later than the place's own
     * card, not proven against it: no figure is claimed.
     */
    @Suppress("UNUSED_PARAMETER")
    internal fun indiaBeyondCards(user: GeoPoint): TimetableMethod = indiaBeyond

    /**
     * Dawat-e-Islami's Delhi table is printed for Delhi (in-karachi.tsv; rulings R30, R40 and R44). On main, at the app's
     * own Delhi, 4.8 km north-east of it, the Fajr shown was a minute before it on 3 days (the city-point audit of 10–11
     * Oct 2026). Rulings R193 and R194 (13 Nov 2026): Dawat-e-Islami's city card for Delhi is later than that table (Asr
     * under the Hanafi school on 22 days of November 2026, Dhuhr on 17, Isha and the standard Asr on some), so Delhi's unit
     * takes its card as well, out to the bound reach of Delhi's table, and eight more cities are units by their own cards
     * ([cardsBesideTheTable], [indiaCards]); every other Indian place takes the cards' latest margins at its own point
     * ([indiaBeyondCards]), Delhi's table riding beside it as far as it did. No figure is claimed, there or elsewhere (one
     * month of one source).
     */
    val indiaUnits: UnitSet = cardsBesideTheTable(
        india.id, indiaMethod,
        OwnTable(
            "delhi", "Delhi", GeoPoint(28.6139, 77.2090),
            listOf(
                LateLimit(
                    4,
                    "Dawat-e-Islami prints no sehri time: the end of eating keeps the sunrise margin, and within a minute's " +
                        "reach of Delhi's point the earlier of its dawn and the user's own is shown (rulings R30 and R44): " +
                        "up to 4 min before its Fajr; an early end is the safe side",
                    setOf(TimedEvent.END_OF_EATING),
                ),
            ),
        ),
        indiaCards, ::indiaBeyondCards,
    )

    /**
     * Bangladesh: the Islamic Foundation (spec §6.2 a). Its year-round Dhaka table adds 3 min to
     * true Fajr, Zuhr and Maghrib and ends sehri 3 min before true dawn; Isha ≈ 18° + 1..4. Since
     * Ramadan 1446 (2025) its Ramadan calendar is 64 district pages: sehri at the district's east
     * edge (seconds dropped), the Fajr azan and iftar at its west edge (rounded up), at 18°, with no
     * minutes added. Margins fitted (task 7f) on the permanent table across the year (`bd-ifb.tsv`,
     * `proof/7f-asia.md`), held out on an independently digitized October page (already public as
     * BD-IFB) which agrees exactly; the fitted core is then widened by a further district edge, a
     * quarter degree (about a minute) either side of Dhaka. The 64 districts are units since 13 Nov
     * 2026 ([Bangladesh], rulings R220–R224): this method at the user's own point, which is the 1993
     * rule at a district's town, and [Bangladesh.held] beside it for the rest of a district under
     * either rule. The district edge stays: without it the districts' own days are early (the fit on
     * every district row of 13 Nov 2026 leaves Maghrib 10 s of it to spare). Hanafi. The Foundation's
     * published method everywhere, in a unit and beyond (R224): the archive holds a district's page
     * for Ramadan only and the app holds no district borders, so no district is a checked timetable.
     */
    val bangladeshMethod = TimetableMethod(
        id = "bd.ifb",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(fajr = 3, dhuhr = 3, maghrib = 3),
        margins = EventOffsets(
            fajr = -53 + DISTRICT_EDGE, sunrise = -56 - DISTRICT_EDGE, dhuhr = 29 + DISTRICT_EDGE,
            asr = -34 + DISTRICT_EDGE, maghrib = 47 + DISTRICT_EDGE, isha = 165 + DISTRICT_EDGE,
        ),
        endOfEating = EndOfEating.DawnAngle(18.0),
        endOfEatingMarginSeconds = -358 - DISTRICT_EDGE,
    )

    val bangladesh: RegistryEntry = single(
        id = "bd.ifb", nameKey = "authority_ifb", entryClass = EntryClass.D_AUTHORITY, method = bangladeshMethod,
        school = AsrSchool.HANAFI, countries = setOf("BD"),
        // Ruling R276 (the second audit's G7): Dhaka's year-round table is a PDF on kivabe.com, not found on the
        // Foundation's own site; its Ramadan pages are the Foundation's own. Said wherever the days compared are named.
        partlyRepublisher = "kivabe.com",
        lateLimits = listOf(
            LateLimit(
                8,
                "the true-dawn precaution swings across the year and the district edge stacks on it (task 7f); the permanent table " +
                    "prints the same month-day times every year while the sun shifts through the leap cycle, a minute more on a day of " +
                    "2027. A district's Ramadan page prints the exact dawn of its west edge, and beside it the " +
                    "1993 rule's three minutes are held from the town of every district that may hold the place, the app holding no " +
                    "borders: up to 164 s more for a neighbour's town (rulings R220–R224). The figure is the worst the held rows show, " +
                    "not a bound derived from the rule",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(4, "IFB's Isha angle is only documented as about 18° + 1..4 min, and the district edge stacks on it (task 7f)", setOf(TimedEvent.ISHA)),
            LateLimit(
                10,
                "the district edge makes the fitted sehri end run up to a minute earlier still (task 7f), and the permanent table " +
                    "against the leap cycle a minute more on a day of 2027; beside a district's Ramadan page, which ends sehri at the " +
                    "exact dawn of its east edge, the 1993 rule's three minutes before dawn are still held, and from the town of every " +
                    "district that may hold the place, the app holding no borders, so a place west of a neighbour's town ends earliest: " +
                    "up to 143 s more (rulings R220–R224). The figure is the worst the held rows show, not a bound derived from the " +
                    "rule; an early end is the safe side",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                8,
                "a district's Ramadan page prints the exact sunset at its west edge; the 1993 rule's three minutes after sunset and " +
                    "the year-round table's margin are held beside it, and from the town of every district that may hold the place, " +
                    "the app holding no borders, so a place east of a neighbour's town waits longest: up to 164 s more (rulings " +
                    "R220–R224). The figure is the worst the held rows show, not a bound derived from the rule",
                setOf(TimedEvent.MAGHRIB),
            ),
            LateLimit(
                4,
                "the district edge puts sunrise up to a minute before Dhaka's (task 7f), and the permanent table against the leap " +
                    "cycle a minute more on a day of 2027; an early end is the safe side",
                setOf(TimedEvent.SUNRISE),
            ),
        ),
    )

    val entries = listOf(pakistan, india, bangladesh)

    private const val DISTRICT_EDGE = 60

    /** Karachi's unit against Banuri's Daimi table once Dawat-e-Islami's card holds its starts as well ([pakistanUnits]). */
    private const val KARACHI_LATE = 4

    /**
     * The same for Asr since every card keeps [CARD_SAFETY] (ruling R235, 13 Nov 2026): Karachi's card kept 30 s until its
     * other months were removed, and the 15 s more carries the Hanafi Asr of a June day from 4 min after Banuri's to 5 at
     * one point of the unit, the app's own Hub (21.7 km from Karachi's point, against Banuri's June 2026 page); Karachi's
     * own point and the unit's three other app points stay at 4, and so does the standard Asr everywhere. A limit names a
     * prayer, not a school ([TimedEvent] has one Asr), so this one covers the standard column too, a minute looser than
     * it has ever been seen: a limit per school would hold it at 4 (the gate's, not this lane's, to add).
     */
    private const val KARACHI_ASR_LATE = 5
}
