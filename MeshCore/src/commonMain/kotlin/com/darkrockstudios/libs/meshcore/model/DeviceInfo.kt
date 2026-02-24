package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
	val firmwareVersion: Int,
	val maxContacts: Int,
	val maxChannels: Int,
	val blePin: Int,
	val firmwareBuild: String,
	val model: String,
	val version: String,
)
