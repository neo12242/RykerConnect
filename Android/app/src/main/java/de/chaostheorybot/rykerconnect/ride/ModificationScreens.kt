package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

@Composable internal fun ModificationsScreen(open:(String)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val revision by SoftwareStore.revision.collectAsState();val status by ModificationSync.status.collectAsState();val conflicts by ModificationSync.conflicts.collectAsState()
    val mods=remember(revision){SoftwareStore.records("modifications").sortedBy{it.getString("title").lowercase()}}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var archived by rememberSaveable{mutableStateOf(false)}
    var choice by remember{mutableStateOf<Pair<String,Boolean>?>(null)}
    LaunchedEffect(Unit){ModificationSync.loadConflicts(context)}
    Text("Modifications",style=MaterialTheme.typography.headlineSmall)
    Text("Track purchases here. Write articles and manage photos on DadRides.")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={open("Modification/new")}){Text("Add modification")}
        OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){ModificationSync.sync(context)}}.onFailure{message=it.message?:"Sync unavailable"};busy=false}}){Text(if(busy)"Working…" else "Sync now")}
    }
    Text(status,style=MaterialTheme.typography.bodySmall)
    val last=ModificationSync.last(context);if(last>0)Text("Last successful sync · ${toolDate(last)}",style=MaterialTheme.typography.bodySmall)
    if(message.isNotBlank())Text(message)
    TextButton(onClick={archived=!archived}){Text(if(archived)"Hide archived" else "Show archived")}
    if(mods.isEmpty())Text("No modifications yet. Add one or sync your existing DadRides drafts. Prices stay private.")
    for(m in mods.filter{archived || !it.getBoolean("archived")})ToolCard {
        val id=m.getString("id");val expenses=ModificationRecords.rows(m)
        Text(m.getString("title"),style=MaterialTheme.typography.titleLarge)
        Text(m.getString("status")+if(m.getBoolean("archived"))" · Archived" else "")
        Text("Recorded: ${OwnershipMath.money(expenses.sumOf{ModificationRecords.cents(it,"amountCents")?:0})}")
        Text("Estimate: "+(ModificationRecords.cents(m,"estimateCents")?.let(OwnershipMath::money)?:"Not entered"))
        if(expenses.any{it.isNull("amountCents")})Text("${expenses.count{it.isNull("amountCents")}} purchase prices not entered")
        TextButton(onClick={open("Modification/$id")}){Text("Edit private details & purchases")}
        OutlinedButton(enabled=!busy,onClick={scope.launch{
            busy=true;runCatching{withContext(Dispatchers.IO){ModificationSync.sync(context);check(!ModificationSync.conflicts.value.containsKey(id)){"Resolve this modification's conflict first"};ModificationSync.editorLink(id)}}
                .onSuccess{runCatching{ModificationSync.openEditor(context,it)}.onFailure{message="Browser unavailable: ${it.message}"}}
                .onFailure{message=it.message?:"Could not open editor"};busy=false
        }}){Text("Edit article & photos on DadRides")}
        conflicts[id]?.let{remote->
            HorizontalDivider();Text("Changed in both places",style=MaterialTheme.typography.titleMedium)
            fun summary(doc:JSONObject):String = buildString {
                append(doc.getString("title"));append(" · "+doc.getString("status"));append("\nEstimate: "+(ModificationRecords.cents(doc,"estimateCents")?.let(OwnershipMath::money)?:"Not entered"))
                append("\nInstalled: "+doc.getString("installedDate").ifBlank{"Not entered"});append("\nMileage (km): "+if(doc.isNull("installedKm"))"Not entered" else doc.getDouble("installedKm"));append("\nArchived: "+doc.getBoolean("archived"))
                ModificationRecords.rows(doc).forEach{append("\n${it.getString("date")} · ${it.getString("label")} · ${ModificationRecords.cents(it,"amountCents")?.let(OwnershipMath::money)?:"Unknown cost"}")}
                append("\n"+doc.getString("notes"))
            }
            Text("This app\n"+summary(m));Text("Website\n"+summary(remote.getJSONObject("document")))
            TextButton(onClick={choice=id to false}){Text("Use app version")};TextButton(onClick={choice=id to true}){Text("Use website version")}
        }
    }
    choice?.let{(id,website)->AlertDialog(onDismissRequest={choice=null},title={Text("Resolve modification conflict?")},text={Text("Use the reviewed ${if(website)"website" else "app"} version for this modification? The other version's fields will be replaced after syncing.")},confirmButton={TextButton(onClick={runCatching{ModificationSync.resolve(context,id,website);message="Choice saved. Tap Sync now to finish."}.onFailure{message=it.message?:"Could not resolve"};choice=null}){Text("Use selected version")}},dismissButton={TextButton(onClick={choice=null}){Text("Cancel")}})}
}

