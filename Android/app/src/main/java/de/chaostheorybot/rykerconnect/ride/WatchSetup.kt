package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.core.content.ContextCompat

object WatchSetup {
    fun enabled(c:Context)=c.getSharedPreferences("watch_control",Context.MODE_PRIVATE).getBoolean("enabled",false)
    fun ready(c:Context)=ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED &&
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
}

/** Optional background privileges are requested only from this user-opened setup screen. */
class WatchSetupActivity:Activity() {
    private lateinit var status:TextView
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val space=(20*resources.displayMetrics.density).toInt()
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(space,space*2,space,space)}
        fun text(value:String,size:Float=16f)=TextView(this).also{it.text=value;it.textSize=size;it.setPadding(0,space/2,0,space/2);content.addView(it)}
        fun button(label:String,action:()->Unit){content.addView(Button(this).apply{this.text=label;setAllCaps(false);setOnClickListener{action()}})}
        text("Watch controls",26f)
        text("Install the matching RykerConnect Watch edition on your watch and keep it paired through Galaxy Wearable. The phone records GPS and saves the ride. End Ride does not upload it.")
        content.addView(Switch(this).apply{
            text="Allow my watch to control rides";isChecked=WatchSetup.enabled(this@WatchSetupActivity)
            setOnCheckedChangeListener{_,checked->getSharedPreferences("watch_control",MODE_PRIVATE).edit().putBoolean("enabled",checked).apply();refresh()}
        })
        status=text("")
        text("To Start or recover a ride while the phone is locked, allow precise location → Allow all the time, then allow unrestricted battery use. GPS recording still stops during a deliberate pause. These settings are optional if you start on the phone.")
        button("1. Location permissions") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        button("2. Allow unrestricted battery") {
            try{startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))}
            catch(_:Exception){startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}
        }
        button("Watch downloads · GitHub releases") { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/neo12242/RykerConnect/releases"))) }
        button("Done"){finish()}
        setContentView(ScrollView(this).apply{addView(content)})
    }
    override fun onResume(){super.onResume();refresh()}
    private fun refresh(){if(::status.isInitialized)status.text=when{
        !WatchSetup.enabled(this)->"Watch control is off."
        WatchSetup.ready(this)->"Phone settings ready for locked-phone starts. Test once while parked."
        else->"Watch controls enabled. Locked-phone Start still needs the settings below."
    }}
}
