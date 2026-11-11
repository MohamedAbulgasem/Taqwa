package world.taqwa.app.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import world.taqwa.app.wear.R

/**
 * The Taqwa arch (spec 2.3): a MONOCHROMATIC_IMAGE, for the slots that take an icon, drawn from the
 * app icon's own arch and lamp. It does not depend on the feed and it never changes, so it is a
 * plain answer and not a timeline, and it is served whether or not the phone has sent anything.
 * The faces tint it.
 */
class MarkSource : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        if (request.complicationType == ComplicationType.MONOCHROMATIC_IMAGE) mark() else null

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.MONOCHROMATIC_IMAGE) mark() else null

    private fun mark(): ComplicationData =
        MonochromaticImageComplicationData.Builder(
            MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_taqwa_arch)).build(),
            text(getString(R.string.complication_mark_description)),
        ).build()
}
