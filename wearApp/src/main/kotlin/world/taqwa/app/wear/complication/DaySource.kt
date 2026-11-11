package world.taqwa.app.wear.complication

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.WeightedElementsComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TaqwaWearApp

/**
 * The day as a dial (spec 2.3, 3.3), WEIGHTED_ELEMENTS: the Taqwa face's slot B, which its Sun
 * style draws round the edge with a `WeightedStroke`, and which any face can show. Seven elements,
 * midnight to midnight, with the night in the palette's hairline, the twilights and the daylight in
 * gold at 15% and 34%, and the stretches that make up the current prayer period in the accent's
 * placeholder colour (`ComplicationFields.day`).
 *
 * Where the Sun turns those stretches and puts its marks comes from [DayDialSource], which no
 * picker offers, because its numbers mean nothing as a ring.
 *
 * It changes at midnight and at every start, so the timeline breaks at both. It is static data,
 * with nothing dynamic in it. The type is served from Wear OS 4 (API 33): below that the source
 * says no data.
 */
class DaySource : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? =
        answerOffMain("day") { answer(request) }

    private fun answer(request: ComplicationRequest): ComplicationDataTimeline? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return nothingToSay()
        if (request.complicationType != ComplicationType.WEIGHTED_ELEMENTS) return null
        val feed = (application as TaqwaWearApp).feedStore.changes.value ?: return nothingToSay()
        return answer(feed, System.currentTimeMillis() / 1000)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun answer(feed: WatchFeed, now: Long): ComplicationDataTimeline {
        val plan = ComplicationTimelines.day(feed, now)
        logTimeline("day", null, plan)
        return plan.toTimeline { _, fields -> stretches(fields) }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return if (type == ComplicationType.WEIGHTED_ELEMENTS) previewStretches() else null
    }

    /** Cape Town on the mockups' Sunday: Fajr 04:33, Sunrise 06:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun previewStretches(): ComplicationData {
        val night = ComplicationFields.HAIRLINE
        val twilight = ComplicationFields.TWILIGHT
        val daylight = ComplicationFields.DAYLIGHT
        return stretches(
            DayFields(
                weights = listOf(273f, 88f, 392f, 217f, 174f, 80f, 216f),
                colors = listOf(night, twilight, daylight, daylight, daylight, twilight, night),
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun stretches(fields: DayFields): ComplicationData =
        WeightedElementsComplicationData.Builder(
            fields.weights.zip(fields.colors) { weight, color -> WeightedElementsComplicationData.Element(weight, color) },
            text(getString(R.string.complication_day_description)),
        )
            // The library wants one of a text, a title and an image beside the elements. The Taqwa
            // face draws none of them.
            .setTitle(text(getString(R.string.complication_day_title)))
            .build()
}
