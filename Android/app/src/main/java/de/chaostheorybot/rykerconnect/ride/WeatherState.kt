package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

data class WeatherView(val report: WeatherReport? = null, val status: String = "Waiting for GPS",
    val stale: Boolean = true)

object WeatherState {
    val enabled = MutableStateFlow(true)
    val view = MutableStateFlow(WeatherView())
    @Volatile var running = false
    private lateinit var app: Context
    private var nextStart = 0L
    fun init(context: Context) {
        app = context.applicationContext
        enabled.value = app.getSharedPreferences("weather", Context.MODE_PRIVATE).getBoolean("enabled", true)
    }
    fun setEnabled(value: Boolean) {
        enabled.value = value
        app.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().putBoolean("enabled", value).apply()
        if (!value) { app.stopService(Intent(app, WeatherService::class.java)); status("Weather off") }
        nextStart = 0
    }
    fun status(message: String) { view.value = view.value.copy(status = message, stale = true) }
    fun connection(connected: Boolean, visible: Boolean) {
        if (!enabled.value || !connected) {
            if (running) app.stopService(Intent(app, WeatherService::class.java))
            status(if (!enabled.value) "Weather off" else "Display disconnected")
            nextStart = 0; return
        }
        if (running) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status("Allow precise location for GPS weather"); return
        }
        if (!visible && !AutoRide.hasBackgroundLocation(app)) { status("Open app or allow location all the time"); return }
        val now = SystemClock.elapsedRealtime()
        if (now < nextStart) return
        nextStart = now + 30_000
        try { ContextCompat.startForegroundService(app, Intent(app, WeatherService::class.java)) }
        catch (_: Exception) { status("Weather start blocked; open app and check location permissions") }
    }
    fun frame(): String {
        val v = view.value; val r = v.report; val p = RideState.preferences.value
        return JSONObject().put("v", 1).put("type", "weather").put("active", enabled.value)
            .put("stale", v.stale).put("available", r != null)
            .put("temperature", r?.temperature(p.fahrenheit) ?: "")
            .put("conditions", r?.conditions() ?: "")
            .put("wind", r?.wind(p.imperial) ?: "")
            .put("rain", r?.rainChance?.let { "$it%" } ?: "—")
            .put("status", v.status.take(90)).toString()
    }
}
