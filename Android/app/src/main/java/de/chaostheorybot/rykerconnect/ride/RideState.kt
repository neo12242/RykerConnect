package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

data class RidePreferences(val imperial: Boolean = true, val fahrenheit: Boolean = true,
    val twelveHour: Boolean = true, val largeText: Boolean = true, val navigation: Boolean = true,
    val hideBody: Boolean = false, val allowAll: Boolean = true, val navPriority: Boolean = true,
    val allowed: Set<String> = emptySet(), val musicLeft: Boolean = true)

object RideState {
    val preferences = MutableStateFlow(RidePreferences())
    val navigation = MutableStateFlow(NavigationFrame())
    val observedApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val notificationStatus = MutableStateFlow("Waiting for notifications")
    val listenerConnected = MutableStateFlow(false)
    val displaySupport = MutableStateFlow("Connect a display to check riding features")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sampleJob: Job? = null
    private var liveNavigation = NavigationFrame()
    private var sample = false
    private lateinit var context: Context
    fun init(app: Context) {
        context = app.applicationContext
        val p = context.getSharedPreferences("ride_options", Context.MODE_PRIVATE)
        preferences.value = RidePreferences(p.getBoolean("imperial", true), p.getBoolean("fahrenheit", true), p.getBoolean("twelve", true), p.getBoolean("large", true), p.getBoolean("navigation", true), p.getBoolean("hide", false), p.getBoolean("all", true), p.getBoolean("priority", true), p.getStringSet("allowed", emptySet())!!.toSet())
        preferences.value = preferences.value.copy(musicLeft = p.getBoolean("music_left", true))
        TripStore.init(context)
        SoftwareStore.init(context)
        RidePhotos.init(context)
        RideCompletion.init(context)
        DadRides.init(context)
        WeatherState.init(context)
        scope.launch {
            while (isActive) {
                try { sendDisplayState() } catch (e: CancellationException) { throw e } catch (_: Exception) { displaySupport.value = "Riding display update failed" }
                delay(2_000)
            }
        }
    }
    fun save(value: RidePreferences) {
        preferences.value = value
        context.getSharedPreferences("ride_options", Context.MODE_PRIVATE).edit()
            .putBoolean("imperial", value.imperial).putBoolean("fahrenheit", value.fahrenheit)
            .putBoolean("twelve", value.twelveHour).putBoolean("large", value.largeText)
            .putBoolean("navigation", value.navigation).putBoolean("hide", value.hideBody)
            .putBoolean("all", value.allowAll).putBoolean("priority", value.navPriority)
            .putStringSet("allowed", value.allowed).putBoolean("music_left", value.musicLeft).apply()
    }
    fun live(frame: NavigationFrame) { liveNavigation = frame; if (!sample) navigation.value = frame }
    fun sampleRoute() {
        sampleJob?.cancel(); sample = true
        sampleJob = scope.launch {
            val steps = listOf("In 0.2 mi, turn right onto Demo Road", "In 300 ft, turn right onto Demo Road", "In 0.5 mi, turn left onto Sample Avenue", "You have arrived")
            for (step in steps) {
                navigation.value = MapsParser.parse(step, "", "Sample arrival 12:30 PM", System.currentTimeMillis()).copy(source = "Sample route")
                delay(8_000)
            }
            useLive()
        }
    }
    fun useLive() { sample = false; sampleJob?.cancel(); navigation.value = liveNavigation }
    fun priorityActive(): Boolean = preferences.value.navigation && preferences.value.navPriority && navigation.value.let { it.active && !it.stale(System.currentTimeMillis()) }
    private suspend fun sendDisplayState() {
        val c = RykerConnectApplication.activeConnection.value
        if (c?.isConnected?.value != true) { displaySupport.value = "Display disconnected"; return }
        if (!c.supportsRideDisplay()) { displaySupport.value = "This firmware needs the riding-display extension"; return }
        val p = preferences.value
        val unitsOk = c.writeRideFrame(JSONObject().put("v", 1).put("type", "units").put("imperial", p.imperial).put("fahrenheit", p.fahrenheit).put("twelve", p.twelveHour).put("large", p.largeText).put("musicLeft", p.musicLeft).put("drivingField", SoftwareStore.value("drivingField").ifBlank { "distance_time" }).toString())
        val n = navigation.value
        val frame = JSONObject().put("v", 1).put("type", "nav").put("active", p.navigation && n.active)
            .put("stale", n.stale(System.currentTimeMillis())).put("instruction", n.instruction.take(90))
            .put("direction", n.direction).put("distance", n.meters?.let { RideUnits.distance(it, p.imperial) } ?: "")
            .put("arrival", n.arrival.take(40)).put("source", n.source)
        // UTF-8 can be wider than the character count; stay below the negotiated BLE payload.
        if (frame.toString().toByteArray(Charsets.UTF_8).size > 470) frame.put("instruction", n.instruction.take(35)).put("arrival", n.arrival.take(20))
        val navOk = c.writeRideFrame(frame.toString())
        val trip = TripStore.summary.value
        val ok = c.writeRideFrame(JSONObject().put("v", 1).put("type", "trip").put("active", trip.recording)
            .put("distance", RideUnits.distance(trip.meters, p.imperial)).put("duration", trip.durationText())
            .put("gps", trip.gps).toString())
        val weatherOk = c.writeRideFrame(WeatherState.frame())
        displaySupport.value = if (unitsOk && navOk && ok && weatherOk) "Riding display synchronized" else "Riding display update failed"
    }
}
