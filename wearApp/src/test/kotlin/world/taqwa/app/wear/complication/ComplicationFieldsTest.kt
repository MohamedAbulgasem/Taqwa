package world.taqwa.app.wear.complication

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at

/**
 * What the complication sources say, before any of it is made into complication data. Every figure
 * is worked by hand on the sample feed's Sunday 18 October 2026 in Cape Town: Fajr 04:33, Sunrise
 * 06:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04 and Isha 20:24, and `at(0, ...)` is that Sunday.
 */
class ComplicationFieldsTest {

    private val feed = TestFeeds.sample

    // ---- The next prayer

    @Test
    fun nextPrayerAtTwoFiftyEight() {
        val next = assertNotNull(ComplicationFields.nextPrayer(feed, at(0, 14, 58)))

        assertEquals("ASR", next.label)
        assertEquals("16:10", next.time)
        assertEquals("in 1 h 12 min", next.countdown)
        // 2 h 25 min into the 3 h 37 min from Dhuhr to Asr.
        assertEquals(145f / 217f, next.progress, 1e-4f)
        assertEquals(at(0, 12, 33), next.startAt)
        assertEquals(at(0, 16, 10), next.endAt)
        assertFalse(next.rtl)
        assertFalse(next.twelveHour)
        // On the Sun's day dial, Dhuhr is twelve o'clock: the period runs from it to 217 minutes on.
        assertEquals(at(0, 12, 33), next.dhuhrAt)
        assertEquals(0f, next.dialStart)
        assertEquals(217f, next.dialEnd)
    }

    @Test
    fun nextPrayerArabicKeepsTheName() {
        val next = assertNotNull(ComplicationFields.nextPrayer(TestFeeds.arabicSaudi(), at(0, 14, 58)))

        // Arabic has no capitals: the name is the one the phone wrote, and so are the digits.
        assertEquals("العصر", next.label)
        assertEquals("١٦:١٠", next.time)
        assertEquals("بعد ١ س ١٢ د", next.countdown)
        assertTrue(next.rtl)
    }

    @Test
    fun nextPrayerLabelsAreCapitalsWhereTheLanguageHasThem() {
        fun labelAt(day: Int, hour: Int, minute: Int) =
            assertNotNull(ComplicationFields.nextPrayer(feed, at(day, hour, minute))).label

        assertEquals("FAJR", labelAt(0, 3, 0))
        assertEquals("DHUHR", labelAt(0, 10, 0))
        assertEquals("ASR", labelAt(0, 14, 0))
        assertEquals("MAGHRIB", labelAt(0, 17, 0))
        assertEquals("ISHA", labelAt(0, 19, 30))
        // Friday's Dhuhr reads Jumuʿah, as it does on the phone.
        assertEquals("JUMUʿAH", labelAt(5, 10, 0))
    }

    @Test
    fun nextPrayerCapitalsFollowTheFeedsLanguage() {
        // In Turkish the capital of i is İ, which is not what a language-blind upper-casing makes.
        val english = feed.copy(days = feed.days.map { it.copy(names = it.names.copy(asr = "ikindi")) })
        val turkish = english.copy(ui = english.ui.copy(language = "tr"))

        assertEquals("İKİNDİ", assertNotNull(ComplicationFields.nextPrayer(turkish, at(0, 14, 58))).label)
        // The same letters in a language without that rule.
        assertEquals("IKINDI", assertNotNull(ComplicationFields.nextPrayer(english, at(0, 14, 58))).label)
    }

    @Test
    fun nextPrayerCarriesTheClockSettingAndTheDirection() {
        val twelve = feed.copy(ui = feed.ui.copy(twelveHour = true, rtl = true))

        val next = assertNotNull(ComplicationFields.nextPrayer(twelve, at(0, 14, 58)))

        assertTrue(next.twelveHour)
        assertTrue(next.rtl)
        // The time is the phone's own clock string, whatever the setting.
        assertEquals("16:10", next.time)
    }

    @Test
    fun nextPrayerCountdownIsTheRulesOwnAtTheMinuteBoundary() {
        // Exactly 72 minutes left reads 1 h 12 min, and 71 min 59 s reads 1 h 11 min (Review Focus 2).
        assertEquals("in 1 h 12 min", assertNotNull(ComplicationFields.nextPrayer(feed, at(0, 14, 58, 0))).countdown)
        assertEquals("in 1 h 11 min", assertNotNull(ComplicationFields.nextPrayer(feed, at(0, 14, 58, 1))).countdown)
        assertEquals("in under a minute", assertNotNull(ComplicationFields.nextPrayer(feed, at(0, 16, 9, 30))).countdown)
    }

