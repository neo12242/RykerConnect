package de.chaostheorybot.rykerconnect.logic

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.services.RykerDeviceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class ConnectionReport(
    val status: String = "Not connected",
    val attempt: Int = 0,
    val lastEvent: String = "No connection attempt yet",
    val lastTransfer: Long = 0,
    val transferKind: String = "None",
    val lastDisconnect: String = "None",
    val error: String? = null
)

/** UI diagnostics contain operation names and status only, never message contents. */
object ConnectionHealth {
    val report = MutableStateFlow(ConnectionReport())
    fun status(value: String, event: String = value) {
        report.update { it.copy(status = value, lastEvent = event) }
    }
    fun transfer(kind: String) {
        report.update { it.copy(lastTransfer = android.os.SystemClock.elapsedRealtime(), transferKind = kind, error = null) }
    }
    fun error(message: String) { report.update { it.copy(error = message, lastEvent = message) } }
}

/** Capped delays avoid continuous scanning while keeping short interruptions responsive. */
object RetrySchedule {
    fun delayMillis(attempt: Int): Long = when {
        attempt <= 1 -> 1_000
        attempt == 2 -> 2_000
        attempt == 3 -> 4_000
        attempt == 4 -> 8_000
        else -> 15_000
    }
}

object MainUnitControl {
    const val CONNECT = "ryker.CONNECT"
    const val DISCONNECT = "ryker.DISCONNECT"
    fun paused(context: Context) = context.getSharedPreferences("connection_control", Context.MODE_PRIVATE)
        .getBoolean("paused", false)
    fun setPaused(context: Context, value: Boolean) {
        context.getSharedPreferences("connection_control", Context.MODE_PRIVATE).edit().putBoolean("paused", value).apply()
    }
    fun request(context: Context, action: String? = null) {
        if (de.chaostheorybot.rykerconnect.BuildConfig.PHONE_EDITION) return
        if (action == CONNECT) setPaused(context, false)
        if (action == DISCONNECT) setPaused(context, true)
        if (!PermissionUtils.hasBluetoothConnect(context)) {
            ConnectionHealth.status("Bluetooth permission needed", "Grant Nearby devices permission in Android app settings")
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, RykerDeviceService::class.java).setAction(action))
        } catch (e: RuntimeException) {
            ConnectionHealth.error("Cannot start connection service: ${e.javaClass.simpleName}. Open the app and check Bluetooth permission.")
        }
    }
}
