package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** Upload derivatives only. Originals and journal images are never rewritten. */
internal object UploadPhoto {
    fun prepare(source: ByteArray, thumbnail: Boolean): ByteArray {
        require(source.size <= 3_000_000) { "Photo derivative is too large" }
        if (metadataSafe(source)) return source
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600) { "Prepared photo cannot be decoded safely" }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size)
            ?: error("Prepared photo cannot be decoded")
        try {
            // Copy pixels into SDR/sRGB instead of retaining the source bitmap's HDR gainmap.
            val clean = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888,
                false, ColorSpace.get(ColorSpace.Named.SRGB))
            try {
                clean.eraseColor(Color.WHITE)
                Canvas(clean).drawBitmap(decoded, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                if (android.os.Build.VERSION.SDK_INT >= 34) clean.setGainmap(null)
                val output = ByteArrayOutputStream()
                check(clean.compress(Bitmap.CompressFormat.JPEG, if (thumbnail) 75 else 82, output)) { "Could not prepare photo" }
                val bytes = output.toByteArray()
                check(metadataSafe(bytes)) { "Could not remove photo metadata" }
                require(bytes.size <= if (thumbnail) 500_000 else 3_000_000) { "Photo derivative is too large" }
                return bytes
            } finally { clean.recycle() }
        } finally { decoded.recycle() }
    }

    /** The API rejects EXIF/XMP (APP1), IPTC (APP13), and comments before image data. */
    fun metadataSafe(bytes: ByteArray): Boolean {
        fun at(i: Int) = bytes[i].toInt() and 255
        if (bytes.size < 4 || at(0) != 255 || at(1) != 216) return false
        var i = 2
        while (i + 3 < bytes.size) {
            if (at(i++) != 255) return false
            while (i < bytes.size && at(i) == 255) i++
            if (i >= bytes.size) return false
            when (val marker = at(i++)) {
                0xe1, 0xed, 0xfe -> return false
                0xda -> return true
                0xd9 -> return false
                else -> {
                    if (marker == 0 || i + 1 >= bytes.size) return false
                    val length = at(i) * 256 + at(i + 1)
                    if (length < 2 || i + length > bytes.size) return false
                    i += length
                }
            }
        }
        return false
    }
}
