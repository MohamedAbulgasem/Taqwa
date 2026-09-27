package world.taqwa.app.recitation

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import world.taqwa.app.nav.LaunchRequests
import world.taqwa.app.notifications.notificationSmallIconResId

/**
 * Where recitation actually plays (spec §6). A `MediaSessionService` rather than a player inside
 * the Activity, because the two things the brief asks for — audio that survives the app going to
 * the background, and controls on the lock screen — are both what a media session is for. The
 * notification, the lock-screen transport and the artwork are the platform's, drawn from the
 * session; this app draws none of them.
 *
 * The app talks to this through a `MediaController` (see `RecitationPlayer`), which is also how
 * Android Auto, Assistant and a paired watch would talk to it.
 */
@UnstableApi
class RecitationService : MediaSessionService() {

    private var session: MediaSession? = null

    /** The session's player, kept so the app's clock can be handed to it (see [AyahPlayer.timeline]). */
    private var ayahPlayer: AyahPlayer? = null

    /** Read-aloud's voice (spec §5.5), kept so the app's script can be handed to it. */
    private var speaker: Speaker? = null

    /**
     * The two lines the notification shows, set by the app just before it sets the queue.
     *
     * It travels as a custom command rather than on each `MediaItem` for a plain reason: a
     * controller's media items cross a binder, `MediaItem`s lose their URI on the way over
     * (Media3 strips it to keep transactions small), and Al-Baqarah is 571 items. Sending the
     * text once and building the metadata on this side keeps the largest surah's queue well under
     * the binder's 1 MB. The artwork is named by URI on each item and never carried as bytes;
     * see [AppIconArtwork] for what carrying it cost.
     */
    private var nowPlaying: NowPlayingText? = null

    /** Whatever the main thread has to do next; see [teardown]. */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * True once a queue has actually been set, so an empty player at start-up is not mistaken for
     * one that has finished. Only [teardown] reads it.
     */
    private var loaded = false

