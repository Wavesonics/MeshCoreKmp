package com.darkrockstudios.libs.meshcore.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PacketBufferTest {

	@Test
	fun processData_completePacket_returnsParsedResponse() {
		val buffer = PacketBuffer()
		val data = byteArrayOf(0x00, 0x2A, 0x00, 0x00, 0x00) // PACKET_OK with value 42
		val results = buffer.processData(data)
		assertEquals(1, results.size)
		assertIs<Response.Ok>(results[0])
		assertEquals(42, (results[0] as Response.Ok).value)
	}

	@Test
	fun processData_noMoreMessages_singleByte() {
		val buffer = PacketBuffer()
		val data = byteArrayOf(0x0A) // PACKET_NO_MORE_MSGS
		val results = buffer.processData(data)
		assertEquals(1, results.size)
		assertIs<Response.NoMoreMessages>(results[0])
	}

	@Test
	fun processData_errorPacket() {
		val buffer = PacketBuffer()
		val data = byteArrayOf(0x01, 0x07) // PACKET_ERROR - message too long
		val results = buffer.processData(data)
		assertEquals(1, results.size)
		assertIs<Response.Error>(results[0])
		assertEquals(ErrorCode.MESSAGE_TOO_LONG, (results[0] as Response.Error).code)
	}

	@Test
	fun reset_clearsBuffer() {
		val buffer = PacketBuffer()
		// Feed some unrecognizable data
		buffer.processData(byteArrayOf(0x7F, 0x01, 0x02))
		buffer.reset()
		// After reset, a valid packet should parse correctly
		val results = buffer.processData(byteArrayOf(0x0A))
		assertEquals(1, results.size)
		assertIs<Response.NoMoreMessages>(results[0])
	}
}
