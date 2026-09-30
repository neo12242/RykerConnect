package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

internal object SyncJson {
    fun canonical(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(v.get(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonical(v.get(it)) }
        is String -> JSONObject.quote(v)
        is Number -> JSONObject.numberToString(v)
        is Boolean -> v.toString()
        else -> error("Unsupported library value")
    }
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun fingerprint(value: Any?) = hash(canonical(value).toByteArray())
    fun copy(v: Any?): Any = when(v) { null,JSONObject.NULL -> JSONObject.NULL; is JSONObject -> JSONObject(v.toString()); is JSONArray -> JSONArray(v.toString()); else -> v }
}

/** Causal, per-record replication. Competing branches survive until explicit resolution. */
internal class LibraryRecords(val state: JSONObject = JSONObject()) {
    val node: String
    private val records: JSONObject
    init {
        if (!state.has("version")) state.put("version",1).put("node",UUID.randomUUID().toString()).put("records",JSONObject())
        require(state.getInt("version")==1) { "Unsupported library version; original state retained" }
        node=state.getString("node"); records=state.getJSONObject("records")
    }
    fun keys() = records.keys().asSequence().toSet()
    fun branches(key:String):List<JSONObject> = records.optJSONObject(key)?.optJSONArray("branches")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun observed(key:String) = records.optJSONObject(key)?.optString("observed") ?: SyncJson.fingerprint(null)
    fun conflicts() = keys().filter { branches(it).size>1 }
    fun desired(key:String):Any? = branches(key).singleOrNull()?.get("value")
    fun wire():JSONObject = JSONObject().put("version",1).put("records",JSONObject().also { out -> keys().sorted().forEach { out.put(it,JSONArray(branches(it).map { b -> JSONObject(b.toString()) })) } })
    private fun mergedClock(clocks:List<JSONObject>):JSONObject = JSONObject().also { out -> clocks.forEach { c -> c.keys().forEach { k -> out.put(k,maxOf(out.optLong(k),c.getLong(k))) } } }
    private fun dominates(a:JSONObject,b:JSONObject):Boolean = b.keys().asSequence().all { a.optLong(it)>=b.getLong(it) }
    private fun normalize(input:List<JSONObject>):List<JSONObject> {
        val same = input.groupBy { SyncJson.fingerprint(it.get("value")) }.values.map { group ->
            JSONObject().put("value",SyncJson.copy(group.first().get("value"))).put("clock",mergedClock(group.map { it.getJSONObject("clock") }))
        }
        return same.filterIndexed { i,b -> same.indices.none { j -> j!=i && dominates(same[j].getJSONObject("clock"),b.getJSONObject("clock")) && !dominates(b.getJSONObject("clock"),same[j].getJSONObject("clock")) } }
    }
    private fun entry(key:String)=records.optJSONObject(key) ?: JSONObject().put("branches",JSONArray()).put("observed",SyncJson.fingerprint(null)).put("applied",JSONObject()).also { records.put(key,it) }
    fun observe(key:String,value:Any?):Boolean {
        if(!records.has(key) && (value==null || value==JSONObject.NULL))return false
        val e=entry(key); val fingerprint=SyncJson.fingerprint(value)
        if(e.getString("observed")==fingerprint)return false
        // A local edit descends from the version actually displayed, not unseen competing data.
        val clock=JSONObject(e.getJSONObject("applied").toString()).put(node,maxOf(e.getJSONObject("applied").optLong(node),branches(key).maxOfOrNull{it.getJSONObject("clock").optLong(node)}?:0)+1)
        val branch=JSONObject().put("value",SyncJson.copy(value)).put("clock",clock)
        e.put("branches",JSONArray(normalize(branches(key)+branch))).put("observed",fingerprint).put("applied",clock)
        return true
    }
    /** Persist an intent before changing files, so a restart can finish the deletion. */
    fun remove(key:String) {
        if(!records.has(key))return
        val e=entry(key)
        val clock=JSONObject(e.getJSONObject("applied").toString()).put(node,
            maxOf(e.getJSONObject("applied").optLong(node),branches(key).maxOfOrNull{it.getJSONObject("clock").optLong(node)}?:0)+1)
        e.put("branches",JSONArray(normalize(branches(key)+JSONObject().put("value",JSONObject.NULL).put("clock",clock))))
    }
    /** A confirmed replacement is durable before its files become visible. */
    fun replace(key:String,value:Any) {
        check(branches(key).size<=1){"Resolve the library conflict before restoring this ride"}
        val e=entry(key)
        val clock=JSONObject(e.getJSONObject("applied").toString()).put(node,
            maxOf(e.getJSONObject("applied").optLong(node),branches(key).maxOfOrNull{it.getJSONObject("clock").optLong(node)}?:0)+1)
        e.put("branches",JSONArray(normalize(branches(key)+JSONObject().put("value",SyncJson.copy(value)).put("clock",clock))))
    }
    fun merge(wire:JSONObject,validateKey:(String)->Unit) {
        require(wire.getInt("version")==1)
        val incoming=wire.getJSONObject("records");require(incoming.length()<=50000){"Library record limit exceeded"}
        // Validate the entire envelope before changing any local records.
        incoming.keys().forEach { key ->
            validateKey(key);val list=incoming.getJSONArray(key);require(list.length() in 1..100)
            for(i in 0 until list.length()) {
                val b=list.getJSONObject(i);require(b.has("value"));val clock=b.getJSONObject("clock");require(clock.length() in 1..100)
                clock.keys().forEach { require(runCatching{UUID.fromString(it)}.isSuccess && clock.getLong(it) in 1 until Long.MAX_VALUE) }
            }
        }
        incoming.keys().forEach { key ->
            val a=incoming.getJSONArray(key);entry(key).put("branches",JSONArray(normalize(branches(key)+(0 until a.length()).map { a.getJSONObject(it) })))
        }
    }
    fun applied(key:String,value:Any?) {
        val b=branches(key).single();require(SyncJson.fingerprint(value)==SyncJson.fingerprint(b.get("value")))
        entry(key).put("observed",SyncJson.fingerprint(value)).put("applied",JSONObject(b.getJSONObject("clock").toString()))
    }
    fun resolve(key:String,index:Int) {
        val list=branches(key);val value=list[index].get("value")
        val clock=mergedClock(list.map{it.getJSONObject("clock")});clock.put(node,clock.optLong(node)+1)
        entry(key).put("branches",JSONArray(listOf(JSONObject().put("value",SyncJson.copy(value)).put("clock",clock))))
    }
}
