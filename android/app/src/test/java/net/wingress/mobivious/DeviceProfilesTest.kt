package net.wingress.mobivious

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceProfilesTest {
    private val address = "https://example.test"
    private val guest = ApiContext(address, null)
    private val alice = ApiContext(address, Account("first", "Alice", Long.MAX_VALUE, address, "a".repeat(64)))
    private val bob = ApiContext(address, Account("second", "Bob", Long.MAX_VALUE, address, "b".repeat(64)))
    private val channel = "UC" + "a".repeat(22)
    private class Memory : ProfilePersistence {
        val data = mutableMapOf<String, Any?>()
        var fail = false
        override fun values() = data.toMap()
        override fun edit(values: Map<String, Any>, remove: Set<String>): Boolean {
            if (fail) return false
            remove.forEach(data::remove); data.putAll(values); return true
        }
    }
    @Test fun identitiesNormalizeInstancesAndIgnoreSessionsButDistinguishReusedNames() {
        val profile = DeviceProfile.of(alice)
        assertEquals(profile, DeviceProfile.of(alice.copy(server = "https://EXAMPLE.test:443/", account = alice.account!!.copy(token = "replacement", username = "Renamed"), generation = 50)))
        assertNotEquals(profile, DeviceProfile.of(bob.copy(account = bob.account!!.copy(username = "Alice"))))
        assertNotEquals(profile, DeviceProfile.of(alice.copy(server = "https://other.test")))
        assertNotEquals(profile, DeviceProfile.of(guest))
    }
    @Test fun savesSurviveRecreationAndRemainIndependentForGuestAndTwoAccounts() {
        val m = Memory(); val p = DeviceProfiles(m)
        p.write(guest, "background", false); p.write(alice, "pip", false); p.write(bob, "chat.appearance", "bob")
        val reopened = DeviceProfiles(m)
        assertEquals(false, reopened.value(guest, "background")); assertNull(reopened.value(alice, "background"))
        assertEquals(false, reopened.value(alice, "pip")); assertNull(reopened.value(bob, "pip"))
        reopened.purge(alice)
        assertNull(p.value(alice, "pip")); assertEquals(false, p.value(guest, "background")); assertEquals("bob", p.value(bob, "chat.appearance"))
    }
    @Test fun legacyUnownedValuesGoOnlyToGuestsAndMigrationIsRepeatable() {
        val m = Memory(); val p = DeviceProfiles(m)
        m.data.putAll(mapOf("background" to false, "pip" to false, "speed" to 1.5f, "region" to "ID",
            "chat.appearance.$address" to ChatAppearance(fontScale = 145).json().toString(), "subscriptions.sort.$address" to "latest"))
        p.migrate(address, alice.account)
        assertEquals(false, p.value(guest, "background")); assertNull(p.value(alice, "background"))
        assertEquals(1.5f, LocalPreferences.read(JSONObject(p.value(guest, "preferences") as String)).speed)
        assertEquals("latest", p.value(guest, "subscriptions.sort")); assertNull(p.value(alice, "subscriptions.sort"))
        val before = m.data.toMap(); p.migrate(address, alice.account); assertEquals(before, m.data)
        assertFalse(m.data.containsKey("background"))
    }
    @Test fun existingDestinationWinsAndOtherInstanceGuestPreferencesRemainSeparate() {
        val m = Memory(); val p = DeviceProfiles(m); val other = ApiContext("https://other.test", null)
        p.write(guest, "background", true)
        m.data["background"] = false
        m.data["preferences.https://other.test"] = AccountPreferences(speed = 1.75f).json().toString()
        m.data["preferences.https://EXAMPLE.test:443/"] = AccountPreferences(speed = .75f).json().toString()
        p.migrate(address, null)
        assertEquals(true, p.value(guest, "background"))
        assertEquals(1.75f, LocalPreferences.read(JSONObject(p.value(other, "preferences") as String)).speed)
        assertEquals(.75f, LocalPreferences.read(JSONObject(p.value(guest, "preferences") as String)).speed)
    }
    @Test fun failedMigrationAndTransferDoNotDeleteSourcesOrAdvanceMarker() {
        val m = Memory(); val p = DeviceProfiles(m); m.data["background"] = false; m.fail = true
        try { p.migrate(address, null); fail() } catch (_: IOException) { }
        assertEquals(mapOf("background" to false), m.data)
        m.fail = false; p.migrate(address, null); p.write(alice, "pip", false)
        val before = m.data.toMap(); m.fail = true
        try { p.transfer(alice, bob, mapOf("session" to "new")); fail() } catch (_: IOException) { }
        assertEquals(before, m.data)
    }
    @Test fun legacyOwnedPositionsIncludingExpiredSessionNeverBecomeGuestData() {
        val m = Memory(); val p = DeviceProfiles(m); val old = alice.account!!.copy(profileId = null, expiresAt = 1)
        val owner = ApiContext(address, old)
        m.data["position.abcdefghijk"] = 37L
        m.data["visibility.search." + JSONArray().put(address).put(old.username)] = SearchVisibility(true, true).json().toString()
        p.migrate(address, old)
        assertEquals(37L, DeviceProfiles.positions(JSONObject(p.value(owner, "positions") as String))["abcdefghijk"])
        assertNull(p.value(guest, "positions")); assertNull(p.value(alice, "positions"))
        p.transfer(owner, alice, mapOf("session" to "confirmed"))
        assertNotNull(p.value(alice, "positions")); assertNull(p.value(owner, "positions")); assertEquals("confirmed", m.data["session"])
    }
    @Test fun unreadableSavedSessionCannotMakeLegacyAccountProgressGuestData() {
        val m = Memory(); val p = DeviceProfiles(m)
        m.data["session"] = "unreadable"; m.data["position.abcdefghijk"] = 42L
        p.migrate(address, null); p.migrate(address, null)
        assertNull(p.value(guest, "positions")); assertEquals(42L, m.data["position.abcdefghijk"])
        p.write(alice, "pip", false)
        assertNull(p.value(alice, "positions"))
    }
    @Test fun malformedLocalPreferencesDropAccountReferencesAndUnsupportedValues() {
        val p = LocalPreferences.read(JSONObject("""{"speed":"1.5","autoplay":"false","quality_dash":"junk","region":"bad","default_home":"Playlists","default_playlist":"IVprivate","watch_history":true,"notifications_only":true,"chat_show_timestamps":"false"}"""))
        assertEquals(1f, p.speed); assertTrue(p.autoplay); assertEquals("auto", p.qualityDash); assertEquals("US", p.region)
        assertEquals("Popular", p.defaultHome); assertEquals("", p.defaultPlaylist); assertFalse(p.watchHistory); assertFalse(p.notificationsOnly)
        assertTrue(p.chat.timestamps)
        assertEquals(40, LocalPreferences.read(JSONObject("""{"max_results":1.5,"captions":[123],"chat_user_blacklist":""}""")).maxResults)
        assertEquals(ChatAppearance(), DeviceProfiles.appearance(JSONObject("""{"overlay":"true","fontScale":1.5}""")))
        assertTrue(DeviceProfiles.positions(JSONObject("""{"abcdefghijk":1.5,"bbbbbbbbbbb":"12","bad":3,"ccccccccccc":-1}""")).isEmpty())
    }
    @Test fun guestSponsorBlockDeltasPreserveOtherOverridesAndResetOnlyOneChannel() {
        val other = "UC" + "b".repeat(22)
        val before = AccountPreferences(sponsorBlock = SponsorBlockSettings(channels = mapOf(other to SponsorBlockChannel("Other", true))))
        val after = before.copy(sponsorBlock = before.sponsorBlock.copy(channels = before.sponsorBlock.channels + (channel to SponsorBlockChannel("First", false))))
        val saved = LocalPreferences.normalize(before.merge(after.changesFrom(before, includeChannelNames = true)))
        assertEquals("First", saved.sponsorBlock.channels[channel]!!.name)
        assertFalse(after.changesFrom(before).getJSONObject("sponsorblock_channel_overrides").getJSONObject(channel).has("name"))
        assertEquals(setOf(channel, other), saved.sponsorBlock.channels.keys); assertFalse(saved.sponsorBlock.effective(channel).enabled)
        val reset = saved.copy(sponsorBlock = saved.sponsorBlock.copy(channels = saved.sponsorBlock.channels - channel))
        assertEquals(setOf(other), saved.merge(reset.changesFrom(saved)).sponsorBlock.channels.keys)
    }
    @Test fun guestBlockingPersistsAndDoesNotMakeAuthenticatedRequests() = runBlocking {
        MockWebServer().use { server ->
            val url = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ url }, { null }); val saved = mutableMapOf<ApiContext, List<BlockedChannel>>()
            val local = object : VisibilityStore {
                override fun searchVisibility(context: ApiContext) = SearchVisibility()
                override fun saveSearchVisibility(context: ApiContext, value: SearchVisibility) = Unit
                override fun blockedSnapshot(context: ApiContext) = saved[context]
                override fun saveBlockedSnapshot(context: ApiContext, value: List<BlockedChannel>) { saved[context] = value }
            }
            val repo = BlockedRepository(api, local); repo.reset()
            repo.setBlocked(api.context(), channel, "Channel", true)
            assertEquals(setOf(channel), repo.state.value.ids)
            val restored = BlockedRepository(api, local); restored.reset(); restored.refresh()
            assertEquals(setOf(channel), restored.state.value.ids)
            restored.setBlocked(api.context(), channel, "Channel", false)
            assertTrue(restored.state.value.ids.isEmpty()); assertEquals(0, server.requestCount)
        }
    }
    @Test fun authenticationParsesOptionalProfileAndPreferencesMetadataIsSeparate() = runBlocking {
        MockWebServer().use { server ->
            val url = server.url("/").toString().trimEnd('/')
            val account = alice.account!!.copy(server = url)
            var callback: Pair<ApiContext, String>? = null
            val api = InvidiousApi({ url }, { account }, onProfile = { c, id -> callback = c to id })
            server.enqueue(MockResponse().setBody(JSONObject().put("accessToken", "new").put("username", "Alice").put("expiresAt", 123456).put("profileId", "a".repeat(64)).toString()))
            assertEquals("a".repeat(64), api.login("Alice", "password").profileId)
            server.enqueue(MockResponse().setBody("{}").addHeader("X-Invidious-Account-Profile", "a".repeat(64)))
            assertEquals(AccountPreferences(), api.preferences()); assertEquals(api.context() to "a".repeat(64), callback)
            server.enqueue(MockResponse().setBody("""{"accessToken":"legacy","username":"Alice","expiresAt":123456}"""))
            assertNull(api.login("Alice", "password").profileId)
        }
    }
    @Test fun delayedProfileMetadataCannotClaimTheNextSession() = runBlocking {
        MockWebServer().use { server ->
            val url = server.url("/").toString().trimEnd('/')
            var account = alice.account!!.copy(server = url, profileId = null)
            var confirmed = false
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    entered.countDown(); release.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("{}").addHeader("X-Invidious-Account-Profile", "a".repeat(64))
                }
            }
            val api = InvidiousApi({ url }, { account }, onProfile = { _, _ -> confirmed = true })
            val result = async(Dispatchers.Default) { api.preferences() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            account = account.copy(token = "replacement", profileId = "b".repeat(64)); release.countDown()
            try { result.await(); fail("Delayed preferences accepted") } catch (_: CancellationException) { }
            assertFalse(confirmed)
        }
    }
}
