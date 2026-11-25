package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.CALCULATED_NAME
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import kotlin.math.abs

/**
 * The old picker's methods as global Other methods (controller ruling R12; Tehran has none), and the
 * class D safe default where nothing is known (spec §6.2 c).
 *
 * Other methods follow the adhan2 presets the old picker used, on this core, with the first-guess
 * safe rounding (starts +30 s, sunrise and the end of eating −30 s; ruling R31, Task 7h), never an
 * authority's fitted margins. Their [RegistryEntry.school] is neutral: the
 * school that leads comes from the place's Automatic entry or the user (Task 8).
 *
 * Asr carries the extra [ADHAN2_ASR_ALLOWANCE]: adhan2 0.0.7 always takes the day's declination
 * once (at its `SolarCoordinates(julianDay)`, not iterated to the Asr moment), which the proof
 * test (`OtherMethodsProofTest`) measured up to about 34 s later than this core's iterated,
 * exact-moment Asr (the Maghreb-Libya research: adhan2 minus first-principles Asr is −37..+34 s).
 * The plain 30 s safe start would then not always reach adhan2's own Asr; only `other.turkey`
 * (`Diyanet.other`) is exempt, since it reuses Diyanet's own `AsrModel.DAILY_0H_UT`, the same
 * fixed-declination approach adhan2 uses there.
 */
object Generic {

