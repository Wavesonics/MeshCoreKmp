package com.darkrockstudios.libs.meshcorekmp.ble

import dev.bluefalcon.AdvertisementDataRetrievalKeys
import dev.bluefalcon.BlueFalcon
import dev.bluefalcon.BlueFalconDelegate
import dev.bluefalcon.BluetoothManagerState
import dev.bluefalcon.BluetoothPeripheral
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BlueFalconBleAdapter(
	private val blueFalcon: BlueFalcon,
) : BleAdapter {

	private val peripheralCache = mutableMapOf<String, BluetoothPeripheral>()

	override val isBluetoothEnabled: Boolean
		get() = blueFalcon.managerState.value == BluetoothManagerState.Ready

	override fun scan(filter: ScanFilter): Flow<DiscoveredDevice> = callbackFlow {
		peripheralCache.clear()

		Napier.d(tag = TAG) { "Starting scan with filter: serviceUuid='${filter.serviceUuid}', namePrefix='${filter.namePrefix}'" }

		val delegate = object : BlueFalconDelegate {
			override fun didDiscoverDevice(
				bluetoothPeripheral: BluetoothPeripheral,
				advertisementData: Map<AdvertisementDataRetrievalKeys, Any>,
			) {
				val name = bluetoothPeripheral.name
				Napier.d(tag = TAG) { "didDiscoverDevice: name='$name', uuid='${bluetoothPeripheral.uuid}', rssi=${bluetoothPeripheral.rssi}" }
				Napier.d(tag = TAG) { "  advertisementData keys: ${advertisementData.keys}" }

				if (filter.namePrefix != null && (name == null || !name.startsWith(filter.namePrefix))) {
					Napier.d(tag = TAG) { "  FILTERED OUT by namePrefix '${filter.namePrefix}'" }
					return
				}

				peripheralCache[bluetoothPeripheral.uuid] = bluetoothPeripheral

				val device = DiscoveredDevice(
					identifier = bluetoothPeripheral.uuid,
					name = name,
					rssi = bluetoothPeripheral.rssi?.toInt() ?: 0,
				)
				Napier.d(tag = TAG) { "  Emitting device: $device" }
				trySend(device)
			}
		}

		blueFalcon.delegates.add(delegate)

		val serviceFilters = if (filter.serviceUuid.isNotBlank()) {
			createServiceFilters(filter.serviceUuid)
		} else {
			emptyList()
		}
		Napier.d(tag = TAG) { "Calling blueFalcon.scan() with ${serviceFilters.size} service filter(s)" }
		blueFalcon.scan(serviceFilters)

		awaitClose {
			blueFalcon.stopScanning()
			blueFalcon.delegates.remove(delegate)
		}
	}

	override fun stopScan() {
		blueFalcon.stopScanning()
	}

	override suspend fun connect(device: DiscoveredDevice): BleConnection {
		val peripheral = peripheralCache[device.identifier]
			?: blueFalcon.retrievePeripheral(device.identifier)
			?: throw MeshCoreBleException(
				"Peripheral not found for identifier: ${device.identifier}"
			)

		val connection = BlueFalconBleConnection(
			blueFalcon = blueFalcon,
			peripheral = peripheral,
			deviceIdentifier = device.identifier,
		)
		connection.connectAndSetup()
		return connection
	}

	companion object {
		private const val TAG = "MeshCoreBLE"
	}
}
