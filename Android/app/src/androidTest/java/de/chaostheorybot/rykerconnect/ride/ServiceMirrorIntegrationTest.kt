package de.chaostheorybot.rykerconnect.ride

import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Ordered phases use unique synthetic IDs; existing emulator records remain intact. */
class ServiceMirrorIntegrationTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val type get()=args.getString("serviceType")!!
    private val entry get()=args.getString("serviceEntry")!!
    private val fuel get()=args.getString("serviceFuel")!!
    private val origin="http://127.0.0.1:8895"
    private fun connect(){DadRides.configure(origin,File(c.cacheDir,"shared-test-key").readText().trim());DadRides.enable(true);SharedLibrary.enable(true)}
    private fun metrics()=OkHttpClient().newCall(Request.Builder().url("$origin/__test/metrics").build()).execute().use{it.body.string()}
    private fun mirrorFixture(){
        SharedLibrary.exportSnapshot().delete()
        val snapshot=SharedLibrary.serviceSnapshot().getJSONObject("records")
        val records=JSONObject();for(key in listOf("software/serviceTypes/$type","software/maintenance/$entry","software/fuel/$fuel"))if(snapshot.has(key))records.put(key,snapshot.get(key))
        ServiceMirror.sync(c,JSONObject().put("version",1).put("records",records))
    }
    private fun remote(key:String)=DadRides.request("services").getJSONArray(key).let{a->(0 until a.length()).map{a.getJSONObject(it)}}
    @Test fun phoneSeedsAndUploadsPrivateServices(){
        assertTrue(BuildConfig.PHONE_EDITION);connect();val before=metrics()
        SoftwareStore.mutate{data->
            data.put("serviceTypes",JSONArray(SoftwareStore.records("serviceTypes")+ServiceCatalog.definition("TEST service",type).put("configured",true).put("intervalKm",1609.344).put("intervalDays",90)))
            data.put("maintenance",JSONArray(SoftwareStore.records("maintenance")+JSONObject().put("id",entry).put("serviceId",type).put("name","TEST service").put("time",1790035200000L).put("odometerKm",1.609344).put("notes","Phone service note").put("receipt","PRIVATE_RECEIPT").put("location",JSONObject().put("lat",61.0))))
            data.put("fuel",JSONArray(SoftwareStore.records("fuel")+JSONObject().put("id",fuel).put("time",1790208000000L).put("odometerKm",3.218688).put("litres",2).put("cost",10).put("receipt","PRIVATE_FUEL_RECEIPT")))
        }
        SharedLibrary.syncNow();mirrorFixture()
        val item=remote("maintenance").single{it.getString("id")==entry};assertEquals("Phone service note",item.getString("notes"));assertFalse(item.has("receipt"));assertFalse(item.has("location"))
        assertFalse(remote("fuel").single{it.getString("id")==fuel}.has("cost"));assertEquals(before,metrics())
    }
    @Test fun espReceivesEditsAndUploadsSameRecord(){
        assertFalse(BuildConfig.PHONE_EDITION);connect();val before=metrics();SharedLibrary.syncNow()
        val item=SoftwareStore.records("maintenance").single{it.getString("id")==entry};assertEquals("PRIVATE_RECEIPT",item.getString("receipt"))
        SoftwareStore.update("maintenance",entry,item.put("notes","ESP service note"));SharedLibrary.syncNow();mirrorFixture()
        assertEquals("ESP service note",remote("maintenance").single{it.getString("id")==entry}.getString("notes"));assertEquals(before,metrics())
    }
    @Test fun phoneReceivesEditThenDeletes(){
        assertTrue(BuildConfig.PHONE_EDITION);connect();val before=metrics();SharedLibrary.syncNow()
        assertEquals("ESP service note",SoftwareStore.records("maintenance").single{it.getString("id")==entry}.getString("notes"))
        SoftwareStore.remove("maintenance",entry);SharedLibrary.syncNow();mirrorFixture()
        assertFalse(remote("maintenance").any{it.getString("id")==entry});assertEquals(before,metrics())
    }
    @Test fun espKeepsDeletionAndDoesNotResurrect(){
        assertFalse(BuildConfig.PHONE_EDITION);connect();val before=metrics();SharedLibrary.syncNow();mirrorFixture()
        assertFalse(SoftwareStore.records("maintenance").any{it.getString("id")==entry});assertFalse(remote("maintenance").any{it.getString("id")==entry});assertEquals(before,metrics())
    }
}
