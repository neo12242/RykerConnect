package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test

class RideModelsTest {
    @Test fun unicodeNotificationPayloadRespectsByteBudgetAndFieldSeparator() {
        val result = NotificationText.bounded("🚲".repeat(50), 61)
        assertEquals(60, result.toByteArray(Charsets.UTF_8).size)
        assertEquals("Title body", NotificationText.bounded("Title\u0003body", 50))
    }
    @Test fun mapsProgressNotificationDoesNotInventMissingDistanceOrArrow() {
        val n = MapsParser.parse("Exit the parking lot toward Amphitheatre Pkwy", "", "Arrive 11:57 AM PDT", 1000)
        assertTrue(n.active); assertEquals("", n.direction); assertNull(n.meters)
        assertEquals("Arrive 11:57 AM PDT", n.arrival)
        assertFalse(n.stale(91000)); assertTrue(n.stale(91001))
    }
    @Test fun explicitManeuversAndDistanceAreParsed() {
        val n = MapsParser.parse("In 0.2 mi, turn right onto Demo Road", "", "", 0)
        assertEquals("right", n.direction); assertEquals(321.8688, n.meters!!, .001)
        assertEquals("left", MapsParser.parse("In 300 ft, turn left", "", "", 0).direction)
        assertEquals(304.8, MapsParser.parse("In 1,000 ft, turn left", "", "", 0).meters!!, .001)
        assertEquals("", MapsParser.parse("Continue on Left Street", "", "", 0).direction)
        assertFalse(MapsParser.parse("", "", "", 0).active)
    }
    @Test fun unitsConvertWithoutChangingSourceDistance() {
        assertEquals("1.0 mi", RideUnits.distance(1609.344, true))
        assertEquals("100 ft", RideUnits.distance(30.48, true))
        assertEquals("1.6 km", RideUnits.distance(1609.344, false))
        assertEquals(32.0, RideUnits.celsiusToFahrenheit(0.0), 0.001)
    }
    @Test fun duplicateSuppressionAllowsUpdatedContentAndExpires() {
        val d = NotificationDeduper()
        assertTrue(d.accept("id", "first", 100)); assertFalse(d.accept("id", "first", 200))
        assertTrue(d.accept("id", "second", 300)); assertTrue(d.accept("id", "second", 10301))
        d.remove("id"); assertTrue(d.accept("id", "second", 10302))
    }
    @Test fun gpsRejectsBadAccuracyImpossibleJumpsAndOldFixes() {
        val a = TrackPoint(37.422, -122.084, 1000, 5f)
        assertTrue(TrackMath.acceptable(null, a))
        assertTrue(TrackMath.acceptable(a, a.copy(lat = 37.4221, time = 6000)))
        assertFalse(TrackMath.acceptable(a, a.copy(lat = 38.0, time = 6000)))
        assertFalse(TrackMath.acceptable(a, a.copy(time = 999)))
        assertFalse(TrackMath.acceptable(null, a.copy(accuracy = 100f)))
        assertFalse(TrackMath.acceptable(null, a.copy(lat = Double.NaN)))
        assertEquals(11.12, TrackMath.distance(a, a.copy(lat = 37.4221)), .1)
    }
}
