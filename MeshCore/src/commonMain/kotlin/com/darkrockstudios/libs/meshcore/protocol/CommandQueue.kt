package com.darkrockstudios.libs.meshcore.protocol

import com.darkrockstudios.libs.meshcore.MeshCoreException
import com.darkrockstudios.libs.meshcore.ble.BleConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CommandQueue(
	private val connection: BleConnection,
	scope: CoroutineScope,
	private val defaultTimeout: Duration = 5.seconds,
) {
	private val commandMutex = Mutex()

	private val _pushEvents = MutableSharedFlow<Response>(extraBufferCapacity = 64)
	val pushEvents: SharedFlow<Response> = _pushEvents.asSharedFlow()

	private var pendingResponseChannel: Channel<Response>? = null

	init {
		scope.launch {
			connection.incomingData.collect { data ->
				val response = ResponseParser.parse(data)
				if (response != null) {
					routeResponse(response)
				}
			}
		}
	}

	private suspend fun routeResponse(response: Response) {
		val pending = pendingResponseChannel
		if (pending != null && !isPushEvent(response)) {
			pending.send(response)
		} else {
			_pushEvents.emit(response)
		}
	}

	private fun isPushEvent(response: Response): Boolean = when (response) {
		is Response.MessagesWaiting -> true
		is Response.Ack -> true
		is Response.AdvertisementReceived -> true
		is Response.RawDataReceived -> true
		is Response.BinaryResponse -> true
		is Response.LogData -> true
		is Response.Unhandled -> true // all unhandled known codes are push events or ignorable
		else -> false
	}

	suspend fun <T : Response> execute(
		command: ByteArray,
		timeout: Duration = defaultTimeout,
	): T {
		return executeStreaming<T>(command, timeout) { false }
	}

	suspend fun <T : Response> executeStreaming(
		command: ByteArray,
		timeout: Duration = defaultTimeout,
		onResponse: suspend (Response) -> Boolean,
	): T {
		commandMutex.withLock {
			val responseChannel = Channel<Response>(1)
			pendingResponseChannel = responseChannel

			try {
				connection.write(command)
				var lastResponse: Response? = null
				while (true) {
					val response = withTimeout(timeout) {
						responseChannel.receive()
					}
					if (response is Response.Error) {
						throw MeshCoreException.DeviceError(response.code)
					}
					lastResponse = response
					if (!onResponse(response)) {
						break
					}
				}
				@Suppress("UNCHECKED_CAST")
				return lastResponse as T
			} catch (e: kotlinx.coroutines.TimeoutCancellationException) {
				throw MeshCoreException.CommandTimeout(
					"Command timed out after $timeout"
				)
			} finally {
				pendingResponseChannel = null
				responseChannel.close()
			}
		}
	}

	fun reset() {
		pendingResponseChannel?.close()
		pendingResponseChannel = null
	}
}
