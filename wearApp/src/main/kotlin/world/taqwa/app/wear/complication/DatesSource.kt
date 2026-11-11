package world.taqwa.app.wear.complication

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TaqwaWearApp

/**
 * The dates (spec 2.3, 3.3), the Taqwa face's slot C and the Moon style's window:
 *
 * - **LONG_TEXT**: the Hijri date in the title, the day and the month as the face writes it
 *   (`11 Jumada al-Ula`, Ruling 24; the full date from a feed written before that form), and the
 *   Gregorian in the text (`FRI 23 OCT`), both as the phone wrote them. What a screen reader says
 *   is the full Hijri date and the Gregorian, joined by the phone's comma
 *   (`ComplicationFields.datesSpoken`);
 * - **SHORT_TEXT**: the weekday in the title, the day of the month in the text, and the moon as a
 *   monochromatic image: the lit part of the moon as it is seen from the feed's hemisphere, white
 *   on nothing, for the face to tint (`ComplicationFields.moonLit`, `moonImage`). The spec's
 *   `[MOON_PHASE_POSITION]` and its 58 pictures are not needed: the face needs no hemisphere
 *   slot, and the moon is the same one the rest of the Hijri month is counted by.
 *
 * It all changes when the date turns over, at the date's own midnight in the place's zone (the
 * feed's `midnight`), and not when the prayer does; the timeline breaks there. The moon is drawn
 * for the middle of each step, so that a date that is mostly over is not shown the moon of its
 * start.
 */
class DatesSource : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? =
        answerOffMain("dates") { answer(request) }

    private fun answer(request: ComplicationRequest): ComplicationDataTimeline? {
        val type = request.complicationType
        if (type != ComplicationType.LONG_TEXT && type != ComplicationType.SHORT_TEXT) return null
        val feed = (application as TaqwaWearApp).feedStore.changes.value ?: return nothingToSay()
        val now = System.currentTimeMillis() / 1000

        val plan = ComplicationTimelines.dates(feed, now)
        logTimeline("dates", type, plan)
        return if (type == ComplicationType.LONG_TEXT) {
            plan.toTimeline { _, fields -> longText(fields, ComplicationFields.datesSpoken(fields, feed.ui)) }
        } else {
            plan.toTimeline { step, fields ->
                val moon = ComplicationFields.moonLit((step.from + step.until) / 2, feed.place.southern)
                shortText(fields.weekday, fields.dayNumber, ComplicationFields.moonImage(moon, MOON_SIZE_PX))
            }
        }
    }

    private fun longText(fields: DatesFields, said: String): ComplicationData =
        LongTextComplicationData.Builder(text(fields.gregorian), text(said))
            .setTitle(text(fields.hijriDayMonth))
            .build()

    private fun shortText(weekday: String, dayNumber: String, moon: Bitmap): ComplicationData =
        ShortTextComplicationData.Builder(text(dayNumber), text("$weekday $dayNumber"))
            .setTitle(text(weekday))
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithBitmap(moon)).build())
            .build()

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val weekday = getString(R.string.preview_weekday)
        val dayNumber = getString(R.string.preview_day_number)
        return when (type) {
            ComplicationType.LONG_TEXT -> {
                val hijri = getString(R.string.preview_hijri)
                val gregorian = getString(R.string.preview_gregorian)
                LongTextComplicationData.Builder(text(gregorian), text("$hijri, $gregorian"))
                    .setTitle(text(getString(R.string.preview_hijri_day_month)))
                    .build()
            }
            ComplicationType.SHORT_TEXT -> {
                // The half moon the mockups draw, lit on the left as it is in the south.
                val moon = ComplicationFields.moonImage(MoonShape(0.5f, waxing = true, litOnLeft = true), MOON_SIZE_PX)
                shortText(weekday, dayNumber, moon)
            }
            else -> null
        }
    }

    private companion object {
        /** Big enough to stay sharp in the 42 pixels the Moon style gives its window on a 450-pixel face. */
        const val MOON_SIZE_PX = 64
    }
}
