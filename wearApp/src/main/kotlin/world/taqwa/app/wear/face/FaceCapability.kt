package world.taqwa.app.wear.face

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.link.await

/**
 * The watch's word to the phone that it has a face to offer ([WatchLinkContract.FACE_CAPABILITY],
 * the Lead's call 5): published while the Taqwa face is not the watch's face and the one activation
 * is unspent, so the phone's "Add the Taqwa face" row shows while a tap can still do something, and
 * removed for good after the one tap. It names no place and no person: a capability is a word.
 *
 * Every caller publishes the state its own call answered: the app's start and FaceUpdateReceiver
 * through [refreshAfterUpdate], Today each time it comes to the front, the face screen as it opens
 * ([readAndPublish]) and the daily nudge [FacePush.state]'s, and the face screen's button
 * [FacePush.addAndActivate]'s. Nothing tells the app of a face chosen in the watch's own picker, so
 * the watch says it at the next of these, and the phone's row is at most a day stale. A stale row
 * opens the face screen, which says how things stand and publishes it, so the tap that finds the
 * row stale also corrects it.
 */
internal object FaceCapability {

    /** Whether the phone's row and Today's chip are wanted: the face not active and a tap still able to help. */
    fun wanted(state: FaceState, spent: Boolean): Boolean =
        !spent && state in WANTED

    /** Adds or removes the capability from [state] and [spent], never from a state it reads itself. */
    suspend fun publish(context: Context, state: FaceState, spent: Boolean) {
        val client = Wearable.getCapabilityClient(context)
        val wanted = wanted(state, spent)
        try {
            withTimeoutOrNull(BOUND_MS) {
                if (wanted) {
                    client.addLocalCapability(WatchLinkContract.FACE_CAPABILITY).await()
                } else {
                    client.removeLocalCapability(WatchLinkContract.FACE_CAPABILITY).await()
                }
            }
            Log.i(TAG, "TaqwaFace capability ${if (wanted) "added" else "removed"} ${state.name}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // No Play services, or the capability already as asked (Wearable's DUPLICATE_CAPABILITY and
            // UNKNOWN_CAPABILITY): the next publish says it again.
            Log.i(TAG, "TaqwaFace capability unchanged ${e.statusCode}")
        } catch (e: Exception) {
            Log.i(TAG, "TaqwaFace capability unchanged ${e.javaClass.simpleName}")
        }
    }

    private val WANTED = setOf(FaceState.NOT_INSTALLED, FaceState.INSTALLED, FaceState.FAILED)
    private const val BOUND_MS = 5_000L
    private const val TAG = "TaqwaFace"
}