@Composable internal fun ModificationEditor(id:String,onSaved:()->Unit) {
    val units by RideState.preferences.collectAsState();val factor=if(units.imperial)1.609344 else 1.0
    val original=remember(id){SoftwareStore.records("modifications").firstOrNull{it.getString("id")==id}}
    val initial=remember(id){original?:ModificationRecords.create()}
    var draft by rememberSaveable(id){mutableStateOf(initial.toString())}
    var estimate by rememberSaveable(id){mutableStateOf(ModificationRecords.cents(initial,"estimateCents")?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}?:"")}
    var mileage by rememberSaveable(id){mutableStateOf(initial.optDouble("installedKm").takeIf{it.isFinite()}?.div(factor)?.toString()?:"")}
    var expenseText by rememberSaveable(id){mutableStateOf(JSONObject().also{out->ModificationRecords.rows(initial).forEach{e->out.put(e.getString("id"),ModificationRecords.cents(e,"amountCents")?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}?:"")}}.toString())}
    var message by remember{mutableStateOf("")};var remove by remember{mutableStateOf<String?>(null)}
    val m=JSONObject(draft)
    fun change(key:String,value:Any){draft=JSONObject(draft).put(key,value).toString()}
    fun expenseChange(expenseId:String,key:String,value:String){val next=JSONObject(draft);ModificationRecords.rows(next).first{it.getString("id")==expenseId}.put(key,value);draft=next.toString()}
    Text(if(original==null)"Add modification" else "Private modification details",style=MaterialTheme.typography.titleLarge)
    OwnershipField("Name",m.getString("title")){change("title",it.take(100))}
    var menu by remember{mutableStateOf(false)}
    Box {OutlinedButton(onClick={menu=true}){Text(m.getString("status")+" ▾")};DropdownMenu(menu,{menu=false}){ModificationRecords.states.forEach{s->DropdownMenuItem(text={Text(s)},onClick={change("status",s);menu=false})}}}
    OwnershipField("Estimated total (USD, optional)",estimate,true){estimate=it}
    OwnershipField("Installation date (YYYY-MM-DD, optional)",m.getString("installedDate")){change("installedDate",it)}
    OwnershipField("Installation odometer (${if(units.imperial)"mi" else "km"}, optional)",mileage,true){mileage=it}
    OutlinedTextField(m.getString("notes"),{change("notes",it.take(2000))},label={Text("Private notes")},modifier=Modifier.fillMaxWidth())
    Row {Checkbox(m.getBoolean("archived"),{change("archived",it)});Text("Archived · expenses stay in totals")}
    Text("Actual purchases",style=MaterialTheme.typography.titleLarge)
    Text("Add each payment once. Installation adds no expense automatically. Blank amount means unknown; 0 means a known free item.")
    for(e in ModificationRecords.rows(m))ToolCard {
        val eid=e.getString("id")
        OwnershipField("Purchase date (YYYY-MM-DD)",e.getString("date")){expenseChange(eid,"date",it)}
        OwnershipField("Description",e.getString("label")){expenseChange(eid,"label",it.take(120))}
        OwnershipField("Amount (USD, optional)",JSONObject(expenseText).optString(eid),true){expenseText=JSONObject(expenseText).put(eid,it).toString()}
        TextButton(onClick={remove=eid}){Text("Remove purchase")}
    }
    TextButton(onClick={val next=JSONObject(draft);val e=JSONObject().put("id",UUID.randomUUID().toString()).put("date",LocalDate.now().toString()).put("label","").put("amountCents",JSONObject.NULL);next.getJSONArray("expenses").put(e);draft=next.toString()}){Text("Add purchase")}
    Button(onClick={runCatching{
        val record=JSONObject(draft).put("title",m.getString("title").trim()).put("estimateCents",ModificationRecords.parseMoney(estimate)?:JSONObject.NULL)
        record.put("installedKm",if(mileage.isBlank())JSONObject.NULL else mileage.toDoubleOrNull()?.times(factor)?:error("Enter valid mileage"))
        ModificationRecords.rows(record).forEach{e->e.put("amountCents",ModificationRecords.parseMoney(JSONObject(expenseText).optString(e.getString("id")))?:JSONObject.NULL)}
        ModificationRecords.save(record,original?.let(SyncJson::fingerprint));onSaved()
    }.onFailure{message=it.message?:"Could not save"}}){Text("Save private details")}
    if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
    remove?.let{eid->AlertDialog(onDismissRequest={remove=null},title={Text("Remove purchase?")},text={Text("This expense will be removed from this draft and spending totals when you save.")},confirmButton={TextButton(onClick={val next=JSONObject(draft);next.put("expenses",JSONArray(ModificationRecords.rows(next).filter{it.getString("id")!=eid}));draft=next.toString();remove=null}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
}
