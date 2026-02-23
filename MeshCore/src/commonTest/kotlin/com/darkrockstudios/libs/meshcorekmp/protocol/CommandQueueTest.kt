@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.darkrockstudios.libs.meshcorekmp.protocol

import com.darkrockstudios.libs.meshcorekmp.FakeBleConnection
import com.darkrockstudios.libs.meshcorekmp.MeshCoreException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class CommandQueueTest {

	@Test
	fun execute_sendsCommandAndReceivesResponse() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()

		launch {
			while (bleConnection.writtenData.isEmpty()) {
				kotlinx.coroutines.yield()
			}
			bleConnection.simulateResponse(byteArrayOf(0x00, 0x2A, 0x00, 0x00, 0x00))
		}

		val result = queue.execute<Response.Ok>(
			command = CommandSerializer.getBattery(),
		)

		assertEquals(42, result.value)
		assertEquals(1, bleConnection.writtenData.size)
		assertEquals(0x14.toByte(), bleConnection.writtenData[0][0])
	}

	@Test
	fun execute_deviceErrorThrows() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)
		testScheduler.advanceUntilIdle()

		launch {
			while (bleConnection.writtenData.isEmpty()) {
				kotlinx.coroutines.yield()
			}
			bleConnection.simulateResponse(byteArrayOf(0x01, 0x07))
		}

		val exception = assertFailsWith<MeshCoreException.DeviceError> {
			queue.execute<Response.Ok>(
				command = CommandSerializer.getBattery(),
			)
		}
		assertEquals(ErrorCode.MESSAGE_TOO_LONG, exception.errorCode)
	}

	@Test
	fun execute_timeoutThrows() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
			defaultTimeout = 100.milliseconds,
		)
		testScheduler.advanceUntilIdle()

		assertFailsWith<MeshCoreException.CommandTimeout> {
			queue.execute<Response.Ok>(
				command = CommandSerializer.getBattery(),
				timeout = 100.milliseconds,
			)
		}
	}

	@Test
	fun pushEvents_messagesWaitingRouted() = runTest(UnconfinedTestDispatcher()) {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)

		val collected = mutableListOf<Response>()
		val collectJob = backgroundScope.launch {
			queue.pushEvents.collect { collected.add(it) }
		}

		// Simulate a push event (no command pending)
		bleConnection.simulateResponse(byteArrayOf(0x83.toByte(), 0x03))

		assertEquals(1, collected.size, "Expected 1 push event, got ${collected.size}")
		assertIs<Response.MessagesWaiting>(collected[0])
		assertEquals(3, (collected[0] as Response.MessagesWaiting).count)

		collectJob.cancel()
	}

	@Test
	fun pushEvents_ackRoutedDuringCommand() = runTest {
		val bleConnection = FakeBleConnection()
		val queue = CommandQueue(
			connection = bleConnection,
			scope = backgroundScope,
		)

		val pushEvents = mutableListOf<Response>()
		val collectJob = backgroundScope.launch {
			queue.pushEvents.collect { pushEvents.add(it) }
		}
		testScheduler.advanceUntilIdle()

		launch {
			while (bleConnection.writtenData.isEmpty()) {
				kotlinx.coroutines.yield()
			}
			// Send a push event (ACK) while a command is pending
			bleConnection.simulateResponse(byteArrayOf(
				0x82.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06
			))
			kotlinx.coroutines.yield()
			// Then send the actual command response
			bleConnection.simulateResponse(byteArrayOf(0x00))
		}

		val result = queue.execute<Response.Ok>(
			command = CommandSerializer.getBattery(),
		)
		testScheduler.advanceUntilIdle()

		assertEquals(1, pushEvents.size)
		assertIs<Response.Ack>(pushEvents[0])
		assertIs<Response.Ok>(result)

		collectJob.cancel()
	}
}
