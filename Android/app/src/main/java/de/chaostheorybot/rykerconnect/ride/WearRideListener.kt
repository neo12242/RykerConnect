package de.chaostheorybot.rykerconnect.ride

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Data Layer already requires matching application identity and signing certificate. */
class WearRideListener:WearableListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onMessageReceived(event:MessageEvent) {
        if(event.path!="/ryker/ride/request"||event.data.size>4096)return
        val data=event.data.copyOf();val node=event.sourceNodeId
        scope.launch {
            val request=runCatching{JSONObject(data.toString(Charsets.UTF_8))}.getOrNull()?:return@launch
            val id=request.optString("id");if(!runCatching{java.util.UUID.fromString(id)}.isSuccess)return@launch
            val reply=try {
                check(WatchSetup.enabled(this@WearRideListener)){"On phone: open Watch controls and enable your watch."}
                SharedLibrary.awaitReady()
                if(request.optString("action")=="STATUS")JSONObject().put("ok",true).put("message","").put("status",RideControl.state(this@WearRideListener))
                else RideControl.dispatch(this@WearRideListener,request,true)
            }catch(e:Exception){JSONObject().put("ok",false).put("message",e.message?.take(240)?:"Phone control unavailable")}
            reply.put("id",id)
            runCatching{Tasks.await(Wearable.getMessageClient(this@WearRideListener).sendMessage(node,"/ryker/ride/reply",reply.toString().toByteArray(Charsets.UTF_8)),5,TimeUnit.SECONDS)}
        }
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
