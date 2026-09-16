package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

object OwnershipUpdates {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun init(context:Context) {
        WidgetAppearance.observe(context)
        scope.launch {
            combine(SoftwareStore.revision,RideState.preferences){_,_->Unit}.collect {
                runCatching{RykerWidgets.updateAll(context)}.onFailure{android.util.Log.e("RykerWidgets","Widget update failed",it)}
                runCatching{ServiceNotifications.schedule(context);ServiceNotifications.check(context)}.onFailure{android.util.Log.e("RykerReminders","Reminder update failed",it)}
            }
        }
    }
}

object ServiceNotifications {
    const val CHANNEL="maintenance"
    const val JOB=8201
    private fun prefs(c:Context)=c.getSharedPreferences("service_notification_state",Context.MODE_PRIVATE)
    fun settings()=SoftwareStore.snapshot().optJSONObject("reminders")?:JSONObject()
    fun alerts(now:Long=System.currentTimeMillis()):List<ServiceAlert> {
        val s=settings()
        return ServiceAlerts.calculate(SoftwareStore.snapshot(),now,s.optDouble("leadKm",if(RideState.preferences.value.imperial)482.8032 else 500.0),s.optInt("leadDays",30))
    }
    fun available(c:Context):Boolean {
        val manager=c.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun schedule(c:Context) {
        val scheduler=c.getSystemService(JobScheduler::class.java)
        if(!settings().optBoolean("enabled")) { scheduler.cancel(JOB);return }
        if(scheduler.getPendingJob(JOB)==null) {
            val result=scheduler.schedule(JobInfo.Builder(JOB,ComponentName(c,ServiceReminderJob::class.java)).setPeriodic(6*60*60*1000L).setPersisted(true).build())
            check(result==JobScheduler.RESULT_SUCCESS){"Android could not schedule service reminders"}
        }
    }
    @Synchronized fun check(c:Context) {
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Service reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val now=System.currentTimeMillis();val enabled=settings().optBoolean("enabled")
        val alerts=alerts(now);val p=prefs(c)
        // Retire notifications for disabled/deleted/completed services, without touching other app channels.
        manager.activeNotifications.filter{it.tag?.startsWith("service:")==true}.forEach { active ->
            val alert=alerts.firstOrNull{"service:"+it.id==active.tag}
            val state=JSONObject(p.getString(active.tag,"{}")?:"{}")
            if(!enabled || alert==null || !alert.approaching || state.optString("baseline")!=alert.baseline || state.optLong("snoozeUntil")>now)
                manager.cancel(active.tag,0)
        }
        if(!enabled){if(p.all.isNotEmpty())p.edit().clear().apply();return}
        if(!available(c) || (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED))return
        for(alert in alerts){
            val tag="service:"+alert.id;val state=JSONObject(p.getString(tag,"{}")?:"{}")
            if(!ServiceAlerts.shouldNotify(alert,state,now))continue
            val summary=describe(alert)
            val snooze=PendingIntent.getBroadcast(c,0,Intent(c,ServiceSnoozeReceiver::class.java).setData(android.net.Uri.parse("ryker-service:"+alert.id)).putExtra("serviceId",alert.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open=RideActionActivity.pending(c,"Log service/"+alert.id)
            val notice=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(alert.name+" · "+if(alert.overdue)"Service due" else "Coming up")
                .setContentText(summary).setStyle(NotificationCompat.BigTextStyle().bigText(summary+" Based on recorded mileage and service dates."))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open)
                .addAction(0,"Record service",open).addAction(0,"Snooze",snooze).build()
            manager.notify(tag,0,notice)
            p.edit().putString(tag,JSONObject().put("baseline",alert.baseline).put("signature",alert.signature).put("snoozeUntil",0).toString()).apply()
        }
    }
    fun describe(a:ServiceAlert):String = listOfNotNull(
        a.remainingKm?.let{if(it<=0)"Mileage due" else "In "+RideUnits.distance(it*1000,RideState.preferences.value.imperial)},
        a.dueTime?.let{"Date: "+toolDate(it)}
    ).joinToString(" · ")
    @Synchronized fun snooze(c:Context,id:String) {
        val alert=alerts().firstOrNull{it.id==id}?:return
        val until=System.currentTimeMillis()+settings().optInt("snoozeDays",7)*ServiceAlerts.DAY
        prefs(c).edit().putString("service:"+id,JSONObject().put("baseline",alert.baseline).put("signature",alert.signature).put("snoozeUntil",until).toString()).apply()
        c.getSystemService(NotificationManager::class.java).cancel("service:"+id,0)
    }
    fun snoozedUntil(c:Context,id:String):Long = JSONObject(prefs(c).getString("service:"+id,"{}")?:"{}").optLong("snoozeUntil")
    fun resume(c:Context,id:String) { prefs(c).edit().remove("service:"+id).apply();check(c) }
}

class ServiceSnoozeReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent){runCatching{ServiceNotifications.snooze(context,intent.getStringExtra("serviceId").orEmpty())}.onFailure{android.util.Log.e("RykerReminders","Could not snooze",it)}}
}
class ServiceReminderJob:JobService() {
    private var work:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        work=CoroutineScope(Dispatchers.IO).launch {
            val failed=runCatching{ServiceNotifications.check(this@ServiceReminderJob);RykerWidgets.updateAll(this@ServiceReminderJob)}.isFailure
            jobFinished(params,failed)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {work?.cancel();return true}
}

@Composable fun ServiceReminderSettings(open:(String)->Unit) {
    val context=LocalContext.current;val units by RideState.preferences.collectAsState()
    val s=remember{ServiceNotifications.settings()}
    var enabled by rememberSaveable{mutableStateOf(s.optBoolean("enabled"))}
    var km by rememberSaveable{mutableStateOf((s.optDouble("leadKm",if(units.imperial)482.8032 else 500.0)/(if(units.imperial)1.609344 else 1.0)).toString())}
    var days by rememberSaveable{mutableStateOf(s.optInt("leadDays",30).toString())}
    var snooze by rememberSaveable{mutableStateOf(s.optInt("snoozeDays",7).toString())}
    var message by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        refresh++
        if(it)runCatching{ServiceNotifications.check(context)}
        message=if(it)"Notifications allowed." else "Notifications are blocked. You can enable them in Android settings."
    }
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME){refresh++;runCatching{ServiceNotifications.check(context)}}
        }
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val revision by SoftwareStore.revision.collectAsState()
    val alerts=remember(revision,refresh){ServiceNotifications.alerts()}
    Text("Service reminders",style=MaterialTheme.typography.headlineSmall)
    Row(Modifier.fillMaxWidth().toggleable(enabled,role=Role.Switch,onValueChange={enabled=it})){Text("Enable service notifications",Modifier.weight(1f));Switch(enabled,null)}
    OwnershipField("Advance warning (${if(units.imperial)"miles" else "km"})",km,true){km=it}
    OwnershipField("Advance warning (days)",days,true){days=it}
    OwnershipField("Snooze duration (days)",snooze,true){snooze=it}
    Text("Checks run after saved changes and periodically in the background. Android may delay delivery. Mileage uses your latest recorded odometer.")
    Button(onClick={runCatching{
        val next=JSONObject().put("enabled",enabled).put("leadKm",(km.replace(',','.').toDoubleOrNull()?:error("Enter a distance"))*(if(units.imperial)1.609344 else 1.0))
            .put("leadDays",days.toIntOrNull()?:error("Enter whole days")).put("snoozeDays",snooze.toIntOrNull()?:error("Enter whole snooze days"))
        ServiceAlerts.validate(next);SoftwareStore.commit(SoftwareStore.snapshot().put("reminders",next))
        ServiceNotifications.schedule(context);ServiceNotifications.check(context);refresh++
        message=if(enabled && !ServiceNotifications.available(context))"Saved. Allow notifications in Android to receive reminders." else "Reminder settings saved"
        if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }.onFailure{message=it.message?:"Could not save reminders"}}){Text("Save reminder settings")}
    TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Android notification settings")}
    if(message.isNotBlank())Text(message)
    Text("Current maintenance",style=MaterialTheme.typography.titleLarge)
    if(alerts.isEmpty())Text("Set service intervals and record a completed service to start reminders.")
    alerts.forEach{alert->ToolCard {
        Text(alert.name+" · "+if(alert.overdue)"Due" else if(alert.approaching)"Coming up" else "Scheduled")
        Text(ServiceNotifications.describe(alert))
        val until=ServiceNotifications.snoozedUntil(context,alert.id)
        if(until>System.currentTimeMillis())Text("Snoozed until "+toolDate(until))
        TextButton(onClick={open("Log service/"+alert.id)}){Text("Record completed service")}
        TextButton(onClick={runCatching{if(until>System.currentTimeMillis())ServiceNotifications.resume(context,alert.id) else ServiceNotifications.snooze(context,alert.id);refresh++}.onFailure{message="Could not change snooze"}}){Text(if(until>System.currentTimeMillis())"Resume reminders" else "Snooze reminder")}
    }}
}
