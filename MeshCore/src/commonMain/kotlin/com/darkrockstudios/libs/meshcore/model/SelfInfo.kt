package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable

@Serializable
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
)
