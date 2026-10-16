package net.wingress.mobivious.data

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class LegacyAnnotation(val id: String, val style: String, val text: String, val startMs: Long, val endMs: Long,
    val x: Float, val y: Float, val width: Float, val height: Float,
    val background: Int, val foreground: Int, val opacity: Float, val textSize: Float,
    val url: String? = null, val tailX: Float? = null, val tailY: Float? = null) {
    fun visible(positionMs: Long) = positionMs >= startMs && positionMs < endMs
}

/** Archives contain untrusted XML. Never resolve a DTD, entity or external resource. */
object AnnotationParser {
    const val MAX_BYTES = 2 * 1024 * 1024
    private val styles = setOf("popup", "speech", "label", "title", "highlight")
    fun time(value: String?): Long? {
        val parts = value?.split(':')?.takeIf { it.size in 1..3 } ?: return null
        var seconds = 0.0
        for (part in parts) {
            val number = part.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
            seconds = seconds * 60 + number
        }
        return (seconds * 1000).takeIf { it.isFinite() && it <= 7 * 24 * 3600 * 1000L }?.toLong()
    }
    fun parse(xml: String): List<LegacyAnnotation> {
        require(xml.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Annotation archive is too large" }
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Unsupported annotation XML" }
        val result = mutableListOf<LegacyAnnotation>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach { runCatching { setFeature(it, false) } }
        }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), object : DefaultHandler() {
            var depth = 0; var nodes = 0; var count = 0
            var base: Map<String, String>? = null
            var shape: Map<String, String>? = null
            var appearance = emptyMap<String, String>()
            var start: Long? = null; var end: Long? = null; var url: String? = null
            var inText = false; val text = StringBuilder()
            fun Attributes.map() = (0 until length).associate { getQName(it) to getValue(it) }
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, name: String, attrs: Attributes) {
                require(++depth <= 32 && ++nodes <= 100_000) { "Annotation XML is too complex" }
                if (name == "annotation") {
                    require(++count <= 5000) { "Too many annotations" }
                    base = attrs.map(); shape = null; appearance = emptyMap(); text.clear()
                    start = null; end = null; url = null
                } else if (base != null) when {
                    name == "TEXT" -> inText = true
                    name == "appearance" -> appearance = attrs.map()
                    name.endsWith("Region") && name != "movingRegion" -> {
                        if (shape == null) shape = attrs.map()
                        time(attrs.getValue("t"))?.let { if (start == null) start = it; end = it }
                    }
                    name == "url" -> url = attrs.getValue("value")?.takeIf { value ->
                        value.length <= 4096 && value.toHttpUrlOrNull()?.let { it.username.isEmpty() && it.password.isEmpty() } == true
                    }
                }
            }
            override fun characters(chars: CharArray, offset: Int, length: Int) {
                if (inText) { require(text.length + length <= 16_384) { "Annotation text is too long" }; text.append(chars, offset, length) }
            }
            override fun endElement(uri: String?, localName: String?, name: String) {
                depth--
                if (name == "TEXT") inText = false
                if (name != "annotation") return
                val b = base; base = null
                val s = shape ?: return
                val style = b?.get("style") ?: if (b?.get("type") == "highlight") "highlight" else "popup"
                if (b?.get("type") !in setOf("text", "highlight") || style !in styles) return
                fun Map<String, String>.number(key: String) = get(key)?.toFloatOrNull()?.takeIf { it.isFinite() }
                val x = s.number("x")?.coerceIn(0f, 100f) ?: return
                val y = s.number("y")?.coerceIn(0f, 100f) ?: return
                val w = s.number("w")?.coerceIn(0f, 100f - x) ?: return
                val h = s.number("h")?.coerceIn(0f, 100f - y) ?: return
                val from = start ?: return; val to = end ?: return
                if (to <= from || w <= 0 || h <= 0) return
                result += LegacyAnnotation(b?.get("id").orEmpty().ifBlank { "annotation-$count" } + ":$count", style,
                    text.toString(), from, to, x, y, w, h,
                    (appearance["bgColor"]?.toLongOrNull()?.toInt() ?: 0xffffff) and 0xffffff,
                    (appearance["fgColor"]?.toLongOrNull()?.toInt() ?: 0) and 0xffffff,
                    appearance.number("bgAlpha")?.coerceIn(0f, 1f) ?: .8f,
                    appearance.number("textSize")?.coerceIn(.5f, 20f) ?: 3.15f,
                    url, s.number("sx")?.coerceIn(0f, 100f), s.number("sy")?.coerceIn(0f, 100f))
            }
        })
        return result
    }
}

data class AnnotationBinding(val context: ApiContext, val occurrence: String, val videoId: String)
data class AnnotationState(val binding: AnnotationBinding? = null, val enabled: Boolean = false, val visible: Boolean = false,
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null,
    val items: List<LegacyAnnotation> = emptyList(), val dismissed: Set<String> = emptySet()) {
    fun active(positionMs: Long) = if (enabled && visible) items.filter { it.id !in dismissed && it.visible(positionMs) } else emptyList()
}

class AnnotationsController(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (String, ApiContext) -> List<LegacyAnnotation>) {
    val state = MutableStateFlow(AnnotationState())
    private var job: Job? = null
    fun bind(binding: AnnotationBinding?, enabled: Boolean, visible: Boolean) {
        if (binding != state.value.binding) {
            job?.cancel(); job = null; state.value = AnnotationState(binding)
        }
        state.value = state.value.copy(enabled = enabled, visible = visible)
        if (!enabled || !visible || binding == null) {
            job?.cancel(); job = null; state.value = state.value.copy(loading = false)
        } else if (!state.value.loaded && !state.value.loading && state.value.error == null) load()
    }
    fun dismiss(id: String) { state.value = state.value.copy(dismissed = state.value.dismissed + id) }
    fun retry() { if (state.value.enabled && state.value.visible) load() }
    private fun load() {
        val binding = state.value.binding ?: return
        job?.cancel(); state.value = state.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val items = fetch(binding.videoId, binding.context)
                ensureActive()
                if (context() == binding.context && state.value.binding == binding)
                    state.value = state.value.copy(loading = false, loaded = true, items = items)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (context() == binding.context && state.value.binding == binding)
                    state.value = state.value.copy(loading = false, error = "Archived annotations unavailable. Try again.")
            }
        }
    }
}
