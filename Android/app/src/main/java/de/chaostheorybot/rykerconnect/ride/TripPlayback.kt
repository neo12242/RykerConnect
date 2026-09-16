package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import java.util.Locale

object ReplayMath {
    fun point(points:List<TrackPoint>, time:Long):TrackPoint? {
        val index=points.binarySearchBy(time){it.time}.let{if(it>=0)it else -it-2}
        val point=points.getOrNull(index)?:return null
        // Do not animate a fabricated line or speed through a GPS gap.
        if(time-point.time>30_000)return null
        return point
    }
}
@Composable fun TripReplay(d:TripDetail, selected:(TrackPoint?)->Unit) {
    var fraction by remember(d.summary.id){mutableFloatStateOf(0f)};var playing by remember{mutableStateOf(false)}
    var speed by remember{mutableIntStateOf(20)};val units by RideState.preferences.collectAsState()
    val duration=(d.summary.ended-d.summary.started).coerceAtLeast(1)
    val at=d.summary.started+(duration*fraction).toLong();val point=ReplayMath.point(d.track,at)
    LaunchedEffect(at){selected(point)}
    LaunchedEffect(playing,speed){while(playing){delay(100);fraction=(fraction+100f*speed/duration).coerceAtMost(1f);if(fraction>=1f)playing=false}}
    ToolCard {
        Text("Trip replay",style=MaterialTheme.typography.titleMedium)
        Slider(fraction,{playing=false;fraction=it})
        Text("${toolDate(at)} · ${TripSummary.duration(at-d.summary.started)}")
        Text(if(point==null)"GPS gap / no recorded location at this time" else point.speed?.let{String.format(Locale.US,"Recorded speed %.1f %s",it*(if(units.imperial)2.236936 else 3.6),if(units.imperial)"mph" else "km/h")}?:"Speed was not recorded")
        Row {Button(onClick={if(fraction>=1f)fraction=0f;playing=!playing}){Text(if(playing)"Pause replay" else "Play replay")};TextButton(onClick={speed=if(speed==20)60 else if(speed==60)1 else 20}){Text("${speed}×")}}
        Text("Orange marker: last recorded fix. GPS gaps are not interpolated.",style=MaterialTheme.typography.bodySmall)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TripTrim(d:TripDetail) {
    var range by remember(d.summary.id){mutableStateOf(0f..1f)};var show by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    val length=(d.summary.ended-d.summary.started).coerceAtLeast(1)
    val from=d.summary.started+(length*range.start).toLong();val to=d.summary.started+(length*range.endInclusive).toLong()
    OutlinedButton(onClick={show=!show}){Text(if(show)"Hide trim controls" else "Trim into a new trip")}
    if(show)ToolCard {
        Text("Choose the portion to keep. Your original trip is retained.")
        RangeSlider(range,{range=it},enabled=!busy)
        Text("${toolDate(from)} → ${toolDate(to)}")
        Button(enabled=!busy&&to>from,onClick={scope.launch{busy=true;message=withContext(Dispatchers.IO){runCatching{TripStore.trimmedCopy(d.summary.id,from,to);"Trimmed copy saved in My trips; original retained"}.getOrElse{it.message?:"Could not trim trip"}};busy=false}}){Text("Save trimmed copy")}
        Text(message)
    }
}
