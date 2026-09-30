package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class TripSummary(val id: String = "", val started: Long = 0, val ended: Long = 0,
    val meters: Double = 0.0, val points: Int = 0, val recording: Boolean = false,
    val gps: String = "Not recording", val interrupted: Boolean = false,
    val automatic: Boolean = false, val title: String = "", val notes: String = "", val modern: Boolean = false,
    val preview: List<TrackPoint> = emptyList(), val derived: Boolean = false,
    val websiteCopy:Boolean=false,val statisticsAvailable:Boolean=true,val displayDate:String?=null,
    val paused:Boolean=false, val pausedMs:Long=0, val pauseStarted:Long=0, val needsRecovery:Boolean=false, val controlToken:String="") {
    val stationarySession: Boolean get() = automatic && modern && meters < 100 && preview.count { (it.speed ?: 0.0) >= 1.0 } < 3
    fun durationText(): String = duration((if (recording) System.currentTimeMillis() else ended) - started)
    fun pausedDuration(now:Long=System.currentTimeMillis()):Long = pausedMs + if(paused && pauseStarted>0)(now-pauseStarted).coerceAtLeast(0) else 0
    companion object {
        fun duration(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return java.lang.String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) }
    }
}
data class TripDetail(val summary: TripSummary, val track: List<TrackPoint>, val stats: TripStats,
    val editedRoute:List<TrackPoint>?=null,val editedDate:String?=null,val pauses:List<RidePause> = emptyList(),val publishedStops:List<PublishedStop>?=null)

