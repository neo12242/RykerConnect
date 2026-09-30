package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RidingCard() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val navigation by RideState.navigation.collectAsState()
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riding dashboard", style = MaterialTheme.typography.titleLarge)
            Text(if (navigation.active) "${navigation.source}: ${navigation.instruction}" else "Navigation, GPS weather, units, and ride history", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open = true }) { Text("Open riding features") }
            OutlinedButton(onClick = { context.startActivity(Intent(context, TripJournalActivity::class.java)) }) { Text("My trips") }
            OutlinedButton(onClick = { context.startActivity(Intent(context, RideToolsActivity::class.java)) }) { Text("Ride tools") }
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) { Dashboard { open = false } }
    }
}

@Composable
internal fun Dashboard(page: String = "all", close: () -> Unit) {
    val context = LocalContext.current
    val options by RideState.preferences.collectAsState()
    val nav by RideState.navigation.collectAsState()
    val listener by RideState.listenerConnected.collectAsState()
    val display by RideState.displaySupport.collectAsState()
    val notificationStatus by RideState.notificationStatus.collectAsState()
    val apps by RideState.observedApps.collectAsState()
    val trip by TripStore.summary.collectAsState()
    val history by TripStore.history.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var feedback by remember { mutableStateOf("") }
    var exportId by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val id = exportId
        if (uri != null && id != null) scope.launch {
            feedback = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    stream.bufferedWriter().use { it.write(TripStore.gpx(id)) }
                    "GPX exported"
                }.getOrElse { "Export failed: ${it.javaClass.simpleName}" }
            }
        }
    }
    fun startRecording() {
        try { ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java)); feedback = "Starting GPS recording…" }
        catch (e: Exception) { feedback = "Could not start recording: ${e.javaClass.simpleName}" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) startRecording() else feedback = "Precise location is needed for a useful ride track. Recording has not started."
    }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    LaunchedEffect(trip.recording) { if (trip.recording) feedback = "" }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (page == "recording") "Recording" else if (page == "preferences") "Riding preferences" else "Riding dashboard", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = close) { Text("Close") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page == "all" || page == "preferences") {
            Section("Google Maps navigation") {
                Text(if (listener) "Notification access connected" else "Notification access unavailable", style = MaterialTheme.typography.bodySmall)
                Toggle("Forward Google Maps directions", options.navigation) { RideState.save(options.copy(navigation = it)) }
                if (nav.active) {
                    Text(if (nav.stale(now)) "Directions stale — check Google Maps" else nav.source, color = MaterialTheme.colorScheme.primary)
                    Text(nav.instruction, style = if (options.largeText) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge)
                    Text(nav.meters?.let { RideUnits.distance(it, options.imperial) } ?: "Next-turn distance not provided")
                    Text(nav.arrival.ifBlank { "Arrival time not provided" })
                    Text("Updated ${((now - nav.updated).coerceAtLeast(0) / 1000)}s ago · ${if (nav.direction.isBlank()) "No reliable maneuver arrow" else nav.direction}", style = MaterialTheme.typography.bodySmall)
                } else Text("Start navigation in Google Maps. Directions will appear here when its navigation notification is available.")
                Button(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                    if (intent != null) context.startActivity(intent) else feedback = "Google Maps is not installed"
                }) { Text("Open Google Maps") }
                if (de.chaostheorybot.rykerconnect.BuildConfig.DEMO_FEATURES) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { RideState.sampleRoute() }) { Text("Try sample route") }
                    TextButton(onClick = { RideState.useLive() }) { Text("Use live Maps") }
                }
                Text(display, style = MaterialTheme.typography.bodySmall)
                Text("Riding display features require compatible ESP firmware.", style = MaterialTheme.typography.bodySmall)
            }
            }
            if (page == "all") { WeatherCard(now); EnvironmentCard() }
            if (page == "all" || page == "preferences") {
            Section("Units and readability") {
                Toggle("Music on left (off: music on right)", options.musicLeft) { RideState.save(options.copy(musicLeft = it)) }
                Text("Music and driving stay side by side. The driving screen includes trip distance and total elapsed time, including stops.", style = MaterialTheme.typography.bodySmall)
                Toggle("Miles and feet (off: kilometers/meters)", options.imperial) { RideState.save(options.copy(imperial = it)) }
                Toggle("Fahrenheit (off: Celsius)", options.fahrenheit) { RideState.save(options.copy(fahrenheit = it)) }
                Toggle("12-hour clock (off: 24-hour)", options.twelveHour) { RideState.save(options.copy(twelveHour = it)) }
                Toggle("Larger display text", options.largeText) { RideState.save(options.copy(largeText = it)) }
            }
            }
            if (page == "all" || page == "preferences") {
            Section("Notification rules") {
                Toggle("Hide message bodies", options.hideBody) { RideState.save(options.copy(hideBody = it)) }
                Toggle("Prioritize navigation over ordinary messages", options.navPriority) { RideState.save(options.copy(navPriority = it)) }
                Toggle("Allow all apps (off: selected apps only)", options.allowAll) { RideState.save(options.copy(allowAll = it)) }
                if (!options.allowAll) {
                    Text("Apps appear below after they send a notification. Unselected apps are blocked.", style = MaterialTheme.typography.bodySmall)
                    for ((pkg, name) in apps.toSortedMap()) Toggle(name, pkg in options.allowed) { checked ->
                        RideState.save(options.copy(allowed = if (checked) options.allowed + pkg else options.allowed - pkg))
                    }
                    if (apps.isEmpty()) Text("No notification apps observed yet")
                }
                Text(notificationStatus, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access settings") }
            }
            }
            if (page == "all" || page == "recording") {
            Section("Record a ride") {
                AutoRecordingControls()
                RideRecordingControls()
            }
            }
            if (page == "all") Section("Ride history") {
                Button(onClick = { context.startActivity(Intent(context, TripJournalActivity::class.java)) }) { Text("My trips — maps and details") }
                if (history.isEmpty()) Text("No saved rides yet")
                for (ride in history) {
                    Text(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ride.started)), style = MaterialTheme.typography.titleSmall)
                    Text("${RideUnits.distance(ride.meters, options.imperial)} · ${ride.durationText()} · ${ride.points} points${if (ride.interrupted) " · recording interrupted" else ""}")
                    OutlinedButton(enabled = ride.points > 0, onClick = { exportId = ride.id; export.launch("RykerConnect-${ride.started}.gpx") }) { Text("Export GPX") }
                    HorizontalDivider()
                }
            }

        }
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); content()
    } }
}
@Composable private fun Toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f)); Switch(checked, change)
    }
}
