package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import java.nio.file.Files

class ServiceBaselinesTest {
    private val mile = 1.609344
    private fun data(): JSONObject = ServiceCatalog.migrate(JSONObject()).also { d ->
        ServiceCatalog.items(d).first { it.getString("name") == "Engine oil & filter" }
            .put("intervalKm", 1000 * mile).put("intervalDays", 365).put("configured", true)
    }
    private fun oil(d: JSONObject) = ServiceCatalog.items(d).first { it.getString("name") == "Engine oil & filter" }
    @Test fun startsEveryEnabledItemWithoutInventingHistoryOrIntervals() {
        val original = data();val type = oil(original)
        val result = ServiceBaselines.apply(original,"2026-09-22",mile)
        assertEquals(11,ServiceBaselines.records(result).size)
        assertFalse(result.has("maintenance"));assertFalse(result.has("fuel"))
        assertEquals(original.getJSONArray("serviceTypes").toString(),result.getJSONArray("serviceTypes").toString())
        val starts=ServiceBaselines.records(result)
        assertEquals(1000*mile,ServiceCatalog.remainingKm(type,emptyList(),mile,starts)!!,0.000001)
        assertEquals(mile,ServiceBaselines.odometer(emptyList(),starts),0.0)
        assertEquals(1,ServiceAlerts.calculate(result,0,10.0,30).size)
        assertNull(ServiceBaselines.startingPoint(ServiceCatalog.definition("Mileage record",ServiceCatalog.MILEAGE),emptyList(),starts))
    }
    @Test fun actualServiceWinsAndOnlyItsCounterResets() {
        val d = ServiceBaselines.apply(data(),"2026-09-22",mile);val type=oil(d)
        val record=JSONObject().put("id","actual").put("serviceId",type.getString("id")).put("name",type.getString("name"))
            .put("time",1790000000000L).put("odometerKm",500*mile).put("cost",99).put("intervalKm",0)
        d.put("maintenance",JSONArray(listOf(record)))
        val updated=ServiceBaselines.apply(d,"2026-09-23",2*mile)
        assertEquals(record.toString(),updated.getJSONArray("maintenance").getJSONObject(0).toString())
        assertEquals("actual",ServiceBaselines.startingPoint(type,listOf(record),ServiceBaselines.records(updated))!!.getString("id"))
        assertEquals(900*mile,ServiceCatalog.remainingKm(type,listOf(record),600*mile,ServiceBaselines.records(updated))!!,0.000001)
        assertEquals("2026-09-22",ServiceBaselines.records(updated).first{it.getString("id")==type.getString("id")}.getString("date"))
    }
    @Test fun calendarDateSurvivesTimezoneAndDaylightSaving() {
        val old=TimeZone.getDefault()
        try { for(zone in listOf("America/Anchorage","UTC","Pacific/Auckland")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val d=ServiceBaselines.apply(data(),"2026-09-22",mile)
            val start=ServiceBaselines.startingPoint(oil(d),emptyList(),ServiceBaselines.records(d))!!
            val due=java.time.Instant.ofEpochMilli(ServiceBaselines.dueTime(start,60)!!).atZone(ZoneId.systemDefault())
            assertEquals(LocalDate.parse("2026-11-21"),due.toLocalDate());assertEquals(0,due.hour)
        } } finally { TimeZone.setDefault(old) }
    }
    @Test fun rejectsInvalidValuesAndLeavesDisabledServicesAlone() {
        assertThrows(Exception::class.java){ServiceBaselines.create("oil","2026-02-30",mile)}
        assertThrows(Exception::class.java){ServiceBaselines.create("oil","2026-09-22",-1.0)}
        assertThrows(Exception::class.java){ServiceBaselines.apply(data(),LocalDate.now().plusDays(1).toString(),mile)}
        val d=data();oil(d).put("enabled",false)
        assertEquals(10,ServiceBaselines.records(ServiceBaselines.apply(d,"2026-09-22",mile)).size)
    }
    @Test fun removalStopsBaselineRemindersAndBackupMergeDoesNotResurrectThem() {
        val d=ServiceBaselines.apply(data(),"2026-09-22",mile)
        SoftwareStore.initFile(Files.createTempDirectory("baseline-merge").resolve("software.json").toFile())
        SoftwareStore.commit(ServiceBaselines.remove(d))
        val merged=SoftwareStore.merge(d)
        assertTrue(ServiceBaselines.records(merged).none{it.getBoolean("enabled")})
        assertTrue(ServiceAlerts.calculate(merged,0,10.0,30).isEmpty())
    }
    @Test fun backupRoundTripValidatesAndPreservesBaselines() {
        val settings=JSONObject().put("allowed",JSONArray())
        listOf("imperial","fahrenheit","twelve","large","musicLeft","navigation","hide","all","priority").forEach{settings.put(it,false)}
        val d=ServiceBaselines.apply(data(),"2026-09-22",mile)
        val bytes=java.io.ByteArrayOutputStream();RideBackup.write(bytes,emptyMap(),settings,d)
        val read=RideBackup.read(java.io.ByteArrayInputStream(bytes.toByteArray())).software
        assertEquals(d.getJSONArray("serviceBaselines").toString(),read.getJSONArray("serviceBaselines").toString())
    }
    @Test fun mirrorRetainsBaselineClocksConflictsAndRemovalWithoutPrivateFields() {
        val a=LibraryRecords();val b=LibraryRecords();val key="software/serviceBaselines/oil"
        a.observe(key,ServiceBaselines.create("oil","2026-09-22",mile).put("receipt","PRIVATE"))
        b.merge(a.wire(),{})
        b.applied(key,b.desired(key))
        a.observe(key,ServiceBaselines.create("oil","2026-09-23",mile))
        b.observe(key,ServiceBaselines.create("oil","2026-09-22",2*mile))
        a.merge(b.wire(),{})
        val projected=ServiceProjection.project(a.wire())
        assertEquals(2,projected.getJSONObject("records").getJSONArray(key).length())
        assertFalse(projected.toString().contains("PRIVATE"))
        a.resolve(key,0)
        a.applied(key,a.desired(key))
        a.remove(key)
        assertEquals(JSONObject.NULL,ServiceProjection.project(a.wire()).getJSONObject("records").getJSONArray(key).getJSONObject(0).get("value"))
    }
}
