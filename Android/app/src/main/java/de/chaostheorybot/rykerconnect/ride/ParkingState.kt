package de.chaostheorybot.rykerconnect.ride

import android.location.Location
import android.os.SystemClock
import org.json.JSONObject

object ParkingState {
    private var fix: Location? = null
    private var stoppedSince: Long? = null
    private var wasConnected = false
    private var lostAt: Long? = null
    private var candidate: JSONObject? = null
    @Synchronized fun location(location: Location) {
        if(!validEntryLocation(location)) return
        if(fix==null || location.elapsedRealtimeNanos-fix!!.elapsedRealtimeNanos>15_000_000_000L)stoppedSince=null
        fix=Location(location)
        val time=location.elapsedRealtimeNanos/1_000_000
        if(location.hasSpeed() && location.speed in 0f..<1f && (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond<=3f)) {
            if(stoppedSince==null) stoppedSince=time
        } else stoppedSince=null
    }
    @Synchronized fun connection(connected:Boolean) {
        val now=SystemClock.elapsedRealtime()
        if(!connected && wasConnected) {
            lostAt=now
            candidate=fix?.takeIf { validEntryLocation(it) && stoppedSince?.let { start -> now-start>=30_000 }==true }
                ?.let { record(it,false,"Stopped before disconnect") }
        }
        if(connected) { lostAt=null;candidate=null }
        else if(lostAt?.let { now-it>=120_000 }==true) {
            candidate?.let { next -> runCatching { SoftwareStore.commit(SoftwareStore.snapshot().put("parking",next)) }
                .onFailure { android.util.Log.w("RykerParking","Could not save parking candidate") } }
            candidate=null;lostAt=null
        }
        wasConnected=connected
    }
    private fun record(p:Location,confirmed:Boolean,source:String)=JSONObject().put("lat",p.latitude).put("lon",p.longitude)
        .put("tripId",TripStore.summary.value.id).put("time",System.currentTimeMillis()).put("fixTime",p.time).put("accuracy",p.accuracy).put("confirmed",confirmed).put("source",source)
    @Synchronized fun saveManual(point:Location) {
        require(validEntryLocation(point)){"Refresh GPS before saving parking"}
        SoftwareStore.commit(SoftwareStore.snapshot().put("parking",record(point,true,"Saved by you")))
    }
    @Synchronized fun confirm() {
        val next=SoftwareStore.snapshot();val point=next.optJSONObject("parking")?:error("No parking candidate")
        point.put("confirmed",true);SoftwareStore.commit(next)
    }
}
