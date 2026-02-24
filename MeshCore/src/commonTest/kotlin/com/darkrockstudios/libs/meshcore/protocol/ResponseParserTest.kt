package com.darkrockstudios.libs.meshcore.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ResponseParserTest {

	@Test
	fun parse_emptyData_returnsNull() {
		assertNull(ResponseParser.parse(byteArrayOf()))
	}

	@Test
	fun parse_unknownCode_returnsNull() {
		assertNull(ResponseParser.parse(byteArrayOf(0x7F)))
	}

	@Test
	fun parse_packetOk_noValue() {
		val data = byteArrayOf(0x00)
		val result = ResponseParser.parse(data)
		assertIs<Response.Ok>(result)
		assertNull(result.value)
	}

	@Test
	fun parse_packetOk_withValue() {
		// Value = 42 = 0x0000002A in LE
		val data = byteArrayOf(0x00, 0x2A, 0x00, 0x00, 0x00)
		val result = ResponseParser.parse(data)
		assertIs<Response.Ok>(result)
		assertEquals(42, result.value)
	}

	@Test
	fun parse_packetError_withCode() {
		val data = byteArrayOf(0x01, 0x03) // Channel not found
		val result = ResponseParser.parse(data)
		assertIs<Response.Error>(result)
		assertEquals(ErrorCode.CHANNEL_NOT_FOUND, result.code)
	}

	@Test
	fun parse_packetError_noCode() {
		val data = byteArrayOf(0x01)
		val result = ResponseParser.parse(data)
		assertIs<Response.Error>(result)
		assertEquals(0, result.code)
	}

	@Test
	fun parse_battery_basicLevel() {
		// Battery at 75%: 0x0C, 4B 00 (75 in LE uint16)
		val data = byteArrayOf(0x0C, 0x4B, 0x00)
		val result = ResponseParser.parse(data)
		assertIs<Response.Battery>(result)
		assertEquals(75, result.levelPercent)
		assertNull(result.usedStorageKb)
		assertNull(result.totalStorageKb)
	}

	@Test
	fun parse_battery_withStorage() {
		val data = ByteArray(11)
		data[0] = 0x0C
		// Battery = 100 (0x0064)
		data[1] = 0x64
		data[2] = 0x00
		// Used storage = 1024 KB (0x00000400)
		data[3] = 0x00
		data[4] = 0x04
		data[5] = 0x00
		data[6] = 0x00
		// Total storage = 8192 KB (0x00002000)
		data[7] = 0x00
		data[8] = 0x20.toByte()
		data[9] = 0x00
		data[10] = 0x00
		val result = ResponseParser.parse(data)
		assertIs<Response.Battery>(result)
		assertEquals(100, result.levelPercent)
		assertEquals(1024, result.usedStorageKb)
		assertEquals(8192, result.totalStorageKb)
	}

	@Test
	fun parse_deviceInfo_v3() {
		val data = ByteArray(80)
		data[0] = 0x0D
		data[1] = 0x03 // firmware version 3
		data[2] = 0x08 // max contacts raw = 8, actual = 16
		data[3] = 0x08 // max channels = 8
		// BLE PIN = 1234 (0x000004D2) in LE
		data[4] = 0xD2.toByte()
		data[5] = 0x04
		data[6] = 0x00
		data[7] = 0x00
		// Firmware build at offset 8-19 = "1.0.0"
		"1.0.0".encodeToByteArray().copyInto(data, 8)
		// Model at offset 20-59 = "TestRadio"
		"TestRadio".encodeToByteArray().copyInto(data, 20)
		// Version at offset 60-79 = "v1.2.3"
		"v1.2.3".encodeToByteArray().copyInto(data, 60)

		val result = ResponseParser.parse(data)
		assertIs<Response.DeviceInfo>(result)
		assertEquals(3, result.firmwareVersion)
		assertEquals(16, result.maxContacts)
		assertEquals(8, result.maxChannels)
		assertEquals(1234, result.blePin)
		assertEquals("1.0.0", result.firmwareBuild)
		assertEquals("TestRadio", result.model)
		assertEquals("v1.2.3", result.version)
	}

	@Test
	fun parse_deviceInfo_legacyVersion() {
		val data = byteArrayOf(0x0D, 0x01)
		val result = ResponseParser.parse(data)
		assertIs<Response.DeviceInfo>(result)
		assertEquals(1, result.firmwareVersion)
		assertEquals(0, result.maxContacts)
		assertEquals("", result.model)
	}

	@Test
	fun parse_channelInfo() {
		val data = ByteArray(34)
		data[0] = 0x12
		data[1] = 0x02 // channel index 2
		"General".encodeToByteArray().copyInto(data, 2)

		val result = ResponseParser.parse(data)
		assertIs<Response.ChannelInfo>(result)
		assertEquals(2, result.index)
		assertEquals("General", result.name)
	}

	@Test
	fun parse_messageSent() {
		val data = ByteArray(10)
		data[0] = 0x06
		data[1] = 0x01 // message type
		// Expected ACK = "aabbccdd"
		data[2] = 0xAA.toByte()
		data[3] = 0xBB.toByte()
		data[4] = 0xCC.toByte()
		data[5] = 0xDD.toByte()
		// Timeout = 30 seconds (0x1E000000 LE)
		data[6] = 0x1E
		data[7] = 0x00
		data[8] = 0x00
		data[9] = 0x00

		val result = ResponseParser.parse(data)
		assertIs<Response.MessageSent>(result)
		assertEquals(1, result.messageType)
		assertEquals("aabbccdd", result.expectedAck)
		assertEquals(30, result.suggestedTimeoutSeconds)
	}

	@Test
	fun parse_channelMessage_standard() {
		val text = "Hello mesh!"
		val textBytes = text.encodeToByteArray()
		val data = ByteArray(8 + textBytes.size)
		data[0] = 0x08
		data[1] = 0x01 // channel index 1
		data[2] = 0x02 // path length 2
		data[3] = 0x00 // text type 0 (plain)
		// Timestamp = 1000 (0x000003E8) in LE
		data[4] = 0xE8.toByte()
		data[5] = 0x03
		data[6] = 0x00
		data[7] = 0x00
		textBytes.copyInto(data, 8)

		val result = ResponseParser.parse(data)
		assertIs<Response.ChannelMessageReceived>(result)
		assertEquals(1, result.channelIndex)
		assertEquals(2, result.pathLength)
		assertEquals(0, result.textType)
		assertEquals(1000L, result.timestamp)
		assertEquals("Hello mesh!", result.text)
		assertNull(result.snr)
	}

	@Test
	fun parse_channelMessage_v3() {
		val text = "Hi"
		val textBytes = text.encodeToByteArray()
		val data = ByteArray(11 + textBytes.size)
		data[0] = 0x11
		data[1] = 40 // SNR raw = 40, actual = 10.0 dB
		data[2] = 0x00 // reserved
		data[3] = 0x00 // reserved
		data[4] = 0x03 // channel index 3
		data[5] = 0x01 // path length 1
		data[6] = 0x00 // text type 0
		// Timestamp = 2000 (0x000007D0) in LE
		data[7] = 0xD0.toByte()
		data[8] = 0x07
		data[9] = 0x00
		data[10] = 0x00
		textBytes.copyInto(data, 11)

		val result = ResponseParser.parse(data)
		assertIs<Response.ChannelMessageReceived>(result)
		assertEquals(3, result.channelIndex)
		assertEquals(1, result.pathLength)
		assertEquals(2000L, result.timestamp)
		assertEquals("Hi", result.text)
		assertEquals(10.0f, result.snr)
	}

	@Test
	fun parse_contactMessage_standard() {
		val text = "Hey"
		val textBytes = text.encodeToByteArray()
		val data = ByteArray(13 + textBytes.size)
		data[0] = 0x07
		// Public key prefix = 01 02 03 04 05 06
		data[1] = 0x01; data[2] = 0x02; data[3] = 0x03
		data[4] = 0x04; data[5] = 0x05; data[6] = 0x06
		data[7] = 0x03 // path length
		data[8] = 0x00 // text type (plain)
		// Timestamp = 500 (0x000001F4) in LE
		data[9] = 0xF4.toByte()
		data[10] = 0x01
		data[11] = 0x00
		data[12] = 0x00
		textBytes.copyInto(data, 13)

		val result = ResponseParser.parse(data)
		assertIs<Response.ContactMessageReceived>(result)
		assertEquals("010203040506", result.publicKeyPrefix)
		assertEquals(3, result.pathLength)
		assertEquals(0, result.textType)
		assertEquals(500L, result.timestamp)
		assertNull(result.signature)
		assertEquals("Hey", result.text)
		assertNull(result.snr)
	}

	@Test
	fun parse_contactMessage_v3_signed() {
		val text = "Signed"
		val textBytes = text.encodeToByteArray()
		val data = ByteArray(20 + textBytes.size)
		data[0] = 0x10
		data[1] = (-20).toByte() // SNR raw = -20, actual = -5.0 dB
		data[2] = 0x00 // reserved
		data[3] = 0x00 // reserved
		// Public key prefix
		data[4] = 0xAA.toByte(); data[5] = 0xBB.toByte(); data[6] = 0xCC.toByte()
		data[7] = 0xDD.toByte(); data[8] = 0xEE.toByte(); data[9] = 0xFF.toByte()
		data[10] = 0x01 // path length
		data[11] = 0x02 // text type = signed
		// Timestamp = 999 (0x000003E7) in LE
		data[12] = 0xE7.toByte()
		data[13] = 0x03
		data[14] = 0x00
		data[15] = 0x00
		// Signature = 11 22 33 44
		data[16] = 0x11; data[17] = 0x22; data[18] = 0x33; data[19] = 0x44
		textBytes.copyInto(data, 20)

		val result = ResponseParser.parse(data)
		assertIs<Response.ContactMessageReceived>(result)
		assertEquals("aabbccddeeff", result.publicKeyPrefix)
		assertEquals(-5.0f, result.snr)
		assertEquals(2, result.textType)
		assertEquals("11223344", result.signature)
		assertEquals("Signed", result.text)
	}

	@Test
	fun parse_noMoreMessages() {
		val data = byteArrayOf(0x0A)
		val result = ResponseParser.parse(data)
		assertIs<Response.NoMoreMessages>(result)
	}

	@Test
	fun parse_messagesWaiting() {
		val data = byteArrayOf(0x83.toByte(), 0x05)
		val result = ResponseParser.parse(data)
		assertIs<Response.MessagesWaiting>(result)
		assertEquals(5, result.count)
	}

	@Test
	fun parse_ack() {
		val data = byteArrayOf(0x82.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = ResponseParser.parse(data)
		assertIs<Response.Ack>(result)
		assertEquals("010203040506", result.ackCode)
	}

	@Test
	fun parse_contactStart() {
		val data = byteArrayOf(0x02)
		val result = ResponseParser.parse(data)
		assertIs<Response.ContactStart>(result)
	}

	@Test
	fun parse_contactEnd() {
		val data = byteArrayOf(0x04)
		val result = ResponseParser.parse(data)
		assertIs<Response.ContactEnd>(result)
	}

	@Test
	fun parse_coreStats() {
		val data = ByteArray(11)
		data[0] = 0x18
		data[1] = 0x00 // STATS_TYPE_CORE
		// Battery = 3700 mV (0x0E74) in LE
		data[2] = 0x74
		data[3] = 0x0E
		// Uptime = 3600 seconds (0x00000E10) in LE
		data[4] = 0x10
		data[5] = 0x0E
		data[6] = 0x00
		data[7] = 0x00
		// Errors = 0
		data[8] = 0x00
		data[9] = 0x00
		// Queue length = 3
		data[10] = 0x03

		val result = ResponseParser.parse(data)
		assertIs<Response.Stats.Core>(result)
		assertEquals(3700, result.batteryMillivolts)
		assertEquals(3600L, result.uptimeSeconds)
		assertEquals(0, result.errors)
		assertEquals(3, result.queueLength)
	}

	@Test
	fun parse_radioStats() {
		val data = ByteArray(14)
		data[0] = 0x18
		data[1] = 0x01 // STATS_TYPE_RADIO
		// Noise floor = -120 dBm (0xFF88) in LE
		data[2] = 0x88.toByte()
		data[3] = 0xFF.toByte()
		// Last RSSI = -80 (0xB0)
		data[4] = (-80).toByte()
		// Last SNR = 24 (24/4.0 = 6.0 dB)
		data[5] = 24
		// TX airtime = 100
		data[6] = 0x64; data[7] = 0x00; data[8] = 0x00; data[9] = 0x00
		// RX airtime = 200
		data[10] = 0xC8.toByte(); data[11] = 0x00; data[12] = 0x00; data[13] = 0x00

		val result = ResponseParser.parse(data)
		assertIs<Response.Stats.Radio>(result)
		assertEquals(-120, result.noiseFloorDbm)
		assertEquals(-80, result.lastRssiDbm)
		assertEquals(6.0f, result.lastSnrDb)
		assertEquals(100L, result.txAirtimeSeconds)
		assertEquals(200L, result.rxAirtimeSeconds)
	}

	@Test
	fun parse_packetStats_legacy() {
		val data = ByteArray(26)
		data[0] = 0x18
		data[1] = 0x02 // STATS_TYPE_PACKETS
		// recv = 1000
		putUInt32LE(data, 2, 1000)
		// sent = 500
		putUInt32LE(data, 6, 500)
		// flood_tx = 300
		putUInt32LE(data, 10, 300)
		// direct_tx = 200
		putUInt32LE(data, 14, 200)
		// flood_rx = 600
		putUInt32LE(data, 18, 600)
		// direct_rx = 400
		putUInt32LE(data, 22, 400)

		val result = ResponseParser.parse(data)
		assertIs<Response.Stats.Packets>(result)
		assertEquals(1000L, result.received)
		assertEquals(500L, result.sent)
		assertEquals(300L, result.floodTx)
		assertEquals(200L, result.directTx)
		assertEquals(600L, result.floodRx)
		assertEquals(400L, result.directRx)
		assertNull(result.recvErrors)
	}

	@Test
	fun parse_packetStats_extended() {
		val data = ByteArray(30)
		data[0] = 0x18
		data[1] = 0x02
		putUInt32LE(data, 2, 100)
		putUInt32LE(data, 6, 50)
		putUInt32LE(data, 10, 30)
		putUInt32LE(data, 14, 20)
		putUInt32LE(data, 18, 60)
		putUInt32LE(data, 22, 40)
		putUInt32LE(data, 26, 5) // recv_errors

		val result = ResponseParser.parse(data)
		assertIs<Response.Stats.Packets>(result)
		assertEquals(5L, result.recvErrors)
	}

	@Test
	fun parse_rawDataReceived() {
		// [0x84][snr*4: i8][rssi: i8][0xFF][payload...]
		val data = byteArrayOf(
			0x84.toByte(),
			40,                  // SNR raw = 40, actual = 10.0 dB
			(-80).toByte(),      // RSSI = -80
			0xFF.toByte(),       // reserved
			0x01, 0x02, 0x03,    // payload
		)
		val result = ResponseParser.parse(data)
		assertIs<Response.RawDataReceived>(result)
		assertEquals(10.0f, result.snr)
		assertEquals(-80, result.rssi)
		assertEquals(3, result.payload.size)
		assertEquals(0x01.toByte(), result.payload[0])
		assertEquals(0x02.toByte(), result.payload[1])
		assertEquals(0x03.toByte(), result.payload[2])
	}

	@Test
	fun parse_rawDataReceived_negativeSnr() {
		val data = byteArrayOf(
			0x84.toByte(),
			(-20).toByte(),      // SNR raw = -20, actual = -5.0 dB
			(-90).toByte(),      // RSSI = -90
			0xFF.toByte(),       // reserved
			0xAA.toByte(),       // payload
		)
		val result = ResponseParser.parse(data)
		assertIs<Response.RawDataReceived>(result)
		assertEquals(-5.0f, result.snr)
		assertEquals(-90, result.rssi)
		assertEquals(1, result.payload.size)
	}

	@Test
	fun parse_rawDataReceived_tooShort() {
		val data = byteArrayOf(0x84.toByte(), 0x00, 0x00)
		assertNull(ResponseParser.parse(data))
	}

	@Test
	fun parse_rawDataReceived_emptyPayload() {
		val data = byteArrayOf(0x84.toByte(), 0x00, 0x00, 0xFF.toByte())
		val result = ResponseParser.parse(data)
		assertIs<Response.RawDataReceived>(result)
		assertEquals(0, result.payload.size)
	}

	@Test
	fun parse_binaryResponse() {
		// [0x8C][0x00][tag: u32 LE][response_data...]
		val data = ByteArray(10)
		data[0] = 0x8C.toByte()
		data[1] = 0x00 // reserved
		// tag = 12345 (0x00003039) in LE
		putUInt32LE(data, 2, 12345)
		// response data
		data[6] = 0xDE.toByte()
		data[7] = 0xAD.toByte()
		data[8] = 0xBE.toByte()
		data[9] = 0xEF.toByte()
		val result = ResponseParser.parse(data)
		assertIs<Response.BinaryResponse>(result)
		assertEquals(12345L, result.tag)
		assertEquals(4, result.responseData.size)
		assertEquals(0xDE.toByte(), result.responseData[0])
		assertEquals(0xAD.toByte(), result.responseData[1])
		assertEquals(0xBE.toByte(), result.responseData[2])
		assertEquals(0xEF.toByte(), result.responseData[3])
	}

	@Test
	fun parse_binaryResponse_tooShort() {
		val data = byteArrayOf(0x8C.toByte(), 0x00, 0x01, 0x00, 0x00)
		assertNull(ResponseParser.parse(data))
	}

	@Test
	fun parse_binaryResponse_emptyData() {
		val data = ByteArray(6)
		data[0] = 0x8C.toByte()
		data[1] = 0x00
		putUInt32LE(data, 2, 42)
		val result = ResponseParser.parse(data)
		assertIs<Response.BinaryResponse>(result)
		assertEquals(42L, result.tag)
		assertEquals(0, result.responseData.size)
	}

	@Test
	fun parse_logData() {
		val data = byteArrayOf(0x88.toByte(), 0x01, 0x02, 0x03)
		val result = ResponseParser.parse(data)
		assertIs<Response.LogData>(result)
		assertEquals(3, result.rawData.size)
	}

	// Helper to write uint32 LE for test data
	private fun putUInt32LE(buffer: ByteArray, offset: Int, value: Long) {
		buffer[offset] = (value and 0xFF).toByte()
		buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
		buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
		buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
	}
}
