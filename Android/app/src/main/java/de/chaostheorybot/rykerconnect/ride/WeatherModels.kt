package de.chaostheorybot.rykerconnect.ride

import kotlin.math.*

data class WeatherFix(val latitude: Double, val longitude: Double, val elapsed: Long)
data class WeatherReport(val celsius: Double, val windKmh: Double, val code: Int,
    val rainChance: Int?, val forecastAt: Long, val fetchedAt: Long, val fetchedElapsed: Long,
    val fix: WeatherFix) {
    fun temperature(fahrenheit: Boolean) = "${(if (fahrenheit) celsius * 1.8 + 32 else celsius).roundToInt()}°${if (fahrenheit) "F" else "C"}"
    fun wind(imperial: Boolean) = "${(windKmh * if (imperial) 0.621371 else 1.0).roundToInt()} ${if (imperial) "mph" else "km/h"}"
    fun conditions() = when (code) {
        0 -> "Clear"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"; 66, 67 -> "Freezing rain"; 71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"; 85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"; else -> "Unknown conditions"
    }
}

object WeatherPolicy {
    const val GPS_MAX_AGE = 120_000L
    const val REFRESH = 900_000L
    const val STALE = 1_800_000L
    fun distance(a: WeatherFix, b: WeatherFix): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun fresh(fix: WeatherFix?, now: Long) = fix != null && now - fix.elapsed in 0..GPS_MAX_AGE
    fun due(report: WeatherReport?, fix: WeatherFix, now: Long) = report == null ||
        now - report.fetchedElapsed >= REFRESH || distance(report.fix, fix) >= 5_000
    fun stale(report: WeatherReport, fix: WeatherFix?, now: Long, wall: Long) =
        !fresh(fix, now) || now - report.fetchedElapsed !in 0..STALE ||
            wall - report.forecastAt !in -900_000..3_600_000 || (fix != null && distance(report.fix, fix) >= 5_000)
}
