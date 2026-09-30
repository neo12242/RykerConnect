package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object VehicleProfile {
    fun validate(j: JSONObject) {
        for ((key, limit) in mapOf("nickname" to 60, "model" to 80, "home" to 300, "vin" to 17)) {
            require(!j.has(key) || j.get(key) is String)
            require(j.optString(key).length <= limit) { "$key is too long" }
        }
        val vin = j.optString("vin")
        require(vin.isBlank() || vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) { "VIN must contain 17 letters/numbers, excluding I, O and Q" }
        require(!j.has("year") || j.getDouble("year").let { it == it.toInt().toDouble() && it.toInt() in 1900..LocalDate.now().year + 1 }) { "Enter a valid model year" }
        require(!j.has("purchaseKm") || j.getDouble("purchaseKm").let { it.isFinite() && it in 0.0..2_000_000.0 }) { "Enter valid purchase mileage" }
        val date = j.optString("purchaseDate")
        require(date.isBlank() || !LocalDate.parse(date).isAfter(LocalDate.now())) { "Purchase date cannot be in the future" }
        val photo = j.optString("photo")
        require(photo.length <= 670_000 && photo.all { it.isLetterOrDigit() || it in "+/=" }) { "Invalid vehicle photo" }
    }
}

data class GarageFilter(val query: String = "", val type: String = "All", val serviceId: String = "",
    val from: LocalDate? = null, val to: LocalDate? = null, val minKm: Double? = null, val maxKm: Double? = null,
    val minCost: Double? = null, val maxCost: Double? = null) {
    fun validate() {
        require(from == null || to == null || from <= to) { "Start date must be before end date" }
        for ((a,b) in listOf(minKm to maxKm, minCost to maxCost)) {
            require(listOfNotNull(a,b).all { it.isFinite() && it >= 0 }) { "Enter non-negative numbers" }
            require(a == null || b == null || a <= b) { "Minimum must not exceed maximum" }
        }
    }
    fun matches(r: JSONObject): Boolean {
        val kind = GarageReport.kind(r)
        val date = Instant.ofEpochMilli(r.optLong("time")).atZone(ZoneId.systemDefault()).toLocalDate()
        val km = r.optDouble("odometerKm",0.0); val cost = r.optDouble("cost",0.0)
        return (type == "All" || type == kind) && (serviceId.isBlank() || r.optString("serviceId") == serviceId) &&
            (query.isBlank() || listOf(r.optString("name"),r.optString("notes"),r.optString("parts"),kind).any { it.contains(query.trim(),true) }) &&
            (from == null || date >= from) && (to == null || date <= to) &&
            (minKm == null || km >= minKm) && (maxKm == null || km <= maxKm) &&
            (minCost == null || cost >= minCost) && (maxCost == null || cost <= maxCost)
    }
}

object GarageReport {
    fun kind(r: JSONObject) = if (r.has("litres")) "Fuel" else if (ServiceCatalog.isMileage(r)) "Mileage" else "Service"
    fun spending(rows: List<JSONObject>, type: String) = rows.filter { kind(it) == type }.sumOf { it.optDouble("cost",0.0) }
    fun cell(value: String): String {
        // Quoting alone does not prevent spreadsheet formula execution.
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun csv(rows: List<JSONObject>, imperial: Boolean): String {
        val header = listOf("Record ID","Date","Type","Service","Odometer (${if(imperial)"mi" else "km"})","Cost (USD)","Fuel (${if(imperial)"US gal" else "L"})","Notes","Parts")
        return (listOf(header) + rows.map { r -> listOf(r.optString("id"),Instant.ofEpochMilli(r.optLong("time")).toString(),
            kind(r),r.optString("name"),String.format(Locale.US,"%.3f",r.optDouble("odometerKm",0.0)/(if(imperial)1.609344 else 1.0)),
            String.format(Locale.US,"%.2f",r.optDouble("cost",0.0)),
            if(r.has("litres")) String.format(Locale.US,"%.3f",r.getDouble("litres")/(if(imperial)3.785411784 else 1.0)) else "",
            r.optString("notes"),r.optString("parts")) }).joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } } + "\r\n"
    }
}

data class ServiceAlert(val id: String, val name: String, val baseline: String, val remainingKm: Double?, val dueTime: Long?, val overdue: Boolean, val approaching: Boolean) {
    val signature get() = "$baseline:${if(overdue)"due" else "soon"}"
}

object ServiceAlerts {
    const val DAY = 86_400_000L
    fun validate(settings: JSONObject) {
        if(settings.has("enabled")) require(settings.get("enabled") is Boolean)
        require(settings.optDouble("leadDays",30.0).let{it == it.toInt().toDouble() && it in 0.0..365.0})
        require(settings.optDouble("leadKm",500.0).let { it.isFinite() && it in 0.0..20_000.0 })
        require(settings.optDouble("snoozeDays",7.0).let{it == it.toInt().toDouble() && it in 1.0..90.0})
    }
    fun calculate(data: JSONObject, now: Long, leadKm: Double, leadDays: Int): List<ServiceAlert> {
        val history = data.optJSONArray("maintenance")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val fuel = data.optJSONArray("fuel")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val baselines = ServiceBaselines.records(data)
        val odo = ServiceBaselines.odometer(history + fuel, baselines)
        return ServiceCatalog.items(data).filter { it.optBoolean("enabled") && it.getString("id") != ServiceCatalog.MILEAGE }.mapNotNull { type ->
            val last = ServiceBaselines.startingPoint(type,history,baselines) ?: return@mapNotNull null
            val left = ServiceCatalog.remainingKm(type,history,odo,baselines)
            val date = ServiceBaselines.dueTime(last,type.optInt("intervalDays"))
            if(left == null && date == null) return@mapNotNull null
            val due = (left != null && left <= 0) || (date != null && date <= now)
            ServiceAlert(type.getString("id"),type.getString("name"),(if(last.has("date"))"new-bike:" else "")+last.getString("id")+":"+last.optLong("time")+":"+last.optDouble("odometerKm"),
                left,date,due,due || (left != null && left <= leadKm) || (date != null && date-now <= leadDays*DAY))
        }.sortedWith(compareByDescending<ServiceAlert> { it.overdue }.thenByDescending { it.approaching }.thenBy { it.dueTime ?: Long.MAX_VALUE }.thenBy { it.remainingKm ?: Double.MAX_VALUE })
    }
    fun shouldNotify(alert: ServiceAlert, state: JSONObject, now: Long): Boolean {
        val sameBaseline = state.optString("baseline") == alert.baseline
        if(sameBaseline && state.optLong("snoozeUntil") > now) return false
        return alert.approaching && (!sameBaseline || state.optString("signature") != alert.signature ||
            (state.optLong("snoozeUntil") > 0 && state.optLong("snoozeUntil") <= now))
    }
}
