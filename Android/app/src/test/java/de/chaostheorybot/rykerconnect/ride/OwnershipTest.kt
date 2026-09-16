package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.nio.file.Files

class OwnershipTest {
    private val id=ServiceCatalog.id("Engine oil & filter")
    private fun data():JSONObject {
        val next=ServiceCatalog.migrate(JSONObject())
        ServiceCatalog.items(next).first{it.getString("id")==id}.put("intervalKm",1000).put("intervalDays",90)
        return next.put("maintenance",JSONArray(listOf(JSONObject().put("id","service-a").put("serviceId",id).put("name","Engine oil & filter").put("time",1_000_000L).put("odometerKm",1000).put("intervalKm",0))))
            .put("fuel",JSONArray(listOf(JSONObject().put("id","fuel-a").put("time",2_000_000L).put("odometerKm",1800).put("litres",10).put("cost",25))))
    }
    @Test fun approachingDueSnoozeAndCompletionRemainDistinct() {
        val now=3_000_000L
        val first=ServiceAlerts.calculate(data(),now,300.0,30).single()
        assertTrue(first.approaching);assertFalse(first.overdue)
        val state=JSONObject().put("baseline",first.baseline).put("signature",first.signature)
        assertFalse(ServiceAlerts.shouldNotify(first,state,now))
        state.put("snoozeUntil",now+100)
        assertFalse(ServiceAlerts.shouldNotify(first,state,now))
        assertTrue(ServiceAlerts.shouldNotify(first,state,now+101))
        val updated=data();updated.getJSONArray("fuel").getJSONObject(0).put("odometerKm",2100)
        val due=ServiceAlerts.calculate(updated,now,300.0,30).single()
        assertTrue(due.overdue)
        assertTrue(ServiceAlerts.shouldNotify(due,JSONObject().put("baseline",first.baseline).put("signature",first.signature),now))
        updated.getJSONArray("maintenance").put(JSONObject().put("id","service-b").put("serviceId",id).put("name","Oil").put("time",now).put("odometerKm",2100))
        assertFalse(ServiceAlerts.calculate(updated,now,300.0,30).single().approaching)
    }
    @Test fun mileageReadingDoesNotBecomeCompletionAndDateCanTriggerFirst() {
        val data=data()
        data.getJSONArray("maintenance").put(JSONObject().put("id","mile").put("serviceId",ServiceCatalog.MILEAGE).put("time",4_000_000L).put("odometerKm",2200))
        val alert=ServiceAlerts.calculate(data,5_000_000L,0.0,0).single()
        assertTrue(alert.overdue);assertTrue(alert.baseline.startsWith("service-a:"))
        assertTrue(ServiceAlerts.calculate(data(),1_000_000L+91*ServiceAlerts.DAY,0.0,0).single().overdue)
    }
    @Test fun disabledAndNeverCompletedServicesDoNotNotify() {
        assertTrue(ServiceAlerts.calculate(ServiceCatalog.migrate(JSONObject()),System.currentTimeMillis(),500.0,30).isEmpty())
        val data=data();ServiceCatalog.items(data).first{it.getString("id")==id}.put("enabled",false)
        assertTrue(ServiceAlerts.calculate(data,System.currentTimeMillis(),500.0,30).isEmpty())
    }
    @Test fun filtersAndTotalsUseSameRecordsAndMileageAddsNoSpending() {
        val a=JSONObject().put("id","a").put("name","Oil").put("notes","winter").put("odometerKm",500).put("cost",40).put("time",0)
        val b=JSONObject().put("id","b").put("serviceId",ServiceCatalog.MILEAGE).put("odometerKm",600).put("cost",0).put("time",0)
        val c=JSONObject().put("id","c").put("litres",10).put("odometerKm",700).put("cost",20).put("time",0)
        val f=GarageFilter(query="WINTER",type="Service",minKm=400.0,maxCost=50.0)
        assertEquals(listOf(a),listOf(a,b,c).filter(f::matches))
        assertEquals(40.0,GarageReport.spending(listOf(a,b,c),"Service"),0.0)
        assertEquals(20.0,GarageReport.spending(listOf(a,b,c),"Fuel"),0.0)
        assertTrue(GarageReport.csv(listOf(a),false).contains("winter"))
        assertThrows(IllegalArgumentException::class.java){GarageFilter(minKm=10.0,maxKm=1.0).validate()}
        assertThrows(IllegalArgumentException::class.java){GarageFilter(from=LocalDate.of(2026,2,1),to=LocalDate.of(2026,1,1)).validate()}
    }
    @Test fun csvEscapesQuotesNewlinesAndFormulaPrefixes() {
        assertEquals("\"a,\"\"b\"\"\nc\"",GarageReport.cell("a,\"b\"\nc"))
        assertEquals("\"'=SUM(1,2)\"",GarageReport.cell("=SUM(1,2)"))
        assertEquals("\"'  @bad\"",GarageReport.cell("  @bad"))
    }
    @Test fun profileValidationRejectsBadVinFutureDateAndNegativeMileage() {
        VehicleProfile.validate(JSONObject())
        VehicleProfile.validate(JSONObject().put("nickname","My Ryker").put("year",2024).put("purchaseKm",100.0))
        assertThrows(IllegalArgumentException::class.java){VehicleProfile.validate(JSONObject().put("vin","123"))}
        assertThrows(IllegalArgumentException::class.java){VehicleProfile.validate(JSONObject().put("purchaseKm",-1))}
        assertThrows(IllegalArgumentException::class.java){VehicleProfile.validate(JSONObject().put("purchaseDate",LocalDate.now().plusDays(1).toString()))}
    }
    @Test fun backupMergePreservesLocalProfileAndAdoptsMissingSingletons() {
        SoftwareStore.initFile(Files.createTempDirectory("ownership").resolve("software.json").toFile())
        val incoming=JSONObject().put("vehicle",JSONObject().put("nickname","Imported")).put("reminders",JSONObject().put("enabled",false))
        SoftwareStore.restore(incoming)
        assertEquals("Imported",SoftwareStore.snapshot().getJSONObject("vehicle").getString("nickname"))
        SoftwareStore.commit(SoftwareStore.snapshot().put("vehicle",JSONObject().put("nickname","Local")))
        assertEquals("Local",SoftwareStore.merge(incoming).getJSONObject("vehicle").getString("nickname"))
    }
    @Test fun ownershipBackupRoundTripAndValidation() {
        val settings=JSONObject().put("allowed",JSONArray())
        listOf("imperial","fahrenheit","twelve","large","musicLeft","navigation","hide","all","priority").forEach{settings.put(it,false)}
        val data=JSONObject().put("vehicle",JSONObject().put("nickname","Test Ryker").put("home","Test address"))
            .put("reminders",JSONObject().put("enabled",true).put("leadDays",30).put("leadKm",500).put("snoozeDays",7))
            .put("widgetOptions",JSONObject().put("theme","Dark").put("hideDetails",true))
        fun read():JSONObject{
            val out=java.io.ByteArrayOutputStream();RideBackup.write(out,emptyMap(),settings,data)
            return RideBackup.read(java.io.ByteArrayInputStream(out.toByteArray())).software
        }
        val restored=read()
        assertEquals("Test address",restored.getJSONObject("vehicle").getString("home"))
        assertTrue(restored.getJSONObject("widgetOptions").getBoolean("hideDetails"))
        data.getJSONObject("reminders").put("leadDays",1.5)
        assertThrows(IllegalArgumentException::class.java){read()}
        data.getJSONObject("reminders").put("leadDays",30)
        data.put("vehicle","invalid")
        assertThrows(IllegalArgumentException::class.java){read()}
    }
}
