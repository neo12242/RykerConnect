package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object MaintenancePlans {
    val states = listOf("Planned", "In progress", "Completed", "Canceled")
    fun validate(p:JSONObject) {
        require(p.getString("id").matches(Regex("[a-f0-9-]{36}")))
        require(p.getString("serviceId").length in 1..100 && p.getString("serviceId")!=ServiceCatalog.MILEAGE)
        require(p.getString("state") in states && p.optString("notes").length<=2000)
        p.optString("target").takeIf{it.isNotBlank()}?.let{java.time.LocalDate.parse(it)}
        for(key in listOf("tasks","parts")) {
            val list=p.getJSONArray(key);require(list.length()<=100)
            val ids=mutableSetOf<String>()
            for(i in 0 until list.length()){
                val x=list.getJSONObject(i);require(ids.add(x.getString("id")) && x.getString("id").matches(Regex("[a-f0-9-]{36}")))
                require(x.getString("name").length in 1..200 && x.get("done") is Boolean)
                if(key=="parts")require(x.getDouble("quantity") in 0.01..10000.0 && x.getDouble("cost") in 0.0..100000.0)
            }
        }
    }
    fun create(serviceId:String):String {
        require(serviceId!=ServiceCatalog.MILEAGE)
        require(ServiceCatalog.items(SoftwareStore.snapshot()).any{it.optString("id")==serviceId && it.optBoolean("enabled")}){"Choose an enabled maintenance service"}
        val id=UUID.randomUUID().toString()
        save(JSONObject().put("id",id).put("serviceId",serviceId).put("state","Planned").put("target","").put("notes","").put("tasks",JSONArray()).put("parts",JSONArray()))
        return id
    }
    fun find(id:String)=SoftwareStore.records("plans").firstOrNull{it.getString("id")==id}
    fun save(p:JSONObject){validate(p);SoftwareStore.mutate{next->
        val list=next.optJSONArray("plans")?:JSONArray();val out=JSONArray();var found=false
        for(i in 0 until list.length()){val item=list.getJSONObject(i);if(item.getString("id")==p.getString("id")){out.put(p);found=true}else out.put(item)}
        if(!found){require(out.length()<10000);out.put(p)};next.put("plans",out)
    }}
    fun completionExists(p:JSONObject)=SoftwareStore.records("maintenance").any{it.optString("id")==p.optString("completionId")}
    /** The service and plan are committed in one existing atomic SoftwareStore replacement. */
    fun complete(id:String,record:JSONObject){SoftwareStore.mutate{next->completeIn(next,id,record)}}
    internal fun completeIn(next:JSONObject,id:String,record:JSONObject){
        val plans=next.getJSONArray("plans");val p=(0 until plans.length()).map{plans.getJSONObject(it)}.first{it.getString("id")==id}
        val history=next.optJSONArray("maintenance")?:JSONArray()
        if((0 until history.length()).any{history.getJSONObject(it).optString("planId")==id})return
        require(p.getString("state") in listOf("Planned","In progress")){"This plan is not open"}
        require(record.getString("serviceId")==p.getString("serviceId") && record.getString("serviceId")!=ServiceCatalog.MILEAGE){"The recorded service must match the plan"}
        val recordId=UUID.randomUUID().toString()
        history.put(JSONObject(record.toString()).put("id",recordId).put("planId",id))
        p.put("state","Completed").put("completionId",recordId)
        next.put("maintenance",history)
    }
    fun rows(p:JSONObject,key:String)=p.getJSONArray(key).let{a->(0 until a.length()).map{a.getJSONObject(it)}}
    fun estimate(p:JSONObject)=rows(p,"parts").sumOf{it.getDouble("quantity")*it.getDouble("cost")}
}
