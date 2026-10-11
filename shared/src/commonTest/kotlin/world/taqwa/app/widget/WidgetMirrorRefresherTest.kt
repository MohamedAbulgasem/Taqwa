package world.taqwa.app.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class MemoryMirrorStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String): String? = map[key]
}

// What the platforms put between the digits and the day-period marker (spec §1.2); built from its
// code point so no invisible character sits in this source.
private val NBSP = Char(0x00A0).toString()

/**
 * `format = null` is what the alarm and boot receivers and iOS's background refresh pass: nobody
 * holds a composition's format there, so the refresher builds one from the stored clock format.
 * These pin that wiring - without it a 12-hour reader's widgets would be rewritten in 24-hour by
 * the next prayer alarm, with the app closed.
 */
class WidgetMirrorRefresherTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val now = Instant.parse("2026-09-06T12:00:00Z")

    // An empty store of its own, as the other repository tests in this module do (a fixed name
    // would carry what an earlier run wrote into the next); DataStore wants an absolute path.
    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-test-refresher-$name-${Random.nextULong()}.preferences_pb".toPath()
        },
    )

    @Test
    fun aNullFormatIsBuiltFromTheStoredTwelveHourSetting() = runTest {
        val settings = settings("twelve-hour")
        settings.setLocation(london)
        settings.setClockFormat(ClockFormat.TWELVE_HOUR)
        val store = MemoryMirrorStore()

        assertTrue(WidgetMirrorRefresher.refresh(settings, engine, store = store, format = null, now = now))

        val snapshot = assertNotNull(WidgetMirrorWriter.read(store))
        assertTrue(snapshot.nextClockTime.contains(NBSP), snapshot.nextClockTime)
        assertEquals(5, snapshot.allClockTimes.size)
        snapshot.allClockTimes.forEach { (prayer, time) -> assertTrue(time.contains(NBSP), "$prayer $time") }
        assertTrue(snapshot.schedule.isNotEmpty())
        snapshot.schedule.forEach { assertTrue(it.clockTime.contains(NBSP), it.toString()) }
    }

    @Test
    fun aNullFormatWithTheDefaultSettingWritesTwentyFourHourTimes() = runTest {
        val settings = settings("default")
        settings.setLocation(london)
        val store = MemoryMirrorStore()

        assertTrue(WidgetMirrorRefresher.refresh(settings, engine, store = store, format = null, now = now))

        val snapshot = assertNotNull(WidgetMirrorWriter.read(store))
        assertFalse(snapshot.nextClockTime.contains(NBSP), snapshot.nextClockTime)
        assertFalse(clockTimesCarryMarker(snapshot.allClockTimes.values.toList() + snapshot.nextClockTime))
    }

    @Test
    fun aFormatTheCallerHoldsIsUsedAsIsWhateverIsStored() = runTest {
        val settings = settings("explicit")
        settings.setLocation(london)
        settings.setClockFormat(ClockFormat.TWELVE_HOUR)
        val store = MemoryMirrorStore()

        WidgetMirrorRefresher.refresh(settings, engine, store = store, format = EnglishPlatformFormat, now = now)

        val snapshot = assertNotNull(WidgetMirrorWriter.read(store))
        assertFalse(snapshot.nextClockTime.contains(NBSP), snapshot.nextClockTime)
    }

    @Test
    fun withNoLocationNothingIsWritten() = runTest {
        val store = MemoryMirrorStore()
        assertFalse(WidgetMirrorRefresher.refresh(settings("no-location"), engine, store = store, now = now))
        assertEquals(null, store.getString(WidgetInputsMirror.KEY))
    }
}
