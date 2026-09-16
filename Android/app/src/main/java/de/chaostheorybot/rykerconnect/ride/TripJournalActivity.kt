package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TripJournalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RykerConnectTheme { Surface(Modifier.fillMaxSize().systemBarsPadding()) { TripJournal { finish() } } } }
    }
}

private fun date(ms: Long) = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))

@Composable fun AutoRecordingControls() {
    val context = LocalContext.current
    val enabled by AutoRide.enabled.collectAsState()
    val status by AutoRide.status.collectAsState()
    var permissionFeedback by remember { mutableStateOf("") }
    var backgroundAllowed by remember { mutableStateOf(AutoRide.hasBackgroundLocation(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) backgroundAllowed = AutoRide.hasBackgroundLocation(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionFeedback = if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) "For automatic starts while locked, open location settings and select Allow all the time." else "Precise location is required. You can enable it in app settings."
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Record when Ryker connects", Modifier.weight(1f)); Switch(enabled, { AutoRide.setEnabled(it) })
    }
    Text(status, style = MaterialTheme.typography.bodyMedium)
    Text("A two-minute disconnect grace period keeps brief dropouts in one trip. Stop recording ends this session immediately.", style = MaterialTheme.typography.bodySmall)
    if (enabled && !backgroundAllowed) {
        Text("Automatic starts with the app closed need precise location and Allow all the time. Routes stay on this phone.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Set up automatic recording") }
    }
    if (permissionFeedback.isNotBlank()) Text(permissionFeedback, style = MaterialTheme.typography.bodySmall)
}

