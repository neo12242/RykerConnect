package de.chaostheorybot.rykerconnect.ride

import android.app.job.*
import android.content.*
import android.net.*
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

object PublicRide {
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun route(points:List<TrackPoint>,trim:Double):List<List<TrackPoint>>{
        require(trim in 0.0..10000.0)
        if(points.isEmpty())return emptyList()
        val lines=mutableListOf<MutableList<TrackPoint>>();var start=true
        for(segment in TripAnalysis.segments(points)){
            start=true
            for(p in segment){val visible=trim==0.0 || TrackMath.distance(points.first(),p)>trim && TrackMath.distance(points.last(),p)>trim
                if(!visible){start=true;continue};if(start){lines.add(mutableListOf());start=false};lines.last().add(p)
            }
        };return lines
    }
    fun manifest(detail:TripDetail,j:JSONObject,trim:Double,stats:Boolean,includeRoute:Boolean):JSONObject{
        val d=detail;val lines=if(includeRoute)route(d.track,trim) else emptyList();require(lines.sumOf{it.size}<=20000){"Route exceeds 20,000 points. Prepare a trimmed copy first."}
        val photos=JSONArray();for(p in RidePhotos.photos(j).filter{it.optBoolean("publish",true)}){
            val id=p.getString("id");val bytes=RidePhotos.file("$id.jpg").readBytes();val thumb=RidePhotos.file("$id.thumb.jpg").readBytes()
            require(bytes.size<=3000000 && thumb.size<=500000){"Photo derivative is too large"}
            photos.put(JSONObject().put("id",id).put("caption",p.optString("caption")).put("sha",hash(bytes)).put("size",bytes.size).put("thumbSha",hash(thumb)).put("thumbSize",thumb.size))
        }
        val selected=(0 until photos.length()).map{photos.getJSONObject(it).getString("id")}
        return JSONObject().put("version",1).put("id",d.summary.id).put("title",d.summary.title.ifBlank{"My ride"}).put("story",d.summary.notes)
            .put("date",java.time.Instant.ofEpochMilli(d.summary.started).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
            .put("tags",JSONArray(j.optString("tags").split(',').map{it.trim().take(30)}.filter{it.isNotBlank()}.distinct().take(15)))
            .put("cover",j.optString("cover").takeIf{it in selected}?:selected.firstOrNull()?:"").put("photos",photos)
            .put("route",JSONArray(lines.map{line->JSONArray(line.map{JSONArray().put(it.lon).put(it.lat)})}))
            .put("stats",if(stats)JSONObject().put("meters",d.stats.meters).put("elapsedMs",(d.summary.ended-d.summary.started).coerceAtLeast(0))
                .put("movingMs",d.stats.movingMs).put("stoppedMs",d.stats.stoppedMs).put("unknownMs",d.stats.unknownMs).put("averageMps",RideCompletion.movingAverage(d.track)?:JSONObject.NULL) else JSONObject())
            .put("privacy",JSONObject().put("trimMeters",trim).put("statsIncluded",stats))
    }
}

object DadRides {
    const val JOB=8203
    val revision=MutableStateFlow(0)
    private lateinit var c:Context
    private lateinit var root:File
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build()
    fun init(context:Context){c=context.applicationContext;root=File(c.noBackupFilesDir,"dadrides").apply{mkdirs()};schedule()}
    private fun prefs()=c.getSharedPreferences("dadrides_options",0)
    fun enabled()=prefs().getBoolean("enabled",false)
    fun enable(value:Boolean){prefs().edit().putBoolean("enabled",value).commit();if(!value){client.dispatcher.cancelAll();c.getSystemService(JobScheduler::class.java).cancel(JOB)}else schedule();revision.value++}
    private fun key():javax.crypto.SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        return store.getKey("dadrides-owner",null) as? javax.crypto.SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("dadrides-owner",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    fun configure(url:String,token:String){
        val u=java.net.URI(url.trim());val debug=c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0
        require(u.scheme=="https" || debug && u.scheme=="http" && u.host in listOf("127.0.0.1","10.0.2.2","localhost")){"Use an HTTPS site address"}
        require(!u.host.isNullOrBlank()&&u.userInfo==null&&u.query==null&&u.fragment==null&&(u.path.isNullOrEmpty()||u.path=="/")){"Enter only the site origin"}
        require(token.matches(Regex("[A-Za-z0-9_-]{32,256}"))){"Enter the DadRides publishing key, not a Cloudflare account key"}
        val value=JSONObject().put("url",url.trim().trimEnd('/')).put("token",token)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val output=cipher.iv+cipher.doFinal(value.toString().toByteArray());atomic(File(root,"owner.bin"),output);revision.value++
    }
    private fun credentials():JSONObject{
        val bytes=File(root,"owner.bin").readBytes();val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size))))
    }
    fun origin()=runCatching{credentials().getString("url")}.getOrDefault("")
    fun disconnect(){enable(false);File(root,"owner.bin").delete();revision.value++}
    private fun atomic(f:File,bytes:ByteArray){val temp=File(f.path+".tmp");temp.writeBytes(bytes);java.nio.file.Files.move(temp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
    private fun folder(id:String):File{require(id.matches(Regex("[a-f0-9-]{36}")));return File(root,id)}
    @Synchronized fun items():List<JSONObject> = if(!::root.isInitialized)emptyList() else root.listFiles().orEmpty().filter{it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}"))}.mapNotNull{runCatching{JSONObject(File(it,"state.json").readText())}.getOrNull()}.sortedByDescending{it.optLong("created")}
    @Synchronized private fun save(item:JSONObject){
        val file=File(folder(item.getString("id")),"state.json")
        val old=if(file.exists())JSONObject(file.readText()) else null
        if(item.optString("state")!="Queued" && old==null)return
        if(old?.optString("state")=="Canceled" && item.optString("state") !in listOf("Queued","Canceled"))return
        if(!enabled() && item.optString("state") in listOf("Uploading","Uploaded draft","Failed"))return
        atomic(file,item.toString().toByteArray());revision.value++
    }
    fun queue(m:JSONObject,allowMetered:Boolean){
        check(enabled()){ "Enable DadRides first" };val origin=origin();require(origin.isNotBlank()){"Connect your site first"}
        val bytes=m.toString().toByteArray();val hash=PublicRide.hash(bytes)
        if(items().any{it.optString("revision")==hash && it.optString("origin")==origin && it.optString("state")!="Canceled"}){schedule();return}
        require(items().count{it.optString("state") in listOf("Queued","Uploading","Failed")}<20){"Finish or cancel pending uploads first"}
        require(root.walkTopDown().filter{it.isFile}.sumOf{it.length()}<250_000_000){"Prepared upload storage is full. Remove old local upload copies in Add-ons."}
        val id=java.util.UUID.randomUUID().toString();val f=folder(id).apply{mkdirs()}
        try{
            atomic(File(f,"manifest.json"),bytes)
            val photos=m.getJSONArray("photos");for(i in 0 until photos.length()){val p=photos.getJSONObject(i);for(ext in listOf("jpg","thumb.jpg"))RidePhotos.file(p.getString("id")+"."+ext).copyTo(File(f,p.getString("id")+"."+ext))}
            save(JSONObject().put("id",id).put("ride",m.getString("id")).put("revision",PublicRide.hash(bytes)).put("origin",origin).put("title",m.getString("title")).put("created",System.currentTimeMillis()).put("state","Queued").put("allowMetered",allowMetered).put("progress",0).put("message","Waiting for network"));schedule()
        }catch(e:Exception){f.listFiles()?.forEach{it.delete()};f.delete();throw e}
    }
    @Synchronized fun removeLocal(id:String){cancel(id);val f=folder(id);f.listFiles()?.forEach{it.delete()};f.delete();revision.value++}
    fun retry(id:String){val item=items().first{it.getString("id")==id};item.put("state","Queued");save(item);schedule()}
    fun cancel(id:String){val item=items().first{it.getString("id")==id};item.put("state","Canceled").put("message","Local upload canceled. Any remote draft remains private.");save(item);client.dispatcher.cancelAll();revision.value++}
    private fun ensureItem(id:String){check(enabled()&&items().firstOrNull{it.getString("id")==id}?.optString("state") in listOf("Queued","Uploading")){"Upload paused or canceled"}}
    fun request(path:String,method:String="GET",bytes:ByteArray?=null,mime:String="application/json"):JSONObject{
        check(enabled()){ "DadRides is disabled" };val config=credentials();val request=Request.Builder().url(config.getString("url")+"/api/owner/"+path).header("Authorization","Bearer "+config.getString("token"))
            .method(method,bytes?.toRequestBody(mime.toMediaType())?:if(method in listOf("POST","PUT"))ByteArray(0).toRequestBody() else null).build()
        client.newCall(request).execute().use{r->val text=r.body?.string().orEmpty();val j=runCatching{JSONObject(text)}.getOrNull();check(r.isSuccessful){j?.optString("error")?.take(160)?:"Site returned HTTP ${r.code}"};return j?:JSONObject()}
    }
    @Synchronized fun schedule(){if(!::c.isInitialized || !enabled() || origin().isBlank())return
        val pending=items().filter{it.optString("state") in listOf("Queued","Uploading")};if(pending.isEmpty())return
        val manager=c.getSystemService(JobScheduler::class.java);if(manager.getPendingJob(JOB)!=null)return
        val builder=JobInfo.Builder(JOB,ComponentName(c,DadRidesJob::class.java)).setPersisted(true)
        if(pending.any{it.optBoolean("allowMetered")})builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) else builder.setRequiredNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build())
        manager.schedule(builder.build())
    }
    suspend fun upload(){for(item in items().filter{it.optString("state") in listOf("Queued","Uploading")}){
        if(!enabled())return
        val cm=c.getSystemService(ConnectivityManager::class.java)
        if(!item.optBoolean("allowMetered") && (cm.isActiveNetworkMetered || cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true))continue
        val id=item.getString("id");val ride=item.getString("ride");val rev=item.getString("revision");val f=folder(id)
        try{
            ensureItem(id);require(item.getString("origin")==origin()){"This upload belongs to a different site; reconnect it or prepare again"}
            item.put("state","Uploading").put("message","Uploading private draft");save(item)
            val bytes=File(f,"manifest.json").readBytes();val m=JSONObject(String(bytes));request("rides/$ride/revisions/$rev","PUT",bytes)
            val photos=m.getJSONArray("photos");for(i in 0 until photos.length()){
                ensureItem(id);val p=photos.getJSONObject(i)
                for(ext in listOf("jpg","thumb.jpg")){ensureItem(id);request("assets/$rev/"+p.getString("id")+"."+ext,"PUT",File(f,p.getString("id")+"."+ext).readBytes(),"image/jpeg")}
                item.put("progress",((i+1)*100)/(photos.length().coerceAtLeast(1)));save(item)
            }
            ensureItem(id);request("rides/$ride/finish/$rev","POST");item.put("state","Uploaded draft").put("progress",100).put("message","Private draft ready. Preview before publishing.");save(item)
        }catch(e:CancellationException){throw e}catch(e:Exception){if(enabled()&&items().firstOrNull{it.getString("id")==id}?.optString("state")!="Canceled"){item.put("state","Failed").put("message",e.message?.take(160)?:"Upload failed; retry when connected");save(item)}}
    }}
    fun checkStatus(id:String):String {
        val item=items().first{it.getString("id")==id}
        require(item.getString("origin")==origin()){ "Reconnect the site used for this upload" }
        val remote=request("rides/${item.getString("ride")}/revisions/${item.getString("revision")}")
        val published=remote.optString("published")==item.getString("revision")
        val old=JSONObject(File(folder(id),"manifest.json").readText());val privacy=old.getJSONObject("privacy")
        val changed=runCatching { PublicRide.hash(PublicRide.manifest(TripStore.detail(item.getString("ride")),RidePhotos.journal(item.getString("ride")),privacy.getDouble("trimMeters"),privacy.getBoolean("statsIncluded"),old.getJSONArray("route").length()>0).toString().toByteArray())!=item.getString("revision") }.getOrDefault(true)
        val message=(if(published)"Published on your site" else "Private uploaded version")+(if(changed)" · Local changes need a new prepared version" else " · Local content matches this version")
        item.put("state",if(published)"Published" else "Uploaded draft").put("changed",changed).put("message",message);save(item);return message
    }
    fun previewUrl(item:JSONObject)=item.getString("origin")+"/#draft/"+item.getString("ride")+"/"+item.getString("revision")
}
class DadRidesJob:JobService(){
    private var work:Job?=null
    override fun onStartJob(params:JobParameters):Boolean{work=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{try{DadRides.upload();jobFinished(params,false);android.os.Handler(mainLooper).postDelayed({DadRides.schedule()},1000)}catch(e:CancellationException){throw e}};return true}
    override fun onStopJob(params:JobParameters):Boolean{work?.cancel();return true}
}
