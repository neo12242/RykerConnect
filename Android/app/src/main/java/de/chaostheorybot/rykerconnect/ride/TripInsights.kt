package de.chaostheorybot.rykerconnect.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

data class RideStop(val point:TrackPoint,val ended:Long) { val duration:Long get()=ended-point.time }
object TripInsights {
    fun stops(points:List<TrackPoint>):List<RideStop> {
        val result=mutableListOf<RideStop>()
        for(segment in TripAnalysis.segments(points)) {
            var first:TrackPoint?=null;var last:TrackPoint?=null
            fun finish() { val a=first;val b=last;if(a!=null && b!=null && b.time-a.time>=30_000)result.add(RideStop(a,b.time));first=null;last=null }
            for(p in segment) {
                if(p.speed!=null && p.speed<1.0) {
                    if(first!=null && TrackMath.distance(first!!,p)>30)finish()
                    if(first==null)first=p
                    last=p
                } else finish()
            }
            finish()
        }
        return result
    }
}

@Composable internal fun TripCharts(detail:TripDetail) {
    val units by RideState.preferences.collectAsState()
    val stops=remember(detail){TripInsights.stops(detail.track)}
    ToolCard {
        Text("Ride profile",style=MaterialTheme.typography.titleLarge)
        ProfileChart("Speed",detail.track,if(units.imperial) "mph" else "km/h") { it.speed?.times(if(units.imperial)2.236936 else 3.6) }
        ProfileChart("GPS elevation",detail.track,if(units.imperial) "ft" else "m") { it.altitude?.times(if(units.imperial)3.28084 else 1.0) }
        Text("Time runs left to right. Gaps stay empty. GPS elevation is approximate and available only for recordings with a usable altitude fix.",style=MaterialTheme.typography.bodySmall)
    }
    ToolCard {
        Text("Stops · ${stops.size}",style=MaterialTheme.typography.titleMedium)
        if(stops.isEmpty())Text("No confirmed stops of at least 30 seconds. Missing speed or GPS is not counted as a stop.")
        stops.forEachIndexed { i, stop -> Text("${i+1}. ${toolDate(stop.point.time)} · ${TripSummary.duration(stop.duration)}") }
        if(stops.isNotEmpty())Text("Amber markers on the map show these stops.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ProfileChart(label:String,points:List<TrackPoint>,unit:String,value:(TrackPoint)->Double?) {
    val values=points.mapNotNull(value)
    Text(label,style=MaterialTheme.typography.titleMedium)
    if(values.isEmpty()){Text("Not recorded for this trip");return}
    val lo=values.min();val hi=values.max();val color=MaterialTheme.colorScheme.primary
    val description=String.format(Locale.US,"%s: %.1f to %.1f %s",label,lo,hi,unit)
    Text(description,style=MaterialTheme.typography.bodySmall)
    val groups=remember(points){TripAnalysis.segments(points)}
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics{contentDescription=description}) {
        val first=points.first().time;val span=(points.last().time-first).coerceAtLeast(1)
        val range=(hi-lo).coerceAtLeast(1.0)
        drawLine(color.copy(alpha=.3f),Offset(0f,size.height),Offset(size.width,size.height))
        for(group in groups) {
            val path=Path();var connected=false
            for(p in group) {
                val v=value(p)
                if(v==null){connected=false;continue}
                val x=(p.time-first).toFloat()/span*size.width
                val y=size.height-((v-lo)/range*size.height).toFloat()
                if(connected)path.lineTo(x,y) else path.moveTo(x,y)
                connected=true
            }
            drawPath(path,color,style=Stroke(3.dp.toPx()))
        }
    }
}
