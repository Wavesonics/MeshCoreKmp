package com.darkrockstudios.libs.meshcorekmp.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BleConnection {
	val connectionState: StateFlow<ConnectionState>
	val incomingData: Flow<ByteArray>
	suspend fun write(data: ByteArray)
	suspend fun requestMtu(mtu: Int): Int
	suspend fun disconnect()
	val deviceIdentifier: String
}
