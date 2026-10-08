package net.wingress.mobivious.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import net.wingress.mobivious.data.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object DownloadPlayback {
    fun source(context: Context, repository: DownloadRepository, record: DownloadRecord, occurrence: String) : androidx.media3.exoplayer.source.MediaSource {
        require(record.playable) { "The selected media tracks have not finished downloading." }
        val details = repository.localDetails(record)
        val metadata = MediaMetadata.Builder().setTitle(details.video.title).setArtist(details.video.author)
            .setArtworkUri(details.video.thumbnail.takeIf(String::isNotBlank)?.let(Uri::parse))
            .setExtras(Bundle().apply { putString("occurrence", occurrence); putString("downloadId", record.id); putBoolean("savePosition", true) }).build()
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
        val media = record.assets.filter { it.media }.sortedBy { if (it.choice.kind == DownloadKind.VIDEO) 0 else 1 }
        val sources = media.mapIndexed { index, asset ->
            val file = repository.file(record, asset)
            require(file.isFile && file.length() > 0) { "The downloaded file is missing. Retry the download." }
            val item = MediaItem.Builder().setMediaId(details.video.id).setUri(Uri.fromFile(file)).setMimeType(asset.choice.mime).setMediaMetadata(metadata)
            if (index == 0) item.setSubtitleConfigurations(details.captions.map { caption ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(caption.url)).setMimeType(MimeTypes.TEXT_VTT).setLanguage(caption.language).setLabel(caption.label).build()
            })
            factory.createMediaSource(item.build())
        }
        return if (sources.size == 1) sources.single() else MergingMediaSource(true, true, *sources.toTypedArray())
    }
}