@Composable internal fun TripJournal(embedded: Boolean = false, openTool: (String) -> Unit = {}, close: () -> Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val history by TripStore.history.collectAsState();val current by TripStore.summary.collectAsState()
    val error by TripStore.storageError.collectAsState();val units by RideState.preferences.collectAsState()
    val revision by SoftwareStore.revision.collectAsState()
    var demos by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") };var favoriteOnly by rememberSaveable{mutableStateOf(false)}
    var sessions by rememberSaveable { mutableStateOf(false) }
    var recentOnly by rememberSaveable{mutableStateOf(false)};var picked by rememberSaveable{mutableStateOf(listOf<String>())}
    var manage by rememberSaveable { mutableStateOf(false) }
    var message by remember{mutableStateOf("")};var merging by remember{mutableStateOf(false)}
    val filtered=remember(history,query,favoriteOnly,recentOnly,revision,demos,sessions){(if(demos)DemoTrips.details.map{it.summary} else history).filter{
        (demos || sessions || !it.stationarySession) &&
        (query.isBlank() || (it.title+" "+it.notes+" "+date(it.started)).contains(query,true)) &&
        (demos || !favoriteOnly || SoftwareStore.favorite(it.id)) && (demos || !recentOnly || it.started>=System.currentTimeMillis()-30L*86400000)
    }}
    BackHandler(selected != null) { selected = null }
    Column {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if(embedded && selected==null)Text("EVERY RIDE, REMEMBERED",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text(if(selected==null)"My Trips" else "Trip details",style=if(embedded && selected==null)MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall)
            }
            if (!embedded || selected != null) TextButton(onClick={if(selected==null)close() else selected=null}){Text(if(selected==null)"Close" else "Back")}
        }
        if(embedded) Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            val spot=remember(revision){SoftwareStore.snapshot().optJSONObject("parking")}
            FilledTonalButton(onClick={openTool("Last Parked")},modifier=Modifier.fillMaxWidth()) {
                Icon(androidx.compose.material.icons.Icons.Default.LocalParking,null)
                Spacer(Modifier.width(8.dp))
                Text("Last Parked · ${if(spot==null)"No location yet" else "View saved location"}")
            }
            if(selected==null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!demos,{demos=false;query=""},{Text("My rides")})
                FilterChip(demos,{demos=true;query="";manage=false;picked=emptyList()},{Text("Demo rides")})
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth>=720.dp && androidx.compose.ui.platform.LocalConfiguration.current.fontScale <= 1.3f
            Row(Modifier.fillMaxSize()) {
                if(wide || selected==null) LazyColumn(Modifier.weight(if(wide)0.4f else 1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if (embedded) item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={openTool("Statistics")}) { Text("Statistics") }
                            TextButton(onClick={openTool("Offline maps")}) { Text("Offline maps") }
                        }
                    }
                    if(demos) item { Text("DEMO RIDES · Synthetic routes with stops and speed data. Read-only; excluded from your history totals and parking.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) }
                    if(!demos) item { ToolCard {
                        if (!embedded) AutoRecordingControls() else {
                            Text(if(current.recording) "Recording your ride" else "Your ride journal", style=MaterialTheme.typography.titleMedium)
                            Text("${history.count{!it.stationarySession}} rides · ${history.count{it.stationarySession}} stationary sessions · Local only", style=MaterialTheme.typography.bodySmall)
                        }
                        if(current.recording) Text("${RideUnits.distance(current.meters,units.imperial)} · ${current.durationText()}",style=MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(embedded) TextButton(onClick={openTool("Recording")}) { Text("Recording controls") }
                            if(current.recording) OutlinedButton(onClick={context.startService(Intent(context,TripRecordingService::class.java).setAction("STOP"))}){Text("Stop & save")}
                        }
                        if (!embedded) OutlinedButton(onClick={context.startActivity(Intent(context,RideToolsActivity::class.java))}){Text("Ride tools and backup")}
                    } }
                    item {
                        OutlinedTextField(query,{query=it},label={Text("Search name, notes or date")},modifier=Modifier.fillMaxWidth())
                        if(!demos) Row {FilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},{Text("Favorites")});Spacer(Modifier.width(8.dp));FilterChip(recentOnly,{recentOnly=!recentOnly},{Text("Last 30 days")})}
                        if(!demos) FilterChip(sessions,{sessions=!sessions},{Text("Include stationary sessions")})
                        if(!demos) TextButton(onClick={manage=!manage; if(!manage)picked=emptyList()}) { Text(if(manage) "Done selecting" else "Select trips to merge") }
                        if(manage) {
                        Text("Select two or more trips to merge into a new copy.",style=MaterialTheme.typography.bodySmall)
                        Button(enabled=picked.size>=2&&!merging,onClick={scope.launch{merging=true;runCatching{withContext(Dispatchers.IO){TripStore.mergedCopy(picked.toSet())}}.onSuccess{selected=it;picked=emptyList();message="Merged copy saved; originals retained"}.onFailure{message=it.message?:"Merge failed"};merging=false}}){Text("Merge selected (${picked.size})")}
                        }
                        if(message.isNotBlank())Text(message)
                        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                        if(filtered.isEmpty())Text(if(history.isEmpty())"No saved trips yet" else "No rides match these filters. Turn on Include stationary sessions to see connection-only records.")
                    }
                    items(filtered,key={it.id}){trip -> Card(Modifier.fillMaxWidth().clickable{selected=trip.id}){
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){if(manage) Checkbox(trip.id in picked,{checked->picked=if(checked)picked+trip.id else picked-trip.id});Text(trip.title.ifBlank{date(trip.started)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)}
                            if(trip.title.isNotBlank())Text(date(trip.started),style=MaterialTheme.typography.bodySmall)
                            Row {RouteSketch(trip.preview,Modifier.width(100.dp).height(64.dp));Column{Text(RideUnits.distance(trip.meters,units.imperial));Text(trip.durationText());Text(if(trip.derived)"Edited copy" else "${trip.points} GPS points",style=MaterialTheme.typography.bodySmall)}}
                            if(!demos) TextButton(onClick={runCatching{SoftwareStore.favorite(trip.id,!SoftwareStore.favorite(trip.id))}.onFailure{message="Could not update favorite"}}){Text(if(SoftwareStore.favorite(trip.id))"★ Favorite" else "☆ Add favorite")}
                            if(trip.stationarySession) Text("Stationary connection session",style=MaterialTheme.typography.bodySmall)
                            if(trip.interrupted) Text("Interrupted recording · recovered points retained",style=MaterialTheme.typography.bodySmall)
                            Text("View map and trip data",color=MaterialTheme.colorScheme.primary)
                        }
                    }}
                }
                if(selected!=null) Box(Modifier.weight(if(wide)0.6f else 1f).fillMaxHeight()){key(selected){TripDetails(selected!!)}}
                else if(wide)Box(Modifier.weight(0.6f).padding(24.dp)){Text("Select a trip to view its map and details")}
            }
        }
    }
}

