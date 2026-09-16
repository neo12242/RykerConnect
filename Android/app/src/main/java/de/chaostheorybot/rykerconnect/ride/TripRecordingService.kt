package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.MainActivity
import de.chaostheorybot.rykerconnect.R

class TripRecordingService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastFix = 0L
    private val checkGps = object : Runnable {
        override fun run() {
            if (TripStore.summary.value.recording && SystemClock.elapsedRealtime() - lastFix > 30_000) TripStore.gps("GPS unavailable or stale")
            handler.postDelayed(this, 10_000)
        }
    }
    override fun onCreate() { super.onCreate(); manager = getSystemService(LocationManager::class.java) }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { AutoRide.manualStop(); TripStore.stop(); stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "AUTO_STOP") { TripStore.stop("Saved after disconnect", intent.getLongExtra("endAt", System.currentTimeMillis())); stopSelf(); return START_NOT_STICKY }
        if (TripStore.summary.value.recording) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        val channel = "ride_recording"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Ride recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TripRecordingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Recording your ride")
            .setContentText("GPS route is saved on this phone").setContentIntent(open).setOngoing(true).addAction(0, "Stop recording", stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(42, notification)
            val recovered=if(intent?.action=="AUTO")TripStore.recoverable(System.currentTimeMillis()) else null
            if(recovered!=null)TripStore.resume(recovered) else TripStore.start(automatic = intent?.action == "AUTO")
            lastFix = SystemClock.elapsedRealtime()
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000, 0f, this, Looper.getMainLooper())
            handler.post(checkGps)
        } catch (e: Exception) { AutoRide.failed("Recording blocked: ${e.javaClass.simpleName}; check location permissions"); TripStore.stop("Recording stopped: ${e.javaClass.simpleName}"); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location: Location) {
        if (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos > 30_000_000_000L) return
        ParkingState.location(location)
        lastFix = SystemClock.elapsedRealtime()
        if (location.accuracy > 50) { TripStore.gps("GPS accuracy too low: ±${location.accuracy.toInt()} m"); return }
        val speed = if (location.hasSpeed() && location.speed.isFinite() && location.speed in 0f..80f && (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond <= 3f)) location.speed.toDouble() else null
        try { TripStore.point(TrackPoint(location.latitude, location.longitude, location.time, location.accuracy, speed, altitude = location.altitude.takeIf { location.hasAltitude() && it.isFinite() && it in -500.0..9000.0 && location.hasVerticalAccuracy() && location.verticalAccuracyMeters <= 20f })) }
        catch (_: Exception) { TripStore.stop("Storage error"); stopSelf() }
    }
    override fun onProviderDisabled(provider: String) { TripStore.gps("GPS is disabled") }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        manager.removeUpdates(this)
        TripStore.stop("Recording stopped")
        super.onDestroy()
    }
}
