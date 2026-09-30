package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RidePausesTest {
    @Test fun pauseSeparatesMovingStationaryAndMissingGpsTime() {
        val points=listOf(TrackPoint(61.0,-149.0,1000,3f,5.0),TrackPoint(61.0003,-149.0,6000,3f,5.0),
            TrackPoint(61.0003,-149.0,11000,3f,0.0),TrackPoint(62.0,-149.0,3_620_000,3f,5.0,segmentStart=true),TrackPoint(62.0003,-149.0,3_625_000,3f,5.0))
        val s=TripAnalysis.stats(points,1000,3_626_000,true,listOf(RidePause(12000,3_612_000)))
        assertEquals(3_600_000,s.pausedMs);assertEquals(10_000,s.movingMs);assertEquals(5_000,s.stoppedMs);assertEquals(10_000,s.unknownMs)
        assertTrue(s.meters<100);assertEquals(2,TripAnalysis.segments(points).size)
    }
    @Test fun duplicatePauseEventsAndClockOrderingCannotDoubleCount() {
        val rows=listOf(JSONObject().put("event","pause").put("at",100),JSONObject().put("event","pause").put("at",110),
            JSONObject().put("event","resume").put("at",90),JSONObject().put("event","resume").put("at",200))
        val pauses=RidePauses.read(rows,0,300)
        assertEquals(1,pauses.size);assertEquals(100,RidePauses.total(pauses,0,300))
        assertEquals(50,RidePauses.total(pauses,150,300))
    }
    @Test fun pauseWithoutFreshLocationHasNoInventedPin() {
        val row=JSONObject().put("event","pause").put("at",100_000).put("point",JSONObject().put("lat",61).put("lon",-149).put("time",10).put("accuracy",5))
        assertNull(RidePauses.read(listOf(row),0,200_000).single().point)
    }
    @Test fun longManualPauseSurvivesEspDisconnectAndReconnect() {
        val policy=TripPolicy()
        policy.tick(true,true,true,true,0,1000)
        assertEquals(TripPolicy.Action.NONE,policy.tick(false,true,true,true,1,1001,paused=true))
        assertEquals(TripPolicy.Action.NONE,policy.tick(false,false,true,true,4_000_000,4_001_000,paused=true))
        assertEquals(TripPolicy.Action.NONE,policy.tick(true,true,true,true,4_100_000,4_101_000,paused=true))
        assertNull(policy.disconnectedAt)
    }
}
