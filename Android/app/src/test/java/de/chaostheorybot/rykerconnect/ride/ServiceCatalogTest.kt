package de.chaostheorybot.rykerconnect.ride

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.nio.file.Files

class ServiceCatalogTest {
    private fun service(id: String, km: Double, time: Long, interval: Double) = JSONObject().put("id",id)
        .put("name","Custom oil").put("odometerKm",km).put("time",time).put("intervalKm",interval).put("intervalDays",30).put("notes","Keep me")
    @Test fun migrationPreservesHistoryAndUsesLatestSchedule() {
        val old = JSONObject().put("maintenance",JSONArray(listOf(service("a",100.0,1,500.0),service("b",200.0,2,700.0))))
        val result = ServiceCatalog.migrate(old)
        assertFalse(old.getJSONArray("maintenance").getJSONObject(0).has("serviceId"))
        assertEquals("Keep me",result.getJSONArray("maintenance").getJSONObject(0).getString("notes"))
        assertEquals(500.0,result.getJSONArray("maintenance").getJSONObject(0).getDouble("intervalKm"),0.0)
        assertEquals(700.0,ServiceCatalog.items(result).first{it.getString("name")=="Custom oil"}.getDouble("intervalKm"),0.0)
        assertEquals(result.toString(),ServiceCatalog.migrate(result).toString())
    }
    @Test fun mileageAdvancesDueWithoutCompletingMaintenance() {
        val data=ServiceCatalog.migrate(JSONObject().put("maintenance",JSONArray(listOf(service("a",100.0,1,500.0)))))
        val type=ServiceCatalog.items(data).first{it.getString("name")=="Custom oil"}
        val history=listOf(data.getJSONArray("maintenance").getJSONObject(0),JSONObject().put("serviceId",ServiceCatalog.MILEAGE).put("odometerKm",550.0).put("time",2))
        assertEquals(50.0,ServiceCatalog.remainingKm(type,history,550.0)!!,0.0)
        assertEquals("a",ServiceCatalog.latest(type,history)!!.getString("id"))
        type.put("name","Renamed oil")
        assertEquals("Custom oil",history[0].getString("name"))
        assertEquals(50.0,ServiceCatalog.remainingKm(type,history,550.0)!!,0.0)
    }
    @Test fun mileageCannotBeRecurringOrDisabled() {
        assertThrows(IllegalArgumentException::class.java){ServiceCatalog.validate(ServiceCatalog.definition("Mileage record",ServiceCatalog.MILEAGE).put("intervalDays",1))}
        assertThrows(IllegalArgumentException::class.java){ServiceCatalog.validate(ServiceCatalog.definition("Mileage record",ServiceCatalog.MILEAGE).put("enabled",false))}
        assertThrows(IllegalArgumentException::class.java){ServiceCatalog.validate(ServiceCatalog.definition("Oil").put("intervalDays",1.5))}
        assertTrue(ServiceCatalog.items(ServiceCatalog.migrate(JSONObject())).all{it.getDouble("intervalKm")==0.0 && it.getInt("intervalDays")==0})
    }
    @Test fun backupMergeAdoptsIncomingConfiguredScheduleButKeepsLocalEdits() {
        SoftwareStore.initFile(Files.createTempDirectory("service-merge").resolve("software.json").toFile())
        val imported=ServiceCatalog.migrate(JSONObject().put("maintenance",JSONArray(listOf(service("a",100.0,1,500.0)))))
        SoftwareStore.restore(imported)
        val local=SoftwareStore.snapshot()
        ServiceCatalog.items(local).first{it.getString("name")=="Custom oil"}.put("intervalKm",800.0)
        SoftwareStore.commit(local)
        val merged=SoftwareStore.merge(imported)
        assertEquals(800.0,ServiceCatalog.items(merged).first{it.getString("name")=="Custom oil"}.getDouble("intervalKm"),0.0)
        assertEquals(1,merged.getJSONArray("maintenance").length())
    }
    @Test fun backupRoundTripRetainsDisabledTypesAndLegacyArchivesStillMigrate() {
        fun roundTrip(data: JSONObject): JSONObject {
            val settings=JSONObject().put("allowed",JSONArray())
            listOf("imperial","fahrenheit","twelve","large","musicLeft","navigation","hide","all","priority").forEach{settings.put(it,false)}
            val bytes=java.io.ByteArrayOutputStream()
            RideBackup.write(bytes,emptyMap(),settings,data)
            return RideBackup.read(java.io.ByteArrayInputStream(bytes.toByteArray())).software
        }
        val old=JSONObject().put("maintenance",JSONArray(listOf(service("a",100.0,1,500.0))))
        val migrated=ServiceCatalog.migrate(roundTrip(old))
        ServiceCatalog.items(migrated).first{it.getString("name")=="Custom oil"}.put("enabled",false)
        val restored=ServiceCatalog.migrate(roundTrip(migrated))
        assertFalse(ServiceCatalog.items(restored).first{it.getString("name")=="Custom oil"}.getBoolean("enabled"))
        assertEquals("a",restored.getJSONArray("maintenance").getJSONObject(0).getString("id"))
    }
}
