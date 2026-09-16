package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeatherCard(now: Long) {
    val context = LocalContext.current
    val enabled by WeatherState.enabled.collectAsState()
    val weather by WeatherState.view.collectAsState()
    val units by RideState.preferences.collectAsState()
    var feedback by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        feedback = if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) "GPS permission granted. Waiting for a fresh fix." else "Precise location is required for GPS weather."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weather at your GPS location", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Weather while connected", Modifier.weight(1f))
                Switch(enabled, { WeatherState.setEnabled(it) })
            }
            Text(weather.status, color = MaterialTheme.colorScheme.primary)
            val report = weather.report
            if (enabled && report != null) {
                if (weather.stale) Text("Previous forecast — not confirmed for your current location", color = MaterialTheme.colorScheme.error)
                Text("${report.temperature(units.fahrenheit)} · ${report.conditions()}", style = MaterialTheme.typography.headlineSmall)
                Text("Wind ${report.wind(units.imperial)} · Precipitation chance ${report.rainChance?.let { "$it%" } ?: "unavailable"} this hour")
                Text("Updated ${(now - report.fetchedAt).coerceAtLeast(0) / 60_000} min ago", style = MaterialTheme.typography.bodySmall)
                Text("Forecast time: " + DateTimeFormatter.ofPattern(if (units.twelveHour) "MMM d, h:mm a" else "MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(report.forecastAt)), style = MaterialTheme.typography.bodySmall)
            }
            Text("Uses your phone's GPS. Sends rounded coordinates to Open-Meteo, never your trip history. Refreshes every 15 minutes or after moving 5 km. Forecast temperature is separate from the ESP sensor.", style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Location permissions") }
                Text("Enable phone Location. Allow all the time supports automatic weather starts with the app closed. Weather also works when trip recording is stopped.", style = MaterialTheme.typography.bodySmall)
            }
            if (feedback.isNotBlank()) Text(feedback)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) }) { Text("Weather data by Open-Meteo · CC BY 4.0") }
        }
    }
}
