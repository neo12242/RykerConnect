package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Website copies are overlays. Original GPS samples and measured statistics are never overwritten. */
object RideEdits {
    private lateinit var c:Context
    fun init(context:Context){c=context.applicationContext}
    fun record(id:String):JSONObject?=SoftwareStore.records("siteEdits").firstOrNull{it.optString("id")==id}
    fun manifest(id:String)=record(id)?.optJSONObject("manifest")
    private fun save(value:JSONObject,expected:JSONObject?=null):Boolean {
        if(SyncJson.canonical(value)==SyncJson.canonical(expected))return true
        val saved=SoftwareStore.syncSet(listOf("siteEdits",value.getString("id")),SyncJson.fingerprint(expected),value)
        if(saved)TripStore.refreshDisplay()
        return saved
    }
    fun localText(id:String,title:String,story:String){updateLocal(id){it.put("title",title.ifBlank{"My ride"}).put("story",story)}}
    fun localJournal(j:JSONObject){updateLocal(j.getString("id")){m->
        m.put("tags",JSONArray(j.optString("tags").split(',').map{it.trim().take(30)}.filter{it.isNotBlank()}.distinct().take(15)))
        val local=RidePhotos.photos(j).associateBy{it.getString("id")};val a=m.getJSONArray("photos")
        for(i in 0 until a.length()){val p=a.getJSONObject(i);local[p.getString("id")]?.let{p.put("caption",it.optString("caption"))}}
        val cover=j.optString("cover");if(cover.isBlank() || (0 until a.length()).any{a.getJSONObject(it).getString("id")==cover})m.put("cover",cover)
    }}
    fun updateLocal(id:String,edit:(JSONObject)->Unit) {
        val old=record(id)?:return;val next=JSONObject(old.toString());edit(next.getJSONObject("manifest"))
        if(SyncJson.canonical(old)!=SyncJson.canonical(next))save(next,old)
    }
    fun journal(raw:JSONObject):JSONObject {
        val m=manifest(raw.getString("id"))?:return raw
        val result=JSONObject(raw.toString());val remote=m.getJSONArray("photos");val captions=(0 until remote.length()).associate{remote.getJSONObject(it).let{p->p.getString("id") to p.optString("caption")}}
        val local=result.getJSONArray("photos");for(i in 0 until local.length()){val p=local.getJSONObject(i);captions[p.getString("id")]?.let{p.put("caption",it)}}
        result.put("tags",(0 until m.getJSONArray("tags").length()).joinToString(", "){m.getJSONArray("tags").getString(it)})
        val cover=m.optString("cover");if(cover.isBlank() || (0 until local.length()).any{local.getJSONObject(it).getString("id")==cover})result.put("cover",cover)
        return result
    }
    fun route(m:JSONObject):List<TrackPoint> {
        val result=mutableListOf<TrackPoint>();val routes=m.getJSONArray("route");var t=0L
        for(i in 0 until routes.length()){val line=routes.getJSONArray(i);for(k in 0 until line.length()){val p=line.getJSONArray(k);result.add(TrackPoint(p.getDouble(1),p.getDouble(0),++t*5000,5f,segmentStart=k==0))}}
        return result
    }
    fun display(raw:TripDetail):TripDetail {
        val m=manifest(raw.summary.id)?:return raw;val stats=m.getJSONObject("stats");val preview=route(m)
        val meters=stats.optDouble("meters",raw.stats.meters)
        val started=if(raw.summary.websiteCopy)WebsiteRide.start(m.getString("date")) else raw.summary.started
        return raw.copy(summary=raw.summary.copy(title=m.getString("title"),notes=m.getString("story"),meters=meters,
            started=started,ended=started+stats.optLong("elapsedMs",raw.summary.ended-raw.summary.started),preview=preview,
            displayDate=m.getString("date"),statisticsAvailable=if(raw.summary.websiteCopy)stats.has("meters") else true),
            stats=raw.stats.copy(meters=meters,movingMs=stats.optLong("movingMs",raw.stats.movingMs),stoppedMs=stats.optLong("stoppedMs",raw.stats.stoppedMs),unknownMs=stats.optLong("unknownMs",raw.stats.unknownMs),
                 averageMps=if(stats.has("averageMps"))stats.optDouble("averageMps").takeIf{it.isFinite()} else raw.stats.averageMps,pausedMs=stats.optLong("pausedMs",raw.stats.pausedMs)),editedRoute=preview,editedDate=m.getString("date"),publishedStops=RidePauses.published(m))
    }
    fun forPublication(m:JSONObject,includeRoute:Boolean):JSONObject {
        val saved=manifest(m.getString("id"))?:return m
        m.put("date",saved.getString("date"))
        // Changing privacy starts from the original track; it must be explicitly previewed again.
        if(SyncJson.canonical(m.getJSONObject("privacy"))==SyncJson.canonical(saved.getJSONObject("privacy"))) {
            if(includeRoute)m.put("route",if(saved.getJSONArray("route").length()==0)JSONArray() else checkedRoute(m.getString("id"),saved)?:error("Original recording is needed to check route privacy"))
            m.put("stats",JSONObject(saved.getJSONObject("stats").toString()))
            if(saved.has("stops"))m.put("stops",JSONArray(saved.getJSONArray("stops").toString()))
        }
        return m
    }
    internal fun newRecord(remote:JSONObject,origin:String)=JSONObject().put("id",remote.getString("id")).put("origin",origin)
        .put("remoteSequence",remote.getLong("sequence"))
        .put("baseRevision",remote.getString("revision")).put("base",JSONObject(remote.getJSONObject("manifest").toString()))
        .put("manifest",JSONObject(remote.getJSONObject("manifest").toString())).put("needsReview",remote.optBoolean("needsReview"))
        .put("published",remote.opt("published")?:JSONObject.NULL)
    private fun incoming(remote:JSONObject,origin:String) {
        val id=remote.getString("id");if(SharedLibrary.isTripDeleted(id))return
        val old=record(id)
        check(!SharedLibrary.hasConflict("software/siteEdits/$id")){"Resolve the two-app ride edit conflict before downloading website changes"}
        if(old!=null && remote.getLong("sequence")<old.optLong("remoteSequence"))return
        if(remote.optBoolean("deleted")){if(old!=null)save(JSONObject(old.toString()).put("remoteDeleted",true).put("remoteSequence",remote.getLong("sequence")),old);return}
        if(old!=null && old.optString("origin")!=origin)throw IllegalStateException("A ride belongs to a different website; review the connection")
        if(old?.optString("baseRevision")==remote.getString("revision") && !old.optBoolean("remoteDeleted")) {
            save(JSONObject(old.toString()).put("published",remote.opt("published")?:JSONObject.NULL).put("needsReview",remote.optBoolean("needsReview")).put("remoteSequence",remote.getLong("sequence")),old);return
        }
        if(old!=null && SyncJson.canonical(old.getJSONObject("manifest"))!=SyncJson.canonical(old.getJSONObject("base"))) {
            save(JSONObject(old.toString()).put("conflict",remote),old);return
        }
        val next=newRecord(remote,origin)
        if(old==null) {
            // Detect edits made after the original upload in an older APK before adopting a website overlay.
            val baseline=DadRides.uploadBaseline(id)
            val local=runCatching{TripStore.originalDetail(id)}.getOrNull()
            if(baseline!=null && local!=null) {
                val title=local.summary.title.ifBlank{"My ride"};val story=local.summary.notes
                if(title!=baseline.optString("title") || story!=baseline.optString("story")) {
                    next.getJSONObject("manifest").put("title",title).put("story",story);next.put("conflict",remote)
                }
            }
        }
        save(next,old)
    }
    private fun checkedRoute(id:String,m:JSONObject):JSONArray? {
        val original=runCatching{TripStore.originalDetail(id)}.getOrNull()?:return null
        if(original.track.isEmpty())return null
        val radius=m.getJSONObject("privacy").getDouble("trimMeters");val result=JSONArray();var line:JSONArray?=null
        for(p in route(m)) {
            if(p.segmentStart)line=null
            val visible=radius==0.0 || TrackMath.distance(original.track.first(),p)>radius && TrackMath.distance(original.track.last(),p)>radius
            if(!visible){line=null;continue}
            if(line==null){line=JSONArray();result.put(line)};line.put(JSONArray().put(p.lon).put(p.lat))
        }
        return result
    }
    @Synchronized fun sync():String {
        if(!::c.isInitialized || !DadRides.enabled() || DadRides.origin().isBlank())return "Website sync not configured"
        val origin=DadRides.origin()
        for(old in SoftwareStore.records("siteEdits").filter{!SharedLibrary.isTripDeleted(it.getString("id")) && it.optString("origin")==origin && !it.has("conflict") && !it.optBoolean("remoteDeleted") && !SharedLibrary.hasConflict("software/siteEdits/${it.getString("id")}")}) {
            val id=old.getString("id")
            if(SyncJson.canonical(old.getJSONObject("manifest"))!=SyncJson.canonical(old.getJSONObject("base"))) {
                try {
                    val remote=DadRides.request("rides/$id/edit","POST",JSONObject().put("base",old.getString("baseRevision")).put("manifest",old.getJSONObject("manifest")).toString().toByteArray())
                    save(newRecord(remote,origin),old)
                }catch(e:DadRidesHttpException){if(e.status==409 && e.payload.has("current"))save(JSONObject(old.toString()).put("conflict",e.payload.getJSONObject("current")),old) else throw e}
            }
        }
        val prefs=c.getSharedPreferences("site_sync",0);val key=SyncJson.fingerprint(origin);var cursor=prefs.getLong(key,0)
        do {
            val response=try{DadRides.request("sync?after=$cursor")}catch(e:DadRidesHttpException){if(e.status==409 && e.payload.optBoolean("reset")){cursor=0;DadRides.request("sync?after=0")}else throw e}
            val changes=response.getJSONArray("changes");for(i in 0 until changes.length())incoming(changes.getJSONObject(i),origin)
            cursor=response.getLong("cursor");prefs.edit().putLong(key,cursor).commit()
        }while(response.getBoolean("more"))
        for(old in SoftwareStore.records("siteEdits").filter{!SharedLibrary.isTripDeleted(it.getString("id")) && it.optString("origin")==origin && it.optBoolean("needsReview") && !it.has("conflict") && SyncJson.canonical(it.getJSONObject("manifest"))==SyncJson.canonical(it.getJSONObject("base"))}) {
            val id=old.getString("id");val checked=checkedRoute(id,old.getJSONObject("manifest"))?:continue
            try {
                val remote=DadRides.request("rides/$id/verify-route","POST",JSONObject().put("base",old.getString("baseRevision")).put("route",checked).toString().toByteArray())
                save(newRecord(remote,origin),old)
            }catch(e:DadRidesHttpException){if(e.status!=409)throw e}
        }
        val conflicts=SoftwareStore.records("siteEdits").count{it.has("conflict")}
        return if(conflicts>0)"$conflicts website conflicts need review" else "Website edits synchronized"
    }
    fun resolve(id:String,useWebsite:Boolean) {
        val old=record(id)?:return;val remote=old.optJSONObject("conflict")?:return
        val next=newRecord(remote,old.getString("origin"));if(!useWebsite)next.put("manifest",old.getJSONObject("manifest"))
        save(next,old);SharedLibrary.changed()
    }
    fun uploadFinished(id:String,revision:String,base:String?):String {
        return try {
            val remote=DadRides.request("rides/$id/adopt","POST",JSONObject().put("base",base?:JSONObject.NULL).put("revision",revision).toString().toByteArray())
            val old=record(id);if(old==null || remote.getLong("sequence")>=old.optLong("remoteSequence"))save(newRecord(remote,DadRides.origin()),old);SharedLibrary.changed();"Private draft ready. Review before publishing."
        }catch(e:DadRidesHttpException){if(e.status==409)"Private upload ready. Another version changed; select the current draft in the website studio." else throw e}
    }
}

class DadRidesHttpException(val status:Int,val payload:JSONObject):IllegalStateException(payload.optString("error","Site returned HTTP $status").take(160))
