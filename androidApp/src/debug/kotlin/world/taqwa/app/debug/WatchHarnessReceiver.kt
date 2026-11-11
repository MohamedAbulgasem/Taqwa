package world.taqwa.app.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.taqwa.app.di.appContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.watchfeed.WatchFeedDebug

/**
 * Debug-only remote control for the watch link (plan 2 Task 7), on the phone emulator the pass
 * runs on. Never referenced from a main source set: it exists in the debug variant alone, and it
 * uses only public API, the app container's settings repository and coordinator, as
 * `RecitationHarnessReceiver` does, and [WatchFeedDebug], the watch side's debug seam.
 *
 * ```
 * adb -s emulator-5562 shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver \
 *   -a world.taqwa.app.debug.WATCH --es cmd <cmd> [extras]
 * ```
 *
 * Commands:
 * - `nodes`: every connected node with its id, name and nearby flag, and which have Taqwa's watch
 *   app, or "no Wear API";
 * - `place --es place "lat,lon,zone,name[,cityId]"`: the location, MANUAL as its source and the
 *   onboarding marked done, then a reschedule and a settle. London is
 *   `51.5074,-0.1278,Europe/London,London,2643743`, Tripoli `32.8872,13.1913,Africa/Tripoli,Tripoli,2210247`.
 *   Both writes keep the pass on its place: a fresh install opens on onboarding, whose location
 *   step could replace the city, and a launch with any source but MANUAL asks for a fix;
 * - `clock --es format 12|24`: the setting, then a reschedule and a settle;
 * - `phrase --es text … --ei target n`: one of the user's own tasbeeh phrases added, then the ask
 *   the Tasbeeh screen makes (`PHRASES_CHANGED`), both through [WatchFeedDebug.phrase], since the
 *   phrase's own type is `:watchcore`'s and never reaches this module;
 * - `send`: an ask that forgets the confirmation first (`WATCH_CHANGED`); `send --ei version n`
 *   sends the feed with its version raised to n, through the link directly;
 * - `appeared`: the damped ask a reconnect makes (`WATCH_APPEARED`);
 * - `reschedule`: a reschedule and a settle, printing the plan's size and its first entry;
 * - `language`: the app's interface language the next feed is built in;
 * - `forms`: the phone's own forms ([WatchFeedDebug.phoneFormsJson]) written to
 *   `files/phone-forms-device.json`, which `run-as world.taqwa.app cat` reads back.
 *
 * Each command prints its result: as the broadcast's result data, which `am broadcast` shows
 * (`Broadcast completed: result=-1, data="…"`), and in the log under [TAG]. The sync's own lines
 * are `System.out`'s `TaqwaWatch …`.
 */
class WatchHarnessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val command = intent.getStringExtra("cmd").orEmpty()
        val ordered = isOrderedBroadcast
        val pending = goAsync()
        scope.launch {
            val printed = try {
                run(context, command, intent)
            } catch (e: Exception) {
                Log.e(TAG, "command $command failed", e)
                "FAILED $command: ${e::class.simpleName}: ${e.message}"
            }
            Log.i(TAG, "$command: $printed")
            if (ordered) pending.setResult(Activity.RESULT_OK, printed, null)
            pending.finish()
        }
    }

    private suspend fun run(context: Context, command: String, intent: Intent): String {
        val settings = appContainer.settingsRepository
        return when (command) {
            "nodes" -> WatchFeedDebug.nodes(context)
            "place" -> {
                val place = placeOf(intent.getStringExtra("place").orEmpty())
                settings.setLocation(place)
                settings.setLocationSource(LocationSource.MANUAL)
                settings.setOnboardingComplete(true)
                val stored = settings.location.first()
                "stored ${stored?.cityName} id=${stored?.cityId} zone=${stored?.timeZoneId} " +
                    "at ${stored?.latitude},${stored?.longitude}; " + rescheduled()
            }
            "clock" -> {
                val format = when (intent.getStringExtra("format")) {
                    "12" -> ClockFormat.TWELVE_HOUR
                    "24" -> ClockFormat.TWENTY_FOUR_HOUR
                    else -> return "clock needs --es format 12 or 24"
                }
                settings.setClockFormat(format)
                "clock ${settings.clockFormat.first()}; " + rescheduled()
            }
            "phrase" -> WatchFeedDebug.phrase(intent.getStringExtra("text").orEmpty(), intent.getIntExtra("target", 33))
            "send" ->
                if (intent.hasExtra("version")) {
                    val version = intent.getIntExtra("version", 0)
                    "version $version ${WatchFeedDebug.sendWithVersion(version)}"
                } else {
                    "WATCH_CHANGED ${WatchFeedDebug.sync("WATCH_CHANGED")}"
                }
            "appeared" -> "WATCH_APPEARED ${WatchFeedDebug.sync("WATCH_APPEARED")}"
            "reschedule" -> rescheduled()
            "language" -> WatchFeedDebug.language()
            "forms" -> {
                val file = File(context.filesDir, FORMS_FILE)
                file.writeText(WatchFeedDebug.phoneFormsJson())
                "wrote files/$FORMS_FILE (${file.length()} bytes)"
            }
            else -> "unknown command \"$command\""
        }
    }

    /** A reschedule as a settings change makes one, then the watch side settled: what both came to. */
    private suspend fun rescheduled(): String {
        val plan = appContainer.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        val first = plan.firstOrNull()?.let { "${it.id} ${it.kind} at ${it.instant}" } ?: "none"
        return "plan of ${plan.size}, first $first; settled=${WatchFeedDebug.settle()}"
    }

    /** "lat,lon,zone,name[,cityId]" as the location the Location screen would store. */
    private fun placeOf(text: String): GeoLocation {
        val parts = text.split(',')
        require(parts.size == 4 || parts.size == 5) { "place is \"lat,lon,zone,name[,cityId]\", not \"$text\"" }
        return GeoLocation(
            latitude = parts[0].trim().toDouble(),
            longitude = parts[1].trim().toDouble(),
            timeZoneId = parts[2].trim(),
            cityName = parts[3].trim(),
            cityId = parts.getOrNull(4)?.trim()?.toInt(),
        )
    }

    private companion object {
        const val ACTION = "world.taqwa.app.debug.WATCH"
        const val TAG = "TaqwaWatchHarness"
        const val FORMS_FILE = "phone-forms-device.json"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
