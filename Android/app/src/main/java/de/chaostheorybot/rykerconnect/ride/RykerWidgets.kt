package de.chaostheorybot.rykerconnect.ride

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.chaostheorybot.rykerconnect.R
import de.chaostheorybot.rykerconnect.MainActivity
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import org.json.JSONObject

class RideActionActivity:ComponentActivity() {
    companion object {
        fun pending(c:Context,page:String):PendingIntent=PendingIntent.getActivity(c,0,
            Intent(c,RideActionActivity::class.java).setData(android.net.Uri.parse("ryker-action:"+android.net.Uri.encode(page))).putExtra("page",page),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val initial=intent.getStringExtra("page")?:"Vehicle profile"
        setContent { RykerConnectTheme { Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            var page by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(initial)}
            Column {
                PageBack(page.substringBefore('/')){finish()}
                ScrollPage(null,null){
                    when {
                        page in listOf("Add mileage","Add fuel","Add service") || page.startsWith("Log service/") || page.startsWith("Complete plan/") -> GarageEntry(page){finish()}
                        page.startsWith("Summary/") -> RideSummaryScreen(page.substringAfter("/")){page=it}
                        page.startsWith("Journal/") -> RideJournalScreen(page.substringAfter("/"))
                        page=="Maintenance planner" -> MaintenancePlannerScreen{page=it}
                        page=="Add-ons" -> DadRidesSettings()
                        page=="Last Parked" -> ParkingTools()
                        page=="Service reminders" -> ServiceReminderSettings{page=it}
                        page=="Widgets" -> WidgetSettings()
                        page=="Navigate home" -> {
                            Text("Navigate home",style=MaterialTheme.typography.headlineSmall)
                            var message by remember{mutableStateOf("")}
                            if(SoftwareStore.snapshot().optJSONObject("vehicle")?.optString("home").isNullOrBlank()){
                                Text("Set a home address to use this shortcut.")
                                VehicleProfileScreen()
                            }
                            Button(onClick={runCatching{navigateHome(this@RideActionActivity)}.onFailure{message=it.message?:"Could not open Maps"}}){Text("Open Google Maps")}
                            if(message.isNotBlank())Text(message)
                        }
                        else -> VehicleProfileScreen()
                    }
                    TextButton(onClick={startActivity(Intent(this@RideActionActivity,MainActivity::class.java));finish()}){Text("Open RykerConnect")}
                }
            }
        } } }
    }
}

open class RykerWidgetProvider:AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ids.forEach{RykerWidgets.update(c,m,it)}}
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,options:Bundle){RykerWidgets.update(c,m,id)}
}
class QuickActionsWidget:RykerWidgetProvider()
class MyRykerWidget:RykerWidgetProvider()
class LastParkedWidget:RykerWidgetProvider()

object RykerWidgets {
    val providers=listOf(QuickActionsWidget::class.java,MyRykerWidget::class.java,LastParkedWidget::class.java)
    fun validate(s:JSONObject){
        require(s.optString("theme","App") in listOf("App","System","Light","Dark"))
        if(s.has("hideDetails"))require(s.get("hideDetails") is Boolean)
    }
    fun updateAll(c:Context){
        val manager=AppWidgetManager.getInstance(c)
        providers.forEach{p->manager.getAppWidgetIds(ComponentName(c,p)).forEach{update(c,manager,it)}}
    }
    fun update(c:Context,m:AppWidgetManager,id:Int)=ResponsiveWidgets.update(c,m,id)
}

@Composable fun WidgetSettings(){
    val context=LocalContext.current;val revision by SoftwareStore.revision.collectAsState()
    val s=remember(revision){SoftwareStore.snapshot().optJSONObject("widgetOptions")?:JSONObject()}
    var message by remember{mutableStateOf("")}
    fun save(next:JSONObject){runCatching{RykerWidgets.validate(next);SoftwareStore.commit(SoftwareStore.snapshot().put("widgetOptions",next));RykerWidgets.updateAll(context)}.onFailure{message="Could not save widget settings"}}
    Text("Home-screen widgets",style=MaterialTheme.typography.headlineSmall)
    Text("Add a widget below, or long-press your Android home screen and choose Widgets → RykerConnect. Resize it using the launcher's handles.")
    listOf("Quick actions","My Ryker","Last Parked").forEachIndexed{index,name->
        OutlinedButton(onClick={
            val manager=AppWidgetManager.getInstance(context)
            message=if(manager.isRequestPinAppWidgetSupported){
                if(manager.requestPinAppWidget(ComponentName(context,RykerWidgets.providers[index]),null,null))"Confirm Add on your home screen." else "Use the home-screen widget picker."
            }else "This launcher requires adding widgets from its widget picker."
        }){Text("Add $name widget")}
    }
    Text("Appearance (all Ryker widgets)",style=MaterialTheme.typography.titleMedium)
    listOf("App","System","Light","Dark").forEach{theme->Row(Modifier.fillMaxWidth().toggleable(s.optString("theme","App")==theme,role=Role.RadioButton,onValueChange={save(JSONObject(s.toString()).put("theme",theme))})){RadioButton(s.optString("theme","App")==theme,null);Text(if(theme=="App")"Follow app theme" else theme)}}
    Row(Modifier.fillMaxWidth().toggleable(s.optBoolean("hideDetails"),role=Role.Switch,onValueChange={save(JSONObject(s.toString()).put("hideDetails",it))})){Text("Hide vehicle and parking details",Modifier.weight(1f));Switch(s.optBoolean("hideDetails"),null)}
    Text("Follow app theme uses your named theme and day/night settings. Parking maps are cached snapshots; tap to open the app. A new location never shows an old map. Privacy mode hides the map too. VIN and home address never appear.")
    if(message.isNotBlank())Text(message)
}
