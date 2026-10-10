package net.wingress.mobivious.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*

@Composable
internal fun RelatedChannelCard(channel: Channel, server: String, thinMode: Boolean, open: () -> Unit) {
    Card(onClick = open, enabled = ContentVisibility.validChannel(channel.id), modifier = Modifier.fillMaxWidth().padding(horizontal = Liquid.inset, vertical = 6.dp)
        .testTag("related-channel-${channel.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelAuthor(server, channel.image, channel.name, !thinMode, size = 48.dp, tag = "related-avatar-${channel.id}")
            if (channel.verified) Text("Verified channel", style = MaterialTheme.typography.labelSmall)
            if (channel.handle.isNotBlank()) Text(channel.handle, style = MaterialTheme.typography.bodySmall)
            DisplayFormats.subscribers(channel.subscribers).takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            channel.videoCount?.let { Text(DisplayFormats.inventory(it, "video"), style = MaterialTheme.typography.bodySmall) }
            if (channel.description.isNotBlank()) Text(channel.description, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CommunityPostCard(vm: AppViewModel, post: CommunityPost, comments: () -> Unit, detail: Boolean = false,
    channel: (String) -> Unit, link: (String) -> Unit, play: (Video) -> Unit, playlist: (Playlist) -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val body = post.comment
    Card(Modifier.fillMaxWidth().padding(horizontal = Liquid.inset, vertical = 8.dp).testTag("post-${post.key}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelAuthor(vm.store.server, body.avatar, body.author, !prefs.thinMode, size = 40.dp,
                tag = "post-avatar-${post.key}", onClick = body.authorId.takeIf(ContentVisibility::validChannel)?.let { { channel(it) } })
            if (body.verified) Text("Verified author", style = MaterialTheme.typography.labelSmall)
            val published = listOfNotNull(body.published.takeIf { it.isNotBlank() }, "Edited".takeIf { body.edited }).joinToString(" · ")
            if (published.isNotBlank()) Text(published, style = MaterialTheme.typography.bodySmall)
            RichCommentText(body, vm.store.server, "", link, collapsedLines = if (detail) Int.MAX_VALUE else 6,
                tag = "post-body-${post.key}", expansionControl = { expanded, toggle ->
                    PostExpansionButton(post.key, expanded, toggle)
                })
            when (val attachment = post.attachment) {
                is PostAttachment.Images -> PostGallery(attachment.images, vm.store.server, "post-media-${post.key}")
                is PostAttachment.VideoItem -> VideoCard(vm, attachment.video, vm.store.server,
                    { play(attachment.video) }, channel, signIn)
                is PostAttachment.PlaylistItem -> PlaylistCard(vm, attachment.playlist, { playlist(attachment.playlist) }, signIn)
                is PostAttachment.Poll -> {
                    Text(if (attachment.quiz) "Quiz" else "Poll", style = MaterialTheme.typography.titleSmall)
                    attachment.choices.forEachIndexed { index, choice ->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("post-choice-${post.key}-$index")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(choice.text)
                                choice.image?.let { PostGallery(listOf(it), vm.store.server, "post-choice-image-${post.key}-$index") }
                                if (attachment.quiz && choice.correct == true) Text("Correct answer", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    attachment.votes?.let { Text(DisplayFormats.audience(it, "vote"), style = MaterialTheme.typography.bodySmall) }
                }
                PostAttachment.Unavailable -> Text("Attachment unavailable", style = MaterialTheme.typography.bodyMedium)
                null -> Unit
            }
            PostActions(post, comments, share = {
                runCatching {
                    context.startActivity(postShareIntent(vm.store.server, post))
                }.onFailure { vm.message.value = "No app could share this post." }
            })
        }
    }
}

internal fun postShareIntent(server: String, post: CommunityPost): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PostLinks.url(server, post)), "Share post")

@Composable
internal fun PostExpansionButton(key: String, expanded: Boolean, toggle: () -> Unit) {
    OutlinedButton(onClick = toggle, modifier = Modifier.heightIn(min = 48.dp).testTag("post-expand-$key")) {
        Text(if (expanded) "Show less" else "Read more")
    }
}

@Composable
internal fun PostActions(post: CommunityPost, comments: () -> Unit, share: () -> Unit) {
    val validId = PostLinks.validId(post.id)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().testTag("post-actions-${post.key}"), verticalAlignment = Alignment.CenterVertically) {
            if (post.comment.likes != null) Row(Modifier.semantics(mergeDescendants = true) { contentDescription = DisplayFormats.audience(post.comment.likes, "like") },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.ThumbUpOffAlt, null, Modifier.size(18.dp))
                Text(DisplayFormats.compact(post.comment.likes), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = share, enabled = validId,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("post-share-${post.key}")) {
                Icon(Icons.Default.Share, "Share post")
            }
        }
        FilledTonalButton(onClick = comments, enabled = validId && ContentVisibility.validChannel(post.channelId),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("post-comments-${post.key}")) {
            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(post.commentCount?.let { "Comments (${DisplayFormats.compact(it)})" } ?: "Comments")
        }
    }
}

@Composable
internal fun PostDetailScreen(vm: AppViewModel, state: PostDetailState, list: LazyListState, channel: (String) -> Unit,
    link: (String) -> Unit, play: (Video) -> Unit, playlist: (Playlist) -> Unit, signIn: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("post-detail"), state = list, contentPadding = PaddingValues(bottom = LocalContentBottomInset.current)) {
        state.post?.let { post ->
            item {
                CommunityPostCard(vm, post, comments = { vm.openPostComments() }, detail = true,
                    channel = channel, link = link, play = play, playlist = playlist, signIn = signIn)
            }
        }
        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        state.error?.let { error -> item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Post unavailable", style = MaterialTheme.typography.titleMedium)
                Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::retryBrowse, modifier = Modifier.testTag("post-retry")) { Text("Retry") }
            }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostCommentsSheet(vm: AppViewModel, state: CommentsState, channel: (String) -> Unit, link: (String) -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .85f }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = {
        if (vm.postComments.value.threadKey != null) {
            vm.backPostComments()
            // Platform dismissal may already have hidden the sheet; returning from replies keeps it open.
            scope.launch { sheetState.show() }
        } else vm.closePostComments()
    }, sheetState = sheetState, sheetMaxWidth = 640.dp) {
        BackHandler(state.threadKey != null) { vm.backPostComments() }
        CommentsPanel(state, prefs.thinMode, Modifier.fillMaxWidth().height(height).testTag("post-comments-sheet"),
            vm::closePostComments, { vm.backPostComments() }, vm::sortPostComments, vm::loadPostComments,
            vm::openPostReplies, vm::postCommentPosition, channel, link)
    }
}

