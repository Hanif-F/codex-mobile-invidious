package net.wingress.mobivious

import android.app.Application
import android.os.Handler
import android.os.Looper
import net.wingress.mobivious.data.InvidiousApi
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.data.ResponseCache
import net.wingress.mobivious.data.WatchedRepository
import net.wingress.mobivious.data.BlockedRepository
import net.wingress.mobivious.player.PlaybackQueueSnapshot
import net.wingress.mobivious.data.ApiContext
import net.wingress.mobivious.data.DeArrowTitles
import net.wingress.mobivious.data.OriginalTitlePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MobiviousApplication : Application(), coil.ImageLoaderFactory {
    lateinit var access: net.wingress.mobivious.data.InstanceAccess
    lateinit var downloads: net.wingress.mobivious.data.DownloadRepository
    lateinit var store: SessionStore
    lateinit var api: InvidiousApi
    lateinit var cache: ResponseCache
    lateinit var playbackSync: net.wingress.mobivious.data.PlaybackSyncRepository
    lateinit var syncCoordinator: net.wingress.mobivious.data.PlaybackSyncCoordinator
    internal val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var networkAvailable = true
    lateinit var watched: WatchedRepository
    lateinit var blocked: BlockedRepository
    lateinit var dearrowTitles: DeArrowTitles
    lateinit var channelSpoilers: net.wingress.mobivious.data.ChannelSpoilerRepository
    lateinit var aiFilter: net.wingress.mobivious.data.AiFilterRepository
    private val titleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val offline = MutableStateFlow(false)
    val playbackQueue = MutableStateFlow(PlaybackQueueSnapshot())
    internal val playbackStatistics = net.wingress.mobivious.player.PlaybackStatistics()
    val playbackContext = MutableStateFlow<ApiContext?>(null)
    internal var playbackVisible = false
    val captionFailure = MutableStateFlow<net.wingress.mobivious.player.CaptionLoadFailure?>(null)
    internal val storyboardImages by lazy { net.wingress.mobivious.ui.StoryboardImages(this) }
    val storyboards by lazy { net.wingress.mobivious.data.StoryboardRepository(api::storyboard) }
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        val verificationUserAgent = android.webkit.WebSettings.getDefaultUserAgent(this)
        access = net.wingress.mobivious.data.InstanceAccess(store::positionContext, store.verificationPersistence,
            store.cloudflarePersistence, { verificationUserAgent })
        downloads = net.wingress.mobivious.data.DownloadRepository(this)
        net.wingress.mobivious.downloads.DownloadExportService.restore(this)
        cache = ResponseCache(File(cacheDir, "feeds"))
        val originalTitles = OriginalTitlePreferences(store::positionContext,
            { store.guestPreferences(it).showOriginalTitle }, { api.preferences(it) })
        api = InvidiousApi({ store.server }, { store.account.value }, { store.save(null) }, cache = cache, onOffline = { offline.value = it }, generation = { store.contextGeneration }, onProfile = store::confirmProfile,
            originalTitles = originalTitles, access = access, onPreferences = { context, preferences ->
                if (::playbackSync.isInitialized) playbackSync.configure(context, preferences)
            })
        dearrowTitles = DeArrowTitles(titleScope, { store.server }) { api.dearrowTitle(it) }
        channelSpoilers = net.wingress.mobivious.data.ChannelSpoilerRepository(titleScope, api::context, api::spoilerPublications)
        channelSpoilers.reset(preferences = if (store.account.value == null) store.guestPreferences() else null)
        aiFilter = net.wingress.mobivious.data.AiFilterRepository(titleScope, api::context, api::aiStatus, api::aiChannels)
        aiFilter.reset()
        playbackSync = net.wingress.mobivious.data.PlaybackSyncRepository(api, store)
        watched = WatchedRepository(api, store, playbackSync)
        syncCoordinator = net.wingress.mobivious.data.PlaybackSyncCoordinator(playbackScope, playbackSync) { networkAvailable }
        playbackSync.changed = { watched.reconcilePending(); syncCoordinator.request() }
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        networkAvailable = connectivity.activeNetwork != null
        connectivity.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { networkAvailable = true; syncCoordinator.resume() }
            override fun onLost(network: android.net.Network) { networkAvailable = connectivity.activeNetwork != null }
        })
        playbackScope.launch { access.recovery.collect { if (it != null) syncCoordinator.resume() } }
        blocked = BlockedRepository(api, store)
        val main = Handler(Looper.getMainLooper())
        fun changed(context: ApiContext) {
            if (context != api.context()) return
            syncCoordinator.resume()
            access.contextChanged()
            downloads.stopManagedCalls()
            watched.reset(context); blocked.reset(context); dearrowTitles.clear(); offline.value = false; playbackContext.value = context
            aiFilter.reset(context)
            channelSpoilers.reset(context, if (context.account == null) store.guestPreferences(context) else null)
        }
        store.onContextChanged = { context ->
            if (Looper.myLooper() == Looper.getMainLooper()) changed(context) else main.post { changed(context) }
        }
        playbackContext.value = api.context()
        blocked.reset()
        watched.reset()
        syncCoordinator.resume()
        if (store.account.value == null) watched.configure(api.context(), store.guestPreferences().savePosition)
    }
    fun imageLoaderBuilder(base: okhttp3.OkHttpClient = okhttp3.OkHttpClient()): coil.ImageLoader.Builder = coil.ImageLoader.Builder(this)
        .okHttpClient(access.client(base)).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                    val context = store.positionContext()
                    val address = chain.request.data.toString().toHttpUrlOrNull()
                    if (address != null && net.wingress.mobivious.data.InstanceAccess.sameOrigin(address, context.server))
                        access.issue.value?.takeIf { it.context == context }?.let { throw net.wingress.mobivious.data.InstanceAccessException(it.reason) }
                    val key = "${net.wingress.mobivious.data.InstanceAccess.cacheScope(context)}|${access.revision.value}|${chain.request.data}|${chain.request.transformations.joinToString { it.cacheKey }}"
                    val result = chain.proceed(chain.request.newBuilder().memoryCacheKey(key).diskCacheKey(key)
                        .headers(chain.request.headers.newBuilder().set(net.wingress.mobivious.data.InstanceAccess.CONTEXT_HEADER,
                            net.wingress.mobivious.data.InstanceAccess.cacheScope(context)).build()).build())
                    if (context != store.positionContext()) throw kotlinx.coroutines.CancellationException("Account or instance changed")
                    return result
                }
            })
        }
    override fun newImageLoader() = imageLoaderBuilder().build()
}
