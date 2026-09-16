package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import androidx.compose.foundation.Image
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import de.chaostheorybot.rykerconnect.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.logic.ConnectionHealth
import de.chaostheorybot.rykerconnect.logic.MainUnitControl
import de.chaostheorybot.rykerconnect.ui.screens.homescreen.HomeScreen
import de.chaostheorybot.rykerconnect.ui.screens.homescreen.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.chaostheorybot.rykerconnect.ui.screens.homescreen.cards.ConnectionPanel
import de.chaostheorybot.rykerconnect.ui.screens.settingsscreen.AppSettingsScreen
import kotlinx.coroutines.delay

private enum class AppTab(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    Connect("Connect", Icons.Default.Bluetooth),
    Trips("My Trips", Icons.Default.Route),
    Garage("My Garage", Icons.Default.Garage),
    Settings("Settings", Icons.Default.Settings)
}

/** Service startup belongs to the shell, never to an individual tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RykerAppShell(store: RykerConnectStore, companion: () -> Unit, reselect: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(AppTab.Dashboard.name) }
    val tab = AppTab.valueOf(selected)
    val stateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val mac by store.getBLEMACToken.collectAsState(initial = "")
    LaunchedEffect(mac) { if (mac.isNotBlank()) MainUnitControl.request(context) }
    val deviceViewModel: HomeViewModel = viewModel()
    val intercomConnected by store.getInterComConnectedToken.collectAsState(initial = false)
    val intercomMacs by store.getIntercomMacsToken.collectAsState(initial = emptyList())
    LaunchedEffect(intercomMacs, intercomConnected) {
        deviceViewModel.updateIntercomConnected(intercomConnected)
        deviceViewModel.onSelectedMacsChanged(intercomMacs)
        deviceViewModel.refreshActiveIntercom()
        delay(500)
        deviceViewModel.setBatteryStatus()
        delay(3000)
        while (intercomConnected) {
            deviceViewModel.setBatteryStatus()
            delay(240_000)
        }
    }
    BackHandler(tab != AppTab.Dashboard) { selected = AppTab.Dashboard.name }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    AppTab.entries.forEach { destination ->
                        NavigationBarItem(selected = tab == destination, onClick = { selected = destination.name },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title, maxLines = 2, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            stateHolder.SaveableStateProvider(selected) {
                var page by rememberSaveable { mutableStateOf<String?>(
                    if(tab==AppTab.Dashboard && (context as? android.app.Activity)?.intent?.getBooleanExtra("demo",false)==true) {
                        "Guided demo"
                    } else null) }
                BackHandler(page != null) { page = null }
                val pages = rememberSaveableStateHolder()
                pages.SaveableStateProvider(page ?: "overview") {
                when (page) {
                    "Device tools" -> Column {
                        PageBack("Device tools") { page = null }
                        Box(Modifier.weight(1f)) { HomeScreen(store = store, companion = companion, reselect = reselect, advanced = true) }
                    }
                    "Appearance" -> AppSettingsScreen(onBack = { page = null }, store = store)
                    "Riding preferences" -> Dashboard(page = "preferences") { page = null }
                    "Recording" -> Dashboard(page = "recording") { page = null }
                    null -> when (tab) {
                        AppTab.Dashboard -> ConnectOverview(mac.isNotBlank(), companion, reselect, dashboard = true) { page = it }
                        AppTab.Connect -> ConnectOverview(mac.isNotBlank(), companion, reselect) { page = it }
                        AppTab.Trips -> TripJournal(embedded = true, openTool = { page = it }) { selected = AppTab.Connect.name }
                        AppTab.Garage -> ScrollPage("My Garage", "CARE FOR YOUR RYKER") {
                            GarageTools(openEntry = { page = it })
                        }
                        AppTab.Settings -> ScrollPage("Settings", "MAKE IT YOURS") {
                            BackupReminder { page="Backup & restore" }
                            Text("Your ride. Your setup.", style = MaterialTheme.typography.headlineSmall)
                            Text("Preferences and history stay on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DestinationCard("Vehicle profile", "Your Ryker, purchase details and home address", Icons.Default.Garage) { page = "Vehicle profile" }
                            DestinationCard("Service reminders", "Advance warnings, notifications and snooze", Icons.Default.Notifications) { page = "Service reminders" }
                            DestinationCard("Add-ons", "Optional DadRides publishing", Icons.Default.Extension) { page = "Add-ons" }
                            DestinationCard("Ride summaries", "End-of-ride notification preferences", Icons.Default.Route) { page = "Ride summaries" }
                            DestinationCard("Widgets", "Quick actions, My Ryker and Last Parked", Icons.Default.Widgets) { page = "Widgets" }
                            DestinationCard("Services & intervals", "Service types and distance or date reminders", Icons.Default.Build) { page = "Services & intervals" }
                            DestinationCard("Dashboard layout", "Choose, reorder and collapse your cards", Icons.Default.Dashboard) { page = "Connect dashboard" }
                            DestinationCard("Connection health", "ESP, sensor, GPS, weather and intercom status", Icons.Default.Bluetooth) { page = "Connection health" }
                            DestinationCard("Guided demo", "Explore a moving sample ride without hardware", Icons.Default.PlayArrow) { page = "Guided demo" }
                            DestinationCard("Display & profiles", "Driving fields and saved display layouts", Icons.Default.Dashboard) { page = "Display & profiles" }
                            DestinationCard("Riding preferences", "Units, music position, navigation and notifications", Icons.Default.Tune) { page = "Riding preferences" }
                            DestinationCard("Recording", "Automatic recording and location access", Icons.Default.MyLocation) { page = "Recording" }
                            DestinationCard("Weather", "GPS weather and location permissions", Icons.Default.Cloud) { page = "Weather" }
                            DestinationCard("Theme", "Eight colors and finishes for your app", Icons.Default.Palette) { page = "Theme" }
                            DestinationCard("Appearance", "Wallpaper colors and app appearance", Icons.Default.Palette) { page = "Appearance" }
                            DestinationCard("Backup & restore", "Save or restore your local data", Icons.Default.Backup) { page = "Backup & restore" }
                            DestinationCard("Device tools", "Pairing, intercom, firmware and diagnostics", Icons.Default.Build) { page = "Device tools" }
                            Text("RykerConnect · Local companion\nNo account required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Column {
                        PageBack(page!!.substringBefore('/')) { page = null }
                        ScrollPage(null, null) {
                            when (page) {
                                "Add fuel", "Add service", "Add mileage" -> GarageEntry(page!!) { val old=page;page=null;old?.let{pages.removeState(it)} }
                                "Vehicle profile" -> VehicleProfileScreen()
                                "Service reminders" -> ServiceReminderSettings { page = it }
                                "Widgets" -> WidgetSettings()
                                "Maintenance planner" -> MaintenancePlannerScreen { page=it }
                                "Add-ons" -> DadRidesSettings()
                                "Ride summaries" -> RideSummarySettings()
                                "Services & intervals" -> ServiceSettings()
                                "Connect dashboard" -> DashboardTools()
                                "Connection health" -> ConnectionHealthTools(store) { page = it }
                                "Guided demo" -> GuidedDemo()
                                "Theme" -> de.chaostheorybot.rykerconnect.ui.theme.ThemePicker()
                                "Last Parked" -> ParkingTools()
                                "Statistics" -> StatisticsTools()
                                "Offline maps" -> OfflineTools()
                                "Display & profiles" -> DisplayTools()
                                "Backup & restore" -> BackupTools()
                                "Weather" -> { val now = rememberClock(); WeatherCard(now) }
                                "Sensor" -> EnvironmentCard()
                                else -> if(page!!.startsWith("Summary/")) RideSummaryScreen(page!!.substringAfter("/")) { page=it } else if(page!!.startsWith("Journal/")) RideJournalScreen(page!!.substringAfter("/")) else if(page!!.startsWith("Complete plan/") || page!!.startsWith("Edit ") || page!!.startsWith("Repeat ") || page!!.startsWith("Log service/")) GarageEntry(page!!) { val old=page;page=null;old?.let{pages.removeState(it)} }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable internal fun PageBack(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable internal fun ScrollPage(title: String?, eyebrow: String?, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (title != null) Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable internal fun DestinationCard(title: String, subtitle: String, icon: ImageVector, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun rememberClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return now
}

@Composable private fun ConnectOverview(associated: Boolean, pair: () -> Unit, forget: () -> Unit, dashboard: Boolean = false, open: (String) -> Unit) {
    val context = LocalContext.current
    val revision by SoftwareStore.revision.collectAsState()
    val cardOrder=remember(revision){DashboardLayout.order(SoftwareStore.value("dashboardOrder"))}
    val report by ConnectionHealth.report.collectAsState()
    val trip by TripStore.summary.collectAsState()
    val units by RideState.preferences.collectAsState()
    val nav by RideState.navigation.collectAsState()
    val title by RykerConnectApplication.music.track.collectAsState()
    val artist by RykerConnectApplication.music.artist.collectAsState()
    val weather by WeatherState.view.collectAsState()
    val weatherEnabled by WeatherState.enabled.collectAsState()
    val now = rememberClock()
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val vehicle = remember(resources, configuration, report.status == "Connected") {
        resources.getDrawable(if (report.status == "Connected") R.drawable.rykeranim_on else R.drawable.rykeranim_off, context.theme) as AnimationDrawable
    }
    DisposableEffect(vehicle) { vehicle.start(); onDispose { vehicle.stop() } }
    var mapsFeedback by remember { mutableStateOf("") }
    ScrollPage(if(dashboard) "Dashboard" else "Connect", "RYKER / COMPANION") {
        if(dashboard){LatestRideCard(open); DestinationCard("Maintenance planner", "Upcoming work, checklists and parts to buy", Icons.Default.Build){open("Maintenance planner")}}
        if(!dashboard) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { Text("RykerConnect", style = MaterialTheme.typography.headlineSmall); Text(if (associated) "Your paired main unit" else "Pair your main unit to get started", style = MaterialTheme.typography.bodySmall) }
                    Image(rememberDrawablePainter(vehicle), contentDescription = "Ryker main unit ${if (report.status == "Connected") "connected" else "disconnected"}", modifier = Modifier.width(112.dp).height(86.dp))
                }
                ConnectionPanel(associated, pair, forget, showDiagnostics = false)
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={open("Connection health")}){Text("Connection health")}
            TextButton(onClick={open("Guided demo")}){Text("Guided demo")}
        }
        }
        if(dashboard) {
        DashboardQuickActions(open)
        Text("Main unit · ${report.status}", style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth>=680.dp && configuration.fontScale<=1.3f)2 else 1
            val width=(maxWidth-16.dp*(columns-1))/columns
            FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                for(name in cardOrder.filter{DashboardLayout.visible(it)}) key(name) {
                    Column(Modifier.width(width-1.dp)) {
                        val collapsed=SoftwareStore.value("collapse$name")=="true"
                        TextButton(onClick={runCatching{SoftwareStore.set("collapse$name",(!collapsed).toString())}.onFailure{mapsFeedback="Could not save card state"}}){Text("$name · ${if(collapsed)"Expand" else "Collapse"}")}
                        if(!collapsed) when(name) {
                            "Ride" -> { ToolCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (trip.recording) "CURRENT RIDE" else "RIDE STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (trip.recording) "Recording" else "Not recording", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) { Text(RideUnits.distance(trip.meters, units.imperial), style = MaterialTheme.typography.headlineMedium); Text("Distance", style = MaterialTheme.typography.bodySmall) }
                Column(Modifier.weight(1f)) { Text(trip.durationText(), style = MaterialTheme.typography.headlineMedium); Text("Elapsed time", style = MaterialTheme.typography.bodySmall) }
            }
            Text(trip.gps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { open("Recording") }) { Text("Recording controls") }
        } }
                            "Music" -> { ToolCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary); Text("Music", style = MaterialTheme.typography.titleMedium) }
            Text(title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist.ifBlank { "Play music on your phone to see it here." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
                            "Navigation" -> { ToolCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Navigation, null, tint = MaterialTheme.colorScheme.primary); Text("Navigation", style = MaterialTheme.typography.titleMedium) }
            Text(if (nav.active) nav.instruction else "Where to next?", style = MaterialTheme.typography.titleLarge)
            Text(if (nav.active) { if (nav.stale(now)) "Directions stale · check Google Maps" else nav.arrival.ifBlank { nav.source } } else "Start a route in Google Maps to see directions here.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                if (intent == null) mapsFeedback = "Google Maps is not installed." else context.startActivity(intent)
            }) { Text("Open Google Maps") }
            if (mapsFeedback.isNotBlank()) Text(mapsFeedback)
        } }
                            "Weather" -> { DestinationCard("Weather", if (!weatherEnabled) "GPS weather is off" else weather.report?.let { "${it.temperature(units.fahrenheit)} · ${it.conditions()}${if (weather.stale) " · Stale" else ""}" } ?: weather.status, Icons.Default.Cloud) { open("Weather") } }
                            "Sensor" -> { EnvironmentCard() }
                        }
                    }
                }
            }
        }
        ServiceSummary()
        }
        if (report.status != "Connected") Text("Live device information becomes available when the main unit connects.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
