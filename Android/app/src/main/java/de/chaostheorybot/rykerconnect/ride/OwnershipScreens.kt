package de.chaostheorybot.rykerconnect.ride

import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

@Composable internal fun OwnershipField(label: String, value: String, numeric: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(300)) }, label={Text(label)}, singleLine=true, modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text))
}

@Composable fun VehicleProfileScreen() {
    val context=LocalContext.current
    val units by RideState.preferences.collectAsState()
    val original=remember { SoftwareStore.snapshot().optJSONObject("vehicle") ?: JSONObject() }
    var nickname by rememberSaveable { mutableStateOf(original.optString("nickname")) }
    var model by rememberSaveable { mutableStateOf(original.optString("model")) }
    var year by rememberSaveable { mutableStateOf(original.optString("year")) }
    var vin by rememberSaveable { mutableStateOf(original.optString("vin")) }
    var date by rememberSaveable { mutableStateOf(original.optString("purchaseDate")) }
    var mileage by rememberSaveable(units.imperial) { mutableStateOf(if(original.has("purchaseKm")) (original.getDouble("purchaseKm")/(if(units.imperial)1.609344 else 1.0)).toString() else "") }
    var home by rememberSaveable { mutableStateOf(original.optString("home")) }
    var photoFile by rememberSaveable { mutableStateOf("") }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    val photo=remember(photoFile,photoRemoved) {
        when {
            photoRemoved -> ""
            photoFile.matches(Regex("vehicle-draft-[a-f0-9-]+\\.txt")) -> runCatching{java.io.File(context.cacheDir,photoFile).readText()}.getOrDefault(original.optString("photo"))
            else -> original.optString("photo")
        }
    }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        busy=true
        runCatching { withContext(Dispatchers.IO){
            val encoded=ReceiptImages.load(context,uri)
            java.io.File(context.cacheDir,"vehicle-draft-${java.util.UUID.randomUUID()}.txt").apply{writeText(encoded)}.name
        } }.onSuccess{photoFile=it;photoRemoved=false}.onFailure{message=it.message?:"Photo could not be loaded"}
        busy=false
    } }
    Text("Your Ryker",style=MaterialTheme.typography.headlineSmall)
    Text("All details are optional. Stored on this phone and included in your backup.")
    ReceiptPreview(photo)
    OutlinedButton(enabled=!busy,onClick={picker.launch(arrayOf("image/*"))}){Text("Choose vehicle photo")}
    if(photo.isNotBlank())TextButton(onClick={photoRemoved=true}){Text("Remove vehicle photo")}
    OwnershipField("Nickname",nickname){nickname=it.take(60)}
    OwnershipField("Model year",year,true){year=it.take(4)}
    OwnershipField("Model / edition",model){model=it.take(80)}
    OwnershipField("VIN (optional)",vin){vin=it.uppercase(Locale.ROOT).take(17)}
    OwnershipField("Purchase date (YYYY-MM-DD, optional)",date){date=it.take(10)}
    OwnershipField("Purchase odometer (${if(units.imperial)"miles" else "km"}, optional)",mileage,true){mileage=it}
    Text("Purchase mileage is a profile detail. Use Record mileage to update maintenance reminders.",style=MaterialTheme.typography.bodySmall)
    OwnershipField("Home address for Google Maps",home){home=it}
    Text("The home address is sent to Google Maps only when you choose Navigate home. VIN and home address never appear on widgets.",style=MaterialTheme.typography.bodySmall)
    Button(enabled=!busy,onClick={runCatching {
        val profile=JSONObject().put("nickname",nickname.trim()).put("model",model.trim()).put("vin",vin.trim())
            .put("purchaseDate",date.trim()).put("home",home.trim()).put("photo",photo)
        if(year.isNotBlank())profile.put("year",year.toIntOrNull()?:error("Enter a whole model year"))
        if(mileage.isNotBlank())profile.put("purchaseKm",(mileage.replace(',','.').toDoubleOrNull()?:error("Enter valid purchase mileage"))*(if(units.imperial)1.609344 else 1.0))
        VehicleProfile.validate(profile)
        SoftwareStore.commit(SoftwareStore.snapshot().put("vehicle",profile));message="Vehicle profile saved"
    }.onFailure{message=it.message?:"Could not save vehicle profile"}}){Text("Save vehicle profile")}
    if(message.isNotBlank())Text(message)
}

