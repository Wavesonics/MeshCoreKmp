package com.darkrockstudios.libs.meshcorekmp.ble

import kotlinx.serialization.Serializable

@Serializable
data class ScanFilter(
	val serviceUuid: String = BleConstants.SERVICE_UUID,
	val namePrefix: String? = null,
)
