package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at

/**
 * Wear OS 3's clock form (Ruling 72 item 1, plan 5's call 6 (b)). Below API 33 the system cannot
 * evaluate dynamic values, so what moves with the clock keeps its words from the entry's start for
 * the whole period: P5.12 Step 1 saw it on emulator-5568 (API 30), "in 1 h 20 min" kept for 80
 * minutes. There the next prayer's long text and every line a screen reader is told take the clock
 * form, the prayer and its time with no countdown, which is true however long the entry is shown,
 * as the Tile draws on a renderer below schema 1.2 (Ruling 65 item 2). From API 33 nothing changes:
 * the text and the line count down, a minute at a time.
 *
 * Each case is run at the two levels the source itself branches on, [WEAR_OS_3] (API 30) and
 * [WEAR_OS_4] (API 33), the level the source reads from `Build.VERSION.SDK_INT`.
 */
class ClockFormTest {

    private val english = TestFeeds.sample
    private val english12 = TestFeeds.other("en12")
    private val libya = TestFeeds.other("ar-ly12")
    private val saudi = TestFeeds.other("ar-sa")

    /** [feed] as a phone sent it before the spoken words. */
    private fun beforeSpoken(feed: WatchFeed): WatchFeed =
        feed.copy(ui = feed.ui.copy(spoken = null), days = feed.days.map { it.copy(gregorianSpoken = "") })

    /** [line] as the system works it out at [moment]. */
    private fun shown(line: SpokenLine, moment: Long): String = DynamicEvaluator(moment).string(line.expression().toDynamicStringProto())

    @Test
    fun theSystemEvaluatesDynamicValuesFromWearOsFour() {
        assertFalse(evaluatesDynamicValues(WEAR_OS_3))
        assertFalse(evaluatesDynamicValues(32))
        assertTrue(evaluatesDynamicValues(WEAR_OS_4))
        assertTrue(evaluatesDynamicValues(36))
    }

    @Test
    fun onWearOsThreeTheLongTextIsThePrayersTime() {
        val now = at(0, 14, 58)
        val fields = assertNotNull(ComplicationFields.nextPrayer(english, now))
        val text = nextPrayerLongText(fields, english, fields.endAt - now, WEAR_OS_3)
        // The words the system shows for the whole entry: Asr's time, beside the title ASR.
        assertEquals("16:10", text.fallback())
        // At the entry's start, five minutes on (the step's two shots) and in its last minute alike.
        for (later in listOf(0L, 5 * 60L, 71 * 60L + 30)) assertEquals("16:10", shown(text, now + later), "at +$later s")
    }

    @Test
    fun onWearOsFourTheLongTextCountsDownAsBefore() {
        val now = at(0, 14, 58)
        val fields = assertNotNull(ComplicationFields.nextPrayer(english, now))
        val text = nextPrayerLongText(fields, english, fields.endAt - now, WEAR_OS_4)
        assertEquals("in 1 h 12 min", text.fallback())
        assertEquals("in 1 h 12 min", shown(text, now))
        assertEquals("in 1 h 7 min", shown(text, now + 5 * 60))
    }

    @Test
    fun onWearOsThreeTheReaderIsToldThePrayerAndItsTime() {
        val now = at(0, 14, 58)
        // What the short text says: the name as the phone writes it, the phone's comma (the Arabic
        // one right to left, Ruling 74 item 1), and the time as the Prayer screen says it, a
        // twelve-hour phone's with its day period.
        val expected = mapOf(
            english to "Asr, 16:10",
            english12 to "Asr, " + FSI + "4:10${NBSP}PM" + PDI,
            libya to "العصر، " + FSI + "4:10${NBSP}م" + PDI,
            saudi to "العصر، ١٦:١٠",
        )
        for ((feed, words) in expected) {
            for (sent in listOf(feed, beforeSpoken(feed))) {
                val fields = assertNotNull(ComplicationFields.nextPrayer(sent, now))
                val said = nextPrayerSaid(fields, sent, fields.endAt - now, WEAR_OS_3)
                assertEquals(words, said.fallback(), sent.ui.language)
                assertEquals(words, shown(said, now + 5 * 60), sent.ui.language)
            }
        }
    }

