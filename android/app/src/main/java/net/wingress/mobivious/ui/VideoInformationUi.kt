package net.wingress.mobivious.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.*
import java.util.Locale

/** Explicit browser targets prevent unsupported bundled links from returning to MainActivity. */
internal fun openExternalContent(context: Context, url: String, failed: () -> Unit) {
    val uri = Uri.parse(url)
    if (uri.scheme !in listOf("https", "http") || uri.userInfo != null) return
    val request = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    val targets = context.packageManager.queryIntentActivities(request, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { request.cloneFilter().setComponent(android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
    if (targets.isEmpty()) { failed(); return }
    runCatching { context.startActivity(Intent.createChooser(targets.first(), "Open link").putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())) }
        .onFailure { failed() }
}

@Composable
internal fun VideoNotices(details: VideoDetails) {
    if (details.listed == false) Text("Unlisted", Modifier.testTag("video-unlisted"), style = MaterialTheme.typography.labelLarge)
    if (details.upcoming == true || details.premiereTimestamp != null || details.notice.isNotBlank()) {
        val date = DisplayFormats.timestamp(details.premiereTimestamp).takeIf(String::isNotBlank)
        val notice = details.notice.ifBlank { if (details.upcoming == true) "Upcoming video" else "Premiere" }
        Text(listOfNotNull(notice, date).joinToString(" · "), Modifier.testTag("video-premiere"), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun VideoDescription(details: VideoDetails, occurrence: String, server: String, expanded: Boolean, link: (String) -> Unit) {
    AnimatedVisibility(expanded, enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoStatistics(details)
            NativeRichText(details.description, details.descriptionHtml, occurrence, server, details.video.id, link, tag = "watch-description-text")
            VideoInformation(details, occurrence, server, link)
        }
    }
}

@Composable
internal fun VideoStatistics(details: VideoDetails) {
    val views = details.video.views.takeIf { details.video.viewCountPrecision == CountPrecision.EXACT }
    val likes = details.likes.takeIf { details.likeCountPrecision == CountPrecision.EXACT }
    if (views == null && likes == null && details.video.publishedAt == null) return
    Column(Modifier.fillMaxWidth().testTag("video-statistics"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DisplayFormats.inventory(views, "view").takeIf(String::isNotBlank)?.let { Text(it, Modifier.testTag("video-exact-views")) }
        DisplayFormats.inventory(likes, "like").takeIf(String::isNotBlank)?.let { Text(it, Modifier.testTag("video-exact-likes")) }
        details.video.publishedAt?.let { Text("Published: ${DisplayFormats.timestamp(it, dateOnly = true)}", Modifier.testTag("video-published-date")) }
    }
}

@Composable
internal fun VideoInformation(details: VideoDetails, occurrence: String, server: String, link: (String) -> Unit) {
    val hasInfo = details.genre.isNotBlank() || details.license != null || details.familyFriendly != null || details.allowedRegions != null || details.music.isNotEmpty()
    if (!hasInfo) return
    var regions by rememberSaveable(occurrence) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("video-information"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (details.genre.isNotBlank()) {
            if (details.genreUrl.isNotBlank() && ContentLinks.resolve(details.genreUrl, server) != null)
                TextButton(onClick = { link(details.genreUrl) }, contentPadding = PaddingValues(0.dp)) { Text("Genre: ${details.genre}") }
            else Text("Genre: ${details.genre}")
        }
        details.license?.let { Text("License: ${it.ifBlank { "Standard YouTube license" }}", Modifier.testTag("video-license")) }
        details.familyFriendly?.let { Text("Family friendly: ${if (it) "Yes" else "No"}") }
        details.allowedRegions?.let { allowed ->
            if (allowed.isEmpty()) Text("No allowed regions reported")
            else {
                TextButton(onClick = { regions = !regions }, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("video-regions-toggle")) {
                    Text("${if (regions) "Hide" else "Show"} allowed regions (${allowed.size})")
                }
                if (regions) Text(allowed.joinToString(", ") { code -> "${Locale.Builder().setRegion(code).build().displayCountry.ifBlank { code }} ($code)" }, Modifier.testTag("video-regions"))
            }
        }
        if (details.music.isNotEmpty()) Text("Music in this video", style = MaterialTheme.typography.titleMedium)
        details.music.forEach { music ->
            Column(Modifier.fillMaxWidth().testTag("video-music-credit")) {
                if (music.song.isNotBlank()) Text(music.song, style = MaterialTheme.typography.titleSmall)
                listOf(music.artist, music.album).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotEmpty)?.let { Text(it) }
                if (music.license.isNotBlank()) Text(music.license, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun LinkResolutionDialog(state: LinkResolutionState, retry: () -> Unit, external: (String) -> Unit, dismiss: () -> Unit) {
    if (state.link == null) return
    AlertDialog(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer, title = { Text("Open channel") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) { CircularProgressIndicator(); Text("Resolving channel…") }
            else Text(state.error ?: "This channel could not be resolved.")
        }
    }, confirmButton = { if (!state.loading) LibraryControlScene { LibraryActionButton("Retry", prominent = true, onClick = retry) } }, dismissButton = {
        Row { if (!state.loading) TextButton(onClick = { external(state.link.resolveUrl); dismiss() }) { Text("Open externally") }
            TextButton(onClick = dismiss) { Text("Cancel") } }
    })
}
