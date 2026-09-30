package de.chaostheorybot.rykerconnect.ride

import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Cross-edition phases run against the isolated Worker, never production. */
class RideRecoveryIntegrationTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    private val id get()=InstrumentationRegistry.getArguments().getString("ride")!!
    private val photo get()=InstrumentationRegistry.getArguments().getString("photo")!!
    private fun metrics()=OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:8890/__test/metrics").build()).execute().use{it.body.string()}
    private fun connect(){DadRides.configure("http://127.0.0.1:8890",File(c.cacheDir,"shared-test-key").readText().trim());DadRides.enable(true)}
    private fun edit(title:String):JSONObject {
        val head=DadRides.request("rides/$id/edit");val m=head.getJSONObject("manifest").put("title",title)
        m.getJSONObject("stats").put("meters",3218.688)
        return DadRides.request("rides/$id/edit","POST",JSONObject().put("base",head.getString("revision")).put("manifest",m).toString().toByteArray())
    }
    @Test fun phoneSeedsOriginalAndWebsite() {
        assertTrue(BuildConfig.PHONE_EDITION);SharedLibrary.enable(false);connect()
        val file=File(c.noBackupFilesDir,"rides/$id.jsonl");check(!file.exists())
        file.writeText("{\"start\":1790182800000,\"version\":2}\n{\"lat\":61.2,\"lon\":-149.9,\"time\":1790182800000,\"accuracy\":5,\"speed\":8}\n{\"lat\":61.201,\"lon\":-149.901,\"time\":1790182820000,\"accuracy\":5,\"speed\":8}\n{\"end\":1790183400000}\n")
        TripStore.saveMetadata(id,"Recovery original","Synthetic ride")
        File(c.cacheDir,"recovery-original.jsonl").writeBytes(file.readBytes())
        val bytes=byteArrayOf(-1,-40,-1,-38,0,2,0,-1,-39);val sha=PublicRide.hash(bytes)
        val m=JSONObject("""{"version":1,"id":"$id","title":"Recovery original","story":"Synthetic website story","date":"2026-09-23","tags":["test"],"cover":"$photo","photos":[{"id":"$photo","caption":"Photo kept on website","sha":"$sha","size":9,"thumbSha":"$sha","thumbSize":9}],"route":[[[-149.8,61.2],[-149.7,61.3]]],"stats":{"meters":1609.344,"elapsedMs":600000,"movingMs":500000,"stoppedMs":100000,"unknownMs":0,"averageMps":3.218688},"privacy":{"trimMeters":500,"statsIncluded":true}}""")
        val body=m.toString().toByteArray();val revision=PublicRide.hash(body)
        DadRides.request("rides/$id/revisions/$revision","PUT",body)
        for(ext in listOf("jpg","thumb.jpg"))DadRides.request("assets/$revision/$photo.$ext","PUT",bytes,"image/jpeg")
        DadRides.request("rides/$id/finish/$revision","POST")
        DadRides.request("rides/$id/publish","POST",JSONObject().put("base",JSONObject.NULL).put("revision",revision).toString().toByteArray())
        RideEdits.uploadFinished(id,revision,null);SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertFalse(TripStore.detail(id).summary.websiteCopy)
    }
    @Test fun espReceivesOriginal() {
        assertFalse(BuildConfig.PHONE_EDITION);connect();SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertTrue(TripStore.originalDetail(id).track.isNotEmpty());assertEquals("Recovery original",TripStore.detail(id).summary.title)
    }
    @Test fun phoneEditsThenDeletes() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();val before=metrics()
        edit("Recovery first edit");val current=edit("Recovery latest edit")
        DadRides.request("rides/$id/publish","POST",JSONObject().put("base",current.get("published")).put("revision",current.getString("revision")).toString().toByteArray())
        SharedLibrary.syncNow()
        assertEquals("Recovery latest edit",TripStore.detail(id).summary.title)
        assertArrayEquals(File(c.cacheDir,"recovery-original.jsonl").readBytes(),File(c.noBackupFilesDir,"rides/$id.jsonl").readBytes())
        assertEquals(1,TripStore.history.value.count{it.id==id})
        SharedLibrary.deleteTrip(id);SharedLibrary.syncNow();assertFalse(TripStore.contains(id));assertEquals(before,metrics())
        assertTrue(RideRecovery.catalog().any{it.id==id})
    }
    @Test fun espReceivesDeletionWithoutAutomaticResurrection() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();SharedLibrary.syncNow()
        assertFalse(TripStore.contains(id));assertTrue(SharedLibrary.isTripDeleted(id));assertTrue(RideRecovery.catalog().any{it.id==id})
    }
    @Test fun phoneRestoresAndRejectsStaleReview() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();val before=metrics()
        val stale=RideRecovery.preview(id);edit("Recovery reviewed copy")
        assertThrows(IllegalStateException::class.java){RideRecovery.restore(stale)};assertFalse(TripStore.contains(id))
        val current=RideRecovery.preview(id)
        assertTrue(RecordingCoordinator.acquire(c))
        try{assertThrows(IllegalStateException::class.java){RideRecovery.restore(current)};assertFalse(TripStore.contains(id))}finally{RecordingCoordinator.release(c)}
        RideRecovery.restore(current);SharedLibrary.syncNow();assertRestored("Recovery reviewed copy")
        assertThrows(IllegalStateException::class.java){RideRecovery.restore(current)}
        assertEquals(before,metrics());assertFalse(RideRecovery.catalog().any{it.id==id})
    }
    private fun assertRestored(title:String) {
        assertTrue(TripStore.contains(id));assertFalse(SharedLibrary.isTripDeleted(id))
        val d=TripStore.detail(id);assertEquals(title,d.summary.title);assertTrue(d.summary.websiteCopy)
        assertTrue(d.track.isEmpty());assertEquals(2,d.editedRoute!!.size);assertEquals(3218.688,d.stats.meters,0.001)
        assertEquals(1,TripStore.history.value.count{it.id==id});assertFalse(SharedLibrary.hasConflict("trip/$id.jsonl"))
        assertFalse(RidePhotos.file("$photo.jpg").exists());assertThrows(IllegalStateException::class.java){TripStore.gpx(id)}
    }
    @Test fun espReceivesRestorationAndBackupPreservesIt() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();assertRestored("Recovery reviewed copy")
        val before=metrics();val output=ByteArrayOutputStream()
        val files=TripStore.backupFiles().filterKeys{it.startsWith("$id.")}
        val software=JSONObject().put("siteEdits",JSONArray().put(RideEdits.record(id)))
        RideBackup.write(output,files,SoftwareStore.preferences(),software)
        val restored=RideBackup.read(output.toByteArray().inputStream())
        assertEquals(id,JSONObject(restored.trips.getValue("$id.jsonl").lineSequence().first()).getJSONObject("websiteCopy").getString("id"))
        assertEquals(before,metrics())
    }
    @Test fun phoneReceivesLaterEditAutomatically() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();val before=metrics()
        edit("Recovery later edit");SharedLibrary.changed()
        val until=System.currentTimeMillis()+30000
        while(TripStore.detail(id).summary.title!="Recovery later edit" && System.currentTimeMillis()<until)Thread.sleep(100)
        assertRestored("Recovery later edit");assertEquals(before,metrics())
        TripStore.saveMetadata(id,"Edited back on phone","Phone story");SharedLibrary.syncNow()
        assertEquals("Edited back on phone",DadRides.request("rides/$id/edit").getJSONObject("manifest").getString("title"))
        assertEquals(before,metrics())
    }
    @Test fun espReceivesLaterEditsAfterRestart() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();assertRestored("Edited back on phone")
        assertEquals("Phone story",TripStore.detail(id).summary.notes)
    }
    @Test fun restoredRouteCannotApproveItsOwnPrivacy() {
        connect();SharedLibrary.enable(true);SharedLibrary.syncNow();val before=metrics()
        val head=DadRides.request("rides/$id/edit");val m=head.getJSONObject("manifest")
        m.getJSONArray("route").getJSONArray(0).getJSONArray(0).put(0,-149.6)
        val changed=DadRides.request("rides/$id/edit","POST",JSONObject().put("base",head.getString("revision")).put("manifest",m).toString().toByteArray())
        assertTrue(changed.getBoolean("needsReview"));SharedLibrary.syncNow()
        assertTrue(RideEdits.record(id)!!.getBoolean("needsReview"))
        assertTrue(DadRides.request("rides/$id/edit").getBoolean("needsReview"))
        val rejected=assertThrows(DadRidesHttpException::class.java){DadRides.request("rides/$id/publish","POST",JSONObject().put("base",changed.get("published")).put("revision",changed.getString("revision")).toString().toByteArray())}
        assertEquals(400,rejected.status);assertEquals(before,metrics())
    }
}
