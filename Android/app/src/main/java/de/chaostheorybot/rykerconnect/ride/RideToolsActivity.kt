package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RideToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { RykerConnectTheme { Surface(Modifier.fillMaxSize().systemBarsPadding()) { RideTools { finish() } } } }
    }
}
internal fun toolDate(time: Long) = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
@Composable private fun RideTools(close: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("Backup") }
    Column {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Ride tools", style = MaterialTheme.typography.headlineSmall); TextButton(onClick=close) { Text("Close") } }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for (name in listOf("Backup", "Display", "Statistics", "Parked", "Garage", "Offline maps")) FilterChip(page==name,{page=name},{Text(name)})
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(page) { "Backup" -> BackupTools(); "Display" -> DisplayTools(); "Statistics" -> StatisticsTools(); "Parked" -> ParkingTools(); "Garage" -> GarageTools(); "Offline maps" -> OfflineTools() }
        }
    }
}

@Composable internal fun BackupTools() {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }; var settings by remember { mutableStateOf(false) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if(uri!=null) scope.launch {
        busy=true
        message=withContext(Dispatchers.IO) { runCatching {
            val trips=TripStore.backupFiles(); val state=SoftwareStore.snapshot(); val prefs=SoftwareStore.preferences().put("app",BackupPreferences.capture(context).put("dynamicColor",de.chaostheorybot.rykerconnect.data.RykerConnectStore(context).getDynamicColorToken.first()))
            val out=context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
            RideBackup.write(out,trips,prefs,state,RidePhotos.backupAssets()); context.getSharedPreferences("backup_status",android.content.Context.MODE_PRIVATE).edit().putLong("last",System.currentTimeMillis()).apply(); "Backup saved: ${trips.keys.count { it.endsWith(".jsonl") }} completed trips."
        }.getOrElse { "Backup failed: ${it.message}. Discard any incomplete export." } }; busy=false
    } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
        busy=true; settings=false
        runCatching { withContext(Dispatchers.IO) { RideBackup.read(context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")) } }
            .onSuccess { preview=it; message="Archive validated" }.onFailure { message="Backup rejected: ${it.message}" }
        busy=false
    } }
    Text("Backup and restore",style=MaterialTheme.typography.titleLarge)
    SharedLibrarySettings()
    BackupReminderSettings()
    Text("Includes ride photos, maintenance plans, appearance/dashboard settings, receipt photos, completed trips, favorites, profiles, fuel/maintenance records and riding preferences. An active recording is excluded. Pairing, permissions, weather cache and downloaded map tiles are not included.")
    Text("The ZIP contains location history. Save it somewhere you trust; no cloud upload is performed by RykerConnect.",style=MaterialTheme.typography.bodySmall)
    Button(enabled=!busy,onClick={export.launch("RykerConnect-backup-${System.currentTimeMillis()}.zip")}) { Text("Save backup") }
    OutlinedButton(enabled=!busy,onClick={import.launch(arrayOf("application/zip","application/octet-stream"))}) { Text("Choose backup to restore") }
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(message)
    preview?.let { archive -> AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text("Restore reviewed backup?")},text={Column {
        Text("${archive.trips.keys.count { it.endsWith(".jsonl") }} trip files. Existing trip IDs and tool records are kept; missing records are added. This does not delete your current data.")
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(settings,{settings=it}); Text("Also apply riding, appearance and dashboard preferences") }
    }},confirmButton={TextButton(enabled=!busy,onClick={scope.launch {
        busy=true
        message=withContext(Dispatchers.IO) { runCatching {
            SoftwareStore.merge(archive.software) // Validate merge before any trip writes.
            RidePhotos.restoreAssets(archive.assets); val added=TripStore.restoreFiles(archive.trips); SoftwareStore.restore(archive.software)
            if(settings){archive.settings.optJSONObject("app")?.let{BackupPreferences.apply(context,it);if(it.has("dynamicColor"))de.chaostheorybot.rykerconnect.data.RykerConnectStore(context).saveDynamicColor(it.getBoolean("dynamicColor"))};SoftwareStore.applyPreferences(archive.settings); val field=archive.software.optString("drivingField"); if(field in listOf("distance_time","distance","time","gps"))SoftwareStore.set("drivingField",field)}
            "Restore complete: $added trips added; existing trips retained."
        }.getOrElse { "Restore did not finish: ${it.message}. Existing trips retained; retrying this archive is safe." } }
        busy=false;preview=null
    }}) {Text("Restore")}},dismissButton={TextButton(enabled=!busy,onClick={preview=null}){Text("Cancel")}}) }
}

