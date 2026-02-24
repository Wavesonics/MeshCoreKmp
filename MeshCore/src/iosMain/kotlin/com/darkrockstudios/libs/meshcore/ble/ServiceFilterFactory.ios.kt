package com.darkrockstudios.libs.meshcore.ble

import dev.bluefalcon.ServiceFilter
import platform.CoreBluetooth.CBUUID

internal actual fun createServiceFilters(serviceUuid: String): List<ServiceFilter> {
	return listOf(
		ServiceFilter(
			serviceUuids = listOf(CBUUID.UUIDWithString(serviceUuid))
		)
	)
}
