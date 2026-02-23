package com.darkrockstudios.libs.meshcorekmp.ble

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredDevice(
	val identifier: String,
	val name: String?,
	val rssi: Int,
)
