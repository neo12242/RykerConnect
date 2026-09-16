package de.chaostheorybot.rykerconnect.ride

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.chaostheorybot.rykerconnect.R
import org.json.JSONObject

object ResponsiveWidgets {
    @Suppress("DEPRECATION")
    fun sizes(options:Bundle):List<WidgetSize>{
        val actual=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        return (if(actual.isNotEmpty())actual.map{WidgetSize(it.width,it.height)} else listOf(
            WidgetSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,150).toFloat(),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,150).toFloat()),
            WidgetSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,150).toFloat(),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,150).toFloat())))
            .filter{it.width>0 && it.height>0}.map{WidgetSize(it.width.coerceAtMost(900f),it.height.coerceAtMost(900f))}.distinct().take(4)
            .ifEmpty{listOf(WidgetSize(150f,150f))}
    }
    fun update(c:Context,m:AppWidgetManager,id:Int){
        val info=m.getAppWidgetInfo(id)?:return
        val kind=when(info.provider.className){QuickActionsWidget::class.java.name->"quick";LastParkedWidget::class.java.name->"parked";else->"ryker"}
        val data=SoftwareStore.snapshot();val p=WidgetAppearance.palette(c,data.optJSONObject("widgetOptions")?:JSONObject())
        val layouts=sizes(m.getAppWidgetOptions(id)).associate{size->SizeF(size.width,size.height) to render(c,kind,data,p,size)}
        m.updateAppWidget(id,RemoteViews(layouts))
        if(kind=="parked")ParkingWidgetMaps.ensure(c)
    }
    internal fun render(c:Context,kind:String,data:JSONObject,p:WidgetPalette,size:WidgetSize):RemoteViews{
        val layout=when(kind){"quick"->if(size.horizontalActions)R.layout.widget_actions_wide else R.layout.widget_actions_grid;"parked"->R.layout.widget_parking_map;else->R.layout.widget_ryker_card}
        val v=RemoteViews(c.packageName,layout)
        v.setColorStateList(R.id.v2_root,"setBackgroundTintList",ColorStateList.valueOf(p.surface))
        fun text(id:Int,value:String,color:Int=p.text,sp:Int?=null){v.setTextViewText(id,value);v.setTextColor(id,color);if(sp!=null)v.setTextViewTextSize(id,TypedValue.COMPLEX_UNIT_SP,sp.toFloat())}
        fun visible(id:Int,show:Boolean){v.setViewVisibility(id,if(show)View.VISIBLE else View.GONE)}
        fun click(id:Int,page:String){v.setOnClickPendingIntent(id,RideActionActivity.pending(c,page))}
        fun dp(value:Int)=(value*c.resources.displayMetrics.density).toInt()
        fun icon(id:Int,drawable:Int,pixels:Int){v.setImageViewResource(id,drawable);v.setInt(id,"setColorFilter",p.accent);v.setViewLayoutWidth(id,pixels.toFloat(),TypedValue.COMPLEX_UNIT_DIP);v.setViewLayoutHeight(id,pixels.toFloat(),TypedValue.COMPLEX_UNIT_DIP)}
        val hidden=data.optJSONObject("widgetOptions")?.optBoolean("hideDetails")==true
        if(kind!="parked"){
            v.setViewPadding(R.id.v2_root,dp(size.padding),dp(size.padding),dp(size.padding),dp(size.padding))
            text(R.id.v2_title,if(kind=="quick")"Quick actions" else if(hidden)"My Ryker" else data.optJSONObject("vehicle")?.optString("nickname")?.ifBlank{"My Ryker"}?:"My Ryker",sp=size.title)
            text(R.id.v2_subtitle,if(kind=="quick")"READY FOR YOUR NEXT RIDE" else "RYKER CONNECT",p.accent,10)
            visible(R.id.v2_subtitle,size.height>=180)
            visible(R.id.v2_title,kind!="quick" || size.height>=160)
        }
        val tileIds=listOf(R.id.tile1,R.id.tile2,R.id.tile3,R.id.tile4)
        val iconIds=listOf(R.id.icon1,R.id.icon2,R.id.icon3,R.id.icon4)
        val labelIds=listOf(R.id.label1,R.id.label2,R.id.label3,R.id.label4)
        val hintIds=listOf(R.id.hint1,R.id.hint2,R.id.hint3,R.id.hint4)
        fun action(index:Int,label:String,hint:String,drawable:Int,page:String){
            val tile=tileIds[index]
            v.setColorStateList(tile,"setBackgroundTintList",ColorStateList.valueOf(p.tile))
            if(kind=="quick" && size.height<160)v.setViewPadding(tile,dp(2),dp(2),dp(2),dp(2))
            icon(iconIds[index],drawable,if(kind=="ryker" || size.height<160)18 else size.icon)
            text(labelIds[index],label,p.accent,if(size.roomy)15 else if(size.height<160)11 else 12)
            text(hintIds[index],hint,p.secondary,12);visible(hintIds[index],kind=="quick" && size.roomy)
            click(tile,page);v.setContentDescription(tile,label)
        }
        when(kind){
            "quick"->{
                action(0,if(size.height<160)"Fuel" else "Add fuel","Log your fill-up",R.drawable.widget_icon_fuel,"Add fuel")
                action(1,if(size.roomy)"Record mileage" else if(size.height<160)"ODO" else "Mileage","Update the odometer",R.drawable.widget_icon_mileage,"Add mileage")
                action(2,"Service","Record maintenance",R.drawable.widget_icon_service,"Add service")
                action(3,if(size.height<160)"Home" else "Go home","Open Google Maps",R.drawable.widget_icon_home,"Navigate home")
                click(R.id.v2_title,"Widgets")
            }
            "ryker"->{
                val profile=data.optJSONObject("vehicle")?:JSONObject()
                val records=listOf("fuel","maintenance").flatMap{key->data.optJSONArray(key)?.let{a->(0 until a.length()).map{a.getJSONObject(it)}}.orEmpty()}
                val latest=records.maxByOrNull{it.optLong("time")}
                val odo=records.maxOfOrNull{it.optDouble("odometerKm",0.0)}
                val alerts=ServiceNotifications.alerts()
                text(R.id.v2_odometer,if(hidden)"Details hidden" else odo?.let{RideUnits.distance(it*1000,RideState.preferences.value.imperial)}?:"No mileage",sp=if(size.roomy)32 else if(size.width<160)20 else 26)
                text(R.id.v2_recorded,if(hidden)"Tap to open" else latest?.let{"Recorded "+java.time.Instant.ofEpochMilli(it.optLong("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}?:"Record your first reading",p.secondary,10)
                val alert=alerts.firstOrNull()
                text(R.id.v2_service,if(hidden)"Private widget" else alert?.let{it.name+" · "+if(it.overdue)"Due" else "Next service"}?:"No service reminder",p.accent,if(size.roomy)17 else 12)
                text(R.id.v2_service_detail,if(hidden)"" else alert?.let{ServiceNotifications.describe(it)}?:"Set intervals in the app",p.secondary,12)
                visible(R.id.v2_service_detail,size.details && !hidden)
                text(R.id.v2_more,alerts.drop(1).take(2).joinToString("\n"){it.name+" · "+if(it.overdue)"Due" else "Scheduled"},p.secondary,13)
                visible(R.id.v2_more,size.roomy && !hidden && alerts.size>1)
                visible(R.id.v2_photo,size.photo && !hidden)
                if(size.photo && !hidden){
                    val encoded=profile.optString("photo")
                    val bitmap=if(encoded.isBlank())null else runCatching{val b=android.util.Base64.decode(encoded,0);BitmapFactory.decodeByteArray(b,0,b.size,BitmapFactory.Options().apply{inSampleSize=4})}.getOrNull()
                    v.setColorStateList(R.id.v2_photo,"setBackgroundTintList",ColorStateList.valueOf(p.tile))
                    v.setColorStateList(R.id.v2_photo,"setImageTintList",if(bitmap==null)ColorStateList.valueOf(p.accent) else null)
                    if(bitmap!=null)v.setImageViewBitmap(R.id.v2_photo,bitmap)
                    else v.setImageViewResource(R.id.v2_photo,R.drawable.widget_icon_garage)
                }
                action(0,"Vehicle","",R.drawable.widget_icon_garage,"Vehicle profile")
                action(1,"Service","",R.drawable.widget_icon_service,"Service reminders")
                visible(R.id.v2_footer,size.height>=190)
                click(R.id.v2_root,"Vehicle profile");click(R.id.v2_service,"Service reminders");click(R.id.v2_odometer,"Add mileage")
            }
            else->{
                val spot=data.optJSONObject("parking")
                val request=spot?.let{ParkingMapRequest(it,size.mapWidth,size.mapHeight,p.accent)}
                val bitmap=if(!hidden && !size.buttonOnly && request!=null)ParkingWidgetMaps.load(c,request) else null
                visible(R.id.v2_map,bitmap!=null)
                if(bitmap!=null)v.setImageViewBitmap(R.id.v2_map,bitmap)
                visible(R.id.v2_placeholder,bitmap==null)
                icon(R.id.v2_pin,if(hidden)R.drawable.widget_icon_lock else R.drawable.widget_icon_pin,if(size.buttonOnly)24 else 36)
                v.setViewPadding(R.id.v2_placeholder,dp(4),dp(4),dp(4),dp(4))
                text(R.id.v2_placeholder_text,when{size.buttonOnly->"Parked";hidden->"Parking hidden";spot==null->"No parking saved\nTap to set location";request!=null && ParkingWidgetMaps.failed(c,request)->"Map unavailable\nTap to open parking";else->"Loading parking map…"},p.text,if(size.buttonOnly)10 else 12)
                visible(R.id.v2_badge,!size.buttonOnly && !hidden && spot!=null)
                text(R.id.v2_badge,if(spot?.optBoolean("confirmed")==true)"P · Last Parked" else "P · Candidate",p.accent,if(size.width<160)10 else 12)
                v.setColorStateList(R.id.v2_badge,"setBackgroundTintList",ColorStateList.valueOf(p.surface))
                visible(R.id.v2_map_info,!size.buttonOnly && bitmap!=null)
                v.setColorStateList(R.id.v2_map_info,"setBackgroundTintList",ColorStateList.valueOf(p.surface))
                visible(R.id.v2_map_detail,size.details && !hidden)
                text(R.id.v2_map_detail,spot?.let{"Saved "+toolDate(it.optLong("time"))+"\nGPS ±"+it.optDouble("accuracy").toInt()+" m · Tap to open parking"}?:"",p.text,if(size.roomy)14 else 12)
                text(R.id.v2_map_attribution,"© OpenStreetMap · OpenMapTiles\nMapLibre · OpenFreeMap",p.secondary,8)
                click(R.id.v2_root,"Last Parked");click(R.id.v2_map,"Last Parked")
                v.setContentDescription(R.id.v2_root,if(hidden)"Last Parked. Location hidden. Open parking." else "Open Last Parked in RykerConnect")
            }
        }
        return v
    }
}
