package net.wingress.mobivious.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DownloadKind { VIDEO, AUDIO, CAPTION, THUMBNAIL, AVATAR }
enum class DownloadStatus { QUEUED, RUNNING, WAITING, COMPLETE, FAILED, CANCELLED }

data class DownloadChoice(val key: String, val kind: DownloadKind, val url: String, val metadata: String) {
    val json get() = JSONObject(metadata)
    val format get() = ApiParser.streamFormat(json)
    val label get() = json.text("label", format.audio?.name.orEmpty())
    val language get() = json.text("languageCode", json.text("language_code"))
    val extension get() = when (kind) {
        DownloadKind.CAPTION -> "vtt"
        DownloadKind.THUMBNAIL, DownloadKind.AVATAR -> "jpg"
        else -> if (format.mimeType.endsWith("webm")) "webm" else if (kind == DownloadKind.AUDIO) "m4a" else "mp4"
    }
    val mime get() = when (kind) {
        DownloadKind.CAPTION -> "text/vtt"
        DownloadKind.THUMBNAIL, DownloadKind.AVATAR -> "image/jpeg"
        else -> format.mimeType
    }
    fun json() = JSONObject().put("key", key).put("kind", kind.name).put("url", url).put("metadata", metadata)
    companion object {
        fun parse(j: JSONObject) = DownloadChoice(j.getString("key"), DownloadKind.valueOf(j.getString("kind")), j.getString("url"), j.getString("metadata"))
        fun catalog(j: JSONObject): DownloadChoice? {
            val kind = when (j.text("kind")) { "video" -> DownloadKind.VIDEO; "audio" -> DownloadKind.AUDIO; "caption" -> DownloadKind.CAPTION; else -> return null }
            if (j.text("key").isBlank() || j.text("url").isBlank()) return null
            val mime = ApiParser.streamFormat(j).mimeType
            if (kind == DownloadKind.VIDEO && !mime.startsWith("video/") || kind == DownloadKind.AUDIO && !mime.startsWith("audio/")) return null
            return DownloadChoice(j.text("key"), kind, j.text("url"), j.toString())
        }
    }
}
data class DownloadCatalog(val metadata: String, val choices: List<DownloadChoice>, val allowed: Boolean, val reason: String) {
    val details get() = ApiParser.details(JSONObject(metadata))
    companion object {
        fun parse(j: JSONObject) = DownloadCatalog(j.getJSONObject("video").toString(), j.optJSONArray("choices")?.objects().orEmpty().mapNotNull(DownloadChoice::catalog).distinctBy { it.key }, j.optBoolean("allowed"), j.text("reason"))
    }
}
data class DownloadSelection(val video: String? = null, val audio: String? = null, val captions: Set<String> = emptySet()) {
    val hasMedia get() = video != null || audio != null
    fun choices(catalog: DownloadCatalog): List<DownloadChoice> {
        require(catalog.allowed && hasMedia) { "Pick at least one video or audio track." }
        val keys = setOfNotNull(video, audio) + captions
        val selected = keys.map { key -> catalog.choices.singleOrNull { it.key == key } ?: error("The selected track is unavailable.") }
        require(video == null || selected.single { it.key == video }.kind == DownloadKind.VIDEO)
        require(audio == null || selected.single { it.key == audio }.kind == DownloadKind.AUDIO)
        require(captions.all { key -> selected.single { it.key == key }.kind == DownloadKind.CAPTION })
        return selected
    }
}
data class DownloadAsset(val choice: DownloadChoice, val filename: String, val transferId: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED, val bytes: Long = 0, val total: Long = 0, val error: String = "") {
    val media get() = choice.kind in listOf(DownloadKind.VIDEO, DownloadKind.AUDIO)
    val artwork get() = choice.kind in listOf(DownloadKind.THUMBNAIL, DownloadKind.AVATAR)
    fun json() = JSONObject().put("choice", choice.json()).put("filename", filename).put("transferId", transferId).put("status", status.name).put("bytes", bytes).put("total", total).put("error", error)
    companion object {
        fun parse(j: JSONObject) = DownloadAsset(DownloadChoice.parse(j.getJSONObject("choice")), j.getString("filename"), j.optLong("transferId"), DownloadStatus.valueOf(j.getString("status")), j.optLong("bytes"), j.optLong("total"), j.text("error"))
    }
}
data class DownloadRecord(val id: String, val server: String, val metadata: String, val created: Long, val assets: List<DownloadAsset>, val positionMs: Long = 0) {
    val details get() = ApiParser.details(JSONObject(metadata))
    val playable get() = assets.any { it.media } && assets.filter { it.media }.all { it.status == DownloadStatus.COMPLETE }
    val active get() = assets.any { it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.WAITING) }
    val bytes get() = assets.filterNot { it.artwork }.sumOf { it.bytes }
    val total get() = assets.filterNot { it.artwork }.takeIf { files -> files.all { it.total > 0 } }?.sumOf { it.total } ?: 0
    fun json() = JSONObject().put("id", id).put("server", server).put("metadata", metadata).put("created", created).put("positionMs", positionMs).put("assets", JSONArray(assets.map { it.json() }))
    companion object {
        fun create(server: String, catalog: DownloadCatalog, selection: DownloadSelection, now: Long = System.currentTimeMillis()): DownloadRecord {
            val selected = selection.choices(catalog)
            return DownloadRecord(UUID.randomUUID().toString(), server, catalog.metadata, now, selected.map { choice -> DownloadAsset(choice, "${choice.key}.${choice.extension}", total = choice.format.bytes) })
        }
        fun parse(j: JSONObject) = DownloadRecord(j.getString("id"), j.getString("server"), j.getString("metadata"), j.getLong("created"), j.getJSONArray("assets").objects().map(DownloadAsset::parse), j.optLong("positionMs"))
    }
}
data class DownloadDialogState(val video: Video? = null, val context: ApiContext? = null, val catalog: DownloadCatalog? = null,
    val selection: DownloadSelection = DownloadSelection(), val loading: Boolean = false, val busy: Boolean = false, val error: String? = null)
