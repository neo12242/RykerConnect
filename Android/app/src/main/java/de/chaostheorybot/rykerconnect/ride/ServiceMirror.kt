package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Explicit projection: receipts, coordinates, vehicle identity and fuel costs never leave the phone. */
internal object ServiceProjection {
    private val fields = mapOf(
        "serviceTypes" to listOf("id", "name", "enabled", "configured", "intervalKm", "intervalDays"),
        "maintenance" to listOf("id", "name", "serviceId", "time", "odometerKm", "intervalKm", "intervalDays", "notes", "parts", "cost"),
        "fuel" to listOf("id", "time", "odometerKm"),
        "serviceBaselines" to listOf("id", "date", "odometerKm", "enabled")
    )

    fun project(wire: JSONObject, unpack: (Any?) -> Any? = { it }): JSONObject {
        val records = JSONObject()
        val source = wire.getJSONObject("records")
        source.keys().asSequence().sorted().forEach { key ->
            val path = key.split('/')
            if (path.size != 3 || path[0] != "software") return@forEach
            val allowed = fields[path[1]] ?: return@forEach
            val branches = source.getJSONArray(key)
            val projected = JSONArray()
            for (i in 0 until branches.length()) {
                val branch = branches.getJSONObject(i)
                val value = unpack(branch.get("value"))
                val safe = if (value == null || value == JSONObject.NULL) JSONObject.NULL else {
                    require(value is JSONObject) { "Service record could not be read; original retained" }
                    JSONObject().also { out -> allowed.filter { value.has(it) }.forEach { out.put(it, SyncJson.copy(value.get(it))) } }
                }
                projected.put(JSONObject().put("value", safe).put("clock", JSONObject(branch.getJSONObject("clock").toString())))
            }
            records.put(key, projected)
        }
        return JSONObject().put("version", 1).put("records", records)
    }
}

internal object ServiceMirror {
    fun sync(context: Context, wire: JSONObject): String {
        if (!DadRides.enabled() || DadRides.origin().isBlank()) return ""
        val origin = DadRides.origin()
        val prefs = context.getSharedPreferences("service_mirror", 0)
        val prefix = SyncJson.fingerprint(origin)
        val fingerprint = SyncJson.fingerprint(wire)
        val now = System.currentTimeMillis()
        if (prefs.getString("$prefix.hash", "") == fingerprint && now - prefs.getLong("$prefix.last", 0) in 0..899_999) return "Services synchronized"
        val records = if (prefs.getString("$prefix.hash", "") == fingerprint) JSONObject() else wire.getJSONObject("records")
        val pages = records.keys().asSequence().toList().chunked(20).ifEmpty { listOf(emptyList()) }
        for ((index, keys) in pages.withIndex()) {
            check(DadRides.enabled() && DadRides.origin() == origin) { "Website connection changed; service sync paused" }
            val page = JSONObject().put("version", 1).put("complete", index == pages.lastIndex).put("records", JSONObject().also { out -> keys.forEach { out.put(it, records.get(it)) } })
            val bytes = page.toString().toByteArray()
            check(bytes.size <= 2_000_000) { "Resolve service conflicts before syncing this service history" }
            DadRides.request("services/sync", "POST", bytes)
        }
        check(DadRides.origin() == origin) { "Website connection changed; retry service sync" }
        prefs.edit().putString("$prefix.hash", fingerprint).putLong("$prefix.last", now).commit()
        return "Services synchronized"
    }
}
