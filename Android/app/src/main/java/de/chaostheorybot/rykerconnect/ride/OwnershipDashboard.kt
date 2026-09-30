package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object ModificationRecords {
    private val uuid = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    val states = listOf("Planned", "Purchased", "Installed")
    fun create(title: String = "") = JSONObject().put("version", 1).put("id", UUID.randomUUID().toString())
        .put("title", title).put("status", "Planned").put("estimateCents", JSONObject.NULL)
        .put("installedDate", "").put("installedKm", JSONObject.NULL).put("notes", "")
        .put("archived", false).put("expenses", JSONArray())
    fun rows(m: JSONObject): List<JSONObject> = m.getJSONArray("expenses").let { a -> (0 until a.length()).map(a::getJSONObject) }
    fun cents(j: JSONObject, key: String): Long? = if (j.isNull(key)) null else j.getLong(key)
    fun parseMoney(text: String): Long? {
        if (text.isBlank()) return null
        require(text.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) { "Use a USD amount with up to two decimal places" }
        return BigDecimal(text).movePointRight(2).longValueExact().also { require(it in 0..100_000_000) { "Amount is too large" } }
    }
    private fun validMoney(j: JSONObject, key: String) {
        require(j.has(key)) { "Missing amount field" }
        if (!j.isNull(key)) { val n = j.get(key); require(n is Number && n.toDouble().isFinite() && n.toDouble() == n.toLong().toDouble() && n.toLong() in 0..100_000_000) { "Invalid amount" } }
    }
    fun validate(m: JSONObject) {
        require(m.keys().asSequence().toSet() == setOf("version","id","title","status","estimateCents","installedDate","installedKm","notes","archived","expenses")) { "Invalid modification fields" }
        require(m.getInt("version") == 1 && uuid.matches(m.getString("id")))
        require(m.getString("title").isNotBlank() && m.getString("title").length <= 100 && m.getString("notes").length <= 2000)
        require(m.getString("status") in states && m.get("archived") is Boolean)
        validMoney(m,"estimateCents")
        m.getString("installedDate").takeIf { it.isNotBlank() }?.let { require(LocalDate.parse(it).toString() == it) }
        if (!m.isNull("installedKm")) require(m.getDouble("installedKm").let { it.isFinite() && it in 0.0..2_000_000.0 })
        val expenses=rows(m);require(expenses.size<=100 && expenses.map { it.getString("id") }.distinct().size==expenses.size)
        expenses.forEach { e ->
            require(e.keys().asSequence().toSet()==setOf("id","date","label","amountCents"))
            require(uuid.matches(e.getString("id")) && e.getString("label").isNotBlank() && e.getString("label").length<=120)
            require(LocalDate.parse(e.getString("date")).toString()==e.getString("date"));validMoney(e,"amountCents")
        }
    }
    fun save(m: JSONObject, expected: String?) {
        validate(m)
        SoftwareStore.mutate { data ->
            val path=listOf("modifications",m.getString("id"))
            val current=LibraryData.softwareValue(data,path)
            require(expected==null && current==null || expected!=null && SyncJson.fingerprint(current)==expected) { "This modification changed. Reopen it before saving; your draft is still here." }
            LibraryData.setSoftwareValue(data,path,m)
        }
    }
}

object OwnershipSeasons {
    fun validate(s: JSONObject) {
        require(s.getString("id").matches(Regex("[a-f0-9-]{36}")))
        require(s.getString("name").isNotBlank() && s.getString("name").length<=80)
        require(LocalDate.parse(s.getString("from"))<=LocalDate.parse(s.getString("to"))) { "Season end must follow its start" }
        for(k in listOf("startKm","endKm"))if(!s.isNull(k))require(s.getDouble(k).let{it.isFinite() && it in 0.0..2_000_000.0})
        if(!s.isNull("startKm")&&!s.isNull("endKm"))require(s.getDouble("endKm")>=s.getDouble("startKm")) { "End mileage must be at least start mileage" }
    }
}

data class OwnershipExpense(val id:String,val date:LocalDate,val category:String,val title:String,val cents:Long?,val notes:String="",val odometerKm:Double?=null)
data class OwnershipPeriod(val name:String,val from:LocalDate,val to:LocalDate,val startKm:Double?=null,val endKm:Double?=null) {
    fun includes(date:LocalDate)=date>=from && date<=to
    fun distanceKm(today:LocalDate=LocalDate.now()):Double? = if(to>today || startKm==null || endKm==null || endKm<=startKm)null else endKm-startKm
}
object OwnershipMath {
    fun records(data:JSONObject):List<OwnershipExpense> {
        fun rows(key:String)=data.optJSONArray(key)?.let { a -> (0 until a.length()).map(a::getJSONObject) }.orEmpty()
        val result=mutableListOf<OwnershipExpense>()
        for(key in listOf("fuel","maintenance"))for(r in rows(key)) {
            if(key=="maintenance" && ServiceCatalog.isMileage(r))continue
            val date=Instant.ofEpochMilli(r.getLong("time")).atZone(ZoneId.systemDefault()).toLocalDate()
            val cents=if(!r.has("cost")||r.isNull("cost"))null else BigDecimal.valueOf(r.getDouble("cost")).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
            result.add(OwnershipExpense(r.getString("id"),date,if(key=="fuel")"Fuel" else "Maintenance",if(key=="fuel")"Fuel stop" else r.getString("name"),cents,r.optString("notes"),r.optDouble("odometerKm").takeIf{it.isFinite()}))
        }
        for(m in rows("modifications"))for(e in ModificationRecords.rows(m))result.add(OwnershipExpense(e.getString("id"),LocalDate.parse(e.getString("date")),"Modifications",m.getString("title")+" · "+e.getString("label"),ModificationRecords.cents(e,"amountCents"),m.optString("notes")))
        return result.sortedWith(compareByDescending<OwnershipExpense>{it.date}.thenBy{it.id})
    }
    fun total(rows:List<OwnershipExpense>)=rows.sumOf{it.cents?:0L}
    fun perKm(rows:List<OwnershipExpense>,period:OwnershipPeriod):Double? {
        if(rows.any{it.cents==null})return null
        return period.distanceKm()?.let{total(rows)/100.0/it}
    }
    fun money(cents:Long)=java.lang.String.format(java.util.Locale.US,"$%,.2f",cents/100.0)
    fun csv(rows:List<OwnershipExpense>,imperial:Boolean):String {
        val header=listOf("Date","Category","Description","Cost (USD)","Odometer (${if(imperial)"mi" else "km"})","Notes")
        return (listOf(header)+rows.map{r->listOf(r.date.toString(),r.category,r.title,r.cents?.let{BigDecimal.valueOf(it,2).toPlainString()}?:"Unknown",r.odometerKm?.let{String.format(java.util.Locale.US,"%.1f",it/(if(imperial)1.609344 else 1.0))}?:"",r.notes)}).joinToString("\r\n",postfix="\r\n"){it.joinToString(",",transform=GarageReport::cell)}
    }
}
