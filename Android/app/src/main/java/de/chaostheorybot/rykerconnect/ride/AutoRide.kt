package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

object AutoRide {
    val enabled = MutableStateFlow(true)
    val status = MutableStateFlow("Checking automatic recording")
    private val policy = TripPolicy()
    private lateinit var app: Application
    private var visibleActivities = 0
    private var pendingUntil = 0L
    private var markedDisconnect = false
    private var wasConnected = false
    fun init(application: Application) {
        app = application
        enabled.value = app.getSharedPreferences("auto_ride", Context.MODE_PRIVATE).getBoolean("enabled", true)
        if (app.getSharedPreferences("auto_ride", Context.MODE_PRIVATE).getBoolean("stopped", false)) policy.manualStop()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { visibleActivities++ }
            override fun onActivityStopped(activity: Activity) { visibleActivities = (visibleActivities - 1).coerceAtLeast(0) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            while (isActive) { try { tick() } catch (e: Exception) { status.value = "Automatic recording error: ${e.javaClass.simpleName}" }; delay(1_000) }
        }
    }
    fun setEnabled(value: Boolean) { enabled.value = value; app.getSharedPreferences("auto_ride", Context.MODE_PRIVATE).edit().putBoolean("enabled", value).apply() }
    fun manualStop() { policy.manualStop(); app.getSharedPreferences("auto_ride", Context.MODE_PRIVATE).edit().putBoolean("stopped", policy.isSuppressed).apply(); pendingUntil = 0; status.value = "Stopped by you; automatic recording resumes on the next connection" }
    fun failed(message: String) { status.value = message; pendingUntil = SystemClock.elapsedRealtime() + 30_000 }
    fun hasBackgroundLocation(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    private suspend fun tick() {
        val connected = RykerConnectApplication.activeConnection.value?.isConnected?.value == true
        if (!connected && wasConnected) app.getSharedPreferences("auto_ride", Context.MODE_PRIVATE).edit().putBoolean("stopped", false).apply()
        ParkingState.connection(connected)
        wasConnected = connected
        WeatherState.connection(connected, visibleActivities > 0)
        val recording = TripStore.summary.value
        val now = SystemClock.elapsedRealtime()
        val action = policy.tick(connected, enabled.value, recording.recording, recording.automatic, now, System.currentTimeMillis())
        val lost = policy.disconnectedAt != null
        if (recording.recording && recording.automatic && lost != markedDisconnect && action != TripPolicy.Action.FINISH) {
            withContext(Dispatchers.IO) { TripStore.markDisconnect(if (lost) System.currentTimeMillis() else null) }; markedDisconnect = lost
        }
        when (action) {
            TripPolicy.Action.START -> {
                if (now < pendingUntil) return
                if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { status.value = "Allow precise location to record automatically"; return }
                if (visibleActivities == 0 && !hasBackgroundLocation(app)) { status.value = "Allow location all the time for automatic starts while locked"; return }
                try {
                    pendingUntil = now + 15_000
                    ContextCompat.startForegroundService(app, Intent(app, TripRecordingService::class.java).setAction("AUTO"))
                    status.value = "Starting automatic trip"
                } catch (e: Exception) { failed("Automatic start blocked; open app and check location permissions") }
            }
            TripPolicy.Action.FINISH -> {
                app.startService(Intent(app, TripRecordingService::class.java).setAction("AUTO_STOP").putExtra("endAt", policy.finishAt))
                status.value = "Trip saved after disconnect"; markedDisconnect = false
            }
            TripPolicy.Action.NONE -> {
                if (recording.recording) status.value = if (lost) "Disconnected · saving in ${((120_000 - (now - policy.disconnectedAt!!)) / 1000).coerceAtLeast(0)}s unless reconnected" else if (recording.automatic && recording.meters < 100) "Connected session · waiting for movement" else if (recording.automatic) "Recording automatically" else "Recording manually"
                else if (!enabled.value) status.value = "Automatic recording is off"
                else if (!connected) status.value = "Ready · waiting for the Ryker main unit"
                else if (policy.isSuppressed) status.value = "Stopped by you; automatic recording resumes on the next connection"
            }
        }
    }
}
