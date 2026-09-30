package de.chaostheorybot.rykerconnect.ride

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.*

/** Runs in Phone while ESP's real recorder service runs in a separate, non-instrumented process. */
class WatchServiceIntegrationTest {
    @Test fun controlsTheOtherEditionAndConfirmsActualServiceState() = runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(SharedLibrary.PHONE,c.packageName);SharedLibrary.awaitReady()
        fun state()=JSONObject(c.contentResolver.call(SharedLibrary.uri(SharedLibrary.FULL),"ride-state",null,null)!!.getString("json")!!)
        fun command(action:String,s:JSONObject)=JSONObject().put("id",UUID.randomUUID().toString()).put("action",action)
            .put("owner",s.getString("owner")).put("token",s.getString("token")).put("observedAt",s.getLong("observedAt"))
        assertEquals("idle",state().getString("state"))
        var ended=false
        try {
            val started=RideControl.dispatch(c,command("START",state()),true)
            assertTrue(started.toString(),started.getBoolean("ok"));val id=started.getJSONObject("status").getString("rideId")
            assertEquals("recording",state().getString("state"))
            assertEquals(SharedLibrary.FULL,RideControl.state(c).getString("owner"))
            val pause=command("PAUSE",state());assertTrue(RideControl.dispatch(c,pause,true).getBoolean("ok"))
            assertEquals("paused",state().getString("state"));delay(100)
            assertTrue(RideControl.dispatch(c,command("RESUME",state()),true).getBoolean("ok"))
            val duplicate=RideControl.dispatch(c,pause,true)
            assertTrue(duplicate.getBoolean("alreadyHandled"));assertEquals("recording",duplicate.getJSONObject("status").getString("state"))
            assertEquals(id,state().getString("rideId"))
            assertTrue(RideControl.dispatch(c,command("PAUSE",state()),true).getBoolean("ok"))
            val finish=command("END",state());assertTrue(RideControl.dispatch(c,finish,true).getBoolean("ok"));ended=true
            assertEquals("idle",state().getString("state"))
            assertEquals(id,state().getString("rideId"))
            assertTrue(RideControl.dispatch(c,finish,true).getBoolean("alreadyHandled"))
        }finally{if(!ended&&state().getString("state")!="idle")RideControl.dispatch(c,command("END",state()),false)}
        Unit
    }
}
