package de.chaostheorybot.rykerconnect.ride

import kotlin.math.ceil

/** Read-only synthetic tracks. Never inserted into TripStore, parking, backups or totals. */
object DemoTrips {
    private fun make(id:String,title:String,notes:String,day:Int,speed:Double,waypoints:List<Pair<Double,Double>>,pause:Boolean=false,gap:Boolean=false):TripDetail {
        val start=java.time.Instant.parse("2026-09-${day.toString().padStart(2,'0')}T18:00:00Z").toEpochMilli()
        val points=mutableListOf<TrackPoint>()
        var time=start
        waypoints.zipWithNext().forEachIndexed { segment,(a,b) ->
            val meters=TrackMath.distance(TrackPoint(a.first,a.second,time,5f),TrackPoint(b.first,b.second,time,5f))
            val steps=ceil(meters/(speed*5)).toInt().coerceAtLeast(1)
            for(i in 0 until steps) {
                val fraction=i.toDouble()/steps
                if(!(gap && segment==2 && i in steps/3..steps/3+12)) points.add(TrackPoint(a.first+(b.first-a.first)*fraction,a.second+(b.second-a.second)*fraction,time,5f,speed))
                time+=5000
            }
            if(pause && segment==1) repeat(60){points.add(TrackPoint(b.first,b.second,time,5f,0.0));time+=5000}
        }
        points.add(TrackPoint(waypoints.last().first,waypoints.last().second,time,5f,0.0))
        for(i in points.indices) points[i]=points[i].copy(altitude=40.0+25.0*kotlin.math.sin(i/35.0))
        val stats=TripAnalysis.stats(points,start,time,true)
        return TripDetail(TripSummary(id=id,started=start,ended=time,meters=stats.meters,points=points.size,title=title,
            notes="DEMO · Synthetic route for exploring the app. Not an actual recorded ride. $notes",modern=true,preview=points),points,stats)
    }
    val details:List<TripDetail> by lazy { listOf(
        make("demo-city","Downtown coffee loop","Short city route with a five-minute coffee stop.",10,7.0,listOf(61.218 to -149.900,61.218 to -149.875,61.207 to -149.875,61.207 to -149.900,61.218 to -149.900),pause=true),
        make("demo-coastal","Coastal afternoon","Scenic sample with a longer route and steady speeds.",11,12.0,listOf(61.218 to -149.900,61.211 to -149.918,61.195 to -149.932,61.180 to -149.969,61.155 to -150.018,61.159 to -150.055)),
        make("demo-long","Southbound day ride","Longer sample with a stop and deliberate GPS gap. The map must not join the missing section.",12,18.0,listOf(61.218 to -149.900,61.190 to -149.880,61.140 to -149.865,61.080 to -149.810,61.005 to -149.710,60.940 to -149.420),pause=true,gap=true)
    ) }
    fun find(id:String)=details.firstOrNull{it.summary.id==id}
}