    @Test
    fun nextPrayerAfterIshaCountsToTomorrowsFajr() {
        val next = assertNotNull(ComplicationFields.nextPrayer(feed, at(0, 22, 30)))

        assertEquals("FAJR", next.label)
        assertEquals("4:33", next.time)
        assertEquals(at(0, 20, 24), next.startAt)
        assertEquals(at(1, 4, 33), next.endAt)
        // 2 h 6 min into the 8 h 9 min from Isha to the next Fajr.
        assertEquals(126f / 489f, next.progress, 1e-4f)
        // Before midnight the dial is still Sunday's: Isha 7 h 51 min after its Dhuhr, and Monday's
        // Fajr 16 h after it.
        assertEquals(at(0, 12, 33), next.dhuhrAt)
        assertEquals(471f, next.dialStart)
        assertEquals(960f, next.dialEnd)
    }

    @Test
    fun nextPrayerBeforeFajrMeasuresFromYesterdaysIsha() {
        val next = assertNotNull(ComplicationFields.nextPrayer(feed, at(1, 4, 10)))

        assertEquals("FAJR", next.label)
        assertEquals(at(0, 20, 24), next.startAt)
        assertEquals(at(1, 4, 33), next.endAt)
        // After midnight the dial is the new date's: Sunday's Isha is 16 h 9 min before Monday's
        // Dhuhr, and Monday's Fajr 8 h before it.
        assertEquals(at(1, 12, 33), next.dhuhrAt)
        assertEquals(-969f, next.dialStart)
        assertEquals(-480f, next.dialEnd)
    }

    @Test
    fun theDialsPeriodIsTheRingsPeriodInMinutes() {
        // The Sun style reads the period as minutes from the dial's Dhuhr, and the Ring and the Arch
        // read the same numbers as a share: (now - start) / (end - start) is the progress.
        for (now in listOf(at(0, 4, 40), at(0, 14, 58), at(0, 18, 0), at(0, 22, 30), at(1, 4, 10), at(3, 9, 15, 30))) {
            val next = assertNotNull(ComplicationFields.nextPrayer(feed, now))
            assertEquals((next.startAt - next.dhuhrAt) / 60f, next.dialStart, 1e-3f, "at $now")
            assertEquals((next.endAt - next.dhuhrAt) / 60f, next.dialEnd, 1e-3f, "at $now")
            assertEquals(next.progress, (next.dialNow - next.dialStart) / (next.dialEnd - next.dialStart), 1e-4f, "at $now")
            assertTrue(next.dialNow in next.dialStart..next.dialEnd, "at $now")
        }
    }

    // ---- The day

    @Test
    fun dayWeightsSumToADay() {
        val day = assertNotNull(ComplicationFields.day(feed, at(0, 14, 58)))

        assertEquals(7, day.weights.size)
        assertEquals(7, day.colors.size)
        assertEquals(1440f, day.weights.sum(), 0.01f)
    }

    @Test
    fun dayWeightsAreTheSevenStretchesOfTheDayInMinutes() {
        val day = assertNotNull(ComplicationFields.day(feed, at(0, 14, 58)))

        // Midnight to Fajr, Fajr to Sunrise, Sunrise to Dhuhr, Dhuhr to Asr, Asr to Maghrib, Maghrib
        // to Isha, Isha to the next midnight: 04:33, 06:01, 12:33, 16:10, 19:04, 20:24 and 24:00.
        assertEquals(listOf(273f, 88f, 392f, 217f, 174f, 80f, 216f), day.weights)
    }

    @Test
    fun dayColoursSeparateTheNightTheTwilightsAndTheDaylight() {
        val day = assertNotNull(ComplicationFields.day(feed, at(0, 14, 58)))

        // The period at 14:58 is Dhuhr to Asr, which is a stretch of its own, and the one lit.
        val night = ComplicationFields.HAIRLINE
        val twilight = ComplicationFields.TWILIGHT
        val daylight = ComplicationFields.DAYLIGHT
        val lit = ComplicationFields.ACCENT_PLACEHOLDER
        assertEquals(listOf(night, twilight, daylight, lit, daylight, twilight, night), day.colors)
    }

    @Test
    fun dayPaletteIsTheDrawings() {
        // Hairline #232825; the gold of Sun's daylight, #F0B429, at 34%, and at 15% for the twilights,
        // the opacities faces.js's sunFace draws them at.
        assertEquals(0xFF232825.toInt(), ComplicationFields.HAIRLINE)
        assertEquals(0x57F0B429, ComplicationFields.DAYLIGHT)
        assertEquals(0x26F0B429, ComplicationFields.TWILIGHT)
        // The accent placeholder is Amber, the default accent, at full strength.
        assertEquals(0xFFF0B429.toInt(), ComplicationFields.ACCENT_PLACEHOLDER)
    }

