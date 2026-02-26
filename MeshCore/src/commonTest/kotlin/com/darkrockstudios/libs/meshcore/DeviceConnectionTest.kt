@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.model.ReceivedBinaryResponse
import com.darkrockstudios.libs.meshcore.model.ReceivedRawData
import com.darkrockstudios.libs.meshcore.protocol.CommandQueue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeviceConnectionTest {

	private fun createSelfInfoResponse(): ByteArray {
		val data = ByteArray(63) // 58 min + 5 for device name "Test\0"
		data[0] = 0x05 // PACKET_SELF_INFO
		data[1] = 0x01 // adv_type
		data[2] = 22   // tx_power
		data[3] = 22   // max_tx_power
		// bytes 4-35: public key (32 zeros)
		// bytes 36-43: lat/lon (zeros)
		// byte 44: multi_acks
		// byte 45: adv_loc_policy
		// byte 46: telemetry_mode
		// byte 47: manual_add_contacts
		// bytes 48-55: radio freq/bw (zeros)
		// byte 56: radio_sf
		// byte 57: radio_cr
		"Test".encodeToByteArray().copyInto(data, 58) // device name
		return data
	}

	private fun createDeviceInfoResponse(): ByteArray {
		val data = ByteArray(80)
		data[0] = 0x0D
		data[1] = 0x03 // firmware v3
		data[2] = 0x04 // max contacts raw=4, actual=8
		data[3] = 0x08 // max channels=8
		data[4] = 0xD2.toByte(); data[5] = 0x04; data[6] = 0x00; data[7] = 0x00 // PIN=1234
		"1.0.0".encodeToByteArray().copyInto(data, 8)
		"TestRadio".encodeToByteArray().copyInto(data, 20)
		"v1.0".encodeToByteArray().copyInto(data, 60)
		return data
	}

	private fun createChannelInfoResponse(index: Int, name: String): ByteArray {
		val data = ByteArray(34)
		data[0] = 0x12
		data[1] = index.toByte()
		name.encodeToByteArray().copyInto(data, 2, 0, minOf(name.length, 32))
		return data
	}

	@Test
	fun getBattery_returnsBatteryInfo() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Simulate initialization responses
		launch {
			// Wait for APP_START
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse()) // SelfInfo
			kotlinx.coroutines.yield()

			// Wait for DEVICE_QUERY
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}

		connection.initialize()

		assertNotNull(connection.deviceInfo.value)
		assertEquals("TestRadio", connection.deviceInfo.value?.model)

		// Now test getBattery
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			// Battery = 85%, no storage info
			bleConnection.simulateResponse(byteArrayOf(0x0C, 0x55, 0x00))
		}

		val battery = connection.getBattery()
		assertEquals(85, battery.levelPercent)
	}

	@Test
	fun getChannel_returnsChannel() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Initialize
		launch {
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse())
			kotlinx.coroutines.yield()
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}
		connection.initialize()

		// Test getChannel
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createChannelInfoResponse(1, "General"))
		}

		val channel = connection.getChannel(1)
		assertEquals(1, channel.index)
		assertEquals("General", channel.name)
	}

	@Test
	fun pollNextMessage_noMessages() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Initialize
		launch {
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse())
			kotlinx.coroutines.yield()
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}
		connection.initialize()

		// Poll message - no messages
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(byteArrayOf(0x0A)) // NO_MORE_MSGS
		}

		val message = connection.pollNextMessage()
		assertEquals(null, message)
	}

	@Test
	fun getCoreStats_returnsStats() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Initialize
		launch {
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse())
			kotlinx.coroutines.yield()
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}
		connection.initialize()

		// Get core stats
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			val statsData = ByteArray(11)
			statsData[0] = 0x18
			statsData[1] = 0x00
			// battery = 3700 mV
			statsData[2] = 0x74; statsData[3] = 0x0E
			// uptime = 3600
			statsData[4] = 0x10; statsData[5] = 0x0E; statsData[6] = 0x00; statsData[7] = 0x00
			// errors = 0
			statsData[8] = 0x00; statsData[9] = 0x00
			// queue = 2
			statsData[10] = 0x02
			bleConnection.simulateResponse(statsData)
		}

		val stats = connection.getCoreStats()
		assertEquals(3700, stats.batteryMillivolts)
		assertEquals(3600L, stats.uptimeSeconds)
		assertEquals(0, stats.errors)
		assertEquals(2, stats.queueLength)
	}

	@Test
	fun sendRawData_sendsAndReceivesOk() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Initialize
		launch {
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse())
			kotlinx.coroutines.yield()
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}
		connection.initialize()

		// Test sendRawData
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(byteArrayOf(0x00)) // OK
		}

		connection.sendRawData(payload = byteArrayOf(0x01, 0x02, 0x03))

		// Verify the command was sent with correct format
		val cmd = bleConnection.writtenData[2]
		assertEquals(0x19.toByte(), cmd[0]) // CMD_SEND_RAW_DATA
		assertEquals(0x00.toByte(), cmd[1]) // empty path
	}

	@Test
	fun sendBinaryRequest_sendsAndReceivesConfirmation() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		// Initialize
		launch {
			while (bleConnection.writtenData.isEmpty()) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createSelfInfoResponse())
			kotlinx.coroutines.yield()
			while (bleConnection.writtenData.size < 2) { kotlinx.coroutines.yield() }
			bleConnection.simulateResponse(createDeviceInfoResponse())
		}
		connection.initialize()

		// Test sendBinaryRequest
		launch {
			while (bleConnection.writtenData.size < 3) { kotlinx.coroutines.yield() }
			// RESP_CODE_SENT (0x06) with type, tag, timeout
			val resp = ByteArray(10)
			resp[0] = 0x06
			resp[1] = 0x01 // message type
			resp[2] = 0x39; resp[3] = 0x30; resp[4] = 0x00; resp[5] = 0x00 // expected ack
			resp[6] = 0x1E; resp[7] = 0x00; resp[8] = 0x00; resp[9] = 0x00 // timeout = 30s
			bleConnection.simulateResponse(resp)
		}

		val publicKey = ByteArray(32) { it.toByte() }
		val confirmation = connection.sendBinaryRequest(publicKey, byteArrayOf(0xFF.toByte()))
		assertEquals(1, confirmation.messageType)
		assertEquals(30, confirmation.suggestedTimeoutSeconds)

		// Verify the command was sent with correct format
		val cmd = bleConnection.writtenData[2]
		assertEquals(0x32.toByte(), cmd[0]) // CMD_SEND_BINARY_REQ
	}

	@Test
	fun incomingRawData_flowReceivesPushEvents() = runTest(UnconfinedTestDispatcher()) {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		val collected = mutableListOf<ReceivedRawData>()
		val collectJob = backgroundScope.launch {
			connection.incomingRawData.collect { collected.add(it) }
		}

		// Simulate a raw data push
		bleConnection.simulateResponse(byteArrayOf(
			0x84.toByte(), 40, (-80).toByte(), 0xFF.toByte(), 0xAA.toByte(), 0xBB.toByte(),
		))

		assertEquals(1, collected.size)
		assertEquals(10.0f, collected[0].snr)
		assertEquals(-80, collected[0].rssi)
		assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), collected[0].payload)

		collectJob.cancel()
	}

	@Test
	fun incomingBinaryResponses_flowReceivesPushEvents() = runTest(UnconfinedTestDispatcher()) {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		val config = ConnectionConfig(
			autoSyncTime = false,
			autoFetchContacts = false,
			autoFetchChannels = false,
			autoPollMessages = false,
		)
		val connection = DeviceConnection(
			bleConnection = bleConnection,
			commandQueue = queue,
			scope = backgroundScope,
			config = config,
		)

		val collected = mutableListOf<ReceivedBinaryResponse>()
		val collectJob = backgroundScope.launch {
			connection.incomingBinaryResponses.collect { collected.add(it) }
		}

		// Simulate a binary response push
		val data = ByteArray(8)
		data[0] = 0x8C.toByte()
		data[1] = 0x00
		// tag = 42
		data[2] = 0x2A; data[3] = 0x00; data[4] = 0x00; data[5] = 0x00
		data[6] = 0xDE.toByte(); data[7] = 0xAD.toByte()
		bleConnection.simulateResponse(data)

		assertEquals(1, collected.size)
		assertEquals(42L, collected[0].tag)
		assertContentEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte()), collected[0].responseData)

		collectJob.cancel()
	}
}
