package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class OwnershipDashboardTest {
    private fun time(date:String)=LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun expense(date:String,cents:Long?)=JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("label","Purchase").put("amountCents",cents?:JSONObject.NULL)
    private fun period()=OwnershipPeriod("Season",LocalDate.parse("2026-05-01"),LocalDate.parse("2026-09-01"),100.0,1100.0)

    @Test fun expensesUsePurchaseDateAndExcludeEstimatesAndMileageOnlyEntries() {
        val mod=ModificationRecords.create("Seat").put("estimateCents",99999).put("status","Installed").put("installedDate","2026-09-01")
        mod.getJSONArray("expenses").put(expense("2026-08-01",10000)).put(expense("2026-09-10",5000))
        val data=JSONObject().put("modifications",JSONArray().put(mod)).put("fuel",JSONArray().put(JSONObject().put("id","fuel").put("time",time("2026-08-01")).put("cost",20.15).put("odometerKm",200)))
            .put("maintenance",JSONArray().put(JSONObject().put("id","mileage").put("serviceId",ServiceCatalog.MILEAGE).put("name","Mileage").put("cost",123).put("time",time("2026-08-02"))))
        val rows=OwnershipMath.records(data).filter{period().includes(it.date)}
        assertEquals(2,rows.size);assertEquals(12015L,OwnershipMath.total(rows))
        assertEquals(0.12015,OwnershipMath.perKm(rows,period())!!,0.000001)
        mod.put("archived",true);assertEquals(12015L,OwnershipMath.total(OwnershipMath.records(data).filter{period().includes(it.date)}))
    }
    @Test fun unknownIsNotZeroAndUnconfirmedMileageWithholdsRatios() {
        val row=OwnershipExpense("one",LocalDate.parse("2026-08-01"),"Modifications","Seat",null)
        assertNull(OwnershipMath.perKm(listOf(row),period()))
        assertEquals(0.0,OwnershipMath.perKm(listOf(row.copy(cents=0)),period())!!,0.0)
        assertNull(OwnershipMath.perKm(listOf(row.copy(cents=100)),period().copy(startKm=null)))
        assertNull(OwnershipMath.perKm(listOf(row.copy(cents=100)),period().copy(endKm=100.0)))
        assertNull(period().copy(to=LocalDate.now().plusDays(1)).distanceKm())
        assertEquals(1234L,ModificationRecords.parseMoney("12.34"));assertNull(ModificationRecords.parseMoney(""))
        assertTrue(runCatching{ModificationRecords.parseMoney("12.345")}.isFailure)
    }
    @Test fun syncChoosesDownloadOnlyWhenLocalVersionIsUnchanged() {
        val base=ModificationRecords.create("Original");val website=JSONObject(base.toString()).put("notes","Site change");val local=JSONObject(base.toString()).put("notes","App change")
        assertEquals("download",ModificationSync.action(null,website,null))
        assertEquals("download",ModificationSync.action(base,website,SyncJson.fingerprint(base)))
        assertEquals("upload",ModificationSync.action(local,website,SyncJson.fingerprint(base)))
        assertEquals("upload",ModificationSync.action(local,null,null))
        assertEquals("equal",ModificationSync.action(website,website,null))
        assertEquals("upload",ModificationSync.action(local,website,null)) // restored local copy must use revision conflict, never silently replace
    }
    @Test fun backupRestoresOwnershipAndRejectsMalformedPrivateExpenses() {
        val mod=ModificationRecords.create("Seat").put("expenses",JSONArray().put(expense("2026-08-01",null)))
        val season=JSONObject().put("id",UUID.randomUUID().toString()).put("name","Season").put("from","2026-05-01").put("to","2026-09-01").put("startKm",100).put("endKm",1100)
        val data=JSONObject().put("modifications",JSONArray().put(mod)).put("ownershipSeasons",JSONArray().put(season))
        val prefs=JSONObject("""{"imperial":true,"fahrenheit":true,"twelve":true,"large":false,"musicLeft":true,"navigation":true,"hide":false,"all":true,"priority":true,"allowed":[]}""")
        fun read():JSONObject {val bytes=ByteArrayOutputStream();RideBackup.write(bytes,emptyMap(),prefs,data);return RideBackup.read(bytes.toByteArray().inputStream()).software}
        val restored=read();val folder=Files.createTempDirectory("ownership-test").toFile()
        try {SoftwareStore.initFile(java.io.File(folder,"software.json"));SoftwareStore.restore(restored);SoftwareStore.restore(restored);assertEquals(1,SoftwareStore.records("modifications").size);assertEquals(1,SoftwareStore.records("ownershipSeasons").size)
            val saved=SoftwareStore.records("modifications").single();ModificationRecords.save(JSONObject(saved.toString()).put("notes","Changed"),SyncJson.fingerprint(saved))
            assertTrue(runCatching{ModificationRecords.save(JSONObject(saved.toString()).put("notes","Stale"),SyncJson.fingerprint(saved))}.isFailure)
        }finally{folder.deleteRecursively()}
        ModificationRecords.rows(mod).single().put("amountCents",-1);assertTrue(runCatching{read()}.isFailure)
    }
    @Test fun csvEscapesFormulaLikeDescriptionsAndMarksUnknownPrices() {
        val csv=OwnershipMath.csv(listOf(OwnershipExpense("one",LocalDate.parse("2026-08-01"),"Modifications","=SUM(A1)",null)),true)
        assertTrue(csv.contains("'=SUM(A1)"));assertTrue(csv.contains("Unknown"));assertTrue(csv.contains("Odometer (mi)"))
    }
}
