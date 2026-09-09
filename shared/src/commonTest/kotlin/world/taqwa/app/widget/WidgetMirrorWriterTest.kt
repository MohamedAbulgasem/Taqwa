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

private class FakePlatformFormat(
    private val tag: String = "en-US",
    private val arabicIndicDigits: Boolean = false,
) : PlatformFormat {
    override fun languageTag() = tag
    override fun localizedDigits(number: Int) =
        if (arabicIndicDigits) number.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
        else number.toString()
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
    fun theSnapshotRecordsTheDigitSetTheFormatItselfDraws() {
        // Not the CLDR default for the tag: the countdown a widget composes itself has to match
        // the clock times this same format produced beside it (D2, S23 round).
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat("ar-LY", arabicIndicDigits = true))
        assertEquals(true, WidgetMirrorWriter.read(store)?.arabicIndicDigits)
        assertEquals(false, WidgetDigits.defaultsToArabicIndic("ar-LY"))

        val western = FakeKeyValueStore()
        WidgetMirrorWriter.write(western, today, "UTC", FakePlatformFormat("ar-EG"))
        assertEquals(false, WidgetMirrorWriter.read(western)?.arabicIndicDigits)
    }

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

    // -- I8: the mirror carries absolute instants, not just a frozen countdown ------------------

    @Test
    fun theWriterRecordsWhenTheNextPrayerActuallyFallsNotJustHowFarAwayItWas() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat())
        val snapshot = WidgetMirrorWriter.read(store)!!
        assertEquals(3600L * 10, snapshot.nextPrayerEpochSeconds)
        // The DHUHR row, the one the timeline marked CURRENT — the ring's other end.
        assertEquals(3600L * 7, snapshot.previousPrayerEpochSeconds)
    }

    // Rendered three hours after it was written, the mirror must not still be claiming the
    // ninety minutes that were true at write time.
    @Test
    fun aMirrorWrittenThreeHoursAgoYieldsNoCountdownRatherThanTheStaleOne() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat())
        val snapshot = WidgetMirrorWriter.read(store)!!
        assertEquals(90L, snapshot.countdownMinutes)
        assertEquals(null, WidgetCountdown.remainingMinutesAt(snapshot, 3600L * 13))
    }

    // Before the day's Fajr no obligatory prayer has passed, so there is no start point to record.
    // The sentinel says so plainly instead of synthesising one.
    @Test
    fun beforeTheFirstPrayerOfTheDayThePreviousInstantIsTheNotRecordedSentinel() {
        val beforeFajr = today.copy(
            rows = today.rows.map { it.copy(status = PrayerStatus.UPCOMING) },
        )
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, beforeFajr, "UTC", FakePlatformFormat())
        assertEquals(0L, WidgetMirrorWriter.read(store)!!.previousPrayerEpochSeconds)
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

class WidgetMirrorWriterScheduleTest {

    private fun dayAt(offsetHours: Long) = world.taqwa.app.domain.DayPrayerTimes(
        date = kotlinx.datetime.LocalDate(2026, 9, 6),
        times = listOf(
            PrayerTime(Prayer.FAJR, Instant.fromEpochSeconds((offsetHours + 5) * 3600)),
            PrayerTime(Prayer.SUNRISE, Instant.fromEpochSeconds((offsetHours + 6) * 3600)),
            PrayerTime(Prayer.DHUHR, Instant.fromEpochSeconds((offsetHours + 12) * 3600)),
            PrayerTime(Prayer.ASR, Instant.fromEpochSeconds((offsetHours + 15) * 3600)),
            PrayerTime(Prayer.MAGHRIB, Instant.fromEpochSeconds((offsetHours + 18) * 3600)),
            PrayerTime(Prayer.ISHA, Instant.fromEpochSeconds((offsetHours + 20) * 3600)),
        ),
        highLatitudeRuleApplied = null,
    )

    private val today = TodayState(
        rows = listOf(TimelineRow(Prayer.DHUHR, Instant.fromEpochSeconds(12 * 3600), PrayerStatus.CURRENT)),
        next = PrayerTime(Prayer.ASR, Instant.fromEpochSeconds(15 * 3600)),
        countdown = 60.minutes,
        ringProgress = 0.5f,
    )

    @Test
    fun theTwoDaysBecomeTenScheduledObligatoryPrayersWithClockStrings() {
        val snapshot = WidgetMirrorWriter.snapshotOf(today, "UTC", FakePlatformFormat(), listOf(dayAt(0), dayAt(24)))
        assertEquals(10, snapshot.schedule.size)
        // Sunrise is never a widget row.
        assertTrue(snapshot.schedule.none { it.prayer == Prayer.SUNRISE })
        val tomorrowsFajr = snapshot.schedule.single { it.prayer == Prayer.FAJR && it.dayIndex == 1 }
        assertEquals(29 * 3600L, tomorrowsFajr.epochSeconds)
        assertEquals("05:00", tomorrowsFajr.clockTime)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1), snapshot.schedule.map { it.dayIndex })
    }

    @Test
    fun theScheduleSurvivesTheStore() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat(), listOf(dayAt(0), dayAt(24)))
        assertEquals(10, WidgetMirrorWriter.read(store)!!.schedule.size)
    }
}
