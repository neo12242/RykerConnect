package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class RideBackupPauseTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private val settings = JSONObject("""{"imperial":true,"fahrenheit":true,"twelve":true,"large":false,"musicLeft":true,"navigation":true,"hide":false,"all":true,"priority":true,"allowed":[]}""")

    private fun readEvents(vararg events: String): BackupPreview {
        val text = (listOf("""{"start":1000,"version":3}""") + events + """{"end":10000}""").joinToString("\n", postfix = "\n")
        val bytes = ByteArrayOutputStream()
        RideBackup.write(bytes, mapOf("$id.jsonl" to text), settings, JSONObject())
        return RideBackup.read(bytes.toByteArray().inputStream()).also { assertEquals(text, it.trips["$id.jsonl"]) }
    }

    @Test fun pauseAndBothResumeFormatsRestoreWithoutChangingTripBytes() {
        for (resume in listOf("""{"event":"resume","at":8000}""", """{"event":"resume","at":8000,"disconnect":0}""")) {
            val backup = readEvents("""{"event":"pause","at":3000,"point":{"lat":61,"lon":-149,"time":2000,"accuracy":3}}""", resume)
            val folder = Files.createTempDirectory("pause-restore").toFile()
            try {
                TripStore.initDirectory(folder)
                assertEquals(1, TripStore.restoreFiles(backup.trips))
                assertEquals(0, TripStore.restoreFiles(backup.trips))
                assertEquals(backup.trips["$id.jsonl"], File(folder, "$id.jsonl").readText())
                val pause = TripStore.detail(id).pauses.single()
                assertEquals(3000L, pause.started)
                assertEquals(8000L, pause.ended)
                assertNotNull(pause.point)
            } finally { folder.deleteRecursively() }
        }
    }

    @Test fun endingWhilePausedNeedsNoSyntheticResumeOrLocation() {
        val backup = readEvents("""{"event":"pause","at":3000}""")
        val rows = backup.trips.getValue("$id.jsonl").lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
        val pause = RidePauses.read(rows, 1000, 10000).single()
        assertNull(pause.ended)
        assertNull(pause.point)
        assertEquals(7000L, RidePauses.total(listOf(pause), 1000, 10000))
    }

    @Test fun malformedEventsRemainRejectedIncludingResumeWithDisconnect() {
        for (event in listOf(
            """{"event":"other","at":3000,"disconnect":0}""",
            """{"event":"pause"}""",
            """{"event":"resume","at":999,"disconnect":0}""",
            """{"event":"pause","at":3000.5}""",
            """{"event":"pause","at":"3000"}""",
            """{"event":"pause","at":3000,"point":"invalid"}""",
            """{"event":"pause","at":3000,"point":{"lat":961,"lon":-149,"time":2000,"accuracy":3}}""",
            """{"event":"pause","at":3000,"point":{"lat":61,"lon":-149,"time":4000,"accuracy":3}}"""
        )) assertTrue("Malformed event accepted", runCatching { readEvents(event) }.isFailure)
    }

    // Optional local acceptance input; never check a private archive into test resources.
    @Test fun suppliedBackupPreservesEveryTripAndPhoto() {
        val path = System.getenv("RYKER_BACKUP_ACCEPTANCE")
        assumeTrue("No private acceptance archive supplied", !path.isNullOrBlank())
        val file = File(path!!)
        val backup = file.inputStream().use(RideBackup::read)
        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.name.startsWith("rides/")) assertEquals(zip.getInputStream(entry).bufferedReader().use { it.readText() }, backup.trips[entry.name.removePrefix("rides/")])
                if (entry.name.startsWith("photos/")) assertArrayEquals(zip.getInputStream(entry).use { it.readBytes() }, backup.assets[entry.name.removePrefix("photos/")])
            }
        }
    }
}
