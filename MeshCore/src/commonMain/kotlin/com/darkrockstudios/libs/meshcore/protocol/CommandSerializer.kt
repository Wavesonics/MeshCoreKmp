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

	internal fun putUInt32LE(buffer: ByteArray, offset: Int, value: Long) {
		buffer[offset] = (value and 0xFF).toByte()
		buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
		buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
		buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
	}
}
