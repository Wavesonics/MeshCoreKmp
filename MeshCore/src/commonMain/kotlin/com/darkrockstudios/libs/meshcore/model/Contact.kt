package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Contact(
	val publicKey: ByteArray,
	val name: String,
	val type: Int = 0,
	val flags: Int = 0,
	val lastAdvertTimestamp: Long = 0,
	val gpsLatitude: Double? = null,
	val gpsLongitude: Double? = null,
	val lastmod: Long = 0,
) {
	@Transient
	val publicKeyPrefix: ByteArray = publicKey.copyOfRange(0, 6)

	fun publicKeyPrefixHex(): String = publicKeyPrefix.toHexString()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as Contact

		if (type != other.type) return false
		if (flags != other.flags) return false
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
		result = 31 * result + lastAdvertTimestamp.hashCode()
		result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
		result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
		result = 31 * result + lastmod.hashCode()
		result = 31 * result + publicKey.contentHashCode()
		result = 31 * result + name.hashCode()
		return result
	}
}
