package com.darkrockstudios.libs.meshcorekmp

import com.darkrockstudios.libs.meshcorekmp.protocol.CommandQueue
import com.darkrockstudios.libs.meshcorekmp.protocol.Response
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DeviceConnectionTest {

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
			bleConnection.simulateResponse(byteArrayOf(0x00)) // OK
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
			bleConnection.simulateResponse(byteArrayOf(0x00))
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
			bleConnection.simulateResponse(byteArrayOf(0x00))
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
			bleConnection.simulateResponse(byteArrayOf(0x00))
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
}
