package net.wingress.mobivious

import android.app.Application
import net.wingress.mobivious.data.InvidiousApi
import net.wingress.mobivious.data.SessionStore

class MobiviousApplication : Application() {
    lateinit var store: SessionStore
    lateinit var api: InvidiousApi
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        api = InvidiousApi({ store.server }, { store.account.value }, { store.save(null) })
    }
}
