package com.darkrockstudios.libs.meshcorekmp.ble

import dev.bluefalcon.ServiceFilter

internal expect fun createServiceFilters(serviceUuid: String): List<ServiceFilter>
