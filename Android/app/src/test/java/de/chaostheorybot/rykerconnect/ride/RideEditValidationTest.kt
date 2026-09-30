package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class RideEditValidationTest {
    private val id="00000000-0000-4000-8000-000000000001"
    private fun manifest()=JSONObject("""{"version":1,"id":"$id","title":"Ride","story":"","date":"2026-09-24","tags":[],"photos":[],"cover":"","route":[],"stats":{},"privacy":{"trimMeters":500,"statsIncluded":false}}""")
    @Test fun acceptsPrivateRouteAndStatistics(){RideEditValidation.manifest(manifest(),id)}
    @Test fun rejectsMalformedRouteBeforeApplyingOverlay(){
        assertThrows(IllegalArgumentException::class.java){RideEditValidation.manifest(manifest().put("route",org.json.JSONArray("[[[181,0]]]")),id)}
    }
    @Test fun rejectsMissingPhotoMetadataAndMismatchedRide(){
        assertThrows(Exception::class.java){RideEditValidation.manifest(manifest().put("photos",org.json.JSONArray("[{}]")),id)}
        assertThrows(IllegalArgumentException::class.java){RideEditValidation.manifest(manifest().put("id","another-ride"),id)}
    }
}
