package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Task 8: the engine's one entry point, with its settings, its adjustments and its cache. */
class EngineTest {

    private val istanbul = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
    private val riyadh = Place(24.68773, 46.72185, "Asia/Riyadh", "SA")
    private val london = Place(51.5074, -0.1278, "Europe/London", "GB")
    private val karachi = Place(24.8608, 67.0104, "Asia/Karachi", "PK")
    private val oslo = Place(59.91273, 10.74609, "Europe/Oslo", "NO")

    /** Twenty places across the classes: authorities (A to D), cautious places and the safe default. */
    private val samples = listOf(
        istanbul,
        Place(21.4225, 39.8262, "Asia/Riyadh", "SA"), // Makkah
        riyadh,
        Place(1.28967, 103.85007, "Asia/Singapore", "SG"),
        Place(3.1412, 101.68653, "Asia/Kuala_Lumpur", "MY"),
        Place(-6.21462, 106.84513, "Asia/Jakarta", "ID"),
        Place(30.06263, 31.24967, "Africa/Cairo", "EG"),
        Place(32.88743, 13.18733, "Africa/Tripoli", "LY"),
        Place(36.73225, 3.08746, "Africa/Algiers", "DZ", admin1 = "Algiers"),
        Place(33.58831, -7.61138, "Africa/Casablanca", "MA"),
        london,
        Place(53.48095, -2.23743, "Europe/London", "GB"), // Manchester
        Place(43.70643, -79.39864, "America/Toronto", "CA"),
        Place(40.71427, -74.00597, "America/New_York", "US"),
        karachi,
        Place(55.75204, 37.61781, "Europe/Moscow", "RU"),
        Place(69.6492, 18.9553, "Europe/Oslo", "NO"), // Tromsø
        Place(-26.20227, 28.04363, "Africa/Johannesburg", "ZA"),
        Place(6.45407, 3.39467, "Africa/Lagos", "NG"),
        Place(-13.83333, -171.76666, "Pacific/Apia", "WS"),
    )

    private val dates = listOf(LocalDate(2026, 6, 21), LocalDate(2026, 9, 26), LocalDate(2026, 12, 21), LocalDate(2027, 2, 20))

    private fun day(place: Place, date: LocalDate = LocalDate(2026, 9, 26), settings: EngineSettings = EngineSettings()) =
        PrayerEngine.dayTimes(place, date, settings)

    private fun PrayerDay.instants(): List<Instant> =
        listOf(fajr, sunrise, dhuhr, asr, asrOther, maghrib, isha, sunset, endOfEating) + listOfNotNull(imsak) +
            ends.values + earliestStart.values

    private fun Instant.isWholeMinute() = epochSeconds % 60 == 0L && nanosecondsOfSecond == 0

    // Every place, every class.

    @Test
    fun `every instant is a whole minute and every day in order for twenty places across the classes`() {
        val classes = mutableSetOf<EntryClass>()
        for (place in samples) {
            for (date in dates) {
                val result = day(place, date)
                classes += result.effective.entryClass
                assertTrue(result.day.instants().all { it.isWholeMinute() }, "${result.effectiveEntry.id} on $date")
                assertTrue(Invariants.holds(result.day), "${result.effectiveEntry.id} on $date is out of order")
                assertEquals(date, result.day.date)
            }
        }
        assertTrue(
            classes.containsAll(setOf(EntryClass.A, EntryClass.C, EntryClass.D_AUTHORITY, EntryClass.D_NONE)),
            "classes covered: $classes",
        )
    }

    @Test
    fun `the day is the pipeline's day when nothing is chosen`() {
        val result = day(istanbul)
        val zone = TimeZone.of(istanbul.zoneId)
        // At the point as the app stores it: three decimals.
        val stored = istanbul.copy(lat = 41.008, lon = 28.978)
        val expected = DayPipeline.day(Registry.resolve(stored), LocalDate(2026, 9, 26), zone)
        assertEquals(expected, result.day)
        assertEquals("tr.diyanet", result.effectiveEntry.id)
        assertEquals(result.resolution, result.effective)
        assertEquals(emptySet(), result.pausedAdjustments)
    }

    // The cache.

