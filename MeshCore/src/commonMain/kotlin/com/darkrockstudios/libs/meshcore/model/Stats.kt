package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Stats {
	@Serializable
	data class Core(
		val batteryMillivolts: Int,
		val uptimeSeconds: Long,
		val errors: Int,
		val queueLength: Int,
	) : Stats()

	@Serializable
	data class Radio(
		val noiseFloorDbm: Int,
		val lastRssiDbm: Int,
		val lastSnrDb: Float,
		val txAirtimeSeconds: Long,
		val rxAirtimeSeconds: Long,
	) : Stats()

	@Serializable
	data class Packets(
		val received: Long,
		val sent: Long,
		val floodTx: Long,
		val directTx: Long,
		val floodRx: Long,
		val directRx: Long,
		val recvErrors: Long? = null,
	) : Stats()
}
