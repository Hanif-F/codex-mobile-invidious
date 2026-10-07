package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.LinkPlaybackRules
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ContentLinksTest {
    private val server = "https://instance.test"
    private val id = "abcdefghijk"
    private val channel = "UC" + "a".repeat(22)

    @Test fun aliasesPreservePreciseTimesAndNormalizeOnlyYoutubeIndexes() {
        for (route in listOf("watch", "w", "v", "e", "shorts", "live", "embed")) {
            val link = VideoLinks.parse("$server/$route/$id?list=PLtest&index=3&time_continue=12.345&end=1m", server)!!
            assertEquals(id, link.id); assertEquals(12345L, link.startMs); assertEquals(60000L, link.playback.endMs); assertEquals(3, link.index)
        }
        assertEquals(2, VideoLinks.parse("https://www.youtube.com/watch?v=$id&list=PLtest&index=3", server)!!.index)
        assertEquals(0L, VideoLinks.parse("$server/watch?v=$id&t=0&time_continue=8&start=9#t=10", server)!!.startMs)
        assertEquals(10000L, VideoLinks.parse("https://youtu.be/$id#t=10", server)!!.startMs)
    }
    @Test fun unsafeOriginsPathsAndOverflowAreRejected() {
        for (url in listOf("https://youtube.com.evil.test/watch?v=$id", "https://user:secret@youtube.com/watch?v=$id",
            "https://youtube.com:8443/watch?v=$id", "https://instance.test:8443/watch?v=$id", "http://instance.test/watch?v=$id",
            "$server/channel/$channel?v=$id", "$server/watch/../../$id", "$server/clip/test?v=$id", "javascript:void(0)"))
            assertNull(url, VideoLinks.parse(url, server))
        for (time in listOf("-1", "NaN", "Infinity", "999999999999999999999h", "1h9223372036854775807s", "1:99", "1foo")) assertNull(time, VideoLinks.timestampMillis(time))
        assertEquals(3723500L, VideoLinks.timestampMillis("1h2m3s500ms"))
        assertEquals(3723000L, VideoLinks.timestampMillis("1:02:03"))
    }
    @Test fun sharedAndRichLinksResolveChannelsTabsPostsAndHashtags() {
        assertEquals(ContentLink.Channel(ChannelLink(channel, tab = ChannelTab.POSTS)), ContentLinks.parse("$server/channel/$channel/community", server))
        for (path in listOf("@creator", "c/Creator", "user/Creator")) {
            val target = ContentLinks.parse("https://www.youtube.com/$path/shorts", server) as ContentLink.Channel
            assertEquals("https://www.youtube.com/$path", target.link.resolveUrl); assertEquals(ChannelTab.SHORTS, target.link.tab)
        }
        assertEquals(ContentLink.Hashtag("日本語"), ContentLinks.resolve("/hashtag/%E6%97%A5%E6%9C%AC%E8%AA%9E", server))
        assertEquals(ContentLink.Seek(12345), ContentLinks.resolve("/watch?v=$id&t=12.345", server, id))
        assertTrue(ContentLinks.resolve("/watch?v=$id&t=12&end=20", server, id) is ContentLink.Video)
        assertTrue(ContentLinks.resolve("/post/Ugpost1", server) is ContentLink.Post)
        assertTrue(ContentLinks.resolve("/redirect?q=https%3A%2F%2Fexample.com%2Fsource", server) is ContentLink.External)
        assertNull(ContentLinks.resolve("https://user:secret@example.com/", server))
        assertEquals(ChannelTab.CLIPS, (ContentLinks.resolve("/@creator/clips", server) as ContentLink.Channel).link.tab)
    }
    @Test fun nullableOptionsKeepExplicitFalseAndNeverChangeSavedDefaults() {
        val link = VideoLinks.parse("$server/watch?v=$id&listen=0&autoplay=false&local=0&speed=1.25x&loop=1&quality_dash=720p&video_codec=h264&subtitles=&comments=reddit&region=id&save_player_pos=0&related_videos=false&extend_desc=1&continue=0&continue_autoplay=0", server)!!
        val options = link.playback.options
        val saved = AccountPreferences(listen = true, local = true, savePosition = true, captions = listOf("English"))
        val effective = options.apply(saved)
        assertFalse(effective.listen); assertFalse(effective.local); assertFalse(effective.autoplay); assertFalse(effective.savePosition)
        assertTrue(effective.videoLoop); assertEquals(1.25f, effective.speed); assertEquals("720p", effective.qualityDash)
        assertEquals("h264", effective.videoCodec); assertTrue(effective.captions.isEmpty()); assertFalse(effective.showYoutubeComments)
        assertEquals("ID", effective.region); assertFalse(effective.relatedVideos); assertTrue(effective.extendDescription)
        assertFalse(effective.continueNext); assertFalse(effective.continueAutoplay)
        assertTrue(saved.listen); assertTrue(saved.savePosition); assertEquals(listOf("English"), saved.captions)
        assertEquals(options, PlaybackLinkOptions.parse(options.json()))
        assertEquals(PlaybackLinkOptions(), VideoLinks.parse("$server/watch?v=$id&speed=99&listen=maybe&quality_dash=nonsense&video_codec=vp9", server)!!.playback.options)
    }
    @Test fun boundsClampWithoutChangingAbsoluteCoordinates() {
        val bounds = LinkPlayback(10000, 50000).bounded(30000)
        assertEquals(30000L, bounds.endMs)
        assertEquals(30000L, LinkPlaybackRules.seek(40000, 120000, bounds.endMs))
        assertEquals(5000L, LinkPlaybackRules.seek(5000, 120000, bounds.endMs))
        assertEquals(0L, LinkPlaybackRules.seek(-1, 120000, bounds.endMs))
        assertNull(LinkPlayback(40000, 50000).bounded(30000).endMs)
        assertTrue(VideoLinks.parse("$server/watch?v=$id&t=10&end=9", server)!!.playback.invalidEnd)
        assertEquals(LinkPlayback(12345, 23456), LinkPlayback.parse(LinkPlayback(12345, 23456).json()))
    }
    @Test fun sharesUseSourceOccurrencesForDuplicatesAndOmitContextForInsertedVideos() {
        val options = PlaybackLinkOptions(listen = true, speed = 1.25f, local = false, loop = true)
        val first = QueueOccurrence.source("PLtest", Video(id, "First"), 0)
        val second = QueueOccurrence.source("PLtest", Video(id, "Second"), 8).copy(linkPlayback = LinkPlayback(5000, 20000, options))
        val queue = PlaybackQueueSnapshot(source = QueueSource("PLtest"), items = listOf(first, second), currentKey = second.key, carriedOptions = options.carried())
        val shared = VideoLinks.parse(VideoLinks.share(server, queue, 12345)!!, server)!!
        assertEquals(8, shared.index); assertEquals("PLtest", shared.playlistId); assertEquals(12345L, shared.startMs)
        assertEquals(20000L, shared.playback.endMs); assertEquals(options, shared.playback.options)
        val inserted = QueueOccurrence.local(Video("bbbbbbbbbbb", "Inserted"))
        val standalone = VideoLinks.parse(VideoLinks.share(server, queue.copy(items = queue.items + inserted, currentKey = inserted.key), 1000)!!, server)!!
        assertNull(standalone.playlistId); assertNull(standalone.index)
        val mixEntry = second.copy(key = "RDmix:8")
        val mix = VideoLinks.parse(VideoLinks.share(server, queue.copy(source = QueueSource("RDmix"), items = listOf(mixEntry), currentKey = mixEntry.key), 20000)!!, server)!!
        assertEquals(id, mix.seedVideoId); assertTrue(mix.startMs!! < mix.playback.endMs!!)
        val unresolvedSeed = second.copy(key = "unresolved-seed")
        val seedShare = VideoLinks.parse(VideoLinks.share(server, queue.copy(items = listOf(unresolvedSeed), currentKey = unresolvedSeed.key), 1000)!!, server)!!
        assertNull(seedShare.playlistId)
        assertNull(VideoLinks.share(server, queue.copy(context = ApiContext("https://previous.test", null)), 1000))
    }
    @Test fun onlyModeSpeedAndProxyCarryToOtherOccurrences() {
        val options = PlaybackLinkOptions(listen = true, speed = 1.5f, local = false, quality = "720p", loop = true, description = true)
        val entry = QueueOccurrence.local(Video(id, "Video")).copy(linkPlayback = LinkPlayback(1000, 2000, options))
        val next = QueueOccurrence.local(Video("bbbbbbbbbbb", "Next"))
        val state = PlaybackQueueSnapshot(items = listOf(entry, next), currentKey = next.key, carriedOptions = options.carried())
        val effective = state.effective(AccountPreferences())
        assertTrue(effective.listen); assertEquals(1.5f, effective.speed); assertFalse(effective.local)
        assertEquals("auto", effective.qualityDash); assertFalse(effective.videoLoop); assertFalse(effective.extendDescription)
        assertNull(state.current?.linkPlayback)
    }
    @Test fun publicResolutionAndHashtagsUseSelectedInstanceAndEncodedValues() = runBlocking {
        MockWebServer().use { mock ->
            mock.start(); val address = mock.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("private-token", "Viewer", Long.MAX_VALUE, address) })
            mock.enqueue(MockResponse().setBody("""{"ucid":"$channel"}"""))
            assertEquals(channel, api.resolveChannel(ChannelLink(resolveUrl = "https://www.youtube.com/@creator")))
            val resolve = mock.takeRequest(); assertNull(resolve.getHeader("Authorization")); assertEquals("https://www.youtube.com/@creator", resolve.requestUrl!!.queryParameter("url"))
            mock.enqueue(MockResponse().setBody("""{"results":[{"videoId":"$id","title":"Hashtag video"}]}"""))
            assertEquals(id, api.hashtag("日本語%&", 2).single().id)
            val hashtag = mock.takeRequest(); assertNull(hashtag.getHeader("Authorization")); assertEquals("/api/v1/hashtag/日本語%&", hashtag.requestUrl!!.pathSegments.joinToString("/", prefix = "/")); assertEquals("2", hashtag.requestUrl!!.queryParameter("page"))
            mock.enqueue(MockResponse().setBody("""{"browseId":"invalid"}"""))
            try { api.resolveChannel(ChannelLink(resolveUrl = "https://www.youtube.com/c/Creator")); fail("Invalid channel accepted") } catch (e: ApiException) { assertEquals(404, e.status) }
        }
    }
    @Test fun delayedResolutionCannotCrossInstanceChanges() = runBlocking {
        MockWebServer().use { mock ->
            mock.start(); var address = mock.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null })
            mock.enqueue(MockResponse().setBody("""{"ucid":"$channel"}""").setBodyDelay(150, TimeUnit.MILLISECONDS))
            val pending = async(Dispatchers.IO) { api.resolveChannel(ChannelLink(resolveUrl = "https://www.youtube.com/@creator")) }
            assertNotNull(mock.takeRequest(2, TimeUnit.SECONDS)); address = server
            try { pending.await(); fail("Stale resolution accepted") } catch (_: CancellationException) { }
        }
    }
}
