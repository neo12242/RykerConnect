package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.BuildConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Run the named phases in order in both installed APKs. All IDs and server data are synthetic. */
class SharedLibraryIntegrationTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val ride get()=args.getString("ride")!!
    private val photo get()=args.getString("photo")!!
    private val fuel get()=args.getString("fuel")!!
    private fun metrics():String=OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:8890/__test/metrics").build()).execute().use{it.body.string()}
    private fun fixturePhoto(seed:Long=42):ByteArray {
        val b=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
        val random=java.util.Random(seed);val pixels=IntArray(320*240){0xff000000.toInt() or random.nextInt(0xffffff)};b.setPixels(pixels,0,320,0,0,320,240)
        val out=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);b.recycle();return UploadPhoto.prepare(out.toByteArray(),false)
    }
    @Test fun phoneSeedExistingLibrary() {
        assertTrue(BuildConfig.PHONE_EDITION);SharedLibrary.enable(false)
        val file=File(c.noBackupFilesDir,"rides/$ride.jsonl");check(!file.exists()){ "Use fresh synthetic IDs" };file.parentFile!!.mkdirs()
        val started=1_790_208_000_000L
        val rows=mutableListOf(JSONObject().put("start",started).put("version",2).put("automatic",false))
        for(i in 0..12)rows.add(JSONObject().put("lat",1.0+i*0.01).put("lon",1.0+i*0.01).put("time",started+i*10_000).put("accuracy",5).put("speed",10))
        rows.add(JSONObject().put("end",started+120_000).put("reason","Synthetic test"));file.writeText(rows.joinToString("\n")+"\n")
        TripStore.saveMetadata(ride,"Existing phone ride","Original story")
        val bytes=fixturePhoto();for(ext in listOf("original","jpg","thumb.jpg"))RidePhotos.file("$photo.$ext").writeBytes(bytes)
        RidePhotos.save(JSONObject().put("id",ride).put("tags","test").put("cover",photo).put("photos",JSONArray().put(JSONObject().put("id",photo).put("caption","Phone caption").put("publish",true))))
        SoftwareStore.mutate{next->val list=SoftwareStore.records("fuel");next.put("fuel",JSONArray(list+JSONObject().put("id",fuel).put("odometerKm",100).put("litres",10).put("cost",25).put("full",true).put("notes","Phone fuel")
            .put("receipt",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))))}
        SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertTrue(File(c.noBackupFilesDir,"shared-library/before-first-sync.json").exists())
        assertArrayEquals(bytes,RidePhotos.file("$photo.original").readBytes())
    }
    @Test fun fullReceivesAndEditsPhoneData() {
        assertFalse(BuildConfig.PHONE_EDITION);SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertEquals("Existing phone ride",TripStore.originalDetail(ride).summary.title)
        for(ext in listOf("original","jpg","thumb.jpg"))assertArrayEquals(fixturePhoto(),RidePhotos.file("$photo.$ext").readBytes())
        val receipt=SoftwareStore.records("fuel").first{it.getString("id")==fuel};assertEquals("Phone fuel",receipt.getString("notes"));assertTrue(receipt.getString("receipt").length>8192)
        TripStore.saveMetadata(ride,"Edited in ESP","Story edited in ESP")
        SoftwareStore.update("fuel",fuel,receipt.put("notes","ESP fuel edit"))
        val j=RidePhotos.journal(ride);j.getJSONArray("photos").getJSONObject(0).put("caption","ESP caption");RidePhotos.save(j)
        SharedLibrary.syncNow()
    }
    @Test fun phoneGetsEditsAndWebsiteRoundTripUsesNoR2():Unit=runBlocking {
        assertTrue(BuildConfig.PHONE_EDITION);SharedLibrary.syncNow()
        assertEquals("Edited in ESP",TripStore.detail(ride).summary.title)
        assertEquals("ESP fuel edit",SoftwareStore.records("fuel").first{it.getString("id")==fuel}.getString("notes"))
        assertEquals("ESP caption",RidePhotos.photos(RidePhotos.journal(ride)).first().getString("caption"))
        val originalFile=File(c.noBackupFilesDir,"rides/$ride.jsonl");val originalBytes=originalFile.readBytes();val original=TripStore.originalDetail(ride)
        DadRides.configure("http://127.0.0.1:8890",File(c.cacheDir,"shared-test-key").readText().trim());DadRides.enable(true)
        val m=PublicRide.manifest(TripStore.detail(ride),RidePhotos.journal(ride),500.0,true,true)
        DadRides.queue(m,true);DadRides.upload();assertEquals("Uploaded draft",DadRides.items().first{it.getString("ride")==ride}.getString("state"))
        val head=DadRides.request("rides/$ride/edit");val edited=JSONObject(head.getJSONObject("manifest").toString())
        edited.put("title","Website edited title").put("story","Website story").put("date","2026-09-23")
        edited.getJSONObject("stats").put("meters",1600)
        edited.getJSONArray("photos").getJSONObject(0).put("caption","Website caption")
        edited.getJSONArray("route").getJSONArray(0).put(0,JSONArray().put(1.00001).put(1.00001))
        val before=metrics()
        val saved=DadRides.request("rides/$ride/edit","POST",JSONObject().put("base",head.getString("revision")).put("manifest",edited).toString().toByteArray())
        assertTrue(saved.getBoolean("needsReview"));RideEdits.sync();SharedLibrary.syncNow()
        assertEquals("Website edited title",TripStore.detail(ride).summary.title)
        assertEquals(1600.0,TripStore.detail(ride).stats.meters,0.0001)
        assertEquals("Website caption",RidePhotos.photos(RidePhotos.journal(ride)).first().getString("caption"))
        assertFalse(RideEdits.record(ride)!!.getBoolean("needsReview"))
        assertTrue(TripStore.detail(ride).editedRoute!!.none{TrackMath.distance(original.track.first(),it)<=500})
        assertArrayEquals(originalBytes,originalFile.readBytes());assertEquals(original.stats,TripStore.originalDetail(ride).stats)
        assertEquals(before,metrics())
    }
    @Test fun fullReceivesWebsiteOverlayAndKeepsOriginal() {
        assertFalse(BuildConfig.PHONE_EDITION);SharedLibrary.syncNow()
        assertEquals("Website edited title",TripStore.detail(ride).summary.title)
        assertEquals("Edited in ESP",TripStore.originalDetail(ride).summary.title)
        assertEquals("2026-09-23",TripStore.detail(ride).editedDate)
        assertEquals(13,TripStore.originalDetail(ride).track.size)
        assertEquals("Website caption",RidePhotos.photos(RidePhotos.journal(ride)).first().getString("caption"))
    }
    @Test fun recordingLeaseAndCompareAndSwapProtectConcurrentWork() {
        assertTrue(BuildConfig.PHONE_EDITION)
        val nonce=UUID.randomUUID().toString();val extras=Bundle().apply{putString("session",nonce)}
        try {
            assertTrue(c.contentResolver.call(SharedLibrary.uri(SharedLibrary.FULL),"claim",null,extras)!!.getBoolean("ok"))
            assertFalse(RecordingCoordinator.acquire(c))
        }finally{c.contentResolver.call(SharedLibrary.uri(SharedLibrary.FULL),"release",null,extras)}
        assertTrue(RecordingCoordinator.acquire(c));RecordingCoordinator.release(c)
        val old=SoftwareStore.records("fuel").first{it.getString("id")==fuel}
        assertFalse(SoftwareStore.syncSet(listOf("fuel",fuel),"outdated",JSONObject(old.toString()).put("notes","Must not overwrite")))
        assertEquals(old.getString("notes"),SoftwareStore.records("fuel").first{it.getString("id")==fuel}.getString("notes"))
        assertThrows(IllegalArgumentException::class.java){SharedLibrary.checkCaller(c,2000)}
    }
    @Test fun recordingCancellationReleasesLease():Unit=runBlocking {
        val entered=CompletableDeferred<Unit>()
        val job=launch(Dispatchers.IO){RecordingCoordinator.withSession(c){entered.complete(Unit);awaitCancellation()}}
        withTimeout(30000){entered.await()};job.cancelAndJoin()
        assertTrue(RecordingCoordinator.acquire(c));RecordingCoordinator.release(c)
    }
    @Test fun fullCopiesWebsiteConnectionAndConverges() {
        assertFalse(BuildConfig.PHONE_EDITION)
        if(DadRides.origin().isBlank())SharedLibrary.copyWebsiteConnection()
        val before=metrics();SharedLibrary.syncNow();RideEdits.sync();SharedLibrary.syncNow()
        assertFalse(SharedLibrary.hasConflict("software/siteEdits/$ride"))
        assertFalse(RideEdits.record(ride)!!.has("conflict"));assertEquals(before,metrics())
    }
    @Test fun phoneConfirmsBothWebsiteConnectionsConverge() {
        assertTrue(BuildConfig.PHONE_EDITION);val before=metrics();SharedLibrary.syncNow();RideEdits.sync();SharedLibrary.syncNow()
        assertFalse(SharedLibrary.hasConflict("software/siteEdits/$ride"))
        assertFalse(RideEdits.record(ride)!!.has("conflict"));assertEquals(before,metrics())
    }
    @Test fun fullPauseForInterruptedTransfer(){assertFalse(BuildConfig.PHONE_EDITION);SharedLibrary.enable(false)}
    @Test fun phonePrepareInterruptedTransfer(){
        assertTrue(BuildConfig.PHONE_EDITION);SharedLibrary.enable(false)
        val id=args.getString("retryPhoto")!!;val bytes=fixturePhoto(83)
        for(ext in listOf("original","jpg","thumb.jpg"))RidePhotos.file("$id.$ext").writeBytes(bytes)
        SharedLibrary.enable(true);SharedLibrary.exportSnapshot().delete()
        val hash=SharedLibrary.blob(RidePhotos.file("$id.original")).getString("blob")
        // Simulate a damaged/incomplete source transfer without touching its retained original file.
        val broken=bytes.clone();broken[0]=(broken[0].toInt() xor 1).toByte();SharedLibrary.blobFile(hash).writeBytes(broken)
        assertArrayEquals(bytes,RidePhotos.file("$id.original").readBytes())
    }
    @Test fun fullRejectsDamagedTransfer(){
        assertFalse(BuildConfig.PHONE_EDITION);SharedLibrary.enable(true);SharedLibrary.syncNow()
        assertFalse(RidePhotos.file(args.getString("retryPhoto")!!+".original").exists())
        assertTrue(SharedLibrary.status.value,SharedLibrary.status.value.contains("checksum"))
        assertEquals(13,TripStore.originalDetail(ride).track.size)
    }
    @Test fun phoneRepairsInterruptedTransfer(){
        assertTrue(BuildConfig.PHONE_EDITION);val source=RidePhotos.file(args.getString("retryPhoto")!!+".original")
        val bytes=source.readBytes();SharedLibrary.blobFile(SyncJson.hash(bytes)).writeBytes(bytes);SharedLibrary.syncNow()
    }
    @Test fun fullResumesVerifiedTransfer(){
        assertFalse(BuildConfig.PHONE_EDITION);SharedLibrary.syncNow();val id=args.getString("retryPhoto")!!
        for(ext in listOf("original","jpg","thumb.jpg"))assertArrayEquals(fixturePhoto(83),RidePhotos.file("$id.$ext").readBytes())
        assertEquals(13,TripStore.originalDetail(ride).track.size)
    }
    @Test fun phoneSynchronizesBrowserEditsWithoutPhotoCalls(){
        assertTrue(BuildConfig.PHONE_EDITION);val before=metrics();SharedLibrary.syncNow()
        assertEquals("Browser editor verified",TripStore.detail(ride).summary.title)
        assertEquals(4023.36,TripStore.detail(ride).stats.meters,0.01)
        assertFalse(RideEdits.record(ride)!!.getBoolean("needsReview"));assertEquals(before,metrics())
    }
}
