package com.darkrockstudios.libs.meshcorekmp.ble

import android.os.ParcelUuid
import dev.bluefalcon.ServiceFilter
import java.util.UUID

internal actual fun createServiceFilters(serviceUuid: String): List<ServiceFilter> {
	return listOf(
		ServiceFilter(
			serviceUuids = listOf(ParcelUuid(UUID.fromString(serviceUuid)))
		)
	)
}
