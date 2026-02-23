package com.darkrockstudios.libs.meshcorekmp.protocol

class PacketBuffer {
	private var buffer = ByteArray(0)

	fun processData(data: ByteArray): List<Response> {
		if (buffer.isEmpty()) {
			// Fast path: try parsing the incoming data directly
			val response = ResponseParser.parse(data)
			if (response != null) return listOf(response)
			// If parsing failed, start buffering
			buffer = data.copyOf()
			return emptyList()
		}

		// Append to existing buffer
		buffer = buffer + data

		// Try to parse from buffer
		val response = ResponseParser.parse(buffer)
		return if (response != null) {
			buffer = ByteArray(0)
			listOf(response)
		} else {
			emptyList()
		}
	}

	fun reset() {
		buffer = ByteArray(0)
	}
}
