package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object SoftwareStore {
    val revision = MutableStateFlow(0)
    private lateinit var file: File
    private var data = JSONObject()
    private var readFailure = false
    fun init(context: Context) {
        initFile(File(context.noBackupFilesDir, "software.json"))
        if (!readFailure) runCatching {
            if (data.optInt("serviceCatalogVersion") < 1 && file.exists()) {
                val backup = File(file.path + ".before-services-v1")
                if (!backup.exists()) file.copyTo(backup)
            }
            commit(ServiceCatalog.migrate(data))
        }.onFailure { readFailure = true; android.util.Log.e("SoftwareStore", "Service migration failed; original data retained", it) }
    }
    @Synchronized internal fun initFile(target: File) {
        file = target
        // A damaged file is never silently replaced by an empty database.
        readFailure = false
        data = runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }.getOrElse { readFailure = true; JSONObject() }
    }
    @Synchronized fun snapshot(): JSONObject = JSONObject(data.toString())
    @Synchronized fun records(key: String): List<JSONObject> = data.optJSONArray(key)?.let { a -> (0 until a.length()).map { JSONObject(a.getJSONObject(it).toString()) } }.orEmpty()
    @Synchronized fun value(key: String) = data.optString(key)
    @Synchronized fun commit(next: JSONObject) {
        check(!readFailure) { "Tools data could not be read; original file retained" }
        val temp = File(file.path + ".tmp"); temp.writeText(next.toString())
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        data = JSONObject(next.toString()); revision.value++
    }
    @Synchronized fun mutate(change: (JSONObject)->Unit) { val next=snapshot(); change(next); commit(next) }
    @Synchronized fun set(key: String, value: String) { commit(snapshot().put(key, value)) }
    @Synchronized fun add(key: String, item: JSONObject) {
        val next = snapshot(); val list = next.optJSONArray(key) ?: JSONArray()
        require(list.length() < 10_000) { "Record limit reached" }
        list.put(JSONObject(item.toString()).put("id", UUID.randomUUID().toString())); commit(next.put(key, list))
    }
    @Synchronized fun update(key: String, id: String, item: JSONObject) {
        val list=records(key); require(list.any { it.optString("id")==id }) { "Entry no longer exists" }
        commit(snapshot().put(key, JSONArray(list.map { if(it.optString("id")==id) JSONObject(item.toString()).put("id",id) else it })))
    }
    @Synchronized fun remove(key: String, id: String) { commit(snapshot().put(key, JSONArray(records(key).filter { it.optString("id") != id }))) }
    fun favorite(id: String) = snapshot().optJSONObject("favorites")?.optBoolean(id) == true
    @Synchronized fun favorite(id: String, checked: Boolean) { val next = snapshot(); val f = next.optJSONObject("favorites") ?: JSONObject(); f.put(id, checked); commit(next.put("favorites", f)) }
    fun preferences(p: RidePreferences = RideState.preferences.value): JSONObject = JSONObject()
        .put("imperial", p.imperial).put("fahrenheit", p.fahrenheit).put("twelve", p.twelveHour).put("large", p.largeText)
        .put("musicLeft", p.musicLeft).put("navigation", p.navigation).put("hide", p.hideBody).put("all", p.allowAll)
        .put("priority", p.navPriority).put("allowed", JSONArray(p.allowed.toList()))
    fun applyPreferences(j: JSONObject) {
        val old = RideState.preferences.value
        val apps = j.optJSONArray("allowed")
        RideState.save(old.copy(imperial = j.optBoolean("imperial", old.imperial), fahrenheit = j.optBoolean("fahrenheit", old.fahrenheit),
            twelveHour = j.optBoolean("twelve", old.twelveHour), largeText = j.optBoolean("large", old.largeText),
            musicLeft = j.optBoolean("musicLeft", old.musicLeft), navigation = j.optBoolean("navigation", old.navigation),
            hideBody = j.optBoolean("hide", old.hideBody), allowAll = j.optBoolean("all", old.allowAll), navPriority = j.optBoolean("priority", old.navPriority),
            allowed = apps?.let { (0 until it.length()).map { i -> it.getString(i) }.toSet() } ?: old.allowed))
    }
    @Synchronized fun restore(imported: JSONObject) { commit(merge(imported)) }
    @Synchronized fun merge(imported: JSONObject): JSONObject {
        val next = ServiceCatalog.migrate(snapshot())
        val incomingData = ServiceCatalog.migrate(imported)
        val catalog = ServiceCatalog.items(next).associateBy { it.getString("id") }.toMutableMap()
        ServiceCatalog.items(incomingData).forEach { item ->
            val old = catalog[item.getString("id")]
            if (old == null || (!old.optBoolean("configured") && item.optBoolean("configured"))) catalog[item.getString("id")] = item
        }
        next.put("serviceTypes", JSONArray(catalog.values.toList()))
        for (key in listOf("fuel", "maintenance", "profiles", "plans", "journals")) {
            val currentArray = next.optJSONArray(key) ?: JSONArray()
            val current = (0 until currentArray.length()).map { currentArray.getJSONObject(it) }.toMutableList(); val ids = current.map { it.getString("id") }.toSet()
            val incoming = incomingData.optJSONArray(key) ?: continue
            for (i in 0 until incoming.length()) { val item = incoming.getJSONObject(i); if (item.getString("id") !in ids) current.add(item) }
            next.put(key, JSONArray(current))
        }
        val favorites = next.optJSONObject("favorites") ?: JSONObject()
        imported.optJSONObject("favorites")?.let { f -> f.keys().forEach { if (!favorites.has(it)) favorites.put(it, f.getBoolean(it)) } }
        next.put("favorites", favorites)
        if (!next.has("parking") && imported.has("parking")) next.put("parking", imported.getJSONObject("parking"))
        for (key in listOf("vehicle", "reminders", "widgetOptions")) {
            if (!next.has(key) && imported.has(key)) next.put(key, JSONObject(imported.getJSONObject(key).toString()))
        }
        return next
    }
}

