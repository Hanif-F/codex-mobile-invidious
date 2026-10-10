package net.wingress.mobivious.ui

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.text.util.Linkify
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun CommentsEntry(state: CommentsState, open: () -> Unit) {
    WatchDisclosureRow("Comments", Icons.Default.ChatBubbleOutline, Modifier.testTag("comments-entry"),
        detail = state.feed.page.count?.let { DisplayFormats.audience(it, "comment") }, onClick = open)
}

@Composable
internal fun CommentsDrawer(vm: AppViewModel, state: CommentsState, modifier: Modifier,
    channel: (String) -> Unit, link: (String) -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    CommentsPanel(state, prefs.thinMode, modifier, vm::closeComments, { vm.backComments() }, vm::sortComments,
        vm::loadComments, vm::openReplies, vm::commentPosition, channel, link, watchStyle = true)
}

@Composable
internal fun CommentsPanel(state: CommentsState, thinMode: Boolean, modifier: Modifier, close: () -> Unit,
    back: () -> Unit, changeSort: (CommentSort) -> Unit, load: (Boolean, String?) -> Unit,
    replies: (Comment) -> Unit, savePosition: (String?, CommentPosition) -> Unit, channel: (String) -> Unit, link: (String) -> Unit,
    watchStyle: Boolean = false) {
    val latest by rememberUpdatedState(state)
    val mainList = remember(state.target, state.context, state.sort) {
        LazyListState(state.feed.position.index, state.feed.position.offset)
    }
    val thread = state.thread
    val key = state.threadKey
    val threadList = remember(state.target, state.context, state.sort, key) {
        LazyListState(thread?.feed?.position?.index ?: 0, thread?.feed?.position?.offset ?: 0)
    }
    val list = if (thread == null) mainList else threadList
    val feed = thread?.feed ?: state.feed
    LaunchedEffect(list, state.target, state.context, state.sort, key) {
        snapshotFlow { CommentPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().collect { position ->
                if (latest.target == state.target && latest.context == state.context && latest.sort == state.sort && latest.threadKey == key)
                    savePosition(key, position)
            }
    }
    val content: @Composable ColumnScope.() -> Unit = {
            if (watchStyle) WatchPanelToolbar(if (thread != null) "Replies" else "Comments", "Close comments", close,
                Icons.Default.ChatBubbleOutline, detail = if (thread == null) state.feed.page.count?.let { commentCount(it) } else null,
                backLabel = "Back to comments", back = if (thread != null) back else null)
            else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (thread != null) IconButton(onClick = back, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to comments") }
                else Icon(Icons.Default.ChatBubbleOutline, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (thread != null) "Replies" else "Comments", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                if (thread == null) state.feed.page.count?.let { Text(commentCount(it), style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = close, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.Close, "Close comments") }
            }
            if (thread == null) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommentSort.entries.forEach { sort -> FilterChip(selected = state.sort == sort,
                    onClick = { changeSort(sort) }, label = { Text(sort.label) }, modifier = Modifier.testTag("comments-sort-${sort.apiValue}")) }
            }
            if (!watchStyle) HorizontalDivider()
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(if (thread == null) "comments-list" else "comment-replies-list"),
                state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                if (thread != null) item(key = "parent") {
                    CommentRow(thread.parent, state.context?.server.orEmpty(), state.videoId.orEmpty(), channel, link, parent = true, thinMode = thinMode)
                    Text("Replies", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(feed.page.items, key = { "comment:${it.key}" }) { comment ->
                    CommentRow(comment, state.context?.server.orEmpty(), state.videoId.orEmpty(), channel, link,
                        replies = if (thread == null) ({ replies(comment) }) else null, thinMode = thinMode)
                }
                if (feed.loading) item { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp)); Text("Loading ${if (thread == null) "comments" else "replies"}…", Modifier.padding(top = 12.dp))
                } }
                if (feed.loaded && feed.page.items.isEmpty() && feed.error == null) item {
                    Text(if (thread == null) "No comments to show. ${if (state.target is CommentTarget.Post) "Comments may be unavailable for this post." else "Comments may be disabled for this video."}" else "No replies to show.",
                        Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (feed.error != null) item {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(if (thread == null) "Comments unavailable" else "Replies unavailable", style = MaterialTheme.typography.titleSmall)
                        Text(feed.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        OutlinedButton(onClick = { load(feed.loaded, key) }, modifier = Modifier.testTag("comments-retry")) { Text("Retry") }
                    }
                }
                if (feed.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp).semantics { contentDescription = "Loading more ${if (thread == null) "comments" else "replies"}" })
                } }
                else if (feed.loaded && feed.page.continuation.isNotEmpty() && feed.error == null) item {
                    OutlinedButton(onClick = { load(true, key) }, modifier = Modifier.fillMaxWidth().testTag("comments-load-more")) {
                        Text(if (thread == null) "More comments" else "More replies")
                    }
                }
            }
    }
    if (watchStyle) WatchPanelSurface(modifier.testTag("comments-drawer"), content)
    else Surface(modifier.testTag("comments-drawer"), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) { Column(Modifier.fillMaxSize(), content = content) }
}