    /**
     * The queue has been emptied — the surah read itself out, or the reader dismissed the bar.
     * Either way there is nothing left to play, and a media session with an empty player is a
     * foreground service and an undismissable `NO_CLEAR` notification for nothing. Media3 takes
     * the notification down on its own; the service and the session it holds are ours to end.
     *
     * `stopSelf()` alone would not do it, for the reason [onTaskRemoved] sets out at length: a
     * service that is both started and bound goes only when it is both stopped and unbound, and
     * this app's own `MediaController` lives in `AppContainer`. Releasing the session first is
     * what disconnects it.
     *
     * Posted, not run here: this arrives inside a player callback, and releasing the player from
     * inside one of its own callbacks is not something to ask of it. The re-check on the other
     * side of the post is what lets a load that arrives in between win.
     */
    private val teardown = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.mediaItemCount > 0) {
                loaded = true
                return
            }
            if (!loaded) return
            loaded = false
            handler.post {
                if (session?.player?.mediaItemCount != 0) return@post
                releaseSession()
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val paths = createRecitationPaths()
        val voice = Speaker(this).also { speaker = it }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(RecitationSourceFactory(TaqaDataSource.Factory(paths), voice))
            // Recitation is speech, and it pauses rather than ducks: a recitation read at a
            // quarter volume under a notification chime is worse than one that waited.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(teardown)
        // Read-aloud (spec §5.5): what the real current item is, for the app's own bar — the
        // session itself only ever shows ayahs (see [AyahPlayer]).
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishPhase(player)
        })
        // Wrapped, never the raw ExoPlayer: the queue underneath is `[ayah, gap, ayah, …]`, and
        // Media3's own Previous and Next step one item — which from the lock screen means stepping
        // onto a 300 ms silence instead of going back an ayah. [AyahPlayer] is the same
        // `RecitationQueue` rule the app's own bar uses, applied to everything outside the app.
        val wrapped = AyahPlayer(player)
        ayahPlayer = wrapped
        val built = MediaSession.Builder(this, wrapped)
            .setCallback(Callback())
            .apply { sessionActivity()?.let { setSessionActivity(it) } }
            .build()
        session = built
        // The lock screen's previous and next (spec §15.1) are the app's to answer: which surah,
        // whether it is on the phone, from where. Told to every connected controller — the app's
        // own is the one that acts.
        wrapped.onSurahSkip = { forward ->
            built.broadcastCustomCommand(
                SessionCommand(if (forward) COMMAND_SURAH_NEXT else COMMAND_SURAH_PREVIOUS, Bundle.EMPTY),
                Bundle.EMPTY,
            )
        }
        // Media3's own default small icon is a generic music note. The status bar should say
        // Taqwa, and the mark the prayer notifications already use is the one it should say it
        // with; `:androidApp` puts the id there in `TaqwaApplication.onCreate`.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(notificationSmallIconResId)
            },
        )
    }

    /**
     * What the real current item is — an ayah, a silence or a translation — as last published in
     * the session extras ([EXTRA_PHASE]). The session itself reports ayahs only ([AyahPlayer]),
     * so this is how the app learns that the voice is reading, and where a live rebuild of the
     * queue must start (read-aloud spec §5.5). Sent only when it changes: a few times an ayah,
     * never with every position update.
     */
    private var phase = PHASE_AYAH

    private fun publishPhase(player: Player) {
        val id = player.currentMediaItem?.mediaId.orEmpty()
        val now = when {
            id.startsWith("$SPEECH_SCHEME://") -> PHASE_SPEECH
            id.startsWith("$SILENCE_SCHEME://") -> PHASE_SILENCE
            else -> PHASE_AYAH
        }
        if (now == phase) return
        phase = now
        session?.setSessionExtras(Bundle().apply { putInt(EXTRA_PHASE, now) })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * What a tap on the notification or the lock-screen player opens (spec §15.5): the app's
     * launcher activity, told to show the ayah being recited. The launcher intent rather than a
     * class name, because this service lives in the shared module and the activity does not.
     */
    private fun sessionActivity(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.putExtra(LaunchRequests.ANDROID_EXTRA_OPEN_PLAYING, true)
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_PLAYING,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The app has been swiped out of Recents. Keep playing if it is playing — that is what a
     * background service is for, and the notification is still there to stop it with. Otherwise
     * go, notification and all: a paused surah nobody can see is not worth a process.
     *
     * `stopSelf()` on its own does not do it, which is worth writing down because the obvious
     * version was written first and measured on a device doing nothing at all. A service that is
     * both started and bound is destroyed only once it is *both* stopped and unbound, and after a
     * swipe-away this app's own `MediaController` is still bound: `RecitationPlayer` lives in
     * `AppContainer`, which outlives the Activity by design. Media3's own `onTaskRemoved` has the
     * same limit — it pauses every player and calls `stopSelf()`, which drops the foreground and
     * leaves the process cached, but leaves the session, the service object and the notification
     * standing. Releasing the session first is what disconnects the controller and lets the
     * service actually be destroyed; `RecitationPlayer` hears that as `onDisconnected` and builds
     * a new controller — and a new service — the next time the reader plays something.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val playing = player != null &&
            player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED
        if (playing) return
        releaseSession()
        stopSelf()
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    /**
     * Idempotent: [onTaskRemoved] gets here first, and [onDestroy] follows it. The voice goes
     * after the player, whose release interrupts a synthesis still waiting on a loading thread;
     * neither waits on the other here (see [Speaker]).
     */
    private fun releaseSession() {
        session?.run {
            player.release()
            release()
        }
        session = null
        ayahPlayer = null
        speaker?.release()
        speaker = null
    }

    private inner class Callback : MediaSession.Callback {

        override fun onConnect(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                // The notification's two ayah buttons, which the system's own controller presses.
                .add(SessionCommand(COMMAND_PREVIOUS_AYAH, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_NEXT_AYAH, Bundle.EMPTY))
                .apply {
                    // The text, the clock and read-aloud's script are this app's alone to send
                    // (read-aloud spec §5.5). The service is exported, and without this any app on
                    // the phone could hand the session a script for its voice to read aloud. Media3
                    // checks a controller's package against its calling uid before this is asked,
                    // and refuses a custom command the controller was not granted here.
                    if (controller.packageName == this@RecitationService.packageName) {
                        add(SessionCommand(COMMAND_NOW_PLAYING, Bundle.EMPTY))
                    }
                }
                .build()
            val accepted = MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                .setAvailableSessionCommands(commands)
            // The media notification controller's commands are what Media3 gives the *platform*
            // session, and without COMMAND_GET_TIMELINE it publishes no queue there at all
            // (spec §18.2). A surah's queue is its ayahs and the silences between them, up to
            // 571 rows that all read "Al-Baqarah": nothing a car, a watch or the system's player
            // can use, and every change of it is a transaction to each of them. The app's own
            // controller keeps the timeline — it is how the bar knows which ayah is playing.
            if (mediaSession.isMediaNotificationController(controller)) {
                accepted.setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_GET_TIMELINE)
                        .build(),
                )
            }
            return accepted.build()
        }

        override fun onCustomCommand(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_NOW_PLAYING) {
                nowPlaying = NowPlayingText(
                    title = args.getString(ARG_TITLE).orEmpty(),
                    subtitle = args.getString(ARG_SUBTITLE).orEmpty(),
                )
                // The surah's clock (spec §14.1), one entry per queue item, gaps and translations
                // included. It arrives before the queue it describes and is only read once the
                // item count matches, which the wrapper checks on every call.
                ayahPlayer?.timeline = args.getLongArray(ARG_TIMELINE)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { SurahTimeline(it.toList()) }
                // Read-aloud (spec §5.5): what the speech items of the queue about to be set say.
                // No script — read-aloud off — lets the voice's engine go.
                val ayahs = args.getIntArray(ARG_SPEECH_AYAHS)
                val texts = args.getStringArray(ARG_SPEECH_TEXTS)
                speaker?.setScript(
                    if (ayahs != null && texts != null && ayahs.size == texts.size && ayahs.isNotEmpty()) {
                        SpeechScript(
                            generation = args.getLong(ARG_SPEECH_GENERATION),
                            engine = args.getString(ARG_SPEECH_ENGINE).orEmpty(),
                            voiceId = args.getString(ARG_SPEECH_VOICE).orEmpty(),
                            language = args.getString(ARG_SPEECH_LANGUAGE).orEmpty(),
                            texts = ayahs.toList().zip(texts.toList()).toMap(),
                        )
                    } else {
                        null
                    },
                )
                // The notification's two ayah buttons (spec §15.1), beside the system's surah
                // previous/next: what the bar does with a long press, the lock screen does with
                // these. Localised by the app, since the service has no string of its own.
                offerAyahButtons(
                    previous = args.getString(ARG_PREVIOUS_AYAH).orEmpty(),
                    next = args.getString(ARG_NEXT_AYAH).orEmpty(),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == COMMAND_PREVIOUS_AYAH) {
                ayahPlayer?.previousAyah()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == COMMAND_NEXT_AYAH) {
                ayahPlayer?.nextAyah()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        private fun offerAyahButtons(previous: String, next: String) {
            val current = session ?: return
            if (previous.isEmpty() && next.isEmpty()) return
            current.setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                        .setSessionCommand(SessionCommand(COMMAND_PREVIOUS_AYAH, Bundle.EMPTY))
                        .setDisplayName(previous)
                        .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                        .setSessionCommand(SessionCommand(COMMAND_NEXT_AYAH, Bundle.EMPTY))
                        .setDisplayName(next)
                        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                        .build(),
                ),
            )
        }

        /**
         * Puts back the URI the binder stripped, and gives every item the same title, subtitle and
         * artwork — the gap and translation items included, so the notification does not blink
         * between ayahs.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val text = nowPlaying
            val artwork = AppIconArtwork.uri(this@RecitationService)
            val resolved = mediaItems.mapTo(ArrayList(mediaItems.size)) { item ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(text?.title)
                    .setArtist(text?.subtitle)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .apply {
                        if (artwork != null) setArtworkUri(artwork)
                    }
                    .build()
                item.buildUpon()
                    .setUri(item.mediaId)
                    .setMediaMetadata(metadata)
                    .build()
            }
            return Futures.immediateFuture(resolved)
        }
    }

    companion object {
        /** Carries [NowPlayingText] to the session before the queue is set. */
        const val COMMAND_NOW_PLAYING = "world.taqwa.app.recitation.NOW_PLAYING"
        const val ARG_TITLE = "title"
        const val ARG_SUBTITLE = "subtitle"

        /** A `LongArray` of every queue item's length in ms: [SurahTimeline.itemsMs]. */
        const val ARG_TIMELINE = "timeline"

        /** The notification's two ayah buttons' labels, in the app's language. */
        const val ARG_PREVIOUS_AYAH = "previousAyah"
        const val ARG_NEXT_AYAH = "nextAyah"

        /** Session to controllers: a lock screen, headset or car pressed previous or next. */
        const val COMMAND_SURAH_PREVIOUS = "world.taqwa.app.recitation.SURAH_PREVIOUS"
        const val COMMAND_SURAH_NEXT = "world.taqwa.app.recitation.SURAH_NEXT"

        /** Controllers to session: the notification's two ayah buttons. */
        const val COMMAND_PREVIOUS_AYAH = "world.taqwa.app.recitation.PREVIOUS_AYAH"
        const val COMMAND_NEXT_AYAH = "world.taqwa.app.recitation.NEXT_AYAH"

        /** Read-aloud's script (spec §5.5), sent with [COMMAND_NOW_PLAYING]. */
        const val ARG_SPEECH_GENERATION = "speechGeneration"
        const val ARG_SPEECH_ENGINE = "speechEngine"
        const val ARG_SPEECH_VOICE = "speechVoice"
        const val ARG_SPEECH_LANGUAGE = "speechLanguage"
        const val ARG_SPEECH_AYAHS = "speechAyahs"
        const val ARG_SPEECH_TEXTS = "speechTexts"

        /** Session extras: the real current item's kind, for the app's bar. */
        const val EXTRA_PHASE = "phase"
        const val PHASE_AYAH = 0
        const val PHASE_SILENCE = 1
        const val PHASE_SPEECH = 2

        private const val REQUEST_OPEN_PLAYING = 31
    }
}

