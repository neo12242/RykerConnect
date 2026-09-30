package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

@Composable fun ServiceBaselineSettings() {
    val revision by SoftwareStore.revision.collectAsState()
    val data = remember(revision) { SoftwareStore.snapshot() }
    val baselines = ServiceBaselines.records(data)
    val units by RideState.preferences.collectAsState()
    val factor = if (units.imperial) 1.609344 else 1.0
    var expanded by rememberSaveable { mutableStateOf(false) }
    val saved = baselines.firstOrNull { it.optBoolean("enabled") } ?: baselines.firstOrNull()
    // Unsaved starting values belong to the owner and must be entered explicitly.
    var date by rememberSaveable(saved?.toString()) { mutableStateOf(saved?.optString("date") ?: "") }
    var mileage by rememberSaveable(saved?.toString(), units.imperial) { mutableStateOf(saved?.let { String.format(Locale.US,"%.6f",it.getDouble("odometerKm")/factor).trimEnd('0').trimEnd('.') } ?: "") }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var source by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf(false) }
    Text("New-bike service baseline", style = MaterialTheme.typography.titleLarge)
    Text("Start tracking from the date and mileage the bike entered service. Completed service records always take precedence.")
    Text(if (baselines.none { it.optBoolean("enabled") }) "No active new-bike baseline" else "${baselines.count { it.optBoolean("enabled") }} starting points saved")
    OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Close baseline setup" else "Set new-bike baseline") }
    if (expanded) {
        OutlinedTextField(date, { date = it.take(10); preview = null }, label = { Text("In-service date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mileage, { mileage = it.take(16); preview = null }, label = { Text("Starting odometer (${if(units.imperial)"miles" else "km"})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            runCatching {
                val latest = SoftwareStore.snapshot()
                val km = (mileage.replace(',', '.').toDoubleOrNull() ?: error("Enter a starting odometer")) * factor
                ServiceBaselines.create("preview", date, km)
                preview = ServiceBaselines.apply(latest, date, km); source = latest.toString(); removing = false
            }.onFailure { message = it.message ?: "Check the baseline values" }
        }) { Text("Preview baseline") }
        if (baselines.any { it.optBoolean("enabled") }) TextButton(onClick = {
            val latest = SoftwareStore.snapshot(); source = latest.toString(); preview = ServiceBaselines.remove(latest); removing = true
        }) { Text("Remove new-bike baselines") }
    }
    if (message.isNotBlank()) Text(message)
    preview?.let { next ->
        val history = SoftwareStore.records("maintenance")
        AlertDialog(onDismissRequest = { preview = null }, title = { Text(if(removing) "Remove new-bike baselines?" else "Review service starting points") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(removing) "Only the starting points will be disabled. Completed services, intervals and spending remain unchanged. Services without completed history will stop tracking until a baseline is restored." else "$date at $mileage ${if(units.imperial)"miles" else "km"}. This does not record completed work.")
                if (!removing) for (type in ServiceCatalog.items(next).filter { it.optBoolean("enabled") && it.getString("id") != ServiceCatalog.MILEAGE }) {
                    Text(type.getString("name") + ": " + when {
                        ServiceCatalog.latest(type, history) != null -> "keep completed-service history"
                        type.optDouble("intervalKm") <= 0 && type.optInt("intervalDays") <= 0 -> "set baseline; interval still needs configuration"
                        else -> "start tracking from this baseline"
                    })
                }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching {
                    SoftwareStore.mutate { current ->
                        check(current.toString() == source) { "Records changed. Preview again before applying." }
                        current.put("serviceBaselines", next.getJSONArray("serviceBaselines"))
                    }
                    message = if(removing) "New-bike baselines removed. Completed history retained." else "New-bike baselines saved. Items with configured intervals are now tracking."
                    preview = null
                }.onFailure { message = it.message ?: "Could not save baselines"; preview = null }
            }) { Text(if(removing) "Remove baselines" else "Apply baseline") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } })
    }
}
