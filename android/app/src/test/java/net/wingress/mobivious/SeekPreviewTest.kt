package net.wingress.mobivious

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeekPreviewTest {
    private val track = StoryboardTrack("/api/v1/storyboards/video?width=320", 320, 180)
    private val context = ApiContext("https://instance.test", null)
    private val cues = listOf(
        StoryboardFrame(0, 10_000, "a", 0, 0, 320, 180),
        StoryboardFrame(10_000, 20_000, "a", 320, 0, 320, 180),
        StoryboardFrame(20_000, 30_000, "b", 0, 0, 320, 180),
        StoryboardFrame(30_000, 60_000, "c", 0, 0, 320, 180))

    private inner class Fixture(val scope: TestScope) {
        val cache = mutableMapOf<String, String>()
        val requests = mutableListOf<Pair<Long, String>>()
        var indexes = 0
        var latency = 0L
        var fail = false
        var uncancellable = false
        val controller = SeekPreviewController(scope, { _, _ -> indexes++; cues }, { cache[it] }, { url ->
            requests.add(scope.testScheduler.currentTime to url)
            if (uncancellable) withContext(NonCancellable) { delay(latency) } else delay(latency)
            if (fail) null else url.also { cache[url] = it }
        }, { _, frame -> StoryboardRules.fits(frame, 640, 180) }, { scope.testScheduler.currentTime })
        fun bind(occurrence: String = "first", clipStart: Long = 0) = controller.bind(SeekPreviewBinding(context, "video", occurrence, track, 60_000, clipStart))
        fun tap(target: Long) = controller.begin(SeekPreviewKind.TAP, target)
        fun drag(target: Long) = controller.begin(SeekPreviewKind.DRAG, target)
    }

    @Test fun highestResolutionAndClipEditorTrackSelectionAreIndependent() {
        val small = StoryboardTrack("small", 160, 90)
        assertEquals(track, StoryboardRules.track(listOf(small, track, StoryboardTrack("bad", 0, 9000))))
        assertEquals(small, StoryboardRules.track(listOf(track, small), sharpest = false))
        assertNull(StoryboardRules.track(listOf(StoryboardTrack("", 320, 180))))
    }
    @Test fun cueBoundariesClipOffsetsEndClampAndCropBoundsAreExact() {
        assertEquals(cues[0], StoryboardRules.frame(cues, 9999))
        assertEquals(cues[1], StoryboardRules.frame(cues, 10_000))
        assertNull(StoryboardRules.frame(cues, 60_000))
        assertEquals(42_788L, StoryboardRules.sourcePosition(30_444, 30_444, 12_345))
        assertEquals(12_345L, StoryboardRules.sourcePosition(-100, 30_444, 12_345))
        assertTrue(StoryboardRules.fits(cues[1], 640, 180))
        assertFalse(StoryboardRules.fits(cues[1], 639, 180))
        assertFalse(StoryboardRules.fits(cues[0].copy(x = Int.MAX_VALUE), 640, 180))
    }
    @Test fun vttRejectsForeignAssetsInvalidTimesAndInvalidRectanglesAndSortsCues() {
        fun cue(start: String, end: String, asset: String) = "$start --> $end\n$asset\n\n"
        val text = "WEBVTT\n\n" + cue("00:00:10.000", "00:00:20.000", "../s.jpg#xywh=320,0,320,180") +
            cue("00:00:00.000", "00:00:10.000", "/sb/a.jpg#xywh=0,0,320,180") +
            cue("00:00:20.000", "00:00:30.000", "https://cdn.test/s.jpg#xywh=0,0,320,180") +
            cue("00:60:00.000", "01:00:10.000", "/sb/b.jpg#xywh=0,0,320,180") +
            cue("00:00:20.000", "00:00:30.000", "/sb/b.jpg#xywh=0,0,0,180")
        val frames = StoryboardVtt.parse(text, "${context.server}/api/v1/storyboards/video")
        assertEquals(listOf(0L, 10_000L), frames.map { it.startMs })
        assertEquals("${context.server}/api/v1/s.jpg", frames[1].url)
    }
    @Test fun indexLoadingIsLazyAndCachedSheetCropsFollowEveryTap() = runTest {
        val f = Fixture(this); f.bind(); runCurrent()
        assertEquals(0, f.indexes)
        f.tap(1000); runCurrent()
        assertEquals(listOf(0L to "a"), f.requests)
        f.tap(15_000); runCurrent()
        assertEquals(cues[1], f.controller.state.value.frame)
        assertEquals(1, f.indexes); assertEquals(1, f.requests.size)
        f.controller.finish()
    }
    @Test fun tapTimerDownloadsOnlyTheNewestTargetAt250Milliseconds() = runTest {
        val f = Fixture(this); f.bind(); f.tap(1000); runCurrent()
        advanceTimeBy(100); f.tap(25_000); runCurrent()
        advanceTimeBy(100); f.tap(35_000); runCurrent()
        assertEquals(cues[0], f.controller.state.value.frame)
        advanceTimeBy(49); runCurrent(); assertEquals(1, f.requests.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(0L to "a", 250L to "c"), f.requests)
        assertEquals(cues[3], f.controller.state.value.frame)
        f.controller.finish()
    }
    @Test fun tapBurstsShareTheirDownloadTimerAndFinishingDropsQueuedDownloads() = runTest {
        val f = Fixture(this); f.bind(); f.tap(1000); runCurrent(); f.controller.finish()
        advanceTimeBy(100); f.tap(25_000); runCurrent()
        assertEquals(1, f.requests.size)
        f.controller.finish(); advanceTimeBy(5000); runCurrent()
        assertEquals(1, f.requests.size); assertNull(f.controller.state.value.sheet)
        f.tap(25_000); runCurrent(); assertEquals(2, f.requests.size)
        f.controller.finish()
    }
    @Test fun draggingBypassesTapTimerAndDoesNotRedownloadTheSameSheet() = runTest {
        val f = Fixture(this); f.bind(); f.tap(1000); runCurrent()
        advanceTimeBy(100); f.drag(25_000); runCurrent()
        f.drag(26_000); runCurrent(); f.drag(35_000); runCurrent()
        assertEquals(listOf(0L to "a", 100L to "b", 100L to "c"), f.requests)
        assertEquals(cues[3], f.controller.state.value.frame)
        f.controller.finish()
    }
    @Test fun cachedFramesCanChangeWhileAnotherSheetIsDownloadingAndLateSheetsCannotReplaceThem() = runTest {
        val f = Fixture(this); f.bind(); f.cache["c"] = "c"; f.latency = 1000
        f.drag(25_000); runCurrent()
        f.drag(35_000); runCurrent()
        assertEquals(cues[3], f.controller.state.value.frame)
        advanceTimeBy(1000); runCurrent()
        assertEquals(cues[3], f.controller.state.value.frame)
        assertEquals(1, f.requests.size)
        f.controller.finish()
    }
    @Test fun manyChangesDuringOneDownloadAreDeduplicatedAndUseTheLatestCrop() = runTest {
        val f = Fixture(this); f.bind(); f.latency = 1000
        f.drag(1000); runCurrent()
        repeat(10) { f.drag(15_000); runCurrent() }
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, f.requests.size); assertEquals(cues[1], f.controller.state.value.frame)
        f.controller.finish()
    }
    @Test fun newOccurrenceAndDisabledBindingRejectLateImagesEvenFromUncancellableLoads() = runTest {
        val f = Fixture(this); f.bind(); f.latency = 1000; f.uncancellable = true
        f.tap(1000); runCurrent(); f.bind("second")
        advanceTimeBy(1000); runCurrent(); assertNull(f.controller.state.value.sheet)
        f.controller.bind(null); f.drag(25_000); advanceUntilIdle()
        assertEquals(1, f.requests.size)
    }
    @Test fun clipPreviewSelectsTheSourceFrameAndFailuresDoNotLoop() = runTest {
        val f = Fixture(this); f.bind(clipStart = 12_345); f.tap(0); runCurrent()
        assertEquals(cues[1], f.controller.state.value.frame)
        f.fail = true; f.drag(12_000); runCurrent()
        repeat(5) { f.drag(13_000); runCurrent() }
        assertEquals(2, f.requests.size)
        assertNull(f.controller.state.value.sheet)
        f.controller.finish()
    }
    @Test fun playerAndClipEditorShareCompletedIndexesButSeparateInstancesDoNot() = runTest {
        var calls = 0
        val repository = StoryboardRepository { _, _ -> calls++; cues }
        assertEquals(cues, repository.frames(track, context))
        assertEquals(cues, repository.frames(track, context))
        repository.frames(track, context.copy(server = "https://other.test"))
        assertEquals(2, calls)
    }
    @Test fun temporarilyDisablingPreviewsDoesNotResetTheTapBudgetButANewOccurrenceDoes() = runTest {
        val f = Fixture(this); f.bind(); f.tap(1000); runCurrent()
        f.controller.bind(null); advanceTimeBy(100); f.bind(); f.tap(25_000); runCurrent()
        assertEquals(1, f.requests.size)
        f.bind("new"); f.tap(25_000); runCurrent()
        assertEquals(listOf(0L to "a", 100L to "b"), f.requests)
        f.controller.finish()
    }
    @Test fun failedIndexesRetryOnTheNextInteractionWithoutLoopingWithinTheCurrentOne() = runTest {
        var calls = 0
        val controller = SeekPreviewController(this, { _, _ ->
            if (++calls == 1) error("temporarily unavailable") else cues
        }, { "cached" }, { _: String -> error("No download needed") }, { _: String, _ -> true }, { testScheduler.currentTime })
        controller.bind(SeekPreviewBinding(context, "video", "first", track, 60_000))
        controller.begin(SeekPreviewKind.DRAG, 1000); runCurrent()
        controller.update(15_000); runCurrent(); assertEquals(1, calls)
        controller.finish(); controller.begin(SeekPreviewKind.DRAG, 1000); runCurrent()
        assertEquals(2, calls); assertEquals("cached", controller.state.value.sheet)
        controller.finish()
    }
}
