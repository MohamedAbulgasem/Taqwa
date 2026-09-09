package world.taqwa.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one-shot channel a widget tap uses to ask the app to open an ayah (design spec §8): the
 * Android Glance widget's tap action and the iOS extension's `widgetURL` both end up here, one
 * through `MainActivity`'s intent extras, the other through `LaunchRequests_iosKt.openAyahFromWidget`
 * (see `LaunchRequests.ios.kt`) called from `.onOpenURL`. `App` collects [pendingAyah] and
 * navigates once onboarding is out of the way, then calls [consume] — this works both cold (the
 * request is set before `App` ever composes) and warm (the app is already open).
 */
object LaunchRequests {
    private val _pendingAyah = MutableStateFlow<Pair<Int, Int>?>(null)

    /** The surah-to-ayah reference an in-flight widget tap wants opened, or null when there is
     * none pending. */
    val pendingAyah: StateFlow<Pair<Int, Int>?> get() = _pendingAyah

    /** Records a request to open [surah]:[ayah]. Overwrites any request still pending — only the
     * most recent tap matters. */
    fun openAyah(surah: Int, ayah: Int) {
        _pendingAyah.value = surah to ayah
    }

    /** Clears the pending request once it has been acted on. */
    fun consume() {
        _pendingAyah.value = null
    }
}
