package de.chaostheorybot.rykerconnect.ride

import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class EnvironmentReading(val celsius: Float, val humidity: Float, val pressureHpa: Float,
    val sampleAgeMs: Long, val receivedAtMs: Long, val simulated: Boolean = false) {
    fun fresh(nowMs: Long) = nowMs >= receivedAtMs && sampleAgeMs + nowMs - receivedAtMs <= 15_000
}

/** BLE environment readings. Simulation is explicit; weather never populates this state. */
object EnvironmentState {
    val reading = MutableStateFlow<EnvironmentReading?>(null)
    val status = MutableStateFlow("Sensor not connected")

    fun clear(message: String) { reading.value = null; status.value = message }

    fun accept(bytes: ByteArray?, nowMs: Long) {
        if (bytes == null) {
            status.value = "Sensor update delayed · retrying"
            return // Keep the last successful sample; freshness still expires normally.
        }
        reading.value = parse(bytes, nowMs)
        status.value = if (reading.value == null) { if(bytes?.size==20 && bytes[1].toInt() and 2 != 0) "Simulated sensor unavailable / stale" else "Sensor unavailable" } else if(reading.value?.simulated==true) "Simulated BME280" else "Measured at the Ryker"
    }

    fun parse(bytes: ByteArray?, nowMs: Long): EnvironmentReading? {
        if (bytes == null || bytes.size != 20 || bytes[0].toInt() != 1 || bytes[1].toInt() !in listOf(1,3)) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(4) // version, validity, sequence
        val t = b.float; val h = b.float; val p = b.float
        val age = b.int.toLong() and 0xffffffffL
        if (!t.isFinite() || !h.isFinite() || !p.isFinite() || t !in -40f..85f ||
            h !in 0f..100f || p !in 300f..1100f || age > 15_000) return null
        return EnvironmentReading(t, h, p, age, nowMs, bytes[1].toInt() and 2 != 0)
    }
}
