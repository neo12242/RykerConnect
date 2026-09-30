package de.chaostheorybot.rykerconnect.ride

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

object OwnershipReport {
    fun pdf(period:OwnershipPeriod,rows:List<OwnershipExpense>,imperial:Boolean):ByteArray {
        val document=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=11f}
        var pageNumber=0;var page:PdfDocument.Page?=null;var y=0f
        fun nextPage(){page?.let{document.finishPage(it)};page=document.startPage(PdfDocument.PageInfo.Builder(612,792,++pageNumber).create());y=44f;page!!.canvas.drawText("RykerConnect · Ownership · Page $pageNumber",36f,y,paint);y+=26f}
        fun line(value:String){
            for(paragraph in value.split('\n')) {
                var remaining=paragraph.ifEmpty{" "}
                while(remaining.isNotEmpty()) {
                    if(page==null || y>748)nextPage()
                    val count=paint.breakText(remaining,true,540f,null).coerceAtLeast(1)
                    page!!.canvas.drawText(remaining.take(count),36f,y,paint);y+=16f;remaining=remaining.drop(count)
                }
            }
        }
        try {
            line(period.name);line("${period.from} through ${period.to} · USD · Entered records only")
            line("Total recorded: ${OwnershipMath.money(OwnershipMath.total(rows))}")
            for(category in listOf("Fuel","Maintenance","Modifications"))line("$category: ${OwnershipMath.money(OwnershipMath.total(rows.filter{it.category==category}))}")
            val unknown=rows.count{it.cents==null};if(unknown>0)line("$unknown costs were not entered and are excluded from totals.")
            line("Cost per ${if(imperial)"mile" else "km"}: "+(OwnershipMath.perKm(rows,period)?.let{java.lang.String.format(java.util.Locale.US,"$%.2f",it*(if(imperial)1.609344 else 1.0))}?:"Unavailable; requires confirmed period mileage and costs."))
            line("Purchase dates determine spending. Installation does not add a second expense.")
            line("")
            if(rows.isEmpty())line("No spending records in this period.")
            rows.forEach{r->
                line("${r.date} · ${r.category} · ${r.cents?.let(OwnershipMath::money)?:"Cost not entered"}")
                line(r.title)
                r.odometerKm?.let{line(java.lang.String.format(java.util.Locale.US,"Odometer: %.1f %s",it/(if(imperial)1.609344 else 1.0),if(imperial)"mi" else "km"))}
                if(r.notes.isNotBlank())line(r.notes)
                line("")
            }
            page?.let{document.finishPage(it)}
            return ByteArrayOutputStream().use{document.writeTo(it);it.toByteArray()}
        } finally {document.close()}
    }
}
