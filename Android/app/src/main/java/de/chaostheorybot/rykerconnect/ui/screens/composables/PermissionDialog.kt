package de.chaostheorybot.rykerconnect.ui.screens.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PermissionDialog(
    permissionTextProvider: PermissionTextProvider,
    isPermanentlyDeclined: Boolean,
    onDismiss: () -> Unit,
    onOkClick: () -> Unit,
    modifier: Modifier = Modifier
){
    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {  Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOkClick() }
        ) {
            Text(if (isPermanentlyDeclined) "Open settings" else "Grant permission")
        }
             },
        title = {
            Text(text = "Permission required")
                },
        text = {
            Text(text = permissionTextProvider.getDescription(isPermanentlyDeclined = isPermanentlyDeclined))
                },
        modifier = modifier
        )
}

interface PermissionTextProvider{
    fun getDescription(isPermanentlyDeclined: Boolean): String
}

class BluetoothPermissionProvider: PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if(isPermanentlyDeclined){
            "Bluetooth permission was permanently denied. " +
                "Open app settings and grant permission manually " +
                "so the app can connect to your RykerConnect device."
        }else{
            "RykerConnect needs Bluetooth to connect to your helmet display " +
                "and send notifications, music information, and battery status."
        }
    }
}

class BluetoothScanPermissionProvider: PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if(isPermanentlyDeclined){
            "Bluetooth scanning permission was permanently denied. " +
                "Open app settings and grant permission manually."
        }else{
            "RykerConnect needs to scan for nearby Bluetooth devices " +
                "to find your helmet display and establish the initial connection."
        }
    }
}

class LocationPermissionProvider: PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if(isPermanentlyDeclined){
            "Location permission was permanently denied. " +
                "Open app settings and grant permission manually. " +
                "Without location access, nearby Bluetooth devices cannot be discovered."
        }else{
            "Android requires location access to discover nearby " +
                "Bluetooth devices. When automatic recording is enabled, connecting the Ryker starts a route stored on this phone."
        }
    }
}

class NotificationPermissionProvider: PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if(isPermanentlyDeclined){
            "Notification permission was permanently denied. " +
                "Open app settings and grant permission manually " +
                "so the background service can work correctly."
        }else{
            "RykerConnect needs notification permission to keep the background service " +
                "running reliably and inform you about the connection status."
        }
    }
}

class PhoneStatePermissionProvider: PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if(isPermanentlyDeclined){
            "Phone status permission was permanently denied. " +
                "Open app settings and grant permission manually."
        }else{
            "RykerConnect uses phone status to show the current network connection (4G/5G) " +
                "and signal strength on your helmet display."
        }
    }
}
