package sample.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.DeviceScanner
import com.darkrockstudios.libs.meshcore.ble.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
	scanner: DeviceScanner,
	connectionScope: CoroutineScope,
	onDeviceConnected: (DeviceConnection) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val devices by scanner.discoveredDevices.collectAsState()
	var isScanning by remember { mutableStateOf(false) }
	var connectingDevice by remember { mutableStateOf<String?>(null) }
	var errorMessage by remember { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = {
			TopAppBar(title = { Text("MeshCore Scanner") })
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Button(
				onClick = {
					if (isScanning) {
						scanner.stopScan()
						isScanning = false
					} else {
						errorMessage = null
						scanner.startScan(scope = scope)
						isScanning = true
					}
				}
			) {
				Text(if (isScanning) "Stop Scan" else "Start Scan")
			}

			if (errorMessage != null) {
				Text(
					text = errorMessage!!,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(vertical = 8.dp),
				)
			}

			if (devices.isEmpty() && isScanning) {
				Box(
					modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
					contentAlignment = Alignment.Center,
				) {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						CircularProgressIndicator(modifier = Modifier.size(32.dp))
						Text(
							"Scanning for devices...",
							modifier = Modifier.padding(top = 8.dp),
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(devices, key = { it.identifier }) { device ->
					DeviceCard(
						device = device,
						isConnecting = connectingDevice == device.identifier,
						onClick = {
							if (connectingDevice != null) return@DeviceCard
							connectingDevice = device.identifier
							errorMessage = null
							scope.launch {
								try {
									val connection = scanner.connect(device, connectionScope)
									onDeviceConnected(connection)
								} catch (e: Exception) {
									errorMessage = "Connection failed: ${e.message}"
								} finally {
									connectingDevice = null
								}
							}
						}
					)
				}
			}
		}
	}
}

@Composable
private fun DeviceCard(
	device: DiscoveredDevice,
	isConnecting: Boolean,
	onClick: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = !isConnecting, onClick = onClick),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = device.name ?: "Unknown Device",
					style = MaterialTheme.typography.titleMedium,
				)
				Text(
					text = device.identifier,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			if (isConnecting) {
				CircularProgressIndicator(modifier = Modifier.size(24.dp))
			} else {
				Text(
					text = "${device.rssi} dBm",
					style = MaterialTheme.typography.bodyMedium,
				)
			}
		}
	}
}
