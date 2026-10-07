package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

enum class CommentSort(val apiValue: String, val label: String) { TOP("top", "Top"), NEWEST("new", "Newest") }
data class CreatorHeart(val name: String, val thumbnail: String)
data class Comment(val author: String, val text: String, val published: String, val likes: Long,
    val id: String = "", val authorId: String = "", val authorUrl: String = "", val avatar: String = "",
    val html: String = "", val edited: Boolean = false, val verified: Boolean = false,
    val creator: Boolean = false, val pinned: Boolean = false, val member: Boolean = false,
    val heart: CreatorHeart? = null, val replyCount: Int = 0, val replyContinuation: String = "") {
    // Old instances/fixtures can omit IDs. Keep a deterministic key when returning to a loaded list.
    val key: String by lazy { id.ifBlank {
        "legacy:" + java.security.MessageDigest.getInstance("SHA-256")
            .digest("$authorId\u0000$author\u0000$published\u0000$text".toByteArray()).joinToString("") { "%02x".format(it) }
    } }
}
data class CommentPage(val items: List<Comment>, val continuation: String = "", val count: Long? = null) {
    companion object {
        fun parse(json: JSONObject): CommentPage = CommentPage(json.optJSONArray("comments")?.objects().orEmpty().map { c ->
            val replies = c.optJSONObject("replies")
            Comment(c.text("author").ifBlank { "Unknown author" }, c.text("content"), c.text("publishedText"), c.optLong("likeCount").coerceAtLeast(0),
                c.text("commentId"), c.text("authorId"), c.text("authorUrl"), Avatars.parse(c),
                c.text("contentHtml"), c.optBoolean("isEdited"), c.optBoolean("verified"),
                c.optBoolean("authorIsChannelOwner"), c.optBoolean("isPinned"), c.optBoolean("isSponsor"),
                c.optJSONObject("creatorHeart")?.let { CreatorHeart(it.text("creatorName"), it.text("creatorThumbnail")) },
                replies?.optInt("replyCount")?.coerceAtLeast(0) ?: 0, replies?.text("continuation").orEmpty())
        }, json.text("continuation"), (json.opt("commentCount") as? Number)?.toLong()?.takeIf { it >= 0 })
    }
}

data class CommentPosition(val index: Int = 0, val offset: Int = 0)
data class CommentFeed(val page: CommentPage = CommentPage(emptyList()), val loaded: Boolean = false,
    val loading: Boolean = false, val loadingMore: Boolean = false, val error: String? = null,
    val position: CommentPosition = CommentPosition())
data class CommentThread(val parent: Comment, val feed: CommentFeed = CommentFeed())
sealed interface CommentTarget {
    data class Video(val id: String) : CommentTarget
    data class Post(val id: String, val channelId: String) : CommentTarget
}
data class CommentsState(val target: CommentTarget? = null, val context: ApiContext? = null, val open: Boolean = false,
    val sort: CommentSort = CommentSort.TOP, val feed: CommentFeed = CommentFeed(),
    val threadKey: String? = null, val threads: Map<String, CommentThread> = emptyMap()) {
    val videoId: String? get() = (target as? CommentTarget.Video)?.id
    val thread: CommentThread? get() = threads[threadKey]
}

