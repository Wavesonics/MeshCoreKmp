package com.darkrockstudios.libs.meshcore.protocol

sealed class Response {
	data class Ok(val value: Int? = null) : Response()

	data class Error(val code: Int) : Response()

	data class DeviceInfo(
		val firmwareVersion: Int,
		val maxContacts: Int,
		val maxChannels: Int,
		val blePin: Int,
		val firmwareBuild: String,
		val model: String,
		val version: String,
	) : Response()

	data class SelfInfo(
		val advertisementType: Int,
		val txPower: Int,
		val maxTxPower: Int,
		val publicKey: String,
		val advertisementLatitude: Double?,
		val advertisementLongitude: Double?,
		val multiAcks: Int,
		val advertisementLocationPolicy: Int,
		val telemetryModeBase: Int,
		val telemetryModeLoc: Int,
		val telemetryModeEnv: Int,
		val manualAddContacts: Boolean,
		val radioFrequency: Double,
		val radioBandwidth: Double,
		val radioSpreadingFactor: Int,
		val radioCodingRate: Int,
		val deviceName: String,
	) : Response()

	data class Battery(
		val milliVolts: Int,
		val usedStorageKb: Int?,
		val totalStorageKb: Int?,
	) : Response()

	data class ChannelInfo(
		val index: Int,
		val name: String,
	) : Response()

	data object ContactStart : Response()

	data class Contact(
		val publicKey: String,
		val type: Int,
		val flags: Int,
		val outPathLen: Int,
		val name: String,
		val lastAdvertTimestamp: Long,
		val gpsLatitude: Double?,
		val gpsLongitude: Double?,
		val lastmod: Long,
	) : Response()

	data object ContactEnd : Response()

	data class MessageSent(
		val messageType: Int,
		val expectedAck: String,
		val suggestedTimeoutSeconds: Int,
	) : Response()

	data class ChannelMessageReceived(
		val channelIndex: Int,
		val pathLength: Int,
		val textType: Int,
		val timestamp: Long,
		val text: String,
		val snr: Float?,
	) : Response()

	data class ContactMessageReceived(
		val publicKeyPrefix: String,
		val pathLength: Int,
		val textType: Int,
		val timestamp: Long,
		val signature: String?,
		val text: String,
		val snr: Float?,
	) : Response()

	data object NoMoreMessages : Response()

	data class MessagesWaiting(val count: Int) : Response()

	data class Ack(val ackCode: String) : Response()

	data class AdvertisementReceived(val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is AdvertisementReceived) return false
			return rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int = rawData.contentHashCode()
	}

	data class CurrentTime(val timestamp: Long) : Response()

	sealed class Stats : Response() {
		data class Core(
			val batteryMillivolts: Int,
			val uptimeSeconds: Long,
			val errors: Int,
			val queueLength: Int,
		) : Stats()

		data class Radio(
			val noiseFloorDbm: Int,
			val lastRssiDbm: Int,
			val lastSnrDb: Float,
			val txAirtimeSeconds: Long,
			val rxAirtimeSeconds: Long,
		) : Stats()

		data class Packets(
			val received: Long,
			val sent: Long,
			val floodTx: Long,
			val directTx: Long,
			val floodRx: Long,
			val directRx: Long,
			val recvErrors: Long?,
		) : Stats()
	}

	data class RawDataReceived(
		val snr: Float,
		val rssi: Int,
		val payload: ByteArray,
	) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is RawDataReceived) return false
			return snr == other.snr && rssi == other.rssi && payload.contentEquals(other.payload)
		}

		override fun hashCode(): Int {
			var result = snr.hashCode()
			result = 31 * result + rssi
			result = 31 * result + payload.contentHashCode()
			return result
		}
	}

	data class BinaryResponse(
		val tag: Long,
		val responseData: ByteArray,
	) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is BinaryResponse) return false
			return tag == other.tag && responseData.contentEquals(other.responseData)
		}

		override fun hashCode(): Int {
			var result = tag.hashCode()
			result = 31 * result + responseData.contentHashCode()
			return result
		}
	}

	data class LogData(val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is LogData) return false
			return rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int = rawData.contentHashCode()
	}

	/**
	 * A packet with a recognized type code but no dedicated parser.
	 * Used for protocol codes we know about but haven't fully implemented yet,
	 * so they are routed correctly instead of blocking the command queue.
	 */
	data class Unhandled(val code: Int, val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Unhandled) return false
			return code == other.code && rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int {
			var result = code
			result = 31 * result + rawData.contentHashCode()
			return result
		}
	}
}
