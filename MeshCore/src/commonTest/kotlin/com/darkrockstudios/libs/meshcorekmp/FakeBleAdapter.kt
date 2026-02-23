package com.darkrockstudios.libs.meshcorekmp

import com.darkrockstudios.libs.meshcorekmp.ble.BleAdapter
import com.darkrockstudios.libs.meshcorekmp.ble.BleConnection
import com.darkrockstudios.libs.meshcorekmp.ble.DiscoveredDevice
import com.darkrockstudios.libs.meshcorekmp.ble.ScanFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeBleAdapter(
	private val fakeConnection: FakeBleConnection = FakeBleConnection(),
) : BleAdapter {
	private var scanChannel = Channel<DiscoveredDevice>(Channel.UNLIMITED)
	var scanStarted = false
		private set
	var scanStopped = false
		private set
	override var isBluetoothEnabled: Boolean = true

	override fun scan(filter: ScanFilter): Flow<DiscoveredDevice> {
		scanStarted = true
		scanStopped = false
		scanChannel = Channel(Channel.UNLIMITED)
		return scanChannel.receiveAsFlow()
	}

	override fun stopScan() {
		scanStopped = true
		scanChannel.close()
	}

	override suspend fun connect(device: DiscoveredDevice): BleConnection {
		return fakeConnection
	}

	fun emitScanResult(device: DiscoveredDevice) {
		scanChannel.trySend(device)
	}
}
