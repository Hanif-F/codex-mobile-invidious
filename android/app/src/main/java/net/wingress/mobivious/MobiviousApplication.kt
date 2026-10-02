package net.wingress.mobivious

import android.app.Application
import net.wingress.mobivious.data.InvidiousApi
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.data.ResponseCache
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MobiviousApplication : Application() {
    lateinit var store: SessionStore
    lateinit var api: InvidiousApi
    lateinit var cache: ResponseCache
    val offline = MutableStateFlow(false)
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        cache = ResponseCache(File(cacheDir, "feeds"))
        api = InvidiousApi({ store.server }, { store.account.value }, { store.save(null) }, cache = cache, onOffline = { offline.value = it })
    }
}
