package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.ui.screens.settingsscreen.AppSettingsScreen
import kotlinx.coroutines.delay

/** Phone tools share the same data paths as the companion, in a separate app sandbox. */
@Composable
internal fun PhoneAppShell(store: RykerConnectStore) {
    val context=LocalContext.current
    var tab by rememberSaveable { mutableStateOf("Dashboard") }
    val states = rememberSaveableStateHolder()
    val tabs = listOf("Dashboard" to Icons.Default.Dashboard, "My Trips" to Icons.Default.Route,
        "My Garage" to Icons.Default.Garage, "Settings" to Icons.Default.Settings)
    BackHandler(tab != "Dashboard") { tab = "Dashboard" }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            NavigationBar {
                tabs.forEach { (name, icon) ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name },
                        icon = { Icon(icon, null) }, label = { Text(name) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            states.SaveableStateProvider(tab) {
                var page by rememberSaveable { mutableStateOf<String?>(null) }
                val pages = rememberSaveableStateHolder()
                BackHandler(page != null) { page = null }
                pages.SaveableStateProvider(page ?: "overview") {
                    if (page == "Appearance") AppSettingsScreen(onBack = { page = null }, store = store)
                    else if (page != null) Column {
                        PageBack(page!!.substringBefore('/')) { page = null }
                        ScrollPage(null, null) {
                            when (val current = page!!) {
                                "Recording" -> PhoneRecordingCard()
                                "Add-ons" -> DadRidesSettings()
                                "Backup & restore" -> BackupTools()
                                "Units" -> PhoneUnits()
                                "Vehicle profile" -> VehicleProfileScreen()
                                "Service reminders" -> ServiceReminderSettings { page = it }
                                "Ride summaries" -> RideSummarySettings()
                                "Widgets" -> WidgetSettings()
                                "Services & intervals" -> ServiceSettings()
                                "Maintenance planner" -> MaintenancePlannerScreen { page = it }
                                "Modifications" -> ModificationsScreen { page = it }
                                "Last Parked" -> ParkingTools()
                                "Statistics" -> StatisticsTools()
                                "Offline maps" -> OfflineTools()
                                "Theme" -> de.chaostheorybot.rykerconnect.ui.theme.ThemePicker()
                                else -> when {
                                    current.startsWith("Modification/") -> ModificationEditor(current.substringAfter('/')) { page = "Modifications"; pages.removeState(current) }
                                    current.startsWith("Ownership season/") -> OwnershipSeasonEditor(current.substringAfter('/')) { page = null; pages.removeState(current) }
                                    current.startsWith("Summary/") -> RideSummaryScreen(current.substringAfter('/')) { page = it }
                                    current.startsWith("Journal/") -> RideJournalScreen(current.substringAfter('/'))
                                    current in listOf("Add fuel", "Add service", "Add mileage") || current.startsWith("Complete plan/") || current.startsWith("Edit ") || current.startsWith("Repeat ") || current.startsWith("Log service/") ->
                                        GarageEntry(current) { page = null; pages.removeState(current) }
                                    else -> Text("This tool is available in the ESP companion edition.")
                                }
                            }
                        }
                    } else when (tab) {
                        "Dashboard" -> ScrollPage("RykerConnect Phone", "YOUR RIDE / YOUR PHONE") {
                            Text("Record with your phone's GPS. No ESP or Bluetooth setup needed.")
                            PhoneRecordingCard()
                            LatestRideCard { page = it }
                            DestinationCard("My Trips", "Saved rides, maps, photos and publishing", Icons.Default.Route) { tab = "My Trips" }
                            DestinationCard("DadRides uploads", "Connect your site and publishing key", Icons.Default.CloudUpload) { page = "Add-ons" }
                            BackupReminder { page = "Backup & restore" }
                        }
                        "My Trips" -> TripJournal(embedded = true, openTool = { page = it }) { tab = "Dashboard" }
                        "My Garage" -> ScrollPage("My Garage", "CARE FOR YOUR RYKER") { SharedGarageOverview { page = it } }
                        "Settings" -> ScrollPage("Settings", "RYKERCONNECT PHONE") {
                            DestinationCard("Vehicle profile", "Your Ryker and ownership details", Icons.Default.Garage) { page = "Vehicle profile" }
                            DestinationCard("Add-ons", "Optional DadRides publishing", Icons.Default.Extension) { page = "Add-ons" }
                            DestinationCard("Watch controls", "Start, pause, resume and end from Wear OS", Icons.Default.Watch) { context.startActivity(Intent(context,WatchSetupActivity::class.java)) }
                            DestinationCard("Units", "Distance, temperature and clock format", Icons.Default.Tune) { page = "Units" }
                            DestinationCard("Ride summaries", "End-of-ride notifications", Icons.Default.Route) { page = "Ride summaries" }
                            DestinationCard("Service reminders", "Notifications for maintenance", Icons.Default.Notifications) { page = "Service reminders" }
                            DestinationCard("Services & intervals", "Distance and date reminders", Icons.Default.Build) { page = "Services & intervals" }
                            DestinationCard("Theme", "Colors and finishes", Icons.Default.Palette) { page = "Theme" }
                            DestinationCard("Appearance", "Wallpaper colors and app appearance", Icons.Default.Palette) { page = "Appearance" }
                            DestinationCard("Backup & restore", "Save data or move it between editions", Icons.Default.Backup) { page = "Backup & restore" }
                            Text("Phone edition · Manual GPS recording\nEnable Shared library to keep rides and services in sync with the ESP edition. Notification mirroring and Maps/music integration are not included.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneRecordingCard() {
    ToolCard { RideRecordingControls() }
}

@Composable
private fun PhoneUnits() {
    val units by RideState.preferences.collectAsState()
    @Composable fun Choice(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f)); Switch(checked, change)
        }
    }
    Choice("Miles and feet", units.imperial) { RideState.save(units.copy(imperial = it)) }
    Choice("Fahrenheit", units.fahrenheit) { RideState.save(units.copy(fahrenheit = it)) }
    Choice("12-hour clock", units.twelveHour) { RideState.save(units.copy(twelveHour = it)) }
}
