package com.darkrockstudios.libs.meshcore.ble

import dev.bluefalcon.BlueFalcon
import dev.bluefalcon.BlueFalconDelegate
import dev.bluefalcon.BluetoothCharacteristic
import dev.bluefalcon.BluetoothPeripheral
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class BlueFalconBleConnection internal constructor(
	private val blueFalcon: BlueFalcon,
	private val peripheral: BluetoothPeripheral,
	override val deviceIdentifier: String,
) : BleConnection {

	private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
	override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

	private val _incomingData = Channel<ByteArray>(Channel.UNLIMITED)
	override val incomingData: Flow<ByteArray> = _incomingData.receiveAsFlow()

	private var rxCharacteristic: BluetoothCharacteristic? = null
	private var txCharacteristic: BluetoothCharacteristic? = null

	private var connectContinuation: CancellableContinuation<Unit>? = null
	private var writeContinuation: CancellableContinuation<Unit>? = null
	private var mtuContinuation: CancellableContinuation<Int>? = null

	private enum class SetupPhase {
		IDLE,
		WAITING_CONNECT,
		WAITING_CHARACTERISTICS,
		WAITING_NOTIFY_ENABLED,
		COMPLETE,
		FAILED,
	}

	private var setupPhase = SetupPhase.IDLE

	private val delegate = object : BlueFalconDelegate {
		override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			// With autoDiscoverAllServicesAndCharacteristics = true,
			// Blue Falcon will trigger service + characteristic discovery automatically.
			setupPhase = SetupPhase.WAITING_CHARACTERISTICS
		}

		override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			_connectionState.value = ConnectionState.Disconnected

			connectContinuation?.let {
				connectContinuation = null
				it.resumeWithException(MeshCoreBleException("Disconnected during setup"))
			}
		}

		override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			if (setupPhase != SetupPhase.WAITING_CHARACTERISTICS) return

			val nusServiceUuid = BleConstants.SERVICE_UUID.lowercase()
			val rxUuid = BleConstants.RX_CHARACTERISTIC_UUID.lowercase()
			val txUuid = BleConstants.TX_CHARACTERISTIC_UUID.lowercase()

			var foundRx: BluetoothCharacteristic? = null
			var foundTx: BluetoothCharacteristic? = null

			for (service in bluetoothPeripheral.services.values) {
				if (service.uuid.toString().lowercase() == nusServiceUuid) {
					for (characteristic in service.characteristics) {
						val charUuid = characteristic.uuid.toString().lowercase()
						if (charUuid == rxUuid) foundRx = characteristic
						if (charUuid == txUuid) foundTx = characteristic
					}
					break
				}
			}

			if (foundRx == null || foundTx == null) {
				val error = "NUS service or characteristics not found"
				_connectionState.value = ConnectionState.Error(error)
				setupPhase = SetupPhase.FAILED
				connectContinuation?.let {
					connectContinuation = null
					it.resumeWithException(MeshCoreBleException(error))
				}
				return
			}

			rxCharacteristic = foundRx
			txCharacteristic = foundTx

			setupPhase = SetupPhase.WAITING_NOTIFY_ENABLED
			blueFalcon.notifyCharacteristic(bluetoothPeripheral, foundTx, true)
		}

		override fun didUpdateNotificationStateFor(
			bluetoothPeripheral: BluetoothPeripheral,
			bluetoothCharacteristic: BluetoothCharacteristic,
		) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			if (setupPhase != SetupPhase.WAITING_NOTIFY_ENABLED) return

			setupPhase = SetupPhase.COMPLETE
			_connectionState.value = ConnectionState.Connected
			connectContinuation?.let {
				connectContinuation = null
				it.resume(Unit)
			}
		}

		// Note: typo in Blue Falcon's API — "didCharacteristcValueChanged" (missing 'i')
		override fun didCharacteristcValueChanged(
			bluetoothPeripheral: BluetoothPeripheral,
			bluetoothCharacteristic: BluetoothCharacteristic,
		) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			val txUuid = BleConstants.TX_CHARACTERISTIC_UUID.lowercase()
			if (bluetoothCharacteristic.uuid.toString().lowercase() == txUuid) {
				val value = bluetoothCharacteristic.value
				if (value != null) {
					_incomingData.trySend(value)
				}
			}
		}

		override fun didWriteCharacteristic(
			bluetoothPeripheral: BluetoothPeripheral,
			bluetoothCharacteristic: BluetoothCharacteristic,
			success: Boolean,
		) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			writeContinuation?.let {
				writeContinuation = null
				if (success) {
					it.resume(Unit)
				} else {
					it.resumeWithException(MeshCoreBleException("Write failed"))
				}
			}
		}

		override fun didUpdateMTU(
			bluetoothPeripheral: BluetoothPeripheral,
			status: Int,
		) {
			if (bluetoothPeripheral.uuid != peripheral.uuid) return
			mtuContinuation?.let {
				mtuContinuation = null
				val newMtu = bluetoothPeripheral.mtuSize ?: 23
				if (status == 0) { // GATT_SUCCESS
					it.resume(newMtu)
				} else {
					it.resumeWithException(
						MeshCoreBleException("MTU request failed with status: $status")
					)
				}
			}
		}
	}

	internal suspend fun connectAndSetup() {
		_connectionState.value = ConnectionState.Connecting
		blueFalcon.delegates.add(delegate)

		try {
			suspendCancellableCoroutine<Unit> { cont ->
				connectContinuation = cont
				setupPhase = SetupPhase.WAITING_CONNECT
				blueFalcon.connect(peripheral, autoConnect = false)

				cont.invokeOnCancellation {
					connectContinuation = null
					setupPhase = SetupPhase.IDLE
					blueFalcon.disconnect(peripheral)
					blueFalcon.delegates.remove(delegate)
				}
			}
		} catch (e: Exception) {
			blueFalcon.delegates.remove(delegate)
			_connectionState.value = ConnectionState.Disconnected
			throw e
		}
	}

	override suspend fun write(data: ByteArray) {
		val rx = rxCharacteristic
			?: throw MeshCoreBleException("Not connected - RX characteristic unavailable")

		suspendCancellableCoroutine<Unit> { cont ->
			writeContinuation = cont
			blueFalcon.writeCharacteristicWithoutEncoding(peripheral, rx, data, writeType = null)
			cont.invokeOnCancellation { writeContinuation = null }
		}
	}

	override suspend fun requestMtu(mtu: Int): Int {
		return suspendCancellableCoroutine { cont ->
			mtuContinuation = cont
			blueFalcon.changeMTU(peripheral, mtu)
			cont.invokeOnCancellation { mtuContinuation = null }
		}
	}

	override suspend fun disconnect() {
		blueFalcon.disconnect(peripheral)
		blueFalcon.delegates.remove(delegate)
		rxCharacteristic = null
		txCharacteristic = null
		_connectionState.value = ConnectionState.Disconnected
		_incomingData.close()
	}
}
