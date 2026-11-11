package world.taqwa.app.wear.face

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.watch.WatchFeed

/**
 * Whether Today shows its face chip: asked of the process's FacePush each time Today comes to the
 * front ([active]), and published to the phone as it answers, so the phone's row follows Today's
 * chip (the Lead's call 5). False until the first answer, and wherever the face is active, the one
 * activation is spent, or the watch has no Watch Face Push.
 */
@Composable
internal fun rememberFaceChip(active: Boolean): Boolean {
    val context = LocalContext.current
    var wanted by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        wanted = withContext(Dispatchers.IO) { faceChipWanted(context.applicationContext) }
    }
    return wanted
}

private suspend fun faceChipWanted(app: Context): Boolean {
    val push = FacePushes.of(app)
    val state = withTimeoutOrNull(FACE_BOUND_MS) { push.state() } ?: return false
    FaceCapability.publish(app, state, push.spent)
    return FaceCapability.wanted(state, push.spent)
}

/**
 * Today's chip, at the end of its list (spec 3.1): "Add the Taqwa face", the face screen's own
 * button drawn the same, which opens the face screen ([FaceActivity]).
 */
@Composable
internal fun FaceRow(feed: WatchFeed) {
    val context = LocalContext.current
    val words = faceWords(feed, ownFaceWords())
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
        FaceButton(words.add, enabled = true, onClick = { context.startActivity(Intent(context, FaceActivity::class.java)) })
    }
}
