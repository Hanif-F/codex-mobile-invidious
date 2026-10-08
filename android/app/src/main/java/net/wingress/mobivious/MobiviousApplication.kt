package net.wingress.mobivious

import android.app.Application
import android.os.Handler
import android.os.Looper
import net.wingress.mobivious.data.InvidiousApi
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.data.ResponseCache
import net.wingress.mobivious.data.WatchedRepository
import net.wingress.mobivious.data.BlockedRepository
import net.wingress.mobivious.data.PlaybackQueueSnapshot
import net.wingress.mobivious.data.ApiContext
import net.wingress.mobivious.data.DeArrowTitles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MobiviousApplication : Application() {
    lateinit var downloads: net.wingress.mobivious.data.DownloadRepository
    lateinit var store: SessionStore
    lateinit var api: InvidiousApi
    lateinit var cache: ResponseCache
    lateinit var watched: WatchedRepository
    lateinit var blocked: BlockedRepository
    lateinit var dearrowTitles: DeArrowTitles
    private val titleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val offline = MutableStateFlow(false)
    val playbackQueue = MutableStateFlow(PlaybackQueueSnapshot())
    val playbackContext = MutableStateFlow<ApiContext?>(null)
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        downloads = net.wingress.mobivious.data.DownloadRepository(this)
        net.wingress.mobivious.downloads.DownloadExportService.restore(this)
        cache = ResponseCache(File(cacheDir, "feeds"))
        api = InvidiousApi({ store.server }, { store.account.value }, { store.save(null) }, cache = cache, onOffline = { offline.value = it }, generation = { store.contextGeneration }, onProfile = store::confirmProfile)
        dearrowTitles = DeArrowTitles(titleScope, { store.server }) { api.dearrowTitle(it) }
        watched = WatchedRepository(api, store)
        blocked = BlockedRepository(api, store)
        val main = Handler(Looper.getMainLooper())
        fun changed(context: ApiContext) {
            if (context != api.context()) return
            watched.reset(context); blocked.reset(context); dearrowTitles.clear(); offline.value = false; playbackContext.value = context
        }
        store.onContextChanged = { context ->
            if (Looper.myLooper() == Looper.getMainLooper()) changed(context) else main.post { changed(context) }
        }
        playbackContext.value = api.context()
        blocked.reset()
        watched.reset()
        if (store.account.value == null) watched.configure(api.context(), store.guestDeArrow().savePosition)
    }
}