/**
 * Ayahs come out of the `.taqa` by byte range ([TaqaDataSource]); the reciter's inter-ayah gap,
 * and read-aloud's breath before a translation, are a [SilenceMediaSource] of their own length;
 * a translation is a WAV the [Speaker] makes as the queue reads ahead ([SpeechDataSource]). Three
 * sources rather than one because there is no MP3 of silence or of speech in the container to
 * point at, and an item of its own for each is what keeps the queue index and
 * `RecitationQueue`'s arithmetic the same list.
 */
@UnstableApi
private class RecitationSourceFactory(
    dataSourceFactory: TaqaDataSource.Factory,
    speaker: Speaker,
) : MediaSource.Factory {

    private val audio = DefaultMediaSourceFactory(dataSourceFactory)

    /** Read-aloud's WAVs (spec §5.5), made on the loading thread as the queue reads ahead. */
    private val speech = ProgressiveMediaSource.Factory(SpeechDataSource.Factory(speaker))

    override fun getSupportedTypes(): IntArray = audio.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        audio.setDrmSessionManagerProvider(drmSessionManagerProvider)
        speech.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        audio.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        speech.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri?.scheme == SILENCE_SCHEME) {
            val millis = uri.authority?.toLongOrNull() ?: 0L
            // The factory's own createMediaSource() hands back a source whose MediaItem is
            // Media3's placeholder — no title, no artist, no artwork — which would blank the
            // notification for the length of every gap. The source will take ours instead.
            return SilenceMediaSource(millis * 1_000L).apply { updateMediaItem(mediaItem) }
        }
        if (uri?.scheme == SPEECH_SCHEME) return speech.createMediaSource(mediaItem)
        return audio.createMediaSource(mediaItem)
    }
}
