package de.chaostheorybot.rykerconnect.ui.screens.homescreen.cards

import android.Manifest
import android.bluetooth.BluetoothManager
import de.chaostheorybot.rykerconnect.BuildConfig
import android.content.pm.PackageManager
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import de.chaostheorybot.rykerconnect.RykerConnectApplication
import de.chaostheorybot.rykerconnect.logic.ConnectionHealth
import de.chaostheorybot.rykerconnect.logic.MainUnitControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectionPanel(isAssociated: Boolean, select: () -> Unit, forget: () -> Unit, showDiagnostics: Boolean = true) {
    val context = LocalContext.current
    val report by ConnectionHealth.report.collectAsState()
    val connection by RykerConnectApplication.activeConnection.collectAsState()
    var diagnostics by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = SystemClock.elapsedRealtime() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(report.status, style = MaterialTheme.typography.titleMedium,
            color = if (report.status == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (showDiagnostics) Text(report.lastEvent, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isAssociated) {
                Button(onClick = select) { Text("Select Device") }
            } else {
                Button(onClick = { MainUnitControl.request(context, MainUnitControl.CONNECT) },
                    enabled = report.status != "Connected" && report.status != "Connecting" && report.status != "Discovering services") { Text("Connect") }
                OutlinedButton(onClick = { MainUnitControl.request(context, MainUnitControl.DISCONNECT) },
                    enabled = report.status != "Disconnected by you") { Text("Disconnect") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDiagnostics) TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Hide diagnostics" else "Diagnostics") }
            if (BuildConfig.DEMO_FEATURES) {
                TextButton(onClick = { preview = true }) { Text("OLED preview") }
            }
        }
        if (diagnostics) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val bluetooth = permission && context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
            Text("Bluetooth: ${if (bluetooth) "on" else "off or unavailable"} · Permission: ${if (permission) "granted" else "missing"}", style = MaterialTheme.typography.bodySmall)
            Text("Attempt: ${report.attempt} · Last disconnect: ${report.lastDisconnect}", style = MaterialTheme.typography.bodySmall)
            Text(if (report.lastTransfer == 0L) "No confirmed data transfer yet" else
                "${report.transferKind} · ${(now - report.lastTransfer).coerceAtLeast(0) / 1000}s ago", style = MaterialTheme.typography.bodySmall)
            Text("Status describes app data readiness, not Android input-device pairing.", style = MaterialTheme.typography.bodySmall)
            report.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(enabled = report.status == "Connected" && !testing, onClick = {
                testing = true
                testResult = null
                scope.launch {
                    try { testResult = if (connection?.testDisplay() == true) "Test sent and acknowledged. Check the connected display." else "Display test failed." }
                    finally { testing = false }
                }
            }) { Text(if (testing) "Testing…" else "Test display") }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (isAssociated) TextButton(onClick = { confirmForget = true }) { Text("Forget Device", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false },
        title = { Text("Forget main unit?") },
        text = { Text("Remove this main unit from the app and request removal of its Bluetooth pairing. You will need to select and pair it again. Use Disconnect to keep pairing.") },
        confirmButton = { TextButton(onClick = { confirmForget = false; forget() }) { Text("Forget Device") } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } })
    if (BuildConfig.DEMO_FEATURES && preview) SimulatorPreview { preview = false }
}

@Composable
private fun SimulatorPreview(close: () -> Unit) {
    val context = LocalContext.current
    val webView = remember { WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                request.url.host != "127.0.0.1" || request.url.port != 8876
        }
        loadUrl("http://127.0.0.1:8876/")
    } }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Simulator OLED preview", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = close) { Text("Close") }
                }
                Text("Local simulation · no physical ESP. If unavailable, run Start RykerConnect Demo.cmd on your PC.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { webView.reload() }) { Text("Reload preview") }
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
