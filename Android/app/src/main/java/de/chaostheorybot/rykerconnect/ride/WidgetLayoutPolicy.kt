package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import java.security.MessageDigest

data class WidgetSize(val width: Float, val height: Float) {
    val buttonOnly get() = width < 110 || height < 100
    val roomy get() = width >= 240 && height >= 230
    val photo get() = width >= 200 && height >= 210
    val details get() = height >= 230
    val horizontalActions get() = width >= 300 && height < 210
    val padding get() = if(width < 160 || height < 160) 8 else if(roomy) 16 else 12
    val icon get() = if(roomy) 38 else if(height < 160) 22 else 28
    val title get() = if(roomy) 20 else if(width < 180) 14 else 17
    private val mapScale get() = minOf(2f,720f/maxOf(width,height))
    val mapWidth get() = (width * mapScale).toInt().coerceIn(64,720)
    val mapHeight get() = (height * mapScale).toInt().coerceIn(64,720)
}

object ParkingMapIdentity {
    fun key(spot: JSONObject, width: Int, height: Int, accent:Int=0): String {
        val raw = "v1|${spot.getDouble("lat")}|${spot.getDouble("lon")}|${spot.getLong("time")}|$width|$height|$accent"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    fun displayable(data: JSONObject) = !data.optJSONObject("widgetOptions").let { it?.optBoolean("hideDetails") ?: false } && data.optJSONObject("parking") != null
}
