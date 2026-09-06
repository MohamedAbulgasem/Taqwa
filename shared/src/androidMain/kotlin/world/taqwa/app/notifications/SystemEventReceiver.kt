package world.taqwa.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
            try {
                val settingsRepository = SettingsRepository(createDataStore())
                // A TIMEZONE_CHANGED broadcast is the strongest signal the app ever gets that
                // the user has moved, and it arrives whether or not the app is running, so the
                // receiver builds the same refresher the container does.
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
