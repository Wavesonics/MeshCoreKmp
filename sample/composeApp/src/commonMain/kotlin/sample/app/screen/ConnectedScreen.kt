package sample.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcorekmp.DeviceConnection
import com.darkrockstudios.libs.meshcorekmp.ble.ConnectionState
import com.darkrockstudios.libs.meshcorekmp.model.BatteryInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedScreen(
	connection: DeviceConnection,
	onDisconnected: () -> Unit,
	onChannelsClick: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val connectionState by connection.connectionState.collectAsState()
	val deviceInfo by connection.deviceInfo.collectAsState()
	var batteryInfo by remember { mutableStateOf<BatteryInfo?>(null) }
	var batteryError by remember { mutableStateOf<String?>(null) }
	var isFetchingBattery by remember { mutableStateOf(false) }

	// Watch for unexpected disconnection
	LaunchedEffect(connectionState) {
		if (connectionState is ConnectionState.Disconnected) {
			onDisconnected()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Connected") },
				actions = {
					IconButton(onClick = {
						scope.launch {
							try {
								connection.disconnect()
							} catch (_: Exception) {
							}
							onDisconnected()
						}
					}) {
						Text("X", style = MaterialTheme.typography.titleMedium)
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			// Device Info Card
			deviceInfo?.let { info ->
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text("Device Info", style = MaterialTheme.typography.titleMedium)
						Text("Model: ${info.model}")
						Text("Firmware: ${info.firmwareBuild}")
						Text("Version: ${info.version}")
						Text("Max Channels: ${info.maxChannels}")
						Text("Max Contacts: ${info.maxContacts}")
					}
				}
			}

			// Get Battery Button
			Button(
				onClick = {
					isFetchingBattery = true
					batteryError = null
					scope.launch {
						try {
							batteryInfo = connection.getBattery()
						} catch (e: Exception) {
							batteryError = "Failed: ${e.message}"
						} finally {
							isFetchingBattery = false
						}
					}
				},
				enabled = !isFetchingBattery,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(if (isFetchingBattery) "Fetching..." else "Get Battery")
			}

			// Channels Button
			Button(
				onClick = onChannelsClick,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Channels")
			}

			if (batteryError != null) {
				Text(
					text = batteryError!!,
					color = MaterialTheme.colorScheme.error,
				)
			}

			// Battery Info Card
			batteryInfo?.let { battery ->
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text("Battery", style = MaterialTheme.typography.titleMedium)
						Text("Level: ${battery.levelPercent}%")
						battery.usedStorageKb?.let { used ->
							battery.totalStorageKb?.let { total ->
								Text("Storage: ${used}KB / ${total}KB")
							}
						}
					}
				}
			}

			// Connection state
			Text(
				text = "State: ${connectionState::class.simpleName}",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
