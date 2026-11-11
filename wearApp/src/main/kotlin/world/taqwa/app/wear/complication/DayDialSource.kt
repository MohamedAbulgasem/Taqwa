package world.taqwa.app.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TaqwaWearApp

/**
 * The day dial's geometry, for the Taqwa face's Sun style and nothing else: its slot 7, a
 * RANGED_VALUE whose three numbers carry the date's midnight, Fajr, Sunrise, Asr, Maghrib and Isha
 * in minutes from Dhuhr, two to a number (`ComplicationFields.dial`, `packDial`). A face cannot read
 * one weight out of the weighted day ([DaySource]), so this is how the Sun learns where to turn the
 * stretches and to put the marks.
 *
 * It is no ring for a person to read: shown on another face, the packed numbers draw a share that
 * stands still and means nothing. So the manifest hides it from every picker (`HIDDEN`), and only
 * a face that names it as a slot's default, as the Taqwa face's fixed slot does, is given it. The
 * library's other way, `SAFE_WATCH_FACE_SUPPORTED_TYPES` with the face named safe, is closed to a
 * store app: the system honours it only for a source holding
 * `com.google.wear.permission.GET_IS_FOR_SAFE_WATCH_FACE`, a signature and privileged permission,
 * and without it the slot was given the battery (Task 8, fix round 1, on the Wear OS 6 emulator).
 *
 * It changes at midnight and at every start, so the timeline breaks at both. It is static data,
 * with nothing dynamic in it. What a screen reader says for it is the date's times as Today shows
 * them, Sunrise only while the phone shows it, though the dial marks sunrise either way
 * (`ComplicationFields.dialSpoken`); the weighted day says what it is. On the Taqwa face the slot
 * has no room of its own (the Sun's dial is drawn by the scene), so the Wear OS 6 system gives a
 * reader no item for it (Task 12): the words are for a system that reads every slot.
 *
 * Its title and text carry the face's own settings (`ComplicationFields.faceSettings`): which way
 * the face's lines read, its clock's hours, digits and day-period markers, as the phone writes
 * them. They hold for as long as a feed is stored, so where the dial has nothing to say (the feed
 * has run out, or does not reach the moment) the source still answers, with the settings and a
 * dial of nothing: a range of 0 to 1, which the face's Sun style reads as no dial (a real one's
 * maximum is 8,192 or more) and draws its empty frame for. So the face keeps the phone's clock when
 * the prayers have gone. Only with no feed at all does it say no data, and the face then writes
 * its clock as the watch does.
 */
class DayDialSource : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? =
        answerOffMain("day dial") { answer(request) }

    private fun answer(request: ComplicationRequest): ComplicationDataTimeline? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        val feed = (application as TaqwaWearApp).feedStore.changes.value ?: return nothingToSay()
        val settings = ComplicationFields.faceSettings(feed.ui)
        val plan = ComplicationTimelines.dial(feed, System.currentTimeMillis() / 1000)
        logTimeline("day dial", null, plan)
        // Where the dial has nothing to say, the face shows its empty form, and the watch's word for
        // that is spec 2.6's.
        val emptySaid = getString(R.string.open_taqwa_on_phone)
        return plan.toTimeline(empty = { settingsAlone(settings, emptySaid) }) { step, fields ->
            geometry(fields, settings, ComplicationFields.dialSpoken(feed, step.from) ?: emptySaid)
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) {
            geometry(PREVIEW_DIAL, PREVIEW_SETTINGS, getString(R.string.preview_day_times))
        } else {
            null
        }

    private fun geometry(fields: DialFields, settings: FaceSettings, said: String): ComplicationData {
        val packed = ComplicationFields.packDial(fields)
        return ranged(packed.value, packed.min, packed.max, settings, said)
    }

    /** The settings with a dial of nothing: what the face is given where the dial has nothing to say. */
    private fun settingsAlone(settings: FaceSettings, said: String): ComplicationData = ranged(0f, 0f, 1f, settings, said)

    /**
     * The dial as the library takes it. [said] is what a screen reader says for it: the date's times
     * (`ComplicationFields.dialSpoken`), never the title and text, which are the face's settings in
     * a code no one is meant to hear.
     */
    private fun ranged(value: Float, min: Float, max: Float, settings: FaceSettings, said: String): ComplicationData =
        RangedValueComplicationData.Builder(value, min, max, text(said))
            .setTitle(text(settings.title))
            .setText(text(settings.text))
            .build()

    private companion object {
        /** The mockups' Sunday at 14:58 in Cape Town, in minutes from its Dhuhr (12:33). */
        val PREVIEW_DIAL = DialFields(midnight = -753, fajr = -480, sunrise = -392, asr = 217, maghrib = 391, isha = 471)

        /** The mockups' phone: English, 24 hours, Western digits. */
        val PREVIEW_SETTINGS = FaceSettings(title = "L2LAM", text = "0123456789PM")
    }
}