@Composable private fun TripDetails(id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val units by RideState.preferences.collectAsState()
    var detail by remember { mutableStateOf<TripDetail?>(null) }
    var feedback by remember { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var draftLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) {
        runCatching { withContext(Dispatchers.IO) { DemoTrips.find(id) ?: TripStore.detail(id) } }.onSuccess { detail = it; if (!draftLoaded) { title = it.summary.title; notes = it.summary.notes; draftLoaded = true } }.onFailure { feedback = "Could not read this trip; its file has been retained." }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) scope.launch {
            feedback = withContext(Dispatchers.IO) { runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Destination unavailable")
                stream.bufferedWriter().use { it.write(TripStore.gpx(id)) }; "GPX exported"
            }.getOrElse { "Export failed: ${it.javaClass.simpleName}" } }
        }
    }
    var replayPoint by remember(id) { mutableStateOf<TrackPoint?>(null) }
    val demo=id.startsWith("demo-")
    val d = detail
    if (d == null) { Text(feedback.ifBlank { "Loading trip…" }, Modifier.padding(16.dp)); return }
    fun speed(value: Double?) = value?.let { java.lang.String.format(java.util.Locale.US, "%.1f %s", it * if (units.imperial) 2.236936 else 3.6, if (units.imperial) "mph" else "km/h") } ?: "Unavailable"
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(demo) Text("DEMO · Synthetic ride",color=MaterialTheme.colorScheme.primary)
        Text(title.ifBlank { date(d.summary.started) }, style = MaterialTheme.typography.titleLarge)
        if (d.track.isEmpty()) Text("No usable GPS points were recorded. This connection session has no route to display.") else { TripMap(d.track, replayPoint); TripReplay(d) { replayPoint = it }; if(!demo) TripTrim(d) }
        Text("Start · ${date(d.summary.started)}\nEnd · ${date(d.summary.ended)}")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Distance · ${RideUnits.distance(d.stats.meters, units.imperial)}", style = MaterialTheme.typography.titleMedium)
            Text("Elapsed time · ${d.summary.durationText()}")
            Text("Moving time · ${if (d.summary.modern && d.stats.maxMps != null) TripSummary.duration(d.stats.movingMs) else "Unavailable"}")
            Text("Stopped time · ${if (d.summary.modern && d.stats.maxMps != null) TripSummary.duration(d.stats.stoppedMs) else "Unavailable"}")
            Text("Average speed (whole trip) · ${speed(if (d.summary.ended > d.summary.started && d.track.size > 1) d.stats.meters / ((d.summary.ended - d.summary.started) / 1000.0) else null)}")
            Text("Maximum GPS speed · ${speed(d.stats.maxMps)}")
            Text("${d.track.size} GPS points · ${d.stats.gaps} route gaps")
            if (d.summary.modern) Text("Unclassified time · ${TripSummary.duration(d.stats.unknownMs)}", style = MaterialTheme.typography.bodySmall)
            else Text("This older trip did not record speed samples. Moving/stopped time and maximum speed are unavailable.", style = MaterialTheme.typography.bodySmall)
            Text("GPS estimates: movement requires at least 1 m/s. Missing or inaccurate fixes are not treated as stops; route gaps are not joined.", style = MaterialTheme.typography.bodySmall)
            if (d.summary.interrupted) Text("Recording was interrupted; only recovered points are shown.")
        } }
        TripCharts(d)
        if(demo) { Text(d.summary.notes); return@Column }
        Button(onClick={context.startActivity(Intent(context,RideActionActivity::class.java).putExtra("page","Summary/$id"))}){Text("Ride summary & photos")}
        OutlinedTextField(title, { title = it.take(100) }, label = { Text("Trip name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = { scope.launch { saving = true; feedback = withContext(Dispatchers.IO) { runCatching { TripStore.saveMetadata(id, title, notes); "Name and notes saved" }.getOrElse { "Could not save changes" } }; saving = false } }) { Text("Save name and notes") }
        OutlinedButton(enabled = d.track.isNotEmpty(), onClick = { export.launch("RykerConnect-${d.summary.started}.gpx") }) { Text("Export GPX") }
        if (feedback.isNotBlank()) Text(feedback)
    }
}

@Composable private fun RouteSketch(points: List<TrackPoint>, modifier: Modifier) {
    val routeColor = MaterialTheme.colorScheme.primary
    val segments = remember(points) { TripAnalysis.segments(points) }
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val minLat = points.minOf { it.lat }; val minLon = points.minOf { it.lon }
        val scaleX = kotlin.math.cos(Math.toRadians(points.first().lat))
        val dx = (points.maxOf { it.lon } - minLon) * scaleX; val dy = points.maxOf { it.lat } - minLat
        val scale = minOf((size.width - 16) / dx.coerceAtLeast(.000001), (size.height - 16) / dy.coerceAtLeast(.000001))
        fun position(p: TrackPoint) = Offset(((size.width - dx * scale) / 2 + (p.lon - minLon) * scaleX * scale).toFloat(), ((size.height + dy * scale) / 2 - (p.lat - minLat) * scale).toFloat())
        for (segment in segments) {
            val path = Path(); segment.forEachIndexed { i, p -> val at = position(p); if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y) }
            drawPath(path, routeColor, style = Stroke(3.dp.toPx()))
        }
        drawCircle(Color(0xff18834a), 4.dp.toPx(), position(points.first()))
        drawCircle(Color(0xffc33b35), 4.dp.toPx(), position(points.last()))
    }
}

