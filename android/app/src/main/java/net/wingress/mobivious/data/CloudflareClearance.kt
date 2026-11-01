package net.wingress.mobivious.data

import okhttp3.Cookie
import okhttp3.HttpUrl

/** A browser-issued credential, independent of account and instance verification tokens. */
data class CloudflareClearance(val cookie: Cookie, val userAgent: String) {
    fun usable(url: HttpUrl, agent: String, now: Long): Boolean =
        cookie.name == "cf_clearance" && cookie.value.isNotEmpty() && cookie.value.length <= 4096 &&
            cookie.matches(url) && cookie.expiresAt / 1000 > now && userAgent == agent &&
            userAgent.isNotBlank() && userAgent.none { it < ' ' || it == '\u007f' }

    companion object {
        fun parse(url: HttpUrl, value: String, userAgent: String): CloudflareClearance? =
            Cookie.parse(url, value)?.takeIf { it.name == "cf_clearance" }?.let { CloudflareClearance(it, userAgent) }
    }
}

/** Persistent implementations must encrypt credentials and bind ciphertext to the exact origin. */
interface CloudflarePersistence {
    fun read(origin: String): CloudflareClearance?
    fun write(origin: String, clearance: CloudflareClearance?)
}

internal class MemoryCloudflarePersistence : CloudflarePersistence {
    private val values = mutableMapOf<String, CloudflareClearance>()
    @Synchronized override fun read(origin: String) = values[origin]
    @Synchronized override fun write(origin: String, clearance: CloudflareClearance?) {
        if (clearance == null) values.remove(origin) else values[origin] = clearance
    }
}
