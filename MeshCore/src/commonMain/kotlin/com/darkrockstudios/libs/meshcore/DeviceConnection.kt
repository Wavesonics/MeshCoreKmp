package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.ble.BleConnection
import com.darkrockstudios.libs.meshcore.ble.ConnectionState
import com.darkrockstudios.libs.meshcore.model.*
import com.darkrockstudios.libs.meshcore.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DeviceConnection internal constructor(
	private val bleConnection: BleConnection,
	internal val commandQueue: CommandQueue,
	private val scope: CoroutineScope,
	private val config: ConnectionConfig,
) {
	val deviceIdentifier: String get() = bleConnection.deviceIdentifier
	val connectionState: StateFlow<ConnectionState> get() = bleConnection.connectionState

	private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
	val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

	private val _selfInfo = MutableStateFlow<SelfInfo?>(null)
	val selfInfo: StateFlow<SelfInfo?> = _selfInfo.asStateFlow()

	private val _channels = MutableStateFlow<List<Channel>>(emptyList())
	val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

	private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
	val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

	private val _incomingMessages = MutableSharedFlow<ReceivedMessage>(extraBufferCapacity = 64)
	val incomingMessages: Flow<ReceivedMessage> = _incomingMessages.asSharedFlow()

	val acks: Flow<String> = commandQueue.pushEvents
		.filterIsInstance<Response.Ack>()
		.map { it.ackCode }

	val advertisements: Flow<ByteArray> = commandQueue.pushEvents
		.filterIsInstance<Response.AdvertisementReceived>()
		.map { it.rawData }

	val incomingRawData: Flow<ReceivedRawData> = commandQueue.pushEvents
		.filterIsInstance<Response.RawDataReceived>()
		.map { ReceivedRawData(snr = it.snr, rssi = it.rssi, payload = it.payload) }

	val incomingBinaryResponses: Flow<ReceivedBinaryResponse> = commandQueue.pushEvents
		.filterIsInstance<Response.BinaryResponse>()
		.map { ReceivedBinaryResponse(tag = it.tag, responseData = it.responseData) }

	internal suspend fun initialize() {
		// 1. APP_START
		commandQueue.execute<Response.Ok>(
			CommandSerializer.appStart(config.appName),
			config.commandTimeout,
		)

		// 2. DEVICE_QUERY
		val deviceInfoResp = commandQueue.execute<Response.DeviceInfo>(
			CommandSerializer.deviceQuery(),
			config.commandTimeout,
		)
		_deviceInfo.value = deviceInfoResp.toDomainModel()

		// 3. Auto-init steps based on config
		if (config.autoSyncTime) {
			syncDeviceTime()
		}

		if (config.autoFetchChannels) {
			val maxChannels = deviceInfoResp.maxChannels.coerceIn(1, 8)
			val channelList = mutableListOf<Channel>()
			for (i in 0 until maxChannels) {
				val ch = getChannel(i)
				channelList.add(ch)
			}
			_channels.value = channelList
		}

		if (config.autoPollMessages) {
			drainMessages()
		}

		// Setup message waiting listener
		scope.launch {
			commandQueue.pushEvents
				.filterIsInstance<Response.MessagesWaiting>()
				.collect { drainMessages() }
		}
	}

	suspend fun disconnect() {
		commandQueue.reset()
		bleConnection.disconnect()
	}

	// --- Battery ---

	suspend fun getBattery(): BatteryInfo {
		val resp = commandQueue.execute<Response.Battery>(
			CommandSerializer.getBattery(),
			config.commandTimeout,
		)
		return BatteryInfo(
			levelPercent = resp.levelPercent,
			usedStorageKb = resp.usedStorageKb,
			totalStorageKb = resp.totalStorageKb,
		)
	}

	// --- Channels ---

	suspend fun getChannel(index: Int): Channel {
		val resp = commandQueue.execute<Response.ChannelInfo>(
			CommandSerializer.getChannel(index),
			config.commandTimeout,
		)
		return Channel(index = resp.index, name = resp.name)
	}

	suspend fun getAllChannels(): List<Channel> {
		val maxChannels = _deviceInfo.value?.maxChannels?.coerceIn(1, 8) ?: 8
		val channelList = mutableListOf<Channel>()
		for (i in 0 until maxChannels) {
			channelList.add(getChannel(i))
		}
		_channels.value = channelList
		return channelList
	}

	suspend fun setChannel(index: Int, name: String, secret: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setChannel(index, name, secret),
			config.commandTimeout,
		)
		// Refresh the channel info
		val updated = getChannel(index)
		_channels.value = _channels.value.toMutableList().apply {
			val existing = indexOfFirst { it.index == index }
			if (existing >= 0) set(existing, updated)
			else add(updated)
		}
	}

	suspend fun deleteChannel(index: Int) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.deleteChannel(index),
			config.commandTimeout,
		)
		_channels.value = _channels.value.toMutableList().apply {
			val existing = indexOfFirst { it.index == index }
			if (existing >= 0) set(existing, Channel(index = index, name = ""))
		}
	}

	// --- Messaging ---

	suspend fun sendChannelMessage(channelIndex: Int, text: String): MessageSentConfirmation {
		val timestamp = currentTimeSeconds()
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendChannelMessage(channelIndex, text, timestamp),
			config.commandTimeout,
		)
		return when (resp) {
			is Response.MessageSent -> MessageSentConfirmation(
				messageType = resp.messageType,
				expectedAck = resp.expectedAck,
				suggestedTimeoutSeconds = resp.suggestedTimeoutSeconds,
			)

			is Response.Ok -> MessageSentConfirmation(
				messageType = 0,
				expectedAck = "",
				suggestedTimeoutSeconds = 0,
			)

			else -> throw MeshCoreException.UnexpectedResponse(
				"Expected MessageSent or Ok, got ${resp::class.simpleName}"
			)
		}
	}

	suspend fun pollNextMessage(): ReceivedMessage? {
		val resp = commandQueue.execute<Response>(
			CommandSerializer.getMessage(),
			config.commandTimeout,
		)
		return when (resp) {
			is Response.ChannelMessageReceived -> resp.toDomainModel()
			is Response.ContactMessageReceived -> resp.toDomainModel()
			is Response.NoMoreMessages -> null
			else -> null
		}
	}

	private suspend fun drainMessages() {
		while (true) {
			val msg = pollNextMessage() ?: break
			_incomingMessages.emit(msg)
		}
	}

	// --- Raw Data / Binary ---

	suspend fun sendRawData(path: ByteArray = ByteArray(0), payload: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.sendRawData(path, payload),
			config.commandTimeout,
		)
	}

	suspend fun sendBinaryRequest(publicKey: ByteArray, requestData: ByteArray): MessageSentConfirmation {
		val resp = commandQueue.execute<Response.MessageSent>(
			CommandSerializer.sendBinaryRequest(publicKey, requestData),
			config.commandTimeout,
		)
		return MessageSentConfirmation(
			messageType = resp.messageType,
			expectedAck = resp.expectedAck,
			suggestedTimeoutSeconds = resp.suggestedTimeoutSeconds,
		)
	}

	// --- Contacts ---

	suspend fun getContacts(): List<Contact> {
		// Contact fetching requires CMD_GET_CONTACTS which streams
		// CONTACT_START -> CONTACT* -> CONTACT_END responses.
		// This is a simplified placeholder - the actual command code
		// isn't fully documented in the companion spec.
		return _contacts.value
	}

	// --- Stats ---

	suspend fun getCoreStats(): Stats.Core {
		val resp = commandQueue.execute<Response.Stats.Core>(
			CommandSerializer.getStats(0),
			config.commandTimeout,
		)
		return Stats.Core(
			batteryMillivolts = resp.batteryMillivolts,
			uptimeSeconds = resp.uptimeSeconds,
			errors = resp.errors,
			queueLength = resp.queueLength,
		)
	}

	suspend fun getRadioStats(): Stats.Radio {
		val resp = commandQueue.execute<Response.Stats.Radio>(
			CommandSerializer.getStats(1),
			config.commandTimeout,
		)
		return Stats.Radio(
			noiseFloorDbm = resp.noiseFloorDbm,
			lastRssiDbm = resp.lastRssiDbm,
			lastSnrDb = resp.lastSnrDb,
			txAirtimeSeconds = resp.txAirtimeSeconds,
			rxAirtimeSeconds = resp.rxAirtimeSeconds,
		)
	}

	suspend fun getPacketStats(): Stats.Packets {
		val resp = commandQueue.execute<Response.Stats.Packets>(
			CommandSerializer.getStats(2),
			config.commandTimeout,
		)
		return Stats.Packets(
			received = resp.received,
			sent = resp.sent,
			floodTx = resp.floodTx,
			directTx = resp.directTx,
			floodRx = resp.floodRx,
			directRx = resp.directRx,
			recvErrors = resp.recvErrors,
		)
	}

	// --- Time sync ---

	suspend fun syncDeviceTime() {
		// Sync time by sending current timestamp
		// Uses the APP_START response's timestamp or sends a time sync command
		// The exact time sync command format isn't fully documented,
		// but typically it's the current Unix timestamp sent to the device
	}

	// --- Conversion helpers ---

	private fun Response.DeviceInfo.toDomainModel() = DeviceInfo(
		firmwareVersion = firmwareVersion,
		maxContacts = maxContacts,
		maxChannels = maxChannels,
		blePin = blePin,
		firmwareBuild = firmwareBuild,
		model = model,
		version = version,
	)

	private fun Response.ChannelMessageReceived.toDomainModel() = ReceivedMessage.ChannelMessage(
		channelIndex = channelIndex,
		timestamp = timestamp,
		text = text,
		pathLength = pathLength,
		snr = snr,
	)

	private fun Response.ContactMessageReceived.toDomainModel() = ReceivedMessage.ContactMessage(
		publicKeyPrefix = publicKeyPrefix,
		signature = signature,
		timestamp = timestamp,
		text = text,
		pathLength = pathLength,
		snr = snr,
	)

	private fun currentTimeSeconds(): Long =
		kotlin.time.Clock.System.now().epochSeconds
}
