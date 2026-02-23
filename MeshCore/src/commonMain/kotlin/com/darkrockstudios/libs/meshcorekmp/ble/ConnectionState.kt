package com.darkrockstudios.libs.meshcorekmp.ble

import kotlinx.serialization.Serializable

@Serializable
sealed class ConnectionState {
	@Serializable
	data object Disconnected : ConnectionState()

	@Serializable
	data object Connecting : ConnectionState()

	@Serializable
	data object Connected : ConnectionState()

	@Serializable
	data class Error(val message: String) : ConnectionState()
}