/** Session-only public comments, independent of player and account mutations. Calls come from the UI scope. */
class CommentsController(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (CommentTarget, CommentSort, String, ApiContext) -> CommentPage,
    private val errorMessage: (Exception) -> String) {
    val state = MutableStateFlow(CommentsState())
    private var revision = 0L
    private val jobs = mutableMapOf<String, Job>()

    fun restore(saved: CommentsState) {
        revision++; jobs.values.forEach { it.cancel() }; jobs.clear()
        state.value = if (saved.context == context()) saved.copy(open = false,
            feed = saved.feed.copy(loading = false, loadingMore = false),
            threads = saved.threads.mapValues { (_, thread) -> thread.copy(feed = thread.feed.copy(loading = false, loadingMore = false)) })
        else CommentsState(context = context())
    }

    fun bind(videoId: String?, enabled: Boolean) = bind(videoId?.takeIf { enabled }?.let(CommentTarget::Video))
    fun bind(target: CommentTarget?) {
        val currentContext = context()
        if (state.value.target == target && state.value.context == currentContext) return
        revision++; jobs.values.forEach { it.cancel() }; jobs.clear()
        state.value = CommentsState(target = target, context = currentContext)
    }
    fun open() {
        if (state.value.target == null || state.value.context != context()) return
        state.value = state.value.copy(open = true)
        if (!state.value.feed.loaded) load()
        state.value.threadKey?.let { if (state.value.thread?.feed?.loaded != true) load(threadKey = it) }
    }
    fun close() { state.value = state.value.copy(open = false) }
    fun back(): Boolean {
        if (!state.value.open) return false
        if (state.value.threadKey != null) state.value = state.value.copy(threadKey = null) else close()
        return true
    }
    fun sort(value: CommentSort) {
        if (!state.value.open || state.value.context != context() || value == state.value.sort) return
        revision++; jobs.values.forEach { it.cancel() }; jobs.clear()
        state.value = state.value.copy(sort = value, feed = CommentFeed(), threadKey = null, threads = emptyMap())
        if (state.value.open) load()
    }
    fun replies(parent: Comment) {
        val current = state.value
        if (!current.open || current.context != context() || parent.replyContinuation.isBlank() || parent !in current.feed.page.items) return
        state.value = current.copy(threadKey = parent.key,
            threads = current.threads + (parent.key to (current.threads[parent.key] ?: CommentThread(parent))))
        if (state.value.thread?.feed?.loaded != true) load(threadKey = parent.key)
    }
    fun position(threadKey: String?, position: CommentPosition) {
        val current = feed(threadKey) ?: return
        if (current.position != position) replace(threadKey, current.copy(position = position))
    }
    private fun feed(key: String?): CommentFeed? = if (key == null) state.value.feed else state.value.threads[key]?.feed
    private fun replace(key: String?, feed: CommentFeed) {
        val current = state.value
        if (key == null) state.value = current.copy(feed = feed)
        else current.threads[key]?.let { state.value = current.copy(threads = current.threads + (key to it.copy(feed = feed))) }
    }
    fun load(more: Boolean = false, threadKey: String? = null) {
        val current = state.value
        val target = current.target ?: return
        val capturedContext = current.context ?: return
        val before = feed(threadKey) ?: return
        if (capturedContext != context() || before.loading || before.loadingMore || more && before.page.continuation.isBlank()) return
        val token = if (more) before.page.continuation else threadKey?.let { current.threads[it]?.parent?.replyContinuation }.orEmpty()
        val capturedRevision = revision
        fun active() = revision == capturedRevision && state.value.target == target && context() == capturedContext
        replace(threadKey, before.copy(loading = !more, loadingMore = more, error = null))
        val jobKey = threadKey?.let { "thread:$it" } ?: "feed"
        jobs[jobKey] = scope.launch {
            try {
                val result = fetch(target, current.sort, token, capturedContext)
                if (active()) {
                    val latest = feed(threadKey) ?: return@launch
                    val items = (if (more) latest.page.items + result.items else result.items).distinctBy { it.key }
                    // Counts normally exist only on the first top-level response.
                    val nextToken = result.continuation.takeUnless { it.isNotEmpty() && it == token }.orEmpty()
                    replace(threadKey, latest.copy(page = result.copy(items = items, continuation = nextToken,
                        count = result.count ?: latest.page.count), loaded = true, loading = false, loadingMore = false))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (active()) feed(threadKey)?.let { replace(threadKey, it.copy(loading = false, loadingMore = false, error = errorMessage(e))) } }
        }
    }
}

sealed interface CommentLink {
    data class Seek(val seconds: Long) : CommentLink
    data class Video(val link: VideoLink) : CommentLink
    data class Channel(val id: String) : CommentLink
    data class ChannelAlias(val link: ChannelLink) : CommentLink
    data class Clip(val link: ClipLink) : CommentLink
    data class Hashtag(val tag: String) : CommentLink
    data class Post(val link: PostLink) : CommentLink
    data class External(val url: String) : CommentLink
}
object CommentLinks {
    fun resolve(raw: String, instance: String, currentVideo: String): CommentLink? = when (val target = ContentLinks.resolve(raw, instance, currentVideo)) {
        is ContentLink.Clip -> CommentLink.Clip(target.link)
        is ContentLink.Seek -> CommentLink.Seek(target.milliseconds / 1000)
        is ContentLink.Video -> CommentLink.Video(target.link)
        is ContentLink.Channel -> target.link.id?.let { CommentLink.Channel(it) } ?: CommentLink.ChannelAlias(target.link)
        is ContentLink.Post -> CommentLink.Post(target.link)
        is ContentLink.Hashtag -> CommentLink.Hashtag(target.tag)
        is ContentLink.External -> CommentLink.External(target.url)
        null -> null
    }
}
