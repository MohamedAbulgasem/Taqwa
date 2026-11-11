package world.taqwa.app.wear.face

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.watch.words
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TaqwaWearApp
import world.taqwa.app.wear.app.WatchTheme

/**
 * The face screen (spec 3.1, plan 5 Task 5), opened by Today's chip and by the phone's "Add the
 * Taqwa face" row (`taqwa://face`). It shows where the face stands, and its button adds the face if
 * the wearer removed it and makes it the watch's face, once:
 * - where the face stands, read as the screen opens, is shown and published to the phone
 *   ([readAndPublish]), so the row that opened it follows a face chosen in the watch's own picker;
 * - the button asks for SET_PUSHED_WATCH_FACE_AS_ACTIVE, unless it is granted or the one activation
 *   is spent, then calls [FacePush.addAndActivate] with the answer;
 * - its answer is published to the phone ([FaceCapability]) and shown.
 *
 * The work runs on the app's scope, so leaving the screen mid-tap still finishes it and tells the
 * phone. It is in the phone's language and direction when a feed is held, and the watch's before.
 */
class FaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TaqwaWearApp
        val push = FacePushes.of(this)
        setContent {
            val feed by app.feedStore.changes.collectAsStateWithLifecycle()
            var state by remember { mutableStateOf<FaceState?>(null) }
            var busy by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                app.scope.launch {
                    readAndPublish(push, BOUND_MS, show = { state = it }) { read, spent -> FaceCapability.publish(app, read, spent) }
                }
            }

            fun addAndActivate(granted: Boolean) {
                busy = true
                app.scope.launch {
                    try {
                        val answer = withTimeoutOrNull(BOUND_MS) { push.addAndActivate(granted) } ?: FaceState.FAILED
                        state = answer
                        FaceCapability.publish(app, answer, push.spent)
                    } finally {
                        busy = false
                    }
                }
            }
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> addAndActivate(granted) }

            val words = faceWords(feed, ownFaceWords())
            val title = feed?.words()?.appName ?: stringResource(R.string.app_name)
            WatchTheme(rtl = feed?.ui?.rtl) {
                FaceScreen(title = title, view = faceView(state, words), busy = busy || state == null) {
                    val granted = checkSelfPermission(SET_ACTIVE) == PackageManager.PERMISSION_GRANTED
                    if (granted || push.spent) addAndActivate(granted) else ask.launch(SET_ACTIVE)
                }
            }
        }
    }

    private companion object {
        const val SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
        const val BOUND_MS = 20_000L
    }
}
