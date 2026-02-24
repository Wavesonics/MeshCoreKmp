package com.darkrockstudios.libs.meshcore.model

data class ReceivedRawData(
	val snr: Float,
	val rssi: Int,
	val payload: ByteArray,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ReceivedRawData) return false
		return snr == other.snr && rssi == other.rssi && payload.contentEquals(other.payload)
	}

	override fun hashCode(): Int {
		var result = snr.hashCode()
		result = 31 * result + rssi
		result = 31 * result + payload.contentHashCode()
		return result
	}
}

data class ReceivedBinaryResponse(
	val tag: Long,
	val responseData: ByteArray,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ReceivedBinaryResponse) return false
		return tag == other.tag && responseData.contentEquals(other.responseData)
	}

	override fun hashCode(): Int {
		var result = tag.hashCode()
		result = 31 * result + responseData.contentHashCode()
		return result
	}
}
