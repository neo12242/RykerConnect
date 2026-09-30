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
        require(!detail.summary.websiteCopy){"Edit the restored ride on DadRides. A new upload requires the original recording and photos."}
        val d=detail;val lines=if(includeRoute)route(d.track,trim) else emptyList();require(lines.sumOf{it.size}<=20000){"Route exceeds 20,000 points. Prepare a trimmed copy first."}
        val photos=JSONArray();for(p in RidePhotos.photos(j).filter{it.optBoolean("publish",true)}){
            val id=p.getString("id");val bytes=RidePhotos.uploadBytes("$id.jpg");val thumb=RidePhotos.uploadBytes("$id.thumb.jpg")
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
                .put("movingMs",d.stats.movingMs).put("stoppedMs",d.stats.stoppedMs).put("unknownMs",d.stats.unknownMs).put("pausedMs",d.stats.pausedMs).put("averageMps",RideCompletion.movingAverage(d.track)?:JSONObject.NULL) else JSONObject())
            .put("stops",JSONArray(if(stats && includeRoute)d.pauses.mapNotNull{pause->pause.point?.takeIf{p->lines.any{line->line.any{it.lat==p.lat && it.lon==p.lon}}}?.let{p->JSONObject().put("point",JSONArray().put(p.lon).put(p.lat)).put("durationMs",pause.duration(d.summary.ended))}}else emptyList<JSONObject>()))
            .put("privacy",JSONObject().put("trimMeters",trim).put("statsIncluded",stats)).let{RideEdits.forPublication(it,includeRoute)}.also{m->
                // Re-check markers against the final reviewed route, including website edits.
                val coordinates=RideEdits.route(m).map{it.lon to it.lat}.toSet()
                val stops=m.optJSONArray("stops")?:JSONArray()
                m.put("stops",JSONArray((0 until stops.length()).map{stops.getJSONObject(it)}.filter{stop->val p=stop.getJSONArray("point");stats && (p.getDouble(0) to p.getDouble(1)) in coordinates}))
            }
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
    internal fun connectionForPeer()=android.os.Bundle().apply{val value=credentials();putString("url",value.getString("url"));putString("key",value.getString("token"))}
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
        check(!TripStore.originalDetail(m.getString("id")).summary.websiteCopy){"This website copy is already on DadRides. Use Sync now for edits."}
        check(enabled()){ "Enable DadRides first" };check(TripStore.contains(m.getString("id")) && !SharedLibrary.isTripDeleted(m.getString("id"))){"This trip was deleted. Close this preview."};val origin=origin();require(origin.isNotBlank()){"Connect your site first"}
        val bytes=m.toString().toByteArray();val hash=PublicRide.hash(bytes)
        if(items().any{it.optString("revision")==hash && it.optString("origin")==origin && it.optString("state")!="Canceled"}){schedule();return}
        require(items().count{it.optString("state") in listOf("Queued","Uploading","Failed")}<20){"Finish or cancel pending uploads first"}
        require(root.walkTopDown().filter{it.isFile}.sumOf{it.length()}<250_000_000){"Prepared upload storage is full. Remove old local upload copies in Add-ons."}
        val id=java.util.UUID.randomUUID().toString();val f=folder(id).apply{mkdirs()}
        try{
            atomic(File(f,"manifest.json"),bytes)
            val photos=m.getJSONArray("photos");for(i in 0 until photos.length()){
                val p=photos.getJSONObject(i)
                for(ext in listOf("jpg","thumb.jpg")){
                    val name=p.getString("id")+"."+ext;val photo=RidePhotos.uploadBytes(name)
                    val thumb=ext=="thumb.jpg"
                    require(photo.size==p.getInt(if(thumb)"thumbSize" else "size") && PublicRide.hash(photo)==p.getString(if(thumb)"thumbSha" else "sha")){"Photos changed; preview the public copy again"}
                    atomic(File(f,name),photo)
                }
            }
            save(JSONObject().put("id",id).put("ride",m.getString("id")).put("revision",PublicRide.hash(bytes)).put("origin",origin).put("title",m.getString("title")).put("created",System.currentTimeMillis()).put("state","Queued").put("allowMetered",allowMetered).put("editBase",RideEdits.record(m.getString("id"))?.takeUnless{it.optBoolean("remoteDeleted")}?.optString("baseRevision")?:JSONObject.NULL).put("progress",0).put("message","Waiting for network"));schedule()
        }catch(e:Exception){f.listFiles()?.forEach{it.delete()};f.delete();throw e}
    }
    @Synchronized fun removeLocal(id:String){cancel(id);val f=folder(id);f.listFiles()?.forEach{it.delete()};f.delete();revision.value++}
    fun retry(id:String){val item=items().first{it.getString("id")==id};check(TripStore.contains(item.getString("ride"))){"This trip was deleted; its upload cannot be retried."};item.put("state","Queued");save(item);schedule()}
    fun cancel(id:String){val item=items().first{it.getString("id")==id};item.put("state","Canceled").put("message","Local upload canceled. Any remote draft remains private.");save(item);cancelUploadCalls(setOf(id));revision.value++}
    private fun cancelUploadCalls(ids:Set<String>){
        (client.dispatcher.queuedCalls()+client.dispatcher.runningCalls()).filter{it.request().tag(String::class.java) in ids}.forEach{it.cancel()}
    }
    @Synchronized internal fun cancelTripUploads(ride:String){
        val pending=items().filter{it.optString("ride")==ride && it.optString("state") in listOf("Queued","Uploading","Failed")}
        pending.forEach{save(it.put("state","Canceled").put("message","Trip deleted from the app. Any uploaded DadRides copy remains on the site."))}
        cancelUploadCalls(pending.map{it.getString("id")}.toSet())
    }
    private fun ensureItem(id:String){
        val item=items().firstOrNull{it.getString("id")==id}
        check(enabled() && item?.optString("state") in listOf("Queued","Uploading")){"Upload paused or canceled"}
        check(TripStore.contains(item!!.getString("ride"))){cancelTripUploads(item.getString("ride"));"Trip deleted; upload canceled"}
    }
    fun request(path:String,method:String="GET",bytes:ByteArray?=null,mime:String="application/json",uploadId:String?=null):JSONObject{
        check(enabled()){ "DadRides is disabled" };val config=credentials();val request=Request.Builder().url(config.getString("url")+"/api/owner/"+path).header("Authorization","Bearer "+config.getString("token"))
            .method(method,bytes?.toRequestBody(mime.toMediaType())?:if(method in listOf("POST","PUT"))ByteArray(0).toRequestBody() else null).build()
        if(uploadId!=null)ensureItem(uploadId)
        val call=client.newCall(request.newBuilder().tag(String::class.java,uploadId).build())
        call.execute().use{r->val text=r.body?.string().orEmpty();val j=runCatching{JSONObject(text)}.getOrNull();if(!r.isSuccessful)throw DadRidesHttpException(r.code,j?:JSONObject().put("error","Site returned HTTP ${r.code}"));return j?:JSONObject()}
    }
    internal fun uploadBaseline(ride:String):JSONObject?=items().firstOrNull{it.optString("ride")==ride && it.optString("origin")==origin()}?.let{runCatching{JSONObject(File(folder(it.getString("id")),"manifest.json").readText())}.getOrNull()}
    @Synchronized fun schedule(){if(!::c.isInitialized || !enabled() || origin().isBlank())return
        val pending=items().filter{it.optString("state") in listOf("Queued","Uploading")};if(pending.isEmpty())return
        val manager=c.getSystemService(JobScheduler::class.java);if(manager.getPendingJob(JOB)!=null)return
        val builder=JobInfo.Builder(JOB,ComponentName(c,DadRidesJob::class.java)).setPersisted(true)
        if(pending.any{it.optBoolean("allowMetered")})builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) else builder.setRequiredNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build())
        manager.schedule(builder.build())
    }
    internal data class UploadPayload(val bytes:ByteArray,val revision:String,val photos:Map<String,File>)
    internal fun preparePayload(folder:File):UploadPayload {
        val manifest=JSONObject(File(folder,"manifest.json").readText())
        val photos=manifest.getJSONArray("photos");val files=linkedMapOf<String,File>()
        for(i in 0 until photos.length()){
            val p=photos.getJSONObject(i);val id=p.getString("id")
            require(id.matches(Regex("[a-f0-9-]{36}"))){"Invalid prepared photo identifier"}
            for(ext in listOf("jpg","thumb.jpg")){
                val name="$id.$ext";val original=File(folder,name);val source=original.readBytes()
                val thumb=ext=="thumb.jpg";val hashKey=if(thumb)"thumbSha" else "sha";val sizeKey=if(thumb)"thumbSize" else "size"
                require(source.size==p.getInt(sizeKey) && PublicRide.hash(source)==p.getString(hashKey)){"Prepared photo checksum mismatch; preview a new upload copy"}
                val clean=UploadPhoto.prepare(source,thumb)
                // Keep immutable queued sources so interrupted repairs are safe to repeat.
                files[name]=if(clean.contentEquals(source))original else File(folder,"upload-$name").also{atomic(it,clean)}
                p.put(hashKey,PublicRide.hash(clean)).put(sizeKey,clean.size)
            }
        }
        val bytes=manifest.toString().toByteArray()
        return UploadPayload(bytes,PublicRide.hash(bytes),files)
    }
    suspend fun upload(){for(item in items().filter{it.optString("state") in listOf("Queued","Uploading")}){
        if(!enabled())return
        val cm=c.getSystemService(ConnectivityManager::class.java)
        if(!item.optBoolean("allowMetered") && (cm.isActiveNetworkMetered || cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true))continue
        val id=item.getString("id");val ride=item.getString("ride");val f=folder(id)
        try{
            ensureItem(id);require(item.getString("origin")==origin()){"This upload belongs to a different site; reconnect it or prepare again"}
            item.put("state","Uploading").put("message","Uploading private draft");save(item)
            val payload=preparePayload(f);val rev=payload.revision;val m=JSONObject(String(payload.bytes))
            ensureItem(id);item.put("revision",rev);save(item)
            request("rides/$ride/revisions/$rev","PUT",payload.bytes,uploadId=id)
            val photos=m.getJSONArray("photos");for(i in 0 until photos.length()){
                ensureItem(id);val p=photos.getJSONObject(i)
                for(ext in listOf("jpg","thumb.jpg")){ensureItem(id);val name=p.getString("id")+"."+ext;request("assets/$rev/$name","PUT",payload.photos.getValue(name).readBytes(),"image/jpeg",uploadId=id)}
                item.put("progress",((i+1)*100)/(photos.length().coerceAtLeast(1)));save(item)
            }
            ensureItem(id);request("rides/$ride/finish/$rev","POST",uploadId=id);ensureItem(id);val readyMessage=RideEdits.uploadFinished(ride,rev,item.optString("editBase").takeUnless{it.isBlank()||it=="null"});item.put("state","Uploaded draft").put("progress",100).put("message",readyMessage);save(item)
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