    @Test
    fun dayLightsEveryPeriodExactly() {
        val night = ComplicationFields.HAIRLINE
        val twilight = ComplicationFields.TWILIGHT
        val daylight = ComplicationFields.DAYLIGHT
        val lit = ComplicationFields.ACCENT_PLACEHOLDER
        fun colorsAt(day: Int, hour: Int, minute: Int) =
            assertNotNull(ComplicationFields.day(feed, at(day, hour, minute))).colors

        // Fajr to Dhuhr is the morning twilight and the morning's daylight.
        assertEquals(listOf(night, lit, lit, daylight, daylight, twilight, night), colorsAt(0, 5, 0))
        // Asr to Maghrib is a stretch of its own now that Asr is a boundary.
        assertEquals(listOf(night, twilight, daylight, daylight, lit, twilight, night), colorsAt(0, 17, 0))
        // Maghrib to Isha is the evening twilight, exactly.
        assertEquals(listOf(night, twilight, daylight, daylight, daylight, lit, night), colorsAt(0, 19, 30))
        // Isha to the next Fajr: before midnight the evening's night is in it ...
        assertEquals(listOf(night, twilight, daylight, daylight, daylight, twilight, lit), colorsAt(0, 21, 0))
        // ... and after midnight, on the next date, the night up to Fajr is.
        assertEquals(listOf(lit, twilight, daylight, daylight, daylight, twilight, night), colorsAt(1, 2, 0))
    }

    @Test
    fun aDayOfTwentyFiveHoursIsStillADialOfTwentyFour() {
        // Where the clocks go back an hour the date is 25 hours long. The dial is a 24-hour circle,
        // and its stretches are where the prayers are, so the night that ends it is cut to what is
        // left of the circle: the extra hour is not drawn twice.
        val longDay = TestFeeds.withDay(feed, 2) { it.copy(midnight = it.midnight + 3_600L) }

        val day = assertNotNull(ComplicationFields.day(longDay, at(0, 14, 58)))

        assertEquals(1440f, day.weights.sum(), 0.01f)
        assertEquals(listOf(273f, 88f, 392f, 217f, 174f, 80f, 216f), day.weights)
    }

    @Test
    fun aDayOfTwentyThreeHoursIsStillADialOfTwentyFour() {
        // Where the clocks go forward, the hour that is skipped falls in the night before Fajr: the
        // stretches from Fajr on are where the prayers are, and the night that ends the date makes
        // up the circle.
        val shortDay = TestFeeds.withDay(feed, 1) { it.copy(midnight = it.midnight + 3_600L) }

        val day = assertNotNull(ComplicationFields.day(shortDay, at(0, 14, 58)))

        assertEquals(1440f, day.weights.sum(), 0.01f)
        assertEquals(listOf(213f, 88f, 392f, 217f, 174f, 80f, 276f), day.weights)
    }

    @Test
    fun aDayWithoutAMidnightBeginsAtSolarMidnight() {
        // A midnight of 0 is the phone saying it cannot say. The rules turn the date over at solar
        // midnight, halfway between the day before's Dhuhr and this one's (WatchRules.dayStart), and
        // the dial begins there: the sample's Dhuhr is 12:33 on both, so it is at 720 minutes.
        val unknown = feed.copy(days = feed.days.map { it.copy(midnight = 0L) })

        val day = assertNotNull(ComplicationFields.day(unknown, at(0, 14, 58)))

        assertEquals(listOf(240f, 88f, 392f, 217f, 174f, 80f, 249f), day.weights)
        assertEquals(720f, day.weights.take(3).sum())
    }

    @Test
    fun everyStretchStaysPositiveWhereTheTimesFallPastMidnight() {
        // A high-latitude summer: Tuesday's Maghrib (00:45) and Isha (00:52) fall on Wednesday, whose
        // own Fajr is at 00:38. A dial stretch has to be longer than nothing, and the seven of them
        // have to add up to the day, whatever the engine says.
        val day = assertNotNull(ComplicationFields.day(TestFeeds.lateMaghribNight(), at(2, 14, 58)))

        assertEquals(7, day.weights.size)
        assertTrue(day.weights.all { it > 0f }, "every stretch is longer than nothing: ${day.weights}")
        assertEquals(1440f, day.weights.sum(), 0.01f)
        // The stretches that do exist are where they were: midnight to Fajr, Fajr to Sunrise,
        // Sunrise to Dhuhr, Dhuhr to Asr, and Asr onward to the end of the date.
        assertEquals(listOf(273f, 88f, 392f, 217f), day.weights.take(4))
        assertEquals(1440f - 970f, day.weights.drop(4).sum(), 0.01f)
    }

    @Test
    fun aCombinedMaghribAndIshaStillHasSevenPositiveStretches() {
        val combined = TestFeeds.withDay(feed, 3) { d -> d.copy(times = d.times.copy(isha = d.times.maghrib)) }

        val day = assertNotNull(ComplicationFields.day(combined, at(2, 14, 58)))

        assertTrue(day.weights.all { it > 0f }, "${day.weights}")
        assertEquals(1440f, day.weights.sum(), 0.01f)
        assertEquals(listOf(273f, 88f, 392f, 217f, 174f), day.weights.take(5))
    }

    // ---- The day dial's geometry (the Sun style)

    @Test
    fun theDialAtTwoFiftyEightIsTheDaysTimesFromDhuhr() {
        val dial = assertNotNull(ComplicationFields.dial(feed, at(0, 14, 58)))

        // Minutes from Sunday's Dhuhr, 12:33: midnight, Fajr 04:33, Sunrise 06:01, Asr 16:10,
        // Maghrib 19:04 and Isha 20:24.
        assertEquals(DialFields(midnight = -753, fajr = -480, sunrise = -392, asr = 217, maghrib = 391, isha = 471), dial)
    }

