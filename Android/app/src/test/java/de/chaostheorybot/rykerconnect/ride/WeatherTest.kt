package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
    private val fix = WeatherFix(61.218, -149.9, 100_000)
    private val wall = 1_800_000_000_000L
    private val report = WeatherReport(10.0, 16.09344, 61, 40, wall, wall, 100_000, fix)
    @Test fun locationMustBeRecentAndNotFromFuture() {
        assertFalse(WeatherPolicy.fresh(null, 100_000))
        assertTrue(WeatherPolicy.fresh(fix, 220_000))
        assertFalse(WeatherPolicy.fresh(fix, 220_001))
        assertFalse(WeatherPolicy.fresh(fix, 99_999))
    }
    @Test fun movementRefreshesBeforeTimer() {
        assertFalse(WeatherPolicy.due(report, fix, 101_000))
        assertTrue(WeatherPolicy.due(report, fix, 1_000_000))
        val moved = fix.copy(latitude = 61.3, elapsed = 101_000)
        assertTrue(WeatherPolicy.due(report, moved, 101_000))
        assertTrue(WeatherPolicy.stale(report, moved, 101_000, wall))
    }
    @Test fun disconnectedGpsAndOldForecastAreNotCurrent() {
        assertFalse(WeatherPolicy.stale(report, fix, 101_000, wall))
        assertTrue(WeatherPolicy.stale(report, null, 101_000, wall))
        assertTrue(WeatherPolicy.stale(report, fix, 300_000, wall))
        assertTrue(WeatherPolicy.stale(report, fix.copy(elapsed = 2_000_000), 2_000_000, wall))
        assertTrue(WeatherPolicy.stale(report, fix, 101_000, wall + 3_600_001))
    }
    @Test fun conversionsAndConditions() {
        assertEquals("50°F", report.temperature(true))
        assertEquals("10°C", report.temperature(false))
        assertEquals("10 mph", report.wind(true))
        assertEquals("16 km/h", report.wind(false))
        assertEquals("Rain", report.conditions())
        assertEquals("Unknown conditions", report.copy(code = -1).conditions())
    }
    private fun payload(probability: String = "40", temperature: String = "10") = """
        {"current":{"time":1800000000,"temperature_2m":$temperature,"wind_speed_10m":16,"weather_code":61},
        "hourly":{"time":[1799996400,1800000000,1800003600],"precipitation_probability":[99,$probability,1]}}
    """
    @Test fun usesMatchingHourAndPreservesMissingProbability() {
        assertEquals(40, WeatherJson.parse(payload(), fix, wall, 100_000).rainChance)
        assertNull(WeatherJson.parse(payload("null"), fix, wall, 100_000).rainChance)
        assertNull(WeatherJson.parse(payload("110"), fix, wall, 100_000).rainChance)
    }
    @Test fun rejectsInvalidOrOldData() {
        for (body in listOf("{}", payload(temperature = "null"), payload(temperature = "999"))) {
            assertTrue(runCatching { WeatherJson.parse(body, fix, wall, 100_000) }.isFailure)
        }
        assertTrue(runCatching { WeatherJson.parse(payload(), fix, wall + 3_600_001, 100_000) }.isFailure)
    }
}
