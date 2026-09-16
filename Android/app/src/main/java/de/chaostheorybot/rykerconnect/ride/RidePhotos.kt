package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object RidePhotos {
    private lateinit var folder:File
    fun init(c:Context){folder=File(c.noBackupFilesDir,"journal-photos").apply{mkdirs()}}
    fun file(name:String):File{require(name.matches(Regex("[a-f0-9-]{36}\\.(original|jpg|thumb.jpg)")));return File(folder,name)}
    fun journal(id:String)=SoftwareStore.records("journals").firstOrNull{it.getString("id")==id}?:JSONObject().put("id",id).put("tags","").put("cover","").put("photos",JSONArray())
    fun photos(j:JSONObject)=j.getJSONArray("photos").let{a->(0 until a.length()).map{a.getJSONObject(it)}}
    fun save(j:JSONObject){validate(j);SoftwareStore.mutate{next->val list=SoftwareStore.records("journals").filter{it.getString("id")!=j.getString("id")};next.put("journals",JSONArray(list+j))}}
    fun validate(j:JSONObject){
        require(j.getString("id").matches(Regex("[a-f0-9-]{36}")) && j.optString("tags").length<=300)
        val p=photos(j);require(p.size<=30 && p.map{it.getString("id")}.distinct().size==p.size)
        for(x in p){require(x.getString("id").matches(Regex("[a-f0-9-]{36}")) && x.optString("caption").length<=500)}
        require(j.optString("cover").isBlank() || p.any{it.getString("id")==j.optString("cover")})
    }
    fun add(c:Context,trip:String,uri:Uri){
        val j=journal(trip);require(photos(j).size<30){"Limit: 30 photos per ride"}
        val id=UUID.randomUUID().toString();val original=file("$id.original")
        try{
            c.contentResolver.openInputStream(uri)!!.use{input->original.outputStream().use{out->val buffer=ByteArray(8192);var size=0;while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=15_000_000){"Choose a photo smaller than 15 MB"};out.write(buffer,0,n)}}}
            val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)){decoder,info,_->
                require(info.size.width.toLong()*info.size.height<=150_000_000){"Photo dimensions too large"}
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                val scale=minOf(1.0,1600.0/maxOf(info.size.width,info.size.height));decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
            }
            bitmap.useImage{idBitmap->
                file("$id.jpg").outputStream().use{idBitmap.compress(Bitmap.CompressFormat.JPEG,82,it)}
                val scale=400.0/maxOf(idBitmap.width,idBitmap.height);val thumb=Bitmap.createScaledBitmap(idBitmap,(idBitmap.width*scale).toInt().coerceAtLeast(1),(idBitmap.height*scale).toInt().coerceAtLeast(1),true)
                thumb.useImage{small->file("$id.thumb.jpg").outputStream().use{small.compress(Bitmap.CompressFormat.JPEG,75,it)}}
            }
            j.getJSONArray("photos").put(JSONObject().put("id",id).put("caption","").put("publish",true))
            if(j.optString("cover").isBlank())j.put("cover",id);save(j)
        }catch(e:Exception){listOf("original","jpg","thumb.jpg").forEach{file("$id.$it").delete()};throw e}
    }
    private inline fun Bitmap.useImage(block:(Bitmap)->Unit){try{block(this)}finally{recycle()}}
    fun remove(trip:String,id:String){val j=journal(trip);j.put("photos",JSONArray(photos(j).filter{it.getString("id")!=id}));if(j.optString("cover")==id)j.put("cover",photos(j).firstOrNull()?.getString("id")?:"");save(j)
        // Prepared uploads own independent copies, so local journal removal cannot corrupt their payload.
        listOf("original","jpg","thumb.jpg").forEach{file("$id.$it").delete()}
    }
    fun backupAssets():Map<String,ByteArray>{
        val result=linkedMapOf<String,ByteArray>();var bytes=0L
        for(j in SoftwareStore.records("journals"))for(p in photos(j))for(ext in listOf("original","jpg","thumb.jpg")){
            val name=p.getString("id")+"."+ext;val f=file(name);require(f.exists()){"A journal photo is missing; backup canceled"}
            bytes+=f.length();require(bytes<90_000_000){"Photo backup exceeds 90 MB. Export fewer local photos before backing up; none were omitted."};result[name]=f.readBytes()
        };return result
    }
    fun restoreAssets(assets:Map<String,ByteArray>){for((name,bytes) in assets){val f=file(name);if(!f.exists()){val temp=File(folder,"$name.tmp");temp.writeBytes(bytes);java.nio.file.Files.move(temp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE)}}}
}
