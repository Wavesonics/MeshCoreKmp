package com.darkrockstudios.libs.meshcorekmp.protocol

object ResponseParser {

	fun parse(data: ByteArray): Response? {
		if (data.isEmpty()) return null
		val code = data[0].toInt() and 0xFF
		return when (code) {
			ResponseCode.PACKET_OK -> parseOk(data)
			ResponseCode.PACKET_ERROR -> parseError(data)
			ResponseCode.PACKET_DEVICE_INFO -> parseDeviceInfo(data)
			ResponseCode.PACKET_SELF_INFO -> parseSelfInfo(data)
			ResponseCode.PACKET_BATTERY -> parseBattery(data)
			ResponseCode.PACKET_CHANNEL_INFO -> parseChannelInfo(data)
			ResponseCode.PACKET_CONTACT_START -> Response.ContactStart
			ResponseCode.PACKET_CONTACT -> parseContact(data)
			ResponseCode.PACKET_CONTACT_END -> Response.ContactEnd
			ResponseCode.PACKET_MSG_SENT -> parseMessageSent(data)
			ResponseCode.PACKET_CHANNEL_MSG_RECV -> parseChannelMessage(data, v3 = false)
			ResponseCode.PACKET_CHANNEL_MSG_RECV_V3 -> parseChannelMessage(data, v3 = true)
			ResponseCode.PACKET_CONTACT_MSG_RECV -> parseContactMessage(data, v3 = false)
			ResponseCode.PACKET_CONTACT_MSG_RECV_V3 -> parseContactMessage(data, v3 = true)
			ResponseCode.PACKET_NO_MORE_MSGS -> Response.NoMoreMessages
			ResponseCode.PACKET_MESSAGES_WAITING -> parseMessagesWaiting(data)
			ResponseCode.PACKET_ACK -> parseAck(data)
			ResponseCode.PACKET_ADVERTISEMENT -> parseAdvertisement(data)
			ResponseCode.PACKET_CURRENT_TIME -> parseCurrentTime(data)
			ResponseCode.RESP_CODE_STATS -> parseStats(data)
			ResponseCode.PUSH_CODE_RAW_DATA -> parseRawData(data)
			ResponseCode.PACKET_LOG_DATA -> Response.LogData(data.copyOfRange(1, data.size))
			ResponseCode.PUSH_CODE_BINARY_RESPONSE -> parseBinaryResponse(data)
			else -> null
		}
	}

	private fun parseOk(data: ByteArray): Response.Ok {
		val value = if (data.size >= 5) getUInt32LE(data, 1).toInt() else null
		return Response.Ok(value)
	}

	private fun parseError(data: ByteArray): Response.Error {
		val code = if (data.size >= 2) data[1].toInt() and 0xFF else 0
		return Response.Error(code)
	}

	private fun parseDeviceInfo(data: ByteArray): Response.DeviceInfo? {
		if (data.size < 2) return null
		val fwVer = data[1].toInt() and 0xFF

		if (fwVer >= 3 && data.size >= 80) {
			val maxContacts = (data[2].toInt() and 0xFF) * 2
			val maxChannels = data[3].toInt() and 0xFF
			val blePin = getUInt32LE(data, 4).toInt()
			val fwBuild = extractString(data, 8, 12)
			val model = extractString(data, 20, 40)
			val version = extractString(data, 60, 20)
			return Response.DeviceInfo(
				firmwareVersion = fwVer,
				maxContacts = maxContacts,
				maxChannels = maxChannels,
				blePin = blePin,
				firmwareBuild = fwBuild,
				model = model,
				version = version,
			)
		}

		return Response.DeviceInfo(
			firmwareVersion = fwVer,
			maxContacts = 0,
			maxChannels = 0,
			blePin = 0,
			firmwareBuild = "",
			model = "",
			version = "",
		)
	}

	private fun parseSelfInfo(data: ByteArray): Response.SelfInfo? {
		if (data.size < 58) return null

		val advType = data[1].toInt() and 0xFF
		val txPower = data[2].toInt() // signed
		val maxTxPower = data[3].toInt() // signed
		val publicKey = data.copyOfRange(4, 36).toHexString()

		val lat = getInt32LE(data, 36) / 1_000_000.0
		val lon = getInt32LE(data, 40) / 1_000_000.0
		val advLat = if (lat != 0.0) lat else null
		val advLon = if (lon != 0.0) lon else null

		val multiAcks = data[44].toInt() and 0xFF
		val advLocPolicy = data[45].toInt() and 0xFF
		val telemetryByte = data[46].toInt() and 0xFF
		val telemetryModeBase = telemetryByte and 0x03
		val telemetryModeLoc = (telemetryByte shr 2) and 0x03
		val telemetryModeEnv = (telemetryByte shr 4) and 0x03
		val manualAddContacts = (data[47].toInt() and 0xFF) > 0

		val radioFreq = getUInt32LE(data, 48) / 1000.0
		val radioBw = getUInt32LE(data, 52) / 1000.0
		val radioSf = data[56].toInt() and 0xFF
		val radioCr = data[57].toInt() and 0xFF

		val deviceName = if (data.size > 58) {
			extractNullTerminatedString(data, 58)
		} else ""

		return Response.SelfInfo(
			advertisementType = advType,
			txPower = txPower,
			maxTxPower = maxTxPower,
			publicKey = publicKey,
			advertisementLatitude = advLat,
			advertisementLongitude = advLon,
			multiAcks = multiAcks,
			advertisementLocationPolicy = advLocPolicy,
			telemetryModeBase = telemetryModeBase,
			telemetryModeLoc = telemetryModeLoc,
			telemetryModeEnv = telemetryModeEnv,
			manualAddContacts = manualAddContacts,
			radioFrequency = radioFreq,
			radioBandwidth = radioBw,
			radioSpreadingFactor = radioSf,
			radioCodingRate = radioCr,
			deviceName = deviceName,
		)
	}

