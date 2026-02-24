package com.darkrockstudios.libs.meshcore.ble

import kotlinx.coroutines.flow.Flow

interface BleAdapter {
	fun scan(filter: ScanFilter = ScanFilter()): Flow<DiscoveredDevice>
	fun stopScan()
	suspend fun connect(device: DiscoveredDevice): BleConnection
	val isBluetoothEnabled: Boolean
}
