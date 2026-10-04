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
    @Test fun ranksResolutionThenFpsThenBitrateAndShowsCodecAndNumericRates() {
        val choices = StreamCatalog.choices(tracks(video("low", 720, 30f, 700_000), video("high", 720, 30f, 1_500_000),
            video("fps", 720, 60f, 900_000), video("1080", 1080, 24f, 2_000_000)), C.TRACK_TYPE_VIDEO)
        assertEquals(listOf("1080", "fps", "high", "low"), choices.map { it.key.id })
        assertEquals("720p30 · H.264", choices[2].primary); assertEquals("720p30 · H.264", choices[3].primary)
        assertEquals("1.5 Mbps", choices[2].secondary)
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
            video("trick", 4320, 60f, 30_000_000).buildUpon().setRoleFlags(C.ROLE_FLAG_TRICK_PLAY).build(),
            supported = intArrayOf(C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_HANDLED)), C.TRACK_TYPE_VIDEO)
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

    private fun videos(vararg formats: Format) = StreamCatalog.choices(tracks(*formats), C.TRACK_TYPE_VIDEO)
    private fun mixed() = videos(video("av-middle", 1080, 30f, 2_000_000, "av01"), video("h-low", 1080, 30f, 6_000_000),
        video("av-low", 1080, 30f, 1_000_000, "av01"), video("h-high", 1080, 30f, 8_000_000),
        video("av-high", 1080, 30f, 3_000_000, "av01"), video("h-middle", 1080, 30f, 7_000_000))

    @Test fun smallMenuGroupsRetainEveryRepresentationIncludingDuplicates() {
        val choices = mixed()
        for (size in 1..4) assertEquals(choices.take(size), StreamCatalog.qualityMenu(choices.take(size)))
        val equal = videos(*Array(4) { video("$it", 720, 30f, 1_000_000) })
        assertEquals(listOf("0", "1", "2", "3"), StreamCatalog.qualityMenu(equal).map { it.key.id })
    }
    @Test fun largeMenusKeepCodecExtremesWithoutPruningTheFullCatalog() {
        val choices = mixed()
        assertEquals(listOf("h-high", "h-low", "av-high", "av-low"), StreamCatalog.qualityMenu(choices).map { it.key.id })
        assertEquals(6, choices.size)
        val selected = choices.single { it.key.id == "av-middle" }
        assertEquals("1080p30 · AV1 · 2 Mbps", StreamCatalog.qualityText(selected))
        assertEquals(selected, StreamCatalog.findVideo(choices, selected.key))
        assertNull(StreamCatalog.findVideo(StreamCatalog.qualityMenu(choices), selected.key))
    }
    @Test fun equalExtremesUseOriginalOrderAndUnknownRatesDoNotBecomeLowVariants() {
        val choices = videos(video("low-first", 720, 29.97f, 1_000_000, "av01"), video("high-first", 720, 29.97f, 3_000_000, "av01"),
            video("high-second", 720, 30f, 3_000_000, "av01"), video("middle", 720, 30f, 2_000_000, "av01"),
            video("low-second", 720, 30f, 1_000_000, "av01"), video("missing", 720, 30f, -1, "av01"))
        assertEquals(listOf("high-first", "low-first"), StreamCatalog.qualityMenu(choices).map { it.key.id })
        assertEquals(listOf("0"), StreamCatalog.qualityMenu(videos(*Array(5) { video("$it", 720, 30f, 1_000_000) })).map { it.key.id })
        assertEquals(listOf("0"), StreamCatalog.qualityMenu(videos(*Array(5) { video("$it", 720, 30f, -1, "") })).map { it.key.id })
    }
    @Test fun spareMenuSlotsPreferNamedOtherCodecsAlphabetically() {
        val choices = videos(video("unknown", 720, 30f, 9_000_000, "custom"), video("av", 720, 30f, 4_000_000, "av01"),
            video("h", 720, 30f, 3_000_000), video("vp-high", 720, 30f, 2_500_000, "vp09"),
            video("vp-low", 720, 30f, 1_000_000, "vp9"), video("hevc-high", 720, 30f, 2_000_000, "hvc1"),
            video("hevc-low", 720, 30f, 500_000, "hev1"))
        assertEquals(listOf("av", "h", "hevc-high", "hevc-low"), StreamCatalog.qualityMenu(choices).map { it.key.id })
    }
    @Test fun menuGroupsUseRoundedFpsAndSeparateResolutions() {
        val choices = videos(video("720-30", 720, 29.97f, 9_000_000), video("1080-30", 1080, 30f, 1_000_000, "av01"),
            video("720-60", 720, 59.94f, 3_000_000), video("1080-60", 1080, 60f, 2_000_000, "av01"))
        assertEquals(listOf("1080-60", "1080-30", "720-60", "720-30"), StreamCatalog.qualityMenu(choices).map { it.key.id })
        assertEquals("720p60 · H.264", choices.single { it.key.id == "720-60" }.primary)
        val unknown = Format.Builder().setId("unknown").setSampleMimeType("video/mp4").setHeight(720).build()
        val meta = StreamFormat("unknown", "video/mp4", "", 1280, 720, 0f, 0, 12_000_000)
        val entry = StreamCatalog.choices(tracks(unknown), C.TRACK_TYPE_VIDEO, listOf(meta)).single()
        assertEquals("720p · Unknown codec", entry.primary); assertEquals("12 MB", entry.secondary)
        assertEquals("VP9", StreamCatalog.codecLabel("mp4a.40.2, vp09.00.31.08", "video/mp4"))
    }
    @Test fun presetsPickResolutionBeforeCodecAndRespectHighestAndLowestRanking() {
        val choices = mixed() + videos(video("h-4k", 2160, 60f, 10_000_000), video("h-720", 720, 30f, 2_000_000),
            video("av-480-high", 480, 60f, 700_000, "av01"), video("av-480-low", 480, 30f, 400_000, "av01"))
        for ((quality, codec, expected) in listOf(Triple("1080p", "av1", "av-high"), Triple("720p", "av1", "h-720"),
            Triple("best", "av1", "h-4k"), Triple("worst", "av1", "av-480-low"), Triple("1080p", "h264", "h-high"),
            Triple("144p", "av1", "av-480-low"), Triple("1080p", "auto", "h-high"))) {
            assertEquals(expected, StreamCatalog.defaultVideo(choices, quality, codec)?.key?.id)
        }
        assertEquals(3, StreamCatalog.automaticVideo(mixed(), "av1").size)
        assertEquals(choices, StreamCatalog.automaticVideo(choices, "auto"))
        val h264 = choices.filter { it.codec == "H.264" }
        assertEquals(h264, StreamCatalog.automaticVideo(h264, "av1"))
        val unknown = Format.Builder().setId("unknown").setSampleMimeType("video/mp4").setHeight(1080).setWidth(1920)
            .setFrameRate(30f).setAverageBitrate(6_500_000).build()
        assertEquals("h-high", StreamCatalog.defaultVideo(mixed() + videos(unknown), "1080p", "auto")?.key?.id)
    }
    @Test fun autoModeSurvivesSingleCodecCandidateAndNewerChoicesReplaceManualState() {
        val choices = mixed()
        val opened = VideoSelection.open("one", AccountPreferences(videoCodec = "av1"), true)
        val manual = opened.select(choices.single { it.key.id == "h-high" }.key)
        assertEquals("h-high", manual.fixed(choices)?.key?.id)
        val auto = manual.auto()
        assertTrue(auto.revision > manual.revision); assertEquals(VideoSelectionMode.AUTO, auto.resolve(choices).mode)
        assertNull(auto.fixed(choices)); assertEquals(listOf("av-low"), auto.adaptive(choices.filter { it.key.id == "av-low" }).map { it.key.id })
        assertEquals(VideoSelectionMode.AUTO, auto.mode)
        val next = VideoSelection.open("two", AccountPreferences(videoCodec = "h264", qualityDash = "720p"), true, auto.revision + 1)
        assertNull(next.manual); assertEquals(VideoSelectionMode.PRESET, next.mode); assertEquals("av1", auto.codec)
    }
    @Test fun reloadMatchesVideoIdentityAndVanishedManualChoicesReturnToCapturedDefaults() {
        val choices = mixed()
        val manual = VideoSelection.open("one", AccountPreferences(videoCodec = "av1", qualityDash = "1080p"), true)
            .select(choices.single { it.key.id == "h-high" }.key)
        val reloaded = choices.map { it.copy(group = TrackGroup("new", it.group.getFormat(it.index)), index = 0) }
        assertEquals("h-high", manual.resolve(reloaded).fixed(reloaded)?.key?.id)
        val remaining = reloaded.filter { it.key.id != "h-high" }
        assertEquals(VideoSelectionMode.PRESET, manual.resolve(remaining).mode)
        assertEquals("av-high", manual.resolve(remaining).fixed(remaining)?.key?.id)
        assertNull(StreamCatalog.findVideo(reloaded + reloaded, manual.manual))
        assertEquals(manual, manual.resolve(emptyList()))
        assertEquals(choices, VideoSelection.open("hls", AccountPreferences(videoCodec = "av1"), false).adaptive(choices))
    }
}
