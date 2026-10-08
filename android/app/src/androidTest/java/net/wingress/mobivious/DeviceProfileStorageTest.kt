package net.wingress.mobivious

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.CancellationException
import net.wingress.mobivious.data.*
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceProfileStorageTest {
    private lateinit var context: Context
    private lateinit var store: SessionStore
    private lateinit var name: String
    private val address = "https://profiles.test"
    private fun account(username: String, id: Char, token: String = "token") = Account(token, username, Long.MAX_VALUE, address, id.toString().repeat(64))
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        name = "device-profile-test-" + UUID.randomUUID()
        context = object : ContextWrapper(app) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = app.getSharedPreferences(name, mode)
        }
        store = SessionStore(context); store.server = address
    }
    @After fun cleanup() { context.baseContext().deleteSharedPreferences(name) }
    private fun Context.baseContext(): Context = (this as ContextWrapper).baseContext
    private fun stale(block: () -> Unit) {
        try { block(); fail("A stale device save was accepted") } catch (_: CancellationException) { }
    }
    @Test fun guestTwoAccountsAndRestartRestoreOnlyTheirOwnDeviceSaves() {
        store.background = false; store.pip = false; store.chatAppearance(ChatAppearance(fontScale = 145))
        store.guestDeArrow(AccountPreferences(speed = .75f, savePosition = true)); store.position("abcdefghijk", 35)
        store.saveSearchVisibility(store.positionContext(), SearchVisibility(true, true))
        store.save(account("Alice", 'a'))
        assertTrue(store.background); assertTrue(store.pip); assertEquals(100, store.chatAppearance().fontScale)
        assertEquals(1f, store.initialPreferences().speed); assertEquals(0L, store.position("abcdefghijk"))
        store.background = false; store.chatAppearance(ChatAppearance(fontScale = 170)); store.position("abcdefghijk", 70)
        store.subscriptionSort(store.positionContext(), SubscriptionSort.LATEST)
        store.save(null)
        assertFalse(store.pip); assertEquals(145, store.chatAppearance().fontScale); assertEquals(35L, store.position("abcdefghijk"))
        assertEquals(.75f, store.initialPreferences().speed)
        store.save(account("Bob", 'b'))
        assertTrue(store.background); assertEquals(100, store.chatAppearance().fontScale); assertEquals(0L, store.position("abcdefghijk"))
        store.pip = false
        store.save(account("Alice", 'a', "renewed"))
        store = SessionStore(context)
        assertEquals("a".repeat(64), store.account.value!!.profileId)
        assertFalse(store.background); assertTrue(store.pip); assertEquals(170, store.chatAppearance().fontScale)
        assertEquals(SubscriptionSort.LATEST, store.subscriptionSort(store.positionContext())); assertEquals(70L, store.position("abcdefghijk"))
    }
    @Test fun capturedWritesRejectSwitchesAndTokenReplacement() {
        val guest = store.positionContext(); store.save(account("Alice", 'a'))
        stale { store.background(false, guest) }; stale { store.pip(false, guest) }
        stale { store.chatAppearance(ChatAppearance(fontScale = 200), guest) }
        stale { store.guestDeArrow(AccountPreferences(speed = 2f), guest) }
        stale { store.saveSearchVisibility(guest, SearchVisibility(true, true)) }
        stale { store.chatTiming("abcdefghijk", 4000, guest) }
        val old = store.positionContext(); store.save(account("Alice", 'a', "replacement"))
        stale { store.setPosition(old, "abcdefghijk", 40) }; stale { store.confirmProfile(old, "b".repeat(64)) }
        assertTrue(store.background); assertEquals(100, store.chatAppearance().fontScale)
    }
    @Test fun trendingCategoryPersistsByAccountAndInstanceAndRejectsStaleWrites() {
        val guest = store.positionContext()
        store.trendingCategory(guest, TrendingCategory.GAMING)
        store.save(account("Alice", 'a'))
        assertEquals(TrendingCategory.LIVESTREAMS, store.trendingCategory(store.positionContext()))
        stale { store.trendingCategory(guest, TrendingCategory.LIVESTREAMS) }
        store.trendingCategory(store.positionContext(), TrendingCategory.GAMING)
        store.save(account("Bob", 'b'))
        assertEquals(TrendingCategory.LIVESTREAMS, store.trendingCategory(store.positionContext()))
        store.save(null); store.server = "https://other.test"
        assertEquals(TrendingCategory.LIVESTREAMS, store.trendingCategory(store.positionContext()))
        store.server = address
        assertEquals(TrendingCategory.GAMING, store.trendingCategory(store.positionContext()))
        store.save(account("Alice", 'a', "renewed")); store = SessionStore(context)
        assertEquals(TrendingCategory.GAMING, store.trendingCategory(store.positionContext()))
    }
    @Test fun stableRenamesAndUsernameReuseRemainSeparate() {
        store.save(account("Alice", 'a')); store.pip = false; store.position("abcdefghijk", 50)
        store.replaceAccount(store.positionContext(), account("Renamed", 'a', "new"))
        assertFalse(store.pip); assertEquals(50L, store.position("abcdefghijk"))
        store.save(null); store.save(account("Alice", 'b'))
        assertTrue(store.pip); assertEquals(0L, store.position("abcdefghijk"))
    }
    @Test fun legacyRenameAndConfirmedSessionUpgradeTransferOnlyTheirOwnProfile() {
        store.save(account("Alice", 'a').copy(profileId = null))
        store.background = false; store.position("abcdefghijk", 60)
        store.replaceAccount(store.positionContext(), account("Renamed", 'a').copy(profileId = null))
        assertFalse(store.background); assertEquals(60L, store.position("abcdefghijk"))
        store.confirmProfile(store.positionContext(), "a".repeat(64))
        store = SessionStore(context)
        assertEquals("a".repeat(64), store.account.value!!.profileId); assertFalse(store.background); assertEquals(60L, store.position("abcdefghijk"))
        store.save(null); store.save(account("Renamed", 'b'))
        assertTrue(store.background); assertEquals(0L, store.position("abcdefghijk"))
    }
    @Test fun switchingInstancesAndBackDoesNotDeleteGuestSaves() {
        store.pip = false; store.position("abcdefghijk", 30)
        store.server = "https://other.test"
        assertTrue(store.pip); assertEquals(0L, store.position("abcdefghijk")); store.background = false
        store.server = address
        assertFalse(store.pip); assertTrue(store.background); assertEquals(30L, store.position("abcdefghijk"))
    }
    @Test fun expiryRetainsAccountDeviceSavesWithoutExposingThemToGuest() {
        store.pip = false; store.save(account("Alice", 'a')); store.chatAppearance(ChatAppearance(fontScale = 190))
        store.save(account("Alice", 'a').copy(expiresAt = 1))
        store = SessionStore(context)
        assertNull(store.account.value); assertFalse(store.pip); assertEquals(100, store.chatAppearance().fontScale)
        store.save(account("Alice", 'a')); assertEquals(190, store.chatAppearance().fontScale)
    }
    @Test fun deletionPurgesOnlySelectedAccountAndGuestOverridesPersist() {
        val channel = "UC" + "a".repeat(22)
        store.guestDeArrow(AccountPreferences(sponsorBlock = SponsorBlockSettings(channels = mapOf(channel to SponsorBlockChannel("Guest", true)))))
        store.saveBlockedSnapshot(store.positionContext(), listOf(BlockedChannel(channel, "Guest")))
        store.save(account("Alice", 'a')); store.pip = false
        store.save(account("Bob", 'b')); store.background = false
        store.save(account("Alice", 'a')); store.deleteProfile(store.positionContext()); store.save(null)
        assertTrue(store.guestDeArrow().sponsorBlock.effective(channel).enabled)
        assertEquals(channel, store.blockedSnapshot(store.positionContext())!!.single().id)
        store.save(account("Bob", 'b')); assertFalse(store.background)
        store.save(account("Alice", 'a')); assertTrue(store.pip)
    }
    @Test fun legacyMigrationKeepsUnownedValuesGuestAndExpiredPositionsPrivate() {
        store.save(account("Alice", 'a').copy(profileId = null, expiresAt = 1))
        val prefs = context.getSharedPreferences("mobivious", Context.MODE_PRIVATE)
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("device.v2.") }.forEach { remove(it) } }.commit()
        prefs.edit().remove("device.profiles.version").putBoolean("background", false).putFloat("speed", 1.5f)
            .putString("chat.appearance.$address", ChatAppearance(fontScale = 150).json().toString())
            .putLong("position.abcdefghijk", 55).putString("visibility.search." + JSONArray().put(address).put("Alice"), SearchVisibility(true, true).json().toString()).commit()
        store = SessionStore(context)
        assertNull(store.account.value); assertFalse(store.background); assertEquals(150, store.chatAppearance().fontScale)
        assertEquals(1.5f, store.guestDeArrow().speed); assertEquals(0L, store.position("abcdefghijk"))
        store.save(account("Alice", 'a').copy(profileId = null))
        assertTrue(store.background); assertEquals(100, store.chatAppearance().fontScale); assertEquals(55L, store.position("abcdefghijk"))
        assertEquals(SearchVisibility(true, true), store.searchVisibility(store.positionContext()))
    }
    @Test fun malformedEncryptedSessionLeavesUnknownLegacyProgressPrivate() {
        val prefs = context.getSharedPreferences("mobivious", Context.MODE_PRIVATE)
        prefs.edit().remove("device.profiles.version").putString("session", "unreadable")
            .putLong("position.abcdefghijk", 42L).commit()
        store = SessionStore(context)
        assertNull(store.account.value); assertEquals(0L, store.position("abcdefghijk"))
        assertEquals(42L, prefs.getLong("position.abcdefghijk", 0))
        store.save(account("Alice", 'a')); assertEquals(0L, store.position("abcdefghijk"))
    }
}
