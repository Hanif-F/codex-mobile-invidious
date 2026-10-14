package net.wingress.mobivious.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.MobiviousApplication

// Keep the manifest-bound component identity when separating it from persistence.
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(DownloadManager.ACTION_DOWNLOAD_COMPLETE, DownloadManager.ACTION_NOTIFICATION_CLICKED)) return
        val app = context.applicationContext as MobiviousApplication
        // DownloadProvider runs under a separate privileged UID. This receiver is
        // exported, but treats broadcasts only as a hint: all state comes from our
        // own DownloadManager rows, never from caller-supplied IDs or paths.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { runCatching { app.downloads.completion() }.onFailure { app.downloads.error.value = it.message ?: "Could not reconcile downloads." } } finally { pending.finish() } }
        if (intent.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("openDownloads", true))
    }
}