	private fun parseBattery(data: ByteArray): Response.Battery? {
		if (data.size < 3) return null
		val level = getUInt16LE(data, 1)
		val usedKb = if (data.size >= 7) getUInt32LE(data, 3).toInt() else null
		val totalKb = if (data.size >= 11) getUInt32LE(data, 7).toInt() else null
		return Response.Battery(
			levelPercent = level,
			usedStorageKb = usedKb,
			totalStorageKb = totalKb,
		)
	}

	private fun parseChannelInfo(data: ByteArray): Response.ChannelInfo? {
		if (data.size < 2) return null
		val index = data[1].toInt() and 0xFF
		val name = if (data.size >= 34) extractString(data, 2, 32) else ""
		return Response.ChannelInfo(index = index, name = name)
	}

	private fun parseContact(data: ByteArray): Response.Contact {
		return Response.Contact(rawData = data.copyOfRange(1, data.size))
	}

	private fun parseMessageSent(data: ByteArray): Response.MessageSent? {
		if (data.size < 10) return null
		val msgType = data[1].toInt() and 0xFF
		val expectedAck = data.copyOfRange(2, 6).toHexString()
		val suggestedTimeout = getUInt32LE(data, 6).toInt()
		return Response.MessageSent(
			messageType = msgType,
			expectedAck = expectedAck,
			suggestedTimeoutSeconds = suggestedTimeout,
		)
	}

	private fun parseChannelMessage(data: ByteArray, v3: Boolean): Response.ChannelMessageReceived? {
		var offset = 1
		var snr: Float? = null

		if (v3) {
			if (data.size < 12) return null
			val snrByte = data[offset].toInt() // signed byte
			snr = snrByte / 4.0f
			offset += 3 // skip SNR + 2 reserved bytes
		} else {
			if (data.size < 8) return null
		}

		val channelIndex = data[offset].toInt() and 0xFF
		val pathLength = data[offset + 1].toInt() and 0xFF
		val textType = data[offset + 2].toInt() and 0xFF
		val timestamp = getUInt32LE(data, offset + 3)
		offset += 7

		val text = data.decodeToString(offset, data.size)

		return Response.ChannelMessageReceived(
			channelIndex = channelIndex,
			pathLength = pathLength,
			textType = textType,
			timestamp = timestamp,
			text = text,
			snr = snr,
		)
	}

	private fun parseContactMessage(data: ByteArray, v3: Boolean): Response.ContactMessageReceived? {
		var offset = 1
		var snr: Float? = null

		if (v3) {
			if (data.size < 20) return null
			val snrByte = data[offset].toInt() // signed byte
			snr = snrByte / 4.0f
			offset += 3 // skip SNR + 2 reserved bytes
		} else {
			if (data.size < 13) return null
		}

		val pubKeyPrefix = data.copyOfRange(offset, offset + 6).toHexString()
		offset += 6

		val pathLength = data[offset].toInt() and 0xFF
		val textType = data[offset + 1].toInt() and 0xFF
		offset += 2

		val timestamp = getUInt32LE(data, offset)
		offset += 4

		val signature = if (textType == 2 && data.size > offset + 4) {
			val sig = data.copyOfRange(offset, offset + 4).toHexString()
			offset += 4
			sig
		} else null

		val text = data.decodeToString(offset, data.size)

		return Response.ContactMessageReceived(
			publicKeyPrefix = pubKeyPrefix,
			pathLength = pathLength,
			textType = textType,
			timestamp = timestamp,
			signature = signature,
			text = text,
			snr = snr,
		)
	}

	private fun parseMessagesWaiting(data: ByteArray): Response.MessagesWaiting {
		val count = if (data.size >= 2) data[1].toInt() and 0xFF else 1
		return Response.MessagesWaiting(count)
	}

	private fun parseAck(data: ByteArray): Response.Ack? {
		if (data.size < 7) return null
		val ackCode = data.copyOfRange(1, 7).toHexString()
		return Response.Ack(ackCode)
	}

