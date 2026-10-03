package net.wingress.mobivious

import android.app.Application
import net.wingress.mobivious.data.InvidiousApi
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.data.ResponseCache
import net.wingress.mobivious.data.WatchedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MobiviousApplication : Application() {
    lateinit var store: SessionStore
    lateinit var api: InvidiousApi
    lateinit var cache: ResponseCache
    lateinit var watched: WatchedRepository
    val offline = MutableStateFlow(false)
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        cache = ResponseCache(File(cacheDir, "feeds"))
        api = InvidiousApi({ store.server }, { store.account.value }, { store.save(null) }, cache = cache, onOffline = { offline.value = it })
        watched = WatchedRepository(api, store)
        store.onContextChanged = { watched.reset(it) }
        watched.reset()
        if (store.account.value == null) watched.configure(api.context(), store.guestDeArrow().savePosition)
    }
}
