package com.darkrockstudios.libs.meshcorekmp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleAdapter(
	private val context: Context,
) : BleAdapter {

	private val bluetoothManager: BluetoothManager =
		context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

	private val bluetoothAdapter get() = bluetoothManager.adapter

	private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
	private var currentScanCallback: ScanCallback? = null

	override val isBluetoothEnabled: Boolean
		get() = bluetoothAdapter?.isEnabled == true

	override fun scan(filter: com.darkrockstudios.libs.meshcorekmp.ble.ScanFilter): Flow<DiscoveredDevice> =
		callbackFlow {
			val leScanner = bluetoothAdapter?.bluetoothLeScanner
				?: throw IllegalStateException("BluetoothLeScanner not available")

			val scanFilters = listOf(
				ScanFilter.Builder()
					.setServiceUuid(ParcelUuid(UUID.fromString(filter.serviceUuid)))
					.build()
			)

			val scanSettings = ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.build()

			val callback = object : ScanCallback() {
				override fun onScanResult(callbackType: Int, result: ScanResult) {
					val device = DiscoveredDevice(
						identifier = result.device.address,
						name = result.device.name,
						rssi = result.rssi,
					)
					trySend(device)
				}

				override fun onScanFailed(errorCode: Int) {
					close(IllegalStateException("BLE scan failed with error code: $errorCode"))
				}
			}

			scanner = leScanner
			currentScanCallback = callback
			leScanner.startScan(scanFilters, scanSettings, callback)

			awaitClose {
				leScanner.stopScan(callback)
				scanner = null
				currentScanCallback = null
			}
		}

	override fun stopScan() {
		currentScanCallback?.let { callback ->
			scanner?.stopScan(callback)
		}
		scanner = null
		currentScanCallback = null
	}

	override suspend fun connect(device: DiscoveredDevice): BleConnection {
		val bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.identifier)
		val connection = AndroidBleConnection(context, bluetoothDevice, device.identifier)
		connection.connectGatt()
		return connection
	}
}