data class FuelEconomy(val litresPer100Km:Double,val fullTank:Boolean)
object GarageMath {
    fun forEntry(entries:List<JSONObject>,id:String):FuelEconomy? {
        val sorted=entries.sortedWith(compareBy<JSONObject>{it.getDouble("odometerKm")}.thenBy{it.optLong("time")})
        val end=sorted.indexOfFirst{it.optString("id")==id}
        if(end<=0 || !sorted[end].optBoolean("calculateMpg",true))return null
        val previousFull=(0 until end).lastOrNull{sorted[it].optBoolean("full")}
        val fullTank=sorted[end].optBoolean("full") && previousFull!=null
        val start=if(fullTank)previousFull!! else end-1
        val distance=sorted[end].getDouble("odometerKm")-sorted[start].getDouble("odometerKm")
        if(distance<=0 || (start+1..end).any{sorted[it].optBoolean("missedFill")})return null
        return FuelEconomy((start+1..end).sumOf{sorted[it].getDouble("litres")}*100/distance,fullTank)
    }

    fun fuel(odometerKm: Double, litres: Double, cost: Double) {
        require(odometerKm.isFinite() && odometerKm in 0.0..2_000_000.0) { "Enter a valid odometer" }
        require(litres.isFinite() && litres in 0.01..200.0) { "Enter a valid fuel amount" }
        require(cost.isFinite() && cost in 0.0..100_000.0) { "Enter a valid cost" }
    }
    fun consumption(entries: List<JSONObject>): Double? {
        val sorted = entries.sortedBy { it.getDouble("odometerKm") }
        val full = sorted.indices.filter { sorted[it].optBoolean("full") }
        if (full.size < 2) return null
        val start = full[full.lastIndex - 1]; val end = full.last()
        val distance = sorted[end].getDouble("odometerKm") - sorted[start].getDouble("odometerKm")
        if (distance <= 0 || (start+1..end).any{sorted[it].optBoolean("missedFill")}) return null
        return (start + 1..end).sumOf { sorted[it].getDouble("litres") } * 100 / distance
    }
}
