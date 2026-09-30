package de.chaostheorybot.rykerconnect.ride

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

object SharedLibrary {
    const val FULL="de.chaostheorybot.rykerconnect"
    const val PHONE="$FULL.phone"
    val status=MutableStateFlow("Library sync starting")
    val revision=MutableStateFlow(0)
    val peerRecording=MutableStateFlow("")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val requests=Channel<Unit>(Channel.CONFLATED)
    private val gate=Any()
    private val ready=java.util.concurrent.CountDownLatch(1)
    private lateinit var c:Context
    private lateinit var root:File
    private lateinit var model:LibraryRecords
    private var loadError:String?=null
    private var notified=""
    @Volatile private var foreground=0
    private val fingerprints=mutableMapOf<String,Triple<Long,Long,JSONObject>>()
    private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->changed()}
    fun init(app:Application) {
        c=app;root=File(c.noBackupFilesDir,"shared-library").apply{mkdirs()}
        try { model=LibraryRecords(File(root,"state.json").takeIf{it.exists()}?.let{JSONObject(it.readText())}?:JSONObject()) }
        catch(_:Exception){loadError="Library sync state could not be read. Original data retained.";status.value=loadError!!;ready.countDown();return}
        listOf("appearance","ride_options").forEach{c.getSharedPreferences(it,0).registerOnSharedPreferenceChangeListener(preferenceListener)}
        app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a:Activity){foreground++;changed()}
            override fun onActivityCreated(a:Activity,b:Bundle?){}
            override fun onActivityStarted(a:Activity){}
            override fun onActivityPaused(a:Activity){foreground=maxOf(0,foreground-1)}
            override fun onActivityStopped(a:Activity){}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
            override fun onActivityDestroyed(a:Activity){}
        })
        scope.launch{de.chaostheorybot.rykerconnect.data.RykerConnectStore(c).getDynamicColorToken.collect{changed()}}
        scope.launch{for(ignored in requests){delay(700);while(requests.tryReceive().isSuccess){};runCatching{syncNow()}.onFailure{status.value="Sync needs attention: ${it.message?.take(140)?:"Retry when available"}"}}}
        scope.launch{while(isActive){delay(30_000);if(foreground>0)changed()}}
        // Retry promptly after reconnecting; the existing foreground timer handles later failures.
        runCatching{app.getSystemService(android.net.ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(object:android.net.ConnectivityManager.NetworkCallback(){
                override fun onAvailable(network:android.net.Network){if(foreground>0)changed()}
            })}
        ready.countDown();changed()
    }
    internal fun awaitReady(){check(ready.await(20,java.util.concurrent.TimeUnit.SECONDS)){"Other edition is still starting; retry sync"};check(loadError==null){loadError.orEmpty()}}
    fun enabled()=::c.isInitialized && c.getSharedPreferences("library_options",0).getBoolean("enabled",true)
    fun enable(value:Boolean){c.getSharedPreferences("library_options",0).edit().putBoolean("enabled",value).commit();if(value)changed() else status.value="Library sync paused; local data retained";revision.value++}
    fun changed(){if(::model.isInitialized && enabled())requests.trySend(Unit)}
    fun peerPackage()=if(c.packageName==PHONE)FULL else PHONE
    fun uri(pkg:String=peerPackage(),path:String="snapshot")=Uri.parse("content://$pkg.sharedlibrary/$path")
    fun peerAvailable():Boolean {
        val peer=peerPackage()
        return c.packageManager.checkSignatures(c.packageName,peer)==PackageManager.SIGNATURE_MATCH && c.packageManager.resolveContentProvider("$peer.sharedlibrary",0)?.packageName==peer
    }
    internal fun checkCaller(context:Context,uid:Int) {
        if(uid==context.applicationInfo.uid)return
        val peer=if(context.packageName==PHONE)FULL else PHONE
        require(context.packageManager.getPackagesForUid(uid)?.contains(peer)==true && context.packageManager.checkSignatures(context.applicationInfo.uid,uid)==PackageManager.SIGNATURE_MATCH){"Only the paired RykerConnect edition can access this library"}
    }
    internal fun temporary(bytes:ByteArray)=File(root,"transfers/${UUID.randomUUID()}.tmp").also{it.parentFile!!.mkdirs();it.writeBytes(bytes)}
    internal fun blobFile(hash:String):File {require(hash.matches(Regex("[a-f0-9]{64}")));return File(root,"blobs/$hash")}
    internal fun blobBytes(bytes:ByteArray):JSONObject {
        val hash=SyncJson.hash(bytes);val destination=blobFile(hash)
        if(!destination.exists()){val temp=temporary(bytes);try{copyAtomic(temp,destination)}finally{temp.delete()}}
        return JSONObject().put("blob",hash).put("bytes",bytes.size)
    }
    internal fun copyAtomic(source:File,target:File){target.parentFile!!.mkdirs();val tmp=File(target.parentFile,".${target.name}.${UUID.randomUUID()}.tmp");try{source.inputStream().use{input->tmp.outputStream().use{out->input.copyTo(out);out.fd.sync()}};Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);synchronized(fingerprints){fingerprints.remove(target.absolutePath)}}finally{tmp.delete()}}
    internal fun blob(file:File):JSONObject = synchronized(fingerprints) {
        val length=file.length();val modified=file.lastModified()
        fingerprints[file.absolutePath]?.takeIf{it.first==length && it.second==modified && blobFile(it.third.getString("blob")).exists()}?.let{return@synchronized JSONObject(it.third.toString())}
        val tmp=temporary(ByteArray(0));val digest=MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().use{input->tmp.outputStream().use{out->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n);out.write(buffer,0,n)};out.fd.sync()}}
            check(file.length()==length && file.lastModified()==modified && tmp.length()==length){"A file changed during sync; retrying preserves both copies"}
            val hash=digest.digest().joinToString(""){"%02x".format(it)};val destination=blobFile(hash);destination.parentFile!!.mkdirs()
            if(!destination.exists())Files.move(tmp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE)
            JSONObject().put("blob",hash).put("bytes",length).also{fingerprints[file.absolutePath]=Triple(length,modified,it)}
        }finally{tmp.delete()}
    }
    private fun persist(){val tmp=temporary(model.state.toString().toByteArray());try{copyAtomic(tmp,File(root,"state.json"))}finally{tmp.delete()}}
    private fun observe() {
        val snapshot=LibraryData.snapshot(c);val active=TripStore.summary.value.takeIf{it.recording}?.id
        (snapshot.keys+model.keys()).filterNot{active!=null && it.startsWith("trip/$active.")}.forEach{key->LibraryData.validateKey(key);model.observe(key,snapshot[key])}
    }
    private fun applyPending() {
        // Confirmed website snapshots have an authoritative server sequence; only unsaved edits compete.
        for(key in model.conflicts().filter{it.startsWith("software/siteEdits/")}) {
            val versions=model.branches(key).map{LibraryData.unpack(it.get("value")) as? JSONObject}
            if(versions.all{it!=null && !it.has("conflict") && it.optLong("remoteSequence")>0 && SyncJson.canonical(it.opt("manifest"))==SyncJson.canonical(it.opt("base"))} && versions.map{it!!.optString("origin")}.distinct().size==1) {
                val index=versions.indices.maxBy{versions[it]!!.getLong("remoteSequence")}
                val newest=versions[index]!!.getLong("remoteSequence")
                if(versions.filter{it!!.getLong("remoteSequence")==newest}.map{SyncJson.canonical(it)}.distinct().size==1)model.resolve(key,index)
            }
        }
        // Immutable file records first; journals/metadata only become visible after files are verified.
        val keys=model.keys().sortedBy{if(it.startsWith("photo/") || it.endsWith(".jsonl"))0 else 1}
        for(key in keys) {
            if(model.branches(key).size!=1)continue
            val desired=model.desired(key);val actual=LibraryData.current(c,key)
            if(key.startsWith("trip/") && key.endsWith(".jsonl") && (desired==null || desired==JSONObject.NULL))
                DadRides.cancelTripUploads(key.removePrefix("trip/").removeSuffix(".jsonl"))
            if(SyncJson.fingerprint(desired)==SyncJson.fingerprint(actual)){model.applied(key,actual);continue}
            if(SyncJson.fingerprint(actual)!=model.observed(key)){model.observe(key,actual);continue}
            if(key.startsWith("trip/") && TripStore.summary.value.recording && key.startsWith("trip/${TripStore.summary.value.id}."))continue
            if(LibraryData.apply(c,key,model.observed(key),desired))model.applied(key,desired)
            else model.observe(key,LibraryData.current(c,key))
        }
        revision.value++;TripStore.refreshDisplay()
    }
    internal fun exportSnapshot():File = synchronized(gate) {
        check(enabled()){ "Library sync is paused" };check(loadError==null){loadError.orEmpty()}
        applyPending();observe();persist()
        val wire=model.wire();val recovery=File(root,"before-first-sync.json")
        if(!recovery.exists()){val backup=temporary(wire.toString().toByteArray());try{copyAtomic(backup,recovery)}finally{backup.delete()}}
        val active=TripStore.summary.value
        wire.put("recording",if(active.recording)JSONObject().put("id",active.id).put("started",active.started) else JSONObject.NULL)
        temporary(wire.toString().toByteArray())
    }
    private fun readLimited(input:InputStream,limit:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536);var size=0
        input.use{while(true){val n=it.read(buffer);if(n<0)break;size+=n;require(size<=limit){"Library manifest too large; original data retained"};out.write(buffer,0,n)}}
        return out.toByteArray()
    }
    private fun receiveBlobs(wire:JSONObject) {
        for((hash,length) in LibraryData.blobReferences(wire)) {
            val destination=blobFile(hash);if(destination.exists() && destination.length()==length)continue
            val tmp=temporary(ByteArray(0));val digest=MessageDigest.getInstance("SHA-256");var count=0L
            try {
                c.contentResolver.openInputStream(uri(path="blob/$hash"))!!.use{input->tmp.outputStream().use{out->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=length);digest.update(buffer,0,n);out.write(buffer,0,n)};out.fd.sync()}}
                require(count==length && digest.digest().joinToString(""){"%02x".format(it)}==hash){"Transferred file checksum mismatch; original retained"}
                destination.parentFile!!.mkdirs();Files.move(tmp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            }finally{tmp.delete()}
        }
    }
    @Synchronized fun syncNow() {
        if(!enabled())return
        check(loadError==null){loadError.orEmpty()};status.value="Checking library changes"
        exportSnapshot().delete()
        var peerMessage="Open the updated other edition to share this library"
        if(peerAvailable()) {try {
            val wire=JSONObject(String(readLimited(c.contentResolver.openInputStream(uri())?:error("Other edition is unavailable"),32_000_000)))
            receiveBlobs(wire)
            synchronized(gate){observe();model.merge(wire,LibraryData::validateKey);persist();applyPending();persist()}
            peerRecording.value=if(wire.optJSONObject("recording")!=null)"A ride is recording in ${if(peerPackage()==PHONE)"Phone" else "ESP"}" else ""
            peerMessage="Phone and ESP libraries synchronized"
            val fingerprint=synchronized(gate){SyncJson.fingerprint(model.wire())}
            if(fingerprint!=notified){c.contentResolver.call(uri(),"changed",null,null);notified=fingerprint}
        }catch(e:Exception){peerMessage="Other edition sync: ${e.message?.take(100)?:"unavailable"}. Local copies retained."}}
        val modifications=runCatching{ModificationSync.sync(c)}.getOrElse{ModificationSync.status.value}
        val site=runCatching{RideEdits.sync()}.fold({it},{"Website sync: ${it.message?.take(100)?:"unavailable"}"})
        // Website changes arrive after the peer exchange. Persist and announce them in this pass.
        exportSnapshot().delete()
        if(peerAvailable())runCatching{
            val fingerprint=synchronized(gate){SyncJson.fingerprint(model.wire())}
            if(fingerprint!=notified){c.contentResolver.call(uri(),"changed",null,null);notified=fingerprint}
        }
        val count=synchronized(gate){model.conflicts().size}
        val services=runCatching{ServiceMirror.sync(c,serviceSnapshot())}.fold({it},{"Service sync: ${it.message?.take(100)?:"unavailable"}"})
        c.getSharedPreferences("library_options",0).edit().putLong("last",System.currentTimeMillis()).commit()
        status.value=peerMessage+(if(count>0)" · $count library conflicts need your choice" else "")
        if(modifications.isNotBlank())status.value+=" · $modifications"
        if(site.isNotBlank())status.value+=" · $site"
        if(services.isNotBlank())status.value+=" · $services"
        revision.value++
    }
    data class Choice(val fingerprint:String,val label:String)
    internal fun serviceSnapshot():JSONObject = synchronized(gate) {
        ServiceProjection.project(model.wire(),LibraryData::unpack)
    }
    fun recordLabel(key:String)=key.removePrefix("software/").replace('/',' ')
    fun conflictChoices():Map<String,List<Choice>> = synchronized(gate){if(!::model.isInitialized)emptyMap() else model.conflicts().associateWith{key->model.branches(key).map{branch->
        val v=LibraryData.unpack(branch.get("value"));val label=if(v==JSONObject.NULL || v==null)"Removed record" else if(v is JSONObject && v.has("blob"))"Saved file · ${v.optLong("bytes")} bytes · ${v.getString("blob").take(10)}" else SyncJson.canonical(v).take(2500)
        Choice(SyncJson.fingerprint(branch.get("value")),label)
    }}}
    fun resolve(key:String,fingerprint:String){synchronized(gate){observe();val index=model.branches(key).indexOfFirst{SyncJson.fingerprint(it.get("value"))==fingerprint};require(index>=0){"Versions changed; review the conflict again"};model.resolve(key,index);persist();applyPending();persist()};changed()}
    fun copyWebsiteConnection(){check(DadRides.origin().isBlank()){ "This edition already has a website connection" };check(peerAvailable());val connection=c.contentResolver.call(uri(),"website-connection",null,null)?:error("The other app has no website connection");DadRides.configure(connection.getString("url")?:error("No website configured"),connection.getString("key")?:error("No website key configured"));DadRides.enable(true);changed()}
    fun stopPeerRecording(){check(peerAvailable());c.contentResolver.call(uri(),"stop-recording",null,null);changed()}
    fun lastSync()=if(::c.isInitialized)c.getSharedPreferences("library_options",0).getLong("last",0) else 0L
    fun hasConflict(key:String)=synchronized(gate){::model.isInitialized && model.branches(key).size>1}
    fun isTripDeleted(id:String)=synchronized(gate) {
        val key="trip/$id.jsonl"
        ::model.isInitialized && model.branches(key).size==1 && model.desired(key)==JSONObject.NULL
    }
    fun deleteTrip(id:String) {
        awaitReady()
        RecordingCoordinator.withoutRecording(c) {
            synchronized(gate) {
                check(loadError==null){loadError.orEmpty()}
                applyPending();observe()
                check(TripStore.contains(id)){"This trip has already been deleted."}
                val journal=SoftwareStore.records("journals").firstOrNull{it.optString("id")==id}
                val sharedPhotos=SoftwareStore.records("journals").filter{it.optString("id")!=id}
                    .flatMap{RidePhotos.photos(it)}.map{it.getString("id")}.toSet()
                val photos=journal?.let{RidePhotos.photos(it)}.orEmpty().map{it.getString("id")}.filterNot{it in sharedPhotos}
                val keys=listOf("trip/$id.jsonl","trip/$id.meta.json","software/journals/$id",
                    "software/favorites/$id","software/siteEdits/$id")+
                    photos.flatMap{photo->listOf("original","jpg","thumb.jpg").map{"photo/$photo.$it"}}
                check(keys.none{model.branches(it).size>1}){"Resolve this trip's library conflicts before deleting it."}
                keys.forEach{model.remove(it)}
                persist()
                try { applyPending() } finally { persist();changed() }
                check(!TripStore.contains(id)){"Trip deletion is pending. Sync again to finish."}
            }
        }
    }
    internal fun restoreWebsite(record:JSONObject) {
        awaitReady();RideEditValidation.record(record)
        check(DadRides.enabled() && DadRides.origin()==record.getString("origin")){"The website connection changed. Reload the list."}
        val id=record.getString("id")
        RecordingCoordinator.withoutRecording(c) {
            synchronized(gate) {
                applyPending();observe()
                check(!TripStore.contains(id)){"This ride is already in My Trips. Use Sync now to receive website edits."}
                val keys=listOf("trip/$id.jsonl","trip/$id.meta.json","software/siteEdits/$id")
                check(keys.none{model.branches(it).size>1}){"Resolve this ride's library conflicts before restoring it."}
                val old=RideEdits.record(id)
                check(old==null || !old.has("conflict") && SyncJson.canonical(old.opt("manifest"))==SyncJson.canonical(old.opt("base"))){"Review the saved ride edits before restoring the website copy."}
                val manifest=record.getJSONObject("manifest")
                val values=listOf(SharedLibrary.blobBytes(WebsiteRide.file(record).toByteArray()),
                    JSONObject().put("title",manifest.getString("title")).put("notes",manifest.getString("story")),LibraryData.pack(record)!!)
                keys.zip(values).forEach{(key,value)->model.replace(key,value)}
                persist()
                try{applyPending()}finally{persist();changed()}
                check(TripStore.contains(id)){"Restore is pending. Sync again to finish."}
            }
        }
    }
}
