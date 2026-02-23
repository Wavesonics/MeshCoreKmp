package com.darkrockstudios.libs.meshcorekmp.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ReceivedMessage {
	abstract val timestamp: Long
	abstract val text: String
	abstract val pathLength: Int
	abstract val snr: Float?

	@Serializable
	data class ChannelMessage(
		val channelIndex: Int,
		override val timestamp: Long,
		override val text: String,
		override val pathLength: Int,
		override val snr: Float? = null,
	) : ReceivedMessage()

	@Serializable
	data class ContactMessage(
		val publicKeyPrefix: String,
		val signature: String? = null,
		override val timestamp: Long,
		override val text: String,
		override val pathLength: Int,
		override val snr: Float? = null,
	) : ReceivedMessage()
}