    @Test
    fun `the cache returns the same object for the same key`() {
        val date = LocalDate(2026, 9, 27)
        val first = day(istanbul, date)
        assertSame(first, day(istanbul, date))
        // A point within the same thousandth of a degree, the stored precision, is the same key.
        assertSame(first, day(istanbul.copy(lat = istanbul.lat + 0.0001), date))
        assertSame(first, day(istanbul.copy(lat = 41.008, lon = 28.978), date))
        assertNotSame(first, day(istanbul.copy(lat = istanbul.lat + 0.001), date))
        assertSame(first, day(istanbul.copy(countryCode = "tr"), date))
        assertNotSame(first, day(istanbul, date.plus(1, DateTimeUnit.DAY)))
        assertNotSame(first, day(istanbul, date, EngineSettings(school = SchoolChoice.Hanafi)))
    }

    @Test
    fun `the cache stays bounded`() {
        val start = LocalDate(2027, 1, 1)
        repeat(PrayerEngine.CACHE_SIZE + 40) { day(london, start.plus(it, DateTimeUnit.DAY)) }
        assertTrue(PrayerEngine.cachedDays <= PrayerEngine.CACHE_SIZE, "held ${PrayerEngine.cachedDays}")
    }

    @Test
    fun `the cache keeps the most recently used day`() {
        val kept = day(london, LocalDate(2028, 1, 1))
        val start = LocalDate(2028, 2, 1)
        repeat(PrayerEngine.CACHE_SIZE + 40) {
            day(london, start.plus(it, DateTimeUnit.DAY))
            // Asked for again on every step: the least recently used go, this one never does.
            day(london, LocalDate(2028, 1, 1))
        }
        assertSame(kept, day(london, LocalDate(2028, 1, 1)))
    }

    @Test
    fun `the bounded cache answers every caller of a key with one object`() {
        val cache = BoundedCache<Int, Any>(2)
        val a = cache.getOrPut(1) { Any() }
        assertSame(a, cache.getOrPut(1) { Any() })
        cache.getOrPut(2) { Any() }
        cache.getOrPut(1) { Any() } // 1 is now the most recent
        cache.getOrPut(3) { Any() } // evicts 2
        assertEquals(2, cache.size)
        assertSame(a, cache.getOrPut(1) { Any() })
    }

    // The Saudi rule.

    @Test
    fun `saudi fajr later adds five minutes under umm al qura in saudi arabia only`() {
        val later = EngineSettings(saudiFajrLater = true)
        val base = day(riyadh).day
        val moved = day(riyadh, settings = later).day
        assertEquals(base.fajr + 5.minutes, moved.fajr)
        assertEquals(base.endOfEating, moved.endOfEating)
        assertEquals(base.copy(fajr = moved.fajr), moved)
        // Nowhere else, and not under another timetable in Saudi Arabia.
        assertEquals(day(istanbul).day, day(istanbul, settings = later).day)
        val mwl = EngineSettings(timetable = TimetableChoice.Entry("other.mwl"), timetableConfirmed = true)
        assertEquals(day(riyadh, settings = mwl).day, day(riyadh, settings = mwl.copy(saudiFajrLater = true)).day)
        val otherUmmAlQura = EngineSettings(timetable = TimetableChoice.Entry("other.ummalqura"), timetableConfirmed = true)
        assertEquals(
            day(riyadh, settings = otherUmmAlQura).day,
            day(riyadh, settings = otherUmmAlQura.copy(saudiFajrLater = true)).day,
        )
    }

    // Choices and their scope.

    @Test
    fun `a scoped choice outside its area falls back to automatic`() {
        val automatic = day(istanbul)
        // London Unified and Muhammadiyah are named timetables, their places' own (ruling R50).
        for (id in listOf("gb.london.lupt", "sa.ummalqura", "gb.cautious", "us.chicago", "id.muhammadiyah", "no.such.entry")) {
            val chosen = day(istanbul, settings = EngineSettings(timetable = TimetableChoice.Entry(id)))
            assertEquals("tr.diyanet", chosen.effectiveEntry.id, id)
            assertEquals(automatic.day, chosen.day, id)
            assertEquals("tr.diyanet", chosen.resolution.entry.id, id)
        }
    }

