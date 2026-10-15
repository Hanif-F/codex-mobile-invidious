package net.wingress.mobivious

import androidx.media3.common.*
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioSelectionTest {
    private fun audio(id: String, language: String? = "en-US", label: String? = "English original", bitrate: Int = 128000,
        roles: Int = C.ROLE_FLAG_MAIN) = Format.Builder().setId(id).setSampleMimeType(MimeTypes.AUDIO_AAC)
        .setCodecs("mp4a.40.2").setLanguage(language).setLabel(label).setAverageBitrate(bitrate).setRoleFlags(roles).build()
    private val original = audio("original")
    private val stable = audio("stable", label = "English Stable Volume", bitrate = 120000)
    private val dub = audio("dub", "id", "Indonesian", 96000, C.ROLE_FLAG_ALTERNATE)
    private fun choices(vararg formats: Format, metadata: List<StreamFormat> = emptyList(), unsupported: Set<String> = emptySet()) =
        StreamCatalog.choices(Tracks(formats.mapIndexed { index, f -> Tracks.Group(TrackGroup("group-$index", f), false,
            intArrayOf(if (f.id in unsupported) C.FORMAT_UNSUPPORTED_SUBTYPE else C.FORMAT_HANDLED), booleanArrayOf(false)) }), C.TRACK_TYPE_AUDIO, metadata)
    private fun policy(preference: String) = AudioSelection.open("one", AccountPreferences(preferredAudio = preference), true)

    @Test fun preferenceCatalogNormalizesAndKeepsRegionalChoicesWithoutCaptionVariants() {
        assertEquals(listOf("original", "original_stable"), AudioPreferences.choices.take(2).map { it.first })
        assertTrue(AudioPreferences.languages.containsAll(listOf("pt-BR" to "Portuguese (Brazil)", "es-ES" to "Spanish (Spain)")))
        assertEquals(AudioPreferences.languages.map { it.second }.sorted(), AudioPreferences.languages.map { it.second })
        assertFalse(AudioPreferences.choices.any { "auto-generated" in it.second })
        assertEquals("pt-BR", AudioPreferences.normalize("PT_br"))
        assertEquals("zh-Hans", AudioPreferences.normalize("ZH_hans"))
        listOf(null, "", "unknown", "en.4", true, 42, JSONObject.NULL).forEach {
            assertEquals("original", AudioPreferences.normalize(it))
        }
    }
    @Test fun storedPreferencesRoundTripAndOnlyPatchAudio() {
        val before = AccountPreferences(region = "ID", videoCodec = "av1")
        val selected = before.copy(preferredAudio = "pt-BR")
        assertEquals(selected, AccountPreferences.parse(selected.json()))
        assertEquals("pt-BR", LocalPreferences.read(selected.json()).preferredAudio)
        val patch = selected.changesFrom(before)
        assertEquals(setOf("preferred_audio"), patch.keys().asSequence().toSet())
        assertEquals("pt-BR", before.merge(patch).preferredAudio)
        assertEquals("av1", before.merge(patch).videoCodec)
        assertEquals("original", AccountPreferences.parse(JSONObject()).preferredAudio)
        assertEquals("original", AccountPreferences.parse(JSONObject().put("preferred_audio", true)).preferredAudio)
        assertEquals("pt-BR", AccountPreferences.parse(JSONObject().put("preferred_audio", "PT_br")).preferredAudio)
    }
    @Test fun accountAudioPatchesAreAuthenticatedSparseAndReadsUseTheSameField() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            val before = AccountPreferences()
            val saved = before.copy(preferredAudio = "id")
            server.enqueue(MockResponse().setBody(saved.json().toString()))
            assertEquals("id", api.preferences(saved.changesFrom(before), api.context()).preferredAudio)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method); assertEquals("Bearer token", request.getHeader("Authorization"))
            assertEquals("{\"preferred_audio\":\"id\"}", request.body.readUtf8())
            server.enqueue(MockResponse().setBody(before.copy(preferredAudio = "original_stable").json().toString()))
            assertEquals("original_stable", api.preferences().preferredAudio)
        }
    }
    @Test fun originalsStableAndDubsIgnoreManifestOrderingAndSelectHighestBitrate() {
        val all = choices(dub, stable, original, audio("low", bitrate = 64000))
        assertEquals("original", policy("original").fixed(all)?.key?.id)
        assertEquals("stable", policy("original_stable").fixed(all)?.key?.id)
        assertEquals("choices=$all policy=${policy("id")}", "dub", policy("id").fixed(all)?.key?.id)
        assertEquals("original", policy("fr").fixed(all)?.key?.id)
        assertEquals("original", policy("original_stable").fixed(choices(dub, original))?.key?.id)
    }
    @Test fun regionalChoicesAreExactAndGeneralLanguagesPreferExactBeforeRegionalTracks() {
        val brazil = audio("br", "pt-BR", "Portuguese (Brazil)", 96000, C.ROLE_FLAG_ALTERNATE)
        val portugal = audio("pt", "pt-PT", "Portuguese (Portugal)", 128000, C.ROLE_FLAG_ALTERNATE)
        val general = audio("general", "pt", "Portuguese", 64000, C.ROLE_FLAG_ALTERNATE)
        assertEquals("br", policy("pt-BR").fixed(choices(original, brazil, portugal))?.key?.id)
        assertEquals("pt", policy("pt").fixed(choices(original, brazil, portugal))?.key?.id)
        assertEquals("general", policy("pt").fixed(choices(original, brazil, portugal, general))?.key?.id)
        assertEquals("original", policy("pt-BR").fixed(choices(original, portugal))?.key?.id)
    }
    @Test fun duplicateItagsAreMatchedUsingLanguageProcessingAndBitrate() {
        val formats = listOf(
            StreamFormat("140", "audio/mp4", "mp4a.40.2", 0, 0, 0f, 128000, 0, AudioIdentity("en-US.4", "English original", true), false),
            StreamFormat("140", "audio/mp4", "mp4a.40.2", 0, 0, 0f, 120000, 0, AudioIdentity("en-US.10", "English", null), true),
            StreamFormat("140", "audio/mp4", "mp4a.40.2", 0, 0, 0f, 96000, 0, AudioIdentity("id.3", "Indonesian", false), false))
        val all = choices(dub.buildUpon().setId("140").build(), original.buildUpon().setId("140").build(),
            stable.buildUpon().setId("140").setRoleFlags(C.ROLE_FLAG_ALTERNATE).build(), metadata = formats)
        assertEquals("id.3", policy("id").fixed(all)?.audioTrackId)
        assertEquals("en-US.10", policy("original_stable").fixed(all)?.audioTrackId)
        assertEquals("en-US.4", policy("original").fixed(all)?.audioTrackId)
    }
    @Test fun processedDubsAreNotUsedForStableOriginalOrLanguagePreferences() {
        val processedDub = audio("dub", "id", "Indonesian Stable Volume", roles = C.ROLE_FLAG_ALTERNATE)
        assertEquals("original", policy("original_stable").fixed(choices(original, processedDub))?.key?.id)
        assertEquals("original", policy("id").fixed(choices(original, processedDub))?.key?.id)
    }
    @Test fun unknownOriginalMetadataRetainsDefaultAndUnsupportedTracksAreExcluded() {
        val unknown = audio("unknown", null, null, roles = 0)
        assertNull(policy("original").fixed(choices(unknown)))
        assertNull(policy("id").fixed(choices(unknown)))
        assertNull(policy("original").fixed(emptyList()))
        assertEquals("original", policy("id").fixed(choices(original, dub, unsupported = setOf("dub")))?.key?.id)
        assertNull(AudioSelection.open("progressive", AccountPreferences(preferredAudio = "id"), false).fixed(choices(original, dub)))
    }
    @Test fun bitrateTiesRetainManifestOrderAcrossLanguageSortedMenus() {
        val first = audio("first", "pt-PT", "Zulu", roles = C.ROLE_FLAG_ALTERNATE)
        val second = audio("second", "pt-BR", "Alpha", roles = C.ROLE_FLAG_ALTERNATE)
        assertEquals("first", policy("pt").fixed(choices(first, second))?.key?.id)
    }
    @Test fun manualTrackSurvivesRenditionIdChangesFallsBackAndAutoRestoresCapturedDefault() {
        val all = choices(original, stable, dub)
        val manual = policy("id").select(all.first { it.stable })
        val replaced = choices(original, stable.buildUpon().setId("replacement").build(), dub)
        assertEquals("replacement", manual.fixed(replaced)?.key?.id)
        assertEquals("original", manual.fixed(choices(original, dub))?.key?.id)
        assertEquals("dub", manual.auto().fixed(all)?.key?.id)
        assertEquals("id", manual.preference)
        assertEquals("original", AudioSelection.open("next", AccountPreferences(), true).fixed(all)?.key?.id)
    }
    @Test fun reusedRenditionIdCannotRestoreAnotherLanguage() {
        val selected = choices(dub).single()
        val manual = policy("id").select(selected)
        val french = audio("dub", "fr", "French", roles = C.ROLE_FLAG_ALTERNATE)
        assertEquals("original", manual.fixed(choices(french, original))?.key?.id)
    }
    @Test fun legacyManifestLanguageTagsMatchCanonicalPreferences() {
        for ((legacy, canonical) in listOf("in" to "id", "iw" to "he", "ji" to "yi", "tl" to "fil")) {
            assertEquals("dub", policy(canonical).fixed(choices(original, audio("dub", legacy, "Dub", roles = C.ROLE_FLAG_ALTERNATE)))?.key?.id)
        }
    }
}
