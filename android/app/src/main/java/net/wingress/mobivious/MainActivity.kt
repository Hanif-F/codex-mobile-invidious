package net.wingress.mobivious

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.wingress.mobivious.data.ContentLinks
import net.wingress.mobivious.player.PlaybackService
import net.wingress.mobivious.ui.AppViewModel
import net.wingress.mobivious.ui.MobiviousApp

class MainActivity : ComponentActivity() {
    val model: AppViewModel by viewModels()
    private val pipMode = mutableStateOf(false)
    private var pipWatching = false
    private var pipSource: Rect? = null
    private var pipUpdatePending = false
    val sharedVideo = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MobiviousApp(model, this, pipMode.value, sharedVideo) }
        // Recreation reconnects to the service session instead of replaying its launch link.
        if (!model.launchIntentConsumed) {
            model.launchIntentConsumed = true
            if (savedInstanceState == null || model.queue.value.token.isEmpty()) handleLink(intent)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleLink(intent) }
    override fun onStart() { super.onStart(); model.refreshSharedSettings() }
    private fun handleLink(intent: Intent) {
        if (intent.getBooleanExtra("openDownloads", false)) { intent.removeExtra("openDownloads"); model.navigate("You", "downloads", rememberOrigin = true); return }
        val text = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent.dataString
        if (text != null) {
            val link = ContentLinks.parse(text, model.store.server)
            if (link != null) sharedVideo.value = model.openContent(link)
            else model.message.value = "Share a YouTube or configured Invidious video, clip, playlist, mix, channel, hashtag or post link."
        }
    }
    fun updatePip(watching: Boolean) {
        pipWatching = watching
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val active = watching && model.playback.value.details != null && model.playback.value.error == null && model.store.pip
        val playing = model.controller.value?.playWhenReady == true
        fun action(name: String, icon: Int, command: String, code: Int) = RemoteAction(Icon.createWithResource(this, icon), name, name,
            PendingIntent.getService(this, code, Intent(this, PlaybackService::class.java).setAction(command), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        val params = PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio())
            .setActions(listOf(action("Back 10 seconds", android.R.drawable.ic_media_rew, "mobivious.rewind", 1),
                action(if (playing) "Pause" else "Play", if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "mobivious.toggle", 2),
                action("Forward 10 seconds", android.R.drawable.ic_media_ff, "mobivious.forward", 3)))
        pipSource?.let { params.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= 31) params.setAutoEnterEnabled(active && playing).setSeamlessResizeEnabled(true)
        setPictureInPictureParams(params.build())
    }
    fun updatePipSource(bounds: Rect) {
        if (bounds.isEmpty || bounds == pipSource || isInPictureInPictureMode) return
        pipSource = Rect(bounds)
        // Window parameter updates must run after Compose's current layout pass.
        if (!pipUpdatePending) {
            pipUpdatePending = true
            window.decorView.post {
                pipUpdatePending = false
                if (!isDestroyed && !isInPictureInPictureMode) updatePip(pipWatching)
            }
        }
    }
    fun supportsPip() = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    fun setFullscreen(fullscreen: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun pipAspectRatio(): Rational {
        // Preserve the exact Android boundaries; rounding below 1 / 2.39 is rejected.
        val (numerator, denominator) = model.playback.value.geometry.pipFraction
        return Rational(numerator, denominator)
    }
    fun enterPip() { model.cancelAccumulatedSeek(); if (model.playback.value.details != null && supportsPip()) { updatePip(pipWatching); enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio()).build()) } }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        model.cancelAccumulatedSeek()
        updatePip(pipWatching)
        if (Build.VERSION.SDK_INT < 31 && pipWatching && model.store.pip && model.playback.value.playing) enterPip()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) { super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); pipMode.value = isInPictureInPictureMode }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.cancelAccumulatedSeek(isInPictureInPictureMode || model.store.background)
        if (!isChangingConfigurations && !isInPictureInPictureMode && !model.store.background) model.controller.value?.pause()
    }
}
