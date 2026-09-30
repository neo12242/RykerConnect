package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServiceProjectionTest {
    @Test fun sameDateServiceBaselineIsIndependentOfSyncOrder() {
        val type=ServiceCatalog.definition("Oil","oil")
        val first=JSONObject().put("id","a").put("serviceId","oil").put("time",1).put("odometerKm",1)
        val second=JSONObject(first.toString()).put("id","b").put("odometerKm",10)
        assertEquals("b",ServiceCatalog.latest(type,listOf(first,second))!!.getString("id"))
        assertEquals("b",ServiceCatalog.latest(type,listOf(second,first))!!.getString("id"))
    }
    @Test fun privatePayloadContainsOnlyServicesAndMinimalOdometers() {
        val library=LibraryRecords()
        val record=JSONObject().put("id","entry").put("name","Oil").put("serviceId","oil").put("time",1).put("odometerKm",1.609344)
            .put("receipt","PRIVATE_IMAGE").put("location",JSONObject().put("lat",61.0)).put("cost",15).put("notes","Owner note")
        library.observe("software/maintenance/entry",record)
        library.observe("software/fuel/entry",record)
        library.observe("software/vehicle",JSONObject().put("vin","PRIVATE_VIN"))
        library.observe("photo/private",JSONObject().put("blob","PRIVATE_PHOTO"))
        val result=ServiceProjection.project(library.wire()).getJSONObject("records")
        assertEquals(2,result.length())
        val service=result.getJSONArray("software/maintenance/entry").getJSONObject(0).getJSONObject("value")
        assertFalse(service.has("receipt"));assertFalse(service.has("location"));assertEquals("Owner note",service.getString("notes"))
        val fuel=result.getJSONArray("software/fuel/entry").getJSONObject(0).getJSONObject("value")
        assertEquals(setOf("id","time","odometerKm"),fuel.keys().asSequence().toSet())
        assertFalse(result.toString().contains("PRIVATE"))
    }
    @Test fun tombstoneClocksAndCompetingChangesSurviveProjection() {
        val a=LibraryRecords();val b=LibraryRecords();val key="software/maintenance/entry"
        val record=JSONObject().put("id","entry").put("name","Oil").put("serviceId","oil").put("time",1).put("odometerKm",1)
        a.observe(key,record);b.merge(a.wire()){};b.applied(key,b.desired(key))
        a.observe(key,null);b.observe(key,JSONObject(record.toString()).put("notes","Offline edit"));a.merge(b.wire()){}
        val raw=a.wire().getJSONObject("records").getJSONArray(key)
        val projected=ServiceProjection.project(a.wire()).getJSONObject("records").getJSONArray(key)
        assertEquals(2,projected.length())
        for(i in 0 until raw.length())assertEquals(raw.getJSONObject(i).getJSONObject("clock").toString(),projected.getJSONObject(i).getJSONObject("clock").toString())
        assertTrue((0 until projected.length()).any{projected.getJSONObject(it).isNull("value")})
    }
    @Test fun packedRecordIsUnpackedBeforeProjection() {
        val a=LibraryRecords();a.observe("software/fuel/entry",JSONObject().put("_rykerLibraryBlob","hash"))
        val projected=ServiceProjection.project(a.wire()){JSONObject().put("id","entry").put("time",1).put("odometerKm",2).put("receipt","private")}
        assertFalse(projected.toString().contains("receipt"));assertFalse(projected.toString().contains("_rykerLibraryBlob"))
    }
}