    @Test
    fun afterIshaTheDialsFajrIsTheOneTheNightRunsTo() {
        // The period is Isha to Monday's Fajr, so the Fajr the dial marks is Monday's: one mark, lit,
        // where the night ends, and not Sunday's, gone by, in the same place.
        val dial = assertNotNull(ComplicationFields.dial(feed, at(0, 22, 30)))

        assertEquals(DialFields(midnight = -753, fajr = 960, sunrise = -392, asr = 217, maghrib = 391, isha = 471), dial)
    }

    @Test
    fun beforeFajrTheDialsIshaIsTheOneTheNightRunsFrom() {
        // After midnight the dial is Monday's, and the Isha it marks is Sunday's, where the night
        // began: 16 h 9 min before Monday's Dhuhr.
        val dial = assertNotNull(ComplicationFields.dial(feed, at(1, 4, 10)))

        assertEquals(DialFields(midnight = -753, fajr = -480, sunrise = -392, asr = 217, maghrib = 391, isha = -969), dial)
    }

    @Test
    fun theDialMarksThePeriodTheNextPrayerSourceSays() {
        // The face lights the marks the next prayer's period begins and ends at: so the dial and the
        // next prayer must agree, to the minute, at every moment of two days.
        var now = at(0, 0, 0)
        while (now < at(2, 0, 0)) {
            val dial = assertNotNull(ComplicationFields.dial(feed, now))
            val next = assertNotNull(ComplicationFields.nextPrayer(feed, now))
            val marks = listOf(dial.fajr, 0, dial.asr, dial.maghrib, dial.isha)
            assertTrue(marks.any { kotlin.math.abs(it - next.dialStart) < 0.75f }, "the start is a mark at $now: $dial, ${next.dialStart}")
            assertTrue(marks.any { kotlin.math.abs(it - next.dialEnd) < 0.75f }, "the end is a mark at $now: $dial, ${next.dialEnd}")
            now += 7 * 60 + 13
        }
    }

    @Test
    fun theDialAndTheDaysStretchesAgree() {
        // The face turns the day's stretches by the dial's midnight: each boundary must then fall on
        // the dial's own mark for it.
        for (now in listOf(at(0, 14, 58), at(0, 5, 0), at(1, 12, 33), at(4, 23, 0))) {
            val dial = assertNotNull(ComplicationFields.dial(feed, now))
            val day = assertNotNull(ComplicationFields.day(feed, now))
            val edges = day.weights.runningFold(dial.midnight.toFloat()) { at, weight -> at + weight }
            assertEquals(dial.sunrise.toFloat(), edges[2], 0.51f, "Sunrise at $now")
            assertEquals(0f, edges[3], 0.51f, "Dhuhr at $now")
            assertEquals(dial.asr.toFloat(), edges[4], 0.51f, "Asr at $now")
            assertEquals(dial.maghrib.toFloat(), edges[5], 0.51f, "Maghrib at $now")
            assertEquals(dial.midnight + 1440f, edges[7], 0.01f, "the circle closes at $now")
        }
    }

    @Test
    fun theDialPacksIntoARangedValueAndComesBack() {
        val cases = listOf(
            assertNotNull(ComplicationFields.dial(feed, at(0, 14, 58))),
            assertNotNull(ComplicationFields.dial(feed, at(0, 22, 30))),
            assertNotNull(ComplicationFields.dial(feed, at(1, 4, 10))),
            DialFields(midnight = -1440, fajr = -1440, sunrise = -1440, asr = 0, maghrib = 0, isha = -1440),
            DialFields(midnight = 0, fajr = 1440, sunrise = 0, asr = 1440, maghrib = 1440, isha = 1440),
        )
        for (dial in cases) {
            val packed = ComplicationFields.packDial(dial)
            assertTrue(packed.min <= packed.value && packed.value <= packed.max, "in order: $packed")
            for (number in listOf(packed.min, packed.value, packed.max)) {
                // Whole numbers below 2^24, so that a Float holds them exactly and the face's floor
                // and % read them back exactly.
                assertTrue(number >= 0f && number < 16_777_216f && number == kotlin.math.floor(number), "exact: $number")
            }
            assertEquals(dial, ComplicationFields.unpackDial(packed.min, packed.value, packed.max))
        }
    }

    @Test
    fun aDialOutOfOrderIsHeldInOrderAndNeverBreaksTheRangedValue() {
        // No engine gives a Sunrise before midnight or an Asr before Sunrise, but the library would
        // end the app's process on a value outside its range, so the packing keeps the order whatever
        // it is given, and reads the out-of-order ones as the nearest in order.
        val wrong = DialFields(midnight = -100, fajr = -480, sunrise = -500, asr = -600, maghrib = 391, isha = 9999)

        val packed = ComplicationFields.packDial(wrong)

        assertTrue(packed.min <= packed.value && packed.value <= packed.max, "$packed")
        val back = ComplicationFields.unpackDial(packed.min, packed.value, packed.max)
        assertEquals(-100, back.midnight)
        assertEquals(-100, back.sunrise)
        assertEquals(-100, back.asr)
        assertEquals(4093 - 1440, back.isha)
    }

