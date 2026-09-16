package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import de.chaostheorybot.rykerconnect.ui.theme.isNightHour
import java.nio.file.Files

class SoftwareExpansionTest {
    private fun fill(id:String,km:Double,time:Long,missed:Boolean=false)=JSONObject().put("id",id).put("odometerKm",km).put("time",time).put("litres",10).put("cost",20).put("full",true).put("missedFill",missed)
    @Test fun missingFillExcludesOnlyAffectedEconomyIntervals() {
        val a=fill("a",100.0,1000);val b=fill("b",200.0,2000,true);val c=fill("c",300.0,3000)
        assertNull(GarageMath.forEntry(listOf(a,b),"b"));assertNull(GarageMath.consumption(listOf(a,b)))
        assertEquals(10.0,GarageMath.forEntry(listOf(a,b,c),"c")!!.litresPer100Km,0.001)
        assertNull(GarageInsights.costPerKm(listOf(a,b,c)))
    }
    @Test fun costIntervalExcludesFirstTankCost() {
        assertEquals(.2,GarageInsights.costPerKm(listOf(fill("a",100.0,1),fill("b",200.0,2)))!!,.0001)
        assertNull(GarageInsights.costPerKm(listOf(fill("a",100.0,1))))
    }
    @Test fun historicalEditsCannotInvertOdometerSequence() {
        val records=listOf(fill("a",100.0,1000),fill("b",300.0,3000))
        GarageInsights.validateOdometer(records,2000,200.0)
        assertThrows(IllegalArgumentException::class.java){GarageInsights.validateOdometer(records,2000,400.0)}
        assertThrows(IllegalArgumentException::class.java){GarageInsights.validateOdometer(records,2000,50.0)}
    }
    @Test fun stopDetectionDoesNotJoinGpsGapsOrUnknownSpeeds() {
        fun point(t:Long,s:Double?=0.0)=TrackPoint(61.0,-149.0,t,5f,s)
        assertEquals(1,TripInsights.stops((0..8).map{point(it*5000L)}).size)
        assertTrue(TripInsights.stops(listOf(point(0),point(5000),point(40000),point(45000))).isEmpty())
        assertTrue(TripInsights.stops((0..8).map{point(it*5000L,null)}).isEmpty())
    }
    @Test fun demoAltitudeExistsAndDoesNotMutateHistory() {
        val old=TripStore.history.value
        assertTrue(DemoTrips.details.all{it.track.all{p->p.altitude!=null}})
        assertEquals(old,TripStore.history.value)
    }
    @Test fun dayNightBoundariesSupportSchedulesAcrossMidnight() {
        assertTrue(isNightHour(6,7,19));assertFalse(isNightHour(7,7,19));assertTrue(isNightHour(19,7,19))
        assertFalse(isNightHour(22,20,8));assertTrue(isNightHour(9,20,8))
    }
    @Test fun stationarySessionsRemainClassifiedWithoutHidingMovingRides() {
        assertTrue(TripSummary(automatic=true,modern=true,meters=0.0).stationarySession)
        assertFalse(TripSummary(automatic=false,modern=true,meters=0.0).stationarySession)
        assertFalse(TripSummary(automatic=true,modern=true,meters=200.0).stationarySession)
    }
    @Test fun dashboardOrderDropsUnknownAndDuplicateEntries() {
        assertEquals(listOf("Sensor","Music","Ride","Navigation","Weather"),DashboardLayout.order("Sensor,Music,Sensor,unknown"))
    }
    @Test fun editingPreservesIdentityAndOtherRecords() {
        val file=Files.createTempDirectory("garage-edit").resolve("software.json").toFile()
        SoftwareStore.initFile(file)
        SoftwareStore.add("fuel",fill("unused",100.0,1));SoftwareStore.add("fuel",fill("unused",200.0,2))
        val before=SoftwareStore.records("fuel");val id=before[0].getString("id")
        SoftwareStore.update("fuel",id,JSONObject(before[0].toString()).put("notes","Corrected"))
        val after=SoftwareStore.records("fuel");assertEquals(2,after.size);assertEquals(id,after[0].getString("id"));assertEquals(before[1].toString(),after[1].toString())
        assertThrows(IllegalArgumentException::class.java){SoftwareStore.update("fuel","missing",JSONObject())}
    }
    @Test fun appearanceBackupRejectsUnknownThemesAndInvalidHours() {
        BackupPreferences.validate(JSONObject().put("appearance",JSONObject().put("theme","Light").put("mode","Schedule").put("dayHour","7").put("nightHour","19")))
        assertThrows(IllegalArgumentException::class.java){BackupPreferences.validate(JSONObject().put("appearance",JSONObject().put("theme","Bad")))}
        assertThrows(IllegalArgumentException::class.java){BackupPreferences.validate(JSONObject().put("appearance",JSONObject().put("dayHour","25")))}
    }
    @Test fun recentAutomaticInterruptedSessionResumesWithGapAndOriginalId() {
        val folder=Files.createTempDirectory("resume-trip").toFile();val id=java.util.UUID.randomUUID().toString();val now=System.currentTimeMillis()
        java.io.File(folder,"$id.jsonl").writeText(JSONObject().put("start",now-60_000).put("version",2).put("automatic",true).toString()+"\n"+JSONObject().put("lat",61).put("lon",-149).put("accuracy",5).put("speed",2).put("time",now-50_000)+"\n")
        TripStore.initDirectory(folder)
        assertEquals(id,TripStore.recoverable(now));assertNull(TripStore.recoverable(now+180_000))
        TripStore.resume(id);assertEquals(id,TripStore.summary.value.id);assertTrue(TripStore.summary.value.recording)
        TripStore.point(TrackPoint(61.0,-149.0,now,5f,0.0))
        TripStore.stop()
        assertTrue(TripStore.detail(id).track.last().segmentStart)
        assertEquals(1,TripStore.history.value.size)
    }
}
