package de.chaostheorybot.rykerconnect.ui.screens.settingsscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit, store: RykerConnectStore) {

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val dynamicColor by store.getDynamicColorToken.collectAsState(initial = false)
    val showDebugCard by store.getShowDebugCardToken.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "App Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = 16.dp, end = 16.dp)
        ) {
            item {
                SettingsSection(title = "Appearance") {
                    SettingsSwitch(
                        title = "Material You",
                        subtitle = "Use colors from the system wallpaper. " +
                                "Changing this clears the named theme and follows the phone light/dark mode.",
                        checked = dynamicColor,
                        onCheckedChange = { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().remove("theme").putString("mode","Fixed").apply(); scope.launch { store.saveDynamicColor(it) } }
                    )
                }
            }

            item {
                SettingsSection(title = "Developer options") {
                    SettingsSwitch(
                        title = "Debug card",
                        subtitle = "Show raw media and notification data, plus the option to " +
                                "remove companion device pairing, under Device tools.",
                        checked = showDebugCard,
                        onCheckedChange = { scope.launch { store.saveShowDebugCard(it) } }
                    )
                }
            }

            item { Spacer(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding() + 16.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
