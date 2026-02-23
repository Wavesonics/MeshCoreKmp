package sample.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.darkrockstudios.libs.meshcorekmp.DeviceConnection
import com.darkrockstudios.libs.meshcorekmp.DeviceScanner
import com.darkrockstudios.libs.meshcorekmp.ble.BleAdapter
import kotlinx.coroutines.launch
import sample.app.navigation.ConnectedRoute
import sample.app.navigation.Route
import sample.app.navigation.ScanRoute
import sample.app.screen.ConnectedScreen
import sample.app.screen.ScanScreen

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
							}
						)
					}
				}
			}
		)
	}
}
