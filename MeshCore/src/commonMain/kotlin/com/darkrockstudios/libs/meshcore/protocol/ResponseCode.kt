package com.darkrockstudios.libs.meshcore.protocol

object ResponseCode {
	// Solicited responses (0x00–0x7F)
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
	const val PACKET_CONTACT_URI = 0x0B
	const val PACKET_BATTERY = 0x0C
	const val PACKET_DEVICE_INFO = 0x0D
	const val PACKET_PRIVATE_KEY = 0x0E
	const val PACKET_DISABLED = 0x0F
	const val PACKET_CONTACT_MSG_RECV_V3 = 0x10
	const val PACKET_CHANNEL_MSG_RECV_V3 = 0x11
	const val PACKET_CHANNEL_INFO = 0x12
	const val PACKET_SIGN_START = 0x13
	const val PACKET_SIGNATURE = 0x14
	const val PACKET_CUSTOM_VARS = 0x15
	const val RESP_CODE_STATS = 0x18
	const val PACKET_AUTOADD_CONFIG = 0x19
	const val PACKET_ALLOWED_REPEAT_FREQ = 0x1A

	// Push events (0x80+) — unsolicited notifications from device
	const val PUSH_CODE_ADVERTISEMENT = 0x80
	const val PUSH_CODE_PATH_UPDATED = 0x81
	const val PUSH_CODE_ACK = 0x82
	const val PUSH_CODE_MSG_WAITING = 0x83
	const val PUSH_CODE_RAW_DATA = 0x84
	const val PUSH_CODE_LOGIN_SUCCESS = 0x85
	const val PUSH_CODE_LOGIN_FAIL = 0x86
	const val PUSH_CODE_STATUS_RESPONSE = 0x87
	const val PUSH_CODE_LOG_DATA = 0x88
	const val PUSH_CODE_TRACE_DATA = 0x89
	const val PUSH_CODE_NEW_ADVERT = 0x8A
	const val PUSH_CODE_TELEMETRY_RESPONSE = 0x8B
	const val PUSH_CODE_BINARY_RESPONSE = 0x8C
	const val PUSH_CODE_PATH_DISCOVERY_RESPONSE = 0x8D
	const val PUSH_CODE_CONTROL_DATA = 0x8E
	const val PUSH_CODE_CONTACT_DELETED = 0x8F

	// Deprecated aliases — use the PUSH_CODE_ prefixed versions above
	@Deprecated("Use PUSH_CODE_ADVERTISEMENT", ReplaceWith("PUSH_CODE_ADVERTISEMENT"))
	const val PACKET_ADVERTISEMENT = PUSH_CODE_ADVERTISEMENT

	@Deprecated("Use PUSH_CODE_ACK", ReplaceWith("PUSH_CODE_ACK"))
	const val PACKET_ACK = PUSH_CODE_ACK

	@Deprecated("Use PUSH_CODE_MSG_WAITING", ReplaceWith("PUSH_CODE_MSG_WAITING"))
	const val PACKET_MESSAGES_WAITING = PUSH_CODE_MSG_WAITING

	@Deprecated("Use PUSH_CODE_LOG_DATA", ReplaceWith("PUSH_CODE_LOG_DATA"))
	const val PACKET_LOG_DATA = PUSH_CODE_LOG_DATA
}
