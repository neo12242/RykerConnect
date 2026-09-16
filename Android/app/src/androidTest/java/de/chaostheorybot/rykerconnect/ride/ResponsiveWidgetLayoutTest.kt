package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ResponsiveWidgetLayoutTest {
    @Test fun remoteViewsInflateAndFitSmallWideAndLargeWidgets(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        val data=SoftwareStore.snapshot()
        val palette=WidgetPalette(0xff182126.toInt(),0xff004f58.toInt(),0xff4fd8eb.toInt(),0xffeeeeee.toInt(),0xffcccccc.toInt())
        var failure:Throwable?=null
        instrumentation.runOnMainSync {
            try{
                val dimensions=listOf(WidgetSize(130f,140f),WidgetSize(180f,220f),WidgetSize(330f,150f),WidgetSize(330f,360f),WidgetSize(360f,500f))
                for(kind in listOf("quick","ryker","parked")){
                    for(size in if(kind=="parked")listOf(WidgetSize(56f,56f))+dimensions else dimensions){
                        val host=FrameLayout(c)
                        val view=ResponsiveWidgets.render(c,kind,data,palette,size).apply(c,host) as ViewGroup
                        host.addView(view)
                        val w=(size.width*c.resources.displayMetrics.density).toInt()
                        val h=(size.height*c.resources.displayMetrics.density).toInt()
                        host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                        host.layout(0,0,w,h)
                        if(kind=="quick")for(id in listOf(R.id.label1,R.id.label2,R.id.label3,R.id.label4)){
                            val label=view.findViewById<TextView>(id);val bounds=Rect()
                            label.getDrawingRect(bounds);view.offsetDescendantRectToMyCoords(label,bounds)
                            assertTrue("$kind $size label outside widget: $bounds",bounds.top>=0 && bounds.bottom<=h && bounds.left>=0 && bounds.right<=w)
                            assertTrue("Empty label height",label.height>0)
                            assertTrue("Label text clipped for $size",label.layout.height<=label.height)
                        }
                        if(kind=="parked" && size.buttonOnly)assertEquals(View.GONE,view.findViewById<View>(R.id.v2_map_info).visibility)
                        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);host.draw(Canvas(image))
                        File(c.cacheDir,"widget-$kind-${size.width.toInt()}x${size.height.toInt()}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
                    }
                }
                val privateData=JSONObject(data.toString()).put("widgetOptions",JSONObject().put("hideDetails",true))
                val privateView=ResponsiveWidgets.render(c,"parked",privateData,palette,WidgetSize(200f,200f)).apply(c,FrameLayout(c))
                assertEquals(View.GONE,privateView.findViewById<View>(R.id.v2_map).visibility)
            }catch(t:Throwable){failure=t}
        }
        failure?.let{throw it}
    }
}
