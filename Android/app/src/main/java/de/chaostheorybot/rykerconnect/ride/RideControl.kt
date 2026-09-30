package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/** Shared command path for UI, signed edition IPC and the Wear Data Layer. */
object RideControl {
    private val epoch = UUID.randomUUID().toString()
    private val mutex = Mutex()
    private val completed = LinkedHashMap<String, Pair<String, String>>()
    fun localState(c: Context): JSONObject {
        val s = TripStore.summary.value; val now = System.currentTimeMillis()
        val state = if (!s.recording) "idle" else if (s.paused) "paused" else if (s.needsRecovery) "interrupted" else "recording"
        return JSONObject().put("owner", c.packageName).put("rideId", s.id).put("state", state)
            .put("token", "$epoch:${s.id}:${s.controlToken}:$state").put("meters", s.meters)
            .put("elapsedMs", if (s.recording) (now-s.started).coerceAtLeast(0) else 0)
            .put("pausedMs", s.pausedDuration(now)).put("gps", s.gps).put("needsRecovery", s.needsRecovery)
            .put("observedAt",SystemClock.elapsedRealtime()).put("miles", RideState.preferences.value.imperial)
    }
    private fun peer(c:Context):String? {
        val p=if(c.packageName==SharedLibrary.PHONE)SharedLibrary.FULL else SharedLibrary.PHONE
        return p.takeIf { c.packageManager.checkSignatures(c.packageName,p)==PackageManager.SIGNATURE_MATCH && c.packageManager.resolveContentProvider("$p.sharedlibrary",0)!=null }
    }
    suspend fun state(c:Context):JSONObject = withContext(Dispatchers.IO) {
        val local=localState(c)
        if(local.getString("state")!="idle")return@withContext local
        peer(c)?.let { p ->
            val remote = c.contentResolver.call(SharedLibrary.uri(p),"ride-state",null,null)?.getString("json")?.let(::JSONObject)
                ?: error("Other edition is unavailable. Open it on your phone.")
            if(remote.getString("state")!="idle")return@withContext remote
        }
        local
    }
    suspend fun request(c:Context, action:String):JSONObject = try {
        val current=state(c)
        dispatch(c,JSONObject().put("id",UUID.randomUUID().toString()).put("action",action).put("token",current.getString("token"))
            .put("owner",current.getString("owner")).put("observedAt",current.getLong("observedAt")),false)
    } catch(e:Exception){failure(c,e.message?:"Ride control unavailable")}
    suspend fun dispatch(c:Context, command:JSONObject, fromWatch:Boolean):JSONObject = withContext(Dispatchers.IO) {
        val owner=command.getString("owner")
        if(owner==c.packageName) executeLocal(c,command,fromWatch) else {
            require(owner==peer(c)){"Matching phone edition is unavailable"}
            val extras=Bundle().apply{putString("json",command.toString());putBoolean("watch",fromWatch)}
            c.contentResolver.call(SharedLibrary.uri(owner),"ride-control",null,extras)?.getString("json")?.let(::JSONObject)
                ?: failure(c,"Other edition did not confirm the action. Refresh status.")
        }
    }
    suspend fun executeLocal(c:Context, command:JSONObject, fromWatch:Boolean):JSONObject = mutex.withLock {
        val id=command.getString("id");UUID.fromString(id)
        val signature=command.toString()
        completed[id]?.let { require(it.first==signature){"Command identifier was reused"};return@withLock JSONObject(it.second).put("status",localState(c)).put("alreadyHandled",true).put("message","Request already handled; current status refreshed") }
        val result=try {
            validate(c,command)
            val action=command.getString("action")
            if(fromWatch && action in listOf("START","RESUME") && !TripRecordingService.running)
                check(WatchSetup.ready(c)){"On phone: Settings → Watch controls. Allow location all the time and unrestricted battery, then retry."}
            withTimeout(12_000) {
                suspendCancellableCoroutine<JSONObject> { continuation ->
                    val receiver=object:ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(code:Int,data:Bundle?) {
                            if(continuation.isActive)continuation.resume(data?.getString("json")?.let(::JSONObject)?:failure(c,"No confirmation received"))
                        }
                    }
                    val intent=Intent(c,TripRecordingService::class.java).setAction(action)
                        .putExtra("command",command.toString()).putExtra("receiver",receiver)
                    try {
                        if(action in listOf("START","RESUME") && !TripRecordingService.running)ContextCompat.startForegroundService(c,intent)
                        else if(!TripRecordingService.running && action=="END") {
                            // Ending a recovered session needs no GPS or foreground-service privilege.
                            check(TripStore.stop()){TripStore.storageError.value};AutoRide.manualStop()
                            continuation.resume(success(c,"Ride saved"))
                        } else if(!TripRecordingService.running && action=="PAUSE") {
                            TripStore.pause();continuation.resume(success(c,"Ride paused"))
                        } else c.startService(intent)
                    } catch(e:Exception){if(continuation.isActive)continuation.resume(failure(c,e.message?:"Open the phone app and check location access"))}
                }
            }
        }catch(e:Exception){failure(c,if(e is TimeoutCancellationException)"Phone did not confirm in time. Refresh before trying again." else e.message?:"Action failed")}
        completed[id]=signature to result.toString();while(completed.size>64)completed.remove(completed.keys.first())
        result
    }
    fun validate(c:Context,command:JSONObject) {
        val current=localState(c)
        require(SystemClock.elapsedRealtime()-command.getLong("observedAt") in 0..15_000){"Command expired. Refresh and try again."}
        require(command.getString("token")==current.getString("token")){"Ride state changed. Refresh and try again."}
        val state=current.getString("state")
        require(when(command.getString("action")){
            "START"->state=="idle";"PAUSE"->state=="recording"||state=="interrupted"
            "RESUME"->state=="paused"||state=="interrupted";"END"->state!="idle";else->false
        }){"This action does not match the current ride"}
    }
    fun success(c:Context,message:String)=JSONObject().put("ok",true).put("message",message).put("status",localState(c))
    fun failure(c:Context,message:String)=JSONObject().put("ok",false).put("message",message.take(240)).put("status",localState(c))
}
