package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private fun amount(value: Double) = String.format(Locale.US, "%.1f", value)
private fun economy(value: Double, imperial: Boolean) = if(imperial) "${amount(235.214583/value)} mpg (US)" else "${amount(value)} L/100 km"

@Composable fun GarageTools(openEntry: ((String) -> Unit)? = null) {
    val revision by SoftwareStore.revision.collectAsState()
    val units by RideState.preferences.collectAsState()
    val fuel=remember(revision){SoftwareStore.records("fuel")}
    val services=remember(revision){SoftwareStore.records("maintenance")}
    var entry by rememberSaveable { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    if(entry != null) { TextButton(onClick={entry=null}){Text("Back to Garage")}; GarageEntry(entry!!){entry=null}; return }
    val lastOdo=(fuel+services).maxOfOrNull{it.getDouble("odometerKm")}
    val factor=if(units.imperial)1.609344 else 1.0
    val distanceUnit=if(units.imperial)"mi" else "km"
    ToolCard {
        Text("YOUR RYKER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text(lastOdo?.let{"${amount(it/factor)} $distanceUnit"}?:"No odometer yet",style=MaterialTheme.typography.headlineLarge)
        Text("Latest recorded odometer",style=MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Button(onClick={if(openEntry!=null)openEntry("Add fuel") else entry="Add fuel"},modifier=Modifier.weight(1f)){Text("Add fuel")}
        FilledTonalButton(onClick={if(openEntry!=null)openEntry("Add service") else entry="Add service"},modifier=Modifier.weight(1f)){Text("Add service")}
    }
    TextButton(onClick={if(openEntry!=null)openEntry("Add mileage") else entry="Add mileage"}){Text("Record mileage")}
    Text("Garage history",style=MaterialTheme.typography.titleLarge)
    if(fuel.isEmpty() && services.isEmpty())Text("Your first fill-up or service will appear here.")
    val filtered=GarageHistoryFilter(fuel+services)
    for(item in filtered) ToolCard {
        val isFuel=item.has("litres")
        Text(if(isFuel)"Fuel stop" else item.getString("name"),style=MaterialTheme.typography.titleMedium)
        Text(toolDate(item.optLong("time")),style=MaterialTheme.typography.bodySmall)
        Text("${amount(item.getDouble("odometerKm")/factor)} $distanceUnit")
        if(isFuel) {
            Text(String.format(Locale.US,"%.2f %s · $%.2f · %s",item.getDouble("litres")/(if(units.imperial)3.785411784 else 1.0),if(units.imperial)"US gal" else "L",item.getDouble("cost"),if(item.optBoolean("full"))"Full tank" else "Partial fill"))
            val result=GarageMath.forEntry(fuel,item.getString("id"))
            Text(if(!item.optBoolean("calculateMpg",true))"MPG calculation off for this entry" else result?.let{"${economy(it.litresPer100Km,units.imperial)} · ${if(it.fullTank)"Full-tank interval" else "Since last fill (estimate)"}"}?:"MPG needs a previous fill at a lower odometer",color=MaterialTheme.colorScheme.primary)
        }
        if(item.optBoolean("missedFill"))Text("Missed fill-up before this entry · economy interval excluded")
        GarageMath.forEntry(fuel,item.optString("id"))?.let { result -> if(GarageInsights.unusual(result))Text("Unusual fuel economy — check odometer, quantity and missed fill-ups.",color=MaterialTheme.colorScheme.error) }
        if(item.optString("notes").isNotBlank())Text(item.getString("notes"))
        if(item.optString("parts").isNotBlank())Text("Parts: ${item.getString("parts")}")
        ReceiptPreview(item.optString("receipt"))
        TextButton(onClick={val page="Edit ${if(isFuel)"fuel" else "service"}/"+item.getString("id");if(openEntry!=null)openEntry(page) else entry=page}){Text("Edit entry")}
        item.optJSONObject("location")?.let { location ->
            Text("GPS ±${location.getDouble("accuracy").toInt()} m · ${toolDate(location.getLong("time"))}",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${location.getDouble("lat")},${location.getDouble("lon")}?q=${location.getDouble("lat")},${location.getDouble("lon")}")))}}){Text("View stop location")}
        }
        ConfirmDelete("Delete entry"){SoftwareStore.remove(if(isFuel)"fuel" else "maintenance",item.getString("id"))}
    }
}

@Composable private fun NumericField(label: String,value: String,change:(String)->Unit) {
    OutlinedTextField(value, {change(it.take(12))},label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
}
@Composable private fun EntryToggle(label:String,checked:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,change)}
}

@Composable fun GarageEntry(kind:String,onSaved:()->Unit = {}) {
    val context=LocalContext.current
    val units by RideState.preferences.collectAsState()
    val revision by SoftwareStore.revision.collectAsState()
    val records=remember(revision){SoftwareStore.records("fuel")+SoftwareStore.records("maintenance")}
    val lastOdo=records.maxOfOrNull{it.getDouble("odometerKm")}
    val fuel=kind=="Add fuel" || kind.startsWith("Edit fuel/")
    val id=kind.substringAfter('/',"")
    val original=remember(kind){records.firstOrNull{it.optString("id")==id}}
    val plan=remember(kind){if(kind.startsWith("Complete plan/"))MaintenancePlans.find(id) else null}
    val editing=kind.startsWith("Edit ")
    val distance=if(units.imperial)"miles" else "km"
    val factor=if(units.imperial)1.609344 else 1.0
    var odo by rememberSaveable{mutableStateOf("")};var quantity by rememberSaveable{mutableStateOf("")};var cost by rememberSaveable{mutableStateOf("")}
    val catalog=remember(revision){ServiceCatalog.items(ServiceCatalog.migrate(SoftwareStore.snapshot()))}
    var serviceId by rememberSaveable{mutableStateOf(if(plan!=null)plan.getString("serviceId") else if(kind=="Add mileage")ServiceCatalog.MILEAGE else if(kind.startsWith("Log service/"))kind.substringAfter("/") else original?.optString("serviceId")?.ifBlank{ServiceCatalog.id(original.optString("name"))} ?: catalog.first().getString("id"))}
    val mileage=serviceId==ServiceCatalog.MILEAGE
    var full by rememberSaveable{mutableStateOf(true)}
    var gps by rememberSaveable{mutableStateOf(SoftwareStore.value("garageGps")!="false")}
    var mpg by rememberSaveable{mutableStateOf(SoftwareStore.value("garageMpg")!="false")}
    var message by remember{mutableStateOf("")}
    var missed by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var parts by rememberSaveable { mutableStateOf("") }
    var receipt by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    var photoBusy by remember { mutableStateOf(false) }
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        photoBusy=true
        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){ReceiptImages.load(context,uri)} }
            .onSuccess{receipt=it}.onFailure{message=it.message?:"Could not attach photo"}
        photoBusy=false
    } }
    LaunchedEffect(kind) { if(!loaded) {
        if(original!=null) {
            odo=if(editing) (original.getDouble("odometerKm")/factor).toString() else ""
            quantity=if(fuel) original.getDouble("litres").div(if(units.imperial)3.785411784 else 1.0).toString() else ""
            cost=original.optDouble("cost",0.0).toString()
            full=original.optBoolean("full",true);mpg=original.optBoolean("calculateMpg",true)
            if(editing){notes=original.optString("notes");parts=original.optString("parts");receipt=original.optString("receipt");missed=original.optBoolean("missedFill");gps=original.has("location");date=java.time.Instant.ofEpochMilli(original.getLong("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()}
        }
        if(plan!=null){notes=plan.optString("notes");parts=MaintenancePlans.rows(plan,"parts").joinToString { it.getString("name")+" x "+it.getDouble("quantity") }.take(500)}
        loaded=true
    } }
    if(saved){Text("Entry saved",style=MaterialTheme.typography.headlineSmall);Text(message);return}
    fun number(text:String)=text.replace(',','.').toDoubleOrNull()?.takeIf{it.isFinite()}?:error("Enter valid numeric values")
    Text(if(fuel)"Log a fuel stop" else if(mileage)"Record your mileage" else "Log a service",style=MaterialTheme.typography.headlineSmall)
    Text("Use the odometer on your Ryker. ${lastOdo?.let{"Last entry: ${amount(it/factor)} $distance."}?:"This will be your first reading."}",style=MaterialTheme.typography.bodyMedium)
    NumericField("Odometer ($distance)",odo){odo=it}
    OutlinedTextField(date,{date=it.take(10)},label={Text("Entry date (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.fillMaxWidth())
    if(fuel) {
        NumericField(if(units.imperial)"Fuel added (US gallons)" else "Fuel added (litres)",quantity){quantity=it}
        NumericField("Total cost (USD)",cost){cost=it}
        EntryToggle("Filled the tank completely",full){full=it}
        EntryToggle("Calculate MPG for this fill-up",mpg){mpg=it;runCatching{SoftwareStore.set("garageMpg",it.toString())}.onFailure{message="Could not remember MPG preference"}}
        EntryToggle("Missed a fill-up before this entry",missed){missed=it}
        Text("Full-tank MPG includes partial fills since the previous full tank. Other intervals are labeled estimates. Turning this off keeps the fuel amount for later calculations.",style=MaterialTheme.typography.bodySmall)
    } else {
        val options=catalog.filter{(it.optBoolean("enabled") || editing && it.getString("id")==serviceId) && (!editing || (it.getString("id")==ServiceCatalog.MILEAGE)==ServiceCatalog.isMileage(original?:JSONObject()))}
        if(plan==null)ServiceChoice(options,serviceId){serviceId=it} else Text("Completing planned service: "+(catalog.firstOrNull{it.optString("id")==serviceId}?.optString("name")?:"Unavailable"))
        if(mileage) Text("Updates your recorded odometer without completing maintenance or adding a fuel stop.")
        else {
            NumericField("Service cost (USD, 0 if none)",cost){cost=it}
            OutlinedTextField(parts,{parts=it.take(500)},label={Text("Parts used")},modifier=Modifier.fillMaxWidth())
        }
    }
    OutlinedTextField(notes,{notes=it.take(2000)},label={Text("Notes")},modifier=Modifier.fillMaxWidth())
    if(!mileage || fuel) {
    OutlinedButton(enabled=!photoBusy,onClick={photo.launch(arrayOf("image/*"))}){Text(if(photoBusy)"Loading photo…" else "Attach receipt photo")}
    ReceiptPreview(receipt)
    if(receipt.isNotBlank())TextButton(onClick={receipt=""}){Text("Remove attached photo")}
    }
    EntryToggle("Save GPS location",gps){gps=it;runCatching{SoftwareStore.set("garageGps",it.toString())}.onFailure{message="Could not remember GPS preference"}}
    val location=EntryLocation(gps && !editing)
    if(editing && gps)Text("Keeps the original saved location. Turn off to remove it.",style=MaterialTheme.typography.bodySmall)
    Text(if(editing && gps)"The original location is preserved when saving edits." else if(gps)"The current fix is attached only when you save. If unavailable, the entry is saved without a location." else "This entry will not include a location.",style=MaterialTheme.typography.bodySmall)
    Button(enabled=!photoBusy,modifier=Modifier.fillMaxWidth(),onClick={runCatching {
        val km=number(odo)*factor
        require(km in 0.0..2_000_000.0){"Enter a valid odometer"}
        val day=java.time.LocalDate.parse(date)
        require(!day.isAfter(java.time.LocalDate.now())){"Entry date cannot be in the future"}
        val oldDay=original?.let{java.time.Instant.ofEpochMilli(it.getLong("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}
        val time=if(editing && day==oldDay)original!!.getLong("time") else if(day==java.time.LocalDate.now())System.currentTimeMillis() else day.atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        GarageInsights.validateOdometer(records.filter{!editing || it.optString("id")!=id},time,km)
        val record=JSONObject().put("time",time).put("odometerKm",km).put("notes",notes.trim()).put("receipt",receipt)
        if(fuel) {
            val litres=number(quantity)*(if(units.imperial)3.785411784 else 1.0);val dollars=number(cost)
            GarageMath.fuel(km,litres,dollars)
            record.put("litres",litres).put("cost",dollars).put("full",full).put("calculateMpg",mpg).put("missedFill",missed)
        } else {
            val type=ServiceCatalog.items(ServiceCatalog.migrate(SoftwareStore.snapshot())).firstOrNull{it.getString("id")==serviceId}?:error("Choose a service")
            require(type.optBoolean("enabled") || editing && original?.optString("serviceId")==serviceId){"This service is disabled"}
            val dollars=if(mileage)0.0 else number(cost.ifBlank{"0"});require(dollars in 0.0..100_000.0){"Enter a valid cost"}
            val historicalName=if(editing && original?.optString("serviceId")==serviceId)original.getString("name") else type.getString("name")
            record.put("serviceId",serviceId).put("name",historicalName).put("intervalKm",if(editing)original?.optDouble("intervalKm",0.0)?:0.0 else 0.0)
                .put("intervalDays",if(editing)original?.optInt("intervalDays")?:0 else 0).put("cost",dollars).put("parts",if(mileage)"" else parts.trim())
            if(mileage)record.remove("receipt")
        }
        val fix=location?.takeIf{gps && validEntryLocation(it)}
        fix?.let{record.put("location",JSONObject().put("lat",it.latitude).put("lon",it.longitude).put("accuracy",it.accuracy).put("time",it.time))}
        if(editing && gps)original?.optJSONObject("location")?.let{record.put("location",it)}
        if(plan!=null)MaintenancePlans.complete(plan.getString("id"),record) else if(editing)SoftwareStore.update(if(fuel)"fuel" else "maintenance",id,record) else SoftwareStore.add(if(fuel)"fuel" else "maintenance",record)
        saved=true
        odo="";quantity="";cost=""
        message="${if(fuel)"Fill-up" else "Service"} saved${if(!record.has("location"))" without location" else " with saved location"}."
        onSaved()
    }.onFailure{message=it.message?:"Could not save entry"}}){Text(if(fuel)"Save fill-up" else if(mileage)"Save mileage" else "Save service")}
    if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodyMedium)
}

internal fun validEntryLocation(location:Location):Boolean = location.hasAccuracy() && location.accuracy in 0f..50f &&
    SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos in 0..120_000_000_000L

@Composable internal fun EntryLocation(enabled:Boolean):Location? {
    val context=LocalContext.current
    var fix by remember{mutableStateOf<Location?>(null)}
    var status by remember{mutableStateOf("")}
    var retry by remember{mutableIntStateOf(0)}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){retry++}
    LaunchedEffect(enabled,retry) {
        fix=null
        if(!enabled){status="Location recording off";return@LaunchedEffect}
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){status="Precise location permission needed";return@LaunchedEffect}
        val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){status="Turn on phone Location, then retry";return@LaunchedEffect}
        val cancellation=CancellationSignal()
        try {
            status="Getting current GPS location…"
            manager.getCurrentLocation(LocationManager.GPS_PROVIDER,cancellation,context.mainExecutor){value ->
                if(!cancellation.isCanceled){fix=value?.takeIf{validEntryLocation(it)};status=fix?.let{"GPS ready · ±${it.accuracy.toInt()} m"}?:"No recent accurate GPS fix"}
            }
            delay(12_000)
            if(fix==null) status="GPS unavailable. You can save without location or retry."
        } catch(_:SecurityException){status="Location permission unavailable"} finally{cancellation.cancel()}
    }
    if(enabled) {
        Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        TextButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) else retry++}) { Text("Refresh GPS / permissions") }
    }
    return fix
}