    @Test
    fun theDialSaysTheDatesTimesAndNotWhatTheDaySays() {
        // Slot 7 spans the face as slot 1 does, so a reader that lists every slot would say "Prayer
        // times today" twice: the dial says the date's times, as Today shows them.
        assertEquals(
            "Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24",
            ComplicationFields.dialSpoken(feed, at(0, 14, 58)),
        )
        // Before Fajr it is still the date's own times, and on a Friday its Dhuhr is Jumuʿah.
        assertEquals(
            "Fajr 4:33, Sunrise 6:01, Jumuʿah 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24",
            ComplicationFields.dialSpoken(feed, at(5, 0, 1)),
        )
    }

    @Test
    fun theDialSpeaksAnArabicPhonesWordsJoinedByTheArabicComma() {
        assertEquals(
            "الفجر ٤:٣٣، الشروق ٦:٠١، الظهر ١٢:٣٣، العصر ١٦:١٠، المغرب ١٩:٠٤، العشاء ٢٠:٢٤",
            ComplicationFields.dialSpoken(TestFeeds.arabicSaudi(), at(0, 14, 58)),
        )
    }

    @Test
    fun theDialSaysSunriseOnlyWhileThePhoneShowsIt() {
        // The phone's Show sunrise off (the owner's decision of 9 November 2026): the dial says the
        // five prayers, as Today's rows and the Tile's show them. Its geometry keeps Sunrise, where
        // Fajr's twilight ends, whatever the setting.
        val hidden = TestFeeds.withoutSunrise(feed)
        val now = at(0, 14, 58)
        assertEquals("Fajr 4:33, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24", ComplicationFields.dialSpoken(hidden, now))
        assertEquals("الفجر ٤:٣٣، الظهر ١٢:٣٣، العصر ١٦:١٠، المغرب ١٩:٠٤، العشاء ٢٠:٢٤", ComplicationFields.dialSpoken(TestFeeds.withoutSunrise(TestFeeds.arabicSaudi()), now))
        assertEquals(ComplicationFields.dial(feed, now), ComplicationFields.dial(hidden, now))
        assertEquals(ComplicationFields.day(feed, now), ComplicationFields.day(hidden, now))
    }

    @Test
    fun theDialSaysNothingWhereItsGeometryIsNone() {
        for (now in listOf(at(29, 20, 24), at(29, 23, 59, 59), at(60, 12, 0))) {
            assertNull(ComplicationFields.dial(feed, now), "dial at $now")
            assertNull(ComplicationFields.dialSpoken(feed, now), "spoken dial at $now")
        }
    }

    // ---- What a twelve-hour phone's times are said with (plan 3, Task 11)

    @Test
    fun aTwelveHourPhoneSaysItsDayPeriod() {
        val twelve = TestFeeds.other("en12")
        val now = at(0, 14, 58)
        val next = assertNotNull(ComplicationFields.nextPrayer(twelve, now))

        // Drawn as before: the bare clock string, beside which the face draws its own day period.
        assertEquals("4:10", next.time)
        // Said as the Prayer screen writes it, whole, so a reader never parts the time from its PM.
        assertEquals(iso("4:10${NBSP}PM"), next.spokenTime)
        val said = nextPrayerSaid(next, twelve, next.endAt - now, sdk = 33)
        assertEquals("Asr in 1 hour 12 minutes, at " + iso("4:10${NBSP}PM"), said.fallback())
        assertEquals(said.fallback(), DynamicEvaluator(now).string(said.expression().toDynamicStringProto()))
        // The short text's description, and the screen's words of a feed written before the spoken
        // ones, the prayer and its time joined by the phone's comma (Ruling 74 item 1).
        assertEquals("Asr, " + iso("4:10${NBSP}PM"), ComplicationFields.prayerSpoken(next))
        val before = twelve.copy(ui = twelve.ui.copy(spoken = null))
        val screens = nextPrayerSaid(assertNotNull(ComplicationFields.nextPrayer(before, now)), before, next.endAt - now, sdk = 33)
        assertEquals("Asr, " + iso("4:10${NBSP}PM") + ", in 1 h 12 min", screens.fallback())
    }

    @Test
    fun theDialSaysTwelveHourTimesWithTheirDayPeriod() {
        // Sunrise carries its day period as the five prayers do.
        assertEquals(
            "Fajr " + iso("4:33${NBSP}AM") + ", Sunrise " + iso("6:01${NBSP}AM") + ", Dhuhr " + iso("12:33${NBSP}PM") +
                ", Asr " + iso("4:10${NBSP}PM") + ", Maghrib " + iso("7:04${NBSP}PM") + ", Isha " + iso("8:24${NBSP}PM"),
            ComplicationFields.dialSpoken(TestFeeds.other("en12"), at(0, 14, 58)),
        )
    }

