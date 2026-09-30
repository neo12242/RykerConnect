package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/** Presentation-only scenario: never writes RideState, TripStore, parking or Bluetooth. */
@Composable internal fun GuidedDemo() {
    if (!de.chaostheorybot.rykerconnect.BuildConfig.DEMO_FEATURES) {
        Text("Demo features are unavailable in this build.")
        return
    }
    var running by rememberSaveable { mutableStateOf(false) }
    var fraction by rememberSaveable { mutableFloatStateOf(0f) }
    var rate by rememberSaveable { mutableIntStateOf(1) }
    val units by RideState.preferences.collectAsState()
    val d=remember { DemoTrips.details.first() }
    val latestFraction by rememberUpdatedState(fraction)
    LaunchedEffect(running,rate){if(running)while(true){delay(1000);fraction=(latestFraction+rate/180f).coerceAtMost(1f);if(fraction>=1f){running=false;break}}}
    val index=(fraction*d.track.lastIndex).toInt().coerceIn(d.track.indices)
    val p=d.track[index]
    val traveled=remember(index){TripAnalysis.stats(d.track.take(index+1),d.summary.started,p.time,true).meters}
    val phase=when {fraction<.2f->"Leaving town";fraction<.42f->"City streets";fraction<.65f->"Coffee stop";fraction<.9f->"Heading home";else->"Ride complete"}
    Text("GUIDED DEMO · Synthetic ride",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    Text("Explore the app without hardware",style=MaterialTheme.typography.headlineSmall)
    Text("This three-minute preview changes navigation, music, sensors and ride progress together. It never records a real trip or changes Last Parked.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={if(fraction>=1f)fraction=0f;running=!running}){Text(if(running)"Pause demo" else "Start demo")}
        OutlinedButton(onClick={running=false;fraction=0f}){Text("Reset demo")}
        FilterChip(rate==1,{rate=1},{Text("1×")});FilterChip(rate==3,{rate=3},{Text("3×")})
    }
    Slider(fraction,{fraction=it},modifier=Modifier.fillMaxWidth())
    Text("${(fraction*100).toInt()}% · $phase",style=MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide=maxWidth>=650.dp && androidx.compose.ui.platform.LocalConfiguration.current.fontScale<=1.3f
        val w=if(wide)(maxWidth-16.dp)/2 else maxWidth
        FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(w-1.dp)){ToolCard {
                Text("Music",style=MaterialTheme.typography.titleMedium)
                Text(listOf("Open road","Coastal afternoon","Homeward bound")[(fraction*2.99f).toInt()],style=MaterialTheme.typography.titleLarge)
                Text("Demo soundtrack · ${if(running)"Playing" else "Paused"}")
                LinearProgressIndicator(progress={((fraction*3)%1f)},modifier=Modifier.fillMaxWidth())
            }}
            Column(Modifier.width(w-1.dp)){ToolCard {
                Text("Driving",style=MaterialTheme.typography.titleMedium)
                Text(if(fraction>=1f)"Arrived" else if((p.speed?:0.0)<1.0)"Stopped for coffee" else if(index%80<40)"Turn right at the next street" else "Continue straight",style=MaterialTheme.typography.titleLarge)
                Text("${RideUnits.distance(traveled,units.imperial)} · ${TripSummary.duration(p.time-d.summary.started)}")
                Text(String.format(Locale.US,"%.1f %s",(p.speed?:0.0)*(if(units.imperial)2.236936 else 3.6),if(units.imperial)"mph" else "km/h"))
            }}
            Column(Modifier.width(w-1.dp)){ToolCard {
                Text("Outdoor sensor · SIMULATED",style=MaterialTheme.typography.titleMedium)
                val temp=16+2*kotlin.math.sin(fraction*6.28)
                Text(String.format(Locale.US,"%.1f°%s · %d%% humidity",if(units.fahrenheit)temp*1.8+32 else temp,if(units.fahrenheit)"F" else "C",(50+fraction*8).toInt()))
                Text("1013 hPa · sample updates with the demo")
            }}
            Column(Modifier.width(w-1.dp)){ToolCard{Text("GPS weather · DEMO",style=MaterialTheme.typography.titleMedium);Text("Partly cloudy · Light wind");Text("No weather request or phone GPS needed")}}
        }
    }
    TripMap(d.track,p)
    Text("Fold or unfold the virtual phone, rotate it, or switch tabs. Your demo position and controls are retained.",style=MaterialTheme.typography.bodySmall)
}
