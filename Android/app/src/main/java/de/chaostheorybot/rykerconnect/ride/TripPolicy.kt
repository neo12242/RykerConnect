package de.chaostheorybot.rykerconnect.ride

/** Pure connection policy. Monotonic time controls grace; wall time dates saved trips. */
class TripPolicy(private val grace: Long = 120_000) {
    var disconnectedAt: Long? = null; private set
    private var disconnectedWall = 0L
    private var suppressed = false
    private var previousConnected = false
    enum class Action { NONE, START, FINISH }
    var finishAt = 0L; private set
    val isSuppressed: Boolean get() = suppressed
    fun manualStop() { suppressed = disconnectedAt == null }
    fun tick(connected: Boolean, enabled: Boolean, recording: Boolean, automatic: Boolean, now: Long, wall: Long, paused: Boolean = false): Action {
        if (!connected && previousConnected) suppressed = false
        previousConnected = connected
        if (!recording) {
            disconnectedAt = null
            return if (connected && enabled && !suppressed) Action.START else Action.NONE
        }
        if (paused) { disconnectedAt = null; return Action.NONE }
        if (!automatic) return Action.NONE
        if (!enabled) { finishAt = wall; disconnectedAt = null; return Action.FINISH }
        // Expiry wins even if the first tick after a long sleep sees a reconnect.
        val lost = disconnectedAt
        if (lost != null && now - lost >= grace) { finishAt = disconnectedWall; disconnectedAt = null; return Action.FINISH }
        if (connected) disconnectedAt = null
        else if (lost == null) { disconnectedAt = now; disconnectedWall = wall }
        return Action.NONE
    }
}

data class TripStats(val meters: Double, val movingMs: Long, val stoppedMs: Long, val unknownMs: Long, val maxMps: Double?, val averageMps: Double?, val gaps: Int, val pausedMs: Long = 0)
object TripAnalysis {
    fun segments(points: List<TrackPoint>): List<List<TrackPoint>> {
        val result = mutableListOf<MutableList<TrackPoint>>()
        for (p in points) {
            if (result.isEmpty() || p.segmentStart || p.time - result.last().last().time >= 30_000 || p.time <= result.last().last().time) result.add(mutableListOf())
            result.last().add(p)
        }
        return result
    }
    fun stats(points: List<TrackPoint>, started: Long, ended: Long, modern: Boolean, pauses: List<RidePause> = emptyList()): TripStats {
        var meters = 0.0; var moving = 0L; var stopped = 0L; var max: Double? = null; var measured = 0L
        for ((a, b) in points.zipWithNext()) {
            val dt = b.time - a.time
            if (b.segmentStart || dt <= 0 || dt >= (if (modern) 30_000 else 120_000) || pauses.any { it.started < b.time && (it.ended ?: ended) > a.time }) continue
            val distance = TrackMath.distance(a, b)
            val speed = if (modern) b.speed else null
            val significant = distance >= maxOf(5.0, (a.accuracy + b.accuracy) / 2.0)
            if (significant && (!modern || speed == null || speed >= 1.0)) meters += distance
            if (modern && speed != null) {
                measured += dt
                if (speed >= 1.0) moving += dt else stopped += dt
                max = maxOf(max ?: 0.0, speed)
            }
        }
        val paused = RidePauses.total(pauses, started, ended)
        return TripStats(meters, moving, stopped, ((ended - started) - measured - paused).coerceAtLeast(0), max,
            if (moving > 0) meters / (moving / 1000.0) else null, (segments(points).size - 1).coerceAtLeast(0), paused)
    }
}
