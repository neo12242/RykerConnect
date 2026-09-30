package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

object ModificationSync {
    val status=MutableStateFlow("Local modifications · Sync when DadRides is connected")
    val conflicts=MutableStateFlow<Map<String,JSONObject>>(emptyMap())
    private fun preferences(c:Context)=c.getSharedPreferences("modification_sync",0)
    private fun key(origin:String,id:String)=SyncJson.fingerprint(origin)+"."+id
    fun last(c:Context)=preferences(c).getLong(key(DadRides.origin(),"last"),0)
    fun action(local:JSONObject?,remote:JSONObject?,baseHash:String?):String {
        if(local==null)return "download"
        if(remote!=null && SyncJson.fingerprint(local)==SyncJson.fingerprint(remote))return "equal"
        return if(remote!=null && baseHash==SyncJson.fingerprint(local))"download" else "upload"
    }
    @Synchronized fun sync(c:Context):String {
        if(!DadRides.enabled() || DadRides.origin().isBlank()){status.value="Local modifications · Connect DadRides in Add-ons to sync";return status.value}
        val origin=DadRides.origin();val prefs=preferences(c)
        fun checkSite(){check(DadRides.enabled() && DadRides.origin()==origin){"Website connection changed. Sync paused."}}
        try {
            check(SharedLibrary.conflictChoices().keys.none{it.startsWith("software/modifications/")}){"Resolve shared-library modification conflicts first"}
            status.value="Synchronizing modifications"
            val remote=DadRides.request("ownership").getJSONArray("items")
            val rows=(0 until remote.length()).map{remote.getJSONObject(it)}.associateBy{it.getString("id")}
            val local=SoftwareStore.records("modifications").associateBy{it.getString("id")}
            val pending=mutableMapOf<String,JSONObject>()
            for(id in (rows.keys+local.keys)) {
                checkSite()
                val l=local[id];val r=rows[id];val document=r?.getJSONObject("document")
                if(document!=null)ModificationRecords.validate(document)
                val state=prefs.getString(key(origin,id),null)?.let(::JSONObject)
                fun remember(row:JSONObject){checkSite();prefs.edit().putString(key(origin,id),JSONObject().put("revision",row.opt("revision")?:JSONObject.NULL).put("hash",SyncJson.fingerprint(row.getJSONObject("document"))).toString()).commit()}
                when(action(l,document,state?.optString("hash"))) {
                    "equal" -> remember(r!!)
                    "download" -> {checkSite();ModificationRecords.save(document!!,l?.let(SyncJson::fingerprint));remember(r)}
                    else -> {
                        try {
                            val saved=DadRides.request("ownership/$id","POST",JSONObject().put("base",state?.opt("revision")?:JSONObject.NULL).put("document",l).toString().toByteArray())
                            remember(saved)
                        } catch(e:DadRidesHttpException) {
                            if(e.status!=409)throw e
                            val current=e.payload.optJSONObject("current")?:throw e
                            ModificationRecords.validate(current.getJSONObject("document"))
                            pending[id]=JSONObject(current.toString()).put("localFingerprint",SyncJson.fingerprint(l))
                        }
                    }
                }
            }
            conflicts.value=pending
            // Persist conflicts for review after an app restart, without touching either record.
            prefs.edit().putString(key(origin,"conflicts"),JSONObject(pending as Map<*,*>).toString()).commit()
            if(pending.isEmpty())prefs.edit().putLong(key(origin,"last"),System.currentTimeMillis()).commit()
            status.value=if(pending.isEmpty())"Modifications synchronized" else "${pending.size} modification conflicts need review"
        }catch(e:Exception){status.value="Modification sync needs attention: ${e.message?.take(140)?:"Retry when online"}";throw e}
        return status.value
    }
    fun loadConflicts(c:Context){
        val obj=preferences(c).getString(key(DadRides.origin(),"conflicts"),null)?.let(::JSONObject)?:JSONObject()
        conflicts.value=obj.keys().asSequence().associateWith{obj.getJSONObject(it)}
    }
    @Synchronized fun resolve(c:Context,id:String,useWebsite:Boolean) {
        val origin=DadRides.origin();val conflict=conflicts.value[id]?:error("Sync again to refresh this conflict")
        val local=SoftwareStore.records("modifications").firstOrNull{it.getString("id")==id}
        require(SyncJson.fingerprint(local)==conflict.getString("localFingerprint")){"Local version changed. Sync and review again."}
        if(useWebsite)ModificationRecords.save(conflict.getJSONObject("document"),SyncJson.fingerprint(local))
        preferences(c).edit().putString(key(origin,id),JSONObject().put("revision",conflict.get("revision")).put("hash",SyncJson.fingerprint(conflict.getJSONObject("document"))).toString()).commit()
        conflicts.value=conflicts.value-id
        preferences(c).edit().putString(key(origin,"conflicts"),JSONObject(conflicts.value as Map<*,*>).toString()).commit()
    }
    fun editorLink(id:String):Uri {
        val origin=DadRides.origin();check(DadRides.enabled()&&origin.isNotBlank()){"Connect DadRides in Add-ons first"}
        val response=DadRides.request("browser-handoff","POST",JSONObject().put("modId",id).toString().toByteArray())
        check(DadRides.origin()==origin && DadRides.enabled()){"Website connection changed"}
        val code=response.getString("code");require(code.matches(Regex("[a-f0-9]{64}")))
        return Uri.parse("$origin/handoff.html#$code")
    }
    fun openEditor(c:Context,uri:Uri) {
        // The browser implements Custom Tabs; the session binder is deliberately null.
        val extras=Bundle().apply{putBinder("android.support.customtabs.extra.SESSION",null)}
        c.startActivity(Intent(Intent.ACTION_VIEW,uri).putExtras(extras).putExtra("android.support.customtabs.extra.TITLE_VISIBILITY",1))
    }
}
