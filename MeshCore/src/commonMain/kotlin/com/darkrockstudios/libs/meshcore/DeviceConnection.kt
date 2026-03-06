package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.ble.BleConnection
import com.darkrockstudios.libs.meshcore.ble.ConnectionState
import com.darkrockstudios.libs.meshcore.model.*
import com.darkrockstudios.libs.meshcore.protocol.CommandQueue
import com.darkrockstudios.libs.meshcore.protocol.CommandSerializer
import com.darkrockstudios.libs.meshcore.protocol.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

	val loginResults: Flow<Pair<String, Boolean>> = commandQueue.pushEvents
		.filter { it is Response.LoginSuccess || it is Response.LoginFail }
		.map {
			when (it) {
				is Response.LoginSuccess -> it.publicKeyPrefix to true
				is Response.LoginFail -> it.publicKeyPrefix to false
				else -> error("unreachable")
			}
		}

	val statusResponses: Flow<Response.StatusResponse> = commandQueue.pushEvents
		.filterIsInstance<Response.StatusResponse>()

	val traceData: Flow<Response.TraceData> = commandQueue.pushEvents
		.filterIsInstance<Response.TraceData>()

	val newAdverts: Flow<Response.NewAdvert> = commandQueue.pushEvents
		.filterIsInstance<Response.NewAdvert>()

	val telemetryResponses: Flow<Response.TelemetryResponse> = commandQueue.pushEvents
		.filterIsInstance<Response.TelemetryResponse>()

	val pathDiscoveryResponses: Flow<Response.PathDiscoveryResponse> = commandQueue.pushEvents
		.filterIsInstance<Response.PathDiscoveryResponse>()

	val controlData: Flow<Response.ControlData> = commandQueue.pushEvents
		.filterIsInstance<Response.ControlData>()

	val contactDeleted: Flow<String> = commandQueue.pushEvents
		.filterIsInstance<Response.ContactDeleted>()
		.map { it.publicKeyPrefix }

	internal suspend fun initialize() {
		// 1. APP_START — returns SelfInfo
		val selfInfoResp = commandQueue.execute<Response.SelfInfo>(
			CommandSerializer.appStart(config.appName),
			config.commandTimeout,
		)
		_selfInfo.value = selfInfoResp.toDomainModel()

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
			milliVolts = resp.milliVolts,
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

	suspend fun sendDirectMessage(publicKeyPrefix: ByteArray, text: String): MessageSentConfirmation {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val timestamp = currentTimeSeconds()
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendDirectMessage(publicKeyPrefix, text, timestamp),
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

	// --- Remote Command ---

	suspend fun sendRemoteCommand(publicKeyPrefix: ByteArray, command: String): MessageSentConfirmation {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val timestamp = currentTimeSeconds()
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendRemoteCommand(publicKeyPrefix, command, timestamp),
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

	// --- Advertisement ---

	suspend fun sendAdvert(flood: Boolean = false) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.sendAdvert(flood),
			config.commandTimeout,
		)
	}

	// --- Contacts ---

	suspend fun addContact(publicKey: ByteArray, name: String, type: Int = 0, flags: Int = 0) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.addContact(publicKey, name, type, flags),
			config.commandTimeout,
		)
	}

	suspend fun updateContact(
		publicKey: ByteArray,
		name: String,
		type: Int,
		flags: Int,
		outPath: ByteArray = ByteArray(64),
	) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.updateContact(publicKey, name, type, flags, outPath),
			config.commandTimeout,
		)
	}

	suspend fun removeContact(publicKey: ByteArray) {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		commandQueue.execute<Response.Ok>(
			CommandSerializer.removeContact(publicKey),
			config.commandTimeout,
		)
	}

	suspend fun resetPath(publicKey: ByteArray) {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		commandQueue.execute<Response.Ok>(
			CommandSerializer.resetPath(publicKey),
			config.commandTimeout,
		)
	}

	suspend fun shareContact(publicKey: ByteArray) {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		commandQueue.execute<Response.Ok>(
			CommandSerializer.shareContact(publicKey),
			config.commandTimeout,
		)
	}

	suspend fun exportContact(publicKey: ByteArray? = null): String {
		val resp = commandQueue.execute<Response.ContactUri>(
			CommandSerializer.exportContact(publicKey),
			config.commandTimeout,
		)
		return resp.uri
	}

	suspend fun importContact(cardData: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.importContact(cardData),
			config.commandTimeout,
		)
	}

	suspend fun setAutoAddConfig(enabled: Boolean) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setAutoAddConfig(enabled),
			config.commandTimeout,
		)
	}

	suspend fun getAutoAddConfig(): Boolean {
		val resp = commandQueue.execute<Response.AutoAddConfig>(
			CommandSerializer.getAutoAddConfig(),
			config.commandTimeout,
		)
		return resp.enabled
	}

	suspend fun getContacts(): List<Contact> {
		val contactList = mutableListOf<Contact>()
		commandQueue.executeStreaming<Response.ContactEnd>(
			CommandSerializer.getContacts(),
			config.commandTimeout,
			onResponse = { response ->
				when (response) {
					is Response.Contact -> {
						contactList.add(response.toDomainModel())
						true // continue
					}

					is Response.ContactEnd -> false // stop
					is Response.ContactStart -> true // continue
					else -> true // ignore others?
				}
			}
		)
		_contacts.value = contactList
		return contactList
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
		val now = currentTimeSeconds()
		val deviceTime = commandQueue.execute<Response.CurrentTime>(
			CommandSerializer.getDeviceTime(),
			config.commandTimeout,
		)
		if (now >= deviceTime.timestamp) {
			commandQueue.execute<Response.Ok>(
				CommandSerializer.setDeviceTime(now),
				config.commandTimeout,
			)
		}
	}

	// --- Device Configuration ---

	suspend fun setName(name: String) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setName(name),
			config.commandTimeout,
		)
	}

	suspend fun setCoords(latitude: Double, longitude: Double) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setCoords(latitude, longitude),
			config.commandTimeout,
		)
	}

	suspend fun setTxPower(power: Int) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setTxPower(power),
			config.commandTimeout,
		)
	}

	suspend fun setDevicePin(pin: Int) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setDevicePin(pin),
			config.commandTimeout,
		)
	}

	suspend fun setRadio(
		frequency: Double,
		bandwidth: Double,
		spreadingFactor: Int,
		codingRate: Int,
		repeat: Int = 0,
	) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setRadio(frequency, bandwidth, spreadingFactor, codingRate, repeat),
			config.commandTimeout,
		)
	}

	suspend fun setTuning(rxDelay: Int, afFactor: Int) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setTuning(rxDelay, afFactor),
			config.commandTimeout,
		)
	}

	suspend fun reboot() {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.reboot(),
			config.commandTimeout,
		)
	}

	suspend fun factoryReset() {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.factoryReset(),
			config.commandTimeout,
		)
	}

	suspend fun setOtherParams(
		manualAddContacts: Boolean,
		telemetryModeBase: Int = 0,
		telemetryModeLoc: Int = 0,
		telemetryModeEnv: Int = 0,
		advLocPolicy: Int = 0,
	) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setOtherParams(
				manualAddContacts,
				telemetryModeBase,
				telemetryModeLoc,
				telemetryModeEnv,
				advLocPolicy
			),
			config.commandTimeout,
		)
	}

	// --- Auth ---

	suspend fun sendLogin(publicKey: ByteArray, password: String): MessageSentConfirmation {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendLogin(publicKey, password),
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

	suspend fun sendLogout(publicKey: ByteArray) {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		commandQueue.execute<Response.Ok>(
			CommandSerializer.sendLogout(publicKey),
			config.commandTimeout,
		)
	}

	// --- Path Discovery ---

	suspend fun sendPathDiscovery(publicKey: ByteArray): MessageSentConfirmation {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendPathDiscovery(publicKey),
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

	suspend fun hasConnection(publicKeyPrefix: ByteArray): Boolean {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val resp = commandQueue.execute<Response.Ok>(
			CommandSerializer.hasConnection(publicKeyPrefix),
			config.commandTimeout,
		)
		return (resp.value ?: 0) > 0
	}

	suspend fun sendTrace(
		tag: Int,
		authCode: Int,
		flags: Int,
		path: ByteArray = ByteArray(0)
	): MessageSentConfirmation {
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendTrace(tag, authCode, flags, path),
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

	// --- Cryptography ---

	suspend fun exportPrivateKey(): ByteArray {
		val resp = commandQueue.execute<Response.PrivateKey>(
			CommandSerializer.exportPrivateKey(),
			config.commandTimeout,
		)
		return resp.key
	}

	suspend fun importPrivateKey(key: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.importPrivateKey(key),
			config.commandTimeout,
		)
	}

	suspend fun signStart(): Int {
		val resp = commandQueue.execute<Response.SignStartResponse>(
			CommandSerializer.signStart(),
			config.commandTimeout,
		)
		return resp.sessionId
	}

	suspend fun signData(chunk: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.signData(chunk),
			config.commandTimeout,
		)
	}

	suspend fun signFinish(): ByteArray {
		val resp = commandQueue.execute<Response.Signature>(
			CommandSerializer.signFinish(),
			config.commandTimeout,
		)
		return resp.signatureData
	}

	// --- Custom Variables ---

	suspend fun getCustomVars(): String {
		val resp = commandQueue.execute<Response.CustomVars>(
			CommandSerializer.getCustomVars(),
			config.commandTimeout,
		)
		return resp.data
	}

	suspend fun setCustomVar(key: String, value: String) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setCustomVar(key, value),
			config.commandTimeout,
		)
	}

	// --- Telemetry ---

	suspend fun getSelfTelemetry(): ByteArray {
		val resp = commandQueue.execute<Response>(
			CommandSerializer.getSelfTelemetry(),
			config.commandTimeout,
		)
		return when (resp) {
			is Response.Ok -> ByteArray(0)
			else -> throw MeshCoreException.UnexpectedResponse(
				"Expected Ok, got ${resp::class.simpleName}"
			)
		}
	}

	suspend fun sendTelemetryRequest(publicKey: ByteArray): MessageSentConfirmation {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendTelemetryRequest(publicKey),
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

	suspend fun sendStatusRequest(publicKey: ByteArray): MessageSentConfirmation {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendStatusRequest(publicKey),
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

	// --- Control Data ---

	suspend fun sendControlData(type: Int, payload: ByteArray) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.sendControlData(type, payload),
			config.commandTimeout,
		)
	}

	// --- Anonymous Requests ---

	suspend fun sendAnonymousRequest(
		publicKey: ByteArray,
		requestType: Int,
		payload: ByteArray = ByteArray(0)
	): MessageSentConfirmation {
		val resp = commandQueue.execute<Response>(
			CommandSerializer.sendAnonymousRequest(publicKey, requestType, payload),
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

	// --- Config Queries ---

	suspend fun getAllowedRepeatFreq(): ByteArray {
		val resp = commandQueue.execute<Response.AllowedRepeatFreq>(
			CommandSerializer.getAllowedRepeatFreq(),
			config.commandTimeout,
		)
		return resp.frequencies
	}

	suspend fun setPathHashMode(mode: Int) {
		commandQueue.execute<Response.Ok>(
			CommandSerializer.setPathHashMode(mode),
			config.commandTimeout,
		)
	}

	// --- ACK waiting ---

	suspend fun sendAndAwaitAck(
		block: suspend DeviceConnection.() -> MessageSentConfirmation,
	): Result<MessageSentConfirmation> {
		// Buffer acks before sending so we don't miss fast responses
		val receivedAcks = mutableSetOf<String>()
		val collectorJob = scope.launch {
			commandQueue.pushEvents
				.filterIsInstance<Response.Ack>()
				.collect { receivedAcks.add(it.ackCode) }
		}

		try {
			val confirmation = block()

			if (confirmation.expectedAck.isEmpty()) return Result.success(confirmation)

			// Check if ack already arrived while sending
			if (confirmation.expectedAck in receivedAcks) return Result.success(confirmation)

			val timeoutMs = if (confirmation.suggestedTimeoutSeconds > 0) {
				confirmation.suggestedTimeoutSeconds * 1000L
			} else {
				config.commandTimeout.inWholeMilliseconds
			}

			// Wait for matching ack, re-checking the buffer on each emission
			val matched = withTimeoutOrNull(timeoutMs) {
				commandQueue.pushEvents
					.filterIsInstance<Response.Ack>()
					.first { it.ackCode == confirmation.expectedAck }
			}

			return if (matched != null || confirmation.expectedAck in receivedAcks) {
				Result.success(confirmation)
			} else {
				Result.failure(
					MeshCoreException.AckTimeout("ACK not received within ${timeoutMs}ms")
				)
			}
		} finally {
			collectorJob.cancel()
		}
	}

	// --- Conversion helpers ---

	private fun Response.Contact.toDomainModel(): Contact {
		return Contact(
			publicKey = publicKey,
			name = name,
			type = type,
			flags = flags,
			lastAdvertTimestamp = lastAdvertTimestamp,
			gpsLatitude = gpsLatitude,
			gpsLongitude = gpsLongitude,
			lastmod = lastmod,
		)
	}

	private fun Response.SelfInfo.toDomainModel() = SelfInfo(
		advertisementType = advertisementType,
		txPower = txPower,
		maxTxPower = maxTxPower,
		publicKey = publicKey,
		advertisementLatitude = advertisementLatitude,
		advertisementLongitude = advertisementLongitude,
		multiAcks = multiAcks,
		advertisementLocationPolicy = advertisementLocationPolicy,
		telemetryModeBase = telemetryModeBase,
		telemetryModeLoc = telemetryModeLoc,
		telemetryModeEnv = telemetryModeEnv,
		manualAddContacts = manualAddContacts,
		radioFrequency = radioFrequency,
		radioBandwidth = radioBandwidth,
		radioSpreadingFactor = radioSpreadingFactor,
		radioCodingRate = radioCodingRate,
		deviceName = deviceName,
	)

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