internal fun navigateHome(context: android.content.Context) {
    val home=SoftwareStore.snapshot().optJSONObject("vehicle")?.optString("home").orEmpty()
    require(home.isNotBlank()){"Set your home address in Vehicle profile first."}
    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(home)+"&travelmode=driving")))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DashboardQuickActions(open:(String)->Unit) {
    val context=LocalContext.current
    var message by remember { mutableStateOf("") }
    val revision by SoftwareStore.revision.collectAsState()
    val profile=remember(revision){SoftwareStore.snapshot().optJSONObject("vehicle")}
    ToolCard {
        Text(profile?.optString("nickname")?.ifBlank{"Your Ryker"}?:"Your Ryker",style=MaterialTheme.typography.titleLarge)
        Text(listOfNotNull(profile?.optString("year")?.takeIf{it.isNotBlank()},profile?.optString("model")?.takeIf{it.isNotBlank()}).joinToString(" · ").ifBlank{"Add your vehicle details in Settings."})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Record mileage" to "Add mileage","Add fuel" to "Add fuel","Log service" to "Add service","Last Parked" to "Last Parked").forEach{(label,page)->
                FilledTonalButton(onClick={open(page)}){Text(label)}
            }
            OutlinedButton(onClick={
                if(profile?.optString("home").isNullOrBlank())open("Vehicle profile")
                else runCatching{navigateHome(context)}.onFailure{message="Could not open Google Maps. Check that a browser or Maps is installed."}
            }){Text(if(profile?.optString("home").isNullOrBlank())"Set home address" else "Navigate home")}
        }
        if(message.isNotBlank())Text(message)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GarageHistoryFilter(records: List<JSONObject>): List<JSONObject> {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val units by RideState.preferences.collectAsState()
    var query by rememberSaveable{mutableStateOf("")};var kind by rememberSaveable{mutableStateOf("All")}
    var service by rememberSaveable{mutableStateOf("")};var advanced by rememberSaveable{mutableStateOf(false)}
    var from by rememberSaveable{mutableStateOf("")};var to by rememberSaveable{mutableStateOf("")}
    var minKm by rememberSaveable{mutableStateOf("")};var maxKm by rememberSaveable{mutableStateOf("")}
    var minCost by rememberSaveable{mutableStateOf("")};var maxCost by rememberSaveable{mutableStateOf("")}
    var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var pendingCsvFile by rememberSaveable{mutableStateOf<String?>(null)}
    val factor=if(units.imperial)1.609344 else 1.0
    val parsed=runCatching {
        fun number(s:String)=s.takeIf{it.isNotBlank()}?.let{it.replace(',','.').toDoubleOrNull()?:error("Enter valid numbers")}
        GarageFilter(query,kind,service,from.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it)},to.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it)},
            number(minKm)?.times(factor),number(maxKm)?.times(factor),number(minCost),number(maxCost)).also{it.validate()}
    }
    val rows=parsed.getOrNull()?.let{filter->records.filter(filter::matches).sortedByDescending{it.optLong("time")}}.orEmpty()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val filename=pendingCsvFile;pendingCsvFile=null
        if(filename!=null)scope.launch {
            busy=true
            runCatching{withContext(Dispatchers.IO){
                require(filename.matches(Regex("garage-export-[a-f0-9-]+\\.csv")))
                val file=java.io.File(context.cacheDir,filename)
                try { if(uri!=null)context.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}}?:error("Cannot open destination") }
                finally { file.delete() }
            }}.onSuccess{message=if(uri==null)"Export cancelled" else "CSV saved"}.onFailure{message="Export failed; discard any incomplete file."}
            busy=false
        }
    }
    Text("Find garage records",style=MaterialTheme.typography.titleMedium)
    OwnershipField("Search service names, notes or parts",query){query=it}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("All","Fuel","Service","Mileage").forEach{type->FilterChip(kind==type,{kind=type;service=""},{Text(type)})}
    }
    TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide filters" else "More filters")}
    if(advanced){
        val definitions=ServiceCatalog.items(ServiceCatalog.migrate(SoftwareStore.snapshot()))
        ServiceChoice(listOf(JSONObject().put("id","").put("name","All service types"))+definitions,service){service=it;kind="All"}
        OwnershipField("From date (YYYY-MM-DD)",from){from=it.take(10)}
        OwnershipField("Through date (YYYY-MM-DD)",to){to=it.take(10)}
        OwnershipField("Minimum odometer (${if(units.imperial)"mi" else "km"})",minKm,true){minKm=it}
        OwnershipField("Maximum odometer (${if(units.imperial)"mi" else "km"})",maxKm,true){maxKm=it}
        OwnershipField("Minimum cost (USD)",minCost,true){minCost=it}
        OwnershipField("Maximum cost (USD)",maxCost,true){maxCost=it}
    }
    TextButton(onClick={query="";kind="All";service="";from="";to="";minKm="";maxKm="";minCost="";maxCost=""}){Text("Clear filters")}
    if(parsed.isFailure) Text("Check filters: use YYYY-MM-DD dates and valid minimum/maximum values.",color=MaterialTheme.colorScheme.error)
    else Text("${rows.size} matching records · $"+String.format(Locale.US,"%.2f",GarageReport.spending(rows,"Fuel")+GarageReport.spending(rows,"Service"))+" recorded spending")
    OutlinedButton(enabled=!busy && parsed.isSuccess && rows.isNotEmpty(),onClick={scope.launch{
        busy=true
        runCatching{withContext(Dispatchers.IO){
            val file=java.io.File(context.cacheDir,"garage-export-${java.util.UUID.randomUUID()}.csv")
            file.writeText(GarageReport.csv(rows,units.imperial),Charsets.UTF_8);file.name
        }}.onSuccess{pendingCsvFile=it;export.launch("RykerConnect-garage-${LocalDate.now()}.csv")}.onFailure{message="Could not prepare CSV"}
        busy=false
    }}){Text("Export matching records (CSV)")}
    if(message.isNotBlank())Text(message)
    var annual by rememberSaveable{mutableStateOf(false)}
    TextButton(onClick={annual=!annual}){Text(if(annual)"Hide annual spending" else "Annual spending")}
    if(annual){
        Text("Based on the current filters and recorded costs; mileage-only entries add no spending.")
        rows.groupBy{java.time.Instant.ofEpochMilli(it.optLong("time")).atZone(java.time.ZoneId.systemDefault()).year}.toSortedMap(compareByDescending{it}).forEach{(year,items)->
            Text("$year · Fuel $"+String.format(Locale.US,"%.2f",GarageReport.spending(items,"Fuel"))+" · Service $"+String.format(Locale.US,"%.2f",GarageReport.spending(items,"Service")))
        }
    }
    return rows
}
