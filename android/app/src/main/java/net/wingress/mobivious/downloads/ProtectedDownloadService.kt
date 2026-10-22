package net.wingress.mobivious.downloads

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.MobiviousApplication

/** Explicitly started credentialed transfers. Interrupted work is retried from the download journal. */
class ProtectedDownloadService : Service() {
    companion object {
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, ProtectedDownloadService::class.java)) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mobivious:protected-downloads")
            .apply { acquire(6 * 60 * 60 * 1000L) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("protected-downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 42, Intent(this, MainActivity::class.java).putExtra("openDownloads", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(42, NotificationCompat.Builder(this, "protected-downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Mobivious download").setContentText("Downloading selected tracks").setContentIntent(open)
            .setOngoing(true).setProgress(0, 0, true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (job?.isActive != true) drain()
        return START_NOT_STICKY
    }
    private fun drain() {
        job = scope.launch {
            val downloads = (application as MobiviousApplication).downloads
            try { downloads.transferManaged() }
            finally {
                job = null
                if (isActive && downloads.records.value.any { record -> record.assets.any { it.managed && it.status == net.wingress.mobivious.data.DownloadStatus.QUEUED } }) drain()
                else stopSelf()
            }
        }
    }
    override fun onTimeout(startId: Int, fgsType: Int) { job?.cancel(); stopSelf() }
    override fun onDestroy() {
        scope.cancel(); (application as MobiviousApplication).downloads.stopManagedCalls()
        wake?.takeIf { it.isHeld }?.release(); wake = null
        super.onDestroy()
    }
}