    @Test
    fun aTwentyFourHourFeedIsSaidAsBefore() {
        val now = at(0, 14, 58)
        // At every period of the day, in Western digits and in the phone's Arabic-Indic ones.
        for (sample in listOf(feed, TestFeeds.other("ar-sa"))) {
            for (moment in listOf(at(0, 3, 0), at(0, 5, 0), at(0, 10, 0), now, at(0, 17, 0), at(0, 19, 30), at(0, 22, 30))) {
                val next = assertNotNull(ComplicationFields.nextPrayer(sample, moment))
                assertEquals(next.time, next.spokenTime, "${sample.ui.language} at $moment")
            }
        }
        val next = assertNotNull(ComplicationFields.nextPrayer(feed, now))
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", nextPrayerSaid(next, feed, next.endAt - now, sdk = 33).fallback())
        assertEquals("Asr, 16:10", ComplicationFields.prayerSpoken(next))
        assertEquals(
            "Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24",
            ComplicationFields.dialSpoken(feed, now),
        )
        // A twelve-hour phone that sent no clockTime (a feed written before it) says its clock as it is.
        val twelveWithout = feed.copy(ui = feed.ui.copy(twelveHour = true))
        assertEquals("16:10", assertNotNull(ComplicationFields.nextPrayer(twelveWithout, now)).spokenTime)
    }

    // ---- The dates

    @Test
    fun datesOnAFriday() {
        val dates = assertNotNull(ComplicationFields.dates(feed, at(5, 14, 58)))

        assertEquals("FRI 23 OCT", dates.gregorian)
        assertEquals("FRI", dates.weekday)
        assertEquals("23", dates.dayNumber)
        // 6 Jumada al-Ula on the Sunday, so the 11th on the Friday.
        assertEquals("11 Jumada al-Ula 1448", dates.hijri)
        // The face's form has no year (Ruling 24).
        assertEquals("11 Jumada al-Ula", dates.hijriDayMonth)
    }

    @Test
    fun aFeedWithoutTheYearlessHijriShowsTheFullDate() {
        // A feed from before the phone sent the face's form: the face shows the date as it is.
        val older = feed.copy(days = feed.days.map { it.copy(hijriDayMonth = "") })
        val dates = assertNotNull(ComplicationFields.dates(older, at(5, 14, 58)))

        assertEquals("11 Jumada al-Ula 1448", dates.hijriDayMonth)
    }

    // ---- The face's own settings

    @Test
    fun faceSettingsForTheMockupsPhone() {
        // English, 24 hours, Western digits: left to right, Manrope, AM and PM.
        val settings = ComplicationFields.faceSettings(feed.ui)

        assertEquals("L2LAM", settings.title)
        assertEquals("0123456789PM", settings.text)
    }

    @Test
    fun faceSettingsForASaudiPhoneOnTwelveHours() {
        val ui = TestFeeds.arabicSaudi().ui.copy(twelveHour = true)
        val settings = ComplicationFields.faceSettings(ui)

        // Right to left, twelve hours, numbers in Noto Sans Arabic, then the morning's marker.
        assertEquals("R1Aص", settings.title)
        assertEquals("٠١٢٣٤٥٦٧٨٩م", settings.text)
    }

    @Test
    fun faceSettingsTakeApartAsTheFaceReadsThem() {
        // tools/face-centre.py reads them with subText: three letters, the morning's marker to the
        // end of the title; ten digits, the afternoon's marker to the end of the text. A marker of
        // any length, and every digit set the phones write.
        val cases = mapOf(
            feed.ui.copy(language = "tr", am = "ÖÖ", pm = "ÖS", twelveHour = true) to listOf("L", "1", "L", "ÖÖ", "ÖS"),
            feed.ui.copy(language = "bn", digitZero = '০') to listOf("L", "2", "L", "AM", "PM"),
            feed.ui.copy(language = "ur", rtl = true, digitZero = '۰', am = "قبل دوپہر", pm = "بعد دوپہر") to
                listOf("R", "2", "A", "قبل دوپہر", "بعد دوپہر"),
            TestFeeds.arabicSaudi().ui.copy(digitZero = '0') to listOf("R", "2", "L", "ص", "م"),
        )
        for ((ui, expected) in cases) {
            val settings = ComplicationFields.faceSettings(ui)
            val title = settings.title
            val text = settings.text
            assertEquals(expected, listOf(title.substring(0, 1), title.substring(1, 2), title.substring(2, 3), title.substring(3), text.substring(10)), ui.language)
            assertEquals((0..9).map { ui.digitZero + it }.joinToString(""), text.substring(0, 10), ui.language)
        }
    }

    @Test
    fun datesTurnOverAtTheDaysMidnightAndNotAtAPrayer() {
        assertEquals("SUN 18 OCT", assertNotNull(ComplicationFields.dates(feed, at(0, 23, 59, 59))).gregorian)
        assertEquals("MON 19 OCT", assertNotNull(ComplicationFields.dates(feed, at(1, 0, 0))).gregorian)
        // Isha has begun and Fajr has not: it is still the date it was at Maghrib, and after
        // midnight it is the next one while the period is the same.
        assertEquals("SUN 18 OCT", assertNotNull(ComplicationFields.dates(feed, at(0, 22, 30))).gregorian)
        assertEquals("MON 19 OCT", assertNotNull(ComplicationFields.dates(feed, at(1, 2, 0))).gregorian)
    }

