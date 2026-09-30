package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable internal fun RideRecoverySettings() {
    val scope=rememberCoroutineScope();val publishing by DadRides.revision.collectAsState()
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var choices by remember{mutableStateOf<List<RideRecovery.Candidate>?>(null)}
    var selected by remember{mutableStateOf<JSONObject?>(null)}
    fun work(action:suspend ()->Unit){scope.launch{busy=true;message="";try{action()}catch(e:Exception){if(e is CancellationException)throw e;message=e.message?:"Could not restore. Please retry."}finally{busy=false}}}
    ToolCard {
        Text("Restore from DadRides",style=MaterialTheme.typography.titleMedium)
        Text("Recover a missing ride's website details and available route. Original GPS recordings and photo files are not downloaded.")
        Button(enabled=!busy&&remember(publishing){DadRides.enabled()&&DadRides.origin().isNotBlank()},onClick={work{choices=withContext(Dispatchers.IO){RideRecovery.catalog()}}}){Text(if(busy)"Checking DadRides…" else "Restore from DadRides")}
        if(message.isNotBlank())Text(message)
    }
    choices?.let{rides->if(selected==null)AlertDialog(onDismissRequest={if(!busy)choices=null},title={Text("Choose a missing ride")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(rides.isEmpty())Text("No missing rides with a current website draft were found. Existing trips receive edits through Sync now.")
            for(ride in rides)OutlinedButton(enabled=!busy,onClick={work{selected=withContext(Dispatchers.IO){RideRecovery.preview(ride.id)}}}){Text("${ride.title}\n${ride.date}")}
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=!busy,onClick={choices=null}){Text("Close")}})}
    selected?.let{record->val m=record.getJSONObject("manifest")
        AlertDialog(onDismissRequest={if(!busy)selected=null},title={Text("Restore this website copy?")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(m.getString("title"),style=MaterialTheme.typography.titleMedium);Text(m.getString("date"));Text(m.getString("story"))
            Text("${RideEdits.route(m).size} available route points. ${if(m.getJSONObject("stats").length()==0)"Ride statistics are unavailable." else "Website ride statistics included."}")
            Text("Hidden route endpoints, original GPS timestamps and ${m.getJSONArray("photos").length()} photo files are not restored. This creates one labeled website copy in My Trips and shares it with the other edition when sync is enabled.")
            if(record.optBoolean("needsReview"))Text("This draft has route changes awaiting privacy verification. A restored copy cannot perform that check; the original recording is required.")
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
        }},dismissButton={TextButton(enabled=!busy,onClick={selected=null}){Text("Back")}},confirmButton={TextButton(enabled=!busy,onClick={work{
            withContext(Dispatchers.IO){RideRecovery.restore(record)}
            selected=null;choices=null;message="Restored ${m.getString("title")} to My Trips. Open the other edition to receive it."
        }}){Text(if(busy)"Restoring…" else "Restore website copy")}})
    }
}

@Composable internal fun WebsiteRideStatistics(detail:TripDetail) {
    val units by RideState.preferences.collectAsState()
    ToolCard {
        Text("Website ride statistics",style=MaterialTheme.typography.titleMedium)
        if(!detail.summary.statisticsAvailable)Text("Statistics were not included in this website copy.") else {
            Text("Distance · ${RideUnits.distance(detail.stats.meters,units.imperial)}")
            Text("Elapsed · ${detail.summary.durationText()}")
            Text("Moving · ${TripSummary.duration(detail.stats.movingMs)}")
            Text("Stopped · ${TripSummary.duration(detail.stats.stoppedMs)}")
            Text("Paused · ${TripSummary.duration(detail.stats.pausedMs)}")
            Text("Unknown · ${TripSummary.duration(detail.stats.unknownMs)}")
        }
        Text("GPS charts, replay, exact start/finish times and original GPX are unavailable.")
    }
}

@Composable internal fun WebsiteRidePhotos(id:String) {
    val context=LocalContext.current;val revision by SoftwareStore.revision.collectAsState()
    val record=remember(id,revision){RideEdits.record(id)};val m=record?.optJSONObject("manifest")
    ToolCard {
        Text("Photos on DadRides",style=MaterialTheme.typography.titleMedium)
        Text("This restored copy includes photo descriptions, but no photo files. Viewing photos on the website uses normal R2 reads.")
        m?.getJSONArray("photos")?.let{photos->for(i in 0 until photos.length())Text("Photo ${i+1}: ${photos.getJSONObject(i).optString("caption").ifBlank{"No caption"}}")}
        Text("Edit this ride on DadRides or save its name and notes in the app. A new prepared upload requires the original recording and local photos.")
        val origin=record?.optString("origin").orEmpty()
        if(origin.isNotBlank())OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$origin/#edit/$id")))}){Text("Open DadRides")}
    }
}
