package world.taqwa.app.wear

import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * The app's process in a debug build, which the debug manifest names in place of [TaqwaWearApp]:
 * the same app, started with the sample feed in the store if the store was empty, so that there is
 * something to draw before a phone has sent anything (a feed that is stored is left alone), and
 * listening for the `DEBUG_FEED` broadcast for as long as it lives (see [DebugFeedReceiver]).
 *
 * A held sample is not installed at start ([SampleFeed.hold]): a watch the broadcast cleared
 * (`--ez clear true`) stays without a feed when its process starts again, until a feed is stored.
 */
class DebugWearApp : TaqwaWearApp() {

    override fun onCreate() {
        super.onCreate()
        listenForDebugFeed()
        SampleFeed.installIfEmpty(this, feedStore)
    }

    /**
     * Since Android 8 a broadcast that names no component and no package (what `am broadcast -a`
     * sends) is not delivered to a receiver declared in the manifest, whatever state the app is in;
     * only one registered by running code gets it. So the plain `-a` form reaches this receiver,
     * while the process is alive, and the explicit `-n` form reaches the manifest's, which starts
     * the process from nothing.
     */
    private fun listenForDebugFeed() {
        ContextCompat.registerReceiver(
            this,
            DebugFeedReceiver(),
            IntentFilter(DebugFeedReceiver.ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
