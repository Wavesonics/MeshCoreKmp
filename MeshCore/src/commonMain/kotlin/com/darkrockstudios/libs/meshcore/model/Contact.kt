package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
	val publicKey: String,
	val publicKeyPrefix: String,
	val name: String,
	val type: Int = 0,
	val flags: Int = 0,
	val lastAdvertTimestamp: Long = 0,
	val gpsLatitude: Double? = null,
	val gpsLongitude: Double? = null,
	val lastmod: Long = 0,
)
