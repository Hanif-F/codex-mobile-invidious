package net.wingress.mobivious.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import net.wingress.mobivious.data.DisplayFormats
import net.wingress.mobivious.data.Video

@Composable
internal fun publishedLabel(video: Video): String {
    // Re-evaluate when the device locale changes, even for already loaded metadata.
    LocalConfiguration.current
    return video.publishedAt?.let { relativePublication(it) } ?: video.published
}

internal fun relativePublication(seconds: Long?): String = DisplayFormats.publication(seconds)?.let {
    DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}.orEmpty()

@Composable
internal fun VideoMetadataLine(video: Video, modifier: Modifier = Modifier, likes: Long? = null) {
    val label = listOf(DisplayFormats.audience(video.views, "view"), DisplayFormats.audience(likes, "like"), publishedLabel(video))
        .filter(String::isNotBlank).joinToString(" · ")
    if (label.isNotBlank()) Text(label, modifier.testTag("video-metadata-${video.id}"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
