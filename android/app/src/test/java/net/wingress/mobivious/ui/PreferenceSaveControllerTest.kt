package net.wingress.mobivious.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceSaveControllerTest {
    @Test fun slowResponseNeverOverwritesNewerIntentAndIndependentFieldsRemainOrdered() = runTest {
        val release=CompletableDeferred<Unit>(); val calls=mutableListOf<PreferenceEdit<String>>()
        val c=PreferenceSaveController<String>(backgroundScope, { calls+=it; if (calls.size==1) release.await() })
        c.bind("account-a")
        c.request("speed","Playback","1","1.25"); runCurrent()
        c.request("speed","Playback","1","1.5")
        c.request("region","Browsing","US","JP")
        c.request("speed","Playback","1","2")
        assertEquals("2",c.edits.value["speed"]?.requested)
        release.complete(Unit); runCurrent()
        assertEquals(listOf("speed:1.25","speed:2","region:JP"),calls.map { "${it.key}:${it.requested}" })
        assertEquals("1.25",calls[1].confirmed)
        assertTrue(c.edits.value.isEmpty())
    }
    @Test fun failuresKeepIntentRetryUsesLatestAndRevertDoesNotWrite() = runTest {
        var fail=true; val sent=mutableListOf<String>()
        val c=PreferenceSaveController<String>(backgroundScope,{ sent+=it.requested; if(fail) error("Offline") })
        c.bind("guest"); c.request("region","Browsing","US","ID"); runCurrent()
        assertEquals(PreferenceSaveStatus.FAILED,c.edits.value["region"]?.status)
        assertEquals("US",c.edits.value["region"]?.confirmed)
        fail=false; c.retry("region"); runCurrent()
        assertEquals(listOf("ID","ID"),sent)
        fail=true; c.request("region","Browsing","ID","JP");runCurrent()
        c.revert("region");runCurrent(); assertEquals(3,sent.size);assertTrue(c.edits.value.isEmpty())
    }
    @Test fun invalidTextNeverReachesSinkAndBlurOrBackgroundFlushesValidDebounce() = runTest {
        val sent=mutableListOf<String>(); val c=PreferenceSaveController<String>(backgroundScope,{sent+=it.requested})
        c.bind("a");c.request("delay","Chat","0","-",validationError="Enter a number",debounce=true)
        advanceTimeBy(1000);c.flush();runCurrent();assertTrue(sent.isEmpty())
        c.request("delay","Chat","0","10",debounce=true);advanceTimeBy(499);runCurrent();assertTrue(sent.isEmpty())
        c.flush();runCurrent();assertEquals(listOf("10"),sent)
        c.request("delay","Chat","10","20",debounce=true);advanceTimeBy(500);runCurrent()
        assertEquals(listOf("10","20"),sent)
    }
    @Test fun contextSwitchDiscardsSensitiveIntentsAndLateResultsCannotPublish() = runTest {
        val release=CompletableDeferred<Unit>();val sent=mutableListOf<String>()
        val c=PreferenceSaveController<String>(backgroundScope,{sent+="${it.context}:${it.requested}";if(it.context=="a")release.await()})
        c.bind("a");c.request("filter","Chat","","private-a");runCurrent()
        c.request("text","Chat","","unsent-a",debounce=true)
        c.bind("b"); assertTrue(c.edits.value.isEmpty())
        c.request("filter","Chat","","public-b");release.complete(Unit);runCurrent()
        advanceTimeBy(1000);runCurrent()
        assertEquals(listOf("a:private-a","b:public-b"),sent);assertTrue(c.edits.value.isEmpty())
    }
    @Test fun returningToConfirmedValueWhileAnOlderWriteRunsStillRestoresIt() = runTest {
        val release = CompletableDeferred<Unit>(); val sent = mutableListOf<String>()
        var durable = "1"
        val c = PreferenceSaveController<String>(backgroundScope, { edit ->
            sent += edit.requested
            if (sent.size == 1) release.await()
            durable = edit.requested
        })
        c.bind("a"); c.request("speed","Playback","1","2"); runCurrent()
        c.request("speed","Playback","1","1"); release.complete(Unit); runCurrent()
        assertEquals(listOf("2","1"),sent); assertEquals("1",durable); assertTrue(c.edits.value.isEmpty())
    }
    @Test fun anOlderFailureCannotTurnTheNewerQueuedValueIntoAnError() = runTest {
        val release = CompletableDeferred<Unit>(); val sent = mutableListOf<String>()
        val c = PreferenceSaveController<String>(backgroundScope, { edit ->
            sent += edit.requested
            if (edit.requested == "old") { release.await(); error("Old request failed") }
        })
        c.bind("a"); c.request("words","Chat","","old"); runCurrent()
        c.request("words","Chat","","new"); release.complete(Unit); runCurrent()
        assertEquals(listOf("old","new"),sent); assertTrue(c.edits.value.isEmpty())
    }
    @Test fun revertingInvalidOrUnsentTextDoesNotSendItDuringLifecycleFlush() = runTest {
        val sent = mutableListOf<String>()
        val c = PreferenceSaveController<String>(backgroundScope,{sent += it.requested})
        c.bind("a"); c.request("color","SponsorBlock","#00FF00","#invalid","Enter a hex color",true)
        c.revert("color"); c.request("words","Chat","","private draft",debounce=true)
        c.revert("words"); c.flush(); advanceTimeBy(1000); runCurrent()
        assertTrue(sent.isEmpty()); assertTrue(c.edits.value.isEmpty())
    }

}
