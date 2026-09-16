package de.chaostheorybot.rykerconnect.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class RideTheme(val name: String, val dark: Boolean, val accent: Color, val container: Color)
val RideThemes = listOf(
    RideTheme("Light", false, Color(0xFF006874), Color(0xFFCCEFF3)),
    RideTheme("Dark", true, Color(0xFF4FD8EB), Color(0xFF004F58)),
    RideTheme("Cyber Orange", true, Color(0xFFFFB078), Color(0xFF653614)),
    RideTheme("Heritage Yellow", true, Color(0xFFF3D65C), Color(0xFF514513)),
    RideTheme("Crimson Rush", true, Color(0xFFFFA6B1), Color(0xFF672C37)),
    RideTheme("Blue Abyss", true, Color(0xFF9CCAFF), Color(0xFF193F6D)),
    RideTheme("Goblin Green", true, Color(0xFFA2DC84), Color(0xFF2C4D23)),
    RideTheme("Purple Galaxy", true, Color(0xFFD6B3FF), Color(0xFF49306C))
)

/** Read once per minute and on preference changes; schedule follows local phone time. */
@Composable internal fun appearanceValues(): Map<String, *> {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("appearance",Context.MODE_PRIVATE)}
    var values by remember(prefs){mutableStateOf(prefs.all.toMap())}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{p,_->values=p.all.toMap()}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return values
}
internal fun isNightHour(hour:Int,day:Int,night:Int):Boolean = if(day<night) hour !in day until night else hour in night until day
internal fun resolveRideTheme(values:Map<String,*>,dark:Boolean,hour:Int):RideTheme? {
    val name=when(values["mode"] ?: "Fixed") {
        "Phone" -> values[if(dark) "nightTheme" else "dayTheme"] ?: if(dark) "Dark" else "Light"
        "Schedule" -> {
            val night=isNightHour(hour,(values["dayHour"] as? String)?.toIntOrNull()?:7,(values["nightHour"] as? String)?.toIntOrNull()?:19)
            values[if(night) "nightTheme" else "dayTheme"] ?: if(night)"Dark" else "Light"
        }
        else -> values["theme"]
    }
    return RideThemes.firstOrNull{it.name==name}
}
@Composable internal fun selectedRideTheme():RideTheme? {
    val values=appearanceValues()
    var hour by remember{mutableIntStateOf(java.time.LocalTime.now().hour)}
    LaunchedEffect(Unit){while(true){hour=java.time.LocalTime.now().hour;kotlinx.coroutines.delay(30_000)}}
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    return resolveRideTheme(values,dark,hour)
}

internal fun namedColors(theme: RideTheme): ColorScheme {
    if (!theme.dark) return LightColors
    return DarkColors.copy(primary = theme.accent, onPrimary = Color(0xFF13191D),
        primaryContainer = theme.container, onPrimaryContainer = theme.accent,
        secondary = theme.accent, secondaryContainer = theme.container, onSecondaryContainer = theme.accent,
        tertiary = theme.accent, surfaceTint = theme.accent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ThemePicker() {
    val context = LocalContext.current
    val selected = selectedRideTheme()
    var expanded by remember { mutableStateOf(false) }
    val current = selected ?: RideThemes[if(androidx.compose.foundation.isSystemInDarkTheme()) 1 else 0]
    Text("Choose your theme", style = MaterialTheme.typography.headlineSmall)
    Text("Eight finishes for your app. Your choice applies immediately and stays selected after restarting.")
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(current.name, {}, readOnly = true, label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) {
            RideThemes.forEach { theme -> DropdownMenuItem(text = { Text(theme.name) },
                leadingIcon = { Box(Modifier.size(20.dp).background(theme.accent, MaterialTheme.shapes.small)) },
                onClick = { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", theme.name).putString("mode","Fixed").apply(); expanded = false }) }
        }
    }
    if(selected == null) Text("Currently following your existing appearance setting. Choose a theme to pin its colors and light/dark mode.", style = MaterialTheme.typography.bodySmall)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RykerConnect", style = MaterialTheme.typography.headlineMedium)
            Text("Your next ride starts here.")
            Button(onClick = {}, enabled = false) { Text("Theme preview") }
        }
    }
    AutomaticThemes()
    Text("Color themes inspired by Ryker panel finishes. Light and Dark preserve the original app palettes.", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ThemeChoice(label:String,value:String,choices:List<String>,save:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(expanded,{expanded=!expanded}) {
        OutlinedTextField(value,{},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded,{expanded=false}){choices.forEach{choice->DropdownMenuItem(text={Text(choice)},onClick={save(choice);expanded=false})}}
    }
}
@Composable private fun AutomaticThemes() {
    val context=LocalContext.current;val values=appearanceValues()
    fun save(key:String,value:String){context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString(key,value).apply()}
    Text("Day & night",style=MaterialTheme.typography.titleLarge)
    val mode=values["mode"] as? String ?: "Fixed"
    ThemeChoice("Switching",mode,listOf("Fixed","Phone","Schedule")){save("mode",it)}
    if(mode!="Fixed") {
        ThemeChoice("Day theme",values["dayTheme"] as? String ?: "Light",RideThemes.map{it.name}){save("dayTheme",it)}
        ThemeChoice("Night theme",values["nightTheme"] as? String ?: "Dark",RideThemes.map{it.name}){save("nightTheme",it)}
        if(mode=="Schedule") {
            val day=values["dayHour"] as? String ?: "7";val night=values["nightHour"] as? String ?: "19"
            ThemeChoice("Day starts (local hour)",day,(0..23).map{it.toString()}.filter{it!=night}){save("dayHour",it)}
            ThemeChoice("Night starts (local hour)",night,(0..23).map{it.toString()}.filter{it!=day}){save("nightHour",it)}
        }
        Text(if(mode=="Phone")"Uses the phone's light/dark mode to select your two themes." else "Uses local phone time; changes apply while open and when you return.",style=MaterialTheme.typography.bodySmall)
    }
}
