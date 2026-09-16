package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import org.json.JSONObject

const val RIDE_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable fun OfflineTools() {
    val context=LocalContext.current
    val manager=remember { MapLibre.getInstance(context); OfflineManager.getInstance(context) }
    val trips by TripStore.history.collectAsState()
    var regions by remember { mutableStateOf<List<OfflineRegion>>(emptyList()) }
    val messages=remember { mutableStateMapOf<Long,String>() }
    var feedback by remember { mutableStateOf("") };var creating by remember{mutableStateOf(false)}
    fun attach(region: OfflineRegion) {
        fun status(s:OfflineRegionStatus) {
            messages[region.id]="${if(s.isComplete)"Ready offline" else if(s.downloadState==OfflineRegion.STATE_ACTIVE)"Downloading" else "Paused"} · ${s.completedResourceCount}/${if(s.isRequiredResourceCountPrecise)s.requiredResourceCount.toString() else "?"} resources · ${s.completedResourceSize/1_000_000} MB"
            if(s.isComplete && s.downloadState==OfflineRegion.STATE_ACTIVE)region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            if(s.completedResourceSize>300_000_000){region.setDownloadState(OfflineRegion.STATE_INACTIVE);messages[region.id]="Paused: 300 MB limit reached. Choose a smaller area."}
        }
        region.setObserver(object:OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(s:OfflineRegionStatus){status(s)}
            override fun onError(error:OfflineRegionError){messages[region.id]="Download interrupted: ${error.message}. Resume when online."}
            override fun mapboxTileCountLimitExceeded(limit:Long){region.setDownloadState(OfflineRegion.STATE_INACTIVE);messages[region.id]="Download tile limit reached"}
        })
        region.getStatus(object:OfflineRegion.OfflineRegionStatusCallback {override fun onStatus(s:OfflineRegionStatus?){s?.let{status(it)}};override fun onError(error:String?){messages[region.id]=error?:"Status unavailable"}})
    }
    fun reload() {manager.listOfflineRegions(object:OfflineManager.ListOfflineRegionsCallback {
        override fun onList(items:Array<OfflineRegion>?){regions=items.orEmpty().toList();regions.forEach{attach(it)}}
        override fun onError(error:String){feedback="Cannot read offline packs: $error"}
    })}
    LaunchedEffect(Unit){reload()}
    DisposableEffect(Unit){onDispose{regions.forEach{it.setObserver(null);it.setDownloadState(OfflineRegion.STATE_INACTIVE)}}}
    Text("Offline trip maps",style=MaterialTheme.typography.titleLarge)
    Text("Download the area around a saved trip for viewing without internet. Includes the map background and labels at zoom levels 0–14. This does not download Google Maps navigation.")
    Text("Keep this page open while downloading. Leaving pauses downloads; return to resume. Use Wi-Fi. A pack pauses at 300 MB and each trip must fit within 1° latitude × 2° longitude.",style=MaterialTheme.typography.bodySmall)
    for(region in regions) ToolCard {
        Text(runCatching{JSONObject(String(region.metadata,Charsets.UTF_8)).optString("name")}.getOrDefault("Offline map"))
        Text(messages[region.id]?:"Checking pack…")
        Row { TextButton(onClick={region.setDownloadState(OfflineRegion.STATE_ACTIVE);attach(region)}){Text("Resume")}; TextButton(onClick={region.setDownloadState(OfflineRegion.STATE_INACTIVE);attach(region)}){Text("Pause")} }
        ConfirmDelete("Delete map pack") {region.setDownloadState(OfflineRegion.STATE_INACTIVE);region.delete(object:OfflineRegion.OfflineRegionDeleteCallback {override fun onDelete(){reload()};override fun onError(error:String){feedback=error}})}
    }
    Text("Choose a trip area",style=MaterialTheme.typography.titleMedium)
    if(trips.none{it.preview.isNotEmpty()})Text("Save a ride with GPS points first.")
    for(trip in trips.filter{it.preview.isNotEmpty()}) OutlinedButton(enabled=!creating,onClick={
        runCatching {
            val points=trip.preview;val south=points.minOf{it.lat}-.02;val north=points.maxOf{it.lat}+.02;val west=points.minOf{it.lon}-.02;val east=points.maxOf{it.lon}+.02
            require(north-south<=1 && east-west<=2 && south>=-85 && north<=85 && west>=-180 && east<=180){"Area too large; trim a shorter trip copy for downloading"}
            require(regions.size<20){"Delete a pack before adding more (limit 20)"}
            val bounds=LatLngBounds.Builder().include(LatLng(south,west)).include(LatLng(north,east)).build()
            val definition=OfflineTilePyramidRegionDefinition(RIDE_MAP_STYLE,bounds,0.0,14.0,1f)
            val meta=JSONObject().put("name",trip.title.ifBlank{toolDate(trip.started)}).put("trip",trip.id).toString().toByteArray()
            creating=true
            manager.createOfflineRegion(definition,meta,object:OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region:OfflineRegion){creating=false;regions=regions+region;attach(region);region.setDownloadState(OfflineRegion.STATE_ACTIVE);feedback="Downloading selected trip area"}
                override fun onError(error:String){creating=false;feedback="Cannot create pack: $error"}
            })
        }.onFailure{feedback=it.message?:"Could not start download"}
    }){Text("Download: "+trip.title.ifBlank{toolDate(trip.started)})}
    Text(feedback)
    Text("MapLibre · OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors",style=MaterialTheme.typography.bodySmall)
}
