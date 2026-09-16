package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test

class TripPolicyTest {
    @Test fun stoppingDuringGraceDoesNotBlockTheNextConnection() {
        val p = TripPolicy()
        p.tick(true, true, true, true, 0, 1000)
        p.tick(false, true, true, true, 100, 1100)
        p.manualStop()
        assertFalse(p.isSuppressed)
        assertEquals(TripPolicy.Action.START, p.tick(true, true, false, false, 200, 1200))
    }
    @Test fun connectionStartsOnceAndDropoutReconnectKeepsTrip() {
        val p = TripPolicy()
        assertEquals(TripPolicy.Action.NONE, p.tick(false, true, false, false, 0, 1000))
        assertEquals(TripPolicy.Action.START, p.tick(true, true, false, false, 100, 1100))
        assertEquals(TripPolicy.Action.NONE, p.tick(true, true, true, true, 200, 1200))
        p.tick(false, true, true, true, 300, 1300)
        assertEquals(300L, p.disconnectedAt)
        assertEquals(TripPolicy.Action.NONE, p.tick(true, true, true, true, 10000, 11000))
        assertNull(p.disconnectedAt)
    }
    @Test fun timeoutUsesDisconnectTimeAndExpiresBeforeLateReconnect() {
        val p = TripPolicy()
        p.tick(true, true, true, true, 0, 1000)
        p.tick(false, true, true, true, 100, 1100)
        assertEquals(TripPolicy.Action.NONE, p.tick(false, true, true, true, 120099, 121099))
        assertEquals(TripPolicy.Action.FINISH, p.tick(true, true, true, true, 120100, 121100))
        assertEquals(1100, p.finishAt)
    }
    @Test fun manualStopWaitsForNextConnectionAndManualTripsIgnoreDropout() {
        val p = TripPolicy()
        p.tick(true, true, true, true, 0, 1000); p.manualStop()
        assertEquals(TripPolicy.Action.NONE, p.tick(true, true, false, false, 100, 1100))
        p.tick(false, true, false, false, 200, 1200)
        assertEquals(TripPolicy.Action.START, p.tick(true, true, false, false, 300, 1300))
        assertEquals(TripPolicy.Action.NONE, p.tick(false, true, true, false, 500000, 501000))
        assertEquals(TripPolicy.Action.FINISH, p.tick(true, false, true, true, 500001, 501001))
    }
    @Test fun statisticsSeparateStopsGapsAndMissingSpeed() {
        val a = TrackPoint(37.0, -122.0, 1000, 5f, 0.0)
        val points = listOf(a, a.copy(time = 6000), a.copy(lat = 37.0002, time = 11000, speed = 4.4), a.copy(lat = 37.1, time = 51000, speed = 4.0))
        val s = TripAnalysis.stats(points, 1000, 56000, true)
        assertEquals(5000, s.stoppedMs); assertEquals(5000, s.movingMs); assertEquals(45000, s.unknownMs)
        assertEquals(22.24, s.meters, .1); assertEquals(4.4, s.maxMps!!, .01)
        assertEquals(1, s.gaps)
        assertNull(TripAnalysis.stats(points.map { it.copy(speed = null) }, 1000, 56000, false).maxMps)
        assertEquals(2, TripAnalysis.segments(points).size)
    }
}
