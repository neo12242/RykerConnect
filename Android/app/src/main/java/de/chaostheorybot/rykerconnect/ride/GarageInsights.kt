package de.chaostheorybot.rykerconnect.ride

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object GarageInsights {
    fun unusual(value:FuelEconomy)=value.litresPer100Km !in 2.0..25.0
    fun validateOdometer(records:List<JSONObject>,time:Long,km:Double) {
        val before=records.filter{it.getLong("time")<time}.maxOfOrNull{it.getDouble("odometerKm")}
        val after=records.filter{it.getLong("time")>time}.minOfOrNull{it.getDouble("odometerKm")}
        require((before==null || km+0.1>=before) && (after==null || km-0.1<=after)){"Odometer conflicts with an earlier or later entry. Check the date or correct that entry first."}
    }
    fun costPerKm(fuel:List<JSONObject>):Double? {
        val sorted=fuel.sortedBy{it.getDouble("odometerKm")}
        if(sorted.size<2 || sorted.drop(1).any{it.optBoolean("missedFill")})return null
        val km=sorted.last().getDouble("odometerKm")-sorted.first().getDouble("odometerKm")
        return if(km>0)sorted.drop(1).sumOf{it.optDouble("cost",0.0)}/km else null
    }
}

object ReceiptImages {
    fun load(context:Context,uri:Uri):String {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        val dimensions=context.contentResolver.openInputStream(uri)?:error("Image unavailable")
        dimensions.use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0){"Choose a readable photo"}
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1200)sample*=2
        val bitmap=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("Could not decode image")
        try {
            val out=java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,out)
            require(out.size()<=500_000){"Photo too large; choose a smaller image"}
            return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
        } finally {bitmap.recycle()}
    }
}
@Composable internal fun ReceiptPreview(encoded:String) {
    var show by remember(encoded){mutableStateOf(false)}
    if(encoded.isBlank())return
    TextButton(onClick={show=!show}){Text(if(show)"Hide receipt" else "View receipt photo")}
    if(show) {
        val bitmap=remember(encoded){runCatching{val bytes=Base64.decode(encoded,Base64.DEFAULT);BitmapFactory.decodeByteArray(bytes,0,bytes.size)}.getOrNull()}
        if(bitmap!=null)Image(bitmap.asImageBitmap(),"Attached receipt photo",Modifier.fillMaxWidth().heightIn(max=400.dp)) else Text("Receipt image unavailable")
    }
}
@Composable internal fun GarageCosts(fuel:List<JSONObject>,services:List<JSONObject>,imperial:Boolean) {
    fun money(n:Double)=String.format(Locale.US,"$%.2f",n)
    var expanded by remember { mutableStateOf(false) }
    ToolCard {
        Text("Fuel & ownership costs",style=MaterialTheme.typography.titleLarge)
        Text("Recorded fuel: ${money(fuel.sumOf{it.getDouble("cost")})} · Service: ${money(services.sumOf{it.optDouble("cost",0.0)})}")
        Text(GarageInsights.costPerKm(fuel)?.let{"Fuel cost per ${if(imperial)"mile" else "km"}: ${money(it*(if(imperial)1.609344 else 1.0))}"}?:"Cost per distance needs a complete fuel interval")
        Text("USD. Estimates use entered odometers, not GPS distance. The first fill establishes the cost interval; missing fills make it unavailable.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide trends" else "Monthly spending & MPG trends")}
        if(expanded) {
            val months=(fuel+services).groupBy{Instant.ofEpochMilli(it.getLong("time")).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)}.toSortedMap(compareByDescending{it})
            if(months.isEmpty())Text("Add records to see spending trends.")
            for((month,items) in months)Text("${month.toString().take(7)} · Fuel ${money(items.filter{it.has("litres")}.sumOf{it.getDouble("cost")})} · Service ${money(items.filter{!it.has("litres")}.sumOf{it.optDouble("cost",0.0)})}")
            Text("Full-tank economy trend",style=MaterialTheme.typography.titleMedium)
            val trend=fuel.sortedBy{it.getLong("time")}.mapNotNull{entry->GarageMath.forEntry(fuel,entry.getString("id"))?.takeIf{it.fullTank}?.let{entry to it}}
            if(trend.isEmpty())Text("Needs two full tanks with no missed fill-ups between them.")
            trend.takeLast(12).forEach{(entry,result)->Text("${toolDate(entry.getLong("time"))} · "+String.format(Locale.US,"%.1f %s",if(imperial)235.214583/result.litresPer100Km else result.litresPer100Km,if(imperial)"mpg" else "L/100 km"))}
        }
    }
}
