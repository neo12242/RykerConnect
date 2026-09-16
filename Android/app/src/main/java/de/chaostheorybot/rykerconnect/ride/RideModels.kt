package de.chaostheorybot.rykerconnect.ride

import kotlin.math.*

data class NavigationFrame(
    val instruction: String = "", val direction: String = "", val meters: Double? = null,
    val arrival: String = "", val updated: Long = 0, val source: String = "Google Maps",
    val active: Boolean = false
) {
    fun stale(now: Long) = active && now - updated > 90_000
}

object MapsParser {
    fun parse(title: String, text: String, subText: String, now: Long): NavigationFrame {
        val instruction = listOf(title, text).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val lower = instruction.lowercase(java.util.Locale.US)
        val direction = when {
            Regex("\\bu[- ]?turn\\b").containsMatchIn(lower) -> "uturn"
            "roundabout" in lower -> "roundabout"
            Regex("\\b(turn|keep|bear|slight|sharp)\\s+(slightly\\s+)?left\\b").containsMatchIn(lower) -> "left"
            Regex("\\b(turn|keep|bear|slight|sharp)\\s+(slightly\\s+)?right\\b").containsMatchIn(lower) -> "right"
            Regex("\\b(continue straight|head north|head south|head east|head west)\\b").containsMatchIn(lower) -> "straight"
            "you have arrived" in lower || "you've arrived" in lower -> "arrive"
            else -> ""
        }
        val match = Regex("(?<![\\w.])(\\d+(?:[.,]\\d+)?)\\s*(km|mi|miles?|ft|feet|meters?|metres?|m)\\b", RegexOption.IGNORE_CASE).find(title)
        val meters = match?.let {
            val raw = it.groupValues[1]
            val value = (if (Regex("\\d{1,3},\\d{3}").matches(raw)) raw.replace(",", "") else raw.replace(",", ".")).toDoubleOrNull() ?: return@let null
            value * when (it.groupValues[2].lowercase()) { "km" -> 1000.0; "mi", "mile", "miles" -> 1609.344; "ft", "feet" -> 0.3048; else -> 1.0 }
        }
        return NavigationFrame(instruction.take(240), direction, meters, subText.take(80), now, active = instruction.isNotBlank())
    }
}

object RideUnits {
    fun distance(meters: Double, imperial: Boolean): String = if (imperial) {
        if (meters < 160.9344) "${(meters / 0.3048).roundToInt()} ft" else String.format(java.util.Locale.US, "%.1f mi", meters / 1609.344)
    } else if (meters < 1000) "${meters.roundToInt()} m" else String.format(java.util.Locale.US, "%.1f km", meters / 1000)
    fun celsiusToFahrenheit(c: Double) = c * 9 / 5 + 32
}

/** Repeated IDs may contain new messages; compare content, and expire duplicates. */
class NotificationDeduper {
    private val seen = LinkedHashMap<String, Pair<Int, Long>>()
    fun accept(key: String, content: String, now: Long): Boolean {
        val previous = seen[key]
        val hash = content.hashCode()
        if (previous != null && previous.first == hash && now - previous.second in 0..10_000) return false
        seen[key] = hash to now
        while (seen.size > 100) seen.remove(seen.keys.first())
        return true
    }
    fun remove(key: String) { seen.remove(key) }
}

data class TrackPoint(val lat: Double, val lon: Double, val time: Long, val accuracy: Float, val speed: Double? = null, val segmentStart: Boolean = false, val altitude: Double? = null)
object NotificationText {
    fun bounded(value: String, bytes: Int): String {
        var text = value.replace('\u0003', ' ')
        while (text.toByteArray(Charsets.UTF_8).size > bytes) text = text.substring(0, text.offsetByCodePoints(text.length, -1))
        return text
    }
}

object TrackMath {
    fun distance(a: TrackPoint, b: TrackPoint): Double {
        val lat = Math.toRadians(b.lat - a.lat); val lon = Math.toRadians(b.lon - a.lon)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lon / 2).pow(2)
        return 6371000 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
    }
    fun acceptable(previous: TrackPoint?, point: TrackPoint): Boolean {
        if (!point.lat.isFinite() || !point.lon.isFinite() || point.lat !in -90.0..90.0 || point.lon !in -180.0..180.0 || !point.accuracy.isFinite() || point.accuracy !in 0f..50f) return false
        if (previous == null) return true
        val seconds = (point.time - previous.time) / 1000.0
        return seconds > 0 && distance(previous, point) / seconds < 80
    }
}
