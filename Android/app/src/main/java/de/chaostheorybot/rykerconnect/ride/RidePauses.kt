package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject

/** A deliberate break, distinct from a stationary GPS sample or an interruption. */
data class RidePause(val started: Long, val ended: Long? = null, val point: TrackPoint? = null) {
    fun duration(until: Long) = ((ended ?: until) - started).coerceAtLeast(0)
}
data class PublishedStop(val point:TrackPoint,val durationMs:Long)

object RidePauses {
    fun published(manifest:JSONObject):List<PublishedStop> {
        val stops=manifest.optJSONArray("stops")?:return emptyList()
        return (0 until stops.length()).map{val s=stops.getJSONObject(it);val p=s.getJSONArray("point");PublishedStop(TrackPoint(p.getDouble(1),p.getDouble(0),0,0f),s.getLong("durationMs"))}
    }
    fun read(rows: List<JSONObject>, start: Long, end: Long): List<RidePause> {
        val result = mutableListOf<RidePause>()
        var open: RidePause? = null
        for (row in rows) {
            when (row.optString("event")) {
                "pause" -> if (open == null) {
                    val at = row.optLong("at", -1)
                    if (at in start..end) {
                        val point = row.optJSONObject("point")?.let { p -> runCatching {
                            TrackPoint(p.getDouble("lat"), p.getDouble("lon"), p.getLong("time"), p.getDouble("accuracy").toFloat())
                        }.getOrNull()?.takeIf { TrackMath.acceptable(null, it) && at - it.time in 0..30_000 } }
                        open = RidePause(at, point = point)
                    }
                }
                "resume" -> open?.let {
                    val at = row.optLong("at", -1)
                    if (at >= it.started) { result.add(it.copy(ended = at.coerceAtMost(end))); open = null }
                }
            }
        }
        open?.let { result.add(it) }
        return result
    }
    fun clipped(pauses: List<RidePause>, start: Long, end: Long): List<RidePause> = pauses.mapNotNull {
        val a = maxOf(start, it.started); val b = minOf(end, it.ended ?: end)
        if (b <= a) null else it.copy(started = a, ended = b, point = it.point.takeIf { _ -> a == it.started })
    }
    fun total(pauses: List<RidePause>, start: Long, end: Long): Long {
        var until = start; var total = 0L
        for (p in clipped(pauses, start, end).sortedBy { it.started }) {
            val b = p.ended!!; total += (b - maxOf(until, p.started)).coerceAtLeast(0); until = maxOf(until, b)
        }
        return total
    }
    fun row(pause: RidePause): JSONObject = JSONObject().put("event", "pause").put("at", pause.started).apply {
        pause.point?.let { put("point", JSONObject().put("lat", it.lat).put("lon", it.lon).put("time", it.time).put("accuracy", it.accuracy)) }
    }
}
