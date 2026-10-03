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
import net.wingress.mobivious.data.*
import android.os.SystemClock
import java.util.UUID
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        const val SET_DISPLAY_TITLE = "mobivious.dearrow.title"
        const val SET_HISTORY_SETTINGS = "mobivious.history.settings"
        const val SPONSOR_STATE = "mobivious.sponsorblock.state"
        const val SPONSOR_CONFIGURE = "mobivious.sponsorblock.configure"
        const val SPONSOR_SKIP = "mobivious.sponsorblock.skip"
        const val SPONSOR_DISMISS = "mobivious.sponsorblock.dismiss"
    }
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Mutex()
    private val app get() = application as MobiviousApplication
    private var owner: Account? = null
    private var ownerContext: ApiContext? = null
    private var history = false
    private var savePosition = false
    private var started = false
    private var marked = false
    private var current: MediaItem? = null
    private val sponsor = SponsorBlockEngine()
    private var sponsorState = SponsorBlockPlayback()
    private var sponsorContext: ApiContext? = null
    private var sponsorToken = ""
    private var segmentJob: Job? = null
    private var segmentsRequested = false
    private var sponsorSeeking = false
    private var sponsorNotice = ""
    private var noticeUntil = 0L
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
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().apply {
                            listOf(SET_DISPLAY_TITLE, SET_HISTORY_SETTINGS, SPONSOR_STATE, SPONSOR_CONFIGURE, SPONSOR_SKIP, SPONSOR_DISMISS).forEach { add(SessionCommand(it, Bundle.EMPTY)) }
                        }.build()).build()
                    else MediaSession.ConnectionResult.reject()
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                    if (customCommand.customAction == SET_HISTORY_SETTINGS) {
                        if (args.getString("mediaId") != player.currentMediaItem?.mediaId || ownerContext != app.api.context())
                            return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                        history = args.getBoolean("history") && owner != null
                        savePosition = args.getBoolean("savePosition")
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (customCommand.customAction != SET_DISPLAY_TITLE) return Futures.immediateFuture(sponsorCommand(customCommand.customAction, args))
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
                ownerContext = app.api.context()
                history = mediaItem?.mediaMetadata?.extras?.getBoolean("history") ?: false
                savePosition = mediaItem?.mediaMetadata?.extras?.getBoolean("savePosition") ?: false
                started = false
                marked = false
                resetSponsor(mediaItem)
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
            override fun onEvents(player: Player, events: Player.Events) { evaluateSponsor() }
        })
        scope.launch { while (isActive) { delay(15_000); if (player.isPlaying) persist() } }
        scope.launch { while (isActive) { delay(100); if (player.isPlaying || sponsorNotice.isNotEmpty()) evaluateSponsor() } }
        scope.launch { app.store.account.collect { if (sponsorContext != null && sponsorContext != app.api.context()) resetSponsor(player.currentMediaItem, readSettings = false) } }
    }
    private fun resetSponsor(item: MediaItem?, readSettings: Boolean = true) {
        segmentJob?.cancel(); segmentJob = null; segmentsRequested = false
        sponsor.reset(); sponsorNotice = ""; noticeUntil = 0
        sponsorContext = if (item == null) null else app.api.context()
        sponsorToken = if (item == null) "" else UUID.randomUUID().toString()
        val raw = if (readSettings) item?.mediaMetadata?.extras?.getString("sponsorblock") else null
        sponsor.settings = runCatching { SponsorBlockSettings.parse(JSONObject(raw ?: "{}")) }.getOrDefault(SponsorBlockSettings())
        publishSponsor(SponsorBlockPlayback(item?.mediaId.orEmpty(), sponsorToken, sponsor.settings))
        evaluateSponsor()
    }
    private fun publishSponsor(value: SponsorBlockPlayback) {
        if (value != sponsorState) {
            sponsorState = value
            session.broadcastCustomCommand(SessionCommand(SPONSOR_STATE, Bundle.EMPTY), Bundle().apply { putString("state", value.json().toString()) })
        }
    }
    private fun evaluateSponsor() {
        if (sponsorSeeking || sponsorContext == null) return
        if (sponsorContext != app.api.context()) { resetSponsor(player.currentMediaItem, readSettings = false); return }
        val item = player.currentMediaItem ?: return
        sponsor.observePosition(player.currentPosition)
        val usable = sponsor.settings.usable && item.mediaMetadata.extras?.getBoolean("liveNow") != true &&
            !player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (usable && !segmentsRequested && SponsorBlockRules.validVideo(item.mediaId)) {
            segmentsRequested = true
            val context = sponsorContext!!; val token = sponsorToken
            segmentJob = scope.launch {
                try {
                    val segments = app.api.sponsorBlock(item.mediaId, context)
                    if (token == sponsorToken && context == app.api.context()) { sponsor.segments = segments; evaluateSponsor() }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Playback remains usable without segment data. */ }
            }
        }
        if (!usable || player.duration <= 0 || player.playerError != null) {
            sponsorNotice = ""
            publishSponsor(SponsorBlockPlayback(item.mediaId, sponsorToken, sponsor.settings)); return
        }
        var decision = sponsor.evaluate(player.currentPosition, player.duration)
        decision.seek?.let { position ->
            sponsorSeeking = true
            try { player.seekTo(position) } finally { sponsorSeeking = false }
            sponsorNotice = "Skipped ${decision.categories.joinToString(", ") { it.label }}"
            noticeUntil = SystemClock.elapsedRealtime() + 3000
            decision = sponsor.evaluate(player.currentPosition, player.duration)
        }
        if (SystemClock.elapsedRealtime() >= noticeUntil) sponsorNotice = ""
        val remaining = decision.active?.let { ((minOf(it.end, player.duration) - player.currentPosition).coerceAtLeast(0) + 999) / 1000 } ?: 0
        publishSponsor(SponsorBlockPlayback(item.mediaId, sponsorToken, sponsor.settings, decision.visible, decision.active, remaining, sponsorNotice))
    }
    private fun sponsorCommand(action: String, args: Bundle): SessionResult {
        if (action == SPONSOR_STATE) return SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply { putString("state", sponsorState.json().toString()) })
        if (action !in listOf(SPONSOR_CONFIGURE, SPONSOR_SKIP, SPONSOR_DISMISS)) return SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        if (args.getString("token") != sponsorToken || sponsorToken.isEmpty() || args.getString("mediaId") != player.currentMediaItem?.mediaId || sponsorContext != app.api.context())
            return SessionResult(SessionError.ERROR_INVALID_STATE)
        when (action) {
            SPONSOR_CONFIGURE -> {
                val config = runCatching { SponsorBlockSettings.parse(JSONObject(args.getString("settings") ?: "{}")) }.getOrNull()
                    ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
                sponsor.settings = config.copy(channels = emptyMap())
            }
            SPONSOR_SKIP, SPONSOR_DISMISS -> {
                evaluateSponsor()
                val active = sponsorState.active?.takeIf { it.id == args.getString("segment") } ?: return SessionResult(SessionError.ERROR_INVALID_STATE)
                if (action == SPONSOR_DISMISS) sponsor.dismiss(active.id)
                else {
                    val end = minOf(active.end, player.duration)
                    if (end <= player.currentPosition) return SessionResult(SessionError.ERROR_INVALID_STATE)
                    player.seekTo(end)
                }
            }
        }
        evaluateSponsor()
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }
    private fun sameOwner(account: Account?): Boolean = account != null && account == app.store.account.value && account.server == app.store.server
    private fun persist(ended: Boolean = false, item: MediaItem? = current, position: Long = player.currentPosition) {
        if (!started || !savePosition || item == null || ownerContext != app.api.context()) return
        val duration = player.duration.takeIf { it > 0 }?.div(1000) ?: 0
        val value = PlaybackRules.save(position / 1000, duration, ended)
        app.store.position(item.mediaId, value)
        val account = owner
        scope.launch { writes.withLock { if (savePosition && sameOwner(account)) runCatching { app.api.position(item.mediaId, value) } } }
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
