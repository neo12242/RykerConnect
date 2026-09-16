package de.chaostheorybot.rykerconnect.ride

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DadRidesPlanningTest {
    private fun id()=UUID.randomUUID().toString()
    private fun plan(pid:String,sid:String)=JSONObject().put("id",pid).put("serviceId",sid).put("state","Planned").put("target","").put("notes","").put("parts",JSONArray()).put("tasks",JSONArray())
    @Test fun completionIsAtomicAndIdempotent(){
        val pid=id();val sid=id();val data=JSONObject().put("plans",JSONArray().put(plan(pid,sid)))
        val record=JSONObject().put("serviceId",sid).put("odometerKm",3000).put("cost",55)
        MaintenancePlans.completeIn(data,pid,record);MaintenancePlans.completeIn(data,pid,record)
        assertEquals(1,data.getJSONArray("maintenance").length())
        assertEquals("Completed",data.getJSONArray("plans").getJSONObject(0).getString("state"))
        assertEquals(data.getJSONArray("maintenance").getJSONObject(0).getString("id"),data.getJSONArray("plans").getJSONObject(0).getString("completionId"))
    }
    @Test fun wrongServiceDoesNotCompletePlan(){
        val pid=id();val data=JSONObject().put("plans",JSONArray().put(plan(pid,id())))
        assertThrows(IllegalArgumentException::class.java){MaintenancePlans.completeIn(data,pid,JSONObject().put("serviceId",ServiceCatalog.MILEAGE))}
        assertFalse(data.has("maintenance"));assertEquals("Planned",data.getJSONArray("plans").getJSONObject(0).getString("state"))
    }
    @Test fun invalidPlanRejected(){
        val p=plan(id(),id());p.getJSONArray("parts").put(JSONObject().put("id",id()).put("name","Oil").put("quantity",-1).put("cost",30).put("done",false))
        assertThrows(IllegalArgumentException::class.java){MaintenancePlans.validate(p)}
    }
    @Test fun estimatesDoNotModifyCompletedHistory(){
        val p=plan(id(),id());p.getJSONArray("parts").put(JSONObject().put("id",id()).put("name","Oil").put("quantity",3).put("cost",12).put("done",true))
        assertEquals(36.0,MaintenancePlans.estimate(p),0.01);assertEquals("Planned",p.getString("state"));assertFalse(p.has("completionId"))
    }
    @Test fun privacySplitsEveryReentryAndPreservesGaps(){
        val points=listOf(TrackPoint(61.0,-149.0,1000,5f),TrackPoint(61.01,-149.0,6000,5f),TrackPoint(61.0,-149.0,11000,5f),TrackPoint(61.02,-149.0,16000,5f),TrackPoint(61.03,-149.0,21000,5f,segmentStart=true),TrackPoint(61.0,-149.0,26000,5f))
        val lines=PublicRide.route(points,500.0)
        assertEquals(3,lines.size);assertTrue(lines.flatten().all{TrackMath.distance(points.first(),it)>500})
        assertTrue(PublicRide.route(points,10000.0).isEmpty())
    }
    @Test fun movingAverageUsesOnlyMeasuredMovingIntervals(){
        val p=listOf(TrackPoint(61.0,-149.0,1000,5f),TrackPoint(61.0,-149.0,6000,5f,10.0),TrackPoint(61.0,-149.0,11000,5f,null),TrackPoint(61.0,-149.0,16000,5f,0.0),TrackPoint(61.0,-149.0,80000,5f,70.0))
        assertEquals(10.0,RideCompletion.movingAverage(p)!!,0.001)
        assertNull(RideCompletion.movingAverage(p.take(1)))
    }
    @Test fun summaryEligibilityExcludesCopiesAndStationaryAutomaticSessions(){
        val t=TripSummary(id=id(),started=1,ended=5,modern=true,automatic=true)
        assertFalse(RideCompletion.eligible(t));assertFalse(RideCompletion.eligible(t.copy(automatic=false,derived=true)))
        assertTrue(RideCompletion.eligible(t.copy(automatic=false)))
        assertFalse(RideCompletion.eligible(t.copy(automatic=false,recording=true)))
    }
    @Test fun publicationSchemaOmitsPrivateDataAndCoordinatesWhenDisabled(){
        val t=TripSummary(id=id(),started=1000,ended=2000,title="Ride")
        val d=TripDetail(t,listOf(TrackPoint(61.0,-149.0,1000,5f)),TripStats(0.0,0,0,1000,null,null,0))
        val j=JSONObject().put("id",t.id).put("photos",JSONArray()).put("tags","touring, family").put("home","PRIVATE")
        val m=PublicRide.manifest(d,j,500.0,false,false)
        assertEquals(0,m.getJSONArray("route").length());assertEquals(0,m.getJSONObject("stats").length())
        assertFalse(m.toString().contains("PRIVATE"));assertFalse(m.has("parking"));assertFalse(m.toString().contains("-149"))
    }
}
