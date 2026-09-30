package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Starting points are not completed maintenance and never enter Garage spending/history. */
object ServiceBaselines {
    fun records(data: JSONObject): List<JSONObject> = data.optJSONArray("serviceBaselines")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }
    }.orEmpty()
    fun validate(item: JSONObject) {
        require(item.keys().asSequence().toSet() == setOf("id", "date", "odometerKm", "enabled")) { "Invalid service baseline fields" }
        require(item.getString("id").matches(Regex("[A-Za-z0-9_.-]{1,100}")) && item.getString("id") != ServiceCatalog.MILEAGE)
        val date = item.getString("date")
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(date).toString() == date && LocalDate.parse(date).year >= 1900) { "Use a valid date (YYYY-MM-DD)" }
        require(item.getDouble("odometerKm").let { it.isFinite() && it in 0.0..2_000_000.0 }) { "Enter a valid starting odometer" }
        require(item.get("enabled") is Boolean)
    }
    fun create(id: String, date: String, km: Double): JSONObject = JSONObject().put("id", id).put("date", date)
        .put("odometerKm", km).put("enabled", true).also(::validate)
    fun apply(data: JSONObject, date: String, km: Double): JSONObject {
        require(!LocalDate.parse(date).isAfter(LocalDate.now())) { "In-service date cannot be in the future" }
        val next = ServiceCatalog.migrate(data)
        val history = next.optJSONArray("maintenance")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val records = records(next).associateBy { it.getString("id") }.toMutableMap()
        for (type in ServiceCatalog.items(next).filter { it.optBoolean("enabled") && it.getString("id") != ServiceCatalog.MILEAGE }) {
            if (ServiceCatalog.latest(type, history) == null) records[type.getString("id")] = create(type.getString("id"), date, km)
        }
        return next.put("serviceBaselines", JSONArray(records.values.toList()))
    }
    fun remove(data: JSONObject): JSONObject = JSONObject(data.toString()).also { next ->
        next.put("serviceBaselines", JSONArray(records(next).map { JSONObject(it.toString()).put("enabled", false) }))
    }
    fun startingPoint(type: JSONObject, history: List<JSONObject>, baselines: List<JSONObject>): JSONObject? {
        ServiceCatalog.latest(type, history)?.let { return it }
        if (type.getString("id") == ServiceCatalog.MILEAGE) return null
        return baselines.firstOrNull { it.optBoolean("enabled") && it.optString("id") == type.getString("id") }?.let {
            validate(it)
            JSONObject(it.toString()).put("time", LocalDate.parse(it.getString("date")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }
    fun dueTime(start: JSONObject, days: Int): Long? = if (days <= 0) null else if (start.has("date"))
        LocalDate.parse(start.getString("date")).plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else start.getLong("time") + days * 86_400_000L
    fun odometer(readings: List<JSONObject>, baselines: List<JSONObject>): Double =
        (readings + baselines.filter { it.optBoolean("enabled") }).maxOfOrNull { it.optDouble("odometerKm", 0.0) } ?: 0.0
}
