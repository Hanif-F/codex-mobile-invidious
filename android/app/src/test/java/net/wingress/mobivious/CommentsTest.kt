package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class CommentsTest {
    private val video = "abcdefghijk"
    private val channel = "UC" + "a".repeat(22)
    private val context = ApiContext("https://instance.test", null)
    private fun comment(id: String, replies: String = "") = Comment("Viewer", "Body $id", "today", 3, id = id,
        authorId = channel, replyCount = if (replies.isEmpty()) 0 else 2, replyContinuation = replies)

    @Test fun modernCommentsPreserveRichContentMetadataAndReplies() {
        val page = CommentPage.parse(JSONObject("""{"commentCount":1234,"continuation":"page+/%&=","comments":[{"commentId":"parent","author":"Creator","authorId":"$channel","authorUrl":"/channel/$channel","authorThumbnail":"/avatar","content":"Hello 😀","contentHtml":"<b>Hello</b> 😀","publishedText":"today","likeCount":999,"verified":true,"isEdited":true,"isPinned":true,"isSponsor":true,"authorIsChannelOwner":true,"creatorHeart":{"creatorName":"Studio","creatorThumbnail":"/heart"},"replies":{"replyCount":2,"continuation":"reply+/%&="}}]}"""))
        val c = page.items.single()
        assertEquals(1234L, page.count); assertEquals("page+/%&=", page.continuation)
        assertEquals("parent", c.key); assertEquals(channel, c.authorId); assertEquals("/avatar", c.avatar)
        assertEquals("<b>Hello</b> 😀", c.html); assertEquals(999L, c.likes)
        assertTrue(c.edited && c.verified && c.pinned && c.member && c.creator)
        assertEquals(CreatorHeart("Studio", "/heart"), c.heart)
        assertEquals(2, c.replyCount); assertEquals("reply+/%&=", c.replyContinuation)
    }
    @Test fun legacyCommentsAndMissingOptionalFieldsRemainReadable() {
        val c = CommentPage.parse(JSONObject("""{"comments":[{"author":"Viewer","content":"Plain","authorThumbnails":[{"url":"small"},{"url":"large"}],"likeCount":-1}]}""")).items.single()
        assertEquals("large", c.avatar); assertEquals("Plain", c.text); assertEquals(0L, c.likes)
        assertEquals("", c.html); assertEquals("", c.replyContinuation); assertNull(c.heart)
        assertEquals(c.key, c.copy().key)
        val empty = CommentPage.parse(JSONObject("""{"comments":[],"commentCount":0}"""))
        assertEquals(0L, empty.count)
        assertNull(CommentPage.parse(JSONObject("{}" )).count)
        assertEquals("Unknown author", CommentPage.parse(JSONObject("""{"comments":[{}]}""")).items.single().author)
    }
    @Test fun linksResolveNativeChannelsVideosTimestampsAndExternalTargets() {
        assertEquals(CommentLink.Seek(30), CommentLinks.resolve("/watch?v=$video&t=30", context.server, video))
        assertEquals(CommentLink.Channel(channel), CommentLinks.resolve("/channel/$channel", context.server, video))
        assertEquals(CommentLink.Video(VideoLink("bbbbbbbbbbb", 60)), CommentLinks.resolve("https://youtu.be/bbbbbbbbbbb?t=1m", context.server, video))
        assertEquals(CommentLink.Video(VideoLink("", playlistId = "PLfixture")), CommentLinks.resolve("/playlist?list=PLfixture", context.server, video))
        assertEquals(CommentLink.External("https://example.com/path"), CommentLinks.resolve("/redirect?q=https%3A%2F%2Fexample.com%2Fpath", context.server, video))
        assertEquals(CommentLink.External("https://youtube.com.evil.test/watch?v=$video&t=30"), CommentLinks.resolve("https://youtube.com.evil.test/watch?v=$video&t=30", context.server, video))
        listOf("javascript:void(0)", "file:///private", "data:text/html,hello", "https://user:secret@example.com/").forEach {
            assertNull(CommentLinks.resolve(it, context.server, video))
        }
    }
    @Test fun commentsApiAlwaysUsesPublicYoutubeRequestsAndEncodesOpaqueTokens() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("private-token", "Viewer", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody("""{"comments":[],"commentCount":0}"""))
            assertEquals(0L, api.comments(video).count)
            val first = server.takeRequest()
            assertEquals("youtube", first.requestUrl!!.queryParameter("source"))
            assertEquals("top", first.requestUrl!!.queryParameter("sort_by")); assertNull(first.requestUrl!!.queryParameter("continuation"))
            assertNull(first.getHeader("Authorization"))
            val token = "reply+/page=2%&"
            server.enqueue(MockResponse().setBody("""{"comments":[],"continuation":"next"}"""))
            assertEquals("next", api.comments(video, CommentSort.NEWEST, token).continuation)
            val reply = server.takeRequest()
            assertEquals("/api/v1/comments/$video", reply.requestUrl!!.encodedPath)
            assertEquals("new", reply.requestUrl!!.queryParameter("sort_by")); assertEquals(token, reply.requestUrl!!.queryParameter("continuation"))
            assertNull(reply.getHeader("Authorization"))
        }
    }
    @Test fun commentsApiRejectsAResponseAfterTheInstanceChanges() = runBlocking {
        MockWebServer().use { server ->
            server.start(); var address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null })
            server.enqueue(MockResponse().setBody("""{"comments":[]}""").setBodyDelay(200, TimeUnit.MILLISECONDS))
            val request = async(Dispatchers.IO) { api.comments(video, context = api.context()) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); address = "https://another.test"
            try { request.await(); fail("Stale response accepted") } catch (_: CancellationException) { }
        }
    }
    @Test fun entryDoesNotFetchUntilOpenedAndCloseReopenKeepsTheListAndPosition() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            val store = CommentsController(scope, { context }, { _, _, _, _ -> requests++; CommentPage(listOf(comment("a")), count = 100) }, { it.message.orEmpty() })
            store.bind(video, true); assertFalse(store.state.value.open); assertEquals(0, requests)
            store.open(); assertEquals(1, requests); assertTrue(store.state.value.feed.loaded)
            store.position(null, CommentPosition(1, 32)); store.close(); store.open()
            assertEquals(1, requests); assertEquals(CommentPosition(1, 32), store.state.value.feed.position)
            store.bind("bbbbbbbbbbb", true)
            assertFalse(store.state.value.open); assertFalse(store.state.value.feed.loaded); assertEquals(CommentSort.TOP, store.state.value.sort)
        } finally { scope.cancel() }
    }
    @Test fun paginationRetainsRowsCountAndFailedCursorAndDeduplicatesOverlap() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val tokens = mutableListOf<String>(); var fail = true
            val store = CommentsController(scope, { context }, { _, _, token, _ ->
                tokens += token
                if (token.isEmpty()) CommentPage(listOf(comment("a")), "next", 20)
                else if (fail) throw IllegalStateException("Offline") else CommentPage(listOf(comment("a"), comment("b")))
            }, { it.message.orEmpty() })
            store.bind(video, true); store.open(); store.load(true)
            assertEquals("Offline", store.state.value.feed.error); assertEquals("next", store.state.value.feed.page.continuation)
            assertEquals(listOf("a"), store.state.value.feed.page.items.map { it.id })
            fail = false; store.load(true)
            assertEquals(listOf("", "next", "next"), tokens)
            assertEquals(listOf("a", "b"), store.state.value.feed.page.items.map { it.id })
            assertEquals(20L, store.state.value.feed.page.count); assertNull(store.state.value.feed.error)
            store.load(true); assertEquals(3, tokens.size)
        } finally { scope.cancel() }
    }
    @Test fun repliesHaveIndependentPagesErrorsAndCachedPositions() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val parent = comment("a", "replies"); val tokens = mutableListOf<String>(); var fail = false
            val store = CommentsController(scope, { context }, { _, _, token, _ ->
                tokens += token
                when (token) {
                    "" -> CommentPage(listOf(parent), "feed-more", 50)
                    "replies" -> CommentPage(listOf(comment("r1")), "reply-more")
                    else -> if (fail) throw IllegalStateException("Reply unavailable") else CommentPage(listOf(comment("r1"), comment("r2")))
                }
            }, { it.message.orEmpty() })
            store.bind(video, true); store.open(); store.position(null, CommentPosition(2, 12)); store.replies(parent)
            store.position(parent.key, CommentPosition(1, 20)); fail = true; store.load(true, parent.key)
            assertEquals("Reply unavailable", store.state.value.thread!!.feed.error); assertNull(store.state.value.feed.error)
            assertEquals("feed-more", store.state.value.feed.page.continuation)
            fail = false; store.load(true, parent.key)
            assertEquals(listOf("r1", "r2"), store.state.value.thread!!.feed.page.items.map { it.id })
            assertTrue(store.back()); assertNull(store.state.value.threadKey); assertEquals(CommentPosition(2, 12), store.state.value.feed.position)
            store.replies(parent); assertEquals(CommentPosition(1, 20), store.state.value.thread!!.feed.position)
            assertEquals(listOf("", "replies", "reply-more", "reply-more"), tokens)
            assertTrue(store.back()); assertTrue(store.back()); assertFalse(store.back())
        } finally { scope.cancel() }
    }
    @Test fun duplicateLoadingIsIgnoredAndLateSortResponsesCannotOverwriteNewest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<Unit>(); var requests = 0
            val store = CommentsController(scope, { context }, { _, sort, _, _ ->
                requests++
                if (sort == CommentSort.TOP) withContext(NonCancellable) { gate.await() }
                CommentPage(listOf(comment(sort.apiValue)))
            }, { it.message.orEmpty() })
            store.bind(video, true); store.open(); store.open(); store.load(); assertEquals(1, requests)
            store.sort(CommentSort.NEWEST); assertEquals(2, requests)
            gate.complete(Unit); yield()
            assertEquals(listOf("new"), store.state.value.feed.page.items.map { it.id })
            assertFalse(store.state.value.feed.loading)
        } finally { scope.cancel() }
    }
    @Test fun videoAccountAndVisibilityChangesDiscardPendingRepliesAndCloseTheDrawer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var active = context; val gate = CompletableDeferred<Unit>(); val parent = comment("a", "reply")
            val store = CommentsController(scope, { active }, { _, _, token, _ ->
                if (token == "reply") withContext(NonCancellable) { gate.await() }
                CommentPage(listOf(if (token.isEmpty()) parent else comment("late")))
            }, { it.message.orEmpty() })
            store.bind(video, true); store.open(); store.replies(parent)
            active = context.copy(generation = 1); store.bind(video, true)
            gate.complete(Unit); yield()
            assertFalse(store.state.value.open); assertTrue(store.state.value.threads.isEmpty()); assertTrue(store.state.value.feed.page.items.isEmpty())
            store.open(); store.bind(video, false)
            assertNull(store.state.value.videoId); assertFalse(store.state.value.open)
            store.open(); assertFalse(store.state.value.open)
            store.bind("bbbbbbbbbbb", true); assertFalse(store.state.value.feed.loaded)
            store.replies(parent); store.sort(CommentSort.NEWEST)
            assertTrue(store.state.value.threads.isEmpty()); assertEquals(CommentSort.TOP, store.state.value.sort)
        } finally { scope.cancel() }
    }
    @Test fun repeatedContinuationStopsRatherThanOfferingAnEndlessPage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = CommentsController(scope, { context }, { _, _, _, _ -> CommentPage(listOf(comment("a")), "same") }, { it.message.orEmpty() })
            store.bind(video, true); store.open(); store.load(true)
            assertEquals("", store.state.value.feed.page.continuation)
            assertEquals(1, store.state.value.feed.page.items.size)
        } finally { scope.cancel() }
    }
}
