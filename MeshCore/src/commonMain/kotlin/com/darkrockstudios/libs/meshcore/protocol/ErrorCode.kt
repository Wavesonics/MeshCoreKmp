package com.darkrockstudios.libs.meshcore.protocol

object ErrorCode {
	const val UNSUPPORTED_CMD = 0x01
	const val NOT_FOUND = 0x02
	const val TABLE_FULL = 0x03
	const val BAD_STATE = 0x04
	const val FILE_IO_ERROR = 0x05
	const val ILLEGAL_ARG = 0x06

	fun describe(code: Int): String = when (code) {
		UNSUPPORTED_CMD -> "Unsupported command"
		NOT_FOUND -> "Not found"
		TABLE_FULL -> "Table full"
		BAD_STATE -> "Bad state"
		FILE_IO_ERROR -> "File I/O error"
		ILLEGAL_ARG -> "Illegal argument"
		else -> "Unknown error (0x${code.toString(16)})"
	}
}
