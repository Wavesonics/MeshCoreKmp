package sample.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.DeviceScanner
import com.darkrockstudios.libs.meshcore.ble.BleAdapter
import kotlinx.coroutines.launch
import sample.app.navigation.*
import sample.app.screen.*

@Composable
fun App(bleAdapter: BleAdapter) {
	MaterialTheme {
		val backStack: SnapshotStateList<Route> = remember {
			listOf<Route>(ScanRoute).toMutableStateList()
		}
		val scope = rememberCoroutineScope()
		val scanner = remember { DeviceScanner(bleAdapter) }
		var activeConnection by remember { mutableStateOf<DeviceConnection?>(null) }

		NavDisplay(
			backStack = backStack,
			onBack = { backStack.removeLastOrNull() },
			entryProvider = entryProvider {
				entry<ScanRoute> {
					ScanScreen(
						scanner = scanner,
						connectionScope = scope,
						onDeviceConnected = { conn ->
							activeConnection = conn
							backStack.add(ConnectedRoute(conn.deviceIdentifier))
						}
					)
				}
				entry<ConnectedRoute> {
					activeConnection?.let { conn ->
						ConnectedScreen(
							connection = conn,
							onDisconnected = {
								scope.launch {
									try {
										activeConnection?.disconnect()
									} catch (_: Exception) {
									}
									activeConnection = null
								}
								backStack.clear()
								backStack.add(ScanRoute)
							},
							onChannelsClick = {
								backStack.add(ChannelsRoute)
							},
							onContactsClick = {
								backStack.add(ContactsRoute)
							}
						)
					}
				}
				entry<ChannelsRoute> {
					activeConnection?.let { conn ->
						ChannelsScreen(
							connection = conn,
							onBack = { backStack.removeLastOrNull() },
							onChannelSelected = { channel ->
								backStack.add(ChannelRoute(channel.index, channel.name))
							}
						)
					}
				}
				entry<ChannelRoute> { route ->
					activeConnection?.let { conn ->
						ChannelScreen(
							channelIndex = route.channelIndex,
							channelName = route.channelName,
							connection = conn,
							onBack = { backStack.removeLastOrNull() },
						)
					}
				}
				entry<ContactsRoute> {
					activeConnection?.let { conn ->
						ContactsScreen(
							connection = conn,
							onBack = { backStack.removeLastOrNull() },
							onContactSelected = { contact ->
								backStack.add(
									DirectMessageRoute(
										contactName = contact.name,
										publicKeyHex = contact.publicKey.toHexString(),
									)
								)
							}
						)
					}
				}
				entry<DirectMessageRoute> { route ->
					activeConnection?.let { conn ->
						DirectMessageScreen(
							contactName = route.contactName,
							publicKeyHex = route.publicKeyHex,
							connection = conn,
							onBack = { backStack.removeLastOrNull() },
						)
					}
				}
			}
		)
	}
}
