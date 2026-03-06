package com.darkrockstudios.libs.meshcore.protocol

object CommandSerializer {

	fun appStart(appName: String = "mccli"): ByteArray {
		val nameBytes = appName.encodeToByteArray()
		val buffer = ByteArray(8 + nameBytes.size)
		buffer[0] = CommandCode.APP_START.toByte()
		buffer[1] = 0x03
		// Bytes 2-7 are reserved (zero-filled by default)
		nameBytes.copyInto(buffer, 8)
		return buffer
	}

	fun deviceQuery(): ByteArray =
		byteArrayOf(CommandCode.DEVICE_QUERY.toByte(), 0x03)

	fun getContacts(): ByteArray =
		byteArrayOf(CommandCode.GET_CONTACTS.toByte())

	fun getChannel(index: Int): ByteArray {
		require(index in 0..7) { "Channel index must be 0-7" }
		return byteArrayOf(CommandCode.GET_CHANNEL.toByte(), index.toByte())
	}

	fun setChannel(index: Int, name: String, secret: ByteArray): ByteArray {
		require(secret.size == 32) { "Secret must be 32 bytes" }
		require(index in 0..7) { "Channel index must be 0-7" }
		val buffer = ByteArray(66)
		buffer[0] = CommandCode.SET_CHANNEL.toByte()
		buffer[1] = index.toByte()
		val nameBytes = name.encodeToByteArray()
		nameBytes.copyInto(buffer, 2, 0, minOf(nameBytes.size, 32))
		secret.copyInto(buffer, 34)
		return buffer
	}

	fun deleteChannel(index: Int): ByteArray =
		setChannel(index, "", ByteArray(32))

