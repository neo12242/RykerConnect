package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray

@Composable fun JournalPhoto(id:String){
    val image=remember(id){runCatching{BitmapFactory.decodeFile(RidePhotos.file("$id.thumb.jpg").path)?.asImageBitmap()}.getOrNull()}
    if(image!=null)Image(image,"Ride photo",Modifier.fillMaxWidth().height(200.dp),contentScale=ContentScale.Fit)else Text("Photo unavailable")
}
@Composable fun RideJournalScreen(id:String){
    val units by RideState.preferences.collectAsState()
    val c=LocalContext.current;val scope=rememberCoroutineScope();val rev by SoftwareStore.revision.collectAsState();val publishing by DadRides.revision.collectAsState()
    val j=remember(rev){RidePhotos.journal(id)};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var tags by rememberSaveable(id){mutableStateOf(j.optString("tags"))};var remove by remember{mutableStateOf<String?>(null)}
    var preview by remember{mutableStateOf<JSONObject?>(null)}
    fun save(next:JSONObject){runCatching{RidePhotos.save(next)}.onFailure{message=it.message?:"Could not save photo details"}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty())scope.launch{
        busy=true;var added=0
        runCatching{withContext(Dispatchers.IO){for(uri in uris){RidePhotos.add(c,id,uri);added++}}}.onSuccess{message="$added photos added"}.onFailure{message="$added added. "+(it.message?:"Could not add photo")};busy=false
    }}
    Text("Ride photos & story",style=MaterialTheme.typography.headlineSmall)
    Text("Photos stay on this phone until you prepare an upload. Your gallery originals are unchanged.")
    Button(enabled=!busy,onClick={picker.launch(arrayOf("image/*"))}){Text(if(busy)"Adding photos…" else "Add photos")}
    OutlinedTextField(tags,{tags=it.take(300)},label={Text("Tags, separated by commas")},modifier=Modifier.fillMaxWidth())
    TextButton(onClick={save(j.put("tags",tags))}){Text("Save tags")}
    if(RidePhotos.photos(j).isEmpty())Text("Add moments from this ride. Up to 30 photos, each under 15 MB.")
    for((index,p) in RidePhotos.photos(j).withIndex())key(p.getString("id")){
        val photoId=p.getString("id");var caption by rememberSaveable(photoId){mutableStateOf(p.optString("caption"))}
        ToolCard{
            JournalPhoto(photoId)
            OutlinedTextField(caption,{caption=it.take(500)},label={Text("Caption")},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={p.put("caption",caption);save(j)}){Text("Save caption")}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(j.optString("cover")==photoId,{save(j.put("cover",photoId))},{Text("Cover photo")})
                if(remember(publishing){DadRides.enabled()})FilterChip(p.optBoolean("publish",true),{p.put("publish",!p.optBoolean("publish",true));save(j)},{Text("Include on website")})
                if(index>0)TextButton(onClick={val list=RidePhotos.photos(j).toMutableList();java.util.Collections.swap(list,index,index-1);save(j.put("photos",JSONArray(list)))}){Text("Move earlier")}
                TextButton(onClick={remove=photoId}){Text("Remove")}
            }
        }
    }
    remove?.let{photoId->AlertDialog(onDismissRequest={remove=null},title={Text("Remove this ride photo?")},text={Text("The phone-gallery original remains. Published versions and already prepared uploads are unchanged.")},confirmButton={TextButton(onClick={runCatching{RidePhotos.remove(id,photoId)}.onFailure{message="Could not remove photo"};remove=null}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Keep photo")}})}
    if(remember(publishing){DadRides.enabled()}){
        Text("Prepare for DadRides",style=MaterialTheme.typography.titleLarge)
        var trim by rememberSaveable{mutableStateOf("500")};var includeStats by rememberSaveable{mutableStateOf(true)};var includeRoute by rememberSaveable{mutableStateOf(true)}
        OutlinedTextField(trim,{trim=it.take(5)},label={Text("Hide start / finish radius (meters, 0–10000)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        Row{Checkbox(includeRoute,{includeRoute=it});Text("Include a redacted route")}
        Row{Checkbox(includeStats,{includeStats=it});Text("Include whole-ride statistics")}
        Text("The 500 m starting value is only a convenience, not a privacy guarantee. Review the map. GPS metadata is removed from every uploaded photo.")
        Button(enabled=!busy,onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){PublicRide.manifest(TripStore.detail(id),RidePhotos.journal(id),trim.toDouble(),includeStats,includeRoute)}}.onSuccess{preview=it}.onFailure{message=it.message?:"Could not prepare ride"};busy=false}}){Text("Preview public copy")}
        preview?.let{manifest->
            ToolCard{
                Text("PUBLIC COPY PREVIEW",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(manifest.getString("title"),style=MaterialTheme.typography.titleLarge);Text(manifest.getString("story"))
                val route=manifest.getJSONArray("route");val points=mutableListOf<TrackPoint>();var index=0L
                for(i in 0 until route.length()){val line=route.getJSONArray(i);for(k in 0 until line.length()){val p=line.getJSONArray(k);points.add(TrackPoint(p.getDouble(1),p.getDouble(0),++index*5000,5f,segmentStart=k==0))}}
                if(points.isNotEmpty())key(manifest.toString()){TripMap(points)}else Text("No route will be uploaded")
                val stats=manifest.getJSONObject("stats");Text(if(stats.has("meters"))"Whole ride: ${RideUnits.distance(stats.getDouble("meters"),units.imperial)} · ${TripSummary.duration(stats.getLong("elapsedMs"))}" else "Statistics excluded")
                Text("${manifest.getJSONArray("photos").length()} selected photos · ${manifest.getJSONObject("privacy").getDouble("trimMeters").toInt()} m endpoint privacy radius")
                val photos=manifest.getJSONArray("photos");for(i in 0 until photos.length()){val p=photos.getJSONObject(i);JournalPhoto(p.getString("id"));Text(p.getString("caption"))}
                var metered by rememberSaveable{mutableStateOf(false)}
                Row{Checkbox(metered,{metered=it});Text("Allow mobile data for this upload")}
                Button(enabled=!busy,onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){DadRides.queue(manifest,metered)}}.onSuccess{message="Private upload queued. Publish only after reviewing the uploaded draft.";preview=null}.onFailure{message=it.message?:"Could not queue upload"};busy=false}}){Text("Queue private draft")}
                TextButton(onClick={preview=null}){Text("Close preview")}
            }
        }
        DadRidesQueue(id)
    }
    if(message.isNotBlank())Text(message)
}

