package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TripStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val id = "11111111-1111-1111-1111-111111111111"
    private fun seed(modern: Boolean = true): File {
        val f = File(temp.root, "$id.jsonl")
        f.writeText("{\"start\":1000,\"version\":${if (modern) 2 else 1},\"automatic\":true}\n" +
            "{\"lat\":37.0,\"lon\":-122.0,\"time\":1000,\"accuracy\":5}\n" +
            "{\"lat\":37.0002,\"lon\":-122.0,\"time\":6000,\"accuracy\":5,\"speed\":4.4}\n" +
            "{\"lat\":37.0004,\"lon\":-122.0,\"time\":16000,\"accuracy\":5,\"speed\":2.2}\n")
        TripStore.initDirectory(temp.root); return f
    }
    @Test fun disconnectTimeoutTrimsProvisionalPointsAndEndsAtDisconnect() {
        val f = seed()
        TripStore.summary.value = TripSummary(id, 1000, recording = true, automatic = true, modern = true)
        TripStore.markDisconnect(10000)
        TripStore.stop("Disconnect", 10000)
        val d = TripStore.detail(id)
        assertEquals(10000, d.summary.ended); assertEquals(2, d.track.size)
        assertFalse(f.readText().contains("16000")); assertFalse(d.summary.interrupted)
        assertEquals(22.24, d.summary.meters, .1)
    }
    @Test fun interruptedGraceRecoveryExcludesPointsAfterDisconnect() {
        val f = seed(); f.appendText("{\"disconnect\":10000}\n")
        TripStore.initDirectory(temp.root)
        val d = TripStore.detail(id)
        assertTrue(d.summary.interrupted); assertEquals(10000, d.summary.ended); assertEquals(2, d.track.size)
        assertTrue(f.readText().contains("16000")) // Recovery never destroys originals.
    }
    @Test fun oldFilePreservedAndNameNotesExportSurviveReload() {
        val f = seed(false); f.appendText("{\"end\":20000}\n"); val original = f.readText()
        TripStore.initDirectory(temp.root)
        TripStore.saveMetadata(id, "Test <ride> & route", "Synthetic notes")
        TripStore.initDirectory(temp.root)
        val d = TripStore.detail(id)
        assertFalse(d.summary.modern); assertNull(d.stats.maxMps)
        assertEquals("Synthetic notes", d.summary.notes); assertEquals(original, f.readText())
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(TripStore.gpx(id).byteInputStream())
        assertEquals(3, xml.getElementsByTagName("trkpt").length)
        assertEquals("Test <ride> & route", xml.getElementsByTagName("name").item(0).textContent)
    }
    @Test fun emptyGpsSessionRemainsVisibleAndMalformedFilesAreReported() {
        File(temp.root, "$id.jsonl").writeText("{\"start\":1000}\n{\"end\":2000}\n")
        File(temp.root, "22222222-2222-2222-2222-222222222222.jsonl").writeText("broken")
        TripStore.initDirectory(temp.root)
        assertEquals(1, TripStore.history.value.size); assertEquals(0, TripStore.history.value.first().points)
        assertTrue(TripStore.storageError.value.contains("1 trip"))
    }
}
