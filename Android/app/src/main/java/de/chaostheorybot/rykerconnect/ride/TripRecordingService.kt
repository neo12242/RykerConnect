package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import kotlinx.coroutines.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.MainActivity
import de.chaostheorybot.rykerconnect.R
import org.json.JSONObject

class TripRecordingService : Service(), LocationListener {
    companion object { @Volatile var running=false; private set }
    private lateinit var manager: LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var lastFix = 0L
    override fun onCreate() { super.onCreate(); manager = getSystemService(LocationManager::class.java);running=true }
    override fun onBind(intent: Intent?) = null
    private fun respond(intent:Intent?,ok:Boolean,message:String) {
        val receiver=intent?.let{androidx.core.content.IntentCompat.getParcelableExtra(it,"receiver",ResultReceiver::class.java)}?:return
        receiver.send(if(ok)0 else 1,Bundle().apply{putString("json",(if(ok)RideControl.success(this@TripRecordingService,message)else RideControl.failure(this@TripRecordingService,message)).toString())})
    }
    private fun sample() {
        check(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){"Allow precise location on the phone"}
        lastFix=SystemClock.elapsedRealtime()
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5_000,0f,this,Looper.getMainLooper())
    }
    private fun notification():Notification {
        val s=TripStore.summary.value
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String,code:Int)=PendingIntent.getService(this,code,Intent(this,TripRecordingService::class.java).setAction(name).putExtra("rideId",s.id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"ride_recording").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if(s.paused)"Ride paused" else "Recording your ride")
            .setContentText(if(s.paused)"GPS recording off · Resume the same ride" else "GPS route is saved on this phone")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0,if(s.paused)"Resume" else "Pause",action(if(s.paused)"RESUME" else "PAUSE",2))
            .addAction(0,"End Ride",action("END",1)).build()
    }
    private fun updateNotification(){getSystemService(NotificationManager::class.java).notify(42,notification())}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val action=intent?.action?:"START"
        try {
            intent?.getStringExtra("command")?.let{RideControl.validate(this,JSONObject(it))}
            intent?.getStringExtra("rideId")?.let{check(it==TripStore.summary.value.id && TripStore.summary.value.recording){"This notification belongs to an ended ride"}}
            when(action) {
                "END","STOP","AUTO_STOP" -> {
                    val s=TripStore.summary.value
                    if(action=="AUTO_STOP" && (s.paused||s.needsRecovery))return START_NOT_STICKY
                    val saved=if(action=="AUTO_STOP")TripStore.stop("Saved after disconnect",intent!!.getLongExtra("endAt",System.currentTimeMillis()))else TripStore.stop()
                    check(saved){TripStore.storageError.value}
                    if(action!="AUTO_STOP")AutoRide.manualStop()
                    manager.removeUpdates(this);respond(intent,true,"Ride saved in My Trips");stopSelf();return START_NOT_STICKY
                }
                "PAUSE" -> {
                    manager.removeUpdates(this)
                    try{TripStore.pause()}catch(e:Exception){if(!TripStore.summary.value.needsRecovery)sample();throw e}
                    updateNotification();respond(intent,true,"Ride paused · GPS recording off");return START_NOT_STICKY
                }
                "RESUME" -> if(recordingJob?.isActive==true) {
                    sample()
                    try{TripStore.resumeRecording()}catch(e:Exception){manager.removeUpdates(this);throw e}
                    updateNotification();respond(intent,true,"Ride resumed");return START_NOT_STICKY
                }
                "AUTO","START" -> if(recordingJob?.isActive==true || TripStore.summary.value.recording) {
                    respond(intent,false,"A ride is already active. Resume or End that ride.");return START_NOT_STICKY
                }
                else -> error("Unknown ride action")
            }
            check(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){"Allow precise location on the phone"}
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("ride_recording","Ride recording",NotificationManager.IMPORTANCE_LOW))
            if(Build.VERSION.SDK_INT>=34)startForeground(42,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)else startForeground(42,notification())
            recordingJob=scope.launch {
                var accepted=false
                try {
                    RecordingCoordinator.withSession(applicationContext) {
                        withContext(Dispatchers.Main) {
                            intent?.getStringExtra("command")?.let{RideControl.validate(this@TripRecordingService,JSONObject(it))}
                            sample()
                            if(action=="RESUME")TripStore.resumeRecording() else {
                                val recovered=if(action=="AUTO")TripStore.recoverable(System.currentTimeMillis())else null
                                if(recovered!=null)TripStore.resume(recovered)else TripStore.start(automatic=action=="AUTO")
                            }
                            updateNotification();accepted=true;respond(intent,true,if(action=="RESUME")"Ride resumed" else "Ride started")
                        }
                        while(isActive) {
                            delay(10_000)
                            if(!RecordingCoordinator.renew(applicationContext)) {
                                withContext(Dispatchers.Main){manager.removeUpdates(this@TripRecordingService);TripStore.interrupted()};break
                            }
                            withContext(Dispatchers.Main){if(SystemClock.elapsedRealtime()-lastFix>30_000)TripStore.gps("GPS unavailable or stale")}
                        }
                    }
                    if(!accepted)respond(intent,false,RecordingCoordinator.status.value.ifBlank{"Another edition owns this ride"})
                }catch(e:CancellationException){throw e}
                catch(e:Exception){withContext(Dispatchers.Main){manager.removeUpdates(this@TripRecordingService);TripStore.interrupted();AutoRide.failed("Recording blocked: "+e.javaClass.simpleName);if(!accepted)respond(intent,false,e.message?:"Open phone and check location permissions")}}
                finally{withContext(NonCancellable+Dispatchers.Main){stopSelf(startId)}}
            }
        }catch(e:Exception){respond(intent,false,e.message?:"Action failed");AutoRide.failed(e.message?:"Recording blocked");if(recordingJob?.isActive!=true)stopSelf()}
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location:Location) {
        val s=TripStore.summary.value
        if(!s.recording||s.paused||s.needsRecovery)return
        if(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos>30_000_000_000L)return
        ParkingState.location(location);lastFix=SystemClock.elapsedRealtime()
        if(location.accuracy>50){TripStore.gps("GPS accuracy too low: ±"+location.accuracy.toInt()+" m");return}
        val speed=if(location.hasSpeed()&&location.speed.isFinite()&&location.speed in 0f..80f&&(!location.hasSpeedAccuracy()||location.speedAccuracyMetersPerSecond<=3f))location.speed.toDouble()else null
        try{TripStore.point(TrackPoint(location.latitude,location.longitude,location.time,location.accuracy,speed,altitude=location.altitude.takeIf{location.hasAltitude()&&it.isFinite()&&it in -500.0..9000.0&&location.hasVerticalAccuracy()&&location.verticalAccuracyMeters<=20f}))}
        catch(_:Exception){TripStore.storageError.value="Storage error; recording interrupted. Free space, then Resume or End.";TripStore.interrupted();stopSelf()}
    }
    override fun onProviderDisabled(provider:String){TripStore.gps("GPS is disabled")}
    override fun onDestroy(){running=false;scope.cancel();manager.removeUpdates(this);TripStore.interrupted();SharedLibrary.changed();super.onDestroy()}
}
