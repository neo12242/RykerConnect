package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun ServiceChoice(options: List<JSONObject>, selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text((options.firstOrNull { it.getString("id") == selected }?.getString("name") ?: "Choose service") + " ▾")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { item -> DropdownMenuItem(text = { Text(item.getString("name")) },
                onClick = { choose(item.getString("id")); expanded = false }) }
        }
    }
}

@Composable fun ServiceSettings() {
    val revision by SoftwareStore.revision.collectAsState()
    val types = remember(revision) { ServiceCatalog.items(ServiceCatalog.migrate(SoftwareStore.snapshot())) }
    val units by RideState.preferences.collectAsState()
    val factor = if (units.imperial) 1.609344 else 1.0
    var selected by rememberSaveable { mutableStateOf(types.first().getString("id")) }
    var name by rememberSaveable(selected) { mutableStateOf(types.firstOrNull { it.getString("id") == selected }?.getString("name") ?: "") }
    val original = types.firstOrNull { it.getString("id") == selected }
    var km by rememberSaveable(selected, units.imperial) { mutableStateOf(((original?.optDouble("intervalKm") ?: 0.0) / factor).toString()) }
    var days by rememberSaveable(selected) { mutableStateOf((original?.optInt("intervalDays") ?: 0).toString()) }
    var enabled by rememberSaveable(selected) { mutableStateOf(original?.optBoolean("enabled") ?: true) }
    var message by remember { mutableStateOf("") }
    Text("Services & intervals", style = MaterialTheme.typography.headlineSmall)
    Text("Choose a service to edit, or add your own. New intervals are unset until you configure them for your Ryker.")
    ServiceChoice(types, selected) { selected = it; message = "" }
    TextButton(onClick = { selected = java.util.UUID.randomUUID().toString(); message = "" }) { Text("Add service type") }
    if (selected == ServiceCatalog.MILEAGE) {
        Text("Mileage record is always available. Record the odometer as often as you like; it never resets maintenance reminders.")
    } else {
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Service name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(km, { km = it.take(12) }, label = { Text("Repeat distance (${if (units.imperial) "miles" else "km"}; 0 = none)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(days, { days = it.take(4) }, label = { Text("Repeat days (0 = none)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { Text("Available for new entries", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        Text("Reminders use the last completed service and your latest recorded odometer. When both intervals are set, the earlier one applies. Disabling keeps history.")
        Button(onClick = { runCatching {
            val item = ServiceCatalog.definition(name, selected).put("intervalKm", (km.replace(',', '.').toDoubleOrNull() ?: error("Enter a distance")) * factor)
                .put("intervalDays", days.toIntOrNull() ?: error("Enter whole days")).put("enabled", enabled).put("configured", true)
            ServiceCatalog.validate(item)
            require(types.none { it.getString("id") != selected && it.getString("name").equals(name.trim(), true) }) { "That service name already exists" }
            val next = ServiceCatalog.migrate(SoftwareStore.snapshot())
            val list = ServiceCatalog.items(next).filter { it.getString("id") != selected }.toMutableList()
            val index = types.indexOfFirst { it.getString("id") == selected }
            list.add(if (index < 0) list.size else index.coerceAtMost(list.size), item)
            SoftwareStore.commit(next.put("serviceTypes", org.json.JSONArray(list)))
            message = "Service settings saved"
        }.onFailure { message = it.message ?: "Could not save service settings" } }) { Text("Save service settings") }
    }
    if (message.isNotBlank()) Text(message)
}

@Composable fun ServiceSummary() {
    val revision by SoftwareStore.revision.collectAsState()
    val units by RideState.preferences.collectAsState()
    val fuel = remember(revision) { SoftwareStore.records("fuel") }
    val history = remember(revision) { SoftwareStore.records("maintenance") }
    val types = remember(revision) { ServiceCatalog.items(ServiceCatalog.migrate(SoftwareStore.snapshot())) }
    val odometer = (fuel + history).maxOfOrNull { it.optDouble("odometerKm", 0.0) } ?: 0.0
    Text("Upcoming service", style = MaterialTheme.typography.titleLarge)
    val recurring = types.filter { it.optBoolean("enabled") && it.getString("id") != ServiceCatalog.MILEAGE && (it.optDouble("intervalKm") > 0 || it.optInt("intervalDays") > 0) }
    if (recurring.isEmpty()) Text("Set your service intervals in Settings → Services & intervals.")
    recurring.forEach { type -> ToolCard {
        Text(type.getString("name"), style = MaterialTheme.typography.titleMedium)
        val last = ServiceCatalog.latest(type, history)
        if (last == null) Text("No service recorded yet. Log the last completed service in My Garage to start this reminder.")
        else {
            ServiceCatalog.remainingKm(type, history, odometer)?.let { left ->
                Text(if (left <= 0) "Mileage service due" else "In " + RideUnits.distance(left * 1000, units.imperial))
            }
            if (type.optInt("intervalDays") > 0) {
                val due = last.getLong("time") + type.getInt("intervalDays") * 86_400_000L
                Text(if (due <= System.currentTimeMillis()) "Date service due" else "Due " + toolDate(due))
            }
        }
    } }
    GarageCosts(fuel, history.filterNot(ServiceCatalog::isMileage), units.imperial)
}
