package net.wingress.mobivious.downloads

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.data.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadExportState(val id: String = "", val asset: String? = null, val destination: String = "", val phase: String = "", val progress: Int = 0, val error: String = "", val convertVideo: Boolean = true, val convertAudio: Boolean = true) {
    val busy get() = phase in listOf("Merging", "Converting", "Saving")
    fun allowsConversion(id: String, asset: String?, destination: String) = phase == "Needs conversion" && this.id == id && this.asset == asset && this.destination == destination
}

/** A user-started foreground export. All Transformer calls use the service's main thread. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadExportService : Service() {
    companion object {
        val state = MutableStateFlow(DownloadExportState())
        private var active: DownloadExportService? = null
        fun restore(context: Context) {
            val prefs = context.getSharedPreferences("download-export", Context.MODE_PRIVATE)
            if (prefs.getBoolean("pending", false)) {
                val request = DownloadExportState(prefs.getString("id", "").orEmpty(), prefs.getString("asset", null), prefs.getString("destination", "").orEmpty(), "Interrupted", error = "Export interrupted. Save to files again to retry.")
                state.value = request
                prefs.edit().putBoolean("pending", false).apply()
                CoroutineScope(Dispatchers.IO).launch {
                    File(context.cacheDir, "export-${request.id}.mp4").delete()
                    removeOutput(context, request.destination)
                }
            }
        }
        fun start(context: Context, id: String, asset: String?, destination: Uri, convert: Boolean = false) {
            if (state.value.busy) return
            val consent = state.value
            require(!convert || consent.allowsConversion(id, asset, destination.toString())) { "Conversion requires confirmation for this export." }
            ContextCompat.startForegroundService(context, Intent(context, DownloadExportService::class.java)
                .putExtra("id", id).putExtra("asset", asset).putExtra("destination", destination.toString()).putExtra("convert", convert)
                .putExtra("convertVideo", consent.convertVideo).putExtra("convertAudio", consent.convertAudio))
        }
        fun cancel(context: Context, id: String = state.value.id) {
            if (id != state.value.id || !state.value.busy && state.value.phase != "Needs conversion") return
            active?.job?.cancel()
            if (!state.value.busy) {
                state.value = state.value.copy(phase = "Cancelled")
                context.getSharedPreferences("download-export", Context.MODE_PRIVATE).edit().putBoolean("pending", false).apply()
                removeOutput(context, state.value.destination)
            }
        }
        suspend fun cancelAndWait(context: Context, id: String) {
            cancel(context, id)
            if (state.value.id == id) active?.job?.join()
        }
        private fun removeOutput(context: Context, destination: String) {
            if (!destination.startsWith("content://")) return
            val uri = Uri.parse(destination)
            if (runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)) return
            // Some providers cannot delete documents. Leave no partially valid media.
            runCatching { context.contentResolver.openOutputStream(uri, "wt")?.close() }
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var transformer: Transformer? = null
    private val app get() = application as MobiviousApplication
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); active = this; getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("download-export", "File exports", NotificationManager.IMPORTANCE_LOW)) }
    private fun publish(value: DownloadExportState) {
        state.value = value
        getSharedPreferences("download-export", MODE_PRIVATE).edit().putString("id", value.id).putString("asset", value.asset).putString("destination", value.destination).putBoolean("pending", value.busy || value.phase == "Needs conversion").apply()
        val open = PendingIntent.getActivity(this, 30, Intent(this, MainActivity::class.java).putExtra("openDownloads", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 31, Intent(this, DownloadExportService::class.java).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "download-export").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Export download")
            .setContentText(value.phase).setContentIntent(open).setOngoing(value.busy).setProgress(100, value.progress, value.progress == 0).addAction(0, "Cancel", cancel).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(41, notification, if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(41, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") { job?.cancel(); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        val id = intent?.getStringExtra("id").orEmpty()
        val destination = intent?.getStringExtra("destination").orEmpty()
        val asset = intent?.getStringExtra("asset")
        if (id.isEmpty() || !destination.startsWith("content://")) { stopSelf(); return START_NOT_STICKY }
        publish(DownloadExportState(id, asset, destination, "Saving", convertVideo = intent!!.getBooleanExtra("convertVideo", true), convertAudio = intent.getBooleanExtra("convertAudio", true)))
        job = scope.launch { export(intent.getBooleanExtra("convert", false)) }
        return START_NOT_STICKY
    }
    private data class Input(val file: File, val mime: String, val durationUs: Long, val width: Int = 0, val height: Int = 0, val fps: Int = 0)
    private fun inspect(file: File, type: String): Input {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith(type) == true } ?: error("The downloaded file has no $type track.")
            return Input(file, track.getString(MediaFormat.KEY_MIME)!!, if (track.containsKey(MediaFormat.KEY_DURATION)) track.getLong(MediaFormat.KEY_DURATION) else 0,
                if (track.containsKey(MediaFormat.KEY_WIDTH)) track.getInteger(MediaFormat.KEY_WIDTH) else 0,
                if (track.containsKey(MediaFormat.KEY_HEIGHT)) track.getInteger(MediaFormat.KEY_HEIGHT) else 0,
                if (track.containsKey(MediaFormat.KEY_FRAME_RATE)) track.getInteger(MediaFormat.KEY_FRAME_RATE) else 0)
        } finally { extractor.release() }
    }
    private suspend fun export(convert: Boolean) {
        val request = state.value
        val temporary = File(cacheDir, "export-${request.id}.mp4")
        try {
            val record = app.downloads.find(request.id) ?: error("The download is unavailable.")
            val selected = request.asset?.let { key -> record.assets.singleOrNull { it.choice.key == key && it.status == DownloadStatus.COMPLETE } ?: error("The selected file is unavailable.") }
            val media = record.assets.filter { it.media }
            var source: File
            if (selected != null) source = app.downloads.file(record, selected)
            else if (media.size == 1) { require(record.playable); source = app.downloads.file(record, media.single()) }
            else {
                require(record.playable && media.size == 2) { "Both media tracks must finish downloading first." }
                val inputs = withContext(Dispatchers.IO) { media.map { asset -> inspect(app.downloads.file(record, asset), if (asset.choice.kind == DownloadKind.VIDEO) "video/" else "audio/") } }
                val supported = InAppMp4Muxer.Factory()
                val needsVideo = inputs.any { it.mime.startsWith("video/") && it.mime !in supported.getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO) }
                val needsAudio = inputs.any { it.mime.startsWith("audio/") && it.mime !in supported.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO) }
                if (!convert && (needsVideo || needsAudio)) { publish(request.copy(phase = "Needs conversion", convertVideo = needsVideo, convertAudio = needsAudio)); return }
                val duration = inputs.map { it.durationUs }.filter { it > 0 }.minOrNull() ?: error("Cannot determine the downloaded duration.")
                val sequences = inputs.map { input ->
                    val item = MediaItem.Builder().setUri(Uri.fromFile(input.file)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(duration / 1000).build()).build()
                    val video = input.mime.startsWith("video/")
                    val edited = EditedMediaItem.Builder(item).setRemoveAudio(video).setRemoveVideo(!video).build()
                    EditedMediaItemSequence.Builder(setOf(if (video) C.TRACK_TYPE_VIDEO else C.TRACK_TYPE_AUDIO)).addItem(edited).build()
                }
                temporary.delete()
                publish(request.copy(phase = if (convert) "Converting" else "Merging"))
                val conversionVideo = convert && request.convertVideo
                val conversionAudio = convert && request.convertAudio
                val composition = Composition.Builder(sequences).setTransmuxVideo(!conversionVideo).setTransmuxAudio(!conversionAudio).build()
                val builder = Transformer.Builder(this).setEncoderFactory(DefaultEncoderFactory.Builder(this).setEnableFallback(false).build())
                if (conversionVideo) builder.setVideoMimeType(MimeTypes.VIDEO_H264)
                if (conversionAudio) builder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                try { transform(builder, composition, temporary) }
                catch (e: ExportException) {
                    if (!convert && e.errorCode in listOf(ExportException.ERROR_CODE_MUXING_FAILED, ExportException.ERROR_CODE_MUXING_TIMEOUT, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED)) {
                        temporary.delete(); publish(request.copy(phase = "Needs conversion", convertVideo = true, convertAudio = true)); return
                    }
                    throw e
                }
                withContext(Dispatchers.IO) {
                    val video = inspect(temporary, "video/"); val audio = inspect(temporary, "audio/")
                    val original = inputs.first { it.mime.startsWith("video/") }
                    require(video.width == original.width && video.height == original.height) { "The device cannot export this resolution unchanged." }
                    require(original.fps == 0 || video.fps == 0 || original.fps == video.fps) { "The device cannot export this frame rate unchanged." }
                    require(kotlin.math.abs(video.durationUs - duration) < 1_000_000 && kotlin.math.abs(audio.durationUs - duration) < 1_000_000) { "The exported tracks have inconsistent durations." }
                }
                source = temporary
            }
            require(source.isFile && source.length() > 0) { "The downloaded file is missing." }
            publish(request.copy(phase = "Saving"))
            withContext(Dispatchers.IO) {
                source.inputStream().use { input -> requireNotNull(contentResolver.openOutputStream(Uri.parse(request.destination), "wt")).use { output ->
                    val buffer = ByteArray(128 * 1024); var copied = 0L; var shown = -1
                    while (true) {
                        ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); copied += count
                        val percent = (copied * 100 / source.length()).toInt()
                        if (percent / 5 != shown / 5) { shown = percent; withContext(Dispatchers.Main) { publish(request.copy(phase = "Saving", progress = percent)) } }
                    }
                    output.flush(); require(copied == source.length()) { "The exported copy is incomplete." }
                } }
            }
            publish(request.copy(phase = "Saved", progress = 100))
        } catch (e: CancellationException) { cleanupDestination(request); publish(request.copy(phase = "Cancelled")); throw e }
        catch (e: Exception) { cleanupDestination(request); publish(request.copy(phase = "Failed", error = e.message ?: "Export failed. Save to files again to retry.")) }
        finally { transformer?.cancel(); transformer = null; temporary.delete(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    private fun cleanupDestination(request: DownloadExportState) { removeOutput(this, request.destination) }
    private suspend fun transform(builder: Transformer.Builder, composition: Composition, file: File) = coroutineScope {
        val progress = launch { val holder = ProgressHolder(); while (isActive) { delay(500); transformer?.let { if (it.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) publish(state.value.copy(progress = holder.progress)) } } }
        try { suspendCancellableCoroutine<Unit> { continuation ->
            val instance = builder.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { if (continuation.isActive) continuation.resumeWithException(exception) }
            }).build()
            transformer = instance
            continuation.invokeOnCancellation { instance.cancel() }
            instance.start(composition, file.absolutePath)
        } } finally { progress.cancel() }
    }
    override fun onTimeout(startId: Int, fgsType: Int) { job?.cancel(); stopSelf() }
    override fun onDestroy() { transformer?.cancel(); scope.cancel(); if (active === this) active = null; super.onDestroy() }
}
