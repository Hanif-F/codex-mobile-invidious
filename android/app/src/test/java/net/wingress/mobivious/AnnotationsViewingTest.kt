package net.wingress.mobivious

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationsViewingTest {
    private val xml = """<document><annotations><annotation id="one" type="text" style="speech">
        <TEXT>Tap &amp; learn</TEXT><segment><movingRegion type="rect">
        <rectRegion t="0:00:01.250" x="10" y="20" w="30" h="15" sx="45" sy="50"/>
        <rectRegion t="0:00:06.500" x="10" y="20" w="30" h="15"/></movingRegion></segment>
        <appearance bgColor="16711680" fgColor="255" bgAlpha="0.6" textSize="4"/>
        <action type="openUrl"><url value="https://www.youtube.com/watch?v=testvideo01&amp;src_vid=testvideo01#t=4s"/></action>
        </annotation><annotation type="pause"/><annotation type="text"><segment><movingRegion>
        <rectRegion t="NaN" x="0" y="0" w="100" h="100"/></movingRegion></segment></annotation>
        </annotations></document>"""

    @Test fun archivePreservesAppearanceActionsAndAbsoluteTiming() {
        val a = AnnotationParser.parse(xml).single()
        assertEquals("Tap & learn", a.text); assertEquals("speech", a.style)
        assertEquals(1250L, a.startMs); assertEquals(6500L, a.endMs)
        assertFalse(a.visible(1249)); assertTrue(a.visible(1250)); assertFalse(a.visible(6500))
        assertEquals(0xff0000, a.background); assertEquals(255, a.foreground); assertEquals(.6f, a.opacity)
        assertEquals(45f, a.tailX)
        assertEquals(ContentLink.Seek(4000), ContentLinks.resolve(a.url!!, "https://instance.test", "testvideo01"))
        assertEquals(3_723_500L, AnnotationParser.time("1:02:03.5")); assertNull(AnnotationParser.time("Infinity"))
    }
    @Test fun unsafeXmlAndOversizedArchivesAreRejectedAndUnsafeLinksAreInert() {
        for (bad in listOf("<!DOCTYPE document SYSTEM 'file:///etc/passwd'><document/>", "<!ENTITY test 'secret'>", " ".repeat(AnnotationParser.MAX_BYTES + 1))) {
            assertThrows(IllegalArgumentException::class.java) { AnnotationParser.parse(bad) }
        }
        assertNull(AnnotationParser.parse(xml.replace("https://www.youtube.com/watch?v=testvideo01&amp;src_vid=testvideo01#t=4s", "javascript:alert(1)")).single().url)
        assertTrue(AnnotationParser.parse(xml.replace("w=\"30\"", "w=\"NaN\"")).isEmpty())
    }
    @Test fun suspensionCancellationDismissalAndOccurrenceIsolation() = runTest {
        var context = ApiContext("https://one.test", null)
        val responses = mutableListOf<CompletableDeferred<List<LegacyAnnotation>>>()
        val controller = AnnotationsController(this, { context }) { _, _ -> CompletableDeferred<List<LegacyAnnotation>>().also { responses += it }.await() }
        val first = AnnotationBinding(context, "first", "testvideo01")
        controller.bind(first, true, true); runCurrent(); assertTrue(controller.state.value.loading)
        controller.bind(first, true, false); runCurrent(); assertFalse(controller.state.value.loading)
        responses[0].complete(AnnotationParser.parse(xml)); runCurrent(); assertTrue(controller.state.value.items.isEmpty())
        controller.bind(first, true, true); runCurrent(); responses[1].complete(AnnotationParser.parse(xml)); runCurrent()
        val item = controller.state.value.items.single()
        assertEquals(1, controller.state.value.active(2000).size)
        controller.dismiss(item.id); assertTrue(controller.state.value.active(2000).isEmpty())
        controller.bind(first.copy(occurrence = "second"), true, true); runCurrent()
        context = ApiContext("https://two.test", null)
        responses[2].complete(AnnotationParser.parse(xml)); runCurrent(); assertTrue(controller.state.value.items.isEmpty())
        controller.bind(null, false, false)
    }
    @Test fun annotationsApiIsPublicBoundedAndMissingArchivesAreEmpty() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = InvidiousApi({ server.url("/").toString() }, { null })
            server.enqueue(MockResponse().setBody(xml)); assertEquals(1, api.annotations("testvideo01").size)
            val request = server.takeRequest(); assertEquals("/api/v1/annotations/testvideo01", request.path)
            assertNull(request.getHeader("Authorization")); assertEquals("text/xml", request.getHeader("Accept"))
            server.enqueue(MockResponse().setResponseCode(404)); assertTrue(api.annotations("testvideo01").isEmpty())
            server.enqueue(MockResponse().setBody(" ".repeat(AnnotationParser.MAX_BYTES + 1)))
            try { api.annotations("testvideo01"); fail("Unbounded archive") } catch (_: java.io.IOException) { }
        }
    }
    @Test fun preferenceDefaultsSparseSavesLinksAndProjectionMetadataRoundTrip() {
        val defaults = AccountPreferences.parse(JSONObject())
        assertFalse(defaults.annotations); assertFalse(defaults.annotationsSubscribed); assertTrue(defaults.vrMode)
        val updated = defaults.copy(annotations = true, annotationsSubscribed = true, vrMode = false)
        assertEquals(updated, AccountPreferences.parse(updated.json()))
        assertEquals(setOf("annotations", "annotations_subscribed", "vr_mode"), updated.changesFrom(defaults).keys().asSequence().toSet())
        val off = PlaybackLinkOptions.parse(JSONObject("""{"iv_load_policy":3,"vr_mode":false}"""))
        assertFalse(off.apply(updated).annotations); assertFalse(off.apply(defaults).vrMode)
        assertEquals(off, PlaybackLinkOptions.parse(off.json())); assertEquals(PlaybackLinkOptions(), off.carried())
        assertEquals("MESH", ApiParser.details(JSONObject("""{"adaptiveFormats":[{"projectionType":"MESH"}]}""")).projectionType)
        assertEquals("EQUIRECTANGULAR", ApiParser.details(JSONObject("""{"projectionType":"EQUIRECTANGULAR"}""")).projectionType)
    }
    @Test fun projectionResolutionPanningClampingAndKnownDirections() {
        val v = ViewingState()
        assertEquals(Projection.FLAT, v.projection(null, true))
        assertEquals(Projection.EAC, v.projection("MESH", true)); assertEquals(Projection.FLAT, v.projection("MESH", false))
        assertEquals(Projection.EQUIRECTANGULAR, v.copy(mode = ViewingMode.EQUIRECTANGULAR).projection(null, false))
        assertEquals(Projection.FLAT, v.copy(error = "failed").projection("MESH", true))
        assertEquals(85f, v.pan(400f, 100f).pitch); assertEquals(40f, v.pan(400f, 100f).yaw)
        assertEquals(v, v.pan(Float.NaN, 1f))
        assertEquals(.5 to .5, ProjectionMath.uv(Projection.EQUIRECTANGULAR, 0.0, 0.0, -1.0))
        assertEquals(.5 to .75, ProjectionMath.uv(Projection.EAC, 0.0, 0.0, -1.0))
        assertEquals(5.0 / 6 to .75, ProjectionMath.uv(Projection.EAC, 1.0, 0.0, 0.0))
        assertEquals(5.0 / 6 to .25, ProjectionMath.uv(Projection.EAC, 0.0, 1.0, 0.0))
        assertEquals(0f to 0f, ProjectionMath.motionAngles(floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)))
        assertEquals(-90f to 0f, ProjectionMath.motionAngles(floatArrayOf(0f,0f,1f, 1f,0f,0f, 0f,1f,0f)))
        assertEquals(0f to -90f, ProjectionMath.motionAngles(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)))
    }
}
