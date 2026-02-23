package com.darkrockstudios.libs.meshcorekmp.protocol

object CommandCode {
	const val APP_START = 0x01
	const val SEND_CHANNEL_MESSAGE = 0x03
	const val GET_MESSAGE = 0x0A
	const val GET_BATTERY = 0x14
	const val DEVICE_QUERY = 0x16
	const val GET_CHANNEL = 0x1F
	const val SET_CHANNEL = 0x20
	const val GET_STATS = 0x38
}
