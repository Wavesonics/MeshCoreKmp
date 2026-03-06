package com.darkrockstudios.libs.meshcore.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommandSerializerTest {

	@Test
	fun appStart_defaultName() {
		val result = CommandSerializer.appStart()
		// 8 header bytes + 5 name bytes ("mccli")
		assertEquals(13, result.size)
		assertEquals(0x01.toByte(), result[0]) // APP_START
		assertEquals(0x03.toByte(), result[1]) // protocol version
		// Bytes 2-7: reserved (zeros)
		for (i in 2..7) {
			assertEquals(0x00.toByte(), result[i])
		}
		// "mccli" = 6d 63 63 6c 69 starting at byte 8
		assertEquals(0x6d.toByte(), result[8])
		assertEquals(0x63.toByte(), result[9])
		assertEquals(0x63.toByte(), result[10])
		assertEquals(0x6c.toByte(), result[11])
		assertEquals(0x69.toByte(), result[12])
	}

	@Test
	fun appStart_customName() {
		val result = CommandSerializer.appStart("test")
		// 8 header bytes + 4 name bytes ("test")
		assertEquals(12, result.size)
		assertEquals(0x01.toByte(), result[0])
		assertEquals(0x03.toByte(), result[1])
		// Bytes 2-7: reserved (zeros)
		for (i in 2..7) {
			assertEquals(0x00.toByte(), result[i])
		}
		// "test" = 74 65 73 74 starting at byte 8
		assertEquals(0x74.toByte(), result[8])
		assertEquals(0x65.toByte(), result[9])
		assertEquals(0x73.toByte(), result[10])
		assertEquals(0x74.toByte(), result[11])
	}

	@Test
	fun appStart_longNameIncluded() {
		val result = CommandSerializer.appStart("verylongname")
		// 8 header bytes + 12 name bytes
		assertEquals(20, result.size)
		assertEquals(0x01.toByte(), result[0])
		assertEquals(0x03.toByte(), result[1])
		// Bytes 2-7: reserved (zeros)
		for (i in 2..7) {
			assertEquals(0x00.toByte(), result[i])
		}
		// Full name starts at byte 8
		val name = result.copyOfRange(8, 20).decodeToString()
		assertEquals("verylongname", name)
	}

	@Test
	fun deviceQuery() {
		val result = CommandSerializer.deviceQuery()
		assertContentEquals(byteArrayOf(0x16, 0x03), result)
	}

	@Test
	fun getChannel_validIndex() {
		val result = CommandSerializer.getChannel(3)
		assertContentEquals(byteArrayOf(0x1F, 0x03), result)
	}

	@Test
	fun getChannel_invalidIndex() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.getChannel(8)
		}
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.getChannel(-1)
		}
	}

	@Test
	fun setChannel_correctSize() {
		val secret = ByteArray(32) { it.toByte() }
		val result = CommandSerializer.setChannel(1, "TestChannel", secret)
		assertEquals(66, result.size)
		assertEquals(0x20.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
		// Channel name starts at offset 2
		assertEquals('T'.code.toByte(), result[2])
		assertEquals('e'.code.toByte(), result[3])
		// Secret starts at offset 34
		assertEquals(0x00.toByte(), result[34])
		assertEquals(0x01.toByte(), result[35])
		assertEquals(0x1F.toByte(), result[65])
	}

	@Test
	fun setChannel_invalidSecret() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.setChannel(1, "Test", ByteArray(16))
		}
	}

	@Test
	fun deleteChannel() {
		val result = CommandSerializer.deleteChannel(2)
		assertEquals(66, result.size)
		assertEquals(0x20.toByte(), result[0])
		assertEquals(0x02.toByte(), result[1])
		// Name should be all zeros
		for (i in 2..33) {
			assertEquals(0x00.toByte(), result[i])
		}
		// Secret should be all zeros
		for (i in 34..65) {
			assertEquals(0x00.toByte(), result[i])
		}
	}

	@Test
	fun sendChannelMessage() {
		val result = CommandSerializer.sendChannelMessage(1, "Hello", 1234567890L)
		assertEquals(12, result.size) // 7 header + 5 text bytes
		assertEquals(0x03.toByte(), result[0])
		assertEquals(0x00.toByte(), result[1])
		assertEquals(0x01.toByte(), result[2])
		// Timestamp 1234567890 = 0x499602D2 in LE: D2 02 96 49
		assertEquals(0xD2.toByte(), result[3])
		assertEquals(0x02.toByte(), result[4])
		assertEquals(0x96.toByte(), result[5])
		assertEquals(0x49.toByte(), result[6])
		// "Hello" = 48 65 6C 6C 6F
		assertEquals(0x48.toByte(), result[7])
		assertEquals(0x65.toByte(), result[8])
		assertEquals(0x6C.toByte(), result[9])
		assertEquals(0x6C.toByte(), result[10])
		assertEquals(0x6F.toByte(), result[11])
	}

	@Test
	fun getMessage() {
		val result = CommandSerializer.getMessage()
		assertContentEquals(byteArrayOf(0x0A), result)
	}

	@Test
	fun getBattery() {
		val result = CommandSerializer.getBattery()
		assertContentEquals(byteArrayOf(0x14), result)
	}

	@Test
	fun getStats_core() {
		val result = CommandSerializer.getStats(0)
		assertContentEquals(byteArrayOf(0x38, 0x00), result)
	}

	@Test
	fun getStats_radio() {
		val result = CommandSerializer.getStats(1)
		assertContentEquals(byteArrayOf(0x38, 0x01), result)
	}

	@Test
	fun getStats_packets() {
		val result = CommandSerializer.getStats(2)
		assertContentEquals(byteArrayOf(0x38, 0x02), result)
	}

	@Test
	fun getStats_invalidSubType() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.getStats(3)
		}
	}

	@Test
	fun sendRawData_emptyPath() {
		val payload = byteArrayOf(0x01, 0x02, 0x03)
		val result = CommandSerializer.sendRawData(ByteArray(0), payload)
		assertEquals(5, result.size) // 1 cmd + 1 path_len + 0 path + 3 payload
		assertEquals(0x19.toByte(), result[0])
		assertEquals(0x00.toByte(), result[1]) // path_len = 0
		assertEquals(0x01.toByte(), result[2])
		assertEquals(0x02.toByte(), result[3])
		assertEquals(0x03.toByte(), result[4])
	}

	@Test
	fun sendRawData_withPath() {
		val path = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
		val payload = byteArrayOf(0x01, 0x02)
		val result = CommandSerializer.sendRawData(path, payload)
		assertEquals(6, result.size) // 1 cmd + 1 path_len + 2 path + 2 payload
		assertEquals(0x19.toByte(), result[0])
		assertEquals(0x02.toByte(), result[1]) // path_len = 2
		assertEquals(0xAA.toByte(), result[2])
		assertEquals(0xBB.toByte(), result[3])
		assertEquals(0x01.toByte(), result[4])
		assertEquals(0x02.toByte(), result[5])
	}

	@Test
	fun sendBinaryRequest_correctLayout() {
		val publicKey = ByteArray(32) { it.toByte() }
		val requestData = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
		val result = CommandSerializer.sendBinaryRequest(publicKey, requestData)
		assertEquals(35, result.size) // 1 cmd + 32 key + 2 data
		assertEquals(0x32.toByte(), result[0])
		// Verify public key starts at offset 1
		for (i in 0 until 32) {
			assertEquals(i.toByte(), result[1 + i])
		}
		// Verify request data starts at offset 33
		assertEquals(0xFF.toByte(), result[33])
		assertEquals(0xFE.toByte(), result[34])
	}

	@Test
	fun sendBinaryRequest_invalidKeySize() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.sendBinaryRequest(ByteArray(16), byteArrayOf(0x01))
		}
	}

	// --- Contact Management ---

	@Test
	fun addContact_correctLayout() {
		val publicKey = ByteArray(32) { it.toByte() }
		val result = CommandSerializer.addContact(publicKey, "Alice", type = 1, flags = 2)
		assertEquals(131, result.size)
		assertEquals(0x09.toByte(), result[0])
		assertEquals(0x00.toByte(), result[1]) // first byte of key
		assertEquals(0x1F.toByte(), result[32]) // last byte of key
		assertEquals(0x01.toByte(), result[33]) // type
		assertEquals(0x02.toByte(), result[34]) // flags
		assertEquals('A'.code.toByte(), result[99]) // name start
	}

	@Test
	fun addContact_invalidKeySize() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.addContact(ByteArray(16), "Bob")
		}
	}

	@Test
	fun removeContact() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.removeContact(prefix)
		assertEquals(7, result.size)
		assertEquals(0x0F.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
		assertEquals(0x06.toByte(), result[6])
	}

	@Test
	fun removeContact_invalidPrefixSize() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.removeContact(ByteArray(4))
		}
	}

	@Test
	fun resetPath() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.resetPath(prefix)
		assertEquals(7, result.size)
		assertEquals(0x0D.toByte(), result[0])
	}

	@Test
	fun shareContact() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.shareContact(prefix)
		assertEquals(7, result.size)
		assertEquals(0x10.toByte(), result[0])
	}

	@Test
	fun exportContact() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.exportContact(prefix)
		assertEquals(7, result.size)
		assertEquals(0x11.toByte(), result[0])
	}

	@Test
	fun importContact() {
		val cardData = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
		val result = CommandSerializer.importContact(cardData)
		assertEquals(4, result.size)
		assertEquals(0x12.toByte(), result[0])
		assertEquals(0xAA.toByte(), result[1])
	}

	@Test
	fun setAutoAddConfig_enabled() {
		val result = CommandSerializer.setAutoAddConfig(true)
		assertContentEquals(byteArrayOf(0x3A, 0x01), result)
	}

	@Test
	fun setAutoAddConfig_disabled() {
		val result = CommandSerializer.setAutoAddConfig(false)
		assertContentEquals(byteArrayOf(0x3A, 0x00), result)
	}

	@Test
	fun getAutoAddConfig() {
		val result = CommandSerializer.getAutoAddConfig()
		assertContentEquals(byteArrayOf(0x3B), result)
	}

	// --- Remote Command ---

	@Test
	fun sendRemoteCommand() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendRemoteCommand(prefix, "reboot", 1000L)
		assertEquals(0x02.toByte(), result[0]) // SEND_TXT_MSG
		assertEquals(0x01.toByte(), result[1]) // subcode: command
		assertEquals(0x00.toByte(), result[2]) // attempt
		// timestamp at 3..6
		assertEquals(0x01.toByte(), result[7]) // first byte of prefix
		assertEquals('r'.code.toByte(), result[13]) // first byte of "reboot"
	}

	// --- Advertisement ---

	@Test
	fun sendAdvert_noFlood() {
		val result = CommandSerializer.sendAdvert(false)
		assertContentEquals(byteArrayOf(0x07, 0x00), result)
	}

	@Test
	fun sendAdvert_flood() {
		val result = CommandSerializer.sendAdvert(true)
		assertContentEquals(byteArrayOf(0x07, 0x01), result)
	}

	// --- Device Configuration ---

	@Test
	fun setName() {
		val result = CommandSerializer.setName("MyNode")
		assertEquals(7, result.size)
		assertEquals(0x08.toByte(), result[0])
		assertEquals('M'.code.toByte(), result[1])
	}

	@Test
	fun setCoords() {
		val result = CommandSerializer.setCoords(45.5, -122.5)
		assertEquals(9, result.size)
		assertEquals(0x0E.toByte(), result[0])
	}

	@Test
	fun setTxPower() {
		val result = CommandSerializer.setTxPower(20)
		assertContentEquals(byteArrayOf(0x0C, 0x14), result)
	}

	@Test
	fun setDevicePin() {
		val result = CommandSerializer.setDevicePin(1234)
		assertEquals(5, result.size)
		assertEquals(0x25.toByte(), result[0])
		// 1234 = 0x4D2 LE: D2 04 00 00
		assertEquals(0xD2.toByte(), result[1])
		assertEquals(0x04.toByte(), result[2])
	}

	@Test
	fun setRadio() {
		val result = CommandSerializer.setRadio(915.0, 125.0, 12, 5, 1)
		assertEquals(12, result.size)
		assertEquals(0x0B.toByte(), result[0])
		assertEquals(12.toByte(), result[9]) // SF
		assertEquals(5.toByte(), result[10]) // CR
		assertEquals(1.toByte(), result[11]) // repeat
	}

	@Test
	fun setTuning() {
		val result = CommandSerializer.setTuning(10, 20)
		assertContentEquals(byteArrayOf(0x15, 0x0A, 0x14), result)
	}

	@Test
	fun reboot() {
		val result = CommandSerializer.reboot()
		assertContentEquals(byteArrayOf(0x13), result)
	}

	@Test
	fun factoryReset() {
		val result = CommandSerializer.factoryReset()
		assertContentEquals(byteArrayOf(0x33), result)
	}

	@Test
	fun setOtherParams() {
		val result = CommandSerializer.setOtherParams(
			advertType = 2,
			telemetryModeBase = 1,
			telemetryModeLoc = 2,
			telemetryModeEnv = 3,
		)
		assertEquals(3, result.size)
		assertEquals(0x26.toByte(), result[0])
		assertEquals(0x02.toByte(), result[1]) // advertType
		// telemetry = 1 | (2 << 2) | (3 << 4) = 1 | 8 | 48 = 57 = 0x39
		assertEquals(0x39.toByte(), result[2])
	}

	// --- Auth ---

	@Test
	fun sendLogin() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendLogin(prefix, "pass")
		assertEquals(11, result.size)
		assertEquals(0x1A.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
		assertEquals('p'.code.toByte(), result[7])
	}

	@Test
	fun sendLogout() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendLogout(prefix)
		assertEquals(7, result.size)
		assertEquals(0x1D.toByte(), result[0])
	}

	// --- Path Discovery ---

	@Test
	fun sendPathDiscovery() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendPathDiscovery(prefix)
		assertEquals(7, result.size)
		assertEquals(0x34.toByte(), result[0])
	}

	@Test
	fun hasConnection() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.hasConnection(prefix)
		assertEquals(7, result.size)
		assertEquals(0x1C.toByte(), result[0])
	}

	@Test
	fun sendTrace() {
		val result = CommandSerializer.sendTrace(1, 2, 3)
		assertEquals(4, result.size)
		assertEquals(0x24.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
		assertEquals(0x02.toByte(), result[2])
		assertEquals(0x03.toByte(), result[3])
	}

	@Test
	fun sendTrace_withPath() {
		val path = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
		val result = CommandSerializer.sendTrace(1, 2, 3, path)
		assertEquals(6, result.size)
		assertEquals(0xAA.toByte(), result[4])
		assertEquals(0xBB.toByte(), result[5])
	}

	// --- Cryptography ---

	@Test
	fun exportPrivateKey() {
		val result = CommandSerializer.exportPrivateKey()
		assertContentEquals(byteArrayOf(0x17), result)
	}

	@Test
	fun importPrivateKey() {
		val key = ByteArray(32) { it.toByte() }
		val result = CommandSerializer.importPrivateKey(key)
		assertEquals(33, result.size)
		assertEquals(0x18.toByte(), result[0])
		assertEquals(0x00.toByte(), result[1])
		assertEquals(0x1F.toByte(), result[32])
	}

	@Test
	fun importPrivateKey_invalidSize() {
		assertFailsWith<IllegalArgumentException> {
			CommandSerializer.importPrivateKey(ByteArray(16))
		}
	}

	@Test
	fun signStart() {
		val result = CommandSerializer.signStart()
		assertContentEquals(byteArrayOf(0x21), result)
	}

	@Test
	fun signData() {
		val chunk = byteArrayOf(0x01, 0x02, 0x03)
		val result = CommandSerializer.signData(chunk)
		assertEquals(4, result.size)
		assertEquals(0x22.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
	}

	@Test
	fun signFinish() {
		val result = CommandSerializer.signFinish(30, 256)
		assertEquals(5, result.size)
		assertEquals(0x23.toByte(), result[0])
		// timeout=30 LE: 1E 00
		assertEquals(0x1E.toByte(), result[1])
		assertEquals(0x00.toByte(), result[2])
		// size=256 LE: 00 01
		assertEquals(0x00.toByte(), result[3])
		assertEquals(0x01.toByte(), result[4])
	}

	// --- Custom Variables ---

	@Test
	fun getCustomVars() {
		val result = CommandSerializer.getCustomVars()
		assertContentEquals(byteArrayOf(0x28), result)
	}

	@Test
	fun setCustomVar() {
		val result = CommandSerializer.setCustomVar("foo", "bar")
		val expected = byteArrayOf(0x29) + "foo=bar".encodeToByteArray()
		assertContentEquals(expected, result)
	}

	// --- Telemetry ---

	@Test
	fun getSelfTelemetry() {
		val result = CommandSerializer.getSelfTelemetry()
		assertContentEquals(byteArrayOf(0x27, 0x00), result)
	}

	@Test
	fun sendTelemetryRequest() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendTelemetryRequest(prefix)
		assertEquals(8, result.size)
		assertEquals(0x27.toByte(), result[0])
		assertEquals(0x01.toByte(), result[1])
		assertEquals(0x01.toByte(), result[2])
	}

	@Test
	fun sendStatusRequest() {
		val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
		val result = CommandSerializer.sendStatusRequest(prefix)
		assertEquals(7, result.size)
		assertEquals(0x1B.toByte(), result[0])
	}

	// --- Control Data ---

	@Test
	fun sendControlData() {
		val payload = byteArrayOf(0x01, 0x02)
		val result = CommandSerializer.sendControlData(5, payload)
		assertEquals(4, result.size)
		assertEquals(0x37.toByte(), result[0])
		assertEquals(0x05.toByte(), result[1])
		assertEquals(0x01.toByte(), result[2])
	}

	// --- Anonymous Requests ---

	@Test
	fun sendAnonymousRequest() {
		val publicKey = ByteArray(32) { it.toByte() }
		val result = CommandSerializer.sendAnonymousRequest(publicKey, 0x02, byteArrayOf(0xAA.toByte()))
		assertEquals(35, result.size)
		assertEquals(0x36.toByte(), result[0])
		assertEquals(0x00.toByte(), result[1]) // first key byte
		assertEquals(0x02.toByte(), result[33]) // request type
		assertEquals(0xAA.toByte(), result[34]) // payload
	}

	// --- Config Queries ---

	@Test
	fun getAllowedRepeatFreq() {
		val result = CommandSerializer.getAllowedRepeatFreq()
		assertContentEquals(byteArrayOf(0x3C), result)
	}

	@Test
	fun setPathHashMode() {
		val result = CommandSerializer.setPathHashMode(1)
		assertContentEquals(byteArrayOf(0x3D, 0x01), result)
	}
}
