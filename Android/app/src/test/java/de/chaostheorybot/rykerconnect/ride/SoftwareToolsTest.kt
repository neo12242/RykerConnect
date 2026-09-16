package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class SoftwareToolsTest {
    private val id="11111111-1111-1111-1111-111111111111"
    private val text="""{"start":1000,"version":2}
{"lat":61.0,"lon":-149.0,"time":2000,"accuracy":3,"speed":10}
{"lat":61.0002,"lon":-149.0,"time":7000,"accuracy":3,"speed":10}
{"end":8000}
"""
    private fun prefs()=JSONObject("""{"imperial":true,"fahrenheit":true,"twelve":true,"large":true,"musicLeft":true,"navigation":true,"hide":false,"all":true,"priority":true,"allowed":[]}""")
    private fun archive():ByteArray=ByteArrayOutputStream().apply {RideBackup.write(this,mapOf("$id.jsonl" to text),prefs(),JSONObject())}.toByteArray()
    @Test fun backupRoundTripAndAdditiveRestore() {
        val folder=Files.createTempDirectory("ryker-restore").toFile()
        try{
            TripStore.initDirectory(folder)
            val backup=RideBackup.read(ByteArrayInputStream(archive()))
            assertEquals(1,TripStore.restoreFiles(backup.trips))
            TripStore.saveMetadata(id,"Keep this name","Original notes")
            assertEquals(0,TripStore.restoreFiles(backup.trips))
            assertEquals("Keep this name",TripStore.detail(id).summary.title)
            assertEquals(2,TripStore.detail(id).track.size)
        }finally{folder.deleteRecursively()}
    }
    @Test fun rejectsTraversalAndUnknownVersionAndCorruptGps() {
        fun zip(name:String,body:String)=ByteArrayOutputStream().apply{ZipOutputStream(this).use{it.putNextEntry(ZipEntry(name));it.write(body.toByteArray());it.closeEntry()}}.toByteArray()
        assertTrue(runCatching{RideBackup.read(ByteArrayInputStream(zip("../outside","bad")))}.isFailure)
        assertTrue(runCatching{RideBackup.read(ByteArrayInputStream(zip("manifest.json","{\"format\":\"RykerConnect\",\"version\":99}")))}.isFailure)
        val bad=ByteArrayOutputStream().apply{RideBackup.write(this,mapOf("$id.jsonl" to text.replace("61.0","961.0")),prefs(),JSONObject())}.toByteArray()
        assertTrue(runCatching{RideBackup.read(ByteArrayInputStream(bad))}.isFailure)
    }
    @Test fun trimCopiesAndRetainsOriginalAndFlagsDerived() {
        val folder=Files.createTempDirectory("ryker-trim").toFile()
        try{
            TripStore.initDirectory(folder);TripStore.restoreFiles(mapOf("$id.jsonl" to text))
            val copied=TripStore.trimmedCopy(id,1000,3000)
            assertEquals(2,TripStore.detail(id).track.size);assertEquals(1,TripStore.detail(copied).track.size)
            assertTrue(TripStore.detail(copied).summary.derived)
            assertTrue(runCatching{TripStore.trimmedCopy(id,1000,1500)}.isFailure)
        }finally{folder.deleteRecursively()}
    }
    @Test fun mergeRejectsOverlapAndKeepsSegmentBoundaries() {
        val folder=Files.createTempDirectory("ryker-merge").toFile();val second="22222222-2222-2222-2222-222222222222"
        try{
            TripStore.initDirectory(folder);TripStore.restoreFiles(mapOf("$id.jsonl" to text,"$second.jsonl" to text))
            assertTrue(runCatching{TripStore.mergedCopy(setOf(id,second))}.isFailure)
            File(folder,"$second.jsonl").writeText(text.replace("1000","9000").replace("2000","10000").replace("7000","15000").replace("8000","16000"))
            val merged=TripStore.detail(TripStore.mergedCopy(setOf(id,second)))
            assertEquals(2,TripAnalysis.segments(merged.track).size)
            assertTrue(merged.summary.derived);assertTrue(File(folder,"$id.jsonl").exists())
        }finally{folder.deleteRecursively()}
    }
    @Test fun replayDoesNotInventLocationThroughGaps() {
        val points=listOf(TrackPoint(61.0,-149.0,1000,3f),TrackPoint(61.1,-149.0,100000,3f))
        assertNull(ReplayMath.point(points,0));assertNotNull(ReplayMath.point(points,2000));assertNull(ReplayMath.point(points,50000));assertEquals(points[1],ReplayMath.point(points,100000))
    }
    @Test fun fullToFullIncludesPartialFuelAndRejectsBadInputs() {
        fun fill(km:Double,l:Double,full:Boolean)=JSONObject().put("odometerKm",km).put("litres",l).put("full",full)
        assertNull(GarageMath.consumption(listOf(fill(100.0,10.0,true))))
        assertEquals(10.0,GarageMath.consumption(listOf(fill(100.0,50.0,true),fill(150.0,4.0,false),fill(200.0,6.0,true)))!!,.0001)
        assertTrue(runCatching{GarageMath.fuel(Double.NaN,2.0,5.0)}.isFailure)
        assertTrue(runCatching{GarageMath.fuel(100.0,-2.0,5.0)}.isFailure)
    }
    @Test fun softwareRestoreMergesByIdAndRetainsExistingFavorites() {
        val folder=Files.createTempDirectory("ryker-tools").toFile()
        try{
            SoftwareStore.initFile(File(folder,"software.json"))
            SoftwareStore.add("fuel",JSONObject().put("odometerKm",100).put("litres",10).put("cost",30))
            val old=SoftwareStore.snapshot();SoftwareStore.favorite(id,true)
            val merged=SoftwareStore.merge(old.put("favorites",JSONObject().put(id,false)))
            assertEquals(1,merged.getJSONArray("fuel").length());assertTrue(merged.getJSONObject("favorites").getBoolean(id))
        }finally{folder.deleteRecursively()}
    }
}
