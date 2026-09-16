package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONObject

data class WidgetPalette(val surface:Int,val tile:Int,val accent:Int,val text:Int,val secondary:Int)
object WidgetAppearance {
    private var listener:SharedPreferences.OnSharedPreferenceChangeListener?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var dynamic=false
    fun observe(context:Context){
        if(listener!=null)return
        listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->refresh(context)}
        context.getSharedPreferences("appearance",Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        scope.launch{RykerConnectStore(context).getDynamicColorToken.collect{dynamic=it;refresh(context)}}
        scope.launch{
            var previous=""
            while(isActive){
                val signature=palette(context,SoftwareStore.snapshot().optJSONObject("widgetOptions")?:JSONObject()).toString()
                if(signature!=previous){previous=signature;refresh(context)}
                delay(30_000)
            }
        }
    }
    private fun refresh(c:Context){scope.launch{runCatching{RykerWidgets.updateAll(c)}.onFailure{android.util.Log.e("RykerWidgets","Theme refresh failed",it)}}}
    fun palette(c:Context,settings:JSONObject):WidgetPalette{
        val systemDark=c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val mode=settings.optString("theme","App")
        val selected=if(mode=="App") resolveRideTheme(c.getSharedPreferences("appearance",Context.MODE_PRIVATE).all,systemDark,java.time.LocalTime.now().hour) else null
        val dark=when(mode){"Light"->false;"Dark"->true;else->selected?.dark?:systemDark}
        val colors=when{
            selected!=null->namedColors(selected)
            mode=="App" && dynamic->if(dark)dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
            dark->DarkColors
            else->LightColors
        }
        return WidgetPalette(colors.surface.toArgb(),colors.primaryContainer.toArgb(),colors.primary.toArgb(),colors.onSurface.toArgb(),colors.onSurfaceVariant.toArgb())
    }
}
