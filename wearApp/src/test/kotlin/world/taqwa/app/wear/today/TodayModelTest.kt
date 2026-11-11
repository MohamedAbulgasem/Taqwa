package world.taqwa.app.wear.today

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.LongestCity
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.WatchUnits

/**
 * Today (plan 3 Task 5): the phone's countdown ring to the next start, and the day's six times
 * beneath it (five where the phone hides Sunrise), each as the phone writes and says it. On the
 * committed samples (`TestFeeds`, `Fixtures.capeTownSample()` and `capeTownSamples()`): Cape Town,
 * whose every day is Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04 and Isha 20:24.
 * `TestFeeds.at` counts days from Sunday 18 October, the sample's second day (`days[1]`).
 */
class TodayModelTest {

    private val sample = TestFeeds.sample
    private val en12 = TestFeeds.other("en12")
    private val arLy12 = TestFeeds.other("ar-ly12")
    private val arSa = TestFeeds.other("ar-sa")

    /** 14:58:24 on the mock Sunday: the drawing's "1:11:36" to Asr at 16:10. */
    private val at145824 = TestFeeds.at(0, 14, 58, 24)

    private fun todayOf(feed: WatchFeed, now: Long, update: Boolean = false): TodayModel =
        assertNotNull(today(feed, now, update), "Today has a ring at $now")

    private fun rowsOf(feed: WatchFeed, now: Long, rule: LitRule = LitRule.LIST): List<RowModel> =
        dayRows(feed, now, rule).also { assertEquals(6, it.size, "the day's six times") }

    @Test
    fun atTwoFiftyEightTheRingCountsDownToAsr() {
        val ring = todayOf(sample, at145824).ring

        assertEquals("ASR IN", ring.label)
        assertEquals("1:11:36", ring.countdown)
        assertEquals("16:10", ring.time)
        // From Dhuhr at 12:33 to Asr at 16:10 is 13,020 s, of which 8,724 have passed.
        assertEquals(8_724f / 13_020f, ring.progress, 1e-4f)
        assertEquals("Asr in 1 hour 11 minutes, at 16:10", ring.spoken)
        assertFalse(ring.rtl)
        assertFalse(ring.notice)
    }

    @Test
    fun arabicKeepsTheNameAndMirrorsTheRing() {
        val ring = todayOf(arLy12, at145824).ring

        // No capitals in Arabic: the phone's own "متبقٍّ على %1$s" with the name as it is.
        assertEquals("متبقٍّ على العصر", ring.label)
        assertTrue(ring.rtl)
        assertEquals("1:11:36", ring.countdown)
        assertEquals("4:10${NBSP}م", ring.time)
    }

    @Test
    fun capitalsOnlyUnderALatinInterface() {
        // A phone whose interface is not in Latin letters writes its label as it is, whatever its letters.
        val notLatin = sample.copy(ui = sample.ui.copy(latinScript = false))

        assertEquals("Asr in", todayOf(notLatin, at145824).ring.label)
    }

    @Test
    fun theLabelIsCasedInThePhonesLanguage() {
        // Turkish capitals dot their i, as the face's label does (ComplicationFields.nextPrayer).
        val turkish = sample.copy(ui = sample.ui.copy(language = "tr"))

        assertEquals("ASR İN", todayOf(turkish, at145824).ring.label)
    }

    @Test
    fun saudiTimesInArabicIndicAndTheRingInWestern() {
        val today = todayOf(arSa, at145824)

        assertEquals("١٦:١٠", today.ring.time)
        assertEquals("1:11:36", today.ring.countdown)
        assertEquals("١٦:١٠", today.rows[3].time)
    }

    @Test
    fun theRingTakesTheFeedsDigitsWhenItNamesNoneOfItsOwn() {
        // An Urdu phone's ring writes its H:MM:SS in the digits of its clock strings (ringDigitZero null).
        val urdu = sample.copy(ui = sample.ui.copy(digitZero = '۰', ringDigitZero = null))

        assertEquals("۱:۱۱:۳۶", todayOf(urdu, at145824).ring.countdown)
    }

    @Test
    fun twelveHourRowsCarryTheDayPeriod() {
        val asr = rowsOf(en12, TestFeeds.at(0, 14, 58))[3]

        assertEquals("4:10${NBSP}PM", asr.time)
        assertEquals("Asr, ${FSI}4:10${NBSP}PM$PDI", asr.spoken)
    }

