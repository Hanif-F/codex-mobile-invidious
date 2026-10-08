package net.wingress.mobivious.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

private class SharedProfilePersistence(private val prefs: SharedPreferences, private val onError: (String?) -> Unit) : ProfilePersistence {
    override fun values(): Map<String, Any?> = prefs.all
    @Synchronized override fun edit(values: Map<String, Any>, remove: Set<String>): Boolean {
        val before = prefs.all
        fun SharedPreferences.Editor.put(name: String, value: Any?) {
            when (value) {
                is Boolean -> putBoolean(name, value)
                is Int -> putInt(name, value)
                is Long -> putLong(name, value)
                is Float -> putFloat(name, value)
                is String -> putString(name, value)
                is Set<*> -> putStringSet(name, value.filterIsInstance<String>().toSet())
                else -> remove(name)
            }
        }
        val editor = prefs.edit()
        remove.forEach { editor.remove(it) }; values.forEach { (k, v) -> editor.put(k, v) }
        if (editor.commit()) { onError(null); return true }
        // commit also changes SharedPreferences' memory on failure. Restore that view.
        prefs.edit().apply { (remove + values.keys).forEach { put(it, before[it]) } }.apply()
        onError("Could not save data on this device. Please try again.")
        return false
    }
}

class SessionStore(context: Context) : LocalPlaybackPositions, VisibilityStore {
    private val prefs = context.getSharedPreferences("mobivious", Context.MODE_PRIVATE)
    val storageError = MutableStateFlow<String?>(null)
    private val persistence = SharedProfilePersistence(prefs) { storageError.value = it }
    private val profiles = DeviceProfiles(persistence)
    private val storedAccount = readAccount()
    val account = MutableStateFlow(storedAccount?.takeIf { it.expiresAt > System.currentTimeMillis() / 1000 })
    @Volatile var contextGeneration: Long = 0
        private set
    var onContextChanged: (ApiContext) -> Unit = {}
    private fun checked(values: Map<String, Any>, remove: Set<String> = emptySet()) {
        if (!persistence.edit(values, remove)) throw java.io.IOException("Could not save data on this device. Please try again.")
    }
    var server: String
        get() = (prefs.all["server"] as? String)?.let { runCatching { DeviceProfile.server(it) }.getOrNull() } ?: "https://invidious.wingress.net"
        set(value) {
            val address = DeviceProfile.server(value)
            val changed = synchronized(this) {
                if (address == server) return
                checked(mapOf("server" to address)); contextGeneration++
                positionContext()
            }
            onContextChanged(changed)
        }
    fun positionContext() = ApiContext(server, account.value, contextGeneration)
    @Synchronized private fun current(context: ApiContext) {
        if (context != positionContext()) throw CancellationException("Account or instance changed")
    }
    init {
        try { profiles.migrate(server, storedAccount) }
        catch (e: Exception) { storageError.value = e.message ?: "Could not upgrade device saves. Restart to retry." }
    }
    var background: Boolean
        get() = profiles.value(positionContext(), "background") as? Boolean ?: true
        set(value) = background(value, positionContext())
    @Synchronized fun background(value: Boolean, context: ApiContext) { current(context); profiles.write(context, "background", value) }
    var pip: Boolean
        get() = profiles.value(positionContext(), "pip") as? Boolean ?: true
        set(value) = pip(value, positionContext())
    @Synchronized fun pip(value: Boolean, context: ApiContext) { current(context); profiles.write(context, "pip", value) }
    fun subscriptionSort(context: ApiContext) = SubscriptionSort.saved(profiles.value(context, "subscriptions.sort") as? String)
    @Synchronized fun subscriptionSort(context: ApiContext, value: SubscriptionSort) {
        current(context); profiles.write(context, "subscriptions.sort", value.key)
    }
    fun chatAppearance(context: ApiContext = positionContext()): ChatAppearance = runCatching {
        DeviceProfiles.appearance(JSONObject(profiles.value(context, "chat.appearance") as? String ?: "{}"))
    }.getOrDefault(ChatAppearance())
    @Synchronized fun chatAppearance(value: ChatAppearance, context: ApiContext = positionContext()) {
        current(context); profiles.write(context, "chat.appearance", value.bounded().json().toString())
    }
    fun chatTiming(videoId: String, context: ApiContext): Int = (profiles.value(context, "chat.timing." + videoId) as? Int)?.takeIf { it in -3_600_000..3_600_000 } ?: 0
    @Synchronized fun chatTiming(videoId: String, value: Int, context: ApiContext) {
        current(context); require(context.account == null && SponsorBlockRules.validVideo(videoId) && value in -3_600_000..3_600_000)
        profiles.write(context, "chat.timing." + videoId, value)
    }
    fun guestDeArrow(context: ApiContext = ApiContext(server, null, contextGeneration)): AccountPreferences = runCatching {
        require(context.account == null)
        LocalPreferences.read(JSONObject(profiles.value(context, "preferences") as? String ?: "{}"))
    }.getOrElse { LocalPreferences.normalize(AccountPreferences()) }
    @Synchronized fun guestDeArrow(value: AccountPreferences, context: ApiContext = positionContext()) {
        current(context); require(context.account == null)
        profiles.write(context, "preferences", LocalPreferences.normalize(value).json().toString())
    }
    fun initialPreferences(context: ApiContext = positionContext()) = if (context.account == null) guestDeArrow(context) else AccountPreferences()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("mobivious.session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mobivious.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun readAccount(): Account? = runCatching {
        val bytes = Base64.decode(prefs.all["session"] as? String ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        Account(json.getString("token"), json.getString("username"), json.getLong("expires"), json.getString("server"), DeviceProfile.validId(json.opt("profileId") as? String))
    }.getOrNull()
    private fun encrypted(value: Account): String {
        val json = JSONObject().put("token", value.token).put("username", value.username).put("expires", value.expiresAt).put("server", value.server)
            .put("profileId", value.profileId ?: JSONObject.NULL).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)
    }
    private fun publish(value: Account?) {
        if (value != account.value) contextGeneration++
        account.value = value
    }
    fun save(value: Account?) {
        val changed = synchronized(this) {
            if (value == null) checked(emptyMap(), setOf("session")) else checked(mapOf("session" to encrypted(value)))
            publish(value); positionContext()
        }
        onContextChanged(changed)
    }
    /** Only a successful response for this exact live session may claim its legacy data. */
    fun confirmProfile(context: ApiContext, id: String) {
        val changed = synchronized(this) {
            current(context)
            val before = context.account ?: return
            val profileId = DeviceProfile.validId(id) ?: return
            if (before.profileId == profileId) return
            // A known stable identity must never be rebound by a response.
            if (before.profileId != null) throw CancellationException("Account identity changed")
            val after = before.copy(profileId = profileId)
            profiles.transfer(context, context.copy(account = after), mapOf("session" to encrypted(after)))
            publish(after); positionContext()
        }
        onContextChanged(changed)
    }
    fun replaceAccount(context: ApiContext, value: Account) {
        val changed = synchronized(this) {
            current(context)
            val before = context.account ?: throw CancellationException("Account changed")
            if (before.profileId != null && value.profileId != null && before.profileId != value.profileId) throw CancellationException("Account identity changed")
            val replacement = value.copy(profileId = value.profileId ?: before.profileId)
            profiles.transfer(context, context.copy(account = replacement), mapOf("session" to encrypted(replacement)))
            publish(replacement); positionContext()
        }
        onContextChanged(changed)
    }
    @Synchronized fun deleteProfile(context: ApiContext) { current(context); profiles.purge(context) }
    override fun positions(context: ApiContext): Map<String, Long> = runCatching {
        DeviceProfiles.positions(JSONObject(profiles.value(context, "positions") as? String ?: "{}"))
    }.getOrDefault(emptyMap())
    fun position(id: String, context: ApiContext = positionContext()): Long = positions(context)[id] ?: 0
    fun position(id: String, value: Long) = setPosition(positionContext(), id, value)
    @Synchronized override fun setPosition(context: ApiContext, id: String, seconds: Long) {
        current(context); require(SponsorBlockRules.validVideo(id) && seconds in 0..Int.MAX_VALUE.toLong())
        val values = JSONObject(positions(context))
        if (seconds == 0L) values.remove(id) else values.put(id, seconds)
        profiles.write(context, "positions", values.toString())
    }
    @Synchronized override fun clearPositions(context: ApiContext) { current(context); profiles.write(context, "positions", null) }
    fun clearPositions() = clearPositions(positionContext())
    override fun searchVisibility(context: ApiContext) = runCatching {
        SearchVisibility.parse(JSONObject(profiles.value(context, "search") as? String ?: "{}"))
    }.getOrDefault(SearchVisibility())
    @Synchronized override fun saveSearchVisibility(context: ApiContext, value: SearchVisibility) {
        current(context); profiles.write(context, "search", value.json().toString())
    }
    override fun blockedSnapshot(context: ApiContext): List<BlockedChannel>? = (profiles.value(context, "blocks") as? String)?.let {
        runCatching { BlockedChannel.parse(JSONArray(it)) }.getOrNull()
    }
    @Synchronized override fun saveBlockedSnapshot(context: ApiContext, value: List<BlockedChannel>) {
        current(context)
        profiles.write(context, "blocks", JSONArray(value.filter { ContentVisibility.validChannel(it.id) }.distinctBy { it.id }
            .map { it.copy(name = it.name.take(200)).json() }).toString())
    }
    @Synchronized fun clearVisibilitySnapshot(context: ApiContext) { current(context); profiles.write(context, "blocks", null) }
}
