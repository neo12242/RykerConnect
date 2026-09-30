package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicit inventory: private keys, upload jobs, Android grants and SDK caches are not replicated. */
internal object LibraryData {
    val arrays=setOf("serviceTypes","fuel","maintenance","profiles","plans","journals","siteEdits","serviceBaselines","modifications","ownershipSeasons")
    private val uuid=Regex("[a-f0-9-]{36}")
    fun validateKey(key:String) {
        val p=key.split('/');require(p.size in 2..3 && p.all{it.matches(Regex("[A-Za-z0-9_.-]{1,100}"))}){"Invalid library record path"}
        when(p[0]) {
            "trip" -> require(p.size==2 && p[1].matches(Regex("[a-f0-9-]{36}\\.(jsonl|meta.json)")))
            "photo" -> require(p.size==2 && p[1].matches(Regex("[a-f0-9-]{36}\\.(original|jpg|thumb.jpg)")))
            "software" -> require(if(p[1] in arrays || p[1]=="favorites")p.size==3 else p.size==2)
            "ridePreference" -> require(p.size==2 && p[1] in SoftwareStore.preferences().keys().asSequence().toSet())
            "appearance" -> require(p.size==2 && p[1] in BackupPreferences.appearanceKeys)
            "preference" -> require(p.size==2 && p[1]=="dynamicColor")
            else -> error("Unsupported library record")
        }
    }
    fun fileValue(file:File,json:Boolean):Any? = if(!file.exists())null else if(json)JSONObject(file.readText()) else SharedLibrary.blob(file)
    internal fun pack(value:Any?):Any? {
        if(value==null || value==JSONObject.NULL)return null
        val bytes=SyncJson.canonical(value).toByteArray()
        return if(bytes.size<=8192)value else SharedLibrary.blobBytes(bytes).let{JSONObject().put("_rykerLibraryBlob",it.getString("blob")).put("bytes",it.getLong("bytes"))}
    }
    internal fun unpack(value:Any?):Any? = if(value is JSONObject && value.has("_rykerLibraryBlob"))org.json.JSONTokener(SharedLibrary.blobFile(value.getString("_rykerLibraryBlob")).readText()).nextValue() else value
    fun snapshot(c:Context):Map<String,Any?> = linkedMapOf<String,Any?>().also { out ->
        TripStore.syncFiles().forEach{out["trip/${it.name}"]=fileValue(it,it.name.endsWith("meta.json"))}
        RidePhotos.syncFiles().forEach{out["photo/${it.name}"]=fileValue(it,false)}
        val data=SoftwareStore.snapshot()
        data.keys().forEach { key ->
            when {
                key in arrays -> data.getJSONArray(key).let { a -> for(i in 0 until a.length()){val record=a.getJSONObject(i);out["software/$key/${record.getString("id")}"]=pack(record)} }
                key=="favorites" -> data.getJSONObject(key).let{f->f.keys().forEach{out["software/favorites/$it"]=f.get(it)}}
                else -> out["software/$key"]=pack(data.get(key))
            }
        }
        val preferences=SoftwareStore.preferences();preferences.keys().forEach{out["ridePreference/$it"]=preferences.get(it)}
        c.getSharedPreferences("appearance",0).all.forEach{(k,v)->if(k in BackupPreferences.appearanceKeys)out["appearance/$k"]=v}
        out["preference/dynamicColor"]=runBlocking{de.chaostheorybot.rykerconnect.data.RykerConnectStore(c).getDynamicColorToken.first()}
    }
    fun softwareValue(data:JSONObject,path:List<String>):Any? {
        val key=path[0];if(path.size==1)return data.opt(key)
        if(key=="favorites")return data.optJSONObject(key)?.opt(path[1])
        return data.optJSONArray(key)?.let{a->(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.optString("id")==path[1]}}
    }
    fun setSoftwareValue(data:JSONObject,path:List<String>,value:Any?) {
        val key=path[0];val absent=value==null || value==JSONObject.NULL
        if(path.size==1){if(absent)data.remove(key) else data.put(key,SyncJson.copy(value));return}
        if(key=="favorites"){val f=data.optJSONObject(key)?:JSONObject();if(absent)f.remove(path[1]) else f.put(path[1],value);data.put(key,f);return}
        val a=data.optJSONArray(key)?:JSONArray();val list=(0 until a.length()).map{a.getJSONObject(it)}.filter{it.getString("id")!=path[1]}
        if(!absent)require(value is JSONObject && value.getString("id")==path[1])
        if(!absent && key=="siteEdits")RideEditValidation.record(value as JSONObject)
        if(!absent && key=="serviceBaselines")ServiceBaselines.validate(value as JSONObject)
        if(!absent && key=="modifications")ModificationRecords.validate(value as JSONObject)
        if(!absent && key=="ownershipSeasons")OwnershipSeasons.validate(value as JSONObject)
        data.put(key,JSONArray(if(absent)list else list+value))
    }
    fun current(c:Context,key:String):Any? {
        val p=key.split('/');validateKey(key)
        return when(p[0]) {
            "software" -> pack(softwareValue(SoftwareStore.snapshot(),p.drop(1)))
            "trip" -> fileValue(File(c.noBackupFilesDir,"rides/${p[1]}"),p[1].endsWith("meta.json"))
            "photo" -> fileValue(RidePhotos.file(p[1]),false)
            "ridePreference" -> SoftwareStore.preferences().opt(p[1])
            "appearance" -> c.getSharedPreferences("appearance",0).all[p[1]]
            "preference" -> runBlocking{de.chaostheorybot.rykerconnect.data.RykerConnectStore(c).getDynamicColorToken.first()}
            else -> null
        }
    }
    fun apply(c:Context,key:String,expected:String,value:Any?):Boolean {
        val p=key.split('/');validateKey(key);val absent=value==null || value==JSONObject.NULL
        return when(p[0]) {
            "software" -> SoftwareStore.syncSet(p.drop(1),expected,unpack(value),packed=true)
            "trip","photo" -> {
                val json=p[0]=="trip" && p[1].endsWith("meta.json")
                val source=if(absent)null else if(json)SharedLibrary.temporary(SyncJson.canonical(value).toByteArray()) else SharedLibrary.blobFile((value as JSONObject).getString("blob"))
                try { if(p[0]=="trip")TripStore.syncReplace(p[1],expected,source) else RidePhotos.syncReplace(p[1],expected,source) }
                finally { if(json)source?.delete() }
            }
            "ridePreference" -> synchronized(RideState){
                if(SyncJson.fingerprint(current(c,key))!=expected)false else { require(!absent);SoftwareStore.applyPreferences(JSONObject().put(p[1],value));true }
            }
            "appearance" -> {
                if(SyncJson.fingerprint(current(c,key))!=expected)false else {
                    val prefs=c.getSharedPreferences("appearance",0);val next=JSONObject(prefs.all.filterKeys{it in BackupPreferences.appearanceKeys})
                    if(absent)next.remove(p[1]) else next.put(p[1],value)
                    BackupPreferences.validate(JSONObject().put("appearance",next))
                    prefs.edit().also{if(absent)it.remove(p[1]) else it.putString(p[1],value.toString())}.commit();true
                }
            }
            "preference" -> {
                if(SyncJson.fingerprint(current(c,key))!=expected)false else { require(value is Boolean);runBlocking{de.chaostheorybot.rykerconnect.data.RykerConnectStore(c).saveDynamicColor(value)};true }
            }
            else -> false
        }
    }
    fun blobReferences(wire:JSONObject):Map<String,Long> = mutableMapOf<String,Long>().also { refs ->
        val records=wire.getJSONObject("records");records.keys().forEach { key ->
            validateKey(key)
            val branches=records.getJSONArray(key);for(i in 0 until branches.length()) {
                    val v=branches.getJSONObject(i).optJSONObject("value")?:continue
                    val blobKey=if(v.has("_rykerLibraryBlob"))"_rykerLibraryBlob" else if(key.startsWith("photo/") || key.startsWith("trip/") && key.endsWith(".jsonl"))"blob" else continue
                    val hash=v.getString(blobKey);val bytes=v.getLong("bytes");require(hash.matches(Regex("[a-f0-9]{64}")) && bytes in 1..512_000_000)
                    require(refs[hash]==null || refs[hash]==bytes);refs[hash]=bytes
            }
        }
    }
}
