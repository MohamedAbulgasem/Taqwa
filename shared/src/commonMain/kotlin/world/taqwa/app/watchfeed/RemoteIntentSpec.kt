package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchLinkContract

/**
 * The intent a Settings row starts on the watch, as `RemoteActivityHelper.startRemoteActivity`
 * takes it: an [action], a [category] and a data [uri]. The helper refuses anything but a VIEW with
 * a data URI in the BROWSABLE category, so both rows' intents have that shape.
 */
internal data class RemoteIntentSpec(val action: String, val category: String, val uri: String)

/**
 * - [WatchAction.INSTALL]: Taqwa's page in the watch's Play Store. The watch app shares the phone's
 *   package (`world.taqwa.app`, spec 2.1), so the page is the one listing's.
 * - [WatchAction.ADD_FACE]: the watch's face screen, `taqwa://face` ([WatchLinkContract.FACE_URI]),
 *   which adds the face if it was removed and makes it the watch's face behind its permission, or
 *   says how to choose it (plan 5 Task 5's `FaceActivity`).
 */
internal fun remoteIntentSpec(action: WatchAction): RemoteIntentSpec = RemoteIntentSpec(
    action = VIEW,
    category = BROWSABLE,
    uri = when (action) {
        WatchAction.INSTALL -> "market://details?id=$PACKAGE"
        WatchAction.ADD_FACE -> WatchLinkContract.FACE_URI
    },
)

// Android's own names (Intent.ACTION_VIEW, Intent.CATEGORY_BROWSABLE), written out so the rule
// lives in common code, where it is tested on both platforms.
private const val VIEW = "android.intent.action.VIEW"
private const val BROWSABLE = "android.intent.category.BROWSABLE"
private const val PACKAGE = "world.taqwa.app"
