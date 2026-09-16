package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.util.Locale

@Composable fun MaintenancePlannerScreen(open:(String)->Unit){
    val units by RideState.preferences.collectAsState()
    val revision by SoftwareStore.revision.collectAsState()
    var tab by rememberSaveable{mutableStateOf("Due / upcoming")}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var message by remember{mutableStateOf("")}
    val plans=remember(revision){SoftwareStore.records("plans")}
    val catalog=remember(revision){ServiceCatalog.items(SoftwareStore.snapshot())}
    fun name(p:JSONObject)=catalog.firstOrNull{it.optString("id")==p.optString("serviceId")}?.optString("name")?:"Unavailable service"
    fun change(p:JSONObject){runCatching{MaintenancePlans.save(p)}.onFailure{message=it.message?:"Could not save plan"}}
    Text("Maintenance planner",style=MaterialTheme.typography.headlineSmall)
    Text("Prepare the work here. Record completed work in My Garage.")
    if(selected!=null){
        val p=plans.firstOrNull{it.optString("id")==selected}
        TextButton(onClick={selected=null}){Text("All plans")}
        if(p==null){Text("Plan unavailable");return}
        key(selected){PlanEditor(p,name(p),catalog.firstOrNull{it.optString("id")==p.optString("serviceId")}?.optBoolean("enabled")==true,::change,open)}
    }else{
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(t in listOf("Due / upcoming","Planned work","Parts to buy"))FilterChip(tab==t,{tab=t},{Text(t)})}
        when(tab){
            "Due / upcoming"->{
                val alerts=ServiceNotifications.alerts()
                if(alerts.isEmpty())Text("No approaching reminders. Review your schedules or create a plan below.")

                val odo=(SoftwareStore.records("fuel")+SoftwareStore.records("maintenance")).maxByOrNull{it.optDouble("odometerKm")}
                Text(odo?.let{"Odometer last recorded: "+toolDate(it.optLong("time"))}?:"No recorded odometer. Mileage forecasts are unavailable.")
                for(s in catalog.filter{it.optBoolean("enabled") && it.optString("id")!=ServiceCatalog.MILEAGE}.sortedBy { s -> val a=alerts.firstOrNull{it.id==s.optString("id")};when{a?.overdue==true->0;a?.approaching==true->1;a!=null->2;else->3} })ToolCard{
                    Text(s.getString("name"),style=MaterialTheme.typography.titleMedium)
                    alerts.firstOrNull{it.id==s.optString("id")}?.let{a->Text(if(a.overdue)"Overdue" else if(a.approaching)"Approaching" else "Scheduled",color=if(a.overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}
                    val last=SoftwareStore.records("maintenance").filter{it.optString("serviceId")==s.getString("id")}.maxByOrNull{it.optLong("time")}
                    val km=s.optDouble("intervalKm");val days=s.optInt("intervalDays")
                    Text(if(km<=0 && days<=0)"Recurring interval not configured" else if(last==null)"Record the last completed service to establish a baseline" else buildString{
                        if(km>0)append("Distance remaining: "+RideUnits.distance((last.optDouble("odometerKm")+km-(odo?.optDouble("odometerKm")?:last.optDouble("odometerKm")))*1000,units.imperial))
                        if(days>0)append("\nDue: "+toolDate(last.optLong("time")+days*86400000L))
                    })
                    TextButton(onClick={runCatching{selected=MaintenancePlans.create(s.getString("id"))}.onFailure{message=it.message?:"Could not create plan"}}){Text("Plan this service")}
                }
                OutlinedButton(onClick={open("Services & intervals")}){Text("Edit services & intervals")}
            }
            "Planned work"->{
                if(plans.isEmpty())Text("Choose a service under Due / upcoming to plan your first job.")
                for(p in plans.sortedBy{it.optString("target").ifBlank{"9999"}})ToolCard{
                    Text(name(p),style=MaterialTheme.typography.titleMedium)
                    Text(p.getString("state")+p.optString("target").takeIf{it.isNotBlank()}.let{if(it==null)" · No target date" else " · Target $it"})
                    if(p.optString("state")=="Completed" && !MaintenancePlans.completionExists(p))Text("Completion record missing · review needed",color=MaterialTheme.colorScheme.error)
                    Text(String.format(Locale.US,"Estimated parts: $%.2f",MaintenancePlans.estimate(p)))
                    TextButton(onClick={selected=p.getString("id")}){Text("Open plan")}
                }
            }
            else->{
                val active=plans.filter{it.optString("state") in listOf("Planned","In progress")}
                Text(String.format(Locale.US,"Estimated total: $%.2f",active.sumOf{MaintenancePlans.estimate(it)}))
                if(active.isEmpty())Text("Parts from your open plans will appear here.")
                for(p in active)ToolCard{Text(name(p),style=MaterialTheme.typography.titleMedium);for(part in MaintenancePlans.rows(p,"parts"))Row{
                    Checkbox(part.optBoolean("done"),{part.put("done",it);change(p)})
                    Text("${part.getString("name")} × ${part.getDouble("quantity")} · $${part.getDouble("cost")} each",Modifier.weight(1f))
                };TextButton(onClick={selected=p.getString("id")}){Text("Open plan")}}
            }
        }
    }
    if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
}

@Composable private fun PlanEditor(p:JSONObject,name:String,enabled:Boolean,save:(JSONObject)->Unit,open:(String)->Unit){
    var target by rememberSaveable{mutableStateOf(p.optString("target"))};var notes by rememberSaveable{mutableStateOf(p.optString("notes"))}
    var task by rememberSaveable{mutableStateOf("")};var part by rememberSaveable{mutableStateOf("")}
    var quantity by rememberSaveable{mutableStateOf("1")};var cost by rememberSaveable{mutableStateOf("0")}
    var error by remember{mutableStateOf("")}
    val editable=p.getString("state") in listOf("Planned","In progress")
    Text(name,style=MaterialTheme.typography.titleLarge);Text(p.getString("state"))
    if(!enabled){Text("Service disabled or missing. Review Settings before recording completion.");TextButton(onClick={open("Services & intervals")}){Text("Open service settings")}}
    if(p.getString("state")=="Completed" && !MaintenancePlans.completionExists(p)){
        Text("The linked Garage record is missing. Review this plan.")
        OutlinedButton(onClick={p.put("state","Planned").remove("completionId");save(p)}){Text("Reopen plan")}
    }
    OutlinedTextField(target,{target=it.take(10)},label={Text("Target date (YYYY-MM-DD, optional)")},modifier=Modifier.fillMaxWidth(),enabled=editable)
    OutlinedTextField(notes,{notes=it.take(2000)},label={Text("Planning notes")},modifier=Modifier.fillMaxWidth(),enabled=editable)
    Button(enabled=editable,onClick={runCatching{if(target.isNotBlank())java.time.LocalDate.parse(target);p.put("target",target).put("notes",notes);save(p);error="Plan saved"}.onFailure{error="Use YYYY-MM-DD or leave the date blank"}}){Text("Save plan")}
    for(key in listOf("tasks","parts")){
        Text(if(key=="tasks")"Preparation checklist" else "Parts & supplies",style=MaterialTheme.typography.titleMedium)
        for(item in MaintenancePlans.rows(p,key))Row(Modifier.fillMaxWidth()){
            Checkbox(item.optBoolean("done"),{item.put("done",it);save(p)},enabled=editable)
            Text(item.getString("name")+if(key=="parts")" × ${item.getDouble("quantity")} · $${item.getDouble("cost")} each" else "",Modifier.weight(1f))
            if(editable)TextButton(onClick={p.put(key,JSONArray(MaintenancePlans.rows(p,key).filter{it.getString("id")!=item.getString("id")}));save(p)}){Text("Remove")}
        }
    }
    if(editable){
        OutlinedTextField(task,{task=it.take(200)},label={Text("New checklist item")},modifier=Modifier.fillMaxWidth())
        TextButton(enabled=task.isNotBlank(),onClick={p.getJSONArray("tasks").put(JSONObject().put("id",UUID.randomUUID()).put("name",task.trim()).put("done",false));save(p);task=""}){Text("Add checklist item")}
        OutlinedTextField(part,{part=it.take(200)},label={Text("Part or supply")},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(quantity,{quantity=it.take(10)},label={Text("Quantity")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))
            OutlinedTextField(cost,{cost=it.take(10)},label={Text("Unit cost (USD)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))
        }
        TextButton(enabled=part.isNotBlank(),onClick={runCatching{
            val q=quantity.toDouble();val c=cost.toDouble();require(q in 0.01..10000.0 && c in 0.0..100000.0)
            p.getJSONArray("parts").put(JSONObject().put("id",UUID.randomUUID()).put("name",part.trim()).put("done",false).put("quantity",q).put("cost",c));save(p);part=""
        }.onFailure{error="Enter a positive quantity and a valid cost"}}){Text("Add part")}
        Text("Parts estimates are not Garage spending. Confirm actual cost when recording the service.")
        OutlinedButton(onClick={p.put("state",if(p.getString("state")=="Planned")"In progress" else "Planned");save(p)}){Text(if(p.getString("state")=="Planned")"Start work" else "Back to planned")}
        Button(enabled=enabled,onClick={runCatching{if(target.isNotBlank())java.time.LocalDate.parse(target);p.put("target",target).put("notes",notes);MaintenancePlans.save(p);open("Complete plan/"+p.getString("id"))}.onFailure{error=it.message?:"Save a valid plan first"}}){Text("Record completed service")}
        var cancel by remember{mutableStateOf(false)}
        TextButton(onClick={cancel=true}){Text("Cancel plan")}
        if(cancel)AlertDialog(onDismissRequest={cancel=false},title={Text("Cancel this plan?")},text={Text("Its history remains. The service reminder stays active.")},confirmButton={TextButton(onClick={p.put("state","Canceled");save(p);cancel=false}){Text("Cancel plan")}},dismissButton={TextButton(onClick={cancel=false}){Text("Keep plan")}})
    }
    if(error.isNotBlank())Text(error)
}
