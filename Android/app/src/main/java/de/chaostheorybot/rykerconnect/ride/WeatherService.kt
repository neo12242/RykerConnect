package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.*
import android.content.pm.PackageManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.MainActivity
import de.chaostheorybot.rykerconnect.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Uses a GPS fix, never IP location or a preset city. No route or location history is stored. */
class WeatherService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fix: WeatherFix? = null
    private var retryAt = 0L
    private var fetching = false
    private var failure: String? = null
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (WeatherState.running) return START_NOT_STICKY
        if (!WeatherState.enabled.value) { stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            WeatherState.status("Allow precise location for GPS weather"); stopSelf(); return START_NOT_STICKY
        }
        val channel = "ride_weather"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Ride weather", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 44, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GPS weather for your ride").setContentText("Weather updates while Ryker is connected")
            .setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(44, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(44, notification)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000, 0f, this, Looper.getMainLooper())
            WeatherState.running = true
            scope.launch { while (isActive) { tick(); delay(5_000) } }
        } catch (e: Exception) {
            Log.w("RykerWeather", "GPS weather start failed: ${e.javaClass.simpleName}")
            WeatherState.status("GPS unavailable; check precise location permission"); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (location.provider != LocationManager.GPS_PROVIDER || !location.hasAccuracy() || location.accuracy > 1_000 ||
            now - location.elapsedRealtimeNanos / 1_000_000 !in 0..WeatherPolicy.GPS_MAX_AGE ||
            !location.latitude.isFinite() || !location.longitude.isFinite()) return
        ParkingState.location(location)
        fix = WeatherFix(location.latitude, location.longitude, location.elapsedRealtimeNanos / 1_000_000)
        tick()
    }
    override fun onProviderDisabled(provider: String) { fix = null; WeatherState.status("GPS disabled") }
    private fun tick() {
        val now = SystemClock.elapsedRealtime(); val currentFix = fix
        val report = WeatherState.view.value.report
        val gpsReady = WeatherPolicy.fresh(currentFix, now)
        val status = when {
            !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> "GPS disabled"
            !gpsReady -> if (currentFix == null) "Waiting for GPS" else "GPS stale; waiting for a new fix"
            fetching -> "Updating weather"
            failure != null -> failure!!
            else -> "GPS weather"
        }
        WeatherState.view.value = WeatherView(report, status,
            !gpsReady || failure != null || report == null || WeatherPolicy.stale(report, currentFix, now, System.currentTimeMillis()))
        if (gpsReady && !fetching && now >= retryAt && (failure != null || WeatherPolicy.due(report, currentFix!!, now))) {
            fetching = true; retryAt = now + 120_000
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { fetch(currentFix!!) }
                    ensureActive()
                    failure = null
                    WeatherState.view.value = WeatherView(result, "GPS weather", true)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    failure = "Weather unavailable; retrying automatically"
                    retryAt = SystemClock.elapsedRealtime() + 60_000
                    Log.w("RykerWeather", "Weather request failed: ${e.javaClass.simpleName}")
                } finally { fetching = false }
                tick()
            }
        }
    }
    private fun fetch(location: WeatherFix): WeatherReport {
        // About 100 m precision is sufficient for forecast grids; never send the ride track.
        val coords = String.format(Locale.US, "latitude=%.3f&longitude=%.3f", location.latitude, location.longitude)
        val url = URL("https://api.open-meteo.com/v1/forecast?$coords&current=temperature_2m,weather_code,wind_speed_10m&hourly=precipitation_probability&forecast_days=2&timeformat=unixtime&timezone=GMT")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        try {
            check(connection.responseCode == 200) { "Weather HTTP error" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return WeatherJson.parse(body, location, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        } finally { connection.disconnect() }
    }
    override fun onDestroy() {
        scope.cancel(); runCatching { manager.removeUpdates(this) }
        WeatherState.running = false
        WeatherState.status(if (WeatherState.enabled.value) "Weather paused" else "Weather off")
        super.onDestroy()
    }
}

object WeatherJson {
    fun parse(body: String, fix: WeatherFix, wall: Long, elapsed: Long): WeatherReport {
        val json = JSONObject(body); val current = json.getJSONObject("current")
        val temp = current.getDouble("temperature_2m"); val wind = current.getDouble("wind_speed_10m")
        require(temp.isFinite() && temp in -100.0..70.0 && wind.isFinite() && wind in 0.0..500.0)
        val time = current.getLong("time") * 1000
        require(wall - time in -900_000..3_600_000) { "Weather forecast is stale" }
        val hourly = json.optJSONObject("hourly")
        val times = hourly?.optJSONArray("time"); val probabilities = hourly?.optJSONArray("precipitation_probability")
        var rain: Int? = null
        if (times != null && probabilities != null) for (i in 0 until minOf(times.length(), probabilities.length())) {
            val hour = times.getLong(i) * 1000
            if (time in hour until hour + 3_600_000 && !probabilities.isNull(i)) {
                val value = probabilities.getDouble(i)
                if (value.isFinite() && value in 0.0..100.0) rain = value.toInt()
            }
        }
        return WeatherReport(temp, wind, current.getInt("weather_code"), rain, time, wall, elapsed, fix)
    }
}
