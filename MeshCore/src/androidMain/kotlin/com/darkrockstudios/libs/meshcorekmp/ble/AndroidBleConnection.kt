package com.darkrockstudios.libs.meshcorekmp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
class AndroidBleConnection(
	private val context: Context,
	private val device: BluetoothDevice,
	override val deviceIdentifier: String,
) : BleConnection {

	private var gatt: BluetoothGatt? = null
	private var rxCharacteristic: BluetoothGattCharacteristic? = null

	private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
	override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

	private val _incomingData = Channel<ByteArray>(Channel.UNLIMITED)
	override val incomingData: Flow<ByteArray> = _incomingData.receiveAsFlow()

	private var connectContinuation: CancellableContinuation<Unit>? = null
	private var writeContinuation: CancellableContinuation<Unit>? = null
	private var mtuContinuation: CancellableContinuation<Int>? = null

	companion object {
		private val SERVICE_UUID = UUID.fromString(BleConstants.SERVICE_UUID)
		private val RX_UUID = UUID.fromString(BleConstants.RX_CHARACTERISTIC_UUID)
		private val TX_UUID = UUID.fromString(BleConstants.TX_CHARACTERISTIC_UUID)
		private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
	}

	private val gattCallback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> {
					gatt.discoverServices()
				}

				BluetoothProfile.STATE_DISCONNECTED -> {
					_connectionState.value = ConnectionState.Disconnected
					connectContinuation?.let {
						connectContinuation = null
						it.resumeWithException(
							MeshCoreBleException("Connection failed with status: $status")
						)
					}
				}
			}
		}

		override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
			if (status != BluetoothGatt.GATT_SUCCESS) {
				val error = "Service discovery failed with status: $status"
				_connectionState.value = ConnectionState.Error(error)
				connectContinuation?.let {
					connectContinuation = null
					it.resumeWithException(MeshCoreBleException(error))
				}
				return
			}

			val service = gatt.getService(SERVICE_UUID)
			if (service == null) {
				val error = "NUS service not found"
				_connectionState.value = ConnectionState.Error(error)
				connectContinuation?.let {
					connectContinuation = null
					it.resumeWithException(MeshCoreBleException(error))
				}
				return
			}

			rxCharacteristic = service.getCharacteristic(RX_UUID)
			val txCharacteristic = service.getCharacteristic(TX_UUID)

			if (rxCharacteristic == null || txCharacteristic == null) {
				val error = "NUS characteristics not found"
				_connectionState.value = ConnectionState.Error(error)
				connectContinuation?.let {
					connectContinuation = null
					it.resumeWithException(MeshCoreBleException(error))
				}
				return
			}

			// Enable notifications on TX characteristic (data from device)
			gatt.setCharacteristicNotification(txCharacteristic, true)
			val descriptor = txCharacteristic.getDescriptor(CCCD_UUID)
			if (descriptor != null) {
				gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
			} else {
				// No CCCD descriptor, but notifications may still work
				onSetupComplete()
			}
		}

		override fun onDescriptorWrite(
			gatt: BluetoothGatt,
			descriptor: BluetoothGattDescriptor,
			status: Int,
		) {
			if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CCCD_UUID) {
				onSetupComplete()
			} else {
				val error = "Failed to enable notifications, status: $status"
				_connectionState.value = ConnectionState.Error(error)
				connectContinuation?.let {
					connectContinuation = null
					it.resumeWithException(MeshCoreBleException(error))
				}
			}
		}

		override fun onCharacteristicChanged(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			value: ByteArray,
		) {
			if (characteristic.uuid == TX_UUID) {
				_incomingData.trySend(value)
			}
		}

		override fun onCharacteristicWrite(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			status: Int,
		) {
			writeContinuation?.let {
				writeContinuation = null
				if (status == BluetoothGatt.GATT_SUCCESS) {
					it.resume(Unit)
				} else {
					it.resumeWithException(
						MeshCoreBleException("Write failed with status: $status")
					)
				}
			}
		}

		override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
			mtuContinuation?.let {
				mtuContinuation = null
				if (status == BluetoothGatt.GATT_SUCCESS) {
					it.resume(mtu)
				} else {
					it.resumeWithException(
						MeshCoreBleException("MTU request failed with status: $status")
					)
				}
			}
		}
	}

	private fun onSetupComplete() {
		_connectionState.value = ConnectionState.Connected
		connectContinuation?.let {
			connectContinuation = null
			it.resume(Unit)
		}
	}

	suspend fun connectGatt() {
		_connectionState.value = ConnectionState.Connecting
		suspendCancellableCoroutine { cont ->
			connectContinuation = cont
			gatt = device.connectGatt(
				context,
				false,
				gattCallback,
				BluetoothDevice.TRANSPORT_LE,
			)
			cont.invokeOnCancellation {
				connectContinuation = null
				gatt?.disconnect()
				gatt?.close()
			}
		}
	}

	override suspend fun write(data: ByteArray) {
		val characteristic = rxCharacteristic
			?: throw MeshCoreBleException("Not connected - RX characteristic unavailable")
		val currentGatt = gatt
			?: throw MeshCoreBleException("Not connected - GATT unavailable")

		suspendCancellableCoroutine { cont ->
			writeContinuation = cont
			val status = currentGatt.writeCharacteristic(
				characteristic,
				data,
				BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
			)
			if (status != BluetoothGatt.GATT_SUCCESS) {
				writeContinuation = null
				cont.resumeWithException(
					MeshCoreBleException("writeCharacteristic returned error: $status")
				)
			}
			cont.invokeOnCancellation { writeContinuation = null }
		}
	}

	override suspend fun requestMtu(mtu: Int): Int {
		val currentGatt = gatt
			?: throw MeshCoreBleException("Not connected - GATT unavailable")

		return suspendCancellableCoroutine { cont ->
			mtuContinuation = cont
			if (!currentGatt.requestMtu(mtu)) {
				mtuContinuation = null
				cont.resumeWithException(MeshCoreBleException("requestMtu call failed"))
			}
			cont.invokeOnCancellation { mtuContinuation = null }
		}
	}

	override suspend fun disconnect() {
		gatt?.disconnect()
		gatt?.close()
		gatt = null
		rxCharacteristic = null
		_connectionState.value = ConnectionState.Disconnected
		_incomingData.close()
	}
}

class MeshCoreBleException(message: String) : Exception(message)
