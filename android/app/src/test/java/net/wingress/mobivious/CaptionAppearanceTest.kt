package net.wingress.mobivious

import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CaptionAppearanceTest {
    @Test fun defaultsAndEverySupportedChoiceRoundTrip() {
        assertEquals(CaptionAppearance(), CaptionAppearance.parse(null))
        for (size in CaptionAppearance.sizes) for (opacity in CaptionAppearance.opacities) {
            val value = CaptionAppearance(size, "#FFFF00", opacity, "#0000FF", opacity, "#00FF00", opacity, "script", "raised")
            assertEquals(value, CaptionAppearance.parse(value.json().toString()))
        }
        for ((font, _) in CaptionAppearance.fonts) for ((edge, _) in CaptionAppearance.edges)
            assertEquals(CaptionAppearance(font = font, edge = edge), CaptionAppearance.parse(CaptionAppearance(font = font, edge = edge).json()))
    }
    @Test fun corruptAndUnsupportedValuesRestoreSafeDefaults() {
        assertEquals(CaptionAppearance(), CaptionAppearance.parse("invalid"))
        assertEquals(CaptionAppearance(), CaptionAppearance.parse(JSONObject("""{"size":100.5,"textOpacity":"0","backgroundOpacity":101,"windowOpacity":-1,"textColor":"#ff0000","font":"remote-font","edge":42} """)))
        assertEquals(CaptionAppearance(), CaptionAppearance(size = Int.MAX_VALUE, textColor = "invalid", font = "missing").normalized())
        assertEquals(0, CaptionAppearance.parse("""{"textOpacity":0} """).textOpacity)
    }
    @Test fun profilesKeepStylesAcrossRecreationAndFailedWritesDoNotReplaceThem() {
        var fail = false
        val data = mutableMapOf<String, Any?>()
        val persistence = object : ProfilePersistence {
            override fun values() = data.toMap()
            override fun edit(values: Map<String, Any>, remove: Set<String>): Boolean {
                if (fail) return false
                remove.forEach(data::remove); data.putAll(values); return true
            }
        }
        val guest = ApiContext("https://example.test", null)
        val account = guest.copy(account = Account("token", "Alice", Long.MAX_VALUE, guest.server, "a".repeat(64)))
        val other = guest.copy(server = "https://other.test")
        val profiles = DeviceProfiles(persistence)
        val styles = listOf(CaptionAppearance(size = 150), CaptionAppearance(font = "script"), CaptionAppearance(windowOpacity = 100))
        listOf(guest, account, other).zip(styles).forEach { (context, style) -> profiles.write(context, "caption.appearance", style.json().toString()) }
        val reopened = DeviceProfiles(persistence)
        listOf(guest, account, other).zip(styles).forEach { (context, style) -> assertEquals(style, CaptionAppearance.parse(reopened.value(context, "caption.appearance") as? String)) }
        fail = true
        assertThrows(java.io.IOException::class.java) { reopened.write(guest, "caption.appearance", CaptionAppearance().json().toString()) }
        assertEquals(styles.first(), CaptionAppearance.parse(reopened.value(guest, "caption.appearance") as? String))
        fail = false; reopened.purge(account)
        assertNull(reopened.value(account, "caption.appearance")); assertNotNull(reopened.value(guest, "caption.appearance"))
    }
}