@Composable fun DadRidesSettings(){
    val c=LocalContext.current;val revision by DadRides.revision.collectAsState();val scope=rememberCoroutineScope()
    val enabled=remember(revision){DadRides.enabled()};var url by rememberSaveable{mutableStateOf(DadRides.origin().ifBlank{"https://rides.example.com"})}
    // The key deliberately does not participate in saved state or backups.
    var token by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    Text("Add-ons",style=MaterialTheme.typography.headlineSmall)
    ToolCard{
        Text("DadRides publishing",style=MaterialTheme.typography.titleLarge)
        Text("Optional personal ride website. Local rides, photos and maintenance work without it.")
        Row{Text("Enable publishing",Modifier.weight(1f));Switch(enabled,{DadRides.enable(it)})}
        if(enabled){
            OutlinedTextField(url,{url=it},label={Text("Site address")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{token=it},label={Text("DadRides publishing key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(enabled=token.isNotBlank(),onClick={runCatching{DadRides.configure(url,token);token="";message="Connection saved. Test it below."}.onFailure{message=it.message?:"Could not save connection"}}){Text("Save connection")}
            Text("The site is not provisioned yet unless you have deployed it. Never enter your Cloudflare account API token here.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!busy&&DadRides.origin().isNotBlank(),onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){DadRides.request("status")}}.onSuccess{message=String.format(java.util.Locale.US,"Connected · %.2f GB reserved / %.1f GB quota",it.getDouble("used")/1e9,it.getDouble("quota")/1e9)}.onFailure{message=it.message?:"Could not connect"};busy=false}}){Text("Test connection / storage")}
            TextButton(onClick={DadRides.disconnect();message="Disconnected. Published rides remain online."}){Text("Disconnect site")}
        }
    }
    if(message.isNotBlank())Text(message)
    if(enabled)DadRidesQueue()
}

@Composable fun DadRidesQueue(ride:String?=null){
    val c=LocalContext.current;val revision by DadRides.revision.collectAsState();val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf("")}
    val items=remember(revision){DadRides.items().filter{ride==null||it.optString("ride")==ride}}
    if(items.isNotEmpty())Text("Publishing queue",style=MaterialTheme.typography.titleLarge)
    for(item in items)ToolCard{
        Text(item.optString("title"),style=MaterialTheme.typography.titleMedium);Text(item.getString("state")+" · "+item.optInt("progress")+"%");Text(item.optString("message"))
        if(item.optString("state") in listOf("Failed","Canceled"))TextButton(onClick={DadRides.retry(item.getString("id"))}){Text("Retry upload")}
        if(item.optString("state") in listOf("Queued","Uploading"))TextButton(onClick={DadRides.cancel(item.getString("id"))}){Text("Cancel upload")}
        var removeCopy by remember{mutableStateOf(false)}
        if(item.optString("state") in listOf("Uploaded draft","Published","Canceled","Failed"))TextButton(onClick={removeCopy=true}){Text("Remove local upload copy")}
        if(removeCopy)AlertDialog(onDismissRequest={removeCopy=false},title={Text("Remove prepared upload copy?")},text={Text("Your local ride/photos and the remote version remain. You can prepare this ride again.")},confirmButton={TextButton(onClick={DadRides.removeLocal(item.getString("id"));removeCopy=false}){Text("Remove copy")}},dismissButton={TextButton(onClick={removeCopy=false}){Text("Keep")}})
        if(item.optString("state") in listOf("Uploaded draft","Published")){
            Button(onClick={runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(DadRides.previewUrl(item))))}.onFailure{message="No browser available"}}){Text("Review / publish on website")}
            TextButton(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){DadRides.checkStatus(item.getString("id"))}}.onSuccess{message=it}.onFailure{message=it.message?:"Could not check status"}}}){Text("Refresh publication / local changes")}
        }
    }
    if(message.isNotBlank())Text(message)
}
