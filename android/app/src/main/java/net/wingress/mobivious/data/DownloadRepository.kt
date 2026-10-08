package net.wingress.mobivious.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.MainActivity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DownloadDatabase(context: Context) : SQLiteOpenHelper(context, "downloads.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE downloads (id TEXT PRIMARY KEY, record TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun read(): List<DownloadRecord> = readableDatabase.query("downloads", arrayOf("record"), null, null, null, null, null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(DownloadRecord.parse(JSONObject(cursor.getString(0)))) }
    }
    @Synchronized fun save(record: DownloadRecord) {
        check(writableDatabase.insertWithOnConflict("downloads", null, ContentValues().apply { put("id", record.id); put("record", record.json().toString()) }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Could not save the download." }
    }
    @Synchronized fun delete(id: String) { writableDatabase.delete("downloads", "id = ?", arrayOf(id)) }
}

class DownloadRepository(private val context: Context) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val database = DownloadDatabase(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = CompletableDeferred<Unit>()
    private val root get() = File(requireNotNull(context.getExternalFilesDir(null)) { "Download storage is unavailable." }, "downloads")
    val records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    init { scope.launch {
        try { mutex.withLock { records.value = database.read().sortedByDescending { it.created } }; reconcile() }
        catch (e: Exception) { error.value = e.message ?: "Could not read downloads." }
        finally { initialized.complete(Unit) }
        while (isActive) { delay(1000); if (records.value.any { it.active }) runCatching { reconcile() }.onFailure { error.value = it.message } }
    } }
    fun find(id: String) = records.value.firstOrNull { it.id == id }
    fun file(record: DownloadRecord, asset: DownloadAsset): File {
        require(record.id.matches(Regex("[0-9a-f-]{36}")) && asset.filename.matches(Regex("[A-Za-z0-9_.-]+")))
        return File(File(root, record.id), asset.filename)
    }
    private fun save(record: DownloadRecord) { database.save(record); records.value = (records.value.filterNot { it.id == record.id } + record).sortedByDescending { it.created } }
    private fun address(server: String, raw: String): String {
        val base = requireNotNull(server.toHttpUrlOrNull())
        val url = requireNotNull(base.resolve(raw))
        require(url.username.isEmpty() && url.password.isEmpty() && (url.isHttps || BuildConfig.DEBUG && url.host in listOf("localhost", "127.0.0.1", "10.0.2.2"))) { "Invalid download address." }
        require(url.host == base.host && url.port == base.port && url.scheme == base.scheme) { "Downloads must use the originating instance." }
        return url.toString()
    }
    suspend fun create(server: String, catalog: DownloadCatalog, selection: DownloadSelection): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            var record = DownloadRecord.create(server, catalog, selection)
            val video = catalog.details.video
            val artwork = listOf(DownloadKind.THUMBNAIL to video.thumbnail, DownloadKind.AVATAR to Avatars.url(server, video.authorAvatar).orEmpty()).mapNotNull { (kind, url) ->
                if (url.isBlank()) null else runCatching { DownloadAsset(DownloadChoice(kind.name.lowercase(), kind, address(server, url), "{}"), "${kind.name.lowercase()}.jpg") }.getOrNull()
            }
            record = record.copy(assets = record.assets + artwork)
            save(record) // Journal before submitting system work; interrupted enqueue can be retried.
            enqueue(record)
            record.id
        }
    }
    private fun enqueue(original: DownloadRecord) {
        var record = original
        record.assets.forEachIndexed { index, asset ->
            if (asset.status == DownloadStatus.COMPLETE || asset.status in listOf(DownloadStatus.RUNNING, DownloadStatus.WAITING) || asset.status == DownloadStatus.QUEUED && asset.transferId > 0) return@forEachIndexed
            // DownloadManager removes files asynchronously. Never let an old attempt
            // delete the destination of a new attempt at the same selected track.
            val pending = asset.copy(filename = "${asset.choice.key}-${UUID.randomUUID()}.${asset.choice.extension}", transferId = 0, status = DownloadStatus.QUEUED, bytes = 0, error = "")
            val replacement = try {
                if (asset.transferId > 0) manager.remove(asset.transferId)
                file(record, asset).delete()
                record = record.copy(assets = record.assets.toMutableList().apply { set(index, pending) }); save(record)
                val target = file(record, pending); check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                val request = DownloadManager.Request(Uri.parse(address(record.server, asset.choice.url)))
                    .setDestinationUri(Uri.fromFile(target)).setTitle(record.details.video.title)
                    .setDescription(if (asset.artwork) "Download artwork" else "${asset.choice.kind.name.lowercase()} track")
                    .setMimeType(asset.choice.mime).setAllowedOverMetered(true).setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                pending.copy(transferId = manager.enqueue(request))
            } catch (e: Exception) { pending.copy(status = DownloadStatus.FAILED, error = e.message ?: "Could not start download.") }
            record = record.copy(assets = record.assets.toMutableList().apply { set(index, replacement) })
            save(record)
        }
    }
    suspend fun reconcile() = mutex.withLock { withContext(Dispatchers.IO) {
        for (record in records.value) {
            val assets = record.assets.map { original ->
                // Recover a system ID if the process died between enqueue and its DB save.
                val asset = if (original.transferId == 0L && original.status == DownloadStatus.QUEUED) {
                    val target = Uri.fromFile(file(record, original)).toString()
                    val recovered = manager.query(DownloadManager.Query()).use { cursor ->
                        var id = 0L
                        while (cursor.moveToNext()) if (cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) == target) id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        id
                    }
                    original.copy(transferId = recovered)
                } else original
                if (asset.transferId == 0L || asset.status in listOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED)) asset
                else manager.query(DownloadManager.Query().setFilterById(asset.transferId)).use { cursor ->
                    if (!cursor.moveToFirst()) asset.copy(status = DownloadStatus.FAILED, error = "The system download was removed. Retry.")
                    else {
                        fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                        val total = number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0)
                        val bytes = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0)
                        val status = when (number(DownloadManager.COLUMN_STATUS).toInt()) {
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETE
                            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                            DownloadManager.STATUS_PAUSED -> DownloadStatus.WAITING
                            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                            else -> DownloadStatus.QUEUED
                        }
                        val valid = status != DownloadStatus.COMPLETE || file(record, asset).let { it.isFile && it.length() > 0 && (total == 0L || it.length() == total) &&
                            (asset.status == DownloadStatus.COMPLETE || validContent(it, asset)) }
                        asset.copy(status = if (valid) status else DownloadStatus.FAILED, total = total, bytes = bytes,
                            error = if (!valid) "The downloaded file is missing or incomplete. Retry." else if (status == DownloadStatus.FAILED) "Download failed (${number(DownloadManager.COLUMN_REASON)}). Retry when the source is available." else "")
                    }
                }
            }
            val reconciled = record.copy(assets = assets)
            if (assets != record.assets) save(reconciled)
            if (assets.any { it.status == DownloadStatus.QUEUED && it.transferId == 0L }) enqueue(reconciled)
        }
    } }
    private fun validContent(file: File, asset: DownloadAsset): Boolean = runCatching {
        if (asset.choice.kind == DownloadKind.CAPTION) file.inputStream().bufferedReader().use { it.readLine()?.removePrefix("\uFEFF")?.startsWith("WEBVTT") == true }
        else if (!asset.media) true
        else {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val types = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                val selected = if (asset.choice.kind == DownloadKind.VIDEO) "video/" else "audio/"
                val excluded = if (asset.choice.kind == DownloadKind.VIDEO) "audio/" else "video/"
                types.any { it.startsWith(selected) } && types.none { it.startsWith(excluded) }
            } finally { extractor.release() }
        }
    }.getOrDefault(false)
    suspend fun retry(id: String) { reconcile(); mutex.withLock { withContext(Dispatchers.IO) { find(id)?.let(::enqueue) } } }
    suspend fun cancel(id: String) = mutex.withLock { withContext(Dispatchers.IO) {
        find(id)?.let { record -> save(record.copy(assets = record.assets.map { asset ->
            if (asset.status == DownloadStatus.COMPLETE) asset else { if (asset.transferId > 0) manager.remove(asset.transferId); file(record, asset).delete(); asset.copy(transferId = 0, status = DownloadStatus.CANCELLED, bytes = 0, error = "Cancelled") }
        })) }
    } }
    suspend fun delete(id: String) = mutex.withLock { withContext(Dispatchers.IO) {
        find(id)?.let { record -> record.assets.forEach { if (it.transferId > 0) manager.remove(it.transferId) }; File(root, record.id).deleteRecursively(); database.delete(id); records.value = records.value.filterNot { it.id == id } }
    } }
    suspend fun position(id: String, position: Long) = mutex.withLock { withContext(Dispatchers.IO) { find(id)?.let { save(it.copy(positionMs = position.coerceAtLeast(0))) } } }
    fun localDetails(record: DownloadRecord): VideoDetails {
        fun artwork(kind: DownloadKind) = record.assets.firstOrNull { it.choice.kind == kind && it.status == DownloadStatus.COMPLETE }?.let { file(record, it).takeIf(File::isFile)?.toURI()?.toString() }.orEmpty()
        val details = record.details
        return details.copy(video = details.video.copy(thumbnail = artwork(DownloadKind.THUMBNAIL), authorAvatar = artwork(DownloadKind.AVATAR)), dash = "", hls = "", fallback = "",
            recommendations = emptyList(), liveChatReplay = false, storyboards = emptyList(), formats = record.assets.filter { it.media }.map { it.choice.format },
            captions = record.assets.filter { it.choice.kind == DownloadKind.CAPTION && it.status == DownloadStatus.COMPLETE }.map { Caption(it.choice.label, it.choice.language, file(record, it).toURI().toString()) })
    }
    fun refresh() { scope.launch { runCatching { reconcile() }.onFailure { error.value = it.message } } }
    suspend fun completion() { initialized.await(); reconcile() }
}

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(DownloadManager.ACTION_DOWNLOAD_COMPLETE, DownloadManager.ACTION_NOTIFICATION_CLICKED)) return
        val app = context.applicationContext as MobiviousApplication
        // DownloadProvider runs under a separate privileged UID. This receiver is
        // exported, but treats broadcasts only as a hint: all state comes from our
        // own DownloadManager rows, never from caller-supplied IDs or paths.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { runCatching { app.downloads.completion() }.onFailure { app.downloads.error.value = it.message ?: "Could not reconcile downloads." } } finally { pending.finish() } }
        if (intent.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("openDownloads", true))
    }
}
