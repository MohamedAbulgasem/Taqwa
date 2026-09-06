package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.i18n.PlatformFormat
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class FakeKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String): String? = map[key]
}

private class FakePlatformFormat(private val tag: String = "en-US") : PlatformFormat {
    override fun languageTag() = tag
    override fun localizedDigits(number: Int) = number.toString()
    override fun clockTime(hour: Int, minute: Int) =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

class WidgetMirrorWriterTest {

    private val today = TodayState(
        rows = listOf(
            TimelineRow(Prayer.FAJR, Instant.fromEpochSeconds(0), PrayerStatus.PASSED),
            TimelineRow(Prayer.DHUHR, Instant.fromEpochSeconds(3600 * 7), PrayerStatus.CURRENT),
            TimelineRow(Prayer.ASR, Instant.fromEpochSeconds(3600 * 10), PrayerStatus.UPCOMING),
            TimelineRow(Prayer.MAGHRIB, Instant.fromEpochSeconds(3600 * 18), PrayerStatus.UPCOMING),
            TimelineRow(Prayer.ISHA, Instant.fromEpochSeconds(3600 * 20), PrayerStatus.UPCOMING),
        ),
        next = PrayerTime(Prayer.ASR, Instant.fromEpochSeconds(3600 * 10)),
        countdown = 90.minutes,
        ringProgress = 0.3f,
    )

    @Test
    fun writingThenReadingReproducesTheCurrentAndNextPrayer() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat())
        val snapshot = WidgetMirrorWriter.read(store)!!
        assertEquals(Prayer.ASR, snapshot.nextPrayer)
        assertEquals(Prayer.DHUHR, snapshot.currentPrayer)
        assertEquals(90L, snapshot.countdownMinutes)
        assertEquals(0.3f, snapshot.ringProgress)
    }

    // The exact phrase `Res.string.today_next_in` renders for Today's ring — "Asr in" in
    // English, "متبقٍ على العصر" in Arabic — for the next prayer. This is what proves the mirror
    // writes the localised sentence itself, never a hardcoded English suffix it composes on its
    // own (that bug is this fix's whole reason to exist), and that it responds to the device's
    // language rather than being pinned to one.
    @Test
    fun countdownLabelIsTheLocalisedNextPrayerInPhraseForTheNextPrayer() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat("en-US"))
        assertEquals("Asr in", WidgetMirrorWriter.read(store)!!.countdownLabel)
    }

    @Test
    fun countdownLabelSwitchesToArabicWithTheDeviceLanguage() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat("ar"))
        assertEquals("متبقٍ على العصر", WidgetMirrorWriter.read(store)!!.countdownLabel)
    }

    // -- C5: the ring is the one field that used to change every second -----------------------
    //
    // Every other field here is minute-granular, so `ringProgress` alone made the serialised
    // snapshot differ on all 60 of a minute's ticks — which is what defeated iOS's `lastSnapshot`
    // dedupe and exhausted WidgetKit's daily reload budget in under a minute.

    @Test
    fun theRingIsQuantisedToOneHundredAndTwentiethsBeforeItReachesTheMirror() {
        val store = FakeKeyValueStore()
        // 0.5041666 sits between two 1/120 steps; the mirror must carry the nearer step exactly.
        WidgetMirrorWriter.write(store, today.copy(ringProgress = 0.5041666f), "UTC", FakePlatformFormat())
        val written = WidgetMirrorWriter.read(store)!!.ringProgress
        assertEquals(0.5f, written)
        assertTrue(abs(round(written * 120f) - written * 120f) < 1e-4f, "not a multiple of 1/120: $written")
    }

    // One second of a three-hour gap moves raw `elapsed / total` by about 1/10800 — far below one
    // 1/120 step — so the two snapshots must serialise to the identical string. That equality is
    // exactly what `TodayViewModel` compares to decide whether to write and nudge at all.
    @Test
    fun oneSecondOfProgressAcrossATypicalPrayerGapSerialisesIdentically() {
        val total = 3 * 60 * 60f
        val atT = today.copy(ringProgress = 4000f / total)
        val aSecondLater = today.copy(ringProgress = 4001f / total)
        assertEquals(
            WidgetMirrorWriter.serializedSnapshot(atT, "UTC", FakePlatformFormat()),
            WidgetMirrorWriter.serializedSnapshot(aSecondLater, "UTC", FakePlatformFormat()),
        )
    }

    // ...but a real move must still get through: quantising may never freeze the ring outright.
    @Test
    fun aFullStepOfProgressStillChangesTheSerialisedSnapshot() {
        assertTrue(
            WidgetMirrorWriter.serializedSnapshot(today.copy(ringProgress = 0.30f), "UTC", FakePlatformFormat()) !=
                WidgetMirrorWriter.serializedSnapshot(today.copy(ringProgress = 0.32f), "UTC", FakePlatformFormat()),
        )
    }

    @Test
    fun readingBeforeAnyWriteReturnsNullRatherThanCrashing() {
        assertEquals(null, WidgetMirrorWriter.read(FakeKeyValueStore()))
    }

    // Nothing wrote `widget_background` into the KeyValueStore until this task — both widgets
    // were stuck on FOLLOW_THEME forever. This is the one write that closes that gap: the
    // Appearance screen calls `writeBackground` beside `SettingsRepository.setWidgetBackground`,
    // under the exact key `TaqwaGlanceWidget.kt` (Android) and `TaqwaMirror.background()` (iOS)
    // read literally.
    @Test
    fun writeBackgroundStoresTheEnumNameUnderTheKeyBothWidgetsRead() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.writeBackground(store, WidgetBackground.TRANSLUCENT_OR_FROSTED)
        assertEquals("TRANSLUCENT_OR_FROSTED", store.getString("widget_background"))
    }

    @Test
    fun writeBackgroundRoundTripsEveryValue() {
        val store = FakeKeyValueStore()
        WidgetBackground.entries.forEach { value ->
            WidgetMirrorWriter.writeBackground(store, value)
            assertEquals(value.name, store.getString("widget_background"))
        }
    }
}
