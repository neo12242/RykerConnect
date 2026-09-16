package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.logic.ConnectionHealth
import de.chaostheorybot.rykerconnect.logic.MainUnitControl
import kotlinx.coroutines.delay

object DashboardLayout {
    val defaults=listOf("Ride","Music","Navigation","Weather","Sensor")
    fun order(value:String):List<String> = (value.split(',').filter{it in defaults}+defaults).distinct()
    fun visible(name:String)=SoftwareStore.value("hide$name")!="true"
}
@Composable internal fun DashboardTools() {
    val revision by SoftwareStore.revision.collectAsState()
    val order=remember(revision){DashboardLayout.order(SoftwareStore.value("dashboardOrder"))}
    var message by remember{mutableStateOf("")}
    fun save(key:String,value:String){runCatching{SoftwareStore.set(key,value)}.onFailure{message="Could not save dashboard setting"}}
    Text("Your Dashboard",style=MaterialTheme.typography.headlineSmall)
    Text("Choose cards and their order. Connection controls are on the Connect tab. Your selected cards remain available.")
    order.forEachIndexed { index,name -> ToolCard {
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Switch(DashboardLayout.visible(name),{save("hide$name",(!it).toString())})}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=index>0,onClick={val next=order.toMutableList();java.util.Collections.swap(next,index,index-1);save("dashboardOrder",next.joinToString(","))}){Text("Move up")}
            TextButton(enabled=index<order.lastIndex,onClick={val next=order.toMutableList();java.util.Collections.swap(next,index,index+1);save("dashboardOrder",next.joinToString(","))}){Text("Move down")}
        }
    } }
    Text(message)
}
@Composable internal fun ConnectionHealthTools(store:RykerConnectStore,open:(String)->Unit) {
    val context=LocalContext.current
    val report by ConnectionHealth.report.collectAsState()
    val sensor by EnvironmentState.reading.collectAsState()
    val sensorStatus by EnvironmentState.status.collectAsState()
    val trip by TripStore.summary.collectAsState()
    val weather by WeatherState.view.collectAsState()
    val intercom by store.getInterComConnectedToken.collectAsState(initial=false)
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    var message by remember{mutableStateOf("")}
    LaunchedEffect(Unit){while(true){now=SystemClock.elapsedRealtime();delay(1000)}}
    fun settings(action:String){runCatching{context.startActivity(Intent(action))}.onFailure{message="Open Android settings manually"}}
    ToolCard {
        Text("Main unit · ${report.status}",style=MaterialTheme.typography.titleLarge)
        Text("${report.lastEvent} · Attempt ${report.attempt}")
        Text(if(report.lastTransfer>0)"Last successful transfer: ${((now-report.lastTransfer).coerceAtLeast(0)/1000)}s ago" else "No successful transfer yet")
        Text("Last disconnect: ${report.lastDisconnect}")
        report.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        FlowRow { TextButton(onClick={MainUnitControl.request(context,MainUnitControl.CONNECT)}){Text("Connect / retry")};TextButton(onClick={settings(Settings.ACTION_BLUETOOTH_SETTINGS)}){Text("Bluetooth settings")} }
    }
    ToolCard {
        Text("Outdoor sensor",style=MaterialTheme.typography.titleMedium)
        val r=sensor
        Text(if(r==null)sensorStatus else if(r.fresh(maxOf(now,SystemClock.elapsedRealtime())))sensorStatus else "Reading stale · main unit may still be connected")
        if(r!=null)Text("Last sample: ${((maxOf(now,SystemClock.elapsedRealtime())-r.receivedAtMs+r.sampleAgeMs).coerceAtLeast(0)/1000)}s ago")
        Text("If only this sensor is unavailable, check the BME280 toggle in the simulator or its I2C wiring on hardware.",style=MaterialTheme.typography.bodySmall)
    }
    ToolCard {Text("GPS & recording",style=MaterialTheme.typography.titleMedium);Text(trip.gps);TextButton(onClick={open("Recording")}){Text("Recording permissions")};TextButton(onClick={settings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)}){Text("Phone location settings")}}
    ToolCard {Text("Weather",style=MaterialTheme.typography.titleMedium);Text(weather.status);TextButton(onClick={open("Weather")}){Text("Weather controls")}}
    ToolCard {Text("Intercom · ${if(intercom)"Connected" else "Not connected"}",style=MaterialTheme.typography.titleMedium);Text("Battery: ${RykerConnectApplication.intercomBattery.takeIf{it>=0}?.let{"$it%"}?:"Unavailable"}");TextButton(onClick={open("Device tools")}){Text("Intercom setup")}}
    TextButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}.onFailure{message="Open app permissions in Android settings"}}){Text("App permissions")}
    if(message.isNotBlank())Text(message)
}
