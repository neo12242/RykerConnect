package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WebsiteRideTest {
    private val id="00000000-0000-4000-8000-000000000041"
    private fun record(stats:String="{}")=RideEdits.newRecord(JSONObject("""{"id":"$id","sequence":8,"revision":"${"a".repeat(64)}","published":null,"manifest":{"version":1,"id":"$id","title":"Recovered ride","story":"Website notes","date":"2026-09-23","tags":[],"photos":[],"cover":"","route":[[[-149.8,61.2],[-149.7,61.3]]],"stats":$stats,"privacy":{"trimMeters":500,"statsIncluded":${stats!="{}"}}}}"""),"https://test.invalid")
    @Test fun websiteFileKeepsIdentityAndRouteWithoutFabricatedGps() {
        val r=record("""{"meters":1609.344,"elapsedMs":600000,"movingMs":500000,"stoppedMs":100000,"unknownMs":0,"averageMps":3.218688}""")
        val folder=Files.createTempDirectory("website-ride").toFile()
        try {
            TripStore.initDirectory(folder);assertEquals(1,TripStore.restoreFiles(mapOf("$id.jsonl" to WebsiteRide.file(r))))
            val d=TripStore.originalDetail(id)
            assertTrue(d.summary.websiteCopy);assertEquals(id,d.summary.id);assertEquals("2026-09-23",d.editedDate)
            assertEquals(2,d.editedRoute!!.size);assertTrue(d.track.isEmpty());assertEquals(1609.344,d.stats.meters,0.001)
            assertFalse(File(folder,"$id.jsonl").readLines().any{JSONObject(it).has("lat")})
            assertThrows(IllegalStateException::class.java){TripStore.gpx(id)}
            assertThrows(IllegalArgumentException::class.java){PublicRide.manifest(d,JSONObject(),500.0,true,true)}
            assertFalse(RideCompletion.eligible(d.summary))
        }finally{folder.deleteRecursively()}
    }
    @Test fun excludedStatisticsStayUnavailableAndInvalidDatesFail() {
        val r=record();val d=WebsiteRide.detail(r)
        assertFalse(d.summary.statisticsAvailable);assertNull(d.stats.maxMps)
        r.getJSONObject("manifest").put("date","2026-02-30")
        assertThrows(Exception::class.java){WebsiteRide.file(r)}
    }
    @Test fun durableRestoreDescendsFromDeletionAndSurvivesStalePeerAndRestart() {
        val key="trip/$id.jsonl";val a=LibraryRecords();val b=LibraryRecords()
        a.observe(key,"original");b.merge(a.wire()){};b.applied(key,b.desired(key))
        a.remove(key);a.applied(key,a.desired(key));val deleted=a.wire()
        a.replace(key,"website-copy")
        val restarted=LibraryRecords(JSONObject(a.state.toString()))
        assertEquals("website-copy",restarted.desired(key));assertNotEquals(SyncJson.fingerprint("website-copy"),restarted.observed(key))
        restarted.applied(key,restarted.desired(key));restarted.merge(b.wire()){}
        assertTrue(restarted.conflicts().isEmpty());assertEquals("website-copy",restarted.desired(key))
        b.merge(deleted){};b.applied(key,b.desired(key));b.merge(restarted.wire()){}
        assertTrue(b.conflicts().isEmpty());assertEquals("website-copy",b.desired(key))
    }
}
