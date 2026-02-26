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
}
