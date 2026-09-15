package world.taqwa.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.taqwa.app.di.appContainer
import world.taqwa.app.recitation.NowPlayingText
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.Reciters

/**
 * Debug-only remote control for the recitation player, so slice 3a task 3 can be verified on a
 * device before task 4 gives it any UI. Never referenced from `androidMain`: it exists in the
 * debug variant alone and is not in a release build at all.
 *
 * ```
 * adb shell am broadcast -a world.taqwa.app.debug.RECITATION \
 *   -n world.taqwa.app/world.taqwa.app.debug.RecitationHarnessReceiver \
 *   --es cmd load --ei surah 36 --ei ayah 1 --es reciter ar.alafasy
 * ```
 *
 * Commands: `load`, `play`, `pause`, `toggle`, `next`, `prev`, `seek --ei ayah n`, `stop`,
 * `state`, `reconcile` (adopt the containers pushed onto the device), `focus` (take audio focus
 * away with a second player, to see the recitation pause).
 *
 * Everything it does is logged under the tag `TaqwaHarness`, including every state change with a
 * millisecond timestamp — which is how the ayah boundary and the gap are actually measured.
 */
class RecitationHarnessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val command = intent.getStringExtra("cmd").orEmpty()
        val reciterId = intent.getStringExtra("reciter") ?: "ar.alafasy"
        val surah = intent.getIntExtra("surah", 1)
        val ayah = intent.getIntExtra("ayah", 1)
        val gapMs = intent.getIntExtra("gap", -1)
        Log.i(TAG, "cmd=$command reciter=$reciterId surah=$surah ayah=$ayah gap=$gapMs")
        val player = appContainer.recitationPlayer
        scope.launch {
            try {
                when (command) {
                    "load" -> {
                        watch()
                        player.load(
                            reciter = reciter(reciterId, gapMs),
                            surah = surah,
                            startAyah = ayah,
                            text = NowPlayingText(
                                title = "Surah $surah",
                                subtitle = "Mishary Rashid Alafasy",
                            ),
                        )
                    }
                    "play" -> player.play()
                    "pause" -> player.pause()
                    "toggle" -> player.toggle()
                    "next" -> player.next()
                    "prev" -> player.previous()
                    "seek" -> player.seekToAyah(ayah)
                    "stop" -> player.stop()
                    "state" -> Log.i(TAG, "state ${player.state.value}")
                    "reconcile" -> {
                        val library = appContainer.recitationLibrary
                        library.reconcile()
                        // `.first()`, not the Flow itself: logging the Flow prints its identity
                        // hash and says nothing about which surahs were adopted.
                        Log.i(
                            TAG,
                            "reconciled $reciterId: " +
                                library.downloaded(reciterId).first().sorted(),
                        )
                    }
                    "focus" -> stealFocus(context)
                    // The alarm receiver's question and answer (spec §16.5), from a process with
                    // no Activity: has the phone moved, and where does a background refresh land?
                    "location" -> {
                        val refresher = appContainer.locationRefresher
                        Log.i(TAG, "location lastKnown=${appContainer.locationRepository.lastKnownCoordinates()} hasMoved=${refresher.hasMoved()}")
                        val landed = refresher.refreshFor(world.taqwa.app.notifications.RescheduleTrigger.ALARM_FIRED)
                        Log.i(TAG, "location after ALARM_FIRED refresh: ${landed?.cityName} ${landed?.latitude},${landed?.longitude}")
                    }
                    else -> Log.w(TAG, "unknown command \"$command\"")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "command $command failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** One collector for the life of the process, logging every state the player publishes. */
    private fun watch() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            appContainer.recitationPlayer.state.collectLatest {
                Log.i(TAG, "state ayah=${it.ayah}/${it.ayahCount} playing=${it.playing} pos=${it.positionMs} dur=${it.durationMs} surah=${it.surah}")
            }
        }
    }

    /**
     * A second player that asks for the focus with `AUDIOFOCUS_GAIN`, which is what a call, an
     * alarm or another music app does. The recitation should pause on its own.
     */
    private fun stealFocus(context: Context) {
        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .build()
        val granted = manager.requestAudioFocus(request)
        Log.i(TAG, "focus request granted=$granted")
        // Something has to actually make a noise, or the platform may hand the focus straight
        // back. The notification sound is always present on an emulator image.
        runCatching {
            val uri = android.media.RingtoneManager
                .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            MediaPlayer.create(context, uri)?.apply {
                setOnCompletionListener {
                    it.release()
                    manager.abandonAudioFocusRequest(request)
                    Log.i(TAG, "focus abandoned")
                }
                start()
            }
        }.onFailure { Log.w(TAG, "could not sound the interruption", it) }
    }

    /**
     * The launch table's gap and id are enough to drive the player; the rest of [Reciter] is
     * manifest data no part of playback reads. `--ei gap` overrides it, to see the boundary with
     * and without silence between ayahs.
     */
    private fun reciter(id: String, gapMs: Int): Reciter {
        val launch = Reciters.LAUNCH.firstOrNull { it.id == id }
        return Reciter(
            id = id,
            nameEn = id,
            nameAr = id,
            style = "murattal",
            kbps = 64,
            gapMs = if (gapMs >= 0) gapMs else (launch?.gapMs ?: 300),
            hue = launch?.hue?.name ?: "AMBER",
            release = "audio-$id-v1",
            totalBytes = 0L,
        )
    }

    private companion object {
        const val TAG = "TaqwaHarness"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var watching: Job? = null
    }
}
