package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.widget.ScheduledPrayer
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetCountdown
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetSnapshot

/**
 * The Lock Screen picture's first line says what the real rectangular accessory's says (the
 * owner's decision, 31 October): the "Maghrib in" phrase beside the time, in the reader's language,
 * not the prayer's name alone. The accessory's line is `entry.headline` in TaqwaWidgetViews.swift,
 * which `TaqwaTimelineProvider.entries` takes from [WidgetCountdown.headline] of the content it
 * builds for the minute; the picture reads the same mirror as the Widgets screen reads it.
 */
class LockScreenHeadlineTest {

    private val hour = 3_600L
    private val midnight = 1_757_116_800L // an arbitrary local midnight, in epoch seconds

    /** 17:39, twenty-one minutes before Maghrib, when the mirror is written. */
    private val writtenAt = midnight + 18 * hour - 21 * 60

    private fun day(index: Int) = listOf(
        ScheduledPrayer(Prayer.FAJR, midnight + index * 24 * hour + 5 * hour, "5:00", index),
        ScheduledPrayer(Prayer.DHUHR, midnight + index * 24 * hour + 12 * hour, "12:00", index),
        ScheduledPrayer(Prayer.ASR, midnight + index * 24 * hour + 15 * hour, "15:00", index),
        ScheduledPrayer(Prayer.MAGHRIB, midnight + index * 24 * hour + 18 * hour, "18:00", index),
        ScheduledPrayer(Prayer.ISHA, midnight + index * 24 * hour + 20 * hour, "20:00", index),
    )

    /** The mirror as the app writes it at [writtenAt] in [languageTag], Maghrib next. */
    private fun mirror(languageTag: String) = WidgetSnapshot(
        nextPrayer = Prayer.MAGHRIB,
        countdownMinutes = 21,
        nextClockTime = "18:00",
        allClockTimes = day(0).associate { it.prayer to it.clockTime },
        currentPrayer = Prayer.ASR,
        languageTag = languageTag,
        ringProgress = 0.93f,
        countdownLabel = WidgetMirrorWriter.countdownLabel(Prayer.MAGHRIB, languageTag),
        nextPrayerEpochSeconds = midnight + 18 * hour,
        previousPrayerEpochSeconds = midnight + 15 * hour,
        schedule = day(0) + day(1),
    )

    /** The picture's first line, from the mirror as the Widgets screen builds its content. */
    private fun pictureFirstLine(snapshot: WidgetSnapshot): String =
        lockScreenHeadline(WidgetContentBuilder.build(snapshot))

    /** The real accessory's first line from the same mirror at the same minute, as the Swift entry builds it. */
    private fun accessoryFirstLine(snapshot: WidgetSnapshot): String = WidgetCountdown.headline(
        WidgetContentBuilder.build(snapshot, writtenAt),
        WidgetCountdown.remainingMinutesAt(snapshot, writtenAt),
    )

    @Test
    fun `in English the first line is the Maghrib in phrase the accessory shows`() {
        val snapshot = mirror("en-GB")
        assertEquals("Maghrib in", pictureFirstLine(snapshot))
        assertEquals(accessoryFirstLine(snapshot), pictureFirstLine(snapshot))
    }

    @Test
    fun `in Arabic it is the Arabic phrase the accessory shows`() {
        val snapshot = mirror("ar-LY")
        assertEquals("متبقٍّ على المغرب", pictureFirstLine(snapshot))
        assertEquals(accessoryFirstLine(snapshot), pictureFirstLine(snapshot))
    }

    @Test
    fun `in every language the app speaks the picture and the accessory say the same words`() {
        for (language in UiLanguage.entries) {
            val snapshot = mirror(language.code)
            assertEquals(accessoryFirstLine(snapshot), pictureFirstLine(snapshot), language.name)
        }
    }
}
