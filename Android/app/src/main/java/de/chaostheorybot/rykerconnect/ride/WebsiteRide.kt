package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** A website snapshot has geometry and optional totals, never original GPS samples. */
internal object WebsiteRide {
    fun start(date:String)=LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun file(record:JSONObject):String {
        RideEditValidation.record(record)
        val m=record.getJSONObject("manifest");val start=start(m.getString("date"))
        require(start>0){"The website ride date must be after 1970"}
        return JSONObject().put("version",2).put("start",start).put("websiteCopy",record).toString()+"\n"+
            JSONObject().put("end",start+m.getJSONObject("stats").optLong("elapsedMs")).put("reason","Restored website copy").toString()+"\n"
    }
    fun detail(record:JSONObject):TripDetail {
        RideEditValidation.record(record)
        val m=record.getJSONObject("manifest");val s=m.getJSONObject("stats");val start=start(m.getString("date"))
        val route=RideEdits.route(m)
        val stats=TripStats(s.optDouble("meters",0.0),s.optLong("movingMs"),s.optLong("stoppedMs"),s.optLong("unknownMs"),null,
            s.optDouble("averageMps").takeIf{it.isFinite()},0,s.optLong("pausedMs"))
        val summary=TripSummary(id=m.getString("id"),started=start,ended=start+s.optLong("elapsedMs"),meters=stats.meters,
            title=m.getString("title"),notes=m.getString("story"),preview=route,websiteCopy=true,
            statisticsAvailable=s.has("meters"),displayDate=m.getString("date"))
        return TripDetail(summary,emptyList(),stats,editedRoute=route,editedDate=m.getString("date"),publishedStops=RidePauses.published(m))
    }
}

internal object RideRecovery {
    data class Candidate(val id:String,val title:String,val date:String)
    fun catalog():List<Candidate> {
        check(DadRides.enabled() && DadRides.origin().isNotBlank()){"Connect DadRides in Add-ons first."}
        val origin=DadRides.origin();var cursor=0L;val result=linkedMapOf<String,Candidate>()
        do {
            val page=DadRides.request("sync?after=$cursor")
            check(DadRides.origin()==origin){"The website connection changed. Reload the list."}
            val changes=page.getJSONArray("changes")
            for(i in 0 until changes.length()) {
                val r=changes.getJSONObject(i);if(r.optBoolean("deleted"))continue
                val m=r.getJSONObject("manifest");val id=r.getString("id");RideEditValidation.manifest(m,id)
                if(!TripStore.contains(id))result[id]=Candidate(id,m.getString("title"),m.getString("date"))
            }
            val next=page.getLong("cursor");check(!page.getBoolean("more") || next>cursor){"Invalid website paging response"};cursor=next
        }while(page.getBoolean("more"))
        return result.values.sortedByDescending{it.date}
    }
    fun preview(id:String):JSONObject {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val origin=DadRides.origin();val record=RideEdits.newRecord(DadRides.request("rides/$id/edit"),origin)
        check(DadRides.origin()==origin){"The website connection changed. Reload the list."}
        RideEditValidation.record(record);return record
    }
    fun restore(reviewed:JSONObject) {
        RideEditValidation.record(reviewed)
        check(DadRides.enabled() && DadRides.origin()==reviewed.getString("origin")){"Reconnect the reviewed website before restoring."}
        // Bring known peer deletions/originals into the causal history before making a new local version.
        SharedLibrary.syncNow()
        val latest=preview(reviewed.getString("id"))
        check(latest.getString("origin")==reviewed.getString("origin")){"The website connection changed. Reload the list."}
        check(latest.getString("baseRevision")==reviewed.getString("baseRevision")){"The website ride changed. Reload and review its latest version."}
        SharedLibrary.restoreWebsite(latest)
    }
}
