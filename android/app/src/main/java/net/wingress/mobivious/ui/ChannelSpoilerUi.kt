package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*

@Composable
internal fun ChannelSpoilerArtwork(vm: AppViewModel, video: Video, modifier: Modifier = Modifier,
    play: (() -> Unit)? = null, playbackOccurrence: String? = null, offline: Boolean = false,
    content: @Composable (Boolean) -> Unit) {
    val state by vm.channelSpoilers.state.collectAsStateWithLifecycle()
    val navigation by vm.spoilerNavigation.collectAsStateWithLifecycle()
    val decision = state.decision(video, cached = !offline)
    var revealed by remember(video.id, video.indexId, video.playlistIndex, vm.api.context(), navigation, state.revision) { mutableStateOf(false) }
    val playbackRevealed = playbackOccurrence != null && state.playback.occurrence == playbackOccurrence && state.playback.thumbnail
    LaunchedEffect(video, state.ready, state.revision, navigation, offline) { if (!offline) vm.channelSpoilers.ensure(listOf(video)) }
    val hidden = decision.thumbnail && !(revealed || playbackRevealed) || decision.pending
    Box(modifier.then(if (hidden) Modifier.testTag("spoiler-thumbnail-${video.id}") else Modifier)
        .then(if (hidden || play != null) Modifier.clickable(enabled = !decision.pending,
        onClickLabel = if (hidden) "Reveal thumbnail" else "Open video") {
        if (hidden) { if (playbackOccurrence != null) vm.channelSpoilers.revealThumbnail(playbackOccurrence) else revealed = true }
        else play?.invoke()
    } else Modifier)) {
        if (hidden) BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = decision.thumbnailLabel }, contentAlignment = Alignment.Center) {
            if (maxWidth < 160.dp || maxHeight < 80.dp) Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (decision.pending) "Loading…" else "Hidden", style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                if (decision.unknown) Text("Upload date unknown", style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            } else Text(decision.thumbnailLabel, Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        } else content(revealed || playbackRevealed)
    }
}

/** Passive surfaces get the masked title; revealable titles use DeArrowTitle. */
@Composable
internal fun maskedVideoTitle(vm: AppViewModel, video: Video, original: String = video.title, offline: Boolean = false): String {
    val state by vm.channelSpoilers.state.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val titles by vm.dearrowTitles.titles.collectAsStateWithLifecycle()
    return state.title(video, if (prefs.dearrowEnabled && original == video.title) titles[video.id] else null, original = original, cached = !offline)
}
