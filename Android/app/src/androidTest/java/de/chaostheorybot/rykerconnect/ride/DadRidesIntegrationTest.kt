package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class DadRidesIntegrationTest {
    @Test fun photosBackupAndPrivateUploadUseIsolatedData():Unit=runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=java.util.UUID.randomUUID().toString()
        fun isolated(label:String)=object:ContextWrapper(base){
            private val root=File(base.cacheDir,"dadrides-test-$suffix-$label").apply{mkdirs()}
            override fun getNoBackupFilesDir()=root
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("test-$suffix-$label-$name",mode)
        }
        val c=isolated("original")
        try{
            TripStore.init(c);SoftwareStore.init(c);RidePhotos.init(c);DadRides.init(c)
            TripStore.start(false);val id=TripStore.summary.value.id;val start=TripStore.summary.value.started
            for(i in 0..6)TripStore.point(TrackPoint(61.2+i*.002,-149.9,start+i*5000,5f,12.0))
            TripStore.stop("Saved",start+30000);TripStore.saveMetadata(id,"TEST isolated photo ride","Synthetic integration fixture; never published.")
            val image=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);image.eraseColor(Color.rgb(48,90,80));val source=File(c.noBackupFilesDir,"fixture.jpg");source.outputStream().use{image.compress(Bitmap.CompressFormat.JPEG,90,it)};image.recycle()
            RidePhotos.add(c,id,Uri.fromFile(source));val journal=RidePhotos.journal(id);val photo=RidePhotos.photos(journal).single();photo.put("caption","Private upload test");RidePhotos.save(journal)
            val assets=RidePhotos.backupAssets();assertEquals(3,assets.size)
            val bytes=ByteArrayOutputStream();RideBackup.write(bytes,TripStore.backupFiles(),SoftwareStore.preferences(),SoftwareStore.snapshot(),assets)
            val archive=RideBackup.read(ByteArrayInputStream(bytes.toByteArray()));assertEquals(3,archive.assets.size)
            val other=isolated("restore");TripStore.init(other);SoftwareStore.init(other);RidePhotos.init(other);RidePhotos.restoreAssets(archive.assets);TripStore.restoreFiles(archive.trips);SoftwareStore.restore(archive.software)
            assertEquals("Private upload test",RidePhotos.photos(RidePhotos.journal(id)).single().getString("caption"))
            assertArrayEquals(assets.values.first(),RidePhotos.backupAssets().values.first())
            assertEquals(0,TripStore.restoreFiles(archive.trips));SoftwareStore.restore(archive.software);assertEquals(1,SoftwareStore.records("journals").size)
            DadRides.init(other);DadRides.configure("http://127.0.0.1:8890",File(base.cacheDir,"dadrides-test-key").readText().trim());DadRides.enable(true)
            val manifest=PublicRide.manifest(TripStore.detail(id),RidePhotos.journal(id),500.0,true,true)
            DadRides.queue(manifest,true);DadRides.queue(manifest,true);assertEquals(1,DadRides.items().size)
            DadRides.upload();val queued=DadRides.items().single();assertEquals(queued.optString("message"),"Uploaded draft",queued.getString("state"))
            val rev=queued.getString("revision");val remote=DadRides.request("rides/$id/revisions/$rev");assertTrue(remote.isNull("published"));assertEquals(1,remote.getInt("ready"))
            DadRides.request("rides/$id/revisions/$rev","DELETE")
            val pid=MaintenancePlans.create(ServiceCatalog.items(SoftwareStore.snapshot()).first{it.optString("id")!=ServiceCatalog.MILEAGE}.getString("id"))
            val plan=MaintenancePlans.find(pid)!!;plan.put("notes","DEMO — isolated test plan");plan.getJSONArray("parts").put(JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("name","Demo filter").put("quantity",1).put("cost",15).put("done",false));MaintenancePlans.save(plan)
            fun screenshot(page:String,name:String){
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                val activity=instrumentation.startActivitySync(android.content.Intent(base,RideActionActivity::class.java).putExtra("page",page).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                instrumentation.waitForIdleSync();Thread.sleep(1500)
                val image=instrumentation.uiAutomation.takeScreenshot()
                File(base.cacheDir,"dadrides-$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
                instrumentation.runOnMainSync{activity.finish()};instrumentation.waitForIdleSync()
            }
            screenshot("Summary/$id","summary");screenshot("Journal/$id","photos");screenshot("Maintenance planner","planner")
            val backup=ByteArrayOutputStream();RideBackup.write(backup,TripStore.backupFiles(),SoftwareStore.preferences(),SoftwareStore.snapshot(),RidePhotos.backupAssets());assertEquals(1,RideBackup.read(ByteArrayInputStream(backup.toByteArray())).software.getJSONArray("plans").length())
            DadRides.enable(false);assertThrows(IllegalStateException::class.java){DadRides.request("status")}
        }finally{
            DadRides.disconnect();TripStore.init(base);SoftwareStore.init(base);RidePhotos.init(base);DadRides.init(base)
        }
    }
}