    private val safeRounding = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE)

    private fun preset(id: String, nameKey: String, fajr: Double, isha: Double, dhuhrMinutes: Int = 1) = single(
        id = id, nameKey = nameKey, entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = id, fajrAngle = fajr, isha = IshaRule.Angle(isha),
            authorityMinutes = EventOffsets(dhuhr = dhuhrMinutes), margins = safeRounding, endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /** Muslim World League: 18°/17°, Dhuhr + 1. */
    val mwl = preset("other.mwl", "method_muslim_world_league", 18.0, 17.0)

    /** ISNA (FCNA): 15°/15°, Dhuhr + 1. */
    val isna = preset("other.isna", "method_isna", 15.0, 15.0)

    /** University of Islamic Sciences, Karachi: 18°/18°, Dhuhr + 1. */
    val karachi = preset("other.karachi", "method_karachi", 18.0, 18.0)

    /**
     * Moonsighting Committee Worldwide (moonsighting.com, how-we.html; adhan2's preset): Zuhr + 5,
     * Maghrib + 3, Fajr the later of 18° and the seasonal morning function, Isha the earlier of 18°
     * and the seasonal evening function (shafaq general); from 55° of latitude a seventh of the night
     * takes 18°'s place, north and south, as the Committee's own tables have it (adhan2 has it north
     * only; TwilightCurves.moonsighting, T3 round of 11 Oct 2026). The functions are minutes from
     * sunrise or sunset, so they are expressed here as day-of-year angle curves for the place's
     * latitude and longitude ([PlaceCurves]), at every resolution. The entry's own method carries none:
     * no resolution reads it unplaced, and its equator curves made every user's first use of the
     * registry run the Committee's whole read (a leap cycle's sun every 6 h, eight leap-cycle reads,
     * 12–14 ms cold on the Mac's JVM), for a method few choose (the T3 review of 11 Oct 2026).
     */
    private val moonsightingBase = TimetableMethod(
        id = "other.moonsighting",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 3),
        margins = safeRounding,
        endOfEatingMarginSeconds = SAFE_END,
    )

    val moonsighting = single(
        id = "other.moonsighting", nameKey = "method_moonsighting", entryClass = EntryClass.D_AUTHORITY,
        method = moonsightingBase, school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    // The class D safe default (spec §6.2 c).

    /**
     * A region's conventions for the safe default: the Fajr prayer at the latest dawn convention in
     * use there (18° where none is known), the end of eating 2 min before the earliest, Isha at the
     * latest convention + 2, sunrise − 2, Dhuhr, Asr and Maghrib + 2 (MWL Fiqh Council, 1986), and
     * larger minutes where a published table nearby prints them (§6.2 d: every published data point
     * is a floor). All with the first-guess 30 s.
     *
     * Fajr, sunrise, Dhuhr and Maghrib carry [EXTRA_SAFETY_SECONDS] on top of that 30 s: the gate
     * (Task 7h §3, `safe-default.tsv`) found `default.safe` up to a minute early or a minute-late
     * sunrise against JAKIM's own printed Subuh (Kuala Lumpur) and Kemenag's own Dhuhr/Maghrib/sunrise
     * (Jakarta), even though both authorities' own conventions are the same nominal figures this
     * region assumes (JAKIM's 18°; Kemenag's own +2 min) — a few tens of seconds of point/ephemeris
     * difference the plain 30 s did not cover. Not spec-mandated minutes (unlike the policy figures
     * below): a plain safety cushion, widened here per the observed excess (task-7-common's "widen by
     * the excess plus 5 s"), the same way [ADHAN2_ASR_ALLOWANCE] widens Asr's above.
     */
    class SafeRegion(
        val id: String,
        val latestFajr: Double,
        val earliestFajr: Double,
        val latestIsha: Double,
        val fajrAfterDawnMinutes: Int = 0,
        val horizonDeg: Double = -0.8333,
        val sunriseMinutes: Int = -2,
        val dhuhrMinutes: Int = 2,
        val asrMinutes: Int = 2,
        val maghribMinutes: Int = 2,
        val ishaMinutes: Int = 2,
        val fajrExtraSeconds: Int = 0,
        val fixedDhuhrLocalMinutes: Int? = null,
        val fixedAsrLocalMinutes: Int? = null,
        val ishaFloorMinutesAfterMaghrib: Int? = null,
        val endOfEating: EndOfEating = EndOfEating.DawnAngle(earliestFajr),
        val endOfEatingMarginSeconds: Int = -120,
    ) {
        val method = TimetableMethod(
            id = id,
            fajrAngle = latestFajr,
            isha = IshaRule.Angle(latestIsha),
            horizonDeg = horizonDeg,
            fajrAfterDawnMinutes = fajrAfterDawnMinutes,
            margins = margins(
                start = SAFE_START, sunrise = sunriseMinutes * 60 + SAFE_SUNRISE - EXTRA_SAFETY_SECONDS,
                fajr = SAFE_START + EXTRA_SAFETY_SECONDS + fajrExtraSeconds,
                dhuhr = dhuhrMinutes * 60 + SAFE_START + EXTRA_SAFETY_SECONDS, asr = asrMinutes * 60 + SAFE_START,
                maghrib = maghribMinutes * 60 + SAFE_START + EXTRA_SAFETY_SECONDS, isha = ishaMinutes * 60 + SAFE_START,
            ),
            endOfEating = endOfEating,
            endOfEatingMarginSeconds = endOfEatingMarginSeconds,
            fixedDhuhrLocalMinutes = fixedDhuhrLocalMinutes,
            fixedAsrLocalMinutes = fixedAsrLocalMinutes,
            ishaFloorMinutesAfterMaghrib = ishaFloorMinutesAfterMaghrib,
        )
    }

    /** Nothing known: 18° throughout. */
    val world = SafeRegion("default.safe", 18.0, 18.0, 18.0)

    /**
     * Latin America and the Caribbean: FCNA's 15° is the latest dawn in use in the Americas, MWL's 18° the earliest. From
     * [AMERICAS_FAR_LATITUDE] the Moonsighting Committee's seasonal dawn can be later still, and its day stands beside the
     * region's there ([americasAt]).
     */
    val americas = SafeRegion("default.safe.americas", 15.0, 18.0, 18.0)

    /**
     * The latitude, north or south, from which a place of [americas] takes the Moonsighting Committee's day beside the
     * region's own ([americasAt], ruling R247). The Committee's dawn at a place's own point first comes after the
     * region's 15° dawn, by seconds, near 43.9° N and 44.0° S (the review of 14 Nov 2026), and the region's finished
     * Fajr, with its cushion, is before the Committee's on no day of 2026 or 2027 up to 43.9° of either hemisphere
     * (`AmericasFarLatitudeTest` holds it below the band). The band begins nearly half a degree before that crossing,
     * so that it does not rest on those seconds; no app city stands between 43.5° and 44°, and a place there has its
     * Dhuhr up to a minute later for it (the Committee's + 5) and nothing else.
     */
    const val AMERICAS_FAR_LATITUDE = 43.5

    /**
     * The Americas' safe default at [user] (ruling R247, the audit's finding F18): the region's own method up to
     * [AMERICAS_FAR_LATITUDE], and from there the Moonsighting Committee's day at the user's own point beside it, a day
     * alongside ([Alongside]): each start the later of the two, sunrise, the end of eating and every end the earlier.
     *
     * Why: the region's premise, "the 15° dawn is the latest in use in the Americas", fails in the far south. From about
     * 51.6° S the 15° sign is missing around midsummer, and on the nights it returns it stands near the middle of the
     * night: at Ushuaia the region's Fajr fell by 121 min from one night of January 2026 (the high-latitude rule) to the
     * next (the sign), to well over an hour before the Fajr of the Committee's own table for the town
     * (`archive/tables/manual/mcw/2026-10-11/ushuaia-2026.txt`), which no rule of the region's then held back; and the
     * Committee's seasonal dawn, the later of 18° and its seasonal function, is after 15° through the far south's winter
     * and from about 44° in summer. The Committee's is a calculator's table, not a timetable anyone there is known to
     * follow, but it is the one published high-latitude rule in wide use in the Americas, so a start before it is not
     * excused. What stands beside the region's day is the app's model of the Committee's rule ([moonsightingBase]), not
     * the Committee's own figures: the model is checked against the Committee's calculator, a start never before it,
     * at nine points from 43.9° to 56° S and at Saint-Pierre (46.8° N), seven of the ten fetched after the band stood
     * (`gate/safe-default-americas-promise.tsv`).
     */
    internal fun americasAt(user: GeoPoint): TimetableMethod =
        if (abs(user.lat) < AMERICAS_FAR_LATITUDE) {
            americas.method
        } else {
            americas.method.copy(alongside = listOf(Alongside(user, moonsightingBase)))
        }

    /**
     * Europe without an authority or a mosque survey (Spain, Portugal, Italy …): the Paris 12–13°
     * family is the latest dawn in wide European use, 18° the earliest; its Zuhr + 5 and Maghrib + 4
     * (78 % of Île-de-France mosques) as floors.
     */
    val europe = SafeRegion("default.safe.europe", 12.0, 18.0, 18.0, dhuhrMinutes = 5, maghribMinutes = 4)

    /**
     * The Balkans and their neighbours without a checked authority (North Macedonia, Serbia outside
     * Sandžak, Croatia, Slovenia, Bulgaria, Greece, Romania, Cyprus, Moldova): the latest Fajr in the
     * region is Kosovo's Sabah (imsak ≈ 19° + 20 min), the earliest dawn 19°, the latest Isha 18.5°;
     * Diyanet's temkin (sunrise − 7, Dhuhr + 5, Asr + 4) and Montenegro's Maghrib + 8.5 as floors.
     */
    val balkans = SafeRegion(
        "default.safe.balkans", 19.0, 19.0, 18.5, fajrAfterDawnMinutes = 20,
        sunriseMinutes = -7, dhuhrMinutes = 5, asrMinutes = 4, maghribMinutes = 9,
    )

    /**
     * Africa south of the Sahara without an authority: 15° Fajr is in use (Abuja, Dakar), 19.5° the
     * earliest dawn (Egypt, Mauritania); Jamiat's and Darul Ihsan's perpetual tables, printed for
     * towns across the continent, add Dhuhr + 5 and Maghrib + 3.
     */
    val africa = SafeRegion("default.safe.africa", 15.0, 19.5, 18.0, dhuhrMinutes = 5, maghribMinutes = 3)

    /**
     * Côte d'Ivoire: Africa's conventions with COSIM's own printed times as floors (the safe defaults lane, 12 Nov
     * 2026, `gate/safe-default-africa.tsv`'s Abidjan rows). COSIM, the national council of imams, prints Zouhr at
     * "13 Heures" and Asr at "16 Heures", each as "Heure conventionnelle en Côte d'Ivoire", the same for the whole
     * country (its month calendar of Joumada al-Oula 1448, 12 Oct to 10 Nov 2026): so Dhuhr is never before 13:00
     * and Asr never before 16:00 (Africa's region came 49 to 50 and 37 min before them at Abidjan). Its last Ramadan
     * edition held (1436, 2015) ends the meal at an imsak 25 min before its Soubh (1434's was 15): so the fast begins
     * 25 min before the region's 15° dawn, COSIM's own construction, and 2 min before that again, the region's own
     * margin for an end (the region's 19.5° end, 2 min before that dawn, came after the 1436 imsak on every day
     * held). With no margin the end was 0 to 1 min before the 1436 edition's imsak, at least 40 s on every day, which
     * is true of that edition alone: on the Soubh COSIM prints for 1448 the same rule came a minute AFTER Soubh − 25 on
     * three days of November and December 2026 (the lane's first review, 14 Nov 2026; the fitter asks for 38 s over the
     * tables held). So the margin is the 2 min every region takes, not a figure fitted to the months held: the end of
     * eating is 2 min earlier than the lane first built it, on every day, and `gate/ci-default.tsv` holds the rule on the
     * Soubh of both 1448 months at every town COSIM prints.
     * The conventional 13:00 and 16:00 hold for the whole country as printed; COSIM moves its other times by town
     * ([coteDIvoireAt]).
     */
    val coteDIvoireRegion = SafeRegion(
        "ci.default", 15.0, 19.5, 18.0, dhuhrMinutes = 5, maghribMinutes = 3,
        fixedDhuhrLocalMinutes = 13 * 60, fixedAsrLocalMinutes = 16 * 60,
        endOfEating = EndOfEating.MinutesBeforeFajr(25), endOfEatingMarginSeconds = -120,
    )

    /**
     * One town an authority moves by printed whole minutes after its base town (COSIM's "correspondances horaires" after
     * Abidjan, the Tajik Council of Ulama's footnote after Dushanbe): [startMinutes] is the latest figure any edition held
     * prints for it and [endMinutes] the earliest (the same where the editions agree), so a start is held to the latest
     * and an end to the earliest. [point] is the app's own city of that name; where the town is not an app city it is the
     * town's point as the lane knew it, good to about [uncertainKm] ([APPROXIMATE_KM], or [UNCERTAIN_KM] for the villages
     * it knew least), which [printedTownsNear] allows for.
     */
    private class PrintedTown(
        val name: String,
        val point: GeoPoint,
        val startMinutes: Int,
        val endMinutes: Int = startMinutes,
        val uncertainKm: Double = 0.0,
    )

    /**
     * The printed towns whose figures bind at [user] (the trust audit's F3 and F5, 13 Nov 2026; ruling R208): every town
     * that may be the nearest one, each point taken as off by up to its [PrintedTown.uncertainKm] either way (so the
     * nearest alone where every point is the app's own). A person standing in a town the authority prints is then held to that town's
     * figure even where the lane's point for it is off by as much as that, and a neighbour's lower figure never stands
     * in for it: of the towns kept, starts take the largest minutes and ends the smallest.
     */
    private fun printedTownsNear(towns: List<PrintedTown>, user: GeoPoint): List<PrintedTown> {
        val distances = towns.map { distanceKm(user, it.point) }
        val nearestAtMost = towns.indices.minOf { distances[it] + towns[it].uncertainKm }
        return towns.filterIndexed { index, town -> distances[index] - town.uncertainKm <= nearestAtMost }
    }

    /** Abidjan, the point COSIM prints its calendars for (the app's own city, `gate/ci-default.tsv`'s Abidjan rows). */
    private val ABIDJAN = GeoPoint(5.35444, -4.00167)

    /**
     * COSIM's "correspondances horaires" (its Ramadan 1434 and 1436 editions, the same in both, read by
     * archive/code/manual/ci-cosim/cosim_tables.py): its times hold as printed for Abidjan, Adzopé, Afféry, Agboville,
     * Anyama, Bingerville, Bongouanou, Daoukro and Prikro, and every other town it lists takes them a printed whole number
     * of minutes earlier or later, from Bondoukou's − 6 to Danané's + 18. Every town of them is here (the trust audit's F3
     * and F5: the twenty-one the app does not list were left out, and a person there took a neighbour's figure, Madinani
     * Kolia's + 10 for its own + 14, a Fajr a minute before COSIM's Soubh): the 46 the app lists at the app's own point
     * (COSIM's "Borotou" is the app's Borotou-Koro, its "Hounien" Zouan-Hounien), with Abidjan itself, and Prikro, Alépé,
     * Nassian, Sinématiali, Tieningboué, Kani, Morondo, Dioulatchédougou, Djibrosso, Guintéguéla, Madinani, Goulia, Tiémé,
     * Bako, Booko, Tienko, Seydougou, Koonan, Toulépleu, Binhouyé and Koro at the point the lane knew for each, marked
     * approximate (no gazetteer was to hand: an open item in BUILD-LOG).
     */
    private val cosimTowns = listOf(
        PrintedTown("Abidjan", ABIDJAN, 0),
        PrintedTown("Adzopé", GeoPoint(6.10694, -3.86194), 0),
        PrintedTown("Affery", GeoPoint(6.32035, -3.95235), 0),
        PrintedTown("Agboville", GeoPoint(5.92801, -4.21319), 0),
        PrintedTown("Anyama", GeoPoint(5.49462, -4.05183), 0),
        PrintedTown("Bingerville", GeoPoint(5.35581, -3.88537), 0),
        PrintedTown("Bongouanou", GeoPoint(6.65175, -4.20406), 0),
        PrintedTown("Daoukro", GeoPoint(7.0591, -3.9631), 0),
        PrintedTown("Abengourou", GeoPoint(6.72972, -3.49639), -2),
        PrintedTown("Bonoua", GeoPoint(5.27247, -3.59625), -2),
        PrintedTown("Adiaké", GeoPoint(5.28634, -3.30403), -4),
        PrintedTown("Aboisso", GeoPoint(5.46779, -3.20711), -4),
        PrintedTown("Agnibilékrou", GeoPoint(7.13113, -3.20415), -4),
        PrintedTown("Tanda", GeoPoint(7.80335, -3.16832), -4),
        PrintedTown("Bouna", GeoPoint(9.26927, -2.9951), -4),
        PrintedTown("Bondoukou", GeoPoint(8.0402, -2.80003), -6),
        PrintedTown("Bouaké", GeoPoint(7.69385, -5.03031), 4),
        PrintedTown("Grand-Lahou", GeoPoint(5.13825, -5.02123), 4),
        PrintedTown("Katiola", GeoPoint(8.13728, -5.10095), 4),
        PrintedTown("Toumodi", GeoPoint(6.55799, -5.01769), 4),
        PrintedTown("Yamoussoukro", GeoPoint(6.82055, -5.27674), 6),
        PrintedTown("Oumé", GeoPoint(6.38309, -5.41759), 6),
        PrintedTown("Divo", GeoPoint(5.83739, -5.35723), 6),
        PrintedTown("Ferkessédougou", GeoPoint(9.5928, -5.19449), 6),
        PrintedTown("Bouaflé", GeoPoint(6.99041, -5.7442), 8),
        PrintedTown("Lakota", GeoPoint(5.84752, -5.682), 8),
        PrintedTown("Gagnoa", GeoPoint(6.13193, -5.9506), 8),
        PrintedTown("Korhogo", GeoPoint(9.45803, -5.62961), 8),
        PrintedTown("Sassandra", GeoPoint(4.95384, -6.08531), 10),
        PrintedTown("Guibéroua", GeoPoint(6.23869, -6.17147), 10),
        PrintedTown("Zuénoula", GeoPoint(7.43027, -6.05054), 10),
        PrintedTown("Tengréla", GeoPoint(10.48115, -6.40694), 10),
        PrintedTown("Kolia", GeoPoint(9.7651, -6.46372), 10),
        PrintedTown("Mankono", GeoPoint(8.05861, -6.18972), 10),
        PrintedTown("San-Pédro", GeoPoint(4.74851, -6.6363), 12),
        PrintedTown("Daloa", GeoPoint(6.87735, -6.45022), 12),
        PrintedTown("Séguéla", GeoPoint(7.96111, -6.67306), 12),
        PrintedTown("Soubré", GeoPoint(5.78377, -6.59388), 12),
        PrintedTown("Tabou", GeoPoint(4.42295, -7.3528), 14),
        PrintedTown("Touba", GeoPoint(8.28333, -7.68333), 16),
        PrintedTown("Man", GeoPoint(7.41251, -7.55383), 16),
        PrintedTown("Borotou-Koro", GeoPoint(8.49111, -7.17861), 16),
        PrintedTown("Duekoué", GeoPoint(6.74202, -7.34918), 16),
        PrintedTown("Guiglo", GeoPoint(6.54368, -7.4935), 16),
        PrintedTown("Odienné", GeoPoint(9.50511, -7.56433), 16),
        PrintedTown("Danané", GeoPoint(7.25957, -8.15498), 18),
        PrintedTown("Zouan-Hounien", GeoPoint(6.91937, -8.2065), 18),
        PrintedTown("Prikro", GeoPoint(7.65, -3.99), 0, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Alépé", GeoPoint(5.50, -3.66), -2, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Nassian", GeoPoint(8.45, -3.47), -2, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Sinématiali", GeoPoint(9.58, -5.38), 6, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Tiéningboué", GeoPoint(8.18, -5.72), 8, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Kani", GeoPoint(8.48, -6.60), 12, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Morondo", GeoPoint(8.95, -6.78), 12, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Dioulatiédougou", GeoPoint(9.07, -7.03), 14, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Djibrosso", GeoPoint(8.75, -6.93), 14, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Guintéguéla", GeoPoint(8.20, -7.35), 14, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Madinani", GeoPoint(9.6167, -6.95), 14, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Goulia", GeoPoint(10.02, -7.20), 14, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Tiémé", GeoPoint(9.55, -7.32), 14, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Bako", GeoPoint(9.13, -7.60), 16, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Booko", GeoPoint(8.95, -7.83), 16, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Tienko", GeoPoint(10.08, -7.47), 16, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Seydougou", GeoPoint(9.60, -7.95), 18, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Koonan", GeoPoint(8.63, -7.83), 18, uncertainKm = UNCERTAIN_KM),
        PrintedTown("Toulépleu", GeoPoint(6.58, -8.41), 18, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Bin-Houyé", GeoPoint(6.78, -8.32), 18, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Koro", GeoPoint(8.55, -7.46), 16, uncertainKm = APPROXIMATE_KM),
    )

    /**
     * Côte d'Ivoire's method at [user] (the safe defaults lane's review, 12 Nov 2026): [coteDIvoireRegion] at the user's
     * own point, and beside it COSIM's construction of the town it prints there ([printedTownsNear]: the nearest, and any
     * town whose point the lane only knew roughly and that may be the user's own; rulings R103, R113 and R118's
     * discipline), as BIK's town figures ride in Kosovo (Balkans.kosovoUnits): a start
     * point at Abidjan's latitude [COSIM_SPARE_MINUTES] farther west than the town's minutes, so every start is never
     * before the region's own at Abidjan plus the town's minutes, and an end point as much less far, so sunrise, the
     * sunset and the end of eating are never after the region's at Abidjan plus them. The region's day at Abidjan is
     * gated never early and never a late end against COSIM's own (`gate/ci-default.tsv`), so a town's day is never early
     * against Abidjan's printed times moved by its figure, COSIM's own construction, whatever the town's latitude. COSIM
     * moves a town by whole minutes, not by its sun, so the town's own sun drifts against them with the seasons: without
     * this the region's own Fajr came before the derived Soubh of 24 of the 46 towns in the Ramadan editions held (June to
     * August), up to 8 min at Tengréla and 7 at Korhogo, Ferké, Odienné and Borotou-Koro, and its end of eating a minute
     * after the derived imsak of Adiaké and Tabou (the derived rows of `gate/ci-default.tsv`); in the northern winter the
     * town's own sunset comes before COSIM's Maghreb moved by its figure (the review's model: 2 to 3 min at Korhogo, Ferké,
     * Tengréla and Odienné; no table held for those months). The user's own point still bounds every time as before:
     * nothing is earlier, and no end later, than the region gave at the user's point alone
     * ([TimetableMethod.endsBoundAtUserPoint]). The start point bounds Dhuhr and Asr as well, which COSIM keeps national
     * (13:00 and 16:00, never moved by a figure): Dhuhr never moves (Abidjan's transit plus 18 min stays before 13:00), Asr
     * only where Abidjan's sun plus the town's minutes is later than 16:00 and the user's own (the cost in BUILD-LOG).
     */
    internal fun coteDIvoireAt(user: GeoPoint): TimetableMethod {
        val near = printedTownsNear(cosimTowns, user)
        return coteDIvoireRegion.method.copy(
            startPoints = listOf(movedFrom(ABIDJAN, near.maxOf { it.startMinutes } + COSIM_SPARE_MINUTES)),
            endPoints = listOf(movedFrom(ABIDJAN, near.minOf { it.endMinutes } - COSIM_SPARE_MINUTES)),
            endsBoundAtUserPoint = true,
        )
    }

    /** The point at [base]'s latitude whose sun runs [minutes] of time after [base]'s (west of it; east when negative). */
    private fun movedFrom(base: GeoPoint, minutes: Double) = GeoPoint(base.lat, base.lon - minutes * DEG_PER_MINUTE)

    /** South-east Asia without a checked authority: JAKIM's 18° is the latest Subuh, the 20° of MUIS, Kemenag and MORA the earliest. */
    val southeastAsia = SafeRegion("default.safe.seasia", 18.0, 20.0, 18.0)

    /**
     * Israel: the Levant's conventions (Jordan's and the PA's −2.25° horizon, Lebanon's 20° dawn, Jordan's 18.2° Isha).
     * Its Fajr carries [fajrExtraSeconds] 138 s more (the safe defaults lane, 12 Nov 2026): the PA's Dar al-Iftaa prints
     * its al-Aqsa table for thirteen towns in Israel by a whole-minute figure each (Nazareth + 0 to Jaffa + 2), and north
     * of Jerusalem in summer the town's own 18° dawn with the region's cushion came up to 133 s before it, a minute
     * early on the clock on 474 of 9,490 place-days (Acre, Safed, Nazareth… in May to July); 133 + 5 s.
     */
    val levant = SafeRegion("default.safe.levant", 18.0, 20.0, 18.2, horizonDeg = -2.25, fajrExtraSeconds = 138)

    /** South Asia beyond Pakistan, India and Bangladesh: Karachi's 18°/18° with IFB's Dhuhr and Maghrib + 3. */
    val southAsia = SafeRegion("default.safe.southasia", 18.0, 18.0, 18.0, dhuhrMinutes = 3, maghribMinutes = 3)

    /**
     * Central Asia without a table (Tajikistan, Turkmenistan): QMDB's 15° is the latest Fajr, the
     * Kyrgyz Muftiate's 18° the earliest, its 16° the latest Isha; the Uzbek Board's former Dhuhr + 5
     * and the Kyrgyz Maghrib + 7 as floors.
     */
    val centralAsia = SafeRegion("default.safe.centralasia", 15.0, 18.0, 16.0, dhuhrMinutes = 5, maghribMinutes = 7)

    /**
     * Tajikistan: Central Asia's conventions with the Council of Ulama's own printed times as floors (the safe defaults
     * lane, 12 Nov 2026, and the trust audit's F16, 13 Nov 2026; `gate/tj-default.tsv`). Held of the Council
     * (shuroiulamo.tj): its month page for Dushanbe of November 2026 and its nine Ramadan calendars of 2018 to 2026
     * (February to June), each with a footnote that moves every time by a printed figure per town ([tajikistanAt]).
     *
     * - Dhuhr: never before 12:45 in the whole country, a national floor read off the one month page held; a floor at
     *   each town's own Peshin would be less late, most at Khorugh and Murghob, and waits for a second month to print the
     *   same minute.
     * - Maghrib + 13, shown 16 min after the sunset with the cushion: Shom is 7 to 13 min after the sunset over the
     *   editions, and the 13 rests on one of them, the June days of 2018; the 2026 edition prints the sunset + 7 to 8.5,
     *   so against the newest calendar Maghrib is shown about 8 min later than it prints.
     * - Isha never before Maghrib + 90 (Khufton is Shom + 90 on every day of the month held), and never before the 18°
     *   dusk + 2 either: the Ramadan calendars print no Khufton, so nothing says what the Council prints from spring to
     *   autumn, when Maghrib + 90 comes before the 18° dusk + 2 (on 87 days of the year at Dushanbe, by up to 15 min
     *   in June, and on about 100 in Sughd, by up to 22); the later of the two until a summer month is held. It was
     *   Central Asia's 16°.
     * - Fajr at the 15° dawn: the Council's Subhi sodiq sits between 16.2° (June 2018) and 19.4° (March 2025).
     * - The end of eating 2 min before the 19.5° dawn, so that it is never after any edition's Subhi sodiq: at Central
     *   Asia's 18° it came after the 2025 edition's on all 29 days, by up to 5 min, and on the minute of the 2024 and 2026
     *   editions' on thirteen days.
     * - Sunrise − 9, shown 12 min before the sun's own with the cushion: the 2021 edition prints the sunrise up to
     *   10 min before the sun's own, 2019's up to 9, 2018's and 2025's up to 6; at − 2 the sunrise shown came after the
     *   printed one on 32 of the 239 days held, by up to 5 min. The − 9 rests on one edition, 2021's: at Dushanbe the
     *   sunrise shown is up to 17 min before the one its other calendars print, the cost of binding every edition.
     *
     * Three cells of the editions are misprinted by a whole hour (the Shom of 27 May 2019 and the Bomdod of 26 and 27 Feb
     * 2026); each is read with the hour put right, the reading named in the table and bound as the print would be
     * (archive/code/manual/tj-council/council_ramadan.py). None of the tables is held out: every margin here was read
     * off them, so the gate proves the build holds them, not that a calendar nobody has seen will agree.
     *
     * The editions are hand-built and do not agree with one another from year to year; every one is bound, the safe
     * side, at the cost BUILD-LOG gives (an open question for the owner). Without the first lane's floors, Dhuhr came 23
     * to 33 min before the Council's, Isha 18 to 25 and Maghrib up to a minute. Turkmenistan keeps [centralAsia]: no table
     * is held there.
     */
    val tajikistanRegion = SafeRegion(
        "tj.default", 15.0, 19.5, 18.0, sunriseMinutes = -9, dhuhrMinutes = 5, maghribMinutes = 13,
        fixedDhuhrLocalMinutes = 12 * 60 + 45, ishaFloorMinutesAfterMaghrib = 90,
    )

    /** Dushanbe, the point the Council of Ulama prints its tables for (the app's own city, `gate/tj-default.tsv`). */
    private val DUSHANBE = GeoPoint(38.53575, 68.77905)

    /**
     * The towns of the Council of Ulama's footnotes, each with the minutes its times come after Dushanbe's (before, when
     * negative), read by archive/code/manual/tj-council/council_month.py and council_ramadan.py. The footnote changed
     * over the years: the calendars of 2020 to 2023 and 2025 print one set (Khujand − 7, Konibodom − 9 …), those of 2024
     * and 2026 and the month page of November 2026 another (Khujand − 3, Konibodom − 6 …), and Isfara, Muminobod,
     * Shahritus and Ayni differ again between them. Nothing says which set a mosque follows, so both bind: a start is held
     * to the latest figure printed for the town and an end to the earliest ([PrintedTown.startMinutes],
     * [PrintedTown.endMinutes]). Ten are app cities, at the app's own point; Rasht (Gharm), Asht (Shaydon), Murghob,
     * Hamadoni, Sh. Shohin, Muminobod, Shahritus, Ayni and N. Khusrav are not, and stand at the point the lane knew for
     * each, marked approximate.
     */
    private val councilTowns = listOf(
        PrintedTown("Dushanbe", DUSHANBE, 0),
        PrintedTown("Istaravshan", GeoPoint(39.9142, 69.00328), -1, -5),
        PrintedTown("Kŭlob", GeoPoint(37.91459, 69.78454), -4, -5),
        PrintedTown("Khujand", GeoPoint(40.28256, 69.62216), -3, -7),
        PrintedTown("Konibodom", GeoPoint(40.29414, 70.43122), -6, -9),
        PrintedTown("Isfara", GeoPoint(40.12649, 70.62526), -4, -9),
        PrintedTown("Khorugh", GeoPoint(37.49039, 71.55337), -11, -12),
        PrintedTown("Bokhtar", GeoPoint(37.83654, 68.77762), 4),
        PrintedTown("Panjakent", GeoPoint(39.49524, 67.60931), 5),
        PrintedTown("Tursunzoda", GeoPoint(38.51271, 68.23163), 3),
        PrintedTown("Rasht", GeoPoint(39.03, 70.38), -6, -7, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Asht", GeoPoint(40.67, 70.35), -6, -9, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Murghob", GeoPoint(38.17, 73.97), -20, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Hamadoni", GeoPoint(37.65, 69.63), -3, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Sh. Shohin", GeoPoint(37.84, 70.04), -5, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Muminobod", GeoPoint(38.11, 70.03), -3, -5, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Shahritus", GeoPoint(37.26, 68.14), 5, 3, uncertainKm = APPROXIMATE_KM),
        PrintedTown("Ayni", GeoPoint(39.39, 68.54), 5, 1, uncertainKm = APPROXIMATE_KM),
        PrintedTown("N. Khusrav", GeoPoint(37.07, 68.05), 4, uncertainKm = UNCERTAIN_KM),
    )

    /**
     * Tajikistan's method at [user] (the trust audit's F16, 13 Nov 2026; ruling R207): [tajikistanRegion] at the user's
     * own point, and beside it the Council's own construction for the town it prints there ([printedTownsNear]), as COSIM's rides in
     * Côte d'Ivoire ([coteDIvoireAt]): a start point at Dushanbe's latitude moved by the town's latest printed minutes, so
     * every start is never before the region's own at Dushanbe plus them, and an end point moved by its earliest, so the
     * sunrise and the end of eating are never after the region's at Dushanbe plus those. The region's day at Dushanbe is
     * gated never early and never a late end against every table held, so a town's day is never early against Dushanbe's
     * printed times moved by its figure, whatever the town's own sun does. The Council moves a town by whole minutes, not
     * by its sun, and the towns of Sughd lie two degrees north of Dushanbe: towards the winter solstice their own sunset
     * pulls ahead of Dushanbe's, and the region at the town's own point, which was all the first lane had, stood on the
     * very minute of the Council's Shom and Khufton at Konibodom and Khujand in November and, the Council's rule carried
     * forward, a minute before them on a dozen December days (the audit's projection; no December page is held); at Asht
     * it was a minute before the printed Asr and Shom on seven November days and before Khufton on five. The user's own
     * point still bounds every time: nothing is earlier, and no end later, than the region gives there alone.
     */
    internal fun tajikistanAt(user: GeoPoint): TimetableMethod {
        val near = printedTownsNear(councilTowns, user)
        return tajikistanRegion.method.copy(
            startPoints = listOf(movedFrom(DUSHANBE, near.maxOf { it.startMinutes } + COSIM_SPARE_MINUTES)),
            endPoints = listOf(movedFrom(DUSHANBE, near.minOf { it.endMinutes } - COSIM_SPARE_MINUTES)),
            endsBoundAtUserPoint = true,
        )
    }

    private fun calculated(
        region: SafeRegion,
        id: String = region.id,
        school: AsrSchool,
        schoolKnown: Boolean,
        scope: Scope = Scope.COUNTRY,
        countries: Set<String> = emptySet(),
        lateLimits: List<LateLimit> = emptyList(),
        safeSideOfKey: String? = null,
    ) = single(
        id = id, nameKey = CALCULATED_NAME, entryClass = EntryClass.D_NONE,
        method = if (id == region.id) region.method else region.method.copy(id = id),
        school = school, schoolKnown = schoolKnown, scope = scope, countries = countries, lateLimits = lateLimits,
        safeSideOfKey = safeSideOfKey,
    )

    /**
     * `default.safe`'s exception (rulings R37/R41): the gate (Task 7h §3, `safe-default.tsv`) checked
     * it against a broad, unrelated sample of real authorities across regions (none of them actually
     * served by this fallback — they each have their own registered entry; this is a stress test of
     * how cautious the fallback is, not a proof of any of them). Never early anywhere sampled, but
     * several apply their own safety minutes deeper than the MWL Fiqh Council's 1986 baseline this
     * region follows (spec §6.2 c): Dar al-Ifta Cairo's own 19.5° dawn runs this region's flat 18°
     * Fajr up to about 12 min late (the worst measured); Diyanet's İstanbul temkin (sunrise − 7,
     * Dhuhr + 5, Asr + 4, Maghrib + 7) runs 4–8 min past this region's flat − 2/+ 2. 20 min covers
     * every measured case with room to spare.
     *
     * Isha gets its own, smaller exception: Dar al-Ifta Cairo's own 17.5° is shallower than this
     * region's 18°, so its real Isha comes up to about 8 min before this region's angle-based
     * estimate reaches 18° — a real, physical angle difference (never early: 18° is always later
     * than 17.5°), not a minutes-policy gap like the events above.
     *
     * The end of eating (end-of-eating audit, 3 Oct 2026) is checked against the printed Fajr of
     * the samples whose dawn is 18° to 18.5° (Umm al-Qura, the Gulf, Kuwait, Diyanet, MARW, INM,
     * Libya, JAKIM): never after it, up to 5 min before JAKIM's Subuh, printed for the zone's latest
     * point and rounded up. It is not checked against the three samples whose own dawn is deeper
     * than the 18° this region assumes (Dar al-Ifta Cairo's 19.5°, Kemenag's and MUIS's 20°): there
     * the 18° end comes after their Fajr, a gap of this fallback left to the owner (safe-default.tsv's
     * header, proof 7h §3). None of those countries is served by it, and their own entries' ends are
     * checked, never after.
     */
    private val worldLateLimits = listOf(
        LateLimit(20, "no local convention is known here; nearby authorities elsewhere run deeper safety minutes than this region's flat 1986 baseline (Task 7h, safe-default.tsv)", setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
        LateLimit(15, "this region's 18° Isha is deeper (later) than some real authorities' own shallower Isha angle (Dar al-Ifta Cairo's 17.5°, Task 7h, safe-default.tsv) — never early, only later by more than the class default", setOf(TimedEvent.ISHA)),
        LateLimit(5, "this region's fast begins 2 min before the 18° dawn, and JAKIM prints Kuala Lumpur's Subuh for its zone's latest point, rounded up: up to 5 min before it (end-of-eating audit, 3 Oct 2026, safe-default.tsv)", setOf(TimedEvent.END_OF_EATING)),
    )

    /*
     * The seven regional defaults' exceptions (engine wave W5, 18 Oct 2026, rulings R136–R138): each region
     * is gated against the held tables in or near it (`gate/safe-default-<region>.tsv`), the way the world
     * default is above. Only Africa's rows are real (Tanzania is routed to `default.safe.africa`, and Darul
     * Ihsan's Dar es Salaam and Zanzibar tables are held); every other row is a stress test against a
     * country that has its own entry and is never routed to the region. Nothing is early and no end is late
     * anywhere checked; each limit below records the cost of the region's own design: the latest convention
     * in use leads every start and the earliest every end (spec §6.2 c), and the floors carry the safety
     * cushion. The minutes are the worst measured plus one, for the leap cycle. What a gate file masks (a
     * printed convention deeper than a floor the spec sets, or a real start before the region's) is a limit
     * of the fallback reported in BUILD-LOG for the owner, never an exception here.
     */

    private val africaLateLimits = listOf(
        LateLimit(25, "This region's Fajr is the latest dawn convention in use in Africa (15°); Darul Ihsan's tables for Dar es Salaam and Zanzibar and Jamiat's for South Africa print an 18° dawn, 15 to 24 min before it (engine wave W5, safe-default-africa.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(19, "This region's Isha is the latest convention in use (18° + 2 min); Darul Ihsan prints a Shafi'i Isha at 15° beside it, 14 to 18 min earlier at Dar es Salaam and Zanzibar, and its Hanafi 18° up to 5 min earlier (engine wave W5, safe-default-africa.tsv).", setOf(TimedEvent.ISHA)),
        LateLimit(11, "The fast begins 2 min before the 19.5° dawn, the earliest convention on the continent: 4 to 9 min before Jamiat's printed sehri, and up to 10 min before Darul Ihsan's Fajr at Dar es Salaam and Zanzibar, whose tables print no sehri (engine wave W5, safe-default-africa.tsv).", setOf(TimedEvent.END_OF_EATING)),
        LateLimit(7, "Jamiat's and Darul Ihsan's floors (Dhuhr + 5, Maghrib + 3) with the safety cushion on top: sunrise up to 6 min before the printed one, Dhuhr and Maghrib up to 5 min after it, Asr up to 4 (engine wave W5, safe-default-africa.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
    )

    private val americasLateLimits = listOf(
        LateLimit(7, "ISNA's 15° dawn is this region's Fajr too, and the mosque tables add a minute or two of their own: up to 6 min after them (engine wave W5, safe-default-americas.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(31, "This region's Isha is the latest convention in the Americas (18° + 2 min), 15 to 30 min after the 15° Isha the ISNA mosques print; the fast begins 2 min before the 18° dawn, 15 to 30 min before their 15° Fajr (engine wave W5, safe-default-americas.tsv).", setOf(TimedEvent.ISHA, TimedEvent.END_OF_EATING)),
        LateLimit(8, "The 1986 floors (sunrise − 2, Dhuhr, Asr and Maghrib + 2) with the safety cushion on top, against mosque tables that print the sun's own times: sunrise up to 7 min before, Dhuhr up to 6 min after, Maghrib up to 7, Asr up to 5 (engine wave W5, safe-default-americas.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
    )

    private val europeLateLimits = listOf(
        LateLimit(57, "This region's Fajr is the latest dawn convention in wide European use (12°): 7 to 37 min after the 15° to 18° dawns Paris's, Lyon's, Marseille's and Munich's calendars print, and up to 56 min after Munich's Diyanet calendar in summer, whose seasonal takdir holds its dawn early (engine wave W5, safe-default-europe.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(145, "This region's Isha is the latest convention in use (18° + 2 min): 13 to 60 min after the 12° to 17° Isha the French and German calendars print, and up to 2 h 24 min after it in summer north of about 45°, where the 18° dusk comes near midnight or, when the sun no longer reaches it, is set by the 1986 proportion (engine wave W5, safe-default-europe.tsv).", setOf(TimedEvent.ISHA)),
        LateLimit(135, "The fast begins 2 min before the 18° dawn, the earliest convention in use: 12 to 60 min before the 12° to 15° Fajr the French and German calendars print, and up to 2 h 14 min before it in summer north of about 45°, where the 18° dawn comes soon after midnight or, when the sun no longer reaches it, is set by the 1986 proportion (engine wave W5, safe-default-europe.tsv).", setOf(TimedEvent.END_OF_EATING)),
        LateLimit(10, "Paris's floors (Dhuhr + 5, Maghrib + 4) and the 1986 ones (sunrise − 2, Asr + 2) with the safety cushion on top, against calendars printing smaller minutes: sunrise up to 6 min before, Dhuhr up to 9 min after, Maghrib up to 8, Asr up to 6 (engine wave W5, safe-default-europe.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
    )

    private val balkansLateLimits = listOf(
        LateLimit(27, "This region's Fajr is Kosovo's Sabah, the latest in the Balkans (the 19° dawn + 20 min): 10 to 26 min after the dawn Bosnia's, Albania's and Montenegro's tables print, and up to 7 min after BIK's own Sabah at its base point (engine wave W5, safe-default-balkans.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(34, "This region's Isha is the latest convention in the Balkans (18.5° + 2 min): 6 to 33 min after the 17° to 18° Isha Bosnia, Albania, Kosovo and Montenegro print, most in summer (engine wave W5, safe-default-balkans.tsv).", setOf(TimedEvent.ISHA)),
        LateLimit(17, "The fast begins 2 min before the 19° dawn; Bosnia's and Albania's tables begin it at a shallower dawn, 7 to 16 min later (engine wave W5, safe-default-balkans.tsv).", setOf(TimedEvent.END_OF_EATING)),
        LateLimit(10, "Diyanet's temkin (sunrise − 7, Dhuhr + 5, Asr + 4) and Montenegro's Maghrib + 9 as floors, with the safety cushion on top, against tables printing smaller minutes: Dhuhr up to 9 min after the printed one, Maghrib up to 8, Asr up to 7, sunrise up to 6 before (engine wave W5, safe-default-balkans.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
    )

    private val levantLateLimits = listOf(
        LateLimit(20, "This region's Fajr is the 18° dawn in use in Jordan and Palestine, with 138 s more for the PA's towns in Israel; Lebanon's Dar al-Fatwa prints a 20° one, 9 to 19 min earlier, Jordan's and the PA's tables add their own minutes, up to 6, and the PA's thirteen towns in Israel print up to 10 min earlier (engine wave W5 and the safe defaults lane of 12 Nov 2026, safe-default-levant.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(12, "This region's Isha is Jordan's 18.2° + 2 min: up to 10 min after Lebanon's and 11 after the PA's, at its towns in Israel (engine wave W5 and the safe defaults lane, safe-default-levant.tsv).", setOf(TimedEvent.ISHA)),
        LateLimit(24, "The fast begins 2 min before the 20° dawn, Lebanon's, the earliest in the Levant: 10 to 16 min before the 18° Fajr Jordan and the PA print at Amman and al-Aqsa, and up to 23 min before the PA's at its towns in Israel in summer (engine wave W5 and the safe defaults lane, safe-default-levant.tsv).", setOf(TimedEvent.END_OF_EATING)),
        LateLimit(15, "Sunrise and sunset at the −2.25° horizon Jordan and the PA use, about 7 min outside the sun's edge, with the floors and the safety cushion on top: sunrise up to 14 min before Beirut's printed one, Maghrib up to 10 min after, Dhuhr up to 7, Asr up to 5 (engine wave W5, safe-default-levant.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
    )

    private val southeastAsiaLateLimits = listOf(
        LateLimit(14, "This region's Fajr is JAKIM's 18° Subuh, the latest in South-east Asia; MUIS, Kemenag and Brunei print a 20° one, 9 to 13 min earlier (engine wave W5, safe-default-seasia.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(15, "The fast begins 2 min before the 20° dawn: 4 to 6 min before Kemenag's and MUIS's Fajr, and up to 14 min before JAKIM's Subuh, which it prints for its zone's latest point, rounded up (engine wave W5, safe-default-seasia.tsv).", setOf(TimedEvent.END_OF_EATING)),
        LateLimit(8, "The 1986 floors (sunrise − 2, Dhuhr, Asr, Maghrib and Isha + 2) with the safety cushion on top, against tables that print the sun's own times: sunrise up to 7 min before, Maghrib up to 6 min after, Dhuhr up to 5, Asr and Isha up to 4 (engine wave W5, safe-default-seasia.tsv).", setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)),
    )

    private val southAsiaLateLimits = listOf(
        LateLimit(8, "Karachi's 18°/18° with IFB's Dhuhr and Maghrib + 3 as floors and the safety cushion on top, against Karachi's, Delhi's and Dhaka's tables: Maghrib up to 7 min after the printed one, sunrise up to 6 before, Dhuhr up to 6 after, Fajr up to 5, Asr and Isha up to 4 (engine wave W5, safe-default-southasia.tsv).", setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)),
    )

    // The safe defaults lane (12 Nov 2026): Tajikistan and Côte d'Ivoire, whose own councils' tables are held, gated
    // against them (gate/tj-default.tsv, gate/ci-default.tsv). Each limit is the worst measured plus one.

    private val tajikistanLateLimits = listOf(
        LateLimit(32, "Central Asia's 15° Fajr, the latest in the region, against the Council of Ulama's Bomdod, whose dawn sits between about 16° and 19.4° over the ten tables held, with each town held to the latest figure either of the Council's footnotes prints for it: up to 26 min after it at Dushanbe (its 2025 calendar) and up to 31 at the towns of Sughd (safe defaults lane and the trust audit's F16, tj-default.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(22, "Sunrise is shown 12 min before the sun's own, because the Council's 2021 calendar prints it up to 10 min before, and a town's is held to the earliest figure either footnote prints: up to 17 min before the sunrise its other calendars print at Dushanbe, and up to 21 at Ayni, whose two figures lie 4 min apart (trust audit F16, tj-default.tsv).", setOf(TimedEvent.SUNRISE)),
        LateLimit(26, "Dhuhr is never before 12:45, the Council's fixed Peshin at Dushanbe plus Panjakent's + 5, the latest its footnote prints: up to 16 min after the Peshin of Khorugh and 25 after Murghob's, the towns printed farthest before Dushanbe; one month is held, so a town's own Peshin waits for a second (safe defaults lane and the trust audit's F16, tj-default.tsv).", setOf(TimedEvent.DHUHR)),
        LateLimit(16, "Maghrib is 16 min after the sunset: the Council's Shom runs 7 to 13 min after it over the tables held, and a town is held to the latest figure either footnote prints: up to 10 min after Shom at Dushanbe, and up to 15 in Sughd, where the two footnotes lie 3 to 5 min apart and the Council's whole minutes run ahead of the town's own sunset in spring (trust audit F16, tj-default.tsv).", setOf(TimedEvent.MAGHRIB)),
        LateLimit(12, "The Council's footnote moves a town by whole minutes, not by its sun, and each town is held to the latest figure printed for it: Asr up to 11 min after its own (about the second shadow less 3), Isha up to 10 after its Khufton, on the one month that prints them (safe defaults lane and the trust audit's F16, tj-default.tsv).", setOf(TimedEvent.ASR, TimedEvent.ISHA)),
        LateLimit(35, "The fast begins 2 min before the 19.5° dawn, so that it is never after the Bomdod of any calendar held (the 2025 one's dawn is about 19.4°); the 2018 calendar's dawn is about 16.2°, and a town is held to the earliest figure either footnote prints: up to 30 min before its Bomdod at Dushanbe and up to 34 at Asht (trust audit F16, tj-default.tsv).", setOf(TimedEvent.END_OF_EATING)),
    )

    private val coteDIvoireLateLimits = listOf(
        LateLimit(13, "Africa's 15° Fajr with the cushion against COSIM's Soubh, the same dawn: up to 5 min after it at Abidjan; COSIM moves its other towns by whole minutes, not by their sun, so where a town's own dawn comes later than that (Tengréla in November and December) the Fajr shown, from the town's own dawn, runs up to 12 min after it (safe defaults lane and the trust audit's F3, ci-default.tsv).", setOf(TimedEvent.FAJR)),
        LateLimit(13, "Africa's sunrise − 2 with the cushion, and never after Abidjan's plus the town's printed minutes: up to 10 min before COSIM's sunrise moved by them, where the town's own sun rises before it (Sassandra, Touba), and up to 12 at Seydougou and Koonan, whose points are only roughly known, so that the lowest figure printed near them bounds it (safe defaults lane and the trust audit's F3, ci-default.tsv).", setOf(TimedEvent.SUNRISE)),
        LateLimit(17, "Africa's Maghrib + 3 with the cushion after the town's own sunset: at Abidjan up to 8 min after COSIM's Maghreb, and in the north in summer, where COSIM's Maghreb moved by the town's whole minutes comes up to 9 min before the town's own sunset (Tengréla), up to 16 (safe defaults lane, ci-default.tsv).", setOf(TimedEvent.MAGHRIB)),
        LateLimit(28, "Africa's 18° + 2 min Isha, the latest convention in the region, against COSIM's Icha at 15°: up to 17 min after it at Abidjan and up to 27 at the northern towns in summer, as Maghrib (safe defaults lane, ci-default.tsv).", setOf(TimedEvent.ISHA)),
        LateLimit(33, "The fast begins 2 min before COSIM's imsak rule (its 1436 edition's Soubh − 25) taken from the 15° dawn, on every day, and never after Abidjan's plus the town's printed minutes: on the Soubh COSIM prints, at least 1 min before the rule everywhere, up to 3 at Abidjan and up to 7 at Seydougou, whose point is only roughly known, as the sunrise. Out of Ramadan COSIM prints no imsak, so its Soubh is the end the gate holds it to: up to 28 min after this one at Abidjan and up to 32 at Seydougou; in Ramadan, up to 13 min before the 1434 edition's imsak (Soubh − 15) at Abidjan and up to 24 at its towns (safe defaults lane, the trust audit's F3 and F36 and the lane's review of 14 Nov 2026, ci-default.tsv).", setOf(TimedEvent.END_OF_EATING)),
    )

    /** The safe default where nothing is known; the later Asr leads. */
    fun safeDefault(region: SafeRegion): RegistryEntry = safeDefaults.first { it.id == region.id }

    private val safeDefaults: List<RegistryEntry> = listOf(
        calculated(world, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.GLOBAL, lateLimits = worldLateLimits),
        calculated(americas, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, lateLimits = americasLateLimits),
        calculated(europe, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, lateLimits = europeLateLimits),
        // Every Balkan authority prints the first-shadow Asr (research, Bosnia to Albania).
        calculated(balkans, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, lateLimits = balkansLateLimits),
        // "S (Maliki / Shafi'i)" for Nigeria, Senegal, Somalia, Ethiopia (research summary table).
        calculated(africa, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, lateLimits = africaLateLimits),
        // "Standard in … South-east Asia" (spec §3.7).
        calculated(southeastAsia, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, lateLimits = southeastAsiaLateLimits),
        // "Standard in the Arab world" (spec §3.7). Israel's only (Registry): the Palestinian Dar al-Iftaa prints its table
        // for 13 Arab towns there, held for 2026 and 2027 and the region's Fajr refit on them, so the card and About name
        // it rather than say no local timetable is known (the second audit's G8, as R209 did for Tajikistan and Côte
        // d'Ivoire); who in those towns follows it is not surveyed, and the kind stays "Calculated by Taqwa".
        calculated(levant, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, lateLimits = levantLateLimits, safeSideOfKey = PA_IFTAA_NAME),
        calculated(southAsia, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, lateLimits = southAsiaLateLimits),
    )

    /**
     * Iran: Sunni times calculated by Taqwa (no Sunni authority found; spec §6.2 c) and the
     * Sunni-times card everywhere. The school per spec §3.7: Hanafi in Sistan-Baluchestan, Golestan
     * and Khorasan, Shafi'i (Standard) in Kurdistan and Hormozgan, otherwise not known, so the later.
     */
    val iran = calculated(world, id = "ir.default", school = AsrSchool.HANAFI, schoolKnown = false, countries = setOf("IR"))
    val iranHanafi = calculated(world, id = "ir.hanafi", school = AsrSchool.HANAFI, schoolKnown = true, scope = Scope.UNIT, countries = setOf("IR"))
    val iranShafii = calculated(world, id = "ir.shafii", school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, countries = setOf("IR"))

    /**
     * Côte d'Ivoire: "S (Maliki / Shafi'i)", as the Africa region's countries ([coteDIvoireRegion]). Calculated by Taqwa
     * on the safe side of what COSIM prints: four of Abidjan's month tables and its town minutes are held, not a year,
     * so COSIM's table cannot be served as checked, and the card and About say whose table it is beside
     * ([RegistryEntry.safeSideOfKey], ruling R209).
     */
    val coteDIvoire = calculated(
        coteDIvoireRegion, id = "ci.default", school = AsrSchool.STANDARD, schoolKnown = true, countries = setOf("CI"),
        lateLimits = coteDIvoireLateLimits, safeSideOfKey = COSIM_NAME,
    )

    /** Côte d'Ivoire's places, each with COSIM's figure for its nearest printed town ([coteDIvoireAt]); no unit of its own. */
    val coteDIvoireUnits: UnitSet = UnitSet(coteDIvoire.id, emptyList(), measuredBeyond = true) { user -> coteDIvoireAt(user) }

    /** The Americas' places, each with the Moonsighting Committee's day beside it from [AMERICAS_FAR_LATITUDE] ([americasAt]); no unit of its own. */
    val americasUnits: UnitSet = UnitSet(americas.id, emptyList(), measuredBeyond = true) { user -> americasAt(user) }

    /** Afghanistan: Hanafi (spec §3.7), South Asia's conventions. */
    val afghanistan = calculated(southAsia, id = "af.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("AF"))

    /**
     * Tajikistan: Hanafi (Central Asia, spec §3.7). Calculated by Taqwa on the safe side of what the Council of Ulama
     * prints ([tajikistanRegion]): a month page and nine Ramadan calendars are held, not a year, so the Council's table
     * cannot be served as checked, and the card and About say whose table it is beside ([RegistryEntry.safeSideOfKey],
     * ruling R209).
     */
    val tajikistan = calculated(
        tajikistanRegion, id = "tj.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("TJ"),
        lateLimits = tajikistanLateLimits, safeSideOfKey = COUNCIL_OF_ULAMA_NAME,
    )

    /** Tajikistan's places, each with the Council's figures for the town it prints there ([tajikistanAt]); no unit of its own. */
    val tajikistanUnits: UnitSet = UnitSet(tajikistan.id, emptyList(), measuredBeyond = true) { user -> tajikistanAt(user) }

    /** Turkmenistan: Hanafi (Central Asia, spec §3.7), no table found. */
    val turkmenistan = calculated(centralAsia, id = "tm.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("TM"))

    val others = listOf(mwl, isna, karachi, moonsighting)
    val entries = safeDefaults + listOf(iran, iranHanafi, iranShafii, coteDIvoire, afghanistan, tajikistan, turkmenistan)

    /** See the KDoc on [SafeRegion]: the extra safety the gate showed the safe default needs. */
    private const val EXTRA_SAFETY_SECONDS = 150

    /** The Council of Ulama of Tajikistan's Islamic Centre, whose printed tables Tajikistan is on the safe side of. */
    private const val COUNCIL_OF_ULAMA_NAME = "authority_council_ulama_tj"

    /** COSIM (Conseil Supérieur des Imams, des Mosquées et des Affaires Islamiques), the same for Côte d'Ivoire. */
    private const val COSIM_NAME = "authority_cosim"

    /** The Palestinian Dar al-Iftaa's short name, the body whose town tables the Levant default is on the safe side of (G8). */
    private const val PA_IFTAA_NAME = "authority_pa_iftaa"

    /** A minute of time is a quarter degree of longitude at one latitude. */
    private const val DEG_PER_MINUTE = 0.25

    /** A town figure's point lies this many minutes farther, so that its whole minutes survive the rounding (as BIK's). */
    private const val COSIM_SPARE_MINUTES = 0.04

    /** How far a printed town's point may be off where the lane had no gazetteer for it ([PrintedTown.uncertainKm]). */
    private const val APPROXIMATE_KM = 30.0

    /** The same for the villages the lane knew least. */
    private const val UNCERTAIN_KM = 60.0
}
