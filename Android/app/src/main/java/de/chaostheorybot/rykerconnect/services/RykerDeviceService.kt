package de.chaostheorybot.rykerconnect.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.R
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import de.chaostheorybot.rykerconnect.data.BATTERY_POLL_DEFAULT_SECONDS
import de.chaostheorybot.rykerconnect.data.MusicService
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.data.setupChargeStateFilter
import de.chaostheorybot.rykerconnect.data.setupSpotifyFilter
import de.chaostheorybot.rykerconnect.logic.BLEDeviceConnection
import de.chaostheorybot.rykerconnect.logic.BluetoothLogic.getActiveIntercom
import de.chaostheorybot.rykerconnect.logic.BluetoothLogic.getBatteryLevel
import de.chaostheorybot.rykerconnect.logic.BluetoothLogic.getDevice
import de.chaostheorybot.rykerconnect.logic.BluetoothLogic.waitForBLEConnection
import de.chaostheorybot.rykerconnect.logic.PermissionUtils
import de.chaostheorybot.rykerconnect.logic.pushPhoneBattery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import de.chaostheorybot.rykerconnect.logic.ConnectionHealth
import de.chaostheorybot.rykerconnect.logic.MainUnitControl
import de.chaostheorybot.rykerconnect.logic.RetrySchedule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration.Companion.seconds

class RykerDeviceService : CompanionDeviceService() {

    private val chargeStateReceiver: BroadcastReceiver = ChargeStateReceiver()
    private val spotifyReceiver: BroadcastReceiver = SpotifyReceiver()
    private var batteryUpdateJob: Job? = null
    private var watchdogJob: Job? = null
    private var chargeModeJob: Job? = null

