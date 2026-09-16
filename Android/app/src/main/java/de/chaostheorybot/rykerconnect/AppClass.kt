package de.chaostheorybot.rykerconnect

import android.app.Application
import de.chaostheorybot.rykerconnect.logic.BLEDeviceConnection
import kotlinx.coroutines.flow.MutableStateFlow

class RykerConnectApplication: Application() {
    companion object {
        val activeConnection = MutableStateFlow<BLEDeviceConnection?>(null)
        var phoneBatteryLevel: Int = -1
        var phoneBatteryCharging: Boolean = false
        var music = MusicInfo()
        
        // Neu für besseren Sync
        var networkSignal: Byte = -1
        var networkType: Byte = 0
        var intercomBattery: Byte = -1
        var volumePercent: Int = -1

        @Suppress("unused")
        val mainUnitConnected = MutableStateFlow<Boolean>(false)
    }

    override fun onCreate() {
        super.onCreate()
        de.chaostheorybot.rykerconnect.ride.RideState.init(this)
        de.chaostheorybot.rykerconnect.ride.AutoRide.init(this)
        de.chaostheorybot.rykerconnect.ride.OwnershipUpdates.init(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching { de.chaostheorybot.rykerconnect.ride.RykerWidgets.updateAll(this) }
    }
}

data class MusicInfo(
    val track: MutableStateFlow<String> = MutableStateFlow(""),
    val artist: MutableStateFlow<String> = MutableStateFlow(""),
    val position: MutableStateFlow<Int> = MutableStateFlow(0),
    val length: MutableStateFlow<Int> = MutableStateFlow(0),
    val state: MutableStateFlow<Boolean> = MutableStateFlow(false)
)
