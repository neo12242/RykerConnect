package de.chaostheorybot.rykerconnect.ride

import de.chaostheorybot.rykerconnect.ui.theme.resolveRideTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResponsiveWidgetPolicyTest {
    @Test fun widgetSizesChooseContentInsteadOfStretchingOneLayout(){
        assertTrue(WidgetSize(56f,56f).buttonOnly)
        assertFalse(WidgetSize(140f,150f).buttonOnly)
        assertFalse(WidgetSize(140f,150f).details)
        assertFalse(WidgetSize(140f,150f).photo)
        assertTrue(WidgetSize(330f,150f).horizontalActions)
        assertTrue(WidgetSize(360f,500f).roomy)
        assertTrue(WidgetSize(360f,500f).photo)
        assertTrue(WidgetSize(360f,500f).details)
        assertTrue(WidgetSize(900f,900f).mapWidth<=720)
        assertTrue(WidgetSize(900f,900f).mapHeight<=720)
        val tall=WidgetSize(180f,500f)
        assertEquals(180.0/500,tall.mapWidth.toDouble()/tall.mapHeight,0.003)
    }
    @Test fun cacheIdentityNeverReusesAnotherParkingLocationOrSize(){
        val p=JSONObject().put("lat",61.0).put("lon",-149.0).put("time",1000)
        val key=ParkingMapIdentity.key(p,300,300)
        assertEquals(key,ParkingMapIdentity.key(JSONObject(p.toString()),300,300))
        assertNotEquals(key,ParkingMapIdentity.key(JSONObject(p.toString()).put("lon",-150.0),300,300))
        assertNotEquals(key,ParkingMapIdentity.key(JSONObject(p.toString()).put("time",2000),300,300))
        assertNotEquals(key,ParkingMapIdentity.key(p,600,300))
        assertTrue(key.matches(Regex("[a-f0-9]{64}")))
    }
    @Test fun privacyAndMissingParkingSuppressMapRequests(){
        assertFalse(ParkingMapIdentity.displayable(JSONObject()))
        val data=JSONObject().put("parking",JSONObject().put("lat",61.0))
        assertTrue(ParkingMapIdentity.displayable(data))
        data.put("widgetOptions",JSONObject().put("hideDetails",true))
        assertFalse(ParkingMapIdentity.displayable(data))
    }
    @Test fun namedPhoneAndScheduledThemesUseTheAppResolver(){
        assertEquals("Cyber Orange",resolveRideTheme(mapOf("mode" to "Fixed","theme" to "Cyber Orange"),false,12)?.name)
        val phone=mapOf("mode" to "Phone","dayTheme" to "Light","nightTheme" to "Blue Abyss")
        assertEquals("Light",resolveRideTheme(phone,false,12)?.name)
        assertEquals("Blue Abyss",resolveRideTheme(phone,true,12)?.name)
        val schedule=phone+mapOf("mode" to "Schedule","dayHour" to "7","nightHour" to "19")
        assertEquals("Light",resolveRideTheme(schedule,true,10)?.name)
        assertEquals("Blue Abyss",resolveRideTheme(schedule,false,21)?.name)
        assertNull(resolveRideTheme(emptyMap<String,String>(),false,12))
    }
    @Test fun newDefaultAndOldExplicitWidgetOverridesRemainValid(){
        listOf("App","System","Light","Dark").forEach{RykerWidgets.validate(JSONObject().put("theme",it))}
        RykerWidgets.validate(JSONObject())
        assertThrows(IllegalArgumentException::class.java){RykerWidgets.validate(JSONObject().put("theme","invalid"))}
    }
}
