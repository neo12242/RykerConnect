package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun SharedRecordingNotice(){
    val peer by SharedLibrary.peerRecording.collectAsState();val failure by RecordingCoordinator.status.collectAsState();val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf("")}
    if(peer.isNotBlank()){Text(peer);OutlinedButton(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){SharedLibrary.stopPeerRecording()}}.onFailure{message="Open the recording app to stop and save this ride"}}}){Text("Stop and save the shared ride")}}
    else if(failure.isNotBlank())Text(failure)
    if(message.isNotBlank())Text(message)
}

@Composable fun SharedLibrarySettings() {
    val status by SharedLibrary.status.collectAsState();val revision by SharedLibrary.revision.collectAsState()
    val software by SoftwareStore.revision.collectAsState();val peer by SharedLibrary.peerRecording.collectAsState()
    val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<String?>(null)}
    val choices=remember(revision){SharedLibrary.conflictChoices()}
    ToolCard {
        Text("Shared ride library",style=MaterialTheme.typography.titleLarge)
        Text("Phone and ESP share saved rides, photos, garage records and common preferences directly on this phone. Each app keeps its own copy.")
        Row{Text("Keep both apps synchronized",Modifier.weight(1f));Switch(remember(revision){SharedLibrary.enabled()},{SharedLibrary.enable(it)})}
        Text(status)
        SharedRecordingNotice()
        val last=remember(revision){SharedLibrary.lastSync()};if(last>0)Text("Last sync attempt · ${toolDate(last)}",style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy&&SharedLibrary.enabled(),onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){SharedLibrary.syncNow()}}.onFailure{message=it.message?:"Could not sync; local data retained"};busy=false}}){Text(if(busy)"Synchronizing…" else "Sync now")}
        if(DadRides.origin().isBlank() && SharedLibrary.peerAvailable())OutlinedButton(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){SharedLibrary.copyWebsiteConnection()}}.onSuccess{message="Website connection copied securely"}.onFailure{message=it.message?:"No website connection available"}}}){Text("Use the other app’s website connection")}
        Text("When DadRides is connected, service schedules, history and recorded odometers also sync to your private My Services page. Edit services in either app. Receipt images and locations stay on this phone; metadata sync does not access R2. Android permissions and Bluetooth pairing remain separate.",style=MaterialTheme.typography.bodySmall)
        choices.keys.forEach{key->OutlinedButton(onClick={selected=key}){Text("Review ${SharedLibrary.recordLabel(key)}")}}
        if(message.isNotBlank())Text(message)
    }
    RideRecoverySettings()
    val site=remember(software){SoftwareStore.records("siteEdits").filter{it.has("conflict")}}
    for(record in site)ToolCard {
        val local=record.getJSONObject("manifest");val remote=record.getJSONObject("conflict").getJSONObject("manifest")
        Text("Website edit conflict",style=MaterialTheme.typography.titleMedium)
        Text("On this phone: ${local.getString("title")}\n${local.getString("story")}")
        Text("On the website: ${remote.getString("title")}\n${remote.getString("story")}")
        for((label,value) in listOf("Phone" to local,"Website" to remote)) {
            val stats=value.getJSONObject("stats")
            Text("$label · ${value.getString("date")} · ${value.getJSONArray("tags")}\nCover: ${if(value.optString("cover").isBlank())"Default illustration" else "Photo " + ((0 until value.getJSONArray("photos").length()).indexOfFirst{value.getJSONArray("photos").getJSONObject(it).getString("id")==value.getString("cover")}+1)}",style=MaterialTheme.typography.bodySmall)
            Text("$label route: ${RideEdits.route(value).size} points in ${value.getJSONArray("route").length()} segments")
            Text("Distance: ${if(stats.has("meters"))"%.2f mi".format(stats.getDouble("meters")/1609.344) else "Private"} · elapsed ${stats.optLong("elapsedMs")/1000}s · moving ${stats.optLong("movingMs")/1000}s · stopped ${stats.optLong("stoppedMs")/1000}s · GPS unknown ${stats.optLong("unknownMs")/1000}s",style=MaterialTheme.typography.bodySmall)
            val photos=value.getJSONArray("photos")
            for(i in 0 until photos.length())Text("Photo ${i+1}: ${photos.getJSONObject(i).getString("caption")}",style=MaterialTheme.typography.bodySmall)
        }
        if(SyncJson.canonical(local.getJSONArray("route"))!=SyncJson.canonical(remote.getJSONArray("route")))Text("The route coordinates differ. Review both routes in the website editor before choosing if the difference is unclear.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={RideEdits.resolve(record.getString("id"),false)}){Text("Keep phone edits")}
            OutlinedButton(onClick={RideEdits.resolve(record.getString("id"),true)}){Text("Use website edits")}
        }
    }
    selected?.let{key->val current=choices[key];if(current!=null)AlertDialog(onDismissRequest={selected=null},title={Text("Choose the version to keep")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Both saved versions have been retained. This choice will synchronize to the other app.")
            for(choice in current){Text(choice.label);OutlinedButton(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){SharedLibrary.resolve(key,choice.fingerprint)}}.onFailure{message=it.message?:"Could not resolve conflict"};selected=null}}){Text("Keep this version")}}
        }},confirmButton={TextButton(onClick={selected=null}){Text("Decide later")}})}
}

@Composable internal fun EditedRideNotice(id:String,original:Boolean,websiteCopy:Boolean=false,onOriginal:(Boolean)->Unit) {
    val revision by SoftwareStore.revision.collectAsState();val edit=remember(id,revision){RideEdits.record(id)}
    if(websiteCopy){ToolCard {
        Text("Restored website copy",style=MaterialTheme.typography.titleMedium)
        Text("Available website details and route only. The original GPS recording, hidden endpoints and photo files are not stored in this copy.")
        if(edit?.optBoolean("needsReview")==true)Text("Original recording required to verify edited route privacy before publishing.")
        if(edit?.has("conflict")==true)Text("An edit conflict needs review in Settings → Add-ons.",color=MaterialTheme.colorScheme.error)
    };return}
    if(edit==null)return
    ToolCard {
        Text(if(original)"Original recording" else "Edited ride · ${edit.getJSONObject("manifest").getString("date")}",style=MaterialTheme.typography.titleMedium)
        Text("Original GPS samples and measured statistics are retained.")
        if(edit.optBoolean("needsReview"))Text("Route changes need a phone sync to check location privacy before publishing.")
        if(edit.has("conflict"))Text("An edit conflict needs review in Settings → Add-ons.",color=MaterialTheme.colorScheme.error)
        OutlinedButton(onClick={onOriginal(!original)}){Text(if(original)"View edited ride" else "View original recording")}
    }
}
