package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One confirmed title choice shared by browsing and background playback, bound to its request owner. */
class OriginalTitlePreferences(private val current: () -> ApiContext,
    private val guest: (ApiContext) -> Boolean, private val fetch: suspend (ApiContext) -> AccountPreferences) {
    private val loading = Mutex()
    private var owner: ApiContext? = null
    private var choice = true
    private var revision = 0L

    @Synchronized fun version() = revision
    @Synchronized fun confirm(context: ApiContext, value: Boolean, expected: Long? = null) {
        if (context != current() || expected != null && expected != revision) return
        owner = context; choice = value; revision++
    }
    @Synchronized private fun saved(context: ApiContext): Boolean? = choice.takeIf { owner == context }
    @Synchronized private fun fallback(context: ApiContext, expected: Long) {
        if (context == current() && expected == revision && owner != context) {
            // A default is not a confirmed write: an already-running successful GET may replace it.
            owner = context; choice = true
        }
    }
    fun known(context: ApiContext): Boolean? {
        if (context != current()) return null
        return if (context.account == null) guest(context) else saved(context)
    }
    private fun check(context: ApiContext) {
        if (context != current()) throw CancellationException("Account or instance changed")
    }
    suspend fun resolve(context: ApiContext): Boolean {
        check(context)
        if (context.account == null) return guest(context).also { check(context) }
        saved(context)?.let { return it }
        return loading.withLock {
            check(context)
            saved(context)?.let { return@withLock it }
            val version = version()
            val value = try { fetch(context).showOriginalTitle }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            check(context)
            // A successful PATCH or newer read wins over an older in-flight read.
            if (value != null) confirm(context, value, version) else fallback(context, version)
            saved(context) ?: true
        }
    }

    companion object {
        /** JSON/Atom reads containing videos; never media, captions, comments or DeArrow. */
        private val videoReads = Regex(
            "api/v1/(?:popular|trending|search|hashtag/[^/]+|videos/[^/]+(?:/downloads)?|" +
                "channels/[^/]+(?:/(?:videos|shorts|streams|latest|search|clips|playlists|releases|podcasts|courses))?|" +
                "playlists/[^/]+|mixes/[^/]+|clips(?:/[^/]+)?|" +
                "auth/(?:feed|history|playlists(?:/[^/]+(?:/feed)?)?|clips(?:/[^/]+)?|saved_playlists))")
        fun supports(path: String): Boolean = path.removePrefix("/").matches(videoReads)
    }
}