    /** Aktuell registrierter Ladezustands-Modus, null wenn kein Receiver haengt. */
    private var registeredChargeMode: Boolean? = null
    private var youTubeMusicManager: YouTubeMusicManager? = null
    private lateinit var networkTypeMonitor: NetworkTypeMonitor
    private var volumeMonitor: VolumeMonitor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Job for delayed cleanup – allows cancellation if device reappears quickly. */
    private var cleanupJob: Job? = null
    private var reconnectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("RykerDeviceService", "Service created")
        networkTypeMonitor = NetworkTypeMonitor(this)
        try {
            networkTypeMonitor.startMonitoring()
            startServiceForeground()
        } catch (e: SecurityException) {
            ConnectionHealth.status("Bluetooth permission needed", "Permission changed; open the app and check permissions")
            stopSelf()
            return
        }
        ensureConnectionLoop()
    }

    private fun startServiceForeground() {
        val channelId = "ryker_connect_service"
        // minSdk 31: Notification-Channels gibt es immer, kein SDK_INT-Guard noetig.
        val channel = NotificationChannel(channelId, "Ryker Connect Active", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ryker Connect")
            .setContentText("Monitoring device and music...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureConnectionLoop()
        return START_STICKY
    }

    @Deprecated("Legacy")
    override fun onDeviceAppeared(address: String) { initDeviceConnection(address) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Deprecated("Legacy override", replaceWith = ReplaceWith(""))
    override fun onDeviceAppeared(info: AssociationInfo) {
        info.deviceMacAddress?.toString()?.let { initDeviceConnection(it) }
    }

    // Presence is a hint only. The connection loop owns the GATT lifecycle.
    private fun initDeviceConnection(address: String) {
        ensureConnectionLoop()
    }

    @Synchronized
    private fun ensureConnectionLoop() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            val store = RykerConnectStore(this@RykerDeviceService)
            var selectedAddress: String? = null
            var attempt = 0
            var nextAttemptAt = 0L
            var lastHealthCheck = 0L
            var healthFailures = 0
            while (isActive) {
                try {
                    val address = store.getBLEMACToken.first().orEmpty()
                    val allowed = PermissionUtils.hasBluetoothConnect(this@RykerDeviceService)
                    val enabled = allowed && getSystemService(android.bluetooth.BluetoothManager::class.java).adapter?.isEnabled == true
                    val paused = MainUnitControl.paused(this@RykerDeviceService)
                    if (address.isBlank() || !allowed || !enabled || paused) {
                        if (RykerConnectApplication.activeConnection.value != null) cleanup()
                        ConnectionHealth.status(when {
                            paused -> "Disconnected by you"
                            address.isBlank() -> "Select a device"
                            !allowed -> "Bluetooth permission needed"
                            else -> "Bluetooth is off"
                        })
                        attempt = 0
                        nextAttemptAt = 0
                        delay(1_000)
                        continue
                    }
                    if (selectedAddress != address) {
                        cleanup()
                        selectedAddress = address
                        attempt = 0
                        nextAttemptAt = 0
                    }
                    val existing = RykerConnectApplication.activeConnection.value
                    if (existing?.isConnected?.value == true) {
                        // Reads verify the data path even if Android retains a stale link.
                        if (android.os.SystemClock.elapsedRealtime() - lastHealthCheck >= 15_000) {
                            lastHealthCheck = android.os.SystemClock.elapsedRealtime()
                            if (existing.readFirmwareVersion() == null) healthFailures++ else healthFailures = 0
                            if (healthFailures >= 2) {
                                ConnectionHealth.error("Device stopped answering; reconnecting")
                                cleanup()
                            } else if (healthFailures == 0) {
                                ConnectionHealth.status("Connected", "Device responding")
                            }
                        }
                        delay(1_000)
                        continue
                    }
                    if (existing != null) cleanup()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now < nextAttemptAt) {
                        ConnectionHealth.status("Retrying", "Next attempt in ${(nextAttemptAt - now + 999) / 1000}s")
                        delay(500)
                        continue
                    }
                    attempt++
                    ConnectionHealth.report.update { it.copy(status = "Connecting", attempt = attempt, lastEvent = "Opening saved device", error = null) }
                    val device = getDevice(application, address)
                    if (device == null) {
                        ConnectionHealth.error("Saved device address is invalid")
                        nextAttemptAt = now + 15_000
                        delay(1_000)
                        continue
                    }
                    val connection = try {
                        BLEDeviceConnection(application, device)
                    } catch (e: SecurityException) {
                        ConnectionHealth.error("Bluetooth permission was revoked")
                        delay(1_000)
                        continue
                    }
                    RykerConnectApplication.activeConnection.value = connection
                    connection.connect()
                    val ready = withTimeoutOrNull(12_000) {
                        while (!connection.isConnected.value) {
                            if (MainUnitControl.paused(this@RykerDeviceService) || store.getBLEMACToken.first() != address) break
                            delay(200)
                        }
                        connection.isConnected.value
                    } == true
                    if (ready && !MainUnitControl.paused(this@RykerDeviceService) && store.getBLEMACToken.first() == address && connection.readFirmwareVersion() != null) {
                        attempt = 0
                        healthFailures = 0
                        lastHealthCheck = android.os.SystemClock.elapsedRealtime()
                        ConnectionHealth.status("Connected", "Services discovered and device read succeeded")
                        store.saveBLEAppear(true)
                        observeChargeStateMode(store)
                        pushPhoneBattery(this@RykerDeviceService)
                        connection.syncAll()
                        if (store.isMusicEnabled()) withContext(Dispatchers.Main) { setupMusicManager(store) }
                        if (store.isVolumeEnabled()) withContext(Dispatchers.Main) {
                            volumeMonitor?.stopMonitoring()
                            volumeMonitor = VolumeMonitor(this@RykerDeviceService).also { it.startMonitoring() }
                        }
                        startIntercomBatteryUpdates(store)
                        startWatchdog()
                    } else {
                        ConnectionHealth.error("Connection or device read timed out; saved pairing retained")
                        cleanup()
                        nextAttemptAt = android.os.SystemClock.elapsedRealtime() + RetrySchedule.delayMillis(attempt)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ConnectionHealth.error("Connection error: ${e.javaClass.simpleName}")
                    cleanup()
                    delay(5_000)
                }
            }
        }
    }

    /**
     * BLE Watchdog: The ESP disconnects after 10 min without any characteristic write.
     * This job checks every 30 s and sends a lightweight time-sync ping if no write
     * has occurred for 8 min (480 000 ms).  Under normal operation the regular
     * data forwarding (battery, media, network, volume) keeps the connection alive,
     * so this should rarely fire.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(60.seconds) // prüfe alle 60 s
                val conn = RykerConnectApplication.activeConnection.value ?: continue
                if (!conn.isConnected.value) continue
                val elapsed = android.os.SystemClock.elapsedRealtime() - conn.lastWriteTimestamp
                if (elapsed >= 480_000) { // 8 minutes
                    conn.writeTime()
                    Log.d("RykerDeviceService", "BLE watchdog ping sent (idle ${elapsed / 1000}s)")
                }
            }
        }
    }

    /**
     * Haengt den [ChargeStateReceiver] an den eingestellten Modus und registriert ihn neu,
     * sobald der Nutzer im Service-Menue umschaltet.
     */
    private fun observeChargeStateMode(store: RykerConnectStore) {
        chargeModeJob?.cancel()
        chargeModeJob = serviceScope.launch {
            store.getUseBatteryChangedToken.collect { useBatteryChanged ->
                if (registeredChargeMode == useBatteryChanged) return@collect
                unregisterChargeStateReceiver()
                try {
                    // NOT_EXPORTED: die Akku-Broadcasts sind geschuetzt, nur das System sendet sie.
                    ContextCompat.registerReceiver(
                        this@RykerDeviceService,
                        chargeStateReceiver,
                        setupChargeStateFilter(useBatteryChanged),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    registeredChargeMode = useBatteryChanged
                    Log.d("RykerDeviceService", "ChargeStateReceiver registriert, changed-Modus: $useBatteryChanged")
                } catch (e: Exception) {
                    Log.e("RykerDeviceService", "ChargeStateReceiver failed: ${e.message}")
                }
            }
        }
    }

    private fun unregisterChargeStateReceiver() {
        if (registeredChargeMode == null) return
        try { unregisterReceiver(chargeStateReceiver) } catch (_: Exception) {}
        registeredChargeMode = null
    }

    private fun setupMusicManager(store: RykerConnectStore) {
        serviceScope.launch {
            val musicPlayer = store.getMusicPlayer()
            withContext(Dispatchers.Main) {
                if (musicPlayer?.id == MusicService.SPOTIFY.id) {
                    try {
                        ContextCompat.registerReceiver(this@RykerDeviceService, spotifyReceiver, setupSpotifyFilter(), ContextCompat.RECEIVER_EXPORTED)
                    } catch (_: Exception) {}
                } else {
                    if (youTubeMusicManager == null) {
                        youTubeMusicManager = YouTubeMusicManager(this@RykerDeviceService)
                    }
                    youTubeMusicManager?.setupYoutubeController()
                }
            }
        }
    }

    /**
     * Minutentakt fuer Telefonakku-Pegel und Intercom-Akku.
     *
     * Der Ladezustand selbst laeuft nicht hier, sondern sofort ueber den
     * [ChargeStateReceiver]. collectLatest startet die Schleife neu, sobald sich die
     * Intercom-Auswahl aendert.
     */
    private fun startIntercomBatteryUpdates(store: RykerConnectStore) {
        batteryUpdateJob?.cancel()
        batteryUpdateJob = serviceScope.launch {
            combine(
                store.getIntercomMacsToken,
                store.getUseBatteryChangedToken,
                store.getBatteryPollSecondsToken
            ) { macs, useBatteryChanged, pollSeconds ->
                Triple(macs, useBatteryChanged, pollSeconds)
            }.collectLatest { (macs, useBatteryChanged, pollSeconds) ->
                // Im Changed-Modus liefert der Receiver den Pegel; die Schleife laeuft dann
                // nur noch fuer das Intercom und bleibt beim Standardtakt.
                val interval = if (useBatteryChanged) {
                    BATTERY_POLL_DEFAULT_SECONDS.seconds
                } else {
                    pollSeconds.seconds
                }

                // currentCoroutineContext(), nicht isActive: letzteres bindet hier an die
                // aeussere Coroutine und bliebe true, wenn collectLatest neu startet.
                while (currentCoroutineContext().isActive) {
                    // Pegel nur pollen, wenn kein Changed-Receiver ihn ohnehin liefert.
                    if (!useBatteryChanged) pushPhoneBattery(this@RykerDeviceService)

                    // Ohne ausgewaehltes oder verbundenes Intercom gar nicht erst auf die
                    // BLE-Verbindung warten - das sparte sonst nichts und kostet 4 s Timeout.
                    val intercom = if (macs.isEmpty()) null else getActiveIntercom(application, macs)
                    if (intercom != null && waitForBLEConnection()) {
                        try {
                            val level = getBatteryLevel(intercom)
                            if (level != -1) {
                                Log.d("RykerDeviceService", "Intercom battery (${intercom.address}): $level")
                                RykerConnectApplication.intercomBattery = level.toByte()
                                store.saveIntercomBattery(level)
                                RykerConnectApplication.activeConnection.value?.writeIntercomBattery(level.toByte())
                            }
                        } catch (e: Exception) { Log.e("RykerDeviceService", "Intercom update error: ${e.message}") }
                    }
                    delay(interval)
                }
            }
        }
    }

    @Deprecated("Legacy")
    override fun onDeviceDisappeared(address: String) { scheduleCleanup("onDeviceDisappeared($address)") }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Deprecated("Legacy override", replaceWith = ReplaceWith(""))
    override fun onDeviceDisappeared(info: AssociationInfo) { scheduleCleanup("onDeviceDisappeared(${info.deviceMacAddress})") }

    /** Presence callbacks cannot override the service-owned connection lifecycle. */
    private fun scheduleCleanup(tag: String) {
        // Do not tear down a healthy GATT link because a presence callback was missed.
        ensureConnectionLoop()
    }

    private fun cleanup() {
        serviceScope.launch { RykerConnectStore(this@RykerDeviceService).saveBLEAppear(false) }
        chargeModeJob?.cancel()
        unregisterChargeStateReceiver()
        try { unregisterReceiver(spotifyReceiver) } catch (_: Exception) {}
        youTubeMusicManager?.destroy()
        youTubeMusicManager = null
        volumeMonitor?.stopMonitoring()
        volumeMonitor = null
        batteryUpdateJob?.cancel()
        watchdogJob?.cancel()
        RykerConnectApplication.activeConnection.value?.disconnect()
        RykerConnectApplication.activeConnection.value = null
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectJob?.cancel()
        cleanup()
        serviceScope.cancel()
        try { networkTypeMonitor.stopMonitoring() } catch (_: Exception) {}
    }
}