@Composable
internal fun CommentRow(comment: Comment, server: String, videoId: String, channel: (String) -> Unit,
    link: (String) -> Unit, replies: (() -> Unit)? = null, parent: Boolean = false, thinMode: Boolean = false) {
    val validAuthor = ContentVisibility.validChannel(comment.authorId)
    val canOpenAuthor = validAuthor || CommentLinks.resolve(comment.authorUrl, server, videoId) != null && comment.authorUrl.isNotBlank()
    val authorClick = { if (validAuthor) channel(comment.authorId) else if (comment.authorUrl.isNotBlank()) link(comment.authorUrl) }
    Column(Modifier.fillMaxWidth().background(if (parent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
        .testTag(if (parent) "comment-parent" else "comment-row-${comment.key}")) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (comment.pinned) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pinned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChannelAuthor(server, comment.avatar, comment.author, !thinMode, size = 40.dp,
                    modifier = Modifier.fillMaxWidth(), tag = "comment-avatar-${comment.key}", onClick = authorClick.takeIf { canOpenAuthor })
                if (comment.creator || comment.verified || comment.member) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comment.creator) Text("Creator", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (comment.verified) Icon(Icons.Default.Verified, "Verified author", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    if (comment.member) Text("Member", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                val date = listOfNotNull(comment.published.takeIf { it.isNotBlank() }, "Edited".takeIf { comment.edited }).joinToString(" · ")
                if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RichCommentText(comment, server, videoId, link)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (comment.likes != null) Row(Modifier.semantics(mergeDescendants = true) { contentDescription = DisplayFormats.audience(comment.likes, "like") },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ThumbUpOffAlt, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(commentCount(comment.likes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    comment.heart?.let { heart -> Row(Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Hearted by ${heart.name.ifBlank { "the creator" }}"
                    }, verticalAlignment = Alignment.CenterVertically) {
                        if (!thinMode && heart.thumbnail.isNotBlank()) ChannelAvatar(server, heart.thumbnail, heart.name, 18.dp, "comment-heart-avatar-${comment.key}")
                        Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    } }
                }
                if (replies != null && comment.replyContinuation.isNotBlank()) AssistChip(onClick = replies,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("comment-replies-${comment.key}"),
                    leadingIcon = { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)) },
                    label = { Text(if (comment.replyCount > 0) "${commentCount(comment.replyCount.toLong())} ${if (comment.replyCount == 1) "reply" else "replies"}" else "View replies") })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

private data class CommentEmoji(val url: String, val alt: String)
private data class RichContent(val text: AnnotatedString, val emoji: Map<String, CommentEmoji>)
private fun attribute(tag: String, name: String): String = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    .find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }.orEmpty()

/** Convert the server's HTML subset to native spans; HTML event handlers are never executed. */
private fun richContent(text: String, htmlSource: String, server: String, video: String, color: Color, link: (String) -> Unit): RichContent {
    val emoji = linkedMapOf<String, CommentEmoji>()
    val plain: Spanned = if (htmlSource.isBlank()) SpannableString(text).also { Linkify.addLinks(it, Linkify.WEB_URLS) }
    else {
        val html = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>").replace(htmlSource, "")
        val withImages = Regex("(?is)<img\\b[^>]*>").replace(html) { match ->
            val alt = Html.fromHtml(attribute(match.value, "alt"), Html.FROM_HTML_MODE_COMPACT).toString().ifBlank { "Emoji" }
            val rawUrl = Html.fromHtml(attribute(match.value, "src"), Html.FROM_HTML_MODE_COMPACT).toString()
            val url = ChannelImages.url(server, rawUrl) ?: resolved(server, rawUrl)
            if (rawUrl.isBlank() || CommentLinks.resolve(url, server, video) == null) Html.escapeHtml(alt)
            else {
                val id = "emoji-${emoji.size}"
                emoji[id] = CommentEmoji(url, alt)
                "<img src=\"$id\">"
            }
        }
        Html.fromHtml(withImages.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT)
    }
    val builder = AnnotatedString.Builder()
    val offsets = IntArray(plain.length + 1)
    for (index in 0 until plain.length) {
        offsets[index] = builder.length
        val img = plain.getSpans(index, index + 1, ImageSpan::class.java).firstOrNull { plain.getSpanStart(it) == index }
        val image = img?.source?.let { emoji[it] }
        if (img != null && image != null) builder.appendInlineContent(img.source!!, image.alt) else builder.append(plain[index])
    }
    offsets[plain.length] = builder.length
    plain.getSpans(0, plain.length, Any::class.java).forEach { span ->
        val start = offsets[plain.getSpanStart(span).coerceIn(0, plain.length)]
        val end = offsets[plain.getSpanEnd(span).coerceIn(0, plain.length)]
        if (start < end) when (span) {
            is StyleSpan -> builder.addStyle(SpanStyle(fontWeight = if (span.style and Typeface.BOLD != 0) FontWeight.Bold else null,
                fontStyle = if (span.style and Typeface.ITALIC != 0) FontStyle.Italic else null), start, end)
            is StrikethroughSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is UnderlineSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is URLSpan -> if (CommentLinks.resolve(span.url, server, video) != null) builder.addLink(LinkAnnotation.Clickable(span.url,
                TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { link(span.url) }, start, end)
        }
    }
    val linked = plain.getSpans(0, plain.length, URLSpan::class.java).map { plain.getSpanStart(it) until plain.getSpanEnd(it) }.toMutableList()
    fun add(start: Int, end: Int, target: String) {
        if (start >= end || linked.any { start < it.last + 1 && end > it.first } || ContentLinks.resolve(target, server, video) == null) return
        builder.addLink(LinkAnnotation.Clickable(target, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { link(target) }, offsets[start], offsets[end])
        linked += start until end
    }
    val auto = SpannableString(plain.toString()).also { Linkify.addLinks(it, Linkify.WEB_URLS) }
    auto.getSpans(0, auto.length, URLSpan::class.java).forEach { add(auto.getSpanStart(it), auto.getSpanEnd(it), it.url) }
    if (video.isNotEmpty()) Regex("(?<![\\p{L}\\p{N}:])(?:\\d+:)?\\d{1,2}:\\d{2}(?![\\p{L}\\p{N}:])").findAll(plain.toString()).forEach {
        VideoLinks.timestampSeconds(it.value)?.let { seconds -> add(it.range.first, it.range.last + 1, "/watch?v=$video&t=$seconds") }
    }
    Regex("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_]+", RegexOption.IGNORE_CASE).findAll(plain.toString()).forEach {
        val base = server.toHttpUrlOrNull()
        if (base != null) add(it.range.first, it.range.last + 1, base.newBuilder().addPathSegment("hashtag").addPathSegment(it.value.drop(1)).build().toString())
    }
    return RichContent(builder.toAnnotatedString(), emoji)
}

@Composable
internal fun RichCommentText(comment: Comment, server: String, video: String, link: (String) -> Unit,
    collapsedLines: Int = 6, tag: String = "comment-body-${comment.key}",
    expansionControl: (@Composable (expanded: Boolean, toggle: () -> Unit) -> Unit)? = null) =
    NativeRichText(comment.text, comment.html, comment.key, server, video, link, collapsedLines, tag, expansionControl)

@Composable
internal fun NativeRichText(text: String, html: String, key: String, server: String, video: String, link: (String) -> Unit,
    collapsedLines: Int = Int.MAX_VALUE, tag: String = "rich-text",
    expansionControl: (@Composable (expanded: Boolean, toggle: () -> Unit) -> Unit)? = null) {
    val color = MaterialTheme.colorScheme.primary
    val onLink by rememberUpdatedState(link)
    val content = remember(html, text, server, video, color) {
        runCatching { richContent(text, html, server, video, color) { onLink(it) } }
            .getOrElse { RichContent(AnnotatedString(text), emptyMap()) }
    }
    var expanded by rememberSaveable(server, key, tag, collapsedLines) { mutableStateOf(false) }
    var truncated by remember(server, key, text, html, collapsedLines) { mutableStateOf(false) }
    val inline = content.emoji.mapValues { (_, emoji) ->
        var failed by remember(emoji.url) { mutableStateOf(false) }
        val width = if (failed) (emoji.alt.length * .6f).coerceIn(1.2f, 12f).em else 1.2.em
        InlineTextContent(Placeholder(width, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
            if (failed) Text(emoji.alt, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            else AsyncImage(emoji.url, emoji.alt, Modifier.fillMaxSize(), onError = { failed = true })
        }
    }
    SelectionContainer {
        Text(content.text, modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyMedium, inlineContent = inline,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow })
    }
    if (collapsedLines != Int.MAX_VALUE && (truncated || expanded)) {
        val toggle = { expanded = !expanded }
        if (expansionControl != null) expansionControl(expanded, toggle)
        else TextButton(onClick = toggle, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(if (expanded) "Show less" else "Read more")
        }
    }
}

private fun commentCount(value: Long): String = DisplayFormats.compact(value)
