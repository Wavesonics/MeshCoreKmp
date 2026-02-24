package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.ble.BleAdapter
import com.darkrockstudios.libs.meshcore.ble.DiscoveredDevice
import com.darkrockstudios.libs.meshcore.ble.ScanFilter
import com.darkrockstudios.libs.meshcore.protocol.CommandQueue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceScanner(
	private val bleAdapter: BleAdapter,
) {
	private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
	val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

	val isBluetoothEnabled: Boolean get() = bleAdapter.isBluetoothEnabled

	private var scanJob: Job? = null

	fun startScan(filter: ScanFilter = ScanFilter(), scope: CoroutineScope) {
		stopScan()
		_discoveredDevices.value = emptyList()
		Napier.d(tag = TAG) { "startScan() called" }
		val scanFlow = bleAdapter.scan(filter)
		scanJob = scope.launch {
			Napier.d(tag = TAG) { "Collecting scan flow" }
			scanFlow.collect { device ->
				Napier.d(tag = TAG) { "Received device '${device.name}' (${device.identifier})" }
				val current = _discoveredDevices.value
				val existingIndex = current.indexOfFirst { it.identifier == device.identifier }
				_discoveredDevices.value = if (existingIndex >= 0) {
					current.toMutableList().apply { set(existingIndex, device) }
				} else {
					current + device
				}
				Napier.d(tag = TAG) { "Total devices = ${_discoveredDevices.value.size}" }
			}
		}
	}

	fun stopScan() {
		scanJob?.cancel()
		scanJob = null
		bleAdapter.stopScan()
	}

	suspend fun connect(
		device: DiscoveredDevice,
		scope: CoroutineScope,
		config: ConnectionConfig = ConnectionConfig(),
	): DeviceConnection {
		stopScan()

		val bleConnection = bleAdapter.connect(device)
		bleConnection.requestMtu(config.requestedMtu)

		val commandQueue = CommandQueue(
			connection = bleConnection,
			scope = scope,
			defaultTimeout = config.commandTimeout,
		)

		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = commandQueue,
			scope = scope,
			config = config,
		)

		connection.initialize()
		return connection
	}

	companion object {
		private const val TAG = "MeshCoreBLE"
	}
}
