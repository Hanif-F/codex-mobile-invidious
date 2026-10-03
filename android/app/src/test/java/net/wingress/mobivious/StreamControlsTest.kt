package net.wingress.mobivious

import androidx.media3.common.*
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamControlsTest {
    private fun video(id: String, height: Int, fps: Float, bitrate: Int, codec: String = "avc1.42c01e") = Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.VIDEO_H264).setCodecs(codec).setWidth(height * 16 / 9).setHeight(height)
        .setFrameRate(fps).setAverageBitrate(bitrate).build()
    private fun audio(id: String = "140", lang: String? = "en", label: String? = "English", bitrate: Int = 128_000, roles: Int = 0) = Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2").setLanguage(lang).setLabel(label)
        .setAverageBitrate(bitrate).setRoleFlags(roles).build()
    private fun tracks(vararg formats: Format, supported: IntArray = IntArray(formats.size) { C.FORMAT_HANDLED }, selected: BooleanArray = BooleanArray(formats.size)) =
        Tracks(listOf(Tracks.Group(TrackGroup("fixture", *formats), true, supported, selected)))
    private fun metadata(id: String, name: String, original: Boolean, drc: Boolean = false, lang: String = "en", bitrate: Long = 128_000) =
        StreamFormat(id, "audio/mp4", "mp4a.40.2", 0, 0, 0f, bitrate, 3_000_000, AudioIdentity("$lang.1", name, original), drc)

    @Test fun parsesOptionalMetadataAndNumericStrings() {
        val f = ApiParser.streamFormat(JSONObject("""{"itag":"140","type":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":"128000","clen":"3000000","audioTrack":{"id":"en.1","displayName":"English","audioIsDefault":false},"isDrc":true}"""))
        assertEquals("mp4a.40.2", f.codec); assertEquals(128_000L, f.bitrate); assertEquals(3_000_000L, f.bytes)
        assertEquals(false, f.audio?.default); assertEquals(true, f.drc)
        val missing = ApiParser.streamFormat(JSONObject("""{"clen":"-1","fps":"NaN","audioTrack":null}"""))
        assertEquals(0L, missing.bytes); assertEquals(0f, missing.fps); assertNull(missing.audio); assertNull(missing.drc)
    }
    @Test fun detailsRetainAdaptiveAndProgressiveFormats() {
        val d = ApiParser.details(JSONObject("""{"adaptiveFormats":[{"itag":"1","size":"1280x720","fps":60}],"formatStreams":[{"itag":"2","url":"/video"}]}"""))
        assertEquals(2, d.formats.size); assertEquals(720, d.formats.first().height); assertEquals("/video", d.fallback)
    }
    @Test fun duplicateAudioItagsUseLanguageAndStableVariant() {
        val original = metadata("140", "English", true)
        val stable = metadata("140", "English Stable Volume", true, true)
        val dubbed = metadata("140", "Spanish", false, lang = "es")
        val formats = listOf(original, stable, dubbed)
        assertEquals(original, StreamCatalog.metadata(audio(), formats))
        assertEquals(stable, StreamCatalog.metadata(audio(label = "English Stable Volume [128000k]"), formats))
        assertEquals(dubbed, StreamCatalog.metadata(audio(lang = "es", label = "Spanish"), formats))
        assertNull(StreamCatalog.metadata(audio(lang = null, label = null), listOf(original, dubbed)))
    }
    @Test fun ranksResolutionThenFpsThenBitrateAndAddsTierDetails() {
        val choices = StreamCatalog.choices(tracks(video("low", 720, 30f, 700_000), video("high", 720, 30f, 1_500_000),
            video("fps", 720, 60f, 900_000), video("1080", 1080, 24f, 2_000_000)), C.TRACK_TYPE_VIDEO)
        assertEquals(listOf("1080", "fps", "high", "low"), choices.map { it.key.id })
        assertTrue(choices[2].primary.contains("High Bitrate")); assertTrue(choices[3].primary.contains("Low Bitrate"))
        assertTrue(choices[2].primary.contains("30 FPS")); assertEquals("H.264 · 1.5 Mbps", choices[2].secondary)
    }
    @Test fun qualityDefaultsMatchWebIncludingUnavailableLowResolution() {
        val choices = StreamCatalog.choices(tracks(video("720", 720, 30f, 800_000), video("360", 360, 24f, 300_000)), C.TRACK_TYPE_VIDEO)
        assertNull(StreamCatalog.defaultVideo(choices, "auto"))
        assertEquals("720", StreamCatalog.defaultVideo(choices, "best")?.key?.id)
        assertEquals("360", StreamCatalog.defaultVideo(choices, "worst")?.key?.id)
        assertEquals("360", StreamCatalog.defaultVideo(choices, "480p")?.key?.id)
        assertEquals("360", StreamCatalog.defaultVideo(choices, "144p")?.key?.id)
        assertEquals("720", StreamCatalog.defaultVideo(choices, "4320p")?.key?.id)
        assertTrue(PreferenceRules.qualities.containsAll(listOf("best", "worst", "4320p", "240p", "144p")))
    }
    @Test fun onlySupportedRepresentationsAreOffered() {
        val choices = StreamCatalog.choices(tracks(video("ok", 720, 30f, 800_000), video("bad", 4320, 60f, 20_000_000),
            supported = intArrayOf(C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_SUBTYPE)), C.TRACK_TYPE_VIDEO)
        assertEquals(listOf("ok"), choices.map { it.key.id })
    }
    @Test fun explicitSelectionAndAutoAreIndependentOfAdaptiveSelectedFlags() {
        val choices = StreamCatalog.choices(tracks(video("720", 720, 30f, 800_000), video("360", 360, 24f, 300_000), selected = booleanArrayOf(true, true)), C.TRACK_TYPE_VIDEO)
        val exact = TrackSelectionParameters.Builder().setOverrideForType(TrackSelectionOverride(choices[0].group, choices[0].index)).build()
        assertTrue(choices[0].explicitlySelected(exact)); assertFalse(choices[1].explicitlySelected(exact))
        val auto = exact.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        assertFalse(choices.any { it.explicitlySelected(auto) })
    }
    @Test fun reloadMatchesIdentityAndRejectsVanishedOrAmbiguousRepresentations() {
        val choice = StreamCatalog.choices(tracks(video("720", 720, 30f, 800_000)), C.TRACK_TYPE_VIDEO).single()
        val reloaded = StreamCatalog.choices(tracks(video("720", 720, 30f, 800_000)), C.TRACK_TYPE_VIDEO)
        assertNotNull(StreamCatalog.find(reloaded, choice.key))
        assertNull(StreamCatalog.find(emptyList(), choice.key))
        assertNull(StreamCatalog.find(reloaded + reloaded, choice.key))
    }
    @Test fun originalStableDubbedAndUnlabeledAudioRemainSelectable() {
        val inputs = listOf(audio(roles = C.ROLE_FLAG_MAIN), audio(id = "141", label = "English Stable Volume", roles = C.ROLE_FLAG_MAIN),
            audio(id = "142", lang = "es", label = "Spanish", roles = C.ROLE_FLAG_ALTERNATE), audio(id = "unknown", lang = null, label = null))
        val choices = StreamCatalog.choices(Tracks(inputs.mapIndexed { index, f -> Tracks.Group(TrackGroup("$index", f), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }), C.TRACK_TYPE_AUDIO)
        assertEquals(AudioSection.entries.toList(), choices.map { it.section })
        assertEquals("Audio track 1", choices.last().primary)
    }
    @Test fun audioTiersAndFileSizeUseKnownValuesOnly() {
        val formats = listOf(metadata("140", "English", true, bitrate = 128_000), metadata("139", "English", true, bitrate = 64_000))
        val choices = StreamCatalog.choices(tracks(audio(), audio(id = "139", bitrate = 64_000)), C.TRACK_TYPE_AUDIO, formats)
        assertTrue(choices[0].primary.contains("High Bitrate")); assertTrue(choices[1].primary.contains("Low Bitrate"))
        assertEquals("AAC · 128 kbps · 3 MB", choices[0].secondary)
        assertEquals("", StreamCatalog.bitrateLabel(-1)); assertEquals("", StreamCatalog.bytesLabel(0))
    }
    @Test fun explicitDubMetadataTakesPrecedenceOverManifestDefaultRole() {
        val dub = metadata("140", "Spanish", false, lang = "es")
        val choices = StreamCatalog.choices(tracks(audio(lang = "es", label = "Spanish", roles = C.ROLE_FLAG_MAIN)), C.TRACK_TYPE_AUDIO, listOf(dub))
        assertEquals(AudioSection.DUBBED, choices.single().section)
    }
}
