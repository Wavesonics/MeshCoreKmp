package com.darkrockstudios.libs.meshcorekmp.protocol

object ErrorCode {
	const val GENERIC = 0x00
	const val INVALID_COMMAND = 0x01
	const val INVALID_PARAMETER = 0x02
	const val CHANNEL_NOT_FOUND = 0x03
	const val CHANNEL_ALREADY_EXISTS = 0x04
	const val CHANNEL_INDEX_OUT_OF_RANGE = 0x05
	const val SECRET_MISMATCH = 0x06
	const val MESSAGE_TOO_LONG = 0x07
	const val DEVICE_BUSY = 0x08
	const val NOT_ENOUGH_STORAGE = 0x09

	fun describe(code: Int): String = when (code) {
		GENERIC -> "Generic error"
		INVALID_COMMAND -> "Invalid command"
		INVALID_PARAMETER -> "Invalid parameter"
		CHANNEL_NOT_FOUND -> "Channel not found"
		CHANNEL_ALREADY_EXISTS -> "Channel already exists"
		CHANNEL_INDEX_OUT_OF_RANGE -> "Channel index out of range"
		SECRET_MISMATCH -> "Secret mismatch"
		MESSAGE_TOO_LONG -> "Message too long"
		DEVICE_BUSY -> "Device busy"
		NOT_ENOUGH_STORAGE -> "Not enough storage"
		else -> "Unknown error (0x${code.toString(16)})"
	}
}
