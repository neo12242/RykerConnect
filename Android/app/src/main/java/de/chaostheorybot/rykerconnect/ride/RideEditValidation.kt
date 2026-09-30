package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import java.net.URI
import java.util.UUID

/** Validate persisted overlays before they can replace a readable local record. */
internal object RideEditValidation {
    private val hash = Regex("[a-f0-9]{64}")
    fun record(record:JSONObject) {
        val id=record.getString("id");UUID.fromString(id)
        val origin=URI(record.getString("origin"))
        require(origin.scheme in setOf("https","http") && origin.host!=null && origin.userInfo==null && origin.query==null && origin.fragment==null)
        require(hash.matches(record.getString("baseRevision")))
        for(key in listOf("base","manifest"))manifest(record.getJSONObject(key),id)
        require(!record.has("remoteSequence") || record.getLong("remoteSequence")>=0)
        record.optJSONObject("conflict")?.let { require(it.getString("id")==id && hash.matches(it.getString("revision")));manifest(it.getJSONObject("manifest"),id) }
    }
    fun manifest(m:JSONObject,id:String) {
        require(m.getInt("version")==1 && m.getString("id")==id)
        require(m.getString("title").length in 1..100 && m.getString("story").length<=2000)
        require(m.getString("date").matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val tags=m.getJSONArray("tags");require(tags.length()<=15);for(i in 0 until tags.length())require(tags.getString(i).length<=30)
        val route=m.getJSONArray("route");var count=0;require(route.length()<=20000)
        for(i in 0 until route.length()){val line=route.getJSONArray(i);require(line.length()>0);count+=line.length();require(count<=20000)
            for(k in 0 until line.length()){val point=line.getJSONArray(k);require(point.length()==2 && point.getDouble(0) in -180.0..180.0 && point.getDouble(1) in -90.0..90.0)}}
        val photos=m.getJSONArray("photos");require(photos.length()<=30);val ids=mutableSetOf<String>()
        for(i in 0 until photos.length()){val p=photos.getJSONObject(i);val photo=p.getString("id");UUID.fromString(photo);require(ids.add(photo) && p.getString("caption").length<=500)
            require(hash.matches(p.getString("sha")) && hash.matches(p.getString("thumbSha")) && p.getLong("size") in 1..3000000 && p.getLong("thumbSize") in 1..500000)}
        require(m.getString("cover").isBlank() || m.getString("cover") in ids)
        val stats=m.getJSONObject("stats");require(stats.keys().asSequence().all{it in setOf("meters","elapsedMs","movingMs","stoppedMs","unknownMs","pausedMs","averageMps")})
        stats.keys().forEach{if(!stats.isNull(it))require(stats.getDouble(it) in 0.0..1e12)}
        val privacy=m.getJSONObject("privacy");require(privacy.getDouble("trimMeters") in 0.0..10000.0 && privacy.get("statsIncluded") is Boolean)
        m.optJSONArray("stops")?.let { stops ->
            require(stops.length()<=1000)
            val visible=RideEdits.route(m).map{it.lon to it.lat}.toSet();var total=0L
            for(i in 0 until stops.length()){val s=stops.getJSONObject(i);val p=s.getJSONArray("point");val duration=s.getLong("durationMs")
                require(p.length()==2 && (p.getDouble(0) to p.getDouble(1)) in visible && duration in 0..1_000_000_000_000)
                total+=duration
            }
            require(stops.length()==0 || privacy.getBoolean("statsIncluded") && total<=stats.optLong("pausedMs"))
        }
    }
}
