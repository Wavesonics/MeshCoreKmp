package com.darkrockstudios.libs.meshcorekmp.ble

import dev.bluefalcon.ServiceFilter
import platform.CoreBluetooth.CBUUID

internal actual fun createServiceFilters(serviceUuid: String): List<ServiceFilter> {
	return listOf(
		ServiceFilter(
			serviceUuids = listOf(CBUUID.UUIDWithString(serviceUuid))
		)
	)
}
