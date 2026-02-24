package com.darkrockstudios.libs.meshcore.ble

import dev.bluefalcon.ServiceFilter

internal expect fun createServiceFilters(serviceUuid: String): List<ServiceFilter>
