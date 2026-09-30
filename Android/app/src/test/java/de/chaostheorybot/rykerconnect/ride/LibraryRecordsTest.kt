package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryRecordsTest {
    private fun accept(target:LibraryRecords,source:LibraryRecords) { target.merge(source.wire()){};target.keys().filter{target.branches(it).size==1}.forEach{target.applied(it,target.desired(it))} }
    @Test fun offlineEditsConflictAndResolutionConverges() {
        val a=LibraryRecords();val b=LibraryRecords();a.observe("title","Original");accept(b,a)
        a.observe("title","Phone edit");b.observe("title","ESP edit");a.merge(b.wire()){};b.merge(a.wire()){}
        assertEquals(listOf("title"),a.conflicts());assertEquals(2,b.branches("title").size)
        a.resolve("title",a.branches("title").indexOfFirst{it.get("value")=="ESP edit"});a.applied("title",a.desired("title"));accept(b,a)
        assertEquals("ESP edit",b.desired("title"));assertTrue(b.conflicts().isEmpty())
    }
    @Test fun equalInitialDataAndIndependentRecordsMergeWithoutConflicts() {
        val a=LibraryRecords();val b=LibraryRecords();a.observe("x",JSONObject().put("a",1).put("b",2));b.observe("x",JSONObject().put("b",2).put("a",1))
        accept(a,b);accept(b,a);a.observe("fuel","New fuel");b.observe("service","New service");accept(a,b);accept(b,a)
        assertTrue(a.conflicts().isEmpty());assertEquals(SyncJson.canonical(a.wire()),SyncJson.canonical(b.wire()))
    }
    @Test fun deletionDoesNotEraseConcurrentEditAndUnseenConflictIsNotSilentlyResolved() {
        val a=LibraryRecords();val b=LibraryRecords();a.observe("ride","Original");accept(b,a)
        a.observe("ride",null);b.observe("ride","Still editing");a.merge(b.wire()){}
        a.observe("ride","Another local edit");assertEquals(2,a.branches("ride").size)
        val restored=LibraryRecords(JSONObject(a.state.toString()));assertEquals(2,restored.branches("ride").size)
    }
    @Test fun malformedBatchIsRejectedBeforeMerge() {
        val a=LibraryRecords();a.observe("safe","Keep");val before=a.state.toString();val b=LibraryRecords();b.observe("bad","No")
        assertThrows(IllegalArgumentException::class.java){a.merge(b.wire()){require(it=="safe")}};assertEquals(before,a.state.toString())
    }
    @Test fun identicalClocksWithDifferentDataRemainReviewable(){
        val a=LibraryRecords();a.observe("record","Original");val wire=a.wire()
        wire.getJSONObject("records").getJSONArray("record").getJSONObject(0).put("value","Recovered different copy")
        a.merge(wire){};assertEquals(2,a.branches("record").size)
    }
    @Test fun durableDeletionSurvivesRestartAndStalePeerThenBackupRestoreConverges(){
        val a=LibraryRecords();val b=LibraryRecords()
        a.observe("trip","Original file");accept(b,a)
        val stale=b.wire()
        a.remove("trip")
        val restarted=LibraryRecords(JSONObject(a.state.toString()))
        assertEquals(SyncJson.fingerprint("Original file"),restarted.observed("trip"))
        assertEquals(JSONObject.NULL,restarted.desired("trip"))
        restarted.applied("trip",null)
        restarted.merge(stale){}
        assertEquals(JSONObject.NULL,restarted.desired("trip"))
        accept(b,restarted)
        assertEquals(JSONObject.NULL,b.desired("trip"))
        restarted.observe("trip","Original file") // Explicit backup restore is a new change.
        accept(b,restarted)
        assertEquals("Original file",b.desired("trip"))
        assertTrue(b.conflicts().isEmpty())
    }
}
