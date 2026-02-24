package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.ble.BleConnection
import com.darkrockstudios.libs.meshcore.ble.ConnectionState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeBleConnection : BleConnection {
	val writtenData = mutableListOf<ByteArray>()
	private val _incomingData = Channel<ByteArray>(Channel.UNLIMITED)
	private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)

	override val connectionState: StateFlow<ConnectionState> = _connectionState
	override val incomingData: Flow<ByteArray> = _incomingData.receiveAsFlow()
	override val deviceIdentifier: String = "fake-device-001"

	var negotiatedMtu: Int = 23

	override suspend fun write(data: ByteArray) {
		writtenData.add(data.copyOf())
	}

	override suspend fun requestMtu(mtu: Int): Int {
		negotiatedMtu = mtu
		return mtu
	}

	override suspend fun disconnect() {
		_connectionState.value = ConnectionState.Disconnected
	}

	fun simulateResponse(data: ByteArray) {
		_incomingData.trySend(data)
	}

	fun setConnectionState(state: ConnectionState) {
		_connectionState.value = state
	}
}