    @Test
    fun `a global choice applies anywhere and automatic still names the place's own`() {
        val chosen = day(istanbul, settings = EngineSettings(timetable = TimetableChoice.Entry("other.mwl"), timetableConfirmed = true))
        assertEquals("other.mwl", chosen.effectiveEntry.id)
        assertEquals(false, chosen.timetablePaused)
        assertEquals("tr.diyanet", chosen.resolution.entry.id)
        // MWL and Diyanet's refitted method can meet on a given minute (Task 7b), so compare a month.
        val differs = (0 until 30).any { offset ->
            val date = LocalDate(2026, 9, 1).plus(offset, DateTimeUnit.DAY)
            val own = day(istanbul, date).day
            val mwl = day(istanbul, date, EngineSettings(timetable = TimetableChoice.Entry("other.mwl"), timetableConfirmed = true)).day
            own.fajr != mwl.fajr || own.isha != mwl.isha
        }
        assertTrue(differs, "MWL's Fajr and Isha never differ from Diyanet's in September")
    }

    // A chosen timetable not yet confirmed (ruling R52).

    private val manchester = Place(53.48095, -2.23743, "Europe/London", "GB")

    @Test
    fun `an unconfirmed timetable is never earlier than automatic and a confirmed one is its own`() {
        val isna = EngineSettings(timetable = TimetableChoice.Entry("other.isna"))
        var ishaEarlierOnce = false
        var date = LocalDate(2026, 1, 1)
        while (date.year == 2026) {
            val automatic = day(manchester, date).day
            val paused = day(manchester, date, isna)
            val confirmed = day(manchester, date, isna.copy(timetableConfirmed = true))
            assertEquals("other.isna", paused.effectiveEntry.id)
            assertTrue(paused.timetablePaused && !confirmed.timetablePaused, "$date")
            with(paused.day) {
                assertTrue(fajr >= automatic.fajr && dhuhr >= automatic.dhuhr && asr >= automatic.asr, "$date")
                assertTrue(maghrib >= automatic.maghrib && isha >= automatic.isha, "$date")
                assertTrue(sunrise <= automatic.sunrise && endOfEating <= automatic.endOfEating, "$date")
                assertTrue(Invariants.holds(this), "$date")
                // And never earlier than the timetable itself either.
                assertTrue(fajr >= confirmed.day.fajr && isha >= confirmed.day.isha, "$date")
            }
            val zone = TimeZone.of(manchester.zoneId)
            val own = DayPipeline.day(confirmed.effective, date, zone, confirmed.school)
            assertEquals(own, confirmed.day, "$date")
            if (confirmed.day.isha < automatic.isha) ishaEarlierOnce = true
            date = date.plus(7, DateTimeUnit.DAY)
        }
        // The pause matters: ISNA's 15° Isha is before the cautious times' on some days.
        assertTrue(ishaEarlierOnce)
    }

    @Test
    fun `an earlier adjustment confirmed under a paused timetable is paused too`() {
        // The review's probe: an unconfirmed ISNA in Manchester with Isha −10 confirmed under
        // ISNA gave an Isha ten minutes before Automatic's (22:40Z against 22:50Z).
        val date = LocalDate(2026, 6, 21)
        val settings = EngineSettings(
            timetable = TimetableChoice.Entry("other.isna"),
            adjustmentsMinutes = mapOf(Prayer.ISHA to -10),
            confirmedAdjustments = mapOf(Prayer.ISHA to "other.isna"),
        )
        val automatic = day(manchester, date).day
        val paused = day(manchester, date, settings)
        assertTrue(paused.timetablePaused)
        assertEquals(setOf(Prayer.ISHA), paused.pausedAdjustments)
        assertTrue(paused.day.isha >= automatic.isha, "Isha ${paused.day.isha} before Automatic's ${automatic.isha}")
        assertEquals(day(manchester, date, settings.copy(adjustmentsMinutes = emptyMap())).day, paused.day)
        // Once the timetable is confirmed, the confirmation under it holds again.
        val confirmed = day(manchester, date, settings.copy(timetableConfirmed = true))
        assertEquals(emptySet(), confirmed.pausedAdjustments)
        val own = day(manchester, date, settings.copy(timetableConfirmed = true, adjustmentsMinutes = emptyMap())).day
        assertEquals(own.isha - 10.minutes, confirmed.day.isha)
    }

