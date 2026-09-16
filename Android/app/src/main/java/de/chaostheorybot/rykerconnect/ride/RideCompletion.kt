package de.chaostheorybot.rykerconnect.ride

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.chaostheorybot.rykerconnect.R
import kotlinx.coroutines.*
import java.util.Locale

object RideCompletion {
    fun eligible(t:TripSummary)=!t.recording && !t.derived && !t.stationarySession && t.ended>=t.started && t.id.isNotBlank()
    fun movingAverage(points:List<TrackPoint>):Double? {
        var weighted=0.0;var time=0L
        for((a,b) in points.zipWithNext())if(!b.segmentStart && b.time-a.time in 1..29999 && (b.speed?:0.0)>=1.0){val dt=b.time-a.time;weighted+=b.speed!!*dt;time+=dt}
        return if(time>0)weighted/time else null
    }
    fun init(c:Context){
        val prefs=c.getSharedPreferences("ride_completion",Context.MODE_PRIVATE)
        if(!prefs.contains("since"))prefs.edit().putLong("since",System.currentTimeMillis()).commit()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{TripStore.history.collect{history->
            for(t in history.filter{eligible(it) && !it.interrupted && it.ended>=prefs.getLong("since",Long.MAX_VALUE)}){
                val key="seen_"+t.id
                if(prefs.getBoolean(key,false))continue
                // Persist before notifying: at-most-once alerts even if Android restarts us.
                if(!prefs.edit().putBoolean(key,true).commit())continue
                if(!prefs.getBoolean("notifications",false))continue
                val nm=c.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel("ride_summaries","Finished rides",NotificationManager.IMPORTANCE_DEFAULT))
                if(androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)continue
                runCatching{NotificationManagerCompat.from(c).notify(t.id.hashCode(),NotificationCompat.Builder(c,"ride_summaries").setSmallIcon(R.drawable.widget_icon_mileage)
                    .setContentTitle("Your ride is saved").setContentText("Open your route, summary and photos")
                    .setContentIntent(RideActionActivity.pending(c,"Summary/"+t.id)).setAutoCancel(true).build())}
            }
        }}
    }
}

@Composable fun RideSummarySettings(){
    val c=LocalContext.current;val p=remember{c.getSharedPreferences("ride_completion",0)}
    var enabled by remember{mutableStateOf(p.getBoolean("notifications",false))}
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){}
    Text("Ride summaries",style=MaterialTheme.typography.headlineSmall)
    Row{Text("Notify when a ride finishes",Modifier.weight(1f));Switch(enabled,{enabled=it;p.edit().putBoolean("notifications",it).apply();if(it)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)})}
    Text("The Dashboard summary works without notifications. Brief reconnects stay in one ride. Old rides do not trigger new alerts.")
}

@Composable fun LatestRideCard(open:(String)->Unit){
    val units by RideState.preferences.collectAsState()
    val history by TripStore.history.collectAsState();val revision by SoftwareStore.revision.collectAsState()
    val trip=history.firstOrNull{RideCompletion.eligible(it)}?:return
    if(remember(revision){SoftwareStore.value("dismissedSummary")}==trip.id)return
    ToolCard{
        Text("YOUR LAST RIDE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(trip.title.ifBlank{toolDate(trip.started)},style=MaterialTheme.typography.titleLarge)
        Text("${RideUnits.distance(trip.meters,units.imperial)} · ${trip.durationText()}" )
        if(trip.interrupted)Text("Interrupted recording · recovered points retained")
        Button(onClick={open("Summary/"+trip.id)}){Text("View ride summary")}
        TextButton(onClick={runCatching{SoftwareStore.set("dismissedSummary",trip.id)}}){Text("Dismiss card")}
    }
}

@Composable fun RideSummaryScreen(id:String,open:(String)->Unit){
    var d by remember(id){mutableStateOf<TripDetail?>(null)};var error by remember{mutableStateOf("")}
    val rev by SoftwareStore.revision.collectAsState();val units by RideState.preferences.collectAsState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(id){runCatching{withContext(Dispatchers.IO){TripStore.detail(id)}}.onSuccess{d=it}.onFailure{error="Ride unavailable; original files retained"}}
    val detail=d
    if(detail==null){Text(error.ifBlank{"Loading ride…"});return}
    val t=detail.summary
    Text("Ride summary",style=MaterialTheme.typography.headlineSmall)
    Text(toolDate(t.started)+" → "+toolDate(t.ended))
    if(detail.track.isNotEmpty())key(id){TripMap(detail.track)}else Text("No usable GPS route recorded")
    ToolCard{
        Text(RideUnits.distance(detail.stats.meters,units.imperial),style=MaterialTheme.typography.headlineMedium)
        Text("Elapsed · ${t.durationText()}")
        Text("Moving · ${if(t.modern && detail.stats.maxMps!=null)TripSummary.duration(detail.stats.movingMs) else "Unavailable"}")
        Text("Stopped · ${if(t.modern && detail.stats.maxMps!=null)TripSummary.duration(detail.stats.stoppedMs) else "Unavailable"}")
        Text("Unknown / GPS gaps · ${TripSummary.duration(detail.stats.unknownMs)}")
        val avg=RideCompletion.movingAverage(detail.track)
        Text("Average moving speed · "+(avg?.let{String.format(Locale.US,"%.1f %s",it*(if(units.imperial)2.236936 else 3.6),if(units.imperial)"mph" else "km/h")}?:"Unavailable"))
        Text("${TripInsights.stops(detail.track).size} detected stops · ${detail.stats.gaps} route gaps")
        Text("GPS estimates. Missing intervals are not counted as stops.",style=MaterialTheme.typography.bodySmall)
        if(t.interrupted)Text("Interrupted recording · recovered points only",color=MaterialTheme.colorScheme.error)
    }
    val parking=remember(rev){SoftwareStore.snapshot().optJSONObject("parking")}
    val associated=parking!=null && parking.optString("tripId")==id && parking.optLong("time") in t.started..(t.ended+120000) && t.ended>=t.started
    Text("Parking · "+if(!associated)"No location associated with this ride" else if(parking!!.optBoolean("confirmed"))"Confirmed" else "Candidate · not yet confirmed")
    if(associated)TextButton(onClick={open("Last Parked")}){Text("View parking")}
    var name by rememberSaveable(id){mutableStateOf(t.title)};var notes by rememberSaveable(id){mutableStateOf(t.notes)}
    OutlinedTextField(name,{name=it.take(100)},label={Text("Ride name")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(notes,{notes=it.take(2000)},label={Text("Story / notes")},modifier=Modifier.fillMaxWidth())
    Button(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){TripStore.saveMetadata(id,name,notes)}}.onSuccess{error="Ride saved"}.onFailure{error="Could not save ride"}}}){Text("Save ride details")}
    TextButton(onClick={runCatching{SoftwareStore.favorite(id,!SoftwareStore.favorite(id))}.onFailure{error="Could not update favorite"}}){Text(if(remember(rev){SoftwareStore.favorite(id)})"★ Favorite" else "☆ Favorite ride")}
    Button(onClick={open("Journal/$id")}){Text("Photos & publishing")}
    TripCharts(detail)
    if(error.isNotBlank())Text(error)
}
