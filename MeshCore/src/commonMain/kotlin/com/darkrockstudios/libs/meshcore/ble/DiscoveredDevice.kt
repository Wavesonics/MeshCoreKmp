package com.darkrockstudios.libs.meshcore.ble

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredDevice(
	val identifier: String,
	val name: String?,
	val rssi: Int,
)
