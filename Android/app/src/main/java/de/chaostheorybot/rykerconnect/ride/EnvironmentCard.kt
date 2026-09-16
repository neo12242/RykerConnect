package de.chaostheorybot.rykerconnect.ride

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EnvironmentCard() {
    val locale = LocalConfiguration.current.locales[0]
    val reading by EnvironmentState.reading.collectAsState()
    val status by EnvironmentState.status.collectAsState()
    val options by RideState.preferences.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = SystemClock.elapsedRealtime() } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Outside sensor", style = MaterialTheme.typography.titleMedium)
            val r = reading
            val observedNow = maxOf(now, SystemClock.elapsedRealtime())
            if (r != null && r.fresh(observedNow)) {
                val temperature = if (options.fahrenheit) r.celsius * 1.8 + 32 else r.celsius.toDouble()
                Text(String.format(locale, "%.1f°%s · %.0f%% humidity", temperature,
                    if (options.fahrenheit) "F" else "C", r.humidity), style = MaterialTheme.typography.headlineSmall)
                Text(String.format(locale, "Pressure: %.1f hPa (%.2f inHg)", r.pressureHpa, r.pressureHpa * 0.029529983))
                Text("${if(r.simulated)"SIMULATED BME280" else "Measured at the Ryker"} · ${(r.sampleAgeMs + observedNow - r.receivedAtMs) / 1000}s ago", style = MaterialTheme.typography.bodySmall)
            } else Text(if (r != null) "Sensor reading stale · waiting for a fresh sample" else status)
            if (r != null && status.contains("delayed")) Text(status, style = MaterialTheme.typography.bodySmall)
            Text(if(r?.simulated==true) "Test readings from the virtual ESP. Separate from GPS weather." else "Temperature, humidity and local barometric pressure. Separate from the GPS weather forecast.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

