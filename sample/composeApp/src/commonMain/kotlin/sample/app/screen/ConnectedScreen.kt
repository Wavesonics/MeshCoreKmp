package sample.app.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.ble.ConnectionState
import com.darkrockstudios.libs.meshcore.model.BatteryInfo
import com.darkrockstudios.libs.meshcore.model.Contact
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

	var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
	var isFetchingContacts by remember { mutableStateOf(false) }
	var contactsError by remember { mutableStateOf<String?>(null) }
	var expanded by remember { mutableStateOf(false) }
	var selectedContact by remember { mutableStateOf<Contact?>(null) }

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

			// Contacts Button
			Button(
				onClick = {
					isFetchingContacts = true
					contactsError = null
					scope.launch {
						try {
							contacts = connection.getContacts()
						} catch (e: Exception) {
							contactsError = "Failed: ${e.message}"
						} finally {
							isFetchingContacts = false
						}
					}
				},
				enabled = !isFetchingContacts,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(if (isFetchingContacts) "Fetching Contacts..." else "Get Contacts")
			}

			if (contactsError != null) {
				Text(
					text = contactsError!!,
					color = MaterialTheme.colorScheme.error,
				)
			}

			if (contacts.isNotEmpty()) {
				ExposedDropdownMenuBox(
					expanded = expanded,
					onExpandedChange = { expanded = !expanded },
					modifier = Modifier.fillMaxWidth()
				) {
					OutlinedTextField(
						value = selectedContact?.name ?: "Select Contact",
						onValueChange = {},
						readOnly = true,
						label = { Text("Direct Contacts") },
						trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
						colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
						modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
							.fillMaxWidth()
					)

					ExposedDropdownMenu(
						expanded = expanded,
						onDismissRequest = { expanded = false }
					) {
						contacts.forEach { contact ->
							DropdownMenuItem(
								text = {
									Column {
										Text(contact.name)
										Text(
											contact.publicKeyPrefix,
											style = MaterialTheme.typography.labelSmall
										)
									}
								},
								onClick = {
									selectedContact = contact
									expanded = false
								}
							)
						}
					}
				}
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
						Text("Level: ${battery.levelPercent}% (${battery.milliVolts}mV)")
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
