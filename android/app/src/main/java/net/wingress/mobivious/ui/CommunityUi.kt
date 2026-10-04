package net.wingress.mobivious.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    Card(onClick = open, enabled = ContentVisibility.validChannel(channel.id), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag("related-channel-${channel.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelAuthor(server, channel.image, channel.name, !thinMode, size = 48.dp, tag = "related-avatar-${channel.id}")
            if (channel.verified) Text("Verified channel", style = MaterialTheme.typography.labelSmall)
            Text("${channel.subscribers} subscribers", style = MaterialTheme.typography.bodySmall)
            if (channel.description.isNotBlank()) Text(channel.description, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CommunityPostCard(vm: AppViewModel, post: CommunityPost, detail: Boolean = false, open: () -> Unit = {},
    channel: (String) -> Unit, link: (String) -> Unit, play: (Video) -> Unit, playlist: (Playlist) -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val body = post.comment
    val action = if (detail || !PostLinks.validId(post.id)) Modifier else Modifier.clickable(onClickLabel = "Open community post", onClick = open)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(action).testTag("post-${post.key}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelAuthor(vm.store.server, body.avatar, body.author, !prefs.thinMode, size = 40.dp,
                tag = "post-avatar-${post.key}", onClick = body.authorId.takeIf(ContentVisibility::validChannel)?.let { { channel(it) } })
            if (body.verified) Text("Verified author", style = MaterialTheme.typography.labelSmall)
            val published = listOfNotNull(body.published.takeIf { it.isNotBlank() }, "Edited".takeIf { body.edited }).joinToString(" · ")
            if (published.isNotBlank()) Text(published, style = MaterialTheme.typography.bodySmall)
            RichCommentText(body, vm.store.server, "", link, collapsedLines = if (detail) Int.MAX_VALUE else 6, tag = "post-body-${post.key}")
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
                    attachment.votes?.let { Text("${java.text.NumberFormat.getIntegerInstance().format(it)} votes", style = MaterialTheme.typography.bodySmall) }
                }
                PostAttachment.Unavailable -> Text("Attachment unavailable", style = MaterialTheme.typography.bodyMedium)
                null -> Unit
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${body.likes} likes" }) {
                Icon(Icons.Default.ThumbUpOffAlt, null, Modifier.size(18.dp))
                Text(java.text.NumberFormat.getIntegerInstance().format(body.likes), style = MaterialTheme.typography.labelMedium)
            }
            if (!detail) TextButton(onClick = open, enabled = PostLinks.validId(post.id), modifier = Modifier.testTag("post-open-${post.key}")) {
                Text(post.commentCount?.let { "View post · $it comments" } ?: "View post")
            }
        }
    }
}

@Composable
internal fun PostDetailScreen(vm: AppViewModel, state: PostDetailState, list: LazyListState, channel: (String) -> Unit,
    link: (String) -> Unit, play: (Video) -> Unit, playlist: (Playlist) -> Unit, signIn: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().testTag("post-detail"), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
        state.post?.let { post ->
            item { CommunityPostCard(vm, post, detail = true, channel = channel, link = link, play = play, playlist = playlist, signIn = signIn) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = vm::openPostComments, enabled = ContentVisibility.validChannel(post.channelId),
                        modifier = Modifier.testTag("post-comments-open")) { Text(post.commentCount?.let { "Comments ($it)" } ?: "Comments") }
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Community post", PostLinks.url(vm.store.server, post)))
                        vm.message.value = "Post link copied"
                    }, modifier = Modifier.testTag("post-copy")) { Text("Copy link") }
                    TextButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PostLinks.url(vm.store.server, post))
                        }, "Share post"))
                    }, modifier = Modifier.testTag("post-share")) { Text("Share") }
                }
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
                    IconButton(onClick = { viewer = false }) { Icon(Icons.Default.Close, "Close image") }
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