    @Test
    fun onWearOsFourTheReaderIsToldTheCountdownAsBefore() {
        val now = at(0, 14, 58)
        val fields = assertNotNull(ComplicationFields.nextPrayer(english, now))
        val said = nextPrayerSaid(fields, english, fields.endAt - now, WEAR_OS_4)
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", said.fallback())
        assertEquals("Asr in 1 hour 7 minutes, at 16:10", shown(said, now + 5 * 60))
        val before = beforeSpoken(english)
        val screens = nextPrayerSaid(assertNotNull(ComplicationFields.nextPrayer(before, now)), before, fields.endAt - now, WEAR_OS_4)
        assertEquals("Asr, 16:10, in 1 h 12 min", screens.fallback())
        assertEquals("Asr, 16:10, in 1 h 7 min", shown(screens, now + 5 * 60))
    }

    @Test
    fun onWearOsThreeNothingCountsDownAtAnyStepOfTwoDays() {
        // Every step of the plan from the night before on, at its start, a few minutes in and an
        // hour in, in the four samples and in their feeds written before the spoken words: the long
        // text is the prayer's time and the line the prayer and its time, at every moment alike.
        for (sample in listOf(english, english12, libya, saudi)) {
            for (feed in listOf(sample, beforeSpoken(sample))) {
                for (step in ComplicationTimelines.nextPrayer(feed, at(0, 0, 30))) {
                    val fields = step.fields ?: continue
                    val text = nextPrayerLongText(fields, feed, fields.endAt - step.from, WEAR_OS_3)
                    val said = nextPrayerSaid(fields, feed, fields.endAt - step.from, WEAR_OS_3)
                    assertEquals(fields.time, text.fallback())
                    assertEquals(ComplicationFields.prayerSpoken(fields), said.fallback())
                    for (into in listOf(0L, 7 * 60L, 61 * 60L)) {
                        val moment = step.from + into
                        if (moment >= step.until) continue
                        assertEquals(fields.time, shown(text, moment), "${feed.ui.language} at $moment")
                        assertEquals(ComplicationFields.prayerSpoken(fields), shown(said, moment), "${feed.ui.language} at $moment")
                    }
                }
            }
        }
    }

    @Test
    fun thePickersPreviewShowsAndSaysWhatTheSlotWill() {
        // The preview is the source's own sample, from its resources: below API 33 its long text is
        // the time and what it says the prayer and its time, as the slot will show and say, joined
        // by the comma of the resources' language (Ruling 74 item 1), and the short text the same
        // at every level.
        assertEquals(
            NextPrayerPreview(longText = "16:10", said = "Asr, 16:10", prayerSaid = "Asr, 16:10"),
            nextPrayerPreview("Asr", "16:10", "in 1 h 12 min", WEAR_OS_3, rtl = false),
        )
        assertEquals(
            NextPrayerPreview(longText = "in 1 h 12 min", said = "Asr, 16:10, in 1 h 12 min", prayerSaid = "Asr, 16:10"),
            nextPrayerPreview("Asr", "16:10", "in 1 h 12 min", WEAR_OS_4, rtl = false),
        )
        assertEquals(
            NextPrayerPreview(longText = "16:10", said = "العصر، 16:10", prayerSaid = "العصر، 16:10"),
            nextPrayerPreview("العصر", "16:10", "بعد 1 س 12 د", WEAR_OS_3, rtl = true),
        )
        assertEquals(
            NextPrayerPreview(longText = "بعد 1 س 12 د", said = "العصر، 16:10، بعد 1 س 12 د", prayerSaid = "العصر، 16:10"),
            nextPrayerPreview("العصر", "16:10", "بعد 1 س 12 د", WEAR_OS_4, rtl = true),
        )
    }

    @Test
    fun thePreviewsCommaIsTheResourcesLanguages() {
        // The preview's words are the app's own resources, in the watch's language: of the seven
        // folders they ship in, Arabic and Urdu are written right to left.
        for (language in listOf("ar", "ur")) assertTrue(previewRightToLeft(language), language)
        for (language in listOf("en", "fr", "tr", "in", "id", "bn", "fa", "iw", "he")) assertFalse(previewRightToLeft(language), language)
    }

    companion object {
        /** Wear OS 3's API level, which has no dynamic values. */
        const val WEAR_OS_3 = 30

        /** Wear OS 4's, the first whose system evaluates them. */
        const val WEAR_OS_4 = 33

        val NBSP = Char(0xA0)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)
    }
}
