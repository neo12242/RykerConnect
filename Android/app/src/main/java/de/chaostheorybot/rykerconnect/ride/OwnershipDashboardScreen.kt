package de.chaostheorybot.rykerconnect.ride

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

@Composable internal fun SharedGarageOverview(open:(String)->Unit) {
    OwnershipOverview(open)
    GarageTools(openEntry=open)
}

@Composable internal fun OwnershipOverview(open:(String)->Unit) {
    val revision by SoftwareStore.revision.collectAsState();val units by RideState.preferences.collectAsState()
    val data=remember(revision){SoftwareStore.snapshot()};val expenses=remember(revision){OwnershipMath.records(data)}
    val today=LocalDate.now();val seasons=SoftwareStore.records("ownershipSeasons")
    var selected by rememberSaveable{mutableStateOf("year:${today.year}")};var menu by remember{mutableStateOf(false)}
    var category by rememberSaveable{mutableStateOf("")}
    val years=(expenses.map{it.date.year}+today.year).distinct().sortedDescending()
    val options=listOf("all" to "All time")+years.map{"year:$it" to it.toString()}+seasons.map{it.getString("id") to it.getString("name")}
    val season=seasons.firstOrNull{it.getString("id")==selected}
    val period=when {
        season!=null -> OwnershipPeriod(season.getString("name"),LocalDate.parse(season.getString("from")),LocalDate.parse(season.getString("to")),season.optDouble("startKm").takeIf{it.isFinite()},season.optDouble("endKm").takeIf{it.isFinite()})
        selected.startsWith("year:") -> selected.substringAfter(':').toInt().let{OwnershipPeriod(it.toString(),LocalDate.of(it,1,1),minOf(LocalDate.of(it,12,31),today))}
        else -> OwnershipPeriod("All time",expenses.minOfOrNull{it.date}?:today,today)
    }
    val rows=expenses.filter{period.includes(it.date)&&it.date<=today}
    ToolCard {
        Text("Ownership overview",style=MaterialTheme.typography.titleLarge)
        Box { OutlinedButton(onClick={menu=true}){Text(period.name+" ▾")};DropdownMenu(menu,{menu=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={selected=id;menu=false;category=""})}} }
        Text("${period.from} – ${minOf(period.to,today)} · USD",style=MaterialTheme.typography.bodySmall)
        Text(OwnershipMath.money(OwnershipMath.total(rows)),style=MaterialTheme.typography.headlineLarge)
        Text("Recorded spending",style=MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            for(kind in listOf("Fuel","Maintenance","Modifications"))TextButton(modifier=Modifier.weight(1f),onClick={category=if(category==kind)"" else kind}) {
                Column { Text(if(kind=="Modifications")"Mods" else kind,style=MaterialTheme.typography.labelMedium);Text(OwnershipMath.money(OwnershipMath.total(rows.filter{it.category==kind})),style=MaterialTheme.typography.titleMedium) }
            }
        }
        if(rows.any{it.cents==null})Text("${rows.count{it.cents==null}} prices not entered; excluded from totals.")
        Text("Totals reflect your saved records.",style=MaterialTheme.typography.bodySmall)
        val running=rows.filter{it.category!="Modifications"}
        fun ratio(values:List<OwnershipExpense>)=OwnershipMath.perKm(values,period)?.let{java.lang.String.format(java.util.Locale.US,"$%.2f / %s",it*(if(units.imperial)1.609344 else 1.0),if(units.imperial)"mile" else "km")}?:"Needs confirmed period mileage and costs"
        if(period.distanceKm()!=null) {
            Text("Fuel + maintenance: ${ratio(running)}")
            Text("Including modifications: ${ratio(rows)}")
        } else {
            Text("Cost per ${if(units.imperial)"mile" else "km"} unavailable",style=MaterialTheme.typography.titleSmall)
            Text("Add a named period with confirmed start/end odometers. GPS trip distance is not used.",style=MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick={open("Ownership season/"+(season?.getString("id")?:"new"))}){Text(if(season==null)"Add season / confirm period mileage" else "Edit season & mileage")}
        if(category.isNotBlank()) {
            Text("$category entries",style=MaterialTheme.typography.titleMedium)
            val matches=rows.filter{it.category==category};if(matches.isEmpty())Text("No recorded expenses in this period.")
            matches.take(50).forEach{r->Text("${r.date} · ${r.title}\n${r.cents?.let(OwnershipMath::money)?:"Cost not entered"}")}
            if(matches.size>50)Text("Showing 50 of ${matches.size}. Export for the complete list.")
        }
        var trends by rememberSaveable{mutableStateOf(false)}
        TextButton(onClick={trends=!trends}){Text(if(trends)"Hide monthly spending" else "Monthly spending")}
        if(trends)rows.groupBy{it.date.toString().take(7)}.toSortedMap(reverseOrder()).forEach{(month,items)->
            Text(month,style=MaterialTheme.typography.titleSmall)
            Text(listOf("Fuel","Maintenance","Modifications").joinToString(" · "){kind->"$kind ${OwnershipMath.money(OwnershipMath.total(items.filter{it.category==kind}))}"})
        }
        OwnershipExports(period,rows,units.imperial)
    }
    ToolCard {
        Text("Modifications & upcoming work",style=MaterialTheme.typography.titleLarge)
        val mods=SoftwareStore.records("modifications").filterNot{it.getBoolean("archived")}
        val pending=mods.filter{it.getString("status")!="Installed"}
        val estimates=pending.mapNotNull{ModificationRecords.cents(it,"estimateCents")}
        Text("${pending.size} planned / purchased mods · ${OwnershipMath.money(estimates.sum())} entered estimates")
        Text("Estimates are separate from actual spending.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={open("Modifications")}){Text("Manage modifications")}
        val alerts=ServiceAlerts.calculate(data,System.currentTimeMillis(),500.0,30).filter{it.approaching}
        alerts.take(3).forEach{Text(it.name+if(it.overdue)" · Due" else " · Upcoming")}
        val plans=SoftwareStore.records("plans").filter{it.optString("state") in listOf("Planned","In progress")}
        Text("${plans.size} open maintenance plans · ${OwnershipMath.money(java.math.BigDecimal.valueOf(plans.sumOf(MaintenancePlans::estimate)).movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact())} parts estimates")
        TextButton(onClick={open("Maintenance planner")}){Text("Review maintenance & parts")}
    }
}

@Composable private fun OwnershipExports(period:OwnershipPeriod,rows:List<OwnershipExpense>,imperial:Boolean) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var message by remember{mutableStateOf("")};var pending by remember{mutableStateOf<ByteArray?>(null)};var busy by remember{mutableStateOf(false)}
    fun save(uri:android.net.Uri?){val bytes=pending;pending=null;if(uri!=null&&bytes!=null)scope.launch{busy=true;message=runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:error("Cannot open export")};"Report saved"}.getOrElse{"Export failed: ${it.message}"};busy=false}}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"),::save)
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"),::save)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled=!busy,onClick={pending=OwnershipMath.csv(rows,imperial).toByteArray();csv.launch("RykerConnect-ownership-${LocalDate.now()}.csv")}){Text("Export CSV")}
        OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){OwnershipReport.pdf(period,rows,imperial)}}.onSuccess{pending=it;pdf.launch("RykerConnect-ownership-${LocalDate.now()}.pdf")}.onFailure{message="Export failed: ${it.message}"};busy=false}}){Text("Export PDF")}
    }
    if(message.isNotBlank())Text(message)
}

