package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.withNearestBeyond
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Egypt: the Egyptian General Authority for Survey (ESA), republished by Dar al-Ifta
 * (profiles-tested.md, data-gulf-egypt; authorities-arab-world-turkiye.md): Fajr 19.5° (fatwa 4021,
 * 2017), Isha 17.5°, no minutes, nearest rounding, about 100 cities at ESA's own points, no
 * elevation; the Asr shadow at the noon declination. Only data from 2025 on is used (a method change
 * between the Dec 2024 and Jan 2025 captures). The fast begins at the printed Fajr.
 *
 * Margins (Task 7a, eg-esa.tsv), national: fitted on eleven towns (the research's split B) with the
 * Conventions' formulas and 5 s and held out on eleven others, where one sunrise (Bawiti, 3 s) was
 * late and Dhuhr, Isha and the end of eating sat inside the 5 s; so they are the bound over all 22
 * towns with 5 s (proof 7a-gulf-egypt.md): Fajr +2, sunrise 0, Dhuhr −6, Asr −2, Maghrib +3, Isha
 * −4, end of eating +5. Held out on five more towns ESA prints (Dahab, Nuweiba, Saint Catherine,
 * Farafra, Shalatin): 0 early.
 *
 * Units: Cairo at ESA's own fitted point (30.08 N 31.27 E); the other towns at the app's points (the
 * five above at their town centres). Three towns' tables come from points well off the town, fitted
 * on their own 2025–2026 days (nothing held out there), and the engine keeps the later start and the
 * earlier end of the table and the method at the town (spec §3.5): Kharga's ESA point runs about
 * 70 s later and Taba's about 2 min later, so their starts are their own (Kharga +42..+48, Taba
 * +96..+99) and their sunrise and end of eating the national ones; Ras Gharib's runs about 65 s
 * earlier, so its starts are the national ones and its sunrise (−42) and end of eating (−49) its
 * own; Taba's own sunrise and dawn run up to 3 min before its table's (a recorded exception; 4 toward its reach's
 * east edge, [reachLate]).
 *
 * Kharga's Dhuhr (ruling R159): ESA's noon there runs 68 to 70 s after the town's, the same every
 * day (noon follows longitude alone), and ESA's own sun (an almanac formula, which gives every held
 * Dhuhr of all 30 towns from one point each, within a second) puts noon up to 4 s after this core's
 * in the first days of November. Fitted on Kharga's 45 held days, none of them those, Dhuhr was
 * +40 and came before ESA's printed one in early November 2026. ESA's rule asks up to 43 s over
 * 2026–2028, in early November each year: +48 with the Conventions' 5 s.
 *
 * Rafah and Sidi Barrani (ruling R160): ESA's own page for every day of 2026 shows their tables printed off the
 * town too, nearer: Rafah's from about 0.06° south and west, Sidi Barrani's from about 0.16° north, so their errors
 * follow the season. With the national margins Rafah's Fajr was a minute early on 2 days in June and Sidi Barrani's
 * Isha on 14 and its end of eating a minute late on 20, from late April to early August. Fitted on their whole year
 * with the Conventions' 5 s, only those are their own: Rafah's Fajr +11, Sidi Barrani's Isha +15 and end of eating
 * −20. Projected at their ESA points with ESA's own sun, 2027 and 2028 ask no more than 2026.
 *
 * The 48 cities ESA's daily page prints beside those 30 (ruling R163, the round of 12 November 2026: the Delta, the
 * Canal, Upper Egypt and the new cities), each on all of 2026 from ESA's own pages: at the app's point for the city
 * where the app lists it (27), else at ESA's own point fitted from its printed year with ESA's almanac sun (as Cairo's,
 * 21); 6th of October and Obour at ESA's fitted point too, since the app's point for each sits 19 km south-east and
 * 9 km west of it and inside the reach. Held out with the national margins on all 365 days: 0 early, at most 2 min
 * late. El Mahalla El Kubra and Baltim (ruling R164) are printed from points well off the town, about 21 km west and
 * 20 km south, as Kharga's: their own margins fitted on their whole year (nothing held out), only the events that ask
 * more than the national ones, with the Conventions' 5 s.
 *
 * Since the review of 12 November 2026 (ruling R172), each of the 23 at ESA's own point is fitted on the odd months of
 * 2026 alone (archive/code/manual/eg-esa/esa_points.py), so the even months are held out of the point too; and ESA's
 * rule, projected at each town's ESA point with its own sun and one offset an event (for a start the smallest any
 * printed minute of 2026 allows, for an end the largest; GulfEgyptProofTest), shows no start before and no end after
 * that projection on any day of 2027 or 2028, with as many seconds to spare as in 2026 to within 4 (El Mahalla El
 * Kubra's Isha 4, Baltim's sunrise 5, the least). That is a check of consistency, not a bound on what ESA will print
 * (the review of 13 Nov 2026): no one offset reproduces every printed minute of 2026 (for 278 of the 288 town-events
 * the largest offset some print needs is 4 to 19 s past the smallest another allows, Asr the widest), so ESA's print
 * of 2026 is a minute after the projection on about 5 % of event-days, where the tables themselves, not the
 * projection, show the engine is not early. Taken with the widest offsets instead, 179 event-days of 2027 and 189 of
 * 2028 at the towns' own points cannot be excluded, by a measure that names 93 days of 2026 of which none was early
 * against the print. Only ESA's own tables for 2027 settle those days: they are captured and gated when ESA serves them.
 *
 * Where two towns' reaches overlap (ruling R165, as Diyanet's R142; Cairo's 30 km takes in New Cairo, Obour and 15th
 * of May, and the Delta's and the new cities' towns touch), the nearer town holds the place, and every other town
 * whose reach holds it rides beside: its point in the envelope where it shares the national margins, its own day at
 * its own point where either has margins of its own. A user there may follow either town's table.
 *
 * Beyond every unit ESA's own point is not known and may be that far off: starts
 * [EDGE_START_SECONDS] s later (Taba's own margins exceed the national ones by up to 103 s, their
 * 5 s included), sunrise and the end of eating 60 s earlier (Ras Gharib's need 42 and 54). Checked by
 * computing all 30 towns at the edge instead of their units: 0 early, 0 late ends, at most 4 min; and the 48 of R163
 * at their units' points on all of 2026: 0 early, 0 late ends, at most 3 min. That check stood at the towns' own
 * points alone and said nothing of the ground just past a town's reach, where the audit of 12 Nov 2026 (finding F13)
 * found the end of eating and sunrise a minute after the town's table, and, replayed on rings past every reach, a
 * start a minute before it east of the towns printed from a point off the town. So out to twice its reach a town's
 * table rides beside the edge ([beside], ruling R213), and each town's own tables are replayed across that belt
 * (gate/eg-esa-envelope.tsv, held to the promise alone): never early and no end late; the day shown there runs up to
 * 6 min after the town's table, a table the place is not said to follow. Class B
 * (reference points verified only at the tested towns).
 */
object Egypt {
    private val national = margins(start = 0, sunrise = 0, fajr = 2, dhuhr = -6, asr = -2, maghrib = 3, isha = -4)

    val method = TimetableMethod(
        id = "eg.esa",
        fajrAngle = 19.5,
        isha = IshaRule.Angle(17.5),
        asrModel = AsrModel.NOON_SHADOW,
        margins = national,
        endOfEatingMarginSeconds = 5,
    )

    /**
     * A town ESA prints, 20 km around its point. It carries no name of its own for About and the site
     * ([AuthorityUnit.named] false, the audit of 12 Nov 2026, finding F34): a reach takes in towns that are not the
     * unit's (Helwan stands in 15th of May's, Rosetta in New Rashid's), and the unit's name is ESA's in Latin letters
     * in every language, so the place is called by the app's own name for it in the reader's language, as it is
     * beyond every unit.
     */
    private fun town(name: String, lat: Double, lon: Double) = AuthorityUnit(
        id = name.lowercase().replace(' ', '-'), name = name, point = GeoPoint(lat, lon), radiusKm = 20.0, named = false,
    )

    /** A town whose ESA table is computed at a point well off the town: its own margins. */
    private fun offPoint(name: String, lat: Double, lon: Double, margins: EventOffsets, endOfEating: Int): AuthorityUnit {
        val unit = town(name, lat, lon)
        return unit.copy(method = method.copy(id = "eg.esa.${unit.id}", margins = margins, endOfEatingMarginSeconds = endOfEating))
    }

    private val placed = listOf(
        town("Cairo", 30.08, 31.27).copy(radiusKm = 30.0),
        town("Alexandria", 31.20176, 29.91582),
        town("Aswan", 24.09082, 32.89942),
        town("Marsa Matruh", 31.3529, 27.23725),
        town("El Arish", 31.13159, 33.79844),
        town("Sallum", 31.55371, 25.15793),
        offPoint("Sidi Barrani", 31.61166, 25.92539, national.copy(isha = 15), endOfEating = -20),
        town("Siwa", 29.2032, 25.51965),
        town("Halaib", 22.22273, 36.64679),
        offPoint("Rafah", 31.28204, 34.23869, national.copy(fajr = 11), endOfEating = 5),
        town("Sharm El Sheikh", 27.91582, 34.32995),
        town("Hurghada", 27.25738, 33.81291),
        offPoint("Kharga", 25.45101, 30.54653, margins(start = 0, sunrise = 0, fajr = 45, dhuhr = 48, asr = 42, maghrib = 46, isha = 44), endOfEating = 5),
        town("Mut", 25.48742, 28.97918),
        town("Bawiti", 28.34827, 28.86936),
        town("Luxor", 25.69893, 32.6421),
        town("Quseir", 26.10611, 34.27716),
        town("Port Said", 31.26531, 32.3019),
        town("El Tor", 28.24168, 33.6222),
        town("Edfu", 24.97916, 32.87722),
        town("El Dabaa", 31.02819, 28.44498),
        town("Damietta", 31.41648, 31.81332),
        offPoint("Ras Gharib", 28.35831, 33.07829, national.copy(sunrise = -42), endOfEating = -49),
        town("Suez", 29.97371, 32.52627),
        town("Dahab", 28.5000, 34.5130),
        town("Nuweiba", 29.0333, 34.6667),
        town("Saint Catherine", 28.5550, 33.9760),
        town("Farafra", 27.0580, 27.9700),
        town("Shalatin", 23.1300, 35.5900),
        offPoint("Taba", 29.4925, 34.8969, margins(start = 0, sunrise = 0, fajr = 96, dhuhr = 97, asr = 98, maghrib = 99, isha = 96), endOfEating = 5),
        // The 48 cities ESA's page prints that had no unit (ruling R163): at the app's point for the city where the app
        // lists it, else at ESA's own point fitted from its printed year (as Cairo's), and so 6th of October and Obour,
        // whose app points lie off it inside the reach; El Mahalla El Kubra and Baltim off-point (R164).
        town("Tanta", 30.78847, 31.00192),
        town("Mansoura", 31.03637, 31.38069),
        offPoint("El Mahalla El Kubra", 30.97063, 31.1669, national.copy(fajr = 31, dhuhr = 29, asr = 30, maghrib = 33, isha = 31), endOfEating = 5),
        town("Zagazig", 30.58768, 31.502),
        town("Asyut", 27.18096, 31.18368),
        town("Faiyum", 29.30995, 30.8418),
        town("Esna", 25.29336, 32.55402),
        town("Ismailia", 30.60427, 32.27225),
        town("6th of October", 29.95, 30.93),
        town("Damanhur", 31.03408, 30.46823),
        town("New Cairo", 30.03, 31.47),
        town("Minya", 28.09193, 30.75813),
        town("Beni Suef", 29.07441, 31.09785),
        town("Sohag", 26.55695, 31.69478),
        town("Qena", 26.16418, 32.72671),
        town("10th of Ramadan", 30.29636, 31.74633),
        town("Kafr El Sheikh", 31.11174, 30.93991),
        town("Obour", 30.23, 31.57),
        town("Delengat", 30.82796, 30.53552),
        town("Nag Hammadi", 26.04949, 32.24142),
        town("Safaga", 26.74906, 33.93891),
        town("El Hamul", 31.31146, 31.14766),
        offPoint("Baltim", 31.55582, 31.08918, national.copy(fajr = 30, sunrise = -2, asr = 0, maghrib = 6), endOfEating = 5),
        town("Sadat City", 30.36742, 30.50871),
        town("Badr", 30.136, 31.715),
        town("El Hammam", 30.84532, 29.38818),
        town("New Salhia", 30.63146, 31.93927),
        town("Ras Sudr", 29.60, 32.67),
        town("Abu Zenima", 29.05, 33.08),
        town("Berenice", 23.75, 35.50),
        town("Marsa Alam", 25.06, 34.88),
        town("El Alamein", 30.81, 28.95),
        town("Baris", 24.40, 30.50),
        town("Toshka", 22.77, 31.41),
        town("15th of May", 29.84, 31.37),
        town("New Administrative Capital", 29.95, 31.85),
        town("New Fashn", 28.81, 30.66),
        town("New Mansoura", 31.49, 31.41),
        town("New Nubaria", 30.66, 30.06),
        town("New Beni Mazar", 28.53, 30.95),
        town("New Port Said", 31.04, 32.61),
        town("New Bir al-Abd", 31.03, 32.98),
        town("New Toshka", 22.87, 31.58),
        town("Capital Gardens", 30.12, 31.90),
        town("New Rashid", 31.38, 30.37),
        town("New Sphinx", 30.11, 30.75),
        town("East Owainat", 22.72, 28.72),
        town("West Nubaria", 30.69, 29.66),
    )

    /**
     * The towns' own exceptions to class B's 2 min across their reaches (ruling R181, spec §6.3). Each town's own tables
     * are replayed across its circle as the app resolves each point (gate/eg-esa-reach.tsv, 13 Nov 2026: 8 bearings at
     * half and 95 % of the radius and each event's worst point of a sweep, the points another town takes left out):
     * never early and no end late anywhere. A start there is the later of the town's table and the place's own sun, and
     * where another town's reach holds the place too (ruling R165) of that town's table as well; an end the earlier of
     * them. So a town's minute at its own point reads 2 toward its reach's west edge, and 3 on some days at the towns
     * here: where two reaches overlap (Cairo's 30 km with 6th of October, Obour, New Cairo and the towns around it; the
     * Delta's towns), and where a town's table is printed from a point off the town (Baltim, Ras Gharib, Kharga, El
     * Mahalla El Kubra), whose own point already reads 2. Only the events the replay found past 2 are listed, each
     * town's own; Taba's sunrise and end of eating, already 3 min before its table at the town (ESA's point for Taba
     * lies about half a degree west), reach 4 toward the reach's east edge. A later start or an earlier end, never an
     * early one. Dropping the other town's table where a place's people follow one alone would tighten the overlaps'
     * ones, but which table they follow is not in ESA's pages, so both are kept.
     */
    private val reachLate: Map<String, Set<TimedEvent>> = mapOf(
        "6th-of-october" to setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
        "badr" to setOf(TimedEvent.FAJR),
        "baltim" to setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.ISHA, TimedEvent.END_OF_EATING),
        "beni-suef" to setOf(TimedEvent.ISHA),
        "cairo" to setOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA, TimedEvent.END_OF_EATING),
        "damanhur" to setOf(TimedEvent.ISHA),
        "damietta" to setOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "el-hamul" to setOf(TimedEvent.FAJR, TimedEvent.END_OF_EATING),
        "el-mahalla-el-kubra" to setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
        "faiyum" to setOf(TimedEvent.SUNRISE),
        "kafr-el-sheikh" to setOf(TimedEvent.END_OF_EATING),
        "kharga" to setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
        "mansoura" to setOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "new-administrative-capital" to setOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "new-bir-al-abd" to setOf(TimedEvent.FAJR, TimedEvent.MAGHRIB),
        "new-cairo" to setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
        "new-mansoura" to setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
        "new-nubaria" to setOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "new-port-said" to setOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "new-sphinx" to setOf(TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "obour" to setOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB),
        "port-said" to setOf(TimedEvent.SUNRISE),
        "rafah" to setOf(TimedEvent.END_OF_EATING),
        "ras-gharib" to setOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        "sadat-city" to setOf(TimedEvent.SUNRISE),
        "sidi-barrani" to setOf(TimedEvent.FAJR),
        "tanta" to setOf(TimedEvent.ISHA),
        "west-nubaria" to setOf(TimedEvent.SUNRISE, TimedEvent.ISHA, TimedEvent.END_OF_EATING),
    )

    private val ends = setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING)

    /** [reachLate]'s limits for a town: its later starts and its earlier ends, each said as what it is. */
    private fun reachLimits(unit: AuthorityUnit): List<LateLimit> {
        if (unit.id == "taba") {
            return listOf(
                LateLimit(
                    4,
                    "ESA computes Taba's table about half a degree west of Taba, and Taqwa keeps the earlier sunrise and " +
                        "dawn of the table and the place's own: up to 3 min before the table's in Taba, and 4 toward the " +
                        "east edge of its reach (its tables replayed across its reach, 13 Nov 2026)",
                    ends,
                ),
            )
        }
        val events = reachLate[unit.id].orEmpty()
        return listOfNotNull(
            (events - ends).takeIf { it.isNotEmpty() }?.let {
                LateLimit(
                    3,
                    "within ${unit.name}'s reach the start shown is the later of ${unit.name}'s table and the place's own " +
                        "sun, and where another town's reach holds the place too, of that town's table as well, so on some " +
                        "days it comes up to 3 min after ${unit.name}'s table (its tables replayed across its reach, 13 Nov 2026)",
                    it,
                )
            },
            events.intersect(ends).takeIf { it.isNotEmpty() }?.let {
                LateLimit(
                    3,
                    "within ${unit.name}'s reach the sunrise and the end of eating shown are the earlier of ${unit.name}'s " +
                        "table and the place's own, and where another town's reach holds the place too, of that town's table " +
                        "as well, so on some days they come up to 3 min before ${unit.name}'s table (its tables replayed " +
                        "across its reach, 13 Nov 2026)",
                    it,
                )
            },
        )
    }

    private val towns = placed.map { it.copy(lateLimits = reachLimits(it)) }.also { all ->
        require(all.map { it.id }.containsAll(reachLate.keys)) { "eg.esa: a reach exception for a town that is not a unit" }
    }

    val units = UnitSet(
        "eg.esa",
        towns,
        around = { user, unit, placed ->
            val others = towns.filter { it.id != unit.id && distanceKm(user, it.point) <= it.radiusKm }
            if (others.isEmpty()) {
                placed
            } else {
                // A town with margins of its own rides as its own day at its own point; the others share this method.
                val (own, shared) = others.partition { it.method != null || unit.method != null }
                placed.copy(
                    envelopePoints = (placed.envelopePoints + shared.map { it.point }).distinct(),
                    alongside = placed.alongside + own.map { Alongside(it.point, it.method ?: method) },
                )
            }
        },
    ) { user ->
        val edge = method.atEdge("eg.esa.edge", national.widened(EDGE_START_SECONDS, sunrise = -EDGE_END_SECONDS))
        val near = beside(user)
        if (near.isEmpty()) edge else edge.copy(alongside = edge.alongside + near.map { Alongside(it.point, it.method ?: method) })
    }

    /**
     * The towns whose tables ride beside the edge at [user] (ruling R213, the audit of 12 Nov 2026, finding F13): every
     * town within [BESIDE_REACHES] of its own reach, nearest first. Just past a town's reach the place had come back to
     * its own sun and the edge's allowances alone, which are sized for a town ESA does not print, not for the printed
     * town next door: west of Ras Gharib and Sidi Barrani, whose tables end the fast and the dawn prayer up to a minute
     * before the edge's allowance, sunrise and the end of eating came a minute after the town's table on some days, as
     * they did somewhere in the 20 km past the reach of around 73 of the 78 towns (the place's own sun runs later
     * than the town's to its west), and east of Taba, Kharga, El Mahalla El Kubra and Baltim, whose own starts exceed
     * the edge's, a start came before it. So out to twice its reach a town's own day at its own point is
     * a day alongside ([Alongside]): the place's own day exactly as before, and the day shown the later start and the
     * earlier end of it and each such town's. That holds where no town's reach holds the place, and there alone: a
     * place inside one town's reach follows that town's table and no other's (ruling R165), however near the next
     * town stands, and can be a minute from the next town's.
     *
     * Past every belt (ruling R260, the second audit's G2, 14 Nov 2026), where no town rides by the rule above: the
     * nearest town's own day, and every town about as near ([ridingBeyond]: no more than a quarter farther, out to
     * five minutes of longitude from the town's point, about 120 km). The belt's two reaches were a judgement that
     * farther out "the town's table is no longer the place's", and it left the app's an-Najaylah, 62 km from Marsa
     * Matruh's point, ending the fast after Marsa Matruh's printed Fajr on every day of 2026 (by 2 min on 78) and
     * beginning Maghrib a minute before Sidi Barrani's (66 km) on 165; Kom Ombo, Mallawi, Dayrut, al-Qusiyah, Dayr
     * Mawas and Zaafarana stand there too. ESA prints by town, so the nearest printed town is the one such a place
     * may follow, and both where two stand about as near. Farther than five minutes of longitude, the edge alone.
     * And where some towns' belts hold the place but the nearest town's does not (ruling R262, 15 Nov 2026: half a
     * kilometre past Tanta's belt, inside Cairo's, the end of eating was a minute after Tanta's printed Fajr on some
     * days), the nearest town and any about as near ride beside the belt's towns ([withNearestBeyond]).
     */
    fun beside(user: GeoPoint): List<AuthorityUnit> =
        towns.map { it to distanceKm(user, it.point) }.filter { (town, km) -> km <= town.radiusKm * BESIDE_REACHES }
            .sortedBy { it.second }.map { it.first }
            .let { inner -> withNearestBeyond(inner, towns, user) }

    val entry: RegistryEntry = single(
        id = "eg.esa", nameKey = "authority_esa", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("EG"),
    )

    /**
     * The old picker's Egyptian method anywhere: 19.5°/17.5°, Dhuhr +1 (adhan2's preset), ±30 s (ruling
     * R31), and Asr [ADHAN2_ASR_ALLOWANCE] later: adhan2 0.0.7 takes one declination for the whole
     * day, so its Asr runs up to about 34 s after this core's (docs/research/2026-09-prayer-times/
     * proof/7h-other-and-default.md). Against the old adhan2 engine at twelve places, every day of
     * 2026–2027 in both schools, this method's Standard Asr was a minute early on 19 of 8,760 days
     * (London 17, New York 2); 0 with the allowance.
     */
    val other: RegistryEntry = single(
        id = "other.egyptian", nameKey = "method_egyptian", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "other.egyptian",
            fajrAngle = 19.5,
            isha = IshaRule.Angle(17.5),
            authorityMinutes = EventOffsets(dhuhr = 1),
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(entry, other)

    /** Beyond every unit, starts: Taba's own margins exceed the national ones by up to 103 s. */
    private const val EDGE_START_SECONDS = 105

    /** Beyond every unit, sunrise and the end of eating: Ras Gharib's need 42 and 54 s; a minute. */
    private const val EDGE_END_SECONDS = 60

    /** How many of its own reaches a town's table rides beside the edge ([beside], ruling R213): 40 km, Cairo's 60. */
    const val BESIDE_REACHES = 2.0
}
