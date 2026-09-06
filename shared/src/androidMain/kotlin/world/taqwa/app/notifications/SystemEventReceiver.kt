package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import world.taqwa.app.city.CityRepository
import world.taqwa.app.location.LocationRefresher
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore
import kotlin.time.Clock

/**
 * `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` all invalidate whatever is currently
 * scheduled: a reboot clears every `AlarmManager` entry outright, and a clock or timezone
 * change can silently leave the existing plan pointing at the wrong wall-clock moments.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RescheduleTrigger.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED -> RescheduleTrigger.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleTrigger.TIMEZONE_CHANGED
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            // goAsync() buys roughly ten seconds before the system may kill the process, and a
            // reschedule that overruns it can be cut off mid-DataStore-write. Giving up a little
            // early leaves the store consistent; the next foreground reschedule will catch up.
            try {
                withTimeout(WORK_BUDGET_MILLIS) {
                    val settingsRepository = SettingsRepository(createDataStore())
                    // A TIMEZONE_CHANGED broadcast is the strongest signal the app ever gets
                    // that the user has moved, and it arrives whether or not the app is running,
                    // so the receiver builds the same refresher the container does.
                    val refresher = LocationRefresher(
                        LocationRepository(createLocationProvider()),
                        CityRepository { Res.readBytes("files/cities.csv").decodeToString() },
                        settingsRepository,
                    )
                    val coordinator = NotificationCoordinator(
                        engine = PrayerTimesEngine(),
                        settingsRepository = settingsRepository,
                        locationOf = { settingsRepository.location.first() },
                        scheduler = createNotificationScheduler(),
                        now = { Clock.System.now() },
                        locationFor = { refresher.refreshFor(it) },
                    )
                    coordinator.reschedule(trigger)
                }
            } catch (_: TimeoutCancellationException) {
                // Nothing useful to do from a broadcast receiver but stop cleanly.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        /** Comfortably inside goAsync()'s own allowance, with room for finish() to run. */
        const val WORK_BUDGET_MILLIS = 8_000L
    }
}