@Composable internal fun DisplayTools() {
    val revision by SoftwareStore.revision.collectAsState(); val p by RideState.preferences.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    val profiles=remember(revision){SoftwareStore.records("profiles")}
    val choices=listOf("distance_time" to "Distance + elapsed time","time" to "Elapsed time","distance" to "Distance","gps" to "GPS status")
    val field=remember(revision){SoftwareStore.value("drivingField").ifBlank{"distance_time"}}
    Text("Driving information",style=MaterialTheme.typography.titleLarge)
    for((key,label) in choices) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { RadioButton(field==key,{ runCatching {SoftwareStore.set("drivingField",key)}.onFailure{message="Could not save selection"} });Text(label) }
    Text("Applies to the driving panel's trip strip. Navigation remains visible.")
    Text("Saved profiles",style=MaterialTheme.typography.titleLarge)
    Text("A profile saves units, text size, panel order, navigation/notification preferences and the driving field. It does not change pairing or permissions.")
    OutlinedTextField(name,{name=it.take(60)},label={Text("Profile name")},modifier=Modifier.fillMaxWidth())
    Button(enabled=name.isNotBlank(),onClick={runCatching {SoftwareStore.add("profiles",JSONObject().put("name",name.trim()).put("preferences",SoftwareStore.preferences(p)).put("drivingField",field)); name="";message="Profile saved"}.onFailure{message="Could not save profile"}}){Text("Save current settings")}
    for(profile in profiles) ToolCard {
        Text(profile.getString("name"),style=MaterialTheme.typography.titleMedium)
        Button(onClick={runCatching {SoftwareStore.applyPreferences(profile.getJSONObject("preferences"));SoftwareStore.set("drivingField",profile.optString("drivingField","distance_time"));message="Profile applied"}.onFailure{message="Profile could not be applied"}}){Text("Apply profile")}
        ConfirmDelete("Delete profile") {SoftwareStore.remove("profiles",profile.getString("id"))}
    }
    Text(message)
}

@Composable internal fun StatisticsTools() {
    val trips by TripStore.history.collectAsState();val units by RideState.preferences.collectAsState()
    val original=trips.filter{!it.derived && !it.stationarySession}
    Text("Ride statistics",style=MaterialTheme.typography.titleLarge)
    Text("${original.size} trips · ${RideUnits.distance(original.sumOf{it.meters},units.imperial)}",style=MaterialTheme.typography.headlineSmall)
    Text("Total elapsed: ${TripSummary.duration(original.sumOf{(it.ended-it.started).coerceAtLeast(0)})}")
    Text("Edited copies and stationary automatic sessions are excluded from riding totals. GPS distances are estimates; missing historical speed is not treated as zero.",style=MaterialTheme.typography.bodySmall)
    val groups=original.groupBy {Instant.ofEpochMilli(it.started).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)}.toSortedMap(compareByDescending{it})
    for((month,rides) in groups) ToolCard {Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),style=MaterialTheme.typography.titleMedium);Text("${rides.size} trips · ${RideUnits.distance(rides.sumOf{it.meters},units.imperial)} · ${TripSummary.duration(rides.sumOf{(it.ended-it.started).coerceAtLeast(0)})}")}
    if(original.isEmpty())Text("Statistics will appear after your first saved ride.")
}

@Composable internal fun ParkingTools() {
    val context=LocalContext.current;val revision by SoftwareStore.revision.collectAsState()
    val spot=remember(revision){SoftwareStore.snapshot().optJSONObject("parking")}
    Text("Last parked location",style=MaterialTheme.typography.titleLarge)
    Text("Automatic candidates require 30 seconds stopped before a disconnect lasting two minutes. Confirm the pin or save your current location.")
    var parkingMessage by remember { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick={manual=!manual}){Text(if(manual) "Cancel current location" else "Save current parking location")}
    if(manual) {
        val fix=EntryLocation(true)
        Button(enabled=fix!=null,onClick={runCatching{ParkingState.saveManual(fix!!)}.onSuccess{manual=false;parkingMessage="Parking saved"}.onFailure{parkingMessage=it.message?:"Unable to save parking"}}){Text("Save parking here")}
    }
    if(parkingMessage.isNotBlank())Text(parkingMessage)
    if(spot==null)Text("No recent disconnect location saved yet.") else {
        Text(if(spot.optBoolean("confirmed")) "Confirmed parking" else "Parking candidate · not yet confirmed",color=MaterialTheme.colorScheme.primary)
        if(!spot.optBoolean("confirmed")) TextButton(onClick={runCatching{ParkingState.confirm()}.onFailure{parkingMessage="Could not confirm parking"}}){Text("Confirm parked here")}
        Text("Saved ${toolDate(spot.getLong("time"))} · accuracy ±${spot.getDouble("accuracy").toInt()} m")
        Text("GPS fix: ${toolDate(spot.optLong("fixTime",spot.getLong("time")))}",style=MaterialTheme.typography.bodySmall)
        val lat=spot.getDouble("lat");val lon=spot.getDouble("lon")
        TripMap(listOf(TrackPoint(lat,lon,spot.getLong("time"),spot.getDouble("accuracy").toFloat())))
        Button(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=walking")))}){Text("Walk back with Google Maps")}
    }
}

@Composable internal fun ToolCard(content:@Composable ColumnScope.()->Unit) {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable internal fun ConfirmDelete(label:String, action:()->Unit) {
    var show by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    TextButton(onClick={show=true}){Text(label)}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text(label+"?")},text={Text("This removes this local record. A previously saved backup can restore it.")},confirmButton={TextButton(onClick={runCatching(action).onFailure{error="Could not delete record"};show=false}){Text("Delete")}},dismissButton={TextButton(onClick={show=false}){Text("Cancel")}})
    if(error.isNotBlank())Text(error)
}
