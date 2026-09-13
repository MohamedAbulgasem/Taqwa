package world.taqwa.app.recitation

import android.content.Intent
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
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

    /**
     * The two lines the notification shows, set by the app just before it sets the queue.
     *
     * It travels as a custom command rather than on each `MediaItem` for a plain reason: a
     * controller's media items cross a binder, `MediaItem`s lose their URI on the way over
     * (Media3 strips it to keep transactions small), and Al-Baqarah is 571 items. Sending the
     * text once and building the metadata on this side keeps the largest surah's queue well under
     * the binder's 1 MB and puts the artwork — 30 KB, one array shared by every item — entirely
     * on this side of it.
     */
    private var nowPlaying: NowPlayingText? = null

    override fun onCreate() {
        super.onCreate()
        val paths = createRecitationPaths()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(RecitationSourceFactory(TaqaDataSource.Factory(paths)))
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
        // Wrapped, never the raw ExoPlayer: the queue underneath is `[ayah, gap, ayah, …]`, and
        // Media3's own Previous and Next step one item — which from the lock screen means stepping
        // onto a 300 ms silence instead of going back an ayah. [AyahPlayer] is the same
        // `RecitationQueue` rule the app's own bar uses, applied to everything outside the app.
        session = MediaSession.Builder(this, AyahPlayer(player)).setCallback(Callback()).build()
        // Media3's own default small icon is a generic music note. The status bar should say
        // Taqwa, and the mark the prayer notifications already use is the one it should say it
        // with; `:androidApp` puts the id there in `TaqwaApplication.onCreate`.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(notificationSmallIconResId)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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

    /** Idempotent: [onTaskRemoved] gets here first, and [onDestroy] follows it. */
    private fun releaseSession() {
        session?.run {
            player.release()
            release()
        }
        session = null
    }

    private inner class Callback : MediaSession.Callback {

        override fun onConnect(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_NOW_PLAYING, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                .setAvailableSessionCommands(commands)
                .build()
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
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        /**
         * Puts back the URI the binder stripped, and gives every item the same title, subtitle and
         * artwork — the gap items included, so the notification does not blink between ayahs.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val text = nowPlaying
            val artwork = AppIconArtwork.bytes(this@RecitationService)
            val resolved = mediaItems.mapTo(ArrayList(mediaItems.size)) { item ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(text?.title)
                    .setArtist(text?.subtitle)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .apply {
                        if (artwork != null) {
                            setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
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
    }
}

/**
 * Ayahs come out of the `.taqa` by byte range ([TaqaDataSource]); the reciter's inter-ayah gap is
 * a [SilenceMediaSource] of its own length. Two sources rather than one because there is no MP3
 * of silence in the container to point at, and a silent item is what keeps the queue index and
 * `RecitationQueue`'s arithmetic the same list.
 */
@UnstableApi
private class RecitationSourceFactory(
    dataSourceFactory: TaqaDataSource.Factory,
) : MediaSource.Factory {

    private val audio = DefaultMediaSourceFactory(dataSourceFactory)

    override fun getSupportedTypes(): IntArray = audio.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        audio.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        audio.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
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
        return audio.createMediaSource(mediaItem)
    }
}