    @Test
    fun `a paused other method at tromso is never earlier than irn's makkah time and repairs no day`() {
        // Review I1 (ruling R82): under the midnight sun a sun-based method's nearest-latitude sunrise
        // is before IRN's Makkah-time Fajr. The paused day keeps the later Fajr and that Fajr's own
        // sunrise: the start's promise holds and the sunrise's gives way.
        val tromso = Place(69.6489, 18.95508, "Europe/Oslo", "NO")
        var sunriseAfterTheChosen = 0
        for (id in listOf("other.mwl", "other.moonsighting", "other.turkey")) {
            val chosen = EngineSettings(timetable = TimetableChoice.Entry(id))
            var date = LocalDate(2026, 1, 1)
            while (date.year == 2026) {
                val automatic = day(tromso, date).day
                val own = day(tromso, date, chosen.copy(timetableConfirmed = true)).day
                val paused = day(tromso, date, chosen)
                assertTrue(paused.timetablePaused, "$id $date")
                with(paused.day) {
                    assertTrue(fajr >= automatic.fajr && dhuhr >= automatic.dhuhr && asr >= automatic.asr, "$id $date")
                    assertTrue(maghrib >= automatic.maghrib && isha >= automatic.isha, "$id $date")
                    assertTrue(fajr >= own.fajr && isha >= own.isha, "$id $date")
                    assertTrue(endOfEating <= automatic.endOfEating && endOfEating <= own.endOfEating, "$id $date")
                    val earlierSunrise = minOf(own.sunrise, automatic.sunrise)
                    val fajrDay = if (fajr == automatic.fajr) automatic else own
                    assertEquals(if (fajr < earlierSunrise) earlierSunrise else fajrDay.sunrise, sunrise, "$id $date")
                    // The pause repairs nothing itself (a few winter days of the chosen method's own are).
                    assertTrue(Invariants.holds(this) && repaired == (own.repaired || automatic.repaired), "$id $date: $this")
                    if (sunrise > own.sunrise) sunriseAfterTheChosen++
                }
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        assertTrue(sunriseAfterTheChosen > 100, "$sunriseAfterTheChosen days")
    }

    @Test
    fun `a confirmation given under another automatic entry is paused again abroad`() {
        // Ruling R70: an Other method confirmed in London (under London Unified) is held to
        // Automatic in İstanbul, where Automatic is Diyanet, until it is confirmed there.
        val londonAutomatic = PrayerEngine.automaticEntryId(london)
        val istanbulAutomatic = PrayerEngine.automaticEntryId(istanbul)
        assertEquals("gb.london.lupt", londonAutomatic)
        assertEquals("tr.diyanet", istanbulAutomatic)
        val mwl = EngineSettings(timetable = TimetableChoice.Entry("other.mwl"), timetableConfirmed = true, timetableConfirmedUnder = londonAutomatic)
        assertFalse(day(london, settings = mwl).timetablePaused)
        val abroad = day(istanbul, settings = mwl)
        assertTrue(abroad.timetablePaused)
        assertEquals(day(istanbul, settings = mwl.copy(timetableConfirmed = false)).day, abroad.day)
        val confirmedHere = day(istanbul, settings = mwl.copy(timetableConfirmedUnder = istanbulAutomatic))
        assertFalse(confirmedHere.timetablePaused)
        assertNotEquals(abroad.day, confirmedHere.day)
    }

    @Test
    fun `automatic and a choice that falls back are never paused`() {
        assertEquals(false, day(istanbul).timetablePaused)
        assertEquals(false, day(istanbul, settings = EngineSettings(timetable = TimetableChoice.Entry("gb.london.lupt"))).timetablePaused)
    }

    // The Asr school.

    @Test
    fun `automatic asr is the place's own school whatever the timetable`() {
        assertEquals(AsrSchool.STANDARD, day(istanbul).school)
        assertEquals(AsrSchool.HANAFI, day(karachi).school)
        // The UK's majority school is not known: the later time leads (spec §3.7).
        assertEquals(AsrSchool.HANAFI, day(london).school)
        val mwlInKarachi = day(karachi, settings = EngineSettings(timetable = TimetableChoice.Entry("other.mwl")))
        assertEquals(AsrSchool.HANAFI, mwlInKarachi.school)
    }

    @Test
    fun `a chosen school leads and the other is asrOther`() {
        val standard = day(karachi, settings = EngineSettings(school = SchoolChoice.Standard))
        val hanafi = day(karachi, settings = EngineSettings(school = SchoolChoice.Hanafi))
        assertEquals(AsrSchool.STANDARD, standard.school)
        assertTrue(hanafi.day.asr > standard.day.asr)
        assertEquals(hanafi.day.asr, standard.day.asrOther)
        assertEquals(standard.day.asr, hanafi.day.asrOther)
    }

    // The legacy high-latitude rule.

    @Test
    fun `the legacy high latitude rule applies to other methods only`() {
        val june = LocalDate(2026, 6, 21)
        val mwl = EngineSettings(timetable = TimetableChoice.Entry("other.mwl"), timetableConfirmed = true)
        val own = day(oslo, june, mwl)
        val seventh = day(oslo, june, mwl.copy(legacyHighLatitude = HighLatRule.Legacy.SEVENTH))
        assertEquals(HighLatRule.Legacy(HighLatRule.Legacy.SEVENTH), seventh.effective.method!!.highLatitude)
        assertNotEquals(own.day, seventh.day)
        // Automatic's authority brings its own rule, and an unknown kind is ignored.
        assertEquals(day(oslo, june).day, day(oslo, june, EngineSettings(legacyHighLatitude = HighLatRule.Legacy.SEVENTH)).day)
        assertEquals(own.day, day(oslo, june, mwl.copy(legacyHighLatitude = "sixth")).day)
    }

    // Adjustments.

    private fun confirmed(place: Place, vararg adjustments: Pair<Prayer, Int>): EngineSettings {
        val entry = Registry.resolve(place).entry.id
        return EngineSettings(
            adjustmentsMinutes = adjustments.toMap(),
            confirmedAdjustments = adjustments.associate { it.first to entry },
        )
    }

    /**
     * [place]'s own sunset on [date] with its Automatic method's horizon (a cautious entry's lowest
     * member horizon; the exact sun, the zone's offset at local noon), unrounded: an earlier
     * Maghrib's floor; null where the sun does not set.
     */
    private fun ownSunset(place: Place, date: LocalDate): Double? {
        val zone = TimeZone.of(place.zoneId)
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val resolution = Registry.resolve(place)
        val horizon = resolution.method?.horizonDeg ?: resolution.members.minOf { it.method.horizonDeg }
        return SunClock(place.lat, place.lon, date, offset, SunModel.EXACT).altitudeTime(horizon, morning = false)
    }

    /** [ownSunset] rounded up, as a start. */
    private fun ownSunsetFloor(place: Place, date: LocalDate): Instant =
        Instant.fromEpochSeconds(ceil(ownSunset(place, date)!! / 60).toLong() * 60)

    @Test
    fun `a confirmed earlier maghrib in riyadh stops at sunset`() {
        val base = day(riyadh).day
        val adjusted = day(riyadh, settings = confirmed(riyadh, Prayer.MAGHRIB to -10)).day
        assertEquals(minOf(base.maghrib, ownSunsetFloor(riyadh, LocalDate(2026, 9, 26))), adjusted.maghrib)
        assertTrue(adjusted.maghrib >= adjusted.sunset)
        assertTrue(Invariants.holds(adjusted))
    }

    @Test
    fun `a confirmed earlier maghrib west of a unit's point never lands before the user's own sunset`() {
        // The day's sunset is the earlier of the unit's point and the user's; west of the point the
        // user's own sun sets later, and the floor is that one (review C1: up to 112 s before it).
        // At the precision the app stores a location, which is the point the engine computes at.
        val staines = Place(51.433, -0.512, "Europe/London", "GB")
        val westOfIstanbul = Place(41.008, 28.765, "Europe/Istanbul", "TR")
        for (place in listOf(staines, westOfIstanbul)) {
            assertTrue(Registry.resolve(place).method!!.fixedPoint != null, "$place has a table's point beside it")
            var date = LocalDate(2026, 1, 1)
            var belowPoint = 0
            var floored = 0
            while (date < LocalDate(2027, 1, 1)) {
                val base = day(place, date).day
                val adjusted = day(place, date, confirmed(place, Prayer.MAGHRIB to -15)).day
                val floor = ownSunsetFloor(place, date)
                // Never below the user's own sunset, unless the timetable's own Maghrib already is
                // (its sun model's seconds): an adjustment then leaves it where it is.
                val lowest = minOf(base.maghrib.epochSeconds.toDouble(), ownSunset(place, date)!!)
                assertTrue(adjusted.maghrib.epochSeconds >= lowest, "$place $date: Maghrib ${adjusted.maghrib} before its own sunset")
                assertEquals(maxOf(base.maghrib - 15.minutes, minOf(base.maghrib, floor)), adjusted.maghrib, "$place $date")
                if (adjusted.maghrib == floor) floored++
                if (base.sunset + 1.minutes < floor) belowPoint++
                date = date.plus(1, DateTimeUnit.DAY)
            }
            assertTrue(floored > 300, "$place: the floor holds the earlier Maghrib ($floored days)")
            assertTrue(belowPoint > 0, "$place: the point's sunset is a minute earlier on some day")
        }
    }

    @Test
    fun `a confirmed earlier dhuhr stops a minute after the transit`() {
        val date = LocalDate(2026, 9, 26)
        val base = day(riyadh, date).day
        val adjusted = day(riyadh, date, confirmed(riyadh, Prayer.DHUHR to -30)).day
        val zone = TimeZone.of(riyadh.zoneId)
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val transit = SunClock(riyadh.lat, riyadh.lon, date, offset, SunModel.EXACT).transit()
        val floor = Instant.fromEpochSeconds(ceil((transit + 60) / 60).toLong() * 60)
        assertEquals(minOf(base.dhuhr, floor), adjusted.dhuhr)
    }

    /** The astronomical Standard Asr at [place] on [date], rounded up: an earlier Asr's floor (ruling R51). */
    private fun standardAsrFloor(place: Place, date: LocalDate): Instant {
        val zone = TimeZone.of(place.zoneId)
        val offset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        val asr = SunClock(place.lat, place.lon, date, offset, SunModel.EXACT).asr(1.0, AsrModel.EXACT_MOMENT)!!
        return Instant.fromEpochSeconds(ceil(asr / 60).toLong() * 60)
    }

    @Test
    fun `a confirmed earlier asr under hanafi stops at the astronomical standard asr`() {
        val date = LocalDate(2026, 9, 26)
        val hanafi = confirmed(karachi, Prayer.ASR to -59).copy(school = SchoolChoice.Hanafi)
        val base = day(karachi, date, EngineSettings(school = SchoolChoice.Hanafi)).day
        val adjusted = day(karachi, date, hanafi).day
        val floor = standardAsrFloor(karachi, date)
        assertEquals(maxOf(base.asr - 59.minutes, minOf(base.asr, floor)), adjusted.asr)
        assertTrue(adjusted.asr >= floor && adjusted.asr < base.asr)
    }

    @Test
    fun `a confirmed earlier asr under the standard school moves down to the astronomical standard asr`() {
        // Diyanet prints the Standard Asr with its own minutes after the shadow: a confirmed −3 there
        // moves it, as far as the shadow itself and no further.
        val date = LocalDate(2026, 9, 26)
        val base = day(istanbul, date).day
        val floor = standardAsrFloor(istanbul, date)
        assertTrue(floor < base.asr, "Diyanet's Asr is later than the shadow")
        val three = day(istanbul, date, confirmed(istanbul, Prayer.ASR to -3))
        assertEquals(maxOf(base.asr - 3.minutes, floor), three.day.asr)
        assertTrue(three.day.asr < base.asr)
        assertEquals(emptySet(), three.pausedAdjustments)
        val far = day(istanbul, date, confirmed(istanbul, Prayer.ASR to -59)).day
        assertEquals(floor, far.asr)
    }

    @Test
    fun `a later adjustment moves only its prayer`() {
        val base = day(istanbul).day
        val adjusted = day(istanbul, settings = EngineSettings(adjustmentsMinutes = mapOf(Prayer.ISHA to 5))).day
        assertEquals(base.isha + 5.minutes, adjusted.isha)
        assertEquals(base.copy(isha = adjusted.isha, ends = adjusted.ends), adjusted)
    }

    @Test
    fun `sunrise and the end of eating never move later`() {
        val base = day(istanbul).day
        val later = day(istanbul, settings = EngineSettings(adjustmentsMinutes = mapOf(Prayer.SUNRISE to 10, Prayer.FAJR to 10))).day
        assertEquals(base.sunrise, later.sunrise)
        assertEquals(base.endOfEating, later.endOfEating)
        assertEquals(base.fajr + 10.minutes, later.fajr)
        val earlier = day(istanbul, settings = confirmed(istanbul, Prayer.FAJR to -20)).day
        assertEquals(base.fajr - 20.minutes, earlier.fajr)
        assertTrue(earlier.endOfEating <= earlier.fajr && earlier.endOfEating <= base.endOfEating)
    }

    @Test
    fun `adjustments keep the order and the floors whatever their size`() {
        val sizes = listOf(-59, -30, -10, 10, 30, 59)
        for (place in listOf(istanbul, riyadh, london, oslo, samples[16])) {
            for (date in listOf(LocalDate(2026, 6, 21), LocalDate(2026, 12, 21))) {
                val base = day(place, date).day
                for (size in sizes) {
                    val all = Prayer.entries.map { it to size }.toTypedArray()
                    val adjusted = day(place, date, confirmed(place, *all)).day
                    val label = "$place $date $size"
                    assertTrue(Invariants.holds(adjusted), label)
                    assertTrue(adjusted.instants().all { it.isWholeMinute() }, label)
                    assertTrue(adjusted.sunrise <= base.sunrise && adjusted.endOfEating <= base.endOfEating, label)
                    ownSunset(place, date)?.let { assertTrue(adjusted.maghrib.epochSeconds >= minOf(base.maghrib.epochSeconds.toDouble(), it), label) }
                    if (size > 0) {
                        // Later only: no start before its own unadjusted time.
                        assertTrue(adjusted.fajr >= base.fajr && adjusted.dhuhr >= base.dhuhr, label)
                        assertTrue(adjusted.asr >= base.asr && adjusted.maghrib >= base.maghrib && adjusted.isha >= base.isha, label)
                    }
                }
            }
        }
    }

    @Test
    fun `a later isha stops a minute before the next end of eating`() {
        // Tromsø from April to July: Isha +59 used to pass the next day's end of eating on most days.
        val tromso = samples[16]
        val later = EngineSettings(adjustmentsMinutes = mapOf(Prayer.ISHA to 59))
        var date = LocalDate(2026, 4, 1)
        var capped = 0
        while (date < LocalDate(2026, 8, 1)) {
            val base = day(tromso, date).day
            val adjusted = day(tromso, date, later).day
            val end = base.ends[Prayer.ISHA]
            if (end != null) {
                assertTrue(adjusted.isha < end, "$date: Isha ${adjusted.isha} at or after its end $end")
                assertEquals(end, adjusted.ends[Prayer.ISHA], "$date")
                if (adjusted.isha < base.isha + 59.minutes) capped++
            }
            assertTrue(adjusted.isha >= base.isha && Invariants.holds(adjusted), "$date")
            date = date.plus(1, DateTimeUnit.DAY)
        }
        assertTrue(capped > 0)
    }

    @Test
    fun `an earlier adjustment confirmed under another timetable is paused and reported`() {
        val base = day(riyadh)
        val elsewhere = EngineSettings(
            adjustmentsMinutes = mapOf(Prayer.MAGHRIB to -2, Prayer.ISHA to 3),
            confirmedAdjustments = mapOf(Prayer.MAGHRIB to "tr.diyanet"),
        )
        val paused = day(riyadh, settings = elsewhere)
        assertEquals(setOf(Prayer.MAGHRIB), paused.pausedAdjustments)
        assertEquals(base.day.maghrib, paused.day.maghrib)
        assertEquals(base.day.isha + 3.minutes, paused.day.isha)
        // Never confirmed at all: paused too (spec §8).
        val unconfirmed = day(riyadh, settings = EngineSettings(adjustmentsMinutes = mapOf(Prayer.FAJR to -2)))
        assertEquals(setOf(Prayer.FAJR), unconfirmed.pausedAdjustments)
        assertEquals(base.day, unconfirmed.day)
        // Confirmed under the timetable in use: applied.
        val applied = day(riyadh, settings = confirmed(riyadh, Prayer.FAJR to -2))
        assertEquals(emptySet(), applied.pausedAdjustments)
        assertEquals(base.day.fajr - 2.minutes, applied.day.fajr)
    }

    @Test
    fun `a confirmation follows the timetable in use`() {
        val underMwl = EngineSettings(
            timetable = TimetableChoice.Entry("other.mwl"),
            timetableConfirmed = true,
            adjustmentsMinutes = mapOf(Prayer.ISHA to -5),
            confirmedAdjustments = mapOf(Prayer.ISHA to "other.mwl"),
        )
        val mwl = day(istanbul, settings = underMwl.copy(adjustmentsMinutes = emptyMap()))
        assertEquals(mwl.day.isha - 5.minutes, day(istanbul, settings = underMwl).day.isha)
        // Back on Automatic (Diyanet), the same confirmation no longer holds.
        val automatic = day(istanbul, settings = underMwl.copy(timetable = TimetableChoice.Automatic))
        assertEquals(setOf(Prayer.ISHA), automatic.pausedAdjustments)
        assertEquals(day(istanbul).day, automatic.day)
    }
}
