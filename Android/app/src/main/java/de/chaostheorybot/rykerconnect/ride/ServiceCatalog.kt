package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ServiceCatalog {
    const val MILEAGE = "mileage-record"
    private val defaults = listOf("Engine oil & filter", "Engine air filter", "CVT air filter", "CVT drive belt",
        "Gearbox / final drive oil", "Coolant", "Brake inspection", "Brake fluid", "Spark plugs", "Tires", "Battery")
    fun id(name: String): String = UUID.nameUUIDFromBytes(name.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8)).toString()
    fun definition(name: String, key: String = id(name)) = JSONObject().put("id", key).put("name", name.trim())
        .put("enabled", true).put("intervalKm", 0.0).put("intervalDays", 0).put("configured", false)
    fun items(data: JSONObject): List<JSONObject> = data.optJSONArray("serviceTypes")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }
    }.orEmpty()
    fun isMileage(record: JSONObject) = record.optString("serviceId") == MILEAGE
    fun validate(item: JSONObject) {
        require(item.getString("id").length in 1..100)
        require(item.getString("name").trim().length in 1..100) { "Enter a service name" }
        require(item.get("enabled") is Boolean)
        val km = item.getDouble("intervalKm")
        require(km.isFinite() && km in 0.0..200_000.0) { "Distance interval must be 0–200,000 km" }
        val days = item.getDouble("intervalDays")
        require(days in 0.0..3650.0 && days == days.toInt().toDouble()) { "Days must be a whole number from 0–3650" }
        if (item.getString("id") == MILEAGE) require(km == 0.0 && days == 0.0 && item.getBoolean("enabled") && item.getString("name") == "Mileage record") { "Mileage record is always available and never recurring" }
    }
    fun migrate(source: JSONObject): JSONObject {
        val next = JSONObject(source.toString())
        val types = items(next).associateBy { it.getString("id") }.toMutableMap()
        require(types.size == items(next).size) { "Duplicate service IDs" }
        defaults.forEach { name -> types.putIfAbsent(id(name), definition(name)) }
        types.putIfAbsent(MILEAGE, definition("Mileage record", MILEAGE))
        val history = next.optJSONArray("maintenance") ?: JSONArray()
        val records = (0 until history.length()).map { history.getJSONObject(it) }
        for (record in records.sortedWith(compareBy<JSONObject> { it.optLong("time") }.thenBy { it.optDouble("odometerKm", 0.0) })) {
            val name = record.getString("name")
            val key = record.optString("serviceId").ifBlank { id(name) }
            record.put("serviceId", key)
            val type = types.getOrPut(key) { definition(name, key) }
            if (source.optInt("serviceCatalogVersion") < 1 && !items(source).any { it.optString("id") == key && it.optBoolean("configured") }) {
                type.put("intervalKm", record.optDouble("intervalKm", 0.0)).put("intervalDays", record.optInt("intervalDays"))
                    .put("configured", true)
            }
        }
        types.values.forEach(::validate)
        return next.put("serviceTypes", JSONArray(types.values.toList())).put("serviceCatalogVersion", 1)
    }
    fun latest(type: JSONObject, history: List<JSONObject>): JSONObject? =
        if (type.getString("id") == MILEAGE) null else history.filter { !isMileage(it) && it.optString("serviceId") == type.getString("id") }
            .maxWithOrNull(compareBy<JSONObject> { it.optLong("time") }.thenBy { it.optDouble("odometerKm") }.thenBy { it.optString("id") })
    fun remainingKm(type: JSONObject, history: List<JSONObject>, odometer: Double, baselines: List<JSONObject> = emptyList()): Double? =
        ServiceBaselines.startingPoint(type, history, baselines)?.takeIf { type.getDouble("intervalKm") > 0 }?.let { it.getDouble("odometerKm") + type.getDouble("intervalKm") - odometer }
}
