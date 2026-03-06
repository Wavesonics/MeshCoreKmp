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
		val publicKey: ByteArray,
		val type: Int,
		val flags: Int,
		val outPathLen: Int,
		val name: String,
		val lastAdvertTimestamp: Long,
		val gpsLatitude: Double?,
		val gpsLongitude: Double?,
		val lastmod: Long,
	) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false

			other as Contact

			if (type != other.type) return false
			if (flags != other.flags) return false
			if (outPathLen != other.outPathLen) return false
			if (lastAdvertTimestamp != other.lastAdvertTimestamp) return false
			if (gpsLatitude != other.gpsLatitude) return false
			if (gpsLongitude != other.gpsLongitude) return false
			if (lastmod != other.lastmod) return false
			if (!publicKey.contentEquals(other.publicKey)) return false
			if (name != other.name) return false

			return true
		}

		override fun hashCode(): Int {
			var result = type
			result = 31 * result + flags
			result = 31 * result + outPathLen
			result = 31 * result + lastAdvertTimestamp.hashCode()
			result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
			result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
			result = 31 * result + lastmod.hashCode()
			result = 31 * result + publicKey.contentHashCode()
			result = 31 * result + name.hashCode()
			return result
		}
	}

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

	data class ContactUri(val uri: String) : Response()

	data class PrivateKey(val key: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is PrivateKey) return false
			return key.contentEquals(other.key)
		}

		override fun hashCode(): Int = key.contentHashCode()
	}

	data object Disabled : Response()

	data class SignStartResponse(val sessionId: Int) : Response()

	data class Signature(val signatureData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Signature) return false
			return signatureData.contentEquals(other.signatureData)
		}

		override fun hashCode(): Int = signatureData.contentHashCode()
	}

	data class CustomVars(val data: String) : Response()

	data class AutoAddConfig(val enabled: Boolean) : Response()

	data class AllowedRepeatFreq(val frequencies: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is AllowedRepeatFreq) return false
			return frequencies.contentEquals(other.frequencies)
		}

		override fun hashCode(): Int = frequencies.contentHashCode()
	}

	// Push event responses

	data class LoginSuccess(val publicKeyPrefix: String) : Response()

	data class LoginFail(val publicKeyPrefix: String) : Response()

	data class StatusResponse(val publicKeyPrefix: String, val statusData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is StatusResponse) return false
			return publicKeyPrefix == other.publicKeyPrefix && statusData.contentEquals(other.statusData)
		}

		override fun hashCode(): Int {
			var result = publicKeyPrefix.hashCode()
			result = 31 * result + statusData.contentHashCode()
			return result
		}
	}

	data class TraceData(val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is TraceData) return false
			return rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int = rawData.contentHashCode()
	}

	data class NewAdvert(val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is NewAdvert) return false
			return rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int = rawData.contentHashCode()
	}

	data class TelemetryResponse(val publicKeyPrefix: String, val telemetryData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is TelemetryResponse) return false
			return publicKeyPrefix == other.publicKeyPrefix && telemetryData.contentEquals(other.telemetryData)
		}

		override fun hashCode(): Int {
			var result = publicKeyPrefix.hashCode()
			result = 31 * result + telemetryData.contentHashCode()
			return result
		}
	}

	data class PathDiscoveryResponse(val rawData: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is PathDiscoveryResponse) return false
			return rawData.contentEquals(other.rawData)
		}

		override fun hashCode(): Int = rawData.contentHashCode()
	}

	data class ControlData(val type: Int, val payload: ByteArray) : Response() {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is ControlData) return false
			return type == other.type && payload.contentEquals(other.payload)
		}

		override fun hashCode(): Int {
			var result = type
			result = 31 * result + payload.contentHashCode()
			return result
		}
	}

	data class ContactDeleted(val publicKeyPrefix: String) : Response()

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