    // ---- A feed that has run out

    @Test
    fun aSpentFeedServesTheEmptyForm() {
        // The sample's last start is Isha on its last day, 16 November at 20:24.
        val last = at(29, 20, 24)
        val before = last - 1

        assertNotNull(ComplicationFields.nextPrayer(feed, before))
        assertNotNull(ComplicationFields.day(feed, before))
        assertNotNull(ComplicationFields.dial(feed, before))
        assertNotNull(ComplicationFields.dates(feed, before))

        for (now in listOf(last, at(29, 22, 30), at(29, 23, 59, 59), at(30, 0, 0), at(60, 12, 0))) {
            assertNull(ComplicationFields.nextPrayer(feed, now), "next prayer at $now")
            assertNull(ComplicationFields.day(feed, now), "day at $now")
            assertNull(ComplicationFields.dial(feed, now), "dial at $now")
            assertNull(ComplicationFields.dates(feed, now), "dates at $now")
        }
    }

    @Test
    fun aFeedWithNoDaysServesTheEmptyForm() {
        val empty: WatchFeed = feed.copy(days = emptyList())

        assertNull(ComplicationFields.nextPrayer(empty, at(0, 14, 58)))
        assertNull(ComplicationFields.day(empty, at(0, 14, 58)))
        assertNull(ComplicationFields.dial(empty, at(0, 14, 58)))
        assertNull(ComplicationFields.dates(empty, at(0, 14, 58)))
    }

    @Test
    fun beforeTheFeedBeginsTheFormIsEmptyToo() {
        // The sample's first day is Saturday 17 October and it has no yesterday before it: until
        // that day's Fajr no start has arrived to measure from, and there is nothing honest to say.
        val beforeAnything = at(-1, 3, 0)

        assertNull(ComplicationFields.nextPrayer(feed, beforeAnything))
        assertNull(ComplicationFields.day(feed, beforeAnything))
        assertNull(ComplicationFields.dial(feed, beforeAnything))
        assertNull(ComplicationFields.dates(feed, beforeAnything))
    }

    // ---- The moon

    @Test
    fun moonOnTheEighteenthIsWaxingNearHalf() {
        // The first quarter of October 2026 fell at 16:13 UTC on the 18th, so at 14:58 in Cape Town
        // (12:58 UTC) the moon was a little short of half lit and waxing: 0.49 on the Moon style's
        // window. (The plan's expected 0.50 to 0.62 came from the mockup's drawing, which no model
        // of the real moon gives for that hour.)
        val south = ComplicationFields.moonLit(at(0, 14, 58), southern = true)
        val north = ComplicationFields.moonLit(at(0, 14, 58), southern = false)

        assertTrue(south.litFraction in 0.45f..0.52f, "lit fraction ${south.litFraction}")
        assertTrue(south.waxing)
        // Waxing in the south is lit on the left, and in the north on the right.
        assertTrue(south.litOnLeft)
        assertFalse(north.litOnLeft)
        assertEquals(south.litFraction, north.litFraction)
        assertTrue(north.waxing)
    }

    @Test
    fun moonFollowsTheAlmanacsPhases() {
        // The phases of October 2026 as the Old Farmer's Almanac gives them, in UTC. A model that is
        // an hour out would still pass; one a day out would not.
        fun utc(day: Int, hour: Int, minute: Int): Long =
            LocalDateTime.of(2026, 10, day, hour, minute).toEpochSecond(ZoneOffset.UTC)

        val lastQuarter = ComplicationFields.moonLit(utc(3, 13, 26), southern = true)
        val new = ComplicationFields.moonLit(utc(10, 15, 50), southern = true)
        val firstQuarter = ComplicationFields.moonLit(utc(18, 16, 13), southern = true)
        val full = ComplicationFields.moonLit(utc(26, 4, 13), southern = true)

        assertEquals(0.5f, lastQuarter.litFraction, 0.02f)
        assertFalse(lastQuarter.waxing)
        assertEquals(0f, new.litFraction, 0.01f)
        assertEquals(0.5f, firstQuarter.litFraction, 0.02f)
        assertTrue(firstQuarter.waxing)
        assertEquals(1f, full.litFraction, 0.01f)

        // Two days after the new moon it is a thin waxing crescent; two days before it, a thin waning one.
        val crescentAfter = ComplicationFields.moonLit(utc(12, 15, 50), southern = true)
        val crescentBefore = ComplicationFields.moonLit(utc(8, 15, 50), southern = true)
        assertTrue(crescentAfter.waxing && crescentAfter.litFraction in 0.02f..0.15f, "${crescentAfter.litFraction}")
        assertTrue(!crescentBefore.waxing && crescentBefore.litFraction in 0.02f..0.15f, "${crescentBefore.litFraction}")
    }

