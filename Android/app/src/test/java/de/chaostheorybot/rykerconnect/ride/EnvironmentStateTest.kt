package de.chaostheorybot.rykerconnect.ride

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EnvironmentStateTest {
    private fun frame(t: Float = 12.5f, h: Float = 65f, p: Float = 990f, age: Int = 1000, valid: Int = 1): ByteArray =
        ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).put(1.toByte()).put(valid.toByte())
            .putShort(42).putFloat(t).putFloat(h).putFloat(p).putInt(age).array()

    @Test fun physicalFrameUsesLittleEndianUnitsAndAges() {
        val r = EnvironmentState.parse(frame(), 2000)!!
        assertEquals(12.5f, r.celsius, 0f); assertEquals(65f, r.humidity, 0f); assertEquals(990f, r.pressureHpa, 0f)
        assertTrue(r.fresh(16000)); assertFalse(r.fresh(16001)); assertFalse(r.fresh(1999))
    }
    @Test fun missingInvalidAndStaleMeasurementsStayUnavailable() {
        for (bytes in listOf(null, byteArrayOf(), frame(valid = 0), frame(t = Float.NaN), frame(h = 101f),
            frame(p = 0f), frame(age = 15001), frame(age = -1), frame().also { it[0] = 2 })) {
            assertNull(EnvironmentState.parse(bytes, 2000))
        }
    }
    @Test fun failedReadRetainsTimestampButExpiresAndDisconnectClears() {
        EnvironmentState.accept(frame(), 2000); assertNotNull(EnvironmentState.reading.value)
        EnvironmentState.accept(null, 3000); assertNotNull(EnvironmentState.reading.value)
        assertEquals(2000L,EnvironmentState.reading.value!!.receivedAtMs)
        assertFalse(EnvironmentState.reading.value!!.fresh(16001))
        assertTrue(EnvironmentState.status.value.contains("delayed"))
        EnvironmentState.clear("Disconnected"); assertEquals("Disconnected", EnvironmentState.status.value); assertNull(EnvironmentState.reading.value)
    }
}
