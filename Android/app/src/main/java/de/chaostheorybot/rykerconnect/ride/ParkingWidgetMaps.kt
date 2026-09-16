package de.chaostheorybot.rykerconnect.ride

import android.app.job.*
import android.content.*
import android.graphics.*
import android.appwidget.AppWidgetManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParkingMapRequest(val spot:JSONObject,val width:Int,val height:Int,val accent:Int) {
    val key=ParkingMapIdentity.key(spot,width,height,accent)
}
object ParkingWidgetMaps {
    const val JOB=8202
    private fun folder(c:Context)=File(c.cacheDir,"parking-widgets").apply{mkdirs()}
    private fun file(c:Context,key:String,suffix:String)=File(folder(c),"$key.$suffix")
    fun load(c:Context,request:ParkingMapRequest):Bitmap? {
        val f=file(c,request.key,"png")
        return if(f.exists())BitmapFactory.decodeFile(f.path,BitmapFactory.Options().apply{inPreferredConfig=Bitmap.Config.RGB_565}) else null
    }
    fun failed(c:Context,r:ParkingMapRequest)=file(c,r.key,"failed").let{it.exists() && System.currentTimeMillis()-it.lastModified()<60_000}
    fun requests(c:Context):List<ParkingMapRequest>{
        val state=SoftwareStore.snapshot()
        if(!ParkingMapIdentity.displayable(state))return emptyList()
        val spot=state.getJSONObject("parking");val manager=AppWidgetManager.getInstance(c)
        val accent=WidgetAppearance.palette(c,state.optJSONObject("widgetOptions")?:JSONObject()).accent
        return manager.getAppWidgetIds(ComponentName(c,LastParkedWidget::class.java)).flatMap{ id ->
            ResponsiveWidgets.sizes(manager.getAppWidgetOptions(id)).filterNot{it.buttonOnly}.map{ParkingMapRequest(spot,it.mapWidth,it.mapHeight,accent)}
        }.distinctBy{it.key}.take(8)
    }
    @Synchronized fun ensure(c:Context){
        if(requests(c).none{!file(c,it.key,"png").exists() && !failed(c,it)})return
        val scheduler=c.getSystemService(JobScheduler::class.java)
        if(scheduler.getPendingJob(JOB)==null) {
            val scheduled=scheduler.schedule(JobInfo.Builder(JOB,ComponentName(c,ParkingMapJob::class.java)).setOverrideDeadline(0).build())
            if(scheduled!=JobScheduler.RESULT_SUCCESS)android.util.Log.w("RykerWidgetMap","Android deferred map rendering")
        }
    }
    suspend fun renderPending(c:Context){
        for(request in requests(c)){
            if(file(c,request.key,"png").exists() || failed(c,request))continue
            if(requests(c).none{it.key==request.key})continue
            try {
                val bitmap=withTimeout(25_000){snapshot(c,request)}
                try {
                    if(requests(c).any{it.key==request.key}){
                        val output=file(c,request.key,"png");val temp=file(c,request.key,"tmp")
                        temp.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                        java.nio.file.Files.move(temp.toPath(),output.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        file(c,request.key,"failed").delete()
                    }
                } finally{bitmap.recycle()}
            } catch(e:TimeoutCancellationException){
                file(c,request.key,"failed").writeText("Timed out")
            } catch(e:CancellationException){throw e}
            catch(e:Exception){
                file(c,request.key,"failed").writeText("Map unavailable")
                android.util.Log.w("RykerWidgetMap","Snapshot unavailable: "+e.javaClass.simpleName)
            }
            RykerWidgets.updateAll(c)
        }
        // Cache only a small bounded set; filenames are generated hashes in this private directory.
        folder(c).listFiles()?.filter{it.name.matches(Regex("[a-f0-9]{64}\\.(png|failed|tmp)"))}
            ?.sortedByDescending{it.lastModified()}?.drop(24)?.forEach{it.delete()}
    }
    private suspend fun snapshot(c:Context,r:ParkingMapRequest):Bitmap=withContext(Dispatchers.Main){
        MapLibre.getInstance(c)
        suspendCancellableCoroutine{continuation->
            val point=LatLng(r.spot.getDouble("lat"),r.spot.getDouble("lon"))
            val snapshotter=MapSnapshotter(c,MapSnapshotter.Options(r.width,r.height)
                .withStyleBuilder(Style.Builder().fromUri(RIDE_MAP_STYLE))
                .withPixelRatio(1f)
                .withCameraPosition(CameraPosition.Builder().target(point).zoom(if(r.width>=550)15.5 else 16.0).build()))
            continuation.invokeOnCancellation{android.os.Handler(android.os.Looper.getMainLooper()).post{snapshotter.cancel()}}
            snapshotter.start({snapshot->
                if(continuation.isActive){
                    val bitmap=snapshot.bitmap.copy(Bitmap.Config.ARGB_8888,true)
                    val position=snapshot.pixelForLatLng(point)
                    val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                    val accent=r.accent
                    paint.color=Color.WHITE;canvas.drawCircle(position.x,position.y-19,20f,paint)
                    paint.color=accent;canvas.drawCircle(position.x,position.y-19,16f,paint)
                    val tip=Path().apply{moveTo(position.x-10,position.y-8);lineTo(position.x,position.y+6);lineTo(position.x+10,position.y-8);close()}
                    canvas.drawPath(tip,paint)
                    paint.color=Color.BLACK;paint.textAlign=Paint.Align.CENTER;paint.textSize=21f;paint.typeface=Typeface.DEFAULT_BOLD
                    canvas.drawText("P",position.x,position.y-12,paint)
                    continuation.resume(bitmap)
                }
            },{error->if(continuation.isActive)continuation.resumeWithException(IllegalStateException(error))})
        }
    }
}
class ParkingMapJob:JobService(){
    private var job:Job?=null
    override fun onStartJob(params:JobParameters):Boolean{
        job=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            try{
                ParkingWidgetMaps.renderPending(this@ParkingMapJob);jobFinished(params,false)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ParkingWidgetMaps.ensure(applicationContext)},300)
            }
            catch(e:CancellationException){throw e}
            catch(e:Exception){android.util.Log.e("RykerWidgetMap","Map job failed",e);jobFinished(params,true)}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean{job?.cancel();return true}
}
