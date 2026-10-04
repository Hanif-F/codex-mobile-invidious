package net.wingress.mobivious.data

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Reads existing metadata only. No channel or video lookup is performed. */
object Avatars {
    fun parse(json: JSONObject): String = (json.opt("authorThumbnail") as? String)?.takeIf { it.isNotBlank() }
        ?: json.optJSONArray("authorThumbnails")?.objects()?.mapNotNull { it.opt("url") as? String }
            ?.lastOrNull { it.isNotBlank() }.orEmpty()

    fun url(server: String, raw: String): String? {
        val base = server.toHttpUrlOrNull() ?: return null
        val input = raw.trim()
        if (input.isEmpty() || '\\' in input) return null
        val remote = (if (input.startsWith("//")) "https:$input" else input).toHttpUrlOrNull()
        val source = remote ?: if (input.startsWith("/ggpht/")) base.resolve(input) else null
        source ?: return null
        if (source.username.isNotEmpty() || source.password.isNotEmpty()) return null
        val sameInstance = source.scheme == base.scheme && source.host == base.host && source.port == base.port
        val path = when {
            sameInstance && source.encodedPath.startsWith("/ggpht/") -> source.encodedPath
            source.host in setOf("yt3.ggpht.com", "yt3.googleusercontent.com") &&
                source.port == (if (source.isHttps) 443 else 80) && source.encodedPath != "/" -> "/ggpht${source.encodedPath}"
            else -> return null
        }
        if (path == "/ggpht/") return null
        val sized = path.replace(Regex("=s\\d+"), "=s176").replace(Regex("/s\\d+-"), "/s176-")
        return base.newBuilder().encodedPath(sized).encodedQuery(source.encodedQuery).fragment(null).build().toString()
    }

    fun initial(name: String): String? {
        val text = Normalizer.normalize(name.trimStart(), Normalizer.Form.NFC)
        if (text.isEmpty() || !Character.isLetter(text.codePointAt(0))) return null
        val clusters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val first = text.substring(0, clusters.next())
        val uppercase = first.uppercase(Locale.ROOT)
        clusters.setText(uppercase)
        return if (clusters.next() == uppercase.length) uppercase else first
    }

    fun show(thin: Boolean, channelId: String, owner: String? = null): Boolean =
        !thin && channelId.isNotBlank() && channelId != owner

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).build()
}
