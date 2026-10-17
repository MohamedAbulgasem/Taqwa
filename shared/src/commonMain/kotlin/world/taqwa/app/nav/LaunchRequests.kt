package world.taqwa.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.notifications.adhkarSetOfNotificationId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The one-shot channel a widget tap uses to ask the app to open an ayah (design spec §8): the
 * Android Glance widget's tap action and the iOS extension's `widgetURL` both end up here, one
 * through `MainActivity`'s intent extras, the other through `LaunchRequests_iosKt.openAyahFromWidget`
 * (see `LaunchRequests.ios.kt`) called from `.onOpenURL`. `App` collects [pendingAyah] and
 * navigates once onboarding is out of the way, then calls [consume] — this works both cold (the
 * request is set before `App` ever composes) and warm (the app is already open).
 */
object LaunchRequests {

    private val _count = MutableStateFlow(0L)

    /**
     * How many requests that move the screen have been made since the process started, consumed
     * or not. Read twice, it says whether one arrived in between: on iOS, the app coming back to
     * the front with a recitation playing opens the recited ayah only if nothing asked for another
     * screen since the app left (a widget tap, an adhkar reminder).
     */
    val count: Long get() = _count.value

    private fun counted() {
        _count.update { it + 1 }
    }

    /** The clock [leavingForOwnLink] measures by; replaced only by tests. */
    internal var clock: TimeSource = TimeSource.Monotonic

    private val linkOpenedAt = MutableStateFlow<TimeMark?>(null)

    /**
     * The app is about to send the person elsewhere itself: a web page, a mail, iOS Settings. A
     * leave of the front a moment later is that trip ([leavingForOwnLink]), and the return from it
     * brings the person back to the screen they left from rather than to the recited ayah. Noted
     * before the link opens rather than counted as a request: the return compares the count with
     * what it was when the app left, and a count bumped before leaving would be part of that.
     */
    fun openingLink() {
        linkOpenedAt.value = clock.markNow()
    }

    /** The link reported that it did not open: the app is not leaving for it after all. */
    fun linkDidNotOpen() {
        linkOpenedAt.value = null
    }

    /**
     * Asked as the app leaves the front: whether it is leaving for a link it opened itself a
     * moment ago. Answered once. A link that opened nothing without saying so (the platform's own
     * link handler reports nothing) is forgotten after a few seconds, so a later leave that is the
     * person's own is not taken for it.
     */
    fun leavingForOwnLink(): Boolean {
        val opened = linkOpenedAt.getAndUpdate { null }
        return opened != null && opened.elapsedNow() < LINK_LEAVE_WINDOW
    }

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
        counted()
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
        counted()
    }

    fun consumePlaying() {
        _pendingPlaying.value = false
    }

    /** The Android intent extra the media session's tap carries; read by `MainActivity`. */
    const val ANDROID_EXTRA_OPEN_PLAYING = "open_playing"

    private val _pendingAdhkar = MutableStateFlow<AdhkarSet?>(null)

    /**
     * A tap on an adhkar reminder (adhkar spec §5): open that set's session on the Prayer tab.
     * Set from `MainActivity` on Android (the [ANDROID_EXTRA_OPEN_ADHKAR] extra) and from the
     * notification-centre delegate on iOS ([openAdhkarForNotification]); collected by
     * `AdhkarLaunchRequests` once onboarding is out of the way, cold or warm, as [pendingAyah] is.
     */
    val pendingAdhkar: StateFlow<AdhkarSet?> get() = _pendingAdhkar

    /** Overwrites any request still pending: only the most recent tap matters. */
    fun openAdhkar(set: AdhkarSet) {
        _pendingAdhkar.value = set
        counted()
    }

    fun consumeAdhkar() {
        _pendingAdhkar.value = null
    }

    /** The Android intent extra an adhkar reminder's tap carries, valued by [adhkarExtra]. */
    const val ANDROID_EXTRA_OPEN_ADHKAR = "open_adhkar"

    /** What [ANDROID_EXTRA_OPEN_ADHKAR] carries for [set]: "morning" or "evening". */
    fun adhkarExtra(set: AdhkarSet): String = set.name.lowercase()

    /**
     * Records the set an [ANDROID_EXTRA_OPEN_ADHKAR] value names. The launcher activity is
     * exported, so the value is untrusted: anything but one of [adhkarExtra]'s is ignored.
     */
    fun openAdhkarFromExtra(value: String?) {
        AdhkarSet.entries.firstOrNull { adhkarExtra(it) == value }?.let(::openAdhkar)
    }

    /** A tapped notification's id, on iOS: an adhkar reminder's opens its set, any other nothing. */
    fun openAdhkarForNotification(id: String) {
        adhkarSetOfNotificationId(id)?.let(::openAdhkar)
    }

    private val _pendingScreen = MutableStateFlow<String?>(null)

    /**
     * A screen asked for by name — "prayer", "quran", "settings", "notifications", "qibla",
     * "tasbeeh", "adhkar", "appearance" — by the debug harnesses only (the store-screenshot runs
     * open every screen in every language without a finger on the glass). Nothing in a release
     * build calls [openScreen]; an unknown name is ignored by the collector.
     */
    val pendingScreen: StateFlow<String?> get() = _pendingScreen

    fun openScreen(name: String) {
        _pendingScreen.value = name
        counted()
    }

    fun consumeScreen() {
        _pendingScreen.value = null
    }

    private val _pendingSearch = MutableStateFlow<String?>(null)

    /**
     * A Quran search typed by the debug harnesses only, for the same reason as [pendingScreen]:
     * `adb` cannot type Arabic, and the search results are one of the things a device run has to
     * look at (spec §17.4). Collected by the Quran root, so the harness opens "quran" first.
     */
    val pendingSearch: StateFlow<String?> get() = _pendingSearch

    fun search(query: String) {
        _pendingSearch.value = query
    }

    fun consumeSearch() {
        _pendingSearch.value = null
    }

    private val LINK_LEAVE_WINDOW = 5.seconds
    private const val SURAH_COUNT = 114
    private const val LONGEST_SURAH = 286
}
