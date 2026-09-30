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
import java.util.UUID

/** Named phases run in two installed editions using fresh, synthetic IDs only. */
class TripDeletionIntegrationTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val id get()=args.getString("ride")!!
    private val keep get()=args.getString("keep")!!
    private val photo get()=args.getString("photo")!!
    private fun metrics()=OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:8890/__test/metrics").build()).execute().use{it.body.string()}
    private fun seedTrip(trip:String,title:String) {
        val file=File(c.noBackupFilesDir,"rides/$trip.jsonl");check(!file.exists())
        file.writeText("{\"start\":1000,\"version\":2,\"automatic\":false}\n{\"end\":61000}\n")
        TripStore.saveMetadata(trip,title,"Synthetic deletion test")
    }
    private fun queueFixture(trip:String):String {
        val job=UUID.randomUUID().toString()
        val dir=File(c.noBackupFilesDir,"dadrides/$job").apply{mkdirs()}
        File(dir,"state.json").writeText(JSONObject().put("id",job).put("ride",trip).put("state","Queued").toString())
        return job
    }
    @Test fun phoneSeedsFalseStartAndPublishedCopy() {
        assertTrue(BuildConfig.PHONE_EDITION);SharedLibrary.enable(false);DadRides.enable(false)
        seedTrip(id,"False start to delete");seedTrip(keep,"Keep this ride")
        // Publish a route-only synthetic copy, then disconnect networking for local deletion.
        DadRides.configure("http://127.0.0.1:8890",File(c.cacheDir,"shared-test-key").readText().trim());DadRides.enable(true)
        val m=PublicRide.manifest(TripStore.detail(id),RidePhotos.journal(id),0.0,true,false)
        val bytes=m.toString().toByteArray();val rev=PublicRide.hash(bytes)
        DadRides.request("rides/$id/revisions/$rev","PUT",bytes)
        DadRides.request("rides/$id/finish/$rev","POST")
        DadRides.request("rides/$id/publish","POST",JSONObject().put("base",JSONObject.NULL).put("revision",rev).toString().toByteArray())
        DadRides.enable(false)
        SoftwareStore.favorite(id,true)
        // A local photo belonging solely to the deleted trip.
        for(ext in listOf("original","jpg","thumb.jpg"))RidePhotos.file("$photo.$ext").writeBytes(byteArrayOf(1,2,3))
        RidePhotos.save(JSONObject().put("id",id).put("tags","test").put("cover",photo).put("photos",JSONArray().put(JSONObject().put("id",photo))))
        queueFixture(id);queueFixture(keep)
        SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertEquals(0,TripStore.detail(id).track.size)
    }
    @Test fun espReceivesAndQueuesItsOwnUpload() {
        assertFalse(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertEquals("False start to delete",TripStore.detail(id).summary.title)
        assertTrue(TripStore.contains(keep));queueFixture(id);queueFixture(keep)
        SharedLibrary.syncNow()
    }
    @Test fun phoneDeletesAndProtectsRecording() {
        assertTrue(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.syncNow()
        val backup=TripStore.backupFiles();File(c.cacheDir,"deletion-backup.json").writeText(JSONObject(backup).toString())
        assertTrue(RecordingCoordinator.acquire(c))
        try { assertThrows(IllegalStateException::class.java){SharedLibrary.deleteTrip(id)};assertTrue(TripStore.contains(id)) }
        finally { RecordingCoordinator.release(c) }
        val before=metrics()
        SharedLibrary.deleteTrip(id);SharedLibrary.syncNow()
        assertDeleted()
        assertEquals(before,metrics())
        val public=OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:8890/api/rides/$id").build()).execute().use{it.code}
        assertEquals(200,public)
    }
    private fun assertDeleted() {
        assertFalse(TripStore.contains(id));assertTrue(TripStore.contains(keep))
        assertTrue(SharedLibrary.isTripDeleted(id))
        assertFalse(SoftwareStore.favorite(id))
        assertFalse(SoftwareStore.records("journals").any{it.optString("id")==id})
        for(ext in listOf("original","jpg","thumb.jpg"))assertFalse(RidePhotos.file("$photo.$ext").exists())
        assertTrue(DadRides.items().filter{it.optString("ride")==id}.all{it.optString("state")=="Canceled"})
        assertTrue(DadRides.items().filter{it.optString("ride")==keep}.all{it.optString("state")=="Queued"})
        assertTrue(TripStore.history.value.none{it.id==id})
    }
    @Test fun espReceivesDeletionAfterRestart() {
        assertFalse(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.enable(true)
        SharedLibrary.syncNow();assertDeleted();SharedLibrary.syncNow();assertDeleted()
    }
    @Test fun phoneRestoresExportedTrip() {
        assertTrue(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.syncNow();assertDeleted()
        val data=JSONObject(File(c.cacheDir,"deletion-backup.json").readText())
        val files=data.keys().asSequence().filter{it.startsWith("$id.")}.associateWith{data.getString(it)}
        assertEquals(1,TripStore.restoreFiles(files))
        SharedLibrary.syncNow()
        assertEquals("False start to delete",TripStore.originalDetail(id).summary.title)
        assertFalse(SharedLibrary.isTripDeleted(id))
    }
    @Test fun espReceivesRestoredTripAndDeletesItBack() {
        assertFalse(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.syncNow()
        assertTrue(TripStore.contains(id));assertFalse(SharedLibrary.isTripDeleted(id))
        SharedLibrary.deleteTrip(id);SharedLibrary.syncNow();assertDeleted()
    }
    @Test fun phoneReceivesEspDeletionAfterRestart() {
        assertTrue(BuildConfig.PHONE_EDITION);DadRides.enable(false);SharedLibrary.syncNow();assertDeleted()
    }
}
