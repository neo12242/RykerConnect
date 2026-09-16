package de.chaostheorybot.rykerconnect.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.ride.*
import kotlinx.coroutines.*

class NotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, error ->
        RideState.notificationStatus.value = "Notification processing failed: ${error.javaClass.simpleName}"
    })
    private val deduper = NotificationDeduper()
    private var navigationKey: String? = null
    private var refresh: Job? = null
    override fun onListenerConnected() {
        super.onListenerConnected()
        RideState.listenerConnected.value = true
        refresh?.cancel()
        refresh = scope.launch {
            while (isActive) {
                try {
                    val maps = activeNotifications.orEmpty().firstOrNull { it.packageName == "com.google.android.apps.maps" && it.notification.category == "navigation" }
                    if (maps != null) updateNavigation(maps) else { navigationKey = null; RideState.live(NavigationFrame()) }
                } catch (_: SecurityException) { RideState.listenerConnected.value = false; RideState.live(NavigationFrame()) }
                delay(5_000)
            }
        }
    }
    override fun onListenerDisconnected() {
        refresh?.cancel(); RideState.listenerConnected.value = false; RideState.live(NavigationFrame())
        super.onListenerDisconnected()
    }
    private fun value(n: Notification, key: String) = n.extras?.getCharSequence(key)?.toString().orEmpty()
    private fun updateNavigation(sbn: StatusBarNotification) {
        navigationKey = sbn.key
        val n = sbn.notification
        RideState.live(MapsParser.parse(value(n, Notification.EXTRA_TITLE), value(n, Notification.EXTRA_TEXT), value(n, Notification.EXTRA_SUB_TEXT), sbn.postTime))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if (sbn.packageName == "com.google.android.apps.maps" && sbn.notification.category == "navigation") { updateNavigation(sbn); return }
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        RideState.observedApps.value = RideState.observedApps.value + (sbn.packageName to appName)
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.isOngoing) return
        val title = value(n, Notification.EXTRA_TITLE)
        val text = value(n, Notification.EXTRA_BIG_TEXT).ifBlank { value(n, Notification.EXTRA_TEXT) }
        if (!deduper.accept(sbn.key, title + "\u0000" + text, System.currentTimeMillis())) { RideState.notificationStatus.value = "Duplicate suppressed"; return }
        val options = RideState.preferences.value
        if (!options.allowAll && sbn.packageName !in options.allowed) { RideState.notificationStatus.value = "Blocked by app allowlist"; return }
        if (RideState.priorityActive()) { RideState.notificationStatus.value = "Ordinary notification suppressed during navigation"; return }
        scope.launch {
            val store = RykerConnectStore(applicationContext)
            if (!store.isNotificationsEnabled()) { RideState.notificationStatus.value = "Notification forwarding is disabled"; return@launch }
            val body = if (options.hideBody) "Message body hidden" else text
            // Privacy filtering precedes both persistence and BLE forwarding.
            store.saveNotification(n.category.orEmpty(), title.take(120), body.take(240), sbn.packageName, appName)
            val connection = RykerConnectApplication.activeConnection.value
            if (connection?.isConnected?.value == true) {
                connection.writeNotification(NotificationText.bounded(appName, 60), NotificationText.bounded(title, 120), NotificationText.bounded(body, 280))
                RideState.notificationStatus.value = "Notification queued for display"
            } else RideState.notificationStatus.value = "Display disconnected; notification not queued"
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        deduper.remove(sbn.key)
        if (sbn.key == navigationKey) { navigationKey = null; RideState.live(NavigationFrame()) }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