	private fun parseAdvertisement(data: ByteArray): Response.AdvertisementReceived {
		return Response.AdvertisementReceived(data.copyOfRange(1, data.size))
	}

	private fun parseCurrentTime(data: ByteArray): Response.CurrentTime? {
		if (data.size < 5) return null
		val timestamp = getUInt32LE(data, 1)
		return Response.CurrentTime(timestamp)
	}

	private fun parseStats(data: ByteArray): Response.Stats? {
		if (data.size < 2) return null
		val subType = data[1].toInt() and 0xFF
		return when (subType) {
			0 -> parseCoreStats(data)
			1 -> parseRadioStats(data)
			2 -> parsePacketStats(data)
			else -> null
		}
	}

	private fun parseCoreStats(data: ByteArray): Response.Stats.Core? {
		if (data.size < 11) return null
		return Response.Stats.Core(
			batteryMillivolts = getUInt16LE(data, 2),
			uptimeSeconds = getUInt32LE(data, 4),
			errors = getUInt16LE(data, 8),
			queueLength = data[10].toInt() and 0xFF,
		)
	}

	private fun parseRadioStats(data: ByteArray): Response.Stats.Radio? {
		if (data.size < 14) return null
		return Response.Stats.Radio(
			noiseFloorDbm = getInt16LE(data, 2),
			lastRssiDbm = data[4].toInt(), // signed
			lastSnrDb = data[5].toInt() / 4.0f, // signed, scaled by 4
			txAirtimeSeconds = getUInt32LE(data, 6),
			rxAirtimeSeconds = getUInt32LE(data, 10),
		)
	}

	private fun parsePacketStats(data: ByteArray): Response.Stats.Packets? {
		if (data.size < 26) return null
		return Response.Stats.Packets(
			received = getUInt32LE(data, 2),
			sent = getUInt32LE(data, 6),
			floodTx = getUInt32LE(data, 10),
			directTx = getUInt32LE(data, 14),
			floodRx = getUInt32LE(data, 18),
			directRx = getUInt32LE(data, 22),
			recvErrors = if (data.size >= 30) getUInt32LE(data, 26) else null,
		)
	}

	private fun parseRawData(data: ByteArray): Response.RawDataReceived? {
		if (data.size < 4) return null
		val snr = data[1].toInt() / 4.0f // signed byte, scaled by 4
		val rssi = data[2].toInt() // signed byte
		// data[3] is reserved (0xFF)
		val payload = if (data.size > 4) data.copyOfRange(4, data.size) else ByteArray(0)
		return Response.RawDataReceived(snr = snr, rssi = rssi, payload = payload)
	}

	private fun parseBinaryResponse(data: ByteArray): Response.BinaryResponse? {
		if (data.size < 6) return null
		// data[1] is reserved (0x00)
		val tag = getUInt32LE(data, 2)
		val responseData = if (data.size > 6) data.copyOfRange(6, data.size) else ByteArray(0)
		return Response.BinaryResponse(tag = tag, responseData = responseData)
	}

	// --- Byte utilities ---

	internal fun getUInt16LE(data: ByteArray, offset: Int): Int =
		(data[offset].toInt() and 0xFF) or
			((data[offset + 1].toInt() and 0xFF) shl 8)

	internal fun getInt16LE(data: ByteArray, offset: Int): Int {
		val unsigned = getUInt16LE(data, offset)
		return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
	}

	internal fun getUInt32LE(data: ByteArray, offset: Int): Long =
		(data[offset].toLong() and 0xFF) or
			((data[offset + 1].toLong() and 0xFF) shl 8) or
			((data[offset + 2].toLong() and 0xFF) shl 16) or
			((data[offset + 3].toLong() and 0xFF) shl 24)

	internal fun getInt32LE(data: ByteArray, offset: Int): Int {
		val unsigned = getUInt32LE(data, offset)
		return unsigned.toInt() // automatically wraps for signed interpretation
	}

	internal fun extractString(data: ByteArray, offset: Int, maxLength: Int): String {
		val end = minOf(offset + maxLength, data.size)
		val bytes = data.copyOfRange(offset, end)
		val nullIndex = bytes.indexOf(0)
		val trimmed = if (nullIndex >= 0) bytes.copyOfRange(0, nullIndex) else bytes
		return trimmed.decodeToString().trim()
	}

	internal fun extractNullTerminatedString(data: ByteArray, offset: Int): String {
		val nullIndex = data.indexOf(0, offset)
		val end = if (nullIndex >= 0) nullIndex else data.size
		return data.decodeToString(offset, end).trim()
	}

	private fun ByteArray.indexOf(byte: Byte, startIndex: Int = 0): Int {
		for (i in startIndex until size) {
			if (this[i] == byte) return i
		}
		return -1
	}

	private fun ByteArray.toHexString(): String =
		joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
