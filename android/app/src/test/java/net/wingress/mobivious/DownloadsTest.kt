package net.wingress.mobivious

import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class DownloadsTest {
    private fun catalog() = DownloadCatalog.parse(JSONObject("""{"video":{"videoId":"testvideo01","title":"How to ride a bike","author":"Cyclist","lengthSeconds":12},"allowed":true,"choices":[{"key":"v360","kind":"video","url":"/download?key=v360","itag":"137","type":"video/mp4","size":"640x360","clen":"1000"},{"key":"aen","kind":"audio","url":"/download?key=aen","itag":"140","type":"audio/mp4","audioTrack":{"id":"en.1","displayName":"English"}},{"key":"aes","kind":"audio","url":"/download?key=aes","itag":"140","type":"audio/mp4","audioTrack":{"id":"es.1","displayName":"Spanish"}},{"key":"cen","kind":"caption","url":"/caption","label":"English","language_code":"en"}]}"""))
    @Test fun selectionsRequireMediaAndKeepExactIdentities() {
        val c = catalog()
        for (selection in listOf(DownloadSelection(), DownloadSelection(captions = setOf("cen")), DownloadSelection(video = "aen"), DownloadSelection(audio = "absent"))) {
            assertTrue(runCatching { selection.choices(c) }.isFailure)
        }
        assertEquals(listOf("v360", "aes", "cen"), DownloadSelection("v360", "aes", setOf("cen")).choices(c).map { it.key })
        assertEquals("es.1", c.choices.single { it.key == "aes" }.format.audio!!.id)
        assertEquals(1, DownloadSelection(audio = "aen").choices(c).size)
    }
    @Test fun repeatDownloadsCreateIndependentEntriesAndRoundTripAllAssets() {
        val first = DownloadRecord.create("https://source.test", catalog(), DownloadSelection("v360", "aen", setOf("cen")), 100)
        val second = DownloadRecord.create("https://source.test", catalog(), DownloadSelection("v360", "aen", setOf("cen")), 101)
        assertNotEquals(first.id, second.id)
        assertEquals(first, DownloadRecord.parse(first.json()))
        assertFalse(first.playable)
        val partial = first.copy(assets = first.assets.map { if (it.choice.kind == DownloadKind.VIDEO) it.copy(status = DownloadStatus.COMPLETE) else it.copy(status = DownloadStatus.FAILED) })
        assertFalse(partial.playable)
        val ready = first.copy(assets = first.assets.map { if (it.media) it.copy(status = DownloadStatus.COMPLETE, bytes = 100) else it.copy(status = DownloadStatus.FAILED) })
        assertTrue(ready.playable); assertEquals(200, ready.bytes)
        assertEquals(ready.copy(positionMs = 5000), DownloadRecord.parse(ready.copy(positionMs = 5000).json()))
    }
    @Test fun restrictedCatalogAndCombinedOrInvalidChoicesCannotBeSelected() {
        val c = catalog().copy(allowed = false, reason = "Disabled")
        assertTrue(runCatching { DownloadSelection(video = "v360").choices(c) }.isFailure)
        val combined = JSONObject("""{"key":"18","kind":"combined","type":"video/mp4","url":"/combined"}""")
        assertNull(DownloadChoice.catalog(combined))
        assertNull(DownloadChoice.catalog(JSONObject("""{"key":"bad","kind":"video","type":"audio/mp4","url":"/bad"}""")))
    }
    @Test fun exportConsentBelongsToOnePendingDestinationAndSelection() {
        val pending = net.wingress.mobivious.downloads.DownloadExportState("first", null, "content://files/one", "Needs conversion")
        assertTrue(pending.allowsConversion("first", null, "content://files/one"))
        assertFalse(pending.allowsConversion("second", null, "content://files/one"))
        assertFalse(pending.allowsConversion("first", "caption", "content://files/one"))
        assertFalse(pending.allowsConversion("first", null, "content://files/two"))
        for (phase in listOf("Cancelled", "Interrupted", "Saved", "Failed", "Converting")) assertFalse(pending.copy(phase = phase).allowsConversion("first", null, "content://files/one"))
    }
    @Test fun progressIncludesAllSelectedFilesButNotArtworkAndKeepsUnknownTotalsIndeterminate() {
        val record = DownloadRecord.create("https://source.test", catalog(), DownloadSelection("v360", "aen"))
        val known = record.copy(assets = record.assets.map { it.copy(bytes = 25, total = 100) } + DownloadAsset(DownloadChoice("art", DownloadKind.AVATAR, "/avatar", "{}"), "art.jpg", bytes = 999, total = 999))
        assertEquals(50L, known.bytes); assertEquals(200L, known.total)
        assertEquals(0L, known.copy(assets = known.assets.map { if (it.media) it.copy(total = 0) else it }).total)
    }
}
