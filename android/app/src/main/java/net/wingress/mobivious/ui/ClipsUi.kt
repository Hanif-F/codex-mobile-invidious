package net.wingress.mobivious.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import coil.transform.Transformation
import kotlinx.coroutines.delay
import net.wingress.mobivious.data.*
import net.wingress.mobivious.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToLong

internal fun shareClip(context: Context, clip: Clip, failure: () -> Unit) {
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, clip.permalink), "Share clip")) }.onFailure { failure() }
}
internal fun copyClip(context: Context, clip: Clip, vm: AppViewModel) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Clip", clip.permalink))
    vm.message.value = "Clip link copied"
}
private fun clippedDate(clip: Clip): String = clip.createdAt?.let {
    "Clipped " + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()))
}.orEmpty()

@Composable internal fun ClipCard(vm: AppViewModel, clip: Clip, play: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val warning = vm.aiDecision(clip.video, AiPageGroup.OTHER, aiState).warning
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open clip", onClick = play)
        .padding(horizontal = 16.dp, vertical = 9.dp).testTag("clip-card-${clip.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!prefs.thinMode || warning != null) Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)) {
            if (warning != null) AiThumbnail(warning, Modifier.fillMaxSize(), compact = true)
            else AsyncImage(resolved(clip.server, clip.video.thumbnail), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Row(Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .82f))
                .padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCut, null, Modifier.size(13.dp), Color.White)
                Text(playerTime(clip.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(clip.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("from ${clip.video.author.ifBlank { "Source video" }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(clip.creator.takeIf(String::isNotBlank), clippedDate(clip).takeIf(String::isNotBlank),
                playerTime(clip.durationMs).takeIf { prefs.thinMode }).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        OverflowMenu("Actions for clip ${clip.title}", clip.id to account, Modifier.size(40.dp).testTag("clip-actions-${clip.id}")) { close ->
            DropdownMenuItem(text = { Text("Share clip") }, onClick = { close(); shareClip(context, clip) { vm.message.value = "No app could share this clip." } })
            DropdownMenuItem(text = { Text("Copy link") }, onClick = { close(); copyClip(context, clip, vm) })
            if (clip.owned(vm.api.context())) DropdownMenuItem(text = { Text("Delete clip") }, onClick = { close(); vm.confirmDeleteClip(clip) })
        }
    }
}

@Composable internal fun ClipDetails(vm: AppViewModel, clip: Clip, close: () -> Unit, channel: (String) -> Unit) {
    val context = LocalContext.current
    val queue by vm.queue.collectAsStateWithLifecycle()
    val savedPrefs by vm.preferences.collectAsStateWithLifecycle()
    val prefs = queue.effective(savedPrefs)
    val details = queue.details?.takeIf { it.video.id == clip.video.id }
    val source = details?.video ?: clip.video
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("clip-details"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Icon(Icons.Default.ContentCut, null, Modifier.padding(9.dp).size(20.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Clip", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (clip.owned(vm.api.context())) IconButton(onClick = { vm.confirmDeleteClip(clip) }, Modifier.testTag("clip-delete")) { Icon(Icons.Default.DeleteOutline, "Delete clip") }
            IconButton(onClick = close) { Icon(Icons.Default.Close, "Close clip") }
        }
        if (clip.creator.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(clip.creator.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
            }
            Text(clip.creator, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("Public", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(clip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(listOf(clippedDate(clip), "${ClipRules.timestamp(clip.startMs)} – ${ClipRules.timestamp(clip.endMs)}").filter(String::isNotBlank).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = { shareClip(context, clip) { vm.message.value = "No app could share this clip." } }, modifier = Modifier.testTag("clip-share")) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share clip")
            }
            OutlinedButton(onClick = { copyClip(context, clip, vm) }) { Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy link") }
            FilterChip(selected = queue.repeat == QueueRepeat.ONE, onClick = { vm.repeatQueue(if (queue.repeat == QueueRepeat.ONE) QueueRepeat.OFF else QueueRepeat.ONE) },
                label = { Text("Loop") }, leadingIcon = { Icon(Icons.Default.Repeat, null, Modifier.size(18.dp)) }, modifier = Modifier.testTag("clip-loop"))
        }
        HorizontalDivider()
        Text("Watch full video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(onClick = vm::watchFullClip, modifier = Modifier.fillMaxWidth().testTag("clip-full-video"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                    AsyncImage(resolved(clip.server, source.thumbnail), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Text(time(source.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = .8f)).padding(3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(source.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(source.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Continue from this moment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (ContentVisibility.validChannel(source.channelId)) CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            ChannelAuthor(clip.server, source.authorAvatar.ifBlank { clip.video.authorAvatar.takeIf { source.channelId == clip.video.channelId }.orEmpty() },
                source.author.ifBlank { "View channel" }, Avatars.show(prefs.thinMode, source.channelId), size = 32.dp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp).testTag("clip-channel"),
                tag = "clip-channel-avatar", onClick = { channel(source.channelId) })
        }
    }
}

@Composable internal fun ClipDialogs(vm: AppViewModel) {
    val resolution by vm.clipResolution.collectAsStateWithLifecycle()
    val deletion by vm.clipDelete.collectAsStateWithLifecycle()
    val busy by vm.clipDeleteBusy.collectAsStateWithLifecycle()
    val error by vm.clipDeleteError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (resolution.link != null) AlertDialog(onDismissRequest = vm::dismissClipResolution, icon = { Icon(Icons.Default.ContentCut, null) },
        title = { Text(if (resolution.foreign) "Clip from another instance" else if (resolution.loading) "Opening clip" else "Clip unavailable") },
        text = { if (resolution.loading) CircularProgressIndicator() else Text(if (resolution.foreign) "This clip belongs to ${resolution.link!!.server}. Open it there to watch it." else resolution.error.orEmpty()) },
        confirmButton = { if (!resolution.loading) TextButton(onClick = {
            if (resolution.foreign) { val url = resolution.link!!.url; vm.dismissClipResolution(); openExternalContent(context, url) { vm.message.value = "No app could open this clip." } }
            else vm.openClipLink(resolution.link!!)
        }) { Text(if (resolution.foreign) "Open in browser" else "Retry") } },
        dismissButton = { TextButton(onClick = vm::dismissClipResolution) { Text("Close") } })
    deletion?.let { clip -> AlertDialog(onDismissRequest = { if (!busy) vm.clipDelete.value = null },
        title = { Text("Delete clip?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("“${clip.title}” will be removed from My Clips and its channel. Its shared link will stop working.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = vm::deleteClip, enabled = !busy, modifier = Modifier.testTag("clip-delete-confirm")) { Text(if (busy) "Deleting…" else "Delete") } },
        dismissButton = { TextButton(onClick = { vm.clipDelete.value = null }, enabled = !busy) { Text("Cancel") } }) }
}

@Composable internal fun ClipEditor(vm: AppViewModel) {
    val editor by vm.clipEditor.collectAsStateWithLifecycle()
    if (!editor.open || editor.context != vm.api.context()) return
    val context = LocalContext.current
    BackHandler { if (!editor.busy) vm.closeClipEditor() }
    Dialog(onDismissRequest = { if (!editor.busy) vm.closeClipEditor() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
            Surface(Modifier.then(if (phone) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxHeight(.95f)),
                shape = if (phone) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.testTag("clip-editor")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, null, Modifier.padding(8.dp), MaterialTheme.colorScheme.primary)
                        Text(if (editor.published == null) "Create clip" else "Your clip is ready", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = vm::closeClipEditor, enabled = !editor.busy) { Icon(Icons.Default.Close, "Close clip editor") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClipPreview(vm, editor)
                        if (editor.published == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text(editor.context?.account?.username.orEmpty().take(1).uppercase()) }
                                }
                                Text(editor.context?.account?.username.orEmpty(), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Default.Public, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Public", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(editor.title, vm::clipTitle, label = { Text("Clip title") }, placeholder = { Text("Give this moment a name") },
                                modifier = Modifier.fillMaxWidth().testTag("clip-title"), enabled = !editor.busy, maxLines = 3,
                                isError = ClipRules.titleCount(editor.title.trim()) > 140,
                                supportingText = { Row(Modifier.fillMaxWidth()) { Text("Required"); Spacer(Modifier.weight(1f)); Text("${ClipRules.titleCount(editor.title)}/140") } })
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(editor.startText, { vm.clipTimeText(it, true) }, label = { Text("Start") }, singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("clip-start"), enabled = !editor.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), isError = !editor.validRange)
                                OutlinedTextField(editor.endText, { vm.clipTimeText(it, false) }, label = { Text("End") }, singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("clip-end"), enabled = !editor.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), isError = !editor.validRange)
                            }
                            ClipFilmstrip(editor, vm::clipTimes)
                            Text("Clips are public and can be 5–120 seconds long. The source video stays unchanged.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("clip-publish-error")) }
                        } else {
                            val clip = editor.published!!
                            Text(clip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Saved to My Clips and ${clip.video.author}’s Clips tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(clip.permalink, style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { shareClip(context, clip) { vm.message.value = "No app could share this clip." } }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
                                OutlinedButton(onClick = { copyClip(context, clip, vm) }) { Text("Copy link") }
                                OutlinedButton(onClick = { vm.watchClip(clip) }, modifier = Modifier.testTag("clip-watch-published")) { Text("Watch clip") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    HorizontalDivider()
                    FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = vm::closeClipEditor, enabled = !editor.busy) { Text(if (editor.published == null) "Cancel" else "Done") }
                        if (editor.published == null) {
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = vm::publishClip, enabled = !editor.busy && editor.validation == null, modifier = Modifier.testTag("clip-publish")) {
                                if (editor.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                                Text(if (editor.busy) "Publishing…" else "Publish clip")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun ClipPreview(vm: AppViewModel, editor: ClipEditorState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val player = remember(editor.occurrence) { ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory()
        .setUserAgent("Mobivious/0.6").setAllowCrossProtocolRedirects(false))).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_ONE
            setPlaybackSpeed(vm.playback.value.speed)
        } }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Preview could not play. Check the connection and retry." }
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, editor.startMs, editor.endMs, editor.validRange) {
        player.pause(); error = null
        if (!editor.validRange) return@LaunchedEffect
        delay(120)
        val details = editor.details ?: return@LaunchedEffect
        val server = editor.context?.server ?: return@LaunchedEffect
        val local = vm.queue.value.effective(vm.preferences.value).local
        val raw = details.hls.ifBlank { details.dash }.ifBlank { details.fallback }.ifBlank { "/api/manifest/dash/id/${details.video.id}?local=$local" }
        val uri = resolved(server, raw)
        val item = MediaItem.Builder().setUri(uri)
            .setMimeType(if (details.hls.isNotBlank()) MimeTypes.APPLICATION_M3U8 else if (details.dash.isNotBlank() || details.fallback.isBlank()) MimeTypes.APPLICATION_MPD else MimeTypes.VIDEO_MP4)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(editor.startMs).setEndPositionMs(editor.endMs).build())
            .setSubtitleConfigurations(details.captions.map { track -> MediaItem.SubtitleConfiguration.Builder(resolved(server, track.url).toUri())
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(track.language).setLabel(track.label).build() }).build()
        player.setMediaItem(item); player.prepare()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            AndroidView(factory = { (LayoutInflater.from(it).inflate(R.layout.clip_preview, null) as PlayerView).apply {
                this.player = player; setShowNextButton(false); setShowPreviousButton(false)
            } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(minOf(maxWidth * 9 / 16, 210.dp))
                .clip(RoundedCornerShape(12.dp)).testTag("clip-preview"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Preview selected range", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = editor.validRange && !editor.busy, onClick = {
                focus.clearFocus(); keyboard?.hide()
                if (error != null) { error = null; player.prepare() }
                if (playing) player.pause() else { player.seekTo(0); player.play() }
            }, modifier = Modifier.testTag("clip-preview-toggle")) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Text(if (playing) "Pause" else "Preview")
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

private class StoryboardCrop(private val frame: StoryboardFrame) : Transformation {
    override val cacheKey = "clip-crop:${frame.x}:${frame.y}:${frame.width}:${frame.height}"
    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        if (frame.x < 0 || frame.y < 0 || frame.x + frame.width > input.width || frame.y + frame.height > input.height) return input
        return Bitmap.createBitmap(input, frame.x, frame.y, frame.width, frame.height)
    }
}

@Composable private fun ClipFilmstrip(editor: ClipEditorState, change: (Long, Long) -> Unit) {
    val context = LocalContext.current
    val duration = (editor.details?.video?.duration ?: 0) * 1000
    if (duration < 5_000) return
    val window = minOf(180_000L, duration)
    var viewport by rememberSaveable(editor.occurrence) { mutableLongStateOf((editor.startMs - 30_000).coerceIn(0, duration - window)) }
    val current by rememberUpdatedState(editor)
    val currentViewport by rememberUpdatedState(viewport)
    LaunchedEffect(editor.startMs, editor.endMs) {
        if (editor.validRange && (editor.startMs < viewport || editor.endMs > viewport + window))
            viewport = ((editor.startMs + editor.endMs - window) / 2).coerceIn(0, duration - window)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ClipRules.timestamp(viewport), style = MaterialTheme.typography.labelSmall)
            Text(ClipRules.timestamp(viewport + window / 2), style = MaterialTheme.typography.labelSmall)
            Text(ClipRules.timestamp(viewport + window), style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).testTag("clip-filmstrip")) {
            val primary = MaterialTheme.colorScheme.primary
            val startFraction = ((editor.startMs - viewport).toFloat() / window).coerceIn(0f, 1f)
            val endFraction = ((editor.endMs - viewport).toFloat() / window).coerceIn(0f, 1f)
            Row(Modifier.fillMaxSize()) { repeat(6) { n ->
                val time = viewport + window * (2 * n + 1) / 12
                val frame = editor.frames.firstOrNull { time in it.startMs until it.endMs }
                val request = frame?.let { ImageRequest.Builder(context).data(it.url).size(CoilSize.ORIGINAL).transformations(StoryboardCrop(it)).build() }
                AsyncImage(request ?: resolved(editor.context?.server.orEmpty(), editor.details?.video?.thumbnail.orEmpty()), null,
                    Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            } }
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = "Clip range"
                stateDescription = "${editor.startText} to ${editor.endText}"
                customActions = listOf(
                    CustomAccessibilityAction("Start 0.1 seconds earlier") { if (!editor.busy && editor.validRange) { change((editor.startMs - 100).coerceAtLeast(maxOf(0, editor.endMs - 120_000)), editor.endMs); true } else false },
                    CustomAccessibilityAction("Start 0.1 seconds later") { if (!editor.busy && editor.validRange) { change((editor.startMs + 100).coerceAtMost(editor.endMs - 5_000), editor.endMs); true } else false },
                    CustomAccessibilityAction("End 0.1 seconds earlier") { if (!editor.busy && editor.validRange) { change(editor.startMs, (editor.endMs - 100).coerceAtLeast(editor.startMs + 5_000)); true } else false },
                    CustomAccessibilityAction("End 0.1 seconds later") { if (!editor.busy && editor.validRange) { change(editor.startMs, (editor.endMs + 100).coerceAtMost(minOf(duration, editor.startMs + 120_000))); true } else false })
            }.pointerInput(editor.occurrence, window) {
                var mode = 0; var delta = 0f; var start = 0L; var end = 0L; var origin = 0L
                detectDragGestures(onDragStart = { p ->
                    start = current.startMs; end = current.endMs; origin = currentViewport; delta = 0f
                    val s = size.width * (start - origin).toFloat() / window
                    val e = size.width * (end - origin).toFloat() / window
                    val hit = 24.dp.toPx()
                    mode = if (abs(p.x - s) <= hit && abs(p.x - s) <= abs(p.x - e)) 1 else if (abs(p.x - e) <= hit) 2 else if (p.x in s..e) 3 else 4
                }, onDrag = { event, amount ->
                    event.consume()
                    if (!current.busy && current.validRange) {
                        delta += amount.x
                        val shift = ((delta / size.width * window / 100).roundToLong()) * 100
                        when (mode) {
                            1 -> change((start + shift).coerceIn(maxOf(0, end - 120_000), end - 5_000), end)
                            2 -> change(start, (end + shift).coerceIn(start + 5_000, minOf(duration, start + 120_000)))
                            3 -> { val s = (start + shift).coerceIn(0, duration - (end - start)); change(s, s + end - start) }
                            4 -> viewport = (origin - shift).coerceIn(0, duration - window)
                        }
                    }
                })
            }) {
                val left = size.width * startFraction; val right = size.width * endFraction
                drawRect(Color.Black.copy(alpha = .58f), size = Size(left, size.height))
                drawRect(Color.Black.copy(alpha = .58f), topLeft = Offset(right, 0f), size = Size(size.width - right, size.height))
                drawRect(primary, topLeft = Offset(left, 2.dp.toPx()), size = Size((right - left).coerceAtLeast(0f), size.height - 4.dp.toPx()), style = Stroke(3.dp.toPx()))
                for (x in listOf(left, right)) {
                    drawLine(primary, Offset(x, 0f), Offset(x, size.height), 9.dp.toPx())
                    drawLine(Color.White, Offset(x, size.height * .32f), Offset(x, size.height * .68f), 2.dp.toPx())
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = !editor.busy && editor.validRange, onClick = { change((editor.startMs - 100).coerceAtLeast(maxOf(0, editor.endMs - 120_000)), editor.endMs) },
                modifier = Modifier.testTag("clip-start-earlier")) { Text("Start −0.1s") }
            Text(String.format(java.util.Locale.ROOT, "%.1f seconds", (editor.endMs - editor.startMs) / 1000.0), style = MaterialTheme.typography.labelLarge)
            TextButton(enabled = !editor.busy && editor.validRange, onClick = { change(editor.startMs, (editor.endMs + 100).coerceAtMost(minOf(duration, editor.startMs + 120_000))) },
                modifier = Modifier.testTag("clip-end-later")) { Text("End +0.1s") }
        }
        if (duration > window) {
            Text("Browse the video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = viewport.toFloat(), onValueChange = { viewport = it.toLong() }, valueRange = 0f..(duration - window).toFloat(), enabled = !editor.busy,
                modifier = Modifier.semantics { contentDescription = "Filmstrip position in source video" })
        }
    }
}