@Composable
internal fun PostGallery(images: List<PostImage>, server: String, tag: String) {
    if (images.isEmpty()) return
    val pager = rememberPagerState { images.size }
    var viewer by rememberSaveable(tag) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag(tag)) { index ->
            val image = images[index]
            val ratio = if (image.width > 0 && image.height > 0) (image.width.toFloat() / image.height).coerceIn(.5f, 3f) else 1f
            PostMediaImage(image, server, "Image ${index + 1} of ${images.size}",
                Modifier.fillMaxWidth().aspectRatio(ratio).clickable(onClickLabel = "Enlarge image") { viewer = true })
        }
        if (images.size > 1) Text("${pager.currentPage + 1} / ${images.size}", style = MaterialTheme.typography.labelMedium)
    }
    if (viewer) Dialog(onDismissRequest = { viewer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val largePager = rememberPagerState(initialPage = pager.currentPage) { images.size }
        Surface(Modifier.fillMaxSize().testTag("post-image-viewer")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Image ${largePager.currentPage + 1} of ${images.size}", Modifier.weight(1f).padding(16.dp))
                    IconButton(onClick = { viewer = false }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.Close, "Close image") }
                }
                HorizontalPager(largePager, Modifier.weight(1f)) { index ->
                    PostMediaImage(images[index], server, "Image ${index + 1} of ${images.size}", Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PostMediaImage(image: PostImage, server: String, description: String, modifier: Modifier) {
    val url = remember(server, image.url) { ChannelImages.url(server, image.url) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        if (url == null || failed) Text("Image unavailable", Modifier.padding(16.dp))
        else AsyncImage(url, description, modifier = Modifier.fillMaxSize(), imageLoader = AvatarImageLoader.get(LocalContext.current),
            contentScale = ContentScale.Fit, onError = { failed = true })
    }
}
