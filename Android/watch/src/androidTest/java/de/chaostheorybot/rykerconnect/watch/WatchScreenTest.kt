package de.chaostheorybot.rykerconnect.watch

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.widget.Button
import com.google.android.gms.wearable.MessageEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** UI/protocol fixtures only; these do not simulate a successful real watch/phone pairing. */
class WatchScreenTest {
    private fun field(a:WatchRideActivity,name:String)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}
    private fun call(a:WatchRideActivity,name:String)=a.javaClass.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun reply(a:WatchRideActivity,id:String,mode:String) {
        val status=JSONObject().put("state",mode).put("owner",a.packageName).put("token","fixture-token")
            .put("observedAt",SystemClock.elapsedRealtime()).put("meters",12345).put("elapsedMs",3600000).put("pausedMs",600000)
        val bytes=JSONObject().put("id",id).put("ok",true).put("message","UI test fixture").put("status",status).toString().toByteArray()
        a.onMessageReceived(object:MessageEvent {
            override fun getData()=bytes
            override fun getPath()="/ryker/ride/reply"
            override fun getSourceNodeId()="fixture-phone"
            override fun getRequestId()=1
        })
    }
    private fun seed(a:WatchRideActivity,mode:String) {
        (field(a,"handler").get(a) as Handler).removeCallbacksAndMessages(null)
        field(a,"node").set(a,"fixture-phone")
        field(a,"requestId").set(a,"seed")
        field(a,"requestAction").set(a,"STATUS")
        field(a,"pending").setBoolean(a,true)
        reply(a,"seed",mode)
    }

    @Test fun backgroundRefreshKeepsFreshRideControlsEnabled() {
        ActivityScenario.launch(WatchRideActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                for(mode in listOf("idle","recording","paused","interrupted")) {
                    seed(a,mode)
                    val primary=field(a,"primary").get(a) as Button
                    val label=primary.text.toString()
                    call(a,"refresh")
                    assertTrue("Status polling must not disable $label",primary.isEnabled)
                    assertEquals(label,primary.text.toString())
                    assertEquals(1f,primary.alpha)
                    if(mode!="idle")assertTrue((field(a,"end").get(a) as Button).isEnabled)
                }
            }
        }
    }

    @Test fun tappingDuringRefreshSupersedesStatusAndWaitsForMatchingCommandReply() {
        ActivityScenario.launch(WatchRideActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                seed(a,"idle")
                call(a,"refresh")
                val statusId=field(a,"requestId").get(a) as String
                val primary=field(a,"primary").get(a) as Button
                primary.performClick()
                val commandId=field(a,"requestId").get(a) as String
                assertNotEquals(statusId,commandId)
                assertEquals("START",field(a,"requestAction").get(a))
                assertEquals("Confirming…",primary.text.toString())
                assertFalse(primary.isEnabled)
                // Even an extra click or explicit refresh cannot replace an in-flight command.
                primary.performClick();call(a,"refresh")
                assertEquals(commandId,field(a,"requestId").get(a))
                reply(a,statusId,"idle")
                assertEquals(commandId,field(a,"requestId").get(a))
                assertFalse(primary.isEnabled)
                reply(a,commandId,"recording")
                assertEquals("Pause Ride",primary.text.toString())
                assertTrue(primary.isEnabled)
                // A later status refresh must not reuse START's busy label.
                call(a,"refresh")
                assertEquals("Pause Ride",primary.text.toString())
                assertTrue(primary.isEnabled)
            }
        }
    }

    @Test fun missingAndStalePhoneStateStillDisableControls() {
        ActivityScenario.launch(WatchRideActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                seed(a,"recording")
                field(a,"confirmed").setLong(a,SystemClock.elapsedRealtime()-16_000)
                call(a,"render")
                assertFalse((field(a,"primary").get(a) as Button).isEnabled)
                assertFalse((field(a,"end").get(a) as Button).isEnabled)
                field(a,"state").set(a,null)
                call(a,"render")
                assertFalse((field(a,"primary").get(a) as Button).isEnabled)
            }
        }
    }

    @Test fun onlyMatchingAcknowledgmentsChangeControls() {
        ActivityScenario.launch(WatchRideActivity::class.java).use { scenario ->
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            fun field(a:WatchRideActivity,name:String)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}
            fun receive(a:WatchRideActivity,mode:String,id:String="fixture") {
                (field(a,"handler").get(a) as Handler).removeCallbacksAndMessages(null)
                field(a,"node").set(a,"fixture-phone");field(a,"requestId").set(a,"fixture");field(a,"pending").setBoolean(a,true)
                val status=JSONObject().put("state",mode).put("meters",12345).put("elapsedMs",3600000).put("pausedMs",600000).put("miles",true)
                val bytes=JSONObject().put("id",id).put("ok",true).put("message","UI test fixture").put("status",status).toString().toByteArray()
                a.onMessageReceived(object:MessageEvent {
                    override fun getData()=bytes
                    override fun getPath()="/ryker/ride/reply"
                    override fun getSourceNodeId()="fixture-phone"
                    override fun getRequestId()=1
                })
            }
            for((mode,label) in listOf("idle" to "Start Ride","recording" to "Pause Ride","paused" to "Resume Ride")) {
                scenario.onActivity { a ->
                    receive(a,mode)
                    assertEquals(label,(field(a,"primary").get(a) as Button).text.toString())
                    assertEquals(if(mode=="idle")View.GONE else View.VISIBLE,(field(a,"end").get(a) as Button).visibility)
                    assertTrue((field(a,"primary").get(a) as Button).isEnabled)
                }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(300)
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                File(instrumentation.targetContext.cacheDir,"watch-$mode-fixture.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            scenario.onActivity { a ->
                receive(a,"idle","stale-reply")
                assertEquals("Resume Ride",(field(a,"primary").get(a) as Button).text.toString())
            }
        }
    }
}
