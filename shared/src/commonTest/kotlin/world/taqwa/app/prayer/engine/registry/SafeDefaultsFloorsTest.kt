package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.authorities.Generic
import world.taqwa.app.prayer.engine.registry.authorities.Levant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The safe defaults lane (12 Nov 2026) and the trust audit's F3, F5 and F16 (13 Nov 2026): where the councils' own
 * tables found a safe default early, its fix, held as rules (no printed time is committed here; the gate rows read the
 * tables: gate/tj-default.tsv, gate/ci-default.tsv, gate/safe-default-levant.tsv). Every third day of 2027, Ramadan 1448
 * among them, at app cities across each country and at the printed towns the app does not list.
 */
class SafeDefaultsFloorsTest {

    private fun days(year: Int = 2027): Sequence<LocalDate> =
        generateSequence(LocalDate(year, 1, 1)) { it.plus(3, DateTimeUnit.DAY) }.takeWhile { it.year == year }

    private fun at(date: LocalDate, hour: Int, minute: Int, zone: TimeZone) =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    @Test
    fun `tajikistan's dhuhr is never before the council's fixed peshin with panjakent's minutes maghrib 13 min after the sunset and isha 90 after maghrib`() {
        val zone = TimeZone.of("Asia/Dushanbe")
        val towns = listOf(
            "Dushanbe" to Place(38.53575, 68.77905, "Asia/Dushanbe", "TJ"),
            "Khujand" to Place(40.28256, 69.62216, "Asia/Dushanbe", "TJ"),
            "Isfara" to Place(40.12649, 70.62526, "Asia/Dushanbe", "TJ"),
            "Panjakent" to Place(39.49524, 67.60931, "Asia/Dushanbe", "TJ"),
            "Khorugh" to Place(37.49039, 71.55337, "Asia/Dushanbe", "TJ"),
            "Bokhtar" to Place(37.83654, 68.77762, "Asia/Dushanbe", "TJ"),
        )
        for ((name, place) in towns) {
            val resolution = Registry.resolve(place)
            assertEquals("tj.default", resolution.entry.id, name)
            assertEquals(EntryClass.D_NONE, resolution.entryClass, name)
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone)
                val label = "$name $date"
                assertTrue(day.dhuhr >= at(date, 12, 45, zone), "$label: Dhuhr before 12:45")
                assertTrue(day.maghrib >= day.sunset + 13.minutes, "$label: Maghrib under 13 min after the sunset")
                assertTrue(day.isha >= day.maghrib + 90.minutes, "$label: Isha under 90 min after Maghrib")
            }
        }
    }

    @Test
    fun `tajikistan's towns are never before dushanbe's day moved by the latest minutes the council prints for them nor their ends after it moved by the earliest`() {
        // The trust audit's F16: the Council's footnote moves a town by whole minutes after Dushanbe, not by its sun, and
        // its two footnotes differ; each place takes its printed town's figures beside its own sun, every start the later
        // of its own and Dushanbe's plus the latest figure, every end the earlier of its own and Dushanbe's plus the
        // earliest. Sughd's towns (Khujand, Konibodom, Isfara, Istaravshan) are where the town's own sun gave no spare.
        val zone = TimeZone.of("Asia/Dushanbe")
        val region = Generic.tajikistanRegion.method
        val dushanbe = Registry.resolve(Place(38.53575, 68.77905, "Asia/Dushanbe", "TJ")).copy(method = region)
        val towns = listOf(
            Triple("Dushanbe", Place(38.53575, 68.77905, "Asia/Dushanbe", "TJ"), 0 to 0),
            Triple("Istaravshan", Place(39.9142, 69.00328, "Asia/Dushanbe", "TJ"), -1 to -5),
            Triple("Khujand", Place(40.28256, 69.62216, "Asia/Dushanbe", "TJ"), -3 to -7),
            Triple("Konibodom", Place(40.29414, 70.43122, "Asia/Dushanbe", "TJ"), -6 to -9),
            Triple("Isfara", Place(40.12649, 70.62526, "Asia/Dushanbe", "TJ"), -4 to -9),
            Triple("Khorugh", Place(37.49039, 71.55337, "Asia/Dushanbe", "TJ"), -11 to -12),
            Triple("Bokhtar", Place(37.83654, 68.77762, "Asia/Dushanbe", "TJ"), 4 to 4),
            Triple("Panjakent", Place(39.49524, 67.60931, "Asia/Dushanbe", "TJ"), 5 to 5),
            Triple("Tursunzoda", Place(38.51271, 68.23163, "Asia/Dushanbe", "TJ"), 3 to 3),
        )
        for ((name, place, figures) in towns) {
            val resolution = Registry.resolve(place)
            assertEquals("tj.default", resolution.entry.id, name)
            val alone = resolution.copy(method = region)
            val (latest, earliest) = figures
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone)
                val own = DayPipeline.day(alone, date, zone)
                val base = DayPipeline.day(dushanbe, date, zone)
                val label = "$name $date"
                for ((what, ours, pair) in listOf(
                    Triple("Fajr", day.fajr, own.fajr to base.fajr), Triple("Asr", day.asr, own.asr to base.asr),
                    Triple("Maghrib", day.maghrib, own.maghrib to base.maghrib), Triple("Isha", day.isha, own.isha to base.isha),
                )) {
                    val floor = maxOf(pair.first, pair.second + latest.minutes)
                    assertTrue(ours >= floor, "$label: $what before the later of its own and Dushanbe's + $latest")
                    assertTrue(ours <= floor + 1.minutes, "$label: $what more than a minute after the later of the two")
                }
                for ((what, ours, pair) in listOf(
                    Triple("the end of eating", day.endOfEating, own.endOfEating to base.endOfEating),
                    Triple("sunrise", day.sunrise, own.sunrise to base.sunrise),
                )) {
                    val ceiling = minOf(pair.first, pair.second + earliest.minutes)
                    assertTrue(ours <= ceiling, "$label: $what after the earlier of its own and Dushanbe's + $earliest")
                    assertTrue(ours >= ceiling - 1.minutes, "$label: $what more than a minute before the earlier of the two")
                }
                assertTrue(day.dhuhr >= own.dhuhr, "$label: Dhuhr before the region's own")
            }
        }
    }

    @Test
    fun `every town the council prints is held to the latest minutes it prints for it after dushanbe and its ends to the earliest`() {
        // The lane's first review (14 Nov 2026): the rule above was held at the towns the app lists alone, though the
        // audit's F16 was that a town the app does not list (Asht) took a neighbour's lower figure. All nineteen towns of
        // both footnotes, as Côte d'Ivoire's: at the town's point, and at the nine whose points are the lane's own (good
        // to about 30 km) a walk away from it too. No start before Dushanbe's day plus the latest minutes printed for
        // the town, no end after it plus the earliest.
        val zone = TimeZone.of("Asia/Dushanbe")
        val dushanbe = Registry.resolve(Place(38.53575, 68.77905, "Asia/Dushanbe", "TJ")).copy(method = Generic.tajikistanRegion.method)
        val listed = listOf(
            Triple("Dushanbe", 38.53575 to 68.77905, 0 to 0), Triple("Istaravshan", 39.9142 to 69.00328, -1 to -5),
            Triple("Kŭlob", 37.91459 to 69.78454, -4 to -5), Triple("Khujand", 40.28256 to 69.62216, -3 to -7),
            Triple("Konibodom", 40.29414 to 70.43122, -6 to -9), Triple("Isfara", 40.12649 to 70.62526, -4 to -9),
            Triple("Khorugh", 37.49039 to 71.55337, -11 to -12), Triple("Bokhtar", 37.83654 to 68.77762, 4 to 4),
            Triple("Panjakent", 39.49524 to 67.60931, 5 to 5), Triple("Tursunzoda", 38.51271 to 68.23163, 3 to 3),
        )
        val unlisted = listOf(
            Triple("Rasht", 39.03 to 70.38, -6 to -7), Triple("Asht", 40.67 to 70.35, -6 to -9),
            Triple("Murghob", 38.17 to 73.97, -20 to -20), Triple("Hamadoni", 37.65 to 69.63, -3 to -3),
            Triple("Sh. Shohin", 37.84 to 70.04, -5 to -5), Triple("Muminobod", 38.11 to 70.03, -3 to -5),
            Triple("Shahritus", 37.26 to 68.14, 5 to 3), Triple("Ayni", 39.39 to 68.54, 5 to 1),
            Triple("N. Khusrav", 37.07 to 68.05, 4 to 4),
        )
        val here = listOf(Triple("at", 0.0, 0.0))
        val around = here + listOf(Triple("north of", 0.2, 0.0), Triple("west of", 0.0, -0.2))
        assertEquals(19, listed.size + unlisted.size)
        for ((towns, spots) in listOf(listed to here, unlisted to around)) {
            for ((name, point, figures) in towns) {
                val (latest, earliest) = figures
                for ((where, north, east) in spots) {
                    val resolution = Registry.resolve(Place(point.first + north, point.second + east, "Asia/Dushanbe", "TJ"))
                    assertEquals("tj.default", resolution.entry.id, name)
                    for (date in days()) {
                        val day = DayPipeline.day(resolution, date, zone)
                        val base = DayPipeline.day(dushanbe, date, zone)
                        val label = "$where $name $date"
                        assertTrue(day.fajr >= base.fajr + latest.minutes, "$label: Fajr before Dushanbe's + $latest")
                        assertTrue(day.asr >= base.asr + latest.minutes, "$label: Asr before Dushanbe's + $latest")
                        assertTrue(day.maghrib >= base.maghrib + latest.minutes, "$label: Maghrib before Dushanbe's + $latest")
                        assertTrue(day.isha >= base.isha + latest.minutes, "$label: Isha before Dushanbe's + $latest")
                        assertTrue(day.dhuhr >= at(date, 12, 45, zone), "$label: Dhuhr before 12:45")
                        assertTrue(day.sunrise <= base.sunrise + earliest.minutes, "$label: sunrise after Dushanbe's + $earliest")
                        assertTrue(day.endOfEating <= base.endOfEating + earliest.minutes, "$label: the end of eating after Dushanbe's + $earliest")
                    }
                }
            }
        }
    }

    @Test
    fun `tajikistan's sunrise is at least 9 min and its end of eating at the 19 and a half degree dawn before central asia's and isha never before the 18 degree dusk`() {
        // Every calendar the Council printed binds (F16): its 2021 one prints the sunrise up to 10 min before the sun's
        // own, its 2025 one a dawn near 19.4°, and no Khufton is held from spring to autumn.
        val place = Place(38.53575, 68.77905, "Asia/Dushanbe", "TJ")
        val zone = TimeZone.of("Asia/Dushanbe")
        val resolution = Registry.resolve(place)
        val centralAsia = resolution.copy(method = Generic.centralAsia.method)
        val method = requireNotNull(resolution.method)
        assertEquals(Generic.centralAsia.method.margins.sunrise - 7 * 60, method.margins.sunrise)
        assertEquals(Generic.centralAsia.method.fajrAngle, method.fajrAngle)
        for (date in days()) {
            val day = DayPipeline.day(resolution, date, zone)
            val other = DayPipeline.day(centralAsia, date, zone)
            assertTrue(day.sunrise <= other.sunrise - 7.minutes, "$date: sunrise")
            assertTrue(day.endOfEating < other.endOfEating, "$date: the end of eating")
            assertTrue(day.fajr >= other.fajr, "$date: Fajr before Central Asia's 15°")
            assertTrue(day.isha >= other.isha, "$date: Isha before Central Asia's")
        }
    }

    @Test
    fun `turkmenistan keeps central asia's region with no fixed dhuhr or isha floor`() {
        val ashgabat = Registry.resolve(Place(37.95, 58.38333, "Asia/Ashgabat", "TM"))
        assertEquals("tm.default", ashgabat.entry.id)
        val method = requireNotNull(ashgabat.method)
        assertNull(method.fixedDhuhrLocalMinutes)
        assertNull(method.ishaFloorMinutesAfterMaghrib)
        assertEquals(Generic.centralAsia.method.margins, method.margins)
    }

    @Test
    fun `cote d'ivoire's dhuhr and asr are never before cosim's conventional 13 and 16 hours and the fast begins 25 min and the region's margin before the dawn`() {
        val zone = TimeZone.of("Africa/Abidjan")
        val towns = listOf(
            "Abidjan" to Place(5.35444, -4.00167, "Africa/Abidjan", "CI"),
            "Bouaké" to Place(7.69385, -5.03031, "Africa/Abidjan", "CI"),
            "Korhogo" to Place(9.45803, -5.62961, "Africa/Abidjan", "CI"),
            "Daloa" to Place(6.87735, -6.45022, "Africa/Abidjan", "CI"),
        )
        val africa = requireNotNull(Registry.byId("default.safe.africa"))
        // The lane's first review (14 Nov 2026): COSIM's rule (Soubh - 25) with no margin came a minute after the rule on
        // the Soubh of its 1448 months, so the end keeps at least the margin every region's end of eating takes, and the
        // fast begins at least 25 min + that margin + Fajr's own cushion before the Fajr shown.
        val margin = Generic.coteDIvoireRegion.method.endOfEatingMarginSeconds
        assertTrue(margin <= Generic.africa.method.endOfEatingMarginSeconds, "the end of eating's margin is under the Africa region's")
        assertTrue(margin <= -120, "the end of eating's margin is under 2 min")
        for ((name, place) in towns) {
            val resolution = Registry.resolve(place)
            assertEquals("ci.default", resolution.entry.id, name)
            assertEquals(EntryClass.D_NONE, resolution.entryClass, name)
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone, AsrSchool.STANDARD)
                val region = DayPipeline.day(africa, place, date, AsrSchool.STANDARD)
                val label = "$name $date"
                assertTrue(day.dhuhr >= at(date, 13, 0, zone), "$label: Dhuhr before 13:00")
                assertTrue(day.asr >= at(date, 16, 0, zone), "$label: Asr before 16:00")
                assertTrue(day.asrOther >= at(date, 16, 0, zone), "$label: the other school's Asr before 16:00")
                assertTrue(day.fajr - day.endOfEating >= 30.minutes, "$label: the fast begins under 30 min before the Fajr shown")
                assertTrue(day.endOfEating <= region.endOfEating, "$label: the end of eating after the Africa region's")
                assertTrue(day.sunrise <= region.sunrise, "$label: sunrise after the Africa region's")
                for ((what, ours, theirs) in listOf(
                    Triple("Fajr", day.fajr, region.fajr), Triple("Maghrib", day.maghrib, region.maghrib),
                    Triple("Isha", day.isha, region.isha),
                )) {
                    assertTrue(ours >= theirs, "$label: $what before the Africa region's")
                }
            }
        }
    }

    @Test
    fun `dhuhr's end in cote d'ivoire and tajikistan is never after the shadow-1 asr at the town itself in either school`() {
        // The second trust audit's G33 (14 Nov 2026), ruling R265: Dhuhr's end was read from the starts' Asr (Abidjan or
        // Dushanbe moved west by the town's minutes, at the base city's latitude), up to 16 min after the first shadow at
        // a northern Ivorian town and 12 in Sughd, while the F2 test held Abidjan alone. It is an end now, read at the
        // ends' points (the user's own and the end point), the earliest, rounded down.
        val towns = listOf(
            Triple("Abidjan", Place(5.35444, -4.00167, "Africa/Abidjan", "CI"), "ci.default"),
            Triple("Katiola", Place(8.13728, -5.10095, "Africa/Abidjan", "CI"), "ci.default"),
            Triple("Korhogo", Place(9.45803, -5.62961, "Africa/Abidjan", "CI"), "ci.default"),
            Triple("Vallée du Bandama", Place(8.76821, -5.6865, "Africa/Abidjan", "CI"), "ci.default"),
            Triple("Khujand", Place(40.28256, 69.62216, "Asia/Dushanbe", "TJ"), "tj.default"),
            Triple("Sughd", Place(39.77447, 68.80929, "Asia/Dushanbe", "TJ"), "tj.default"),
        )
        for ((name, place, entryId) in towns) {
            val zone = TimeZone.of(place.zoneId)
            val resolution = Registry.resolve(place)
            assertEquals(entryId, resolution.entry.id, name)
            for (date in days() + days(2028)) {
                for (school in AsrSchool.entries) {
                    val day = DayPipeline.day(resolution, date, zone, school)
                    val firstShadow = requireNotNull(DayPipeline.realAsr(resolution.point, date, zone, AsrSchool.STANDARD))
                    val end = day.ends.getValue(Prayer.DHUHR)
                    assertTrue(end <= firstShadow, "$name $date $school: Dhuhr's end $end after the first shadow $firstShadow")
                    assertTrue(day.asr > requireNotNull(DayPipeline.realAsr(resolution.point, date, zone, school)), "$name $date $school: Asr")
                }
            }
        }
    }

    @Test
    fun `cote d'ivoire's towns are never before abidjan's day moved by cosim's printed minutes nor their ends after it`() {
        // COSIM's correspondances horaires (gate/ci-default.tsv's derived rows): a town's times are Abidjan's plus its
        // printed minutes. Each place takes its nearest printed town's figure beside its own sun: every start the later
        // of the two, every end the earlier, whole minutes kept (a minute more or less only where the rounding asks it).
        // Where a printed town whose point is only roughly known may be the nearest (the trust audit's F3 and F5), starts
        // take the largest figure of those that may be and ends the smallest.
        val zone = TimeZone.of("Africa/Abidjan")
        // The region's own day at Abidjan, as the town figures read it (Abidjan's resolution carries its own + 0 beside it).
        val abidjan = Registry.resolve(Place(5.35444, -4.00167, "Africa/Abidjan", "CI")).copy(method = Generic.coteDIvoireRegion.method)
        val towns = listOf(
            Triple("Bondoukou", Place(8.0402, -2.80003, "Africa/Abidjan", "CI"), -6 to -6),
            Triple("Adiaké", Place(5.28634, -3.30403, "Africa/Abidjan", "CI"), -4 to -4),
            Triple("Daoukro", Place(7.0591, -3.9631, "Africa/Abidjan", "CI"), 0 to 0),
            Triple("Korhogo", Place(9.45803, -5.62961, "Africa/Abidjan", "CI"), 8 to 8),
            Triple("Tengréla", Place(10.48115, -6.40694, "Africa/Abidjan", "CI"), 10 to 10),
            // Not printed by COSIM: its nearest printed town is Kolia (+ 10), and Madinani and Dioulatiédougou (+ 14),
            // no app cities, may be nearer than the lane's points for them say.
            Triple("Boundiali", Place(9.52167, -6.48694, "Africa/Abidjan", "CI"), 14 to 10),
            Triple("Danané", Place(7.25957, -8.15498, "Africa/Abidjan", "CI"), 18 to 18),
        )
        for ((name, place, figures) in towns) {
            val (minutes, endMinutes) = figures
            val resolution = Registry.resolve(place)
            assertEquals("ci.default", resolution.entry.id, name)
            val alone = resolution.copy(method = Generic.coteDIvoireRegion.method)
            val figure = minutes.minutes
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone)
                val own = DayPipeline.day(alone, date, zone)
                val base = DayPipeline.day(abidjan, date, zone)
                val label = "$name $date"
                for ((what, ours, pair) in listOf(
                    Triple("Fajr", day.fajr, own.fajr to base.fajr), Triple("Maghrib", day.maghrib, own.maghrib to base.maghrib),
                    Triple("Isha", day.isha, own.isha to base.isha),
                )) {
                    val floor = maxOf(pair.first, pair.second + figure)
                    assertTrue(ours >= floor, "$label: $what before the later of its own and Abidjan's + $minutes")
                    assertTrue(ours <= floor + 1.minutes, "$label: $what more than a minute after the later of the two")
                }
                for ((what, ours, pair) in listOf(
                    Triple("the end of eating", day.endOfEating, own.endOfEating to base.endOfEating),
                    Triple("sunrise", day.sunrise, own.sunrise to base.sunrise),
                )) {
                    val ceiling = minOf(pair.first, pair.second + endMinutes.minutes)
                    assertTrue(ours <= ceiling, "$label: $what after the earlier of its own and Abidjan's + $endMinutes")
                    assertTrue(ours >= ceiling - 1.minutes, "$label: $what more than a minute before the earlier of the two")
                }
                assertEquals(own.dhuhr, day.dhuhr, "$label: Dhuhr moved by a town figure")
            }
        }
    }

    @Test
    fun `every town cosim prints that the app does not list is held to its own printed minutes after abidjan and never a neighbour's lower ones`() {
        // The trust audit's F3 and F5: the twenty-one towns of COSIM's correspondances horaires that are no app city
        // were left out, and a person at Madinani took Kolia's + 10 for its own + 14, a Fajr before COSIM's Soubh. At the
        // town's point, and a walk away from it (the points are the lane's own, good to about 30 km): no start before
        // Abidjan's day plus the town's printed minutes, no end after it.
        val zone = TimeZone.of("Africa/Abidjan")
        val abidjan = Registry.resolve(Place(5.35444, -4.00167, "Africa/Abidjan", "CI")).copy(method = Generic.coteDIvoireRegion.method)
        val towns = listOf(
            Triple("Prikro", 7.65 to -3.99, 0), Triple("Alépé", 5.50 to -3.66, -2), Triple("Nassian", 8.45 to -3.47, -2),
            Triple("Sinématiali", 9.58 to -5.38, 6), Triple("Tiéningboué", 8.18 to -5.72, 8), Triple("Kani", 8.48 to -6.60, 12),
            Triple("Morondo", 8.95 to -6.78, 12), Triple("Dioulatiédougou", 9.07 to -7.03, 14), Triple("Djibrosso", 8.75 to -6.93, 14),
            Triple("Guintéguéla", 8.20 to -7.35, 14), Triple("Madinani", 9.6167 to -6.95, 14), Triple("Goulia", 10.02 to -7.20, 14),
            Triple("Tiémé", 9.55 to -7.32, 14), Triple("Bako", 9.13 to -7.60, 16), Triple("Booko", 8.95 to -7.83, 16),
            Triple("Tienko", 10.08 to -7.47, 16), Triple("Koro", 8.55 to -7.46, 16), Triple("Seydougou", 9.60 to -7.95, 18),
            Triple("Koonan", 8.63 to -7.83, 18), Triple("Toulépleu", 6.58 to -8.41, 18), Triple("Bin-Houyé", 6.78 to -8.32, 18),
        )
        for ((name, point, minutes) in towns) {
            for ((where, north, east) in listOf(Triple("at", 0.0, 0.0), Triple("north of", 0.2, 0.0), Triple("west of", 0.0, -0.2))) {
                val resolution = Registry.resolve(Place(point.first + north, point.second + east, "Africa/Abidjan", "CI"))
                assertEquals("ci.default", resolution.entry.id, name)
                for (date in days()) {
                    val day = DayPipeline.day(resolution, date, zone)
                    val base = DayPipeline.day(abidjan, date, zone)
                    val label = "$where $name $date"
                    assertTrue(day.fajr >= base.fajr + minutes.minutes, "$label: Fajr before Abidjan's + $minutes")
                    assertTrue(day.maghrib >= base.maghrib + minutes.minutes, "$label: Maghrib before Abidjan's + $minutes")
                    assertTrue(day.isha >= base.isha + minutes.minutes, "$label: Isha before Abidjan's + $minutes")
                    assertTrue(day.sunrise <= base.sunrise + minutes.minutes, "$label: sunrise after Abidjan's + $minutes")
                    assertTrue(day.endOfEating <= base.endOfEating + minutes.minutes, "$label: the end of eating after Abidjan's + $minutes")
                }
            }
        }
    }

    @Test
    fun `a printed town's figure never makes a start earlier or an end later than the region gives at the place itself`() {
        val places = listOf(
            Place(9.61, -6.94, "Africa/Abidjan", "CI") to Generic.coteDIvoireRegion.method,
            Place(7.6, -5.6, "Africa/Abidjan", "CI") to Generic.coteDIvoireRegion.method,
            Place(5.2, -3.3, "Africa/Abidjan", "CI") to Generic.coteDIvoireRegion.method,
            Place(40.28256, 69.62216, "Asia/Dushanbe", "TJ") to Generic.tajikistanRegion.method,
            Place(39.77, 68.82, "Asia/Dushanbe", "TJ") to Generic.tajikistanRegion.method,
            Place(38.17, 73.97, "Asia/Dushanbe", "TJ") to Generic.tajikistanRegion.method,
        )
        for ((place, region) in places) {
            val zone = TimeZone.of(place.zoneId)
            val resolution = Registry.resolve(place)
            val alone = resolution.copy(method = region)
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone)
                val own = DayPipeline.day(alone, date, zone)
                val label = "${place.lat}, ${place.lon} $date"
                assertTrue(day.fajr >= own.fajr && day.dhuhr >= own.dhuhr && day.asr >= own.asr, "$label: an earlier start")
                assertTrue(day.maghrib >= own.maghrib && day.isha >= own.isha, "$label: an earlier start")
                assertTrue(day.sunrise <= own.sunrise && day.endOfEating <= own.endOfEating, "$label: a later end")
            }
        }
    }

    @Test
    fun `israel's pa towns take the levant region whose fajr carries the 138 s the pa's town tables asked for`() {
        // The proof is the gate's (safe-default-levant.tsv reads the PA's own tables for these towns, which may not be
        // committed here): without the 138 s the region's Fajr was a minute early at Acre, Safed, Nazareth and the other
        // northern towns in summer. ps.iftaa's own day at a town may run up to its gated minutes after the print, so it
        // is no bound to hold the region to; this holds the routing and the margin, so that losing either goes red.
        for (town in Levant.paTowns.filter { it.country == "IL" }) {
            val resolution = Registry.resolve(Place(town.point.lat, town.point.lon, "Asia/Jerusalem", "IL"))
            assertEquals("default.safe.levant", resolution.entry.id, town.name)
            assertEquals(EntryClass.D_NONE, resolution.entryClass, town.name)
        }
        val levant = Generic.levant.method.margins.fajr
        val world = Generic.world.method.margins.fajr
        assertEquals(138, levant - world)
    }
}
