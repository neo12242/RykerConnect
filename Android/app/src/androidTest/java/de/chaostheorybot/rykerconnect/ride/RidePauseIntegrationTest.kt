package de.chaostheorybot.rykerconnect.ride

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

class RidePauseIntegrationTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun pauseResumeRecoveryBackupAndEndKeepOneRide() {
        SharedLibrary.awaitReady();SharedLibrary.enable(false);DadRides.enable(false)
        check(!TripStore.summary.value.recording)
        val dir=File(c.cacheDir,"pause-test-${UUID.randomUUID()}")
        try {
            TripStore.initDirectory(dir);TripStore.start()
            val s=TripStore.summary.value;val t=s.started;val id=s.id
            TripStore.point(TrackPoint(61.0,-149.0,t+1000,3f,5.0))
            TripStore.point(TrackPoint(61.0003,-149.0,t+6000,3f,5.0))
            TripStore.pause(t+7000)
            TripStore.point(TrackPoint(62.0,-149.0,t+10_000,3f,5.0))
            assertEquals(2,TripStore.summary.value.points)
            TripStore.initDirectory(dir)
            assertTrue(TripStore.summary.value.recording);assertTrue(TripStore.summary.value.paused);assertTrue(TripStore.summary.value.needsRecovery)
            assertEquals(id,TripStore.summary.value.id);assertTrue(TripStore.history.value.isEmpty())
            TripStore.resumeRecording(t+3_607_000)
            TripStore.point(TrackPoint(61.5,-149.0,t+3_600_000,3f,5.0))
            assertEquals(2,TripStore.summary.value.points)
            TripStore.point(TrackPoint(62.0,-149.0,t+3_608_000,3f,5.0))
            TripStore.point(TrackPoint(62.0003,-149.0,t+3_613_000,3f,5.0))
            TripStore.pause(t+3_614_000);assertTrue(TripStore.stop(endAt=t+3_624_000))
            val detail=TripStore.originalDetail(id)
            assertEquals(1,TripStore.history.value.size);assertEquals(4,detail.track.size);assertEquals(2,detail.pauses.size)
            assertEquals(3_610_000,detail.stats.pausedMs);assertTrue(detail.stats.meters<100)
            assertEquals(2,TripAnalysis.segments(detail.track).size)
            assertEquals(3_624_000,detail.stats.movingMs+detail.stats.stoppedMs+detail.stats.unknownMs+detail.stats.pausedMs)
            val backup=TripStore.backupFiles();assertTrue(backup["$id.jsonl"]!!.contains("\"event\":\"pause\""))
            val restored=File(c.cacheDir,"pause-restore-${UUID.randomUUID()}")
            TripStore.initDirectory(restored);assertEquals(1,TripStore.restoreFiles(backup))
            assertEquals(detail.pauses,TripStore.originalDetail(id).pauses)
            assertFalse(TripStore.summary.value.recording)
        }finally{TripStore.init(c)}
    }
    @Test fun staleCommandsAndRepeatedIdentifiersDoNotMutateRide() = runBlocking {
        SharedLibrary.awaitReady();SharedLibrary.enable(false);DadRides.enable(false)
        val dir=File(c.cacheDir,"pause-command-${UUID.randomUUID()}")
        try {
            TripStore.initDirectory(dir);TripStore.start();TripStore.interrupted()
            val before=RideControl.localState(c)
            val command=JSONObject().put("id",UUID.randomUUID().toString()).put("action","PAUSE").put("owner",c.packageName)
                .put("token",before.getString("token")).put("observedAt",before.getLong("observedAt"))
            val first=RideControl.executeLocal(c,command,false);assertTrue(first.toString(),first.getBoolean("ok"))
            val duplicate=RideControl.executeLocal(c,command,false);assertTrue(duplicate.getBoolean("ok"));assertTrue(duplicate.getBoolean("alreadyHandled"));assertEquals("paused",duplicate.getJSONObject("status").getString("state"))
            val stale=JSONObject(command.toString()).put("id",UUID.randomUUID().toString()).put("action","END")
            assertFalse(RideControl.executeLocal(c,stale,false).getBoolean("ok"));assertTrue(TripStore.summary.value.recording)
            TripStore.stop();Unit
        }finally{TripStore.init(c)}
    }
}
