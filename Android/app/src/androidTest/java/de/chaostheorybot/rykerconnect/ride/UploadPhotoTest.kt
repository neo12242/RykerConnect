package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Gainmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

internal fun metadataPhoto(): ByteArray {
    val bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(48, 90, 80))
    try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
        val jpeg = output.toByteArray()
        // Valid JPEG with an APP1 XMP block, like metadata rejected by the real API.
        val xmp = "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>".toByteArray()
        val length = xmp.size + 2
        return jpeg.copyOfRange(0, 2) + byteArrayOf(255.toByte(), 225.toByte(), (length shr 8).toByte(), length.toByte()) + xmp + jpeg.copyOfRange(2, jpeg.size)
    } finally { bitmap.recycle() }
}

class UploadPhotoTest {
    @Test fun metadataBearingJpegBecomesSafeAndCleanJpegIsNotRecompressed() {
        val source = metadataPhoto(); val saved = source.copyOf()
        assertFalse(UploadPhoto.metadataSafe(source))
        val prepared = UploadPhoto.prepare(source, false)
        assertTrue(UploadPhoto.metadataSafe(prepared))
        assertArrayEquals(saved, source)
        assertSame(prepared, UploadPhoto.prepare(prepared, false))
        val decoded = BitmapFactory.decodeByteArray(prepared, 0, prepared.size)
        assertNotNull(decoded); assertEquals(640, decoded.width); assertEquals(400, decoded.height)
        decoded.recycle()
    }

    @Test fun actualAndroidHdrEncodingIsFlattenedForUpload() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 34)
        val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
        val gain = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(60, 120, 180)); gain.eraseColor(Color.GRAY)
        try {
            bitmap.setGainmap(Gainmap(gain))
            val output = ByteArrayOutputStream(); assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            val hdr = output.toByteArray()
            assertFalse("HDR encoding should reproduce the server's metadata rejection", UploadPhoto.metadataSafe(hdr))
            val prepared = UploadPhoto.prepare(hdr, false)
            assertTrue(UploadPhoto.metadataSafe(prepared))
            val decoded = BitmapFactory.decodeByteArray(prepared, 0, prepared.size)
            assertFalse(decoded.hasGainmap()); decoded.recycle()
        } finally { bitmap.recycle(); gain.recycle() }
    }

    @Test fun existingQueuedSnapshotIsRepairedIdempotentlyWithoutChangingSources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "upload-photo-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val id = UUID.randomUUID().toString(); val source = metadataPhoto()
            val photo = JSONObject().put("id", id).put("sha", PublicRide.hash(source)).put("size", source.size)
                .put("thumbSha", PublicRide.hash(source)).put("thumbSize", source.size)
            val manifest = JSONObject().put("photos", JSONArray().put(photo)).toString()
            File(folder, "manifest.json").writeText(manifest)
            for (ext in listOf("jpg", "thumb.jpg")) File(folder, "$id.$ext").writeBytes(source)
            val prepared = DadRides.preparePayload(folder)
            assertNotEquals(PublicRide.hash(manifest.toByteArray()), prepared.revision)
            assertEquals(prepared.revision, DadRides.preparePayload(folder).revision)
            assertEquals(manifest, File(folder, "manifest.json").readText())
            val repaired = JSONObject(String(prepared.bytes)).getJSONArray("photos").getJSONObject(0)
            for (ext in listOf("jpg", "thumb.jpg")) {
                assertArrayEquals(source, File(folder, "$id.$ext").readBytes())
                val bytes = prepared.photos.getValue("$id.$ext").readBytes()
                assertTrue(UploadPhoto.metadataSafe(bytes))
                assertEquals(PublicRide.hash(bytes), repaired.getString(if (ext == "jpg") "sha" else "thumbSha"))
            }
            File(folder, "$id.jpg").appendBytes(byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { DadRides.preparePayload(folder) }
        } finally { folder.deleteRecursively() }
    }
}