@Composable internal fun TripMap(points: List<TrackPoint>, replay: TrackPoint? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val view = remember { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }
    DisposableEffect(view, lifecycle) {
        view.onStart(); view.onResume()
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); else -> {}
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    LaunchedEffect(view) { delay(15_000); if (!loaded) failed = true }
    AndroidView(factory = {
        view.apply {
            addOnDidFailLoadingMapListener { failed = true }
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            getMapAsync { map ->
                map.setStyle(RIDE_MAP_STYLE) { style ->
                    loaded = true
                    style.addSource(GeoJsonSource("replay", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                    style.addLayer(CircleLayer("replay-marker", "replay").withProperties(circleColor("#e39114"), circleRadius(8f), circleStrokeColor("#ffffff"), circleStrokeWidth(2f)))
                    val lines = JSONArray()
                    for (segment in TripAnalysis.segments(points).filter { it.size > 1 }) lines.put(JSONArray().apply { segment.forEach { put(JSONArray().put(it.lon).put(it.lat)) } })
                    style.addSource(GeoJsonSource("trip-route", JSONObject().put("type", "MultiLineString").put("coordinates", lines).toString()))
                    style.addLayer(LineLayer("trip-route-line", "trip-route").withProperties(lineColor("#315fe8"), lineWidth(5f)))
                    for ((name, point, color) in listOf(Triple("start", points.first(), "#18834a"), Triple("end", points.last(), "#c33b35"))) {
                        style.addSource(GeoJsonSource(name, JSONObject().put("type", "Point").put("coordinates", JSONArray().put(point.lon).put(point.lat)).toString()))
                        style.addLayer(CircleLayer("$name-marker", name).withProperties(circleColor(color), circleRadius(7f), circleStrokeColor("#ffffff"), circleStrokeWidth(2f)))
                    }
                    val stops=TripInsights.stops(points)
                    if(stops.isNotEmpty()) {
                        val coordinates=JSONArray().apply{stops.forEach{put(JSONArray().put(it.point.lon).put(it.point.lat))}}
                        style.addSource(GeoJsonSource("stops",JSONObject().put("type","MultiPoint").put("coordinates",coordinates).toString()))
                        style.addLayer(CircleLayer("stop-markers","stops").withProperties(circleColor("#d99010"),circleRadius(6f),circleStrokeColor("#ffffff"),circleStrokeWidth(2f)))
                    }
                    view.post {
                        val coordinates = points.map { LatLng(it.lat, it.lon) }
                        if (coordinates.distinct().size > 1) map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coordinates).build(), 48))
                        else map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 15.0))
                    }
                }
            }
        }
    }, update = { mapView -> mapView.getMapAsync { map -> map.style?.getSourceAs<GeoJsonSource>("replay")?.setGeoJson(if (replay == null) "{\"type\":\"FeatureCollection\",\"features\":[]}" else JSONObject().put("type","Point").put("coordinates",JSONArray().put(replay.lon).put(replay.lat)).toString()) } }, modifier = Modifier.fillMaxWidth().height(320.dp))
    Text(if (points.size == 1) "Saved location · Pinch to zoom" else "Green: start · Red: finish · Amber: stops · Pinch to zoom", style = MaterialTheme.typography.bodySmall)
    if (!loaded && !failed) Text("Loading free map…", style = MaterialTheme.typography.bodySmall)
    if (failed) {
        Text("Map background unavailable. Your saved route and statistics are still available below.")
        RouteSketch(points, Modifier.fillMaxWidth().height(160.dp))
    }
    Text("MapLibre · OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
}
