package world.taqwa.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import world.taqwa.app.di.appContainer
import world.taqwa.app.nav.LaunchRequests

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // appContext is now assigned in TaqwaApplication.onCreate, which runs before this on
        // every launch path, including a cold boot receiver.
        enableEdgeToEdge()
        // Before setContent, so a cold tap has its request pending by the time App composes and
        // starts collecting (design spec §8).
        recordAyahRequest(intent)
        setContent {
            App(appContainer)
        }
    }

    /**
     * The warm half: a tap that lands on an activity instance the platform decided to reuse
     * arrives here instead of in `onCreate`. Both paths have to be covered — which of the two a
     * given tap takes is the platform's decision, not this app's, and `onCreate` alone would
     * leave the widget doing nothing at all on whichever taps went the other way.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recordAyahRequest(intent)
        // Kept as the activity's current intent so a later getIntent() agrees with what was acted
        // on, matching what the platform does for onCreate's — but only after recordAyahRequest
        // has stripped the tap extras, so a process restored from a saved instance state does not
        // find them still on getIntent() and replay the same tap.
        setIntent(intent)
    }

    /**
     * Hands a widget tap's ayah to [LaunchRequests], which `App` collects once onboarding is out
     * of the way.
     *
     * The two extras are plain ints put on the intent by the ayah widget's click action. Both
     * must be present and positive: a launch from the app icon or from a prayer widget carries
     * neither, and a half-filled intent is a bug that should open Today rather than a surah zero.
     */
    private fun recordAyahRequest(intent: Intent?) {
        val surah = intent?.getIntExtra(EXTRA_OPEN_SURAH, 0) ?: 0
        val ayah = intent?.getIntExtra(EXTRA_OPEN_AYAH, 0) ?: 0
        if (surah > 0 && ayah > 0) LaunchRequests.openAyah(surah, ayah)
        // Stripped once recorded, so an activity recreated from a saved instance state — which
        // hands onCreate the same intent back — never re-reads these and replays the tap.
        intent?.removeExtra(EXTRA_OPEN_SURAH)
        intent?.removeExtra(EXTRA_OPEN_AYAH)
    }

    private companion object {
        const val EXTRA_OPEN_SURAH = "open_surah"
        const val EXTRA_OPEN_AYAH = "open_ayah"
    }
}
