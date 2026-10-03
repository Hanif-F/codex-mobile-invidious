package net.wingress.mobivious.player

import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.data.Account

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object { const val SET_DISPLAY_TITLE = "mobivious.dearrow.title" }
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Mutex()
    private val app get() = application as MobiviousApplication
    private var owner: Account? = null
    private var history = false
    private var savePosition = false
    private var started = false
    private var marked = false
    private var current: MediaItem? = null
    override fun onCreate() {
        super.onCreate()
        // This data source has no Authorization headers or account cookies.
        val mediaHttp = DefaultHttpDataSource.Factory().setUserAgent("Mobivious/0.1").setAllowCrossProtocolRedirects(false)
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(mediaHttp)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
        }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    if (controller.isTrusted || controller.packageName == packageName) MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(SET_DISPLAY_TITLE, Bundle.EMPTY)).build()).build()
                    else MediaSession.ConnectionResult.reject()
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (controller.packageName != packageName || customCommand.customAction != SET_DISPLAY_TITLE) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                    val item = player.currentMediaItem
                    val title = args.getString("title")
                    if (item != null && item.mediaId == args.getString("mediaId") && !title.isNullOrBlank()) {
                        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean("dearrowMetadataOnly", true) }
                        player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(title).setExtras(extras).build()).build())
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> =
                    Futures.immediateFuture(mediaItems.filter { it.localConfiguration?.uri?.scheme in listOf("https", "http") })
            }).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val metadataOnly = current?.mediaId == mediaItem?.mediaId && mediaItem?.mediaMetadata?.extras?.getBoolean("dearrowMetadataOnly") == true && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                current = mediaItem
                if (metadataOnly) return
                owner = app.store.account.value
                history = mediaItem?.mediaMetadata?.extras?.getBoolean("history") ?: false
                savePosition = mediaItem?.mediaMetadata?.extras?.getBoolean("savePosition") ?: false
                started = false
                marked = false
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    started = true
                    if (!marked && history) {
                        marked = true
                        val item = current; val account = owner
                        scope.launch { if (item != null && sameOwner(account)) runCatching { app.api.watched(item.mediaId) } }
                    }
                } else persist()
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) persist(ended = true) }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_REMOVE) persist(item = oldPosition.mediaItem, position = oldPosition.positionMs)
                else if (reason == Player.DISCONTINUITY_REASON_SEEK) persist()
            }
        })
        scope.launch { while (isActive) { delay(15_000); if (player.isPlaying) persist() } }
    }
    private fun sameOwner(account: Account?): Boolean = account != null && account == app.store.account.value && account.server == app.store.server
    private fun persist(ended: Boolean = false, item: MediaItem? = current, position: Long = player.currentPosition) {
        if (!started || !savePosition || item == null) return
        val duration = player.duration.takeIf { it > 0 }?.div(1000) ?: 0
        val value = PlaybackRules.save(position / 1000, duration, ended)
        app.store.position(item.mediaId, value)
        val account = owner
        scope.launch { writes.withLock { if (sameOwner(account)) runCatching { app.api.position(item.mediaId, value) } } }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "mobivious.toggle" -> if (player.playWhenReady) player.pause() else player.play()
            "mobivious.rewind" -> player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
            "mobivious.forward" -> player.seekTo(player.currentPosition + 10_000)
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady || !app.store.background) { player.pause(); stopSelf() } }
    override fun onDestroy() { scope.cancel(); session.release(); player.release(); super.onDestroy() }
}
