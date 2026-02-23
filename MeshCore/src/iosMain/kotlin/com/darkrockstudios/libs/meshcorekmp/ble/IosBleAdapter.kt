package com.darkrockstudios.libs.meshcorekmp.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class IosBleAdapter : BleAdapter {
	override val isBluetoothEnabled: Boolean
		get() = throw NotImplementedError("iOS BLE not yet implemented")

	override fun scan(filter: ScanFilter): Flow<DiscoveredDevice> {
		throw NotImplementedError("iOS BLE not yet implemented")
	}

	override fun stopScan() {
		throw NotImplementedError("iOS BLE not yet implemented")
	}

	override suspend fun connect(device: DiscoveredDevice): BleConnection {
		throw NotImplementedError("iOS BLE not yet implemented")
	}
}
