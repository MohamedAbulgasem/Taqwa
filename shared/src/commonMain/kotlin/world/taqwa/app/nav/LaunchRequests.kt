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

    /**
     * Records a request to open [surah]:[ayah]. Overwrites any request still pending — only the
     * most recent tap matters.
     *
     * A reference outside the Quran is dropped here, not by the callers: the Android launcher
     * activity is exported, so any app on the phone can start it with `open_surah=999`, and a
     * surah the database does not have used to reach the reader and throw inside its coroutine.
     * The bounds are the Quran's own — 114 surahs, none longer than al-Baqarah's 286 ayahs — and
     * an ayah past its own surah's length is left to the reader, which clamps it.
     */
    fun openAyah(surah: Int, ayah: Int) {
        if (surah !in 1..SURAH_COUNT || ayah !in 1..LONGEST_SURAH) return
        _pendingAyah.value = surah to ayah
    }

    /** Clears the pending request once it has been acted on. */
    fun consume() {
        _pendingAyah.value = null
    }

    private val _pendingPlaying = MutableStateFlow(false)

    /**
     * A tap on the media notification or lock-screen player (spec §15.5): open the ayah being
     * recited, wherever it is by the time the app is in front. Resolved at that moment rather
     * than carried as a reference, because the voice keeps moving while the app comes up.
     */
    val pendingPlaying: StateFlow<Boolean> get() = _pendingPlaying

    fun openPlaying() {
        _pendingPlaying.value = true
    }

    fun consumePlaying() {
        _pendingPlaying.value = false
    }

    /** The Android intent extra the media session's tap carries; read by `MainActivity`. */
    const val ANDROID_EXTRA_OPEN_PLAYING = "open_playing"

    private val _pendingScreen = MutableStateFlow<String?>(null)

    /**
     * A screen asked for by name — "prayer", "quran", "settings", "notifications", "qibla",
     * "tasbeeh", "appearance" — by the debug harnesses only (the store-screenshot runs open every
     * screen in every language without a finger on the glass). Nothing in a release build calls
     * [openScreen]; an unknown name is ignored by the collector.
     */
    val pendingScreen: StateFlow<String?> get() = _pendingScreen

    fun openScreen(name: String) {
        _pendingScreen.value = name
    }

    fun consumeScreen() {
        _pendingScreen.value = null
    }

    private const val SURAH_COUNT = 114
    private const val LONGEST_SURAH = 286
}
