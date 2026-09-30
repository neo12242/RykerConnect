package de.chaostheorybot.rykerconnect.ride

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One durable authority while both editions are installed. A stale lease is checked before reuse. */
internal object RecordingCoordinator {
    val status=kotlinx.coroutines.flow.MutableStateFlow("")
    private val serviceSession = Mutex()
    internal suspend fun withSession(c:Context, block:suspend ()->Unit) = serviceSession.withLock {
        if(acquire(c)) try { block() } finally { withContext(NonCancellable) { release(c) } }
    }
    private var session:String?=null
    private var authority:String?=null
    private fun authority(c:Context):String {
        val full=SharedLibrary.FULL
        return if(c.packageName==full || c.packageManager.checkSignatures(c.packageName,full)==PackageManager.SIGNATURE_MATCH && c.packageManager.resolveContentProvider("$full.sharedlibrary",0)!=null)full else c.packageName
    }
    private fun call(c:Context,method:String,pkg:String,session:String,recover:Boolean=false):Boolean {
        val extras=Bundle().apply{putString("session",session);putBoolean("recover",recover)}
        return if(pkg==c.packageName)handle(c,method,c.applicationInfo.uid,extras).getBoolean("ok")
        else c.contentResolver.call(SharedLibrary.uri(pkg),method,null,extras)?.getBoolean("ok")==true
    }
    fun acquire(c:Context):Boolean {
        if(session!=null)return false
        val id=UUID.randomUUID().toString();val pkg=authority(c)
        val acquired=runCatching{call(c,"claim",pkg,id,true)}.getOrDefault(false)
        if(acquired){session=id;authority=pkg;status.value=""}else {status.value="Another edition is recording or its recording status is unavailable. Sync to check it.";AutoRide.failed(status.value);SharedLibrary.changed()}
        return acquired
    }
    fun renew(c:Context):Boolean {
        val id=session?:return false;val pkg=authority?:return false
        return runCatching{call(c,"renew",pkg,id)}.getOrDefault(false)
    }
    fun release(c:Context){val id=session;val pkg=authority;session=null;authority=null;if(id!=null&&pkg!=null)runCatching{call(c,"release",pkg,id)}}
    /** A separate lease never replaces the service's live session. */
    internal fun <T> withoutRecording(c:Context, block:()->T):T {
        check(!TripStore.summary.value.recording){"Stop recording in both apps before deleting a trip."}
        val id=UUID.randomUUID().toString();val pkg=authority(c)
        check(call(c,"claim",pkg,id)){"Stop recording in both apps before deleting a trip."}
        try { return block() } finally { call(c,"release",pkg,id) }
    }
    @Synchronized fun handle(c:Context,method:String,uid:Int,extras:Bundle):Bundle {
        val owner=if(uid==c.applicationInfo.uid)c.packageName else c.packageManager.getPackagesForUid(uid)?.firstOrNull{it==SharedLibrary.FULL||it==SharedLibrary.PHONE}?:error("Unknown recording caller")
        val id=extras.getString("session")?:error("Missing recording session");UUID.fromString(id)
        val file=File(c.noBackupFilesDir,"recording-lease.json")
        val boot=Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,0)
        var lease=if(file.exists())JSONObject(file.readText()) else JSONObject()
        if(lease.optInt("boot",-1)!=boot)lease=JSONObject()
        val now=SystemClock.elapsedRealtime();var ok=false
        when(method) {
            "claim" -> {
                // Each edition serializes its service sessions; reclaim its own lease after process recovery.
                var available=!lease.has("owner") || (lease.optString("owner")==owner && extras.getBoolean("recover"))
                if(!available && lease.optLong("until")<now) {
                    val previous=lease.getString("owner")
                    val recording=if(previous==c.packageName)TripStore.summary.value.recording else runCatching{
                        c.contentResolver.call(SharedLibrary.uri(previous),"recording",null,null)?.getBoolean("active")?:true
                    }.getOrDefault(true)
                    available=!recording
                }
                if(available){lease=JSONObject().put("owner",owner).put("session",id).put("boot",boot).put("until",now+60_000);ok=true}
            }
            "renew" -> if(lease.optString("owner")==owner && lease.optString("session")==id){lease.put("until",now+60_000);ok=true}
            "release" -> if(lease.optString("owner")==owner && lease.optString("session")==id){lease=JSONObject();ok=true}
        }
        if(ok){val temp=File(file.path+".tmp");temp.writeText(lease.toString());java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
        return Bundle().apply{putBoolean("ok",ok)}
    }
}
