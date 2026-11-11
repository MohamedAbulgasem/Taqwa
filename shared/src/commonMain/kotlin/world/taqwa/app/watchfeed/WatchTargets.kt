package world.taqwa.app.watchfeed

/**
 * A watch the phone is connected to, as the Wear Data Layer lists it (`NodeClient.getConnectedNodes`):
 * its node [id], its [name] (`Node.displayName`, the name the system gives it), and whether it is
 * [nearby], directly connected (`Node.isNearby`) rather than reached through Google's cloud.
 */
internal data class WatchPeer(val id: String, val name: String, val nearby: Boolean)

/** The watch a Settings row acts on: its node id, and its name as the system gives it. */
internal data class WatchTarget(val nodeId: String, val name: String)

/**
 * Settings › Watch's two Android rows (plan 5 Task 6): the watch "Install on your watch" opens
 * Taqwa's Play page on ([install]), and the watch "Add the Taqwa face" opens the face's screen on
 * ([addFace]); null where the row is not shown. [NONE] is no row at all, which is every iPhone's
 * answer.
 */
internal data class WatchTargets(val install: WatchTarget?, val addFace: WatchTarget?) {
    companion object {
        val NONE: WatchTargets = WatchTargets(install = null, addFace = null)
    }
}

/** What a row asks of the watch: Taqwa's Play page, or the face's screen ([remoteIntentSpec]). */
internal enum class WatchAction { INSTALL, ADD_FACE }

/**
 * Which watch each row acts on, from the [connected] watches (null: no Wear API on this phone),
 * those that have Taqwa's watch app ([withApp], `taqwa_watch`) and those that say they have the face
 * to add ([wantsFace], `taqwa_face_addable`), each a set of node ids:
 * - **install** is the first directly connected watch without Taqwa (spec 5);
 * - **addFace** is the first directly connected watch with Taqwa that has the face to add. The
 *   watch says so only while `FaceCapability.wanted` holds (the face is not its face, and the one
 *   activation is unspent), so the row goes quiet with the watch's own chip (the Lead's call 5);
 * - a watch reached only through the cloud acts on nothing: a row would tap into a watch that may
 *   be anywhere, the way the feed itself goes only to a watch directly connected (Ruling 39).
 */
internal fun watchTargets(connected: List<WatchPeer>?, withApp: Set<String>, wantsFace: Set<String>): WatchTargets {
    val nearby = connected.orEmpty().filter { it.nearby }
    if (nearby.isEmpty()) return WatchTargets.NONE
    return WatchTargets(
        install = nearby.firstOrNull { it.id !in withApp }?.target(),
        addFace = nearby.firstOrNull { it.id in withApp && it.id in wantsFace }?.target(),
    )
}

private fun WatchPeer.target() = WatchTarget(nodeId = id, name = name)

/**
 * Whether Settings shows its Watch section: while an Android row applies, or the iPhone's "Add to
 * Apple Watch" is offered ([appleFaceOffered], plan 4's `WatchFaceOffer.available()`). Each platform
 * has only its own: an Android phone's offer is never available, and an iPhone's targets are always
 * [WatchTargets.NONE], so on the iPhone the section is exactly plan 4's row.
 */
internal fun watchSectionShown(targets: WatchTargets, appleFaceOffered: Boolean): Boolean =
    targets != WatchTargets.NONE || appleFaceOffered