    @Test
    fun moonIsDarkAtTheNewMoonOfJanuary2000() {
        // 6 January 2000 at 18:14 UTC: the new moon the mean cycle is counted from.
        val epoch = LocalDateTime.of(2000, 1, 6, 18, 14).toEpochSecond(ZoneOffset.UTC)

        assertEquals(0f, ComplicationFields.moonLit(epoch, southern = false).litFraction, 0.01f)
    }

    @Test
    fun aWaningMoonIsLitOnTheRightInTheSouthAndOnTheLeftInTheNorth() {
        val waning = LocalDateTime.of(2026, 10, 3, 13, 26).toEpochSecond(ZoneOffset.UTC)

        assertFalse(ComplicationFields.moonLit(waning, southern = true).litOnLeft)
        assertTrue(ComplicationFields.moonLit(waning, southern = false).litOnLeft)
    }

    // ---- The moon's image

    private fun alpha(pixels: IntArray, size: Int, x: Int, y: Int) = (pixels[y * size + x] ushr 24) and 0xFF

    @Test
    fun theLitPartOfAHalfMoonIsItsLeftHalfOrItsRight() {
        val size = 64
        val left = ComplicationFields.moonPixels(MoonShape(0.5f, waxing = true, litOnLeft = true), size)
        val right = ComplicationFields.moonPixels(MoonShape(0.5f, waxing = false, litOnLeft = false), size)

        assertEquals(size * size, left.size)
        assertEquals(255, alpha(left, size, 16, 32))
        assertEquals(0, alpha(left, size, 48, 32))
        assertEquals(0, alpha(right, size, 16, 32))
        assertEquals(255, alpha(right, size, 48, 32))
    }

    @Test
    fun theImageIsWhiteWithAlphaAndNothingElse() {
        val pixels = ComplicationFields.moonPixels(MoonShape(0.3f, waxing = true, litOnLeft = true), 48)

        for (pixel in pixels) {
            if (pixel ushr 24 == 0) assertEquals(0, pixel) else assertEquals(0xFFFFFF, pixel and 0xFFFFFF)
        }
        assertTrue(pixels.any { it ushr 24 == 255 }, "some of it is solid")
        assertTrue(pixels.any { val a = it ushr 24; a in 1..254 }, "and its edge is smooth")
    }

    @Test
    fun noPixelOutsideTheDiscIsLit() {
        val size = 64
        val full = ComplicationFields.moonPixels(MoonShape(1f, waxing = true, litOnLeft = true), size)

        assertEquals(255, alpha(full, size, 32, 32))
        for ((x, y) in listOf(0 to 0, 63 to 0, 0 to 63, 63 to 63, 2 to 6)) {
            assertEquals(0, alpha(full, size, x, y), "corner ($x, $y)")
        }
    }

    @Test
    fun theLitAreaIsTheLitFractionOfTheDisc() {
        val size = 96
        fun area(pixels: IntArray) = pixels.sumOf { (it ushr 24) / 255.0 }
        val disc = area(ComplicationFields.moonPixels(MoonShape(1f, waxing = true, litOnLeft = true), size))

        for (k in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            for (onLeft in listOf(true, false)) {
                val lit = area(ComplicationFields.moonPixels(MoonShape(k, waxing = true, litOnLeft = onLeft), size))
                assertEquals(k.toDouble(), lit / disc, 0.02, "k = $k, litOnLeft = $onLeft")
            }
        }
    }

    @Test
    fun aNewMoonHasNothingToShow() {
        val pixels = ComplicationFields.moonPixels(MoonShape(0f, waxing = true, litOnLeft = true), 64)

        assertTrue(pixels.all { it ushr 24 < 8 }, "at most a hairline of the limb")
    }

    @Test
    fun aGibbousMoonReachesPastTheCentreAndACrescentStopsShortOfIt() {
        val size = 64
        val gibbous = ComplicationFields.moonPixels(MoonShape(0.8f, waxing = true, litOnLeft = true), size)
        val crescent = ComplicationFields.moonPixels(MoonShape(0.2f, waxing = true, litOnLeft = true), size)

        // Just right of the centre line: lit in the gibbous moon, dark in the crescent.
        assertEquals(255, alpha(gibbous, size, 40, 32))
        assertEquals(0, alpha(crescent, size, 36, 32))
        // The crescent's horns are on the left limb.
        assertEquals(255, alpha(crescent, size, 4, 32))
    }

    @Test
    fun theMirrorImageIsTheMirror() {
        val size = 64
        val left = ComplicationFields.moonPixels(MoonShape(0.3f, waxing = true, litOnLeft = true), size)
        val right = ComplicationFields.moonPixels(MoonShape(0.3f, waxing = true, litOnLeft = false), size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                assertEquals(left[y * size + x], right[y * size + (size - 1 - x)], "($x, $y)")
            }
        }
    }

    private companion object {
        val NBSP = Char(0xA0)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)

        /** [time] as a spoken sentence splices it: between a first-strong isolate and its pop. */
        fun iso(time: String): String = "$FSI$time$PDI"
    }
}
