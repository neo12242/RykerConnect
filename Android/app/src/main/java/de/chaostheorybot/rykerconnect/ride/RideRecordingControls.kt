package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@Composable internal fun RideRecordingControls() {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var state by remember{mutableStateOf<JSONObject?>(null)}
    var feedback by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var endState by remember{mutableStateOf<JSONObject?>(null)}
    val error by TripStore.storageError.collectAsState()
    suspend fun refresh(){runCatching{RideControl.state(c)}.onSuccess{state=it}.onFailure{state=null;feedback=it.message?:"Recording status unavailable"}}
    fun act(action:String,expected:JSONObject?=state){
        if(busy||expected==null)return
        val request=JSONObject().put("id",UUID.randomUUID().toString()).put("action",action).put("owner",expected.getString("owner"))
            .put("token",expected.getString("token")).put("observedAt",expected.getLong("observedAt"))
        busy=true
        scope.launch{try{val reply=RideControl.dispatch(c,request,false);feedback=reply.optString("message");refresh()}catch(e:Exception){feedback=e.message?:"Action failed"}finally{busy=false}}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true){scope.launch{refresh();act("START")}}
        else feedback="Allow precise location before starting a ride."
    }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(Unit){while(true){if(!busy)refresh();delay(2_000)}}
    val mode=state?.optString("state")
    Text(when(mode){"recording"->"Recording your ride";"paused"->"Ride paused";"interrupted"->"Ride interrupted";"idle"->"Ready to ride";else->"Checking recorder…"},style=MaterialTheme.typography.titleLarge)
    state?.let { s -> if(mode!="idle"){
        Text(RideUnits.distance(s.optDouble("meters"),s.optBoolean("miles")),style=MaterialTheme.typography.headlineSmall)
        Text("Ride time ${TripSummary.duration(s.optLong("elapsedMs")-s.optLong("pausedMs"))} · Paused ${TripSummary.duration(s.optLong("pausedMs"))}")
        Text(s.optString("gps"))
        if(s.optString("owner")!=c.packageName)Text("Recording in your other edition. These controls operate that same ride.",style=MaterialTheme.typography.bodySmall)
    }}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy&&mode!=null,onClick={when(mode){
            "idle"->if(ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)act("START")else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            "paused","interrupted"->act("RESUME");"recording"->act("PAUSE")
        }},modifier=Modifier.weight(1f)){Text(if(busy)"Confirming…" else when(mode){"idle"->"Start Ride";"recording"->"Pause Ride";else->"Resume Ride"})}
        if(mode!=null&&mode!="idle")OutlinedButton(enabled=!busy,onClick={endState=state},modifier=Modifier.weight(1f)){Text("End Ride")}
    }
    if(endState!=null)AlertDialog(onDismissRequest={endState=null},title={Text("End this ride?")},text={Text("Save this ride in My Trips. Uploading and publishing are separate.")},
        confirmButton={TextButton(onClick={val expected=endState;endState=null;act("END",expected)}){Text("End Ride")}},dismissButton={TextButton(onClick={endState=null}){Text("Keep ride")}})
    Text("Pause at a long stop to stop GPS recording. Resume keeps the same ride. End saves it to My Trips.",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={c.startActivity(Intent(c,WatchSetupActivity::class.java))}){Text("Watch controls & setup")}
    if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
        TextButton(onClick={notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Enable recording notifications")}
    if(feedback.isNotBlank())Text(feedback)
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
}