@Composable internal fun OwnershipSeasonEditor(id:String,onSaved:()->Unit) {
    val units by RideState.preferences.collectAsState();val original=remember(id){SoftwareStore.records("ownershipSeasons").firstOrNull{it.getString("id")==id}}
    val factor=if(units.imperial)1.609344 else 1.0
    var name by rememberSaveable(id){mutableStateOf(original?.getString("name")?:"${LocalDate.now().year} riding season")}
    var from by rememberSaveable(id){mutableStateOf(original?.getString("from")?:LocalDate.now().withDayOfYear(1).toString())}
    var to by rememberSaveable(id){mutableStateOf(original?.getString("to")?:LocalDate.now().toString())}
    var start by rememberSaveable(id){mutableStateOf(original?.optDouble("startKm")?.takeIf{it.isFinite()}?.div(factor)?.toString()?:"")}
    var end by rememberSaveable(id){mutableStateOf(original?.optDouble("endKm")?.takeIf{it.isFinite()}?.div(factor)?.toString()?:"")}
    var error by remember{mutableStateOf("")}
    Text("Name a season or reporting period",style=MaterialTheme.typography.titleLarge)
    OwnershipField("Name",name){name=it.take(80)};OwnershipField("From (YYYY-MM-DD)",from){from=it};OwnershipField("Through (YYYY-MM-DD)",to){to=it}
    Text("Optional: confirm the vehicle's actual odometer at the start and end of these dates. Leave blank if unknown. For a season in progress, use the date of your latest confirmed reading as Through.")
    OwnershipField("Start odometer (${if(units.imperial)"mi" else "km"})",start,true){start=it};OwnershipField("End odometer (${if(units.imperial)"mi" else "km"})",end,true){end=it}
    Button(onClick={runCatching{
        fun km(s:String):Any=if(s.isBlank())JSONObject.NULL else (s.toDoubleOrNull()?.times(factor)?:error("Enter valid mileage"))
        val record=JSONObject().put("id",original?.getString("id")?:UUID.randomUUID().toString()).put("name",name.trim()).put("from",from).put("to",to).put("startKm",km(start)).put("endKm",km(end));OwnershipSeasons.validate(record)
        SoftwareStore.mutate{data->val path=listOf("ownershipSeasons",record.getString("id"));require(SyncJson.fingerprint(LibraryData.softwareValue(data,path))==SyncJson.fingerprint(original)){"Season changed; reopen before saving"};LibraryData.setSoftwareValue(data,path,record)};onSaved()
    }.onFailure{error=it.message?:"Could not save season"}}){Text("Save season")}
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
}