    @Test
    fun aTwelveHourSunriseRowCarriesItsDayPeriod() {
        val rows = rowsOf(en12, TestFeeds.at(0, 14, 58))
        val sunrise = rows[1]

        assertEquals("6:01${NBSP}AM", sunrise.time)
        assertTrue(sunrise.small)
        assertEquals("Sunrise, ${FSI}6:01${NBSP}AM$PDI", sunrise.spoken)
        for (row in rows) assertTrue(row.time.endsWith("${NBSP}AM") || row.time.endsWith("${NBSP}PM"), row.time)
    }

    @Test
    fun twelveHourSpeechSaysTheDayPeriod() {
        val ring = todayOf(en12, at145824).ring

        assertEquals("4:10${NBSP}PM", ring.time)
        assertEquals("Asr in 1 hour 11 minutes, at ${FSI}4:10${NBSP}PM$PDI", ring.spoken)
    }

    @Test
    fun aNewerVersionTakesTheTimesPlace() {
        val ring = todayOf(sample, at145824, update = true).ring

        assertEquals("Update Taqwa on your watch", ring.time)
        assertTrue(ring.notice)
        assertEquals("1:11:36", ring.countdown)
        assertEquals("Asr in 1 hour 11 minutes, at 16:10, Update Taqwa on your watch", ring.spoken)
    }

    @Test
    fun anArabicUpdateIsJoinedByTheArabicComma() {
        val ring = todayOf(arSa, at145824, update = true).ring

        assertEquals("حدّث تقوى على ساعتك", ring.time)
        assertTrue(ring.spoken.endsWith("، حدّث تقوى على ساعتك"), ring.spoken)
    }

    @Test
    fun withoutSpokenWordsTheRingSaysWhatItShows() {
        // A feed from before the spoken words: the screen's own label and time.
        val unspoken = sample.copy(ui = sample.ui.copy(spoken = null))

        assertEquals("ASR IN 16:10", todayOf(unspoken, at145824).ring.spoken)
    }

    @Test
    fun underAMinuteTheRingSaysSo() {
        val ring = todayOf(sample, TestFeeds.at(0, 16, 9, 30)).ring

        assertEquals("0:00:30", ring.countdown)
        assertEquals("Asr in under a minute, at 16:10", ring.spoken)
    }

    @Test
    fun theHoursAreNotPaddedAndAStartBeginsTheRingAgain() {
        // 04:10 on Monday, before its Fajr at 4:33: the night's countdown, its hour unpadded.
        assertEquals("0:23:00", todayOf(sample, TestFeeds.at(1, 4, 10)).ring.countdown)

        // The instant Asr begins, the ring starts again from empty, counting to Maghrib at 19:04.
        val atAsr = todayOf(sample, TestFeeds.at(0, 16, 10)).ring
        assertEquals("MAGHRIB IN", atAsr.label)
        assertEquals("2:54:00", atAsr.countdown)
        assertEquals(0f, atAsr.progress)
    }

    @Test
    fun atTheLargestFontTheCountdownKeepsItsSize() {
        assertEquals(38f, TodayType.countdown.range(1.3f).start)
        assertEquals(38f, TodayType.countdown.range(1.3f).endInclusive)
        assertEquals(10.5f, TodayType.label.range(1.3f).start)
        assertEquals(13.65f, TodayType.label.range(1.3f).endInclusive, 1e-3f)
    }

    @Test
    fun theLongestCityIsKeptWhole() {
        val name = LongestCity.name()
        val far = sample.copy(place = sample.place.copy(city = name))

        assertEquals(name, todayOf(far, at145824).city)
        assertEquals(1, TodayType.city.maxLines)
    }

    @Test
    fun theLongestCityIsTheBundlesLongest() {
        // The bundle's longest on 24 October was 91 bytes (a Bengali name of 33 characters): a
        // helper that read another column, or cities.csv alone, would find a shorter one.
        assertTrue(LongestCity.name().encodeToByteArray().size >= 91, LongestCity.name())
    }

