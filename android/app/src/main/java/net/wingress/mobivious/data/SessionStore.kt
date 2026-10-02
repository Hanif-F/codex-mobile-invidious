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

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("mobivious", Context.MODE_PRIVATE)
    val account = MutableStateFlow(readAccount())
    var server: String
        get() = prefs.getString("server", "https://mobivious.wingress.net")!!
        set(value) { prefs.edit().putString("server", value).apply() }
    var background: Boolean
        get() = prefs.getBoolean("background", true)
        set(value) { prefs.edit().putBoolean("background", value).apply() }
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
            .takeIf { it.expiresAt > System.currentTimeMillis() / 1000 }
    }.getOrNull()
    fun save(value: Account?) {
        if (value == null) prefs.edit().remove("session").apply() else {
            val json = JSONObject().put("token", value.token).put("username", value.username).put("expires", value.expiresAt).put("server", value.server).toString()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            prefs.edit().putString("session", Base64.encodeToString(cipher.iv + cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)).apply()
        }
        account.value = value
    }
    // A local fallback is never replayed to the server; only current playback is uploaded.
    fun position(id: String): Long = prefs.getLong("position.$id", 0)
    fun position(id: String, value: Long) { prefs.edit().putLong("position.$id", value).apply() }
    fun clearPositions() { prefs.edit().apply { prefs.all.keys.filter { it.startsWith("position.") }.forEach(::remove) }.apply() }
}
