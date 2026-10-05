package net.wingress.mobivious.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class SessionStore(context: Context) : LocalPlaybackPositions, VisibilityStore {
    private val prefs = context.getSharedPreferences("mobivious", Context.MODE_PRIVATE)
    private val storedAccount = readAccount()
    val account = MutableStateFlow(storedAccount?.takeIf { it.expiresAt > System.currentTimeMillis() / 1000 })
    @Volatile var contextGeneration: Long = 0
        private set
    var onContextChanged: (ApiContext) -> Unit = {}
    var server: String
        get() = prefs.getString("server", "https://invidious.wingress.net")!!
        set(value) {
            if (value == server) return
            contextGeneration++
            prefs.edit().putString("server", value).apply(); onContextChanged(positionContext())
        }
    var background: Boolean
        get() = prefs.getBoolean("background", true)
        set(value) { prefs.edit().putBoolean("background", value).apply() }
    fun chatAppearance(): ChatAppearance = runCatching { ChatAppearance.parse(JSONObject(prefs.getString("chat.appearance.$server", "{}")!!)) }.getOrDefault(ChatAppearance())
    fun chatAppearance(value: ChatAppearance) { prefs.edit().putString("chat.appearance.$server", value.bounded().json().toString()).apply() }
    fun chatTiming(videoId: String, context: ApiContext): Int = prefs.getInt("chat.timing.${context.server}.$videoId", 0).coerceIn(-3_600_000, 3_600_000)
    fun chatTiming(videoId: String, value: Int, context: ApiContext) {
        require(context.account == null && value in -3_600_000..3_600_000)
        prefs.edit().putInt("chat.timing.${context.server}.$videoId", value).apply()
    }
    var pip: Boolean
        get() = prefs.getBoolean("pip", true)
        set(value) { prefs.edit().putBoolean("pip", value).apply() }
    var defaultSpeed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) { prefs.edit().putFloat("speed", value).apply() }
    var maxHeight: Int
        get() = prefs.getInt("height", Int.MAX_VALUE)
        set(value) { prefs.edit().putInt("height", value).apply() }
    var region: String
        get() = prefs.getString("region", "US")!!
        set(value) { prefs.edit().putString("region", value).apply() }
    fun guestDeArrow(): AccountPreferences = prefs.getString("preferences.$server", null)?.let {
        runCatching { AccountPreferences.parse(JSONObject(it)) }.getOrNull()
    } ?: AccountPreferences(watchHistory = false, speed = defaultSpeed, qualityDash = if (maxHeight == Int.MAX_VALUE) "auto" else "${maxHeight}p", region = region,
        dearrowEnabled = prefs.getBoolean("dearrow.enabled.$server", false),
        dearrowShowOriginal = prefs.getBoolean("dearrow.original.$server", true), sponsorBlock = runCatching {
            SponsorBlockSettings.parse(JSONObject(prefs.getString("sponsorblock.$server", "{}")!!))
        }.getOrDefault(SponsorBlockSettings()))
    fun guestDeArrow(value: AccountPreferences) {
        prefs.edit().putString("preferences.$server", value.copy(watchHistory = false, sponsorBlock = value.sponsorBlock.copy(channels = emptyMap())).json().toString())
            .putBoolean("dearrow.enabled.$server", value.dearrowEnabled)
            .putBoolean("dearrow.original.$server", value.dearrowShowOriginal)
            .putString("sponsorblock.$server", value.sponsorBlock.copy(channels = emptyMap()).json().toString()).apply()
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("mobivious.session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mobivious.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun readAccount(): Account? = runCatching {
        val bytes = Base64.decode(prefs.getString("session", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        Account(json.getString("token"), json.getString("username"), json.getLong("expires"), json.getString("server"))
    }.getOrNull()
    fun save(value: Account?) {
        if (value != account.value) contextGeneration++
        if (value == null) prefs.edit().remove("session").apply() else {
            val json = JSONObject().put("token", value.token).put("username", value.username).put("expires", value.expiresAt).put("server", value.server).toString()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            prefs.edit().putString("session", Base64.encodeToString(cipher.iv + cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)).apply()
        }
        account.value = value
        onContextChanged(positionContext())
    }
    // A local fallback is never replayed to the server; only current playback is uploaded.
    private fun positionContext() = ApiContext(server, account.value, contextGeneration)
    private fun positionsKey(context: ApiContext) = "playback.positions." +
        org.json.JSONArray().put(context.server).put(context.account?.username ?: JSONObject.NULL).toString()
    init {
        // Attribute legacy positions to their saved session, including an expired session,
        // rather than exposing that account's fallback to a guest on the next launch.
        val legacy = prefs.all.filterKeys { it.startsWith("position.") }
        if (legacy.isNotEmpty()) {
            val owner = ApiContext(storedAccount?.server ?: server, storedAccount)
            val values = JSONObject(prefs.getString(positionsKey(owner), "{}")!!)
            legacy.forEach { (key, value) -> if (value is Long && value > 0) values.put(key.removePrefix("position."), value) }
            prefs.edit().apply { putString(positionsKey(owner), values.toString()); legacy.keys.forEach(::remove) }.apply()
        }
    }
    override fun positions(context: ApiContext): Map<String, Long> = runCatching {
        val values = JSONObject(prefs.getString(positionsKey(context), "{}")!!)
        values.keys().asSequence().mapNotNull { id -> values.optLong(id).takeIf { it > 0 }?.let { id to it } }.toMap()
    }.getOrDefault(emptyMap())
    fun position(id: String, context: ApiContext = positionContext()): Long = positions(context)[id] ?: 0
    fun position(id: String, value: Long) = setPosition(positionContext(), id, value)
    @Synchronized override fun setPosition(context: ApiContext, id: String, seconds: Long) {
        val values = JSONObject(positions(context))
        if (seconds <= 0) values.remove(id) else values.put(id, seconds)
        prefs.edit().putString(positionsKey(context), values.toString()).apply()
    }
    @Synchronized override fun clearPositions(context: ApiContext) { prefs.edit().remove(positionsKey(context)).apply() }
    fun clearPositions() = clearPositions(positionContext())
    private fun visibilityKey(kind: String, context: ApiContext) = "visibility.$kind." +
        org.json.JSONArray().put(context.server).put(context.account?.username ?: JSONObject.NULL).toString()
    override fun searchVisibility(context: ApiContext) = runCatching {
        SearchVisibility.parse(JSONObject(prefs.getString(visibilityKey("search", context), "{}")!!))
    }.getOrDefault(SearchVisibility())
    override fun saveSearchVisibility(context: ApiContext, value: SearchVisibility) {
        prefs.edit().putString(visibilityKey("search", context), value.json().toString()).apply()
    }
    override fun blockedSnapshot(context: ApiContext): List<BlockedChannel>? {
        if (context.account == null) return null
        return prefs.getString(visibilityKey("blocks", context), null)?.let { raw ->
            runCatching { BlockedChannel.parse(org.json.JSONArray(raw)) }.getOrNull()
        }
    }
    override fun saveBlockedSnapshot(context: ApiContext, value: List<BlockedChannel>) {
        if (context.account != null) prefs.edit().putString(visibilityKey("blocks", context), org.json.JSONArray(value.map { it.json() }).toString()).apply()
    }
    fun clearVisibilitySnapshot(context: ApiContext) { prefs.edit().remove(visibilityKey("blocks", context)).apply() }
}
