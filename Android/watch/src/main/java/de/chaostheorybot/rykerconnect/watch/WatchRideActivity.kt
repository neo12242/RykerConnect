package de.chaostheorybot.rykerconnect.watch

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Small round-screen remote. The phone owns all ride data and acknowledges every change. */
class WatchRideActivity:Activity(),MessageClient.OnMessageReceivedListener {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var title:TextView;private lateinit var detail:TextView;private lateinit var message:TextView
    private lateinit var primary:Button;private lateinit var end:Button
    private var state:JSONObject?=null
    private var node:String?=null
    private var requestId:String?=null
    private var requestAction=""
    private var confirmed=0L
    private var visible=false
    private var pending=false
    private val tick=object:Runnable { override fun run(){if(!visible)return;if(!pending)refresh();render();handler.postDelayed(this,5_000)} }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(28),dp(20),dp(28),dp(28));setBackgroundColor(Color.rgb(8,19,24))}
        fun label(size:Float)=TextView(this).apply{setTextColor(Color.WHITE);textSize=size;gravity=Gravity.CENTER;setPadding(0,dp(2),0,dp(2));content.addView(this)}
        label(11f).text=if(packageName.endsWith(".phone"))"RYKER · PHONE" else "RYKER · ESP"
        title=label(18f).apply{setTypeface(typeface,Typeface.BOLD)}
        detail=label(12f)
        fun button(text:String,color:Int,action:()->Unit)=Button(this).apply{
            this.text=text;setAllCaps(false);textSize=15f;setTextColor(Color.BLACK);minHeight=dp(48)
            background=GradientDrawable().apply{setColor(color);cornerRadius=dp(28).toFloat()}
            content.addView(this,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(6)})
            setOnClickListener{action()}
        }
        primary=button("Connecting…",Color.rgb(114,221,204)){
            val mode=state?.optString("state")
            command(when(mode){"idle"->"START";"recording"->"PAUSE";"paused","interrupted"->"RESUME";else->return@button})
        }
        end=button("End Ride",Color.rgb(242,172,158)){
            val captured=state
            AlertDialog.Builder(this).setTitle("End ride?").setMessage("Save on phone. Upload separately.")
                .setPositiveButton("End Ride"){_,_->command("END",captured)}.setNegativeButton("Keep ride",null).show()
        }
        message=label(12f)
        button("Refresh",Color.rgb(158,181,190)){refresh()}
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(content);isFocusableInTouchMode=true;requestFocus()
            setOnGenericMotionListener{_,event->if(event.action==MotionEvent.ACTION_SCROLL){scrollBy(0,(-event.getAxisValue(MotionEvent.AXIS_SCROLL)*dp(42)).toInt());true}else false}}
        setContentView(scroll);render()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onResume(){super.onResume();visible=true;Wearable.getMessageClient(this).addListener(this);handler.post(tick)}
    override fun onPause(){visible=false;handler.removeCallbacksAndMessages(null);Wearable.getMessageClient(this).removeListener(this);pending=false;requestId=null;requestAction="";state=null;node=null;confirmed=0;super.onPause()}
    private fun isCurrentRequest(id:String)=visible&&pending&&requestId==id
    private fun beginRequest(action:String):String {
        val id=UUID.randomUUID().toString()
        requestId=id;requestAction=action;pending=true;render()
        // Include node discovery in the deadline. Superseded requests cannot fail a later command.
        handler.postDelayed({if(isCurrentRequest(id))fail("No confirmation. Refresh to check the ride before retrying.")},14_000)
        return id
    }
    private fun refresh(){
        if(pending||!visible)return
        val id=beginRequest("STATUS")
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if(!isCurrentRequest(id))return@addOnSuccessListener
            val selected=nodes.singleOrNull()?:nodes.filter{it.isNearby}.singleOrNull()
            if(selected==null){fail("Phone disconnected. Pair through Galaxy Wearable, then retry.");return@addOnSuccessListener}
            if(node!=selected.id){state=null;confirmed=0;render()}
            node=selected.id;send(JSONObject().put("action","STATUS"),id)
        }.addOnFailureListener{if(isCurrentRequest(id))fail("Phone connection unavailable. Open the matching phone app.")}
    }
    private fun command(action:String,expected:JSONObject?=state){
        if(!visible||(pending&&requestAction!="STATUS"))return
        if(expected==null||SystemClock.elapsedRealtime()-confirmed>15_000){message.text="Refresh phone status first.";refresh();return}
        val request=JSONObject().put("action",action).put("owner",expected.getString("owner")).put("token",expected.getString("token")).put("observedAt",expected.getLong("observedAt"))
        // A tap may replace a background STATUS request, but never another ride command.
        message.text="Waiting for phone…";send(request,beginRequest(action))
    }
    private fun send(request:JSONObject,id:String){
        if(!isCurrentRequest(id))return
        val target=node?:run{fail("Phone disconnected");return}
        request.put("id",id)
        Wearable.getMessageClient(this).sendMessage(target,"/ryker/ride/request",request.toString().toByteArray(Charsets.UTF_8))
            .addOnFailureListener{if(isCurrentRequest(id))fail("Not delivered. Open the matching phone app and refresh.")}
    }
    override fun onMessageReceived(event:MessageEvent){
        if(event.path!="/ryker/ride/reply"||event.data.size>8192)return
        val reply=runCatching{JSONObject(event.data.toString(Charsets.UTF_8))}.getOrNull()?:return
        runOnUiThread {
            if(event.sourceNodeId!=node||!isCurrentRequest(reply.optString("id")))return@runOnUiThread
            pending=false;requestId=null
            val wasUnknown=state==null
            state=reply.optJSONObject("status");confirmed=SystemClock.elapsedRealtime()
            if(reply.optBoolean("ok")) {
                if(requestAction!="STATUS"){
                    message.text=reply.optString("message")
                    getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(45,VibrationEffect.DEFAULT_AMPLITUDE))
                }else if(message.text.isBlank()||wasUnknown)message.text="Connected to phone"
            } else {message.text=reply.optString("message","Action was not confirmed");if(state==null)confirmed=0}
            render()
        }
    }
    private fun fail(reason:String){pending=false;requestId=null;state=null;node=null;confirmed=0;message.text=reason;render()}
    private fun time(ms:Long):String{val minutes=ms.coerceAtLeast(0)/60_000;return String.format(Locale.US,"%d:%02d",minutes/60,minutes%60)}
    private fun render(){
        val s=state;val mode=s?.optString("state")
        title.text=when(mode){"idle"->"Ready to ride";"recording"->"Recording";"paused"->"Paused";"interrupted"->"Interrupted";else->"Phone status"}
        primary.text=if(pending&&requestAction!="STATUS")"Confirming…" else when(mode){"idle"->"Start Ride";"recording"->"Pause Ride";"paused","interrupted"->"Resume Ride";else->"Connect phone"}
        val fresh=s!=null&&SystemClock.elapsedRealtime()-confirmed<=15_000
        val commandPending=pending&&requestAction!="STATUS"
        primary.isEnabled=fresh&&!commandPending;end.isEnabled=fresh&&!commandPending;end.visibility=if(mode!=null&&mode!="idle")View.VISIBLE else View.GONE
        primary.alpha=if(primary.isEnabled)1f else .45f;end.alpha=if(end.isEnabled)1f else .45f
        detail.text=if(s!=null&&mode!="idle"){
            val miles=s.optBoolean("miles");val distance=s.optDouble("meters")/if(miles)1609.344 else 1000.0
            String.format(Locale.US,"%.1f %s\nRide %s · Pause %s",distance,if(miles)"mi" else "km",time(s.optLong("elapsedMs")-s.optLong("pausedMs")),time(s.optLong("pausedMs")))
        }else "Your phone records GPS"
    }
}
