package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.json.JSONObject

object BackupPreferences {
    val appearanceKeys=setOf("theme","mode","dayTheme","nightTheme","dayHour","nightHour")
    val layoutKeys=setOf("dashboardOrder","drivingField","garageGps","garageMpg")+DashboardLayout.defaults.flatMap{listOf("hide$it","collapse$it")}
    fun capture(context:Context):JSONObject {
        val appearance=JSONObject()
        context.getSharedPreferences("appearance",Context.MODE_PRIVATE).all.forEach{(key,value)->if(key in appearanceKeys)appearance.put(key,value)}
        val layout=JSONObject();layoutKeys.forEach{key->SoftwareStore.value(key).takeIf{it.isNotBlank()}?.let{layout.put(key,it)}}
        return JSONObject().put("appearance",appearance).put("layout",layout)
    }
    fun validate(value:JSONObject) {
        val a=value.optJSONObject("appearance")?:JSONObject()
        require(a.keys().asSequence().all{it in appearanceKeys}){"Unknown appearance setting"}
        val names=de.chaostheorybot.rykerconnect.ui.theme.RideThemes.map{it.name}
        for(key in listOf("theme","dayTheme","nightTheme"))if(a.has(key))require(a.getString(key) in names)
        if(a.has("mode"))require(a.getString("mode") in listOf("Fixed","Phone","Schedule"))
        for(key in listOf("dayHour","nightHour"))if(a.has(key))require(a.getString(key).toIntOrNull() in 0..23)
        require(a.optString("dayHour","7")!=a.optString("nightHour","19"))
        val layout=value.optJSONObject("layout")?:JSONObject()
        require(layout.keys().asSequence().all{it in layoutKeys})
        layout.keys().forEach{require(layout.get(it) is String && layout.getString(it).length<=200)}
        if(value.has("dynamicColor"))require(value.get("dynamicColor") is Boolean)
    }
    fun apply(context:Context,value:JSONObject) {
        validate(value)
        value.optJSONObject("appearance")?.let{a->val editor=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit();appearanceKeys.forEach{editor.remove(it)};a.keys().forEach{editor.putString(it,a.getString(it))};editor.apply()}
        value.optJSONObject("layout")?.let{layout->val next=SoftwareStore.snapshot();layout.keys().forEach{next.put(it,layout.getString(it))};SoftwareStore.commit(next)}
    }
}
@Composable internal fun BackupReminderSettings() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("backup_status",Context.MODE_PRIVATE)}
    var enabled by remember{mutableStateOf(prefs.getBoolean("reminders",true))}
    var last by remember{mutableLongStateOf(prefs.getLong("last",0))}
    DisposableEffect(prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{p,_->last=p.getLong("last",0)};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
    Text(if(last==0L)"No completed backup recorded on this phone" else "Last successful backup: ${toolDate(last)}")
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Remind me in the app every 30 days",Modifier.weight(1f));Switch(enabled,{enabled=it;prefs.edit().putBoolean("reminders",it).apply()})}
    if(enabled && (last==0L || System.currentTimeMillis()-last>=30L*86400000))Text("Backup due · use Save backup below",color=MaterialTheme.colorScheme.primary)
}
@Composable internal fun BackupReminder(open:()->Unit) {
    val prefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("backup_status",Context.MODE_PRIVATE)
    if(prefs.getBoolean("reminders",true) && System.currentTimeMillis()-prefs.getLong("last",0)>=30L*86400000) {
        OutlinedButton(onClick=open){Text("Backup reminder · save your latest records")}
    }
}
