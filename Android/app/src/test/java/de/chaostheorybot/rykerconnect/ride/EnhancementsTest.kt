package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.ui.graphics.luminance
import de.chaostheorybot.rykerconnect.ui.theme.RideThemes
import de.chaostheorybot.rykerconnect.ui.theme.namedColors

class EnhancementsTest {
    private fun fill(id:String,km:Double,litres:Double,full:Boolean,calculate:Boolean=true)=JSONObject()
        .put("id",id).put("odometerKm",km).put("litres",litres).put("full",full).put("calculateMpg",calculate)
    @Test fun eachFillUsesItsOwnIntervalAndIncludesPartialFuel() {
        val entries=listOf(fill("a",100.0,10.0,true),fill("b",200.0,4.0,false,false),fill("c",300.0,8.0,true),fill("d",400.0,7.0,false))
        assertNull(GarageMath.forEntry(entries,"a"));assertNull(GarageMath.forEntry(entries,"b"))
        val full=GarageMath.forEntry(entries,"c")!!
        assertTrue(full.fullTank);assertEquals(6.0,full.litresPer100Km,0.0001)
        val estimate=GarageMath.forEntry(entries,"d")!!
        assertFalse(estimate.fullTank);assertEquals(7.0,estimate.litresPer100Km,0.0001)
        assertNull(GarageMath.forEntry(listOf(fill("a",100.0,10.0,true),fill("b",100.0,5.0,true)),"b"))
    }
    @Test fun syntheticTripsHaveUsefulStatsAndRemainSeparate() {
        val before=TripStore.history.value
        val demos=DemoTrips.details
        assertEquals(3,demos.size)
        assertTrue(demos.all{it.summary.id.startsWith("demo-") && it.stats.meters>2000 && it.track.size>50})
        assertTrue(demos.any{it.stats.stoppedMs>=290_000})
        assertTrue(demos.any{it.stats.gaps>0})
        assertEquals(before,TripStore.history.value)
        assertTrue(demos.all{it.track.zipWithNext().all{(a,b)->a.time<b.time}})
    }
    @Test fun simulationFlagCannotBeConfusedWithPhysicalReading() {
        fun bytes(flags:Int,age:Int=0)=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).put(1.toByte()).put(flags.toByte()).putShort(2).putFloat(22f).putFloat(48f).putFloat(1013.2f).putInt(age).array()
        assertFalse(EnvironmentState.parse(bytes(1),100)!!.simulated)
        assertTrue(EnvironmentState.parse(bytes(3),100)!!.simulated)
        assertNull(EnvironmentState.parse(bytes(2),100));assertNull(EnvironmentState.parse(bytes(3,20000),100))
        assertNull(EnvironmentState.parse(bytes(5),100))
        EnvironmentState.accept(bytes(2),100)
        assertTrue(EnvironmentState.status.value.contains("Simulated"))
    }
    @Test fun themeTextMaintainsReadableContrast() {
        fun ratio(a:Float,b:Float)=(maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
        assertEquals(8,RideThemes.size)
        for(theme in RideThemes) {
            val colors=namedColors(theme)
            assertTrue(theme.name,ratio(colors.onSurface.luminance(),colors.surface.luminance())>=4.5)
            assertTrue(theme.name,ratio(colors.onPrimary.luminance(),colors.primary.luminance())>=4.5)
            assertTrue(theme.name,ratio(colors.onPrimaryContainer.luminance(),colors.primaryContainer.luminance())>=4.5)
        }
    }
}
