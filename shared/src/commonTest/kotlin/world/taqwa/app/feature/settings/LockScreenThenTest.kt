package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetPrayerRow

/**
 * The prayer the Lock Screen picture names on its second line ("then Isha · العشاء"), chosen as
 * the rectangular accessory chooses it (`prayerAfterNext` in TaqwaWidgetViews.swift): the row after
 * the next prayer's, wrapping past Isha to Fajr, and the first row when the next prayer is not
 * among the rows. The real accessory shows no countdown, so neither does the picture.
 */
class LockScreenThenTest {

    private val rows = ObligatoryPrayers.map { prayer ->
        WidgetPrayerRow(prayer, displayName = "${prayer.name} · ع", clockTime = "0:00", isCurrent = false)
    }

    private fun nextIs(name: String, rows: List<WidgetPrayerRow> = this.rows) = WidgetContent(
        nextPrayerDisplayName = name,
        countdownMinutes = 21,
        nextClockTime = "18:32",
        rows = rows,
        ringProgress = 0.86f,
        countdownLabel = "$name in",
    )

    @Test
    fun `the line under names the prayer after the next one`() {
        assertEquals(Prayer.ISHA, prayerAfterNext(nextIs("MAGHRIB · ع"))?.prayer)
    }

    @Test
    fun `after Isha it wraps to Fajr`() {
        assertEquals(Prayer.FAJR, prayerAfterNext(nextIs("ISHA · ع"))?.prayer)
    }

    @Test
    fun `a next prayer missing from the rows falls back to the first row`() {
        assertEquals(Prayer.FAJR, prayerAfterNext(nextIs("Sunrise"))?.prayer)
    }

    @Test
    fun `no rows means no second line`() {
        assertNull(prayerAfterNext(nextIs("MAGHRIB · ع", rows = emptyList())))
    }
}