	fun sendDirectMessage(
		publicKeyPrefix: ByteArray,
		text: String,
		timestampSeconds: Long,
		attempt: Int = 0
	): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val textBytes = text.encodeToByteArray()
		val buffer = ByteArray(7 + 6 + textBytes.size)
		buffer[0] = CommandCode.SEND_TXT_MSG.toByte()
		buffer[1] = 0x00 // subcode: text
		buffer[2] = attempt.toByte()
		putUInt32LE(buffer, 3, timestampSeconds)
		publicKeyPrefix.copyInto(buffer, 7)
		textBytes.copyInto(buffer, 13)
		return buffer
	}

	fun sendChannelMessage(channelIndex: Int, text: String, timestampSeconds: Long): ByteArray {
		require(channelIndex in 0..7) { "Channel index must be 0-7" }
		val textBytes = text.encodeToByteArray()
		val buffer = ByteArray(7 + textBytes.size)
		buffer[0] = CommandCode.SEND_CHANNEL_MESSAGE.toByte()
		buffer[1] = 0x00
		buffer[2] = channelIndex.toByte()
		putUInt32LE(buffer, 3, timestampSeconds)
		textBytes.copyInto(buffer, 7)
		return buffer
	}

	fun getMessage(): ByteArray =
		byteArrayOf(CommandCode.GET_MESSAGE.toByte())

	fun getDeviceTime(): ByteArray =
		byteArrayOf(CommandCode.GET_DEVICE_TIME.toByte())

	fun setDeviceTime(epochSeconds: Long): ByteArray {
		val buffer = ByteArray(5)
		buffer[0] = CommandCode.SET_DEVICE_TIME.toByte()
		putUInt32LE(buffer, 1, epochSeconds)
		return buffer
	}

	fun getBattery(): ByteArray =
		byteArrayOf(CommandCode.GET_BATTERY.toByte())

	fun getStats(subType: Int): ByteArray {
		require(subType in 0..2) { "Stats sub-type must be 0-2" }
		return byteArrayOf(CommandCode.GET_STATS.toByte(), subType.toByte())
	}

	fun sendRawData(path: ByteArray, payload: ByteArray): ByteArray {
		val buffer = ByteArray(1 + 1 + path.size + payload.size)
		buffer[0] = CommandCode.SEND_RAW_DATA.toByte()
		buffer[1] = path.size.toByte()
		path.copyInto(buffer, 2)
		payload.copyInto(buffer, 2 + path.size)
		return buffer
	}

	fun sendBinaryRequest(publicKey: ByteArray, requestData: ByteArray): ByteArray {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val buffer = ByteArray(1 + 32 + requestData.size)
		buffer[0] = CommandCode.SEND_BINARY_REQ.toByte()
		publicKey.copyInto(buffer, 1)
		requestData.copyInto(buffer, 33)
		return buffer
	}

	// --- Phase 1: Contact Management ---

	fun addContact(publicKey: ByteArray, name: String, type: Int = 0, flags: Int = 0): ByteArray {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val nameBytes = name.encodeToByteArray()
		// 1 cmd + 32 key + 1 type + 1 flags + 64 path + 32 name = 131
		val buffer = ByteArray(131)
		buffer[0] = CommandCode.ADD_UPDATE_CONTACT.toByte()
		publicKey.copyInto(buffer, 1)
		buffer[33] = type.toByte()
		buffer[34] = flags.toByte()
		// bytes 35-98: out_path (64 bytes, zeros = no path)
		// bytes 99-130: name (32 bytes)
		nameBytes.copyInto(buffer, 99, 0, minOf(nameBytes.size, 32))
		return buffer
	}

	fun updateContact(
		publicKey: ByteArray,
		name: String,
		type: Int,
		flags: Int,
		outPath: ByteArray = ByteArray(64),
	): ByteArray {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		require(outPath.size <= 64) { "Path must be <= 64 bytes" }
		val nameBytes = name.encodeToByteArray()
		val buffer = ByteArray(131)
		buffer[0] = CommandCode.ADD_UPDATE_CONTACT.toByte()
		publicKey.copyInto(buffer, 1)
		buffer[33] = type.toByte()
		buffer[34] = flags.toByte()
		outPath.copyInto(buffer, 35, 0, minOf(outPath.size, 64))
		nameBytes.copyInto(buffer, 99, 0, minOf(nameBytes.size, 32))
		return buffer
	}

	fun removeContact(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.REMOVE_CONTACT.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun resetPath(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.RESET_PATH.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun shareContact(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.SHARE_CONTACT.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun exportContact(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.EXPORT_CONTACT.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun importContact(cardData: ByteArray): ByteArray {
		val buffer = ByteArray(1 + cardData.size)
		buffer[0] = CommandCode.IMPORT_CONTACT.toByte()
		cardData.copyInto(buffer, 1)
		return buffer
	}

	fun setAutoAddConfig(enabled: Boolean): ByteArray =
		byteArrayOf(CommandCode.SET_AUTOADD_CONFIG.toByte(), if (enabled) 0x01 else 0x00)

	fun getAutoAddConfig(): ByteArray =
		byteArrayOf(CommandCode.GET_AUTOADD_CONFIG.toByte())

	// --- Phase 1: Remote Command ---

	fun sendRemoteCommand(
		publicKeyPrefix: ByteArray,
		command: String,
		timestampSeconds: Long,
		attempt: Int = 0,
	): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val cmdBytes = command.encodeToByteArray()
		val buffer = ByteArray(7 + 6 + cmdBytes.size)
		buffer[0] = CommandCode.SEND_TXT_MSG.toByte()
		buffer[1] = 0x01 // subcode: command
		buffer[2] = attempt.toByte()
		putUInt32LE(buffer, 3, timestampSeconds)
		publicKeyPrefix.copyInto(buffer, 7)
		cmdBytes.copyInto(buffer, 13)
		return buffer
	}

	// --- Phase 1: Advertisement ---

	fun sendAdvert(flood: Boolean = false): ByteArray =
		byteArrayOf(CommandCode.SEND_ADVERT.toByte(), if (flood) 0x01 else 0x00)

	// --- Phase 2: Device Configuration ---

	fun setName(name: String): ByteArray {
		val nameBytes = name.encodeToByteArray()
		val buffer = ByteArray(1 + nameBytes.size)
		buffer[0] = CommandCode.SET_NAME.toByte()
		nameBytes.copyInto(buffer, 1)
		return buffer
	}

	fun setCoords(latitude: Double, longitude: Double): ByteArray {
		val buffer = ByteArray(9)
		buffer[0] = CommandCode.SET_COORDS.toByte()
		putInt32LE(buffer, 1, (latitude * 1_000_000).toInt())
		putInt32LE(buffer, 5, (longitude * 1_000_000).toInt())
		return buffer
	}

	fun setTxPower(power: Int): ByteArray =
		byteArrayOf(CommandCode.SET_TX_POWER.toByte(), power.toByte())

	fun setDevicePin(pin: Int): ByteArray {
		val buffer = ByteArray(5)
		buffer[0] = CommandCode.SET_DEVICE_PIN.toByte()
		putUInt32LE(buffer, 1, pin.toLong())
		return buffer
	}

	fun setRadio(
		frequency: Double,
		bandwidth: Double,
		spreadingFactor: Int,
		codingRate: Int,
		repeat: Int = 0,
	): ByteArray {
		val buffer = ByteArray(12)
		buffer[0] = CommandCode.SET_RADIO.toByte()
		putUInt32LE(buffer, 1, (frequency * 1000).toLong())
		putUInt32LE(buffer, 5, (bandwidth * 1000).toLong())
		buffer[9] = spreadingFactor.toByte()
		buffer[10] = codingRate.toByte()
		buffer[11] = repeat.toByte()
		return buffer
	}

	fun setTuning(rxDelay: Int, afFactor: Int): ByteArray =
		byteArrayOf(CommandCode.SET_TUNING.toByte(), rxDelay.toByte(), afFactor.toByte())

	fun reboot(): ByteArray =
		byteArrayOf(CommandCode.REBOOT.toByte())

	fun factoryReset(): ByteArray =
		byteArrayOf(CommandCode.FACTORY_RESET.toByte())

	fun setOtherParams(
		advertType: Int,
		telemetryModeBase: Int = 0,
		telemetryModeLoc: Int = 0,
		telemetryModeEnv: Int = 0,
	): ByteArray {
		val telemetryByte = (telemetryModeBase and 0x03) or
				((telemetryModeLoc and 0x03) shl 2) or
				((telemetryModeEnv and 0x03) shl 4)
		return byteArrayOf(
			CommandCode.SET_OTHER_PARAMS.toByte(),
			advertType.toByte(),
			telemetryByte.toByte(),
		)
	}

	// --- Phase 2: Auth ---

	fun sendLogin(publicKeyPrefix: ByteArray, password: String): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val pwdBytes = password.encodeToByteArray()
		val buffer = ByteArray(7 + pwdBytes.size)
		buffer[0] = CommandCode.SEND_LOGIN.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		pwdBytes.copyInto(buffer, 7)
		return buffer
	}

	fun sendLogout(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.SEND_LOGOUT.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	// --- Phase 2: Path Discovery ---

	fun sendPathDiscovery(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.SEND_PATH_DISCOVERY.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun hasConnection(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.HAS_CONNECTION.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	fun sendTrace(auth: Int, tag: Int, flags: Int, path: ByteArray = ByteArray(0)): ByteArray {
		val buffer = ByteArray(4 + path.size)
		buffer[0] = CommandCode.SEND_TRACE.toByte()
		buffer[1] = auth.toByte()
		buffer[2] = tag.toByte()
		buffer[3] = flags.toByte()
		path.copyInto(buffer, 4)
		return buffer
	}

	// --- Phase 3: Cryptography ---

	fun exportPrivateKey(): ByteArray =
		byteArrayOf(CommandCode.EXPORT_PRIVATE_KEY.toByte())

	fun importPrivateKey(key: ByteArray): ByteArray {
		require(key.size == 32) { "Private key must be 32 bytes" }
		val buffer = ByteArray(33)
		buffer[0] = CommandCode.IMPORT_PRIVATE_KEY.toByte()
		key.copyInto(buffer, 1)
		return buffer
	}

	fun signStart(): ByteArray =
		byteArrayOf(CommandCode.SIGN_START.toByte())

	fun signData(chunk: ByteArray): ByteArray {
		val buffer = ByteArray(1 + chunk.size)
		buffer[0] = CommandCode.SIGN_DATA.toByte()
		chunk.copyInto(buffer, 1)
		return buffer
	}

	fun signFinish(timeout: Int, size: Int): ByteArray {
		val buffer = ByteArray(5)
		buffer[0] = CommandCode.SIGN_FINISH.toByte()
		putUInt16LE(buffer, 1, timeout)
		putUInt16LE(buffer, 3, size)
		return buffer
	}

	// --- Phase 3: Custom Variables ---

	fun getCustomVars(): ByteArray =
		byteArrayOf(CommandCode.GET_CUSTOM_VARS.toByte())

	fun setCustomVar(key: String, value: String): ByteArray {
		val payload = "$key=$value".encodeToByteArray()
		val buffer = ByteArray(1 + payload.size)
		buffer[0] = CommandCode.SET_CUSTOM_VAR.toByte()
		payload.copyInto(buffer, 1)
		return buffer
	}

	// --- Phase 3: Telemetry ---

	fun getSelfTelemetry(): ByteArray =
		byteArrayOf(CommandCode.GET_TELEMETRY.toByte(), 0x00)

	fun sendTelemetryRequest(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(8)
		buffer[0] = CommandCode.GET_TELEMETRY.toByte()
		buffer[1] = 0x01 // remote request
		publicKeyPrefix.copyInto(buffer, 2)
		return buffer
	}

	fun sendStatusRequest(publicKeyPrefix: ByteArray): ByteArray {
		require(publicKeyPrefix.size == 6) { "Public key prefix must be 6 bytes" }
		val buffer = ByteArray(7)
		buffer[0] = CommandCode.SEND_STATUS_REQ.toByte()
		publicKeyPrefix.copyInto(buffer, 1)
		return buffer
	}

	// --- Phase 3: Control Data ---

	fun sendControlData(type: Int, payload: ByteArray): ByteArray {
		val buffer = ByteArray(2 + payload.size)
		buffer[0] = CommandCode.SEND_CONTROL_DATA.toByte()
		buffer[1] = type.toByte()
		payload.copyInto(buffer, 2)
		return buffer
	}

	// --- Phase 3: Anonymous Requests ---

	fun sendAnonymousRequest(publicKey: ByteArray, requestType: Int, payload: ByteArray = ByteArray(0)): ByteArray {
		require(publicKey.size == 32) { "Public key must be 32 bytes" }
		val buffer = ByteArray(1 + 32 + 1 + payload.size)
		buffer[0] = CommandCode.SEND_ANONYMOUS_REQ.toByte()
		publicKey.copyInto(buffer, 1)
		buffer[33] = requestType.toByte()
		payload.copyInto(buffer, 34)
		return buffer
	}

	// --- Phase 3: Config Queries ---

	fun getAllowedRepeatFreq(): ByteArray =
		byteArrayOf(CommandCode.GET_ALLOWED_REPEAT_FREQ.toByte())

	fun setPathHashMode(mode: Int): ByteArray =
		byteArrayOf(CommandCode.SET_PATH_HASH_MODE.toByte(), mode.toByte())

	// --- Byte utilities ---

	internal fun putUInt32LE(buffer: ByteArray, offset: Int, value: Long) {
		buffer[offset] = (value and 0xFF).toByte()
		buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
		buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
		buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
	}

	internal fun putInt32LE(buffer: ByteArray, offset: Int, value: Int) {
		putUInt32LE(buffer, offset, value.toLong() and 0xFFFFFFFFL)
	}

	internal fun putUInt16LE(buffer: ByteArray, offset: Int, value: Int) {
		buffer[offset] = (value and 0xFF).toByte()
		buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
	}
}
