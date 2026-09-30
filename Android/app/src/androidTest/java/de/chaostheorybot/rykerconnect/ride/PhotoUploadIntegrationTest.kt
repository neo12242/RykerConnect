package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Requires scripts/test-dadrides-worker.mjs through adb reverse tcp:8890 tcp:8890. */
class PhotoUploadIntegrationTest {
    @Test fun retryRepairsOldFailedPhotoAgainstUnchangedWorker(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val root = File(base.cacheDir, "photo-upload-integration-$suffix").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("test-$suffix-$name", mode)
        }
        val ride = UUID.randomUUID().toString(); val photo = UUID.randomUUID().toString()
        val source = metadataPhoto(); val hash = PublicRide.hash(source)
        val manifest = JSONObject().put("version", 1).put("id", ride).put("title", "TEST local photo repair")
            .put("story", "Synthetic fixture, never published").put("date", "2026-09-24")
            .put("tags", JSONArray()).put("cover", photo).put("route", JSONArray()).put("stats", JSONObject())
            .put("privacy", JSONObject().put("trimMeters", 500).put("statsIncluded", false))
            .put("photos", JSONArray().put(JSONObject().put("id", photo).put("caption", "Synthetic image")
                .put("sha", hash).put("size", source.size).put("thumbSha", hash).put("thumbSize", source.size)))
        val bytes = manifest.toString().toByteArray(); val oldRevision = PublicRide.hash(bytes)
        var newRevision: String? = null
        try {
            DadRides.init(context)
            DadRides.configure("http://127.0.0.1:8890", File(base.cacheDir, "dadrides-test-key").readText().trim())
            DadRides.enable(true)
            DadRides.request("rides/$ride/revisions/$oldRevision", "PUT", bytes)
            val rejection = assertThrows(IllegalStateException::class.java) {
                DadRides.request("assets/$oldRevision/$photo.jpg", "PUT", source, "image/jpeg")
            }
            assertEquals("Upload a JPEG without EXIF metadata", rejection.message)
            val upload = UUID.randomUUID().toString()
            val folder = File(root, "dadrides/$upload").apply { mkdirs() }
            File(folder, "manifest.json").writeBytes(bytes)
            for (ext in listOf("jpg", "thumb.jpg")) File(folder, "$photo.$ext").writeBytes(source)
            File(folder, "state.json").writeText(JSONObject().put("id", upload).put("ride", ride)
                .put("revision", oldRevision).put("origin", "http://127.0.0.1:8890").put("title", "TEST local photo repair")
                .put("created", System.currentTimeMillis()).put("state", "Failed").put("allowMetered", true)
                .put("progress", 0).put("message", rejection.message).toString())
            DadRides.retry(upload)
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline && DadRides.items().single().optString("state") in listOf("Queued", "Uploading")) delay(200)
            val done = DadRides.items().single()
            newRevision = done.getString("revision")
            assertEquals(done.optString("message"), "Uploaded draft", done.getString("state"))
            assertNotEquals(oldRevision, newRevision)
            val remote = DadRides.request("rides/$ride/revisions/$newRevision")
            assertEquals(1, remote.getInt("ready")); assertTrue(remote.isNull("published"))
            assertArrayEquals(source, File(folder, "$photo.jpg").readBytes())
            assertArrayEquals(bytes, File(folder, "manifest.json").readBytes())
        } finally {
            for (revision in setOfNotNull(oldRevision, newRevision)) runCatching { DadRides.request("rides/$ride/revisions/$revision", "DELETE") }
            DadRides.disconnect(); DadRides.init(base); root.deleteRecursively()
        }
    }
}
