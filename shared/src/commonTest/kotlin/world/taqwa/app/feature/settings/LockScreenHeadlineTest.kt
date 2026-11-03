package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.widget.ScheduledPrayer
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetCountdown
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetSnapshot

/**
 * The Lock Screen picture's first line, like the real rectangular accessory's, names the next
 * prayer as the prayer list does ("Maghrib · المغرب"), beside its start time, the same way the
 * "then" line under it names the prayer after (the owner's decision, 3 Nov 2026). It once read
 * "Maghrib in" beside the start time, which read as a countdown it was not.
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

    /** How the prayer list names the next prayer in the same mirror. */
    private fun listName(snapshot: WidgetSnapshot): String =
        WidgetContentBuilder.build(snapshot).rows.first { it.prayer == Prayer.MAGHRIB }.displayName

    @Test
    fun `in English the first line names the prayer as the list does and has no countdown phrase`() {
        val snapshot = mirror("en-GB")
        assertEquals(listName(snapshot), pictureFirstLine(snapshot))
        assertFalse(" in" in pictureFirstLine(snapshot), pictureFirstLine(snapshot))
    }

    @Test
    fun `in every language the first line is the prayer's name as the list writes it`() {
        for (language in UiLanguage.entries) {
            val snapshot = mirror(language.code)
            assertEquals(listName(snapshot), pictureFirstLine(snapshot), language.name)
        }
    }
}
