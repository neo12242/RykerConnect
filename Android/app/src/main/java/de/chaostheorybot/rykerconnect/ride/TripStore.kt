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
    val preview: List<TrackPoint> = emptyList(), val derived: Boolean = false) {
    val stationarySession: Boolean get() = automatic && modern && meters < 100 && preview.count { (it.speed ?: 0.0) >= 1.0 } < 3
    fun durationText(): String = duration((if (recording) System.currentTimeMillis() else ended) - started)
    companion object {
        fun duration(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return java.lang.String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) }
    }
}
data class TripDetail(val summary: TripSummary, val track: List<TrackPoint>, val stats: TripStats)

object TripStore {
    val summary = MutableStateFlow(TripSummary())
    val history = MutableStateFlow<List<TripSummary>>(emptyList())
    val storageError = MutableStateFlow("")
    private lateinit var directory: File
    private var previous: TrackPoint? = null
    private var resuming = false
    @Synchronized fun init(context: Context) {
        initDirectory(File(context.noBackupFilesDir, "rides"))
    }
    @Synchronized internal fun initDirectory(folder: File) {
        directory = folder.apply { mkdirs() }
        summary.value = TripSummary(); previous = null; resuming=false
        refresh()
    }
    @Synchronized fun start(automatic: Boolean = false) {
        if (summary.value.recording) return
        val now = System.currentTimeMillis(); val id = java.util.UUID.randomUUID().toString()
        file(id).writeText(JSONObject().put("start", now).put("version", 2).put("automatic", automatic).toString() + "\n")
        previous = null; resuming=false
        summary.value = TripSummary(id, now, recording = true, gps = "Waiting for GPS", automatic = automatic, modern = true)
    }
    @Synchronized fun recoverable(now:Long):String? = history.value.firstOrNull {
        it.automatic && it.modern && it.interrupted && now-it.ended in 0..120_000
    }?.id
    @Synchronized fun resume(id:String) {
        check(!summary.value.recording)
        val d=detail(id)
        require(d.summary.automatic && d.summary.interrupted && System.currentTimeMillis()-d.summary.ended in 0..120_000){"Interrupted session is no longer recent"}
        file(id).appendText(JSONObject().put("disconnect",0).toString()+"\n")
        previous=null;resuming=true
        summary.value=d.summary.copy(recording=true,ended=0,interrupted=false,gps="Recovered session · waiting for GPS")
        refresh()
    }
    @Synchronized fun point(input: TrackPoint) {
        val p=if(resuming)input.copy(segmentStart=true) else input
        val current = summary.value
        if (!current.recording || p.time < current.started || !TrackMath.acceptable(previous, p)) return
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
    @Synchronized fun gps(message: String) { if (summary.value.recording) summary.value = summary.value.copy(gps = message) }
    @Synchronized fun markDisconnect(wall: Long?) {
        if (summary.value.recording && summary.value.automatic) file(summary.value.id).appendText(JSONObject().put("disconnect", wall ?: 0).toString() + "\n")
    }
    @Synchronized fun stop(reason: String = "Saved", endAt: Long = System.currentTimeMillis()) {
        val current = summary.value
        if (!current.recording) return
        val end = endAt.coerceAtLeast(current.started)
        val saved = runCatching {
            if (endAt < System.currentTimeMillis() - 1_000) {
                val kept = records(current.id).filter { !it.has("time") || it.getLong("time") <= end }
                replace(file(current.id), kept.joinToString("\n") + "\n" + JSONObject().put("end", end).put("reason", reason) + "\n")
            } else file(current.id).appendText(JSONObject().put("end", end).put("reason", reason).toString() + "\n")
        }.isSuccess
        summary.value = current.copy(recording = false, ended = end, gps = if (saved) reason else "Storage error; saved points may be incomplete", interrupted = !saved)
        previous = null; refresh()
        history.value.firstOrNull { it.id == current.id }?.let { summary.value = it.copy(gps = summary.value.gps) }
    }
    private fun file(id: String): File {
        require(Regex("[a-f0-9-]{36}").matches(id)); return File(directory, "$id.jsonl")
    }
    private fun replace(file: File, text: String) {
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    private fun records(id: String): List<JSONObject> = file(id).readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    @Synchronized fun detail(id: String): TripDetail {
        val rows = records(id); val header = rows.first(); val started = header.getLong("start")
        val end = rows.lastOrNull { it.has("end") }
        val pendingDisconnect = rows.lastOrNull { it.has("disconnect") }?.optLong("disconnect") ?: 0L
        val rawTrack = rows.filter { it.has("lat") }.mapNotNull { row -> runCatching {
            TrackPoint(row.getDouble("lat"), row.getDouble("lon"), row.getLong("time"), row.getDouble("accuracy").toFloat(), if (row.has("speed")) row.getDouble("speed") else null, row.optBoolean("segmentStart"), row.optDouble("altitude").takeIf { it.isFinite() && it in -500.0..9000.0 })
        }.getOrNull() }.filter { TrackMath.acceptable(null, it) }
        val ended = end?.getLong("end") ?: pendingDisconnect.takeIf { it > 0 } ?: rawTrack.lastOrNull()?.time ?: started
        val track = rawTrack.filter { it.time in started..ended }
        val modern = header.optInt("version", 1) >= 2
        val stats = TripAnalysis.stats(track, started, ended, modern)
        val metadata = File(directory, "$id.meta.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        // Full points retain gap boundaries in route previews; UI samples per segment.
        return TripDetail(TripSummary(id, started, ended, stats.meters, track.size, interrupted = end == null || end.optString("reason").startsWith("Recording stopped") || end.optString("reason").startsWith("Storage error"),
            automatic = header.optBoolean("automatic"), title = metadata?.optString("title").orEmpty(), notes = metadata?.optString("notes").orEmpty(), modern = modern, preview = track, derived = header.optBoolean("derived")), track, stats)
    }
    @Synchronized fun saveMetadata(id: String, title: String, notes: String) {
        require(file(id).exists()); require(title.length <= 100 && notes.length <= 2000)
        replace(File(directory, "$id.meta.json"), JSONObject().put("title", title.trim()).put("notes", notes.trim()).toString())
        refresh()
    }
    @Synchronized private fun refresh() {
        var failures = 0
        history.value = directory.listFiles().orEmpty().filter { it.extension == "jsonl" && !(summary.value.recording && it.nameWithoutExtension == summary.value.id) }.mapNotNull { f ->
            runCatching { detail(f.nameWithoutExtension).summary }.getOrElse { failures++; null }
        }.sortedByDescending { it.started }
        storageError.value = if (failures > 0) "$failures trip file(s) could not be read; originals retained" else ""
    }
    @Synchronized fun gpx(id: String): String {
        val detail = detail(id)
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
        val d = detail(id); require(start >= d.summary.started && end <= d.summary.ended && end > start)
        val points = d.track.filter { it.time in start..end }; require(points.isNotEmpty()) { "Selected range has no GPS points" }
        return copyTrip(points, start, end, "Trimmed: " + d.summary.title.ifBlank { "ride" }, "Copy of $id. Original retained.", d.summary.modern)
    }
    @Synchronized fun mergedCopy(ids: Set<String>): String {
        require(ids.size >= 2)
        val rides = ids.map { detail(it) }.sortedBy { it.summary.started }
        require(rides.zipWithNext().all { (a,b) -> a.summary.ended <= b.summary.started }) { "Overlapping trips cannot be merged" }
        val points = rides.flatMap { it.track.mapIndexed { index, point -> if (index == 0) point.copy(segmentStart = true) else point } }.distinctBy { it.time }; require(points.isNotEmpty()) { "Selected trips have no GPS points" }
        return copyTrip(points, rides.first().summary.started, rides.last().summary.ended, "Merged ride", "Originals retained: " + rides.joinToString { it.summary.id }, rides.all { it.summary.modern })
    }
    private fun copyTrip(points: List<TrackPoint>, start: Long, end: Long, title: String, notes: String, modern: Boolean): String {
        val id = java.util.UUID.randomUUID().toString()
        val rows = mutableListOf(JSONObject().put("start", start).put("version", if (modern) 2 else 1).put("automatic", false).put("derived", true))
        for (p in points) rows.add(JSONObject().put("lat",p.lat).put("lon",p.lon).put("time",p.time).put("accuracy",p.accuracy).put("segmentStart",p.segmentStart).apply { p.speed?.let { put("speed",it) }; p.altitude?.let { put("altitude",it) } })
        rows.add(JSONObject().put("end",end).put("reason","Edited copy"))
        replace(file(id), rows.joinToString("\n") + "\n")
        saveMetadata(id, title.take(100), notes.take(2000)); return id
    }
}
