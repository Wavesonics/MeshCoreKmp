package com.darkrockstudios.libs.meshcorekmp.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
	val publicKeyPrefix: String,
	val name: String,
	val lastSeen: Long,
	val type: Int = 0,
)
