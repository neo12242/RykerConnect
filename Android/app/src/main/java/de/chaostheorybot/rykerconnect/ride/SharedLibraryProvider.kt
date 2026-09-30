package de.chaostheorybot.rykerconnect.ride

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor

/** Android permission plus explicit package/signature verification; never publicly accessible. */
class SharedLibraryProvider:ContentProvider() {
    override fun onCreate()=true
    private fun authorized(){SharedLibrary.checkCaller(requireNotNull(context),Binder.getCallingUid());SharedLibrary.awaitReady();check(SharedLibrary.enabled()){"Library sync is paused"}}
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        authorized();require(mode=="r")
        return when {
            uri.path=="/snapshot" -> {val file=SharedLibrary.exportSnapshot();ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY,Handler(Looper.getMainLooper())){file.delete()}}
            uri.pathSegments.size==2 && uri.pathSegments[0]=="blob" -> ParcelFileDescriptor.open(SharedLibrary.blobFile(uri.pathSegments[1]),ParcelFileDescriptor.MODE_READ_ONLY)
            else -> throw java.io.FileNotFoundException("Unknown library resource")
        }
    }
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        SharedLibrary.checkCaller(requireNotNull(context),Binder.getCallingUid())
        SharedLibrary.awaitReady()
        return when(method) {
            "changed" -> {authorized();SharedLibrary.changed();Bundle.EMPTY}
            "website-connection" -> {authorized();DadRides.connectionForPeer()}
            "recording" -> Bundle().apply{putBoolean("active",TripStore.summary.value.recording)}
            "ride-state" -> Bundle().apply{putString("json",RideControl.localState(requireNotNull(context)).toString())}
            "ride-control" -> {
                val c=requireNotNull(context);val request=requireNotNull(extras)
                val identity=Binder.clearCallingIdentity()
                try { val reply=kotlinx.coroutines.runBlocking { RideControl.executeLocal(c,org.json.JSONObject(request.getString("json")?:error("Missing command")),request.getBoolean("watch")) }
                    Bundle().apply{putString("json",reply.toString())}
                } finally { Binder.restoreCallingIdentity(identity) }
            }
            "stop-recording" -> {if(TripStore.summary.value.recording)requireNotNull(context).startService(android.content.Intent(context,TripRecordingService::class.java).setAction("STOP"));Bundle.EMPTY}
            "claim","renew","release" -> RecordingCoordinator.handle(requireNotNull(context),method,Binder.getCallingUid(),extras?:Bundle.EMPTY)
            else -> error("Unsupported library operation")
        }
    }
    override fun getType(uri:Uri)="application/octet-stream"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?{authorized();return null}
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