object TripStore {
    val summary = MutableStateFlow(TripSummary())
    val history = MutableStateFlow<List<TripSummary>>(emptyList())
    val storageError = MutableStateFlow("")
    private lateinit var directory: File
    private var previous: TrackPoint? = null
    private var resuming = false
    private var minimumPointTime=0L
    private var recoveryProblem=""
    @Synchronized fun init(context: Context) {
        initDirectory(File(context.noBackupFilesDir, "rides"))
    }
    @Synchronized internal fun initDirectory(folder: File) {
        directory = folder.apply { mkdirs() }
        summary.value = TripSummary(); previous = null; resuming=false;minimumPointTime=0;recoveryProblem=""
        val active = File(directory, "active-session.json")
        if(active.exists()) runCatching {
            val marker=JSONObject(active.readText());if(!marker.has("id"))return@runCatching
            val id=marker.getString("id")
            val rows=records(id)
            if(rows.none { it.has("end") }) {
                val detail=originalDetail(id)
                val last=rows.lastOrNull { it.optString("event") in listOf("pause","resume") }
                val paused=last?.optString("event")=="pause"
                val pauseAt=if(paused)last!!.getLong("at") else 0
                val closed=RidePauses.total(detail.pauses.filter{it.ended!=null},detail.summary.started,System.currentTimeMillis())
                summary.value=detail.summary.copy(recording=true,ended=0,paused=paused,pauseStarted=pauseAt,pausedMs=closed,
                    needsRecovery=true,interrupted=true,gps=if(paused)"Paused · Resume or End ride" else "Recording interrupted · Resume or End ride",
                    controlToken=java.util.UUID.randomUUID().toString())
                resuming=true
            }
        }.onFailure { recoveryProblem="Active ride could not be recovered; saved files retained. Restore access to the ride files before starting another ride." }
        refresh()
    }
    @Synchronized fun start(automatic: Boolean = false) {
        check(recoveryProblem.isBlank()){recoveryProblem}
        if (summary.value.recording) return
        val now = System.currentTimeMillis(); val id = java.util.UUID.randomUUID().toString()
        replace(file(id),JSONObject().put("start", now).put("version", 3).put("automatic", automatic).toString() + "\n")
        replace(File(directory,"active-session.json"),JSONObject().put("id",id).toString())
        previous = null; resuming=false;minimumPointTime=now
        summary.value = TripSummary(id, now, recording = true, gps = "Waiting for GPS", automatic = automatic, modern = true,controlToken=java.util.UUID.randomUUID().toString())
        SharedLibrary.changed()
    }
    @Synchronized fun recoverable(now:Long):String? = history.value.firstOrNull {
        it.automatic && it.modern && it.interrupted && now-it.ended in 0..120_000
    }?.id
    @Synchronized fun resume(id:String) {
        check(!summary.value.recording)
        val d=originalDetail(id)
        require(d.summary.automatic && d.summary.interrupted && System.currentTimeMillis()-d.summary.ended in 0..120_000){"Interrupted session is no longer recent"}
        file(id).appendText(JSONObject().put("disconnect",0).toString()+"\n")
        replace(File(directory,"active-session.json"),JSONObject().put("id",id).toString())
        previous=null;resuming=true;minimumPointTime=System.currentTimeMillis()
        summary.value=d.summary.copy(recording=true,ended=0,interrupted=false,gps="Recovered session · waiting for GPS",controlToken=java.util.UUID.randomUUID().toString())
        refresh()
    }
    private fun appendEvent(row:JSONObject) {
        // Isolate a torn final GPS row before writing a durable control event.
        java.io.FileOutputStream(file(summary.value.id),true).use { out -> out.write(("\n"+row.toString()+"\n").toByteArray(Charsets.UTF_8)); out.fd.sync() }
    }
    @Synchronized fun pause(now:Long=System.currentTimeMillis()) {
        val s=summary.value;check(s.recording){"No active ride"};if(s.paused)return
        val at=maxOf(now,s.started,previous?.time?:0)
        val anchor=previous?.takeIf{at-it.time in 0..30_000 && it.accuracy<=50}
        appendEvent(RidePauses.row(RidePause(at,point=anchor)))
        previous=null;resuming=true
        summary.value=s.copy(paused=true,pauseStarted=at,gps="Paused · GPS recording off",controlToken=java.util.UUID.randomUUID().toString())
    }
    @Synchronized fun resumeRecording(now:Long=System.currentTimeMillis()) {
        val s=summary.value;check(s.recording){"No active ride"};if(!s.paused&&!s.needsRecovery)return
        val at=maxOf(now,s.started,s.pauseStarted)
        appendEvent(JSONObject().put("event","resume").put("at",at).put("disconnect",0))
        previous=null;resuming=true;minimumPointTime=at
        summary.value=s.copy(paused=false,pausedMs=s.pausedDuration(at),pauseStarted=0,needsRecovery=false,gps="Waiting for GPS",controlToken=java.util.UUID.randomUUID().toString())
    }
    @Synchronized fun interrupted() {
        val s=summary.value;if(!s.recording)return
        previous=null;resuming=true
        summary.value=s.copy(needsRecovery=true,gps=if(s.paused)"Paused · Resume or End ride" else "Recording interrupted · Resume or End ride",controlToken=java.util.UUID.randomUUID().toString())
    }
    @Synchronized fun point(input: TrackPoint) {
        val p=if(resuming)input.copy(segmentStart=true) else input
        val current = summary.value
        if (!current.recording || current.paused || current.needsRecovery || p.time < maxOf(current.started,minimumPointTime) || !TrackMath.acceptable(previous, p)) return
        val prior = previous
        // Keep stationary samples too: they distinguish a stop from missing GPS.
        if (prior != null && p.time - prior.time < 4_000) return
        val distance = if (prior != null && p.time - prior.time < 30_000) TrackMath.distance(prior, p) else 0.0
        val counted = if (prior != null && distance >= maxOf(5.0, (prior.accuracy + p.accuracy) / 2.0) && (p.speed == null || p.speed >= 1.0)) distance else 0.0
        val row = JSONObject().put("lat", p.lat).put("lon", p.lon).put("time", p.time).put("accuracy", p.accuracy).put("segmentStart",p.segmentStart)
        p.speed?.let { row.put("speed", it) }; p.altitude?.let { row.put("altitude", it) }
        file(current.id).appendText(row.toString() + "\n")
        previous = p; resuming=false
        summary.value = current.copy(meters = current.meters + counted, points = current.points + 1, gps = "GPS active · ±${p.accuracy.toInt()} m")
    }
    @Synchronized fun gps(message: String) { if (summary.value.recording && !summary.value.paused && !summary.value.needsRecovery) summary.value = summary.value.copy(gps = message) }
    @Synchronized fun markDisconnect(wall: Long?) {
        if (summary.value.recording && summary.value.automatic && !summary.value.paused && !summary.value.needsRecovery) file(summary.value.id).appendText(JSONObject().put("disconnect", wall ?: 0).toString() + "\n")
    }
    @Synchronized fun stop(reason: String = "Saved", endAt: Long = System.currentTimeMillis()):Boolean {
        val current = summary.value
        if (!current.recording) return true
        val end = endAt.coerceAtLeast(current.started)
        val saved = runCatching {
            if (endAt < System.currentTimeMillis() - 1_000) {
                val kept = records(current.id).filter { (!it.has("time") || it.getLong("time") <= end) && (!it.has("at") || it.getLong("at")<=end) }
                replace(file(current.id), kept.joinToString("\n") + "\n" + JSONObject().put("end", end).put("reason", reason) + "\n")
            } else appendEvent(JSONObject().put("end", end).put("reason", reason))
        }.isSuccess
        if(!saved){storageError.value="Could not save the end of this ride. Free storage and try End Ride again.";return false}
        runCatching{replace(File(directory,"active-session.json"),"{}")}
        summary.value = current.copy(recording = false, ended = end, gps = if (saved) reason else "Storage error; saved points may be incomplete", interrupted = !saved)
        previous = null; refresh(); SharedLibrary.changed()
        history.value.firstOrNull { it.id == current.id }?.let { summary.value = it.copy(gps = summary.value.gps) }
        return true
    }
    private fun file(id: String): File {
        require(Regex("[a-f0-9-]{36}").matches(id)); return File(directory, "$id.jsonl")
    }
    private fun replace(file: File, text: String) {
        val temp = File(file.path + ".tmp")
        java.io.FileOutputStream(temp).use{it.write(text.toByteArray(Charsets.UTF_8));it.fd.sync()}
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    private fun records(id: String): List<JSONObject> = file(id).readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    @Synchronized fun detail(id: String):TripDetail = RideEdits.display(originalDetail(id))
    @Synchronized fun originalDetail(id: String): TripDetail {
        val rows = records(id); val header = rows.first(); val started = header.getLong("start")
        header.optJSONObject("websiteCopy")?.let {
            require(it.getString("id")==id){"Website copy identifier mismatch"}
            return WebsiteRide.detail(it)
        }
        val end = rows.lastOrNull { it.has("end") }
        val pendingDisconnect = rows.lastOrNull { it.has("disconnect") }?.optLong("disconnect") ?: 0L
        val rawTrack = rows.filter { it.has("lat") }.mapNotNull { row -> runCatching {
            TrackPoint(row.getDouble("lat"), row.getDouble("lon"), row.getLong("time"), row.getDouble("accuracy").toFloat(), if (row.has("speed")) row.getDouble("speed") else null, row.optBoolean("segmentStart"), row.optDouble("altitude").takeIf { it.isFinite() && it in -500.0..9000.0 })
        }.getOrNull() }.filter { TrackMath.acceptable(null, it) }
        val active=summary.value.id==id && summary.value.recording
        val eventAt=rows.maxOfOrNull{it.optLong("at",0)}?:0
        val ended = end?.getLong("end") ?: if(active)System.currentTimeMillis() else maxOf(started,pendingDisconnect.takeIf { it > 0 } ?: rawTrack.lastOrNull()?.time ?: started,eventAt)
        val track = rawTrack.filter { it.time in started..ended }
        val modern = header.optInt("version", 1) >= 2
        val pauses = RidePauses.read(rows,started,ended).map{if(end!=null && it.ended==null)it.copy(ended=ended) else it}
        val stats = TripAnalysis.stats(track, started, ended, modern,pauses)
        val metadata = File(directory, "$id.meta.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        // Full points retain gap boundaries in route previews; UI samples per segment.
        return TripDetail(TripSummary(id, started, ended, stats.meters, track.size, interrupted = end == null || end.optString("reason").startsWith("Recording stopped") || end.optString("reason").startsWith("Storage error"),
            automatic = header.optBoolean("automatic"), title = metadata?.optString("title").orEmpty(), notes = metadata?.optString("notes").orEmpty(), modern = modern, preview = track, derived = header.optBoolean("derived"),pausedMs=stats.pausedMs), track, stats,pauses=pauses)
    }
    @Synchronized fun saveMetadata(id: String, title: String, notes: String) {
        require(file(id).exists()); require(title.length <= 100 && notes.length <= 2000)
        replace(File(directory, "$id.meta.json"), JSONObject().put("title", title.trim()).put("notes", notes.trim()).toString())
        RideEdits.localText(id,title.trim(),notes.trim());refresh();SharedLibrary.changed()
    }
    @Synchronized internal fun syncFiles():List<File> = directory.listFiles().orEmpty().filter {
        (it.name.endsWith(".jsonl") || it.name.endsWith(".meta.json")) && !(summary.value.recording && it.name.startsWith(summary.value.id))
    }
    @Synchronized internal fun syncReplace(name:String,expected:String,source:File?):Boolean {
        require(name.matches(Regex("[a-f0-9-]{36}\\.(jsonl|meta.json)")))
        if(summary.value.recording && name.startsWith(summary.value.id))return false
        val target=File(directory,name)
        val actual=LibraryData.fileValue(target,name.endsWith("meta.json"))
        if(SyncJson.fingerprint(actual)!=expected)return false
        if(source==null){check(!target.exists() || target.delete()){ "Could not remove local file; retry sync" }} else SharedLibrary.copyAtomic(source,target)
        refresh();return true
    }
    @Synchronized internal fun refreshDisplay(){refresh()}
    internal fun contains(id:String)=file(id).exists()
    @Synchronized private fun refresh() {
        var failures = 0
        history.value = directory.listFiles().orEmpty().filter { it.extension == "jsonl" && !(summary.value.recording && it.nameWithoutExtension == summary.value.id) }.mapNotNull { f ->
            runCatching { detail(f.nameWithoutExtension).summary }.getOrElse { failures++; null }
        }.sortedByDescending { it.started }
        storageError.value = recoveryProblem.ifBlank { if (failures > 0) "$failures trip file(s) could not be read; originals retained" else "" }
    }
    @Synchronized fun gpx(id: String): String {
        val detail = originalDetail(id)
        check(!detail.summary.websiteCopy){"Original GPS samples are unavailable in a restored website copy"}
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"RykerConnect\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>${escape(detail.summary.title.ifBlank { "RykerConnect ride" })}</name>")
            for (segment in TripAnalysis.segments(detail.track)) {
                append("<trkseg>")
                for (p in segment) append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">${p.altitude?.let { "<ele>$it</ele>" }.orEmpty()}<time>${Instant.ofEpochMilli(p.time)}</time></trkpt>")
                append("</trkseg>")
            }
            append("</trk></gpx>")
        }
    }

    @Synchronized fun backupFiles(): Map<String, String> = directory.listFiles().orEmpty()
        .filter { (it.name.endsWith(".jsonl") || it.name.endsWith(".meta.json")) && !it.name.startsWith(summary.value.id.takeIf { summary.value.recording } ?: "NO_ACTIVE_TRIP") }
        .associate { it.name to it.readText() }

    @Synchronized fun restoreFiles(files: Map<String, String>): Int {
        val added = mutableListOf<File>()
        try {
            for ((name, text) in files.filterKeys { it.endsWith(".jsonl") }) {
                val id = name.removeSuffix(".jsonl"); val target = file(id)
                if (target.exists()) continue
                replace(target, text); added.add(target)
                files["$id.meta.json"]?.let { meta -> val m = File(directory, "$id.meta.json"); replace(m, meta); added.add(m) }
            }
        } catch (e: Exception) { added.forEach { it.delete() }; throw e }
        refresh(); return added.count { it.extension == "jsonl" }
    }

    @Synchronized fun trimmedCopy(id: String, start: Long, end: Long): String {
        val d = originalDetail(id); require(start >= d.summary.started && end <= d.summary.ended && end > start)
        val points = d.track.filter { it.time in start..end }; require(points.isNotEmpty()) { "Selected range has no GPS points" }
        return copyTrip(points, start, end, "Trimmed: " + d.summary.title.ifBlank { "ride" }, "Copy of $id. Original retained.", d.summary.modern,RidePauses.clipped(d.pauses,start,end))
    }
    @Synchronized fun mergedCopy(ids: Set<String>): String {
        require(ids.size >= 2)
        val rides = ids.map { originalDetail(it) }.sortedBy { it.summary.started }
        require(rides.none{it.summary.websiteCopy}){"Merging needs original recordings. A website copy has no original GPS samples."}
        require(rides.zipWithNext().all { (a,b) -> a.summary.ended <= b.summary.started }) { "Overlapping trips cannot be merged" }
        val points = rides.flatMap { it.track.mapIndexed { index, point -> if (index == 0) point.copy(segmentStart = true) else point } }.distinctBy { it.time }; require(points.isNotEmpty()) { "Selected trips have no GPS points" }
        return copyTrip(points, rides.first().summary.started, rides.last().summary.ended, "Merged ride", "Originals retained: " + rides.joinToString { it.summary.id }, rides.all { it.summary.modern },rides.flatMap{it.pauses})
    }
    private fun copyTrip(points: List<TrackPoint>, start: Long, end: Long, title: String, notes: String, modern: Boolean,pauses:List<RidePause> = emptyList()): String {
        val id = java.util.UUID.randomUUID().toString()
        val rows = mutableListOf(JSONObject().put("start", start).put("version", if (modern) 2 else 1).put("automatic", false).put("derived", true))
        for (p in points) rows.add(JSONObject().put("lat",p.lat).put("lon",p.lon).put("time",p.time).put("accuracy",p.accuracy).put("segmentStart",p.segmentStart).apply { p.speed?.let { put("speed",it) }; p.altitude?.let { put("altitude",it) } })
        for(pause in pauses){rows.add(RidePauses.row(pause));pause.ended?.let{rows.add(JSONObject().put("event","resume").put("at",it))}}
        rows.add(JSONObject().put("end",end).put("reason","Edited copy"))
        replace(file(id), rows.joinToString("\n") + "\n")
        saveMetadata(id, title.take(100), notes.take(2000)); return id
    }
}
