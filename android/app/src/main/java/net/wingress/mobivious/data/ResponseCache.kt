package net.wingress.mobivious.data

import java.io.File
import java.security.MessageDigest

/** Device-private feed snapshots, separated by server and account session. No mutation queue. */
class ResponseCache(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    private fun file(key: String) = File(directory, MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })
    @Synchronized fun read(key: String): String? = runCatching {
        val file = file(key)
        if (!file.exists() || clock() - file.lastModified() > 24 * 60 * 60 * 1000L) null else file.readText()
    }.getOrNull()
    @Synchronized fun write(key: String, value: String) {
        if (value.length > 2_000_000) return
        runCatching {
            directory.mkdirs()
            val temp = File.createTempFile("feed", ".tmp", directory)
            temp.writeText(value)
            if (!temp.renameTo(file(key))) temp.delete()
            directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(50)?.forEach { it.delete() }
        }
    }
    @Synchronized fun clear() { directory.listFiles()?.forEach { it.delete() } }
}
