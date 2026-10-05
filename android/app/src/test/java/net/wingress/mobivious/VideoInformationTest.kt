package net.wingress.mobivious

import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VideoInformationTest {
    @Test fun suppliedMetadataPreservesFalseZeroAndMusicCredits() {
        val details = ApiParser.details(JSONObject("""{"videoId":"abcdefghijk","title":"Video","description":"Plain","descriptionHtml":"<b>Rich</b>","likeCount":0,"authorVerified":true,"subCountText":"12.3K","isUpcoming":true,"premiereTimestamp":1800000000,"isListed":false,"genre":"Music","genreUrl":"/channel/UCmusic","license":"","isFamilyFriendly":false,"allowedRegions":["ID","US","ID"],"musicTracks":[{"song":"Song","artist":"Artist","album":"Album","license":"Music license"}],"error":"Premieres soon"}"""))
        assertEquals("<b>Rich</b>", details.descriptionHtml); assertEquals(0L, details.likes); assertEquals(true, details.authorVerified)
        assertEquals("12.3K", details.subscribers); assertEquals(true, details.upcoming); assertEquals(1800000000L, details.premiereTimestamp)
        assertEquals(false, details.listed); assertEquals("", details.license); assertEquals(false, details.familyFriendly)
        assertEquals(listOf("ID", "US"), details.allowedRegions); assertEquals(MusicCredit("Song", "Artist", "Album", "Music license"), details.music.single())
        assertEquals("Premieres soon", details.notice)
    }
    @Test fun oldAndMalformedOptionalFieldsRemainUnknown() {
        for (json in listOf("{}", """{"likeCount":-1,"authorVerified":"true","isFamilyFriendly":null,"isListed":null,"premiereTimestamp":-1,"allowedRegions":null,"license":null,"musicTracks":[{}]}""")) {
            val details = ApiParser.details(JSONObject(json))
            assertNull(details.likes); assertNull(details.authorVerified); assertNull(details.listed); assertNull(details.license)
            assertNull(details.familyFriendly); assertNull(details.allowedRegions); assertNull(details.premiereTimestamp); assertTrue(details.music.isEmpty())
        }
    }
}