    @Test
    fun theListFollowsThePrayerScreensRule() {
        val rows = rowsOf(sample, TestFeeds.at(0, 14, 58))
        val (fajr, sunrise, dhuhr, asr, maghrib) = rows

        assertEquals(listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"), rows.map { it.name })
        assertEquals(RowStatus.PASSED, fajr.status)
        assertEquals("Passed", fajr.state)
        assertEquals(RowStatus.PASSED, sunrise.status)
        assertTrue(sunrise.small)
        assertEquals(RowStatus.CURRENT, dhuhr.status)
        assertEquals("Current prayer", dhuhr.state)
        assertEquals(RowStatus.UPCOMING, asr.status)
        assertEquals("Next prayer", asr.state)
        assertEquals(RowStatus.UPCOMING, maghrib.status)
        assertNull(maghrib.state)
        assertEquals(listOf(false, true, false, false, false, false), rows.map { it.small })

        // Fajr stops being current at sunrise, and before Fajr nothing is lit.
        assertTrue(rowsOf(sample, TestFeeds.at(0, 6, 40)).none { it.status == RowStatus.CURRENT })
        assertTrue(rowsOf(sample, TestFeeds.at(1, 4, 10)).none { it.status == RowStatus.CURRENT })
    }

    @Test
    fun sunriseIsNeverCurrent() {
        // Between Sunrise and Dhuhr the glance rule keeps Fajr lit, never Sunrise.
        val rows = rowsOf(sample, TestFeeds.at(0, 9, 0), LitRule.GLANCE)

        assertEquals(RowStatus.PASSED, rows[1].status)
        assertEquals(RowStatus.CURRENT, rows[0].status)

        // A time has passed from its own instant on, as the phone's rows count it.
        assertEquals(RowStatus.PASSED, rowsOf(sample, TestFeeds.at(0, 6, 1))[1].status)
        assertEquals(RowStatus.UPCOMING, rowsOf(sample, TestFeeds.at(0, 6, 0, 59))[1].status)
    }

    @Test
    fun theGlanceRuleKeepsFajrUntilDhuhr() {
        val rows = rowsOf(sample, TestFeeds.at(0, 6, 40), LitRule.GLANCE)

        assertEquals(RowStatus.CURRENT, rows[0].status)
        assertEquals("Current prayer", rows[0].state)
    }

    @Test
    fun afterIshaTheNextIsTomorrowsFajr() {
        val today = todayOf(sample, TestFeeds.at(0, 22, 30))

        assertEquals("FAJR IN", today.ring.label)
        assertEquals("4:33", today.ring.time)
        // The rows are still Sunday's, until midnight: Isha lit, the rest passed, and no row is
        // the next start, which is Monday's.
        assertEquals(RowStatus.CURRENT, today.rows[5].status)
        assertEquals(List(5) { RowStatus.PASSED }, today.rows.take(5).map { it.status })
        assertTrue(today.rows.none { it.state == "Next prayer" })
    }

    @Test
    fun aFridayNamesJumuah() {
        // Friday 23 October is the sample's days[6], five days after the mock Sunday.
        assertEquals("JUMUʿAH IN", todayOf(sample, TestFeeds.at(5, 11, 0)).ring.label)
        assertEquals("Jumuʿah", rowsOf(sample, TestFeeds.at(5, 11, 0))[2].name)
        assertEquals("DHUHR IN", todayOf(sample, TestFeeds.at(4, 11, 0)).ring.label)
    }

    @Test
    fun pastTheLastStartThereIsNoRing() {
        // The sample's last day is Monday 16 November, whose Isha at 20:24 is its last start.
        assertNull(today(sample, TestFeeds.at(29, 21, 0)))
        assertNull(today(sample, TestFeeds.at(31, 14, 58)))
        assertNull(today(sample, TestFeeds.at(29, 21, 0), update = true))
    }

    @Test
    fun aRowIsSaidAsThePhoneSaysIt() {
        val rows = rowsOf(sample, TestFeeds.at(0, 14, 58))

        assertEquals("Asr, 16:10", rows[3].spoken)
        assertEquals("16:10", rows[3].time)
        assertEquals("Sunrise, 6:01", rows[1].spoken)
    }

    /**
     * M8 point A5 (6 November 2026): an Arabic or Urdu row joins its name and its time with the
     * Arabic comma "، ", as the phone's rows and the Apple Watch's Today do (Ruling 64 item 6).
     */
    @Test
    fun anArabicOrUrduRowIsJoinedByTheArabicComma() {
        val at = TestFeeds.at(0, 14, 58)
        val arabic = rowsOf(arSa, at)
        val urdu = rowsOf(TestFeeds.phone("capetown-ur24"), at)

        assertEquals("العصر، ١٦:١٠", arabic[3].spoken)
        assertEquals("الشروق، ٦:٠١", arabic[1].spoken)
        assertEquals("عصر، ۱۶:۱۰", urdu[3].spoken)
    }

    @Test
    fun withoutSunriseTheListIsTheFivePrayers() {
        // The owner's decision of 9 November 2026: the phone's Show sunrise off, as a phone has it
        // until the user turns it on, leaves Sunrise out of the list, as the Prayer screen leaves
        // its row out, and changes nothing else.
        val hidden = TestFeeds.withoutSunrise(sample)
        val at = TestFeeds.at(0, 14, 58)
        val rows = dayRows(hidden, at, LitRule.LIST)

        assertEquals(listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"), rows.map { it.name })
        assertEquals(rowsOf(sample, at).filterNot { it.small }, rows)
        assertTrue(rows.none { it.small })
        // A screen reader is told the five rows the screen shows, each with its state.
        assertEquals(listOf("Fajr, 4:33", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24"), rows.map { it.spoken })
        assertEquals(listOf("Passed", "Current prayer", "Next prayer", null, null), rows.map { it.state })
        // The Tile's rule too, and the ring, which never counted to Sunrise.
        val morning = TestFeeds.at(0, 6, 40)
        assertEquals(rowsOf(sample, morning, LitRule.GLANCE).filterNot { it.small }, dayRows(hidden, morning, LitRule.GLANCE))
        assertEquals(todayOf(sample, at).ring, todayOf(hidden, at).ring)
        assertEquals(rows, todayOf(hidden, at).rows)
        // On, as the samples have it: the six.
        assertEquals(6, todayOf(sample, at).rows.size)
    }

    @Test
    fun withoutSunriseTheFiveRowsShareItsRoom() {
        // No gap where Sunrise stood: the five share the six rows' room, 24.4 units each, so the
        // first row, the city and the screen keep their places, and the step from the countdown
        // line at 40 to the city at 184 stays even.
        val list = TodayLayout.list(small = FIVE)

        listOf(64f, 88.4f, 112.8f, 137.2f, 161.6f).zip(list.baselines).forEach { (want, got) -> assertEquals(want, got, 1e-3f) }
        assertEquals(5, list.baselines.size)
        assertEquals(184f, list.city, 1e-3f)
        assertEquals(200f, list.height, 1e-3f)
        assertEquals(24.4f, TodayLayout.pitch(small = false, drawn = 12f, shared = true), 1e-4f)
        // Grown, it is as long as the six rows' list, its first row where theirs is.
        for (grow in listOf(1.06f, 1.12f, 1.3f)) {
            val five = TodayLayout.list(FIVE, List(5) { grow }, grow)
            val six = TodayLayout.list(SMALL, List(6) { grow }, grow)
            assertEquals(six.height, five.height, 1e-3f, "at $grow")
            assertEquals(six.baselines.first(), five.baselines.first(), 1e-3f, "at $grow")
        }
        // Isha stands higher, where the round screen is wider: the times may reach 170.3.
        assertEquals(170.33f, widestColumn(round = true, small = FIVE), 0.01f)
    }

    @Test
    fun aRowsPitchGrowsWithItsName() {
        assertEquals(21f, TodayLayout.pitch(small = false, drawn = 12f), 1e-4f)
        assertEquals(27.3f, TodayLayout.pitch(small = false, drawn = 15.6f), 1e-4f)
        assertEquals(17f, TodayLayout.pitch(small = true, drawn = 10f), 1e-4f)
        assertEquals(22.1f, TodayLayout.pitch(small = true, drawn = 13f), 1e-4f)
    }

    @Test
    fun atItsEndTheListIsTheTilesScreen() {
        // gTile's rows: the baselines 64, 85, 102, 123, 144 and 165, and the city at 184, so the
        // list is one screen of 200 units, and scrolled to its end it sits where the Tile's does.
        val list = TodayLayout.list(small = listOf(false, true, false, false, false, false))

        assertEquals(listOf(64f, 85f, 102f, 123f, 144f, 165f), list.baselines)
        assertEquals(184f, list.city)
        assertEquals(200f, list.height)
        // Each row's own edges, which the page rounds to pixels from the list's top, so that no
        // row's rounding moves the rows after it.
        assertEquals(listOf(49.5f, 70.5f, 87.5f, 108.5f, 129.5f, 150.5f), list.tops)
        assertEquals(171.5f, list.cityTop)
    }

    @Test
    fun theListsEdgesRoundFromItsTop() {
        // At 2.27 pixels a unit (454 px across), rounding each row's height on its own puts the
        // city 2 px low; rounding each edge from the top keeps every edge within half a pixel.
        val list = TodayLayout.list(small = listOf(false, true, false, false, false, false))
        val unit = 454f / 200f
        val edges = listOf(0f) + list.tops + list.cityTop + list.height
        val heights = edges.zipWithNext { a, b -> TodayLayout.px(b, unit) - TodayLayout.px(a, unit) }

        assertEquals(454, heights.sum())
        assertEquals(edges.map { TodayLayout.px(it, unit) }, edges.indices.map { i -> heights.take(i).sum() })
        for (edge in edges) assertTrue(abs(TodayLayout.px(edge, unit) - edge * unit) <= 0.5f, "the edge at $edge units")
        assertEquals(listOf(0, 112, 160, 199, 246, 294, 342, 389, 454), edges.map { TodayLayout.px(it, unit) })
    }

    @Test
    fun aLargerFontLengthensTheList() {
        // Every name drawn at 1.3 times its size (the layout's own rule, whatever the lines' cap).
        val list = TodayLayout.list(small = listOf(false, true, false, false, false, false), grow = List(6) { 1.3f }, cityGrow = 1.3f)

        // Each row's pitch grows with its name, and its baseline keeps its place in the row.
        val gaps = list.baselines.zipWithNext { a, b -> b - a }
        listOf(27.3f, 22.1f, 27.3f, 27.3f, 27.3f).zip(gaps).forEach { (want, got) -> assertEquals(want, got, 1e-3f) }
        assertEquals(LIST_TOP_GROWN, list.baselines.first(), 1e-3f)
        assertTrue(list.height > 200f, "${list.height}")
    }

    @Test
    fun theTimesEndWhereTheDrawingHasThem() {
        // English on 24 hours: every name and time fits, so the times end at 150, as gTile's do.
        val end = TodayLayout.timeEnd(nameWidths = listOf(20f, 46.8f), timeWidths = listOf(33.7f, 33.7f), maxEnds = listOf(183f, 167f))

        assertEquals(150f, end)
        assertEquals(150f - 33.7f - TodayLayout.GAP - TodayLayout.NAME_START, TodayLayout.nameRoom(end, 33.7f), 1e-3f)
    }

    @Test
    fun aLongNameMovesEveryTimeTowardsTheEdge() {
        // A 12-hour Friday in English: "Jumuʿah" (54.3 units) beside "12:33 PM" (53.8). The times
        // are a column, so they all move, and keep one edge.
        val end = TodayLayout.timeEnd(nameWidths = listOf(54.3f, 25f), timeWidths = listOf(53.8f, 46.4f), maxEnds = listOf(183f, 167.3f))

        assertEquals(TodayLayout.NAME_START + 54.3f + TodayLayout.GAP + 53.8f, end, 1e-3f)
        assertEquals(54.3f, TodayLayout.nameRoom(end, 53.8f), 1e-3f)
    }

    @Test
    fun theTimesStopAtTheNarrowestRowsRoundEdge() {
        // A name longer still is cut: the column ends at the round edge of the row where the screen
        // is narrowest, so that no row's time leaves it.
        val end = TodayLayout.timeEnd(nameWidths = listOf(100f, 25f), timeWidths = listOf(53.8f, 46.4f), maxEnds = listOf(183f, 167f))

        assertEquals(167f, end)
        assertEquals(167f - 53.8f - TodayLayout.GAP - TodayLayout.NAME_START, TodayLayout.nameRoom(end, 53.8f), 1e-3f)
    }

    @Test
    fun theLowestRowsEdgeIsTheRoundScreens() {
        // Each row's time may reach the round screen's chord at the row's lower edge in the drawing,
        // less the frame's inset: Isha's, at 165, is the narrowest.
        val list = TodayLayout.list(small = listOf(false, true, false, false, false, false))
        val ends = list.baselines.mapIndexed { i, y -> TodayLayout.maxEnd(y, small = i == 1, round = true) }

        assertTrue(ends.all { it > 160f && it < 195f }, "$ends")
        assertEquals(ends.min(), ends.last())
        assertEquals(194f, TodayLayout.maxEnd(165f, small = false, round = false))
    }

    @Test
    fun theWidestColumnOfTimesEndsAtIshasEdgeInTheDrawing() {
        // However long the names and times (a 12-hour Friday's "Jumuʿah" beside "12:33 PM" needs
        // 167.1 units already at the drawing's size), the column stops at the narrowest row's round
        // edge where the drawing has the rows: Isha's lower edge, 167.3 units across.
        assertEquals(167.32f, widestColumn(round = true), 0.01f)
        assertEquals(194f, widestColumn(round = false))
    }

    @Test
    fun atItsEndTheListKeepsEveryRowsTimeOnTheRoundScreenAtEveryFontSize() {
        // A list longer than the screen stands higher at its end than the drawing does. On
        // emulator-5558 at font 1.3 Fajr's baseline came 23 units from the top and its 24-hour time
        // was cut (Task 9 Step 4); at 1.24, 12-hour English's column, moved out to about 166 units
        // for "Maghrib 7:04 PM", ran its "4:33 AM" to the round edge (review round 1). So wherever
        // the list's end puts a row, with each name drawn at its largest or shrunk to its line's
        // size and the time at its largest, the round screen less the frame's inset must still
        // reach the widest column the times may take, on either clock and in any language.
        // With Sunrise and without it (the phone's Show sunrise off), the five rows sharing its room.
        for (small in listOf(SMALL, FIVE)) {
            for (round in listOf(true, false)) {
                for (step in 85..200) {
                    val fontScale = step / 100f
                    val least = narrowestAtTheEnd(
                        rowGrow = TodayType.rowName.growthAt(fontScale),
                        timeGrow = TodayType.rowTime.growthAt(fontScale),
                        sunriseGrow = TodayType.sunrise.growthAt(fontScale),
                        cityGrow = TodayType.city.growthAt(fontScale),
                        round = round,
                        small = small,
                    )
                    assertTrue(
                        least >= widestColumn(round, small),
                        "${small.size} rows at font $fontScale (round $round): a row's time may reach only $least at the list's end",
                    )
                }
            }
        }
    }

    @Test
    fun theCapIsTheLargestWearOsSizeTheRoundScreenAllows() {
        // Wear OS's own font sizes, as Settings, Accessibility, Text size sets them on emulator-5558.
        // The cap is the largest of them whose list keeps the widest column at its end, 1.12: at
        // 1.18 Fajr's time at the top would stand outside the frame's inset. So 1.18 and Largest,
        // 1.24, show the list a wearer sees at 1.12, where a 12-hour Friday's "Jumuʿah" stays whole.
        val sizes = listOf(1.06f, 1.12f, 1.18f, 1.24f)
        val allowed = sizes.filter { narrowestAtTheEnd(rowGrow = it, timeGrow = it, sunriseGrow = it, cityGrow = it, round = true) >= widestColumn(round = true) }

        assertEquals(listOf(1.06f, 1.12f), allowed)
        assertEquals(allowed.last(), TodayType.LIST_CAP)
        // Without Sunrise the five rows share its room, and the cap holds them too.
        val fiveAt = { size: Float -> narrowestAtTheEnd(rowGrow = size, timeGrow = size, sunriseGrow = size, cityGrow = size, round = true, small = FIVE) }
        assertTrue(fiveAt(TodayType.LIST_CAP) >= widestColumn(round = true, small = FIVE))
    }

    @Test
    fun aGrownRowReachesNearerTheRoundEdge() {
        // Near the top a row's glyphs rise above its baseline as far as it is grown, where the
        // screen is narrower: 12 units grown by 1.24 reach 11.9 above a baseline at 31.4, not 9.6.
        val drawn = TodayLayout.maxEnd(31.4f, small = false, round = true)
        val grown = TodayLayout.maxEnd(31.4f, small = false, round = true, grow = 1.24f)

        assertEquals(100f + (WatchUnits.chordUnits(31.4f - 0.8f * 12f * 1.24f, round = true, inset = 6f)) / 2, grown, 1e-3f)
        assertTrue(grown < drawn, "$grown, $drawn")
    }

    @Test
    fun theListGrowsWithTheFontUpToItsCap() {
        // Every line of the list grows with Wear OS's 1.06 and 1.12, and stops at the cap, 1.12,
        // with its 1.18 and 1.24 ("Largest").
        assertEquals(1.12f, TodayType.LIST_CAP)
        for (line in listOf(TodayType.rowName, TodayType.rowTime, TodayType.sunrise, TodayType.city)) {
            assertEquals(line.units * 1.06f, line.range(1.06f).endInclusive, 1e-3f)
            assertEquals(line.units * 1.12f, line.range(1.12f).endInclusive, 1e-3f)
            assertEquals(line.units * TodayType.LIST_CAP, line.range(1.18f).endInclusive, 1e-3f)
            assertEquals(line.units * TodayType.LIST_CAP, line.range(1.24f).endInclusive, 1e-3f)
        }
    }

    @Test
    fun aboveItsCapTheListIsAsItIsThere() {
        // So Wear OS's 1.18 and its Largest, 1.24, get the list at the cap, as a Galaxy's larger
        // sizes do, and as any size between the cap and 1.18 does.
        fun listAt(fontScale: Float) = TodayLayout.list(
            SMALL,
            SMALL.map { s -> (if (s) TodayType.sunrise else TodayType.rowName).growthAt(fontScale) },
            TodayType.city.growthAt(fontScale),
        )
        val capped = listAt(TodayType.LIST_CAP)
        for (fontScale in listOf(1.15f, 1.18f, 1.24f, 1.3f, 1.5f, 2f)) assertEquals(capped, listAt(fontScale), "at font $fontScale")
    }

    /** How far a list line is drawn at [fontScale], at its largest: its range's end over its size. */
    private fun Line.growthAt(fontScale: Float): Float = range(fontScale).endInclusive / units

    /** The farthest the times' column may reach, as the page bounds it ([TodayLayout.timeEnd] over [TodayLayout.maxEnds]), for the rows [small]. */
    private fun widestColumn(round: Boolean, small: List<Boolean> = SMALL): Float =
        TodayLayout.timeEnd(nameWidths = List(small.size) { 1_000f }, timeWidths = List(small.size) { 1_000f }, maxEnds = TodayLayout.maxEnds(small, round))

    /**
     * The least room any row's time has at the list's end: every mix of names drawn at their
     * largest ([rowGrow], Sunrise's [sunriseGrow]) or shrunk to their line's size, the city at
     * [cityGrow], and each time at its largest ([timeGrow], Sunrise's [sunriseGrow]), as the page
     * draws it.
     */
    private fun narrowestAtTheEnd(rowGrow: Float, timeGrow: Float, sunriseGrow: Float, cityGrow: Float, round: Boolean, small: List<Boolean> = SMALL): Float {
        var least = Float.MAX_VALUE
        for (mix in 0 until (1 shl small.size)) {
            val grows = small.indices.map { i -> if (mix and (1 shl i) != 0) 1f else if (small[i]) sunriseGrow else rowGrow }
            val list = TodayLayout.list(small, grows, cityGrow)
            val risen = (list.height - WatchUnits.ACROSS).coerceAtLeast(0f)
            list.baselines.forEachIndexed { i, baseline ->
                val time = if (small[i]) sunriseGrow else timeGrow
                least = minOf(least, TodayLayout.maxEnd(baseline - risen, small[i], round, grow = time))
            }
        }
        return least
    }

    private companion object {
        /** The first row's baseline with the names grown by 1.3: the room above it, then 14.5 × 1.3. */
        const val LIST_TOP_GROWN = 49.5f + 14.5f * 1.3f

        /** The day's rows, Sunrise the small one. */
        val SMALL = listOf(false, true, false, false, false, false)

        /** The day's rows where the phone hides Sunrise: the five prayers. */
        val FIVE = List(5) { false }

        val NBSP = Char(0xA0)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)
    }
}
