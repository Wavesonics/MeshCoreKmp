package com.darkrockstudios.libs.meshcorekmp.protocol

object ResponseCode {
	const val PACKET_OK = 0x00
	const val PACKET_ERROR = 0x01
	const val PACKET_CONTACT_START = 0x02
	const val PACKET_CONTACT = 0x03
	const val PACKET_CONTACT_END = 0x04
	const val PACKET_SELF_INFO = 0x05
	const val PACKET_MSG_SENT = 0x06
	const val PACKET_CONTACT_MSG_RECV = 0x07
	const val PACKET_CHANNEL_MSG_RECV = 0x08
	const val PACKET_CURRENT_TIME = 0x09
	const val PACKET_NO_MORE_MSGS = 0x0A
	const val PACKET_BATTERY = 0x0C
	const val PACKET_DEVICE_INFO = 0x0D
	const val PACKET_CONTACT_MSG_RECV_V3 = 0x10
	const val PACKET_CHANNEL_MSG_RECV_V3 = 0x11
	const val PACKET_CHANNEL_INFO = 0x12
	const val RESP_CODE_STATS = 0x18
	const val PACKET_ADVERTISEMENT = 0x80
	const val PACKET_ACK = 0x82
	const val PACKET_MESSAGES_WAITING = 0x83
	const val PACKET_LOG_DATA = 0x88
}
