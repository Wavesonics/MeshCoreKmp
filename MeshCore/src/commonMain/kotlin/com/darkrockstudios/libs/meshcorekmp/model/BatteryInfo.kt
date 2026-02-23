package com.darkrockstudios.libs.meshcorekmp.model

import kotlinx.serialization.Serializable

@Serializable
data class BatteryInfo(
	val levelPercent: Int,
	val usedStorageKb: Int? = null,
	val totalStorageKb: Int? = null,
)
